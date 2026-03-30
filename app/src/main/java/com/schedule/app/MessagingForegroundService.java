package com.schedule.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Фоновый сервис сообщений — работает даже когда приложение закрыто.
 *
 * Принцип работы (как в Telegram):
 *  – Foreground-сервис с минимальным уведомлением (без звука, без иконки в статус-баре)
 *  – Каждые POLL_INTERVAL_MS опрашивает Supabase на новые входящие сообщения
 *  – Показывает push-уведомление при новом сообщении (с открытием конкретного чата)
 *  – START_STICKY + AlarmManager restart = переживает убийство Android
 *
 * Ключи SharedPreferences используются одинаковые с MainActivity чтобы lastTs
 * синхронизировался: когда пользователь открыл приложение — MainActivity.onResume()
 * пишет lastTs = now, и сервис не будет повторно уведомлять о прочитанных сообщениях.
 */
public class MessagingForegroundService extends Service {

    public static final String TAG             = "BgService";
    public static final String CHANNEL_ID      = "sapp_bg_connection";
    public static final String CHANNEL_NAME    = "Фоновое соединение";
    public static final int    FG_NOTIF_ID     = 9001;
    public static final String PREF_BG_ENABLED = "bg_service_enabled";

    // ── Те же ключи что и в MainActivity — чтобы lastTs был общим ──────────
    private static final String PREFS         = "schedule_prefs";
    private static final String KEY_USERNAME  = "sb_username";       // совпадает с MainActivity.PREF_SB_USER
    private static final String KEY_LAST_TS   = "sb_last_notif_ts";  // совпадает с MainActivity.PREF_SB_LAST_TS
    private static final String KEY_GROUPS    = "user_groups_";      // prefix, далее + username

    // ── Тайминги ─────────────────────────────────────────────────────────────
    // 15 секунд — баланс между оперативностью и расходом батареи
    private static final int POLL_INTERVAL_MS = 15_000;

    // ── Уведомления ──────────────────────────────────────────────────────────
    private static final String CH_MESSAGES  = "sapp_messages";  // высокий приоритет
    private static final int    BASE_NOTIF_ID = 3000;

    private Handler           pollHandler;
    private Runnable          pollRunnable;
    private AppLogger         log;
    private SharedPreferences prefs;
    private boolean           isPolling = false;

    // Дедупликация: не показываем одно и то же уведомление дважды
    // Ключ: "sender:text" — уникальный per-сообщение
    private final Set<String> shownKeys = new HashSet<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        log   = AppLogger.get(this);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        createChannels();
        log.i(TAG, "Service created");
        // startForeground ОБЯЗАН быть вызван в течение 5 секунд от onCreate (Android 12+)
        startForeground(FG_NOTIF_ID, buildFgNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.i(TAG, "onStartCommand flags=" + flags);
        if (!isPolling) startPolling();
        // START_STICKY: Android перезапустит сервис если убьёт (передаст null Intent)
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Пользователь смахнул приложение из недавних — переплановываем перезапуск
        log.i(TAG, "onTaskRemoved — scheduleRestart");
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
        log.i(TAG, "Service destroyed — scheduling restart");
        scheduleRestart();
    }

    // ── Restart on kill ───────────────────────────────────────────────────────

    private void scheduleRestart() {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), MessagingForegroundService.class);
            PendingIntent pi = PendingIntent.getService(
                    getApplicationContext(), 1, restartIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager am =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            long triggerAt = System.currentTimeMillis() + 5_000L;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            log.i(TAG, "Restart scheduled in 5s");
        } catch (Exception e) {
            log.w(TAG, "scheduleRestart error: " + e.getMessage());
        }
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        isPolling   = true;
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;
                new Thread(MessagingForegroundService.this::doPoll).start();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        // Первый тик через 2 секунды после старта
        pollHandler.postDelayed(pollRunnable, 2_000);
        log.i(TAG, "Polling started every " + POLL_INTERVAL_MS + "ms");
    }

    private void stopPolling() {
        isPolling = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler = null;
        }
    }

    // ── Supabase poll ─────────────────────────────────────────────────────────

    private void doPoll() {
        try {
            String username = prefs.getString(KEY_USERNAME, "");
            if (username == null || username.isEmpty()) {
                log.w(TAG, "doPoll: username не задан, пропускаю");
                return;
            }

            // Читаем lastTs — синхронизирован с MainActivity (общий ключ)
            long lastTs = prefs.getLong(KEY_LAST_TS, 0);
            if (lastTs == 0) {
                // Первый запуск — смотрим за последние 5 минут
                lastTs = System.currentTimeMillis() - 5 * 60_000L;
            }

            long maxTs = lastTs;

            // Сообщения группируем по отправителю для объединения в одно уведомление
            LinkedHashMap<String, List<String>> byUser = new LinkedHashMap<>();

            // 1. Личные сообщения (to_user = username)
            maxTs = pollMessages(
                SupabaseClient.URL + "/rest/v1/messages"
                    + "?select=from_user,text,ts,extra,sticker"
                    + "&to_user=eq." + urlEncode(username)
                    + "&ts=gt." + lastTs
                    + "&order=ts.asc&limit=50",
                username, maxTs, byUser, false
            );

            // 2. Групповые сообщения
            String groupsJson = prefs.getString(KEY_GROUPS + username, "[]");
            List<String> groupIds = parseGroupIds(groupsJson);
            for (String groupId : groupIds) {
                maxTs = pollMessages(
                    SupabaseClient.URL + "/rest/v1/messages"
                        + "?select=from_user,text,ts,extra,sticker,chat_key"
                        + "&chat_key=eq." + urlEncode("group_" + groupId)
                        + "&to_user=eq.__broadcast__"
                        + "&from_user=neq." + urlEncode(username)
                        + "&ts=gt." + lastTs
                        + "&order=ts.asc&limit=20",
                    groupId, maxTs, byUser, true
                );
            }

            // ВАЖНО: всегда обновляем lastTs, даже если сообщений нет.
            // Без этого при следующем поллинге мы снова смотрим с того же starTs
            // и можем повторно уведомить о уже прочитанных сообщениях.
            long newTs = Math.max(maxTs, System.currentTimeMillis());
            // Обновляем только если не было обновлено приложением (MainActivity.onResume)
            // Берём максимум чтобы не откатиться назад
            long storedTs = prefs.getLong(KEY_LAST_TS, 0);
            if (newTs > storedTs) {
                prefs.edit().putLong(KEY_LAST_TS, newTs).apply();
            }

            if (!byUser.isEmpty()) {
                showNotifications(byUser);
            }

        } catch (Exception e) {
            log.w(TAG, "doPoll error: " + e.getMessage());
        }
    }

    private long pollMessages(String urlStr, String chatId, long currentMaxTs,
            LinkedHashMap<String, List<String>> byUser, boolean isGroup) {
        long maxTs = currentMaxTs;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setRequestProperty("apikey",        SupabaseClient.ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY);
            conn.setRequestProperty("Accept",        "application/json");

            int status = conn.getResponseCode();
            if (status != 200) { conn.disconnect(); return maxTs; }

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close(); conn.disconnect();

            String body = baos.toString("UTF-8").trim();
            if (body.isEmpty() || body.equals("[]")) return maxTs;

            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject msg = arr.getJSONObject(i);
                String from  = msg.optString("from_user", "?");
                long   ts    = msg.optLong("ts", 0);
                String text  = msg.optString("text", "");
                String extra = msg.optString("extra", "");

                // Обновляем maxTs
                if (ts > maxTs) maxTs = ts;

                // Пропускаем служебные (реакции, прочтения)
                if (!extra.isEmpty()) {
                    try {
                        JSONObject ex = new JSONObject(extra);
                        String tp = ex.optString("type", "");
                        if ("reaction".equals(tp) || "read_receipt".equals(tp)) continue;
                    } catch (Exception ignored) {}
                }

                // Формируем текст превью
                text = buildPreview(text, extra, msg.optString("sticker", ""));

                // Дедупликация
                String dedupeKey = chatId + ":" + from + ":" + ts;
                if (shownKeys.contains(dedupeKey)) continue;
                shownKeys.add(dedupeKey);
                // Не даём Set расти бесконечно
                if (shownKeys.size() > 500) shownKeys.clear();

                // Для личных — ключ отправитель; для групп — "from в group_id"
                String displayKey = isGroup ? from + " в " + chatId : from;
                byUser.computeIfAbsent(displayKey, k -> new ArrayList<>()).add(text);
            }

        } catch (Exception e) {
            log.w(TAG, "pollMessages error for " + chatId + ": " + e.getMessage());
        }
        return maxTs;
    }

    private String buildPreview(String text, String extra, String sticker) {
        if (text.startsWith("ENC:")) return "🔐 Зашифрованное сообщение";
        if (!text.isEmpty() && !text.startsWith("{")) {
            return text.length() > 80 ? text.substring(0, 77) + "…" : text;
        }
        if (!extra.isEmpty()) {
            try {
                JSONObject ex = new JSONObject(extra);
                String ft = ex.optString("fileType", "");
                String tp = ex.optString("type", "");
                if ("group_invite".equals(tp)) return "👥 Добавил(а) в группу";
                if ("voice".equals(ft))        return "🎤 Голосовое";
                if ("circle".equals(ft))       return "⭕ Видеосообщение";
                if ("video".equals(ft))        return "🎬 Видео";
                if (ex.has("image"))           return "📷 Фото";
                if ("file".equals(ft))         return "📎 " + ex.optString("fileName", "Файл");
                if (!ft.isEmpty())             return "📎 Файл";
            } catch (Exception ignored) {}
        }
        if (!sticker.isEmpty()) return sticker + " Стикер";
        return "Новое сообщение";
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void showNotifications(LinkedHashMap<String, List<String>> byUser) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log.w(TAG, "POST_NOTIFICATIONS permission not granted");
                return;
            }
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        for (Map.Entry<String, List<String>> entry : byUser.entrySet()) {
            String from  = entry.getKey();
            List<String> msgs = entry.getValue();
            String preview = msgs.size() == 1
                ? msgs.get(0)
                : msgs.size() + " новых сообщений";
            if (preview.length() > 100) preview = preview.substring(0, 97) + "…";

            // Тап открывает конкретный чат
            Intent tapIntent = new Intent(this, MainActivity.class);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            tapIntent.putExtra("open_chat", from);
            int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(
                this, Math.abs(from.hashCode()), tapIntent, piFlags);

            Notification notif = new NotificationCompat.Builder(this, CH_MESSAGES)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("@" + from)
                .setContentText(preview)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(preview))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVibrate(new long[]{0, 200, 80, 200})
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setGroup("sapp_msg_group")
                .build();

            // Стабильный ID — одно уведомление на отправителя (новое заменяет старое)
            int notifId = BASE_NOTIF_ID + Math.abs(from.hashCode() % 1000);
            nm.notify(notifId, notif);
            log.i(TAG, "Push @" + from + " (" + msgs.size() + " сообщ.): " + preview);
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    /**
     * Минимальное постоянное уведомление — как у Telegram.
     * IMPORTANCE_MIN: нет иконки в статус-баре, нет звука, нет вибрации.
     * Пользователь не замечает его при обычном использовании.
     */
    public Notification buildFgNotification() {
        Intent tap = new Intent(this, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("ScheduleApp")
            .setContentText("Ожидание сообщений")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    // ── Channels ──────────────────────────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Канал сервиса — IMPORTANCE_MIN (невидимый, без звука)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel chSvc = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN);
            chSvc.setDescription("Фоновое соединение ScheduleApp");
            chSvc.setShowBadge(false);
            chSvc.setSound(null, null);
            chSvc.enableVibration(false);
            nm.createNotificationChannel(chSvc);
        }

        // Канал сообщений — HIGH (пуш-уведомления о новых сообщениях)
        if (nm.getNotificationChannel(CH_MESSAGES) == null) {
            NotificationChannel chMsg = new NotificationChannel(
                CH_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH);
            chMsg.setDescription("Входящие сообщения в чате");
            chMsg.enableVibration(true);
            chMsg.setVibrationPattern(new long[]{0, 200, 80, 200});
            nm.createNotificationChannel(chMsg);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> parseGroupIds(String json) {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject g = arr.optJSONObject(i);
                if (g == null) continue;
                String id = g.optString("id", "");
                if (id.startsWith("grp_")) ids.add(id);
            }
        } catch (Exception ignored) {}
        return ids;
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ── Static helpers (вызываются из MainActivity) ───────────────────────────

    /**
     * Вызывать из MainActivity.onResume() — отмечает все сообщения как прочитанные.
     * Сервис больше не будет уведомлять о том что пользователь уже увидел.
     * Примечание: MainActivity.onResume() уже делает это через PREF_SB_LAST_TS — этот
     * метод существует как явный API для ясности кода.
     */
    public static void markRead(Context ctx) {
        ctx.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("sb_last_notif_ts", System.currentTimeMillis())
            .apply();
    }
}
