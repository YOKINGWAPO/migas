package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMeasuresTest {
    private val values = listOf(
        BodyValue(10, BodyPart.WAIST, 95.0),
        BodyValue(17, BodyPart.WAIST, 93.5),
        BodyValue(24, BodyPart.WAIST, 92.0),
        BodyValue(17, BodyPart.BICEPS, 36.0),
        BodyValue(24, BodyPart.CHEST, 104.0),
    )

    @Test
    fun previousIsTheLatestValueBeforeTheDayPerPart() {
        val p = BodyMeasures.previous(values, day = 24)
        assertEquals(mapOf(BodyPart.WAIST to values[1], BodyPart.BICEPS to values[3]), p)
    }

    @Test
    fun previousIgnoresTheSameDay() {
        assertTrue(BodyMeasures.previous(values, day = 10).isEmpty())
    }

    @Test
    fun changesGoFromFirstToLastInsideTheRange() {
        val c = BodyMeasures.changes(values, from = 15, to = 30)
        assertEquals(1, c.size)
        assertEquals(BodyPart.WAIST, c[0].part)
        assertEquals(-1.5, c[0].deltaCm, 1e-9)
    }

    @Test
    fun partsMeasuredOnASingleDayHaveNoChange() {
        val c = BodyMeasures.changes(values, from = 0, to = 30)
        assertEquals(listOf(BodyPart.WAIST), c.map { it.part })
        assertEquals(-3.0, c[0].deltaCm, 1e-9)
    }

    @Test
    fun changesAreInBodyPartOrder() {
        val more = values + BodyValue(30, BodyPart.CHEST, 102.0)
        assertEquals(listOf(BodyPart.CHEST, BodyPart.WAIST), BodyMeasures.changes(more, 0, 30).map { it.part })
    }
}
