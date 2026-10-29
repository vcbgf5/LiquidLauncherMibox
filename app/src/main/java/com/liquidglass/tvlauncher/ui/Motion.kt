package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
        translationY = (1f - p) * distance.toPx()
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}
