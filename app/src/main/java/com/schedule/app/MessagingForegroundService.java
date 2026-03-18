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
import androidx.core.app.NotificationCompat;

/**
 * Фоновый сервис — держит постоянное соединение с Supabase даже когда
 * приложение закрыто (аналог Telegram background connection).
 *
 * Запускается через Android.startBackgroundService() из JS.
 * Показывает постоянное уведомление «ScheduleApp · Подключено».
 * Опрашивает Supabase каждые 5 сек и показывает push при новых сообщениях.
 */
public class MessagingForegroundService extends Service {

    public static final String TAG                  = "BgService";
    public static final String CHANNEL_ID           = "sapp_bg_connection";
    public static final String CHANNEL_NAME         = "Фоновое соединение";
    public static final int    FG_NOTIF_ID          = 9001;
    public static final String PREF_BG_ENABLED      = "bg_service_enabled";
    public static final String PREF_SB_USER         = "sb_username";
    public static final String PREF_SB_LAST_TS      = "sb_last_notif_ts";

    private static final int   POLL_INTERVAL_MS     = 5000;
    private static final int   RECONNECT_BACKOFF_MS = 30000;

    private Handler        pollHandler;
    private Runnable       pollRunnable;
    private AppLogger      log;
    private SharedPreferences prefs;
    private int            msgNotifCounter = 2000;
    private boolean        isPolling       = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        log   = AppLogger.get(this);
        prefs = getSharedPreferences("sapp_prefs", MODE_PRIVATE);
        log.i(TAG, "Service created");
        createNotificationChannel();
        startForeground(FG_NOTIF_ID, buildFgNotification("Подключено"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.i(TAG, "onStartCommand — запускаю поллинг");
        if (!isPolling) startPolling();
        return START_STICKY; // Android перезапустит сервис если убьёт
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
        log.i(TAG, "Service destroyed");
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        isPolling   = true;
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = () -> {
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

    // ── Supabase poll (выполняется в фоновом потоке) ──────────────────────────

    private void doPoll() {
        try {
            String username = prefs.getString(PREF_SB_USER, "");
            if (username == null || username.isEmpty()) return;

            long lastTs = prefs.getLong(PREF_SB_LAST_TS, System.currentTimeMillis() - 60000);

            String urlStr = SupabaseClient.URL
                + "/rest/v1/messages"
                + "?select=from_user,text,ts,extra"
                + "&to_user=eq." + java.net.URLEncoder.encode(username, "UTF-8")
                + "&ts=gt." + lastTs
                + "&order=ts.asc"
                + "&limit=50";

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
            if (status != 200) { conn.disconnect(); return; }

            java.io.InputStream is = conn.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close(); conn.disconnect();

            String body = baos.toString("UTF-8").trim();
            if (body.isEmpty() || body.equals("[]")) return;

            org.json.JSONArray arr = new org.json.JSONArray(body);
            if (arr.length() == 0) return;

            long maxTs = lastTs;
            java.util.LinkedHashMap<String, java.util.List<String>> byUser =
                new java.util.LinkedHashMap<>();

            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject msg = arr.getJSONObject(i);
                String from = msg.optString("from_user", "?");
                long   ts   = msg.optLong("ts", 0);
                String text = msg.optString("text", "");

                if (text.isEmpty() || text.startsWith("{")) {
                    String extra = msg.optString("extra", "");
                    if (!extra.isEmpty()) {
                        try {
                            org.json.JSONObject ex = new org.json.JSONObject(extra);
                            String ft = ex.optString("fileType", "");
                            switch (ft) {
                                case "voice":  text = "🎤 Голосовое сообщение"; break;
                                case "circle": text = "⭕ Видеосообщение"; break;
                                case "video":  text = "🎬 Видео"; break;
                                case "file":   text = "📎 " + ex.optString("fileName", "Файл"); break;
                                default:
                                    if (!ft.isEmpty()) text = "📎 " + ex.optString("fileName", "Медиафайл");
                            }
                        } catch (Exception ignored) {}
                    }
                    if (text.isEmpty()) text = "📎 Файл";
                }
                if (ts > maxTs) maxTs = ts;
                byUser.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(text);
            }

            prefs.edit().putLong(PREF_SB_LAST_TS, maxTs + 1).apply();
            showMessageNotifications(byUser);

        } catch (Exception e) {
            log.w(TAG, "poll error: " + e.getMessage());
        }
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

        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, piFlags);

        // Создаём канал для сообщений (если не создан)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel msgChannel = new NotificationChannel(
                "sapp_messages", "Сообщения",
                NotificationManager.IMPORTANCE_HIGH);
            msgChannel.enableVibration(true);
            msgChannel.setVibrationPattern(new long[]{0, 200, 80, 200});
            nm.createNotificationChannel(msgChannel);
        }

        for (java.util.Map.Entry<String, java.util.List<String>> entry : byUser.entrySet()) {
            String from  = entry.getKey();
            java.util.List<String> texts = entry.getValue();
            String preview = texts.size() == 1
                ? texts.get(0)
                : texts.size() + " новых сообщения";
            if (preview.length() > 100) preview = preview.substring(0, 97) + "…";

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
                .build();
            nm.notify(msgNotifCounter++, notif);
            log.i(TAG, "Уведомление от @" + from + " (" + texts.size() + " сообщ.)");
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW); // тихий — не мешает
            ch.setDescription("Фоновое соединение ScheduleApp");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** Строит постоянное уведомление в духе Telegram. */
    public Notification buildFgNotification(String statusText) {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent, piFlags);

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
