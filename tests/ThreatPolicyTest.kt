package si.safeer.tv

import com.safeer.threatfeed.FeedMatch
import com.safeer.threatfeed.FeedSource
import com.safeer.threatfeed.PlainList
import com.safeer.threatfeed.PlainListSource

// JVM checks for the threat shield (run: tests/run_threat_policy_tests.sh). Android classes come from tests/stubs.
fun main() {
    check(!ThreatBlockEngine.isThreat("https://www.bbc.com/news"))
    check(!ThreatBlockEngine.isThreat("https://urlhaus.abuse.ch/browse/")) { "feed providers are not threats" }
    check(!ThreatBlockEngine.isThreat("https://phishing.army/")) { "feed providers are not threats" }

    // Built-in list: zero-bypass for critical categories, single-use tokens for the rest.
    ThreatBlockEngine.addThreat("compromised.fastly.net", "Zlonamerna koda (Malware)", "test fixture")
    check(ThreatBlockEngine.isThreat("https://compromised.fastly.net/file"))
    ThreatBlockEngine.allowForSession("compromised.fastly.net")
    check(ThreatBlockEngine.isThreat("https://compromised.fastly.net/file")) { "malware has no bypass" }
    check(!ThreatBlockEngine.isThreat("https://compromised.fastly.net.example.org/file")) { "domain boundary" }
    ThreatBlockEngine.addThreat("phish.fixture.test", "Spletno ribarjenje (Phishing)", "test fixture")
    val phishing = ThreatBlockEngine.checkThreat("https://login.phish.fixture.test/") ?: error("phishing must match")
    val html = ThreatBlockEngine.createSecurityInterstitialHtml("https://login.phish.fixture.test/", phishing)
    val token = Regex("safeer://bypass-threat\\?token=([0-9a-f]{32})").find(html)?.groupValues?.get(1) ?: error("token link missing")
    check(!html.contains("domain=")) { "no forgeable bypass link" }
    val bypass = ThreatBlockEngine.consumeBypassToken(token) ?: error("token must be valid once")
    check(ThreatBlockEngine.consumeBypassToken(token) == null) { "token is single-use" }
    ThreatBlockEngine.allowForSession(bypass.domain)
    check(!ThreatBlockEngine.isThreat("https://login.phish.fixture.test/")) { "explicit bypass covers the matched domain" }
    val malware = ThreatBlockEngine.checkThreat("https://compromised.fastly.net/") ?: error("malware must match")
    val criticalHtml = ThreatBlockEngine.createSecurityInterstitialHtml("https://compromised.fastly.net/", malware)
    check(!criticalHtml.contains("bypass-threat") && criticalHtml.contains("ui_zero_bypass")) { "critical page offers no bypass" }
    println("PASS: built-in list, zero-bypass, single-use tokens, provider sites not blocked")

    // Signed Safeer Threat Intelligence layer.
    val source = FeedSource("fixture", "CC0-1.0", "Fixture <b>source</b>", "https://safeer.si/")
    fun signed(indicator: String, category: String) = SignedThreatIntel.toMatchResult(FeedMatch(indicator, "hostname", category, source))
    check(ThreatBlockEngine.isCriticalThreat(signed("x.test", "botnet_c2")?.category))
    check(ThreatBlockEngine.isCriticalThreat(signed("x.test", "malware")?.category))
    check(!ThreatBlockEngine.isCriticalThreat(signed("x.test", "phishing")?.category))
    check(!ThreatBlockEngine.isCriticalThreat(signed("x.test", "scam")?.category))
    check(signed("x.test", "ads") == null && signed("x.test", "tracker") == null) { "ads are not security threats" }
    ThreatBlockEngine.signedFeedMatcher = { url, host ->
        when {
            host == "c2.signed.test" -> signed(host, "botnet_c2")
            host.endsWith("scam.signed.test") -> signed("scam.signed.test", "scam")
            url.startsWith("https://files.signed.test/payload") -> SignedThreatIntel.toMatchResult(
                FeedMatch("https://files.signed.test/payload?<script>", "url", "malware", source))
            host == "broken.signed.test" -> throw IllegalStateException("matcher failure")
            else -> null
        }
    }
    check(ThreatBlockEngine.isThreat("https://c2.signed.test/beacon"))
    ThreatBlockEngine.allowForSession("c2.signed.test")
    check(ThreatBlockEngine.isThreat("https://c2.signed.test/beacon")) { "signed C2 has no bypass" }
    check(!ThreatBlockEngine.isThreat("https://broken.signed.test/")) { "matcher errors never block or crash" }
    val payload = ThreatBlockEngine.checkThreat("https://files.signed.test/payload") ?: error("signed URL must match")
    val payloadHtml = ThreatBlockEngine.createSecurityInterstitialHtml("https://files.signed.test/payload", payload)
    check(!payloadHtml.contains("<script>") && payloadHtml.contains("&lt;script&gt;") && !payloadHtml.contains("<b>source</b>")) { "escaped" }
    check(ThreatBlockEngine.isThreat("https://shop.scam.signed.test/"))
    ThreatBlockEngine.allowForSession("scam.signed.test")
    check(!ThreatBlockEngine.isThreat("https://shop.scam.signed.test/"))
    ThreatBlockEngine.addThreat("mixed.signed.test", "Spletno ribarjenje (Phishing)", "seed")
    ThreatBlockEngine.signedFeedMatcher = { _, host -> if (host == "mixed.signed.test") signed(host, "malware") else null }
    check(ThreatBlockEngine.checkThreat("https://mixed.signed.test/")?.category == "Zlonamerna koda (Malware)") { "critical signed match wins" }
    ThreatBlockEngine.signedFeedMatcher = null
    check(SignedThreatIntel.statusLine() == if (SignedThreatIntel.isEnabled) "ui_signed_intel_not_downloaded" else "ui_signed_intel_waiting")
    check(SignedThreatIntel.TRUSTED_KEYS.values.all { java.util.Base64.getDecoder().decode(it).size == 32 }) { "trusted keys are 32-byte Ed25519 keys" }
    println("PASS: signed feed layer, zero-bypass for signed C2/malware, escaping, matcher failures ignored")

    // 🏦 BankGuard
    val fake = ThreatBlockEngine.FAKE_BANK_CATEGORY
    check(ThreatBlockEngine.checkThreat("https://nlb-klik-varnost.net/prijava")?.category == fake) { "bank lookalike host" }
    check(ThreatBlockEngine.checkThreat("https://otpbamka.si/")?.category == fake) { "bank typosquat host" }
    check(!ThreatBlockEngine.isCriticalThreat(fake))
    for (real in listOf("https://klik.nlb.si/", "https://bankanet.otpbanka.si/", "https://www.dbs.si/", "https://3ds.bankart.si/acs", "https://www.paypal.com/signin")) {
        check(!ThreatBlockEngine.isThreat(real)) { "real bank blocked: $real" }
    }
    ThreatBlockEngine.addThreat("bankanet.otpbanka.si", "Spletno ribarjenje (Phishing)", "mistaken list")
    check(!ThreatBlockEngine.isThreat("https://bankanet.otpbanka.si/")) { "a mistaken phishing entry never blocks a real bank" }
    val lookalike = ThreatBlockEngine.checkThreat("https://nkbm-prijava.eu/")!!
    val warning = ThreatBlockEngine.createSecurityInterstitialHtml("https://nkbm-prijava.eu/", lookalike)
    check(warning.contains("ui_fake_bank_title") && warning.contains("ui_fake_bank_desc") && warning.contains("ui_fake_bank_type") &&
        warning.contains("href=\"https://otpbanka.si/\"") && warning.contains("bypass-threat")) { "fake bank warning page" }
    ThreatBlockEngine.allowForSession("nkbm-prijava.eu")
    check(!ThreatBlockEngine.isThreat("https://nkbm-prijava.eu/")) { "session bypass after the warning" }

    val pageJson = """{"host":"secure-login.example","scheme":"https","password":true,"title":"NLB Klik - prijava"}"""
    val pageMatch = ThreatBlockEngine.checkFakeBankPage("https://secure-login.example/index.html", pageJson)
    check(pageMatch?.category == fake) { "fake bank page" }
    check(ThreatBlockEngine.createSecurityInterstitialHtml("https://secure-login.example/", pageMatch!!, afterPageLoad = true).contains("history.go(-2)"))
    check(ThreatBlockEngine.checkFakeBankPage("https://other.example/", pageJson) == null) { "late answer of a previous page is ignored" }
    check(ThreatBlockEngine.checkFakeBankPage("https://klik.nlb.si/", pageJson.replace("secure-login.example", "klik.nlb.si")) == null)
    check(ThreatBlockEngine.checkFakeBankPage("https://www.facebook.com/nlb", pageJson.replace("secure-login.example", "www.facebook.com")) == null)
    check(ThreatBlockEngine.checkFakeBankPage("https://secure-login.example/", "null") == null)

    // HTML attachment opened locally (SI-CERT TZ009): checked by content, no host
    val localJson = """{"host":"","scheme":"content","password":true,"title":"NLB Klik - prijava"}"""
    val localMatch = ThreatBlockEngine.checkFakeBankPage("content://com.android.providers.downloads.documents/document/1", localJson)
    check(localMatch?.category == fake && localMatch!!.matchedDomain == ThreatBlockEngine.LOCAL_PAGE_KEY && localMatch.sourceFeed!!.contains("ui_fake_bank_local")) { "local attachment: $localMatch" }
    check(ThreatBlockEngine.checkFakeBankPage("https://secure-login.example/", localJson) == null) { "scheme of the answer must match the page" }
    check(ThreatBlockEngine.checkFakeBankPage("file:///sdcard/Download/racun.html", localJson.replace("NLB Klik - prijava", "Moj racun")) == null)
    ThreatBlockEngine.allowForSession(ThreatBlockEngine.LOCAL_PAGE_KEY)
    check(ThreatBlockEngine.checkFakeBankPage("content://x/y", localJson) == null) { "session bypass for local pages" }

    // Card form dressed up as a police fine (SI-CERT, May 2026): no bank, no official-site button
    val lureJson = """{"host":"kazen-placilo.example","scheme":"https","card":true,"title":"Placilo kazni","headings":"Policija - prekrsek","text":"Kazen 39 EUR placajte s kartico"}"""
    val lureMatch = ThreatBlockEngine.checkFakeBankPage("https://kazen-placilo.example/pay", lureJson)
    check(lureMatch?.category == fake && lureMatch!!.sourceFeed!!.contains("ui_fake_bank_lure")) { "lure: $lureMatch" }
    check(!ThreatBlockEngine.createSecurityInterstitialHtml("https://kazen-placilo.example/pay", lureMatch!!, afterPageLoad = true).contains("ui_fake_bank_open_real"))

    val urlhaus = PlainListSource("urlhaus", "abuse.ch URLhaus", "https://urlhaus.abuse.ch/downloads/hostfile/", "Zlonamerna koda (Malware)", "urlhaus")
    val feodo = PlainListSource("feodo", "abuse.ch Feodo Tracker", "https://feodotracker.abuse.ch/", "Botnet C2 Server", "feodo", minEntries = 0, ipv4 = true)
    val added = ThreatBlockEngine.rebuildFromLists(listOf(
        PlainList(urlhaus, listOf("evil-from-list.example", "compromised.posta.si"), 0),
        PlainList(feodo, listOf("192.0.2.44"), 0),
        PlainList(PlainListSource("phishing-army", "Phishing Army", "https://phishing.army/", "Spletno ribarjenje (Phishing)", "phishing"), listOf("www.nlb.si", "paypal.com"), 0),
    ))
    check(added == 3) { "added $added" }
    check(ThreatBlockEngine.isThreat("https://compromised.posta.si/x.apk")) { "confirmed malware on a catalogue host still blocks" }
    check(ThreatBlockEngine.isAllowedForSession("nkbm-prijava.eu") && !ThreatBlockEngine.isAllowedForSession("secure-login.example"))
    check(ThreatBlockEngine.isThreat("https://evil-from-list.example/") && ThreatBlockEngine.isThreat("http://192.0.2.44:8080/gate"))
    check(!ThreatBlockEngine.isThreat("https://www.nlb.si/") && !ThreatBlockEngine.isThreat("https://www.paypal.com/"))
    check(ThreatBlockEngine.isThreat("https://payload-delivery.cc/")) { "built-in list kept" }
    println("PASS: BankGuard hosts and pages, real banks untouched, agent lists")

    // EasyList rules: raw lists go to the ad blocker, never into the threat tree; real banks and the allowlist stay untouched
    val easylist = PlainListSource("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", "Oglasi (EasyList)", "easylist", minEntries = 1, raw = true)
    val rules = listOf("||adnetwork.example^", "/ads/banners/*\$image", "@@||adnetwork.example/ok.js\$script", "@@||allowed-site.example^\$document", "||nlb.si/ads/")
    check(ThreatBlockEngine.rebuildFromLists(listOf(PlainList(easylist, rules, 0))) == 0) { "raw lists must not become threats" }
    check(AdBlockEngine.installFilterLists(listOf(PlainList(easylist, rules, 0))) == 5 && AdBlockEngine.filterRuleCount == 5)
    check(AdBlockEngine.handleIntercept("https://adnetwork.example/a.js", "https://news.example/", "*/*", false) != null) { "easylist host rule" }
    check(AdBlockEngine.handleIntercept("https://adnetwork.example/ok.js", "https://news.example/", "*/*", false) == null) { "easylist exception" }
    check(AdBlockEngine.handleIntercept("https://site.example/ads/banners/x.png", "https://news.example/", "image/*", false) != null)
    check(AdBlockEngine.handleIntercept("https://site.example/ads/banners/x.js", "https://news.example/", "*/*", false) == null) { "type option" }
    check(AdBlockEngine.handleIntercept("https://adnetwork.example/", "https://news.example/", "text/html", true) == null) { "main frame is never blocked by lists" }
    check(AdBlockEngine.handleIntercept("https://adnetwork.example/a.js", "https://allowed-site.example/page", "*/*", false) == null) { "\$document exception" }
    check(AdBlockEngine.handleIntercept("https://www.nlb.si/ads/x.js", "https://news.example/", "*/*", false) == null) { "real banks are never touched by lists" }
    check(AdBlockEngine.installFilterLists(emptyList()) == 0 && AdBlockEngine.handleIntercept("https://adnetwork.example/a.js", "https://news.example/", "*/*", false) == null)
    println("PASS: EasyList rules through the ad blocker")
}
