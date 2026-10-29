package com.liquidglass.tvlauncher.data

/**
 * What changed in the newest version, shown once after an update ("Co nowego") and from
 * Settings → Aktualizacje. Newest first; kept short, one line per change.
 */
object Changelog {
    val NEWEST: List<String> = listOf(
        "Głośność: gdy Box pokazuje, że jego poziom jest prawdziwy, launcher przejmuje przyciski głośności i nie pokazuje systemowego paska (zostaje tylko nasz). Gdyby to nie działało, sam oddaje przyciski systemowi.",
        "Własne tapety: w Pilocie z telefonu doszło wysyłanie zdjęć. Pojawiają się w kategorii „Moje zdjęcia” i od razu są tapetą.",
    )
}
