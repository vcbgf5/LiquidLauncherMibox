#!/usr/bin/env python3
"""Writes the Liquid TV logo in every form the project needs, from one description (pure Python, no packages).

    python3 tools/make_logo.py

Output:  docs/logo.svg and site/logo.svg (rounded square, for the README and the page),
         app/src/main/res/drawable-v26/ic_launcher_{background,foreground,monochrome}.xml (the adaptive icon of the launcher).
The PNG fallbacks (mipmap/ic_launcher*.png, drawable-xhdpi/app_banner.png) are renders of docs/logo.svg made with any SVG renderer.

The logo: a glass TV screen with a play triangle and a dock of three coloured tiles, on the blue-violet-pink gradient.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BLUE, VIOLET, PINK = "#1F5BFF", "#7A4DF0", "#E24BA8"
TILES = ["#2F6BFF", "#7A4DF0", "#E24BA8"]


def rrect(x, y, w, h, r):
    """Path data of a rounded rectangle."""
    return (f"M{x + r:g},{y:g} H{x + w - r:g} A{r:g},{r:g} 0 0 1 {x + w:g},{y + r:g} V{y + h - r:g} "
            f"A{r:g},{r:g} 0 0 1 {x + w - r:g},{y + h:g} H{x + r:g} A{r:g},{r:g} 0 0 1 {x:g},{y + h - r:g} "
            f"V{y + r:g} A{r:g},{r:g} 0 0 1 {x + r:g},{y:g} Z")


SCREEN = rrect(25, 26, 58, 38, 9)
DOCK = rrect(30, 70.5, 48, 11.5, 5.75)
TILE_PATHS = [rrect(x, 73.6, 9.5, 5.3, 2.1) for x in (35.5, 49.25, 63)]
PLAY = "M49.5,37 L62,45 L49.5,53 Z"
SHEEN = "M20,52 C34,30 50,24 78,22 L92,22 L92,30 C62,36 40,50 30,70 L20,70 Z"


def svg(rounded=True):
    clip = f'<clipPath id="shape"><rect width="108" height="108" rx="{24 if rounded else 0}"/></clipPath>'
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512" role="img" aria-label="Liquid TV">
 <defs>
  {clip}
  <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{BLUE}"/><stop offset=".5" stop-color="{VIOLET}"/><stop offset="1" stop-color="{PINK}"/></linearGradient>
  <radialGradient id="glow" cx="26" cy="18" r="76" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff" stop-opacity=".42"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
  <linearGradient id="rim" x1="25" y1="26" x2="83" y2="64" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff" stop-opacity=".98"/><stop offset=".5" stop-color="#fff" stop-opacity=".2"/><stop offset="1" stop-color="#fff" stop-opacity=".8"/></linearGradient>
  <linearGradient id="fill" x1="28" y1="28" x2="80" y2="64" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff" stop-opacity=".38"/><stop offset="1" stop-color="#fff" stop-opacity=".08"/></linearGradient>
  <clipPath id="scr"><path d="{SCREEN}"/></clipPath>
 </defs>
 <g clip-path="url(#shape)">
  <rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#glow)"/>
  <g clip-path="url(#scr)"><path d="{SHEEN}" fill="#fff" fill-opacity=".2"/></g>
  <path d="{SCREEN}" fill="url(#fill)" stroke="url(#rim)" stroke-width="2.4"/>
  <path d="{PLAY}" fill="#fff" stroke="#fff" stroke-width="3.2" stroke-linejoin="round"/>
  <path d="{DOCK}" fill="#fff" fill-opacity=".94"/>
  {''.join(f'<path d="{p}" fill="{c}"/>' for p, c in zip(TILE_PATHS, TILES))}
 </g>
</svg>
'''


NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'


def vector(body, aapt=False):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS}{chr(10) + "    " + AAPT if aapt else ""}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
'''


def argb(hex_color, alpha):
    return "#%02X%s" % (round(alpha * 255), hex_color.lstrip("#").upper())


def gradient(kind, items, **attrs):
    lines = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in items)
    extra = "".join(f'\n                android:{k}="{v}"' for k, v in attrs.items())
    return f'                <gradient\n                android:type="{kind}"{extra}>\n{lines}            </gradient>\n'


def android_files():
    background = vector(f'''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
{gradient("linear", [(0, argb(BLUE, 1)), (0.5, argb(VIOLET, 1)), (1, argb(PINK, 1))], startX=0, startY=0, endX=108, endY=108)}        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
{gradient("radial", [(0, "#6BFFFFFF"), (1, "#00FFFFFF")], centerX=26, centerY=18, gradientRadius=76)}        </aapt:attr>
    </path>
''', aapt=True)
    tiles = "".join(f'    <path android:fillColor="{argb(c, 1)}" android:pathData="{p}" />\n' for p, c in zip(TILE_PATHS, TILES))
    foreground = vector(f'''    <group>
        <clip-path android:pathData="{SCREEN}" />
        <path android:fillColor="#33FFFFFF" android:pathData="{SHEEN}" />
    </group>
    <path
        android:pathData="{SCREEN}"
        android:strokeWidth="2.4">
        <aapt:attr name="android:fillColor">
{gradient("linear", [(0, "#61FFFFFF"), (1, "#14FFFFFF")], startX=28, startY=28, endX=80, endY=64)}        </aapt:attr>
        <aapt:attr name="android:strokeColor">
{gradient("linear", [(0, "#FAFFFFFF"), (0.5, "#33FFFFFF"), (1, "#CCFFFFFF")], startX=25, startY=26, endX=83, endY=64)}        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{PLAY}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.2"
        android:strokeLineJoin="round" />
    <path android:fillColor="#F0FFFFFF" android:pathData="{DOCK}" />
{tiles}''', aapt=True)
    mono = vector(f'''    <path
        android:fillColor="#33FFFFFF"
        android:pathData="{SCREEN}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2.4" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{PLAY}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.2"
        android:strokeLineJoin="round" />
    <path android:fillColor="#FFFFFFFF" android:pathData="{DOCK}" />
''')
    return {"ic_launcher_background.xml": background, "ic_launcher_foreground.xml": foreground, "ic_launcher_monochrome.xml": mono}


def main():
    for target in ("docs/logo.svg", "site/logo.svg"):
        path = ROOT / target
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(svg(True), encoding="utf-8")
    (ROOT / "docs" / "logo-square.svg").write_text(svg(False), encoding="utf-8")
    out = ROOT / "app/src/main/res/drawable-v26"
    out.mkdir(parents=True, exist_ok=True)
    for name, text in android_files().items():
        (out / name).write_text(text, encoding="utf-8")
    print("logo written")


if __name__ == "__main__":
    main()
