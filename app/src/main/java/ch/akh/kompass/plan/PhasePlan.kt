package ch.akh.kompass.plan

import java.time.DayOfWeek
import java.time.LocalDate

data class Phase(
    val index: Int,
    val name: String,
    val plannedStart: LocalDate,
    val plannedEnd: LocalDate,
    val minDays: Int,
    val goal: String,
    val why: String,
    val milestone: String,
    val tips: List<String>,
)

/** Wovon die Erinnerungszeit abhängt. */
enum class Anchor { WAKE, LIGHTS_OUT, FIXED }

/** Welche Daten eine Gewohnheit automatisch abhaken. */
enum class Auto { NONE, WAKE, MOOD, GYM, CARDIO_RUN, SWIM, NATURE, MARCH, READ, FOCUS, NICOTINE_FREE }

data class HabitDef(
    val key: String,
    val title: String,
    val category: String,
    val introPhase: Int,
    val endPhase: Int = 99,
    val perWeek: (Int) -> Int = { 7 },
    val minText: (Int) -> String,
    val fullText: (Int) -> String,
    val anchor: Anchor? = null,
    /** Bei WAKE und LIGHTS_OUT: Minuten relativ dazu. Bei FIXED: Minute des Tages. */
    val offset: (Int) -> Int = { 0 },
    val weekday: DayOfWeek? = null,
    val auto: Auto = Auto.NONE,
    val why: String = "",
)

object PhasePlan {
    val phases = listOf(
        Phase(
            0, "Hinschauen",
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 8), 14,
            goal = "Nur messen, nichts erzwingen. Schlaf, Snus, Bildschirmzeit und Stimmung eintragen. Einzige neue Gewohnheit: Bett machen.",
            why = "Du brauchst einen ehrlichen Startpunkt. Aus diesen zwei Wochen berechnet die App deine Start-Aufstehzeit und deinen Snus-Ausgangswert.",
            milestone = "14 Tage lang Schlaf und Snus eingetragen. Egal wie es aussieht, danach geht es weiter.",
            tips = listOf(
                "Tippe abends auf \"Gute Nacht\" und morgens auf \"Wach\". Mehr braucht es nicht.",
                "Jeden Beutel antippen, auch wenn es weh tut, die Zahl zu sehen. Ohne Zahl kein Plan.",
                "Bildschirmzeit-Zugriff in den Einstellungen freigeben, dann misst die App von selbst.",
            ),
        ),
        Phase(
            1, "Schlaf-Anker",
            LocalDate.of(2026, 11, 9), LocalDate.of(2027, 1, 3), 42,
            goal = "Jeden Tag zur gleichen Zeit aufstehen, auch am Wochenende. Alle 4 Tage rückt der Wecker 15 Minuten früher, bis 7:00 erreicht ist.",
            why = "Die Aufstehzeit stellt die innere Uhr. Als Wolf ist das der grösste Hebel für Energie am Tag und Müdigkeit am Abend.",
            milestone = "An 10 von 14 Tagen höchstens 30 Minuten neben der Zielzeit aufgestanden.",
            tips = listOf(
                "Wecker ausser Reichweite legen, damit du aufstehen musst.",
                "Direkt nach dem Aufstehen 10 Minuten raus, auch bei Nebel.",
                "Wenn du abends nicht müde bist: trotzdem zur Zielzeit aufstehen. Die Müdigkeit kommt nach ein paar Tagen.",
                "Kein Snus in der letzten Stunde vor dem Schlafen.",
            ),
        ),
        Phase(
            2, "Körper und Ordnung",
            LocalDate.of(2027, 1, 4), LocalDate.of(2027, 2, 28), 42,
            goal = "Gym 2x pro Woche, Sonntag Wäschetag, jeden Abend 10 Minuten Zimmer-Reset, Fokusblöcke am Nachmittag. Der Snus-Abbau beginnt.",
            why = "Mit stabilem Schlaf hast du jetzt Energie für Bewegung und Ordnung. Nachmittags ist dein Hoch, dort liegen Gym und Arbeit.",
            milestone = "7:00 Uhr steht und mindestens 12 Gym-Einheiten in dieser Phase.",
            tips = listOf(
                "Gym-Tasche am Vorabend packen.",
                "Mindestversion Gym: hingehen, eine Übung, wieder gehen. Das zählt.",
                "Wäsche: Maschine starten, bevor du etwas anderes machst.",
            ),
        ),
        Phase(
            3, "Struktur",
            LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 25), 42,
            goal = "Gym 3x plus 1x Joggen oder Bike, Abendroutine 60 Minuten mit Lesen und Skincare, 4x pro Woche kochen, Budget führen, 4 Stunden Fokus, Bildschirm-Budget.",
            why = "Jetzt kommen die Bausteine, die deinen Alltag tragen. Games und YouTube bleiben erlaubt, aber nach der Arbeit und mit Budget.",
            milestone = "Snus unter 6 Beutel pro Tag und Abendroutine an 10 von 14 Tagen.",
            tips = listOf(
                "Einmal pro Woche einkaufen mit Liste, dann ist Kochen einfacher als Bestellen.",
                "Monatlich die Buchungen als CSV aus dem E-Banking exportieren und importieren.",
                "Games erst, wenn der Fokus-Block erledigt ist.",
            ),
        ),
        Phase(
            4, "Frei",
            LocalDate.of(2027, 4, 26), LocalDate.of(2027, 6, 27), 28,
            goal = "Ab dem Rauchfrei-Tag nikotinfrei. 5x Sport pro Woche inklusive Schwimmen, koffeinfrei, 6 Stunden Fokus, jede Woche Zeit in der Natur.",
            why = "Die grosse Veränderung kommt erst, wenn alles andere trägt. Sport und guter Schlaf helfen gegen das Verlangen.",
            milestone = "4 Wochen nikotinfrei.",
            tips = listOf(
                "Craving dauert 3 bis 5 Minuten. Drück den Craving-Knopf und warte den Timer ab.",
                "Nikotinersatz aus der Apotheke ist erlaubt und hilft.",
                "Ein Ausrutscher ist kein Rückfall. Nächster Beutel bleibt einfach weg.",
            ),
        ),
        Phase(
            5, "Bereit für die RS",
            LocalDate.of(2027, 6, 28), LocalDate.of(2027, 8, 31), 0,
            goal = "Aufstehen Richtung 6:00, Ausdauer und Rucksackmärsche, tägliche Liegestütze. Alles halten statt Neues anfangen.",
            why = "In der Rekrutenschule zählen Schlaf, Ausdauer und ein Kopf, der Routinen kennt. Genau das hast du dann.",
            milestone = "Die RS mit stabilem Körper und Kopf beginnen.",
            tips = listOf(
                "Ziel-Aufstehzeit in den Einstellungen auf 6:00 setzen, der Wecker wandert wieder langsam.",
                "Schuhe für die RS früh einlaufen.",
                "Sieh, was du seit Oktober geschafft hast. Das nimmst du mit.",
            ),
        ),
    )

    private fun byPhase(vararg pairs: Pair<Int, String>): (Int) -> String = { p ->
        pairs.lastOrNull { p >= it.first }?.second ?: pairs.first().second
    }

    private fun intByPhase(vararg pairs: Pair<Int, Int>): (Int) -> Int = { p ->
        pairs.lastOrNull { p >= it.first }?.second ?: pairs.first().second
    }

    val habits = listOf(
        HabitDef(
            "bett", "Bett machen", "Ordnung", 0,
            minText = { "Decke glattziehen" }, fullText = { "Bett komplett machen" },
            anchor = Anchor.WAKE, offset = { 20 },
            why = "Der erste kleine Sieg am Tag.",
        ),
        HabitDef(
            "stimmung", "Stimmung eintragen", "Kopf", 0,
            minText = { "Drei Zahlen antippen" }, fullText = { "Plus ein Satz, was heute gut war" },
            anchor = Anchor.LIGHTS_OUT, offset = { -75 }, auto = Auto.MOOD,
            why = "Zeigt dir und später vielleicht dem Hausarzt, wie es dir wirklich geht.",
        ),
        HabitDef(
            "aufstehen", "Zur Zielzeit aufstehen", "Schlaf", 1,
            minText = { "Höchstens 30 Min daneben" }, fullText = { "Pünktlich, höchstens 10 Min daneben" },
            auto = Auto.WAKE,
            why = "Der wichtigste Hebel für deine innere Uhr. Wird automatisch abgehakt, wenn du \"Wach\" drückst.",
        ),
        HabitDef(
            "licht", "Tageslicht am Morgen", "Schlaf", 1,
            minText = { "2 Min am offenen Fenster" }, fullText = { "10 Min draussen" },
            anchor = Anchor.WAKE, offset = { 30 },
            why = "Licht am Morgen verschiebt die innere Uhr nach vorne.",
        ),
        HabitDef(
            "abend", "Abendroutine ohne Bildschirm", "Schlaf", 1,
            minText = byPhase(1 to "Handy weg, Zähne putzen", 3 to "30 Min ohne Bildschirm"),
            fullText = byPhase(1 to "30 Min ohne Bildschirm", 3 to "60 Min: Skincare, Lesen, Tag abschliessen"),
            anchor = Anchor.LIGHTS_OUT, offset = intByPhase(1 to -30, 3 to -60),
            why = "Dein Ziel: jeden Abend eine Stunde ohne Medien. Wir starten mit 30 Minuten.",
        ),
        HabitDef(
            "lesen", "Lesen", "Kopf", 1,
            minText = { "1 Seite" }, fullText = byPhase(1 to "10 Min", 3 to "20 Min", 5 to "30 Min"),
            anchor = Anchor.LIGHTS_OUT, offset = { -25 }, auto = Auto.READ,
            why = "Eine Seite zählt. Meistens werden es mehr.",
        ),
        HabitDef(
            "snusfenster", "Snus-freie Zeitfenster", "Nikotin", 1, endPhase = 3,
            minText = { "Erste 30 Min nach dem Aufstehen ohne" },
            fullText = { "Und die letzte Stunde vor dem Schlafen ohne" },
            why = "Nikotin am Abend macht wach und stört den Tiefschlaf.",
        ),
        HabitDef(
            "koffein14", "Kein Koffein nach 14 Uhr", "Schlaf", 1, endPhase = 3,
            minText = { "Kein Kaffee nach 14 Uhr" }, fullText = { "Auch kein Energy, Cola oder Eistee" },
            why = "Koffein wirkt 6 bis 8 Stunden nach.",
        ),
        HabitDef(
            "gym", "Gym", "Körper", 2,
            perWeek = intByPhase(2 to 2, 3 to 3),
            minText = { "Hingehen, 20 Min, eine Übung" }, fullText = { "Ganzes Training" },
            anchor = Anchor.FIXED, offset = { 16 * 60 + 30 }, auto = Auto.GYM,
            why = "Nachmittags ist dein Leistungshoch als Wolf.",
        ),
        HabitDef(
            "waesche", "Wäsche", "Ordnung", 2, perWeek = { 1 },
            minText = { "Eine Maschine starten" }, fullText = { "Waschen, aufhängen, versorgen" },
            anchor = Anchor.FIXED, offset = { 11 * 60 }, weekday = DayOfWeek.SUNDAY,
            why = "Ein fester Tag statt \"wenn ich dran denke\".",
        ),
        HabitDef(
            "zimmer", "Zimmer-Reset", "Ordnung", 2,
            minText = { "5 Dinge an ihren Platz" }, fullText = { "10 Min aufräumen" },
            anchor = Anchor.LIGHTS_OUT, offset = { -90 },
            why = "Jeden Abend ein bisschen, dann wird es nie wieder ein Berg.",
        ),
        HabitDef(
            "fokus", "Fokus-Arbeit", "Arbeit", 2, perWeek = { 5 },
            minText = { "1 Block à 25 Min" },
            fullText = byPhase(2 to "2 Blöcke à 50 Min", 3 to "4 Stunden", 4 to "6 Stunden", 5 to "8 Stunden"),
            anchor = Anchor.FIXED, offset = { 14 * 60 }, auto = Auto.FOCUS,
            why = "Dein Ziel sind 8 Stunden ohne Ablenkung. Das wächst Schritt für Schritt.",
        ),
        HabitDef(
            "cardio", "Joggen oder Bike", "Körper", 3, perWeek = { 1 },
            minText = byPhase(3 to "15 Min zügig gehen", 5 to "20 Min laufen"),
            fullText = byPhase(3 to "30 Min joggen oder biken", 5 to "5 km laufen"),
            anchor = Anchor.FIXED, offset = { 17 * 60 }, weekday = DayOfWeek.WEDNESDAY, auto = Auto.CARDIO_RUN,
        ),
        HabitDef(
            "kochen", "Selbst kochen", "Alltag", 3, perWeek = intByPhase(3 to 4, 4 to 5),
            minText = { "Etwas Einfaches selbst machen" }, fullText = { "Richtige Mahlzeit kochen" },
            anchor = Anchor.FIXED, offset = { 18 * 60 + 30 },
        ),
        HabitDef(
            "skincare", "Skincare", "Alltag", 3,
            minText = { "Gesicht waschen" }, fullText = { "Waschen und pflegen" },
            anchor = Anchor.LIGHTS_OUT, offset = { -55 },
        ),
        HabitDef(
            "budget", "Finanzen checken", "Geld", 3, perWeek = { 1 },
            minText = { "Ausgaben der Woche eintragen" }, fullText = { "Plus Budget prüfen" },
            anchor = Anchor.FIXED, offset = { 18 * 60 }, weekday = DayOfWeek.SUNDAY,
        ),
        HabitDef(
            "schwimmen", "Schwimmen", "Körper", 4, perWeek = { 1 },
            minText = { "20 Min im Wasser" }, fullText = { "30 Min Bahnen" }, auto = Auto.SWIM,
        ),
        HabitDef(
            "natur", "Zeit in der Natur", "Kopf", 4, perWeek = { 1 },
            minText = { "30 Min im Wald oder am See" }, fullText = { "2 Stunden draussen" },
            anchor = Anchor.FIXED, offset = { 13 * 60 }, weekday = DayOfWeek.SATURDAY, auto = Auto.NATURE,
        ),
        HabitDef(
            "koffeinfrei", "Koffeinfrei", "Körper", 4,
            minText = { "Kein Kaffee" }, fullText = { "Auch kein Energy, Cola oder Eistee" },
        ),
        HabitDef(
            "nikotinfrei", "Nikotinfrei", "Nikotin", 4,
            minText = { "Kein Beutel heute" }, fullText = { "Kein Beutel heute" }, auto = Auto.NICOTINE_FREE,
            why = "Wird automatisch abgehakt, solange du keinen Beutel einträgst.",
        ),
        HabitDef(
            "kraft", "Liegestütze und Rumpf", "Körper", 5,
            minText = { "20 Liegestütze" }, fullText = { "3 Runden: 20 Liegestütze, 1 Min Plank" },
            anchor = Anchor.WAKE, offset = { 40 },
        ),
        HabitDef(
            "marsch", "Marsch mit Rucksack", "Körper", 5, perWeek = { 1 },
            minText = { "45 Min mit 8 kg" }, fullText = { "2 Stunden mit 12 kg" },
            anchor = Anchor.FIXED, offset = { 10 * 60 }, weekday = DayOfWeek.SATURDAY, auto = Auto.MARCH,
        ),
    )

    fun phase(i: Int): Phase = phases[i.coerceIn(0, phases.lastIndex)]

    fun habitsFor(phase: Int): List<HabitDef> =
        habits.filter { phase >= it.introPhase && phase <= it.endPhase }

    fun habitsIntroducedIn(phase: Int): List<HabitDef> = habits.filter { it.introPhase == phase }
}
