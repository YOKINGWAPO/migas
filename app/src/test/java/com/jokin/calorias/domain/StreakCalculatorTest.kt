package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    // Monday 2026-10-12; the ISO week runs Monday to Sunday.
    private val monday = LocalDate.of(2026, 10, 12).toEpochDay()

    @Test
    fun noLogsMeansNoStreak() {
        val r = StreakCalculator.compute(emptySet(), monday)
        assertEquals(0, r.current)
        assertEquals(0, r.best)
    }

    @Test
    fun consecutiveDaysCount() {
        val days = (monday - 4..monday).toSet()
        assertEquals(5, StreakCalculator.compute(days, monday).current)
    }

    @Test
    fun todayUnloggedDoesNotBreakStreak() {
        val days = (monday - 3..monday - 1).toSet()
        val r = StreakCalculator.compute(days, monday)
        assertEquals(3, r.current)
        assertFalse(r.loggedToday)
    }

    @Test
    fun singleGapIsProtectedOncePerWeek() {
        val wed = monday + 2
        val days = setOf(monday, wed)
        val r = StreakCalculator.compute(days, wed)
        assertEquals(2, r.current)
        assertEquals(setOf(monday + 1), r.protectedDays)
    }

    @Test
    fun secondGapSameWeekBreaksStreak() {
        val days = setOf(monday, monday + 2, monday + 4)
        assertEquals(1, StreakCalculator.compute(days, monday + 4).current)
    }

    @Test
    fun gapsInDifferentWeeksAreBothProtected() {
        // Gap on Saturday of one week and on Tuesday of the next.
        val sat = monday + 5
        val days = setOf(monday + 4, monday + 6, monday + 7, monday + 9)
        val r = StreakCalculator.compute(days, monday + 9)
        assertEquals(4, r.current)
        assertEquals(setOf(sat, monday + 8), r.protectedDays)
    }

    @Test
    fun twoMissedDaysInARowBreakStreak() {
        val days = setOf(monday, monday + 3)
        assertEquals(1, StreakCalculator.compute(days, monday + 3).current)
    }

    @Test
    fun yesterdayGapStaysOpenUntilTodayEnds() {
        val days = setOf(monday, monday + 1)
        val r = StreakCalculator.compute(days, monday + 3)
        assertEquals(2, r.current)
        assertTrue(monday + 2 in r.protectedDays)
    }

    @Test
    fun bestKeepsLongestHistoricRun() {
        val days = (monday - 20..monday - 10).toSet() + setOf(monday)
        val r = StreakCalculator.compute(days, monday)
        assertEquals(11, r.best)
        assertEquals(1, r.current)
    }

    @Test
    fun petStageEvolvesAtMilestones() {
        assertEquals(PetStage.EGG, PetStage.forStreak(6))
        assertEquals(PetStage.CHICK, PetStage.forStreak(7))
        assertEquals(PetStage.BIRD, PetStage.forStreak(30))
        assertEquals(PetStage.PHOENIX, PetStage.forStreak(100))
    }

    @Test
    fun petMood() {
        assertEquals(PetMood.HAPPY, StreakCalculator.petMood(monday, monday, 20))
        assertEquals(PetMood.HAPPY, StreakCalculator.petMood(monday - 1, monday, 9))
        assertEquals(PetMood.HUNGRY, StreakCalculator.petMood(monday - 1, monday, 14))
        assertEquals(PetMood.SLEEPING, StreakCalculator.petMood(monday - 2, monday, 14))
        assertEquals(PetMood.SLEEPING, StreakCalculator.petMood(null, monday, 14))
    }
}
