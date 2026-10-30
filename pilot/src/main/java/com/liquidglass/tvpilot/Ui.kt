package com.liquidglass.tvpilot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colours of the app: a deep blue-violet night with glass panels on it, like the launcher. */
object Pal {
    val top = Color(0xFF131C42)
    val bottom = Color(0xFF07080D)
    val accent = Color(0xFF7C9CFF)
    val accent2 = Color(0xFFB36BFF)
    val spotify = Color(0xFF1ED760)
    val muted = Color(0xFFB0B3C0)
    val dim = Color(0xFF8E8E93)
    val good = Color(0xFF43D675)
    val bad = Color(0xFFFF6B61)
    val brand: Brush get() = Brush.linearGradient(listOf(accent, accent2))
}

private val Rim: Brush
    get() = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.28f)))

/** A frosted panel: a faint white wash and a rim that is bright at one corner. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = if (strong) 0.17f else 0.10f), Color.White.copy(alpha = 0.04f))))
        .border(1.dp, Rim, shape)

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Heading(icon: ImageVector, title: String, tint: Color = Color.White) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun BodyText(text: String) {
    Text(text, color = Pal.muted, fontSize = 14.sp, lineHeight = 20.sp)
}

/** A wide button, with an icon in front of the label. [primary] fills it with the brand gradient. */
@Composable
fun PilotButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val fill = if (primary) {
        Pal.brand
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f)))
    }
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.0f else 0.14f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** A round glass button with an icon and a small caption under it. */
@Composable
fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp, tint: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Box(
            modifier = Modifier.size(size).glass(CircleShape, strong = true).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.42f))
        }
        Text(label, color = Pal.muted, fontSize = 12.sp, maxLines = 1)
    }
}

class NavItem(val label: String, val icon: ImageVector)

/** The bottom bar: a glass pill, the chosen tab lit with the brand colours. */
@Composable
fun GlassNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(28.dp), strong = true)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEachIndexed { index, item ->
            val chosen = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (chosen) Brush.linearGradient(listOf(Pal.accent.copy(alpha = 0.40f), Pal.accent2.copy(alpha = 0.34f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(item.icon, contentDescription = null, tint = if (chosen) Color.White else Pal.muted, modifier = Modifier.size(22.dp))
                Text(item.label, color = if (chosen) Color.White else Pal.muted, fontSize = 11.sp, maxLines = 1, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** The Spotify mark, drawn rather than imported: a green disc with three curved bars. */
@Composable
fun SpotifyMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(Pal.spotify, radius = s / 2f)
        val black = Color(0xFF0B0C10)
        listOf(0.30f to 0.075f, 0.425f to 0.06f, 0.55f to 0.05f).forEachIndexed { index, (y, thickness) ->
            val width = s * (0.58f - 0.07f * index)
            val left = (s - width) / 2f
            val top = s * y
            drawArc(
                color = black,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(width, width * 0.55f),
                style = Stroke(width = s * thickness, cap = StrokeCap.Round),
            )
        }
    }
}
