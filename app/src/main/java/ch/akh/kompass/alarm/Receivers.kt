package ch.akh.kompass.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.akh.kompass.R
import ch.akh.kompass.data.FocusSession
import ch.akh.kompass.data.HabitLog
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Führt Arbeit im Hintergrund aus, ohne dass Android den Receiver zu früh beendet. */
fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_WAKE) return
        val snooze = intent.getBooleanExtra(Scheduler.EXTRA_SNOOZE, false)
        showAlarm(context)
        if (!snooze) runAsync { Scheduler.scheduleWake(context) }
    }

    companion object {
        fun showAlarm(context: Context) {
            val full = PendingIntent.getActivity(
                context, 77, Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val wake = PendingIntent.getBroadcast(
                context, 78, Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.WAKE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, Notify.CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Guten Morgen")
                .setContentText("Zeit aufzustehen. Licht, Bett, los.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(0, "Ich bin wach", wake)
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT
            if (Notify.canPost(context)) {
                try {
                    NotificationManagerCompat.from(context).notify(Notify.ID_ALARM, n)
                } catch (_: SecurityException) {
                }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_REMIND -> runAsync { remind(context, intent.getStringExtra(Scheduler.EXTRA_KEY) ?: return@runAsync) }
            Scheduler.ACTION_CHECK -> runAsync { ScreenTime.check(context) }
            Scheduler.ACTION_FOCUS_END -> runAsync { focusEnd(context) }
        }
    }

    private suspend fun remind(context: Context, key: String) {
        val e = Engine.get(context)
        val today = Time.todayStr()
        val all = Scheduler.reminders(context)
        val r = all.firstOrNull { it.key == key }
        if (r != null) {
            if (r.habit) {
                e.syncAuto()
                val done = e.dao.habitsSince(today).any { it.date == today && it.key == key }
                if (!done) {
                    Notify.simple(
                        context, notifId(key), Notify.CH_REMIND, r.title, r.text, "today",
                        listOf(
                            habitAction(context, key, 1, "Mindestens erledigt"),
                            habitAction(context, key, 2, "Ganz erledigt"),
                        ),
                    )
                }
            } else {
                val actions = if (key == "_lights") {
                    listOf(
                        NotificationCompat.Action(
                            0, "Gute Nacht",
                            PendingIntent.getBroadcast(
                                context, 79, Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.GOOD_NIGHT),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ),
                    )
                } else emptyList()
                val route = if (key == "_review") "mood" else "today"
                Notify.simple(context, if (key == "_lights") Notify.ID_LIGHTS else notifId(key), Notify.CH_REMIND, r.title, r.text, route, actions)
            }
        }
        Scheduler.scheduleReminders(context)
    }

    private suspend fun focusEnd(context: Context) {
        val e = Engine.get(context)
        val p = e.prefs
        val end = p.focusEndsAt
        if (end <= 0) return
        val start = end - p.focusMinutes * 60_000L
        e.dao.insertFocus(FocusSession(date = Time.dayOf(start).toString(), startedAt = start, minutes = p.focusMinutes, label = p.focusLabel))
        p.focusEndsAt = 0
        e.syncAuto()
        Notify.simple(context, Notify.ID_FOCUS, Notify.CH_FOCUS, "Fokus-Block fertig",
            "${p.focusMinutes} Minuten geschafft. Steh auf, trink Wasser, 5 bis 10 Minuten Pause.", "focus")
    }

    companion object {
        fun notifId(key: String) = 100 + (key.hashCode() and 0xFFF)

        fun habitAction(context: Context, key: String, level: Int, label: String): NotificationCompat.Action {
            val i = Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.HABIT)
                .putExtra("key", key).putExtra("level", level)
            val pi = PendingIntent.getBroadcast(context, (key.hashCode() and 0xFFFF) * 4 + level, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action(0, label, pi)
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val e = Engine.get(context)
        when (intent.action) {
            HABIT -> {
                val key = intent.getStringExtra("key") ?: return
                val level = intent.getIntExtra("level", 2)
                Notify.cancel(context, ReminderReceiver.notifId(key))
                runAsync { e.dao.upsertHabit(HabitLog(Time.todayStr(), key, level)) }
            }
            GOOD_NIGHT -> {
                Notify.cancel(context, Notify.ID_LIGHTS)
                runAsync { e.goodNight() }
            }
            WAKE -> {
                Notify.cancel(context, Notify.ID_ALARM)
                context.sendBroadcast(Intent(AlarmActivity.ACTION_STOP).setPackage(context.packageName))
                runAsync {
                    e.wake()
                    Scheduler.rescheduleAll(context)
                }
            }
        }
    }

    companion object {
        const val HABIT = "ch.akh.kompass.HABIT"
        const val GOOD_NIGHT = "ch.akh.kompass.GOOD_NIGHT"
        const val WAKE = "ch.akh.kompass.WAKE"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notify.createChannels(context)
        runAsync { Scheduler.rescheduleAll(context) }
    }
}
