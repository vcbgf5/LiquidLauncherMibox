package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay

internal val ShelfColor = Color(0xFF050608)

private val HeroHeight = 300.dp

// 20 top padding + 78 tile (130dp at 5:3) + 8 gap + 18 label + 8 bottom padding.
// Keep in sync with FavoritesDock / AppTile.
private val DockHeight = 132.dp

/**
 * tvOS-style layout: a photo hero on top, a glass dock of favorites
 * straddling its bottom edge, and the remaining apps in a scrollable grid.
 *
 * [homeSignal] ticks every time Home or Back is pressed; each tick scrolls
 * the grid back to the top and puts focus on the first favorite, which is
 * how the Apple TV "Home button returns to the top" behaviour works.
 */
@Composable
fun HomeScreen(
    favorites: List<AppInfo>,
    rest: List<AppInfo>,
    homeSignal: Int,
    onLaunch: (AppInfo) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val firstTileFocus = remember { FocusRequester() }
    val backdrop = ImageBitmap.imageResource(R.drawable.bg_hero_blur)
    val hasFavorites = favorites.isNotEmpty()

    LaunchedEffect(homeSignal, hasFavorites) {
        gridState.scrollToItem(0)
        if (hasFavorites) {
            delay(120) // let the dock finish its first layout before asking for focus
            runCatching { firstTileFocus.requestFocus() }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShelfColor)) {
        val density = LocalDensity.current
        val backdropSize = with(density) { IntSize(maxWidth.roundToPx(), HeroHeight.roundToPx()) }

        Column(modifier = Modifier.fillMaxSize()) {
            HeroBanner(modifier = Modifier.height(HeroHeight))

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = 48.dp,
                    end = 48.dp,
                    top = DockHeight / 2 + 24.dp,
                    bottom = 40.dp,
                ),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(rest, key = { it.key }) { app ->
                    AppTile(
                        label = app.label,
                        art = app.tile,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onLaunch(app) },
                        onLongClick = { onOpenMenu(app) },
                    )
                }
            }
        }

        if (hasFavorites) {
            FavoritesDock(
                apps = favorites,
                backdrop = backdrop,
                backdropSize = backdropSize,
                firstTileFocus = firstTileFocus,
                onLaunch = onLaunch,
                onOpenMenu = onOpenMenu,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = HeroHeight - DockHeight / 2),
            )
        }
    }
}
