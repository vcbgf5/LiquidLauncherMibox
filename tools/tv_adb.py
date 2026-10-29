#!/usr/bin/env python3
"""
Liquid TV: połączenie z Boxem / telewizorem przez ADB i najczęstsze czynności, z komputera.

Sam łączysz się z urządzeniem (domyślnie port 5555). Skrypt nie ma żadnych zależności poza
Pythonem 3.8+; jeśli na komputerze nie ma programu `adb`, może pobrać go od Google (platform-tools)
do folderu obok siebie.

Przed pierwszym użyciem na Boxie: Ustawienia -> Preferencje urządzenia -> Informacje ->
kliknij 7 razy „Kompilacja”, potem Opcje programisty -> włącz „Debugowanie USB” (a jeśli jest,
także „Debugowanie przez sieć”). Adres IP Boxa: Ustawienia -> Sieć. Przy pierwszym połączeniu
Box pokaże pytanie „Zezwolić na debugowanie USB?”: zaznacz „Zawsze” i potwierdź.

Użycie:
    python tv_adb.py                      # zapyta o adres IP i pokaże menu
    python tv_adb.py 192.168.1.50         # port 5555, menu
    python tv_adb.py 192.168.1.50 --port 5555 --install --enable-service
    python tv_adb.py --help
"""
import argparse
import os
import platform
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

PACKAGE = "com.liquidglass.tvlauncher"
SERVICE = f"{PACKAGE}/{PACKAGE}.SystemService"
APK_URL = "https://github.com/vcbgf5/LiquidLauncherMibox/releases/latest/download/LiquidTV.apk"
SITE_URL = "https://vcbgf5.github.io/LiquidLauncherMibox/"
HERE = Path(__file__).resolve().parent


def say(text=""):
    print(text, flush=True)


# ---------------------------------------------------------------------------------------------
# adb: znalezienie programu i uruchamianie poleceń
# ---------------------------------------------------------------------------------------------

def platform_tools_url():
    system = {"Windows": "windows", "Darwin": "darwin"}.get(platform.system(), "linux")
    return f"https://dl.google.com/android/repository/platform-tools-latest-{system}.zip"


def find_adb():
    """`adb` z PATH albo z folderu platform-tools obok skryptu; None, jeśli go nie ma."""
    found = shutil.which("adb")
    if found:
        return found
    exe = "adb.exe" if platform.system() == "Windows" else "adb"
    local = HERE / "platform-tools" / exe
    return str(local) if local.exists() else None


def download_platform_tools():
    """Pobiera oficjalne narzędzia Google (platform-tools) do folderu obok skryptu."""
    url = platform_tools_url()
    say(f"Pobieram platform-tools od Google:\n  {url}")
    with tempfile.TemporaryDirectory() as tmp:
        target = Path(tmp) / "platform-tools.zip"
        urllib.request.urlretrieve(url, target)
        with zipfile.ZipFile(target) as archive:
            archive.extractall(HERE)
    exe = HERE / "platform-tools" / ("adb.exe" if platform.system() == "Windows" else "adb")
    if exe.exists() and platform.system() != "Windows":
        exe.chmod(exe.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return str(exe) if exe.exists() else None


def ensure_adb():
    adb = find_adb()
    if adb:
        return adb
    say("Nie znalazłem programu `adb` (ani w PATH, ani w folderze platform-tools obok skryptu).")
    if input("Pobrać go od Google teraz? [T/n] ").strip().lower() in ("", "t", "tak", "y", "yes"):
        adb = download_platform_tools()
        if adb:
            say(f"Gotowe: {adb}")
            return adb
    sys.exit("Bez `adb` dalej się nie da. Pobierz platform-tools: "
             "https://developer.android.com/tools/releases/platform-tools")


class Tv:
    """Jedno urządzenie, z którym rozmawiamy przez adb."""

    def __init__(self, adb, serial):
        self.adb = adb
        self.serial = serial

    def run(self, *args, timeout=120, check=False):
        """Uruchamia `adb -s <urządzenie> ...` i zwraca (kod, tekst wyjścia)."""
        command = [self.adb, "-s", self.serial, *args]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return 124, "Przekroczono czas oczekiwania."
        output = (result.stdout + result.stderr).strip()
        if check and result.returncode != 0:
            say(output)
        return result.returncode, output

    def shell(self, *args, **kwargs):
        return self.run("shell", *args, **kwargs)

    def prop(self, name):
        return self.shell("getprop", name)[1].strip()


def connect(adb, ip, port):
    serial = f"{ip}:{port}"
    say(f"Łączę z {serial} ...")
    result = subprocess.run([adb, "connect", serial], capture_output=True, text=True, timeout=30)
    text = (result.stdout + result.stderr).strip()
    say(text)
    tv = Tv(adb, serial)
    code, state = tv.run("get-state", timeout=15)
    if code != 0 or state.strip() != "device":
        if "unauthorized" in state:
            say("Box czeka na Twoją zgodę: potwierdź na ekranie pytanie o debugowanie i spróbuj jeszcze raz.")
        else:
            say("Nie udało się połączyć. Sprawdź, czy:\n"
                "  - komputer i Box są w tej samej sieci, a adres IP się zgadza,\n"
                "  - w opcjach programisty włączone jest debugowanie (USB / przez sieć),\n"
                f"  - port {port} jest właściwy (zwykle 5555; w razie potrzeby przez USB: adb tcpip 5555).")
        return None
    model = tv.prop("ro.product.model") or "?"
    android = tv.prop("ro.build.version.release") or "?"
    say(f"Połączono: {model}, Android {android}.")
    return tv


# ---------------------------------------------------------------------------------------------
# Czynności
# ---------------------------------------------------------------------------------------------

def installed_version(tv):
    code, out = tv.shell("dumpsys", "package", PACKAGE)
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("versionName="):
            return line.split("=", 1)[1]
    return None


def status(tv):
    version = installed_version(tv)
    say(f"Liquid TV na Boxie: {'wersja ' + version if version else 'nie jest zainstalowany'}")
    code, out = tv.shell("settings", "get", "secure", "enabled_accessibility_services")
    on = SERVICE in out or f"{PACKAGE}/.SystemService" in out
    say(f"Usługa dostępności (pasek głośności, zasilanie): {'włączona' if on else 'wyłączona'}")
    code, out = tv.shell("cmd", "package", "resolve-activity", "--brief",
                         "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    say(f"Domyślny ekran główny: {out.splitlines()[-1] if out else '?'}")


def download_apk():
    target = Path(tempfile.gettempdir()) / "LiquidTV.apk"
    say(f"Pobieram najnowszą wersję:\n  {APK_URL}")
    urllib.request.urlretrieve(APK_URL, target)
    say(f"Pobrano {target.stat().st_size / 1_000_000:.1f} MB.")
    return target


def install(tv):
    apk = download_apk()
    say("Instaluję na Boxie (to chwilę trwa) ...")
    code, out = tv.run("install", "-r", str(apk), timeout=300)
    say(out)
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out or "signatures do not match" in out:
        say("\nZainstalowana wersja jest podpisana innym kluczem (to dotyczy tylko wersji sprzed 1.0.4).\n"
            "Trzeba ją raz odinstalować; ustawienia launchera wrócą do domyślnych.")
        if input("Odinstalować starą wersję i zainstalować nową? [t/N] ").strip().lower() in ("t", "tak", "y", "yes"):
            say(tv.run("uninstall", PACKAGE)[1])
            say(tv.run("install", str(apk), timeout=300)[1])
    elif code == 0:
        say("Zainstalowano.")


def uninstall(tv):
    if input("Odinstalować Liquid TV (usunie jego ustawienia)? [t/N] ").strip().lower() in ("t", "tak", "y", "yes"):
        say(tv.run("uninstall", PACKAGE)[1])


def enable_service(tv):
    """Włącza usługę dostępności Liquid TV, nie ruszając innych już włączonych usług."""
    code, current = tv.shell("settings", "get", "secure", "enabled_accessibility_services")
    current = "" if current.strip() in ("", "null") else current.strip()
    services = [s for s in current.split(":") if s]
    if SERVICE not in services:
        services.append(SERVICE)
    tv.shell("settings", "put", "secure", "enabled_accessibility_services", ":".join(services), check=True)
    tv.shell("settings", "put", "secure", "accessibility_enabled", "1", check=True)
    say("Usługa dostępności Liquid TV włączona (pasek głośności nad każdą aplikacją i okno zasilania).")


def disable_service(tv):
    code, current = tv.shell("settings", "get", "secure", "enabled_accessibility_services")
    services = [s for s in current.strip().split(":") if s and s != "null" and PACKAGE not in s]
    tv.shell("settings", "put", "secure", "enabled_accessibility_services", ":".join(services) or "null")
    say("Usługa dostępności Liquid TV wyłączona.")


def volume_diagnostics(tv):
    """Zbiera to, co decyduje o głośności: usługa dostępności, audio, CEC i dziennik Liquid TV."""
    say("== Usługa dostępności Liquid TV ==")
    code, out = tv.shell("settings", "get", "secure", "enabled_accessibility_services")
    say(f"enabled_accessibility_services = {out}")
    code, out = tv.shell("dumpsys", "accessibility")
    lines = [l.strip() for l in out.splitlines() if "liquid" in l.lower() or "SystemService" in l]
    say("\n".join(lines[:8]) or "(usługa nie występuje w dumpsys accessibility: nie jest włączona albo nie działa)")
    say("\n== Audio ==")
    code, out = tv.shell("dumpsys", "audio")
    keep = [l.strip() for l in out.splitlines()
            if any(k in l.lower() for k in ("fixed", "hdmi", "stream_music", "cec", "volume"))]
    say("\n".join(keep[:25]) or "(brak danych)")
    say("\n== HDMI-CEC ==")
    for name in ("hdmi_control_enabled", "hdmi_control_auto_device_off_enabled", "hdmi_system_audio_control_enabled"):
        say(f"{name} = {tv.shell('settings', 'get', 'global', name)[1]}")
    say("\n== Dziennik Liquid TV (ostatnie wpisy) ==")
    code, out = tv.run("logcat", "-d", "-s", "LiquidTV:I", timeout=30)
    say("\n".join(out.splitlines()[-15:]) or "(pusto: usługa nie zapisała jeszcze żadnego klawisza)")
    say("\n== Test ==")
    say("Wysyłam klawisz głośności w górę przez adb i czytam dziennik jeszcze raz ...")
    tv.shell("input", "keyevent", "KEYCODE_VOLUME_UP")
    code, out = tv.run("logcat", "-d", "-s", "LiquidTV:I", timeout=30)
    say("\n".join(out.splitlines()[-4:]) or "(usługa nie widzi klawisza)")


def set_home(tv):
    say("Ustawiam Liquid TV jako domyślny ekran główny ...")
    code, out = tv.shell("cmd", "package", "set-home-activity", f"{PACKAGE}/.MainActivity")
    say(out or "Gotowe.")
    if code != 0:
        say("Jeśli to nie zadziałało, wciśnij Home na pilocie i wybierz Liquid TV z listy „Zawsze”.")


def set_weather_city(tv):
    """Ustawia miasto pogody (dowolne: launcher znajdzie je po nazwie przez Open-Meteo)."""
    name = input("Miasto (np. Gdańsk, Zakopane, Berlin): ").strip()
    if not name:
        return
    code, out = tv.shell("am", "start", "-n", f"{PACKAGE}/.MainActivity", "--es", "weather_city", f"'{name}'")
    say(out or f"Wysłano: {name}. Pogoda odświeży się za chwilę (jeśli miasta nie znajdzie, zostanie poprzednie).")


def grant_permissions(tv):
    """Nadaje launcherowi wszystko, co Android pozwala nadać zwykłej aplikacji przez adb."""
    say("Nadaję uprawnienia ...")
    steps = [
        ("zmiana ustawień systemowych (włączanie usługi dostępności z poziomu aplikacji)",
         ["pm", "grant", PACKAGE, "android.permission.WRITE_SECURE_SETTINGS"]),
        ("lista sparowanych urządzeń Bluetooth (Android 12+)",
         ["pm", "grant", PACKAGE, "android.permission.BLUETOOTH_CONNECT"]),
        ("odczyt kalendarza systemowego",
         ["pm", "grant", PACKAGE, "android.permission.READ_CALENDAR"]),
        ("rysowanie nad innymi aplikacjami",
         ["appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow"]),
        ("praca w tle bez oszczędzania baterii",
         ["cmd", "deviceidle", "whitelist", f"+{PACKAGE}"]),
    ]
    for label, command in steps:
        code, out = tv.shell(*command)
        failed = code != 0 or "Exception" in out or "Unknown permission" in out or "not a changeable" in out
        say(f"  {'BŁĄD' if failed else 'OK  '}  {label}" + (f"\n        {out.splitlines()[0]}" if failed and out else ""))
    enable_service(tv)
    say("\nCzego Android nie pozwala nadać żadnej zwykłej aplikacji: uprawnień „systemowych” (podpis producenta),\n"
        "np. odczytu list „Dalej oglądaj” innych aplikacji (READ_EPG_DATA) albo wyłączania Boxa. Fabryczny launcher\n"
        "je ma, bo jest częścią systemu. Reszta, co widzi i robi fabryczny launcher, jest dostępna po powyższych krokach.")


def connect_calendar(tv):
    """Podłącza kalendarz Google do widżetu wydarzeń przez tajny adres iCal."""
    say("Kalendarz Google na komputerze: Ustawienia kalendarza -> (wybierz kalendarz) -> „Tajny adres w formacie iCal”.\n"
        "Skopiuj ten link i wklej tutaj (pusty = odłącz kalendarz).")
    url = input("Link iCal: ").strip()
    if url and not url.startswith(("http://", "https://", "webcal://")):
        say("To nie wygląda jak link (powinien zaczynać się od https://).")
        return
    code, out = tv.shell("am", "start", "-n", f"{PACKAGE}/.MainActivity", "--es", "ical_url", f"'{url}'")
    say(out or ("Kalendarz podłączony." if url else "Kalendarz odłączony."))


def type_link(tv):
    """Wpisuje link do pobrania w zaznaczone pole na Boxie (np. w Downloaderze)."""
    choices = {"1": APK_URL, "2": SITE_URL + "apk"}
    say("Który link wpisać w zaznaczone pole na Boxie?\n"
        f"  1) bezpośredni do pliku: {APK_URL}\n"
        f"  2) ze strony pobierania: {SITE_URL}apk")
    url = choices.get(input("Wybór [1]: ").strip() or "1")
    if url:
        tv.shell("input", "text", url, check=True)
        say("Wpisano.")


def screenshot(tv):
    target = Path.cwd() / "zrzut_ekranu_tv.png"
    command = [tv.adb, "-s", tv.serial, "exec-out", "screencap", "-p"]
    with open(target, "wb") as handle:
        result = subprocess.run(command, stdout=handle, stderr=subprocess.PIPE)
    if result.returncode == 0 and target.stat().st_size > 0:
        say(f"Zapisano zrzut ekranu: {target}")
    else:
        say("Nie udało się zrobić zrzutu ekranu.")


def sleep_tv(tv):
    tv.shell("input", "keyevent", "KEYCODE_SLEEP")
    say("Wysłano „uśpij”.")


def wake_tv(tv):
    tv.shell("input", "keyevent", "KEYCODE_WAKEUP")
    say("Wysłano „obudź”.")


def reboot(tv):
    if input("Zrestartować Boxa? [t/N] ").strip().lower() in ("t", "tak", "y", "yes"):
        tv.run("reboot")
        say("Restart wysłany.")


def open_launcher(tv):
    tv.shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    say("Otwarto Liquid TV.")


MENU = [
    ("Pokaż stan (wersja, usługa dostępności, domyślny launcher)", status),
    ("Zainstaluj / zaktualizuj Liquid TV (najnowsza wersja z GitHuba)", install),
    ("Włącz usługę dostępności (pasek głośności, okno zasilania)", enable_service),
    ("Ustaw Liquid TV jako domyślny ekran główny", set_home),
    ("Wpisz link do pobrania w zaznaczone pole na Boxie", type_link),
    ("Otwórz Liquid TV na Boxie", open_launcher),
    ("Zrzut ekranu Boxa (zapisze plik w bieżącym folderze)", screenshot),
    ("Uśpij Boxa", sleep_tv),
    ("Obudź Boxa", wake_tv),
    ("Podłącz kalendarz Google (link iCal) do widżetu wydarzeń", connect_calendar),
    ("Ustaw miasto pogody", set_weather_city),
    ("Nadaj launcherowi uprawnienia (ustawienia, Bluetooth, kalendarz, tło)", grant_permissions),
    ("Diagnostyka głośności (usługa, audio, CEC, dziennik)", volume_diagnostics),
    ("Wyłącz usługę dostępności", disable_service),
    ("Odinstaluj Liquid TV", uninstall),
    ("Zrestartuj Boxa", reboot),
]


def menu(tv):
    while True:
        say("\n=== Co zrobić? ===")
        for number, (label, _) in enumerate(MENU, 1):
            say(f"  {number:>2}) {label}")
        say("   0) Koniec")
        choice = input("Wybór: ").strip()
        if choice in ("0", "q", "Q", ""):
            return
        if choice.isdigit() and 1 <= int(choice) <= len(MENU):
            try:
                MENU[int(choice) - 1][1](tv)
            except Exception as error:  # jedna nieudana czynność nie kończy programu
                say(f"Błąd: {error}")
        else:
            say("Nie ma takiej pozycji.")


def main():
    parser = argparse.ArgumentParser(description="Liquid TV: połączenie z Boxem przez ADB i najczęstsze czynności.")
    parser.add_argument("ip", nargs="?", help="adres IP Boxa w sieci (Ustawienia -> Sieć)")
    parser.add_argument("--port", type=int, default=5555, help="port debugowania (domyślnie 5555)")
    parser.add_argument("--status", action="store_true", help="pokaż stan i zakończ")
    parser.add_argument("--install", action="store_true", help="zainstaluj / zaktualizuj najnowszą wersję")
    parser.add_argument("--enable-service", action="store_true", help="włącz usługę dostępności Liquid TV")
    parser.add_argument("--home", action="store_true", help="ustaw Liquid TV jako domyślny ekran główny")
    parser.add_argument("--screenshot", action="store_true", help="zrób zrzut ekranu Boxa")
    parser.add_argument("--sleep", action="store_true", help="uśpij Boxa")
    parser.add_argument("--wake", action="store_true", help="obudź Boxa")
    args = parser.parse_args()

    adb = ensure_adb()
    ip = args.ip or input("Adres IP Boxa (np. 192.168.1.50): ").strip()
    if not ip:
        sys.exit("Bez adresu IP nie da się połączyć.")
    tv = connect(adb, ip, args.port)
    if tv is None:
        sys.exit(1)

    actions = [
        (args.status, status), (args.install, install), (args.enable_service, enable_service),
        (args.home, set_home), (args.screenshot, screenshot), (args.sleep, sleep_tv), (args.wake, wake_tv),
    ]
    chosen = [function for flag, function in actions if flag]
    if chosen:
        for function in chosen:
            function(tv)
    else:
        menu(tv)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        say("\nPrzerwano.")
