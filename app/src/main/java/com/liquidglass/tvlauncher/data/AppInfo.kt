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
    /** Set for "continue watching" entries: an intent URI that opens that very title. */
    val intentUri: String? = null,
) {
    val key: String get() = "$packageName/$className"
}
