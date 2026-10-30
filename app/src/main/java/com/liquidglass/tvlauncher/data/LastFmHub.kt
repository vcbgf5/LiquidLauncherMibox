package com.liquidglass.tvlauncher.data

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liquidglass.spotify.SpotifyPlayback
import com.liquidglass.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The launcher's Last.fm session. The phone app (Liquid TV Pilot) signs in with a username and password
 * (Last.fm's "mobile session" auth) and sends over only the resulting session key; from then on this
 * watches [SpotifyHub.playback] (via [observe], called from [SpotifyHub.refreshPlayback]) and scrobbles
 * it the way Last.fm's own clients do: "now playing" the moment a track starts, a real scrobble once it
 * has played half its length or 4 minutes, whichever is shorter (and only past 30 seconds long).
 */
object LastFmHub {
    var connected: Boolean by mutableStateOf(false)
        private set

    /** The Last.fm username, for "Połączono jako …" in Settings. */
    var user: String by mutableStateOf("")
        private set

    /** The reason of the last failed call, shown in Settings; empty while all is well. */
    var problem: String by mutableStateOf("")
        private set

    private var settings: LauncherSettings? = null

    // What is being watched for scrobbling: the track key (artist|||title) and how long it has played.
    private var trackKey: String? = null
    private var trackDurationMs: Long = 0L
    private var playedMs: Long = 0L
    private var scrobbled: Boolean = false
    private var startedAt: Long = 0L
    private var lastTick: Long = 0L

    fun start(settings: LauncherSettings) {
        this.settings = settings
        rebuild()
    }

    private fun rebuild() {
        val s = settings
        connected = !s?.lastfmSession.isNullOrBlank()
        user = s?.lastfmUser.orEmpty()
    }

    /** Takes what the phone sent (session key and username as JSON); true when it was understood. */
    fun login(json: String): Boolean {
        val s = settings ?: return false
        return try {
            val o = JSONObject(json)
            s.lastfmSession = o.getString("k")
            s.lastfmUser = o.optString("u")
            problem = ""
            rebuild()
            connected
        } catch (e: Exception) {
            false
        }
    }

    fun logout() {
        settings?.clearLastFm()
        trackKey = null
        rebuild()
    }

    /**
     * Called whenever [SpotifyHub] reads fresh playback: keeps the "played so far" counter (so toggling
     * scrobbling off and on, or Last.fm being briefly unreachable, does not reset a track's progress),
     * and fires the network calls only while connected and switched on.
     */
    suspend fun observe(playback: SpotifyPlayback?) {
        val now = SystemClock.elapsedRealtime()
        if (playback == null || playback.title.isBlank() || playback.artist.isBlank()) {
            trackKey = null
            return
        }
        val key = "${playback.artist}|||${playback.title}"
        val changed = key != trackKey
        if (changed) {
            trackKey = key
            trackDurationMs = playback.durationMs
            playedMs = 0L
            scrobbled = false
            startedAt = System.currentTimeMillis() / 1000
        } else if (playback.playing) {
            playedMs += (now - lastTick).coerceAtLeast(0L)
        }
        lastTick = now

        val s = settings ?: return
        if (!connected || !s.lastfmScrobbling) return
        val apiKey = BuildConfig.LASTFM_API_KEY
        val secret = BuildConfig.LASTFM_API_SECRET
        val sessionKey = s.lastfmSession
        if (apiKey.isBlank() || secret.isBlank() || sessionKey.isNullOrBlank()) return
        val client = LastFmClient(apiKey, secret, sessionKey)

        if (changed) {
            runCatching {
                withContext(Dispatchers.IO) { client.updateNowPlaying(playback.artist, playback.title, playback.album) }
            }.onFailure { problem = it.message.orEmpty() }.onSuccess { problem = "" }
            return
        }
        if (!scrobbled && trackDurationMs > 30_000L && playedMs >= minOf(trackDurationMs / 2, 4 * 60_000L)) {
            scrobbled = true
            runCatching {
                withContext(Dispatchers.IO) { client.scrobble(playback.artist, playback.title, playback.album, startedAt) }
            }.onFailure { problem = it.message.orEmpty() }.onSuccess { problem = "" }
        }
    }
}
