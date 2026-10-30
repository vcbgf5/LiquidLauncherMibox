package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The classic "tube TV" power-off: whatever is on screen is squeezed from the top and bottom into
 * one bright line across the middle (two black bars closing in over it, as if the caller's own
 * screen were still showing underneath), the line shrinks to a dot, and the dot winks out — the
 * effect old Samsungs (and CRTs before them) show when switched off. Plays over [onFinished], which
 * the caller uses to send the box the real shutdown once the screen has visibly "turned off".
 */
@Composable
fun PowerOffAnimation(onFinished: () -> Unit) {
    // close: the black bars meeting in the middle. line: the bright seam's width shrinking to a
    // dot once they have. fade: the dot winking out at the very end.
    val close = remember { Animatable(0f) }
    val line = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        close.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(70) // the bright seam held still for a beat, like the phosphor glow draining
        line.animateTo(0f, tween(260, easing = LinearOutSlowInEasing))
        fade.animateTo(0f, tween(180))
        onFinished()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val halfHeight = maxHeight / 2f
        // Two bars closing in from the top and bottom; what they haven't covered yet is the
        // caller's own screen, composed underneath this overlay, showing through as a shrinking band.
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(halfHeight * close.value).background(Color.Black))
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(halfHeight * close.value).background(Color.Black))
        // The bright seam where the picture was squeezed to nothing: full width, then a shrinking dot.
        if (close.value > 0.85f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(line.value.coerceAtLeast(0.006f))
                    .height(2.dp)
                    .alpha(fade.value)
                    .background(Color.White),
            )
        }
    }
}
