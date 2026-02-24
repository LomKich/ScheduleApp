package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private WebView           webView;
    private SharedPreferences prefs;
    private AppLogger         log;

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

        log.i(TAG, "onCreate завершён");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Аппаратное ускорение
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(100);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(false);
        log.i(TAG, "WebSettings: JS=on DomStorage=on FileAccess=on MixedContent=ALWAYS_ALLOW Hardware=on Cache=CACHE_ELSE_NETWORK");

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        log.i(TAG, "JavascriptInterface 'Android' зарегистрирован");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                log.js(cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber());
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
    public void onBackPressed() {
        webView.evaluateJavascript(
            "(function(){ return typeof nativeBack==='function' ? (nativeBack() ? 'handled' : 'exit') : 'exit'; })()",
            result -> {
                if (!("\"handled\"").equals(result)) {
                    finish();
                }
            }
        );
    }

    @Override protected void onPause()  { super.onPause();  log.i(TAG, "onPause"); }
    @Override protected void onResume() { super.onResume(); log.i(TAG, "onResume"); }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.section("onDestroy");
        log.i(TAG, "Приложение завершается");
    }

    // ══════════════════════════════ JavaScript Bridge ══════════════════════════════

    class AndroidBridge {

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
         */
        @JavascriptInterface
        public void setAppIcon(String icon) {
            final String pkg = getPackageName();
            final String[] allAliases = {
                pkg + ".IconDark",
                pkg + ".IconOrange",
                pkg + ".IconAmoled",
                pkg + ".IconSamek"
            };
            final String target;
            switch (icon) {
                case "dark":   target = pkg + ".IconDark";   break;
                case "amoled": target = pkg + ".IconAmoled"; break;
                case "samek":  target = pkg + ".IconSamek";  break;
                default:       target = pkg + ".IconOrange"; break;
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Сменить иконку?")
                    .setMessage("После смены иконки приложение закроется — это стандартное поведение Android. " +
                                "Ярлык на главном экране нужно будет добавить заново.\n\nПродолжить?")
                    .setPositiveButton("Сменить", (d, w) -> {
                        getPackageManager().setComponentEnabledSetting(
                            new ComponentName(pkg, target),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        );
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
                            finishAffinity();
                        }, 500);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            });
        }

        /**
         * Нативное скачивание файла без CORS — возвращает JSON: {ok, base64, error?}
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
    }
}
