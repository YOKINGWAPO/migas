package com.jokin.calorias.domain

import kotlin.random.Random

/** The names are stored in settings and in the backup: add entries, never rename them. */
enum class MascotKind { POTATO, PENGUIN, RACCOON }

/** Accessories the streak unlocks. Several per tier so the mascot does not wear the same thing every day. */
enum class StreakReward(val tier: PetStage) {
    PIXEL_GLASSES(PetStage.CHICK),
    MUSTACHE(PetStage.CHICK),
    BACKWARDS_CAP(PetStage.CHICK),
    GANG_BANDANA(PetStage.CHICK),
    PLUMBER_CAP(PetStage.CHICK),
    ELECTRIC_EARS(PetStage.CHICK),
    HEADBAND(PetStage.BIRD),
    CHEF_HAT(PetStage.BIRD),
    CAPE(PetStage.BIRD),
    STRAW_HAT(PetStage.BIRD),
    ELF_HAT(PetStage.BIRD),
    NINJA_BAND(PetStage.BIRD),
    LEGENDARY(PetStage.PHOENIX),
    LASER_EYES(PetStage.PHOENIX),
    CROWN(PetStage.PHOENIX),
    DRILL_SHADES(PetStage.PHOENIX),
}

object RewardPicker {
    fun unlocked(streak: Int): List<StreakReward> =
        StreakReward.entries.filter { streak >= it.tier.minStreak }

    /**
     * The reward shown on [day], or null before the first tier. Same day and [shuffle] give the same
     * reward, so it does not change on every recomposition; tapping the mascot bumps [shuffle].
     */
    fun forDay(streak: Int, day: Long, shuffle: Int = 0): StreakReward? {
        val pool = unlocked(streak)
        if (pool.isEmpty()) return null
        return pool[Random(day * 31 + shuffle).nextInt(pool.size)]
    }
}
