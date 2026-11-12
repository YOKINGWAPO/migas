package com.jokin.calorias.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSchemasTest {
    private val all = mapOf(
        "TEST" to AiSchemas.TEST,
        "VOICE" to AiSchemas.VOICE,
        "EXERCISES" to AiSchemas.EXERCISES,
        "FOOD" to AiSchemas.FOOD,
        "MEAL" to AiSchemas.MEAL,
    )

    /** Every object, however deep, as structured outputs requires it: closed, with every property required. */
    @Test
    fun `every object is closed and requires all its properties`() {
        all.forEach { (name, schema) -> walk(name, schema) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun walk(path: String, node: Map<String, Any?>) {
        if (node["type"] == "object") {
            val props = node["properties"] as Map<String, Map<String, Any?>>
            assertEquals("$path: additionalProperties", false, node["additionalProperties"])
            assertEquals("$path: required", props.keys, (node["required"] as List<String>).toSet())
            props.forEach { (k, v) -> walk("$path.$k", v) }
        }
        (node["items"] as? Map<String, Any?>)?.let { walk("$path[]", it) }
    }

    /** The shared readers look these keys up by name; a typo in the schema would make Claude drop the value. */
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `schemas use the keys the readers parse`() {
        fun keys(s: Map<String, Any?>) = (s["properties"] as Map<String, Any?>).keys
        assertEquals(setOf("comida", "items", "agua_ml"), keys(AiSchemas.VOICE))
        assertTrue(keys(AiSchemas.FOOD).containsAll(listOf("nombre", "marca", "kcal", "proteinas", "hidratos", "grasas", "racion_g")))
        val dish = ((AiSchemas.MEAL["properties"] as Map<String, Any?>)["componentes"] as Map<String, Any?>)["items"] as Map<String, Any?>
        assertEquals(setOf("nombre", "gramos", "kcal_100g", "proteinas_100g", "hidratos_100g", "grasas_100g"), keys(dish))
        val set = ((AiSchemas.EXERCISES["properties"] as Map<String, Any?>)["ejercicios"] as Map<String, Any?>)["items"] as Map<String, Any?>
        assertEquals(setOf("nombre", "series", "repeticiones", "peso_kg"), keys(set))
    }
}
