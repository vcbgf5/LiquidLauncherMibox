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
import base64
import json
import os
import platform
import shlex
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import time
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
    say("Naciśnij teraz PRAWDZIWY przycisk głośności (albo Menu) na pilocie Boxa, potem wróć tu i wciśnij Enter.\n"
        "(Klawisz wysłany przez adb omija usługę dostępności, więc nie sprawdza niczego.)")
    input()
    code, out = tv.run("logcat", "-d", "-s", "LiquidTV:I", timeout=30)
    say("\n".join(out.splitlines()[-8:]) or "(usługa nie widzi żadnego klawisza: sprawdź, czy jest włączona, albo pilot nie wysyła go do Boxa)")


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


def writable_folder():
    """A folder the user can always write to (the current one may be a protected program folder)."""
    candidates = [Path.home() / "Pictures", Path.home() / "Desktop", Path.home() / "Downloads", Path.home(), Path.cwd()]
    for folder in candidates:
        try:
            if folder.is_dir():
                probe = folder / ".liquidtv_write_test"
                probe.write_bytes(b"")
                probe.unlink()
                return folder
        except OSError:
            continue
    import tempfile
    return Path(tempfile.gettempdir())


def screenshot(tv):
    target = writable_folder() / "zrzut_ekranu_tv.png"
    command = [tv.adb, "-s", tv.serial, "exec-out", "screencap", "-p"]
    try:
        with open(target, "wb") as handle:
            result = subprocess.run(command, stdout=handle, stderr=subprocess.PIPE)
    except OSError as error:
        say(f"Nie udało się zapisać pliku ({error}).")
        return
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


def custom_command(tv):
    """Dowolna komenda na Boxie. Bez przedrostka to `adb shell ...`; zaczynając od `adb ` wykonuje zwykłe polecenie adb."""
    say("Wpisz komendę dla Boxa (pusta linia = powrót do menu). Przykłady:\n"
        "  pm list packages -d                    (wyłączone aplikacje)\n"
        "  dumpsys audio                          (stan dźwięku)\n"
        "  input keyevent 24                      (głośność w górę)\n"
        "  settings get global hdmi_control_enabled\n"
        "  adb logcat -d -s LiquidTV              (zaczynając od „adb ” to zwykłe polecenie adb)")
    while True:
        line = input("komenda> ").strip()
        if not line or line.lower() in ("q", "quit", "exit", "koniec"):
            return
        if line.lower().startswith("adb "):
            try:
                words = shlex.split(line[4:], posix=(os.name != "nt"))
            except ValueError as error:
                say(f"Nie rozumiem tej linii ({error}).")
                continue
            code, out = tv.run(*words, timeout=300)
        else:
            code, out = tv.shell(line, timeout=300)
        say(out if out else "(brak wyjścia)")
        if code != 0:
            say(f"[kod wyjścia: {code}]")


def declared_permissions(tv):
    """Uprawnienia zadeklarowane w zainstalowanej aplikacji i które z nich są już nadane (według dumpsys package)."""
    code, out = tv.shell("dumpsys", "package", PACKAGE, timeout=60)
    lines = out.splitlines()
    declared = []
    granted = set()
    start = next((i for i, l in enumerate(lines) if l.strip() == "requested permissions:"), None)
    if start is not None:
        indent = len(lines[start]) - len(lines[start].lstrip())
        for l in lines[start + 1:]:
            if l.strip() and len(l) - len(l.lstrip()) <= indent:
                break
            name = l.strip().split(":")[0]
            if name and all(c.isalnum() or c in "._" for c in name):
                declared.append(name)
    for l in lines:
        text = l.strip()
        if ": granted=true" in text:
            granted.add(text.split(":")[0])
    return declared, granted


def grant_everything(tv):
    """Nadaje WSZYSTKO, co Android pozwala: każde zadeklarowane uprawnienie, operacje appops, pracę w tle i usługi."""
    say("Nadaję wszystkie uprawnienia (także te, których launcher nie potrzebuje) ...")
    declared, _ = declared_permissions(tv)
    if not declared:
        say("Nie widzę uprawnień aplikacji: czy Liquid TV jest zainstalowany? (pozycja 2 w menu)")
        return
    for permission in declared:
        tv.shell("pm", "grant", PACKAGE, permission)
    say(f"  próbowałem nadać {len(declared)} uprawnień")

    # Operacje, które nie są zwykłymi uprawnieniami (appops), oraz praca w tle.
    ops = ["SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "REQUEST_INSTALL_PACKAGES", "GET_USAGE_STATS", "RUN_IN_BACKGROUND",
           "RUN_ANY_IN_BACKGROUND", "WAKE_LOCK", "START_FOREGROUND", "BOOT_COMPLETED", "POST_NOTIFICATION", "ACCESS_NOTIFICATIONS",
           "MANAGE_EXTERNAL_STORAGE", "PICTURE_IN_PICTURE", "READ_CLIPBOARD", "WRITE_CLIPBOARD", "TOAST_WINDOW",
           "CAMERA", "RECORD_AUDIO", "COARSE_LOCATION", "FINE_LOCATION", "READ_CONTACTS", "WRITE_CONTACTS",
           "READ_CALENDAR", "WRITE_CALENDAR", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_PHONE_STATE",
           "ACTIVITY_RECOGNITION", "BODY_SENSORS", "WIFI_SCAN", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"]
    done = 0
    for op in ops:
        code, out = tv.shell("appops", "set", PACKAGE, op, "allow")
        if code == 0 and "rror" not in out and "xception" not in out:
            done += 1
    say(f"  operacje appops: {done} z {len(ops)} przyjęte (reszty ten Android nie zna)")
    for label, command in [
        ("bez oszczędzania baterii (doze)", ["cmd", "deviceidle", "whitelist", f"+{PACKAGE}"]),
        ("najwyższy priorytet w tle (standby bucket)", ["am", "set-standby-bucket", PACKAGE, "active"]),
        ("dostęp do powiadomień i odtwarzaczy", ["cmd", "notification", "allow_listener", f"{PACKAGE}/.MediaListenerService"]),
    ]:
        code, out = tv.shell(*command)
        failed = code != 0 or "xception" in out or "rror" in out
        say(f"  {'BŁĄD' if failed else 'OK  '}  {label}" + (f"\n        {out.splitlines()[0]}" if failed and out else ""))
    # Usługi, także gdy powyższe polecenie powiadomień nie istnieje w tym Androidzie.
    code, current = tv.shell("settings", "get", "secure", "enabled_notification_listeners")
    entry = f"{PACKAGE}/{PACKAGE}.MediaListenerService"
    if entry not in current:
        merged = entry if not current.strip() or current.strip() == "null" else current.strip() + ":" + entry
        tv.shell("settings", "put", "secure", "enabled_notification_listeners", merged)
    enable_service(tv)

    declared, granted = declared_permissions(tv)
    missing = [p for p in declared if p not in granted]
    say(f"\nZadeklarowanych uprawnień: {len(declared)}, nadanych: {len(declared) - len(missing)}.")
    if missing:
        say("Nienadane (Android nie pozwala ich nadać zwykłej aplikacji: to uprawnienia systemowe/podpis producenta albo\n"
            "niedostępne w tej wersji Androida):\n  " + "\n  ".join(missing))


# ---- Podgląd ekranu na żywo -------------------------------------------------------------------------------

SCRCPY_FALLBACK = "https://github.com/Genymobile/scrcpy/releases/download/v2.7/scrcpy-win64-v2.7.zip"


def find_scrcpy():
    """`scrcpy` z PATH albo z folderu scrcpy obok skryptu; None, jeśli go nie ma."""
    found = shutil.which("scrcpy")
    if found:
        return found
    exe = "scrcpy.exe" if platform.system() == "Windows" else "scrcpy"
    for candidate in sorted(HERE.glob(f"scrcpy*/{exe}")):
        return str(candidate)
    return None


def download_scrcpy():
    """Pobiera scrcpy (płynny podgląd i sterowanie myszą) z oficjalnego GitHuba; tylko dla Windows (gdzie to najwygodniejsze)."""
    if platform.system() != "Windows":
        say("Na tym systemie zainstaluj scrcpy menedżerem pakietów (np. `brew install scrcpy`, `sudo apt install scrcpy`).")
        return None
    url = SCRCPY_FALLBACK
    try:
        with urllib.request.urlopen("https://api.github.com/repos/Genymobile/scrcpy/releases/latest", timeout=20) as response:
            assets = json.load(response).get("assets", [])
        url = next((a["browser_download_url"] for a in assets if "win64" in a["name"] and a["name"].endswith(".zip")), url)
    except Exception:
        pass
    say(f"Pobieram scrcpy (ok. 20 MB):\n  {url}")
    with tempfile.TemporaryDirectory() as tmp:
        target = Path(tmp) / "scrcpy.zip"
        urllib.request.urlretrieve(url, target)
        with zipfile.ZipFile(target) as archive:
            archive.extractall(HERE)
    return find_scrcpy()


KEYS = {
    "Up": 19, "Down": 20, "Left": 21, "Right": 22, "Return": 23, "KP_Enter": 23, "space": 23,
    "Escape": 4, "BackSpace": 4, "h": 3, "H": 3, "plus": 24, "equal": 24, "KP_Add": 24, "minus": 25, "KP_Subtract": 25,
    "m": 164, "p": 85, "Prior": 92, "Next": 93,
}


def tk_live_view(tv):
    """Okno z odświeżanym zrzutem ekranu (wystarcza zwykły Python z tkinter) i sterowaniem klawiaturą."""
    try:
        import tkinter as tk
    except ImportError:
        say("Ten Python nie ma tkinter, więc nie ma okna podglądu. Zainstaluj scrcpy (pozycja ponownie) albo Pythona z tkinter.")
        return
    root = tk.Tk()
    root.title("Liquid TV: podgląd na żywo | strzałki, Enter, Esc = wstecz, H = home, + / - = głośność, Q = zamknij")
    label = tk.Label(root, bg="black")
    label.pack()
    state = {"png": None, "run": True, "fps": 0.0}

    def grab():
        command = [tv.adb, "-s", tv.serial, "exec-out", "screencap", "-p"]
        while state["run"]:
            started = time.time()
            try:
                data = subprocess.run(command, capture_output=True, timeout=20).stdout
            except Exception:
                data = b""
            if data.startswith(b"\x89PNG"):
                state["png"] = data
                state["fps"] = 1.0 / max(time.time() - started, 0.01)
            else:
                time.sleep(1)

    def refresh():
        data = state["png"]
        if data is not None:
            state["png"] = None
            image = tk.PhotoImage(data=base64.b64encode(data).decode("ascii"))
            # Połowa rozmiaru, żeby okno mieściło się na ekranie; pełna jakość jest w zrzucie z menu.
            factor = max(1, round(image.width() / 960))
            image = image.subsample(factor, factor)
            label.configure(image=image)
            label.image = image
            root.title(f"Liquid TV: podgląd na żywo ({state['fps']:.1f} kl./s) | strzałki, Enter, Esc, H, + / -, Q = zamknij")
        if state["run"]:
            root.after(120, refresh)

    def press(event):
        if event.keysym in ("q", "Q"):
            close()
            return
        code = KEYS.get(event.keysym)
        if code is not None:
            threading.Thread(target=lambda: tv.shell("input", "keyevent", str(code)), daemon=True).start()

    def close():
        state["run"] = False
        root.destroy()

    root.bind("<Key>", press)
    root.protocol("WM_DELETE_WINDOW", close)
    threading.Thread(target=grab, daemon=True).start()
    refresh()
    root.mainloop()


def live_view(tv):
    """Ekran Boxa na żywo: scrcpy (płynnie, z myszą), a gdy go nie ma, okno z odświeżanym zrzutem."""
    scrcpy = find_scrcpy()
    if not scrcpy:
        say("Płynny podgląd robi program scrcpy (darmowy, oficjalny). Bez niego pokażę okno odświeżane około raz na sekundę.")
        if input("Pobrać scrcpy teraz? [T/n] ").strip().lower() in ("", "t", "tak", "y", "yes"):
            try:
                scrcpy = download_scrcpy()
            except Exception as error:
                say(f"Nie udało się pobrać scrcpy ({error}).")
    if scrcpy:
        say("Otwieram podgląd (scrcpy). Zamknięcie okna wraca do menu.")
        environment = dict(os.environ, ADB=tv.adb)
        command = [scrcpy, "-s", tv.serial, "--window-title", "Liquid TV: podgląd na żywo", "--no-audio"]
        code = subprocess.run(command, env=environment).returncode
        if code == 0:
            return
        say("scrcpy się nie uruchomił; pokazuję prostszy podgląd.")
    tk_live_view(tv)


MENU = [
    ("Pokaż stan (wersja, usługa dostępności, domyślny launcher)", status),
    ("Zainstaluj / zaktualizuj Liquid TV (najnowsza wersja z GitHuba)", install),
    ("Włącz usługę dostępności (pasek głośności, okno zasilania)", enable_service),
    ("Ustaw Liquid TV jako domyślny ekran główny", set_home),
    ("Wpisz link do pobrania w zaznaczone pole na Boxie", type_link),
    ("Otwórz Liquid TV na Boxie", open_launcher),
    ("Zrzut ekranu Boxa (zapisze plik w Obrazach lub na Pulpicie)", screenshot),
    ("Podgląd ekranu Boxa NA ŻYWO (scrcpy albo okno z odświeżaniem)", live_view),
    ("Uśpij Boxa", sleep_tv),
    ("Obudź Boxa", wake_tv),
    ("Podłącz kalendarz Google (link iCal) do widżetu wydarzeń", connect_calendar),
    ("Ustaw miasto pogody", set_weather_city),
    ("Nadaj launcherowi uprawnienia (ustawienia, Bluetooth, kalendarz, tło)", grant_permissions),
    ("Nadaj WSZYSTKIE uprawnienia (także niepotrzebne)", grant_everything),
    ("Własna komenda (adb shell albo adb ...)", custom_command),
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
    parser.add_argument("--live", action="store_true", help="pokaż ekran Boxa na żywo")
    parser.add_argument("--shell", metavar="KOMENDA", help="wykonaj jedną komendę na Boxie (adb shell) i zakończ")
    parser.add_argument("--grant-all", action="store_true", help="nadaj wszystkie uprawnienia i zakończ")
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
    if args.shell:
        code, out = tv.shell(args.shell, timeout=300)
        say(out or "(brak wyjścia)")
        sys.exit(0 if code == 0 else 1)
    if args.grant_all:
        actions.append((True, grant_everything))
    if args.live:
        actions.append((True, live_view))
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
