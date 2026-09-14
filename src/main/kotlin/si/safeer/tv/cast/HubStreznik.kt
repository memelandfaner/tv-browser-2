package si.safeer.tv.cast

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Streznik Safeer Huba v brskalniku: majhen HTTP streznik z nadgradnjo na WebSocket.
 *
 * Zakaj lastna izvedba in ne knjiznica: v APK-ju ne zelimo nove odvisnosti samo zaradi
 * tega, okhttp pa zna biti odjemalec in ne streznik. Protokol je RFC 6455, isti, ki ga
 * govori ze Hub na racunalniku, zato televizor in telefon ostaneta nespremenjena -
 * govorimo isti jezik, le streznik stoji drugje.
 *
 * Razred namenoma ne pozna Androidovih storitev in ne uporabniskega vmesnika, da ga je
 * mogoce preizkusiti sam zase.
 */
class HubStreznik(
    private val zeljenaVrata: Int = PRIVZETA_VRATA,
    /** Odgovori na navadne zahteve HTTP. Vrne null, ce poti ne pozna (404). */
    private val naZahtevo: (Zahteva) -> Odgovor?,
    /** Ali je ta zahteva za nadgradnjo v WebSocket dovoljena; vrne razlog zavrnitve ali null. */
    private val preveriVstopnico: (Zahteva) -> String?,
    /** Nova odprta povezava. */
    private val naPovezavo: (Povezava) -> Unit
) {

    data class Zahteva(
        val metoda: String,
        val pot: String,
        val poizvedba: Map<String, String>,
        val glave: Map<String, String>,
        val telo: String,
        val odjemalec: String
    )

    data class Odgovor(
        val koda: Int,
        val telo: String,
        val vrsta: String = "application/json; charset=utf-8"
    )

    private val tece = AtomicBoolean(false)
    private var vticnica: ServerSocket? = null
    private val povezave = CopyOnWriteArrayList<Povezava>()

    /** Koliko bajtov trenutno zadrzujejo vse povezave skupaj (sestavljanje sporocil). */
    private val skupajZadrzano = java.util.concurrent.atomic.AtomicLong(0)

    /** Vrata, na katerih streznik dejansko poslusa (lahko se razlikujejo od zeljenih). */
    @Volatile
    var vrata: Int = 0
        private set

    fun zazeni(): Boolean {
        if (tece.get()) return true
        val vt = odpriVticnico() ?: return false
        vticnica = vt
        vrata = vt.localPort
        tece.set(true)
        zazeniNit("safeer-hub-accept") { zankaSprejemanja(vt) }
        Log.i(OZNAKA, "Hub posluša na vratih $vrata")
        return true
    }

    fun ustavi() {
        if (!tece.getAndSet(false)) return
        for (p in povezave) {
            try {
                p.zapri(1001, "hub se ustavlja")
            } catch (_: Exception) {
            }
        }
        povezave.clear()
        try {
            vticnica?.close()
        } catch (_: Exception) {
        }
        vticnica = null
        vrata = 0
        Log.i(OZNAKA, "Hub ustavljen")
    }

    fun teceZdaj(): Boolean = tece.get()

    fun steviloPovezav(): Int = povezave.size

    /**
     * Najprej poskusimo obicajna vrata, da je naslov predvidljiv. Ce so zasedena (npr. na
     * napravi ze tece kaj drugega), vzamemo prosta - naslov ionako objavimo prek mDNS, zato
     * uporabniku ni treba vedeti niti imena niti stevilke.
     */
    private fun odpriVticnico(): ServerSocket? {
        for (kandidat in listOf(zeljenaVrata, 0)) {
            try {
                return ServerSocket(kandidat, CAKALNA_VRSTA, InetAddress.getByName("0.0.0.0"))
            } catch (e: Exception) {
                Log.w(OZNAKA, "Vrat $kandidat ni bilo mogoče odpreti: ${e.message}")
            }
        }
        return null
    }

    /**
     * Niti zaganjamo s privzetim skladom. Poskus, da bi ga zmanjsali, se je v preizkusu
     * koncal s StackOverflowError: sklad je navidezni pomnilnik, ki se dodeljuje sproti,
     * zato neaktivna nit v resnici porabi le nekaj kilobajtov - prihranek bi bil navidezen,
     * tveganje sesutja pa pravo. Pomnilnik zares omejujeta stevilo povezav in velikost
     * sporocil, ne velikost sklada.
     */
    private fun zazeniNit(ime: String, blok: () -> Unit) {
        val nit = Thread(null, { blok() }, ime)
        nit.isDaemon = true
        nit.start()
    }

    private fun zankaSprejemanja(vt: ServerSocket) {
        while (tece.get()) {
            val odjemalec = try {
                vt.accept()
            } catch (e: Exception) {
                if (tece.get()) Log.w(OZNAKA, "Napaka pri sprejemanju: ${e.message}")
                break
            }
            zazeniNit("safeer-hub-odjemalec") {
                try {
                    postrezi(odjemalec)
                } catch (e: Exception) {
                    Log.w(OZNAKA, "Povezava končana z napako: ${e.message}")
                } finally {
                    try {
                        if (!odjemalec.isClosed) odjemalec.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun postrezi(vticnica: Socket) {
        vticnica.soTimeout = BRALNI_TIMEOUT_MS
        vticnica.tcpNoDelay = true
        val vhod = vticnica.getInputStream().buffered()
        val izhod = vticnica.getOutputStream()

        val zahteva = preberiZahtevo(vhod, vticnica) ?: return

        val nadgradnja = zahteva.glave["upgrade"]?.lowercase() == "websocket"
        if (nadgradnja) {
            val razlog = preveriVstopnico(zahteva)
            if (razlog != null) {
                // Povezavo zavrnemo se pred rokovanjem: naprava brez veljavne vstopnice
                // ne sme nikoli priti do sporocil.
                posljiOdgovor(izhod, Odgovor(403, "{\"napaka\":\"$razlog\"}"))
                return
            }
            val kljuc = zahteva.glave["sec-websocket-key"]
            if (kljuc.isNullOrBlank()) {
                posljiOdgovor(izhod, Odgovor(400, "{\"napaka\":\"manjka kljuc\"}"))
                return
            }
            // Hisa ima nekaj naprav, ne nekaj sto. Odvecno povezavo raje zavrnemo, kot da
            // bi z niti in medpomnilniki po nepotrebnem jedli pomnilnik naprave.
            if (povezave.size >= NAJVEC_POVEZAV) {
                posljiOdgovor(izhod, Odgovor(503, "{\"napaka\":\"preveč povezanih naprav\"}"))
                return
            }
            rokovanje(izhod, kljuc)
            val povezava = Povezava(vticnica, vhod, izhod, zahteva)
            povezave.add(povezava)
            try {
                naPovezavo(povezava)
                povezava.zankaBranja()
            } finally {
                povezave.remove(povezava)
            }
            return
        }

        val odgovor = try {
            naZahtevo(zahteva) ?: Odgovor(404, "{\"napaka\":\"ni te poti\"}")
        } catch (e: Exception) {
            Log.w(OZNAKA, "Napaka pri obdelavi ${zahteva.pot}: ${e.message}")
            Odgovor(500, "{\"napaka\":\"notranja napaka\"}")
        }
        posljiOdgovor(izhod, odgovor)
    }

    // ---------------------------------------------------------------- HTTP

    private fun preberiZahtevo(vhod: InputStream, vticnica: Socket): Zahteva? {
        val prva = preberiVrstico(vhod) ?: return null
        val deli = prva.split(" ")
        if (deli.size < 2) return null
        val metoda = deli[0].uppercase()
        val celotnaPot = deli[1]

        val glave = HashMap<String, String>()
        var stevec = 0
        while (true) {
            val vrstica = preberiVrstico(vhod) ?: break
            if (vrstica.isEmpty()) break
            if (++stevec > NAJVEC_GLAV) return null
            val dvopicje = vrstica.indexOf(':')
            if (dvopicje <= 0) continue
            glave[vrstica.substring(0, dvopicje).trim().lowercase()] =
                vrstica.substring(dvopicje + 1).trim()
        }

        val dolzina = glave["content-length"]?.toIntOrNull() ?: 0
        if (dolzina > NAJVECJE_TELO) return null
        val telo = if (dolzina > 0) {
            val medpomnilnik = ByteArray(dolzina)
            var prebrano = 0
            while (prebrano < dolzina) {
                val n = vhod.read(medpomnilnik, prebrano, dolzina - prebrano)
                if (n < 0) break
                prebrano += n
            }
            String(medpomnilnik, 0, prebrano, Charsets.UTF_8)
        } else ""

        val vprasaj = celotnaPot.indexOf('?')
        val pot = if (vprasaj >= 0) celotnaPot.substring(0, vprasaj) else celotnaPot
        val poizvedba = if (vprasaj >= 0) razcleniPoizvedbo(celotnaPot.substring(vprasaj + 1)) else emptyMap()

        return Zahteva(
            metoda = metoda,
            pot = pot,
            poizvedba = poizvedba,
            glave = glave,
            telo = telo,
            // Naslov jemljemo iz povezave, nikoli iz tega, kar naprava pove o sebi.
            odjemalec = vticnica.inetAddress?.hostAddress ?: ""
        )
    }

    private fun preberiVrstico(vhod: InputStream): String? {
        val izpis = ByteArrayOutputStream()
        while (true) {
            val b = try {
                vhod.read()
            } catch (e: SocketTimeoutException) {
                return null
            }
            if (b < 0) return if (izpis.size() == 0) null else izpis.toString("UTF-8")
            if (b == '\n'.code) {
                var niz = izpis.toString("UTF-8")
                if (niz.endsWith("\r")) niz = niz.dropLast(1)
                return niz
            }
            if (izpis.size() > NAJVECJA_VRSTICA) return null
            izpis.write(b)
        }
    }

    private fun razcleniPoizvedbo(niz: String): Map<String, String> {
        val izid = HashMap<String, String>()
        for (par in niz.split("&")) {
            if (par.isEmpty()) continue
            val i = par.indexOf('=')
            if (i < 0) izid[odkodiraj(par)] = "" else izid[odkodiraj(par.substring(0, i))] =
                odkodiraj(par.substring(i + 1))
        }
        return izid
    }

    private fun odkodiraj(niz: String): String = try {
        java.net.URLDecoder.decode(niz, "UTF-8")
    } catch (_: Exception) {
        niz
    }

    private fun posljiOdgovor(izhod: OutputStream, odgovor: Odgovor) {
        val telo = odgovor.telo.toByteArray(Charsets.UTF_8)
        val glava = StringBuilder()
        glava.append("HTTP/1.1 ").append(odgovor.koda).append(" ").append(besedaKode(odgovor.koda)).append("\r\n")
        glava.append("Content-Type: ").append(odgovor.vrsta).append("\r\n")
        glava.append("Content-Length: ").append(telo.size).append("\r\n")
        // Hub je krajevna naprava; odgovori naj se nikjer ne shranjujejo.
        glava.append("Cache-Control: no-store\r\n")
        glava.append("Connection: close\r\n\r\n")
        izhod.write(glava.toString().toByteArray(Charsets.UTF_8))
        izhod.write(telo)
        izhod.flush()
    }

    private fun besedaKode(koda: Int): String = when (koda) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        429 -> "Too Many Requests"
        503 -> "Service Unavailable"
        else -> "Internal Server Error"
    }

    // ----------------------------------------------------------- WebSocket

    private fun rokovanje(izhod: OutputStream, kljuc: String) {
        val sprejem = MessageDigest.getInstance("SHA-1")
            .digest((kljuc.trim() + CAROBNI_NIZ).toByteArray(Charsets.US_ASCII))
        val zakodiran = vBase64(sprejem)
        val odgovor = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $zakodiran\r\n\r\n"
        izhod.write(odgovor.toByteArray(Charsets.US_ASCII))
        izhod.flush()
    }

    /**
     * Base64 po RFC 4648. Namenoma ne uporabljamo android.util.Base64: brez te vezi je
     * streznik mogoce pognati in preizkusiti v navadnem JVM, brez naprave.
     */
    private fun vBase64(podatki: ByteArray): String {
        val abeceda = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val izpis = StringBuilder()
        var i = 0
        while (i < podatki.size) {
            val b0 = podatki[i].toInt() and 0xFF
            val b1 = if (i + 1 < podatki.size) podatki[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < podatki.size) podatki[i + 2].toInt() and 0xFF else 0
            izpis.append(abeceda[b0 shr 2])
            izpis.append(abeceda[((b0 and 0x03) shl 4) or (b1 shr 4)])
            izpis.append(if (i + 1 < podatki.size) abeceda[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            izpis.append(if (i + 2 < podatki.size) abeceda[b2 and 0x3F] else '=')
            i += 3
        }
        return izpis.toString()
    }

    /** Ena odprta povezava z napravo. */
    inner class Povezava(
        private val vticnica: Socket,
        private val vhod: InputStream,
        private val izhod: OutputStream,
        val zahteva: Zahteva
    ) {
        /** Ime naprave, ki ga vpise usmerjevalnik, ko se naprava predstavi. */
        @Volatile
        var imeNaprave: String = ""

        @Volatile
        var idNaprave: String = ""

        /** Zmoznosti, ki jih je naprava prijavila (npr. "cast", "sync"). */
        val zmoznosti = HashSet<String>()

        private val odprta = AtomicBoolean(true)
        private val kljucnicaPisanja = Any()

        /**
         * Koliko bajtov ta povezava trenutno zadrzuje pri sestavljanju sporocila. Racun je
         * tudi skupen: vse povezave skupaj ne smejo zadrzati vec, kot dovoli meja, sicer bi
         * lahko nekaj naprav hkrati napolnilo pomnilnik naprave.
         */
        private var zadrzano = 0L

        private fun zadrziBajte(n: Int): Boolean {
            val skupaj = skupajZadrzano.addAndGet(n.toLong())
            if (skupaj > NAJVECJI_SKUPNI_ZADRZEK) {
                skupajZadrzano.addAndGet(-n.toLong())
                return false
            }
            zadrzano += n
            return true
        }

        private fun sprostiZadrzek() {
            if (zadrzano > 0) {
                skupajZadrzano.addAndGet(-zadrzano)
                zadrzano = 0
            }
        }

        /** Kaj narediti s prejetim sporocilom; nastavi usmerjevalnik. */
        @Volatile
        var naSporocilo: ((String) -> Unit)? = null

        @Volatile
        var naZaprtje: (() -> Unit)? = null

        val naslov: String get() = zahteva.odjemalec

        fun jeOdprta(): Boolean = odprta.get() && !vticnica.isClosed

        fun poslji(besedilo: String) {
            if (!jeOdprta()) return
            val podatki = besedilo.toByteArray(Charsets.UTF_8)
            synchronized(kljucnicaPisanja) {
                try {
                    zapisiOkvir(OPKODA_BESEDILO, podatki)
                } catch (e: Exception) {
                    Log.w(OZNAKA, "Pisanje ni uspelo: ${e.message}")
                    zapri(1011, "napaka pri pisanju")
                }
            }
        }

        fun zapri(koda: Int = 1000, razlog: String = "") {
            if (!odprta.getAndSet(false)) return
            sprostiZadrzek()
            try {
                synchronized(kljucnicaPisanja) {
                    val telo = ByteArrayOutputStream()
                    telo.write((koda shr 8) and 0xFF)
                    telo.write(koda and 0xFF)
                    telo.write(razlog.toByteArray(Charsets.UTF_8))
                    zapisiOkvir(OPKODA_ZAPRI, telo.toByteArray())
                }
            } catch (_: Exception) {
            }
            try {
                vticnica.close()
            } catch (_: Exception) {
            }
            try {
                naZaprtje?.invoke()
            } catch (_: Exception) {
            }
        }

        /**
         * Streznik svojih okvirjev ne masklira (RFC 6455), odjemalcevi pa morajo biti
         * maskirani - neustrezen okvir zavrnemo, namesto da bi ga poskusali razumeti.
         */
        private fun zapisiOkvir(opkoda: Int, podatki: ByteArray) {
            val glava = ByteArrayOutputStream()
            glava.write(0x80 or opkoda)
            when {
                podatki.size < 126 -> glava.write(podatki.size)
                podatki.size <= 0xFFFF -> {
                    glava.write(126)
                    glava.write((podatki.size shr 8) and 0xFF)
                    glava.write(podatki.size and 0xFF)
                }
                else -> {
                    glava.write(127)
                    for (i in 7 downTo 0) glava.write(((podatki.size.toLong() shr (8 * i)) and 0xFF).toInt())
                }
            }
            izhod.write(glava.toByteArray())
            izhod.write(podatki)
            izhod.flush()
        }

        internal fun zankaBranja() {
            var zbrano = ByteArrayOutputStream()
            var zbranaOpkoda = -1
            var zadnjiPing = System.currentTimeMillis()
            var tihihKrogov = 0

            while (jeOdprta()) {
                val prvi = try {
                    vhod.read()
                } catch (e: SocketTimeoutException) {
                    // Tisina ni nujno napaka: posljemo ping in pocakamo se en krog.
                    val zdaj = System.currentTimeMillis()
                    if (zdaj - zadnjiPing > PING_VSAKIH_MS) {
                        zadnjiPing = zdaj
                        // Naprava, ki se po vec pingih ne oglasi, je najbrz izginila
                        // (izklopljen wifi, ugasnjen telefon). Taksne povezave ne drzimo
                        // odprte, sicer se nit in medpomnilnik kopicita.
                        if (++tihihKrogov > NAJVEC_TIHIH_KROGOV) {
                            zapri(1001, "naprava se ne oglaša")
                            break
                        }
                        synchronized(kljucnicaPisanja) {
                            try {
                                zapisiOkvir(OPKODA_PING, ByteArray(0))
                            } catch (_: Exception) {
                                zapri(1011, "ping ni uspel")
                            }
                        }
                        continue
                    }
                    continue
                } catch (e: Exception) {
                    break
                }
                if (prvi < 0) break
                tihihKrogov = 0

                val zakljucen = (prvi and 0x80) != 0
                val opkoda = prvi and 0x0F

                val drugi = vhod.read()
                if (drugi < 0) break
                val maskiran = (drugi and 0x80) != 0
                if (!maskiran) {
                    // Odjemalec, ki ne maskira, ni skladen; taksne povezave ne beremo naprej.
                    zapri(1002, "okvir ni maskiran")
                    break
                }

                var dolzina = (drugi and 0x7F).toLong()
                if (dolzina == 126L) {
                    dolzina = ((vhod.read() shl 8) or vhod.read()).toLong()
                } else if (dolzina == 127L) {
                    dolzina = 0
                    for (i in 0 until 8) dolzina = (dolzina shl 8) or vhod.read().toLong()
                }
                if (dolzina < 0 || dolzina > NAJVECJE_SPOROCILO) {
                    zapri(1009, "sporočilo je preveliko")
                    break
                }

                val maska = ByteArray(4)
                if (!preberiTocno(maska)) break
                val podatki = ByteArray(dolzina.toInt())
                if (!preberiTocno(podatki)) break
                for (i in podatki.indices) podatki[i] = (podatki[i].toInt() xor maska[i % 4].toInt()).toByte()

                when (opkoda) {
                    OPKODA_ZAPRI -> {
                        zapri(1000, "")
                        return
                    }
                    OPKODA_PING -> synchronized(kljucnicaPisanja) {
                        try {
                            zapisiOkvir(OPKODA_PONG, podatki)
                        } catch (_: Exception) {
                        }
                    }
                    OPKODA_PONG -> { /* ziv je, nic drugega ni treba */ }
                    OPKODA_BESEDILO, OPKODA_DVOJISKO, OPKODA_NADALJEVANJE -> {
                        if (opkoda != OPKODA_NADALJEVANJE) zbranaOpkoda = opkoda
                        if (zbrano.size() + podatki.size > NAJVECJE_SPOROCILO) {
                            zapri(1009, "sporočilo je preveliko")
                            return
                        }
                        if (!zadrziBajte(podatki.size)) {
                            zapri(1013, "naprava naj poskusi znova")
                            return
                        }
                        zbrano.write(podatki)
                        if (zakljucen) {
                            if (zbranaOpkoda == OPKODA_BESEDILO) {
                                val besedilo = String(zbrano.toByteArray(), Charsets.UTF_8)
                                try {
                                    naSporocilo?.invoke(besedilo)
                                } catch (e: Exception) {
                                    Log.w(OZNAKA, "Obdelava sporočila ni uspela: ${e.message}")
                                }
                            }
                            zbrano = ByteArrayOutputStream()
                            zbranaOpkoda = -1
                            sprostiZadrzek()
                        }
                    }
                }
            }
            zapri(1000, "")
        }

        private fun preberiTocno(cilj: ByteArray): Boolean {
            var prebrano = 0
            while (prebrano < cilj.size) {
                val n = try {
                    vhod.read(cilj, prebrano, cilj.size - prebrano)
                } catch (e: Exception) {
                    return false
                }
                if (n < 0) return false
                prebrano += n
            }
            return true
        }
    }

    companion object {
        private const val OZNAKA = "SafeerHubStreznik"
        const val PRIVZETA_VRATA = 8990

        private const val CAROBNI_NIZ = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val OPKODA_NADALJEVANJE = 0x0
        private const val OPKODA_BESEDILO = 0x1
        private const val OPKODA_DVOJISKO = 0x2
        private const val OPKODA_ZAPRI = 0x8
        private const val OPKODA_PING = 0x9
        private const val OPKODA_PONG = 0xA

        // Meje so postavljene po meritvi na televizorju: naprava ima okoli 2,7 GB, prostega
        // pa je le nekaj sto MB, brskalnik sam pa je ze velik. Java kopica ni ozko grlo -
        // nevarno je, da sistem zaradi pomanjkanja pomnilnika ubije cel brskalnik. Zato
        // raje zavrnemo odvecno povezavo ali preveliko sporocilo, kot da tvegamo to.
        const val NAJVEC_POVEZAV = 6
        private const val NAJVECJE_SPOROCILO = 256L * 1024
        private const val NAJVECJI_SKUPNI_ZADRZEK = 1L * 1024 * 1024
        private const val NAJVECJE_TELO = 64 * 1024
        private const val NAJVECJA_VRSTICA = 8 * 1024
        private const val NAJVEC_GLAV = 64
        private const val BRALNI_TIMEOUT_MS = 30_000
        private const val PING_VSAKIH_MS = 25_000L
        /** Po toliko zaporednih pingih brez odziva povezavo zapremo, da se ne kopicijo. */
        private const val NAJVEC_TIHIH_KROGOV = 3
        private const val CAKALNA_VRSTA = 16
    }
}
