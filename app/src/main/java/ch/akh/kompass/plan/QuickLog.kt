package ch.akh.kompass.plan

import ch.akh.kompass.data.SnusEntry
import ch.akh.kompass.data.SubstanceEntry
import ch.akh.kompass.data.HabitLog

/**
 * Schnelle Einträge, die oft passieren (Snus, Kaffee, Craving).
 * Jede Funktion trägt ein und gibt zurück, wie man es wieder austrägt.
 */
object QuickLog {
    suspend fun pouch(e: Engine, at: Long = System.currentTimeMillis()): suspend () -> Unit {
        val id = e.dao.insertSnus(SnusEntry(at = at, type = 0))
        e.syncAuto()
        return { e.dao.deleteSnusId(id); e.syncAuto() }
    }

    suspend fun craving(e: Engine): suspend () -> Unit {
        val id = e.dao.insertSnus(SnusEntry(at = System.currentTimeMillis(), type = 1))
        return { e.dao.deleteSnusId(id) }
    }

    suspend fun substance(e: Engine, type: String): suspend () -> Unit {
        val id = e.dao.insertSubstance(SubstanceEntry(at = System.currentTimeMillis(), type = type))
        return { e.dao.deleteSubstanceId(id) }
    }

    /** Setzt eine Gewohnheit auf ein Level (0 = offen) und merkt sich den alten Stand. */
    suspend fun habit(e: Engine, day: String, key: String, level: Int): suspend () -> Unit {
        val before = e.dao.habitOnce(day, key)
        if (level == 0) e.dao.deleteHabit(day, key) else e.dao.upsertHabit(HabitLog(day, key, level))
        return { if (before == null) e.dao.deleteHabit(day, key) else e.dao.upsertHabit(before) }
    }

    /** Lesesitzung eintragen und Seite im Buch nachführen. */
    suspend fun reading(e: Engine, b: ch.akh.kompass.data.Book, newPage: Int, minutes: Int): suspend () -> Unit {
        val day = ch.akh.kompass.util.Time.todayStr()
        val habitBefore = e.dao.habitOnce(day, "lesen")
        val read = (newPage - b.current).coerceAtLeast(0)
        val finished = b.pages in 1..newPage
        val id = e.dao.insertReading(ch.akh.kompass.data.ReadingSession(bookId = b.id, date = day, minutes = minutes, pages = read))
        e.dao.upsertBook(
            b.copy(current = maxOf(newPage, b.current), status = if (finished) 2 else 1,
                finishedAt = if (finished) day else b.finishedAt, startedAt = b.startedAt ?: day),
        )
        e.syncAuto()
        return {
            e.dao.deleteReadingId(id)
            e.dao.upsertBook(b)
            if (habitBefore == null) e.dao.deleteHabit(day, "lesen") else e.dao.upsertHabit(habitBefore)
        }
    }
}
