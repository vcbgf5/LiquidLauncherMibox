package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liquidglass.tvlauncher.R
import com.liquidglass.tvlauncher.data.Qr

/**
 * "Pilot z telefonu": the web address of the control page as a QR code to scan with the phone's
 * camera (phone and box must be on the same Wi-Fi), the address in words, and whether the page
 * stays available after this window closes.
 */
@Composable
fun RemoteDialog(
    url: String?,
    always: Boolean,
    onToggleAlways: () -> Unit,
    onNewLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .width(640.dp)
                .clip(shape)
                .background(LocalTheme.current.glass.card)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.remote_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                QrCode(url, Modifier.size(210.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (url != null) R.string.remote_hint else R.string.remote_no_network),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    if (url != null) {
                        Text(
                            text = url,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(top = 14.dp)) {
                DialogButton(
                    label = stringResource(if (always) R.string.remote_always_on else R.string.remote_always_off),
                    autoFocus = true,
                    onClick = onToggleAlways,
                )
                DialogButton(label = stringResource(R.string.remote_new_link), autoFocus = false, onClick = onNewLink)
                DialogButton(label = stringResource(R.string.diag_close), autoFocus = false, onClick = onDismiss)
            }
        }
    }
}

/** A QR code for [text] in black on a white card (QR readers need dark on light, whatever the theme). */
@Composable
private fun QrCode(text: String?, modifier: Modifier) {
    val modules = remember(text) { text?.let { Qr.modules(it) } }
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (modules != null) {
            Canvas(Modifier.size(182.dp)) {
                val count = modules.size
                val cell = size.minDimension / count
                for (y in 0 until count) {
                    for (x in 0 until count) {
                        if (modules[y][x]) {
                            drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                        }
                    }
                }
            }
        }
    }
}
