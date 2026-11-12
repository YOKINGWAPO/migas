package com.jokin.calorias.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

data class StreakResult(
    val current: Int,
    val best: Int,
    val loggedToday: Boolean,
    /** Missed days covered by the weekly protector within the current streak. */
    val protectedDays: Set<Long>,
)

/** Streak tiers that unlock mascot rewards (see StreakReward). The names come from the old emoji pet. */
enum class PetStage(val minStreak: Int) {
    EGG(0),
    CHICK(7),
    BIRD(30),
    PHOENIX(100);

    companion object {
        fun forStreak(streak: Int): PetStage = entries.last { streak >= it.minStreak }
    }
}

enum class PetMood { HAPPY, HUNGRY, SLEEPING }

object StreakCalculator {
    /**
     * A day counts when it has at least one entry. One missed day per ISO week is forgiven,
     * as long as the day after it is logged. Today never breaks the streak: it is still open,
     * and yesterday's gap stays forgivable until today ends.
     */
    fun compute(loggedDays: Set<Long>, today: Long): StreakResult {
        val first = loggedDays.minOrNull()
        if (first == null || first > today) return StreakResult(0, 0, false, emptySet())

        var run = 0
        var best = 0
        val protectedInRun = mutableSetOf<Long>()
        val usedWeeks = mutableSetOf<Long>()
        val loggedToday = today in loggedDays

        for (day in first..today) {
            if (day in loggedDays) {
                run++
                best = maxOf(best, run)
                continue
            }
            if (day == today) break
            val nextIsLoggedOrOpen = (day + 1) in loggedDays || (day + 1 == today)
            val week = isoWeekKey(day)
            if (run > 0 && nextIsLoggedOrOpen && week !in usedWeeks) {
                usedWeeks += week
                protectedInRun += day
            } else {
                run = 0
                protectedInRun.clear()
            }
        }
        return StreakResult(run, best, loggedToday, protectedInRun.toSet())
    }

    fun petMood(lastLoggedDay: Long?, today: Long, hourOfDay: Int): PetMood {
        if (lastLoggedDay == null || today - lastLoggedDay >= 2) return PetMood.SLEEPING
        if (lastLoggedDay < today && hourOfDay >= 11) return PetMood.HUNGRY
        return PetMood.HAPPY
    }

    private fun isoWeekKey(epochDay: Long): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.get(IsoFields.WEEK_BASED_YEAR) * 100L + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }
}
