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

    companion object {
        const val DEFAULT_DOCK_TRANSPARENCY = 60
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    }
}
