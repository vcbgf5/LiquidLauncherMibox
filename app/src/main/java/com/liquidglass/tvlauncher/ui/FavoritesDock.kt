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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.data.AppInfo

internal val DockTileWidth = 130.dp
private val DockShape = RoundedCornerShape(32.dp)

/**
 * The rounded glass tray holding the favorites, centred across the seam
 * between the hero photo and the app grid.
 *
 * Real backdrop blur isn't available on the Android versions most TV
 * boxes ship, so the blur is baked: [backdrop] is a pre-blurred copy of
 * the hero, drawn here at the spot where the hero really is (found from
 * this node's position on screen). Below the hero there's nothing to
 * blur, so the tray falls back to the solid shelf colour, which also hides
 * grid rows scrolling underneath it. A white tint and a hairline border
 * on top give the frosted-glass look.
 */
@Composable
fun FavoritesDock(
    apps: List<AppInfo>,
    backdrop: ImageBitmap,
    backdropSize: IntSize,
    firstTileFocus: FocusRequester,
    onLaunch: (AppInfo) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .shadow(28.dp, DockShape, clip = false)
            .clip(DockShape)
            .background(ShelfColor)
            .drawBehind {
                translate(left = -origin.x, top = -origin.y) {
                    drawImage(backdrop, dstSize = backdropSize)
                }
            }
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
