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

    /** How big the apps on the home screen are, in percent of the normal size. */
    var dockSize: Int
        get() = prefs.getInt(KEY_DOCK_SIZE, DEFAULT_DOCK_SIZE).coerceIn(MIN_DOCK_SIZE, MAX_DOCK_SIZE)
        set(value) {
            prefs.edit().putInt(KEY_DOCK_SIZE, value.coerceIn(MIN_DOCK_SIZE, MAX_DOCK_SIZE)).apply()
        }

    /** The Apple-style light on the app tiles: brighter near the top, darker toward the bottom. */
    var tileLighting: Boolean
        get() = prefs.getBoolean(KEY_TILE_LIGHTING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TILE_LIGHTING, value).apply()
        }

    /** The launcher's own volume bar (needs its accessibility service) instead of Android's. */
    var customVolume: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_VOLUME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CUSTOM_VOLUME, value).apply()
        }

    /** What the big clock shows: see the CLOCK_* constants in HomeScreen (0 = time and date). */
    var clockContent: Int
        get() = prefs.getInt(KEY_CLOCK_CONTENT, 0).coerceIn(0, 2)
        set(value) {
            prefs.edit().putInt(KEY_CLOCK_CONTENT, value).apply()
        }

    /** Where the big clock sits: 0 = high up on the photo (default), 1 = in the middle. */
    var clockPosition: Int
        get() = prefs.getInt(KEY_CLOCK_POSITION, 0).coerceIn(0, 1)
        set(value) {
            prefs.edit().putInt(KEY_CLOCK_POSITION, value).apply()
        }

    /** Wi-Fi and Bluetooth icons in the top bar. */
    var statusIcons: Boolean
        get() = prefs.getBoolean(KEY_STATUS_ICONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_STATUS_ICONS, value).apply()
        }

    /** Which widgets are on the page above the home screen (WidgetType keys). */
    var enabledWidgets: Set<String>
        get() = prefs.getString(KEY_WIDGETS, null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: WidgetType.DEFAULT_KEYS
        set(value) {
            prefs.edit().putString(KEY_WIDGETS, value.joinToString(",")).apply()
        }

    /** The private iCal address of the user's Google calendar, or null when none is connected. */
    var icalUrl: String?
        get() = prefs.getString(KEY_ICAL_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_ICAL_URL, value ?: "").apply()
        }

    /** A big clock in the middle of the home screen instead of the small one in the corner. */
    var bigClock: Boolean
        get() = prefs.getBoolean(KEY_BIG_CLOCK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BIG_CLOCK, value).apply()
        }

    /** Minutes without a key press after which the screensaver starts; 0 = never. */
    var screensaverMinutes: Int
        get() = prefs.getInt(KEY_SCREENSAVER, DEFAULT_SCREENSAVER_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_SCREENSAVER, value).apply()
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
        const val DEFAULT_DOCK_SIZE = 100
        const val MIN_DOCK_SIZE = 70
        const val MAX_DOCK_SIZE = 140
        const val DEFAULT_SCREENSAVER_MINUTES = 3
        private const val KEY_DOCK_SIZE = "dock_size"
        private const val KEY_BIG_CLOCK = "big_clock"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_ICAL_URL = "ical_url"
        private const val KEY_CLOCK_CONTENT = "clock_content"
        private const val KEY_CLOCK_POSITION = "clock_position"
        private const val KEY_STATUS_ICONS = "status_icons"
        private const val KEY_CUSTOM_VOLUME = "custom_volume"
        private const val KEY_TILE_LIGHTING = "tile_lighting"
        private const val KEY_SCREENSAVER = "screensaver_minutes"
        const val DEFAULT_DOCK_TRANSPARENCY = 60
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    }
}
