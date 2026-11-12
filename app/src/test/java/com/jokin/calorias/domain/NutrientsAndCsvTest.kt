package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsAndCsvTest {
    @Test
    fun scaleAndSum() {
        val banana = Nutrients(89.0, 1.1, 22.8, 0.3, mapOf(ExtraNutrient.FIBER to 2.6))
        val portion = banana.scaledTo(200.0)
        assertEquals(178.0, portion.kcal, 1e-9)
        assertEquals(5.2, portion.extras[ExtraNutrient.FIBER]!!, 1e-9)
        val total = portion + Nutrients(50.0, 1.0, 1.0, 1.0, mapOf(ExtraNutrient.SALT to 0.1))
        assertEquals(228.0, total.kcal, 1e-9)
        assertEquals(0.1, total.extras[ExtraNutrient.SALT]!!, 1e-9)
    }

    @Test
    fun recipePer100g() {
        val rice = Nutrients(350.0, 7.0, 77.0, 1.0)
        val oil = Nutrients(900.0, 0.0, 0.0, 100.0)
        val mix = Nutrients.per100gOfMix(listOf(rice to 100.0, oil to 10.0))
        assertEquals((350.0 + 90.0) / 110.0 * 100, mix.kcal, 1e-9)
        assertEquals((1.0 + 10.0) / 110.0 * 100, mix.fat, 1e-9)
    }

    @Test
    fun emptyMixIsZero() {
        assertEquals(Nutrients.ZERO, Nutrients.per100gOfMix(emptyList()))
    }

    @Test
    fun csvEscapesAndFormats() {
        val csv = Csv.build(listOf("a", "b"), listOf(listOf("x;y", 1.5), listOf("=SUM(A1)", null), listOf("say \"hi\"", -0.4)))
        val lines = csv.removePrefix("﻿").lines()
        assertEquals("a;b", lines[0])
        assertEquals("\"x;y\";1,50", lines[1])
        assertEquals("'=SUM(A1);", lines[2])
        assertEquals("\"say \"\"hi\"\"\";-0,40", lines[3])
    }

    @Test
    fun englishCsvUsesCommasAndDecimalPoints() {
        val csv = Csv.build(listOf("a", "b"), listOf(listOf("x,y", 1.5), listOf("+1", 1234.5), listOf("x;y", null)), english = true)
        val lines = csv.removePrefix("\uFEFF").lines()
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals("a,b", lines[0])
        assertEquals("\"x,y\",1.50", lines[1])
        assertEquals("'+1,1234.50", lines[2])
        assertEquals("x;y,", lines[3])
    }

    @Test
    fun guessMealByHour() {
        assertEquals(MealType.BREAKFAST, MealType.guessFor(java.time.LocalTime.of(8, 0)))
        assertEquals(MealType.LUNCH, MealType.guessFor(java.time.LocalTime.of(14, 30)))
        assertEquals(MealType.DINNER, MealType.guessFor(java.time.LocalTime.of(21, 15)))
        assertEquals(MealType.SNACK, MealType.guessFor(java.time.LocalTime.of(18, 0)))
    }
}
