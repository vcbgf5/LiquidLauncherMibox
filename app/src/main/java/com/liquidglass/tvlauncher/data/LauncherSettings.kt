package com.liquidglass.tvlauncher.data

import android.content.Context

/** The launcher's own options, remembered between runs. */
class LauncherSettings(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** How see-through the dock is, 0 (frosted) to 100 (almost clear glass). */
    var dockTransparency: Int
        get() = prefs.getInt(KEY_DOCK_TRANSPARENCY, DEFAULT_DOCK_TRANSPARENCY).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_DOCK_TRANSPARENCY, value.coerceIn(0, 100)).apply()
        }

    /** A category id from the wallpaper catalog, or [WallpaperCatalog.OFF] / [WallpaperCatalog.ALL]. */
    var wallpaperCategory: String
        get() = prefs.getString(KEY_WALLPAPER_CATEGORY, WallpaperCatalog.ALL) ?: WallpaperCatalog.ALL
        set(value) {
            prefs.edit().putString(KEY_WALLPAPER_CATEGORY, value).apply()
        }

    /** How often the wallpaper changes, in minutes. */
    var wallpaperIntervalMinutes: Int
        get() = prefs.getInt(KEY_WALLPAPER_INTERVAL, DEFAULT_WALLPAPER_INTERVAL)
        set(value) {
            prefs.edit().putInt(KEY_WALLPAPER_INTERVAL, value).apply()
        }

    /** The wallpaper shown last, so the next pick is a different one. */
    var lastWallpaper: String?
        get() = prefs.getString(KEY_LAST_WALLPAPER, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_WALLPAPER, value).apply()
        }

    companion object {
        const val DEFAULT_WALLPAPER_INTERVAL = 10
        private const val KEY_WALLPAPER_CATEGORY = "wallpaper_category"
        private const val KEY_WALLPAPER_INTERVAL = "wallpaper_interval"
        private const val KEY_LAST_WALLPAPER = "last_wallpaper"
        const val DEFAULT_DOCK_TRANSPARENCY = 60
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    }
}
