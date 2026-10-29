package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TileShape = RoundedCornerShape(12.dp)
private val GlowColor = Color(0xFF9DBBFF)

/**
 * One app tile: a wide 5:3 card that sits flat until it gets D-pad focus.
 *
 * On focus it springs up and out, picks up a white ring and a soft blue
 * glow, a band of light sweeps diagonally across it once, and its name
 * fades in underneath. Pressing OK squashes it back for a moment, like a
 * real button. All of it runs on springs, so moving focus around feels
 * like one continuous motion instead of a snap.
 *
 * OK = [onClick], told where the tile is on screen right now (so the
 * launch animation can start from exactly there). Holding OK, or
 * long-pressing with a pointer = [onLongClick].
 * Width comes from [modifier]; the height follows from the 5:3 ratio.
 * [reserveLabelSpace] = false lets the name hang below the tile without taking space.
 * [enterProgress] (0..1) lets a screen fade/slide its tiles in as a cascade.
 * [onFocusChange] tells a screen which tile has focus (used to find its edges).
 */
@Composable
fun AppTile(
    label: String,
    art: ImageBitmap,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    reserveLabelSpace: Boolean = true,
    enterProgress: (() -> Float)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val longFired = remember { booleanArrayOf(false) }
    // Kept as a reference, not a value: bounds are read at click time, so they
    // include whatever the screen transition and the focus scale are doing then.
    val artCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bounds(): Rect = artCoordinates[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero

    val motion = spring<Float>(dampingRatio = 0.62f, stiffness = 380f)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else if (focused) 1.14f else 1f,
        animationSpec = motion,
        label = "scale",
    )
    val lift by animateFloatAsState(if (focused) -8f else 0f, motion, label = "lift")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "glow")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "labelAlpha")

    // 0 -> 1 once each time the tile gains focus; drives the sweeping highlight.
    val sheen = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        sheen.snapTo(0f)
        if (focused) sheen.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                val enter = enterProgress?.invoke() ?: 1f
                translationY = (lift + (1f - enter) * 36f) * density
                scaleX = scale
                scaleY = scale
                alpha = enter
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .onKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val repeats = event.nativeKeyEvent.repeatCount
                        if (repeats == 0) {
                            longFired[0] = false
                            pressed = true
                        }
                        // The first auto-repeat of a held key is the long press.
                        if (repeats == 1 && !longFired[0]) {
                            longFired[0] = true
                            pressed = false
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val wasLong = longFired[0]
                        longFired[0] = false
                        pressed = false
                        if (!wasLong) onClick(bounds())
                        true
                    }
                    else -> false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick(bounds()) }, onLongPress = { onLongClick() })
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 3f)
                .onGloballyPositioned { artCoordinates[0] = it }
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
            // Soft sheen across the top edge, only while focused.
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
            // The band of light that sweeps across once when focus arrives.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val t = sheen.value
                        if (t > 0f && t < 1f) {
                            val w = size.width
                            val x = -w * 0.5f + t * w * 2.0f
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                                    start = Offset(x, 0f),
                                    end = Offset(x + w * 0.45f, size.height * 0.45f),
                                ),
                            )
                        }
                    },
            )
        }

        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                // Without reserved space the name takes no layout height at all and is
                // just drawn under the tile, which keeps the tile itself centred in
                // whatever holds it (the dock puts the name in its bottom padding).
                .then(
                    if (reserveLabelSpace) {
                        Modifier
                    } else {
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, 0) { placeable.place(0, 0) }
                        }
                    },
                )
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
