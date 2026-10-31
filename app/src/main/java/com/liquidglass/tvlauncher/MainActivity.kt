package com.liquidglass.tvlauncher

import android.Manifest
import android.accounts.AccountManager
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.liquidglass.tvlauncher.data.AccountProvider
import com.liquidglass.tvlauncher.data.AdbResult
import com.liquidglass.tvlauncher.data.AppFolder
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.AppRepository
import com.liquidglass.tvlauncher.data.BoxDiscovery
import com.liquidglass.tvlauncher.data.SpotifyHub
import com.liquidglass.tvlauncher.data.LastFmHub
import com.liquidglass.tvlauncher.data.Edition
import com.liquidglass.tvlauncher.data.Changelog
import com.liquidglass.tvlauncher.data.FavoritesStore
import com.liquidglass.tvlauncher.data.FolderArt
import com.liquidglass.tvlauncher.data.FolderStore
import com.liquidglass.tvlauncher.data.FrameMonitor
import com.liquidglass.tvlauncher.data.IconOverrides
import com.liquidglass.tvlauncher.data.IconPack
import com.liquidglass.tvlauncher.data.KeyLearner
import com.liquidglass.tvlauncher.data.KeyLog
import com.liquidglass.tvlauncher.data.IconPacks
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.NowPlayingReader
import com.liquidglass.tvlauncher.data.NowPlayingState
import com.liquidglass.tvlauncher.data.Performance
import com.liquidglass.tvlauncher.data.PriorityApps
import com.liquidglass.tvlauncher.data.RemoteServer
import com.liquidglass.tvlauncher.data.SelfAdb
import com.liquidglass.tvlauncher.data.SystemStatus
import com.liquidglass.tvlauncher.data.SystemTools
import com.liquidglass.tvlauncher.data.UpdateManager
import com.liquidglass.tvlauncher.data.UpdateStatus
import com.liquidglass.tvlauncher.data.Updater
import com.liquidglass.tvlauncher.data.Agenda
import com.liquidglass.tvlauncher.data.AgendaRepository
import com.liquidglass.tvlauncher.data.Cities
import com.liquidglass.tvlauncher.data.Weather
import com.liquidglass.tvlauncher.data.WeatherCity
import com.liquidglass.tvlauncher.data.WeatherRepository
import com.liquidglass.tvlauncher.data.WidgetData
import com.liquidglass.tvlauncher.data.WidgetDataReader
import com.liquidglass.tvlauncher.data.WidgetType
import com.liquidglass.tvlauncher.data.WallpaperCatalog
import com.liquidglass.tvlauncher.data.WallpaperImage
import com.liquidglass.tvlauncher.data.WallpaperRepository
import com.liquidglass.tvlauncher.data.isFolder
import com.liquidglass.tvlauncher.ui.AppMenu
import com.liquidglass.tvlauncher.ui.CursorOverlay
import com.liquidglass.tvlauncher.ui.DiagnosticsDialog
import com.liquidglass.tvlauncher.ui.FolderDialog
import com.liquidglass.tvlauncher.ui.HomeScreen
import com.liquidglass.tvlauncher.ui.IconPicker
import com.liquidglass.tvlauncher.ui.LauncherTheme
import com.liquidglass.tvlauncher.ui.GlassBackdrop
import com.liquidglass.tvlauncher.ui.GlassStyle
import com.liquidglass.tvlauncher.ui.LocalGlassBackdrop
import com.liquidglass.tvlauncher.ui.LocalGlassStyle
import com.liquidglass.tvlauncher.ui.LocalTheme
import com.liquidglass.tvlauncher.ui.LocalTileLighting
import com.liquidglass.tvlauncher.ui.LocalTileTilt
import com.liquidglass.tvlauncher.ui.PAGE_HOME
import com.liquidglass.tvlauncher.ui.PAGE_INFO
import com.liquidglass.tvlauncher.ui.LaunchOverlay
import com.liquidglass.tvlauncher.ui.LearnKeyPrompt
import com.liquidglass.tvlauncher.ui.MirrorScreen
import com.liquidglass.tvlauncher.ui.LaunchRequest
import com.liquidglass.tvlauncher.ui.PowerOffAnimation
import com.liquidglass.tvlauncher.ui.WallpaperCategoryPicker
import com.liquidglass.tvlauncher.ui.MenuOption
import com.liquidglass.tvlauncher.ui.QuickPanel
import com.liquidglass.tvlauncher.ui.QuickTile
import com.liquidglass.tvlauncher.ui.RemoteDialog
import com.liquidglass.tvlauncher.ui.Screensaver
import com.liquidglass.tvlauncher.ui.SettingsDialog
import com.liquidglass.tvlauncher.ui.SettingsExtras
import com.liquidglass.tvlauncher.ui.SpotifyScreen
import com.liquidglass.tvlauncher.ui.TileMotion
import com.liquidglass.tvlauncher.ui.WallpaperState

class MainActivity : ComponentActivity() {

    /** Lives as long as the activity: adb tools and the phone remote run in it. */
    private val appScope = MainScope()

    /** Bumped on every onResume so installs/uninstalls made elsewhere show up. */
    private var resumeSignal by mutableIntStateOf(0)

    /** Bumped on Home/Back: focus returns to the first favorite. */
    private var resetSignal by mutableIntStateOf(0)

    /** Bumped when the launcher comes back from an app it started: focus returns to that app's tile. */
    private var restoreSignal by mutableIntStateOf(0)
    private var pendingRestore = false

    /** One of the PAGE_ constants: info (top), home (middle), the app list (bottom). */
    private var page by mutableIntStateOf(PAGE_HOME)

    /** The app being opened (null when none) and whether its opening animation is out. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)
    private var launchExpanded by mutableStateOf(false)

    /** True while the launcher is on screen; wallpaper rotation only runs then. */
    private var inForeground by mutableStateOf(false)

    /** When a key or touch last arrived (the screensaver counts idle time from it), and whether it is up. */
    private var lastInputAt by mutableLongStateOf(System.currentTimeMillis())
    private var screensaverShowing by mutableStateOf(false)

    /** The key that woke the screensaver: it does nothing else, not even when it is released. */
    private var wakeKeyCode = -1

    /** Bumped to fetch the weather again (OK on the weather widget, or a new city from adb). */
    private var weatherTick by mutableIntStateOf(0)

    /** Bumped to fetch the calendar again (a new link, or OK on the events widget). */
    private var calendarTick by mutableIntStateOf(0)

    /** Bumped by "next wallpaper" in the settings. */
    private var skipSignal by mutableIntStateOf(0)

    /** Bumped when photos were sent from the phone or removed, so the wallpaper categories are listed again. */
    private var userPhotosTick by mutableIntStateOf(0)
    private var videoWallpaperTick by mutableIntStateOf(0)

    /** A wallpaper category the phone page asks to switch to (consumed by the screen). */
    private var forcedCategory by mutableStateOf<String?>(null)

    /** The phone's screen, as the latest frame sent by the Pilot app, and when it arrived. */
    private var mirrorFrame by mutableStateOf<ImageBitmap?>(null)
    private var mirrorAt = 0L

    /** After Back on a shared screen the frames are refused for a while (and the phone is told to stop). */
    private var mirrorRefusedUntil = 0L

    /** The cursor's real screen position, tracked regardless of which dot is actually drawing it
     * (the system overlay or the in-window fallback); this is what new deltas are added onto, so the
     * dot doesn't keep snapping back to center on every update once the overlay takes over. */
    private var cursorScreenPos: Offset? = null

    /** The fallback, in-window dot's position (null while [SystemService] is drawing its own instead,
     * or the feature isn't in use); only Compose moves it, adb is only called for the tap itself (see
     * receiveCursor). [cursorTick] fires on every update, whichever dot is the one actually showing,
     * so the watchdog below can time either of them out the same way. */
    private var cursorPos by mutableStateOf<Offset?>(null)
    private var cursorAt = 0L
    private var cursorTick by mutableIntStateOf(0)

    /** The quick settings (hold OK on the gear, or the Menu key). */
    private var showQuick by mutableStateOf(false)

    /** The Spotify player screen (opened from its widget, or from the phone). */
    private var showSpotify by mutableStateOf(false)

    /** Raised when the set of widgets was changed from outside the settings window (the phone sent Spotify). */
    private var widgetsTick by mutableIntStateOf(0)

    // Bookkeeping for the rotation: what it last did, so coming back from an app doesn't reshuffle.
    private var lastRotationKey = ""
    private var lastWallpaperAt = 0L

    private lateinit var store: FavoritesStore
    private lateinit var settings: LauncherSettings
    private lateinit var folderStore: FolderStore
    private lateinit var updates: UpdateManager
    private lateinit var remoteServer: RemoteServer
    private lateinit var boxDiscovery: BoxDiscovery
    private var frameMonitor: FrameMonitor? = null

    /** Bumped when the phone page asks for another mode, so the screen reads the setting again. */
    private var performanceTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FavoritesStore(this)
        settings = LauncherSettings(this)
        SpotifyHub.start(settings)
        LastFmHub.start(settings)
        folderStore = FolderStore(this)
        updates = UpdateManager(this, settings)
        Performance.refresh(this, settings)
        remoteServer = RemoteServer(
            token = { settings.remoteToken },
            handle = ::handleRemote,
            tempDir = cacheDir,
            upload = ::receivePhoto,
            mirror = ::receiveFrame,
            cursor = ::receiveCursor,
        )
        boxDiscovery = BoxDiscovery(this)
        handleIntent(intent)

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var favoriteKeys by remember { mutableStateOf(store.load()) }
            var folders by remember { mutableStateOf(folderStore.load()) }
            var menuApp by remember { mutableStateOf<AppInfo?>(null) }
            var folderPickApp by remember { mutableStateOf<AppInfo?>(null) }
            var openFolder by remember { mutableStateOf<String?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            var dockTransparency by remember { mutableIntStateOf(settings.dockTransparency) }
            var dockSize by remember { mutableIntStateOf(settings.dockSize) }
            var screensaverMinutes by remember { mutableIntStateOf(settings.screensaverMinutes) }
            var bigClock by remember { mutableStateOf(settings.bigClock) }
            var tileLighting by remember { mutableStateOf(settings.tileLighting) }
            var seasonalTheme by remember { mutableStateOf(settings.seasonalTheme) }
            var animatedWallpaper by remember { mutableStateOf(settings.animatedWallpaper) }
            val videoWallpaperFile by remember(videoWallpaperTick) {
                mutableStateOf(WallpaperRepository.videoFile(this).takeIf { it.isFile })
            }
            var tileTilt by remember { mutableStateOf(settings.tileTilt) }
            var parallax by remember { mutableStateOf(settings.parallax) }
            var accentIndex by remember { mutableIntStateOf(settings.accentIndex) }
            var glassIndex by remember { mutableIntStateOf(settings.glassTintIndex) }
            var glassStyle by remember { mutableIntStateOf(settings.glassStyle) }
            var performanceMode by remember(performanceTick) { mutableIntStateOf(settings.performanceMode) }
            var clockContent by remember { mutableIntStateOf(settings.clockContent) }
            var clockPosition by remember { mutableIntStateOf(settings.clockPosition) }
            var statusIcons by remember { mutableStateOf(settings.statusIcons) }
            var topBarMinimal by remember { mutableStateOf(settings.topBarMinimal) }
            var lastfmScrobbling by remember { mutableStateOf(settings.lastfmScrobbling) }
            var systemStatus by remember { mutableStateOf(SystemStatus.UNKNOWN) }
            var volumeMode by remember { mutableIntStateOf(settings.volumeMode) }
            var showVolumeDiag by remember { mutableStateOf(false) }
            var diagTick by remember { mutableIntStateOf(0) }
            var accessibilityOn by remember { mutableStateOf(false) }
            var notificationAccessOn by remember { mutableStateOf(false) }
            LaunchedEffect(showSettings, resumeSignal) {
                accessibilityOn = SystemTools.accessibilityOn(this@MainActivity)
                notificationAccessOn = SystemTools.notificationAccessOn(this@MainActivity)
            }
            val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarTick++ }
            var showPower by remember { mutableStateOf(false) }
            var poweringOff by remember { mutableStateOf(false) }
            var showWallpaperCategoryPicker by remember { mutableStateOf(false) }
            var iconPickerApp by remember { mutableStateOf<AppInfo?>(null) }
            var iconsVersion by remember { mutableIntStateOf(0) }
            var iconPacks by remember { mutableStateOf<List<IconPack>>(emptyList()) }
            var accountName by remember { mutableStateOf(AccountProvider.googleAccountName(this@MainActivity)) }

            val wallpaper = remember {
                WallpaperState(WallpaperImage(
                    Edition.bundledHero(resources),
                    ImageBitmap.imageResource(resources, R.drawable.bg_hero_blur),
                ))
            }
            var baseCatalog by remember { mutableStateOf(WallpaperCatalog.EMPTY) }
            // The repository's catalog plus the photos sent from the phone.
            val catalog = remember(baseCatalog, userPhotosTick) { WallpaperRepository.withUserPhotos(this@MainActivity, baseCatalog) }
            var wallpaperCategory by remember { mutableStateOf(settings.wallpaperCategory) }
            var wallpaperInterval by remember { mutableIntStateOf(settings.wallpaperIntervalMinutes) }
            var photoCredit by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(forcedCategory) {
                forcedCategory?.let {
                    wallpaperCategory = it
                    settings.wallpaperCategory = it
                    skipSignal++
                    forcedCategory = null
                }
            }
            var widgetKeys by remember(widgetsTick) { mutableStateOf(settings.enabledWidgets) }
            var widgetData by remember { mutableStateOf(WidgetData.EMPTY) }
            var agenda by remember { mutableStateOf(Agenda.EMPTY) }
            var weather by remember { mutableStateOf(WeatherRepository.cached(this@MainActivity)) }
            var weatherCity by remember { mutableStateOf(settings.weatherCity) }
            var nowPlaying by remember { mutableStateOf(NowPlayingState.NONE) }

            // Things the adb tools and the phone remote ask the screen to show.
            var showRemote by remember { mutableStateOf(false) }
            var remoteAlways by remember { mutableStateOf(settings.remoteAlways) }
            var remotePort by remember { mutableIntStateOf(0) }
            var remoteIp by remember { mutableStateOf<String?>(null) }
            var remoteTokenVersion by remember { mutableIntStateOf(0) }
            var showChangelog by remember { mutableStateOf(false) }
            var reportDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
            var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
            var disableList by remember { mutableStateOf(false) }
            var restoreList by remember { mutableStateOf<List<String>?>(null) }
            var showAdbDiag by remember { mutableStateOf(false) }
            var adbDiagText by remember { mutableStateOf("") }

            val openAdbDiagnostics: () -> Unit = {
                adbDiagText = getString(R.string.adb_diag_running)
                showAdbDiag = true
                appScope.launch { adbDiagText = SelfAdb.diagnose(this@MainActivity) }
            }

            // In-app updates (see UpdateManager): the status, the version found and the download.
            var silentUpdates by remember { mutableStateOf(settings.silentUpdates) }
            var adbStatus by remember { mutableStateOf("") }
            var autoCheck by remember { mutableStateOf(settings.autoCheckUpdates) }
            var updateInterval by remember { mutableIntStateOf(settings.updateIntervalSeconds) }

            // Automatic: while the launcher is on screen it asks GitHub for the newest version every
            // few seconds (5 by default; one tiny request), and installs a newer one as soon as the
            // remote has been left alone for a moment.
            LaunchedEffect(inForeground, autoCheck) {
                if (!inForeground || !autoCheck) return@LaunchedEffect
                while (true) {
                    val wait = updates.autoStep(userIdle = { System.currentTimeMillis() - lastInputAt > 4_000L })
                    delay(wait)
                }
            }

            // After an update: what is new, once.
            LaunchedEffect(Unit) {
                val current = Updater.currentVersionCode(this@MainActivity)
                val seen = settings.lastSeenVersionCode
                if (seen != current) {
                    val fresh = try {
                        val info = packageManager.getPackageInfo(packageName, 0)
                        info.firstInstallTime == info.lastUpdateTime
                    } catch (_: Exception) {
                        true
                    }
                    if ((seen in 1 until current) || (seen == 0L && !fresh)) showChangelog = true
                    settings.lastSeenVersionCode = current
                }
            }

            val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { name ->
                    AccountProvider.save(this@MainActivity, name)
                    accountName = name
                }
            }

            // Anything on top of the home screen: while it is open the screensaver doesn't start and
            // the breathing wallpaper stands still.
            val dialogOpen = showSettings || menuApp != null || iconPickerApp != null || showPower || showQuick ||
                openFolder != null || folderPickApp != null || showRemote || showChangelog || reportDialog != null || showSpotify ||
                confirm != null || disableList || restoreList != null || showAdbDiag || showVolumeDiag || launchRequest != null

            // Screensaver: starts after the chosen time without any key press, while the launcher
            // is on screen and nothing (a dialog, an app opening) is going on. Every key press
            // (see dispatchKeyEvent) restarts the wait.
            LaunchedEffect(inForeground, screensaverMinutes, dialogOpen) {
                if (!inForeground || screensaverMinutes <= 0 || dialogOpen) return@LaunchedEffect
                snapshotFlow { lastInputAt }.collectLatest {
                    delay(screensaverMinutes * 60_000L)
                    screensaverShowing = true
                }
            }

            LaunchedEffect(resumeSignal) {
                iconPacks = withContext(Dispatchers.Default) { IconPacks.installed(this@MainActivity) }
            }

            // Network and Bluetooth state for the status icons; refreshed while the launcher is on screen.
            LaunchedEffect(inForeground) {
                while (inForeground) {
                    systemStatus = withContext(Dispatchers.Default) { SystemStatus.read(this@MainActivity) }
                    delay(8_000)
                }
            }

            LaunchedEffect(resumeSignal, iconsVersion) {
                apps = AppRepository.load(this@MainActivity)
                if (accountName == null) accountName = AccountProvider.googleAccountName(this@MainActivity)
            }

            LaunchedEffect(resumeSignal) {
                if (baseCatalog.isEmpty) baseCatalog = WallpaperRepository.catalog(this@MainActivity)
            }

            // The widgets page: numbers about the network, Bluetooth and the box, refreshed every few
            // seconds while that page is shown (and once whenever the launcher comes back).
            LaunchedEffect(inForeground, page) {
                if (inForeground) {
                    widgetData = withContext(Dispatchers.Default) { WidgetDataReader.read(this@MainActivity) }
                    while (page == PAGE_INFO && inForeground) {
                        delay(8_000)
                        widgetData = withContext(Dispatchers.Default) { WidgetDataReader.read(this@MainActivity) }
                    }
                }
            }

            // What is playing, for the "Now playing" widget: only while that page is shown.
            LaunchedEffect(inForeground, page, widgetKeys) {
                if (inForeground && page == PAGE_INFO && WidgetType.NOW_PLAYING.key in widgetKeys) {
                    while (true) {
                        nowPlaying = withContext(Dispatchers.Default) { NowPlayingReader.read(this@MainActivity) }
                        delay(1_500)
                    }
                }
            }

            // What Spotify is playing, for its widget: only while the widgets page is shown (the player screen reads it by itself).
            LaunchedEffect(inForeground, page, widgetKeys, SpotifyHub.connected) {
                if (inForeground && SpotifyHub.connected && page == PAGE_INFO && WidgetType.SPOTIFY.key in widgetKeys) {
                    while (true) {
                        SpotifyHub.refreshPlayback()
                        delay(4_000)
                    }
                }
            }

            // The phone remote's web server runs while its window is open, or all the time when asked to.
            LaunchedEffect(showRemote, remoteAlways) {
                if (showRemote || remoteAlways) {
                    withContext(Dispatchers.IO) { remoteServer.start() }
                    remotePort = remoteServer.port
                    remoteIp = withContext(Dispatchers.Default) { WidgetDataReader.localAddress() }
                    boxDiscovery.start(remoteServer.port)
                } else {
                    remoteServer.stop()
                    remotePort = 0
                    boxDiscovery.stop()
                }
            }

            // The weather: for the chosen city, every half hour while the launcher is on screen. A city
            // sent over adb by name is looked up first.
            LaunchedEffect(inForeground, weatherTick, weatherCity.name) {
                settings.pendingWeatherCity?.let { name ->
                    settings.pendingWeatherCity = null
                    WeatherRepository.geocode(name)?.let { found ->
                        settings.customWeatherCity = found
                        settings.weatherCity = found
                        weatherCity = found
                        return@LaunchedEffect // the changed city restarts this effect
                    }
                }
                while (inForeground) {
                    WeatherRepository.load(this@MainActivity, weatherCity)?.let { weather = it }
                    delay(30 * 60_000L)
                }
            }

            // The calendar: fetched when the launcher comes up, when a link is set or refreshed, and
            // every 20 minutes after that.
            LaunchedEffect(inForeground, calendarTick) {
                while (inForeground) {
                    agenda = AgendaRepository.load(this@MainActivity, settings.icalUrl)
                    delay(20 * 60_000L)
                }
            }

            // Wallpaper rotation. Restarts when the category, interval or "next" changes, and
            // while the launcher is in the background it does nothing (no downloads for nobody).
            LaunchedEffect(catalog, wallpaperCategory, wallpaperInterval, inForeground, skipSignal) {
                if (!inForeground) return@LaunchedEffect
                val files = catalog.filesFor(wallpaperCategory)
                if (files.isEmpty()) {
                    wallpaper.show(null)
                    photoCredit = null
                    return@LaunchedEffect
                }
                val key = "$wallpaperCategory/$skipSignal"
                val changedByUser = key != lastRotationKey
                lastRotationKey = key
                if (!changedByUser) {
                    // Back from an app: carry on with the old timer instead of changing at once.
                    delay(maxOf(0L, lastWallpaperAt + wallpaperInterval * 60_000L - System.currentTimeMillis()))
                }
                while (true) {
                    val pool = files.filter { it.path != settings.lastWallpaper }.ifEmpty { files }
                    val pick = pool.random()
                    val image = WallpaperRepository.load(this@MainActivity, pick.path)
                    if (image != null) {
                        wallpaper.show(image)
                        settings.lastWallpaper = pick.path
                        photoCredit = pick.credit
                        lastWallpaperAt = System.currentTimeMillis()
                        delay(wallpaperInterval * 60_000L)
                    } else {
                        delay(30_000L) // offline or a bad file: try again shortly
                    }
                }
            }

            // First run: YouTube, Netflix, YouTube Music, Play Store, Spotify and
            // Disney+ (whichever are installed). Only if none of them is installed
            // is the dock filled from the start of the list, so it is never empty.
            LaunchedEffect(apps) {
                if (favoriteKeys == null && apps.isNotEmpty()) {
                    val defaults = PriorityApps.defaultFavoriteKeys(apps)
                        .ifEmpty { apps.take(FavoritesStore.MAX).map { it.key } }
                    favoriteKeys = defaults
                    store.save(defaults)
                }
            }

            val keys = favoriteKeys.orEmpty()
            val byKey = remember(apps) { apps.associateBy { it.key } }
            val favorites = remember(apps, keys) { keys.mapNotNull { byKey[it] } }
            val widgets = WidgetType.values().filter { it.key in widgetKeys }
            val weatherLine = weather?.let { "${it.city} · ${it.temperature.roundToInt()}° " + stringResource(Weather.textOf(it.code)) }

            // The app list shows the folders first, then every app that isn't in one.
            val folderTiles = remember(apps, folders) {
                folders.mapNotNull { folder ->
                    val members = folder.keys.mapNotNull { byKey[it] }
                    if (members.isEmpty()) null else FolderArt.tile(folder.name, members)
                }
            }
            val gridApps = remember(apps, folders, folderTiles) {
                val inFolders = folders.flatMap { it.keys }.toSet()
                folderTiles + apps.filter { it.key !in inFolders }
            }

            fun saveKeys(updated: List<String>) {
                favoriteKeys = updated
                store.save(updated)
            }

            fun saveFolders(updated: List<AppFolder>) {
                folders = updated
                folderStore.save(updated)
            }

            // Back never leaves the launcher: from the info or app pages it returns to the home
            // screen, and from the home screen it just returns focus to the dock.
            BackHandler {
                menuApp = null
                if (mirrorFrame != null) {
                    // Back ends the sharing; the phone is told on its next frame.
                    mirrorFrame = null
                    mirrorRefusedUntil = System.currentTimeMillis() + 15_000L
                } else if (KeyLearner.learning) {
                    KeyLearner.stop()
                } else if (page != PAGE_HOME) {
                    page = PAGE_HOME
                } else {
                    resetSignal++
                }
            }

            // The shared phone screen goes away a few seconds after the last frame.
            LaunchedEffect(mirrorFrame != null) {
                while (mirrorFrame != null) {
                    delay(1_000)
                    if (System.currentTimeMillis() - mirrorAt > 4_000L) mirrorFrame = null
                }
            }

            // The gyroscope cursor dot goes away a few seconds after the last update from the phone,
            // wherever it's actually being drawn; restarts (and so cancels the previous timer) on every
            // new update via the cursorTick key.
            LaunchedEffect(cursorTick) {
                delay(3_100)
                if (System.currentTimeMillis() - cursorAt >= 3_000L) {
                    cursorPos = null
                    cursorScreenPos = null
                    SystemService.hideCursor()
                }
            }

            // The button taught in "learn" mode was saved: say which one it was.
            val learnedKey = KeyLearner.learned
            LaunchedEffect(learnedKey) {
                if (learnedKey.isNotEmpty()) {
                    toast(getString(R.string.quick_key_learned, learnedKey))
                    KeyLearner.acknowledge()
                }
            }

            val theme = LauncherTheme.of(accentIndex, glassIndex)
            val glassBackdrop = remember(wallpaper) { GlassBackdrop(wallpaper) }
            CompositionLocalProvider(
                LocalTheme provides theme,
                LocalGlassStyle provides glassStyle,
                LocalGlassBackdrop provides glassBackdrop,
                LocalTileLighting provides tileLighting,
                LocalTileTilt provides tileTilt,
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeScreen(
                    favorites = favorites,
                    allApps = gridApps,
                    widgets = widgets,
                    widgetData = widgetData,
                    agenda = agenda,
                    weather = weather,
                    weatherLine = weatherLine,
                    nowPlaying = nowPlaying,
                    onWidget = { type ->
                        when (type) {
                            WidgetType.AGENDA -> calendarTick++
                            WidgetType.WEATHER -> weatherTick++
                            WidgetType.NETWORK -> openSystemScreen(Settings.ACTION_WIFI_SETTINGS)
                            WidgetType.BLUETOOTH -> openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
                            WidgetType.SYSTEM -> openSystemScreen(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                            WidgetType.NOW_PLAYING -> {
                                val item = nowPlaying.item
                                if (item != null) {
                                    item.playPause()
                                } else if (!nowPlaying.hasAccess) {
                                    grantNotificationAccess()
                                }
                            }
                            WidgetType.SPOTIFY -> if (SpotifyHub.connected) spotifyPlayPause()
                            WidgetType.CALENDAR -> Unit
                        }
                    },
                    onWidgetLong = { type ->
                        if (type == WidgetType.NOW_PLAYING) nowPlaying.item?.next()
                        if (type == WidgetType.SPOTIFY && SpotifyHub.connected) showSpotify = true
                    },
                    page = page,
                    onPage = { page = it },
                    resetSignal = resetSignal,
                    focusPaused = dialogOpen,
                    accountName = accountName,
                    updateAvailable = updates.info != null,
                    wallpaper = wallpaper,
                    dockTransparency = dockTransparency / 100f,
                    dockSize = dockSize / 100f,
                    bigClock = bigClock,
                    clockContent = clockContent,
                    clockPosition = clockPosition,
                    // The minimal bar has no status icons, but its list shows the same states in words.
                    status = if (statusIcons || topBarMinimal) systemStatus else null,
                    onOpenWifi = { openSystemScreen(Settings.ACTION_WIFI_SETTINGS) },
                    onOpenBluetooth = { openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS) },
                    onLaunch = { app, from ->
                        if (launchRequest == null) {
                            launchRequest = LaunchRequest(app, from)
                            launchExpanded = true
                        }
                    },
                    // Titles, folders and the system-home shortcut have no favorite/uninstall menu.
                    onOpenMenu = { if (it.intentUri == null && it in apps) menuApp = it },
                    onOpenFolder = { openFolder = it },
                    onOpenSettings = { showSettings = true },
                    onOpenPower = { showQuick = true },
                    onOpenAccount = {
                        if (accountName == null) {
                            try {
                                accountPicker.launch(AccountProvider.chooserIntent())
                            } catch (_: Exception) {
                                openAccountSettings()
                            }
                        } else {
                            openAccountSettings()
                        }
                    },
                    restoreSignal = restoreSignal,
                    parallaxOn = parallax,
                    minimalTopBar = topBarMinimal,
                    seasonalTheme = seasonalTheme,
                    videoWallpaper = if (animatedWallpaper) videoWallpaperFile else null,
                    idle = dialogOpen || screensaverShowing || !inForeground,
                    modifier = Modifier.fillMaxSize(),
                )

                // Draws over everything: the tile growing to fill the screen as the app opens.
                LaunchOverlay(
                    request = launchRequest,
                    expanded = launchExpanded,
                    onExpanded = { launchRequest?.let { startApp(it.app) } },
                    onCollapsed = { launchRequest = null },
                )

                Screensaver(
                    wallpaper = wallpaper,
                    visible = screensaverShowing,
                    extraLine = if (WidgetType.WEATHER.key in widgetKeys) weatherLine else null,
                )

                // Not a dialog on purpose: a dialog would take the keys, and this waits for one.
                if (KeyLearner.learning) LearnKeyPrompt()

                // The phone's screen, while it is being shared: full screen, black bars around a portrait picture.
                mirrorFrame?.let { MirrorScreen(it) }

                // The gyroscope cursor dot, while the phone's touchpad screen is sending updates.
                cursorPos?.let { CursorOverlay(it) }
            }

            if (showSettings) {
                val offLabel = stringResource(R.string.wallpaper_off)
                val allLabel = stringResource(R.string.wallpaper_all)
                // Only "off" until the repo has photos; then "all" and every category.
                val options = if (catalog.isEmpty) {
                    listOf(WallpaperCatalog.OFF to offLabel)
                } else {
                    listOf(WallpaperCatalog.ALL to allLabel) + catalog.categories.map { it.id to it.name } +
                        (WallpaperCatalog.OFF to offLabel)
                }
                val versionName = Updater.currentVersionName(this@MainActivity)
                val newVersion = updates.info
                val newVersionName = newVersion?.versionName ?: ""
                val statusText = when (updates.status) {
                    UpdateStatus.IDLE -> stringResource(R.string.update_version, versionName)
                    UpdateStatus.CHECKING -> stringResource(R.string.update_checking, versionName)
                    UpdateStatus.UP_TO_DATE -> stringResource(R.string.update_latest, versionName)
                    UpdateStatus.AVAILABLE -> stringResource(R.string.update_available, versionName, newVersionName)
                    UpdateStatus.CHECK_FAILED -> stringResource(R.string.update_check_failed)
                    UpdateStatus.DOWNLOADING -> stringResource(R.string.update_downloading, updates.percent)
                    UpdateStatus.DOWNLOAD_FAILED -> stringResource(R.string.update_download_failed)
                    UpdateStatus.NEEDS_PERMISSION -> stringResource(R.string.update_permission)
                    UpdateStatus.WRONG_SIGNATURE -> stringResource(R.string.update_wrong_key)
                    UpdateStatus.INSTALLING -> stringResource(R.string.update_installing)
                    UpdateStatus.SILENT_INSTALLING -> stringResource(R.string.update_silent_installing)
                    UpdateStatus.SILENT_FAILED -> updates.problem
                }
                val installLabel = if (newVersion != null && updates.status != UpdateStatus.DOWNLOADING) {
                    stringResource(R.string.settings_update_install, newVersionName)
                } else {
                    null
                }
                val lite = Performance.lite
                val performanceNote = stringResource(
                    when {
                        performanceMode == LauncherSettings.PERF_LITE -> R.string.performance_note_lite
                        performanceMode == LauncherSettings.PERF_FULL -> R.string.performance_note_full
                        lite -> R.string.performance_note_auto_on
                        else -> R.string.performance_note_auto_off
                    },
                )
                var animationScale by remember { mutableStateOf(SystemTools.animationScale(this@MainActivity)) }
                var quickKeyShown by remember { mutableStateOf(settings.quickKey) }
                SettingsDialog(
                    versionLabel = stringResource(R.string.settings_version_label, if (Edition.lite) "$versionName Lite" else versionName),
                    transparencyPercent = dockTransparency,
                    onTransparencyChange = {
                        dockTransparency = it
                        settings.dockTransparency = it
                    },
                    bigClock = bigClock,
                    onBigClockChange = {
                        bigClock = it
                        settings.bigClock = it
                    },
                    tileLighting = tileLighting,
                    onTileLightingChange = {
                        tileLighting = it
                        settings.tileLighting = it
                    },
                    seasonalTheme = seasonalTheme,
                    onSeasonalThemeChange = {
                        seasonalTheme = it
                        settings.seasonalTheme = it
                    },
                    animatedWallpaper = animatedWallpaper,
                    hasVideoWallpaper = videoWallpaperFile != null,
                    onAnimatedWallpaperChange = {
                        animatedWallpaper = it
                        settings.animatedWallpaper = it
                    },
                    clockContent = clockContent,
                    onClockContentChange = {
                        clockContent = it
                        settings.clockContent = it
                    },
                    clockPosition = clockPosition,
                    onClockPositionChange = {
                        clockPosition = it
                        settings.clockPosition = it
                    },
                    statusIcons = statusIcons,
                    onStatusIconsChange = {
                        statusIcons = it
                        settings.statusIcons = it
                    },
                    dockSizePercent = dockSize,
                    onDockSizeChange = {
                        dockSize = it
                        settings.dockSize = it
                    },
                    screensaverMinutes = screensaverMinutes,
                    onScreensaverChange = {
                        screensaverMinutes = it
                        settings.screensaverMinutes = it
                    },
                    onShowScreensaver = {
                        showSettings = false
                        screensaverShowing = true
                    },
                    categoryOptions = options,
                    categoryId = wallpaperCategory,
                    onPickCategories = { showWallpaperCategoryPicker = true },
                    intervalMinutes = wallpaperInterval,
                    onIntervalChange = {
                        wallpaperInterval = it
                        settings.wallpaperIntervalMinutes = it
                    },
                    photoCredit = photoCredit,
                    onNextWallpaper = { skipSignal++ },
                    updateStatus = statusText,
                    installLabel = installLabel,
                    onCheckUpdates = { appScope.launch { updates.checkNow() } },
                    onInstallUpdate = { appScope.launch { updates.install(automatic = false) } },
                    silentUpdates = silentUpdates,
                    onSilentUpdatesChange = {
                        silentUpdates = it
                        settings.silentUpdates = it
                    },
                    adbStatus = adbStatus,
                    onConnectAdb = {
                        adbStatus = getString(R.string.adb_connecting)
                        appScope.launch {
                            adbStatus = when (val result = SelfAdb.shell(this@MainActivity, "id")) {
                                is AdbResult.Ok -> getString(R.string.adb_connected)
                                else -> adbProblem(result)
                            }
                        }
                    },
                    autoCheckUpdates = autoCheck,
                    onAutoCheckChange = {
                        autoCheck = it
                        settings.autoCheckUpdates = it
                    },
                    networkStatus = networkStatus(),
                    volumeMode = volumeMode,
                    onVolumeModeChange = {
                        volumeMode = it
                        settings.volumeMode = it
                    },
                    onOpenVolumeDiagnostics = {
                        showSettings = false
                        showVolumeDiag = true
                    },
                    accessibilityOn = accessibilityOn,
                    onOpenAccessibility = {
                        if (SystemTools.enableAccessibility(this@MainActivity)) {
                            accessibilityOn = true
                        } else {
                            showSettings = false
                            openSystemScreen(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        }
                    },
                    onOpenPowerMenu = {
                        showSettings = false
                        showPower = true
                    },
                    systemShortcuts = listOf(
                        stringResource(R.string.shortcut_wifi) to { openSystemScreen(Settings.ACTION_WIFI_SETTINGS) },
                        stringResource(R.string.shortcut_bluetooth) to { openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS) },
                        stringResource(R.string.shortcut_display) to { openSystemScreen(Settings.ACTION_DISPLAY_SETTINGS) },
                        stringResource(R.string.shortcut_sound) to { openSystemScreen(Settings.ACTION_SOUND_SETTINGS) },
                        stringResource(R.string.shortcut_dream) to { openSystemScreen(Settings.ACTION_DREAM_SETTINGS) },
                        stringResource(R.string.shortcut_a11y) to { openSystemScreen(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    ),
                    enabledWidgets = widgetKeys,
                    onWidgetToggle = { type, on ->
                        val updated = if (on) widgetKeys + type.key else widgetKeys - type.key
                        widgetKeys = updated
                        settings.enabledWidgets = updated
                    },
                    calendarLinked = settings.icalUrl != null,
                    calendarStatus = stringResource(
                        if (settings.icalUrl != null) R.string.widgets_calendar_linked else R.string.widgets_calendar_unlinked,
                    ),
                    onRefreshCalendar = { calendarTick++ },
                    onDisconnectCalendar = {
                        settings.icalUrl = null
                        calendarTick++
                    },
                    onRequestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                    weatherCities = (Cities.BUILTIN + listOfNotNull(settings.customWeatherCity)).map { it.name }.distinct(),
                    weatherCity = weatherCity.name,
                    onWeatherCityChange = { name ->
                        val chosen: WeatherCity? = (Cities.BUILTIN + listOfNotNull(settings.customWeatherCity)).firstOrNull { it.name == name }
                        if (chosen != null) {
                            settings.weatherCity = chosen
                            weatherCity = chosen
                        }
                    },
                    onOpenSystemSettings = { openSettings() },
                    extras = SettingsExtras(
                        accentIndex = accentIndex,
                        onAccent = {
                            accentIndex = it
                            settings.accentIndex = it
                        },
                        glassIndex = glassIndex,
                        onGlass = {
                            glassIndex = it
                            settings.glassTintIndex = it
                        },
                        glassStyle = glassStyle,
                        onGlassStyle = {
                            glassStyle = it
                            settings.glassStyle = it
                        },
                        tileTilt = tileTilt,
                        onTileTilt = {
                            tileTilt = it
                            settings.tileTilt = it
                        },
                        parallax = parallax,
                        onParallax = {
                            parallax = it
                            settings.parallax = it
                        },
                        performanceMode = performanceMode,
                        performanceNote = performanceNote,
                        onPerformance = {
                            performanceMode = it
                            settings.performanceMode = it
                            settings.autoLite = false
                            Performance.refresh(this@MainActivity, settings)
                        },
                        updateIntervalSeconds = updateInterval,
                        onUpdateInterval = {
                            updateInterval = it
                            settings.updateIntervalSeconds = it
                        },
                        onShowChangelog = {
                            showSettings = false
                            showChangelog = true
                        },
                        onAdbDiagnostics = {
                            showSettings = false
                            openAdbDiagnostics()
                        },
                        topBarMinimal = topBarMinimal,
                        onTopBarMinimal = {
                            topBarMinimal = it
                            settings.topBarMinimal = it
                        },
                        quickKeyName = if (quickKeyShown == 0) "" else KeyLog.name(quickKeyShown),
                        onLearnQuickKey = {
                            showSettings = false
                            KeyLearner.start()
                        },
                        onClearQuickKey = {
                            settings.quickKey = 0
                            quickKeyShown = 0
                        },
                        onSetupAdb = {
                            showSettings = false
                            reportDialog = getString(R.string.tools_setup_title) to getString(R.string.tools_setup_running)
                            appScope.launch {
                                val result = SystemTools.grantPermissions(this@MainActivity)
                                reportDialog = getString(R.string.tools_setup_title) to if (result is AdbResult.Ok) {
                                    // The connection works, so the silent updates can be used.
                                    settings.silentUpdates = true
                                    silentUpdates = true
                                    accessibilityOn = SystemTools.accessibilityOn(this@MainActivity)
                                    notificationAccessOn = SystemTools.notificationAccessOn(this@MainActivity)
                                    result.value.joinToString("\n") + "\n\n" + getString(R.string.tools_setup_done)
                                } else {
                                    adbProblem(result)
                                }
                            }
                        },
                        onFreeMemory = {
                            toast(getString(R.string.tools_working))
                            appScope.launch { reportAdb(SystemTools.freeMemory(this@MainActivity)) { it } }
                        },
                        animationScale = animationScale,
                        onAnimationScale = { scale ->
                            appScope.launch {
                                val result = SystemTools.setAnimationScale(this@MainActivity, scale)
                                if (result is AdbResult.Ok) animationScale = scale else toast(adbProblem(result))
                            }
                        },
                        onDisableApps = {
                            showSettings = false
                            disableList = true
                        },
                        onRestoreApps = {
                            showSettings = false
                            appScope.launch {
                                when (val result = SystemTools.disabledPackages(this@MainActivity)) {
                                    is AdbResult.Ok -> if (result.value.isEmpty()) toast(getString(R.string.tools_nothing_disabled)) else restoreList = result.value
                                    else -> toast(adbProblem(result))
                                }
                            }
                        },
                        onTvOn = { appScope.launch { reportAdb(SystemTools.tvOn(this@MainActivity)) { getString(R.string.tools_cec_sent) } } },
                        onTvToggle = { appScope.launch { reportAdb(SystemTools.tvPowerToggle(this@MainActivity)) { getString(R.string.tools_cec_sent) } } },
                        onRemote = {
                            showSettings = false
                            showRemote = true
                        },
                        notificationAccessOn = notificationAccessOn,
                        onNotificationAccess = {
                            grantNotificationAccess()
                            notificationAccessOn = SystemTools.notificationAccessOn(this@MainActivity)
                        },
                        lastfmConnected = LastFmHub.connected,
                        lastfmUser = LastFmHub.user,
                        lastfmScrobbling = lastfmScrobbling,
                        onLastfmScrobblingChange = {
                            lastfmScrobbling = it
                            settings.lastfmScrobbling = it
                        },
                        onLastfmDisconnect = { LastFmHub.logout() },
                    ),
                    onDismiss = { showSettings = false },
                )
            }

            if (showQuick) {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val volumePercent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val lite = Performance.lite
                val tiles = listOf(
                    QuickTile(
                        stringResource(R.string.shortcut_wifi),
                        networkStatus(),
                        systemStatus.network != com.liquidglass.tvlauncher.data.NetworkKind.OFFLINE,
                    ) {
                        showQuick = false
                        openSystemScreen(Settings.ACTION_WIFI_SETTINGS)
                    },
                    QuickTile(
                        stringResource(R.string.shortcut_bluetooth),
                        stringResource(
                            when (systemStatus.bluetooth) {
                                true -> R.string.quick_on
                                false -> R.string.quick_off
                                null -> R.string.quick_none
                            },
                        ),
                        systemStatus.bluetooth == true,
                    ) {
                        showQuick = false
                        openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
                    },
                    QuickTile(stringResource(R.string.shortcut_sound), "$volumePercent%", volumePercent > 0) {
                        showQuick = false
                        openSystemScreen(Settings.ACTION_SOUND_SETTINGS)
                    },
                    QuickTile(stringResource(R.string.power_screensaver), stringResource(R.string.quick_now), false) {
                        showQuick = false
                        screensaverShowing = true
                    },
                    QuickTile(
                        stringResource(R.string.settings_performance),
                        stringResource(
                            when {
                                performanceMode == LauncherSettings.PERF_AUTO && lite -> R.string.performance_quick_auto_lite
                                performanceMode == LauncherSettings.PERF_AUTO -> R.string.performance_auto
                                performanceMode == LauncherSettings.PERF_LITE -> R.string.performance_lite
                                else -> R.string.performance_full
                            },
                        ),
                        lite,
                    ) {
                        val next = (performanceMode + 1) % 3
                        performanceMode = next
                        settings.performanceMode = next
                        settings.autoLite = false
                        Performance.refresh(this@MainActivity, settings)
                    },
                    QuickTile(stringResource(R.string.tools_free_memory), stringResource(R.string.quick_adb), false) {
                        showQuick = false
                        toast(getString(R.string.tools_working))
                        appScope.launch { reportAdb(SystemTools.freeMemory(this@MainActivity)) { it } }
                    },
                    QuickTile(stringResource(R.string.quick_standby), stringResource(R.string.quick_adb), false) {
                        showQuick = false
                        appScope.launch { reportAdb(SystemTools.standby(this@MainActivity)) { getString(R.string.quick_sent) } }
                    },
                    QuickTile(stringResource(R.string.quick_reboot), stringResource(R.string.quick_adb), false) {
                        showQuick = false
                        confirm = ConfirmRequest(getString(R.string.quick_reboot_ask), getString(R.string.quick_reboot)) {
                            appScope.launch { reportAdb(SystemTools.reboot(this@MainActivity)) { getString(R.string.quick_sent) } }
                        }
                    },
                    QuickTile(stringResource(R.string.tools_tv_on), "HDMI-CEC", false) {
                        showQuick = false
                        appScope.launch { reportAdb(SystemTools.tvOn(this@MainActivity)) { getString(R.string.tools_cec_sent) } }
                    },
                    QuickTile(stringResource(R.string.tools_tv_toggle), "HDMI-CEC", false) {
                        showQuick = false
                        appScope.launch { reportAdb(SystemTools.tvPowerToggle(this@MainActivity)) { getString(R.string.tools_cec_sent) } }
                    },
                    QuickTile(stringResource(R.string.quick_remote), stringResource(R.string.quick_qr), remoteAlways) {
                        showQuick = false
                        showRemote = true
                    },
                    QuickTile(
                        stringResource(R.string.settings_custom_volume),
                        stringResource(
                            when (volumeMode) {
                                LauncherSettings.VOLUME_OFF -> R.string.volume_mode_off
                                LauncherSettings.VOLUME_REPLACE -> R.string.volume_mode_replace_short
                                else -> R.string.volume_mode_beside_short
                            },
                        ),
                        volumeMode != LauncherSettings.VOLUME_OFF,
                    ) {
                        val next = (volumeMode + 1) % 3
                        volumeMode = next
                        settings.volumeMode = next
                    },
                    QuickTile(stringResource(R.string.settings_volume_diagnostics), stringResource(R.string.quick_diagnostics), false) {
                        showQuick = false
                        showVolumeDiag = true
                    },
                    QuickTile(stringResource(R.string.settings_adb_diagnostics), stringResource(R.string.quick_diagnostics), false) {
                        showQuick = false
                        openAdbDiagnostics()
                    },
                    QuickTile(stringResource(R.string.group_system), stringResource(R.string.quick_settings), false) {
                        showQuick = false
                        showSettings = true
                    },
                )
                QuickPanel(
                    title = stringResource(R.string.quick_title),
                    tiles = tiles,
                    onDismiss = { showQuick = false },
                )
            }

            menuApp?.let { app ->
                val shown = favorites.map { it.key }
                val index = shown.indexOf(app.key)
                val isFavorite = index >= 0
                val folder = FolderStore.folderOf(folders, app.key)
                val options = buildList {
                    if (isFavorite) {
                        add(MenuOption(stringResource(R.string.menu_remove_favorite)) {
                            saveKeys(keys - app.key)
                        })
                        if (index > 0) {
                            add(MenuOption(stringResource(R.string.menu_move_left)) {
                                saveKeys(swapped(shown, index, index - 1))
                            })
                        }
                        if (index < shown.size - 1) {
                            add(MenuOption(stringResource(R.string.menu_move_right)) {
                                saveKeys(swapped(shown, index, index + 1))
                            })
                        }
                    } else if (shown.size < FavoritesStore.MAX) {
                        add(MenuOption(stringResource(R.string.menu_add_favorite)) {
                            saveKeys(shown + app.key)
                        })
                    }
                    if (folder == null) {
                        add(MenuOption(stringResource(R.string.menu_add_to_folder)) { folderPickApp = app })
                    } else {
                        add(MenuOption(stringResource(R.string.menu_remove_from_folder, folder.name)) {
                            saveFolders(FolderStore.remove(folders, app.key))
                        })
                    }
                    add(MenuOption(stringResource(R.string.menu_change_icon)) { iconPickerApp = app })
                    if (IconOverrides(this@MainActivity).get(app.key) != null) {
                        add(MenuOption(stringResource(R.string.menu_reset_icon)) {
                            IconOverrides(this@MainActivity).clear(app.key)
                            iconsVersion++
                        })
                    }
                    add(MenuOption(stringResource(R.string.menu_app_info)) { openAppInfo(app) })
                    add(MenuOption(stringResource(R.string.menu_force_stop)) {
                        appScope.launch { reportAdb(SystemTools.forceStop(this@MainActivity, app.packageName)) { getString(R.string.menu_force_stopped, app.label) } }
                    })
                    if (!SystemTools.isProtected(this@MainActivity, app.packageName)) {
                        add(MenuOption(stringResource(R.string.menu_disable_app)) { confirm = disableRequest(app.packageName, app.label) { iconsVersion++ } })
                    }
                    add(MenuOption(stringResource(R.string.menu_uninstall)) { uninstall(app) })
                }
                AppMenu(title = app.label, options = options, onDismiss = { menuApp = null })
            }

            folderPickApp?.let { app ->
                val names = (FolderStore.PRESETS + folders.map { it.name }).distinct()
                AppMenu(
                    title = stringResource(R.string.menu_pick_folder),
                    options = names.map { name ->
                        MenuOption(name) { saveFolders(FolderStore.add(folders, name, app.key)) }
                    },
                    onDismiss = { folderPickApp = null },
                )
            }

            openFolder?.let { name ->
                val members = folders.firstOrNull { it.name == name }?.keys.orEmpty().mapNotNull { byKey[it] }
                if (members.isEmpty()) {
                    openFolder = null
                } else {
                    FolderDialog(
                        name = name,
                        apps = members,
                        onLaunch = { app ->
                            openFolder = null
                            if (launchRequest == null) {
                                // The dialog has its own window, so the tile's position there means nothing: grow from the middle.
                                val metrics = resources.displayMetrics
                                val cx = metrics.widthPixels / 2f
                                val cy = metrics.heightPixels / 2f
                                launchRequest = LaunchRequest(app, Rect(cx - 200f, cy - 120f, cx + 200f, cy + 120f))
                                launchExpanded = true
                            }
                        },
                        onMenu = { menuApp = it },
                        onDismiss = { openFolder = null },
                    )
                }
            }

            if (disableList) {
                val candidates = apps.filter { !it.isFolder && it.intentUri == null && !SystemTools.isProtected(this@MainActivity, it.packageName) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                AppMenu(
                    title = stringResource(R.string.tools_disable_apps),
                    options = candidates.map { app ->
                        val suffix = if (SystemTools.isHomeApp(this@MainActivity, app.packageName)) " · " + stringResource(R.string.tools_home_app) else ""
                        MenuOption(app.label + suffix) { confirm = disableRequest(app.packageName, app.label) { iconsVersion++ } }
                    },
                    onDismiss = { disableList = false },
                )
            }

            restoreList?.let { packages ->
                AppMenu(
                    title = stringResource(R.string.tools_restore_apps),
                    options = packages.map { pkg ->
                        val label = SystemTools.labelOf(this@MainActivity, pkg)
                        MenuOption(if (label == pkg) pkg else "$label ($pkg)") {
                            appScope.launch {
                                val result = SystemTools.setAppEnabled(this@MainActivity, pkg, true)
                                if (result is AdbResult.Ok) {
                                    toast(getString(R.string.tools_restored, label))
                                    iconsVersion++
                                } else {
                                    toast(adbProblem(result))
                                }
                            }
                        }
                    },
                    onDismiss = { restoreList = null },
                )
            }

            confirm?.let { request ->
                AppMenu(
                    title = request.title,
                    options = listOf(
                        MenuOption(request.label) { request.action() },
                        MenuOption(stringResource(R.string.cancel)) {},
                    ),
                    onDismiss = { confirm = null },
                )
            }

            if (showSpotify && SpotifyHub.connected) {
                SpotifyScreen(ensureDevice = { spotifyDevice() }, onDismiss = { showSpotify = false })
            }

            if (showRemote) {
                val token = remember(remoteTokenVersion) { settings.remoteToken }
                val url = if (remoteIp != null && remotePort > 0) "http://$remoteIp:$remotePort/?t=$token" else null
                RemoteDialog(
                    url = url,
                    always = remoteAlways,
                    onToggleAlways = {
                        remoteAlways = !remoteAlways
                        settings.remoteAlways = remoteAlways
                    },
                    onNewLink = {
                        settings.newRemoteToken()
                        remoteTokenVersion++
                    },
                    onDismiss = { showRemote = false },
                )
            }

            if (showChangelog) {
                DiagnosticsDialog(
                    title = stringResource(R.string.changelog_title, Updater.currentVersionName(this@MainActivity)),
                    text = Changelog.NEWEST.joinToString("\n\n") { "•  $it" },
                    actions = emptyList(),
                    closeLabel = stringResource(R.string.changelog_ok),
                    onDismiss = { showChangelog = false },
                    monospace = false,
                )
            }

            reportDialog?.let { (title, text) ->
                DiagnosticsDialog(
                    title = title,
                    text = text,
                    actions = emptyList(),
                    closeLabel = stringResource(R.string.diag_close),
                    onDismiss = { reportDialog = null },
                    monospace = false,
                )
            }

            if (showAdbDiag) {
                DiagnosticsDialog(
                    title = stringResource(R.string.settings_adb_diagnostics),
                    text = adbDiagText,
                    actions = listOf(
                        MenuOption(stringResource(R.string.diag_refresh)) {
                            adbDiagText = getString(R.string.adb_diag_running)
                            appScope.launch { adbDiagText = SelfAdb.diagnose(this@MainActivity) }
                        },
                    ),
                    closeLabel = stringResource(R.string.diag_close),
                    onDismiss = { showAdbDiag = false },
                )
            }

            if (showVolumeDiag) {
                // diagTick only forces the text to be rebuilt after a test.
                val text = remember(diagTick, volumeMode) { volumeDiagnostics() }
                DiagnosticsDialog(
                    title = stringResource(R.string.diag_title),
                    text = text,
                    actions = listOf(
                        MenuOption(stringResource(R.string.diag_test_bar)) {
                            if (!SystemService.testOverlay(55)) {
                                Toast.makeText(this@MainActivity, getString(R.string.power_needs_service), Toast.LENGTH_LONG).show()
                            }
                            diagTick++
                        },
                        MenuOption(stringResource(R.string.diag_volume_up)) { testVolume(AudioManager.ADJUST_RAISE); diagTick++ },
                        MenuOption(stringResource(R.string.diag_volume_down)) { testVolume(AudioManager.ADJUST_LOWER); diagTick++ },
                        MenuOption(stringResource(R.string.diag_system_windows)) {
                            reportDialog = getString(R.string.diag_system_windows) to getString(R.string.tools_working)
                            appScope.launch {
                                val result = SelfAdb.run(this@MainActivity) { session ->
                                    "okna z „volume”:\n" + session.sh("dumpsys window windows | grep -i volume | head -12").text +
                                        "\n\naudio (HDMI/CEC/stała głośność):\n" +
                                        session.sh("dumpsys audio | grep -i -E 'fixed|hdmi|cec|system audio' | head -14").text
                                }
                                reportDialog = getString(R.string.diag_system_windows) to if (result is AdbResult.Ok) result.value else adbProblem(result)
                            }
                        },
                        MenuOption(stringResource(R.string.diag_refresh)) { diagTick++ },
                    ),
                    closeLabel = stringResource(R.string.diag_close),
                    onDismiss = { showVolumeDiag = false },
                )
            }

            if (showPower) {
                val needsService = stringResource(R.string.power_needs_service)
                AppMenu(
                    title = stringResource(R.string.power_title),
                    options = listOf(
                        MenuOption(stringResource(R.string.power_screensaver)) { screensaverShowing = true },
                        MenuOption(stringResource(R.string.power_standby)) {
                            appScope.launch { reportAdb(SystemTools.standby(this@MainActivity)) { getString(R.string.quick_sent) } }
                        },
                        MenuOption(stringResource(R.string.quick_reboot)) {
                            confirm = ConfirmRequest(getString(R.string.quick_reboot_ask), getString(R.string.quick_reboot)) {
                                appScope.launch { reportAdb(SystemTools.reboot(this@MainActivity)) { getString(R.string.quick_sent) } }
                            }
                        },
                        MenuOption(stringResource(R.string.power_shutdown)) {
                            confirm = ConfirmRequest(getString(R.string.power_shutdown_ask), getString(R.string.power_shutdown)) {
                                poweringOff = true
                            }
                        },
                        MenuOption(stringResource(R.string.power_off)) {
                            // Android only opens its power dialog for an accessibility service.
                            if (!SystemService.showPowerDialog()) {
                                Toast.makeText(this@MainActivity, needsService, Toast.LENGTH_LONG).show()
                                openSystemScreen(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            }
                        },
                        MenuOption(stringResource(R.string.power_sleep_settings)) { openSystemScreen(Settings.ACTION_DREAM_SETTINGS) },
                        MenuOption(stringResource(R.string.power_display_settings)) { openSystemScreen(Settings.ACTION_DISPLAY_SETTINGS) },
                    ),
                    onDismiss = { showPower = false },
                )
            }

            iconPickerApp?.let { app ->
                IconPicker(
                    app = app,
                    packs = iconPacks,
                    loadContents = { IconPacks.contents(this@MainActivity, it) },
                    loadIcon = { pack, name -> IconPacks.bitmap(this@MainActivity, pack, name, 160) },
                    onPick = { pack, name ->
                        IconOverrides(this@MainActivity).set(app.key, pack.packageName, name)
                        iconsVersion++
                        iconPickerApp = null
                    },
                    onReset = {
                        IconOverrides(this@MainActivity).clear(app.key)
                        iconsVersion++
                        iconPickerApp = null
                    },
                    onDismiss = { iconPickerApp = null },
                )
            }

            if (showWallpaperCategoryPicker) {
                val allIds = remember(catalog) { catalog.categories.map { it.id } }
                val selected = remember(wallpaperCategory, allIds) { WallpaperCatalog.selectedIds(wallpaperCategory, allIds) }
                fun applyCategories(next: Set<String>) {
                    val value = if (next.isEmpty()) WallpaperCatalog.OFF else next.joinToString(",")
                    wallpaperCategory = value
                    settings.wallpaperCategory = value
                }
                WallpaperCategoryPicker(
                    categories = catalog.categories,
                    selected = selected,
                    onToggle = { id, on -> applyCategories(if (on) selected + id else selected - id) },
                    onSelectAll = { applyCategories(allIds.toSet()) },
                    onSelectNone = { applyCategories(emptySet()) },
                    onDismiss = { showWallpaperCategoryPicker = false },
                )
            }

            // Drawn last, over everything else, so no dialog or menu is left showing through it.
            if (poweringOff) {
                PowerOffAnimation(
                    onFinished = {
                        appScope.launch {
                            // On success the box is on its way down and this never gets to matter;
                            // on failure the screen needs to come back so the toast makes sense.
                            reportAdb(SystemTools.shutdown(this@MainActivity)) { getString(R.string.quick_sent) }
                            poweringOff = false
                        }
                    },
                )
            }
            }
        }

        if (Build.VERSION.SDK_INT >= 24 && settings.performanceMode == LauncherSettings.PERF_AUTO && !Performance.lite) startFrameMonitor()
    }

    /** Learns from the frames the launcher really draws whether this box can keep up (see [FrameMonitor]). */
    @RequiresApi(24)
    private fun startFrameMonitor() {
        val monitor = FrameMonitor {
            runOnUiThread {
                settings.autoLite = true
                Performance.refresh(this, settings)
                Toast.makeText(this, getString(R.string.performance_detected), Toast.LENGTH_LONG).show()
            }
        }
        frameMonitor = monitor
        window.addOnFrameMetricsAvailableListener(
            Window.OnFrameMetricsAvailableListener { _, metrics, _ -> monitor.onFrame(metrics) },
            Handler(Looper.getMainLooper()),
        )
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        resumeSignal++
        // Back from an app: the opening animation plays in reverse and focus returns to its tile.
        launchExpanded = false
        if (pendingRestore) {
            pendingRestore = false
            restoreSignal++
        }
    }

    override fun onPause() {
        inForeground = false
        screensaverShowing = false
        super.onPause()
    }

    override fun onDestroy() {
        remoteServer.stop()
        boxDiscovery.stop()
        appScope.cancel()
        super.onDestroy()
    }

    /** Any key wakes the screensaver (and is otherwise swallowed); any key also restarts the idle wait. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInputAt = System.currentTimeMillis()
        KeyLog.record("launcher", event)
        if (event.action == KeyEvent.ACTION_DOWN) TileMotion.note(event.keyCode)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            volumeKeysSeen++
            lastVolumeKey = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
            lastVolumeKeyAt = lastInputAt
        }
        if (screensaverShowing) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                wakeKeyCode = event.keyCode
                screensaverShowing = false
            }
            return true
        }
        if (event.keyCode == wakeKeyCode) {
            if (event.action == KeyEvent.ACTION_UP) wakeKeyCode = -1
            return true
        }
        // "Teach the button" mode: the next unusual key becomes the quick settings button.
        if (KeyLearner.offer(event, settings)) return true
        // The Menu button (or the one the user taught) opens the quick settings.
        val taught = settings.quickKey
        if (event.keyCode in QUICK_KEYS || (taught != 0 && event.keyCode == taught)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && launchRequest == null) showQuick = !showQuick
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (wakeScreensaver()) return true
        // A fallback for touchpad remotes whose driver reports swipes as plain touch events
        // rather than through dispatchGenericMotionEvent.
        trackTouchpadGesture(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Mice and air-mouse remotes: moving them counts as being there. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (wakeScreensaver()) return true
        trackTouchpadGesture(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    /** True when the screensaver was up (the event is then used up waking it). */
    private fun wakeScreensaver(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInputAt > 1000) lastInputAt = now // not on every mouse move
        if (!screensaverShowing) return false
        screensaverShowing = false
        return true
    }

    // ---- Touchpad remotes --------------------------------------------------------------------
    // Some TV remotes (including some that pair with Mi Box hardware) have a small touchpad and
    // report swipes/taps as MotionEvents instead of D-pad KeyEvents. This tracks one gesture at a
    // time (down -> moves -> up) and, once it ends, replays it as the matching D-pad KeyEvent(s)
    // through the normal dispatchKeyEvent path below, so it reuses the very same navigation,
    // focus-movement and OK handling a physical D-pad press already goes through. Purely additive:
    // it never consumes the original event and is a no-op (and never throws) for any other device.

    private var touchpadDown = false
    private var touchpadLastX = 0f
    private var touchpadLastY = 0f
    private var touchpadDx = 0f
    private var touchpadDy = 0f

    /** How far a gesture must travel before it counts as a swipe rather than a stray touch. */
    private val touchpadSwipeMinPx: Float by lazy { 24f * resources.displayMetrics.density }

    /** Roughly one apps-grid tile's width: a horizontal swipe this long moves focus one step further. */
    private val touchpadTileStepPx: Float by lazy { resources.displayMetrics.widthPixels / 6f }

    private fun isTouchpadSource(source: Int): Boolean =
        (source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD ||
            (source and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL

    /** Feeds [event]'s deltas into the current gesture, if it comes from a touchpad-like source. */
    private fun trackTouchpadGesture(event: MotionEvent) {
        try {
            if (!isTouchpadSource(event.source)) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchpadDown = true
                    touchpadLastX = event.x
                    touchpadLastY = event.y
                    touchpadDx = 0f
                    touchpadDy = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!touchpadDown) {
                        // Some drivers move straight into ACTION_MOVE without an ACTION_DOWN.
                        touchpadDown = true
                        touchpadLastX = event.x
                        touchpadLastY = event.y
                        return
                    }
                    touchpadDx += event.x - touchpadLastX
                    touchpadDy += event.y - touchpadLastY
                    touchpadLastX = event.x
                    touchpadLastY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    if (touchpadDown) finishTouchpadGesture()
                    touchpadDown = false
                }
                MotionEvent.ACTION_CANCEL -> touchpadDown = false
            }
        } catch (_: Exception) {
            touchpadDown = false
        }
    }

    /** The gesture just ended: a short one is a tap (OK), a longer one a swipe (page or focus move). */
    private fun finishTouchpadGesture() {
        val dx = touchpadDx
        val dy = touchpadDy
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < touchpadSwipeMinPx) {
            sendDpadKey(KeyEvent.KEYCODE_DPAD_CENTER)
            return
        }
        val across = abs(dx)
        val down = abs(dy)
        when {
            // Mostly horizontal: nudge focus left/right, a step per tile-width of swipe distance.
            across > down * 1.5f -> {
                val steps = (across / touchpadTileStepPx).roundToInt().coerceIn(1, 2)
                val code = if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                repeat(steps) { sendDpadKey(code) }
            }
            // Mostly vertical: the same single step Up/Down already takes between pages.
            down > across * 1.5f -> sendDpadKey(if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
            else -> Unit // too diagonal to call left/right vs up/down; ignore it
        }
    }

    /** Feeds a synthetic D-pad press through [dispatchKeyEvent] - the same path a real remote key takes. */
    private fun sendDpadKey(keyCode: Int) {
        val now = System.currentTimeMillis()
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /** Home pressed while the launcher is already on screen: back to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screensaverShowing = false
        showQuick = false
        pendingRestore = false
        page = PAGE_HOME
        resetSignal++
        handleIntent(intent)
    }

    private fun swapped(list: List<String>, from: Int, to: Int): List<String> =
        list.toMutableList().also { val moved = it[from]; it[from] = it[to]; it[to] = moved }

    private fun startApp(app: AppInfo) {
        val intent = try {
            if (app.intentUri != null) {
                // A "continue watching" title: the app's own deep link opens that very film or episode.
                Intent.parseUri(app.intentUri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(app.packageName, app.className)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
            }
        } catch (_: Exception) {
            launchExpanded = false
            return
        }
        try {
            // The screen is already black from the opening animation, so a plain fade hands over cleanly.
            val options = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
            pendingRestore = true
            // If the app never actually takes over the screen, don't stay stuck on black.
            window.decorView.postDelayed({ if (inForeground) launchExpanded = false }, 2500)
        } catch (_: Exception) {
            launchExpanded = false // uninstalled since the list was loaded; the next resume refreshes it
        }
    }

    /**
     * `adb shell am start -n com.liquidglass.tvlauncher/.MainActivity --es ical_url "<link>"` connects a
     * Google calendar (an empty link disconnects it); tools/tv_adb.py does this for you.
     */
    private fun handleIntent(intent: Intent?) {
        // From the accessibility service, when the taught remote button is pressed in any app.
        if (intent?.getBooleanExtra("open_quick", false) == true) showQuick = true
        if (intent?.hasExtra("ical_url") == true) {
            settings.icalUrl = intent.getStringExtra("ical_url")?.trim()?.takeIf { it.isNotEmpty() }
            calendarTick++
        }
        // `--es weather_city "Gdańsk"`: a city that is looked up by name and then used for the weather.
        intent?.getStringExtra("weather_city")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            settings.pendingWeatherCity = it
            weatherTick++
        }
    }

    // ---- adb tools: results and messages -----------------------------------------------------------

    /** Play or pause Spotify (OK on its widget). */
    private fun spotifyPlayPause() {
        appScope.launch {
            val playing = SpotifyHub.playback?.playing == true
            SpotifyHub.call { client -> if (playing) client.pause() else client.play() }
            delay(400)
            SpotifyHub.refreshPlayback()
        }
    }

    /**
     * The Spotify device that is this box. It only shows up while the Spotify app runs on the box, so when it is
     * not there the app is started, waited for, and the launcher brought back to the front.
     */
    private suspend fun spotifyDevice(): String? {
        SpotifyHub.deviceForThisBox(this)?.let { return it.id }
        val launch = packageManager.getLaunchIntentForPackage("com.spotify.tv.android")
            ?: packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (launch == null) {
            toast("Zainstaluj aplikację Spotify na Boksie")
            return null
        }
        toast("Uruchamiam Spotify na Boksie…")
        withContext(Dispatchers.Main) { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        var found: String? = null
        repeat(10) {
            if (found == null) {
                delay(1_500)
                found = SpotifyHub.deviceForThisBox(this)?.id
            }
        }
        withContext(Dispatchers.Main) {
            startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        return found
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /** What to tell the user when an adb tool could not run. */
    private fun adbProblem(result: AdbResult<*>): String = when (result) {
        AdbResult.NeedsApproval -> getString(R.string.update_silent_needs_approval)
        AdbResult.NoAdb -> getString(R.string.update_silent_no_adb)
        is AdbResult.Failed -> getString(R.string.adb_error, result.reason)
        is AdbResult.Ok -> ""
    }

    /** Shows [ok] of a successful result, or why it didn't work, as a toast. */
    private fun <T> reportAdb(result: AdbResult<T>, ok: (T) -> String) {
        toast(if (result is AdbResult.Ok) ok(result.value) else adbProblem(result))
    }

    /** "Switch off <app>?": runs the switch-off through adb when confirmed. */
    private fun disableRequest(packageName: String, label: String, done: () -> Unit): ConfirmRequest {
        val home = SystemTools.isHomeApp(this, packageName)
        return ConfirmRequest(
            title = getString(if (home) R.string.tools_disable_home_ask else R.string.tools_disable_ask, label),
            label = getString(R.string.tools_disable_do),
        ) {
            appScope.launch {
                val result = SystemTools.setAppEnabled(this@MainActivity, packageName, false)
                if (result is AdbResult.Ok) {
                    toast(getString(R.string.tools_disabled, label))
                    done()
                } else {
                    toast(adbProblem(result))
                }
            }
        }
    }

    /** The widget's "allow access to notifications": directly when the permission was granted over adb, otherwise Android's screen. */
    private fun grantNotificationAccess() {
        if (!SystemTools.enableNotificationAccess(this)) {
            openSystemScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    // ---- The phone remote ------------------------------------------------------------------------------

    /** Keys that open the quick settings out of the box; the user can teach another one in the settings. */
    private val QUICK_KEYS = setOf(
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_TV_CONTENTS_MENU, KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU,
    )

    private val MAX_USER_PHOTOS = 60

    private val keyCodes = mapOf(
        "UP" to 19, "DOWN" to 20, "LEFT" to 21, "RIGHT" to 22, "OK" to 23, "BACK" to 4, "HOME" to 3, "PLAYPAUSE" to 85,
        // Added for the Pilot's voice control (next track, menu); same "key" action, no new when-branch needed.
        "NEXT" to 87, "MENU" to 82,
    )

    /** One frame of the phone's screen (a JPEG). Runs on a server thread; "stop" tells the phone to quit sharing. */
    private fun receiveFrame(jpeg: ByteArray): String {
        val now = System.currentTimeMillis()
        if (now < mirrorRefusedUntil) return "stop"
        val options = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return "ok"
        val first = mirrorFrame == null
        runOnUiThread {
            mirrorFrame = bitmap.asImageBitmap()
            mirrorAt = now
            screensaverShowing = false
            if (first && !inForeground) {
                // Sharing was started while another app is in front: bring the launcher up to show it.
                try {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                } catch (_: Exception) {
                }
            }
        }
        return "ok"
    }

    /**
     * A cursor delta from the phone's gyroscope (see /cursor in RemoteServer): added to an on-screen
     * position that Compose alone moves — adb is only invoked at the moment of a tap, with that
     * position as the coordinates, which is what keeps the dot smooth despite adb's own latency.
     * Runs on a server thread, much more often than [handleRemote].
     *
     * The point of a remote mouse is pointing at whatever app is already in front (Netflix, a game…),
     * so this never switches to the launcher itself: the dot is drawn by [SystemService], the same
     * accessibility-service overlay the volume bar uses, which sits above every app without taking
     * focus. Only without that service configured does it fall back to the launcher's own, in-window
     * dot ([CursorOverlay]), which can only show while the launcher itself is the one on screen.
     */
    private fun receiveCursor(dx: Float, dy: Float, tap: Boolean): String {
        val now = System.currentTimeMillis()
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - 1).toFloat()
        val maxY = (metrics.heightPixels - 1).toFloat()
        val base = cursorScreenPos ?: Offset(maxX / 2f, maxY / 2f)
        val next = Offset((base.x + dx).coerceIn(0f, maxX), (base.y + dy).coerceIn(0f, maxY))
        cursorScreenPos = next
        val shownByService = SystemService.showCursor(next.x.roundToInt(), next.y.roundToInt())
        runOnUiThread {
            // Only used as the fallback dot (see above) once the service is drawing its own.
            cursorPos = if (shownByService) null else next
            cursorAt = now
            cursorTick++
            screensaverShowing = false
        }
        if (tap) {
            // adb reaches whatever app is in front, same as the D-pad keys; only the tap itself pays that latency.
            runBlocking { SelfAdb.shell(this@MainActivity, "input tap ${next.x.roundToInt()} ${next.y.roundToInt()}") }
        }
        return "ok"
    }

    /** A photo sent from the phone: checked, copied to the launcher's own folder and shown. Runs on a server thread. */
    private fun receivePhoto(name: String, file: java.io.File): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension == "mp4") return receiveVideo(file)
        if (extension !in listOf("jpg", "jpeg", "png", "webp")) return "To nie wygląda na zdjęcie (jpg, png albo webp)."
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "Nie udało się odczytać tego zdjęcia."
        val folder = WallpaperRepository.userDir(this).apply { mkdirs() }
        if ((folder.listFiles()?.size ?: 0) >= MAX_USER_PHOTOS) return "Za dużo zdjęć (limit $MAX_USER_PHOTOS): najpierw usuń stare."
        val safe = name.substringBeforeLast('.').filter { it.isLetterOrDigit() || it in "-_" }.take(40).ifEmpty { "zdjecie" }
        val target = java.io.File(folder, "${System.currentTimeMillis()}-$safe.$extension")
        file.copyTo(target, overwrite = true)
        runOnUiThread {
            userPhotosTick++
            forcedCategory = WallpaperRepository.USER_CATEGORY
        }
        return "Zdjęcie dodane i ustawione jako tapeta"
    }

    /** Saves the clip, pulls its first frame as a still picture (used everywhere a video can't play:
     * glass blur, screensaver, the Lite edition) and turns animated wallpaper on. */
    private fun receiveVideo(file: java.io.File): String {
        val target = WallpaperRepository.videoFile(this)
        file.copyTo(target, overwrite = true)
        val retriever = android.media.MediaMetadataRetriever()
        val poster = try {
            retriever.setDataSource(target.path)
            retriever.getFrameAtTime(0)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
        if (poster == null) {
            target.delete()
            return "Nie udało się odczytać tego wideo (nieobsługiwany format?)."
        }
        val folder = WallpaperRepository.userDir(this).apply { mkdirs() }
        java.io.File(folder, WallpaperRepository.VIDEO_POSTER_NAME).outputStream().use {
            poster.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
        }
        settings.animatedWallpaper = true
        runOnUiThread {
            userPhotosTick++
            videoWallpaperTick++
            forcedCategory = WallpaperRepository.USER_CATEGORY
        }
        return "Animowana tapeta ustawiona"
    }

    /** Runs one button of the phone's web page; called on a server thread, returns the line the page shows. */
    private fun handleRemote(action: String, value: String): String {
        fun adb(result: AdbResult<*>, ok: String) = if (result is AdbResult.Ok) ok else adbProblem(result)
        return when (action) {
            "key" -> {
                val code = keyCodes[value] ?: return "Nieznany przycisk"
                // Through adb the key reaches whatever app is in front, not just the launcher.
                adb(runBlocking { SelfAdb.shell(this@MainActivity, "input keyevent $code") }, "OK")
            }
            "vol_up", "vol_down", "mute" -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = when (action) {
                    "vol_up" -> AudioManager.ADJUST_RAISE
                    "vol_down" -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                runOnUiThread { SystemService.testOverlay(now * 100 / max, audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
                "Głośność: ${now * 100 / max}%"
            }
            "spotify_login" -> {
                if (SpotifyHub.login(value)) {
                    // The widget is switched on the first time, so the new player is found right away.
                    runOnUiThread {
                        if (WidgetType.SPOTIFY.key !in settings.enabledWidgets) settings.enabledWidgets = settings.enabledWidgets + WidgetType.SPOTIFY.key
                        widgetsTick++
                    }
                    "Spotify połączony z launcherem"
                } else {
                    "Nie udało się odczytać logowania do Spotify"
                }
            }
            "spotify_open" -> {
                if (!SpotifyHub.connected) return "Najpierw zaloguj się do Spotify w aplikacji Pilot"
                runOnUiThread { showSpotify = true }
                "Odtwarzacz Spotify na TV"
            }
            "spotify_logout" -> {
                SpotifyHub.logout()
                "Spotify odłączony od launchera"
            }
            "lastfm_login" -> {
                if (LastFmHub.login(value)) {
                    "Last.fm połączony z launcherem"
                } else {
                    "Nie udało się odczytać logowania do Last.fm"
                }
            }
            "ping" -> "Liquid TV ${Updater.currentVersionName(this)}: połączono"
            "mirror_stop" -> {
                runOnUiThread { mirrorFrame = null }
                "ok"
            }
            "text" -> {
                // Typed into whatever field is focused on the TV. Android's `input text` only knows ASCII.
                val clean = value.take(200)
                if (clean.isBlank()) return "Wpisz tekst"
                val quoted = "'" + clean.replace("'", "'\\''").replace(" ", "%s") + "'"
                adb(runBlocking { SelfAdb.shell(this@MainActivity, "input text $quoted") }, "Wpisano na TV (tylko znaki ASCII)")
            }
            "standby" -> adb(runBlocking { SystemTools.standby(this@MainActivity) }, "Box idzie spać")
            "reboot" -> adb(runBlocking { SystemTools.reboot(this@MainActivity) }, "Restartuję Box")
            "tv_on" -> adb(runBlocking { SystemTools.tvOn(this@MainActivity) }, "Wysłano do telewizora (CEC)")
            "tv_power" -> adb(runBlocking { SystemTools.tvPowerToggle(this@MainActivity) }, "Wysłano do telewizora (CEC)")
            "memory" -> runBlocking { SystemTools.freeMemory(this@MainActivity) }.let { adb(it, (it as? AdbResult.Ok)?.value ?: "") }
            "open" -> {
                val uri = Uri.parse(value.trim())
                if (uri.scheme != "http" && uri.scheme != "https") return "Podaj adres zaczynający się od https://"
                val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(packageManager) == null) return "Ten Box nie ma aplikacji, która otworzy ten adres"
                runOnUiThread {
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }
                "Wysłano na TV"
            }
            "ical" -> {
                val link = value.trim()
                if (link.isNotEmpty() && !link.startsWith("http")) return "To nie wygląda na adres iCal"
                settings.icalUrl = link.takeIf { it.isNotEmpty() }
                runOnUiThread { calendarTick++ }
                if (link.isEmpty()) "Kalendarz odłączony" else "Kalendarz połączony"
            }
            "city" -> {
                if (value.isBlank()) return "Wpisz nazwę miasta"
                settings.pendingWeatherCity = value.trim()
                runOnUiThread { weatherTick++ }
                "Szukam: ${value.trim()}"
            }
            "wallpaper" -> {
                runOnUiThread { skipSignal++ }
                "Następna tapeta"
            }
            "clear_photos" -> {
                WallpaperRepository.userDir(this).listFiles()?.forEach { it.delete() }
                runOnUiThread {
                    userPhotosTick++
                    if (settings.wallpaperCategory == WallpaperRepository.USER_CATEGORY) forcedCategory = WallpaperCatalog.ALL
                }
                "Usunięto własne zdjęcia"
            }
            "clear_video" -> {
                WallpaperRepository.videoFile(this).delete()
                java.io.File(WallpaperRepository.userDir(this), WallpaperRepository.VIDEO_POSTER_NAME).delete()
                settings.animatedWallpaper = false
                runOnUiThread {
                    userPhotosTick++
                    videoWallpaperTick++
                }
                "Wyłączono animowaną tapetę"
            }
            "screensaver" -> {
                runOnUiThread { screensaverShowing = true }
                "Wygaszacz"
            }
            "perf" -> {
                val next = (settings.performanceMode + 1) % 3
                settings.performanceMode = next
                settings.autoLite = false
                runOnUiThread {
                    Performance.refresh(this, settings)
                    performanceTick++
                }
                when (next) {
                    LauncherSettings.PERF_LITE -> "Tryb wydajności: zawsze oszczędnie"
                    LauncherSettings.PERF_FULL -> "Tryb wydajności: pełne efekty"
                    else -> "Tryb wydajności: automatyczny"
                }
            }
            else -> "Nieznane polecenie"
        }
    }

    // ---- Volume diagnostics -------------------------------------------------------------------------------

    /** Volume keys the launcher itself received (only while it is in front), for the diagnostics. */
    private var volumeKeysSeen = 0
    private var lastVolumeKey = ""
    private var lastVolumeKeyAt = 0L

    /** Changes the music volume the way a key would, and shows the bar: a test of the whole chain. */
    private fun testVolume(direction: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        SystemService.testOverlay(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max, audio.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    /** Everything that decides whether volume works, in plain text. */
    private fun volumeDiagnostics(): String {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        fun at(millis: Long) = if (millis == 0L) "—" else format.format(java.util.Date(millis))
        fun yes(value: Boolean) = if (value) "tak" else "nie"
        val outputs = try {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString(", ") {
                when (it.type) {
                    android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                    android.media.AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "głośnik"
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                    android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                    android.media.AudioDeviceInfo.TYPE_LINE_ANALOG, android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line"
                    else -> "typ ${it.type}"
                }
            }
        } catch (_: Throwable) {
            "?"
        }
        fun global(name: String) = try {
            Settings.Global.getString(contentResolver, name) ?: "brak"
        } catch (_: Throwable) {
            "?"
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val mode = when (settings.volumeMode) {
            LauncherSettings.VOLUME_OFF -> "wyłączony"
            LauncherSettings.VOLUME_REPLACE -> "nasz pasek, systemowy zamykany"
            else -> "nasz pasek, systemowy zostaje"
        }
        return buildString {
            appendLine("Usługa dostępności włączona w ustawieniach: ${yes(SystemTools.accessibilityOn(this@MainActivity))}")
            appendLine("Usługa działa (połączona):                  ${yes(SystemService.connected)}")
            appendLine("Tryb paska Liquid TV:                       $mode")
            appendLine()
            appendLine("Klawisze głośności widziane przez usługę:   ${SystemService.keysSeen} (ostatni: ${SystemService.lastKey.ifEmpty { "—" }} o ${at(SystemService.lastKeyAt)})")
            appendLine("Klawisze widziane przez sam launcher:       $volumeKeysSeen (ostatni: ${lastVolumeKey.ifEmpty { "—" }} o ${at(lastVolumeKeyAt)})")
            appendLine("Zmiany głośności wykryte (sygnał/ustawienia): ${SystemService.changesSeen} (ostatnia: ${SystemService.lastChange.ifEmpty { "—" }} o ${at(SystemService.lastChangeAt)})")
            appendLine("Dokąd idzie dźwięk: ${com.liquidglass.tvlauncher.data.VolumeRouting.describe(audio)}")
            appendLine("Poziom głośności Boxa uznany za prawdziwy: ${yes(settings.volumeLevelReal)}; przejmowanie przycisków zepsute: ${yes(settings.volumeConsumeBroken)} (nieudane: ${SystemService.ignoredAdjusts})")
            appendLine()
            appendLine("Ostatnie przyciski (nowe na górze), z nazwą urządzenia:")
            appendLine(KeyLog.dump())
            appendLine()
            appendLine("Głośność multimediów: ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} z $max, wyciszenie: ${yes(audio.isStreamMute(AudioManager.STREAM_MUSIC))}")
            appendLine("Stała głośność (telewizor steruje): ${yes(audio.isVolumeFixed)}")
            appendLine("Wyjścia audio: $outputs")
            appendLine("HDMI-CEC włączone: ${global("hdmi_control_enabled")}")
            appendLine()
            appendLine("Jak to czytać: naciśnij przycisk głośności na pilocie (albo zmień głośność na głośniku Bluetooth) i wybierz Odśwież.")
            appendLine("- Przez HDMI głośnością rządzi telewizor (CEC), a Box widzi tylko przyciski: pasek pokazuje wtedy kierunek zmiany (▲ ▼), nie poziom.")
            appendLine("- Przy głośniku Bluetooth pasek pokazuje prawdziwy poziom, bo rośnie licznik zmian głośności.")
            appendLine("- Przycisk jest na liście przycisków, ale licznik „klawisze widziane przez usługę” nie rośnie: to inny przycisk niż głośność; podaj nazwę z listy.")
            appendLine("- Przycisku nie ma na liście: pilot nie wysyła go do Boxa (np. używa podczerwieni do telewizora).")
            appendLine("- Usługa nie działa: Ustawienia → Narzędzia → Skonfiguruj adb albo włącz ją w ustawieniach dostępności.")
        }
    }

    /** One of Android's own settings screens, or the main settings when this box doesn't have it. */
    private fun openSystemScreen(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            openSettings()
        }
    }

    /** Whether the box is online, and how, for the settings. */
    private fun networkStatus(): String {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = try {
            manager?.getNetworkCapabilities(manager.activeNetwork)
        } catch (_: Exception) {
            null
        }
        return getString(
            when {
                capabilities == null -> R.string.net_none
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.string.net_wifi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.net_ethernet
                else -> R.string.net_other
            },
        )
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
        }
    }

    /** Accounts screen if the device has one, the main settings otherwise. */
    private fun openAccountSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
        } catch (_: Exception) {
            openSettings()
        }
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

/** A question with one action behind it ("Restart the box?"), shown as a small menu with "Cancel". */
private class ConfirmRequest(val title: String, val label: String, val action: () -> Unit)
