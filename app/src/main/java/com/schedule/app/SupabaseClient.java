package com.schedule.app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Supabase REST API клиент.
 *
 * Покрывает:
 *  – Database  : select / insert / update / upsert / delete
 *  – RPC       : вызов PostgreSQL-функций
 *  – Auth      : signUp / signIn / signOut / getUser / setToken
 *  – Storage   : upload (bytes/base64) / getPublicUrl / delete
 *
 * Все методы — синхронные, вызываются из фонового потока
 * (JS делает Android.supabase*() в worker / async).
 * Каждый шаг логируется через AppLogger.
 */
public class SupabaseClient {

    private static final String TAG = "Supabase";

    // ── Константы подключения ────────────────────────────────────────────────
    public static final String URL = "https://mjonazsosajvgevqllzs.supabase.co";
    public static final String ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1qb25henNvc2FqdmdldnFsbHpzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM2NjAwNDMsImV4cCI6MjA4OTIzNjA0M30" +
            ".yUB_HRQSeh3TeOseZ4_ARNiBuklr5AoEbBgvJJl5p3Y";

    private static final MediaType JSON_MT =
            MediaType.get("application/json; charset=utf-8");

    // ── Поля ─────────────────────────────────────────────────────────────────
    private static volatile SupabaseClient instance;

    private final OkHttpClient http;
    private final AppLogger    log;
    private volatile String    authToken = null;   // JWT после signIn/signUp

    // ── Singleton ─────────────────────────────────────────────────────────────
    public static SupabaseClient get(Context ctx, AppLogger log) {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) instance = new SupabaseClient(ctx, log);
            }
        }
        return instance;
    }

    private SupabaseClient(Context ctx, AppLogger log) {
        this.log  = log;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30,  TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        log.i(TAG, "▶ SupabaseClient готов [" + URL + "]");
        log.i(TAG, "  anonKey=" + ANON_KEY.substring(0, 20) + "...");
    }

    // ── Управление токеном ────────────────────────────────────────────────────

    /** Установить JWT вручную (например, из SharedPreferences при рестарте). */
    public void setAuthToken(String token) {
        this.authToken = token;
        if (token != null) {
            log.i(TAG, "setAuthToken: токен установлен (" + token.length() + " символов)");
        } else {
            log.i(TAG, "setAuthToken: токен сброшен (null)");
        }
    }

    public String getAuthToken() { return authToken; }

    // ── Построитель запросов ──────────────────────────────────────────────────

    /** Базовые заголовки Supabase для всех запросов. */
    private Request.Builder base(String path) {
        String bearer = (authToken != null && !authToken.isEmpty()) ? authToken : ANON_KEY;
        return new Request.Builder()
                .url(URL + path)
                .header("apikey",        ANON_KEY)
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type",  "application/json");
    }

    /** Выполнить запрос и вернуть JSON: {ok, status, body, error?}. */
    private String exec(Request req) {
        String method  = req.method();
        String urlPath = req.url().encodedPath() +
                (req.url().encodedQuery() != null ? "?" + req.url().encodedQuery() : "");
        log.i(TAG, "→ " + method + " " + urlPath);
        long t0 = System.currentTimeMillis();
        try (Response resp = http.newCall(req).execute()) {
            long ms     = System.currentTimeMillis() - t0;
            int  status = resp.code();
            ResponseBody rb   = resp.body();
            String       body = rb != null ? rb.string() : "";
            boolean ok = status >= 200 && status < 300;

            if (ok) {
                log.i(TAG, "← " + status + " OK (" + ms + "ms) " + urlPath
                        + " | body_len=" + body.length());
            } else {
                // Логируем первые 300 символов тела ошибки
                String snippet = body.length() > 300 ? body.substring(0, 300) + "…" : body;
                log.e(TAG, "← " + status + " ERR (" + ms + "ms) " + urlPath
                        + " | " + snippet);
            }

            JSONObject res = new JSONObject();
            res.put("ok",     ok);
            res.put("status", status);
            res.put("body",   body);
            return res.toString();

        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            log.e(TAG, "← EXCEPTION (" + ms + "ms) " + urlPath + " | " + e.getMessage());
            try {
                JSONObject err = new JSONObject();
                err.put("ok",     false);
                err.put("status", 0);
                err.put("body",   "");
                err.put("error",  e.getMessage());
                return err.toString();
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"unknown\"}";
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATABASE — REST API
    // ════════════════════════════════════════════════════════════════════════

    /**
     * SELECT — GET /rest/v1/{table}?{query}
     *
     * Примеры query:
     *   "select=*"
     *   "select=id,name&status=eq.active&order=created_at.desc&limit=50"
     *   "select=*&id=eq.42"
     */
    public String select(String table, String query) {
        log.i(TAG, "select ▸ table=" + table + " query=" + query);
        String path = "/rest/v1/" + table +
                (query != null && !query.isEmpty() ? "?" + query : "");
        Request req = base(path)
                .header("Prefer", "return=representation")
                .get()
                .build();
        return exec(req);
    }

    /**
     * INSERT — POST /rest/v1/{table}
     * json — одиночный объект "{…}" или массив "[{…},{…}]"
     */
    public String insert(String table, String json) {
        String preview = json.length() > 150 ? json.substring(0, 150) + "…" : json;
        log.i(TAG, "insert ▸ table=" + table + " body=" + preview);
        Request req = base("/rest/v1/" + table)
                .header("Prefer", "return=representation")
                .post(RequestBody.create(json, JSON_MT))
                .build();
        return exec(req);
    }

    /**
     * UPDATE — PATCH /rest/v1/{table}?{filter}
     * filter пример: "id=eq.5"  или  "user_id=eq.42&status=eq.pending"
     */
    public String update(String table, String filter, String json) {
        String preview = json.length() > 150 ? json.substring(0, 150) + "…" : json;
        log.i(TAG, "update ▸ table=" + table + " filter=" + filter + " body=" + preview);
        String path = "/rest/v1/" + table +
                (filter != null && !filter.isEmpty() ? "?" + filter : "");
        Request req = base(path)
                .header("Prefer", "return=representation")
                .patch(RequestBody.create(json, JSON_MT))
                .build();
        return exec(req);
    }

    /**
     * UPSERT — POST /rest/v1/{table} с Prefer: resolution=merge-duplicates
     * Вставляет, если нет; обновляет, если есть (по primary key).
     */
    public String upsert(String table, String json) {
        String preview = json.length() > 150 ? json.substring(0, 150) + "…" : json;
        log.i(TAG, "upsert ▸ table=" + table + " body=" + preview);
        Request req = base("/rest/v1/" + table)
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(RequestBody.create(json, JSON_MT))
                .build();
        return exec(req);
    }

    /**
     * DELETE — DELETE /rest/v1/{table}?{filter}
     * filter обязателен во избежание удаления всей таблицы.
     */
    public String delete(String table, String filter) {
        log.i(TAG, "delete ▸ table=" + table + " filter=" + filter);
        if (filter == null || filter.isEmpty()) {
            log.e(TAG, "delete ОТКЛОНЁН: filter не задан (защита от удаления всей таблицы)");
            return "{\"ok\":false,\"error\":\"filter required for delete\"}";
        }
        String path = "/rest/v1/" + table + "?" + filter;
        Request req = base(path)
                .header("Prefer", "return=representation")
                .delete()
                .build();
        return exec(req);
    }

    // ════════════════════════════════════════════════════════════════════════
    // RPC — PostgreSQL-функции
    // ════════════════════════════════════════════════════════════════════════

    /**
     * RPC — POST /rest/v1/rpc/{function}
     * paramsJson — JSON-объект с аргументами функции, например:
     *   "{\"user_id\": 42, \"amount\": 100}"
     */
    public String rpc(String function, String paramsJson) {
        String body = (paramsJson == null || paramsJson.isEmpty()) ? "{}" : paramsJson;
        log.i(TAG, "rpc ▸ function=" + function + " params=" + body);
        Request req = base("/rest/v1/rpc/" + function)
                .post(RequestBody.create(body, JSON_MT))
                .build();
        return exec(req);
    }

    // ════════════════════════════════════════════════════════════════════════
    // AUTH
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Регистрация — POST /auth/v1/signup
     * Возвращает {ok, status, body} — в body.access_token будет JWT.
     */
    public String signUp(String email, String password) {
        log.i(TAG, "signUp ▸ email=" + email);
        try {
            JSONObject b = new JSONObject();
            b.put("email",    email);
            b.put("password", password);
            Request req = base("/auth/v1/signup")
                    .post(RequestBody.create(b.toString(), JSON_MT))
                    .build();
            String result = exec(req);
            parseAndSaveToken(result, "signUp");
            return result;
        } catch (Exception e) {
            log.e(TAG, "signUp ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Вход — POST /auth/v1/token?grant_type=password
     * После успеха authToken автоматически устанавливается.
     */
    public String signIn(String email, String password) {
        log.i(TAG, "signIn ▸ email=" + email);
        try {
            JSONObject b = new JSONObject();
            b.put("email",    email);
            b.put("password", password);
            Request req = base("/auth/v1/token?grant_type=password")
                    .post(RequestBody.create(b.toString(), JSON_MT))
                    .build();
            String result = exec(req);
            parseAndSaveToken(result, "signIn");
            return result;
        } catch (Exception e) {
            log.e(TAG, "signIn ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Выход — POST /auth/v1/logout
     * Сбрасывает локальный authToken.
     */
    public String signOut() {
        log.i(TAG, "signOut ▸");
        Request req = base("/auth/v1/logout")
                .post(RequestBody.create("{}", JSON_MT))
                .build();
        String result = exec(req);
        authToken = null;
        log.i(TAG, "signOut: authToken сброшен");
        return result;
    }

    /**
     * Получить текущего пользователя — GET /auth/v1/user
     * Требует действующего JWT.
     */
    public String getUser() {
        log.i(TAG, "getUser ▸ " + (authToken != null ? "token_set" : "NO TOKEN"));
        Request req = base("/auth/v1/user").get().build();
        return exec(req);
    }

    /** Извлекает access_token из ответа auth и сохраняет в authToken. */
    private void parseAndSaveToken(String result, String src) {
        try {
            JSONObject res  = new JSONObject(result);
            String     body = res.optString("body", "");
            if (!body.isEmpty()) {
                JSONObject parsed = new JSONObject(body);
                String token = parsed.optString("access_token", null);
                if (token != null && !token.isEmpty()) {
                    authToken = token;
                    log.i(TAG, src + ": access_token сохранён (" + token.length() + " chars)");
                } else {
                    log.w(TAG, src + ": access_token не найден в ответе");
                }
            }
        } catch (Exception e) {
            log.w(TAG, src + ": не удалось извлечь токен: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // STORAGE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Загрузить файл из raw bytes — POST /storage/v1/object/{bucket}/{path}
     */
    public String storageUpload(String bucket, String path, byte[] data, String mimeType) {
        log.i(TAG, "storageUpload ▸ bucket=" + bucket + " path=" + path
                + " size=" + data.length + " mime=" + mimeType);
        MediaType mt = MediaType.parse(
                mimeType != null ? mimeType : "application/octet-stream");
        Request req = base("/storage/v1/object/" + bucket + "/" + path)
                .header("Content-Type", mimeType != null ? mimeType : "application/octet-stream")
                .post(RequestBody.create(data, mt))
                .build();
        return exec(req);
    }

    /**
     * Загрузить файл из Base64-строки.
     * mimeType пример: "image/jpeg", "image/png", "audio/mpeg"
     */
    public String storageUploadBase64(String bucket, String path,
                                      String base64Data, String mimeType) {
        log.i(TAG, "storageUploadBase64 ▸ bucket=" + bucket + " path=" + path
                + " b64_len=" + base64Data.length() + " mime=" + mimeType);
        try {
            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
            log.i(TAG, "storageUploadBase64: декодировано " + data.length + " байт");
            return storageUpload(bucket, path, data, mimeType);
        } catch (Exception e) {
            log.e(TAG, "storageUploadBase64 ошибка декодирования: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    /**
     * Получить публичный URL файла (bucket должен быть public).
     * Не делает сетевого запроса — просто формирует строку.
     */
    public String storageGetPublicUrl(String bucket, String path) {
        String url = URL + "/storage/v1/object/public/" + bucket + "/" + path;
        log.i(TAG, "storageGetPublicUrl ▸ " + url);
        return url;
    }

    /**
     * Удалить файл из Storage — DELETE /storage/v1/object/{bucket}/{path}
     */
    public String storageDelete(String bucket, String path) {
        log.i(TAG, "storageDelete ▸ bucket=" + bucket + " path=" + path);
        Request req = base("/storage/v1/object/" + bucket + "/" + path)
                .delete()
                .build();
        return exec(req);
    }

    /**
     * Получить список файлов в папке — POST /storage/v1/object/list/{bucket}
     * prefix — папка внутри бакета, например "avatars/"
     */
    public String storageList(String bucket, String prefix) {
        log.i(TAG, "storageList ▸ bucket=" + bucket + " prefix=" + prefix);
        try {
            JSONObject b = new JSONObject();
            b.put("prefix", prefix != null ? prefix : "");
            b.put("limit",  100);
            b.put("offset", 0);
            Request req = base("/storage/v1/object/list/" + bucket)
                    .post(RequestBody.create(b.toString(), JSON_MT))
                    .build();
            return exec(req);
        } catch (Exception e) {
            log.e(TAG, "storageList ошибка: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Утилиты
    // ════════════════════════════════════════════════════════════════════════

    private String errorJson(String msg) {
        try {
            JSONObject err = new JSONObject();
            err.put("ok",    false);
            err.put("status", 0);
            err.put("body",  "");
            err.put("error", msg != null ? msg : "unknown");
            return err.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }
}
