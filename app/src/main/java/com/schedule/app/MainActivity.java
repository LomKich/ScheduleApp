package com.schedule.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST_CODE = 1001;
    private WebView webView;
    private SharedPreferences prefs;
    private DnsVpnService vpnService;
    private boolean vpnBound = false;

    private final ServiceConnection vpnConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DnsVpnService.LocalBinder binder = (DnsVpnService.LocalBinder) service;
            vpnService = binder.getService();
            vpnBound = true;
            updateVpnStateInWeb();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            vpnBound = false;
            vpnService = null;
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Полноэкранный режим с безопасными отступами
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        prefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // JavaScript Bridge — доступен как window.Android в HTML
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Разрешаем все запросы
                return super.shouldInterceptRequest(view, request);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateVpnStateInWeb();
            }
        });

        // Загрузить локальный index.html из assets
        webView.loadUrl("file:///android_asset/index.html");

        // Привязать VPN сервис если запущен
        Intent intent = new Intent(this, DnsVpnService.class);
        bindService(intent, vpnConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService();
        }
    }

    private void startVpnService() {
        String dnsKey = prefs.getString("dns_key", "system");
        String dohUrl = prefs.getString("doh_url", "");
        String dpiStrategy = prefs.getString("dpi_strategy", "general");

        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_START);
        intent.putExtra(DnsVpnService.EXTRA_DOH_URL, dohUrl);
        intent.putExtra(DnsVpnService.EXTRA_DPI_STRATEGY, dpiStrategy);
        startService(intent);
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, DnsVpnService.class);
        intent.setAction(DnsVpnService.ACTION_STOP);
        startService(intent);
    }

    void updateVpnStateInWeb() {
        boolean active = vpnBound && vpnService != null && vpnService.isRunning();
        String statusText = active ? "DNS: " + prefs.getString("dns_key", "system").toUpperCase() +
                " • DPI: " + prefs.getString("dpi_strategy", "general") : "DNS и DPI обход неактивны";
        String js = String.format("if(typeof onVpnStateChanged==='function')onVpnStateChanged(%b,'%s')",
                active, statusText);
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vpnBound) unbindService(vpnConnection);
    }

    // ══ JavaScript Bridge ══
    class AndroidBridge {

        @JavascriptInterface
        public void setDns(String key, String dohUrl) {
            prefs.edit()
                .putString("dns_key", key)
                .putString("doh_url", dohUrl)
                .apply();
            if (vpnBound && vpnService != null && vpnService.isRunning()) {
                vpnService.updateDns(dohUrl);
            }
        }

        @JavascriptInterface
        public void setDpiStrategy(String strategyId) {
            prefs.edit().putString("dpi_strategy", strategyId).apply();
            if (vpnBound && vpnService != null && vpnService.isRunning()) {
                vpnService.updateDpiStrategy(strategyId);
            }
        }

        @JavascriptInterface
        public void toggleVpn() {
            boolean running = vpnBound && vpnService != null && vpnService.isRunning();
            if (running) {
                stopVpnService();
                webView.post(() -> updateVpnStateInWeb());
            } else {
                // Запросить разрешение на VPN
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
                String text = active ? "DNS: " + prefs.getString("dns_key", "system").toUpperCase()
                        + " • DPI: " + prefs.getString("dpi_strategy", "general")
                        : "DNS и DPI обход неактивны";
                JSONObject obj = new JSONObject();
                obj.put("active", active);
                obj.put("text", text);
                return obj.toString();
            } catch (Exception e) {
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
                return "{}";
            }
        }
    }
}
