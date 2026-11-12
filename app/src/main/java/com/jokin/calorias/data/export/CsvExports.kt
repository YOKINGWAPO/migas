package com.jokin.calorias.data.export

import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.ComboWithItems
import com.jokin.calorias.data.db.EntryWithFood
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.NoteEntity
import com.jokin.calorias.data.db.RecipeWithItems
import com.jokin.calorias.data.db.WaterEntity
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.data.health.HealthDay
import com.jokin.calorias.data.health.Workout
import com.jokin.calorias.data.health.WorkoutType
import com.jokin.calorias.data.per100g
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.Csv
import com.jokin.calorias.domain.DayValue
import com.jokin.calorias.domain.Expenditure
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One CSV per data type, with the column names in the app's language because they are read by a
 * person. The words live here and not in resources: the diary and weight files must keep headers
 * and meal names that [com.jokin.calorias.domain.CsvImport] reads back, and both sides are tested
 * without Android.
 */
object CsvExports {
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    private fun date(day: Long) = LocalDate.ofEpochDay(day).toString()

    private fun clock(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(time)

    fun meals(entries: List<EntryWithFood>, zone: ZoneId, english: Boolean = false): String = Csv.build(
        if (english) {
            listOf("date", "time", "meal", "food", "brand", "source", "grams", "kcal", "protein_g", "carbs_g", "fat_g",
                "sugars_g", "fiber_g", "saturated_fat_g", "salt_g")
        } else {
            listOf("fecha", "hora", "comida", "alimento", "marca", "origen", "gramos", "kcal", "proteinas_g", "hidratos_g", "grasas_g",
                "azucares_g", "fibra_g", "saturadas_g", "sal_g")
        },
        entries.map { (e, f) ->
            val n = f.forGrams(e.grams)
            listOf(date(e.epochDay), clock(e.loggedAt, zone), name(e.meal, english), f.name, f.brand, name(f.source, english), e.grams,
                n.kcal, n.protein, n.carbs, n.fat, n.extras[ExtraNutrient.SUGARS], n.extras[ExtraNutrient.FIBER],
                n.extras[ExtraNutrient.SATURATED_FAT], n.extras[ExtraNutrient.SALT])
        },
        english,
    )

    fun notes(notes: List<NoteEntity>, english: Boolean = false): String =
        Csv.build(if (english) listOf("date", "note") else listOf("fecha", "nota"), notes.map { listOf(date(it.epochDay), it.text) }, english)

    fun exercises(sets: List<ExerciseEntity>, english: Boolean = false): String = Csv.build(
        if (english) listOf("date", "exercise", "sets", "reps", "weight_kg") else listOf("fecha", "ejercicio", "series", "repeticiones", "peso_kg"),
        sets.map { listOf(date(it.epochDay), it.name, it.sets, it.reps, it.weightKg) },
        english,
    )

    fun bodyMeasures(measures: List<BodyMeasureEntity>, english: Boolean = false): String = Csv.build(
        if (english) listOf("date", "part", "cm") else listOf("fecha", "parte", "cm"),
        measures.map { listOf(date(it.epochDay), name(it.part, english), it.cm) },
        english,
    )

    fun water(water: List<WaterEntity>, zone: ZoneId, english: Boolean = false): String = Csv.build(
        if (english) listOf("date", "time", "ml") else listOf("fecha", "hora", "ml"),
        water.map { listOf(date(it.epochDay), clock(it.loggedAt, zone), it.ml) },
        english,
    )

    fun weight(weights: List<DayValue>, bodyFat: List<DayValue>, english: Boolean = false): String {
        val fat = bodyFat.associate { it.epochDay to it.value }
        return Csv.build(
            if (english) listOf("date", "weight_kg", "body_fat_pct") else listOf("fecha", "peso_kg", "grasa_corporal_pct"),
            weights.sortedBy { it.epochDay }.map { listOf(date(it.epochDay), it.value, fat[it.epochDay]) },
            english,
        )
    }

    /** [split] holds the Garmin-style split per day; missing days (no goal yet) leave both columns empty. */
    fun garminDaily(
        days: List<HealthDay>,
        split: Map<Long, Expenditure>,
        sleepMinutes: Map<Long, Long>,
        restingHr: Map<Long, Double>,
        english: Boolean = false,
    ): String = Csv.build(
        if (english) listOf("date", "total_kcal", "resting_kcal", "active_kcal", "steps", "distance_km", "sleep_h", "resting_hr")
        else listOf("fecha", "kcal_totales", "kcal_reposo", "kcal_activas", "pasos", "distancia_km", "sueno_h", "fc_reposo"),
        days.sortedBy { it.epochDay }.map { d ->
            val active = split[d.epochDay]?.active
            val resting = if (active != null && d.totalKcal != null) d.totalKcal - active else null
            listOf(date(d.epochDay), d.totalKcal, resting, active, d.steps, d.distanceM?.div(1000),
                sleepMinutes[d.epochDay]?.let { it / 60.0 }, restingHr[d.epochDay])
        },
        english,
    )

    fun workouts(workouts: List<Workout>, zone: ZoneId, english: Boolean = false): String = Csv.build(
        if (english) listOf("date", "time", "type", "title", "minutes", "distance_km", "kcal")
        else listOf("fecha", "hora", "tipo", "titulo", "minutos", "distancia_km", "kcal"),
        workouts.sortedBy { it.start }.map { w ->
            listOf(date(w.epochDay), w.start.atZone(zone).toLocalTime().format(time), name(w.type, english), w.title, w.minutes,
                w.distanceM?.div(1000), w.kcal)
        },
        english,
    )

    fun combos(combos: List<ComboWithItems>, english: Boolean = false): String = Csv.build(
        if (english) listOf("combo", "food", "grams", "kcal") else listOf("combo", "alimento", "gramos", "kcal"),
        combos.flatMap { c -> c.items.map { listOf(c.combo.name, it.food.name, it.item.grams, it.food.forGrams(it.item.grams).kcal) } },
        english,
    )

    fun recipes(recipes: List<RecipeWithItems>, english: Boolean = false): String = Csv.build(
        if (english) listOf("recipe", "servings", "ingredient", "grams", "kcal_per_recipe_serving")
        else listOf("receta", "raciones", "ingrediente", "gramos", "kcal_por_racion_receta"),
        recipes.flatMap { r ->
            val perServing = r.food.forGrams(r.food.servingGrams ?: 0.0).kcal
            r.items.map { listOf(r.recipe.name, r.recipe.servings, it.food.name, it.item.grams, perServing) }
        },
        english,
    )

    fun ownProducts(foods: List<FoodEntity>, english: Boolean = false): String = Csv.build(
        if (english) listOf("name", "brand", "barcode", "source", "kcal_100g", "protein_100g", "carbs_100g", "fat_100g", "serving_g")
        else listOf("nombre", "marca", "codigo", "origen", "kcal_100g", "proteinas_100g", "hidratos_100g", "grasas_100g", "racion_g"),
        foods.map { f ->
            val n = f.per100g()
            listOf(f.name, f.brand, f.barcode, name(f.source, english), n.kcal, n.protein, n.carbs, n.fat, f.servingGrams)
        },
        english,
    )

    private fun name(meal: MealType, english: Boolean): String = when (meal) {
        MealType.BREAKFAST -> if (english) "Breakfast" else "Desayuno"
        MealType.LUNCH -> if (english) "Lunch" else "Comida"
        MealType.DINNER -> if (english) "Dinner" else "Cena"
        MealType.SNACK -> "Snacks"
    }

    private fun name(source: FoodSource, english: Boolean): String = when (source) {
        FoodSource.OPEN_FOOD_FACTS -> "Open Food Facts"
        FoodSource.BEDCA -> "BEDCA"
        FoodSource.CIQUAL -> "CIQUAL"
        FoodSource.USDA -> "USDA"
        FoodSource.OWN -> if (english) "Own" else "Propio"
        FoodSource.ESTIMATED -> if (english) "AI estimate" else "Estimado por IA"
        FoodSource.RECIPE -> if (english) "Recipe" else "Receta"
        FoodSource.QUICK -> if (english) "Quick" else "Rápido"
        FoodSource.IMPORTED -> if (english) "Imported" else "Importado"
    }

    private fun name(part: BodyPart, english: Boolean): String = when (part) {
        BodyPart.CHEST -> if (english) "Chest" else "Pecho"
        BodyPart.BICEPS -> if (english) "Biceps" else "Bíceps"
        BodyPart.WAIST -> if (english) "Waist" else "Cintura"
        BodyPart.ABDOMEN -> "Abdomen"
        BodyPart.HIP -> if (english) "Hip" else "Cadera"
        BodyPart.THIGH -> if (english) "Thigh" else "Muslo"
        BodyPart.NECK -> if (english) "Neck" else "Cuello"
    }

    private fun name(type: WorkoutType, english: Boolean): String = when (type) {
        WorkoutType.RUNNING -> if (english) "Run" else "Carrera"
        WorkoutType.WALKING -> if (english) "Walk" else "Caminata"
        WorkoutType.CYCLING -> if (english) "Cycling" else "Ciclismo"
        WorkoutType.SWIMMING -> if (english) "Swimming" else "Natación"
        WorkoutType.HIKING -> if (english) "Hiking" else "Senderismo"
        WorkoutType.STRENGTH -> if (english) "Strength" else "Fuerza"
        WorkoutType.YOGA -> "Yoga"
        WorkoutType.OTHER -> if (english) "Workout" else "Entreno"
    }
}
