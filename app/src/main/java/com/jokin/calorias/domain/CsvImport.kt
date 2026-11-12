package com.jokin.calorias.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Reads a food diary or a weight history from a CSV made by another app or by hand. Columns are
 * found by name (Spanish or English, any order); separator, decimal mark and day/month order are
 * detected. Every bad row is reported with its line instead of being dropped silently.
 */
object CsvImport {
    const val MAX_CHARS = 5_000_000
    const val MAX_ROWS = 50_000
    private const val MAX_NAME = 120
    // A row without grams is stored like a quick entry: one 1000 g serving under the 950 kcal/100 g food limit.
    private val KCAL_RANGE = 0.0..9_500.0
    private val MACRO_RANGE = 0.0..1_000.0
    private val GRAMS_RANGE = 0.1..5_000.0
    private val KG_RANGE = 20.0..400.0
    private const val MAX_KCAL_PER_100G = 950.0
    private val EARLIEST = LocalDate.of(2000, 1, 1)

    enum class Column { DATE, TIME, MEAL, NAME, BRAND, GRAMS, KCAL, PROTEIN, CARBS, FAT, WEIGHT }

    /** Header names after [normalizeHeader]; units in brackets are dropped before matching. */
    private val ALIASES: Map<Column, Set<String>> = mapOf(
        Column.DATE to setOf("fecha", "date", "dia", "day"),
        Column.TIME to setOf("hora", "time"),
        Column.MEAL to setOf("comida", "meal", "tipo_de_comida", "momento", "meal_type"),
        Column.NAME to setOf("alimento", "food", "nombre", "name", "producto", "product", "descripcion", "description", "food_name", "item"),
        Column.BRAND to setOf("marca", "brand"),
        Column.GRAMS to setOf("gramos", "grams", "g", "cantidad", "cantidad_g", "amount", "amount_g", "quantity", "quantity_g"),
        Column.KCAL to setOf("kcal", "calorias", "calories", "energia", "energy", "energia_kcal", "energy_kcal", "calorias_kcal"),
        Column.PROTEIN to setOf("proteinas", "proteinas_g", "proteina", "protein", "protein_g", "proteins"),
        Column.CARBS to setOf("hidratos", "hidratos_g", "carbohidratos", "carbohidratos_g", "carbs", "carbs_g", "carbohydrates", "carbohydrates_g"),
        Column.FAT to setOf("grasas", "grasas_g", "grasa", "fat", "fat_g", "fats", "lipidos"),
        Column.WEIGHT to setOf("peso", "peso_kg", "weight", "weight_kg", "kg", "body_weight"),
    )

    private val MEALS: Map<String, MealType> = buildMap {
        listOf("desayuno", "breakfast").forEach { put(it, MealType.BREAKFAST) }
        listOf("comida", "almuerzo", "lunch").forEach { put(it, MealType.LUNCH) }
        listOf("cena", "dinner", "supper").forEach { put(it, MealType.DINNER) }
        listOf("snack", "snacks", "merienda", "tentempie", "aperitivo", "picoteo", "media_manana", "otros", "other").forEach { put(it, MealType.SNACK) }
    }

    enum class FileProblem { EMPTY, TOO_BIG, TOO_MANY_ROWS, UNKNOWN_COLUMNS }

    class ImportException(val problem: FileProblem) : IllegalArgumentException(problem.name)

    enum class RowProblem { BAD_DATE, FUTURE_DATE, BAD_NUMBER, OUT_OF_RANGE, MISSING_NAME, MISSING_KCAL, TOO_MANY_KCAL_PER_100G }

    data class RowError(val line: Int, val problem: RowProblem, val column: Column?)

    /** [meal] is null when the file has none (or an unknown one) and no time to guess it from. */
    data class DiaryRow(
        val line: Int,
        val epochDay: Long,
        val time: LocalTime?,
        val meal: MealType?,
        val name: String,
        val brand: String?,
        val grams: Double?,
        val nutrients: Nutrients,
    )

    data class WeightRow(val line: Int, val epochDay: Long, val kg: Double)

    sealed interface Parsed {
        val errors: List<RowError>

        data class Diary(val rows: List<DiaryRow>, override val errors: List<RowError>) : Parsed
        data class Weights(val rows: List<WeightRow>, override val errors: List<RowError>) : Parsed
    }

    fun parse(text: String, today: LocalDate): Parsed {
        if (text.length > MAX_CHARS) throw ImportException(FileProblem.TOO_BIG)
        val clean = text.removePrefix("\uFEFF")
        val lines = clean.lines()
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        if (headerIndex < 0) throw ImportException(FileProblem.EMPTY)
        val separator = detectSeparator(lines[headerIndex])
        val records = splitRecords(clean, separator)
        val header = records.first().fields.map(::normalizeHeader)
        val columns = Column.entries.mapNotNull { col -> header.indexOfFirst { it in ALIASES.getValue(col) }.takeIf { it >= 0 }?.let { col to it } }.toMap()
        val body = records.drop(1).filter { r -> r.fields.any { it.isNotBlank() } }
        if (body.size > MAX_ROWS) throw ImportException(FileProblem.TOO_MANY_ROWS)
        val decimalComma = separator != ','
        val dayFirst = detectDayFirst(columns[Column.DATE]?.let { i -> body.mapNotNull { it.fields.getOrNull(i) } }.orEmpty())
        val reader = RowReader(columns, decimalComma, dayFirst, today)
        return when {
            Column.DATE in columns && Column.NAME in columns && (Column.KCAL in columns || hasAllMacros(columns)) -> reader.diary(body)
            Column.DATE in columns && Column.WEIGHT in columns -> reader.weights(body)
            else -> throw ImportException(FileProblem.UNKNOWN_COLUMNS)
        }
    }

    private fun hasAllMacros(columns: Map<Column, Int>) = listOf(Column.PROTEIN, Column.CARBS, Column.FAT).all { it in columns }

    private class RowReader(val columns: Map<Column, Int>, val decimalComma: Boolean, val dayFirst: Boolean, val today: LocalDate) {
        private class RowFailure(val problem: RowProblem, val column: Column?) : Exception()

        private fun Record.raw(col: Column): String? = columns[col]?.let { fields.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

        private fun Record.number(col: Column, range: ClosedFloatingPointRange<Double>): Double? {
            val raw = raw(col) ?: return null
            val value = parseNumber(raw, decimalComma) ?: throw RowFailure(RowProblem.BAD_NUMBER, col)
            if (value !in range) throw RowFailure(RowProblem.OUT_OF_RANGE, col)
            return value
        }

        private fun Record.day(): Long {
            val date = parseDate(raw(Column.DATE) ?: throw RowFailure(RowProblem.BAD_DATE, Column.DATE), dayFirst)
                ?: throw RowFailure(RowProblem.BAD_DATE, Column.DATE)
            if (date.isAfter(today)) throw RowFailure(RowProblem.FUTURE_DATE, Column.DATE)
            if (date.isBefore(EARLIEST)) throw RowFailure(RowProblem.BAD_DATE, Column.DATE)
            return date.toEpochDay()
        }

        private fun <T> collect(body: List<Record>, read: (Record) -> T): Pair<List<T>, List<RowError>> {
            val rows = mutableListOf<T>()
            val errors = mutableListOf<RowError>()
            body.forEach { r ->
                try {
                    rows += read(r)
                } catch (f: RowFailure) {
                    errors += RowError(r.line, f.problem, f.column)
                }
            }
            return rows to errors
        }

        fun diary(body: List<Record>): Parsed.Diary {
            val (rows, errors) = collect(body) { r ->
                val day = r.day()
                val name = r.raw(Column.NAME)?.let(::cleanText) ?: throw RowFailure(RowProblem.MISSING_NAME, Column.NAME)
                val protein = r.number(Column.PROTEIN, MACRO_RANGE)
                val carbs = r.number(Column.CARBS, MACRO_RANGE)
                val fat = r.number(Column.FAT, MACRO_RANGE)
                val kcal = r.number(Column.KCAL, KCAL_RANGE)
                    ?: if (protein != null && carbs != null && fat != null) protein * 4 + carbs * 4 + fat * 9
                    else throw RowFailure(RowProblem.MISSING_KCAL, Column.KCAL)
                val grams = r.raw(Column.GRAMS)?.let { stripUnit(it) }?.let { raw ->
                    val g = parseNumber(raw, decimalComma) ?: throw RowFailure(RowProblem.BAD_NUMBER, Column.GRAMS)
                    if (g !in GRAMS_RANGE) throw RowFailure(RowProblem.OUT_OF_RANGE, Column.GRAMS)
                    g
                }
                if (grams != null && kcal / grams * 100 > MAX_KCAL_PER_100G) throw RowFailure(RowProblem.TOO_MANY_KCAL_PER_100G, Column.KCAL)
                val time = r.raw(Column.TIME)?.let(::parseTime)
                val meal = r.raw(Column.MEAL)?.let { MEALS[normalizeHeader(it)] } ?: time?.let(MealType::guessFor)
                DiaryRow(r.line, day, time, meal, name, r.raw(Column.BRAND)?.let(::cleanText), grams,
                    Nutrients(kcal, protein ?: 0.0, carbs ?: 0.0, fat ?: 0.0))
            }
            return Parsed.Diary(rows, errors)
        }

        fun weights(body: List<Record>): Parsed.Weights {
            val (rows, errors) = collect(body) { r ->
                val kg = r.number(Column.WEIGHT, KG_RANGE) ?: throw RowFailure(RowProblem.BAD_NUMBER, Column.WEIGHT)
                WeightRow(r.line, r.day(), kg)
            }
            return Parsed.Weights(rows, errors)
        }
    }

    /** Ready-to-fill template with the column names of the app's language and two example rows. */
    fun diaryTemplate(english: Boolean): String = Csv.build(
        if (english) listOf("date", "meal", "food", "grams", "kcal", "protein_g", "carbs_g", "fat_g")
        else listOf("fecha", "comida", "alimento", "gramos", "kcal", "proteinas_g", "hidratos_g", "grasas_g"),
        listOf(
            listOf("2026-01-15", if (english) "breakfast" else "desayuno", if (english) "Oat flakes" else "Copos de avena", 60, 225.0, 8.1, 35.8, 4.2),
            listOf("2026-01-15", if (english) "dinner" else "cena", if (english) "Restaurant pizza" else "Pizza del restaurante", "", 850.0, "", "", ""),
        ),
    )

    fun weightTemplate(english: Boolean): String = Csv.build(
        if (english) listOf("date", "weight_kg") else listOf("fecha", "peso_kg"),
        listOf(listOf("2026-01-15", 82.4), listOf("2026-01-22", 81.9)),
    )

    internal data class Record(val line: Int, val fields: List<String>)

    private fun detectSeparator(header: String): Char =
        listOf(';', '\t', ',').maxBy { sep -> header.count { it == sep } }

    /** RFC 4180 records: quoted fields may hold the separator, doubled quotes and line breaks. */
    internal fun splitRecords(text: String, sep: Char): List<Record> {
        val records = mutableListOf<Record>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0
        fun endField() { fields += field.toString(); field.clear() }
        fun endRecord() {
            endField()
            records += Record(recordLine, fields.toList())
            fields.clear()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' && (inQuotes || field.isEmpty()) -> inQuotes = !inQuotes
                inQuotes -> { if (ch == '\n') line++; field.append(ch) }
                ch == sep -> endField()
                ch == '\r' -> {}
                ch == '\n' -> { endRecord(); line++; recordLine = line }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records.dropWhile { r -> r.fields.all { it.isBlank() } }
    }

    /** "Proteínas (g)" → "proteinas", "Energy kcal" → "energy_kcal". */
    internal fun normalizeHeader(raw: String): String =
        VoiceParser.normalize(raw.replace(Regex("\\(.*?\\)|\\[.*?]"), " "))
            .replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun cleanText(raw: String): String? =
        raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().take(MAX_NAME).takeIf { it.isNotEmpty() }

    private fun stripUnit(raw: String): String = raw.trim().removeSuffix("g").removeSuffix("G").trim()

    /** With a ';' or tab file a comma is the decimal mark; "1.234,5" and "1,234.5" are read by their last mark. */
    internal fun parseNumber(raw: String, decimalComma: Boolean): Double? {
        val s = raw.trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.any { it !in "0123456789.,-" }) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> if (decimalComma || s.count { it == ',' } == 1) s.replace(',', '.') else return null
            else -> s
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private val NUMERIC_DATE = Regex("""^(\d{1,4})[/.\-](\d{1,2})[/.\-](\d{1,4})""")

    /** True unless some date only makes sense month-first (13/01 can only be day-first; 01/13 only month-first). */
    private fun detectDayFirst(rawDates: List<String>): Boolean {
        var dayFirst = false
        var monthFirst = false
        rawDates.forEach { raw ->
            val m = NUMERIC_DATE.find(raw.trim()) ?: return@forEach
            val (a, b, c) = m.destructured
            if (a.length == 4) return@forEach
            if (c.length < 2) return@forEach
            if (a.toInt() > 12) dayFirst = true
            if (b.toInt() > 12) monthFirst = true
        }
        return dayFirst || !monthFirst
    }

    internal fun parseDate(raw: String, dayFirst: Boolean): LocalDate? {
        val m = NUMERIC_DATE.find(raw.trim()) ?: return null
        val (a, b, c) = m.destructured
        return try {
            when {
                a.length == 4 -> LocalDate.of(a.toInt(), b.toInt(), c.toInt())
                c.length == 4 || c.length == 2 -> {
                    val year = if (c.length == 2) 2000 + c.toInt() else c.toInt()
                    if (dayFirst) LocalDate.of(year, b.toInt(), a.toInt()) else LocalDate.of(year, a.toInt(), b.toInt())
                }
                else -> null
            }
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    private fun parseTime(raw: String): LocalTime? = try {
        LocalTime.parse(raw.trim().let { if (it.length == 4 && it[1] == ':') "0$it" else it }.take(5))
    } catch (e: DateTimeParseException) {
        null
    }
}
