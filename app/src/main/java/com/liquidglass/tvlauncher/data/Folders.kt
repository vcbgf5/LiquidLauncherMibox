package com.liquidglass.tvlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

/** A named group of apps; [keys] are [AppInfo.key]s, in the order they were added. */
class AppFolder(val name: String, val keys: List<String>)

/** Folders are shown in the app list as tiles of their own; these helpers mark and build them. */
const val FOLDER_PACKAGE = "liquidtv.folder"

val AppInfo.isFolder: Boolean get() = packageName == FOLDER_PACKAGE

/** Remembers the folders and what is in them. An app is in at most one folder. */
class FolderStore(context: Context) {
    private val prefs = context.getSharedPreferences("folders", Context.MODE_PRIVATE)

    fun load(): List<AppFolder> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val keys = item.getJSONArray("k")
            AppFolder(item.getString("n"), (0 until keys.length()).map { keys.getString(it) })
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(folders: List<AppFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(JSONObject().put("n", folder.name).put("k", JSONArray(folder.keys)))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "list"

        /** Names offered when an app is put in a folder (typing a name on a TV is no fun). */
        val PRESETS = listOf("Streaming", "Gry", "Muzyka", "Narzędzia", "Dla dzieci", "Inne")

        /** [folders] with [key] moved into the folder called [name] (made if it doesn't exist). */
        fun add(folders: List<AppFolder>, name: String, key: String): List<AppFolder> {
            val cleaned = remove(folders, key)
            return if (cleaned.any { it.name == name }) {
                cleaned.map { if (it.name == name) AppFolder(name, it.keys + key) else it }
            } else {
                cleaned + AppFolder(name, listOf(key))
            }
        }

        /** [folders] without [key]; a folder that becomes empty disappears. */
        fun remove(folders: List<AppFolder>, key: String): List<AppFolder> =
            folders.map { AppFolder(it.name, it.keys - key) }.filter { it.keys.isNotEmpty() }

        fun folderOf(folders: List<AppFolder>, key: String): AppFolder? = folders.firstOrNull { key in it.keys }
    }
}

/** Draws a folder's tile: dark glass with up to four of its apps' tiles in a 2 x 2 grid. */
object FolderArt {
    fun tile(name: String, members: List<AppInfo>): AppInfo {
        val width = TileArt.WIDTH
        val height = TileArt.HEIGHT
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(70, 74, 92), Color.rgb(26, 28, 38), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val pad = 18f
        val gap = 12f
        val cellW = (width - 2 * pad - gap) / 2f
        val cellH = (height - 2 * pad - gap) / 2f
        members.take(4).forEachIndexed { index, app ->
            val left = pad + (index % 2) * (cellW + gap)
            val top = pad + (index / 2) * (cellH + gap)
            val rect = RectF(left, top, left + cellW, top + cellH)
            val clip = Path().apply { addRoundRect(rect, 12f, 12f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(app.tile.asAndroidBitmap(), null, rect, null)
            canvas.restore()
        }
        return AppInfo(label = name, packageName = FOLDER_PACKAGE, className = name, tile = out.asImageBitmap())
    }
}
