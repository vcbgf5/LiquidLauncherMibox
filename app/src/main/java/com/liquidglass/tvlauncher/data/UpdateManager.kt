package com.liquidglass.tvlauncher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liquidglass.tvlauncher.R

enum class UpdateStatus {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, CHECK_FAILED, DOWNLOADING, DOWNLOAD_FAILED,
    NEEDS_PERMISSION, WRONG_SIGNATURE, INSTALLING, SILENT_INSTALLING, SILENT_FAILED,
}

/**
 * Everything about updating the launcher in one place: asking GitHub for the newest version,
 * downloading it, and installing it (through the box's own adb when that is switched on, so
 * Android's installer window never shows, or through the system installer otherwise).
 *
 * While the launcher is on screen [autoStep] runs in a loop (every [LauncherSettings.updateIntervalSeconds],
 * 5 by default). A check is one tiny request, so that is cheap; when it fails (no internet, GitHub
 * refusing too many requests) the wait grows step by step instead of hammering. A newer version is
 * installed as soon as nobody has pressed a key for a moment, and every version is offered
 * automatically once (a failed silent install is tried again after a while), so a refused
 * install is not repeated every few seconds.
 */
class UpdateManager(context: Context, private val settings: LauncherSettings) {
    private val app = context.applicationContext

    var status by mutableStateOf(UpdateStatus.IDLE)
        private set
    var info by mutableStateOf<UpdateInfo?>(null)
        private set
    var percent by mutableIntStateOf(0)
        private set

    /** Why the last silent install didn't work (shown while [status] is [UpdateStatus.SILENT_FAILED]). */
    var problem by mutableStateOf("")
        private set

    private var failures = 0

    private val replaceable = setOf(
        UpdateStatus.IDLE, UpdateStatus.CHECKING, UpdateStatus.UP_TO_DATE, UpdateStatus.AVAILABLE, UpdateStatus.CHECK_FAILED,
    )

    private val working: Boolean
        get() = status == UpdateStatus.DOWNLOADING || status == UpdateStatus.SILENT_INSTALLING || status == UpdateStatus.INSTALLING

    /** Asks GitHub. True when it answered at all. */
    suspend fun check(automatic: Boolean): Boolean {
        if (working) return true
        if (!automatic) status = UpdateStatus.CHECKING
        val reached: Boolean
        when (val result = Updater.check(app)) {
            is Updater.CheckResult.Newer -> {
                reached = true
                failures = 0
                info = result.info
                if (!automatic || status in replaceable) status = UpdateStatus.AVAILABLE
            }
            Updater.CheckResult.UpToDate -> {
                reached = true
                failures = 0
                info = null
                if (!automatic || status in replaceable) status = UpdateStatus.UP_TO_DATE
            }
            Updater.CheckResult.Failed -> {
                reached = false
                failures++
                if (!automatic) status = UpdateStatus.CHECK_FAILED
            }
        }
        settings.lastUpdateCheck = System.currentTimeMillis()
        return reached
    }

    /** The "check now" button: look, and when something newer is there, install it right away. */
    suspend fun checkNow() {
        check(automatic = false)
        if (info != null) install(automatic = false)
    }

    /** Downloads the newest version and installs it. [automatic] = nobody pressed anything: stay quiet about problems. */
    suspend fun install(automatic: Boolean) {
        val target = info ?: return
        if (working) return
        status = UpdateStatus.DOWNLOADING
        percent = 0
        val file = Updater.download(app, target) { percent = it }
        if (file == null) {
            status = UpdateStatus.DOWNLOAD_FAILED
            return
        }
        // Android refuses an update signed with another key; better to say so than to fail obscurely.
        if (!Updater.sameSigner(app, file)) {
            status = UpdateStatus.WRONG_SIGNATURE
            return
        }
        if (settings.silentUpdates) {
            // No Android window: the box's own adb installs it and starts the launcher again.
            status = UpdateStatus.SILENT_INSTALLING
            when (val result = SelfAdb.installApk(app, file)) {
                is AdbResult.Ok -> {
                    info = null
                    status = UpdateStatus.UP_TO_DATE
                    return
                }
                else -> problem = explain(result)
            }
            status = UpdateStatus.SILENT_FAILED
            // Falls through to the normal installer either way: a silent install that keeps failing
            // (adb's authorization lost after a reboot is the usual reason) must not leave the
            // automatic loop just giving up forever with nothing on screen to show for it.
        }
        status = when (Updater.install(app, file, askPermission = !automatic)) {
            Updater.InstallStart.STARTED -> UpdateStatus.INSTALLING
            Updater.InstallStart.NEEDS_PERMISSION -> UpdateStatus.NEEDS_PERMISSION
            Updater.InstallStart.WRONG_SIGNATURE -> UpdateStatus.WRONG_SIGNATURE
            Updater.InstallStart.FAILED -> UpdateStatus.DOWNLOAD_FAILED
        }
    }

    /**
     * One round of the automatic loop: check, and install a new version once [userIdle] says nobody
     * is using the remote. Returns how many milliseconds to wait before the next round.
     */
    suspend fun autoStep(userIdle: () -> Boolean): Long {
        val reached = check(automatic = true)
        val target = info
        if (target != null && userIdle() && !working && mayOffer(target)) {
            settings.lastAutoInstalled = target.versionCode
            settings.lastAutoInstallAt = System.currentTimeMillis()
            install(automatic = true)
        }
        val base = settings.updateIntervalSeconds * 1000L
        return if (reached) base else (base shl failures.coerceAtMost(6)).coerceAtMost(5 * 60_000L).coerceAtLeast(base)
    }

    /** Each version is pushed at the user once; a failed silent install may be retried after a quarter of an hour. */
    private fun mayOffer(target: UpdateInfo): Boolean {
        if (settings.lastAutoInstalled != target.versionCode) return true
        val retryable = settings.silentUpdates && status == UpdateStatus.SILENT_FAILED
        return retryable && System.currentTimeMillis() - settings.lastAutoInstallAt > 15 * 60_000L
    }

    private fun explain(result: AdbResult<*>): String = when (result) {
        AdbResult.NeedsApproval -> app.getString(R.string.update_silent_needs_approval)
        AdbResult.NoAdb -> app.getString(R.string.update_silent_no_adb)
        is AdbResult.Failed -> app.getString(R.string.update_silent_failed, result.reason)
        is AdbResult.Ok -> ""
    }
}
