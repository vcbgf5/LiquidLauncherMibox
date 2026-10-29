# Liquid TV

Launcher na Android TV (Xiaomi Mi Box itp.) inspirowany ekranem głównym Apple TV.
Natywna aplikacja: Kotlin + Jetpack Compose, bez WebView.

Trzy osobne ekrany, jeden nad drugim:

1. **Widżety** (na górze) — strzałka w górę z ikon u góry ekranu głównego: duży zegar, pełna data i rząd widżetów:
   kalendarz miesiąca, najbliższe wydarzenia z Kalendarza Google, sieć (Wi-Fi, sygnał, adres IP), Bluetooth ze sparowanymi
   urządzeniami oraz pamięć i system. Które widżety są widoczne, wybierasz w Ustawieniach → Widżety. Strzałka w dół,
   **Wstecz** albo **Home** wraca.
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

**Kalendarz Google w widżecie.** Android TV zwykle nie ma własnego kalendarza, więc launcher czyta „Tajny adres w formacie
iCal” Twojego kalendarza Google (Kalendarz Google na komputerze → ustawienia kalendarza). Link wysyłasz na Boxa skryptem
`tools/tv_adb.py` (pozycja „Podłącz kalendarz Google”), a launcher odświeża go co 20 minut i pokazuje najbliższe dwa tygodnie,
także wydarzenia cykliczne. Rozumie typowe powtarzanie (codziennie, co tydzień z dniami, co miesiąc, co rok), ale nie wszystkie
egzotyczne reguły. Bez linku próbuje kalendarza systemowego, jeśli Box go ma i dałeś zgodę.

**Pogoda.** Widżet „Pogoda” pokazuje temperaturę, odczuwalną, wiatr, wilgotność i prognozę na kolejne dni (dane z Open-Meteo,
darmowe, bez klucza). Miasto wybierasz w Ustawieniach → Widżety (lista polskich miast), a dowolne inne ustawisz skryptem
`tools/tv_adb.py` („Ustaw miasto pogody”). Pogodę można też pokazać pod zegarem na ekranie głównym i na wygaszaczu.

**Zmiana ikony jednej aplikacji.** Przytrzymaj OK na aplikacji → „Zmień ikonę…”. W launcherze są trzy paczki ikon (Apple TV,
Liquid Glass, Minimal) dla 14 popularnych aplikacji; pojawiają się tam też paczki Nova/ADW zainstalowane na Boxie. Ikony paczek
wbudowanych rysuje `tools/make_tvos_tiles.py` z otwartych kształtów logo (Simple Icons, CC0); nazwy i logo należą do ich właścicieli.

**Głośność.** Pasek głośności Liquid TV (usługa dostępności) działa nad każdą aplikacją. Przyciski głośności zawsze trafiają dalej do
Androida (głośność nie może przestać działać przez launcher); pasek tylko to pokazuje. Po naciśnięciu sprawdza, co system zrobił
z poziomem: jeśli głośność zmieniła się o część skali (pilot, głośnik Bluetooth, wyjście z własną regulacją), pokazuje prawdziwy
poziom, a jeśli Box poziomu nie widzi (telewizor steruje głośnością przez HDMI-CEC), pokazuje kierunek ▲ ▼. Tryby: wyłączony;
„systemowy zostaje”; „systemowy ukryty” (domyślny): pasek Androida jest zamykany kilkukrotnie zaraz po naciśnięciu, więc widać
głównie nasz. Tryb przełączysz w Szybkich ustawieniach. Gdy głośność nie działa: Ustawienia → System → „Diagnostyka głośności”
pokazuje, jakie przyciski dochodzą z pilota, czy zmiany głośności są wykrywane i dokąd idzie dźwięk; „Okna systemowe i audio (adb)”
pokazuje, które okno Androida jest paskiem głośności. Skrypt `tools/tv_adb.py` ma tę samą diagnostykę z komputera.

**Przycisk Menu.** Przycisk Menu (oraz Settings i Contents Menu) otwiera szybkie ustawienia. Jeśli pilot wysyła inny kod, albo system
go nie przepuszcza: Ustawienia → System → „Przycisk szybkich ustawień”, naciśnij wybrany przycisk, a launcher go zapamięta.
Z włączoną usługą dostępności działa on w każdej aplikacji.

**Uprawnienia.** Skrypt `tools/tv_adb.py` ma pozycję „Nadaj launcherowi uprawnienia”: zmiana ustawień systemowych (usługa
dostępności włącza się wtedy sama z ustawień), sparowane urządzenia Bluetooth, kalendarz, rysowanie nad aplikacjami i praca w tle.
Czego Android nie pozwala nadać żadnej zwykłej aplikacji, to uprawnień „systemowych” (podpis producenta): fabryczny launcher je
ma, bo jest częścią systemu, a Liquid TV nie. Dotyczy to m.in. list „Dalej oglądaj” innych aplikacji i wyłączania Boxa.
Dlatego tej listy w launcherze już nie ma.

**Szybkie ustawienia.** Przytrzymaj OK na zębatce (albo naciśnij Menu na pilocie): kafelki z Wi-Fi, Bluetooth, dźwiękiem,
wygaszaczem, trybem wydajności, zwolnieniem pamięci, uśpieniem, restartem, włączaniem telewizora (HDMI-CEC), pilotem z telefonu i ustawieniami.

**Narzędzia przez adb** (Ustawienia → Narzędzia): jednoklikowa konfiguracja (uprawnienia, usługa dostępności, dostęp do powiadomień,
ciche aktualizacje), zwolnienie pamięci (zatrzymuje aplikacje w tle i czyści cache), szybsze animacje systemu (0,5× lub wyłączone),
wyłączanie aplikacji (np. fabrycznych; z potwierdzeniem i listą do przywrócenia; wyłączenie fabrycznego ekranu głównego sprawia,
że przycisk Home otwiera Liquid TV), uśpienie, restart i wyłączenie Boxa, włączanie i przełączanie telewizora przez HDMI-CEC
(zależy od telewizora i jego ustawień CEC), a w menu aplikacji „Wymuś zatrzymanie”.

**Foldery.** Przytrzymaj OK na aplikacji → „Dodaj do folderu” (Streaming, Gry, Muzyka, Narzędzia, Dla dzieci, Inne). Foldery
są pierwsze na liście wszystkich aplikacji; aplikacja w folderze znika z głównej siatki.

**Pilot z telefonu.** Ustawienia → Narzędzia → „Pilot z telefonu”: kod QR z adresem strony działającej w launcherze (ta sama sieć
Wi-Fi). Na telefonie: pilot (strzałki, OK, wstecz, home), głośność, uśpienie/restart, pola na link kalendarza Google, miasto
pogody i adres do otwarcia na TV. W adresie jest sekret; „Nowy link” unieważnia stary. Strona działa tylko przy otwartym oknie,
chyba że włączysz „działa cały czas”.

**Teraz gra.** Widżet pokazuje utwór, wykonawcę, okładkę i postęp z aplikacji muzycznych i wideo (Spotify, YouTube Music itd.);
OK = odtwórz/pauza, przytrzymanie OK = następny utwór. Wymaga dostępu do powiadomień (nadawany przyciskiem w Narzędziach
albo w ustawieniach Androida).

**Wygląd i wydajność.** Kafelki pochylają się w stronę, z której przyszedł fokus, a grafika przesuwa się za ramką; tapeta
lekko przesuwa się przy przechodzeniu po doku; kolor akcentu i odcień szkła okien (Ustawienia → Wygląd); na jasnych zdjęciach
góra i dół są automatycznie przyciemniane, żeby zegar i nazwy aplikacji były czytelne. **Tryb wydajności** (automatyczny /
zawsze oszczędnie / pełne efekty): w oszczędnym nie ma oddychającej tapety, błysków, pochylania ani płynnego przenikania tapet,
tapeta jest mniejsza, a cienie lżejsze; automatyczny włącza go sam, gdy urządzenie jest słabe albo gdy zmierzone klatki
launchera stale się spóźniają. Po powrocie z aplikacji fokus wraca na kafelek, z którego ją uruchomiono.

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

Gdy launcher jest na ekranie, **sam sprawdza co 5 sekund** (do zmiany: 5 s, 30 s, 5 min, 1 godz.; Ustawienia → Aktualizacje),
czy w najnowszym wydaniu jest nowsza wersja. To jedno malutkie zapytanie; gdy się nie uda (brak internetu, GitHub odmawia),
odstępy rosną do 5 minut, żeby nie zasypywać serwera. Nowa wersja instaluje się, gdy przez chwilę nie dotykasz pilota.
Tę samą wersję launcher proponuje samoczynnie tylko raz (nieudaną cichą instalację ponawia po kwadransie), a przy zębatce
świeci czerwona kropka, gdy nowa wersja czeka.

**Ciche aktualizacje (bez okna Androida)** działają przez adb na samym Boxie. Jednorazowo: włącz debugowanie w Opcjach
programisty, wejdź w Ustawienia → **Narzędzia (adb)** → **Skonfiguruj adb**, a na telewizorze zaznacz „Zawsze zezwalaj”.
Instalacja wykonuje się w osobnej powłoce na Boxie (przeżywa restart launchera) i sama uruchamia launcher po zakończeniu;
jeśli coś nie pójdzie, Ustawienia → Aktualizacje → **Diagnostyka adb** pokazuje krok po kroku, co zawiodło. Wymaga Androida 8+.
Po aktualizacji pokazuje się okno **Co nowego** (ponownie: Ustawienia → Aktualizacje).

W ustawieniach launchera (zębatka): **Sprawdź aktualizacje**, **Aktualizuj teraz**, przełącznik **Aktualizuj automatycznie**
i **Ciche aktualizacje**. Bez adb system zapyta o zgodę na „instalowanie nieznanych aplikacji” i pokaże własne okno instalacji.

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
instalacja i aktualizacja Liquid TV z GitHuba (większość tego umie już sam launcher: Ustawienia → Narzędzia), włączenie usługi dostępności (pasek głośności, okno zasilania),
ustawienie domyślnego ekranu głównego, wpisanie linku w zaznaczone pole, zrzut ekranu (zapisuje w Obrazach lub na Pulpicie),
uśpienie i obudzenie Boxa, **własna komenda** (dowolna komenda `adb shell`, a zaczynając od `adb ` zwykłe polecenie adb; albo
jednorazowo `--shell "komenda"`) oraz **„Nadaj WSZYSTKIE uprawnienia”** (`--grant-all`): każde uprawnienie zadeklarowane w aplikacji
(także te, których launcher nie używa; są wpisane w manifeście tylko po to, żeby dało się je nadać), operacje appops, praca w tle,
dostęp do powiadomień i usługa dostępności, z podsumowaniem tego, czego Android nie pozwala nadać zwykłej aplikacji.
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
