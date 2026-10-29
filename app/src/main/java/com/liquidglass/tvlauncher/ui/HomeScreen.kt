package com.liquidglass.tvlauncher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay

internal val ShelfColor = Color(0xFF050608)

private val DockBottomInset = 36.dp

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
    onLaunch: (AppInfo) -> Unit,
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
    val hero = painterResource(R.drawable.bg_hero)
    val backdrop = ImageBitmap.imageResource(R.drawable.bg_hero_blur)

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

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShelfColor)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val screenSize = IntSize(widthPx, heightPx)
        val dockInsetPx = with(density) { DockBottomInset.toPx() }

        val progress by animateFloatAsState(
            targetValue = if (showApps) 1f else 0f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            label = "screen",
        )

        // ---- Screen 1: home -------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -progress * heightPx }
                .focusProperties { canFocus = !showApps },
        ) {
            Image(
                painter = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            TopBar(
                accountName = accountName,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
                modifier = Modifier.align(Alignment.TopEnd),
            )

            if (hasFavorites) {
                FavoritesDock(
                    apps = favorites,
                    backdrop = backdrop,
                    screenSize = screenSize,
                    bottomInsetPx = dockInsetPx,
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
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
                    AppTile(
                        label = app.label,
                        art = app.tile,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (index == 0) gridFirst else null,
                        onClick = { onLaunch(app) },
                        onLongClick = { onOpenMenu(app) },
                    )
                }
            }
        }
    }
}
