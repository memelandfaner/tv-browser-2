package si.safeer.tv.link

import android.app.Activity
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import si.safeer.tv.cast.CastReceiverService
import si.safeer.tv.cast.HubDiscovery
import si.safeer.tv.cast.HubPairing
import org.json.JSONObject

/**
 * Most med stranjo Safeer Linka in televizorjem.
 *
 * Televizor je v Safeer Castu zaslon (prejemnik), ne posiljatelj: tu zato ni
 * posiljanja, ampak stanje povezave, seznanitev s kodo in sinhronizacija.
 *
 * Stran je del aplikacije (assets/link/) in most je pripet samo njenemu pogledu,
 * nikoli zavihku s spletno stranjo. Zeton naprave ostane v zasebnih nastavitvah.
 */
class LinkMost(
    private val dejavnost: Activity,
    private val pogled: WebView,
    private val trenutnaStran: () -> Pair<String, String?>,
    private val zapriZaslon: () -> Unit,
    private val odpriVBrskalniku: (String) -> Unit
) {

    companion object {
        private const val TAG = "SafeerLink"
        const val PREFS = "safeer_cast_prefs"
    }

    private fun nastavitve() = dejavnost.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hubUrl(): String = nastavitve().getString("hub_url", "") ?: ""

    private fun ime(): String =
        "tv-" + android.os.Build.MODEL.replace(Regex("\\s+"), "-").lowercase()

    private fun odziv(vrsta: String, podatki: Any) {
        val telo = when (podatki) {
            is JSONObject -> podatki.toString()
            is Boolean -> podatki.toString()
            else -> JSONObject.quote(podatki.toString())
        }
        pogled.post {
            try {
                pogled.evaluateJavascript(
                    "window.safeerLinkOdziv && window.safeerLinkOdziv(${JSONObject.quote(vrsta)}, $telo)",
                    null
                )
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Odziva ni bilo mogoce dostaviti: ${e.message}")
            }
        }
    }

    private fun napaka(sporocilo: String) = odziv("napaka", sporocilo)

    // ------------------------------------------------------------------

    @JavascriptInterface
    fun jeTelevizor(): Boolean = true

    /** Stanje sinhronizacije. Na televizorju je zaenkrat izklopljena. */
    @JavascriptInterface
    fun sinhronizacijaStanje(): String =
        "{\"zaznamki\":{\"vklopljena\":false,\"stevilo\":0,\"nadvoljo\":false}}"

    /** Na televizorju sinhronizacije zaznamkov (se) ni; povemo naravnost. */
    @JavascriptInterface
    fun nastaviSinhronizacijo(vklopljena: Boolean) {
        napaka("Sinhronizacija zaznamkov na televizorju še ni na voljo.")
    }


    /**
     * Jezik, ki ga ima uporabnik na napravi -- stran govori v njem.
     * Vrnemo samo dvocrkovno oznako; stran zna slovensko in anglesko.
     */
    @JavascriptInterface
    fun jezik(): String = try {
        val jeziki = dejavnost.resources.configuration.locales
        val prvi = if (jeziki.size() > 0) jeziki.get(0) else java.util.Locale.getDefault()
        (prvi.language ?: "").lowercase().take(2)
    } catch (e: Throwable) {
        ""
    }

    /**
     * Odklopi TO napravo od Safeer Linka: pozabi zeton in naslov.
     *
     * Namenoma ne posegamo v druge naprave -- to je odlocitev za napravo, ki jo ima
     * uporabnik v roki. Ostale se odstrani v Safeer Controlu.
     */
    @JavascriptInterface
    fun pozabiNapravo() {
        try {
            nastavitve().edit()
                .remove("control_token")
                .remove("hub_url")
                .remove("hub_ticket_path")
                .remove("hub_last_seen")
                .apply()
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Nastavitev ni bilo mogoce pocistiti: ${e.message}")
        }
        odziv("pozabljeno", true)
    }

    @JavascriptInterface
    fun stanje(): String {
        return try {
            JSONObject().apply {
                put("hub", hubUrl())
                put("znan", hubUrl().isNotBlank())
                put("seznanjen", HubPairing.token(dejavnost) != null)
                put("naprava", "Safeer TV (" + android.os.Build.MODEL + ")")
                put("id", ime())
                put("videnZadnjic", nastavitve().getLong("hub_last_seen", 0L))
            }.toString()
        } catch (e: Throwable) {
            "{\"znan\":false,\"seznanjen\":false}"
        }
    }

    @JavascriptInterface
    fun poisciHub() {
        try {
            HubDiscovery.discover(dejavnost) { naslov ->
                odziv("hub", JSONObject().apply {
                    put("najden", naslov != null)
                    put("naslov", naslov ?: "")
                })
            }
        } catch (e: Throwable) {
            napaka("Iskanja ni bilo mogoce zagnati: ${e.message}")
        }
    }

    @JavascriptInterface
    fun seznani() {
        val naslov = hubUrl()
        if (naslov.isBlank()) {
            napaka("Hub ni znan. Najprej ga poisci.")
            return
        }
        try {
            HubPairing.pair(
                dejavnost, naslov, ime(), "Safeer TV (" + android.os.Build.MODEL + ")",
                { koda -> odziv("koda", koda) },
                { uspelo ->
                    odziv("seznanitev", uspelo)
                    if (uspelo) {
                        // Sprejemnik lahko zdaj stece: televizor je od tu naprej dosegljiv.
                        try {
                            CastReceiverService.start(
                                dejavnost, null,
                                "Safeer TV (" + android.os.Build.MODEL + ")"
                            )
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (e: Throwable) {
            napaka("Seznanitve ni bilo mogoce zaceti: ${e.message}")
        }
    }

    /** Televizor ne odpira svoje povezave posiljatelja; pove le, ali sprejemnik tece. */
    @JavascriptInterface
    fun poveziSe() {
        odziv("povezava", CastReceiverService.instance != null)
    }

    /** Televizor ne vodi seznama naprav -- Hub ga javlja posiljateljem. */
    @JavascriptInterface
    fun naprave(): String = "[]"

    @JavascriptInterface
    fun trenutnaStranJson(): String {
        return try {
            val (url, naslov) = trenutnaStran()
            JSONObject().apply {
                put("url", url)
                put("naslov", naslov ?: "")
                put("posljiva", false)
            }.toString()
        } catch (e: Throwable) {
            "{\"url\":\"\",\"naslov\":\"\",\"posljiva\":false}"
        }
    }

    @JavascriptInterface
    fun posljiTrenutno(idNaprave: String) {
        napaka("Televizor je zaslon in ne posilja.")
    }

    @JavascriptInterface
    fun poslji(idNaprave: String, url: String, naslov: String) {
        napaka("Televizor je zaslon in ne posilja.")
    }

    @JavascriptInterface
    fun nadzor(idNaprave: String, ukaz: String, vrednost: Double) {
        napaka("Televizor je zaslon in ne upravlja drugih zaslonov.")
    }

    @JavascriptInterface
    fun zapri() {
        dejavnost.runOnUiThread { zapriZaslon() }
    }

    @JavascriptInterface
    fun odpri(url: String) {
        val cist = url.trim()
        if (!cist.startsWith("http://") && !cist.startsWith("https://")) return
        dejavnost.runOnUiThread {
            zapriZaslon()
            odpriVBrskalniku(cist)
        }
    }


    fun pospravi() {
        // Televizor tu nima odprte povezave, ki bi jo bilo treba zapreti.
    }
}
