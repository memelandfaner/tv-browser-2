// JVM tests for SignedThreatFeed.kt. Run with clients/kotlin/run_tests.sh (or the copy in each app).
// Usage: java -cp test.jar:bcprov-ed25519-1.78.1.jar com.safeer.threatfeed.SignedThreatFeedTestKt <conformance-dir>
package com.safeer.threatfeed

import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

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
        e.printStackTrace()
    }
}

private fun check(condition: Boolean, message: () -> String = { "check failed" }) {
    if (!condition) throw AssertionError(message())
}

private fun <T> expectThrows(type: Class<out Throwable>, body: () -> T) {
    try {
        body()
    } catch (e: Throwable) {
        if (type.isInstance(e)) return
        throw AssertionError("expected ${type.simpleName}, got ${e.javaClass.simpleName}: ${e.message}")
    }
    throw AssertionError("expected ${type.simpleName}")
}

private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

private const val X509_PREFIX = "302a300506032b6570032100"
private val L = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))

class TestKey(val id: String) {
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val publicRaw: ByteArray = pair.public.encoded.copyOfRange(12, 44)
    val publicB64: String = b64(publicRaw)
    fun signRaw(message: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
        initSign(pair.private)
        update(message)
        sign()
    }
    fun sign(context: ByteArray, data: ByteArray): String = b64(signRaw(context + data))
}

private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

class BuiltFeed(val envelope: ByteArray, val bundle: ByteArray, val version: Long)

private fun buildFeed(
    key: TestKey,
    version: Long,
    rules: List<List<Any>>,
    generated: String = "2026-09-11T12:00:00Z",
    expires: String = "2096-09-11T12:00:00Z",
    feed: String = "threats",
    sources: String = "[{\"id\":\"fixture\",\"license\":\"CC0-1.0\",\"name\":\"Safeer test fixture\",\"url\":\"https://safeer.si/\"}]",
): BuiltFeed {
    val rulesText = rules.joinToString(",", "[", "]") { r -> "[${quote(r[0] as String)},${quote(r[1] as String)},${quote(r[2] as String)},${r[3]}]" }
    val rulesSha = sha(ascii(rulesText))
    val bundle = ascii(
        "{\"expires_at\":${quote(expires)},\"feed_type\":${quote(feed)},\"generated_at\":${quote(generated)}," +
            "\"rule_count\":${rules.size},\"rules\":$rulesText,\"schema_version\":1,\"sha256\":${quote(rulesSha)}," +
            "\"sources\":$sources,\"version\":$version}"
    )
    val manifest = ascii(
        "{\"bundle\":{\"path\":${quote("$feed-$version.json")},\"sha256\":${quote(sha(bundle))}," +
            "\"signature\":{\"alg\":\"ed25519\",\"key_id\":${quote(key.id)},\"sig\":${quote(key.sign(SignedFeedFormat.BUNDLE_CONTEXT, bundle))}}," +
            "\"size\":${bundle.size}},\"expires_at\":${quote(expires)},\"feed_type\":${quote(feed)},\"generated_at\":${quote(generated)}," +
            "\"rule_count\":${rules.size},\"rules_sha256\":${quote(rulesSha)},\"schema_version\":1,\"type\":\"safeer-feed-manifest\",\"version\":$version}"
    )
    val envelope = ascii(
        "{\"signed\":${quote(b64(manifest))},\"signatures\":[{\"alg\":\"ed25519\",\"key_id\":${quote(key.id)}," +
            "\"sig\":${quote(key.sign(SignedFeedFormat.MANIFEST_CONTEXT, manifest))}}]}"
    )
    return BuiltFeed(envelope, bundle, version)
}

private val BASE_RULES: List<List<Any>> = listOf(
    listOf("phish.example", "domain", "phishing", 0),
    listOf("xn--bcher-kva.example", "domain", "scam", 0),
    listOf("malware.example", "hostname", "malware", 0),
    listOf("93.184.216.34", "ipv4", "botnet_c2", 0),
    listOf("2606:2800:220:1:248:1893:25c8:1946", "ipv6", "botnet_c2", 0),
    listOf("http://files.example:8080/bin.sh", "url", "malware", 0),
)

/** In-memory feed server: path -> body, with switchable failures. */
class FakeServer : FeedFetcher {
    val files = HashMap<String, ByteArray>()
    var offline = false
    val requests = ArrayList<String>()

    fun publish(feed: BuiltFeed, base: String = "https://intel.test") {
        files["$base/v1/threats/latest.json"] = feed.envelope
        files["$base/v1/threats/threats-${feed.version}.json"] = feed.bundle
    }

    override fun fetch(url: String, limit: Int): ByteArray {
        requests.add(url)
        if (offline) throw java.io.IOException("network unreachable")
        val body = files[url] ?: throw java.io.IOException("HTTP status 404")
        if (body.size > limit) throw java.io.IOException("response too large")
        return body
    }
}

private fun tempDir(): File = Files.createTempDirectory("safeer-feed-test").toFile().apply { deleteOnExit() }

private fun jdkVerify(publicRaw: ByteArray, message: ByteArray, signature: ByteArray): Boolean = try {
    val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(hex(X509_PREFIX) + publicRaw))
    Signature.getInstance("Ed25519").run {
        initVerify(key)
        update(message)
        verify(signature)
    }
} catch (e: Exception) {
    false
}

fun main(args: Array<String>) {
    val conformanceDir = File(args.getOrElse(0) { "clients/conformance" })

    test("RFC 8032 test vectors 1-3 and malleability") {
        val vectors = listOf(
            Triple("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"),
            Triple("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
                "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"),
            Triple("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
                "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"),
        )
        for ((pub, msg, sig) in vectors) {
            val p = hex(pub); val m = hex(msg); val s = hex(sig)
            check(Ed25519Verifier.verify(p, m, s)) { "vector must verify" }
            check(!Ed25519Verifier.verify(p, m + byteArrayOf(0x78), s))
            val tampered = s.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
            check(!Ed25519Verifier.verify(p, m, tampered))
            val sLittle = s.copyOfRange(32, 64).reversedArray()
            val sPlusL = BigInteger(1, sLittle).add(L).toByteArray().let { big ->
                val out = ByteArray(32)
                val trimmed = if (big.size > 32) big.copyOfRange(big.size - 32, big.size) else big
                System.arraycopy(trimmed, 0, out, 32 - trimmed.size, trimmed.size)
                out.reversedArray()
            }
            check(!Ed25519Verifier.verify(p, m, s.copyOfRange(0, 32) + sPlusL)) { "S + L must be rejected" }
            check(!Ed25519Verifier.verify(p.copyOf(31), m, s))
            check(!Ed25519Verifier.verify(p, m, s.copyOf(63)))
        }
    }

    test("Ed25519 agrees with the JDK on 400 random and mutated signatures") {
        val random = SecureRandom()
        repeat(400) { round ->
            val key = TestKey("k")
            val message = ByteArray(random.nextInt(300)).also { random.nextBytes(it) }
            val signature = key.signRaw(message)
            check(Ed25519Verifier.verify(key.publicRaw, message, signature)) { "valid signature rejected in round $round" }
            val pub = key.publicRaw.copyOf()
            val msg = message.copyOf()
            val sig = signature.copyOf()
            when (round % 3) {
                0 -> sig[random.nextInt(64)] = (sig[random.nextInt(64)].toInt() xor (1 shl random.nextInt(8))).toByte()
                1 -> if (msg.isNotEmpty()) msg[random.nextInt(msg.size)] = (msg[0].toInt() xor 1).toByte() else sig[0] = (sig[0].toInt() xor 1).toByte()
                else -> pub[random.nextInt(32)] = (pub[random.nextInt(32)].toInt() xor (1 shl random.nextInt(8))).toByte()
            }
            val ours = Ed25519Verifier.verify(pub, msg, sig)
            val jdk = jdkVerify(pub, msg, sig)
            check(ours == jdk) { "disagreement in round $round: ours=$ours jdk=$jdk" }
        }
    }

    test("conformance corpus verdicts match the reference client") {
        val trustedText = File(conformanceDir, "trusted_keys.json").readText().trim()
        val trustedMatch = Regex("^\\{\"([^\"]+)\": \"([^\"]+)\"}$").find(trustedText) ?: throw AssertionError("bad trusted_keys.json")
        val trusted = mapOf(trustedMatch.groupValues[1] to trustedMatch.groupValues[2])
        var cases = 0
        val kinds = HashSet<String>()
        for (line in File(conformanceDir, "cases.txt").readLines()) {
            if (line.isBlank()) continue
            val f = line.split(" ")
            val (name, expect) = f[0] to f[1]
            val installed = f[2].toLong()
            val now = f[3].toLong()
            val envelope = Base64.getDecoder().decode(f[5])
            val bundle = if (f[6] == "-") null else Base64.getDecoder().decode(f[6])
            var count: Int? = null
            val verdict = try {
                val manifest = SignedFeedVerifier.verifyManifest(envelope, trusted, "threats", installed, now)
                if (bundle != null) count = SignedFeedVerifier.verifyBundle(bundle, manifest, trusted).ruleCount
                "ok"
            } catch (e: RollbackException) {
                "rollback"
            } catch (e: ExpiredFeedException) {
                "expired"
            } catch (e: FeedVerificationException) {
                "error"
            }
            check(verdict == expect) { "case $name: expected $expect, got $verdict" }
            if (expect == "ok" && f[4] != "-") check(count == f[4].toInt()) { "case $name: rule count $count" }
            kinds.add(expect)
            cases++
        }
        check(cases >= 100) { "corpus too small: $cases" }
        check(kinds == setOf("ok", "error", "rollback", "expired"))
    }

    val key = TestKey("safeer-test")
    val trusted = mapOf(key.id to key.publicB64)
    val now = 1_789_171_200L // 2026-09-12T00:00:00Z

    test("matching: domains, hostnames, URLs, IPv4, IPv6 and IDN") {
        val feed = buildFeed(key, 100, BASE_RULES + listOf(listOf("https://evil.example/a?b=1", "url", "phishing", 0)))
        val manifest = SignedFeedVerifier.verifyManifest(feed.envelope, trusted, "threats", 0, now)
        val index = SignedFeedVerifier.verifyBundle(feed.bundle, manifest, trusted).index
        check(index.matchUrl("https://malware.example/login")?.category == "malware")
        check(index.matchUrl("https://MALWARE.example./x")?.category == "malware")
        check(index.matchUrl("https://sub.malware.example/") == null) { "hostname rules must not cover subdomains" }
        check(index.matchUrl("https://a.b.phish.example/x")?.let { it.category == "phishing" && it.indicator == "phish.example" } == true)
        check(index.matchHost("phish.example")?.indicatorType == "domain")
        check(index.matchUrl("https://notphish.example/") == null) { "label boundary" }
        check(index.matchUrl("https://phish.example.attacker.test/") == null)
        check(index.matchUrl("http://files.example:8080/bin.sh")?.category == "malware")
        check(index.matchUrl("http://user:pw@files.example:8080/bin.sh#frag")?.indicatorType == "url")
        check(index.matchUrl("http://files.example:8080/other") == null)
        check(index.matchUrl("http://files.example/bin.sh") == null) { "port is part of a URL rule" }
        check(index.matchUrl("https://evil.example:443/a?b=1")?.category == "phishing") { "default port removed" }
        check(index.matchUrl("HTTPS://EVIL.EXAMPLE/a?b=1")?.category == "phishing")
        check(index.matchUrl("https://evil.example/a?b=2") == null)
        check(index.matchUrl("http://93.184.216.34/")?.indicatorType == "ipv4")
        check(index.matchUrl("http://[2606:2800:220:1:248:1893:25c8:1946]:8080/")?.category == "botnet_c2")
        check(index.matchUrl("http://[2606:2800:0220:0001:0248:1893:25C8:1946]/")?.category == "botnet_c2") { "IPv6 normalized" }
        check(index.matchHost("[2606:2800:220:1:248:1893:25c8:1946]")?.indicatorType == "ipv6")
        check(index.matchUrl("https://shop.xn--bcher-kva.example/")?.category == "scam")
        check(index.matchUrl("https://github.com/") == null)
        check(index.matchUrl("not a url") == null && index.matchUrl("") == null && index.matchHost(null) == null)
        check(index.matchUrl("https://malware.example:99999/") == null) { "invalid port" }
        check(index.matchUrl("javascript:alert(1)") == null)
        check(index.matchUrl("https://malware.example\\@good.example/")?.category == "malware") { "backslash ends authority like Chromium" }
        check(index.matchUrl("https://good.example@malware.example/")?.category == "malware") { "userinfo is ignored" }
    }

    test("most severe rule wins; IPv4-mapped IPv6 matches IPv4 rules; categories follow the feed type") {
        val rules: List<List<Any>> = listOf(
            listOf("evil.example", "domain", "phishing", 0),
            listOf("c2.evil.example", "domain", "botnet_c2", 0),
            listOf("https://evil.example/payload.apk", "url", "malware", 0),
            listOf("www.shop.example", "hostname", "scam", 0),
            listOf("shop.example", "domain", "malware", 0),
            listOf("dup.example", "hostname", "phishing", 0),
            listOf("dup.example", "hostname", "malware", 0),
            listOf("93.184.216.34", "ipv4", "botnet_c2", 0),
        )
        val feed = buildFeed(key, 100, rules)
        val manifest = SignedFeedVerifier.verifyManifest(feed.envelope, trusted, "threats", 0, now)
        val index = SignedFeedVerifier.verifyBundle(feed.bundle, manifest, trusted).index
        check(index.matchUrl("https://c2.evil.example/gate.php")?.category == "botnet_c2") { "C2 under a phishing domain" }
        check(index.matchUrl("https://login.evil.example/")?.category == "phishing")
        check(index.matchUrl("https://evil.example/payload.apk")?.let { it.category == "malware" && it.indicatorType == "url" } == true)
        check(index.matchUrl("https://www.shop.example/")?.category == "malware") { "domain malware beats hostname scam" }
        check(index.matchHost("dup.example")?.category == "malware") { "duplicate indicator keeps the most severe" }
        check(index.matchUrl("http://[::ffff:5db8:d822]/")?.category == "botnet_c2") { "IPv4-mapped IPv6" }
        check(index.matchHost("[::ffff:93.184.216.34]") == null || index.matchHost("::ffff:5db8:d822")?.category == "botnet_c2")
        val ads = buildFeed(key, 101, listOf(listOf("ads.example", "hostname", "ads", 0)))
        val adsManifest = SignedFeedVerifier.verifyManifest(ads.envelope, trusted, "threats", 0, now)
        expectThrows(FeedVerificationException::class.java) { SignedFeedVerifier.verifyBundle(ads.bundle, adsManifest, trusted) }
        val adblock = buildFeed(key, 102, listOf(listOf("ads.example", "hostname", "ads", 0)), feed = "adblock")
        val adblockManifest = SignedFeedVerifier.verifyManifest(adblock.envelope, trusted, "adblock", 0, now)
        check(SignedFeedVerifier.verifyBundle(adblock.bundle, adblockManifest, trusted).ruleCount == 1)
        val c2InAdblock = buildFeed(key, 103, listOf(listOf("c2.example", "hostname", "botnet_c2", 0)), feed = "adblock")
        val c2Manifest = SignedFeedVerifier.verifyManifest(c2InAdblock.envelope, trusted, "adblock", 0, now)
        expectThrows(FeedVerificationException::class.java) { SignedFeedVerifier.verifyBundle(c2InAdblock.bundle, c2Manifest, trusted) }
    }

    test("IPv6 text follows RFC 5952 exactly like Python ipaddress") {
        val expected = mapOf(
            "2001:db8:0:0:1:0:0:1" to "2001:db8::1:0:0:1",
            "2001:0:0:1:0:0:0:1" to "2001:0:0:1::1",
            "0:0:0:0:0:0:0:0" to "::",
            "0:0:0:0:0:0:0:1" to "::1",
            "1:0:0:0:0:0:0:0" to "1::",
            "2001:db8:0:1:1:1:1:1" to "2001:db8:0:1:1:1:1:1",
            "2606:2800:0220:0001:0248:1893:25C8:1946" to "2606:2800:220:1:248:1893:25c8:1946",
            "fe80::" to "fe80::",
        )
        for ((input, output) in expected) {
            val parsed = Ipv6.parse(input) ?: throw AssertionError("cannot parse $input")
            check(Ipv6.format(parsed) == output) { "$input -> ${Ipv6.format(parsed)}, expected $output" }
        }
        for (bad in listOf("", ":", ":::", "1::2::3", "1:2:3:4:5:6:7:8:9", "12345::", "g::1", "::ffff:1.2.3.4", "fe80::1%eth0", "1:2:3:4:5:6:7::8")) {
            check(Ipv6.parse(bad) == null) { "must reject $bad" }
        }
    }

    test("strict JSON cursor refuses non-canonical input") {
        val bad = listOf("{\"a\" :1}", "{\"a\":01}", "{\"a\":1.0}", "{\"a\":-1}", "{\"a\":\"\\u0041\"}", "{\"a\":\"\\n\"}",
            "{\"a\":true}", "{\"a\":null}", "{\"a\":9007199254740992}", "{\"a\":\"\u00e9\"}", "{\"a\":1,\"a\":2}")
        for (text in bad) {
            expectThrows(FeedVerificationException::class.java) {
                val cursor = JsonCursor(text.toByteArray(Charsets.UTF_8))
                cursor.readValue()
                cursor.expectEnd()
            }
        }
        val ok = JsonCursor(ascii("{\"a\":[\"x\\\"y\\\\\",9007199254740991]}")).readValue() as Map<*, *>
        check((ok["a"] as List<*>)[0] == "x\"y\\" && (ok["a"] as List<*>)[1] == 9007199254740991L)
        expectThrows(FeedVerificationException::class.java) {
            JsonCursor(ascii("[[[[[[[[[[1]]]]]]]]]]")).readValue()
        }
    }

    test("store installs, survives restart, refuses rollback, forgery, expiry and tampering") {
        val dir = tempDir()
        val server = FakeServer()
        val impostor = TestKey(key.id)
        val store = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test/"), server) { now }
        check(!store.load() && store.bundle == null)

        server.publish(buildFeed(key, 100, BASE_RULES))
        check(store.update()) { store.lastError }
        check(store.installedVersion() == 100L)
        check(store.matchUrl("https://malware.example/")?.category == "malware")
        check(!store.update() && store.lastError.isEmpty()) { "same version is up to date" }
        check(server.requests.last().endsWith("/latest.json")) { "up-to-date check downloads only the manifest" }

        val attempts = listOf(
            "rollback" to buildFeed(key, 99, BASE_RULES.drop(3)),
            "forged" to buildFeed(impostor, 103, BASE_RULES.drop(3)),
            "expired" to buildFeed(key, 102, BASE_RULES.drop(3), generated = "2019-01-01T00:00:00Z", expires = "2019-01-08T00:00:00Z"),
        )
        for ((label, feed) in attempts) {
            server.files.clear()
            server.publish(feed)
            check(!store.update()) { "$label must fail" }
            check(store.lastError.isNotEmpty()) { "$label must record an error" }
            check(store.installedVersion() == 100L && store.matchUrl("https://malware.example/") != null) { "$label kept v100" }
        }
        val tampered = buildFeed(key, 104, BASE_RULES.drop(3))
        server.files.clear()
        server.publish(BuiltFeed(tampered.envelope, String(tampered.bundle, Charsets.US_ASCII).replace("93.184.216.34", "93.184.216.35").toByteArray(), 104))
        check(!store.update() && store.installedVersion() == 100L)
        server.files.clear()
        server.publish(BuiltFeed(tampered.envelope, ascii("<html>not found</html>"), 104))
        check(!store.update() && store.installedVersion() == 100L)
        server.files.clear()
        server.publish(BuiltFeed(tampered.envelope, ByteArray(0), 104))
        check(!store.update() && store.installedVersion() == 100L)
        server.files.clear()
        server.publish(BuiltFeed(tampered.envelope, tampered.bundle + tampered.bundle, 104))
        check(!store.update() && store.lastError.contains("too large")) { "oversized bundle refused by the size limit" }
        server.offline = true
        check(!store.update() && store.matchUrl("https://malware.example/") != null)
        server.offline = false

        server.files.clear()
        server.publish(buildFeed(key, 101, BASE_RULES.drop(3) + listOf(listOf("newbad.example", "hostname", "malware", 0))))
        check(store.update()) { store.lastError }
        check(store.matchUrl("https://malware.example/") == null && store.matchUrl("https://newbad.example/") != null)
        val remaining = dir.list()!!.sorted()
        check(remaining == listOf("threats-101-bundle.json", "threats-101-manifest.json", "threats-state.json")) { "old files removed: $remaining" }

        val restarted = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), FakeServer().apply { offline = true }) { now }
        check(restarted.load()) { restarted.lastError }
        check(restarted.matchUrl("https://newbad.example/")?.category == "malware")
        server.files.clear()
        server.publish(buildFeed(key, 100, BASE_RULES))
        val again = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { now }
        again.load()
        check(!again.update() && again.installedVersion() == 101L) { "older release after restart is a rollback" }
    }

    test("expired stored bundle is still used; state file is the commit point") {
        val dir = tempDir()
        val server = FakeServer()
        var clock = now
        val store = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { clock }
        server.publish(buildFeed(key, 100, BASE_RULES, expires = "2026-09-13T00:00:00Z"))
        check(store.update())
        clock = now + 30 * 86400
        val later = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { clock }
        check(later.load() && later.matchUrl("https://malware.example/") != null) { "expired stored bundle still protects" }
        // A crash after writing the new files but before the state file keeps the old version.
        val newer = buildFeed(key, 200, BASE_RULES.drop(2))
        File(dir, "threats-200-bundle.json").writeBytes(newer.bundle)
        File(dir, "threats-200-manifest.json").writeBytes(newer.envelope)
        val crashed = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { clock }
        check(crashed.load() && crashed.bundle?.manifest?.version == 100L)
    }

    test("corrupted or foreign stored files are never trusted") {
        val dir = tempDir()
        val server = FakeServer()
        val store = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { now }
        server.publish(buildFeed(key, 100, BASE_RULES))
        check(store.update())
        val bundleFile = File(dir, "threats-100-bundle.json")
        bundleFile.writeBytes(String(bundleFile.readBytes(), Charsets.US_ASCII).replace("malware.example", "malware.exampla").toByteArray())
        val reloaded = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { now }
        check(!reloaded.load() && reloaded.bundle == null) { "tampered stored bundle refused" }
        check(reloaded.update() && reloaded.matchUrl("https://malware.example/") != null) { "same version downloaded again" }
        File(dir, "threats-state.json").writeText("{\"version\":\"x\"}")
        check(SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { now }.installedVersion() == 0L)
        val otherKey = SignedFeedStore(dir, "threats", mapOf("other" to TestKey("other").publicB64), listOf("https://intel.test"), server) { now }
        File(dir, "threats-state.json").writeText("{\"version\":100}")
        check(!otherKey.load()) { "a bundle signed by an untrusted key is refused on load" }
    }

    test("disabled without trusted keys; fallback to a second mirror") {
        val server = FakeServer()
        server.publish(buildFeed(key, 100, BASE_RULES), base = "https://mirror.test")
        val disabled = SignedFeedStore(tempDir(), "threats", emptyMap(), listOf("https://intel.test"), server) { now }
        check(!disabled.enabled && !disabled.update() && !disabled.load() && server.requests.isEmpty())
        check(!SignedFeedService(disabled).start())
        val mirrored = SignedFeedStore(tempDir(), "threats", trusted, listOf("https://intel.test", "https://mirror.test"), server) { now }
        check(mirrored.update() && mirrored.matchHost("malware.example") != null) { mirrored.lastError }
        expectThrows(java.io.IOException::class.java) { HttpsFeedFetcher().fetch("http://intel.test/v1/threats/latest.json", 10) }
    }

    test("background service loads, updates and notifies") {
        val dir = tempDir()
        val server = FakeServer()
        server.publish(buildFeed(key, 100, BASE_RULES))
        val store = SignedFeedStore(dir, "threats", trusted, listOf("https://intel.test"), server) { now }
        val changes = java.util.concurrent.atomic.AtomicInteger()
        val service = SignedFeedService(store, intervalSeconds = 3600, retrySeconds = 3600, firstDelaySeconds = 0) { changes.incrementAndGet() }
        check(service.start() && !service.start())
        val deadline = System.currentTimeMillis() + 10_000
        while (store.bundle == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        check(store.bundle?.manifest?.version == 100L && changes.get() == 1)
        server.files.clear()
        server.publish(buildFeed(key, 101, BASE_RULES))
        val done = java.util.concurrent.CountDownLatch(1)
        var installed = false
        check(service.requestUpdate { installed = it; done.countDown() })
        check(done.await(10, java.util.concurrent.TimeUnit.SECONDS) && installed && changes.get() == 2)
        service.stop()
        check(!service.requestUpdate())
    }

    test("large bundle: 250000 rules verify and index within limits") {
        val rules = ArrayList<List<Any>>(250_000)
        for (i in 0 until 250_000) {
            val kind = when (i % 4) { 0 -> "domain"; 1 -> "hostname"; 2 -> "url"; else -> "ipv4" }
            val value = when (kind) {
                "url" -> "https://host$i.example/path/$i?id=$i"
                "ipv4" -> "8.${(i shr 16) and 255}.${(i shr 8) and 255}.${i and 255}"
                else -> "host$i.malicious-$i.example"
            }
            rules.add(listOf(value, kind, if (i % 2 == 0) "phishing" else "malware", 0))
        }
        val sorted = rules.sortedWith(compareBy({ it[1] as String }, { it[0] as String }))
        val feed = buildFeed(key, 100, sorted)
        val started = System.nanoTime()
        val manifest = SignedFeedVerifier.verifyManifest(feed.envelope, trusted, "threats", 0, now)
        val bundle = SignedFeedVerifier.verifyBundle(feed.bundle, manifest, trusted)
        val millis = (System.nanoTime() - started) / 1_000_000
        println("     ${feed.bundle.size / 1024} KiB, ${bundle.ruleCount} rules verified and indexed in $millis ms")
        check(bundle.ruleCount == 250_000)
        check(bundle.index.matchUrl("https://a.host4.malicious-4.example/")?.category == "phishing")
        check(bundle.index.matchUrl("https://host6.example/path/6?id=6")?.category == "phishing")
        val lookups = System.nanoTime()
        repeat(100_000) { bundle.index.matchUrl("https://www.news$it.example/article/$it?ref=home") }
        println("     100000 URL lookups in ${(System.nanoTime() - lookups) / 1_000_000} ms")
    }

    println("\n$passed passed, $failed failed")
    if (failed > 0) System.exit(1)
}
