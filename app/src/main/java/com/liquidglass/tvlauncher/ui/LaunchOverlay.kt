package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.liquidglass.tvlauncher.data.AppInfo
import kotlin.math.roundToInt

/** An app being opened, and where its tile was on screen. */
class LaunchRequest(val app: AppInfo, val from: Rect)

/**
 * The opening animation: the tile grows out of its own spot to fill the
 * screen while everything else dims to black, and the app is started the
 * moment it is full ([onExpanded]). When the launcher comes back to the
 * front, [expanded] flips to false and the same animation plays in reverse,
 * shrinking back into the tile ([onCollapsed] fires at the end).
 *
 * The tile's artwork is only 400x240, so it fades out well before it is
 * full-screen rather than being blown up into a blur.
 */
@Composable
fun LaunchOverlay(
    request: LaunchRequest?,
    expanded: Boolean,
    onExpanded: () -> Unit,
    onCollapsed: () -> Unit,
) {
    if (request == null) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val progress = remember(request) { Animatable(0f) }

        LaunchedEffect(request, expanded) {
            progress.animateTo(
                targetValue = if (expanded) 1f else 0f,
                animationSpec = tween(durationMillis = if (expanded) 340 else 300, easing = FastOutSlowInEasing),
            )
            if (expanded) onExpanded() else onCollapsed()
        }

        val p = progress.value
        val left = lerp(request.from.left, 0f, p)
        val top = lerp(request.from.top, 0f, p)
        val right = lerp(request.from.right, screenWidth, p)
        val bottom = lerp(request.from.bottom, screenHeight, p)
        val artAlpha = 1f - ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = p)))
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { (right - left).coerceAtLeast(1f).toDp() },
                    height = with(density) { (bottom - top).coerceAtLeast(1f).toDp() },
                )
                .graphicsLayer { alpha = artAlpha }
                .clip(RoundedCornerShape(lerp(12f, 0f, p).dp)),
        ) {
            Image(
                bitmap = request.app.tile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
