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

    private static final int POLL_INTERVAL_MS  = 5000;

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
        prefs = getSharedPreferences("sapp_prefs", MODE_PRIVATE);
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
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScheduleApp:BgService");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(60 * 60 * 1000L); // максимум 1 час, потом автообновляем
                log.i(TAG, "WakeLock acquired");
            }
        } catch (Exception e) {
            log.w(TAG, "WakeLock acquire failed: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                log.i(TAG, "WakeLock released");
            }
        } catch (Exception e) {
            log.w(TAG, "WakeLock release error: " + e.getMessage());
        }
    }

    /** Перезапрашивает WakeLock раз в 30 минут чтобы не истёк. */
    private void renewWakeLockIfNeeded() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                acquireWakeLock();
            }
        } catch (Exception e) {}
    }

    // ── Restart on kill ───────────────────────────────────────────────────────

    private void scheduleRestart() {
        // Используем AlarmManager для перезапуска через 3 секунды
        try {
            Intent restartIntent = new Intent(getApplicationContext(), MessagingForegroundService.class);
            android.app.PendingIntent pi;
            pi = android.app.PendingIntent.getService(
                    getApplicationContext(), 1, restartIntent,
                    android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager am =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) {
                am.set(android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 3000, pi);
                log.i(TAG, "Restart scheduled in 3s");
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
                            String tp = ex.optString("type", "");
                            if ("group_invite".equals(tp)) {
                                text = "👥 Добавил(а) в группу";
                            } else switch (ft) {
                                case "voice":  text = "🎤 Голосовое"; break;
                                case "circle": text = "⭕ Видеосообщение"; break;
                                case "video":  text = "🎬 Видео"; break;
                                case "file":   text = "📎 " + ex.optString("fileName","Файл"); break;
                                default:       if (!ft.isEmpty()) text = "📎 Файл";
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
            log.i(TAG, "Push от @" + from + " (" + texts.size() + " сообщ.)");
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Фоновое соединение ScheduleApp");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
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
