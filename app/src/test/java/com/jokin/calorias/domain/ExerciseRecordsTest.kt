package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseRecordsTest {
    @Test
    fun noHistoryNoRecord() {
        assertNull(ExerciseRecords.summarize(emptyList(), 100))
    }

    @Test
    fun lastIsTheLatestSetOfThePreviousSessionAndIgnoresToday() {
        val history = listOf(
            LoggedSet(90, 3, 10, 40.0),
            LoggedSet(95, 3, 10, 42.5),
            LoggedSet(95, 2, 8, 45.0),
            LoggedSet(100, 3, 10, 47.5),
        )
        val r = ExerciseRecords.summarize(history, day = 100)!!
        assertEquals(LoggedSet(95, 2, 8, 45.0), r.last)
        assertEquals(LoggedSet(100, 3, 10, 47.5), r.best)
    }

    @Test
    fun bestTiesOnWeightGoToMoreReps() {
        val r = ExerciseRecords.summarize(listOf(LoggedSet(1, 3, 8, 40.0), LoggedSet(2, 3, 10, 40.0)), day = 3)!!
        assertEquals(LoggedSet(2, 3, 10, 40.0), r.best)
    }

    @Test
    fun bodyweightBestIsMostReps() {
        val r = ExerciseRecords.summarize(listOf(LoggedSet(1, 4, 15, null), LoggedSet(2, 3, 20, null), LoggedSet(3, 5, 12, null)), day = 3)!!
        assertEquals(LoggedSet(2, 3, 20, null), r.best)
        assertEquals(LoggedSet(2, 3, 20, null), r.last)
    }

    @Test
    fun firstSessionHasNoLast() {
        val r = ExerciseRecords.summarize(listOf(LoggedSet(5, 4, 8, null)), day = 5)!!
        assertNull(r.last)
    }
}
