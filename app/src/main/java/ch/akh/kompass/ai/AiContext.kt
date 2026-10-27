package ch.akh.kompass.ai

import android.content.Context
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.util.Health
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Baut aus den lokalen Daten eine kurze, sachliche Zusammenfassung für Claude.
 *
 * Datensparsam: nur die Bereiche, die eine Funktion braucht UND die freigegeben sind,
 * nur Zahlen und Zeiten der letzten Tage. Kein Name, keine Buchungstexte, Notizen nur mit
 * ausdrücklicher Freigabe. Kurz heisst auch: günstig.
 */
object AiContext {
    suspend fun build(context: Context, areas: Set<AiArea>): String {
        val store = AiStore.get(context)
        val allowed = areas.filter { store.shares(it) }.toSet()
        val e = Engine.get(context)
        val dao = e.dao
        val p = e.prefs
        val today = Time.today()
        val now = System.currentTimeMillis()
        val out = StringBuilder()
        fun line(s: String) { out.append("- ").append(s).append('\n') }

        out.append("Heute: ${Time.weekday(today)} ${today}, jetzt ${Time.hm(now)} Uhr.\n")

        if (AiArea.HABITS in allowed) {
            val ph = PhasePlan.phase(p.phase)
            val st = runCatching { e.phaseStatus(today) }.getOrNull()
            out.append("Plan:\n")
            line("Phase ${ph.index} \"${ph.name}\", Tag ${st?.daysIn ?: "?"}. Ziel: ${ph.goal}")
            st?.criteria?.forEach { line("Meilenstein: ${it.text}: ${if (it.met) "erfüllt" else "offen"} (${it.detail})") }
            val items = runCatching { e.activeItemsOnce() }.getOrDefault(emptyList())
            val logs = dao.habitsSince(today.minusDays(6).toString())
            val todayLogs = logs.filter { it.date == today.toString() }.associate { it.key to it.level }
            if (items.isNotEmpty()) {
                line("Gewohnheiten (letzte 7 Tage erledigt / Ziel pro Woche, heute):")
                items.forEach { h ->
                    val n = logs.count { it.key == h.key && it.level >= 1 }
                    val t = when (todayLogs[h.key]) { 2 -> "voll"; 1 -> "mini"; else -> "offen" }
                    out.append("  · ${h.title}: $n/${h.perWeek}, heute $t\n")
                }
            }
        }

        if (AiArea.SLEEP in allowed) {
            out.append("Schlaf (Ziel aufstehen ${Time.hm(p.currentWake)}, Endziel ${Time.hm(p.goalWake)}, Bedarf ${Time.dur(p.sleepNeedMinutes)}, Licht aus ${Time.hm(e.lightsOut())}):\n")
            val nights = dao.sleepSince(Time.calToday().minusDays(6).toString())
            if (nights.isEmpty()) line("keine Nächte eingetragen")
            nights.forEach { s ->
                val bed = s.bedAt?.let { Time.hm(it) } ?: "?"
                val wake = s.wakeAt?.let { Time.hm(it) } ?: "?"
                val dur = if (s.bedAt != null && s.wakeAt != null) Time.durMs(s.wakeAt - s.bedAt) else "?"
                line("${s.wakeDate}: Bett $bed, wach $wake, $dur" + if (s.quality > 0) ", Qualität ${s.quality}/5" else "")
            }
            if (e.sleeping()) line("Gerade im Schlafmodus (Gute Nacht gedrückt).")
        }

        if (AiArea.NICOTINE in allowed) {
            val perDay = dao.snusTimesOnce(Time.startOfDay(today.minusDays(6))).groupingBy { Time.dayOf(it).toString() }.eachCount()
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            out.append("Snus (Beutel pro Tag, ältester zuerst): ${days.joinToString(", ") { "${perDay[it.toString()] ?: 0}" }}\n")
            line("Ausgangswert ${p.snusBaseline}/Tag, Limit heute: ${e.snusLimit(today)?.toString() ?: "keins (nur zählen)"}, Abbau ab ${p.taperStart}, rauchfrei-Ziel ${p.quitDate}")
            dao.lastPouch()?.let { line("Letzter Beutel vor ${Time.durMs(now - it)}") }
            val cravings = dao.snusCount(1, Time.startOfDay(today.minusDays(6)), now)
            if (cravings > 0) line("$cravings Cravings in 7 Tagen überstanden")
            val subs = dao.substancesBetween(Time.startOfDay(today.minusDays(6)), now)
            if (subs.isNotEmpty()) line("Konsum 7 Tage: " + subs.groupBy { it.type }.map { (k, v) -> "$k ${v.sumOf { it.amount }}" }.joinToString(", "))
        }

        if (AiArea.TRAINING in allowed) {
            out.append("Training:\n")
            val gyms = dao.workoutsBetween(Time.startOfDay(today.minusDays(13)), now)
            line("Gym-Einheiten in 14 Tagen: ${gyms.size}" + (gyms.maxByOrNull { it.startedAt }?.let { ", zuletzt ${it.template} am ${Time.dayOf(it.startedAt)}" } ?: ""))
            val cardio = dao.cardioSince(today.minusDays(6).toString())
            if (cardio.isNotEmpty()) line("Ausdauer 7 Tage: " + cardio.joinToString(", ") { "${it.type} ${it.minutes} Min" })
            runCatching { Health.stepsOn(context, today) }.getOrNull()?.let { line("Schritte heute: $it (Ziel ${p.stepGoal})") }
        }

        if (AiArea.NUTRITION in allowed) {
            val days = (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
            val perDay = days.map { d -> d to Meals.on(context, d) }.filter { it.second.isNotEmpty() }
            if (perDay.isNotEmpty()) {
                out.append("Essen (Ziel ${Meals.kcalGoal(context)} kcal):\n")
                perDay.forEach { (d, l) -> line("$d: ${l.sumOf { it.kcal }} kcal, ${l.sumOf { it.protein }} g Protein (${l.joinToString(", ") { it.name }.take(120)})") }
            }
        }

        if (AiArea.BOOKS in allowed) {
            val books = dao.books().first()
            if (books.isNotEmpty()) {
                out.append("Bücher:\n")
                books.take(25).forEach { b ->
                    val st = when (b.status) { 2 -> "gelesen" + if (b.rating > 0) ", ${b.rating}/5" else ""; 1 -> "lese gerade, Seite ${b.current}/${b.pages}"; else -> "will lesen" }
                    line("${b.title}${if (b.author.isNotBlank()) " (${b.author})" else ""}: $st")
                }
            }
        }

        if (AiArea.MOOD in allowed) {
            val moods = dao.moodSinceOnce(today.minusDays(6).toString())
            if (moods.isNotEmpty()) {
                out.append("Stimmung 1-5 (Stimmung/Energie/Freude):\n")
                moods.forEach { m -> line("${m.date}: ${m.mood}/${m.energy}/${m.joy}" + if (m.social) ", unter Leuten" else "") }
            }
        }

        if (AiArea.NOTES in allowed) {
            val notes = mutableListOf<String>()
            p.intention(today.toString()).takeIf { it.isNotBlank() }?.let { notes += "Vorsatz heute: $it" }
            dao.moodOnce(today.toString())?.note?.takeIf { it.isNotBlank() }?.let { notes += "Notiz heute: $it" }
            dao.reviews().first().firstOrNull()?.let { r ->
                notes += "Letzter Wochenrückblick (${r.weekStart}): Erfolg \"${r.win}\", Lektion \"${r.lesson}\", Fokus \"${r.nextFocus}\""
            }
            if (notes.isNotEmpty()) {
                out.append("Eigene Notizen:\n")
                notes.forEach { line(it.take(300)) }
            }
        }

        if (AiArea.SCREEN in allowed && ScreenTime.hasPermission(context)) {
            runCatching {
                val d = ScreenTime.day(context, today)
                out.append("Bildschirmzeit heute: ${Time.durMs(d.total)}, davon Unterhaltung ${Time.durMs(d.entertainment)} (Budget ${Time.dur(p.entertainmentBudget)})\n")
                if (d.apps.isNotEmpty()) line("Top-Apps: " + d.apps.sortedByDescending { it.ms }.take(3).joinToString(", ") { "${it.label} ${Time.durMs(it.ms)}" })
                val late = ScreenTime.lateLastNight(context, today)
                if (late > 0) line("Nach Licht aus gestern noch ${Time.durMs(late)} am Handy")
            }
        }

        if (AiArea.MONEY in allowed) {
            val cal = Time.calToday()
            val from = cal.withDayOfMonth(1)
            val sums = dao.categorySums(from.toString(), from.plusMonths(1).toString()).first()
            val budgets = dao.budgets().first().associate { it.category to it.monthly }
            out.append("Finanzen ${from.month.value}/${from.year} (Tag ${cal.dayOfMonth} von ${from.lengthOfMonth()}), CHF:\n")
            if (p.monthlyIncome > 0) line("Einkommen ${p.monthlyIncome.toInt()}, Sparziel ${p.savingsGoal.toInt()}")
            sums.forEach { s -> line("${s.category}: ${"%.0f".format(s.total)}" + (budgets[s.category]?.let { " (Budget ${"%.0f".format(it)})" } ?: "")) }
            line("Durch weniger Snus gespart: ${"%.0f".format(e.moneySaved(today))}")
            // Budget-Töpfe: Soll gegen Ist aus den echten Buchungen und den Käufen im Portfolio.
            ch.akh.kompass.finance.BudgetStore.get(context).setup.value?.let { bs ->
                val cs = ch.akh.kompass.finance.Cycle.start(bs.payday, cal)
                val ce = ch.akh.kompass.finance.Cycle.end(bs.payday, cal)
                val since = minOf(bs.start, cs.toString())
                val spends = dao.txBetween(since, cal.plusDays(1).toString()).first()
                    .map { ch.akh.kompass.finance.Spend(it.date, it.amount, it.category) }
                val pd = ch.akh.kompass.portfolio.PortfolioStore.get(context).data.value
                val btcId = pd.assets.firstOrNull { it.kind == "btc" }?.id
                val etfIds = pd.assets.filter { it.kind == "etf" }.map { it.id }.toSet()
                val invested = mapOf(
                    ch.akh.kompass.finance.PotKind.BTC to pd.trades.filter { it.assetId == btcId && it.qty > 0 }.map { it.date to it.chf },
                    ch.akh.kompass.finance.PotKind.ETF to pd.trades.filter { it.assetId in etfIds && it.qty > 0 }.map { it.date to it.chf },
                )
                val states = ch.akh.kompass.finance.PotMath.states(bs, spends, invested, cal)
                val un = ch.akh.kompass.finance.PotMath.unassigned(bs, spends, cs.toString(), ce.toString())
                ch.akh.kompass.finance.PotMath.aiSummary(bs, states, un, cal).forEachIndexed { i, s ->
                    if (i == 0) out.append(s).append('\n') else line(s)
                }
            }
        }

        val daysLeft = ChronoUnit.DAYS.between(Time.calToday(), LocalDate.of(2027, 8, 2))
        out.append("Noch $daysLeft Tage bis zum Militär (August 2027).\n")
        return out.toString()
    }
}
