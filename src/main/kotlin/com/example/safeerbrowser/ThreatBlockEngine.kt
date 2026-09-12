package com.example.safeerbrowser

import android.net.Uri
import android.webkit.WebResourceResponse
import com.safeer.threatfeed.BankGuard
import com.safeer.threatfeed.BankVerdict
import com.safeer.threatfeed.PlainList
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 🛡️ ThreatBlockEngine
 * Namensko jedro za brezkompromisno blokado Botnet C2 strežnikov, zlonamerne programske opreme (Malware),
 * ribarjenja (Phishing) in indikatorjev napadov (IOC).
 * 
 * 🔒 PRAVILO O NEDOTAKLJIVOSTI: Za te grožnje NE obstaja noben video ali embed bypass!
 */
object ThreatBlockEngine {

    var isEnabled: Boolean = true

    // Statistika blokiranih groženj
    val blockedC2Count = AtomicLong(0)
    val blockedMalwareCount = AtomicLong(0)
    val blockedPhishingCount = AtomicLong(0)
    val blockedIocCount = AtomicLong(0)
    val totalBlockedThreats = AtomicLong(0)

    // Hitri Domain Suffix Trie za grožnje (atomska zamenjava, ko agent naloži sezname)
    @Volatile
    private var threatTrie = DomainSuffixTrie()

    // Začasno odobrena spletna mesta (uporabnik je izrecno kliknil 'Nadaljuj na lastno odgovornost' za to sejo)
    private val sessionBypassedDomains = ConcurrentHashMap.newKeySet<String>()

    // Enokratni žetoni za potrditev obvoza: stran ne more sama sestaviti povezave za odklep.
    data class PendingBypass(val domain: String, val targetUrl: String, val expiryMs: Long)
    private val pendingBypasses = ConcurrentHashMap<String, PendingBypass>()

    /**
     * Dodatni preverjeni vir groženj: Safeer Threat Intelligence (Ed25519 podpisan seznam, glej
     * SignedThreatIntel.kt). Dopolnjuje vgrajeni seznam, nikoli ga ne nadomešča; napaka vira nikoli
     * ne prekine navigacije.
     */
    @Volatile
    var signedFeedMatcher: ((url: String, host: String) -> DomainSuffixTrie.MatchResult?)? = null

    private fun signedMatch(url: String, host: String): DomainSuffixTrie.MatchResult? {
        val matcher = signedFeedMatcher ?: return null
        return try {
            matcher(url, host)
        } catch (e: Exception) {
            null
        }
    }

    /** Vgrajeni seznam in podpisani vir; kritična kategorija (C2/malware) ima vedno prednost. */
    private fun findThreat(url: String, host: String): DomainSuffixTrie.MatchResult? {
        val local = threatTrie.findMatch(host)
        if (local != null && isCriticalThreat(local.category)) return local
        val signed = signedMatch(url, host) ?: return local
        return if (local == null || isCriticalThreat(signed.category)) signed else local
    }

    /** Kritične C2/malware grožnje nimajo obvoza (pravilo Zero-Bypass). */
    fun isCriticalThreat(category: String?): Boolean {
        if (category == null) return false
        val c = category.lowercase()
        return c.contains("c2") || c.contains("botnet") || c.contains("malware") || c.contains("zlonamerna")
    }

    fun createBypassToken(domain: String, targetUrl: String): String {
        val now = System.currentTimeMillis()
        pendingBypasses.entries.removeIf { it.value.expiryMs < now }
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        pendingBypasses[token] = PendingBypass(domain, targetUrl, now + 300_000L)
        return token
    }

    fun consumeBypassToken(token: String): PendingBypass? {
        val entry = pendingBypasses.remove(token) ?: return null
        if (System.currentTimeMillis() > entry.expiryMs) return null
        return entry
    }

    private fun htmlEscape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#x27;")

    // Dogodek ob blokadi
    var onThreatBlocked: ((domain: String, category: String, source: String, isMainFrame: Boolean) -> Unit)? = null

    init {
        loadSeedThreatDatabase(threatTrie)
    }

    // 🏦 Safeer BankGuard ------------------------------------------------------------------------

    /** Kategorija opozorila BankGuard: ni kritična, po opozorilu je mogoče nadaljevati. */
    const val FAKE_BANK_CATEGORY = "Fake bank (Phishing)"

    private val bankHostCache = object : LinkedHashMap<String, BankVerdict?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BankVerdict?>?) = size > 512
    }
    private val fakeBankOfficialDomains = ConcurrentHashMap<String, String>()

    /** Gostitelj, ki se s svojim imenom izdaja za banko (lokalno preverjanje, rezultat se hrani). */
    fun fakeBankHost(host: String): BankVerdict? {
        synchronized(bankHostCache) {
            if (bankHostCache.containsKey(host)) return bankHostCache[host]
        }
        val verdict = try { BankGuard.hostVerdict(host) } catch (e: Exception) { null }
        synchronized(bankHostCache) { bankHostCache[host] = verdict }
        return verdict
    }

    /** Uporabnik je po opozorilu izbral nadaljevanje za ta gostitelj (ta seja). */
    fun isAllowedForSession(host: String): Boolean = sessionBypassedDomains.contains(host.lowercase().trim().trimEnd('.'))

    /** Naslov zgodovine za opozorilo po naložitvi strani (prepozna vrnitev z "Nazaj"). */
    const val FAKE_BANK_HISTORY_URL = "safeer://security-interstitial/fake-bank"

    /** Prave banke (uradne domene, bančne skupine, plačilna in identitetna infrastruktura). */
    fun isRealBankHost(host: String): Boolean = try { BankGuard.isTrusted(host) } catch (e: Exception) { false }

    private fun fakeBankMatch(host: String, verdict: BankVerdict): DomainSuffixTrie.MatchResult {
        if (fakeBankOfficialDomains.size > 256) fakeBankOfficialDomains.clear()
        fakeBankOfficialDomains[host] = verdict.officialDomain
        return DomainSuffixTrie.MatchResult(
            isMatched = true,
            matchedDomain = host,
            category = FAKE_BANK_CATEGORY,
            sourceFeed = "Safeer Threat Shield · ${verdict.bankName} → ${verdict.officialDomain}",
        )
    }

    /** Naložena stran: [signalsJson] je rezultat BankGuard.PAGE_SCRIPT v glavnem oknu. */
    fun checkFakeBankPage(pageUrl: String, signalsJson: String?): DomainSuffixTrie.MatchResult? {
        if (!isEnabled) return null
        return try {
            val signals = BankGuard.signalsFromJson(signalsJson) ?: return null
            val pageHost = Uri.parse(pageUrl).host?.lowercase()?.trim()?.trimEnd('.') ?: return null
            val host = signals.host.lowercase().trim().trimEnd('.')
            if (host.isEmpty() || host != pageHost) return null // odgovor prejšnje strani
            if (sessionBypassedDomains.contains(host)) return null
            val verdict = BankGuard.pageVerdict(signals) ?: return null
            fakeBankMatch(host, verdict)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Zgradi novo drevo iz vgrajenega seznama in seznamov agenta ter ga atomsko zamenja.
     * Domene pravih bank se iz zunanjih seznamov prevzamejo samo kot potrjen C2/malware, nikoli kot ribarjenje.
     */
    fun rebuildFromLists(lists: List<PlainList>): Int {
        val newTrie = DomainSuffixTrie()
        loadSeedThreatDatabase(newTrie)
        var added = 0
        for (list in lists) {
            // Real bank domains are taken over only as confirmed C2/malware (a compromised server), never as phishing.
            val critical = isCriticalThreat(list.source.category)
            for (entry in list.entries) {
                if (!critical && isRealBankHost(entry)) continue
                newTrie.insert(entry, list.source.category, list.source.name)
                added++
            }
        }
        threatTrie = newTrie
        return added
    }

    /**
     * Vnaprej naložena semenska baza znanih nevarnih C2, malware in phishing domen.
     */
    private fun loadSeedThreatDatabase(threatTrie: DomainSuffixTrie) {
        // 1. abuse.ch Feodo Tracker (Botnet C2 strežniki - Dridex, Emotet, QakBot, TrickBot)
        val feodoC2 = listOf(
            "c2-tracker.net", "botnet-master.org", "dridex-panel.cc",
            "dridex-c2-botnet.ru", "emotet-feed.com", "emotet-loader.biz", "qakbot-gate.biz",
            "qakbot-drop.cc", "trickbot-c2.top", "icedid-network.cc", "icedid-c2-network.net",
            "bazarloader-c2.net", "cobaltstrike-beacon.info", "cobaltstrike-beacon.xyz", "lokibot-panel.ru",
            "redline-stealer.cc", "redline-stealer-gate.org", "vidar-c2.top", "vidar-c2-gate.net",
            "raccoon-gate.com", "raccoon-stealer.biz", "asyncrat-host.duckdns.org", "njrat-beacon.biz",
            "njrat-server.com", "remcos-c2.org", "remcos-panel.net", "agenttesla-gate.net",
            "agenttesla-c2.net", "formbook-panel.cc", "xworm-controller.top", "lumma-stealer.top",
            "meduza-stealer.cc"
        )
        for (d in feodoC2) threatTrie.insert(d, category = "Botnet C2 Server", sourceFeed = "abuse.ch Feodo Tracker")

        // 2. abuse.ch URLhaus & ThreatFox (Zlonamerna koda / Malware distribution & IOC)
        val urlhausMalware = listOf(
            "malware-drop.com", "payload-delivery.cc",
            "evil-apk-download.net", "stealer-gate.org", "cryptominer-pool.top", "ransomware-host.xyz",
            "dropper-server.ru", "trojan-source.cc", "apk-injector.top", "malicious-script.biz",
            "23vlcfp.cfd", "2lizguk.buzz", "x91kza.monster", "dl-android-update.top",
            "system-patch-android.click", "security-alert-center.top", "device-scan-security.cc",
            "update-system-firmware.top", "cleaner-update-android.xyz"
        )
        for (d in urlhausMalware) threatTrie.insert(d, category = "Zlonamerna koda (Malware)", sourceFeed = "abuse.ch URLhaus / ThreatFox")

        // 3. Phishing Army & Lažno predstavljanje (Kraja gesel in bančnih podatkov)
        val phishingDomains = listOf(
            "login-bank-verification.com", "secure-account-update.net",
            "verify-paypal-center.com", "apple-id-suspended.info", "google-account-recovery.top",
            "microsoft-auth-verify.cc", "nlb-klik-prijava.com", "nkbm-varnostni-pregled.net",
            "posta-slovenije-paket.top", "dhl-slovenia-slednje.cc", "si-pass-prijava.info"
        )
        for (d in phishingDomains) threatTrie.insert(d, category = "Spletno ribarjenje (Phishing)", sourceFeed = "Phishing Army")

        // 4. StevenBlack Malware & Agresivna stavniška omrežja z nevarno kodo
        val stevenBlackMalware = listOf(
            "20bet.top", "20bet-aff.com", "1xbet.mobi", "1xbet-partner.com", "vulkanvegas-play.top",
            "parimatch-aff.com", "monetag-loader.com", "richpush-ads.co", "onclickalgo.com",
            "syndication.exoclick.com"
        )
        for (d in stevenBlackMalware) threatTrie.insert(d, category = "Nevarno oglasno/stavno omrežje", sourceFeed = "StevenBlack Unified")
    }

    /**
     * Vstavi novo zaznano grožnjo v bazo.
     */
    fun addThreat(domain: String, category: String, sourceFeed: String) {
        threatTrie.insert(domain, category, sourceFeed)
    }

    /**
     * Preveri, ali URL ali gostitelj predstavlja varnostno grožnjo.
     * Vrne podrobnosti o grožnji ali null, če je domena varna.
     */
    fun checkThreat(url: String): DomainSuffixTrie.MatchResult? {
        if (!isEnabled || url.isEmpty()) return null

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase()?.trim() ?: return null
            if (host.isEmpty()) return null

            val match = findThreat(url, host)
            if (match == null) {
                // 🏦 BankGuard: naslov, ki posnema banko (npr. nlb-klik-prijava.com, otpbamka.si)
                if (sessionBypassedDomains.contains(host)) return null
                val verdict = fakeBankHost(host) ?: return null
                return fakeBankMatch(host, verdict)
            }

            // 🔒 ZERO-BYPASS: kritične C2 in malware grožnje nikoli nimajo izjeme
            if (isCriticalThreat(match.category)) return match

            // Prave banke nikoli ne dobijo opozorila o ribarjenju ali prevari (seznami se lahko zmotijo)
            if (isRealBankHost(host)) return null

            // Preveri začasne izjeme za to sejo (le za manj nevarne kategorije)
            if (sessionBypassedDomains.contains(host) || sessionBypassedDomains.contains(match.matchedDomain.lowercase())) {
                return null
            }
            return match
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Pomožna funkcija za hitro preverjanje, ali je URL grožnja.
     */
    fun isThreat(url: String): Boolean = checkThreat(url) != null

    /**
     * Odobri domeno za to sejo (uporabnik je izbral 'Nadaljuj na lastno odgovornost').
     */
    fun allowForSession(domain: String) {
        val clean = domain.trim().lowercase()
        if (clean.isEmpty()) return
        val isUrl = clean.contains("://")
        val host = if (isUrl) {
            (try { Uri.parse(clean).host } catch (e: Exception) { null })?.lowercase() ?: return
        } else {
            clean
        }
        val local = threatTrie.findMatch(host)
        val signed = signedMatch(if (isUrl) clean else "https://$clean/", host)
        if (listOfNotNull(local, signed).any { isCriticalThreat(it.category) }) {
            android.util.Log.w("SafeerSecurity", "Zero-Bypass: zavrnjen obvoz za kritično grožnjo '$clean'")
            return
        }
        sessionBypassedDomains.add(clean)
    }

    /**
     * Zabeleži statistiko blokade.
     */
    fun recordBlock(result: DomainSuffixTrie.MatchResult) {
        totalBlockedThreats.incrementAndGet()
        val cat = result.category?.lowercase() ?: ""
        when {
            cat.contains("c2") || cat.contains("botnet") -> blockedC2Count.incrementAndGet()
            cat.contains("malware") || cat.contains("zlonamerna") -> blockedMalwareCount.incrementAndGet()
            cat.contains("phishing") || cat.contains("ribarjenje") -> blockedPhishingCount.incrementAndGet()
            else -> blockedIocCount.incrementAndGet()
        }
    }

    /**
     * Ustvari privlačen AMOLED Red varnostni opozorilni zaslon (Security Interstitial Page) za glavno okno.
     */
    fun createSecurityInterstitialHtml(blockedUrl: String, match: DomainSuffixTrie.MatchResult, afterPageLoad: Boolean = false): String {
        // Opozorilo po naložitvi strani stoji za lažno stranjo v zgodovini: "Nazaj" preskoči obe.
        val backSteps = if (afterPageLoad) 2 else 1
        val isFakeBank = match.category == FAKE_BANK_CATEGORY
        val officialDomain = if (isFakeBank) fakeBankOfficialDomains[match.matchedDomain.lowercase()] else null
        val heading = if (isFakeBank) UiText.get(R.string.ui_fake_bank_title) else UiText.get(R.string.ui_threat_blocked)
        val description = if (isFakeBank) UiText.get(R.string.ui_fake_bank_desc) else UiText.get(R.string.ui_threat_desc)
        val officialButtonHtml = if (officialDomain != null) {
            val safeDomain = htmlEscape(officialDomain)
            "<a class=\"btn btn-primary\" style=\"background:#16a34a\" href=\"https://$safeDomain/\">${htmlEscape(UiText.get(R.string.ui_fake_bank_open_real, officialDomain))}</a>"
        } else ""
        val domain = htmlEscape(match.matchedDomain)
        val category = htmlEscape(if (isFakeBank) UiText.get(R.string.ui_fake_bank_type) else match.category ?: UiText.get(R.string.ui_security_threat))
        val source = htmlEscape(match.sourceFeed ?: UiText.get(R.string.ui_security_shield))
        val bypassActionHtml = if (isCriticalThreat(match.category)) {
            "<div class=\"footer-text\" style=\"color:#ff8888;font-size:13px\">🔒 ${htmlEscape(UiText.get(R.string.ui_zero_bypass))}</div>"
        } else {
            val token = createBypassToken(match.matchedDomain, blockedUrl)
            "<a class=\"btn btn-danger-outline\" href=\"safeer://bypass-threat?token=$token\">${UiText.get(R.string.ui_continue_risk)}</a>"
        }

        return """
        <!DOCTYPE html>
        <html lang="${UiText.language}">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>${UiText.get(R.string.ui_security_warning)}</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background-color: #050508;
                    color: #e5e5e5;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    min-height: 100vh;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                    padding: 24px;
                    text-align: center;
                }
                .card {
                    background: rgba(22, 10, 14, 0.85);
                    border: 1px solid rgba(255, 68, 68, 0.35);
                    border-radius: 20px;
                    padding: 32px 24px;
                    max-width: 480px;
                    width: 100%;
                    box-shadow: 0 10px 40px rgba(255, 0, 0, 0.25);
                    backdrop-filter: blur(12px);
                }
                .icon {
                    width: 72px;
                    height: 72px;
                    margin: 0 auto 20px;
                    background: rgba(255, 68, 68, 0.15);
                    border: 2px solid #ff4444;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 36px;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse {
                    0% { box-shadow: 0 0 0 0 rgba(255, 68, 68, 0.5); }
                    70% { box-shadow: 0 0 0 16px rgba(255, 68, 68, 0); }
                    100% { box-shadow: 0 0 0 0 rgba(255, 68, 68, 0); }
                }
                h1 {
                    font-size: 22px;
                    font-weight: 700;
                    color: #ff5555;
                    margin-bottom: 12px;
                }
                p.desc {
                    font-size: 15px;
                    color: #a0a0b0;
                    line-height: 1.5;
                    margin-bottom: 24px;
                }
                .badge-box {
                    background: rgba(0, 0, 0, 0.5);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 12px;
                    padding: 14px;
                    margin-bottom: 24px;
                    text-align: left;
                }
                .badge-row {
                    display: flex;
                    justify-content: space-between;
                    font-size: 13px;
                    margin-bottom: 6px;
                }
                .badge-row:last-child { margin-bottom: 0; }
                .badge-label { color: #888; }
                .badge-val { color: #fff; font-weight: 600; word-break: break-all; }
                .badge-danger { color: #ff5555; font-weight: 700; }
                
                .btn {
                    display: block;
                    width: 100%;
                    padding: 14px;
                    border-radius: 12px;
                    font-size: 15px;
                    font-weight: 600;
                    text-decoration: none;
                    cursor: pointer;
                    margin-bottom: 12px;
                    border: none;
                    transition: all 0.2s ease;
                }
                .btn-primary {
                    background: #2563eb;
                    color: #fff;
                    box-shadow: 0 4px 14px rgba(37, 99, 235, 0.4);
                }
                .btn-primary:active { background: #1d4ed8; transform: scale(0.98); }
                .btn-danger-outline {
                    background: transparent;
                    color: #888;
                    border: 1px solid rgba(255, 255, 255, 0.15);
                    font-size: 13px;
                    padding: 10px;
                }
                .btn-danger-outline:active { color: #ff5555; border-color: #ff5555; }
                .footer-text {
                    font-size: 12px;
                    color: #555;
                    margin-top: 16px;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="icon">🛑</div>
                <h1>${htmlEscape(heading)}</h1>
                <p class="desc">${htmlEscape(description)}</p>
                
                <div class="badge-box">
                    <div class="badge-row">
                        <span class="badge-label">${UiText.get(R.string.ui_domain)}</span>
                        <span class="badge-val">$domain</span>
                    </div>
                    <div class="badge-row">
                        <span class="badge-label">${UiText.get(R.string.ui_threat_type)}</span>
                        <span class="badge-danger">$category</span>
                    </div>
                    <div class="badge-row">
                        <span class="badge-label">${UiText.get(R.string.ui_security_source)}</span>
                        <span class="badge-val">$source</span>
                    </div>
                </div>

                <button class="btn btn-primary" onclick="if (history.length > $backSteps) { history.go(-$backSteps); } else { location.href = 'about:blank'; }">
                    ${UiText.get(R.string.ui_back_safe)}
                </button>

                $officialButtonHtml
                
                $bypassActionHtml

                <div class="footer-text">
                    Safeer Threat Shield • abuse.ch Feodo / URLhaus / ThreatFox • Phishing Army • HaGeZi
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Vrne WebResourceResponse z varnostnim opozorilom za glavno okno ali prazen odgovor za podvire.
     */
    fun handleThreatIntercept(url: String, isMainFrame: Boolean): WebResourceResponse? {
        val match = checkThreat(url) ?: return null
        recordBlock(match)
        onThreatBlocked?.invoke(match.matchedDomain, match.category ?: "Grožnja", match.sourceFeed ?: "Safeer Shield", isMainFrame)

        return if (isMainFrame) {
            val html = createSecurityInterstitialHtml(url, match)
            WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
        } else {
            // Podviri (skripte, slike, C2 beaconi) se tiho prekinejo z varnim praznim odgovorom
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }
}
