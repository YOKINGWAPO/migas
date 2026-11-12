package com.jokin.calorias.data

import android.content.Context
import androidx.room.withTransaction
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.EntryWithFood
import com.jokin.calorias.data.db.LogEntryEntity
import com.jokin.calorias.data.db.NoteEntity
import com.jokin.calorias.data.db.WaterEntity
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.domain.MealType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

sealed interface HealthWrite {
    data object Written : HealthWrite
    data object NotAllowed : HealthWrite
    data class Failed(val reason: String) : HealthWrite
}

/** Everything that is logged per day: food entries, water and the day's note. */
class LogRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val health: HealthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = db.logDao()

    fun observeDay(day: Long): Flow<List<EntryWithFood>> = log.observeDay(day)

    fun observeRange(from: Long, to: Long): Flow<List<EntryWithFood>> = log.observeRange(from, to)

    suspend fun range(from: Long, to: Long): List<EntryWithFood> = log.range(from, to)

    fun observeLoggedDays(): Flow<List<Long>> = log.observeLoggedDays()

    suspend fun loggedDays(): List<Long> = log.loggedDays()

    suspend fun entry(id: Long): LogEntryEntity? = log.byId(id)

    suspend fun hasEntries(day: Long): Boolean = log.countForDay(day) > 0

    suspend fun logFood(day: Long, meal: MealType, foodId: Long, grams: Double, comboId: Long? = null): Long {
        require(grams > 0 && grams < 10_000) { context.getString(R.string.today_error_amount) }
        return log.insert(LogEntryEntity(epochDay = day, meal = meal, foodId = foodId, grams = grams, comboId = comboId, loggedAt = clock()))
    }

    suspend fun logCombo(day: Long, meal: MealType, comboId: Long) {
        val combo = db.comboDao().byId(comboId) ?: error(context.getString(R.string.today_error_combo_gone))
        val now = clock()
        log.insertAll(combo.items.map { LogEntryEntity(epochDay = day, meal = meal, foodId = it.item.foodId, grams = it.item.grams, comboId = comboId, loggedAt = now) })
    }

    suspend fun updateGrams(entry: LogEntryEntity, grams: Double, meal: MealType) {
        require(grams > 0 && grams < 10_000) { context.getString(R.string.today_error_amount) }
        log.update(entry.copy(grams = grams, meal = meal))
    }

    suspend fun delete(entry: LogEntryEntity) = log.delete(entry)

    /** Puts back an entry just deleted, with its id, for "Deshacer". */
    suspend fun restore(entry: LogEntryEntity) {
        log.insert(entry)
    }

    /** Repeats a meal from another day; returns how many entries were copied. */
    suspend fun copyMeal(fromDay: Long, fromMeal: MealType, toDay: Long, toMeal: MealType): Int {
        val source = log.entriesOfMeal(fromDay, fromMeal)
        val now = clock()
        db.withTransaction {
            log.insertAll(source.map { it.copy(id = 0, epochDay = toDay, meal = toMeal, loggedAt = now) })
        }
        return source.size
    }

    fun observeWater(day: Long): Flow<Int> = db.waterDao().observeDayTotal(day)

    /** Saves locally first; the Health Connect copy is a bonus whose outcome is reported, never hidden. */
    suspend fun addWater(day: Long, ml: Int): HealthWrite {
        require(ml in 1..5000) { context.getString(R.string.today_error_water) }
        val now = clock()
        db.waterDao().insert(WaterEntity(epochDay = day, ml = ml, loggedAt = now))
        return try {
            if (health.writeHydration(ml, Instant.ofEpochMilli(now))) HealthWrite.Written else HealthWrite.NotAllowed
        } catch (e: Exception) {
            HealthWrite.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun undoLastWater(day: Long) = db.waterDao().deleteLast(day)

    fun observeNote(day: Long): Flow<NoteEntity?> = db.noteDao().observe(day)

    suspend fun setNote(day: Long, text: String) {
        val clean = text.trim().take(500)
        if (clean.isEmpty()) db.noteDao().delete(day) else db.noteDao().upsert(NoteEntity(day, clean))
    }
}
