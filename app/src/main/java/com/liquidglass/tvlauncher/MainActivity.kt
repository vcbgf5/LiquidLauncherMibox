package com.liquidglass.tvlauncher

import android.accounts.AccountManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.delay
import com.liquidglass.tvlauncher.data.AccountProvider
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.AppRepository
import com.liquidglass.tvlauncher.data.FavoritesStore
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.PriorityApps
import com.liquidglass.tvlauncher.data.WallpaperCatalog
import com.liquidglass.tvlauncher.data.WallpaperImage
import com.liquidglass.tvlauncher.data.WallpaperRepository
import com.liquidglass.tvlauncher.ui.AppMenu
import com.liquidglass.tvlauncher.ui.HomeScreen
import com.liquidglass.tvlauncher.ui.LaunchOverlay
import com.liquidglass.tvlauncher.ui.LaunchRequest
import com.liquidglass.tvlauncher.ui.MenuOption
import com.liquidglass.tvlauncher.ui.SettingsDialog
import com.liquidglass.tvlauncher.ui.WallpaperState

class MainActivity : ComponentActivity() {

    /** Bumped on every onResume so installs/uninstalls made elsewhere show up. */
    private var resumeSignal by mutableIntStateOf(0)

    /** Bumped on Home/Back: focus returns to the first favorite. */
    private var resetSignal by mutableIntStateOf(0)

    /** False = home screen (photo + dock), true = the full app list. */
    private var showApps by mutableStateOf(false)

    /** The app being opened (null when none) and whether its opening animation is out. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)
    private var launchExpanded by mutableStateOf(false)

    /** True while the launcher is on screen; wallpaper rotation only runs then. */
    private var inForeground by mutableStateOf(false)

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

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var favoriteKeys by remember { mutableStateOf(store.load()) }
            var menuApp by remember { mutableStateOf<AppInfo?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            var dockTransparency by remember { mutableIntStateOf(settings.dockTransparency) }
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

            val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { name ->
                    AccountProvider.save(this@MainActivity, name)
                    accountName = name
                }
            }

            LaunchedEffect(resumeSignal) {
                apps = AppRepository.load(this@MainActivity)
                if (accountName == null) accountName = AccountProvider.googleAccountName(this@MainActivity)
            }

            LaunchedEffect(resumeSignal) {
                if (catalog.isEmpty) catalog = WallpaperRepository.catalog(this@MainActivity)
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

            fun saveKeys(updated: List<String>) {
                favoriteKeys = updated
                store.save(updated)
            }

            // Back never leaves the launcher: from the app list it goes up to the
            // home screen, from the home screen it just returns focus to the dock.
            BackHandler {
                menuApp = null
                if (showApps) showApps = false else resetSignal++
            }

            Box(modifier = Modifier.fillMaxSize()) {
                HomeScreen(
                    favorites = favorites,
                    allApps = apps,
                    showApps = showApps,
                    onShowApps = { showApps = it },
                    resetSignal = resetSignal,
                    accountName = accountName,
                    wallpaper = wallpaper,
                    dockTransparency = dockTransparency / 100f,
                    onLaunch = { app, from ->
                        if (launchRequest == null) {
                            launchRequest = LaunchRequest(app, from)
                            launchExpanded = true
                        }
                    },
                    onOpenMenu = { menuApp = it },
                    onOpenSettings = { showSettings = true },
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

                // Draws over everything: the tile growing to fill the screen as the app opens.
                LaunchOverlay(
                    request = launchRequest,
                    expanded = launchExpanded,
                    onExpanded = { launchRequest?.let { startApp(it.app) } },
                    onCollapsed = { launchRequest = null },
                )
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
                SettingsDialog(
                    transparencyPercent = dockTransparency,
                    onTransparencyChange = {
                        dockTransparency = it
                        settings.dockTransparency = it
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
                    add(MenuOption(stringResource(R.string.menu_app_info)) { openAppInfo(app) })
                    add(MenuOption(stringResource(R.string.menu_uninstall)) { uninstall(app) })
                }
                AppMenu(title = app.label, options = options, onDismiss = { menuApp = null })
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
        super.onPause()
    }

    /** Home pressed while the launcher is already on screen: back to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showApps = false
        resetSignal++
    }

    private fun swapped(list: List<String>, from: Int, to: Int): List<String> =
        list.toMutableList().also { val moved = it[from]; it[from] = it[to]; it[to] = moved }

    private fun startApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(app.packageName, app.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
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
