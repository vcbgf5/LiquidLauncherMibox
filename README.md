# Liquid TV

Launcher na Android TV (Xiaomi Mi Box itp.) inspirowany ekranem głównym Apple TV.
Natywna aplikacja: Kotlin + Jetpack Compose, bez WebView.

Trzy osobne ekrany, jeden nad drugim:

1. **Data** (na górze) — strzałka w górę z ikon u góry ekranu głównego: duży zegar, pełna data, kalendarz miesiąca,
   i rząd „Dalej oglądaj”. Strzałka w dół, **Wstecz** albo **Home** wraca.
2. **Ekran główny** (środek, tu się startuje) — zdjęcie na cały ekran, u góry zegar, ustawienia i konto Google,
   na dole sam dok z ulubionymi (zaokrąglony „szklany” panel z rozmytym tłem, do 6 aplikacji).
3. **Wszystkie aplikacje** (na dole) — strzałka w dół na doku przesuwa cały ekran w górę i wjeżdża pełnoekranowa siatka.
   Strzałka w górę z pierwszego rzędu, **Wstecz** albo **Home** wraca na ekran główny.

Ikona zębatki otwiera **ustawienia launchera**: przezroczystość docka i **rozmiar aplikacji na ekranie głównym**
(strzałki w lewo/prawo, zmiana widoczna od razu), tapety, wygaszacz, aktualizacje oraz przejście do **ustawień systemowych** Androida. Ikona obok pokazuje konto Google z urządzenia (pierwsza litera adresu).

**Rozmiar aplikacji** (70–140%) ma ograniczenie: dok nigdy nie wychodzi poza ekran. Przy sześciu ulubionych na ekranie
1080p domyślny rozmiar już wypełnia szerokość, więc większe wartości działają dopiero przy mniejszej liczbie ulubionych
(na przykład pięciu), a przy sześciu można je zmniejszać.

**Wygaszacz.** Po kilku minutach bez naciskania pilota (ustawienie „Wygaszacz po”: wyłączony, 1, 3, 5, 10, 15 lub 30 min,
domyślnie 3) na cały ekran wchodzi aktualna tapeta z bardzo powolnym zoomem, wielki zegar i data w „płynnym szkle”
(przezroczyste litery z poświatą i cienką krawędzią). Zegar powoli wędruje po ekranie, żeby nic nie wypalało się w jednym
miejscu. Dowolny przycisk budzi launcher (i nic poza tym nie robi). „Pokaż wygaszacz teraz” w ustawieniach pokazuje go od razu.
To samo jest zarejestrowane jako **systemowy wygaszacz Androida**: Ustawienia → Preferencje urządzenia → Wygaszacz ekranu →
**Liquid TV** (wtedy system sam go włącza po swoim czasie, także gdy launcher nie jest na wierzchu).


Animacje: płynne przejście między ekranami (zdjęcie „zostaje w tyle", rzędy aplikacji wjeżdżają kaskadą),
kafelki wchodzą jeden po drugim, zaznaczony kafelek unosi się, świeci i przebiega po nim połysk, wciśnięcie OK
lekko go „dociska", a otwierana aplikacja wyrasta z własnego kafelka na cały ekran (po powrocie zwija się z powrotem).
Zdjęcie bardzo powoli „oddycha".

**„Dalej oglądaj” z Netflixa i Disney+.** Android pozwala aplikacji czytać tylko *własne* wpisy z listy „Dalej oglądaj”;
wpisy innych aplikacji (i to tylko z aktywnego profilu, nigdy ze wszystkich naraz) czyta wyłącznie system. Dlatego:

- w rzędzie „Dalej oglądaj” jest kafelek **ekranu systemowego** (jeśli fabryczny launcher nie jest wyłączony) — tam ten rząd jest;
- jeśli Twoje urządzenie mimo wszystko pozwoli na odczyt, launcher pokaże prawdziwe tytuły i kliknięcie włączy dany film.
  Żeby to sprawdzić, na komputerze (po `adb connect`) wydaj raz:

```bash
adb shell pm grant com.liquidglass.tvlauncher com.android.providers.tv.permission.READ_EPG_DATA
```

  i otwórz stronę z datą (strzałka w górę z ikon na górze). Jeśli komenda zgłosi błąd albo rząd nadal pokazuje tylko kafelek systemowy, system tego nie dopuszcza.

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
![Data i dalej oglądaj (strona nad ekranem głównym)](docs/preview-info.jpg)
![Wygaszacz](docs/preview-screensaver.jpg)

**Klikalny podgląd w przeglądarce: https://vcbgf5.github.io/LiquidLauncherMibox/preview.html**

Możesz w nim klikać kafelki (pierwsze kliknięcie zaznacza, drugie „otwiera”), używać wirtualnego pilota pod ekranem
albo strzałek, Enter i Esc na klawiaturze. Prawy przycisk myszy lub „Przytrzymaj OK (menu)” pokazuje menu aplikacji.
Działa też na telefonie.

To makieta z przykładowymi ikonami — prawdziwa aplikacja pokazuje ikony zainstalowanych aplikacji.
Plik źródłowy: [`preview/preview.html`](preview/preview.html).

## Aktualizacje w aplikacji

Launcher **sam sprawdza**, czy w najnowszym wydaniu jest nowsza wersja (jedno malutkie zapytanie, gdy launcher pojawia się
na ekranie, najwyżej raz na 6 godzin) i — gdy jest — **sam ją pobiera i otwiera systemowy instalator**. Zostaje Ci tylko
zatwierdzić instalację (Android nigdy nie pozwala instalować po cichu). Tę samą wersję launcher proponuje samoczynnie tylko raz,
więc jeśli odmówisz, nie będzie Cię nękał; wtedy przy zębatce świeci czerwona kropka.

W ustawieniach launchera (zębatka): **Sprawdź aktualizacje** (sprawdza od razu i jeśli jest nowa wersja, od razu ją pobiera),
**Aktualizuj teraz** (gdy nowa wersja czeka) i przełącznik **Aktualizuj automatycznie** (domyślnie włączony).
Przy pierwszej aktualizacji system zapyta o zgodę na „instalowanie nieznanych aplikacji” dla Liquid TV — włącz ją
i wybierz Aktualizuj teraz jeszcze raz (samoczynna aktualizacja sama tego okna nie otwiera).

**Żeby aktualizacje „na wierzchu” w ogóle działały, wersje muszą być podpisane tym samym kluczem.** Jednorazowo:

1. Repo → **Settings → Secrets and variables → Actions → New repository secret**: nazwa `KEYSTORE_PASSPHRASE`,
   wartość: długie losowe hasło (min. 30 znaków; to ono chroni klucz, więc nie wymyślaj krótkiego).
2. Zbuduj wydanie (np. nowy release `v1.0.2`). Workflow sam utworzy klucz i zapisze go w repo **zaszyfrowanego**
   hasłem z sekretu (`signing/release.p12.enc`) — bez hasła jest bezużyteczny.
3. **Ostatni raz odinstaluj starą wersję** (była podpisana innym kluczem) i zainstaluj nową ręcznie.
   Od tej chwili każda kolejna wersja zainstaluje się na poprzedniej przez „Aktualizuj teraz”.

Nie usuwaj ani nie zmieniaj sekretu `KEYSTORE_PASSPHRASE` — bez niego nie da się podpisać następnych wersji
tym samym kluczem i trzeba zaczynać od nowa (z odinstalowaniem).

## Połączenie z Boxem przez ADB (skrypt na komputer)

[`tools/tv_adb.py`](tools/tv_adb.py) łączy się z Boxem przez sieć (domyślnie port 5555) i ma menu najczęstszych czynności:
instalacja i aktualizacja Liquid TV z GitHuba, włączenie usługi dostępności (pasek głośności, okno zasilania),
ustawienie domyślnego ekranu głównego, wpisanie linku w zaznaczone pole, zrzut ekranu, uśpienie i obudzenie Boxa.
Potrzebuje tylko Pythona 3; jeśli nie ma `adb`, zaproponuje pobranie go od Google.

```bash
python tools/tv_adb.py 192.168.1.50
```

Najpierw na Boxie włącz Opcje programisty (7 razy „Kompilacja” w Informacjach) i debugowanie, a przy pierwszym połączeniu
potwierdź pytanie na ekranie telewizora.

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
