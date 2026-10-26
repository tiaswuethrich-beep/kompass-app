package ch.akh.kompass.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ch.akh.kompass.MainActivity
import ch.akh.kompass.R
import ch.akh.kompass.alarm.ActionReceiver
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.alarm.runAsync
import ch.akh.kompass.data.HabitLog
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.HabitItem
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Welche Bereiche das Widget zeigt. Gilt für alle Kompass-Widgets. */
object WidgetPrefs {
    const val HABITS = "w_habits"
    const val HIDE_DONE = "w_hide_done"
    const val WEEKLY = "w_weekly"
    const val SNUS = "w_snus"
    const val FOCUS = "w_focus"
    const val SCREEN = "w_screen"
    const val SLEEP = "w_sleep"
    const val COFFEE = "w_coffee"
    const val BRIEF = "w_brief"

    val defaults = mapOf(
        HABITS to true, HIDE_DONE to false, WEEKLY to true, SNUS to true, FOCUS to true, SCREEN to true,
        SLEEP to true, COFFEE to true, BRIEF to true,
    )

    fun get(context: Context, key: String): Boolean = Prefs.get(context).sp.getBoolean(key, defaults[key] ?: true)
    fun set(context: Context, key: String, v: Boolean) = Prefs.get(context).sp.edit().putBoolean(key, v).apply()
}

class KompassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { update(context) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        runAsync { update(context) }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val TEXT = 0xFFE8E9F3.toInt()
        val BAD = 0xFFFF6B6B.toInt()
        val GOOD = 0xFF34D399.toInt()
        val ON_GOOD = 0xFF04241A.toInt()

        /** Von überall aufrufbar, wenn sich Daten geändert haben. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { update(app) } }
        }

        private fun action(context: Context, action: String, rc: Int, type: String? = null): PendingIntent =
            PendingIntent.getBroadcast(
                context, rc, Intent(context, ActionReceiver::class.java).setAction(action).apply { if (type != null) putExtra("type", type) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun open(context: Context, route: String, rc: Int): PendingIntent =
            PendingIntent.getActivity(
                context, rc,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KompassWidget::class.java))
            if (ids.isEmpty()) return
            val e = Engine.get(context)
            val p = e.prefs
            val today = Time.today()
            val todayStr = today.toString()
            val phase = PhasePlan.phase(p.phase)

            val items = e.activeItemsOnce()
            val logs = e.dao.habitsSince(todayStr)
            val daily = items.filter { it.daily }
            val done = daily.count { h -> logs.any { it.key == h.key && it.date == todayStr } }
            val snus = e.dao.snusCount(0, Time.startOfDay(today), Time.endOfDay(today))
            val limit = e.snusLimit()
            val focus = e.dao.focusMinutesOnce(todayStr)
            val sleeping = e.dao.openSleep() != null
            val usage = if (WidgetPrefs.get(context, WidgetPrefs.SCREEN) && ScreenTime.hasPermission(context)) ScreenTime.day(context) else null
            val coffee = e.dao.substancesBetween(Time.startOfDay(today), Time.endOfDay(today)).count { it.type == "Kaffee" }
            val nowMin = Time.minuteOfDay(System.currentTimeMillis())
            val sinceWake = ((nowMin - p.currentWake) % 1440 + 1440) % 1440
            val untilLights = ((e.lightsOut() - p.currentWake) % 1440 + 1440) % 1440
            val brief = when {
                !p.briefingsEnabled -> null
                sinceWake < 6 * 60 && !p.briefingDone("morgen", todayStr) -> "morgen"
                sinceWake >= untilLights - 120 && sinceWake < untilLights + 180 && !p.briefingDone("abend", todayStr) -> "abend"
                else -> null
            }

            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_kompass)
                v.setTextViewText(R.id.w_title, "PHASE ${phase.index} · ${phase.name.uppercase()}")
                val wake = if (p.alarmEnabled) "Wecker ${Time.hm(p.currentWake)}" else "Wecker aus"
                v.setTextViewText(R.id.w_sub, "${Time.weekday(today)} ${Time.short(today)} · $done/${daily.size} erledigt\n$wake · Licht aus ${Time.hm(e.lightsOut())}")
                v.setOnClickPendingIntent(R.id.w_title, open(context, "today", 300))
                v.setOnClickPendingIntent(R.id.w_sub, open(context, "plan", 301))

                // Kennzahlen
                val showSnus = WidgetPrefs.get(context, WidgetPrefs.SNUS)
                val showCoffee = WidgetPrefs.get(context, WidgetPrefs.COFFEE)
                val showFocus = WidgetPrefs.get(context, WidgetPrefs.FOCUS)
                val showScreen = WidgetPrefs.get(context, WidgetPrefs.SCREEN)
                v.setViewVisibility(R.id.w_snus_box, if (showSnus) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_coffee_box, if (showCoffee) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_quick, if (showSnus || showCoffee) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_focus_box, if (showFocus) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_screen_box, if (showScreen) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_stats, if (showFocus || showScreen) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.w_snus, if (limit != null) "$snus / $limit" else "$snus")
                v.setTextColor(R.id.w_snus, if (limit != null && snus > limit) BAD else TEXT)
                v.setOnClickPendingIntent(R.id.w_snus_add, action(context, ActionReceiver.SNUS_ADD, 302))
                v.setOnClickPendingIntent(R.id.w_snus_minus, action(context, ActionReceiver.SNUS_UNDO, 308))
                v.setViewVisibility(R.id.w_snus_minus, if (snus > 0) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.w_coffee, "$coffee")
                v.setOnClickPendingIntent(R.id.w_coffee_add, action(context, ActionReceiver.SUBSTANCE_ADD, 309, "Kaffee"))
                v.setOnClickPendingIntent(R.id.w_coffee_minus, action(context, ActionReceiver.SUBSTANCE_UNDO, 310, "Kaffee"))
                v.setViewVisibility(R.id.w_coffee_minus, if (coffee > 0) View.VISIBLE else View.GONE)
                v.setOnClickPendingIntent(R.id.w_coffee_box, open(context, "substances", 311))
                val showBrief = brief != null && WidgetPrefs.get(context, WidgetPrefs.BRIEF)
                v.setViewVisibility(R.id.w_brief_btn, if (showBrief) View.VISIBLE else View.GONE)
                if (brief != null) {
                    v.setTextViewText(R.id.w_brief_btn, if (brief == "morgen") "Morgen-Check-in" else "Abend-Check-in")
                    v.setOnClickPendingIntent(R.id.w_brief_btn, open(context, "briefing/$brief", 312))
                }
                v.setOnClickPendingIntent(R.id.w_snus_box, open(context, "snus", 303))
                v.setTextViewText(R.id.w_focus, Time.dur(focus))
                v.setOnClickPendingIntent(R.id.w_focus_box, open(context, "focus", 304))
                v.setTextViewText(R.id.w_screen, usage?.let { Time.durMs(it.entertainment) } ?: "–")
                val over = usage != null && p.phase >= 3 && usage.entertainment > p.entertainmentBudget * 60_000L
                v.setTextColor(R.id.w_screen, if (over) BAD else TEXT)
                v.setOnClickPendingIntent(R.id.w_screen_box, open(context, "screen", 305))

                // Schlaf-Knopf
                v.setViewVisibility(R.id.w_sleep_btn, if (WidgetPrefs.get(context, WidgetPrefs.SLEEP)) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.w_sleep_btn, if (sleeping) "Ich bin wach" else "Gute Nacht")
                v.setOnClickPendingIntent(
                    R.id.w_sleep_btn,
                    action(context, if (sleeping) ActionReceiver.WAKE else ActionReceiver.GOOD_NIGHT, 306),
                )

                // Gewohnheiten-Liste
                val showHabits = WidgetPrefs.get(context, WidgetPrefs.HABITS)
                v.setViewVisibility(R.id.w_habits_head, if (showHabits) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_list, if (showHabits) View.VISIBLE else View.GONE)
                v.setViewVisibility(R.id.w_empty, View.GONE)
                if (showHabits) {
                    val svc = Intent(context, WidgetService::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
                    @Suppress("DEPRECATION")
                    v.setRemoteAdapter(R.id.w_list, svc)
                    v.setEmptyView(R.id.w_list, R.id.w_empty)
                    val template = PendingIntent.getBroadcast(
                        context, 307, Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.TOGGLE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    v.setPendingIntentTemplate(R.id.w_list, template)
                }
                manager.updateAppWidget(id, v)
            }
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
        }

        /** Die Gewohnheiten, die das Widget gerade zeigt. */
        suspend fun habitRows(context: Context): Pair<List<HabitItem>, List<HabitLog>> {
            val e = Engine.get(context)
            val today = Time.today()
            val logs = e.dao.habitsSince(Time.weekStart(today).toString())
            val todayStr = today.toString()
            val hideDone = WidgetPrefs.get(context, WidgetPrefs.HIDE_DONE)
            val weekly = WidgetPrefs.get(context, WidgetPrefs.WEEKLY)
            val items = e.activeItemsOnce().filter { h ->
                (h.daily || weekly) && !(hideDone && logs.any { it.key == h.key && it.date == todayStr })
            }.sortedBy { h -> if (logs.any { it.key == h.key && it.date == todayStr }) 1 else 0 }
            return items to logs
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(val context: Context) : RemoteViewsFactory {
        private var items: List<HabitItem> = emptyList()
        private var logs: List<HabitLog> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            val (i, l) = runBlocking { KompassWidget.habitRows(context) }
            items = i
            logs = l
        }
        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = items.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_habit_item)
            val h = items.getOrNull(position) ?: return v
            val today = Time.todayStr()
            val level = logs.firstOrNull { it.key == h.key && it.date == today }?.level ?: 0
            val week = logs.count { it.key == h.key && it.level >= 1 }
            v.setTextViewText(R.id.h_title, h.title + if (!h.daily) "  ($week/${h.perWeek})" else "")
            v.setTextColor(R.id.h_title, if (level > 0) KompassWidget.GOOD else KompassWidget.TEXT)
            v.setTextViewText(R.id.h_sub, if (level == 2) h.full else "Mindestens: ${h.min}")
            v.setInt(R.id.h_min, "setBackgroundResource", if (level == 1) R.drawable.chip_on else R.drawable.chip_off)
            v.setInt(R.id.h_full, "setBackgroundResource", if (level == 2) R.drawable.chip_on else R.drawable.chip_off)
            v.setTextColor(R.id.h_min, if (level == 1) KompassWidget.ON_GOOD else KompassWidget.TEXT)
            v.setTextColor(R.id.h_full, if (level == 2) KompassWidget.ON_GOOD else KompassWidget.TEXT)
            v.setOnClickFillInIntent(R.id.h_min, Intent().putExtra("key", h.key).putExtra("level", 1))
            v.setOnClickFillInIntent(R.id.h_full, Intent().putExtra("key", h.key).putExtra("level", 2))
            v.setOnClickFillInIntent(R.id.h_text, Intent().putExtra("key", h.key).putExtra("level", 2))
            return v
        }
    }
}
