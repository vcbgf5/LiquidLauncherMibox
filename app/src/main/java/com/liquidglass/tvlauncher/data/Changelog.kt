package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Last.fm: scrobbluje to, co gra przez Spotify. Połącz się w aplikacji Pilot (zakładka Spotify); włącznik i rozłączenie w Ustawieniach → Widżety.",
        "Kursor na TV żyroskopem telefonu: w aplikacji Pilot (zakładka Pilot → Otwórz touchpad) przechyl telefon, żeby ruszać kropką po ekranie, dotknij ekranu, żeby kliknąć. Wymaga skonfigurowanego adb.",
        "Piloty z gładzikiem: przesunięcie działa jak strzałki (zmiana ekranu, przesuwanie fokusu), stuknięcie jak OK.",
        "Tekst piosenki w pełnoekranowym odtwarzaczu Spotify: przycisk „Tekst” obok playlist, podświetla aktualną linijkę (z lrclib.net).",
        "Sterowanie głosem w aplikacji Pilot: powiedz „głośniej”, „następny”, „pauza” albo „do góry” zamiast szukać przycisku.",
        "Aplikacja Pilot umie teraz znaleźć Boksy w sieci Wi-Fi same (zakładka Box → „Szukaj w sieci”) i przełączać się między nimi bez skanowania kodu QR za każdym razem.",
        "Animacja wyłączania: ekran zamyka się jak w starych telewizorach (pasek na środku, potem kropka) przed wysłaniem komendy wyłączenia.",
        "Animowana tapeta: wyślij krótki klip z aplikacji Pilot (zakładka Zdjęcia) — zapętla się bez dźwięku w tle ekranu głównego. Niedostępne w wersji Lite i w trybie oszczędnym.",
        "Poprawka: automatyczna aktualizacja już nie \"cichnie\" na zawsze, gdy cicha instalacja przez adb akurat zawiedzie — pokaże zwykłe okno instalacji zamiast nic nie robić.",
        "Gotowe motywy kolorystyczne, sezonowe tło (śnieg/liście/kwiaty) i widżet Spotify w kolorze okładki (Ustawienia → Wygląd).",
        "Poprawka: widżet Spotify (OK = pauza/wznów, przytrzymanie OK = pełny odtwarzacz) nie blokuje już reszty strony widżetów.",
        "Spotify na telewizorze: widżet „Spotify” i pełnoekranowy odtwarzacz z okładkami, playlistami i utworami. Zaloguj się raz w aplikacji Pilot (zakładka Spotify); potrzebny Spotify Premium.",
        "Nowe logo launchera i baner na ekran telewizora; aplikacja Pilot na telefon ma nowe ikony i szklany wygląd.",
        "Wersja Lite (LiquidTVLite.apk, dla telewizorów z ok. 0,5 GB RAM) jest znacznie mniejsza i oszczędniejsza.",
    )
}
