package com.jokin.calorias.ui.food

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.decodeExtras
import com.jokin.calorias.data.remote.EstimatedFood
import com.jokin.calorias.data.remote.Macro
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.data.settings.LabelMode
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today
import java.time.LocalTime
import kotlinx.coroutines.launch

private val EDITABLE_EXTRAS = listOf(ExtraNutrient.SUGARS, ExtraNutrient.FIBER, ExtraNutrient.SATURATED_FAT, ExtraNutrient.SALT)

/** Creates an own product (from a scan that found nothing, or by hand) or edits any food. */
@Composable
fun FoodEditorScreen(nav: NavController, foodId: Long, initialBarcode: String, initialName: String, initialBrand: String) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(foodId == 0L) }
    var source by rememberSaveable { mutableStateOf(FoodSource.OWN) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var brand by rememberSaveable { mutableStateOf(initialBrand) }
    var barcode by rememberSaveable { mutableStateOf(initialBarcode) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var extras by rememberSaveable { mutableStateOf(EDITABLE_EXTRAS.map { "" }) }
    var servingName by rememberSaveable { mutableStateOf("") }
    var servingGrams by rememberSaveable { mutableStateOf("") }
    var showLabelCamera by remember { mutableStateOf(false) }
    val settings by remember { c.settings.settings }.collectAsState(initial = AppSettings())
    var confirmDelete by remember { mutableStateOf(false) }
    // Extras the form does not show (vitamins from Open Food Facts) survive an edit untouched.
    var hiddenExtras by remember { mutableStateOf<Map<ExtraNutrient, Double>>(emptyMap()) }

    fun fill(e: EstimatedFood) {
        if (e.name.isNotBlank()) name = e.name
        e.brand?.let { brand = it }
        fun macro(m: Macro, v: Double) = if (m in e.unreadMacros) "" else decimal(v)
        kcal = decimal(e.nutrients.kcal, 0)
        protein = macro(Macro.PROTEIN, e.nutrients.protein); carbs = macro(Macro.CARBS, e.nutrients.carbs); fat = macro(Macro.FAT, e.nutrients.fat)
        extras = EDITABLE_EXTRAS.map { k -> e.nutrients.extras[k]?.let { decimal(it, 2) } ?: "" }
        e.servingGrams?.let { servingGrams = decimal(it, 0); if (servingName.isBlank()) servingName = res.getString(R.string.add_one_serving) }
    }

    LaunchedEffect(foodId) {
        if (loaded) return@LaunchedEffect
        val f = c.foods.byId(foodId) ?: return@LaunchedEffect
        source = f.source; name = f.name; brand = f.brand.orEmpty(); barcode = f.barcode.orEmpty()
        kcal = decimal(f.kcal, 0); protein = decimal(f.protein); carbs = decimal(f.carbs); fat = decimal(f.fat)
        val all = decodeExtras(f.extrasJson)
        extras = EDITABLE_EXTRAS.map { k -> all[k]?.let { decimal(it, 2) } ?: "" }
        hiddenExtras = all - EDITABLE_EXTRAS.toSet()
        servingName = f.servingName.orEmpty(); servingGrams = f.servingGrams?.let { decimal(it, 0) }.orEmpty()
        loaded = true
    }

    if (showLabelCamera) {
        LabelCameraDialog(
            modeLabel = stringResource(settings.labelMode.labelRes),
            liveReading = settings.labelMode == LabelMode.BASIC,
            read = c.foods::readLabel,
            onResult = { e ->
                fill(e)
                showLabelCamera = false
                val unread = e.unreadMacros.joinToString(", ") { res.getString(macroRes(it)).lowercase() }
                val message = if (unread.isEmpty()) res.getString(R.string.add_editor_label_read)
                else res.getString(R.string.add_editor_label_unread, unread)
                scope.launch { snackbar.showSnackbar(message) }
            },
            onDismiss = { showLabelCamera = false },
        )
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (foodId == 0L) R.string.add_editor_new_product else R.string.add_edit_food), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (foodId == 0L && initialBarcode.isNotEmpty()) Muted(stringResource(R.string.add_editor_code_not_found, initialBarcode))
        if (source != FoodSource.OWN) Muted(stringResource(R.string.add_editor_source, stringResource(source.labelRes())))

        val modeTag = stringResource(if (settings.labelMode == LabelMode.AI) R.string.add_label_tag_ai else R.string.add_label_tag_basic)
        FilledTonalButton(onClick = { showLabelCamera = true }) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_editor_label_photo, modeTag))
        }

        SectionCard {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.add_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it.take(60) }, label = { Text(stringResource(R.string.add_brand_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                barcode, { v -> if (v.length <= 14 && v.all(Char::isDigit)) barcode = v },
                label = { Text(stringResource(R.string.add_barcode_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard {
            SectionTitle(stringResource(R.string.add_per_100g))
            NumberField(kcal, { kcal = it }, stringResource(R.string.add_calories), Modifier.fillMaxWidth(), suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it }, stringResource(R.string.add_protein), Modifier.weight(1f), suffix = "g")
                NumberField(carbs, { carbs = it }, stringResource(R.string.add_carbs), Modifier.weight(1f), suffix = "g")
                NumberField(fat, { fat = it }, stringResource(R.string.add_fat), Modifier.weight(1f), suffix = "g")
            }
            EDITABLE_EXTRAS.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEachIndexed { col, key ->
                        val i = row * 2 + col
                        NumberField(extras[i], { v -> extras = extras.toMutableList().also { it[i] = v } }, stringResource(key.labelRes()), Modifier.weight(1f), suffix = key.unit)
                    }
                }
            }
        }
        SectionCard {
            SectionTitle(stringResource(R.string.add_serving_optional))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(servingName, { servingName = it.take(40) }, label = { Text(stringResource(R.string.add_name)) }, singleLine = true, modifier = Modifier.weight(1.4f))
                NumberField(servingGrams, { servingGrams = it }, stringResource(R.string.add_grams), Modifier.weight(1f), suffix = "g")
            }
        }

        val savedMessage = stringResource(R.string.saved)
        Button(
            onClick = {
                scope.runAction(snackbar, savedMessage) {
                    val n = Nutrients(
                        kcal = parseDecimal(kcal) ?: error(res.getString(R.string.add_editor_missing_kcal)),
                        protein = parseDecimal(protein) ?: 0.0,
                        carbs = parseDecimal(carbs) ?: 0.0,
                        fat = parseDecimal(fat) ?: 0.0,
                        extras = hiddenExtras + EDITABLE_EXTRAS.mapIndexedNotNull { i, k -> parseDecimal(extras[i])?.let { k to it } },
                    )
                    val saved = c.foods.save(foodId, name, brand, barcode, n, parseDecimal(servingGrams), servingName, source)
                    if (foodId == 0L) {
                        nav.navigate(Routes.log(saved.id, today(), MealType.guessFor(LocalTime.now()))) {
                            popUpTo(Routes.FOOD) { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                }
            },
            enabled = name.isNotBlank() && parseDecimal(kcal) != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.add_save)) }

        if (foodId != 0L) OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.add_editor_delete_food)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.add_editor_delete_title, name)) },
            text = { Text(stringResource(R.string.add_editor_delete_text)) },
            confirmButton = {
                val deletedMessage = stringResource(R.string.add_editor_deleted)
                TextButton(onClick = {
                    confirmDelete = false
                    scope.runAction(snackbar, deletedMessage) {
                        val f = c.foods.byId(foodId) ?: return@runAction
                        c.foods.delete(f)
                        nav.popBackStack()
                    }
                }) { Text(stringResource(R.string.add_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }
}

@StringRes
private fun macroRes(macro: Macro): Int = when (macro) {
    Macro.PROTEIN -> R.string.add_protein
    Macro.CARBS -> R.string.add_carbs
    Macro.FAT -> R.string.add_fat
}
