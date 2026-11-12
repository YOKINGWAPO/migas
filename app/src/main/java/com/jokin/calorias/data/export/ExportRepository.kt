package com.jokin.calorias.data.export

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.jokin.calorias.R
import com.jokin.calorias.data.EnergyRepository
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.seed.BUNDLED_SOURCES
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.data.seed.Seeder
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportKind(@StringRes val labelRes: Int) {
    MEALS(R.string.mine_export_kind_meals),
    WATER(R.string.mine_export_kind_water),
    WEIGHT(R.string.mine_export_kind_weight),
    GARMIN(R.string.mine_export_kind_watch),
    COMBOS_RECIPES(R.string.mine_export_kind_combos_recipes),
    OWN_PRODUCTS(R.string.mine_own_products),
}

enum class ExportFormat(@StringRes val labelRes: Int, val mime: String, @StringRes val descriptionRes: Int) {
    CSV(R.string.mine_export_format_csv, "application/zip", R.string.mine_export_format_csv_hint),
    JSON(R.string.mine_export_format_json, "application/json", R.string.mine_export_format_json_hint),
    PDF(R.string.mine_export_format_pdf, "application/pdf", R.string.mine_export_format_pdf_hint),
}

data class ExportRequest(val kinds: Set<ExportKind>, val from: Long, val to: Long, val format: ExportFormat)

/** Per-day totals shared by the PDF and the progress screen. */
data class DaySummary(val epochDay: Long, val nutrients: Nutrients, val waterMl: Int, val weightKg: Double?, val note: String?)

class ExportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val health: HealthRepository,
    private val energy: EnergyRepository,
    private val settings: SettingsRepository,
    private val seeder: Seeder,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** English is the base language, so anything but Spanish gets the English CSV, like the rest of the app. */
    private val english: Boolean get() = context.resources.configuration.locales[0].language != "es"

    suspend fun export(request: ExportRequest): File = withContext(Dispatchers.IO) {
        require(request.from <= request.to) { context.getString(R.string.mine_export_bad_range) }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Old exports are temporary share payloads; keep only the newest one.
        dir.listFiles()?.forEach { it.delete() }
        val stamp = LocalDate.now().toString()
        when (request.format) {
            ExportFormat.JSON -> File(dir, "calorias_backup_$stamp.json").apply { writeText(BackupCodec.encode(backup())) }
            ExportFormat.CSV -> File(dir, "calorias_$stamp.zip").apply { writeZip(this, csvFiles(request)) }
            ExportFormat.PDF -> File(dir, context.getString(R.string.mine_export_pdf_file, stamp)).apply {
                PdfReport.write(context, this, request.from, request.to, summaries(request.from, request.to))
            }
        }
    }

    suspend fun summaries(from: Long, to: Long): List<DaySummary> {
        val entries = db.logDao().range(from, to).groupBy { it.entry.epochDay }
        val water = db.waterDao().range(from, to).groupBy { it.epochDay }
        val notes = db.noteDao().range(from, to).associateBy { it.epochDay }
        val weights = health.weights(from, to).associateBy { it.epochDay }
        return (from..to).map { day ->
            DaySummary(
                epochDay = day,
                nutrients = Nutrients.sum(entries[day].orEmpty().map { it.food.forGrams(it.entry.grams) }),
                waterMl = water[day].orEmpty().sumOf { it.ml },
                weightKg = weights[day]?.value,
                note = notes[day]?.text,
            )
        }
    }

    private suspend fun csvFiles(r: ExportRequest): Map<String, String> = buildMap {
        if (ExportKind.MEALS in r.kinds) {
            put(file(R.string.mine_export_file_meals), CsvExports.meals(db.logDao().range(r.from, r.to), zone, english))
            put(file(R.string.mine_export_file_notes), CsvExports.notes(db.noteDao().range(r.from, r.to), english))
            put(file(R.string.mine_export_file_exercises), CsvExports.exercises(db.exerciseDao().range(r.from, r.to), english))
        }
        if (ExportKind.WATER in r.kinds) put(file(R.string.mine_export_file_water), CsvExports.water(db.waterDao().range(r.from, r.to), zone, english))
        if (ExportKind.WEIGHT in r.kinds) {
            put(file(R.string.mine_export_file_weight), CsvExports.weight(health.weights(r.from, r.to), health.bodyFat(r.from, r.to), english))
            put(file(R.string.mine_export_file_measures), CsvExports.bodyMeasures(db.bodyDao().range(r.from, r.to), english))
        }
        if (ExportKind.GARMIN in r.kinds) {
            val sleep = health.sleep(r.from, r.to).associate { it.epochDay to it.minutes }
            val hr = health.restingHeartRate(r.from, r.to).associate { it.epochDay to it.value }
            val days = health.days(r.from, r.to)
            val goal = energy.goal()
            val split = if (goal == null) emptyMap() else {
                val resting = energy.restingKcal(goal, LocalDate.now().toEpochDay())
                val now = ZonedDateTime.now()
                days.mapValues { (day, d) -> energy.expenditure(day, d.burn, resting, now) }
            }
            put(file(R.string.mine_export_file_watch), CsvExports.garminDaily(days.values.toList(), split, sleep, hr, english))
            put(file(R.string.mine_export_file_workouts), CsvExports.workouts(health.workouts(r.from, r.to), zone, english))
        }
        if (ExportKind.COMBOS_RECIPES in r.kinds) {
            put(file(R.string.mine_export_file_combos), CsvExports.combos(db.comboDao().observeAll().first(), english))
            put(file(R.string.mine_export_file_recipes), CsvExports.recipes(db.recipeDao().observeAll().first(), english))
        }
        if (ExportKind.OWN_PRODUCTS in r.kinds) {
            put(file(R.string.mine_export_file_own_products), CsvExports.ownProducts(db.foodDao().observeOwn().first(), english))
        }
    }

    private fun file(@StringRes name: Int): String = context.getString(name)

    private fun writeZip(target: File, files: Map<String, String>) {
        require(files.isNotEmpty()) { context.getString(R.string.mine_export_no_kind) }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /** Bundled basics are re-seeded on install; only the ones that are referenced travel in the backup. */
    private suspend fun backup(): Backup {
        val b = db.backupDao()
        val entries = b.entries()
        val comboItems = b.comboItems()
        val recipes = b.recipes()
        val recipeItems = b.recipeItems()
        val measures = b.measures()
        val referenced = (entries.map { it.foodId } + comboItems.map { it.foodId } + recipeItems.map { it.foodId } +
            recipes.map { it.foodId } + measures.mapNotNull { it.foodId }).toSet()
        val foods = b.foods().filter { it.source !in BUNDLED_SOURCES || it.id in referenced }
        return Backup(
            exportedAt = System.currentTimeMillis(),
            foods = foods, entries = entries, combos = b.combos(), comboItems = comboItems,
            recipes = recipes, recipeItems = recipeItems, goals = b.goals(), water = b.water(),
            measures = measures, notes = b.notes(), settings = settings.current(), exercises = b.exercises(),
            bodyMeasures = b.bodyMeasures(),
        )
    }

    /** Replaces all app data with the backup's. Returns a short description of what was restored. */
    suspend fun restore(json: String): String {
        val backup = try {
            BackupCodec.decode(json)
        } catch (e: InvalidBackupException) {
            throw IllegalArgumentException(describe(e.problem), e)
        }
        db.withTransaction {
            val b = db.backupDao()
            b.clearEntries(); b.clearComboItems(); b.clearCombos(); b.clearRecipeItems(); b.clearRecipes()
            b.clearMeasures(); b.clearFoods(); b.clearGoals(); b.clearWater(); b.clearNotes(); b.clearExercises()
            b.clearBodyMeasures()
            b.insertFoods(backup.foods); b.insertEntries(backup.entries); b.insertCombos(backup.combos)
            b.insertComboItems(backup.comboItems); b.insertRecipes(backup.recipes); b.insertRecipeItems(backup.recipeItems)
            b.insertGoals(backup.goals); b.insertWater(backup.water); b.insertMeasures(backup.measures); b.insertNotes(backup.notes)
            b.insertExercises(backup.exercises); b.insertBodyMeasures(backup.bodyMeasures)
        }
        settings.save(backup.settings)
        seeder.ensureSeeded()
        val days = backup.entries.map { it.epochDay }.distinct().size
        return context.getString(R.string.mine_export_restored, backup.entries.size, days, backup.foods.size, backup.combos.size)
    }

    private fun describe(problem: BackupProblem): String = when (problem) {
        is BackupProblem.Unreadable -> context.getString(R.string.mine_backup_unreadable, problem.reason.toString())
        BackupProblem.ForeignFile -> context.getString(R.string.mine_backup_foreign)
        BackupProblem.NewerVersion -> context.getString(R.string.mine_backup_newer)
        is BackupProblem.RestingOutOfRange -> context.getString(R.string.mine_backup_resting_out_of_range, problem.kcal)
        is BackupProblem.CorrectionOutOfRange -> context.getString(R.string.mine_backup_correction_out_of_range, problem.kcal)
        is BackupProblem.ImpossibleBirthDate -> context.getString(R.string.mine_backup_impossible_birth_date, problem.epochDay)
        is BackupProblem.BrokenReferences ->
            context.resources.getQuantityString(R.plurals.mine_backup_broken_references, problem.count, problem.count)
    }
}
