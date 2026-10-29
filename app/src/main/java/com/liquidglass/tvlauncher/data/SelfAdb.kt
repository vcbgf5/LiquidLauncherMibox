package com.liquidglass.tvlauncher.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbException
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.SyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** What came of an attempt to talk to the box's own adb service. */
sealed interface AdbResult<out T> {
    class Ok<T>(val value: T) : AdbResult<T>

    /** The box is waiting for the user to allow this launcher's key on the TV screen. */
    object NeedsApproval : AdbResult<Nothing>

    /** Nothing listens on the adb port: debugging over the network is off. */
    object NoAdb : AdbResult<Nothing>

    class Failed(val reason: String) : AdbResult<Nothing>
}

class ShellOut(val exitCode: Int, val text: String) {
    val ok: Boolean get() = exitCode == 0
}

/** One open adb connection; everything in [SelfAdb.run] happens through it. */
class AdbSession internal constructor(private val dadb: Dadb) {
    fun sh(command: String): ShellOut {
        val response = dadb.shell(command)
        return ShellOut(response.exitCode, response.allOutput.trim())
    }

    /** Copies [file] to the box (as a plain readable file), or throws. */
    fun push(file: File, remotePath: String) {
        when (val result = dadb.push(file, remotePath, SelfAdb.REGULAR_FILE_MODE, System.currentTimeMillis())) {
            is SyncResult.Failure -> throw IOException("push: ${result.reason}")
            SyncResult.Success -> Unit
        }
    }
}

/**
 * The launcher talking to the adb service of the very box it runs on (127.0.0.1:5555), exactly as
 * `adb shell` from a computer would. It is what makes the "real" system actions possible without a
 * computer: silent updates, putting the box to sleep, restarting it, freeing memory, switching
 * apps off, granting the launcher its permissions.
 *
 * It needs debugging over the network switched on in the developer options, and a one-time
 * "Allow" (tick "Always allow") on the TV screen for the launcher's own key. Nothing here is used
 * unless the user starts it from the settings (or switches on silent updates).
 */
object SelfAdb {
    /** st_mode of an ordinary file, rw-r--r--: what adb clients send when they push one. */
    internal const val REGULAR_FILE_MODE = 33188

    private const val HOST = "127.0.0.1"
    private const val DEFAULT_PORT = 5555

    /** How long the box may take to answer, which includes the user finding the remote to accept the key. */
    private const val ANSWER_WAIT_MS = 90_000
    private const val CONNECT_WAIT_MS = 2_500

    /** The last error text (for the diagnostics screen). */
    @Volatile
    var lastError: String = ""
        private set

    /** The library behind this needs java.util.Base64, which Android has since 8.0. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 26

    private fun keyPair(context: Context): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    /**
     * Connects, runs [work] and disconnects. The first thing sent is a harmless `echo`, which is
     * also where the one-time approval happens. Exceptions from [work] come back as [AdbResult.Failed].
     */
    suspend fun <T> run(context: Context, work: (AdbSession) -> T): AdbResult<T> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!supported) return@withContext fail("wymaga Androida 8.0 lub nowszego")
        val keys = try {
            keyPair(app)
        } catch (e: Throwable) {
            return@withContext fail("klucz adb: " + describe(e))
        }
        var result = attempt(app, keys, work)
        if (result is AdbResult.NoAdb && switchOnDebugging(app)) {
            // Android 11+ boxes can be told to listen on the network; give the service a moment.
            delay(1_500)
            result = attempt(app, keys, work)
        }
        result
    }

    /** Runs one shell command and returns its output (or why it didn't work). */
    suspend fun shell(context: Context, command: String): AdbResult<ShellOut> = run(context) { it.sh(command) }

    /**
     * A connection that stays open (the caller closes it) for quick repeated commands, such as the volume
     * keys, where connecting every time would be too slow. Blocks; null when adb can't be reached (not
     * switched on, or the TV has not been told to allow the launcher's key yet).
     */
    fun connect(context: Context): Dadb? {
        if (!supported) return null
        val app = context.applicationContext
        val keys = try {
            keyPair(app)
        } catch (e: Throwable) {
            lastError = "klucz adb: " + describe(e)
            return null
        }
        for ((host, port) in endpoints()) {
            try {
                val dadb = Dadb.create(host, port, keys, connectTimeout = CONNECT_WAIT_MS, socketTimeout = 15_000)
                val hello = dadb.shell("echo liquidtv")
                if (hello.allOutput.contains("liquidtv")) return dadb
                dadb.close()
            } catch (e: Throwable) {
                lastError = describe(e)
            }
        }
        return null
    }

    private fun <T> attempt(app: Context, keys: AdbKeyPair, work: (AdbSession) -> T): AdbResult<T> {
        var sawProblem: AdbResult<T>? = null
        for ((host, port) in endpoints()) {
            var connected = false
            try {
                Dadb.create(host, port, keys, connectTimeout = CONNECT_WAIT_MS, socketTimeout = ANSWER_WAIT_MS).use { dadb ->
                    val session = AdbSession(dadb)
                    val hello = session.sh("echo liquidtv")
                    if (!hello.text.contains("liquidtv")) return fail("nieoczekiwana odpowiedź: ${hello.text}")
                    connected = true
                    return AdbResult.Ok(work(session))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val verdict = classify(e, connected)
                if (verdict is AdbResult.NoAdb) {
                    sawProblem = sawProblem ?: verdict
                    continue // nothing at this address: try the next one
                }
                return verdict
            }
        }
        return sawProblem ?: AdbResult.NoAdb
    }

    /** Which addresses may serve adb: the usual 5555, or the port the box itself says it listens on. */
    private fun endpoints(): List<Pair<String, Int>> {
        val ports = LinkedHashSet<Int>()
        for (name in listOf("service.adb.tcp.port", "persist.adb.tcp.port")) {
            property(name)?.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { ports.add(it) }
        }
        ports.add(DEFAULT_PORT)
        val hosts = listOfNotNull(HOST, WidgetDataReader.localAddress())
        return hosts.flatMap { host -> ports.map { host to it } }
    }

    private fun property(name: String): String? = try {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readLine()?.trim() }
    } catch (_: Throwable) {
        null
    }

    private fun switchOnDebugging(app: Context): Boolean = try {
        Settings.Global.putInt(app.contentResolver, Settings.Global.ADB_ENABLED, 1)
        if (Build.VERSION.SDK_INT >= 30) Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
        true
    } catch (_: Throwable) {
        false // needs WRITE_SECURE_SETTINGS, which is only granted over adb in the first place
    }

    private fun <T> fail(reason: String): AdbResult<T> {
        lastError = reason
        return AdbResult.Failed(reason)
    }

    private fun describe(e: Throwable): String {
        val cause = e.cause
        val own = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
        return if (cause != null && cause !== e) own + " ← " + cause.javaClass.simpleName + (cause.message?.let { ": $it" } ?: "") else own
    }

    /**
     * Before the first command went through, a failed TCP connection means nothing listens
     * (debugging is off); a handshake that stalled or was cut means the TV is still asking (or the
     * answer was "no"). After it, anything that goes wrong is a plain failure with its reason.
     */
    private fun classify(e: Throwable, connected: Boolean): AdbResult<Nothing> {
        val detail = describe(e)
        lastError = detail
        if (connected) return AdbResult.Failed(detail)
        return when (e) {
            is AdbAuthException -> AdbResult.NeedsApproval
            is AdbConnectException -> if (e.message.orEmpty().startsWith("Failed to connect")) AdbResult.NoAdb else AdbResult.NeedsApproval
            is AdbException -> AdbResult.Failed(detail)
            else -> AdbResult.Failed(detail)
        }
    }

    // ---- Updating this very app --------------------------------------------------------------

    /**
     * Installs [apk] over the running launcher. Installing a package kills the app it replaces,
     * and with it this connection, so the work is handed to a shell on the box that outlives us:
     * it installs, then starts the launcher again. While that runs, the log it writes is polled,
     * so a refusal (a wrong signature, no space) is still reported.
     */
    suspend fun installApk(context: Context, apk: File): AdbResult<String> = run<String>(context) { session ->
        val pkg = context.packageName
        val remote = "/data/local/tmp/liquidtv-update.apk"
        val log = "/data/local/tmp/liquidtv-update.log"
        session.push(apk, remote)
        session.sh("rm -f $log")
        val script = "pm install -r $remote > $log 2>&1; rm -f $remote; am start -n $pkg/.MainActivity > /dev/null 2>&1"
        var started = session.sh("nohup sh -c '$script' > /dev/null 2>&1 &")
        if (started.exitCode == 127) started = session.sh("sh -c '$script' > /dev/null 2>&1 &")
        if (!started.ok) throw IOException("start: ${started.text}")
        repeat(120) {
            Thread.sleep(750)
            val out = session.sh("cat $log 2>/dev/null").text
            if (out.contains("Success")) return@run out
            if (out.contains("Failure") || out.contains("Exception") || out.contains("Error")) throw IOException(out.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("Performing") } ?: out)
        }
        throw IOException("instalator nie odpowiedział")
    }

    // ---- Diagnostics ---------------------------------------------------------------------------

    /** A plain-text report of every step that decides whether adb works, for the screen that shows it. */
    suspend fun diagnose(context: Context): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val lines = ArrayList<String>()
        fun yes(value: Boolean) = if (value) "tak" else "nie"
        lines += "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MODEL}"
        lines += "Wbudowany klient adb działa na tej wersji: ${yes(supported)}"
        lines += "Klucz adb launchera: ${yes(File(app.filesDir, "adbkey").exists())}"
        for ((host, port) in endpoints()) {
            lines += "Port $host:$port otwarty: ${yes(portOpen(host, port))}"
        }
        lines += "debugowanie (adb_enabled): " + (try {
            Settings.Global.getInt(app.contentResolver, Settings.Global.ADB_ENABLED, -1).toString()
        } catch (_: Throwable) {
            "?"
        })
        lines += ""
        lines += "Łączenie… (jeśli telewizor zapyta o debugowanie, zaznacz „Zawsze zezwalaj”)"
        val result = run(app) { session ->
            listOf(
                "Użytkownik: " + session.sh("id").text.take(60),
                "Android (getprop): " + session.sh("getprop ro.build.version.release").text,
                "Aplikacja zainstalowana jako: " + session.sh("pm path ${app.packageName}").text.take(80),
                "pm: " + session.sh("pm list packages -d | head -3").text.replace('\n', ' ').take(80),
            )
        }
        when (result) {
            is AdbResult.Ok -> {
                lines += "Połączenie i autoryzacja: działa"
                lines += result.value
            }
            AdbResult.NeedsApproval -> lines += "Połączenie: czeka na zgodę na ekranie telewizora albo ją odrzucono"
            AdbResult.NoAdb -> lines += "Połączenie: nic nie nasłuchuje. Włącz Opcje programisty → Debugowanie USB (a jeśli jest, także debugowanie przez sieć)"
            is AdbResult.Failed -> lines += "Połączenie: błąd – ${result.reason}"
        }
        if (lastError.isNotEmpty()) lines += "Ostatni błąd: $lastError"
        lines.joinToString("\n")
    }

    private fun portOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 800) }
        true
    } catch (_: Throwable) {
        false
    }
}
