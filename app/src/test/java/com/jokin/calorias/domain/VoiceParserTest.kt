package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {
    private fun parse(text: String) = VoiceParser.parse(text, glassMl = 250)

    @Test
    fun documentExample() {
        val r = parse("Para desayunar, 200 gramos de plátano y un yogur natural")
        assertEquals(MealType.BREAKFAST, r.meal)
        assertEquals(
            listOf(ParsedItem("platano", 200.0, VoiceUnit.GRAM), ParsedItem("yogur natural", 1.0, VoiceUnit.UNIT)),
            r.items,
        )
    }

    @Test
    fun wordNumbersAndUnits() {
        val r = parse("he comido dos huevos y un vaso de leche")
        assertEquals(listOf(ParsedItem("huevos", 2.0, VoiceUnit.UNIT), ParsedItem("leche", 1.0, VoiceUnit.GLASS)), r.items)
        assertNull(r.meal)
    }

    @Test
    fun waterGoesToWaterNotItems() {
        val r = parse("he bebido dos vasos de agua")
        assertEquals(500, r.waterMl)
        assertEquals(emptyList<ParsedItem>(), r.items)
    }

    @Test
    fun decimalCommaAndLitres() {
        assertEquals(1500, parse("1,5 litros de agua").waterMl)
    }

    @Test
    fun gluedUnitAndHalf() {
        val r = parse("para cenar 150g de salmón, medio aguacate")
        assertEquals(MealType.DINNER, r.meal)
        assertEquals(listOf(ParsedItem("salmon", 150.0, VoiceUnit.GRAM), ParsedItem("aguacate", 0.5, VoiceUnit.UNIT)), r.items)
    }

    @Test
    fun foodWithoutQuantityDefaultsToOneUnit() {
        assertEquals(listOf(ParsedItem("cafe con leche", 1.0, VoiceUnit.UNIT)), parse("un café con leche.").items)
    }

    @Test
    fun aQuarterAndAPair() {
        val r = parse("un cuarto de pizza, un par de galletas")
        assertEquals(listOf(ParsedItem("pizza", 0.25, VoiceUnit.UNIT), ParsedItem("galletas", 2.0, VoiceUnit.UNIT)), r.items)
    }

    @Test
    fun mealKeywords() {
        assertEquals(MealType.LUNCH, parse("para comer lentejas").meal)
        assertEquals(MealType.SNACK, parse("de merienda una manzana").meal)
        assertEquals(listOf(ParsedItem("lentejas", 1.0, VoiceUnit.UNIT)), parse("para comer lentejas").items)
    }

    @Test
    fun emptyTextGivesNothing() {
        val r = parse("   ")
        assertEquals(emptyList<ParsedItem>(), r.items)
        assertEquals(0, r.waterMl)
    }

    @Test
    fun fixedGramsForGenericMeasures() {
        assertEquals(30.0, VoiceUnit.TABLESPOON.fixedGrams(2.0, 250)!!, 0.0)
        assertNull(VoiceUnit.SLICE.fixedGrams(1.0, 250))
    }

    private fun english(text: String) = VoiceParser.parse(text, glassMl = 250, language = VoiceLanguage.ENGLISH)

    @Test
    fun englishDocumentExample() {
        val r = english("For breakfast, 200 grams of banana and a natural yogurt")
        assertEquals(MealType.BREAKFAST, r.meal)
        assertEquals(
            listOf(ParsedItem("banana", 200.0, VoiceUnit.GRAM), ParsedItem("natural yogurt", 1.0, VoiceUnit.UNIT)),
            r.items,
        )
    }

    @Test
    fun englishWordNumbersUnitsAndFiller() {
        val r = english("I had two eggs and a glass of milk")
        assertEquals(listOf(ParsedItem("eggs", 2.0, VoiceUnit.UNIT), ParsedItem("milk", 1.0, VoiceUnit.GLASS)), r.items)
        assertNull(r.meal)
    }

    @Test
    fun englishWaterAfterContraction() {
        val r = english("I've drunk two glasses of water")
        assertEquals(500, r.waterMl)
        assertEquals(emptyList<ParsedItem>(), r.items)
    }

    @Test
    fun englishHalvesQuartersAndCouples() {
        assertEquals(
            listOf(ParsedItem("pizza", 0.5, VoiceUnit.UNIT), ParsedItem("cookies", 2.0, VoiceUnit.UNIT)),
            english("half a pizza, a couple of cookies").items,
        )
        assertEquals(listOf(ParsedItem("pizza", 0.25, VoiceUnit.UNIT)), english("a quarter of a pizza").items)
    }

    @Test
    fun englishGluedUnitAndHalfAn() {
        val r = english("for dinner 150g of salmon, half an avocado")
        assertEquals(MealType.DINNER, r.meal)
        assertEquals(listOf(ParsedItem("salmon", 150.0, VoiceUnit.GRAM), ParsedItem("avocado", 0.5, VoiceUnit.UNIT)), r.items)
    }

    @Test
    fun englishHundredsSlicesAndPlus() {
        assertEquals(listOf(ParsedItem("rice", 200.0, VoiceUnit.GRAM)), english("two hundred grams of rice").items)
        assertEquals(
            listOf(ParsedItem("bread", 1.0, VoiceUnit.SLICE), ParsedItem("olive oil", 1.0, VoiceUnit.TABLESPOON)),
            english("a slice of bread plus a tablespoon of olive oil").items,
        )
    }

    @Test
    fun englishMealKeywords() {
        assertEquals(MealType.LUNCH, english("lentils for lunch").meal)
        val snack = english("as a snack an apple")
        assertEquals(MealType.SNACK, snack.meal)
        assertEquals(listOf(ParsedItem("apple", 1.0, VoiceUnit.UNIT)), snack.items)
    }

    @Test
    fun languageIsExplicit() {
        assertEquals(VoiceLanguage.SPANISH, VoiceLanguage.of("es"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.of("en"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguage.of("fr"))
        // Spanish words mean nothing to the English parser, and the other way round.
        assertEquals(listOf(ParsedItem("gramos de arroz", 200.0, VoiceUnit.UNIT)), english("200 gramos de arroz").items)
        assertEquals(listOf(ParsedItem("grams of rice", 200.0, VoiceUnit.UNIT)), parse("200 grams of rice").items)
    }
}
