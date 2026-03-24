package com.schedule.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Типизированный хелпер для таблиц ScheduleApp в Supabase.
 *
 * Таблицы:
 *   presence    – онлайн-статус пользователей
 *   messages    – личные/групповые сообщения
 *   leaderboard – рейтинги игр
 *   users       – профили пользователей
 *   accounts    – авторизация (username + pwd_hash)
 *
 * Все методы синхронные — вызывай из фонового потока (JS async/Worker).
 * Каждый метод возвращает JSON: {ok, status, body, error?}
 * При успехе body — это JSON-строка (массив или объект от PostgREST).
 */
public class SupabaseHelper {

    private static final String TAG = "SupabaseHelper";

    private static volatile SupabaseHelper instance;

    private final SupabaseClient db;
    private final AppLogger      log;

    // ── Singleton ─────────────────────────────────────────────────────────────
    public static SupabaseHelper get(Context ctx, AppLogger log) {
        if (instance == null) {
            synchronized (SupabaseHelper.class) {
                if (instance == null) instance = new SupabaseHelper(ctx, log);
            }
        }
        return instance;
    }

    private SupabaseHelper(Context ctx, AppLogger log) {
        this.log = log;
        this.db  = SupabaseClient.get(ctx, log);
        log.i(TAG, "SupabaseHelper инициализирован");
    }

    // ════════════════════════════════════════════════════════════════════════
    // PRESENCE — онлайн-статус
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Обновить/создать присутствие пользователя (heartbeat).
     * Вызывать каждые ~15-30 секунд пока пользователь онлайн.
     *
     * @param username   уникальный ник
     * @param name       отображаемое имя
     * @param avatar     эмодзи или URL
     * @param avatarType "emoji" | "url" | "base64"
     * @param avatarData base64-данные аватара (или null)
     * @param color      hex-цвет профиля, например "#e87722"
     * @param status     "online" | "away" | "offline"
     * @param vip        VIP-статус
     * @param badge      текст бейджа или null
     * @param pwdHash    хэш пароля (для совместимости с presence)
     */
    public String presenceUpsert(String username, String name, String avatar,
                                  String avatarType, String avatarData,
                                  String color, String status,
                                  boolean vip, String badge, String pwdHash) {
        log.i(TAG, "presenceUpsert ▸ username=" + username + " status=" + status);
        try {
            JSONObject obj = new JSONObject();
            obj.put("username",    username);
            obj.put("name",        name);
            obj.put("avatar",      avatar);
            obj.put("avatar_type", avatarType != null ? avatarType : "emoji");
            if (avatarData != null) obj.put("avatar_data", avatarData);
            obj.put("color",       color != null ? color : "#e87722");
            obj.put("status",      status != null ? status : "online");
            obj.put("vip",         vip);
            if (badge != null)     obj.put("badge", badge);
            if (pwdHash != null)   obj.put("pwd_hash", pwdHash);
            obj.put("ts",          System.currentTimeMillis());
            return db.upsert("presence", obj.toString());
        } catch (Exception e) {
            log.e(TAG, "presenceUpsert ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Пометить пользователя как оффлайн (status=offline).
     */
    public String presenceOffline(String username) {
        log.i(TAG, "presenceOffline ▸ username=" + username);
        try {
            JSONObject obj = new JSONObject();
            obj.put("status", "offline");
            obj.put("ts", System.currentTimeMillis());
            return db.update("presence", "username=eq." + encodeFilter(username), obj.toString());
        } catch (Exception e) {
            log.e(TAG, "presenceOffline ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить всех онлайн-пользователей (status != 'offline').
     * Результат: массив строк presence.
     */
    public String presenceGetOnline() {
        log.i(TAG, "presenceGetOnline ▸");
        return db.select("presence",
                "select=username,name,avatar,avatar_type,avatar_data,color,status,vip,badge,ts" +
                "&status=neq.offline" +
                "&order=ts.desc");
    }

    /**
     * Получить одного пользователя из presence.
     */
    public String presenceGetUser(String username) {
        log.i(TAG, "presenceGetUser ▸ username=" + username);
        return db.select("presence",
                "select=*&username=eq." + encodeFilter(username) + "&limit=1");
    }

    /**
     * Удалить запись присутствия (полный выход).
     */
    public String presenceDelete(String username) {
        log.i(TAG, "presenceDelete ▸ username=" + username);
        return db.delete("presence", "username=eq." + encodeFilter(username));
    }

    // ════════════════════════════════════════════════════════════════════════
    // MESSAGES — личные и групповые сообщения
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Отправить личное сообщение.
     *
     * @param chatKey  уникальный ключ чата (например, sorted_concat(from, to))
     * @param fromUser отправитель
     * @param toUser   получатель
     * @param text     текст сообщения
     */
    public String messageSend(String chatKey, String fromUser, String toUser, String text) {
        log.i(TAG, "messageSend ▸ chat=" + chatKey + " from=" + fromUser
                + " to=" + toUser + " len=" + (text != null ? text.length() : 0));
        try {
            JSONObject obj = new JSONObject();
            obj.put("chat_key",  chatKey);
            obj.put("from_user", fromUser);
            obj.put("to_user",   toUser);
            obj.put("text",      text);
            obj.put("ts",        System.currentTimeMillis());
            return db.insert("messages", obj.toString());
        } catch (Exception e) {
            log.e(TAG, "messageSend ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить историю чата по chat_key.
     * @param chatKey  ключ чата
     * @param afterTs  получить сообщения новее этого timestamp (0 = все)
     * @param limit    максимум сообщений (рекомендуется 50-100)
     */
    public String messageGetByChatKey(String chatKey, long afterTs, int limit) {
        log.i(TAG, "messageGetByChatKey ▸ chat=" + chatKey
                + " afterTs=" + afterTs + " limit=" + limit);
        String filter = "select=*&chat_key=eq." + encodeFilter(chatKey)
                + "&order=ts.asc&limit=" + limit;
        if (afterTs > 0) filter += "&ts=gt." + afterTs;
        return db.select("messages", filter);
    }

    /**
     * Получить последние входящие сообщения для пользователя (to_user).
     * Используется для polling — получить всё новее afterTs.
     */
    public String messageGetInbox(String toUser, long afterTs, int limit) {
        log.i(TAG, "messageGetInbox ▸ to=" + toUser + " afterTs=" + afterTs + " limit=" + limit);
        String filter = "select=*&to_user=eq." + encodeFilter(toUser)
                + "&order=ts.asc&limit=" + limit;
        if (afterTs > 0) filter += "&ts=gt." + afterTs;
        return db.select("messages", filter);
    }

    /**
     * Удалить сообщение по id.
     */
    public String messageDelete(long messageId) {
        log.i(TAG, "messageDelete ▸ id=" + messageId);
        return db.delete("messages", "id=eq." + messageId);
    }

    /**
     * Удалить все сообщения чата (оба направления).
     * ВНИМАНИЕ: необратимо.
     */
    public String messageClearChat(String chatKey) {
        log.i(TAG, "messageClearChat ▸ chat=" + chatKey);
        return db.delete("messages", "chat_key=eq." + encodeFilter(chatKey));
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEADERBOARD — рейтинги игр
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Обновить/создать запись в лидерборде.
     * Если запись (game, username) уже есть — обновляет score и ts.
     *
     * @param game     идентификатор игры, например "doom" | "minecraft" | "tanks"
     * @param username ник игрока
     * @param name     отображаемое имя
     * @param avatar   эмодзи или URL
     * @param color    hex-цвет
     * @param score    очки (целое число)
     */
    public String leaderboardUpsert(String game, String username, String name,
                                     String avatar, String color, int score) {
        log.i(TAG, "leaderboardUpsert ▸ game=" + game
                + " username=" + username + " score=" + score);
        try {
            JSONObject obj = new JSONObject();
            obj.put("game",     game);
            obj.put("username", username);
            obj.put("name",     name);
            obj.put("avatar",   avatar);
            obj.put("color",    color != null ? color : "#e87722");
            obj.put("score",    score);
            obj.put("ts",       System.currentTimeMillis());
            return db.upsert("leaderboard", obj.toString());
        } catch (Exception e) {
            log.e(TAG, "leaderboardUpsert ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить топ-N игроков по игре.
     * @param game  идентификатор игры
     * @param limit количество записей (рекомендуется 10-50)
     */
    public String leaderboardGet(String game, int limit) {
        log.i(TAG, "leaderboardGet ▸ game=" + game + " limit=" + limit);
        return db.select("leaderboard",
                "select=*&game=eq." + encodeFilter(game)
                        + "&order=score.desc&limit=" + limit);
    }

    /**
     * Получить позицию конкретного игрока в игре.
     */
    public String leaderboardGetUser(String game, String username) {
        log.i(TAG, "leaderboardGetUser ▸ game=" + game + " username=" + username);
        return db.select("leaderboard",
                "select=*&game=eq." + encodeFilter(game)
                        + "&username=eq." + encodeFilter(username)
                        + "&limit=1");
    }

    /**
     * Удалить запись из лидерборда.
     */
    public String leaderboardDelete(String game, String username) {
        log.i(TAG, "leaderboardDelete ▸ game=" + game + " username=" + username);
        return db.delete("leaderboard",
                "game=eq." + encodeFilter(game)
                        + "&username=eq." + encodeFilter(username));
    }

    // ════════════════════════════════════════════════════════════════════════
    // USERS — профили пользователей
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Создать профиль пользователя (при регистрации).
     *
     * @param username   уникальный ник
     * @param name       отображаемое имя
     * @param pwdHash    хэш пароля
     * @param avatar     эмодзи или URL
     * @param avatarType "emoji" | "url" | "base64"
     * @param color      hex-цвет профиля
     */
    public String userCreate(String username, String name, String pwdHash,
                              String avatar, String avatarType, String color) {
        log.i(TAG, "userCreate ▸ username=" + username + " name=" + name);
        try {
            JSONObject obj = new JSONObject();
            obj.put("username",    username);
            obj.put("name",        name);
            obj.put("pwd_hash",    pwdHash);
            obj.put("avatar",      avatar != null ? avatar : "😊");
            obj.put("avatar_type", avatarType != null ? avatarType : "emoji");
            obj.put("color",       color != null ? color : "#e87722");
            obj.put("bio",         "");
            obj.put("status",      "online");
            obj.put("vip",         false);
            obj.put("created_at",  System.currentTimeMillis());
            return db.insert("users", obj.toString());
        } catch (Exception e) {
            log.e(TAG, "userCreate ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить профиль пользователя по username.
     */
    public String userGet(String username) {
        log.i(TAG, "userGet ▸ username=" + username);
        return db.select("users",
                "select=*&username=eq." + encodeFilter(username) + "&limit=1");
    }

    /**
     * Обновить поля профиля.
     * @param username  чей профиль обновлять
     * @param fieldsJson JSON-объект с полями для обновления, например:
     *                   {"name":"Alex","bio":"Привет","avatar":"🔥","color":"#ff0000"}
     */
    public String userUpdate(String username, String fieldsJson) {
        log.i(TAG, "userUpdate ▸ username=" + username + " fields=" + fieldsJson);
        return db.update("users", "username=eq." + encodeFilter(username), fieldsJson);
    }

    /**
     * Обновить аватар пользователя.
     * @param avatarType "emoji" | "url" | "base64"
     * @param avatar     эмодзи или URL
     * @param avatarData base64-данные (только если type="base64"), иначе null
     */
    public String userUpdateAvatar(String username, String avatarType,
                                    String avatar, String avatarData) {
        log.i(TAG, "userUpdateAvatar ▸ username=" + username + " type=" + avatarType);
        try {
            JSONObject obj = new JSONObject();
            obj.put("avatar",      avatar);
            obj.put("avatar_type", avatarType);
            if (avatarData != null) obj.put("avatar_data", avatarData);
            return db.update("users", "username=eq." + encodeFilter(username), obj.toString());
        } catch (Exception e) {
            log.e(TAG, "userUpdateAvatar ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Обновить статус пользователя.
     * @param status "online" | "away" | "offline"
     */
    public String userUpdateStatus(String username, String status) {
        log.i(TAG, "userUpdateStatus ▸ username=" + username + " status=" + status);
        try {
            JSONObject obj = new JSONObject();
            obj.put("status", status);
            return db.update("users", "username=eq." + encodeFilter(username), obj.toString());
        } catch (Exception e) {
            log.e(TAG, "userUpdateStatus ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Проверить, существует ли username.
     * Возвращает {ok, exists: true/false}
     */
    public String userExists(String username) {
        log.i(TAG, "userExists ▸ username=" + username);
        try {
            String result = db.select("users",
                    "select=username&username=eq." + encodeFilter(username) + "&limit=1");
            JSONObject res = new JSONObject(result);
            boolean ok = res.optBoolean("ok", false);
            String body = res.optString("body", "[]");
            boolean exists = false;
            if (ok) {
                JSONArray arr = new JSONArray(body);
                exists = arr.length() > 0;
            }
            JSONObject out = new JSONObject();
            out.put("ok",     ok);
            out.put("exists", exists);
            log.i(TAG, "userExists: username=" + username + " exists=" + exists);
            return out.toString();
        } catch (Exception e) {
            log.e(TAG, "userExists ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить список всех пользователей (для поиска/списка контактов).
     * @param limit максимум записей
     */
    public String userGetAll(int limit) {
        log.i(TAG, "userGetAll ▸ limit=" + limit);
        return db.select("users",
                "select=username,name,avatar,avatar_type,color,vip,badge,bio,status"
                        + "&order=created_at.desc&limit=" + limit);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACCOUNTS — авторизация
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Зарегистрировать аккаунт.
     * @param username  уникальный ник
     * @param pwdHash   хэш пароля (SHA-256 или bcrypt — делай в JS)
     * @param name      отображаемое имя
     * @param avatar    эмодзи
     * @param color     hex-цвет
     */
    public String accountCreate(String username, String pwdHash,
                                 String name, String avatar, String color) {
        log.i(TAG, "accountCreate ▸ username=" + username + " name=" + name);
        try {
            JSONObject obj = new JSONObject();
            obj.put("username",   username);
            obj.put("pwd_hash",   pwdHash);
            obj.put("name",       name);
            obj.put("avatar",     avatar != null ? avatar : "😊");
            obj.put("color",      color  != null ? color  : "#e87722");
            obj.put("created_at", System.currentTimeMillis());
            return db.insert("accounts", obj.toString());
        } catch (Exception e) {
            log.e(TAG, "accountCreate ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить аккаунт для проверки пароля при входе.
     * Возвращает pwd_hash который JS сравнивает с введённым.
     */
    public String accountGet(String username) {
        log.i(TAG, "accountGet ▸ username=" + username);
        return db.select("accounts",
                "select=username,pwd_hash,name,avatar,color"
                        + "&username=eq." + encodeFilter(username) + "&limit=1");
    }

    /**
     * Обновить поля аккаунта.
     * @param fieldsJson JSON-объект, например {"name":"Alex","avatar":"🚀"}
     */
    public String accountUpdate(String username, String fieldsJson) {
        log.i(TAG, "accountUpdate ▸ username=" + username + " fields=" + fieldsJson);
        return db.update("accounts", "username=eq." + encodeFilter(username), fieldsJson);
    }

    /**
     * Сменить хэш пароля.
     */
    public String accountChangePassword(String username, String newPwdHash) {
        log.i(TAG, "accountChangePassword ▸ username=" + username);
        try {
            JSONObject obj = new JSONObject();
            obj.put("pwd_hash", newPwdHash);
            return db.update("accounts", "username=eq." + encodeFilter(username), obj.toString());
        } catch (Exception e) {
            log.e(TAG, "accountChangePassword ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Утилиты
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Кодирует строку для безопасного использования в PostgREST-фильтре.
     * Экранирует пробелы и спецсимволы.
     */
    private String encodeFilter(String value) {
        if (value == null) return "";
        // PostgREST не требует полного URL-кодирования для eq.,
        // но пробелы и запятые нужно кодировать
        return value
                .replace("%",  "%25")
                .replace(" ",  "%20")
                .replace(",",  "%2C")
                .replace(".",  "%2E")
                .replace("&",  "%26")
                .replace("=",  "%3D")
                .replace("+",  "%2B")
                .replace("\"", "%22")
                .replace("'",  "%27");
    }

    private String errorJson(String msg) {
        try {
            JSONObject err = new JSONObject();
            err.put("ok",     false);
            err.put("status", 0);
            err.put("body",   "");
            err.put("error",  msg != null ? msg : "unknown");
            return err.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }
}
