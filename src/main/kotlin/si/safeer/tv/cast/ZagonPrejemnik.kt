package si.safeer.tv.cast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Po vklopu televizorja znova zazene Safeer Link, ce ga je uporabnik prizgal.
 *
 * Brez tega bi moral uporabnik po vsakem izklopu televizorja odpreti brskalnik, preden
 * bi telefon spet nasel zaslon. Ce Link ni prizgan, ta prejemnik ne naredi nicesar.
 */
class ZagonPrejemnik : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val akcija = intent?.action ?: return
        if (akcija != Intent.ACTION_BOOT_COMPLETED &&
            akcija != "android.intent.action.QUICKBOOT_POWERON" &&
            akcija != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context?.applicationContext ?: return
        try {
            HubStoritev.zagotovi(app)
        } catch (e: Throwable) {
            Log.w("SafeerHubZagon", "Zagona po vklopu ni bilo mogoce izvesti: ${e.message}")
        }
    }
}
