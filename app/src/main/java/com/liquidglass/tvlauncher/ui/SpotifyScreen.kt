package com.liquidglass.tvlauncher.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.palette.graphics.Palette
import com.liquidglass.spotify.SpotifyClient
import com.liquidglass.spotify.SpotifyImages
import com.liquidglass.spotify.SpotifyPlaylist
import com.liquidglass.spotify.SpotifyTrack
import com.liquidglass.tvlauncher.data.Edition
import com.liquidglass.tvlauncher.data.SpotifyHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1ED760)
private val Night = Color(0xFF0A0B10)

/** The few icons of the player, drawn from path data (24 x 24), tinted by the Icon that shows them. */
private object PlayerIcons {
    private fun vector(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    val Play: ImageVector by lazy { vector("Play", "M8 5v14l11-7z") }
    val Pause: ImageVector by lazy { vector("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z") }
    val Next: ImageVector by lazy { vector("Next", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z") }
    val Prev: ImageVector by lazy { vector("Prev", "M6 6h2v12H6zm3.5 6l8.5 6V6z") }
    val Shuffle: ImageVector by lazy {
        vector(
            "Shuffle",
            "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z",
        )
    }
    val Music: ImageVector by lazy { vector("Music", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z") }
}

/** A Spotify picture, downloaded off the main thread (and kept in memory by [SpotifyImages]). */
@Composable
fun rememberSpotifyCover(url: String?, maxSide: Int = 400): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) { SpotifyImages.load(url, maxSide)?.asImageBitmap() }
    }
    return image
}

private val SpotifyDefaultTint = Color(0xFF1C2447)

/** A muted colour pulled from [cover], for a background that matches the song playing. */
@Composable
fun rememberSpotifyTint(cover: ImageBitmap?): androidx.compose.runtime.State<Color> = produceState(SpotifyDefaultTint, cover) {
    val bitmap = cover?.asAndroidBitmap()
    value = if (bitmap == null) {
        SpotifyDefaultTint
    } else {
        withContext(Dispatchers.Default) {
            try {
                val palette = Palette.from(bitmap).maximumColorCount(8).generate()
                Color(palette.getDarkMutedColor(palette.getMutedColor(SpotifyDefaultTint.toArgb())))
            } catch (e: Throwable) {
                SpotifyDefaultTint
            }
        }
    }
}

/** The progress of what is playing, moved on between reads so the bar runs smoothly. */
@Composable
private fun rememberProgressMs(): Long {
    val tick by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(500)
            value = SystemClock.elapsedRealtime()
        }
    }
    val p = SpotifyHub.playback ?: return 0L
    val elapsed = if (p.playing) tick - SpotifyHub.playbackAt else 0L
    return (p.progressMs + elapsed).coerceIn(0L, p.durationMs.coerceAtLeast(0L))
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun RoundButton(icon: ImageVector, size: Dp, tint: Color = Color.White, requester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "spotifyButton")
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusable()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color(0xFF101010) else tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun PillButton(label: String, requester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = if (focused) Color(0xFF101010) else Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusable()
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 22.dp, vertical = 11.dp),
    )
}

@Composable
private fun PlaylistTile(playlist: SpotifyPlaylist, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "playlistTile")
    val shape = RoundedCornerShape(16.dp)
    val cover = rememberSpotifyCover(playlist.imageUrl, 300)
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.9f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            if (cover != null) {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(PlayerIcons.Music, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(34.dp))
            }
        }
        Text(playlist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (playlist.tracks > 0) "${playlist.tracks} utworów" else playlist.owner,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackRow(index: Int, track: SpotifyTrack, playing: Boolean, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val cover = rememberSpotifyCover(track.imageUrl, 96)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else if (playing) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ink = if (focused) Color(0xFF101010) else Color.White
        Text(
            text = if (playing) "♪" else (index + 1).toString(),
            color = if (playing && !focused) SpotifyGreen else ink.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.width(26.dp),
        )
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f))) {
            if (cover != null) Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(track.name, color = if (playing && !focused) SpotifyGreen else ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = ink.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(clock(track.durationMs), color = ink.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

/**
 * The Spotify player, full screen: the big cover, title, progress and controls on the left, your playlists on the
 * right (OK opens a playlist's tracks; OK on a track plays it). It uses the session the phone app handed over
 * (see [SpotifyHub]) and plays on the Spotify device that is this box; [ensureDevice] finds that device, starting
 * the Spotify app on the box when it is not running. Back closes a playlist, then the player.
 */
@Composable
fun SpotifyScreen(ensureDevice: suspend () -> String?, onDismiss: () -> Unit) {
    val hub = SpotifyHub
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var open by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<SpotifyTrack>?>(null) }
    var note by remember { mutableStateOf("") }
    val playRequester = remember { FocusRequester() }
    val firstItem = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        SpotifyImages.configure(if (Edition.lite) 4 * 1024 * 1024 else 14 * 1024 * 1024)
        hub.refreshPlayback()
        playlists = hub.call { it.playlists() }.orEmpty()
        loading = false
    }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { playRequester.requestFocus() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_500)
            hub.refreshPlayback()
        }
    }
    LaunchedEffect(open) {
        tracks = null
        val playlist = open ?: return@LaunchedEffect
        tracks = hub.call { it.playlistTracks(playlist.id) } ?: emptyList()
        delay(120)
        runCatching { firstItem.requestFocus() }
    }

    /** Runs [block] on the Spotify device of this box (found, or the Spotify app started, when needed). */
    fun act(block: (SpotifyClient, String?) -> Unit) {
        scope.launch {
            note = ""
            val target = hub.playback?.device?.id ?: ensureDevice()
            if (target == null) {
                note = "Nie widać Spotify na tym Boksie. Uruchom aplikację Spotify i spróbuj jeszcze raz."
                return@launch
            }
            hub.call { block(it, target) }
            delay(500)
            hub.refreshPlayback()
        }
    }

    val playback = hub.playback
    val cover = rememberSpotifyCover(playback?.imageUrl, 500)
    val tint by rememberSpotifyTint(cover)
    val progress = rememberProgressMs()

    Dialog(
        onDismissRequest = { if (open != null) open = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint, Night, Night))),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                // ---- what is playing ----
                Column(
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .shadow(22.dp, RoundedCornerShape(20.dp), clip = false)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(PlayerIcons.Music, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(80.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        playback?.title?.ifBlank { null } ?: "Nic nie gra",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(playback?.artist.orEmpty(), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playback?.album.orEmpty(), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (playback != null && playback.durationMs > 0) {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(clock(progress), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(Modifier.fillMaxWidth((progress.toFloat() / playback.durationMs).coerceIn(0f, 1f)).height(4.dp).background(Color.White))
                            }
                            Text(clock(playback.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(PlayerIcons.Shuffle, 46.dp, tint = if (playback?.shuffle == true) SpotifyGreen else Color.White) {
                            act { c, d -> c.shuffle(playback?.shuffle != true, d) }
                        }
                        RoundButton(PlayerIcons.Prev, 50.dp) { act { c, d -> c.previous(d) } }
                        RoundButton(if (playback?.playing == true) PlayerIcons.Pause else PlayerIcons.Play, 66.dp, requester = playRequester) {
                            act { c, d ->
                                if (playback?.playing == true) c.pause(d) else c.play(deviceId = d)
                            }
                        }
                        RoundButton(PlayerIcons.Next, 50.dp) { act { c, d -> c.next(d) } }
                    }
                    playback?.device?.let {
                        Text("na: " + it.name, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    val message = note.ifEmpty { hub.problem }
                    if (message.isNotEmpty()) {
                        Text(message, color = Color(0xFFFF8A80), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }

                // ---- playlists, or the tracks of one ----
                val chosen = open
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (chosen == null) {
                        Text("Twoje playlisty", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (playlists.isEmpty()) {
                            Text(
                                if (loading) "Wczytuję…" else "Nie ma żadnych playlist (albo Spotify ich nie pokazał).",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(playlists, key = { _, item -> item.id }) { index, playlist ->
                                PlaylistTile(playlist, if (index == 0) firstItem else null) { open = playlist }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                chosen.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton("Odtwórz playlistę") { act { c, d -> c.play(contextUri = chosen.uri, deviceId = d) } }
                        }
                        val list = tracks
                        when {
                            list == null -> Text("Wczytuję utwory…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                            list.isEmpty() -> Text(
                                "Spotify nie pokazuje utworów tej playlisty (dostępne są tylko Twoje własne). Możesz ją odtworzyć w całości przyciskiem powyżej.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                            )
                            else -> LazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(list, key = { index, item -> index.toString() + item.uri }) { index, track ->
                                    TrackRow(index, track, playback?.trackUri == track.uri, if (index == 0) firstItem else null) {
                                        act { c, d -> c.play(contextUri = chosen.uri, trackUri = track.uri, deviceId = d) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
