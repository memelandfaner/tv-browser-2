package com.example.safeerbrowser

import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import org.json.JSONObject

class TvKeyRouter(private val host: MainActivity) {

    private var lastDpadNavAt = 0L

    fun dispatch(event: KeyEvent): Boolean {
        if (host.isScreenOffActive()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                host.toggleScreenOffAudio(false)
            }
            return true
        }

        val keyCode = event.keyCode
        val profile = SiteProfileResolver.fromUrl(host.activeUrl())
        val chromeFocused = host.isChromeFocused()

        if (host.playback.isNativeActive()) {
            if (event.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (host.channelPad.cancel()) return true
                host.handleBrowserBack()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_PROG_RED || keyCode == KeyEvent.KEYCODE_MENU || keyCode == 183) {
                host.chrome.showPortals()
                return true
            }
            val nativeDigit = ChannelDigitPad.digitOf(keyCode)
            if (nativeDigit != null && host.channelPad.accepts()) {
                return host.channelPad.onDigit(nativeDigit, event.repeatCount)
            }
            if (host.playback.handleNativeKey(event)) return true
        }

        if (profile === YoutubeTvSiteProfile) {
            val handled = YoutubeTvSiteProfile.handleKey(event, host)
            if (handled) return true
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK &&
                (profile === YoutubeTvSiteProfile || profile === HydraSiteProfile)
            ) {
                return true
            }
            if (!chromeFocused && profile.consumeActionUp(keyCode)) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    SafeerDbg.log(
                        "H130",
                        "TvKeyRouter.kt:keyup",
                        "consume xplore OK up",
                        JSONObject().put("code", keyCode)
                    )
                }
                return true
            }
            return host.superDispatchKey(event)
        }

        if (host.tabSwitcherOverlay.visibility == View.VISIBLE || host.findInPageBar.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                host.handleBrowserBack()
                return true
            }
            return host.superDispatchKey(event)
        }

        if (keyCode == KeyEvent.KEYCODE_PROG_RED || keyCode == KeyEvent.KEYCODE_MENU || keyCode == 183) {
            host.chrome.showPortals()
            Toast.makeText(host, "🔍 Hitri TV portali...", Toast.LENGTH_SHORT).show()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (profile.handleSearchKey(host)) return true
            host.chrome.showPortals()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_PROG_GREEN || keyCode == KeyEvent.KEYCODE_INFO || keyCode == 184) {
            host.virtualPointerView.isPointerVisible = !host.virtualPointerView.isPointerVisible
            Toast.makeText(
                host,
                if (host.virtualPointerView.isPointerVisible) "🖱️ Kazalec TV vklopljen" else "🖐️ D-Pad način vklopljen",
                Toast.LENGTH_SHORT
            ).show()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BOOKMARK || keyCode == 185) {
            host.showBookmarksDialog()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE || keyCode == 186 || keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_F) {
            host.activeWebView()?.evaluateJavascript("window._safeer_toggle_fullscreen();", null)
            return true
        }

        if (host.isChromeFocused() &&
            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            return host.moveChromeFocus(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        if (host.editUrl.hasFocus()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val q = host.editUrl.text.toString().trim()
                    if (q.isNotEmpty() && q != "https://www.google.com" && q != "www.google.com") {
                        host.performNavigation(q)
                        host.hideKeyboard()
                        host.editUrl.clearFocus()
                        host.activeWebView()?.requestFocus()
                        return true
                    } else {
                        host.showKeyboard()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    host.hideKeyboard()
                    host.editUrl.clearFocus()
                    host.activeWebView()?.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    host.hideKeyboard()
                    host.editUrl.clearFocus()
                    host.activeWebView()?.requestFocus()
                    return true
                }
            }
            return host.superDispatchKey(event)
        }

        val isSuggestionsOverlayFocused = host.searchSuggestionsOverlay.hasFocus() ||
            host.portalChipsContainer.hasFocus() ||
            host.suggestionsListContainer.hasFocus()

        if (isSuggestionsOverlayFocused) {
            return host.superDispatchKey(event)
        }

        val isTopBarFocused = host.isTopBarFocused()

        if (host.virtualPointerView.isPointerVisible && !isTopBarFocused) {
            val activeWv = host.activeWebView()
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (host.virtualPointerView.pointerY < 120f) {
                        host.editUrl.requestFocus()
                    } else {
                        host.virtualPointerView.movePointer(0f, -40f, activeWv)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    host.virtualPointerView.movePointer(0f, 40f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    host.virtualPointerView.movePointer(-40f, 0f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    host.virtualPointerView.movePointer(40f, 0f, activeWv)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (activeWv != null) {
                        host.virtualPointerView.performClickOnWebView(activeWv)
                    }
                    return true
                }
            }
        }

        if (host.editUrl.hasFocus() && host.searchSuggestionsOverlay.visibility == View.VISIBLE &&
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        ) {
            host.hideKeyboard()
            if (host.portalChipsContainer.childCount > 0) {
                host.portalChipsContainer.getChildAt(0).requestFocus()
                return true
            } else if (host.suggestionsListContainer.childCount > 0) {
                host.suggestionsListContainer.getChildAt(0).requestFocus()
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (host.tabManager.count > 1) {
                    host.tabManager.switchToPrevTab()
                    Toast.makeText(host, "◀ Prejšnji zavihek", Toast.LENGTH_SHORT).show()
                } else {
                    host.activeWebView()?.pageUp(false)
                }
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (host.tabManager.count > 1) {
                    host.tabManager.switchToNextTab()
                    Toast.makeText(host, "Naslednji zavihek ▶", Toast.LENGTH_SHORT).show()
                } else {
                    host.activeWebView()?.pageDown(false)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (host.channelPad.cancel()) return true
                host.handleBrowserBack()
                return true
            }
        }

        if (isTopBarFocused && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            return host.superDispatchKey(event)
        }

        val dpadMove = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        if (dpadMove && profile !== YoutubeTvSiteProfile && event.action == KeyEvent.ACTION_DOWN) {
            val now = android.os.SystemClock.uptimeMillis()
            if (event.repeatCount > 0 && now - lastDpadNavAt < 95L) {
                return true
            }
            lastDpadNavAt = now
        }

        val gridDigit = ChannelDigitPad.digitOf(keyCode)
        if (gridDigit != null && host.channelPad.accepts()) {
            return host.channelPad.onDigit(gridDigit, event.repeatCount)
        }

        if (profile.handleKey(event, host)) return true

        return host.superDispatchKey(event)
    }
}
