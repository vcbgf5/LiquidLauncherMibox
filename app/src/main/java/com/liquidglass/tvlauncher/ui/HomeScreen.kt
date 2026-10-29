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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay
import kotlin.math.abs

internal val ShelfColor = Color(0xFF050608)

/** The pages, top to bottom: info above home above the app list. */
const val PAGE_INFO = 0
const val PAGE_HOME = 1
const val PAGE_APPS = 2

private val DockBottomInset = 32.dp
private const val GRID_COLUMNS = 6

/**
 * Three screens stacked vertically like pages of a pager ([page] is one of the PAGE_ constants):
 *
 *  - [PAGE_INFO] (top): the date, a calendar and "continue watching".
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
    watchNext: List<AppInfo>,
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
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFavorites = favorites.isNotEmpty()
    val hasApps = allApps.isNotEmpty()
    val gridState = rememberLazyGridState()
    val dockFirst = remember { FocusRequester() }
    val gridFirst = remember { FocusRequester() }
    val infoFirst = remember { FocusRequester() }

    // Which tile / row has focus on the pages that need to know where their edges are.
    var focusedGridIndex by remember { mutableIntStateOf(0) }

    // Whether the page on screen currently holds focus (see the watchdog below).
    var pageHasFocus by remember { mutableStateOf(true) }

    val focusFirst: () -> Unit = {
        runCatching {
            when (page) {
                PAGE_INFO -> infoFirst.requestFocus()
                PAGE_HOME -> if (hasFavorites) dockFirst.requestFocus()
                else -> if (hasApps) gridFirst.requestFocus()
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

    // The photo breathes: a barely-there zoom, back and forth, forever. It is refreshed only
    // 20 times a second and stands still while a page slides, so the slide gets all the GPU.
    val zoomState = rememberSlowOscillation(
        from = 1f,
        to = 1.06f,
        periodMillis = 26_000,
        paused = { abs(progress - page) > 0.001f },
    )
    val heroFade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { heroFade.animateTo(1f, tween(900)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShelfColor)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val screenWidth = maxWidth // a nested Box's scope can't reach maxWidth itself
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
                        // the dock instead of travelling with it.
                        translationY = (progress - 1f) * heightPx * 0.45f
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
                GlassClock(
                    widthDp = screenWidth,
                    nowMillis = now.value,
                    scale = 0.14f,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .riseIn(delayMillis = 250, distance = 16.dp, fromScale = 0.96f),
                )
            }

            TopBar(
                accountName = accountName,
                updateAvailable = updateAvailable,
                showClock = !bigClock,
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
                    firstTileFocus = dockFirst,
                    onLaunch = onLaunch,
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
                        focusRequester = if (index == 0) gridFirst else null,
                        enterProgress = { ((progress - 1.12f - row * 0.06f) / 0.5f).coerceIn(0f, 1f) },
                        onFocusChange = { if (it) focusedGridIndex = index },
                        onClick = { rect -> onLaunch(app, rect) },
                        onLongClick = { onOpenMenu(app) },
                    )
                }
            }
        }

        // ---- Page 0: date and what to watch (above home) -------------------------
        InfoPage(
            wallpaper = wallpaper,
            watchNext = watchNext,
            entry = { ((0.95f - progress) / 0.7f).coerceIn(0f, 1f) },
            firstFocus = infoFirst,
            onLaunch = onLaunch,
            onOpenMenu = onOpenMenu,
            modifier = Modifier
                .graphicsLayer { translationY = (0f - progress) * heightPx }
                .focusProperties { canFocus = page == PAGE_INFO }
                .onFocusChanged { if (page == PAGE_INFO) pageHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    // The page has a single row of tiles, so Down always goes back to the home screen.
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
