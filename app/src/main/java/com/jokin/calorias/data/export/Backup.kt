package com.jokin.calorias.data.export

import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.ComboEntity
import com.jokin.calorias.data.db.ComboItemEntity
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.data.db.LogEntryEntity
import com.jokin.calorias.data.db.MeasureEntity
import com.jokin.calorias.data.db.NoteEntity
import com.jokin.calorias.data.db.RecipeEntity
import com.jokin.calorias.data.db.RecipeItemEntity
import com.jokin.calorias.data.db.WaterEntity
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.domain.EnergyMath
import com.jokin.calorias.domain.Trends
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Full copy of the app's own data. The AI keys are deliberately not part of it. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val foods: List<FoodEntity>,
    val entries: List<LogEntryEntity>,
    val combos: List<ComboEntity>,
    val comboItems: List<ComboItemEntity>,
    val recipes: List<RecipeEntity>,
    val recipeItems: List<RecipeItemEntity>,
    val goals: List<GoalEntity>,
    val water: List<WaterEntity>,
    val measures: List<MeasureEntity>,
    val notes: List<NoteEntity>,
    val settings: AppSettings,
    /** Added in version 2; version 1 copies have none. */
    val exercises: List<ExerciseEntity> = emptyList(),
    /** Added in version 3. Progress photos travel in their own ZIP, not here. */
    val bodyMeasures: List<BodyMeasureEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "calorias-backup"
        const val VERSION = 5
    }
}

/** What is wrong with a backup, so the screen can say it in the app's language. */
sealed interface BackupProblem {
    data class Unreadable(val reason: String?) : BackupProblem
    data object ForeignFile : BackupProblem
    data object NewerVersion : BackupProblem
    data class RestingOutOfRange(val kcal: Int) : BackupProblem
    data class CorrectionOutOfRange(val kcal: Int) : BackupProblem
    data class ImpossibleBirthDate(val epochDay: Long) : BackupProblem
    data class BrokenReferences(val count: Int) : BackupProblem
}

/** [message] is for logs; [problem] carries what the user is told. */
class InvalidBackupException(val problem: BackupProblem, message: String) : IllegalArgumentException(message)

object BackupCodec {
    /** Loose sanity bounds so a tampered file cannot crash date maths; the real age check runs when the goal is saved. */
    private val BIRTH_DAY_RANGE = LocalDate.of(1900, 1, 1).toEpochDay()..LocalDate.of(2100, 1, 1).toEpochDay()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /** Rejects files that are not our backups or that reference rows they do not contain. */
    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            val reason = e.message?.take(120)
            throw InvalidBackupException(BackupProblem.Unreadable(reason), "No es una copia de Calorías válida: $reason")
        }
        if (backup.format != Backup.FORMAT) throw InvalidBackupException(BackupProblem.ForeignFile, "El archivo no es una copia de Calorías")
        if (backup.version > Backup.VERSION) throw InvalidBackupException(BackupProblem.NewerVersion, "La copia es de una versión más nueva de la app")
        backup.settings.garminRestingKcal?.let {
            if (it !in EnergyMath.RESTING_KCAL_RANGE) throw InvalidBackupException(BackupProblem.RestingOutOfRange(it), "Calorías en reposo fuera de rango: $it")
        }
        backup.settings.burnCorrectionKcal.let {
            if (it !in -Trends.MAX_BURN_CORRECTION..Trends.MAX_BURN_CORRECTION) throw InvalidBackupException(BackupProblem.CorrectionOutOfRange(it), "Ajuste del gasto fuera de rango: $it")
        }
        backup.goals.mapNotNull { it.birthEpochDay }.firstOrNull { it !in BIRTH_DAY_RANGE }?.let {
            throw InvalidBackupException(BackupProblem.ImpossibleBirthDate(it), "Fecha de nacimiento imposible en la copia: $it")
        }
        val foodIds = backup.foods.map { it.id }.toSet()
        val comboIds = backup.combos.map { it.id }.toSet()
        val recipeIds = backup.recipes.map { it.id }.toSet()
        val dangling = backup.entries.count { it.foodId !in foodIds } +
            backup.comboItems.count { it.foodId !in foodIds || it.comboId !in comboIds } +
            backup.recipeItems.count { it.foodId !in foodIds || it.recipeId !in recipeIds } +
            backup.recipes.count { it.foodId !in foodIds } +
            backup.measures.count { it.foodId != null && it.foodId !in foodIds }
        if (dangling > 0) throw InvalidBackupException(BackupProblem.BrokenReferences(dangling), "La copia está incompleta: $dangling referencias rotas")
        return backup
    }
}
