package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboDetectorTest {
    private val today = 20_000L
    private val bread = 1L
    private val cheese = 2L
    private val ham = 3L
    private val coffee = 4L

    private fun meal(day: Long, vararg ids: Long) = MealOccurrence(day, MealType.BREAKFAST, ids.toSet())

    @Test
    fun suggestsGroupSeenThreeTimes() {
        val meals = listOf(meal(today, bread, cheese, ham), meal(today - 1, bread, cheese, ham), meal(today - 5, bread, cheese, ham))
        val s = ComboDetector.suggest(meals, today, emptySet(), emptySet())
        assertEquals(listOf(ComboSuggestion(setOf(bread, cheese, ham), 3)), s)
    }

    @Test
    fun twoTimesIsNotEnough() {
        val meals = listOf(meal(today, bread, cheese), meal(today - 1, bread, cheese))
        assertTrue(ComboDetector.suggest(meals, today, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun keepsSubsetWhenItIsMoreFrequent() {
        val meals = listOf(
            meal(today, bread, cheese, ham), meal(today - 1, bread, cheese, ham), meal(today - 2, bread, cheese, ham),
            meal(today - 3, bread, cheese, coffee),
        )
        val s = ComboDetector.suggest(meals, today, emptySet(), emptySet())
        assertEquals(setOf(bread, cheese), s.first().foodIds)
        assertEquals(4, s.first().occurrences)
        assertTrue(s.any { it.foodIds == setOf(bread, cheese, ham) })
    }

    @Test
    fun ignoresMealsOutside30Days() {
        val meals = listOf(meal(today, bread, cheese), meal(today - 29, bread, cheese), meal(today - 30, bread, cheese))
        assertTrue(ComboDetector.suggest(meals, today, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun skipsExistingAndDismissed() {
        val meals = (0L..2L).map { meal(today - it, bread, cheese) } + (0L..2L).map { meal(today - it, ham, coffee) }
        val s = ComboDetector.suggest(meals, today, existingCombos = setOf(setOf(bread, cheese)), dismissedSignatures = setOf("3-4"))
        assertTrue(s.isEmpty())
    }

    @Test
    fun signatureIsOrderIndependent() {
        assertEquals("1-2-3", ComboSuggestion(setOf(3L, 1L, 2L), 3).signature)
    }
}
