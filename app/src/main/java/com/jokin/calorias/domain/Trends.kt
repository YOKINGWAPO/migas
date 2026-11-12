package com.jokin.calorias.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

data class DayValue(val epochDay: Long, val value: Double)

object Trends {
    /** Energy stored in 1 kg of body weight change; the usual approximation for mixed tissue. */
    const val KCAL_PER_KG = 7700.0
    const val MIN_DAYS_FOR_REAL_TDEE = 21

    /** Minimum share of days with food logged so the intake average means something. */
    private const val MIN_LOGGED_SHARE = 0.7

    /** For each measured day, the mean of the weigh-ins in the 7 days ending that day (chart trend line). */
    fun movingAverage7(points: List<DayValue>): List<DayValue> {
        val sorted = points.sortedBy { it.epochDay }
        return sorted.map { p ->
            val window = sorted.filter { it.epochDay in (p.epochDay - 6)..p.epochDay }
            DayValue(p.epochDay, window.map { it.value }.average())
        }
    }

    /**
     * Weekly weight change in kg/week: least-squares slope of the raw weigh-ins over the last
     * [windowDays]. The regression already absorbs daily noise; fitting the moving average instead
     * would skew the slope at the start of the window. Null with fewer than two distinct days.
     */
    /**
     * [total] spread over the weeks that actually have data: from [firstDataDay] (or [from], whichever is
     * later) to [to]. A year of km with only a month of watch data is not a year's average. Never less
     * than a week, so a couple of days of data are not extrapolated into a big weekly figure.
     */
    fun perWeek(total: Double, from: Long, to: Long, firstDataDay: Long?): Double {
        val start = maxOf(from, firstDataDay ?: from)
        val days = maxOf(DAYS_PER_WEEK, to - start + 1)
        return total / (days / DAYS_PER_WEEK.toDouble())
    }

    private const val DAYS_PER_WEEK = 7L

    fun weeklyRate(weights: List<DayValue>, today: Long, windowDays: Int = 28): Double? {
        val points = weights.filter { it.epochDay in (today - windowDays + 1)..today }
        if (points.size < 2) return null
        val meanX = points.map { it.epochDay.toDouble() }.average()
        val meanY = points.map { it.value }.average()
        val sxx = points.sumOf { (it.epochDay - meanX) * (it.epochDay - meanX) }
        if (sxx == 0.0) return null
        val sxy = points.sumOf { (it.epochDay - meanX) * (it.value - meanY) }
        return sxy / sxx * 7
    }

    /**
     * Real expenditure from the energy balance: what was eaten minus what the weight trend says
     * was stored. Needs weigh-ins spanning [MIN_DAYS_FOR_REAL_TDEE] days inside the window and
     * food logged on most of those days.
     */
    fun estimatedRealTdee(intakeByDay: Map<Long, Double>, weights: List<DayValue>, today: Long, windowDays: Int = 28): Double? {
        val inWindow = weights.filter { it.epochDay in (today - windowDays + 1)..today }
        val firstDay = inWindow.minOfOrNull { it.epochDay } ?: return null
        val lastDay = inWindow.maxOf { it.epochDay }
        val span = lastDay - firstDay
        if (span < MIN_DAYS_FOR_REAL_TDEE) return null

        val intakes = (firstDay..lastDay).mapNotNull { intakeByDay[it] }.filter { it > 0 }
        if (intakes.size < (span + 1) * MIN_LOGGED_SHARE) return null

        val kgPerDay = weeklyRate(inWindow, today, windowDays)?.div(7) ?: return null
        return intakes.average() - kgPerDay * KCAL_PER_KG
    }

    /** Largest recalibration offered; a bigger gap points at unlogged food or a bad weigh-in, not at Garmin. */
    const val MAX_BURN_CORRECTION = 600

    /** Smaller changes are within the noise of the estimate and not worth asking about. */
    const val MIN_CORRECTION_CHANGE = 100

    /**
     * Compares the burn implied by intake and weight with Garmin's average over the same days and
     * returns the correction to add to Garmin's figures. It is absolute (against Garmin's raw totals),
     * so accepting it twice does not stack. Only finished days count: today's intake and Garmin total
     * are partial. Null when there is not enough data or the change from [currentCorrection] is noise.
     */
    fun burnReview(
        intakeByDay: Map<Long, Double>,
        weights: List<DayValue>,
        garminTotalByDay: Map<Long, Double>,
        today: Long,
        currentCorrection: Int,
        windowDays: Int = 28,
    ): BurnReview? {
        val yesterday = today - 1
        val real = estimatedRealTdee(intakeByDay.filterKeys { it <= yesterday }, weights.filter { it.epochDay <= yesterday }, yesterday, windowDays)
            ?: return null
        val days = (yesterday - windowDays + 1)..yesterday
        val garmin = days.mapNotNull { garminTotalByDay[it] }
        if (garmin.size < windowDays * MIN_LOGGED_SHARE) return null
        val garminAvg = garmin.average()
        val correction = ((real - garminAvg) / 10).roundToInt() * 10
        val clamped = correction.coerceIn(-MAX_BURN_CORRECTION, MAX_BURN_CORRECTION)
        if (abs(clamped - currentCorrection) < MIN_CORRECTION_CHANGE) return null
        return BurnReview(real, garminAvg, clamped)
    }

    /** Below this the weight is holding steady, and any date would be decades away or meaningless. */
    private const val MIN_RATE_FOR_ETA = 0.05

    /** Within this many kg of the target it counts as reached; scale noise is about that size. */
    private const val REACHED_MARGIN_KG = 0.3

    /** When the weight trend reaches [targetKg] at [weeklyRate] (kg/week), counting from [today]. */
    fun targetEta(currentKg: Double, targetKg: Double, weeklyRate: Double, today: Long): TargetEta {
        val remaining = targetKg - currentKg
        if (abs(remaining) <= REACHED_MARGIN_KG) return TargetEta.Reached
        if (abs(weeklyRate) < MIN_RATE_FOR_ETA || sign(weeklyRate) != sign(remaining)) return TargetEta.NotApproaching
        val weeks = remaining / weeklyRate
        return TargetEta.On(today + (weeks * 7).roundToLong(), weeks)
    }
}

data class BurnReview(val realTdee: Double, val garminAvg: Double, val correctionKcal: Int)

sealed interface TargetEta {
    data object Reached : TargetEta
    data object NotApproaching : TargetEta
    data class On(val epochDay: Long, val weeks: Double) : TargetEta
}
