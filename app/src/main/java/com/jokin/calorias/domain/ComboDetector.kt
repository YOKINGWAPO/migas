package com.jokin.calorias.domain

data class MealOccurrence(val epochDay: Long, val meal: MealType, val foodIds: Set<Long>)

data class ComboSuggestion(val foodIds: Set<Long>, val occurrences: Int) {
    /** Stable key used to remember suggestions the user dismissed. */
    val signature: String get() = foodIds.sorted().joinToString("-")
}

object ComboDetector {
    const val MIN_OCCURRENCES = 3
    const val WINDOW_DAYS = 30

    /** Bigger meals are trimmed so subset enumeration stays at 2^8 per meal at most. */
    private const val MAX_FOODS_PER_MEAL = 8

    /**
     * Finds groups of 2+ foods eaten together in the same meal at least [MIN_OCCURRENCES] times.
     * Only maximal groups are returned: if {pan, queso, lacón} qualifies with the same count,
     * {pan, queso} is not suggested on its own.
     */
    fun suggest(
        meals: List<MealOccurrence>,
        today: Long,
        existingCombos: Set<Set<Long>>,
        dismissedSignatures: Set<String>,
    ): List<ComboSuggestion> {
        val windowStart = today - WINDOW_DAYS + 1
        val counts = HashMap<Set<Long>, Int>()
        meals.asSequence()
            .filter { it.epochDay in windowStart..today && it.foodIds.size >= 2 }
            .forEach { meal ->
                subsetsOfSizeTwoOrMore(meal.foodIds.sorted().take(MAX_FOODS_PER_MEAL)).forEach { subset ->
                    counts[subset] = (counts[subset] ?: 0) + 1
                }
            }
        val frequent = counts.filterValues { it >= MIN_OCCURRENCES }
        return frequent
            .filter { (set, count) ->
                frequent.none { (other, otherCount) -> other.size > set.size && other.containsAll(set) && otherCount == count }
            }
            .map { (set, count) -> ComboSuggestion(set, count) }
            .filter { it.foodIds !in existingCombos && it.signature !in dismissedSignatures }
            .sortedWith(compareByDescending<ComboSuggestion> { it.occurrences }.thenByDescending { it.foodIds.size })
    }

    private fun subsetsOfSizeTwoOrMore(ids: List<Long>): List<Set<Long>> {
        val result = mutableListOf<Set<Long>>()
        val n = ids.size
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) < 2) continue
            result += ids.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
        }
        return result
    }
}
