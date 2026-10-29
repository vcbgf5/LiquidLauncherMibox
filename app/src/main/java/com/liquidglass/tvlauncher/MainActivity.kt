package com.liquidglass.tvlauncher

import android.Manifest
import android.accounts.AccountManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.liquidglass.tvlauncher.data.AccountProvider
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.AppRepository
import com.liquidglass.tvlauncher.data.FavoritesStore
import com.liquidglass.tvlauncher.data.IconOverrides
import com.liquidglass.tvlauncher.data.IconPack
import com.liquidglass.tvlauncher.data.IconPacks
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.PriorityApps
import com.liquidglass.tvlauncher.data.SystemStatus
import com.liquidglass.tvlauncher.data.UpdateInfo
import com.liquidglass.tvlauncher.data.Updater
import com.liquidglass.tvlauncher.data.Agenda
import com.liquidglass.tvlauncher.data.AgendaRepository
import com.liquidglass.tvlauncher.data.WidgetData
import com.liquidglass.tvlauncher.data.WidgetDataReader
import com.liquidglass.tvlauncher.data.WidgetType
import com.liquidglass.tvlauncher.data.WallpaperCatalog
import com.liquidglass.tvlauncher.data.WallpaperImage
import com.liquidglass.tvlauncher.data.WallpaperRepository
import com.liquidglass.tvlauncher.ui.AppMenu
import com.liquidglass.tvlauncher.ui.HomeScreen
import com.liquidglass.tvlauncher.ui.IconPicker
import com.liquidglass.tvlauncher.ui.LocalTileLighting
import com.liquidglass.tvlauncher.ui.PAGE_HOME
import com.liquidglass.tvlauncher.ui.PAGE_INFO
import com.liquidglass.tvlauncher.ui.LaunchOverlay
import com.liquidglass.tvlauncher.ui.LaunchRequest
import com.liquidglass.tvlauncher.ui.MenuOption
import com.liquidglass.tvlauncher.ui.Screensaver
import com.liquidglass.tvlauncher.ui.SettingsDialog
import com.liquidglass.tvlauncher.ui.WallpaperState

class MainActivity : ComponentActivity() {

    /** Bumped on every onResume so installs/uninstalls made elsewhere show up. */
    private var resumeSignal by mutableIntStateOf(0)

    /** Bumped on Home/Back: focus returns to the first favorite. */
    private var resetSignal by mutableIntStateOf(0)

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

    /** Bumped to fetch the calendar again (a new link, or OK on the events widget). */
    private var calendarTick by mutableIntStateOf(0)

    /** Bumped by "next wallpaper" in the settings. */
    private var skipSignal by mutableIntStateOf(0)

    // Bookkeeping for the rotation: what it last did, so coming back from an app doesn't reshuffle.
    private var lastRotationKey = ""
    private var lastWallpaperAt = 0L

    private lateinit var store: FavoritesStore
    private lateinit var settings: LauncherSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FavoritesStore(this)
        settings = LauncherSettings(this)
        handleIntent(intent)

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var favoriteKeys by remember { mutableStateOf(store.load()) }
            var menuApp by remember { mutableStateOf<AppInfo?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            var dockTransparency by remember { mutableIntStateOf(settings.dockTransparency) }
            var dockSize by remember { mutableIntStateOf(settings.dockSize) }
            var screensaverMinutes by remember { mutableIntStateOf(settings.screensaverMinutes) }
            var bigClock by remember { mutableStateOf(settings.bigClock) }
            var tileLighting by remember { mutableStateOf(settings.tileLighting) }
            var clockContent by remember { mutableIntStateOf(settings.clockContent) }
            var clockPosition by remember { mutableIntStateOf(settings.clockPosition) }
            var statusIcons by remember { mutableStateOf(settings.statusIcons) }
            var systemStatus by remember { mutableStateOf(SystemStatus.UNKNOWN) }
            var customVolume by remember { mutableStateOf(settings.customVolume) }
            var accessibilityOn by remember { mutableStateOf(false) }
            LaunchedEffect(showSettings, resumeSignal) { accessibilityOn = accessibilityServiceOn() }
            val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarTick++ }
            var showPower by remember { mutableStateOf(false) }
            var iconPickerApp by remember { mutableStateOf<AppInfo?>(null) }
            var iconsVersion by remember { mutableIntStateOf(0) }
            var iconPacks by remember { mutableStateOf<List<IconPack>>(emptyList()) }
            var accountName by remember { mutableStateOf(AccountProvider.googleAccountName(this@MainActivity)) }

            val wallpaper = remember {
                WallpaperState(WallpaperImage(
                    ImageBitmap.imageResource(resources, R.drawable.bg_hero),
                    ImageBitmap.imageResource(resources, R.drawable.bg_hero_blur),
                ))
            }
            var catalog by remember { mutableStateOf(WallpaperCatalog.EMPTY) }
            var wallpaperCategory by remember { mutableStateOf(settings.wallpaperCategory) }
            var wallpaperInterval by remember { mutableIntStateOf(settings.wallpaperIntervalMinutes) }
            var photoCredit by remember { mutableStateOf<String?>(null) }
            var widgetKeys by remember { mutableStateOf(settings.enabledWidgets) }
            var widgetData by remember { mutableStateOf(WidgetData.EMPTY) }
            var agenda by remember { mutableStateOf(Agenda.EMPTY) }

            // In-app updates (see Updater): what the check found, and where the download is.
            val scope = rememberCoroutineScope()
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var updateStatus by remember { mutableStateOf(UpdateStatus.IDLE) }
            var downloadPercent by remember { mutableIntStateOf(0) }
            var autoCheck by remember { mutableStateOf(settings.autoCheckUpdates) }

            // [automatic] = nobody pressed anything: stay quiet about errors, only the red dot
            // shows. Downloading and handing the APK to the installer is [installUpdate].
            suspend fun installUpdate(automatic: Boolean) {
                val info = updateInfo ?: return
                if (updateStatus == UpdateStatus.DOWNLOADING) return
                updateStatus = UpdateStatus.DOWNLOADING
                downloadPercent = 0
                val file = Updater.download(this@MainActivity, info) { downloadPercent = it }
                updateStatus = if (file == null) {
                    UpdateStatus.DOWNLOAD_FAILED
                } else when (Updater.install(this@MainActivity, file, askPermission = !automatic)) {
                    Updater.InstallStart.STARTED -> UpdateStatus.INSTALLING
                    Updater.InstallStart.NEEDS_PERMISSION -> UpdateStatus.NEEDS_PERMISSION
                    Updater.InstallStart.WRONG_SIGNATURE -> UpdateStatus.WRONG_SIGNATURE
                    Updater.InstallStart.FAILED -> UpdateStatus.DOWNLOAD_FAILED
                }
            }

            // Pings GitHub for the newest version.json. A newer one is installed right away when
            // [thenInstall] says so (the check button, or the automatic path with the switch on).
            suspend fun checkForUpdates(automatic: Boolean, thenInstall: Boolean) {
                if (!automatic) updateStatus = UpdateStatus.CHECKING
                when (val result = Updater.check(this@MainActivity)) {
                    is Updater.CheckResult.Newer -> {
                        updateInfo = result.info
                        updateStatus = UpdateStatus.AVAILABLE
                        // The automatic path offers each version once; a refused install is not repeated.
                        val alreadyOffered = automatic && settings.lastAutoInstalled == result.info.versionCode
                        if (thenInstall && !alreadyOffered) {
                            if (automatic) settings.lastAutoInstalled = result.info.versionCode
                            installUpdate(automatic)
                        }
                    }
                    Updater.CheckResult.UpToDate -> {
                        updateInfo = null
                        if (!automatic) updateStatus = UpdateStatus.UP_TO_DATE
                    }
                    Updater.CheckResult.Failed -> if (!automatic) updateStatus = UpdateStatus.CHECK_FAILED
                }
                settings.lastUpdateCheck = System.currentTimeMillis()
            }

            // Automatic: whenever the launcher comes on screen, at most every 6 hours (one tiny
            // request), and with the switch on a newer version is downloaded and offered to Android.
            LaunchedEffect(inForeground, autoCheck) {
                if (inForeground && autoCheck && System.currentTimeMillis() - settings.lastUpdateCheck > 6 * 3_600_000L) {
                    checkForUpdates(automatic = true, thenInstall = true)
                }
            }

            val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { name ->
                    AccountProvider.save(this@MainActivity, name)
                    accountName = name
                }
            }

            // Screensaver: starts after the chosen time without any key press, while the launcher
            // is on screen and nothing (a dialog, an app opening) is going on. Every key press
            // (see dispatchKeyEvent) restarts the wait.
            LaunchedEffect(inForeground, screensaverMinutes, showSettings, menuApp, iconPickerApp, launchRequest) {
                if (!inForeground || screensaverMinutes <= 0 || showSettings || menuApp != null || iconPickerApp != null || showPower || launchRequest != null) {
                    return@LaunchedEffect
                }
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
                if (catalog.isEmpty) catalog = WallpaperRepository.catalog(this@MainActivity)
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

            fun saveKeys(updated: List<String>) {
                favoriteKeys = updated
                store.save(updated)
            }

            // Back never leaves the launcher: from the info or app pages it returns to the home
            // screen, and from the home screen it just returns focus to the dock.
            BackHandler {
                menuApp = null
                if (page != PAGE_HOME) page = PAGE_HOME else resetSignal++
            }

            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalTileLighting provides tileLighting) {
                HomeScreen(
                    favorites = favorites,
                    allApps = apps,
                    widgets = widgets,
                    widgetData = widgetData,
                    agenda = agenda,
                    onWidget = { type ->
                        when (type) {
                            WidgetType.AGENDA -> calendarTick++
                            WidgetType.NETWORK -> openSystemScreen(Settings.ACTION_WIFI_SETTINGS)
                            WidgetType.BLUETOOTH -> openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
                            WidgetType.SYSTEM -> openSystemScreen(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                            WidgetType.CALENDAR -> Unit
                        }
                    },
                    page = page,
                    onPage = { page = it },
                    resetSignal = resetSignal,
                    focusPaused = showSettings || menuApp != null || iconPickerApp != null || showPower || launchRequest != null,
                    accountName = accountName,
                    updateAvailable = updateInfo != null,
                    wallpaper = wallpaper,
                    dockTransparency = dockTransparency / 100f,
                    dockSize = dockSize / 100f,
                    bigClock = bigClock,
                    clockContent = clockContent,
                    clockPosition = clockPosition,
                    status = if (statusIcons) systemStatus else null,
                    onOpenWifi = { openSystemScreen(Settings.ACTION_WIFI_SETTINGS) },
                    onOpenBluetooth = { openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS) },
                    onLaunch = { app, from ->
                        if (launchRequest == null) {
                            launchRequest = LaunchRequest(app, from)
                            launchExpanded = true
                        }
                    },
                    // Titles and the system-home shortcut have no favorite/uninstall menu.
                    onOpenMenu = { if (it.intentUri == null && it in apps) menuApp = it },
                    onOpenSettings = { showSettings = true },
                    onOpenPower = { showPower = true },
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
                    modifier = Modifier.fillMaxSize(),
                )
                }

                // Draws over everything: the tile growing to fill the screen as the app opens.
                LaunchOverlay(
                    request = launchRequest,
                    expanded = launchExpanded,
                    onExpanded = { launchRequest?.let { startApp(it.app) } },
                    onCollapsed = { launchRequest = null },
                )

                Screensaver(wallpaper = wallpaper, visible = screensaverShowing)
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
                val newVersionName = updateInfo?.versionName ?: ""
                val statusText = when (updateStatus) {
                    UpdateStatus.IDLE -> stringResource(R.string.update_version, versionName)
                    UpdateStatus.CHECKING -> stringResource(R.string.update_checking, versionName)
                    UpdateStatus.UP_TO_DATE -> stringResource(R.string.update_latest, versionName)
                    UpdateStatus.AVAILABLE -> stringResource(R.string.update_available, versionName, newVersionName)
                    UpdateStatus.CHECK_FAILED -> stringResource(R.string.update_check_failed)
                    UpdateStatus.DOWNLOADING -> stringResource(R.string.update_downloading, downloadPercent)
                    UpdateStatus.DOWNLOAD_FAILED -> stringResource(R.string.update_download_failed)
                    UpdateStatus.NEEDS_PERMISSION -> stringResource(R.string.update_permission)
                    UpdateStatus.WRONG_SIGNATURE -> stringResource(R.string.update_wrong_key)
                    UpdateStatus.INSTALLING -> stringResource(R.string.update_installing)
                }
                val installLabel = if (updateInfo != null && updateStatus != UpdateStatus.DOWNLOADING) {
                    stringResource(R.string.settings_update_install, newVersionName)
                } else {
                    null
                }
                SettingsDialog(
                    versionLabel = stringResource(R.string.settings_version_label, versionName),
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
                    onCategoryChange = {
                        wallpaperCategory = it
                        settings.wallpaperCategory = it
                    },
                    intervalMinutes = wallpaperInterval,
                    onIntervalChange = {
                        wallpaperInterval = it
                        settings.wallpaperIntervalMinutes = it
                    },
                    photoCredit = photoCredit,
                    onNextWallpaper = { skipSignal++ },
                    updateStatus = statusText,
                    installLabel = installLabel,
                    onCheckUpdates = { scope.launch { checkForUpdates(automatic = false, thenInstall = true) } },
                    onInstallUpdate = { scope.launch { installUpdate(automatic = false) } },
                    autoCheckUpdates = autoCheck,
                    onAutoCheckChange = {
                        autoCheck = it
                        settings.autoCheckUpdates = it
                    },
                    networkStatus = networkStatus(),
                    customVolume = customVolume,
                    onCustomVolumeChange = {
                        customVolume = it
                        settings.customVolume = it
                    },
                    accessibilityOn = accessibilityOn,
                    onOpenAccessibility = {
                        if (enableAccessibilityDirectly()) {
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
                    onOpenSystemSettings = { openSettings() },
                    onDismiss = { showSettings = false },
                )
            }

            menuApp?.let { app ->
                val shown = favorites.map { it.key }
                val index = shown.indexOf(app.key)
                val isFavorite = index >= 0
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
                    add(MenuOption(stringResource(R.string.menu_change_icon)) { iconPickerApp = app })
                    if (IconOverrides(this@MainActivity).get(app.key) != null) {
                        add(MenuOption(stringResource(R.string.menu_reset_icon)) {
                            IconOverrides(this@MainActivity).clear(app.key)
                            iconsVersion++
                        })
                    }
                    add(MenuOption(stringResource(R.string.menu_app_info)) { openAppInfo(app) })
                    add(MenuOption(stringResource(R.string.menu_uninstall)) { uninstall(app) })
                }
                AppMenu(title = app.label, options = options, onDismiss = { menuApp = null })
            }

            if (showPower) {
                val needsService = stringResource(R.string.power_needs_service)
                AppMenu(
                    title = stringResource(R.string.power_title),
                    options = listOf(
                        MenuOption(stringResource(R.string.power_screensaver)) { screensaverShowing = true },
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
        }
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        resumeSignal++
        // Back from an app: the opening animation plays in reverse.
        launchExpanded = false
    }

    override fun onPause() {
        inForeground = false
        screensaverShowing = false
        super.onPause()
    }

    /** Any key wakes the screensaver (and is otherwise swallowed); any key also restarts the idle wait. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInputAt = System.currentTimeMillis()
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
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (wakeScreensaver()) return true
        return super.dispatchTouchEvent(ev)
    }

    /** Mice and air-mouse remotes: moving them counts as being there. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (wakeScreensaver()) return true
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

    /** Home pressed while the launcher is already on screen: back to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        screensaverShowing = false
        page = PAGE_HOME
        resetSignal++
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
        if (intent?.hasExtra("ical_url") == true) {
            settings.icalUrl = intent.getStringExtra("ical_url")?.trim()?.takeIf { it.isNotEmpty() }
            calendarTick++
        }
    }

    /** Turns the accessibility service on without the system screens, when WRITE_SECURE_SETTINGS was granted over adb. */
    private fun enableAccessibilityDirectly(): Boolean = try {
        val service = "$packageName/${SystemService::class.java.name}"
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (service !in current.split(':')) {
            val updated = if (current.isEmpty()) service else "$current:$service"
            Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
        }
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    } catch (_: SecurityException) {
        false
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

    /** Whether the user has switched on this app's accessibility service. */
    private fun accessibilityServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains("$packageName/") == true
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

private enum class UpdateStatus {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, CHECK_FAILED, DOWNLOADING, DOWNLOAD_FAILED, NEEDS_PERMISSION, WRONG_SIGNATURE, INSTALLING,
}
