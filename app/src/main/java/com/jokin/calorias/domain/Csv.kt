package com.jokin.calorias.domain

import java.util.Locale

/**
 * CSV for spreadsheets, with a UTF-8 BOM so accents survive. Spanish Excel expects ';' and ','
 * decimals; English Excel expects ',' and '.' decimals. Google Sheets detects both on import.
 */
object Csv {
    private const val BOM = "\uFEFF"

    fun build(header: List<String>, rows: List<List<Any?>>, english: Boolean = false): String = buildString {
        val sep = if (english) "," else ";"
        val locale = if (english) Locale.US else Locale.forLanguageTag("es-ES")
        append(BOM)
        appendLine(header.joinToString(sep) { escape(it, sep) })
        rows.forEach { row -> appendLine(row.joinToString(sep) { escape(format(it, locale), sep) }) }
    }

    private fun format(value: Any?, locale: Locale): String = when (value) {
        null -> ""
        is Double -> String.format(locale, "%.2f", value)
        is Float -> format(value.toDouble(), locale)
        else -> value.toString()
    }

    private fun escape(field: String, sep: String): String {
        val needsQuotes = field.contains(sep) || field.contains('"') || field.contains('\n') || field.contains('\r')
        // A leading = + or @ would run as a formula in a spreadsheet; '-' is kept for negative numbers.
        val safe = if (field.isNotEmpty() && field[0] in "=+@") "'$field" else field
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
