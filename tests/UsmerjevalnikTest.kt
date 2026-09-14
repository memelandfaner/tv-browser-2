package si.safeer.tv.cast

/**
 * Preizkus bralca JSON in usmerjevalnika Safeer Huba brez naprave in brez omrezja.
 *
 * Kar preverjamo, je tisto, kar bi se v zivo pokazalo sele kot "ne dela" ali, huje, kot
 * tiha varnostna luknja: da se sporocilo posreduje samo tistemu, ki mu je namenjeno; da
 * nihce ne pride skozi brez potrditve na televizorju; da se vstopnica porabi enkrat; da
 * seznami ne rastejo v nedogled; in da bralec JSON zavrne tisto, kar ni pravilen JSON.
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

// ------------------------------------------------------------ pomozni odjemalec

private class Lazni(override val naslov: String = "192.168.0.50") : HubUsmerjevalnik.Odjemalec {
    val prejeto = ArrayList<String>()
    var zaprt = false
    var zapriKodo = 0
    override fun poslji(besedilo: String) {
        prejeto.add(besedilo)
    }
    override fun zapri(koda: Int, razlog: String) {
        zaprt = true
        zapriKodo = koda
    }
    fun zadnje(): String = prejeto.lastOrNull() ?: ""
    fun pocisti() = prejeto.clear()
}

private class LazniPomnilnik : HubUsmerjevalnik.Shramba {
    val vsebina = HashMap<String, String>()
    override fun beri(kljuc: String): String? = vsebina[kljuc]
    override fun pisi(kljuc: String, vrednost: String) {
        vsebina[kljuc] = vrednost
    }
}

private var cas = 1_700_000_000_000L
private var stevec = 0

private fun usmerjevalnik(shramba: HubUsmerjevalnik.Shramba? = null): HubUsmerjevalnik =
    HubUsmerjevalnik(shramba, { cas }, { n -> "t%d-%d".format(++stevec, n) })

private fun tip(sporocilo: String): String = JsonLahki.objekt(sporocilo)?.nizAli("type") ?: "?"
private fun polje(sporocilo: String, kljuc: String): String =
    JsonLahki.objekt(sporocilo)?.nizAli(kljuc) ?: ""

private fun registracija(deviceId: String, vloga: String, zmoznosti: String = "[\"url\",\"control\"]"): String =
    """{"id":"r-$deviceId","type":"cast.register","payload":{"device_id":"$deviceId","name":"Naprava $deviceId","role":"$vloga","capabilities":$zmoznosti}}"""

private fun zahteva(
    metoda: String,
    pot: String,
    telo: String = "",
    odjemalec: String = "192.168.0.50",
    glave: Map<String, String> = emptyMap(),
    poizvedba: Map<String, String> = emptyMap()
) = HubStreznik.Zahteva(metoda, pot, poizvedba, glave, telo, odjemalec)

// ------------------------------------------------------------ JSON

private fun preizkusJson() {
    println("\n== bralec JSON ==")

    val ovojnica = JsonLahki.objekt(
        """{"id":"a1","type":"cast.url","timestamp":1.5,"target":"tv","payload":{"url":"https://safeer.si/"}}"""
    )
    preveri("objekt se prebere", ovojnica != null)
    preveriEnako("tip", "cast.url", ovojnica?.niz("type"))
    preveriEnako("cilj", "tv", ovojnica?.niz("target"))
    preveriEnako("stevilo", 1.5, ovojnica?.stevilo("timestamp"))
    preveriEnako("tovor ostane nedotaknjen", """{"url":"https://safeer.si/"}""", ovojnica?.surovo("payload"))
    preveriEnako("vgnezdeno se prebere sele ob potrebi", "https://safeer.si/",
        ovojnica?.objekt("payload")?.niz("url"))
    preveriEnako("niz na mestu stevila je null", null, ovojnica?.stevilo("type"))

    // Kljuc znotraj niza ne sme zmesti bralca.
    val past = JsonLahki.objekt("""{"opomba":"\"type\":\"cast.control\"","type":"cast.ping"}""")
    preveriEnako("kljuc v nizu ne zmede bralca", "cast.ping", past?.niz("type"))

    preveriEnako("ubegi", "vrstica\nnova \"navednica\" č",
        JsonLahki.objekt("""{"a":"vrstica\nnova \"navednica\" č"}""")?.niz("a"))

    preveriEnako("zmoznosti", listOf("url", "control", "sync"),
        JsonLahki.objekt("""{"c":["url","control","sync"]}""")?.nizi("c"))

    preveri("smeti za objektom se zavrnejo", JsonLahki.objekt("""{"a":1} nekaj""") == null)
    preveri("nezakljucen niz se zavrne", JsonLahki.objekt("""{"a":"b}""") == null)
    preveri("nezakljucen objekt se zavrne", JsonLahki.objekt("""{"a":1""") == null)
    preveri("manjkajoca vejica se zavrne", JsonLahki.objekt("""{"a":1 "b":2}""") == null)
    preveri("nadzorni znak v nizu se zavrne", JsonLahki.objekt("{\"a\":\"b\nc\"}") == null)
    preveri("pokvarjeno stevilo se zavrne", JsonLahki.objekt("""{"a":01.}""") == null)
    preveri("pokvarjen vgnezden objekt se zavrne", JsonLahki.objekt("""{"a":{"b":}}""") == null)
    preveri("seznam ni objekt", JsonLahki.objekt("""[1,2,3]""") == null)
    preveri("prazen objekt je v redu", JsonLahki.objekt("{}") != null)

    val globoko = StringBuilder()
    for (i in 0..20) globoko.append("""{"a":""")
    globoko.append("1")
    for (i in 0..20) globoko.append("}")
    preveri("pregloboko vgnezdenje se zavrne", JsonLahki.objekt(globoko.toString()) == null)

    val zapis = JsonLahki.Zapis()
        .niz("a", "z \"navednico\"")
        .stevilo("b", 3.0)
        .stevilo("c", 1.25)
        .logicno("d", true)
        .nic("e")
        .seznamNizov("f", listOf("x", "y"))
    preveriEnako("zapis", """{"a":"z \"navednico\"","b":3,"c":1.25,"d":true,"e":null,"f":["x","y"]}""",
        zapis.toString())
    preveri("kar zapisemo, znamo tudi prebrati", JsonLahki.objekt(zapis.toString())?.niz("a") == "z \"navednico\"")
}

// ------------------------------------------------------------ register in cast

private fun preizkusRegistra() {
    println("\n== register naprav in cast ==")
    val u = usmerjevalnik()

    val tv = Lazni("192.168.0.20")
    val telefon = Lazni("192.168.0.30")

    preveriEnako("prejemnik se registrira", "cast.ack", tip(u.odgovorNa(tv, registracija("tv1", "receiver"))!!))
    preveriEnako("potrditev je sprejeta", "accepted", polje(u.odgovorNa(tv, registracija("tv1", "receiver"))!!, "status"))

    val odgovorPosiljatelja = u.odgovorNa(telefon, registracija("fon1", "sender"))
    preveriEnako("posiljatelj se registrira", "accepted", polje(odgovorPosiljatelja!!, "status"))
    preveriEnako("posiljatelj takoj dobi seznam naprav", "cast.devices", tip(telefon.zadnje()))
    preveri("v seznamu je prejemnik", telefon.zadnje().contains("\"id\":\"tv1\""))
    preveri("v seznamu ni posiljatelja", !telefon.zadnje().contains("\"id\":\"fon1\""))

    preveriEnako("brez device_id zavrnjeno", "rejected",
        polje(u.odgovorNa(telefon, """{"id":"x","type":"cast.register","payload":{}}""")!!, "status"))

    // Posredovanje
    tv.pocisti()
    val ukaz = """{"id":"u1","type":"cast.url","target":"tv1","payload":{"url":"https://safeer.si/"}}"""
    val potrditev = u.odgovorNa(telefon, ukaz)!!
    preveriEnako("ukaz je sprejet", "accepted", polje(potrditev, "status"))
    preveriEnako("potrditev se sklicuje na sporocilo", "u1", polje(potrditev, "ref_id"))
    preveriEnako("prejemnik dobi ukaz nespremenjen", ukaz, tv.zadnje())

    preveriEnako("neznan cilj je zavrnjen", "rejected",
        polje(u.odgovorNa(telefon, """{"id":"u2","type":"cast.url","target":"ni-me"}""")!!, "status"))

    preveriEnako("ping vrne pong", "cast.pong", tip(u.odgovorNa(telefon, """{"id":"p1","type":"cast.ping"}""")!!))
    preveriEnako("pong ohrani id", "p1", polje(u.odgovorNa(telefon, """{"id":"p1","type":"cast.ping"}""")!!, "id"))

    val neznano = u.odgovorNa(telefon, """{"id":"n1","type":"racun.izprazni"}""")!!
    preveriEnako("neznan tip ni posredovan", "error", polje(neznano, "status"))
    preveri("neznan tip dobi razlago", polje(neznano, "error").contains("Neznan tip"))

    preveriEnako("pokvarjeno sporocilo ne podre nicesar", "error",
        polje(u.odgovorNa(telefon, "{to ni json")!!, "status"))

    preveriEnako("sporocilo brez tipa", "error",
        polje(u.odgovorNa(telefon, """{"id":"b1"}""")!!, "status"))

    // cast.status se razposlje posiljateljem, odgovora ni
    telefon.pocisti()
    preveri("cast.status nima odgovora",
        u.odgovorNa(tv, """{"id":"s1","type":"cast.status","device_id":"tv1","payload":{"state":"playing"}}""") == null)
    preveriEnako("posiljatelj izve za stanje", "cast.status", tip(telefon.zadnje()))

    // Odklop prejemnika osvezi seznam pri posiljateljih
    telefon.pocisti()
    u.odklopi(tv)
    preveriEnako("po odklopu se seznam osvezi", "cast.devices", tip(telefon.zadnje()))
    preveri("odklopljenega prejemnika ni vec v seznamu", !telefon.zadnje().contains("\"id\":\"tv1\""))
    preveriEnako("ukaz odklopljeni napravi je zavrnjen", "rejected",
        polje(u.odgovorNa(telefon, """{"id":"u3","type":"cast.url","target":"tv1"}""")!!, "status"))
}

// ------------------------------------------------------------ sinhronizacija

private fun preizkusSinhronizacije() {
    println("\n== sinhronizacija ==")
    val u = usmerjevalnik()
    val a = Lazni("192.168.0.31")
    val b = Lazni("192.168.0.32")
    u.odgovorNa(a, registracija("fonA", "sync-client", """["sync"]"""))
    u.odgovorNa(b, registracija("fonB", "sync-client", """["sync"]"""))

    b.pocisti()
    val podatki = """{"id":"d1","type":"sync.data","payload":{"category":"bookmarks","version":3,"timestamp":100,"data":[{"u":"https://safeer.si/"}]}}"""
    preveriEnako("sync.data sprejet", "accepted", polje(u.odgovorNa(a, podatki)!!, "status"))
    preveriEnako("potrditev je v prostoru sync", "sync.ack", tip(u.odgovorNa(a, podatki)!!))
    preveriEnako("druga naprava dobi podatke", podatki, b.zadnje())
    preveri("posiljatelj sam sebi ne posilja", a.prejeto.none { it == podatki })
    preveriEnako("kategorija je shranjena", listOf("bookmarks"), u.kategorijeSinhronizacije())

    // Naprava, ki je bila ugasnjena, dohiti
    val c = Lazni("192.168.0.33")
    u.odgovorNa(c, registracija("fonC", "sync-client", """["sync"]"""))
    c.pocisti()
    val zahtevek = """{"id":"z1","type":"sync.request","payload":{"category":"bookmarks"}}"""
    preveriEnako("zahtevek sprejet", "accepted", polje(u.odgovorNa(c, zahtevek)!!, "status"))
    preveriEnako("dobi shranjeno stanje", "sync.data", tip(c.zadnje()))
    preveri("stanje vsebuje podatke", c.zadnje().contains("https://safeer.si/"))
    preveriEnako("stanje je naslovljeno nanj", "fonC", polje(c.zadnje(), "target"))

    c.pocisti()
    preveriEnako("ce ze ima novejso razlicico, ne posiljamo", "accepted",
        polje(u.odgovorNa(c, """{"id":"z2","type":"sync.request","payload":{"category":"bookmarks","since_version":9}}""")!!, "status"))
    preveri("in res ne dobi nicesar", c.prejeto.isEmpty())

    // Naslovljeno sporocilo gre samo naslovniku
    b.pocisti()
    c.pocisti()
    val naslovljeno = """{"id":"n2","type":"sync.status","target":"fonB","payload":{"state":"ok"}}"""
    preveriEnako("naslovljeno sprejeto", "accepted", polje(u.odgovorNa(a, naslovljeno)!!, "status"))
    preveriEnako("dobi ga samo naslovnik", naslovljeno, b.zadnje())
    preveri("drugi ga ne dobijo", c.prejeto.isEmpty())

    preveriEnako("neznan naslovnik zavrnjen", "rejected",
        polje(u.odgovorNa(a, """{"id":"n3","type":"sync.status","target":"ni-ga"}""")!!, "status"))

    // sync.ack gre naprej brez odgovora
    a.pocisti()
    val potrdilo = """{"id":"a1","type":"sync.ack","target":"fonA","ref_id":"d1","status":"accepted"}"""
    preveri("sync.ack nima odgovora", u.odgovorNa(b, potrdilo) == null)
    preveriEnako("sync.ack pride do naslovnika", potrdilo, a.zadnje())

    // Prevelika kategorija se ne shrani
    val velika = "x".repeat(HubUsmerjevalnik.NAJVECJA_KATEGORIJA + 10)
    u.odgovorNa(a, """{"id":"v1","type":"sync.data","payload":{"category":"history","version":1,"timestamp":200,"data":"$velika"}}""")
    preveri("prevelika kategorija se ne shrani", !u.kategorijeSinhronizacije().contains("history"))

    // Starejsi zapis ne povozi novejsega
    u.odgovorNa(a, """{"id":"s1","type":"sync.data","payload":{"category":"bookmarks","version":1,"timestamp":50,"data":["staro"]}}""")
    val d = Lazni("192.168.0.34")
    u.odgovorNa(d, registracija("fonD", "sync-client", """["sync"]"""))
    d.pocisti()
    u.odgovorNa(d, """{"id":"z3","type":"sync.request","payload":{"category":"bookmarks"}}""")
    preveri("starejsi zapis ne povozi novejsega", d.zadnje().contains("https://safeer.si/"))

    // Stevilo kategorij je omejeno
    for (i in 0 until HubUsmerjevalnik.NAJVEC_KATEGORIJ + 5) {
        u.odgovorNa(a, """{"id":"k$i","type":"sync.data","payload":{"category":"kat$i","version":1,"timestamp":${300 + i},"data":["$i"]}}""")
    }
    preveri("kategorij ni vec kot dovoljeno",
        u.kategorijeSinhronizacije().size <= HubUsmerjevalnik.NAJVEC_KATEGORIJ)

    // Brez druge naprave, ki sinhronizira
    val u2 = usmerjevalnik()
    val sam = Lazni("192.168.0.35")
    u2.odgovorNa(sam, registracija("sam", "sync-client", """["sync"]"""))
    preveriEnako("sam s sabo ne sinhronizira", "rejected",
        polje(u2.odgovorNa(sam, """{"id":"x1","type":"sync.status","payload":{"a":1}}""")!!, "status"))
}

// ------------------------------------------------------------ seznanjanje

private fun preizkusSeznanjanja() {
    println("\n== seznanjanje ==")
    val pomnilnik = LazniPomnilnik()
    val u = usmerjevalnik(pomnilnik)

    val (pairId, pin) = u.zacniSeznanitev("fon1", "Matejev telefon", "192.168.0.30")!!
    preveri("koda je sestmestna", pin.length == 6 && pin.all { it.isDigit() })
    preveriEnako("prijava caka", 1, u.cakajocePrijave().size)
    preveriEnako("vmesnik vidi ime naprave", "Matejev telefon", u.cakajocePrijave().first().ime)
    preveriEnako("vmesnik vidi isto kodo", pin, u.cakajocePrijave().first().pin)

    preveri("pred potrditvijo ni zetona", u.prevzemiZeton(pairId) == null)

    preveri("potrditev na televizorju uspe", u.potrdiPrijavo(pairId))
    val zeton = u.prevzemiZeton(pairId)
    preveri("naprava prevzame zeton", !zeton.isNullOrBlank())
    preveri("zeton je veljaven", u.jeVeljavenZeton(zeton))
    preveri("drugic prevzem ne uspe", u.prevzemiZeton(pairId) == null)
    preveriEnako("prijava ni vec na seznamu", 0, u.cakajocePrijave().size)
    preveriEnako("naprava je na seznamu seznanjenih", 1, u.seznanjeneNaprave().size)
    preveri("zetoni so shranjeni", pomnilnik.vsebina.isNotEmpty())
    preveri("zetona ne pokazemo v seznamu", u.seznanjeneNaprave().none { it.ime.contains("saf_tv_") })

    // Zetoni prezivijo ponovni zagon
    val u2 = HubUsmerjevalnik(pomnilnik, { cas })
    preveri("po ponovnem zagonu zeton se velja", u2.jeVeljavenZeton(zeton))
    preveri("neveljaven zeton ne velja", !u2.jeVeljavenZeton("saf_tv_neki"))
    preveri("prazen zeton ne velja", !u2.jeVeljavenZeton(""))

    // Odvzem dostopa odklopi napravo
    val naprava = Lazni("192.168.0.30")
    u2.odgovorNa(naprava, registracija("fon1", "sender"))
    preveriEnako("dostop odvzet", 1, u2.prekliciNapravo("fon1"))
    preveri("naprava je odklopljena", naprava.zaprt)
    preveri("zeton po odvzemu ne velja vec", !u2.jeVeljavenZeton(zeton))

    // Ista naprava ne kopici prijav
    val u3 = usmerjevalnik()
    u3.zacniSeznanitev("fon2", "A", "192.168.0.31")
    u3.zacniSeznanitev("fon2", "A", "192.168.0.31")
    preveriEnako("ista naprava ne kopici prijav", 1, u3.cakajocePrijave().size)

    // Vec kot toliko cakajocih ne sprejmemo
    for (i in 0 until HubUsmerjevalnik.NAJVEC_CAKAJOCIH + 3) u3.zacniSeznanitev("n$i", "N$i", "192.168.0.4$i")
    preveriEnako("cakajocih ni vec kot dovoljeno", HubUsmerjevalnik.NAJVEC_CAKAJOCIH, u3.cakajocePrijave().size)
    preveri("nova prijava cez mejo je zavrnjena", u3.zacniSeznanitev("cez", "C", "192.168.0.99") == null)

    // Koda potece
    val u4 = usmerjevalnik()
    val (star, _) = u4.zacniSeznanitev("fon3", "B", "192.168.0.32")!!
    cas += HubUsmerjevalnik.PIN_VELJA_MS + 1000
    preveriEnako("potekla prijava izgine", 0, u4.cakajocePrijave().size)
    preveri("potekle prijave ni mogoce potrditi", !u4.potrdiPrijavo(star))
    cas -= HubUsmerjevalnik.PIN_VELJA_MS + 1000

    // Zavrnitev
    val u5 = usmerjevalnik()
    val (zaZavreci, _) = u5.zacniSeznanitev("fon4", "C", "192.168.0.33")!!
    preveri("zavrnitev uspe", u5.zavrniPrijavo(zaZavreci))
    preveriEnako("po zavrnitvi ni prijave", 0, u5.cakajocePrijave().size)
    preveri("zavrnjene ni mogoce prevzeti", u5.prevzemiZeton(zaZavreci) == null)
}

// ------------------------------------------------------------ vstopnice

private fun preizkusVstopnic() {
    println("\n== vstopnice ==")
    val u = usmerjevalnik()
    val vstopnica = u.izdajVstopnico()
    preveri("vstopnica velja enkrat", u.porabiVstopnico(vstopnica))
    preveri("drugic ne velja", !u.porabiVstopnico(vstopnica))
    preveri("izmisljena ne velja", !u.porabiVstopnico("kar-tako"))
    preveri("prazna ne velja", !u.porabiVstopnico(""))

    val potekla = u.izdajVstopnico()
    cas += HubUsmerjevalnik.VSTOPNICA_VELJA_MS + 1000
    preveri("potekla ne velja", !u.porabiVstopnico(potekla))
    cas -= HubUsmerjevalnik.VSTOPNICA_VELJA_MS + 1000

    val prva = u.izdajVstopnico()
    for (i in 0 until HubUsmerjevalnik.NAJVEC_VSTOPNIC + 2) u.izdajVstopnico()
    preveri("najstarejsa pade ven, ko jih je prevec", !u.porabiVstopnico(prva))

    // Nadgradnja v WebSocket
    val nova = u.izdajVstopnico()
    preveriEnako("brez vstopnice ni nadgradnje", "neveljavna ali potekla vstopnica",
        u.preveriVstopnico(zahteva("GET", "/cast/ws")))
    preveriEnako("z interneta ni nadgradnje", "samo v krajevnem omrežju",
        u.preveriVstopnico(zahteva("GET", "/cast/ws", odjemalec = "8.8.8.8", poizvedba = mapOf("ticket" to nova))))
    preveriEnako("napacna pot", "neznana pot", u.preveriVstopnico(zahteva("GET", "/link/ws")))
    preveri("z vstopnico iz omrezja gre",
        u.preveriVstopnico(zahteva("GET", "/cast/ws", poizvedba = mapOf("ticket" to nova))) == null)
}

// ------------------------------------------------------------ HTTP

private fun preizkusHttp() {
    println("\n== koncne tocke ==")
    val u = usmerjevalnik()

    val zunaj = u.odgovori(zahteva("POST", "/cast/pair/start", """{"device_id":"x","name":"X"}""", "203.0.113.5"))
    preveriEnako("z interneta se ni mogoce prijaviti", 403, zunaj?.koda)

    preveriEnako("brez device_id je napaka", 400,
        u.odgovori(zahteva("POST", "/cast/pair/start", """{"name":"X"}"""))?.koda)

    val prijava = u.odgovori(zahteva("POST", "/cast/pair/start", """{"device_id":"fon1","name":"Telefon"}"""))
    preveriEnako("prijava uspe", 200, prijava?.koda)
    val pairId = polje(prijava!!.telo, "pair_id")
    val pin = polje(prijava.telo, "pin")
    preveri("odgovor vsebuje kodo", pin.length == 6)
    preveriEnako("koda na televizorju je ista", pin, u.cakajocePrijave().first().pin)

    val prezgodaj = u.odgovori(zahteva("POST", "/cast/pair/claim", """{"pair_id":"$pairId"}"""))
    preveriEnako("pred potrditvijo prevzem ne uspe", "false",
        JsonLahki.objekt(prezgodaj!!.telo)?.logicno("approved").toString())

    u.potrdiPrijavo(pairId)
    val prevzem = u.odgovori(zahteva("POST", "/cast/pair/claim", """{"pair_id":"$pairId"}"""))
    preveriEnako("po potrditvi prevzem uspe", true, JsonLahki.objekt(prevzem!!.telo)?.logicno("approved"))
    val zeton = polje(prevzem.telo, "token")
    preveri("zeton ima prepoznavno predpono", zeton.startsWith("saf_tv_"))

    // Dolzino zetona merimo na pravi nakljucnosti, ne na laznem generatorju iz preizkusa.
    val pravi = HubUsmerjevalnik(null, { cas })
    val (praviPair, _) = pravi.zacniSeznanitev("fon9", "Pravi", "192.168.0.60")!!
    pravi.potrdiPrijavo(praviPair)
    val praviZeton = pravi.prevzemiZeton(praviPair).orEmpty()
    preveri("pravi zeton je dovolj dolg", praviZeton.length >= 48)
    preveri("dva zetona nista enaka", praviZeton != run {
        val (drugi, _) = pravi.zacniSeznanitev("fon10", "Drugi", "192.168.0.61")!!
        pravi.potrdiPrijavo(drugi)
        pravi.prevzemiZeton(drugi).orEmpty()
    })

    preveriEnako("brez zetona ni vstopnice", 401, u.odgovori(zahteva("POST", "/cast/ticket"))?.koda)
    val vstopnica = u.odgovori(zahteva("POST", "/cast/ticket", glave = mapOf("x-safeer-token" to zeton)))
    preveriEnako("z zetonom je vstopnica", 200, vstopnica?.koda)
    preveri("vstopnica takoj deluje", u.porabiVstopnico(polje(vstopnica!!.telo, "ticket")))

    preveriEnako("seznam naprav zahteva zeton", 401, u.odgovori(zahteva("GET", "/cast/devices"))?.koda)
    preveriEnako("s zetonom je seznam", 200,
        u.odgovori(zahteva("GET", "/cast/devices", glave = mapOf("x-safeer-token" to zeton)))?.koda)

    val stanje = u.odgovori(zahteva("GET", "/cast/health", glave = mapOf("x-safeer-token" to zeton)))
    preveriEnako("stanje je na voljo", 200, stanje?.koda)
    preveriEnako("razlicica protokola je ista kot na racunalniku", "0.2", polje(stanje!!.telo, "protocol"))

    preveri("neznana pot ni odgovorjena", u.odgovori(zahteva("GET", "/cast/skrivnost")) == null)
    // Naprava, ki isce Hub, prav po tem loci Safeer Hub od tujega streznika na istih vratih.
    preveriEnako("znana pot z napacnim glagolom vrne 405", 405,
        u.odgovori(zahteva("GET", "/cast/ticket"))?.koda)
    preveriEnako("tudi prijava z GET vrne 405", 405,
        u.odgovori(zahteva("GET", "/cast/pair/start"))?.koda)
    preveri("potrjevanje ni dosegljivo po omrezju",
        u.odgovori(zahteva("POST", "/cast/pair/approve", """{"pair_id":"$pairId"}""")) == null)
    preveri("cakajocih prijav ni mogoce prebrati po omrezju",
        u.odgovori(zahteva("GET", "/cast/pair/pending")) == null)
    preveri("odvzem dostopa ni dosegljiv po omrezju",
        u.odgovori(zahteva("POST", "/cast/devices/revoke", """{"device_id":"fon1"}""")) == null)

    // Prevec prijav -> 429
    for (i in 0 until HubUsmerjevalnik.NAJVEC_CAKAJOCIH + 2) {
        u.odgovori(zahteva("POST", "/cast/pair/start", """{"device_id":"m$i","name":"M$i"}"""))
    }
    preveriEnako("prevec prijav dobi 429", 429,
        u.odgovori(zahteva("POST", "/cast/pair/start", """{"device_id":"zadnji","name":"Z"}"""))?.koda)
}

// ------------------------------------------------------------ meje

private fun preizkusMeja() {
    println("\n== meje pomnilnika ==")
    val u = usmerjevalnik()
    val odjemalci = ArrayList<Lazni>()
    for (i in 0 until HubUsmerjevalnik.NAJVEC_NAPRAV + 4) {
        val o = Lazni("192.168.0.${100 + i}")
        odjemalci.add(o)
        u.odgovorNa(o, registracija("n$i", "receiver"))
    }
    preveri("povezanih naprav ni vec kot dovoljeno", u.steviloNaprav() <= HubUsmerjevalnik.NAJVEC_NAPRAV)

    val dolgoIme = "I".repeat(200)
    val u2 = usmerjevalnik()
    val o = Lazni()
    u2.odgovorNa(o, """{"id":"d1","type":"cast.register","payload":{"device_id":"dolg","name":"$dolgoIme"}}""")
    val ime = JsonLahki.objekt("{\"n\":${u2.povezaniPrejemniki().substringAfter("\"name\":").substringBefore(",")}}")
        ?.niz("n").orEmpty()
    preveri("ime je porezano", ime.length <= HubUsmerjevalnik.NAJVEC_IMENA)

    preveri("krajevni naslov je prepoznan", HubUsmerjevalnik.jeKrajevni("192.168.0.5"))
    preveri("10.x je krajevni", HubUsmerjevalnik.jeKrajevni("10.0.0.7"))
    preveri("172.16.x je krajevni", HubUsmerjevalnik.jeKrajevni("172.16.4.4"))
    preveri("localhost je krajevni", HubUsmerjevalnik.jeKrajevni("127.0.0.1"))
    preveri("javni naslov ni krajevni", !HubUsmerjevalnik.jeKrajevni("8.8.8.8"))
    preveri("prazen naslov ni krajevni", !HubUsmerjevalnik.jeKrajevni(""))
    preveri("ime gostitelja ni naslov", !HubUsmerjevalnik.jeKrajevni("zlonamerno.example.com"))
}

fun main() {
    println("Preizkus bralca JSON in usmerjevalnika Safeer Huba")
    preizkusJson()
    preizkusRegistra()
    preizkusSinhronizacije()
    preizkusSeznanjanja()
    preizkusVstopnic()
    preizkusHttp()
    preizkusMeja()
    println()
    if (napak == 0) {
        println("Vse v redu.")
    } else {
        println("Napak: $napak")
        System.exit(1)
    }
}
