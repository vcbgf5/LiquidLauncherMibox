package com.liquidglass.tvlauncher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Builds the wide 5:3 artwork tvOS-style tiles are made of.
 *
 * TV apps ship a 16:9 banner, which is cropped to fill the tile. Phone-style
 * apps only have a square icon; those get a gradient taken from the icon's
 * own colours with the icon centred on top, so the grid stays uniform.
 */
object TileArt {
    const val WIDTH = 400
    const val HEIGHT = 240

    fun fromBanner(banner: Drawable): Bitmap {
        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val iw = banner.intrinsicWidth
        val ih = banner.intrinsicHeight
        if (iw <= 0 || ih <= 0) {
            banner.setBounds(0, 0, WIDTH, HEIGHT)
        } else {
            val scale = maxOf(WIDTH.toFloat() / iw, HEIGHT.toFloat() / ih)
            val dw = iw * scale
            val dh = ih * scale
            banner.setBounds(
                ((WIDTH - dw) / 2).toInt(),
                ((HEIGHT - dh) / 2).toInt(),
                ((WIDTH + dw) / 2).toInt(),
                ((HEIGHT + dh) / 2).toInt(),
            )
        }
        banner.draw(canvas)
        return out
    }

    fun fromIcon(icon: Drawable): Bitmap {
        val size = 192
        val iconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val iconCanvas = Canvas(iconBitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            // Adaptive icons are 108dp with only the middle 72dp visible, so
            // draw them oversized and let the bitmap edge do the cropping.
            val pad = (size * 0.25f).toInt()
            icon.setBounds(-pad, -pad, size + pad, size + pad)
        } else {
            icon.setBounds(0, 0, size, size)
        }
        icon.draw(iconCanvas)

        val palette = Palette.from(iconBitmap).maximumColorCount(12).generate()
        val seed = palette.getDarkMutedColor(palette.getDominantColor(0xFF2C2C2E.toInt()))
        val top = ColorUtils.blendARGB(seed, Color.WHITE, 0.22f)
        val bottom = ColorUtils.blendARGB(seed, Color.BLACK, 0.35f)

        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), backdrop)

        val side = HEIGHT * 0.64f
        val left = (WIDTH - side) / 2f
        val topY = (HEIGHT - side) / 2f
        val dst = RectF(left, topY, left + side, topY + side)
        val clip = Path().apply { addRoundRect(dst, side * 0.22f, side * 0.22f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(iconBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return out
    }
}
