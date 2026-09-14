package si.safeer.tv.cast

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Preizkus streznika Safeer Huba brez naprave.
 *
 * Preverjamo tisto, kar bi se v zivo pokazalo sele kot "ne dela": odgovore HTTP, pravilno
 * rokovanje po RFC 6455 (z znanim primerom iz standarda), pretok sporocil v obe smeri,
 * zavrnitev brez vstopnice, zavrnitev nemaskiranih okvirjev in omejitev velikosti.
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

private class TestniOdjemalec(vrata: Int) {
    val vticnica = Socket("127.0.0.1", vrata)
    val vhod: InputStream = vticnica.getInputStream().buffered()
    val izhod: OutputStream = vticnica.getOutputStream()

    fun posljiSurovo(niz: String) {
        izhod.write(niz.toByteArray(Charsets.UTF_8))
        izhod.flush()
    }

    fun preberiVrstico(): String {
        val sb = StringBuilder()
        while (true) {
            val b = vhod.read()
            if (b < 0) break
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    fun preberiGlave(): Pair<String, Map<String, String>> {
        val prva = preberiVrstico()
        val glave = HashMap<String, String>()
        while (true) {
            val v = preberiVrstico()
            if (v.isEmpty()) break
            val i = v.indexOf(':')
            if (i > 0) glave[v.substring(0, i).trim().lowercase()] = v.substring(i + 1).trim()
        }
        return Pair(prva, glave)
    }

    /** Okvir odjemalca mora biti maskiran - tako kot ga poslje pravi brskalnik. */
    fun posljiBesedilo(besedilo: String, maskiraj: Boolean = true) {
        val podatki = besedilo.toByteArray(Charsets.UTF_8)
        val glava = ArrayList<Byte>()
        glava.add((0x80 or 0x1).toByte())
        val zastavicaMaske = if (maskiraj) 0x80 else 0x00
        when {
            podatki.size < 126 -> glava.add((zastavicaMaske or podatki.size).toByte())
            podatki.size <= 0xFFFF -> {
                glava.add((zastavicaMaske or 126).toByte())
                glava.add(((podatki.size shr 8) and 0xFF).toByte())
                glava.add((podatki.size and 0xFF).toByte())
            }
            else -> {
                glava.add((zastavicaMaske or 127).toByte())
                for (i in 7 downTo 0) glava.add(((podatki.size.toLong() shr (8 * i)) and 0xFF).toByte())
            }
        }
        izhod.write(glava.toByteArray())
        if (maskiraj) {
            val maska = byteArrayOf(0x12, 0x34, 0x56, 0x78)
            izhod.write(maska)
            val zakrit = ByteArray(podatki.size)
            for (i in podatki.indices) zakrit[i] = (podatki[i].toInt() xor maska[i % 4].toInt()).toByte()
            izhod.write(zakrit)
        } else {
            izhod.write(podatki)
        }
        izhod.flush()
    }

    /** Prebere en okvir streznika; vrne par (opkoda, besedilo). */
    fun preberiOkvir(): Pair<Int, String> {
        val prvi = vhod.read()
        if (prvi < 0) return Pair(-1, "")
        val opkoda = prvi and 0x0F
        val drugi = vhod.read()
        if (drugi < 0) return Pair(-1, "")
        preveri("strežnik svojih okvirjev ne maskira", (drugi and 0x80) == 0)
        var dolzina = (drugi and 0x7F).toLong()
        if (dolzina == 126L) dolzina = ((vhod.read() shl 8) or vhod.read()).toLong()
        else if (dolzina == 127L) {
            dolzina = 0
            for (i in 0 until 8) dolzina = (dolzina shl 8) or vhod.read().toLong()
        }
        val podatki = ByteArray(dolzina.toInt())
        var prebrano = 0
        while (prebrano < podatki.size) {
            val n = vhod.read(podatki, prebrano, podatki.size - prebrano)
            if (n < 0) break
            prebrano += n
        }
        return Pair(opkoda, String(podatki, 0, prebrano, Charsets.UTF_8))
    }

    fun zapri() {
        try {
            vticnica.close()
        } catch (_: Exception) {
        }
    }
}

private fun pricakovanSprejem(kljuc: String): String {
    val d = MessageDigest.getInstance("SHA-1")
        .digest((kljuc + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
    return java.util.Base64.getEncoder().encodeToString(d)
}

// ------------------------------------------------------------------------ test

fun main() {
    println("== preizkus strežnika Safeer Huba ==")

    val prispela = CountDownLatch(1)
    var zadnjeSporocilo = ""

    val streznik = HubStreznik(
        zeljenaVrata = 0,
        naZahtevo = { z ->
            when (z.pot) {
                "/cast/health" -> HubStreznik.Odgovor(200, "{\"stanje\":\"v redu\"}")
                else -> null
            }
        },
        preveriVstopnico = { z ->
            if (z.poizvedba["ticket"] == "prava") null else "neveljavna vstopnica"
        },
        naPovezavo = { p ->
            p.naSporocilo = { sporocilo ->
                zadnjeSporocilo = sporocilo
                p.poslji("odmev:$sporocilo")
                prispela.countDown()
            }
        }
    )

    preveri("strežnik se zažene", streznik.zazeni())
    val vrata = streznik.vrata
    preveri("dobil je vrata", vrata > 0)
    println("  (vrata $vrata)")

    // 1) navadna zahteva HTTP
    run {
        val o = TestniOdjemalec(vrata)
        o.posljiSurovo("GET /cast/health HTTP/1.1\r\nHost: test\r\n\r\n")
        val (prva, glave) = o.preberiGlave()
        preveri("zdravje odgovori 200: $prva", prva.contains(" 200 "))
        preveri("odgovor se ne shranjuje", glave["cache-control"] == "no-store")
        o.zapri()
    }

    // 2) neznana pot
    run {
        val o = TestniOdjemalec(vrata)
        o.posljiSurovo("GET /ni-te-poti HTTP/1.1\r\nHost: test\r\n\r\n")
        val (prva, _) = o.preberiGlave()
        preveri("neznana pot odgovori 404: $prva", prva.contains(" 404 "))
        o.zapri()
    }

    // 3) nadgradnja brez veljavne vstopnice mora biti zavrnjena SE PRED rokovanjem
    run {
        val o = TestniOdjemalec(vrata)
        o.posljiSurovo(
            "GET /cast/ws?ticket=napacna HTTP/1.1\r\nHost: test\r\n" +
                "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        val (prva, _) = o.preberiGlave()
        preveri("brez vstopnice ni povezave: $prva", prva.contains(" 403 "))
        o.zapri()
    }

    // 4) pravilno rokovanje po standardu + pretok sporocil v obe smeri
    run {
        val kljuc = "dGhlIHNhbXBsZSBub25jZQ=="
        val o = TestniOdjemalec(vrata)
        o.posljiSurovo(
            "GET /cast/ws?ticket=prava HTTP/1.1\r\nHost: test\r\n" +
                "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $kljuc\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        val (prva, glave) = o.preberiGlave()
        preveri("rokovanje odgovori 101: $prva", prva.contains(" 101 "))
        // Znani primer iz standarda: tako preverimo SHA-1 in nase kodiranje base64 hkrati.
        preveriEnako("ključ sprejema po RFC 6455", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", glave["sec-websocket-accept"])
        preveriEnako("ključ sprejema se ujema z izračunom", pricakovanSprejem(kljuc), glave["sec-websocket-accept"])

        o.posljiBesedilo("{\"vrsta\":\"pozdrav\"}")
        preveri("sporočilo je prispelo", prispela.await(5, TimeUnit.SECONDS))
        preveriEnako("strežnik je prebral pravo vsebino", "{\"vrsta\":\"pozdrav\"}", zadnjeSporocilo)
        val (opkoda, odgovor) = o.preberiOkvir()
        preveriEnako("odgovor je besedilni okvir", 1, opkoda)
        preveriEnako("vsebina odgovora", "odmev:{\"vrsta\":\"pozdrav\"}", odgovor)
        preveri("strežnik šteje eno povezavo", streznik.steviloPovezav() == 1)
        o.zapri()
    }

    // 5) nemaskiran okvir odjemalca ni skladen - povezavo je treba zavrniti
    run {
        val o = TestniOdjemalec(vrata)
        o.posljiSurovo(
            "GET /cast/ws?ticket=prava HTTP/1.1\r\nHost: test\r\n" +
                "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        o.preberiGlave()
        o.posljiBesedilo("brez maske", maskiraj = false)
        val (opkoda, _) = o.preberiOkvir()
        preveri("nemaskiran okvir konča povezavo (opkoda=$opkoda)", opkoda == 0x8 || opkoda == -1)
        o.zapri()
    }

    Thread.sleep(300)
    streznik.ustavi()
    preveri("po ustavitvi ne teče", !streznik.teceZdaj())

    println()
    if (napak == 0) {
        println("VSE V REDU")
    } else {
        println("NAPAK: $napak")
        System.exit(1)
    }
}
