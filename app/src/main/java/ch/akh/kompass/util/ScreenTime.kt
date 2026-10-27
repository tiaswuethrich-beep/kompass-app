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

data class AppUsage(val pkg: String, val label: String, val ms: Long, val entertainment: Boolean, val category: String = "Andere", val count: Int = 0)

data class Session(val pkg: String, val start: Long, val end: Long)

data class DayUsage(
    val total: Long,
    val entertainment: Long,
    val apps: List<AppUsage>,
    val sessions: List<Session> = emptyList(),
    /** Minuten pro Stunde des Tages (24 Werte). */
    val perHour: List<Float> = List(24) { 0f },
    /** Zeit pro Kategorie, absteigend. */
    val byCategory: List<Pair<String, Long>> = emptyList(),
    /** Summe nur aus den Ereignissen bzw. nur aus der Android-Statistik, zum Vergleichen. */
    val eventsTotal: Long = 0,
    val systemTotal: Long = 0,
)

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

    private fun csv(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun isEntertainment(context: Context, pkg: String): Boolean {
        val p = Prefs.get(context)
        if (pkg in csv(p.excludedEntertainment)) return false
        if (pkg in csv(p.extraEntertainment)) return true
        if (pkg in knownEntertainment) return true
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

    /**
     * Alle Nutzungs-Sitzungen zwischen from und to, direkt aus den Android-Ereignissen.
     * Gezählt wird die sichtbare Zeit einer App (von "geöffnet" bis "nicht mehr sichtbar"),
     * so wie Android sie für die eigene Bildschirmzeit nutzt. Bildschirm aus beendet alles.
     */
    fun sessions(context: Context, from: Long, to: Long): List<Session> {
        if (!hasPermission(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = minOf(to, System.currentTimeMillis())
        if (end <= from) return emptyList()
        val events = usm.queryEvents(from - 12 * 3600_000L, end)
        val hasStop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val visible = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val out = ArrayList<Session>()
        val e = UsageEvents.Event()
        fun add(pkg: String, s: Long, at: Long) {
            val a = maxOf(s, from)
            val b = minOf(at, end)
            if (b > a) out += Session(pkg, a, b)
        }
        fun hide(pkg: String, act: String, at: Long) {
            val set = visible[pkg] ?: return
            if (set.remove(act) && set.isEmpty()) {
                since.remove(pkg)?.let { add(pkg, it, at) }
                visible.remove(pkg)
            }
        }
        fun closeAll(at: Long) {
            for ((pkg, s) in since) add(pkg, s, at)
            since.clear()
            visible.clear()
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val act = e.className ?: pkg
            when (e.eventType) {
                1 -> { // ACTIVITY_RESUMED
                    val set = visible.getOrPut(pkg) { mutableSetOf() }
                    if (set.isEmpty()) since[pkg] = e.timeStamp
                    set += act
                }
                2 -> if (!hasStop) hide(pkg, act, e.timeStamp) // ACTIVITY_PAUSED, nur vor Android 10
                23, 24 -> hide(pkg, act, e.timeStamp) // ACTIVITY_STOPPED, ACTIVITY_DESTROYED
                16, 26 -> closeAll(e.timeStamp) // Bildschirm aus, Gerät aus
            }
        }
        closeAll(end)
        val skip = launcherPackages(context) + context.packageName + "com.android.systemui"
        val pm = context.packageManager
        val launchable = HashMap<String, Boolean>()
        return out.filter { s ->
            s.pkg !in skip && launchable.getOrPut(s.pkg) { pm.getLaunchIntentForPackage(s.pkg) != null }
        }.sortedBy { it.start }
    }

    /** Sichtbare Zeit pro App zwischen from und to (Millisekunden), aus den Ereignissen. */
    fun usage(context: Context, from: Long, to: Long): Map<String, Long> =
        sessions(context, from, to).groupBy { it.pkg }.mapValues { (_, l) -> l.sumOf { it.end - it.start } }

    /**
     * Die Tageswerte, die Android selbst speichert (dieselbe Quelle wie viele Bildschirmzeit-Anzeigen).
     * Nur Tages-Pakete, die an diesem Tag beginnen, damit nichts vom Vortag mitgezählt wird.
     */
    fun systemUsage(context: Context, d: LocalDate): Map<String, Long> {
        if (!hasPermission(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        // Android speichert Kalendertage, darum hier Mitternacht als Grenze.
        val start = Time.calStart(d)
        val end = minOf(Time.calStart(d.plusDays(1)), System.currentTimeMillis())
        val out = HashMap<String, Long>()
        val list = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()
        for (s in list) {
            // Android beginnt die Tages-Pakete nicht immer genau um Mitternacht, darum etwas Spielraum.
            if (s.firstTimeStamp < start - 3 * 3600_000L || s.firstTimeStamp >= end) continue
            val ms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) maxOf(s.totalTimeVisible, s.totalTimeInForeground) else s.totalTimeInForeground
            if (ms > 0) out[s.packageName] = (out[s.packageName] ?: 0L) + ms
        }
        val skip = launcherPackages(context) + context.packageName + "com.android.systemui"
        val pm = context.packageManager
        return out.filterKeys { it !in skip && pm.getLaunchIntentForPackage(it) != null }
    }

    /** Tagesübersicht: Apps, Kategorien und Verteilung über die Stunden. */
    fun day(context: Context, d: LocalDate = Time.today()): DayUsage {
        val from = Time.startOfDay(d)
        val to = Time.endOfDay(d)
        val ss = sessions(context, from, to)
        val events = ss.groupBy { it.pkg }.mapValues { (_, l) -> l.sumOf { it.end - it.start } }
        val counts = ss.groupingBy { it.pkg }.eachCount()
        val source = Prefs.get(context).screenSource
        val system = if (source == 1) emptyMap() else systemUsage(context, d)
        val merged = when (source) {
            1 -> events
            2 -> system
            else -> (events.keys + system.keys).associateWith { maxOf(events[it] ?: 0L, system[it] ?: 0L) }
        }
        val apps = merged.filterValues { it >= 1000L }.map { (pkg, ms) ->
            AppUsage(pkg, label(context, pkg), ms, isEntertainment(context, pkg), category(context, pkg), counts[pkg] ?: 0)
        }.sortedByDescending { it.ms }
        val perHour = FloatArray(24)
        for (s in ss) {
            var t = s.start
            while (t < s.end) {
                val h = Time.minuteOfDay(t) / 60
                val next = minOf(s.end, Time.at(Time.calDay(t), (h + 1) * 60))
                if (next <= t) break
                perHour[h] += (next - t) / 60_000f
                t = next
            }
        }
        val byCat = apps.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.ms } }.sortedByDescending { it.second }
        return DayUsage(
            total = apps.sumOf { it.ms },
            entertainment = apps.filter { it.entertainment }.sumOf { it.ms },
            apps = apps, sessions = ss, perHour = perHour.toList(), byCategory = byCat,
            eventsTotal = events.values.sum(), systemTotal = system.values.sum(),
        )
    }

    fun category(context: Context, pkg: String): String {
        knownCategory[pkg]?.let { return it }
        return try {
            when (context.packageManager.getApplicationInfo(pkg, 0).category) {
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_VIDEO -> "Videos"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social Media"
                ApplicationInfo.CATEGORY_AUDIO -> "Musik"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produktiv"
                ApplicationInfo.CATEGORY_MAPS -> "Unterwegs"
                else -> "Andere"
            }
        } catch (_: Exception) {
            "Andere"
        }
    }

    private val knownCategory = mapOf(
        "com.google.android.youtube" to "Videos", "com.netflix.mediaclient" to "Videos", "tv.twitch.android.app" to "Videos",
        "com.disney.disneyplus" to "Videos", "com.amazon.avod.thirdpartyclient" to "Videos",
        "com.instagram.android" to "Social Media", "com.zhiliaoapp.musically" to "Social Media", "com.ss.android.ugc.trill" to "Social Media",
        "com.snapchat.android" to "Social Media", "com.twitter.android" to "Social Media", "com.facebook.katana" to "Social Media",
        "com.reddit.frontpage" to "Social Media", "com.pinterest" to "Social Media", "com.discord" to "Chats",
        "com.whatsapp" to "Chats", "org.telegram.messenger" to "Chats", "org.thoughtcrime.securesms" to "Chats",
        "com.google.android.apps.messaging" to "Chats", "com.spotify.music" to "Musik",
        "com.android.chrome" to "Browser", "org.mozilla.firefox" to "Browser", "com.brave.browser" to "Browser",
        "com.sec.android.app.sbrowser" to "Browser", "com.google.android.googlequicksearchbox" to "Browser",
        "com.anthropic.claude" to "Produktiv", "com.openai.chatgpt" to "Produktiv",
        "ch.sbb.mobile.android.b2c" to "Unterwegs",
    )

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
