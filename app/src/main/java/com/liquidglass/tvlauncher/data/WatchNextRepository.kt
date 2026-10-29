package com.liquidglass.tvlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Continue watching" entries that apps such as Netflix and Disney+ publish to Android TV's
 * Watch Next list, newest first. Each one opens that exact title when clicked.
 *
 * Android only lets an app read the Watch Next entries it published itself; reading other
 * apps' entries needs a system-level permission that an installed launcher doesn't get.
 * So this returns an empty list unless the device allows it. It is only attempted when the
 * permission is already granted (nothing is requested on screen); it can be granted with:
 *
 *     adb shell pm grant com.liquidglass.tvlauncher com.android.providers.tv.permission.READ_EPG_DATA
 *
 * and if the system still refuses, the result is simply empty.
 */
object WatchNextRepository {
    private const val PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"

    suspend fun load(context: Context, limit: Int = 7): List<AppInfo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext emptyList()
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()

        val pm = context.packageManager
        val items = ArrayList<AppInfo>()
        try {
            val projection = arrayOf(
                BaseColumns._ID,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            )
            val order = TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC"
            context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, projection, null, null, order)?.use { c ->
                while (c.moveToNext() && items.size < limit) {
                    val intentUri = c.getString(3) ?: continue
                    val title = c.getString(1) ?: continue
                    val pkg = c.getString(2) ?: continue
                    val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                    val poster = c.getString(4)?.let { loadPoster(context, it) }
                    val art = if (poster != null) TileArt.fromPoster(poster) else TileArt.labelled(title, appName)
                    items += AppInfo(
                        label = title,
                        packageName = pkg,
                        className = "watchnext/" + c.getLong(0),
                        tile = art.asImageBitmap(),
                        intentUri = intentUri,
                    )
                }
            }
        } catch (_: Exception) {
            // SecurityException (not allowed) or anything else: nothing to show.
        }
        items
    }

    private fun loadPoster(context: Context, uri: String): Bitmap? {
        return try {
            val bytes = if (uri.startsWith("http")) {
                Net.download(uri, 6_000_000)
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 800) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) {
            null
        }
    }
}
