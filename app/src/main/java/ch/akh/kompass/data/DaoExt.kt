package ch.akh.kompass.data

import ch.akh.kompass.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Beutel pro Tag, nach der Wach-Logik gezählt (nicht nach Mitternacht). */
private fun perDay(times: List<Long>): List<DayCount> =
    times.groupingBy { Time.dayOf(it).toString() }.eachCount().map { (d, n) -> DayCount(d, n) }.sortedBy { it.day }

fun KDao.snusPerDay(from: Long): Flow<List<DayCount>> = snusTimes(from).map { perDay(it) }

suspend fun KDao.snusPerDayOnce(from: Long): List<DayCount> = perDay(snusTimesOnce(from))
