package com.jokin.calorias.data

import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.domain.ExerciseParser
import com.jokin.calorias.domain.ExerciseRecord
import com.jokin.calorias.domain.ExerciseRecords
import com.jokin.calorias.domain.LoggedSet
import com.jokin.calorias.domain.ParsedExercise
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException

data class ExerciseInterpretation(
    val sets: List<ParsedExercise>,
    val usedAi: Boolean,
    /** Why the AI mode was skipped or failed, so the fallback to basic mode is never silent. */
    val aiProblem: ExerciseAiProblem?,
)

sealed interface ExerciseAiProblem {
    /** [detail] is the network error's own message, when it has one. */
    data class Unreachable(val provider: String, val detail: String?) : ExerciseAiProblem
    data object UnexpectedFormat : ExerciseAiProblem
}

class ExerciseRepository(
    private val db: AppDatabase,
    private val ai: AiGateway,
) {
    fun observeDay(day: Long): Flow<List<ExerciseEntity>> = db.exerciseDao().observeDay(day)

    suspend fun byId(id: Long): ExerciseEntity? = db.exerciseDao().byId(id)

    /** Previous session and best set for [name], ignoring case; null for a new exercise. */
    suspend fun record(name: String, day: Long): ExerciseRecord? {
        val key = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val history = db.exerciseDao().history(key).map { LoggedSet(it.epochDay, it.sets, it.reps, it.weightKg) }
        return ExerciseRecords.summarize(history, day)
    }

    suspend fun recentNames(): List<String> = db.exerciseDao().recentNames(limit = 12)

    suspend fun add(day: Long, sets: List<ParsedExercise>) {
        val now = System.currentTimeMillis()
        db.exerciseDao().insertAll(sets.map { ExerciseEntity(epochDay = day, name = it.name, sets = it.sets, reps = it.reps, weightKg = it.weightKg, loggedAt = now) })
    }

    suspend fun update(set: ExerciseEntity) = db.exerciseDao().update(set)

    suspend fun delete(id: Long) = db.exerciseDao().delete(id)

    suspend fun interpret(text: String): ExerciseInterpretation {
        val basic = { ExerciseParser.parse(text) }
        val session = ai.session() ?: return ExerciseInterpretation(basic(), usedAi = false, aiProblem = null)
        return try {
            ExerciseInterpretation(session.interpretExercises(text), usedAi = true, aiProblem = null)
        } catch (e: IOException) {
            ExerciseInterpretation(basic(), usedAi = false, aiProblem = ExerciseAiProblem.Unreachable(session.providerName, e.message))
        } catch (e: SerializationException) {
            ExerciseInterpretation(basic(), usedAi = false, aiProblem = ExerciseAiProblem.UnexpectedFormat)
        }
    }
}
