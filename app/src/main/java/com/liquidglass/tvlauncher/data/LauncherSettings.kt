package com.liquidglass.tvlauncher.data

import com.liquidglass.spotify.SpotifyTokens
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

    /** A faint drift of snow, blossom or leaves on the home screen, picked from today's month. */
    var seasonalTheme: Boolean
        get() = prefs.getBoolean(KEY_SEASONAL_THEME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SEASONAL_THEME, value).apply()
        }

    /** Play the uploaded video clip (if any) as the home screen's wallpaper instead of a photo. */
    var animatedWallpaper: Boolean
        get() = prefs.getBoolean(KEY_ANIMATED_WALLPAPER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ANIMATED_WALLPAPER, value).apply()
        }

    /**
     * The launcher's own volume bar (it needs its accessibility service): [VOLUME_OFF];
     * [VOLUME_BESIDE], where the bar shows every change of the volume and Android's own bar is left alone;
     * and [VOLUME_REPLACE] (the default), where the volume keys are taken by the service and applied
     * without Android's bar, and any other change (a Bluetooth speaker, HDMI) gets Android's bar closed.
     */
    var volumeMode: Int
        get() = prefs.getInt(KEY_VOLUME_MODE, VOLUME_REPLACE).coerceIn(VOLUME_OFF, VOLUME_REPLACE)
        set(value) {
            // Choosing a mode again gives the key handling another chance.
            prefs.edit().putInt(KEY_VOLUME_MODE, value).putBoolean(KEY_VOLUME_CONSUME_BROKEN, false).apply()
        }

    /** The box was seen changing the music volume by part of its range: its level is real (not a TV's over HDMI-CEC). */
    var volumeLevelReal: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_LEVEL_REAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VOLUME_LEVEL_REAL, value).apply()
        }

    /** Taking the volume keys over did nothing a few times in a row: they are left to Android from now on. */
    var volumeConsumeBroken: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_CONSUME_BROKEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VOLUME_CONSUME_BROKEN, value).apply()
        }

    /** The remote button taught to open the quick settings (a key code), 0 = none taught. */
    var quickKey: Int
        get() = prefs.getInt(KEY_QUICK_KEY, 0)
        set(value) {
            prefs.edit().putInt(KEY_QUICK_KEY, value).apply()
        }

    /** What the big clock shows: see the CLOCK_* constants in HomeScreen (0 = time and date). */
    var clockContent: Int
        get() = prefs.getInt(KEY_CLOCK_CONTENT, 0).coerceIn(0, 3)
        set(value) {
            prefs.edit().putInt(KEY_CLOCK_CONTENT, value).apply()
        }

    /** Where the big clock sits: 0 = high up on the photo (default), 1 = in the middle. */
    var clockPosition: Int
        get() = prefs.getInt(KEY_CLOCK_POSITION, 0).coerceIn(0, 1)
        set(value) {
            prefs.edit().putInt(KEY_CLOCK_POSITION, value).apply()
        }

    /** The top bar as one menu button (three lines) that unfolds a list, instead of a row of round buttons. */
    var topBarMinimal: Boolean
        get() = prefs.getBoolean(KEY_TOPBAR_MINIMAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOPBAR_MINIMAL, value).apply()
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

    /** Install updates through the box's own ADB service, so Android's installer window never shows. */
    var silentUpdates: Boolean
        get() = prefs.getBoolean(KEY_SILENT_UPDATES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SILENT_UPDATES, value).apply()
        }

    /** The city the weather is for (Warsaw until the user picks another). */
    var weatherCity: WeatherCity
        get() = WeatherCity(
            prefs.getString(KEY_CITY_NAME, null) ?: Cities.BUILTIN.first().name,
            Double.fromBits(prefs.getLong(KEY_CITY_LAT, Cities.BUILTIN.first().latitude.toRawBits())),
            Double.fromBits(prefs.getLong(KEY_CITY_LON, Cities.BUILTIN.first().longitude.toRawBits())),
        )
        set(value) {
            prefs.edit().putString(KEY_CITY_NAME, value.name)
                .putLong(KEY_CITY_LAT, value.latitude.toRawBits()).putLong(KEY_CITY_LON, value.longitude.toRawBits()).apply()
        }

    /** A city looked up by name (over adb), offered in the city list next to the built-in ones. */
    var customWeatherCity: WeatherCity?
        get() = prefs.getString(KEY_CUSTOM_NAME, null)?.let {
            WeatherCity(
                it,
                Double.fromBits(prefs.getLong(KEY_CUSTOM_LAT, 0L)),
                Double.fromBits(prefs.getLong(KEY_CUSTOM_LON, 0L)),
            )
        }
        set(value) {
            val edit = prefs.edit()
            if (value == null) {
                edit.remove(KEY_CUSTOM_NAME)
            } else {
                edit.putString(KEY_CUSTOM_NAME, value.name)
                    .putLong(KEY_CUSTOM_LAT, value.latitude.toRawBits()).putLong(KEY_CUSTOM_LON, value.longitude.toRawBits())
            }
            edit.apply()
        }

    /** A city name sent over adb that still has to be looked up. */
    var pendingWeatherCity: String?
        get() = prefs.getString(KEY_PENDING_CITY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PENDING_CITY, value ?: "").apply()
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


    /** How often (seconds) the launcher asks GitHub whether a newer version exists while it is on screen. */
    var updateIntervalSeconds: Int
        get() = prefs.getInt(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL).coerceAtLeast(5)
        set(value) {
            prefs.edit().putInt(KEY_UPDATE_INTERVAL, value).apply()
        }

    /** When the launcher last started an automatic install (used to retry a failed silent one after a while). */
    var lastAutoInstallAt: Long
        get() = prefs.getLong(KEY_LAST_AUTO_INSTALL_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_AUTO_INSTALL_AT, value).apply()
        }

    /** The versionCode whose "what's new" the user has seen, so it shows once after an update. */
    var lastSeenVersionCode: Long
        get() = prefs.getLong(KEY_LAST_SEEN_VERSION, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SEEN_VERSION, value).apply()
        }

    /** [PERF_AUTO] decides by itself (and remembers it), [PERF_LITE] always saves effort, [PERF_FULL] never does. */
    var performanceMode: Int
        get() = prefs.getInt(KEY_PERFORMANCE, if (Edition.lite) PERF_LITE else PERF_AUTO).coerceIn(PERF_AUTO, PERF_FULL)
        set(value) {
            prefs.edit().putInt(KEY_PERFORMANCE, value).apply()
        }

    /** Set when the frame monitor saw the launcher stutter: [PERF_AUTO] then keeps the light mode on. */
    var autoLite: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LITE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_LITE, value).apply()
        }

    /** Index into the accent colours (see LauncherTheme). */
    var accentIndex: Int
        get() = prefs.getInt(KEY_ACCENT, 0)
        set(value) {
            prefs.edit().putInt(KEY_ACCENT, value).apply()
        }

    /** Index into the glass tints (see LauncherTheme). */
    var glassTintIndex: Int
        get() = prefs.getInt(KEY_GLASS_TINT, 0)
        set(value) {
            prefs.edit().putInt(KEY_GLASS_TINT, value).apply()
        }

    /** The Spotify session the phone app handed over (the Client ID is public; the tokens are this app's alone). */
    var spotifyClientId: String
        get() = prefs.getString(KEY_SPOTIFY_CLIENT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SPOTIFY_CLIENT, value).apply()
        }

    val spotifyAccess: String? get() = prefs.getString(KEY_SPOTIFY_ACCESS, null)

    val spotifyRefresh: String? get() = prefs.getString(KEY_SPOTIFY_REFRESH, null)

    val spotifyExpiresAt: Long get() = prefs.getLong(KEY_SPOTIFY_EXPIRES, 0L)

    fun saveSpotifyTokens(tokens: SpotifyTokens) {
        prefs.edit()
            .putString(KEY_SPOTIFY_ACCESS, tokens.access)
            .putString(KEY_SPOTIFY_REFRESH, tokens.refresh)
            .putLong(KEY_SPOTIFY_EXPIRES, tokens.expiresAt)
            .apply()
    }

    fun clearSpotify() {
        prefs.edit().remove(KEY_SPOTIFY_ACCESS).remove(KEY_SPOTIFY_REFRESH).remove(KEY_SPOTIFY_EXPIRES).apply()
    }

    /** The Last.fm session key the phone app handed over (Last.fm's "mobile session" auth; the key never expires). */
    var lastfmSession: String?
        get() = prefs.getString(KEY_LASTFM_SESSION, null)
        set(value) {
            prefs.edit().putString(KEY_LASTFM_SESSION, value).apply()
        }

    /** The Last.fm username, shown as "Połączono jako …" in Settings. */
    var lastfmUser: String
        get() = prefs.getString(KEY_LASTFM_USER, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LASTFM_USER, value).apply()
        }

    /** Whether what Spotify plays is scrobbled to Last.fm (only matters once a session is connected). */
    var lastfmScrobbling: Boolean
        get() = prefs.getBoolean(KEY_LASTFM_SCROBBLING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LASTFM_SCROBBLING, value).apply()
        }

    fun clearLastFm() {
        prefs.edit().remove(KEY_LASTFM_SESSION).remove(KEY_LASTFM_USER).apply()
    }

    /** Look of the windows and menus: 0 flat dark glass, 1 Liquid Glass (see GlassStyle). */
    var glassStyle: Int
        get() = prefs.getInt(KEY_GLASS_STYLE, if (Edition.lite) 0 else 1)
        set(value) {
            prefs.edit().putInt(KEY_GLASS_STYLE, value).apply()
        }

    /** The wallpaper drifts a little sideways as focus moves along the dock. */
    var parallax: Boolean
        get() = prefs.getBoolean(KEY_PARALLAX, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PARALLAX, value).apply()
        }

    /** App tiles lean towards the direction focus arrived from, like on tvOS. */
    var tileTilt: Boolean
        get() = prefs.getBoolean(KEY_TILE_TILT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TILE_TILT, value).apply()
        }

    /** Keep the "remote from the phone" web page available all the time, not just while its screen is open. */
    var remoteAlways: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_ALWAYS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REMOTE_ALWAYS, value).apply()
        }

    /** The secret in the phone page's address; made on first use. */
    val remoteToken: String
        get() = prefs.getString(KEY_REMOTE_TOKEN, null) ?: newRemoteToken()

    fun newRemoteToken(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = java.security.SecureRandom()
        val token = (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        prefs.edit().putString(KEY_REMOTE_TOKEN, token).apply()
        return token
    }

    companion object {
        const val DEFAULT_UPDATE_INTERVAL = 5
        const val PERF_AUTO = 0
        const val PERF_LITE = 1
        const val PERF_FULL = 2
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        private const val KEY_LAST_AUTO_INSTALL_AT = "last_auto_install_at"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        private const val KEY_PERFORMANCE = "performance_mode"
        private const val KEY_AUTO_LITE = "auto_lite"
        private const val KEY_ACCENT = "accent"
        private const val KEY_GLASS_TINT = "glass_tint"
        private const val KEY_GLASS_STYLE = "glass_style"
        private const val KEY_SPOTIFY_CLIENT = "spotify_client"
        private const val KEY_SPOTIFY_ACCESS = "spotify_access"
        private const val KEY_SPOTIFY_REFRESH = "spotify_refresh"
        private const val KEY_SPOTIFY_EXPIRES = "spotify_expires"
        private const val KEY_LASTFM_SESSION = "lastfm_session"
        private const val KEY_LASTFM_USER = "lastfm_user"
        private const val KEY_LASTFM_SCROBBLING = "lastfm_scrobbling"
        private const val KEY_PARALLAX = "parallax"
        private const val KEY_TILE_TILT = "tile_tilt"
        private const val KEY_REMOTE_ALWAYS = "remote_always"
        private const val KEY_REMOTE_TOKEN = "remote_token"
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
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_CITY_LAT = "city_lat"
        private const val KEY_CITY_LON = "city_lon"
        private const val KEY_CUSTOM_NAME = "custom_city_name"
        private const val KEY_CUSTOM_LAT = "custom_city_lat"
        private const val KEY_CUSTOM_LON = "custom_city_lon"
        private const val KEY_PENDING_CITY = "pending_city"
        private const val KEY_SILENT_UPDATES = "silent_updates"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_ICAL_URL = "ical_url"
        private const val KEY_CLOCK_CONTENT = "clock_content"
        private const val KEY_CLOCK_POSITION = "clock_position"
        private const val KEY_STATUS_ICONS = "status_icons"
        private const val KEY_TOPBAR_MINIMAL = "topbar_minimal"
        const val VOLUME_OFF = 0
        const val VOLUME_BESIDE = 1
        const val VOLUME_REPLACE = 2
        private const val KEY_VOLUME_MODE = "volume_mode"
        private const val KEY_VOLUME_LEVEL_REAL = "volume_level_real"
        private const val KEY_VOLUME_CONSUME_BROKEN = "volume_consume_broken"
        private const val KEY_QUICK_KEY = "quick_key"
        private const val KEY_TILE_LIGHTING = "tile_lighting"
        private const val KEY_SEASONAL_THEME = "seasonal_theme"
        private const val KEY_ANIMATED_WALLPAPER = "animated_wallpaper"
        private const val KEY_SCREENSAVER = "screensaver_minutes"
        const val DEFAULT_DOCK_TRANSPARENCY = 60
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    }
}
