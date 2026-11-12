package com.jokin.calorias.ui.progress

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jokin.calorias.AppContainer
import com.jokin.calorias.R
import com.jokin.calorias.data.export.DaySummary
import com.jokin.calorias.data.health.Workout
import com.jokin.calorias.domain.BodyMeasures
import com.jokin.calorias.domain.DayBurn
import com.jokin.calorias.domain.DayValue
import com.jokin.calorias.domain.EnergyMath
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.domain.PartChange
import com.jokin.calorias.domain.TargetEta
import com.jokin.calorias.domain.Trends
import com.jokin.calorias.ui.common.today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

enum class Period(@StringRes val labelRes: Int, val days: Int) {
    WEEK(R.string.today_period_week, 7),
    MONTH(R.string.today_period_month, 30),
    QUARTER(R.string.today_period_quarter, 91),
    YEAR(R.string.today_period_year, 365),
}

data class WeekBar(val weekStart: Long, val eatenAvg: Double, val burnedAvg: Double?)

data class Progress(
    val weights: List<DayValue>,
    val weightTrend: List<DayValue>,
    val weeklyRate: Double?,
    val targetWeightKg: Double?,
    /** Null without a target weight, a weight trend or a rate. */
    val targetEta: TargetEta?,
    val weekBars: List<WeekBar>,
    val loggedDays: Int,
    val daysInGoal: Int,
    val avgNutrients: Nutrients,
    val realTdee: Double?,
    val garminTdeeAvg: Double?,
    /** Change of the 7-day mean within the period, and the first day it is measured from; null with under two points. */
    val weightChange: Double?,
    val weightChangeSince: Long?,
    val kmPerWeek: Double,
    val workoutsPerWeek: Double,
    val stepsAvg: Double?,
    val sleepAvgMin: Double?,
    /** First day of the period with watch data; activity averages count from here. Null when there is none. */
    val activitySince: Long?,
    val topFoods: List<Pair<String, Int>>,
    val mealTimes: Map<MealType, String>,
    val waterAvgMl: Double,
    val bodyChanges: List<PartChange>,
)

sealed interface ProgressState {
    data object Loading : ProgressState
    data class Ready(val p: Progress) : ProgressState
    data class Failed(val message: String) : ProgressState
}

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val _period = MutableStateFlow(Period.MONTH)
    val period: StateFlow<Period> = _period.asStateFlow()
    private val _state = MutableStateFlow<ProgressState>(ProgressState.Loading)
    val state: StateFlow<ProgressState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun setPeriod(p: Period) {
        _period.value = p
        load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = ProgressState.Loading
            _state.value = try {
                ProgressState.Ready(compute(_period.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProgressState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun compute(period: Period): Progress {
        val to = today()
        val from = to - period.days + 1
        val zone = ZoneId.systemDefault()
        val summaries: List<DaySummary> = c.export.summaries(from, to)
        val entries = c.log.range(from, to)
        val health = c.health.days(from - 7, to)
        val workouts: List<Workout> = c.health.workouts(from, to)
        val sleep = c.health.sleep(from, to)
        // Weight history reaches back further so the trend line and rate have context at the start.
        val weights = c.health.weights(from - 28, to)
        val goal = c.energy.goal()

        val logged = summaries.filter { it.nutrients.kcal > 0 }
        val weeks = logged.groupBy { (it.epochDay - from) / 7 }
        // Without a goal there is no profile to estimate the resting burn, so only a written total counts.
        val resting = goal?.let { c.energy.restingKcal(it, to) }
        val now = ZonedDateTime.now()
        fun dayBurn(day: Long): Double? {
            val h = health[day] ?: return null
            return if (resting == null) h.totalKcal else c.energy.burnTotal(h, resting, now)
        }
        val weekBars = weeks.map { (w, days) ->
            val burned = days.mapNotNull { dayBurn(it.epochDay) }.takeIf { it.isNotEmpty() }?.average()
            WeekBar(from + w * 7, days.map { it.nutrients.kcal }.average(), burned)
        }

        // A day is "in goal" when intake lands within 10 % of that day's target.
        val daysInGoal = if (goal == null || resting == null) 0 else {
            val correction = c.energy.burnCorrectionKcal()
            val history = health.values.filter { it.hasBurn && it.epochDay != to }
                .map { c.energy.expenditure(it.epochDay, it.burn, resting, now) }
            logged.count { d ->
                val burn = health[d.epochDay]?.burn ?: DayBurn(null, null)
                val exp = c.energy.expenditure(d.epochDay, burn, resting, now)
                val target = EnergyMath.dailyTargetKcal(
                    goal.mode, goal.pace, goal.sex, exp,
                    dayHasWatchData = health[d.epochDay]?.hasBurn == true,
                    dayFinished = d.epochDay < to, last14Days = history, restingKcal = resting,
                    burnCorrectionKcal = correction,
                )
                abs(d.nutrients.kcal - target) <= target * 0.10
            }
        }

        val intakeByDay = logged.associate { it.epochDay to it.nutrients.kcal }
        val garminTotals = (from..to).filter { it != to }.mapNotNull { dayBurn(it) }
        // A watch only writes to Health Connect from when it was allowed to; older weeks are empty, not idle.
        val activitySince = ((from..to).filter { d -> health[d]?.let { it.steps != null || it.hasBurn } == true } +
            workouts.map { it.epochDay }).minOrNull()

        val topFoods = entries.groupingBy { it.food.name }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val mealTimes = entries.groupBy { it.entry.meal }.mapValues { (_, list) ->
            val minutes = list.map { Instant.ofEpochMilli(it.entry.loggedAt).atZone(zone).toLocalTime().let { t -> t.hour * 60 + t.minute } }.average().toInt()
            "%02d:%02d".format(minutes / 60, minutes % 60)
        }

        val trend = Trends.movingAverage7(weights)
        val rate = Trends.weeklyRate(weights, to)
        val targetWeight = goal?.targetWeightKg
        val eta = if (targetWeight != null && rate != null) trend.lastOrNull()?.let { Trends.targetEta(it.value, targetWeight, rate, to) } else null
        val trendInPeriod = trend.filter { it.epochDay >= from }

        return Progress(
            weights = weights.filter { it.epochDay >= from },
            weightTrend = trendInPeriod,
            weeklyRate = rate,
            targetWeightKg = targetWeight,
            targetEta = eta,
            weekBars = weekBars.sortedBy { it.weekStart },
            loggedDays = logged.size,
            daysInGoal = daysInGoal,
            avgNutrients = if (logged.isEmpty()) Nutrients.ZERO else Nutrients.sum(logged.map { it.nutrients }).dividedBy(logged.size.toDouble()),
            realTdee = Trends.estimatedRealTdee(intakeByDay, weights, to),
            garminTdeeAvg = garminTotals.takeIf { it.isNotEmpty() }?.average(),
            weightChange = trendInPeriod.takeIf { it.size >= 2 }?.let { it.last().value - it.first().value },
            weightChangeSince = trendInPeriod.takeIf { it.size >= 2 }?.first()?.epochDay,
            kmPerWeek = Trends.perWeek(workouts.sumOf { it.distanceM ?: 0.0 } / 1000, from, to, activitySince),
            workoutsPerWeek = Trends.perWeek(workouts.size.toDouble(), from, to, activitySince),
            stepsAvg = (from..to).mapNotNull { health[it]?.steps?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            sleepAvgMin = sleep.map { it.minutes.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            activitySince = activitySince,
            topFoods = topFoods,
            mealTimes = mealTimes,
            waterAvgMl = summaries.filter { it.waterMl > 0 }.map { it.waterMl.toDouble() }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            bodyChanges = BodyMeasures.changes(c.body.values(from, to), from, to),
        )
    }
}
