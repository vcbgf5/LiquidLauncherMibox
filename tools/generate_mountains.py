import numpy as np
from PIL import Image, ImageFilter, ImageDraw
import sys

S = 2                       # supersample
W, H = 1920 * S, 640 * S
OUT = sys.argv[1]

def fbm1d(n, seed, octaves=7, base=3, pers=0.52, ridged=False):
    r = np.random.default_rng(seed)
    out = np.zeros(n); amp = 1.0; tot = 0.0
    for o in range(octaves):
        k = base * 2 ** o + 1
        pts = r.random(k)
        xs = np.linspace(0, k - 1, n)
        i0 = np.floor(xs).astype(int); i1 = np.minimum(i0 + 1, k - 1)
        t = xs - i0; t = (1 - np.cos(t * np.pi)) / 2
        v = pts[i0] * (1 - t) + pts[i1] * t
        if ridged:
            v = 1 - np.abs(2 * v - 1)
        out += amp * v; tot += amp; amp *= pers
    return out / tot

def noise2d(w, h, cells, seed):
    r = np.random.default_rng(seed)
    a = (r.random((cells[1], cells[0])) * 255).astype(np.uint8)
    img = Image.fromarray(a).resize((w, h), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0

def lerp(a, b, t):
    return a + (b - a) * t

xs = np.arange(W)
ys = np.arange(H)[:, None]
Y = np.repeat(ys, W, axis=1).astype(np.float32)

# ---------- sky ----------
sky_stops = [(0.00, (22, 32, 74)), (0.35, (70, 84, 140)), (0.58, (190, 130, 150)),
             (0.74, (255, 168, 118)), (1.00, (255, 214, 160))]
t = (np.arange(H) / (H * 0.80)).clip(0, 1)
sky = np.zeros((H, 3), dtype=np.float32)
for i in range(len(sky_stops) - 1):
    p0, c0 = sky_stops[i]; p1, c1 = sky_stops[i + 1]
    m = (t >= p0) & (t <= p1)
    u = ((t[m] - p0) / (p1 - p0))[:, None]
    sky[m] = lerp(np.array(c0, dtype=np.float32), np.array(c1, dtype=np.float32), u)
img = np.repeat(sky[:, None, :], W, axis=1)

# sun glow
sx, sy = 0.68 * W, 0.66 * H
d = np.sqrt((xs[None, :] - sx) ** 2 + ((Y - sy) * 1.5) ** 2)
glow = np.exp(-(d / (0.22 * W)) ** 1.3)[..., None]
img = img + glow * np.array([255, 200, 140], dtype=np.float32) * 0.55
core = np.exp(-(d / (0.035 * W)) ** 2)[..., None]
img = img + core * np.array([255, 240, 210], dtype=np.float32) * 0.9

# clouds
c = (0.55 * noise2d(W, int(H * 0.7), (7, 3), 1) + 0.3 * noise2d(W, int(H * 0.7), (14, 6), 2)
     + 0.15 * noise2d(W, int(H * 0.7), (30, 10), 3))
c = np.clip((c - 0.48) * 3.2, 0, 1)
cl = np.zeros((H, W), dtype=np.float32); cl[: c.shape[0]] = c
fade = np.clip(1 - np.abs(Y / H - 0.42) / 0.35, 0, 1) ** 1.2
cl = cl * fade
cloud_col = lerp(np.array([200, 150, 175], dtype=np.float32), np.array([255, 190, 140], dtype=np.float32),
                 np.clip(Y / H * 1.4 - 0.2, 0, 1)[..., None])
img = img * (1 - cl[..., None] * 0.55) + cloud_col * cl[..., None] * 0.55

# ---------- mountain layers (far -> near) ----------
haze = np.array([236, 170, 140], dtype=np.float32)
layers = [
    dict(base=0.60, amp=0.34, seed=21, col_dark=(112, 112, 160), col_lit=(255, 200, 160), snow=True,  mist=0.55, tex=0.35),
    dict(base=0.68, amp=0.30, seed=22, col_dark=(84, 86, 132),  col_lit=(246, 170, 130), snow=True,  mist=0.50, tex=0.45),
    dict(base=0.77, amp=0.24, seed=23, col_dark=(56, 60, 102),  col_lit=(226, 140, 112), snow=False, mist=0.45, tex=0.5),
    dict(base=0.87, amp=0.18, seed=24, col_dark=(34, 40, 74),   col_lit=(170, 110, 100), snow=False, mist=0.35, tex=0.55),
    dict(base=0.98, amp=0.12, seed=25, col_dark=(16, 20, 38),   col_lit=(90, 70, 80),    snow=False, mist=0.12, tex=0.4),
]
sun_x = 0.68 * W

for li, L in enumerate(layers):
    ridge = 0.62 * fbm1d(W, L['seed'], octaves=8, base=2, pers=0.5, ridged=True) \
          + 0.38 * fbm1d(W, L['seed'] + 100, octaves=6, base=3, pers=0.5)
    ridge = (ridge - ridge.min()) / (ridge.max() - ridge.min())
    # keep the biggest peaks away from the far-left/right edges a bit
    env = 0.55 + 0.45 * np.sin(np.linspace(0.08, 1.0 - 0.08, W) * np.pi) ** 0.8
    top = (L['base'] - L['amp'] * ridge * env) * H           # y of the ridge line
    slope = np.gradient(np.convolve(top, np.ones(9) / 9, mode='same'))
    slope = np.clip(slope / (np.abs(slope).max() + 1e-6) * 3.0, -1, 1)
    tex_line = fbm1d(W, L['seed'] + 200, octaves=7, base=8, pers=0.6)

    D = Y - top[None, :]                                    # depth below ridge, px
    mask = D >= 0
    shift = np.mod(xs[None, :] - 0.55 * D, W).astype(np.int32)
    sl = slope[shift]
    tx = tex_line[shift]
    lit = np.clip(0.5 + 0.5 * sl, 0, 1)                      # faces toward the sun
    # sun side lights the faces more strongly near the sun
    prox = np.exp(-((xs - sun_x) / (0.45 * W)) ** 2)[None, :]
    lit = np.clip(lit * (0.30 + 0.85 * prox) + (tx - 0.5) * L['tex'] * 1.3, 0, 1)

    cd = np.array(L['col_dark'], dtype=np.float32); cl_ = np.array(L['col_lit'], dtype=np.float32)
    col = lerp(cd, cl_, lit[..., None])

    if L['snow']:
        snow_line = (0.45 + 0.35 * fbm1d(W, L['seed'] + 300, octaves=5, base=6)) * L['amp'] * H
        sd = D / (snow_line[shift] + 1e-6)
        snow_amt = np.clip(1.6 - sd * 1.6, 0, 1) * np.clip(0.75 + (tx - 0.5) * 1.2, 0, 1)
        snow_col = lerp(np.array([160, 175, 215], dtype=np.float32), np.array([255, 236, 214], dtype=np.float32), lit[..., None])
        col = lerp(col, snow_col, snow_amt[..., None] * 0.92)

    # atmospheric perspective + valley mist toward the bottom of each layer
    depth_haze = (1 - li / (len(layers) - 1)) ** 1.1
    mist_h = L['mist'] * H * 0.55
    mist = np.exp(-np.clip(D, 0, None) / (mist_h + 1e-6) * 1.0)       # 1 near ridge, 0 deep
    ridge_haze = 0.20 + 0.55 * depth_haze
    base_mist = np.clip((Y - (L['base'] * H - 0.05 * H)) / (0.18 * H), 0, 1) * 0.55 * (1 - 0.8 * li / (len(layers) - 1))
    fog = np.clip(ridge_haze * 0.35 + base_mist, 0, 0.9)[..., None] if False else None
    hz = np.clip(depth_haze * 0.30 + base_mist * 0.9, 0, 0.9)
    col = lerp(col, haze, hz[..., None])
    img = np.where(mask[..., None], col, img)

    # soft rim light on the ridge line facing the sun
    rim = np.exp(-np.clip(D, 0, None) / (1.6 * S)) * (mask) * np.clip(0.4 + 0.6 * np.clip(-slope, 0, 1), 0, 1)[None, :]
    img = img + rim[..., None] * np.array([255, 215, 170], dtype=np.float32) * 0.25 * (0.4 + depth_haze)

# ---------- foreground pines on the nearest hill ----------
pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
dr = ImageDraw.Draw(pil)
L = layers[-1]
r = np.random.default_rng(5)
ridge = 0.62 * fbm1d(W, L['seed'], octaves=8, base=2, pers=0.5, ridged=True) + 0.38 * fbm1d(W, L['seed'] + 100, octaves=6, base=3, pers=0.5)
ridge = (ridge - ridge.min()) / (ridge.max() - ridge.min())
env = 0.55 + 0.45 * np.sin(np.linspace(0.08, 1.0 - 0.08, W) * np.pi) ** 0.8
top = (L['base'] - L['amp'] * ridge * env) * H
for _ in range(900):
    x = int(r.integers(0, W)); yb = top[x] + r.integers(0, 14 * S)
    hgt = r.integers(12 * S, 36 * S); wd = hgt * 0.34
    shade = int(r.integers(6, 20))
    col = (shade, shade + 4, shade + 12)
    dr.polygon([(x, yb - hgt), (x - wd, yb), (x + wd, yb)], fill=col)
pil = pil.filter(ImageFilter.GaussianBlur(0.8 * S / 2))

arr = np.asarray(pil, dtype=np.float32)
# vignette + grain
vx = (xs[None, :] / W - 0.5) * 2; vy = (Y / H - 0.5) * 2
vig = 1 - 0.22 * np.clip(vx ** 2 * 0.8 + vy ** 2 * 0.6, 0, 1)
arr = arr * vig[..., None]
arr += np.random.default_rng(9).normal(0, 2.2, arr.shape)
res = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).resize((W // S, H // S), Image.LANCZOS)
res.save(OUT, quality=93)
print("saved", res.size)
