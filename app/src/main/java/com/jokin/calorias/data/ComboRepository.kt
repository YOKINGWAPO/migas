package com.jokin.calorias.data

import android.content.Context
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.ComboEntity
import com.jokin.calorias.data.db.ComboItemEntity
import com.jokin.calorias.data.db.ComboWithItems
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.ComboDetector
import com.jokin.calorias.domain.ComboSuggestion
import com.jokin.calorias.domain.MealOccurrence
import com.jokin.calorias.domain.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class ComboProposal(val suggestion: ComboSuggestion, val foods: List<FoodEntity>, val typicalGrams: Map<Long, Double>)

class ComboRepository(private val context: Context, private val db: AppDatabase, private val settings: SettingsRepository) {
    private val combos = db.comboDao()

    fun observeAll(): Flow<List<ComboWithItems>> = combos.observeAll()

    suspend fun byId(id: Long): ComboWithItems? = combos.byId(id)

    suspend fun save(id: Long, name: String, icon: String, items: List<Pair<Long, Double>>): Long {
        require(name.isNotBlank()) { context.getString(R.string.add_error_combo_needs_name) }
        require(items.isNotEmpty()) { context.getString(R.string.add_error_combo_needs_food) }
        require(items.all { it.second > 0 }) { context.getString(R.string.add_error_food_needs_amount) }
        return combos.save(
            ComboEntity(id = id, name = name.trim().take(60), icon = icon.ifBlank { "🍽️" }.take(8)),
            items.map { (foodId, grams) -> ComboItemEntity(comboId = id, foodId = foodId, grams = grams) },
        )
    }

    suspend fun delete(id: Long) = combos.delete(id)

    suspend fun createFromMeal(day: Long, meal: MealType, name: String): Long {
        val entries = db.logDao().entriesOfMeal(day, meal)
        return save(0, name, "🍽️", entries.map { it.foodId to it.grams })
    }

    /** Live proposals from the last 30 days of entries, minus existing combos and dismissed ones. */
    fun observeProposals(today: Long): Flow<List<ComboProposal>> =
        combine(
            db.logDao().observeMealFoods(today - ComboDetector.WINDOW_DAYS + 1),
            combos.observeAll(),
            settings.settings,
        ) { mealFoods, existing, s ->
            val meals = mealFoods.groupBy { it.epochDay to it.meal }
                .map { (key, rows) -> MealOccurrence(key.first, key.second, rows.map { it.foodId }.toSet()) }
            val existingSets = existing.map { c -> c.items.map { it.item.foodId }.toSet() }.toSet()
            ComboDetector.suggest(meals, today, existingSets, s.dismissedCombos).take(1)
        }.map { suggestions -> suggestions.map { toProposal(it, today) } }

    suspend fun dismiss(signature: String) {
        settings.update { it.copy(dismissedCombos = it.dismissedCombos + signature) }
    }

    /** Uses the most recent quantity of each food as the combo's default amount. */
    private suspend fun toProposal(s: ComboSuggestion, today: Long): ComboProposal {
        val foods = db.foodDao().byIds(s.foodIds)
        val recent = db.logDao().range(today - ComboDetector.WINDOW_DAYS + 1, today)
        val grams = s.foodIds.associateWith { id -> recent.lastOrNull { it.entry.foodId == id }?.entry?.grams ?: 100.0 }
        return ComboProposal(s, foods, grams)
    }
}
