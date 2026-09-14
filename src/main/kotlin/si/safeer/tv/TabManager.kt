package si.safeer.tv

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import java.util.UUID

data class TabModel(
    val id: String = UUID.randomUUID().toString(),
    var webView: ChromiumEngineView,
    var title: String = "Google",
    var url: String = "https://www.google.com",
    var isDesktop: Boolean = false,
    var favicon: String = "🔍",
    /** Zavihek spi: stran je odlozena, pogled je prazen in ne zaseda pomnilnika. */
    var spi: Boolean = false,
    /** Naslov, ki ga ob prebuditvi nalozimo nazaj. */
    var spalniNaslov: String = "",
    /** Kje je uporabnik bral, ko je zavihek zaspal. */
    var spalniOdmik: Int = 0,
    /** Zavihek predvaja zvok ali sliko - tak ne sme zaspati. */
    var predvaja: Boolean = false
)

/**
 * Zavihki brskalnika na televizorju.
 *
 * Televizor ima okoli 2,7 GB pomnilnika, prostega pa le nekaj sto MB, in vsak odprt zavihek
 * drzi svojo stran v izrisovalniku. Zato zavihki v ozadju zaspijo: stran odlozimo, naslov in
 * mesto branja pa si zapomnimo in ju ob vrnitvi obnovimo. Uporabnik vidi le, da se stran ob
 * vrnitvi na hitro nalozi - ne vidi pa tega, da bi mu sistem brskalnik ubil.
 *
 * Aktivni zavihek in zavihek, ki predvaja, ne zaspita nikoli.
 */
class TabManager(
    private val container: FrameLayout,
    private val onTabsUpdated: (count: Int, activeTab: TabModel?) -> Unit
) {

    companion object {
        private const val TAG = "SafeerZavihki"
        private const val PRAZNA = "about:blank"
        /** Koliko zavihkov v ozadju sme ostati budnih (poleg aktivnega). */
        private const val BUDNIH_V_OZADJU = 1
        private const val NAJVEC_ZAVIHKOV = 5
        /** V tem casu drugo sesutje iste strani stejemo za ponovitev. */
        private const val PONOVITEV_MS = 60_000L
        private const val DOMACA = "file:///android_asset/brave_home.html"
    }

    private val tabs = mutableListOf<TabModel>()
    private var activeTabId: String? = null

    /** Zadnji zavihek, ki ga je uporabnik zapustil; ta se sme ostati buden. */
    private var prejsnjiId: String? = null

    /** Zadnja smrt izrisovalnika po zavihkih: naslov in cas. Proti zankam sesutja. */
    private val zadnjaSmrt = HashMap<String, Pair<String, Long>>()

    val count: Int get() = tabs.size

    private fun ustvariPogled(context: Context): ChromiumEngineView {
        val pogled = ChromiumEngineView(context)
        pogled.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Ko sistemu zmanjka pomnilnika, ubije izrisovalnik strani. Brez tega bi z njim
        // padel cel brskalnik; tako pa zavihek samo obnovimo.
        pogled.naSmrtIzrisovalnika = { stari, sesul -> obnoviPoSmrti(stari, sesul) }
        return pogled
    }

    fun createTab(context: Context, url: String = "https://www.google.com", makeActive: Boolean = true): TabModel {
        while (tabs.size >= NAJVEC_ZAVIHKOV) {
            val victim = tabs.firstOrNull { it.id != activeTabId } ?: tabs.firstOrNull() ?: break
            val idx = tabs.indexOf(victim)
            try { victim.webView.destroy() } catch (_: Exception) {}
            if (idx >= 0) tabs.removeAt(idx)
        }
        val webView = ustvariPogled(context)

        val tab = TabModel(
            webView = webView,
            title = "Nov zavihek",
            url = url
        )

        tabs.add(tab)

        if (makeActive || tabs.size == 1) {
            switchTab(tab.id)
        }

        webView.loadUrl(url)
        notifyUpdated()
        return tab
    }

    fun switchTab(tabId: String) {
        val target = tabs.find { it.id == tabId } ?: return
        if (activeTabId != null && activeTabId != tabId) prejsnjiId = activeTabId
        activeTabId = tabId

        container.removeAllViews()
        if (target.webView.parent != null) {
            (target.webView.parent as? ViewGroup)?.removeView(target.webView)
        }
        container.addView(target.webView)
        zbudi(target)
        for (tab in tabs) {
            try {
                if (tab.id == tabId) tab.webView.onResume()
                else tab.webView.onPause()
            } catch (_: Exception) {}
        }
        pospraviOzadje()

        notifyUpdated()
    }

    // ------------------------------------------------------------------ spanje zavihkov

    /** Zavihki v ozadju, ki niso na vrsti, zaspijo; zadnji zapusceni sme se ostati buden. */
    private fun pospraviOzadje() {
        if (BUDNIH_V_OZADJU <= 0) {
            uspavajOzadje(true)
            return
        }
        for (tab in tabs) {
            if (tab.id == activeTabId || tab.id == prejsnjiId) continue
            uspavaj(tab)
        }
    }

    /**
     * Uspava zavihke v ozadju. `vse` naj bo true, ko sistem javi pomanjkanje pomnilnika -
     * takrat ne obdrzimo niti zadnjega zapuscenega.
     */
    fun uspavajOzadje(vse: Boolean) {
        for (tab in tabs) {
            if (tab.id == activeTabId) continue
            if (!vse && tab.id == prejsnjiId) continue
            uspavaj(tab)
        }
    }

    private fun uspavaj(tab: TabModel) {
        if (tab.spi || tab.predvaja || tab.id == activeTabId) return
        val naslov = (tab.webView.url ?: tab.url).trim()
        if (naslov.isBlank() || naslov == PRAZNA) return
        tab.spalniNaslov = naslov
        tab.spalniOdmik = try { tab.webView.scrollY } catch (_: Exception) { 0 }
        tab.spi = true
        try {
            tab.webView.stopLoading()
            tab.webView.loadUrl(PRAZNA)
            tab.webView.clearHistory()
            // Samo pomnilniski del predpomnilnika; datoteke na disku pustimo, da je
            // prebuditev hitra.
            tab.webView.clearCache(false)
            tab.webView.onPause()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tab.webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Zavihka ni bilo mogoce uspavati: ${e.message}")
        }
        Log.i(TAG, "Zavihek spi: ${tab.spalniNaslov.take(60)}")
    }

    private fun zbudi(tab: TabModel) {
        if (!tab.spi) return
        tab.spi = false
        val naslov = tab.spalniNaslov
        val odmik = tab.spalniOdmik
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tab.webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            }
            tab.webView.onResume()
            if (naslov.isNotBlank()) tab.webView.loadUrl(naslov)
            if (odmik > 0) {
                // Mesto branja obnovimo, ko je stran ze na zaslonu.
                tab.webView.postDelayed({
                    try { tab.webView.scrollTo(0, odmik) } catch (_: Exception) {}
                }, 900)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Zavihka ni bilo mogoce prebuditi: ${e.message}")
        }
    }

    /** Ali kateri zavihek trenutno predvaja; tak ne zaspi. */
    fun oznaciPredvajanje(tabId: String?, predvaja: Boolean) {
        for (tab in tabs) if (tab.id == tabId) tab.predvaja = predvaja
    }

    // ------------------------------------------------------------------ smrt izrisovalnika

    /**
     * Izrisovalnik strani je umrl (sistemu je zmanjkalo pomnilnika ali pa se je stran sesula).
     * Tak pogled je po Androidovih pravilih neuporaben: zavrzemo ga in naredimo novega.
     * Aktivni zavihek stran znova nalozi, zavihek v ozadju pa gre spat - do tja, kjer je bil.
     */
    private fun obnoviPoSmrti(stari: ChromiumEngineView, sesul: Boolean) {
        val idx = tabs.indexOfFirst { it.webView === stari }
        if (idx < 0) {
            try { stari.destroy() } catch (_: Exception) {}
            return
        }
        val tab = tabs[idx]
        val naslov = when {
            tab.spi && tab.spalniNaslov.isNotBlank() -> tab.spalniNaslov
            !(stari.url ?: "").isNullOrBlank() && stari.url != PRAZNA -> stari.url!!
            else -> tab.url
        }
        val bilAktiven = tab.id == activeTabId

        // Ce ista stran podre izrisovalnik dvakrat zapored, je ne nalagamo znova: sicer se
        // brskalnik vrti v krogu sesutij. Namesto nje odpremo domaco stran.
        val zdaj = System.currentTimeMillis()
        val prej = zadnjaSmrt[tab.id]
        val ponovitev = prej != null && prej.first == naslov && (zdaj - prej.second) < PONOVITEV_MS
        zadnjaSmrt[tab.id] = naslov to zdaj
        val zaNalozit = if (ponovitev) DOMACA else naslov

        Log.w(TAG, "Izrisovalnik je umrl (${if (sesul) "sesutje" else "sistem je sprostil pomnilnik"}); " +
            (if (ponovitev) "stran se je sesula dvakrat, odpiram domaco stran: "
             else "obnavljam zavihek ") + naslov.take(60))

        try { (stari.parent as? ViewGroup)?.removeView(stari) } catch (_: Exception) {}
        try { stari.destroy() } catch (_: Exception) {}

        val nov = ustvariPogled(container.context)
        tab.webView = nov
        tab.url = zaNalozit

        if (bilAktiven) {
            container.removeAllViews()
            container.addView(nov)
            tab.spi = false
            nov.loadUrl(zaNalozit)
        } else {
            // Zavihka v ozadju ne budimo: naj pocaka, da ga uporabnik res zeli.
            tab.spi = true
            tab.spalniNaslov = zaNalozit
        }
        notifyUpdated()
    }

    // ------------------------------------------------------------------ ostalo

    fun closeTab(context: Context, tabId: String) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx == -1) return

        val tabToClose = tabs[idx]
        tabToClose.webView.destroy()
        tabs.removeAt(idx)
        if (prejsnjiId == tabId) prejsnjiId = null

        if (tabs.isEmpty()) {
            createTab(context, "https://www.google.com", true)
        } else if (activeTabId == tabId) {
            val nextIdx = if (idx < tabs.size) idx else tabs.size - 1
            switchTab(tabs[nextIdx].id)
        } else {
            notifyUpdated()
        }
    }

    fun closeAllTabs(context: Context) {
        for (tab in tabs) {
            tab.webView.destroy()
        }
        tabs.clear()
        prejsnjiId = null
        container.removeAllViews()
        createTab(context, "https://www.google.com", true)
    }

    fun getActiveTab(): TabModel? {
        return tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()
    }

    fun getAllTabs(): List<TabModel> = tabs.toList()

    fun switchToNextTab() {
        if (tabs.size <= 1) return
        val curIdx = tabs.indexOfFirst { it.id == activeTabId }
        if (curIdx != -1) {
            val nextIdx = (curIdx + 1) % tabs.size
            switchTab(tabs[nextIdx].id)
        }
    }

    fun switchToPrevTab() {
        if (tabs.size <= 1) return
        val curIdx = tabs.indexOfFirst { it.id == activeTabId }
        if (curIdx != -1) {
            val prevIdx = if (curIdx - 1 < 0) tabs.size - 1 else curIdx - 1
            switchTab(tabs[prevIdx].id)
        }
    }

    private fun notifyUpdated() {
        onTabsUpdated(tabs.size, getActiveTab())
    }
}
