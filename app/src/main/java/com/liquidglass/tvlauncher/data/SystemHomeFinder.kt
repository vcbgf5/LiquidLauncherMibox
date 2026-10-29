package com.liquidglass.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import com.liquidglass.tvlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The device's own home screen (the stock Android TV / Google TV launcher), if it is
 * still enabled. Its "continue watching" row is the one place Netflix and Disney+
 * titles are shown, so this tile jumps there. Returned as an [AppInfo] so it
 * opens like any other tile.
 */
object SystemHomeFinder {
    suspend fun find(context: Context): AppInfo? = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val other = runCatching { pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL) }
            .getOrDefault(emptyList())
            .firstOrNull { it.activityInfo.packageName != context.packageName }
            ?: return@withContext null
        val art = TileArt.labelled(
            context.getString(R.string.info_watch_next),
            context.getString(R.string.info_system_home),
        )
        AppInfo(
            label = context.getString(R.string.info_watch_next),
            packageName = other.activityInfo.packageName,
            className = other.activityInfo.name,
            tile = art.asImageBitmap(),
        )
    }
}
