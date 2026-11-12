package com.jokin.calorias.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appViewModel
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today

data class EditorItem(val food: FoodEntity, val gramsText: String) {
    val grams: Double get() = parseDecimal(gramsText) ?: 0.0
}

/** Holds a combo or recipe being edited; lives in the nav entry so picking a food does not lose it. */
class ItemsEditorViewModel : ViewModel() {
    var loaded by mutableStateOf(false)
    var name by mutableStateOf("")
    var extra by mutableStateOf("")
    val items = mutableStateListOf<EditorItem>()
}

/** Consumes a food id returned by the search screen in pick mode. */
@Composable
private fun PickedFood(handle: SavedStateHandle, onPicked: suspend (Long) -> Unit) {
    val picked by handle.getStateFlow<Long?>(Routes.PICKED_FOOD, null).collectAsState()
    LaunchedEffect(picked) {
        val id = picked ?: return@LaunchedEffect
        handle[Routes.PICKED_FOOD] = null
        onPicked(id)
    }
}

private fun LazyListScope.itemRows(vm: ItemsEditorViewModel) {
    itemsIndexed(vm.items) { i, it ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.food.name, style = MaterialTheme.typography.titleSmall)
                    Muted("${kcal(it.food.forGrams(it.grams).kcal)} kcal")
                }
                NumberField(it.gramsText, { v -> vm.items[i] = it.copy(gramsText = v) }, "g", Modifier.width(110.dp))
                IconButton(onClick = { vm.items.removeAt(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.mine_remove)) }
            }
        }
    }
}

@Composable
private fun ConfirmDelete(question: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(question) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.mine_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_cancel)) } },
    )
}

@Composable
fun ComboEditorScreen(nav: NavController, id: Long, handle: SavedStateHandle) {
    val c = appContainer()
    val vm = appViewModel(key = "combo$id") { ItemsEditorViewModel() }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (vm.loaded) return@LaunchedEffect
        vm.extra = "🍽️"
        c.combos.byId(id)?.let { combo ->
            vm.name = combo.combo.name; vm.extra = combo.combo.icon
            vm.items += combo.items.map { EditorItem(it.food, decimal(it.item.grams, 0)) }
        }
        vm.loaded = true
    }
    PickedFood(handle) { foodId -> c.foods.byId(foodId)?.let { vm.items += EditorItem(it, decimal(it.servingGrams ?: 100.0, 0)) } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(if (id == 0L) R.string.mine_combo_new else R.string.mine_combo_edit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.extra, { vm.extra = it.take(4) }, label = { Text(stringResource(R.string.mine_icon)) }, singleLine = true, modifier = Modifier.width(90.dp))
                OutlinedTextField(vm.name, { vm.name = it.take(60) }, label = { Text(stringResource(R.string.mine_name)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        itemRows(vm)
        item {
            Muted(stringResource(R.string.mine_combo_total, kcal(vm.items.sumOf { it.food.forGrams(it.grams).kcal })))
            FilledTonalButton(onClick = { nav.navigate(Routes.search(today(), MealType.SNACK, pick = true)) }) { Text(stringResource(R.string.mine_combo_add_food)) }
        }
        item {
            val saved = stringResource(R.string.mine_combo_saved)
            Button(
                onClick = {
                    scope.runAction(snackbar, saved) {
                        c.combos.save(id, vm.name, vm.extra, vm.items.map { it.food.id to it.grams })
                        nav.popBackStack()
                    }
                },
                enabled = vm.name.isNotBlank() && vm.items.isNotEmpty() && vm.items.all { it.grams > 0 },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.mine_save)) }
            if (id != 0L) OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.mine_combo_delete)) }
        }
    }
    if (confirm) {
        val deleted = stringResource(R.string.mine_combo_deleted)
        ConfirmDelete(stringResource(R.string.mine_combo_delete_question), { confirm = false }) {
            confirm = false
            scope.runAction(snackbar, deleted) { c.combos.delete(id); nav.popBackStack() }
        }
    }
}

@Composable
fun RecipeEditorScreen(nav: NavController, id: Long, handle: SavedStateHandle) {
    val c = appContainer()
    val vm = appViewModel(key = "recipe$id") { ItemsEditorViewModel() }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (vm.loaded) return@LaunchedEffect
        vm.extra = "4"
        c.recipes.byId(id)?.let { r ->
            vm.name = r.recipe.name; vm.extra = r.recipe.servings.toString()
            vm.items += r.items.map { EditorItem(it.food, decimal(it.item.grams, 0)) }
        }
        vm.loaded = true
    }
    PickedFood(handle) { foodId -> c.foods.byId(foodId)?.let { vm.items += EditorItem(it, "100") } }

    val servings = vm.extra.toIntOrNull() ?: 0
    val totalKcal = vm.items.sumOf { it.food.forGrams(it.grams).kcal }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(if (id == 0L) R.string.mine_recipe_new else R.string.mine_recipe_edit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.name, { vm.name = it.take(80) }, label = { Text(stringResource(R.string.mine_name)) }, singleLine = true, modifier = Modifier.weight(1f))
                NumberField(vm.extra, { vm.extra = it }, stringResource(R.string.mine_recipe_servings), Modifier.width(110.dp), decimals = false)
            }
        }
        itemRows(vm)
        item {
            if (servings > 0) {
                Muted(stringResource(R.string.mine_recipe_total, kcal(totalKcal), kcal(totalKcal / servings), decimal(vm.items.sumOf { it.grams } / servings, 0)))
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.search(today(), MealType.SNACK, pick = true)) }) { Text(stringResource(R.string.mine_recipe_add_ingredient)) }
        }
        item {
            val saved = stringResource(R.string.mine_recipe_saved)
            Button(
                onClick = {
                    scope.runAction(snackbar, saved) {
                        c.recipes.save(id, vm.name, servings, vm.items.map { it.food to it.grams })
                        nav.popBackStack()
                    }
                },
                enabled = vm.name.isNotBlank() && servings > 0 && vm.items.isNotEmpty() && vm.items.all { it.grams > 0 },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.mine_save)) }
            if (id != 0L) OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.mine_recipe_delete)) }
        }
    }
    if (confirm) {
        val deleted = stringResource(R.string.mine_recipe_deleted)
        ConfirmDelete(stringResource(R.string.mine_recipe_delete_question), { confirm = false }) {
            confirm = false
            scope.runAction(snackbar, deleted) {
                c.recipes.byId(id)?.let { c.recipes.delete(it) }
                nav.popBackStack()
            }
        }
    }
}
