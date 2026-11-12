package com.jokin.calorias.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jokin.calorias.R
import com.jokin.calorias.domain.DayValue
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.TargetEta
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.MotionSpec
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appViewModel
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.formatDay
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.kilos
import com.jokin.calorias.ui.common.shortDate
import com.jokin.calorias.ui.common.today
import com.jokin.calorias.ui.theme.MacroColors
import com.jokin.calorias.ui.today.NutrientList
import kotlin.math.roundToInt

@Composable
fun ProgressScreen() {
    val vm = appViewModel { ProgressViewModel(it) }
    val period by vm.period.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.headlineMedium)
        PeriodSwitch(period, vm::setPeriod)
        when (val s = state) {
            ProgressState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ProgressState.Failed -> SectionCard {
                Text(stringResource(R.string.today_stats_failed, s.message))
                TextButton(onClick = vm::load) { Text(stringResource(R.string.today_retry)) }
            }
            is ProgressState.Ready -> ProgressContent(s.p, period)
        }
    }
}

/** Segmented control: the pill slides to the chosen period. */
@Composable
private fun PeriodSwitch(selected: Period, onSelect: (Period) -> Unit) {
    val index by animateFloatAsState(selected.ordinal.toFloat(), MotionSpec.fast, label = "period")
    val pill = MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .drawBehind {
                val w = size.width / Period.entries.size
                drawRoundRect(pill, Offset(w * index + 3.dp.toPx(), 3.dp.toPx()), Size(w - 6.dp.toPx(), size.height - 6.dp.toPx()), CornerRadius(9.dp.toPx()))
            }
            .padding(vertical = 3.dp),
    ) {
        Period.entries.forEach { p ->
            Text(
                stringResource(p.labelRes),
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).clickable { onSelect(p) }.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (p == selected) FontWeight.Bold else FontWeight.Normal,
                color = if (p == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProgressContent(p: Progress, period: Period) {
    WeightCard(p)
    EatenCard(p)
    MacrosCard(p)
    if (p.bodyChanges.isNotEmpty()) BodyCard(p)
    ActivityCard(p, period)
    HabitsCard(p)
}

@Composable
private fun WeightCard(p: Progress) {
    SectionCard {
        SectionTitle(stringResource(R.string.today_weight))
        if (p.weights.isEmpty()) {
            Muted(stringResource(R.string.today_no_weighins))
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${decimal(p.weightTrend.last().value)} kg", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(10.dp))
            // What changed in the chosen period; the big figure is always today's 7-day mean.
            if (p.weightChange != null && p.weightChangeSince != null) {
                Muted(stringResource(R.string.today_change_since, signedKg(p.weightChange), shortDate(p.weightChangeSince)), Modifier.padding(bottom = 8.dp))
            }
        }
        Muted(listOfNotNull(stringResource(R.string.today_seven_day_mean), p.weeklyRate?.let { stringResource(R.string.today_weekly_rate, signedKg(it, 2)) }).joinToString(" · "))
        p.targetWeightKg?.let { target -> p.targetEta?.let { TargetEtaLine(it, target) } }
        WeightChart(p.weights, p.weightTrend)
    }
}

@Composable
private fun EatenCard(p: Progress) {
    SectionCard {
        SectionTitle(stringResource(R.string.today_eaten_vs_burned))
        if (p.weekBars.isEmpty()) {
            Muted(stringResource(R.string.today_no_meals_period))
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${p.daysInGoal}", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(8.dp))
            Muted(pluralStringResource(R.plurals.today_days_in_goal, p.loggedDays, p.loggedDays), Modifier.padding(bottom = 8.dp))
        }
        WeekBars(p.weekBars)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(MaterialTheme.colorScheme.primary, stringResource(R.string.today_eaten_weekly))
            Legend(MaterialTheme.colorScheme.outline, stringResource(R.string.today_watch_burn))
        }
        Spacer(Modifier.height(4.dp))
        p.realTdee?.let {
            Text(stringResource(R.string.today_real_tdee, kcal(it)), fontWeight = FontWeight.SemiBold)
            Muted(p.garminTdeeAvg?.let { g -> stringResource(R.string.today_real_tdee_basis_watch, kcal(g)) } ?: stringResource(R.string.today_real_tdee_basis))
        } ?: Muted(stringResource(R.string.today_real_tdee_pending))
    }
}

@Composable
private fun MacrosCard(p: Progress) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val n = p.avgNutrients
    val carbsK = n.carbs * 4
    val protK = n.protein * 4
    val fatK = n.fat * 9
    val total = (carbsK + protK + fatK).takeIf { it > 0 } ?: 1.0
    SectionCard(Modifier.animateContentSize()) {
        SectionTitle(stringResource(R.string.today_avg_macros))
        if (p.loggedDays == 0) {
            Muted(stringResource(R.string.today_no_meals_logged_period))
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kcal(n.kcal), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(8.dp))
            Muted(stringResource(R.string.today_kcal_per_day), Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        // One bar split by the share of kcal each macro brings.
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            var x = 0f
            listOf(carbsK to MacroColors.carbs, protK to MacroColors.protein, fatK to MacroColors.fat).forEach { (k, color) ->
                val w = (k / total).toFloat() * size.width
                drawRect(color, Offset(x, 0f), Size(w, size.height))
                x += w
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            MacroShare(stringResource(R.string.today_carbs), n.carbs, carbsK / total, MacroColors.carbs, Modifier.weight(1f))
            MacroShare(stringResource(R.string.today_protein), n.protein, protK / total, MacroColors.protein, Modifier.weight(1f))
            MacroShare(stringResource(R.string.today_fat), n.fat, fatK / total, MacroColors.fat, Modifier.weight(1f))
        }
        TextButton(onClick = { showAll = !showAll }) { Text(stringResource(if (showAll) R.string.today_hide_nutrients else R.string.today_show_nutrients)) }
        if (showAll) NutrientList(n)
    }
}

@Composable
private fun MacroShare(label: String, grams: Double, share: Double, color: Color, modifier: Modifier) {
    Column(modifier) {
        Legend(color, label)
        Text("${grams.roundToInt()} g · ${(share * 100).roundToInt()} %", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BodyCard(p: Progress) {
    SectionCard {
        SectionTitle(stringResource(R.string.today_measures))
        Muted(stringResource(R.string.today_date_range, shortDate(p.bodyChanges.minOf { it.first.epochDay }), shortDate(p.bodyChanges.maxOf { it.last.epochDay })))
        p.bodyChanges.forEach { ch ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(ch.part.labelRes()), Modifier.weight(1f))
                Muted("${decimal(ch.first.cm)} → ${decimal(ch.last.cm)} cm")
                Spacer(Modifier.width(10.dp))
                val sign = if (ch.deltaCm > 0) "+" else ""
                Text(
                    "$sign${decimal(ch.deltaCm)}",
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(p: Progress, period: Period) {
    SectionCard {
        SectionTitle(stringResource(R.string.today_activity))
        val periodStart = today() - period.days + 1
        // Garmin only wrote to Health Connect from some day on; say so instead of averaging over empty weeks.
        p.activitySince?.takeIf { it > periodStart }?.let { Muted(stringResource(R.string.today_activity_since, shortDate(it))) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat(decimal(p.kmPerWeek), stringResource(R.string.today_km_per_week), Modifier.weight(1f))
            Stat(decimal(p.workoutsPerWeek), stringResource(R.string.today_workouts_per_week), Modifier.weight(1f))
        }
        if (p.stepsAvg != null || p.sleepAvgMin != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(p.stepsAvg?.toInt()?.toString() ?: "—", stringResource(R.string.today_avg_steps), Modifier.weight(1f))
                Stat(p.sleepAvgMin?.let { "${(it / 60).toInt()} h ${(it % 60).toInt()}" } ?: "—", stringResource(R.string.today_avg_sleep), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitsCard(p: Progress) {
    var open by rememberSaveable { mutableStateOf(false) }
    SectionCard(Modifier.animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.today_habits), Modifier.weight(1f))
            Muted(stringResource(if (open) R.string.today_hide else R.string.today_show))
        }
        if (open) {
            if (p.topFoods.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Muted(stringResource(R.string.today_top_foods))
                p.topFoods.forEachIndexed { i, (name, count) ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("${i + 1}. $name", Modifier.weight(1f))
                        Muted(pluralStringResource(R.plurals.today_times, count, count))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            MealType.entries.forEach { m -> p.mealTimes[m]?.let { Muted(stringResource(R.string.today_meal_time, stringResource(m.labelRes()), it)) } }
            Muted(stringResource(R.string.today_avg_water, decimal(p.waterAvgMl / 1000)))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Muted(label)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TargetEtaLine(eta: TargetEta, targetKg: Double) {
    val target = "${kilos(targetKg)} kg"
    when (eta) {
        TargetEta.Reached -> Text(stringResource(R.string.today_eta_reached, target))
        TargetEta.NotApproaching -> Muted(stringResource(R.string.today_eta_not_approaching, target))
        is TargetEta.On -> {
            val date = formatDay(eta.epochDay, "dMMMMyyyy")
            val weeks = eta.weeks.roundToInt()
            val inWeeks = if (weeks <= 1) stringResource(R.string.today_eta_one_week) else pluralStringResource(R.plurals.today_eta_weeks, weeks, weeks)
            Text(stringResource(R.string.today_eta_on, target, date, inWeeks))
        }
    }
}

/** Weighings as faint dots, the 7-day mean as a line with an area under it and its last value marked. */
@Composable
private fun WeightChart(points: List<DayValue>, trend: List<DayValue>) {
    val accent = MaterialTheme.colorScheme.primary
    val dots = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.surfaceContainerHigh
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labels = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp)) {
        val all = points + trend
        val minDay = all.minOf { it.epochDay }
        val maxDay = all.maxOf { it.epochDay }.coerceAtLeast(minDay + 1)
        val low = all.minOf { it.value }
        val high = all.maxOf { it.value }
        val pad = maxOf(0.3, (high - low) * 0.15)
        val minW = low - pad
        val maxW = high + pad
        val left = 40.dp.toPx()
        val w = size.width - left - 8.dp.toPx()
        fun x(d: Long) = left + (d - minDay).toFloat() / (maxDay - minDay) * w
        fun y(v: Double) = size.height - ((v - minW) / (maxW - minW)).toFloat() * size.height
        listOf(high, (high + low) / 2, low).forEach { v ->
            drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
            val t = labels.measure(decimal(v), labelStyle)
            drawText(t, topLeft = Offset(0f, y(v) - t.size.height / 2))
        }
        if (trend.size >= 2) {
            val line = Path()
            trend.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.epochDay), y(p.value)) else line.lineTo(x(p.epochDay), y(p.value)) }
            val area = Path().apply {
                addPath(line)
                lineTo(x(trend.last().epochDay), size.height)
                lineTo(x(trend.first().epochDay), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f))))
            drawPath(line, accent, style = Stroke(3.dp.toPx()))
        }
        points.forEach { drawCircle(dots, 3.dp.toPx(), Offset(x(it.epochDay), y(it.value))) }
        trend.lastOrNull()?.let {
            drawCircle(surface, 7.dp.toPx(), Offset(x(it.epochDay), y(it.value)))
            drawCircle(accent, 5.dp.toPx(), Offset(x(it.epochDay), y(it.value)))
        }
    }
}

/** Weekly averages side by side: what was eaten and what Garmin says was burned. */
@Composable
private fun WeekBars(weeks: List<WeekBar>) {
    val eatenColor = MaterialTheme.colorScheme.primary
    val burnedColor = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.surfaceContainerHigh
    val labels = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 12.dp)) {
        val max = weeks.maxOf { maxOf(it.eatenAvg, it.burnedAvg ?: 0.0) }.coerceAtLeast(1.0)
        val bottom = size.height - 18.dp.toPx()
        val slot = size.width / weeks.size
        val bar = (slot * 0.32f).coerceAtMost(14.dp.toPx())
        drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        weeks.forEachIndexed { i, wk ->
            val cx = slot * i + slot / 2
            val he = (wk.eatenAvg / max).toFloat() * bottom
            drawRoundRect(eatenColor, Offset(cx - bar - 1.dp.toPx(), bottom - he), Size(bar, he), CornerRadius(bar / 2))
            wk.burnedAvg?.let { b ->
                val hb = (b / max).toFloat() * bottom
                drawRoundRect(burnedColor, Offset(cx + 1.dp.toPx(), bottom - hb), Size(bar, hb), CornerRadius(bar / 2))
            }
            // Only the first and last weeks are labelled; with a year of bars every label would overlap.
            if (i == 0 || i == weeks.lastIndex) {
                val t = labels.measure(shortDate(wk.weekStart), labelStyle)
                val lx = (cx - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(lx, bottom + 4.dp.toPx()))
            }
        }
    }
}

private fun signedKg(v: Double, digits: Int = 1): String = "${if (v > 0) "+" else ""}${decimal(v, digits)} kg"
