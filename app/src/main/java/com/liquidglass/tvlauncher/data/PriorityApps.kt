package com.liquidglass.tvlauncher.data

/**
 * The apps that always come first, in this order, and that make up the
 * default favorites. Each entry lists the package names an app is known
 * by: the Android TV build first, the phone build as a fallback for boxes
 * that only have that one.
 */
object PriorityApps {
    private val GROUPS: List<List<String>> = listOf(
        listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        listOf("com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music"),
        listOf("com.android.vending"),
        listOf("com.spotify.tv.android", "com.spotify.music"),
        listOf("com.disney.disneyplus"),
    )

    /** Position in the priority list, or [Int.MAX_VALUE] for everything else. */
    fun rank(packageName: String): Int {
        val index = GROUPS.indexOfFirst { packageName in it }
        return if (index < 0) Int.MAX_VALUE else index
    }

    /** One installed app per group (the first variant that exists), in priority order. */
    fun defaultFavoriteKeys(apps: List<AppInfo>): List<String> =
        GROUPS.mapNotNull { group ->
            group.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        }.map { it.key }
}
