package com.jokin.calorias.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.jokin.calorias.domain.MealType
import kotlinx.coroutines.flow.Flow

data class EntryWithFood(
    @Embedded val entry: LogEntryEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: FoodEntity,
)

data class RecentFood(
    @Embedded val food: FoodEntity,
    val lastGrams: Double,
    val lastAt: Long,
)

data class MealFood(val epochDay: Long, val meal: MealType, val foodId: Long)

data class ComboItemWithFood(
    @Embedded val item: ComboItemEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: FoodEntity,
)

data class ComboWithItems(
    @Embedded val combo: ComboEntity,
    @Relation(entity = ComboItemEntity::class, parentColumn = "id", entityColumn = "comboId") val items: List<ComboItemWithFood>,
)

data class RecipeItemWithFood(
    @Embedded val item: RecipeItemEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: FoodEntity,
)

data class RecipeWithItems(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id") val food: FoodEntity,
    @Relation(entity = RecipeItemEntity::class, parentColumn = "id", entityColumn = "recipeId") val items: List<RecipeItemWithFood>,
)

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>)

    /** Drops the old BEDCA basics nothing points at; the ones in the diary, combos, recipes or measures stay. */
    @Query(
        """
        DELETE FROM food WHERE source = 'BEDCA'
          AND id NOT IN (SELECT foodId FROM log_entry)
          AND id NOT IN (SELECT foodId FROM combo_item)
          AND id NOT IN (SELECT foodId FROM recipe)
          AND id NOT IN (SELECT foodId FROM recipe_item)
          AND id NOT IN (SELECT foodId FROM measure WHERE foodId IS NOT NULL)
        """
    )
    suspend fun deleteUnusedBedca(): Int

    @Update
    suspend fun update(food: FoodEntity)

    @Delete
    suspend fun delete(food: FoodEntity)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE id IN (:ids)")
    suspend fun byIds(ids: Collection<Long>): List<FoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM food WHERE barcode = :barcode")
    suspend fun byBarcode(barcode: String): FoodEntity?

    /** Own products first, then recipes, bundled basics (old BEDCA ones after the current table) and cached products; recipes' backing foods included, quick entries not. */
    @Query(
        """
        SELECT * FROM food WHERE searchName LIKE '%' || :query || '%' AND source != 'QUICK'
        ORDER BY CASE source WHEN 'OWN' THEN 0 WHEN 'RECIPE' THEN 1 WHEN 'CIQUAL' THEN 2 WHEN 'USDA' THEN 2 WHEN 'BEDCA' THEN 3 WHEN 'ESTIMATED' THEN 3 ELSE 4 END,
                 CASE WHEN searchName LIKE :query || '%' THEN 0 ELSE 1 END,
                 length(searchName)
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<FoodEntity>

    @Query(
        """
        SELECT f.*, e.grams AS lastGrams, MAX(e.loggedAt) AS lastAt
        FROM log_entry e JOIN food f ON f.id = e.foodId
        WHERE e.epochDay >= :sinceDay
        GROUP BY f.id ORDER BY lastAt DESC LIMIT :limit
        """
    )
    fun observeRecent(sinceDay: Long, limit: Int): Flow<List<RecentFood>>

    @Query("SELECT * FROM food WHERE source IN ('OWN', 'ESTIMATED') ORDER BY name COLLATE NOCASE")
    fun observeOwn(): Flow<List<FoodEntity>>

    @Query("SELECT COUNT(*) FROM food WHERE source = :source")
    suspend fun countBySource(source: FoodSource): Int

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM log_entry WHERE foodId = :id) + (SELECT COUNT(*) FROM combo_item WHERE foodId = :id)
             + (SELECT COUNT(*) FROM recipe_item WHERE foodId = :id) + (SELECT COUNT(*) FROM recipe WHERE foodId = :id)
        """
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Transaction
    @Query("SELECT * FROM log_entry WHERE epochDay = :day ORDER BY loggedAt")
    fun observeDay(day: Long): Flow<List<EntryWithFood>>

    @Transaction
    @Query("SELECT * FROM log_entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, loggedAt")
    fun observeRange(from: Long, to: Long): Flow<List<EntryWithFood>>

    @Transaction
    @Query("SELECT * FROM log_entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, loggedAt")
    suspend fun range(from: Long, to: Long): List<EntryWithFood>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun byId(id: Long): LogEntryEntity?

    @Query("SELECT * FROM log_entry WHERE epochDay = :day AND meal = :meal")
    suspend fun entriesOfMeal(day: Long, meal: MealType): List<LogEntryEntity>

    @Query("SELECT DISTINCT epochDay FROM log_entry")
    fun observeLoggedDays(): Flow<List<Long>>

    @Query("SELECT DISTINCT epochDay FROM log_entry")
    suspend fun loggedDays(): List<Long>

    @Query("SELECT COUNT(*) FROM log_entry WHERE epochDay = :day")
    suspend fun countForDay(day: Long): Int

    @Query("SELECT epochDay, meal, foodId FROM log_entry WHERE epochDay >= :sinceDay")
    fun observeMealFoods(sinceDay: Long): Flow<List<MealFood>>
}

@Dao
interface ComboDao {
    @Insert
    suspend fun insert(combo: ComboEntity): Long

    @Update
    suspend fun update(combo: ComboEntity)

    @Insert
    suspend fun insertItems(items: List<ComboItemEntity>)

    @Query("DELETE FROM combo_item WHERE comboId = :comboId")
    suspend fun deleteItems(comboId: Long)

    @Query("DELETE FROM combo WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    @Query("SELECT * FROM combo ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ComboWithItems>>

    @Transaction
    @Query("SELECT * FROM combo WHERE id = :id")
    suspend fun byId(id: Long): ComboWithItems?

    @Transaction
    suspend fun save(combo: ComboEntity, items: List<ComboItemEntity>): Long {
        val id = if (combo.id == 0L) insert(combo) else combo.id.also { update(combo); deleteItems(it) }
        insertItems(items.map { it.copy(id = 0, comboId = id) })
        return id
    }
}

@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Insert
    suspend fun insertItems(items: List<RecipeItemEntity>)

    @Query("DELETE FROM recipe_item WHERE recipeId = :recipeId")
    suspend fun deleteItems(recipeId: Long)

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeWithItems>>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun byId(id: Long): RecipeWithItems?
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal WHERE id = 1")
    fun observe(): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE id = 1")
    suspend fun get(): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(water: WaterEntity): Long

    @Query("DELETE FROM water WHERE id = (SELECT id FROM water WHERE epochDay = :day ORDER BY loggedAt DESC LIMIT 1)")
    suspend fun deleteLast(day: Long)

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water WHERE epochDay = :day")
    fun observeDayTotal(day: Long): Flow<Int>

    @Query("SELECT * FROM water WHERE epochDay BETWEEN :from AND :to ORDER BY loggedAt")
    fun observeRange(from: Long, to: Long): Flow<List<WaterEntity>>

    @Query("SELECT * FROM water WHERE epochDay BETWEEN :from AND :to ORDER BY loggedAt")
    suspend fun range(from: Long, to: Long): List<WaterEntity>
}

@Dao
interface MeasureDao {
    @Insert
    suspend fun insert(measure: MeasureEntity): Long

    @Insert
    suspend fun insertAll(measures: List<MeasureEntity>)

    @Query("DELETE FROM measure WHERE id = :id")
    suspend fun delete(id: Long)

    /** Food-specific measures first, then the generic ones. */
    @Query("SELECT * FROM measure WHERE foodId = :foodId OR foodId IS NULL ORDER BY foodId IS NULL, grams")
    fun observeForFood(foodId: Long): Flow<List<MeasureEntity>>

    @Query("SELECT * FROM measure WHERE foodId = :foodId")
    suspend fun ownOf(foodId: Long): List<MeasureEntity>

    @Query("SELECT COUNT(*) FROM measure WHERE foodId IS NULL")
    suspend fun countGeneric(): Int
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise WHERE epochDay = :day ORDER BY loggedAt, id")
    fun observeDay(day: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun byId(id: Long): ExerciseEntity?

    /** Distinct names, most recent first, so the form can offer what the user usually does. */
    @Query("SELECT name FROM exercise GROUP BY name ORDER BY MAX(loggedAt) DESC LIMIT :limit")
    suspend fun recentNames(limit: Int): List<String>

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE ORDER BY epochDay, loggedAt, id")
    suspend fun history(name: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, loggedAt, id")
    suspend fun range(from: Long, to: Long): List<ExerciseEntity>

    @Insert
    suspend fun insertAll(sets: List<ExerciseEntity>)

    @Update
    suspend fun update(set: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE epochDay = :day")
    fun observe(day: Long): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM note WHERE epochDay = :day")
    suspend fun delete(day: Long)

    @Query("SELECT * FROM note WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    suspend fun range(from: Long, to: Long): List<NoteEntity>
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_measure WHERE epochDay = :day ORDER BY part")
    fun observeDay(day: Long): Flow<List<BodyMeasureEntity>>

    @Query("SELECT * FROM body_measure WHERE epochDay <= :day ORDER BY epochDay")
    suspend fun upTo(day: Long): List<BodyMeasureEntity>

    @Query("SELECT * FROM body_measure WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, part")
    suspend fun range(from: Long, to: Long): List<BodyMeasureEntity>

    @Query("DELETE FROM body_measure WHERE epochDay = :day")
    suspend fun clearDay(day: Long)

    @Insert
    suspend fun insertAll(measures: List<BodyMeasureEntity>)

    @Query("SELECT * FROM body_photo WHERE epochDay = :day ORDER BY takenAt, id")
    fun observePhotos(day: Long): Flow<List<BodyPhotoEntity>>

    @Query("SELECT DISTINCT epochDay FROM body_photo WHERE epochDay BETWEEN :from AND :to")
    fun observePhotoDays(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT * FROM body_photo ORDER BY epochDay, takenAt, id")
    suspend fun allPhotos(): List<BodyPhotoEntity>

    @Query("SELECT fileName FROM body_photo")
    suspend fun photoFileNames(): List<String>

    @Insert
    suspend fun insertPhoto(photo: BodyPhotoEntity): Long

    @Query("DELETE FROM body_photo WHERE id = :id")
    suspend fun deletePhoto(id: Long)
}

/** Whole-database dump and restore for the JSON backup. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM food") suspend fun foods(): List<FoodEntity>
    @Query("SELECT * FROM log_entry") suspend fun entries(): List<LogEntryEntity>
    @Query("SELECT * FROM combo") suspend fun combos(): List<ComboEntity>
    @Query("SELECT * FROM combo_item") suspend fun comboItems(): List<ComboItemEntity>
    @Query("SELECT * FROM recipe") suspend fun recipes(): List<RecipeEntity>
    @Query("SELECT * FROM recipe_item") suspend fun recipeItems(): List<RecipeItemEntity>
    @Query("SELECT * FROM goal") suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM water") suspend fun water(): List<WaterEntity>
    @Query("SELECT * FROM measure") suspend fun measures(): List<MeasureEntity>
    @Query("SELECT * FROM note") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM exercise") suspend fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM body_measure") suspend fun bodyMeasures(): List<BodyMeasureEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFoods(v: List<FoodEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntries(v: List<LogEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCombos(v: List<ComboEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertComboItems(v: List<ComboItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecipes(v: List<RecipeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecipeItems(v: List<RecipeItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoals(v: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWater(v: List<WaterEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMeasures(v: List<MeasureEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNotes(v: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExercises(v: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBodyMeasures(v: List<BodyMeasureEntity>)

    @Query("DELETE FROM log_entry") suspend fun clearEntries()
    @Query("DELETE FROM combo_item") suspend fun clearComboItems()
    @Query("DELETE FROM combo") suspend fun clearCombos()
    @Query("DELETE FROM recipe_item") suspend fun clearRecipeItems()
    @Query("DELETE FROM recipe") suspend fun clearRecipes()
    @Query("DELETE FROM measure") suspend fun clearMeasures()
    @Query("DELETE FROM food") suspend fun clearFoods()
    @Query("DELETE FROM goal") suspend fun clearGoals()
    @Query("DELETE FROM water") suspend fun clearWater()
    @Query("DELETE FROM note") suspend fun clearNotes()
    @Query("DELETE FROM exercise") suspend fun clearExercises()
    @Query("DELETE FROM body_measure") suspend fun clearBodyMeasures()
}
