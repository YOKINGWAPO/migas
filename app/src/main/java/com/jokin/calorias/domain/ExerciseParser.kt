package com.jokin.calorias.domain

data class ParsedExercise(val name: String, val sets: Int, val reps: Int, val weightKg: Double?)

/**
 * Offline parser for strength sets, in Spanish or English whatever the app's language:
 * "4x15 flexiones y 4 por 8 de dominadas", "cuatro series de quince repeticiones de flexiones",
 * "press banca 3x10 con 40 kilos", "bench press 3 sets of 10 with 60 kg". Weights are in kilos only.
 * A bare "20 flexiones" is one set of 20.
 */
object ExerciseParser {
    const val MAX_SETS = 50
    const val MAX_REPS = 1000
    const val MAX_WEIGHT_KG = 500.0

    private val SEPARATORS = Regex("""\s*(,|;|\by\b|\be\b|\bmas\b|\bluego\b|\bdespues\b|\band\b|\bplus\b|\bthen\b)\s*""")

    private val WORD_NUMBERS = mapOf(
        "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30,
        "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80,
        "noventa" to 90, "cien" to 100,
        // No English "once": the word is eleven in Spanish.
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90, "hundred" to 100,
    )

    private val WEIGHT = Regex("""(?:\b(?:con|with|at)\s+)?(\d+(?:\.\d+)?)\s*(?:kg|kilos?|kilogramos?|kilograms?)\b""")
    private val SETS_X_REPS = Regex("""(\d+)\s*(?:x|por|series?\s+de|veces|sets?\s+of|by|times)\s*(\d+)""")
    private val SINGLE_COUNT = Regex("""^(\d+)\s+""")
    private val FILLER = Regex(
        """\b(he hecho|hice|hecho|apunta|apuntar|anade|anadir|registra|registrar|hoy|repeticiones|repeticion|reps|rep|series|serie|""" +
            """i ve done|i have done|i did|did|done|log|add|today|repetitions|repetition|sets|set)\b"""
    )
    private val EDGE_PREPOSITIONS = Regex("""^(de|del|en|con|of|with|at)\s+|\s+(de|del|en|con|of|with|at)$""")

    fun parse(text: String): List<ParsedExercise> =
        VoiceParser.normalize(text).split(SEPARATORS).mapNotNull { parseSegment(withDigits(it)) }

    private fun withDigits(segment: String): String =
        segment.split(' ').joinToString(" ") { w -> WORD_NUMBERS[w]?.toString() ?: w }

    private fun parseSegment(segment: String): ParsedExercise? {
        var rest = segment
        val weight = WEIGHT.find(rest)?.let { m ->
            rest = rest.removeRange(m.range)
            m.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_WEIGHT_KG }
        }
        val sets: Int
        val reps: Int
        val setsReps = SETS_X_REPS.find(rest)
        if (setsReps != null) {
            sets = setsReps.groupValues[1].toInt()
            reps = setsReps.groupValues[2].toInt()
            rest = rest.removeRange(setsReps.range)
        } else {
            val single = SINGLE_COUNT.find(rest.trim()) ?: return null
            sets = 1
            reps = single.groupValues[1].toInt()
            rest = rest.trim().removeRange(single.range)
        }
        if (sets !in 1..MAX_SETS || reps !in 1..MAX_REPS) return null
        val name = cleanName(rest) ?: return null
        return ParsedExercise(name, sets, reps, weight)
    }

    private fun cleanName(raw: String): String? {
        var name = raw.replace(FILLER, " ").replace(Regex("""\s+"""), " ").trim()
        while (EDGE_PREPOSITIONS.containsMatchIn(name)) name = name.replace(EDGE_PREPOSITIONS, "").trim()
        if (name.isEmpty() || name.any { it.isDigit() }) return null
        return name.replaceFirstChar { it.uppercase() }.take(60)
    }
}
