#!/usr/bin/env python3
"""Builds the clickable browser preview (one self-contained HTML file) from preview/src/.

    python3 tools/build_preview.py                      # preview/preview.html and webos/index.html
    python3 tools/build_preview.py --version 1.0.15     # version shown in the preview
    python3 tools/build_preview.py --out site.html      # a single output file

preview/src/preview.html is the editable page. The pictures in preview/src/assets/ (app tiles
app-*.jpg and wallpapers wall-*.jpg) are inlined as data: URLs, and the "what's new" list is taken
from Changelog.kt, so the preview shows what the app shows.
"""
import argparse
import base64
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "preview" / "src"
CHANGELOG = ROOT / "app/src/main/java/com/liquidglass/tvlauncher/data/Changelog.kt"


def changelog_lines():
    text = CHANGELOG.read_text(encoding="utf-8")
    block = text.split("listOf(", 1)[1].rsplit(")", 1)[0]
    lines = []
    for m in re.finditer(r'^\s*"((?:[^"\\]|\\.)*)"\s*,?\s*$', block, re.M):
        raw = m.group(1).replace('\\"', '"').replace("\\n", " ").replace("\\\\", "\\")
        lines.append(html.escape(raw, quote=False))
    return lines


def js(value):
    # Safe inside <script>: no "</script" and no U+2028/2029 surprises.
    return json.dumps(value, ensure_ascii=False).replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--version", default=(SRC / "version.txt").read_text().strip(), help="version shown in the preview (default: preview/src/version.txt)")
    ap.add_argument("--out", help="write only this file instead of preview/preview.html and webos/index.html")
    args = ap.parse_args()

    assets = {}
    for f in sorted((SRC / "assets").glob("*.jpg")):
        assets[f.stem] = "data:image/jpeg;base64," + base64.b64encode(f.read_bytes()).decode("ascii")
    page = (SRC / "preview.html").read_text(encoding="utf-8")
    for token, value in (("__ASSETS__", js(assets)), ("__CHANGELOG__", js(changelog_lines())), ("__VERSION__", args.version.lstrip("v"))):
        if token not in page:
            sys.exit("missing placeholder " + token)
        page = page.replace(token, value)

    outs = [Path(args.out)] if args.out else [ROOT / "preview" / "preview.html", ROOT / "webos" / "index.html"]
    for out in outs:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(page, encoding="utf-8")
        print("wrote", out, "%.0f KB" % (len(page.encode("utf-8")) / 1024))


if __name__ == "__main__":
    main()
