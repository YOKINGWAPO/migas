package com.jokin.calorias.data.seed

import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.encodeExtras
import com.jokin.calorias.data.searchKey
import com.jokin.calorias.domain.ExtraNutrient

/**
 * Parses the bundled basic-foods table (assets/alimentos.csv, built by datos/alimentos/build_table.py).
 * Columns: source;code;name_es;name_en;kcal;protein;carbs;fat;fiber;sugars;saturated_fat;salt;serving_es;serving_en;serving_g
 * (per 100 g). An empty nutrient is not published by the source; an empty serving means none.
 */
object FoodTableCsv {
    private const val COLUMNS = 15
    private val EXTRAS = listOf(
        8 to ExtraNutrient.FIBER, 9 to ExtraNutrient.SUGARS, 10 to ExtraNutrient.SATURATED_FAT, 11 to ExtraNutrient.SALT,
    )

    /** Names and servings come in the app's language at the time of seeding; search matches both languages. */
    fun parse(lines: Sequence<String>, english: Boolean, now: Long): List<FoodEntity> =
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { index, line ->
            val c = line.split(';')
            require(c.size == COLUMNS) { "alimentos.csv row ${index + 1} has ${c.size} columns" }
            val source = when (c[0]) {
                "CIQUAL" -> FoodSource.CIQUAL
                "USDA" -> FoodSource.USDA
                else -> throw IllegalArgumentException("alimentos.csv row ${index + 1}: unknown source ${c[0]}")
            }
            fun number(i: Int): Double? = c[i].trim().takeIf { it.isNotEmpty() }?.toDouble()
            val nameEs = c[2].trim()
            val nameEn = c[3].trim()
            require(nameEs.isNotEmpty() && nameEn.isNotEmpty()) { "alimentos.csv row ${index + 1} has an empty name" }
            val servingGrams = number(14)
            FoodEntity(
                name = if (english) nameEn else nameEs,
                searchName = searchKey(nameEs, nameEn),
                kcal = requireNotNull(number(4)) { "alimentos.csv row ${index + 1} has no kcal" },
                // A macro the source left unpublished counts as 0, as on a food label without that line.
                protein = number(5) ?: 0.0, carbs = number(6) ?: 0.0, fat = number(7) ?: 0.0,
                extrasJson = encodeExtras(EXTRAS.mapNotNull { (i, nutrient) -> number(i)?.let { nutrient to it } }.toMap()),
                servingName = servingGrams?.let { (if (english) c[13] else c[12]).trim() },
                servingGrams = servingGrams,
                source = source,
                createdAt = now,
                updatedAt = now,
            )
        }.toList()
}
