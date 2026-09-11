// JVM tests for ThreatListAgent.kt with a local HTTP server. Run with clients/kotlin/run_tests.sh.
package com.safeer.threatfeed

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
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

/** Serves /hosts and /phish with ETag support; counts full downloads and conditional hits. */
private class FakeLists {
    @Volatile var hosts = hostsFile("evil-one.example", "evil-two.example")
    @Volatile var phish = "# Phishing Army blocklist\n" + (1..25).joinToString("\n") { "phish$it.example" } + "\n"
    @Volatile var delayMs = 0L
    @Volatile var brokenHosts = false
    val downloads = AtomicInteger()
    val notModified = AtomicInteger()
    val conditional = AtomicInteger()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base: String

    init {
        server.createContext("/") { exchange ->
            if (delayMs > 0) Thread.sleep(delayMs)
            val body = when (exchange.requestURI.path) {
                "/hosts" -> if (brokenHosts) "<html><body>Wi-Fi login (abuse.ch)</body></html>" else hosts
                "/phish" -> phish
                else -> null
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }
            val etag = "\"" + Integer.toHexString(body.hashCode()) + "\""
            val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
            if (ifNoneMatch != null) conditional.incrementAndGet()
            if (ifNoneMatch == etag) {
                notModified.incrementAndGet()
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
                return@createContext
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("ETag", etag)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            downloads.incrementAndGet()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    fun stop() = server.stop(0)

    companion object {
        fun hostsFile(vararg extra: String) = "# abuse.ch URLhaus Host file\n# comment\n\n" +
            (1..30).joinToString("\n") { "127.0.0.1\tbad$it.example" } + "\n" + extra.joinToString("\n") { "0.0.0.0 $it" } + "\n"
    }
}

private val HOSTS = PlainListSource("urlhaus", "abuse.ch URLhaus", "", "malware", marker = "urlhaus")
private val PHISH = PlainListSource("phishing-army", "Phishing Army", "", "phishing", marker = "phishing army")

private fun sources(server: FakeLists) = listOf(HOSTS.copy(url = "${server.base}/hosts"), PHISH.copy(url = "${server.base}/phish"))

private fun agent(
    dir: File,
    server: FakeLists,
    minInterval: Long = 0,
    delayMs: Long = 50,
    clock: () -> Long = { System.currentTimeMillis() / 1000 },
    onLists: (List<PlainList>) -> Unit = {},
) = ThreatListAgent(
    dir, sources(server), HttpsListFetcher(allowPlainHttpForTests = true), startDelayMs = delayMs,
    minCheckIntervalSeconds = minInterval, clock = clock, onLists = onLists,
)

fun main() {
    val server = FakeLists()
    val dir = Files.createTempDirectory("safeer-lists").toFile()

    test("parser: hosts files, comments, duplicates and invalid names") {
        val text = "# URLhaus\n127.0.0.1 a-b.example\n0.0.0.0\tA-B.EXAMPLE.\nplain.example # note\nlocalhost\n" +
            "127.0.0.1 localhost\n0.0.0.0 0.0.0.0\n1.2.3.4\nbad..example\n-bad.example\nüber.example\n||adblock.example^\nexample\n"
        val entries = PlainListParser.parse(text.toByteArray(), HOSTS.copy(minEntries = 1))
        check(entries == listOf("a-b.example", "plain.example", "adblock.example")) { entries.toString() }
    }
    test("parser: rejects pages without the marker, HTML and too short lists") {
        for (text in listOf("# something else\n" + "x.example\n".repeat(30), "<html>urlhaus</html>\n" + "x.example\n", "# URLhaus\na.example\n")) {
            try {
                PlainListParser.parse(text.toByteArray(), HOSTS)
                throw AssertionError("accepted: ${text.take(30)}")
            } catch (e: ListRejectedException) { /* expected */ }
        }
    }
    test("parser: IPv4 lists") {
        val entries = PlainListParser.parse("# Feodo Tracker\n1.2.3.4\n256.1.1.1\n10.0.0.1 # c2\nhost.example\n".toByteArray(),
            PlainListSource("feodo", "Feodo", "", "botnet_c2", marker = "feodo", minEntries = 0, ipv4 = true))
        check(entries == listOf("1.2.3.4", "10.0.0.1")) { entries.toString() }
    }
    test("HTTPS only by default") {
        try {
            HttpsListFetcher().fetch("${server.base}/hosts", 1000, null, null)
            throw AssertionError("plain HTTP accepted")
        } catch (e: IOException) {
            check(e.message == "only HTTPS is allowed")
        }
    }
    test("first start: returns at once, downloads after the delay and saves the lists") {
        server.delayMs = 1500
        val delivered = CountDownLatch(1)
        var got: List<PlainList> = emptyList()
        val a = agent(dir, server, delayMs = 100) { got = it; delivered.countDown() }
        val started = System.nanoTime()
        check(a.start())
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        check(elapsedMs < 100) { "start() took $elapsedMs ms" }
        check(delivered.await(15, TimeUnit.SECONDS)) { "lists not delivered: ${a.lastError}" }
        a.stop()
        server.delayMs = 0
        check(got.size == 2 && got[0].entries.contains("evil-one.example") && got[1].entries.size == 25) { got.map { it.entries.size }.toString() }
        check(File(dir, "urlhaus.list").isFile && File(dir, "phishing-army.meta").isFile && File(dir, "agent.state").isFile)
        check(server.downloads.get() == 2)
    }
    test("restart: saved lists arrive before any network request, unchanged lists are not downloaded again") {
        val downloadsBefore = server.downloads.get()
        val requestsBefore = server.downloads.get() + server.notModified.get()
        val delivered = CountDownLatch(1)
        val a = agent(dir, server, delayMs = 60_000) { delivered.countDown() }
        a.start()
        check(delivered.await(5, TimeUnit.SECONDS)) { "saved lists not loaded" }
        check(server.downloads.get() + server.notModified.get() == requestsBefore) { "network used before the delay" }
        check(a.lists.size == 2 && a.lists[0].entries.size == 32)
        a.stop()
        val b = agent(dir, server)
        b.loadSaved()
        check(b.refresh() == 0) { b.lastError }
        check(server.downloads.get() == downloadsBefore && server.notModified.get() == 2) { "downloads=${server.downloads.get()} 304=${server.notModified.get()}" }
    }
    test("quick restarts do not repeat the check; forced update does") {
        val now = System.currentTimeMillis() / 1000
        val a = agent(dir, server, minInterval = 900, clock = { now + 60 })
        a.loadSaved()
        val requests = server.downloads.get() + server.notModified.get()
        check(a.refresh(force = false) == 0)
        check(server.downloads.get() + server.notModified.get() == requests) { "throttle ignored" }
        a.refresh(force = true)
        check(server.downloads.get() + server.notModified.get() == requests + 2)
    }
    test("changed list is downloaded and delivered; the other stays") {
        server.hosts = FakeLists.hostsFile("evil-one.example", "new-threat.example")
        var got: List<PlainList> = emptyList()
        val a = agent(dir, server) { got = it }
        a.loadSaved()
        check(a.refresh() == 1) { a.lastError }
        check(got.size == 2 && got[0].entries.contains("new-threat.example") && !got[0].entries.contains("evil-two.example"))
        val reloaded = agent(dir, server).loadSaved()
        check(reloaded[0].entries.contains("new-threat.example"))
    }
    test("captive portal or error page keeps the list in use") {
        server.brokenHosts = true
        server.hosts = FakeLists.hostsFile("changed.example")
        val a = agent(dir, server)
        a.loadSaved()
        check(a.refresh() == 0)
        check(a.lastError.contains("urlhaus") && a.lists[0].entries.contains("new-threat.example")) { a.lastError }
        server.brokenHosts = false
    }
    test("damaged saved list is ignored and downloaded in full") {
        val file = File(dir, "urlhaus.list")
        file.writeText(file.readText() + "\ninjected.example")
        val a = agent(dir, server)
        val loaded = a.loadSaved()
        check(loaded.size == 1 && loaded[0].source.id == "phishing-army") { loaded.map { it.source.id }.toString() }
        val conditionalBefore = server.conditional.get()
        check(a.refresh() == 1) { a.lastError }
        check(server.conditional.get() == conditionalBefore + 1) { "sent a validator for the damaged list" }
        check(a.lists.size == 2 && !a.lists[0].entries.contains("injected.example"))
    }
    test("server offline keeps every list") {
        server.stop()
        val a = agent(dir, server)
        a.loadSaved()
        check(a.refresh() == 0 && a.lists.size == 2 && a.lastError.isNotEmpty())
    }
    test("listener errors do not stop the agent") {
        val a = agent(dir, server, delayMs = 60_000) { throw IllegalStateException("boom") }
        a.start()
        Thread.sleep(300)
        check(a.lists.size == 2)
        a.stop()
    }

    dir.deleteRecursively()
    println("ThreatListAgent: $passed passed, $failed failed")
    exitProcess(if (failed == 0) 0 else 1)
}
