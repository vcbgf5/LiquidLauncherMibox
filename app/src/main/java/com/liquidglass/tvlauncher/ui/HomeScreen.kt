package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay

internal val ShelfColor = Color(0xFF050608)

private val DockBottomInset = 32.dp

/**
 * Two separate screens stacked vertically, like pages of a pager:
 *
 *  1. Home: the photo filling the screen, the clock/settings/account in
 *     the corner, and only the favorites dock at the bottom.
 *  2. All apps: nothing but the full app grid.
 *
 * Down on the dock slides the whole of screen 1 up and out while screen 2
 * rises into its place; Up from the grid's first row (or Back, or Home —
 * handled by the caller through [showApps] / [resetSignal]) reverses it.
 *
 * The slide is one critically-damped spring ([progress]) that everything
 * else is derived from: the photo trails behind the dock for depth, screen 1
 * fades as it leaves, and the grid's rows ripple in one after another.
 * Meanwhile the photo breathes with a very slow zoom, and fades up from
 * black when the launcher starts.
 *
 * Only the visible screen can take focus, so D-pad movement never wanders
 * onto tiles that are sitting off-screen.
 */
@Composable
fun HomeScreen(
    favorites: List<AppInfo>,
    allApps: List<AppInfo>,
    showApps: Boolean,
    onShowApps: (Boolean) -> Unit,
    resetSignal: Int,
    accountName: String?,
    wallpaper: WallpaperState,
    dockTransparency: Float,
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFavorites = favorites.isNotEmpty()
    val hasApps = allApps.isNotEmpty()
    val gridState = rememberLazyGridState()
    val dockFirst = remember { FocusRequester() }
    val gridFirst = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Whenever the screen changes (or Home is pressed), put focus on the
    // first tile of the screen that is now showing.
    LaunchedEffect(showApps, resetSignal, hasFavorites, hasApps) {
        if (showApps) gridState.scrollToItem(0)
        delay(90) // the screen that just became focusable needs a frame to say so
        if (showApps) {
            if (hasApps) runCatching { gridFirst.requestFocus() }
        } else if (hasFavorites) {
            runCatching { dockFirst.requestFocus() }
        }
    }

    // The photo breathes: a barely-there zoom, back and forth, forever.
    val zoomState = rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val heroFade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { heroFade.animateTo(1f, tween(900)) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShelfColor)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val screenSize = IntSize(widthPx, heightPx)
        val dockInsetPx = with(density) { DockBottomInset.toPx() }

        val progress by animateFloatAsState(
            targetValue = if (showApps) 1f else 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 200f),
            label = "screen",
        )

        // ---- Screen 1: home -------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -progress * heightPx
                    alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
                }
                .focusProperties { canFocus = !showApps },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val zoom = zoomState.value
                        scaleX = zoom
                        scaleY = zoom
                        alpha = heroFade.value
                        // Slides the other way while the screen moves up, so the photo
                        // trails behind the dock instead of travelling with it.
                        translationY = progress * heightPx * 0.45f
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

            TopBar(
                accountName = accountName,
                onOpenSettings = onOpenSettings,
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
                    zoom = { zoomState.value },
                    firstTileFocus = dockFirst,
                    onLaunch = onLaunch,
                    onOpenMenu = onOpenMenu,
                    onScrollDown = { if (hasApps) onShowApps(true) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = DockBottomInset),
                )
            }
        }

        // ---- Screen 2: all apps ---------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - progress) * heightPx }
                .focusProperties { canFocus = showApps }
                .onPreviewKeyEvent { event ->
                    if (showApps && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        // Up inside the grid moves focus; from the first row there is
                        // nowhere to go, which means "back to the home screen".
                        if (!focusManager.moveFocus(FocusDirection.Up)) onShowApps(false)
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
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(allApps, key = { _, app -> app.key }) { index, app ->
                    // Row by row: each row starts a little later than the one above it.
                    val row = (index / 6).coerceAtMost(4)
                    AppTile(
                        label = app.label,
                        art = app.tile,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (index == 0) gridFirst else null,
                        enterProgress = { ((progress - 0.12f - row * 0.06f) / 0.5f).coerceIn(0f, 1f) },
                        onClick = { rect -> onLaunch(app, rect) },
                        onLongClick = { onOpenMenu(app) },
                    )
                }
            }
        }
    }
}
