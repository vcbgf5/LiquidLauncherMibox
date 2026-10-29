package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.WidgetType
import kotlinx.coroutines.delay

private const val STEP = 10

/** How often the wallpaper can change, in minutes. */
val WallpaperIntervals = listOf(1, 5, 10, 30, 60)

/** Idle minutes before the screensaver starts; 0 = never. */
val ScreensaverDelays = listOf(0, 1, 3, 5, 10, 15, 30)

private val CardColor = Color(0xEB1C1E26)
private val GroupColor = Color.White.copy(alpha = 0.07f)
private val Blue = Color(0xFF0A84FF)
private val Purple = Color(0xFFAF52DE)
private val Indigo = Color(0xFF5E5CE6)
private val Green = Color(0xFF30D158)
private val Gray = Color(0xFF8E8E93)
private val Orange = Color(0xFFFF9F0A)

/**
 * The launcher's own settings, opened from the gear in the top bar, in groups:
 *  - Appearance: how transparent the dock is (the dock behind the card updates live), how big
 *    its apps are, and a big clock in the middle instead of the small one,
 *  - Wallpapers: which category to rotate through, how often, and skipping to the next one,
 *  - Screensaver: when it starts, and showing it right now,
 *  - Updates: the current version, "check now", "update now" (only when [installLabel] is set)
 *    and whether the launcher updates by itself,
 *  - System: whether the box is online, the volume bar and power menu (they need the launcher's
 *    accessibility service), shortcuts to the Android screens (Wi-Fi, Bluetooth, display, sound...)
 *    and the Android system settings.
 *
 * Up/Down moves between rows, Left/Right changes a value, OK presses an action or flips a
 * switch. [categoryOptions] is (id, display name) pairs in the order they are cycled through.
 */
@Composable
fun SettingsDialog(
    versionLabel: String,
    transparencyPercent: Int,
    onTransparencyChange: (Int) -> Unit,
    dockSizePercent: Int,
    onDockSizeChange: (Int) -> Unit,
    bigClock: Boolean,
    onBigClockChange: (Boolean) -> Unit,
    tileLighting: Boolean,
    onTileLightingChange: (Boolean) -> Unit,
    clockContent: Int,
    onClockContentChange: (Int) -> Unit,
    clockPosition: Int,
    onClockPositionChange: (Int) -> Unit,
    statusIcons: Boolean,
    onStatusIconsChange: (Boolean) -> Unit,
    categoryOptions: List<Pair<String, String>>,
    categoryId: String,
    onCategoryChange: (String) -> Unit,
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit,
    photoCredit: String?,
    onNextWallpaper: () -> Unit,
    screensaverMinutes: Int,
    onScreensaverChange: (Int) -> Unit,
    onShowScreensaver: () -> Unit,
    updateStatus: String,
    installLabel: String?,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    silentUpdates: Boolean,
    onSilentUpdatesChange: (Boolean) -> Unit,
    adbStatus: String,
    onConnectAdb: () -> Unit,
    enabledWidgets: Set<String>,
    onWidgetToggle: (WidgetType, Boolean) -> Unit,
    calendarLinked: Boolean,
    calendarStatus: String,
    onRefreshCalendar: () -> Unit,
    onDisconnectCalendar: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    networkStatus: String,
    customVolume: Boolean,
    onCustomVolumeChange: (Boolean) -> Unit,
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenPowerMenu: () -> Unit,
    systemShortcuts: List<Pair<String, () -> Unit>>,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The main screen is a list of categories; OK opens one, Back comes out of it (and closes the list).
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    var lastCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }

    Dialog(
        onDismissRequest = { if (category != null) category = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dim the screen only a little, so the dock and wallpaper stay visible while they are adjusted.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.2f) }

        val noneLabel = stringResource(R.string.wallpaper_none)
        val categoryIndex = categoryOptions.indexOfFirst { it.first == categoryId }
        val categoryLabel = categoryOptions.getOrNull(categoryIndex)?.second ?: noneLabel

        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 500.dp)
                .clip(shape)
                .background(CardColor)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = category?.let { "‹  " + stringResource(it.titleRes) } ?: stringResource(R.string.settings_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (category == null) {
                    Text(text = versionLabel, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                when (category) {
                null -> CategoryList(
                    summaries = mapOf(
                        SettingsCategory.APPEARANCE to "",
                        SettingsCategory.WIDGETS to "",
                        SettingsCategory.WALLPAPERS to categoryLabel,
                        SettingsCategory.SCREENSAVER to if (screensaverMinutes <= 0) {
                            stringResource(R.string.screensaver_off)
                        } else {
                            stringResource(R.string.minutes_short, screensaverMinutes)
                        },
                        SettingsCategory.UPDATES to if (installLabel != null) stringResource(R.string.update_badge) else "",
                        SettingsCategory.SYSTEM to networkStatus,
                    ),
                    focusOn = lastCategory,
                    onOpen = {
                        lastCategory = it
                        category = it
                    },
                )
                SettingsCategory.APPEARANCE -> Group {
                    AdjustRow(
                        label = stringResource(R.string.settings_dock_transparency),
                        percent = transparencyPercent,
                        autoFocus = true,
                        onStep = { direction ->
                            onTransparencyChange((transparencyPercent + direction * STEP).coerceIn(0, 100))
                        },
                    )
                    Divider()
                    ChoiceRow(
                        label = stringResource(R.string.settings_dock_size),
                        value = "$dockSizePercent%",
                        onStep = { direction ->
                            onDockSizeChange(
                                (dockSizePercent + direction * STEP)
                                    .coerceIn(LauncherSettings.MIN_DOCK_SIZE, LauncherSettings.MAX_DOCK_SIZE),
                            )
                        },
                    )
                    Divider()
                    ToggleRow(stringResource(R.string.settings_big_clock), bigClock, onBigClockChange)
                    Divider()
                    ChoiceRow(
                        label = stringResource(R.string.settings_clock_content),
                        value = stringResource(
                            when (clockContent) {
                                CLOCK_TIME_ONLY -> R.string.clock_content_time
                                CLOCK_TIME_DATE_STATUS -> R.string.clock_content_status
                                else -> R.string.clock_content_date
                            },
                        ),
                        onStep = { direction -> onClockContentChange((clockContent + direction).mod(3)) },
                    )
                    Divider()
                    ChoiceRow(
                        label = stringResource(R.string.settings_clock_position),
                        value = stringResource(if (clockPosition == CLOCK_POS_CENTER) R.string.clock_position_center else R.string.clock_position_high),
                        onStep = { onClockPositionChange(if (clockPosition == CLOCK_POS_CENTER) CLOCK_POS_HIGH else CLOCK_POS_CENTER) },
                    )
                    Divider()
                    ToggleRow(stringResource(R.string.settings_status_icons), statusIcons, onStatusIconsChange)
                    Divider()
                    ToggleRow(stringResource(R.string.settings_tile_lighting), tileLighting, onTileLightingChange)
                }

                SettingsCategory.WIDGETS -> Group {
                    WidgetType.values().forEachIndexed { index, type ->
                        if (index > 0) Divider()
                        ToggleRow(
                            label = stringResource(
                                when (type) {
                                    WidgetType.CALENDAR -> R.string.widget_pick_calendar
                                    WidgetType.AGENDA -> R.string.widget_pick_agenda
                                    WidgetType.NETWORK -> R.string.widget_pick_network
                                    WidgetType.BLUETOOTH -> R.string.widget_pick_bluetooth
                                    WidgetType.SYSTEM -> R.string.widget_pick_system
                                },
                            ),
                            checked = type.key in enabledWidgets,
                            onChange = { onWidgetToggle(type, it) },
                            autoFocus = index == 0,
                        )
                    }
                    Divider()
                    Note(calendarStatus, top = 10.dp)
                    Note(stringResource(R.string.widgets_calendar_help))
                    ActionRow(label = stringResource(R.string.widgets_refresh), arrow = false, onClick = onRefreshCalendar)
                    if (calendarLinked) {
                        Divider()
                        ActionRow(label = stringResource(R.string.widgets_disconnect), arrow = false, onClick = onDisconnectCalendar)
                    }
                    Divider()
                    ActionRow(label = stringResource(R.string.widgets_request_permission), arrow = false, onClick = onRequestCalendarPermission)
                }
                SettingsCategory.WALLPAPERS -> Group {
                    ChoiceRow(
                        label = stringResource(R.string.settings_wallpaper),
                        value = categoryLabel,
                        autoFocus = true,
                        onStep = { direction ->
                            if (categoryOptions.isNotEmpty()) {
                                val from = if (categoryIndex < 0) 0 else categoryIndex
                                val next = (from + direction).mod(categoryOptions.size)
                                onCategoryChange(categoryOptions[next].first)
                            }
                        },
                    )
                    Divider()
                    ChoiceRow(
                        label = stringResource(R.string.settings_wallpaper_interval),
                        value = intervalLabel(intervalMinutes),
                        onStep = { direction ->
                            val i = WallpaperIntervals.indexOf(intervalMinutes).let { if (it < 0) 2 else it }
                            onIntervalChange(WallpaperIntervals[(i + direction).coerceIn(0, WallpaperIntervals.lastIndex)])
                        },
                    )
                    Divider()
                    ActionRow(label = stringResource(R.string.settings_wallpaper_next), arrow = false, onClick = onNextWallpaper)
                    if (photoCredit != null) {
                        Note(stringResource(R.string.settings_photo_by, photoCredit))
                    }
                }

                SettingsCategory.SCREENSAVER -> Group {
                    ChoiceRow(
                        label = stringResource(R.string.settings_screensaver),
                        autoFocus = true,
                        value = if (screensaverMinutes <= 0) {
                            stringResource(R.string.screensaver_off)
                        } else {
                            stringResource(R.string.minutes_short, screensaverMinutes)
                        },
                        onStep = { direction ->
                            val i = ScreensaverDelays.indexOf(screensaverMinutes).let { if (it < 0) 2 else it }
                            onScreensaverChange(ScreensaverDelays[(i + direction).coerceIn(0, ScreensaverDelays.lastIndex)])
                        },
                    )
                    Divider()
                    ActionRow(label = stringResource(R.string.settings_screensaver_show), arrow = false, onClick = onShowScreensaver)
                }

                SettingsCategory.UPDATES -> Group {
                    Note(updateStatus, top = 10.dp)
                    ActionRow(label = stringResource(R.string.settings_update_check), arrow = false, autoFocus = true, onClick = onCheckUpdates)
                    if (installLabel != null) {
                        Divider()
                        ActionRow(label = installLabel, arrow = false, onClick = onInstallUpdate)
                    }
                    Divider()
                    ToggleRow(stringResource(R.string.settings_update_auto), autoCheckUpdates, onAutoCheckChange)
                    Divider()
                    ToggleRow(stringResource(R.string.settings_silent_updates), silentUpdates, onSilentUpdatesChange)
                    Note(stringResource(R.string.settings_silent_help))
                    ActionRow(label = stringResource(R.string.settings_connect_adb), arrow = false, onClick = onConnectAdb)
                    if (adbStatus.isNotEmpty()) Note(adbStatus)
                }

                SettingsCategory.SYSTEM -> Group {
                    Note(networkStatus, top = 10.dp)
                    Note(stringResource(if (accessibilityOn) R.string.settings_a11y_on else R.string.settings_a11y_off))
                    if (!accessibilityOn) {
                        ActionRow(label = stringResource(R.string.settings_open_a11y), arrow = true, autoFocus = true, onClick = onOpenAccessibility)
                        Divider()
                    }
                    ToggleRow(stringResource(R.string.settings_custom_volume), customVolume, onCustomVolumeChange, autoFocus = accessibilityOn)
                    Divider()
                    ActionRow(label = stringResource(R.string.settings_power_menu), arrow = false, onClick = onOpenPowerMenu)
                    systemShortcuts.forEach { (label, open) ->
                        Divider()
                        ActionRow(
                            label = label,
                            arrow = true,
                            onClick = {
                                onDismiss()
                                open()
                            },
                        )
                    }
                    Divider()
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
        }
    }
}

@Composable
private fun intervalLabel(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.hour_short) else stringResource(R.string.minutes_short, minutes)

/** The settings are split into these categories; the main screen lists them. */
private enum class SettingsCategory(val titleRes: Int, val tint: Color) {
    APPEARANCE(R.string.group_appearance, Blue),
    WIDGETS(R.string.group_widgets, Orange),
    WALLPAPERS(R.string.group_wallpapers, Purple),
    SCREENSAVER(R.string.group_screensaver, Indigo),
    UPDATES(R.string.group_updates, Green),
    SYSTEM(R.string.group_system, Gray),
}

/** A rounded card holding a category's rows. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GroupColor)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** The main settings screen: one row per category, with the current state of it on the right. */
@Composable
private fun CategoryList(summaries: Map<SettingsCategory, String>, focusOn: SettingsCategory, onOpen: (SettingsCategory) -> Unit) {
    Group {
        SettingsCategory.values().forEachIndexed { index, category ->
            if (index > 0) Divider()
            CategoryRow(
                category = category,
                summary = summaries[category].orEmpty(),
                autoFocus = category == focusOn,
                onClick = { onOpen(category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, summary: String, autoFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    val ink = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(category.tint),
            contentAlignment = Alignment.Center,
        ) {
            val icon = Modifier.size(20.dp)
            when (category) {
                SettingsCategory.APPEARANCE -> SlidersIcon(icon)
                SettingsCategory.WIDGETS -> WidgetsIcon(icon)
                SettingsCategory.WALLPAPERS -> PhotoIcon(icon)
                SettingsCategory.SCREENSAVER -> MoonIcon(icon)
                SettingsCategory.UPDATES -> RefreshIcon(icon)
                SettingsCategory.SYSTEM -> GearIcon(icon)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(category.titleRes),
            color = ink,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (summary.isNotEmpty()) {
            Text(text = summary, color = ink.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        Text("›", color = ink, fontSize = 20.sp)
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}

/** Small grey explanatory text inside a group (not focusable). */
@Composable
private fun Note(text: String, top: Dp = 4.dp) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = top, bottom = 6.dp),
    )
}

/**
 * A row where Left/Right steps a value; shared by the slider, the pick-from-a-list rows and the
 * switches. [onOk] (the switches) makes OK do something too. The focused row is a white pill.
 */
@Composable
private fun StepRow(
    label: String,
    autoFocus: Boolean,
    onStep: (Int) -> Unit,
    onOk: (() -> Unit)? = null,
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
    val ink = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
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
            .then(if (onOk != null) Modifier.okAction(onOk) else Modifier)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontSize = 15.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
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
                    .width(120.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ink.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .width((120 * percent / 100f).dp)
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
private fun ChoiceRow(label: String, value: String, autoFocus: Boolean = false, onStep: (Int) -> Unit) {
    StepRow(label, autoFocus = autoFocus, onStep = onStep) { ink ->
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

/** An on/off switch like the ones on iOS; OK, Left and Right all flip it. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, autoFocus: Boolean = false) {
    StepRow(label, autoFocus = autoFocus, onStep = { onChange(!checked) }, onOk = { onChange(!checked) }) { ink ->
        val knobX by animateDpAsState(
            targetValue = if (checked) 20.dp else 0.dp,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "knob",
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(if (checked) Green else ink.copy(alpha = 0.25f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 2.dp + knobX)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun ActionRow(label: String, arrow: Boolean, autoFocus: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = if (arrow) "$label  ›" else label,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f),
        )
    }
}

// ---- Group icons, drawn in white on the coloured badge --------------------------------------

@Composable
private fun SlidersIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        listOf(0.2f to 0.65f, 0.5f to 0.3f, 0.8f to 0.55f).forEach { (y, knob) ->
            drawLine(Color.White, Offset(w * 0.05f, h * y), Offset(w * 0.95f, h * y), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            drawCircle(Blue, radius = w * 0.14f, center = Offset(w * knob, h * y))
            drawCircle(Color.White, radius = w * 0.14f, center = Offset(w * knob, h * y), style = Stroke(width = w * 0.08f))
        }
    }
}

@Composable
private fun WidgetsIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val gap = w * 0.1f
        val box = (w - gap) / 2f
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (cx, cy) ->
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx * (box + gap), cy * (box + gap)),
                size = Size(box, box),
                cornerRadius = CornerRadius(w * 0.12f),
            )
        }
    }
}

@Composable
private fun PhotoIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.05f, h * 0.12f),
            size = Size(w * 0.9f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.14f),
            style = Stroke(width = w * 0.1f),
        )
        drawCircle(Color.White, radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.36f))
        val hills = Path().apply {
            moveTo(w * 0.12f, h * 0.82f)
            lineTo(w * 0.38f, h * 0.5f)
            lineTo(w * 0.55f, h * 0.7f)
            lineTo(w * 0.68f, h * 0.58f)
            lineTo(w * 0.88f, h * 0.82f)
            close()
        }
        drawPath(hills, Color.White, style = Fill)
    }
}

@Composable
private fun MoonIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        drawCircle(Color.White, radius = w * 0.42f, center = Offset(w * 0.46f, size.height * 0.52f))
        // A second circle in the badge's colour bites out the crescent.
        drawCircle(Indigo, radius = w * 0.36f, center = Offset(w * 0.64f, size.height * 0.4f))
    }
}

@Composable
private fun RefreshIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawArc(
            color = Color.White,
            startAngle = -60f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            style = Stroke(width = w * 0.12f, cap = StrokeCap.Round),
        )
        val head = Path().apply {
            moveTo(w * 0.52f, h * 0.0f)
            lineTo(w * 0.86f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.36f)
            close()
        }
        drawPath(head, Color.White, style = Fill)
    }
}
