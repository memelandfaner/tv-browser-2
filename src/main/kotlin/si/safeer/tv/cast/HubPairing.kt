package si.safeer.tv.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Seznanitev naprave s Safeer Hubom.
 *
 * Naprava se prijavi, dobi šestmestno kodo in jo pokaže na svojem zaslonu. Uporabnik isto kodo
 * vidi v Safeer Controlu in prijavo potrdi; šele nato naprava dobi svoj žeton. Tipkati ni
 * treba ničesar — kar je pri televizorju z daljincem edino znosno.
 *
 * Žeton naprave odpira samo Cast in Sync, ne celotnega Controla.
 */
object HubPairing {

    private const val TAG = "SafeerHubPairing"
    const val PREFS_NAME = "safeer_cast_prefs"
    const val KEY_CONTROL_TOKEN = "control_token"
    private const val POLL_MS = 3000L
    private const val MAX_WAIT_MS = 300_000L   // pet minut, kolikor velja koda

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val glavna = Handler(Looper.getMainLooper())
    @Volatile
    private var tece = false

    /** Osnovni naslov Huba (http://...) iz naslova WebSocketa. */
    fun httpBase(wsUrl: String): String =
        wsUrl.replace(Regex("^wss"), "https").replace(Regex("^ws"), "http")
            .substringBefore("/cast/ws").substringBefore("/link/ws").substringBefore("/safeer/ws")
            .trimEnd('/')

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONTROL_TOKEN, null)

    /**
     * Začne seznanitev. `pokaziKodo` dobi kodo, ki naj se prikaže uporabniku; `koncano` dobi
     * true, ko je naprava seznanjena, ali false, če je poteklo oziroma ni šlo.
     */
    fun pair(context: Context, wsUrl: String, deviceId: String, deviceName: String,
             pokaziKodo: (String) -> Unit, koncano: (Boolean) -> Unit) {
        if (tece) {
            Log.i(TAG, "Seznanitev ze tece.")
            return
        }
        val app = context.applicationContext
        val osnova = httpBase(wsUrl)
        tece = true

        val telo = JSONObject().apply {
            put("device_id", deviceId)
            put("name", deviceName)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        client.newCall(Request.Builder().url("$osnova/cast/pair/start").post(telo).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Prijave ni bilo mogoce zaceti: ${e.message}")
                    tece = false
                    glavna.post { koncano(false) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val odgovor = response.use { it.body?.string().orEmpty() to it.isSuccessful }
                    if (!odgovor.second) {
                        Log.w(TAG, "Hub je prijavo zavrnil.")
                        tece = false
                        glavna.post { koncano(false) }
                        return
                    }
                    val json = try { JSONObject(odgovor.first) } catch (e: Exception) { null }
                    val pairId = json?.optString("pair_id").orEmpty()
                    val koda = json?.optString("pin").orEmpty()
                    if (pairId.isBlank() || koda.isBlank()) {
                        tece = false
                        glavna.post { koncano(false) }
                        return
                    }
                    Log.i(TAG, "Koda za seznanitev prikazana; cakam na potrditev.")
                    glavna.post { pokaziKodo(koda) }
                    pocakajNaPotrditev(app, osnova, pairId, System.currentTimeMillis(), koncano)
                }
            })
    }

    private fun pocakajNaPotrditev(context: Context, osnova: String, pairId: String,
                                   zacetek: Long, koncano: (Boolean) -> Unit) {
        if (System.currentTimeMillis() - zacetek > MAX_WAIT_MS) {
            Log.i(TAG, "Potrditev ni prisla v petih minutah; neham.")
            tece = false
            glavna.post { koncano(false) }
            return
        }
        val telo = JSONObject().apply { put("pair_id", pairId) }
            .toString().toRequestBody("application/json".toMediaTypeOrNull())
        client.newCall(Request.Builder().url("$osnova/cast/pair/claim").post(telo).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    glavna.postDelayed({ pocakajNaPotrditev(context, osnova, pairId, zacetek, koncano) }, POLL_MS)
                }

                override fun onResponse(call: Call, response: Response) {
                    val besedilo = response.use { it.body?.string().orEmpty() }
                    val json = try { JSONObject(besedilo) } catch (e: Exception) { null }
                    val zeton = if (json?.optBoolean("approved") == true) json.optString("token") else ""
                    if (zeton.isNullOrBlank()) {
                        glavna.postDelayed({ pocakajNaPotrditev(context, osnova, pairId, zacetek, koncano) }, POLL_MS)
                        return
                    }
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString(KEY_CONTROL_TOKEN, zeton).apply()
                    Log.i(TAG, "Naprava je seznanjena s Safeer Hubom.")
                    tece = false
                    glavna.post { koncano(true) }
                }
            })
    }
}
