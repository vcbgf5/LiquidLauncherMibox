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

    /** Whether the launcher looks for a new version by itself and, when there is one, starts the update. */
    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_UPDATES, value).apply()
        }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()
        }

    /**
     * The newest versionCode the launcher already started installing by itself. If the user
     * backs out of the system installer, the same version is not pushed at them again.
     */
    var lastAutoInstalled: Long
        get() = prefs.getLong(KEY_LAST_AUTO_INSTALLED, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_AUTO_INSTALLED, value).apply()
        }

    companion object {
        private const val KEY_AUTO_UPDATES = "auto_updates"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LAST_AUTO_INSTALLED = "last_auto_installed"
        const val DEFAULT_WALLPAPER_INTERVAL = 10
        private const val KEY_WALLPAPER_CATEGORY = "wallpaper_category"
        private const val KEY_WALLPAPER_INTERVAL = "wallpaper_interval"
        private const val KEY_LAST_WALLPAPER = "last_wallpaper"
        const val DEFAULT_DOCK_TRANSPARENCY = 60
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    }
}
