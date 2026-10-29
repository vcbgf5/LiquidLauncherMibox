package com.liquidglass.tvlauncher.data

import android.content.Context

/**
 * Remembers which apps are pinned to the dock, and in what order.
 *
 * `load()` returns null when nothing valid is saved, which is how first run
 * is told apart from "empty dock". Bumping [DEFAULTS_VERSION] makes every
 * install forget its saved list once and fall back to the new defaults.
 */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): List<String>? {
        if (prefs.getInt(KEY_VERSION, 0) != DEFAULTS_VERSION) return null
        return prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }
    }

    fun save(keys: List<String>) {
        prefs.edit()
            .putInt(KEY_VERSION, DEFAULTS_VERSION)
            .putString(KEY, keys.joinToString(SEPARATOR))
            .apply()
    }

    companion object {
        const val MAX = 6
        private const val DEFAULTS_VERSION = 2
        private const val KEY = "keys"
        private const val KEY_VERSION = "defaults_version"
        private const val SEPARATOR = "|"
    }
}
