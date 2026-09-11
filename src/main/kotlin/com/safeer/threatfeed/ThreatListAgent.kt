/*
 * Safeer threat list agent for Android and Android TV (pure JVM, no Android imports).
 *
 * Keeps public plain-text blocklists (abuse.ch, Phishing Army ...) on the device and refreshes them
 * every time the browser starts, without slowing the start or the first page:
 *  - start() returns at once; all work runs on one low-priority background thread.
 *  - The lists saved by the previous run are loaded first (integrity checked with SHA-256), so the
 *    protection is complete a moment after start instead of after a download.
 *  - The network check waits [startDelayMs] (the first page loads first) and uses conditional GET
 *    (ETag / Last-Modified): an unchanged list costs one small request, not a download.
 *  - Restarts in quick succession do not repeat the check within [minCheckIntervalSeconds].
 *  - Afterwards the lists are checked every [intervalSeconds] while the browser runs.
 *  - Every failure keeps the lists that are already in use. Requests are anonymous GETs of public files.
 *
 * The same file is used unchanged by Safeer Browser for Android and Android TV.
 */
package com.safeer.threatfeed

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One public blocklist: a hosts file or one domain per line. */
data class PlainListSource(
    /** File-name safe identifier: lower case letters, digits and dashes. */
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    /** Text that must appear near the top of the file; rejects captive portals and error pages. */
    val marker: String,
    val minEntries: Int = 20,
    val maxBytes: Int = 32 * 1024 * 1024,
    /** Lists of IPv4 addresses (for example Feodo Tracker) instead of host names. */
    val ipv4: Boolean = false,
) {
    init {
        require(id.length in 1..40 && id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) { "invalid list id" }
    }
}

class PlainList(val source: PlainListSource, val entries: List<String>, val fetchedAtEpochSeconds: Long)

class ListRejectedException(message: String) : IOException(message)

data class ConditionalResponse(val notModified: Boolean, val body: ByteArray?, val etag: String?, val lastModified: String?)

fun interface ConditionalFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, limit: Int, etag: String?, lastModified: String?): ConditionalResponse
}

/** Anonymous conditional GET: no cookies or caches, HTTPS only (also after redirects), hard limits. */
class HttpsListFetcher(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    private val totalTimeoutMs: Long = 120_000,
    private val allowPlainHttpForTests: Boolean = false,
) : ConditionalFetcher {
    override fun fetch(url: String, limit: Int, etag: String?, lastModified: String?): ConditionalResponse {
        if (!url.startsWith("https://") && !allowPlainHttpForTests) throw IOException("only HTTPS is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true // HttpURLConnection never follows HTTPS -> HTTP
            connection.useCaches = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", "Safeer")
            connection.setRequestProperty("Accept", "text/plain")
            if (!etag.isNullOrEmpty()) connection.setRequestProperty("If-None-Match", etag)
            if (!lastModified.isNullOrEmpty()) connection.setRequestProperty("If-Modified-Since", lastModified)
            val status = connection.responseCode
            if (!allowPlainHttpForTests && connection.url.protocol != "https") throw IOException("redirected away from HTTPS")
            if (status == 304) return ConditionalResponse(true, null, etag, lastModified)
            if (status != 200) throw IOException("HTTP status $status")
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared > limit) throw IOException("response too large")
            val deadline = System.currentTimeMillis() + totalTimeoutMs
            val body = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream(64 * 1024)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > limit) throw IOException("response too large")
                    out.write(buffer, 0, read)
                    if (System.currentTimeMillis() > deadline) throw IOException("download took too long")
                }
                out.toByteArray()
            }
            return ConditionalResponse(false, body, connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
        } finally {
            connection.disconnect()
        }
    }
}

object PlainListParser {
    private val hostLabel = Regex("[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?")
    private val ipv4 = Regex("(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])){3}")
    private val sinkAddresses = setOf("0.0.0.0", "127.0.0.1", "::", "::1")

    /** Normalized, de-duplicated entries; throws [ListRejectedException] for files that are not this list. */
    fun parse(bytes: ByteArray, source: PlainListSource): List<String> {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8).lowercase()
        if (!head.contains(source.marker.lowercase())) throw ListRejectedException("marker '${source.marker}' missing")
        if (head.contains("<html") || head.contains("<!doctype")) throw ListRejectedException("HTML instead of a list")
        val seen = LinkedHashSet<String>()
        String(bytes, Charsets.UTF_8).lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("!")) return@forEach
            val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
            val candidate = (if (parts.size >= 2 && parts[0] in sinkAddresses) parts[1] else parts[0])
                .lowercase().trimEnd('.').removePrefix("||").removeSuffix("^")
            val entry = if (source.ipv4) candidate.takeIf { ipv4.matches(it) } else candidate.takeIf { isHostName(it) }
            if (entry != null) seen.add(entry)
        }
        if (seen.size < source.minEntries) throw ListRejectedException("only ${seen.size} entries")
        return ArrayList(seen)
    }

    fun isHostName(name: String): Boolean {
        if (name.length !in 4..253 || !name.contains('.') || ipv4.matches(name)) return false
        if (name == "localhost" || name.endsWith(".localhost") || name.endsWith(".local")) return false
        val labels = name.split('.')
        return labels.all { hostLabel.matches(it) } && labels.last().any { it.isLetter() }
    }
}

class ThreatListAgent(
    val directory: File,
    val sources: List<PlainListSource>,
    private val fetcher: ConditionalFetcher = HttpsListFetcher(),
    private val startDelayMs: Long = 12_000L,
    private val minCheckIntervalSeconds: Long = 15 * 60L,
    private val intervalSeconds: Long = 6 * 3600L,
    private val retrySeconds: Long = 3600L,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    /** Receives every list in use: once after loading the saved lists and after every change. */
    private val onLists: (List<PlainList>) -> Unit,
) {
    init {
        require(sources.map { it.id }.toSet().size == sources.size) { "duplicate list id" }
    }

    private val lock = Any()
    private var executor: ScheduledExecutorService? = null
    private var scheduled: ScheduledFuture<*>? = null
    private val random = java.security.SecureRandom()

    @Volatile var lists: List<PlainList> = emptyList()
        private set
    @Volatile var lastError: String = ""
        private set

    private class Meta(val sha256: String, val count: Int, val fetchedAt: Long, val etag: String?, val lastModified: String?)

    private val validators = HashMap<String, Meta>()

    private fun listFile(id: String) = File(directory, "$id.list")
    private fun metaFile(id: String) = File(directory, "$id.meta")
    private val stateFile get() = File(directory, "agent.state")

    /** Starts the agent on a background thread and returns immediately. */
    @Synchronized
    fun start(): Boolean {
        if (executor != null) return false
        val service = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "safeer-threat-lists").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
        executor = service
        service.execute { if (loadSaved().isNotEmpty()) notifyLists() }
        schedule(startDelayMs, periodic = false)
        return true
    }

    @Synchronized
    fun stop() {
        scheduled?.cancel(false)
        executor?.shutdownNow()
        executor = null
        scheduled = null
    }

    /** Immediate check that ignores the restart throttle (for an "update lists" button). */
    @Synchronized
    fun requestUpdate(callback: ((changedLists: Int) -> Unit)? = null): Boolean {
        val service = executor ?: return false
        service.execute {
            val changed = try { refresh(force = true) } catch (e: Exception) { 0 }
            try { callback?.invoke(changed) } catch (e: Exception) { /* ignore */ }
        }
        return true
    }

    /** Loads the lists saved by an earlier run; damaged files are ignored (and downloaded again). */
    fun loadSaved(): List<PlainList> = synchronized(lock) {
        val loaded = ArrayList<PlainList>()
        for (source in sources) {
            val meta = readMeta(source.id) ?: continue
            try {
                val bytes = listFile(source.id).readBytes()
                if (sha256(bytes) != meta.sha256) throw IOException("checksum mismatch")
                val entries = if (bytes.isEmpty()) emptyList() else String(bytes, Charsets.UTF_8).split('\n')
                if (entries.size != meta.count) throw IOException("entry count mismatch")
                loaded.add(PlainList(source, entries, meta.fetchedAt))
                validators[source.id] = meta
            } catch (e: Exception) {
                validators.remove(source.id)
            } catch (e: OutOfMemoryError) {
                validators.remove(source.id)
            }
        }
        lists = loaded
        loaded
    }

    /** Checks every list; returns how many changed. Skipped within the restart throttle unless forced. */
    fun refresh(force: Boolean = false): Int = synchronized(lock) {
        val now = clock()
        if (!force && now - lastCheck() in 0 until minCheckIntervalSeconds && lists.size == sources.size) return 0
        val current = lists.associateBy { it.source.id }.toMutableMap()
        val errors = ArrayList<String>()
        var changed = 0
        for (source in sources) {
            val known = validators[source.id]?.takeIf { current.containsKey(source.id) }
            try {
                val response = fetcher.fetch(source.url, source.maxBytes, known?.etag, known?.lastModified)
                if (response.notModified) {
                    if (known == null) throw IOException("304 without a saved list")
                    continue
                }
                val entries = PlainListParser.parse(response.body ?: throw IOException("empty body"), source)
                val bytes = entries.joinToString("\n").toByteArray(Charsets.UTF_8)
                val meta = Meta(sha256(bytes), entries.size, now, response.etag?.take(200), response.lastModified?.take(100))
                if (known != null && known.sha256 == meta.sha256) {
                    writeMeta(source.id, meta) // same content, new validators
                    validators[source.id] = meta
                    continue
                }
                ensureDirectory()
                atomicWrite(listFile(source.id), bytes)
                writeMeta(source.id, meta)
                validators[source.id] = meta
                current[source.id] = PlainList(source, entries, now)
                changed++
            } catch (e: Exception) {
                errors.add("${source.id}: ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: OutOfMemoryError) {
                errors.add("${source.id}: out of memory")
            }
        }
        lastError = errors.joinToString("; ")
        try {
            ensureDirectory()
            atomicWrite(stateFile, "lastCheck=$now\n".toByteArray(Charsets.US_ASCII))
        } catch (e: IOException) { /* throttle is best effort */ }
        if (changed > 0) {
            lists = sources.mapNotNull { current[it.id] }
            notifyLists()
        }
        changed
    }

    fun lastCheck(): Long = try {
        stateFile.readText(Charsets.US_ASCII).trim().removePrefix("lastCheck=").toLong()
    } catch (e: Exception) {
        0L
    }

    private fun notifyLists() {
        try { onLists(lists) } catch (e: Exception) { /* a listener must not stop the agent */ }
    }

    @Synchronized
    private fun schedule(delayMs: Long, periodic: Boolean) {
        val service = executor ?: return
        val jitter = if (periodic) 0.9 + random.nextDouble() * 0.2 else 1.0
        scheduled = service.schedule({
            try { refresh(force = false) } catch (e: Exception) { /* keep lists in use */ }
            schedule((if (lastError.isEmpty()) intervalSeconds else retrySeconds) * 1000, periodic = true)
        }, maxOf(0L, (delayMs * jitter).toLong()), TimeUnit.MILLISECONDS)
    }

    private fun readMeta(id: String): Meta? {
        return try {
            val values = metaFile(id).readLines(Charsets.UTF_8).filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            Meta(
                values["sha256"] ?: return null, values["count"]?.toInt() ?: return null, values["fetchedAt"]?.toLong() ?: 0L,
                values["etag"]?.takeIf { it.isNotEmpty() }, values["lastModified"]?.takeIf { it.isNotEmpty() },
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun writeMeta(id: String, meta: Meta) {
        ensureDirectory()
        val clean = { value: String? -> (value ?: "").replace("\n", "").replace("\r", "") }
        val text = "sha256=${meta.sha256}\ncount=${meta.count}\nfetchedAt=${meta.fetchedAt}\n" +
            "etag=${clean(meta.etag)}\nlastModified=${clean(meta.lastModified)}\n"
        atomicWrite(metaFile(id), text.toByteArray(Charsets.UTF_8))
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create ${directory.name}")
    }

    private fun atomicWrite(target: File, data: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(data)
            out.flush()
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
