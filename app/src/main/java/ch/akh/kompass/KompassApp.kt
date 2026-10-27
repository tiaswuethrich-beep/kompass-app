package ch.akh.kompass

import android.app.Application
import ch.akh.kompass.alarm.Notify
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KompassApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ch.akh.kompass.util.Time.store = Prefs.get(this).dayStore
        ch.akh.kompass.util.Time.wakeGuess = { d -> guessWake(d) }
        Notify.createChannels(this)
        scope.launch { runCatching { ch.akh.kompass.util.Time.repairAutoStarts(); Prefs.get(this@KompassApp).bump() } }
        if (Prefs.get(this).onboarded) {
            scope.launch { Scheduler.rescheduleAll(this@KompassApp) }
        }
    }

    /**
     * Aufstehzeit aus der Handynutzung: Ende der längsten Pause von mindestens 3 Stunden,
     * die zwischen 3 Uhr und der Frist endet. Ohne Nutzungszugriff gibt es keine Schätzung.
     */
    private fun guessWake(d: java.time.LocalDate): Long? {
        val T = ch.akh.kompass.util.Time
        val from = T.calStart(d) - 6 * 3600_000L
        val to = T.deadline(d)
        val sessions = ch.akh.kompass.util.ScreenTime.sessions(this, from, to)
        if (sessions.isEmpty()) return null
        val lo = T.at(d, T.EARLIEST_WAKE)
        var lastEnd = from
        var best: Long? = null
        var bestGap = 3 * 3600_000L
        for (s in sessions) {
            val gap = s.start - lastEnd
            if (gap >= bestGap && s.start in lo..to) { bestGap = gap; best = s.start }
            if (s.end > lastEnd) lastEnd = s.end
        }
        return best
    }
}
