package com.jokin.calorias.data

import android.content.Context
import androidx.room.withTransaction
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.RecipeEntity
import com.jokin.calorias.data.db.RecipeItemEntity
import com.jokin.calorias.data.db.RecipeWithItems
import com.jokin.calorias.domain.Nutrients
import kotlinx.coroutines.flow.Flow

/**
 * A recipe owns a food (source RECIPE) whose per-100 g values are recomputed from the ingredients
 * on every save, so logging a recipe is the same as logging any food: one serving = total / servings.
 */
class RecipeRepository(private val context: Context, private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val recipes = db.recipeDao()

    fun observeAll(): Flow<List<RecipeWithItems>> = recipes.observeAll()

    suspend fun byId(id: Long): RecipeWithItems? = recipes.byId(id)

    suspend fun save(id: Long, name: String, servings: Int, items: List<Pair<FoodEntity, Double>>): Long {
        require(name.isNotBlank()) { context.getString(R.string.add_error_recipe_needs_name) }
        require(servings in 1..100) { context.getString(R.string.add_error_recipe_servings_range) }
        require(items.isNotEmpty() && items.all { it.second > 0 }) { context.getString(R.string.add_error_ingredient_needs_amount) }

        val totalGrams = items.sumOf { it.second }
        val per100 = Nutrients.per100gOfMix(items.map { (food, grams) -> food.per100g() to grams })
        val cleanName = name.trim().take(80)
        val now = clock()

        return db.withTransaction {
            val existing = if (id != 0L) recipes.byId(id) else null
            val food = FoodEntity(
                id = existing?.food?.id ?: 0,
                name = cleanName,
                searchName = searchKey(cleanName, null),
                kcal = per100.kcal, protein = per100.protein, carbs = per100.carbs, fat = per100.fat,
                extrasJson = encodeExtras(per100.extras),
                servingGrams = totalGrams / servings,
                servingName = context.getString(R.string.add_one_serving),
                source = FoodSource.RECIPE,
                createdAt = existing?.food?.createdAt ?: now,
                updatedAt = now,
            )
            val foodId = if (existing != null) food.id.also { db.foodDao().update(food) } else db.foodDao().insert(food)
            val recipeId = if (existing != null) {
                recipes.update(existing.recipe.copy(name = cleanName, servings = servings))
                recipes.deleteItems(existing.recipe.id)
                existing.recipe.id
            } else {
                recipes.insert(RecipeEntity(name = cleanName, servings = servings, foodId = foodId))
            }
            recipes.insertItems(items.map { (f, g) -> RecipeItemEntity(recipeId = recipeId, foodId = f.id, grams = g) })
            recipeId
        }
    }

    /** The backing food stays if past entries use it, so history keeps its calories. */
    suspend fun delete(recipe: RecipeWithItems) {
        db.withTransaction {
            recipes.delete(recipe.recipe.id)
            if (db.foodDao().usageCount(recipe.food.id) == 0) db.foodDao().delete(recipe.food)
        }
    }
}
