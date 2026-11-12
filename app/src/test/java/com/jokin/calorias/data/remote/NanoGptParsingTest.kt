package com.jokin.calorias.data.remote

import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.ParsedExercise
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NanoGptParsingTest {
    private val riceReply = """{"nombre": "Arroz redondo tradicional", "marca": "brillante", "kcal": 156, "proteinas": 2.7,
        "hidratos": 31, "grasas": 2.3, "azucares": 0.1, "fibra": 0.6, "saturadas": 0.3, "sal": 0.30, "racion_g": 125}"""

    @Test
    fun readsCleanLabelReply() {
        val food = foodFromJson(parseModelJson(riceReply), fallbackName = "Producto")
        assertEquals("Arroz redondo tradicional", food.name)
        assertEquals("brillante", food.brand)
        assertEquals(156.0, food.nutrients.kcal, 0.0)
        assertEquals(2.7, food.nutrients.protein, 0.0)
        assertEquals(31.0, food.nutrients.carbs, 0.0)
        assertEquals(2.3, food.nutrients.fat, 0.0)
        assertEquals(0.3, food.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.0)
        assertEquals(125.0, food.servingGrams!!, 0.0)
        assertEquals(emptySet<Macro>(), food.unreadMacros)
    }

    /** deepseek-v4.1-flash replies exactly this to the connection test. */
    @Test
    fun dropsTokensLeakedIntoValues() {
        assertEquals(JsonPrimitive(true), parseModelJson("""{"ok":<|TOOL_CALL|>true}""")["ok"])
        val food = foodFromJson(parseModelJson("""{"kcal": 156, "grasas":<|TOOL_CALL|>2.3}"""), fallbackName = "Producto")
        assertEquals(2.3, food.nutrients.fat, 0.0)
    }

    @Test
    fun stripsMarkdownFences() {
        assertEquals(JsonPrimitive(156), parseModelJson("```json\n{\"kcal\": 156}\n```")["kcal"])
    }

    @Test
    fun acceptsNumbersWrittenAsLabelText() {
        assertEquals(2.3, nutrientValue(JsonPrimitive("2,3"))!!, 0.0)
        assertEquals(2.3, nutrientValue(JsonPrimitive("2.3 g"))!!, 0.0)
        assertEquals(156.0, nutrientValue(JsonPrimitive("156 kcal"))!!, 0.0)
        assertEquals(0.0, nutrientValue(JsonPrimitive("<0,5 g"))!!, 0.0)
        assertEquals(0.0, nutrientValue(JsonPrimitive(0))!!, 0.0)
    }

    @Test
    fun rejectsValuesThatAreNotNumbers() {
        assertNull(nutrientValue(null))
        assertNull(nutrientValue(JsonNull))
        assertNull(nutrientValue(JsonPrimitive("n/d")))
        assertNull(nutrientValue(JsonPrimitive("")))
        assertNull(nutrientValue(JsonPrimitive(-1)))
    }

    @Test
    fun marksMissingMacrosAsUnread() {
        val food = foodFromJson(parseModelJson("""{"kcal": 156, "proteinas": 2.7, "hidratos": null}"""), fallbackName = "Producto")
        assertEquals(setOf(Macro.CARBS, Macro.FAT), food.unreadMacros)
        assertEquals(2.7, food.nutrients.protein, 0.0)
        assertEquals("Producto", food.name)
    }

    @Test
    fun readsExercisesAndDropsInvalidOnes() {
        val reply = """{"ejercicios": [
            {"nombre": "flexiones", "series": 4, "repeticiones": 15, "peso_kg": null},
            {"nombre": "Press banca", "series": "3", "repeticiones": 10, "peso_kg": "42,5 kg"},
            {"nombre": "Dominadas", "repeticiones": 8},
            {"nombre": "Sentadillas", "series": 0, "repeticiones": 10},
            {"nombre": "", "series": 3, "repeticiones": 10},
            {"nombre": "Plancha", "series": 3}
        ]}"""
        assertEquals(
            listOf(
                ParsedExercise("Flexiones", 4, 15, null),
                ParsedExercise("Press banca", 3, 10, 42.5),
                ParsedExercise("Dominadas", 1, 8, null),
            ),
            exercisesFromJson(parseModelJson(reply)),
        )
        assertEquals(emptyList<ParsedExercise>(), exercisesFromJson(parseModelJson("""{"otra": 1}""")))
    }

    @Test(expected = AiException::class)
    fun failsWithoutCalories() {
        foodFromJson(parseModelJson("""{"nombre": "Arroz", "kcal": null}"""), fallbackName = "Producto")
    }

    /** Real deepseek-v4.1-flash reply to a Nutrition5k dish photo (307 kcal measured). */
    private val mealReply = """{"componentes": [{"nombre": "carne de ternera a la plancha", "gramos": 90, "kcal_100g": 250, "proteinas_100g": 26,
        "hidratos_100g": 0, "grasas_100g": 16}, {"nombre": "puré de patata con mantequilla", "gramos": 60, "kcal_100g": 110, "proteinas_100g": 2,
        "hidratos_100g": 15, "grasas_100g": 4.5}, {"nombre": "brotes verdes y flores comestibles", "gramos": 5, "kcal_100g": 25,
        "proteinas_100g": 2, "hidratos_100g": 3, "grasas_100g": 0.5}], "confianza": "media",
        "nota": "La cantidad de aceite de la plancha y la composición exacta del puré son estimaciones."}"""

    @Test
    fun readsRealMealReply() {
        val meal = mealFromJson(parseModelJson(mealReply))
        assertEquals(listOf("Carne de ternera a la plancha", "Puré de patata con mantequilla", "Brotes verdes y flores comestibles"), meal.dishes.map { it.name })
        assertEquals(listOf(90.0, 60.0, 5.0), meal.dishes.map { it.grams })
        assertEquals(4.5, meal.dishes[1].nutrients.fat, 0.0)
        assertEquals(292.25, meal.dishes.sumOf { it.nutrients.scaledTo(it.grams).kcal }, 1e-9)
        assertEquals("media", meal.confidence)
        assertEquals("La cantidad de aceite de la plancha y la composición exacta del puré son estimaciones.", meal.note)
    }

    @Test
    fun mealDropsComponentsItCannotUse() {
        val reply = """{"componentes": [
            {"nombre": "patatas fritas", "gramos": "150 g", "kcal_100g": "312", "proteinas_100g": "3,4", "grasas_100g": 15},
            {"nombre": "", "gramos": 50, "kcal_100g": 100},
            {"nombre": "salsa", "gramos": 0, "kcal_100g": 300},
            {"nombre": "filete", "gramos": 120},
            {"nombre": "piedra", "gramos": 100, "kcal_100g": 2000},
            {"nombre": "bandeja", "gramos": 9000, "kcal_100g": 100}
        ], "confianza": "segura", "nota": "null"}"""
        val meal = mealFromJson(parseModelJson(reply))
        assertEquals(1, meal.dishes.size)
        val fries = meal.dishes.single()
        assertEquals("Patatas fritas", fries.name)
        assertEquals(150.0, fries.grams, 0.0)
        assertEquals(312.0, fries.nutrients.kcal, 0.0)
        assertEquals(3.4, fries.nutrients.protein, 1e-9)
        assertEquals(0.0, fries.nutrients.carbs, 0.0)
        assertNull(meal.confidence)
        assertNull(meal.note)
    }

    @Test
    fun noFoodGivesNoDishes() {
        assertEquals(MealEstimate(emptyList(), null, null), mealFromJson(parseModelJson("""{"componentes": []}""")))
    }
}
