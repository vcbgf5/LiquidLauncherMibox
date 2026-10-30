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

    /**
     * The files to rotate through for [categoryIds]: [OFF] (none), [ALL] (every category, kept for
     * what older versions of the app already saved), or a comma-separated list of category ids —
     * what the multi-select list in Settings writes, letting more than one category be on at once.
     */
    fun filesFor(categoryIds: String): List<WallpaperFile> = when (categoryIds) {
        OFF, "" -> emptyList()
        ALL -> categories.flatMap { it.files }
        else -> {
            val wanted = categoryIds.split(',').toSet()
            categories.filter { it.id in wanted }.flatMap { it.files }
        }
    }

    companion object {
        /** Rotation switched off: the wallpaper built into the app stays. */
        const val OFF = "off"

        /** Rotate through every category (an older save, or nothing picked yet). */
        const val ALL = "all"

        val EMPTY = WallpaperCatalog(emptyList())

        /** The set of category ids [categoryIds] (see [filesFor]) picks out, for the multi-select
         * list, given every real id there is to pick from ([allIds]). */
        fun selectedIds(categoryIds: String, allIds: List<String>): Set<String> = when (categoryIds) {
            OFF, "" -> emptySet()
            ALL -> allIds.toSet()
            else -> categoryIds.split(',').toSet()
        }
    }
}
