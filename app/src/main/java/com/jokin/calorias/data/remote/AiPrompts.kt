package com.jokin.calorias.data.remote

import com.jokin.calorias.domain.ExerciseParser
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.domain.ParsedExercise
import com.jokin.calorias.domain.ParsedItem
import com.jokin.calorias.domain.ParsedVoice
import com.jokin.calorias.domain.VoiceParser
import com.jokin.calorias.domain.VoiceUnit
import java.io.IOException
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Macro { PROTEIN, CARBS, FAT }

/** [unreadMacros] were missing or unreadable in the reply; their value in [nutrients] is a placeholder 0. */
data class EstimatedFood(
    val name: String,
    val brand: String?,
    val nutrients: Nutrients,
    val servingGrams: Double?,
    val unreadMacros: Set<Macro> = emptySet(),
)

/** One component of a plated meal: [nutrients] per 100 g as served, [grams] on the plate. */
data class EstimatedDish(val name: String, val grams: Double, val nutrients: Nutrients)

/** [confidence] is the model's own "alta", "media" or "baja"; [note] its main doubt. Empty [dishes]: no food seen. */
data class MealEstimate(val dishes: List<EstimatedDish>, val confidence: String?, val note: String?)

/** Any failure talking to an AI provider, with a message fit for the user. An IOException so callers fall back to basic mode. */
open class AiException(message: String) : IOException(message)

/** The reply had no usable calories; the client replaces it with a message in the app's language. */
class MissingKcalException : AiException("no kcal in the reply")

/**
 * What the app asks an AI and how it reads the reply. NanoGPT and Claude get the same prompts, so a
 * change of provider never changes what is asked. All methods throw [AiException] or a serialization
 * error when the reply cannot be used.
 */
interface AiClient {
    /** Shown in messages: "no connection to …". */
    val name: String

    suspend fun testConnection(key: String, model: String): String
    suspend fun interpretVoice(key: String, model: String, text: String, glassMl: Int, language: Locale): ParsedVoice
    suspend fun interpretExercises(key: String, model: String, text: String, language: Locale): List<ParsedExercise>
    suspend fun estimateFood(key: String, model: String, name: String, language: Locale): EstimatedFood
    suspend fun readLabel(key: String, model: String, jpeg: ByteArray): EstimatedFood
    suspend fun estimateMeal(key: String, model: String, jpeg: ByteArray, hint: String, language: Locale): MealEstimate
}

/**
 * The prompts stay in Spanish whatever the app's language: they were tuned in Spanish and their JSON keys
 * are what the readers and [AiSchemas] expect. Only the text the user will see (food names, notes) follows
 * the app's language. Quotes in user text become apostrophes so they cannot break the JSON shape asked for.
 */
object AiPrompts {
    const val TEST = "Responde solo con JSON: {\"ok\": true}"

    fun voice(text: String, glassMl: Int, language: Locale) = """
        Analiza esta frase en ${nameOf(language)} sobre lo que alguien ha comido o bebido y devuelve SOLO JSON con esta forma:
        {"comida": "desayuno|comida|cena|snack|null", "items": [{"nombre": "texto", "cantidad": número, "unidad": "g|ml|unidad|racion|vaso|taza|cucharada|cucharadita|rebanada|punado"}], "agua_ml": número}
        Reglas: "nombre" es el alimento en ${nameOf(language)}, en singular y sin cantidades. El agua no va en items, se suma en agua_ml (un vaso = $glassMl ml).
        Si no se dice la comida, usa null. No inventes alimentos que no se mencionan.
        Frase: "${text.take(500).replace("\"", "'")}"
    """.trimIndent()

    fun exercises(text: String, language: Locale) = """
        Analiza esta frase en ${nameOf(language)} sobre ejercicios de fuerza que alguien ha hecho y devuelve SOLO JSON con esta forma:
        {"ejercicios": [{"nombre": "texto", "series": número, "repeticiones": número, "peso_kg": número o null}]}
        Reglas: "nombre" es el ejercicio en ${nameOf(language)}, en plural o como se suele decir, sin cantidades ("Flexiones", "Press banca").
        "4x15" son 4 series de 15 repeticiones. Si no se dicen series, es 1. Peso solo si se dice. No inventes ejercicios.
        Frase: "${text.take(500).replace("\"", "'")}"
    """.trimIndent()

    fun food(name: String, language: Locale) = """
        Estima los valores nutricionales típicos por 100 g de "${name.take(80).replace("\"", "'")}" tal como se toma en España.
        Devuelve SOLO JSON: {"nombre": "nombre corto en ${nameOf(language)}", "kcal": n, "proteinas": n, "hidratos": n, "grasas": n,
        "azucares": n, "fibra": n, "saturadas": n, "sal": n, "racion_g": gramos de una ración o unidad típica}
    """.trimIndent()

    const val LABEL = """Es la foto de un producto envasado o de su etiqueta nutricional. Lee los valores POR 100 g (o 100 ml).
Devuelve SOLO JSON: {"nombre": "nombre del producto", "marca": "marca o null", "kcal": n, "proteinas": n, "hidratos": n,
"grasas": n, "azucares": n, "fibra": n, "saturadas": n, "sal": n, "racion_g": gramos de la ración si aparece o null}
"nombre" es la denominación del producto tal como está impresa (por ejemplo, la que precede a los ingredientes).
Ignora fotos, recetas y sugerencias de presentación: lo que aparece en ellas no es el producto.
Si un valor no se ve, pon null. No inventes valores que no estén en la etiqueta."""

    /**
     * Tuned against Nutrition5k dishes with weighed truth: asking for separate components, size references
     * and the unseen cooking fat took DeepSeek 4.1 Flash from a 21 % to a 14 % median error; nudging it
     * "not to fall short" only added bias.
     */
    fun meal(hint: String, language: Locale) = """
        Eres dietista y estimas lo que hay en la foto de un plato para apuntarlo en un diario de calorías. Piensa paso a paso por dentro, pero responde SOLO con el JSON.
        1. Separa cada componente visible ("arroz blanco", "pechuga a la plancha", "patatas fritas", "salsa"). Lo que no se puede pesar por separado va junto (una ensalada mixta es un componente).
        2. Estima los gramos de cada componente tal como está (cocinado). Usa referencias de tamaño: plato llano ~26 cm, plato de postre ~20 cm, tenedor ~19 cm, vaso ~250 ml. Calcula superficie y altura del montón antes de dar los gramos; no cuentes el plato ni lo que no se come.
        3. Da los valores por 100 g del componente cocinado. Incluye la grasa que no se ve: lo frito absorbe aceite, lo salteado o a la plancha de restaurante lleva aceite, y los aliños y salsas visibles son un componente aparte.
        4. Si dudas entre dos tamaños, elige el intermedio. No infles ni recortes: la estimación debe ser la más probable.
        Pista del usuario (si contradice la foto, manda la foto): "${hint.take(200).replace("\"", "'")}"
        JSON: {"componentes": [{"nombre": "texto corto en ${nameOf(language)}", "gramos": n, "kcal_100g": n, "proteinas_100g": n, "hidratos_100g": n, "grasas_100g": n}], "confianza": "alta|media|baja", "nota": "una frase en ${nameOf(language)} con lo más dudoso, o null"}
        Si en la foto no hay comida, devuelve {"componentes": []}.
    """.trimIndent()

    /** "inglés", "español": the language's name as the rest of the prompt is written. */
    private fun nameOf(language: Locale): String = language.getDisplayLanguage(PROMPT_LOCALE)

    private val PROMPT_LOCALE: Locale = Locale.forLanguageTag("es")
}

private val JSON = Json { ignoreUnknownKeys = true }

/** Special tokens such as `<|TOOL_CALL|>` that some models leak into their reply, even inside values. */
private val LEAKED_TOKEN = Regex("""<\|[A-Za-z_]+\|>""")

private val LEADING_NUMBER = Regex("""^(<)?\s*(\d+(?:[.,]\d+)?)""")

internal fun parseModelJson(content: String): JsonObject {
    val cleaned = content.replace(LEAKED_TOKEN, "").trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return JSON.parseToJsonElement(cleaned).jsonObject
}

/**
 * Accepts what models actually send for a label value: a number, or a string such as "2,3",
 * "2.3 g" or "<0,5" (counted as 0, like the on-device label parser).
 */
internal fun nutrientValue(element: JsonElement?): Double? {
    val p = element as? JsonPrimitive ?: return null
    if (!p.isString) return p.doubleOrNull?.takeIf { it >= 0 }
    val match = LEADING_NUMBER.find(p.content.trim()) ?: return null
    if (match.groupValues[1].isNotEmpty()) return 0.0
    return match.groupValues[2].replace(',', '.').toDoubleOrNull()
}

internal fun voiceFromJson(o: JsonObject): ParsedVoice {
    val meal = when (o["comida"]?.jsonPrimitive?.content) {
        "desayuno" -> MealType.BREAKFAST
        "comida" -> MealType.LUNCH
        "cena" -> MealType.DINNER
        "snack" -> MealType.SNACK
        else -> null
    }
    val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { el ->
        val item = el as? JsonObject ?: return@mapNotNull null
        val name = item["nombre"]?.jsonPrimitive?.content?.let(VoiceParser::normalize)?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val qty = (item["cantidad"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 && it < 10_000 } ?: 1.0
        ParsedItem(name.take(80), qty, unitOf(item["unidad"]?.jsonPrimitive?.content))
    }
    val water = (o["agua_ml"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.0, 5000.0) ?: 0.0
    return ParsedVoice(meal, items, water.toInt())
}

private fun unitOf(raw: String?): VoiceUnit = when (raw?.lowercase()) {
    "g" -> VoiceUnit.GRAM
    "ml" -> VoiceUnit.MILLILITER
    "racion" -> VoiceUnit.SERVING
    "vaso" -> VoiceUnit.GLASS
    "taza" -> VoiceUnit.CUP
    "cucharada" -> VoiceUnit.TABLESPOON
    "cucharadita" -> VoiceUnit.TEASPOON
    "rebanada" -> VoiceUnit.SLICE
    "punado" -> VoiceUnit.HANDFUL
    else -> VoiceUnit.UNIT
}

internal fun foodFromJson(o: JsonObject, fallbackName: String): EstimatedFood {
    fun num(k: String) = nutrientValue(o[k])
    val kcal = num("kcal")?.takeIf { it <= 950 } ?: throw MissingKcalException()
    val extras = buildMap {
        num("azucares")?.let { put(ExtraNutrient.SUGARS, it.coerceAtMost(100.0)) }
        num("fibra")?.let { put(ExtraNutrient.FIBER, it.coerceAtMost(100.0)) }
        num("saturadas")?.let { put(ExtraNutrient.SATURATED_FAT, it.coerceAtMost(100.0)) }
        num("sal")?.let { put(ExtraNutrient.SALT, it.coerceAtMost(100.0)) }
    }
    val macros = mapOf(Macro.PROTEIN to num("proteinas"), Macro.CARBS to num("hidratos"), Macro.FAT to num("grasas"))
    fun macro(m: Macro) = macros[m]?.coerceAtMost(100.0) ?: 0.0
    val nutrients = Nutrients(kcal, macro(Macro.PROTEIN), macro(Macro.CARBS), macro(Macro.FAT), extras)
    val name = o["nombre"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(80) ?: fallbackName
    val brand = o["marca"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(60)
    val unread = macros.filterValues { it == null }.keys
    return EstimatedFood(name, brand, nutrients, num("racion_g")?.takeIf { it in 1.0..2000.0 }, unread)
}

/** Components without a name, grams or calories are dropped: a dish the user cannot correct is worse than none. */
internal fun mealFromJson(o: JsonObject): MealEstimate {
    val dishes = (o["componentes"] as? JsonArray).orEmpty().mapNotNull { el ->
        val item = el as? JsonObject ?: return@mapNotNull null
        fun num(k: String) = nutrientValue(item[k])
        val name = item["nombre"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
        val grams = num("gramos")?.takeIf { it in 1.0..MAX_DISH_GRAMS } ?: return@mapNotNull null
        val kcal = num("kcal_100g")?.takeIf { it <= 950 } ?: return@mapNotNull null
        fun macro(k: String) = num(k)?.coerceAtMost(100.0) ?: 0.0
        EstimatedDish(name.replaceFirstChar { it.uppercase() }.take(80), grams, Nutrients(kcal, macro("proteinas_100g"), macro("hidratos_100g"), macro("grasas_100g")))
    }
    val confidence = (o["confianza"] as? JsonPrimitive)?.content?.lowercase()?.takeIf { it in setOf("alta", "media", "baja") }
    val note = (o["nota"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(200)
    return MealEstimate(dishes, confidence, note)
}

private const val MAX_DISH_GRAMS = 3000.0

internal fun exercisesFromJson(o: JsonObject): List<ParsedExercise> =
    (o["ejercicios"] as? JsonArray).orEmpty().mapNotNull { el ->
        val item = el as? JsonObject ?: return@mapNotNull null
        val name = item["nombre"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return@mapNotNull null
        val sets = nutrientValue(item["series"])?.toInt() ?: 1
        val reps = nutrientValue(item["repeticiones"])?.toInt() ?: return@mapNotNull null
        if (sets !in 1..ExerciseParser.MAX_SETS || reps !in 1..ExerciseParser.MAX_REPS) return@mapNotNull null
        val weight = nutrientValue(item["peso_kg"])?.takeIf { it > 0 && it <= ExerciseParser.MAX_WEIGHT_KG }
        ParsedExercise(name.replaceFirstChar { it.uppercase() }.take(60), sets, reps, weight)
    }
