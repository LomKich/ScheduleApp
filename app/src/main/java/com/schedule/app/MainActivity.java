package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.view.View;
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

    private WebView                webView;
    private SharedPreferences      prefs;
    private DnsVpnService          vpnService;
    private boolean                vpnBound = false;
    private AppLogger              log;
    // Колбэк для file chooser (фон приложения)
    private ValueCallback<Uri[]>   fileChooserCallback = null;

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

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

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
        // Агрессивное кэширование — страница грузится из кэша, обновляется только при изменениях
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
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
        log.i(TAG, "WebSettings: JS=on DomStorage=on FileAccess=on MixedContent=ALWAYS_ALLOW Hardware=on Cache=CACHE_ELSE_NETWORK");

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        log.i(TAG, "JavascriptInterface 'Android' зарегистрирован");

        // Перехват console.log / console.error из JS
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                log.js(cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber());
                return true;
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
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Выбери изображение"),
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
                return super.shouldInterceptRequest(view, req);
            }
        });
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
            // Обработка выбранного изображения для фона
            if (fileChooserCallback == null) return;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                log.i(TAG, "Изображение выбрано: " + uri);
                // Конвертируем в base64 в фоновом потоке, затем передаём в JS
                final Uri finalUri = uri;
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
                        // через BitmapFactory, чтобы WebView мог отображать фон без проблем
                        byte[] bytes;
                        String mime;
                        android.graphics.Bitmap bitmap =
                            android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length);
                        if (bitmap != null) {
                            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, jpegOut);
                            bitmap.recycle();
                            bytes = jpegOut.toByteArray();
                            mime = "image/jpeg";
                            log.i(TAG, "Изображение сконвертировано в JPEG, размер: " + bytes.length + " байт");
                        } else {
                            // Fallback: передаём как есть, определяем MIME через ContentResolver
                            bytes = rawBytes;
                            mime = getContentResolver().getType(finalUri);
                            if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                            log.w(TAG, "BitmapFactory не смог декодировать, используем raw bytes, mime=" + mime);
                        }

                        String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                        String dataUrl = "data:" + mime + ";base64," + b64;

                        // Передаём в JS через evaluateJavascript
                        final String jsDataUrl = dataUrl;
                        runOnUiThread(() -> {
                            // Закрываем file chooser (передаём uri обратно)
                            fileChooserCallback.onReceiveValue(new Uri[]{finalUri});
                            fileChooserCallback = null;
                            // Вызываем JS-функцию с base64 данными
                            String escaped = jsDataUrl.replace("\\", "\\\\").replace("'", "\\'");
                            webView.evaluateJavascript(
                                "if(typeof onNativeBgImagePicked==='function')onNativeBgImagePicked('" + escaped + "')",
                                null
                            );
                            log.i(TAG, "Фон передан в JS, размер: " + bytes.length + " байт");
                        });
                    } catch (Exception e) {
                        log.e(TAG, "Ошибка чтения изображения: " + e.getMessage());
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
                log.i(TAG, "Выбор изображения отменён");
                fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = null;
            }
        }
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

    @Override protected void onPause()   { super.onPause();   log.i(TAG, "onPause"); }
    @Override protected void onResume()  { super.onResume();  log.i(TAG, "onResume"); }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.section("onDestroy");
        log.i(TAG, "Приложение завершается");
        if (vpnBound) unbindService(vpnConnection);
    }

    // ══════════════════════════════ JavaScript Bridge ══════════════════════════════
    // Важно: static + public — иначе WebView не может найти методы через рефлексию

    public class AndroidBridge {

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
         */
        @JavascriptInterface
        public String nativeFetch(String urlStr) {
            log.i(TAG, "nativeFetch: " + urlStr);
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                log.e(TAG, "nativeFetch error: " + e.getMessage());
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
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Выбери фоновое изображение"),
                        IMAGE_PICK_CODE
                    );
                } catch (Exception e) {
                    log.e(TAG, "pickImageForBackground error: " + e.getMessage());
                    webView.evaluateJavascript(
                        "if(typeof toast==='function')toast('❌ Не удалось открыть галерею')",
                        null
                    );
                }
            });
        }

        /**
         * Нативное скачивание файла без CORS — возвращает JSON: {ok, base64, error?}
         * base64 — содержимое файла в Base64, декодируется в JS в ArrayBuffer.
         */
        @JavascriptInterface
        public String nativeDownloadBase64(String urlStr) {
            log.i(TAG, "nativeDownloadBase64: " + urlStr);
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                log.e(TAG, "nativeDownloadBase64 error: " + e.getMessage());
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception ex) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        /**
         * OTA: открыть URL в браузере (для скачивания APK обновления)
         */
        @JavascriptInterface
        public void openUrl(String urlStr) {
            log.i(TAG, "openUrl: " + urlStr);
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
         * OTA: скачать APK и запустить системный установщик.
         * Скачивает во внутреннее хранилище (getFilesDir/apk/) и открывает
         * стандартный диалог "Установить" — пользователю нужна одна кнопка.
         */
        @JavascriptInterface
        public void downloadAndInstallApk(String urlStr) {
            log.i(TAG, "downloadAndInstallApk: " + urlStr);
            // Скачивание в фоне, установка в UI-потоке
            new Thread(() -> {
                try {
                    // Папка для APK
                    java.io.File apkDir = new java.io.File(getFilesDir(), "apk");
                    if (!apkDir.exists()) apkDir.mkdirs();
                    java.io.File apkFile = new java.io.File(apkDir, "update.apk");
                    if (apkFile.exists()) apkFile.delete();

                    // Скачиваем
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 ScheduleApp/" + getVersionName());
                    conn.setInstanceFollowRedirects(true);

                    int status = conn.getResponseCode();
                    if (status < 200 || status >= 300) {
                        log.e(TAG, "downloadAndInstallApk HTTP error: " + status);
                        runOnUiThread(() -> webView.evaluateJavascript(
                            "if(typeof toast==='function')toast('❌ Ошибка скачивания: HTTP " + status + "')", null));
                        return;
                    }

                    java.io.InputStream is = conn.getInputStream();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(apkFile);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.close();
                    is.close();
                    conn.disconnect();
                    log.i(TAG, "APK downloaded: " + apkFile.length() + " bytes");

                    // Открываем установщик через FileProvider
                    runOnUiThread(() -> {
                        try {
                            android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                apkFile
                            );
                            android.content.Intent intent = new android.content.Intent(
                                android.content.Intent.ACTION_INSTALL_PACKAGE);
                            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            // Разрешаем установку не из маркета
                            intent.putExtra(android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                            startActivity(intent);
                            log.i(TAG, "Installer launched");
                        } catch (Exception e) {
                            log.e(TAG, "Launch installer error: " + e.getMessage());
                            webView.evaluateJavascript(
                                "if(typeof toast==='function')toast('❌ Не удалось открыть установщик')", null);
                        }
                    });

                } catch (Exception e) {
                    log.e(TAG, "downloadAndInstallApk error: " + e.getMessage());
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "if(typeof toast==='function')toast('❌ " + e.getMessage() + "')", null));
                }
            }).start();
        }

        private String getVersionName() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.5.0";
            }
        }

    }
}
