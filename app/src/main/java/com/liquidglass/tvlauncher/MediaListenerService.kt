package com.liquidglass.tvlauncher

import android.service.notification.NotificationListenerService

/**
 * Does nothing by itself. Android only lets an app see what the media apps are playing (title,
 * artist, the play/pause controls) when it is a notification listener, so this empty one is what the
 * "Now playing" widget asks for in Settings → System → "Dostęp do powiadomień".
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    companion object {
        @Volatile
        var connected = false
            private set
    }
}
