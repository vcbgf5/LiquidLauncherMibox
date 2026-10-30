package com.liquidglass.tvlauncher.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import com.liquidglass.tvlauncher.BuildConfig
import com.liquidglass.tvlauncher.R

/**
 * Which build this is. Every release has the normal APK (`LiquidTV.apk`) and a "lite" one
 * (`LiquidTVLite.apk`) for old TVs with about 0.5 GB of RAM: the same app and key, so one replaces the
 * other, but the lite one starts in the economy mode, keeps the windows plain and holds every picture
 * (wallpaper, tiles, icons) in far less memory. Each edition updates itself from its own file.
 */
object Edition {
    val lite: Boolean = BuildConfig.LITE

    /** The release file this edition updates from. */
    val apkName: String = if (lite) "LiquidTVLite.apk" else "LiquidTV.apk"

    /** The picture built into the app (shown before any wallpaper is downloaded); the lite edition keeps it half-size in 565. */
    fun bundledHero(resources: Resources): ImageBitmap {
        if (!lite) return ImageBitmap.imageResource(resources, R.drawable.bg_hero)
        // The lite edition ships its own, already half-size picture (src/lite/res/drawable/bg_hero.jpg).
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val decoded = BitmapFactory.decodeResource(resources, R.drawable.bg_hero, options)
        return (decoded ?: Bitmap.createBitmap(16, 9, Bitmap.Config.RGB_565)).asImageBitmap()
    }
}
