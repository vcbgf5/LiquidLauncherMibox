package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.Agenda
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.Performance
import com.liquidglass.tvlauncher.data.isFolder
import com.liquidglass.tvlauncher.data.WidgetData
import com.liquidglass.tvlauncher.data.WidgetType
import com.liquidglass.tvlauncher.data.NetworkKind
import com.liquidglass.tvlauncher.data.NowPlayingState
import com.liquidglass.tvlauncher.data.SystemStatus
import com.liquidglass.tvlauncher.data.Weather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val ShelfColor = Color(0xFF050608)

/** What the big clock shows. */
const val CLOCK_TIME_DATE = 0
const val CLOCK_TIME_ONLY = 1
const val CLOCK_TIME_DATE_STATUS = 2
const val CLOCK_TIME_DATE_WEATHER = 3

/** Where the big clock sits on the photo. */
const val CLOCK_POS_HIGH = 0
const val CLOCK_POS_CENTER = 1

/** The pages, top to bottom: info above home above the app list. */
const val PAGE_INFO = 0
const val PAGE_HOME = 1
const val PAGE_APPS = 2

private val DockBottomInset = 32.dp
private const val GRID_COLUMNS = 6

/** The photo is always a little larger than the screen, so sliding it sideways never shows an edge. */
private const val HERO_ZOOM = 1.04f

/**
 * Three screens stacked vertically like pages of a pager ([page] is one of the PAGE_ constants):
 *
 *  - [PAGE_INFO] (top): the date and time and a row of widgets (calendar, events, network...).
 *    Reached with Up from the top bar; Down comes back.
 *  - [PAGE_HOME] (middle): the photo filling the screen, the clock/settings/account in the
 *    corner and, at the bottom, the favorites dock. Down on the dock goes to the app list.
 *  - [PAGE_APPS] (bottom): the full app grid. Up from its first row comes back.
 *
 * Back and Home are handled by the caller through [page] / [resetSignal].
 *
 * Where the app list starts is worked out from which tile has focus (first row), never from
 * the scroll position or from whether a focus move failed: after scrolling a long list the
 * grid is often left a few pixels off its start, and those checks then misfire.
 *
 * The slide is one critically-damped spring ([progress]) everything else is derived from: the
 * photo trails behind the dock for depth, the home screen fades as it leaves, the grid's rows
 * ripple in one after another. The photo breathes with a very slow zoom and fades up from
 * black at start. Only the visible page can take focus, and if focus is ever lost it is put
 * back on that page's first tile ([focusPaused] holds that off while a dialog is open).
 */
@Composable
fun HomeScreen(
    favorites: List<AppInfo>,
    allApps: List<AppInfo>,
    widgets: List<WidgetType>,
    widgetData: WidgetData,
    agenda: Agenda,
    weather: Weather?,
    nowPlaying: NowPlayingState,
    onWidget: (WidgetType) -> Unit,
    onWidgetLong: (WidgetType) -> Unit,
    page: Int,
    onPage: (Int) -> Unit,
    resetSignal: Int,
    focusPaused: Boolean,
    accountName: String?,
    updateAvailable: Boolean,
    wallpaper: WallpaperState,
    dockTransparency: Float,
    dockSize: Float,
    bigClock: Boolean,
    clockContent: Int,
    clockPosition: Int,
    status: SystemStatus?,
    weatherLine: String?,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenAccount: () -> Unit,
    restoreSignal: Int,
    parallaxOn: Boolean,
    idle: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val hasFavorites = favorites.isNotEmpty()
    val hasApps = allApps.isNotEmpty()
    val gridState = rememberLazyGridState()
    val infoFirst = remember { FocusRequester() }

    // One focus handle per tile, by app, so focus can be put back on the tile that was last used.
    val dockRequesters = remember { HashMap<String, FocusRequester>() }
    val gridRequesters = remember { HashMap<String, FocusRequester>() }
    fun dockRequester(app: AppInfo) = dockRequesters.getOrPut(app.key) { FocusRequester() }
    fun gridRequester(app: AppInfo) = gridRequesters.getOrPut(app.key) { FocusRequester() }

    // The tile an app was last started from (dock or list), for coming back to it.
    var lastUsedKey by remember { mutableStateOf<String?>(null) }
    var lastUsedInDock by remember { mutableStateOf(true) }
    // Where focus is along the dock, 0..1, which slides the photo a little the other way.
    val parallax = remember { Animatable(0f) }
    val currentIdle by rememberUpdatedState(idle)

    // Which tile / row has focus on the pages that need to know where their edges are.
    var focusedGridIndex by remember { mutableIntStateOf(0) }

    // Whether the page on screen currently holds focus (see the watchdog below).
    var pageHasFocus by remember { mutableStateOf(true) }

    val focusFirst: () -> Unit = {
        runCatching {
            when (page) {
                PAGE_INFO -> infoFirst.requestFocus()
                PAGE_HOME -> if (hasFavorites) dockRequester(favorites.first()).requestFocus()
                else -> if (hasApps) gridRequester(allApps.first()).requestFocus()
            }
        }
    }

    // Back from an app: focus returns to the tile it was started from, not to the first one.
    LaunchedEffect(restoreSignal) {
        val key = lastUsedKey
        if (restoreSignal == 0 || key == null) return@LaunchedEffect
        delay(280)
        runCatching {
            when {
                lastUsedInDock && page == PAGE_HOME -> {
                    favorites.firstOrNull { it.key == key }?.let { dockRequester(it).requestFocus() }
                }
                !lastUsedInDock && page == PAGE_APPS -> {
                    val index = allApps.indexOfFirst { it.key == key }
                    if (index >= 0) {
                        gridState.scrollToItem(index)
                        delay(60)
                        gridRequester(allApps[index]).requestFocus()
                    }
                }
                else -> Unit
            }
        }
    }

    // Whenever the page changes (or Home is pressed), put focus on its first tile.
    LaunchedEffect(page, resetSignal, hasFavorites, hasApps) {
        if (page == PAGE_APPS) gridState.scrollToItem(0)
        focusedGridIndex = 0
        delay(90) // the page that just became focusable needs a frame to say so
        focusFirst()
    }

    // Watchdog: if focus ends up nowhere (a tile scrolled away, a dialog closed...), D-pad keys
    // would do nothing at all, so take it back after a moment.
    LaunchedEffect(page, pageHasFocus, focusPaused) {
        if (!pageHasFocus && !focusPaused) {
            delay(400)
            focusFirst()
        }
    }

    // 0 = info, 1 = home, 2 = apps; every page sits at (its index - progress) screens down.
    val progress by animateFloatAsState(
        targetValue = page.toFloat(),
        animationSpec = spring(dampingRatio = 1f, stiffness = 200f),
        label = "screen",
    )

    // The photo breathes: a barely-there zoom, back and forth. It is refreshed only 15 times a
    // second, stands still while a page slides or a dialog is open, and is off in the light mode.
    val zoomState = rememberSlowOscillation(
        from = HERO_ZOOM,
        to = HERO_ZOOM + 0.06f,
        periodMillis = 26_000,
        fps = 15,
        enabled = !Performance.lite,
        paused = { abs(progress - 1f) > 0.001f || currentIdle },
    )
    LaunchedEffect(parallaxOn, favorites.size) { if (!parallaxOn || favorites.size < 2) parallax.animateTo(0f) }
    val heroFade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { heroFade.animateTo(1f, tween(900)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShelfColor)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val screenWidth = maxWidth // a nested Box's scope can't reach maxWidth itself
        val screenHeight = maxHeight
        val heightPx = with(density) { maxHeight.roundToPx() }
        val screenSize = IntSize(widthPx, heightPx)
        val dockInsetPx = with(density) { DockBottomInset.toPx() }

        // ---- Page 1: home ----------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (1f - progress) * heightPx
                    alpha = (1f - abs(progress - 1f) * 1.15f).coerceIn(0f, 1f)
                    // Fade each draw op, not a full-screen offscreen copy of the page, every frame.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .focusProperties { canFocus = page == PAGE_HOME }
                .onFocusChanged { if (page == PAGE_HOME) pageHasFocus = it.hasFocus },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val zoom = zoomState.floatValue
                        scaleX = zoom
                        scaleY = zoom
                        alpha = heroFade.value
                        // Slides against the page while it moves, so the photo trails behind
                        // the dock instead of travelling with it, and drifts the other way as
                        // focus moves along the dock.
                        translationY = (progress - 1f) * heightPx * 0.45f
                        translationX = -parallax.value * widthPx * 0.012f
                    },
            ) {
                // Cross-fade between wallpapers: the old one stays under the new one while it fades in.
                wallpaper.previous?.let { old ->
                    Image(
                        bitmap = old.full,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Image(
                    bitmap = wallpaper.current.full,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (wallpaper.previous != null) wallpaper.fade.value else 1f },
                )
            }

            if (bigClock) {
                // The big clock instead of the small one: in the middle of the photo, above the dock.
                val now = rememberNow()
                val extraLine = when (clockContent) {
                    CLOCK_TIME_DATE_STATUS -> statusLine(status)
                    CLOCK_TIME_DATE_WEATHER -> weatherLine
                    else -> null
                }
                val high = clockPosition == CLOCK_POS_HIGH
                GlassClock(
                    widthDp = screenWidth,
                    nowMillis = now.value,
                    scale = 0.14f,
                    showDate = clockContent != CLOCK_TIME_ONLY,
                    extraLine = extraLine,
                    modifier = Modifier
                        .align(if (high) Alignment.TopCenter else Alignment.Center)
                        .then(if (high) Modifier.padding(top = screenHeight * 0.13f) else Modifier)
                        .riseIn(delayMillis = 250, distance = 16.dp, fromScale = 0.96f),
                )
            }

            TopBar(
                accountName = accountName,
                updateAvailable = updateAvailable,
                showClock = !bigClock,
                status = status,
                onOpenWifi = onOpenWifi,
                onOpenBluetooth = onOpenBluetooth,
                onUp = { onPage(PAGE_INFO) },
                onOpenSettings = onOpenSettings,
                onOpenPower = onOpenPower,
                onOpenAccount = onOpenAccount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .riseIn(delayMillis = 350, distance = 12.dp, fromScale = 1f),
            )

            if (hasFavorites) {
                FavoritesDock(
                    apps = favorites,
                    backdrop = wallpaper.current.blurred,
                    previousBackdrop = wallpaper.previous?.blurred,
                    backdropFade = { wallpaper.fade.value },
                    screenSize = screenSize,
                    bottomInsetPx = dockInsetPx,
                    transparency = dockTransparency,
                    sizeFactor = dockSize,
                    zoom = { zoomState.floatValue },
                    shiftX = { -parallax.value * widthPx * 0.012f },
                    requesterFor = ::dockRequester,
                    onFocusedIndex = { index ->
                        if (parallaxOn && favorites.size > 1) {
                            scope.launch { parallax.animateTo(index.toFloat() / (favorites.size - 1) * 2f - 1f, spring(dampingRatio = 1f, stiffness = 60f)) }
                        }
                    },
                    onLaunch = { app, rect ->
                        lastUsedKey = app.key
                        lastUsedInDock = true
                        onLaunch(app, rect)
                    },
                    onOpenMenu = onOpenMenu,
                    onScrollDown = { if (hasApps) onPage(PAGE_APPS) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = DockBottomInset),
                )
            }
        }

        // ---- Page 2: all apps ------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (2f - progress) * heightPx }
                .focusProperties { canFocus = page == PAGE_APPS }
                .onFocusChanged { if (page == PAGE_APPS) pageHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    // Up from the first row goes back home. Anything else is left to the normal
                    // D-pad focus movement (return false), which scrolls the grid as needed.
                    if (page == PAGE_APPS && event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionUp && focusedGridIndex < GRID_COLUMNS
                    ) {
                        onPage(PAGE_HOME)
                        true
                    } else {
                        false
                    }
                },
        ) {
            wallpaper.previous?.let { old ->
                Image(
                    bitmap = old.blurred,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(GRID_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(allApps, key = { _, app -> app.key }) { index, app ->
                    // Row by row: each row starts a little later than the one above it.
                    val row = (index / GRID_COLUMNS).coerceAtMost(4)
                    AppTile(
                        label = app.label,
                        art = app.tile,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = gridRequester(app),
                        enterProgress = { ((progress - 1.12f - row * 0.06f) / 0.5f).coerceIn(0f, 1f) },
                        onFocusChange = { if (it) focusedGridIndex = index },
                        onClick = { rect ->
                            if (app.isFolder) {
                                onOpenFolder(app.className)
                            } else {
                                lastUsedKey = app.key
                                lastUsedInDock = false
                                onLaunch(app, rect)
                            }
                        },
                        onLongClick = { onOpenMenu(app) },
                    )
                }
            }
        }

        // ---- Page 0: date, time and the widgets (above home) ----------------------
        InfoPage(
            wallpaper = wallpaper,
            widgets = widgets,
            data = widgetData,
            agenda = agenda,
            weather = weather,
            nowPlaying = nowPlaying,
            entry = { ((0.95f - progress) / 0.7f).coerceIn(0f, 1f) },
            firstFocus = infoFirst,
            onWidget = onWidget,
            onWidgetLong = onWidgetLong,
            modifier = Modifier
                .graphicsLayer { translationY = (0f - progress) * heightPx }
                .focusProperties { canFocus = page == PAGE_INFO }
                .onFocusChanged { if (page == PAGE_INFO) pageHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    // The page is one row of widgets, so Down always goes back to the home screen.
                    if (page == PAGE_INFO && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onPage(PAGE_HOME)
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

/** "Wi-Fi · Bluetooth": the one-line status shown under the big clock. */
@Composable
private fun statusLine(status: SystemStatus?): String? {
    status ?: return null
    val network = stringResource(
        when (status.network) {
            NetworkKind.WIFI -> R.string.status_wifi
            NetworkKind.ETHERNET -> R.string.status_ethernet
            NetworkKind.OTHER -> R.string.status_online
            NetworkKind.OFFLINE -> R.string.status_offline
        },
    )
    return if (status.bluetooth == true) network + " · " + stringResource(R.string.status_bluetooth) else network
}
