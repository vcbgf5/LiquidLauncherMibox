package com.liquidglass.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack: an app that carries a set of replacement icons (the Nova / ADW format). */
class IconPack(val packageName: String, val label: String)

/**
 * Icon packs installed on the box, so the icon of a single app can be swapped for one from a pack.
 *
 * Packs from Google Play for Nova, Apex, Lawnchair and the like all work the same way: the pack's
 * app announces itself with one of a few well-known intent actions and ships an `appfilter.xml`
 * that says which of its drawables belongs to which app. Those drawables are read straight out
 * of the pack's own resources; nothing is copied.
 */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
    )

    /** What a pack holds: every drawable name (sorted), and which one goes with which app. */
    class Contents(
        val names: List<String>,
        private val byComponent: Map<String, String>,
        private val byPackage: Map<String, String>,
    ) {
        /** The pack's own icon for this app, if it has one. */
        fun match(packageName: String, className: String): String? =
            byComponent["ComponentInfo{$packageName/$className}"] ?: byPackage[packageName]
    }

    private val contentsCache = HashMap<String, Contents>()

    /** The packs that come with the launcher first, then the ones installed on the box. */
    fun installed(context: Context): List<IconPack> = BuiltinIconPacks.packs(context) + installedOnBox(context)

    private fun installedOnBox(context: Context): List<IconPack> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPack>()
        for (action in ACTIONS) {
            val activities = try {
                pm.queryIntentActivities(Intent(action), 0)
            } catch (_: Exception) {
                emptyList()
            }
            for (info in activities) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || pkg in found) continue
                found[pkg] = IconPack(pkg, info.loadLabel(pm)?.toString() ?: pkg)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    suspend fun contents(context: Context, pack: IconPack): Contents = withContext(Dispatchers.Default) {
        if (BuiltinIconPacks.isBuiltin(pack.packageName)) return@withContext BuiltinIconPacks.contents()
        synchronized(contentsCache) { contentsCache[pack.packageName] }
            ?: parse(context, pack).also { synchronized(contentsCache) { contentsCache[pack.packageName] = it } }
    }

    fun drawable(context: Context, packageName: String, name: String): Drawable? = try {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) null else ResourcesCompat.getDrawable(res, id, null)
    } catch (_: Throwable) {
        null
    }

    /** One icon of a pack as a bitmap [size] pixels square, for the picker. */
    suspend fun bitmap(context: Context, pack: IconPack, name: String, size: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            if (BuiltinIconPacks.isBuiltin(pack.packageName)) {
                return@withContext BuiltinIconPacks.tile(context, pack.packageName, name)?.asImageBitmap()
            }
            val drawable = drawable(context, pack.packageName, name) ?: return@withContext null
            try {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }

    private fun parse(context: Context, pack: IconPack): Contents {
        val names = HashSet<String>()
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()

        fun read(parser: XmlPullParser) {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue(null, "drawable")
                    val component = parser.getAttributeValue(null, "component")
                    if (drawable != null) {
                        names += drawable
                        if (component != null) {
                            byComponent[component] = drawable
                            // "ComponentInfo{com.app/com.app.Main}": also remember it by package alone,
                            // for TV apps whose launcher activity the phone-oriented pack doesn't know.
                            val pkg = component.substringAfter('{', "").substringBefore('/', "")
                            if (pkg.isNotEmpty()) byPackage.putIfAbsent(pkg, drawable)
                        }
                    }
                }
                event = parser.next()
            }
        }

        try {
            val res: Resources = context.packageManager.getResourcesForApplication(pack.packageName)
            val xmlId = res.getIdentifier("appfilter", "xml", pack.packageName)
            if (xmlId != 0) {
                val parser = res.getXml(xmlId)
                try {
                    read(parser)
                } finally {
                    parser.close()
                }
            } else {
                res.assets.open("appfilter.xml").use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input, "UTF-8")
                    read(parser)
                }
            }
        } catch (_: Throwable) {
            // A pack that can't be read just shows up empty.
        }
        return Contents(names.sorted(), byComponent, byPackage)
    }
}

/** Which app has which icon from which pack: one choice per app, kept between runs. */
class IconOverrides(context: Context) {
    private val prefs = context.getSharedPreferences("icons", Context.MODE_PRIVATE)

    class Choice(val packageName: String, val drawable: String)

    fun get(appKey: String): Choice? {
        val raw = prefs.getString(appKey, null) ?: return null
        val pack = raw.substringBefore('|', "")
        val drawable = raw.substringAfter('|', "")
        return if (pack.isEmpty() || drawable.isEmpty()) null else Choice(pack, drawable)
    }

    fun set(appKey: String, packageName: String, drawable: String) {
        prefs.edit().putString(appKey, "$packageName|$drawable").apply()
    }

    fun clear(appKey: String) {
        prefs.edit().remove(appKey).apply()
    }
}
