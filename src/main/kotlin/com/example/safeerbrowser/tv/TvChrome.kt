package com.example.safeerbrowser

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class TvChrome(private val host: MainActivity) {

    private fun applyPageInset(url: String) {
        val kiosk = SiteProfileResolver.fromUrl(url).hideChrome(url)
        val chromeOn = !kiosk &&
            host.mobileTopBar.visibility == View.VISIBLE &&
            !host.playback.isActive()
        val pad = if (chromeOn) {
            val h = host.mobileTopBar.height
            if (h > 0) h else (56 * host.resources.displayMetrics.density).toInt()
        } else {
            0
        }
        if (host.webViewContainer.paddingTop != pad) {
            host.webViewContainer.setPadding(0, pad, 0, 0)
        }
    }

    fun setChromeHidden(hidden: Boolean) {
        val url = host.activeUrl()
        val stayKiosk = SiteProfileResolver.fromUrl(url).hideChrome(url)
        if (hidden || stayKiosk) {
            host.mobileTopBar.visibility = View.GONE
        } else if (!host.playback.isActive()) {
            host.mobileTopBar.visibility = View.VISIBLE
            host.mobileTopBar.translationY = 0f
        }
        applyPageInset(url)
        host.mobileTopBar.post { applyPageInset(url) }
    }

    fun applyUrlChrome(url: String) {
        if (TvSite.isYoutubeTv(url)) {
            host.hideKeyboard()
            host.editUrl.clearFocus()
            host.searchSuggestionsOverlay.visibility = View.GONE
            if (host.mobileTopBar.translationY != 0f) {
                host.mobileTopBar.animate().translationY(0f).setDuration(180).start()
            }
            host.activeWebView()?.requestFocus()
        } else if (SiteProfileResolver.fromUrl(url).hideChrome(url)) {
            host.hideKeyboard()
            host.editUrl.clearFocus()
            host.searchSuggestionsOverlay.visibility = View.GONE
            host.mobileTopBar.visibility = View.GONE
            host.activeWebView()?.requestFocus()
        } else if (!host.playback.isActive()) {
            host.mobileTopBar.visibility = View.VISIBLE
            if (host.mobileTopBar.translationY != 0f) {
                host.mobileTopBar.animate().translationY(0f).setDuration(180).start()
            }
        }
        applyPageInset(url)
        host.mobileTopBar.post { applyPageInset(url) }
    }

    fun showPortals() {
        host.mobileTopBar.animate().translationY(0f).setDuration(150).start()
        host.searchSuggestionsOverlay.visibility = View.VISIBLE
        renderPortals()
        if (host.portalChipsContainer.childCount > 0) {
            host.portalChipsContainer.getChildAt(0).requestFocus()
        } else {
            host.editUrl.requestFocus()
        }
    }

    fun navigate(url: String) {
        host.performNavigation(url)
    }

    fun renderPortals() {
        val container = host.portalChipsContainer
        container.removeAllViews()
        val portals = PortalManager.loadPortals(host)
        val density = host.resources.displayMetrics.density

        for (item in portals) {
            val btn = Button(host).apply {
                text = item.title
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setTextColor(host.resources.getColorStateList(R.color.color_portal_chip_text, host.theme))
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
                    host.performNavigation(item.url)
                    host.closeSuggestionsAndFocusWeb()
                }

                setOnLongClickListener {
                    PortalManager.showEditPortalsDialog(host) {
                        renderPortals()
                    }
                    true
                }

                setOnKeyListener { view, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            host.editUrl.requestFocus()
                            return@setOnKeyListener true
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (host.suggestionsListContainer.childCount > 0) {
                                host.suggestionsListContainer.getChildAt(0).requestFocus()
                                return@setOnKeyListener true
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            val nextIdx = container.indexOfChild(view) + 1
                            if (nextIdx < container.childCount) {
                                container.getChildAt(nextIdx).requestFocus()
                                return@setOnKeyListener true
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            val prevIdx = container.indexOfChild(view) - 1
                            if (prevIdx >= 0) {
                                container.getChildAt(prevIdx).requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
            container.addView(btn)
        }

        val editBtn = Button(host).apply {
            text = "⚙️ Uredi"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setTextColor(host.resources.getColorStateList(R.color.color_portal_chip_text, host.theme))
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
                PortalManager.showEditPortalsDialog(host) {
                    renderPortals()
                }
            }

            setOnKeyListener { view, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        host.editUrl.requestFocus()
                        return@setOnKeyListener true
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        val prevIdx = container.indexOfChild(view) - 1
                        if (prevIdx >= 0) {
                            container.getChildAt(prevIdx).requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
                false
            }
        }
        container.addView(editBtn)
    }

    fun updateOmniboxDisplay(url: String, title: String?) {
        if (host.editUrl.hasFocus()) return

        if (url.isEmpty() || url == "about:blank" || url.startsWith("https://www.google.com") || url.startsWith("file:///android_asset")) {
            host.editUrl.setText("")
            host.editUrl.hint = "Iščite na Google ali vnesite naslov..."
            host.tvSecurityLock.text = "🔍"
            return
        }

        try {
            val uri = Uri.parse(url)
            val hostName = uri.host ?: url
            val cleanHost = hostName.removePrefix("www.")
            val path = uri.path ?: ""
            val display = if (!uri.fragment.isNullOrEmpty()) {
                "$cleanHost$path#${uri.fragment}"
            } else if (path.length > 1 && path != "/") {
                "$cleanHost$path"
            } else {
                cleanHost
            }
            host.editUrl.setText(display)
        } catch (_: Exception) {
            host.editUrl.setText(url)
        }
    }

    fun toast(msg: String) {
        Toast.makeText(host, msg, Toast.LENGTH_SHORT).show()
    }
}
