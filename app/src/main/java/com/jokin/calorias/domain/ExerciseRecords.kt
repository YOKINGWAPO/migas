package com.jokin.calorias.domain

data class LoggedSet(val epochDay: Long, val sets: Int, val reps: Int, val weightKg: Double?)

/** [best] is the heaviest set, or the most reps when the exercise was never weighted. */
data class ExerciseRecord(val last: LoggedSet?, val best: LoggedSet)

object ExerciseRecords {
    /** [history] in logging order; "last" is the latest set on a day before [day]. */
    fun summarize(history: List<LoggedSet>, day: Long): ExerciseRecord? {
        if (history.isEmpty()) return null
        val last = history.filter { it.epochDay < day }.maxByOrNull { it.epochDay }
            ?.let { lastDay -> history.last { it.epochDay == lastDay.epochDay } }
        val weighted = history.filter { it.weightKg != null }
        val best = if (weighted.isNotEmpty()) {
            weighted.maxWith(compareBy<LoggedSet> { it.weightKg }.thenBy { it.reps })
        } else {
            history.maxWith(compareBy<LoggedSet> { it.reps }.thenBy { it.sets })
        }
        return ExerciseRecord(last, best)
    }
}
