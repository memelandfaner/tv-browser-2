package si.safeer.tv

/**
 * Vedenje blokiranja oglasov, pribito s primeri.
 *
 * Namen: pred ciscenjem posnamemo, kaj brskalnik danes blokira in kaj spusti, da po
 * ciscenju lahko dokazemo, da se ni spremenilo nic, kar steje. Posebej so pribiti
 * pojavna in podtaknjena okna (popunder, in-page push), ker jih ne smemo izgubiti.
 */

private var napak = 0

private fun preveri(opis: String, pogoj: Boolean) {
    if (pogoj) println("  OK   $opis") else { println("  NAPAKA $opis"); napak++ }
}

private data class Primer(
    val naslov: String,
    val blokiran: Boolean,
    val opis: String,
    val glavniOkvir: Boolean = false
)

private val PRIMERI = listOf(
    // --- oglasni in sledilni strezniki ---
    Primer("https://doubleclick.net/ad.js", true, "oglasni streznik doubleclick"),
    Primer("https://static.doubleclick.net/slika.png", true, "poddomena oglasnega streznika"),
    Primer("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", true, "googlesyndication"),
    Primer("https://criteo.com/beacon", true, "criteo"),
    Primer("https://taboola.com/widget", true, "taboola"),
    Primer("https://scorecardresearch.com/beacon.js", true, "scorecardresearch"),
    Primer("https://mc.yandex.ru/watch/1", true, "yandex metrika"),

    // --- pojavna in podtaknjena okna: tega ne smemo izgubiti ---
    Primer("https://popads.net/pop.js", true, "popads"),
    Primer("https://popcash.net/p.js", true, "popcash"),
    Primer("https://propu.sh/ntfc.php", true, "propu.sh (in-page push)"),
    Primer("https://onclickmega.com/x", true, "onclickmega"),
    Primer("https://onclickalgo.com/x", true, "onclickalgo"),
    Primer("https://highperformancegate.com/x", true, "highperformancegate"),
    Primer("https://rollerads.com/x", true, "rollerads"),
    Primer("https://pushground.com/x", true, "pushground"),
    Primer("https://neznanastran.si/js/popunder.min.js", true, "pot s popunder"),
    Primer("https://neznanastran.si/monetag/loader.js", true, "pot z monetag"),

    // --- vsebinska blokada: stavnice in odrasle vsebine ---
    Primer("https://bet365.com/", true, "stavnica bet365 (glavni okvir)", glavniOkvir = true),
    Primer("https://1xbet.com/si", true, "stavnica 1xbet (glavni okvir)", glavniOkvir = true),
    Primer("https://vulkanvegas.com/", true, "igralnica", glavniOkvir = true),
    Primer("https://chaturbate.com/", true, "stran za odrasle", glavniOkvir = true),

    // --- zascita pred odkrivanjem razhroscevalnika ---
    Primer("https://nekastran.si/disable-devtool.min.js", true, "disable-devtool"),
    Primer("https://nekastran.si/js/devtools-detector.js", true, "devtools-detector"),

    // --- splosne oglasne poti pri nezaupanja vrednih gostiteljih ---
    Primer("https://neznanastran.si/ads.js", true, "pot /ads.js"),
    Primer("https://neznanastran.si/pixel.gif", true, "sledilna pika"),
    Primer("https://neznanastran.si/adservice.php", true, "adservice"),

    // --- kar mora ostati dosegljivo ---
    Primer("https://www.youtube.com/watch?v=abc", false, "youtube"),
    Primer("https://m.youtube.com/watch?v=abc", false, "mobilni youtube"),
    Primer("https://music.youtube.com/", false, "youtube music"),
    Primer("https://klik.nlb.si/login", false, "banka NLB"),
    Primer("https://www.rtvslo.si/slika.jpg", false, "rtvslo"),
    Primer("https://24ur.com/clanek", false, "24ur"),
    Primer("https://wikipedia.org/wiki/Slovenija", false, "wikipedija"),
    Primer("https://xploretv.si/watch.js", false, "Xplore predvajalnik"),
    Primer("https://image.tmdb.org/t/p/w500/a.jpg", false, "plakati TMDB"),
    Primer("https://core.streamex.sh/seg1.ts", false, "segment videa"),
    Primer("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1", false, "video tok"),

    // --- video tok, ki je v resnici oglas ---
    Primer("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1&oad=1", true, "oglasni video tok"),
    Primer("https://www.youtube.com/pagead/adview", true, "oglasna pot na zaupanja vrednem gostitelju")
)

fun main() {
    println("== vedenje blokiranja oglasov ==")
    AdBlockEngine.isEnabled = true

    for (p in PRIMERI) {
        val odgovor = AdBlockEngine.handleIntercept(p.naslov, null, null, p.glavniOkvir)
        val blokiran = odgovor != null
        preveri(
            "%-46s %s".format(p.opis, if (p.blokiran) "blokiran" else "spuščen"),
            blokiran == p.blokiran
        )
    }

    // Seznami sami: noben vnos ne sme biti odvecen ali podvojen.
    println()
    println("== higiena seznamov ==")
    val poddomene = AdBlockEngine.vgrajeneOglasneDomene().filter { d ->
        val deli = d.split(".")
        (1 until deli.size - 1).any { k -> deli.drop(k).joinToString(".") in AdBlockEngine.vgrajeneOglasneDomene() }
    }
    preveri("v oglasnem seznamu ni odvečnih poddomen (${poddomene.joinToString()})", poddomene.isEmpty())

    val beleOdvecne = AdBlockEngine.vgrajenaBelaLista().filter { d ->
        val deli = d.split(".")
        (1 until deli.size - 1).any { k -> deli.drop(k).joinToString(".") in AdBlockEngine.vgrajenaBelaLista() }
    }
    preveri("na beli listi ni odvečnih poddomen (${beleOdvecne.joinToString()})", beleOdvecne.isEmpty())

    val vObeh = AdBlockEngine.vgrajeneOglasneDomene().intersect(AdBlockEngine.vgrajenaBelaLista())
    preveri("nobena domena ni hkrati blokirana in na beli listi", vObeh.isEmpty())

    println()
    if (napak == 0) println("VSE V REDU") else { println("NAPAK: $napak"); System.exit(1) }
}
