package si.safeer.tv

import android.content.Context
import java.util.Locale

/**
 * Jezik vmesnika televizijskega brskalnika.
 *
 * Privzeto se ravna po jeziku televizorja: isti APK nosi prevode za vse nase jezike,
 * Android pa sam izbere pravega glede na nastavitev naprave. Nemec torej dobi nemscino,
 * ne da bi karkoli nastavljal. Kdor zeli drugace (televizor v enem jeziku, brskalnik v
 * drugem), lahko jezik izbere tukaj -- enako kot v razlicici za telefon.
 */
object JezikVmesnika {

    private const val DATOTEKA = "safeer_tv_prefs"
    private const val KLJUC = "pref_language"
    const val SAMODEJNO = "auto"

    /** Jeziki, za katere imamo prevode. Imena so v svojem jeziku, zato jih ni treba prevajati. */
    val JEZIKI = listOf(
        "sl" to "Slovenščina",
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano"
    )

    fun izbrani(context: Context): String =
        context.getSharedPreferences(DATOTEKA, Context.MODE_PRIVATE)
            .getString(KLJUC, SAMODEJNO) ?: SAMODEJNO

    fun nastavi(context: Context, oznaka: String) {
        context.getSharedPreferences(DATOTEKA, Context.MODE_PRIVATE)
            .edit().putString(KLJUC, oznaka).apply()
    }

    /** Ime izbire za prikaz v meniju. */
    fun imeIzbire(context: Context): String {
        val izbran = izbrani(context)
        if (izbran == SAMODEJNO) {
            val samodejno = UiText.get(R.string.ui_lang_auto)
            return if (samodejno.isNotBlank()) samodejno else "Automatic"
        }
        return JEZIKI.firstOrNull { it.first == izbran }?.second ?: izbran
    }

    /**
     * Vrne kontekst z izbranim jezikom. Pri "auto" vrne prvotnega, da ostane v veljavi
     * nastavitev naprave (tudi ce jo uporabnik spremeni, ko brskalnik ze tece).
     */
    fun vKontekstu(osnovni: Context): Context {
        val izbran = izbrani(osnovni)
        if (izbran == SAMODEJNO) return osnovni
        // Locale.setDefault namenoma NE kliceva: to bi spremenilo jezik celega procesa
        // in bi obvisel tudi potem, ko uporabnik izbiro vrne na samodejno.
        return try {
            val jezik = Locale(izbran)
            val nastavitve = android.content.res.Configuration(osnovni.resources.configuration)
            nastavitve.setLocale(jezik)
            osnovni.createConfigurationContext(nastavitve)
        } catch (_: Exception) {
            osnovni
        }
    }
}
