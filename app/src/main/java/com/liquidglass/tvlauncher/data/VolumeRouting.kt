package com.liquidglass.tvlauncher.data

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where the sound goes decides whether the volume the box reports means anything. Through HDMI the
 * box's volume is "fixed" (Android just flips it between 0 and full) and the real volume is the TV's,
 * set over HDMI-CEC, which the box can neither read nor see change. A Bluetooth speaker or a
 * built-in output has a real level that does change.
 */
object VolumeRouting {
    fun hdmiPresent(audio: AudioManager): Boolean = try {
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC
        }
    } catch (_: Throwable) {
        false
    }

    @Suppress("DEPRECATION")
    fun bluetoothActive(audio: AudioManager): Boolean = try {
        audio.isBluetoothA2dpOn
    } catch (_: Throwable) {
        false
    }

    /** True when the music volume the box reports is the real one (so a level bar tells the truth). */
    fun levelIsReal(audio: AudioManager): Boolean =
        !audio.isVolumeFixed && (bluetoothActive(audio) || !hdmiPresent(audio))

    /** One line for the diagnostics. */
    fun describe(audio: AudioManager): String = when {
        audio.isVolumeFixed -> "stała głośność (steruje nią telewizor): pasek pokazuje kierunek zmiany"
        bluetoothActive(audio) -> "głośnik Bluetooth: prawdziwy poziom"
        hdmiPresent(audio) -> "HDMI (głośność ustawia telewizor przez CEC, Box jej nie zna): pasek pokazuje kierunek zmiany"
        else -> "wyjście z własną głośnością: prawdziwy poziom"
    }
}
