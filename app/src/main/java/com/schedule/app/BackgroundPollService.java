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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BackgroundPollService — живёт когда MainActivity уходит в фон или закрывается.
 *
 * Функции:
 *  1. Каждые POLL_INTERVAL_MS опрашивает Supabase на новые сообщения
 *  2. Показывает системные уведомления если пришли новые сообщения
 *  3. Выполняет очередь загрузок файлов (catbox.moe) в фоне
 *
 * Запуск:  startForegroundService(new Intent(this, BackgroundPollService.class))
 * Остановка: stopService(new Intent(this, BackgroundPollService.class))
 */
public class BackgroundPollService extends Service {

    private static final String TAG = "BgPollService";

    // ── Каналы уведомлений ──────────────────────────────────────────
    public static final String CH_MESSAGES = "sapp_messages";       // входящие сообщения (HIGH)
    public static final String CH_SERVICE  = "sapp_bg_service";     // ongoing (MIN / silent)
    public static final String CH_UPLOAD   = "sapp_uploads";        // прогресс загрузки (LOW)

    private static final int NOTIF_SERVICE_ID  = 1000; // ongoing
    private static final int NOTIF_MSG_BASE    = 2000; // base ID for message notifs
    private static final int NOTIF_UPLOAD_ID   = 3000; // upload progress

    // ── Тайминги ────────────────────────────────────────────────────
    private static final long POLL_INTERVAL_MS = 20_000L;   // 20 сек в фоне
    private static final long POLL_INTERVAL_ACTIVE = 8_000L; // 8 сек сразу после запуска

    // ── SharedPreferences ключи ─────────────────────────────────────
    static final String PREFS        = "schedule_prefs";
    static final String KEY_USERNAME = "push_username";
    static final String KEY_SB_URL   = "push_sb_url";
    static final String KEY_SB_KEY   = "push_sb_key";
    static final String KEY_LAST_TS  = "push_last_ts";
    static final String KEY_MUTED    = "push_muted";        // comma-sep мюченные чаты

    // ── Состояние ────────────────────────────────────────────────────
    private SharedPreferences prefs;
    private Handler           pollHandler;
    private Runnable          pollRunnable;
    private AppLogger         log;
    private ExecutorService   uploadExecutor;  // однопоточный — грузим последовательно
    private final Set<String> shownNotifKeys = new HashSet<>(); // дедупликация уведомлений
    private volatile boolean  running = false;

    // ── Callback-интерфейс для upload (вызывается из MainActivity) ──
    public interface UploadCallback {
        void onProgress(String jobId, int pct, String label);
        void onDone(String jobId, String url);
        void onError(String jobId, String error);
    }
    private static volatile UploadCallback uploadCallback = null;
    public static void setUploadCallback(UploadCallback cb) { uploadCallback = cb; }

    // ══════════════════════════════════════════════════════════════════
    @Override
    public void onCreate() {
        super.onCreate();
        log = AppLogger.get(this);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        uploadExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "upload-worker");
            t.setDaemon(false);
            return t;
        });
        createChannels();
        log.i(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.i(TAG, "onStartCommand flags=" + flags);

        // Обязательно startForeground сразу — иначе ANR на Android 12+
        startForeground(NOTIF_SERVICE_ID, buildServiceNotification("Ожидание сообщений…"));

        if (!running) {
            running = true;
            startPolling();
        }
        // START_STICKY: Android перезапустит сервис если убьёт его (передаёт null Intent)
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
        log.i(TAG, "onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ══════════════════════════════════════════════════════════════════
    // POLLING
    // ══════════════════════════════════════════════════════════════════

    private void startPolling() {
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                doPollAsync();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        // Первый тик быстрый
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_ACTIVE);
    }

    /** Запускает один цикл polling в фоновом потоке */
    private void doPollAsync() {
        String username = prefs.getString(KEY_USERNAME, null);
        String sbUrl    = prefs.getString(KEY_SB_URL, null);
        String sbKey    = prefs.getString(KEY_SB_KEY, null);
        if (username == null || sbUrl == null || sbKey == null) {
            log.w(TAG, "poll skipped: no config (username/url/key not set)");
            return;
        }
        long lastTs = prefs.getLong(KEY_LAST_TS, System.currentTimeMillis() - 5 * 60_000L);

        new Thread(() -> {
            try {
                String endpoint = sbUrl.replaceAll("/$", "")
                    + "/rest/v1/messages"
                    + "?select=*"
                    + "&to_user=eq." + urlEncode(username)
                    + "&ts=gt." + lastTs
                    + "&order=ts.asc"
                    + "&limit=20";

                String body = httpGet(endpoint, sbKey);
                if (body == null) return;

                JSONArray rows = new JSONArray(body);
                if (rows.length() == 0) return;

                long maxTs = lastTs;
                // Группируем по отправителю
                java.util.Map<String, java.util.List<JSONObject>> bySender = new java.util.LinkedHashMap<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String sender = row.optString("from_user", "?");
                    long ts = row.optLong("ts", 0);
                    maxTs = Math.max(maxTs, ts);
                    if (!bySender.containsKey(sender)) bySender.put(sender, new java.util.ArrayList<>());
                    bySender.get(sender).add(row);
                }

                prefs.edit().putLong(KEY_LAST_TS, maxTs).apply();
                log.i(TAG, "poll found " + rows.length() + " new msgs from " + bySender.size() + " senders");

                // Показываем уведомления
                String mutedRaw = prefs.getString(KEY_MUTED, "");
                Set<String> muted = new HashSet<>();
                if (mutedRaw != null && !mutedRaw.isEmpty()) {
                    for (String m : mutedRaw.split(",")) muted.add(m.trim());
                }
                for (java.util.Map.Entry<String, java.util.List<JSONObject>> e : bySender.entrySet()) {
                    String sender = e.getKey();
                    if (muted.contains(sender)) continue;
                    java.util.List<JSONObject> msgs = e.getValue();
                    JSONObject last = msgs.get(msgs.size() - 1);
                    String text = buildPreviewText(last);
                    showMessageNotification(sender, text, msgs.size());
                }
            } catch (Exception ex) {
                log.w(TAG, "poll error: " + ex.getMessage());
            }
        }, "bg-poll").start();
    }

    /** Формирует превью текста сообщения */
    private String buildPreviewText(JSONObject msg) {
        try {
            // Проверяем extra field для медиа
            String extra = msg.optString("extra", null);
            if (extra != null && !extra.isEmpty()) {
                JSONObject ep = new JSONObject(extra);
                String ft = ep.optString("fileType", "");
                if ("voice".equals(ft))  return "🎤 Голосовое";
                if ("video".equals(ft))  return "🎬 Видео";
                if ("image".equals(ft) || ep.has("image")) return "📷 Фото";
                if ("file".equals(ft))   return "📎 " + ep.optString("fileName", "Файл");
            }
            String text = msg.optString("text", "");
            if (!text.isEmpty()) return text.length() > 60 ? text.substring(0, 60) + "…" : text;
            // sticker field
            String sticker = msg.optString("sticker", "");
            if (!sticker.isEmpty()) return sticker + " Стикер";
        } catch (Exception ignored) {}
        return "Новое сообщение";
    }

    // ══════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ══════════════════════════════════════════════════════════════════

    private void showMessageNotification(String sender, String text, int count) {
        // Дедупликация: не показываем одно и то же уведомление дважды
        String dedupeKey = sender + ":" + text;
        if (shownNotifKeys.contains(dedupeKey)) return;
        shownNotifKeys.add(dedupeKey);
        // Очищаем старые ключи если накопилось много
        if (shownNotifKeys.size() > 200) shownNotifKeys.clear();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        }

        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        tapIntent.putExtra("open_chat", sender);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, sender.hashCode(), tapIntent, piFlags);

        String title = "@" + sender;
        String body  = count > 1 ? count + " новых сообщения: " + text : text;

        Notification notif = new NotificationCompat.Builder(this, CH_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVibrate(new long[]{0, 200, 100, 200})
            .setGroup("sapp_messages")
            .build();

        // ID уникален по sender
        int notifId = NOTIF_MSG_BASE + Math.abs(sender.hashCode() % 900);
        NotificationManagerCompat.from(this).notify(notifId, notif);
        log.i(TAG, "showed notif for @" + sender + ": " + body.substring(0, Math.min(body.length(), 50)));
    }

    /** Обновляет ongoing уведомление сервиса */
    private void updateServiceNotif(String status) {
        NotificationManagerCompat.from(this)
            .notify(NOTIF_SERVICE_ID, buildServiceNotification(status));
    }

    private Notification buildServiceNotification(String status) {
        Intent tap = new Intent(this, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap, piFlags);

        return new NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Расписание")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .setSilent(true)
            .build();
    }

    // ══════════════════════════════════════════════════════════════════
    // ASYNC UPLOAD — вызывается из AndroidBridge через статический метод
    // ══════════════════════════════════════════════════════════════════

    /**
     * Ставит загрузку файла в очередь фонового потока.
     * По завершению вызывает UploadCallback.
     *
     * @param base64   файл в Base64 (без data:…)
     * @param fileName имя файла
     * @param mimeType MIME-тип
     * @param jobId    уникальный ID задачи — возвращается в callback
     */
    public void enqueueUpload(String base64, String fileName, String mimeType, String jobId) {
        log.i(TAG, "enqueueUpload jobId=" + jobId + " file=" + fileName
                + " b64len=" + (base64 != null ? base64.length() : 0));

        updateServiceNotif("Загружаю: " + fileName);

        uploadExecutor.submit(() -> {
            try {
                byte[] fileBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                long fileSize = fileBytes.length;
                log.i(TAG, "upload start jobId=" + jobId + " size=" + fileSize);

                // Прогресс-таймер (imitation — catbox нет реального прогресса)
                final long startTs = System.currentTimeMillis();
                final float ESTIMATED_KBPS = 38f;
                final float estimatedSec = Math.max(5f, fileSize / 1024f / ESTIMATED_KBPS);
                final android.os.Handler h = new android.os.Handler(Looper.getMainLooper());
                final boolean[] done = {false};

                // Запускаем progress ticker
                Runnable ticker = new Runnable() {
                    @Override public void run() {
                        if (done[0]) return;
                        float elapsed = (System.currentTimeMillis() - startTs) / 1000f;
                        int pct = Math.min(92, (int)(elapsed / estimatedSec * 100 * 1.15f));
                        float remain = Math.max(0, estimatedSec - elapsed);
                        String label;
                        if (remain < 4)      label = "Почти готово…";
                        else if (remain < 60) label = "~" + (int)remain + " сек";
                        else                  label = "~" + (int)(remain/60) + ":" + String.format("%02d",(int)(remain%60));
                        if (uploadCallback != null) uploadCallback.onProgress(jobId, pct, label);
                        if (!done[0]) h.postDelayed(this, 600);
                    }
                };
                h.post(ticker);

                // --- Загрузка с 3 попытками ---
                String resultUrl = null;
                Exception lastErr = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    if (attempt > 1) {
                        long delay = attempt * 2000L;
                        if (uploadCallback != null)
                            uploadCallback.onProgress(jobId, 0, "⟳ Попытка " + attempt + "/3…");
                        Thread.sleep(delay);
                    }
                    try {
                        resultUrl = uploadToCatbox(fileBytes, fileName, mimeType);
                        break; // успех
                    } catch (Exception e) {
                        lastErr = e;
                        log.w(TAG, "upload attempt " + attempt + " failed: " + e.getMessage());
                        boolean retryable = e.getMessage() != null && (
                            e.getMessage().contains("abort") ||
                            e.getMessage().contains("timeout") ||
                            e.getMessage().contains("reset") ||
                            e.getMessage().contains("connect"));
                        if (!retryable || attempt == 3) break;
                    }
                }

                done[0] = true;
                h.removeCallbacksAndMessages(null);

                if (resultUrl != null) {
                    log.i(TAG, "upload OK jobId=" + jobId + " url=" + resultUrl);
                    updateServiceNotif("Ожидание сообщений…");
                    if (uploadCallback != null) uploadCallback.onDone(jobId, resultUrl);
                } else {
                    String err = lastErr != null ? lastErr.getMessage() : "Upload failed";
                    log.e(TAG, "upload FAILED jobId=" + jobId + ": " + err);
                    updateServiceNotif("Ожидание сообщений…");
                    if (uploadCallback != null) uploadCallback.onError(jobId, err);
                }

            } catch (Exception e) {
                log.e(TAG, "upload exception jobId=" + jobId + ": " + e.getMessage());
                updateServiceNotif("Ожидание сообщений…");
                if (uploadCallback != null) uploadCallback.onError(jobId, e.getMessage());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════
    // HTTP HELPERS
    // ══════════════════════════════════════════════════════════════════

    private String uploadToCatbox(byte[] fileBytes, String fileName, String mimeType) throws Exception {
        String boundary = "----CatboxBound" + Long.toHexString(System.currentTimeMillis());
        URL url = new URL("https://catbox.moe/user/api.php");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(180_000); // 3 минуты read timeout (большие файлы)
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 ScheduleApp");

        OutputStream os = conn.getOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        // reqtype
        os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n").getBytes(StandardCharsets.UTF_8));
        // file
        os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(fileBytes);
        os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int status = conn.getResponseCode();
        InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        is.close(); conn.disconnect();
        String body = baos.toString("UTF-8").trim();
        if (status == 200 && body.startsWith("https://")) return body;
        throw new Exception("catbox HTTP " + status + ": " + body.substring(0, Math.min(body.length(), 100)));
    }

    private String httpGet(String urlStr, String apiKey) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) { conn.disconnect(); return null; }
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close(); conn.disconnect();
            return baos.toString("UTF-8");
        } catch (Exception e) {
            log.w(TAG, "httpGet error: " + e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ══════════════════════════════════════════════════════════════════
    // CHANNELS
    // ══════════════════════════════════════════════════════════════════

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Сообщения — HIGH priority, вибрация, звук
        NotificationChannel chMsg = new NotificationChannel(CH_MESSAGES, "Сообщения",
            NotificationManager.IMPORTANCE_HIGH);
        chMsg.setDescription("Входящие сообщения в чате");
        chMsg.enableVibration(true);
        chMsg.setVibrationPattern(new long[]{0, 200, 100, 200});
        nm.createNotificationChannel(chMsg);

        // Сервис (ongoing) — MIN, без звука, без вибрации
        NotificationChannel chSvc = new NotificationChannel(CH_SERVICE, "Фоновая служба",
            NotificationManager.IMPORTANCE_MIN);
        chSvc.setDescription("Служба получения сообщений");
        chSvc.setSound(null, null);
        chSvc.enableVibration(false);
        nm.createNotificationChannel(chSvc);

        // Загрузки — LOW
        NotificationChannel chUpload = new NotificationChannel(CH_UPLOAD, "Загрузка файлов",
            NotificationManager.IMPORTANCE_LOW);
        chUpload.setDescription("Прогресс загрузки файлов");
        nm.createNotificationChannel(chUpload);
    }

    // ══════════════════════════════════════════════════════════════════
    // STATIC HELPERS — вызываются из MainActivity
    // ══════════════════════════════════════════════════════════════════

    /** Сохраняет конфиг для сервиса (вызывается из JS через savePushConfig) */
    public static void savePushConfig(Context ctx, String username, String sbUrl, String sbKey) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_SB_URL, sbUrl)
            .putString(KEY_SB_KEY, sbKey)
            // Начинаем с текущего момента — не хотим получить кучу старых уведомлений
            .putLong(KEY_LAST_TS, System.currentTimeMillis() - 30_000L)
            .apply();
    }

    /** Обновляет последний виденный ts (вызывается когда пользователь видит сообщения) */
    public static void updateLastTs(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_TS, System.currentTimeMillis())
            .apply();
    }
}
