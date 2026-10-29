package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreensaverLocale = Locale("pl", "PL")

/**
 * The screensaver: the current wallpaper full screen (slowly zooming and drifting, cross-fading
 * whenever the wallpaper changes) with a big clock and the date in "liquid glass" lettering.
 *
 * The clock stands exactly in the middle; it is the photo behind it that keeps drifting, so
 * the lettering never burns into the same pixels. It fades in when [visible] turns true and out
 * when it turns false; while it is fully gone nothing of it is composed.
 */
@Composable
fun Screensaver(wallpaper: WallpaperState, visible: Boolean, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        shown.animateTo(if (visible) 1f else 0f, tween(if (visible) 1600 else 350))
    }
    if (!visible && shown.value <= 0f) return

    val zoom = rememberSlowOscillation(from = 1.02f, to = 1.12f, periodMillis = 45_000)
    val panX = rememberSlowOscillation(from = -1f, to = 1f, periodMillis = 60_000)
    val panY = rememberSlowOscillation(from = -1f, to = 1f, periodMillis = 85_000)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = shown.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.floatValue
                    scaleY = zoom.floatValue
                    translationX = panX.floatValue * widthPx * 0.012f
                    translationY = panY.floatValue * heightPx * 0.012f
                },
        ) {
            wallpaper.previous?.let { old ->
                Image(
                    bitmap = old.full,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Image(
                bitmap = wallpaper.current.full,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (wallpaper.previous != null) wallpaper.fade.value else 1f },
            )
        }
        // A little darkening so the lettering reads on bright photos too.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.40f)),
                    ),
                ),
        )

        val now = rememberNow()
        GlassClock(widthDp = maxWidth, nowMillis = now.value, scale = 0.2f, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * A big clock with the date under it, in glass lettering, centred in the space it is given
 * (the digits dead centre, the date right under them). Its size follows the screen width:
 * the digits are [scale] of it.
 */
@Composable
internal fun GlassClock(
    widthDp: Dp,
    nowMillis: Long,
    scale: Float,
    showDate: Boolean = true,
    extraLine: String? = null,
    modifier: Modifier = Modifier,
) {
    val date = Date(nowMillis)
    val clockSize = (widthDp.value * scale).sp
    val clockDp = with(LocalDensity.current) { clockSize.toDp() }
    val dateOffset = clockDp * 0.6f
    val extraOffset = clockDp * 0.86f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        GlassText(
            text = SimpleDateFormat("HH:mm", ScreensaverLocale).format(date),
            size = clockSize,
            weight = FontWeight.Bold,
            tracking = 0.sp,
        )
        if (showDate) {
            GlassText(
                text = SimpleDateFormat("EEEE, d MMMM", ScreensaverLocale).format(date).replaceFirstChar { it.uppercase() },
                size = clockSize * 0.17f,
                weight = FontWeight.SemiBold,
                tracking = 0.sp,
                modifier = Modifier.offset(y = dateOffset),
            )
            if (extraLine != null) {
                GlassText(
                    text = extraLine,
                    size = clockSize * 0.12f,
                    weight = FontWeight.Medium,
                    tracking = 0.sp,
                    modifier = Modifier.offset(y = extraOffset),
                )
            }
        }
    }
}

/**
 * Lettering that looks like frosted, liquid glass, built from three copies of the text on top
 * of each other: a soft glow around the letters, a body that is bright at the top and
 * clearer at the bottom so the photo shows through it, and a thin crisp edge.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun GlassText(text: String, size: TextUnit, weight: FontWeight, tracking: TextUnit, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.toPx() }
    val base = TextStyle(fontSize = size, fontWeight = weight, letterSpacing = tracking)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style = base.copy(
                color = Color.White.copy(alpha = 0.30f),
                shadow = Shadow(Color.White.copy(alpha = 0.9f), Offset.Zero, blurRadius = px * 0.30f),
            ),
        )
        BasicText(
            text = text,
            style = base.copy(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.32f))),
            ),
        )
        BasicText(
            text = text,
            style = base.copy(
                color = Color.White.copy(alpha = 0.8f),
                drawStyle = Stroke(width = (px * 0.012f).coerceAtLeast(1.5f)),
            ),
        )
    }
}
