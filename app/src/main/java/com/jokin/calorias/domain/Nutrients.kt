package com.jokin.calorias.domain

/**
 * Nutrients beyond the four core ones. [reference] follows the EU Reference Intakes
 * (Regulation 1169/2011, Annex XIII) except fibre (EFSA 25 g). [isLimit] marks nutrients
 * where the reference is a ceiling rather than a target.
 */
enum class ExtraNutrient(val unit: String, val reference: Double, val isLimit: Boolean) {
    SUGARS("g", 90.0, true),
    FIBER("g", 25.0, false),
    SATURATED_FAT("g", 20.0, true),
    SALT("g", 6.0, true),
    VITAMIN_A("µg", 800.0, false),
    VITAMIN_D("µg", 5.0, false),
    VITAMIN_E("mg", 12.0, false),
    VITAMIN_C("mg", 80.0, false),
    VITAMIN_B1("mg", 1.1, false),
    VITAMIN_B2("mg", 1.4, false),
    VITAMIN_B6("mg", 1.4, false),
    VITAMIN_B12("µg", 2.5, false),
    FOLATE("µg", 200.0, false),
    CALCIUM("mg", 800.0, false),
    IRON("mg", 14.0, false),
    MAGNESIUM("mg", 375.0, false),
    ZINC("mg", 10.0, false),
    POTASSIUM("mg", 2000.0, false),
    PHOSPHORUS("mg", 700.0, false);
}

enum class Macro { CARBS, PROTEIN, FAT }

/** Values per 100 g (or 100 ml). */
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val extras: Map<ExtraNutrient, Double> = emptyMap(),
) {
    fun scaledTo(grams: Double): Nutrients {
        val f = grams / 100.0
        return Nutrients(kcal * f, protein * f, carbs * f, fat * f, extras.mapValues { it.value * f })
    }

    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        kcal + other.kcal,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
        (extras.keys + other.extras.keys).associateWith { (extras[it] ?: 0.0) + (other.extras[it] ?: 0.0) },
    )

    /**
     * The macro that brings the most energy (4 kcal/g for carbs and protein, 9 for fat), or null when
     * there are no macros at all, as in a quick entry with only kcal. Ties go to carbs, then protein.
     */
    fun dominantMacro(): Macro? {
        val energy = listOf(Macro.CARBS to carbs * 4, Macro.PROTEIN to protein * 4, Macro.FAT to fat * 9)
        val best = energy.maxBy { it.second }
        return best.first.takeIf { best.second > 0 }
    }

    fun dividedBy(divisor: Double): Nutrients {
        require(divisor > 0) { "divisor must be positive" }
        return scaledTo(100.0 / divisor)
    }

    companion object {
        val ZERO = Nutrients()

        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(ZERO) { acc, n -> acc + n }

        /** Per-100 g values of a mix, e.g. a recipe made of [ingredients] (per-100 g values, grams). */
        fun per100gOfMix(ingredients: List<Pair<Nutrients, Double>>): Nutrients {
            val totalGrams = ingredients.sumOf { it.second }
            if (totalGrams <= 0) return ZERO
            return sum(ingredients.map { (n, g) -> n.scaledTo(g) }).dividedBy(totalGrams / 100.0)
        }
    }
}
