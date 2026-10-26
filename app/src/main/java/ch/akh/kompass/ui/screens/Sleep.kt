package ch.akh.kompass.ui.screens

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.akh.kompass.alarm.AlarmActivity
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.data.SleepEntry
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.ui.LineChart
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TimeDialog
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SleepScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val undo = ch.akh.kompass.ui.undo()
    val entries by e.dao.recentSleep(60).collectAsState(initial = emptyList())
    var pick by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<SleepEntry?>(null) }

    fun resched() = scope.launch(Dispatchers.IO) { Scheduler.rescheduleAll(ctx) }

    val done = entries.filter { it.wakeAt != null }
    val withBed = done.filter { it.bedAt != null }
    val last7 = withBed.take(7)
    val avgSleep = if (last7.isEmpty()) null else last7.sumOf { (it.wakeAt!! - it.bedAt!!) } / last7.size
    val wakeAvg = done.take(7).map { Time.minuteOfDay(it.wakeAt!!) }.let { if (it.isEmpty()) null else it.sum() / it.size }

    Screen(title = "Schlaf", onBack = onBack) {
        item {
            Section(title = "Dein Schlaf-Anker") {
                Row {
                    Stat("Aufstehen", Time.hm(prefs.currentWake), Modifier.weight(1f).clickable { pick = "current" }, sub = "antippen zum Ändern")
                    Stat("Endziel", Time.hm(prefs.goalWake), Modifier.weight(1f).clickable { pick = "goal" })
                    Stat("Licht aus", Time.hm(e.lightsOut()), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                if (prefs.shiftNote.isNotBlank()) Muted(prefs.shiftNote)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wecker")
                        Muted(
                            Scheduler.nextWake(ctx)?.let { "Nächster: ${Time.weekday(Time.dayOf(it))} ${Time.hm(it)}" } ?: "Aus",
                        )
                    }
                    Switch(checked = prefs.alarmEnabled, onCheckedChange = {
                        prefs.alarmEnabled = it
                        resched()
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text("Schlafbedarf", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(450, 480, 510, 540).forEach { m ->
                        FilterChip(selected = prefs.sleepNeedMinutes == m, onClick = {
                            prefs.sleepNeedMinutes = m
                            resched()
                        }, label = { Text(Time.dur(m)) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Verschiebung", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 4, 7).forEach { d ->
                        FilterChip(selected = prefs.shiftEveryDays == d, onClick = { prefs.shiftEveryDays = d },
                            label = { Text("alle $d Tage") })
                    }
                }
                Muted("Jeweils ${prefs.shiftStepMinutes} Minuten früher, nur wenn es an den meisten Tagen geklappt hat.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AlarmActivity.EXTRA_TEST, true))
                }) { Text("Wecker testen") }
            }
        }

        // Android 14+: Vollbild-Wecker braucht eine Erlaubnis
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) item {
                Section(title = "Vollbild-Wecker erlauben") {
                    Muted("Damit der Wecker auch bei gesperrtem Handy den ganzen Bildschirm zeigt.")
                    TextButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Einstellung öffnen") }
                }
            }
        }

        item { HealthConnectCard() }

        item {
            val days = (13 downTo 0).map { Time.today().minusDays(it.toLong()) }
            val byDay = withBed.associateBy { it.wakeDate }
            val hours = days.map { d -> byDay[d.toString()]?.let { (it.wakeAt!! - it.bedAt!!) / 3_600_000f } ?: 0f }
            Section(title = "Schlafdauer, 14 Nächte", subtitle = "Stunden, gestrichelt: dein Schlafbedarf") {
                ch.akh.kompass.ui.BarChart(
                    hours, days.map { if (it.dayOfWeek == java.time.DayOfWeek.MONDAY || it == days.last()) Time.short(it) else "" },
                    goal = prefs.sleepNeedMinutes / 60f,
                    colorFor = { _, v -> if (v > 0 && v < prefs.sleepNeedMinutes / 60f - 1f) ch.akh.kompass.ui.Warn else androidx.compose.ui.graphics.Color.Unspecified },
                )
            }
        }

        item {
            Section(title = "Letzte 7 Nächte") {
                Row {
                    Stat("Schlaf im Schnitt", avgSleep?.let { Time.durMs(it) } ?: "–", Modifier.weight(1f),
                        sub = "Ziel ${Time.dur(prefs.sleepNeedMinutes)}")
                    Stat("Aufgestanden im Schnitt", wakeAvg?.let { Time.hm(it) } ?: "–", Modifier.weight(1f))
                }
            }
        }

        item {
            val days = (29 downTo 0).map { Time.today().minusDays(it.toLong()) }
            val byDay = done.associateBy { it.wakeDate }
            Section(title = "Aufstehzeit, 30 Tage", subtitle = "Gestrichelt: aktuelles Ziel") {
                LineChart(
                    values = days.map { d -> byDay[d.toString()]?.wakeAt?.let { Time.minuteOfDay(it).toFloat() } },
                    labels = listOf(Time.short(days.first()), Time.short(days.last())),
                    goal = prefs.currentWake.toFloat(),
                    invert = true,
                    yLabel = { Time.hm(it.toInt()) },
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nächte", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val now = System.currentTimeMillis()
                    edit = SleepEntry(bedAt = Time.at(Time.today().minusDays(1), e.lightsOut()), wakeAt = Time.at(Time.today(), prefs.currentWake).coerceAtMost(now), wakeDate = Time.todayStr())
                }) { Icon(Icons.Filled.Add, "Nacht eintragen") }
            }
        }
        entries.forEach { s ->
            item {
                Row(Modifier.fillMaxWidth().clickable { edit = s }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val day = s.wakeAt?.let { Time.dayOf(it) } ?: s.bedAt?.let { Time.dayOf(it) }
                        Text(day?.let { "${Time.weekday(it)} ${Time.short(it)}" } ?: "")
                        Muted(
                            "${s.bedAt?.let { Time.hm(it) } ?: "?"} bis ${s.wakeAt?.let { Time.hm(it) } ?: "läuft"}" +
                                if (s.bedAt != null && s.wakeAt != null) " · ${Time.durMs(s.wakeAt - s.bedAt)}" else "",
                        )
                        if (s.note.isNotBlank()) Muted(s.note)
                    }
                    IconButton(onClick = { undo.run("Nacht gelöscht") { e.dao.deleteSleep(s); suspend { e.dao.upsertSleep(s) } } }) {
                        Icon(Icons.Filled.Delete, "Löschen")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    when (pick) {
        "current" -> TimeDialog(prefs.currentWake, "Aktuelle Aufstehzeit", { pick = null }) {
            prefs.currentWake = it
            prefs.lastShiftCheck = Time.today()
            pick = null
            resched()
        }
        "goal" -> TimeDialog(prefs.goalWake, "Endziel Aufstehzeit", { pick = null }) {
            prefs.goalWake = it
            pick = null
            resched()
        }
    }

    edit?.let { s -> SleepEditDialog(s, onDismiss = { edit = null }) { updated ->
        val before = s
        undo.run("Nacht gespeichert") {
            e.dao.upsertSleep(updated)
            suspend {
                if (before.id == 0L) e.dao.sleepOn(updated.wakeDate)?.let { e.dao.deleteSleep(it) } else e.dao.upsertSleep(before)
                Unit
            }
        }
        edit = null
    } }
}

@Composable
private fun SleepEditDialog(s: SleepEntry, onDismiss: () -> Unit, onSave: (SleepEntry) -> Unit) {
    var bed by remember { mutableStateOf(s.bedAt) }
    var wake by remember { mutableStateOf(s.wakeAt) }
    var which by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf(s.quality) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nacht bearbeiten") },
        text = {
            Column {
                TextButton(onClick = { which = "bed" }) { Text("Eingeschlafen: ${bed?.let { "${Time.short(Time.dayOf(it))} ${Time.hm(it)}" } ?: "–"}") }
                TextButton(onClick = { which = "wake" }) { Text("Aufgewacht: ${wake?.let { "${Time.short(Time.dayOf(it))} ${Time.hm(it)}" } ?: "–"}") }
                Text("Wie erholt?", style = MaterialTheme.typography.labelLarge)
                ScaleRow("Qualität", quality) { quality = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = wake
                onSave(s.copy(bedAt = bed, wakeAt = w, wakeDate = w?.let { Time.dayOf(it).toString() } ?: s.wakeDate, quality = quality))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    when (which) {
        "bed" -> TimeDialog(bed?.let { Time.minuteOfDay(it) } ?: 23 * 60, "Eingeschlafen", { which = null }) { m ->
            // Abends = Vortag des Aufwachens, nach Mitternacht = gleicher Tag.
            val wakeDay: LocalDate = wake?.let { Time.dayOf(it) } ?: Time.today()
            bed = if (m >= 12 * 60) Time.at(wakeDay.minusDays(1), m) else Time.at(wakeDay, m)
            which = null
        }
        "wake" -> TimeDialog(wake?.let { Time.minuteOfDay(it) } ?: 7 * 60, "Aufgewacht", { which = null }) { m ->
            val day: LocalDate = wake?.let { Time.dayOf(it) } ?: Time.today()
            wake = Time.at(day, m)
            which = null
        }
    }
}
