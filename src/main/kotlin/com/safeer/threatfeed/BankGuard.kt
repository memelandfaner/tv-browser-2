/*
 * Safeer BankGuard for Android and Android TV (pure JVM): real banks stay untouched, fake banks get
 * a warning. Kotlin port of clients/python/safeer_bank_guard.py; both run clients/banks/cases.json.
 * Everything runs on the device; no page content or address leaves it.
 */
package com.safeer.threatfeed

import java.net.IDN
import java.text.Normalizer

data class Bank(
    val id: String,
    val name: String,
    val official: List<String>,
    val family: List<String>,
    val names: List<String>,
    val tokens: List<String>,
)

data class BankVerdict(val bankId: String, val bankName: String, val officialDomain: String, val reason: String, val detail: String)

/** What the page script reports; only pages with credential fields carry the text fields. */
data class PageSignals(
    val host: String,
    val scheme: String = "https",
    val password: Boolean = false,
    val otp: Boolean = false,
    val card: Boolean = false,
    /** Slovenian tax number (davčna številka) or card PIN field: real banks never ask for these on a login page. */
    val taxid: Boolean = false,
    val pin: Boolean = false,
    val title: String = "",
    val site: String = "",
    val headings: String = "",
    val logos: String = "",
    val text: String = "",
    /** News article or blog post (og:type, schema.org type): never treated as a login page. */
    val article: Boolean = false,
)

object BankGuard {
    /** JavaScript that returns page signals as an object; evaluate in the main frame after loading. */
    val PAGE_SCRIPT: String get() = BankGuardData.PAGE_SCRIPT

    private val banks = BankGuardData.BANKS
    private val trusted: Set<String> = HashSet(banks.flatMap { it.official + it.family } + BankGuardData.INFRASTRUCTURE)
    private val officialLabels: Map<String, Bank> = LinkedHashMap<String, Bank>().apply {
        for (bank in banks) for (domain in bank.official) putIfAbsent(domain.substringBefore('.').replace("-", ""), bank)
    }
    private val paymentPhrases = BankGuardData.PAYMENT_PHRASES.map { fold(it) }
    private val lurePatterns = BankGuardData.LURE_PHRASES.map { lurePattern(fold(it)) }
    private val embeddedContext = listOf("klik", "banka", "bank", "hranilnica")
    private val separators = Regex("[.\\-_]")
    private val numericHost = Regex("[0-9.:]+")
    private val confusables: Map<Char, Char> = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y', 'і' to 'i', 'ј' to 'j', 'ѕ' to 's',
        'ԁ' to 'd', 'ӏ' to 'l', 'ı' to 'i', 'ɩ' to 'l', 'ο' to 'o', 'ν' to 'v', 'κ' to 'k', 'ρ' to 'p', 'τ' to 't', 'α' to 'a',
        '0' to 'o', '1' to 'l', '3' to 'e', '5' to 's', '@' to 'a',
    )

    fun fold(text: String?): String =
        Normalizer.normalize((text ?: "").lowercase(), Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")

    private fun skeleton(text: String): String = String(text.map { confusables[it] ?: it }.toCharArray())

    private fun cleanHost(host: String?): String {
        var h = (host ?: "").trim().lowercase().trimEnd('.')
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        if (h.any { it.code > 127 }) {
            // internationalized names reported in Unicode; the rules work on the xn-- form
            h = try { IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).lowercase() } catch (e: IllegalArgumentException) { h }
        }
        return h
    }

    private fun under(host: String, domains: List<String>) = domains.any { host == it || host.endsWith(".$it") }

    /** The bank when host is one of its own domains or a subdomain of one. */
    fun officialBank(host: String?): Bank? {
        val h = cleanHost(host)
        return banks.firstOrNull { bank -> bank.official.any { h == it || h.endsWith(".$it") } }
    }

    /** Official bank domains, bank group domains and payment/identity infrastructure. */
    fun isTrusted(host: String?): Boolean {
        // Host and each parent domain against a set: cheap enough for every request.
        var h = cleanHost(host)
        while (h.isNotEmpty()) {
            if (h in trusted) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
        return false
    }

    private fun verdict(bank: Bank, reason: String, detail: String) = BankVerdict(bank.id, bank.name, bank.official.first(), reason, detail)

    fun hostVerdict(rawHost: String?): BankVerdict? {
        val host = cleanHost(rawHost)
        if (host.isEmpty() || !host.contains('.') || isTrusted(host) || numericHost.matches(host)) return null
        val labels = host.split('.')
        val tokens = host.split(separators).filter { it.isNotEmpty() }
        val context = labels.dropLast(1).joinToString(".").split(separators).filter { it in BankGuardData.CONTEXT_TOKENS }.toSortedSet()
        for (bank in banks) {
            val hit = bank.tokens.firstOrNull { it in tokens }
            if (hit != null && context.isNotEmpty()) return verdict(bank, "lookalike", "'$hit' with '${context.first()}'")
        }
        for (label in labels.dropLast(1)) {
            if (!label.startsWith("xn--")) continue
            val unicodeLabel = try { IDN.toUnicode(label, IDN.ALLOW_UNASSIGNED) } catch (e: IllegalArgumentException) { continue }
            if (unicodeLabel == label) continue
            val sk = skeleton(fold(unicodeLabel))
            val parts = sk.split('-', '_').filter { it.isNotEmpty() }.toSet()
            var bank = officialLabels[sk.replace("-", "")]
            if (bank == null && (parts.any { it in BankGuardData.CONTEXT_TOKENS } || context.isNotEmpty())) {
                // a whole word of the label is a bank name next to banking words (not a substring)
                bank = banks.firstOrNull { b -> b.tokens.any { it.length >= 4 && it in parts } }
            }
            if (bank != null) return verdict(bank, "homoglyph", unicodeLabel)
        }
        for (bank in banks) {
            val product = bank.tokens.firstOrNull { t -> t in tokens && t !in officialLabels && embeddedContext.any { t.contains(it) } }
            if (product != null) return verdict(bank, "lookalike", "'$product'")
        }
        val registrable = if (labels.size >= 2) labels[labels.size - 2] else labels[0]
        val tld = labels.last()
        val plain = registrable.replace("-", "")
        val sk = skeleton(plain)
        if (sk != plain) {
            val bank = officialLabels[sk]
            if (bank != null && (sk.length >= 4 || bank.official.any { it.endsWith(".$tld") })) return verdict(bank, "typosquat", registrable)
        }
        for ((label, bank) in officialLabels) {
            val sameTld = bank.official.any { it.substringBefore('.').replace("-", "") == label && it.endsWith(".$tld") }
            // a missing letter only counts for longer names ("revolt" is a word, "sparkase" is a typo)
            val longEnough = plain.length >= label.length || plain.length >= 7
            if (label.length >= 6 && sameTld && longEnough && damerauOne(plain, label)) return verdict(bank, "typosquat", registrable)
        }
        return null
    }

    fun pageVerdict(signals: PageSignals?): BankVerdict? {
        if (signals == null || !(signals.password || signals.otp || signals.card || signals.taxid || signals.pin)) return null
        val host = cleanHost(signals.host)
        // An HTML attachment opened from mail (file:, content:) has no host, so no domain list can help.
        val local = signals.scheme in BankGuardData.LOCAL_SCHEMES
        if (!local && ((signals.scheme != "http" && signals.scheme != "https") || host.isEmpty() || isTrusted(host))) return null
        if (!local && under(host, BankGuardData.PAGE_CHECK_SKIP)) return null // brand pages on large platforms
        if (signals.article) return null
        val pageText = fold(listOf(signals.title, signals.site, signals.headings, signals.logos, signals.text).joinToString(" "))
        if (paymentPhrases.any { phraseIn(it, pageText) }) return null
        val prominent = fold(listOf(signals.title, signals.site, signals.headings, signals.logos).joinToString(" "))
        for (bank in banks) for (name in bank.names) {
            if (phraseIn(fold(name), prominent)) return verdict(bank, if (local) "local" else "page", name)
        }
        // A card form dressed up as a fine, tax or parcel payment (police, FURS, delivery): no bank name needed.
        if (signals.card) {
            for (pattern in lurePatterns) {
                val found = pattern.find(pageText) ?: continue
                return BankVerdict("card", "Plačilna kartica", "", "lure", found.value)
            }
        }
        return null
    }

    fun check(host: String?, signals: PageSignals? = null): BankVerdict? = hostVerdict(host) ?: pageVerdict(signals)

    /**
     * Page signals from the JSON that WebView.evaluateJavascript(PAGE_SCRIPT) hands back, or null when
     * the value is not an object (script blocked, page gone). Oversized or malformed input gives null.
     */
    fun signalsFromJson(json: String?): PageSignals? {
        if (json == null || json.length > MAX_SIGNALS_JSON) return null
        val map = try { LenientJson.parse(json) as? Map<*, *> } catch (e: IllegalArgumentException) { null } ?: return null
        fun text(key: String, max: Int) = (map[key] as? String ?: "").take(max)
        fun flag(key: String) = map[key] == true
        return PageSignals(
            host = text("host", 253), scheme = text("scheme", 16), password = flag("password"), otp = flag("otp"), card = flag("card"),
            taxid = flag("taxid"), pin = flag("pin"),
            title = text("title", 200), site = text("site", 200), headings = text("headings", 250), logos = text("logos", 1100),
            text = text("text", 3000), article = flag("article"),
        )
    }

    private const val MAX_SIGNALS_JSON = 64 * 1024

    private fun phraseIn(phrase: String, text: String): Boolean {
        var index = text.indexOf(phrase)
        while (index >= 0) {
            val before = if (index == 0) ' ' else text[index - 1]
            val afterIndex = index + phrase.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isAsciiLetterOrDigit() && !after.isAsciiLetterOrDigit()) return true
            index = text.indexOf(phrase, index + 1)
        }
        return false
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in '0'..'9'

    /** Whole words; a trailing '*' in the catalogue lets a word start with the stem (kazn*, policij*). */
    private fun lurePattern(phrase: String): Regex {
        val parts = phrase.split(" ").map { word ->
            if (word.endsWith("*")) Regex.escape(word.dropLast(1)) else Regex.escape(word) + "(?![a-z0-9])"
        }
        return Regex("(?<![a-z0-9])" + parts.joinToString("\\s+"))
    }

    internal fun damerauOne(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val diff = a.indices.filter { a[it] != b[it] }
            if (diff.size == 1) return true
            return diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        }
        val (shorter, longer) = if (a.length < b.length) a to b else b to a
        return longer.indices.any { longer.removeRange(it, it + 1) == shorter }
    }
}

/** Small tolerant JSON reader (objects, arrays, strings, numbers, booleans, null) with a depth limit. */
internal object LenientJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.skip()
        require(reader.pos == text.length) { "trailing data" }
        return value
    }

    private class Reader(val s: String) {
        var pos = 0

        fun skip() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

        fun value(depth: Int): Any? {
            require(depth <= 16) { "nesting too deep" }
            skip()
            require(pos < s.length) { "unexpected end" }
            return when (s[pos]) {
                '{' -> {
                    pos++
                    val map = LinkedHashMap<String, Any?>()
                    skip()
                    if (peek('}')) return map.also { pos++ }
                    while (true) {
                        skip()
                        val key = string()
                        skip()
                        expect(':')
                        map[key] = value(depth + 1)
                        skip()
                        if (peek(',')) { pos++; continue }
                        expect('}')
                        return map
                    }
                }
                '[' -> {
                    pos++
                    val list = ArrayList<Any?>()
                    skip()
                    if (peek(']')) return list.also { pos++ }
                    while (true) {
                        list.add(value(depth + 1))
                        skip()
                        if (peek(',')) { pos++; continue }
                        expect(']')
                        return list
                    }
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        fun peek(c: Char) = pos < s.length && s[pos] == c

        fun expect(c: Char) {
            require(peek(c)) { "expected '$c' at $pos" }
            pos++
        }

        fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, pos)) { "unexpected token at $pos" }
            pos += word.length
            return result
        }

        fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("bad number at $start")
        }

        fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(pos < s.length) { "unterminated string" }
                val c = s[pos++]
                when {
                    c == '"' -> return out.toString()
                    c != '\\' -> out.append(c)
                    else -> {
                        require(pos < s.length) { "unterminated escape" }
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "short unicode escape" }
                                out.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: throw IllegalArgumentException("bad unicode escape"))
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("bad escape")
                        }
                    }
                }
            }
        }
    }
}
