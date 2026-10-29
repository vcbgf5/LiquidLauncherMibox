package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.liquidglass.tvlauncher.R
import kotlinx.coroutines.delay

private const val STEP = 10

/** How often the wallpaper can change, in minutes. */
val WallpaperIntervals = listOf(1, 5, 10, 30, 60)

/**
 * The launcher's own settings, opened from the gear in the top bar:
 *  - how transparent the dock is (the dock behind the card updates live),
 *  - which wallpaper category to rotate through, and how often,
 *  - skipping to the next wallpaper right now,
 *  - updates: the current version, "check now", "update now" (only when [installLabel] is set)
 *    and whether the launcher checks by itself,
 *  - a way into the Android system settings.
 *
 * Left/Right changes a value, OK presses an action. [categoryOptions] is
 * (id, display name) pairs in the order they are cycled through.
 */
@Composable
fun SettingsDialog(
    transparencyPercent: Int,
    onTransparencyChange: (Int) -> Unit,
    categoryOptions: List<Pair<String, String>>,
    categoryId: String,
    onCategoryChange: (String) -> Unit,
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit,
    photoCredit: String?,
    onNextWallpaper: () -> Unit,
    updateStatus: String,
    installLabel: String?,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dim the screen only a little, so the dock and wallpaper stay visible while they are adjusted.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.2f) }

        val noneLabel = stringResource(R.string.wallpaper_none)
        val categoryIndex = categoryOptions.indexOfFirst { it.first == categoryId }
        val categoryLabel = categoryOptions.getOrNull(categoryIndex)?.second ?: noneLabel

        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(Color(0xE62A2C36))
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp),
            )
            AdjustRow(
                label = stringResource(R.string.settings_dock_transparency),
                percent = transparencyPercent,
                autoFocus = true,
                onStep = { direction ->
                    onTransparencyChange((transparencyPercent + direction * STEP).coerceIn(0, 100))
                },
            )
            ChoiceRow(
                label = stringResource(R.string.settings_wallpaper),
                value = categoryLabel,
                onStep = { direction ->
                    if (categoryOptions.isNotEmpty()) {
                        val from = if (categoryIndex < 0) 0 else categoryIndex
                        val next = (from + direction).mod(categoryOptions.size)
                        onCategoryChange(categoryOptions[next].first)
                    }
                },
            )
            ChoiceRow(
                label = stringResource(R.string.settings_wallpaper_interval),
                value = intervalLabel(intervalMinutes),
                onStep = { direction ->
                    val i = WallpaperIntervals.indexOf(intervalMinutes).let { if (it < 0) 2 else it }
                    onIntervalChange(WallpaperIntervals[(i + direction).coerceIn(0, WallpaperIntervals.lastIndex)])
                },
            )
            ActionRow(label = stringResource(R.string.settings_wallpaper_next), arrow = false, onClick = onNextWallpaper)
            if (photoCredit != null) {
                Text(
                    text = stringResource(R.string.settings_photo_by, photoCredit),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 6.dp),
                )
            }
            Text(
                text = updateStatus,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
            )
            ActionRow(label = stringResource(R.string.settings_update_check), arrow = false, onClick = onCheckUpdates)
            if (installLabel != null) {
                ActionRow(label = installLabel, arrow = false, onClick = onInstallUpdate)
            }
            ChoiceRow(
                label = stringResource(R.string.settings_update_auto),
                value = stringResource(if (autoCheckUpdates) R.string.on_label else R.string.off_label),
                onStep = { onAutoCheckChange(!autoCheckUpdates) },
            )
            ActionRow(
                label = stringResource(R.string.settings_system),
                arrow = true,
                onClick = {
                    onDismiss()
                    onOpenSystemSettings()
                },
            )
        }
    }
}

@Composable
private fun intervalLabel(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.hour_short) else stringResource(R.string.minutes_short, minutes)

/** A row where Left/Right steps a value; shared by the slider and the pick-from-a-list rows. */
@Composable
private fun StepRow(
    label: String,
    autoFocus: Boolean,
    onStep: (Int) -> Unit,
    control: @Composable (ink: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    val ink = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else when (event.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    else -> false
                }
            }
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontSize = 14.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
        control(ink)
    }
}

@Composable
private fun AdjustRow(label: String, percent: Int, autoFocus: Boolean, onStep: (Int) -> Unit) {
    StepRow(label, autoFocus, onStep) { ink ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = ink, fontSize = 18.sp)
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(110.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ink.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .width((110 * percent / 100f).dp)
                        .height(6.dp)
                        .background(ink),
                )
            }
            Text("$percent%", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
            Text("›", color = ink, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, value: String, onStep: (Int) -> Unit) {
    StepRow(label, autoFocus = false, onStep = onStep) { ink ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = ink, fontSize = 18.sp)
            Text(
                text = value,
                color = ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Text("›", color = ink, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ActionRow(label: String, arrow: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (arrow) "$label  ›" else label,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f),
        )
    }
}
