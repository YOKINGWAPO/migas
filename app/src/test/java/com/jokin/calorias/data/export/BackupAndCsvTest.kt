package com.jokin.calorias.data.export

import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.EntryWithFood
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.data.db.LogEntryEntity
import com.jokin.calorias.data.health.HealthDay
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.CsvImport
import com.jokin.calorias.domain.DayValue
import com.jokin.calorias.domain.Expenditure
import com.jokin.calorias.domain.GoalPace
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Sex
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupAndCsvTest {
    private val banana = FoodEntity(
        id = 7, name = "Plátano", searchName = "platano", kcal = 94.0, protein = 1.2, carbs = 20.8, fat = 0.3,
        extrasJson = """{"SUGARS":17.0}""", source = FoodSource.BEDCA, createdAt = 0, updatedAt = 0,
    )
    private val pushUps = ExerciseEntity(id = 1, epochDay = 20_000, name = "Flexiones", sets = 4, reps = 15, loggedAt = 1)
    private val entry = LogEntryEntity(id = 1, epochDay = 20_000, meal = MealType.BREAKFAST, foodId = 7, grams = 200.0, loggedAt = 8 * 3_600_000L)

    private fun backup(entries: List<LogEntryEntity> = listOf(entry)) = Backup(
        exportedAt = 1, foods = listOf(banana), entries = entries, combos = emptyList(), comboItems = emptyList(),
        recipes = emptyList(), recipeItems = emptyList(), goals = emptyList(), water = emptyList(), measures = emptyList(),
        notes = emptyList(), settings = AppSettings(glassMl = 300),
    )

    @Test
    fun roundTrip() {
        val original = backup()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
    }

    @Test
    fun backupNeverContainsApiKeyField() {
        assertFalse(BackupCodec.encode(backup()).contains("key", ignoreCase = true))
    }

    @Test
    fun rejectsForeignJson() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode("""{"hello": 1}""") }
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode("not json") }
    }

    @Test
    fun rejectsOtherFormatAndNewerVersion() {
        val json = BackupCodec.encode(backup())
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(json.replace("calorias-backup", "otra-app")) }
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(json.replace("\"version\": ${Backup.VERSION}", "\"version\": 99")) }
    }

    @Test
    fun exercisesTravelInTheBackup() {
        val withSets = backup().copy(exercises = listOf(pushUps, ExerciseEntity(id = 2, epochDay = 20_000, name = "Press banca", sets = 3, reps = 10, weightKg = 40.0, loggedAt = 2)))
        assertEquals(withSets, BackupCodec.decode(BackupCodec.encode(withSets)))
    }

    @Test
    fun importsVersionOneCopiesWithoutExercises() {
        val v1 = BackupCodec.encode(backup()).replace("\"version\": ${Backup.VERSION}", "\"version\": 1")
            .replace(Regex(""",\s*"exercises": \[\s*]"""), "")
        assertFalse(v1.contains("exercises"))
        assertEquals(backup().copy(version = 1), BackupCodec.decode(v1))
    }

    @Test
    fun bodyMeasuresTravelInTheBackup() {
        val withMeasures = backup().copy(bodyMeasures = listOf(BodyMeasureEntity(20_000, BodyPart.WAIST, 92.5), BodyMeasureEntity(20_000, BodyPart.BICEPS, 36.0)))
        assertEquals(withMeasures, BackupCodec.decode(BackupCodec.encode(withMeasures)))
    }

    @Test
    fun importsVersionTwoCopiesWithoutBodyMeasures() {
        val v2 = BackupCodec.encode(backup()).replace("\"version\": ${Backup.VERSION}", "\"version\": 2")
            .replace(Regex(""",\s*"bodyMeasures": \[\s*]"""), "")
        assertFalse(v2.contains("bodyMeasures"))
        assertEquals(backup().copy(version = 2), BackupCodec.decode(v2))
    }

    @Test
    fun bodyMeasuresCsvHasOneRowPerPart() {
        val csv = CsvExports.bodyMeasures(listOf(BodyMeasureEntity(20_000, BodyPart.WAIST, 92.5), BodyMeasureEntity(20_000, BodyPart.THIGH, 58.0)))
        assertEquals(
            listOf("fecha;parte;cm", "2024-10-04;Cintura;92,50", "2024-10-04;Muslo;58,00"),
            csv.trimStart('\uFEFF').trim().lines().map { it.trim() },
        )
    }

    @Test
    fun oldGoalWithFatPerKgImportsWithDefaultFatShare() {
        val goal = GoalEntity(weightKg = 94.0, heightCm = 180.0, ageYears = 30, sex = Sex.MALE, pace = GoalPace.LOSE, proteinPerKg = 1.8)
        val json = BackupCodec.encode(backup().copy(goals = listOf(goal)))
            .replace("\"fatPercent\": 25", "\"fatPerKg\": 0.8")
        assertFalse(json.contains("fatPercent"))
        assertEquals(listOf(goal), BackupCodec.decode(json).goals)
    }

    @Test
    fun birthDateTravelsInTheBackup() {
        val goal = GoalEntity(weightKg = 94.0, heightCm = 180.0, ageYears = 30, sex = Sex.MALE, pace = GoalPace.LOSE, birthEpochDay = 9_000)
        assertEquals(listOf(goal), BackupCodec.decode(BackupCodec.encode(backup().copy(goals = listOf(goal)))).goals)
    }

    @Test
    fun rejectsImpossibleBirthDate() {
        val goal = GoalEntity(weightKg = 94.0, heightCm = 180.0, ageYears = 30, sex = Sex.MALE, pace = GoalPace.LOSE, birthEpochDay = Long.MAX_VALUE)
        val json = BackupCodec.encode(backup().copy(goals = listOf(goal)))
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(json) }
    }

    @Test
    fun versionThreeGoalWithoutBirthDateKeepsItsAge() {
        val goal = GoalEntity(weightKg = 94.0, heightCm = 180.0, ageYears = 30, sex = Sex.MALE, pace = GoalPace.LOSE)
        val v3 = BackupCodec.encode(backup().copy(goals = listOf(goal))).replace("\"version\": ${Backup.VERSION}", "\"version\": 3")
            .replace(Regex(""",\s*"birthEpochDay": null"""), "")
        assertFalse(v3.contains("birthEpochDay"))
        val imported = BackupCodec.decode(v3).goals.single()
        assertEquals(goal, imported)
        assertEquals(30, imported.ageOn(30_000))
    }

    @Test
    fun goalAgeComesFromTheBirthDateWhenThereIsOne() {
        val birth = LocalDate.parse("1995-10-16").toEpochDay()
        val goal = GoalEntity(weightKg = 94.0, heightCm = 180.0, ageYears = 20, sex = Sex.MALE, pace = GoalPace.LOSE, birthEpochDay = birth)
        assertEquals(31, goal.ageOn(LocalDate.parse("2026-10-16").toEpochDay()))
    }

    @Test
    fun exercisesCsvListsEverySet() {
        val csv = CsvExports.exercises(listOf(pushUps, pushUps.copy(id = 2, name = "Press banca", sets = 3, reps = 10, weightKg = 42.5)))
        assertEquals(
            listOf("fecha;ejercicio;series;repeticiones;peso_kg", "2024-10-04;Flexiones;4;15;", "2024-10-04;Press banca;3;10;42,50"),
            csv.trimStart('\uFEFF').trim().lines().map { it.trim() },
        )
    }

    @Test
    fun rejectsDanglingReferences() {
        val broken = BackupCodec.encode(backup(listOf(entry.copy(foodId = 99))))
        val e = assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(broken) }
        assertEquals("La copia está incompleta: 1 referencias rotas", e.message)
        assertEquals(BackupProblem.BrokenReferences(1), e.problem)
    }

    @Test
    fun foreignAndNewerBackupsSayWhy() {
        val json = BackupCodec.encode(backup())
        val foreign = assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(json.replace("calorias-backup", "otra-app")) }
        assertEquals(BackupProblem.ForeignFile, foreign.problem)
        val newer = assertThrows(InvalidBackupException::class.java) {
            BackupCodec.decode(json.replace("\"version\": ${Backup.VERSION}", "\"version\": 99"))
        }
        assertEquals(BackupProblem.NewerVersion, newer.problem)
    }

    @Test
    fun englishCsvHasEnglishWords() {
        val csv = CsvExports.bodyMeasures(listOf(BodyMeasureEntity(20_000, BodyPart.WAIST, 92.5)), english = true)
        assertEquals(listOf("date,part,cm", "2024-10-04,Waist,92.50"), csv.trimStart('\uFEFF').trim().lines().map { it.trim() })
        val meals = CsvExports.meals(listOf(EntryWithFood(entry, banana)), ZoneOffset.UTC, english = true).trimStart('\uFEFF').lines()
        assertEquals("date,time,meal,food,brand,source,grams,kcal,protein_g,carbs_g,fat_g,sugars_g,fiber_g,saturated_fat_g,salt_g", meals[0])
        assertEquals("2024-10-04,08:00,Breakfast,Plátano,,BEDCA,200.00,188.00,2.40,41.60,0.60,34.00,,,", meals[1])
    }

    @Test
    fun mealsCsvReadsBackIntoTheImporterInBothLanguages() {
        listOf(false, true).forEach { english ->
            val csv = CsvExports.meals(listOf(EntryWithFood(entry, banana)), ZoneOffset.UTC, english)
            val parsed = CsvImport.parse(csv, LocalDate.of(2026, 11, 12)) as CsvImport.Parsed.Diary
            assertEquals(emptyList<CsvImport.RowError>(), parsed.errors)
            val row = parsed.rows.single()
            assertEquals(20_000L, row.epochDay)
            assertEquals(MealType.BREAKFAST, row.meal)
            assertEquals("Plátano", row.name)
            assertEquals(200.0, row.grams!!, 1e-9)
            assertEquals(188.0, row.nutrients.kcal, 1e-9)
            assertEquals(2.4, row.nutrients.protein, 1e-9)
        }
    }

    @Test
    fun weightCsvReadsBackIntoTheImporterInBothLanguages() {
        listOf(false, true).forEach { english ->
            val csv = CsvExports.weight(listOf(DayValue(20_000, 82.4)), listOf(DayValue(20_000, 21.5)), english)
            val parsed = CsvImport.parse(csv, LocalDate.of(2026, 11, 12)) as CsvImport.Parsed.Weights
            assertEquals(listOf(CsvImport.WeightRow(2, 20_000, 82.4)), parsed.rows)
        }
    }

    @Test
    fun keepsGarminRestingAndRejectsAbsurdValues() {
        val withResting = backup().copy(settings = AppSettings(garminRestingKcal = 2115))
        assertEquals(2115, BackupCodec.decode(BackupCodec.encode(withResting)).settings.garminRestingKcal)
        val absurd = BackupCodec.encode(backup().copy(settings = AppSettings(garminRestingKcal = 50_000)))
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(absurd) }
    }

    @Test
    fun keepsBurnCorrectionAndRejectsAbsurdValues() {
        val withCorrection = backup().copy(settings = AppSettings(burnCorrectionKcal = -250, burnReviewDay = 20_000))
        val decoded = BackupCodec.decode(BackupCodec.encode(withCorrection)).settings
        assertEquals(-250, decoded.burnCorrectionKcal)
        assertEquals(20_000L, decoded.burnReviewDay)
        val absurd = BackupCodec.encode(backup().copy(settings = AppSettings(burnCorrectionKcal = -5000)))
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(absurd) }
    }

    @Test
    fun garminCsvUsesGarminStyleSplit() {
        val day = HealthDay(epochDay = 20_000, totalKcal = 2597.0, activeKcal = 342.0, steps = 9000, distanceM = 7000.0)
        val noSplit = day.copy(epochDay = 20_001)
        val csv = CsvExports.garminDaily(listOf(day, noSplit), mapOf(20_000L to Expenditure(2115.0, 482.0)), emptyMap(), emptyMap())
        val rows = csv.removePrefix("\uFEFF").trim().lines()
        assertEquals("fecha;kcal_totales;kcal_reposo;kcal_activas;pasos;distancia_km;sueno_h;fc_reposo", rows[0])
        assertEquals("2024-10-04;2597,00;2115,00;482,00;9000;7,00;;", rows[1])
        assertEquals("2024-10-05;2597,00;;;9000;7,00;;", rows[2])
    }

    @Test
    fun mealsCsvScalesToLoggedGrams() {
        val csv = CsvExports.meals(listOf(EntryWithFood(entry, banana)), ZoneOffset.UTC)
        val row = csv.lines()[1].split(';')
        assertEquals("2024-10-04", row[0])
        assertEquals("08:00", row[1])
        assertEquals("Desayuno", row[2])
        assertEquals("200,00", row[6])
        assertEquals("188,00", row[7])
        assertEquals("34,00", row[11])
    }
}
