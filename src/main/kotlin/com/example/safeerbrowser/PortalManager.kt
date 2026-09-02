package com.example.safeerbrowser

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

data class PortalItem(
    var title: String,
    var url: String
)

typealias Portal = PortalItem

object PortalManager {

    private const val PREFS_NAME = "safeer_portals_prefs"
    private const val KEY_PORTALS = "custom_tv_portals"

    val DEFAULT_PORTALS = listOf(
        PortalItem("🌐 Google", "https://www.google.com"),
        PortalItem("📡 Xplore TV", "https://www.xploretv.si/livetv"),
        PortalItem("📺 YouTube TV", "https://www.youtube.com/tv"),
        PortalItem("🎬 Filmi", "https://hydrahd.ws/"),
        PortalItem("📰 24ur.com", "https://www.24ur.com"),
        PortalItem("🇸🇮 RTV SLO", "https://www.rtvslo.si"),
        PortalItem("🎬 StreamNexus", "https://google.com/search?q=streamnexus+hd"),
        PortalItem("📖 Wikipedia", "https://sl.wikipedia.org"),
        PortalItem("🤖 ChatGPT", "https://chatgpt.com")
    )

    fun loadPortals(context: Context): MutableList<PortalItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PORTALS, null)
        if (jsonStr.isNullOrEmpty()) {
            return DEFAULT_PORTALS.toMutableList()
        }

        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<PortalItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title", "")
                val url = obj.optString("url", "")
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    list.add(PortalItem(title, url))
                }
            }
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {}

        return DEFAULT_PORTALS.toMutableList()
    }

    fun savePortals(context: Context, list: List<PortalItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PORTALS, arr.toString()).apply()
    }

    fun showEditPortalsDialog(activity: Activity, onUpdated: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val portals = loadPortals(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_mobile_menu_dialog)
            setPadding(40, 36, 40, 36)
            layoutParams = ViewGroup.LayoutParams(1100, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Header
        val header = TextView(activity).apply {
            text = "⚙️ Urejanje TV portalov in bližnjic"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)

        // Scrollable List of Portals
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                650
            )
        }

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshList() {
            listContainer.removeAllViews()
            for ((index, item) in portals.withIndex()) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundResource(R.drawable.bg_tab_card)
                    setPadding(24, 16, 24, 16)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 4, 0, 4)
                    layoutParams = lp
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val titleTv = TextView(activity).apply {
                    text = "${item.title}\n(${item.url})"
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(titleTv)

                // Delete Button
                val delBtn = Button(activity).apply {
                    text = "🗑️"
                    textSize = 14f
                    setBackgroundResource(R.drawable.bg_mobile_icon_button)
                    val blp = LinearLayout.LayoutParams(100, 90)
                    blp.marginStart = 16
                    layoutParams = blp
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener {
                        portals.removeAt(index)
                        savePortals(activity, portals)
                        refreshList()
                        onUpdated()
                        Toast.makeText(activity, "Izbrisano: ${item.title}", Toast.LENGTH_SHORT).show()
                    }
                }
                row.addView(delBtn)

                listContainer.addView(row)
            }
        }

        refreshList()
        scroll.addView(listContainer)
        root.addView(scroll)

        // Action Buttons Row
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
            gravity = android.view.Gravity.CENTER
        }

        // Add New Portal Button
        val btnAdd = Button(activity).apply {
            text = "➕ Dodaj nov portal"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            setPadding(32, 16, 32, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                showAddPortalDialog(activity) { newItem ->
                    portals.add(newItem)
                    savePortals(activity, portals)
                    refreshList()
                    onUpdated()
                }
            }
        }
        btnRow.addView(btnAdd)

        // Reset Defaults Button
        val btnReset = Button(activity).apply {
            text = "🔄 Privzeto"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = 16
            layoutParams = lp
            setPadding(28, 16, 28, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                portals.clear()
                portals.addAll(DEFAULT_PORTALS)
                savePortals(activity, portals)
                refreshList()
                onUpdated()
                Toast.makeText(activity, "Portali ponastavljeni na privzete!", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(btnReset)

        // Close Button
        val btnClose = Button(activity).apply {
            text = "Zapri ✕"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = 16
            layoutParams = lp
            setPadding(28, 16, 28, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                dialog.dismiss()
            }
        }
        btnRow.addView(btnClose)

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun showAddPortalDialog(activity: Activity, onAdded: (PortalItem) -> Unit) {
        val addDialog = Dialog(activity)
        addDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        addDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_mobile_menu_dialog)
            setPadding(36, 32, 36, 32)
            layoutParams = ViewGroup.LayoutParams(850, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(activity).apply {
            text = "➕ Dodaj novo TV bližnjico"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        layout.addView(titleTv)

        val editName = EditText(activity).apply {
            hint = "Ime (npr. 🎬 Netflix ali Delo)"
            setTextColor(Color.parseColor("#F8FAFC"))
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            setPadding(24, 16, 24, 16)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        layout.addView(editName)

        val editUrl = EditText(activity).apply {
            hint = "Spletni naslov (npr. https://delo.si)"
            setTextColor(Color.parseColor("#F8FAFC"))
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 16
            layoutParams = lp
            setPadding(24, 16, 24, 16)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        layout.addView(editUrl)

        val bRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 24, 0, 0)
        }

        val btnCancel = Button(activity).apply {
            text = "Prekliči"
            setTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            setPadding(24, 12, 24, 12)
            setOnClickListener { addDialog.dismiss() }
        }
        bRow.addView(btnCancel)

        val btnSave = Button(activity).apply {
            text = "Shrani"
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = 16
            layoutParams = lp
            setPadding(28, 12, 28, 12)
            setOnClickListener {
                val name = editName.text.toString().trim()
                var url = editUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(activity, "Prosimo, vnesite ime in naslov!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                onAdded(PortalItem(name, url))
                addDialog.dismiss()
                Toast.makeText(activity, "Dodan portal: $name", Toast.LENGTH_SHORT).show()
            }
        }
        bRow.addView(btnSave)

        layout.addView(bRow)
        addDialog.setContentView(layout)
        addDialog.show()
    }
}
