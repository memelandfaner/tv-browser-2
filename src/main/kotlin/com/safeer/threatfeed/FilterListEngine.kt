/*
 * Safeer filter-list engine for Android and Android TV (pure JVM, no Android imports).
 *
 * Network rules in the Adblock Plus / uBlock Origin syntax used by EasyList: ||host^ anchors, |start and
 * end| anchors, * wildcards, ^ separators, /regex/ patterns, @@ exceptions and the options third-party,
 * domain=, the resource types, document, important. Cosmetic rules (##) and rules with options this
 * engine does not implement (redirect, csp, removeparam, rewrite ...) are skipped, never guessed.
 *
 * Matching is indexed so that one request costs a few hash lookups: ||host rules by host name (walking the
 * request host's parent domains), all other rules by their longest alphanumeric token. Compiled sets are
 * immutable and can be swapped atomically while requests are being checked on other threads.
 *
 * The same file is used unchanged by Safeer Browser for Android and Android TV.
 */
package com.safeer.threatfeed

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class ResourceType(val mask: Int, val option: String) {
    DOCUMENT(1, "document"), SUBDOCUMENT(2, "subdocument"), SCRIPT(4, "script"), IMAGE(8, "image"),
    STYLESHEET(16, "stylesheet"), FONT(32, "font"), MEDIA(64, "media"), XHR(128, "xmlhttprequest"),
    OBJECT(256, "object"), PING(512, "ping"), WEBSOCKET(1024, "websocket"), OTHER(2048, "other");

    companion object {
        val ALL_MASK = values().sumOf { it.mask }
        /** Types a rule without a type option applies to (everything but the page itself). */
        val DEFAULT_MASK = ALL_MASK and DOCUMENT.mask.inv()
        private val byOption = values().associateBy { it.option } + mapOf(
            "xhr" to XHR, "css" to STYLESHEET, "frame" to SUBDOCUMENT, "doc" to DOCUMENT,
        )
        fun fromOption(name: String): ResourceType? = byOption[name]

        /** Best guess from what an Android WebView request carries: the Accept header and the address. */
        fun guess(url: String, accept: String?, isMainFrame: Boolean): ResourceType {
            if (isMainFrame) return DOCUMENT
            val a = accept?.lowercase() ?: ""
            if (a.startsWith("image/")) return IMAGE
            if (a.startsWith("text/css")) return STYLESHEET
            if (a.startsWith("text/html")) return SUBDOCUMENT
            if (a.startsWith("video/") || a.startsWith("audio/")) return MEDIA
            if (a.contains("font")) return FONT
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            val ext = path.substringAfterLast('/').substringAfterLast('.', "")
            return when (ext) {
                "js", "mjs" -> SCRIPT
                "css" -> STYLESHEET
                "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp" -> IMAGE
                "woff", "woff2", "ttf", "otf", "eot" -> FONT
                "mp4", "webm", "m3u8", "ts", "mp3", "m4a", "ogg", "aac", "mpd", "m4s" -> MEDIA
                "json", "xml" -> XHR
                "html", "htm" -> SUBDOCUMENT
                "swf" -> OBJECT
                else -> if (a == "*/*" || a.isEmpty()) OTHER else XHR
            }
        }
    }
}

class FilterRequest(
    val url: String,
    /** Address of the page that makes the request (null for the page itself). */
    val pageUrl: String?,
    val type: ResourceType,
) {
    val lowerUrl: String = url.lowercase()
    val host: String = FilterListEngine.hostOf(lowerUrl)
    val pageHost: String = FilterListEngine.hostOf(pageUrl?.lowercase() ?: "")
    val thirdParty: Boolean = pageHost.isNotEmpty() && FilterListEngine.registrable(host) != FilterListEngine.registrable(pageHost)
}

/** One parsed network rule. */
class NetworkFilter internal constructor(
    val text: String,
    val exception: Boolean,
    val important: Boolean,
    internal val typeMask: Int,
    /** null = both, true = third-party only, false = first-party only */
    internal val thirdParty: Boolean?,
    internal val domains: List<String>,
    internal val excludedDomains: List<String>,
    /** ||host^ rule without a path: matched by host name only */
    internal val hostAnchor: String?,
    internal val pattern: Pattern?,
    internal val token: String?,
) {
    fun matches(request: FilterRequest): Boolean {
        if (typeMask and request.type.mask == 0) return false
        if (thirdParty != null && thirdParty != request.thirdParty) return false
        if (domains.isNotEmpty() || excludedDomains.isNotEmpty()) {
            // domain= refers to the page; for the page itself (document) it refers to the page being opened
            val site = if (request.pageHost.isEmpty()) request.host else request.pageHost
            if (excludedDomains.any { FilterListEngine.under(site, it) }) return false
            if (domains.isNotEmpty() && domains.none { FilterListEngine.under(site, it) }) return false
        }
        if (hostAnchor != null) return FilterListEngine.under(request.host, hostAnchor)
        return pattern != null && pattern.matcher(request.lowerUrl).find()
    }
}

class FilterDecision(val block: Boolean, val filter: NetworkFilter?)

/** An immutable, indexed set of network rules. */
class FilterSet internal constructor(
    private val hostBlocks: Map<String, List<NetworkFilter>>,
    private val hostExceptions: Map<String, List<NetworkFilter>>,
    private val tokenBlocks: Map<String, List<NetworkFilter>>,
    private val tokenExceptions: Map<String, List<NetworkFilter>>,
    private val untokenizedBlocks: List<NetworkFilter>,
    private val untokenizedExceptions: List<NetworkFilter>,
    val size: Int,
    val skipped: Int,
) {
    companion object {
        val EMPTY = FilterSet(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList(), 0, 0)
    }

    private fun candidates(request: FilterRequest, byHost: Map<String, List<NetworkFilter>>, byToken: Map<String, List<NetworkFilter>>,
                           rest: List<NetworkFilter>, visit: (NetworkFilter) -> Boolean): NetworkFilter? {
        var host = request.host
        while (host.isNotEmpty()) {
            byHost[host]?.forEach { if (visit(it)) return it }
            val dot = host.indexOf('.')
            host = if (dot < 0) "" else host.substring(dot + 1)
        }
        for (token in FilterListEngine.tokens(request.lowerUrl)) {
            byToken[token]?.forEach { if (visit(it)) return it }
        }
        rest.forEach { if (visit(it)) return it }
        return null
    }

    /** The exception (`@@`) that matches, or null. */
    fun exceptionFor(request: FilterRequest): NetworkFilter? =
        candidates(request, hostExceptions, tokenExceptions, untokenizedExceptions) { it.matches(request) }

    /** Block/allow decision for one request; null when no rule applies. */
    fun decide(request: FilterRequest): FilterDecision? {
        val block = candidates(request, hostBlocks, tokenBlocks, untokenizedBlocks) { it.matches(request) } ?: return null
        if (block.important) return FilterDecision(true, block)
        val exception = exceptionFor(request)
        return if (exception != null) FilterDecision(false, exception) else FilterDecision(true, block)
    }

    /** True when an `@@...$document` exception covers the whole page (all its requests are then allowed). */
    fun isPageAllowed(pageUrl: String): Boolean {
        val request = FilterRequest(pageUrl, null, ResourceType.DOCUMENT)
        return exceptionFor(request) != null
    }
}

object FilterListEngine {
    private const val MAX_REGEX_RULES = 2000
    private val tokenPattern = Regex("[a-z0-9]{3,}")
    private val hostPattern = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    private val twoPartSuffixes = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk", "net.uk", "co.jp", "ne.jp", "or.jp", "com.au", "net.au", "org.au", "co.nz",
        "com.br", "com.mx", "com.ar", "com.tr", "com.cn", "com.hk", "com.tw", "co.kr", "co.in", "co.za", "com.sg", "com.my",
        "co.id", "com.ph", "com.vn", "com.ua", "com.pl", "co.il", "com.eg", "com.sa", "com.ng", "co.ke", "com.pk",
        "github.io", "gitlab.io", "pages.dev", "web.app", "firebaseapp.com", "herokuapp.com", "vercel.app", "netlify.app",
        "blogspot.com", "wordpress.com", "cloudfront.net", "amazonaws.com", "azurewebsites.net", "duckdns.org", "no-ip.org",
    )

    fun hostOf(lowerUrl: String): String {
        val schemeEnd = lowerUrl.indexOf("://")
        if (schemeEnd < 0) return ""
        var start = schemeEnd + 3
        val at = lowerUrl.indexOf('@', start)
        val slash = lowerUrl.indexOf('/', start).let { if (it < 0) lowerUrl.length else it }
        if (at in start until slash) start = at + 1
        var end = slash
        val q = lowerUrl.indexOf('?', start).let { if (it < 0) lowerUrl.length else it }
        if (q < end) end = q
        val hash = lowerUrl.indexOf('#', start).let { if (it < 0) lowerUrl.length else it }
        if (hash < end) end = hash
        var host = lowerUrl.substring(start, end)
        if (host.startsWith("[")) return host.substringBefore(']').removePrefix("[")
        val colon = host.lastIndexOf(':')
        if (colon >= 0) host = host.substring(0, colon)
        return host.trimEnd('.')
    }

    /** eTLD+1 with a small built-in suffix list: enough to tell first party from third party. */
    fun registrable(host: String): String {
        if (host.isEmpty()) return host
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val lastTwo = parts[parts.size - 2] + "." + parts[parts.size - 1]
        return if (lastTwo in twoPartSuffixes && parts.size >= 3) parts[parts.size - 3] + "." + lastTwo else lastTwo
    }

    fun under(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

    fun tokens(lowerUrl: String): Sequence<String> = tokenPattern.findAll(lowerUrl).map { it.value }

    /** Parses one rule; null for comments, cosmetic rules and rules with unsupported options. */
    fun parse(rawLine: String, regexBudget: IntArray = intArrayOf(MAX_REGEX_RULES)): NetworkFilter? {
        var line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#\$#") || line.contains("#%#")) return null
        val exception = line.startsWith("@@")
        if (exception) line = line.substring(2)
        var typeMask = 0
        var negatedTypes = 0
        var thirdParty: Boolean? = null
        var important = false
        val domains = ArrayList<String>()
        val excluded = ArrayList<String>()
        var pattern = line
        val isRegex = line.length > 2 && line.startsWith("/") && (line.endsWith("/") || line.contains("/$"))
        val dollar = if (isRegex) line.lastIndexOf("/$").let { if (it > 0) it + 1 else -1 } else line.lastIndexOf('$')
        if (dollar > 0) {
            pattern = line.substring(0, dollar)
            for (option in line.substring(dollar + 1).split(',')) {
                val negated = option.startsWith("~")
                val name = option.removePrefix("~").lowercase()
                when {
                    name == "third-party" || name == "3p" -> thirdParty = !negated
                    name == "first-party" || name == "1p" -> thirdParty = negated
                    name == "important" -> important = true
                    name == "match-case" || name == "generichide" || name == "genericblock" || name == "elemhide" ||
                        name == "ghide" || name == "ehide" -> { /* cosmetic hints: nothing to do for network rules */ }
                    name.startsWith("domain=") -> for (d in name.substring(7).split('|')) {
                        if (d.startsWith("~")) excluded.add(d.substring(1)) else if (d.isNotEmpty()) domains.add(d)
                    }
                    ResourceType.fromOption(name) != null -> {
                        val type = ResourceType.fromOption(name)!!
                        if (negated) negatedTypes = negatedTypes or type.mask else typeMask = typeMask or type.mask
                    }
                    else -> return null // redirect=, csp=, removeparam=, rewrite=, header=, ping etc.: not implemented
                }
            }
        }
        if (typeMask == 0) typeMask = ResourceType.DEFAULT_MASK
        typeMask = typeMask and negatedTypes.inv()
        if (typeMask == 0 || pattern.isEmpty()) return null
        if (isRegex) {
            if (regexBudget[0] <= 0) return null
            val compiled = try { Pattern.compile(pattern.substring(1, pattern.length - 1), Pattern.CASE_INSENSITIVE) } catch (e: PatternSyntaxException) { return null }
            regexBudget[0]--
            return NetworkFilter(rawLine, exception, important, typeMask, thirdParty, domains, excluded, null, compiled, null)
        }
        val lower = pattern.lowercase()
        // ||host^ (or ||host, ||host/, ||host^$...) with nothing else: host-name rule
        if (lower.startsWith("||")) {
            val rest = lower.substring(2).removeSuffix("^").removeSuffix("/")
            if (hostPattern.matches(rest) && !rest.contains('*')) {
                return NetworkFilter(rawLine, exception, important, typeMask, thirdParty, domains, excluded, rest, null, null)
            }
        }
        val regex = toRegex(lower) ?: return null
        val compiled = try { Pattern.compile(regex) } catch (e: PatternSyntaxException) { return null }
        val token = bestToken(lower)
        return NetworkFilter(rawLine, exception, important, typeMask, thirdParty, domains, excluded, null, compiled, token)
    }

    /** ABP pattern -> regular expression over the lower-case URL. */
    internal fun toRegex(pattern: String): String? {
        var p = pattern
        val sb = StringBuilder()
        if (p.startsWith("||")) { sb.append("^[a-z][a-z0-9+.-]*://([^/?#]*\\.)?"); p = p.substring(2) }
        else if (p.startsWith("|")) { sb.append("^"); p = p.substring(1) }
        var endAnchor = false
        if (p.endsWith("|")) { endAnchor = true; p = p.dropLast(1) }
        if (p == "*" || p.isEmpty()) return if (sb.isEmpty() && !endAnchor) null else sb.append(".*").append(if (endAnchor) "$" else "").toString()
        for (ch in p) {
            when (ch) {
                '*' -> sb.append(".*")
                '^' -> sb.append("(?:[^a-z0-9_.%-]|$)")
                '.', '+', '?', '(', ')', '[', ']', '{', '}', '\\', '$', '|' -> sb.append('\\').append(ch)
                else -> sb.append(ch)
            }
        }
        if (endAnchor) sb.append("$")
        return sb.toString()
    }

    /**
     * Longest alphanumeric run of the pattern that appears as a whole run in every matching URL: it must be
     * bounded on both sides by a separator inside the pattern (or by an anchor), never by a wildcard or the
     * unanchored pattern edge, where the URL may continue the run (pattern `ads.js` matches `myads.js`).
     */
    internal fun bestToken(pattern: String): String? {
        val startAnchored = pattern.startsWith("|")
        val endAnchored = pattern.endsWith("|")
        val body = pattern.removePrefix("||").removePrefix("|").removeSuffix("|")
        var best: String? = null
        for (match in tokenPattern.findAll(body)) {
            val before = body.getOrNull(match.range.first - 1)
            val after = body.getOrNull(match.range.last + 1)
            val boundedBefore = if (before == null) startAnchored else before != '*'
            val boundedAfter = if (after == null) endAnchored else after != '*'
            if (!boundedBefore || !boundedAfter) continue
            if (best == null || match.value.length > best.length) best = match.value
        }
        return best
    }

    /** Compiles rule lines into an indexed set. */
    fun compile(lines: Iterable<String>): FilterSet {
        val hostBlocks = HashMap<String, MutableList<NetworkFilter>>()
        val hostExceptions = HashMap<String, MutableList<NetworkFilter>>()
        val tokenBlocks = HashMap<String, MutableList<NetworkFilter>>()
        val tokenExceptions = HashMap<String, MutableList<NetworkFilter>>()
        val restBlocks = ArrayList<NetworkFilter>()
        val restExceptions = ArrayList<NetworkFilter>()
        var size = 0
        var skipped = 0
        val budget = intArrayOf(MAX_REGEX_RULES)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) continue
            val filter = parse(trimmed, budget)
            if (filter == null) { skipped++; continue }
            size++
            when {
                filter.hostAnchor != null -> (if (filter.exception) hostExceptions else hostBlocks).getOrPut(filter.hostAnchor) { ArrayList(1) }.add(filter)
                filter.token != null -> (if (filter.exception) tokenExceptions else tokenBlocks).getOrPut(filter.token) { ArrayList(1) }.add(filter)
                else -> (if (filter.exception) restExceptions else restBlocks).add(filter)
            }
        }
        return FilterSet(hostBlocks, hostExceptions, tokenBlocks, tokenExceptions, restBlocks, restExceptions, size, skipped)
    }
}
