package com.jokin.calorias.ui.theme

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.jokin.calorias.R
import com.jokin.calorias.data.settings.Accent
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.data.settings.ThemeMode
import kotlinx.coroutines.delay

/** The design uses one accent for what matters, greys for the rest and red only for going over. */
val OverColor = Color(0xFFD64545)

/** One colour per macro, used for its bar and for food avatars (the macro that brings most kcal). */
object MacroColors {
    val carbs = Color(0xFFE9A93A)
    val protein = Color(0xFFE2725B)
    val fat = Color(0xFF5B8DD6)
}

/** Glass fill on the water row. */
val WaterColor = Color(0xFF4C9BD8)

/** Display face for big figures and titles (Bricolage Grotesque, OFL; licence in assets/licenses). */
val BrandFont = FontFamily(
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = BrandFont, fontWeight = FontWeight.ExtraBold),
        displayMedium = t.displayMedium.copy(fontFamily = BrandFont, fontWeight = FontWeight.ExtraBold),
        displaySmall = t.displaySmall.copy(fontFamily = BrandFont, fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = BrandFont, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = BrandFont, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = BrandFont, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = BrandFont, fontWeight = FontWeight.Bold),
    )
}

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = lerp(accent, Color.White, 0.82f),
    onPrimaryContainer = lerp(accent, Color.Black, 0.55f),
    secondary = Color(0xFF5F6368),
    secondaryContainer = Color(0xFFE8EAED),
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFF7F7F5),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF0F0EE),
    error = OverColor,
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = lerp(accent, Color.White, 0.35f),
    onPrimary = Color.Black,
    primaryContainer = lerp(accent, Color.Black, 0.55f),
    onPrimaryContainer = lerp(accent, Color.White, 0.75f),
    secondary = Color(0xFFBDC1C6),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF262626),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CaloriasTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val rgb = settings.accent == Accent.RGB && !settings.dynamicColor
    val accent = Color(settings.accent.argb)
    val scheme = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor -> dynamicLightColorScheme(context)
        dark -> darkScheme(accent)
        else -> lightScheme(accent)
    }
    // Edge-to-edge picks icon colours from the system theme; follow the in-app choice instead.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val hue = remember { mutableFloatStateOf(0f) }
    if (rgb && !animationsOff(context)) {
        LaunchedEffect(Unit) {
            while (true) {
                hue.floatValue = (System.currentTimeMillis() % RGB_TURN_MS) / RGB_TURN_MS.toFloat() * 360f
                delay(RGB_TICK_MS)
            }
        }
    }
    val marks = remember(rgb, scheme.primary, dark) { MarkColor(scheme.primary, if (rgb) hue else null, dark) }
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        CompositionLocalProvider(LocalMarkColor provides marks, content = content)
    }
}

fun Accent.color(): Color = Color(argb)

/**
 * Colour of the app's marks: the +, the rings, the highlights. It is the accent, or with RGB a soft
 * hue that keeps turning. Read [value] only while drawing: the change then redraws those marks and
 * never recomposes the screen.
 */
@Stable
class MarkColor(private val fixed: Color, private val hue: State<Float>?, private val dark: Boolean) {
    val turning: Boolean get() = hue != null
    val value: Color get() = hue?.let { softHue(it.value, dark) } ?: fixed
}

val LocalMarkColor = staticCompositionLocalOf { MarkColor(Color.Unspecified, null, false) }

/** Dark ink or white, whichever reads better on [background]; a turning mark goes through light yellows. */
fun contentOn(background: Color): Color = if (background.luminance() > 0.45f) Color(0xFF1B2420) else Color.White

/** The RGB swatch in Settings: the whole wheel at the marks' softness. */
fun rgbWheel(dark: Boolean): List<Color> = (0..6).map { softHue(it * 60f, dark) }

private fun softHue(hue: Float, dark: Boolean): Color =
    if (dark) Color.hsl(hue % 360f, 0.6f, 0.72f) else Color.hsl(hue % 360f, 0.55f, 0.5f)

/** Same check as the app's reduce-motion helper; theme cannot depend on ui.common, which depends on it. */
private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** One turn of the wheel a minute: noticeable without pulling the eye. */
private const val RGB_TURN_MS = 60_000L
/** A tenth of a second moves the hue 0,6°, too little to see as a step, and keeps the app off every frame. */
private const val RGB_TICK_MS = 100L
