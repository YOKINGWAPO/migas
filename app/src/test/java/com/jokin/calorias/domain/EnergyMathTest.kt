package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EnergyMathTest {
    private val man = Profile(weightKg = 80.0, heightCm = 180.0, ageYears = 30, sex = Sex.MALE)

    @Test
    fun mifflinMale() {
        // 800 + 1125 - 150 + 5
        assertEquals(1780.0, EnergyMath.mifflinStJeor(man), 0.001)
    }

    @Test
    fun mifflinFemaleUsesMinus161() {
        assertEquals(1614.0, EnergyMath.mifflinStJeor(man.copy(sex = Sex.FEMALE)), 0.001)
    }

    @Test
    fun restingPrefersGarminFigure() {
        assertEquals(2115.0, EnergyMath.restingKcal(man, 2115), 0.001)
        assertEquals(1780.0, EnergyMath.restingKcal(man, null), 0.001)
    }

    @Test
    fun finishedDayMatchesGarminSplit() {
        // Real Garmin day: 2597 total = 2114 resting + 483 active; Health Connect's active record said only 342.
        val e = EnergyMath.expenditure(DayBurn(2597.0, 342.0), restingKcal = 2115.0, elapsedFraction = 1.0)
        assertEquals(2115.0, e.basal, 0.001)
        assertEquals(482.0, e.active, 0.001)
        assertEquals(2597.0, e.total, 0.001)
    }

    @Test
    fun todayCountsRestingBurnSoFarAndProjectsFullDay() {
        // 21:04 on a real day: Garmin total 2397 = 1854 resting so far + 543 active.
        val fraction = (21 * 60 + 4) / 1440.0
        val e = EnergyMath.expenditure(DayBurn(2397.0, 311.0), restingKcal = 2115.0, elapsedFraction = fraction)
        assertEquals(541.0, e.active, 1.0)
        assertEquals(2115.0 + e.active, e.total, 0.001)
    }

    @Test
    fun staleTotalNeverMakesActiveNegative() {
        val e = EnergyMath.expenditure(DayBurn(900.0, null), restingKcal = 2115.0, elapsedFraction = 0.75)
        assertEquals(0.0, e.active, 0.0)
    }

    @Test
    fun loggedWorkoutIsAFloorWhenTheTotalDoesNotReachTheRestingSoFar() {
        // Real case at 20:38: Garmin total 1454, resting estimated with the profile (1904) is 1637 so far,
        // yet a walk wrote 350 active kcal. Active must not drop to 0.
        val fraction = (20 * 60 + 38) / 1440.0
        val e = EnergyMath.expenditure(DayBurn(1454.0, 350.0), restingKcal = 1904.0, elapsedFraction = fraction)
        assertEquals(350.0, e.active, 0.001)
        assertEquals(1904.0 + 350.0, e.total, 0.001)
    }

    @Test
    fun withoutTotalFallsBackToActiveRecord() {
        assertEquals(300.0, EnergyMath.expenditure(DayBurn(null, 300.0), 2115.0, 0.5).active, 0.001)
        assertEquals(0.0, EnergyMath.expenditure(DayBurn(null, null), 2115.0, 0.5).active, 0.0)
    }

    @Test
    fun elapsedFractionOfToday() {
        val zone = ZoneId.of("Europe/Madrid")
        val day = LocalDate.of(2026, 10, 14)
        assertEquals(0.5, EnergyMath.elapsedFraction(day, day.atTime(12, 0).atZone(zone)), 1e-9)
        assertEquals(1.0, EnergyMath.elapsedFraction(day, day.plusDays(1).atTime(8, 0).atZone(zone)), 0.0)
        assertEquals(0.0, EnergyMath.elapsedFraction(day, day.minusDays(1).atTime(23, 0).atZone(zone)), 0.0)
    }

    @Test
    fun elapsedFractionUsesRealLengthOnDstDay() {
        // 25 October 2026 has 25 hours in Madrid; at 12:00 local, 13 of them have passed.
        val zone = ZoneId.of("Europe/Madrid")
        val day = LocalDate.of(2026, 10, 25)
        assertEquals(13.0 / 25.0, EnergyMath.elapsedFraction(day, day.atTime(12, 0).atZone(zone)), 1e-9)
    }

    private fun target(
        mode: TargetMode = TargetMode.DYNAMIC,
        pace: GoalPace = GoalPace.LOSE,
        sex: Sex = Sex.MALE,
        day: Expenditure,
        watch: Boolean = true,
        finished: Boolean = true,
        history: List<Expenditure> = emptyList(),
        resting: Double = 1800.0,
        correction: Int = 0,
    ) = EnergyMath.dailyTargetKcal(mode, pace, sex, day, watch, finished, history, resting, correction)

    @Test
    fun burnCorrectionShiftsTheGarminBase() {
        // A typical day of 2800 keeps the 25 % deficit cap out of the way.
        val typical = listOf(Expenditure(1800.0, 1000.0))
        assertEquals(2500 - 250 - 550, target(day = Expenditure(1800.0, 700.0), history = typical, correction = -250))
        assertEquals(2800 - 250 - 550, target(day = Expenditure(1800.0, 0.0), watch = false, history = typical, correction = -250))
        assertEquals(2800 + 150 + 275, target(mode = TargetMode.FIXED, pace = GoalPace.GAIN, day = Expenditure(0.0, 0.0), history = typical, correction = 150))
    }

    @Test
    fun burnCorrectionLeavesTheSedentaryEstimateAlone() {
        assertEquals(2160, target(mode = TargetMode.FIXED, pace = GoalPace.MAINTAIN, day = Expenditure(0.0, 0.0), correction = -300))
    }

    @Test
    fun paceIsRealKilosPerWeek() {
        assertEquals(-550, GoalPace.LOSE.dailyKcal)
        assertEquals(-275, GoalPace.LOSE_GENTLE.dailyKcal)
        assertEquals(-825, GoalPace.LOSE_FAST.dailyKcal)
        assertEquals(0, GoalPace.MAINTAIN.dailyKcal)
        assertEquals(275, GoalPace.GAIN.dailyKcal)
    }

    @Test
    fun dynamicTargetUsesTheRealBurnOfAFinishedDay() {
        val typical = listOf(Expenditure(1800.0, 600.0))
        assertEquals(2500 - 550, target(day = Expenditure(1800.0, 700.0), history = typical))
        assertEquals(2200 - 550, target(day = Expenditure(1800.0, 400.0), history = typical))
    }

    @Test
    fun todayStartsAtTheTypicalDayAndRisesWithMoreActivity() {
        val typical = listOf(Expenditure(1800.0, 400.0), Expenditure(1800.0, 600.0))
        assertEquals(2300 - 550, target(day = Expenditure(1800.0, 50.0), finished = false, history = typical))
        assertEquals(2900 - 550, target(day = Expenditure(1800.0, 1100.0), finished = false, history = typical))
    }

    /** The reported bug: resting 2100, no Garmin data, so the target sat at 2100 − 500. */
    @Test
    fun withoutWatchDataTheTargetIsNotJustRestingMinusDeficit() {
        val t = target(day = Expenditure(2100.0, 0.0), watch = false, finished = false, resting = 2100.0)
        assertEquals(2100 * 1.2 - 550, t.toDouble(), 0.5)
    }

    @Test
    fun fixedTargetAveragesHistory() {
        val history = listOf(Expenditure(1800.0, 400.0), Expenditure(1800.0, 800.0))
        assertEquals(2400 + 275, target(mode = TargetMode.FIXED, pace = GoalPace.GAIN, day = Expenditure(1800.0, 0.0), history = history))
    }

    @Test
    fun fixedTargetWithoutHistoryUsesSedentaryEstimate() {
        assertEquals(2160, target(mode = TargetMode.FIXED, pace = GoalPace.MAINTAIN, day = Expenditure(0.0, 0.0)))
    }

    @Test
    fun deficitNeverExceedsAQuarterOfTheBurn() {
        assertEquals(3200 - 800, target(pace = GoalPace.LOSE_FAST, day = Expenditure(2000.0, 1200.0)))
        assertEquals(2100, target(pace = GoalPace.LOSE_FAST, sex = Sex.FEMALE, day = Expenditure(1800.0, 1000.0)))
    }

    @Test
    fun targetNeverBelowTheFloorForEachSex() {
        assertEquals(1500, target(day = Expenditure(1300.0, 0.0), resting = 1300.0))
        assertEquals(1200, target(sex = Sex.FEMALE, day = Expenditure(1300.0, 0.0), resting = 1300.0))
    }

    @Test
    fun referenceWeightIsTargetThenHealthyBmiCeiling() {
        assertEquals(75.0, EnergyMath.referenceWeightKg(94.0, 180.0, targetWeightKg = 75.0), 0.0)
        assertEquals(81.0, EnergyMath.referenceWeightKg(94.0, 180.0, targetWeightKg = null), 1e-9)
        assertEquals(70.0, EnergyMath.referenceWeightKg(70.0, 180.0, targetWeightKg = null), 0.0)
    }

    /** The reported case: 1600 kcal used to leave 62 g of carbs with 94 kg × 1.8 protein and × 0.8 fat. */
    @Test
    fun macrosForTheReportedCaseLeaveRealCarbs() {
        val m = EnergyMath.macroTargets(1600, referenceWeightKg = 81.0, proteinPerKg = 1.6, fatPercent = 25)
        assertEquals(129.6, m.proteinG, 0.001)
        assertEquals(48.6, m.fatG, 0.001)
        assertEquals((1600 - 129.6 * 4 - 48.6 * 9) / 4, m.carbsG, 0.001)
        assertTrue(m.carbsG > EnergyMath.LOW_CARBS_G)
    }

    @Test
    fun fatFollowsTheShareOfEnergyAboveItsFloor() {
        val m = EnergyMath.macroTargets(2800, referenceWeightKg = 70.0, proteinPerKg = 1.6, fatPercent = 30)
        assertEquals(2800 * 0.30 / 9, m.fatG, 0.001)
        assertEquals((2800 - 112 * 4 - 2800 * 0.30) / 4, m.carbsG, 0.001)
    }

    @Test
    fun carbsNeverNegative() {
        val m = EnergyMath.macroTargets(1200, referenceWeightKg = 120.0, proteinPerKg = 2.2, fatPercent = 35)
        assertEquals(0.0, m.carbsG, 0.0)
    }
}
