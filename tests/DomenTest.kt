package si.safeer.tv

/**
 * Preizkus strnjenega seznama domen.
 *
 * Glavno vprasanje ni hitrost in ne poraba, ampak: ali blokira natanko iste stvari kot prej?
 * Zato poleg robnih primerov naredimo se primerjavo s preprosto referencno izvedbo, ki dela
 * po starih pravilih (drevo od vrhnje domene navznoter, obvelja prva shranjena pripona), in
 * ju spustimo cez deset tisoc domen in nekaj tisoc gostiteljev.
 */

private var napak = 0

private fun preveri(opis: String, pogoj: Boolean) {
    if (pogoj) {
        println("  OK   $opis")
    } else {
        println("  NAPAKA $opis")
        napak++
    }
}

private fun preveriEnako(opis: String, pricakovano: Any?, dobljeno: Any?) {
    preveri("$opis (pričakovano=$pricakovano, dobljeno=$dobljeno)", pricakovano == dobljeno)
}

// ---------------------------------------------------------------- referenca

/** Preprosta, ocitno pravilna izvedba istih pravil; sluzi samo za primerjavo. */
private class Referenca {
    private val vpisi = LinkedHashMap<String, Pair<String?, String?>>()

    fun insert(domena: String, kategorija: String?, vir: String?) {
        val clean = domena.trim().lowercase()
        if (clean.isEmpty() || clean.startsWith("#")) return
        val deli = clean.split('.').filter { it.isNotEmpty() }
        if (deli.isEmpty()) return
        val kljuc = deli.joinToString(".")
        if (kljuc.length > 255) return
        if (!vpisi.containsKey(kljuc)) vpisi[kljuc] = kategorija to vir
    }

    fun najdi(gostitelj: String): Triple<String, String?, String?>? {
        val clean = gostitelj.trim().lowercase()
        if (clean.isEmpty() || clean.startsWith("#")) return null
        val deli = clean.split('.').filter { it.isNotEmpty() }
        if (deli.isEmpty()) return null
        var pripona = ""
        for (i in deli.size - 1 downTo 0) {
            pripona = if (pripona.isEmpty()) deli[i] else deli[i] + "." + pripona
            val zadetek = vpisi[pripona]
            if (zadetek != null) return Triple(pripona, zadetek.first, zadetek.second)
        }
        return null
    }

    val velikost: Int get() = vpisi.size
}

// ---------------------------------------------------------------- primeri

private fun preizkusOsnovnih() {
    println("\n== osnovno ujemanje ==")
    val seznam = DomainSuffixTrie()
    seznam.insert("doubleclick.net", "Oglasi", "Vgrajeni seznam")
    seznam.insert("Bad-Site.COM", "Zlonamerna koda", "URLhaus")
    seznam.insert("  tracker.example.org  ", null, null)

    preveri("domena se ujame", seznam.matches("doubleclick.net"))
    preveri("poddomena se ujame", seznam.matches("ads.g.doubleclick.net"))
    preveri("velike crke ne motijo", seznam.matches("BAD-SITE.com"))
    preveri("presledki ne motijo", seznam.matches("tracker.example.org"))
    preveri("druga domena se ne ujame", !seznam.matches("example.net"))
    preveri("naddomena se ne ujame", !seznam.matches("net"))
    preveri("prazno se ne ujame", !seznam.matches(""))

    val zadetek = seznam.findMatch("banner.ads.doubleclick.net")
    preveriEnako("ujeta je shranjena domena", "doubleclick.net", zadetek?.matchedDomain)
    preveriEnako("kategorija", "Oglasi", zadetek?.category)
    preveriEnako("vir", "Vgrajeni seznam", zadetek?.sourceFeed)
    preveriEnako("stevilo vnosov", 3, seznam.size)
}

private fun preizkusRobnih() {
    println("\n== robni primeri ==")
    val seznam = DomainSuffixTrie()
    seznam.insert("#komentar.com")
    seznam.insert("")
    seznam.insert("...")
    preveriEnako("komentar in prazno se ne shranita", 0, seznam.size)

    seznam.insert("..primer..com.")
    preveri("odvecne pike se pospravijo", seznam.matches("primer.com"))
    preveriEnako("ujeta domena je pospravljena", "primer.com", seznam.findMatch("a.primer.com")?.matchedDomain)

    seznam.insert("dvojnik.si", "prva", "prvi")
    seznam.insert("dvojnik.si", "druga", "drugi")
    preveriEnako("dvojnik se ne podvoji", 2, seznam.size)
    preveriEnako("obvelja prvi vpis", "prva", seznam.findMatch("dvojnik.si")?.category)

    seznam.insert("si", "vrhnja", "test")
    preveriEnako("najkrajsa pripona obvelja", "si", seznam.findMatch("karkoli.dvojnik.si")?.matchedDomain)

    val dolga = (1..40).joinToString(".") { "zelodolgdelimena$it" }
    seznam.insert(dolga)
    preveri("predolga domena se ne shrani", !seznam.matches(dolga))

    seznam.clear()
    preveriEnako("po brisanju je prazno", 0, seznam.size)
    preveri("po brisanju ni zadetkov", !seznam.matches("primer.com"))
}

private fun preizkusVstavljanjaPoIskanju() {
    println("\n== vstavljanje po iskanju ==")
    val seznam = DomainSuffixTrie()
    seznam.insert("prva.com")
    preveri("prva se ujame", seznam.matches("prva.com"))
    seznam.insert("druga.com", "kategorija", "vir")
    preveri("po iskanju vstavljena se ujame", seznam.matches("x.druga.com"))
    preveri("prva se se vedno ujame", seznam.matches("prva.com"))
    preveriEnako("kategorija nove", "kategorija", seznam.findMatch("druga.com")?.category)
    preveriEnako("velikost", 2, seznam.size)
}

private fun nakljucnaDomena(r: java.util.Random): String {
    val crke = "abcdefghijklmnopqrstuvwxyz0123456789-"
    val koncnice = arrayOf("com", "net", "si", "org", "info", "xyz", "co.uk")
    val delov = 1 + r.nextInt(3)
    val sb = StringBuilder()
    for (d in 0 until delov) {
        val dolzina = 1 + r.nextInt(12)
        for (i in 0 until dolzina) sb.append(crke[r.nextInt(crke.length)])
        sb.append('.')
    }
    sb.append(koncnice[r.nextInt(koncnice.size)])
    return sb.toString()
}

private fun preizkusPrimerjave() {
    println("\n== primerjava z referencno izvedbo ==")
    val r = java.util.Random(20260914L)
    val seznam = DomainSuffixTrie()
    val referenca = Referenca()
    val vstavljene = ArrayList<String>()

    val kategorije = arrayOf<String?>(null, "Oglasi", "Phishing", "Botnet")
    val viri = arrayOf<String?>(null, "Feodo", "URLhaus", "Phishing Army", "StevenBlack")

    for (i in 0 until 10000) {
        val d = nakljucnaDomena(r)
        val k = kategorije[r.nextInt(kategorije.size)]
        val v = viri[r.nextInt(viri.size)]
        // Vcasih vstavimo isto domeno dvakrat in vcasih z velikimi crkami ali presledki.
        val zapis = when (r.nextInt(10)) {
            0 -> "  " + d.uppercase() + " "
            1 -> ".$d."
            else -> d
        }
        seznam.insert(zapis, k, v)
        referenca.insert(zapis, k, v)
        vstavljene.add(d)
        // Med vstavljanjem tudi iscemo, da preverimo strnjevanje sredi dela.
        if (i % 1000 == 999) {
            val g = vstavljene[r.nextInt(vstavljene.size)]
            val a = seznam.findMatch(g)
            val b = referenca.najdi(g)
            if ((a == null) != (b == null)) {
                preveri("iskanje med vstavljanjem se ujema pri $g", false)
            }
        }
    }
    preveriEnako("enako stevilo vnosov", referenca.velikost, seznam.size)

    var razlik = 0
    var zadetkov = 0
    for (i in 0 until 5000) {
        val gostitelj = if (r.nextBoolean()) {
            val osnova = vstavljene[r.nextInt(vstavljene.size)]
            when (r.nextInt(3)) {
                0 -> osnova
                1 -> "www." + osnova
                else -> "a.b." + osnova
            }
        } else {
            nakljucnaDomena(r)
        }
        val a = seznam.findMatch(gostitelj)
        val b = referenca.najdi(gostitelj)
        if (b != null) zadetkov++
        val enako = when {
            a == null && b == null -> true
            a == null || b == null -> false
            else -> a.matchedDomain == b.first && a.category == b.second && a.sourceFeed == b.third
        }
        if (!enako) {
            if (razlik < 5) println("    razlika pri $gostitelj: novo=$a, referenca=$b")
            razlik++
        }
    }
    preveriEnako("razlik med izvedbama", 0, razlik)
    preveri("preizkus je res kaj nasel ($zadetkov zadetkov)", zadetkov > 1000)
}

private fun preizkusPorabe() {
    println("\n== poraba pomnilnika ==")
    val koliko = 100000
    val r = java.util.Random(7L)
    System.gc()
    Thread.sleep(200)
    val prej = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    val seznam = DomainSuffixTrie()
    for (i in 0 until koliko) {
        seznam.insert(nakljucnaDomena(r), "Phishing", "Phishing Army")
    }
    // Iskanje sprozi strnjevanje; sele potem je poraba prava.
    seznam.matches("primer.com")
    System.gc()
    Thread.sleep(300)
    val potem = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    val naDomeno = (potem - prej).toDouble() / koliko
    println("    %d domen zavzame %.1f MB, kar je %.1f B na domeno".format(
        koliko, (potem - prej) / 1048576.0, naDomeno))
    println("    (stara drevesna izvedba je merila 292 B na domeno)")
    preveri("poraba je pod 60 B na domeno", naDomeno < 60)
    preveri("seznam po meritvi se vedno dela", seznam.size in 1..koliko)
}

private fun preizkusHitrosti() {
    println("\n== hitrost iskanja ==")
    val r = java.util.Random(11L)
    val seznam = DomainSuffixTrie()
    val domene = ArrayList<String>()
    for (i in 0 until 200000) {
        val d = nakljucnaDomena(r)
        domene.add(d)
        seznam.insert(d, "Oglasi", "Seznam")
    }
    seznam.matches("ogrevanje.com")
    val zacetek = System.nanoTime()
    var najdenih = 0
    for (i in 0 until 20000) {
        val g = "www." + domene[r.nextInt(domene.size)]
        if (seznam.matches(g)) najdenih++
    }
    val naIskanje = (System.nanoTime() - zacetek) / 20000.0 / 1000.0
    println("    %.1f mikrosekunde na iskanje pri 200.000 domenah (%d zadetkov)".format(naIskanje, najdenih))
    preveri("iskanje je hitrejse od 50 mikrosekund", naIskanje < 50)
}

fun main() {
    println("Preizkus strnjenega seznama domen")
    preizkusOsnovnih()
    preizkusRobnih()
    preizkusVstavljanjaPoIskanju()
    preizkusPrimerjave()
    preizkusPorabe()
    preizkusHitrosti()
    println()
    if (napak == 0) {
        println("Vse v redu.")
    } else {
        println("Napak: $napak")
        System.exit(1)
    }
}
