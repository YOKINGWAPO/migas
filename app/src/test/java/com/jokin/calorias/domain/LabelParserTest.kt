package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {
    private val columnsX = listOf(0, 420, 700, 980)

    /** Builds OCR lines as ML Kit returns a printed table: one line per non-empty cell, 50 px per row. */
    private fun table(vararg rows: List<String>): List<TextLine> = rows.flatMapIndexed { r, cells ->
        cells.mapIndexedNotNull { c, text ->
            if (text.isEmpty()) null else TextLine(text, TextBox(columnsX[c], r * 50, columnsX[c] + 12 * text.length, r * 50 + 36))
        }
    }

    private fun line(text: String, row: Int) = TextLine(text, TextBox(0, row * 50, 24 * text.length, row * 50 + 36))

    private val spanish = table(
        listOf("Información nutricional", "Por 100 g", "Por ración (30 g)", "%IR*"),
        listOf("Valor energético", "1046 kJ / 250 kcal", "314 kJ / 75 kcal", "4%"),
        listOf("Grasas", "3,2 g", "1,0 g", "1%"),
        listOf("de las cuales saturadas", "1,1 g", "0,3 g", "2%"),
        listOf("Hidratos de carbono", "40,5 g", "12,2 g", "5%"),
        listOf("de los cuales azúcares", "12 g", "3,6 g", "4%"),
        listOf("Fibra alimentaria", "2,5 g", "0,8 g", ""),
        listOf("Proteínas", "8,1 g", "2,4 g", "5%"),
        listOf("Sal", "0,85 g", "0,26 g", "4%"),
    )

    @Test
    fun typicalSpanishTablePicksPer100gColumn() {
        val r = LabelParser.parse(spanish)!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(
            mapOf(ExtraNutrient.SATURATED_FAT to 1.1, ExtraNutrient.SUGARS to 12.0, ExtraNutrient.FIBER to 2.5, ExtraNutrient.SALT to 0.85),
            r.nutrients.extras,
        )
        assertEquals(30.0, r.servingGrams!!, 0.001)
    }

    @Test
    fun lineOrderDoesNotMatter() {
        assertEquals(LabelParser.parse(spanish), LabelParser.parse(spanish.shuffled(kotlin.random.Random(7))))
    }

    @Test
    fun servingColumnFirstUsesThe100gHeader() {
        val r = LabelParser.parse(
            table(
                listOf("Nutrition", "Per serving (30g)", "Per 100g", ""),
                listOf("Energy", "314kJ/75kcal", "1046kJ/250kcal", ""),
                listOf("Fat", "1.0g", "3.2g", ""),
                listOf("of which saturates", "0.3g", "1.1g", ""),
                listOf("Carbohydrate", "12.2g", "40.5g", ""),
                listOf("Protein", "2.4g", "8.1g", ""),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(1.1, r.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(30.0, r.servingGrams!!, 0.001)
    }

    @Test
    fun energyWrappedOntoNextRow() {
        val r = LabelParser.parse(
            table(
                listOf("Valor energético", "1046 kJ", "", ""),
                listOf("", "250 kcal", "", ""),
                listOf("Grasas", "3,2 g", "", ""),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
    }

    @Test
    fun kilojoulesOnlyAreConverted() {
        val r = LabelParser.parse(table(listOf("Energía", "1.046 kJ", "", ""), listOf("Grasas", "3 g", "", "")))!!
        assertEquals(250.0, r.nutrients.kcal, 0.01)
    }

    @Test
    fun bareEnergyPairTakesTheKcalHalf() {
        val r = LabelParser.parse(table(listOf("Energía (kJ/kcal)", "1046/250", "", ""), listOf("Grasas", "3 g", "", "")))!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
    }

    @Test
    fun belowLimitMeansZero() {
        val r = LabelParser.parse(
            table(listOf("Valor energético", "250 kcal", "", ""), listOf("de los cuales azúcares", "<0,5 g", "", ""), listOf("Sal", "< 0,01 g", "", ""))
        )!!
        assertEquals(0.0, r.nutrients.extras[ExtraNutrient.SUGARS]!!, 0.0)
        assertEquals(0.0, r.nutrients.extras[ExtraNutrient.SALT]!!, 0.0)
    }

    @Test
    fun wholeRowInOneLineWithoutHeader() {
        val r = LabelParser.parse(listOf(line("Valor energético 1046 kJ/250 kcal 523 kJ/125 kcal", 0), line("Grasas 3,2 g 1,6 g 2%", 1)))!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
    }

    @Test
    fun milligramsBecomeGrams() {
        val r = LabelParser.parse(table(listOf("Energía", "250 kcal", "", ""), listOf("Sal", "850 mg", "", "")))!!
        assertEquals(0.85, r.nutrients.extras[ExtraNutrient.SALT]!!, 0.0001)
    }

    @Test
    fun implausibleValuesAreDropped() {
        // OCR losing the decimal comma: "3,2" read as "32,0" would pass, but "320" cannot be grams per 100 g.
        val r = LabelParser.parse(table(listOf("Energía", "250 kcal", "", ""), listOf("Grasas", "320 g", "", "")))!!
        assertEquals(0.0, r.nutrients.fat, 0.0)
        assertNull(LabelParser.parse(table(listOf("Energía", "2500 kcal", "", ""))))
    }

    @Test
    fun numbersBeforeTheKeywordAreIgnored() {
        val r = LabelParser.parse(table(listOf("Energía", "250 kcal", "", ""), listOf("Vitamina B12 Proteínas", "8 g", "", "")))!!
        assertEquals(8.0, r.nutrients.protein, 0.001)
    }

    @Test
    fun noEnergyMeansNoReading() {
        assertNull(LabelParser.parse(table(listOf("Grasas", "3,2 g", "", ""), listOf("Proteínas", "8 g", "", ""))))
        assertNull(LabelParser.parse(emptyList()))
    }

    @Test
    fun tableBoundsCoverTheNutrientRows() {
        val withNoise = spanish + line("Ingredientes: harina de trigo", 12)
        val box = LabelParser.tableBounds(withNoise)
        assertNotNull(box)
        assertEquals(TextBox(0, 50, columnsX[3] + 12 * 2, 8 * 50 + 36), box)
    }

    @Test
    fun typicalUkTableWithSizeBeforeServing() {
        val r = LabelParser.parse(
            table(
                listOf("Typical values", "Per 100g", "Per 30g serving", "%RI*"),
                listOf("Energy", "1046kJ / 250kcal", "314kJ / 75kcal", "4%"),
                listOf("Fat", "3.2g", "1.0g", "1%"),
                listOf("of which saturates", "1.1g", "0.3g", "2%"),
                listOf("Carbohydrate", "40.5g", "12.2g", "5%"),
                listOf("of which sugars", "12g", "3.6g", "4%"),
                listOf("Fibre", "2.5g", "0.8g", ""),
                listOf("Protein", "8.1g", "2.4g", "5%"),
                listOf("Salt", "0.85g", "0.26g", "4%"),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(
            mapOf(ExtraNutrient.SATURATED_FAT to 1.1, ExtraNutrient.SUGARS to 12.0, ExtraNutrient.FIBER to 2.5, ExtraNutrient.SALT to 0.85),
            r.nutrients.extras,
        )
        assertEquals(30.0, r.servingGrams!!, 0.001)
    }

    @Test
    fun englishRowsInOneLineWithFiberSpelling() {
        val r = LabelParser.parse(
            listOf(
                line("Energy 1046 kJ/250 kcal", 0),
                line("Fat 3.2 g", 1),
                line("Saturated fat 1.1 g", 2),
                line("Carbohydrates 40.5 g", 3),
                line("Sugars 12 g", 4),
                line("Dietary fiber 2.5 g", 5),
                line("Protein 8.1 g", 6),
                line("Salt 0.85 g", 7),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(
            mapOf(ExtraNutrient.SATURATED_FAT to 1.1, ExtraNutrient.SUGARS to 12.0, ExtraNutrient.FIBER to 2.5, ExtraNutrient.SALT to 0.85),
            r.nutrients.extras,
        )
        assertNull(r.servingGrams)
    }

    @Test
    fun tableBoundsNeedTwoNutrients() {
        assertNull(LabelParser.tableBounds(listOf(line("Sal marina", 0), line("Ingredientes: harina", 1))))
        assertNull(LabelParser.tableBounds(emptyList()))
    }

    @Test
    fun unitGluedAsNineOnEveryRowIsStripped() {
        val r = LabelParser.parse(
            table(
                listOf("Información nutricional", "Por 100 g", "", ""),
                listOf("Valor energético", "1566 kJ / 372 kcal", "", ""),
                listOf("Grasas", "7,09", "", ""),
                listOf("de las cuales saturadas", "1,39", "", ""),
                listOf("Hidratos de carbono", "58,79", "", ""),
                listOf("de los cuales azúcares", "0,79", "", ""),
                listOf("Proteínas", "13,59", "", ""),
            )
        )!!
        assertEquals(372.0, r.nutrients.kcal, 0.001)
        assertEquals(7.0, r.nutrients.fat, 0.001)
        assertEquals(58.7, r.nutrients.carbs, 0.001)
        assertEquals(13.5, r.nutrients.protein, 0.001)
        assertEquals(1.3, r.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.001)
        assertEquals(0.7, r.nutrients.extras[ExtraNutrient.SUGARS]!!, 0.001)
    }

    @Test
    fun saturatesThatCannotFitInTheFatAreLeftBlank() {
        // "13" is "1,3" with the comma lost, but nothing confirms it: better blank than a guess.
        val r = LabelParser.parse(
            table(
                listOf("Valor energético", "372 kcal", "", ""),
                listOf("Grasas", "7,0 g", "", ""),
                listOf("de las cuales saturadas", "13 g", "", ""),
                listOf("Hidratos de carbono", "58,7 g", "", ""),
                listOf("Proteínas", "13,5 g", "", ""),
            )
        )!!
        assertNull(r.nutrients.extras[ExtraNutrient.SATURATED_FAT])
        assertEquals(7.0, r.nutrients.fat, 0.001)
    }

    @Test
    fun lostDecimalCommaInAMacroIsRestoredByTheKcal() {
        // "35" of fat with 372 kcal cannot be; "3,5" makes the kcal add up.
        val r = LabelParser.parse(
            table(
                listOf("Valor energético", "372 kcal", "", ""),
                listOf("Grasas", "35 g", "", ""),
                listOf("Hidratos de carbono", "65 g", "", ""),
                listOf("Proteínas", "13,5 g", "", ""),
            )
        )!!
        assertEquals(3.5, r.nutrients.fat, 0.001)
    }

    @Test
    fun valuesAreKeptAsPrintedWhenTheyAddUp() {
        // "19 g" of protein could be "1,9" with a lost comma, but the kcal agree with 19.
        val r = LabelParser.parse(
            table(
                listOf("Energía", "400 kcal", "", ""),
                listOf("Grasas", "8 g", "", ""),
                listOf("Hidratos de carbono", "60 g", "", ""),
                listOf("Proteínas", "19 g", "", ""),
            )
        )!!
        assertEquals(19.0, r.nutrients.protein, 0.001)
        assertEquals(60.0, r.nutrients.carbs, 0.001)
    }

    @Test
    fun labelsWrappedOverSeveralLinesInTwoLanguages() {
        // Small print: each label spans lines, the value sits between them (Spanish / Portuguese).
        fun at(text: String, x: Int, y: Int) = TextLine(text, TextBox(x, y - 6, x + 8 * text.length, y + 6))
        val r = LabelParser.parse(
            listOf(
                at("Valor", 10, 47), at("Energético/", 10, 60), at("Energia", 10, 72), at("74 kcal", 200, 66),
                at("Grasas", 10, 86), at("Lípidos", 10, 98), at("4,2 g", 200, 92),
                at("de las cuales/", 10, 112), at("dos quais", 10, 122), at("-Saturadas/", 10, 134), at("Saturados", 10, 145), at("2,9 g", 200, 140),
                at("Hidratos de", 10, 159), at("Carbono", 10, 170), at("5,1 g", 200, 165),
                at("Proteinas", 10, 229), at("3,9 g", 200, 229),
                at("Sal", 10, 246), at("0,08 g", 200, 246),
            )
        )!!
        assertEquals(74.0, r.nutrients.kcal, 0.001)
        assertEquals(4.2, r.nutrients.fat, 0.001)
        assertEquals(2.9, r.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.001)
        assertEquals(5.1, r.nutrients.carbs, 0.001)
        assertEquals(3.9, r.nutrients.protein, 0.001)
        assertEquals(0.08, r.nutrients.extras[ExtraNutrient.SALT]!!, 0.0001)
    }

    @Test
    fun runningTextOnSmallPackages() {
        val r = LabelParser.parse(
            listOf(
                line("Información nutricional por 100 g: valor energético 1046 kJ/250 kcal; grasas 3,2 g,", 0),
                line("de las cuales saturadas 1,1 g; hidratos de carbono 40,5 g, de los cuales azúcares 12 g;", 1),
                line("fibra alimentaria 2,5 g; proteínas 8,1 g; sal 0,85 g.", 2),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(
            mapOf(ExtraNutrient.SATURATED_FAT to 1.1, ExtraNutrient.SUGARS to 12.0, ExtraNutrient.FIBER to 2.5, ExtraNutrient.SALT to 0.85),
            r.nutrients.extras,
        )
    }

    @Test
    fun unsaturatedFatsAreNotSaturates() {
        val r = LabelParser.parse(
            table(
                listOf("Valor energético", "884 kcal", "", ""),
                listOf("Grasas", "100 g", "", ""),
                listOf("de las cuales saturadas", "14 g", "", ""),
                listOf("monoinsaturadas", "73 g", "", ""),
                listOf("poliinsaturadas", "11 g", "", ""),
            )
        )!!
        assertEquals(14.0, r.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.001)
    }

    @Test
    fun germanTable() {
        val r = LabelParser.parse(
            table(
                listOf("Nährwerte", "pro 100 g", "", ""),
                listOf("Brennwert", "1046 kJ / 250 kcal", "", ""),
                listOf("Fett", "3,2 g", "", ""),
                listOf("davon gesättigte Fettsäuren", "1,1 g", "", ""),
                listOf("Kohlenhydrate", "40,5 g", "", ""),
                listOf("davon Zucker", "12 g", "", ""),
                listOf("Ballaststoffe", "2,5 g", "", ""),
                listOf("Eiweiß", "8,1 g", "", ""),
                listOf("Salz", "0,85 g", "", ""),
            )
        )!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
        assertEquals(40.5, r.nutrients.carbs, 0.001)
        assertEquals(8.1, r.nutrients.protein, 0.001)
        assertEquals(
            mapOf(ExtraNutrient.SATURATED_FAT to 1.1, ExtraNutrient.SUGARS to 12.0, ExtraNutrient.FIBER to 2.5, ExtraNutrient.SALT to 0.85),
            r.nutrients.extras,
        )
    }

    @Test
    fun theFirstOfTwo100gHeadersIsThePer100gColumn() {
        val r = LabelParser.parse(
            table(
                listOf("Valores medios", "por 100g", "IR por 100g", "por ración 40 g"),
                listOf("Valor energético", "375 kcal", "19 %", "147 kcal"),
                listOf("Grasas", "7,0 g", "10 %", "2,8 g"),
                listOf("Hidratos de carbono", "59 g", "23 %", "23 g"),
                listOf("Proteínas", "14 g", "28 %", "5,6 g"),
            )
        )!!
        assertEquals(375.0, r.nutrients.kcal, 0.001)
        assertEquals(7.0, r.nutrients.fat, 0.001)
        assertEquals(14.0, r.nutrients.protein, 0.001)
    }

    @Test
    fun kcalComeFromMacrosWhenTheEnergyRowIsMissing() {
        val r = LabelParser.parse(table(listOf("Grasas", "3 g", "", ""), listOf("Hidratos de carbono", "40 g", "", ""), listOf("Proteínas", "8 g", "", "")))!!
        assertEquals(4 * 8.0 + 4 * 40.0 + 9 * 3.0, r.nutrients.kcal, 0.001)
        assertEquals(emptySet<Macro>(), r.unreadMacros)
    }

    @Test
    fun macrosNotFoundAreReportedAsUnread() {
        val r = LabelParser.parse(table(listOf("Valor energético", "250 kcal", "", ""), listOf("Grasas", "3,2 g", "", "")))!!
        assertEquals(setOf(Macro.PROTEIN, Macro.CARBS), r.unreadMacros)
        assertEquals(0.0, r.nutrients.protein, 0.0)
    }

    @Test
    fun passesThatAgreeOutvoteOneMisread() {
        val clean = spanish
        val misread = spanish.map { if (it.text == "8,1 g") it.copy(text = "3,1 g") else it }
        assertEquals(8.1, LabelParser.parseBest(listOf(misread, clean, clean))!!.nutrients.protein, 0.001)
        assertEquals(8.1, LabelParser.parseBest(listOf(clean, clean, misread))!!.nutrients.protein, 0.001)
    }

    @Test
    fun spacedUnitReadAsNine() {
        val r = LabelParser.parse(table(listOf("Valor energético", "250 kcal", "", ""), listOf("Sal", "0,08 9", "", ""), listOf("Grasas", "3,2 9", "", "")))!!
        assertEquals(0.08, r.nutrients.extras[ExtraNutrient.SALT]!!, 0.0001)
        assertEquals(3.2, r.nutrients.fat, 0.001)
    }

    @Test
    fun aGlued8OnALabelThatPrintsItsUnits() {
        // Every other value shows its "g": "0,78" and "1,38" are "0,7g" and "1,3g" with the g read as 8.
        val r = LabelParser.parse(
            table(
                listOf("Valor energético", "375 kcal", "", ""),
                listOf("Grasas", "7,0 g", "", ""),
                listOf("de las cuales saturadas", "1,38", "", ""),
                listOf("Hidratos de carbono", "59 g", "", ""),
                listOf("de los cuales azúcares", "0,78", "", ""),
                listOf("Fibra alimentaria", "10,0 g", "", ""),
                listOf("Proteínas", "14 g", "", ""),
                listOf("Sal", "0,02 g", "", ""),
            )
        )!!
        assertEquals(1.3, r.nutrients.extras[ExtraNutrient.SATURATED_FAT]!!, 0.001)
        assertEquals(0.7, r.nutrients.extras[ExtraNutrient.SUGARS]!!, 0.001)
        assertEquals(0.02, r.nutrients.extras[ExtraNutrient.SALT]!!, 0.0001)
    }

    @Test
    fun kcalWorkedOutFromMacrosAreFlagged() {
        val r = LabelParser.parse(table(listOf("Grasas", "3 g", "", ""), listOf("Hidratos de carbono", "40 g", "", ""), listOf("Proteínas", "8 g", "", "")))!!
        assertTrue(r.kcalFromMacros)
        assertFalse(LabelParser.parse(spanish)!!.kcalFromMacros)
    }

    @Test
    fun theLiveReadingFillsInKcalCutOffInThePhoto() {
        val photo = spanish.filterNot { "kcal" in it.text }
        assertTrue(LabelParser.parse(photo)!!.kcalFromMacros)
        val live = LabelParser.parse(spanish)!!
        val r = LabelParser.parseBest(listOf(photo), hint = live)!!
        assertEquals(250.0, r.nutrients.kcal, 0.001)
        assertFalse(r.kcalFromMacros)
        assertEquals(8.1, r.nutrients.protein, 0.001)
    }

    @Test
    fun thePhotoOutweighsALiveReadingThatDisagrees() {
        val live = LabelParser.parse(spanish.map { if (it.text == "8,1 g") it.copy(text = "6,1 g") else it })!!
        assertEquals(8.1, LabelParser.parseBest(listOf(spanish), hint = live)!!.nutrients.protein, 0.001)
    }
}
