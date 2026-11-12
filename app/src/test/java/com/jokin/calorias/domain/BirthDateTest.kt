package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BirthDateTest {
    private fun d(text: String) = LocalDate.parse(text).toEpochDay()

    @Test
    fun ageGoesUpOnTheBirthdayNotTheDayBefore() {
        val birth = d("1995-10-16")
        assertEquals(30, BirthDate.ageOn(birth, d("2026-10-15")))
        assertEquals(31, BirthDate.ageOn(birth, d("2026-10-16")))
        assertEquals(31, BirthDate.ageOn(birth, d("2027-10-15")))
    }

    @Test
    fun ageIsZeroOnTheBirthDay() {
        assertEquals(0, BirthDate.ageOn(d("2026-10-16"), d("2026-10-16")))
    }

    @Test
    fun birthdayOnlyOnItsDay() {
        val birth = d("1995-10-16")
        assertTrue(BirthDate.isBirthday(birth, d("2026-10-16")))
        assertFalse(BirthDate.isBirthday(birth, d("2026-10-15")))
        assertFalse(BirthDate.isBirthday(birth, d("2026-10-17")))
    }

    @Test
    fun birthDayItselfIsNotABirthday() {
        assertFalse(BirthDate.isBirthday(d("2026-10-16"), d("2026-10-16")))
    }

    @Test
    fun leapDayBirthdayIsFirstOfMarchInCommonYears() {
        val birth = d("2000-02-29")
        assertEquals(25, BirthDate.ageOn(birth, d("2026-02-28")))
        assertEquals(26, BirthDate.ageOn(birth, d("2026-03-01")))
        assertFalse(BirthDate.isBirthday(birth, d("2026-02-28")))
        assertTrue(BirthDate.isBirthday(birth, d("2026-03-01")))
        assertTrue(BirthDate.isBirthday(birth, d("2028-02-29")))
        assertFalse(BirthDate.isBirthday(birth, d("2028-03-01")))
    }
}
