package com.liquidglass.tvlauncher.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * One launchable app as shown on the home screen. [tile] is the finished
 * 5:3 artwork, rendered once at load time so scrolling never has to touch
 * a Drawable again.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val tile: ImageBitmap,
) {
    val key: String get() = "$packageName/$className"
}
