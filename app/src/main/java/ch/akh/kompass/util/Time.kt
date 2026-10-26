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

    fun today(): LocalDate = LocalDate.now()
    fun todayStr(): String = today().toString()

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun timeOf(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    fun minuteOfDay(millis: Long): Int = timeOf(millis).let { it.hour * 60 + it.minute }

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDay(d: LocalDate): Long = startOfDay(d.plusDays(1))
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
