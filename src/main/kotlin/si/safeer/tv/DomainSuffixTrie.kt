package si.safeer.tv

/**
 * 🌲 DomainSuffixTrie
 * Visoko-zmogljiva drevesna struktura (Radix / Suffix Trie) za O(k) preverjanje domen in poddomen.
 * Domene shranjuje v obratnem vrstnem redu segmentov (npr. ["com", "doubleclick"] za doubleclick.com).
 * Če je v drevesu "badsite.com", avtomatsko ujame tudi "a.b.badsite.com" brez počasnih nizovnih zank.
 */
class DomainSuffixTrie {

    class Node {
        val children = HashMap<String, Node>()
        var isTerminal: Boolean = false
        var category: String? = null
        var sourceFeed: String? = null
    }

    data class MatchResult(
        val isMatched: Boolean,
        val matchedDomain: String,
        val category: String?,
        val sourceFeed: String?
    )

    private val root = Node()
    var size: Int = 0
        private set

    /**
     * Vstavi domeno v Suffix Trie.
     */
    fun insert(domain: String, category: String? = null, sourceFeed: String? = null) {
        val clean = domain.trim().lowercase()
        if (clean.isEmpty() || clean.startsWith("#")) return

        val parts = clean.split('.').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return

        var current = root
        // Vstavljamo od TLD proti poddomeni (od zadaj naprej)
        for (i in parts.size - 1 downTo 0) {
            val part = parts[i]
            current = current.children.getOrPut(part) { Node() }
        }
        if (!current.isTerminal) {
            current.isTerminal = true
            current.category = category
            current.sourceFeed = sourceFeed
            size++
        }
    }

    /**
     * Vstavi več domen naenkrat.
     */
    fun insertAll(domains: Collection<String>, category: String? = null, sourceFeed: String? = null) {
        for (d in domains) {
            insert(d, category, sourceFeed)
        }
    }

    /**
     * Preveri, ali gostitelj (host) ali katerakoli njegova nad-domena ustreza vnosu v drevesu.
     * Primer: Za "analytics.sub.track.com" preveri [com -> track -> sub -> analytics].
     * Če je [com -> track] označen kot terminal, vrne true takoj!
     */
    fun matches(host: String): Boolean {
        return findMatch(host) != null
    }

    /**
     * Poišče podrobnosti ujemanja (kategorija grožnje, vir itd.).
     */
    fun findMatch(host: String): MatchResult? {
        val clean = host.trim().lowercase()
        if (clean.isEmpty()) return null

        val parts = clean.split('.').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var current = root
        var matchedPartsCount = 0

        for (i in parts.size - 1 downTo 0) {
            val part = parts[i]
            val next = current.children[part] ?: return null
            current = next
            matchedPartsCount++

            if (current.isTerminal) {
                val matchedDomain = parts.subList(parts.size - matchedPartsCount, parts.size).joinToString(".")
                return MatchResult(
                    isMatched = true,
                    matchedDomain = matchedDomain,
                    category = current.category,
                    sourceFeed = current.sourceFeed
                )
            }
        }

        return if (current.isTerminal) {
            MatchResult(
                isMatched = true,
                matchedDomain = clean,
                category = current.category,
                sourceFeed = current.sourceFeed
            )
        } else {
            null
        }
    }

    /**
     * Počisti drevo.
     */
    fun clear() {
        root.children.clear()
        size = 0
    }
}
