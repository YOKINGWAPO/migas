package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendsTest {
    @Test
    fun movingAverageUsesPrevious7Days() {
        val pts = listOf(DayValue(1, 80.0), DayValue(2, 82.0), DayValue(9, 78.0))
        val avg = Trends.movingAverage7(pts)
        assertEquals(80.0, avg[0].value, 1e-9)
        assertEquals(81.0, avg[1].value, 1e-9)
        assertEquals(78.0, avg[2].value, 1e-9) // day 9 window is days 3..9
    }

    @Test
    fun weeklyRateOfSteadyLoss() {
        // 0.1 kg/day lost => -0.7 kg/week.
        val pts = (0L..27L).map { DayValue(100 + it, 90.0 - 0.1 * it) }
        assertEquals(-0.7, Trends.weeklyRate(pts, today = 127)!!, 1e-6)
    }

    @Test
    fun weeklyRateNeedsTwoPoints() {
        assertNull(Trends.weeklyRate(listOf(DayValue(10, 80.0)), today = 10))
    }

    @Test
    fun realTdeeFromBalance() {
        // Ate 2000/day for 28 days and lost 1 kg in 27 days: 7700/27 kcal/day came from body stores.
        val weights = (0L..27L).map { DayValue(it, 80.0 - it / 27.0) }
        val intake = (0L..27L).associateWith { 2000.0 }
        assertEquals(2000 + 7700 / 27.0, Trends.estimatedRealTdee(intake, weights, today = 27)!!, 1e-6)
    }

    @Test
    fun realTdeeNeedsThreeWeeks() {
        val weights = (0L..13L).map { DayValue(it, 80.0) }
        val intake = (0L..13L).associateWith { 2000.0 }
        assertNull(Trends.estimatedRealTdee(intake, weights, today = 13))
    }

    @Test
    fun realTdeeNeedsMostDaysLogged() {
        val weights = (0L..27L).map { DayValue(it, 80.0) }
        val intake = (0L..10L).associateWith { 2000.0 }
        assertNull(Trends.estimatedRealTdee(intake, weights, today = 27))
    }

    // 28 finished days (0..27) before "today" = 28: ate 2000/day and lost 1 kg, so the real burn is 2000 + 7700/27 ≈ 2285.
    private val losingWeights = (0L..27L).map { DayValue(it, 80.0 - it / 27.0) }
    private val steadyIntake = (0L..27L).associateWith { 2000.0 }

    @Test
    fun burnReviewProposesTheGapWithGarmin() {
        val garmin = (0L..27L).associateWith { 2600.0 }
        val review = Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = 0)!!
        assertEquals(2000 + 7700 / 27.0, review.realTdee, 1e-6)
        assertEquals(2600.0, review.garminAvg, 1e-9)
        assertEquals(-310, review.correctionKcal)
    }

    @Test
    fun burnReviewIgnoresTodaysPartialIntakeAndBurn() {
        val garmin = (0L..27L).associateWith { 2600.0 } + (28L to 400.0)
        val intake = steadyIntake + (28L to 300.0)
        val weights = losingWeights + DayValue(28, 80.0 - 28 / 27.0)
        assertEquals(-310, Trends.burnReview(intake, weights, garmin, today = 28, currentCorrection = 0)!!.correctionKcal)
    }

    @Test
    fun burnReviewIsAbsoluteSoAcceptingAgainChangesNothing() {
        val garmin = (0L..27L).associateWith { 2600.0 }
        assertNull(Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = -310))
        assertNull(Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = -250))
        assertEquals(-310, Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = -200)!!.correctionKcal)
    }

    @Test
    fun burnReviewIsCapped() {
        val garmin = (0L..27L).associateWith { 3500.0 }
        assertEquals(-Trends.MAX_BURN_CORRECTION, Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = 0)!!.correctionKcal)
    }

    @Test
    fun burnReviewNeedsGarminOnMostDays() {
        val garmin = (0L..10L).associateWith { 2600.0 }
        assertNull(Trends.burnReview(steadyIntake, losingWeights, garmin, today = 28, currentCorrection = 0))
    }

    @Test
    fun burnReviewNeedsTheRealBurnEstimate() {
        val garmin = (0L..27L).associateWith { 2600.0 }
        assertNull(Trends.burnReview((0L..5L).associateWith { 2000.0 }, losingWeights, garmin, today = 28, currentCorrection = 0))
    }

    @Test
    fun etaFromTheCurrentRate() {
        val eta = Trends.targetEta(currentKg = 90.0, targetKg = 85.0, weeklyRate = -0.5, today = 100)
        assertEquals(TargetEta.On(170, 10.0), eta)
    }

    @Test
    fun etaWhenGainingTowardsTheTarget() {
        assertEquals(TargetEta.On(114, 2.0), Trends.targetEta(currentKg = 70.0, targetKg = 71.0, weeklyRate = 0.5, today = 100))
    }

    @Test
    fun noEtaWhenMovingAwayOrFlat() {
        assertEquals(TargetEta.NotApproaching, Trends.targetEta(90.0, 85.0, weeklyRate = 0.3, today = 100))
        assertEquals(TargetEta.NotApproaching, Trends.targetEta(90.0, 85.0, weeklyRate = -0.01, today = 100))
    }

    @Test
    fun reachedWithinScaleNoise() {
        assertEquals(TargetEta.Reached, Trends.targetEta(85.2, 85.0, weeklyRate = -0.5, today = 100))
        assertEquals(TargetEta.Reached, Trends.targetEta(84.8, 85.0, weeklyRate = 0.0, today = 100))
    }

    @Test
    fun perWeekCountsOnlyTheWeeksWithData() {
        // A year selected, watch data for the last 28 days only: 140 km is 35 km a week, not 2,7.
        assertEquals(35.0, Trends.perWeek(140.0, from = 1, to = 365, firstDataDay = 338), 0.001)
    }

    @Test
    fun perWeekUsesThePeriodWhenDataStartsBeforeIt() {
        assertEquals(10.0, Trends.perWeek(40.0, from = 100, to = 127, firstDataDay = 50), 0.001)
        assertEquals(10.0, Trends.perWeek(40.0, from = 100, to = 127, firstDataDay = null), 0.001)
    }

    @Test
    fun perWeekNeverExtrapolatesLessThanAWeek() {
        // Two days of data with 6 km must not become 21 km a week.
        assertEquals(6.0, Trends.perWeek(6.0, from = 1, to = 30, firstDataDay = 29), 0.001)
    }
}
