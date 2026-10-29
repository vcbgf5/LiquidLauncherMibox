package com.liquidglass.tvlauncher.data

import android.content.Context

/**
 * Remembers which apps are pinned to the dock, and in what order.
 * `load()` returns null until the user (or the first-run default) has
 * saved something, which is how first run is told apart from "empty dock".
 */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): List<String>? =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }

    fun save(keys: List<String>) {
        prefs.edit().putString(KEY, keys.joinToString(SEPARATOR)).apply()
    }

    companion object {
        const val MAX = 5
        private const val KEY = "keys"
        private const val SEPARATOR = "|"
    }
}
