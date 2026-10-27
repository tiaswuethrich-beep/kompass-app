package ch.akh.kompass.bank

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Liest Kontoauszüge im Bankenstandard ISO 20022 (camt.053, camt.054, camt.052).
 * Jede Schweizer Bank bietet das im E-Banking an, oft als ZIP. Genauer als CSV,
 * weil Betrag, Richtung und Gegenpartei sauber getrennt sind.
 */
object Camt {

    data class Entry(val date: String, val amount: Double, val text: String, val ref: String)

    fun looksLike(bytes: ByteArray): Boolean {
        if (isZip(bytes)) return true
        val head = String(bytes.copyOf(minOf(bytes.size, 2000)), Charsets.UTF_8)
        return head.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("<") && head.contains("camt.05")
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    /** Akzeptiert eine XML-Datei oder ein ZIP mit mehreren XML-Dateien. */
    fun parse(bytes: ByteArray): List<Entry> {
        if (!isZip(bytes)) return parseXml(ByteArrayInputStream(bytes))
        val out = ArrayList<Entry>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".xml")) {
                    out += parseXml(ByteArrayInputStream(zip.readBytes()))
                }
                e = zip.nextEntry
            }
        }
        return out
    }

    private fun factory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Schutz gegen präparierte XML-Dateien (keine externen Verweise nachladen).
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        isExpandEntityReferences = false
    }

    private fun Element.kids(name: String): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == name) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun Element.kid(vararg path: String): Element? {
        var e: Element = this
        for (p in path) e = e.kids(p).firstOrNull() ?: return null
        return e
    }

    private fun Element.txt(vararg path: String): String? = kid(*path)?.textContent?.trim()?.ifBlank { null }

    private fun amountOf(e: Element): Double? {
        val v = e.txt("Amt")?.toDoubleOrNull() ?: return null
        return when (e.txt("CdtDbtInd")) {
            "DBIT" -> -v
            "CRDT" -> v
            else -> null
        }
    }

    private fun party(tx: Element, debit: Boolean): String? {
        val role = if (debit) "Cdtr" else "Dbtr"
        val rp = tx.kid("RltdPties") ?: return null
        return rp.txt(role, "Nm") ?: rp.txt(role, "Pty", "Nm")
    }

    fun parseXml(input: InputStream): List<Entry> {
        val doc = factory().newDocumentBuilder().parse(input)
        val nodes = doc.getElementsByTagNameNS("*", "Ntry")
        val out = ArrayList<Entry>()
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as Element
            val total = amountOf(n) ?: continue
            if (n.txt("Sts") == "PDNG" || n.txt("Sts", "Cd") == "PDNG") continue // vorgemerkt, noch nicht gebucht
            val date = (n.txt("BookgDt", "Dt") ?: n.txt("BookgDt", "DtTm") ?: n.txt("ValDt", "Dt") ?: continue).take(10)
            val ref = n.txt("AcctSvcrRef") ?: n.txt("NtryRef") ?: ""
            val general = n.txt("AddtlNtryInf")
            val details = n.kids("NtryDtls").flatMap { it.kids("TxDtls") }
            // Sammelbuchung mit eigenen Beträgen pro Zahlung: einzeln eintragen.
            val split = details.size > 1 && details.all { amountOf(it) != null || it.txt("AmtDtls", "TxAmt", "Amt") != null }
            if (split) {
                details.forEachIndexed { k, t ->
                    val a = amountOf(t) ?: (t.txt("AmtDtls", "TxAmt", "Amt")?.toDoubleOrNull()?.let { if (total < 0) -it else it }) ?: return@forEachIndexed
                    out += Entry(date, a, describe(t, a < 0, general), "$ref#$k")
                }
            } else {
                out += Entry(date, total, describe(details.firstOrNull(), total < 0, general), ref)
            }
        }
        return out
    }

    private fun describe(t: Element?, debit: Boolean, general: String?): String {
        val parts = listOfNotNull(
            t?.let { party(it, debit) },
            t?.kid("RmtInf")?.kids("Ustrd")?.joinToString(" ") { it.textContent.trim() }?.ifBlank { null },
            t?.txt("AddtlTxInf"),
        ).distinct()
        val s = if (parts.isNotEmpty()) parts.joinToString(" · ") else general ?: "Buchung"
        return s.replace(Regex("\\s+"), " ").take(120)
    }
}
