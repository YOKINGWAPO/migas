package com.jokin.calorias.data

import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.domain.VoiceParser
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun FoodEntity.per100g(): Nutrients = Nutrients(kcal, protein, carbs, fat, decodeExtras(extrasJson))

fun FoodEntity.forGrams(grams: Double): Nutrients = per100g().scaledTo(grams)

fun FoodEntity.displayName(): String = if (brand.isNullOrBlank()) name else "$name · $brand"

fun encodeExtras(extras: Map<ExtraNutrient, Double>): String =
    json.encodeToString(extras.mapKeys { it.key.name })

/** Unknown keys (e.g. from a newer backup) are dropped instead of failing the whole food. */
fun decodeExtras(raw: String): Map<ExtraNutrient, Double> {
    val map: Map<String, Double> = json.decodeFromString(raw)
    return map.mapNotNull { (k, v) -> ExtraNutrient.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap()
}

fun searchKey(name: String, brand: String?): String = VoiceParser.normalize(listOfNotNull(name, brand).joinToString(" "))
