package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.AppInfo
import com.liquidglass.tvlauncher.data.IconPack
import com.liquidglass.tvlauncher.data.IconPacks
import kotlinx.coroutines.delay

/**
 * Picks a different icon for one app. On the left: "original icon" (puts the app's own back) and
 * the icon packs installed on the box; on the right: the icons of the pack that has focus, the
 * one made for this app first. OK on an icon uses it for this app only.
 */
@Composable
fun IconPicker(
    app: AppInfo,
    packs: List<IconPack>,
    loadContents: suspend (IconPack) -> IconPacks.Contents,
    loadIcon: suspend (IconPack, String) -> ImageBitmap?,
    onPick: (IconPack, String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var selected by remember { mutableStateOf(packs.firstOrNull()) }
        var contents by remember { mutableStateOf<IconPacks.Contents?>(null) }
        LaunchedEffect(selected) {
            contents = null
            selected?.let { contents = loadContents(it) }
        }

        // The pack's own icon for this app goes first, the rest follow alphabetically.
        val matched = contents?.match(app.packageName, app.className)
        val names = remember(contents, matched) {
            contents?.let { c -> listOfNotNull(matched) + c.names.filter { it != matched } }.orEmpty()
        }

        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .width(780.dp)
                .height(470.dp)
                .clip(shape)
                .background(Color(0xEB1C1E26))
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.tile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(90.dp).height(54.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.icon_picker_title, app.label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.icon_picker_hint),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.width(230.dp).fillMaxHeight()) {
                    SourceRow(
                        label = stringResource(R.string.icon_picker_original),
                        selected = false,
                        autoFocus = packs.isEmpty(),
                        onFocus = {},
                        onClick = onReset,
                    )
                    packs.forEachIndexed { index, pack ->
                        SourceRow(
                            label = pack.label,
                            selected = pack == selected,
                            autoFocus = index == 0,
                            onFocus = { selected = pack },
                            onClick = {},
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val pack = selected
                    when {
                        pack == null -> Hint(stringResource(R.string.icon_picker_no_packs))
                        contents == null -> Hint(stringResource(R.string.icon_picker_loading))
                        names.isEmpty() -> Hint(stringResource(R.string.icon_picker_empty_pack))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(names, key = { _, name -> name }) { index, name ->
                                IconCell(
                                    pack = pack,
                                    name = name,
                                    suggested = matched != null && index == 0,
                                    loadIcon = loadIcon,
                                    onClick = { onPick(pack, name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
        modifier = Modifier.padding(8.dp),
    )
}

/** One entry of the left list. Taking focus on a pack shows that pack's icons on the right. */
@Composable
private fun SourceRow(label: String, selected: Boolean, autoFocus: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
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
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.92f)
                    selected -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f),
        )
    }
}

@Composable
private fun IconCell(
    pack: IconPack,
    name: String,
    suggested: Boolean,
    loadIcon: suspend (IconPack, String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, pack.packageName, name) { value = loadIcon(pack, name) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.07f))
            .then(if (suggested && !focused) Modifier.border(2.dp, Color(0xFF0A84FF), shape) else Modifier)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
