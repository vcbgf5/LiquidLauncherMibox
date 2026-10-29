package com.liquidglass.tvlauncher.data

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Installs an update without Android's installer window, by asking the box's own ADB service to
 * do it, exactly as `adb install -r` from a computer would.
 *
 * This works because the user switches on debugging over the network (the same thing the computer
 * uses, port 5555). The launcher connects to it on the box itself with a key of its own; the
 * very first time Android asks on the TV screen whether to allow that key ("Allow USB debugging?"),
 * and after "Always allow" it never asks again. The launcher uses the connection only to install
 * its own updates. It is opt-in, in the settings, because it gives the launcher the power of ADB.
 */
object SilentInstaller {
    private const val HOST = "127.0.0.1"
    private const val PORT = 5555

    sealed interface Result {
        /** It worked (a replaced app usually never gets to hear about it: it is restarted). */
        object Done : Result

        /** The box is waiting for the user to allow this launcher's key on the TV screen. */
        object NeedsApproval : Result

        /** Nothing listens on the ADB port: debugging over the network is off. */
        object NoAdb : Result

        class Failed(val reason: String) : Result
    }

    private fun keyPair(context: Context): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    /** Connects and runs a harmless command: the way to give the one-time approval, and to test it. */
    suspend fun check(context: Context): Result = run(context) { dadb ->
        val response = dadb.shell("echo liquidtv")
        if (response.allOutput.contains("liquidtv")) Result.Done else Result.Failed(response.allOutput.trim())
    }

    suspend fun install(context: Context, apk: File): Result = run(context) { dadb ->
        when (val result = dadb.install(apk, "-r")) {
            is InstallResult.Success -> Result.Done
            is InstallResult.Failure -> Result.Failed(result.reason.trim())
        }
    }

    private suspend fun run(context: Context, work: (Dadb) -> Result): Result = withContext(Dispatchers.IO) {
        try {
            // Waiting for the user to answer the TV's question must not hang for ever.
            withTimeoutOrNull(120_000L) {
                Dadb.create(HOST, PORT, keyPair(context)).use { dadb -> work(dadb) }
            } ?: Result.NeedsApproval
        } catch (e: Throwable) {
            val name = e.javaClass.simpleName.lowercase()
            val message = (e.message ?: "").lowercase()
            when {
                "auth" in name || "auth" in message || "unauthorized" in message -> Result.NeedsApproval
                "connect" in name || "refused" in message || "econnrefused" in message || "timed out" in message -> Result.NoAdb
                else -> Result.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
