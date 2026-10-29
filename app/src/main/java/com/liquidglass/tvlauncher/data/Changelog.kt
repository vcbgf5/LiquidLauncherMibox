package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Nowa aplikacja na telefon „Liquid TV Pilot” (plik LiquidTVPilot.apk w wydaniu): pilot, wpisywanie tekstu, wysyłanie zdjęć jako tapet, udostępnianie ekranu telefonu na TV (Wstecz na TV kończy) i „Udostępnij” z galerii lub przeglądarki.",
        "Głośność bez systemowego paska: launcher odbiera przyciski + i − i ustawia głośność przez adb (media volume bez --show). Wymaga skonfigurowanego adb (Ustawienia → Narzędzia → Skonfiguruj adb).",
    )
}
