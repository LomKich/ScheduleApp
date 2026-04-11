package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.graphics.Color;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final String TAG              = "MainActivity";
    private static final int    VPN_REQUEST_CODE  = 1001;
    private static final int    IMAGE_PICK_CODE   = 1002;
    private static final int    NOTIF_PERM_CODE   = 1003;
    private static final int    AUDIO_PERM_CODE   = 1004;
    private static final int    CAMERA_PERM_CODE  = 1005;
    private static final int    CIRCLE_RECORD_CODE= CircleRecordActivity.REQUEST_CODE;
    private static final String NOTIF_CHANNEL_ID  = "sapp_messages";
    private static final String NOTIF_CHANNEL_NAME = "Сообщения";
    private static final int    NOTIF_ID          = 42;

    private WebView                webView;

    // ── Встроенный браузер ──────────────────────────────────────────────
    private android.widget.FrameLayout _browserOverlay = null;
    private WebView                    _browserWebView = null;
    private SharedPreferences      prefs;
    private DnsVpnService          vpnService;
    private boolean                vpnBound = false;
    private AppLogger              log;
    private SupabaseClient         supabase;
    private SupabaseHelper         helper;
    private ValueCallback<Uri[]>   fileChooserCallback = null;
    private boolean                isNativeBgPick = false;
    private android.webkit.PermissionRequest _pendingPermissionRequest = null;

    // ── Нативная запись голосовых (обход ограничений WebView getUserMedia) ──
    private MediaRecorder  _voiceRecorder   = null;
    private java.io.File   _voiceFile       = null;
    private long           _voiceStartMs    = 0;
    private android.os.Handler _voiceTimerHandler = null;
    private boolean        _pendingVoiceRecordRetry = false;
    // Telegram-style: блокировка для атомарного чтения/записи состояния записи
    private final Object   _voiceLock       = new Object();
    private volatile boolean _voiceCancelled = false;

    // Java-side poll timer — работает даже когда WebView заморожен в фоне
    private android.os.Handler     pollHandler;
    private Runnable               pollRunnable;
    private static final int       POLL_INTERVAL_FG = 2000;  // мс foreground
    private static final int       POLL_INTERVAL_BG = 5000;  // мс background
    private boolean                appInForeground  = false;

    // ── Фоновые уведомления: Java-side Supabase poller ───────────────
    // Когда WebView заморожен, JS не работает. Java сама опрашивает Supabase.
    private static final String PREF_SB_USER    = "sb_username";
    private static final String PREF_SB_LAST_TS = "sb_last_notif_ts";
    private int                 _notifIdCounter = 1000;

    private final ServiceConnection vpnConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DnsVpnService.LocalBinder binder = (DnsVpnService.LocalBinder) service;
            vpnService = binder.getService();
            vpnBound   = true;
            log.i(TAG, "VPN сервис подключён");
            updateVpnStateInWeb();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            vpnBound   = false;
            vpnService = null;
            log.w(TAG, "VPN сервис отключён");
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        log = AppLogger.get(this);
        log.section("onCreate");
        log.i(TAG, "Приложение запускается");

        supabase = SupabaseClient.get(this, log);
        log.i(TAG, "SupabaseClient готов [URL=" + SupabaseClient.URL + "]");
        helper = SupabaseHelper.get(this, log);
        log.i(TAG, "SupabaseHelper готов (presence / messages / leaderboard / users / accounts)");

        // Современный подход: контент рисуется за системными барами (status + nav)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Прозрачные статус-бар и навигационная панель
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // Android 10+: прозрачность жест-бара и контрастный фон контрастности
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        // Иконки статус-бара — светлые (под тёмный фон приложения)
        WindowInsetsControllerCompat wic = new WindowInsetsControllerCompat(
            getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);
        wic.setAppearanceLightNavigationBars(false);

        // Контент заходит за вырез камеры (punch-hole / Dynamic Island)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        prefs   = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE);
        webView = new WebView(this);
        setContentView(webView);

        setupWebView();
        log.i(TAG, "Загружаем index.html");
        webView.loadUrl("file:///android_asset/index.html");

        Intent vpnIntent = new Intent(this, DnsVpnService.class);
        try {
            bindService(vpnIntent, vpnConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            log.w(TAG, "bindService отклонён системой: " + e.getMessage());
        }

        log.i(TAG, "onCreate завершён");
        createNotificationChannel();
        // Запрашиваем исключение из оптимизации батареи — один раз при первом запуске.
        // Без этого Android убивает фоновый сервис и уведомления не приходят.
        requestBatteryOptimizationExemption();
    }

    /** Предлагает пользователю добавить приложение в белый список батареи (один раз). */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (prefs.getBoolean("battery_opt_asked", false)) return;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return;
            prefs.edit().putBoolean("battery_opt_asked", true).apply();
            new AlertDialog.Builder(this)
                .setTitle("🔔 Уведомления в фоне")
                .setMessage("Для получения сообщений когда приложение закрыто — разрешите работу в фоне.\n\nНа следующем экране нажмите «Разрешить».")
                .setPositiveButton("Настроить", (d, w) -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            startActivity(new android.content.Intent(
                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
        } catch (Exception e) {
            log.w(TAG, "requestBatteryOptimizationExemption: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Входящие сообщения");
            ch.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Аппаратное ускорение
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        // Фон WebView в цвет сплэша чтобы не было белой вспышки
        webView.setBackgroundColor(0xFF0D0D0D);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // Разрешаем iframe-ам из file:// загружать другие локальные файлы (doom.html, minecraft.html, quake.html)
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        // Для локальных asset-файлов используем LOAD_DEFAULT —
        // браузерный кэш работает, но при изменении файла подхватывает новую версию.
        // LOAD_CACHE_ELSE_NETWORK слишком агрессивен — не видит обновлений JS.
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Отключаем pinch-to-zoom
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        // Фиксируем зум на 100% — без этого бывает лишний пересчёт
        ws.setTextZoom(100);
        // Включаем базу данных для офлайн-кэша
        ws.setDatabaseEnabled(true);
        // Геолокация не нужна — отключаем
        ws.setGeolocationEnabled(false);
        // Ускорение рендера: отключаем safe browsing для локальных файлов
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(false);
        }
        // Отключаем полосу прокрутки WebView
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollbarFadingEnabled(false);
        log.i(TAG, "WebSettings: JS=on DomStorage=on FileAccess=on MixedContent=ALWAYS_ALLOW Hardware=on Cache=LOAD_DEFAULT");

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        log.i(TAG, "JavascriptInterface 'Android' зарегистрирован");

        // Перехват console.log / console.error из JS
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                log.js(cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber());
                return true;
            }

            // ─── Разрешения для WebView (микрофон, камера и т.д.) ───
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                String[] requestedResources = request.getResources();
                java.util.List<String> toGrant = new java.util.ArrayList<>();
                for (String res : requestedResources) {
                    if (res.equals(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        // Нужно runtime-разрешение RECORD_AUDIO
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                MainActivity.this, android.Manifest.permission.RECORD_AUDIO)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            toGrant.add(res);
                        } else {
                            // Запрашиваем у пользователя и сохраняем request для повтора
                            _pendingPermissionRequest = request;
                            androidx.core.app.ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{ android.Manifest.permission.RECORD_AUDIO },
                                AUDIO_PERM_CODE);
                            return;
                        }
                    } else if (res.equals(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        toGrant.add(res); // камера — разрешаем без лишних проверок
                    }
                }
                if (!toGrant.isEmpty()) {
                    request.grant(toGrant.toArray(new String[0]));
                } else {
                    request.deny();
                }
            }

            // ─── Нативный файловый пикер для <input type="file"> ───
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                // Если предыдущий колбэк не закрыт — отменяем его
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                }
                fileChooserCallback = filePathCallback;

                // Определяем MIME-тип по accept из HTML input
                String mimeType = "image/*";
                String chooserTitle = "Выбери файл";
                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                if (acceptTypes != null) {
                    for (String t : acceptTypes) {
                        if (t != null && t.startsWith("audio")) {
                            mimeType = "audio/*";
                            chooserTitle = "Выбери аудио файл";
                            break;
                        }
                        if (t != null && t.startsWith("video")) {
                            mimeType = "video/*";
                            chooserTitle = "Выбери видео";
                            break;
                        }
                        if (t != null && t.equals("*/*")) {
                            mimeType = "*/*";
                            break;
                        }
                    }
                }

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mimeType);
                // Для аудио явно разрешаем все аудио-расширения
                if (mimeType.equals("audio/*")) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"audio/*", "application/ogg", "application/mp4"});
                }
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, chooserTitle),
                        IMAGE_PICK_CODE
                    );
                } catch (Exception e) {
                    log.e(TAG, "onShowFileChooser error: " + e.getMessage());
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap fav) {
                log.i(TAG, "onPageStarted: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                log.i(TAG, "onPageFinished: " + url);
                injectErrorHandler();
                updateVpnStateInWeb();
                // Запускаем Java-поллер после загрузки страницы
                startJavaPollTimer();
                // Передаём реальную высоту статус-бара в CSS-переменную
                injectStatusBarHeight();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    log.e(TAG, "Ошибка страницы: " + err.getErrorCode()
                            + " — " + err.getDescription() + " | " + req.getUrl());
                } else {
                    log.w(TAG, "Ошибка ресурса: " + req.getUrl() + " | " + err.getDescription());
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!url.startsWith("file://") && !url.contains("fonts.googleapis")) {
                    log.i(TAG, "fetch → " + url);
                }
                // ── Hot-patch: если файл был обновлён — отдаём из filesDir ────────────
                // ── Emoji pack: кастомная схема https://emoji.local/ ──────────────────
                // file:///android_asset/ не перехватывается надёжно — используем HTTPS
                if (url.startsWith("https://emoji.local/")) {
                    // req.getUrl().getPath() возвращает автоматически URL-decoded путь
                    String emojiPath = req.getUrl().getPath(); // напр. "/nat/Fire.png"
                    if (emojiPath != null && emojiPath.startsWith("/")) {
                        emojiPath = emojiPath.substring(1); // убираем leading "/"
                    }
                    java.io.File emojiFile = new java.io.File(
                        new java.io.File(getFilesDir(), "emoji"), emojiPath);
                    if (emojiFile.exists()) {
                        try {
                            java.util.Map<String, String> h = new java.util.HashMap<>();
                            h.put("Access-Control-Allow-Origin", "*");
                            WebResourceResponse r = new WebResourceResponse(
                                "image/png", null, 200, "OK", h,
                                new java.io.FileInputStream(emojiFile));
                            return r;
                        } catch (Exception e) {
                            log.w(TAG, "emoji serve error: " + e.getMessage());
                        }
                    } else {
                        log.w(TAG, "emoji not found: " + emojiFile.getAbsolutePath());
                    }
                    return null;
                }

                if (url.startsWith("file:///android_asset/")) {
                    String assetPath = url.substring("file:///android_asset/".length());
                    // Hot-patch файлы
                    java.io.File patchFile = new java.io.File(
                        new java.io.File(getFilesDir(), "hotpatch"), assetPath);
                    if (patchFile.exists()) {
                        try {
                            String mime = "text/html";
                            if (assetPath.endsWith(".js"))  mime = "application/javascript";
                            if (assetPath.endsWith(".css")) mime = "text/css";
                            if (assetPath.endsWith(".json"))mime = "application/json";
                            log.i(TAG, "hotpatch serving: " + assetPath);
                            return new WebResourceResponse(mime, "UTF-8",
                                new java.io.FileInputStream(patchFile));
                        } catch (Exception e) {
                            log.w(TAG, "hotpatch read error: " + e.getMessage());
                        }
                    }
                }
                // Перехватываем twemoji CDN — исправляем MIME-тип (text/plain → application/javascript)
                if (url.contains("twemoji")) {
                    try {
                        java.net.URL jurl = new java.net.URL(url);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) jurl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(15000);
                        conn.connect();
                        if (conn.getResponseCode() == 200) {
                            java.io.InputStream is = conn.getInputStream();
                            WebResourceResponse resp = new WebResourceResponse(
                                "application/javascript", "UTF-8", is
                            );
                            java.util.Map<String, String> headers = new java.util.HashMap<>();
                            headers.put("Access-Control-Allow-Origin", "*");
                            resp.setResponseHeaders(headers);
                            return resp;
                        }
                    } catch (Exception e) {
                        log.w(TAG, "intercept fetch failed for " + url + ": " + e.getMessage());
                    }
                }
                return super.shouldInterceptRequest(view, req);
            }
        });
    }

    /** Запускает Java-таймер который пингует JS каждые 2-5 сек.
     *  В фоне — напрямую опрашивает Supabase и показывает уведомления,
     *  не полагаясь на JS (WebView заморожен Android'ом). */
    private void startJavaPollTimer() {
        if (pollHandler != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
        pollHandler  = new android.os.Handler(android.os.Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (webView == null) return;
                if (appInForeground) {
                    // На переднем плане — пускаем обычный JS-тик
                    webView.evaluateJavascript(
                        "if(typeof window._javaTick==='function'){window._javaTick();}",
                        null
                    );
                } else {
                    // В фоне — WebView заморожен, JS не работает.
                    // Опрашиваем Supabase напрямую из Java.
                    new Thread(MainActivity.this::_bgPollSupabase).start();
                }
                int interval = appInForeground ? POLL_INTERVAL_FG : POLL_INTERVAL_BG;
                pollHandler.postDelayed(this, interval);
            }
        };
        appInForeground = true;
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_FG);
        log.i(TAG, "Java poll timer запущен (fg=" + POLL_INTERVAL_FG + "мс bg=" + POLL_INTERVAL_BG + "мс)");
    }

    /**
     * Фоновый поллинг Supabase напрямую из Java (без JS).
     * Вызывается только когда приложение в фоне (WebView заморожен).
     * Читает новые сообщения и показывает системные уведомления.
     */
    private void _bgPollSupabase() {
        try {
            String username = prefs.getString(PREF_SB_USER, "");
            if (username == null || username.isEmpty()) return;

            long lastTs = prefs.getLong(PREF_SB_LAST_TS, System.currentTimeMillis() - 30000);

            // Запрашиваем новые сообщения TO меня после lastTs
            String urlStr = SupabaseClient.URL
                + "/rest/v1/messages?select=from_user,text,ts,extra"
                + "&to_user=eq." + java.net.URLEncoder.encode(username, "UTF-8")
                + "&ts=gt." + lastTs
                + "&order=ts.asc&limit=20";

            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("apikey", SupabaseClient.ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + SupabaseClient.ANON_KEY);
            conn.setRequestProperty("Accept", "application/json");

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
            // Группируем по отправителю — одно уведомление на человека
            java.util.LinkedHashMap<String, java.util.List<String>> byUser =
                new java.util.LinkedHashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject msg = arr.getJSONObject(i);
                String from  = msg.optString("from_user", "?");
                long   ts    = msg.optLong("ts", 0);
                String text  = msg.optString("text", "");
                String extra = msg.optString("extra", "");

                // Пропускаем служебные (реакции, read_receipt)
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

                // Зашифрованное сообщение
                if (text.startsWith("ENC:")) {
                    text = "🔐 Зашифрованное сообщение";
                } else if (text.isEmpty() || text.startsWith("{")) {
                    // Медиа-сообщение — читаем тип из extra
                    if (!extra.isEmpty()) {
                        try {
                            org.json.JSONObject ex = new org.json.JSONObject(extra);
                            String ft = ex.optString("fileType", "");
                            String tp = ex.optString("type", "");
                            if ("group_invite".equals(tp))  text = "👥 Добавил(а) в группу";
                            else if ("voice".equals(ft))    text = "🎤 Голосовое";
                            else if ("circle".equals(ft))   text = "⭕ Видеосообщение";
                            else if ("video".equals(ft))    text = "🎬 Видео";
                            else if (ex.has("image"))       text = "📷 Фото";
                            else if ("file".equals(ft))     text = "📎 " + ex.optString("fileName", "Файл");
                            else if (!ft.isEmpty())         text = "📎 Файл";
                        } catch (Exception ignored) {}
                    }
                    if (text.isEmpty()) {
                        String sticker = msg.optString("sticker", "");
                        text = sticker.isEmpty() ? "📎 Файл" : sticker + " Стикер";
                    }
                }

                if (ts > maxTs) maxTs = ts;
                byUser.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(text);
            }

            // Сохраняем новый lastTs
            prefs.edit().putLong(PREF_SB_LAST_TS, maxTs + 1).apply();

            // Показываем уведомление (одно суммарное либо раздельные)
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            }

            for (java.util.Map.Entry<String, java.util.List<String>> entry : byUser.entrySet()) {
                String from = entry.getKey();
                java.util.List<String> texts = entry.getValue();
                String preview = texts.size() == 1
                    ? texts.get(0)
                    : texts.size() + " новых сообщений";
                if (preview.length() > 100) preview = preview.substring(0, 97) + "…";

                // Уникальный intent на каждого отправителя — открывает его чат
                Intent tapIntent = new Intent(this, MainActivity.class);
                tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                tapIntent.putExtra("open_chat", from);
                int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
                PendingIntent pi = PendingIntent.getActivity(
                    this, Math.abs(from.hashCode()), tapIntent, piFlags);

                NotificationCompat.Builder nb = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("@" + from)
                    .setContentText(preview)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(preview))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setVibrate(new long[]{0, 200, 80, 200})
                    .setDefaults(NotificationCompat.DEFAULT_SOUND)
                    .setGroup("sapp_msg_group");
                // Стабильный ID: одно уведомление на отправителя (новое заменяет старое)
                int notifId = 1000 + Math.abs(from.hashCode() % 1000);
                nm.notify(notifId, nb.build());
                log.i(TAG, "_bgPollSupabase: показано уведомление от @" + from + " (" + texts.size() + " сообщ.)");
            }
        } catch (Exception e) {
            // Тихо — не спамим логи при каждом фоновом тике
            log.w(TAG, "_bgPollSupabase error: " + e.getMessage());
        }
    }

    /**
     * JS вызывает этот метод при логине/смене аккаунта,
     * чтобы Java знала чей username отслеживать для фоновых уведомлений.
     */
        @JavascriptInterface
        public void setCurrentUser(String username) {
            log.i(TAG, "setCurrentUser: " + username);
            prefs.edit()
                .putString(PREF_SB_USER, username != null ? username : "")
                // Начинаем отслеживать с текущего момента (не уведомляем о старых)
                .putLong(PREF_SB_LAST_TS, System.currentTimeMillis())
                .apply();
        }

        /**
         * JS вызывает при изменении списка групп пользователя.
         * Сохраняем JSON-массив групп в SharedPreferences для фонового сервиса.
         */
        @JavascriptInterface
        public void saveUserGroups(String username, String groupsJson) {
            if (username == null || username.isEmpty() || groupsJson == null) return;
            log.i(TAG, "saveUserGroups: " + username + " groups=" + groupsJson.length() + " chars");
            prefs.edit()
                .putString("user_groups_" + username, groupsJson)
                .apply();
        }

        /**
         * Сброс счётчика уведомлений (вызывается когда пользователь открыл чат).
         */
        @JavascriptInterface
        public void dismissNotifications() {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancelAll();
            // Обновляем lastTs чтобы не показывать прочитанное снова
            prefs.edit().putLong(PREF_SB_LAST_TS, System.currentTimeMillis()).apply();
        }

        /** Возвращает сохранённый username для фонового поллинга */
        @JavascriptInterface
        public String getCurrentUser() {
            return prefs.getString(PREF_SB_USER, "");
        }

    private void injectStatusBarHeight() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
            getWindow().getDecorView(), (v, insets) -> {
                androidx.core.graphics.Insets sysInsets = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() |
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                );
                float density = getResources().getDisplayMetrics().density;
                int topDp = Math.round(sysInsets.top    / density);
                int botDp = Math.round(sysInsets.bottom / density);
                // Если bottom=0 при жест-навигации — берём резервное значение из ресурсов
                if (botDp == 0) {
                    int navBarId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
                    if (navBarId > 0) {
                        int navPx = getResources().getDimensionPixelSize(navBarId);
                        botDp = Math.max(botDp, Math.round(navPx / density));
                    }
                    // Для жест-навигации минимум 10dp
                    if (botDp == 0) botDp = 10;
                }
                final int finalTop = topDp, finalBot = botDp;
                log.i(TAG, "injectStatusBarHeight: statusBar=" + finalTop + "dp navBar=" + finalBot + "dp");
                String js = String.format(
                    "document.documentElement.style.setProperty('--safe-top','%dpx');" +
                    "document.documentElement.style.setProperty('--safe-top-native','%dpx');" +
                    "document.documentElement.style.setProperty('--safe-bot','%dpx');" +
                    "console.log('[StatusBar] safe-top=%dpx safe-bot=%dpx');",
                    finalTop, finalTop, finalBot, finalTop, finalBot
                );
                webView.post(() -> webView.evaluateJavascript(js, null));
                return insets;
            }
        );
        androidx.core.view.ViewCompat.requestApplyInsets(getWindow().getDecorView());
        // Повторный запрос через 500мс — на случай если первый пришёл до layout
        getWindow().getDecorView().postDelayed(
            () -> androidx.core.view.ViewCompat.requestApplyInsets(getWindow().getDecorView()),
            500
        );
    }

    private void injectErrorHandler() {
        String js =
            "(function(){"
            + "var _oe=window.onerror;"
            + "window.onerror=function(msg,src,line,col,err){"
            + "  var s='[onerror] '+msg+' @ '+(src||'?')+':'+line+':'+col;"
            + "  if(err&&err.stack)s+='\\nSTACK: '+err.stack.substring(0,400);"
            + "  if(window.Android&&window.Android.logError)window.Android.logError(s);"
            + "  if(_oe)return _oe(msg,src,line,col,err);return false;"
            + "};"
            + "window.addEventListener('unhandledrejection',function(e){"
            + "  var r=e.reason||{};"
            + "  var s='[Promise rejected] '+(r.message||String(r));"
            + "  if(r.stack)s+='\\nSTACK: '+r.stack.substring(0,400);"
            + "  if(window.Android&&window.Android.logError)window.Android.logError(s);"
            + "});"
            + "console.log('[Logger] window.onerror + unhandledrejection перехвачены');"
            + "})();";
        webView.evaluateJavascript(js, null);
        log.i(TAG, "JS error handler внедрён");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            log.i(TAG, "VPN разрешение получено");
            startVpnService();
        } else if (requestCode == VPN_REQUEST_CODE) {
            log.w(TAG, "VPN разрешение отклонено");
        } else if (requestCode == IMAGE_PICK_CODE) {
            // ─── Общая логика чтения файла ───
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                log.i(TAG, "Файл выбран: " + uri + " | нативный режим: " + isNativeBgPick);
                final Uri finalUri = uri;
                final boolean wasNativePick = isNativeBgPick;
                isNativeBgPick = false;

                // Если это НЕ нативный пик фона, а <input type="file"> из WebView —
                // просто возвращаем URI в callback, без image-обработки
                if (!wasNativePick && fileChooserCallback != null) {
                    log.i(TAG, "Передаём URI в fileChooserCallback напрямую");
                    fileChooserCallback.onReceiveValue(new Uri[]{finalUri});
                    fileChooserCallback = null;
                    return;
                }

                new Thread(() -> {
                    try {
                        InputStream is = getContentResolver().openInputStream(finalUri);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                        is.close();
                        byte[] rawBytes = baos.toByteArray();

                        // Конвертируем любой формат (HEIC/HEIF/BMP/WEBP/и т.д.) в JPEG
                        byte[] bytes;
                        String mime;
                        android.graphics.Bitmap bitmap =
                            android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length);
                        if (bitmap != null) {
                            // Масштабируем до разумного размера (макс. 1920px по длинной стороне)
                            // чтобы base64 не превышал ~10 МБ лимит WebView
                            final int MAX_SIDE = 1920;
                            int w = bitmap.getWidth(), h = bitmap.getHeight();
                            if (w > MAX_SIDE || h > MAX_SIDE) {
                                float scale = Math.min((float) MAX_SIDE / w, (float) MAX_SIDE / h);
                                int nw = Math.round(w * scale), nh = Math.round(h * scale);
                                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                                bitmap.recycle();
                                bitmap = scaled;
                                log.i(TAG, "Изображение уменьшено: " + w + "x" + h + " → " + nw + "x" + nh);
                            }
                            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, jpegOut);
                            bitmap.recycle();
                            bytes = jpegOut.toByteArray();
                            mime = "image/jpeg";
                            log.i(TAG, "Изображение сконвертировано в JPEG, размер: " + bytes.length + " байт");
                        } else {
                            bytes = rawBytes;
                            mime = getContentResolver().getType(finalUri);
                            if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                            log.w(TAG, "BitmapFactory не смог декодировать, mime=" + mime);
                        }

                        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                        String dataUrl = "data:" + mime + ";base64," + b64;
                        final String jsDataUrl = dataUrl;

                        final byte[] finalBytes = bytes;
                        runOnUiThread(() -> {
                            if (fileChooserCallback != null) {
                                fileChooserCallback.onReceiveValue(null);
                                fileChooserCallback = null;
                            }
                            // Передаём изображение в JS (только для нативного пика фона)
                            String escaped = jsDataUrl.replace("\\", "\\\\").replace("'", "\\'");
                            webView.evaluateJavascript(
                                "if(typeof onNativeBgImagePicked==='function')onNativeBgImagePicked('" + escaped + "')",
                                null
                            );
                            log.i(TAG, "Фон передан в JS, размер: " + finalBytes.length + " байт");
                        });
                    } catch (Exception e) {
                        log.e(TAG, "Ошибка чтения изображения: " + e.getMessage());
                        isNativeBgPick = false;
                        runOnUiThread(() -> {
                            if (fileChooserCallback != null) {
                                fileChooserCallback.onReceiveValue(null);
                                fileChooserCallback = null;
                            }
                            webView.evaluateJavascript(
                                "if(typeof toast==='function')toast('❌ Не удалось прочитать изображение')",
                                null
                            );
                        });
                    }
                }).start();
            } else {
                // Пользователь отменил выбор
                log.i(TAG, "Выбор изображения отменён");
                isNativeBgPick = false;
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                }
            }
        } else if (requestCode == CIRCLE_RECORD_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                String videoPath = data.getStringExtra(CircleRecordActivity.EXTRA_VIDEO_PATH);
                if (videoPath != null && !videoPath.isEmpty()) {
                    log.i(TAG, "CircleRecord: got video path=" + videoPath);
                    // Уведомляем JS что видео снято, идёт загрузка
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof onNativeCircleUploading==='function')onNativeCircleUploading()", null));
                    new Thread(() -> {
                        try {
                            java.io.File f = new java.io.File(videoPath);
                            if (!f.exists()) {
                                runOnUiThread(() -> webView.evaluateJavascript(
                                    "if(typeof onNativeCircleError==='function')onNativeCircleError('File not found')", null));
                                return;
                            }
                            // Читаем файл
                            byte[] bytes = new byte[(int) f.length()];
                            java.io.FileInputStream fis = new java.io.FileInputStream(f);
                            int totalRead = 0, n;
                            while (totalRead < bytes.length &&
                                   (n = fis.read(bytes, totalRead, bytes.length - totalRead)) != -1) {
                                totalRead += n;
                            }
                            fis.close();
                            f.delete();
                            log.i(TAG, "CircleRecord: uploading " + bytes.length + " bytes");

                            // Загружаем напрямую на CDN (pixeldrain → catbox)
                            // Не передаём base64 через JS-мост — слишком большой для evaluateJavascript
                            String fileName = "circle_" + System.currentTimeMillis() + ".mp4";
                            String result = null;
                            try {
                                result = _mainPixeldrainUpload(bytes, fileName, "video/mp4");
                            } catch (Exception pdEx) {
                                log.w(TAG, "pixeldrain circle fallback: " + pdEx.getMessage());
                            }
                            if (result == null || !result.contains("\"ok\":true")) {
                                try {
                                    result = _mainCatboxUpload(bytes, fileName, "video/mp4");
                                } catch (Exception cbEx) {
                                    log.w(TAG, "catbox circle error: " + cbEx.getMessage());
                                }
                            }

                            if (result != null && result.contains("\"ok\":true")) {
                                try {
                                    org.json.JSONObject jo = new org.json.JSONObject(result);
                                    String url = jo.optString("url", "");
                                    if (!url.isEmpty()) {
                                        final String fileUrl = url;
                                        final long fileSize = bytes.length;
                                        log.i(TAG, "CircleRecord: CDN OK url=" + fileUrl);
                                        runOnUiThread(() -> webView.evaluateJavascript(
                                            "if(typeof onNativeCircleUploaded==='function')onNativeCircleUploaded('" +
                                            fileUrl.replace("'", "\'") + "'," + fileSize + ")", null));
                                        return;
                                    }
                                } catch (Exception je) { log.e(TAG, "parse url: " + je.getMessage()); }
                            }
                            log.e(TAG, "CircleRecord upload failed");
                            runOnUiThread(() -> webView.evaluateJavascript(
                                "if(typeof onNativeCircleError==='function')onNativeCircleError('Upload failed')", null));
                        } catch (Exception e) {
                            log.e(TAG, "CircleRecord error: " + e.getMessage());
                            runOnUiThread(() -> webView.evaluateJavascript(
                                "if(typeof onNativeCircleError==='function')onNativeCircleError('Error')", null));
                        }
                    }).start();
                }
            } else {
                log.i(TAG, "CircleRecord: cancelled");
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeCircleCancelled==='function')onNativeCircleCancelled()", null));
            }
        }
    }

    // ── Вспомогательные методы для загрузки файлов из MainActivity ───────────
    // _catboxUploadBytes и _pixeldrainUploadBytes объявлены в AndroidBridge,
    // поэтому создаём делегаты на уровне MainActivity для использования в onActivityResult.

    private String _mainCatboxUpload(byte[] bytes, String fileName, String mime) {
        return new AndroidBridge()._catboxUploadBytesPublic(bytes, fileName, mime);
    }

    private String _mainPixeldrainUpload(byte[] bytes, String fileName, String mime) {
        return new AndroidBridge()._pixeldrainUploadBytesPublic(bytes, fileName, mime);
    }

    private void startVpnService() {
        String dnsKey      = prefs.getString("dns_key", "system");
        String dohUrl      = prefs.getString("doh_url", "");
        String dpiStrategy = prefs.getString("dpi_strategy", "general");
        log.i(TAG, "startVpnService dns=" + dnsKey + " dpi=" + dpiStrategy);
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_START);
        intent.putExtra(DnsVpnService.EXTRA_DOH_URL, dohUrl);
        intent.putExtra(DnsVpnService.EXTRA_DPI_STRATEGY, dpiStrategy);
        startService(intent);
    }

    private void stopVpnService() {
        log.i(TAG, "stopVpnService");
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_STOP);
        startService(intent);
    }

    void updateVpnStateInWeb() {
        boolean active = vpnBound && vpnService != null && vpnService.isRunning();
        String statusText = active
            ? "DNS: " + prefs.getString("dns_key", "system").toUpperCase()
              + " • DPI: " + prefs.getString("dpi_strategy", "general")
            : "DNS и DPI обход неактивны";
        String js = String.format(
            "if(typeof onVpnStateChanged==='function')onVpnStateChanged(%b,'%s')",
            active, statusText
        );
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onBackPressed() {
        // Встроенный браузер: Back = история или закрыть
        if (_browserOverlay != null) {
            if (_browserWebView != null && _browserWebView.canGoBack()) {
                _browserWebView.goBack();
            } else {
                closeBrowser();
            }
            return;
        }
        // Спрашиваем JS: можно ли перейти назад внутри приложения
        webView.evaluateJavascript(
            "(function(){ return typeof nativeBack==='function' ? (nativeBack() ? 'handled' : 'exit') : 'exit'; })()",
            result -> {
                if (!"\"handled\"".equals(result)) {
                    // JS сказал 'exit' — мы на главном экране, закрываем приложение
                    finish();
                }
            }
        );
    }

    /** Закрыть встроенный браузер */
    private void closeBrowser() {
        if (_browserOverlay == null) return;
        android.view.ViewGroup root = (android.view.ViewGroup) getWindow().getDecorView();
        root.removeView(_browserOverlay);
        if (_browserWebView != null) {
            _browserWebView.stopLoading();
            _browserWebView.destroy();
            _browserWebView = null;
        }
        _browserOverlay = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        appInForeground = false;
        log.i(TAG, "onPause — переключаю поллер в фоновый режим (" + POLL_INTERVAL_BG + "мс)");
        // Если фоновый сервис включён — он сам поллит, Java-поллер в MainActivity не нужен
        boolean svcEnabled = prefs.getBoolean(MessagingForegroundService.PREF_BG_ENABLED, false);
        if (svcEnabled) {
            // Останавливаем дублирующий поллер в MainActivity
            if (pollHandler != null && pollRunnable != null) {
                pollHandler.removeCallbacks(pollRunnable);
            }
        } else {
            // Сервис выключен — MainActivity сам поллит в фоне
            if (pollHandler != null && pollRunnable != null) {
                pollHandler.removeCallbacks(pollRunnable);
                pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_BG);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        appInForeground = true;
        log.i(TAG, "onResume — переключаю поллер в активный режим (" + POLL_INTERVAL_FG + "мс)");
        // Снимаем все уведомления когда пользователь вернулся в приложение
        NotificationManager nm2 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm2 != null) nm2.cancelAll();
        prefs.edit().putLong(PREF_SB_LAST_TS, System.currentTimeMillis()).apply();
        // Ускоряем интервал — приложение на экране
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.post(pollRunnable); // немедленный тик + восстановление
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Открываем конкретный чат если пришли из уведомления
        if (intent != null && intent.hasExtra("open_chat")) {
            String chatUser = intent.getStringExtra("open_chat");
            if (chatUser != null && !chatUser.isEmpty() && webView != null) {
                // Небольшая задержка чтобы WebView успел инициализироваться
                webView.postDelayed(() -> {
                    String js = "if(typeof messengerOpenChat==='function'){" +
                                "  messengerOpenChat('" + chatUser.replace("'", "\\'") + "');" +
                                "}";
                    webView.evaluateJavascript(js, null);
                }, 600);
                log.i(TAG, "onNewIntent: открываю чат @" + chatUser);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERM_CODE) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            String result = granted ? "granted" : "denied";
            log.i(TAG, "Notification permission result: " + result);
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof onNativeNotifPermissionResult==='function')" +
                "onNativeNotifPermissionResult('" + result + "')", null));
        } else if (requestCode == AUDIO_PERM_CODE) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            log.i(TAG, "Audio permission result: " + (granted ? "granted" : "denied"));
            if (granted) {
                // PermissionRequest протухает пока пользователь отвечал на диалог.
                // Пробуем grant() если объект ещё жив, иначе говорим JS повторить запрос.
                if (_pendingPermissionRequest != null) {
                    try {
                        _pendingPermissionRequest.grant(
                            new String[]{ android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE });
                        log.i(TAG, "PermissionRequest.grant() OK");
                    } catch (Exception e) {
                        log.w(TAG, "PermissionRequest expired, asking JS to retry getUserMedia");
                        // Просим JS повторно вызвать mcVoiceTouchStart — WebView
                        // снова дёрнет onPermissionRequest, но теперь разрешение уже есть
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof mcVoiceRetryAfterPermission==='function')" +
                            "mcVoiceRetryAfterPermission();", null));
                    }
                } else {
                    // Объект уже null — просим JS повторить
                    log.i(TAG, "pendingPermissionRequest is null, asking JS to retry");
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof mcVoiceRetryAfterPermission==='function')" +
                        "mcVoiceRetryAfterPermission();", null));
                }
            } else {
                if (_pendingPermissionRequest != null) _pendingPermissionRequest.deny();
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof toast==='function')toast('🎤 Нет доступа к микрофону')", null));
            }
            _pendingPermissionRequest = null;
            // Если ждали нативную запись — перезапускаем
            if (_pendingVoiceRecordRetry && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                _pendingVoiceRecordRetry = false;
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof mcVoiceRetryAfterPermission==='function')mcVoiceRetryAfterPermission();", null));
            } else {
                _pendingVoiceRecordRetry = false;
            }
        } else if (requestCode == CAMERA_PERM_CODE) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            log.i(TAG, "Camera permission result: " + (granted ? "granted" : "denied"));
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof onCameraPermissionResult==='function')onCameraPermissionResult('" +
                (granted ? "granted" : "denied") + "')", null));
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.section("onDestroy");
        log.i(TAG, "Приложение завершается");
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler = null;
        }
        if (vpnBound) unbindService(vpnConnection);
    }

    // ══════════════════════════════ JavaScript Bridge ══════════════════════════════
    /** Интерфейс для прогресс-колбэка при загрузке */
    interface UploadProgressCallback { void onProgress(int pct); }

    // Важно: static + public — иначе WebView не может найти методы через рефлексию

    public class AndroidBridge {

        // ─── Push-уведомления ─────────────────────────────────────────────────────

        /** Возвращает "granted" | "denied" | "default" */
        /** Получить текущую яркость экрана (0-255, или -1 = авто) */
        @JavascriptInterface
        public int getScreenBrightness() {
            try {
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                float br = lp.screenBrightness;
                if (br < 0) return -1;
                return Math.round(br * 255);
            } catch (Exception e) {
                log.w(TAG, "getScreenBrightness error: " + e.getMessage());
                return -1;
            }
        }

        /** Установить яркость экрана (0-255, или -1 = вернуть авто) */
        @JavascriptInterface
        public void setScreenBrightness(final int value) {
            log.i(TAG, "setScreenBrightness: " + value);
            runOnUiThread(() -> {
                try {
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = value < 0 ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                                     : Math.max(0, Math.min(255, value)) / 255f;
                    getWindow().setAttributes(lp);
                } catch (Exception e) {
                    log.w(TAG, "setScreenBrightness error: " + e.getMessage());
                }
            });
        }

        /** Запрашивает разрешение на использование камеры */
        @JavascriptInterface
        public void requestCameraPermission() {
            log.i(TAG, "requestCameraPermission called");
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                log.i(TAG, "Camera already granted");
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onCameraPermissionResult==='function')onCameraPermissionResult('granted')", null));
            } else {
                log.i(TAG, "Requesting CAMERA permission");
                ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{ android.Manifest.permission.CAMERA },
                    CAMERA_PERM_CODE);
            }
        }

        @JavascriptInterface
        public String getNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int state = ContextCompat.checkSelfPermission(
                    MainActivity.this, android.Manifest.permission.POST_NOTIFICATIONS);
                return (state == PackageManager.PERMISSION_GRANTED) ? "granted" : "default";
            }
            // До Android 13 разрешение не нужно — проверяем включён ли канал
            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.areNotificationsEnabled()) return "denied";
            return "granted";
        }

        /** Запрашивает runtime-разрешение (Android 13+). На старых — сразу "granted". */
        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int state = ContextCompat.checkSelfPermission(
                    MainActivity.this, android.Manifest.permission.POST_NOTIFICATIONS);
                if (state == PackageManager.PERMISSION_GRANTED) {
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof onNativeNotifPermissionResult==='function')" +
                        "onNativeNotifPermissionResult('granted')", null));
                } else {
                    ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERM_CODE);
                }
            } else {
                // Android < 13: разрешение не требуется
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeNotifPermissionResult==='function')" +
                    "onNativeNotifPermissionResult('granted')", null));
            }
        }

        /** Показывает системное уведомление */
        @JavascriptInterface
        public void showNotification(String title, String body) {
            log.i(TAG, "showNotification: " + title + " — " + body);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            }
            Intent tapIntent = new Intent(MainActivity.this, MainActivity.class);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(
                MainActivity.this, 0, tapIntent, flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    MainActivity.this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVibrate(new long[]{0, 250, 100, 250});

            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, builder.build());
        }

        // ─── DNS / DPI / VPN ──────────────────────────────────────────────────────

        @JavascriptInterface
        public void setDns(String key, String dohUrl) {
            log.i(TAG, "JS→setDns key=" + key + " url=" + dohUrl);
            prefs.edit().putString("dns_key", key).putString("doh_url", dohUrl).apply();
            if (vpnBound && vpnService != null && vpnService.isRunning()) {
                vpnService.updateDns(dohUrl);
            }
        }

        @JavascriptInterface
        public void setDpiStrategy(String strategyId) {
            log.i(TAG, "JS→setDpiStrategy " + strategyId);
            prefs.edit().putString("dpi_strategy", strategyId).apply();
            if (vpnBound && vpnService != null && vpnService.isRunning()) {
                vpnService.updateDpiStrategy(strategyId);
            }
        }

        @JavascriptInterface
        public void toggleVpn() {
            boolean running = vpnBound && vpnService != null && vpnService.isRunning();
            log.i(TAG, "JS→toggleVpn running=" + running);
            if (running) {
                stopVpnService();
                webView.post(() -> updateVpnStateInWeb());
            } else {
                Intent permIntent = VpnService.prepare(MainActivity.this);
                if (permIntent != null) {
                    startActivityForResult(permIntent, VPN_REQUEST_CODE);
                } else {
                    startVpnService();
                }
            }
        }

        @JavascriptInterface
        public String getVpnState() {
            try {
                boolean active = vpnBound && vpnService != null && vpnService.isRunning();
                String text = active
                    ? "DNS: " + prefs.getString("dns_key", "system").toUpperCase()
                      + " • DPI: " + prefs.getString("dpi_strategy", "general")
                    : "DNS и DPI обход неактивны";
                JSONObject obj = new JSONObject();
                obj.put("active", active);
                obj.put("text", text);
                return obj.toString();
            } catch (Exception e) {
                log.e(TAG, "getVpnState error", e);
                return "{}";
            }
        }

        @JavascriptInterface
        public String getSavedPrefs() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("dns", prefs.getString("dns_key", "system"));
                obj.put("doh", prefs.getString("doh_url", ""));
                obj.put("dpi", prefs.getString("dpi_strategy", "general"));
                return obj.toString();
            } catch (Exception e) {
                log.e(TAG, "getSavedPrefs error", e);
                return "{}";
            }
        }

        /** Вызывается из window.onerror через JS */
        @JavascriptInterface
        public void logError(String message) {
            log.e("JS:ERROR", message);
        }

        /** Короткий лог из JS: Android.log('текст') */
        @JavascriptInterface
        public void log(String message) {
            log.i("JS:IN", message);
        }

        /** Общий лог из JS: Android.logMsg('INFO', 'текст') */
        @JavascriptInterface
        public void logMsg(String level, String message) {
            log.js(level != null ? level : "LOG", message, "JS", 0);
        }

        /** Показать диалог с путём к лог-файлу */
        @JavascriptInterface
        public void showLogPath() {
            runOnUiThread(() ->
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Лог-файл")
                    .setMessage("Папка Download\nФайл: ScheduleApp_logs.txt")
                    .setPositiveButton("OK", null)
                    .show()
            );
        }

        /**
         * Нативный HTTP GET без CORS — возвращает JSON: {ok, status, body, error?}
         * Для GitHub URL автоматически пробует зеркала (обход блокировок в России).
         */
        @JavascriptInterface
        public String nativeFetch(String urlStr) {
            log.i(TAG, "nativeFetch: " + urlStr);
            java.net.Proxy proxy = buildProxy();

            // Для GitHub API — пробуем зеркала если оригинал не ответил
            if (urlStr != null && urlStr.contains("api.github.com")) {
                String[] mirrors = {
                    urlStr,
                    urlStr.replace("api.github.com", "api.github.moeyy.xyz"),
                    urlStr.replace("api.github.com", "api.kgithub.com"),
                };
                for (String mirrorUrl : mirrors) {
                    String result = nativeFetchInternal(mirrorUrl, proxy);
                    try {
                        org.json.JSONObject r = new org.json.JSONObject(result);
                        if (r.optBoolean("ok", false)) {
                            log.i(TAG, "nativeFetch github mirror OK: " + mirrorUrl);
                            return result;
                        }
                    } catch (Exception ignored) {}
                    log.w(TAG, "nativeFetch github mirror failed: " + mirrorUrl);
                }
                // Все зеркала провалились — вернём последний результат
                return nativeFetchInternal(urlStr, proxy);
            }

            return nativeFetchInternal(urlStr, proxy);
        }

        /**
         * Нативное скачивание файла без CORS — возвращает JSON: {ok, base64, error?}
         * base64 — содержимое файла в Base64, декодируется в JS в ArrayBuffer.
         */
        @JavascriptInterface
        public String nativeDownloadBase64(String urlStr) {
            log.i(TAG, "nativeDownloadBase64: " + urlStr);
            return nativeDownloadBase64Internal(urlStr, buildProxy());
        }

        /**
         * Установить SOCKS5 прокси (например, от Telegram).
         * Вызов: Android.setSocksProxy('proxy.example.com', 1080, 'user', 'pass')
         * Для сброса: Android.setSocksProxy('', 0, '', '')
         */
        @JavascriptInterface
        public void setSocksProxy(String host, int port, String username, String password) {
            log.i(TAG, "setSocksProxy host=" + host + " port=" + port);
            prefs.edit()
                .putString("proxy_host", host)
                .putInt("proxy_port", port)
                .putString("proxy_user", username != null ? username : "")
                .putString("proxy_pass", password != null ? password : "")
                .apply();
        }

        /**
         * Получить текущие настройки прокси — JSON: {host, port, user}
         */
        @JavascriptInterface
        public String getProxySettings() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("host", prefs.getString("proxy_host", ""));
                obj.put("port", prefs.getInt("proxy_port", 0));
                obj.put("user", prefs.getString("proxy_user", ""));
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        /**
         * Переключает иконку приложения через activity-alias.
         * icon: "dark" | "orange" | "amoled" | "samek"
         * Показывает диалог-предупреждение, т.к. Android вынужден
         * закрыть приложение при смене активного alias'а.
         */
        @JavascriptInterface
        public void setAppIcon(String icon) {
            final String pkg = getPackageName();
            final String[] allAliases = {
                pkg + ".IconDark",
                pkg + ".IconOrange",
                pkg + ".IconAmoled",
                pkg + ".IconSamek",
                pkg + ".IconPurple",
                pkg + ".IconForest",
                pkg + ".IconGold",
                pkg + ".IconGlass",
                pkg + ".IconWin11",
                pkg + ".IconPixel",
                pkg + ".IconAero",
                pkg + ".IconRose",
                pkg + ".IconSunset",
                pkg + ".IconBw",
                pkg + ".IconLight",
                pkg + ".IconCandy",
                pkg + ".IconOcean"
            };
            final String target;
            switch (icon) {
                case "dark":   target = pkg + ".IconDark";   break;
                case "amoled": target = pkg + ".IconAmoled"; break;
                case "samek":  target = pkg + ".IconSamek";  break;
                case "purple": target = pkg + ".IconPurple"; break;
                case "forest": target = pkg + ".IconForest"; break;
                case "gold":   target = pkg + ".IconGold";   break;
                case "glass":  target = pkg + ".IconGlass";  break;
                case "win11":  target = pkg + ".IconWin11";  break;
                case "pixel":  target = pkg + ".IconPixel";  break;
                case "aero":   target = pkg + ".IconAero";   break;
                case "rose":   target = pkg + ".IconRose";   break;
                case "sunset": target = pkg + ".IconSunset"; break;
                case "bw":     target = pkg + ".IconBw";     break;
                case "light":  target = pkg + ".IconLight";  break;
                case "candy":  target = pkg + ".IconCandy";  break;
                case "ocean":  target = pkg + ".IconOcean";  break;
                default:       target = pkg + ".IconOrange"; break;
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Сменить иконку?")
                    .setMessage("После смены иконки приложение закроется — это стандартное поведение Android. " +
                                "Ярлык на главном экране нужно будет добавить заново.\n\n" +
                                "Продолжить?")
                    .setPositiveButton("Сменить", (d, w) -> {
                        // Сначала включаем новый alias
                        getPackageManager().setComponentEnabledSetting(
                            new ComponentName(pkg, target),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        );
                        // Отключаем остальные с небольшой задержкой
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            for (String alias : allAliases) {
                                if (!alias.equals(target)) {
                                    getPackageManager().setComponentEnabledSetting(
                                        new ComponentName(pkg, alias),
                                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                        android.content.pm.PackageManager.DONT_KILL_APP
                                    );
                                }
                            }
                            // Корректно завершаем приложение сами — иначе Android сделает это грубо
                            finishAffinity();
                        }, 500);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            });
        }

        /**
         * Открывает системный пикер изображений для выбора фона.
         * Результат возвращается через JS-функцию onNativeBgImagePicked(dataUrl)
         */
        @JavascriptInterface
        public void pickImageForBackground() {
            log.i(TAG, "pickImageForBackground");
            runOnUiThread(() -> {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                }
                isNativeBgPick = true; // помечаем: результат идёт напрямую в onNativeBgImagePicked
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Выбери фоновое изображение"),
                        IMAGE_PICK_CODE
                    );
                } catch (Exception e) {
                    isNativeBgPick = false;
                    log.e(TAG, "pickImageForBackground error: " + e.getMessage());
                    webView.evaluateJavascript(
                        "if(typeof toast==='function')toast('❌ Не удалось открыть галерею')",
                        null
                    );
                }
            });
        }

        /**
         * Переключает ориентацию экрана для DOOM и других игр
         * orientation: "landscape" | "portrait"
         */
        @JavascriptInterface
        public void setOrientation(String orientation) {
            log.i(TAG, "JS→setOrientation: " + orientation);
            runOnUiThread(() -> {
                if ("landscape".equals(orientation)) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                }
            });
        }

        /**
         * OTA: открыть URL в браузере (для скачивания APK обновления)
         */
        @JavascriptInterface
        public void openUrl(String urlStr) {
            log.i(TAG, "openUrl: " + urlStr);
            // http/https → встроенный браузер; остальные схемы → внешнее приложение
            if (urlStr != null && (urlStr.startsWith("http://") || urlStr.startsWith("https://"))) {
                openInAppBrowser(urlStr);
                return;
            }
            runOnUiThread(() -> {
                try {
                    android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(urlStr)
                    );
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    log.e(TAG, "openUrl error: " + e.getMessage());
                }
            });
        }

        /**
         * Открыть URL во встроенном браузере (оверлей поверх приложения).
         * Вызывается из JS: Android.openInAppBrowser(url)
         */
        @JavascriptInterface
        public void openInAppBrowser(String urlStr) {
            log.i(TAG, "openInAppBrowser: " + urlStr);
            runOnUiThread(() -> {
                // Закрываем предыдущий если был
                if (_browserOverlay != null) closeBrowser();

                android.content.Context ctx = MainActivity.this;

                // ── Корневой контейнер ───────────────────────────────────
                _browserOverlay = new android.widget.FrameLayout(ctx);
                _browserOverlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                _browserOverlay.setBackgroundColor(0xFF0F0F0F);



                // ── Внутренний WebView (без панели — Back закрывает) ─────────
                _browserWebView = new WebView(ctx);
                android.webkit.WebSettings ws = _browserWebView.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setLoadWithOverviewMode(true);
                ws.setUseWideViewPort(true);
                ws.setBuiltInZoomControls(true);
                ws.setDisplayZoomControls(false);
                ws.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                ws.setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                _browserWebView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));

                _browserWebView.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        // intent:// и другие не-http схемы — пробуем открыть нативно
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            try {
                                android.content.Intent intent;
                                if (url.startsWith("intent://")) {
                                    intent = android.content.Intent.parseUri(url,
                                        android.content.Intent.URI_INTENT_SCHEME);
                                } else {
                                    intent = new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url));
                                }
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } catch (Exception e) {
                                log.e(TAG, "browser intent error: " + e.getMessage());
                            }
                            return true; // не грузим в WebView
                        }
                        return false;
                    }
                    @Override
                    public void onPageFinished(android.webkit.WebView view, String url) {}
                });

                _browserOverlay.addView(_browserWebView);

                // ── Добавляем поверх DecorView ───────────────────────────
                android.view.ViewGroup decorView = (android.view.ViewGroup) getWindow().getDecorView();
                decorView.addView(_browserOverlay);

                _browserWebView.loadUrl(urlStr);
            });
        }



        /**
         * Открытие банковского deep-link с fallback на web-URL.
         * Если приложение банка не установлено — открывает веб-версию в браузере.
         */
        @JavascriptInterface
        public void openBankDeeplink(String deeplink, String webUrl) {
            log.i(TAG, "openBankDeeplink: " + deeplink);
            runOnUiThread(() -> {
                // Сначала пробуем deep-link банка
                boolean opened = false;
                try {
                    android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(deeplink)
                    );
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    // Проверяем, есть ли хоть одно приложение для этого intent
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                        opened = true;
                        log.i(TAG, "openBankDeeplink: opened via deeplink");
                    }
                } catch (Exception e) {
                    log.e(TAG, "openBankDeeplink deeplink error: " + e.getMessage());
                }
                // Fallback: открываем web-версию
                if (!opened) {
                    try {
                        android.content.Intent webIntent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(webUrl)
                        );
                        webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(webIntent);
                        log.i(TAG, "openBankDeeplink: fallback to webUrl");
                    } catch (Exception e2) {
                        log.e(TAG, "openBankDeeplink webUrl error: " + e2.getMessage());
                    }
                }
            });
        }

        /**
         * OTA: диалог с предложением обновить приложение
         */
        @JavascriptInterface
        public void showUpdateDialog(String version, String apkUrl, String notes) {
            log.i(TAG, "showUpdateDialog v" + version);
            runOnUiThread(() ->
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Доступно обновление " + version)
                    .setMessage("Что нового:\n" + notes + "\n\nСкачать и установить?")
                    .setPositiveButton("Скачать", (d, w) -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(apkUrl)
                            );
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } catch (Exception e) {
                            log.e(TAG, "Download APK error: " + e.getMessage());
                        }
                    })
                    .setNegativeButton("Позже", null)
                    .show()
            );
        }

        /**
         * OTA: очистить кэш WebView
         */
        @JavascriptInterface
        public void clearWebViewCache() {
            log.i(TAG, "clearWebViewCache");
            runOnUiThread(() -> {
                webView.clearCache(true);
                webView.clearHistory();
            });
        }

        /**
         * Горячий патч: сохраняет файл в filesDir/hotpatch/<assetPath>.
         * JS вызывает это после скачивания изменённого файла.
         * shouldInterceptRequest подхватит его при следующей загрузке.
         * @param assetPath  относительный путь, напр. "js/social.js" или "index.html"
         * @param content    содержимое файла (UTF-8 строка)
         * @return "ok" или сообщение об ошибке
         */
        @JavascriptInterface
        public String hotPatchSaveFile(String assetPath, String content) {
            try {
                // Безопасность: запрещаем path traversal
                if (assetPath.contains("..") || assetPath.startsWith("/")) {
                    return "err: invalid path";
                }
                java.io.File dir = new java.io.File(getFilesDir(), "hotpatch");
                java.io.File target = new java.io.File(dir, assetPath);
                target.getParentFile().mkdirs();
                java.io.FileWriter fw = new java.io.FileWriter(target, false);
                fw.write(content);
                fw.close();
                log.i(TAG, "hotPatchSaveFile: saved " + assetPath + " (" + content.length() + " chars)");
                return "ok";
            } catch (Exception e) {
                log.e(TAG, "hotPatchSaveFile error: " + e.getMessage());
                return "err: " + e.getMessage();
            }
        }

        /**
         * Горячий патч: возвращает список установленных патчей (JSON массив путей).
         */
        @JavascriptInterface
        public String hotPatchList() {
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "hotpatch");
                if (!dir.exists()) return "[]";
                org.json.JSONArray arr = new org.json.JSONArray();
                listFilesRecursive(dir, dir, arr);
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        private void listFilesRecursive(java.io.File root, java.io.File current, org.json.JSONArray arr) {
            java.io.File[] files = current.listFiles();
            if (files == null) return;
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    listFilesRecursive(root, f, arr);
                } else {
                    String rel = root.toURI().relativize(f.toURI()).getPath();
                    arr.put(rel);
                }
            }
        }

        /**
         * Горячий патч: удалить все патчи (откат к assets).
         */
        @JavascriptInterface
        public void hotPatchClearAll() {
            try {
                java.io.File dir = new java.io.File(getFilesDir(), "hotpatch");
                deleteRecursive(dir);
                log.i(TAG, "hotPatchClearAll: done");
            } catch (Exception e) {
                log.e(TAG, "hotPatchClearAll error: " + e.getMessage());
            }
        }

        private void deleteRecursive(java.io.File f) {
            if (f.isDirectory()) {
                java.io.File[] ch = f.listFiles();
                if (ch != null) for (java.io.File c : ch) deleteRecursive(c);
            }
            f.delete();
        }

        // ── Emoji Pack ────────────────────────────────────────────────────────

        // ══ Emoji Pack ════════════════════════════════════════════════════════

        /**
         * true если emoji-пак полностью распакован.
         * Проверяет маркер-файл .ready — создаётся только после успешной распаковки.
         * Персистентно: filesDir сохраняется между перезапусками приложения.
         */
        @JavascriptInterface
        public boolean isEmojiPackReady() {
            java.io.File emojiDir = new java.io.File(getFilesDir(), "emoji");
            java.io.File marker   = new java.io.File(emojiDir, ".ready");
            // Обнаруживаем старую структуру (emoji/emoji/act/...) — сбрасываем маркер
            // чтобы при следующем запуске пак переразвернулся с правильными путями
            java.io.File staleSubdir = new java.io.File(emojiDir, "emoji");
            if (staleSubdir.exists() && staleSubdir.isDirectory()) {
                log.w(TAG, "isEmojiPackReady: обнаружена старая структура emoji/emoji/, сброс маркера для переразвёртки");
                marker.delete();
                deleteRecursive(emojiDir);
                emojiDir.mkdirs();
            }
            boolean ready = marker.exists();
            log.i(TAG, "isEmojiPackReady: " + ready);
            return ready;
        }

        /**
         * Скачивает ZIP-архив emoji-пака и распаковывает в filesDir/emoji/.
         * Запускается в фоновом потоке — не блокирует UI.
         * Прогресс: вызывает JS-функцию onEmojiPackProgress(pct, label, isDone).
         *   pct = 0-100 (прогресс), -1 = ошибка
         *   isDone = true когда пак полностью готов
         *
         * Сохранение персистентно: filesDir не очищается при перезапуске приложения.
         * Очищается только при "Очистить данные" в настройках или удалении приложения.
         */
        @JavascriptInterface
        public void downloadEmojiPackZip(final String zipUrl) {
            log.i(TAG, "downloadEmojiPackZip: " + zipUrl);
            new Thread(() -> {
                java.io.File emojiDir = new java.io.File(getFilesDir(), "emoji");
                java.io.File tmpZip   = new java.io.File(getFilesDir(), "emoji_pack.zip");
                java.io.File marker   = new java.io.File(emojiDir, ".ready");

                // Чистим предыдущую незавершённую попытку
                marker.delete();
                deleteRecursive(emojiDir);
                emojiDir.mkdirs();
                tmpZip.delete();

                try {
                    // 1. Скачиваем ZIP (с зеркалами для России)
                    final String[] mirrors = {
                        zipUrl,
                        zipUrl.replace("github.com",
                            "ghproxy.com/https://github.com"),
                        zipUrl.replace("github.com",
                            "mirror.ghproxy.com/https://github.com"),
                        zipUrl.replace("github.com",
                            "ghfast.top/https://github.com"),
                    };

                    boolean downloaded = false;
                    for (String mirrorUrl : mirrors) {
                        try {
                            _jsCallback("onEmojiPackProgress(0,'⬇ Подключаюсь...',false)");
                            java.net.URL url = new java.net.URL(mirrorUrl);
                            java.net.HttpURLConnection conn =
                                (java.net.HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(20_000);
                            conn.setReadTimeout(120_000);
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 ScheduleApp");
                            conn.setInstanceFollowRedirects(true);
                            conn.connect();
                            if (conn.getResponseCode() != 200) {
                                conn.disconnect(); continue;
                            }
                            long total = conn.getContentLengthLong();
                            java.io.InputStream is = conn.getInputStream();
                            java.io.FileOutputStream fos =
                                new java.io.FileOutputStream(tmpZip);
                            byte[] buf = new byte[65536]; int n; long got = 0;
                            while ((n = is.read(buf)) != -1) {
                                fos.write(buf, 0, n);
                                got += n;
                                if (total > 0 && got % (512 * 1024) == 0) {
                                    int pct = (int)(got * 45 / total);
                                    String label = "⬇ " + (got/1024/1024) +
                                        " / " + (total/1024/1024) + " МБ";
                                    _jsCallback("onEmojiPackProgress(" + pct +
                                        ",'" + label + "',false)");
                                }
                            }
                            fos.close(); is.close(); conn.disconnect();
                            downloaded = true;
                            log.i(TAG, "emoji ZIP downloaded: " + tmpZip.length());
                            break;
                        } catch (Exception e) {
                            log.w(TAG, "emoji mirror fail: " + e.getMessage());
                        }
                    }

                    if (!downloaded || tmpZip.length() < 1024)
                        throw new Exception("Не удалось скачать архив");

                    // 2. Считаем записи для точного прогресса
                    _jsCallback("onEmojiPackProgress(46,'📦 Распаковываю...',false)");
                    int totalEntries = 0;
                    {
                        java.util.zip.ZipInputStream ctr =
                            new java.util.zip.ZipInputStream(
                                new java.io.BufferedInputStream(
                                    new java.io.FileInputStream(tmpZip)));
                        while (ctr.getNextEntry() != null) {
                            totalEntries++; ctr.closeEntry();
                        }
                        ctr.close();
                    }

                    // 3. Распаковываем
                    java.util.zip.ZipInputStream zis =
                        new java.util.zip.ZipInputStream(
                            new java.io.BufferedInputStream(
                                new java.io.FileInputStream(tmpZip)));
                    java.util.zip.ZipEntry entry;
                    int extracted = 0;
                    // Определяем общий префикс верхнего уровня (напр. "emoji-main/")
                    // GitHub archive генерирует zip вида: emoji-main/emoji/act/Fire.png
                    // Нам нужно срезать "emoji-main/" чтобы получить "emoji/act/Fire.png"
                    String stripPrefix = null;
                    {
                        java.util.zip.ZipInputStream sniffer =
                            new java.util.zip.ZipInputStream(
                                new java.io.BufferedInputStream(
                                    new java.io.FileInputStream(tmpZip)));
                        java.util.zip.ZipEntry snEntry;
                        while ((snEntry = sniffer.getNextEntry()) != null) {
                            String sn = snEntry.getName();
                            if (sn.contains("/")) {
                                stripPrefix = sn.substring(0, sn.indexOf('/') + 1);
                                sniffer.closeEntry(); break;
                            }
                            sniffer.closeEntry();
                        }
                        sniffer.close();
                    }
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        // Срезаем общий префикс верхнего уровня (напр. "emoji-main/")
                        if (stripPrefix != null && name.startsWith(stripPrefix)) {
                            name = name.substring(stripPrefix.length());
                        }
                        // GitHub archive: emoji-main/emoji/act/Fire.png
                        // После срезания emoji-main/ остаётся emoji/act/Fire.png
                        // Срезаем ещё один уровень "emoji/" чтобы файлы легли в
                        // filesDir/emoji/act/Fire.png (а не filesDir/emoji/emoji/act/...)
                        if (name.startsWith("emoji/")) {
                            name = name.substring("emoji/".length());
                        }
                        if (name.isEmpty() || name.contains("..") || name.startsWith("/")) {
                            zis.closeEntry(); continue;
                        }
                        java.io.File out = new java.io.File(emojiDir, name);
                        if (entry.isDirectory()) {
                            out.mkdirs();
                        } else {
                            out.getParentFile().mkdirs();
                            java.io.FileOutputStream fos =
                                new java.io.FileOutputStream(out);
                            byte[] b = new byte[8192]; int r;
                            while ((r = zis.read(b)) != -1) fos.write(b, 0, r);
                            fos.close();
                        }
                        extracted++;
                        zis.closeEntry();
                        if (totalEntries > 0 && extracted % 200 == 0) {
                            int pct = 46 + (int)(extracted * 54L / totalEntries);
                            _jsCallback("onEmojiPackProgress(" + pct +
                                ",'📦 " + extracted + "/" + totalEntries + "',false)");
                        }
                    }
                    zis.close();
                    tmpZip.delete();

                    // 4. Маркер готовности — записываем количество файлов
                    java.io.FileWriter fw = new java.io.FileWriter(marker);
                    fw.write(String.valueOf(extracted));
                    fw.close();

                    log.i(TAG, "emoji pack ready, files: " + extracted);
                    _jsCallback("onEmojiPackProgress(100,'✅ Эмодзи готовы! ("
                        + extracted + " файлов)',true)");

                } catch (Exception e) {
                    log.e(TAG, "emoji pack error: " + e.getMessage());
                    tmpZip.delete();
                    deleteRecursive(emojiDir);
                    emojiDir.mkdirs();
                    String msg = e.getMessage() != null
                        ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 50))
                              .replace("'", "")
                        : "неизвестная ошибка";
                    _jsCallback("onEmojiPackProgress(-1,'❌ " + msg + "',false)");
                }
            }, "emoji-zip-dl").start();
        }

        /** Вызывает JS-функцию из фонового потока через WebView.post() */
        private void _jsCallback(final String js) {
            if (webView != null) {
                String safe = js.replace("\\", "\\\\");
                webView.post(() -> webView.evaluateJavascript(
                    "(function(){try{" + safe + "}catch(e){}})()", null));
            }
        }

        /**
         * Удаляет emoji-пак и маркер — при следующем запуске скачается снова.
         */
        @JavascriptInterface
        public void clearEmojiPack() {
            try {
                java.io.File emojiDir = new java.io.File(getFilesDir(), "emoji");
                deleteRecursive(emojiDir);
                emojiDir.mkdirs();
                log.i(TAG, "clearEmojiPack: done");
            } catch (Exception e) {
                log.e(TAG, "clearEmojiPack error: " + e.getMessage());
            }
        }

        /**
         * Скачивает APK через Java (без WebView, без CORS) и запускает установщик.
         * Прогресс передаётся в JS через onOtaProgress(pct, label).
         * Список зеркал пробуется последовательно.
         */
        @JavascriptInterface
        public void downloadAndInstallApk(String originalUrl) {
            log.i(TAG, "downloadAndInstallApk: " + originalUrl);
            final String[] candidates = {
                originalUrl,
                "https://ghproxy.com/"        + originalUrl,
                "https://mirror.ghproxy.com/" + originalUrl,
                "https://ghfast.top/"         + originalUrl,
                "https://gh.con.sh/"          + originalUrl,
            };
            new Thread(() -> {
                java.io.File apkDir  = new java.io.File(getFilesDir(), "apk");
                if (!apkDir.exists()) apkDir.mkdirs();
                java.io.File apkFile = new java.io.File(apkDir, "update.apk");
                if (apkFile.exists()) apkFile.delete();

                Runnable notifyProgress = null; // будет переназначен ниже
                Exception lastError = null;

                for (String urlStr : candidates) {
                    try {
                        log.i(TAG, "Trying: " + urlStr);
                        _jsProgress(5, "⏳ Подключаюсь...");

                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) url.openConnection(buildProxy());
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(20000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 ScheduleApp/" + getVersionName());
                        conn.setInstanceFollowRedirects(true);

                        int status = conn.getResponseCode();
                        if (status < 200 || status >= 300) {
                            conn.disconnect();
                            lastError = new Exception("HTTP " + status);
                            log.w(TAG, "Mirror HTTP " + status + ": " + urlStr);
                            continue;
                        }

                        long total = conn.getContentLengthLong();
                        java.io.InputStream is = conn.getInputStream();
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(apkFile);
                        byte[] buf = new byte[16384];
                        long done = 0; int n;
                        while ((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                            done += n;
                            if (total > 0) {
                                // 5%–90% — реальная загрузка
                                final int pct = 5 + (int)(done * 85 / total);
                                final String lbl = pct + "% — Скачиваю...";
                                _jsProgress(pct, lbl);
                            }
                        }
                        fos.close(); is.close(); conn.disconnect();
                        log.i(TAG, "Downloaded: " + apkFile.length() + " bytes");

                        _jsProgress(95, "Устанавливаю...");

                        // Запускаем установщик
                        runOnUiThread(() -> {
                            try {
                                android.net.Uri apkUri =
                                    androidx.core.content.FileProvider.getUriForFile(
                                        MainActivity.this,
                                        getPackageName() + ".fileprovider", apkFile);
                                android.content.Intent intent = new android.content.Intent(
                                    android.content.Intent.ACTION_INSTALL_PACKAGE);
                                intent.setDataAndType(apkUri,
                                    "application/vnd.android.package-archive");
                                intent.addFlags(
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                _jsProgress(100, "✅ Готово!");
                                webView.postDelayed(() -> webView.evaluateJavascript(
                                    "if(typeof otaClose==='function')otaClose()", null), 1500);
                            } catch (Exception e) {
                                log.e(TAG, "Install error: " + e.getMessage());
                                _jsError("❌ Не удалось запустить установщик: " + e.getMessage());
                            }
                        });
                        return;

                    } catch (Exception e) {
                        lastError = e;
                        log.w(TAG, "Mirror error: " + urlStr + " → " + e.getMessage());
                    }
                }

                // Все зеркала провалились
                final String msg = lastError != null ? lastError.getMessage() : "Нет соединения";
                log.e(TAG, "All mirrors failed: " + msg);
                _jsError("❌ Не удалось скачать: " + msg);
            }).start();
        }

        private void _jsProgress(int pct, String label) {
            final String js = String.format(
                "var b=document.getElementById('ota-progress-bar');" +
                "var l=document.getElementById('ota-progress-label');" +
                "if(b)b.style.width='%d%%';" +
                "if(l)l.textContent='%s';",
                pct, label.replace("'", "\\'"));
            webView.post(() -> webView.evaluateJavascript(js, null));
        }

        private void _jsError(String message) {
            final String js = String.format(
                "var l=document.getElementById('ota-progress-label');" +
                "if(l)l.textContent='%s';" +
                "var b=document.getElementById('ota-dl-btn');" +
                "if(b){b.disabled=false;b.textContent='⬇ Попробовать снова';}" +
                "var c=document.getElementById('ota-cancel-btn');" +
                "if(c)c.disabled=false;",
                message.replace("'", "\\'"));
            webView.post(() -> webView.evaluateJavascript(js, null));
        }

        /**
         * Загружает файл на catbox.moe синхронно (блокирует JS-поток).
         * Используй nativeUploadFileAsync для фоновой загрузки.
         *
         * @param base64   содержимое файла в Base64 (без data:... префикса)
         * @param fileName имя файла с расширением
         * @param mimeType MIME-тип
         * @return JSON {ok:true, url:"https://files.catbox.moe/…"} или {ok:false, error:"…"}
         */
        @JavascriptInterface
        public String nativeUploadFile(String base64, String fileName, String mimeType) {
            log.i(TAG, "nativeUploadFile: " + fileName + " mime=" + mimeType
                    + " b64len=" + (base64 != null ? base64.length() : 0));
            try {
                byte[] fileBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                return _catboxUploadBytes(fileBytes, fileName, mimeType, null);
            } catch (Exception e) {
                log.e(TAG, "nativeUploadFile error: " + e.getMessage());
                try {
                    org.json.JSONObject err = new org.json.JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        /**
         * Асинхронная загрузка файла на catbox.moe — не блокирует UI.
         * Прогресс и результат приходят через JS-колбэки:
         *   onUploadProgress(callbackId, pct)   — 0..100
         *   onUploadDone(callbackId, url)        — успех
         *   onUploadError(callbackId, errMsg)    — ошибка
         *
         * @param base64     файл в Base64
         * @param fileName   имя файла
         * @param mimeType   MIME-тип
         * @param callbackId произвольный ID для колбэков
         */
        @JavascriptInterface
        public void nativeUploadFileAsync(String base64, String fileName,
                                          String mimeType, String callbackId) {
            log.i(TAG, "nativeUploadFileAsync: " + fileName + " id=" + callbackId
                    + " b64len=" + (base64 != null ? base64.length() : 0));
            final String id = callbackId != null ? callbackId : "upload_" + System.currentTimeMillis();
            new Thread(() -> {
                try {
                    byte[] fileBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    UploadProgressCallback progressCb = (pct) -> {
                        final int p = pct;
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onUploadProgress==='function')onUploadProgress('"
                            + id + "'," + p + ")", null));
                    };
                    // Сначала пробуем pixeldrain (быстрее)
                    String result = null;
                    log.i(TAG, "trying pixeldrain...");
                    try {
                        result = _pixeldrainUploadBytes(fileBytes, fileName, mimeType, progressCb);
                        if (!new org.json.JSONObject(result).optBoolean("ok")) {
                            log.w(TAG, "pixeldrain failed, fallback catbox");
                            result = null;
                        }
                    } catch (Exception pdEx) {
                        log.w(TAG, "pixeldrain error: " + pdEx.getMessage() + " -> catbox");
                        result = null;
                    }
                    if (result == null) {
                        log.i(TAG, "using catbox fallback");
                        result = _catboxUploadBytes(fileBytes, fileName, mimeType, progressCb);
                    }
                    org.json.JSONObject res = new org.json.JSONObject(result);
                    if (res.optBoolean("ok")) {
                        final String fileUrl = res.getString("url");
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onUploadDone==='function')onUploadDone('"
                            + id + "','" + fileUrl + "')", null));
                    } else {
                        final String err = res.optString("error", "Upload failed");
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onUploadError==='function')onUploadError('"
                            + id + "','" + err.replace("'", "\\'") + "')", null));
                    }
                } catch (Exception e) {
                    log.e(TAG, "nativeUploadFileAsync error: " + e.getMessage());
                    final String err = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof onUploadError==='function')onUploadError('"
                        + id + "','" + err.replace("'", "\\'") + "')", null));
                }
            }).start();
        }

        /**
         * Внутренняя загрузка на catbox.moe.
         * progressCb может быть null — тогда прогресс не отслеживается.
         */
        // Публичные обёртки — для вызова из MainActivity.onActivityResult
        String _catboxUploadBytesPublic(byte[] b, String fn, String mt) {
            return _catboxUploadBytes(b, fn, mt, null);
        }
        String _pixeldrainUploadBytesPublic(byte[] b, String fn, String mt) {
            return _pixeldrainUploadBytes(b, fn, mt, null);
        }

        /**
         * Загружает файл на GitHub через Contents API (PUT).
         * Вызывается из JS: Android.nativeGitHubUpload(apiUrl, bodyJson, pat, callbackId)
         * По завершении вызывает window.__ghUpDone_<id>(rawUrl) или window.__ghUpErr_<id>(errMsg).
         */
        @android.webkit.JavascriptInterface
        public void nativeGitHubUpload(final String apiUrl, final String bodyJson,
                                       final String pat, final String callbackId) {
            log.i(TAG, "nativeGitHubUpload: " + apiUrl.substring(0, Math.min(80, apiUrl.length())));
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("Accept",        "application/vnd.github.v3+json");
                    conn.setRequestProperty("Authorization", "Bearer " + pat);
                    conn.setRequestProperty("Content-Type",  "application/json");
                    conn.setRequestProperty("User-Agent",    "ScheduleApp/1.0");
                    conn.setConnectTimeout(20_000);
                    conn.setReadTimeout(120_000);
                    conn.setDoOutput(true);

                    byte[] bodyBytes = bodyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(bodyBytes.length);
                    conn.getOutputStream().write(bodyBytes);
                    conn.getOutputStream().flush();

                    int code = conn.getResponseCode();
                    java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                    String resp = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))
                        .lines().collect(java.util.stream.Collectors.joining());

                    if (code < 200 || code >= 300) {
                        throw new Exception("HTTP " + code + ": " + resp.substring(0, Math.min(120, resp.length())));
                    }

                    // Строим raw-URL из apiUrl
                    // apiUrl: https://api.github.com/repos/owner/repo/contents/folder/file
                    // rawUrl: https://raw.githubusercontent.com/owner/repo/main/folder/file
                    String rawUrl = apiUrl
                        .replace("api.github.com/repos/", "raw.githubusercontent.com/")
                        .replace("/contents/", "/main/");

                    final String safeUrl = rawUrl.replace("'", "\'");
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof window['__ghUpDone_" + callbackId + "']==='function')" +
                        "window['__ghUpDone_" + callbackId + "']('" + safeUrl + "')", null));

                } catch (Exception e) {
                    log.e(TAG, "nativeGitHubUpload error: " + e.getMessage());
                    final String err = (e.getMessage() != null ? e.getMessage() : "Unknown")
                        .replace("'", "\'");
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof window['__ghUpErr_" + callbackId + "']==='function')" +
                        "window['__ghUpErr_" + callbackId + "']('" + err + "')", null));
                }
            }).start();
        }

        private String _catboxUploadBytes(byte[] fileBytes, String fileName,
                                           String mimeType, UploadProgressCallback progressCb) {
            String boundary = "----CatboxBoundary" + Long.toHexString(System.currentTimeMillis());
            try {
                // Собираем multipart-части заранее чтобы знать Content-Length
                byte[] partReqBytes = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n"
                    + "fileupload\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] partFileHeaderBytes = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                    + fileName + "\"\r\n"
                    + "Content-Type: " + mimeType + "\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] partEndBytes = ("\r\n--" + boundary + "--\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);

                long totalLen = partReqBytes.length + partFileHeaderBytes.length
                    + fileBytes.length + partEndBytes.length;

                java.net.URL url = new java.net.URL("https://catbox.moe/user/api.php");
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection(buildProxy());
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(totalLen);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(180000);
                conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 ScheduleApp/" + getVersionName());

                java.io.OutputStream os = conn.getOutputStream();
                os.write(partReqBytes);
                os.write(partFileHeaderBytes);

                // Пишем файл чанками с отслеживанием прогресса
                int chunkSize = 65536; // 64KB
                long written = partReqBytes.length + partFileHeaderBytes.length;
                for (int off = 0; off < fileBytes.length; off += chunkSize) {
                    int len = Math.min(chunkSize, fileBytes.length - off);
                    os.write(fileBytes, off, len);
                    written += len;
                    if (progressCb != null && totalLen > 0) {
                        int pct = (int)(written * 95 / totalLen); // 0-95%, 100% — после ответа
                        progressCb.onProgress(pct);
                    }
                }
                os.write(partEndBytes);
                os.flush();
                os.close();

                int status = conn.getResponseCode();
                java.io.InputStream is = status < 400
                    ? conn.getInputStream() : conn.getErrorStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                is.close();
                conn.disconnect();
                String body = baos.toString("UTF-8").trim();
                log.i(TAG, "_catboxUploadBytes: status=" + status + " body=" + body.substring(0, Math.min(body.length(), 120)));

                org.json.JSONObject res = new org.json.JSONObject();
                // catbox возвращает 200 + URL файла (может быть http:// или https://)
                if ((status == 200 || status == 201)
                        && (body.startsWith("https://") || body.startsWith("http://"))) {
                    // Принудительно делаем https
                    String fileUrl = body.startsWith("http://")
                        ? "https://" + body.substring(7) : body;
                    res.put("ok", true);
                    res.put("url", fileUrl);
                    if (progressCb != null) progressCb.onProgress(100);
                    log.i(TAG, "_catboxUploadBytes OK: " + fileUrl);
                } else {
                    res.put("ok", false);
                    res.put("error", "HTTP " + status + ": "
                        + body.substring(0, Math.min(body.length(), 300)));
                    log.w(TAG, "_catboxUploadBytes failed: " + status + " body=" + body);
                }
                return res.toString();
            } catch (Exception e) {
                log.e(TAG, "_catboxUploadBytes error: " + e.getMessage());
                try {
                    org.json.JSONObject err = new org.json.JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        /**
         * Загрузка файла на pixeldrain.com — быстрый CDN, файлы постоянные, 10GB/мес бесплатно.
         * API: PUT https://pixeldrain.com/api/file/{name} → {"id":"xxxx"}
         * URL файла: https://pixeldrain.com/api/file/{id}
         */
        private String _pixeldrainUploadBytes(byte[] fileBytes, String fileName,
                                              String mimeType, UploadProgressCallback progressCb) {
            try {
                String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                java.net.URL url = new java.net.URL("https://pixeldrain.com/api/file/" + safeName);
                java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection(buildProxy());
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(fileBytes.length);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(180000);
                conn.setRequestProperty("Content-Type", mimeType);

                java.io.OutputStream os = conn.getOutputStream();
                int chunkSize = 65536;
                for (int off = 0; off < fileBytes.length; off += chunkSize) {
                    int len = Math.min(chunkSize, fileBytes.length - off);
                    os.write(fileBytes, off, len);
                    if (progressCb != null && fileBytes.length > 0) {
                        int pct = (int)((long)(off + len) * 95 / fileBytes.length);
                        progressCb.onProgress(pct);
                    }
                }
                os.flush(); os.close();

                int status = conn.getResponseCode();
                java.io.InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                is.close(); conn.disconnect();
                String body = baos.toString("UTF-8").trim();
                log.i(TAG, "_pixeldrainUpload: status=" + status + " body=" + body.substring(0, Math.min(body.length(), 120)));

                org.json.JSONObject res = new org.json.JSONObject();
                if (status == 200 || status == 201) {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    String id = json.getString("id");
                    String fileUrl = "https://pixeldrain.com/api/file/" + id;
                    res.put("ok", true);
                    res.put("url", fileUrl);
                    if (progressCb != null) progressCb.onProgress(100);
                    log.i(TAG, "_pixeldrainUpload OK: " + fileUrl);
                } else {
                    res.put("ok", false);
                    res.put("error", "HTTP " + status + ": " + body.substring(0, Math.min(body.length(), 200)));
                    log.w(TAG, "_pixeldrainUpload failed: " + status);
                }
                return res.toString();
            } catch (Exception e) {
                log.e(TAG, "_pixeldrainUpload error: " + e.getMessage());
                try {
                    org.json.JSONObject err = new org.json.JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"pixeldrain error\"}"; }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // 🎤 НАТИВНАЯ ЗАПИСЬ ГОЛОСА (обход getUserMedia в file:// WebView)
        // JS: Android.startVoiceRecording() → запись
        //     Android.stopVoiceRecording()  → загрузка + колбэк onNativeVoiceDone(url,dur,mime)
        //     Android.cancelVoiceRecording() → отмена
        // ══════════════════════════════════════════════════════════════════

        @JavascriptInterface
        public void startVoiceRecording() {
            log.i(TAG, "startVoiceRecording called");
            // Проверяем разрешение RECORD_AUDIO
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    MainActivity.this, android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                log.w(TAG, "startVoiceRecording: RECORD_AUDIO not granted, requesting...");
                _pendingVoiceRecordRetry = true;
                androidx.core.app.ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{ android.Manifest.permission.RECORD_AUDIO },
                    AUDIO_PERM_CODE);
                return;
            }
            log.i(TAG, "startVoiceRecording: permission OK, initializing MediaRecorder");
            // Telegram-style: сначала чисто останавливаем предыдущую (без upload)
            synchronized (_voiceLock) {
                _voiceCancelled = false;
                _cleanupVoiceLocked();
            }
            try {
                java.io.File voiceFile = java.io.File.createTempFile("voice_", ".m4a", getCacheDir());
                log.i(TAG, "startVoiceRecording: temp file=" + voiceFile.getAbsolutePath());
                MediaRecorder recorder = new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioSamplingRate(44100);
                recorder.setAudioEncodingBitRate(128000);
                recorder.setOutputFile(voiceFile.getAbsolutePath());
                recorder.setOnErrorListener((mr, what, extra) ->
                    log.e(TAG, "MediaRecorder error: what=" + what + " extra=" + extra));
                recorder.prepare();
                log.i(TAG, "startVoiceRecording: prepare() OK");
                // Фиксируем состояние атомарно до start()
                synchronized (_voiceLock) {
                    _voiceRecorder = recorder;
                    _voiceFile     = voiceFile;
                }
                recorder.start();
                _voiceStartMs = System.currentTimeMillis();
                log.i(TAG, "startVoiceRecording: recording started, maxDur=60s");
                // Тикаем каждую секунду → JS обновляет таймер
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                synchronized (_voiceLock) { _voiceTimerHandler = handler; }
                Runnable tick = new Runnable() {
                    @Override public void run() {
                        // Telegram-style: проверяем состояние под блокировкой
                        synchronized (_voiceLock) {
                            if (_voiceRecorder == null || _voiceCancelled) return;
                        }
                        int sec = (int)((System.currentTimeMillis() - _voiceStartMs) / 1000);
                        log.i(TAG, "Voice tick: " + sec + "s");
                        webView.evaluateJavascript(
                            "if(typeof onNativeVoiceTick==='function')onNativeVoiceTick(" + sec + ")", null);
                        if (sec < 60) {
                            handler.postDelayed(this, 1000);
                        } else {
                            log.i(TAG, "Voice max duration reached, auto-stop");
                            stopVoiceRecording();
                        }
                    }
                };
                handler.postDelayed(tick, 1000);
            } catch (Exception e) {
                log.e(TAG, "startVoiceRecording error: " + e.getMessage());
                synchronized (_voiceLock) { _cleanupVoiceLocked(); }
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeVoiceError==='function')onNativeVoiceError('Не удалось запустить запись: " + e.getMessage() + "')", null));
            }
        }

        @JavascriptInterface
        public void stopVoiceRecording() {
            // Telegram-style: атомарно извлекаем состояние — только один поток пройдёт
            final MediaRecorder recorder;
            final java.io.File file;
            final int duration;
            synchronized (_voiceLock) {
                if (_voiceRecorder == null || _voiceCancelled) {
                    log.w(TAG, "stopVoiceRecording: already stopped/cancelled, ignoring");
                    return;
                }
                log.i(TAG, "stopVoiceRecording called");
                if (_voiceTimerHandler != null) {
                    _voiceTimerHandler.removeCallbacksAndMessages(null);
                    _voiceTimerHandler = null;
                }
                duration  = (int)((System.currentTimeMillis() - _voiceStartMs) / 1000);
                recorder  = _voiceRecorder;
                file      = _voiceFile;
                _voiceRecorder = null;  // ← обнуляем под lock — второй вызов выйдет выше
                _voiceFile     = null;
            }
            try {
                recorder.stop();
                recorder.release();
                log.i(TAG, "stopVoiceRecording: recorder stopped OK, duration=" + duration + "s");
            } catch (Exception e) {
                log.w(TAG, "stopVoiceRecording stop error: " + e.getMessage());
            }
            if (file == null || !file.exists()) {
                log.w(TAG, "stopVoiceRecording: file is null or missing");
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeVoiceError==='function')onNativeVoiceError('Файл записи не найден')", null));
                return;
            }
            log.i(TAG, "stopVoiceRecording: file size=" + file.length() + " bytes");
            if (file.length() < 500) {
                log.w(TAG, "stopVoiceRecording: file too small (" + file.length() + " bytes)");
                file.delete();
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onNativeVoiceError==='function')onNativeVoiceError('Запись слишком короткая')", null));
                return;
            }
            // Загружаем: pixeldrain → catbox fallback
            log.i(TAG, "stopVoiceRecording: uploading, size=" + file.length());
            new Thread(() -> {
                try {
                    byte[] bytes = new byte[(int) file.length()];
                    java.io.FileInputStream fis = new java.io.FileInputStream(file);
                    int read = fis.read(bytes); fis.close();
                    log.i(TAG, "stopVoiceRecording: read " + read + " bytes from file");
                    file.delete();
                    // Telegram-style: проверяем отмену перед отправкой
                    if (_voiceCancelled) {
                        log.i(TAG, "stopVoiceRecording: cancelled before upload, aborting");
                        return;
                    }
                    String fileName = "voice_" + System.currentTimeMillis() + ".m4a";
                    log.i(TAG, "stopVoiceRecording: trying pixeldrain for " + fileName);
                    String result = null;
                    try {
                        result = _pixeldrainUploadBytes(bytes, fileName, "audio/mp4", null);
                        if (!new org.json.JSONObject(result).optBoolean("ok")) { result = null; }
                    } catch (Exception pdEx) { log.w(TAG, "pixeldrain voice fallback: " + pdEx.getMessage()); result = null; }
                    if (result == null) {
                        log.i(TAG, "stopVoiceRecording: pixeldrain failed, using catbox");
                        String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                        result = nativeUploadFile(b64, fileName, "audio/mp4");
                    }
                    log.i(TAG, "stopVoiceRecording: upload result=" + result.substring(0, Math.min(result.length(), 100)));
                    // Проверяем ещё раз после долгого upload
                    if (_voiceCancelled) {
                        log.i(TAG, "stopVoiceRecording: cancelled after upload, not delivering");
                        return;
                    }
                    org.json.JSONObject res = new org.json.JSONObject(result);
                    if (res.optBoolean("ok")) {
                        final String url = res.getString("url");
                        log.i(TAG, "stopVoiceRecording: upload OK url=" + url + " dur=" + duration);
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onNativeVoiceDone==='function')onNativeVoiceDone('"
                            + url + "'," + duration + ",'audio/mp4')", null));
                    } else {
                        final String err = res.optString("error", "Upload failed");
                        log.e(TAG, "stopVoiceRecording: upload failed: " + err);
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onNativeVoiceError==='function')onNativeVoiceError('" + err + "')", null));
                    }
                } catch (Exception e) {
                    log.e(TAG, "stopVoiceRecording upload error: " + e.getMessage());
                    if (!_voiceCancelled) {
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof onNativeVoiceError==='function')onNativeVoiceError('" + e.getMessage() + "')", null));
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void cancelVoiceRecording() {
            log.i(TAG, "cancelVoiceRecording called");
            synchronized (_voiceLock) {
                // Telegram-style: ставим флаг ДО cleanup — upload-поток увидит его
                _voiceCancelled = true;
                if (_voiceTimerHandler != null) {
                    _voiceTimerHandler.removeCallbacksAndMessages(null);
                    _voiceTimerHandler = null;
                }
                _cleanupVoiceLocked();
            }
            log.i(TAG, "cancelVoiceRecording: done");
            // Уведомляем JS об отмене (UI сбрасывается)
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof onNativeVoiceCancelled==='function')onNativeVoiceCancelled()", null));
        }

        // Вызывается только внутри synchronized(_voiceLock)
        private void _cleanupVoiceLocked() {
            if (_voiceRecorder != null) {
                try { _voiceRecorder.stop(); } catch (Exception ignored) {}
                try { _voiceRecorder.release(); } catch (Exception ignored) {}
                _voiceRecorder = null;
            }
            if (_voiceFile != null) { _voiceFile.delete(); _voiceFile = null; }
        }

        private String getVersionName() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.5.0";
            }
        }

        // ─── Прокси-хелперы ────────────────────────────────────────────────────

        /** Строит java.net.Proxy из сохранённых настроек (SOCKS5 или NO_PROXY). */
        private java.net.Proxy buildProxy() {
            String host = prefs.getString("proxy_host", "");
            int    port = prefs.getInt("proxy_port", 0);
            if (host == null || host.isEmpty() || port <= 0) {
                return java.net.Proxy.NO_PROXY;
            }
            log.i(TAG, "buildProxy SOCKS5 " + host + ":" + port);
            java.net.InetSocketAddress addr = new java.net.InetSocketAddress(host, port);
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS, addr);

            // Если задан пользователь — регистрируем Authenticator
            String user = prefs.getString("proxy_user", "");
            String pass = prefs.getString("proxy_pass", "");
            if (user != null && !user.isEmpty()) {
                final String fu = user, fp = pass != null ? pass : "";
                java.net.Authenticator.setDefault(new java.net.Authenticator() {
                    @Override
                    protected java.net.PasswordAuthentication getPasswordAuthentication() {
                        return new java.net.PasswordAuthentication(fu, fp.toCharArray());
                    }
                });
            }
            return proxy;
        }

        /** nativeFetch с поддержкой прокси. */
        private String nativeFetchInternal(String urlStr, java.net.Proxy proxy) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 ScheduleApp/1.0");
                conn.setRequestProperty("Accept", "application/json");
                conn.setInstanceFollowRedirects(true);
                int status = conn.getResponseCode();
                InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = is.read(tmp)) != -1) baos.write(tmp, 0, n);
                is.close();
                conn.disconnect();
                String body = baos.toString(StandardCharsets.UTF_8.name());
                JSONObject res = new JSONObject();
                res.put("ok", status >= 200 && status < 300);
                res.put("status", status);
                res.put("body", body);
                return res.toString();
            } catch (Exception e) {
                log.e(TAG, "nativeFetchInternal error: " + e.getMessage());
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("status", 0);
                    err.put("body", "");
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        /** nativeDownloadBase64 с поддержкой прокси. */
        private String nativeDownloadBase64Internal(String urlStr, java.net.Proxy proxy) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 ScheduleApp/1.0");
                conn.setInstanceFollowRedirects(true);
                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    conn.disconnect();
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("error", "HTTP " + status);
                    return err.toString();
                }
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] tmp = new byte[16384];
                int n;
                while ((n = is.read(tmp)) != -1) baos.write(tmp, 0, n);
                is.close();
                conn.disconnect();
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                JSONObject res = new JSONObject();
                res.put("ok", true);
                res.put("base64", b64);
                return res.toString();
            } catch (Exception e) {
                log.e(TAG, "nativeDownloadBase64Internal error: " + e.getMessage());
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // SUPABASE BRIDGE
        // Все методы блокирующие — JS должен вызывать их в async/Worker.
        // Каждый метод возвращает JSON-строку: {ok, status, body, error?}
        // ══════════════════════════════════════════════════════════════════

        // ── Database ──────────────────────────────────────────────────────

        /**
         * SELECT из таблицы.
         * @param table  имя таблицы Supabase, например "messages"
         * @param query  PostgREST query string, например "select=*&user_id=eq.42&order=created_at.desc&limit=50"
         * @return JSON {ok, status, body} — body это JSON-массив строк
         *
         * Пример из JS:
         *   const r = JSON.parse(Android.supabaseSelect("messages", "select=*&order=created_at.desc&limit=20"));
         *   if (r.ok) { const rows = JSON.parse(r.body); }
         */
        @JavascriptInterface
        public String supabaseSelect(String table, String query) {
            log.i(TAG, "JS→supabaseSelect table=" + table + " query=" + query);
            return supabase.select(table, query);
        }

        /**
         * INSERT одной или нескольких строк.
         * @param table имя таблицы
         * @param json  одиночный объект "{…}" или массив "[{…},{…}]"
         * @return JSON {ok, status, body} — body: вставленные строки
         *
         * Пример:
         *   Android.supabaseInsert("messages", JSON.stringify({text:"Привет", user_id:1}))
         */
        @JavascriptInterface
        public String supabaseInsert(String table, String json) {
            log.i(TAG, "JS→supabaseInsert table=" + table);
            return supabase.insert(table, json);
        }

        /**
         * UPDATE строк по фильтру.
         * @param table  имя таблицы
         * @param filter PostgREST фильтр, например "id=eq.5"
         * @param json   поля для обновления "{read:true}"
         *
         * Пример:
         *   Android.supabaseUpdate("messages", "id=eq.5", JSON.stringify({read: true}))
         */
        @JavascriptInterface
        public String supabaseUpdate(String table, String filter, String json) {
            log.i(TAG, "JS→supabaseUpdate table=" + table + " filter=" + filter);
            return supabase.update(table, filter, json);
        }

        /**
         * UPSERT — вставить или обновить по primary key.
         * @param table имя таблицы
         * @param json  объект или массив объектов
         *
         * Пример:
         *   Android.supabaseUpsert("profiles", JSON.stringify({id: userId, name: "Alex"}))
         */
        @JavascriptInterface
        public String supabaseUpsert(String table, String json) {
            log.i(TAG, "JS→supabaseUpsert table=" + table);
            return supabase.upsert(table, json);
        }

        /**
         * DELETE строк по фильтру.
         * ВНИМАНИЕ: filter обязателен, пустая строка вернёт ошибку.
         * @param filter PostgREST фильтр, например "id=eq.5"
         *
         * Пример:
         *   Android.supabaseDelete("messages", "id=eq.42")
         */
        @JavascriptInterface
        public String supabaseDelete(String table, String filter) {
            log.i(TAG, "JS→supabaseDelete table=" + table + " filter=" + filter);
            return supabase.delete(table, filter);
        }

        // ── RPC ───────────────────────────────────────────────────────────

        /**
         * Вызов PostgreSQL-функции через /rpc/.
         * @param function  имя функции
         * @param paramsJson JSON-объект с параметрами или "{}"
         *
         * Пример:
         *   Android.supabaseRpc("get_unread_count", JSON.stringify({p_user_id: 1}))
         */
        @JavascriptInterface
        public String supabaseRpc(String function, String paramsJson) {
            log.i(TAG, "JS→supabaseRpc function=" + function);
            return supabase.rpc(function, paramsJson);
        }

        // ── Auth ──────────────────────────────────────────────────────────

        /**
         * Регистрация нового пользователя.
         * После успеха JWT автоматически сохраняется в SupabaseClient.
         *
         * Пример:
         *   const r = JSON.parse(Android.supabaseSignUp("user@example.com", "password123"));
         *   if (r.ok) { const data = JSON.parse(r.body); /* data.access_token * / }
         */
        @JavascriptInterface
        public String supabaseSignUp(String email, String password) {
            log.i(TAG, "JS→supabaseSignUp email=" + email);
            return supabase.signUp(email, password);
        }

        /**
         * Вход по email + password.
         * После успеха JWT автоматически сохраняется — все последующие
         * запросы к БД и Storage будут идти от имени этого пользователя.
         *
         * Пример:
         *   const r = JSON.parse(Android.supabaseSignIn("user@example.com", "pass"));
         *   if (r.ok) { const data = JSON.parse(r.body); saveToken(data.access_token); }
         */
        @JavascriptInterface
        public String supabaseSignIn(String email, String password) {
            log.i(TAG, "JS→supabaseSignIn email=" + email);
            return supabase.signIn(email, password);
        }

        /**
         * Выход — сбрасывает JWT на сервере и локально.
         */
        @JavascriptInterface
        public String supabaseSignOut() {
            log.i(TAG, "JS→supabaseSignOut");
            return supabase.signOut();
        }

        /**
         * Получить данные текущего аутентифицированного пользователя.
         * Требует установленного JWT (после signIn/signUp или setToken).
         */
        @JavascriptInterface
        public String supabaseGetUser() {
            log.i(TAG, "JS→supabaseGetUser");
            return supabase.getUser();
        }

        /**
         * Установить JWT вручную (например, восстановленный из localStorage/SharedPrefs).
         * После вызова все запросы будут идти с этим токеном.
         *
         * Пример:
         *   Android.supabaseSetToken(localStorage.getItem("sb_token"))
         */
        @JavascriptInterface
        public void supabaseSetToken(String token) {
            log.i(TAG, "JS→supabaseSetToken len=" + (token != null ? token.length() : "null"));
            supabase.setAuthToken(token);
        }

        /**
         * Получить текущий JWT (для сохранения в localStorage/SharedPrefs).
         * @return JWT-строка или пустая строка если не авторизован
         */
        @JavascriptInterface
        public String supabaseGetToken() {
            String t = supabase.getAuthToken();
            log.i(TAG, "JS→supabaseGetToken: " + (t != null ? "token(" + t.length() + ")" : "null"));
            return t != null ? t : "";
        }

        // ── Storage ───────────────────────────────────────────────────────

        /**
         * Загрузить файл в Storage из Base64-строки.
         * @param bucket    имя бакета, например "avatars"
         * @param path      путь внутри бакета, например "user_42/photo.jpg"
         * @param base64    содержимое файла в Base64
         * @param mimeType  MIME-тип, например "image/jpeg"
         *
         * Пример:
         *   const dataUrl = "data:image/jpeg;base64,/9j/4AA..."
         *   const b64 = dataUrl.split(",")[1]
         *   Android.supabaseStorageUpload("avatars", "user_1/avatar.jpg", b64, "image/jpeg")
         */
        @JavascriptInterface
        public String supabaseStorageUpload(String bucket, String path,
                                             String base64, String mimeType) {
            log.i(TAG, "JS→supabaseStorageUpload bucket=" + bucket + " path=" + path
                    + " mime=" + mimeType + " b64_len=" + (base64 != null ? base64.length() : 0));
            return supabase.storageUploadBase64(bucket, path, base64, mimeType);
        }

        /**
         * Получить публичный URL файла из Storage.
         * Не делает сетевой запрос — просто формирует URL.
         * @return URL-строка, например "https://…/storage/v1/object/public/avatars/user_1/photo.jpg"
         */
        @JavascriptInterface
        public String supabaseStorageGetUrl(String bucket, String path) {
            log.i(TAG, "JS→supabaseStorageGetUrl bucket=" + bucket + " path=" + path);
            return supabase.storageGetPublicUrl(bucket, path);
        }

        /**
         * Удалить файл из Storage.
         */
        @JavascriptInterface
        public String supabaseStorageDelete(String bucket, String path) {
            log.i(TAG, "JS→supabaseStorageDelete bucket=" + bucket + " path=" + path);
            return supabase.storageDelete(bucket, path);
        }

        /**
         * Получить список файлов в папке бакета.
         * @param prefix папка внутри бакета, например "user_42/"
         *
         * Пример:
         *   const r = JSON.parse(Android.supabaseStorageList("uploads", "user_42/"));
         *   if (r.ok) { const files = JSON.parse(r.body); }
         */
        @JavascriptInterface
        public String supabaseStorageList(String bucket, String prefix) {
            log.i(TAG, "JS→supabaseStorageList bucket=" + bucket + " prefix=" + prefix);
            return supabase.storageList(bucket, prefix);
        }

        // ══════════════════════════════════════════════════════════════════
        // TYPED HELPERS — типизированные методы под таблицы ScheduleApp
        // ══════════════════════════════════════════════════════════════════

        // ── PRESENCE ─────────────────────────────────────────────────────

        /**
         * Upsert присутствия (heartbeat).
         * Вызывать каждые 15-30 секунд пока пользователь онлайн.
         *
         * @param username   ник
         * @param name       отображаемое имя
         * @param avatar     эмодзи или URL
         * @param avatarType "emoji" | "url" | "base64"
         * @param avatarData base64-данные аватара (или пустая строка)
         * @param color      hex-цвет "#e87722"
         * @param status     "online" | "away" | "offline"
         * @param vip        1 = VIP, 0 = нет
         * @param badge      текст бейджа или пустая строка
         * @param pwdHash    хэш пароля (для совместимости) или пустая строка
         *
         * JS пример:
         *   Android.presenceUpsert("alex","Алекс","😎","emoji","","#e87722","online",0,"","")
         */
        @JavascriptInterface
        public String presenceUpsert(String username, String name, String avatar,
                                      String avatarType, String avatarData,
                                      String color, String status,
                                      int vip, String badge, String pwdHash) {
            log.i(TAG, "JS→presenceUpsert username=" + username + " status=" + status);
            return helper.presenceUpsert(username, name, avatar, avatarType,
                    avatarData.isEmpty() ? null : avatarData,
                    color, status, vip == 1,
                    badge.isEmpty() ? null : badge,
                    pwdHash.isEmpty() ? null : pwdHash);
        }

        /**
         * Пометить пользователя оффлайн.
         *
         * JS пример:
         *   Android.presenceOffline("alex")
         */
        @JavascriptInterface
        public String presenceOffline(String username) {
            log.i(TAG, "JS→presenceOffline username=" + username);
            return helper.presenceOffline(username);
        }

        /**
         * Получить список онлайн-пользователей.
         * Возвращает {ok, status, body} — body: JSON-массив presence-строк.
         *
         * JS пример:
         *   const r = JSON.parse(Android.presenceGetOnline());
         *   if (r.ok) { const users = JSON.parse(r.body); }
         */
        @JavascriptInterface
        public String presenceGetOnline() {
            log.i(TAG, "JS→presenceGetOnline");
            return helper.presenceGetOnline();
        }

        /**
         * Получить presence одного пользователя.
         */
        @JavascriptInterface
        public String presenceGetUser(String username) {
            log.i(TAG, "JS→presenceGetUser username=" + username);
            return helper.presenceGetUser(username);
        }

        /**
         * Удалить запись presence (полный выход/удаление).
         */
        @JavascriptInterface
        public String presenceDelete(String username) {
            log.i(TAG, "JS→presenceDelete username=" + username);
            return helper.presenceDelete(username);
        }

        // ── MESSAGES ─────────────────────────────────────────────────────

        /**
         * Отправить сообщение.
         *
         * @param chatKey  ключ чата (обычно sorted(from,to).join("_"))
         * @param fromUser отправитель
         * @param toUser   получатель
         * @param text     текст
         *
         * JS пример:
         *   Android.messageSend("alex_bob", "alex", "bob", "Привет!")
         */
        @JavascriptInterface
        public String messageSend(String chatKey, String fromUser, String toUser, String text) {
            log.i(TAG, "JS→messageSend chat=" + chatKey + " from=" + fromUser + " to=" + toUser);
            return helper.messageSend(chatKey, fromUser, toUser, text);
        }

        /**
         * Получить историю чата.
         *
         * @param chatKey  ключ чата
         * @param afterTs  timestamp — получить сообщения новее (0 = все)
         * @param limit    максимум сообщений
         *
         * JS пример (polling новых):
         *   const r = JSON.parse(Android.messageGetByChatKey("alex_bob", lastTs, 50));
         *   if (r.ok) { const msgs = JSON.parse(r.body); }
         */
        @JavascriptInterface
        public String messageGetByChatKey(String chatKey, double afterTs, int limit) {
            log.i(TAG, "JS→messageGetByChatKey chat=" + chatKey + " afterTs=" + afterTs);
            return helper.messageGetByChatKey(chatKey, (long) afterTs, limit);
        }

        /**
         * Получить входящие сообщения (все to_user=username новее afterTs).
         * Используется для глобального polling всех чатов.
         *
         * JS пример:
         *   const r = JSON.parse(Android.messageGetInbox("alex", lastTs, 100));
         */
        @JavascriptInterface
        public String messageGetInbox(String toUser, double afterTs, int limit) {
            log.i(TAG, "JS→messageGetInbox to=" + toUser + " afterTs=" + afterTs);
            return helper.messageGetInbox(toUser, (long) afterTs, limit);
        }

        /**
         * Удалить сообщение по id.
         */
        @JavascriptInterface
        public String messageDelete(double messageId) {
            log.i(TAG, "JS→messageDelete id=" + messageId);
            return helper.messageDelete((long) messageId);
        }

        /**
         * Очистить весь чат по chat_key (удалить все сообщения).
         */
        @JavascriptInterface
        public String messageClearChat(String chatKey) {
            log.i(TAG, "JS→messageClearChat chat=" + chatKey);
            return helper.messageClearChat(chatKey);
        }

        // ── LEADERBOARD ───────────────────────────────────────────────────

        /**
         * Обновить/создать запись в лидерборде.
         *
         * @param game     "doom" | "minecraft" | "tanks" | …
         * @param username ник игрока
         * @param name     отображаемое имя
         * @param avatar   эмодзи
         * @param color    hex-цвет
         * @param score    очки
         *
         * JS пример:
         *   Android.leaderboardUpsert("doom","alex","Алекс","😎","#e87722",1500)
         */
        @JavascriptInterface
        public String leaderboardUpsert(String game, String username, String name,
                                         String avatar, String color, int score) {
            log.i(TAG, "JS→leaderboardUpsert game=" + game + " username=" + username + " score=" + score);
            return helper.leaderboardUpsert(game, username, name, avatar, color, score);
        }

        /**
         * Получить топ игроков по игре.
         *
         * @param game  идентификатор игры
         * @param limit количество записей (рекомендуется 10)
         *
         * JS пример:
         *   const r = JSON.parse(Android.leaderboardGet("doom", 10));
         *   if (r.ok) { const top = JSON.parse(r.body); }
         */
        @JavascriptInterface
        public String leaderboardGet(String game, int limit) {
            log.i(TAG, "JS→leaderboardGet game=" + game + " limit=" + limit);
            return helper.leaderboardGet(game, limit);
        }

        /**
         * Получить запись конкретного игрока.
         */
        @JavascriptInterface
        public String leaderboardGetUser(String game, String username) {
            log.i(TAG, "JS→leaderboardGetUser game=" + game + " username=" + username);
            return helper.leaderboardGetUser(game, username);
        }

        /**
         * Удалить запись из лидерборда.
         */
        @JavascriptInterface
        public String leaderboardDelete(String game, String username) {
            log.i(TAG, "JS→leaderboardDelete game=" + game + " username=" + username);
            return helper.leaderboardDelete(game, username);
        }

        // ── USERS ─────────────────────────────────────────────────────────

        /**
         * Создать профиль пользователя (при регистрации).
         *
         * @param username   ник
         * @param name       отображаемое имя
         * @param pwdHash    хэш пароля
         * @param avatar     эмодзи
         * @param avatarType "emoji" | "url" | "base64"
         * @param color      hex-цвет
         *
         * JS пример:
         *   Android.userCreate("alex","Алексей",sha256(password),"😎","emoji","#e87722")
         */
        @JavascriptInterface
        public String userCreate(String username, String name, String pwdHash,
                                  String avatar, String avatarType, String color) {
            log.i(TAG, "JS→userCreate username=" + username);
            return helper.userCreate(username, name, pwdHash, avatar, avatarType, color);
        }

        /**
         * Получить профиль пользователя.
         *
         * JS пример:
         *   const r = JSON.parse(Android.userGet("alex"));
         *   if (r.ok) { const arr = JSON.parse(r.body); const user = arr[0]; }
         */
        @JavascriptInterface
        public String userGet(String username) {
            log.i(TAG, "JS→userGet username=" + username);
            return helper.userGet(username);
        }

        /**
         * Обновить произвольные поля профиля.
         * @param fieldsJson JSON-объект с полями, например:
         *                   {"name":"Алексей","bio":"Привет всем!","color":"#ff0000"}
         *
         * JS пример:
         *   Android.userUpdate("alex", JSON.stringify({bio: "Новое описание", color: "#00ff00"}))
         */
        @JavascriptInterface
        public String userUpdate(String username, String fieldsJson) {
            log.i(TAG, "JS→userUpdate username=" + username);
            return helper.userUpdate(username, fieldsJson);
        }

        /**
         * Обновить аватар пользователя.
         *
         * @param avatarType "emoji" | "url" | "base64"
         * @param avatar     значение (эмодзи/URL)
         * @param avatarData base64-данные (если type=base64) или пустая строка
         */
        @JavascriptInterface
        public String userUpdateAvatar(String username, String avatarType,
                                        String avatar, String avatarData) {
            log.i(TAG, "JS→userUpdateAvatar username=" + username + " type=" + avatarType);
            return helper.userUpdateAvatar(username, avatarType, avatar,
                    avatarData.isEmpty() ? null : avatarData);
        }

        /**
         * Обновить статус ("online" | "away" | "offline").
         */
        @JavascriptInterface
        public String userUpdateStatus(String username, String status) {
            log.i(TAG, "JS→userUpdateStatus username=" + username + " status=" + status);
            return helper.userUpdateStatus(username, status);
        }

        /**
         * Проверить, занят ли username.
         * Возвращает JSON: {ok, exists: true/false}
         *
         * JS пример:
         *   const r = JSON.parse(Android.userExists("alex"));
         *   if (r.ok && r.exists) { showError("Ник занят"); }
         */
        @JavascriptInterface
        public String userExists(String username) {
            log.i(TAG, "JS→userExists username=" + username);
            return helper.userExists(username);
        }

        /**
         * Получить список всех пользователей (контакты/поиск).
         * @param limit максимум записей
         */
        @JavascriptInterface
        public String userGetAll(int limit) {
            log.i(TAG, "JS→userGetAll limit=" + limit);
            return helper.userGetAll(limit);
        }

        // ── ACCOUNTS ──────────────────────────────────────────────────────

        /**
         * Зарегистрировать аккаунт.
         *
         * @param username  ник
         * @param pwdHash   хэш пароля (SHA-256 в hex, делать в JS)
         * @param name      отображаемое имя
         * @param avatar    эмодзи
         * @param color     hex-цвет
         *
         * JS пример:
         *   const hash = await sha256(password); // в hex
         *   const r = JSON.parse(Android.accountCreate("alex", hash, "Алексей", "😎", "#e87722"));
         */
        @JavascriptInterface
        public String accountCreate(String username, String pwdHash,
                                     String name, String avatar, String color) {
            log.i(TAG, "JS→accountCreate username=" + username);
            return helper.accountCreate(username, pwdHash, name, avatar, color);
        }

        /**
         * Получить аккаунт для верификации пароля.
         * В ответе будет pwd_hash — JS сравнивает с хэшем введённого пароля.
         *
         * JS пример:
         *   const r = JSON.parse(Android.accountGet("alex"));
         *   if (r.ok) {
         *     const acc = JSON.parse(r.body)[0];
         *     if (acc.pwd_hash === await sha256(enteredPassword)) { // вошли }
         *   }
         */
        @JavascriptInterface
        public String accountGet(String username) {
            log.i(TAG, "JS→accountGet username=" + username);
            return helper.accountGet(username);
        }

        /**
         * Обновить поля аккаунта.
         * @param fieldsJson JSON-объект, например {"name":"Новое имя","avatar":"🚀"}
         */
        @JavascriptInterface
        public String accountUpdate(String username, String fieldsJson) {
            log.i(TAG, "JS→accountUpdate username=" + username);
            return helper.accountUpdate(username, fieldsJson);
        }

        /**
         * Сменить пароль аккаунта.
         * @param newPwdHash новый хэш пароля (SHA-256 в hex)
         */
        @JavascriptInterface
        public String accountChangePassword(String username, String newPwdHash) {
            log.i(TAG, "JS→accountChangePassword username=" + username);
            return helper.accountChangePassword(username, newPwdHash);
        }

        /** Возвращает версию приложения из build.gradle (versionName) */
        @JavascriptInterface
        public String getAppVersion() {
            return getVersionName();
        }

        /** Переинжектирует высоту статус-бара (нужно для восстановления после notch-toggle) */
        @JavascriptInterface
        public void reinjectStatusBar() {
            runOnUiThread(() -> injectStatusBarHeight());
        }

        // ─── Нативная запись кружка (Camera2 + Telegram-стиль UI) ─────────────

        /** Открывает CircleRecordActivity — нативный рекордер кружков. */
        @JavascriptInterface
        public void startCircleRecord() {
            log.i(TAG, "startCircleRecord called");
            runOnUiThread(() -> {
                boolean camOk = ContextCompat.checkSelfPermission(
                    MainActivity.this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
                boolean micOk = ContextCompat.checkSelfPermission(
                    MainActivity.this, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;

                if (!camOk || !micOk) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.RECORD_AUDIO
                        }, CAMERA_PERM_CODE);
                    return;
                }
                // Сбрасываем флаги перед открытием
                CircleRecordActivity.shouldStop   = false;
                CircleRecordActivity.shouldCancel = false;
                Intent intent = new Intent(MainActivity.this, CircleRecordActivity.class);
                startActivityForResult(intent, CIRCLE_RECORD_CODE);
            });
        }

        /** JS → отпустил палец → остановить запись и отправить */
        @JavascriptInterface
        public void stopCircleRecord() {
            log.i(TAG, "stopCircleRecord called");
            CircleRecordActivity.shouldStop = true;
        }

        /** JS → свайп отмены → отменить запись */
        @JavascriptInterface
        public void cancelCircleRecord() {
            log.i(TAG, "cancelCircleRecord called");
            CircleRecordActivity.shouldCancel = true;
        }

        // ─── Фоновый сервис (Telegram-style keep-alive) ───────────────────────

        /** Включает фоновый сервис и сохраняет настройку. */
        @JavascriptInterface
        public void startBackgroundService() {
            log.i(TAG, "startBackgroundService called");
            prefs.edit().putBoolean(MessagingForegroundService.PREF_BG_ENABLED, true).apply();
            Intent intent = new Intent(MainActivity.this, MessagingForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        /** Останавливает фоновый сервис и сохраняет настройку. */
        @JavascriptInterface
        public void stopBackgroundService() {
            log.i(TAG, "stopBackgroundService called");
            prefs.edit().putBoolean(MessagingForegroundService.PREF_BG_ENABLED, false).apply();
            stopService(new Intent(MainActivity.this, MessagingForegroundService.class));
        }

        /** Возвращает true если фоновый сервис включён. */
        @JavascriptInterface
        public boolean isBackgroundServiceEnabled() {
            return prefs.getBoolean(MessagingForegroundService.PREF_BG_ENABLED, false);
        }

        /**
         * Возвращает Java-side PREF_SB_LAST_TS чтобы JS мог синхронизировать
         * _fbInboxLastTs после долгого фонового периода.
         */
        @JavascriptInterface
        public long getJavaSbLastTs() {
            return prefs.getLong(PREF_SB_LAST_TS, 0);
        }

        /** Сохраняет base64-изображение в галерею телефона */
        @JavascriptInterface
        public void saveImageToGallery(String base64Data) {
            runOnUiThread(() -> {
                try {
                    // Убираем data URL prefix
                    String data = base64Data;
                    if (data.contains(",")) data = data.split(",")[1];
                    byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                    String fileName = "ScheduleApp_" + System.currentTimeMillis() + ".jpg";
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_PICTURES);
                    }
                    android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                            os.write(bytes);
                        }
                        webView.post(() -> webView.evaluateJavascript(
                            "if(typeof toast==='function')toast('✅ Фото сохранено в галерею')", null));
                    }
                } catch (Exception e) {
                    log.e(TAG, "saveImageToGallery error: " + e.getMessage());
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof toast==='function')toast('❌ Не удалось сохранить фото')", null));
                }
            });
        }

        // ─── СГЭУ / БРСО — нативный fetch (обход CORS) ──────────────────────────

        /**
         * Выполняет GET-запрос к brso.sseu.ru без CORS-ограничений.
         * Вызывается из JS: Android.sseuFetch(url, callbackName)
         * callbackName — глобальная JS-функция, которую вызовем с результатом.
         */
        @JavascriptInterface
        public void sseuFetch(final String url, final String callbackName) {
            new Thread(() -> {
                String result = "null";
                String error  = null;
                try {
                    java.net.URL jurl = new java.net.URL(url);
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) jurl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                    conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36");
                    conn.setRequestProperty("Referer", "https://brso.sseu.ru/");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code >= 200 && code < 300) {
                        java.io.InputStream is = conn.getInputStream();
                        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                        byte[] tmp = new byte[4096];
                        int n;
                        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
                        result = buf.toString("UTF-8");
                    } else {
                        error = "HTTP " + code;
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String finalResult = result;
                final String finalError  = error != null
                    ? error.replace("\\", "\\\\").replace("\"", "\\\"")
                    : null;
                webView.post(() -> {
                    if (finalError != null) {
                        webView.evaluateJavascript(
                            "if(typeof " + callbackName + "==='function')" +
                            callbackName + "(null,\"" + finalError + "\")", null);
                    } else {
                        webView.evaluateJavascript(
                            "if(typeof " + callbackName + "==='function')" +
                            callbackName + "(" + finalResult + ",null)", null);
                    }
                });
            }).start();
        }

    }
}
