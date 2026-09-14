package si.safeer.tv.cast

import android.content.Context
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Vklop in izklop Safeer Huba na televizorju.
 *
 * Hub ne tece kar sam od sebe. Stece, ko ga uporabnik v Safeer Linku prizge, in tece,
 * dokler ga ne ugasne ali dokler brskalnik ne konca. Zeljo si zapomnimo: kdor je Hub
 * enkrat prizgal, pricakuje, da bo televizor naslednjic spet dosegljiv, ne da bi moral
 * karkoli nastavljati.
 *
 * Zakaj Hub na televizorju ne ugasne, ko uporabnik zapre zaslon Safeer Linka, na telefonu
 * pa bo: televizor je zaslon, ki naj bo dosegljiv, medtem ko uporabnik gleda - ce bi Hub
 * ugasnil, bi telefon izgubil daljinca ravno takrat, ko ga potrebuje. Telefon pa je v roki
 * in ga nima smisla obremenjevati, ko brskalnik ni odprt.
 */
object HubKrmilnik {

    private const val TAG = "SafeerHubKrmilnik"
    const val PREFS = "safeer_cast_prefs"
    const val KLJUC_VKLOPLJEN = "hub_vklopljen"

    @Volatile
    private var streznik: HubStreznik? = null

    @Volatile
    var usmerjevalnik: HubUsmerjevalnik? = null
        private set

    fun tece(): Boolean = streznik?.teceZdaj() == true

    fun vrata(): Int = streznik?.vrata ?: 0

    /** Ali je uporabnik Hub prizgal (tudi ce trenutno ne tece, npr. pred zagonom brskalnika). */
    fun jeZazelen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KLJUC_VKLOPLJEN, false)

    private fun zapomniZeljo(context: Context, vklopljen: Boolean) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KLJUC_VKLOPLJEN, vklopljen).apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Nastavitve ni bilo mogoce zapisati: ${e.message}")
        }
    }

    /** Ob zagonu brskalnika: Hub stece samo, ce ga je uporabnik ze prej prizgal. */
    fun samodejniZagon(context: Context) {
        if (jeZazelen(context) && !tece()) zazeni(context, zapomni = false)
    }

    /**
     * Zazene Hub: streznik, usmerjevalnik in objavo v omrezju. Vrne true, ce tece.
     */
    @Synchronized
    fun zazeni(context: Context, zapomni: Boolean = true): Boolean {
        val app = context.applicationContext
        if (tece()) {
            if (zapomni) zapomniZeljo(app, true)
            return true
        }

        val u = HubUsmerjevalnik(NastavitveShramba(app))
        val s = HubStreznik(
            naZahtevo = { zahteva -> u.odgovori(zahteva) },
            preveriVstopnico = { zahteva -> u.preveriVstopnico(zahteva) },
            naPovezavo = { povezava -> povezi(u, povezava) }
        )
        if (!s.zazeni()) {
            Log.w(TAG, "Huba ni bilo mogoce zagnati.")
            return false
        }
        streznik = s
        usmerjevalnik = u

        HubObjava.objavi(app, s.vrata, imeHuba()) { uspelo ->
            if (!uspelo) {
                // Brez oglasa Hub se vedno dela; naprava, ki ga je ze videla, pozna naslov.
                Log.i(TAG, "Hub tece, oglas v omrezju pa ni uspel.")
            }
        }
        if (zapomni) zapomniZeljo(app, true)
        Log.i(TAG, "Safeer Hub tece na ${naslov()}")
        return true
    }

    /** Ugasne Hub. `zapomni` naj bo true samo, kadar je tako odlocil uporabnik. */
    @Synchronized
    fun ustavi(context: Context?, zapomni: Boolean = true) {
        HubObjava.umakni()
        streznik?.ustavi()
        streznik = null
        usmerjevalnik = null
        if (zapomni && context != null) zapomniZeljo(context.applicationContext, false)
        Log.i(TAG, "Safeer Hub ustavljen.")
    }

    private fun povezi(u: HubUsmerjevalnik, povezava: HubStreznik.Povezava) {
        val odjemalec = object : HubUsmerjevalnik.Odjemalec {
            override val naslov: String = povezava.naslov
            override fun poslji(besedilo: String) = povezava.poslji(besedilo)
            override fun zapri(koda: Int, razlog: String) = povezava.zapri(koda, razlog)
        }
        povezava.naSporocilo = { sporocilo -> u.obdelaj(odjemalec, sporocilo) }
        povezava.naZaprtje = { u.odklopi(odjemalec) }
    }

    private fun imeHuba(): String = "Safeer TV (" + android.os.Build.MODEL + ")"

    /** Naslov, na katerem je Hub dosegljiv; prazen, ce ne tece ali ce ni omrezja. */
    fun naslov(): String {
        val vrata = vrata()
        if (vrata == 0) return ""
        val ip = krajevniNaslov() ?: return ""
        return "http://$ip:$vrata"
    }

    /**
     * Prvi krajevni naslov IPv4 te naprave. Televizor ima navadno enega samega, a na
     * napravah z vec vmesniki izberemo tistega, ki je res v hisnem omrezju.
     */
    fun krajevniNaslov(): String? = try {
        val vmesniki = NetworkInterface.getNetworkInterfaces()
        var najdeno: String? = null
        while (vmesniki.hasMoreElements() && najdeno == null) {
            val vmesnik = vmesniki.nextElement()
            if (!vmesnik.isUp || vmesnik.isLoopback) continue
            val naslovi = vmesnik.inetAddresses
            while (naslovi.hasMoreElements()) {
                val naslov = naslovi.nextElement()
                if (naslov is Inet4Address && naslov.isSiteLocalAddress) {
                    najdeno = naslov.hostAddress
                    break
                }
            }
        }
        najdeno
    } catch (e: Exception) {
        Log.w(TAG, "Naslova ni bilo mogoce ugotoviti: ${e.message}")
        null
    }

    /** Stanje za stran Safeer Linka. */
    fun stanjeJson(context: Context): String {
        val u = usmerjevalnik
        return JsonLahki.Zapis()
            .logicno("tece", tece())
            .logicno("zazelen", jeZazelen(context))
            .niz("naslov", naslov())
            .niz("ime", if (HubObjava.objavljenoIme.isNotBlank()) HubObjava.objavljenoIme else imeHuba())
            .logicno("objavljen", HubObjava.jeObjavljen())
            .stevilo("naprav", (u?.steviloNaprav() ?: 0).toDouble())
            .stevilo("cakajocih", (u?.cakajocePrijave()?.size ?: 0).toDouble())
            .stevilo("seznanjenih", (u?.seznanjeneNaprave()?.size ?: 0).toDouble())
            .toString()
    }

    /** Zetoni seznanjenih naprav v zasebnih nastavitvah brskalnika. */
    private class NastavitveShramba(private val context: Context) : HubUsmerjevalnik.Shramba {
        override fun beri(kljuc: String): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(kljuc, null)

        override fun pisi(kljuc: String, vrednost: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(kljuc, vrednost).apply()
        }
    }
}
