package ch.akh.kompass.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Alle Einstellungen. Wird in SharedPreferences gespeichert und als StateFlow
 * beobachtbar gemacht, damit die Oberfläche sich bei Änderungen neu zeichnet.
 */
class Prefs private constructor(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Zählt bei jeder Änderung hoch. */
    val version: StateFlow<Int> = _version

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _version.value = _version.value + 1
    }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = edit { putBoolean("onboarded", v) }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = edit { putString("name", v) }

    /** Phase, in der Akh gerade ist (0 bis 5). */
    var phase: Int
        get() = sp.getInt("phase", 0)
        set(v) = edit { putInt("phase", v) }

    var phaseStart: LocalDate
        get() = LocalDate.parse(sp.getString("phaseStart", null) ?: LocalDate.now().toString())
        set(v) = edit { putString("phaseStart", v.toString()) }

    var planStart: LocalDate
        get() = LocalDate.parse(sp.getString("planStart", null) ?: LocalDate.now().toString())
        set(v) = edit { putString("planStart", v.toString()) }

    // ---------- Schlaf (Minuten nach Mitternacht) ----------
    /** Aufstehzeit am Anfang (gemessen oder geschätzt). */
    var startWake: Int
        get() = sp.getInt("startWake", 10 * 60)
        set(v) = edit { putInt("startWake", v) }

    /** Endziel, standardmässig 7:00. */
    var goalWake: Int
        get() = sp.getInt("goalWake", 7 * 60)
        set(v) = edit { putInt("goalWake", v) }

    /** Aktuelle Ziel-Aufstehzeit, wandert schrittweise Richtung goalWake. */
    var currentWake: Int
        get() = sp.getInt("currentWake", startWake)
        set(v) = edit { putInt("currentWake", v) }

    var lastShiftCheck: LocalDate?
        get() = sp.getString("lastShiftCheck", null)?.let { LocalDate.parse(it) }
        set(v) = edit { putString("lastShiftCheck", v?.toString()) }

    var shiftStepMinutes: Int
        get() = sp.getInt("shiftStep", 15)
        set(v) = edit { putInt("shiftStep", v) }

    var shiftEveryDays: Int
        get() = sp.getInt("shiftDays", 4)
        set(v) = edit { putInt("shiftDays", v) }

    var sleepNeedMinutes: Int
        get() = sp.getInt("sleepNeed", 8 * 60)
        set(v) = edit { putInt("sleepNeed", v) }

    var alarmEnabled: Boolean
        get() = sp.getBoolean("alarmEnabled", false)
        set(v) = edit { putBoolean("alarmEnabled", v) }

    var remindersEnabled: Boolean
        get() = sp.getBoolean("remindersEnabled", true)
        set(v) = edit { putBoolean("remindersEnabled", v) }

    /** Text der letzten Schlaf-Coach-Entscheidung. */
    var shiftNote: String
        get() = sp.getString("shiftNote", "") ?: ""
        set(v) = edit { putString("shiftNote", v) }

    var snoozeUsed: String
        get() = sp.getString("snoozeUsed", "") ?: ""
        set(v) = edit { putString("snoozeUsed", v) }

    // ---------- Snus ----------
    var snusBaseline: Int
        get() = sp.getInt("snusBaseline", 20)
        set(v) = edit { putInt("snusBaseline", v) }

    var pouchesPerCan: Int
        get() = sp.getInt("pouchesPerCan", 20)
        set(v) = edit { putInt("pouchesPerCan", v) }

    var canPrice: Float
        get() = sp.getFloat("canPrice", 8f)
        set(v) = edit { putFloat("canPrice", v) }

    var taperStart: LocalDate
        get() = LocalDate.parse(sp.getString("taperStart", null) ?: "2027-01-04")
        set(v) = edit { putString("taperStart", v.toString()) }

    var quitDate: LocalDate
        get() = LocalDate.parse(sp.getString("quitDate", null) ?: "2027-05-03")
        set(v) = edit { putString("quitDate", v.toString()) }

    // ---------- Bildschirm ----------
    /** Tagesbudget für Games und Unterhaltung in Minuten. */
    var entertainmentBudget: Int
        get() = sp.getInt("entBudget", 180)
        set(v) = edit { putInt("entBudget", v) }

    /** Zusätzliche Pakete, die als Unterhaltung zählen (kommagetrennt). */
    var extraEntertainment: String
        get() = sp.getString("extraEnt", "") ?: ""
        set(v) = edit { putString("extraEnt", v) }

    var lastBudgetWarn: String
        get() = sp.getString("lastBudgetWarn", "") ?: ""
        set(v) = edit { putString("lastBudgetWarn", v) }

    var lastLateWarn: String
        get() = sp.getString("lastLateWarn", "") ?: ""
        set(v) = edit { putString("lastLateWarn", v) }

    // ---------- Gewohnheiten ----------
    fun habitDisabled(key: String): Boolean = sp.getBoolean("hoff_$key", false)
    fun setHabitDisabled(key: String, off: Boolean) = edit { putBoolean("hoff_$key", off) }

    /** Eigene Erinnerungszeit pro Gewohnheit (Minuten nach Mitternacht), -1 = Standard. */
    fun habitReminder(key: String): Int = sp.getInt("hrem_$key", -1)
    fun setHabitReminder(key: String, minute: Int) = edit { putInt("hrem_$key", minute) }

    // ---------- Finanzen ----------
    /** Gespeicherte Spaltenzuordnung für den CSV-Import. */
    var csvMapping: String
        get() = sp.getString("csvMapping", "") ?: ""
        set(v) = edit { putString("csvMapping", v) }

    var monthlyIncome: Float
        get() = sp.getFloat("income", 0f)
        set(v) = edit { putFloat("income", v) }

    var savingsGoal: Float
        get() = sp.getFloat("savingsGoal", 0f)
        set(v) = edit { putFloat("savingsGoal", v) }

    // ---------- Fokus ----------
    var focusEndsAt: Long
        get() = sp.getLong("focusEndsAt", 0L)
        set(v) = edit { putLong("focusEndsAt", v) }

    var focusMinutes: Int
        get() = sp.getInt("focusMinutes", 50)
        set(v) = edit { putInt("focusMinutes", v) }

    var focusLabel: String
        get() = sp.getString("focusLabel", "") ?: ""
        set(v) = edit { putString("focusLabel", v) }

    fun bump() { _version.value = _version.value + 1 }

    companion object {
        const val FILE = "kompass_prefs"
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
