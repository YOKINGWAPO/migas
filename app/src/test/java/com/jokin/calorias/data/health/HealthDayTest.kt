package com.jokin.calorias.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthDayTest {
    private fun day(total: Double?, active: Double?) = HealthDay(1, total, active, steps = 5000, distanceM = null)

    @Test
    fun `a day with only active calories still counts as watch burn`() {
        assertTrue(day(total = null, active = 320.0).hasBurn)
        assertTrue(day(total = 2400.0, active = null).hasBurn)
    }

    @Test
    fun `steps alone are not burn data`() {
        assertFalse(day(total = null, active = null).hasBurn)
    }

    @Test
    fun `known watch apps get their name and unknown ones are flagged`() {
        val packages = setOf("com.sec.android.app.shealth", "com.garmin.android.apps.connectmobile", "org.example.watch")
        assertEquals(listOf("Garmin Connect", "Samsung Health"), SourceApps.names(packages))
        assertTrue(SourceApps.anyUnknown(packages))
        assertFalse(SourceApps.anyUnknown(setOf("com.fitbit.FitbitMobile")))
    }
}
