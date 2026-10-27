package ch.akh.kompass.ai

import android.content.Context
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.data.AppDatabase
import ch.akh.kompass.data.CustomHabit
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Kompass anpassen": Akh schreibt einen Wunsch, Claude Haiku übersetzt ihn in eine Liste
 * von Änderungen aus einem festen Katalog. Nichts wird ohne Bestätigung übernommen, jede
 * Änderung lässt sich rückgängig machen, und Werte bleiben innerhalb fester Grenzen.
 */
object Customize {

    // ---------------- Katalog der Einstellungen ----------------

    enum class Kind { INT, BOOL, TIME, MONEY }

    class Setting(
        val key: String,
        val label: String,
        val kind: Kind,
        val min: Double = 0.0,
        val max: Double = 0.0,
        val get: (Context) -> Any,
        val set: (Context, Any) -> Unit,
    )

    private fun p(c: Context) = Prefs.get(c)

    val settings: List<Setting> = listOf(
        Setting("name", "Name in der Begrüssung", Kind.INT, get = { p(it).name }, set = { c, v -> p(c).name = v.toString().take(30) }),
        Setting("remindersEnabled", "Erinnerungen an/aus", Kind.BOOL, get = { p(it).remindersEnabled }, set = { c, v -> p(c).remindersEnabled = v as Boolean }),
        Setting("reminderRepeatMinutes", "Nachfassen nach Minuten (0 = nie)", Kind.INT, 0.0, 240.0, { p(it).reminderRepeatMinutes }, { c, v -> p(c).reminderRepeatMinutes = v as Int }),
        Setting("reminderRepeatMax", "Wie oft nachfassen", Kind.INT, 1.0, 5.0, { p(it).reminderRepeatMax }, { c, v -> p(c).reminderRepeatMax = v as Int }),
        Setting("briefingsEnabled", "Morgen- und Abend-Check-in an/aus", Kind.BOOL, get = { p(it).briefingsEnabled }, set = { c, v -> p(c).briefingsEnabled = v as Boolean }),
        Setting("morningBriefingOffset", "Morgen-Check-in Minuten nach dem Aufstehen", Kind.INT, 0.0, 240.0, { p(it).morningBriefingOffset }, { c, v -> p(c).morningBriefingOffset = v as Int }),
        Setting("goalWake", "Endziel Aufstehzeit", Kind.TIME, 300.0, 600.0, { p(it).goalWake }, { c, v -> p(c).goalWake = v as Int }),
        Setting("sleepNeedMinutes", "Schlafbedarf in Minuten", Kind.INT, 360.0, 600.0, { p(it).sleepNeedMinutes }, { c, v -> p(c).sleepNeedMinutes = v as Int }),
        Setting("stepGoal", "Schrittziel", Kind.INT, 2000.0, 20000.0, { p(it).stepGoal }, { c, v -> p(c).stepGoal = v as Int }),
        Setting("stepGoalMax", "Schrittziel steigt bis", Kind.INT, 4000.0, 25000.0, { p(it).stepGoalMax }, { c, v -> p(c).stepGoalMax = v as Int }),
        Setting("focusMinutes", "Länge Fokusblock in Minuten", Kind.INT, 10.0, 120.0, { p(it).focusMinutes }, { c, v -> p(c).focusMinutes = v as Int }),
        Setting("entertainmentBudget", "Tagesbudget Unterhaltung in Minuten", Kind.INT, 0.0, 600.0, { p(it).entertainmentBudget }, { c, v -> p(c).entertainmentBudget = v as Int }),
        Setting("blockShorts", "Reels und Shorts blockieren", Kind.BOOL, get = { p(it).blockShorts }, set = { c, v -> p(c).blockShorts = v as Boolean }),
        Setting("blockTikTok", "TikTok blockieren", Kind.BOOL, get = { p(it).blockTikTok }, set = { c, v -> p(c).blockTikTok = v as Boolean }),
        Setting("eveningStart", "Feierabend ab (Uhrzeit, -1 = aus)", Kind.TIME, -1.0, 1439.0, { p(it).eveningStart }, { c, v -> p(c).eveningStart = v as Int }),
        Setting("eveningAskAfter", "Nachfrage nach Minuten Unterhaltung am Feierabend", Kind.INT, 5.0, 120.0, { p(it).eveningAskAfter }, { c, v -> p(c).eveningAskAfter = v as Int }),
        Setting("eveningExtend", "Verlängerung nach Nachfrage in Minuten", Kind.INT, 5.0, 60.0, { p(it).eveningExtend }, { c, v -> p(c).eveningExtend = v as Int }),
        Setting("hardLimit", "Hartes Tageslimit an/aus", Kind.BOOL, get = { p(it).hardLimit }, set = { c, v -> p(c).hardLimit = v as Boolean }),
        Setting("kcalGoal", "Kalorienziel pro Tag", Kind.INT, 1500.0, 4500.0, { Meals.kcalGoal(it) }, { c, v -> Meals.setKcalGoal(c, v as Int) }),
        Setting("monthlyIncome", "Monatliches Einkommen CHF", Kind.MONEY, 0.0, 20000.0, { p(it).monthlyIncome.toDouble() }, { c, v -> p(c).monthlyIncome = (v as Double).toFloat() }),
        Setting("savingsGoal", "Sparziel pro Monat CHF", Kind.MONEY, 0.0, 10000.0, { p(it).savingsGoal.toDouble() }, { c, v -> p(c).savingsGoal = (v as Double).toFloat() }),
        Setting("aiBudget", "KI-Monatsbudget in Dollar (höchstens 10)", Kind.MONEY, 0.1, 10.0, { AiStore.get(it).monthlyBudget.toDouble() }, { c, v -> AiStore.get(c).monthlyBudget = (v as Double).toFloat() }),
        Setting("aiDaily", "KI-Anfragen pro Tag", Kind.INT, 5.0, 200.0, { AiStore.get(it).dailyLimit }, { c, v -> AiStore.get(c).dailyLimit = v as Int }),
    )

    // ---------------- Aussehen ----------------

    /** Abschnitte auf "Heute", die man ausblenden kann (Key wie in Today.kt). */
    val sections = linkedMapOf(
        "briefing" to "Check-in-Karte",
        "intention" to "Heute zählt",
        "ai_entry" to "KI-Impuls und Coach-Knopf",
        "quick" to "Schnell eintragen",
        "habits" to "Gewohnheiten",
        "sleep" to "Schlaf",
        "snus" to "Snus",
        "steps" to "Schritte",
        "focus" to "Fokus",
        "screen" to "Bildschirmzeit",
        "mood" to "Stimmung",
        "events" to "Termine",
        "tip" to "Tipp der Phase",
    )

    val accents = linkedMapOf(
        "violett" to 0xFF8B7CFF, "türkis" to 0xFF2DD4BF, "blau" to 0xFF60A5FA, "grün" to 0xFF34D399,
        "pink" to 0xFFF472B6, "orange" to 0xFFFB923C, "rot" to 0xFFFF6B6B, "indigo" to 0xFF818CF8,
    )

    fun hidden(c: Context): Set<String> = p(c).sp.getString("ui_hidden", "")!!.split(',').filter { it.isNotBlank() }.toSet()
    fun accent(c: Context): Long? = p(c).sp.getLong("ui_accent", 0L).takeIf { it != 0L }
    fun textScale(c: Context): Float = p(c).sp.getFloat("ui_text", 1f)
    /** "system", "dark" oder "light" */
    fun themeMode(c: Context): String = p(c).sp.getString("ui_mode", "system") ?: "system"

    // ---------------- Änderungen ----------------

    sealed class Change(val text: String) {
        class SetValue(val setting: Setting, val value: Any, text: String) : Change(text)
        class Habit(val key: String, val on: Boolean, text: String) : Change(text)
        class NewHabit(val title: String, val min: String, val full: String, val perWeek: Int, text: String) : Change(text)
        class Hide(val section: String, val hide: Boolean, text: String) : Change(text)
        class Accent(val color: Long, text: String) : Change(text)
        class TextSize(val scale: Float, text: String) : Change(text)
        class Mode(val mode: String, text: String) : Change(text)
        /** Geht nur mit einem App-Update: wird als Wunsch gesammelt. */
        class Wish(val wish: String) : Change("Wunschliste: $wish")
    }

    data class Plan(val changes: List<Change>, val reply: String)

    private fun fmt(s: Setting, v: Any): String = when (s.kind) {
        Kind.TIME -> if (v == -1) "aus" else Time.hm(v as Int)
        Kind.BOOL -> if (v == true) "an" else "aus"
        Kind.MONEY -> "%.2f".format(v as Double)
        Kind.INT -> v.toString()
    }

    private suspend fun catalog(c: Context): String = withContext(Dispatchers.IO) {
        val e = Engine.get(c)
        val sb = StringBuilder()
        sb.append("EINSTELLUNGEN (key: Beschreibung = aktueller Wert [Grenzen]):\n")
        settings.forEach { s ->
            val v = s.get(c)
            val range = when (s.kind) { Kind.BOOL -> "true/false"; Kind.TIME -> "Minuten nach Mitternacht"; else -> if (s.key == "name") "Text" else "${s.min.toInt()}..${s.max.toInt()}" }
            sb.append("- ${s.key}: ${s.label} = ${if (s.key == "name") v else fmt(s, v)} [$range]\n")
        }
        sb.append("GEWOHNHEITEN (key: Titel = an/aus):\n")
        e.builtInItems().forEach { h -> sb.append("- ${h.key}: ${h.title} = ${if (e.prefs.habitDisabled(h.key)) "aus" else "an"}\n") }
        sb.append("ABSCHNITTE AUF HEUTE (key: Name = sichtbar/versteckt):\n")
        val hid = hidden(c)
        sections.forEach { (k, n) -> sb.append("- $k: $n = ${if (k in hid) "versteckt" else "sichtbar"}\n") }
        sb.append("AKZENTFARBEN: ${accents.keys.joinToString(", ")} (aktuell ${accents.entries.firstOrNull { it.value == accent(c) }?.key ?: "violett"})\n")
        sb.append("TEXTGRÖSSE: 0.85 bis 1.3 (aktuell ${textScale(c)}), MODUS: system/dark/light (aktuell ${themeMode(c)})\n")
        sb.toString()
    }

    private const val TASK = """Übersetze den Wunsch in Änderungen an der App. Erlaubt sind NUR diese Aktionen:
{"type":"set","key":"<Einstellung>","value":<Zahl|true|false|"Text">}  (Uhrzeiten als Minuten nach Mitternacht)
{"type":"habit","key":"<Gewohnheit>","on":true|false}
{"type":"new_habit","title":"...","min":"Mindestversion","full":"Vollversion","perWeek":1-7}
{"type":"hide","section":"<Abschnitt>","hide":true|false}
{"type":"accent","color":"<Farbname>"}
{"type":"text","scale":0.85-1.3}
{"type":"mode","mode":"system|dark|light"}
{"type":"wish","text":"..."}  (für alles, was nur mit einem App-Update geht, z. B. neue Bildschirme oder Funktionen)
Antworte NUR mit JSON: {"reply":"ein bis zwei Sätze an den Nutzer, was du änderst","actions":[...]}
Ändere nur, was gewünscht ist. Bleib in den Grenzen. Wenn der Wunsch unklar ist: keine Aktionen und im reply nachfragen."""

    suspend fun propose(c: Context, wish: String): Result<Plan> {
        val r = Ai.tool(c, TASK + "\n\n" + catalog(c), AiTurn(true, wish.take(500)), 600)
        if (r is AiResult.Fail) return Result.failure(Exception(r.message))
        val text = (r as AiResult.Ok).text
        return runCatching {
            val o = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
            Plan(parse(c, o.optJSONArray("actions") ?: JSONArray()), o.optString("reply"))
        }.recoverCatching { error("Antwort nicht lesbar. Formuliere den Wunsch etwas anders.") }
    }

    private fun parse(c: Context, arr: JSONArray): List<Change> {
        val out = mutableListOf<Change>()
        val builtIn = Engine.get(c).builtInItems().associateBy { it.key }
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            when (a.optString("type")) {
                "set" -> {
                    val s = settings.firstOrNull { it.key == a.optString("key") } ?: continue
                    val raw = a.opt("value")
                    val v: Any = when {
                        s.key == "name" -> raw.toString().take(30)
                        s.kind == Kind.BOOL -> (raw as? Boolean) ?: raw.toString().toBooleanStrictOrNull() ?: continue
                        s.kind == Kind.MONEY -> (raw.toString().toDoubleOrNull() ?: continue).coerceIn(s.min, s.max)
                        else -> (raw.toString().toDoubleOrNull()?.toInt() ?: continue).let { if (s.kind == Kind.TIME && it == -1) -1 else it.coerceIn(s.min.toInt(), s.max.toInt()) }
                    }
                    out += Change.SetValue(s, v, "${s.label}: ${if (s.key == "name") s.get(c) else fmt(s, s.get(c))} → ${if (s.key == "name") v else fmt(s, v)}")
                }
                "habit" -> {
                    val h = builtIn[a.optString("key")] ?: continue
                    val on = a.optBoolean("on", true)
                    out += Change.Habit(h.key, on, "Gewohnheit \"${h.title}\" ${if (on) "einschalten" else "ausschalten"}")
                }
                "new_habit" -> {
                    val t = a.optString("title").take(40)
                    if (t.isBlank()) continue
                    val pw = a.optInt("perWeek", 7).coerceIn(1, 7)
                    out += Change.NewHabit(t, a.optString("min").take(80).ifBlank { t }, a.optString("full").take(80).ifBlank { t }, pw, "Neue Gewohnheit \"$t\" (${if (pw == 7) "täglich" else "${pw}x pro Woche"})")
                }
                "hide" -> {
                    val k = a.optString("section"); val n = sections[k] ?: continue
                    val h = a.optBoolean("hide", true)
                    out += Change.Hide(k, h, "Heute: \"$n\" ${if (h) "ausblenden" else "einblenden"}")
                }
                "accent" -> {
                    val name = a.optString("color").lowercase(); val col = accents[name] ?: continue
                    out += Change.Accent(col, "Akzentfarbe: $name")
                }
                "text" -> {
                    val s = a.optDouble("scale", 1.0).toFloat().coerceIn(0.85f, 1.3f)
                    out += Change.TextSize(s, "Textgrösse: ${(s * 100).toInt()} %")
                }
                "mode" -> {
                    val m = a.optString("mode").takeIf { it in setOf("system", "dark", "light") } ?: continue
                    out += Change.Mode(m, "Darstellung: " + mapOf("system" to "wie Handy", "dark" to "dunkel", "light" to "hell")[m])
                }
                "wish" -> a.optString("text").take(300).takeIf { it.isNotBlank() }?.let { out += Change.Wish(it) }
            }
        }
        return out
    }

    // ---------------- Anwenden und Rückgängig ----------------

    /** Wendet die Änderungen an und merkt sich den alten Stand für "Rückgängig". */
    suspend fun apply(c: Context, changes: List<Change>) = withContext(Dispatchers.IO) {
        val prefs = p(c)
        val undo = JSONObject()
        val sp = prefs.sp.all
        fun keep(k: String) { if (!undo.has(k)) undo.put(k, sp[k]?.let { JSONObject().put("v", it).put("t", it.javaClass.simpleName) } ?: JSONObject.NULL) }
        val newHabitIds = JSONArray()
        changes.forEach { ch ->
            when (ch) {
                is Change.SetValue -> {
                    if (ch.setting.key.startsWith("ai")) undo.put("@" + ch.setting.key, ch.setting.get(c).toString())
                    else listOf(prefKey(ch.setting.key)).forEach { keep(it) }
                    ch.setting.set(c, ch.value)
                }
                is Change.Habit -> { keep("hoff_${ch.key}"); prefs.setHabitDisabled(ch.key, !ch.on) }
                is Change.NewHabit -> {
                    val id = AppDatabase.get(c).dao().insertCustom(CustomHabit(title = ch.title, minText = ch.min, fullText = ch.full, perWeek = ch.perWeek))
                    newHabitIds.put(id)
                }
                is Change.Hide -> {
                    keep("ui_hidden")
                    val h = hidden(c).toMutableSet().apply { if (ch.hide) add(ch.section) else remove(ch.section) }
                    prefs.sp.edit().putString("ui_hidden", h.joinToString(",")).apply()
                }
                is Change.Accent -> { keep("ui_accent"); prefs.sp.edit().putLong("ui_accent", ch.color).apply() }
                is Change.TextSize -> { keep("ui_text"); prefs.sp.edit().putFloat("ui_text", ch.scale).apply() }
                is Change.Mode -> { keep("ui_mode"); prefs.sp.edit().putString("ui_mode", ch.mode).apply() }
                is Change.Wish -> addWish(c, ch.wish)
            }
        }
        undo.put("@habits", newHabitIds)
        prefs.sp.edit().putString("ui_undo", undo.toString()).apply()
        prefs.bump()
        runCatching { Scheduler.rescheduleAll(c) }
    }

    /** Prefs-Schlüssel hinter einer Einstellung (für Rückgängig). */
    private fun prefKey(k: String) = when (k) {
        "eveningAskAfter" -> "eveningAsk"
        "reminderRepeatMinutes" -> "remRepeat"
        "reminderRepeatMax" -> "remRepeatMax"
        "briefingsEnabled" -> "briefings"
        "morningBriefingOffset" -> "mbOffset"
        "sleepNeedMinutes" -> "sleepNeed"
        "entertainmentBudget" -> "entBudget"
        "monthlyIncome" -> "income"
        else -> k
    }

    fun canUndo(c: Context) = p(c).sp.contains("ui_undo")

    suspend fun undo(c: Context) = withContext(Dispatchers.IO) {
        val prefs = p(c)
        val o = JSONObject(prefs.sp.getString("ui_undo", null) ?: return@withContext)
        val ed = prefs.sp.edit()
        o.keys().forEach { k ->
            when {
                k == "@habits" -> {
                    val ids = o.getJSONArray(k)
                    val dao = AppDatabase.get(c).dao()
                    val all = dao.customHabitsOnce()
                    for (i in 0 until ids.length()) all.firstOrNull { it.id == ids.getLong(i) }?.let { dao.deleteCustom(it) }
                }
                k == "@aiBudget" -> AiStore.get(c).monthlyBudget = o.getString(k).toFloat()
                k == "@aiDaily" -> AiStore.get(c).dailyLimit = o.getString(k).toInt()
                o.isNull(k) -> ed.remove(k)
                else -> {
                    val e = o.getJSONObject(k)
                    when (e.getString("t")) {
                        "Boolean" -> ed.putBoolean(k, e.getBoolean("v"))
                        "Integer" -> ed.putInt(k, e.getInt("v"))
                        "Long" -> ed.putLong(k, e.getLong("v"))
                        "Float" -> ed.putFloat(k, e.getDouble("v").toFloat())
                        else -> ed.putString(k, e.getString("v"))
                    }
                }
            }
        }
        ed.remove("ui_undo").apply()
        prefs.bump()
        runCatching { Scheduler.rescheduleAll(c) }
    }

    // ---------------- Wunschliste ----------------

    fun wishes(c: Context): List<String> = runCatching {
        val a = JSONArray(p(c).sp.getString("ui_wishes", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addWish(c: Context, w: String) {
        val a = JSONArray(wishes(c) + w)
        p(c).sp.edit().putString("ui_wishes", a.toString()).apply()
        p(c).bump()
    }

    fun removeWish(c: Context, w: String) {
        p(c).sp.edit().putString("ui_wishes", JSONArray(wishes(c) - w).toString()).apply()
        p(c).bump()
    }
}
