package com.liquidglass.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds every launchable app, the same way a stock launcher does.
 *
 * Order: the [PriorityApps] first, in their fixed order; then the other TV
 * (leanback) apps; then everything else, so a sideloaded phone app never
 * pushes a real TV app out of view. The last two groups are alphabetical.
 */
object AppRepository {

    suspend fun load(context: Context): List<AppInfo> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val self = context.packageName
        val seen = HashSet<String>()
        val result = ArrayList<AppInfo>()

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val group = ArrayList<AppInfo>()
            for (info in query(pm, category)) {
                val pkg = info.activityInfo.packageName
                if (pkg == self) continue
                if (!seen.add(pkg + "/" + info.activityInfo.name)) continue
                runCatching { toAppInfo(pm, info) }.getOrNull()?.let(group::add)
            }
            result += group.sortedBy { it.label.lowercase() }
        }

        // Stable sort: apps outside the priority list keep the order built above.
        result.sortedBy { PriorityApps.rank(it.packageName) }
    }

    private fun toAppInfo(pm: PackageManager, info: ResolveInfo): AppInfo {
        val banner = info.activityInfo.loadBanner(pm)
        val bitmap = if (banner != null) TileArt.fromBanner(banner) else TileArt.fromIcon(info.loadIcon(pm))
        return AppInfo(
            label = info.loadLabel(pm)?.toString() ?: info.activityInfo.packageName,
            packageName = info.activityInfo.packageName,
            className = info.activityInfo.name,
            tile = bitmap.asImageBitmap(),
        )
    }

    private fun query(pm: PackageManager, category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
