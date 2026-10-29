#!/usr/bin/env python3
"""Turn any photo into the two images the home screen needs.

    python3 tools/make_hero_assets.py my_photo.jpg

writes, into app/src/main/res/drawable/:
  bg_hero.jpg       1920x1080 photo (centre-cropped to 16:9), shown full screen on the home screen
  bg_hero_blur.jpg  480x270 blurred copy of it, drawn behind the dock as its "frosted glass"
                    backdrop and behind the app grid

Needs Pillow. Pick a photo whose bottom third is fairly dark or calm: the
dock sits there.
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"


def cover(img: Image.Image, w: int, h: int) -> Image.Image:
    scale = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    left, top = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((left, top, left + w, top + h))


def main(src: str) -> None:
    hero = cover(Image.open(src).convert("RGB"), 1920, 1080)
    hero.save(OUT / "bg_hero.jpg", quality=92)
    hero.resize((480, 270), Image.LANCZOS).filter(ImageFilter.GaussianBlur(7)).save(
        OUT / "bg_hero_blur.jpg", quality=90
    )
    print("wrote", OUT / "bg_hero.jpg", "and", OUT / "bg_hero_blur.jpg")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
