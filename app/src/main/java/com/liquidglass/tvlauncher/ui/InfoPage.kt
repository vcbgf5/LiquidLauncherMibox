package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.AppInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PL = Locale("pl", "PL")
private val WEEKDAYS = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

/**
 * The page above the home screen: the date (big clock, full date, a month calendar) and a
 * row of "continue watching" tiles (real Watch Next titles when the system allows reading
 * them, otherwise a shortcut to the system home screen, which shows them).
 *
 * [entry] (0..1) is how far the page has slid in; the content fades up with it.
 * [firstFocus] goes to the first tile, or to the page itself when it has no tiles,
 * so there is always something focused to carry Up/Back.
 */
@Composable
fun InfoPage(
    wallpaper: WallpaperState,
    watchNext: List<AppInfo>,
    entry: () -> Float,
    firstFocus: FocusRequester,
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()
    val hasTiles = watchNext.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (hasTiles) Modifier else Modifier.focusRequester(firstFocus).focusable()),
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
                .padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 24.dp)
                .graphicsLayer {
                    val e = entry()
                    alpha = e
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    translationY = (1f - e) * 40f * density
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ClockBlock(now.value)
                CalendarCard(now.value)
            }
            if (watchNext.isNotEmpty()) {
                TileRow(stringResource(R.string.info_watch_next), watchNext, firstFocus, onLaunch, onOpenMenu)
            }
        }
    }
}

@Composable
private fun TileRow(
    title: String,
    items: List<AppInfo>,
    firstFocus: FocusRequester?,
    onLaunch: (AppInfo, Rect) -> Unit,
    onOpenMenu: (AppInfo) -> Unit,
) {
    Text(
        text = title.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp, start = 2.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        items.forEachIndexed { index, app ->
            AppTile(
                label = app.label,
                art = app.tile,
                modifier = Modifier.width(104.dp),
                focusRequester = if (index == 0) firstFocus else null,
                onClick = { rect -> onLaunch(app, rect) },
                onLongClick = { onOpenMenu(app) },
            )
        }
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

@Composable
private fun CalendarCard(nowMillis: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val monthTitle = SimpleDateFormat("LLLL yyyy", PL).format(cal.time).replaceFirstChar { it.uppercase() }
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weeks = (offset + days + 6) / 7

    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(monthTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
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
}

@Composable
private fun Cell(text: String, header: Boolean, today: Boolean) {
    Box(
        modifier = Modifier.width(36.dp).height(25.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (today) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White))
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontSize = if (header) 11.sp else 13.sp,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
            color = when {
                today -> Color(0xFF111111)
                header -> Color.White.copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.9f)
            },
        )
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
