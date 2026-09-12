/*
 * Runs clients/banks/cases.json against the Kotlin BankGuard (the Python tests run the same file).
 *   java -cp bank-guard-tests.jar com.safeer.threatfeed.BankGuardTestKt clients/banks/cases.json
 */
package com.safeer.threatfeed

import java.io.File
import kotlin.system.exitProcess

private var failures = 0
private var passed = 0

private fun check(name: String, condition: Boolean) {
    if (condition) passed++ else {
        failures++
        println("FAIL: $name")
    }
}

private fun summary(verdict: BankVerdict?): Map<String, String>? =
    verdict?.let { mapOf("bank" to it.bankId, "reason" to it.reason) }

@Suppress("UNCHECKED_CAST")
fun main(args: Array<String>) {
    val cases = LenientJson.parse(File(args.getOrElse(0) { "clients/banks/cases.json" }).readText(Charsets.UTF_8)) as Map<String, Any?>
    for (case in cases["hosts"] as List<Map<String, Any?>>) {
        val host = case["host"] as String
        val expected = case["expect"] as Map<String, String>?
        val actual = summary(BankGuard.hostVerdict(host))
        check("host $host expected $expected got $actual", actual == expected)
    }
    for (case in cases["pages"] as List<Map<String, Any?>>) {
        val s = case["signals"] as Map<String, Any?>
        val signals = PageSignals(
            host = s["host"] as String, scheme = s["scheme"] as? String ?: "https",
            password = s["password"] == true, otp = s["otp"] == true, card = s["card"] == true, taxid = s["taxid"] == true, pin = s["pin"] == true,
            title = s["title"] as? String ?: "", site = s["site"] as? String ?: "", headings = s["headings"] as? String ?: "",
            logos = s["logos"] as? String ?: "", text = s["text"] as? String ?: "", article = s["article"] == true,
        )
        val expected = case["expect"] as Map<String, String>?
        val actual = summary(BankGuard.pageVerdict(signals))
        check("page '${case["name"]}' expected $expected got $actual", actual == expected)
        // The same signals as WebView would hand them back (JSON with escapes) must give the same verdict.
        val json = "{" + s.entries.joinToString(",") { (k, v) ->
            "\"$k\":" + if (v is String) "\"" + v.map { c -> if (c.code > 0x7e || c == '"' || c == '\\') "\\u%04x".format(c.code) else c.toString() }.joinToString("") + "\"" else v.toString()
        } + "}"
        val parsed = BankGuard.signalsFromJson(json)
        check("page '${case["name"]}' via JSON", summary(BankGuard.pageVerdict(parsed)) == expected && parsed?.title == signals.title)
    }
    check("official subdomain", BankGuard.officialBank("klik.nlb.si")?.id == "nlb")
    check("suffix trick is not official", BankGuard.officialBank("nlb.si.evil.example") == null)
    check("group and infrastructure trusted", BankGuard.isTrusted("eklik.nlb-rs.ba") && BankGuard.isTrusted("3ds.bankart.si"))
    check("suffix trick is not trusted", !BankGuard.isTrusted("nlb-rs.ba.evil.example"))
    check("fold", BankGuard.fold("Deželna ŠTEVILKA č") == "dezelna stevilka c")
    check("damerau", BankGuard.damerauOne("otpbanka", "otpbamka") && BankGuard.damerauOne("paypal", "paypall") && !BankGuard.damerauOne("abc", "xyz"))
    check("page script present", BankGuard.PAGE_SCRIPT.contains("one-time-code"))
    check("non-object JSON", BankGuard.signalsFromJson("null") == null && BankGuard.signalsFromJson("\"x\"") == null)
    check("malformed JSON", BankGuard.signalsFromJson("{\"host\":") == null && BankGuard.signalsFromJson("{".repeat(100)) == null)
    check("oversized JSON", BankGuard.signalsFromJson("{\"text\":\"" + "a".repeat(70_000) + "\"}") == null)
    check("clipped text", BankGuard.signalsFromJson("{\"password\":true,\"title\":\"" + "b".repeat(5000) + "\"}")?.title?.length == 200)
    println("BankGuard Kotlin: $passed passed, $failures failed")
    exitProcess(if (failures == 0) 0 else 1)
}
