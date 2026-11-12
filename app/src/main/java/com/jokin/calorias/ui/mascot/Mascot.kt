package com.jokin.calorias.ui.mascot

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.PetMood
import com.jokin.calorias.domain.StreakReward
import com.jokin.calorias.ui.common.rememberReduceMotion
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The mascot, drawn from code. It blinks now and then, jumps whenever [jumpKey] changes to a non-null
 * value (including the first time it is shown with one), and the
 * legendary and laser rewards loop while it is on screen (unless animations are turned off).
 */
@Composable
fun Mascot(kind: MascotKind, pose: Pose, reward: StreakReward?, modifier: Modifier = Modifier, jumpKey: Any? = null) {
    val art = remember(kind, pose, reward) { mascotArt(kind, pose, reward) }
    val reduceMotion = rememberReduceMotion()
    val loops = !reduceMotion && (reward == StreakReward.LEGENDARY || reward == StreakReward.LASER_EYES)

    val time by produceState(0f, loops) {
        if (!loops) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    val blink = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2_800, 5_500))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(110))
        }
    }
    val jump = remember { Animatable(0f) }
    LaunchedEffect(jumpKey) {
        if (jumpKey == null || reduceMotion) return@LaunchedEffect
        jump.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        jump.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
    }

    Canvas(modifier.graphicsLayer {
        translationY = -jump.value * size.height * 0.09f
        scaleY = 1f + jump.value * 0.04f
        scaleX = 1f - jump.value * 0.03f
        transformOrigin = TransformOrigin(0.5f, 1f)
    }) {
        drawArt(art, Frame(time, blink.value))
    }
}

/** Still picture of the mascot for places that cannot run Compose, such as the home-screen widget. */
fun mascotBitmap(kind: MascotKind, pose: Pose, reward: StreakReward?, px: Int): Bitmap {
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        drawArt(mascotArt(kind, pose, reward), Frame.Still)
    }
    return image.asAndroidBitmap()
}

/** Asleep wins; the legendary reward is always charging; otherwise a save makes it cheer for a moment. */
fun poseFor(mood: PetMood, reward: StreakReward?, cheering: Boolean): Pose = when {
    mood == PetMood.SLEEPING -> Pose.SLEEP
    reward == StreakReward.LEGENDARY -> Pose.CHARGE
    cheering -> Pose.HAPPY
    mood == PetMood.HUNGRY -> Pose.HUNGRY
    else -> Pose.NEUTRAL
}
