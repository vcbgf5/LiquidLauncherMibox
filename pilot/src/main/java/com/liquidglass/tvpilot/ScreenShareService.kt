package com.liquidglass.tvpilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Shares the phone's screen to the TV: Android's screen capture hands over a picture now and then,
 * each is squeezed into a JPEG (at most 1280 px long, about 8 a second) and posted to the launcher's
 * web server, which shows the latest one full screen. There is no sound. It stops when the notification's
 * Stop is pressed, when the system ends the capture, or when the TV answers "stop" (Back on the remote).
 */
class ScreenShareService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val thread = HandlerThread("liquidtv-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val sender = Executors.newSingleThreadExecutor()

    @Volatile
    private var sending = false
    private var lastSent = 0L

    @Volatile
    private var failures = 0
    private var link: BoxLink? = null
    private var width = 0
    private var height = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        // A service started as a foreground one must say so at once, whatever happens next.
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        link = BoxLink(
            intent.getStringExtra(EXTRA_HOST).orEmpty(),
            intent.getIntExtra(EXTRA_PORT, 8765),
            intent.getStringExtra(EXTRA_TOKEN).orEmpty(),
        )
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSharing()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val granted = try {
            manager.getMediaProjection(code, data)
        } catch (_: Exception) {
            null
        }
        if (granted == null) {
            stopSharing()
            return START_NOT_STICKY
        }
        projection = granted
        granted.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { stopSharing() }
            }
        }, handler)
        startCapture(granted)
        running = true
        return START_NOT_STICKY
    }

    private fun startCapture(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val scale = minOf(1f, 1280f / maxOf(metrics.widthPixels, metrics.heightPixels))
        width = ((metrics.widthPixels * scale).toInt() and 1.inv()).coerceAtLeast(2)
        height = ((metrics.heightPixels * scale).toInt() and 1.inv()).coerceAtLeast(2)
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        display = projection.createVirtualDisplay(
            "liquidtv-share", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, handler,
        )
        imageReader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage()
            if (image != null) {
                try {
                    val now = SystemClock.uptimeMillis()
                    if (!sending && now - lastSent >= 120) {
                        val jpeg = toJpeg(image)
                        lastSent = now
                        sending = true
                        sender.execute { send(jpeg) }
                    }
                } finally {
                    image.close()
                }
            }
        }, handler)
    }

    private fun send(jpeg: ByteArray) {
        try {
            val reply = link?.let { Box.frame(it, jpeg) }.orEmpty()
            failures = 0
            if (reply.trim() == "stop") handler.post { stopSharing() }
        } catch (_: Exception) {
            failures++
            if (failures > 25) handler.post { stopSharing() }
        } finally {
            sending = false
        }
    }

    private fun toJpeg(image: Image): ByteArray {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
        val out = ByteArrayOutputStream(96 * 1024)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 58, out)
        if (bitmap !== padded) padded.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun stopSharing() {
        if (!running && projection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        running = false
        runCatching { reader?.setOnImageAvailableListener(null, null) }
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        val target = link
        if (target != null) Thread { runCatching { Box.actionBlocking(target, "mirror_stop") } }.start()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        thread.quitSafely()
        sender.shutdown()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Udostępnianie ekranu", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Ekran telefonu jest na TV")
            .setContentText("Dotknij Zatrzymaj, żeby przestać")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Zatrzymaj", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "share"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.liquidglass.tvpilot.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TOKEN = "token"

        /** True while the screen is being shared; the app's screen follows it. */
        var running by mutableStateOf(false)
            private set

        fun start(context: Context, resultCode: Int, data: Intent, link: BoxLink) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenShareService::class.java)
                    .putExtra(EXTRA_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data)
                    .putExtra(EXTRA_HOST, link.host)
                    .putExtra(EXTRA_PORT, link.port)
                    .putExtra(EXTRA_TOKEN, link.token),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenShareService::class.java).setAction(ACTION_STOP))
        }
    }
}
