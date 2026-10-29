# Liquid TV na LG webOS (podgląd do testów)

Prawdziwy launcher jest aplikacją na Androida i na webOS nie wystartuje. Telewizory LG uruchamiają za to aplikacje
internetowe (HTML/JS), więc ten folder pakuje **klikalny podgląd** wyglądu Liquid TV (źródło: `preview/src/`, budowany poleceniem `python3 tools/build_preview.py` do `webos/index.html`) jako
aplikację webOS. Przydaje się do obejrzenia i sprawdzenia wyglądu oraz nawigacji pilotem LG (strzałki, OK, Wstecz).
To makieta z przykładowymi ikonami, nie działający launcher.

## Jak zainstalować na telewizorze LG

1. Na telewizorze zainstaluj z LG Content Store aplikację **Developer Mode** i zaloguj się kontem LG Developer
   (darmowe, https://webostv.developer.lge.com/). Włącz „Dev Mode Status” i „Key Server”, zanotuj adres IP telewizora.
2. Na komputerze zainstaluj narzędzia: `npm install -g @webos-tools/cli`.
3. Zbuduj paczkę (albo pobierz gotowy plik `.ipk` z zakładki Actions, praca „webOS package”):
   ```bash
   ares-package webos --outdir out
   ```
4. Dodaj telewizor i zainstaluj:
   ```bash
   ares-setup-device --add lgtv --info "{'host':'IP_TELEWIZORA','port':'9922','username':'prisoner'}"
   ares-novacom --device lgtv --getkey
   ares-install --device lgtv out/com.liquidtv.preview_1.0.0_all.ipk
   ares-launch --device lgtv com.liquidtv.preview
   ```
   Aplikacja „Liquid TV (podgląd)” pojawi się też na liście aplikacji telewizora.

Konto deweloperskie LG wymaga odnawiania co kilka godzin do kilku dni (zgodnie z zasadami Developer Mode).
