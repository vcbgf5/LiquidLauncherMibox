package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.liquidglass.tvlauncher.data.WeatherKind
import kotlin.math.cos
import kotlin.math.sin

private val Sun = Color(0xFFFFC83D)
private val CloudLight = Color(0xFFF2F4F8)
private val CloudDark = Color(0xFFB9C0CC)
private val Rain = Color(0xFF6CB6FF)
private val Bolt = Color(0xFFFFD60A)

/** Weather icons drawn in code (no image files): sun, moon, clouds, rain, snow, storm, fog. */
@Composable
fun WeatherIcon(kind: WeatherKind, day: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        when (kind) {
            WeatherKind.CLEAR -> if (day) sun(s * 0.5f, s * 0.5f, s * 0.22f) else moon(s * 0.5f, s * 0.5f, s * 0.3f)
            WeatherKind.PARTLY -> {
                if (day) sun(s * 0.38f, s * 0.36f, s * 0.17f) else moon(s * 0.38f, s * 0.36f, s * 0.22f)
                cloud(s * 0.1f, s * 0.38f, s * 0.82f, CloudLight)
            }
            WeatherKind.CLOUDY -> cloud(s * 0.06f, s * 0.26f, s * 0.88f, CloudDark)
            WeatherKind.FOG -> {
                cloud(s * 0.1f, s * 0.12f, s * 0.8f, CloudDark)
                listOf(0.68f, 0.82f).forEach { y ->
                    drawLine(CloudLight, Offset(s * 0.18f, s * y), Offset(s * 0.82f, s * y), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.DRIZZLE -> {
                cloud(s * 0.08f, s * 0.1f, s * 0.84f, CloudDark)
                listOf(0.34f, 0.56f, 0.76f).forEach { x -> drop(s * x, s * 0.72f, s) }
            }
            WeatherKind.RAIN -> {
                cloud(s * 0.08f, s * 0.08f, s * 0.84f, CloudDark)
                listOf(0.3f, 0.5f, 0.7f).forEach { x ->
                    drawLine(Rain, Offset(s * x, s * 0.66f), Offset(s * (x - 0.06f), s * 0.88f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(s * 0.08f, s * 0.08f, s * 0.84f, CloudDark)
                listOf(0.3f to 0.74f, 0.5f to 0.86f, 0.7f to 0.74f).forEach { (x, y) ->
                    drawCircle(Color.White, radius = s * 0.045f, center = Offset(s * x, s * y))
                }
            }
            WeatherKind.STORM -> {
                cloud(s * 0.08f, s * 0.06f, s * 0.84f, CloudDark)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.56f)
                    lineTo(s * 0.4f, s * 0.78f)
                    lineTo(s * 0.5f, s * 0.78f)
                    lineTo(s * 0.44f, s * 0.96f)
                    lineTo(s * 0.64f, s * 0.7f)
                    lineTo(s * 0.54f, s * 0.7f)
                    close()
                }
                drawPath(bolt, Bolt)
            }
        }
    }
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float) {
    drawCircle(Sun, radius = r, center = Offset(cx, cy))
    repeat(8) { i ->
        val angle = Math.toRadians(i * 45.0)
        val from = r * 1.35f
        val to = r * 1.75f
        drawLine(
            Sun,
            Offset(cx + cos(angle).toFloat() * from, cy + sin(angle).toFloat() * from),
            Offset(cx + cos(angle).toFloat() * to, cy + sin(angle).toFloat() * to),
            strokeWidth = r * 0.28f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2))) }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(cx - r * 0.35f, cy - r * 1.2f), Size(r * 1.9f, r * 1.9f)))
    }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), Color(0xFFE8ECF4))
}

private fun DrawScope.cloud(x: Float, y: Float, width: Float, color: Color) {
    val h = width * 0.5f
    drawCircle(color, radius = h * 0.34f, center = Offset(x + width * 0.3f, y + h * 0.58f))
    drawCircle(color, radius = h * 0.44f, center = Offset(x + width * 0.55f, y + h * 0.46f))
    drawCircle(color, radius = h * 0.3f, center = Offset(x + width * 0.78f, y + h * 0.66f))
    drawRoundRect(
        color,
        topLeft = Offset(x + width * 0.14f, y + h * 0.56f),
        size = Size(width * 0.72f, h * 0.42f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.2f),
    )
}

private fun DrawScope.drop(x: Float, y: Float, s: Float) {
    drawCircle(Rain, radius = s * 0.04f, center = Offset(x, y))
    drawLine(Rain, Offset(x, y), Offset(x - s * 0.02f, y + s * 0.12f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
}
