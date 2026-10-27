package ch.akh.kompass.finance

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Monatsbudget mit Töpfen. Jeder Lohn wird auf Töpfe verteilt; Ausgaben zählen über ihre
 * Kategorie zu genau einem Topf. Ein "Monat" läuft von Zahltag zu Zahltag.
 */
enum class PotKind(val label: String, val collects: Boolean) {
    FIX("Fixkosten", false),
    ALLTAG("Alltag", false),
    WUNSCH("Wunsch-Topf", true),
    SPAREN("Sparkonto", true),
    STEUERN("Steuern", true),
    ETF("ETF-Sparplan", false),
    BTC("Bitcoin-Sparplan", false),
}

data class Pot(
    val id: String,
    val name: String,
    val amount: Double,
    val kind: PotKind,
    val categories: List<String>,
    val note: String = "",
)

data class BudgetSetup(
    val income: Double,
    /** Tag im Monat, an dem der Lohn kommt (1–28; 31 = letzter Tag). */
    val payday: Int,
    /** Erster Zahltag mit dem neuen Budget. */
    val start: String,
    val pots: List<Pot>,
    /** Wann zuletzt eine höhere Sparrate angeboten oder angenommen wurde. */
    val lastRaise: String? = null,
) {
    val total: Double get() = pots.sumOf { it.amount }
    fun potOf(category: String): Pot? = pots.firstOrNull { category in it.categories }

    fun toJson(): String = JSONObject()
        .put("income", income).put("payday", payday).put("start", start).put("lastRaise", lastRaise ?: "")
        .put("pots", JSONArray(pots.map {
            JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("kind", it.kind.name)
                .put("cats", JSONArray(it.categories)).put("note", it.note)
        })).toString()

    companion object {
        fun fromJson(s: String): BudgetSetup {
            val o = JSONObject(s)
            val arr = o.getJSONArray("pots")
            return BudgetSetup(
                o.getDouble("income"), o.getInt("payday"), o.getString("start"),
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONObject(i)
                    val c = p.getJSONArray("cats")
                    Pot(p.getString("id"), p.getString("name"), p.getDouble("amount"),
                        runCatching { PotKind.valueOf(p.getString("kind")) }.getOrDefault(PotKind.ALLTAG),
                        (0 until c.length()).map { c.getString(it) }, p.optString("note"))
                },
                o.optString("lastRaise").ifBlank { null },
            )
        }

        /** Akhs Plan vom 27.10.2026: 2'300 netto. */
        fun akh(today: LocalDate, payday: Int = 25): BudgetSetup {
            val start = Cycle.nextPayday(payday, today)
            return BudgetSetup(
                2300.0, payday, start.toString(),
                listOf(
                    Pot("fix", "Fixkosten", 540.0, PotKind.FIX,
                        listOf("Wohnen", "Handy und Internet", "Gesundheit", "Gym und Sport", "Transport", "Gaming und Abos"),
                        "GA, Miete, Krankenkasse, Handy, Gym, Claude"),
                    Pot("steuern", "Steuern", 100.0, PotKind.STEUERN, listOf("Steuern"), "Reserve für die Steuerrechnung"),
                    Pot("sparen", "Sparkonto", 450.0, PotKind.SPAREN, listOf("Sparen"), "Notgroschen, später Auszug"),
                    Pot("etf", "MSCI World", 100.0, PotKind.ETF, listOf("Anlegen"), "Sparplan"),
                    Pot("btc", "Bitcoin", 50.0, PotKind.BTC, emptyList(), "Sparplan auf die Ledger"),
                    Pot("wunsch", "Wunsch-Topf", 200.0, PotKind.WUNSCH, listOf("Wünsche"), "Flüge, Konzerte, Gadgets"),
                    Pot("alltag", "Alltag & Freizeit", 860.0, PotKind.ALLTAG,
                        listOf("Lebensmittel", "Auswärts essen", "Kleidung", "Freizeit", "Snus und Kiosk", "Bücher", "Sonstiges"),
                        "Essen, Ausgang, Kleider, Kleinkram"),
                ),
            )
        }
    }
}

object Cycle {
    private fun paydayIn(year: Int, month: Int, payday: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return first.withDayOfMonth(minOf(payday, first.lengthOfMonth()))
    }

    /** Beginn des laufenden Budget-Monats (letzter Zahltag, heute eingeschlossen). */
    fun start(payday: Int, today: LocalDate): LocalDate {
        val thisMonth = paydayIn(today.year, today.monthValue, payday)
        if (!today.isBefore(thisMonth)) return thisMonth
        val prev = today.minusMonths(1)
        return paydayIn(prev.year, prev.monthValue, payday)
    }

    /** Erster Tag NACH dem laufenden Budget-Monat. */
    fun end(payday: Int, today: LocalDate): LocalDate {
        val s = start(payday, today)
        val n = s.plusMonths(1)
        return paydayIn(n.year, n.monthValue, payday)
    }

    fun nextPayday(payday: Int, today: LocalDate): LocalDate {
        val s = start(payday, today)
        return if (s == today) today else end(payday, today)
    }

    /** Wie viele Lohn-Monate seit dem Start begonnen haben (inkl. dem laufenden). */
    fun count(payday: Int, start: LocalDate, today: LocalDate): Int {
        if (today.isBefore(start)) return 0
        var n = 0
        var d = start
        while (!d.isAfter(today) && n < 1200) {
            n++
            val next = d.plusMonths(1)
            d = paydayIn(next.year, next.monthValue, payday)
        }
        return n
    }
}

/** Eine Buchung, reduziert auf das, was die Töpfe brauchen. */
data class Spend(val date: String, val amount: Double, val category: String)

data class PotState(
    val pot: Pot,
    /** In diesem Lohn-Monat ausgegeben (bei Spar-/Anlage-Töpfen: eingezahlt bzw. gekauft). */
    val thisCycle: Double,
    /** Nur bei sammelnden Töpfen: was laut Plan drin sein sollte, minus Entnahmen. */
    val balance: Double?,
) {
    val remaining: Double get() = pot.amount - thisCycle
    val isSaving: Boolean get() = pot.kind in setOf(PotKind.SPAREN, PotKind.ETF, PotKind.BTC)
    val over: Boolean get() = !isSaving && thisCycle > pot.amount + 0.5
    val done: Boolean get() = isSaving && thisCycle >= pot.amount - 0.5
}

object PotMath {
    /**
     * @param spends alle Buchungen seit dem Budget-Start (Ausgaben negativ)
     * @param invested Käufe aus dem Portfolio pro Topfart (BTC/ETF), als (Datum, CHF)
     */
    fun states(
        setup: BudgetSetup,
        spends: List<Spend>,
        invested: Map<PotKind, List<Pair<String, Double>>>,
        today: LocalDate,
    ): List<PotState> {
        val start = LocalDate.parse(setup.start)
        val active = !today.isBefore(start)
        val cs = if (active) Cycle.start(setup.payday, today) else start
        val ce = if (active) Cycle.end(setup.payday, today) else start
        val cycles = Cycle.count(setup.payday, start, today)
        fun inCycle(d: String) = active && d >= cs.toString() && d < ce.toString()
        fun sinceStart(d: String) = d >= start.toString()
        return setup.pots.map { pot ->
            val out = spends.filter { it.amount < 0 && it.category in pot.categories }
            val cycleSpent = out.filter { inCycle(it.date) }.sumOf { -it.amount }
            val thisCycle = when (pot.kind) {
                PotKind.ETF, PotKind.BTC -> {
                    val buys = invested[pot.kind].orEmpty().filter { inCycle(it.first) }.sumOf { it.second }
                    maxOf(buys, cycleSpent)
                }
                else -> cycleSpent
            }
            val balance = if (pot.kind.collects) {
                val used = out.filter { sinceStart(it.date) }.sumOf { -it.amount }
                when (pot.kind) {
                    // Sparkonto: was bisher überwiesen wurde.
                    PotKind.SPAREN -> used
                    // Wunsch und Steuern: Monatsbeitrag sammelt sich, Ausgaben gehen ab.
                    else -> cycles * pot.amount - used
                }
            } else null
            PotState(pot, thisCycle, balance)
        }
    }

    /** Ausgaben in Kategorien, die zu keinem Topf gehören (ohne Einnahmen). */
    fun unassigned(setup: BudgetSetup, spends: List<Spend>, from: String, to: String): Map<String, Double> =
        spends.filter { it.amount < 0 && it.date >= from && it.date < to && setup.potOf(it.category) == null }
            .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { -it.amount } }

    /** Alltagsgeld pro Woche (Jahresbetrag / 52). */
    fun weekly(monthly: Double) = monthly * 12 / 52

    /**
     * Nach 3 vollen Lohn-Monaten im Plan (Alltag nie überzogen) eine höhere Sparrate anbieten.
     * Gibt die Anzahl guter Monate zurück, wenn ein Angebot fällig ist, sonst null.
     */
    fun raiseDue(setup: BudgetSetup, spends: List<Spend>, today: LocalDate): Int? {
        val start = LocalDate.parse(setup.lastRaise ?: setup.start)
        val alltag = setup.pots.filter { it.kind == PotKind.ALLTAG }
        if (alltag.isEmpty()) return null
        val current = Cycle.start(setup.payday, today)
        var d = if (setup.lastRaise != null) Cycle.end(setup.payday, start) else start
        var good = 0
        while (d.isBefore(current)) {
            val e = Cycle.end(setup.payday, d)
            val ok = alltag.all { p ->
                spends.filter { it.amount < 0 && it.category in p.categories && it.date >= d.toString() && it.date < e.toString() }
                    .sumOf { -it.amount } <= p.amount
            }
            good = if (ok) good + 1 else 0
            d = e
        }
        return if (good >= 3) good else null
    }

    /** Hochrechnung der Ausgaben bis zum nächsten Lohn (gleiches Tempo wie bisher). */
    fun projection(setup: BudgetSetup, spent: Double, today: LocalDate): Double {
        val s = Cycle.start(setup.payday, today)
        val e = Cycle.end(setup.payday, today)
        val total = ChronoUnit.DAYS.between(s, e).toDouble()
        val done = (ChronoUnit.DAYS.between(s, today) + 1).toDouble().coerceAtLeast(1.0)
        return spent / done * total
    }

    /**
     * Kurze Soll/Ist-Zusammenfassung für die KI. Nur Beträge und Topfnamen, keine Buchungstexte.
     */
    fun aiSummary(setup: BudgetSetup, states: List<PotState>, unassigned: Map<String, Double>, today: LocalDate): List<String> {
        fun f(x: Double) = "%.0f".format(x)
        val start = LocalDate.parse(setup.start)
        if (today.isBefore(start)) {
            return listOf("Budget-Töpfe starten mit dem Lohn am $start (Lohn ${f(setup.income)}). Bis dahin zählt nichts.") +
                setup.pots.map { "Topf ${it.name}: ${f(it.amount)} pro Monat" }
        }
        val cs = Cycle.start(setup.payday, today)
        val ce = Cycle.end(setup.payday, today)
        val day = ChronoUnit.DAYS.between(cs, today) + 1
        val len = ChronoUnit.DAYS.between(cs, ce)
        val out = ArrayList<String>()
        out += "Budget-Töpfe, Lohn-Monat $cs bis ${ce.minusDays(1)} (Tag $day von $len), Lohn ${f(setup.income)}, Soll gegen Ist:"
        for (st in states) {
            val p = st.pot
            out += when (p.kind) {
                PotKind.FIX, PotKind.ALLTAG ->
                    "${p.name}: Soll ${f(p.amount)}, Ist ${f(st.thisCycle)}, Hochrechnung ${f(projection(setup, st.thisCycle, today))}" +
                        if (st.over) " (überzogen)" else ""
                PotKind.WUNSCH -> "${p.name}: +${f(p.amount)} pro Monat, aktuell im Topf ${f(st.balance ?: 0.0)}, diesen Monat ausgegeben ${f(st.thisCycle)}"
                PotKind.STEUERN -> "${p.name}: Reserve ${f(st.balance ?: 0.0)} (${f(p.amount)} pro Monat)"
                PotKind.SPAREN -> "${p.name}: Soll ${f(p.amount)}, überwiesen ${f(st.thisCycle)}" + if (st.done) " (erledigt)" else ""
                PotKind.ETF, PotKind.BTC -> "${p.name}-Sparplan: Soll ${f(p.amount)}, gekauft ${f(st.thisCycle)}" + if (st.done) " (erledigt)" else ""
            }
        }
        if (unassigned.isNotEmpty()) out += "Ohne Topf: " + unassigned.entries.joinToString(", ") { "${it.key} ${f(it.value)}" }
        return out
    }

    fun daysLeft(setup: BudgetSetup, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, Cycle.end(setup.payday, today)).toInt()
}
