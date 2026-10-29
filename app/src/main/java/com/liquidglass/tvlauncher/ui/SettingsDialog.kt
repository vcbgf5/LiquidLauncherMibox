package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.liquidglass.tvlauncher.R
import kotlinx.coroutines.delay

private const val STEP = 10

/**
 * The launcher's own settings, opened from the gear in the top bar:
 * how transparent the dock is (Left/Right changes it, and the dock behind
 * the card updates live), and a way into the Android system settings.
 */
@Composable
fun SettingsDialog(
    transparencyPercent: Int,
    onTransparencyChange: (Int) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dim the screen only a little, so the dock stays visible while it is adjusted.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.2f) }

        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(shape)
                .background(Color(0xE62A2C36))
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp),
            )
            AdjustRow(
                label = stringResource(R.string.settings_dock_transparency),
                percent = transparencyPercent,
                autoFocus = true,
                onStep = { direction ->
                    onTransparencyChange((transparencyPercent + direction * STEP).coerceIn(0, 100))
                },
            )
            ActionRow(
                label = stringResource(R.string.settings_system),
                onClick = {
                    onDismiss()
                    onOpenSystemSettings()
                },
            )
        }
    }
}

@Composable
private fun AdjustRow(label: String, percent: Int, autoFocus: Boolean, onStep: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    val ink = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontSize = 14.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = ink, fontSize = 18.sp)
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(110.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ink.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .width((110 * percent / 100f).dp)
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
private fun ActionRow(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "$label  ›",
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.9f),
        )
    }
}
