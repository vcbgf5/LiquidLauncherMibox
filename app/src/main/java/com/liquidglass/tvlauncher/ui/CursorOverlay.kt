package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The dot that follows the phone's gyroscope (see RemoteServer's /cursor and MainActivity's
 * receiveCursor): drawn straight at [position], in screen pixels, with nothing else in between —
 * adb is only used at the instant of a tap, so the dot itself is as smooth as Compose can make it.
 */
@Composable
fun CursorOverlay(position: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Opaque black border + white fill, sized in real dp (not raw pixels, which on a dense TV
        // panel used to draw a dot too small and too translucent to actually see from the couch).
        val r = 20f * density
        drawCircle(color = Color.Black, radius = r, center = position)
        drawCircle(color = Color.White, radius = r - 3.5f * density, center = position)
    }
}
