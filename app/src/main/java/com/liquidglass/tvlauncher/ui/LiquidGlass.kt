package com.liquidglass.tvlauncher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.data.Performance
import kotlin.math.max

/** The two looks of the windows, menus and the dock's edge (Settings → Appearance). */
object GlassStyle {
    /** Flat dark glass: a solid tinted card with a thin edge. */
    const val CLASSIC = 0

    /** Frosted glass over the wallpaper with bent light at the rim, a bright edge and a soft sheen. */
    const val LIQUID = 1
}

val LocalGlassStyle = compositionLocalOf { GlassStyle.CLASSIC }

/**
 * What a glass panel looks through: the wallpaper's pre-blurred copy (the same one the dock uses).
 * Real backdrop blur needs a newer Android than most boxes have, so the panel draws that copy itself
 * at the spot where the photo is on screen. Everything is read when drawing, so a wallpaper change
 * (and its cross-fade) repaints the panels without recomposing anything.
 */
class GlassBackdrop(private val wallpaper: WallpaperState) {
    val image: ImageBitmap get() = wallpaper.current.blurred
    val previous: ImageBitmap? get() = wallpaper.previous?.blurred
    val fade: Float get() = wallpaper.fade.value

    private var lumaFor: ImageBitmap? = null
    private var lumaValue = 0.4f

    /** 0 for a dark photo, 1 for a bright one: bright photos get a darker veil so the white text stays readable. */
    val luma: Float
        get() {
            val current = image
            if (current !== lumaFor) {
                lumaValue = averageLuma(current)
                lumaFor = current
            }
            return lumaValue
        }
}

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

private fun averageLuma(image: ImageBitmap): Float = try {
    val small = Bitmap.createScaledBitmap(image.asAndroidBitmap(), 16, 9, true)
    val pixels = IntArray(16 * 9)
    small.getPixels(pixels, 0, 16, 0, 0, 16, 9)
    var sum = 0f
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        sum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }
    sum / pixels.size
} catch (e: Exception) {
    0.4f
}

private val Saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.7f) })

/**
 * The surface of a window or menu: [corner] is its corner radius, [tint] the colour of the dark glass.
 * In the classic style it is a flat card. In the Liquid Glass style it is frosted glass that shows
 * the blurred, more colourful wallpaper, bends it towards the rim (magnified in bands, like a thick
 * lens), is veiled with the tint, catches light along the top and left edges and has a faint colour
 * fringe at the rim. The light mode falls back to the flat card.
 */
fun Modifier.glassPanel(corner: Dp, tint: Color): Modifier = composed {
    val shape = RoundedCornerShape(corner)
    val backdrop = LocalGlassBackdrop.current
    if (LocalGlassStyle.current != GlassStyle.LIQUID || Performance.lite || backdrop == null) {
        Modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    } else {
        val view = LocalView.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        val metrics = view.resources.displayMetrics
        val screen = IntSize(metrics.widthPixels, metrics.heightPixels)
        Modifier
            .shadow(
                elevation = 26.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.55f),
            )
            .onGloballyPositioned { coordinates ->
                // Where the panel is on the screen (a dialog is a window of its own), to cut the photo to it.
                val onScreen = IntArray(2)
                view.getLocationOnScreen(onScreen)
                val inWindow = coordinates.positionInRoot()
                val next = Offset(onScreen[0] + inWindow.x, onScreen[1] + inWindow.y)
                if (next != origin) origin = next
            }
            .clip(shape)
            .drawBehind { drawLiquidBackdrop(backdrop, origin, screen, tint, corner.toPx()) }
            .drawLiquidRim(corner)
    }
}

/** Only the bright rim, for surfaces that already draw their own frosted backdrop (the dock). */
fun Modifier.liquidRim(corner: Dp): Modifier = composed {
    if (LocalGlassStyle.current == GlassStyle.LIQUID && !Performance.lite) Modifier.drawLiquidRim(corner) else Modifier
}

private fun DrawScope.drawPhoto(backdrop: GlassBackdrop, screen: IntSize) {
    val previous = backdrop.previous
    if (previous != null) {
        drawImage(previous, dstSize = screen, colorFilter = Saturate)
        drawImage(backdrop.image, dstSize = screen, alpha = backdrop.fade, colorFilter = Saturate)
    } else {
        drawImage(backdrop.image, dstSize = screen, colorFilter = Saturate)
    }
}

private fun DrawScope.drawLiquidBackdrop(backdrop: GlassBackdrop, origin: Offset, screen: IntSize, tint: Color, corner: Float) {
    val w = size.width
    val h = size.height
    val centre = Offset(w / 2f, h / 2f)
    val band = 5.dp.toPx()

    // The photo, magnified more and more towards the rim: each layer is cut a band narrower and magnified less.
    listOf(1.22f, 1.14f, 1.08f, 1.04f).forEachIndexed { index, magnification ->
        val inset = band * index
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = inset,
                    top = inset,
                    right = w - inset,
                    bottom = h - inset,
                    cornerRadius = CornerRadius(max(0f, corner - inset)),
                ),
            )
        }
        clipPath(path) {
            scale(magnification, pivot = centre) {
                translate(left = -origin.x, top = -origin.y) { drawPhoto(backdrop, screen) }
            }
        }
    }

    // The tinted veil: darker over a bright photo so the text keeps its contrast.
    drawRect(tint.copy(alpha = (0.36f + 0.38f * backdrop.luma).coerceIn(0.36f, 0.76f)))
    // Light from the top left, a softer one from the bottom right.
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), endY = h * 0.45f))
    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset.Zero, end = Offset(w * 0.6f, h * 0.5f)))
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), center = Offset(w, h), radius = max(w, h) * 0.5f))
}

private fun Modifier.drawLiquidRim(corner: Dp): Modifier = drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    val r = corner.toPx()

    fun edge(brush: Brush, inset: Float, width: Float) {
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(w - 2f * inset, h - 2f * inset),
            cornerRadius = CornerRadius(max(0f, r - inset)),
            style = Stroke(width),
        )
    }

    val topLeft = Offset.Zero
    val bottomRight = Offset(w, h)
    // A glow just inside the edge, as if the glass were lit from within.
    edge(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.White.copy(alpha = 0.08f)), start = topLeft, end = bottomRight),
        inset = 3.dp.toPx(),
        width = 8.dp.toPx(),
    )
    // The rim splits the light a little: pink towards the top left, cyan towards the bottom right.
    edge(Brush.linearGradient(listOf(Color(0xFFFF8AD8).copy(alpha = 0.28f), Color.Transparent), start = topLeft, end = bottomRight), inset = 2.4.dp.toPx(), width = 1.2.dp.toPx())
    edge(Brush.linearGradient(listOf(Color.Transparent, Color(0xFF7FE6FF).copy(alpha = 0.30f)), start = topLeft, end = bottomRight), inset = 2.4.dp.toPx(), width = 1.2.dp.toPx())
    // The bright edge: strongest on the top left, a second, weaker one on the bottom right.
    edge(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.28f to Color.White.copy(alpha = 0.35f),
            0.55f to Color.White.copy(alpha = 0.06f),
            0.80f to Color.White.copy(alpha = 0.28f),
            1f to Color.White.copy(alpha = 0.70f),
            start = topLeft,
            end = bottomRight,
        ),
        inset = 0.8.dp.toPx(),
        width = 1.6.dp.toPx(),
    )
    // A thin highlight along the top edge.
    drawLine(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), startX = r, endX = max(r + 1f, w - r)),
        start = Offset(r, 1.4.dp.toPx()),
        end = Offset(max(r + 1f, w - r), 1.4.dp.toPx()),
        strokeWidth = 1.2.dp.toPx(),
    )
}
