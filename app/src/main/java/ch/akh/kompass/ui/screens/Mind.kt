package ch.akh.kompass.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.data.Book
import ch.akh.kompass.data.CustomHabit
import ch.akh.kompass.data.FocusSession
import ch.akh.kompass.data.MoodEntry
import ch.akh.kompass.data.ReadingSession
import ch.akh.kompass.data.WeeklyReview
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.ui.BarChart
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.LineChart
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Progress
import ch.akh.kompass.ui.Ring
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.TimeDialog
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================= Bücher =======================

@Composable
fun BooksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val books by e.dao.books().collectAsState(initial = emptyList())
    val year = Time.today().year
    val reading by e.dao.readingSince(Time.today().minusDays(27).toString()).collectAsState(initial = emptyList())
    var add by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Book?>(null) }

    val days = (27 downTo 0).map { Time.today().minusDays(it.toLong()) }
    val pagesPerDay = days.map { d -> reading.filter { it.date == d.toString() }.sumOf { it.pages }.toFloat() }

    Screen(title = "Bücher", onBack = onBack, fab = {
        FloatingActionButton(onClick = { add = true }) { Icon(Icons.Filled.Add, "Buch hinzufügen") }
    }) {
        item {
            Section {
                Row {
                    Stat("Fertig $year", "${books.count { it.status == 2 && (it.finishedAt ?: "").startsWith(year.toString()) }}", Modifier.weight(1f))
                    Stat("Seiten 7 Tage", "${pagesPerDay.takeLast(7).sum().toInt()}", Modifier.weight(1f))
                    Stat("Minuten 7 Tage", "${reading.filter { it.date >= Time.today().minusDays(6).toString() }.sumOf { it.minutes }}", Modifier.weight(1f))
                }
            }
        }
        item {
            Section(title = "Seiten pro Tag") { BarChart(pagesPerDay, days.mapIndexed { i, d -> if (i % 7 == 0) Time.short(d) else "" }) }
        }
        listOf(1 to "Lese ich gerade", 0 to "Will ich lesen", 2 to "Gelesen").forEach { (st, label) ->
            val group = books.filter { it.status == st }
            if (group.isNotEmpty()) {
                item { Text(label, style = MaterialTheme.typography.titleLarge) }
                items(group, key = { it.id }) { b ->
                    Section(title = b.title, subtitle = b.author.ifBlank { null }) {
                        if (b.pages > 0) {
                            Progress(b.current.toFloat() / b.pages)
                            Muted("Seite ${b.current} von ${b.pages}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.status != 2) Button(onClick = { session = b }) { Text("Gelesen eintragen") }
                            if (b.status == 0) OutlinedButton(onClick = {
                                scope.launch(Dispatchers.IO) { e.dao.upsertBook(b.copy(status = 1, startedAt = Time.todayStr())) }
                            }) { Text("Anfangen") }
                            IconButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.deleteBook(b) } }) { Icon(Icons.Filled.Delete, "Löschen") }
                        }
                    }
                }
            }
        }
        if (books.isEmpty()) item {
            Section(title = "Noch keine Bücher") {
                Muted("Füg ein Buch hinzu, das dich wirklich interessiert. Für den Start eher dünn und packend als wichtig und schwer.")
            }
        }
    }

    if (add) {
        var title by remember { mutableStateOf("") }
        var author by remember { mutableStateOf("") }
        var pages by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Buch hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(title, { title = it }, "Titel")
                    TextInput(author, { author = it }, "Autor")
                    NumberField(pages, { pages = it }, "Seiten")
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        e.dao.insertBook(Book(title = title.trim(), author = author.trim(), pages = pages.toIntOrNull() ?: 0,
                            status = if (books.none { it.status == 1 }) 1 else 0,
                            startedAt = if (books.none { it.status == 1 }) Time.todayStr() else null))
                    }
                    add = false
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } },
        )
    }

    session?.let { b ->
        var page by remember(b.id) { mutableStateOf("") }
        var minutes by remember(b.id) { mutableStateOf("15") }
        AlertDialog(
            onDismissRequest = { session = null },
            title = { Text(b.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(page, { page = it }, "Jetzt auf Seite (bisher ${b.current})")
                    NumberField(minutes, { minutes = it }, "Minuten gelesen")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newPage = page.toIntOrNull() ?: b.current
                    val read = (newPage - b.current).coerceAtLeast(0)
                    val finished = b.pages in 1..newPage
                    scope.launch(Dispatchers.IO) {
                        e.dao.insertReading(ReadingSession(bookId = b.id, date = Time.todayStr(), minutes = minutes.toIntOrNull() ?: 0, pages = read))
                        e.dao.upsertBook(b.copy(current = newPage, status = if (finished) 2 else 1,
                            finishedAt = if (finished) Time.todayStr() else b.finishedAt, startedAt = b.startedAt ?: Time.todayStr()))
                        e.syncAuto()
                    }
                    session = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { session = null }) { Text("Abbrechen") } },
        )
    }
}

// ======================= Fokus =======================

@Composable
fun FocusScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val today = Time.todayStr()
    val sessions by e.dao.focusSince(Time.today().minusDays(6).toString()).collectAsState(initial = emptyList())
    val todayMin = sessions.filter { it.date == today }.sumOf { it.minutes }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var label by remember { mutableStateOf(prefs.focusLabel) }
    val running = prefs.focusEndsAt > now
    LaunchedEffect(running, prefs.focusEndsAt) {
        while (prefs.focusEndsAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val target = when {
        prefs.phase >= 5 -> 480
        prefs.phase >= 4 -> 360
        prefs.phase >= 3 -> 240
        else -> 100
    }

    fun start(min: Int) {
        prefs.focusMinutes = min
        prefs.focusLabel = label
        val end = System.currentTimeMillis() + min * 60_000L
        prefs.focusEndsAt = end
        Scheduler.scheduleFocusEnd(ctx, end)
        now = System.currentTimeMillis()
    }

    Screen(title = "Fokus", onBack = onBack) {
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(todayMin.toFloat() / target, "${todayMin / 60}:${"%02d".format(todayMin % 60)}\nvon ${target / 60} h")
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("Heute fokussiert", style = MaterialTheme.typography.titleMedium)
                        Muted("Dein Weg zu 8 Stunden: Phase 2 rund 1,5 h, Phase 3 4 h, Phase 4 6 h, Phase 5 8 h.")
                    }
                }
            }
        }
        item {
            Section(title = if (running) "Block läuft" else "Neuen Block starten") {
                if (running) {
                    val left = ((prefs.focusEndsAt - now) / 1000).toInt().coerceAtLeast(0)
                    Text("${left / 60}:${"%02d".format(left % 60)}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    if (prefs.focusLabel.isNotBlank()) Muted(prefs.focusLabel)
                    Spacer(Modifier.height(8.dp))
                    Muted("Handy umdrehen, Nicht-stören an. Die App meldet sich, wenn der Block fertig ist.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            // Abbrechen: die bisherige Zeit zählt trotzdem, wenn es mindestens 10 Minuten waren.
                            val start = prefs.focusEndsAt - prefs.focusMinutes * 60_000L
                            val done = ((System.currentTimeMillis() - start) / 60_000L).toInt()
                            prefs.focusEndsAt = 0
                            Scheduler.scheduleFocusEnd(ctx, 0)
                            if (done >= 10) scope.launch(Dispatchers.IO) {
                                e.dao.insertFocus(FocusSession(date = today, startedAt = start, minutes = done, label = prefs.focusLabel))
                                e.syncAuto()
                            }
                        }) { Text("Beenden") }
                    }
                } else {
                    TextInput(label, { label = it }, "Woran arbeitest du? (optional)")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 50, 90).forEach { m ->
                            Button(onClick = { start(m) }, modifier = Modifier.weight(1f)) { Text("$m min") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Text("Nicht-stören-Einstellungen öffnen")
                    }
                }
            }
        }
        item {
            val days = (6 downTo 0).map { Time.today().minusDays(it.toLong()) }
            Section(title = "Letzte 7 Tage", subtitle = "Stunden") {
                BarChart(
                    days.map { d -> sessions.filter { it.date == d.toString() }.sumOf { it.minutes } / 60f },
                    days.map { Time.weekday(it) },
                    goal = target / 60f,
                )
            }
        }
        items(sessions.take(20), key = { it.id }) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${Time.weekday(Time.dayOf(s.startedAt))} ${Time.hm(s.startedAt)}", Modifier.weight(1f))
                Muted("${s.minutes} min${if (s.label.isNotBlank()) " · ${s.label}" else ""}")
            }
        }
    }
}

// ======================= Stimmung =======================

@Composable
fun MoodScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val today = Time.todayStr()
    val moods by e.dao.moodSince(Time.today().minusDays(29).toString()).collectAsState(initial = emptyList())
    val reviews by e.dao.reviews().collectAsState(initial = emptyList())
    val todayMood = moods.firstOrNull { it.date == today }
    var mo by remember(todayMood) { mutableStateOf(todayMood?.mood ?: 0) }
    var en by remember(todayMood) { mutableStateOf(todayMood?.energy ?: 0) }
    var jo by remember(todayMood) { mutableStateOf(todayMood?.joy ?: 0) }
    var social by remember(todayMood) { mutableStateOf(todayMood?.social ?: false) }
    var note by remember(todayMood) { mutableStateOf(todayMood?.note ?: "") }

    val ws = Time.weekStart().toString()
    val thisReview = reviews.firstOrNull { it.weekStart == ws }
    var win by remember(thisReview) { mutableStateOf(thisReview?.win ?: "") }
    var lesson by remember(thisReview) { mutableStateOf(thisReview?.lesson ?: "") }
    var next by remember(thisReview) { mutableStateOf(thisReview?.nextFocus ?: "") }
    var joyful by remember(thisReview) { mutableStateOf(thisReview?.joyful ?: "") }

    val last14 = moods.filter { it.date >= Time.today().minusDays(13).toString() }
    val lowJoy = last14.size >= 10 && last14.map { it.joy }.average() <= 2.0

    Screen(title = "Stimmung", onBack = onBack) {
        item {
            Section(title = "Heute") {
                ScaleRow("Stimmung", mo) { mo = it }
                ScaleRow("Energie", en) { en = it }
                ScaleRow("Freude", jo) { jo = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zeit mit Menschen, die mir guttun", Modifier.weight(1f))
                    Switch(checked = social, onCheckedChange = { social = it })
                }
                TextInput(note, { note = it }, "Was war heute gut? (ein Satz reicht)", singleLine = false)
                Spacer(Modifier.height(8.dp))
                Button(enabled = mo > 0 && en > 0 && jo > 0, onClick = {
                    scope.launch(Dispatchers.IO) {
                        e.dao.upsertMood(MoodEntry(today, mo, en, jo, social, note.trim()))
                        e.syncAuto()
                    }
                }) { Text(if (todayMood == null) "Speichern" else "Aktualisieren") }
            }
        }
        if (lowJoy) item {
            Section(title = "Kurz ehrlich") {
                Text("Deine Freude liegt seit zwei Wochen meist bei 2 oder tiefer.")
                Spacer(Modifier.height(6.dp))
                Muted("Das ist kein Versagen und nichts, was du allein mit Disziplin lösen musst. Ein Termin beim Hausarzt ist ein guter, normaler nächster Schritt. Zeig dort einfach diese Werte.")
                Muted("Wenn es dir richtig schlecht geht: Die Dargebotene Hand, Telefon 143, rund um die Uhr.")
            }
        }
        item {
            val days = (29 downTo 0).map { Time.today().minusDays(it.toLong()) }
            val map = moods.associateBy { it.date }
            Section(title = "30 Tage", subtitle = "Stimmung, Energie und Freude") {
                Text("Stimmung", style = MaterialTheme.typography.labelLarge)
                LineChart(days.map { map[it.toString()]?.mood?.toFloat() }, listOf(Time.short(days.first()), Time.short(days.last())), min = 1f, max = 5f, height = 80.dp)
                Text("Freude", style = MaterialTheme.typography.labelLarge)
                LineChart(days.map { map[it.toString()]?.joy?.toFloat() }, listOf(Time.short(days.first()), Time.short(days.last())), min = 1f, max = 5f, height = 80.dp)
                Text("Energie", style = MaterialTheme.typography.labelLarge)
                LineChart(days.map { map[it.toString()]?.energy?.toFloat() }, listOf(Time.short(days.first()), Time.short(days.last())), min = 1f, max = 5f, height = 80.dp)
            }
        }
        item {
            Section(title = "Wochenrückblick", subtitle = "Woche ab ${Time.short(Time.weekStart())}") {
                TextInput(win, { win = it }, "Was lief diese Woche gut?", singleLine = false)
                TextInput(joyful, { joyful = it }, "Was hat mir Freude gemacht?", singleLine = false)
                TextInput(lesson, { lesson = it }, "Was nehme ich mit?", singleLine = false)
                TextInput(next, { next = it }, "Worauf konzentriere ich mich nächste Woche?", singleLine = false)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch(Dispatchers.IO) { e.dao.upsertReview(WeeklyReview(ws, win.trim(), lesson.trim(), next.trim(), joyful.trim())) }
                }) { Text("Rückblick speichern") }
            }
        }
        items(reviews.filter { it.weekStart != ws }.take(12), key = { it.weekStart }) { r ->
            Section(title = "Woche ab ${Time.short(java.time.LocalDate.parse(r.weekStart))}") {
                if (r.win.isNotBlank()) Muted("Gut: ${r.win}")
                if (r.joyful.isNotBlank()) Muted("Freude: ${r.joyful}")
                if (r.lesson.isNotBlank()) Muted("Gelernt: ${r.lesson}")
                if (r.nextFocus.isNotBlank()) Muted("Fokus: ${r.nextFocus}")
            }
        }
    }
}

// ======================= Gewohnheiten verwalten =======================

@Composable
fun HabitsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val customs by e.dao.customHabits().collectAsState(initial = emptyList())
    val logs by e.dao.habitsSinceFlow(Time.today().minusDays(27).toString()).collectAsState(initial = emptyList())
    var timeFor by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }

    fun resched() = scope.launch(Dispatchers.IO) { Scheduler.scheduleReminders(ctx) }

    Screen(title = "Gewohnheiten", onBack = onBack, fab = {
        FloatingActionButton(onClick = { add = true }) { Icon(Icons.Filled.Add, "Eigene Gewohnheit") }
    }) {
        item { Muted("Antippen der Uhrzeit ändert die Erinnerung. Mit dem Schalter kannst du eine Gewohnheit pausieren.") }
        val all = e.builtInItems() + customs.map { e.customItem(it) }
        items(all, key = { it.key }) { h ->
            val done28 = logs.count { it.key == h.key && it.level >= 1 }
            val active = if (h.custom != null) h.custom.active else !prefs.habitDisabled(h.key)
            Section(title = h.title, subtitle = if (h.daily) "täglich" else "${h.perWeek}x pro Woche${h.weekday?.let { " (${Time.weekday(Time.today().with(it))})" } ?: ""}") {
                Muted("Mindestens: ${h.min}")
                Muted("Ganz: ${h.full}")
                if (h.why.isNotBlank()) Muted("Warum: ${h.why}")
                Spacer(Modifier.height(6.dp))
                val possible = if (h.daily) 28 else h.perWeek * 4
                Progress(done28.toFloat() / possible, color = Good)
                Muted("$done28 von $possible in 4 Wochen")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { timeFor = h.key }) {
                        Text(h.reminderMinute?.let { "Erinnerung ${Time.hm(it)}" } ?: "Keine Erinnerung")
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { on ->
                        if (h.custom != null) scope.launch(Dispatchers.IO) { e.dao.upsertCustom(h.custom.copy(active = on)) }
                        else prefs.setHabitDisabled(h.key, !on)
                        resched()
                    })
                    if (h.custom != null) IconButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.deleteCustom(h.custom) } }) {
                        Icon(Icons.Filled.Delete, "Löschen")
                    }
                }
            }
        }
        item {
            val upcoming = PhasePlan.habits.filter { it.introPhase > prefs.phase }
            if (upcoming.isNotEmpty()) Section(title = "Kommt später") {
                upcoming.forEach { Muted("Phase ${it.introPhase}: ${it.title}") }
            }
        }
    }

    timeFor?.let { key ->
        val item = (e.builtInItems() + customs.map { e.customItem(it) }).firstOrNull { it.key == key }
        TimeDialog(item?.reminderMinute ?: (18 * 60), "Erinnerung", { timeFor = null }) { m ->
            val c = item?.custom
            if (c != null) scope.launch(Dispatchers.IO) { e.dao.upsertCustom(c.copy(reminderMinute = m)) }
            else prefs.setHabitReminder(key, m)
            timeFor = null
            resched()
        }
    }

    if (add) {
        var title by remember { mutableStateOf("") }
        var min by remember { mutableStateOf("") }
        var full by remember { mutableStateOf("") }
        var perWeek by remember { mutableStateOf(7) }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Eigene Gewohnheit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(title, { title = it }, "Name")
                    TextInput(min, { min = it }, "Mindestversion (sehr klein)")
                    TextInput(full, { full = it }, "Ganze Version")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(7, 5, 3, 1).forEach { n ->
                            FilterChip(selected = perWeek == n, onClick = { perWeek = n }, label = { Text(if (n == 7) "täglich" else "${n}x/W") })
                        }
                    }
                    Muted("Tipp: Neue Gewohnheiten erst, wenn die bisherigen sitzen.")
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && min.isNotBlank(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        e.dao.insertCustom(CustomHabit(title = title.trim(), minText = min.trim(), fullText = full.ifBlank { min }.trim(), perWeek = perWeek))
                    }
                    add = false
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Abbrechen") } },
        )
    }
}
