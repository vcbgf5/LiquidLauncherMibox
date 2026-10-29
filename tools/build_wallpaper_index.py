#!/usr/bin/env python3
"""Rebuild wallpapers/index.json, the list the launcher app downloads.

Every sub-folder of wallpapers/ is a category and every image in it is a
wallpaper, so adding your own photos is just dropping files into a folder
(a new folder = a new category). Display names come from
tools/wallpaper_categories.json when the folder is listed there, otherwise
from the folder name. Photographer credits come from wallpapers/credits.json.

The output has no timestamp, so it only changes when the photos do.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WALL = ROOT / "wallpapers"
EXT = {".jpg", ".jpeg", ".png", ".webp"}


def load(path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def main():
    names = {c["id"]: c["name"] for c in load(ROOT / "tools/wallpaper_categories.json", [])}
    order = list(names)
    credits = load(WALL / "credits.json", {})
    cats = []
    for folder in sorted((p for p in WALL.iterdir() if p.is_dir() and not p.name.startswith(".")), key=lambda p: (order.index(p.name) if p.name in order else 999, p.name)):
        files = []
        for f in sorted(folder.iterdir()):
            if f.suffix.lower() in EXT:
                entry = {"p": f"{folder.name}/{f.name}"}
                by = credits.get(entry["p"], {}).get("name")
                if by:
                    entry["by"] = by
                files.append(entry)
        if files:
            cats.append({"id": folder.name, "name": names.get(folder.name, folder.name.replace("-", " ").replace("_", " ").title()), "files": files})
    (WALL / "index.json").write_text(json.dumps({"categories": cats}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"index.json: {len(cats)} categories, {sum(len(c['files']) for c in cats)} wallpapers")


if __name__ == "__main__":
    main()
