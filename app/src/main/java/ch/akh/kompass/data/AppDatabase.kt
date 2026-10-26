package ch.akh.kompass.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SleepEntry::class, HabitLog::class, CustomHabit::class, SnusEntry::class,
        SubstanceEntry::class, Workout::class, SetEntry::class, CardioEntry::class,
        Book::class, ReadingSession::class, FocusSession::class, Tx::class, Budget::class,
        MoodEntry::class, WeeklyReview::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): KDao

    companion object {
        const val NAME = "kompass.db"
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .build().also { instance = it }
            }

        /** Nach einem Backup-Import neu öffnen. */
        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
