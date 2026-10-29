package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** One square of the quick settings: a name, its state in words, and whether it is "on" (lit with the accent colour). */
class QuickTile(val title: String, val state: String, val active: Boolean, val onClick: () -> Unit)

private const val COLUMNS = 4

/**
 * The quick settings, opened by holding OK on the gear (or the Menu key): the things you want
 * without digging through the settings, as a grid of tiles you move over with the D-pad.
 * A tile's state is read again on every redraw, so a switch shows its new state straight away.
 */
@Composable
fun QuickPanel(title: String, tiles: List<QuickTile>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .width(660.dp)
                .clip(shape)
                .background(LocalTheme.current.glass.card)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            )
            tiles.chunked(COLUMNS).forEachIndexed { row, rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    rowTiles.forEachIndexed { column, tile ->
                        QuickTileView(tile, autoFocus = row == 0 && column == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(tile: QuickTile, autoFocus: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    val accent = LocalTheme.current.accent
    val ink = if (focused) Color(0xFF111111) else Color.White
    Box(
        modifier = Modifier
            .width(147.dp)
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(tile.onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (tile.active) accent else ink.copy(alpha = 0.25f)),
            )
            Text(
                text = tile.title,
                color = ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = tile.state,
            color = ink.copy(alpha = 0.65f),
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        )
    }
}
