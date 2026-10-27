package ch.akh.kompass.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.akh.kompass.MainActivity
import ch.akh.kompass.R

object Notify {
    const val CH_ALARM = "wecker_v1"
    const val CH_REMIND = "erinnerung"
    const val CH_COACH = "coach"
    const val CH_FOCUS = "fokus"

    const val ID_ALARM = 1
    const val ID_FOCUS = 2
    const val ID_BUDGET = 3
    const val ID_LATE = 4
    const val ID_LIGHTS = 5

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Dein Wecker zur Ziel-Aufstehzeit"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val remind = NotificationChannel(CH_REMIND, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Erinnerungen an deine Gewohnheiten"
        }
        val coach = NotificationChannel(CH_COACH, "Coach", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Bildschirm-Budget, Schlafenszeit und Hinweise"
        }
        val focus = NotificationChannel(CH_FOCUS, "Fokus", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ende eines Fokus-Blocks"
        }
        nm.createNotificationChannels(listOf(alarm, remind, coach, focus))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context, route: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context, (route ?: "home").hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun simple(context: Context, id: Int, channel: String, title: String, text: String, route: String? = null,
               actions: List<NotificationCompat.Action> = emptyList()) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, route))
        actions.forEach { b.addAction(it) }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** Nach "Gute Nacht": alle offenen Hinweise wegräumen, damit nachts nichts mehr aufleuchtet. */
    fun clearForNight(context: Context) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        try {
            nm.activeNotifications.filter { it.id != ID_ALARM && it.id != ID_FOCUS }.forEach { nm.cancel(it.tag, it.id) }
        } catch (_: Exception) {
            nm.cancelAll()
        }
    }
}
