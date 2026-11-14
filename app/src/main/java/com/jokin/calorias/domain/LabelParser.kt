package com.jokin.calorias.domain

import java.text.Normalizer
import kotlin.math.abs

/** Box of a recognised text line, in any pixel space where y grows downwards. */
data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top

    fun union(o: TextBox) = TextBox(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

data class TextLine(val text: String, val box: TextBox)

/**
 * [unreadMacros] were not found on the label; their value in [nutrients] is a placeholder 0.
 * [kcalFromMacros]: the kcal were not read either, they are 4·protein + 4·carbs + 9·fat.
 */
data class LabelReading(
    val nutrients: Nutrients,
    val servingGrams: Double?,
    val unreadMacros: Set<Macro> = emptySet(),
    val kcalFromMacros: Boolean = false,
)

/**
 * Reads an EU nutrition table from OCR lines without any network.
 *
 * Layout: every line holding a nutrient keyword (in several languages, and despite a misread
 * letter) starts a block; label lines without keyword ("Carbono" under "Hidratos de") extend it.
 * Lines of numbers are grouped into columns, and each column is paired with the blocks top to
 * bottom (see [align]). Numbers on the keyword's own line count too, which also covers labels
 * printed as running text ("grasas 3,2 g, de las cuales saturadas 1,1 g"). Within a block the
 * value comes from the "per 100 g" column: the one under the "100 g" header, or the first one.
 *
 * Reading: OCR misreads numbers in predictable ways ("7,0g" comes back as "7,09", "1,3 g" as
 * "13"), so each number yields a few possible readings with a cost, and the cheapest combination
 * that obeys the table's own arithmetic wins: kcal match 4·protein + 4·carbs + 9·fat, saturates
 * fit within fat, sugars within carbs, and nothing per 100 g weighs more than 100 g.
 */
object LabelParser {
    // OTHER rows (sodium, starch, vitamins...) are not kept, but they hold their own values in the layout.
    private enum class Field { ENERGY, FAT, SATURATED, CARBS, SUGARS, FIBER, PROTEIN, SALT, OTHER }

    // Earlier entries win where matches overlap: rows that are not kept first, then sub-rows before
    // their parents ("grasas saturadas" must not be read as "grasas"). The lookbehinds keep
    // "monoinsaturadas", "unsaturated" and "ungesättigte" out of saturates.
    private val KEYWORDS = listOf(
        Field.OTHER to Regex(
            """insatur|unsatur|ungesattigt|onverzadigd|polyol|poliol|almidon|starch|amido|amidon|starke|\b(sodio|sodium|natrium)\b|""" +
                """colesterol|cholesterol|calci|vitamin|\b(hierro|iron|fer|ferro)\b|potas|magnes|fosforo|phosph|\btrans\b|omega|lactos|alcohol""",
        ),
        // Besides the Western European languages, the Central European ones some Lidl and Aldi packs carry.
        Field.SATURATED to Regex("""(?<!in|un)satur|(?<!un)gesattigt|(?<!on)verzadigd|(?<!ne)nasycen|nasicen|telitett"""),
        Field.SUGARS to Regex("""azucar|acucar|sugar|sucre|zucker|zuccher|suiker|cukr|zahar|sladkor|cukor"""),
        Field.FIBER to Regex("""fibra|fibre|fiber|ballaststoff|vezel|vlaknin|rostanyag|blonnik"""),
        Field.PROTEIN to Regex("""protein|eiwei|eiwit|bilkovin|bielkovin|beljakovin|feherje|bialk"""),
        Field.SALT to Regex("""\b(sal|salt|sel|salz|sale|zout|sare|sol|sul)\b"""),
        Field.CARBS to Regex("""hidrato|carbohidrat|carbohydrat|glucid|kohlenhydrat|carboidrat|koolhydrat|sacharid|ogljikov|szenhidrat|weglowodan"""),
        Field.FAT to Regex("""grasa|\bfat\b|gordura|lipid|matieres grasses|\bfett\b|grassi|\bvet(ten)?\b|\btuky\b|grasim|mascob|\bzsir|tluszcz"""),
        Field.ENERGY to Regex("""energ|calori|brennwert"""),
    )
    // Names checked letter by letter when no keyword matched; short names are left out, too easy to hit by chance.
    private val WORD = Regex("""[a-z]{6,}""")
    private val SPELLINGS = listOf(
        Field.SATURATED to listOf("saturadas", "saturados", "saturates", "saturated", "saturees", "gesattigte", "verzadigde"),
        Field.SUGARS to listOf("azucares", "acucares", "sugars", "sucres", "zuccheri", "suikers"),
        Field.FIBER to listOf("alimentaria", "ballaststoffe", "vezels"),
        Field.PROTEIN to listOf("proteinas", "proteines", "proteins", "protein", "proteine", "eiwitten"),
        Field.CARBS to listOf("hidratos", "carbohidratos", "carbohydrate", "carbohydrates", "glucidos", "glucides", "kohlenhydrate", "carboidrati", "koolhydraten"),
        Field.FAT to listOf("grasas", "lipidos", "gordura", "matieres", "grasses", "grassi"),
        Field.ENERGY to listOf("energia", "energie", "energy", "energetico", "energetique", "brennwert"),
    )

    private val SUB_ROWS = mapOf(Field.FAT to setOf(Field.SATURATED, Field.OTHER), Field.CARBS to setOf(Field.SUGARS, Field.OTHER))

    // A unit may be followed by anything but a letter ("2113kJ/505kcal"); a bare number must end
    // cleanly, which stops "0,5gr" from matching as a bare "0" followed by text.
    private val NUMBER = Regex("""(?<![a-z\d.,])(<\s*)?(\d+(?:[.,]\d+)?)(?:\s*(kcal|kca[il1]?|kj|mg|gr|g|ml|%)(?![a-z])|(?![a-z\d]|[.,]\d))""")

    private val TWO_DECIMALS_THEN_8_OR_9 = Regex("""\d+[.,]\d[89]""")

    // A stray mark OCR glues to the front of a value cell ("t60g", "¿26g"); only cut in value lines,
    // where words like "B12" or "E471" do not occur.
    private val STRAY_PREFIX = Regex("""(?<![a-z\d])[a-z](?=\d)""")

    // OCR reads a separate unit "g" as "9" or "8" ("0,08 9", "100 9") and a leading "0" as "o"
    // ("o,5 g"). Both are replaced one char for one, so offsets keep mapping to x.
    private val SPACED_G = Regex("""(?<=\d\s{1,3})[98](?![\d.,%])""")
    private val LETTER_ZERO = Regex("""(?<![a-z\d])o(?=[.,]\d)""")

    // Some labels print the unit before the value ("g 3,4"), which OCR may read as "9 34".
    private val LEADING_UNIT = Regex("""^(\s*)[g9](?=\s+\d+(?:[.,]\d+)?\s*$)""")

    private val HEADER_100 = Regex("""(?<!\d)100\s*(g|ml)\b""")
    private val SERVING = Regex("""(racion|porcion|serving|portion|dose)\D{0,20}?(\d+(?:[.,]\d+)?)\s*(g|ml)\b""")

    // UK labels put the size before the word: "Per 30g serving".
    private val SERVING_SIZE_FIRST = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml)\s+(serving|portion)\b""")
    private val KJ_WORD = Regex("""\bkj\b""")
    private const val KJ_PER_KCAL = 4.184

    // Costs of reading a number some other way than as printed (see [readings]).
    private const val COST_G_AS_9 = 1.0
    private const val COST_G_AS_DIGIT_ON_THIS_LABEL = 0.2
    private const val COST_LOST_SEPARATOR = 1.5
    private const val COST_OTHER_COLUMN = 1.0
    private const val COST_FROM_KJ = 0.2
    private const val COST_ABSENT = 3.0
    private const val AGREEMENT_BONUS = 0.5
    private const val COST_HINT = 0.3

    // Extras have no kcal to vouch for a corrected reading, and on real labels such guesses were
    // wrong more often than right: past this cost an extra is left blank for the user to fill in.
    private const val MAX_EXTRA_COST = 0.3

    // Row alignment, in line heights: how far a value may sit from its label, and what leaving one out costs.
    private const val MAX_ROW_OFFSET = 1.5
    private const val COST_UNPLACED_VALUE = 1.0
    private const val COST_EXTRA_ROW = 0.3
    private const val COLUMN_SPREAD = 2.5

    private enum class Unit { KCAL, KJ, MG, PERCENT, OTHER }

    private class Token(val raw: String, val belowLimit: Boolean, val unit: Unit, val x: Double, val unitPrinted: Boolean)

    private class Candidate(val value: Double, val cost: Double)

    /** A normalised line: the text the regexes run on, with offsets still mapping to x. */
    private class Line(val source: TextLine) {
        val text = normalize(source.text)
        val keywords: List<Pair<Field, IntRange>> = keywordsIn(text)
    }

    /** [inline]: not the last keyword on its line, so the values to its right belong to a later one. */
    private class Block(val field: Field, val lines: MutableList<Line>, val inline: Boolean = false) {
        val top get() = lines.minOf { it.source.box.top }
        val bottom get() = lines.maxOf { it.source.box.bottom }
        val left get() = lines.minOf { it.source.box.left }
        val tokens = mutableListOf<Token>()
        val valueLines = mutableListOf<Line>()
    }

    /** Null when the label yields neither energy nor the three macros. */
    fun parse(lines: List<TextLine>): LabelReading? = parseBest(listOf(lines))

    /**
     * Reconciles several OCR passes of the same label: a reading they agree on is preferred. [hint]
     * is an earlier reading of the same label (the camera preview's) that votes like one more pass
     * and fills in what the passes missed, such as kcal cut off at the edge of the photo.
     */
    fun parseBest(passes: List<List<TextLine>>, hint: LabelReading? = null): LabelReading? {
        val perPass = passes.map(::candidatesOf) + listOfNotNull(hint?.let(::candidatesOf))
        val merged = Field.entries.associateWith { f -> merge(perPass.map { it[f].orEmpty() }) }
        val (chosen, kcalFromMacros) = solve(merged) ?: return null
        val text = passes.flatten().joinToString(" ") { normalize(it.text) }
        val servingSize = SERVING.find(text)?.groupValues?.get(2) ?: SERVING_SIZE_FIRST.find(text)?.groupValues?.get(1)
        val serving = servingSize?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 1.0..2000.0 }
        val extras = buildMap {
            chosen[Field.SUGARS]?.let { put(ExtraNutrient.SUGARS, it) }
            chosen[Field.FIBER]?.let { put(ExtraNutrient.FIBER, it) }
            chosen[Field.SATURATED]?.let { put(ExtraNutrient.SATURATED_FAT, it) }
            chosen[Field.SALT]?.let { put(ExtraNutrient.SALT, it) }
        }
        val unread = buildSet {
            if (Field.PROTEIN !in chosen) add(Macro.PROTEIN)
            if (Field.CARBS !in chosen) add(Macro.CARBS)
            if (Field.FAT !in chosen) add(Macro.FAT)
        }
        val nutrients = Nutrients(
            kcal = chosen.getValue(Field.ENERGY),
            protein = chosen[Field.PROTEIN] ?: 0.0,
            carbs = chosen[Field.CARBS] ?: 0.0,
            fat = chosen[Field.FAT] ?: 0.0,
            extras = extras,
        )
        return LabelReading(nutrients, serving ?: hint?.servingGrams, unread, kcalFromMacros)
    }

    /** Box around the nutrient lines, or null unless at least two different nutrients are visible. */
    fun tableBounds(lines: List<TextLine>): TextBox? {
        val blocks = blocksOf(lines.map(::Line).sortedBy { it.source.box.centerY })
        if (blocks.map { it.field }.distinct().size < 2) return null
        return blocks.flatMap { it.lines + it.valueLines }.map { it.source.box }.reduce(TextBox::union)
    }

    /** A previous reading as candidates, a little less trusted than a number read off this photo. */
    private fun candidatesOf(r: LabelReading): Map<Field, List<Candidate>> = buildMap {
        fun put(f: Field, v: Double?) = v?.let { put(f, listOf(Candidate(it, COST_HINT))) }
        if (!r.kcalFromMacros) put(Field.ENERGY, r.nutrients.kcal)
        if (Macro.PROTEIN !in r.unreadMacros) put(Field.PROTEIN, r.nutrients.protein)
        if (Macro.CARBS !in r.unreadMacros) put(Field.CARBS, r.nutrients.carbs)
        if (Macro.FAT !in r.unreadMacros) put(Field.FAT, r.nutrients.fat)
        put(Field.SATURATED, r.nutrients.extras[ExtraNutrient.SATURATED_FAT])
        put(Field.SUGARS, r.nutrients.extras[ExtraNutrient.SUGARS])
        put(Field.FIBER, r.nutrients.extras[ExtraNutrient.FIBER])
        put(Field.SALT, r.nutrients.extras[ExtraNutrient.SALT])
    }

    // ---- Layout: from lines to candidate values per field ----

    private fun candidatesOf(textLines: List<TextLine>): Map<Field, List<Candidate>> {
        val lines = textLines.map(::Line).sortedBy { it.source.box.centerY }
        val blocks = blocksOf(lines)
        if (blocks.isEmpty()) return emptyMap()
        val columnX = headerColumn(lines, blocks)
        val gluedG = gluedUnit(blocks)
        val result = mutableMapOf<Field, List<Candidate>>()
        for (field in Field.entries) {
            val block = blocks.firstOrNull { it.field == field && it.tokens.isNotEmpty() } ?: continue
            val list = if (field == Field.ENERGY) energyCandidates(block, columnX, gluedG) else gramCandidates(block, columnX, gluedG)
            if (list.isNotEmpty()) result[field] = list
        }
        return result
    }

    private fun blocksOf(lines: List<Line>): List<Block> {
        val blocks = mutableListOf<Block>()
        val valueLines = mutableListOf<Line>()
        val lineHeight = lines.map { it.source.box.height }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        for (line in lines) {
            when {
                line.keywords.size == 1 && blocks.lastOrNull()?.let { it.field == line.keywords[0].first && line.source.box.top - it.bottom <= lineHeight } == true ->
                    // "Grasas" / "Lípidos" on two lines: one row in two languages.
                    blocks.last().let { b -> b.lines += line; b.tokens += tokensIn(line, line.keywords[0].second.last + 1, line.text.length) }
                line.keywords.isNotEmpty() -> {
                    // Running text: each keyword takes the numbers up to the next keyword on the line.
                    val kept = line.keywords.filterIndexed { i, (f, range) -> !isParentOfNeighbour(line, i, f, range) && !isAsideOfNeighbour(line, i, f, range) }
                    kept.forEachIndexed { i, (field, range) ->
                        val end = kept.getOrNull(i + 1)?.second?.first ?: line.text.length
                        val block = Block(field, mutableListOf(line), inline = i < kept.size - 1)
                        block.tokens += tokensIn(line, range.last + 1, end)
                        blocks += block
                    }
                }
                isValueLine(line) -> valueLines += line
                else -> continuationOf(blocks, line, lineHeight)?.lines?.add(line)
            }
        }
        if (blocks.isEmpty()) return blocks
        val labelLeft = blocks.minOf { it.left }
        val h = lineHeight.coerceAtLeast(1)
        for (column in columnsOf(valueLines.filter { it.source.box.left > labelLeft }, h)) {
            for ((line, block) in align(column, blocks.filter { !it.inline }, h)) {
                block.tokens += tokensIn(line, 0, line.text.length, valueLine = true)
                block.valueLines += line
            }
        }
        return blocks.filter { it.field != Field.OTHER }
    }

    /**
     * Value lines grouped into the table's columns ("por 100 g", "por ración", "%IR") by their
     * horizontal centre. Each column is aligned with the labels on its own: in a photo taken at an
     * angle the columns of one row drift apart in height.
     */
    private fun columnsOf(lines: List<Line>, lineHeight: Int): List<List<Line>> {
        val columns = mutableListOf<MutableList<Line>>()
        fun center(l: Line) = (l.source.box.left + l.source.box.right) / 2.0
        for (line in lines.sortedBy { it.source.box.centerY }) {
            val nearest = columns.minByOrNull { c -> abs(c.sumOf(::center) / c.size - center(line)) }
            if (nearest != null && abs(nearest.sumOf(::center) / nearest.size - center(line)) <= COLUMN_SPREAD * lineHeight) nearest += line
            else columns += mutableListOf(line)
        }
        return columns
    }

    /**
     * Pairs a column's value lines with the nutrient blocks top to bottom. Matching in order rather than each
     * value to its nearest label survives the usual misalignments: values sitting half a row low
     * under multi-line labels, or a row whose value is missing. A block may take several rows
     * (energy often has kJ and kcal on two), at a small cost so that rows prefer a block of their own.
     */
    private fun align(rows: List<Line>, blocks: List<Block>, lineHeight: Int): List<Pair<Line, Block>> {
        val order = blocks.sortedBy { it.top }
        fun cost(v: Line, b: Block): Double? {
            val y = v.source.box.centerY
            val from = b.lines.first().source.box.centerY
            val to = b.lines.last().source.box.centerY
            val d = when {
                y < from -> from - y
                y > to -> y - to
                else -> 0
            }.toDouble() / lineHeight
            return d.takeIf { it <= MAX_ROW_OFFSET }
        }
        // best[i][j]: cheapest placement of the first i rows whose last placed row went to block j-1 (j = 0: none yet).
        val n = order.size
        val best = Array(rows.size + 1) { DoubleArray(n + 1) { Double.MAX_VALUE } }
        val from = Array(rows.size + 1) { IntArray(n + 1) { -1 } }
        val placed = Array(rows.size + 1) { BooleanArray(n + 1) }
        best[0][0] = 0.0
        for (i in rows.indices) {
            for (j in 0..n) {
                val here = best[i][j]
                if (here == Double.MAX_VALUE) continue
                if (here + COST_UNPLACED_VALUE < best[i + 1][j]) {
                    best[i + 1][j] = here + COST_UNPLACED_VALUE
                    from[i + 1][j] = j
                    placed[i + 1][j] = false
                }
                for (k in maxOf(j, 1)..n) {
                    val m = cost(rows[i], order[k - 1]) ?: continue
                    val c = here + m + if (k == j) COST_EXTRA_ROW else 0.0
                    if (c < best[i + 1][k]) {
                        best[i + 1][k] = c
                        from[i + 1][k] = j
                        placed[i + 1][k] = true
                    }
                }
            }
        }
        var j = (0..n).minBy { best[rows.size][it] }
        val pairs = mutableListOf<Pair<Line, Block>>()
        for (i in rows.size downTo 1) {
            if (placed[i][j]) pairs += rows[i - 1] to order[j - 1]
            j = from[i][j]
        }
        return pairs
    }

    /** "Grasas saturadas", "Saturated fat", "Fett, davon gesättigte": the parent word only names the sub-row. */
    private fun isParentOfNeighbour(line: Line, i: Int, field: Field, range: IntRange): Boolean {
        val children = SUB_ROWS[field] ?: return false
        return neighbours(line, i, range).any { it in children }
    }

    /** "Sal (equivalente de sodio)": an aside about another nutrient does not take the row's value. */
    private fun isAsideOfNeighbour(line: Line, i: Int, field: Field, range: IntRange): Boolean =
        field == Field.OTHER && neighbours(line, i, range).any { it != Field.OTHER && SUB_ROWS[it]?.contains(Field.OTHER) != true }

    /** Fields named right before or after keyword [i] with no number in between. */
    private fun neighbours(line: Line, i: Int, range: IntRange): List<Field> =
        listOfNotNull(line.keywords.getOrNull(i - 1), line.keywords.getOrNull(i + 1)).filter { (_, r) ->
            line.text.substring(minOf(range.last, r.last) + 1, maxOf(range.first, r.first)).none(Char::isDigit)
        }.map { it.first }

    /**
     * Mostly numbers ("1046 kJ / 250 kcal", "3,2 g 1,0 g 4 %"), and not the "Por 100 g" header nor
     * a lone "9" or "8": that is a unit cell ("g") the OCR misread, not a value.
     */
    private fun isValueLine(line: Line): Boolean {
        val text = prepared(line.text)
        if (text.trim() in setOf("9", "8")) return false
        return NUMBER.containsMatchIn(text) && text.count(Char::isLetter) <= text.count(Char::isDigit) + 6 &&
            !HEADER_100.containsMatchIn(text) && !SERVING.containsMatchIn(text)
    }

    /** A wrapped label ("Hidratos de" / "Carbono", "Energía" / "(kJ/kcal)") continues the block above it. */
    private fun continuationOf(blocks: List<Block>, line: Line, lineHeight: Int): Block? {
        val last = blocks.lastOrNull() ?: return null
        val gap = line.source.box.top - last.bottom
        val aligned = abs(line.source.box.left - last.left) <= 3 * lineHeight
        return last.takeIf { aligned && gap <= lineHeight && last.lines.size < 4 }
    }

    /**
     * x of the "100 g" header: the header row is the one closest above the table, and when it
     * names 100 g twice ("por 100 g" and "%IR por 100 g") the per-100 g column comes first.
     */
    private fun headerColumn(lines: List<Line>, blocks: List<Block>): Double? {
        val tableTop = blocks.minOf { it.top }
        val headers = lines.mapNotNull { l -> HEADER_100.find(prepared(l.text))?.let { l to xOf(l, it.range.first) } }
        val above = headers.filter { it.first.source.box.bottom <= tableTop + it.first.source.box.height }
        if (above.isEmpty()) return headers.firstOrNull()?.second
        val lowest = above.maxOf { it.first.source.box.top }
        val band = above.filter { lowest - it.first.source.box.top <= 3 * it.first.source.box.height }
        return band.minOf { it.second }
    }

    /**
     * How this label's "g" gets misread. [digit]: the 9 or 8 it turns into throughout, when most
     * gram values end in it with no unit read. [unitsPrinted]: most values do show their "g", so one
     * that does not and ends in "…,78" most likely had its "g" glued on as an 8 or 9.
     */
    private class GluedUnit(val digit: Char?, val unitsPrinted: Boolean)

    private fun gluedUnit(blocks: List<Block>): GluedUnit {
        val grams = blocks.filter { it.field != Field.ENERGY }.flatMap { it.tokens }.filter { it.unit == Unit.OTHER && !it.belowLimit }
        val bare = grams.filter { !it.unitPrinted }
        val digit = listOf('9', '8').firstOrNull { d ->
            val n = bare.count { it.raw.length >= 2 && it.raw.endsWith(d) }
            n >= 3 && n >= 0.4 * bare.size
        }
        return GluedUnit(digit, unitsPrinted = grams.size >= 4 && grams.count { it.unitPrinted } >= 0.5 * grams.size)
    }

    private fun tokensIn(line: Line, start: Int, end: Int, valueLine: Boolean = false): List<Token> {
        val text = prepared(line.text).let { if (valueLine) STRAY_PREFIX.replace(it, " ") else it }
        return NUMBER.findAll(text.substring(0, end), start).mapNotNull { m ->
            val unit = when (m.groupValues[3]) {
                "kj" -> Unit.KJ
                "", "g", "gr", "ml" -> Unit.OTHER
                "mg" -> Unit.MG
                "%" -> Unit.PERCENT
                else -> Unit.KCAL
            }
            Token(m.groupValues[2], m.groupValues[1].isNotEmpty(), unit, xOf(line, m.range.first), m.groupValues[3].isNotEmpty())
        }.toList()
    }

    /** The column's token first, then the others: OCR sometimes drops a cell and shifts the row. */
    private fun ordered(tokens: List<Token>, columnX: Double?): List<Token> =
        if (columnX == null) tokens.sortedBy { it.x } else tokens.sortedBy { abs(it.x - columnX) }

    private fun gramCandidates(block: Block, columnX: Double?, gluedG: GluedUnit): List<Candidate> {
        val tokens = ordered(block.tokens.filter { it.unit == Unit.OTHER || it.unit == Unit.MG }, columnX)
        return tokens.take(2).flatMapIndexed { i, t ->
            readings(t, gluedG).map { c ->
                val grams = if (t.unit == Unit.MG) c.value / 1000 else c.value
                Candidate(grams, c.cost + i * COST_OTHER_COLUMN + rarity(block.field, grams))
            }
        }.filter { it.value <= 100.0 }
    }

    /**
     * Cost of a value few foods have. Extras have no kcal to check them against, so this is what
     * tells "15 g" of salt from "1,5 g" with the comma lost; a printed "14,5 g" has no other reading.
     */
    private fun rarity(field: Field, grams: Double): Double = when (field) {
        Field.SALT -> if (grams > 10) 2.0 else 0.0
        Field.FIBER -> if (grams > 20) 2.0 else 0.0
        Field.SATURATED -> if (grams > 40) 1.0 else 0.0
        Field.PROTEIN -> if (grams > 50) 2.0 else if (grams > 40) 1.0 else 0.0
        else -> 0.0
    }

    private fun energyCandidates(block: Block, columnX: Double?, gluedG: GluedUnit): List<Candidate> {
        val out = mutableListOf<Candidate>()
        ordered(block.tokens.filter { it.unit == Unit.KCAL }, columnX).take(2).forEachIndexed { i, t ->
            readings(t, gluedG).forEach { out += Candidate(it.value, it.cost + i * COST_OTHER_COLUMN) }
        }
        ordered(block.tokens.filter { it.unit == Unit.KJ }, columnX).take(2).forEachIndexed { i, t ->
            out += Candidate(kilojoules(t.raw) / KJ_PER_KCAL, COST_FROM_KJ + i * COST_OTHER_COLUMN)
        }
        val bare = ordered(block.tokens.filter { it.unit == Unit.OTHER }, columnX)
        // "Energía (kJ/kcal) 1046/250": the kcal figure is the one paired with its kJ equivalent.
        val byX = bare.sortedBy { it.x }
        byX.zipWithNext().filter { (a, b) -> value(b) > 0 && value(a) / value(b) in 4.0..4.3 }
            .forEachIndexed { i, (_, b) -> out += Candidate(value(b), i * COST_OTHER_COLUMN) }
        val mentionsKcal = block.lines.any { "kcal" in it.text || "kal)" in it.text }
        val mentionsKj = block.lines.any { KJ_WORD.containsMatchIn(it.text) }
        if (mentionsKcal && !mentionsKj) bare.take(2).forEachIndexed { i, t -> readings(t, gluedG).forEach { out += Candidate(it.value, it.cost + i * COST_OTHER_COLUMN) } }
        return out.filter { it.value in 0.0..950.0 }
    }

    // ---- Reading numbers ----

    /**
     * The number as printed, plus what it was likely printed as when the OCR got it wrong. On a
     * label where [gluedG] shows how its "g" gets misread, stripping that digit is the likely reading.
     */
    private fun readings(t: Token, gluedG: GluedUnit): List<Candidate> {
        if (t.belowLimit) return listOf(Candidate(0.0, 0.0))
        if (t.unit != Unit.OTHER) return listOf(Candidate(number(t.raw), 0.0))
        val systematic = !t.unitPrinted && (
            (gluedG.digit != null && t.raw.endsWith(gluedG.digit)) ||
                (gluedG.unitsPrinted && TWO_DECIMALS_THEN_8_OR_9.matches(t.raw))
            )
        // Nobody prints "09 g": a leading zero means the comma after it was lost.
        val leadingZero = t.raw.length >= 2 && t.raw[0] == '0' && t.raw[1].isDigit()
        val out = mutableListOf(Candidate(number(t.raw), if (systematic) COST_G_AS_9 else if (leadingZero) COST_ABSENT else 0.0))
        // "7,09" / "139": a glued "g" read as a 9 (or, on some labels, an 8).
        val stripCost = if (systematic) COST_G_AS_DIGIT_ON_THIS_LABEL else COST_G_AS_9
        val withoutG = if (!t.unitPrinted && t.raw.length >= 2 && (systematic || t.raw.endsWith('9'))) t.raw.dropLast(1).trimEnd(',', '.') else null
        withoutG?.let { out += Candidate(number(it), stripCost) }
        // "13" for "1,3": the decimal comma got lost.
        for ((raw, base) in listOfNotNull(t.raw to (if (systematic) COST_G_AS_9 else 0.0), withoutG?.let { it to stripCost })) {
            if (raw.length >= 2 && raw.all(Char::isDigit)) {
                val lost = if (raw[0] == '0') COST_G_AS_DIGIT_ON_THIS_LABEL else COST_LOST_SEPARATOR
                out += Candidate(number(raw.dropLast(1) + "." + raw.last()), base + lost)
            }
        }
        return out.distinctBy { it.value }
    }

    private fun value(t: Token) = if (t.belowLimit) 0.0 else number(t.raw)

    private fun number(raw: String) = raw.replace(',', '.').toDouble()

    /** "1.046 kJ" uses a thousands dot; energy per 100 g never needs decimals in kJ. */
    private fun kilojoules(raw: String) = if (Regex("""\d{1,3}[.,]\d{3}""").matches(raw)) raw.filter(Char::isDigit).toDouble() else number(raw)

    // ---- Choosing ----

    /** Same value from several passes is worth more than one pass's guess. */
    private fun merge(lists: List<List<Candidate>>): List<Candidate> {
        val all = lists.flatMap { list -> list.groupBy { it.value }.map { (_, cs) -> cs.minBy { it.cost } } }
        return all.groupBy { Math.round(it.value * 100) }
            .map { (_, cs) -> Candidate(cs.first().value, cs.minOf { it.cost } - AGREEMENT_BONUS * (cs.size - 1)) }
            .sortedBy { it.cost }
    }

    /** The values chosen, and whether the kcal had to come from the macros. */
    private fun solve(c: Map<Field, List<Candidate>>): Pair<Map<Field, Double>, Boolean>? {
        fun options(f: Field) = c[f].orEmpty().take(6).map<Candidate, Candidate?> { it } + null
        var best: Map<Field, Double>? = null
        var bestCost = Double.MAX_VALUE
        for (e in options(Field.ENERGY)) for (p in options(Field.PROTEIN)) for (cb in options(Field.CARBS)) for (f in options(Field.FAT)) {
            val grams = listOfNotNull(p, cb, f).sumOf { it.value }
            if (grams > 102) continue
            var cost = listOf(e, p, cb, f).sumOf { it?.cost ?: COST_ABSENT }
            cost += energyMismatch(e?.value, p?.value, cb?.value, f?.value)
            if (cost < bestCost) {
                bestCost = cost
                best = buildMap {
                    e?.let { put(Field.ENERGY, it.value) }
                    p?.let { put(Field.PROTEIN, it.value) }
                    cb?.let { put(Field.CARBS, it.value) }
                    f?.let { put(Field.FAT, it.value) }
                }
            }
        }
        val core = best ?: return null
        val macros = listOf(Field.PROTEIN, Field.CARBS, Field.FAT)
        val energy = core[Field.ENERGY]
            ?: if (macros.all { it in core }) 4 * core.getValue(Field.PROTEIN) + 4 * core.getValue(Field.CARBS) + 9 * core.getValue(Field.FAT) else return null
        val total = macros.sumOf { core[it] ?: 0.0 }
        fun extra(f: Field, limit: Double?) =
            c[f].orEmpty().firstOrNull { limit == null || it.value <= limit + 0.1 }?.takeIf { it.cost <= MAX_EXTRA_COST }?.value
        // A sub-row must fit in its parent; with the parent unread, in what the kcal leave for it.
        fun room(parent: Field) = core[parent] ?: implied(parent, core, energy) ?: (100 - total)
        val values = core + (Field.ENERGY to energy) + listOfNotNull(
            extra(Field.SATURATED, room(Field.FAT))?.let { Field.SATURATED to it },
            extra(Field.SUGARS, room(Field.CARBS))?.let { Field.SUGARS to it },
            extra(Field.FIBER, 100 - total)?.let { Field.FIBER to it },
            extra(Field.SALT, 100 - total)?.let { Field.SALT to it },
        )
        return values to (Field.ENERGY !in core)
    }

    /** Grams of the one unread macro that would make up the kcal, with room for rounding; null unless exactly that one is missing. */
    private fun implied(missing: Field, core: Map<Field, Double>, kcal: Double): Double? {
        val factors = mapOf(Field.PROTEIN to 4, Field.CARBS to 4, Field.FAT to 9)
        if (factors.keys.any { it != missing && it !in core }) return null
        val rest = factors.entries.filter { it.key != missing }.sumOf { (f, k) -> core.getValue(f) * k }
        return ((kcal - rest) / factors.getValue(missing)).coerceAtLeast(0.0) * 1.15 + 1
    }

    /**
     * Cost of kcal disagreeing with the macros. Labels round and leave out fibre, polyols and
     * alcohol, so a few percent off is free; far off means one of the numbers was misread.
     */
    private fun energyMismatch(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?): Double {
        kcal ?: return 0.0
        val known = listOf(protein to 4, carbs to 4, fat to 9)
        val computed = known.sumOf { (v, k) -> (v ?: 0.0) * k }
        val missing = known.filter { it.first == null }
        return if (missing.isEmpty()) {
            val rel = abs(kcal - computed) / maxOf(kcal, 40.0)
            minOf(4.0, 12 * maxOf(0.0, rel - 0.08))
        } else {
            // With a macro missing, the rest must still fit under the kcal.
            if (computed > kcal * 1.1 + 10) 4.0 else 0.0
        }
    }

    // ---- Text ----

    private fun keywordsIn(text: String): List<Pair<Field, IntRange>> {
        val found = mutableListOf<Pair<Field, IntRange>>()
        fun free(range: IntRange) = found.none { (_, r) -> range.first <= r.last && r.first <= range.last }
        for ((field, re) in KEYWORDS) {
            for (m in re.findAll(text)) if (free(m.range)) found += field to m.range
        }
        for (word in WORD.findAll(text)) {
            if (!free(word.range)) continue
            misspelt(word.value)?.let { found += it to word.range }
        }
        // Several labels per row ("Energía/Energia", "Grasas/Lípidos"): one block per field.
        return found.sortedBy { it.second.first }.distinctBy { it.first }
    }

    /** The field whose name [word] is, give or take the letter or two OCR gets wrong ("catoohydrates", "proleine"). */
    private fun misspelt(word: String): Field? {
        val limit = if (word.length >= 9) 2 else 1
        return SPELLINGS.firstOrNull { (_, names) -> names.any { abs(it.length - word.length) <= limit && editDistance(it, word) <= limit } }?.first
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun prepared(text: String) = LETTER_ZERO.replace(SPACED_G.replace(LEADING_UNIT.replace(text, "$1 "), "g"), "0")

    /** OCR boxes are per line, so a position inside the line is estimated from the character offset. */
    private fun xOf(line: Line, index: Int): Double {
        val box = line.source.box
        return box.left + (box.right - box.left) * (index.toDouble() / line.text.length.coerceAtLeast(1))
    }

    // NFD then dropping the marks keeps one char per precomposed letter, so offsets still map to x.
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
}
