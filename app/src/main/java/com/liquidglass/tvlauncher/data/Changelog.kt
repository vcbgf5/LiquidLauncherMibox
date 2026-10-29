package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Głośność: pasek Liquid TV pokazuje prawdziwy poziom, gdy Box go widzi (pilot, głośnik Bluetooth), a przy sterowaniu telewizorem przez HDMI-CEC kierunek zmiany ▲ ▼. Systemowy pasek jest zamykany tuż po naciśnięciu. Przyciski głośności nigdy nie są blokowane.",
        "Przycisk Menu otwiera szybkie ustawienia. Jeśli Twój pilot wysyła inny kod: Ustawienia → System → „Przycisk szybkich ustawień”, naciśnij przycisk, a launcher go zapamięta (z włączoną usługą działa w każdej aplikacji).",
        "Diagnostyka głośności pokazuje ostatnie przyciski z pilota z nazwą urządzenia, wykryte zmiany głośności i dokąd idzie dźwięk; w skrypcie na komputer doszły własna komenda i „Nadaj wszystkie uprawnienia”.",
        "W Szybkich ustawieniach doszły: tryb paska głośności, diagnostyka głośności i adb.",
            )
}
