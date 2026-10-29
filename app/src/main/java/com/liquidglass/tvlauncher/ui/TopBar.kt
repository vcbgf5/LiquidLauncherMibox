package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clock, settings and Google account, top right of the home screen.
 * The icons are drawn in code so the app needs no icon library.
 */
@Composable
fun TopBar(
    accountName: String?,
    updateAvailable: Boolean,
    showClock: Boolean,
    onUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(15_000)
        }
    }

    Row(
        modifier = modifier
            // Anything above the top bar is the info page: Up from here goes there.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUp()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClock) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 10f)),
            )
        }
        TopBarButton(onClick = onOpenSettings) {
            Box(modifier = Modifier.size(24.dp)) {
                GearIcon(Modifier.fillMaxSize())
                if (updateAvailable) {
                    // A small red dot: a newer version is waiting in the settings.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5A4F)),
                    )
                }
            }
        }
        TopBarButton(onClick = onOpenAccount) { AccountIcon(accountName) }
    }
}

@Composable
private fun TopBarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "buttonScale",
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "buttonGlow")

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f + 0.22f * glow))
            .border(1.5.dp, Color.White.copy(alpha = 0.25f + 0.65f * glow), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun GearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val ringRadius = side * 0.27f
        val ringWidth = side * 0.13f
        val outer = ringRadius + ringWidth / 2f
        val toothWidth = ringWidth * 0.95f
        val toothLength = side * 0.12f
        drawCircle(Color.White, radius = ringRadius, style = Stroke(width = ringWidth))
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = center) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(center.x - toothWidth / 2f, center.y - outer - toothLength + ringWidth * 0.2f),
                    size = Size(toothWidth, toothLength),
                    cornerRadius = CornerRadius(ringWidth * 0.25f),
                )
            }
        }
    }
}

@Composable
private fun AccountIcon(accountName: String?) {
    if (accountName.isNullOrBlank()) {
        // No account known yet: a plain person silhouette.
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White, radius = w * 0.19f, center = Offset(w / 2f, h * 0.36f))
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.14f, h * 0.60f),
                size = Size(w * 0.72f, h * 0.60f),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF7C9CFF), Color(0xFFB36BFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = accountName.first().uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
