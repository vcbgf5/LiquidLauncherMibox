package com.liquidglass.tvlauncher.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal downloader (no extra libraries): bytes of a URL, or null on any failure. */
object Net {
    fun download(url: String, limit: Int = 12_000_000): ByteArray? {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                useCaches = false
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > limit) return null
                        out.write(buffer, 0, n)
                    }
                }
                out.toByteArray()
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
