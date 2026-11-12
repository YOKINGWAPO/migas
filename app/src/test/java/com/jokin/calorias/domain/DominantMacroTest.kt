package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominantMacroTest {
    @Test
    fun `fat counts nine kcal per gram`() {
        // 10 g of fat (90 kcal) beats 20 g of carbs (80 kcal), as in Greek yogurt or nuts.
        assertEquals(Macro.FAT, Nutrients(kcal = 170.0, protein = 3.0, carbs = 20.0, fat = 10.0).dominantMacro())
    }

    @Test
    fun `lean meat is protein`() {
        assertEquals(Macro.PROTEIN, Nutrients(kcal = 104.0, protein = 22.0, carbs = 1.0, fat = 1.5).dominantMacro())
    }

    @Test
    fun `fruit is carbs`() {
        assertEquals(Macro.CARBS, Nutrients(kcal = 94.0, protein = 1.4, carbs = 27.0, fat = 0.4).dominantMacro())
    }

    @Test
    fun `a tie goes to carbs`() {
        assertEquals(Macro.CARBS, Nutrients(protein = 10.0, carbs = 10.0).dominantMacro())
    }

    @Test
    fun `kcal without macros has no dominant macro`() {
        assertNull(Nutrients(kcal = 300.0).dominantMacro())
    }
}
