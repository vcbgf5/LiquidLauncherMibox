package com.liquidglass.tvlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Fetches wallpapers from this project's own GitHub repo (the `wallpapers/`
 * folder, served by raw.githubusercontent.com) and keeps what it downloaded
 * in the app's cache, so a wallpaper that was shown once works offline.
 */
object WallpaperRepository {
    private const val BASE = "https://raw.githubusercontent.com/vcbgf5/LiquidLauncherMibox/main/wallpapers/"
    private const val TARGET_WIDTH = 1920
    private const val TARGET_WIDTH_LITE = 1280

    suspend fun catalog(context: Context): WallpaperCatalog = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "wallpapers/index.json")
        val fresh = Net.download(BASE + "index.json")?.toString(Charsets.UTF_8)
        if (fresh != null) {
            runCatching {
                cache.parentFile?.mkdirs()
                cache.writeText(fresh)
            }
        }
        parse(fresh ?: runCatching { cache.readText() }.getOrNull())
    }

    const val USER_CATEGORY = "user"
    private const val USER_PREFIX = "user/"

    /** Where the photos sent from the phone live (never in the cache, so clearing caches does not lose them). */
    fun userDir(context: Context): File = File(context.filesDir, "user_wallpapers")

    /** [base] plus a "Moje zdjęcia" category made of the photos the user sent, when there are any. */
    fun withUserPhotos(context: Context, base: WallpaperCatalog): WallpaperCatalog {
        val files = userDir(context).listFiles()?.filter { it.isFile }?.sortedBy { it.name }
            ?.map { WallpaperFile(USER_PREFIX + it.name, null) }.orEmpty()
        if (files.isEmpty()) return base
        return WallpaperCatalog(base.categories.filter { it.id != USER_CATEGORY } + WallpaperCategory(USER_CATEGORY, "Moje zdjęcia", files))
    }

    /** Downloads (once) and decodes one wallpaper; null if it can't be had or read. */
    suspend fun load(context: Context, path: String): WallpaperImage? = withContext(Dispatchers.IO) {
        if (path.startsWith(USER_PREFIX)) {
            val own = File(userDir(context), path.removePrefix(USER_PREFIX))
            return@withContext if (own.isFile) decode(own) else null
        }
        val file = File(context.cacheDir, "wallpapers/" + path.replace('/', '_'))
        if (!file.exists()) {
            val url = BASE + path.split('/').joinToString("/") { Uri.encode(it) }
            val bytes = Net.download(url) ?: return@withContext null
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
            val target = if (Performance.lite) TARGET_WIDTH_LITE else TARGET_WIDTH
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2
            // Mutable, because the darkening for bright photos is painted into the picture itself.
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inMutable = true
            }
            val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
            bakeScrim(bitmap)
            WallpaperImage(bitmap.asImageBitmap(), blurredCopy(bitmap).asImageBitmap())
        } catch (_: Throwable) {
            null // includes OutOfMemoryError on very small devices: keep the current wallpaper
        }
    }

    /**
     * Keeps the clock at the top and the app names at the bottom readable on bright photos: how
     * bright those two bands are is measured on a tiny copy, and the picture is darkened towards its
     * top and bottom edge by as much as it needs (dark photos are left alone). Painting it into the
     * bitmap once means drawing it later costs nothing, unlike a gradient laid over the screen.
     */
    private fun bakeScrim(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val small = Bitmap.createScaledBitmap(bitmap, 64, 36, true)
        fun brightness(fromRow: Int, toRow: Int): Float {
            var sum = 0f
            var count = 0
            for (y in fromRow until toRow) {
                for (x in 0 until 64) {
                    val p = small.getPixel(x, y)
                    sum += (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
                    count++
                }
            }
            return if (count == 0) 0f else sum / count
        }
        fun strength(brightness: Float) = ((brightness - 0.30f) * 1.3f).coerceIn(0f, 0.55f)
        val top = strength(brightness(0, 14))
        val bottom = strength(brightness(22, 36))
        small.recycle()
        if (top < 0.02f && bottom < 0.02f) return
        val canvas = Canvas(bitmap)
        val paint = Paint()
        if (top >= 0.02f) {
            paint.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, Color.argb((top * 255).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.45f, paint)
        }
        if (bottom >= 0.02f) {
            paint.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb((bottom * 255).toInt(), 0, 0, 0), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), paint)
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
}
