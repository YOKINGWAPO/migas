package com.jokin.calorias.data.export

import androidx.room.withTransaction
import com.jokin.calorias.data.QUICK_SERVING_GRAMS
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.LogEntryEntity
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.data.searchKey
import com.jokin.calorias.domain.CsvImport
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** What a diary import will do, shown before anything is written. */
data class DiaryPreview(
    val rows: List<CsvImport.DiaryRow>,
    /** Days in the file that already have entries in the app; skipped unless the user opts in. */
    val alreadyLogged: Set<Long>,
) {
    val days: Set<Long> = rows.map { it.epochDay }.toSet()
    val withoutMeal: Int = rows.count { it.meal == null }

    fun toImport(skipLoggedDays: Boolean): List<CsvImport.DiaryRow> =
        if (skipLoggedDays) rows.filter { it.epochDay !in alreadyLogged } else rows
}

data class DiaryImportResult(val entries: Int, val days: Int, val foods: Int)

/** Writes the rows [CsvImport] already validated: the diary into Room, weigh-ins into Health Connect. */
class ImportRepository(
    private val db: AppDatabase,
    private val health: HealthRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun previewDiary(parsed: CsvImport.Parsed.Diary): DiaryPreview {
        val logged = db.logDao().loggedDays().toSet()
        return DiaryPreview(parsed.rows, parsed.rows.map { it.epochDay }.filter { it in logged }.toSet())
    }

    /**
     * Rows with grams become a food per 100 g plus an entry of those grams; rows without them are
     * stored like a quick entry (one serving holding the totals). Identical foods are created once.
     */
    suspend fun importDiary(rows: List<CsvImport.DiaryRow>, defaultMeal: MealType): DiaryImportResult {
        val now = clock()
        val foodIds = mutableMapOf<FoodKey, Long>()
        db.withTransaction {
            val entries = rows.map { row ->
                val grams = row.grams ?: QUICK_SERVING_GRAMS
                val key = FoodKey.of(row, grams)
                val foodId = foodIds.getOrPut(key) { db.foodDao().insert(foodFor(row, key, now)) }
                val meal = row.meal ?: defaultMeal
                LogEntryEntity(epochDay = row.epochDay, meal = meal, foodId = foodId, grams = grams, loggedAt = loggedAt(row, meal))
            }
            db.logDao().insertAll(entries)
        }
        return DiaryImportResult(rows.size, rows.map { it.epochDay }.distinct().size, foodIds.size)
    }

    suspend fun canWriteWeight(): Boolean = health.canWriteWeight()

    /** The last weigh-in of a day wins when a file repeats it. False when the permission is missing. */
    suspend fun importWeights(rows: List<CsvImport.WeightRow>): Boolean =
        health.writeImportedWeights(rows.associate { it.epochDay to it.kg })

    private fun foodFor(row: CsvImport.DiaryRow, key: FoodKey, now: Long) = FoodEntity(
        name = key.name.take(80),
        brand = key.brand?.take(60),
        searchName = searchKey(key.name, key.brand),
        kcal = key.per100g.kcal, protein = key.per100g.protein, carbs = key.per100g.carbs, fat = key.per100g.fat,
        servingGrams = if (row.grams == null) QUICK_SERVING_GRAMS else null,
        source = if (row.grams == null) FoodSource.QUICK else FoodSource.IMPORTED,
        createdAt = now,
        updatedAt = now,
    )

    /** The file's time when it has one, else a usual hour for the meal, so Progress's meal times stay sensible. */
    private fun loggedAt(row: CsvImport.DiaryRow, meal: MealType): Long {
        val time = row.time ?: TYPICAL_TIME.getValue(meal)
        return LocalDate.ofEpochDay(row.epochDay).atTime(time).atZone(zone()).toInstant().toEpochMilli()
    }

    /** Foods are matched on name, brand and values rounded to 0.1, so float noise does not split them. */
    private data class FoodKey(val name: String, val brand: String?, val per100g: Nutrients, val quick: Boolean) {
        companion object {
            fun of(row: CsvImport.DiaryRow, grams: Double): FoodKey {
                val per100 = row.nutrients.scaledTo(100 * 100 / grams)
                fun r(v: Double) = (v * 10).roundToInt() / 10.0
                return FoodKey(row.name, row.brand, Nutrients(r(per100.kcal), r(per100.protein), r(per100.carbs), r(per100.fat)), row.grams == null)
            }
        }
    }

    private companion object {
        val TYPICAL_TIME = mapOf(
            MealType.BREAKFAST to LocalTime.of(8, 30),
            MealType.LUNCH to LocalTime.of(14, 0),
            MealType.DINNER to LocalTime.of(21, 0),
            MealType.SNACK to LocalTime.of(17, 30),
        )
    }
}
