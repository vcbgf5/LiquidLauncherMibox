package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidglass.tvlauncher.data.WallpaperCategory
import kotlinx.coroutines.delay

private val CheckMark: ImageVector by lazy {
    ImageVector.Builder(name = "Check", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z").toNodes())
        .build()
}

/**
 * Which wallpaper categories to rotate through, more than one at once: every category is its own
 * row with a check mark, OK flips it (and keeps the list open, so several can be picked in a row),
 * Back/Wstecz closes the list. ["Wszystkie"] / ["Żadna"] rows at the top check or clear them all.
 */
@Composable
fun WallpaperCategoryPicker(categories: List<WallpaperCategory>, selected: Set<String>, onToggle: (String, Boolean) -> Unit, onSelectAll: () -> Unit, onSelectNone: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .glassPanel(22.dp, LocalTheme.current.glass.menu)
                .padding(14.dp),
        ) {
            Text(
                text = "Kategorie tapet",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp),
            )
            Text(
                text = "OK zaznacza i odznacza; da się wybrać kilka naraz.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 2,
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
            )
            Column(modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                PickerRow(label = "Wszystkie", checked = selected.size == categories.size, autoFocus = true, onClick = onSelectAll)
                PickerRow(label = "Żadna (tapeta domyślna)", checked = selected.isEmpty(), autoFocus = false, onClick = onSelectNone)
                categories.forEach { cat ->
                    PickerRow(
                        label = cat.name,
                        checked = cat.id in selected,
                        autoFocus = false,
                        onClick = { onToggle(cat.id, cat.id !in selected) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, checked: Boolean, autoFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        checked && focused -> Color(0xFF111111)
                        checked -> LocalTheme.current.accent
                        else -> Color.White.copy(alpha = 0.16f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(CheckMark, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
