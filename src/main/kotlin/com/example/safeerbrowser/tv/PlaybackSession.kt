package com.example.safeerbrowser

import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

    fun isLiveNative(): Boolean = exo.isActive() && exo.isLiveStream()

    fun tuneLiveChannel(oneBased: Int) {
        exo.tuneLiveChannel(oneBased)
    }

    fun togglePlayPause() {
        if (exo.isActive()) {
            exo.togglePlayPause()
        } else {
            host.activeWebView()?.evaluateJavascript(
                """
                (function(){
                    var v = document.querySelector('video');
                    if (v) {
                        if (v.paused) v.play(); else v.pause();
                    }
                })();
                """.trimIndent(), null
            )
        }
    }

    fun seekBy(deltaSeconds: Int) {
        if (exo.isActive()) {
            exo.seekBy((deltaSeconds * 1000).toLong())
        } else {
            val delta = deltaSeconds
            host.activeWebView()?.evaluateJavascript(
                """
                (function(){
                    var v = document.querySelector('video');
                    if (v) v.currentTime = Math.max(0, v.currentTime + ($delta));
                })();
                """.trimIndent(), null
            )
        }
    }

    fun showOsd(title: String, subtitle: String? = null, durationMs: Long = 3000L) {
        if (exo.isActive()) {
            exo.showOsd(title, subtitle, durationMs)
        } else {
            host.showTvOsd(title, subtitle, durationMs)
        }
    }

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
    private var trackSelector: DefaultTrackSelector? = null
    private var spinner: ProgressBar? = null
    private var statusLabel: TextView? = null
    private var playingChannel: String = ""
    private var surfaceSecure: Boolean = false
    private var lastSession: XploreDashSession? = null
    private var surfaceBound: Boolean = false
    private var loggedEncryptedReady: Boolean = false
    private var lastZapAt: Long = 0L
    /** 0 = prefer AVC; 1 = already retried 720p/30fps AVC after MediaCodec 4003. */
    private var codecRetry: Int = 0

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

    /** Pause page media without releasing MediaKeys (zap / clear HEVC must not fight Exo). */
    private val pauseVideosJs = """
        (function(){
            window._safeer_xplore_native_player = true;
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
                host.activeWebView()?.evaluateJavascript(pauseVideosJs, null)
            } catch (_: Exception) {}
            main.postDelayed(this, 4000L)
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
        main.removeCallbacks(holdWebView)
        val alreadyNative = player != null
        when {
            wv == null || skipCdm -> attachPlayerSafe(session)
            session.encrypted && !alreadyNative -> wv.evaluateJavascript(pauseWebViewJs) {
                attachPlayerSafe(session)
            }
            else -> wv.evaluateJavascript(pauseVideosJs) {
                attachPlayerSafe(session)
            }
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

    private val hideOsdRunnable = Runnable {
        statusLabel?.animate()?.alpha(0f)?.setDuration(250)?.withEndAction {
            statusLabel?.visibility = View.GONE
        }?.start()
    }

    private fun setStatus(text: String, autoHideMs: Long = 0L) {
        main.post {
            statusLabel?.text = text
            statusLabel?.alpha = 1f
            statusLabel?.visibility = View.VISIBLE
            main.removeCallbacks(hideOsdRunnable)
            if (autoHideMs > 0L) {
                main.postDelayed(hideOsdRunnable, autoHideMs)
            }
        }
    }

    fun showOsd(title: String, subtitle: String? = null, durationMs: Long = 3000L) {
        val text = if (!subtitle.isNullOrEmpty()) "$title\n$subtitle" else title
        setStatus(text, durationMs)
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
            tv.setPadding(44, 20, 44, 20)
            tv.gravity = Gravity.CENTER
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#E60B0F17"))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#3300D2FF"))
            }
            tv.background = bg
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = 70
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
                    .put("retry", codecRetry)
            )
            logExoError(error)
            if (s != null && codecRetry < 1 && isVideoCodecError(error)) {
                codecRetry = 1
                spinner?.visibility = View.VISIBLE
                setStatus("Druga kakovost (H.264) …")
                SafeerDbg.log(
                    "H341",
                    "ExoPlayerSession.kt:retry",
                    "hevc 4003 fallback avc",
                    JSONObject().put("ch", playingChannel).put("code", error.errorCode)
                )
                main.post { retrySaferCodec(s) }
                return
            }
            spinner?.visibility = View.GONE
            setStatus("Predvajanje ni uspelo. NAZAJ za spored.")
        }

        override fun onRenderedFirstFrame() {
            spinner?.visibility = View.GONE
            if (playingChannel == SMOKE_CHANNEL) {
                setStatus("Media3 DASH OK (brez DRM)")
            } else {
                setStatus(playingChannel.ifEmpty { "Predvajanje" }, 3000L)
                try {
                    host.activeWebView()?.evaluateJavascript(pauseVideosJs, null)
                } catch (_: Exception) {}
                main.removeCallbacks(holdWebView)
                main.postDelayed(holdWebView, 4000L)
            }
            val fmt = player?.videoFormat
            SafeerDbg.log(
                "H335",
                "ExoPlayerSession.kt:frame",
                "first frame",
                JSONObject()
                    .put("ch", playingChannel)
                    .put("enc", lastSession?.encrypted == true)
                    .put("mime", fmt?.sampleMimeType ?: "")
                    .put("codec", fmt?.codecs ?: "")
                    .put("retry", codecRetry)
            )
            player?.let { logEncryptedReady(it) }
        }
    }

    private fun attachPlayer(session: XploreDashSession) {
        lastSession = session
        loggedEncryptedReady = false
        if (playingChannel != session.dashChannel) {
            codecRetry = 0
        }
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
                    .setTargetOffsetMs(2_000)
                    .setMinOffsetMs(1_200)
                    .setMaxOffsetMs(4_000)
                    .build()
            )
        }
        val mediaItem = mediaItemBuilder.build()
        val exo = ensurePlayer()
        applyTrackPolicy()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
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
            .setBufferDurationsMs(1_500, 12_000, 800, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val sel = DefaultTrackSelector(host)
        trackSelector = sel
        applyTrackPolicy()
        val renderers = DefaultRenderersFactory(host)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        val exo = ExoPlayer.Builder(host)
            .setRenderersFactory(renderers)
            .setTrackSelector(sel)
            .setLoadControl(loadControl)
            .build()
        try {
            exo.setForegroundMode(true)
        } catch (_: Exception) {}
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

    private fun applyTrackPolicy() {
        val sel = trackSelector ?: return
        val b = sel.buildUponParameters()
            .setForceHighestSupportedBitrate(false)
            .setAllowVideoMixedMimeTypeAdaptiveness(false)
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265)
            .setMaxVideoSize(1920, 1080)
        if (codecRetry >= 1) {
            b.setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
                .setMaxVideoSize(1280, 720)
                .setMaxVideoFrameRate(30)
                .setExceedVideoConstraintsIfNecessary(true)
        }
        sel.setParameters(b)
    }

    private fun isVideoCodecError(error: PlaybackException): Boolean {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
            else -> (error.message ?: "").contains("MediaCodecVideoRenderer", ignoreCase = true)
        }
    }

    /** Recreate Exo after MediaCodec 4003 so the decoder is not left in an error state. */
    private fun retrySaferCodec(session: XploreDashSession) {
        val old = player
        player = null
        trackSelector = null
        try {
            old?.stop()
        } catch (_: Exception) {}
        try {
            old?.release()
        } catch (_: Exception) {}
        attachPlayerSafe(session)
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
        val live = isLiveStream()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (host.channelPad.confirmOrCancel()) return true
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
                if (live) zapChannel(-1, event.repeatCount) else seekBy(-10_000L)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (live) zapChannel(1, event.repeatCount) else seekBy(10_000L)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (live) zapChannel(1, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (live) zapChannel(-1, event.repeatCount)
                true
            }
            else -> false
        }
    }

    fun isLiveStream(): Boolean {
        val s = lastSession
        if (s != null && (s.dashChannel.contains("ott", true) || s.mpdUrl.contains("__c/"))) return true
        return host.activeUrl().contains("/livetv", true)
    }

    fun tuneLiveChannel(oneBased: Int) {
        if (oneBased < 1) return
        host.lastCenterClickTime = System.currentTimeMillis()
        XploreDashCapture.markOk()
        if (isActive()) {
            main.removeCallbacks(holdWebView)
            main.postDelayed(holdWebView, 4000L)
            spinner?.visibility = View.VISIBLE
            setStatus("Program $oneBased")
        }
        SafeerDbg.log(
            "H340",
            "ExoPlayerSession.kt:tune",
            "tune live",
            JSONObject().put("n", oneBased).put("ch", playingChannel)
        )
        val js = "window._safeer_xplore_tune_channel && window._safeer_xplore_tune_channel($oneBased)"
        host.activeWebView()?.evaluateJavascript(js) { raw ->
            main.post {
                val name = parseZapName(raw)
                if (name.startsWith("!")) {
                    val have = name.drop(1)
                    setStatus("Ni programa $oneBased (spored $have)", 3000L)
                } else if (name.isNotEmpty()) {
                    setStatus("$oneBased | $name", 3500L)
                }
            }
        }
    }

    private fun zapChannel(delta: Int, repeatCount: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val minGap = if (repeatCount > 0) 420L else 240L
        if (now - lastZapAt < minGap) return true
        lastZapAt = now
        host.lastCenterClickTime = System.currentTimeMillis()
        XploreDashCapture.markOk()
        main.removeCallbacks(holdWebView)
        main.postDelayed(holdWebView, 4000L)
        spinner?.visibility = View.VISIBLE
        setStatus("Preklop …")
        SafeerDbg.log(
            "H339",
            "ExoPlayerSession.kt:zap",
            "live zap",
            JSONObject().put("d", delta).put("ch", playingChannel)
        )
        val js = "window._safeer_xplore_zap_channel && window._safeer_xplore_zap_channel($delta)"
        host.activeWebView()?.evaluateJavascript(js) { raw ->
            main.post {
                val name = parseZapName(raw)
                if (name.isNotEmpty()) setStatus(name, 3500L)
            }
        }
        return true
    }

    private fun parseZapName(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null" || raw == "0") return ""
        return raw.trim().trim('"').replace("\\n", " ").replace("\\\"", "\"").trim().take(40)
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            showOsd("⏸ Pavza", playingChannel.ifEmpty { null }, 2500L)
        } else {
            p.play()
            showOsd("▶ Predvajanje", playingChannel.ifEmpty { null }, 2500L)
        }
    }

    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        if (!p.isCurrentMediaItemSeekable) return
        val next = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(next)
        val sign = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        showOsd("⏩ $sign", playingChannel.ifEmpty { null }, 2000L)
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
        codecRetry = 0
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
        trackSelector = null
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
