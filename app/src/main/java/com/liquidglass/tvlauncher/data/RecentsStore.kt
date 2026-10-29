package com.liquidglass.tvlauncher.data

import android.content.Context

/** The apps opened from this launcher most recently, newest first. */
class RecentsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    fun record(key: String) {
        val updated = (listOf(key) + load().filter { it != key }).take(MAX)
        prefs.edit().putString(KEY, updated.joinToString(SEPARATOR)).apply()
    }

    companion object {
        private const val MAX = 12
        private const val KEY = "recent_apps"
        private const val SEPARATOR = "|"
    }
}
