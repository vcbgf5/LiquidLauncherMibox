package com.liquidglass.tvpilot

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Last.fm's "mobile session" auth: a username and password, signed with the app's API key and secret
 * (auth.getMobileSession) — no redirect, unlike Spotify. Only the resulting session key is kept (it never
 * expires); the password itself is never sent anywhere but Last.fm, and never reaches the launcher.
 */
private object LastFmAuth {
    private const val API = "https://ws.audioscrobbler.com/2.0/"

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    /** Last.fm's "method signature": the params sorted by name, glued together, the secret appended, MD5'd. */
    private fun sign(params: Map<String, String>, secret: String): String {
        val base = params.toSortedMap().entries.joinToString("") { (k, v) -> k + v } + secret
        return MessageDigest.getInstance("MD5").digest(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Blocking: trades a username and password for a session key and the (possibly corrected) username. */
    fun mobileSession(apiKey: String, secret: String, username: String, password: String): Pair<String, String> {
        val params = mapOf("method" to "auth.getMobileSession", "username" to username, "password" to password, "api_key" to apiKey)
        val signed = params + ("api_sig" to sign(params, secret)) + ("format" to "json")
        val body = signed.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }.toByteArray()
        val connection = URL(API).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val text = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(text)
            if (code !in 200..299 || json.has("error")) throw Exception(json.optString("message").ifBlank { "HTTP $code" })
            val session = json.getJSONObject("session")
            return session.getString("key") to session.getString("name")
        } finally {
            connection.disconnect()
        }
    }
}

/** Sign-in to Last.fm: username and password, signed right here on the phone, sent to the launcher as a session key only. */
@Composable
fun LastFmCard(sendToBox: (String, String) -> Unit, onStatus: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }

    GlassCard {
        Text("Last.fm", color = Color(0xFFD51007), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        BodyText("Scrobbluj to, co gra przez Spotify na Boksie, na Twoje konto Last.fm.")
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            label = { Text("Nazwa użytkownika Last.fm") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        PilotButton(
            if (connecting) "Łączę…" else "Połącz z Last.fm",
            icon = PilotIcons.Check,
            primary = true,
            enabled = !connecting && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val apiKey = BuildConfig.LASTFM_API_KEY
                val secret = BuildConfig.LASTFM_API_SECRET
                if (apiKey.isBlank() || secret.isBlank()) {
                    onStatus("Ta kompilacja nie ma klucza API Last.fm")
                    return@PilotButton
                }
                connecting = true
                scope.launch {
                    try {
                        val (key, name) = withContext(Dispatchers.IO) { LastFmAuth.mobileSession(apiKey, secret, username, password) }
                        password = ""
                        sendToBox("lastfm_login", JSONObject().put("k", key).put("u", name).toString())
                        onStatus("Połączono z Last.fm jako $name")
                    } catch (e: Exception) {
                        onStatus("Last.fm odrzucił logowanie: ${e.message ?: e.javaClass.simpleName}")
                    } finally {
                        connecting = false
                    }
                }
            },
        )
    }
}
