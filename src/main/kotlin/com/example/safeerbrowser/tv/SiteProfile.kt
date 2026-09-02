package com.example.safeerbrowser

import android.view.KeyEvent
import org.json.JSONObject

enum class PlaybackMode {
    InPlaceWebView,
    CustomView,
    ExoPlayer
}

object TvSite {
    fun isXplore(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("xploretv") || u.contains("a1xploretv")
    }

    fun isYoutubeTv(url: String): Boolean {
        return url.contains("youtube.com/tv", ignoreCase = true)
    }

    fun isHydra(url: String): Boolean {
        return url.contains("hydrahd", ignoreCase = true)
    }

    fun isHydraPlayer(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("hydrahd") && (u.contains("/movie/") || u.contains("/tv/") || u.contains("/watch"))
    }

    fun isWatchPage(url: String): Boolean {
        val u = url.lowercase()
        if (isYoutubeTv(u) || isXplore(u) || isHydraPlayer(u)) return false
        return u.contains("/watch") || u.contains("/shorts/") || u.contains("youtube.com/embed")
    }

    fun isBrowserHome(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("android_asset/brave_home") || u.startsWith("file:///android_asset/brave_home")
    }

    fun hideChrome(url: String): Boolean {
        return isXplore(url) || isHydraPlayer(url)
    }
}

interface SiteProfile {
    fun matches(url: String): Boolean
    fun hideChrome(url: String): Boolean = false
    fun playbackMode(): PlaybackMode = PlaybackMode.CustomView
    fun consumeActionUp(keyCode: Int): Boolean = false
    fun handleSearch(query: String, host: MainActivity): Boolean = false
    fun handleSearchKey(host: MainActivity): Boolean = false
    fun handleKey(event: KeyEvent, host: MainActivity): Boolean
    fun handleBack(host: MainActivity): Boolean
}

object SiteProfileResolver {
    private val profiles = listOf(
        XploreSiteProfile,
        YoutubeTvSiteProfile,
        HydraSiteProfile,
        GenericWebSiteProfile
    )

    fun fromUrl(url: String): SiteProfile {
        return profiles.first { it.matches(url) }
    }
}

object XploreSiteProfile : SiteProfile {
    override fun matches(url: String) = TvSite.isXplore(url)
    override fun hideChrome(url: String) = true
    override fun playbackMode() = PlaybackMode.ExoPlayer

    override fun consumeActionUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }

    override fun handleSearch(query: String, host: MainActivity): Boolean {
        val wv = host.activeWebView() ?: return false
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        host.hideKeyboard()
        host.editUrl.clearFocus()
        wv.requestFocus()
        wv.evaluateJavascript(
            "window._safeer_xplore_search ? window._safeer_xplore_search('$escaped') : false;",
            null
        )
        return true
    }

    override fun handleSearchKey(host: MainActivity): Boolean {
        val wv = host.activeWebView()
        host.hideKeyboard()
        host.editUrl.clearFocus()
        wv?.requestFocus()
        wv?.evaluateJavascript(
            "window._safeer_xplore_search ? window._safeer_xplore_search('') : false;",
            null
        )
        return true
    }

    override fun handleKey(event: KeyEvent, host: MainActivity): Boolean {
        val wv = host.activeWebView() ?: return false
        val keyCode = event.keyCode
        if (host.isTopBarFocused()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.hideKeyboard()
                host.editUrl.clearFocus()
                wv.requestFocus()
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            return false
        }
        if (!wv.hasFocus()) wv.requestFocus()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('UP');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('LEFT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('RIGHT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (host.playback.isNativeActive()) {
                    return host.playback.handleNativeKey(event)
                }
                val now = System.currentTimeMillis()
                if (now - host.lastCenterClickTime < 450) return true
                host.lastCenterClickTime = now
                XploreDashCapture.markOk()
                SafeerDbg.log(
                    "H4",
                    "XploreSiteProfile.kt:ok",
                    "xplore OK",
                    JSONObject().put("url", host.activeUrl().take(160)).put("hasWv", true)
                )
                wv.evaluateJavascript(XPLORE_OK_JS, null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                wv.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                wv.evaluateJavascript("window._safeer_seek(-10);", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                wv.evaluateJavascript("window._safeer_seek(10);", null)
                return true
            }
            else -> return false
        }
    }

    override fun handleBack(host: MainActivity): Boolean {
        val xv = host.activeWebView()
        SafeerDbg.log(
            "H14",
            "XploreSiteProfile.kt:back",
            "xplore native back",
            JSONObject().put("url", host.activeUrl().take(160)).put("exitingFs", host.playback.isActive())
        )
        xv?.evaluateJavascript(XPLORE_BACK_JS) { result ->
            if (result != null && result.contains("exit")) {
                host.runOnUiThread {
                    host.stopPageMedia("xploreExit")
                    xv.loadUrl("file:///android_asset/brave_home.html")
                }
            }
        }
        return true
    }

    private val XPLORE_OK_JS = """
        (function(){
            try {
                var path = (location.pathname || '').toLowerCase();
                var onLivetv = path.indexOf('/livetv') !== -1;
                var onHome = path.indexOf('/home') !== -1 || path === '/' || path === '';
                var elPeek = document.querySelector('.safeer-active-card');
                var clsPeek = ((elPeek && elPeek.className) || '') + '';
                var clsPeekL = clsPeek.toLowerCase();
                var tPeek = ((elPeek && (elPeek.innerText || elPeek.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
                var hrefPeek = '';
                try { hrefPeek = ((elPeek && elPeek.getAttribute && (elPeek.getAttribute('href') || '')) + '').toLowerCase(); } catch (eHref) {}
                var tileFocused = clsPeekL.indexOf('item--event') !== -1;
                var inMenu = false;
                try { inMenu = !!(elPeek && elPeek.closest && elPeek.closest('.menu, #csh__menu_bar, .menu-items-wrapper')); } catch (e0) {}
                var isHomeNav = !tileFocused && (
                    clsPeekL.indexOf('home-link') !== -1 ||
                    tPeek === 'za vas' ||
                    (hrefPeek.indexOf('/home') !== -1 && tPeek.indexOf('za vas') !== -1)
                );
                var isLiveNav = clsPeekL.indexOf('livetv-link') !== -1 || ((tPeek === 'tv v živo' || tPeek === 'tv v zivo') && clsPeekL.indexOf('menu-items-wrapper') === -1);
                var isOtherNav = !tileFocused && (
                    tPeek === 'filmi' || tPeek === 'več' || tPeek === 'vec' || tPeek === 'družina' || tPeek === 'druzina' ||
                    tPeek.indexOf('knjižnic') !== -1 || tPeek.indexOf('knjiznic') !== -1 ||
                    tPeek.indexOf('vodič') !== -1 || tPeek.indexOf('vodic') !== -1
                );
                var isTopNav = !tileFocused && (inMenu || isHomeNav || isLiveNav || isOtherNav);
                var pv = window._safeer_xplore_player_el || document.querySelector('video');
                var ov = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                var oc = ov ? ((ov.className || '') + '').toLowerCase() : '';
                var overlayOn = oc.indexOf('player-fullwindow') !== -1 || oc.indexOf('player-scaled') !== -1;
                if (!tileFocused && !isTopNav && overlayOn && pv && (pv.videoWidth || 0) >= 320 && pv.readyState >= 2) {
                    var pr = pv.getBoundingClientRect();
                    if (pr.width >= 800 && pr.height >= 450) {
                        if (pv.paused) { try { pv.play(); } catch (ePlay) {} }
                        return true;
                    }
                }
                if (isHomeNav) {
                    window._safeer_xplore_want_play = false;
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eSsH) {}
                    try { if (window._safeerDbg) window._safeerDbg('H342', 'XploreSiteProfile.kt:ok', 'za vas no play', { path: path.slice(0, 40), onHome: !!onHome }); } catch (eLogH) {}
                    if (onHome) return true;
                    location.href = 'https://www.xploretv.si/home';
                    return true;
                }
                if (isLiveNav) {
                    window._safeer_xplore_want_play = false;
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eSs) {}
                    if (onLivetv) {
                        if (window._safeer_xplore_ensure_focus) window._safeer_xplore_ensure_focus();
                        return true;
                    }
                    location.href = 'https://www.xploretv.si/livetv';
                    return true;
                }
                if (isTopNav) {
                    window._safeer_xplore_want_play = false;
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eSsN) {}
                    try { if (window._safeerDbg) window._safeerDbg('H342', 'XploreSiteProfile.kt:ok', 'menu no play', { t: tPeek.slice(0, 40), path: path.slice(0, 40) }); } catch (eLogN) {}
                    try { if (elPeek) elPeek.click(); } catch (eNc) {}
                    return true;
                }
                if (onLivetv && tileFocused) {
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eLv0) {}
                    window._safeer_xplore_want_play = true;
                    window._safeer_xplore_video_boosted = false;
                    window._safeer_xplore_playing = false;
                    var tile = elPeek;
                    try { if (tile && tile.closest) tile = tile.closest('.item--event, .item.item--event') || tile; } catch (eCl) {}
                    try { tile.click(); } catch (eClick) {}
                    return true;
                }
                var elHold = document.querySelector('.safeer-active-card');
                if (!elHold && window._safeer_xplore_ensure_focus) window._safeer_xplore_ensure_focus();
                var el = document.querySelector('.safeer-active-card');
                var t = ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
                var isMenu = false;
                try { isMenu = !!(el && el.closest && el.closest('.menu, #csh__menu_bar, .menu-items-wrapper')); } catch (e1) {}
                if (el && ((el.className || '') + '').toLowerCase().indexOf('livetv-link') !== -1) isMenu = true;
                if (el && ((el.className || '') + '').toLowerCase().indexOf('home-link') !== -1) isMenu = true;
                if (isMenu) {
                    window._safeer_xplore_want_play = false;
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eSsM) {}
                    return window._safeer_click_focused_card();
                }
                if (onLivetv) {
                    try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (eLv) {}
                    window._safeer_xplore_want_play = true;
                    window._safeer_xplore_video_boosted = false;
                    window._safeer_xplore_playing = false;
                    var liveTile = document.querySelector('.item.item--event.safeer-active-card, .item--event.safeer-active-card');
                    if (!liveTile) {
                        try { if (window._safeer_xplore_ensure_focus) window._safeer_xplore_ensure_focus(); } catch (eF) {}
                        liveTile = document.querySelector('.item.item--event.safeer-active-card, .item--event.safeer-active-card');
                    }
                    if (liveTile) {
                        try { liveTile.click(); } catch (eLc) {}
                        return true;
                    }
                    return window._safeer_click_focused_card();
                }
                if (el && t.indexOf('glej zdaj') === -1 && !isMenu) {
                    var cls = ((el.className || '') + '').toLowerCase();
                    if (cls.indexOf('search') === -1) {
                        var alreadyEvent = path.indexOf('/event') !== -1;
                        if (!alreadyEvent) {
                            try { sessionStorage.setItem('safeer_xplore_autoplay', '1'); } catch (e2) {}
                        } else {
                            try { sessionStorage.removeItem('safeer_xplore_autoplay'); } catch (e2b) {}
                        }
                        window._safeer_xplore_want_play = true;
                        window._safeer_xplore_video_boosted = false;
                        window._safeer_xplore_playing = false;
                        return window._safeer_click_focused_card();
                    }
                }
            } catch (e) {}
            return window._safeer_xplore_play_from_start ? window._safeer_xplore_play_from_start() : window._safeer_click_focused_card();
        })();
    """.trimIndent()

    private val XPLORE_BACK_JS = """
        (function(){
            var p = (location.pathname || '').toLowerCase();
            var v = window._safeer_xplore_player_el || document.querySelector('video');
            var r = v ? v.getBoundingClientRect() : { width: 0, height: 0 };
            var framed = !!(v && (v.videoWidth || 0) >= 320 && v.readyState >= 2);
            var ov = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
            var oc = ov ? ((ov.className || '') + '').toLowerCase() : '';
            var overlayOn = oc.indexOf('player-fullwindow') !== -1 || oc.indexOf('player-scaled') !== -1;
            var playing = overlayOn || framed || !!window._safeer_xplore_playing || (!!v && !v.paused && r.width >= 400);
            try { if (window._safeerDbg) window._safeerDbg('H14', 'XploreSiteProfile.kt:back', 'xplore back', { path: p.slice(0, 80), playing: !!playing, framed: !!framed, overlay: overlayOn, w: Math.round(r.width || 0), vw: v ? (v.videoWidth || 0) : 0 }); } catch (e) {}
            var hadCdm = false;
            try { hadCdm = !!(window._safeer_xplore_release_cdm && window._safeer_xplore_release_cdm()); } catch (eCdm) {}
            try {
                document.querySelectorAll('video,audio').forEach(function(m){ try { m.pause(); m.muted = true; } catch (eP) {} });
            } catch (eVid) {}
            try {
                window._safeer_xplore_playing = false;
                window._safeer_xplore_want_play = false;
                window._safeer_xplore_video_boosted = false;
                window._safeer_xplore_fs_clicked = false;
                window._safeer_xplore_replay_clicked = false;
                window._safeer_xplore_player_el = null;
                if (window._safeerSiteAgent && window._safeerSiteAgent.clearWant) window._safeerSiteAgent.clearWant();
            } catch (e2) {}
            var stay = playing || overlayOn || p.indexOf('/event') !== -1 || p.indexOf('/livetv') !== -1 ||
                p.indexOf('/movies') !== -1 || p.indexOf('/library') !== -1 || p.indexOf('/gridguide') !== -1;
            if (stay) {
                if (p.indexOf('/livetv') !== -1) {
                    if (playing || overlayOn || framed) {
                        try { if (window._safeer_xplore_unsmash) window._safeer_xplore_unsmash(); } catch (eU) {}
                        try {
                            var ovClose = document.querySelector('.zw-overlays-layer, [class*="overlays-layer"]');
                            if (ovClose) {
                                ovClose.classList.remove('player-fullwindow', 'player-scaled');
                                ovClose.classList.add('player-closed');
                            }
                        } catch (eC) {}
                        function goLivetv() {
                            location.href = 'https://www.xploretv.si/livetv';
                        }
                        if (hadCdm) setTimeout(goLivetv, 400);
                        else goLivetv();
                        try { if (window._safeerDbg) window._safeerDbg('H270', 'XploreSiteProfile.kt:back', 'livetv reload', { framed: !!framed, overlay: overlayOn }); } catch (eL) {}
                        return 'livetv';
                    }
                    location.href = 'https://www.xploretv.si/home';
                    return 'home';
                }
                location.href = 'https://www.xploretv.si/home';
                return 'home';
            }
            try { if (window.SafeerBridge && window.SafeerBridge.setChromeHidden) window.SafeerBridge.setChromeHidden(false); } catch (e3) {}
            return 'exit';
        })();
    """.trimIndent()
}

object YoutubeTvSiteProfile : SiteProfile {
    override fun matches(url: String) = TvSite.isYoutubeTv(url)
    override fun playbackMode() = PlaybackMode.InPlaceWebView

    override fun handleSearch(query: String, host: MainActivity): Boolean {
        val wv = host.activeWebView() ?: return false
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        host.hideKeyboard()
        host.editUrl.clearFocus()
        wv.requestFocus()
        wv.evaluateJavascript(
            "window._safeer_yt_tv_search ? window._safeer_yt_tv_search('$escaped') : (location.hash = '#/search?q=' + encodeURIComponent('$escaped'));",
            null
        )
        return true
    }

    override fun handleSearchKey(host: MainActivity): Boolean {
        val wv = host.activeWebView()
        host.hideKeyboard()
        host.editUrl.clearFocus()
        wv?.requestFocus()
        wv?.evaluateJavascript(
            "window._safeer_yt_tv_search ? window._safeer_yt_tv_search('') : (location.hash = '#/search');",
            null
        )
        return true
    }

    fun dispatchYoutubeTvKey(host: MainActivity, event: KeyEvent): Boolean {
        val webView = host.activeWebView() ?: return host.superDispatchKey(event)
        if (event.action == KeyEvent.ACTION_DOWN && !webView.hasFocus()) {
            host.hideKeyboard()
            host.editUrl.clearFocus()
            webView.requestFocus()
        }
        return webView.dispatchKeyEvent(event)
    }

    override fun handleKey(event: KeyEvent, host: MainActivity): Boolean {
        val keyCode = event.keyCode
        val curUrl = host.activeUrl().lowercase()
        if (host.isChromeFocused() && event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            host.hideKeyboard()
            host.editUrl.clearFocus()
            host.activeWebView()?.requestFocus()
            return true
        }
        if (host.isChromeFocused() || host.virtualPointerView.isPointerVisible ||
            host.tabSwitcherOverlay.visibility == android.view.View.VISIBLE ||
            host.findInPageBar.visibility == android.view.View.VISIBLE
        ) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (curUrl.contains("#/watch") || curUrl.contains("/watch?v=")) {
                    host.btnBack.requestFocus()
                    true
                } else {
                    dispatchYoutubeTvKey(host, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> dispatchYoutubeTvKey(host, event)
            else -> false
        }
    }

    override fun handleBack(host: MainActivity): Boolean {
        val ytWv = host.activeWebView() ?: return false
        ytWv.evaluateJavascript(
            """
            (function(){
                var href = (location.href || '').toLowerCase();
                var h = (location.hash || '').toLowerCase();
                var watching = h.indexOf('/watch') !== -1 || h.indexOf('/player') !== -1 ||
                    href.indexOf('watch?v=') !== -1 ||
                    !!document.querySelector('ytlr-watch, ytlr-watch-default, ytlr-player');
                if (!watching) {
                    var v = document.querySelector('video');
                    if (v && (v.videoWidth || 0) >= 320 && Math.max(v.clientWidth || 0, v.offsetWidth || 0) >= 640) {
                        watching = true;
                    }
                }
                try {
                    document.querySelectorAll('video,audio').forEach(function(m){ try { m.pause(); } catch (eP) {} });
                } catch (eV) {}
                if (watching || h.indexOf('/search') !== -1) {
                    location.replace('https://www.youtube.com/tv');
                    return 'browse';
                }
                return 'exit';
            })();
            """.trimIndent()
        ) { result ->
            if (result != null && result.contains("exit")) {
                host.runOnUiThread {
                    ytWv.loadUrl("file:///android_asset/brave_home.html")
                }
            }
        }
        return true
    }
}

object HydraSiteProfile : SiteProfile {
    override fun matches(url: String) = TvSite.isHydra(url)
    override fun hideChrome(url: String) = TvSite.isHydraPlayer(url)
    override fun playbackMode() = PlaybackMode.CustomView

    override fun consumeActionUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }

    override fun handleKey(event: KeyEvent, host: MainActivity): Boolean {
        val wv = host.activeWebView() ?: return false
        val keyCode = event.keyCode
        val stayInKiosk = TvSite.isHydraPlayer(host.activeUrl())
        if (host.isTopBarFocused()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.hideKeyboard()
                host.editUrl.clearFocus()
                wv.requestFocus()
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            return false
        }
        if (!wv.hasFocus()) wv.requestFocus()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('UP');") { result ->
                    if (stayInKiosk) return@evaluateJavascript
                    if (result == "-1" || result == "null" || result == null) {
                        host.runOnUiThread {
                            host.mobileTopBar.visibility = android.view.View.VISIBLE
                            host.mobileTopBar.animate().translationY(0f).setDuration(150).start()
                            host.editUrl.requestFocus()
                        }
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('LEFT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('RIGHT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                wv.evaluateJavascript("window._safeer_click_focused_card();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                wv.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                wv.evaluateJavascript("window._safeer_seek(-10);", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                wv.evaluateJavascript("window._safeer_seek(10);", null)
                return true
            }
            else -> return false
        }
    }

    override fun handleBack(host: MainActivity): Boolean {
        if (!TvSite.isHydraPlayer(host.activeUrl())) {
            return GenericWebSiteProfile.handleBack(host)
        }
        val hv = host.activeWebView() ?: return false
        hv.evaluateJavascript(
            """
            (function(){
                window._safeer_hydra_leave = true;
                try { if (window._safeer_hydra_unsmash) window._safeer_hydra_unsmash(); } catch (eU) {}
                try {
                    document.querySelectorAll('video,audio').forEach(function(m){ try { m.pause(); } catch (eP) {} });
                } catch (eV) {}
                return 'exit';
            })();
            """.trimIndent()
        ) { _ ->
            host.runOnUiThread {
                if (hv.canGoBack()) {
                    hv.goBack()
                } else {
                    hv.loadUrl("https://hydrahd.ws/")
                }
            }
        }
        return true
    }
}

object GenericWebSiteProfile : SiteProfile {
    override fun matches(url: String) = true
    override fun playbackMode() = PlaybackMode.CustomView

    override fun handleKey(event: KeyEvent, host: MainActivity): Boolean {
        val wv = host.activeWebView()
        val keyCode = event.keyCode
        val watch = TvSite.isWatchPage(host.activeUrl())
        if (watch && !host.isTopBarFocused() && !host.virtualPointerView.isPointerVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val now = System.currentTimeMillis()
                    if (now - host.lastCenterClickTime < 380) {
                        wv?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
                    } else {
                        wv?.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                    }
                    host.lastCenterClickTime = now
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    wv?.evaluateJavascript("window._safeer_seek(-10);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    wv?.evaluateJavascript("window._safeer_seek(10);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    host.editUrl.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    wv?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
                    return true
                }
            }
        }
        if (wv == null) return false
        if (host.isTopBarFocused()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                host.hideKeyboard()
                host.editUrl.clearFocus()
                wv.requestFocus()
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            return false
        }
        if (!wv.hasFocus()) wv.requestFocus()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('UP');") { result ->
                    if (result == "-1" || result == "null" || result == null) {
                        host.runOnUiThread {
                            host.mobileTopBar.visibility = android.view.View.VISIBLE
                            host.mobileTopBar.animate().translationY(0f).setDuration(150).start()
                            host.editUrl.requestFocus()
                        }
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('LEFT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                wv.evaluateJavascript("window._safeer_navigate_spatial('RIGHT');", null)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                wv.evaluateJavascript("window._safeer_click_focused_card();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                wv.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                wv.evaluateJavascript("window._safeer_seek(-10);", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                wv.evaluateJavascript("window._safeer_seek(10);", null)
                return true
            }
            else -> return false
        }
    }

    override fun handleBack(host: MainActivity): Boolean {
        val tab = host.tabManager.getActiveTab()
        if (TvSite.isWatchPage(host.activeUrl())) {
            if (tab?.webView?.canGoBack() == true) {
                tab.webView.goBack()
                return true
            }
            tab?.webView?.loadUrl("file:///android_asset/brave_home.html")
            return true
        }
        if (tab != null && tab.webView.canGoBack()) {
            tab.webView.goBack()
            return true
        }
        if (host.tabManager.count > 1 && tab != null) {
            host.tabManager.closeTab(host, tab.id)
            return true
        }
        return false
    }
}
