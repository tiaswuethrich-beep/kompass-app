package ch.akh.kompass.plan

import ch.akh.kompass.data.snusPerDay
import ch.akh.kompass.data.snusPerDayOnce
import android.content.Context
import ch.akh.kompass.data.AppDatabase
import ch.akh.kompass.data.CustomHabit
import ch.akh.kompass.data.HabitLog
import ch.akh.kompass.data.KDao
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.data.SleepEntry
import ch.akh.kompass.util.Time
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Eine Gewohnheit, wie sie in der Oberfläche erscheint (eingebaut oder eigene). */
data class HabitItem(
    val key: String,
    val title: String,
    val category: String,
    val min: String,
    val full: String,
    val perWeek: Int,
    val reminderMinute: Int?,
    val weekday: DayOfWeek?,
    val auto: Auto,
    val why: String,
    val introPhase: Int,
    val custom: CustomHabit? = null,
) {
    val daily: Boolean get() = perWeek >= 7
}

data class Criterion(val text: String, val met: Boolean, val detail: String = "")

data class PhaseStatus(
    val phase: Phase,
    val daysIn: Int,
    val criteria: List<Criterion>,
) {
    val ready: Boolean get() = phase.index < PhasePlan.phases.lastIndex && criteria.all { it.met }
}

/**
 * Die Logik hinter der App: Gewohnheiten der aktuellen Phase, Schlafverschiebung,
 * Snus-Abbauplan, automatisches Abhaken und Phasen-Freischaltung.
 */
class Engine(context: Context) {
    private val app: Context = context
    val prefs = Prefs.get(context)
    val dao: KDao = AppDatabase.get(context).dao()

    // ---------------- Schlaf ----------------

    /** Licht aus = Aufstehzeit minus Schlafbedarf (Minuten nach Mitternacht, 0 bis 1439). */
    fun lightsOut(): Int = ((prefs.currentWake - prefs.sleepNeedMinutes) % 1440 + 1440) % 1440

    /** Beginn der Abendroutine. */
    fun routineStart(): Int {
        val len = if (prefs.phase >= 3) 60 else 30
        return ((lightsOut() - len) % 1440 + 1440) % 1440
    }

    /**
     * Prüft alle paar Tage, ob die Ziel-Aufstehzeit 15 Minuten früher rücken darf.
     * Rückt nur, wenn die bisherige Zeit an den meisten Tagen geklappt hat.
     */
    suspend fun updateSleepShift(today: LocalDate = Time.today()) {
        if (prefs.phase < 1) return
        val last = prefs.lastShiftCheck
        if (last == null) {
            prefs.lastShiftCheck = today
            return
        }
        val every = prefs.shiftEveryDays.coerceAtLeast(1)
        val days = ChronoUnit.DAYS.between(last, today).toInt()
        if (days < every) return
        if (prefs.currentWake <= prefs.goalWake) {
            if (prefs.currentWake < prefs.goalWake) prefs.currentWake = prefs.goalWake
            prefs.shiftNote = "Du bist bei deinem Ziel von ${Time.hm(prefs.goalWake)}. Jetzt heisst es: halten."
            prefs.lastShiftCheck = today
            return
        }
        val logs = dao.habitsSince(last.plusDays(1).toString())
            .filter { it.key == "aufstehen" && it.date <= today.toString() && it.level >= 1 }
            .map { it.date }.toSet()
        val needed = (every - 1).coerceAtLeast(1)
        if (logs.size >= needed) {
            val next = (prefs.currentWake - prefs.shiftStepMinutes).coerceAtLeast(prefs.goalWake)
            prefs.currentWake = next
            prefs.shiftNote = "Gut gemacht. Neue Aufstehzeit: ${Time.hm(next)}. Licht aus um ${Time.hm(lightsOut())}."
        } else {
            prefs.shiftNote = "Die Aufstehzeit bleibt bei ${Time.hm(prefs.currentWake)}, bis sie sitzt. Kein Problem, das ist der Plan."
        }
        prefs.lastShiftCheck = today
    }

    /** Falls "Wach" vergessen wurde: Nicht stören wieder aus, sobald keine Nacht mehr läuft. */
    suspend fun releaseNightIfOver() {
        if (!sleeping()) ch.akh.kompass.util.Dnd.endNight(app)
    }

    /** "Gute Nacht" gedrückt und noch nicht "Wach". */
    suspend fun sleeping(): Boolean = dao.openSleep() != null

    /** Nachtruhe: nach "Gute Nacht" oder zwischen Licht aus (+15 Min) und der Aufstehzeit. */
    suspend fun quietNow(now: Long = System.currentTimeMillis()): Boolean {
        if (sleeping()) return true
        val m = Time.minuteOfDay(now)
        val from = (lightsOut() + 15) % 1440
        val to = prefs.currentWake
        return if (from < to) m in from until to else m >= from || m < to
    }

    /** Gibt eine Funktion zurück, die den Eintrag wieder rückgängig macht. */
    suspend fun goodNight(now: Long = System.currentTimeMillis()): suspend () -> Unit {
        ch.akh.kompass.util.Dnd.startNight(app)
        val undo = goodNightEntry(now)
        return { undo(); ch.akh.kompass.util.Dnd.endNight(app) }
    }

    private suspend fun goodNightEntry(now: Long): suspend () -> Unit {
        val open = dao.openSleep()
        return if (open != null && now - (open.bedAt ?: 0) < 4 * 3600_000L) {
            dao.upsertSleep(open.copy(bedAt = now))
            suspend { dao.upsertSleep(open) }
        } else {
            val id = dao.insertSleep(SleepEntry(bedAt = now))
            suspend {
                dao.openSleep()?.takeIf { it.id == id }?.let { dao.deleteSleep(it) }
                Unit
            }
        }
    }

    /** "Ich bin wach" zurücknehmen: Aufwachzeit entfernen und "aufstehen" austragen. */
    suspend fun undoWake(day: LocalDate = Time.calToday()) {
        // Tagesbeginn zurücknehmen, wenn er von "Wach" kam.
        Time.store?.let { st -> if (st.source(day) == "wach") st.clear(day) }
        prefs.bump()
        val s = dao.sleepOn(day.toString())
        if (s != null) {
            if (s.bedAt != null) dao.upsertSleep(s.copy(wakeAt = null)) else dao.deleteSleep(s)
        }
        dao.deleteHabit(day.toString(), "aufstehen")
    }

    /** Morgens "Wach" gedrückt (oder Wecker beendet). Gibt das Level für "aufstehen" zurück. */
    suspend fun wake(now: Long = System.currentTimeMillis()): Int {
        ch.akh.kompass.util.Dnd.endNight(app)
        // "Wach" startet den neuen Tag (ab 3 Uhr, davor zählt es als Nickerchen).
        val day = Time.beginDay(now)
        // Die Nacht gehört zum Kalendertag des Aufwachens.
        val wakeDate = Time.calDay(now).toString()
        val open = dao.openSleep()
        val existing = dao.sleepOn(wakeDate)
        if (open != null && now - (open.bedAt ?: 0) < 16 * 3600_000L) {
            dao.upsertSleep(open.copy(wakeAt = now, wakeDate = wakeDate))
        } else if (existing == null) {
            dao.insertSleep(SleepEntry(bedAt = null, wakeAt = now, wakeDate = wakeDate))
        }
        prefs.bump()
        if (Time.minuteOfDay(now) < Time.EARLIEST_WAKE) return 0
        val diff = abs(Time.circularDiff(Time.minuteOfDay(now), prefs.currentWake))
        val level = when {
            diff <= 10 -> 2
            diff <= 30 -> 1
            else -> 0
        }
        if (prefs.phase >= 1 && level > 0) dao.upsertHabit(HabitLog(day.toString(), "aufstehen", level))
        updateSleepShift(day)
        return level
    }

    // ---------------- Snus ----------------

    /** Tageslimit laut Abbauplan, null = noch kein Limit (nur zählen). */
    fun snusLimit(day: LocalDate = Time.today()): Int? {
        val start = prefs.taperStart
        val quit = prefs.quitDate
        if (prefs.phase < 2 && day < start) return null
        if (day < start) return null
        if (day >= quit) return 0
        val total = ceil(ChronoUnit.DAYS.between(start, quit) / 14.0).toInt().coerceAtLeast(1)
        val block = (ChronoUnit.DAYS.between(start, day) / 14).toInt()
        val base = prefs.snusBaseline
        val end = 4.coerceAtMost(base)
        return (base - (base - end) * (block + 1).toDouble() / total).roundToInt().coerceAtLeast(end)
    }

    /** Gespartes Geld seit Beginn des Abbaus, in CHF. */
    suspend fun moneySaved(today: LocalDate = Time.today()): Double {
        val start = prefs.taperStart
        if (today < start) return 0.0
        val counts = dao.snusPerDayOnce(Time.startOfDay(start)).associate { it.day to it.n }
        var saved = 0.0
        var d = start
        while (d <= today) {
            val n = counts[d.toString()] ?: 0
            saved += (prefs.snusBaseline - n).coerceAtLeast(0)
            d = d.plusDays(1)
        }
        return saved / prefs.pouchesPerCan.coerceAtLeast(1) * prefs.canPrice
    }

    // ---------------- Gewohnheiten ----------------

    fun reminderFor(def: HabitDef, phase: Int): Int? {
        val override = prefs.habitReminder(def.key)
        if (override >= 0) return override
        val a = def.anchor ?: return null
        val off = def.offset(phase)
        return when (a) {
            Anchor.WAKE -> ((prefs.currentWake + off) % 1440 + 1440) % 1440
            Anchor.LIGHTS_OUT -> ((lightsOut() + off) % 1440 + 1440) % 1440
            Anchor.FIXED -> off
        }
    }

    fun builtInItems(phase: Int = prefs.phase): List<HabitItem> =
        PhasePlan.habitsFor(phase).map { d ->
            HabitItem(
                key = d.key, title = d.title, category = d.category,
                min = d.minText(phase), full = d.fullText(phase), perWeek = d.perWeek(phase),
                reminderMinute = reminderFor(d, phase), weekday = d.weekday, auto = d.auto,
                why = d.why, introPhase = d.introPhase,
            )
        }

    fun customItem(c: CustomHabit) = HabitItem(
        key = "c_${c.id}", title = c.title, category = "Eigene", min = c.minText, full = c.fullText,
        perWeek = c.perWeek, reminderMinute = c.reminderMinute, weekday = null, auto = Auto.NONE,
        why = "", introPhase = 0, custom = c,
    )

    fun activeItems(customs: List<CustomHabit>, phase: Int = prefs.phase): List<HabitItem> =
        builtInItems(phase).filterNot { prefs.habitDisabled(it.key) } +
            customs.filter { it.active }.map { customItem(it) }

    suspend fun activeItemsOnce(): List<HabitItem> = activeItems(dao.customHabitsOnce())

    suspend fun setAuto(day: String, key: String, level: Int) {
        if (level <= 0) return
        val existing = dao.habitsSince(day).firstOrNull { it.date == day && it.key == key }
        if (existing == null || existing.level < level) dao.upsertHabit(HabitLog(day, key, level))
    }

    /** Hakt Gewohnheiten ab, die sich aus anderen Daten ergeben. */
    suspend fun syncAuto(date: LocalDate = Time.today()) {
        val day = date.toString()
        val phase = prefs.phase
        val active = PhasePlan.habitsFor(phase).filterNot { prefs.habitDisabled(it.key) }
        for (h in active) {
            when (h.auto) {
                Auto.MOOD -> dao.moodOnce(day)?.let { setAuto(day, h.key, if (it.note.isNotBlank()) 2 else 1) }
                Auto.GYM -> {
                    val ws = dao.workoutsBetween(Time.startOfDay(date), Time.endOfDay(date))
                    var best = 0
                    for (w in ws) {
                        val sets = dao.setCount(w.id)
                        if (sets > 0) best = maxOf(best, if (sets >= 9) 2 else 1)
                    }
                    setAuto(day, h.key, best)
                }
                Auto.CARDIO_RUN, Auto.SWIM, Auto.NATURE, Auto.MARCH -> {
                    val types = when (h.auto) {
                        Auto.CARDIO_RUN -> setOf("Joggen", "Bike")
                        Auto.SWIM -> setOf("Schwimmen")
                        Auto.NATURE -> setOf("Natur", "Wandern", "Bike")
                        else -> setOf("Marsch")
                    }
                    val full = when (h.auto) {
                        Auto.NATURE, Auto.MARCH -> 120
                        else -> 30
                    }
                    val mins = dao.cardioOn(day).filter { it.type in types }.sumOf { it.minutes }
                    if (mins > 0) setAuto(day, h.key, if (mins >= full) 2 else 1)
                }
                Auto.READ -> {
                    val mins = dao.readingMinutesOn(day)
                    val pages = dao.readingPagesOn(day)
                    val target = when {
                        phase >= 5 -> 30
                        phase >= 3 -> 20
                        else -> 10
                    }
                    if (mins > 0 || pages > 0) setAuto(day, h.key, if (mins >= target) 2 else 1)
                }
                Auto.FOCUS -> {
                    val mins = dao.focusMinutesOnce(day)
                    val target = when {
                        phase >= 5 -> 480
                        phase >= 4 -> 360
                        phase >= 3 -> 240
                        else -> 100
                    }
                    if (mins >= 25) setAuto(day, h.key, if (mins >= target) 2 else 1)
                }
                Auto.NICOTINE_FREE -> {
                    val n = dao.snusCount(0, Time.startOfDay(date), Time.endOfDay(date))
                    if (n == 0) setAuto(day, h.key, 2) else dao.deleteHabit(day, h.key)
                }
                Auto.WAKE, Auto.NONE, Auto.STEPS -> Unit // Schritte: siehe Health.syncSteps
            }
        }
    }

    // ---------------- Phasen ----------------

    suspend fun phaseStatus(today: LocalDate = Time.today()): PhaseStatus {
        val p = PhasePlan.phase(prefs.phase)
        val daysIn = ChronoUnit.DAYS.between(prefs.phaseStart, today).toInt() + 1
        val from14 = today.minusDays(13).toString()
        val logs14 = dao.habitsSince(from14)
        fun days(key: String) = logs14.filter { it.key == key && it.level >= 1 }.map { it.date }.toSet().size
        val c = mutableListOf<Criterion>()
        val minOk = daysIn >= p.minDays
        if (p.minDays > 0) c += Criterion("Mindestens ${p.minDays} Tage in dieser Phase", minOk, "Tag $daysIn")
        when (p.index) {
            0 -> {
                val nights = dao.sleepSince(today.minusDays(13).toString()).size
                c += Criterion("Schlaf eingetragen", nights >= 7, "$nights Nächte in 14 Tagen (mind. 7)")
            }
            1 -> {
                val n = days("aufstehen")
                c += Criterion("An 10 von 14 Tagen zur Zielzeit aufgestanden", n >= 10, "$n von 14")
                val gap = prefs.currentWake - prefs.goalWake
                c += Criterion("Aufstehzeit höchstens 1 h vom Ziel entfernt", gap <= 60, "aktuell ${Time.hm(prefs.currentWake)}")
            }
            2 -> {
                c += Criterion("Aufstehen um ${Time.hm(prefs.goalWake)} erreicht", prefs.currentWake <= prefs.goalWake, "aktuell ${Time.hm(prefs.currentWake)}")
                val gyms = dao.habitsSince(prefs.phaseStart.toString()).count { it.key == "gym" && it.level >= 1 }
                c += Criterion("Mindestens 12 Gym-Einheiten in dieser Phase", gyms >= 12, "$gyms bisher")
            }
            3 -> {
                val perDay = dao.snusPerDayOnce(Time.startOfDay(today.minusDays(6))).associate { it.day to it.n }
                val avg = (0..6).sumOf { perDay[today.minusDays(it.toLong()).toString()] ?: 0 } / 7.0
                c += Criterion("Snus unter 6 Beutel pro Tag (7-Tage-Schnitt)", avg < 6, "%.1f pro Tag".format(avg))
                val n = days("abend")
                c += Criterion("Abendroutine an 10 von 14 Tagen", n >= 10, "$n von 14")
            }
            4 -> {
                val last = dao.lastPouch()
                val free = if (last == null) 999 else ChronoUnit.DAYS.between(Time.dayOf(last), today).toInt()
                c += Criterion("4 Wochen nikotinfrei", free >= 28, if (last == null) "kein Beutel eingetragen" else "$free Tage frei")
            }
        }
        return PhaseStatus(p, daysIn, c)
    }

    /** Wechselt in die nächste Phase und berechnet die Startwerte aus den bisherigen Daten. */
    suspend fun advancePhase(today: LocalDate = Time.today()) {
        val next = (prefs.phase + 1).coerceAtMost(PhasePlan.phases.lastIndex)
        enterPhase(next, today)
    }

    suspend fun enterPhase(next: Int, today: LocalDate = Time.today()) {
        if (next >= 1 && prefs.phase < 1) {
            val wakes = dao.sleepSince(today.minusDays(14).toString()).mapNotNull { it.wakeAt }.map { Time.minuteOfDay(it) }
            if (wakes.size >= 3) {
                // Median, auf 15 Minuten gerundet. Späte Aufstehzeiten über Mittag bleiben erhalten.
                val med = wakes.sorted()[wakes.size / 2]
                val rounded = ((med + 7) / 15) * 15
                prefs.startWake = rounded.coerceAtLeast(prefs.goalWake)
            }
            prefs.currentWake = prefs.startWake.coerceAtLeast(prefs.goalWake)
            prefs.lastShiftCheck = today
            prefs.alarmEnabled = true
            prefs.shiftNote = "Start bei ${Time.hm(prefs.currentWake)}. Alle ${prefs.shiftEveryDays} Tage 15 Minuten früher, wenn es klappt."
        }
        if (next >= 2 && prefs.phase < 2) {
            val perDay = dao.snusPerDayOnce(Time.startOfDay(today.minusDays(14)))
            if (perDay.size >= 5) prefs.snusBaseline = (perDay.sumOf { it.n }.toDouble() / perDay.size).roundToInt().coerceAtLeast(1)
            prefs.taperStart = today
            if (prefs.quitDate < today.plusDays(84)) prefs.quitDate = today.plusDays(119)
        }
        if (next >= 4 && prefs.phase < 4 && prefs.quitDate > today.plusDays(7)) {
            prefs.quitDate = today.plusDays(7)
        }
        prefs.phase = next
        prefs.phaseStart = today
    }

    suspend fun previousPhase(today: LocalDate = Time.today()) {
        if (prefs.phase == 0) return
        prefs.phase = prefs.phase - 1
        prefs.phaseStart = today
    }

    // ---------------- Wochenstand ----------------

    /** Wie oft eine Gewohnheit in der laufenden Woche erledigt wurde. */
    fun weekCount(logs: List<HabitLog>, key: String, today: LocalDate = Time.today()): Int {
        val ws = Time.weekStart(today).toString()
        return logs.count { it.key == key && it.date >= ws && it.level >= 1 }
    }

    /** Gestern verpasst und heute noch offen: "Nie zweimal hintereinander". */
    fun missedYesterday(logs: List<HabitLog>, item: HabitItem, today: LocalDate = Time.today()): Boolean {
        if (!item.daily) return false
        if (item.introPhase == prefs.phase && prefs.phaseStart >= today.minusDays(1)) return false
        val y = today.minusDays(1).toString()
        val t = today.toString()
        return logs.none { it.key == item.key && it.date == y } && logs.none { it.key == item.key && it.date == t }
    }

    companion object {
        @Volatile private var instance: Engine? = null
        fun get(context: Context): Engine =
            instance ?: synchronized(this) {
                instance ?: Engine(context.applicationContext).also { instance = it }
            }
    }
}
