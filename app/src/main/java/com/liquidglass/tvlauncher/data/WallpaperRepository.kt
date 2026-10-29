package com.liquidglass.tvlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches wallpapers from this project's own GitHub repo (the `wallpapers/`
 * folder, served by raw.githubusercontent.com) and keeps what it downloaded
 * in the app's cache, so a wallpaper that was shown once works offline.
 */
object WallpaperRepository {
    private const val BASE = "https://raw.githubusercontent.com/vcbgf5/LiquidLauncherMibox/main/wallpapers/"
    private const val TARGET_WIDTH = 1920

    suspend fun catalog(context: Context): WallpaperCatalog = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "wallpapers/index.json")
        val fresh = download(BASE + "index.json")?.toString(Charsets.UTF_8)
        if (fresh != null) {
            runCatching {
                cache.parentFile?.mkdirs()
                cache.writeText(fresh)
            }
        }
        parse(fresh ?: runCatching { cache.readText() }.getOrNull())
    }

    /** Downloads (once) and decodes one wallpaper; null if it can't be had or read. */
    suspend fun load(context: Context, path: String): WallpaperImage? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "wallpapers/" + path.replace('/', '_'))
        if (!file.exists()) {
            val url = BASE + path.split('/').joinToString("/") { Uri.encode(it) }
            val bytes = download(url) ?: return@withContext null
            runCatching {
                file.parentFile?.mkdirs()
                val part = File(file.path + ".part")
                part.writeBytes(bytes)
                part.renameTo(file)
            }
        }
        decode(file)
    }

    private fun parse(text: String?): WallpaperCatalog {
        if (text.isNullOrBlank()) return WallpaperCatalog.EMPTY
        return try {
            val array = JSONObject(text).getJSONArray("categories")
            val categories = (0 until array.length()).mapNotNull { i ->
                val c = array.getJSONObject(i)
                val files = c.getJSONArray("files")
                val list = (0 until files.length()).map { j ->
                    val f = files.getJSONObject(j)
                    WallpaperFile(f.getString("p"), f.optString("by").ifBlank { null })
                }
                if (list.isEmpty()) null else WallpaperCategory(c.getString("id"), c.optString("name", c.getString("id")), list)
            }
            WallpaperCatalog(categories)
        } catch (_: Exception) {
            WallpaperCatalog.EMPTY
        }
    }

    private fun decode(file: File): WallpaperImage? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) {
                file.delete()
                return null
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
            WallpaperImage(bitmap.asImageBitmap(), blurredCopy(bitmap).asImageBitmap())
        } catch (_: Throwable) {
            null // includes OutOfMemoryError on very small devices: keep the current wallpaper
        }
    }

    /**
     * A heavily blurred 480x270 version, cropped to 16:9 first so it lines up
     * with the full-screen photo. Blurring a tiny copy keeps it cheap.
     */
    private fun blurredCopy(source: Bitmap): Bitmap {
        val wanted = 16f / 9f
        val ratio = source.width.toFloat() / source.height
        val cropped = when {
            ratio > wanted -> {
                val w = (source.height * wanted).toInt()
                Bitmap.createBitmap(source, (source.width - w) / 2, 0, w, source.height)
            }
            ratio < wanted -> {
                val h = (source.width / wanted).toInt()
                Bitmap.createBitmap(source, 0, (source.height - h) / 2, source.width, h)
            }
            else -> source
        }
        val small = Bitmap.createScaledBitmap(cropped, 192, 108, true)
        return Bitmap.createScaledBitmap(boxBlur(small, radius = 5, passes = 3), 480, 270, true)
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        var src = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var dst = IntArray(w * h)
        repeat(passes) {
            blurPass(src, dst, w, h, radius, horizontal = true)
            blurPass(dst, src, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(src, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val count = radius * 2 + 1
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                for (k in -radius..radius) {
                    val sx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                    val sy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                    val p = src[sy * w + sx]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                }
                dst[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    private fun download(url: String, limit: Int = 12_000_000): ByteArray? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > limit) return null
                        out.write(buffer, 0, n)
                    }
                }
                out.toByteArray()
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
