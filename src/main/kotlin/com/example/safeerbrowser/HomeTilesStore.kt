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

data class HomeTile(
    var title: String,
    var url: String
)

object HomeTilesStore {

    private const val PREFS = "safeer_home_tiles"
    private const val KEY = "tiles_json"

    val DEFAULT_TILES = listOf(
        HomeTile("Xplore TV", "https://www.xploretv.si/livetv"),
        HomeTile("YouTube", "https://www.youtube.com/tv"),
        HomeTile("Filmi", "https://hydrahd.ws/"),
        HomeTile("24ur", "https://www.24ur.com"),
        HomeTile("RTV SLO", "https://www.rtvslo.si"),
        HomeTile("Google", "https://www.google.com"),
        HomeTile("ChatGPT", "https://chatgpt.com"),
        HomeTile("Kripto", "https://cryptoquant.com"),
        HomeTile("Wikipedija", "https://sl.wikipedia.org")
    )

    fun load(context: Context): MutableList<HomeTile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw.isNullOrEmpty()) return DEFAULT_TILES.toMutableList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<HomeTile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title", "").trim()
                val url = o.optString("url", "").trim()
                if (title.isNotEmpty() && url.isNotEmpty()) out.add(HomeTile(title, url))
            }
            if (out.isEmpty()) DEFAULT_TILES.toMutableList() else {
                var changed = false
                val migrated = out.map { tile ->
                    val next = migrateXploreTile(tile)
                    if (next.url != tile.url) changed = true
                    next
                }.toMutableList()
                if (changed) save(context, migrated)
                migrated
            }
        } catch (_: Exception) {
            DEFAULT_TILES.toMutableList()
        }
    }

    private fun migrateXploreTile(tile: HomeTile): HomeTile {
        if (!tile.title.contains("Xplore", ignoreCase = true)) return tile
        if (!tile.url.contains("xploretv", ignoreCase = true)) return tile
        val low = tile.url.lowercase()
        if (low.contains("/login") || low.contains("/prijava") ||
            low.endsWith("xploretv.si") || low.endsWith("xploretv.si/")
        ) {
            return tile.copy(url = "https://www.xploretv.si/livetv")
        }
        return tile
    }

    fun save(context: Context, tiles: List<HomeTile>) {
        val arr = JSONArray()
        for (t in tiles) {
            arr.put(JSONObject().put("title", t.title).put("url", t.url))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun toJson(context: Context): String {
        val arr = JSONArray()
        for (t in load(context)) {
            arr.put(JSONObject().put("title", t.title).put("url", t.url))
        }
        return arr.toString()
    }

    fun showEditor(activity: Activity, onUpdated: () -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val tiles = load(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_mobile_menu_dialog)
            setPadding(40, 36, 40, 36)
            layoutParams = ViewGroup.LayoutParams(1240, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(TextView(activity).apply {
            text = "Urejanje kartic na naslovnici"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        })

        val listContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 620)
        }

        fun refresh() {
            listContainer.removeAllViews()
            tiles.forEachIndexed { index, item ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundResource(R.drawable.bg_tab_card)
                    setPadding(20, 12, 12, 12)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 4, 0, 4)
                    layoutParams = lp
                }
                row.addView(TextView(activity).apply {
                    text = "${item.title}\n${item.url}"
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                fun iconBtn(label: String, onClick: () -> Unit): Button {
                    return Button(activity).apply {
                        text = label
                        textSize = 14f
                        setBackgroundResource(R.drawable.bg_mobile_icon_button)
                        val blp = LinearLayout.LayoutParams(if (label.length > 2) 128 else 96, 86)
                        blp.marginStart = 8
                        layoutParams = blp
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnClickListener { onClick() }
                    }
                }
                if (index > 0) {
                    row.addView(iconBtn("▲") {
                        val prev = tiles[index - 1]
                        tiles[index - 1] = tiles[index]
                        tiles[index] = prev
                        save(activity, tiles)
                        refresh()
                    })
                }
                if (index < tiles.lastIndex) {
                    row.addView(iconBtn("▼") {
                        val next = tiles[index + 1]
                        tiles[index + 1] = tiles[index]
                        tiles[index] = next
                        save(activity, tiles)
                        refresh()
                    })
                }
                row.addView(iconBtn("Uredi") {
                    showTileForm(activity, item) { updated ->
                        tiles[index] = updated
                        save(activity, tiles)
                        refresh()
                    }
                })
                row.addView(iconBtn("Briši") {
                    tiles.removeAt(index)
                    save(activity, tiles)
                    refresh()
                    Toast.makeText(activity, "Odstranjeno: ${item.title}", Toast.LENGTH_SHORT).show()
                })
                listContainer.addView(row)
            }
        }

        refresh()
        scroll.addView(listContainer)
        root.addView(scroll)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 22, 0, 0)
            gravity = android.view.Gravity.CENTER
        }
        fun actionBtn(label: String, color: String, onClick: () -> Unit): Button {
            return Button(activity).apply {
                text = label
                setTextColor(Color.parseColor(color))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_mobile_omnibox)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = 12
                layoutParams = lp
                setPadding(28, 16, 28, 16)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { onClick() }
            }
        }
        btnRow.addView(actionBtn("Dodaj kartico", "#00E5FF") {
            showTileForm(activity, null) { created ->
                tiles.add(created)
                save(activity, tiles)
                refresh()
            }
        })
        btnRow.addView(actionBtn("Privzeto", "#94A3B8") {
            tiles.clear()
            tiles.addAll(DEFAULT_TILES)
            save(activity, tiles)
            refresh()
            Toast.makeText(activity, "Kartice so spet privzete", Toast.LENGTH_SHORT).show()
        })
        btnRow.addView(actionBtn("Zapri", "#F8FAFC") { dialog.dismiss() })
        root.addView(btnRow)
        dialog.setOnDismissListener { onUpdated() }
        dialog.setContentView(root)
        dialog.show()
    }

    fun showTileForm(activity: Activity, existing: HomeTile?, onSaved: (HomeTile) -> Unit) {
        val addDialog = Dialog(activity)
        addDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        addDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_mobile_menu_dialog)
            setPadding(36, 32, 36, 32)
            layoutParams = ViewGroup.LayoutParams(850, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        layout.addView(TextView(activity).apply {
            text = if (existing == null) "Nova kartica" else "Uredi kartico"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        })
        val editName = EditText(activity).apply {
            hint = "Ime (npr. YouTube)"
            setText(existing?.title ?: "")
            setTextColor(Color.parseColor("#F8FAFC"))
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            setPadding(24, 16, 24, 16)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        layout.addView(editName)
        val editUrl = EditText(activity).apply {
            hint = "Naslov (npr. youtube.com/tv)"
            setText(existing?.url ?: "")
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
        bRow.addView(Button(activity).apply {
            text = "Prekliči"
            setTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.bg_mobile_omnibox)
            setPadding(24, 12, 24, 12)
            setOnClickListener { addDialog.dismiss() }
        })
        bRow.addView(Button(activity).apply {
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
                    Toast.makeText(activity, "Vnesite ime in naslov", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                onSaved(HomeTile(name, url))
                addDialog.dismiss()
            }
        })
        layout.addView(bRow)
        addDialog.setContentView(layout)
        addDialog.show()
        editName.requestFocus()
    }
}
