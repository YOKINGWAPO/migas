package com.jokin.calorias.domain

import com.jokin.calorias.domain.CsvImport.Column
import com.jokin.calorias.domain.CsvImport.FileProblem
import com.jokin.calorias.domain.CsvImport.ImportException
import com.jokin.calorias.domain.CsvImport.Parsed
import com.jokin.calorias.domain.CsvImport.RowProblem
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun diary(text: String) = CsvImport.parse(text, today) as Parsed.Diary
    private fun weights(text: String) = CsvImport.parse(text, today) as Parsed.Weights
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `spanish semicolon file with decimal commas and accented headers`() {
        val d = diary("Fecha;Comida;Alimento;Gramos;Calorías;Proteínas (g);Hidratos (g);Grasas (g)\n03/02/2026;Cena;Tortilla;150;\"235,5\";12,3;4;18\n")
        assertEquals(emptyList<CsvImport.RowError>(), d.errors)
        val r = d.rows.single()
        assertEquals(day(2026, 2, 3), r.epochDay)
        assertEquals(MealType.DINNER, r.meal)
        assertEquals("Tortilla", r.name)
        assertEquals(150.0, r.grams!!, 0.0)
        assertEquals(Nutrients(235.5, 12.3, 4.0, 18.0), r.nutrients)
    }

    @Test
    fun `english comma file in another column order with iso dates`() {
        val d = diary("food,kcal,date,meal,grams\nBanana,105,2026-11-10,Breakfast,120\n\"Rice, boiled\",130,2026-11-10,lunch,100\n")
        assertEquals(listOf("Banana", "Rice, boiled"), d.rows.map { it.name })
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH), d.rows.map { it.meal })
        assertEquals(105.0, d.rows[0].nutrients.kcal, 0.0)
    }

    @Test
    fun `round trip with the app's own diary export`() {
        val exported = "\uFEFFfecha;hora;comida;alimento;marca;origen;gramos;kcal;proteinas_g;hidratos_g;grasas_g;azucares_g;fibra_g;saturadas_g;sal_g\n" +
            "2026-11-11;08:15;Desayuno;Yogur natural;Danone;Open Food Facts;125,00;76,25;4,38;5,88;3,75;;;;\n"
        val r = diary(exported).rows.single()
        assertEquals("Danone", r.brand)
        assertEquals(LocalTime.of(8, 15), r.time)
        assertEquals(125.0, r.grams!!, 0.0)
        assertEquals(76.25, r.nutrients.kcal, 1e-9)
    }

    @Test
    fun `month first dates are detected from a day above twelve`() {
        val d = diary("date,food,kcal\n01/13/2026,Apple,52\n02/03/2026,Pear,57\n")
        assertEquals(listOf(day(2026, 1, 13), day(2026, 2, 3)), d.rows.map { it.epochDay })
    }

    @Test
    fun `ambiguous dates are read day first`() {
        assertEquals(day(2026, 2, 3), diary("fecha;alimento;kcal\n03/02/2026;Pera;57\n").rows.single().epochDay)
    }

    @Test
    fun `missing meal is guessed from the time, else left for the user`() {
        val d = diary("fecha;hora;alimento;kcal\n2026-11-10;21:30;Sopa;120\n2026-11-10;;Pan;80\n")
        assertEquals(MealType.DINNER, d.rows[0].meal)
        assertNull(d.rows[1].meal)
    }

    @Test
    fun `kcal is computed from macros when the column is missing`() {
        val r = diary("fecha;alimento;proteinas;hidratos;grasas\n2026-11-10;Batido;20;10;5\n").rows.single()
        assertEquals(20 * 4 + 10 * 4 + 5 * 9.0, r.nutrients.kcal, 1e-9)
    }

    @Test
    fun `row without grams keeps only its totals`() {
        val r = diary("fecha;alimento;gramos;kcal\n2026-11-10;Menú del día;;850\n").rows.single()
        assertNull(r.grams)
        assertEquals(850.0, r.nutrients.kcal, 0.0)
    }

    @Test
    fun `grams with a unit suffix are accepted`() {
        assertEquals(200.0, diary("fecha;alimento;cantidad;kcal\n2026-11-10;Arroz;200 g;260\n").rows.single().grams!!, 0.0)
    }

    @Test
    fun `bad rows are reported with their line and the good ones kept`() {
        val d = diary(
            "fecha;alimento;gramos;kcal\n" +
                "2026-11-10;Pan;50;130\n" +
                "mañana;Leche;200;120\n" +
                "2026-11-10;;100;50\n" +
                "2026-11-10;Aceite;10;abc\n" +
                "2026-11-10;Bocadillo;-5;300\n" +
                "2026-11-10;Agua;100;\n" +
                "2026-11-20;Futuro;100;100\n" +
                "2026-11-10;Error;10;500\n"
        )
        assertEquals(listOf("Pan"), d.rows.map { it.name })
        assertEquals(
            listOf(
                CsvImport.RowError(3, RowProblem.BAD_DATE, Column.DATE),
                CsvImport.RowError(4, RowProblem.MISSING_NAME, Column.NAME),
                CsvImport.RowError(5, RowProblem.BAD_NUMBER, Column.KCAL),
                CsvImport.RowError(6, RowProblem.OUT_OF_RANGE, Column.GRAMS),
                CsvImport.RowError(7, RowProblem.MISSING_KCAL, Column.KCAL),
                CsvImport.RowError(8, RowProblem.FUTURE_DATE, Column.DATE),
                CsvImport.RowError(9, RowProblem.TOO_MANY_KCAL_PER_100G, Column.KCAL),
            ),
            d.errors,
        )
    }

    @Test
    fun `line numbers count line breaks inside quoted fields`() {
        val d = diary("fecha;alimento;kcal\n2026-11-10;\"Tarta\nde queso\";300\n2026-11-10;Pan;x\n")
        assertEquals("Tarta de queso", d.rows.single().name)
        assertEquals(4, d.errors.single().line)
    }

    @Test
    fun `quotes inside quoted fields and blank lines`() {
        val d = diary("\n\nfecha;alimento;kcal\n\n2026-11-10;\"Pizza \"\"4 quesos\"\"\";800\n\n")
        assertEquals("Pizza \"4 quesos\"", d.rows.single().name)
    }

    @Test
    fun `weight history from the app's own export and from another app`() {
        val own = weights("\uFEFFfecha;peso_kg;grasa_corporal_pct\n2026-11-01;82,40;21,50\n2026-11-08;81,90;\n")
        assertEquals(listOf(82.4, 81.9), own.rows.map { it.kg })
        val other = weights("Date,Weight (kg)\n2026-10-01,90.5\n2026-10-02,500\n")
        assertEquals(listOf(90.5), other.rows.map { it.kg })
        assertEquals(CsvImport.RowError(3, RowProblem.OUT_OF_RANGE, Column.WEIGHT), other.errors.single())
    }

    @Test
    fun `numbers with thousands marks are read by their last mark`() {
        assertEquals(1234.5, CsvImport.parseNumber("1.234,5", decimalComma = true)!!, 1e-9)
        assertEquals(1234.5, CsvImport.parseNumber("1,234.5", decimalComma = false)!!, 1e-9)
        assertEquals(75.5, CsvImport.parseNumber("75,5", decimalComma = true)!!, 1e-9)
        assertNull(CsvImport.parseNumber("=1+1", decimalComma = true))
        assertNull(CsvImport.parseNumber("", decimalComma = true))
    }

    @Test
    fun `impossible dates are rejected`() {
        assertNull(CsvImport.parseDate("31/02/2026", dayFirst = true))
        assertNull(CsvImport.parseDate("2026-13-01", dayFirst = true))
        assertEquals(LocalDate.of(2026, 2, 3), CsvImport.parseDate("03.02.26", dayFirst = true))
    }

    @Test
    fun `dates before 2000 are rejected`() {
        assertEquals(RowProblem.BAD_DATE, diary("fecha;alimento;kcal\n1999-12-31;Pan;80\n").errors.single().problem)
    }

    @Test
    fun `file problems`() {
        assertEquals(FileProblem.EMPTY, assertThrows(ImportException::class.java) { CsvImport.parse("", today) }.problem)
        assertEquals(FileProblem.EMPTY, assertThrows(ImportException::class.java) { CsvImport.parse("\n \n", today) }.problem)
        assertEquals(FileProblem.UNKNOWN_COLUMNS, assertThrows(ImportException::class.java) { CsvImport.parse("a;b;c\n1;2;3\n", today) }.problem)
        assertEquals(FileProblem.TOO_BIG, assertThrows(ImportException::class.java) { CsvImport.parse("x".repeat(CsvImport.MAX_CHARS + 1), today) }.problem)
        val many = "fecha;peso\n" + "2026-01-01;80\n".repeat(CsvImport.MAX_ROWS + 1)
        assertEquals(FileProblem.TOO_MANY_ROWS, assertThrows(ImportException::class.java) { CsvImport.parse(many, today) }.problem)
    }

    @Test
    fun `header only gives an empty result, not an error`() {
        val d = diary("fecha;alimento;kcal\n")
        assertTrue(d.rows.isEmpty() && d.errors.isEmpty())
    }

    @Test
    fun `both templates parse back without errors`() {
        listOf(true, false).forEach { english ->
            val d = diary(CsvImport.diaryTemplate(english))
            assertEquals(2, d.rows.size)
            assertEquals(emptyList<CsvImport.RowError>(), d.errors)
            assertNull(d.rows[1].grams)
            val w = weights(CsvImport.weightTemplate(english))
            assertEquals(listOf(82.4, 81.9), w.rows.map { it.kg })
        }
    }
}
