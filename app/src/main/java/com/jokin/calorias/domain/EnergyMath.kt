package com.jokin.calorias.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/** Names are stored in the database and in backups: add entries, never rename them. */
enum class GoalPace(val kgPerWeek: Double) {
    LOSE_GENTLE(-0.25),
    LOSE(-0.5),
    LOSE_FAST(-0.75),
    MAINTAIN(0.0),
    GAIN(0.25);

    /** Daily energy change for this pace, at [EnergyMath.KCAL_PER_KG] per kg of body weight. */
    val dailyKcal: Int get() = (kgPerWeek * EnergyMath.KCAL_PER_KG / 7).roundToInt()
}

enum class TargetMode { DYNAMIC, FIXED }

data class Profile(
    val weightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val sex: Sex,
)

/** Basal is what the body burns at rest; active comes from movement measured by the watch. */
data class Expenditure(val basal: Double, val active: Double) {
    val total: Double get() = basal + active
}

data class MacroTargets(val proteinG: Double, val carbsG: Double, val fatG: Double)

/** Health Connect totals for one day; null when the watch wrote nothing for that day. */
data class DayBurn(val totalKcal: Double?, val activeKcal: Double?)

object EnergyMath {
    /** Energy in a kg of body weight lost or gained, mostly fat (the usual 7700 kcal/kg rule). */
    const val KCAL_PER_KG = 7700.0

    /** A deficit beyond a quarter of the burn costs muscle and is hard to sustain. */
    const val MAX_DEFICIT_FRACTION = 0.25

    /** Usual floors below which an unsupervised diet stops being safe. */
    fun minTargetKcal(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    /** Carbs below this (the adult RDA, mostly for the brain) deserve a warning. */
    const val LOW_CARBS_G = 130.0

    /** Fat floor per kg of reference weight, for hormones and fat-soluble vitamins. */
    const val MIN_FAT_PER_KG = 0.6

    private const val HEALTHY_BMI_CEILING = 25.0

    /** Plausible full-day resting burn for an adult; anything outside is a typo. */
    val RESTING_KCAL_RANGE = 800..4000

    /** Sedentary multiplier, used only for the fixed mode when there is no watch history yet. */
    private const val SEDENTARY_FACTOR = 1.2

    fun mifflinStJeor(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.ageYears
        return base + if (p.sex == Sex.MALE) 5 else -161
    }

    /** Full-day resting burn: Garmin's own figure when the user copied it in, else Mifflin-St Jeor. */
    fun restingKcal(profile: Profile, garminRestingKcal: Int?): Double =
        garminRestingKcal?.toDouble() ?: mifflinStJeor(profile)

    /** Share of [day] already lived at [now]: 1 for past days, 0 for future ones. DST days keep their real length. */
    fun elapsedFraction(day: LocalDate, now: ZonedDateTime): Double {
        val start = day.atStartOfDay(now.zone)
        val end = day.plusDays(1).atStartOfDay(now.zone)
        if (!now.isAfter(start)) return 0.0
        if (!now.isBefore(end)) return 1.0
        return Duration.between(start, now).toMillis().toDouble() / Duration.between(start, end).toMillis()
    }

    /**
     * Garmin's own split (other brands write either figure; both paths are covered): the resting burn accrues evenly through the day and "active" is everything
     * above it. Health Connect's active-calories record only covers workouts, so it is a floor: a total
     * that lags behind (Garmin writes it in blocks) or a resting estimate above Garmin's own would
     * otherwise hide a logged workout. Basal is always the full day, which makes today's total a projection.
     */
    fun expenditure(day: DayBurn, restingKcal: Double, elapsedFraction: Double): Expenditure {
        val aboveResting = day.totalKcal?.let { it - restingKcal * elapsedFraction } ?: 0.0
        return Expenditure(restingKcal, maxOf(0.0, aboveResting, day.activeKcal ?: 0.0))
    }

    /**
     * Base burn plus the pace adjustment, never below the floor for [sex]. The fixed mode uses the
     * typical day (14-day average). The dynamic mode uses the real burn of a finished day, and for
     * today the typical day unless the watch already shows more, so the target never starts the
     * morning at the resting burn alone. Without watch data for the day it falls back to typical.
     * [burnCorrectionKcal] recalibrates Garmin's burn against the weight trend (see [Trends.burnReview]);
     * it is left out when there is no Garmin data at all and the base is the sedentary estimate.
     */
    fun dailyTargetKcal(
        mode: TargetMode,
        pace: GoalPace,
        sex: Sex,
        dayExpenditure: Expenditure,
        dayHasWatchData: Boolean,
        dayFinished: Boolean,
        last14Days: List<Expenditure>,
        restingKcal: Double,
        burnCorrectionKcal: Int = 0,
    ): Int {
        val typical = if (last14Days.isEmpty()) restingKcal * SEDENTARY_FACTOR else last14Days.map { it.total }.average()
        val usesTypical = mode == TargetMode.FIXED || !dayHasWatchData
        val garminBase = when {
            usesTypical -> typical
            dayFinished -> dayExpenditure.total
            else -> max(typical, dayExpenditure.total)
        }
        val base = if (usesTypical && last14Days.isEmpty()) garminBase else garminBase + burnCorrectionKcal
        val adjust = max(pace.dailyKcal.toDouble(), -base * MAX_DEFICIT_FRACTION)
        return max(minTargetKcal(sex), (base + adjust).roundToInt())
    }

    /** Protein and the fat floor scale with this weight: the target weight, else the weight at BMI 25 if above it. */
    fun referenceWeightKg(weightKg: Double, heightCm: Double, targetWeightKg: Double?): Double {
        targetWeightKg?.let { return it }
        val heightM = heightCm / 100
        return min(weightKg, HEALTHY_BMI_CEILING * heightM * heightM)
    }

    /** Protein per kg of reference weight, fat as a share of the energy (with a floor), carbs the rest. */
    fun macroTargets(targetKcal: Int, referenceWeightKg: Double, proteinPerKg: Double, fatPercent: Int): MacroTargets {
        val protein = referenceWeightKg * proteinPerKg
        val fat = max(targetKcal * fatPercent / 100.0 / 9, referenceWeightKg * MIN_FAT_PER_KG)
        val carbs = max(0.0, (targetKcal - protein * 4 - fat * 9) / 4)
        return MacroTargets(protein, carbs, fat)
    }
}
