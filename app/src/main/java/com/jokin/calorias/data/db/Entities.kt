package com.jokin.calorias.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jokin.calorias.domain.BirthDate
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.GoalPace
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Sex
import com.jokin.calorias.domain.TargetMode
import kotlinx.serialization.Serializable

enum class FoodSource {
    OPEN_FOOD_FACTS,
    /** Basics bundled up to 1.11.1; no longer seeded (its licence forbids redistribution), kept for foods already in use. */
    BEDCA,
    OWN,
    ESTIMATED,
    RECIPE,
    /** Logged by its totals alone (eating out); stored as one serving of [com.jokin.calorias.data.QUICK_SERVING_GRAMS]. */
    QUICK,
    /** Brought in from another app's CSV; searchable like cached products, kept out of the own-foods list. */
    IMPORTED,
    /** Bundled from ANSES's CIQUAL 2025 table (Licence Ouverte / Etalab 2.0), see assets/alimentos.csv. */
    CIQUAL,
    /** Bundled from USDA FoodData Central (public domain) for basics CIQUAL lacks. */
    USDA,
}

/** Nutrient values are per 100 g; [extrasJson] holds the [com.jokin.calorias.domain.ExtraNutrient] map. */
@Serializable
@Entity(
    tableName = "food",
    indices = [Index(value = ["barcode"], unique = true), Index("searchName")],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    /** Lowercase, accent-free name + brand, so search matches "platano" and "plátano". */
    val searchName: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val extrasJson: String = "{}",
    val servingGrams: Double? = null,
    val servingName: String? = null,
    val source: FoodSource,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "log_entry",
    indices = [Index("epochDay"), Index("foodId")],
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT)],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: MealType,
    val foodId: Long,
    val grams: Double,
    /** Set when the entry came from logging a combo, to show it grouped. */
    val comboId: Long? = null,
    val loggedAt: Long,
)

@Serializable
@Entity(tableName = "combo")
data class ComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "🍽️",
)

@Serializable
@Entity(
    tableName = "combo_item",
    indices = [Index("comboId"), Index("foodId")],
    foreignKeys = [
        ForeignKey(entity = ComboEntity::class, parentColumns = ["id"], childColumns = ["comboId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class ComboItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comboId: Long,
    val foodId: Long,
    val grams: Double,
)

/** A recipe is backed by a food of source RECIPE whose per-100 g values come from the ingredients. */
@Serializable
@Entity(
    tableName = "recipe",
    indices = [Index(value = ["foodId"], unique = true)],
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT)],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Int,
    val foodId: Long,
)

@Serializable
@Entity(
    tableName = "recipe_item",
    indices = [Index("recipeId"), Index("foodId")],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class RecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodId: Long,
    val grams: Double,
)

/** Single row (id = 1) with the user's profile and goal. */
@Serializable
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: Int = 1,
    val weightKg: Double,
    val heightCm: Double,
    /** Age when the goal was saved; only used while [birthEpochDay] is missing (goals from before 1.4.0). */
    val ageYears: Int,
    val sex: Sex,
    val pace: GoalPace,
    val mode: TargetMode = TargetMode.DYNAMIC,
    /** Grams per kg of reference weight (see EnergyMath.referenceWeightKg). */
    val proteinPerKg: Double = 1.6,
    @ColumnInfo(defaultValue = "25") val fatPercent: Int = 25,
    val targetWeightKg: Double? = null,
    val birthEpochDay: Long? = null,
) {
    fun ageOn(day: Long): Int = birthEpochDay?.let { BirthDate.ageOn(it, day) } ?: ageYears
}

@Serializable
@Entity(tableName = "water", indices = [Index("epochDay")])
data class WaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val ml: Int,
    val loggedAt: Long,
)

/** Household measure; [foodId] null means a generic one available for every food. */
@Serializable
@Entity(
    tableName = "measure",
    indices = [Index("foodId")],
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
)
data class MeasureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
)

@Serializable
@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey val epochDay: Long,
    val text: String,
)

/** Strength sets; deliberately no calories: Garmin already counts what it measures. */
@Serializable
@Entity(tableName = "exercise", indices = [Index("epochDay")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val loggedAt: Long,
)

/** One tape measurement per body part and day. */
@Serializable
@Entity(tableName = "body_measure", primaryKeys = ["epochDay", "part"])
data class BodyMeasureEntity(
    val epochDay: Long,
    val part: BodyPart,
    val cm: Double,
)

/** Progress photo; the JPEG lives in the app's private storage, never in the JSON backup (size). */
@Entity(tableName = "body_photo", indices = [Index("epochDay"), Index(value = ["fileName"], unique = true)])
data class BodyPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val fileName: String,
    val takenAt: Long,
)
