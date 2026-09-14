package si.safeer.tv

/**
 * Seznam domen za blokiranje, shranjen strnjeno.
 *
 * Prej je bila tu drevesna struktura (suffix trie): vsak del domene je imel svoje vozlisce
 * s svojim HashMapom. Meritev je pokazala 292 bajtov na domeno - pri vec sto tisoc domenah
 * je to stotine megabajtov na napravi, ki jih nima. Televizor je zaradi tega umiral.
 *
 * Zdaj so vse domene v enem samem nizu, poleg pa so tri majhna polja: kje se vsaka zacne,
 * kako je dolga in iz katerega seznama je. Iskanje gre z bisekcijo po urejenih zacetkih.
 * Poraba pade na priblizno trideset bajtov na domeno, ujemanje pa je natanko isto kot prej:
 * domena se ujame tudi za vse svoje poddomene, obvelja pa najkrajsa shranjena pripona.
 *
 * Nalasc ne hranimo zgoscenih vrednosti namesto domen: to bi bilo se manjse, a bi ob
 * (zelo redkem) trku zablokiralo nedolzno stran. Pri varnostni funkciji tega ne tvegamo.
 */
class DomainSuffixTrie {

    data class MatchResult(
        val isMatched: Boolean,
        val matchedDomain: String,
        val category: String?,
        val sourceFeed: String?
    )

    /** Bazen vseh domen, ena za drugo brez locil. */
    private var bazen: String = ""

    /** Med vstavljanjem pisemo sem; ob prvem iskanju to postane bazen. */
    private var gradilnik: StringBuilder? = null

    private var zacetki = IntArray(0)
    private var dolzine = ShortArray(0)
    private var kategorije = ShortArray(0)
    private var viri = ShortArray(0)
    private var stevilo = 0

    /** True, ko so polja urejena in brez dvojnikov (pripravljena za bisekcijo). */
    private var urejeno = true

    /** Kategorije in imena seznamov so le nekaj razlicnih nizov; hranimo jih enkrat. */
    private val besede = ArrayList<String?>()
    private val besedeIndeks = HashMap<String?, Int>()

    val size: Int
        @Synchronized get() {
            strni()
            return stevilo
        }

    // ------------------------------------------------------------------ vstavljanje

    @Synchronized
    fun insert(domain: String, category: String? = null, sourceFeed: String? = null) {
        val clean = normaliziraj(domain) ?: return
        val g = gradilnik ?: StringBuilder(bazen).also { gradilnik = it }
        if (stevilo == zacetki.size) povecajPolja()
        zacetki[stevilo] = g.length
        dolzine[stevilo] = clean.length.toShort()
        kategorije[stevilo] = indeksBesede(category)
        viri[stevilo] = indeksBesede(sourceFeed)
        g.append(clean)
        stevilo++
        urejeno = false
    }

    @Synchronized
    fun insertAll(domains: Collection<String>, category: String? = null, sourceFeed: String? = null) {
        for (d in domains) insert(d, category, sourceFeed)
    }

    @Synchronized
    fun clear() {
        bazen = ""
        gradilnik = null
        zacetki = IntArray(0)
        dolzine = ShortArray(0)
        kategorije = ShortArray(0)
        viri = ShortArray(0)
        stevilo = 0
        urejeno = true
        besede.clear()
        besedeIndeks.clear()
    }

    // ------------------------------------------------------------------ iskanje

    fun matches(host: String): Boolean = findMatch(host) != null

    /**
     * Poisce najkrajso shranjeno pripono gostitelja - enako kot prej drevo, ki se je
     * ustavilo pri prvem oznacenem vozliscu na poti od vrhnje domene navznoter.
     */
    @Synchronized
    fun findMatch(host: String): MatchResult? {
        val clean = normaliziraj(host) ?: return null
        strni()
        if (stevilo == 0) return null

        // Pripone beremo kar iz gostitelja, od najkrajse proti najdaljsi: "com", "b.com",
        // "a.b.com". Niza ne sestavljamo - primerjamo po odmiku, zato ob iskanju ne nastane
        // nic smeti. Pri blokiranju oglasov se to zgodi ob vsaki zahtevi strani.
        var odmik = clean.length
        while (true) {
            odmik = clean.lastIndexOf('.', odmik - 1)
            val zacetekPripone = if (odmik < 0) 0 else odmik + 1
            val najden = poisci(clean, zacetekPripone)
            if (najden >= 0) {
                return MatchResult(
                    isMatched = true,
                    matchedDomain = clean.substring(zacetekPripone),
                    category = besede.getOrNull(kategorije[najden].toInt()),
                    sourceFeed = besede.getOrNull(viri[najden].toInt())
                )
            }
            if (odmik <= 0) return null
        }
    }

    // ------------------------------------------------------------------ notranjost

    /** Normalizacija je ista kot prej: male crke, brez praznih delov, komentarji odpadejo. */
    private fun normaliziraj(vhod: String): String? {
        val clean = vhod.trim().lowercase()
        if (clean.isEmpty() || clean.startsWith("#")) return null
        val deli = clean.split('.').filter { it.isNotEmpty() }
        if (deli.isEmpty()) return null
        val zdruzeno = deli.joinToString(".")
        // Dolzino hranimo v dveh bajtih; daljse od tega ni prava domena.
        return if (zdruzeno.length > NAJDALJSA) null else zdruzeno
    }

    private fun indeksBesede(beseda: String?): Short {
        besedeIndeks[beseda]?.let { return it.toShort() }
        if (besede.size >= Short.MAX_VALUE) return 0   // toliko razlicnih virov ne pricakujemo
        besede.add(beseda)
        val indeks = besede.size - 1
        besedeIndeks[beseda] = indeks
        return indeks.toShort()
    }

    private fun povecajPolja() {
        val nova = if (zacetki.isEmpty()) 64 else zacetki.size * 2
        zacetki = zacetki.copyOf(nova)
        dolzine = dolzine.copyOf(nova)
        kategorije = kategorije.copyOf(nova)
        viri = viri.copyOf(nova)
    }

    /**
     * Uredi vnose po vsebini in odstrani dvojnike (obvelja prvi vstavljeni, tako kot prej,
     * ko drugi vpis v ze oznaceno vozlisce ni spremenil nicesar). Bazen ob tem prepisemo,
     * da za dvojniki ne ostaja prostor.
     */
    private fun strni() {
        if (urejeno) return
        val g = gradilnik
        if (g != null) {
            bazen = g.toString()
            gradilnik = null
        }
        if (stevilo == 0) {
            urejeno = true
            return
        }

        val vrstniRed = (0 until stevilo).sortedWith(Comparator { a, b ->
            val c = primerjajVnosa(a, b)
            if (c != 0) c else a - b          // ob enakem nizu naj bo prvi vstavljeni prvi
        })

        val novBazen = StringBuilder(bazen.length)
        val novZacetki = IntArray(stevilo)
        val novDolzine = ShortArray(stevilo)
        val novKategorije = ShortArray(stevilo)
        val novViri = ShortArray(stevilo)
        var novih = 0

        for (i in vrstniRed) {
            if (novih > 0 && primerjajZNizom(novZacetki, novDolzine, novBazen, novih - 1, i, bazen) == 0) {
                continue                      // dvojnik; prvi je ze notri
            }
            novZacetki[novih] = novBazen.length
            novDolzine[novih] = dolzine[i]
            novKategorije[novih] = kategorije[i]
            novViri[novih] = viri[i]
            novBazen.append(bazen, zacetki[i], zacetki[i] + dolzine[i].toInt())
            novih++
        }

        bazen = novBazen.toString()
        zacetki = novZacetki.copyOf(novih)
        dolzine = novDolzine.copyOf(novih)
        kategorije = novKategorije.copyOf(novih)
        viri = novViri.copyOf(novih)
        stevilo = novih
        urejeno = true
    }

    private fun primerjajVnosa(a: Int, b: Int): Int {
        val za = zacetki[a]
        val zb = zacetki[b]
        val da = dolzine[a].toInt()
        val db = dolzine[b].toInt()
        val n = if (da < db) da else db
        for (k in 0 until n) {
            val x = bazen[za + k]
            val y = bazen[zb + k]
            if (x != y) return if (x < y) -1 else 1
        }
        return da - db
    }

    /** Primerja ze prepisan vnos (v novem bazenu) s se neprepisanim (v starem). */
    private fun primerjajZNizom(
        novZacetki: IntArray, novDolzine: ShortArray, novBazen: StringBuilder,
        novIndeks: Int, starIndeks: Int, starBazen: String
    ): Int {
        val zn = novZacetki[novIndeks]
        val dn = novDolzine[novIndeks].toInt()
        val zs = zacetki[starIndeks]
        val ds = dolzine[starIndeks].toInt()
        val n = if (dn < ds) dn else ds
        for (k in 0 until n) {
            val x = novBazen[zn + k]
            val y = starBazen[zs + k]
            if (x != y) return if (x < y) -1 else 1
        }
        return dn - ds
    }

    private fun poisci(kljuc: String, odKje: Int): Int {
        var spodaj = 0
        var zgoraj = stevilo - 1
        while (spodaj <= zgoraj) {
            val sredina = (spodaj + zgoraj) ushr 1
            val c = primerjajSKljucem(sredina, kljuc, odKje)
            when {
                c == 0 -> return sredina
                c < 0 -> spodaj = sredina + 1
                else -> zgoraj = sredina - 1
            }
        }
        return -1
    }

    private fun primerjajSKljucem(i: Int, kljuc: String, odKje: Int): Int {
        val z = zacetki[i]
        val d = dolzine[i].toInt()
        val dolzinaKljuca = kljuc.length - odKje
        val n = if (d < dolzinaKljuca) d else dolzinaKljuca
        for (k in 0 until n) {
            val x = bazen[z + k]
            val y = kljuc[odKje + k]
            if (x != y) return if (x < y) -1 else 1
        }
        return d - dolzinaKljuca
    }

    companion object {
        /** Domena, daljsa od tega, ni prava domena; dolzino hranimo v dveh bajtih. */
        private const val NAJDALJSA = 255
    }
}
