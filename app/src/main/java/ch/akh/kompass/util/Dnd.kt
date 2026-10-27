package ch.akh.kompass.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import ch.akh.kompass.data.Prefs

/** Nicht-stören während Fokus-Blöcken und Akku-Ausnahme für zuverlässige Erinnerungen. */
object Dnd {
    fun granted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Schaltet "Nicht stören" ein, wenn erlaubt. Merkt sich, dass Kompass es eingeschaltet hat. */
    fun startFocus(context: Context) {
        if (!granted(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Prefs.get(context).sp.edit().putBoolean("dndByKompass", true).apply()
        }
    }

    /** Schaltet "Nicht stören" nur dann wieder aus, wenn Kompass es eingeschaltet hat. */
    fun endFocus(context: Context) {
        val sp = Prefs.get(context).sp
        if (!sp.getBoolean("dndByKompass", false)) return
        sp.edit().putBoolean("dndByKompass", false).apply()
        if (!granted(context)) return
        context.getSystemService(NotificationManager::class.java).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    /** Ab "Gute Nacht": Nicht stören an (Wecker klingeln trotzdem). */
    fun startNight(context: Context) {
        if (!granted(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Prefs.get(context).sp.edit().putBoolean("dndNight", true).apply()
        }
    }

    /** Bei "Wach": Nicht stören wieder aus, aber nur, wenn Kompass es eingeschaltet hat. */
    fun endNight(context: Context) {
        val sp = Prefs.get(context).sp
        if (!sp.getBoolean("dndNight", false)) return
        sp.edit().putBoolean("dndNight", false).apply()
        if (!granted(context)) return
        context.getSystemService(NotificationManager::class.java).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
