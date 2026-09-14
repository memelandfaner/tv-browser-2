package si.safeer.tv

/**
 * Koliko pomnilnika zares poje seznam blokiranih domen?
 *
 * Na televizorju je Java kopica okoli 95 MB, seznami pa nosijo priblizno pol milijona
 * domen. DomainSuffixTrie ima na vsakem vozliscu svoj HashMap - tudi listi, ki otrok
 * nimajo - zato je vprasanje, koliko od tega gre za koristne podatke in koliko za
 * ovojnico. Tu to izmerimo in primerjamo s stisnjeno obliko, ki hrani le 64-bitne
 * odtise pripon in vir zadetka.
 *
 * Meritev ni ocena: strukturo zgradimo, pospravimo smeti in odcitamo zasedenost kopice.
 */

private const val DOMEN = 477_000

// --------------------------------------------------------------- stisnjena oblika

/**
 * Stisnjen seznam domen: urejeno polje 64-bitnih odtisov in bajt z virom.
 *
 * Ujemanje poddomen ostane enako kot pri drevesu: za gostitelja preverimo odtis celotnega
 * imena in vsake njegove nad-domene. Namesto vozlisc z ovojnico imamo dve zvezni polji.
 */
class StisnjeneDomene private constructor(
    private val odtisi: LongArray,
    private val viri: ByteArray
) {
    fun velikost(): Int = odtisi.size

    fun najdi(host: String): Int {
        val ime = host.trim().lowercase()
        if (ime.isEmpty()) return -1
        var zacetek = 0
        while (true) {
            val odtis = odtisOd(ime, zacetek)
            val i = poisci(odtis)
            if (i >= 0) return viri[i].toInt()
            val pika = ime.indexOf('.', zacetek)
            if (pika < 0) return -1
            zacetek = pika + 1
        }
    }

    private fun poisci(odtis: Long): Int {
        var lo = 0
        var hi = odtisi.size - 1
        while (lo <= hi) {
            val sredina = (lo + hi) ushr 1
            val v = odtisi[sredina]
            when {
                v < odtis -> lo = sredina + 1
                v > odtis -> hi = sredina - 1
                else -> return sredina
            }
        }
        return -1
    }

    companion object {
        /** FNV-1a, 64-bitni. Stabilen in brez odvisnosti. */
        fun odtisOd(niz: String, od: Int = 0): Long {
            var h = -3750763034362895579L
            for (i in od until niz.length) {
                h = h xor (niz[i].code.toLong() and 0xFF)
                h *= 1099511628211L
            }
            return h
        }

        fun zgradi(domene: List<String>, viriDomen: ByteArray): StisnjeneDomene {
            val pari = LongArray(domene.size)
            for (i in domene.indices) pari[i] = odtisOd(domene[i].lowercase())
            val vrstni = (0 until domene.size).sortedBy { pari[it] }
            val odtisi = LongArray(domene.size)
            val viri = ByteArray(domene.size)
            for ((novi, stari) in vrstni.withIndex()) {
                odtisi[novi] = pari[stari]
                viri[novi] = viriDomen[stari]
            }
            return StisnjeneDomene(odtisi, viri)
        }
    }
}

// ------------------------------------------------------------------- meritev

private fun pospravi() {
    for (i in 0 until 4) {
        System.gc()
        Thread.sleep(120)
    }
}

private fun zasedeno(): Long {
    val r = Runtime.getRuntime()
    return r.totalMemory() - r.freeMemory()
}

private fun mb(bajtov: Long): String = String.format("%.1f MB", bajtov / 1024.0 / 1024.0)

private fun ustvariDomene(koliko: Int): List<String> {
    // Razmerje oznak posnema prave sezname: vecina je ime.tld, del je poddomena.ime.tld.
    val tld = listOf("com", "net", "org", "info", "xyz", "top", "ru", "cn", "si", "de")
    val zlogi = listOf("ad", "track", "click", "pay", "log", "stat", "cdn", "api", "shop",
        "bank", "mail", "secure", "login", "app", "data", "media", "cloud", "host")
    val izid = ArrayList<String>(koliko)
    var seme = 12345L
    fun nakljucno(n: Int): Int {
        seme = seme * 6364136223846793005L + 1442695040888963407L
        return (((seme ushr 33).toInt()) and 0x7FFFFFFF) % n
    }
    for (i in 0 until koliko) {
        val osnova = zlogi[nakljucno(zlogi.size)] + zlogi[nakljucno(zlogi.size)] + i
        val koncnica = tld[nakljucno(tld.size)]
        izid.add(
            if (nakljucno(10) < 3) "${zlogi[nakljucno(zlogi.size)]}.$osnova.$koncnica"
            else "$osnova.$koncnica"
        )
    }
    return izid
}

fun main() {
    println("== poraba pomnilnika za $DOMEN domen ==")
    val domene = ustvariDomene(DOMEN)
    val viri = ByteArray(DOMEN) { (it % 7).toByte() }

    // --- sedanja resitev ---
    pospravi()
    val predDrevesom = zasedeno()
    var drevo: DomainSuffixTrie? = DomainSuffixTrie()
    for ((i, d) in domene.withIndex()) drevo!!.insert(d, "kategorija", "vir${i % 7}")
    pospravi()
    val poDrevesu = zasedeno()
    val stroskDrevesa = poDrevesu - predDrevesom
    println("drevo (DomainSuffixTrie): ${mb(stroskDrevesa)}  (vnosov ${drevo!!.size})")

    // preverimo se pravilnost, preden ga spustimo
    val vzorec = listOf(domene[0], domene[DOMEN / 2], domene[DOMEN - 1])
    for (v in vzorec) {
        if (!drevo!!.matches(v)) println("  NAPAKA: drevo ne najde $v")
        if (!drevo!!.matches("a.b.$v")) println("  NAPAKA: drevo ne ujame poddomene a.b.$v")
    }
    if (drevo!!.matches("tega.zagotovo.ni.example")) println("  NAPAKA: drevo ujame nekaj, cesar ni")

    drevo = null
    pospravi()

    // --- stisnjena oblika ---
    val predStisnjeno = zasedeno()
    var stisnjeno: StisnjeneDomene? = StisnjeneDomene.zgradi(domene, viri)
    pospravi()
    val poStisnjenem = zasedeno()
    val stroskStisnjenega = poStisnjenem - predStisnjeno
    println("stisnjeno (odtisi + vir):  ${mb(stroskStisnjenega)}  (vnosov ${stisnjeno!!.velikost()})")

    for (v in vzorec) {
        if (stisnjeno!!.najdi(v) < 0) println("  NAPAKA: stisnjeno ne najde $v")
        if (stisnjeno!!.najdi("a.b.$v") < 0) println("  NAPAKA: stisnjeno ne ujame poddomene a.b.$v")
    }
    if (stisnjeno!!.najdi("tega.zagotovo.ni.example") >= 0) {
        println("  NAPAKA: stisnjeno ujame nekaj, cesar ni")
    }

    // --- hitrost iskanja ---
    val poizvedbe = ArrayList<String>(20000)
    for (i in 0 until 20000) poizvedbe.add("neki.$i.example.com")
    var t0 = System.nanoTime()
    var zadetkov = 0
    for (q in poizvedbe) if (stisnjeno!!.najdi(q) >= 0) zadetkov++
    val casStisnjeno = (System.nanoTime() - t0) / 1_000_000.0

    stisnjeno = null
    pospravi()
    val drevo2 = DomainSuffixTrie()
    for (d in domene) drevo2.insert(d)
    t0 = System.nanoTime()
    var zadetkov2 = 0
    for (q in poizvedbe) if (drevo2.matches(q)) zadetkov2++
    val casDrevo = (System.nanoTime() - t0) / 1_000_000.0

    println()
    println("iskanje 20000 gostiteljev:")
    println("  drevo:     %.1f ms (zadetkov %d)".format(casDrevo, zadetkov2))
    println("  stisnjeno: %.1f ms (zadetkov %d)".format(casStisnjeno, zadetkov))

    println()
    val prihranek = stroskDrevesa - stroskStisnjenega
    println("PRIHRANEK: ${mb(prihranek)} (%.1f-krat manj)".format(
        if (stroskStisnjenega > 0) stroskDrevesa.toDouble() / stroskStisnjenega else 0.0))
    println("na vnos: drevo %d B, stisnjeno %d B".format(
        stroskDrevesa / DOMEN, stroskStisnjenega / DOMEN))
}
