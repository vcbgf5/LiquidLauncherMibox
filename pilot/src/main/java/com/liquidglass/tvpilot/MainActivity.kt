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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

/**
 * Liquid TV Pilot: a remote for the box (arrows, volume, power, typing, opening a link), sending photos
 * as wallpapers, and sharing the phone's screen to the TV. The box is found through the QR code that
 * Settings > Narzędzia > Pilot z telefonu shows on the TV. "Share" in the gallery or a browser also lands here.
 */
class MainActivity : ComponentActivity() {
    /** Something shared from another app, waiting to be sent once the box is known. */
    var shared by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shared = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
        setContent { PilotApp(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shared = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
    }
}

private val tabs = listOf("Pilot" to "◉", "Zdjęcia" to "▣", "Ekran" to "▭", "Box" to "⚙")

@Composable
private fun PilotApp(activity: MainActivity) {
    val context = LocalContext.current
    val store = remember { LinkStore(context) }
    var link by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(if (store.load() == null) 3 else 0) }
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

    // Photos and links shared from other apps.
    LaunchedEffect(activity.shared, link) {
        val intent = activity.shared
        val current = link
        if (intent != null && current != null) {
            activity.shared = null
            status = shareToBox(context, current, intent)
        } else if (intent != null) {
            status = "Połącz z Boxem, żeby wysłać to, co udostępniasz"
            tab = 3
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = Color(0xFF0B0C10),
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        0 -> RemoteTab(send)
                        1 -> PhotosTab(link, send, { status = it })
                        2 -> ScreenTab(link) { status = it }
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
                }
                if (status.isNotEmpty()) {
                    Text(status, color = Color(0xFF9DBBFF), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
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
private fun RowScope.Wide(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1, fontSize = 13.sp) }
}

@Composable
private fun Pad(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.width(96.dp).height(64.dp)) { Text(label, fontSize = 20.sp) }
}

@Composable
private fun RemoteTab(send: (String, String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Pad("▲") { send("key", "UP") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Pad("◀") { send("key", "LEFT") }
            Pad("OK") { send("key", "OK") }
            Pad("▶") { send("key", "RIGHT") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Pad("▼") { send("key", "DOWN") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wide("Wstecz") { send("key", "BACK") }
            Wide("Home") { send("key", "HOME") }
            Wide("⏯") { send("key", "PLAYPAUSE") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wide("Głośność −") { send("vol_down", "") }
            Wide("Wycisz") { send("mute", "") }
            Wide("Głośność +") { send("vol_up", "") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wide("Uśpij Box") { send("standby", "") }
            Wide("Włącz TV") { send("tv_on", "") }
            Wide("TV wł./wył.") { send("tv_power", "") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Tekst do wpisania na TV") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { send("text", text) }, modifier = Modifier.fillMaxWidth()) { Text("Wpisz na TV") }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adres do otwarcia na TV") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { send("open", url) }, modifier = Modifier.fillMaxWidth()) { Text("Otwórz na TV") }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Własne tapety", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Wybierz zdjęcia z telefonu: trafią do kategorii „Moje zdjęcia” na Boksie i od razu staną się tapetą. " +
                "Zdjęcia są zmniejszane i obracane do pionu przed wysłaniem. Możesz też użyć „Udostępnij” w galerii.",
            color = Color(0xFFB0B3C0),
        )
        Button(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Wysyłam…" else "Wybierz zdjęcia i wyślij")
        }
        OutlinedButton(onClick = { send("clear_photos", "") }, modifier = Modifier.fillMaxWidth()) {
            Text("Usuń moje zdjęcia z Boksa")
        }
        OutlinedButton(onClick = { send("wallpaper", "") }, modifier = Modifier.fillMaxWidth()) {
            Text("Następna tapeta")
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Udostępnianie ekranu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ekran telefonu pojawi się na telewizorze (obraz, bez dźwięku, kilka klatek na sekundę). " +
                "Na TV przycisk Wstecz kończy udostępnianie. Telefon i Box muszą być w tej samej sieci Wi-Fi.",
            color = Color(0xFFB0B3C0),
        )
        if (running) {
            Button(onClick = { ScreenShareService.stop(context) }, modifier = Modifier.fillMaxWidth()) { Text("Zatrzymaj udostępnianie") }
        } else {
            Button(
                onClick = {
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pokaż ekran telefonu na TV") }
        }
        Text(
            "Filmy i muzykę z telefonu najlepiej wysyłać wbudowanym Chromecastem Mi Box S (ikona „Prześlij” w aplikacji), " +
                "bo ten tryb pokazuje tylko obraz.",
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
        )
    }
}

// ---- Connection ------------------------------------------------------------------------------------------

@Composable
private fun LinkTab(link: BoxLink?, onLink: (BoxLink) -> Unit, onStatus: (String) -> Unit, onCheck: () -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(link?.host.orEmpty()) }
    var port by remember { mutableStateOf((link?.port ?: 8765).toString()) }
    var token by remember { mutableStateOf(link?.token.orEmpty()) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Połączenie z Boksem", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Na TV wejdź w Ustawienia → Narzędzia (adb) → Pilot z telefonu i zeskanuj pokazany kod. " +
                "Albo wpisz adres IP i kod ręcznie.",
            color = Color(0xFFB0B3C0),
        )
        Button(
            onClick = {
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Skanuj kod QR z telewizora") }
        OutlinedTextField(host, { host = it.trim() }, label = { Text("Adres IP Boksa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it.trim() }, label = { Text("Kod (z adresu, po t=)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val number = port.toIntOrNull()
                if (host.isBlank() || token.isBlank() || number == null) {
                    onStatus("Podaj adres, port i kod")
                } else {
                    onLink(BoxLink(host, number, token))
                    onStatus("Zapisano")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zapisz") }
        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Sprawdź połączenie") }
        Spacer(Modifier.height(8.dp))
    }
}
