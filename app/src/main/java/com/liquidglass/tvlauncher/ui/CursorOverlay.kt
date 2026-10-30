package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The dot that follows the phone's gyroscope (see RemoteServer's /cursor and MainActivity's
 * receiveCursor): drawn straight at [position], in screen pixels, with nothing else in between —
 * adb is only used at the instant of a tap, so the dot itself is as smooth as Compose can make it.
 */
@Composable
fun CursorOverlay(position: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 15f, center = position)
        drawCircle(color = Color.White, radius = 13f, center = position, style = Stroke(width = 3f))
        drawCircle(color = Color(0xFF0A84FF), radius = 5f, center = position)
    }
}
