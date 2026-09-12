package com.example.safeerbrowser

import android.content.Context
import com.safeer.threatfeed.PlainListSource
import com.safeer.threatfeed.ThreatListAgent
import java.io.File

/**
 * 🔄 ThreatFeedsUpdater – agent za sezname nevarnih strani (Feodo Tracker, URLhaus, Phishing Army).
 *
 * Ob vsakem zagonu brskalnika:
 *  1. takoj v ozadju (nizka prioriteta) naloži sezname, shranjene ob prejšnjem zagonu (preverjeni s SHA-256),
 *  2. približno 12 sekund po zagonu preveri nove sezname (pogojni prenos: nespremenjen seznam je ena majhna
 *     zahteva) in jih zamenja naenkrat, brez prekinitve brskanja,
 *  3. med delovanjem preverja vsakih 6 ur.
 * Prej so se seznami ob vsakem zagonu v celoti prenašali in sproti vstavljali v aktivno drevo.
 */
object ThreatFeedsUpdater {

    private val SOURCES = listOf(
        PlainListSource(
            id = "feodo", name = "abuse.ch Feodo Tracker", url = "https://feodotracker.abuse.ch/downloads/ipblocklist.txt",
            category = "Botnet C2 Server", marker = "feodo", minEntries = 0, ipv4 = true,
        ),
        PlainListSource(
            id = "urlhaus", name = "abuse.ch URLhaus", url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            category = "Zlonamerna koda (Malware)", marker = "urlhaus",
        ),
        PlainListSource(
            id = "phishing-army", name = "Phishing Army Extended",
            url = "https://phishing.army/download/phishing_army_blocklist_extended.txt",
            category = "Spletno ribarjenje (Phishing)", marker = "phishing",
        ),
        PlainListSource(
            id = "hagezi-tif", name = "HaGeZi Threat Intelligence Feeds (mini)",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/tif.mini-onlydomains.txt",
            category = "Nevarne strani (grožnje, ribarjenje, prevare)", marker = "hagezi",
        ),
        PlainListSource(
            id = "hagezi-fake", name = "HaGeZi Fake (lažne trgovine in prevare)",
            url = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/fake-onlydomains.txt",
            category = "Lažne trgovine in prevare", marker = "hagezi",
        ),
    )

    @Volatile
    private var agent: ThreatListAgent? = null

    @Volatile
    var ruleCount: Int = 0
        private set

    /** Zažene agenta (enkrat na proces). Vrne takoj; vse delo poteka v ozadju. */
    @Synchronized
    fun start(context: Context) {
        if (agent != null) return
        val listAgent = ThreatListAgent(File(context.applicationContext.filesDir, "threat-lists"), SOURCES) { lists ->
            ruleCount = ThreatBlockEngine.rebuildFromLists(lists)
        }
        agent = listAgent
        listAgent.start()
    }

    /** Takojšnje preverjanje (gumb "Posodobi sezname"); [onComplete] dobi število pravil v uporabi. */
    fun updateFeedsAsync(context: Context, onComplete: ((totalRules: Int) -> Unit)? = null) {
        start(context)
        val requested = agent?.requestUpdate { onComplete?.invoke(ruleCount) } ?: false
        if (!requested) onComplete?.invoke(ruleCount)
    }

    fun statusLine(): String {
        val lists = agent?.lists.orEmpty()
        if (lists.isEmpty()) return UiText.get(R.string.ui_lists_first_download)
        return UiText.get(R.string.ui_lists_status, lists.sumOf { it.entries.size }, lists.size, SOURCES.size)
    }
}
