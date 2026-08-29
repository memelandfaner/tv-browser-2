package com.example.safeerbrowser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.*
import java.net.URLEncoder

class MainActivity : android.app.Activity() {

    private lateinit var mainRoot: RelativeLayout
    private lateinit var mobileTopBar: LinearLayout
    private lateinit var btnHome: Button
    private lateinit var omniboxContainer: LinearLayout
    private lateinit var tvSecurityLock: TextView
    private lateinit var editUrl: EditText
    private lateinit var btnClearUrl: TextView
    private lateinit var btnSearchTrigger: TextView
    private lateinit var btnPointerToggle: Button
    private lateinit var btnAddTab: Button
    private lateinit var btnTabCount: Button
    private lateinit var btnMenu: Button
    private lateinit var pageProgressBar: ProgressBar
    private lateinit var webViewContainer: FrameLayout
    private lateinit var virtualPointerView: VirtualPointerView

    // Overlays & Secondary Views
    private lateinit var tabSwitcherOverlay: RelativeLayout
    private lateinit var tabsGridView: GridView
    private lateinit var btnNewTabInSwitcher: Button
    private lateinit var btnCloseTabsSwitcher: Button
    private lateinit var btnCloseAllTabs: TextView

    private lateinit var findInPageBar: LinearLayout
    private lateinit var editFindText: EditText
    private lateinit var tvFindMatches: TextView
    private lateinit var btnFindPrev: Button
    private lateinit var btnFindNext: Button
    private lateinit var btnFindClose: Button

    // Managers & Repositories
    private lateinit var tabManager: TabManager
    private lateinit var repository: BrowserRepository
    private lateinit var downloadHandler: DownloadHandler

    private var customVideoView: View? = null
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null
    private var isDarkModeActive: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#06090F")
        window.navigationBarColor = Color.parseColor("#000000")

        repository = BrowserRepository(this)
        downloadHandler = DownloadHandler(this)

        initViews()
        setupTabManager()
        setupOmnibox()
        setupTopButtons()
        setupTouchGestures()
        setupFindInPage()

        // Zaženi posodobitev varnostnih seznamov (Feodo, URLhaus, Phishing Army) v ozadju
        ThreatFeedsUpdater.updateFeedsAsync(this)

        // Odpri začetni zavihek
        val targetUrl = intent?.dataString ?: "file:///android_asset/brave_home.html"
        tabManager.createTab(this, targetUrl, true)
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Safeer:BackgroundAudioWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        acquireWakeLock()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        acquireWakeLock()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val url = intent?.dataString
        if (!url.isNullOrEmpty()) {
            val activeTab = tabManager.getActiveTab()
            if (activeTab != null) {
                activeTab.webView.loadUrl(url)
            } else {
                tabManager.createTab(this, url, true)
            }
        }
    }

    private fun initViews() {
        mainRoot = findViewById(R.id.mainRoot)
        mobileTopBar = findViewById(R.id.mobileTopBar)
        btnHome = findViewById(R.id.btnHome)
        omniboxContainer = findViewById(R.id.omniboxContainer)
        tvSecurityLock = findViewById(R.id.tvSecurityLock)
        editUrl = findViewById(R.id.editUrl)
        btnClearUrl = findViewById(R.id.btnClearUrl)
        btnSearchTrigger = findViewById(R.id.btnSearchTrigger)
        btnPointerToggle = findViewById(R.id.btnPointerToggle)
        btnAddTab = findViewById(R.id.btnAddTab)
        btnTabCount = findViewById(R.id.btnTabCount)
        btnMenu = findViewById(R.id.btnMenu)
        pageProgressBar = findViewById(R.id.pageProgressBar)
        webViewContainer = findViewById(R.id.webViewContainer)
        virtualPointerView = findViewById(R.id.virtualPointerView)

        tabSwitcherOverlay = findViewById(R.id.tabSwitcherOverlay)
        tabsGridView = findViewById(R.id.tabsGridView)
        btnNewTabInSwitcher = findViewById(R.id.btnNewTabInSwitcher)
        btnCloseTabsSwitcher = findViewById(R.id.btnCloseTabsSwitcher)
        btnCloseAllTabs = findViewById(R.id.btnCloseAllTabs)

        findInPageBar = findViewById(R.id.findInPageBar)
        editFindText = findViewById(R.id.editFindText)
        tvFindMatches = findViewById(R.id.tvFindMatches)
        btnFindPrev = findViewById(R.id.btnFindPrev)
        btnFindNext = findViewById(R.id.btnFindNext)
        btnFindClose = findViewById(R.id.btnFindClose)
    }

    private fun setupTabManager() {
        tabManager = TabManager(webViewContainer) { count, activeTab ->
            btnTabCount.text = count.toString()
            if (activeTab != null) {
                activeTab.webView.isDarkMode = isDarkModeActive
                attachTabListeners(activeTab)
                updateOmniboxDisplay(activeTab.url, activeTab.webView.title)
            }
        }
    }

    private fun attachTabListeners(tab: TabModel) {
        val wv = tab.webView

        wv.onProgressUpdate = { progress ->
            if (tabManager.getActiveTab()?.id == tab.id) {
                if (progress < 100) {
                    pageProgressBar.visibility = View.VISIBLE
                    pageProgressBar.progress = progress
                } else {
                    pageProgressBar.visibility = View.GONE
                }
            }
        }

        wv.onUrlChanged = { newUrl ->
            tab.url = newUrl
            if (tabManager.getActiveTab()?.id == tab.id) {
                updateOmniboxDisplay(newUrl, wv.title)
            }
        }

        wv.onPageLoaded = { finalUrl, pageTitle ->
            tab.url = finalUrl
            tab.title = pageTitle
            if (finalUrl.isNotEmpty() && !finalUrl.startsWith("about:", ignoreCase = true)) {
                val cleanTitle = if (pageTitle.isNotEmpty()) pageTitle else finalUrl
                repository.addHistory(cleanTitle, finalUrl)
            }
        }

        wv.onTitleChanged = { title ->
            tab.title = title
            if (tabManager.getActiveTab()?.id == tab.id) {
                updateOmniboxDisplay(tab.url, title)
            }
        }

        wv.onSecurityChanged = { isSecure ->
            if (tabManager.getActiveTab()?.id == tab.id) {
                tvSecurityLock.text = if (isSecure) "🔒" else "⚠️"
                tvSecurityLock.setTextColor(
                    if (isSecure) Color.parseColor("#10B981") else Color.parseColor("#F59E0B")
                )
            }
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadHandler.startDownload(url, userAgent, contentDisposition, mimeType)
        }

        wv.onFullscreenToggled = { customView, callback ->
            if (customView != null) {
                customVideoView = customView
                customVideoCallback = callback
                mobileTopBar.visibility = View.GONE
                webViewContainer.visibility = View.GONE
                mainRoot.addView(
                    customView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else {
                customVideoView?.let { mainRoot.removeView(it) }
                customVideoView = null
                customVideoCallback = null
                mobileTopBar.visibility = View.VISIBLE
                webViewContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun setupOmnibox() {
        editUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val currentUrl = tabManager.getActiveTab()?.url ?: ""
                editUrl.setText(currentUrl)
                editUrl.selectAll()
                btnClearUrl.visibility = if (editUrl.text.isNotEmpty()) View.VISIBLE else View.GONE
            } else {
                btnClearUrl.visibility = View.GONE
                val activeTab = tabManager.getActiveTab()
                updateOmniboxDisplay(activeTab?.url ?: "", activeTab?.webView?.title)
            }
        }

        editUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (editUrl.hasFocus()) {
                    btnClearUrl.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performNavigation(editUrl.text.toString().trim())
                hideKeyboard()
                editUrl.clearFocus()
                true
            } else {
                false
            }
        }

        btnClearUrl.setOnClickListener {
            editUrl.setText("")
            editUrl.requestFocus()
        }

        btnSearchTrigger.setOnClickListener {
            performNavigation(editUrl.text.toString().trim())
            hideKeyboard()
            editUrl.clearFocus()
        }
    }

    private fun updateOmniboxDisplay(url: String, title: String?) {
        if (editUrl.hasFocus()) return

        if (url.isEmpty() || url == "about:blank" || url.startsWith("file:///android_asset/brave_home.html")) {
            editUrl.setText("")
            editUrl.hint = getString(R.string.url_hint)
            tvSecurityLock.text = "🦁"
            return
        }

        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            val cleanHost = host.removePrefix("www.")
            val path = uri.path ?: ""
            val display = if (path.length > 1 && path != "/") "$cleanHost$path" else cleanHost
            editUrl.setText(display)
        } catch (_: Exception) {
            editUrl.setText(url)
        }
    }

    private fun performNavigation(input: String) {
        if (input.isEmpty()) return

        val finalUrl = when {
            input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true) || input.startsWith("file://", ignoreCase = true) -> {
                input
            }
            input.contains(".") && !input.contains(" ") -> {
                "https://$input"
            }
            else -> {
                "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
            }
        }

        val activeTab = tabManager.getActiveTab()
        if (activeTab != null) {
            activeTab.webView.loadUrl(finalUrl)
        } else {
            tabManager.createTab(this, finalUrl, true)
        }
    }

    private fun setupTopButtons() {
        btnHome.setOnClickListener {
            tabManager.getActiveTab()?.webView?.loadUrl("file:///android_asset/brave_home.html")
        }

        btnPointerToggle.setOnClickListener {
            virtualPointerView.isPointerVisible = !virtualPointerView.isPointerVisible
            Toast.makeText(
                this,
                if (virtualPointerView.isPointerVisible) "🖱️ Kazalec TV vklopljen" else "🖐️ Kazalec TV izklopljen",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnAddTab.setOnClickListener {
            tabManager.createTab(this, "file:///android_asset/brave_home.html", true)
        }

        btnTabCount.setOnClickListener {
            toggleTabSwitcher()
        }

        btnMenu.setOnClickListener {
            showMobileMenu()
        }

        // Tab switcher buttons
        btnNewTabInSwitcher.setOnClickListener {
            tabSwitcherOverlay.visibility = View.GONE
            tabManager.createTab(this, "file:///android_asset/brave_home.html", true)
        }

        btnCloseTabsSwitcher.setOnClickListener {
            tabSwitcherOverlay.visibility = View.GONE
        }

        btnCloseAllTabs.setOnClickListener {
            tabManager.closeAllTabs(this)
            tabSwitcherOverlay.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (Math.abs(diffX) > Math.abs(diffY) &&
                    Math.abs(diffX) > SWIPE_THRESHOLD &&
                    Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) {
                        tabManager.switchToPrevTab()
                        Toast.makeText(this@MainActivity, "◀ Prejšnji zavihek", Toast.LENGTH_SHORT).show()
                    } else {
                        tabManager.switchToNextTab()
                        Toast.makeText(this@MainActivity, "Naslednji zavihek ▶", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return false
            }
        })

        omniboxContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun toggleTabSwitcher() {
        if (tabSwitcherOverlay.visibility == View.VISIBLE) {
            tabSwitcherOverlay.visibility = View.GONE
        } else {
            renderTabsGrid()
            tabSwitcherOverlay.visibility = View.VISIBLE
        }
    }

    private fun renderTabsGrid() {
        val allTabs = tabManager.getAllTabs()
        val activeId = tabManager.getActiveTab()?.id

        tabsGridView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = allTabs.size
            override fun getItem(position: Int): Any = allTabs[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val tab = allTabs[position]
                val view = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_tab_card, parent, false)

                val tvTitle = view.findViewById<TextView>(R.id.tvTabTitle)
                val tvUrl = view.findViewById<TextView>(R.id.tvTabUrl)
                val tvActiveBadge = view.findViewById<TextView>(R.id.tvActiveBadge)
                val btnClose = view.findViewById<TextView>(R.id.btnTabClose)
                val cardRoot = view.findViewById<RelativeLayout>(R.id.tabCardRoot)

                tvTitle.text = tab.title.ifEmpty { "Zavihek ${position + 1}" }
                tvUrl.text = tab.url
                
                val isActive = (tab.id == activeId)
                cardRoot.setBackgroundResource(if (isActive) R.drawable.bg_tab_card_active else R.drawable.bg_tab_card)
                tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE

                view.setOnClickListener {
                    tabManager.switchTab(tab.id)
                    tabSwitcherOverlay.visibility = View.GONE
                }

                btnClose.setOnClickListener {
                    tabManager.closeTab(this@MainActivity, tab.id)
                    renderTabsGrid()
                }

                return view
            }
        }
    }

    private fun showMobileMenu() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_mobile_menu)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)

        val activeTab = tabManager.getActiveTab()
        val wv = activeTab?.webView

        val menuBtnBack = dialog.findViewById<Button>(R.id.menuBtnBack)
        val menuBtnForward = dialog.findViewById<Button>(R.id.menuBtnForward)
        val menuBtnReload = dialog.findViewById<Button>(R.id.menuBtnReload)
        val menuBtnStar = dialog.findViewById<Button>(R.id.menuBtnStar)
        val menuBtnShare = dialog.findViewById<Button>(R.id.menuBtnShare)

        menuBtnBack.setOnClickListener {
            if (wv?.canGoBack() == true) wv.goBack()
            dialog.dismiss()
        }

        menuBtnForward.setOnClickListener {
            if (wv?.canGoForward() == true) wv.goForward()
            dialog.dismiss()
        }

        menuBtnReload.setOnClickListener {
            wv?.reload()
            dialog.dismiss()
        }

        val curUrl = activeTab?.url ?: ""
        val isBm = repository.isBookmarked(curUrl)
        menuBtnStar.text = if (isBm) "⭐" else "☆"
        menuBtnStar.setOnClickListener {
            if (isBm) {
                repository.removeBookmark(curUrl)
                Toast.makeText(this, getString(R.string.toast_bookmark_removed), Toast.LENGTH_SHORT).show()
            } else {
                repository.addBookmark(wv?.title ?: "Zaznamek", curUrl)
                Toast.makeText(this, getString(R.string.toast_bookmark_added), Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        menuBtnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, curUrl)
            }
            startActivity(Intent.createChooser(shareIntent, "Deli stran"))
            dialog.dismiss()
        }

        dialog.findViewById<LinearLayout>(R.id.rowMenuNewTab).setOnClickListener {
            tabManager.createTab(this, "https://www.google.com", true)
            dialog.dismiss()
            editUrl.requestFocus()
            showKeyboard()
        }

        dialog.findViewById<LinearLayout>(R.id.rowMenuBookmarks).setOnClickListener {
            dialog.dismiss()
            showBookmarksDialog()
        }

        dialog.findViewById<LinearLayout>(R.id.rowMenuDownloads).setOnClickListener {
            dialog.dismiss()
            try {
                startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: Exception) {
                Toast.makeText(this, "Mapa prenosov je v mapi Prenosi", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.findViewById<LinearLayout>(R.id.rowMenuHistory).setOnClickListener {
            dialog.dismiss()
            showHistoryDialog()
        }

        dialog.findViewById<LinearLayout>(R.id.rowMenuFindInPage).setOnClickListener {
            dialog.dismiss()
            showFindInPage()
        }

        val cbDesktop = dialog.findViewById<CheckBox>(R.id.cbDesktopSite)
        cbDesktop.isChecked = activeTab?.isDesktop ?: false
        dialog.findViewById<LinearLayout>(R.id.rowMenuDesktopSite).setOnClickListener {
            val nextState = !cbDesktop.isChecked
            cbDesktop.isChecked = nextState
            activeTab?.isDesktop = nextState
            wv?.isDesktopMode = nextState
            wv?.reload()
            dialog.dismiss()
        }

        // Threat Shield Status Dialog
        dialog.findViewById<LinearLayout>(R.id.rowMenuThreatStats).setOnClickListener {
            dialog.dismiss()
            showThreatStatsDialog()
        }

        val cbAdBlock = dialog.findViewById<CheckBox>(R.id.cbAdBlock)
        cbAdBlock.isChecked = AdBlockEngine.isEnabled
        dialog.findViewById<LinearLayout>(R.id.rowMenuAdBlock).setOnClickListener {
            AdBlockEngine.isEnabled = !AdBlockEngine.isEnabled
            cbAdBlock.isChecked = AdBlockEngine.isEnabled
            Toast.makeText(
                this,
                if (AdBlockEngine.isEnabled) "🛡️ AdBlock vklopljen" else "⚠️ AdBlock izklopljen",
                Toast.LENGTH_SHORT
            ).show()
            wv?.reload()
            dialog.dismiss()
        }

        val cbDark = dialog.findViewById<CheckBox>(R.id.cbDarkMode)
        cbDark.isChecked = isDarkModeActive
        dialog.findViewById<LinearLayout>(R.id.rowMenuDarkMode).setOnClickListener {
            isDarkModeActive = !isDarkModeActive
            cbDark.isChecked = isDarkModeActive
            tabManager.getAllTabs().forEach { t ->
                t.webView.applyDarkMode(isDarkModeActive)
            }
            Toast.makeText(
                this,
                if (isDarkModeActive) "🌙 AMOLED Temni način vklopljen" else "☀️ Svetli način vklopljen",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showThreatStatsDialog() {
        val totalThreats = ThreatBlockEngine.totalBlockedThreats.get()
        val c2 = ThreatBlockEngine.blockedC2Count.get()
        val malware = ThreatBlockEngine.blockedMalwareCount.get()
        val phishing = ThreatBlockEngine.blockedPhishingCount.get()
        val totalAds = AdBlockEngine.blockedAdsCount.get()

        AlertDialog.Builder(this)
            .setTitle("🛑 Safeer Threat Shield & AdBlock")
            .setMessage(
                """
                Varnostni ščit varuje vašo napravo pred nevarnimi C2 strežniki in zlonamerno kodo:
                
                • Blokiranih C2 Botnet strežnikov: $c2
                • Blokiranih Malware prenosov: $malware
                • Blokiranih Phishing strani: $phishing
                • Skupaj preprečenih groženj: $totalThreats
                • Blokiranih oglasov in sledilcev: $totalAds
                
                Viri: abuse.ch Feodo Tracker, URLhaus, ThreatFox, Phishing Army, StevenBlack Hosts.
                """.trimIndent()
            )
            .setPositiveButton("Posodobi sezname") { _, _ ->
                Toast.makeText(this, "🔄 Posodabljam varnostne sezname...", Toast.LENGTH_SHORT).show()
                ThreatFeedsUpdater.updateFeedsAsync(this) { added ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "✅ Dodanih $added novih varnostnih pravil!", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Zapri", null)
            .show()
    }

    private fun showFindInPage() {
        findInPageBar.visibility = View.VISIBLE
        editFindText.requestFocus()
        showKeyboard()

        val activeWv = tabManager.getActiveTab()?.webView
        activeWv?.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            tvFindMatches.text = if (numberOfMatches > 0) "${activeMatchOrdinal + 1}/$numberOfMatches" else "0/0"
        }
        val query = editFindText.text.toString()
        if (query.isNotEmpty()) {
            activeWv?.findAllAsync(query)
        } else {
            tvFindMatches.text = "0/0"
        }
    }

    private fun showBookmarksDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_bookmarks)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val listView = dialog.findViewById<ListView>(R.id.bookmarksListView)
        val btnClose = dialog.findViewById<Button>(R.id.btnCloseBookmarks)
        val bookmarks = repository.getBookmarks()

        listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = bookmarks.size
            override fun getItem(position: Int): Any = bookmarks[position]
            override fun getItemId(position: Int): Long = bookmarks[position].id

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val bm = bookmarks[position]
                val view = convertView ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_bookmark, parent, false)

                view.findViewById<TextView>(R.id.tvBmIcon).text = bm.icon
                view.findViewById<TextView>(R.id.tvBmTitle).text = bm.title
                view.findViewById<TextView>(R.id.tvBmUrl).text = bm.url

                view.setOnClickListener {
                    tabManager.getActiveTab()?.webView?.loadUrl(bm.url)
                    dialog.dismiss()
                }

                view.findViewById<Button>(R.id.btnDeleteBm).setOnClickListener {
                    repository.removeBookmark(bm.url)
                    dialog.dismiss()
                    showBookmarksDialog()
                }

                return view
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showHistoryDialog() {
        val history = repository.getHistory(50)
        val items = history.map { "${it.title}\n${it.url}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🕒 Zgodovina brskanja")
            .setItems(items) { _, which ->
                val selected = history[which]
                tabManager.getActiveTab()?.webView?.loadUrl(selected.url)
            }
            .setPositiveButton("Počisti zgodovino") { _, _ ->
                repository.clearHistory()
                Toast.makeText(this, "Zgodovina počiščena", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zapri", null)
            .show()
    }

    private fun setupFindInPage() {
        editFindText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val wv = tabManager.getActiveTab()?.webView ?: return
                wv.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                    tvFindMatches.text = if (numberOfMatches > 0) "${activeMatchOrdinal + 1}/$numberOfMatches" else "0/0"
                }
                wv.findAllAsync(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnFindPrev.setOnClickListener {
            tabManager.getActiveTab()?.webView?.findNext(false)
        }

        btnFindNext.setOnClickListener {
            tabManager.getActiveTab()?.webView?.findNext(true)
        }

        btnFindClose.setOnClickListener {
            tabManager.getActiveTab()?.webView?.clearMatches()
            findInPageBar.visibility = View.GONE
            hideKeyboard()
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editUrl, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editUrl.windowToken, 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val activeWv = tabManager.getActiveTab()?.webView

        // 1. Zelen gumb / INFO -> Preklop kazalca
        if (keyCode == KeyEvent.KEYCODE_PROG_GREEN || keyCode == KeyEvent.KEYCODE_INFO) {
            virtualPointerView.isPointerVisible = !virtualPointerView.isPointerVisible
            Toast.makeText(
                this,
                if (virtualPointerView.isPointerVisible) "🖱️ Kazalec TV vklopljen" else "🖐️ Kazalec TV izklopljen",
                Toast.LENGTH_SHORT
            ).show()
            return true
        }

        // 2. Modri gumb -> Ponovno naloži stran
        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
            activeWv?.reload()
            return true
        }

        val isTopBarFocused = editUrl.hasFocus() || btnHome.hasFocus() || btnPointerToggle.hasFocus() ||
                              btnAddTab.hasFocus() || btnTabCount.hasFocus() || btnMenu.hasFocus()

        // 3. Če je kazalec vklopljen in fokus NI v vnosnem polju ali gumbih vrstice:
        if (virtualPointerView.isPointerVisible && !isTopBarFocused) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (virtualPointerView.pointerY < 120f) {
                        editUrl.requestFocus()
                    } else {
                        virtualPointerView.movePointer(0f, -40f, activeWv)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    virtualPointerView.movePointer(0f, 40f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    virtualPointerView.movePointer(-40f, 0f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    virtualPointerView.movePointer(40f, 0f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (activeWv != null) {
                        virtualPointerView.performClickOnWebView(activeWv)
                    }
                    return true
                }
            }
        }

        // Standardna D-Pad navigacija brez kazalca ali za vrstico:
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (activeWv != null && activeWv.hasFocus() && activeWv.scrollY == 0) {
                    editUrl.requestFocus()
                    return true
                } else if (activeWv != null && activeWv.hasFocus()) {
                    activeWv.scrollBy(0, -220)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isTopBarFocused) {
                    activeWv?.requestFocus()
                    return true
                } else if (activeWv != null && activeWv.hasFocus()) {
                    activeWv.scrollBy(0, 220)
                    return true
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                showMobileMenu()
                return true
            }
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_PROG_RED -> {
                editUrl.requestFocus()
                showKeyboard()
                return true
            }
            KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_PROG_YELLOW -> {
                showBookmarksDialog()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                activeWv?.pageUp(false)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                activeWv?.pageDown(false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                activeWv?.evaluateJavascript(
                    "var v = document.querySelector('video'); if (v) { v.paused ? v.play() : v.pause(); }",
                    null
                )
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (customVideoView != null) {
            tabManager.getActiveTab()?.webView?.exitFullscreenVideo()
            return
        }

        if (findInPageBar.visibility == View.VISIBLE) {
            tabManager.getActiveTab()?.webView?.clearMatches()
            findInPageBar.visibility = View.GONE
            return
        }

        if (tabSwitcherOverlay.visibility == View.VISIBLE) {
            tabSwitcherOverlay.visibility = View.GONE
            return
        }

        val activeTab = tabManager.getActiveTab()
        if (activeTab != null && activeTab.webView.canGoBack()) {
            activeTab.webView.goBack()
            return
        }

        if (tabManager.count > 1 && activeTab != null) {
            tabManager.closeTab(this, activeTab.id)
            return
        }

        super.onBackPressed()
    }
}
