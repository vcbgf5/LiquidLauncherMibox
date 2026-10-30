package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.Agenda
import com.liquidglass.tvlauncher.data.AgendaSource
import com.liquidglass.tvlauncher.data.NetworkKind
import com.liquidglass.tvlauncher.data.NowPlayingState
import com.liquidglass.tvlauncher.data.SpotifyHub
import com.liquidglass.tvlauncher.data.Weather
import com.liquidglass.tvlauncher.data.WidgetData
import com.liquidglass.tvlauncher.data.WidgetType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val PL = Locale("pl", "PL")
private val WEEKDAYS = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

/**
 * The page above the home screen: the date and time, and a row of widgets chosen in the settings
 * (calendar, upcoming events, network, Bluetooth, storage and memory). The widgets are cards you
 * can move between with Left/Right; OK on one opens the matching Android screen, or refreshes it.
 *
 * [entry] (0..1) is how far the page has slid in; the content fades up with it.
 * [firstFocus] goes to the first widget, or to the page itself when none is shown, so there is
 * always something focused to carry Up/Down/Back.
 */
@Composable
fun InfoPage(
    wallpaper: WallpaperState,
    widgets: List<WidgetType>,
    data: WidgetData,
    agenda: Agenda,
    weather: Weather?,
    nowPlaying: NowPlayingState,
    entry: () -> Float,
    firstFocus: FocusRequester,
    onWidget: (WidgetType) -> Unit,
    onWidgetLong: (WidgetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (widgets.isNotEmpty()) Modifier else Modifier.focusRequester(firstFocus).focusable()),
    ) {
        wallpaper.previous?.let { old ->
            Image(bitmap = old.blurred, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Image(
            bitmap = wallpaper.current.blurred,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (wallpaper.previous != null) wallpaper.fade.value else 1f },
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 36.dp, bottom = 26.dp)
                .graphicsLayer {
                    val e = entry()
                    alpha = e
                    translationY = (1f - e) * 40f * density
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            Box(modifier = Modifier.weight(1f).padding(horizontal = 48.dp)) {
                ClockBlock(now.value)
            }
            if (widgets.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(widgets, key = { _, type -> type.key }) { index, type ->
                        val focus = if (index == 0) firstFocus else null
                        when (type) {
                            WidgetType.CALENDAR -> WidgetCard(310.dp, stringResource(R.string.widget_calendar), focus, onClick = { onWidget(type) }) {
                                MonthCalendar(now.value)
                            }
                            WidgetType.AGENDA -> WidgetCard(330.dp, stringResource(R.string.widget_agenda_title), focus, onClick = { onWidget(type) }) {
                                AgendaContent(agenda, now.value)
                            }
                            WidgetType.WEATHER -> WidgetCard(
                                340.dp,
                                weather?.city?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.widget_weather_title_city, it) }
                                    ?: stringResource(R.string.widget_weather_title),
                                focus,
                                onClick = { onWidget(type) },
                            ) {
                                WeatherContent(weather)
                            }
                            WidgetType.NOW_PLAYING -> WidgetCard(
                                320.dp,
                                stringResource(R.string.widget_now_playing),
                                focus,
                                onClick = { onWidget(type) },
                                onLongClick = { onWidgetLong(type) },
                            ) {
                                NowPlayingContent(nowPlaying)
                            }
                            WidgetType.SPOTIFY -> WidgetCard(
                                330.dp,
                                stringResource(R.string.widget_spotify),
                                focus,
                                onClick = { onWidget(type) },
                                onLongClick = { onWidgetLong(type) },
                            ) {
                                SpotifyContent()
                            }
                            WidgetType.NETWORK -> WidgetCard(270.dp, stringResource(R.string.widget_network_title), focus, onClick = { onWidget(type) }) {
                                NetworkContent(data)
                            }
                            WidgetType.BLUETOOTH -> WidgetCard(270.dp, stringResource(R.string.widget_bluetooth_title), focus, onClick = { onWidget(type) }) {
                                BluetoothContent(data)
                            }
                            WidgetType.SYSTEM -> WidgetCard(290.dp, stringResource(R.string.widget_system_title), focus, onClick = { onWidget(type) }) {
                                SystemContent(data)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A glass card holding one widget. Focus lifts it and brightens its edge. */
@Composable
private fun WidgetCard(
    width: Dp,
    title: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "widgetScale",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .width(width)
            .height(236.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onLongClick != null) Modifier.okOrLongAction(onClick, onLongClick) else Modifier.okAction(onClick))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .background(Color.White.copy(alpha = if (focused) 0.17f else 0.09f))
            .border(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) 0.9f else 0.2f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun ClockBlock(nowMillis: Long) {
    val date = Date(nowMillis)
    Column {
        Text(
            text = SimpleDateFormat("HH:mm", PL).format(date),
            color = Color.White,
            fontSize = 84.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp,
        )
        Text(
            text = SimpleDateFormat("EEEE, d MMMM yyyy", PL).format(date).replaceFirstChar { it.uppercase() },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---- Calendar -----------------------------------------------------------------------------------

@Composable
private fun MonthCalendar(nowMillis: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val monthTitle = SimpleDateFormat("LLLL yyyy", PL).format(cal.time).replaceFirstChar { it.uppercase() }
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weeks = (offset + days + 6) / 7

    Text(monthTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
    Row {
        WEEKDAYS.forEach { Cell(it, header = true, today = false) }
    }
    repeat(weeks) { week ->
        Row {
            repeat(7) { column ->
                val day = week * 7 + column - offset + 1
                Cell(if (day in 1..days) day.toString() else "", header = false, today = day == today)
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean, today: Boolean) {
    Box(
        modifier = Modifier.width(38.dp).height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (today) {
            Box(modifier = Modifier.size(21.dp).clip(CircleShape).background(Color.White))
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontSize = if (header) 10.5.sp else 12.5.sp,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
            color = when {
                today -> Color(0xFF111111)
                header -> Color.White.copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.9f)
            },
        )
    }
}

@Composable
private fun AgendaContent(agenda: Agenda, nowMillis: Long) {
    if (agenda.events.isEmpty()) {
        val message = when {
            agenda.source == AgendaSource.NONE && agenda.needsPermission -> R.string.agenda_needs_permission
            agenda.source == AgendaSource.NONE && agenda.failed -> R.string.agenda_no_calendar
            agenda.source == AgendaSource.NONE -> R.string.agenda_not_connected
            agenda.failed -> R.string.agenda_failed
            else -> R.string.agenda_empty
        }
        Muted(stringResource(message))
        return
    }
    val dayFormat = SimpleDateFormat("EEE d MMM", PL)
    val timeFormat = SimpleDateFormat("HH:mm", PL)
    val todayKey = dayKey(nowMillis)
    var lastDay = ""
    agenda.events.take(6).forEach { event ->
        val key = dayKey(event.startMillis)
        val day = when {
            key == todayKey -> stringResource(R.string.agenda_today)
            key == dayKey(nowMillis + 24L * 3_600_000L) -> stringResource(R.string.agenda_tomorrow)
            else -> dayFormat.format(Date(event.startMillis)).replaceFirstChar { it.uppercase() }
        }
        if (day != lastDay) {
            lastDay = day
            Text(day, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (event.allDay) stringResource(R.string.agenda_all_day) else timeFormat.format(Date(event.startMillis)),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.width(56.dp),
            )
            Text(text = event.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun dayKey(millis: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))

// ---- Weather ----------------------------------------------------------------------------------------

@Composable
private fun WeatherContent(weather: Weather?) {
    if (weather == null) {
        Muted(stringResource(R.string.weather_none))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(weather.kind, weather.isDay, Modifier.size(58.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("${weather.temperature.roundToInt()}°", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
            Text(stringResource(Weather.textOf(weather.code)), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1)
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.weather_details, weather.feelsLike.roundToInt(), weather.windKmh.roundToInt(), weather.humidity),
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 11.5.sp,
        maxLines = 1,
    )
    Spacer(Modifier.height(8.dp))
    val labels = remember { SimpleDateFormat("EEE", PL) }
    val parser = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        weather.days.drop(1).take(4).forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val label = runCatching { labels.format(parser.parse(day.date)!!) }.getOrDefault("")
                Text(label.replaceFirstChar { it.uppercase() }, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                WeatherIcon(Weather.kindOf(day.code), true, Modifier.size(26.dp))
                Text("${day.max.roundToInt()}° / ${day.min.roundToInt()}°", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

// ---- Now playing -------------------------------------------------------------------------------------

@Composable
private fun NowPlayingContent(state: NowPlayingState) {
    val item = state.item
    if (!state.hasAccess) {
        Muted(stringResource(R.string.np_no_access))
        return
    }
    if (item == null) {
        Muted(stringResource(R.string.np_nothing))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))) {
            item.art?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.artist.isNotEmpty()) {
                Text(item.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = item.appLabel + " · " + stringResource(if (item.playing) R.string.np_playing else R.string.np_paused),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
    if (item.durationMs > 0) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f))
                    .height(5.dp)
                    .background(Color.White),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Muted(stringResource(R.string.np_hint))
}

// ---- Spotify -----------------------------------------------------------------------------------------

@Composable
private fun SpotifyContent() {
    if (!SpotifyHub.connected) {
        Muted(stringResource(R.string.spotify_connect_hint))
        return
    }
    val playback = SpotifyHub.playback
    if (playback == null || playback.title.isBlank()) {
        Muted(stringResource(R.string.spotify_nothing))
        return
    }
    val cover = rememberSpotifyCover(playback.imageUrl, 200)
    val tint by rememberSpotifyTint(cover)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f))))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(78.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))) {
                if (cover != null) Image(bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(playback.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (playback.artist.isNotEmpty()) {
                    Text(playback.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = (playback.device?.name?.let { it + " · " } ?: "") + stringResource(if (playback.playing) R.string.np_playing else R.string.np_paused),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        if (playback.durationMs > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((playback.progressMs.toFloat() / playback.durationMs).coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(Color(0xFF1ED760)),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Muted(stringResource(R.string.spotify_widget_hint))
}

// ---- Network, Bluetooth, system --------------------------------------------------------------------

@Composable
private fun NetworkContent(data: WidgetData) {
    val network = data.network
    val kindText = when (network.kind) {
        NetworkKind.WIFI -> R.string.status_wifi
        NetworkKind.ETHERNET -> R.string.status_ethernet
        NetworkKind.OTHER -> R.string.status_online
        NetworkKind.OFFLINE -> R.string.status_offline
    }
    Text(stringResource(kindText), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    if (network.kind == NetworkKind.OFFLINE) {
        Muted(stringResource(R.string.widget_network_offline))
        return
    }
    network.signalPercent?.let { Meter(stringResource(R.string.widget_signal), it / 100f, "$it%") }
    network.linkMbps?.let { Line(stringResource(R.string.widget_link_speed), "$it Mb/s") }
    network.ip?.let { Line(stringResource(R.string.widget_ip), it) }
}

@Composable
private fun BluetoothContent(data: WidgetData) {
    val bluetooth = data.bluetooth
    when (bluetooth.enabled) {
        null -> {
            Text(stringResource(R.string.widget_bluetooth_none), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        false -> {
            Text(stringResource(R.string.widget_bluetooth_off), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Muted(stringResource(R.string.widget_bluetooth_open))
        }
        true -> {
            Text(stringResource(R.string.widget_bluetooth_on), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            when {
                bluetooth.needsPermission -> Muted(stringResource(R.string.widget_bluetooth_permission))
                bluetooth.devices.isEmpty() -> Muted(stringResource(R.string.widget_bluetooth_no_devices))
                else -> {
                    Text(stringResource(R.string.widget_paired), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    bluetooth.devices.take(5).forEach {
                        Text(it, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemContent(data: WidgetData) {
    val system = data.system
    if (system.storageTotalBytes > 0) {
        val used = system.storageTotalBytes - system.storageFreeBytes
        Meter(
            stringResource(R.string.widget_storage),
            used.toFloat() / system.storageTotalBytes,
            stringResource(R.string.widget_free_of, gigabytes(system.storageFreeBytes), gigabytes(system.storageTotalBytes)),
        )
    }
    if (system.ramTotalBytes > 0) {
        val used = system.ramTotalBytes - system.ramFreeBytes
        Meter(
            stringResource(R.string.widget_ram),
            used.toFloat() / system.ramTotalBytes,
            stringResource(R.string.widget_free_of, gigabytes(system.ramFreeBytes), gigabytes(system.ramTotalBytes)),
        )
    }
    val hours = system.uptimeMillis / 3_600_000L
    val minutes = system.uptimeMillis / 60_000L % 60
    Line(stringResource(R.string.widget_uptime), stringResource(R.string.widget_uptime_value, hours, minutes))
    if (system.androidVersion.isNotEmpty()) Line("Android", "${system.androidVersion} · ${system.model}")
}

private fun gigabytes(bytes: Long): String = String.format(PL, "%.1f GB", bytes / 1_073_741_824.0)

@Composable
private fun Muted(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, lineHeight = 17.sp)
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
        Text(value, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A label, a thin bar filled to [fraction] and a value under it. */
@Composable
private fun Meter(label: String, fraction: Float, value: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp)
            Text(value, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(5.dp)
                    .background(Color.White),
            )
        }
    }
}

/** Wall-clock time, refreshed every 15 seconds (so the date also rolls over at midnight). */
@Composable
internal fun rememberNow(): State<Long> {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    return now
}
