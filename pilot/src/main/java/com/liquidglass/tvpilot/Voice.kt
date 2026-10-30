package com.liquidglass.tvpilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

/** One voice command: the Polish words that trigger it, and the same action/value a remote button already sends. */
private class VoiceCommand(val phrases: List<String>, val action: String, val value: String, val label: String)

/** Mirrors the buttons in [RemoteTab] (and the volume buttons): a voice command just presses one of them. */
private val voiceCommands = listOf(
    VoiceCommand(listOf("odtwórz", "odtwarzaj", "graj", "pauza", "wstrzymaj", "zatrzymaj"), "key", "PLAYPAUSE", "odtwarzanie / pauza"),
    VoiceCommand(listOf("następny", "następna", "kolejny", "kolejna", "pomiń"), "key", "NEXT", "następny utwór"),
    VoiceCommand(listOf("głośniej", "podgłośnij"), "vol_up", "", "głośniej"),
    VoiceCommand(listOf("ciszej", "przycisz"), "vol_down", "", "ciszej"),
    VoiceCommand(listOf("wycisz", "cisza"), "mute", "", "wycisz"),
    VoiceCommand(listOf("do góry", "w górę", "góra"), "key", "UP", "do góry"),
    VoiceCommand(listOf("w dół", "na dół", "dół"), "key", "DOWN", "w dół"),
    VoiceCommand(listOf("w lewo", "lewo"), "key", "LEFT", "w lewo"),
    VoiceCommand(listOf("w prawo", "prawo"), "key", "RIGHT", "w prawo"),
    VoiceCommand(listOf("ok", "zatwierdź", "wybierz"), "key", "OK", "OK"),
    VoiceCommand(listOf("wstecz", "cofnij"), "key", "BACK", "wstecz"),
    VoiceCommand(listOf("menu"), "key", "MENU", "menu"),
)

/** Finds the first command one of whose phrases matches [heard]: a whole word for a one-word phrase,
 * a substring for a few words (SpeechRecognizer hands back a short sentence, not a single token). */
private fun matchVoiceCommand(heard: String): VoiceCommand? {
    val text = heard.lowercase().trim()
    val words = text.split(Regex("\\s+")).toSet()
    return voiceCommands.firstOrNull { command ->
        command.phrases.any { phrase -> if (phrase.contains(' ')) text.contains(phrase) else phrase in words }
    }
}

private sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    class Done(val heard: String, val command: VoiceCommand?, val error: String? = null) : VoiceState()
}

/**
 * A mic button: tap it to say a short command in Polish ("głośniej", "następny", "do góry"...); the recognized
 * phrase is matched against [voiceCommands] and sent through the same [send] every other button already uses.
 */
@Composable
fun VoiceCard(send: (String, String) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<VoiceState>(VoiceState.Idle) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    val listen: () -> Unit = listen@{
        val engine = recognizer
        if (engine == null) {
            state = VoiceState.Done("", null, "Rozpoznawanie mowy niedostępne na tym telefonie")
            return@listen
        }
        state = VoiceState.Listening
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val command = matchVoiceCommand(heard)
                if (command != null) send(command.action, command.value)
                state = VoiceState.Done(heard, command)
            }
            override fun onError(error: Int) {
                state = VoiceState.Done("", null, "Nie udało się rozpoznać mowy, spróbuj jeszcze raz")
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        engine.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else state = VoiceState.Done("", null, "Potrzebny dostęp do mikrofonu")
    }

    GlassCard {
        Heading(PilotIcons.Mic, "Sterowanie głosem")
        BodyText("Powiedz np. „głośniej”, „następny”, „pauza” albo „do góry” — pilot wyśle ten sam przycisk.")
        PilotButton(
            if (state is VoiceState.Listening) "Słucham…" else "Powiedz komendę",
            {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    listen()
                } else {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            Modifier.fillMaxWidth(),
            icon = PilotIcons.Mic,
            primary = true,
            enabled = state !is VoiceState.Listening,
        )
        val done = state as? VoiceState.Done
        if (done != null) {
            val text = when {
                done.error != null -> done.error
                done.heard.isBlank() -> "Nie usłyszałem nic, spróbuj ponownie."
                done.command != null -> "Usłyszałem: „${done.heard}” → ${done.command.label}"
                else -> "Usłyszałem: „${done.heard}” — nie rozpoznano komendy"
            }
            Text(text, color = Pal.muted, fontSize = 13.sp)
        }
    }
}
