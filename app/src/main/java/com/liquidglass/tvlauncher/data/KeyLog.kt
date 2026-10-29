package com.liquidglass.tvlauncher.data

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * The last keys that reached the launcher or its accessibility service, newest first, for the
 * diagnostics. A remote's "Menu" button can send any of a dozen key codes (or none the system lets
 * through); this shows which, so nothing has to be guessed.
 */
object KeyLog {
    private const val MAX = 24
    private val entries = ArrayDeque<String>()
    private val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Records a key press (not releases or auto-repeats). [where] says who saw it. */
    @Synchronized
    fun record(where: String, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        val device = event.device?.name ?: "?"
        val line = "${format.format(Date())} $where ${name(event.keyCode)} (${event.keyCode}) kod=${event.scanCode} $device"
        entries.addFirst(line)
        while (entries.size > MAX) entries.removeLast()
    }

    @Synchronized
    fun dump(): String = if (entries.isEmpty()) "(jeszcze nic nie naciśnięto)" else entries.joinToString("\n")

    fun name(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}

/**
 * Teaches the launcher which remote button should open the quick settings. While [learning] is on,
 * the next key that isn't one of the basic ones (arrows, OK, Back, Home, volume, power) is taken,
 * remembered in the settings and swallowed. Both the launcher's window and the accessibility
 * service offer keys, so it works even when the system keeps the key away from apps.
 */
object KeyLearner {
    var learning by mutableStateOf(false)
        private set

    /** The name of the key just learned, until the screen has told the user. */
    var learned by mutableStateOf("")
        private set

    private var startedAt = 0L

    fun start() {
        learned = ""
        startedAt = System.currentTimeMillis()
        learning = true
    }

    fun stop() {
        learning = false
    }

    fun acknowledge() {
        learned = ""
    }

    private val basic = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_UNKNOWN,
    )

    /**
     * Called for every key press by both the activity and the service. True = the key was taken
     * for learning (the caller should swallow it).
     */
    fun offer(event: KeyEvent, settings: LauncherSettings): Boolean {
        if (!learning) return false
        if (System.currentTimeMillis() - startedAt > 30_000L) {
            learning = false
            return false
        }
        if (event.keyCode in basic) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            settings.quickKey = event.keyCode
            learned = KeyLog.name(event.keyCode)
            learning = false
        }
        return true
    }
}
