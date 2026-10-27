package ch.akh.kompass.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KDao {
    // ---------- Schlaf ----------
    @Insert suspend fun insertSleep(e: SleepEntry): Long
    @Upsert suspend fun upsertSleep(e: SleepEntry)
    @Delete suspend fun deleteSleep(e: SleepEntry)
    @Query("SELECT * FROM sleep WHERE wakeAt IS NULL AND bedAt IS NOT NULL AND bedAt > CAST(strftime('%s', 'now') AS INTEGER) * 1000 - 57600000 ORDER BY bedAt DESC LIMIT 1")
    suspend fun openSleep(): SleepEntry?
    @Query("SELECT * FROM sleep WHERE wakeAt IS NULL AND bedAt IS NOT NULL AND bedAt > CAST(strftime('%s', 'now') AS INTEGER) * 1000 - 57600000 ORDER BY bedAt DESC LIMIT 1")
    fun openSleepFlow(): Flow<SleepEntry?>
    @Query("SELECT * FROM sleep ORDER BY COALESCE(wakeAt, bedAt) DESC LIMIT :n")
    fun recentSleep(n: Int): Flow<List<SleepEntry>>
    @Query("SELECT * FROM sleep WHERE wakeDate >= :from AND wakeAt IS NOT NULL ORDER BY wakeAt")
    suspend fun sleepSince(from: String): List<SleepEntry>
    @Query("SELECT * FROM sleep WHERE wakeDate = :day AND wakeAt IS NOT NULL LIMIT 1")
    suspend fun sleepOn(day: String): SleepEntry?

    // ---------- Gewohnheiten ----------
    @Upsert suspend fun upsertHabit(h: HabitLog)
    @Query("SELECT * FROM habit_log WHERE date = :date AND `key` = :key")
    suspend fun habitOnce(date: String, key: String): HabitLog?
    @Query("DELETE FROM habit_log WHERE date = :date AND `key` = :key")
    suspend fun deleteHabit(date: String, key: String)
    @Query("SELECT * FROM habit_log WHERE date = :date")
    fun habitsOn(date: String): Flow<List<HabitLog>>
    @Query("SELECT * FROM habit_log WHERE date >= :from")
    fun habitsSinceFlow(from: String): Flow<List<HabitLog>>
    @Query("SELECT * FROM habit_log WHERE date >= :from")
    suspend fun habitsSince(from: String): List<HabitLog>

    @Insert suspend fun insertCustom(h: CustomHabit): Long
    @Upsert suspend fun upsertCustom(h: CustomHabit)
    @Delete suspend fun deleteCustom(h: CustomHabit)
    @Query("SELECT * FROM custom_habit ORDER BY id")
    fun customHabits(): Flow<List<CustomHabit>>
    @Query("SELECT * FROM custom_habit ORDER BY id")
    suspend fun customHabitsOnce(): List<CustomHabit>

    // ---------- Snus ----------
    @Insert suspend fun insertSnus(e: SnusEntry): Long
    @Query("DELETE FROM snus WHERE id = :id") suspend fun deleteSnusId(id: Long)
    @Query("DELETE FROM snus WHERE id = (SELECT id FROM snus WHERE type = 0 ORDER BY at DESC LIMIT 1)")
    suspend fun undoLastPouch()
    @Query("SELECT COUNT(*) FROM snus WHERE type = :type AND at >= :from AND at < :to")
    fun snusCountFlow(type: Int, from: Long, to: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM snus WHERE type = :type AND at >= :from AND at < :to")
    suspend fun snusCount(type: Int, from: Long, to: Long): Int
    @Query("SELECT at FROM snus WHERE type = 0 AND at >= :from ORDER BY at")
    fun snusTimes(from: Long): Flow<List<Long>>
    @Query("SELECT at FROM snus WHERE type = 0 AND at >= :from ORDER BY at")
    suspend fun snusTimesOnce(from: Long): List<Long>
    @Query("SELECT MAX(at) FROM snus WHERE type = 0")
    fun lastPouchFlow(): Flow<Long?>
    @Query("SELECT MAX(at) FROM snus WHERE type = 0")
    suspend fun lastPouch(): Long?

    // ---------- Konsum ----------
    @Insert suspend fun insertSubstance(e: SubstanceEntry): Long
    @Query("DELETE FROM substance WHERE id = :id") suspend fun deleteSubstanceId(id: Long)
    @Query("SELECT * FROM substance WHERE at >= :from AND at < :to ORDER BY at")
    suspend fun substancesBetween(from: Long, to: Long): List<SubstanceEntry>
    @Query("SELECT * FROM substance WHERE at >= :from AND at < :to ORDER BY at")
    fun substancesBetweenFlow(from: Long, to: Long): Flow<List<SubstanceEntry>>
    @Query("SELECT * FROM substance WHERE at >= :from ORDER BY at DESC")
    fun substancesSince(from: Long): Flow<List<SubstanceEntry>>
    @Delete suspend fun deleteSubstance(e: SubstanceEntry)

    // ---------- Gym ----------
    @Insert suspend fun insertWorkout(w: Workout): Long
    @Upsert suspend fun upsertWorkout(w: Workout)
    @Delete suspend fun deleteWorkout(w: Workout)
    @Query("SELECT * FROM set_entry WHERE workoutId = :id")
    suspend fun setsOfOnce(id: Long): List<SetEntry>
    @Insert suspend fun insertSets(s: List<SetEntry>)
    @Query("DELETE FROM set_entry WHERE workoutId = :id")
    suspend fun deleteSetsOf(id: Long)
    @Query("SELECT * FROM workout ORDER BY startedAt DESC")
    fun workouts(): Flow<List<Workout>>
    @Query("SELECT * FROM workout WHERE id = :id")
    fun workout(id: Long): Flow<Workout?>
    @Query("SELECT * FROM workout WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun openWorkout(): Flow<Workout?>
    @Query("SELECT COUNT(*) FROM workout WHERE startedAt >= :from AND startedAt < :to")
    suspend fun workoutCount(from: Long, to: Long): Int
    @Insert suspend fun insertSet(s: SetEntry): Long
    @Delete suspend fun deleteSet(s: SetEntry)
    @Query("SELECT * FROM set_entry WHERE workoutId = :id ORDER BY exercise, idx")
    fun setsOf(id: Long): Flow<List<SetEntry>>
    @Query("SELECT * FROM set_entry WHERE exercise = :exercise ORDER BY at")
    fun setsFor(exercise: String): Flow<List<SetEntry>>
    @Query("SELECT * FROM set_entry WHERE exercise = :exercise AND workoutId = (SELECT workoutId FROM set_entry WHERE exercise = :exercise AND workoutId != :excludeWorkout ORDER BY at DESC LIMIT 1) ORDER BY idx")
    suspend fun lastSetsFor(exercise: String, excludeWorkout: Long): List<SetEntry>
    @Query("SELECT DISTINCT exercise FROM set_entry ORDER BY exercise")
    fun loggedExercises(): Flow<List<String>>

    // ---------- Ausdauer ----------
    @Insert suspend fun insertCardio(c: CardioEntry): Long
    @Query("DELETE FROM cardio WHERE id = :id") suspend fun deleteCardioId(id: Long)
    @Delete suspend fun deleteCardio(c: CardioEntry)
    @Query("SELECT * FROM cardio ORDER BY date DESC, id DESC")
    fun cardio(): Flow<List<CardioEntry>>
    @Query("SELECT * FROM cardio WHERE date >= :from")
    suspend fun cardioSince(from: String): List<CardioEntry>

    // ---------- Bücher ----------
    @Insert suspend fun insertBook(b: Book): Long
    @Upsert suspend fun upsertBook(b: Book)
    @Delete suspend fun deleteBook(b: Book)
    @Query("SELECT * FROM book ORDER BY status = 1 DESC, status ASC, id DESC")
    fun books(): Flow<List<Book>>
    @Query("SELECT * FROM book WHERE id = :id")
    suspend fun book(id: Long): Book?
    @Insert suspend fun insertReading(r: ReadingSession): Long
    @Query("DELETE FROM reading WHERE id = :id") suspend fun deleteReadingId(id: Long)
    @Query("SELECT * FROM reading WHERE date >= :from ORDER BY date")
    fun readingSince(from: String): Flow<List<ReadingSession>>

    // ---------- Fokus ----------
    @Insert suspend fun insertFocus(f: FocusSession): Long
    @Query("DELETE FROM focus WHERE id = :id") suspend fun deleteFocusId(id: Long)
    @Delete suspend fun deleteFocus(f: FocusSession)
    @Query("SELECT * FROM focus WHERE date >= :from ORDER BY startedAt DESC")
    fun focusSince(from: String): Flow<List<FocusSession>>
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus WHERE date = :day")
    fun focusMinutesOn(day: String): Flow<Int>

    // ---------- Finanzen ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTx(t: Tx): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTxs(t: List<Tx>): List<Long>
    @Upsert suspend fun upsertTx(t: Tx)
    @Query("DELETE FROM tx WHERE id IN (:ids)") suspend fun deleteTxIds(ids: List<Long>)
    @Delete suspend fun deleteTx(t: Tx)
    /** Automatisch aus Bank-Benachrichtigungen erfasste Buchungen in einem Zeitraum (inkl. Enddatum). */
    @Query("SELECT * FROM tx WHERE source = 'Push' AND date >= :from AND date <= :to")
    suspend fun pushTxBetween(from: String, to: String): List<Tx>
    @Query("SELECT * FROM tx WHERE date >= :from AND date < :to ORDER BY date DESC, id DESC")
    fun txBetween(from: String, to: String): Flow<List<Tx>>
    @Query("SELECT category, SUM(amount) AS total FROM tx WHERE date >= :from AND date < :to GROUP BY category ORDER BY total")
    fun categorySums(from: String, to: String): Flow<List<CategorySum>>
    @Upsert suspend fun upsertBudget(b: Budget)
    @Delete suspend fun deleteBudget(b: Budget)
    @Query("SELECT * FROM budget ORDER BY category")
    fun budgets(): Flow<List<Budget>>

    // ---------- Stimmung ----------
    @Upsert suspend fun upsertMood(m: MoodEntry)
    @Query("DELETE FROM mood WHERE date = :day") suspend fun deleteMood(day: String)
    @Query("SELECT * FROM mood WHERE date = :day")
    fun moodOn(day: String): Flow<MoodEntry?>
    @Query("SELECT * FROM mood WHERE date >= :from ORDER BY date")
    fun moodSince(from: String): Flow<List<MoodEntry>>
    @Query("SELECT * FROM mood WHERE date >= :from ORDER BY date")
    suspend fun moodSinceOnce(from: String): List<MoodEntry>
    @Upsert suspend fun upsertReview(r: WeeklyReview)
    @Query("SELECT * FROM review ORDER BY weekStart DESC")
    fun reviews(): Flow<List<WeeklyReview>>

    // ---------- Für automatisches Abhaken ----------
    @Query("SELECT * FROM workout WHERE startedAt >= :from AND startedAt < :to")
    suspend fun workoutsBetween(from: Long, to: Long): List<Workout>
    @Query("SELECT COUNT(*) FROM set_entry WHERE workoutId = :id")
    suspend fun setCount(id: Long): Int
    @Query("SELECT * FROM cardio WHERE date = :day")
    suspend fun cardioOn(day: String): List<CardioEntry>
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM reading WHERE date = :day")
    suspend fun readingMinutesOn(day: String): Int
    @Query("SELECT COALESCE(SUM(pages), 0) FROM reading WHERE date = :day")
    suspend fun readingPagesOn(day: String): Int
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus WHERE date = :day")
    suspend fun focusMinutesOnce(day: String): Int
    @Query("SELECT * FROM mood WHERE date = :day")
    suspend fun moodOnce(day: String): MoodEntry?
    @Query("SELECT MIN(at) FROM snus")
    suspend fun firstSnus(): Long?
}
