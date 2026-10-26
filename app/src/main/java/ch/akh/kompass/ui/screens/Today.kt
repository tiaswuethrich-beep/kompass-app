package ch.akh.kompass.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import ch.akh.kompass.MainActivity
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.data.HabitLog
import ch.akh.kompass.data.MoodEntry
import ch.akh.kompass.data.SnusEntry
import ch.akh.kompass.plan.Auto
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.HabitItem
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Progress
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.go
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Kalender
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@Composable
fun TodayScreen(nav: NavHostController, activity: MainActivity) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val dao = e.dao
    val scope = rememberCoroutineScope()
    val today = Time.today()
    val todayStr = today.toString()
    val from = minOf(Time.weekStart(today), today.minusDays(1)).toString()

    val logs by dao.habitsSinceFlow(from).collectAsState(initial = emptyList())
    val customs by dao.customHabits().collectAsState(initial = emptyList())
    val openSleep by dao.openSleepFlow().collectAsState(initial = null)
    val snusToday by dao.snusCountFlow(0, Time.startOfDay(today), Time.endOfDay(today)).collectAsState(initial = 0)
    val cravings by dao.snusCountFlow(1, Time.startOfDay(today), Time.endOfDay(today)).collectAsState(initial = 0)
    val lastPouch by dao.lastPouchFlow().collectAsState(initial = null)
    val focusMin by dao.focusMinutesOn(todayStr).collectAsState(initial = 0)
    val mood by dao.moodOn(todayStr).collectAsState(initial = null)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            e.updateSleepShift()
            e.syncAuto()
            e.syncAuto(today.minusDays(1))
            Scheduler.rescheduleAll(ctx)
        }
    }

    val status by produceState<ch.akh.kompass.plan.PhaseStatus?>(null, logs, prefs.phase, snusToday) {
        value = withContext(Dispatchers.IO) { e.phaseStatus() }
    }
    val usage by produceState<ch.akh.kompass.util.DayUsage?>(null) {
        value = withContext(Dispatchers.IO) { if (ScreenTime.hasPermission(ctx)) ScreenTime.day(ctx) else null }
    }
    val events by produceState(emptyList<ch.akh.kompass.util.CalEvent>()) {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        value = if (ok) withContext(Dispatchers.IO) { Kalender.eventsOn(ctx, today) } else emptyList()
    }

    val items = e.activeItems(customs)
    val phase = PhasePlan.phase(prefs.phase)
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 4..10 -> "Guten Morgen"
        in 11..16 -> "Hallo"
        in 17..21 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    var wakeShown by remember { mutableStateOf(activity.wakeLevel.value) }

    Screen(title = "Heute") {
        item {
            Column {
                Text("$greeting${if (prefs.name.isNotBlank()) ", ${prefs.name}" else ""}", style = MaterialTheme.typography.headlineMedium)
                Muted("${Time.nice(today)} · Phase ${phase.index}: ${phase.name} · Tag ${status?.daysIn ?: 1}")
            }
        }

        wakeShown?.let { lvl ->
            item {
                Section {
                    Text(
                        when (lvl) {
                            2 -> "Pünktlich aufgestanden. Genau so wird die neue Zeit normal."
                            1 -> "Aufgestanden, knapp neben der Zielzeit. Zählt."
                            else -> "Aufgestanden. Heute zählt nicht als pünktlich, morgen neuer Versuch."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Muted("Jetzt: 10 Minuten raus ans Licht, dann Bett machen.")
                    TextButton(onClick = { wakeShown = null; activity.wakeLevel.value = null }) { Text("OK") }
                }
            }
        }

        status?.takeIf { it.ready }?.let { s ->
            item {
                Section(title = "Meilenstein erreicht", subtitle = s.phase.milestone, onClick = { nav.go("plan") }) {
                    Text("Du kannst Phase ${s.phase.index + 1} freischalten, wenn du bereit bist.", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---------- Schlaf ----------
        item {
            val sleeping = openSleep != null
            Section(title = "Schlaf", onClick = { nav.go("sleep") }) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (prefs.alarmEnabled) Stat("Wecker", Time.hm(prefs.currentWake), Modifier.weight(1f))
                    else Stat("Wecker", "aus", Modifier.weight(1f))
                    Stat("Licht aus", Time.hm(e.lightsOut()), Modifier.weight(1f))
                    if (prefs.phase >= 1) Stat("Routine ab", Time.hm(e.routineStart()), Modifier.weight(1f))
                }
                if (prefs.shiftNote.isNotBlank() && prefs.phase >= 1) {
                    Spacer(Modifier.height(8.dp))
                    Muted(prefs.shiftNote)
                }
                Spacer(Modifier.height(12.dp))
                if (sleeping) {
                    Muted("Gute Nacht gedrückt um ${Time.hm(openSleep!!.bedAt ?: 0L)}")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            val lvl = withContext(Dispatchers.IO) { e.wake().also { Scheduler.rescheduleAll(ctx) } }
                            wakeShown = lvl
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.WbSunny, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wach")
                    }
                } else {
                    FilledTonalButton(onClick = {
                        scope.launch(Dispatchers.IO) { e.goodNight() }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Bedtime, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Gute Nacht")
                    }
                }
            }
        }

        // ---------- Gewohnheiten ----------
        item {
            val daily = items.filter { it.daily }
            val weekly = items.filter { !it.daily }
            val doneToday = daily.count { h -> logs.any { it.key == h.key && it.date == todayStr } }
            Section(
                title = "Gewohnheiten",
                subtitle = "$doneToday von ${daily.size} heute erledigt",
                trailing = { TextButton(onClick = { nav.go("habits") }) { Text("Alle") } },
            ) {
                Progress(if (daily.isEmpty()) 0f else doneToday.toFloat() / daily.size, color = Good)
                Spacer(Modifier.height(8.dp))
                daily.forEach { h ->
                    HabitRow(h, logs, todayStr, e) { lvl ->
                        scope.launch(Dispatchers.IO) {
                            if (lvl == 0) dao.deleteHabit(todayStr, h.key) else dao.upsertHabit(HabitLog(todayStr, h.key, lvl))
                        }
                    }
                }
                if (weekly.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Diese Woche", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    weekly.forEach { h ->
                        HabitRow(h, logs, todayStr, e) { lvl ->
                            scope.launch(Dispatchers.IO) {
                                if (lvl == 0) dao.deleteHabit(todayStr, h.key) else dao.upsertHabit(HabitLog(todayStr, h.key, lvl))
                            }
                        }
                    }
                }
            }
        }

        // ---------- Snus ----------
        if (prefs.phase < 4 || snusToday > 0) item {
            val limit = e.snusLimit()
            Section(title = "Snus", onClick = { nav.go("snus") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat(
                        "Heute", if (limit != null) "$snusToday / $limit" else "$snusToday",
                        Modifier.weight(1f),
                        color = if (limit != null && snusToday > limit) Bad else MaterialTheme.colorScheme.onSurface,
                        sub = if (limit == null) "nur zählen" else "Limit laut Plan",
                    )
                    Stat(
                        "Letzter Beutel",
                        lastPouch?.let { Time.durMs(System.currentTimeMillis() - it) } ?: "noch keiner",
                        Modifier.weight(1f), sub = if (cravings > 0) "$cravings Cravings überstanden" else null,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) { dao.insertSnus(SnusEntry(at = System.currentTimeMillis(), type = 0)); e.syncAuto() }
                    }, modifier = Modifier.weight(1f)) { Text("+1 Beutel") }
                    OutlinedButton(onClick = { nav.go("craving") }, modifier = Modifier.weight(1f)) { Text("Craving") }
                }
            }
        }

        // ---------- Fokus ----------
        if (prefs.phase >= 2 || focusMin > 0) item {
            val target = when {
                prefs.phase >= 5 -> 480
                prefs.phase >= 4 -> 360
                prefs.phase >= 3 -> 240
                else -> 100
            }
            Section(title = "Fokus", onClick = { nav.go("focus") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat("Heute", Time.dur(focusMin), Modifier.weight(1f), sub = "Ziel ${Time.dur(target)}")
                    Button(onClick = { nav.go("focus") }) { Text("Block starten") }
                }
                Spacer(Modifier.height(8.dp))
                Progress(focusMin.toFloat() / target)
            }
        }

        // ---------- Bildschirm ----------
        item {
            Section(title = "Bildschirmzeit", onClick = { nav.go("screen") }) {
                val u = usage
                if (u == null) {
                    Muted("Gib der App einmal den Nutzungszugriff, dann misst sie deine Bildschirmzeit selbst.")
                    TextButton(onClick = { ctx.startActivity(ScreenTime.settingsIntent()) }) { Text("Zugriff erlauben") }
                } else {
                    Row {
                        Stat("Gesamt", Time.durMs(u.total), Modifier.weight(1f))
                        Stat(
                            "Games und Videos", Time.durMs(u.entertainment), Modifier.weight(1f),
                            sub = if (prefs.phase >= 3) "Budget ${Time.dur(prefs.entertainmentBudget)}" else null,
                            color = if (prefs.phase >= 3 && u.entertainment > prefs.entertainmentBudget * 60_000L) Bad else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (prefs.phase >= 3) {
                        Spacer(Modifier.height(8.dp))
                        Progress(u.entertainment / (prefs.entertainmentBudget * 60_000f))
                    }
                }
            }
        }

        // ---------- Stimmung ----------
        item {
            Section(title = "Wie geht es dir heute?", onClick = { nav.go("mood") }) {
                val m = mood
                if (m != null) {
                    Row {
                        Stat("Stimmung", "${m.mood}/5", Modifier.weight(1f))
                        Stat("Energie", "${m.energy}/5", Modifier.weight(1f))
                        Stat("Freude", "${m.joy}/5", Modifier.weight(1f))
                    }
                } else {
                    QuickMood { mo, en, jo ->
                        scope.launch(Dispatchers.IO) {
                            dao.upsertMood(MoodEntry(todayStr, mo, en, jo))
                            e.syncAuto()
                        }
                    }
                }
            }
        }

        // ---------- Kalender ----------
        if (events.isNotEmpty()) item {
            Section(title = "Termine heute") {
                events.forEach { ev ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(if (ev.allDay) "ganztags" else Time.hm(ev.begin), Modifier.width(72.dp), color = MaterialTheme.colorScheme.primary)
                        Text(ev.title)
                    }
                }
            }
        }

        // ---------- Tipp ----------
        item {
            val tip = phase.tips[(today.dayOfYear) % phase.tips.size]
            Section(title = "Tipp für diese Phase") { Muted(tip) }
        }
    }
}

/** Eine Gewohnheit mit den zwei Stufen Mindestens und Ganz. */
@Composable
fun HabitRow(h: HabitItem, logs: List<HabitLog>, day: String, e: Engine, onSet: (Int) -> Unit) {
    val level = logs.firstOrNull { it.key == h.key && it.date == day }?.level ?: 0
    val week = e.weekCount(logs, h.key)
    val missed = e.missedYesterday(logs, h)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    h.title + if (!h.daily) "  ($week/${h.perWeek})" else "",
                    fontWeight = FontWeight.Medium,
                    color = if (level > 0) Good else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (level == 2) h.full else "Mindestens: ${h.min}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missed) Text("Gestern verpasst. Heute nicht zweimal.", style = MaterialTheme.typography.bodySmall, color = Bad)
                if (h.auto != Auto.NONE && level == 0) Text("Wird automatisch abgehakt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            FilterChip(selected = level == 1, onClick = { onSet(if (level == 1) 0 else 1) }, label = { Text("Mini") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = level == 2, onClick = { onSet(if (level == 2) 0 else 2) }, label = { Text("Ganz") })
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun QuickMood(onSave: (Int, Int, Int) -> Unit) {
    var mo by remember { mutableStateOf(0) }
    var en by remember { mutableStateOf(0) }
    var jo by remember { mutableStateOf(0) }
    Column {
        ScaleRow("Stimmung", mo) { mo = it }
        ScaleRow("Energie", en) { en = it }
        ScaleRow("Freude", jo) { jo = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(mo, en, jo) },
            enabled = mo > 0 && en > 0 && jo > 0,
            colors = ButtonDefaults.buttonColors(),
        ) { Text("Speichern") }
    }
}

@Composable
fun ScaleRow(label: String, value: Int, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
        (1..5).forEach { v ->
            FilterChip(
                selected = value == v, onClick = { onPick(v) }, label = { Text("$v") },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
