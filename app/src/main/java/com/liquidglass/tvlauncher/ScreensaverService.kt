package com.liquidglass.tvlauncher

import android.service.dreams.DreamService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.WallpaperImage
import com.liquidglass.tvlauncher.data.WallpaperRepository
import com.liquidglass.tvlauncher.ui.Screensaver
import com.liquidglass.tvlauncher.ui.WallpaperState
import kotlinx.coroutines.delay

/**
 * The same screensaver as the launcher's own, offered to Android as a system screensaver:
 * Settings > Device Preferences > Screen saver > Liquid TV. Android then starts it after its
 * own idle time, whichever app is on screen (the launcher's timer only works on the launcher).
 *
 * A dream is a service, not an activity, so it has to play the part of the lifecycle and
 * saved-state owner that Compose looks for on its window.
 */
class ScreensaverService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any key or touch ends it
        isFullscreen = true

        val settings = LauncherSettings(this)
        val wallpaper = WallpaperState(
            WallpaperImage(
                ImageBitmap.imageResource(resources, R.drawable.bg_hero),
                ImageBitmap.imageResource(resources, R.drawable.bg_hero_blur),
            ),
        )
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensaverService)
            setViewTreeSavedStateRegistryOwner(this@ScreensaverService)
            setContent {
                // The launcher's own wallpapers: the category and interval picked in its settings.
                LaunchedEffect(Unit) {
                    val files = WallpaperRepository.catalog(this@ScreensaverService).filesFor(settings.wallpaperCategory)
                    if (files.isEmpty()) return@LaunchedEffect
                    var last = settings.lastWallpaper
                    while (true) {
                        val pick = files.filter { it.path != last }.ifEmpty { files }.random()
                        val image = WallpaperRepository.load(this@ScreensaverService, pick.path)
                        if (image != null) {
                            wallpaper.show(image)
                            last = pick.path
                            delay(settings.wallpaperIntervalMinutes * 60_000L)
                        } else {
                            delay(30_000L)
                        }
                    }
                }
                Screensaver(wallpaper = wallpaper, visible = true)
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
