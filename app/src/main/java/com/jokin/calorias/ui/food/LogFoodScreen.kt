package com.jokin.calorias.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.LogEntryEntity
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalAppScope
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MealSelector
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SaveButton
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SharedContainer
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.grams
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.kilos
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.today.NutrientList

/** A unit the amount is counted in: grams, the food's serving or a household measure. */
private data class AmountUnit(val label: String, val grams: Double)

/** Its label is only a key: the chip and the field show "g". */
private val GRAMS = AmountUnit("gramos", 1.0)

/** "2,0" or "2.0" depending on the language: a whole amount reads better without it. */
private val TRAILING_ZERO = Regex("""[.,]0$""")

/** Key shared by a logged row and this screen, so the row grows into it. */
fun entryContainerKey(entryId: Long) = "entry-$entryId"

@Composable
fun LogFoodScreen(nav: NavController, foodId: Long, day: Long, initialMeal: MealType, entryId: Long) {
    // The container exists before the food loads, so the row has something to grow into on the first frame.
    SharedContainer(entryId.takeIf { it != 0L }?.let(::entryContainerKey), Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LogFoodContent(nav, foodId, day, initialMeal, entryId)
    }
}

@Composable
private fun LogFoodContent(nav: NavController, foodId: Long, day: Long, initialMeal: MealType, entryId: Long) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val appScope = LocalAppScope.current
    val food by remember(foodId) { c.foods.observe(foodId) }.collectAsState(initial = null)
    val measures by remember(foodId) { c.foods.observeMeasures(foodId) }.collectAsState(initial = emptyList())
    var entry by remember { mutableStateOf<LogEntryEntity?>(null) }

    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var unitLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var addMeasure by remember { mutableStateOf(false) }

    val f = food ?: return
    val servingLabel = f.servingName ?: stringResource(R.string.serving)
    val units = buildList {
        add(GRAMS)
        if (f.servingGrams != null) add(AmountUnit(servingLabel, f.servingGrams))
        measures.forEach { add(AmountUnit(it.name, it.grams)) }
    }.distinctBy { it.label }

    // Defaults: an edited entry keeps its grams; a new one starts with one serving if the food has it.
    LaunchedEffect(f.id, entryId) {
        if (amount.isNotEmpty()) return@LaunchedEffect
        if (entryId != 0L) {
            entry = c.log.entry(entryId)
            entry?.let { e ->
                meal = e.meal
                val serving = f.servingGrams
                // A quick entry's grams are a storage detail (see FoodRepository.saveQuick): edit it in servings.
                if (f.source == FoodSource.QUICK && serving != null) {
                    unitLabel = servingLabel; amount = kilos(e.grams / serving)
                } else {
                    unitLabel = GRAMS.label; amount = decimal(e.grams, 0)
                }
            }
        } else if (f.servingGrams != null) {
            unitLabel = servingLabel; amount = "1"
        } else {
            unitLabel = GRAMS.label; amount = "100"
        }
    }

    val unit = units.firstOrNull { it.label == unitLabel } ?: GRAMS
    val totalGrams = (parseDecimal(amount) ?: 0.0) * unit.grams
    val n = f.forGrams(totalGrams)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(f.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Muted(listOfNotNull(f.brand, stringResource(f.source.labelRes()), f.barcode).joinToString(" · "))
        MealSelector(meal, { meal = it })

        SectionCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u ->
                    FilterChip(
                        selected = u == unit,
                        onClick = {
                            // Keep the same grams when switching unit, so the amount does not jump.
                            val g = totalGrams
                            unitLabel = u.label
                            amount = if (g > 0) decimal(g / u.grams, if (u == GRAMS) 0 else 1).replace(TRAILING_ZERO, "") else amount
                        },
                        label = { Text(if (u == GRAMS) "g" else "${u.label} (${grams(u.grams)})") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(amount, { amount = it }, stringResource(R.string.add_amount), Modifier.weight(1f), suffix = if (unit == GRAMS) "g" else unit.label)
                Spacer(Modifier.width(12.dp))
                if (unit != GRAMS) Text("= ${grams(totalGrams)}", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { addMeasure = true }) { Text(stringResource(R.string.add_log_add_measure)) }
        }

        SectionCard {
            Text("${kcal(n.kcal)} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Muted(stringResource(R.string.add_log_macros, decimal(n.protein), decimal(n.carbs), decimal(n.fat)))
            Muted(stringResource(R.string.add_log_per_100g, kcal(f.kcal)))
            TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.add_log_hide_nutrients else R.string.add_log_show_nutrients))
            }
            if (showAll) NutrientList(n)
        }

        SaveButton(
            text = stringResource(if (entryId == 0L) R.string.add_add else R.string.add_save_changes),
            onSave = {
                val current = entry
                if (current == null) c.log.logFood(day, meal, f.id, totalGrams) else c.log.updateGrams(current, totalGrams, meal)
            },
            onSaved = { nav.popBackStack(Routes.TODAY, inclusive = false).also { popped -> if (!popped) nav.popBackStack() } },
            enabled = totalGrams > 0,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate(Routes.food(f.id)) }) { Text(stringResource(R.string.add_edit_food)) }
            entry?.let { e ->
                OutlinedButton(onClick = {
                    scope.runAction(snackbar) {
                        c.log.delete(e)
                        nav.popBackStack()
                        // This screen is gone by now: the offer to undo lives in the app's scope.
                        appScope.runAction(snackbar) {
                            val r = snackbar.showSnackbar(res.getString(R.string.add_log_deleted), actionLabel = res.getString(R.string.add_undo), duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) c.log.restore(e)
                        }
                    }
                }) { Text(stringResource(R.string.add_log_delete_entry)) }
            }
        }
    }

    if (addMeasure) {
        var name by remember { mutableStateOf("") }
        var g by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMeasure = false },
            title = { Text(stringResource(R.string.add_log_new_measure)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.add_log_measure_name)) }, singleLine = true)
                    NumberField(g, { g = it }, stringResource(R.string.add_grams), suffix = "g")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val grams = parseDecimal(g) ?: 0.0
                        scope.runAction(snackbar) { c.foods.addMeasure(f.id, name, grams); addMeasure = false }
                    },
                    enabled = name.isNotBlank() && (parseDecimal(g) ?: 0.0) > 0,
                ) { Text(stringResource(R.string.add_save)) }
            },
            dismissButton = { TextButton(onClick = { addMeasure = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }
}
