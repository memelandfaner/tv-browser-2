// JVM tests for FilterListEngine.kt (EasyList network rules). Run by tests/run_tests.sh.
package com.safeer.threatfeed

import kotlin.system.exitProcess

private var passed = 0
private var failed = 0

private fun test(name: String, body: () -> Unit) {
    try {
        body()
        passed++
        println("PASS $name")
    } catch (e: Throwable) {
        failed++
        println("FAIL $name: ${e.javaClass.simpleName}: ${e.message}")
    }
}

private fun check(condition: Boolean, message: () -> String = { "check failed" }) {
    if (!condition) throw AssertionError(message())
}

private val RULES = """
[Adblock Plus 2.0]
! Title: test list
||ads.example^
||tracker.example^${'$'}third-party
||cdn.example/banners/*${'$'}image
||first.example^${'$'}~third-party
/ads/banners/*${'$'}image
/ads/300.${'$'}subdocument
-ad-sidebar.${'$'}image
&adzone=
|http://banner.example/
.gif|
/^https?:\/\/[a-z]{4}\.evil\.example\/[0-9]+\.js/${'$'}script
||yandex.example^${'$'}domain=~mail.example
||only.example^${'$'}domain=news.example|~sport.news.example
||important.example^${'$'}important
@@||important.example/ok.js
@@||ads.example/allowed.js${'$'}script
@@||whitelisted.example^${'$'}document
@@||shop.example^${'$'}generichide
example.com##.ad-banner
example.com#@#.ad-banner
||redirected.example^${'$'}redirect=noopjs
||csp.example^${'$'}csp=script-src 'none'
||nomatch.example^${'$'}unknownoption
""".trim().lines()

private fun request(url: String, page: String? = "https://news.example/story", type: ResourceType = ResourceType.guess(url, null, false)) =
    FilterRequest(url, page, type)

fun main() {
    val set = FilterListEngine.compile(RULES)
    fun blocked(url: String, page: String? = "https://news.example/story", type: ResourceType? = null): Boolean =
        set.decide(request(url, page, type ?: ResourceType.guess(url, null, false)))?.block == true

    test("compiles EasyList syntax, skips comments, cosmetic rules and unsupported options") {
        check(set.size == 18) { "size ${set.size}" }
        check(set.skipped == 5) { "skipped ${set.skipped}" }
        check(FilterListEngine.parse("! comment") == null && FilterListEngine.parse("site.example##.ad") == null)
        check(FilterListEngine.parse("||x.example^\$popup") == null) { "popup rules are not navigation blocks" }
    }
    test("host anchors cover the host and its subdomains, not look-alikes") {
        check(blocked("https://ads.example/a.js"))
        check(blocked("https://cdn.ads.example/pixel.gif"))
        check(!blocked("https://notads.example/a.js"))
        check(!blocked("https://ads.example.org/a.js"))
        check(!blocked("https://good.example/ads.example/a.js")) { "host anchor is not a substring match" }
    }
    test("third-party and first-party options use the registrable domain of the page") {
        check(blocked("https://tracker.example/t.js", page = "https://news.example/"))
        check(!blocked("https://tracker.example/t.js", page = "https://www.tracker.example/"))
        check(blocked("https://first.example/x.js", page = "https://app.first.example/"))
        check(!blocked("https://first.example/x.js", page = "https://other.example/"))
        check(FilterListEngine.registrable("www.bbc.co.uk") == "bbc.co.uk" && FilterListEngine.registrable("a.b.example.com") == "example.com")
    }
    test("resource types: option, negation and the guess from Accept header and extension") {
        check(blocked("https://cdn.example/banners/top.png"))
        check(!blocked("https://cdn.example/banners/top.js")) { "image-only rule" }
        check(blocked("https://site.example/ads/banners/x.webp"))
        check(blocked("https://site.example/ads/300.html", type = ResourceType.SUBDOCUMENT))
        check(!blocked("https://site.example/ads/300.html", type = ResourceType.SCRIPT))
        check(ResourceType.guess("https://x/y.js", "*/*", false) == ResourceType.SCRIPT)
        check(ResourceType.guess("https://x/y", "image/webp,*/*", false) == ResourceType.IMAGE)
        check(ResourceType.guess("https://x/y", "text/html", true) == ResourceType.DOCUMENT)
        check(ResourceType.guess("https://x/api", "application/json", false) == ResourceType.XHR)
    }
    test("plain patterns, anchors, separators and wildcards behave like Adblock Plus") {
        check(blocked("https://site.example/x-ad-sidebar.png"))
        check(blocked("https://site.example/page?x=1&adzone=top"))
        check(!blocked("https://site.example/page?x=1&myadzone=top")) { "'&' must be matched literally" }
        check(blocked("http://banner.example/b.js"))
        check(!blocked("https://banner.example/b.js")) { "|http:// start anchor" }
        check(blocked("https://site.example/pic.gif"))
        check(!blocked("https://site.example/pic.gif?x=1")) { "end anchor" }
        check(FilterListEngine.toRegex("||ex.ample/a^") == "^[a-z][a-z0-9+.-]*://([^/?#]*\\.)?ex\\.ample/a(?:[^a-z0-9_.%-]|$)")
    }
    test("tokens never hide a match: an unanchored pattern edge may continue in the URL") {
        check(FilterListEngine.bestToken("/ads/banner.") == "banner")
        check(FilterListEngine.bestToken("ads.js") == null) { "'ads' could be part of 'myads'" }
        check(FilterListEngine.bestToken("||ads.example^") == "example")
        check(FilterListEngine.bestToken("*ads*") == null)
        val one = FilterListEngine.compile(listOf("ads.js"))
        check(one.decide(request("https://site.example/myads.js"))?.block == true)
    }
    test("regular expression rules") {
        check(blocked("https://abcd.evil.example/123.js"))
        check(!blocked("https://abcde.evil.example/123.js"))
        check(!blocked("https://abcd.evil.example/123.css"))
    }
    test("domain= includes and excludes the page's site") {
        check(blocked("https://yandex.example/a.js", page = "https://news.example/"))
        check(!blocked("https://yandex.example/a.js", page = "https://mail.example/inbox"))
        check(blocked("https://only.example/a.js", page = "https://www.news.example/"))
        check(!blocked("https://only.example/a.js", page = "https://sport.news.example/"))
        check(!blocked("https://only.example/a.js", page = "https://other.example/"))
    }
    test("exceptions win, unless the block is important; \$document allows the whole page") {
        check(!blocked("https://ads.example/allowed.js"))
        check(blocked("https://ads.example/allowed.js", type = ResourceType.IMAGE)) { "exception limited to scripts" }
        check(blocked("https://important.example/ok.js"))
        check(set.isPageAllowed("https://www.whitelisted.example/page"))
        check(!set.isPageAllowed("https://ads.example/"))
        check(!set.isPageAllowed("https://shop.example/")) { "generichide is not a document exception" }
        check(set.decide(request("https://ads.example/", page = null, type = ResourceType.DOCUMENT)) == null) { "plain rules do not block navigation" }
    }
    test("host extraction") {
        check(FilterListEngine.hostOf("https://user:pw@www.example.com:8443/p?q#f") == "www.example.com")
        check(FilterListEngine.hostOf("https://[::1]:8080/x") == "::1")
        check(FilterListEngine.hostOf("https://example.com.") == "example.com")
        check(FilterListEngine.hostOf("data:text/html,x") == "")
    }
    test("an empty set and unknown requests decide nothing") {
        check(FilterSet.EMPTY.decide(request("https://ads.example/a.js")) == null)
        check(set.decide(request("https://harmless.example/app.js")) == null)
    }
    println("FilterListEngine: $passed passed, $failed failed")
    if (failed > 0) exitProcess(1)
}
