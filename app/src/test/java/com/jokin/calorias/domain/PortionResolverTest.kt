package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PortionResolverTest {
    @Test
    fun fixedUnitsIgnoreFood() {
        assertEquals(200.0, PortionResolver.grams(200.0, VoiceUnit.GRAM, 250, 120.0, emptyList()), 0.0)
        assertEquals(500.0, PortionResolver.grams(2.0, VoiceUnit.GLASS, 250, null, emptyList()), 0.0)
    }

    @Test
    fun unitUsesServingSize() {
        assertEquals(120.0, PortionResolver.grams(2.0, VoiceUnit.UNIT, 250, 60.0, emptyList()), 0.0)
    }

    @Test
    fun unitFallsBackToOwnPortionThenDefault() {
        assertEquals(45.0, PortionResolver.grams(1.5, VoiceUnit.SERVING, 250, null, listOf(Portion("pieza", 30.0))), 0.0)
        assertEquals(50.0, PortionResolver.grams(0.5, VoiceUnit.UNIT, 250, null, emptyList()), 0.0)
    }

    @Test
    fun sliceLooksForSlicePortion() {
        val portions = listOf(Portion("1 pieza", 400.0), Portion("1 Rebanada", 25.0))
        assertEquals(50.0, PortionResolver.grams(2.0, VoiceUnit.SLICE, 250, 400.0, portions), 0.0)
        assertEquals(30.0, PortionResolver.grams(1.0, VoiceUnit.SLICE, 250, 400.0, emptyList()), 0.0)
    }

    @Test
    fun sliceAlsoFindsEnglishPortion() {
        val portions = listOf(Portion("1 loaf", 400.0), Portion("1 Slice", 25.0))
        assertEquals(50.0, PortionResolver.grams(2.0, VoiceUnit.SLICE, 250, 400.0, portions), 0.0)
    }
}
