package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Głośność bez systemowego paska: launcher odbiera przyciski + i −, a głośność ustawia przez adb (komenda „media volume” bez --show), więc widać tylko nasz pasek. Wymaga skonfigurowanego adb (Ustawienia → Narzędzia → Skonfiguruj adb). Gdy adb nie działa, użyje własnego wywołania, a gdyby i to zawiodło, odda przyciski systemowi.",
    )
}
