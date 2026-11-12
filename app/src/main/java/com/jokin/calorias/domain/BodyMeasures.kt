package com.jokin.calorias.domain

/** Names are stored in the database and in backups: add entries, never rename them. */
enum class BodyPart {
    CHEST,
    BICEPS,
    WAIST,
    ABDOMEN,
    HIP,
    THIGH,
    NECK,
}

data class BodyValue(val epochDay: Long, val part: BodyPart, val cm: Double)

data class PartChange(val part: BodyPart, val first: BodyValue, val last: BodyValue) {
    val deltaCm: Double get() = last.cm - first.cm
}

object BodyMeasures {
    /** Anything outside is a typo: no adult neck is under 10 cm nor any waist over 250. */
    val CM_RANGE = 10.0..250.0

    /** Latest value of each part on a day before [day], to show next to the field being filled. */
    fun previous(values: List<BodyValue>, day: Long): Map<BodyPart, BodyValue> =
        values.filter { it.epochDay < day }.groupBy { it.part }.mapValues { (_, v) -> v.maxBy { it.epochDay } }

    /** First and last value of each part within [from]..[to]; parts measured only once are left out. */
    fun changes(values: List<BodyValue>, from: Long, to: Long): List<PartChange> =
        values.filter { it.epochDay in from..to }.groupBy { it.part }
            .filterValues { v -> v.map { it.epochDay }.distinct().size > 1 }
            .map { (part, v) -> PartChange(part, v.minBy { it.epochDay }, v.maxBy { it.epochDay }) }
            .sortedBy { it.part.ordinal }
}
