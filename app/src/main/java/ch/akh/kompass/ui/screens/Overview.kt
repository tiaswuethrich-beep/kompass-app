package ch.akh.kompass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.BarChart
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.LineChart
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.DayUsage
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ======================= Bildschirmzeit =======================

@Composable
fun ScreenTimeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    var refresh by remember { mutableIntStateOf(0) }
    val has = ScreenTime.hasPermission(ctx)
    val days = (6 downTo 0).map { Time.today().minusDays(it.toLong()) }
    val week by produceState<List<DayUsage>?>(null, refresh, prefs.extraEntertainment) {
        value = withContext(Dispatchers.IO) { if (has) days.map { ScreenTime.day(ctx, it) } else null }
    }
    val late by produceState(0L, refresh) {
        value = withContext(Dispatchers.IO) { if (has) ScreenTime.lateLastNight(ctx) else 0L }
    }

    Screen(title = "Bildschirmzeit", onBack = onBack) {
        if (!has) {
            item {
                Section(title = "Zugriff fehlt") {
                    Muted("Android zeigt Bildschirmzeit nur Apps mit \"Nutzungszugriff\". Tippe unten, such Kompass in der Liste und schalte es ein. Die Daten bleiben auf deinem Handy.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { ctx.startActivity(ScreenTime.settingsIntent()) }) { Text("Nutzungszugriff öffnen") }
                    Button(onClick = { refresh++ }) { Text("Erneut prüfen") }
                }
            }
            return@Screen
        }
        val w = week
        if (w == null) {
            item { Muted("Lade ...") }
            return@Screen
        }
        val today = w.last()
        item {
            Section(title = "Heute") {
                Row {
                    Stat("Gesamt", Time.durMs(today.total), Modifier.weight(1f))
                    Stat("Games und Videos", Time.durMs(today.entertainment), Modifier.weight(1f),
                        color = if (today.entertainment > prefs.entertainmentBudget * 60_000L) Bad else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Stat("Nach Licht aus (letzte Nacht)", Time.durMs(late), color = if (late > 15 * 60_000L) Bad else Good)
            }
        }
        item {
            Section(title = "Letzte 7 Tage", subtitle = "Stunden Games und Videos, gestrichelt: Budget") {
                BarChart(
                    w.map { it.entertainment / 3_600_000f }, days.map { Time.weekday(it) },
                    goal = prefs.entertainmentBudget / 60f,
                )
                Spacer(Modifier.height(8.dp))
                Muted("Gesamt im Schnitt: ${Time.durMs(w.sumOf { it.total } / 7)} pro Tag")
            }
        }
        item {
            Section(title = "Tagesbudget für Games und Videos", subtitle = if (prefs.phase < 3) "Aktiv ab Phase 3, du kannst es aber schon setzen" else null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120, 180, 240).forEach { m ->
                        FilterChip(selected = prefs.entertainmentBudget == m, onClick = { prefs.entertainmentBudget = m },
                            label = { Text(if (m % 60 == 0) "${m / 60} h" else "${m / 60}.5 h") })
                    }
                }
                Muted("Kein Verbot. Games und YouTube kommen nach dem Fokus-Block, dann ohne schlechtes Gewissen.")
            }
        }
        item {
            Section(title = "Apps heute", subtitle = "Schalter = zählt als Unterhaltung") {
                val extra = prefs.extraEntertainment.split(',').filter { it.isNotBlank() }.toSet()
                today.apps.take(25).forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label)
                            Muted(Time.durMs(a.ms))
                        }
                        Switch(checked = a.entertainment, onCheckedChange = { on ->
                            prefs.extraEntertainment = (if (on) extra + a.pkg else extra - a.pkg).joinToString(",")
                            refresh++
                        })
                    }
                }
            }
        }
    }
}

// ======================= Verlauf =======================

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val today = Time.today()
    val from = today.minusDays(55)
    val logs by e.dao.habitsSinceFlow(from.toString()).collectAsState(initial = emptyList())
    val moods by e.dao.moodSince(today.minusDays(29).toString()).collectAsState(initial = emptyList())
    val snus by e.dao.snusPerDay(Time.startOfDay(today.minusDays(55))).collectAsState(initial = emptyList())
    val sleep by produceState(emptyList<ch.akh.kompass.data.SleepEntry>(), logs.size) {
        value = withContext(Dispatchers.IO) { e.dao.sleepSince(today.minusDays(55).toString()) }
    }
    val weeks = (7 downTo 0).map { Time.weekStart(today).minusWeeks(it.toLong()) }
    val active = e.builtInItems().filter { it.daily }.map { it.key }.toSet()

    Screen(title = "Verlauf") {
        item {
            val totalDone = logs.count { it.level >= 1 }
            val full = logs.count { it.level == 2 }
            Section(title = "Die letzten 8 Wochen") {
                Row {
                    Stat("Erledigt", "$totalDone", Modifier.weight(1f), sub = "davon $full ganz")
                    Stat("Phase", "${prefs.phase}: ${PhasePlan.phase(prefs.phase).name}", Modifier.weight(1f))
                }
            }
        }
        item {
            Section(title = "Tägliche Gewohnheiten pro Woche", subtitle = "Anteil erledigt") {
                val vals = weeks.map { ws ->
                    val we = ws.plusDays(7).toString()
                    val n = logs.count { it.key in active && it.date >= ws.toString() && it.date < we }
                    val days = if (ws == Time.weekStart(today)) today.dayOfWeek.value else 7
                    if (active.isEmpty()) 0f else n.toFloat() / (active.size * days)
                }
                BarChart(vals.map { it * 100 }, weeks.map { Time.short(it) }, goal = 70f)
                Muted("Gestrichelt: 70 %. Mehr braucht es nicht, um voranzukommen.")
            }
        }
        item {
            val days = (55 downTo 0).map { today.minusDays(it.toLong()) }
            val map = sleep.associateBy { it.wakeDate }
            Section(title = "Aufstehzeit, 8 Wochen") {
                LineChart(days.map { d -> map[d.toString()]?.wakeAt?.let { Time.minuteOfDay(it).toFloat() } },
                    listOf(Time.short(days.first()), Time.short(days.last())), goal = prefs.goalWake.toFloat(), invert = true,
                    yLabel = { Time.hm(it.toInt()) })
            }
        }
        item {
            val m = snus.associate { it.day to it.n }
            val vals = weeks.map { ws -> (0..6).sumOf { m[ws.plusDays(it.toLong()).toString()] ?: 0 } / 7f }
            Section(title = "Snus, Schnitt pro Tag je Woche") {
                BarChart(vals, weeks.map { Time.short(it) })
            }
        }
        item {
            val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
            val map = moods.associateBy { it.date }
            Section(title = "Freude, 30 Tage") {
                LineChart(days.map { map[it.toString()]?.joy?.toFloat() }, listOf(Time.short(days.first()), Time.short(days.last())), min = 1f, max = 5f, height = 90.dp)
            }
        }
    }
}
