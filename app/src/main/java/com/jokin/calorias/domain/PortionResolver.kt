package com.jokin.calorias.domain

data class Portion(val name: String, val grams: Double)

object PortionResolver {
    /** Used when a food has no serving size at all, so "un X" still produces a sensible entry. */
    const val DEFAULT_UNIT_G = 100.0
    const val DEFAULT_SLICE_G = 30.0

    /** Portion names are typed by the user, in Spanish or English. */
    private val SLICE_WORDS = listOf("rebanada", "loncha", "slice")

    /**
     * Grams for "quantity unit of food". Food-independent units are converted directly; a slice
     * looks for a slice-like portion of the food; a unit or serving uses the food's serving size.
     */
    fun grams(quantity: Double, unit: VoiceUnit, glassMl: Int, servingGrams: Double?, portions: List<Portion>): Double {
        unit.fixedGrams(quantity, glassMl)?.let { return it }
        val perOne = when (unit) {
            VoiceUnit.SLICE -> portions.firstOrNull { p -> SLICE_WORDS.any { p.name.contains(it, true) } }?.grams
                ?: DEFAULT_SLICE_G
            else -> servingGrams ?: portions.firstOrNull()?.grams ?: DEFAULT_UNIT_G
        }
        return quantity * perOne
    }
}
