package com.liquidglass.tvlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The light mode: when it is on the launcher draws less (no breathing wallpaper, no sweeping
 * highlight or tilt on tiles, no cascading entrances, instant wallpaper changes, a smaller wallpaper
 * in memory, lighter shadows), which is what keeps a weak box smooth.
 *
 * It follows the setting: always, never, or "automatic", which means on when the device looks weak
 * or when [FrameMonitor] has watched the launcher stutter (that is remembered, so it stays on).
 * It is a snapshot state, so everything that reads it redraws when it changes.
 */
object Performance {
    var lite: Boolean by mutableStateOf(false)
        private set

    fun refresh(context: Context, settings: LauncherSettings) {
        lite = when (settings.performanceMode) {
            LauncherSettings.PERF_LITE -> true
            LauncherSettings.PERF_FULL -> false
            else -> settings.autoLite || looksWeak(context)
        }
    }

    private fun looksWeak(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.isLowRamDevice || Runtime.getRuntime().availableProcessors() <= 2 || Build.VERSION.SDK_INT < 24
    } catch (_: Throwable) {
        false
    }
}

/**
 * Watches how long the launcher's frames really take (only frames that were drawn, so an idle
 * screen costs nothing) and calls [onStutter] once when the picture keeps missing its deadline
 * during normal use. The first seconds after start-up are not counted: filling the screen for the
 * first time is always slow.
 */
class FrameMonitor(private val startedAt: Long = SystemClock.uptimeMillis(), private val onStutter: () -> Unit) {
    private var frames = 0
    private var slow = 0
    private var badWindows = 0
    private var fired = false

    @RequiresApi(24)
    fun onFrame(metrics: FrameMetrics) {
        if (fired || SystemClock.uptimeMillis() - startedAt < STARTUP_GRACE_MS) return
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        val millis = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L
        frames++
        if (millis > SLOW_FRAME_MS) slow++
        if (frames >= WINDOW) {
            if (slow * 100 / frames >= BAD_PERCENT) badWindows++ else badWindows = (badWindows - 1).coerceAtLeast(0)
            frames = 0
            slow = 0
            if (badWindows >= BAD_WINDOWS_NEEDED) {
                fired = true
                onStutter()
            }
        }
    }

    private companion object {
        const val STARTUP_GRACE_MS = 10_000L
        const val SLOW_FRAME_MS = 34L // two missed frames at 60 Hz
        const val WINDOW = 60
        const val BAD_PERCENT = 30
        const val BAD_WINDOWS_NEEDED = 3
    }
}
