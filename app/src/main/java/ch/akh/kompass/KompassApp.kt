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
        Notify.createChannels(this)
        if (Prefs.get(this).onboarded) {
            scope.launch { Scheduler.rescheduleAll(this@KompassApp) }
        }
    }
}
