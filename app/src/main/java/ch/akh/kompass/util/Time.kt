package ch.akh.kompass.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Time {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val de = Locale.GERMAN
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", de)
    private val shortFmt = DateTimeFormatter.ofPattern("d. MMM", de)
    private val dmyFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", de)
    private val wdFmt = DateTimeFormatter.ofPattern("EE", de)

    /**
     * Speichert, wann ein Tag begonnen hat. Ein Tag beginnt mit "Wach", nicht um Mitternacht.
     * Wird beim App-Start gesetzt (siehe KompassApp).
     */
    interface DayStore {
        fun start(d: LocalDate): Long?
        fun record(d: LocalDate, at: Long, source: String)
        fun source(d: LocalDate): String?
        fun clear(d: LocalDate)
        /** Geplante Aufstehzeit in Minuten nach Mitternacht. */
        fun planWake(): Int
    }

    @Volatile var store: DayStore? = null

    /**
     * Schätzt, wann an einem Tag wirklich aufgestanden wurde (z. B. Ende der längsten Handypause in der Nacht).
     * Wird beim App-Start gesetzt. Null heisst: keine Schätzung möglich.
     */
    @Volatile var wakeGuess: ((LocalDate) -> Long?)? = null

    /**
     * Beginn für einen Tag, an dem "Wach" vergessen wurde: die geschätzte Aufstehzeit,
     * sonst die geplante. Nie die Frist selbst, sonst rutschen alle Einträge vom Vormittag in den Vortag.
     */
    fun guessStart(d: LocalDate): Long {
        val lo = at(d, EARLIEST_WAKE)
        val hi = deadline(d)
        val guess = try { wakeGuess?.invoke(d) } catch (_: Exception) { null }
        val plan = at(d, store?.planWake() ?: (7 * 60))
        return (guess ?: plan).coerceIn(lo, hi)
    }

    /**
     * Repariert automatische Tagesbeginne der letzten Tage, die noch auf die Frist (14 Uhr) gesetzt sind.
     * Die Einträge selbst bleiben unverändert, sie werden nur wieder dem richtigen Tag zugeordnet.
     */
    fun repairAutoStarts(days: Int = 14) {
        val s = store ?: return
        val cal = calToday()
        for (i in 0..days) {
            val d = cal.minusDays(i.toLong())
            if (s.source(d) != "auto") continue
            val start = s.start(d) ?: continue
            val fixed = guessStart(d)
            if (fixed < start) s.record(d, fixed, "auto")
        }
    }

    /** Frühestens ab 3 Uhr kann "Wach" einen neuen Tag beginnen (sonst war es ein Nickerchen). */
    const val EARLIEST_WAKE = 3 * 60

    /** Kalenderdatum, nur für Anzeigen und den Schlaf (Nacht gehört zum Tag des Aufwachens). */
    fun calToday(): LocalDate = LocalDate.now(zone)
    fun calDay(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun calStart(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Spätester Beginn eines Tages, falls "Wach" vergessen wurde: 14 Uhr oder 4 Stunden nach der geplanten Aufstehzeit. */
    fun deadline(d: LocalDate): Long {
        val plan = store?.planWake() ?: (7 * 60)
        return at(d, maxOf(14 * 60, plan + 4 * 60).coerceAtMost(23 * 60))
    }

    /**
     * Beginn eines Tages: wann "Wach" gedrückt wurde. Ohne Eintrag: bei vergangenen Tagen die geplante
     * Aufstehzeit, beim heutigen Kalendertag die Frist (bis dahin läuft noch der Vortag).
     */
    fun startOfDay(d: LocalDate): Long {
        val s = store ?: return calStart(d)
        s.start(d)?.let { return it }
        return if (d >= calToday()) deadline(d) else at(d, s.planWake())
    }

    fun endOfDay(d: LocalDate): Long = startOfDay(d.plusDays(1))

    /** Der aktuelle Tag. Wechselt mit "Wach" (oder spätestens zur Frist), nicht um Mitternacht. */
    fun today(): LocalDate {
        val cal = calToday()
        val now = System.currentTimeMillis()
        val s = store
        if (s != null && s.start(cal) == null && now >= deadline(cal)) {
            // Wach vergessen: Tag beginnt automatisch, damit Zähler nicht ewig am Vortag hängen.
            // Beginn rückwirkend auf die geschätzte Aufstehzeit, damit Snus, Kaffee usw. vom Vormittag zählen.
            s.record(cal, guessStart(cal), "auto")
        }
        return if (now >= startOfDay(cal)) cal else cal.minusDays(1)
    }

    fun todayStr(): String = today().toString()

    /** Zu welchem Tag ein Zeitpunkt gehört (nach der Wach-Logik). */
    fun dayOf(millis: Long): LocalDate {
        val c = calDay(millis)
        return if (millis >= startOfDay(c)) c else c.minusDays(1)
    }

    /** "Wach" gedrückt: beginnt einen neuen Tag, wenn es dafür nicht zu früh ist. Gibt den Tag zurück. */
    fun beginDay(now: Long = System.currentTimeMillis(), source: String = "wach"): LocalDate {
        val cal = calDay(now)
        val s = store ?: return cal
        val existing = s.start(cal)
        val src = s.source(cal)
        if (minuteOfDay(now) >= EARLIEST_WAKE && (existing == null || (src == "auto" && now < existing) || src == "fitbit")) {
            s.record(cal, now, source)
        }
        return dayOf(now)
    }

    fun timeOf(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    fun minuteOfDay(millis: Long): Int = timeOf(millis).let { it.hour * 60 + it.minute }
    fun at(d: LocalDate, minuteOfDay: Int): Long {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val dayShift = Math.floorDiv(minuteOfDay, 1440).toLong()
        return LocalDateTime.of(d.plusDays(dayShift), LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    fun weekStart(d: LocalDate = today()): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** "07:15" aus Minuten nach Mitternacht (auch negative oder > 24 h). */
    fun hm(minuteOfDay: Int): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun hm(millis: Long): String = hm(minuteOfDay(millis))

    /** "1 h 25 min" */
    fun dur(minutes: Int): String {
        val m = abs(minutes)
        val s = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min"
        return if (minutes < 0) "-$s" else s
    }

    fun durMs(ms: Long): String = dur((ms / 60000.0).roundToInt())

    fun nice(d: LocalDate): String = d.format(dayFmt)
    fun short(d: LocalDate): String = d.format(shortFmt)
    fun full(d: LocalDate): String = d.format(dmyFmt)
    fun weekday(d: LocalDate): String = d.format(wdFmt)

    /** Differenz zweier Uhrzeiten in Minuten, kürzester Weg über Mitternacht. */
    fun circularDiff(a: Int, b: Int): Int {
        var d = ((a - b) % 1440 + 1440) % 1440
        if (d > 720) d -= 1440
        return d
    }
}
