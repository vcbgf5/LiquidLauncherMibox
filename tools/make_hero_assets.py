#!/usr/bin/env python3
"""Turn any photo into the two images the home screen needs.

    python3 tools/make_hero_assets.py my_photo.jpg

writes, into app/src/main/res/drawable/:
  bg_hero.jpg       1920x640 hero photo (centre-cropped to 3:1)
  bg_hero_blur.jpg  480x150 blurred copy with the same bottom fade as
                    HeroBanner.kt, drawn behind the dock as its "frosted" backdrop

Needs Pillow and numpy. If you change the gradient stops in HeroBanner.kt,
change STOPS below too.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

SHELF = np.array([5, 6, 8], dtype=np.float32)
# (position, colour, alpha) - mirrors the Brush.verticalGradient in HeroBanner.kt
STOPS = [
    (0.00, np.array([0, 0, 0], dtype=np.float32), 0.00),
    (0.55, np.array([0, 0, 0], dtype=np.float32), 0.05),
    (0.82, SHELF, 0.80),
    (1.00, SHELF, 1.00),
]
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"


def cover(img: Image.Image, w: int, h: int) -> Image.Image:
    scale = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    left, top = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((left, top, left + w, top + h))


def main(src: str) -> None:
    photo = Image.open(src).convert("RGB")
    hero = cover(photo, 1920, 640)
    hero.save(OUT / "bg_hero.jpg", quality=92)

    # The app shows the hero at 3.2:1, i.e. the middle 600 of those 640 rows.
    shown = hero.crop((0, 20, 1920, 620))
    arr = np.asarray(
        shown.resize((480, 150), Image.LANCZOS).filter(ImageFilter.GaussianBlur(7)), dtype=np.float32
    )
    for row in range(arr.shape[0]):
        t = row / (arr.shape[0] - 1)
        for (p0, c0, a0), (p1, c1, a1) in zip(STOPS, STOPS[1:]):
            if p0 <= t <= p1:
                k = (t - p0) / (p1 - p0)
                colour, alpha = c0 * (1 - k) + c1 * k, a0 * (1 - k) + a1 * k
                break
        arr[row] = arr[row] * (1 - alpha) + colour * alpha
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).save(OUT / "bg_hero_blur.jpg", quality=90)
    print("wrote", OUT / "bg_hero.jpg", "and", OUT / "bg_hero_blur.jpg")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
