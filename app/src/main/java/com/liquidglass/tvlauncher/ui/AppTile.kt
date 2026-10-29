package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.liquidglass.tvlauncher.data.Performance
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Whether tiles get the tvOS-style light (set from the launcher's settings). */
val LocalTileLighting = compositionLocalOf { true }

private val TileShape = RoundedCornerShape(12.dp)

// Built once: gradients with open ends fit any tile size, so nothing is allocated while drawing.
private val TileLightBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.17f),
    0.40f to Color.Transparent,
    0.58f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.36f),
)
private val TileSheenBrush = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
    startY = 0f,
    endY = 140f,
)
private val TiltSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 90f)

/**
 * One app tile: a wide 5:3 card that sits flat until it gets D-pad focus.
 *
 * On focus it springs up and out, picks up a white ring and a soft glow in the accent colour, and
 * (unless the light mode is on) a band of light sweeps diagonally across it once while it leans
 * towards the side focus came from and settles back, with the artwork sliding a little behind the
 * frame, so the tile feels like a thing with depth. Its name fades in underneath. Pressing OK
 * squashes it back for a moment, like a real button.
 *
 * Every animated value is read only while drawing, never while composing, so moving focus around
 * costs no recomposition at all: that is what keeps it smooth on a slow box.
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
    val lite = Performance.lite
    val lighting = LocalTileLighting.current
    val tiltOn = LocalTileTilt.current && !lite
    val glowColor = LocalTheme.current.glow
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val longFired = remember { booleanArrayOf(false) }
    // Kept as a reference, not a value: bounds are read at click time, so they
    // include whatever the screen transition and the focus scale are doing then.
    val artCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun bounds(): Rect = artCoordinates[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero

    val motion = spring<Float>(dampingRatio = 0.62f, stiffness = 380f)
    val scale = animateFloatAsState(
        targetValue = if (pressed) 1.06f else if (focused) 1.14f else 1f,
        animationSpec = motion,
        label = "scale",
    )
    val lift = animateFloatAsState(if (focused) -8f else 0f, motion, label = "lift")
    val glow = animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "glow")
    val labelAlpha = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "labelAlpha")

    // 0 -> 1 once each time the tile gains focus drives the sweeping highlight; the tilt starts
    // leaning towards the side focus arrived from and springs back to flat.
    val sheen = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        sheen.snapTo(0f)
        if (focused) {
            if (tiltOn) {
                tiltY.snapTo(TileMotion.dx * 9f)
                tiltX.snapTo(-TileMotion.dy * 7f)
            }
            coroutineScope {
                if (tiltOn) {
                    launch { tiltY.animateTo(0f, TiltSpring) }
                    launch { tiltX.animateTo(0f, TiltSpring) }
                }
                if (!lite) launch { sheen.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
            }
        } else {
            tiltX.snapTo(0f)
            tiltY.snapTo(0f)
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                val enter = enterProgress?.invoke() ?: 1f
                translationY = (lift.value + (1f - enter) * 36f) * density
                scaleX = scale.value
                scaleY = scale.value
                alpha = enter
                compositingStrategy = CompositingStrategy.ModulateAlpha
                if (tiltOn) {
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    cameraDistance = 14f * density
                }
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
                .graphicsLayer {
                    // The shadow grows and takes the accent colour with focus (softer and cheaper in the light mode).
                    val g = glow.value
                    shadowElevation = (if (lite) 2f + 8f * g else 2f + 20f * g) * density
                    shape = TileShape
                    clip = false
                    ambientShadowColor = Color.Black
                    spotShadowColor = if (lite) Color.Black else lerp(Color.Black, glowColor, g)
                }
                .clip(TileShape),
        ) {
            Image(
                bitmap = art,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The artwork sits a little behind the frame: it grows with focus and slides against the lean.
                        val s = 1f + 0.05f * glow.value
                        scaleX = s
                        scaleY = s
                        if (tiltOn) {
                            translationX = -tiltY.value * 0.8f * density
                            translationY = tiltX.value * 0.7f * density
                        }
                    },
            )
            // One layer for everything drawn over the artwork: the tvOS light (the top of a tile a
            // little brighter, the bottom a little darker, whatever artwork the app brought), the
            // soft sheen along the top edge and the white ring while focused, and the sweeping band.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (lighting) drawRect(TileLightBrush)
                        val g = glow.value
                        if (g > 0.01f) {
                            drawRect(TileSheenBrush, alpha = g)
                            val ring = 2.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f * g),
                                topLeft = Offset(ring / 2f, ring / 2f),
                                size = Size(size.width - ring, size.height - ring),
                                cornerRadius = CornerRadius(12.dp.toPx() - ring / 2f),
                                style = Stroke(ring),
                            )
                        }
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
                    alpha = labelAlpha.value
                    translationY = (1f - labelAlpha.value) * -4f * density
                },
        )
    }
}
