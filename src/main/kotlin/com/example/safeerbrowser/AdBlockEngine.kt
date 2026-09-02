package com.example.safeerbrowser

import android.net.Uri
import android.webkit.WebResourceResponse
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
        val trusted = listOf(
            "google.com", "google.si", "gstatic.com", "googleapis.com", "googleusercontent.com",
            "duckduckgo.com", "bing.com", "yahoo.com", "wikipedia.org", "wikimedia.org",
            "youtube.com", "m.youtube.com", "music.youtube.com", "googlevideo.com", "ytimg.com",
            "accounts.youtube.com", "accounts.google.com", "myaccount.google.com",
            "nlb.si", "nkbm.si", "skb.si", "dh.si", "intesa.si", "intesasanpaolobank.si",
            "sparkasse.si", "revolut.com", "n26.com", "delavska-hranilnica.si",
            "bks-bank.si", "unicreditbank.si", "lon.si", "gorenjska-banka.si",
            "rtvslo.si", "24ur.com", "siol.net", "github.com",
            "xploretv.si", "www.xploretv.si", "a1xploretv.si", "a1.si", "a1.net",
            "cdn23.a1.net", "widevine.com", "drmtoday.com", "castlabs.com", "expressplay.com",
            "bitmovin.com", "bitmovin-a.akamaihd.net", "theoplayer.com",
            "akamaihd.net", "akamaized.net",
            "themoviedb.org", "tmdb.org", "image.tmdb.org", "api.themoviedb.org",
            "streamex.sh", "streamex.ws", "vidlink.pro", "vidsrc.me", "vidsrc.in", "vidsrc.pm",
            "vidsrc.net", "vidsrc.to", "vidsrc.xyz", "autoembed.co", "autoembed.cc", "multiembed.mov",
            "2embed.cc", "111movies.com", "hydrahd.ws", "ythd.org", "megacloud.tv", "rabbitstream.net",
            "dokicloud.one", "vizcloud.online", "filemoon.sx", "streamtape.com", "vidgod.me",
            "peach.stream", "cinemanos.com", "core.streamex.sh", "streamwish.to", "doodstream.com",
            "pornhub.com", "phncdn.com", "phncdn.net"
        )
        for (d in trusted) whitelistTrie.insert(d)
    }

    private fun initializeBlockedDomains() {
        val adsAndGambling = listOf(
            // Stavniške & Casino platforme
            "20bet.com", "20bet.top", "20bet-aff.com", "1xbet.com", "1xbet.mobi", "1xbet-partner.com",
            "betwinner.com", "melbet.com", "mostbet.com", "vulkanvegas.com", "parimatch.com", "ggbet.com",
            "betsson.com", "unibet.com", "bet365.com", "betway.com", "bwin.com", "campobet.com",
            "rabona.com", "fezbet.com", "librabet.com", "nomini.com", "wazamba.com", "sportaza.com",
            "greatwin.com", "casinia.com", "spinanga.com", "boomerang-casino.com", "pin-up.casino",

            // Popunderji, In-Page Push & Agresivna oglasna omrežja (vključno z video preroll & bannerji)
            "popads.net", "popcash.net", "monetag.com", "adcash.com", "propellerads.com",
            "exoclick.com", "trafficjunky.com", "trafficjunky.net", "ads.trafficjunky.net", "delivery.trafficjunky.net",
            "tsyndicate.com", "et-code.com", "ero-advertising.com", "clickadu.com", "adsterra.com", "adxad.com",
            "hilltopads.com", "hilltopads.net", "richpush.co", "pushground.com", "admaven.com", "rollerads.com",
            "juicyads.com", "trafficfactory.biz", "realsrv.com", "onclickalgo.com", "onclickperformance.com",
            "onclickmega.com", "onclickgate.com", "syndication.exoclick.com", "syndication.realsrv.com",
            "doublepimp.com", "deloplen.com", "highperformancegate.com", "effectivegate.com", "pussing.com",
            "propu.sh", "creativecdn.com", "whosamung.us", "traffichaus.com", "bngpt.com", "adnxs.com",
            "trafficstars.com", "livejasmin.com", "bongacams.com", "chaturbate.com", "stripchat.com", "cam4.com",

            // Oglasni strežniki in sledilci
            "doubleclick.net", "googleads.g.doubleclick.net", "static.doubleclick.net",
            "googlesyndication.com", "pagead2.googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adservice.google.si", "amazon-adsystem.com",
            "taboola.com", "outbrain.com", "criteo.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "smartadserver.com", "bidswitch.net", "casalemedia.com",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "clarity.ms",
            "mc.yandex.ru", "metrika.yandex.ru", "an.yandex.ru"
        )
        for (d in adsAndGambling) blockedTrie.insert(d)
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
        try {
            val uri = Uri.parse(lower)
            val host = uri.host?.lowercase()?.trim() ?: ""

            if (host.isNotEmpty()) {
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
        } catch (_: Exception) {}

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
    fun handleIntercept(url: String): WebResourceResponse? {
        if (!isEnabled) return null
        val lower = url.lowercase()

        if (shouldBlockUrl(url)) {
            blockedAdsCount.incrementAndGet()
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
