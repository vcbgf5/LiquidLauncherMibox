package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * A card of plain text (what the launcher knows about something) with a few buttons under it that
 * do NOT close the card, so a test can be run and its result read straight away: the caller
 * rebuilds [text] after each action.
 */
@Composable
fun DiagnosticsDialog(
    title: String,
    text: String,
    actions: List<MenuOption>,
    closeLabel: String,
    onDismiss: () -> Unit,
    monospace: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .width(640.dp)
                .heightIn(max = 500.dp)
                .clip(shape)
                .background(LocalTheme.current.glass.card)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(20.dp),
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            actions.forEachIndexed { index, option -> DialogButton(option.label, autoFocus = index == 0, onClick = option.action) }
            DialogButton(closeLabel, autoFocus = actions.isEmpty(), onClick = onDismiss)
        }
    }
}

/** A full-width button row for the cards in this file and the other small dialogs. */
@Composable
internal fun DialogButton(label: String, autoFocus: Boolean, onClick: () -> Unit) {
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
            .onFocusChanged { focused = it.isFocused }
            .okAction(onClick)
            .focusRequester(requester)
            .focusable()
            .background(if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color(0xFF111111) else Color.White.copy(alpha = 0.92f),
        )
    }
}
