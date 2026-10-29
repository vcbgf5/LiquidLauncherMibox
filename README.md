# Liquid TV

Launcher na Android TV (Xiaomi Mi Box itp.) inspirowany ekranem głównym Apple TV.
Natywna aplikacja: Kotlin + Jetpack Compose, bez WebView.

- **Hero** — zdjęcie na górze ekranu, ściemnione tylko przy dolnej krawędzi.
- **Dok z ulubionymi** — zaokrąglony, „szklany” panel z rozmytym tłem na granicy hero i siatki, do 5 aplikacji.
- **Siatka** — pozostałe aplikacje, przewijana pilotem.
- **Kafelki** jak w tvOS: szerokie prostokąty 5:3 (banner aplikacji TV, a dla zwykłych aplikacji ikona na kolorowym tle), po najechaniu unoszą się, rosną i świecą.
- **Przytrzymaj OK** na aplikacji → menu: dodaj/usuń z ulubionych, przesuń w lewo/prawo, informacje o aplikacji, odinstaluj.
- **Home / Wstecz** wraca na górę i zaznacza pierwszą ulubioną.

## Pobranie APK

- Strona z przyciskiem: **https://vcbgf5.github.io/LiquidLauncherMibox/**
- Krótki adres do wpisania pilotem (od razu pobiera najnowszą wersję): **vcbgf5.github.io/LiquidLauncherMibox/apk**
- Bezpośredni link: `https://github.com/vcbgf5/LiquidLauncherMibox/releases/latest/download/LiquidTV.apk`

Nowe wydanie: wypchnij tag, np. `git tag v1.1.0 && git push origin v1.1.0`. Workflow zbuduje APK i doda go do Releases,
a linki powyżej zawsze wskazują na najnowsze.

## Instalacja na Mi Box

1. Wgraj `app-release.apk` (Downloader, pendrive albo `adb install`).
2. Ustaw jako ekran główny (przycisk Home): Button Mapper albo wyłączenie
   fabrycznego launchera przez ADB:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Cofnięcie: `adb shell pm enable com.google.android.tvlauncher`.

## Zdjęcie w hero

`app/src/main/res/drawable/bg_hero.jpg` to na razie wygenerowany krajobraz górski. Żeby wstawić własne zdjęcie:

1. Zapisz je jako `bg_hero.jpg` w proporcjach ok. 3:1 (np. 1920×640) — podmień plik.
2. Przygotuj do niego rozmyte tło docka `bg_hero_blur.jpg` (480×150): wytnij środek zdjęcia w proporcji 3,2:1,
   zmniejsz do 480×150, rozmyj (Gaussian ~7 px) i nałóż to samo ściemnienie dołu, co w `HeroBanner.kt`.
   Skrypt do tego: `tools/make_hero_assets.py`.

## Budowanie lokalnie

Android Studio (Hedgehog+) → Open → Run, albo `./gradlew assembleDebug`.
