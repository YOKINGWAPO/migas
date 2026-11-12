package com.jokin.calorias.domain

import java.time.LocalDate
import java.time.Period

object BirthDate {
    /** Same bounds the goal always had for a typed age. */
    val AGE_RANGE = 14..100

    /** Full years on [day]; someone born on 29 February turns a year older on 1 March in common years. */
    fun ageOn(birthEpochDay: Long, day: Long): Int =
        Period.between(LocalDate.ofEpochDay(birthEpochDay), LocalDate.ofEpochDay(day)).years

    /** True on the day the age goes up, so the greeting always matches the age shown with it. */
    fun isBirthday(birthEpochDay: Long, day: Long): Boolean =
        day > birthEpochDay && ageOn(birthEpochDay, day) > ageOn(birthEpochDay, day - 1)
}
