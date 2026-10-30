package com.liquidglass.tvpilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liquid TV Pilot: a remote for the box (arrows, volume, power, typing, opening a link), sending photos
 * as wallpapers, sharing the phone's screen to the TV, and Spotify (sign-in and control; the launcher gets
 * the sign-in too and shows its own player). The box is found through the QR code that
 * Settings > Narzędzia > Pilot z telefonu shows on the TV. "Share" in the gallery or a browser also lands here.
 */
class MainActivity : ComponentActivity() {
    /** Something shared from another app, waiting to be sent once the box is known. */
    var shared by mutableStateOf<Intent?>(null)

    /** The address Spotify's sign-in page sent the browser back to (liquidtvpilot://spotify-callback?code=...). */
    var spotifyCallback by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shared = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
        spotifyCallback = callbackOf(intent)
        setContent { PilotApp(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shared = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
        callbackOf(intent)?.let { spotifyCallback = it }
    }

    private fun callbackOf(intent: Intent?): Uri? = intent?.data?.takeIf { it.scheme == "liquidtvpilot" }
}

private val navItems = listOf(
    NavItem("Pilot", PilotIcons.Remote),
    NavItem("Zdjęcia", PilotIcons.Photo),
    NavItem("Ekran", PilotIcons.Cast),
    NavItem("Spotify", PilotIcons.Music),
    NavItem("Box", PilotIcons.Tv),
)

private const val TAB_SPOTIFY = 3
private const val TAB_BOX = 4

@Composable
private fun PilotApp(activity: MainActivity) {
    val context = LocalContext.current
    val store = remember { LinkStore(context) }
    val spotify = remember { SpotifySession(context) }
    var spotifyVersion by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(if (store.load() == null) TAB_BOX else 0) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val send: (String, String) -> Unit = { action, value ->
        val current = link
        if (current == null) {
            status = "Najpierw połącz z Boxem (zakładka Box)"
        } else {
            scope.launch { status = Box.action(current, action, value) }
        }
    }

    // The status line fades after a few seconds.
    LaunchedEffect(status) {
        if (status.isNotEmpty()) {
            delay(5_000)
            status = ""
        }
    }

    // Photos and links shared from other apps.
    LaunchedEffect(activity.shared, link) {
        val intent = activity.shared
        val current = link
        if (intent != null && current != null) {
            activity.shared = null
            status = shareToBox(context, current, intent)
        } else if (intent != null) {
            status = "Połącz z Boxem, żeby wysłać to, co udostępniasz"
            tab = TAB_BOX
        }
    }

    // Spotify sent the browser back here: trade the code for tokens and give them to the launcher.
    LaunchedEffect(activity.spotifyCallback) {
        val uri = activity.spotifyCallback ?: return@LaunchedEffect
        activity.spotifyCallback = null
        tab = TAB_SPOTIFY
        status = "Kończę logowanie do Spotify…"
        val error = withContext(Dispatchers.IO) { spotify.finish(uri) }
        if (error != null) {
            status = error
        } else {
            spotifyVersion++
            status = "Połączono ze Spotify"
            val current = link
            val tokens = spotify.tokens()
            if (current != null && tokens != null) {
                val answer = Box.action(current, "spotify_login", tokens.toJson(spotify.clientId))
                status = "Połączono ze Spotify. " + answer.take(80)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.background(Brush.verticalGradient(listOf(Pal.top, Pal.bottom))),
            bottomBar = { GlassNav(navItems, tab) { tab = it } },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Header(link) { tab = TAB_BOX }
                Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    when (tab) {
                        0 -> RemoteTab(send)
                        1 -> PhotosTab(link, send) { status = it }
                        2 -> ScreenTab(link) { status = it }
                        TAB_SPOTIFY -> SpotifyTab(spotify, spotifyVersion, link, { status = it }, send)
                        else -> LinkTab(
                            link = link,
                            onLink = {
                                link = it
                                store.save(it)
                            },
                            onStatus = { status = it },
                            onCheck = { send("ping", "") },
                        )
                    }
                    if (status.isNotEmpty()) {
                        Text(
                            status,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .glass(RoundedCornerShape(50), strong = true)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The top line: the app's mark and name, and whether a box is linked (tapping it opens the connection tab). */
@Composable
private fun Header(link: BoxLink?, onBox: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Pal.brand), contentAlignment = Alignment.Center) {
            Icon(PilotIcons.Remote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text("Liquid TV Pilot", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.glass(RoundedCornerShape(50)).clickable(onClick = onBox).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (link != null) Pal.good else Pal.dim))
            Text(link?.host ?: "Niepołączony", color = Pal.muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** What "Share" from another app brings: pictures become wallpapers, a link opens on the TV, other text is typed there. */
private suspend fun shareToBox(context: Context, link: BoxLink, intent: Intent): String {
    if (intent.type?.startsWith("image/") == true) {
        @Suppress("DEPRECATION")
        val uris: List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
            )
        }
        var last = "Brak zdjęć"
        uris.forEachIndexed { index, uri ->
            last = Box.uploadPhoto(context, link, uri)
            if (uris.size > 1) last = "${index + 1}/${uris.size}: $last"
        }
        return last
    }
    val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    val address = Regex("""https?://\S+""").find(text)?.value
    return if (address != null) Box.action(link, "open", address) else Box.action(link, "text", text)
}

// ---- Remote -----------------------------------------------------------------------------------------------

@Composable
private fun BoxScope.PadArrow(at: Alignment, icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.align(at).padding(10.dp).size(64.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

/** The direction ring with OK in the middle. */
@Composable
private fun DPad(send: (String, String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val key: (String) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        send("key", it)
    }
    Box(
        modifier = Modifier
            .size(252.dp)
            .glass(CircleShape, strong = true),
        contentAlignment = Alignment.Center,
    ) {
        PadArrow(Alignment.TopCenter, PilotIcons.Up, "Góra") { key("UP") }
        PadArrow(Alignment.BottomCenter, PilotIcons.Down, "Dół") { key("DOWN") }
        PadArrow(Alignment.CenterStart, PilotIcons.Left, "Lewo") { key("LEFT") }
        PadArrow(Alignment.CenterEnd, PilotIcons.Right, "Prawo") { key("RIGHT") }
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(Pal.brand).clickable { key("OK") },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RemoteTab(send: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DPad(send)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(PilotIcons.Back, "Wstecz", { send("key", "BACK") })
            RoundAction(PilotIcons.Home, "Home", { send("key", "HOME") })
            RoundAction(PilotIcons.Play, "Odtwarzanie", { send("key", "PLAYPAUSE") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(PilotIcons.VolumeDown, "Ciszej", { send("vol_down", "") })
            RoundAction(PilotIcons.VolumeOff, "Wycisz", { send("mute", "") })
            RoundAction(PilotIcons.VolumeUp, "Głośniej", { send("vol_up", "") })
        }
        GlassCard {
            Heading(PilotIcons.Power, "Zasilanie")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PilotButton("Uśpij Box", { send("standby", "") }, Modifier.weight(1f))
                PilotButton("Włącz TV", { send("tv_on", "") }, Modifier.weight(1f))
                PilotButton("TV wł./wył.", { send("tv_power", "") }, Modifier.weight(1f))
            }
        }
        GlassCard {
            Heading(PilotIcons.Keyboard, "Wpisz na TV")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Tekst do wpisania") },
                modifier = Modifier.fillMaxWidth(),
            )
            PilotButton("Wpisz na TV", { send("text", text) }, Modifier.fillMaxWidth(), icon = PilotIcons.Send, primary = true)
        }
        GlassCard {
            Heading(PilotIcons.Link, "Otwórz na TV")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Adres do otwarcia") },
                modifier = Modifier.fillMaxWidth(),
            )
            PilotButton("Otwórz na TV", { send("open", url) }, Modifier.fillMaxWidth(), icon = PilotIcons.Send, primary = true)
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---- Photos -----------------------------------------------------------------------------------------------

@Composable
private fun PhotosTab(link: BoxLink?, send: (String, String) -> Unit, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val target = link
        if (target == null) {
            onStatus("Najpierw połącz z Boxem (zakładka Box)")
        } else if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                var last = ""
                uris.forEachIndexed { index, uri ->
                    onStatus("Wysyłam ${index + 1} z ${uris.size}…")
                    last = Box.uploadPhoto(context, target, uri)
                }
                onStatus(last)
                busy = false
            }
        }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Heading(PilotIcons.Photo, "Własne tapety")
            BodyText(
                "Wybierz zdjęcia z telefonu: trafią do kategorii „Moje zdjęcia” na Boksie i od razu staną się tapetą. " +
                    "Zdjęcia są zmniejszane i obracane do pionu przed wysłaniem. Możesz też użyć „Udostępnij” w galerii.",
            )
            PilotButton(
                if (busy) "Wysyłam…" else "Wybierz zdjęcia i wyślij",
                { picker.launch("image/*") },
                Modifier.fillMaxWidth(),
                icon = PilotIcons.Photo,
                primary = true,
                enabled = !busy,
            )
            PilotButton("Następna tapeta", { send("wallpaper", "") }, Modifier.fillMaxWidth(), icon = PilotIcons.Refresh)
            PilotButton("Usuń moje zdjęcia z Boksa", { send("clear_photos", "") }, Modifier.fillMaxWidth(), icon = PilotIcons.Trash)
        }
    }
}

// ---- Screen sharing --------------------------------------------------------------------------------------

@Composable
private fun ScreenTab(link: BoxLink?, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val running = ScreenShareService.running
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val target = link
        if (result.resultCode == Activity.RESULT_OK && data != null && target != null) {
            ScreenShareService.start(context, result.resultCode, data, target)
            onStatus("Ekran idzie na TV")
        } else {
            onStatus("Udostępnianie nie zostało zatwierdzone")
        }
    }
    val askProjection = {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(manager.createScreenCaptureIntent())
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { askProjection() }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Heading(PilotIcons.Cast, "Udostępnianie ekranu")
            BodyText(
                "Ekran telefonu pojawi się na telewizorze (obraz, bez dźwięku, kilka klatek na sekundę). " +
                    "Na TV przycisk Wstecz kończy udostępnianie. Telefon i Box muszą być w tej samej sieci Wi-Fi.",
            )
            if (running) {
                PilotButton("Zatrzymaj udostępnianie", { ScreenShareService.stop(context) }, Modifier.fillMaxWidth(), icon = PilotIcons.Pause, primary = true)
            } else {
                PilotButton(
                    "Pokaż ekran telefonu na TV",
                    {
                        if (link == null) {
                            onStatus("Najpierw połącz z Boxem (zakładka Box)")
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            askProjection()
                        }
                    },
                    Modifier.fillMaxWidth(),
                    icon = PilotIcons.Cast,
                    primary = true,
                )
            }
            Text(
                "Filmy i muzykę z telefonu najlepiej wysyłać wbudowanym Chromecastem Mi Box S (ikona „Prześlij” w aplikacji), " +
                    "bo ten tryb pokazuje tylko obraz.",
                color = Pal.dim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

// ---- Connection ------------------------------------------------------------------------------------------

@Composable
private fun LinkTab(link: BoxLink?, onLink: (BoxLink) -> Unit, onStatus: (String) -> Unit, onCheck: () -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(link?.host.orEmpty()) }
    var port by remember { mutableStateOf((link?.port ?: 8765).toString()) }
    var token by remember { mutableStateOf(link?.token.orEmpty()) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Heading(PilotIcons.Tv, "Połączenie z Boksem")
            BodyText(
                "Na TV wejdź w Ustawienia → Narzędzia (adb) → Pilot z telefonu i zeskanuj pokazany kod. " +
                    "Albo wpisz adres IP i kod ręcznie.",
            )
            PilotButton(
                "Skanuj kod QR z telewizora",
                {
                    val scanner = GmsBarcodeScanning.getClient(
                        context,
                        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
                    )
                    scanner.startScan()
                        .addOnSuccessListener { code ->
                            val parsed = BoxLink.parse(code.rawValue.orEmpty())
                            if (parsed != null) {
                                host = parsed.host
                                port = parsed.port.toString()
                                token = parsed.token
                                onLink(parsed)
                                onStatus("Zapisano Box ${parsed.host}")
                            } else {
                                onStatus("To nie jest kod z Boksa (Pilot z telefonu)")
                            }
                        }
                        .addOnFailureListener { onStatus("Skaner niedostępny (${it.message}). Wpisz dane ręcznie.") }
                },
                Modifier.fillMaxWidth(),
                icon = PilotIcons.Remote,
                primary = true,
            )
        }
        GlassCard {
            OutlinedTextField(host, { host = it.trim() }, label = { Text("Adres IP Boksa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it.trim() }, label = { Text("Kod (z adresu, po t=)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PilotButton(
                "Zapisz",
                {
                    val number = port.toIntOrNull()
                    if (host.isBlank() || token.isBlank() || number == null) {
                        onStatus("Podaj adres, port i kod")
                    } else {
                        onLink(BoxLink(host, number, token))
                        onStatus("Zapisano")
                    }
                },
                Modifier.fillMaxWidth(),
                icon = PilotIcons.Check,
            )
            PilotButton("Sprawdź połączenie", onCheck, Modifier.fillMaxWidth(), icon = PilotIcons.Refresh)
        }
        Spacer(Modifier.height(8.dp))
    }
}
