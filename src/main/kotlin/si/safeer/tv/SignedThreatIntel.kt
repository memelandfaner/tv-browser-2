package si.safeer.tv

import android.content.Context
import android.util.Log
import com.safeer.threatfeed.FeedMatch
import com.safeer.threatfeed.SignedFeedService
import com.safeer.threatfeed.SignedFeedStore
import java.io.File

/**
 * 🛡️ Safeer Threat Intelligence
 * Dodatna plast zaščite: preverjen, z Ed25519 podpisan seznam nevarnih strani, ki ga objavlja
 * https://github.com/memelandfaner/safeer-threat-intel. Brskalnik prenese le dve javni datoteki
 * (anonimen GET, brez piškotkov in identifikatorjev); vse preverjanje poteka na napravi. Ob vsaki
 * napaki ostane v uporabi zadnji preverjen seznam, vgrajena zaščita pa deluje tudi brez njega.
 */
object SignedThreatIntel {
    private const val TAG = "SafeerThreatIntel"

    /**
     * Zaupanja vredni javni ključi (key_id -> base64 Ed25519). Brez ključa ta plast ostane izklopljena.
     *
     * Produkcijski ključ "safeer-prod-2026-09" (ustvarjen 11. 9. 2026 na računalniku lastnika; zasebni del
     * nikoli ni bil v repozitoriju, sliki Docker ali APK). Nov ključ se doda sem pred menjavo na strežniku,
     * star se odstrani šele, ko ga strežnik ne uporablja več.
     */
    val TRUSTED_KEYS: Map<String, String> = mapOf(
        "safeer-prod-2026-09" to "Z4fKgHcD1wdrYBdvybqszN/z355SrGUREUhJyQJISpA=",
    )
    val BASE_URLS = listOf("https://intel.safeer.si")

    @Volatile
    private var store: SignedFeedStore? = null
    private var service: SignedFeedService? = null

    val isEnabled: Boolean get() = TRUSTED_KEYS.isNotEmpty()

    @Synchronized
    fun start(context: Context) {
        if (service != null || !isEnabled) return
        val feedStore = SignedFeedStore(
            File(context.applicationContext.filesDir, "threat-intel"), "threats", TRUSTED_KEYS, BASE_URLS,
        )
        val feedService = SignedFeedService(feedStore) { updated ->
            val bundle = updated.bundle
            Log.i(TAG, "Preverjen seznam groženj v${bundle?.manifest?.version}: ${bundle?.ruleCount} pravil")
        }
        store = feedStore
        ThreatBlockEngine.signedFeedMatcher = { url, host -> toMatchResult(feedStore.matchUrl(url) ?: feedStore.matchHost(host)) }
        if (feedService.start()) service = feedService
    }

    /** Takojšnje preverjanje novega seznama (gumb "Posodobi sezname"). */
    fun requestUpdate(callback: ((installed: Boolean) -> Unit)? = null): Boolean = service?.requestUpdate(callback) ?: false

    /** Kratko stanje za pogovorno okno statistike. */
    fun statusLine(): String {
        if (!isEnabled) return UiText.get(R.string.ui_signed_intel_waiting)
        val bundle = store?.bundle ?: return UiText.get(R.string.ui_signed_intel_not_downloaded)
        return UiText.get(R.string.ui_signed_intel_rules, bundle.ruleCount, bundle.manifest.version)
    }

    internal fun toMatchResult(match: FeedMatch?): DomainSuffixTrie.MatchResult? {
        if (match == null) return null
        val category = when (match.category) {
            "botnet_c2" -> "Botnet C2 strežnik"
            "malware" -> "Zlonamerna koda (Malware)"
            "phishing" -> "Spletno ribarjenje (Phishing)"
            "scam" -> "Prevara (Scam)"
            else -> return null // oglasi in sledilci niso varnostna grožnja
        }
        return DomainSuffixTrie.MatchResult(
            isMatched = true,
            matchedDomain = match.indicator,
            category = category,
            sourceFeed = "Safeer Threat Intelligence · ${match.source.name}",
        )
    }
}
