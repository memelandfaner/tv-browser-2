package si.safeer.tv.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Objava Safeer Huba v krajevnem omrezju (mDNS / NSD).
 *
 * Nasprotje HubDiscovery: tam Hub iscemo, tu ga objavljamo. Naprava, ki isce, iz oglasa
 * prebere pot do WebSocketa in pot do vstopnice, zato ji naslova ni treba ugibati in ga
 * uporabniku ni treba nikamor prepisovati.
 *
 * Objavimo samo to, kar je potrebno za povezavo. Nobenega imena uporabnika, nobene vsebine:
 * oglas v omrezju vidi vsak, ki je v njem.
 */
object HubObjava {

    private const val TAG = "SafeerHubObjava"

    /** Pri objavi brez koncne pike; iskanje (HubDiscovery) uporablja obliko s piko. */
    private val VRSTA_STORITVE = HubDiscovery.SERVICE_TYPE.trimEnd('.')

    /** Zgornja meja imena storitve po standardu DNS-SD. */
    private const val NAJDALJSE_IME = 63

    private var nsd: NsdManager? = null
    private var poslusalec: NsdManager.RegistrationListener? = null

    /** Ime, pod katerim nas sistem res objavlja (lahko ga preimenuje, ce je zasedeno). */
    @Volatile
    var objavljenoIme: String = ""
        private set

    fun jeObjavljen(): Boolean = poslusalec != null

    /**
     * Objavi Hub na danih vratih. Povratni klic pove, ali je objava uspela; ce ne, Hub
     * vseeno dela - naprava ga lahko najde po zadnjem znanem naslovu.
     */
    fun objavi(context: Context, vrata: Int, ime: String, koncano: (Boolean) -> Unit = {}) {
        if (poslusalec != null) {
            koncano(true)
            return
        }
        val upravitelj = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (upravitelj == null) {
            Log.i(TAG, "NsdManager ni na voljo; Huba ne objavljam.")
            koncano(false)
            return
        }

        val podatki = NsdServiceInfo().apply {
            // Ime storitve sme biti dolgo do 63 bajtov; krajsanje pod to mejo bi ime
            // odrezalo sredi besede in na telefonu bi pisalo nekaj nerazumljivega.
            serviceName = ime.take(NAJDALJSE_IME)
            serviceType = VRSTA_STORITVE
            port = vrata
            // Iste lastnosti kot jih objavlja Hub na racunalniku, da naprave ne rabijo
            // vedeti, kdo od obeh jim odgovarja. Naslova (host) ne objavljamo: Android
            // imena .local ne zna razresiti, zato bi bilo zavajajoce.
            setAttribute("ws", "/cast/ws")
            setAttribute("ticket", "/cast/ticket")
            setAttribute("auth", "ticket")
            setAttribute("version", HubUsmerjevalnik.RAZLICICA_PROTOKOLA)
            setAttribute("role", "hub")
            setAttribute("name", ime)
        }

        val novi = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                objavljenoIme = info.serviceName ?: ime
                Log.i(TAG, "Hub objavljen kot \"$objavljenoIme\" na vratih $vrata")
                koncano(true)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Objava ni uspela (napaka $errorCode).")
                poslusalec = null
                koncano(false)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Objava Huba umaknjena.")
                objavljenoIme = ""
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Umika objave ni bilo mogoce izvesti (napaka $errorCode).")
            }
        }

        nsd = upravitelj
        poslusalec = novi
        try {
            upravitelj.registerService(podatki, NsdManager.PROTOCOL_DNS_SD, novi)
        } catch (e: Exception) {
            Log.w(TAG, "Objave ni bilo mogoce zagnati: ${e.message}")
            poslusalec = null
            koncano(false)
        }
    }

    /** Umakne objavo. Zapis v omrezju sicer se nekaj casa zivi, zato se naslov vedno preveri. */
    fun umakni() {
        val upravitelj = nsd
        val trenutni = poslusalec
        poslusalec = null
        objavljenoIme = ""
        if (upravitelj == null || trenutni == null) return
        try {
            upravitelj.unregisterService(trenutni)
        } catch (e: Exception) {
            Log.w(TAG, "Objave ni bilo mogoce umakniti: ${e.message}")
        }
    }
}
