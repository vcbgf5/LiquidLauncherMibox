package com.liquidglass.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.liquidglass.tvlauncher.data.KeyLearner
import com.liquidglass.tvlauncher.data.KeyLog
import com.liquidglass.tvlauncher.data.LauncherSettings
import com.liquidglass.tvlauncher.data.VolumeRouting
import kotlin.math.abs

/**
 * Lets the launcher reach past its own screen, once the user switches it on in Android's
 * accessibility settings. It reads nothing from the screen; it does these things:
 *
 *  - Volume: shows the launcher's own bar at the top, above whatever app is in front, Netflix
 *    included. The bar is driven by *changes of the volume* (the broadcast Android sends, backed up by
 *    a settings observer), not by key presses, because on a TV the keys are handled by the system
 *    itself and a Bluetooth speaker or an HDMI device changes the volume without any key at all.
 *    Over HDMI the TV owns the volume and the box cannot read it, so the bar then shows which way a
 *    key turned it. In [LauncherSettings.VOLUME_REPLACE] Android's own bar is closed again a moment
 *    after every volume key or change. The keys themselves are never swallowed.
 *  - The remote button taught as "quick settings": works in any app.
 *  - Power: [showPowerDialog] asks Android for its "power off / restart" dialog, which no ordinary
 *    app is allowed to do.
 */
class SystemService : AccessibilityService() {

    private var overlay: VolumeOverlay? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: LauncherSettings
    private lateinit var audio: AudioManager

    private var lastLevel = -1
    private var lastMuted = false

    /**
     * Set once the music volume has been seen changing by part of its range, which proves the level is a
     * real one (a TV output that only flips between 0 and full, or that the box cannot see, never does).
     */
    private var levelReal = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream == AudioManager.STREAM_MUSIC) volumeChanged("sygnał systemu")
        }
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            if (level != lastLevel || muted != lastMuted) volumeChanged("ustawienia")
        }
    }

    private val closeDialogs = Runnable {
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Throwable) {
            // Android 12+ refuses this for apps: the bar then simply stays, ours is drawn above it.
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        settings = LauncherSettings(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        overlay = VolumeOverlay(this)
        lastLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        // These two broadcasts are what Android's own volume bar listens to; they are not in the public SDK.
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        try {
            registerReceiver(volumeReceiver, filter)
        } catch (e: Throwable) {
            Log.w(TAG, "volume receiver: $e")
        }
        try {
            contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        } catch (e: Throwable) {
            Log.w(TAG, "settings observer: $e")
        }
        Log.i(TAG, "service connected")
    }

    /**
     * The volume (or the mute) changed, whoever did it. When the level is real (a Bluetooth speaker,
     * a built-in output) the bar shows it. Through HDMI the box's number only flips between 0 and
     * full, so there the bar is driven by the key presses instead (see [onKeyEvent]).
     */
    private fun volumeChanged(source: String) {
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (lastLevel >= 0 && abs(level - lastLevel) in 1 until max) levelReal = true
        lastLevel = level
        lastMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        changesSeen++
        lastChange = source
        lastChangeAt = System.currentTimeMillis()
        val mode = settings.volumeMode
        if (mode == LauncherSettings.VOLUME_OFF || !(levelReal || VolumeRouting.levelIsReal(audio))) return
        showCurrent()
        if (mode == LauncherSettings.VOLUME_REPLACE) hideSystemBar()
    }

    /** Android's bar appears a moment after the change: ask for it to be closed a few times, then once more late. */
    private fun hideSystemBar() {
        if (Build.VERSION.SDK_INT >= 31) return
        handler.removeCallbacks(closeDialogs)
        for (delay in longArrayOf(0, 60, 140, 260, 400, 600, 900)) handler.postDelayed(closeDialogs, delay)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        KeyLog.record("usługa", event)
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.i(TAG, "key ${KeyLog.name(code)} ($code) device=${event.device?.name}")
        }

        // Learning which button opens the quick settings.
        if (KeyLearner.offer(event, settings)) return true
        if (settings.quickKey != 0 && code == settings.quickKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openQuickSettings()
            return true
        }

        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false
        }
        keysSeen++
        lastKey = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_") + if (event.action == KeyEvent.ACTION_UP) " (puszczony)" else ""
        lastKeyAt = System.currentTimeMillis()

        // The keys always go on to Android untouched, so the volume can never stop working because of
        // this service; the bar only reports, and Android's own bar is closed when that is wanted.
        val mode = settings.volumeMode
        if (mode == LauncherSettings.VOLUME_OFF || event.action != KeyEvent.ACTION_DOWN) return false
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mutedBefore = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        // Give the system a moment to apply the key, then look at what it did to the level.
        handler.postDelayed({ afterVolumeKey(code, before, mutedBefore) }, 260)
        if (mode == LauncherSettings.VOLUME_REPLACE) hideSystemBar()
        return false
    }

    /**
     * The level moved by part of its range: it is real, show it. It flipped between 0 and full or did not
     * move at all (the TV sets the volume over HDMI-CEC and the box cannot read it): show only which
     * way the key turned it, unless the level was proven real earlier (then it is just at its end).
     */
    private fun afterVolumeKey(code: Int, before: Int, mutedBefore: Boolean) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muteChanged = audio.isStreamMute(AudioManager.STREAM_MUSIC) != mutedBefore
        if (abs(after - before) in 1 until max) levelReal = true
        if (levelReal || VolumeRouting.levelIsReal(audio) || (muteChanged && code == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            showCurrent()
        } else {
            overlay?.showDirection(
                when (code) {
                    KeyEvent.KEYCODE_VOLUME_UP -> 1
                    KeyEvent.KEYCODE_VOLUME_DOWN -> -1
                    else -> 0
                },
            )
        }
    }

    /** The taught button: brings the launcher up with its quick settings open, from any app. */
    private fun openQuickSettings() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("open_quick", true),
            )
        } catch (e: Throwable) {
            Log.w(TAG, "quick settings: $e")
        }
    }

    private fun showCurrent() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val percent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        overlay?.show(percent, audio.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(volumeReceiver) }
        runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
        overlay?.release()
        overlay = null
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        Log.i(TAG, "service disconnected")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "LiquidTV"

        @Volatile
        private var instance: SystemService? = null

        /** What the service has seen, for the volume diagnostics in the settings. */
        @Volatile
        var keysSeen = 0
            private set

        @Volatile
        var lastKey = ""
            private set

        @Volatile
        var lastKeyAt = 0L
            private set

        /** Changes of the volume noticed (by the system broadcast or the settings observer), and the last one. */
        @Volatile
        var changesSeen = 0
            private set

        @Volatile
        var lastChange = ""
            private set

        @Volatile
        var lastChangeAt = 0L
            private set

        val connected: Boolean get() = instance != null

        /** Opens the system power dialog; false when the service isn't switched on. */
        fun showPowerDialog(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false

        /** Draws the bar once with the given level, to test that it can be shown at all; false without the service. */
        fun testOverlay(percent: Int, muted: Boolean = false): Boolean {
            val service = instance ?: return false
            service.overlay?.show(percent, muted)
            return true
        }
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

    /** The TV's volume is unknown: show only which way it was turned (1 up, -1 down, 0 mute/unmute). */
    fun showDirection(direction: Int) {
        view.setDirection(direction)
        display()
    }

    fun show(percent: Int, muted: Boolean) {
        view.set(percent, muted)
        display()
    }

    private fun display() {
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

    /** null = a real level is shown; otherwise only the way the TV's volume was turned (1, -1) or muted (0). */
    private var direction: Int? = null

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
        this.direction = null
        invalidate()
    }

    fun setDirection(direction: Int) {
        this.direction = direction
        this.muted = direction == 0
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
        val relative = direction
        val level = if (muted) 0 else if (relative != null) 50 else percent
        if (level == 0) {
            val x = sx + 16f * d
            canvas.drawLine(x, cy - 5f * d, x + 10f * d, cy + 5f * d, icon)
            canvas.drawLine(x + 10f * d, cy - 5f * d, x, cy + 5f * d, icon)
        } else {
            canvas.drawArc(RectF(sx + 8f * d, cy - 6f * d, sx + 20f * d, cy + 6f * d), -50f, 100f, false, icon)
            canvas.drawArc(RectF(sx + 5f * d, cy - 11f * d, sx + 25f * d, cy + 11f * d), -50f, 100f, false, icon)
        }

        if (relative != null) {
            // The TV's level is unknown: a word and a triangle for the way the volume was turned.
            val word = if (relative == 0) "Wyciszenie" else "Głośność"
            canvas.drawText(word, 66f * d, cy + text.textSize * 0.35f, text)
            if (relative != 0) {
                val cx = w - 52f * d
                val half = 11f * d
                val triangle = Path().apply {
                    if (relative > 0) {
                        moveTo(cx, cy - half)
                        lineTo(cx + half, cy + half * 0.7f)
                        lineTo(cx - half, cy + half * 0.7f)
                    } else {
                        moveTo(cx, cy + half)
                        lineTo(cx + half, cy - half * 0.7f)
                        lineTo(cx - half, cy - half * 0.7f)
                    }
                    close()
                }
                canvas.drawPath(triangle, fill)
            }
            return
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
