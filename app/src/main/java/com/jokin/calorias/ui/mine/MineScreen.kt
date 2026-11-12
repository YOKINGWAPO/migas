package com.jokin.calorias.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.domain.GoalPace
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.kilos
import com.jokin.calorias.ui.common.today
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

@Composable
fun MineScreen(nav: NavController) {
    val c = appContainer()
    val goal by remember { c.energy.observeGoal() }.collectAsState(initial = null)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_me), style = MaterialTheme.typography.headlineMedium)
        GoalCard(goal, onEdit = { nav.navigate(Routes.GOAL) })
        SectionCard {
            Entry(Icons.Outlined.Fastfood, stringResource(R.string.mine_combos), stringResource(R.string.mine_combos_subtitle)) { nav.navigate(Routes.COMBOS) }
            Divider()
            Entry(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.mine_recipes), stringResource(R.string.mine_recipes_subtitle)) {
                nav.navigate(Routes.RECIPES)
            }
            Divider()
            Entry(Icons.Outlined.Sell, stringResource(R.string.mine_own_products), stringResource(R.string.mine_own_products_subtitle)) { nav.navigate(Routes.OWN) }
        }
        SectionCard {
            Entry(Icons.Outlined.Settings, stringResource(R.string.mine_settings), stringResource(R.string.mine_settings_subtitle)) { nav.navigate(Routes.SETTINGS) }
            Divider()
            Entry(Icons.Outlined.ImportExport, stringResource(R.string.mine_export), stringResource(R.string.mine_export_subtitle)) { nav.navigate(Routes.EXPORT) }
        }
    }
}

/** Today's target first, then where the goal is heading; it opens the goal screen. */
@Composable
private fun GoalCard(goal: GoalEntity?, onEdit: () -> Unit) {
    val c = appContainer()
    var target by remember(goal) { mutableStateOf<Int?>(null) }
    var error by remember(goal) { mutableStateOf<String?>(null) }
    LaunchedEffect(goal) {
        val g = goal ?: return@LaunchedEffect
        try {
            target = c.energy.forDay(g, today(), today()).targetKcal
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    SectionCard(Modifier.clickable(onClick = onEdit)) {
        if (goal == null) {
            Text(stringResource(R.string.mine_no_goal), style = MaterialTheme.typography.titleLarge)
            Muted(stringResource(R.string.mine_no_goal_hint))
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.mine_set_up)) }
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(target?.toString() ?: "—", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(8.dp))
            Muted(stringResource(R.string.mine_kcal_today), Modifier.padding(bottom = 8.dp))
        }
        error?.let { Muted(stringResource(R.string.mine_target_error, it)) }
        Spacer(Modifier.height(4.dp))
        Text(paceText(goal.pace), style = MaterialTheme.typography.titleMedium)
        Muted(
            listOfNotNull(
                "${kilos(goal.weightKg)} kg",
                goal.targetWeightKg?.let { stringResource(R.string.mine_goal_weight_short, kilos(it)) },
                stringResource(R.string.mine_protein_per_kg_short, decimal(goal.proteinPerKg)),
            ).joinToString(" · ")
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.mine_change_goal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun paceText(pace: GoalPace): String {
    val label = stringResource(pace.labelRes())
    return when {
        pace.kgPerWeek < 0 -> stringResource(R.string.mine_pace_per_week, label, decimal(abs(pace.kgPerWeek), 2))
        pace.kgPerWeek > 0 -> stringResource(R.string.mine_pace_per_week, label, "+${decimal(pace.kgPerWeek, 2)}")
        else -> label
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Muted(subtitle)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
