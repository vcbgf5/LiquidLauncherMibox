package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TileShape = RoundedCornerShape(12.dp)
private val GlowColor = Color(0xFF9DBBFF)

/**
 * One app tile: a wide 5:3 card that sits flat until it gets D-pad focus,
 * then lifts, grows, picks up a white ring and a soft blue glow, and
 * fades its name in underneath — all on springs, so moving the focus
 * around feels like one continuous motion instead of a snap.
 *
 * OK = open the app. Holding OK (or long-pressing with a pointer) = [onLongClick].
 * Width comes from [modifier]; the height follows from the 5:3 ratio.
 */
@Composable
fun AppTile(
    label: String,
    art: ImageBitmap,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val longFired = remember { booleanArrayOf(false) }

    val motion = spring<Float>(dampingRatio = 0.62f, stiffness = 380f)
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, motion, label = "scale")
    val lift by animateFloatAsState(if (focused) -8f else 0f, motion, label = "lift")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "glow")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "labelAlpha")

    Column(
        modifier = modifier
            .graphicsLayer {
                translationY = lift * density
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val repeats = event.nativeKeyEvent.repeatCount
                        if (repeats == 0) longFired[0] = false
                        // The first auto-repeat of a held key is the long press.
                        if (repeats == 1 && !longFired[0]) {
                            longFired[0] = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val wasLong = longFired[0]
                        longFired[0] = false
                        if (!wasLong) onClick()
                        true
                    }
                    else -> false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 3f)
                .shadow(
                    elevation = (2f + 20f * glow).dp,
                    shape = TileShape,
                    clip = false,
                    spotColor = lerp(Color.Black, GlowColor, glow),
                )
                .clip(TileShape)
                .border(2.dp, Color.White.copy(alpha = 0.9f * glow), TileShape),
        ) {
            Image(
                bitmap = art,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Glassy sheen across the top edge, only while focused.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.22f * glow), Color.Transparent),
                            endY = 140f,
                        ),
                    ),
            )
        }

        // Always reserves its row so tiles never shift when the name appears.
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(18.dp)
                .graphicsLayer {
                    alpha = labelAlpha
                    translationY = (1f - labelAlpha) * -4f * density
                },
        )
    }
}
