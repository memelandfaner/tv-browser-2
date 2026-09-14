package si.safeer.tv

import android.net.Uri
import android.webkit.WebResourceResponse
import com.safeer.threatfeed.FilterListEngine
import com.safeer.threatfeed.FilterRequest
import com.safeer.threatfeed.FilterSet
import com.safeer.threatfeed.PlainList
import com.safeer.threatfeed.ResourceType
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * ⚡ AdBlockEngine
 * Visoko-zmogljivo jedro za blokiranje oglasov s pomočjo Domain Suffix Trie podatkovne strukture,
 * preverjanja poti (Path Rules) in preprečevanja popunder / in-page push oglasnih omrežij brez motenja normalne navigacije.
 */
object AdBlockEngine {

    var isEnabled: Boolean = true
    val blockedAdsCount = AtomicLong(0)

    var onAdBlocked: (() -> Unit)? = null

    // Suffix Trie za blokirane oglasne in stavniške domene (O(k) iskanje)
    private val blockedTrie = DomainSuffixTrie()

    // Suffix Trie za strogo preverjene varne domene (Bela lista)
    private val whitelistTrie = DomainSuffixTrie()

    // Vgrajena seznama hranimo tudi kot polji, da ju je mogoce preveriti v preizkusih.
    // POZOR: razglasitev mora stati PRED blokom init, sicer ju ta prepise s praznim
    // seznamom - polja se v Kotlinu izvedejo po vrstnem redu zapisa.
    private var _bela: List<String> = emptyList()
    private var _oglasne: List<String> = emptyList()

    /** Vgrajeni seznami, izpostavljeni za preizkuse higiene (podvojitve, odvecne poddomene). */
    fun vgrajenaBelaLista(): List<String> = _bela
    fun vgrajeneOglasneDomene(): List<String> = _oglasne

    // 📜 Pravila EasyList (agent za sezname jih prenese, FilterListEngine jih prevede); zamenjava je atomska
    @Volatile
    private var filterSet: FilterSet = FilterSet.EMPTY
    val filterRuleCount: Int get() = filterSet.size
    private val pageAllowances = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 32
    }

    /** Prevede surove sezname pravil (raw) iz agenta; kliče se v ozadju, ob vsakem zagonu in po posodobitvi. */
    fun installFilterLists(lists: List<PlainList>): Int {
        val rules = ArrayList<String>()
        for (list in lists) if (list.source.raw) rules.addAll(list.entries)
        val compiled = if (rules.isEmpty()) FilterSet.EMPTY else FilterListEngine.compile(rules)
        filterSet = compiled
        synchronized(pageAllowances) { pageAllowances.clear() }
        return compiled.size
    }

    private fun isPageAllowed(pageUrl: String): Boolean {
        val set = filterSet
        if (set.size == 0) return false
        synchronized(pageAllowances) { pageAllowances[pageUrl]?.let { return it } }
        val allowed = try { set.isPageAllowed(pageUrl) } catch (e: RuntimeException) { false }
        synchronized(pageAllowances) { pageAllowances[pageUrl] = allowed }
        return allowed
    }

    /** Odločitev pravil EasyList za en zahtevek: true = blokiraj. Prave banke in bela lista so že izločene prej. */
    fun filterListBlocks(url: String, pageUrl: String?, accept: String?, isMainFrame: Boolean): Boolean {
        val set = filterSet
        if (set.size == 0 || isMainFrame) return false
        val page = pageUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (page != null && isPageAllowed(page)) return false
        val type = ResourceType.guess(url, accept, false)
        return try { set.decide(FilterRequest(url, page, type))?.block == true } catch (e: RuntimeException) { false }
    }

    /** Gostitelj iz naslova; ob nerazumljivem naslovu prazen niz. */
    private fun gostiteljIz(lower: String): String =
        try { Uri.parse(lower).host?.lowercase()?.trim() ?: "" } catch (_: Exception) { "" }

    /** Bela lista, prave banke in Xplore veljajo tudi za pravila EasyList.
     *  Gostitelja sprejme ze razclenjenega, da ga ni treba razclenjevati dvakrat. */
    private fun jeZaupanjaVreden(lower: String, host: String): Boolean =
        host.isEmpty() || ThreatBlockEngine.isRealBankHost(host) ||
            whitelistTrie.matches(host) || isXploreRelated(host, lower)

    // Vzorci oglasnih, sledilnih in analitičnih poti (Path Rules)
    // Ne uporabljaj splošnih imen kot /watch.js — to pobije predvajalnike (Xplore TV).
    private val BLOCKED_PATH_PATTERNS = listOf(
        "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info",
        "/pcs/activeview", "/pagead/adview", "/pagead/interaction",
        "/ads.js", "/ad.js", "/adservice.", "/pixel.", "collect?v=",
        "/metrika", "/tag.js", "/monetag/", "/popunder",
        "disable-devtool", "devtools-detector"
    )

    private val TRUSTED_AD_PATHS = listOf(
        "/pagead/", "/api/stats/ads", "/get_midroll_info", "/pcs/activeview"
    )

    init {
        initializeWhitelist()
        initializeBlockedDomains()
    }

    private fun initializeWhitelist() {
        // Drevo ujame tudi vse poddomene, zato so tu samo korenske domene.
        // Vnosi kot m.youtube.com ali accounts.google.com so bili odvecni.
        val trusted = listOf(
            "google.com", "google.si", "gstatic.com", "googleapis.com", "googleusercontent.com",
            "recaptcha.net", "duckduckgo.com", "bing.com", "yahoo.com", "wikipedia.org", "wikimedia.org",
            "youtube.com", "googlevideo.com", "ytimg.com",
            "nlb.si", "nkbm.si", "skb.si", "dh.si", "intesa.si", "intesasanpaolobank.si",
            "sparkasse.si", "revolut.com", "n26.com", "delavska-hranilnica.si",
            "bks-bank.si", "unicreditbank.si", "lon.si", "gorenjska-banka.si",
            "rtvslo.si", "24ur.com", "siol.net", "github.com",
            "xploretv.si", "a1xploretv.si", "a1.si", "a1.net",
            "widevine.com", "drmtoday.com", "castlabs.com", "expressplay.com",
            "bitmovin.com", "theoplayer.com", "akamaihd.net", "akamaized.net",
            "themoviedb.org", "tmdb.org",
            "streamex.sh", "streamex.ws", "vidlink.pro", "vidsrc.me", "vidsrc.in", "vidsrc.pm",
            "vidsrc.net", "vidsrc.to", "vidsrc.xyz", "autoembed.co", "autoembed.cc", "multiembed.mov",
            "2embed.cc", "111movies.com", "hydrahd.ws", "ythd.org", "megacloud.tv", "rabbitstream.net",
            "dokicloud.one", "vizcloud.online", "filemoon.sx", "streamtape.com", "vidgod.me",
            "peach.stream", "cinemanos.com", "streamwish.to", "doodstream.com",
            "pornhub.com", "phncdn.com", "phncdn.net"
        )
        _bela = trusted
        for (d in trusted) whitelistTrie.insert(d)
    }

    private fun initializeBlockedDomains() {
        // Doslej je bil to en sam seznam, v katerem sta se pomesala dva razlicna namena.
        // Loceno je jasneje in laze vzdrzevati: prvi seznam je oglasno filtriranje, drugi
        // pa odlocitev o vsebini. Razlika ni kozmeticna - EasyList pokriva prvo, drugega
        // pa ne pozna in se za glavni okvir sploh ne uporabi, zato mora ostati pri nas.
        //
        // Vseh poddomen ni treba nasteti: drevo ujame tudi vse poddomene korenske domene.

        /** Oglasni, sledilni in podtikalni strezniki (popunder, in-page push). */
        val oglasniStrezniki = listOf(
            // Podtikanje oken in vsiljena obvestila - to je najbolj motece za uporabnika
            "popads.net", "popcash.net", "monetag.com", "adcash.com", "propellerads.com",
            "exoclick.com", "tsyndicate.com", "et-code.com", "ero-advertising.com", "clickadu.com",
            "adsterra.com", "adxad.com", "hilltopads.com", "hilltopads.net", "richpush.co",
            "pushground.com", "admaven.com", "rollerads.com", "juicyads.com", "realsrv.com",
            "onclickperformance.com", "onclickmega.com", "onclickgate.com", "onclickalgo.com",
            "doublepimp.com", "deloplen.com", "highperformancegate.com", "effectivegate.com",
            "pussing.com", "propu.sh", "whosamung.us", "bngpt.com",

            // Oglasni strezniki in sledilci
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adservice.google.si", "amazon-adsystem.com",
            "taboola.com", "outbrain.com", "criteo.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "smartadserver.com", "bidswitch.net", "casalemedia.com",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "clarity.ms",
            "adnxs.com", "creativecdn.com", "trafficstars.com",
            "trafficjunky.com", "trafficjunky.net", "traffichaus.com", "trafficfactory.biz",
            "mc.yandex.ru", "metrika.yandex.ru", "an.yandex.ru"
        )

        /** Vsebinska blokada: stavnice, igralnice in strani za odrasle.
         *  To ni oglasno filtriranje - tu gre za odlocitev, katere strani se sploh ne odprejo. */
        val blokiranaVsebina = listOf(
            "20bet.com", "1xbet.com", "betwinner.com", "melbet.com", "mostbet.com",
            "vulkanvegas.com", "parimatch.com", "ggbet.com", "betsson.com", "unibet.com",
            "bet365.com", "betway.com", "bwin.com", "campobet.com", "rabona.com", "fezbet.com",
            "librabet.com", "nomini.com", "wazamba.com", "sportaza.com", "greatwin.com",
            "casinia.com", "spinanga.com", "boomerang-casino.com", "pin-up.casino",
            "livejasmin.com", "bongacams.com", "chaturbate.com", "stripchat.com", "cam4.com"
        )

        val vse = oglasniStrezniki + blokiranaVsebina
        _oglasne = vse
        for (d in vse) blockedTrie.insert(d)
    }

    /**
     * Preveri, ali je domena na strogi beli listi.
     */
    fun isWhitelisted(host: String): Boolean {
        return whitelistTrie.matches(host)
    }

    private fun isXploreRelated(host: String, url: String): Boolean {
        return host.contains("xploretv") || host.contains("a1xploretv") ||
            host == "a1.si" || host.endsWith(".a1.si") ||
            host == "a1.net" || host.endsWith(".a1.net") ||
            host.contains("widevine") || host.contains("drmtoday") ||
            host.contains("castlabs") || host.contains("expressplay") ||
            url.contains("xploretv.si")
    }

    /**
     * Preveri, ali URL ustreza oglasu, sledilcu ali blokirani domeni.
     */
    fun shouldBlockUrl(url: String): Boolean {
        if (!isEnabled || url.isEmpty()) return false
        val lower = url.lowercase()
        return vgrajenoBlokira(lower, gostiteljIz(lower))
    }

    /** Odlocitev vgrajenih pravil za ze razclenjen naslov. */
    private fun vgrajenoBlokira(lower: String, host: String): Boolean {

        // 1. Devtools zaščita (disable-devtool.js vedno blokiraj)
        if (lower.contains("disable-devtool") || lower.contains("devtools-detector")) {
            return true
        }

        // YouTube oglasni video tokovi (ne originalni posnetek)
        if (lower.contains("googlevideo.com") && (
                lower.contains("&oad=") || lower.contains("?oad=") ||
                lower.contains("ctier=l") || lower.contains("/ad_break")
            )) {
            return true
        }

        // 2. Domene: bela lista PREJ, da predvajalnik (npr. Xplore /watch.js) ni izpraznjen
        run {
            if (host.isNotEmpty()) {
                // Prave banke in plačilna infrastruktura (katalog BankGuard) delujejo brez posegov
                if (ThreatBlockEngine.isRealBankHost(host)) return false
                if (blockedTrie.matches(host)) {
                    return true
                }

                if (whitelistTrie.matches(host) || isXploreRelated(host, lower)) {
                    for (p in TRUSTED_AD_PATHS) {
                        if (lower.contains(p)) return true
                    }
                    return false
                }
            }
        }

        // 3. Preverjanje poti samo za nezaupanja vredne gostitelje
        for (pattern in BLOCKED_PATH_PATTERNS) {
            if (lower.contains(pattern)) {
                return true
            }
        }

        // 4. 🎬 Video Media Guard: Dovoli veljavne video toke in segmente preverjenih medijskih strežnikov
        if (lower.contains(".m3u8") || lower.contains(".ts") || lower.contains("/hls/") || 
            lower.contains("/embed/") || lower.contains("googlevideo.com") ||
            lower.contains("youtube.com/youtubei") || lower.contains("youtube.com/s/player") ||
            lower.contains("youtube.com/tv") || lower.contains("xploretv.si") ||
            lower.contains("youtube.com/api/") || lower.contains("youtube.com/results") || lower.contains("ytimg.com") ||
            lower.contains("phncdn.com") || lower.contains("phncdn.net")) {
            // Če je specifičen oglasni strežnik, ga blokiraj
            if (lower.contains("googleads") || lower.contains("pagead") || lower.contains("adservice") ||
                lower.contains("doubleclick") || lower.contains("ad.youtube.com") || lower.contains("ads.youtube.com") ||
                lower.contains("trafficjunky") || lower.contains("tsyndicate")) {
                return true
            }
            return false
        }

        return false
    }

    /**
     * Prestrezanje oglasnih zahtevkov in vračanje veljavnih praznih odgovorov.
     */
    fun handleIntercept(url: String): WebResourceResponse? = handleIntercept(url, null, null, false)

    /** Prestrezanje z vsem, kar WebView o zahtevku ve: vgrajena pravila, nato pravila EasyList. */
    fun handleIntercept(url: String, pageUrl: String?, accept: String?, isMainFrame: Boolean): WebResourceResponse? {
        if (!isEnabled) return null
        val lower = url.lowercase()

        // Naslov razclenimo enkrat; oba sloja dobita isti rezultat.
        val host = gostiteljIz(lower)
        val vgrajeno = url.isNotEmpty() && vgrajenoBlokira(lower, host)
        val poSeznamih = !vgrajeno && !isMainFrame && !jeZaupanjaVreden(lower, host) &&
            filterListBlocks(url, pageUrl, accept, isMainFrame)

        if (vgrajeno || poSeznamih) {
            blockedAdsCount.incrementAndGet()
            android.util.Log.d("SafeerAdBlock", "blokirano: ${url.take(120)}")
            onAdBlocked?.invoke()

            val isJson = lower.endsWith(".json") || lower.contains("json") ||
                         lower.contains("/pagead/") || lower.contains("/api/stats/ads") ||
                         lower.contains("get_midroll_info")

            val mime = when {
                isJson -> "application/json"
                lower.endsWith(".js") -> "application/javascript"
                lower.endsWith(".css") -> "text/css"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".gif") -> "image/gif"
                lower.endsWith(".svg") -> "image/svg+xml"
                lower.endsWith(".html") -> "text/html"
                else -> "text/plain"
            }

            val contentBytes = if (isJson) {
                "{\"adPlacements\":[],\"status\":\"ok\"}".toByteArray(Charsets.UTF_8)
            } else {
                ByteArray(0)
            }

            return WebResourceResponse(
                mime,
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                ByteArrayInputStream(contentBytes)
            )
        }

        return null
    }
}
