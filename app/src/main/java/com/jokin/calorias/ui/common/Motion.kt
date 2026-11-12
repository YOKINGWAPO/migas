package com.jokin.calorias.ui.common

import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

/**
 * One place for how things move. Springs follow Material 3's expressive motion scheme (damping /
 * stiffness), written by hand because that scheme is not in the stable material3 the app uses.
 */
object MotionSpec {
    /** Presses and small toggles. */
    val fast = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    /** Values that change on screen: the day's gauge, totals, bars. */
    val standard = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    /** The day's gauge: overshoots a little so a new total lands with a bit of weight. */
    val bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = 260f)

    /** Screen changes: the old one leaves fast, the new one arrives without ever mixing with it. */
    const val SCREEN_OUT_MS = 90
    const val SCREEN_IN_MS = 210
    /** A row growing into its screen, and shrinking back. */
    const val CONTAINER_MS = 320
    const val CONTAINER_FADE_MS = 120
    /** Going back: what predictive back scrubs through while the finger moves. */
    const val SCREEN_BACK_MS = 260

    /** How long the ✓ stays on a save button before leaving the screen. */
    const val SAVE_CONFIRM_MS = 320L
    /** How long a just-added row stays highlighted. */
    const val HIGHLIGHT_MS = 1200
}

/**
 * True when "Remove animations" is on. Compose already shortens its own animations then; this is for
 * loops the app drives frame by frame (the mascot), which would otherwise keep moving.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Sinks 4 % while pressed and springs back. */
fun Modifier.pressScale(source: InteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, MotionSpec.fast, label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Navigation transitions. Replace Compose's default 700 ms cross-fade, where both screens show at
 * once and the app feels slow, with a fade-through: the old screen is gone before the new one grows in.
 */
object ScreenTransitions {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MotionSpec.SCREEN_IN_MS, MotionSpec.SCREEN_OUT_MS, LinearOutSlowInEasing)) +
            scaleIn(tween(MotionSpec.SCREEN_IN_MS, MotionSpec.SCREEN_OUT_MS, LinearOutSlowInEasing), initialScale = 0.97f)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(MotionSpec.SCREEN_OUT_MS, easing = FastOutLinearInEasing))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(MotionSpec.SCREEN_BACK_MS)) + scaleIn(tween(MotionSpec.SCREEN_BACK_MS), initialScale = 0.97f)
    }
    /** Shrinks the leaving screen: with predictive back it follows the finger. */
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        scaleOut(tween(MotionSpec.SCREEN_BACK_MS), targetScale = 0.9f) + fadeOut(tween(MotionSpec.SCREEN_BACK_MS))
    }
}

/**
 * When a row grows into its screen, the screen underneath has to stay while it grows: both sides
 * fade over the container's own duration instead of the quick fade-through.
 */
object ContainerTransitions {
    val enter: EnterTransition = fadeIn(tween(MotionSpec.CONTAINER_MS))
    val exit: ExitTransition = fadeOut(tween(MotionSpec.CONTAINER_MS, easing = FastOutLinearInEasing))
}

/** Set around the NavHost so a row can grow into the screen it opens. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The animation of the navigation destination a composable belongs to. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A container that morphs into the one with the same [key] on the next screen (a row into its detail
 * screen and back). With a null [key], or outside a NavHost, it is a plain Box.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedContainer(key: Any?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (key == null || shared == null || animated == null) {
        Box(modifier, content = content)
        return
    }
    val morph = with(shared) {
        Modifier.sharedBounds(
            rememberSharedContentState(key),
            animated,
            // Quick swap of contents, so the container reads as one solid card for most of its growth.
            enter = fadeIn(tween(MotionSpec.CONTAINER_FADE_MS)),
            exit = fadeOut(tween(MotionSpec.CONTAINER_FADE_MS)),
            boundsTransform = { _, _ -> tween(MotionSpec.CONTAINER_MS, easing = FastOutSlowInEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
        )
    }
    Box(modifier.then(morph), content = content)
}
