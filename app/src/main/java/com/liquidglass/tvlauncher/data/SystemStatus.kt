package com.liquidglass.tvlauncher.data

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkKind { OFFLINE, WIFI, ETHERNET, OTHER }

/**
 * What the box is connected to, for the status icons: the network it is on and whether
 * Bluetooth is on ([bluetooth] is null when the box has no Bluetooth at all).
 */
data class SystemStatus(val network: NetworkKind, val bluetooth: Boolean?) {
    companion object {
        val UNKNOWN = SystemStatus(NetworkKind.OFFLINE, null)

        fun read(context: Context): SystemStatus {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = try {
                manager?.getNetworkCapabilities(manager.activeNetwork)
            } catch (_: Exception) {
                null
            }
            val network = when {
                capabilities == null -> NetworkKind.OFFLINE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                else -> NetworkKind.OTHER
            }
            val bluetooth = try {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled
            } catch (_: Throwable) {
                null
            }
            return SystemStatus(network, bluetooth)
        }
    }
}
