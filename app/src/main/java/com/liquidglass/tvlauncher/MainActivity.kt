package com.liquidglass.tvlauncher

import android.accounts.AccountManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.liquidglass.tvlauncher.data.AccountProvider
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.AppRepository
import com.liquidglass.tvlauncher.data.FavoritesStore
import com.liquidglass.tvlauncher.data.PriorityApps
import com.liquidglass.tvlauncher.ui.AppMenu
import com.liquidglass.tvlauncher.ui.HomeScreen
import com.liquidglass.tvlauncher.ui.MenuOption

class MainActivity : ComponentActivity() {

    /** Bumped on every onResume so installs/uninstalls made elsewhere show up. */
    private var resumeSignal by mutableIntStateOf(0)

    /** Bumped on Home/Back: focus returns to the first favorite. */
    private var resetSignal by mutableIntStateOf(0)

    /** False = home screen (photo + dock), true = the full app list. */
    private var showApps by mutableStateOf(false)

    private lateinit var store: FavoritesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FavoritesStore(this)

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var favoriteKeys by remember { mutableStateOf(store.load()) }
            var menuApp by remember { mutableStateOf<AppInfo?>(null) }
            var accountName by remember { mutableStateOf(AccountProvider.googleAccountName(this@MainActivity)) }

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

            HomeScreen(
                favorites = favorites,
                allApps = apps,
                showApps = showApps,
                onShowApps = { showApps = it },
                resetSignal = resetSignal,
                accountName = accountName,
                onLaunch = { launchApp(it) },
                onOpenMenu = { menuApp = it },
                onOpenSettings = { openSettings() },
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
        resumeSignal++
    }

    /** Home pressed while the launcher is already on screen: back to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showApps = false
        resetSignal++
    }

    private fun swapped(list: List<String>, from: Int, to: Int): List<String> =
        list.toMutableList().also { val moved = it[from]; it[from] = it[to]; it[to] = moved }

    private fun launchApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(app.packageName, app.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Uninstalled since the list was loaded; the next resume refreshes it.
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
