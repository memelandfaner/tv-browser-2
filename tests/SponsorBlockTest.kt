// JVM tests for SponsorBlock.kt with a local HTTP server. Run by tests/run_tests.sh.
package com.safeer.threatfeed

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

private const val VIDEO = "dQw4w9WgXcQ" // sha256 prefix 5f6b
private const val OTHER = "aaaaaaaaaaa"

private val BODY = """[
  {"videoID": "$OTHER", "segments": [{"segment": [1, 5], "category": "sponsor", "actionType": "skip", "UUID": "x"}]},
  {"videoID": "$VIDEO", "segments": [
     {"segment": [30.5, 60.25], "category": "selfpromo", "actionType": "skip", "UUID": "b"},
     {"segment": [5.0, 20.0], "category": "sponsor", "actionType": "skip", "UUID": "a"},
     {"segment": [70, 80], "category": "sponsor", "actionType": "mute", "UUID": "c"},
     {"segment": [90, 100], "category": "outro", "actionType": "skip", "UUID": "d"},
     {"segment": [100, 100.2], "category": "sponsor", "actionType": "skip", "UUID": "e"},
     {"segment": ["x", 1], "category": "sponsor", "actionType": "skip", "UUID": "f"}
  ]}
]"""

fun main() {
    test("video id from watch, short links, embeds, shorts and the youtube.com/tv fragment") {
        check(SponsorBlock.videoIdFromUrl("https://www.youtube.com/watch?v=$VIDEO&t=10") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://m.youtube.com/watch?feature=share&v=$VIDEO") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://youtu.be/$VIDEO?si=abc") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://www.youtube.com/embed/$VIDEO") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://www.youtube.com/shorts/$VIDEO") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://www.youtube.com/tv#/watch/video/control?v=$VIDEO&resume") == VIDEO)
        check(SponsorBlock.videoIdFromUrl("https://www.youtube.com/") == null)
        check(SponsorBlock.videoIdFromUrl("https://example.com/watch?v=$VIDEO") == null)
        check(SponsorBlock.videoIdFromUrl(null) == null)
    }
    test("hash prefix and API address never contain the video id") {
        check(SponsorBlock.hashPrefix(VIDEO) == "5f6b") { SponsorBlock.hashPrefix(VIDEO) }
        val url = SponsorBlock.apiUrl(VIDEO)
        check(url.startsWith("https://sponsor.ajay.app/api/skipSegments/5f6b?")) { url }
        check(!url.contains(VIDEO))
        check(url.contains("sponsor") && url.contains("skip"))
    }
    test("parser picks this video's skip segments of the wanted categories, sorted") {
        val segments = SponsorBlock.parse(BODY, VIDEO)
        check(segments == listOf(SponsorSegment(5.0, 20.0, "sponsor"), SponsorSegment(30.5, 60.25, "selfpromo"))) { segments.toString() }
        check(SponsorBlock.parse(BODY, "bbbbbbbbbbb").isEmpty())
        check(SponsorBlock.parse(null, VIDEO).isEmpty() && SponsorBlock.parse("{bad", VIDEO).isEmpty() && SponsorBlock.parse("{}", VIDEO).isEmpty())
    }
    test("apply script is a single safe statement") {
        val script = SponsorBlock.applyScript(VIDEO, SponsorBlock.parse(BODY, VIDEO))
        check(script == "window.__safeerSb && window.__safeerSb.set(\"$VIDEO\", [[5.0,20.0,\"sponsor\"],[30.5,60.25,\"selfpromo\"]]);") { script }
        try { SponsorBlock.applyScript("bad\");alert(1);//", emptyList()); throw AssertionError("accepted") } catch (e: IllegalArgumentException) { /* expected */ }
        check(SponsorBlock.RUNTIME_JS.contains("SafeerBridge.sponsorSegments") && SponsorBlock.RUNTIME_JS.contains("__safeerSb"))
    }
    test("HTTPS only by default") {
        try { HttpsSponsorFetcher().fetch("http://example.com/x"); throw AssertionError("accepted http") } catch (e: java.io.IOException) { check(e.message == "only HTTPS is allowed") }
    }
    val requests = AtomicInteger()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/skipSegments") { exchange ->
        requests.incrementAndGet()
        val prefix = exchange.requestURI.path.substringAfterLast('/')
        val body = if (prefix == "5f6b") BODY.toByteArray() else ByteArray(0)
        exchange.sendResponseHeaders(if (body.isEmpty()) 404 else 200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    val base = "http://127.0.0.1:${server.address.port}"
    val http = HttpsSponsorFetcher(allowPlainHttpForTests = true)
    SponsorBlock.fetcher = SponsorFetcher { url -> http.fetch(url.replace("https://sponsor.ajay.app", base)) }
    try {
        test("lookup by hash prefix, cached (including 404 = no segments)") {
            SponsorBlock.clearCache()
            check(SponsorBlock.segmentsFor(VIDEO).size == 2)
            check(SponsorBlock.segmentsFor(VIDEO).size == 2 && requests.get() == 1) { "requests=${requests.get()}" }
            check(SponsorBlock.segmentsFor("bbbbbbbbbbb").isEmpty() && requests.get() == 2)
            check(SponsorBlock.segmentsFor("bbbbbbbbbbb").isEmpty() && requests.get() == 2) { "404 is cached" }
            check(SponsorBlock.segmentsFor("not a video id").isEmpty() && requests.get() == 2)
        }
        test("async lookup answers on the background thread; a server error keeps nothing in the cache") {
            SponsorBlock.clearCache()
            val done = CountDownLatch(1)
            var got: List<SponsorSegment>? = null
            SponsorBlock.fetchAsync(VIDEO) { got = it; done.countDown() }
            check(done.await(10, TimeUnit.SECONDS) && got?.size == 2) { got.toString() }
            SponsorBlock.fetcher = SponsorFetcher { throw java.io.IOException("offline") }
            SponsorBlock.clearCache()
            check(SponsorBlock.segmentsFor(VIDEO).isEmpty())
            SponsorBlock.fetcher = SponsorFetcher { url -> http.fetch(url.replace("https://sponsor.ajay.app", base)) }
            check(SponsorBlock.segmentsFor(VIDEO).size == 2) { "an error must not be cached" }
        }
    } finally {
        server.stop(0)
    }
    println("SponsorBlock: $passed passed, $failed failed")
    if (failed > 0) exitProcess(1)
}
