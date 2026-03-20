package com.schedule.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

/**
 * Постоянный фоновый сервис — работает даже когда приложение закрыто.
 * START_STICKY + WakeLock + startForeground = гарантированная работа.
 * Аналог Telegram background connection.
 */
public class MessagingForegroundService extends Service {

    public static final String TAG             = "BgService";
    public static final String CHANNEL_ID      = "sapp_bg_connection";
    public static final String CHANNEL_NAME    = "Фоновое соединение";
    public static final int    FG_NOTIF_ID     = 9001;
    public static final String PREF_BG_ENABLED = "bg_service_enabled";
    public static final String PREF_SB_USER    = "sb_username";
    public static final String PREF_SB_LAST_TS = "sb_last_notif_ts";

    // 30 секунд в фоне — разумный баланс между оперативностью и трафиком
    private static final int POLL_INTERVAL_MS  = 30_000;

    private Handler           pollHandler;
    private Runnable          pollRunnable;
    private AppLogger         log;
    private SharedPreferences prefs;
    private int               msgNotifCounter  = 2000;
    private boolean           isPolling        = false;
    private PowerManager.WakeLock wakeLock;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        log   = AppLogger.get(this);
        // ВАЖНО: используем тот же файл настроек что и MainActivity ("schedule_prefs")
        // чтобы username и lastTs были общими между сервисом и активити
        prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE);
        log.i(TAG, "Service created");
        createNotificationChannel();
        // startForeground ОБЯЗАН быть вызван в течение 5 секунд от onCreate
        startForeground(FG_NOTIF_ID, buildFgNotification("Подключено · ожидаю сообщения"));
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.i(TAG, "onStartCommand flags=" + flags);
        // Обновляем уведомление — оно могло пропасть
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(FG_NOTIF_ID, buildFgNotification("Подключено · ожидаю сообщения"));

        if (!isPolling) startPolling();

        // START_STICKY: Android перезапустит сервис если убьёт его
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
        releaseWakeLock();
        log.i(TAG, "Service destroyed — scheduling restart");
        scheduleRestart();
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    private void acquireWakeLock() {
        // WakeLock не используем — он держит CPU активным и тратит батарею.
        // Android сам управляет пробуждением через Doze + AlarmManager.
        log.i(TAG, "WakeLock: отключён для экономии батареи и трафика");
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
    }

    private void renewWakeLockIfNeeded() { /* не используется */ }

    // ── Restart on kill ───────────────────────────────────────────────────────

    private void scheduleRestart() {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), MessagingForegroundService.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getService(
                    getApplicationContext(), 1, restartIntent,
                    android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager am =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) {
                long triggerAt = System.currentTimeMillis() + 3000;
                // setExactAndAllowWhileIdle работает даже в Doze-режиме (Android 6+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
                log.i(TAG, "Restart scheduled in 3s (Doze-safe)");
            }
        } catch (Exception e) {
            log.w(TAG, "scheduleRestart error: " + e.getMessage());
        }
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        isPolling   = true;
        pollHandler = new Handler(Looper.getMainLooper());
        int[] tickCount = {0};
        pollRunnable = () -> {
            tickCount[0]++;
            // Обновляем WakeLock каждые 30 тиков (~2.5 мин)
            if (tickCount[0] % 30 == 0) renewWakeLockIfNeeded();
            new Thread(this::doPoll).start();
            pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        };
        pollHandler.postDelayed(pollRunnable, 1000);
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
            String username = prefs.getString(PREF_SB_USER, "");
            if (username == null || username.isEmpty()) {
                log.w(TAG, "doPoll: username не задан, пропускаю");
                return;
            }
            long lastTs = prefs.getLong(PREF_SB_LAST_TS, 0);
            if (lastTs == 0) lastTs = System.currentTimeMillis() - 60_000L;

            // ── 1. Личные сообщения (to_user = username) ───────────────────
            String urlPersonal = SupabaseClient.URL
                + "/rest/v1/messages?select=from_user,text,ts,extra,sticker,chat_key"
                + "&to_user=eq." + java.net.URLEncoder.encode(username, "UTF-8")
                + "&ts=gt." + lastTs
                + "&order=ts.asc"
                + "&limit=50";

            // ── 2. Групповые сообщения (to_user = __broadcast__) ───────────
            // Загружаем список групп пользователя из SharedPreferences
            String groupsJson = prefs.getString("user_groups_" + username, "[]");
            java.util.List<String> groupIds = parseGroupIds(groupsJson);

            java.util.LinkedHashMap<String, java.util.List<String>> byUser =
                new java.util.LinkedHashMap<>();
            long maxTs = lastTs;

            // Поллим личные сообщения
            maxTs = pollEndpoint(urlPersonal, username, lastTs, maxTs, byUser, false);

            // Поллим каждую группу
            for (String groupId : groupIds) {
                String chatKey = "group_" + groupId;
                String urlGroup = SupabaseClient.URL
                    + "/rest/v1/messages?select=from_user,text,ts,extra,sticker,chat_key"
                    + "&chat_key=eq." + java.net.URLEncoder.encode(chatKey, "UTF-8")
                    + "&to_user=eq.__broadcast__"
                    + "&from_user=neq." + java.net.URLEncoder.encode(username, "UTF-8")
                    + "&ts=gt." + lastTs
                    + "&order=ts.asc"
                    + "&limit=20";
                // groupId используем как ключ — уведомление будет «в группе X»
                maxTs = pollEndpoint(urlGroup, groupId, lastTs, maxTs, byUser, true);
            }

            if (maxTs > lastTs) {
                prefs.edit().putLong(PREF_SB_LAST_TS, maxTs + 1).apply();
            }

            if (!byUser.isEmpty()) {
                showMessageNotifications(byUser);
            }

        } catch (Exception e) {
            log.w(TAG, "poll error: " + e.getMessage());
        }
    }

    /**
     * Загружает сообщения с одного endpoint и добавляет в byUser.
     * @param chatId   для личных — username отправителя; для групп — groupId
     * @param isGroup  true = сообщение из группы, в title покажем «Группа»
     * @return новый maxTs
     */
    private long pollEndpoint(String urlStr, String chatId, long lastTs, long maxTs,
            java.util.LinkedHashMap<String, java.util.List<String>> byUser,
            boolean isGroup) throws Exception {

        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn =
            (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("apikey",        SupabaseClient.ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY);
        conn.setRequestProperty("Accept",        "application/json");

        int status = conn.getResponseCode();
        if (status != 200) { conn.disconnect(); return maxTs; }

        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close(); conn.disconnect();

        String body = baos.toString("UTF-8").trim();
        if (body.isEmpty() || body.equals("[]")) return maxTs;

        org.json.JSONArray arr = new org.json.JSONArray(body);
        if (arr.length() == 0) return maxTs;

        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject msg = arr.getJSONObject(i);
            String from  = msg.optString("from_user", "?");
            long   ts    = msg.optLong("ts", 0);
            String text  = msg.optString("text", "");
            String extra = msg.optString("extra", "");

            // Пропускаем служебные
            if (!extra.isEmpty()) {
                try {
                    org.json.JSONObject ex = new org.json.JSONObject(extra);
                    String tp = ex.optString("type", "");
                    if ("reaction".equals(tp) || "read_receipt".equals(tp)) {
                        if (ts > maxTs) maxTs = ts;
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            if (text.startsWith("ENC:")) {
                text = "🔐 Зашифрованное сообщение";
            } else if (text.isEmpty() || text.startsWith("{")) {
                if (!extra.isEmpty()) {
                    try {
                        org.json.JSONObject ex = new org.json.JSONObject(extra);
                        String ft = ex.optString("fileType", "");
                        String tp = ex.optString("type", "");
                        if      ("group_invite".equals(tp)) text = "👥 Добавил(а) в группу";
                        else if ("voice".equals(ft))        text = "🎤 Голосовое";
                        else if ("circle".equals(ft))       text = "⭕ Видеосообщение";
                        else if ("video".equals(ft))        text = "🎬 Видео";
                        else if (ex.has("image"))           text = "📷 Фото";
                        else if ("file".equals(ft))         text = "📎 " + ex.optString("fileName", "Файл");
                        else if (!ft.isEmpty())             text = "📎 Файл";
                    } catch (Exception ignored) {}
                }
                if (text.isEmpty()) {
                    String sticker = msg.optString("sticker", "");
                    text = sticker.isEmpty() ? "📎 Файл" : sticker + " Стикер";
                }
            }

            if (ts > maxTs) maxTs = ts;

            // Для групп ключ = "grp:groupId:from" чтобы объединять по группе+отправителю
            String notifKey = isGroup ? "grp:" + chatId + ":" + from : from;
            // Для групп показываем «from в группе», для личных — просто from
            String displayKey = isGroup ? from + " в группе " + chatId : from;
            byUser.computeIfAbsent(displayKey, k -> new java.util.ArrayList<>()).add(text);
        }
        return maxTs;
    }

    /** Достаёт список group id из JSON-строки формата [{id:"grp_...",…},…] */
    private java.util.List<String> parseGroupIds(String json) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject g = arr.optJSONObject(i);
                if (g == null) continue;
                String id = g.optString("id", "");
                // Включаем только обычные группы, не публичную
                if (id.startsWith("grp_")) ids.add(id);
            }
        } catch (Exception ignored) {}
        return ids;
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void showMessageNotifications(
            java.util.LinkedHashMap<String, java.util.List<String>> byUser) {

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        }

        // Убеждаемся что канал создан (на случай если createNotificationChannel не вызвался)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mc = new NotificationChannel(
                "sapp_messages", "Сообщения", NotificationManager.IMPORTANCE_HIGH);
            mc.enableVibration(true);
            mc.setVibrationPattern(new long[]{0, 200, 80, 200});
            nm.createNotificationChannel(mc);
        }

        for (java.util.Map.Entry<String, java.util.List<String>> entry : byUser.entrySet()) {
            String from  = entry.getKey();
            java.util.List<String> texts = entry.getValue();
            String preview = texts.size() == 1 ? texts.get(0)
                           : texts.size() + " новых сообщений";
            if (preview.length() > 100) preview = preview.substring(0, 97) + "…";

            // Intent открывает конкретный чат при тапе на уведомление
            Intent tapIntent = new Intent(this, MainActivity.class);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            tapIntent.putExtra("open_chat", from);  // MainActivity.onNewIntent обработает
            int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            // requestCode уникален по sender чтобы у каждого отправителя свой PendingIntent
            PendingIntent pi = PendingIntent.getActivity(
                this, Math.abs(from.hashCode()), tapIntent, piFlags);

            Notification notif = new NotificationCompat.Builder(this, "sapp_messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("@" + from)
                .setContentText(preview)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(preview))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVibrate(new long[]{0, 200, 80, 200})
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setGroup("sapp_msg_group")
                .build();

            // Стабильный ID по sender: одно уведомление на человека (новое заменяет старое)
            int notifId = 3000 + Math.abs(from.hashCode() % 1000);
            nm.notify(notifId, notif);
            log.i(TAG, "Push от @" + from + " (" + texts.size() + " сообщ.): " + preview);
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Фоновое соединение ScheduleApp");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            // IMPORTANCE_MIN = не показывает иконку в статус-баре, не пикает
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public Notification buildFgNotification(String statusText) {
        Intent tap = new Intent(this, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("ScheduleApp")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
}
