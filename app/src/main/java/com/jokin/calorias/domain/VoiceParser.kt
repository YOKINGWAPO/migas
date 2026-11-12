package com.jokin.calorias.domain

import java.text.Normalizer

enum class VoiceUnit {
    GRAM, KILOGRAM, MILLILITER, LITER, GLASS, CUP, TABLESPOON, TEASPOON, HANDFUL, SLICE, SERVING, UNIT;

    /** Grams for units that do not depend on the food; null when it does (a slice, a unit, a serving). */
    fun fixedGrams(quantity: Double, glassMl: Int): Double? = when (this) {
        GRAM, MILLILITER -> quantity
        KILOGRAM, LITER -> quantity * 1000
        GLASS -> quantity * glassMl
        CUP -> quantity * GenericMeasures.CUP_G
        TABLESPOON -> quantity * GenericMeasures.TABLESPOON_G
        TEASPOON -> quantity * GenericMeasures.TEASPOON_G
        HANDFUL -> quantity * GenericMeasures.HANDFUL_G
        SLICE, SERVING, UNIT -> null
    }
}

object GenericMeasures {
    const val TABLESPOON_G = 15.0
    const val TEASPOON_G = 5.0
    const val CUP_G = 240.0
    const val HANDFUL_G = 30.0
}

data class ParsedItem(val name: String, val quantity: Double, val unit: VoiceUnit)

data class ParsedVoice(val meal: MealType?, val items: List<ParsedItem>, val waterMl: Int)

/** Language of the spoken phrase: it picks the words; the grammar (quantity, unit, food) is shared. */
enum class VoiceLanguage {
    SPANISH, ENGLISH;

    companion object {
        /** The speech recogniser follows the app's language; anything but Spanish is spoken in English. */
        fun of(languageCode: String): VoiceLanguage = if (languageCode == "es") SPANISH else ENGLISH
    }
}

/**
 * Offline parser for phrases like "para desayunar 200 gramos de plátano y un yogur natural" or
 * "for breakfast 200 grams of banana and a natural yogurt".
 * It only handles quantity + unit + food; anything freer is the AI mode's job.
 */
object VoiceParser {
    private class Lexicon(
        val meals: List<Pair<MealType, Regex>>,
        val filler: Regex,
        val separators: Regex,
        val articles: Set<String>,
        val wordNumbers: Map<String, Double>,
        /** Checked before single numbers; each phrase is consumed whole. */
        val quantityPhrases: List<Pair<List<String>, Double>>,
        /** Words that multiply the number before them: "two hundred". */
        val multipliers: Map<String, Double>,
        val units: Map<String, VoiceUnit>,
        /** The word between unit and food: "200 gramos de arroz". */
        val connector: String,
        val water: String,
    )

    private fun unitTable(vararg entries: Pair<VoiceUnit, List<String>>): Map<String, VoiceUnit> =
        entries.flatMap { (unit, words) -> words.map { it to unit } }.toMap()

    private val SPANISH = Lexicon(
        meals = listOf(
            MealType.BREAKFAST to Regex("""\bdesayun\w*"""),
            MealType.LUNCH to Regex("""\b(almuerz\w*|almorz\w*|para comer|en la comida|de comida|a mediodia)\b"""),
            MealType.DINNER to Regex("""\b(cena|cenar|cene|cenado|en la cena|para cenar)\b"""),
            MealType.SNACK to Regex("""\b(merienda|merendar|merende|merendado|snack|picoteo|tentempie)\b"""),
        ),
        filler = Regex(
            """\b(para (desayunar|comer|cenar|merendar|almorzar)|(en|de|para) (el |la )?(desayuno|comida|cena|merienda|almuerzo)|""" +
                """(me )?he (comido|tomado|bebido|desayunado|cenado|almorzado|merendado)|""" +
                """comi|tome|bebi|desayune|cene|almorce|merende|hoy|esta (manana|tarde|noche)|apunta|apuntar|anade|anadir|registra|registrar)\b"""
        ),
        separators = Regex("""\s*(,|;|\by\b|\be\b|\bmas\b)\s*"""),
        articles = setOf("el", "la", "los", "las", "un", "una", "unos", "unas"),
        wordNumbers = mapOf(
            "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0,
            "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
            "medio" to 0.5, "media" to 0.5, "cien" to 100.0, "doscientos" to 200.0, "doscientas" to 200.0,
            "trescientos" to 300.0, "trescientas" to 300.0, "quinientos" to 500.0, "quinientas" to 500.0,
        ),
        quantityPhrases = listOf(
            listOf("un", "cuarto") to 0.25,
            listOf("una", "cuarto") to 0.25,
            listOf("un", "par", "de") to 2.0,
        ),
        multipliers = emptyMap(),
        units = unitTable(
            VoiceUnit.GRAM to listOf("g", "gr", "grs", "gramo", "gramos"),
            VoiceUnit.KILOGRAM to listOf("kg", "kilo", "kilos", "kilogramo", "kilogramos"),
            VoiceUnit.MILLILITER to listOf("ml", "mililitro", "mililitros"),
            VoiceUnit.LITER to listOf("l", "litro", "litros"),
            VoiceUnit.GLASS to listOf("vaso", "vasos"),
            VoiceUnit.CUP to listOf("taza", "tazas"),
            VoiceUnit.TABLESPOON to listOf("cucharada", "cucharadas"),
            VoiceUnit.TEASPOON to listOf("cucharadita", "cucharaditas"),
            VoiceUnit.HANDFUL to listOf("punado", "punados"),
            VoiceUnit.SLICE to listOf("rebanada", "rebanadas", "loncha", "lonchas"),
            VoiceUnit.SERVING to listOf("racion", "raciones", "plato", "platos", "porcion", "porciones"),
            VoiceUnit.UNIT to listOf("unidad", "unidades", "pieza", "piezas"),
        ),
        connector = "de",
        water = "agua",
    )

    // Apostrophes are gone after normalising, so "I've had" arrives as "i ve had".
    private val ENGLISH = Lexicon(
        meals = listOf(
            MealType.BREAKFAST to Regex("""\bbreakfast\w*"""),
            MealType.LUNCH to Regex("""\blunch\w*"""),
            MealType.DINNER to Regex("""\b(dinner|supper)\b"""),
            MealType.SNACK to Regex("""\bsnack\w*"""),
        ),
        filler = Regex(
            """\b(for (breakfast|lunch|dinner|supper|a snack|snack)|(at|with|in|during) (breakfast|lunch|dinner|supper)|as a snack|""" +
                """(i )?((have|ve|just) )*(had|have|ate|eaten|drank|drunk)|""" +
                """today|this (morning|afternoon|evening)|tonight|log|add|record|track)\b"""
        ),
        separators = Regex("""\s*(,|;|\band\b|\bplus\b)\s*"""),
        articles = setOf("the", "a", "an", "some", "my"),
        wordNumbers = mapOf(
            "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
            "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
            "half" to 0.5,
        ),
        quantityPhrases = listOf(
            listOf("half", "a") to 0.5,
            listOf("half", "an") to 0.5,
            listOf("a", "quarter") to 0.25,
            listOf("a", "couple", "of") to 2.0,
            listOf("a", "pair", "of") to 2.0,
            listOf("a", "dozen") to 12.0,
        ),
        multipliers = mapOf("hundred" to 100.0),
        units = unitTable(
            VoiceUnit.GRAM to listOf("g", "gr", "gram", "grams", "gramme", "grammes"),
            VoiceUnit.KILOGRAM to listOf("kg", "kilo", "kilos", "kilogram", "kilograms"),
            VoiceUnit.MILLILITER to listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres"),
            VoiceUnit.LITER to listOf("l", "liter", "liters", "litre", "litres"),
            VoiceUnit.GLASS to listOf("glass", "glasses"),
            VoiceUnit.CUP to listOf("cup", "cups", "mug", "mugs"),
            VoiceUnit.TABLESPOON to listOf("tablespoon", "tablespoons", "tbsp"),
            VoiceUnit.TEASPOON to listOf("teaspoon", "teaspoons", "tsp"),
            VoiceUnit.HANDFUL to listOf("handful", "handfuls"),
            VoiceUnit.SLICE to listOf("slice", "slices"),
            VoiceUnit.SERVING to listOf("serving", "servings", "portion", "portions", "plate", "plates", "bowl", "bowls"),
            VoiceUnit.UNIT to listOf("unit", "units", "piece", "pieces"),
        ),
        connector = "of",
        water = "water",
    )

    fun parse(text: String, glassMl: Int, language: VoiceLanguage = VoiceLanguage.SPANISH): ParsedVoice {
        val lexicon = when (language) {
            VoiceLanguage.SPANISH -> SPANISH
            VoiceLanguage.ENGLISH -> ENGLISH
        }
        val normalized = normalize(text)
        val meal = lexicon.meals.firstOrNull { it.second.containsMatchIn(normalized) }?.first
        val body = lexicon.filler.replace(normalized, " ")
        val items = mutableListOf<ParsedItem>()
        var waterMl = 0.0
        for (chunk in body.split(lexicon.separators)) {
            val item = parseChunk(chunk.trim(), lexicon) ?: continue
            if (item.name == lexicon.water || item.name.startsWith("${lexicon.water} ")) {
                waterMl += item.unit.fixedGrams(item.quantity, glassMl) ?: (item.quantity * glassMl)
            } else {
                items += item
            }
        }
        return ParsedVoice(meal, items, waterMl.toInt())
    }

    /** Lowercase without accents, so "plátano" and "platano" match and "ñ" becomes "n". */
    fun normalize(text: String): String {
        val stripped = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
        return stripped
            .replace(Regex("""(\d),(\d)"""), "$1.$2")
            .replace(Regex("""(\d)([a-z])"""), "$1 $2")
            .replace(Regex("""[^a-z0-9.,; ]"""), " ")
            .replace(Regex("""(?<!\d)\.|\.(?!\d)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun parseChunk(chunk: String, lexicon: Lexicon): ParsedItem? {
        val words = chunk.split(' ').filter { it.isNotBlank() }.toMutableList()
        if (words.isEmpty()) return null

        var quantity: Double? = null
        val phrase = lexicon.quantityPhrases.firstOrNull { (start, _) -> words.size >= start.size && words.subList(0, start.size) == start }
        if (phrase != null) {
            quantity = phrase.second; repeat(phrase.first.size) { words.removeAt(0) }
        } else {
            val first = words[0]
            val number = first.toDoubleOrNull() ?: lexicon.wordNumbers[first]
            if (number != null) {
                words.removeAt(0)
                val multiplier = words.firstOrNull()?.let(lexicon.multipliers::get)
                if (multiplier != null) words.removeAt(0)
                quantity = number * (multiplier ?: 1.0)
            }
        }

        var unit: VoiceUnit? = null
        if (words.isNotEmpty() && lexicon.units.containsKey(words[0])) {
            unit = lexicon.units.getValue(words[0]); words.removeAt(0)
        }
        if (words.isNotEmpty() && words[0] == lexicon.connector) words.removeAt(0)
        while (words.isNotEmpty() && words[0] in lexicon.articles) words.removeAt(0)

        val name = words.joinToString(" ").trim()
        if (name.isEmpty()) return null
        return ParsedItem(name, quantity ?: 1.0, unit ?: VoiceUnit.UNIT)
    }
}
