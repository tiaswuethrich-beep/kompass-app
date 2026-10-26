package ch.akh.kompass.plan

import ch.akh.kompass.data.SetEntry
import kotlin.math.roundToInt

data class PlannedExercise(val name: String, val sets: Int, val repsLow: Int, val repsHigh: Int, val note: String = "")

data class Template(val name: String, val description: String, val exercises: List<PlannedExercise>)

/**
 * Einfache Ganzkörperpläne für den Wiedereinstieg. A und B abwechseln.
 * Progression: Wenn alle Sätze das obere Wiederholungsziel schaffen, beim nächsten Mal mehr Gewicht.
 */
object Training {
    val templates = listOf(
        Template(
            "Ganzkörper A", "Kniebeuge, Drücken, Ziehen. Etwa 45 Minuten.",
            listOf(
                PlannedExercise("Kniebeuge", 3, 6, 10, "Bis die Oberschenkel parallel sind"),
                PlannedExercise("Bankdrücken", 3, 6, 10),
                PlannedExercise("Rudern am Kabel", 3, 8, 12),
                PlannedExercise("Beinbeuger", 2, 10, 15),
                PlannedExercise("Plank (Sekunden)", 3, 30, 60, "Wiederholungen = Sekunden"),
            ),
        ),
        Template(
            "Ganzkörper B", "Hüfte, Schultern, Rücken. Etwa 45 Minuten.",
            listOf(
                PlannedExercise("Rumänisches Kreuzheben", 3, 6, 10, "Rücken gerade, Hüfte nach hinten"),
                PlannedExercise("Schulterdrücken", 3, 6, 10),
                PlannedExercise("Latzug", 3, 8, 12),
                PlannedExercise("Ausfallschritte", 2, 8, 12, "Pro Bein"),
                PlannedExercise("Bizeps-Curls", 2, 10, 15),
            ),
        ),
        Template(
            "Ganzkörper C", "Für die dritte Einheit ab Phase 3.",
            listOf(
                PlannedExercise("Beinpresse", 3, 8, 12),
                PlannedExercise("Schrägbankdrücken Kurzhanteln", 3, 8, 12),
                PlannedExercise("Klimmzüge (assistiert)", 3, 5, 10),
                PlannedExercise("Seitheben", 2, 12, 15),
                PlannedExercise("Trizepsdrücken am Kabel", 2, 10, 15),
            ),
        ),
        Template(
            "Mindestversion", "Für schlechte Tage: hingehen, eine Übung, fertig. Zählt voll als Gym.",
            listOf(PlannedExercise("Kniebeuge", 2, 8, 10)),
        ),
        Template(
            "RS-Vorbereitung", "Körpergewicht und Ausdauerkraft für die Rekrutenschule.",
            listOf(
                PlannedExercise("Liegestütze", 4, 10, 25),
                PlannedExercise("Klimmzüge (assistiert)", 3, 5, 10),
                PlannedExercise("Kniebeuge", 3, 10, 15),
                PlannedExercise("Farmer's Walk (Meter)", 3, 30, 50, "Wiederholungen = Meter"),
                PlannedExercise("Plank (Sekunden)", 3, 45, 90),
            ),
        ),
    )

    val allExercises: List<String> = (templates.flatMap { t -> t.exercises.map { it.name } } + listOf(
        "Kreuzheben", "Dips", "Face Pulls", "Wadenheben", "Hip Thrust", "Beinstrecker", "Bauchrad",
    )).distinct().sorted()

    fun plannedFor(name: String): PlannedExercise? =
        templates.flatMap { it.exercises }.firstOrNull { it.name == name }

    /** Geschätztes Maximalgewicht für eine Wiederholung (Epley). */
    fun e1rm(weight: Double, reps: Int): Double = if (reps <= 0) 0.0 else weight * (1 + reps / 30.0)

    /** Vorschlag für heute aus den Sätzen vom letzten Mal. */
    fun suggestion(exercise: String, last: List<SetEntry>): String {
        val plan = plannedFor(exercise)
        if (last.isEmpty()) {
            return if (plan != null) "Erstes Mal: leicht starten, ${plan.sets} x ${plan.repsLow} bis ${plan.repsHigh}. Technik vor Gewicht."
            else "Erstes Mal: leicht starten, Technik vor Gewicht."
        }
        val w = last.maxOf { it.weight }
        val top = plan?.repsHigh ?: 10
        val allTop = last.size >= (plan?.sets ?: 3) && last.all { it.reps >= top }
        val fmt = { x: Double -> if (x % 1.0 == 0.0) x.roundToInt().toString() else "%.1f".format(x) }
        return when {
            w == 0.0 && allTop -> "Letztes Mal alle Sätze mit $top geschafft. Heute etwas schwerer machen."
            allTop -> "Letztes Mal alles geschafft. Heute ${fmt(w + if (w >= 40) 2.5 else 1.25)} kg."
            else -> "Letztes Mal ${last.joinToString(" / ") { "${it.reps}" }} mit ${fmt(w)} kg. Heute gleiches Gewicht, eine Wiederholung mehr."
        }
    }
}
