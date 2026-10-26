package ch.akh.kompass.util

import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import ch.akh.kompass.data.AppDatabase
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.data.Tx
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ======================= Kalender =======================

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

object Kalender {
    fun eventsOn(context: Context, d: LocalDate): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            android.content.ContentUris.appendId(it, Time.startOfDay(d))
            android.content.ContentUris.appendId(it, Time.endOfDay(d))
            it.build()
        }
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                ),
                null, null, CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalEvent(c.getString(0) ?: "(ohne Titel)", c.getLong(1), c.getLong(2), c.getInt(3) == 1)
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }
}

// ======================= CSV-Import =======================

data class CsvMapping(
    val headerRow: Int,
    val dateCol: Int,
    val textCol: Int,
    val amountCol: Int,
    val debitCol: Int,
    val creditCol: Int,
) {
    fun encode() = listOf(headerRow, dateCol, textCol, amountCol, debitCol, creditCol).joinToString(",")

    companion object {
        fun decode(s: String): CsvMapping? = s.split(',').mapNotNull { it.toIntOrNull() }.let {
            if (it.size == 6) CsvMapping(it[0], it[1], it[2], it[3], it[4], it[5]) else null
        }
    }
}

object Csv {
    val categories = listOf(
        "Lebensmittel", "Auswärts essen", "Snus und Kiosk", "Gaming und Abos", "Transport",
        "Kleidung", "Gym und Sport", "Wohnen", "Handy und Internet", "Gesundheit", "Freizeit",
        "Bücher", "Sparen", "Sonstiges", "Einnahmen",
    )

    private val rules = listOf(
        "Lebensmittel" to listOf("migros", "coop", "denner", "lidl", "aldi", "volg", "spar", "manor food", "landi"),
        "Auswärts essen" to listOf("mcdonald", "burger king", "kebab", "restaurant", "pizza", "subway", "starbucks", "uber eats", "just eat", "eat.ch", "smood", "dieci", "kfc", "cafe", "bar "),
        "Snus und Kiosk" to listOf("kiosk", "k kiosk", "valora", "avec", "press & books", "tabak", "snus"),
        "Gaming und Abos" to listOf("steam", "playstation", "xbox", "nintendo", "epic games", "netflix", "spotify", "disney", "youtube", "apple.com", "google play", "twitch", "riot"),
        "Transport" to listOf("sbb", "postauto", "zvv", "bls", "tpg", "uber", "tankstelle", "shell", "avia", "socar", "parking", "mobility"),
        "Kleidung" to listOf("zalando", "h&m", "zara", "about you", "nike", "adidas", "snipes", "c&a", "bershka"),
        "Gym und Sport" to listOf("fitness", "gym", "update", "migros fitness", "activ fitness", "decathlon", "ochsner", "intersport", "hallenbad", "badi"),
        "Handy und Internet" to listOf("swisscom", "salt", "sunrise", "yallo", "wingo", "digital republic"),
        "Gesundheit" to listOf("apotheke", "pharmacie", "amavita", "sunstore", "arzt", "css", "helsana", "sanitas", "swica", "visana", "concordia", "assura", "kpt"),
        "Bücher" to listOf("orell", "thalia", "exlibris", "ex libris", "buch"),
        "Wohnen" to listOf("miete", "ikea", "jumbo", "hornbach", "ewz", "bkw"),
        "Sparen" to listOf("sparkonto", "säule 3a", "3a", "viac", "frankly", "finpension"),
    )

    fun guessCategory(text: String, amount: Double): String {
        if (amount > 0) return "Einnahmen"
        val t = text.lowercase()
        for ((cat, keys) in rules) if (keys.any { t.contains(it) }) return cat
        return "Sonstiges"
    }

    fun parse(content: String): List<List<String>> {
        val lines = content.replace("﻿", "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sample = lines.take(10).joinToString("\n")
        val delim = listOf(';', ',', '\t').maxByOrNull { d -> sample.count { it == d } } ?: ';'
        return lines.map { splitLine(it, delim) }
    }

    private fun splitLine(line: String, d: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == d && !q -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    fun guessMapping(rows: List<List<String>>): CsvMapping {
        var header = 0
        for ((i, r) in rows.take(30).withIndex()) {
            if (r.any { c -> c.lowercase().let { it.contains("datum") || it == "date" || it.contains("booking date") } }) { header = i; break }
        }
        val h = rows.getOrNull(header)?.map { it.lowercase() } ?: emptyList()
        fun find(vararg keys: String) = h.indexOfFirst { c -> keys.any { c.contains(it) } }
        return CsvMapping(
            headerRow = header,
            dateCol = find("buchungsdatum", "datum", "date").coerceAtLeast(0),
            textCol = find("buchungstext", "beschreibung", "text", "description", "details", "mitteilung", "empfänger", "zahlungsempfänger"),
            amountCol = find("betrag", "amount"),
            debitCol = find("belastung", "lastschrift", "debit", "soll"),
            creditCol = find("gutschrift", "credit", "haben"),
        )
    }

    private val dateFormats = listOf("dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(s: String): LocalDate? {
        val t = s.trim().take(10).trim()
        for (f in dateFormats) {
            try {
                return LocalDate.parse(t, f)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun parseAmount(s: String): Double? {
        var t = s.replace("CHF", "").replace("'", "").replace("’", "").replace(" ", "").replace(" ", "").trim()
        if (t.isEmpty()) return null
        val neg = t.startsWith("-") || t.endsWith("-") || (t.startsWith("(") && t.endsWith(")"))
        t = t.trim('-', '+', '(', ')')
        if (t.contains(',') && t.contains('.')) {
            t = if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        } else if (t.contains(',')) {
            t = t.replace(',', '.')
        }
        val v = t.toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    fun toTx(rows: List<List<String>>, m: CsvMapping): List<Tx> {
        val seen = HashMap<String, Int>()
        val out = mutableListOf<Tx>()
        for (r in rows.drop(m.headerRow + 1)) {
            val date = parseDate(r.getOrNull(m.dateCol) ?: continue) ?: continue
            val amount: Double = if (m.amountCol >= 0) {
                parseAmount(r.getOrNull(m.amountCol) ?: "") ?: continue
            } else {
                val debit = if (m.debitCol >= 0) parseAmount(r.getOrNull(m.debitCol) ?: "") else null
                val credit = if (m.creditCol >= 0) parseAmount(r.getOrNull(m.creditCol) ?: "") else null
                when {
                    debit != null && debit != 0.0 -> -kotlin.math.abs(debit)
                    credit != null -> kotlin.math.abs(credit)
                    else -> continue
                }
            }
            val text = (if (m.textCol >= 0) r.getOrNull(m.textCol) else null)?.ifBlank { null }
                ?: r.filterIndexed { i, _ -> i != m.dateCol && i != m.amountCol }.joinToString(" ").take(80)
            val base = "$date|$amount|$text"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            out += Tx(date = date.toString(), amount = amount, category = guessCategory(text, amount), text = text.take(120), source = "CSV", hash = "$base|$n")
        }
        return out
    }
}

// ======================= Backup =======================

object Backup {
    fun export(context: Context, uri: Uri) {
        val db = AppDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry("kompass.db"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("prefs.json"))
                zip.write(prefsJson(context).toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun prefsJson(context: Context): String {
        val o = JSONObject()
        for ((k, v) in Prefs.get(context).sp.all) {
            val e = JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is String -> e.put("t", "s").put("v", v)
                is Set<*> -> e.put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> continue
            }
            o.put(k, e)
        }
        return o.toString()
    }

    /** Ersetzt alle Daten. Danach muss die App neu starten. */
    fun import(context: Context, uri: Uri) {
        var dbBytes: ByteArray? = null
        var prefs: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    when (e.name) {
                        "kompass.db" -> dbBytes = zip.readBytes()
                        "prefs.json" -> prefs = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    e = zip.nextEntry
                }
            }
        }
        val bytes = dbBytes ?: error("Keine Kompass-Sicherung")
        AppDatabase.close()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        dbFile.writeBytes(bytes)
        prefs?.let { json ->
            val o = JSONObject(json)
            val ed = Prefs.get(context).sp.edit().clear()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "set" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
        }
    }
}
