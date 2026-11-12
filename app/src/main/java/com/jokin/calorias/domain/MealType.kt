package com.jokin.calorias.domain

import java.time.LocalTime

enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK;

    companion object {
        /** Spanish meal schedule: late lunch and dinner are the norm. */
        fun guessFor(time: LocalTime): MealType = when (time.hour) {
            in 5..10 -> BREAKFAST
            in 11..12 -> SNACK
            in 13..16 -> LUNCH
            in 17..19 -> SNACK
            in 20..23 -> DINNER
            else -> SNACK
        }
    }
}
