package com.jokin.calorias.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.displayName
import com.jokin.calorias.data.remote.OffProduct
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.runAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Done(val products: List<OffProduct>) : OnlineState
    data class Failed(val message: String) : OnlineState
}

/**
 * Name search: local results (own products, recipes, BEDCA, cached products) as you type, and
 * Open Food Facts for branded products. In [pickMode] the chosen food id goes back to the caller.
 */
@Composable
fun SearchScreen(nav: NavController, day: Long, meal: MealType, pickMode: Boolean) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var local by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var online by remember { mutableStateOf<OnlineState>(OnlineState.Idle) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(200)
        local = c.foods.searchLocal(query)
    }
    // Open Food Facts allows few searches per minute, so wait until typing pauses.
    LaunchedEffect(query) {
        online = OnlineState.Idle
        if (query.trim().length < 3) return@LaunchedEffect
        delay(900)
        online = OnlineState.Loading
        online = try {
            OnlineState.Done(c.foods.searchOnline(query))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OnlineState.Failed(e.message ?: res.getString(R.string.add_no_connection))
        }
    }

    fun choose(food: FoodEntity) {
        if (pickMode) {
            nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_FOOD, food.id)
            nav.popBackStack()
        } else {
            nav.navigate(Routes.log(food.id, day, meal))
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.add_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (local.isNotEmpty()) item { SectionTitle(stringResource(R.string.add_search_on_phone), Modifier.padding(top = 12.dp)) }
        items(local, key = { "l" + it.id }) { f ->
            ResultRow(f.displayName(), "${kcal(f.kcal)} kcal/100 g · ${stringResource(f.source.labelRes())}") { choose(f) }
        }
        if (query.trim().length >= 3) {
            item { SectionTitle("Open Food Facts", Modifier.padding(top = 12.dp)) }
            when (val o = online) {
                OnlineState.Idle -> Unit
                OnlineState.Loading -> item { Row(Modifier.padding(8.dp)) { CircularProgressIndicator() } }
                is OnlineState.Failed -> item { Muted(stringResource(R.string.add_search_off_failed, o.message)) }
                is OnlineState.Done -> {
                    if (o.products.isEmpty()) item { Muted(stringResource(R.string.add_search_no_results)) }
                    items(o.products, key = { "o" + it.barcode }) { p ->
                        ResultRow(listOfNotNull(p.name, p.brand).joinToString(" · "), "${kcal(p.nutrients.kcal)} kcal/100 g") {
                            scope.runAction(snackbar) { choose(c.foods.cacheOffProduct(p)) }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { nav.navigate(Routes.food(0, name = query.trim())) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text(stringResource(R.string.add_search_create_own)) }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Muted(subtitle)
    }
}
