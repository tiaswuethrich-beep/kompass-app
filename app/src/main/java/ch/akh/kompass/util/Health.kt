package ch.akh.kompass.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.data.SleepEntry
import ch.akh.kompass.plan.Engine
import java.time.Instant
import java.time.LocalDate

/**
 * Health Connect: Hier legt die Fitbit-App Schlaf und Schritte ab.
 * Kompass liest nur, schreibt nichts zurück.
 */
object Health {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    private const val PROVIDER = "com.google.android.apps.healthdata"

    /** true, wenn Health Connect auf dem Handy nutzbar ist. */
    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context, PROVIDER) == HealthConnectClient.SDK_AVAILABLE

    fun needsUpdate(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context, PROVIDER) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun storeIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context)

    suspend fun granted(context: Context): Set<String> = try {
        if (!available(context)) emptySet() else client(context).permissionController.getGrantedPermissions()
    } catch (_: Exception) {
        emptySet()
    }

    suspend fun hasAll(context: Context) = granted(context).containsAll(PERMISSIONS)

    // ---------------- Schritte ----------------

    suspend fun stepsOn(context: Context, d: LocalDate): Long? = try {
        val start = Instant.ofEpochMilli(Time.startOfDay(d))
        val end = Instant.ofEpochMilli(minOf(Time.endOfDay(d), System.currentTimeMillis()))
        val r = client(context).aggregate(
            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, end)),
        )
        r[StepsRecord.COUNT_TOTAL] ?: 0L
    } catch (_: Exception) {
        null
    }

    /** Schritte der letzten n Tage (ältester zuerst). null, wenn nicht lesbar. */
    suspend fun stepsDays(context: Context, n: Int): List<Long?> {
        if (!available(context)) return List(n) { null }
        val today = Time.today()
        return (n - 1 downTo 0).map { stepsOn(context, today.minusDays(it.toLong())) }
    }

    /**
     * Hakt "Schritte" ab und passt das Ziel an:
     * Wer in 14 Tagen an mindestens 10 Tagen das Ziel schafft, bekommt 500 Schritte mehr, bis zur Obergrenze.
     */
    suspend fun syncSteps(context: Context) {
        if (!available(context) || !hasAll(context)) return
        val p = Prefs.get(context)
        val e = Engine.get(context)
        if (p.habitDisabled("schritte")) return
        val today = Time.today()
        for (d in listOf(today.minusDays(1), today)) {
            val s = stepsOn(context, d) ?: continue
            val goal = p.stepGoal
            val lvl = when {
                s >= goal -> 2
                s >= goal / 2 -> 1
                else -> 0
            }
            if (p.phase >= 1) e.setAuto(d.toString(), "schritte", lvl)
        }
        val last = p.stepGoalChecked.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        if (last == null) {
            p.stepGoalChecked = today.toString()
        } else if (!today.isBefore(last.plusDays(14))) {
            val days = stepsDays(context, 15).dropLast(1)
            val hit = days.count { (it ?: 0) >= p.stepGoal }
            val max = if (p.phase >= 5) maxOf(p.stepGoalMax, 12000) else p.stepGoalMax
            p.stepNote = when {
                hit >= 10 && p.stepGoal < max -> {
                    p.stepGoal = minOf(max, p.stepGoal + 500)
                    "Ziel an $hit von 14 Tagen geschafft. Neues Ziel: ${p.stepGoal} Schritte."
                }
                hit >= 10 -> "Ziel an $hit von 14 Tagen geschafft. Du bist auf dem Höchstziel."
                else -> "Ziel an $hit von 14 Tagen geschafft. Es bleibt bei ${p.stepGoal}, ab 10 Tagen steigt es."
            }
            p.stepGoalChecked = today.toString()
        }
    }

    // ---------------- Schlaf ----------------

    /**
     * Übernimmt die Schlafphasen der letzten Tage. Pro Aufwach-Tag zählt der längste Schlaf.
     * Bestehende Einträge bekommen die genaueren Zeiten der Fitbit, Bewertung und Notiz bleiben.
     * Gibt die Anzahl übernommener Nächte zurück, oder -1, wenn kein Zugriff besteht.
     */
    suspend fun syncSleep(context: Context, days: Int = 14): Int {
        if (!available(context) || !hasAll(context)) return -1
        val dao = Engine.get(context).dao
        val now = Instant.now()
        val recs = try {
            client(context).readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(days * 86_400L), now),
                ),
            ).records
        } catch (_: Exception) {
            return -1
        }
        val awake = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
        val byDay = recs.filter { java.time.Duration.between(it.startTime, it.endTime).toMinutes() >= 180 }
            .groupBy { Time.dayOf(it.endTime.toEpochMilli()).toString() }
            .mapValues { (_, l) -> l.maxBy { java.time.Duration.between(it.startTime, it.endTime).toMinutes() } }
        var n = 0
        for ((day, r) in byDay) {
            val bed = r.startTime.toEpochMilli()
            val wake = r.endTime.toEpochMilli()
            val awakeMin = r.stages.filter { it.stage in awake }.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            val asleep = ((wake - bed) / 60_000L - awakeMin).toInt()
            val note = "Fitbit · ${Time.dur(asleep)} geschlafen"
            val existing = dao.sleepOn(day)
            val open = dao.openSleep()?.takeIf { o -> (o.bedAt ?: 0L) in (bed - 6 * 3600_000L)..wake }
            when {
                existing != null -> {
                    if (existing.bedAt == bed && existing.wakeAt == wake) continue
                    val keepNote = existing.note.takeIf { it.isNotBlank() && !it.startsWith("Fitbit") }
                    dao.upsertSleep(existing.copy(bedAt = bed, wakeAt = wake, note = keepNote?.let { "$it · $note" } ?: note))
                }
                open != null -> dao.upsertSleep(open.copy(bedAt = bed, wakeAt = wake, wakeDate = day, note = note))
                else -> dao.insertSleep(SleepEntry(bedAt = bed, wakeAt = wake, wakeDate = day, note = note))
            }
            n++
        }
        // Aufstehzeit aus der Fitbit für "Aufstehen" verwenden, wenn noch nichts eingetragen ist.
        val e = Engine.get(context)
        val today = Time.todayStr()
        val p = Prefs.get(context)
        byDay[today]?.let { r ->
            if (p.phase >= 1 && e.dao.habitOnce(today, "aufstehen") == null) {
                val diff = kotlin.math.abs(Time.circularDiff(Time.minuteOfDay(r.endTime.toEpochMilli()), p.currentWake))
                val lvl = when {
                    diff <= 10 -> 2
                    diff <= 30 -> 1
                    else -> 0
                }
                e.setAuto(today, "aufstehen", lvl)
            }
        }
        Prefs.get(context).healthLastSync = System.currentTimeMillis()
        return n
    }

    /** Beides auf einmal, Fehler werden geschluckt. */
    suspend fun syncAll(context: Context, force: Boolean = false) {
        try {
            val p = Prefs.get(context)
            if (p.healthSleepAuto && (force || System.currentTimeMillis() - p.healthLastSync > 15 * 60_000L)) syncSleep(context)
            syncSteps(context)
        } catch (_: Exception) {
        }
    }
}
