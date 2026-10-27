package ch.akh.kompass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.akh.kompass.data.MoodEntry
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.QuickLog
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.ui.undo
import ch.akh.kompass.util.Health
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Morgen- und Abend-Check-in: alles Wichtige in einer Minute eintragen.
 * Morgens: Schlaf, Stimmung, das eine Ding für heute. Abends: Konsum, Lesen, offene Gewohnheiten, Freude.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingScreen(kind: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val undo = undo()
    val morning = kind == "morgen"
    val today = Time.today()
    val todayStr = today.toString()
    val dao = e.dao
    val mood by dao.moodOn(todayStr).collectAsState(initial = null)
    val logs by dao.habitsOn(todayStr).collectAsState(initial = emptyList())
    val customs by dao.customHabits().collectAsState(initial = emptyList())
    val subs by dao.substancesBetweenFlow(Time.startOfDay(today), Time.endOfDay(today)).collectAsState(initial = emptyList())
    val snus by dao.snusCountFlow(0, Time.startOfDay(today), Time.endOfDay(today)).collectAsState(initial = 0)
    val books by dao.books().collectAsState(initial = emptyList())
    var reload by remember { mutableIntStateOf(0) }
    val sleep by produceState<ch.akh.kompass.data.SleepEntry?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            if (prefs.healthSleepAuto) Health.syncSleep(ctx)
            dao.sleepOn(todayStr)
        }
    }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { e.syncAuto(); Health.syncSteps(ctx) } }

    Screen(title = if (morning) "Morgen-Check-in" else "Abend-Check-in", onBack = onBack) {
        aiItem(if (morning) ch.akh.kompass.ai.AiFeature.MORNING else ch.akh.kompass.ai.AiFeature.EVENING)
        item {
            Muted(
                if (morning) "${Time.nice(today)}. Eine Minute, dann los in den Tag."
                else "Kurz zurückschauen, dann ist der Kopf frei für die Abendroutine.",
            )
        }

        if (morning) {
            item {
                Section(title = "Wie hast du geschlafen?") {
                    val s = sleep
                    if (s?.bedAt != null && s.wakeAt != null) {
                        Row {
                            Stat("Geschlafen", Time.durMs(s.wakeAt - s.bedAt), Modifier.weight(1f))
                            Stat("Aufgewacht", Time.hm(s.wakeAt), Modifier.weight(1f))
                        }
                        if (s.note.isNotBlank()) Muted(s.note)
                    } else {
                        Muted("Noch keine Nacht für heute. Mit der Fitbit kommt sie automatisch, sonst im Schlaf-Bereich eintragen.")
                    }
                    Spacer(Modifier.height(6.dp))
                    ScaleRow("Erholt", s?.quality ?: 0) { q ->
                        val before = s
                        if (before != null) undo.run("Schlafqualität gespeichert") {
                            dao.upsertSleep(before.copy(quality = q))
                            reload++
                            suspend { dao.upsertSleep(before); reload++ }
                        }
                    }
                }
            }
        }

        item {
            Section(title = "Wie geht's dir?") {
                var mo by remember(mood) { mutableStateOf(mood?.mood ?: 0) }
                var en by remember(mood) { mutableStateOf(mood?.energy ?: 0) }
                var jo by remember(mood) { mutableStateOf(mood?.joy ?: 0) }
                var note by remember(mood) { mutableStateOf(mood?.note ?: "") }
                fun save() {
                    if (mo == 0 || en == 0) return
                    val before = mood
                    val m = MoodEntry(todayStr, mo, en, if (jo == 0) mo else jo, before?.social ?: false, note.trim())
                    undo.run("Gespeichert") {
                        dao.upsertMood(m)
                        e.syncAuto()
                        suspend {
                            if (before == null) {
                                dao.deleteMood(todayStr)
                                dao.deleteHabit(todayStr, "stimmung")
                            } else dao.upsertMood(before)
                        }
                    }
                }
                ScaleRow("Stimmung", mo) { mo = it; save() }
                ScaleRow("Energie", en) { en = it; save() }
                if (!morning) {
                    ScaleRow("Freude", jo) { jo = it; save() }
                    TextInput(note, { note = it }, "Was war heute gut? (ein Satz reicht)", singleLine = false)
                    Spacer(Modifier.height(6.dp))
                    FilledTonalButton(onClick = { save() }, enabled = mo > 0 && en > 0) { Text("Notiz speichern") }
                    if (mo in 1..2 && jo in 1..2) {
                        Spacer(Modifier.height(6.dp))
                        Muted("Ein schwerer Tag. Das ist okay. Wenn sich das öfter so anfühlt, lohnt sich ein Gespräch mit deinem Hausarzt, ganz ohne Druck.")
                    }
                }
            }
        }

        if (morning) {
            item {
                Section(title = "Das eine Ding, das heute zählt") {
                    var t by remember { mutableStateOf(prefs.intention(todayStr)) }
                    TextInput(t, { t = it; prefs.setIntention(todayStr, it) }, "z. B. Gym, Wäsche, 2 Fokus-Blöcke")
                    Spacer(Modifier.height(6.dp))
                    val items = e.activeItems(customs).filter { it.daily && logs.none { l -> l.key == it.key } }
                    if (items.isNotEmpty()) Muted("Heute offen: " + items.take(5).joinToString(", ") { it.title })
                }
            }
        }

        item {
            Section(title = if (morning) "Schon etwas konsumiert?" else "Heute konsumiert", subtitle = "Antippen = +1, Minus nimmt den letzten zurück") {
                val counts = listOf("Kaffee", "Energy", "Alkohol", "Weed").associateWith { t -> subs.count { it.type == t } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CounterChip("Snus", snus,
                        onAdd = { undo.run("Beutel eingetragen") { QuickLog.pouch(e) } },
                        onRemove = {
                            undo.run("Beutel ausgetragen") {
                                val last = dao.lastPouch()
                                if (last != null && Time.dayOf(last) == today) dao.undoLastPouch()
                                e.syncAuto()
                                suspend { if (last != null) QuickLog.pouch(e, last); Unit }
                            }
                        })
                    counts.forEach { (t, n) ->
                        if (t == "Weed" && n == 0 && !morning) return@forEach
                        CounterChip(t, n,
                            onAdd = { undo.run("$t eingetragen") { QuickLog.substance(e, t) } },
                            onRemove = {
                                undo.run("$t ausgetragen") {
                                    val last = subs.lastOrNull { it.type == t }
                                    if (last != null) dao.deleteSubstanceId(last.id)
                                    suspend { if (last != null) dao.insertSubstance(last); Unit }
                                }
                            })
                    }
                }
                val lateCaffeine = subs.any { (it.type == "Kaffee" || it.type == "Energy") && Time.minuteOfDay(it.at) >= 14 * 60 }
                if (lateCaffeine) {
                    Spacer(Modifier.height(6.dp))
                    Muted("Koffein nach 14 Uhr wirkt bis in die Nacht. Morgen früher dran denken.")
                }
            }
        }

        if (!morning) {
            val reading = books.firstOrNull { it.status == 1 }
            item {
                Section(title = "Gelesen?") {
                    if (reading == null) {
                        Muted("Kein Buch in Arbeit. Im Bücher-Bereich eins anfangen.")
                    } else {
                        var page by remember(reading.id, reading.current) { mutableStateOf("") }
                        var minutes by remember(reading.id) { mutableStateOf("15") }
                        Text(reading.title, fontWeight = FontWeight.Medium)
                        Muted("Bisher Seite ${reading.current}${if (reading.pages > 0) " von ${reading.pages}" else ""}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberField(page, { page = it }, "Jetzt Seite", Modifier.weight(1f))
                            NumberField(minutes, { minutes = it }, "Minuten", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = {
                            val p = page.toIntOrNull() ?: reading.current
                            undo.run("Lesen eingetragen") { QuickLog.reading(e, reading, p, minutes.toIntOrNull() ?: 0) }
                            page = ""
                        }, enabled = (minutes.toIntOrNull() ?: 0) > 0 || page.isNotBlank()) { Text("Eintragen") }
                    }
                }
            }
            item {
                val open = e.activeItems(customs).filter { it.daily }
                Section(title = "Gewohnheiten heute", subtitle = "${open.count { h -> logs.any { it.key == h.key } }} von ${open.size} erledigt") {
                    open.forEach { h ->
                        HabitRow(h, logs, todayStr, e) { lvl ->
                            undo.run(if (lvl == 0) "${h.title}: wieder offen" else "${h.title}: erledigt") { QuickLog.habit(e, todayStr, h.key, lvl) }
                        }
                    }
                }
            }
            item {
                Section(title = "Morgen") {
                    Row {
                        Stat("Licht aus", Time.hm(e.lightsOut()), Modifier.weight(1f))
                        Stat("Wecker", if (prefs.alarmEnabled) Time.hm(prefs.currentWake) else "aus", Modifier.weight(1f))
                    }
                    if (prefs.intention(todayStr).isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Muted("Heute wolltest du: ${prefs.intention(todayStr)}")
                    }
                    if (prefs.alarmEnabled && prefs.alarmViaClock) {
                        Spacer(Modifier.height(6.dp))
                        FilledTonalButton(onClick = { ch.akh.kompass.alarm.ClockAlarm.ensure(ctx, force = true) }) {
                            Text("Wecker auf ${Time.hm(prefs.currentWake)} stellen")
                        }
                    }
                }
            }
        }

        item {
            val done = prefs.briefingDone(kind, todayStr)
            Button(
                onClick = {
                    // Wer den Morgen-Check-in macht, ist wach: Tag starten, falls noch nicht geschehen.
                    if (morning && Time.today() < Time.calToday()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { e.wake() }
                    }
                    prefs.setBriefingDone(kind, todayStr, true)
                    ch.akh.kompass.alarm.Notify.cancel(ctx, ch.akh.kompass.alarm.ReminderReceiver.notifId(if (morning) "_morning" else "_evening"))
                    undo.offer("Check-in abgeschlossen") { prefs.setBriefingDone(kind, todayStr, false) }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (done) "Fertig" else if (morning) "Los geht's" else "Fertig für heute") }
        }
    }
}

@Composable
private fun CounterChip(label: String, count: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = onAdd) {
            Text("$label  $count", color = if (count == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        AnimatedVisibility(count > 0) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Remove, "$label zurücknehmen") }
        }
    }
}
