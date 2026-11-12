package com.jokin.calorias.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PhotoArchiveTest {
    private val file = "0c8d1e2f-3a4b-4c5d-8e9f-a0b1c2d3e4f5.jpg"
    private val day = LocalDate.of(2026, 10, 15).toEpochDay()

    @Test
    fun entryNameRoundTripsTheDay() {
        val name = PhotoArchive.entryName(day, file)
        assertEquals("2026-10-15_$file", name)
        assertEquals(PhotoArchive.Entry(day, file), PhotoArchive.parse(name))
    }

    @Test
    fun pathsAndForeignNamesAreRejected() {
        assertNull(PhotoArchive.parse("../2026-10-15_$file"))
        assertNull(PhotoArchive.parse("fotos/2026-10-15_$file"))
        assertNull(PhotoArchive.parse("2026-10-15_vacaciones.jpg"))
        assertNull(PhotoArchive.parse("2026-10-15_${file.removeSuffix(".jpg")}.png"))
    }

    @Test
    fun impossibleDatesAreRejected() {
        assertNull(PhotoArchive.parse("2026-13-40_$file"))
    }

    @Test
    fun onlyAppFileNamesAreValidPhotoFiles() {
        assertTrue(PhotoArchive.isPhotoFileName(file))
        assertFalse(PhotoArchive.isPhotoFileName("../$file"))
        assertFalse(PhotoArchive.isPhotoFileName("calorias.db"))
    }
}
