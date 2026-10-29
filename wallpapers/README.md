# Tapety

Launcher losuje tapety z tego folderu i pobiera je przez `raw.githubusercontent.com`.
**Każdy podfolder to kategoria, każdy obraz w nim to tapeta.** Lista, którą czyta aplikacja,
to `index.json`; buduje ją automatycznie workflow **Wallpapers**.

## Skąd wziąć 100 zdjęć z Unsplash (raz, ok. 5 minut)

1. Załóż darmowe konto na <https://unsplash.com/developers> i kliknij **New Application**
   (zaakceptuj warunki, nazwa dowolna, np. „Liquid TV”).
2. Na stronie aplikacji skopiuj **Access Key** (ten krótszy, NIE „Secret key”).
3. W repozytorium na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**.
   Nazwa: `UNSPLASH_ACCESS_KEY`, wartość: skopiowany klucz.
4. Zakładka **Actions → Wallpapers → Run workflow**.

Darmowy limit Unsplash to 50 zapytań na godzinę, więc jedno uruchomienie pobiera ok. 40 zdjęć.
**Uruchom workflow jeszcze dwa razy w odstępach godziny** — każde kolejne dokończy to, czego brakowało,
aż w każdej kategorii będzie 10 zdjęć (razem 100). Workflow sam zatwierdza zdjęcia i `index.json` w repo.

Kategorie i hasła wyszukiwania są w [`tools/wallpaper_categories.json`](../tools/wallpaper_categories.json) —
możesz je zmienić (np. dodać „samochody”) i znów uruchomić workflow.

## Własne zdjęcia

Wrzuć je do folderu kategorii (nowy folder = nowa kategoria) przez **Add file → Upload files**.
Najlepiej JPEG 1920×1080 (16:9), do ok. 400 KB. Po commicie workflow sam odświeży `index.json`.

## Licencja i autorzy

Zdjęcia z Unsplash są na [licencji Unsplash](https://unsplash.com/license). Autorzy są w `credits.json`,
a launcher pokazuje imię autora bieżącej tapety w swoich ustawieniach.
