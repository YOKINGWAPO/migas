package com.jokin.calorias.domain

import java.text.Normalizer
import kotlin.math.abs

/** Box of a recognised text line, in any pixel space where y grows downwards. */
data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2

    fun union(o: TextBox) = TextBox(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

data class TextLine(val text: String, val box: TextBox)

data class LabelReading(val nutrients: Nutrients, val servingGrams: Double?)

/**
 * Reads an EU nutrition table from OCR lines without any network. Lines are regrouped into table
 * rows by position (OCR often splits a row into one line per column), each row is matched by its
 * keyword, and the value comes from the "per 100 g" column: the one under the "100 g" header, or
 * the first column when there is no header (EU labels put per-100 g first).
 */
object LabelParser {
    private enum class Field { ENERGY, FAT, SATURATED, CARBS, SUGARS, FIBER, PROTEIN, SALT }

    // Sub-rows go before their parents: "grasas saturadas" must not be read as "grasas".
    private val KEYWORDS = listOf(
        Field.SATURATED to Regex("""saturad|saturat|satures"""),
        Field.SUGARS to Regex("""azucar|sugar|acucar|sucre"""),
        Field.FIBER to Regex("""fibra|fibre|fiber"""),
        Field.PROTEIN to Regex("""protein"""),
        Field.SALT to Regex("""\b(sal|salt|sel)\b"""),
        Field.CARBS to Regex("""hidrato|carbohidrat|carbohydrat|glucid"""),
        Field.FAT to Regex("""grasa|\bfat\b|gordura|lipid|matieres grasses"""),
        Field.ENERGY to Regex("""energ|calori"""),
    )

    private val NUMBER = Regex("""(?<![a-z\d.,])(<\s*)?(\d+(?:[.,]\d+)?)\s*(kca[il1]|kj|mg|g|ml|%)?(?![a-z])""")
    private val HEADER_100 = Regex("""\b100\s*(g|ml)\b""")
    private val SERVING = Regex("""(racion|porcion|serving|portion|dose)\D{0,20}?(\d+(?:[.,]\d+)?)\s*(g|ml)\b""")

    // UK labels put the size before the word: "Per 30g serving".
    private val SERVING_SIZE_FIRST = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml)\s+(serving|portion)\b""")
    private const val KJ_PER_KCAL = 4.184

    private enum class Unit { KCAL, KJ, MG, PERCENT, OTHER }

    private class Token(val value: Double, val unit: Unit, val x: Double)

    private class Row(val lines: List<TextLine>) {
        val texts = lines.map { normalize(it.text) }
        val field: Field? = KEYWORDS.firstOrNull { (_, re) -> texts.any(re::containsMatchIn) }?.first
    }

    /** Null when no energy value can be found: without kcal the reading is not worth prefilling. */
    fun parse(lines: List<TextLine>): LabelReading? {
        val rows = rowsOf(lines)
        val columnX = lines.firstNotNullOfOrNull { l -> HEADER_100.find(normalize(l.text))?.let { xOf(l, normalize(l.text), it.range.first) } }

        val kcal = energyKcal(rows, columnX)?.takeIf { it in 0.0..950.0 } ?: return null
        fun grams(f: Field): Double? = rows.firstOrNull { it.field == f }?.let { valueOf(it, f, columnX) }?.takeIf { it in 0.0..100.0 }

        val extras = buildMap {
            grams(Field.SUGARS)?.let { put(ExtraNutrient.SUGARS, it) }
            grams(Field.FIBER)?.let { put(ExtraNutrient.FIBER, it) }
            grams(Field.SATURATED)?.let { put(ExtraNutrient.SATURATED_FAT, it) }
            grams(Field.SALT)?.let { put(ExtraNutrient.SALT, it) }
        }
        val nutrients = Nutrients(kcal, grams(Field.PROTEIN) ?: 0.0, grams(Field.CARBS) ?: 0.0, grams(Field.FAT) ?: 0.0, extras)
        val text = lines.joinToString(" ") { normalize(it.text) }
        val servingSize = SERVING.find(text)?.groupValues?.get(2) ?: SERVING_SIZE_FIRST.find(text)?.groupValues?.get(1)
        val serving = servingSize?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 1.0..2000.0 }
        return LabelReading(nutrients, serving)
    }

    /** Box around the nutrient rows, or null unless at least two different nutrients are visible. */
    fun tableBounds(lines: List<TextLine>): TextBox? {
        val rows = rowsOf(lines).filter { it.field != null }
        if (rows.mapNotNull { it.field }.distinct().size < 2) return null
        return rows.flatMap { it.lines }.map { it.box }.reduce(TextBox::union)
    }

    private fun rowsOf(lines: List<TextLine>): List<Row> {
        val groups = mutableListOf<MutableList<TextLine>>()
        for (line in lines.sortedBy { it.box.centerY }) {
            val anchor = groups.lastOrNull()?.first()?.box
            if (anchor != null && line.box.centerY in anchor.top..anchor.bottom) groups.last() += line else groups += mutableListOf(line)
        }
        return groups.map { g -> Row(g.sortedBy { it.box.left }) }
    }

    /** Energy may wrap: "1046 kJ" on the keyword row and "250 kcal" on the next one, which has no keyword. */
    private fun energyKcal(rows: List<Row>, columnX: Double?): Double? {
        val i = rows.indexOfFirst { it.field == Field.ENERGY }
        if (i < 0) return null
        val energyRows = listOfNotNull(rows[i], rows.getOrNull(i + 1)?.takeIf { it.field == null })
        val tokens = energyRows.flatMap { tokensAfterKeyword(it, Field.ENERGY) }
        pick(tokens.filter { it.unit == Unit.KCAL }, columnX)?.let { return it.value }
        pick(tokens.filter { it.unit == Unit.KJ }, columnX)?.let { return it.value / KJ_PER_KCAL }
        // "Energía (kJ/kcal) 1046/250": the kcal figure is the one paired with its kJ equivalent.
        val bare = tokens.filter { it.unit == Unit.OTHER }
        bare.zipWithNext().firstOrNull { (a, b) -> b.value > 0 && a.value / b.value in 4.0..4.3 }?.let { return it.second.value }
        val mentionsKcal = energyRows.any { r -> r.texts.any { "kcal" in it } }
        val mentionsKj = energyRows.any { r -> r.texts.any { Regex("""\bkj\b""").containsMatchIn(it) } }
        return if (mentionsKcal && !mentionsKj) pick(bare, columnX)?.value else null
    }

    private fun valueOf(row: Row, field: Field, columnX: Double?): Double? {
        val token = pick(tokensAfterKeyword(row, field).filter { it.unit != Unit.PERCENT && it.unit != Unit.KCAL && it.unit != Unit.KJ }, columnX)
            ?: return null
        return if (token.unit == Unit.MG) token.value / 1000 else token.value
    }

    private fun pick(tokens: List<Token>, columnX: Double?): Token? =
        if (columnX == null) tokens.minByOrNull { it.x } else tokens.minByOrNull { abs(it.x - columnX) }

    /** Numbers before the keyword (or on lines left of it) are not values: "Vitamina B12", "100 g de". */
    private fun tokensAfterKeyword(row: Row, field: Field): List<Token> {
        val re = KEYWORDS.first { it.first == field }.second
        // -1 on a continuation row (wrapped energy): every number on it counts.
        val keyLine = row.texts.indexOfFirst(re::containsMatchIn)
        return row.lines.indices.filter { it >= keyLine }.flatMap { i ->
            val text = row.texts[i]
            val start = if (i == keyLine) re.find(text)!!.range.last + 1 else 0
            NUMBER.findAll(text, start).mapNotNull { m -> tokenOf(m, row.lines[i], text) }.toList()
        }
    }

    private fun tokenOf(m: MatchResult, line: TextLine, text: String): Token? {
        val unit = when (m.groupValues[3]) {
            "kj" -> Unit.KJ
            "", "g", "ml" -> Unit.OTHER
            "mg" -> Unit.MG
            "%" -> Unit.PERCENT
            else -> Unit.KCAL
        }
        val raw = m.groupValues[2]
        // "1.046 kJ" uses a thousands dot; energy per 100 g never needs decimals in kJ.
        val number = if (unit == Unit.KJ && Regex("""\d{1,3}[.,]\d{3}""").matches(raw)) raw.filter(Char::isDigit) else raw.replace(',', '.')
        val value = number.toDoubleOrNull() ?: return null
        val belowLimit = m.groupValues[1].isNotEmpty()
        // EU labels may write "<0,5 g" or "0 g" for negligible amounts; both mean zero.
        return Token(if (belowLimit) 0.0 else value, unit, xOf(line, text, m.range.first))
    }

    /** OCR boxes are per line, so a position inside the line is estimated from the character offset. */
    private fun xOf(line: TextLine, text: String, index: Int): Double {
        val width = line.box.right - line.box.left
        return line.box.left + width * (index.toDouble() / text.length.coerceAtLeast(1))
    }

    // NFD then dropping the marks keeps one char per precomposed letter, so offsets still map to x.
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
}
