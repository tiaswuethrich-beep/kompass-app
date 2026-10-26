package ch.akh.kompass.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Eine Nacht. Datum = Tag des Aufwachens (yyyy-MM-dd). */
@Entity(tableName = "sleep", indices = [Index(value = ["wakeDate"])])
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedAt: Long? = null,
    val wakeAt: Long? = null,
    val wakeDate: String = "",
    val quality: Int = 0,
    val note: String = "",
)

/** Erledigte Gewohnheit. level 1 = Mindestversion, 2 = Vollversion. */
@Entity(tableName = "habit_log", primaryKeys = ["date", "key"])
data class HabitLog(
    val date: String,
    val key: String,
    val level: Int,
)

@Entity(tableName = "custom_habit")
data class CustomHabit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val minText: String,
    val fullText: String,
    val perWeek: Int = 7,
    val reminderMinute: Int? = null,
    val active: Boolean = true,
)

/** type 0 = Beutel, 1 = Craving überstanden */
@Entity(tableName = "snus", indices = [Index(value = ["at"])])
data class SnusEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: Int = 0,
)

/** Koffein, Alkohol, Weed: nur mitzählen. */
@Entity(tableName = "substance", indices = [Index(value = ["at"])])
data class SubstanceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    val amount: Int = 1,
)

@Entity(tableName = "workout")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val template: String,
    val note: String = "",
)

@Entity(tableName = "set_entry", indices = [Index(value = ["workoutId"]), Index(value = ["exercise"])])
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val idx: Int,
    val reps: Int,
    val weight: Double,
    val at: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cardio", indices = [Index(value = ["date"])])
data class CardioEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val minutes: Int,
    val km: Double = 0.0,
    val note: String = "",
)

/** status 0 = will ich lesen, 1 = lese ich, 2 = fertig */
@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val pages: Int = 0,
    val current: Int = 0,
    val status: Int = 0,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val rating: Int = 0,
)

@Entity(tableName = "reading", indices = [Index(value = ["date"])])
data class ReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val date: String,
    val minutes: Int,
    val pages: Int,
)

@Entity(tableName = "focus", indices = [Index(value = ["date"])])
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startedAt: Long,
    val minutes: Int,
    val label: String = "",
)

@Entity(tableName = "tx", indices = [Index(value = ["date"]), Index(value = ["hash"], unique = true)])
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amount: Double,
    val category: String,
    val text: String,
    val source: String = "manuell",
    val hash: String,
)

@Entity(tableName = "budget")
data class Budget(
    @PrimaryKey val category: String,
    val monthly: Double,
)

@Entity(tableName = "mood")
data class MoodEntry(
    @PrimaryKey val date: String,
    val mood: Int,
    val energy: Int,
    val joy: Int,
    val social: Boolean = false,
    val note: String = "",
)

@Entity(tableName = "review")
data class WeeklyReview(
    @PrimaryKey val weekStart: String,
    val win: String,
    val lesson: String,
    val nextFocus: String,
    val joyful: String = "",
)

data class DayCount(val day: String, val n: Int)
data class DaySum(val day: String, val total: Double)
data class CategorySum(val category: String, val total: Double)
data class ExerciseBest(val exercise: String, val weight: Double, val reps: Int, val at: Long)
