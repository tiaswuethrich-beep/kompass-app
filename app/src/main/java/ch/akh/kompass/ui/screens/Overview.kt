@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.akh.kompass.ui.screens

import ch.akh.kompass.data.snusPerDay
import ch.akh.kompass.data.snusPerDayOnce
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import ch.akh.kompass.ui.DonutChart
import ch.akh.kompass.ui.RankBar
import ch.akh.kompass.ui.StackedBarChart
import ch.akh.kompass.ui.chartColor
import ch.akh.kompass.ui.rememberTicker
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

private val categoryOrder = listOf("Videos", "Games", "Social Media", "Chats", "Browser", "Musik", "Produktiv", "News", "Unterwegs", "Andere")

fun categoryColor(c: String): androidx.compose.ui.graphics.Color = chartColor(categoryOrder.indexOf(c).let { if (it < 0) categoryOrder.size - 1 else it })

@Composable
fun ScreenTimeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    var refresh by remember { mutableIntStateOf(0) }
    val tick = rememberTicker()
    val has = ScreenTime.hasPermission(ctx)
    var loadedAt by remember { mutableLongStateOf(0L) }
    val days = (6 downTo 0).map { Time.today().minusDays(it.toLong()) }
    val week by produceState<List<DayUsage>?>(null, refresh, tick, prefs.extraEntertainment, prefs.excludedEntertainment, prefs.screenSource) {
        value = withContext(Dispatchers.IO) { if (has) days.map { ScreenTime.day(ctx, it) } else null }
        loadedAt = System.currentTimeMillis()
    }
    val late by produceState(0L, refresh, tick) {
        value = withContext(Dispatchers.IO) { if (has) ScreenTime.lateLastNight(ctx) else 0L }
    }
    var openApp by remember { mutableStateOf<String?>(null) }

    Screen(title = "Bildschirmzeit", onBack = onBack, actions = {
        IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Neu laden") }
    }) {
        if (has) aiItem(ch.akh.kompass.ai.AiFeature.SCREEN)
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
            Section(title = "Heute", subtitle = "Stand ${Time.hm(loadedAt)}, aktualisiert sich jede Minute") {
                Row {
                    Stat("Gesamt", Time.durMs(today.total), Modifier.weight(1f))
                    Stat("Games und Videos", Time.durMs(today.entertainment), Modifier.weight(1f),
                        color = if (today.entertainment > prefs.entertainmentBudget * 60_000L) Bad else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Nach Licht aus", Time.durMs(late), Modifier.weight(1f), sub = "letzte Nacht", color = if (late > 15 * 60_000L) Bad else Good)
                    Stat("Geöffnet", "${today.sessions.size}x", Modifier.weight(1f), sub = "Sitzungen heute")
                }
            }
        }
        item {
            Section(title = "Kategorien heute") {
                DonutChart(
                    today.byCategory.map { it.first to it.second / 60_000f },
                    center = Time.durMs(today.total),
                    format = { Time.dur(it.toInt()) },
                    colorOf = { if (it == "Rest") chartColor(7) else categoryColor(it) },
                )
            }
        }
        item {
            Section(title = "Über den Tag verteilt", subtitle = "Minuten pro Stunde") {
                val lo = ch.akh.kompass.plan.Engine.get(ctx).lightsOut()
                BarChart(today.perHour, (0..23).map { if (it % 3 == 0) "$it" else "" }, height = 110.dp,
                    colorFor = { h, _ ->
                        val loH = lo / 60
                        val wakeH = prefs.currentWake / 60
                        val night = if (loH > wakeH) (h >= loH || h < wakeH) else (h in loH until wakeH)
                        if (night) Bad else androidx.compose.ui.graphics.Color.Unspecified
                    })
                Muted("Rot: Stunden zwischen Licht aus und Aufstehen.")
            }
        }
        item {
            val cats = categoryOrder.filter { c -> w.any { d -> d.byCategory.any { it.first == c && it.second > 0 } } }
            Section(title = "Letzte 7 Tage nach Kategorie", subtitle = "Stunden pro Tag, gestrichelt: Budget Games und Videos") {
                StackedBarChart(
                    w.map { d -> cats.map { c -> (d.byCategory.firstOrNull { it.first == c }?.second ?: 0L) / 3_600_000f } },
                    days.map { Time.weekday(it) }, cats,
                    goal = prefs.entertainmentBudget / 60f,
                    colorOf = { categoryColor(it) },
                    format = { String.format("%.1f h", it) },
                )
                Spacer(Modifier.height(8.dp))
                Muted("Schnitt: ${Time.durMs(w.sumOf { it.total } / 7)} gesamt, ${Time.durMs(w.sumOf { it.entertainment } / 7)} Games und Videos pro Tag")
            }
        }
        item { BlockerSection() }
        item {
            Section(title = "Tagesbudget für Games und Videos", subtitle = if (prefs.phase < 3) "Aktiv ab Phase 3, du kannst es aber schon setzen" else null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120, 180, 240).forEach { m ->
                        FilterChip(selected = prefs.entertainmentBudget == m, onClick = { prefs.entertainmentBudget = m },
                            label = { Text(if (m % 60 == 0) "${m / 60} h" else "${m / 60}.5 h") })
                    }
                }
                Muted("Kein Verbot. Games und YouTube kommen nach dem Fokus-Block, dann ohne schlechtes Gewissen.")
            }
        }
        item {
            Section(title = "Apps heute", subtitle = "Schalter: zählt als Unterhaltung. Tippen zeigt die Sitzungen.") {
                val extra = prefs.extraEntertainment.split(',').filter { it.isNotBlank() }.toSet()
                val excl = prefs.excludedEntertainment.split(',').filter { it.isNotBlank() }.toSet()
                val top = today.apps.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L
                today.apps.take(30).forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RankBar(
                            a.label, Time.durMs(a.ms), a.ms.toFloat() / top, categoryColor(a.category),
                            Modifier.weight(1f).clickable { openApp = if (openApp == a.pkg) null else a.pkg },
                            sub = "${a.category}${if (a.count > 0) " · ${a.count}x geöffnet" else ""}",
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = a.entertainment, onCheckedChange = { on ->
                            // Ein = sicher Unterhaltung, Aus = sicher nicht. Gilt auch für bekannte Apps wie YouTube.
                            prefs.extraEntertainment = (if (on) extra + a.pkg else extra - a.pkg).joinToString(",")
                            prefs.excludedEntertainment = (if (on) excl - a.pkg else excl + a.pkg).joinToString(",")
                            refresh++
                        })
                    }
                    if (openApp == a.pkg) {
                        val ss = today.sessions.filter { it.pkg == a.pkg }
                        Column(Modifier.padding(start = 8.dp, bottom = 6.dp)) {
                            if (ss.isEmpty()) Muted("Keine einzelnen Sitzungen gefunden, Wert stammt aus der Android-Statistik.")
                            ss.takeLast(20).reversed().forEach { s ->
                                Text("${Time.hm(s.start)} bis ${Time.hm(s.end)} · ${Time.durMs(s.end - s.start)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Section(title = "Datenquelle", subtitle = "Falls die Zahlen nicht mit deinem Handy übereinstimmen") {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Automatisch", 1 to "Ereignisse", 2 to "Android-Statistik").forEach { (v, l) ->
                        FilterChip(selected = prefs.screenSource == v, onClick = { prefs.screenSource = v }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Muted("Heute laut Ereignissen (jedes Öffnen und Schliessen einer App): ${Time.durMs(today.eventsTotal)}")
                Muted("Heute laut Android-Statistik: ${if (prefs.screenSource == 1) "nicht geladen" else Time.durMs(today.systemTotal)}")
                Muted("Automatisch nimmt pro App den höheren Wert, damit nichts fehlt. Vergleich mit Einstellungen > Digital Wellbeing und nimm die Quelle, die passt.")
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

// ======================= Blocker =======================

@Composable
fun BlockerSection() {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val tick = rememberTicker(5_000L)
    val on = remember(tick) { ch.akh.kompass.blocker.BlockerService.enabled(ctx) }
    var pickEvening by remember { mutableStateOf(false) }
    Section(title = "Blocker", subtitle = if (on) "Aktiv. ${prefs.blockedCount}x Reels oder Shorts weggeklickt." else "Noch nicht eingeschaltet", highlight = !on) {
        if (!on) {
            Muted("Der Blocker läuft als Bedienungshilfe. Nur so kann Android erkennen, ob in YouTube oder Instagram gerade Shorts oder Reels offen sind. Kompass speichert dabei nichts und schickt nichts weg.")
            Spacer(Modifier.height(6.dp))
            Muted("So geht's: Tippen, dann \"Installierte Apps\" > \"Kompass Blocker\" einschalten. Steht dort \"Eingeschränkte Einstellung\": In den App-Infos von Kompass oben rechts auf ⋮ tippen, \"Eingeschränkte Einstellungen zulassen\" wählen und es nochmals versuchen.")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startActivity(ch.akh.kompass.blocker.BlockerService.settingsIntent()) }) { Text("Blocker einschalten") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("App-Infos") }
            }
            Spacer(Modifier.height(10.dp))
        }
        BlockerToggle("Reels und Shorts blockieren", "Reels aus DMs und geteilte Shorts-Links gehen weiterhin", prefs.blockShorts) { prefs.blockShorts = it }
        BlockerToggle("TikTok ganz blockieren", "TikTok besteht nur aus Kurzvideos", prefs.blockTikTok) { prefs.blockTikTok = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Feierabend")
                Muted(if (prefs.eveningStart >= 0) "Ab ${Time.hm(prefs.eveningStart)}: nach ${prefs.eveningAskAfter} Min Videos und Games kommt eine Nachfrage" else "Aus")
            }
            Switch(checked = prefs.eveningStart >= 0, onCheckedChange = { prefs.eveningStart = if (it) 20 * 60 else -1 })
        }
        if (prefs.eveningStart >= 0) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.TextButton(onClick = { pickEvening = true }) { Text("Ab ${Time.hm(prefs.eveningStart)}") }
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterChip(selected = prefs.eveningAskAfter == m, onClick = { prefs.eveningAskAfter = m }, label = { Text("$m min") })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BlockerToggle(
            "Hartes Tageslimit", "Ab ${Time.dur(prefs.entertainmentBudget)} Games und Videos ist für heute Schluss. Das Limit stellst du unten ein.",
            prefs.hardLimit,
        ) { prefs.hardLimit = it }
    }
    if (pickEvening) ch.akh.kompass.ui.TimeDialog(prefs.eveningStart.coerceAtLeast(0), "Feierabend ab", { pickEvening = false }) {
        prefs.eveningStart = it
        pickEvening = false
    }
}

@Composable
private fun BlockerToggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Muted(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
