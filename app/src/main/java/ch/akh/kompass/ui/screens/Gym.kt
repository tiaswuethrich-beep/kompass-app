package ch.akh.kompass.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ch.akh.kompass.data.SetEntry
import ch.akh.kompass.data.Workout
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.Training
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.LineChart
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.go
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.ui.toDoubleLenient
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun kg(x: Double) = if (x % 1.0 == 0.0) x.roundToInt().toString() else "%.2f".format(x).trimEnd('0').trimEnd('.')

@Composable
fun GymScreen(nav: NavHostController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val workouts by e.dao.workouts().collectAsState(initial = emptyList())
    val exercises by e.dao.loggedExercises().collectAsState(initial = emptyList())
    val open = workouts.firstOrNull { it.endedAt == null }
    val weekStart = Time.startOfDay(Time.weekStart())
    val thisWeek = workouts.count { it.startedAt >= weekStart }
    val target = when {
        prefs.phase >= 3 -> 3
        prefs.phase >= 2 -> 2
        else -> 1
    }
    val suggested = Training.next(prefs.phase, workouts.filter { it.endedAt != null }.map { it.template })
    val rotation = Training.rotation(prefs.phase)

    fun start(template: String) {
        scope.launch {
            val id = withContext(Dispatchers.IO) { e.dao.insertWorkout(Workout(startedAt = System.currentTimeMillis(), template = template)) }
            nav.go("workout/$id")
        }
    }

    Screen(title = "Gym", onBack = onBack) {
        item {
            Section(title = "Diese Woche") {
                Row {
                    Stat("Trainings", "$thisWeek / $target", Modifier.weight(1f), color = if (thisWeek >= target) Good else MaterialTheme.colorScheme.onSurface)
                    Stat("Insgesamt", "${workouts.size}", Modifier.weight(1f))
                }
                if (prefs.phase < 2) Muted("Gym startet im Plan erst in Phase 2. Wenn du jetzt schon gehst: super, aber kein Muss.")
            }
        }
        if (open != null) item {
            Section(title = "Training läuft", subtitle = "${open.template} seit ${Time.hm(open.startedAt)}") {
                Button(onClick = { nav.go("workout/${open.id}") }, modifier = Modifier.fillMaxWidth()) { Text("Weitermachen") }
            }
        }
        item {
            Section(title = "Training starten", subtitle = "Vorschlag heute: $suggested\nReihenfolge: ${rotation.joinToString(" → ") { it.substringBefore(":") }}") {
                Training.templates.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontWeight = if (t.name == suggested) FontWeight.Bold else FontWeight.Normal,
                                color = if (t.name == suggested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Muted(t.description)
                        }
                        FilledTonalButton(onClick = { start(t.name) }) { Text("Start") }
                    }
                }
            }
        }
        item {
            Section(title = "Ausdauer für die RS", subtitle = "Zusätzlich zum Gym, je nach Phase") {
                Training.endurancePlan(prefs.phase).forEach { Muted("• $it") }
                TextButton(onClick = { nav.go("cardio") }) { Text("Ausdauer eintragen") }
            }
        }
        if (exercises.isNotEmpty()) item {
            Section(title = "Übungen und Rekorde") {
                exercises.forEach { ex ->
                    Text(ex, Modifier.fillMaxWidth().clickable { nav.go("exercise/${Uri.encode(ex)}") }.padding(vertical = 8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item { Text("Verlauf", style = MaterialTheme.typography.titleLarge) }
        items(workouts.filter { it.endedAt != null }.take(60), key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth().clickable { nav.go("workout/${w.id}") }.padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    val d = Time.dayOf(w.startedAt)
                    Text("${Time.weekday(d)} ${Time.short(d)} · ${w.template}")
                    Muted(w.endedAt?.let { Time.durMs(it - w.startedAt) } ?: "")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun WorkoutScreen(id: Long, nav: NavHostController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val workout by e.dao.workout(id).collectAsState(initial = null)
    val sets by e.dao.setsOf(id).collectAsState(initial = emptyList())
    val undo = ch.akh.kompass.ui.undo()
    val w = workout
    val template = Training.templates.firstOrNull { it.name == w?.template }
    val extra = remember { mutableStateListOf<String>() }
    val names = ((template?.exercises?.map { it.name } ?: emptyList()) + sets.map { it.exercise } + extra).distinct()
    var addOpen by remember { mutableStateOf(false) }
    var rest by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(rest) {
        if (rest > 0) {
            delay(1000)
            rest -= 1
        }
    }

    Screen(
        title = w?.template ?: "Training",
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Training löschen") }
        },
    ) {
        if (rest > 0) item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pause: ${rest / 60}:${"%02d".format(rest % 60)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { rest += 30 }) { Text("+30 s") }
                    IconButton(onClick = { rest = 0 }) { Icon(Icons.Filled.Close, "Pause beenden") }
                }
            }
        }
        names.forEach { name ->
            item(key = name) {
                ExerciseCard(
                    name = name,
                    workoutId = id,
                    sets = sets.filter { it.exercise == name },
                    e = e,
                    onSave = { reps, weight ->
                        scope.launch(Dispatchers.IO) {
                            val idx = sets.count { it.exercise == name } + 1
                            e.dao.insertSet(SetEntry(workoutId = id, exercise = name, idx = idx, reps = reps, weight = weight))
                        }
                        rest = 90
                    },
                    onDelete = { s -> undo.run("Satz gelöscht") { e.dao.deleteSet(s); suspend { e.dao.insertSet(s); Unit } } },
                )
            }
        }
        item {
            Column {
                OutlinedButton(onClick = { addOpen = true }) { Text("Übung hinzufügen") }
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                    Training.allExercises.filter { it !in names }.forEach { ex ->
                        DropdownMenuItem(text = { Text(ex) }, onClick = {
                            extra += ex
                            addOpen = false
                        })
                    }
                }
            }
        }
        item {
            if (w != null && w.endedAt == null) {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            e.dao.upsertWorkout(w.copy(endedAt = System.currentTimeMillis()))
                            e.syncAuto(Time.dayOf(w.startedAt))
                        }
                        onBack()
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Training beenden") }
            } else if (w != null) {
                Muted("Beendet. Dauer ${w.endedAt?.let { Time.durMs(it - w.startedAt) } ?: ""}.")
                TextButton(onClick = { scope.launch(Dispatchers.IO) { e.dao.upsertWorkout(w.copy(endedAt = null)) } }) {
                    Text("Wieder öffnen")
                }
            }
        }
    }

    if (confirmDelete && w != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Training löschen?") },
            text = { Text("Alle Sätze dieses Trainings werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    undo.run("Training gelöscht") {
                        val saved = e.dao.setsOfOnce(w.id)
                        e.dao.deleteSetsOf(w.id)
                        e.dao.deleteWorkout(w)
                        suspend {
                            e.dao.upsertWorkout(w)
                            e.dao.insertSets(saved)
                        }
                    }
                    onBack()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ExerciseCard(
    name: String,
    workoutId: Long,
    sets: List<SetEntry>,
    e: Engine,
    onSave: (Int, Double) -> Unit,
    onDelete: (SetEntry) -> Unit,
) {
    val last by produceState(emptyList<SetEntry>(), name) {
        value = withContext(Dispatchers.IO) { e.dao.lastSetsFor(name, workoutId) }
    }
    val plan = Training.plannedFor(name)
    var reps by remember(name) { mutableStateOf("") }
    var weight by remember(name) { mutableStateOf("") }
    LaunchedEffect(last, sets.size) {
        val ref = sets.lastOrNull() ?: last.firstOrNull()
        if (ref != null) {
            if (weight.isBlank()) weight = kg(ref.weight)
            if (reps.isBlank()) reps = ref.reps.toString()
        } else if (plan != null && reps.isBlank()) {
            reps = plan.repsHigh.toString()
        }
    }
    Section(
        title = name,
        subtitle = plan?.let { "${it.sets} Sätze à ${it.repsLow} bis ${it.repsHigh}" + if (it.note.isNotBlank()) " · ${it.note}" else "" },
    ) {
        Muted(Training.suggestion(name, last))
        Spacer(Modifier.height(8.dp))
        sets.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Satz ${s.idx}", Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${s.reps} x ${kg(s.weight)} kg", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = { onDelete(s) }) { Icon(Icons.Filled.Close, "Satz löschen") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(reps, { reps = it }, "Wdh.", Modifier.weight(1f))
            NumberField(weight, { weight = it }, "kg", Modifier.weight(1f), decimal = true)
            Button(
                onClick = { onSave(reps.toIntOrNull() ?: 0, weight.toDoubleLenient() ?: 0.0) },
                enabled = (reps.toIntOrNull() ?: 0) > 0,
            ) { Text("Satz") }
        }
    }
}

@Composable
fun ExerciseScreen(name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Engine.get(ctx) }
    val sets by e.dao.setsFor(name).collectAsState(initial = emptyList())
    val byDay = sets.groupBy { Time.dayOf(it.at) }.toSortedMap()
    val bestPerDay = byDay.mapValues { (_, l) -> l.maxOf { Training.e1rm(it.weight, it.reps) } }
    val best = sets.maxByOrNull { Training.e1rm(it.weight, it.reps) }
    val heaviest = sets.maxByOrNull { it.weight }

    Screen(title = name, onBack = onBack) {
        item {
            Section(title = "Rekorde") {
                Row {
                    Stat("Schwerster Satz", heaviest?.let { "${kg(it.weight)} kg x ${it.reps}" } ?: "–", Modifier.weight(1f))
                    Stat("Geschätztes Maximum", best?.let { "${kg(Training.e1rm(it.weight, it.reps).roundToInt().toDouble())} kg" } ?: "–", Modifier.weight(1f))
                }
            }
        }
        item {
            Section(title = "Entwicklung", subtitle = "Geschätztes Maximum pro Training") {
                LineChart(
                    values = bestPerDay.values.map { it.toFloat() },
                    labels = bestPerDay.keys.map { Time.short(it) },
                    yLabel = { "${it.roundToInt()} kg" },
                )
            }
        }
        byDay.entries.reversed().forEach { (d, l) ->
            item {
                Section(title = "${Time.weekday(d)} ${Time.short(d)}") {
                    Muted(l.sortedBy { it.idx }.joinToString("   ") { "${it.reps} x ${kg(it.weight)}" })
                }
            }
        }
    }
}
