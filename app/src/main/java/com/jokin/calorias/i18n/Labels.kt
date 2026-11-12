package com.jokin.calorias.i18n

import androidx.annotation.StringRes
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.health.WorkoutType
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.GoalPace
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Sex
import com.jokin.calorias.domain.StreakReward
import com.jokin.calorias.domain.TargetMode

/*
 * Display names of the enums shared across screens. The enums stay free of text because their
 * names are stored in the database and in backups; the words depend on the app's language.
 */

@StringRes
fun MealType.labelRes(): Int = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.DINNER -> R.string.meal_dinner
    MealType.SNACK -> R.string.meal_snack
}

@StringRes
fun FoodSource.labelRes(): Int = when (this) {
    FoodSource.OPEN_FOOD_FACTS -> R.string.source_open_food_facts
    FoodSource.BEDCA -> R.string.source_bedca
    FoodSource.CIQUAL -> R.string.source_ciqual
    FoodSource.USDA -> R.string.source_usda
    FoodSource.OWN -> R.string.source_own
    FoodSource.ESTIMATED -> R.string.source_estimated
    FoodSource.RECIPE -> R.string.source_recipe
    FoodSource.QUICK -> R.string.source_quick
    FoodSource.IMPORTED -> R.string.source_imported
}

@StringRes
fun ExtraNutrient.labelRes(): Int = when (this) {
    ExtraNutrient.SUGARS -> R.string.nutrient_sugars
    ExtraNutrient.FIBER -> R.string.nutrient_fiber
    ExtraNutrient.SATURATED_FAT -> R.string.nutrient_saturated_fat
    ExtraNutrient.SALT -> R.string.nutrient_salt
    ExtraNutrient.VITAMIN_A -> R.string.nutrient_vitamin_a
    ExtraNutrient.VITAMIN_D -> R.string.nutrient_vitamin_d
    ExtraNutrient.VITAMIN_E -> R.string.nutrient_vitamin_e
    ExtraNutrient.VITAMIN_C -> R.string.nutrient_vitamin_c
    ExtraNutrient.VITAMIN_B1 -> R.string.nutrient_vitamin_b1
    ExtraNutrient.VITAMIN_B2 -> R.string.nutrient_vitamin_b2
    ExtraNutrient.VITAMIN_B6 -> R.string.nutrient_vitamin_b6
    ExtraNutrient.VITAMIN_B12 -> R.string.nutrient_vitamin_b12
    ExtraNutrient.FOLATE -> R.string.nutrient_folate
    ExtraNutrient.CALCIUM -> R.string.nutrient_calcium
    ExtraNutrient.IRON -> R.string.nutrient_iron
    ExtraNutrient.MAGNESIUM -> R.string.nutrient_magnesium
    ExtraNutrient.ZINC -> R.string.nutrient_zinc
    ExtraNutrient.POTASSIUM -> R.string.nutrient_potassium
    ExtraNutrient.PHOSPHORUS -> R.string.nutrient_phosphorus
}

@StringRes
fun BodyPart.labelRes(): Int = when (this) {
    BodyPart.CHEST -> R.string.body_part_chest
    BodyPart.BICEPS -> R.string.body_part_biceps
    BodyPart.WAIST -> R.string.body_part_waist
    BodyPart.ABDOMEN -> R.string.body_part_abdomen
    BodyPart.HIP -> R.string.body_part_hip
    BodyPart.THIGH -> R.string.body_part_thigh
    BodyPart.NECK -> R.string.body_part_neck
}

@StringRes
fun WorkoutType.labelRes(): Int = when (this) {
    WorkoutType.RUNNING -> R.string.workout_running
    WorkoutType.WALKING -> R.string.workout_walking
    WorkoutType.CYCLING -> R.string.workout_cycling
    WorkoutType.SWIMMING -> R.string.workout_swimming
    WorkoutType.HIKING -> R.string.workout_hiking
    WorkoutType.STRENGTH -> R.string.workout_strength
    WorkoutType.YOGA -> R.string.workout_yoga
    WorkoutType.OTHER -> R.string.workout_other
}

@StringRes
fun MascotKind.labelRes(): Int = when (this) {
    MascotKind.POTATO -> R.string.mascot_potato
    MascotKind.PENGUIN -> R.string.mascot_penguin
    MascotKind.RACCOON -> R.string.mascot_raccoon
}

@StringRes
fun StreakReward.labelRes(): Int = when (this) {
    StreakReward.PIXEL_GLASSES -> R.string.reward_pixel_glasses
    StreakReward.MUSTACHE -> R.string.reward_mustache
    StreakReward.BACKWARDS_CAP -> R.string.reward_backwards_cap
    StreakReward.GANG_BANDANA -> R.string.reward_gang_bandana
    StreakReward.PLUMBER_CAP -> R.string.reward_plumber_cap
    StreakReward.ELECTRIC_EARS -> R.string.reward_electric_ears
    StreakReward.HEADBAND -> R.string.reward_headband
    StreakReward.CHEF_HAT -> R.string.reward_chef_hat
    StreakReward.CAPE -> R.string.reward_cape
    StreakReward.STRAW_HAT -> R.string.reward_straw_hat
    StreakReward.ELF_HAT -> R.string.reward_elf_hat
    StreakReward.NINJA_BAND -> R.string.reward_ninja_band
    StreakReward.LEGENDARY -> R.string.reward_legendary
    StreakReward.LASER_EYES -> R.string.reward_laser_eyes
    StreakReward.CROWN -> R.string.reward_crown
    StreakReward.DRILL_SHADES -> R.string.reward_drill_shades
}

@StringRes
fun Sex.labelRes(): Int = when (this) {
    Sex.MALE -> R.string.sex_male
    Sex.FEMALE -> R.string.sex_female
}

@StringRes
fun GoalPace.labelRes(): Int = when (this) {
    GoalPace.LOSE_GENTLE -> R.string.pace_lose_gentle
    GoalPace.LOSE -> R.string.pace_lose
    GoalPace.LOSE_FAST -> R.string.pace_lose_fast
    GoalPace.MAINTAIN -> R.string.pace_maintain
    GoalPace.GAIN -> R.string.pace_gain
}

@StringRes
fun TargetMode.labelRes(): Int = when (this) {
    TargetMode.DYNAMIC -> R.string.target_mode_dynamic
    TargetMode.FIXED -> R.string.target_mode_fixed
}
