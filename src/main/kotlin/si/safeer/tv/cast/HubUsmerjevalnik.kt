package si.safeer.tv.cast

import java.security.SecureRandom
import java.util.UUID

/**
 * Usmerjevalnik Safeer Huba na televizorju: register naprav, seznanjanje, vstopnice in
 * posredovanje sporocil Cast/Sync.
 *
 * Govori natanko isti jezik kot Hub na racunalniku (core/cast/protocol.py, hub.py, router.py,
 * razlicica protokola 0.2), zato odjemalcev na telefonu in televizorju ni treba spreminjati -
 * zamenja se le, kje streznik stoji.
 *
 * Tri stvari so tu drugacne kot na racunalniku, in to namenoma:
 *
 *  1. Potrjevanje je krajevno in brez tipkanja. Naprava, ki se zeli prikljuciti, pokaze
 *     sestmestno kodo na svojem zaslonu, televizor pa pokaze isto kodo in ime naprave;
 *     uporabnik pritisne V redu. Na televizor se nikoli ne vnasa zeton - iskati ga in tipkati
 *     z daljincem je muka. Enako kot pri Chromecastu: kdor je v istem omrezju in ga uporabnik
 *     potrdi, sme predvajati.
 *  2. Potrditev ni na voljo po omrezju. Koncne tocke za cakajoce prijave, potrditev in odvzem
 *     dostopa obstajajo samo kot metode, ki jih poklice uporabniski vmesnik televizorja.
 *     Cesar ni v streznku, ni mogoce zlorabiti.
 *  3. Meje so postavljene ze v zasnovi, ne naknadno. Register naprav, cakajoce prijave,
 *     vstopnice in shramba za sinhronizacijo imajo vsak svojo zgornjo mejo, ker je televizor
 *     naprava z malo pomnilnika in ga sistem ob pomanjkanju brez opozorila ubije.
 *
 * Razred ne pozna Androida, da ga je mogoce preizkusiti v navadnem JVM.
 */
class HubUsmerjevalnik(
    private val shramba: Shramba? = null,
    private val ura: () -> Long = { System.currentTimeMillis() },
    private val nakljucni: (Int) -> String = { privzetoNakljucno(it) }
) {

    /** Trajna shramba za zetone seznanjenih naprav (na Androidu SharedPreferences). */
    interface Shramba {
        fun beri(kljuc: String): String?
        fun pisi(kljuc: String, vrednost: String)
    }

    /** Ena povezana naprava, kakor jo vidi usmerjevalnik. */
    interface Odjemalec {
        val naslov: String
        fun poslji(besedilo: String)
        fun zapri(koda: Int, razlog: String)
    }

    private class Naprava(
        val id: String,
        var ime: String,
        var vloga: String,
        var zmoznosti: List<String>,
        var naslov: String,
        var zadnjic: Double,
        var povezava: Odjemalec?
    )

    private class Prijava(
        val pairId: String,
        val deviceId: String,
        val ime: String,
        val pin: String,
        val naslov: String,
        var nastala: Long,
        var potrjena: Boolean = false,
        var zeton: String? = null
    )

    private class Kategorija(
        val ime: String,
        val razlicica: Double,
        val cas: Double,
        val podatkiSurovo: String,
        val vir: String?
    ) {
        val bajtov: Int = podatkiSurovo.toByteArray(Charsets.UTF_8).size
    }

    /** Podatki o cakajoci prijavi za vmesnik televizorja. */
    data class CakajocaPrijava(
        val pairId: String,
        val ime: String,
        val pin: String,
        val naslov: String,
        val starostSekund: Int
    )

    data class SeznanjenaNaprava(val deviceId: String, val ime: String, val seznanjenaOb: Double)

    private val kljucnica = Any()

    private val naprave = LinkedHashMap<String, Naprava>()
    private val posiljatelji = LinkedHashSet<Odjemalec>()
    private val prijave = LinkedHashMap<String, Prijava>()
    private val zetoni = LinkedHashMap<String, SeznanjenaNaprava>()
    private val vstopnice = LinkedHashMap<String, Long>()
    private val sinhronizacija = LinkedHashMap<String, Kategorija>()

    /** Klice se, ko se seznam cakajocih prijav spremeni, da vmesnik pokaze kodo brez spraševanja. */
    @Volatile
    var naSpremembePrijav: (() -> Unit)? = null

    /** Klice se ob spremembi seznama povezanih naprav (za prikaz v Safeer Linku). */
    @Volatile
    var naSpremembeNaprav: (() -> Unit)? = null

    init {
        naloziZetone()
    }

    // ------------------------------------------------------------------ zetoni naprav

    private fun naloziZetone() {
        val zapis = shramba?.beri(KLJUC_ZETONOV) ?: return
        val pogled = JsonLahki.objekt(zapis) ?: return
        for (zeton in pogled.kljuci()) {
            if (zetoni.size >= NAJVEC_SEZNANJENIH) break
            val naprava = pogled.objekt(zeton) ?: continue
            val id = naprava.niz("device_id") ?: continue
            zetoni[zeton] = SeznanjenaNaprava(id, naprava.nizAli("name", id), naprava.stevilo("paired_at") ?: 0.0)
        }
    }

    private fun shraniZetone() {
        val shramba = this.shramba ?: return
        val zapis = JsonLahki.Zapis()
        for ((zeton, naprava) in zetoni) {
            zapis.surovo(
                zeton,
                JsonLahki.Zapis()
                    .niz("device_id", naprava.deviceId)
                    .niz("name", naprava.ime)
                    .stevilo("paired_at", naprava.seznanjenaOb)
                    .toString()
            )
        }
        shramba.pisi(KLJUC_ZETONOV, zapis.toString())
    }

    /** Primerjava v stalnem casu; zeton je varnostna vrednost, ne navaden niz. */
    private fun enaka(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var razlika = 0
        for (i in a.indices) razlika = razlika or (a[i].code xor b[i].code)
        return razlika == 0
    }

    fun jeVeljavenZeton(zeton: String?): Boolean {
        if (zeton.isNullOrEmpty()) return false
        synchronized(kljucnica) {
            for (znani in zetoni.keys) if (enaka(znani, zeton)) return true
        }
        return false
    }

    // ------------------------------------------------------------------ seznanjanje

    private fun pocistiPrijave() {
        val zdaj = ura()
        val potekle = prijave.filterValues {
            zdaj - it.nastala > (if (it.potrjena) PREVZEM_VELJA_MS else PIN_VELJA_MS)
        }.keys.toList()
        for (kljuc in potekle) prijave.remove(kljuc)
    }

    /**
     * Naprava se prijavi in dobi kodo, ki jo pokaze na svojem zaslonu. Vrne null, ce je
     * cakajocih prijav prevec - takrat naj naprava poskusi cez nekaj minut.
     */
    fun zacniSeznanitev(deviceId: String, ime: String, naslov: String): Pair<String, String>? {
        synchronized(kljucnica) {
            pocistiPrijave()
            // Ista naprava, ki poskusa znova, naj ne kopici prijav.
            val stare = prijave.filterValues { it.deviceId == deviceId }.keys.toList()
            for (kljuc in stare) prijave.remove(kljuc)
            if (prijave.size >= NAJVEC_CAKAJOCIH) return null
            val prijava = Prijava(
                pairId = nakljucni(8),
                deviceId = deviceId,
                ime = if (ime.isBlank()) deviceId else ime,
                pin = pin(),
                naslov = naslov,
                nastala = ura()
            )
            prijave[prijava.pairId] = prijava
            naSpremembePrijav?.invoke()
            return prijava.pairId to prijava.pin
        }
    }

    /** Sestmestna koda. SecureRandom, ne navadni random - to je varnostna vrednost. */
    private fun pin(): String {
        val stevilka = 100000 + (nakljucniStevec.nextInt(900000))
        return stevilka.toString()
    }

    /** Kaj caka na potrditev; vmesnik televizorja pokaze ime in kodo. */
    fun cakajocePrijave(): List<CakajocaPrijava> = synchronized(kljucnica) {
        pocistiPrijave()
        prijave.values.map {
            CakajocaPrijava(it.pairId, it.ime, it.pin, it.naslov, ((ura() - it.nastala) / 1000).toInt())
        }
    }

    /**
     * Uporabnik je na televizorju pritisnil V redu. Izda zeton, ki ga naprava prevzame sama -
     * televizor ga nikoli ne pokaze in uporabniku ga ni treba nikamor prepisovati.
     */
    fun potrdiPrijavo(pairId: String): Boolean = synchronized(kljucnica) {
        pocistiPrijave()
        val prijava = prijave[pairId] ?: return false
        if (zetoni.size >= NAJVEC_SEZNANJENIH) {
            // Raje povemo, da ne gre, kot da bi seznam rasel v nedogled.
            return false
        }
        prijava.potrjena = true
        prijava.nastala = ura()
        prijava.zeton = "saf_tv_" + nakljucni(24)
        zetoni[prijava.zeton!!] = SeznanjenaNaprava(prijava.deviceId, prijava.ime, ura() / 1000.0)
        shraniZetone()
        naSpremembePrijav?.invoke()
        return true
    }

    fun zavrniPrijavo(pairId: String): Boolean = synchronized(kljucnica) {
        val odstranjena = prijave.remove(pairId) != null
        if (odstranjena) naSpremembePrijav?.invoke()
        return odstranjena
    }

    /** Naprava prevzame svoj zeton. Uspe natanko enkrat. */
    fun prevzemiZeton(pairId: String): String? = synchronized(kljucnica) {
        pocistiPrijave()
        val prijava = prijave[pairId] ?: return null
        if (!prijava.potrjena || prijava.zeton == null) return null
        prijave.remove(pairId)
        naSpremembePrijav?.invoke()
        return prijava.zeton
    }

    fun seznanjeneNaprave(): List<SeznanjenaNaprava> = synchronized(kljucnica) { zetoni.values.toList() }

    /** Odvzame dostop napravi in jo, ce je povezana, tudi odklopi. */
    fun prekliciNapravo(deviceId: String): Int {
        val odklopi = ArrayList<Odjemalec>()
        val koliko: Int
        synchronized(kljucnica) {
            val odvzeti = zetoni.filterValues { it.deviceId == deviceId }.keys.toList()
            for (kljuc in odvzeti) zetoni.remove(kljuc)
            koliko = odvzeti.size
            if (koliko > 0) shraniZetone()
            naprave[deviceId]?.povezava?.let { odklopi.add(it) }
        }
        for (povezava in odklopi) {
            try {
                povezava.zapri(1008, "dostop odvzet")
            } catch (e: Exception) {
                // Naprave, ki je ze izginila, ni treba odklapljati.
            }
        }
        return koliko
    }

    // ------------------------------------------------------------------ vstopnice

    /**
     * Enokratna vstopnica za WebSocket, kratke veljavnosti. Povezava brez nje sploh ne nastane -
     * enako kot na racunalniku, kjer jo izda Controlov SessionManager.
     */
    fun izdajVstopnico(): String = synchronized(kljucnica) {
        pocistiVstopnice()
        if (vstopnice.size >= NAJVEC_VSTOPNIC) {
            // Najstarejsa pade ven; drugace bi jih nekdo lahko naracal poljubno veliko.
            vstopnice.remove(vstopnice.keys.first())
        }
        val vstopnica = nakljucni(16)
        vstopnice[vstopnica] = ura()
        return vstopnica
    }

    private fun pocistiVstopnice() {
        val zdaj = ura()
        val potekle = vstopnice.filterValues { zdaj - it > VSTOPNICA_VELJA_MS }.keys.toList()
        for (kljuc in potekle) vstopnice.remove(kljuc)
    }

    /** Porabi vstopnico; druga uporaba iste ne uspe. */
    fun porabiVstopnico(vstopnica: String?): Boolean {
        if (vstopnica.isNullOrEmpty()) return false
        synchronized(kljucnica) {
            pocistiVstopnice()
            val najdena = vstopnice.keys.firstOrNull { enaka(it, vstopnica) } ?: return false
            vstopnice.remove(najdena)
            return true
        }
    }

    // ------------------------------------------------------------------ register naprav

    private fun napraveJson(): String {
        val prejemniki = naprave.values.filter { it.vloga == "receiver" && it.povezava != null }
        return prejemniki.joinToString(",", "[", "]") { napravaJson(it) }
    }

    private fun napravaJson(naprava: Naprava): String = JsonLahki.Zapis()
        .niz("id", naprava.id)
        .niz("name", naprava.ime)
        .niz("role", naprava.vloga)
        .seznamNizov("capabilities", naprava.zmoznosti)
        .niz("ip", naprava.naslov)
        .nic("port")
        .stevilo("last_seen", naprava.zadnjic)
        .toString()

    /** Seznam povezanih prejemnikov za vmesnik in za koncno tocko /cast/devices. */
    fun povezaniPrejemniki(): String = synchronized(kljucnica) { napraveJson() }

    fun steviloNaprav(): Int = synchronized(kljucnica) { naprave.count { it.value.povezava != null } }

    private fun idPovezave(povezava: Odjemalec): String? =
        naprave.entries.firstOrNull { it.value.povezava === povezava }?.key

    /** Nova povezava: usmerjevalnik prevzame njena sporocila in pospravi za njo. */
    fun sprejmiPovezavo(odjemalec: Odjemalec, nastaviPoslusalca: ((String) -> Unit, () -> Unit) -> Unit) {
        nastaviPoslusalca({ sporocilo -> obdelaj(odjemalec, sporocilo) }, { odklopi(odjemalec) })
    }

    fun odklopi(povezava: Odjemalec) {
        var spremenjeno = false
        synchronized(kljucnica) {
            val odklopljeni = naprave.filterValues { it.povezava === povezava }.keys.toList()
            for (id in odklopljeni) {
                val naprava = naprave[id] ?: continue
                naprava.povezava = null
                if (naprava.vloga == "receiver") spremenjeno = true
                // Naprave ne pozabimo takoj: ime in zmoznosti so uporabni, ko se vrne.
                // Ce jih je prevec, pade ven najstarejsa odklopljena.
                pocistiRegister()
            }
            posiljatelji.remove(povezava)
        }
        if (spremenjeno) {
            objaviNaprave()
            naSpremembeNaprav?.invoke()
        }
    }

    private fun pocistiRegister() {
        while (naprave.size > NAJVEC_NAPRAV) {
            val odvecna = naprave.entries.firstOrNull { it.value.povezava == null } ?: break
            naprave.remove(odvecna.key)
        }
    }

    // ------------------------------------------------------------------ obdelava sporocil

    fun obdelaj(od: Odjemalec, surovo: String) {
        val odgovor = odgovorNa(od, surovo)
        if (odgovor != null) od.poslji(odgovor)
    }

    /**
     * Vrne odgovor, ki naj se poslje posiljatelju, ali null, ce odgovora ni.
     * Locena metoda zato, da jo je mogoce preizkusiti brez omrezja.
     */
    fun odgovorNa(od: Odjemalec, surovo: String): String? {
        val sporocilo = JsonLahki.objekt(surovo)
            ?: return potrditev("unknown", "error", "Neveljavno sporočilo.")
        val tip = sporocilo.niz("type")
        val id = sporocilo.nizAli("id", "unknown")
        val prostor = prostorOd(tip)

        if (tip.isNullOrEmpty()) return potrditev(id, "error", "Sporočilu manjka polje 'type'.")

        if (tip == "cast.register") return registriraj(od, sporocilo, id)

        if (tip == "cast.ping") return ovojnica("cast.pong", id).toString()

        if (tip in SYNC_POSREDOVANJE) return usmeriSinhronizacijo(od, sporocilo, surovo, id)

        if (tip == "sync.ack") {
            val cilj = sporocilo.niz("target")
            val povezava = synchronized(kljucnica) { naprave[cilj]?.povezava }
            povezava?.poslji(surovo)
            return null
        }

        if (tip in CAST_POSREDOVANJE) {
            val cilj = sporocilo.niz("target")
            val prejemnik = synchronized(kljucnica) {
                naprave[cilj]?.takeIf { it.vloga == "receiver" }?.povezava
            } ?: return potrditev(id, "rejected", "Ciljna naprava '${cilj ?: ""}' ni povezana ali ne obstaja.")
            return if (posljiVarno(prejemnik, surovo)) potrditev(id, "accepted")
            else potrditev(id, "error", "Napaka pri posredovanju prejemniku.")
        }

        if (tip == "cast.status") {
            val deviceId = sporocilo.niz("device_id")
            synchronized(kljucnica) {
                naprave[deviceId]?.zadnjic = ura() / 1000.0
            }
            objaviPosiljateljem(surovo)
            return null
        }

        if (tip == "cast.ack") return null

        // Kar ni na seznamu, se ne posreduje nikamor. Dovoljenja se ne smejo siriti po nesreci.
        return potrditev(id, "error", "Neznan tip sporočila: '$tip'", prostor)
    }

    private fun registriraj(od: Odjemalec, sporocilo: JsonLahki.Pogled, id: String): String {
        val tovor = sporocilo.objekt("payload")
        val deviceId = tovor?.niz("device_id")
        if (deviceId.isNullOrBlank()) return potrditev(id, "rejected", "Manjka device_id.")

        val vloga = tovor.niz("role") ?: "receiver"
        val zmoznosti = tovor.nizi("capabilities").ifEmpty { listOf("url", "control") }
        var jePrejemnik = false
        synchronized(kljucnica) {
            if (!naprave.containsKey(deviceId) && naprave.size >= NAJVEC_NAPRAV) {
                pocistiRegister()
                if (naprave.size >= NAJVEC_NAPRAV) {
                    return potrditev(id, "rejected", "Preveč naprav; odklopite katero od prejšnjih.")
                }
            }
            val naprava = naprave.getOrPut(deviceId) {
                Naprava(deviceId, deviceId, vloga, zmoznosti, od.naslov, ura() / 1000.0, null)
            }
            naprava.ime = (tovor.niz("name")?.takeIf { it.isNotBlank() } ?: deviceId).take(NAJVEC_IMENA)
            naprava.vloga = vloga
            naprava.zmoznosti = zmoznosti
            // Naslov vzamemo iz vticnice, ne iz tega, kar naprava trdi o sebi.
            naprava.naslov = od.naslov
            naprava.zadnjic = ura() / 1000.0
            naprava.povezava = od
            if (vloga == "receiver") {
                jePrejemnik = true
            } else {
                posiljatelji.add(od)
            }
        }
        if (jePrejemnik) {
            objaviNaprave()
        } else {
            posljiVarno(od, ovojnica("cast.devices").surovo("devices", povezaniPrejemniki()).toString())
        }
        naSpremembeNaprav?.invoke()
        return potrditev(id, "accepted")
    }

    private fun usmeriSinhronizacijo(
        od: Odjemalec,
        sporocilo: JsonLahki.Pogled,
        surovo: String,
        id: String
    ): String? {
        val tip = sporocilo.niz("type")
        val posiljatelj = synchronized(kljucnica) { idPovezave(od) }
        val tovor = sporocilo.objekt("payload")

        if (tip == "sync.data" && tovor != null) {
            shraniKategorijo(tovor, posiljatelj)
        }

        if (tip == "sync.request" && tovor != null) {
            val ime = tovor.niz("category") ?: ""
            val shranjeno = synchronized(kljucnica) { sinhronizacija[ime] }
            if (shranjeno != null) {
                val od_razlicice = tovor.stevilo("since_version")
                if (od_razlicice == null || shranjeno.razlicica > od_razlicice) {
                    val tovorNazaj = JsonLahki.Zapis()
                        .niz("category", shranjeno.ime)
                        .stevilo("version", shranjeno.razlicica)
                        .stevilo("timestamp", shranjeno.cas)
                        .surovo("data", shranjeno.podatkiSurovo)
                    val odgovor = ovojnica("sync.data", cilj = posiljatelj)
                        .surovo("payload", tovorNazaj.toString())
                    posljiVarno(od, odgovor.toString())
                }
                return potrditev(id, "accepted", null, "sync")
            }
        }

        val cilj = sporocilo.niz("target")
        if (!cilj.isNullOrEmpty() && cilj != VSEM) {
            val povezava = synchronized(kljucnica) { naprave[cilj]?.povezava }
                ?: return potrditev(id, "rejected", "Naprava '$cilj' ni povezana ali ne obstaja.", "sync")
            return if (posljiVarno(povezava, surovo)) potrditev(id, "accepted", null, "sync")
            else potrditev(id, "error", "Napaka pri posredovanju.", "sync")
        }

        val prejemniki = synchronized(kljucnica) {
            naprave.values.filter {
                it.povezava != null && it.id != posiljatelj &&
                    (it.zmoznosti.contains(ZMOZNOST_SYNC) || it.vloga == "sync-client")
            }.mapNotNull { it.povezava }
        }
        if (prejemniki.isEmpty()) {
            return potrditev(id, "rejected", "Nobena druga naprava ne sinhronizira.", "sync")
        }
        var dostavljeno = 0
        for (povezava in prejemniki) if (posljiVarno(povezava, surovo)) dostavljeno++
        return if (dostavljeno > 0) potrditev(id, "accepted", null, "sync")
        else potrditev(id, "error", "Nobene naprave ni bilo mogoče doseči.", "sync")
    }

    /**
     * Shrani zadnje stanje kategorije, da naprava, ki je bila ugasnjena, lahko dohiti.
     * Meje so tu, ker gre za pomnilnik televizorja: prevelika kategorija se ne shrani,
     * prevec kategorij pa ne nastane.
     */
    private fun shraniKategorijo(tovor: JsonLahki.Pogled, vir: String?) {
        val ime = tovor.niz("category") ?: return
        val podatki = tovor.surovo("data") ?: return
        val bajtov = podatki.toByteArray(Charsets.UTF_8).size
        if (bajtov > NAJVECJA_KATEGORIJA) return
        val cas = tovor.stevilo("timestamp") ?: 0.0
        synchronized(kljucnica) {
            val staro = sinhronizacija[ime]
            if (staro != null && staro.cas > cas) return
            val nova = Kategorija(ime, tovor.stevilo("version") ?: 0.0, cas, podatki, vir)
            sinhronizacija[ime] = nova
            // Ce smo cez skupno mejo, gredo ven najstarejsi vpisi, dokler nismo spet pod njo.
            while (sinhronizacija.size > NAJVEC_KATEGORIJ ||
                sinhronizacija.values.sumOf { it.bajtov } > NAJVEC_SKUPAJ_SYNC
            ) {
                val najstarejsa = sinhronizacija.keys.firstOrNull { it != ime } ?: break
                sinhronizacija.remove(najstarejsa)
            }
        }
    }

    fun kategorijeSinhronizacije(): List<String> = synchronized(kljucnica) { sinhronizacija.keys.sorted() }

    private fun objaviNaprave() {
        val sporocilo = ovojnica("cast.devices").surovo("devices", povezaniPrejemniki()).toString()
        objaviPosiljateljem(sporocilo)
    }

    private fun objaviPosiljateljem(sporocilo: String) {
        val kopija = synchronized(kljucnica) { posiljatelji.toList() }
        for (posiljatelj in kopija) {
            if (!posljiVarno(posiljatelj, sporocilo)) {
                synchronized(kljucnica) { posiljatelji.remove(posiljatelj) }
            }
        }
    }

    private fun posljiVarno(komu: Odjemalec, besedilo: String): Boolean = try {
        komu.poslji(besedilo)
        true
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------------ ovojnice

    private fun ovojnica(tip: String, id: String? = null, cilj: String? = null): JsonLahki.Zapis =
        JsonLahki.Zapis()
            .niz("id", id ?: UUID.randomUUID().toString())
            .niz("type", tip)
            .stevilo("timestamp", ura() / 1000.0)
            .nic("sender")
            .niz("target", cilj)

    private fun potrditev(
        refId: String,
        stanje: String,
        napaka: String? = null,
        prostor: String = "cast"
    ): String = ovojnica("$prostor.ack")
        .niz("ref_id", refId)
        .niz("status", stanje)
        .niz("error", napaka)
        .toString()

    private fun prostorOd(tip: String?): String {
        if (tip.isNullOrEmpty() || !tip.contains(".")) return "cast"
        return tip.substringBefore(".")
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * Koncne tocke, ki jih televizor ponuja po omrezju. Namenoma jih je malo:
     * prijava, prevzem zetona, vstopnica, seznam naprav in stanje. Potrjevanje in odvzem
     * dostopa se dogajata samo na televizorju, zato ju tu ni.
     */
    fun odgovori(zahteva: HubStreznik.Zahteva): HubStreznik.Odgovor? {
        val pot = zahteva.pot
        val krajevni = jeKrajevni(zahteva.odjemalec)

        if (pot == "/cast/pair/start" && zahteva.metoda == "POST") {
            if (!krajevni) return HubStreznik.Odgovor(403, napakaJson("Seznanjanje je mogoče samo v krajevnem omrežju."))
            val telo = JsonLahki.objekt(zahteva.telo)
            val deviceId = (telo?.niz("device_id") ?: "").trim().take(NAJVEC_IMENA)
            val ime = (telo?.niz("name") ?: "").trim().take(NAJVEC_IMENA)
            if (deviceId.isEmpty()) return HubStreznik.Odgovor(400, napakaJson("Manjka device_id."))
            val prijava = zacniSeznanitev(deviceId, ime, zahteva.odjemalec)
                ?: return HubStreznik.Odgovor(429, napakaJson("Preveč čakajočih prijav; poskusite čez nekaj minut."))
            return HubStreznik.Odgovor(
                200,
                JsonLahki.Zapis()
                    .niz("pair_id", prijava.first)
                    .niz("pin", prijava.second)
                    .stevilo("expires_in_seconds", (PIN_VELJA_MS / 1000).toDouble())
                    .toString()
            )
        }

        if (pot == "/cast/pair/claim" && zahteva.metoda == "POST") {
            if (!krajevni) return HubStreznik.Odgovor(403, napakaJson("Samo v krajevnem omrežju."))
            val telo = JsonLahki.objekt(zahteva.telo)
            val zeton = prevzemiZeton(telo?.niz("pair_id") ?: "")
                ?: return HubStreznik.Odgovor(200, JsonLahki.Zapis().logicno("approved", false).toString())
            return HubStreznik.Odgovor(
                200,
                JsonLahki.Zapis().logicno("approved", true).niz("token", zeton).toString()
            )
        }

        if (pot == "/cast/ticket" && zahteva.metoda == "POST") {
            if (!krajevni) return HubStreznik.Odgovor(403, napakaJson("Samo v krajevnem omrežju."))
            if (!jeVeljavenZeton(zahteva.glave["x-safeer-token"])) {
                return HubStreznik.Odgovor(401, napakaJson("Naprava ni seznanjena."))
            }
            return HubStreznik.Odgovor(
                200,
                JsonLahki.Zapis()
                    .niz("ticket", izdajVstopnico())
                    .stevilo("expires_in_seconds", (VSTOPNICA_VELJA_MS / 1000).toDouble())
                    .toString()
            )
        }

        if (pot == "/cast/devices" && zahteva.metoda == "GET") {
            if (!krajevni || !jeVeljavenZeton(zahteva.glave["x-safeer-token"])) {
                return HubStreznik.Odgovor(401, napakaJson("Naprava ni seznanjena."))
            }
            return HubStreznik.Odgovor(200, povezaniPrejemniki())
        }

        if (pot == "/cast/health" && zahteva.metoda == "GET") {
            if (!krajevni || !jeVeljavenZeton(zahteva.glave["x-safeer-token"])) {
                return HubStreznik.Odgovor(401, napakaJson("Naprava ni seznanjena."))
            }
            val stanje = synchronized(kljucnica) {
                JsonLahki.Zapis()
                    .niz("status", "ok")
                    .niz("protocol", RAZLICICA_PROTOKOLA)
                    .stevilo("receivers", naprave.count { it.value.vloga == "receiver" && it.value.povezava != null }.toDouble())
                    .stevilo("senders", posiljatelji.size.toDouble())
                    .stevilo("sync_peers", naprave.count {
                        it.value.povezava != null &&
                            (it.value.zmoznosti.contains(ZMOZNOST_SYNC) || it.value.vloga == "sync-client")
                    }.toDouble())
                    // Samo imena kategorij, nikoli vsebina.
                    .seznamNizov("sync_categories", sinhronizacija.keys.sorted())
                    .toString()
            }
            return HubStreznik.Odgovor(200, stanje)
        }

        return null
    }

    /**
     * Nadgradnja v WebSocket je dovoljena samo iz krajevnega omrezja in samo z veljavno
     * enokratno vstopnico. Vrne razlog zavrnitve ali null, ce je vse v redu.
     */
    fun preveriVstopnico(zahteva: HubStreznik.Zahteva): String? {
        if (zahteva.pot != "/cast/ws") return "neznana pot"
        if (!jeKrajevni(zahteva.odjemalec)) return "samo v krajevnem omrežju"
        if (!porabiVstopnico(zahteva.poizvedba["ticket"])) return "neveljavna ali potekla vstopnica"
        return null
    }

    private fun napakaJson(sporocilo: String): String =
        JsonLahki.Zapis().niz("detail", sporocilo).toString()

    companion object {
        const val RAZLICICA_PROTOKOLA = "0.2"
        const val VSEM = "all"
        const val ZMOZNOST_SYNC = "sync"

        private const val KLJUC_ZETONOV = "cast_naprave"

        private val CAST_POSREDOVANJE = setOf("cast.url", "cast.media", "cast.control")
        private val SYNC_POSREDOVANJE = setOf("sync.request", "sync.data", "sync.status")

        // Meje so del zasnove, ne naknadni popravek. Televizor ima malo pomnilnika in ga
        // sistem ob pomanjkanju ubije brez opozorila, zato ima vsak seznam svojo streho.
        const val NAJVEC_NAPRAV = 16
        const val NAJVEC_CAKAJOCIH = 8
        const val NAJVEC_SEZNANJENIH = 16
        const val NAJVEC_VSTOPNIC = 8
        const val NAJVEC_KATEGORIJ = 8
        const val NAJVECJA_KATEGORIJA = 192 * 1024
        const val NAJVEC_SKUPAJ_SYNC = 512 * 1024
        const val NAJVEC_IMENA = 64

        const val PIN_VELJA_MS = 300_000L
        const val PREVZEM_VELJA_MS = 600_000L
        const val VSTOPNICA_VELJA_MS = 60_000L

        private val nakljucniStevec = SecureRandom()

        private fun privzetoNakljucno(bajtov: Int): String {
            val podatki = ByteArray(bajtov)
            nakljucniStevec.nextBytes(podatki)
            val izpis = StringBuilder(bajtov * 2)
            for (b in podatki) izpis.append(String.format("%02x", b.toInt() and 0xFF))
            return izpis.toString()
        }

        /**
         * Seznanjanje in predvajanje sta krajevna stvar; z interneta se naprava ne sme prijaviti.
         * Enako kot pri Chromecastu: kdor je v hisi, sme vprasati, kdor ni, ne more niti vprasati.
         */
        fun jeKrajevni(naslov: String): Boolean {
            if (naslov.isBlank()) return false
            return try {
                val ip = java.net.InetAddress.getByName(naslov)
                ip.isSiteLocalAddress || ip.isLoopbackAddress || ip.isLinkLocalAddress ||
                    ip.isAnyLocalAddress
            } catch (e: Exception) {
                false
            }
        }
    }
}
