package com.jokin.calorias.data.seed

import android.content.res.Resources
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.MeasureEntity
import com.jokin.calorias.domain.GenericMeasures

/**
 * Bundled data the app needs from the first launch: the basic-foods table (CIQUAL, plus USDA for what it
 * lacks) and generic household measures, named in the app's language at the time they are first stored.
 */
class Seeder(private val db: AppDatabase, private val resources: Resources) {
    /** Inserts only what is missing, so it is safe after a restore and when the table grows in an update. */
    suspend fun ensureSeeded() {
        db.foodDao().deleteUnusedBedca()
        val present = db.backupDao().foods().filter { it.source in BUNDLED_SOURCES }.map { it.searchName }.toSet()
        val english = resources.configuration.locales[0].language == "en"
        val bundled = resources.assets.open("alimentos.csv").bufferedReader()
            .useLines { FoodTableCsv.parse(it, english, System.currentTimeMillis()) }
        val missing = bundled.filter { it.searchName !in present }
        if (missing.isNotEmpty()) db.foodDao().insertAll(missing)

        if (db.measureDao().countGeneric() == 0) {
            db.measureDao().insertAll(
                listOf(
                    MeasureEntity(name = resources.getString(R.string.mine_measure_tablespoon), grams = GenericMeasures.TABLESPOON_G),
                    MeasureEntity(name = resources.getString(R.string.mine_measure_teaspoon), grams = GenericMeasures.TEASPOON_G),
                    MeasureEntity(name = resources.getString(R.string.mine_measure_cup), grams = GenericMeasures.CUP_G),
                    MeasureEntity(name = resources.getString(R.string.mine_measure_handful), grams = GenericMeasures.HANDFUL_G),
                    MeasureEntity(name = resources.getString(R.string.mine_measure_glass), grams = 250.0),
                )
            )
        }
    }
}

/** Re-seeded from the APK on every launch, so a backup only needs the ones that are referenced. */
val BUNDLED_SOURCES = setOf(FoodSource.CIQUAL, FoodSource.USDA)
