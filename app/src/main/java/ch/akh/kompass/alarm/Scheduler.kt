package ch.akh.kompass.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import ch.akh.kompass.MainActivity
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.util.Time
import java.time.LocalDate

data class Reminder(
    val key: String,
    val title: String,
    val text: String,
    val minute: Int,
    val weekday: java.time.DayOfWeek? = null,
    val habit: Boolean = true,
)

object Scheduler {
    const val ACTION_WAKE = "ch.akh.kompass.WAKE_ALARM"
    const val ACTION_REMIND = "ch.akh.kompass.REMIND"
    const val ACTION_CHECK = "ch.akh.kompass.CHECK_SCREEN"
    const val ACTION_FOCUS_END = "ch.akh.kompass.FOCUS_END"
    const val EXTRA_KEY = "key"
    const val EXTRA_SNOOZE = "snooze"
    const val EXTRA_ATTEMPT = "attempt"

    private const val RC_WAKE = 1000
    private const val RC_SNOOZE = 1001
    private const val RC_CHECK = 1002
    private const val RC_FOCUS = 1003
    private const val SCHEDULED_KEYS = "scheduledReminderKeys"

    private fun am(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(context).canScheduleExactAlarms()

    private fun wakeIntent(context: Context, snooze: Boolean, rc: Int): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).setAction(ACTION_WAKE).putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(context, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Nächster Weckzeitpunkt nach jetzt, oder null wenn der Wecker aus ist. */
    fun nextWake(context: Context, now: Long = System.currentTimeMillis()): Long? {
        val p = Prefs.get(context)
        if (!p.alarmEnabled) return null
        val today = Time.today()
        val t = Time.at(today, p.currentWake)
        return if (t > now + 30_000) t else Time.at(today.plusDays(1), p.currentWake)
    }

    fun scheduleWake(context: Context) {
        val pi = wakeIntent(context, false, RC_WAKE)
        val next = nextWake(context)
        val am = am(context)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val show = PendingIntent.getActivity(
            context, RC_WAKE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canExact(context)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(next, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun snooze(context: Context, minutes: Int = 5) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = wakeIntent(context, true, RC_SNOOZE)
        val show = PendingIntent.getActivity(
            context, RC_SNOOZE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am(context).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (_: SecurityException) {
            am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---------------- Erinnerungen ----------------

    suspend fun reminders(context: Context): List<Reminder> {
        val e = Engine.get(context)
        val p = e.prefs
        val list = mutableListOf<Reminder>()
        for (h in e.activeItemsOnce()) {
            val m = h.reminderMinute ?: continue
            list += Reminder(h.key, h.title, "Mindestens: ${h.min}. Ganz: ${h.full}.", m, h.weekday)
        }
        if (p.phase >= 1) {
            list += Reminder(
                "_routine", "Abendroutine beginnt",
                "Bildschirme weg. Licht aus um ${Time.hm(e.lightsOut())}.", e.routineStart(), habit = false,
            )
        }
        list += Reminder(
            "_lights", "Licht aus",
            "Tipp auf \"Gute Nacht\", damit dein Schlaf gezählt wird.", e.lightsOut(), habit = false,
        )
        if (p.briefingsEnabled) {
            list += Reminder(
                "_morning", "Morgen-Check-in",
                "Eine Minute: Wie hast du geschlafen, wie geht's dir, was zählt heute?",
                (p.currentWake + p.morningBriefingOffset) % 1440, habit = false,
            )
            list += Reminder(
                "_evening", "Abend-Check-in",
                "Kurz eintragen, was heute war: Kaffee, Snus, Lesen, Freude. Dann ab in die Abendroutine.",
                ((e.routineStart() - 20) % 1440 + 1440) % 1440, habit = false,
            )
        }
        if (p.phase >= 2) {
            list += Reminder(
                "_review", "Wochenrückblick",
                "5 Minuten: Was lief gut, was nimmst du mit?", 19 * 60, java.time.DayOfWeek.SUNDAY, habit = false,
            )
        }
        return list
    }

    private fun remindIntent(context: Context, key: String): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_KEY, key)
        return PendingIntent.getBroadcast(context, 2000 + (key.hashCode() and 0xFFFF), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextOccurrence(r: Reminder, now: Long = System.currentTimeMillis()): Long {
        var d: LocalDate = Time.today()
        repeat(9) {
            val t = Time.at(d, r.minute)
            if (t > now + 15_000 && (r.weekday == null || d.dayOfWeek == r.weekday)) return t
            d = d.plusDays(1)
        }
        return Time.at(d, r.minute)
    }

    fun scheduleOne(context: Context, r: Reminder) {
        val at = nextOccurrence(r)
        val pi = remindIntent(context, r.key)
        val am = am(context)
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    suspend fun scheduleReminders(context: Context) {
        val p = Prefs.get(context)
        val old = p.sp.getStringSet(SCHEDULED_KEYS, emptySet()) ?: emptySet()
        val list = if (p.remindersEnabled && p.onboarded) reminders(context) else emptyList()
        val keys = list.map { it.key }.toSet()
        (old - keys).forEach { am(context).cancel(remindIntent(context, it)) }
        list.forEach { scheduleOne(context, it) }
        p.sp.edit().putStringSet(SCHEDULED_KEYS, keys).apply()
    }

    private fun followUpIntent(context: Context, key: String, attempt: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
            .putExtra(EXTRA_KEY, key).putExtra(EXTRA_ATTEMPT, attempt)
        return PendingIntent.getBroadcast(context, 6000 + (key.hashCode() and 0xFFFF), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Nachfassen: Wenn eine Gewohnheit nach der Erinnerung noch offen ist, kommt später nochmals eine.
     * Nie in der Nacht und nie nach Beginn der Abendroutine (ausser bei Gewohnheiten der Abendroutine selbst).
     */
    fun scheduleFollowUp(context: Context, key: String, attempt: Int) {
        val e = Engine.get(context)
        val p = e.prefs
        if (p.reminderRepeatMinutes <= 0 || attempt > p.reminderRepeatMax) return
        val at = System.currentTimeMillis() + p.reminderRepeatMinutes * 60_000L
        val m = Time.minuteOfDay(at)
        val lo = e.lightsOut()
        val sinceWake = ((m - p.currentWake) % 1440 + 1440) % 1440
        val untilLights = ((lo - p.currentWake) % 1440 + 1440) % 1440
        if (sinceWake >= untilLights || Time.dayOf(at) != Time.today()) return
        val pi = followUpIntent(context, key, attempt)
        try {
            am(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
        }
    }

    fun cancelFollowUp(context: Context, key: String) {
        am(context).cancel(followUpIntent(context, key, 0))
    }

    /** Prüft alle 30 Minuten Bildschirm-Budget und späte Nutzung. */
    fun scheduleScreenCheck(context: Context) {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK)
        val pi = PendingIntent.getBroadcast(context, RC_CHECK, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am(context).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR, pi,
        )
    }

    fun scheduleFocusEnd(context: Context, at: Long) {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FOCUS_END)
        val pi = PendingIntent.getBroadcast(context, RC_FOCUS, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = am(context)
        if (at <= 0) {
            am.cancel(pi)
            return
        }
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Alles neu planen (App-Start, Neustart, Einstellungen geändert). */
    suspend fun rescheduleAll(context: Context) {
        Engine.get(context).updateSleepShift()
        scheduleWake(context)
        scheduleReminders(context)
        scheduleScreenCheck(context)
        val p = Prefs.get(context)
        if (p.focusEndsAt > System.currentTimeMillis()) scheduleFocusEnd(context, p.focusEndsAt)
    }
}
