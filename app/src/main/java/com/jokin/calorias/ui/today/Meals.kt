package com.jokin.calorias.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jokin.calorias.R
import com.jokin.calorias.data.db.EntryWithFood
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.data.settings.WaterUnit
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.AvatarStack
import com.jokin.calorias.ui.common.FoodAvatar
import com.jokin.calorias.ui.common.MotionSpec
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SharedContainer
import com.jokin.calorias.ui.common.amountText
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.food.entryContainerKey
import com.jokin.calorias.ui.theme.LocalMarkColor
import com.jokin.calorias.ui.theme.OverColor
import com.jokin.calorias.ui.theme.WaterColor
import kotlin.math.ceil

private val MealType.icon: ImageVector
    get() = when (this) {
        MealType.BREAKFAST -> Icons.Outlined.FreeBreakfast
        MealType.LUNCH -> Icons.Outlined.LunchDining
        MealType.DINNER -> Icons.Outlined.Bedtime
        MealType.SNACK -> Icons.Outlined.Cookie
    }

/**
 * The four meals as rows in one card. A row shows what was eaten as avatars and opens to list it;
 * an empty row adds to that meal. A meal that just got something lights up once.
 */
@Composable
fun MealsCard(
    entries: List<EntryWithFood>,
    expanded: Set<MealType>,
    highlighted: Set<Long>,
    onToggle: (MealType) -> Unit,
    onAdd: (MealType, Offset) -> Unit,
    onOpen: (EntryWithFood) -> Unit,
    onDelete: (EntryWithFood) -> Unit,
    onCopyYesterday: (MealType) -> Unit,
    onSaveCombo: (MealType) -> Unit,
) {
    SectionCard(Modifier.animateContentSize()) {
        MealType.entries.forEachIndexed { i, meal ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            val mealEntries = entries.filter { it.entry.meal == meal }
            MealRow(
                meal, mealEntries, meal in expanded, mealEntries.any { it.entry.id in highlighted }, highlighted,
                onToggle = { onToggle(meal) },
                onAdd = { onAdd(meal, it) },
                onOpen = onOpen,
                onDelete = onDelete,
                onCopyYesterday = { onCopyYesterday(meal) },
                onSaveCombo = { onSaveCombo(meal) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealRow(
    meal: MealType,
    entries: List<EntryWithFood>,
    expanded: Boolean,
    lit: Boolean,
    highlighted: Set<Long>,
    onToggle: () -> Unit,
    onAdd: (Offset) -> Unit,
    onOpen: (EntryWithFood) -> Unit,
    onDelete: (EntryWithFood) -> Unit,
    onCopyYesterday: () -> Unit,
    onSaveCombo: () -> Unit,
) {
    val total = Nutrients.sum(entries.map { it.food.forGrams(it.entry.grams) })
    var center by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf(false) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(lit) {
        if (lit) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(MotionSpec.HIGHLIGHT_MS))
        }
    }
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, MotionSpec.fast, label = "chevron")
    val marks = LocalMarkColor.current
    val mealName = stringResource(meal.labelRes())
    Column {
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { center = it.boundsInRoot().center }
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind { drawRoundRect(marks.value.copy(alpha = 0.16f * glow.value), cornerRadius = CornerRadius(12.dp.toPx())) }
                    .combinedClickable(onLongClick = { menu = true }) { if (entries.isEmpty()) onAdd(center) else onToggle() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(meal.icon, contentDescription = null, Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mealName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (entries.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        AvatarStack(entries.map { it.food.name to it.food.forGrams(it.entry.grams).dominantMacro() }, ring = MaterialTheme.colorScheme.surfaceContainer)
                    }
                }
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.today_add), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("${kcal(total.kcal)} kcal", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = stringResource(if (expanded) R.string.today_collapse_meal else R.string.today_expand_meal, mealName),
                        modifier = Modifier.size(14.dp).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.today_add_to_meal, mealName.lowercase())) }, onClick = { menu = false; onAdd(center) })
                DropdownMenuItem(text = { Text(stringResource(R.string.today_copy_yesterday)) }, onClick = { menu = false; onCopyYesterday() })
                if (entries.size >= 2) DropdownMenuItem(text = { Text(stringResource(R.string.today_save_as_combo)) }, onClick = { menu = false; onSaveCombo() })
            }
        }
        AnimatedVisibility(expanded && entries.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.animateContentSize()) {
                entries.forEach { e -> key(e.entry.id) { EntryRow(e, e.entry.id in highlighted, onOpen, onDelete) } }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onAdd(center) }) { Text(stringResource(R.string.today_add)) }
                    TextButton(onClick = onCopyYesterday) { Text(stringResource(R.string.today_copy_yesterday)) }
                    if (entries.size >= 2) TextButton(onClick = onSaveCombo) { Text(stringResource(R.string.today_save_combo)) }
                }
            }
        }
    }
}

/** Swipe left to delete; tap to open it, the row growing into its screen. */
@Composable
private fun EntryRow(e: EntryWithFood, highlighted: Boolean, onOpen: (EntryWithFood) -> Unit, onDelete: (EntryWithFood) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState()
    val glow = remember { Animatable(if (highlighted) 1f else 0f) }
    LaunchedEffect(Unit) { if (glow.value > 0f) glow.animateTo(0f, tween(MotionSpec.HIGHLIGHT_MS)) }
    val marks = LocalMarkColor.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val n = e.food.forGrams(e.entry.grams)
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDelete(e)
            }
        },
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(OverColor).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.today_delete), tint = Color.White)
            }
        },
    ) {
        SharedContainer(entryContainerKey(e.entry.id), Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().background(surface)
                    .drawBehind { drawRect(marks.value.copy(alpha = 0.16f * glow.value)) }
                    .clickable { onOpen(e) }.padding(start = 52.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoodAvatar(e.food.name, n.dominantMacro(), size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted(amountText(e.food, e.entry.grams))
                }
                Text(kcal(n.kcal), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Water as glasses that fill when tapped: an empty glass adds one, a full one takes the last back.
 * More than fits on the row shows as "+N".
 */
@Composable
fun WaterRow(waterMl: Int, s: AppSettings, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val goalGlasses = ceil(s.waterGoalMl.toDouble() / s.glassMl).toInt().coerceIn(1, MAX_GLASSES)
    val glasses = waterMl.toFloat() / s.glassMl
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_water), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Muted(
                    if (s.waterUnit == WaterUnit.GLASSES) pluralStringResource(R.plurals.today_water_glasses, goalGlasses, decimal(glasses.toDouble()), goalGlasses)
                    else stringResource(R.string.today_water_litres, decimal(waterMl / 1000.0), decimal(s.waterGoalMl / 1000.0))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val full = glasses.toInt()
                val removeGlass = stringResource(R.string.today_remove_glass)
                val addGlass = stringResource(R.string.today_add_glass)
                for (i in 0 until goalGlasses) {
                    val level = (glasses - i).coerceIn(0f, 1f)
                    Glass(level, Modifier.semantics { contentDescription = if (i < full) removeGlass else addGlass }) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (i < full) onUndo() else onAdd(s.glassMl)
                    }
                }
                if (full > goalGlasses) Text("+${full - goalGlasses}", style = MaterialTheme.typography.labelLarge, color = WaterColor)
            }
        }
    }
}

@Composable
private fun Glass(level: Float, modifier: Modifier, onClick: () -> Unit) {
    val fill by animateFloatAsState(level, MotionSpec.bouncy, label = "glass")
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(20.dp, 28.dp).clickable(onClick = onClick)) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.1f, h * 0.08f)
            lineTo(w * 0.9f, h * 0.08f)
            lineTo(w * 0.78f, h * 0.92f)
            lineTo(w * 0.22f, h * 0.92f)
            close()
        }
        if (fill > 0f) clipPath(glass) {
            val top = h * 0.92f - (h * 0.84f) * fill
            drawRect(WaterColor, Offset(0f, top), Size(w, h - top))
        }
        drawPath(glass, outline, style = Stroke(1.4.dp.toPx()))
    }
}

private const val MAX_GLASSES = 10
