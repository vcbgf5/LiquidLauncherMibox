package com.liquidglass.tvlauncher.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings

/**
 * Advertises this box on the LAN (NsdManager/mDNS, service type [SERVICE_TYPE]) so the Pilot app can
 * find it by name instead of typing an IP. Only the name and port go out this way — anything on the
 * Wi-Fi can read an NSD record, so the secret token never travels here, only in the QR code / the
 * phone's saved link, exactly as before.
 */
class BoxDiscovery(private val context: Context) {
    private val manager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var listener: NsdManager.RegistrationListener? = null

    fun start(port: Int) {
        if (listener != null || port <= 0) return
        val info = NsdServiceInfo().apply {
            serviceName = boxName(context)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                listener = null
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            listener = registration
        } catch (_: Exception) {
            // no NSD on this box: the phone still works by QR code / manual entry
        }
    }

    fun stop() {
        val registration = listener ?: return
        listener = null
        try {
            manager.unregisterService(registration)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SERVICE_TYPE = "_liquidtv._tcp."

        /** The same name Spotify is shown (see SpotifyHub.deviceForThisBox): the device's own name, else its model. */
        fun boxName(context: Context): String =
            runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}
