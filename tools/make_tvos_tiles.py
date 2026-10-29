#!/usr/bin/env python3
"""
Draws the Apple TV-style tiles the launcher uses for well-known apps: a wide 5:3 card with a
brand-coloured gradient, a soft light from the top left, the app's glyph in the middle with a
drop shadow (the "layer floating above the background" look of tvOS icons), and a gloss on top.

The glyphs in tools/glyphs/ are from Simple Icons (https://simpleicons.org, CC0); Disney+ and
Prime Video are not in that set, so they are lettered. The names and logos belong to their
owners: the tiles only identify the apps on the user's own screen.

    pip install pillow cairosvg numpy
    python3 tools/make_tvos_tiles.py

Writes app/src/main/res/drawable-nodpi/tile_<id>.png (400x240, the Apple TV look) and
tile_<id>_glass.png (the iOS 26 / tvOS 26 "Liquid Glass" look: a frosted, tinted glass logo with a
bright rim over a soft colour wash), tile_<id>_mono.png (minimal: graphite card, white logo) and, with
--sheet FILE (--sheet-style glass|mono for the other sets), a contact sheet.
"""
import io
import sys
from pathlib import Path

import cairosvg
import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
GLYPHS = ROOT / "tools" / "glyphs"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
FONT_BOLD = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"
S = 2                      # drawn at 2x, then scaled down for smooth edges
W, H = 400 * S, 240 * S

# id: (top-left colour, bottom-right colour, glyph file or lettering, glyph colour)
TILES = {
    "youtube":      ("#FF4B3E", "#B00010", "youtube", "#FFFFFF"),
    "youtubemusic": ("#3A3A3E", "#0B0B0D", "youtubemusic", "#FF1F3D"),
    "netflix":      ("#1F1F22", "#000000", "netflix", "#E50914"),
    "googleplay":   ("#FFFFFF", "#D7E1F0", "googleplay", ["#00C6FF", "#00E676", "#FFD600", "#FF3D57"]),
    "spotify":      ("#25E26B", "#0C7436", "spotify", "#0A0A0A"),
    "disneyplus":   ("#2A62F0", "#0A1C68", "Disney+", "#FFFFFF"),
    "primevideo":   ("#1AB8F0", "#0A3552", "prime video", "#FFFFFF"),
    "hbomax":       ("#4A3BFF", "#12084F", "hbomax", "#FFFFFF"),
    "twitch":       ("#B07CFF", "#5223B8", "twitch", "#FFFFFF"),
    "plex":         ("#323236", "#0C0C0E", "plex", "#EBAF00"),
    "kodi":         ("#2FCBF6", "#0A5278", "kodi", "#FFFFFF"),
    "appletv":      ("#34343A", "#000000", "appletv", "#FFFFFF"),
    "crunchyroll":  ("#FF9448", "#CF4F06", "crunchyroll", "#FFFFFF"),
    "vlc":          ("#FFA526", "#D26200", "vlcmediaplayer", "#FFFFFF"),
}


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(c1, c2):
    """Diagonal linear gradient, top left c1 to bottom right c2."""
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    t = (x / W * 0.55 + y / H * 0.45)[..., None]
    a, b = np.array(hex_rgb(c1), np.float32), np.array(hex_rgb(c2), np.float32)
    return Image.fromarray((a + (b - a) * t).astype(np.uint8), "RGB").convert("RGBA")


def radial_light():
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((x - W * 0.18) / (W * 0.8)) ** 2 + ((y + H * 0.1) / (H * 1.1)) ** 2)
    alpha = np.clip(1 - d, 0, 1) ** 1.6 * 0.30 * 255
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    img.putalpha(Image.fromarray(alpha.astype(np.uint8), "L"))
    return img


def glyph_mask(name):
    svg = (GLYPHS / f"{name}.svg").read_bytes()
    target_h = int(H * 0.50)
    png = cairosvg.svg2png(bytestring=svg, output_height=target_h)
    glyph = Image.open(io.BytesIO(png)).convert("RGBA")
    if glyph.width > int(W * 0.62):
        glyph = glyph.resize((int(W * 0.62), int(glyph.height * W * 0.62 / glyph.width)), Image.LANCZOS)
    return glyph.getchannel("A")


def text_mask(text):
    size = int(H * 0.22)
    font = ImageFont.truetype(FONT_BOLD, size)
    while font.getlength(text) > W * 0.78:
        size -= 4
        font = ImageFont.truetype(FONT_BOLD, size)
    mask = Image.new("L", (int(font.getlength(text)) + 8, int(size * 1.4)), 0)
    ImageDraw.Draw(mask).text((4, int(size * 0.1)), text, font=font, fill=255)
    return mask.crop(mask.getbbox())


def paint(mask, colour):
    """A layer shaped like [mask], flat colour or (list of colours) a four-stop diagonal gradient."""
    if isinstance(colour, list):
        n = len(colour)
        y, x = np.mgrid[0:mask.height, 0:mask.width].astype(np.float32)
        t = np.clip((x / mask.width * 0.5 + y / mask.height * 0.5), 0, 0.9999) * (n - 1)
        i = t.astype(int)
        f = (t - i)[..., None]
        cols = np.array([hex_rgb(c) for c in colour], np.float32)
        rgb = cols[i] * (1 - f) + cols[np.minimum(i + 1, n - 1)] * f
        layer = Image.fromarray(rgb.astype(np.uint8), "RGB").convert("RGBA")
    else:
        layer = Image.new("RGBA", mask.size, hex_rgb(colour) + (255,))
    layer.putalpha(mask)
    return layer


def draw_tile(spec):
    top, bottom, glyph, colour = spec
    tile = gradient(top, bottom)
    tile.alpha_composite(radial_light())

    mask = text_mask(glyph) if " " in glyph or not (GLYPHS / f"{glyph}.svg").exists() else glyph_mask(glyph)
    pos = ((W - mask.width) // 2, (H - mask.height) // 2 - int(H * 0.02))

    # The glyph floats above the card: a soft shadow underneath, a hair of highlight on top.
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow_mask = Image.new("L", (W, H), 0)
    shadow_mask.paste(mask, (pos[0], pos[1] + int(H * 0.035)))
    shadow_mask = shadow_mask.filter(ImageFilter.GaussianBlur(H * 0.03)).point(lambda v: int(v * 0.5))
    shadow.putalpha(shadow_mask)
    tile.alpha_composite(shadow)
    layer = paint(mask, colour)
    tile.alpha_composite(layer, pos)

    # Gloss: a pale band over the top of the card, fading out.
    y = np.arange(H, dtype=np.float32)[:, None]
    gloss = np.clip(1 - y / (H * 0.55), 0, 1) ** 1.5 * 0.16 * 255
    g = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    g.putalpha(Image.fromarray(np.repeat(gloss, W, axis=1).astype(np.uint8), "L"))
    tile.alpha_composite(g)
    # And a little darkening along the bottom edge.
    dark = np.clip((y / H - 0.72) / 0.28, 0, 1) * 0.20 * 255
    d = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d.putalpha(Image.fromarray(np.repeat(dark, W, axis=1).astype(np.uint8), "L"))
    tile.alpha_composite(d)
    return tile.resize((W // S, H // S), Image.LANCZOS).convert("RGB")


def lighten(rgb, amount):
    return tuple(int(c + (255 - c) * amount) for c in rgb)


def draw_glass_tile(spec):
    """iOS 26 style: the logo is a piece of frosted glass lying on a soft, bright colour wash."""
    top, bottom, glyph, colour = spec
    flat = colour[0] if isinstance(colour, list) else colour
    r_, g_, b_ = hex_rgb(flat)
    if 0.2126 * r_ + 0.7152 * g_ + 0.0722 * b_ < 60:
        flat = "#FFFFFF"   # a near-black logo would turn the glass murky: use clear white glass

    # A softer, lighter background with a couple of colour blobs, so there is something to refract.
    bg = gradient(top, bottom)
    blobs = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(blobs)
    tint = lighten(hex_rgb(top), 0.35) + (150,)
    deep = hex_rgb(bottom) + (170,)
    draw.ellipse((-W * 0.15, -H * 0.5, W * 0.55, H * 0.7), fill=tint)
    draw.ellipse((W * 0.5, H * 0.35, W * 1.15, H * 1.4), fill=deep)
    bg.alpha_composite(blobs.filter(ImageFilter.GaussianBlur(H * 0.16)))
    bg.alpha_composite(radial_light())

    mask = text_mask(glyph) if " " in glyph or not (GLYPHS / f"{glyph}.svg").exists() else glyph_mask(glyph)
    pos = ((W - mask.width) // 2, (H - mask.height) // 2 - int(H * 0.02))
    full = Image.new("L", (W, H), 0)
    full.paste(mask, pos)

    tile = bg.copy()
    # Soft shadow under the glass.
    sh = full.filter(ImageFilter.GaussianBlur(H * 0.035)).point(lambda v: int(v * 0.45))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow_mask = Image.new("L", (W, H), 0)
    shadow_mask.paste(sh, (0, int(H * 0.04)))
    shadow.putalpha(shadow_mask)
    tile.alpha_composite(shadow)

    # The glass body: the background behind it, blurred and brightened, then tinted with the
    # logo colour and washed with white from above.
    frosted = bg.filter(ImageFilter.GaussianBlur(H * 0.05))
    frosted = Image.eval(frosted.convert("RGB"), lambda v: min(255, int(v * 1.2 + 24))).convert("RGBA")
    tinted = Image.new("RGBA", (W, H), hex_rgb(flat) + (int(255 * 0.42),))
    frosted.alpha_composite(tinted)
    y = np.arange(H, dtype=np.float32)[:, None]
    wash = np.clip(1 - (y - pos[1]) / max(mask.height, 1), 0, 1) * 0.40 * 255
    wash_img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    wash_img.putalpha(Image.fromarray(np.repeat(wash, W, axis=1).astype(np.uint8), "L"))
    frosted.alpha_composite(wash_img)
    tile.paste(frosted, (0, 0), full)

    # Inner shadow along the lower edge and a bright rim that fades from top left to bottom right.
    inner = full.filter(ImageFilter.GaussianBlur(H * 0.02))
    inner_shadow = ImageChops.subtract(full, ImageChops.offset(inner, 0, -int(H * 0.025)))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dark.putalpha(inner_shadow.point(lambda v: int(v * 0.35)))
    tile.alpha_composite(dark)
    r = max(3, int(H * 0.011))
    rim = ImageChops.subtract(full, full.filter(ImageFilter.MinFilter(2 * r + 1)))
    gy, gx = np.mgrid[0:H, 0:W].astype(np.float32)
    fall = np.clip(1.0 - (gx / W * 0.5 + gy / H * 0.5) * 1.25, 0.18, 1.0)
    rim_alpha = (np.asarray(rim, np.float32) * fall).astype(np.uint8)
    rim_img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    rim_img.putalpha(Image.fromarray(rim_alpha, "L"))
    tile.alpha_composite(rim_img)

    # Gloss over the whole tile and a hairline border, like the tvOS 26 tiles.
    gloss = np.clip(1 - y / (H * 0.5), 0, 1) ** 1.6 * 0.20 * 255
    g = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    g.putalpha(Image.fromarray(np.repeat(gloss, W, axis=1).astype(np.uint8), "L"))
    tile.alpha_composite(g)
    ImageDraw.Draw(tile).rounded_rectangle((2, 2, W - 3, H - 3), radius=28 * S // 2, outline=(255, 255, 255, 60), width=S)
    return tile.resize((W // S, H // S), Image.LANCZOS).convert("RGB")


def draw_mono_tile(spec):
    """Minimal: a graphite card, a faint glow in the brand colour, the logo in plain white."""
    top, bottom, glyph, colour = spec
    flat = colour[0] if isinstance(colour, list) else colour
    y = np.arange(H, dtype=np.float32)[:, None]
    shade = np.repeat((0x2A - (0x2A - 0x14) * y / H), W, axis=1)
    rgb = np.stack([shade, shade + 1, shade + 4], axis=-1).astype(np.uint8)
    tile = Image.fromarray(rgb, "RGB").convert("RGBA")

    # A soft pool of the brand colour behind the logo, so each tile is still told apart at a glance.
    gy, gx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((gx - W / 2) / (W * 0.45)) ** 2 + ((gy - H / 2) / (H * 0.7)) ** 2)
    glow_alpha = (np.clip(1 - d, 0, 1) ** 2 * 0.34 * 255).astype(np.uint8)
    glow = Image.new("RGBA", (W, H), hex_rgb(top) + (0,))
    glow.putalpha(Image.fromarray(glow_alpha, "L"))
    tile.alpha_composite(glow)

    mask = text_mask(glyph) if " " in glyph or not (GLYPHS / f"{glyph}.svg").exists() else glyph_mask(glyph)
    pos = ((W - mask.width) // 2, (H - mask.height) // 2)
    tile.alpha_composite(paint(mask, "#FFFFFF"), pos)
    ImageDraw.Draw(tile).rounded_rectangle((2, 2, W - 3, H - 3), radius=28 * S // 2, outline=(255, 255, 255, 36), width=S)
    return tile.resize((W // S, H // S), Image.LANCZOS).convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tiles, glass, mono = {}, {}, {}
    for name, spec in TILES.items():
        tile = draw_tile(spec)
        tile.save(OUT / f"tile_{name}.png", optimize=True)
        tiles[name] = tile
        gl = draw_glass_tile(spec)
        gl.save(OUT / f"tile_{name}_glass.png", optimize=True)
        glass[name] = gl
        mo = draw_mono_tile(spec)
        mo.save(OUT / f"tile_{name}_mono.png", optimize=True)
        mono[name] = mo
        print("wrote", f"tile_{name}.png", f"tile_{name}_glass.png")
    if "--sheet" in sys.argv:
        sheet_path = sys.argv[sys.argv.index("--sheet") + 1]
        style = sys.argv[sys.argv.index("--sheet-style") + 1] if "--sheet-style" in sys.argv else "apple"
        tiles = {"glass": glass, "mono": mono}.get(style, tiles)
        cols, pad = 4, 24
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (400 + pad) + pad, rows * (240 + pad) + pad), (16, 17, 22))
        for i, tile in enumerate(tiles.values()):
            rounded = Image.new("L", tile.size, 0)
            ImageDraw.Draw(rounded).rounded_rectangle((0, 0, tile.width - 1, tile.height - 1), 24, fill=255)
            sheet.paste(tile, (pad + (i % cols) * (400 + pad), pad + (i // cols) * (240 + pad)), rounded)
        sheet.save(sheet_path, quality=92)


if __name__ == "__main__":
    main()
