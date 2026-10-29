package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

class MenuOption(val label: String, val action: () -> Unit)

/**
 * The menu that opens when OK is held on an app: pin/unpin, reorder,
 * app info, uninstall. A small glass card over a dimmed screen, driven
 * entirely by the D-pad (the first row is focused as soon as it opens).
 */
@Composable
fun AppMenu(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(shape)
                .background(LocalTheme.current.glass.menu)
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .padding(14.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp),
            )
            // Long lists (every app that can be switched off) scroll; the focused row is kept in view.
            Column(modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    MenuRow(
                        label = option.label,
                        autoFocus = index == 0,
                        onClick = {
                            onDismiss()
                            option.action()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, autoFocus: Boolean, onClick: () -> Unit) {
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
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f),
        )
    }
}
