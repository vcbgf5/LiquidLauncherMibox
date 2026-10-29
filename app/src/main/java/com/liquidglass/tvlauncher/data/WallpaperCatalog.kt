package com.liquidglass.tvlauncher.data

import androidx.compose.ui.graphics.ImageBitmap

/** A decoded wallpaper, plus a pre-blurred copy used as the dock's frosted backdrop. */
class WallpaperImage(val full: ImageBitmap, val blurred: ImageBitmap)

/** One photo in the repo's `wallpapers/` folder. [credit] is the photographer, when known. */
class WallpaperFile(val path: String, val credit: String?)

class WallpaperCategory(val id: String, val name: String, val files: List<WallpaperFile>)

/**
 * What the repo offers: the content of `wallpapers/index.json`, which a GitHub
 * workflow rebuilds whenever photos are added. Empty when the repo has no
 * photos yet or the device is offline and nothing was cached.
 */
class WallpaperCatalog(val categories: List<WallpaperCategory>) {
    val isEmpty: Boolean get() = categories.isEmpty()

    fun filesFor(categoryId: String): List<WallpaperFile> = when (categoryId) {
        OFF -> emptyList()
        ALL -> categories.flatMap { it.files }
        else -> categories.firstOrNull { it.id == categoryId }?.files.orEmpty()
    }

    companion object {
        /** Rotation switched off: the wallpaper built into the app stays. */
        const val OFF = "off"

        /** Rotate through every category. */
        const val ALL = "all"

        val EMPTY = WallpaperCatalog(emptyList())
    }
}
