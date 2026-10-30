package com.liquidglass.tvlauncher.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liquidglass.spotify.SpotifyClient
import com.liquidglass.spotify.SpotifyDevice
import com.liquidglass.spotify.SpotifyException
import com.liquidglass.spotify.SpotifyPlayback
import com.liquidglass.spotify.SpotifyTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher's Spotify session. The phone app (Liquid TV Pilot) signs in to Spotify and sends the session
 * here; from then on the launcher talks to the Spotify Web API itself and keeps the tokens fresh.
 * What it reads is shown by the "Spotify" widget and the player screen; playing needs Spotify Premium and a
 * device Spotify can see (the Spotify app running on this box).
 *
 * The state is a snapshot state, so everything that shows it redraws when it changes.
 */
object SpotifyHub {
    var connected: Boolean by mutableStateOf(false)
        private set

    /** The last thing read from Spotify, or null when nothing is playing. */
    var playback: SpotifyPlayback? by mutableStateOf(null)
        private set

    /** When [playback] was read ([SystemClock.elapsedRealtime]), to move the progress bar on between reads. */
    var playbackAt: Long by mutableLongStateOf(0L)
        private set

    /** The reason of the last failure, shown on the player screen; empty while all is well. */
    var problem: String by mutableStateOf("")
        private set

    private var settings: LauncherSettings? = null
    private var client: SpotifyClient? = null

    fun start(settings: LauncherSettings) {
        this.settings = settings
        rebuild()
    }

    private fun rebuild() {
        val s = settings ?: return
        val refresh = s.spotifyRefresh
        client = if (refresh.isNullOrBlank() || s.spotifyClientId.isBlank()) {
            null
        } else {
            SpotifyClient(s.spotifyClientId, SpotifyTokens(s.spotifyAccess.orEmpty(), refresh, s.spotifyExpiresAt)) { fresh -> s.saveSpotifyTokens(fresh) }
        }
        connected = client != null
        if (!connected) playback = null
    }

    /** Takes the session the phone sent (see [SpotifyTokens.toJson]); true when it was understood. */
    fun login(json: String): Boolean {
        val s = settings ?: return false
        val parsed = SpotifyTokens.fromJson(json) ?: return false
        s.spotifyClientId = parsed.first
        s.saveSpotifyTokens(parsed.second)
        problem = ""
        rebuild()
        return connected
    }

    fun logout() {
        settings?.clearSpotify()
        rebuild()
    }

    private fun explain(e: SpotifyException): String = when (e.status) {
        403 -> "Spotify odmówił: " + e.message.orEmpty() + ". Sterowanie wymaga konta Premium dodanego w panelu aplikacji Spotify."
        404 -> "Nie widać urządzenia Spotify. Uruchom aplikację Spotify na Boksie."
        429 -> "Spotify prosi o chwilę przerwy."
        0 -> "Brak połączenia ze Spotify (" + e.message.orEmpty() + ")"
        else -> e.message.orEmpty()
    }

    /** Runs a Spotify call off the main thread; a failure becomes [problem] and gives null. A lapsed sign-in disconnects. */
    suspend fun <T> call(block: (SpotifyClient) -> T): T? = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext null
        try {
            val result = block(c)
            problem = ""
            result
        } catch (e: SpotifyException) {
            if ((e.status == 400 || e.status == 401) && e.message.orEmpty().contains("refresh", ignoreCase = true)) {
                logout()
                problem = "Sesja Spotify wygasła: zaloguj się jeszcze raz w aplikacji Pilot."
            } else {
                problem = explain(e)
            }
            null
        }
    }

    suspend fun refreshPlayback() {
        if (client == null) return
        val read = call { it.playback() }
        // A failed read keeps what was shown; a null from a successful one (nothing playing) clears it.
        if (read != null || problem.isEmpty()) {
            playback = read
            playbackAt = SystemClock.elapsedRealtime()
            // Every caller of this already polls at its own pace, so Last.fm scrobbling just rides along.
            LastFmHub.observe(read)
        }
    }

    /** The Spotify device that is this box: its name matches the box's, else a TV, else the active one, else any. */
    suspend fun deviceForThisBox(context: Context): SpotifyDevice? {
        val list = call { it.devices() }.orEmpty()
        val names = listOfNotNull(
            runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull(),
            Build.MODEL,
            Build.DEVICE,
        ).filter { it.isNotBlank() }
        return list.firstOrNull { d -> names.any { it.equals(d.name, ignoreCase = true) } }
            ?: list.firstOrNull { it.type.equals("TV", ignoreCase = true) || it.type.equals("CastVideo", ignoreCase = true) }
            ?: list.firstOrNull { it.active }
            ?: list.firstOrNull()
    }
}
