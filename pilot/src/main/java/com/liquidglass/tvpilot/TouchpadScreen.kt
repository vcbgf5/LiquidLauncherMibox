package com.liquidglass.tvpilot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** How often accumulated gyroscope deltas are sent to the box. Samples arrive far faster than this
 * (SENSOR_DELAY_GAME), so they are coalesced into one small POST instead of one per sample. */
private const val SEND_PERIOD_MS = 33L // ~30 updates/sec

/**
 * Turns the phone's own motion into a stream of small (dx, dy) cursor deltas, in roughly the box
 * screen's pixel units. Prefers the gyroscope: it reports an angular *rate*, so each sample's own
 * turn (rate × elapsed time) is used directly as a delta. Falls back to the game rotation vector,
 * then plain accelerometer tilt, when a phone has no gyroscope — both report an *orientation*
 * instead, so their delta is the change since the previous sample.
 *
 * Everything here runs on the thread [start] was called from (the main thread, since no Handler is
 * passed to registerListener), the same thread [take] is read from, so no locking is needed.
 */
class CursorMotion(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensor = gyroscope ?: rotationVector ?: accelerometer

    /** Whether any usable motion sensor was found on this phone. */
    val available: Boolean get() = sensor != null

    private var lastNanos = 0L
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var haveLastAngle = false

    private var pendingDx = 0f
    private var pendingDy = 0f

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    /** Empties the delta accumulated since the last call and returns what it held. */
    fun take(): Pair<Float, Float> {
        val result = pendingDx to pendingDy
        pendingDx = 0f
        pendingDy = 0f
        return result
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                if (lastNanos != 0L) {
                    val dt = (event.timestamp - lastNanos) / 1_000_000_000f
                    // values[0..2] are rad/s turned around the phone's own x/y/z axis. Held like a
                    // remote: tilting the top left/right (y axis) moves the dot sideways, tilting it
                    // forward/back (x axis) moves the dot up/down.
                    add(event.values[1] * dt, -event.values[0] * dt)
                }
                lastNanos = event.timestamp
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(matrix, orientation)
                updateFromAngles(orientation[0], orientation[1])
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // No gyroscope or rotation vector on this phone: a rougher tilt straight from gravity.
                val roll = atan2(event.values[0], event.values[2])
                val pitch = atan2(-event.values[1], event.values[2])
                updateFromAngles(roll, pitch)
            }
        }
    }

    /** For the two orientation-based sensors: the delta is the change since the previous sample. */
    private fun updateFromAngles(yaw: Float, pitch: Float) {
        if (haveLastAngle) {
            add(angleDiff(yaw, lastYaw), -angleDiff(pitch, lastPitch))
        }
        lastYaw = yaw
        lastPitch = pitch
        haveLastAngle = true
    }

    private fun add(angleX: Float, angleY: Float) {
        pendingDx += (angleX * SENSITIVITY).coerceIn(-MAX_STEP, MAX_STEP)
        pendingDy += (angleY * SENSITIVITY).coerceIn(-MAX_STEP, MAX_STEP)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        /** Pixels of cursor movement per radian turned; picked by feel (there's no device to try it on). */
        private const val SENSITIVITY = 900f

        /** Caps one sample's contribution, so a spike (or just picking the phone up) can't fling the dot across the screen. */
        private const val MAX_STEP = 40f

        // Not `const`: Math.PI.toFloat() is a function call, which Kotlin doesn't allow in a const initializer.
        private val PI_F = Math.PI.toFloat()

        private fun angleDiff(a: Float, b: Float): Float {
            var diff = a - b
            while (diff > PI_F) diff -= 2 * PI_F
            while (diff < -PI_F) diff += 2 * PI_F
            return diff
        }
    }
}

/**
 * Full-screen "mouse": tilt the phone to move a dot on the TV, tap anywhere here to click where it
 * is. Opened from the Pilot tab; "Zamknij" (or the phone's own Back, see PilotApp) removes this
 * screen, which disposes [CursorMotion] and cancels the sending loop below — cleanly unregistering
 * the sensor and stopping updates. See RemoteServer's /cursor and MainActivity.receiveCursor on the
 * box for the rest of the design: the dot there moves purely in Compose, adb only fires on a tap.
 */
@Composable
fun TouchpadScreen(link: BoxLink, onClose: () -> Unit) {
    val context = LocalContext.current
    val motion = remember { CursorMotion(context) }
    var tapPending by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        motion.start()
        onDispose { motion.stop() }
    }

    // Coalesces the gyroscope's own sample rate down to a steady, modest number of small POSTs.
    LaunchedEffect(link) {
        while (isActive) {
            delay(SEND_PERIOD_MS)
            val (dx, dy) = motion.take()
            val tap = tapPending
            if (tap) tapPending = false
            if (dx != 0f || dy != 0f || tap) {
                withContext(Dispatchers.IO) { Box.cursor(link, dx, dy, tap) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.top, Pal.bottom)))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tapPending = true
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                PilotIcons.Cursor,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                if (motion.available) "Przechylaj telefon — kursor rusza się na TV" else "Ten telefon nie ma czujnika ruchu do tego trybu",
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            Text("Dotknij gdziekolwiek na ekranie, żeby kliknąć w miejscu kursora", color = Pal.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        PilotButton(
            "Zamknij",
            onClose,
            Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(0.7f),
            icon = PilotIcons.Back,
        )
    }
}
