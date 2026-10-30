package com.liquidglass.tvpilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.spotify.SpotifyAuth
import com.liquidglass.spotify.SpotifyClient
import com.liquidglass.spotify.SpotifyDevice
import com.liquidglass.spotify.SpotifyException
import com.liquidglass.spotify.SpotifyImages
import com.liquidglass.spotify.SpotifyPlayback
import com.liquidglass.spotify.SpotifyPlaylist
import com.liquidglass.spotify.SpotifyProfile
import com.liquidglass.spotify.SpotifyTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** The phone's Spotify session: the Client ID, the tokens and the sign-in that is in progress in the browser. */
class SpotifySession(context: Context) {
    private val prefs = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    /** A Client ID typed in the app, used when the build has none (the CI build brings the project's own). */
    var clientIdOverride: String
        get() = prefs.getString("client", "").orEmpty()
        set(value) {
            prefs.edit().putString("client", value.trim()).apply()
        }

    val clientId: String get() = clientIdOverride.ifBlank { BuildConfig.SPOTIFY_CLIENT_ID }

    val hasTokens: Boolean get() = prefs.getString("refresh", null) != null

    fun tokens(): SpotifyTokens? {
        val refresh = prefs.getString("refresh", null) ?: return null
        return SpotifyTokens(prefs.getString("access", "").orEmpty(), refresh, prefs.getLong("expires", 0L))
    }

    fun save(tokens: SpotifyTokens) {
        prefs.edit().putString("access", tokens.access).putString("refresh", tokens.refresh).putLong("expires", tokens.expiresAt).apply()
    }

    fun client(): SpotifyClient? = tokens()?.let { SpotifyClient(clientId, it) { fresh -> save(fresh) } }

    fun logout() {
        prefs.edit().remove("access").remove("refresh").remove("expires").apply()
    }

    /** The address to open in the browser to sign in, or null when there is no Client ID yet. */
    fun beginLogin(): String? {
        val id = clientId
        if (id.isBlank()) return null
        val verifier = SpotifyAuth.newVerifier()
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val state = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("verifier", verifier).putString("state", state).apply()
        return SpotifyAuth.authorizeUrl(id, SpotifyAuth.challenge(verifier), state)
    }

    /** Blocking: turns the address Spotify sent the browser back to into tokens. Returns an error text, or null when signed in. */
    fun finish(uri: Uri): String? {
        val error = uri.getQueryParameter("error")
        if (error != null) return if (error == "access_denied") "Logowanie do Spotify zostało anulowane" else "Spotify: $error"
        val verifier = prefs.getString("verifier", null)
        val state = prefs.getString("state", null)
        val code = uri.getQueryParameter("code")
        if (verifier == null || code == null || state == null || uri.getQueryParameter("state") != state) {
            return "Logowanie do Spotify nie pasuje do rozpoczętego (spróbuj jeszcze raz)"
        }
        return try {
            save(SpotifyAuth.exchange(clientId, code, verifier))
            prefs.edit().remove("verifier").remove("state").apply()
            null
        } catch (e: SpotifyException) {
            "Spotify odrzucił logowanie: ${e.message.orEmpty()}"
        }
    }
}

private fun explain(e: SpotifyException): String = when (e.status) {
    403 -> "Spotify odmówił (${e.message.orEmpty()}). Sterowanie wymaga konta Premium, a konto musi być dodane w „User Management” aplikacji w panelu Spotify."
    404 -> "Nie ma aktywnego urządzenia Spotify. Otwórz aplikację Spotify na Boksie albo telefonie i spróbuj jeszcze raz."
    429 -> "Spotify prosi o chwilę przerwy (za dużo zapytań)."
    0 -> "Brak połączenia z internetem (${e.message.orEmpty()})"
    else -> e.message.orEmpty()
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun Cover(url: String?, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val image by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) { SpotifyImages.load(url, 400)?.asImageBitmap() }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner)).background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Icon(PilotIcons.Music, contentDescription = null, tint = Pal.dim, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun SpotifyTab(
    session: SpotifySession,
    version: Int,
    link: BoxLink?,
    onStatus: (String) -> Unit,
    sendToBox: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var connected by remember(version) { mutableStateOf(session.hasTokens) }
    var profile by remember { mutableStateOf<SpotifyProfile?>(null) }
    var playback by remember { mutableStateOf<SpotifyPlayback?>(null) }
    var fetchedAt by remember { mutableLongStateOf(0L) }
    var devices by remember { mutableStateOf<List<SpotifyDevice>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var pickedDevice by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf("") }
    var clientField by remember { mutableStateOf(session.clientIdOverride) }
    var volume by remember { mutableStateOf<Float?>(null) }

    /** Runs a Spotify call off the main thread; a failure becomes the [problem] line. A lapsed sign-in signs out. */
    suspend fun <T> call(block: (SpotifyClient) -> T): T? = withContext(Dispatchers.IO) {
        val client = session.client() ?: return@withContext null
        try {
            val result = block(client)
            problem = ""
            result
        } catch (e: SpotifyException) {
            if ((e.status == 400 || e.status == 401) && e.message.orEmpty().contains("refresh", ignoreCase = true)) {
                session.logout()
                connected = false
                problem = "Sesja Spotify wygasła. Zaloguj się jeszcze raz."
            } else {
                problem = explain(e)
            }
            null
        }
    }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        SpotifyImages.configure(10 * 1024 * 1024)
        profile = call { it.profile() }
        playlists = call { it.playlists() }.orEmpty()
        var round = 0
        while (true) {
            playback = call { it.playback() }
            fetchedAt = System.currentTimeMillis()
            if (round % 2 == 0) devices = call { it.devices() }.orEmpty()
            round++
            delay(4_000)
        }
    }

    fun act(block: (SpotifyClient) -> Unit) {
        scope.launch {
            call(block)
            delay(450)
            playback = call { it.playback() }
            fetchedAt = System.currentTimeMillis()
        }
    }

    val target: String? = pickedDevice ?: playback?.device?.id ?: devices.firstOrNull { it.active }?.id

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpotifyMark(40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Spotify", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (connected) (profile?.name?.let { "Zalogowano jako $it" } ?: "Zalogowano") else "Niepołączony",
                        color = Pal.muted,
                        fontSize = 13.sp,
                    )
                }
                if (connected) {
                    Text(
                        "Wyloguj",
                        color = Pal.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                            session.logout()
                            connected = false
                            profile = null
                            playback = null
                            playlists = emptyList()
                            devices = emptyList()
                        }.padding(8.dp),
                    )
                }
            }
            if (profile != null && profile?.premium == false) {
                Text("To konto nie jest Premium: Spotify pozwala sterować odtwarzaniem tylko z Premium.", color = Pal.bad, fontSize = 13.sp)
            }
        }

        if (problem.isNotEmpty()) {
            Text(problem, color = Pal.bad, fontSize = 13.sp, lineHeight = 18.sp)
        }

        if (!connected) {
            GlassCard {
                BodyText(
                    "Zaloguj się, żeby sterować muzyką z telefonu, a w launcherze na telewizorze mieć odtwarzacz z okładkami i playlistami. " +
                        "Potrzebne jest konto Spotify Premium.",
                )
                if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) {
                    OutlinedTextField(
                        value = clientField,
                        onValueChange = { clientField = it.trim() },
                        label = { Text("Client ID aplikacji ze Spotify") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PilotButton(
                    "Połącz ze Spotify",
                    icon = PilotIcons.Music,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) session.clientIdOverride = clientField
                        val url = session.beginLogin()
                        if (url == null) {
                            onStatus("Wpisz Client ID aplikacji ze Spotify")
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }
            GlassCard {
                Text("Jednorazowo w panelu Spotify", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                BodyText("W aplikacji na developer.spotify.com (Settings → Redirect URIs) dodaj dokładnie ten adres i zapisz:")
                SelectionContainer {
                    Text(SpotifyAuth.REDIRECT_URI, color = Pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                PilotButton(
                    "Kopiuj adres",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        clipboard.setText(AnnotatedString(SpotifyAuth.REDIRECT_URI))
                        onStatus("Skopiowano adres przekierowania")
                    },
                )
            }
            return@Column
        }

        // ---- now playing ----
        val current = playback
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(current?.imageUrl, Modifier.size(96.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        current?.title?.ifBlank { null } ?: "Nic nie gra",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(current?.artist.orEmpty(), color = Pal.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current?.device?.let { "na: ${it.name}" } ?: "Wybierz urządzenie niżej",
                        color = Pal.dim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (current != null && current.durationMs > 0) {
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        delay(500)
                        value = System.currentTimeMillis()
                    }
                }
                val position = (current.progressMs + if (current.playing) now - fetchedAt else 0L).coerceIn(0L, current.durationMs)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clock(position), color = Pal.dim, fontSize = 12.sp)
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(position.toFloat() / current.durationMs).height(5.dp).background(Pal.brand))
                    }
                    Text(clock(current.durationMs), color = Pal.dim, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundAction(
                    PilotIcons.Shuffle,
                    "Losowo",
                    onClick = { act { it.shuffle(!(current?.shuffle ?: false), target) } },
                    size = 52.dp,
                    tint = if (current?.shuffle == true) Pal.spotify else Color.White,
                )
                RoundAction(PilotIcons.Prev, "Poprzedni", onClick = { act { it.previous(target) } }, size = 52.dp)
                RoundAction(
                    if (current?.playing == true) PilotIcons.Pause else PilotIcons.Play,
                    if (current?.playing == true) "Pauza" else "Graj",
                    onClick = { act { if (current?.playing == true) it.pause(target) else it.play(deviceId = target) } },
                    size = 68.dp,
                )
                RoundAction(PilotIcons.Next, "Następny", onClick = { act { it.next(target) } }, size = 52.dp)
            }
            val device = current?.device
            if (device != null && device.volume >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(PilotIcons.VolumeDown, contentDescription = null, tint = Pal.muted, modifier = Modifier.size(20.dp))
                    Slider(
                        value = volume ?: device.volume.toFloat(),
                        onValueChange = { volume = it },
                        onValueChangeFinished = {
                            val chosen = (volume ?: device.volume.toFloat()).toInt()
                            volume = null
                            act { it.setVolume(chosen, device.id) }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pal.spotify, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(PilotIcons.VolumeUp, contentDescription = null, tint = Pal.muted, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ---- devices ----
        GlassCard {
            Text("Urządzenia", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (devices.isEmpty()) {
                BodyText("Nie widać żadnego urządzenia. Otwórz aplikację Spotify na Boksie (albo telefonie), a pojawi się tutaj.")
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { device ->
                        val chosen = device.id == target
                        Text(
                            device.name,
                            color = if (chosen) Color(0xFF0B0C10) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (chosen) Brush.linearGradient(listOf(Pal.spotify, Pal.spotify)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))))
                                .clickable {
                                    pickedDevice = device.id
                                    act { it.transfer(device.id, play = playback?.playing == true) }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        // ---- the launcher on the TV ----
        GlassCard {
            Text("Launcher na TV", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (link == null) {
                BodyText("Połącz telefon z Boksem (zakładka Box), a launcher dostanie Spotify i pokaże odtwarzacz z okładkami.")
            } else {
                BodyText("Wyślij logowanie do launchera, żeby miał widżet „Spotify” i pełnoekranowy odtwarzacz z okładkami i playlistami.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PilotButton(
                        "Wyślij do TV",
                        icon = PilotIcons.Send,
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val tokens = session.tokens()
                            if (tokens != null) sendToBox("spotify_login", tokens.toJson(session.clientId))
                        },
                    )
                    PilotButton("Otwórz na TV", icon = PilotIcons.Tv, modifier = Modifier.weight(1f), onClick = { sendToBox("spotify_open", "") })
                }
            }
        }

        // ---- Last.fm scrobbling ----
        LastFmCard(sendToBox, onStatus)

        // ---- playlists ----
        if (playlists.isNotEmpty()) {
            Text("Twoje playlisty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
            playlists.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { playlist ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { act { it.play(contextUri = playlist.uri, deviceId = target) } }
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Cover(playlist.imageUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                            Text(playlist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (playlist.tracks > 0) "${playlist.tracks} utworów" else playlist.owner,
                                color = Pal.dim,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
