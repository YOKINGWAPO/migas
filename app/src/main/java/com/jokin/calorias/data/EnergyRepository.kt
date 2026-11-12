package com.jokin.calorias.data

import android.content.Context
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.data.health.HealthDay
import com.jokin.calorias.data.health.HealthRepository
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.BirthDate
import com.jokin.calorias.domain.DayBurn
import com.jokin.calorias.domain.EnergyMath
import com.jokin.calorias.domain.Expenditure
import com.jokin.calorias.domain.MacroTargets
import com.jokin.calorias.domain.Profile
import com.jokin.calorias.domain.Trends
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

data class DayEnergy(
    val expenditure: Expenditure,
    val targetKcal: Int,
    val macros: MacroTargets,
    /** False when Health Connect gave nothing for the day and the basal is an estimate. */
    val fromGarmin: Boolean,
    val health: HealthDay?,
    val weightKg: Double,
    /** Today's Garmin total as it stands now (Garmin's own figure); null on other days. */
    val garminSoFarKcal: Double?,
    /** False while the resting burn is still the Mifflin-St Jeor estimate. */
    val restingFromGarmin: Boolean,
    /** Accepted recalibration of Garmin's burn from the weight trend; 0 when none. */
    val burnCorrectionKcal: Int,
)

private const val BURN_REVIEW_EVERY_DAYS = 7

/** Joins the goal with Health Connect to answer "how much can I eat on this day". */
class EnergyRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val health: HealthRepository,
    private val settings: SettingsRepository,
) {
    fun observeGoal(): Flow<GoalEntity?> = db.goalDao().observe()

    suspend fun goal(): GoalEntity? = db.goalDao().get()

    suspend fun saveGoal(goal: GoalEntity) {
        require(goal.weightKg in 30.0..300.0) { context.getString(R.string.today_error_weight) }
        require(goal.heightCm in 120.0..230.0) { context.getString(R.string.today_error_height) }
        val today = LocalDate.now().toEpochDay()
        require(goal.birthEpochDay == null || goal.birthEpochDay <= today) { context.getString(R.string.today_error_birth_future) }
        require(goal.ageOn(today) in BirthDate.AGE_RANGE) { context.getString(R.string.today_error_age) }
        require(goal.proteinPerKg in 0.5..3.0 && goal.fatPercent in 15..45) { context.getString(R.string.today_error_macros) }
        db.goalDao().upsert(goal)
    }

    suspend fun garminRestingKcal(): Int? = settings.current().garminRestingKcal

    suspend fun burnCorrectionKcal(): Int = settings.current().burnCorrectionKcal

    /** Answering the weekly review either way marks [today] so it asks again a week later. */
    suspend fun answerBurnReview(today: Long, acceptedCorrection: Int?) {
        require(acceptedCorrection == null || acceptedCorrection in -Trends.MAX_BURN_CORRECTION..Trends.MAX_BURN_CORRECTION) { context.getString(R.string.today_error_adjustment) }
        settings.update { it.copy(burnCorrectionKcal = acceptedCorrection ?: it.burnCorrectionKcal, burnReviewDay = today) }
    }

    suspend fun clearBurnCorrection() = settings.update { it.copy(burnCorrectionKcal = 0) }

    /** True when the last answer is a week old or there was none. */
    suspend fun burnReviewDue(today: Long): Boolean = settings.current().burnReviewDay?.let { today - it >= BURN_REVIEW_EVERY_DAYS } ?: true

    /** Null clears it and goes back to the Mifflin-St Jeor estimate. */
    suspend fun saveGarminRestingKcal(kcal: Int?) {
        require(kcal == null || kcal in EnergyMath.RESTING_KCAL_RANGE) { context.getString(R.string.today_error_resting) }
        settings.update { it.copy(garminRestingKcal = kcal) }
    }

    /** Most recent scale weight in the last 60 days, if Garmin wrote any. */
    suspend fun latestScaleWeight(today: Long): Double? = health.weights(today - 60, today).maxByOrNull { it.epochDay }?.value

    /** Full-day resting burn for [goal], with the latest scale weight when Garmin has one. */
    suspend fun restingKcal(goal: GoalEntity, today: Long): Double =
        restingKcal(goal, latestScaleWeight(today) ?: goal.weightKg, settings.current().garminRestingKcal, today)

    private fun restingKcal(goal: GoalEntity, weightKg: Double, garminRestingKcal: Int?, day: Long): Double =
        EnergyMath.restingKcal(Profile(weightKg, goal.heightCm, goal.ageOn(day), goal.sex), garminRestingKcal)

    /** Garmin-style split of [burn] for [day], judged at [now] so today only counts the resting burn so far. */
    fun expenditure(day: Long, burn: DayBurn, restingKcal: Double, now: ZonedDateTime = ZonedDateTime.now()): Expenditure =
        EnergyMath.expenditure(burn, restingKcal, EnergyMath.elapsedFraction(LocalDate.ofEpochDay(day), now))

    /** A finished day's whole burn as the app splits it, or null when the watch wrote no calories that day. */
    fun burnTotal(day: HealthDay, restingKcal: Double, now: ZonedDateTime = ZonedDateTime.now()): Double? =
        if (day.hasBurn) expenditure(day.epochDay, day.burn, restingKcal, now).total else null

    suspend fun forDay(goal: GoalEntity, day: Long, today: Long): DayEnergy {
        val weight = latestScaleWeight(today) ?: goal.weightKg
        val current = settings.current()
        val calc = dayCalc(goal, day, today, health.days(day - 14, day), weight, current, ZonedDateTime.now())
        val reference = EnergyMath.referenceWeightKg(weight, goal.heightCm, goal.targetWeightKg)
        val healthDay = calc.health
        return DayEnergy(
            expenditure = calc.expenditure,
            targetKcal = calc.target,
            macros = EnergyMath.macroTargets(calc.target, reference, goal.proteinPerKg, goal.fatPercent),
            fromGarmin = calc.fromGarmin,
            health = healthDay,
            weightKg = weight,
            garminSoFarKcal = healthDay?.totalKcal?.takeIf { day == today },
            restingFromGarmin = current.garminRestingKcal != null,
            burnCorrectionKcal = current.burnCorrectionKcal,
        )
    }

    /**
     * Each day's target in [days] up to [today], exactly as [forDay] computes it, with a single
     * Health Connect read for the whole range (the week strip and the calendar colour every day).
     */
    suspend fun targetsFor(goal: GoalEntity, days: LongRange, today: Long): Map<Long, Int> {
        val last = minOf(days.last, today)
        if (last < days.first) return emptyMap()
        val healthDays = health.days(days.first - 14, last)
        val weight = latestScaleWeight(today) ?: goal.weightKg
        val current = settings.current()
        val now = ZonedDateTime.now()
        return (days.first..last).associateWith { dayCalc(goal, it, today, healthDays, weight, current, now).target }
    }

    private class DayCalc(val expenditure: Expenditure, val target: Int, val fromGarmin: Boolean, val health: HealthDay?)

    /** [healthDays] must cover the 14 days before [day] and [day] itself. */
    private fun dayCalc(
        goal: GoalEntity,
        day: Long,
        today: Long,
        healthDays: Map<Long, HealthDay>,
        weight: Double,
        current: AppSettings,
        now: ZonedDateTime,
    ): DayCalc {
        val resting = restingKcal(goal, weight, current.garminRestingKcal, day)
        val healthDay = healthDays[day]
        val expenditure = expenditure(day, healthDay?.burn ?: DayBurn(null, null), resting, now)
        val history = (day - 14 until day).mapNotNull { healthDays[it] }.filter { it.hasBurn }
            .map { expenditure(it.epochDay, it.burn, resting, now) }
        val fromGarmin = healthDay?.hasBurn == true
        val target = EnergyMath.dailyTargetKcal(
            goal.mode, goal.pace, goal.sex, expenditure,
            dayHasWatchData = fromGarmin, dayFinished = day < today, last14Days = history, restingKcal = resting,
            burnCorrectionKcal = current.burnCorrectionKcal,
        )
        return DayCalc(expenditure, target, fromGarmin, healthDay)
    }
}
