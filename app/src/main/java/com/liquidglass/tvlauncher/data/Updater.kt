package com.liquidglass.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val versionCode: Long, val versionName: String)

/**
 * Updates from this project's GitHub releases.
 *
 * Every release carries two files, written by the build: `LiquidTV.apk` and a tiny
 * `version.json` with the build's versionCode. Checking is one small download of that
 * json compared with the installed versionCode; updating downloads the APK and hands it to
 * Android's installer, which asks the user to confirm (Android never lets an app
 * install silently). It only replaces the running app when both are signed with the same
 * key, which the CI build takes care of once the KEYSTORE_PASSPHRASE secret is set.
 */
object Updater {
    private const val BASE = "https://github.com/vcbgf5/LiquidLauncherMibox/releases/latest/download/"

    sealed interface CheckResult {
        class Newer(val info: UpdateInfo) : CheckResult
        object UpToDate : CheckResult
        object Failed : CheckResult
    }

    enum class InstallStart { STARTED, NEEDS_PERMISSION, FAILED }

    fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val bytes = Net.download(BASE + "version.json", 100_000) ?: return@withContext CheckResult.Failed
        try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val code = json.getLong("versionCode")
            if (code > currentVersionCode(context)) {
                CheckResult.Newer(UpdateInfo(code, json.optString("versionName", code.toString())))
            } else {
                CheckResult.UpToDate
            }
        } catch (_: Exception) {
            CheckResult.Failed
        }
    }

    /** Downloads the release APK (reusing one already downloaded), reporting percent done. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "LiquidTV-${info.versionCode}.apk")
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        if (target.exists() && isOurApk(context, target)) return@withContext target
        try {
            val connection = (URL(BASE + "LiquidTV.apk").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val total = connection.contentLengthLong
                val part = File(dir, "download.part")
                connection.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                if (!part.renameTo(target) || !isOurApk(context, target)) {
                    target.delete()
                    part.delete()
                    return@withContext null
                }
                target
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Guards against installing an error page or a different app: it must be this app, and newer. */
    private fun isOurApk(context: Context, file: File): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return false
        return info.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(info) > currentVersionCode(context)
    }

    /** Opens the system installer for [file]; on Android 8+ first needs the "install unknown apps" switch. */
    fun install(context: Context, file: File): InstallStart {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return InstallStart.NEEDS_PERMISSION
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            InstallStart.STARTED
        } catch (_: Exception) {
            InstallStart.FAILED
        }
    }
}
