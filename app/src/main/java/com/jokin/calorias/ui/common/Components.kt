package com.jokin.calorias.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jokin.calorias.R
import com.jokin.calorias.domain.Macro
import com.jokin.calorias.ui.theme.BrandFont
import com.jokin.calorias.ui.theme.MacroColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Snackbar shared by every screen to report results and errors. */
val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("no snackbar host") }

/** Outlives single screens: for messages that must stay after leaving one, such as "Deshacer". */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope> { error("no app scope") }

/**
 * Runs a user action and shows any failure as a message; nothing is swallowed. Cancellation
 * is rethrown so leaving a screen still stops its work.
 */
fun CoroutineScope.runAction(snackbar: SnackbarHostState, onSuccessMessage: String? = null, block: suspend () -> Unit) {
    launch {
        try {
            block()
            onSuccessMessage?.let { snackbar.showSnackbar(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbar.showSnackbar(e.message ?: e.javaClass.simpleName)
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimals: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 8 && new.all { it.isDigit() || (decimals && (it == ',' || it == '.')) }) onValueChange(new) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = value.isNotEmpty() && parseDecimal(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * Primary save button: runs [onSave], turns into a ✓ with a confirmation buzz, and calls [onSaved]
 * a moment later (usually to leave the screen). Errors go to the snackbar and leave the button as it was.
 */
@Composable
fun SaveButton(text: String, onSave: suspend () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    var done by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (done) return@Button
            scope.runAction(snackbar) {
                onSave()
                done = true
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                delay(MotionSpec.SAVE_CONFIRM_MS)
                onSaved()
            }
        },
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(52.dp).pressScale(source),
    ) {
        AnimatedContent(done, label = "save") { saved ->
            if (saved) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.saved)) else Text(text)
        }
    }
}

/**
 * A whole number whose digits roll like an odometer: only the digits that change move, upwards when
 * the value grows and downwards when it shrinks. Digits are keyed from the right so units stay units.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    var previous by remember { mutableIntStateOf(value) }
    val growing = value > previous
    SideEffect { previous = value }
    val text = value.toString()
    Row(modifier) {
        text.forEachIndexed { i, digit ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = digit,
                    transitionSpec = {
                        val dir = if (growing) 1 else -1
                        (slideInVertically(DIGIT_SPRING) { dir * it } + fadeIn()) togetherWith
                            (slideOutVertically(DIGIT_SPRING) { -dir * it } + fadeOut()) using SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { d -> Text(d.toString(), style = style, color = color) }
            }
        }
    }
}

private val DIGIT_SPRING = spring(dampingRatio = 0.72f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)

fun Macro?.color(): Color = when (this) {
    Macro.CARBS -> MacroColors.carbs
    Macro.PROTEIN -> MacroColors.protein
    Macro.FAT -> MacroColors.fat
    null -> Color(0xFF8E9A93)
}

/** A food as a coloured initial: the colour is the macro that brings most of its kcal. */
@Composable
fun FoodAvatar(name: String, macro: Macro?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(modifier.size(size).clip(CircleShape).background(macro.color()), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "·",
            color = Color.White,
            fontFamily = BrandFont,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.46f).sp,
            lineHeight = (size.value * 0.46f).sp,
        )
    }
}

/** Overlapping avatars, at most [max]; each has a ring in [ring] so the overlap reads. */
@Composable
fun AvatarStack(foods: List<Pair<String, Macro?>>, ring: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, max: Int = 6) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-(size / 3))) {
        foods.take(max).forEach { (name, macro) ->
            FoodAvatar(name, macro, Modifier.border(2.dp, ring, CircleShape), size)
        }
        if (foods.size > max) {
            Box(Modifier.size(size).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Text("+${foods.size - max}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
