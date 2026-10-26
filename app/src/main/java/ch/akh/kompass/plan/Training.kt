package ch.akh.kompass.plan

import ch.akh.kompass.data.SetEntry
import kotlin.math.roundToInt

data class PlannedExercise(val name: String, val sets: Int, val repsLow: Int, val repsHigh: Int, val note: String = "")

data class Template(val name: String, val description: String, val exercises: List<PlannedExercise>)

/**
 * Pläne aus Akhs Lieblingsübungen, dazu Rumpf und Tragkraft für die RS.
 * Progression: Wenn alle Sätze das obere Wiederholungsziel schaffen, beim nächsten Mal mehr Gewicht.
 */
object Training {
    const val A = "Tag A: Drücken und Ziehen"
    const val B = "Tag B: Beine und Rücken"
    const val C = "Tag C: Brust, Schultern, Arme"
    const val MINI = "Mindestversion"
    const val RS_CORE = "RS: Rumpf und Tragkraft"
    const val RS_CIRCUIT = "RS: Zirkel Kraftausdauer"

    val templates = listOf(
        Template(
            A, "Bankdrücken, Latzug, Schultern, Trizeps, dazu Rumpf. Etwa 50 Minuten.",
            listOf(
                PlannedExercise("Bankdrücken", 3, 6, 10, "Schulterblätter zusammen, Füsse fest am Boden"),
                PlannedExercise("Latzug Kabel", 3, 8, 12, "Zur Brust ziehen, nicht in den Nacken"),
                PlannedExercise("Seitheben Kabelzug", 3, 12, 15, "Leicht, sauber, kein Schwung"),
                PlannedExercise("Reverse Butterfly", 3, 12, 15, "Gut für die Haltung unter dem Rucksack"),
                PlannedExercise("Trizeps Pushdowns", 3, 10, 15),
                PlannedExercise("Plank (Sekunden)", 3, 30, 60, "Wiederholungen = Sekunden"),
            ),
        ),
        Template(
            B, "Beinpresse, Rudern, Schrägbank, Waden, Bizeps, dazu Rumpf. Etwa 50 Minuten.",
            listOf(
                PlannedExercise("Beinpresse", 3, 8, 12, "Tief, aber unterer Rücken bleibt am Polster"),
                PlannedExercise("Rudern Kabel", 3, 8, 12, "Brust raus, Ellbogen nah am Körper"),
                PlannedExercise("Schrägbankdrücken", 3, 8, 12),
                PlannedExercise("Wadenheben Smith-Maschine", 3, 10, 15, "Unten kurz halten. Wichtig für lange Märsche"),
                PlannedExercise("Bizepscurls Kurzhanteln", 3, 10, 12),
                PlannedExercise("Dead Bug (pro Seite)", 3, 8, 12, "Rücken bleibt flach am Boden"),
            ),
        ),
        Template(
            C, "Butterfly, Latzug, Beinpresse, Schultern, Arme, dazu Farmer's Walk. Ab drei Trainings pro Woche.",
            listOf(
                PlannedExercise("Butterfly", 3, 10, 15),
                PlannedExercise("Latzug Kabel", 3, 10, 12, "Diesmal etwas leichter, mehr Wiederholungen"),
                PlannedExercise("Beinpresse", 3, 12, 15),
                PlannedExercise("Rudern Kabel", 2, 10, 12),
                PlannedExercise("Seitheben Kabelzug", 3, 12, 15),
                PlannedExercise("Bizepscurls Kurzhanteln", 2, 10, 12),
                PlannedExercise("Trizeps Pushdowns", 2, 10, 15),
                PlannedExercise("Farmer's Walk (Meter)", 3, 30, 50, "Schwere Kurzhanteln tragen, Rumpf fest. Wiederholungen = Meter"),
            ),
        ),
        Template(
            MINI, "Für schlechte Tage: hingehen, zwei Übungen, fertig. Zählt voll als Gym.",
            listOf(
                PlannedExercise("Bankdrücken", 2, 8, 10),
                PlannedExercise("Latzug Kabel", 2, 8, 12),
            ),
        ),
        Template(
            RS_CORE, "Was die Infanterie braucht: tragen, stabil bleiben, Treppen und Gelände. Ab Phase 4 einmal pro Woche.",
            listOf(
                PlannedExercise("Farmer's Walk (Meter)", 4, 30, 60, "So schwer, dass die letzten Meter hart sind"),
                PlannedExercise("Goblet Squat", 3, 10, 15),
                PlannedExercise("Step-ups mit Rucksack (pro Bein)", 3, 8, 12, "Rucksack mit 8 bis 15 kg"),
                PlannedExercise("Ausfallschritte gehend (pro Bein)", 3, 8, 12),
                PlannedExercise("Klimmzüge (assistiert)", 3, 5, 10),
                PlannedExercise("Pallof Press (pro Seite)", 3, 10, 12, "Kabel seitlich, nicht verdrehen lassen"),
                PlannedExercise("Seitstütz (Sekunden, pro Seite)", 3, 20, 45),
            ),
        ),
        Template(
            RS_CIRCUIT, "Zirkel ohne lange Pausen: Durchbeissen üben. 3 bis 5 Runden, 1 Minute Pause zwischen den Runden.",
            listOf(
                PlannedExercise("Liegestütze", 5, 10, 25),
                PlannedExercise("Goblet Squat", 5, 12, 20),
                PlannedExercise("Rudern Kabel", 5, 12, 15),
                PlannedExercise("Burpees", 5, 8, 15),
                PlannedExercise("Plank (Sekunden)", 5, 30, 60),
            ),
        ),
    )

    /** Reihenfolge der Trainings je nach Phase. */
    fun rotation(phase: Int): List<String> = when {
        phase >= 5 -> listOf(A, B, RS_CORE, C, B, RS_CIRCUIT)
        phase >= 4 -> listOf(A, B, C, A, B, RS_CORE)
        phase >= 3 -> listOf(A, B, C)
        else -> listOf(A, B)
    }

    /**
     * Nächstes Training in der Rotation. Ausgelassene Tage verschieben nichts:
     * Es geht immer mit dem Training nach dem letzten weiter.
     */
    fun next(phase: Int, lastTemplates: List<String>): String {
        val rot = rotation(phase)
        val done = lastTemplates.count { it in rot }
        return rot[done % rot.size]
    }

    /** Ausdauer-Aufbau für Marsch und Laufen, je nach Phase. */
    fun endurancePlan(phase: Int): List<String> = when {
        phase >= 5 -> listOf(
            "Intervalle: 6 x 3 Min zügig laufen, 2 Min gehen",
            "Langer Lauf: 40 bis 50 Min locker, man kann noch reden",
            "Marsch: 2 bis 3 Stunden mit 12 bis 15 kg, gute Schuhe einlaufen",
        )
        phase >= 4 -> listOf(
            "Intervalle: 5 x 2 Min zügig, 2 Min gehen",
            "Locker 30 bis 40 Min laufen oder biken",
            "Marsch: 90 Min mit 8 bis 10 kg",
        )
        phase >= 3 -> listOf(
            "Locker 20 bis 30 Min joggen oder biken",
            "Schwimmen 20 bis 30 Min",
        )
        else -> listOf("Zügig spazieren, 20 bis 30 Min. Die Schritte zählen mit.")
    }

    val allExercises: List<String> = (templates.flatMap { t -> t.exercises.map { it.name } } + listOf(
        "Kreuzheben", "Dips", "Face Pulls", "Hip Thrust", "Beinstrecker", "Beinbeuger", "Bauchrad", "Schulterdrücken",
        "Kniebeuge", "Hängendes Beinheben", "Russian Twist", "Treppensteigen mit Rucksack (Minuten)",
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
