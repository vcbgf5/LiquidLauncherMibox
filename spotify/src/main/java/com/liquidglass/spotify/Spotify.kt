package com.liquidglass.spotify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A small Spotify Web API client (blocking calls: run them off the main thread). Sign-in is the
 * "authorization code with PKCE" flow, which needs only the app's public Client ID, never a secret,
 * so it is safe to ship inside an APK. The phone app signs in and hands the tokens to the launcher.
 *
 * Playing needs a Spotify Premium account and a device that Spotify can see (the Spotify app running on the box).
 */
class SpotifyTokens(val access: String, val refresh: String, val expiresAt: Long) {
    fun expiresSoon(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt - 60_000

    /** The form in which the phone gives the session to the launcher. */
    fun toJson(clientId: String): String =
        JSONObject().put("c", clientId).put("a", access).put("r", refresh).put("e", expiresAt).toString()

    companion object {
        /** Reads what [toJson] wrote: the client id and the tokens. */
        fun fromJson(text: String): Pair<String, SpotifyTokens>? = try {
            val o = JSONObject(text)
            o.getString("c") to SpotifyTokens(o.getString("a"), o.getString("r"), o.getLong("e"))
        } catch (e: Exception) {
            null
        }
    }
}

/** Spotify refused or something failed: [status] is the HTTP status (0 when there was no answer at all). */
class SpotifyException(val status: Int, message: String) : Exception(message)

class SpotifyDevice(val id: String, val name: String, val type: String, val active: Boolean, val volume: Int)

class SpotifyPlayback(
    val playing: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val shuffle: Boolean,
    val contextUri: String?,
    val trackUri: String?,
    val device: SpotifyDevice?,
)

class SpotifyPlaylist(val id: String, val uri: String, val name: String, val owner: String, val imageUrl: String?, val tracks: Int)

class SpotifyTrack(val uri: String, val name: String, val artist: String, val imageUrl: String?, val durationMs: Long)

class SpotifyProfile(val id: String, val name: String, val premium: Boolean)

object SpotifyAuth {
    /** Must be added, exactly like this, to the Redirect URIs of the app in the Spotify developer dashboard. */
    const val REDIRECT_URI = "liquidtvpilot://spotify-callback"

    const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing " +
        "playlist-read-private playlist-read-collaborative user-read-private"

    private const val ACCOUNTS = "https://accounts.spotify.com"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    fun newVerifier(): String {
        val random = SecureRandom()
        return String(CharArray(64) { ALPHABET[random.nextInt(ALPHABET.length)] })
    }

    fun challenge(verifier: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )

    fun authorizeUrl(clientId: String, challenge: String, state: String): String =
        "$ACCOUNTS/authorize?response_type=code&client_id=${enc(clientId)}&scope=${enc(SCOPES)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}&state=${enc(state)}&code_challenge_method=S256&code_challenge=${enc(challenge)}"

    fun exchange(clientId: String, code: String, verifier: String): SpotifyTokens = token(
        "client_id=${enc(clientId)}&grant_type=authorization_code&code=${enc(code)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}&code_verifier=${enc(verifier)}",
        previousRefresh = null,
    )

    fun refresh(clientId: String, refreshToken: String): SpotifyTokens = token(
        "client_id=${enc(clientId)}&grant_type=refresh_token&refresh_token=${enc(refreshToken)}",
        previousRefresh = refreshToken,
    )

    private fun token(form: String, previousRefresh: String?): SpotifyTokens {
        val connection = URL("$ACCOUNTS/api/token").openConnection() as HttpURLConnection
        try {
            val body = form.toByteArray()
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val text = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = try {
                    JSONObject(text).let { it.optString("error_description").ifBlank { it.optString("error") } }
                } catch (e: Exception) {
                    ""
                }
                throw SpotifyException(code, reason.ifBlank { "HTTP $code" })
            }
            val json = JSONObject(text)
            return SpotifyTokens(
                access = json.getString("access_token"),
                refresh = json.optString("refresh_token").ifBlank { previousRefresh.orEmpty() },
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
            )
        } catch (e: SpotifyException) {
            throw e
        } catch (e: Exception) {
            throw SpotifyException(0, e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }
}

class SpotifyClient(
    val clientId: String,
    tokens: SpotifyTokens,
    /** Called with the new tokens whenever they were renewed, so the caller can keep them. */
    private val onTokens: (SpotifyTokens) -> Unit = {},
) {
    @Volatile
    var tokens: SpotifyTokens = tokens
        private set

    private class Reply(val status: Int, val body: String)

    @Synchronized
    private fun renew() {
        val fresh = SpotifyAuth.refresh(clientId, tokens.refresh)
        tokens = fresh
        onTokens(fresh)
    }

    private fun raw(method: String, path: String, json: String?): Reply {
        val connection = URL(API + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Authorization", "Bearer ${tokens.access}")
            if (method == "PUT" || method == "POST") {
                val bytes = json?.toByteArray() ?: ByteArray(0)
                connection.doOutput = true
                if (json != null) connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return Reply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun send(method: String, path: String, json: String? = null): Reply {
        try {
            if (tokens.expiresSoon()) renew()
            var reply = raw(method, path, json)
            if (reply.status == 401) {
                renew()
                reply = raw(method, path, json)
            }
            return reply
        } catch (e: SpotifyException) {
            throw e
        } catch (e: Exception) {
            throw SpotifyException(0, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun message(reply: Reply): String = try {
        JSONObject(reply.body).getJSONObject("error").optString("message").ifBlank { "HTTP ${reply.status}" }
    } catch (e: Exception) {
        "HTTP ${reply.status}"
    }

    private fun checked(reply: Reply): Reply {
        if (reply.status !in 200..299) throw SpotifyException(reply.status, message(reply))
        return reply
    }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    private fun deviceQuery(deviceId: String?): String = if (deviceId.isNullOrBlank()) "" else "?device_id=${enc(deviceId)}"

    fun profile(): SpotifyProfile {
        val o = JSONObject(checked(send("GET", "/me")).body)
        return SpotifyProfile(o.optString("id"), o.optString("display_name").ifBlank { o.optString("id") }, o.optString("product") == "premium")
    }

    /** What is playing right now, or null when nothing is (Spotify answers "no content" without an active device). */
    fun playback(): SpotifyPlayback? {
        val reply = send("GET", "/me/player")
        if (reply.status == 204 || reply.body.isBlank()) return null
        checked(reply)
        val o = JSONObject(reply.body)
        val item = o.optJSONObject("item")
        val album = item?.optJSONObject("album")
        return SpotifyPlayback(
            playing = o.optBoolean("is_playing"),
            title = item?.optString("name").orEmpty(),
            artist = artists(item?.optJSONArray("artists")),
            album = album?.optString("name").orEmpty(),
            imageUrl = bestImage(album?.optJSONArray("images"), 300),
            progressMs = o.optLong("progress_ms"),
            durationMs = item?.optLong("duration_ms") ?: 0L,
            shuffle = o.optBoolean("shuffle_state"),
            contextUri = o.optJSONObject("context")?.optString("uri")?.ifBlank { null },
            trackUri = item?.optString("uri")?.ifBlank { null },
            device = o.optJSONObject("device")?.let { device(it) },
        )
    }

    fun devices(): List<SpotifyDevice> {
        val list = JSONObject(checked(send("GET", "/me/player/devices")).body).optJSONArray("devices") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let { d -> device(d) } }
    }

    fun playlists(limit: Int = 50): List<SpotifyPlaylist> {
        val list = JSONObject(checked(send("GET", "/me/playlists?limit=$limit")).body).optJSONArray("items") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val p = list.optJSONObject(index) ?: return@mapNotNull null
            val id = p.optString("id")
            if (id.isBlank()) return@mapNotNull null
            SpotifyPlaylist(
                id = id,
                uri = p.optString("uri").ifBlank { "spotify:playlist:$id" },
                name = p.optString("name"),
                owner = p.optJSONObject("owner")?.optString("display_name").orEmpty(),
                imageUrl = bestImage(p.optJSONArray("images"), 300),
                tracks = p.optJSONObject("items")?.optInt("total") ?: p.optJSONObject("tracks")?.optInt("total") ?: 0,
            )
        }
    }

    /** The tracks of a playlist. Spotify only shows them for playlists the user owns or collaborates on; others answer 403. */
    fun playlistTracks(id: String, limit: Int = 100): List<SpotifyTrack> {
        var reply = send("GET", "/playlists/${enc(id)}/items?limit=$limit")
        if (reply.status == 404) reply = send("GET", "/playlists/${enc(id)}/tracks?limit=$limit")
        val list = JSONObject(checked(reply).body).optJSONArray("items") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val entry = list.optJSONObject(index) ?: return@mapNotNull null
            val t = entry.optJSONObject("item") ?: entry.optJSONObject("track") ?: return@mapNotNull null
            val uri = t.optString("uri")
            if (uri.isBlank()) return@mapNotNull null
            SpotifyTrack(
                uri = uri,
                name = t.optString("name"),
                artist = artists(t.optJSONArray("artists")),
                imageUrl = bestImage(t.optJSONObject("album")?.optJSONArray("images"), 64),
                durationMs = t.optLong("duration_ms"),
            )
        }
    }

    /** Starts something: a playlist/album ([contextUri], optionally from [trackUri]), a single [trackUri], or just resumes. */
    fun play(contextUri: String? = null, trackUri: String? = null, deviceId: String? = null) {
        val body = when {
            contextUri != null -> JSONObject().put("context_uri", contextUri).also {
                if (trackUri != null) it.put("offset", JSONObject().put("uri", trackUri))
            }.toString()
            trackUri != null -> JSONObject().put("uris", JSONArray().put(trackUri)).toString()
            else -> null
        }
        checked(send("PUT", "/me/player/play" + deviceQuery(deviceId), body))
    }

    fun pause(deviceId: String? = null) {
        checked(send("PUT", "/me/player/pause" + deviceQuery(deviceId)))
    }

    fun next(deviceId: String? = null) {
        checked(send("POST", "/me/player/next" + deviceQuery(deviceId)))
    }

    fun previous(deviceId: String? = null) {
        checked(send("POST", "/me/player/previous" + deviceQuery(deviceId)))
    }

    fun seek(positionMs: Long, deviceId: String? = null) {
        val query = "?position_ms=$positionMs" + if (deviceId.isNullOrBlank()) "" else "&device_id=${enc(deviceId)}"
        checked(send("PUT", "/me/player/seek$query"))
    }

    fun setVolume(percent: Int, deviceId: String? = null) {
        val query = "?volume_percent=${percent.coerceIn(0, 100)}" + if (deviceId.isNullOrBlank()) "" else "&device_id=${enc(deviceId)}"
        checked(send("PUT", "/me/player/volume$query"))
    }

    fun shuffle(on: Boolean, deviceId: String? = null) {
        val query = "?state=$on" + if (deviceId.isNullOrBlank()) "" else "&device_id=${enc(deviceId)}"
        checked(send("PUT", "/me/player/shuffle$query"))
    }

    /** Moves playback to another device (the one named [deviceId]), starting it when [play] is set. */
    fun transfer(deviceId: String, play: Boolean = true) {
        checked(send("PUT", "/me/player", JSONObject().put("device_ids", JSONArray().put(deviceId)).put("play", play).toString()))
    }

    private fun device(d: JSONObject) = SpotifyDevice(
        id = d.optString("id"),
        name = d.optString("name"),
        type = d.optString("type"),
        active = d.optBoolean("is_active"),
        volume = d.optInt("volume_percent", -1),
    )

    private fun artists(list: JSONArray?): String {
        if (list == null) return ""
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("name")?.ifBlank { null } }.joinToString(", ")
    }

    /** The smallest picture that is still at least [want] pixels wide, or the biggest there is. */
    private fun bestImage(images: JSONArray?, want: Int): String? {
        if (images == null || images.length() == 0) return null
        var best: String? = null
        var bestWidth = Int.MAX_VALUE
        var biggest: String? = null
        var biggestWidth = -1
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val url = image.optString("url")
            if (url.isBlank()) continue
            val width = image.optInt("width", 0)
            if (width >= want && width < bestWidth) {
                best = url
                bestWidth = width
            }
            if (width > biggestWidth) {
                biggest = url
                biggestWidth = width
            }
        }
        return best ?: biggest
    }

    private companion object {
        const val API = "https://api.spotify.com/v1"
    }
}

/** Cover pictures, downloaded once and kept in memory (the cache is sized in bytes, so a weak TV can keep it small). */
object SpotifyImages {
    @Volatile
    private var cache = newCache(12 * 1024 * 1024)

    private fun newCache(bytes: Int) = object : LruCache<String, Bitmap>(bytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Sets how much memory the pictures may use (call before the first [load]). */
    fun configure(bytes: Int) {
        cache = newCache(bytes)
    }

    /** Blocking: a downscaled picture (longest side at most [maxSide]) or null. */
    fun load(url: String, maxSide: Int = 400): Bitmap? {
        cache.get(url)?.let { return it }
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                if (connection.responseCode != 200) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size > 4_000_000) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                if (bitmap != null) cache.put(url, bitmap)
                bitmap
            } finally {
                connection.disconnect()
            }
        } catch (e: Throwable) {
            null
        }
    }
}
