package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.data.AppInfo

private val DockTileWidth = 128.dp
private val DockShape = RoundedCornerShape(32.dp)

/**
 * The rounded glass tray holding the favorites, sitting at the bottom of
 * the home screen. D-pad Down from any tile calls [onScrollDown], which
 * slides the screen up to the full app list.
 *
 * The tiles are centred in the tray: their names hang below them inside
 * the tray's bottom padding instead of pushing the row upward.
 *
 * [transparency] (0..1) is the user's option: 0 is frosted and nearly
 * opaque, 1 is almost clear glass with the photo showing through.
 *
 * Real backdrop blur isn't available on the Android versions most TV
 * boxes ship, so the blur is baked: [backdrop] is a pre-blurred copy of
 * the whole photo, drawn here at the spot where that photo really is
 * (including the photo's slow zoom, [zoom]). The tray is always
 * [bottomInsetPx] above the bottom of a [screenSize] screen and
 * horizontally centred, so its position is worked out from its own size
 * instead of being measured on screen, which the slide animations would
 * throw off.
 */
@Composable
fun FavoritesDock(
    apps: List<AppInfo>,
    backdrop: ImageBitmap,
    previousBackdrop: ImageBitmap?,
    backdropFade: () -> Float,
    screenSize: IntSize,
    bottomInsetPx: Float,
    transparency: Float,
    zoom: () -> Float,
    firstTileFocus: FocusRequester,
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val t = transparency.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .riseIn(delayMillis = 80, distance = 40.dp, fromScale = 0.96f)
            .onSizeChanged { size = it }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onScrollDown()
                    true
                } else {
                    false
                }
            }
            .shadow(28.dp, DockShape, clip = false)
            .clip(DockShape)
            .drawBehind {
                val origin = Offset(
                    x = (screenSize.width - size.width) / 2f,
                    y = screenSize.height - bottomInsetPx - size.height,
                )
                translate(left = -origin.x, top = -origin.y) {
                    scale(scale = zoom(), pivot = Offset(screenSize.width / 2f, screenSize.height / 2f)) {
                        val a = 1f - 0.6f * t
                        // While the wallpaper changes, the old backdrop stays underneath.
                        previousBackdrop?.let { drawImage(it, dstSize = screenSize, alpha = a) }
                        val fade = if (previousBackdrop != null) backdropFade() else 1f
                        drawImage(backdrop, dstSize = screenSize, alpha = a * fade)
                    }
                }
            }
            .background(Color.Black.copy(alpha = 0.18f * (1f - t)))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.04f + 0.14f * (1f - t)),
                        Color.White.copy(alpha = 0.02f + 0.04f * (1f - t)),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f - 0.08f * t), DockShape)
            .padding(horizontal = 28.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        apps.forEachIndexed { index, app ->
            AppTile(
                label = app.label,
                art = app.tile,
                modifier = Modifier
                    .width(DockTileWidth)
                    .riseIn(delayMillis = 220 + index * 70, distance = 28.dp, fromScale = 0.85f),
                focusRequester = if (index == 0) firstTileFocus else null,
                reserveLabelSpace = false,
                onClick = { rect -> onLaunch(app, rect) },
                onLongClick = { onOpenMenu(app) },
            )
        }
    }
}
