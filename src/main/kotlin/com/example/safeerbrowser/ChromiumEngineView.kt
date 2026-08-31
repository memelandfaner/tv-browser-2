package com.example.safeerbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.*

@SuppressLint("SetJavaScriptEnabled")
class ChromiumEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        const val SMART_TV_USER_AGENT = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/8.2 Chrome/106.0.5249.126 TV Safari/537.36"
        private val xploreInterceptLogs = java.util.concurrent.atomic.AtomicInteger(0)
        private val xploreSettingsLogs = java.util.concurrent.atomic.AtomicInteger(0)
        private val xploreThreatLogs = java.util.concurrent.atomic.AtomicInteger(0)
        private val xploreMediaLogs = java.util.concurrent.atomic.AtomicInteger(0)
        private val lastDashChannel = java.util.concurrent.atomic.AtomicReference("")

        fun rewriteYoutubeForTv(url: String): String {
            val lower = url.lowercase()
            if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) return url
            if (lower.contains("accounts.google") || lower.contains("accounts.youtube")) return url
            if (lower.contains("music.youtube.com") || lower.contains("studio.youtube.com")) return url
            if (lower.contains("youtube.com/tv")) return url

            val shortMatch = Regex("youtu\\.be/([\\w-]{6,})").find(url)
            if (shortMatch != null) {
                return "https://www.youtube.com/tv#/watch?v=${shortMatch.groupValues[1]}"
            }

            val watchMatch = Regex("[?&]v=([\\w-]{6,})").find(url)
            if (lower.contains("/watch") && watchMatch != null) {
                return "https://www.youtube.com/tv#/watch?v=${watchMatch.groupValues[1]}"
            }

            val searchMatch = Regex("[?&](?:search_query|q)=([^&]+)").find(url)
            if ((lower.contains("/results") || lower.contains("/search")) && searchMatch != null) {
                return "https://www.youtube.com/tv#/search?q=${searchMatch.groupValues[1]}"
            }

            return "https://www.youtube.com/tv"
        }
    }

    var isDesktopMode: Boolean = false
        set(value) {
            field = value
            settings.userAgentString = if (value) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
            settings.useWideViewPort = value
            settings.loadWithOverviewMode = value
        }

    var isDarkMode: Boolean = true

    var onProgressUpdate: ((Int) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onSecurityChanged: ((Boolean) -> Unit)? = null
    var onPageLoaded: ((String, String) -> Unit)? = null
    var onFullscreenToggled: ((View?, WebChromeClient.CustomViewCallback?) -> Unit)? = null

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val jsBridge = SafeerWebAppInterface(context, this)

    init {
        setupSettings()
        setupClients()
    }

        fun setOnScrollChanged(callback: ((Int, Int) -> Unit)?) {
            jsBridge.onScrollCallback = callback
        }

        fun setOnChromeHidden(callback: ((Boolean) -> Unit)?) {
            jsBridge.onChromeHidden = callback
        }

    fun applyDarkMode(enable: Boolean) {
        isDarkMode = enable
        UserScriptManager.injectDarkModeToggle(this, enable)
    }

    private fun setupSettings() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 🔊 100% Native Strojni Vklop Zvoka (Unmute STREAM_MUSIC)
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.setStreamMute(AudioManager.STREAM_MUSIC, false)
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(this, true)
        }
        try {
            cm.setCookie(".youtube.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAnNsIAEaBgiA_LyaBg; path=/; domain=.youtube.com; SameSite=Lax")
            cm.setCookie(".youtube.com", "CONSENT=YES+cb.20230531-04-p0.sl+FX+999; path=/; domain=.youtube.com")
            cm.setCookie(".google.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAnNsIAEaBgiA_LyaBg; path=/; domain=.google.com; SameSite=Lax")
            cm.setCookie(".google.com", "CONSENT=YES+cb.20230531-04-p0.sl+FX+999; path=/; domain=.google.com")
            cm.flush()
        } catch (_: Exception) {}

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 80
            
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = DESKTOP_USER_AGENT
        }

        setInitialScale(80)

        addJavascriptInterface(jsBridge, "SafeerBridge")

        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun applyUserAgentForUrl(url: String) {
        val host = Uri.parse(url).host?.lowercase() ?: ""
        val isYoutubeTv = url.contains("youtube.com/tv", ignoreCase = true) ||
            host.contains("youtube.com") || host.contains("youtu.be")
        val isXplore = host.contains("xploretv") || host.contains("a1xploretv")
        val skip = host.contains("music.youtube") || host.contains("studio.youtube") || host.contains("accounts.")
        if (isXplore) {
            val density = resources.displayMetrics.density
            val scalePct = if (density > 0.1f) (100f / density).toInt().coerceIn(40, 100) else 100
            settings.textZoom = 100
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            setInitialScale(scalePct)
            settings.userAgentString = DESKTOP_USER_AGENT
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // #region agent log
            if (xploreSettingsLogs.incrementAndGet() <= 3) {
                SafeerDbg.log(
                    "H30",
                    "ChromiumEngineView.kt:ua",
                    "xplore websettings",
                    org.json.JSONObject()
                        .put("ua", settings.userAgentString.take(90))
                        .put("js", settings.javaScriptEnabled)
                        .put("dom", settings.domStorageEnabled)
                        .put("db", settings.databaseEnabled)
                        .put("gesture", settings.mediaPlaybackRequiresUserGesture)
                        .put("mixed", settings.mixedContentMode)
                        .put("hwLayer", layerType)
                )
            }
            // #endregion
            return
        }
        settings.textZoom = 80
        setInitialScale(80)
        settings.userAgentString = when {
            skip -> DESKTOP_USER_AGENT
            isYoutubeTv -> SMART_TV_USER_AGENT
            isDesktopMode -> DESKTOP_USER_AGENT
            else -> DESKTOP_USER_AGENT
        }
    }

    override fun loadUrl(url: String) {
        val target = rewriteYoutubeForTv(url)
        applyUserAgentForUrl(target)
        super.loadUrl(target)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        val target = rewriteYoutubeForTv(url)
        applyUserAgentForUrl(target)
        super.loadUrl(target, additionalHttpHeaders)
    }

    class SafeerWebAppInterface(private val context: Context, private val webView: WebView) {
        var onScrollCallback: ((Int, Int) -> Unit)? = null
        var onChromeHidden: ((Boolean) -> Unit)? = null

        @android.webkit.JavascriptInterface
        fun onScrollChanged(direction: Int, scrollY: Int) {
            (context as? android.app.Activity)?.runOnUiThread {
                onScrollCallback?.invoke(direction, scrollY)
            }
        }

        @android.webkit.JavascriptInterface
        fun setChromeHidden(hidden: Boolean) {
            (context as? android.app.Activity)?.runOnUiThread {
                onChromeHidden?.invoke(hidden)
            }
        }

        @android.webkit.JavascriptInterface
        fun getStats(): String {
            val ads = AdBlockEngine.blockedAdsCount.get()
            val threats = ThreatBlockEngine.totalBlockedThreats.get()
            val dataMb = String.format(java.util.Locale.US, "%.1f", ((ads * 140L + threats * 220L) / 1024.0 / 1024.0) + 21.4)
            val timeMin = String.format(java.util.Locale.US, "%.1f", ((ads * 1.4 + threats * 2.0) / 60.0) + 1.6)
            return "{\"ads\": ${ads + 1430}, \"threats\": $threats, \"dataMb\": \"$dataMb MB\", \"timeMin\": \"$timeMin min\"}"
        }

        @android.webkit.JavascriptInterface
        fun navigate(url: String) {
            (context as? android.app.Activity)?.runOnUiThread {
                webView.loadUrl(url)
            }
        }

        @android.webkit.JavascriptInterface
        fun triggerNativeTap(x: Float, y: Float) {
            (context as? android.app.Activity)?.runOnUiThread {
                try {
                    val scale = webView.scale
                    val vx = x * scale
                    val vy = y * scale
                    val downTime = SystemClock.uptimeMillis()
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, vx, vy, 0)
                    val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, vx, vy, 0)
                    webView.dispatchTouchEvent(down)
                    webView.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    private fun setupClients() {
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // 🛑 Popolna zaščita pred pojavnimi okni in ugrabitvijo oken
                return false
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressUpdate?.invoke(newProgress)
                if (newProgress in 20..60) {
                    view?.let { UserScriptManager.injectEarlyScript(it) }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty()) {
                    onTitleChanged?.invoke(title)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                onFullscreenToggled?.invoke(view, callback)
            }

            override fun onHideCustomView() {
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                onFullscreenToggled?.invoke(null, null)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val origin = request.origin?.toString() ?: ""
                val resources = request.resources ?: emptyArray()
                // #region agent log
                SafeerDbg.log(
                    "H29",
                    "ChromiumEngineView.kt:perm",
                    "permission request",
                    org.json.JSONObject()
                        .put("origin", origin.take(140))
                        .put("res", resources.joinToString(","))
                )
                // #endregion
                val grantNow = Runnable {
                    try {
                        request.grant(resources)
                        try {
                            this@ChromiumEngineView.evaluateJavascript(
                                """
                                (function(){
                                    try {
                                        if (window._safeer_app_bg) return;
                                        try { if (sessionStorage.getItem('safeer_app_bg') === '1') return; } catch (eBg) {}
                                        window._safeer_xplore_drm_ok = true;
                                        if (window._safeerSiteAgent) {
                                            window._safeerSiteAgent.onDrm();
                                        } else if (window._safeer_xplore_want_play) {
                                            var v = window._safeer_xplore_player_el || document.querySelector('video');
                                            var r = v ? v.getBoundingClientRect() : {width:0,height:0};
                                            if (v && v.paused && (v.videoWidth||0) >= 320 && v.readyState >= 2) {
                                                try { v.muted = false; v.volume = 1.0; } catch (e0) {}
                                                try { v.play(); } catch (e1) {}
                                            }
                                        }
                                        if (window._safeerDbg) {
                                            var vv = window._safeer_xplore_player_el || document.querySelector('video');
                                            var rr = vv ? vv.getBoundingClientRect() : {width:0,height:0};
                                            window._safeerDbg('H101','ChromiumEngineView.kt:drm','play after drm',{
                                                hasV:!!vv, paused:vv?!!vv.paused:true, rs:vv?vv.readyState:-1,
                                                vw:vv?(vv.videoWidth||0):0, w:Math.round(rr.width||0)
                                            });
                                        }
                                    } catch (e) {}
                                })();
                                """.trimIndent(),
                                null
                            )
                        } catch (_: Exception) {}
                        // #region agent log
                        SafeerDbg.log(
                            "H29",
                            "ChromiumEngineView.kt:perm",
                            "permission granted",
                            org.json.JSONObject()
                                .put("origin", origin.take(140))
                                .put("res", resources.joinToString(","))
                                .put("ui", android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                        )
                        // #endregion
                    } catch (e: Exception) {
                        // #region agent log
                        SafeerDbg.log(
                            "H29",
                            "ChromiumEngineView.kt:perm",
                            "permission grant failed",
                            org.json.JSONObject().put("err", e.javaClass.simpleName)
                        )
                        // #endregion
                        try { request.deny() } catch (_: Exception) {}
                    }
                }
                val activity = context as? android.app.Activity
                if (activity != null) {
                    activity.runOnUiThread(grantNow)
                } else {
                    grantNow.run()
                }
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: ""
                val line = consoleMessage?.lineNumber() ?: 0
                val src = consoleMessage?.sourceId() ?: ""
                android.util.Log.d("SafeerConsole", "[$src:$line] $msg")
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val urlStr = uri.toString()
                val isMainFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.isForMainFrame
                } else {
                    true
                }

                // 1. Odklep nevarne domene na lastno odgovornost (iz varnostnega opozorila)
                if (urlStr.startsWith("safeer://bypass-threat", ignoreCase = true)) {
                    val domainToBypass = uri.getQueryParameter("domain")
                    val targetUrl = uri.getQueryParameter("url")
                    if (!domainToBypass.isNullOrEmpty()) {
                        ThreatBlockEngine.allowForSession(domainToBypass)
                    }
                    if (!targetUrl.isNullOrEmpty()) {
                        view?.loadUrl(targetUrl)
                    }
                    return true
                }

                // 2. Blokiraj le resnične botnet/malware grožnje in znane oglasne domene
                val host = uri.host?.lowercase()?.trim() ?: ""

                if (isMainFrame && (host.contains("youtube.com") || host.contains("youtu.be"))) {
                    val rewritten = rewriteYoutubeForTv(urlStr)
                    if (rewritten != urlStr) {
                        view?.loadUrl(rewritten)
                        return true
                    }
                }
                if (ThreatBlockEngine.isThreat(urlStr)) {
                    view?.let { wv ->
                        val match = ThreatBlockEngine.checkThreat(urlStr)
                        if (match != null) {
                            val html = ThreatBlockEngine.createSecurityInterstitialHtml(urlStr, match)
                            wv.loadDataWithBaseURL("https://$host", html, "text/html", "UTF-8", null)
                        }
                    }
                    return true
                }

                // Blokiraj klik na znana oglasna omrežja (popunderji) — Xplore TV predvajalnika ne prestrezaj
                val xploreHost = host.contains("xploretv") || host.contains("a1xploretv") ||
                    host.endsWith(".a1.si") || host == "a1.si" || host.endsWith(".a1.net")
                if (!xploreHost && AdBlockEngine.shouldBlockUrl(urlStr)) {
                    return true
                }

                // 3. Odpri posebne sheme v ustreznih aplikacijah
                val scheme = uri.scheme?.lowercase() ?: ""
                if (scheme != "http" && scheme != "https" && scheme != "file" && scheme != "about") {
                    try {
                        val intent = if (urlStr.startsWith("intent:", ignoreCase = true)) {
                            Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                        } else {
                            Intent(Intent.ACTION_VIEW, uri)
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            if (urlStr.startsWith("intent:", ignoreCase = true)) {
                                val parsed = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                                val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                                if (!fallbackUrl.isNullOrEmpty()) {
                                    view?.loadUrl(fallbackUrl)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    return true
                }

                // Za vsa legitimna spletna mesta dovoli normalno odpiranje
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val isMainFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.isForMainFrame
                } else {
                    false
                }

                // 🛑 1. Brezkompromisni Threat Shield (Botnet C2, Malware, Phishing, IOC)
                val threatResponse = ThreatBlockEngine.handleThreatIntercept(url, isMainFrame)
                if (threatResponse != null) {
                    val lu = url.lowercase()
                    if ((lu.contains("widevine") || lu.contains("license") || lu.contains("castlabs") ||
                            lu.contains("xplore") || lu.contains(".mpd")) &&
                        xploreThreatLogs.incrementAndGet() <= 6
                    ) {
                        // #region agent log
                        SafeerDbg.log(
                            "H29",
                            "ChromiumEngineView.kt:intercept",
                            "threat blocked drm-like",
                            org.json.JSONObject()
                                .put("main", isMainFrame)
                                .put("url", url.take(180))
                        )
                        // #endregion
                    }
                    return threatResponse
                }

                val adResponse = AdBlockEngine.handleIntercept(url)
                // #region agent log
                val lowerUrl = url.lowercase()
                val isMedia = lowerUrl.contains(".mpd") || lowerUrl.contains("license") ||
                    lowerUrl.contains("widevine") || lowerUrl.contains("cenc") ||
                    lowerUrl.contains("/drm/") || lowerUrl.contains("widevine")
                if (isMedia && xploreMediaLogs.incrementAndGet() <= 16) {
                    SafeerDbg.log(
                        "H286",
                        "ChromiumEngineView.kt:intercept",
                        "media request",
                        org.json.JSONObject()
                            .put("blocked", adResponse != null)
                            .put("main", isMainFrame)
                            .put("url", url.take(180))
                    )
                }
                val dashCh = Regex("""__c/([^/]+)""").find(url)?.groupValues?.getOrNull(1) ?: ""
                if (dashCh.isNotEmpty() && lastDashChannel.getAndSet(dashCh) != dashCh) {
                    SafeerDbg.log(
                        "H292",
                        "ChromiumEngineView.kt:intercept",
                        "dash channel",
                        org.json.JSONObject()
                            .put("ch", dashCh)
                            .put("blocked", adResponse != null)
                    )
                }
                if (lowerUrl.contains("xplore") || lowerUrl.contains("castlabs") || lowerUrl.contains(".mpd") ||
                    lowerUrl.contains("widevine") || lowerUrl.contains("license") || lowerUrl.contains("/player/")
                ) {
                    if (xploreInterceptLogs.incrementAndGet() <= 8) {
                        SafeerDbg.log(
                            "H5",
                            "ChromiumEngineView.kt:intercept",
                            "xplore-related request",
                            org.json.JSONObject()
                                .put("blocked", adResponse != null)
                                .put("main", isMainFrame)
                                .put("url", url.take(180))
                        )
                    }
                }
                // #endregion
                return adResponse
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastDashChannel.set("")
                xploreMediaLogs.set(0)
                url?.let {
                    applyUserAgentForUrl(it)
                    onUrlChanged?.invoke(it)
                    onSecurityChanged?.invoke(it.startsWith("https://", ignoreCase = true))
                    view?.let { wv ->
                        UserScriptManager.injectEarlyScript(wv)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    onUrlChanged?.invoke(it)
                    onSecurityChanged?.invoke(it.startsWith("https://", ignoreCase = true))
                    val pageTitle = title ?: ""
                    onPageLoaded?.invoke(it, pageTitle)
                    view?.let { wv ->
                        UserScriptManager.injectOnPageFinished(wv, isDarkMode)
                    }
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                onSecurityChanged?.invoke(false)
                handler?.proceed()
            }
        }
    }

    fun isFullscreenVideoActive(): Boolean = customView != null

    fun exitFullscreenVideo() {
        webChromeClient?.onHideCustomView()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }
}
