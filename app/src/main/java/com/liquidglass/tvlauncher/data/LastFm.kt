package com.liquidglass.tvlauncher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * A small Last.fm API client (blocking calls: run them off the main thread). Sign-in itself (Last.fm's
 * "mobile session" auth, a username and password signed with the app's API key and secret) happens on the
 * phone, in Liquid TV Pilot — see its LastFmCard.kt — which hands this app only the resulting session key,
 * never the password; this file only needs to sign the scrobbling calls that key makes, with the same
 * "method signature" algorithm.
 */
class LastFmException(message: String) : Exception(message)

private const val LASTFM_API = "https://ws.audioscrobbler.com/2.0/"

private fun lastFmEnc(text: String): String = URLEncoder.encode(text, "UTF-8")

/** Last.fm's "method signature": the params sorted by name, glued together, the secret appended, MD5'd. */
private fun lastFmSign(params: Map<String, String>, secret: String): String {
    val base = params.toSortedMap().entries.joinToString("") { (k, v) -> k + v } + secret
    return MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Signs and POSTs [params] (api_key/api_sig/format are added here) and returns the parsed JSON reply. */
private fun lastFmCall(params: Map<String, String>, apiKey: String, secret: String): JSONObject {
    val full = params + ("api_key" to apiKey)
    val signed = full + ("api_sig" to lastFmSign(full, secret)) + ("format" to "json")
    val body = signed.entries.joinToString("&") { (k, v) -> "${lastFmEnc(k)}=${lastFmEnc(v)}" }.toByteArray()
    val connection = URL(LASTFM_API).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setFixedLengthStreamingMode(body.size)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        val text = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw LastFmException("HTTP $code")
        }
        if (code !in 200..299 || json.has("error")) throw LastFmException(json.optString("message").ifBlank { "HTTP $code" })
        return json
    } catch (e: LastFmException) {
        throw e
    } catch (e: Exception) {
        throw LastFmException(e.message ?: e.javaClass.simpleName)
    } finally {
        connection.disconnect()
    }
}

/** Scrobbles with the session key the phone sends (see [LastFmHub.login]): what plays now, and what played. */
class LastFmClient(private val apiKey: String, private val secret: String, private val sessionKey: String) {
    fun updateNowPlaying(artist: String, track: String, album: String) {
        val params = mutableMapOf("method" to "track.updateNowPlaying", "artist" to artist, "track" to track, "sk" to sessionKey)
        if (album.isNotBlank()) params["album"] = album
        lastFmCall(params, apiKey, secret)
    }

    /** [startedAt] is when the track began playing, in seconds since epoch (Last.fm's own "timestamp" field). */
    fun scrobble(artist: String, track: String, album: String, startedAt: Long) {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "artist" to artist,
            "track" to track,
            "timestamp" to startedAt.toString(),
            "sk" to sessionKey,
        )
        if (album.isNotBlank()) params["album"] = album
        lastFmCall(params, apiKey, secret)
    }
}
