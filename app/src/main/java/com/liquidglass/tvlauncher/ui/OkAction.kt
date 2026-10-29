package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/**
 * "OK was pressed on this row": a click on release, but only for a press
 * that started here.
 *
 * This matters for menus opened by holding OK. The held key keeps sending
 * repeats to the menu window, and a plain `clickable` would count those as
 * a fresh press and then fire the first row the moment the key is let go.
 * Here only a first, non-repeat key-down arms the row, so the menu opened
 * by a long press ignores that same press entirely: the key has to be
 * released and pressed again to choose something.
 *
 * Place it before `.focusable()` in the modifier chain (it listens for
 * keys passing through from the focused element below it).
 */
fun Modifier.okAction(onClick: () -> Unit): Modifier = composed {
    val armed = remember { booleanArrayOf(false) }
    this
        .onKeyEvent { event ->
            if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                return@onKeyEvent false
            }
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) armed[0] = true
                    true
                }
                KeyEventType.KeyUp -> {
                    val fire = armed[0]
                    armed[0] = false
                    if (fire) onClick()
                    true
                }
                else -> false
            }
        }
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
}
