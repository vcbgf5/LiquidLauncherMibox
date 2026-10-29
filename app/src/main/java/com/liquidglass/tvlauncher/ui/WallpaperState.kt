package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liquidglass.tvlauncher.data.WallpaperImage

/**
 * The wallpaper on screen. [show] cross-fades to a new one: while it runs,
 * [previous] is still drawn underneath and [fade] goes 0 -> 1 for the new
 * [current] on top. With no wallpaper chosen, [bundled] (built into the app) is used.
 */
@Stable
class WallpaperState(private val bundled: WallpaperImage) {
    var current by mutableStateOf(bundled)
        private set
    var previous by mutableStateOf<WallpaperImage?>(null)
        private set

    val fade = Animatable(1f)

    suspend fun show(next: WallpaperImage?) {
        val target = next ?: bundled
        if (target === current) return
        previous = current
        current = target
        fade.snapTo(0f)
        fade.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        previous = null
    }
}
