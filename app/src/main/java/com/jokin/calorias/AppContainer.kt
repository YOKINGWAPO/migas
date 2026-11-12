package com.jokin.calorias

import android.content.Context
import com.jokin.calorias.data.AiGateway
import com.jokin.calorias.data.BodyRepository
import com.jokin.calorias.data.ComboRepository
import com.jokin.calorias.data.EnergyRepository
import com.jokin.calorias.data.ExerciseRepository
import com.jokin.calorias.data.FoodRepository
import com.jokin.calorias.data.LogRepository
import com.jokin.calorias.data.PhotoInput
import com.jokin.calorias.data.RecipeRepository
import com.jokin.calorias.data.VoiceRepository
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.export.ExportRepository
import com.jokin.calorias.data.export.ImportRepository
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.data.ocr.LabelOcr
import com.jokin.calorias.data.remote.ClaudeClient
import com.jokin.calorias.data.remote.NanoGptClient
import com.jokin.calorias.data.remote.OpenFoodFactsClient
import com.jokin.calorias.data.seed.Seeder
import com.jokin.calorias.data.settings.SecretBox
import com.jokin.calorias.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency wiring; one instance per process, owned by [CaloriasApp]. */
class AppContainer(context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context, SecretBox())
    val health = HealthRepository(context)
    val ai = AiGateway(NanoGptClient(http, context), ClaudeClient(context), settings)
    val foods = FoodRepository(context, db, OpenFoodFactsClient(http, context), ai, LabelOcr(), settings)
    val log = LogRepository(context, db, health)
    val combos = ComboRepository(context, db, settings)
    val recipes = RecipeRepository(context, db)
    val energy = EnergyRepository(context, db, health, settings)
    val voice = VoiceRepository(context, db, foods, ai, settings)
    val exercises = ExerciseRepository(db, ai)
    val photos = PhotoInput(context)
    val body = BodyRepository(context, db, photos)
    val seeder = Seeder(db, context.resources)
    val export = ExportRepository(context, db, health, energy, settings, seeder)
    val imports = ImportRepository(db, health)
}
