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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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

internal val DockTileWidth = 120.dp
private val DockShape = RoundedCornerShape(32.dp)

/**
 * The rounded glass tray holding the favorites, sitting at the bottom of
 * the home screen. D-pad Down from any tile calls [onScrollDown], which
 * slides the screen up to the full app list.
 *
 * Real backdrop blur isn't available on the Android versions most TV
 * boxes ship, so the blur is baked: [backdrop] is a pre-blurred copy of
 * the whole photo, drawn here at the spot where that photo really is. The
 * tray is always [bottomInsetPx] above the bottom of a [screenSize] screen
 * and horizontally centred, so its position is worked out from its own
 * size instead of being measured on screen (which would be thrown off by
 * the slide animation). A white tint and a hairline border on top give the
 * frosted-glass look.
 */
@Composable
fun FavoritesDock(
    apps: List<AppInfo>,
    backdrop: ImageBitmap,
    screenSize: IntSize,
    bottomInsetPx: Float,
    firstTileFocus: FocusRequester,
    onLaunch: (AppInfo) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    onScrollDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Row(
        modifier = modifier
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
                    drawImage(backdrop, dstSize = screenSize)
                }
            }
            .background(Color.Black.copy(alpha = 0.18f))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), DockShape)
            .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        apps.forEachIndexed { index, app ->
            AppTile(
                label = app.label,
                art = app.tile,
                modifier = Modifier.width(DockTileWidth),
                focusRequester = if (index == 0) firstTileFocus else null,
                onClick = { onLaunch(app) },
                onLongClick = { onOpenMenu(app) },
            )
        }
    }
}
