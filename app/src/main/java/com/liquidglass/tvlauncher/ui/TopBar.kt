package com.liquidglass.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.liquidglass.tvlauncher.R
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.tvlauncher.data.NetworkKind
import com.liquidglass.tvlauncher.data.SystemStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clock, settings and Google account, top right of the home screen.
 * The icons are drawn in code so the app needs no icon library. With [minimal] the row of round
 * buttons is replaced by a single menu button (three lines) that unfolds the same choices as a list.
 */
@Composable
fun TopBar(
    accountName: String?,
    updateAvailable: Boolean,
    showClock: Boolean,
    status: SystemStatus?,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenAccount: () -> Unit,
    minimal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(15_000)
        }
    }

    if (minimal) {
        MinimalTopBar(
            time = time,
            accountName = accountName,
            updateAvailable = updateAvailable,
            showClock = showClock,
            status = status,
            onUp = onUp,
            onOpenWifi = onOpenWifi,
            onOpenBluetooth = onOpenBluetooth,
            onOpenQuick = onOpenPower,
            onOpenSettings = onOpenSettings,
            onOpenAccount = onOpenAccount,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier
            // Anything above the top bar is the info page: Up from here goes there.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUp()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClock) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 10f)),
            )
        }
        // Network and Bluetooth, like the stock launcher's status icons; OK opens Android's own screen for them.
        if (status != null) {
            TopBarButton(onClick = onOpenWifi) { NetworkIcon(status.network, Modifier.size(24.dp)) }
            if (status.bluetooth != null) {
                TopBarButton(onClick = onOpenBluetooth) { BluetoothIcon(status.bluetooth, Modifier.size(24.dp)) }
            }
        }
        TopBarButton(onClick = onOpenSettings, onLongClick = onOpenPower) {
            Box(modifier = Modifier.size(24.dp)) {
                GearIcon(Modifier.fillMaxSize())
                if (updateAvailable) {
                    // A small red dot: a newer version is waiting in the settings.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5A4F)),
                    )
                }
            }
        }
        TopBarButton(onClick = onOpenAccount) { AccountIcon(accountName) }
    }
}

@Composable
private fun TopBarButton(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val longFired = remember { booleanArrayOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "buttonScale",
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "buttonGlow")

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f + 0.22f * glow))
            .border(1.5.dp, Color.White.copy(alpha = 0.25f + 0.65f * glow), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val repeats = event.nativeKeyEvent.repeatCount
                        if (repeats == 0) longFired[0] = false
                        // The first auto-repeat of a held key is the long press.
                        if (repeats == 1 && !longFired[0] && onLongClick != null) {
                            longFired[0] = true
                            onLongClick()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val wasLong = longFired[0]
                        longFired[0] = false
                        if (!wasLong) onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick?.invoke() })
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Wi-Fi (arcs), cable (three linked boxes) or offline (dimmed Wi-Fi, crossed out). */
@Composable
private fun NetworkIcon(kind: NetworkKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (kind == NetworkKind.ETHERNET) {
            val box = w * 0.28f
            val corner = CornerRadius(w * 0.05f)
            drawRoundRect(Color.White, Offset(w / 2 - box / 2, h * 0.08f), Size(box, box * 0.8f), corner)
            drawRoundRect(Color.White, Offset(w * 0.06f, h * 0.68f), Size(box, box * 0.8f), corner)
            drawRoundRect(Color.White, Offset(w * 0.94f - box, h * 0.68f), Size(box, box * 0.8f), corner)
            val line = w * 0.07f
            drawLine(Color.White, Offset(w / 2, h * 0.08f + box * 0.8f), Offset(w / 2, h * 0.52f), strokeWidth = line)
            drawLine(Color.White, Offset(w * 0.06f + box / 2, h * 0.52f), Offset(w * 0.94f - box / 2, h * 0.52f), strokeWidth = line)
            drawLine(Color.White, Offset(w * 0.06f + box / 2, h * 0.52f), Offset(w * 0.06f + box / 2, h * 0.68f), strokeWidth = line)
            drawLine(Color.White, Offset(w * 0.94f - box / 2, h * 0.52f), Offset(w * 0.94f - box / 2, h * 0.68f), strokeWidth = line)
            return@Canvas
        }
        val online = kind != NetworkKind.OFFLINE
        val color = Color.White.copy(alpha = if (online) 1f else 0.45f)
        val cx = w / 2f
        val cy = h * 0.84f
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        listOf(0.30f, 0.54f, 0.78f).forEach { r ->
            drawArc(
                color = color,
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - w * r, cy - w * r),
                size = Size(w * r * 2, w * r * 2),
                style = stroke,
            )
        }
        drawCircle(color, radius = w * 0.075f, center = Offset(cx, cy))
        if (!online) {
            drawLine(Color.White, Offset(w * 0.12f, h * 0.1f), Offset(w * 0.88f, h * 0.92f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        }
    }
}

/** The Bluetooth rune; dimmed and crossed out when Bluetooth is off. */
@Composable
private fun BluetoothIcon(on: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = if (on) 1f else 0.45f)
        val rune = Path().apply {
            moveTo(w * 0.27f, h * 0.30f)
            lineTo(w * 0.73f, h * 0.70f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.73f, h * 0.30f)
            lineTo(w * 0.27f, h * 0.70f)
        }
        drawPath(rune, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (!on) {
            drawLine(Color.White, Offset(w * 0.12f, h * 0.1f), Offset(w * 0.88f, h * 0.92f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun GearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val ringRadius = side * 0.27f
        val ringWidth = side * 0.13f
        val outer = ringRadius + ringWidth / 2f
        val toothWidth = ringWidth * 0.95f
        val toothLength = side * 0.12f
        drawCircle(Color.White, radius = ringRadius, style = Stroke(width = ringWidth))
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = center) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(center.x - toothWidth / 2f, center.y - outer - toothLength + ringWidth * 0.2f),
                    size = Size(toothWidth, toothLength),
                    cornerRadius = CornerRadius(ringWidth * 0.25f),
                )
            }
        }
    }
}

@Composable
private fun AccountIcon(accountName: String?) {
    if (accountName.isNullOrBlank()) {
        // No account known yet: a plain person silhouette.
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White, radius = w * 0.19f, center = Offset(w / 2f, h * 0.36f))
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.14f, h * 0.60f),
                size = Size(w * 0.72f, h * 0.60f),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF7C9CFF), Color(0xFFB36BFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = accountName.first().uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

// ---- Minimal style ----------------------------------------------------------------------------------------

private class TopMenuEntry(val label: String, val state: String, val dot: Boolean = false, val onClick: () -> Unit)

/**
 * The quiet version of the top bar: the clock and one button with three lines. OK on it unfolds a small
 * glass list (Wi-Fi, Bluetooth, account, quick settings, settings) right under it; Back or choosing a row
 * folds it again. Up from the button still goes to the widgets page.
 */
@Composable
private fun MinimalTopBar(
    time: String,
    accountName: String?,
    updateAvailable: Boolean,
    showClock: Boolean,
    status: SystemStatus?,
    onUp: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenQuick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonFocus = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }

    // Back folds the list first (this handler is newer than the launcher's own, so it is asked first).
    BackHandler(enabled = expanded) {
        expanded = false
        runCatching { buttonFocus.requestFocus() }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(60)
            runCatching { firstRow.requestFocus() }
        }
    }

    val network = stringResource(
        when (status?.network) {
            NetworkKind.WIFI -> R.string.status_wifi
            NetworkKind.ETHERNET -> R.string.status_ethernet
            NetworkKind.OTHER -> R.string.status_online
            NetworkKind.OFFLINE, null -> R.string.status_offline
        },
    )
    val bluetooth = stringResource(
        when (status?.bluetooth) {
            true -> R.string.quick_on
            false -> R.string.quick_off
            null -> R.string.quick_none
        },
    )
    val entries = listOf(
        TopMenuEntry(stringResource(R.string.shortcut_wifi), network) { onOpenWifi() },
        TopMenuEntry(stringResource(R.string.shortcut_bluetooth), bluetooth) { onOpenBluetooth() },
        TopMenuEntry(stringResource(R.string.topbar_account), accountName ?: stringResource(R.string.topbar_account_none)) { onOpenAccount() },
        TopMenuEntry(stringResource(R.string.quick_title), "") { onOpenQuick() },
        TopMenuEntry(
            stringResource(R.string.topbar_settings),
            if (updateAvailable) stringResource(R.string.update_badge) else "",
            dot = updateAvailable,
        ) { onOpenSettings() },
    )

    Column(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.onPreviewKeyEvent { event ->
                // Anything above the top bar is the info page: Up from here goes there.
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    onUp()
                    true
                } else {
                    false
                }
            },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showClock) {
                Text(
                    text = time,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 10f)),
                )
            }
            MenuButton(
                focusRequester = buttonFocus,
                open = expanded,
                dot = updateAvailable && !expanded,
                onClick = { expanded = !expanded },
            )
        }
        if (expanded) {
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(300.dp)
                    .riseIn(delayMillis = 0, distance = 8.dp, fromScale = 0.97f)
                    .clip(shape)
                    .background(LocalTheme.current.glass.menu)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                    .padding(8.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    TopMenuRow(
                        entry = entry,
                        focusRequester = if (index == 0) firstRow else null,
                        onClick = {
                            expanded = false
                            entry.onClick()
                        },
                    )
                }
            }
        }
    }
}

/** Three short lines; nothing around them until it is focused. */
@Composable
private fun MenuButton(focusRequester: FocusRequester, open: Boolean, dot: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (focused) 0.26f else if (open) 0.14f else 0f))
            .border(1.5.dp, Color.White.copy(alpha = if (focused) 0.9f else 0f), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawLine(Color.White, Offset(w * 0.08f, h * y), Offset(w * 0.92f, h * y), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
        }
        if (dot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A4F)),
            )
        }
    }
}

@Composable
private fun TopMenuRow(entry: TopMenuEntry, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.label, color = ink, fontSize = 15.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
        if (entry.state.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.dot) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5A4F)))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                }
                Text(entry.state, color = ink.copy(alpha = 0.6f), fontSize = 12.5.sp, maxLines = 1)
            }
        }
    }
}
