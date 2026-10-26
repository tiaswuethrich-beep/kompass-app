package ch.akh.kompass.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import ch.akh.kompass.alarm.Notify
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.plan.Engine
import java.time.LocalDate

data class AppUsage(val pkg: String, val label: String, val ms: Long, val entertainment: Boolean)

data class DayUsage(val total: Long, val entertainment: Long, val apps: List<AppUsage>)

/** Liest die echte Bildschirmzeit über den Nutzungszugriff von Android. */
object ScreenTime {
    private val knownEntertainment = setOf(
        "com.google.android.youtube", "com.google.android.apps.youtube.music",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.instagram.android",
        "com.netflix.mediaclient", "tv.twitch.android.app", "com.reddit.frontpage",
        "com.snapchat.android", "com.twitter.android", "com.facebook.katana",
        "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.valvesoftware.android.steam.community",
        "com.playstation.remoteplay", "com.microsoft.xboxone.smartglass", "com.discord",
        "com.pinterest", "com.vanced.android.youtube", "app.revanced.android.youtube",
    )

    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun launcherPackages(context: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
    }

    fun isEntertainment(context: Context, pkg: String): Boolean {
        if (pkg in knownEntertainment) return true
        val extra = Prefs.get(context).extraEntertainment.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (pkg in extra) return true
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            ai.category == ApplicationInfo.CATEGORY_GAME || ai.category == ApplicationInfo.CATEGORY_VIDEO
        } catch (_: Exception) {
            false
        }
    }

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    /** Vordergrundzeit pro App zwischen from und to (Millisekunden). */
    fun usage(context: Context, from: Long, to: Long): Map<String, Long> {
        if (!hasPermission(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val end = minOf(to, System.currentTimeMillis())
        if (end <= from) return emptyMap()
        val events = usm.queryEvents(from - 3 * 3600_000L, end)
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val e = UsageEvents.Event()
        fun close(pkg: String, at: Long) {
            val s = open.remove(pkg) ?: return
            val a = maxOf(s, from)
            val b = minOf(at, end)
            if (b > a) total[pkg] = (total[pkg] ?: 0L) + (b - a)
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                1 -> { // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                    // Ein anderes Paket im Vordergrund beendet alle offenen.
                    open.keys.filter { it != pkg }.forEach { close(it, e.timeStamp) }
                    if (pkg !in open) open[pkg] = e.timeStamp
                }
                2, 23 -> close(pkg, e.timeStamp) // ACTIVITY_PAUSED, ACTIVITY_STOPPED
                16, 26 -> open.keys.toList().forEach { close(it, e.timeStamp) } // Bildschirm aus, Gerät aus
            }
        }
        open.keys.toList().forEach { close(it, end) }
        val skip = launcherPackages(context) + context.packageName + "com.android.systemui"
        return total.filterKeys { it !in skip }
    }

    fun day(context: Context, d: LocalDate = Time.today()): DayUsage {
        val map = usage(context, Time.startOfDay(d), Time.endOfDay(d))
        val apps = map.entries.map { (pkg, ms) ->
            AppUsage(pkg, label(context, pkg), ms, isEntertainment(context, pkg))
        }.sortedByDescending { it.ms }
        return DayUsage(apps.sumOf { it.ms }, apps.filter { it.entertainment }.sumOf { it.ms }, apps)
    }

    /** Bildschirmzeit nach "Licht aus" in der letzten Nacht. */
    fun lateLastNight(context: Context, today: LocalDate = Time.today()): Long {
        val e = Engine.get(context)
        val lo = e.lightsOut()
        // Licht aus am Abend vor heute (oder kurz nach Mitternacht heute).
        val start = if (lo >= 12 * 60) Time.at(today.minusDays(1), lo) else Time.at(today, lo)
        val end = Time.at(today, e.prefs.currentWake)
        return usage(context, start, end).values.sum()
    }

    /** Alle 30 Minuten: Budget überschritten oder nach Licht aus noch am Handy? */
    fun check(context: Context) {
        if (!hasPermission(context)) return
        val p = Prefs.get(context)
        if (!p.onboarded) return
        val today = Time.todayStr()
        val e = Engine.get(context)
        if (p.phase >= 3 && p.lastBudgetWarn != today) {
            val d = day(context)
            val budget = p.entertainmentBudget * 60_000L
            if (d.entertainment > budget) {
                p.lastBudgetWarn = today
                Notify.simple(
                    context, Notify.ID_BUDGET, Notify.CH_COACH, "Unterhaltungs-Budget aufgebraucht",
                    "Heute schon ${Time.durMs(d.entertainment)} Games und Videos. Was wäre jetzt ein guter nächster Schritt?",
                    "screen",
                )
            }
        }
        if (p.phase >= 1) {
            val now = System.currentTimeMillis()
            val mod = Time.minuteOfDay(now)
            val lo = e.lightsOut()
            val since = Time.circularDiff(mod, lo)
            if (since in 20..180 && p.lastLateWarn != today) {
                val recent = usage(context, now - 20 * 60_000L, now).values.sum()
                if (recent > 10 * 60_000L) {
                    p.lastLateWarn = today
                    Notify.simple(
                        context, Notify.ID_LATE, Notify.CH_COACH, "Licht aus war um ${Time.hm(lo)}",
                        "Leg das Handy ausser Reichweite. Morgen klingelt der Wecker um ${Time.hm(p.currentWake)}.",
                        "today",
                    )
                }
            }
        }
    }
}
