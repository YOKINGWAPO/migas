package com.jokin.calorias.data.remote

/**
 * JSON schemas for Claude's structured outputs, one per prompt in [AiPrompts] and with the same keys,
 * so the shared readers parse either provider's reply. Structured outputs need every object closed
 * (`additionalProperties: false`) with all its properties required; a value that may be missing is
 * nullable instead of optional.
 */
object AiSchemas {
    val TEST: Map<String, Any?> = obj("ok" to bool())

    val VOICE: Map<String, Any?> = obj(
        "comida" to nullable("string"),
        "items" to array(obj("nombre" to str(), "cantidad" to num(), "unidad" to str())),
        "agua_ml" to num(),
    )

    val EXERCISES: Map<String, Any?> = obj(
        "ejercicios" to array(obj("nombre" to str(), "series" to num(), "repeticiones" to num(), "peso_kg" to nullable("number"))),
    )

    /** Shared by the estimate by name and the label photo; the name estimate just leaves "marca" null. */
    val FOOD: Map<String, Any?> = obj(
        "nombre" to str(),
        "marca" to nullable("string"),
        "kcal" to nullable("number"),
        "proteinas" to nullable("number"),
        "hidratos" to nullable("number"),
        "grasas" to nullable("number"),
        "azucares" to nullable("number"),
        "fibra" to nullable("number"),
        "saturadas" to nullable("number"),
        "sal" to nullable("number"),
        "racion_g" to nullable("number"),
    )

    val MEAL: Map<String, Any?> = obj(
        "componentes" to array(
            obj(
                "nombre" to str(),
                "gramos" to num(),
                "kcal_100g" to num(),
                "proteinas_100g" to num(),
                "hidratos_100g" to num(),
                "grasas_100g" to num(),
            ),
        ),
        "confianza" to mapOf("type" to "string", "enum" to listOf("alta", "media", "baja")),
        "nota" to nullable("string"),
    )

    private fun obj(vararg props: Pair<String, Map<String, Any?>>): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(*props),
        "required" to props.map { it.first },
        "additionalProperties" to false,
    )

    private fun array(items: Map<String, Any?>) = mapOf("type" to "array", "items" to items)
    private fun str() = mapOf("type" to "string")
    private fun num() = mapOf("type" to "number")
    private fun bool() = mapOf("type" to "boolean")
    private fun nullable(type: String) = mapOf("type" to listOf(type, "null"))
}
