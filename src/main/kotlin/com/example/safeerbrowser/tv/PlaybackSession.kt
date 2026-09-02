package com.example.safeerbrowser

import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import org.json.JSONObject
import java.util.UUID

/**
 * Playback overlay session. Hydra/generic sites use Android WebView custom-view.
 * Xplore DASH+Widevine is decoded by Media3 ExoPlayer on a SurfaceView overlay.
 */
interface PlaybackSession {
    fun enter(view: View, callback: WebChromeClient.CustomViewCallback?)
    fun exit()
    fun isActive(): Boolean
}

class HostPlayback(private val host: MainActivity) : PlaybackSession {
    private val custom = WebViewCustomViewSession(host)
    val exo = ExoPlayerSession(host)

    override fun enter(view: View, callback: WebChromeClient.CustomViewCallback?) {
        if (exo.isActive()) return
        custom.enter(view, callback)
    }

    override fun exit() {
        if (exo.isActive()) exo.exit()
        if (custom.isActive()) custom.exit()
    }

    override fun isActive(): Boolean = exo.isActive() || custom.isActive()

    fun isNativeActive(): Boolean = exo.isActive()

    fun playDash(session: XploreDashSession) {
        exo.playDash(session)
    }

    fun playClearSmoke() {
        exo.playClearSmoke()
    }

    fun handleNativeKey(event: KeyEvent): Boolean = exo.handleKey(event)

    fun release() {
        exo.release()
        custom.exit()
    }
}

class WebViewCustomViewSession(private val host: MainActivity) : PlaybackSession {
    override fun enter(view: View, callback: WebChromeClient.CustomViewCallback?) {
        host.customVideoView = view
        host.customVideoCallback = callback
        host.mobileTopBar.visibility = View.GONE
        host.webViewContainer.visibility = View.GONE
        host.mainRoot.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun exit() {
        host.customVideoView?.let { host.mainRoot.removeView(it) }
        host.customVideoView = null
        host.customVideoCallback = null
        val url = host.activeUrl()
        val stayKiosk = SiteProfileResolver.fromUrl(url).hideChrome(url)
        host.mobileTopBar.visibility = if (stayKiosk) View.GONE else View.VISIBLE
        host.webViewContainer.visibility = View.VISIBLE
    }

    override fun isActive(): Boolean = host.customVideoView != null
}

class ExoPlayerSession(private val host: MainActivity) : PlaybackSession {
    companion object {
        const val CLEAR_DASH_MPD = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
        const val SMOKE_CHANNEL = "SMOKE_CLEAR"
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: FrameLayout? = null
    private var surfaceView: SurfaceView? = null
    private var player: ExoPlayer? = null
    private var spinner: ProgressBar? = null
    private var statusLabel: TextView? = null
    private var playingChannel: String = ""
    private var surfaceSecure: Boolean = false
    private var lastSession: XploreDashSession? = null
    private var surfaceBound: Boolean = false
    private var loggedEncryptedReady: Boolean = false

    private val pauseWebViewJs = """
        (function(){
            window._safeer_xplore_native_player = true;
            try { if (window._safeer_xplore_release_cdm) window._safeer_xplore_release_cdm(); } catch (e) {}
            try {
                document.querySelectorAll('video,audio').forEach(function(m){
                    try { m.pause(); m.muted = true; m.volume = 0; } catch (e2) {}
                });
            } catch (e3) {}
            return 1;
        })();
    """.trimIndent()

    private val holdWebView = object : Runnable {
        override fun run() {
            if (!isActive() || playingChannel == SMOKE_CHANNEL) return
            try {
                host.activeWebView()?.evaluateJavascript(pauseWebViewJs, null)
            } catch (_: Exception) {}
            main.postDelayed(this, 2500L)
        }
    }

    override fun enter(view: View, callback: WebChromeClient.CustomViewCallback?) {
        // Custom-view is not used for Xplore native playback.
    }

    fun playDash(session: XploreDashSession) {
        if (session.mpdUrl.isEmpty()) return
        main.post {
            startOnUi(session)
        }
    }

    fun playClearSmoke() {
        SafeerDbg.log(
            "H337",
            "ExoPlayerSession.kt:smoke",
            "clear dash no drm",
            JSONObject().put("url", CLEAR_DASH_MPD)
        )
        playDash(
            XploreDashSession(
                mpdUrl = CLEAR_DASH_MPD,
                licenseUrl = "",
                licenseHeaders = emptyMap(),
                mediaHeaders = emptyMap(),
                dashChannel = SMOKE_CHANNEL,
                jsonWrapKey = null,
                encrypted = false,
                hasPssh = false
            )
        )
    }

    private fun startOnUi(session: XploreDashSession) {
        host.mobileTopBar.visibility = View.GONE
        try {
            host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        val secure = session.encrypted
        ensureOverlay(secure)
        setStatus(
            if (session.dashChannel == SMOKE_CHANNEL) "Media3 DASH (brez DRM)"
            else "Media3 ${session.dashChannel.ifEmpty { "DASH" }}"
        )
        val wv = host.activeWebView()
        val skipCdm = session.dashChannel == SMOKE_CHANNEL
        if (wv != null && !skipCdm) {
            wv.evaluateJavascript(pauseWebViewJs) {
                attachPlayerSafe(session)
            }
        } else {
            attachPlayerSafe(session)
        }
        if (!skipCdm) {
            main.removeCallbacks(holdWebView)
            main.postDelayed(holdWebView, 2500L)
        }
    }

    private fun attachPlayerSafe(session: XploreDashSession) {
        try {
            Class.forName("androidx.media3.exoplayer.ExoPlayer")
        } catch (t: Throwable) {
            setStatus("Media3 MANJKA v APK (dex)")
            SafeerDbg.log(
                "H337",
                "ExoPlayerSession.kt:missing",
                "media3 class not found",
                JSONObject().put("err", t.javaClass.simpleName).put("msg", (t.message ?: "").take(120))
            )
            return
        }
        try {
            attachPlayer(session)
        } catch (t: Throwable) {
            setStatus("ExoPlayer crash: ${t.javaClass.simpleName}")
            SafeerDbg.log(
                "H337",
                "ExoPlayerSession.kt:crash",
                "attach failed",
                JSONObject().put("err", t.javaClass.simpleName).put("msg", (t.message ?: "").take(160))
            )
        }
    }

    private fun setStatus(text: String) {
        statusLabel?.text = text
        statusLabel?.visibility = View.VISIBLE
    }

    private fun ensureOverlay(secure: Boolean = false) {
        val frame = overlay ?: FrameLayout(host).also { f ->
            f.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            f.setBackgroundColor(Color.BLACK)
            f.isClickable = true
            f.isFocusable = true
            f.isFocusableInTouchMode = true
            f.elevation = 48f
            overlay = f
        }
        if (surfaceView != null && surfaceSecure != secure) {
            try { player?.setVideoSurface(null) } catch (_: Exception) {}
            frame.removeView(surfaceView)
            surfaceView = null
            surfaceBound = false
        }
        if (surfaceView == null) {
            val sv = SurfaceView(host)
            sv.holder.setFormat(PixelFormat.OPAQUE)
            sv.setZOrderMediaOverlay(true)
            if (secure && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                sv.setSecure(true)
            }
            surfaceSecure = secure
            frame.addView(
                sv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            surfaceView = sv
        }
        if (spinner == null) {
            val pb = ProgressBar(host)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.CENTER
            frame.addView(pb, lp)
            spinner = pb
        }
        if (statusLabel == null) {
            val tv = TextView(host)
            tv.setTextColor(Color.WHITE)
            tv.textSize = 22f
            tv.setPadding(36, 28, 36, 28)
            tv.setBackgroundColor(Color.parseColor("#CC111827"))
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.topMargin = 48
            frame.addView(tv, lp)
            statusLabel = tv
        }
        spinner?.visibility = View.VISIBLE
        if (frame.parent == null) {
            host.mainRoot.addView(
                frame,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        frame.bringToFront()
        frame.requestFocus()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val exo = player ?: return
            if (playbackState == Player.STATE_READY) {
                spinner?.visibility = View.GONE
                val vs = exo.videoSize
                SafeerDbg.log(
                    "H333",
                    "ExoPlayerSession.kt:ready",
                    "native ready",
                    JSONObject()
                        .put("ch", playingChannel)
                        .put("w", vs.width)
                        .put("h", vs.height)
                        .put("live", exo.isCurrentMediaItemLive)
                        .put("enc", lastSession?.encrypted == true)
                )
                logEncryptedReady(exo)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = (error.message ?: "").take(160)
            setStatus("Exo napaka: ${error.errorCode} $msg")
            val s = lastSession
            SafeerDbg.log(
                "H334",
                "ExoPlayerSession.kt:err",
                "native error",
                JSONObject()
                    .put("ch", playingChannel)
                    .put("code", error.errorCode)
                    .put("msg", msg)
                    .put("cause", (error.cause?.javaClass?.simpleName ?: ""))
                    .put("enc", s?.encrypted == true)
                    .put("pssh", s?.hasPssh == true)
                    .put("licHost", hostOf(s?.licenseUrl ?: ""))
                    .put("hdrNames", headerNames(s))
            )
            logExoError(error)
            spinner?.visibility = View.GONE
        }

        override fun onRenderedFirstFrame() {
            spinner?.visibility = View.GONE
            if (playingChannel == SMOKE_CHANNEL) {
                setStatus("Media3 DASH OK (brez DRM)")
            } else {
                statusLabel?.visibility = View.GONE
            }
            SafeerDbg.log(
                "H335",
                "ExoPlayerSession.kt:frame",
                "first frame",
                JSONObject()
                    .put("ch", playingChannel)
                    .put("enc", lastSession?.encrypted == true)
            )
            player?.let { logEncryptedReady(it) }
        }
    }

    private fun attachPlayer(session: XploreDashSession) {
        lastSession = session
        loggedEncryptedReady = false
        ensureOverlay(session.encrypted)
        playingChannel = session.dashChannel
        val licenseHeaders = LinkedHashMap(session.licenseHeaders)
        val mediaHeaders = LinkedHashMap(
            if (session.mediaHeaders.isNotEmpty()) session.mediaHeaders else session.licenseHeaders
        )
        mediaHeaders.keys.filter { it.equals("Content-Type", true) }.toList().forEach { mediaHeaders.remove(it) }
        val ua = mediaHeaders["User-Agent"]
            ?: licenseHeaders["User-Agent"]
            ?: ChromiumEngineView.DESKTOP_USER_AGENT
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(session.mpdUrl)
            .setMimeType(MimeTypes.APPLICATION_MPD)
        val live = session.dashChannel.contains("ott", true) || session.mpdUrl.contains("__c/")
        if (live) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(5_000)
                    .build()
            )
        }
        val mediaItem = mediaItemBuilder.build()
        val exo = ensurePlayer()
        try {
            exo.stop()
        } catch (_: Exception) {}
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(mediaHeaders)
        val dashFactory = DashMediaSource.Factory(DefaultDataSource.Factory(host, httpFactory))
        if (session.encrypted) {
            val licenseHttp = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(4_000)
                .setReadTimeoutMs(4_000)
                .setDefaultRequestProperties(licenseHeaders)
            val wrapKey = session.jsonWrapKey
            val forceDefault = session.licenseUrl.isNotEmpty()
            dashFactory.setDrmSessionManagerProvider {
                val callback = if (!wrapKey.isNullOrEmpty()) {
                    JsonWrapDrmCallback(session.licenseUrl, licenseHeaders, wrapKey)
                } else {
                    HttpMediaDrmCallback(session.licenseUrl, forceDefault, licenseHttp).also { cb ->
                        licenseHeaders.forEach { (k, v) ->
                            if (!k.equals("Content-Type", true) && !k.equals("Content-Length", true)) {
                                cb.setKeyRequestProperty(k, v)
                            }
                        }
                    }
                }
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(callback)
            }
        }
        exo.setMediaSource(dashFactory.createMediaSource(mediaItem))
        bindSurface(exo)
        exo.prepare()
        exo.playWhenReady = true
        exo.volume = 1f
        val okMs = if (host.lastCenterClickTime > 0L) System.currentTimeMillis() - host.lastCenterClickTime else -1L
        SafeerDbg.log(
            "H336",
            "ExoPlayerSession.kt:prep",
            "native prepare",
            JSONObject()
                .put("ch", playingChannel)
                .put("hasLic", session.licenseUrl.isNotEmpty())
                .put("enc", session.encrypted)
                .put("pssh", session.hasPssh)
                .put("hdrN", session.licenseHeaders.size)
                .put("hdrNames", headerNames(session))
                .put("mediaN", session.mediaHeaders.size)
                .put("licHost", hostOf(session.licenseUrl))
                .put("licPath", mpdPathOf(session.licenseUrl))
                .put("okMs", okMs)
                .put("wrap", session.jsonWrapKey ?: "")
        )
        if (session.encrypted) {
            Log.i(
                "SafeerExo",
                "prepare encrypted ch=${session.dashChannel} mpdPath=${mpdPathOf(session.mpdUrl)} hasPssh=${session.hasPssh} licHost=${hostOf(session.licenseUrl)} licPath=${mpdPathOf(session.licenseUrl)} hdrNames=${headerNames(session)} okMs=$okMs"
            )
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 30_000, 1_000, 2_000)
            .build()
        val exo = ExoPlayer.Builder(host)
            .setLoadControl(loadControl)
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        exo.setWakeMode(C.WAKE_MODE_NETWORK)
        exo.addListener(playerListener)
        player = exo
        return exo
    }

    private fun bindSurface(exo: ExoPlayer) {
        val sv = surfaceView ?: return
        if (!surfaceBound) {
            sv.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    player?.setVideoSurface(holder.surface)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    player?.setVideoSurface(null)
                }
            })
            surfaceBound = true
        }
        val surface = sv.holder.surface
        if (surface != null && surface.isValid) {
            exo.setVideoSurface(surface)
        } else {
            exo.setVideoSurfaceView(sv)
        }
    }

    private fun logEncryptedReady(exo: ExoPlayer) {
        if (loggedEncryptedReady) return
        val s = lastSession ?: return
        if (!s.encrypted) return
        val vs = exo.videoSize
        if (vs.width <= 0 || vs.height <= 0) return
        loggedEncryptedReady = true
        val licHost = hostOf(s.licenseUrl)
        Log.i(
            "SafeerExo",
            "encrypted READY w=${vs.width} h=${vs.height} live=${exo.isCurrentMediaItemLive} licHost=$licHost ch=${s.dashChannel}"
        )
        SafeerDbg.log(
            "H333",
            "ExoPlayerSession.kt:ready",
            "encrypted READY",
            JSONObject()
                .put("ch", s.dashChannel)
                .put("w", vs.width)
                .put("h", vs.height)
                .put("live", exo.isCurrentMediaItemLive)
                .put("licHost", licHost)
        )
    }

    private fun logExoError(error: PlaybackException) {
        val s = lastSession
        Log.e(
            "SafeerExo",
            "mpd path=${mpdPathOf(s?.mpdUrl ?: "")} hasPssh=${s?.hasPssh == true} licHost=${hostOf(s?.licenseUrl ?: "")} hdrNames=${headerNames(s)} ch=${s?.dashChannel ?: ""} code=${error.errorCode} chain=${causeChain(error)}"
        )
    }

    private fun headerNames(s: XploreDashSession?): String {
        return s?.licenseHeaders?.keys?.joinToString(",") ?: ""
    }

    private fun hostOf(url: String): String {
        if (url.isEmpty()) return ""
        return try {
            Uri.parse(url).host ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun mpdPathOf(url: String): String {
        if (url.isEmpty()) return ""
        return try {
            (Uri.parse(url).path ?: "").take(160)
        } catch (_: Exception) {
            ""
        }
    }

    private fun causeChain(error: Throwable): String {
        val parts = ArrayList<String>()
        var c: Throwable? = error
        var i = 0
        while (c != null && i < 6) {
            val msg = (c.message ?: "").replace('\n', ' ').take(80)
            parts.add(c.javaClass.simpleName + ":" + msg)
            c = c.cause
            i++
        }
        return parts.joinToString(" <- ")
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isActive() || event.action != KeyEvent.ACTION_DOWN) return isActive()
        val p = player ?: return true
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (p.isPlaying) p.pause() else p.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                p.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                p.pause()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBy(-10_000L)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy(10_000L)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        if (!p.isCurrentMediaItemSeekable) return
        val next = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(next)
    }

    override fun exit() {
        main.removeCallbacks(holdWebView)
        releasePlayerOnly()
        overlay?.let { host.mainRoot.removeView(it) }
        overlay = null
        surfaceView = null
        spinner = null
        statusLabel = null
        playingChannel = ""
        surfaceSecure = false
        surfaceBound = false
        lastSession = null
        loggedEncryptedReady = false
        try {
            host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        XploreDashCapture.reset()
        val url = host.activeUrl()
        val stayKiosk = SiteProfileResolver.fromUrl(url).hideChrome(url)
        host.mobileTopBar.visibility = if (stayKiosk) View.GONE else View.VISIBLE
        host.webViewContainer.visibility = View.VISIBLE
        host.activeWebView()?.evaluateJavascript(
            "window._safeer_xplore_native_player=false;",
            null
        )
    }

    override fun isActive(): Boolean = overlay?.parent != null

    fun release() {
        exit()
    }

    private fun releasePlayerOnly() {
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }
}

private class JsonWrapDrmCallback(
    private val licenseUrl: String,
    headers: Map<String, String>,
    private val wrapKey: String
) : MediaDrmCallback {
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(headers["User-Agent"] ?: ChromiumEngineView.DESKTOP_USER_AGENT)
        .setConnectTimeoutMs(4_000)
        .setReadTimeoutMs(4_000)
        .setDefaultRequestProperties(headers)
    private val inner = HttpMediaDrmCallback(licenseUrl, dataSourceFactory)

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest
    ): ByteArray {
        return inner.executeProvisionRequest(uuid, request)
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest
    ): ByteArray {
        val challenge = request.data ?: ByteArray(0)
        val b64 = Base64.encodeToString(challenge, Base64.NO_WRAP)
        val json = JSONObject().put(wrapKey, b64).toString()
        val url = request.licenseServerUrl?.takeIf { it.isNotEmpty() } ?: licenseUrl
        val dataSource = dataSourceFactory.createDataSource()
        val spec = DataSpec.Builder()
            .setUri(url)
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(json.toByteArray(Charsets.UTF_8))
            .setHttpRequestHeaders(
                mapOf("Content-Type" to "application/json")
            )
            .build()
        dataSource.open(spec)
        try {
            val buf = java.io.ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            while (true) {
                val n = dataSource.read(tmp, 0, tmp.size)
                if (n == C.RESULT_END_OF_INPUT || n < 0) break
                buf.write(tmp, 0, n)
            }
            val raw = buf.toByteArray()
            val asText = raw.toString(Charsets.UTF_8).trim()
            if (asText.startsWith("{")) {
                val obj = JSONObject(asText)
                for (k in listOf("license", "payload", "ckc", "response", wrapKey)) {
                    if (obj.has(k)) {
                        val v = obj.optString(k, "")
                        if (v.isNotEmpty()) {
                            return try {
                                Base64.decode(v, Base64.DEFAULT)
                            } catch (_: Exception) {
                                v.toByteArray(Charsets.UTF_8)
                            }
                        }
                    }
                }
            }
            return raw
        } finally {
            try { dataSource.close() } catch (_: Exception) {}
        }
    }
}
