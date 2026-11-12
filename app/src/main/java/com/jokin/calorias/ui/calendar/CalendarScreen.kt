package com.jokin.calorias.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.data.health.Workout
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.formatDay
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.today
import com.jokin.calorias.ui.theme.LocalMarkColor
import com.jokin.calorias.ui.theme.MacroColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

const val OPEN_DAY_KEY = "open_day"

/** How a logged day went against its own target; the same ±10 % that Progress counts as "in goal". */
private enum class DayOutcome { IN_GOAL, OVER, LOGGED }

private fun outcome(eatenKcal: Double, target: Int?): DayOutcome = when {
    target == null || target <= 0 -> DayOutcome.LOGGED
    abs(eatenKcal - target) <= target * IN_GOAL_MARGIN -> DayOutcome.IN_GOAL
    eatenKcal > target -> DayOutcome.OVER
    else -> DayOutcome.LOGGED
}

private const val IN_GOAL_MARGIN = 0.10

@Composable
fun CalendarScreen(nav: NavController) {
    val c = appContainer()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val first = ym.atDay(1).toEpochDay()
    val last = ym.atEndOfMonth().toEpochDay()
    var selected by rememberSaveable { mutableLongStateOf(today()) }

    val entries by remember(first) { c.log.observeRange(first, last) }.collectAsState(initial = emptyList())
    val notes by remember(first) { c.db.noteDao().observeRange(first, last) }.collectAsState(initial = emptyList())
    val waters by remember(first) { c.db.waterDao().observeRange(first, last) }.collectAsState(initial = emptyList())
    val photoDays by remember(first) { c.db.bodyDao().observePhotoDays(first, last) }.collectAsState(initial = emptyList())
    val goal by remember { c.energy.observeGoal() }.collectAsState(initial = null)
    var workouts by remember(first) { mutableStateOf<List<Workout>>(emptyList()) }
    var targets by remember(first) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var healthError by remember(first) { mutableStateOf<String?>(null) }
    LaunchedEffect(first, goal) {
        try {
            workouts = c.health.workouts(first, last)
            goal?.let { targets = c.energy.targetsFor(it, first..last, today()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            healthError = e.message ?: e.javaClass.simpleName
        }
    }

    val eatenByDay = entries.groupBy { it.entry.epochDay }.mapValues { (_, l) -> Nutrients.sum(l.map { it.food.forGrams(it.entry.grams) }) }
    val workoutsByDay = workouts.groupBy { it.epochDay }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_diary), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDay(first, "MMMMyyyy").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.today_prev_month)) }
            IconButton(onClick = { month = ym.plusMonths(1).toString() }, enabled = ym < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.today_next_month))
            }
        }
        SectionCard {
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach {
                    Text(it.getDisplayName(TextStyle.NARROW, appLocale()), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            // The month slides in from the side it comes from.
            AnimatedContent(
                targetState = ym,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { dir * it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -dir * it / 4 } + fadeOut())
                },
                label = "month",
            ) { shown ->
                val f = shown.atDay(1).toEpochDay()
                val leading = shown.atDay(1).dayOfWeek.value - 1
                val cells = List(leading) { null } + (f..shown.atEndOfMonth().toEpochDay()).toList()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { day ->
                                Box(Modifier.weight(1f).aspectRatio(0.9f)) {
                                    if (day != null) {
                                        val eaten = eatenByDay[day]
                                        DayCell(
                                            day, day == selected, eaten?.let { outcome(it.kcal, targets[day]) },
                                            day in photoDays, workoutsByDay[day].orEmpty().isNotEmpty(),
                                        ) { selected = day }
                                    }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(MaterialTheme.colorScheme.primaryContainer, stringResource(R.string.today_in_goal))
                Legend(OverFill, stringResource(R.string.today_over))
                Legend(MaterialTheme.colorScheme.surfaceContainerHigh, stringResource(R.string.today_logged))
            }
        }
        healthError?.let { Muted(stringResource(R.string.today_health_read_failed, it)) }

        val dayEntries = entries.filter { it.entry.epochDay == selected }
        val total = eatenByDay[selected] ?: Nutrients.ZERO
        SectionCard {
            Text(dayTitle(selected, today()), style = MaterialTheme.typography.titleLarge)
            Muted(formatDay(selected, "EEEEdMMMM"))
            Spacer(Modifier.height(8.dp))
            if (dayEntries.isEmpty()) {
                Muted(stringResource(R.string.today_no_meals_logged))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(kcal(total.kcal), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(6.dp))
                    Muted(targets[selected]?.let { stringResource(R.string.today_of_kcal, it) } ?: "kcal")
                }
                Muted(stringResource(R.string.today_macros_line, total.protein.roundToInt(), total.carbs.roundToInt(), total.fat.roundToInt()))
            }
            val water = waters.filter { it.epochDay == selected }.sumOf { it.ml }
            if (water > 0) DetailLine(Icons.Outlined.WaterDrop, stringResource(R.string.today_water_line, decimal(water / 1000.0)))
            workoutsByDay[selected].orEmpty().forEach { w ->
                DetailLine(Icons.AutoMirrored.Outlined.DirectionsRun, "${w.title ?: stringResource(w.type.labelRes())} · ${w.minutes} min" + (w.distanceM?.let { " · ${decimal(it / 1000, 2)} km" } ?: ""))
            }
            notes.firstOrNull { it.epochDay == selected }?.let { DetailLine(Icons.Outlined.EditNote, it.text) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                nav.getBackStackEntry(Routes.TODAY).savedStateHandle[OPEN_DAY_KEY] = selected
                nav.popBackStack(Routes.TODAY, inclusive = false)
            }) { Text(stringResource(R.string.today_open_day)) }
        }
    }
}

private val OverFill = MacroColors.carbs.copy(alpha = 0.3f)

@Composable
private fun DayCell(day: Long, selected: Boolean, outcome: DayOutcome?, hasPhoto: Boolean, hasWorkout: Boolean, onClick: () -> Unit) {
    val isToday = day == today()
    val shape = RoundedCornerShape(10.dp)
    val marks = LocalMarkColor.current
    val fill = when (outcome) {
        DayOutcome.IN_GOAL -> if (marks.turning) Color.Transparent else MaterialTheme.colorScheme.primaryContainer
        DayOutcome.OVER -> OverFill
        DayOutcome.LOGGED -> MaterialTheme.colorScheme.surfaceContainerHigh
        null -> Color.Transparent
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(fill)
            .drawBehind { if (marks.turning && outcome == DayOutcome.IN_GOAL) drawRect(marks.value.copy(alpha = 0.3f)) }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .clickable(enabled = day <= today(), onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            LocalDate.ofEpochDay(day).dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
            color = when {
                day > today() -> MaterialTheme.colorScheme.outlineVariant
                outcome == DayOutcome.IN_GOAL -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (hasWorkout) Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = stringResource(R.string.today_workout), Modifier.size(11.dp))
            if (hasPhoto) Icon(Icons.Outlined.CameraAlt, contentDescription = stringResource(R.string.today_photo), Modifier.size(11.dp))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
