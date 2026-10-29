package com.liquidglass.tvlauncher

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.AppRepository
import com.liquidglass.tvlauncher.data.FavoritesStore
import com.liquidglass.tvlauncher.ui.AppMenu
import com.liquidglass.tvlauncher.ui.HomeScreen
import com.liquidglass.tvlauncher.ui.MenuOption

class MainActivity : ComponentActivity() {

    /** Bumped on every onResume so installs/uninstalls made elsewhere show up. */
    private var resumeSignal by mutableIntStateOf(0)

    /** Bumped on Home/Back: scroll to the top and focus the first favorite. */
    private var homeSignal by mutableIntStateOf(0)

    private lateinit var store: FavoritesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FavoritesStore(this)

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var favoriteKeys by remember { mutableStateOf(store.load()) }
            var menuApp by remember { mutableStateOf<AppInfo?>(null) }

            LaunchedEffect(resumeSignal) {
                apps = AppRepository.load(this@MainActivity)
            }

            // First run: pin the first few (TV) apps so the dock isn't empty.
            LaunchedEffect(apps) {
                if (favoriteKeys == null && apps.isNotEmpty()) {
                    val defaults = apps.take(FavoritesStore.MAX).map { it.key }
                    favoriteKeys = defaults
                    store.save(defaults)
                }
            }

            val keys = favoriteKeys.orEmpty()
            val byKey = remember(apps) { apps.associateBy { it.key } }
            val favorites = remember(apps, keys) { keys.mapNotNull { byKey[it] } }
            val rest = remember(apps, favorites) {
                val pinned = favorites.map { it.key }.toSet()
                apps.filter { it.key !in pinned }
            }

            fun saveKeys(updated: List<String>) {
                favoriteKeys = updated
                store.save(updated)
            }

            BackHandler {
                menuApp = null
                homeSignal++
            }

            HomeScreen(
                favorites = favorites,
                rest = rest,
                homeSignal = homeSignal,
                onLaunch = { launchApp(it) },
                onOpenMenu = { menuApp = it },
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

    /** Home pressed while the launcher is already on screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal++
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
