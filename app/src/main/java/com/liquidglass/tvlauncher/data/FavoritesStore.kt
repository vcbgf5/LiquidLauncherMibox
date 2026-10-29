package com.liquidglass.tvlauncher.data

import android.content.Context

/**
 * Remembers which apps are pinned to the dock, and in what order.
 *
 * `load()` returns null when nothing is saved, which is how first run
 * is told apart from "empty dock". The saved list is never thrown away by an
 * app update: updating keeps the dock exactly as it was.
 */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): List<String>? {
        return prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }
    }

    fun save(keys: List<String>) {
        prefs.edit()
            .putString(KEY, keys.joinToString(SEPARATOR))
            .apply()
    }

    companion object {
        const val MAX = 6
        private const val KEY = "keys"
        private const val SEPARATOR = "|"
    }
}
