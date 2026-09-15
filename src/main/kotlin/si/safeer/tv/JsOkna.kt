package si.safeer.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Okna JavaScripta: alert(), confirm(), prompt() in vprasanje pred zapustitvijo strani.
 *
 * Privzeti WebChromeClient teh oken ne narise, ampak jih tiho preklice. Stran, ki na
 * odgovor caka, se zato ustavi brez pojasnila. Na televizorju je to se bolj moteče,
 * ker uporabnik nima nacina, da bi stran "kliknil mimo".
 *
 * Na TV so besedila vecja, gumbi pa dobijo fokus, da delujejo s krmilnimi tipkami.
 */
object JsOkna {

    private const val NAJVEC_ZNAKOV = 1200
    private const val PRAG_ZA_UTISANJE = 3

    private var kljucStrani: String? = null
    private var stevec = 0
    private var utisano = false

    /** Ponastavi stetje (ob novi navigaciji). */
    fun ponastavi() {
        kljucStrani = null
        stevec = 0
        utisano = false
    }

    private fun izvor(url: String?): String {
        val u = try { Uri.parse(url ?: "") } catch (e: Throwable) { null }
        val gostitelj = u?.host ?: return UiText.get(R.string.ui_this_page)
        val shema = u.scheme ?: ""
        return if (shema == "https") gostitelj else "$shema://$gostitelj"
    }

    private fun kljuc(url: String?): String {
        val u = try { Uri.parse(url ?: "") } catch (e: Throwable) { null }
        if (u == null) return url ?: ""
        return (u.scheme ?: "") + "://" + (u.host ?: "") + (u.path ?: "")
    }

    private fun aktivnost(context: Context?): Activity? {
        var c = context
        var varovalo = 0
        while (c is ContextWrapper && varovalo < 20) {
            if (c is Activity) return c
            c = c.baseContext
            varovalo += 1
        }
        return null
    }

    private fun smemoPokazati(url: String?): Boolean {
        val k = kljuc(url)
        if (k != kljucStrani) {
            kljucStrani = k
            stevec = 0
            utisano = false
        }
        if (utisano) return false
        stevec += 1
        return true
    }

    private fun pokazi(
        view: View?,
        url: String?,
        sporocilo: String?,
        privzetoBesedilo: String?,
        jePrompt: Boolean,
        potrdiNapis: String,
        preklicNapis: String?,
        koncano: (Boolean, String?) -> Unit
    ): Boolean {
        val dejavnost = aktivnost(view?.context)
        if (view == null || dejavnost == null || dejavnost.isFinishing || !view.isShown) {
            koncano(false, null)
            return true
        }
        if (!smemoPokazati(url)) {
            koncano(false, null)
            return true
        }

        val gostota = dejavnost.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * gostota).toInt()

        val vsebina = LinearLayout(dejavnost)
        vsebina.orientation = LinearLayout.VERTICAL
        vsebina.setPadding(dp(28), dp(16), dp(28), dp(8))

        val besedilo = TextView(dejavnost)
        besedilo.text = (sporocilo ?: "").take(NAJVEC_ZNAKOV)
        besedilo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        vsebina.addView(besedilo)

        var polje: EditText? = null
        if (jePrompt) {
            val e = EditText(dejavnost)
            e.setText(privzetoBesedilo ?: "")
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.setSingleLine(true)
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            e.isFocusableInTouchMode = true
            e.setSelection(e.text?.length ?: 0)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            vsebina.addView(e, lp)
            polje = e
        }

        var utisaj: CheckBox? = null
        if (stevec >= PRAG_ZA_UTISANJE) {
            val c = CheckBox(dejavnost)
            c.text = UiText.get(R.string.ui_dont_show_dialogs)
            c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            vsebina.addView(c, lp)
            utisaj = c
        }

        var odgovorjeno = false
        fun odgovori(potrjeno: Boolean) {
            if (odgovorjeno) return
            odgovorjeno = true
            if (utisaj != null && utisaj.isChecked) utisano = true
            koncano(potrjeno, polje?.text?.toString())
        }

        try {
            val graditelj = AlertDialog.Builder(dejavnost)
                .setTitle(izvor(url))
                .setView(vsebina)
                .setCancelable(true)
                .setPositiveButton(potrdiNapis) { _, _ -> odgovori(true) }
                .setOnCancelListener { odgovori(false) }
                .setOnDismissListener { odgovori(false) }
            if (preklicNapis != null) {
                graditelj.setNegativeButton(preklicNapis) { _, _ -> odgovori(false) }
            }
            val okno = graditelj.create()
            okno.show()
            if (polje != null) {
                polje.requestFocus()
            } else {
                okno.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
            }
        } catch (e: Throwable) {
            odgovori(false)
        }
        return true
    }

    fun alert(view: WebView?, url: String?, sporocilo: String?, rezultat: JsResult?): Boolean {
        if (rezultat == null) return false
        return pokazi(view, url, sporocilo, null, false, UiText.get(R.string.ui_ok), null) { _, _ ->
            rezultat.confirm()
        }
    }

    fun confirm(view: WebView?, url: String?, sporocilo: String?, rezultat: JsResult?): Boolean {
        if (rezultat == null) return false
        return pokazi(view, url, sporocilo, null, false, UiText.get(R.string.ui_ok), UiText.get(R.string.ui_cancel)) { potrjeno, _ ->
            if (potrjeno) rezultat.confirm() else rezultat.cancel()
        }
    }

    fun prompt(
        view: WebView?,
        url: String?,
        sporocilo: String?,
        privzeto: String?,
        rezultat: JsPromptResult?
    ): Boolean {
        if (rezultat == null) return false
        return pokazi(view, url, sporocilo, privzeto, true, UiText.get(R.string.ui_ok), UiText.get(R.string.ui_cancel)) { potrjeno, besedilo ->
            if (potrjeno) rezultat.confirm(besedilo ?: "") else rezultat.cancel()
        }
    }

    fun predZapustitvijo(
        view: WebView?,
        url: String?,
        sporocilo: String?,
        rezultat: JsResult?
    ): Boolean {
        if (rezultat == null) return false
        val besedilo = if (sporocilo.isNullOrBlank()) {
            UiText.get(R.string.ui_leave_page_msg)
        } else {
            sporocilo
        }
        return pokazi(view, url, besedilo, null, false, UiText.get(R.string.ui_leave_page), UiText.get(R.string.ui_stay)) { potrjeno, _ ->
            if (potrjeno) rezultat.confirm() else rezultat.cancel()
        }
    }
}
