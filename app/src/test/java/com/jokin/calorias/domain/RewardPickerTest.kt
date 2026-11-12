package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardPickerTest {
    private val day = 20_400L

    @Test
    fun nothingBeforeTheFirstTier() {
        assertTrue(RewardPicker.unlocked(6).isEmpty())
        assertNull(RewardPicker.forDay(6, day))
    }

    @Test
    fun tiersUnlockAtSevenThirtyAndHundredDays() {
        assertEquals(setOf(PetStage.CHICK), RewardPicker.unlocked(7).map { it.tier }.toSet())
        assertEquals(setOf(PetStage.CHICK), RewardPicker.unlocked(29).map { it.tier }.toSet())
        assertEquals(setOf(PetStage.CHICK, PetStage.BIRD), RewardPicker.unlocked(30).map { it.tier }.toSet())
        assertEquals(StreakReward.entries.toSet(), RewardPicker.unlocked(100).toSet())
    }

    @Test
    fun sameDayGivesTheSameReward() {
        assertEquals(RewardPicker.forDay(120, day), RewardPicker.forDay(120, day))
    }

    @Test
    fun pickIsAlwaysUnlocked() {
        for (streak in listOf(7, 12, 30, 64, 100, 365)) {
            val pool = RewardPicker.unlocked(streak)
            for (d in day until day + 60) assertTrue(RewardPicker.forDay(streak, d)!! in pool)
        }
    }

    @Test
    fun rewardsChangeAcrossDaysAndTaps() {
        val byDay = (day until day + 30).map { RewardPicker.forDay(100, it) }.toSet()
        val byTap = (0 until 30).map { RewardPicker.forDay(100, day, shuffle = it) }.toSet()
        assertTrue("only ${byDay.size} distinct rewards in a month", byDay.size >= 8)
        assertTrue("only ${byTap.size} distinct rewards in 30 taps", byTap.size >= 8)
    }
}
