package com.liquidglass.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.liquidglass.tvlauncher.MediaListenerService

/** What a media app is playing right now, with the controls to drive it. */
class NowPlaying(
    val appLabel: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val art: ImageBitmap?,
    private val controller: MediaController,
) {
    fun playPause() {
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }

    fun next() = controller.transportControls.skipToNext()

    fun previous() = controller.transportControls.skipToPrevious()
}

/** [hasAccess] is false until the user lets the launcher see notifications (media sessions need it). */
class NowPlayingState(val hasAccess: Boolean, val item: NowPlaying?) {
    companion object {
        val NONE = NowPlayingState(false, null)
    }
}

object NowPlayingReader {
    private var artKey = ""
    private var artImage: ImageBitmap? = null

    fun read(context: Context): NowPlayingState {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return NowPlayingState.NONE
        val controllers = try {
            manager.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
        } catch (_: SecurityException) {
            return NowPlayingState.NONE
        } catch (_: Throwable) {
            return NowPlayingState.NONE
        }
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null }
            ?: controllers.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
            ?: return NowPlayingState(true, null)
        val metadata = chosen.metadata ?: return NowPlayingState(true, null)
        val state = chosen.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        var position = state?.position ?: 0L
        if (playing && state != null) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val app = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(chosen.packageName, 0)).toString()
        } catch (_: Throwable) {
            chosen.packageName
        }
        val key = "$title|$artist|${chosen.packageName}"
        if (key != artKey) {
            artKey = key
            val raw = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            artImage = raw?.let { small(it) }?.asImageBitmap()
        }
        return NowPlayingState(
            true,
            NowPlaying(app, title, artist, playing, position.coerceAtLeast(0L), metadata.getLong(MediaMetadata.METADATA_KEY_DURATION), artImage, chosen),
        )
    }

    /** Covers are often huge; the widget only needs a thumbnail. */
    private fun small(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= 240) return source
        val scale = 240f / longest
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }
}
