package com.liquidglass.tvpilot

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the box listens: the address in the QR code its screen shows (http://192.168.0.170:8765/?t=secret). */
data class BoxLink(val host: String, val port: Int, val token: String) {
    val base: String get() = "http://$host:$port"

    companion object {
        private val pattern = Regex("""https?://([^/:\s]+):(\d+)/?\?t=([A-Za-z0-9]+)""")

        fun parse(text: String): BoxLink? {
            val match = pattern.find(text.trim()) ?: return null
            return BoxLink(match.groupValues[1], match.groupValues[2].toInt(), match.groupValues[3])
        }
    }
}

/** Remembers the box between runs. */
class LinkStore(context: Context) {
    private val prefs = context.getSharedPreferences("pilot", Context.MODE_PRIVATE)

    fun load(): BoxLink? {
        val host = prefs.getString("host", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return BoxLink(host, prefs.getInt("port", 8765), token)
    }

    fun save(link: BoxLink) {
        prefs.edit().putString("host", link.host).putInt("port", link.port).putString("token", link.token).apply()
    }
}

/** A box seen on the network, before it is chosen: its name and address, never its token (see [BoxFinder]). */
data class FoundBox(val name: String, val host: String, val port: Int)

/**
 * Looks for other Liquid TV boxes on the Wi-Fi (NsdManager/mDNS; the box registers itself under
 * [SERVICE_TYPE] — see BoxDiscovery.kt in the launcher). Only the name, host and port ever travel
 * this way, never the secret token, so a box found here still needs its QR code / a saved link
 * before it can be controlled. [start] keeps calling [onFound] with the growing list found so far;
 * call [stop] once the screen that is searching goes away.
 */
class BoxFinder(private val context: Context) {
    private val manager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var discovery: NsdManager.DiscoveryListener? = null
    private val found = mutableMapOf<String, FoundBox>()

    fun start(onFound: (List<FoundBox>) -> Unit) {
        if (discovery != null) return
        found.clear()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                manager.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            found[info.serviceName] = FoundBox(info.serviceName, host, info.port)
                            onFound(found.values.sortedBy { it.name })
                        }
                    },
                )
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                found.remove(service.serviceName)
                onFound(found.values.sortedBy { it.name })
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discovery = listener
        } catch (_: Exception) {
            // no NSD on this phone: the list stays empty, QR code / manual entry still work
        }
    }

    fun stop() {
        val listener = discovery ?: return
        discovery = null
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_liquidtv._tcp."
    }
}

/** The calls the launcher's phone page answers (see RemoteServer in the launcher). */
object Box {
    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    /** A button: [action] and [value] as the launcher's web page sends them; returns the line to show. */
    suspend fun action(link: BoxLink, action: String, value: String = ""): String = withContext(Dispatchers.IO) {
        try {
            val body = "a=${enc(action)}&v=${enc(value)}".toByteArray()
            request("${link.base}/do?t=${enc(link.token)}", body.size.toLong(), "application/x-www-form-urlencoded") { it.write(body) }
        } catch (e: Exception) {
            "Brak połączenia z Boxem (${e.message ?: e.javaClass.simpleName})"
        }
    }

    /** The same as [action], for a thread of its own. */
    fun actionBlocking(link: BoxLink, action: String, value: String = ""): String {
        val body = "a=${enc(action)}&v=${enc(value)}".toByteArray()
        return request("${link.base}/do?t=${enc(link.token)}", body.size.toLong(), "application/x-www-form-urlencoded") { it.write(body) }
    }

    /** One JPEG frame of the shared screen. Blocking: the sharing service calls it from its own thread. */
    fun frame(link: BoxLink, jpeg: ByteArray): String =
        request("${link.base}/mirror?t=${enc(link.token)}", jpeg.size.toLong(), "image/jpeg") { it.write(jpeg) }

    /** Shrinks a photo (and turns it upright) and sends it as a wallpaper. */
    suspend fun uploadPhoto(context: Context, link: BoxLink, uri: Uri): String = withContext(Dispatchers.IO) {
        val prepared = Photos.prepare(context, uri) ?: return@withContext "Nie udało się odczytać zdjęcia"
        try {
            request("${link.base}/upload?t=${enc(link.token)}&name=${enc(prepared.name)}", prepared.file.length(), "image/jpeg") { out ->
                prepared.file.inputStream().use { it.copyTo(out) }
            }
        } catch (e: Exception) {
            "Brak połączenia z Boxem (${e.message ?: e.javaClass.simpleName})"
        } finally {
            prepared.file.delete()
        }
    }

    /** Sends a short video clip as an animated wallpaper; not shrunk, so pick a short one yourself. */
    suspend fun uploadVideo(context: Context, link: BoxLink, uri: Uri): String = withContext(Dispatchers.IO) {
        val prepared = Videos.prepare(context, uri) ?: return@withContext "Nie udało się odczytać tego pliku"
        try {
            if (prepared.tooBig) return@withContext "Plik jest za duży (limit ${Videos.MAX_BYTES / 1_000_000} MB) — wybierz krótszy klip."
            request("${link.base}/upload?t=${enc(link.token)}&name=${enc(prepared.name)}", prepared.file.length(), "video/mp4") { out ->
                prepared.file.inputStream().use { it.copyTo(out) }
            }
        } catch (e: Exception) {
            "Brak połączenia z Boxem (${e.message ?: e.javaClass.simpleName})"
        } finally {
            prepared.file.delete()
        }
    }

    private fun request(url: String, length: Long, type: String, write: (OutputStream) -> Unit): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 4_000
            connection.readTimeout = 20_000
            connection.setFixedLengthStreamingMode(length)
            connection.setRequestProperty("Content-Type", type)
            connection.outputStream.use(write)
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}

class PreparedPhoto(val file: File, val name: String)

object Photos {
    private const val LONGEST_SIDE = 3000

    /**
     * Reads the picture the user chose, turns it upright (phones store it sideways plus a note),
     * shrinks it so that no side is much over 3000 px and saves it as a JPEG in the cache. That
     * keeps uploads fast and well under the launcher's size limit, and turns HEIC and the like into JPEG.
     */
    fun prepare(context: Context, uri: Uri): PreparedPhoto? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONGEST_SIDE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            if (decoded == null) {
                null
            } else {
                val orientation = resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val upright = turn(decoded, orientation)
                val file = File(context.cacheDir, "up-${System.nanoTime()}.jpg")
                file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                PreparedPhoto(file, displayName(resolver, uri).substringBeforeLast('.').ifBlank { "zdjecie" } + ".jpg")
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun turn(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
        }
        return uri.lastPathSegment ?: "zdjecie"
    }
}

class PreparedVideo(val file: File, val name: String, val tooBig: Boolean)

/** Just a straight copy to a temp file (no re-encoding: Android has no simple on-device video trimmer),
 * so the box can reject it if it is bigger than the upload limit. Pick a short clip yourself. */
object Videos {
    const val MAX_BYTES = 25_000_000L

    fun prepare(context: Context, uri: Uri): PreparedVideo? = try {
        val file = File(context.cacheDir, "up-${System.nanoTime()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        if (file.length() <= 0L) null else PreparedVideo(file, "wideo-${System.nanoTime()}.mp4", file.length() > MAX_BYTES)
    } catch (_: Throwable) {
        null
    }
}
