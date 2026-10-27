package ch.akh.kompass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavHostController
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.plan.PhaseStatus
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Progress
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun PlanScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    val logs by e.dao.habitsSinceFlow(Time.today().minusDays(30).toString()).collectAsState(initial = emptyList())
    val status by produceState<PhaseStatus?>(null, prefs.phase, logs) {
        value = withContext(Dispatchers.IO) { e.phaseStatus() }
    }
    var confirm by remember { mutableStateOf<String?>(null) }

    val rsDate = LocalDate.of(2027, 8, 1)
    val total = ChronoUnit.DAYS.between(prefs.planStart, rsDate).toFloat().coerceAtLeast(1f)
    val done = ChronoUnit.DAYS.between(prefs.planStart, Time.today()).toFloat()

    fun move(forward: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (forward) e.advancePhase() else e.previousPhase()
                Scheduler.rescheduleAll(ctx)
            }
        }
    }

    Screen(title = "Dein Plan") {
        aiItem(ch.akh.kompass.ai.AiFeature.PLAN)
        item {
            Section(title = "Bis zur RS", subtitle = "${ChronoUnit.DAYS.between(Time.today(), rsDate)} Tage bis August 2027") {
                Progress(done / total)
                Spacer(Modifier.height(6.dp))
                Muted("Kein Sprint. Eine Sache nach der anderen, und Rückschritte sind eingeplant.")
            }
        }

        status?.let { s ->
            item {
                Section(title = "Jetzt: Phase ${s.phase.index} · ${s.phase.name}", subtitle = "Tag ${s.daysIn}") {
                    Text(s.phase.goal)
                    Spacer(Modifier.height(8.dp))
                    Muted("Warum: ${s.phase.why}")
                    Spacer(Modifier.height(12.dp))
                    Text("Meilenstein", style = MaterialTheme.typography.titleSmall)
                    Muted(s.phase.milestone)
                    Spacer(Modifier.height(8.dp))
                    s.criteria.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Icon(
                                if (c.met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                                tint = if (c.met) Good else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.text)
                                if (c.detail.isNotBlank()) Muted(c.detail)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (s.phase.index < PhasePlan.phases.lastIndex) {
                        if (s.ready) {
                            Button(onClick = { confirm = "next" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Phase ${s.phase.index + 1} starten")
                            }
                        } else {
                            Muted("Sobald alles erfüllt ist, kannst du die nächste Phase starten. Kein Datum zwingt dich.")
                            TextButton(onClick = { confirm = "force" }) { Text("Trotzdem schon weiter") }
                        }
                    }
                    if (s.phase.index > 0) {
                        TextButton(onClick = { confirm = "back" }) { Text("Eine Phase zurück (z. B. nach Krankheit)") }
                    }
                }
            }
        }

        item { Text("Alle Phasen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }

        PhasePlan.phases.forEach { p ->
            item {
                val current = p.index == prefs.phase
                val past = p.index < prefs.phase
                Section(
                    title = "${if (past) "Erledigt · " else ""}Phase ${p.index}: ${p.name}",
                    subtitle = "Geplant ${Time.short(p.plannedStart)} bis ${Time.full(p.plannedEnd)}",
                ) {
                    Text(p.goal, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    val newHabits = PhasePlan.habitsIntroducedIn(p.index)
                    if (newHabits.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Neu in dieser Phase", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        newHabits.forEach { h ->
                            Muted("• ${h.title}: mindestens ${h.minText(p.index).lowercaseFirst()}")
                        }
                    }
                }
            }
        }
    }

    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (kind) {
                        "back" -> "Eine Phase zurück?"
                        "force" -> "Wirklich schon weiter?"
                        else -> "Nächste Phase starten?"
                    },
                )
            },
            text = {
                Text(
                    when (kind) {
                        "back" -> "Das ist kein Scheitern. Du gehst einen Schritt zurück, festigst die Basis und gehst dann wieder vor."
                        "force" -> "Der Meilenstein ist noch nicht erreicht. Zu grosse Schritte waren bisher dein Stolperstein. Sicher?"
                        else -> "Neue Gewohnheiten kommen dazu, die bisherigen bleiben. Bereit?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    move(kind != "back")
                    confirm = null
                }) { Text("Ja") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}

private fun String.lowercaseFirst() = if (isEmpty()) this else this[0].lowercase() + substring(1)
