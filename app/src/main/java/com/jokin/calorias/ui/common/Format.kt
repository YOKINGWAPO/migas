package com.jokin.calorias.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Android sets the default locale to the app's language (per-app setting or the system's). */
fun appLocale(): Locale = Locale.getDefault()

fun kcal(v: Double): String = "${v.roundToInt()}"

fun grams(v: Double): String = if (v >= 10 || v == v.roundToInt().toDouble()) "${v.roundToInt()} g" else String.format(appLocale(), "%.1f g", v)

fun decimal(v: Double, digits: Int = 1): String = String.format(appLocale(), "%.${digits}f", v)

/** "40" or "12,5": whole kilos without a useless ",0". */
fun kilos(v: Double): String = if (v % 1.0 == 0.0) "${v.toLong()}" else decimal(v, 1)

/** Accepts "1,5" and "1.5"; null for anything that is not a non-negative number. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

/**
 * [day] in the app's language from a skeleton such as "EEEEdMMMM" or "MMMMyyyy": the locale picks
 * order and words ("lunes, 3 de febrero" / "Monday, February 3").
 */
fun formatDay(day: Long, skeleton: String): String {
    val locale = appLocale()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
fun dayTitle(day: Long, today: Long): String = when (day) {
    today -> stringResource(R.string.day_today)
    today - 1 -> stringResource(R.string.day_yesterday)
    today + 1 -> stringResource(R.string.day_tomorrow)
    else -> formatDay(day, "EEEEdMMMM").replaceFirstChar { it.uppercase() }
}

fun shortDate(day: Long): String = formatDay(day, "dMMM")

fun today(): Long = LocalDate.now().toEpochDay()

/** Quick entries are stored as servings of an arbitrary weight, so they read as servings, not grams. */
@Composable
fun amountText(food: FoodEntity, grams: Double): String {
    val serving = food.servingGrams
    if (food.source != FoodSource.QUICK || serving == null) return grams(grams)
    val servings = grams / serving
    val unit = if (servings == 1.0) food.servingName ?: stringResource(R.string.serving) else stringResource(R.string.servings)
    return "${kilos(servings)} $unit"
}
