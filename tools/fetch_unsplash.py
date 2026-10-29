#!/usr/bin/env python3
"""Download wallpapers from Unsplash into wallpapers/<category>/<photo-id>.jpg.

Runs in GitHub Actions (workflow "Wallpapers"), but works anywhere:

    UNSPLASH_ACCESS_KEY=... python3 tools/fetch_unsplash.py

Categories and their search queries live in tools/wallpaper_categories.json.
Photos are cropped by Unsplash's image service to exactly 1920x1080.

Unsplash's free "demo" API tier allows 50 requests per hour, and each photo
needs one request (the download-tracking call its guidelines require), plus
one search per category. So a full run of 100 photos takes about three runs
an hour apart. The script is safe to repeat: it stops cleanly when its
request budget is used up and the next run carries on where it left off
(search results are cached in wallpapers/.search-cache.json).

Credits (photographer + link) go to wallpapers/credits.json; the app shows
the photographer's name next to the wallpaper, as Unsplash asks.

Environment: UNSPLASH_ACCESS_KEY (required), PER_CATEGORY (default 10),
API_BUDGET (default 45), UNSPLASH_API (override, used by the tests).
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WALL = ROOT / "wallpapers"
API = os.environ.get("UNSPLASH_API", "https://api.unsplash.com").rstrip("/")
KEY = os.environ.get("UNSPLASH_ACCESS_KEY", "")
PER_CATEGORY = int(os.environ.get("PER_CATEGORY", "10"))
BUDGET = int(os.environ.get("API_BUDGET", "45"))
MIN_WIDTH = 2500
IMAGE_PARAMS = "w=1920&h=1080&fit=crop&crop=entropy&q=78&fm=jpg"


class OutOfBudget(Exception):
    pass


used = 0


def api(path, params=None):
    """One call to the Unsplash API; counts against the budget."""
    global used
    if used >= BUDGET:
        raise OutOfBudget()
    used += 1
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(url, headers={"Authorization": f"Client-ID {KEY}", "Accept-Version": "v1"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        if e.code in (403, 429):  # rate limit
            raise OutOfBudget()
        raise


def fetch_image(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "liquid-tv-wallpapers"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    if len(data) < 20_000:
        raise RuntimeError(f"suspiciously small image ({len(data)} bytes)")
    tmp = dest.with_suffix(".part")
    tmp.write_bytes(data)
    tmp.replace(dest)


def load(path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def main():
    if not KEY:
        sys.exit("UNSPLASH_ACCESS_KEY is not set. Add it as a repository secret (see wallpapers/README.md).")
    cats = load(ROOT / "tools/wallpaper_categories.json", [])
    cache_path, credits_path = WALL / ".search-cache.json", WALL / "credits.json"
    cache, credits = load(cache_path, {}), load(credits_path, {})
    added = 0
    try:
        for cat in cats:
            folder = WALL / cat["id"]
            folder.mkdir(parents=True, exist_ok=True)
            have = len(list(folder.glob("*.jpg")))
            if have >= PER_CATEGORY:
                continue
            if cat["id"] not in cache:
                data = api("/search/photos", {"query": cat["query"], "orientation": "landscape", "per_page": 30,
                                              "content_filter": "high", "order_by": "relevant"})
                cache[cat["id"]] = [
                    {"id": p["id"], "width": p.get("width", 0), "raw": p["urls"]["raw"],
                     "download": p["links"]["download_location"], "page": p["links"]["html"],
                     "name": p["user"]["name"], "profile": p["user"]["links"]["html"]}
                    for p in data.get("results", [])
                ]
            for p in cache[cat["id"]]:
                if have >= PER_CATEGORY:
                    break
                dest = folder / f"{p['id']}.jpg"
                if dest.exists():
                    continue
                if p["width"] < MIN_WIDTH:
                    continue
                # Unsplash asks apps to ping this endpoint whenever a photo is downloaded.
                api(urllib.parse.urlparse(p["download"]).path, dict(urllib.parse.parse_qsl(urllib.parse.urlparse(p["download"]).query)))
                sep = "&" if "?" in p["raw"] else "?"
                try:
                    fetch_image(p["raw"] + sep + IMAGE_PARAMS, dest)
                except Exception as e:  # one bad photo shouldn't stop the rest
                    print(f"  skipped {p['id']}: {e}")
                    continue
                credits[f"{cat['id']}/{dest.name}"] = {"name": p["name"], "profile": p["profile"], "page": p["page"]}
                have += 1
                added += 1
                print(f"  {cat['id']}: {dest.name} by {p['name']}")
            print(f"{cat['id']}: {have}/{PER_CATEGORY}")
    except OutOfBudget:
        print("\nUnsplash request limit reached for now. Run again in an hour to continue.")
    finally:
        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
        credits_path.write_text(json.dumps(credits, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    print(f"\nDownloaded {added} new photos, used {used} API requests.")


if __name__ == "__main__":
    main()
