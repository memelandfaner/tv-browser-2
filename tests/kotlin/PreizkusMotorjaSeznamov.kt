/*
 * Preizkus motorja seznamov brez Androida: FilterListEngine je cist JVM, zato ga
 * lahko prevedemo in pozenemo s samim kotlinc-em -- brez emulatorja in brez SDK.
 *
 * Zene ga tools/preveri-motor.sh (in CI).
 */
import com.safeer.threatfeed.FilterListEngine
import com.safeer.threatfeed.FilterRequest
import com.safeer.threatfeed.ResourceType

private var padlo = 0

private fun trdi(opis: String, pogoj: Boolean) {
    if (pogoj) {
        println("  V REDU  $opis")
    } else {
        println("  PADLO   $opis")
        padlo++
    }
}

private fun preizkusKozmetika() {
    println("kozmeticna pravila (skrivanje elementov)")
    val set = FilterListEngine.compile(listOf(
        "! komentar",
        "example.com##.oglas",
        "example.com,drugi.com##.pasica",
        "##.splosno",                      // brez domene: namenoma preskoceno
        "tretji.com##div:has-text(Oglas)", // proceduralno: ni CSS, preskoceno
        "example.com#@#.pasica",           // izjema za to domeno
        "||oglasi.example.net^",           // omrezno pravilo, ne kozmeticno
    ))

    trdi("domena dobi svoj selektor",
        set.cosmetic.selectorsFor("example.com").contains(".oglas"))
    trdi("poddomena podeduje pravila",
        set.cosmetic.selectorsFor("www.example.com").contains(".oglas"))
    trdi("pravilo z vec domenami velja za obe",
        set.cosmetic.selectorsFor("drugi.com").contains(".pasica"))
    trdi("izjema #@# odstrani selektor",
        !set.cosmetic.selectorsFor("example.com").contains(".pasica"))
    trdi("splosno pravilo (brez domene) se ne shrani",
        set.cosmetic.selectorsFor("example.com").none { it == ".splosno" })
    trdi("proceduralni selektor se preskoci",
        set.cosmetic.selectorsFor("tretji.com").isEmpty())
    trdi("tuja domena ne dobi nicesar",
        set.cosmetic.selectorsFor("nekaj-drugega.si").isEmpty())
    // set.size steje omrezna pravila, set.cosmetic.size pa kozmeticne vrstice, ki smo jih
    // sprejeli: .oglas, .pasica in izjema #@#.pasica. Splosno in proceduralno sta zavrzena.
    trdi("omrezno pravilo ostane omrezno",
        set.size == 1)
    trdi("sprejeta so tri kozmeticna pravila, dve zavrzeni",
        set.cosmetic.size == 3)
}

private fun preizkusOmrezje() {
    println("omrezna pravila (blokiranje zahtevkov)")
    val set = FilterListEngine.compile(listOf(
        "||oglasi.example.net^",
        "@@||oglasi.example.net/dovoljeno.js",
        "/reklama-banner.",
    ))

    fun blokira(url: String, stran: String?) =
        set.decide(FilterRequest(url, stran, ResourceType.SCRIPT))?.block == true

    trdi("blokira oglasno domeno",
        blokira("https://oglasi.example.net/a.js", "https://stran.si/"))
    trdi("blokira poddomeno oglasne domene",
        blokira("https://cdn.oglasi.example.net/a.js", "https://stran.si/"))
    trdi("izjema @@ prevlada",
        !blokira("https://oglasi.example.net/dovoljeno.js", "https://stran.si/"))
    trdi("vzorec v poti se ujame",
        blokira("https://stran.si/img/reklama-banner.png", "https://stran.si/"))
    trdi("navadna datoteka ni blokirana",
        !blokira("https://stran.si/img/logo.png", "https://stran.si/"))
}

fun main() {
    preizkusKozmetika()
    preizkusOmrezje()
    println()
    if (padlo > 0) {
        println("PADLO: $padlo preizkusov")
        kotlin.system.exitProcess(1)
    }
    println("Vse v redu.")
}
