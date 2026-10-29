# Liquid TV

Launcher na Android TV (Xiaomi Mi Box itp.) inspirowany ekranem głównym Apple TV.
Natywna aplikacja: Kotlin + Jetpack Compose, bez WebView.

Dwa osobne ekrany:

1. **Ekran główny** — zdjęcie na cały ekran, u góry zegar, ustawienia i konto Google, na dole sam dok z ulubionymi
   (zaokrąglony „szklany” panel z rozmytym tłem, do 6 aplikacji).
2. **Wszystkie aplikacje** — strzałka w dół na doku przesuwa cały ekran w górę i wjeżdża pełnoekranowa siatka.
   Strzałka w górę z pierwszego rzędu, **Wstecz** albo **Home** wraca na ekran główny.

Ikona zębatki otwiera **ustawienia launchera**: przezroczystość docka (strzałki w lewo/prawo, zmiana widoczna od razu)
oraz przejście do **ustawień systemowych** Androida. Ikona obok pokazuje konto Google z urządzenia (pierwsza litera adresu).

Animacje: płynne przejście między ekranami (zdjęcie „zostaje w tyle", rzędy aplikacji wjeżdżają kaskadą),
kafelki wchodzą jeden po drugim, zaznaczony kafelek unosi się, świeci i przebiega po nim połysk, wciśnięcie OK
lekko go „dociska", a otwierana aplikacja wyrasta z własnego kafelka na cały ekran (po powrocie zwija się z powrotem).
Zdjęcie bardzo powoli „oddycha".

**Tapety z Unsplash.** Launcher zmienia tapetę co kilka minut (z płynnym przenikaniem), losując z wybranej kategorii:
zwierzęta, góry, miasto, las, ocean, kosmos, zima, niebo, abstrakcja, minimalizm (lub ze wszystkich).
Kategorię i częstotliwość wybierasz w ustawieniach launchera (zębatka), tam też jest „Następna tapeta” i imię autora zdjęcia.
Zdjęcia leżą w folderze [`wallpapers/`](wallpapers/) tego repozytorium i są stamtąd pobierane — jak je tam wgrać
(klucz Unsplash, jedno kliknięcie w Actions), opisuje [`wallpapers/README.md`](wallpapers/README.md).
Dopóki zdjęć nie ma, działa wbudowana tapeta.

Domyślne ulubione i zawsze pierwsze w siatce: YouTube, Netflix, YouTube Music, Sklep Play, Spotify, Disney+.

- **Kafelki** jak w tvOS: szerokie prostokąty 5:3 (banner aplikacji TV, a dla zwykłych aplikacji ikona na kolorowym tle), po najechaniu unoszą się, rosną i świecą.
- **Przytrzymaj OK** na aplikacji → menu: dodaj/usuń z ulubionych, przesuń w lewo/prawo, informacje o aplikacji, odinstaluj.

## Podgląd

![Ekran główny](docs/preview-home.jpg)
![Wszystkie aplikacje](docs/preview-apps.jpg)

**Klikalny podgląd w przeglądarce: https://vcbgf5.github.io/LiquidLauncherMibox/preview.html**

Możesz w nim klikać kafelki (pierwsze kliknięcie zaznacza, drugie „otwiera”), używać wirtualnego pilota pod ekranem
albo strzałek, Enter i Esc na klawiaturze. Prawy przycisk myszy lub „Przytrzymaj OK (menu)” pokazuje menu aplikacji.
Działa też na telefonie.

To makieta z przykładowymi ikonami — prawdziwa aplikacja pokazuje ikony zainstalowanych aplikacji.
Plik źródłowy: [`preview/preview.html`](preview/preview.html).

## Pobranie APK

- Strona z przyciskiem: **https://vcbgf5.github.io/LiquidLauncherMibox/**
- Krótki adres do wpisania pilotem (od razu pobiera najnowszą wersję): **vcbgf5.github.io/LiquidLauncherMibox/apk**
- Bezpośredni link: `https://github.com/vcbgf5/LiquidLauncherMibox/releases/latest/download/LiquidTV.apk`

Nowe wydanie: utwórz release z tagiem (np. `v1.1.0`) na GitHubie albo wypchnij tag. Workflow zbuduje APK i doda go do Releases,
a linki powyżej zawsze wskazują na najnowsze.

## Instalacja na Mi Box

1. Wgraj `app-release.apk` (Downloader, pendrive albo `adb install`).
2. Ustaw jako ekran główny (przycisk Home): Button Mapper albo wyłączenie
   fabrycznego launchera przez ADB:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Cofnięcie: `adb shell pm enable com.google.android.tvlauncher`.

## Zdjęcie na ekranie głównym

`app/src/main/res/drawable/bg_hero.jpg` to na razie wygenerowany krajobraz górski. Żeby wstawić własne zdjęcie:

```bash
python3 tools/make_hero_assets.py moje_zdjecie.jpg
```

Skrypt przytnie je do 16:9 (1920×1080) i przygotuje rozmytą kopię `bg_hero_blur.jpg`, która jest tłem docka
i ekranu z aplikacjami. Wybierz zdjęcie z ciemniejszym dołem — tam leży dok.

## Budowanie lokalnie

Android Studio (Hedgehog+) → Open → Run, albo `./gradlew assembleDebug`.
