package si.safeer.tv.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import si.safeer.tv.R

/**
 * Safeer Link na televizorju tece kot storitev, ne kot del brskalnika.
 *
 * Prej je Hub zivel v dejavnosti: ko je uporabnik zaprl brskalnik, je izginil, in
 * "Poslji na TV" na telefonu ni imel kam poslati. Televizor je zaslon v dnevni sobi -
 * uporabnik pricakuje, da je dosegljiv takrat, ko hoce nekaj poslati nanj, ne takrat,
 * ko je na njem slucajno odprt brskalnik.
 *
 * Zato: kdor Safeer Link na televizorju prizge, ga prizge za stalno. Storitev tece v
 * ospredju (Android drugace strezniski vticnik v ozadju ubije), ZagonPrejemnik pa jo
 * po ponovnem vklopu televizorja zazene znova. Ugasne jo samo uporabnik.
 *
 * Vrsta storitve je "connectedDevice": strezemo napravam, ki so seznanjene s tem
 * televizorjem. To je edina vrsta, ki jo Android dovoli zagnati tudi po vklopu naprave
 * (dataSync in mediaPlayback sta iz BOOT_COMPLETED prepovedana).
 */
class HubStoritev : Service() {

    companion object {
        private const val TAG = "SafeerHubStoritev"
        private const val KANAL = "safeer_link_hub"
        private const val OBVESTILO = 4042

        const val AKCIJA_ZACNI = "si.safeer.tv.cast.HUB_ZACNI"
        const val AKCIJA_KONCAJ = "si.safeer.tv.cast.HUB_KONCAJ"

        /**
         * Uporabnik je Safeer Link prizgal. Hub zazenemo takoj (da vmesnik lahko pove,
         * ali je uspelo), storitev pa poskrbi, da tece naprej, ko brskalnika ni vec.
         */
        fun vklopi(context: Context): Boolean {
            val app = context.applicationContext
            val uspelo = HubKrmilnik.zazeni(app, zapomni = true)
            if (uspelo) zazeniStoritev(app, AKCIJA_ZACNI)
            return uspelo
        }

        /** Uporabnik je Safeer Link ugasnil: Hub ustavimo in zelje si ne zapomnimo vec. */
        fun izklopi(context: Context) {
            val app = context.applicationContext
            HubKrmilnik.ustavi(app, zapomni = true)
            zazeniStoritev(app, AKCIJA_KONCAJ)
        }

        /**
         * Ob zagonu brskalnika in po vklopu televizorja: ce je uporabnik Link ze prizgal,
         * poskrbi, da tece. Ce ga ni, se ne zgodi nic - nobenega obvestila in nobenega
         * omreznega prometa.
         */
        fun zagotovi(context: Context) {
            val app = context.applicationContext
            if (!HubKrmilnik.jeZazelen(app)) return
            zazeniStoritev(app, AKCIJA_ZACNI)
        }

        private fun zazeniStoritev(app: Context, akcija: String) {
            val namera = Intent(app, HubStoritev::class.java).apply { action = akcija }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(namera)
                else app.startService(namera)
            } catch (e: Throwable) {
                // Android lahko zagon iz ozadja zavrne (npr. tik pred ugasnjenjem naprave).
                // Hub v tem primeru tece naprej v procesu brskalnika, dokler ta zivi.
                Log.w(TAG, "Storitve ni bilo mogoce zagnati: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AKCIJA_KONCAJ) {
            HubKrmilnik.ustavi(applicationContext, zapomni = false)
            ustaviOspredje()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!HubKrmilnik.jeZazelen(applicationContext)) {
            ustaviOspredje()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            pripraviKanal()
            startForeground(OBVESTILO, obvestilo())
        } catch (e: Throwable) {
            Log.w(TAG, "Obvestila ni bilo mogoce prikazati: ${e.message}")
        }
        if (!HubKrmilnik.tece() && !HubKrmilnik.zazeni(applicationContext, zapomni = false)) {
            Log.w(TAG, "Huba ni bilo mogoce zagnati; storitev koncujem.")
            ustaviOspredje()
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY: ce Android storitev ubije zaradi pomnilnika, naj jo po sprostitvi
        // zazene znova - uporabnik je povedal, da naj bo televizor dosegljiv.
        return START_STICKY
    }

    override fun onDestroy() {
        // Hub zivi v istem procesu; ko gre storitev, gre z njo tudi vticnik.
        HubKrmilnik.ustavi(applicationContext, zapomni = false)
        super.onDestroy()
    }

    private fun ustaviOspredje() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Throwable) {
        }
    }

    private fun pripraviKanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val upravitelj = getSystemService(NotificationManager::class.java) ?: return
        if (upravitelj.getNotificationChannel(KANAL) != null) return
        val kanal = NotificationChannel(
            KANAL,
            getString(R.string.hub_obvestilo_naslov),
            NotificationManager.IMPORTANCE_LOW
        )
        kanal.description = getString(R.string.hub_obvestilo_besedilo)
        kanal.setShowBadge(false)
        upravitelj.createNotificationChannel(kanal)
    }

    private fun obvestilo(): Notification {
        val gradnik = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, KANAL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return gradnik
            .setContentTitle(getString(R.string.hub_obvestilo_naslov))
            .setContentText(getString(R.string.hub_obvestilo_besedilo))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }
}
