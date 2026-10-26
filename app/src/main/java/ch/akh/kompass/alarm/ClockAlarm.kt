package ch.akh.kompass.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.util.Time

/**
 * Stellt den Wecker in der Uhr-App des Handys (bei Samsung: Samsung Uhr).
 * Android erlaubt das nur, solange Kompass offen ist. Darum stellt Kompass den nächsten Wecker
 * bei jedem Öffnen und erinnert abends, falls er für morgen noch fehlt.
 * Es wird ein einmaliger Wecker mit dem Namen "Kompass" gestellt. Die Uhr-App verwendet einen
 * bestehenden Wecker mit gleicher Zeit und gleichem Namen wieder.
 */
object ClockAlarm {
    const val LABEL = "Kompass"

    /** Schlüssel für den nächsten nötigen Wecker, null wenn der Wecker aus ist. */
    fun neededKey(context: Context): String? {
        val next = Scheduler.nextWake(context) ?: return null
        return "${Time.dayOf(next)}@${Time.minuteOfDay(next)}"
    }

    fun active(context: Context): Boolean {
        val p = Prefs.get(context)
        return p.alarmEnabled && p.alarmViaClock
    }

    fun isSet(context: Context): Boolean = neededKey(context)?.let { it == Prefs.get(context).clockAlarmSet } ?: true

    /** Muss aus einer sichtbaren Activity aufgerufen werden. Gibt true zurück, wenn es geklappt hat. */
    fun ensure(activityContext: Context, force: Boolean = false): Boolean {
        if (!active(activityContext)) return false
        val key = neededKey(activityContext) ?: return false
        val p = Prefs.get(activityContext)
        if (!force && p.clockAlarmSet == key) return true
        val minute = key.substringAfter('@').toInt()
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, minute / 60)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute % 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            .putExtra(AlarmClock.EXTRA_VIBRATE, true)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            activityContext.startActivity(i)
            p.clockAlarmSet = key
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Öffnet die Wecker-Liste der Uhr-App, z. B. um alte Kompass-Wecker zu löschen. */
    fun openClock(context: Context) {
        try {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
