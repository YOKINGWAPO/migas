package com.jokin.calorias.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.displayName
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.kcal

@Composable
private fun ListScaffold(title: String, action: String?, onAction: () -> Unit, empty: String, isEmpty: Boolean, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.let { FilledTonalButton(onClick = onAction) { Text(it) } }
            }
        }
        if (isEmpty) item { Muted(empty, Modifier.padding(top = 12.dp)) }
        content()
    }
}

@Composable
private fun Line(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Muted(subtitle)
    }
}

@Composable
fun CombosScreen(nav: NavController) {
    val c = appContainer()
    val combos by remember { c.combos.observeAll() }.collectAsState(initial = emptyList())
    ListScaffold(stringResource(R.string.mine_combos), stringResource(R.string.mine_combos_new), { nav.navigate(Routes.combo(0)) },
        stringResource(R.string.mine_combos_empty), combos.isEmpty()) {
        items(combos, key = { it.combo.id }) { combo ->
            Line("${combo.combo.icon} ${combo.combo.name}",
                "${combo.items.joinToString(", ") { it.food.name }} · ${kcal(combo.items.sumOf { it.food.forGrams(it.item.grams).kcal })} kcal") {
                nav.navigate(Routes.combo(combo.combo.id))
            }
        }
    }
}

@Composable
fun RecipesScreen(nav: NavController) {
    val c = appContainer()
    val recipes by remember { c.recipes.observeAll() }.collectAsState(initial = emptyList())
    ListScaffold(stringResource(R.string.mine_recipes), stringResource(R.string.mine_recipes_new), { nav.navigate(Routes.recipe(0)) },
        stringResource(R.string.mine_recipes_empty), recipes.isEmpty()) {
        items(recipes, key = { it.recipe.id }) { r ->
            val perServing = r.food.forGrams(r.food.servingGrams ?: 0.0).kcal
            Line(r.recipe.name, pluralStringResource(R.plurals.mine_recipes_line, r.recipe.servings, r.recipe.servings, kcal(perServing))) {
                nav.navigate(Routes.recipe(r.recipe.id))
            }
        }
    }
}

@Composable
fun OwnProductsScreen(nav: NavController) {
    val c = appContainer()
    val foods by remember { c.foods.observeOwn() }.collectAsState(initial = emptyList())
    ListScaffold(stringResource(R.string.mine_own_products), stringResource(R.string.mine_own_products_new), { nav.navigate(Routes.food(0)) },
        stringResource(R.string.mine_own_products_empty), foods.isEmpty()) {
        items(foods, key = { it.id }) { f ->
            Line(f.displayName(), "${kcal(f.kcal)} kcal/100 g · ${stringResource(f.source.labelRes())}" + (f.barcode?.let { " · $it" } ?: "")) {
                nav.navigate(Routes.food(f.id))
            }
        }
    }
}
