package com.liquidglass.spotify

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics for a track, from lrclib.net's free, keyless API (no account or secret needed, unlike the
 * rest of this module). [synced] is a line-by-line (position in ms, text) list parsed out of LRC's
 * "[mm:ss.xx]text" tags, present when the track has timed lyrics; [plain] is the untimed text, used
 * as a fallback when it doesn't.
 */
class Lyrics(val synced: List<Pair<Long, String>>?, val plain: String?)

object LyricsClient {
    private const val API = "https://lrclib.net/api/get"

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    /** Blocking: looks up a track's lyrics by title/artist/album (and duration, when known). Null on a 404 or any failure. */
    fun lookup(title: String, artist: String, album: String, durationMs: Long): Lyrics? {
        if (title.isBlank() || artist.isBlank()) return null
        var url = "$API?track_name=${enc(title)}&artist_name=${enc(artist)}"
        if (album.isNotBlank()) url += "&album_name=${enc(album)}"
        if (durationMs > 0) url += "&duration=${durationMs / 1000}"
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val synced = o.optString("syncedLyrics").ifBlank { null }?.let(::parseSynced)
            val plain = o.optString("plainLyrics").ifBlank { null }
            if (synced == null && plain == null) null else Lyrics(synced, plain)
        } catch (e: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Turns LRC's "[mm:ss.xx]text" lines into (position in ms, text) pairs; lines without a tag (metadata, blank) are dropped. */
    private fun parseSynced(lrc: String): List<Pair<Long, String>> {
        val tag = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
        val lines = mutableListOf<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty()) continue
            val text = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val millis = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                lines += (minutes * 60_000 + seconds * 1000 + millis) to text
            }
        }
        return lines.sortedBy { it.first }
    }
}
