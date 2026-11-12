package com.jokin.calorias.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [
        FoodEntity::class, LogEntryEntity::class, ComboEntity::class, ComboItemEntity::class,
        RecipeEntity::class, RecipeItemEntity::class, GoalEntity::class, WaterEntity::class,
        MeasureEntity::class, NoteEntity::class, ExerciseEntity::class, BodyMeasureEntity::class, BodyPhotoEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = FatAsPercent::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun comboDao(): ComboDao
    abstract fun recipeDao(): RecipeDao
    abstract fun goalDao(): GoalDao
    abstract fun waterDao(): WaterDao
    abstract fun measureDao(): MeasureDao
    abstract fun noteDao(): NoteDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun bodyDao(): BodyDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "calorias.db").build()
    }
}

/** Fat moved from grams per kg to a share of the energy (the new column defaults to 25 %); adds body measures and photos. */
@DeleteColumn(tableName = "goal", columnName = "fatPerKg")
class FatAsPercent : AutoMigrationSpec
