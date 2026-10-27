package ch.akh.kompass.blocker

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import java.util.concurrent.Executors

/**
 * Blockiert Reels und Shorts und hilft am Feierabend beim Aufhören.
 * Läuft als Bedienungshilfe, weil Android nur so sehen lässt, welcher Bildschirm in einer App offen ist.
 * Es wird nichts gespeichert oder verschickt: Kompass schaut nur nach bekannten Bildschirm-Kennungen.
 */
class BlockerService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var overlay: View? = null
    private var overlayKind: String? = null
    private var currentPkg: String? = null
    private var previousPkg: String? = null
    private var enteredAt = 0L
    private var lastScan = 0L
    private var lastBlock = 0L
    /** YouTube wurde direkt aus einer anderen App geöffnet (z. B. geteilter Link): Shorts erlaubt, bis YouTube verlassen wird. */
    private var sharedYouTube = false
    private var checking = false

    private val tick = object : Runnable {
        override fun run() {
            checkTime()
            main.postDelayed(this, 30_000L)
        }
    }

    override fun onServiceConnected() {
        main.postDelayed(tick, 5_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != currentPkg) {
            previousPkg = currentPkg
            currentPkg = pkg
            enteredAt = System.currentTimeMillis()
            if (pkg == YT) {
                val from = previousPkg
                sharedYouTube = from != null && from != YT && from !in launchers() && from != packageName
            } else if (overlay != null && pkg !in launchers()) {
                // Neue App im Vordergrund: Blocker nur behalten, wenn auch sie Unterhaltung ist.
                if (!ScreenTime.isEntertainment(this, pkg)) removeOverlay()
            }
            if (pkg in launchers()) removeOverlay()
            checkTime()
        }
        val now = System.currentTimeMillis()
        if (now - lastScan < 400) return
        lastScan = now
        val p = Prefs.get(this)
        when {
            pkg == TIKTOK && p.blockTikTok -> block("TikTok ist blockiert")
            p.blockShorts && pkg == YT && !sharedYouTube && isYouTubeShorts() -> block("Shorts sind blockiert")
            p.blockShorts && pkg == IG && isInstagramReelsFeed() -> block("Reels sind blockiert. Reels aus DMs gehen weiterhin.")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        removeOverlay()
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---------------- Erkennung ----------------

    private fun has(root: AccessibilityNodeInfo, vararg ids: String): Boolean =
        ids.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } }

    private fun isYouTubeShorts(): Boolean {
        val root = rootInActiveWindow ?: return false
        return has(
            root,
            "$YT:id/reel_recycler", "$YT:id/reel_player_page_container", "$YT:id/reel_watch_player",
            "$YT:id/reel_progress_bar", "$YT:id/shorts_container",
        )
    }

    /**
     * Reels-Feed: der Reels-Player mit der unteren Tab-Leiste. Ein Reel aus einer DM öffnet sich
     * ohne Tab-Leiste und wird darum nicht blockiert.
     */
    private fun isInstagramReelsFeed(): Boolean {
        val root = rootInActiveWindow ?: return false
        val player = has(root, "$IG:id/clips_viewer_view_pager", "$IG:id/clips_viewer_container", "$IG:id/clips_video_container")
        if (!player) return false
        if (has(root, "$IG:id/direct_thread_header", "$IG:id/thread_title", "$IG:id/row_thread_composer_edittext")) return false
        return has(root, "$IG:id/tab_bar", "$IG:id/bottom_tab_bar")
    }

    private fun block(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 1500) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        Prefs.get(this).blockedCount = Prefs.get(this).blockedCount + 1
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------------- Feierabend und Tageslimit ----------------

    private fun eveningKey(): String = "eveningExt_${Time.todayStr()}"

    private fun checkTime() {
        val pkg = currentPkg ?: return
        if (checking || !ScreenTime.isEntertainment(this, pkg)) return
        checking = true
        worker.execute {
            try {
                val p = Prefs.get(this)
                val now = System.currentTimeMillis()
                var kind: String? = null
                var text = ""
                // Tageslimit (hart)
                if (p.hardLimit) {
                    val today = Time.today()
                    val ent = entertainment(Time.startOfDay(today), now)
                    if (ent >= p.entertainmentBudget * 60_000L) {
                        kind = "limit"
                        text = "Heute schon ${Time.durMs(ent)} Games und Videos. Dein Limit liegt bei ${Time.dur(p.entertainmentBudget)}. Für heute ist hier Schluss."
                    }
                }
                // Feierabend: Nachfrage
                if (kind == null && p.eveningStart >= 0) {
                    val since = eveningSince(p.eveningStart, now)
                    if (since != null) {
                        val ent = entertainment(since, now)
                        val ext = p.sp.getInt(eveningKey(), 0)
                        val allowed = (p.eveningAskAfter + ext * p.eveningExtend) * 60_000L
                        if (ent >= allowed) {
                            kind = "ask"
                            text = "Seit Feierabend schon ${Time.durMs(ent)} Videos und Games. Willst du wirklich weiterschauen?"
                        }
                    }
                }
                main.post {
                    checking = false
                    if (kind != null && currentPkg == pkg) showOverlay(kind, text)
                }
            } catch (_: Exception) {
                main.post { checking = false }
            }
        }
    }

    /** Beginn des laufenden Feierabends, oder null, wenn gerade kein Feierabend ist (Feierabend dauert bis 5 Uhr). */
    private fun eveningSince(start: Int, now: Long): Long? {
        val m = Time.minuteOfDay(now)
        val cal = Time.calDay(now)
        return when {
            m >= start -> Time.at(cal, start)
            m < 5 * 60 -> Time.at(cal.minusDays(1), start)
            else -> null
        }
    }

    private fun entertainment(from: Long, to: Long): Long =
        ScreenTime.usage(this, from, to).filterKeys { ScreenTime.isEntertainment(this, it) }.values.sum()

    // ---------------- Overlay ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun showOverlay(kind: String, text: String) {
        if (overlay != null && overlayKind == kind) return
        removeOverlay()
        val ctx: Context = this
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF7141530.toInt(), 0xF70D1A26.toInt()))
            isClickable = true
        }
        fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            this.text = t
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }
        fun button(t: String, primary: Boolean, onClick: () -> Unit) = Button(ctx).apply {
            this.text = t
            isAllCaps = false
            textSize = 16f
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFFE8E9F3.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                if (primary) colors = intArrayOf(0xFF8B7CFF.toInt(), 0xFF5B8DEF.toInt())
                else { setColor(0x00000000); setStroke(dp(1), 0xFF4A4F70.toInt()) }
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(12) }
        }
        if (kind == "ask") {
            box.addView(label("Feierabend", 14f, 0xFF2DD4BF.toInt(), true))
            box.addView(label(text, 20f, 0xFFE8E9F3.toInt(), true))
            box.addView(label("Morgen bist du froh um jede Minute Schlaf mehr.", 15f, 0xFFAEB2C8.toInt()))
            box.addView(button("Für heute Schluss", true) {
                removeOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            })
            box.addView(button("Noch ${Prefs.get(this).eveningExtend} Minuten", false) {
                val p = Prefs.get(this)
                p.sp.edit().putInt(eveningKey(), p.sp.getInt(eveningKey(), 0) + 1).apply()
                removeOverlay()
            })
        } else {
            box.addView(label("Tageslimit erreicht", 14f, 0xFFFF7A85.toInt(), true))
            box.addView(label(text, 20f, 0xFFE8E9F3.toInt(), true))
            box.addView(label("Lesen, raus an die Luft oder Gitarre? Morgen gibt's wieder neue Zeit.", 15f, 0xFFAEB2C8.toInt()))
            box.addView(button("Zum Homescreen", true) {
                removeOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            })
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            getSystemService(WindowManager::class.java).addView(box, lp)
            overlay = box
            overlayKind = kind
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        overlay?.let { v -> try { getSystemService(WindowManager::class.java).removeView(v) } catch (_: Exception) {} }
        overlay = null
        overlayKind = null
    }

    private var launcherCache: Set<String>? = null
    private fun launchers(): Set<String> = launcherCache ?: run {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet().also { launcherCache = it }
    }

    companion object {
        const val YT = "com.google.android.youtube"
        const val IG = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"

        fun enabled(context: Context): Boolean {
            val expected = ComponentName(context, BlockerService::class.java).flattenToString()
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val split = TextUtils.SimpleStringSplitter(':').apply { setString(list) }
            return split.any { it.equals(expected, ignoreCase = true) }
        }

        fun settingsIntent() = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
