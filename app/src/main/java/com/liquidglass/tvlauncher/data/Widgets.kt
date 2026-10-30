package com.liquidglass.tvlauncher.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.SystemClock
import android.os.Build
import android.os.StatFs
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** The widgets that can be put on the page above the home screen, in the order they appear. */
enum class WidgetType(val key: String) {
    CALENDAR("calendar"),
    AGENDA("agenda"),
    WEATHER("weather"),
    NOW_PLAYING("nowplaying"),
    SPOTIFY("spotify"),
    NETWORK("network"),
    BLUETOOTH("bluetooth"),
    SYSTEM("system"),
    ;

    companion object {
        val DEFAULT_KEYS = setOf(CALENDAR.key, AGENDA.key, WEATHER.key, NOW_PLAYING.key, NETWORK.key, BLUETOOTH.key)
    }
}

class NetworkDetails(val kind: NetworkKind, val signalPercent: Int?, val linkMbps: Int?, val ip: String?)

/** [enabled] is null when the box has no Bluetooth; [needsPermission] when Android hides the paired devices. */
class BluetoothDetails(val enabled: Boolean?, val devices: List<String>, val needsPermission: Boolean)

class SystemDetails(
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val ramFreeBytes: Long,
    val ramTotalBytes: Long,
    val uptimeMillis: Long,
    val androidVersion: String,
    val model: String,
)

class WidgetData(val network: NetworkDetails, val bluetooth: BluetoothDetails, val system: SystemDetails) {
    companion object {
        val EMPTY = WidgetData(
            NetworkDetails(NetworkKind.OFFLINE, null, null, null),
            BluetoothDetails(null, emptyList(), false),
            SystemDetails(0, 0, 0, 0, 0, "", ""),
        )
    }
}

/** Reads the numbers the widgets show. Everything here needs only ordinary (install-time) permissions. */
object WidgetDataReader {

    fun read(context: Context): WidgetData = WidgetData(network(context), bluetooth(context), system(context))

    @Suppress("DEPRECATION")
    private fun network(context: Context): NetworkDetails {
        val kind = SystemStatus.read(context).network
        var signal: Int? = null
        var mbps: Int? = null
        if (kind == NetworkKind.WIFI) {
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wifi?.connectionInfo
                if (info != null) {
                    if (info.rssi > -127) signal = ((info.rssi + 100) * 2).coerceIn(0, 100)
                    if (info.linkSpeed > 0) mbps = info.linkSpeed
                }
            } catch (_: Throwable) {
            }
        }
        return NetworkDetails(kind, signal, mbps, localAddress())
    }

    /** The box's address on the local network, or null when it has none. */
    fun localAddress(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Throwable) {
        null
    }

    private fun bluetooth(context: Context): BluetoothDetails {
        val adapter = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } catch (_: Throwable) {
            null
        } ?: return BluetoothDetails(null, emptyList(), false)
        val enabled = try {
            adapter.isEnabled
        } catch (_: Throwable) {
            false
        }
        var devices = emptyList<String>()
        var needsPermission = false
        if (enabled) {
            try {
                devices = adapter.bondedDevices.map { it.name ?: it.address }.sorted()
            } catch (_: SecurityException) {
                needsPermission = true // Android 12+ asks for the "nearby devices" permission
            } catch (_: Throwable) {
            }
        }
        return BluetoothDetails(enabled, devices, needsPermission)
    }

    private fun system(context: Context): SystemDetails {
        var storageFree = 0L
        var storageTotal = 0L
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            storageFree = stat.availableBytes
            storageTotal = stat.totalBytes
        } catch (_: Throwable) {
        }
        var ramFree = 0L
        var ramTotal = 0L
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            ramFree = info.availMem
            ramTotal = info.totalMem
        } catch (_: Throwable) {
        }
        return SystemDetails(
            storageFree, storageTotal, ramFree, ramTotal,
            SystemClock.elapsedRealtime(), Build.VERSION.RELEASE ?: "", Build.MODEL ?: "",
        )
    }
}
