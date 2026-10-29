package com.liquidglass.tvlauncher.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * A tiny web server inside the launcher, so a phone on the same Wi-Fi can control the box: a page
 * with a D-pad, volume, power, and boxes to paste a calendar link, a city or a web address into
 * (typing those with a TV remote is miserable). The address, with a secret in it, is shown as a
 * QR code on the TV; a request without the secret gets nothing.
 *
 * [handle] is called with the action name and value of each button press and returns the text the
 * page shows back. It is called on a server thread.
 */
class RemoteServer(
    private val token: () -> String,
    private val handle: (action: String, value: String) -> String,
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(3)

    /** The port it listens on, once [start] succeeded. */
    @Volatile
    var port: Int = 0
        private set

    val running: Boolean get() = server != null

    @Synchronized
    fun start(): Boolean {
        if (server != null) return true
        for (candidate in FIRST_PORT..FIRST_PORT + 5) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate))
                server = socket
                port = candidate
                Thread({ acceptLoop(socket) }, "liquidtv-remote").apply { isDaemon = true }.start()
                return true
            } catch (_: Exception) {
                // that port is taken: try the next one
            }
        }
        return false
    }

    @Synchronized
    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        port = 0
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                return
            }
            try {
                pool.execute { serve(client) }
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use {
            try {
                client.soTimeout = 5_000
                val input = client.getInputStream()
                val head = readHead(input) ?: return
                val lines = head.split("\r\n")
                val request = lines.firstOrNull()?.split(' ') ?: return
                if (request.size < 2) return
                val method = request[0]
                val target = request[1]
                val path = target.substringBefore('?')
                val query = parseForm(target.substringAfter('?', ""))
                val length = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                val body = if (length > 0) String(readBytes(input, length), Charsets.UTF_8) else ""

                val output = client.getOutputStream()
                if (!sameToken(query["t"].orEmpty())) {
                    respond(output, 403, "text/plain; charset=utf-8", "Zły albo stary link. Zeskanuj kod QR z ekranu telewizora jeszcze raz.")
                    return
                }
                when {
                    method == "GET" && path == "/" -> respond(output, 200, "text/html; charset=utf-8", PAGE.replace("__TOKEN__", query["t"].orEmpty()))
                    method == "POST" && path == "/do" -> {
                        val form = parseForm(body)
                        val reply = try {
                            handle(form["a"].orEmpty(), form["v"].orEmpty())
                        } catch (e: Exception) {
                            "Błąd: ${e.message}"
                        }
                        respond(output, 200, "text/plain; charset=utf-8", reply)
                    }
                    else -> respond(output, 404, "text/plain; charset=utf-8", "Nie ma takiej strony")
                }
            } catch (_: Exception) {
                // a phone that dropped the connection: nothing to do
            }
        }
    }

    private fun sameToken(given: String): Boolean =
        MessageDigest.isEqual(given.toByteArray(), token().toByteArray())

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = if (b == "\r\n\r\n"[matched].code) matched + 1 else if (b == '\r'.code) 1 else 0
            if (matched == 4) return out.toString("UTF-8").trimEnd()
        }
        return null
    }

    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    private fun parseForm(text: String): Map<String, String> =
        text.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    private fun respond(output: java.io.OutputStream, status: Int, type: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 403) "Forbidden" else "Not Found"
        output.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        output.write(bytes)
        output.flush()
    }

    companion object {
        const val FIRST_PORT = 8765
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 8 * 1024

        /** The control page; "__TOKEN__" is replaced with the secret of the request. */
        private val PAGE = """<!doctype html>
<html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Liquid TV</title>
<style>
:root{color-scheme:dark}
body{margin:0;font-family:-apple-system,system-ui,Segoe UI,Roboto,sans-serif;background:#0b0c10;color:#f2f2f7;padding:16px 16px 40px}
h1{font-size:22px;margin:4px 0 14px}
h2{font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:#8e8e93;margin:22px 0 8px}
.card{background:#1c1e26;border-radius:16px;padding:12px}
button{font:inherit;color:#fff;background:#2c2e3a;border:0;border-radius:12px;padding:14px 10px;min-width:0}
button:active{background:#0a84ff}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.grid2{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}
.wide{grid-column:span 3}
input{width:100%;box-sizing:border-box;font:inherit;color:#fff;background:#0b0c10;border:1px solid #3a3c4a;border-radius:12px;padding:12px;margin-bottom:8px}
#msg{position:fixed;left:12px;right:12px;bottom:12px;background:#0a84ff;color:#fff;border-radius:12px;padding:12px;text-align:center;display:none}
</style></head><body>
<h1>Liquid TV · pilot</h1>
<h2>Nawigacja</h2>
<div class="card"><div class="grid">
<span></span><button onclick="k('UP')">▲</button><span></span>
<button onclick="k('LEFT')">◀</button><button onclick="k('OK')">OK</button><button onclick="k('RIGHT')">▶</button>
<button onclick="k('BACK')">Wstecz</button><button onclick="k('DOWN')">▼</button><button onclick="k('HOME')">Home</button>
<button onclick="k('PLAYPAUSE')" class="wide">⏯ Odtwórz / pauza</button>
</div></div>
<h2>Głośność</h2>
<div class="card"><div class="grid">
<button onclick="s('vol_down')">−</button><button onclick="s('mute')">🔇</button><button onclick="s('vol_up')">+</button>
</div></div>
<h2>Zasilanie</h2>
<div class="card"><div class="grid2">
<button onclick="s('standby')">Uśpij Box</button><button onclick="s('tv_on')">Włącz TV</button>
<button onclick="s('tv_power')">TV wł./wył.</button><button onclick="if(confirm('Zrestartować Box?'))s('reboot')">Restart</button>
</div></div>
<h2>Wyślij na TV</h2>
<div class="card">
<input id="url" type="url" placeholder="Adres strony lub filmu (https://…)">
<button onclick="s('open',url.value)" style="width:100%">Otwórz na TV</button>
</div>
<h2>Kalendarz Google</h2>
<div class="card">
<input id="ical" type="url" placeholder="Tajny adres w formacie iCal">
<button onclick="s('ical',ical.value)" style="width:100%">Połącz kalendarz</button>
</div>
<h2>Pogoda</h2>
<div class="card">
<input id="city" placeholder="Miasto, np. Gdańsk">
<button onclick="s('city',city.value)" style="width:100%">Ustaw miasto</button>
</div>
<h2>Launcher</h2>
<div class="card"><div class="grid2">
<button onclick="s('wallpaper')">Następna tapeta</button><button onclick="s('screensaver')">Wygaszacz teraz</button>
<button onclick="s('memory')">Zwolnij pamięć</button><button onclick="s('perf')">Tryb wydajności</button>
</div></div>
<div id="msg"></div>
<script>
var T='__TOKEN__';
function toast(t){var m=document.getElementById('msg');m.textContent=t;m.style.display='block';clearTimeout(window._t);window._t=setTimeout(function(){m.style.display='none'},2600)}
function s(a,v){fetch('/do?t='+encodeURIComponent(T),{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'a='+encodeURIComponent(a)+'&v='+encodeURIComponent(v||'')}).then(function(r){return r.text()}).then(toast).catch(function(){toast('Brak połączenia z Boxem')})}
function k(n){s('key',n)}
</script></body></html>"""
    }
}
