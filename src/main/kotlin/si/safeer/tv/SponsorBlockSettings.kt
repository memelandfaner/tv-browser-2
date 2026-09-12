package si.safeer.tv

import android.content.Context

/** ⏭ SponsorBlock: skipping of sponsor segments on YouTube (on by default; the viewer can switch it off in the menu). */
object SponsorBlockSettings {
    private const val PREFS = "safeer_ui_prefs"
    private const val KEY = "sponsorblock_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
