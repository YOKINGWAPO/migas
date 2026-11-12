package com.jokin.calorias.ui

import com.jokin.calorias.domain.MealType

/** String routes; every argument is a number or an enum name, so nothing needs URL escaping except free text. */
object Routes {
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val PROGRESS = "progress"
    const val MINE = "mine"
    const val SCAN = "scan/{day}/{meal}"
    const val SEARCH = "search/{day}/{meal}?pick={pick}"
    const val VOICE = "voice/{day}/{meal}"
    const val MEAL_PHOTO = "mealphoto/{day}/{meal}"
    const val EXERCISE = "exercise/{day}?id={id}"
    const val BODY = "body/{day}"
    const val LOG = "log/{foodId}/{day}/{meal}?entryId={entryId}"
    const val FOOD = "food/{foodId}?barcode={barcode}&name={name}&brand={brand}"
    const val COMBOS = "combos"
    const val COMBO = "combo/{id}"
    const val RECIPES = "recipes"
    const val RECIPE = "recipe/{id}"
    const val OWN = "own"
    const val GOAL = "goal"
    const val SETTINGS = "settings"
    const val EXPORT = "export"
    const val IMPORT = "import"

    /** Key in the previous entry's SavedStateHandle where a picked food id is returned. */
    const val PICKED_FOOD = "picked_food"

    val topLevel = setOf(TODAY, CALENDAR, PROGRESS, MINE)

    fun scan(day: Long, meal: MealType) = "scan/$day/${meal.name}"
    fun search(day: Long, meal: MealType, pick: Boolean = false) = "search/$day/${meal.name}?pick=$pick"
    fun voice(day: Long, meal: MealType) = "voice/$day/${meal.name}"
    fun mealPhoto(day: Long, meal: MealType) = "mealphoto/$day/${meal.name}"
    fun exercise(day: Long, id: Long = 0) = "exercise/$day?id=$id"
    fun body(day: Long) = "body/$day"
    fun log(foodId: Long, day: Long, meal: MealType, entryId: Long = 0) = "log/$foodId/$day/${meal.name}?entryId=$entryId"
    fun food(foodId: Long, barcode: String? = null, name: String? = null, brand: String? = null): String =
        "food/$foodId?barcode=${enc(barcode)}&name=${enc(name)}&brand=${enc(brand)}"
    fun combo(id: Long) = "combo/$id"
    fun recipe(id: Long) = "recipe/$id"

    private fun enc(v: String?) = android.net.Uri.encode(v ?: "")
}
