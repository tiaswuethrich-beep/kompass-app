package ch.akh.kompass.ai

import android.content.Context
import ch.akh.kompass.data.AppDatabase
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.data.Tx
import ch.akh.kompass.util.Csv
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Ordnet Buchungen unter "Sonstiges" einer Kategorie zu.
 *
 * Sparsam und privat: Zuerst greifen gelernte Regeln (kostenlos, offline). Nur was dann noch
 * offen ist, geht an Claude Haiku, und zwar nur der Händlername ohne Ziffern (keine IBAN,
 * keine Kartennummer, kein Betrag). Jede Antwort wird als Regel gespeichert, damit derselbe
 * Händler nie wieder angefragt werden muss.
 */
object FinanceAi {
    data class Outcome(val byRule: Int, val byAi: Int, val left: Int, val costUsd: Double, val error: String? = null)

    /** Händler-Schlüssel: Kleinbuchstaben, ohne Ziffern und Sonderzeichen, erste drei Wörter. */
    fun merchant(text: String): String =
        text.lowercase().replace(Regex("[^a-zäöüéèàç ]"), " ").split(' ').filter { it.length > 1 }.take(3).joinToString(" ")

    private fun ruleKey(m: String) = "txrule_$m"

    fun rule(context: Context, text: String): String? =
        merchant(text).takeIf { it.isNotBlank() }?.let { Prefs.get(context).sp.getString(ruleKey(it), null) }

    private fun saveRule(context: Context, m: String, cat: String) {
        Prefs.get(context).sp.edit().putString(ruleKey(m), cat).apply()
    }

    suspend fun categorize(context: Context, monthsBack: Long = 3): Outcome = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).dao()
        val to = Time.calToday().plusDays(1)
        val from = to.minusMonths(monthsBack).withDayOfMonth(1)
        val open = dao.txBetween(from.toString(), to.toString()).first().filter { it.category == "Sonstiges" && it.amount < 0 }
        if (open.isEmpty()) return@withContext Outcome(0, 0, 0, 0.0)

        // 1. Gelernte Regeln
        var byRule = 0
        val rest = mutableListOf<Tx>()
        open.forEach { t ->
            val c = rule(context, t.text)
            if (c != null && c in Csv.categories && c != "Sonstiges") { dao.upsertTx(t.copy(category = c)); byRule++ } else rest += t
        }
        if (rest.isEmpty()) return@withContext Outcome(byRule, 0, 0, 0.0)

        // 2. Claude nur mit freigegebenen Händlernamen
        val store = AiStore.get(context)
        if (!store.shares(AiArea.MONEY_TEXTS)) {
            return@withContext Outcome(byRule, 0, rest.size, 0.0, "Händlernamen sind nicht freigegeben (Mehr › KI).")
        }
        val names = rest.map { merchant(it.text) }.filter { it.isNotBlank() }.distinct().take(80)
        if (names.isEmpty()) return@withContext Outcome(byRule, 0, rest.size, 0.0)
        val cats = Csv.categories.filter { it != "Einnahmen" }
        val task = "Ordne jeden Händlernamen (Schweiz) genau einer Kategorie zu: ${cats.joinToString(", ")}. " +
            "Wenn unklar: Sonstiges. Antworte NUR mit einem JSON-Objekt {\"händlername\": \"Kategorie\", ...}, ohne weiteren Text."
        when (val r = Ai.tool(context, task, AiTurn(true, names.joinToString("\n")), 60L + names.size * 18L)) {
            is AiResult.Fail -> Outcome(byRule, 0, rest.size, 0.0, r.message)
            is AiResult.Ok -> {
                val map = runCatching {
                    val o = JSONObject(r.text.substring(r.text.indexOf('{'), r.text.lastIndexOf('}') + 1))
                    o.keys().asSequence().associateWith { o.getString(it) }.filterValues { it in cats }
                }.getOrElse { return@withContext Outcome(byRule, 0, rest.size, r.costUsd, "Antwort nicht lesbar.") }
                map.forEach { (m, c) -> if (c != "Sonstiges") saveRule(context, m, c) }
                var byAi = 0
                rest.forEach { t ->
                    val c = map[merchant(t.text)]
                    if (c != null && c != "Sonstiges") { dao.upsertTx(t.copy(category = c)); byAi++ }
                }
                Prefs.get(context).bump()
                Outcome(byRule, byAi, rest.size - byAi, r.costUsd)
            }
        }
    }
}
