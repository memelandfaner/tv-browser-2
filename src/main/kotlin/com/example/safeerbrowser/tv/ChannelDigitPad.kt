package com.example.safeerbrowser

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Classic TV channel entry: 1 → 1st Live TV program, 2 then 5 → 25th.
 * Digits wait [COMMIT_MS] after the last key, or commit at 3 digits.
 */
class ChannelDigitPad(private val host: MainActivity) {
    companion object {
        private const val COMMIT_MS = 1400L
        private const val MAX_DIGITS = 3

        fun digitOf(keyCode: Int): Int? {
            return when (keyCode) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
                KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
                KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
                else -> null
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val buf = StringBuilder()
    private var hud: TextView? = null
    private val commitRun = Runnable { commit() }

    fun accepts(): Boolean {
        if (host.isChromeFocused()) return false
        if (host.editUrl.hasFocus()) return false
        if (host.playback.isNativeActive()) return host.playback.isLiveNative()
        val url = host.activeUrl()
        return TvSite.isXplore(url) && url.contains("/livetv", ignoreCase = true)
    }

    fun hasPending(): Boolean = buf.isNotEmpty()

    fun onDigit(digit: Int, repeatCount: Int): Boolean {
        if (!accepts()) return false
        if (repeatCount > 0) return true
        if (buf.length >= MAX_DIGITS) buf.clear()
        buf.append(digit)
        showHud(buf.toString())
        main.removeCallbacks(commitRun)
        if (buf.length >= MAX_DIGITS) {
            commit()
        } else {
            main.postDelayed(commitRun, COMMIT_MS)
        }
        SafeerDbg.log(
            "H340",
            "ChannelDigitPad.kt:digit",
            "ch digit",
            JSONObject().put("buf", buf.toString())
        )
        return true
    }

    fun confirmOrCancel(): Boolean {
        if (buf.isEmpty()) return false
        commit()
        return true
    }

    fun cancel(): Boolean {
        if (buf.isEmpty()) return false
        buf.clear()
        main.removeCallbacks(commitRun)
        hideHud()
        return true
    }

    private fun commit() {
        main.removeCallbacks(commitRun)
        val raw = buf.toString()
        buf.clear()
        val n = raw.toIntOrNull() ?: 0
        if (n <= 0) {
            hideHud()
            return
        }
        showHud(n.toString())
        host.playback.tuneLiveChannel(n)
        main.postDelayed({ hideHud() }, 2200L)
    }

    private fun showHud(text: String) {
        val root = host.mainRoot
        val tv = hud ?: TextView(host).also { v ->
            v.setTextColor(Color.WHITE)
            v.textSize = 64f
            v.setTypeface(Typeface.DEFAULT_BOLD)
            v.setPadding(48, 24, 48, 28)
            v.setBackgroundColor(Color.parseColor("#CC0B1220"))
            v.gravity = Gravity.CENTER
            v.elevation = 80f
            val lp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            lp.topMargin = 56
            lp.marginEnd = 56
            root.addView(v, lp)
            hud = v
        }
        tv.text = text
        tv.visibility = View.VISIBLE
        tv.bringToFront()
    }

    private fun hideHud() {
        hud?.visibility = View.GONE
    }
}
