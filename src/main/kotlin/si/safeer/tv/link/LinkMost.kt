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
            // Seznam mora priti do strani kot seznam; ce ga zavijemo v narekovaje,
            // stran dobi besedilo in odziv tiho odpade.
            is org.json.JSONArray -> podatki.toString()
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

    /**
     * Napaka za stran. [koda] je stabilna oznaka, ki jo stran prevede v jezik naprave;
     * [sporocilo] ostane zraven kot rezerva in za diagnostiko, ce kode ne pozna.
     */
    private fun napaka(koda: String, sporocilo: String) = odziv(
        "napaka",
        JSONObject().put("koda", koda).put("sporocilo", sporocilo)
    )

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
        napaka(
            "sync_tv_ni_na_voljo",
            si.safeer.tv.UiText.get(si.safeer.tv.R.string.ui_link_sync_unavailable)
                .ifBlank { "Bookmark sync is not available on the television yet." }
        )
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
            napaka("iskanje_ni_steklo", "Iskanja ni bilo mogoce zagnati: ${e.message}")
        }
    }

    @JavascriptInterface
    fun seznani() {
        val naslov = hubUrl()
        if (naslov.isBlank()) {
            napaka("hub_ni_znan", "Hub ni znan. Najprej ga poisci.")
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
            napaka("seznanitev_ni_stekla", "Seznanitve ni bilo mogoce zaceti: ${e.message}")
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
        napaka("tv_je_zaslon", "Televizor je zaslon in ne posilja.")
    }

    @JavascriptInterface
    fun poslji(idNaprave: String, url: String, naslov: String) {
        napaka("tv_je_zaslon", "Televizor je zaslon in ne posilja.")
    }

    @JavascriptInterface
    fun nadzor(idNaprave: String, ukaz: String, vrednost: Double) {
        napaka("tv_ne_upravlja", "Televizor je zaslon in ne upravlja drugih zaslonov.")
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


    // ------------------------------------------------------------------ Hub na televizorju

    /**
     * Stanje Huba, ki ga gosti ta televizor.
     *
     * Ob vsakem pogledu na stran tudi povemo usmerjevalniku, kam naj javi spremembe -
     * tako se cakajoca prijava pokaze sama od sebe in uporabniku ni treba osvezevati.
     */
    @JavascriptInterface
    fun hubStanje(): String {
        pripniPoslusalce()
        return try {
            si.safeer.tv.cast.HubKrmilnik.stanjeJson(dejavnost)
        } catch (e: Throwable) {
            "{\"tece\":false,\"zazelen\":false}"
        }
    }

    @JavascriptInterface
    fun hubVklopi() {
        try {
            val uspelo = si.safeer.tv.cast.HubKrmilnik.zazeni(dejavnost)
            pripniPoslusalce()
            if (!uspelo) napaka("hub_ni_zagnan", "Huba ni bilo mogoce zagnati.")
            odziv("hub-tu", JSONObject(hubStanje()))
        } catch (e: Throwable) {
            napaka("hub_ni_zagnan", "Huba ni bilo mogoce zagnati: ${e.message}")
        }
    }

    @JavascriptInterface
    fun hubIzklopi() {
        try {
            si.safeer.tv.cast.HubKrmilnik.ustavi(dejavnost)
            odziv("hub-tu", JSONObject(hubStanje()))
        } catch (e: Throwable) {
            napaka("hub_ni_ustavljen", "Huba ni bilo mogoce ustaviti: ${e.message}")
        }
    }

    /** Naprave, ki cakajo na potrditev: ime in sestmestna koda, ki jo naprava kaze na zaslonu. */
    @JavascriptInterface
    fun hubPrijave(): String = try {
        val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik
        org.json.JSONArray().apply {
            u?.cakajocePrijave()?.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.pairId)
                    put("ime", p.ime)
                    put("koda", p.pin)
                    put("naslov", p.naslov)
                    put("starost", p.starostSekund)
                })
            }
        }.toString()
    } catch (e: Throwable) {
        "[]"
    }

    /** Uporabnik je na televizorju pritisnil V redu. Nicesar ni treba vtipkati. */
    @JavascriptInterface
    fun hubPotrdi(idPrijave: String) {
        val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik
        if (u == null) {
            napaka("hub_ne_tece", "Hub ne tece.")
            return
        }
        if (!u.potrdiPrijavo(idPrijave)) {
            napaka("prijava_potekla", "Prijave ni vec ali pa je poteklo.")
        }
        odziv("hub-prijave", org.json.JSONArray(hubPrijave()))
    }

    @JavascriptInterface
    fun hubZavrni(idPrijave: String) {
        si.safeer.tv.cast.HubKrmilnik.usmerjevalnik?.zavrniPrijavo(idPrijave)
        odziv("hub-prijave", org.json.JSONArray(hubPrijave()))
    }

    /** Naprave, ki jim je uporabnik ze dovolil. */
    @JavascriptInterface
    fun hubSeznanjene(): String = try {
        val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik
        org.json.JSONArray().apply {
            u?.seznanjeneNaprave()?.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.deviceId)
                    put("ime", n.ime)
                    put("od", n.seznanjenaOb)
                })
            }
        }.toString()
    } catch (e: Throwable) {
        "[]"
    }

    /** Odvzame dostop napravi in jo, ce je povezana, odklopi. */
    @JavascriptInterface
    fun hubPreklici(idNaprave: String) {
        val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik
        if (u == null) {
            napaka("hub_ne_tece", "Hub ne tece.")
            return
        }
        u.prekliciNapravo(idNaprave)
        odziv("hub-seznanjene", org.json.JSONArray(hubSeznanjene()))
    }

    /**
     * Usmerjevalnik javi spremembe strani, da se nova prijava pokaze takoj.
     * Poslusalca pripnemo vedno na novo, ker se Hub lahko vmes ugasne in prizge.
     */
    private fun pripniPoslusalce() {
        val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik ?: return
        u.naSpremembePrijav = { odziv("hub-prijave", org.json.JSONArray(hubPrijave())) }
        u.naSpremembeNaprav = { odziv("hub-tu", JSONObject(si.safeer.tv.cast.HubKrmilnik.stanjeJson(dejavnost))) }
    }


    fun pospravi() {
        // Televizor tu nima odprte povezave, ki bi jo bilo treba zapreti.
        // Hub pa namenoma tece naprej: televizor je zaslon, ki naj bo dosegljiv tudi
        // takrat, ko uporabnik zapre ta zaslon in gleda. Ugasne ga uporabnik sam ali
        // konec brskalnika. Odklopimo samo poslusalca, da stran ne ostane v pomnilniku.
        try {
            val u = si.safeer.tv.cast.HubKrmilnik.usmerjevalnik
            u?.naSpremembePrijav = null
            u?.naSpremembeNaprav = null
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Poslusalcev ni bilo mogoce odkljuciti: ${e.message}")
        }
    }
}
