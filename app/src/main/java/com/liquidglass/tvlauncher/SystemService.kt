package com.liquidglass.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.liquidglass.tvlauncher.data.LauncherSettings

/**
 * Lets the launcher reach past its own screen, once the user switches it on in Android's
 * accessibility settings. It reads nothing from the screen; it does two things:
 *
 *  - Volume: the volume keys are taken here (whatever app is in front, Netflix included), the
 *    volume is changed, and the launcher's own bar slides in at the top instead of Android's.
 *    When the box has fixed volume (the TV controls it) the keys are left alone.
 *  - Power: [showPowerDialog] asks Android for its "power off / restart" dialog, which no ordinary
 *    app is allowed to do.
 */
class SystemService : AccessibilityService() {

    private var overlay: VolumeOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = VolumeOverlay(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false
        }
        if (!LauncherSettings(this).customVolume) return false
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.isVolumeFixed) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = when (code) {
                KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                else -> AudioManager.ADJUST_TOGGLE_MUTE
            }
            // Flag 0: no system volume bar, ours is the one shown.
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val percent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
            overlay?.show(percent, audio.isStreamMute(AudioManager.STREAM_MUSIC))
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        overlay?.release()
        overlay = null
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        private var instance: SystemService? = null

        /** Opens the system power dialog; false when the service isn't switched on. */
        fun showPowerDialog(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
    }
}

/** The volume bar drawn above every app: a glass-dark pill with a speaker, a level bar and the percentage. */
private class VolumeOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val view = VolumeBarView(context)
    private val density = context.resources.displayMetrics.density
    private var attached = false

    private val hide = Runnable {
        view.animate().alpha(0f).translationY(-30f * density).setDuration(220).withEndAction { remove() }.start()
    }

    fun show(percent: Int, muted: Boolean) {
        view.set(percent, muted)
        handler.removeCallbacks(hide)
        view.animate().cancel()
        if (!attached) {
            view.alpha = 0f
            view.translationY = -30f * density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (28 * density).toInt()
            }
            try {
                windowManager.addView(view, params)
                attached = true
            } catch (_: Exception) {
                return
            }
        }
        view.animate().alpha(1f).translationY(0f).setDuration(180).start()
        handler.postDelayed(hide, 2200)
    }

    private fun remove() {
        if (attached) {
            runCatching { windowManager.removeView(view) }
            attached = false
        }
    }

    fun release() {
        handler.removeCallbacks(hide)
        view.animate().cancel()
        remove()
    }
}

private class VolumeBarView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private var percent = 0
    private var muted = false

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEB1C1E26.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        color = 0x40FFFFFF
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun set(percent: Int, muted: Boolean) {
        this.percent = percent.coerceIn(0, 100)
        this.muted = muted
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((400 * d).toInt(), (58 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, background)
        canvas.drawRoundRect(RectF(d / 2, d / 2, w - d / 2, h - d / 2), radius, radius, border)

        // Speaker: a small body, and either sound waves or a cross when muted.
        val sx = 24f * d
        val cy = h / 2f
        val body = Path().apply {
            moveTo(sx, cy - 4f * d)
            lineTo(sx + 5f * d, cy - 4f * d)
            lineTo(sx + 11f * d, cy - 10f * d)
            lineTo(sx + 11f * d, cy + 10f * d)
            lineTo(sx + 5f * d, cy + 4f * d)
            lineTo(sx, cy + 4f * d)
            close()
        }
        val fillIcon = Paint(icon).apply { style = Paint.Style.FILL }
        canvas.drawPath(body, fillIcon)
        val level = if (muted) 0 else percent
        if (level == 0) {
            val x = sx + 16f * d
            canvas.drawLine(x, cy - 5f * d, x + 10f * d, cy + 5f * d, icon)
            canvas.drawLine(x + 10f * d, cy - 5f * d, x, cy + 5f * d, icon)
        } else {
            canvas.drawArc(RectF(sx + 8f * d, cy - 6f * d, sx + 20f * d, cy + 6f * d), -50f, 100f, false, icon)
            canvas.drawArc(RectF(sx + 5f * d, cy - 11f * d, sx + 25f * d, cy + 11f * d), -50f, 100f, false, icon)
        }

        // Level bar and percentage.
        val barLeft = 66f * d
        val barRight = w - 82f * d
        val barTop = cy - 4f * d
        val barBottom = cy + 4f * d
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 4f * d, 4f * d, track)
        if (level > 0) {
            val end = barLeft + (barRight - barLeft) * level / 100f
            canvas.drawRoundRect(RectF(barLeft, barTop, end, barBottom), 4f * d, 4f * d, fill)
        }
        val label = if (muted) "—" else "$percent%"
        canvas.drawText(label, w - 70f * d, cy + text.textSize * 0.35f, text)
    }
}
