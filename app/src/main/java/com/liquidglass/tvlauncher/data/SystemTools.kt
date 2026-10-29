package com.liquidglass.tvlauncher.data

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.liquidglass.tvlauncher.MediaListenerService
import com.liquidglass.tvlauncher.SystemService
import kotlinx.coroutines.delay

/**
 * What the launcher can do to the box through its own adb connection ([SelfAdb]): sleep, restart,
 * free memory, switch an app off, set the animation speed, wake or switch the TV through HDMI-CEC,
 * and give itself its permissions. Each call reports through [AdbResult] so the screen can say what
 * to do when adb isn't switched on or the TV is still waiting for the key to be allowed.
 */
object SystemTools {

    // ---- Power -------------------------------------------------------------------------------

    /** Puts the box to sleep (the TV follows when HDMI-CEC "turn off the TV with the box" is on). */
    suspend fun standby(context: Context): AdbResult<ShellOut> = SelfAdb.shell(context, "input keyevent 223")

    suspend fun reboot(context: Context): AdbResult<ShellOut> = SelfAdb.shell(context, "reboot")

    suspend fun shutdown(context: Context): AdbResult<ShellOut> = SelfAdb.shell(context, "reboot -p")

    /** Switches the TV on through HDMI-CEC ("one touch play"), falling back to waking this box. */
    suspend fun tvOn(context: Context): AdbResult<String> = SelfAdb.run(context) { session ->
        val result = session.sh("cmd hdmi_control onetouchplay")
        if (result.ok && !result.text.contains("error", ignoreCase = true) && !result.text.contains("unknown", ignoreCase = true)) {
            "cmd hdmi_control onetouchplay"
        } else {
            session.sh("input keyevent 224")
            "wybudzenie Boxa (CEC nie odpowiedziało: ${result.text.take(60)})"
        }
    }

    /** The "TV power" key, which Android turns into a CEC message that switches the TV on or off. */
    suspend fun tvPowerToggle(context: Context): AdbResult<ShellOut> = SelfAdb.shell(context, "input keyevent 177")

    // ---- Memory ------------------------------------------------------------------------------

    private fun freeRamMb(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /** Stops the cached background apps and empties every app's cache; returns a one-line summary. */
    suspend fun freeMemory(context: Context): AdbResult<String> {
        val before = freeRamMb(context)
        val result = SelfAdb.run(context) { session ->
            session.sh("am kill-all")
            session.sh("pm trim-caches 4G")
        }
        if (result !is AdbResult.Ok) return result.retype()
        delay(1_500)
        val after = freeRamMb(context)
        return AdbResult.Ok("Wolny RAM: $before MB → $after MB, pamięć podręczna aplikacji wyczyszczona")
    }

    // ---- Animations ----------------------------------------------------------------------------

    /** The speed of Android's own window animations (1 = normal, 0.5 = twice as fast, 0 = none). */
    fun animationScale(context: Context): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)

    suspend fun setAnimationScale(context: Context, scale: Float): AdbResult<String> {
        val names = listOf(Settings.Global.WINDOW_ANIMATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.ANIMATOR_DURATION_SCALE)
        try {
            names.forEach { Settings.Global.putFloat(context.contentResolver, it, scale) }
            return AdbResult.Ok("ok")
        } catch (_: SecurityException) {
            // Not allowed to write the setting directly: the box's adb can.
        }
        return SelfAdb.run(context) { session ->
            names.forEach { session.sh("settings put global $it $scale") }
            "ok"
        }
    }

    // ---- Switching apps off --------------------------------------------------------------------

    private val protectedPackages = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.shell", "com.google.android.gms", "com.google.android.gsf",
        "com.android.vending", "com.android.bluetooth", "com.android.keychain", "com.android.certinstaller", "com.android.webview",
        "com.google.android.webview", "com.android.phone", "com.android.server.telecom", "com.android.externalstorage",
        "com.android.documentsui", "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
        "com.google.android.tvrecommendations", "com.google.android.leanbacklauncher.recommendations",
    )

    /** Apps that can't be switched off from here: this launcher and the parts of Android that would break the box. */
    fun isProtected(context: Context, packageName: String): Boolean =
        packageName == context.packageName || packageName in protectedPackages ||
            packageName.startsWith("com.android.providers.") || packageName.startsWith("com.android.server.")

    /** True for apps that provide a home screen (switching off the stock one makes the Home button open this launcher). */
    fun isHomeApp(context: Context, packageName: String): Boolean = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL).any { it.activityInfo.packageName == packageName }
    } catch (_: Throwable) {
        false
    }

    private val packagePattern = Regex("[A-Za-z0-9_.]+")

    suspend fun setAppEnabled(context: Context, packageName: String, enabled: Boolean): AdbResult<ShellOut> {
        if (!packagePattern.matches(packageName) || isProtected(context, packageName)) return AdbResult.Failed("tej aplikacji nie wolno wyłączać")
        return SelfAdb.shell(context, if (enabled) "pm enable $packageName" else "pm disable-user --user 0 $packageName")
    }

    suspend fun forceStop(context: Context, packageName: String): AdbResult<ShellOut> {
        if (!packagePattern.matches(packageName)) return AdbResult.Failed("zła nazwa pakietu")
        return SelfAdb.shell(context, "am force-stop $packageName")
    }

    /** The packages that are switched off right now. */
    suspend fun disabledPackages(context: Context): AdbResult<List<String>> = SelfAdb.run(context) { session ->
        session.sh("pm list packages -d").text.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { packagePattern.matches(it) }
            .sorted()
            .toList()
    }

    /** The name an app shows in the launcher, or its package name when Android doesn't have it. */
    fun labelOf(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)).toString()
    } catch (_: Throwable) {
        packageName
    }

    // ---- Permissions -----------------------------------------------------------------------------

    /**
     * Gives the launcher what the settings would otherwise send you through Android's screens for:
     * writing the secure settings (which lets it switch its own accessibility service and
     * notification access on), the system calendar, Bluetooth, and installing its own updates.
     * Returns one line per step.
     */
    suspend fun grantPermissions(context: Context): AdbResult<List<String>> {
        val app = context.applicationContext
        val pkg = app.packageName
        val result = SelfAdb.run(app) { session ->
            val lines = ArrayList<String>()
            fun step(label: String, command: String) {
                val out = session.sh(command)
                lines += (if (out.ok) "✓ " else "✗ ") + label + if (out.ok || out.text.isBlank()) "" else " (${out.text.lineSequence().first().take(60)})"
            }
            step("Zapis ustawień zabezpieczonych", "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
            step("Kalendarz systemowy", "pm grant $pkg android.permission.READ_CALENDAR")
            if (Build.VERSION.SDK_INT >= 31) step("Bluetooth (sparowane urządzenia)", "pm grant $pkg android.permission.BLUETOOTH_CONNECT")
            step("Instalowanie aktualizacji", "appops set $pkg REQUEST_INSTALL_PACKAGES allow")
            lines
        }
        if (result !is AdbResult.Ok) return result
        // With the secure-settings permission in hand the two services can be switched on directly.
        val lines = ArrayList(result.value)
        lines += if (enableAccessibility(app)) "✓ Usługa dostępności Liquid TV włączona" else "✗ Usługa dostępności (włącz ją w ustawieniach dostępności)"
        lines += if (enableNotificationAccess(app)) "✓ Dostęp do powiadomień i odtwarzaczy (widżet „Teraz gra”)" else "✗ Dostęp do powiadomień"
        return AdbResult.Ok(lines)
    }

    /** Switches the accessibility service on without the system screens (needs WRITE_SECURE_SETTINGS). */
    fun enableAccessibility(context: Context): Boolean = try {
        val service = ComponentName(context, SystemService::class.java).flattenToString()
        addToSetting(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, service)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    } catch (_: SecurityException) {
        false
    }

    /** Lets the launcher see what the media apps are playing (needs WRITE_SECURE_SETTINGS). */
    fun enableNotificationAccess(context: Context): Boolean = try {
        addToSetting(context, "enabled_notification_listeners", ComponentName(context, MediaListenerService::class.java).flattenToString())
        true
    } catch (_: SecurityException) {
        false
    }

    private fun addToSetting(context: Context, name: String, entry: String) {
        val current = Settings.Secure.getString(context.contentResolver, name).orEmpty()
        if (entry !in current.split(':')) {
            Settings.Secure.putString(context.contentResolver, name, if (current.isEmpty()) entry else "$current:$entry")
        }
    }

    fun accessibilityOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains("${context.packageName}/") == true
    }

    fun notificationAccessOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.contains("${context.packageName}/") == true
    }
}

/** A failed [AdbResult] carried over to a function that returns another type (a success is not expected here). */
fun <T> AdbResult<*>.retype(): AdbResult<T> = when (this) {
    AdbResult.NeedsApproval -> AdbResult.NeedsApproval
    AdbResult.NoAdb -> AdbResult.NoAdb
    is AdbResult.Failed -> this
    is AdbResult.Ok -> AdbResult.Failed("nieoczekiwany wynik")
}
