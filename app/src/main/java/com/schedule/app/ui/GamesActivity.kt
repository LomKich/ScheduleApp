package com.schedule.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.schedule.app.AppLogger

/**
 * GamesActivity — полноэкранный WebView для секретных мини-игр.
 *
 * Грузит file:///android_asset/index.html, ждёт onPageFinished,
 * затем вызывает eggOpen() чтобы сразу показать пикер игр.
 *
 * JS-мост «GamesNative» позволяет странице:
 *   - GamesNative.closeGames()  — закрыть активити
 *   - GamesNative.log(msg)      — логировать в AppLogger
 *
 * Переданный extras:
 *   - "theme_id"   : String  — текущая тема (orange/amoled/...)
 *   - "username"   : String  — текущий пользователь (для лидерборда)
 *
 * Лидерборд сохраняется в localStorage games, синхронизируется
 * через существующий saveHi() → Supabase внутри index.html.
 */
class GamesActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val log by lazy { AppLogger.get(this) }
    private val TAG = "GamesActivity"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, no action bar
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(webView)

        // ── WebSettings (same as MainActivity) ────────────────────────────
        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            allowFileAccess                  = true
            allowContentAccess               = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs      = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            cacheMode                        = WebSettings.LOAD_DEFAULT
            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls              = false
            displayZoomControls              = false
        }
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        // ── JS bridge ─────────────────────────────────────────────────────
        webView.addJavascriptInterface(GamesNativeBridge(), "GamesNative")

        // ── WebViewClient — open egg picker once page is ready ────────────
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                log.i(TAG, "onPageFinished: $url")

                val themeId  = intent.getStringExtra("theme_id")  ?: "orange"
                val username = intent.getStringExtra("username")   ?: ""

                // 1. Применяем тему
                // 2. Если есть пользователь — симулируем вход (profileLoad будет работать)
                // 3. Открываем egg overlay
                val js = """
                    (function() {
                        // Apply theme from native
                        try {
                            if (typeof applyTheme === 'function') {
                                applyTheme('$themeId');
                            }
                        } catch(e) {}

                        // Make the back gesture / × button call closeGames
                        window._gamesNativeClose = function() {
                            if (typeof GamesNative !== 'undefined') {
                                GamesNative.closeGames();
                            }
                        };

                        // Override eggClose to go back to native UI
                        const _origEggClose = window.eggClose;
                        window.eggClose = function() {
                            if (typeof _origEggClose === 'function') _origEggClose();
                            window._gamesNativeClose();
                        };

                        // Open egg picker
                        setTimeout(function() {
                            if (typeof eggOpen === 'function') {
                                eggOpen();
                            }
                        }, 100);
                    })();
                """.trimIndent()

                view.post { view.evaluateJavascript(js, null) }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                log.e(TAG, "WebResource error: ${error.errorCode} ${request.url}")
            }
        }

        // ── Back press — close activity ───────────────────────────────────
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Try to go back within games overlay first
                webView.evaluateJavascript("""
                    (function() {
                        var back = document.getElementById('egg-back');
                        if (back && back.classList.contains('show')) {
                            back.click(); return 'handled';
                        }
                        return 'close';
                    })()
                """.trimIndent()) { result ->
                    if (result?.contains("close") == true) {
                        finish()
                    }
                }
            }
        })

        // ── Load ──────────────────────────────────────────────────────────
        log.i(TAG, "Loading index.html for games")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.stopLoading()
        webView.destroy()
    }

    // ── JS Bridge ─────────────────────────────────────────────────────────
    inner class GamesNativeBridge {

        @JavascriptInterface
        fun closeGames() {
            log.i(TAG, "GamesNative.closeGames() called")
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun log(msg: String) {
            log.i(TAG, "[JS] $msg")
        }

        // Pass scores back to native ViewModel if needed in the future
        @JavascriptInterface
        fun onNewHighScore(game: String, score: Int, username: String) {
            log.i(TAG, "New hi-score: $game=$score by @$username")
            // Leaderboard is already saved inside saveHi() in JS via Supabase
            // This is just an extra hook for native notifications if desired
        }
    }
}
