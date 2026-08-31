package com.example.safeerbrowser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.media.AudioManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.*
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : android.app.Activity() {

    private lateinit var mainRoot: RelativeLayout
    private lateinit var mobileTopBar: LinearLayout
    private lateinit var btnBack: Button
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
    private lateinit var searchSuggestionsOverlay: LinearLayout
    private lateinit var portalChipsContainer: LinearLayout
    private lateinit var suggestionsListContainer: LinearLayout

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

    private fun isHydraPlayerUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("hydrahd") && (u.contains("/movie/") || u.contains("/tv/") || u.contains("/watch"))
    }

    private fun isKioskUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("xploretv") || u.contains("a1xploretv") || isHydraPlayerUrl(u)
    }

    private fun handlePageScroll(direction: Int, scrollY: Int) {
        val url = tabManager.getActiveTab()?.url ?: ""
        if (url.contains("youtube.com/tv", ignoreCase = true) || isKioskUrl(url)) {
            return
        }
        val isTopBarFocused = editUrl.hasFocus() || btnHome.hasFocus() || btnBack.hasFocus() || btnMenu.hasFocus()
        if (direction > 0 && scrollY > 100) {
            if (mobileTopBar.translationY == 0f && !isTopBarFocused && searchSuggestionsOverlay.visibility != View.VISIBLE) {
                mobileTopBar.animate().translationY(-mobileTopBar.height.toFloat()).setDuration(220).start()
            }
        } else if (direction < 0 || scrollY <= 40) {
            if (mobileTopBar.translationY != 0f) {
                mobileTopBar.animate().translationY(0f).setDuration(220).start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.parseColor("#06090F")
        window.navigationBarColor = Color.parseColor("#000000")

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        repository = BrowserRepository(this)
        downloadHandler = DownloadHandler(this)
        isDarkModeActive = getSharedPreferences("safeer_ui_prefs", MODE_PRIVATE).getBoolean("dark_mode", true)

        initViews()
        setupTabManager()
        setupOmnibox()
        setupSearchSuggestions()
        setupTopButtons()
        setupTouchGestures()
        setupFindInPage()

        // Zaženi posodobitev varnostnih seznamov (Feodo, URLhaus, Phishing Army) v ozadju
        ThreatFeedsUpdater.updateFeedsAsync(this)

        val targetUrl = intent?.dataString ?: "file:///android_asset/brave_home.html"
        tabManager.createTab(this, targetUrl, true)

        debugJsReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                tabManager.getActiveTab()?.webView?.evaluateJavascript(cmd, null)
            }
        }
        @Suppress("DEPRECATION")
        registerReceiver(debugJsReceiver, android.content.IntentFilter("com.example.safeerbrowser.EVAL_JS"))
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var debugJsReceiver: android.content.BroadcastReceiver? = null
    private var webViewsPaused = false

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

    private fun silenceBackgroundMedia(reason: String) {
        if (!::tabManager.isInitialized) return
        val tabs = tabManager.getAllTabs()
        // #region agent log
        SafeerDbg.log(
            "H210",
            "MainActivity.kt:$reason",
            "pauseAll",
            JSONObject()
                .put("tabs", tabs.size)
                .put("already", webViewsPaused)
                .put("wl", wakeLock?.isHeld == true)
        )
        // #endregion
        val js = """
            (function(){
                window._safeer_app_bg = true;
                try { sessionStorage.setItem('safeer_app_bg','1'); } catch (eS) {}
                try { if (window._safeerSiteAgent && window._safeerSiteAgent.clearWant) window._safeerSiteAgent.clearWant(); } catch (e) {}
                var n = 0;
                document.querySelectorAll('video,audio').forEach(function(m){
                    try { m.pause(); m.muted = true; m.volume = 0; n++; } catch (e2) {}
                });
                try { if (navigator.mediaSession) navigator.mediaSession.playbackState = 'paused'; } catch (e3) {}
                try { if (window._safeerDbg) window._safeerDbg('H210','site_agent.js','silence',{n:n,path:(location.pathname||'').slice(0,40),vis:document.visibilityState,paused:true}); } catch (e4) {}
                return n;
            })();
        """.trimIndent()
        val firstSilence = !webViewsPaused
        webViewsPaused = true
        for (tab in tabs) {
            try {
                if (firstSilence) {
                    tab.webView.evaluateJavascript(js) { result ->
                        // #region agent log
                        SafeerDbg.log(
                            "H210",
                            "MainActivity.kt:$reason",
                            "js-done",
                            JSONObject().put("n", result ?: "null")
                        )
                        // #endregion
                        try { tab.webView.onPause() } catch (_: Exception) {}
                        try { tab.webView.pauseTimers() } catch (_: Exception) {}
                    }
                } else {
                    tab.webView.post {
                        try { tab.webView.onPause() } catch (_: Exception) {}
                        try { tab.webView.pauseTimers() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                try { tab.webView.onPause() } catch (_: Exception) {}
            }
        }
        releaseWakeLock()
        try {
            @Suppress("DEPRECATION")
            (getSystemService(AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocus(null)
        } catch (_: Exception) {}
    }

    private fun resumeBackgroundMedia() {
        if (!::tabManager.isInitialized) return
        val tabs = tabManager.getAllTabs()
        // #region agent log
        SafeerDbg.log(
            "H211",
            "MainActivity.kt:onResume",
            "resumeAll",
            JSONObject().put("tabs", tabs.size).put("paused", webViewsPaused)
        )
        // #endregion
        try { tabs.firstOrNull()?.webView?.resumeTimers() } catch (_: Exception) {}
        for (tab in tabs) {
            try { tab.webView.onResume() } catch (_: Exception) {}
            try {
                tab.webView.evaluateJavascript(
                    "try{window._safeer_app_bg=false;sessionStorage.removeItem('safeer_app_bg');}catch(e){}",
                    null
                )
            } catch (_: Exception) {}
        }
        webViewsPaused = false
    }

    private fun stopPageMedia(reason: String) {
        if (!::tabManager.isInitialized) return
        val tabs = tabManager.getAllTabs()
        // #region agent log
        SafeerDbg.log(
            "H220",
            "MainActivity.kt:$reason",
            "stopPage",
            JSONObject().put("tabs", tabs.size)
        )
        // #endregion
        val js = """
            (function(){
                window._safeer_app_bg = true;
                try { sessionStorage.setItem('safeer_app_bg','1'); } catch (eS) {}
                try { if (window._safeerSiteAgent && window._safeerSiteAgent.clearWant) window._safeerSiteAgent.clearWant(); } catch (e) {}
                var n = 0;
                document.querySelectorAll('video,audio').forEach(function(m){
                    try { m.pause(); m.muted = true; m.volume = 0; n++; } catch (e2) {}
                });
                try { if (navigator.mediaSession) navigator.mediaSession.playbackState = 'paused'; } catch (e3) {}
                try { if (window._safeerDbg) window._safeerDbg('H220','site_agent.js','stopPage',{n:n,path:(location.pathname||'').slice(0,40)}); } catch (e4) {}
                return n;
            })();
        """.trimIndent()
        for (tab in tabs) {
            try { tab.webView.evaluateJavascript(js, null) } catch (_: Exception) {}
        }
        try {
            @Suppress("DEPRECATION")
            (getSystemService(AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocus(null)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        silenceBackgroundMedia("onPause")
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumeBackgroundMedia()
    }

    override fun onStop() {
        silenceBackgroundMedia("onStop")
        if (::tabManager.isInitialized) {
            val tab = tabManager.getActiveTab()
            val url = tab?.url ?: ""
            if (url.contains("xploretv", ignoreCase = true) || url.contains("a1xploretv", ignoreCase = true)) {
                // #region agent log
                SafeerDbg.log("H222", "MainActivity.kt:onStop", "leave xplore", JSONObject().put("url", url.take(120)))
                // #endregion
                try { tab?.webView?.loadUrl("file:///android_asset/brave_home.html") } catch (_: Exception) {}
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        silenceBackgroundMedia("onDestroy")
        if (::tabManager.isInitialized) {
            for (tab in tabManager.getAllTabs()) {
                try { tab.webView.destroy() } catch (_: Exception) {}
            }
        }
        try { debugJsReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        val url = intent?.dataString
        if (!url.isNullOrEmpty()) {
            val activeTab = tabManager.getActiveTab()
            if (activeTab != null) {
                activeTab.webView.loadUrl(url)
            } else {
                tabManager.createTab(this, url, true)
            }
        } else if (intent?.action == Intent.ACTION_MAIN) {
            // #region agent log
            SafeerDbg.log(
                "H110",
                "MainActivity.kt:onNewIntent",
                "launcher home",
                org.json.JSONObject().put("action", intent.action ?: "")
            )
            // #endregion
            showBrowserStartPage()
        }
    }

    private fun showBrowserStartPage() {
        try {
            customVideoCallback?.onCustomViewHidden()
        } catch (_: Exception) {}
        try {
            customVideoView?.let { mainRoot.removeView(it) }
        } catch (_: Exception) {}
        customVideoView = null
        customVideoCallback = null
        webViewContainer.visibility = View.VISIBLE
        mobileTopBar.visibility = View.VISIBLE
        mobileTopBar.translationY = 0f
        val home = "file:///android_asset/brave_home.html"
        val activeTab = tabManager.getActiveTab()
        if (activeTab != null) {
            stopPageMedia("startPage")
            activeTab.webView.loadUrl(home)
        } else {
            tabManager.createTab(this, home, true)
        }
    }

    private fun initViews() {
        mainRoot = findViewById(R.id.mainRoot)
        mobileTopBar = findViewById(R.id.mobileTopBar)
        btnBack = findViewById(R.id.btnBack)
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

        searchSuggestionsOverlay = findViewById(R.id.searchSuggestionsOverlay)
        portalChipsContainer = findViewById(R.id.portalChipsContainer)
        suggestionsListContainer = findViewById(R.id.suggestionsListContainer)

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
        try {
            wv.setOnScrollChanged { direction, scrollY -> handlePageScroll(direction, scrollY) }
            wv.setOnChromeHidden { hidden ->
                val stayKiosk = isKioskUrl(tab.url) || isKioskUrl(wv.url ?: "")
                if (hidden || stayKiosk) {
                    mobileTopBar.visibility = View.GONE
                } else if (customVideoView == null) {
                    mobileTopBar.visibility = View.VISIBLE
                    mobileTopBar.translationY = 0f
                }
            }
        } catch (_: Exception) {}

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
                if (newUrl.contains("youtube.com/tv", ignoreCase = true)) {
                    hideKeyboard()
                    editUrl.clearFocus()
                    searchSuggestionsOverlay.visibility = View.GONE
                    if (mobileTopBar.translationY != 0f) {
                        mobileTopBar.animate().translationY(0f).setDuration(180).start()
                    }
                    wv.requestFocus()
                } else if (isKioskUrl(newUrl)) {
                    hideKeyboard()
                    editUrl.clearFocus()
                    searchSuggestionsOverlay.visibility = View.GONE
                    mobileTopBar.visibility = View.GONE
                    wv.requestFocus()
                } else if (customVideoView == null) {
                    mobileTopBar.visibility = View.VISIBLE
                    if (mobileTopBar.translationY != 0f) {
                        mobileTopBar.animate().translationY(0f).setDuration(180).start()
                    }
                }
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
                val url = tab.url
                if (url.startsWith("file://") || url.startsWith("about:") || url.isEmpty()) {
                    tvSecurityLock.text = "🦁"
                    tvSecurityLock.setTextColor(Color.parseColor("#10B981"))
                } else {
                    tvSecurityLock.text = if (isSecure) "🔒" else "⚠️"
                    tvSecurityLock.setTextColor(
                        if (isSecure) Color.parseColor("#10B981") else Color.parseColor("#F59E0B")
                    )
                }
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
                val stayKiosk = isKioskUrl(tab.url) || isKioskUrl(wv.url ?: "")
                mobileTopBar.visibility = if (stayKiosk) View.GONE else View.VISIBLE
                webViewContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun setupOmnibox() {
        editUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                omniboxContainer.setBackgroundResource(R.drawable.bg_tab_card_active)
                searchSuggestionsOverlay.visibility = View.VISIBLE
                mobileTopBar.animate().translationY(0f).setDuration(150).start()
                val currentUrl = tabManager.getActiveTab()?.url ?: ""
                if (currentUrl.startsWith("https://www.google.com") || currentUrl.startsWith("file:///android_asset") || currentUrl == "about:blank") {
                    editUrl.setText("")
                } else {
                    editUrl.setText(currentUrl)
                    editUrl.selectAll()
                }
                btnClearUrl.visibility = if (editUrl.text.isNotEmpty()) View.VISIBLE else View.GONE
                fetchGoogleSuggestions(editUrl.text.toString())
            } else {
                omniboxContainer.setBackgroundResource(R.drawable.bg_mobile_omnibox)
                btnClearUrl.visibility = View.GONE
                searchSuggestionsOverlay.visibility = View.GONE
                val activeTab = tabManager.getActiveTab()
                updateOmniboxDisplay(activeTab?.url ?: "", activeTab?.webView?.title)
            }
        }

        editUrl.setOnClickListener {
            showKeyboard()
        }

        omniboxContainer.setOnClickListener {
            editUrl.requestFocus()
            showKeyboard()
        }

        editUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (editUrl.hasFocus()) {
                    btnClearUrl.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
                    fetchGoogleSuggestions(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performNavigation(editUrl.text.toString().trim())
                hideKeyboard()
                editUrl.clearFocus()
                searchSuggestionsOverlay.visibility = View.GONE
                tabManager.getActiveTab()?.webView?.requestFocus()
                true
            } else {
                false
            }
        }

        editUrl.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    hideKeyboard()
                    if (portalChipsContainer.childCount > 0) {
                        portalChipsContainer.getChildAt(0).requestFocus()
                        return@setOnKeyListener true
                    } else if (suggestionsListContainer.childCount > 0) {
                        suggestionsListContainer.getChildAt(0).requestFocus()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        btnClearUrl.setOnClickListener {
            editUrl.setText("")
            editUrl.requestFocus()
            showKeyboard()
            suggestionsListContainer.removeAllViews()
        }

        btnSearchTrigger.setOnClickListener {
            val text = editUrl.text.toString().trim()
            if (text.isNotEmpty()) {
                performNavigation(text)
                hideKeyboard()
                editUrl.clearFocus()
                searchSuggestionsOverlay.visibility = View.GONE
                tabManager.getActiveTab()?.webView?.requestFocus()
            } else {
                editUrl.requestFocus()
                showKeyboard()
            }
        }
    }

    private fun setupSearchSuggestions() {
        renderPortals()
    }

    private fun renderPortals() {
        portalChipsContainer.removeAllViews()
        val portals = PortalManager.loadPortals(this)
        val density = resources.displayMetrics.density

        for (item in portals) {
            val btn = Button(this).apply {
                text = item.title
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    setTextColor(resources.getColorStateList(R.color.color_portal_chip_text, theme))
                } else {
                    setTextColor(Color.parseColor("#00E5FF"))
                }
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_portal_chip)
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                isFocusable = true
                isFocusableInTouchMode = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (38 * density).toInt()
                )
                lp.marginEnd = (10 * density).toInt()
                layoutParams = lp

                setOnClickListener {
                    performNavigation(item.url)
                    closeSuggestionsAndFocusWeb()
                }

                setOnLongClickListener {
                    PortalManager.showEditPortalsDialog(this@MainActivity) {
                        renderPortals()
                    }
                    true
                }

                setOnKeyListener { view, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            editUrl.requestFocus()
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (suggestionsListContainer.childCount > 0) {
                                suggestionsListContainer.getChildAt(0).requestFocus()
                                return@setOnKeyListener true
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            val nextIdx = portalChipsContainer.indexOfChild(view) + 1
                            if (nextIdx < portalChipsContainer.childCount) {
                                portalChipsContainer.getChildAt(nextIdx).requestFocus()
                                return@setOnKeyListener true
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            val prevIdx = portalChipsContainer.indexOfChild(view) - 1
                            if (prevIdx >= 0) {
                                portalChipsContainer.getChildAt(prevIdx).requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
            portalChipsContainer.addView(btn)
        }

        // Add ⚙️ Uredi Portale button at the end
        val editBtn = Button(this).apply {
            text = "⚙️ Uredi"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                setTextColor(resources.getColorStateList(R.color.color_portal_chip_text, theme))
            } else {
                setTextColor(Color.parseColor("#94A3B8"))
            }
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_portal_chip)
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            isFocusable = true
            isFocusableInTouchMode = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (38 * density).toInt()
            )
            layoutParams = lp

            setOnClickListener {
                PortalManager.showEditPortalsDialog(this@MainActivity) {
                    renderPortals()
                }
            }

            setOnKeyListener { view, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        editUrl.requestFocus()
                        return@setOnKeyListener true
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        val prevIdx = portalChipsContainer.indexOfChild(view) - 1
                        if (prevIdx >= 0) {
                            portalChipsContainer.getChildAt(prevIdx).requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }
        portalChipsContainer.addView(editBtn)
    }

    private fun closeSuggestionsAndFocusWeb() {
        hideKeyboard()
        editUrl.clearFocus()
        searchSuggestionsOverlay.visibility = View.GONE
        tabManager.getActiveTab()?.webView?.requestFocus()
    }

    private fun fetchGoogleSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            runOnUiThread { suggestionsListContainer.removeAllViews() }
            return
        }
        Thread {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = java.net.URL("https://suggestqueries.google.com/complete/search?client=chrome&q=$encoded")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 1200
                conn.readTimeout = 1200
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = org.json.JSONArray(responseText)
                    if (jsonArr.length() > 1) {
                        val suggestionsArr = jsonArr.getJSONArray(1)
                        val list = mutableListOf<String>()
                        for (i in 0 until minOf(suggestionsArr.length(), 5)) {
                            list.add(suggestionsArr.getString(i))
                        }
                        displaySuggestions(list)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun displaySuggestions(list: List<String>) {
        runOnUiThread {
            suggestionsListContainer.removeAllViews()
            if (list.isEmpty()) return@runOnUiThread

            for (item in list) {
                val tv = TextView(this).apply {
                    text = "🔍  $item"
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 14f
                    setBackgroundResource(R.drawable.bg_mobile_omnibox)
                    setPadding(28, 16, 28, 16)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 4, 0, 4)
                    layoutParams = lp

                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            setBackgroundResource(R.drawable.bg_tab_card_active)
                        } else {
                            setBackgroundResource(R.drawable.bg_mobile_omnibox)
                        }
                    }

                    setOnClickListener {
                        performNavigation(item)
                        closeSuggestionsAndFocusWeb()
                    }
                }
                suggestionsListContainer.addView(tv)
            }
        }
    }

    private fun updateOmniboxDisplay(url: String, title: String?) {
        if (editUrl.hasFocus()) return

        if (url.isEmpty() || url == "about:blank" || url.startsWith("https://www.google.com") || url.startsWith("file:///android_asset")) {
            editUrl.setText("")
            editUrl.hint = "Iščite na Google ali vnesite naslov..."
            tvSecurityLock.text = "🔍"
            return
        }

        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            val cleanHost = host.removePrefix("www.")
            val path = uri.path ?: ""
            val display = if (!uri.fragment.isNullOrEmpty()) {
                "$cleanHost$path#${uri.fragment}"
            } else if (path.length > 1 && path != "/") {
                "$cleanHost$path"
            } else {
                cleanHost
            }
            editUrl.setText(display)
        } catch (_: Exception) {
            editUrl.setText(url)
        }
    }

    private fun performNavigation(input: String) {
        var cleanInput = input.trim()
        if (cleanInput.isEmpty()) return

        if (cleanInput.startsWith("file:///android_asset/brave_home.html", ignoreCase = true)) {
            cleanInput = cleanInput.removePrefix("file:///android_asset/brave_home.html").trim()
            if (cleanInput.isEmpty()) return
        }

        val activeUrl = tabManager.getActiveTab()?.url ?: ""
        val onYoutubeTv = activeUrl.contains("youtube.com/tv", ignoreCase = true)
        val onXploreTv = activeUrl.contains("xploretv", ignoreCase = true)

        val finalUrl = when {
            cleanInput.startsWith("http://", ignoreCase = true) || cleanInput.startsWith("https://", ignoreCase = true) || cleanInput.startsWith("file://", ignoreCase = true) -> {
                cleanInput
            }
            cleanInput.contains(".") && !cleanInput.contains(" ") -> {
                "https://$cleanInput"
            }
            onXploreTv -> {
                hideKeyboard()
                editUrl.clearFocus()
                tabManager.getActiveTab()?.webView?.requestFocus()
                "https://www.xploretv.si/home?action=search&q=" + URLEncoder.encode(cleanInput, "UTF-8")
            }
            onYoutubeTv -> {
                val ytWv = tabManager.getActiveTab()?.webView
                val escaped = cleanInput.replace("\\", "\\\\").replace("'", "\\'")
                if (ytWv != null) {
                    hideKeyboard()
                    editUrl.clearFocus()
                    ytWv.requestFocus()
                    ytWv.evaluateJavascript(
                        "window._safeer_yt_tv_search ? window._safeer_yt_tv_search('$escaped') : (location.hash = '#/search?q=' + encodeURIComponent('$escaped'));",
                        null
                    )
                    return
                }
                "https://www.youtube.com/tv#/search?q=" + URLEncoder.encode(cleanInput, "UTF-8")
            }
            else -> {
                "https://www.google.com/search?q=" + URLEncoder.encode(cleanInput, "UTF-8")
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
        btnBack.setOnClickListener {
            onBackPressed()
        }

        btnHome.setOnClickListener {
            stopPageMedia("btnHome")
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
            tabManager.createTab(this, "file:///android_asset/brave_home.html", true)
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
            getSharedPreferences("safeer_ui_prefs", MODE_PRIVATE).edit().putBoolean("dark_mode", isDarkModeActive).apply()
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
        val token = currentFocus?.windowToken ?: editUrl.windowToken ?: window.decorView.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
    }

    private var lastCenterClickTime: Long = 0L

    private fun dispatchYoutubeTvKey(webView: ChromiumEngineView?, event: KeyEvent): Boolean {
        if (webView == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && !webView.hasFocus()) {
            hideKeyboard()
            editUrl.clearFocus()
            webView.requestFocus()
        }
        return webView.dispatchKeyEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val activeTab = tabManager.getActiveTab()
        val activeWv = activeTab?.webView
        val curUrl = activeTab?.url?.lowercase() ?: ""
        val isYoutubeTv = curUrl.contains("youtube.com/tv")
        val isXploreTv = curUrl.contains("xploretv")
        val isHydraPlayer = isHydraPlayerUrl(curUrl)
        val isWatchPage = !isYoutubeTv && !isXploreTv && !isHydraPlayer && (curUrl.contains("/watch") || curUrl.contains("/shorts/") || curUrl.contains("youtube.com/embed"))
        val chromeFocused = btnBack.hasFocus() || btnHome.hasFocus() || btnPointerToggle.hasFocus() ||
            btnAddTab.hasFocus() || btnTabCount.hasFocus() || btnMenu.hasFocus() ||
            btnClearUrl.hasFocus() || btnSearchTrigger.hasFocus() ||
            editUrl.hasFocus() || mobileTopBar.hasFocus()

        if (isYoutubeTv && chromeFocused && event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    hideKeyboard()
                    editUrl.clearFocus()
                    activeWv?.requestFocus()
                    return true
                }
            }
        }

        if (isYoutubeTv && !chromeFocused && !virtualPointerView.isPointerVisible &&
            tabSwitcherOverlay.visibility != View.VISIBLE && findInPageBar.visibility != View.VISIBLE
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (curUrl.contains("#/watch") || curUrl.contains("/watch?v=")) {
                        btnBack.requestFocus()
                        return true
                    }
                    return dispatchYoutubeTvKey(activeWv, event)
                }
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    return dispatchYoutubeTvKey(activeWv, event)
                }
            }
        }

        if (isXploreTv && chromeFocused && event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    hideKeyboard()
                    editUrl.clearFocus()
                    activeWv?.requestFocus()
                    return true
                }
            }
        }

        // Xplore TV GO: pass the remote through to the page. Custom spatial nav
        // and key-up swallowing prevented Castlabs from receiving OK/arrows.
        if (isXploreTv && !chromeFocused && !virtualPointerView.isPointerVisible &&
            tabSwitcherOverlay.visibility != View.VISIBLE && findInPageBar.visibility != View.VISIBLE
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    return dispatchYoutubeTvKey(activeWv, event)
                }
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        // 1. Če je odprt Tab Switcher ali iskanje na strani, omogoči nativno D-Pad navigacijo
        if (tabSwitcherOverlay.visibility == View.VISIBLE || findInPageBar.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                onBackPressed()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // 2. 🔴 RDEČI gumb / SEARCH / MENU
        if (keyCode == KeyEvent.KEYCODE_PROG_RED || keyCode == KeyEvent.KEYCODE_MENU || keyCode == 183) {
            mobileTopBar.animate().translationY(0f).setDuration(150).start()
            searchSuggestionsOverlay.visibility = View.VISIBLE
            renderPortals()
            if (portalChipsContainer.childCount > 0) {
                portalChipsContainer.getChildAt(0).requestFocus()
            } else {
                editUrl.requestFocus()
            }
            Toast.makeText(this, "🔍 Hitri TV portali...", Toast.LENGTH_SHORT).show()
            return true
        }

        // 🔍 SEARCH na YouTube TV ali Xplore odpre nativni iskalnik (ne Google)
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (isYoutubeTv) {
                hideKeyboard()
                editUrl.clearFocus()
                activeWv?.requestFocus()
                activeWv?.evaluateJavascript(
                    "window._safeer_yt_tv_search ? window._safeer_yt_tv_search('') : (location.hash = '#/search');",
                    null
                )
                return true
            }
            if (isXploreTv) {
                hideKeyboard()
                editUrl.clearFocus()
                activeWv?.requestFocus()
                return dispatchYoutubeTvKey(activeWv, event)
            }
            mobileTopBar.animate().translationY(0f).setDuration(150).start()
            searchSuggestionsOverlay.visibility = View.VISIBLE
            renderPortals()
            if (portalChipsContainer.childCount > 0) {
                portalChipsContainer.getChildAt(0).requestFocus()
            } else {
                editUrl.requestFocus()
            }
            return true
        }

        // 3. 🟢 ZELENI gumb / INFO -> Preklop kazalca
        if (keyCode == KeyEvent.KEYCODE_PROG_GREEN || keyCode == KeyEvent.KEYCODE_INFO || keyCode == 184) {
            virtualPointerView.isPointerVisible = !virtualPointerView.isPointerVisible
            Toast.makeText(
                this,
                if (virtualPointerView.isPointerVisible) "🖱️ Kazalec TV vklopljen" else "🖐️ D-Pad način vklopljen",
                Toast.LENGTH_SHORT
            ).show()
            return true
        }

        // 4. 🟡 RUMENI gumb / BOOKMARK -> Zaznamki
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BOOKMARK || keyCode == 185) {
            showBookmarksDialog()
            return true
        }

        // 5. 🔵 MODRI gumb -> Preklop celozaslonskega načina (Fullscreen ⛶)
        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE || keyCode == 186 || keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_F) {
            activeWv?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
            return true
        }

        // 6. Če ima fokus iskalno polje (editUrl):
        if (editUrl.hasFocus()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val q = editUrl.text.toString().trim()
                    if (q.isNotEmpty() && q != "https://www.google.com" && q != "www.google.com") {
                        performNavigation(q)
                        hideKeyboard()
                        editUrl.clearFocus()
                        activeWv?.requestFocus()
                        return true
                    } else {
                        showKeyboard()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    hideKeyboard()
                    editUrl.clearFocus()
                    activeWv?.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (editUrl.selectionStart <= 0) {
                        btnHome.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (editUrl.selectionEnd >= editUrl.text.length) {
                        btnSearchTrigger.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    hideKeyboard()
                    editUrl.clearFocus()
                    activeWv?.requestFocus()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        val isSuggestionsOverlayFocused = searchSuggestionsOverlay.hasFocus() || 
                                          portalChipsContainer.hasFocus() || 
                                          suggestionsListContainer.hasFocus()

        if (isSuggestionsOverlayFocused) {
            return super.dispatchKeyEvent(event)
        }

        val isTopBarFocused = btnBack.hasFocus() || btnHome.hasFocus() || btnPointerToggle.hasFocus() ||
                              btnAddTab.hasFocus() || btnTabCount.hasFocus() || btnMenu.hasFocus() ||
                              btnClearUrl.hasFocus() || btnSearchTrigger.hasFocus() || 
                              editUrl.hasFocus() || mobileTopBar.hasFocus()

        // 7. Če je kazalec vklopljen in fokus NI v top bar:
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

        // 8. 🎬 Predvajalniški način (Watch Page / Video Playing)
        if (isWatchPage && !isTopBarFocused && !virtualPointerView.isPointerVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val now = System.currentTimeMillis()
                    if (now - lastCenterClickTime < 380) {
                        activeWv?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
                    } else {
                        activeWv?.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                    }
                    lastCenterClickTime = now
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    activeWv?.evaluateJavascript("window._safeer_seek(-10);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    activeWv?.evaluateJavascript("window._safeer_seek(10);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    editUrl.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    activeWv?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
                    return true
                }
            }
        }

        // 9. 🧭 Standardna D-Pad navigacija po spletnih straneh in predlogih
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (editUrl.hasFocus() && searchSuggestionsOverlay.visibility == View.VISIBLE) {
                    hideKeyboard()
                    if (portalChipsContainer.childCount > 0) {
                        portalChipsContainer.getChildAt(0).requestFocus()
                        return true
                    } else if (suggestionsListContainer.childCount > 0) {
                        suggestionsListContainer.getChildAt(0).requestFocus()
                        return true
                    }
                }
                if (isTopBarFocused) {
                    hideKeyboard()
                    editUrl.clearFocus()
                    activeWv?.requestFocus()
                    activeWv?.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                    return true
                } else if (activeWv != null) {
                    activeWv.requestFocus()
                    activeWv.evaluateJavascript("window._safeer_navigate_spatial('DOWN');", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isTopBarFocused) {
                    return super.dispatchKeyEvent(event)
                } else if (activeWv != null) {
                    val stayInKiosk = isXploreTv || isHydraPlayer
                    activeWv.evaluateJavascript("window._safeer_navigate_spatial('UP');") { result ->
                        if (stayInKiosk) return@evaluateJavascript
                        if (result == "-1" || result == "null" || result == null) {
                            runOnUiThread { 
                                mobileTopBar.visibility = View.VISIBLE
                                mobileTopBar.animate().translationY(0f).setDuration(150).start()
                                editUrl.requestFocus() 
                            }
                        }
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isTopBarFocused) {
                    return super.dispatchKeyEvent(event)
                } else if (activeWv != null) {
                    activeWv.requestFocus()
                    activeWv.evaluateJavascript("window._safeer_navigate_spatial('LEFT');", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isTopBarFocused) {
                    return super.dispatchKeyEvent(event)
                } else if (activeWv != null) {
                    activeWv.requestFocus()
                    activeWv.evaluateJavascript("window._safeer_navigate_spatial('RIGHT');", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (isTopBarFocused) {
                    return super.dispatchKeyEvent(event)
                } else if (activeWv != null) {
                    activeWv.evaluateJavascript("window._safeer_click_focused_card();", null)
                    return true
                }
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (tabManager.count > 1) {
                    tabManager.switchToPrevTab()
                    Toast.makeText(this, "◀ Prejšnji zavihek", Toast.LENGTH_SHORT).show()
                } else {
                    activeWv?.pageUp(false)
                }
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (tabManager.count > 1) {
                    tabManager.switchToNextTab()
                    Toast.makeText(this, "Naslednji zavihek ▶", Toast.LENGTH_SHORT).show()
                } else {
                    activeWv?.pageDown(false)
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                activeWv?.evaluateJavascript("window._safeer_toggle_play_pause();", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                activeWv?.evaluateJavascript("window._safeer_seek(-10);", null)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                activeWv?.evaluateJavascript("window._safeer_seek(10);", null)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                onBackPressed()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        val exitingXploreFs = customVideoView != null &&
            (tabManager.getActiveTab()?.url?.lowercase()?.contains("xploretv") == true)
        if (customVideoView != null) {
            tabManager.getActiveTab()?.webView?.exitFullscreenVideo()
            if (!exitingXploreFs) return
        }

        if (searchSuggestionsOverlay.visibility == View.VISIBLE) {
            searchSuggestionsOverlay.visibility = View.GONE
            hideKeyboard()
            editUrl.clearFocus()
            tabManager.getActiveTab()?.webView?.requestFocus()
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

        if (editUrl.hasFocus()) {
            hideKeyboard()
            editUrl.clearFocus()
            tabManager.getActiveTab()?.webView?.requestFocus()
            return
        }

        val activeTab = tabManager.getActiveTab()
        val curUrl = activeTab?.url?.lowercase() ?: ""
        val isBrowserHome = curUrl.contains("android_asset/brave_home") ||
            curUrl.startsWith("file:///android_asset/brave_home")
        val isYoutubeTv = curUrl.contains("youtube.com/tv")
        val isXploreTv = curUrl.contains("xploretv")
        val isHydraPlayer = isHydraPlayerUrl(curUrl)
        val isWatchPage = !isYoutubeTv && !isXploreTv && !isHydraPlayer && (curUrl.contains("/watch") || curUrl.contains("/shorts/"))

        if (isBrowserHome) {
            // #region agent log
            SafeerDbg.log("H220", "MainActivity.kt:back", "leave browser", JSONObject().put("url", curUrl.take(80)))
            // #endregion
            silenceBackgroundMedia("backHome")
            finish()
            return
        }

        if (isXploreTv) {
            val xv = activeTab?.webView
            if (xv?.canGoBack() == true) {
                xv.goBack()
            } else {
                stopPageMedia("xploreExit")
                xv?.loadUrl("file:///android_asset/brave_home.html")
                mobileTopBar.visibility = View.VISIBLE
            }
            return
        }

        if (isHydraPlayer) {
            val hv = activeTab?.webView
            hv?.evaluateJavascript(
                """
                (function(){
                    if (document.documentElement.classList.contains('safeer-hydra-fs')) {
                        try { if (window._safeer_hydra_unsmash) window._safeer_hydra_unsmash(); } catch (eU) {}
                        return 'unsmash';
                    }
                    return 'exit';
                })();
                """.trimIndent()
            ) { result ->
                if (result != null && result.contains("exit")) {
                    runOnUiThread {
                        if (hv?.canGoBack() == true) {
                            hv.goBack()
                        } else {
                            hv?.loadUrl("https://hydrahd.ws/")
                        }
                    }
                }
            }
            return
        }

        if (isYoutubeTv) {
            val ytWv = activeTab?.webView
            ytWv?.evaluateJavascript(
                """
                (function() {
                    var h = (location.hash || '').toLowerCase();
                    if (h.indexOf('/watch') !== -1 || h.indexOf('/search') !== -1 || h.indexOf('/player') !== -1) {
                        history.back();
                        return 'back';
                    }
                    return 'exit';
                })();
                """.trimIndent()
            ) { result ->
                if (result != null && result.contains("exit")) {
                    runOnUiThread {
                        ytWv.loadUrl("file:///android_asset/brave_home.html")
                    }
                }
            }
            return
        }

        if (isWatchPage) {
            if (activeTab?.webView?.canGoBack() == true) {
                activeTab.webView.goBack()
                return
            } else {
                activeTab?.webView?.loadUrl("file:///android_asset/brave_home.html")
                return
            }
        }

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
