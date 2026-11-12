package com.jokin.calorias.data.seed

import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.per100g
import com.jokin.calorias.domain.ExtraNutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class FoodTableCsvTest {
    private val lines = File("src/main/assets/alimentos.csv").readLines()
    private val spanish = FoodTableCsv.parse(lines.asSequence(), english = false, now = 0)
    private val english = FoodTableCsv.parse(lines.asSequence(), english = true, now = 0)

    @Test
    fun loadsTheWholeCiqualTableWithUniqueSearchKeys() {
        assertTrue("expected the full CIQUAL table, got ${spanish.size}", spanish.size >= 3300)
        assertEquals(spanish.size, spanish.map { it.searchName }.toSet().size)
        assertTrue(spanish.any { it.source == FoodSource.USDA })
    }

    @Test
    fun everydayFoodsHaveTheirShortNameAndServing() {
        val banana = spanish.first { it.name == "Plátano" }
        assertEquals(FoodSource.CIQUAL, banana.source)
        assertTrue((banana.servingGrams ?: 0.0) > 0)
        assertTrue(spanish.any { it.name.startsWith("Garbanzo") })
        assertTrue(spanish.any { it.name.startsWith("Lechuga") })
    }

    @Test
    fun nameAndServingFollowTheLanguageButTheSearchKeyDoesNot() {
        val es = spanish.first { it.name == "Plátano" }
        val en = english[spanish.indexOf(es)]
        assertEquals("Banana", en.name)
        assertEquals(es.searchName, en.searchName)
        assertTrue(en.searchName.contains("platano") && en.searchName.contains("banana"))
        assertTrue(es.servingName != en.servingName)
    }

    @Test
    fun valuesArePlausible() {
        spanish.forEach { f ->
            val n = f.per100g()
            assertTrue(f.name, n.kcal in 0.0..900.0)
            assertTrue(f.name, n.protein + n.carbs + n.fat <= 100.5)
            assertTrue(f.name, (n.extras[ExtraNutrient.SATURATED_FAT] ?: 0.0) <= n.fat + 0.05)
            f.servingGrams?.let { assertTrue(f.name, it > 0) }
        }
    }

    /**
     * Our own rows (USDA basics, with carbs converted to the EU basis) must still add up: Atwater
     * factors 4/4/9 and fibre 2 within 15 %. CIQUAL rows are published values and are not re-checked.
     */
    @Test
    fun usdaEnergyMatchesMacros() {
        val off = spanish.filter { it.source == FoodSource.USDA }.mapNotNull { f ->
            val n = f.per100g()
            val computed = 4 * n.protein + 4 * n.carbs + 9 * n.fat + 2 * (n.extras[ExtraNutrient.FIBER] ?: 0.0)
            if (abs(computed - n.kcal) > maxOf(15.0, n.kcal * 0.15)) "${f.name}: ${n.kcal} vs ${"%.0f".format(computed)}" else null
        }
        assertTrue(off.joinToString("\n"), off.isEmpty())
    }

    @Test
    fun unpublishedNutrientsAreLeftOutAndMissingMacrosCountAsZero() {
        val row = "CIQUAL;1;Prueba;Test;50;;10;;;;;;;;"
        val food = FoodTableCsv.parse(sequenceOf(row), english = false, now = 0).single()
        val n = food.per100g()
        assertEquals(0.0, n.protein, 0.0)
        assertEquals(0.0, n.fat, 0.0)
        assertEquals(10.0, n.carbs, 0.0)
        assertTrue(n.extras.isEmpty())
        assertNull(food.servingName)
    }

    @Test
    fun rejectsRowsItCannotTrust() {
        assertThrows(IllegalArgumentException::class.java) {
            FoodTableCsv.parse(sequenceOf("BEDCA;1;Prueba;Test;50;1;1;1;;;;;;;"), english = false, now = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodTableCsv.parse(sequenceOf("CIQUAL;1;Prueba;Test;;1;1;1;;;;;;;"), english = false, now = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodTableCsv.parse(sequenceOf("CIQUAL;1;Prueba;Test;50;1;1"), english = false, now = 0)
        }
    }
}
