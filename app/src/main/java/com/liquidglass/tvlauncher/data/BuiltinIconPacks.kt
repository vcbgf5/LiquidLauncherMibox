package com.liquidglass.tvlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.liquidglass.tvlauncher.R

/**
 * Three icon packs that come with the launcher, so changing one app's icon works without
 * installing anything: Apple TV (a wide card in the app's colours, the logo floating on it),
 * Liquid Glass (the same as frosted iOS 26 / tvOS 26 glass) and Minimal (graphite card, white
 * logo). They are drawn by tools/make_tvos_tiles.py from open logo shapes (Simple Icons, CC0); the
 * names and logos belong to their owners and only identify the apps on the user's own screen.
 *
 * They show up in the icon picker next to any Nova/ADW packs installed on the box, under the
 * pseudo package names `builtin:apple`, `builtin:glass` and `builtin:mono`.
 */
object BuiltinIconPacks {
    const val PREFIX = "builtin:"

    private class Entry(val name: String, val apple: Int, val glass: Int, val mono: Int, val packages: List<String>)

    private val ENTRIES = listOf(
        Entry("youtube", R.drawable.tile_youtube, R.drawable.tile_youtube_glass, R.drawable.tile_youtube_mono,
            listOf("com.google.android.youtube.tv", "com.google.android.youtube")),
        Entry("youtubemusic", R.drawable.tile_youtubemusic, R.drawable.tile_youtubemusic_glass, R.drawable.tile_youtubemusic_mono,
            listOf("com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music")),
        Entry("netflix", R.drawable.tile_netflix, R.drawable.tile_netflix_glass, R.drawable.tile_netflix_mono,
            listOf("com.netflix.ninja", "com.netflix.mediaclient")),
        Entry("googleplay", R.drawable.tile_googleplay, R.drawable.tile_googleplay_glass, R.drawable.tile_googleplay_mono,
            listOf("com.android.vending")),
        Entry("spotify", R.drawable.tile_spotify, R.drawable.tile_spotify_glass, R.drawable.tile_spotify_mono,
            listOf("com.spotify.tv.android", "com.spotify.music")),
        Entry("disneyplus", R.drawable.tile_disneyplus, R.drawable.tile_disneyplus_glass, R.drawable.tile_disneyplus_mono,
            listOf("com.disney.disneyplus")),
        Entry("primevideo", R.drawable.tile_primevideo, R.drawable.tile_primevideo_glass, R.drawable.tile_primevideo_mono,
            listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
        Entry("hbomax", R.drawable.tile_hbomax, R.drawable.tile_hbomax_glass, R.drawable.tile_hbomax_mono,
            listOf("com.wbd.stream", "com.hbo.hbonow")),
        Entry("twitch", R.drawable.tile_twitch, R.drawable.tile_twitch_glass, R.drawable.tile_twitch_mono,
            listOf("tv.twitch.android.app")),
        Entry("plex", R.drawable.tile_plex, R.drawable.tile_plex_glass, R.drawable.tile_plex_mono,
            listOf("com.plexapp.android")),
        Entry("kodi", R.drawable.tile_kodi, R.drawable.tile_kodi_glass, R.drawable.tile_kodi_mono,
            listOf("org.xbmc.kodi")),
        Entry("appletv", R.drawable.tile_appletv, R.drawable.tile_appletv_glass, R.drawable.tile_appletv_mono,
            listOf("com.apple.atve.androidtv.appletv")),
        Entry("crunchyroll", R.drawable.tile_crunchyroll, R.drawable.tile_crunchyroll_glass, R.drawable.tile_crunchyroll_mono,
            listOf("com.crunchyroll.crunchyroid")),
        Entry("vlc", R.drawable.tile_vlc, R.drawable.tile_vlc_glass, R.drawable.tile_vlc_mono,
            listOf("org.videolan.vlc")),
    )

    private val STYLES = listOf("apple" to R.string.pack_apple, "glass" to R.string.pack_glass, "mono" to R.string.pack_mono)

    fun isBuiltin(packageName: String): Boolean = packageName.startsWith(PREFIX)

    fun packs(context: Context): List<IconPack> =
        STYLES.map { (id, label) -> IconPack(PREFIX + id, context.getString(label)) }

    /** Every icon of the pack, and which of them belongs to which app. */
    fun contents(): IconPacks.Contents = IconPacks.Contents(
        names = ENTRIES.map { it.name }.sorted(),
        byComponent = emptyMap(),
        byPackage = ENTRIES.flatMap { entry -> entry.packages.map { it to entry.name } }.toMap(),
    )

    /** The 5:3 tile called [name] in the pack [packId] (`builtin:glass`...), or null. */
    fun tile(context: Context, packId: String, name: String): Bitmap? {
        val entry = ENTRIES.firstOrNull { it.name == name } ?: return null
        val resource = when (packId.removePrefix(PREFIX)) {
            "glass" -> entry.glass
            "mono" -> entry.mono
            else -> entry.apple
        }
        return runCatching { BitmapFactory.decodeResource(context.resources, resource) }.getOrNull()
    }
}
