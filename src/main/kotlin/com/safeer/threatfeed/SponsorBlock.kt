/*
 * Safeer SponsorBlock client for Android and Android TV (pure JVM, no Android imports).
 *
 * Skips sponsor segments in YouTube videos using the community database at sponsor.ajay.app
 * (https://sponsor.ajay.app, data licensed CC BY-NC-SA 4.0). Privacy: the browser never sends a
 * video ID. It asks for the first 4 hex digits of the SHA-256 of the ID (the "k-anonymity" API of
 * SponsorBlock), receives the segments of every video sharing that prefix and picks its own locally.
 * Requests are anonymous GETs without cookies; nothing else about the viewer leaves the device.
 *
 * The same file is used unchanged by Safeer Browser for Android and Android TV.
 */
package com.safeer.threatfeed

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/** One segment to skip: seconds from [start] to [end]; [category] is sponsor, selfpromo or interaction. */
data class SponsorSegment(val start: Double, val end: Double, val category: String)

fun interface SponsorFetcher {
    /** Body of the API answer for the hash prefix, or null for "no segments" (HTTP 404). */
    @Throws(IOException::class)
    fun fetch(url: String): String?
}

object SponsorBlock {
    const val API = "https://sponsor.ajay.app/api/skipSegments"
    /** Skipped automatically; other categories (intro, outro, music...) stay untouched. */
    val CATEGORIES = listOf("sponsor", "selfpromo", "interaction")
    private const val MAX_CACHE = 64
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    private val cache = object : LinkedHashMap<String, List<SponsorSegment>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>?): Boolean = size > MAX_CACHE
    }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "safeer-sponsorblock").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    @Volatile var fetcher: SponsorFetcher = HttpsSponsorFetcher()
    /** Why the last lookup delivered nothing (network error, HTTP status), for diagnostics; empty when it succeeded. */
    @Volatile var lastError: String = ""

    /** YouTube video ID from a watch, embed, shorts or youtu.be address (also inside the #fragment of youtube.com/tv). */
    fun videoIdFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val lower = url.lowercase()
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) return null
        val query = Regex("[?&#/]v=([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])").find(url)
        if (query != null) return query.groupValues[1]
        val path = Regex("(?:youtu\\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])").find(url)
        return path?.groupValues?.get(1)
    }

    fun hashPrefix(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 4)
    }

    fun apiUrl(videoId: String): String {
        val categories = CATEGORIES.joinToString(",", "[", "]") { "\"$it\"" }
        return API + "/" + hashPrefix(videoId) + "?categories=" + URLEncoder.encode(categories, "UTF-8") +
            "&actionTypes=" + URLEncoder.encode("[\"skip\"]", "UTF-8")
    }

    /** Segments of [videoId] from the API body of its hash prefix; malformed input gives an empty list. */
    fun parse(body: String?, videoId: String): List<SponsorSegment> {
        if (body.isNullOrBlank()) return emptyList()
        val root = try { LenientJson.parse(body) } catch (e: IllegalArgumentException) { return emptyList() }
        val videos = root as? List<*> ?: return emptyList()
        val result = ArrayList<SponsorSegment>()
        for (video in videos) {
            val map = video as? Map<*, *> ?: continue
            if (map["videoID"] != videoId) continue
            val segments = map["segments"] as? List<*> ?: continue
            for (item in segments) {
                val segment = item as? Map<*, *> ?: continue
                if ((segment["actionType"] as? String ?: "skip") != "skip") continue
                val category = segment["category"] as? String ?: continue
                if (category !in CATEGORIES) continue
                val bounds = segment["segment"] as? List<*> ?: continue
                val start = (bounds.getOrNull(0) as? Double) ?: continue
                val end = (bounds.getOrNull(1) as? Double) ?: continue
                if (end - start < 0.5 || start < 0) continue
                result.add(SponsorSegment(start, end, category))
            }
        }
        result.sortBy { it.start }
        return result
    }

    /** Blocking lookup with a cache (also of "no segments"); network errors give an empty list and are not cached. */
    fun segmentsFor(videoId: String): List<SponsorSegment> {
        if (!videoIdPattern.matches(videoId)) return emptyList()
        synchronized(cache) { cache[videoId]?.let { return it } }
        val segments = try {
            val body = fetcher.fetch(apiUrl(videoId))
            val parsed = parse(body, videoId)
            lastError = when {
                body == null -> "HTTP 404 (za to predpono ni odsekov)"
                parsed.isEmpty() && !body.contains(videoId) -> "v odgovoru (${body.length} B) ni tega videa"
                else -> ""
            }
            parsed
        } catch (e: IOException) {
            lastError = "IOException: ${e.message}"
            return emptyList()
        } catch (e: RuntimeException) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            return emptyList()
        }
        synchronized(cache) { cache[videoId] = segments }
        return segments
    }

    /** Lookup on the background thread; [onResult] runs on that thread too. */
    fun fetchAsync(videoId: String, onResult: (List<SponsorSegment>) -> Unit) {
        if (!videoIdPattern.matches(videoId)) return
        executor.execute {
            val segments = segmentsFor(videoId)
            try { onResult(segments) } catch (e: RuntimeException) { /* the page is gone */ }
        }
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    /** JavaScript that hands the segments to the page runtime (see [RUNTIME_JS]). */
    fun applyScript(videoId: String, segments: List<SponsorSegment>): String {
        require(videoIdPattern.matches(videoId))
        val list = segments.joinToString(",", "[", "]") { "[${it.start},${it.end},\"${it.category}\"]" }
        return "window.__safeerSb && window.__safeerSb.set(\"$videoId\", $list);"
    }

    /**
     * Page runtime: watches the address for the current video, asks the app for its segments through
     * SafeerBridge.sponsorSegments(id) and jumps over every segment while the video plays. Idempotent.
     */
    const val RUNTIME_JS: String = """
(function () {
  if (window.__safeerSb) return;
  var LABEL = { sponsor: "Sponzor preskočen", selfpromo: "Samopromocija preskočena", interaction: "Poziv preskočen" };
  var state = { id: null, segments: [], done: {}, video: null, asked: 0, answered: false };
  var toast = null, toastTimer = null;
  function showToast(text) {
    try {
      if (!toast) {
        toast = document.createElement("div");
        toast.setAttribute("style", "position:fixed;left:50%;bottom:12%;transform:translateX(-50%);z-index:2147483647;" +
          "background:rgba(15,23,42,.92);color:#e2e8f0;font:600 15px/1.2 system-ui,sans-serif;padding:10px 16px;border-radius:10px;" +
          "box-shadow:0 4px 18px rgba(0,0,0,.4);pointer-events:none;opacity:0;transition:opacity .25s");
        (document.body || document.documentElement).appendChild(toast);
      }
      toast.textContent = "⏭ " + text;
      toast.style.opacity = "1";
      clearTimeout(toastTimer);
      toastTimer = setTimeout(function () { toast.style.opacity = "0"; }, 1800);
    } catch (e) {}
  }
  function videoId() {
    var h = location.href || "";
    var m = h.match(/[?&#\/]v=([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])/) || h.match(/(?:youtu\.be\/|\/embed\/|\/shorts\/|\/live\/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])/);
    return m ? m[1] : null;
  }
  function mainVideo() {
    var list = document.querySelectorAll("video");
    var best = null;
    for (var i = 0; i < list.length; i++) {
      var v = list[i];
      if (!best || (v.readyState > 0 && v.getBoundingClientRect().width > best.getBoundingClientRect().width)) best = v;
    }
    return best;
  }
  function onTime() {
    var v = state.video;
    if (!v || !state.segments.length || v.paused || v.seeking) return;
    var t = v.currentTime;
    for (var i = 0; i < state.segments.length; i++) {
      var s = state.segments[i];
      if (t >= s[0] && t < s[1] - 0.25 && !state.done[i]) {
        if (v.duration && s[1] >= v.duration - 0.5) { state.done[i] = true; return; }
        state.done[i] = true;
        v.currentTime = s[1];
        showToast(LABEL[s[2]] || LABEL.sponsor);
        return;
      }
    }
  }
  function attach() {
    var v = mainVideo();
    if (v === state.video) return;
    if (state.video) { try { state.video.removeEventListener("timeupdate", onTime); } catch (e) {} }
    state.video = v;
    if (v) v.addEventListener("timeupdate", onTime);
  }
  function ask() {
    if (!state.id || state.answered || state.asked >= 3) return;
    state.asked++;
    try { if (window.SafeerBridge && window.SafeerBridge.sponsorSegments) window.SafeerBridge.sponsorSegments(state.id); } catch (e) {}
  }
  var ticks = 0;
  function tick() {
    var id = videoId();
    if (id !== state.id) {
      state.id = id; state.segments = []; state.done = {}; state.asked = 0; state.answered = false;
      ask();
    } else if (ticks % 5 === 0) {
      ask(); // the app answers once; retry only while there is no answer yet
    }
    ticks++;
    attach();
  }
  window.__safeerSb = {
    set: function (id, segments) {
      if (id !== state.id) return;
      state.answered = true;
      state.segments = Array.isArray(segments) ? segments : [];
      state.done = {};
      attach();
    },
    state: function () { return { id: state.id, segments: state.segments.length }; }
  };
  setInterval(tick, 1000);
  tick();
})();
"""
}

/** Anonymous HTTPS GET: no cookies, hard limits, 404 = no segments. */
class HttpsSponsorFetcher(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
    private val allowPlainHttpForTests: Boolean = false,
) : SponsorFetcher {
    override fun fetch(url: String): String? {
        if (!url.startsWith("https://") && !allowPlainHttpForTests) throw IOException("only HTTPS is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", "Safeer")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status == 404) return null
            if (status != 200) throw IOException("HTTP status $status")
            val out = ByteArrayOutputStream(16 * 1024)
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > MAX_SPONSOR_BODY) throw IOException("response too large")
                    out.write(buffer, 0, read)
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }
}

private const val MAX_SPONSOR_BODY = 512 * 1024
