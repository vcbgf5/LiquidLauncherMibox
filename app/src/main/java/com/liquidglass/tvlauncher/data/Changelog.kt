package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Aktualizacje: launcher sprawdza nowe wersje co 5 sekund (można zmienić) i instaluje je sam, gdy nie dotykasz pilota.",
        "Cicha aktualizacja przez adb naprawiona: sama uruchamia launcher po instalacji i dokładnie mówi, co poszło nie tak.",
        "Nowe: Ustawienia → System → „Skonfiguruj adb”: jedno kliknięcie nadaje launcherowi uprawnienia i włącza usługi.",
        "Zasilanie przez adb: uśpienie, restart, wyłączenie, włączanie i wyłączanie telewizora przez HDMI-CEC.",
        "Zwolnij pamięć jednym przyciskiem, wyłączanie aplikacji z możliwością przywrócenia, szybsze animacje Androida.",
        "Menu szybkich ustawień: przytrzymaj zębatkę albo naciśnij Menu.",
        "Foldery aplikacji: przytrzymaj aplikację → „Dodaj do folderu”.",
        "Widżet „Teraz gra” ze sterowaniem (Spotify, YouTube Music i inne) oraz pilot z telefonu przez kod QR.",
        "Wygląd: pochylanie kafelków i warstwy jak na tvOS, ruch tapety przy przesuwaniu dockiem, kolory akcentu i odcienie szkła.",
        "Tapety same przyciemniają górę i dół, gdy zdjęcie jest jasne, żeby zegar i nazwy aplikacji były czytelne.",
        "Tryb wydajności: sam wykrywa przycinanie i wyłącza efekty, a launcher rysuje tylko to, co się zmienia.",
        "Po powrocie z aplikacji fokus wraca na kafelek, z którego ją uruchomiono.",
    )
}
