package si.safeer.tv.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Safeer Cast Receiver Service za Android TV (tv-browser-2).
 *
 * Deluje kot zanesljiva storitev v ozadju, ki vzdržuje WebSocket povezavo s Safeer Cast Hubom
 * ali sprejema neposredne ukaze za predvajanje URL-jev in medijskih tokov.
 */
class CastReceiverService : Service() {

    companion object {
        private const val TAG = "SafeerCastReceiver"
        private const val CHANNEL_ID = "safeer_cast_channel"
        private const val NOTIFICATION_ID = 4040

        const val ACTION_START = "si.safeer.tv.cast.START"
        const val ACTION_STOP = "si.safeer.tv.cast.STOP"
        const val EXTRA_HUB_URL = "extra_hub_url"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        /** Privzeto vozlišče: lahka različica na vedno vklopljeni napravi v domačem omrežju. */
        const val DEFAULT_HUB_URL = "ws://192.168.0.143:8990/cast/ws"
        const val PREFS_NAME = "safeer_cast_prefs"
        const val KEY_HUB_URL = "hub_url"

        const val ACTION_OPEN_CAST = "si.safeer.tv.cast.OPEN"
        const val EXTRA_CAST_URL = "cast_url"
        const val EXTRA_CAST_TITLE = "cast_title"
        const val EXTRA_CAST_POSITION = "cast_position"
        private const val WAKE_NOTIFICATION_ID = 4041
        private const val WAKE_CHANNEL_ID = "safeer_cast_wake"

        /** MainActivity javi, ali je v ospredju; ce ni, naslov odpremo z namero. */
        @Volatile
        var krmilnikVOspredju: Boolean = false

        // Callback vmesnik za povezavo z MainActivity / ChromiumEngineView
        var mediaController: CastMediaController? = null

        /** Tekoča storitev, da lahko aplikacija pošlje stanje predvajanja nazaj pošiljatelju. */
        @Volatile
        var instance: CastReceiverService? = null
            private set

        fun start(context: Context, hubUrl: String? = null, deviceName: String? = null) {
            val intent = Intent(context, CastReceiverService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HUB_URL, hubUrl)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    interface CastMediaController {
        fun onCastUrlReceived(url: String, title: String?, startPosition: Double)
        fun onCastControl(action: String, position: Double?, volume: Double?)
        fun getCurrentPlaybackState(): Map<String, Any?>
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hubUrl: String = DEFAULT_HUB_URL
    private var deviceId: String = "tv-" + Build.MODEL.replace("\\s+".toRegex(), "-").lowercase()
    private var deviceName: String = "Philips Android TV"
    private var isRunning = false
    private var reconnectAttempts = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Naslov vozlišča: 1. iz namere, 2. iz shranjenih nastavitev, 3. privzeti.
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fromIntent = intent?.getStringExtra(EXTRA_HUB_URL)
        hubUrl = when {
            !fromIntent.isNullOrBlank() -> fromIntent.also { prefs.edit().putString(KEY_HUB_URL, it).apply() }
            else -> prefs.getString(KEY_HUB_URL, DEFAULT_HUB_URL) ?: DEFAULT_HUB_URL
        }
        intent?.getStringExtra(EXTRA_DEVICE_NAME)?.let { if (it.isNotBlank()) deviceName = it }
        instance = this

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        isRunning = true
        connectToHub()

        return START_STICKY
    }

    private fun connectToHub() {
        if (!isRunning) return

        Log.i(TAG, "Povezujem se na Safeer Cast Hub: $hubUrl (naprava: $deviceId)")
        val request = Request.Builder().url(hubUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Uspešno povezan s Cast Hubom!")
                reconnectAttempts = 0

                // 1. Registracija naprave kot Receiver
                val registerMsg = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("type", "cast.register")
                    put("payload", JSONObject().apply {
                        put("device_id", deviceId)
                        put("name", deviceName)
                        put("role", "receiver")
                        put("capabilities", org.json.JSONArray(listOf("url", "media", "control", "volume", "seek")))
                    })
                }
                ws.send(registerMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(ws, text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Povezava s hubom padla: ${t.message}. Poskus ponovne povezave...")
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Povezava zaprta ($code): $reason")
                scheduleReconnect()
            }
        })
    }

    /**
     * Naslov odpre v brskalniku tudi takrat, ko ta ni v ospredju. Android 10+ zagon dejavnosti
     * iz ozadja pogosto zavrne, zato poleg neposrednega poskusa objavimo se obvestilo s
     * celozaslonsko namero -- tega sistem odpre sam.
     */
    private fun odpriVBrskalniku(url: String, title: String?, startPos: Double) {
        val namera = Intent().apply {
            setClassName(packageName, "si.safeer.tv.MainActivity")
            action = ACTION_OPEN_CAST
            putExtra(EXTRA_CAST_URL, url)
            putExtra(EXTRA_CAST_TITLE, title ?: "")
            putExtra(EXTRA_CAST_POSITION, startPos)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Android 10+ zagon dejavnosti iz ozadja zavrne tiho, brez izjeme, zato tega poskusa
        // ne zapisujemo kot uspeh; pravi mehanizem je obvestilo s celozaslonsko namero spodaj.
        try {
            startActivity(namera)
        } catch (e: Exception) {
            Log.w(TAG, "Neposredni zagon brskalnika ni uspel: ${e.message}")
        }
        pripraviKanalZaPrebujanje()
        try {
            var zastavice = android.app.PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                zastavice = zastavice or android.app.PendingIntent.FLAG_IMMUTABLE
            }
            val cakajoca = android.app.PendingIntent.getActivity(
                this, WAKE_NOTIFICATION_ID, namera, zastavice)
            val gradnik = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, WAKE_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
            }
            val obvestilo = gradnik
                .setContentTitle("Safeer Cast")
                .setContentText(if (title.isNullOrBlank()) url else title)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(cakajoca)
                .setFullScreenIntent(cakajoca, true)
                .setAutoCancel(true)
                .build()
            val upravitelj = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            upravitelj.notify(WAKE_NOTIFICATION_ID, obvestilo)
            Log.i(TAG, "Objavljeno obvestilo s celozaslonsko namero za $url")
        } catch (e: Exception) {
            Log.w(TAG, "Obvestila s celozaslonsko namero ni bilo mogoce objaviti: ${e.message}")
        }
    }

    /**
     * Kanal za prebujanje mora biti IMPORTANCE_HIGH, sicer sistem celozaslonske namere ne odpre.
     * Kanal storitve v ospredju ostane tih, da med gledanjem ne moti.
     */
    private fun pripraviKanalZaPrebujanje() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val upravitelj = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (upravitelj.getNotificationChannel(WAKE_CHANNEL_ID) != null) return
            val kanal = android.app.NotificationChannel(
                WAKE_CHANNEL_ID,
                "Safeer Cast - prihajajoca vsebina",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            kanal.description = "Odpre brskalnik, ko telefon poslje povezavo na televizor."
            kanal.setShowBadge(false)
            upravitelj.createNotificationChannel(kanal)
        } catch (e: Exception) {
            Log.w(TAG, "Kanala za prebujanje ni bilo mogoce ustvariti: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning) return
        reconnectAttempts++
        val delayMs = (reconnectAttempts * 2000L).coerceAtMost(30000L)
        mainHandler.postDelayed({ connectToHub() }, delayMs)
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val msgId = json.optString("id", UUID.randomUUID().toString())
            val type = json.optString("type")

            when (type) {
                "cast.url" -> {
                    val payload = json.getJSONObject("payload")
                    val url = payload.getString("url")
                    val title = payload.optString("title", "")
                    val startPos = payload.optDouble("start_position", 0.0)

                    Log.i(TAG, "Prejet cast.url: $url ($title)")
                    mainHandler.post {
                        val krmilnik = mediaController
                        if (krmilnik != null && krmilnikVOspredju) {
                            krmilnik.onCastUrlReceived(url, title, startPos)
                        } else {
                            odpriVBrskalniku(url, title, startPos)
                        }
                    }
                    sendAck(ws, msgId, "accepted")
                }

                "cast.control" -> {
                    val payload = json.getJSONObject("payload")
                    val action = payload.getString("action")
                    val position = if (payload.has("position")) payload.getDouble("position") else null
                    val volume = if (payload.has("volume")) payload.getDouble("volume") else null

                    Log.i(TAG, "Prejet cast.control: $action (pos: $position, vol: $volume)")
                    mainHandler.post {
                        mediaController?.onCastControl(action, position, volume)
                    }
                    sendAck(ws, msgId, "accepted")
                }

                "cast.ping" -> {
                    val pong = JSONObject().apply {
                        put("id", msgId)
                        put("type", "cast.pong")
                    }
                    ws.send(pong.toString())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Napaka pri obdelavi vhodnega sporočila: ${e.message}", e)
        }
    }

    private fun sendAck(ws: WebSocket, refId: String, status: String, error: String? = null) {
        val ack = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "cast.ack")
            put("ref_id", refId)
            put("status", status)
            if (error != null) put("error", error)
        }
        ws.send(ack.toString())
    }

    fun broadcastStatus(state: String, currentUrl: String?, title: String?, position: Double, duration: Double) {
        val statusMsg = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "cast.status")
            put("device_id", deviceId)
            put("payload", JSONObject().apply {
                put("state", state)
                put("current_url", currentUrl)
                put("title", title)
                put("position", position)
                put("duration", duration)
            })
        }
        webSocket?.send(statusMsg.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        mainHandler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        mediaController = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safeer Cast Receiver",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sprejemanje lokalnih cast ukazov za TV predvajalnik"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Safeer Cast Receiver")
            .setContentText("Pripravljen na predvajanje iz telefona in računalnika")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
