package ch.akh.kompass.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ch.akh.kompass.data.CardioEntry
import ch.akh.kompass.data.SnusEntry
import ch.akh.kompass.data.SubstanceEntry
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.BarChart
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.go
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.ui.toDoubleLenient
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// ======================= Ausdauer =======================

val cardioTypes = listOf("Joggen", "Bike", "Schwimmen", "Natur", "Wandern", "Marsch", "Spaziergang")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val all by e.dao.cardio().collectAsState(initial = emptyList())
    var type by remember { mutableStateOf("Joggen") }
    var minutes by remember { mutableStateOf("30") }
    var km by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var yesterday by remember { mutableStateOf(false) }

    val weeks = (7 downTo 0).map { Time.weekStart().minusWeeks(it.toLong()) }
    val perWeek = weeks.map { ws ->
        val we = ws.plusDays(7).toString()
        all.filter { it.date >= ws.toString() && it.date < we }.sumOf { it.minutes }.toFloat()
    }

    Screen(title = "Ausdauer und Natur", onBack = onBack) {
        item {
            Section(title = "Eintragen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cardioTypes.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minutes, { minutes = it }, "Minuten", Modifier.weight(1f))
                    NumberField(km, { km = it }, "km (optional)", Modifier.weight(1f), decimal = true)
                }
                TextInput(note, { note = it }, "Notiz (optional)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text("Heute") })
                    Spacer(Modifier.size(6.dp))
                    FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text("Gestern") })
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val d = if (yesterday) Time.today().minusDays(1) else Time.today()
                        scope.launch(Dispatchers.IO) {
                            e.dao.insertCardio(CardioEntry(date = d.toString(), type = type, minutes = minutes.toIntOrNull() ?: 0, km = km.toDoubleLenient() ?: 0.0, note = note))
                            e.syncAuto(d)
                        }
                        note = ""
                    }, enabled = (minutes.toIntOrNull() ?: 0) > 0) { Text("Speichern") }
                }
            }
        }
        item {
            Section(title = "Minuten pro Woche") {
                BarChart(perWeek, weeks.map { Time.short(it) })
            }
        }
        items(all.take(80), key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${c.type} · ${Time.dur(c.minutes)}${if (c.km > 0) " · ${"%.1f".format(c.km)} km" else ""}")
                    Muted(Time.short(LocalDate.parse(c.date)) + if (c.note.isNotBlank()) " · ${c.note}" else "")
                }
                IconButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.deleteCardio(c) } }) { Icon(Icons.Filled.Delete, "Löschen") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ======================= Snus =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnusScreen(nav: NavHostController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val today = Time.today()
    val count by e.dao.snusCountFlow(0, Time.startOfDay(today), Time.endOfDay(today)).collectAsState(initial = 0)
    val perDay by e.dao.snusPerDay(Time.startOfDay(today.minusDays(29))).collectAsState(initial = emptyList())
    val cravings by e.dao.snusCountFlow(1, 0, Long.MAX_VALUE).collectAsState(initial = 0)
    val lastPouch by e.dao.lastPouchFlow().collectAsState(initial = null)
    val saved by produceState(0.0, count, prefs.version.value) {
        value = withContext(Dispatchers.IO) { e.moneySaved() }
    }
    var dateEdit by remember { mutableStateOf<String?>(null) }
    var baseline by remember { mutableStateOf(prefs.snusBaseline.toString()) }
    var perCan by remember { mutableStateOf(prefs.pouchesPerCan.toString()) }
    var price by remember { mutableStateOf("%.2f".format(prefs.canPrice)) }

    val limit = e.snusLimit()
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val map = perDay.associate { it.day to it.n }
    val values = days.map { (map[it.toString()] ?: 0).toFloat() }
    val avg7 = values.takeLast(7).average()
    val freeDays = lastPouch?.let { ChronoUnit.DAYS.between(Time.dayOf(it), today) }

    Screen(title = "Snus", onBack = onBack) {
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat("Heute", if (limit != null) "$count / $limit" else "$count", Modifier.weight(1f),
                        color = if (limit != null && count > limit) Bad else MaterialTheme.colorScheme.onSurface)
                    Stat("Schnitt 7 Tage", "%.1f".format(avg7), Modifier.weight(1f))
                    Stat("Cravings besiegt", "$cravings", Modifier.weight(1f), color = Good)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) { e.dao.insertSnus(SnusEntry(at = System.currentTimeMillis())); e.syncAuto() }
                    }, modifier = Modifier.weight(1f)) { Text("+1 Beutel") }
                    OutlinedButton(onClick = { nav.go("craving") }, modifier = Modifier.weight(1f)) { Text("Craving") }
                }
                TextButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.undoLastPouch(); e.syncAuto() } }) { Text("Letzten Beutel rückgängig") }
            }
        }
        if (prefs.phase >= 4 || (freeDays != null && freeDays >= 1)) item {
            Section(title = "Nikotinfrei") {
                Stat("Seit dem letzten Beutel", freeDays?.let { "$it Tage" } ?: "–", sub = lastPouch?.let { "zuletzt ${Time.short(Time.dayOf(it))}" })
            }
        }
        item {
            Section(title = "Letzte 30 Tage", subtitle = if (limit != null) "Gestrichelt: dein heutiges Limit" else "Noch ohne Limit, nur zählen") {
                BarChart(
                    values, days.mapIndexed { i, d -> if (i % 5 == 0) Time.short(d) else "" },
                    goal = limit?.toFloat(),
                    colorFor = { i, v ->
                        val l = e.snusLimit(days[i])
                        if (l != null && v > l) Bad else androidx.compose.ui.graphics.Color.Unspecified
                    },
                )
            }
        }
        item {
            Section(title = "Abbauplan") {
                Text("Start Abbau: ${Time.full(prefs.taperStart)}", Modifier.padding(vertical = 2.dp))
                TextButton(onClick = { dateEdit = "taper" }) { Text("Ändern") }
                Text("Rauchfrei-Tag: ${Time.full(prefs.quitDate)}", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { dateEdit = "quit" }) { Text("Ändern") }
                Muted("Alle 2 Wochen sinkt das Limit von ${prefs.snusBaseline} Richtung 4 Beutel, am Rauchfrei-Tag auf 0.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(baseline, { baseline = it }, "Ausgangswert/Tag", Modifier.weight(1f))
                    NumberField(perCan, { perCan = it }, "Beutel/Dose", Modifier.weight(1f))
                    NumberField(price, { price = it }, "CHF/Dose", Modifier.weight(1f), decimal = true)
                }
                TextButton(onClick = {
                    baseline.toIntOrNull()?.let { prefs.snusBaseline = it.coerceAtLeast(1) }
                    perCan.toIntOrNull()?.let { prefs.pouchesPerCan = it.coerceAtLeast(1) }
                    price.toDoubleLenient()?.let { prefs.canPrice = it.toFloat() }
                }) { Text("Werte speichern") }
            }
        }
        item {
            Section(title = "Gespart seit Abbau-Start") {
                Stat("", "CHF %.0f".format(saved), sub = "verglichen mit ${prefs.snusBaseline} Beuteln pro Tag")
            }
        }
        item {
            Section(title = "Hilfe") {
                Muted("• Craving dauert 3 bis 5 Minuten. Der Craving-Knopf hilft dir durch.")
                Muted("• Nikotinersatz (Kaugummi, Lutschtabletten) ist erlaubt. Frag in der Apotheke.")
                Muted("• Rauchstopplinie, kostenlos und auch für Snus: 0848 000 181 (bitte auf stop-tabac.ch prüfen).")
                Muted("• Ein Ausrutscher ist kein Rückfall. Der nächste Beutel bleibt einfach weg.")
            }
        }
    }

    dateEdit?.let { which ->
        val init = if (which == "quit") prefs.quitDate else prefs.taperStart
        val state = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateEdit = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "quit") prefs.quitDate = d else prefs.taperStart = d
                    }
                    dateEdit = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateEdit = null }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun CravingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    var left by remember { mutableIntStateOf(240) }
    LaunchedEffect(left) {
        if (left > 0) {
            delay(1000)
            left -= 1
        }
    }
    val breath = rememberInfiniteTransition(label = "atem")
    val s by breath.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "atem",
    )
    val ideas = listOf(
        "Ein grosses Glas kaltes Wasser trinken.",
        "20 Kniebeugen oder Liegestütze.",
        "Kaugummi oder Pfefferminz.",
        "Kurz raus an die frische Luft.",
        "Jemandem schreiben.",
        "Gesicht mit kaltem Wasser waschen.",
    )
    Screen(title = "Craving", onBack = onBack) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (left > 0) "Warte es ab" else "Geschafft. Es ist vorbei.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Muted("Das Verlangen steigt, erreicht einen Höhepunkt und fällt wieder. Meist nach 3 bis 5 Minuten.")
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(200.dp).scale(s).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${left / 60}:${"%02d".format(left % 60)}", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (s > 0.8f) "ausatmen" else "einatmen", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        item {
            Section(title = "Während du wartest") {
                ideas.forEach { Muted("• $it") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { e.dao.insertSnus(SnusEntry(at = System.currentTimeMillis(), type = 1)) }
                        onBack()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Überstanden") }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { e.dao.insertSnus(SnusEntry(at = System.currentTimeMillis(), type = 0)); e.syncAuto() }
                        onBack()
                    }
                }, modifier = Modifier.weight(1f)) { Text("Doch genommen") }
            }
        }
        item { Muted("Doch genommen ist okay. Ehrlich eintragen ist wichtiger als perfekt sein.") }
    }
}

// ======================= Konsum =======================

private val substanceTypes = listOf("Kaffee", "Energy", "Alkohol", "Weed")

@Composable
fun SubstanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val since = Time.startOfDay(Time.today().minusDays(27))
    val list by e.dao.substancesSince(since).collectAsState(initial = emptyList())
    val weekStart = Time.startOfDay(Time.weekStart())
    Screen(title = "Konsum", onBack = onBack) {
        item {
            Section(title = "Eintragen", subtitle = "Nur zählen, nicht verurteilen. Ab Phase 1 kein Koffein nach 14 Uhr, ab Phase 4 gar keins.") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    substanceTypes.forEach { t ->
                        OutlinedButton(onClick = {
                            scope.launch(Dispatchers.IO) { e.dao.insertSubstance(SubstanceEntry(at = System.currentTimeMillis(), type = t)) }
                        }, modifier = Modifier.weight(1f)) { Text(t, maxLines = 1) }
                    }
                }
            }
        }
        item {
            Section(title = "Diese Woche") {
                Row {
                    substanceTypes.forEach { t ->
                        Stat(t, "${list.count { it.type == t && it.at >= weekStart }}", Modifier.weight(1f))
                    }
                }
                val late = list.count { (it.type == "Kaffee" || it.type == "Energy") && it.at >= weekStart && Time.minuteOfDay(it.at) >= 14 * 60 }
                if (late > 0) {
                    Spacer(Modifier.height(6.dp))
                    Muted("$late mal Koffein nach 14 Uhr diese Woche. Das schiebt deinen Schlaf nach hinten.")
                }
            }
        }
        items(list, key = { it.id }) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.type, Modifier.weight(1f))
                Muted("${Time.short(Time.dayOf(s.at))} ${Time.hm(s.at)}")
                IconButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.deleteSubstance(s) } }) { Icon(Icons.Filled.Delete, "Löschen") }
            }
        }
    }
}
