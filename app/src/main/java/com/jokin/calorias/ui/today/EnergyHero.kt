package com.jokin.calorias.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jokin.calorias.R
import com.jokin.calorias.data.health.SourceApps
import com.jokin.calorias.domain.EnergyMath
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.MotionSpec
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.RollingNumber
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.theme.BrandFont
import com.jokin.calorias.ui.theme.LocalMarkColor
import com.jokin.calorias.ui.theme.MacroColors
import com.jokin.calorias.ui.theme.OverColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one figure that matters on Today: kcal left, on a 270° ring, with the three macros under it.
 * Everything else about the day's energy is one tap away in [DayDetailsSheet].
 */
@Composable
fun EnergyHero(state: EnergyState.Ready, eaten: Nutrients, onDetails: () -> Unit, onRingPlaced: (Offset?) -> Unit) {
    val e = state.energy
    // Scrolled out of the list, the ring is no longer somewhere the add sheet can be pulled into.
    DisposableEffect(Unit) { onDispose { onRingPlaced(null) } }
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDetails)
                    .onGloballyPositioned { onRingPlaced(it.boundsInRoot().center) },
            ) { DayRing(eaten.kcal, e.targetKcal) }
            Muted(stringResource(R.string.today_eaten_of_target, kcal(eaten.kcal), e.targetKcal))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MacroBar(stringResource(R.string.today_carbs), eaten.carbs, e.macros.carbsG, MacroColors.carbs, Modifier.weight(1f))
                MacroBar(stringResource(R.string.today_protein), eaten.protein, e.macros.proteinG, MacroColors.protein, Modifier.weight(1f))
                MacroBar(stringResource(R.string.today_fat), eaten.fat, e.macros.fatG, MacroColors.fat, Modifier.weight(1f))
            }
            if (e.macros.carbsG < EnergyMath.LOW_CARBS_G) {
                Spacer(Modifier.height(8.dp))
                Muted(stringResource(R.string.today_low_carbs, e.macros.carbsG.roundToInt()))
            }
        }
    }
}

@Composable
private fun DayRing(eaten: Double, target: Int) {
    val fraction = if (target > 0) (eaten / target).toFloat() else 0f
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), MotionSpec.bouncy, label = "ring")
    val over = eaten > target
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val marks = LocalMarkColor.current
    Box(Modifier.size(RING_SIZE, RING_SIZE * 0.88f), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(RING_SIZE)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, RING_START, RING_SWEEP, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (sweep > 0f) drawArc(if (over) OverColor else marks.value, RING_START, RING_SWEEP * sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(top = RING_SIZE * 0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
            RollingNumber(
                abs(target - eaten).roundToInt(),
                TextStyle(fontFamily = BrandFont, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, letterSpacing = (-1).sp),
                color = if (over) OverColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(if (over) R.string.today_kcal_over else R.string.today_kcal_left),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RING_SIZE = 208.dp
private const val RING_START = 135f
private const val RING_SWEEP = 270f

@Composable
private fun MacroBar(label: String, value: Double, target: Double, color: Color, modifier: Modifier) {
    val over = target > 0 && value > target * 1.1
    val progress by animateFloatAsState(if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f, MotionSpec.standard, label = "macro")
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (over) OverColor else color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${value.roundToInt()} / ${target.roundToInt()} g",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (over) OverColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Where does this come from": the target's arithmetic, the watch's figures and every nutrient. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsSheet(state: EnergyState.Ready, eaten: Nutrients, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    val e = state.energy
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.today_where_from), style = MaterialTheme.typography.titleLarge)
            Figure(stringResource(R.string.today_target), "${e.targetKcal} kcal")
            Figure(stringResource(R.string.today_eaten), "${kcal(eaten.kcal)} kcal")
            Figure(stringResource(if (e.garminSoFarKcal != null) R.string.today_burned_so_far else R.string.today_burned), "${kcal(e.garminSoFarKcal ?: e.expenditure.total)} kcal")
            val split = if (e.garminSoFarKcal != null) R.string.today_split_forecast else R.string.today_split
            Muted(stringResource(split, kcal(e.expenditure.basal), kcal(e.expenditure.active), kcal(e.expenditure.total)))
            if (e.burnCorrectionKcal != 0) Muted(stringResource(R.string.today_burn_correction_note, signedKcal(e.burnCorrectionKcal)))
            if (!e.fromGarmin) {
                Muted(stringResource(if (e.restingFromGarmin) R.string.today_no_watch_resting else R.string.today_no_watch_estimated))
            } else if (!e.restingFromGarmin) {
                Muted(stringResource(R.string.today_resting_estimated))
            }
            WatchFigures(state)
            FilledTonalButton(onClick = onRefresh) { Text(stringResource(R.string.today_refresh_watch)) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SectionTitle(stringResource(R.string.today_nutrients))
            NutrientList(eaten)
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WatchFigures(state: EnergyState.Ready) {
    val h = state.energy.health
    if (h == null && state.sleep == null && state.restingHr == null && state.workouts.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    SectionTitle(h?.sources?.let { SourceApps.names(it) }?.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: stringResource(R.string.today_your_watch))
    Row(Modifier.fillMaxWidth()) {
        Stat(stringResource(R.string.today_steps), h?.steps?.toString() ?: "—", Modifier.weight(1f))
        Stat(stringResource(R.string.today_sleep), state.sleep?.let { stringResource(R.string.today_sleep_duration, it.minutes / 60, it.minutes % 60) } ?: "—", Modifier.weight(1f))
        Stat(stringResource(R.string.today_resting_hr), state.restingHr?.let { stringResource(R.string.today_bpm, it.toInt()) } ?: "—", Modifier.weight(1f))
    }
    state.workouts.forEach { w ->
        Column {
            Text(w.title ?: stringResource(w.type.labelRes()))
            Muted(listOfNotNull("${w.minutes} min", w.distanceM?.let { "${decimal(it / 1000, 2)} km" }, w.kcal?.let { "${kcal(it)} kcal" }).joinToString(" · "))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Muted(label)
    }
}

@Composable
fun NutrientList(n: Nutrients) {
    ExtraNutrient.entries.forEach { key ->
        val v = n.extras[key] ?: return@forEach
        val pct = v / key.reference * 100
        val flag = if (key.isLimit && pct > 100) OverColor else MaterialTheme.colorScheme.onSurfaceVariant
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(stringResource(key.labelRes()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${decimal(v)} ${key.unit}", style = MaterialTheme.typography.bodyMedium)
            Text("  ${pct.toInt()}%", color = flag, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
        }
    }
    Muted(stringResource(R.string.today_nutrients_reference))
}
