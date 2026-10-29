package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Głośność: pasek Liquid TV reaguje na każdą zmianę głośności (pilot, głośnik Bluetooth, HDMI), a systemowy pasek jest zamykany. Tryb zmienisz w Szybkich ustawieniach albo w Ustawienia → System.",
        "Przycisk Menu otwiera szybkie ustawienia. Jeśli Twój pilot wysyła inny kod: Ustawienia → System → „Przycisk szybkich ustawień”, naciśnij przycisk, a launcher go zapamięta (z włączoną usługą działa w każdej aplikacji).",
        "Diagnostyka głośności pokazuje teraz ostatnie przyciski z pilota z nazwą urządzenia, wykryte zmiany głośności oraz okna systemowe (przez adb).",
        "W Szybkich ustawieniach doszły: tryb paska głośności, diagnostyka głośności i adb.",
        )
}
