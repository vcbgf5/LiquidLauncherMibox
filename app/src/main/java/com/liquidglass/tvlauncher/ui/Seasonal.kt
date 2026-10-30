package com.liquidglass.tvlauncher.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.data.Performance
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

/** The look of the season, or none in summer, when a bare sky needs no decoration. */
private enum class Season { WINTER, SPRING, AUTUMN }

private fun currentSeason(): Season? = when (Calendar.getInstance().get(Calendar.MONTH)) {
    Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Season.WINTER
    Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
    Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> Season.AUTUMN
    else -> null // summer: nothing drifts
}

private class Flake(seed: Random) {
    val x = seed.nextFloat()
    val size = 3f + seed.nextFloat() * 6f
    val speed = 0.5f + seed.nextFloat() * 0.9f
    val sway = 0.015f + seed.nextFloat() * 0.03f
    val phase = seed.nextFloat() * 1000f
    val spin = (seed.nextFloat() - 0.5f) * 90f
}

/**
 * A faint drift of snow, falling leaves or blossom petals behind the home screen's tiles, picked
 * from today's month (nothing in summer). Off entirely in the light performance mode and when the
 * setting is off: it is decoration, never something the rest of the screen depends on.
 */
@Composable
fun SeasonalOverlay(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled || Performance.lite) return
    val season = remember { currentSeason() } ?: return
    val flakes = remember { List(16) { Flake(Random(it * 7919 + 13)) } }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            time.floatValue = (SystemClock.uptimeMillis() - start) / 1000f
            delay(1000L / 12) // smooth enough for something this slow, light on a weak box
        }
    }
    val color = when (season) {
        Season.WINTER -> Color.White
        Season.SPRING -> Color(0xFFFFD4E8)
        Season.AUTUMN -> Color(0xFFE2A25A)
    }
    Canvas(modifier = modifier) {
        val t = time.floatValue
        val px = 1.dp.toPx()
        flakes.forEach { flake ->
            val fall = ((t * flake.speed * 0.035f) + flake.phase) % 1.2f - 0.1f
            val y = fall * size.height
            val x = (flake.x + sin((t * flake.sway) + flake.phase) * 0.06f).mod(1f) * size.width
            val alpha = (0.22f * (1f - (fall - 0.5f).let { it * it } * 1.4f)).coerceIn(0.05f, 0.22f)
            if (season == Season.WINTER) {
                drawCircle(color.copy(alpha = alpha), radius = flake.size * px * 0.5f, center = Offset(x, y))
            } else {
                rotate(degrees = flake.spin + t * 6f, pivot = Offset(x, y)) {
                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(x - flake.size * px * 0.5f, y - flake.size * px * 0.32f),
                        size = Size(flake.size * px, flake.size * px * 0.64f),
                        cornerRadius = CornerRadius(flake.size * px * 0.32f),
                    )
                }
            }
        }
    }
}
