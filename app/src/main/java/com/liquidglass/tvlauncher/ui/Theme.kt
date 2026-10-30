package com.liquidglass.tvlauncher.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.liquidglass.tvlauncher.R

/** One accent colour choice: its name and the colour used for switches, focus glow and highlights. */
class AccentChoice(val nameRes: Int, val color: Color)

/** One tint of the dark glass the dialogs and menus are made of. */
class GlassChoice(val nameRes: Int, val card: Color, val menu: Color)

val Accents = listOf(
    AccentChoice(R.string.accent_blue, Color(0xFF0A84FF)),
    AccentChoice(R.string.accent_purple, Color(0xFFAF52DE)),
    AccentChoice(R.string.accent_green, Color(0xFF30D158)),
    AccentChoice(R.string.accent_orange, Color(0xFFFF9F0A)),
    AccentChoice(R.string.accent_pink, Color(0xFFFF375F)),
    AccentChoice(R.string.accent_white, Color(0xFFE8E8ED)),
)

val GlassTints = listOf(
    GlassChoice(R.string.glass_graphite, Color(0xEB1C1E26), Color(0xE62A2C36)),
    GlassChoice(R.string.glass_navy, Color(0xEB132042), Color(0xE61E2D55)),
    GlassChoice(R.string.glass_violet, Color(0xEB271A44), Color(0xE6352557)),
    GlassChoice(R.string.glass_forest, Color(0xEB11281E), Color(0xE61B3A2C)),
    GlassChoice(R.string.glass_wine, Color(0xEB341A22), Color(0xE6482733)),
)

/** A one-tap bundle of [Accents] and [GlassTints] indices, for Settings → Wygląd → Gotowe motywy. */
class ThemePreset(val nameRes: Int, val accentIndex: Int, val glassIndex: Int)

val ThemePresets = listOf(
    ThemePreset(R.string.preset_default, 0, 0),
    ThemePreset(R.string.preset_midnight, 5, 1),
    ThemePreset(R.string.preset_sunset, 3, 4),
    ThemePreset(R.string.preset_forest, 2, 3),
    ThemePreset(R.string.preset_rose, 4, 2),
    ThemePreset(R.string.preset_mono, 5, 0),
)

/** What the user picked in Settings → Appearance, handed down to every screen and dialog. */
class LauncherTheme(val accent: Color, val glass: GlassChoice) {
    /** A lighter version of the accent: the soft light round a focused tile. */
    val glow: Color = lerp(accent, Color.White, 0.38f)

    companion object {
        val Default = LauncherTheme(Accents[0].color, GlassTints[0])

        fun of(accentIndex: Int, glassIndex: Int) = LauncherTheme(
            Accents[accentIndex.coerceIn(0, Accents.lastIndex)].color,
            GlassTints[glassIndex.coerceIn(0, GlassTints.lastIndex)],
        )
    }
}

val LocalTheme = compositionLocalOf { LauncherTheme.Default }

/** Whether tiles lean towards the direction focus came from (set from the launcher's settings). */
val LocalTileTilt = compositionLocalOf { true }

/**
 * The last D-pad direction pressed, which the tile that receives focus next uses to lean the way
 * the focus came from. Written by the activity as the keys arrive, before Compose moves focus.
 */
object TileMotion {
    @Volatile
    var dx = 0

    @Volatile
    var dy = 0

    fun note(keyCode: Int) {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { dx = -1; dy = 0 }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { dx = 1; dy = 0 }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> { dx = 0; dy = -1 }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { dx = 0; dy = 1 }
        }
    }
}
