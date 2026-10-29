package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import android.os.SystemClock
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Fades in, rises and grows into place once, [delayMillis] after the
 * element first appears. The spring is slightly under-damped, so things
 * settle with a small overshoot instead of stopping dead. Staggering the
 * delays of siblings gives a cascade.
 */
fun Modifier.riseIn(
    delayMillis: Int,
    distance: Dp = 24.dp,
    fromScale: Float = 0.92f,
): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 170f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        compositingStrategy = CompositingStrategy.ModulateAlpha
        translationY = (1f - p) * distance.toPx()
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}

/**
 * A very slow back-and-forth value (the photo's "breathing" zoom and drift) that goes from [from]
 * to [to] and back every [periodMillis] * 2.
 *
 * An ordinary infinite animation ticks on every frame, which keeps the GPU busy for ever and,
 * on a weak TV box, steals time from the animations that matter (moving focus, sliding pages).
 * This one is only refreshed [fps] times a second, which for something that moves a fraction of
 * a pixel per second looks exactly the same, and not at all while [paused] says so (during a
 * page slide).
 */
@Composable
fun rememberSlowOscillation(
    from: Float,
    to: Float,
    periodMillis: Int,
    fps: Int = 20,
    paused: () -> Boolean = { false },
): FloatState {
    val value = remember { mutableFloatStateOf(from) }
    LaunchedEffect(from, to, periodMillis, fps) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            if (!paused()) {
                val t = ((SystemClock.uptimeMillis() - start) % (2L * periodMillis)).toFloat() / periodMillis
                val x = if (t <= 1f) t else 2f - t
                value.floatValue = from + (to - from) * x
            }
            delay(1000L / fps)
        }
    }
    return value
}
