"""Phase 7 その2：魚図鑑の40種の持ち物の絵（32×32）を描く。

使い方：python3 tools/artgen/make_fish.py [--preview 出力.png]

形と色と模様は tools/datagen/build_fishing.py の表（FISH）から読む。魚は左下（尾）から右上（頭）へ斜めに描き、
背は濃く、腹は明るく、輪郭は濃い色で縁どる（ほかの持ち物の絵と同じ見え方。tools/artgen/enhance_icons.py）。
記憶魚と湖の主は、光の粒（白い点）をまわりに散らす。メダカのような小魚は、2〜3匹の群れで描く。
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "datagen"))
from build_fishing import FISH  # noqa: E402

OUT = ROOT / "src/main/resources/assets/amemaru/textures/item/fish"
S = 32
ANGLE = math.radians(-32)          # 尾（左下）から頭（右上）への傾き（画面の y は下向き）


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (255,)


class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        self.kind = [[None] * S for _ in range(S)]     # 塗った部品（輪郭を描くため）

    def put(self, x, y, c, kind="body"):
        if 0 <= x < S and 0 <= y < S and c is not None:
            self.img.putpixel((x, y), c)
            self.kind[y][x] = kind

    def get(self, x, y):
        return self.img.getpixel((x, y))

    def outline(self, color):
        """塗った所のまわり（上下左右）に、輪郭の色を置く"""
        add = []
        for y in range(S):
            for x in range(S):
                if self.kind[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and self.kind[ny][nx] not in (None, "glint", "outline"):
                        add.append((x, y))
                        break
        for x, y in add:
            self.put(x, y, color, "outline")


def hash2(x, y, k=0):
    """画素ごとの決まった乱数（0〜1）。x・y の一次式の余りでは線の模様になってしまうため、混ぜてから使う"""
    h = (x * 374761393 + y * 668265263 + k * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0


def to_fish(x, y, cx, cy, angle):
    """画面の点 → 魚の座標（u：尾→頭の向き、v：背→腹の向き）"""
    dx, dy = x + 0.5 - cx, y + 0.5 - cy
    ca, sa = math.cos(angle), math.sin(angle)
    u = dx * ca + dy * sa
    v = -dx * sa + dy * ca
    return u, v


# 形ごとの体の決まり：(長さ, 太さ, 太い所の位置, 頭の丸み, 尾の付け根の細さ, 尾びれの大きさ, 背びれ, 尻びれ)
SHAPES = {
    "slim": (25, 4.6, 0.58, 0.85, 0.30, 5.5, (0.45, 0.62, 2.2), (0.30, 0.42, 1.6)),
    "round": (22, 6.6, 0.55, 0.75, 0.34, 5.5, (0.38, 0.66, 2.6), (0.28, 0.42, 2.0)),
    "tiny": (11, 2.4, 0.58, 0.9, 0.35, 2.8, (0.45, 0.60, 1.0), (0.32, 0.45, 0.8)),
    "long": (27, 2.8, 0.55, 0.9, 0.55, 3.5, (0.20, 0.80, 1.0), (0.15, 0.45, 0.8)),
    "eel": (29, 2.6, 0.62, 0.95, 0.70, 0.0, (0.05, 0.85, 1.0), (0.05, 0.55, 0.8)),
    "pike": (28, 3.8, 0.50, 0.60, 0.32, 5.0, (0.12, 0.28, 2.0), (0.14, 0.28, 1.6)),
    "goby": (20, 4.6, 0.75, 1.0, 0.40, 4.0, (0.40, 0.70, 2.4), (0.30, 0.48, 1.4)),
    "catfish": (26, 4.8, 0.70, 1.0, 0.30, 5.0, (0.55, 0.65, 2.0), (0.10, 0.45, 1.2)),
    "cod": (25, 5.2, 0.60, 0.85, 0.28, 5.0, (0.30, 0.75, 2.4), (0.25, 0.50, 1.8)),
    "salmon": (26, 5.0, 0.55, 0.75, 0.28, 6.0, (0.48, 0.62, 2.6), (0.30, 0.40, 1.6)),
    "sturgeon": (29, 4.4, 0.55, 0.45, 0.25, 6.5, (0.25, 0.32, 1.8), (0.25, 0.32, 1.4)),
    "koi": (24, 6.0, 0.55, 0.80, 0.30, 6.0, (0.30, 0.68, 2.4), (0.28, 0.40, 1.8)),
    "perch": (22, 5.8, 0.55, 0.75, 0.30, 5.0, (0.32, 0.70, 3.2), (0.28, 0.42, 2.0)),
    "arowana": (28, 4.6, 0.45, 0.60, 0.40, 5.0, (0.10, 0.30, 2.4), (0.10, 0.32, 2.4)),
    "gourami": (19, 7.0, 0.55, 0.80, 0.35, 5.0, (0.30, 0.65, 2.6), (0.15, 0.62, 2.6)),
    "lord": (31, 7.6, 0.55, 0.70, 0.30, 7.0, (0.35, 0.70, 2.6), (0.25, 0.45, 2.0)),
}


def half_height(t, shape):
    """尾（t=0）から頭（t=1）までの体の半分の太さ（0〜1の割合）"""
    _, _, peak, head_round, tail_thin, _, _, _ = shape
    if t < 0 or t > 1:
        return 0.0
    if t <= peak:
        s = t / peak
        return tail_thin + (1.0 - tail_thin) * math.sin(s * math.pi / 2) ** 1.4
    s = (t - peak) / (1.0 - peak)
    return max(0.0, math.cos(s * math.pi / 2) ** head_round)


def draw_fish(cv, spec, cx, cy, scale=1.0, angle=ANGLE, seed=0, glow=False):
    fid, name, chapter, stars, spots, lo, hi, price, diff, shape_name, colors, pattern = spec[:12]
    body, back, belly, fin, accent, light = colors
    body, back, belly, fin = hexc(body), hexc(back), hexc(belly), hexc(fin)
    accent = hexc(accent)
    light = hexc(light) if light else None
    shape = SHAPES[shape_name]
    length, thick, peak, _, _, tail_size, dorsal, anal = shape
    length *= scale
    thick *= scale
    tail_size *= scale
    rng = random.Random(seed)
    half = length / 2.0
    for y in range(S):
        for x in range(S):
            u, v = to_fish(x, y, cx, cy, angle)
            t = (u + half) / length                    # 0＝尾の付け根、1＝鼻先
            # うなぎは体をうねらせる
            if shape_name == "eel":
                v -= math.sin(t * math.pi * 2.0) * 1.6 * scale
            hh = half_height(t, shape) * thick
            inside = abs(v) <= hh and 0 <= t <= 1
            if inside:
                # 背（v<0）は濃く、腹（v>0）は明るく
                r = v / max(hh, 0.01)
                if r < -0.35:
                    c = mix(back, body, (r + 1.0) / 0.65 * 0.8)
                elif r > 0.35:
                    c = mix(body, belly, (r - 0.35) / 0.65)
                else:
                    c = body
                c = apply_pattern(c, pattern, t, r, u, v, x, y, accent, light, body, back, rng, shape_name, chapter)
                # 背のすじの光（上の縁の少し内側）
                if -0.75 < r < -0.45 and 0.2 < t < 0.85 and pattern not in ("plates",):
                    c = mix(c, (255, 255, 255, 255), 0.18)
                cv.put(x, y, c)
                continue
            # 尾びれ（付け根から後ろへ、ふたまた）
            if tail_size > 0 and -tail_size / length <= t < 0.02:
                d = -t * length                           # 付け根からの距離
                spread = 1.0 * scale + d * 1.05
                # 先の半分を V にえぐる（はぜ・なまずは丸い尾）
                notch = (d - tail_size * 0.45) * 1.1 if shape_name not in ("goby", "catfish") else -1
                if abs(v) <= spread and abs(v) >= notch:
                    c = shade(fin, 0.95 if abs(v) > spread * 0.6 else 1.08)
                    if pattern == "tail":
                        c = hexc(colors[4]) if (int(d) + int(abs(v))) % 2 == 0 else fin
                    cv.put(x, y, c, "fin")
                    continue
            # 背びれ（背の上）
            a, b, hgt = dorsal
            if a <= t <= b and v < 0:
                top = half_height(t, shape) * thick
                k = (t - a) / max(b - a, 0.01)
                fin_h = hgt * scale * (math.sin(k * math.pi) ** 0.6 if shape_name != "perch" else (1.2 - k * 0.6))
                if top < -v <= top + fin_h:
                    c = shade(fin, 1.0 - 0.15 * ((-v - top) / max(fin_h, 0.1)))
                    if shape_name == "perch" and int(u) % 2 == 0:
                        c = shade(fin, 0.75)
                    if pattern == "plates":
                        c = hexc(colors[4])
                    cv.put(x, y, c, "fin")
                    continue
            # 尻びれ・腹びれ（腹の下）
            a, b, hgt = anal
            if a <= t <= b and v > 0:
                bot = half_height(t, shape) * thick
                k = (t - a) / max(b - a, 0.01)
                fin_h = hgt * scale * math.sin(k * math.pi) ** 0.6
                if shape_name == "gourami":
                    fin_h = hgt * scale * (0.6 + k)
                if bot < v <= bot + fin_h:
                    cv.put(x, y, shade(fin, 0.95), "fin")
                    continue
    # 目（鼻先の少し後ろ、背寄り）
    eye_t = 0.86 if shape_name not in ("goby", "catfish") else 0.82
    if shape_name == "lord":
        eye_t = 0.84
    eu = eye_t * length - half
    ev = -half_height(eye_t, shape) * thick * 0.25
    ex, ey = from_fish(eu, ev, cx, cy, angle)
    cv.put(ex, ey, hexc("#141418"), "eye")
    if shape_name == "lord":
        cv.put(ex + 1, ey, hexc("#C8F8FF"), "eye")      # 主の目は、淡く光る
    # なまずのひげ・こいのひげ
    if shape_name in ("catfish", "koi", "sturgeon", "lord"):
        mu = 0.99 * length - half
        mx, my = from_fish(mu, 0.8 * scale, cx, cy, angle)
        whisk = hexc(colors[3])
        n = 5 if shape_name == "catfish" else 3
        for i in range(1, n + 1):
            wx, wy = from_fish(mu + i * 0.7, 0.8 * scale + i * 0.9, cx, cy, angle)
            cv.put(wx, wy, shade(whisk, 0.85), "fin")
    # 光の粒（記憶魚・湖の主）
    if glow and light:
        for _ in range(7 if shape_name == "lord" else 5):
            gx, gy = rng.randrange(1, S - 1), rng.randrange(1, S - 1)
            if cv.kind[gy][gx] is None:
                cv.put(gx, gy, light, "glint")


def from_fish(u, v, cx, cy, angle):
    ca, sa = math.cos(angle), math.sin(angle)
    dx = u * ca - v * sa
    dy = u * sa + v * ca
    return int(math.floor(cx + dx)), int(math.floor(cy + dy))


def apply_pattern(c, pattern, t, r, u, v, x, y, accent, light, body, back, rng, shape_name, chapter):
    if pattern == "dots":
        if hash2(x, y, 1) < 0.10 and r < 0.2:
            return shade(back, 0.9)
    elif pattern == "stripe":
        if abs(r) < 0.16 and 0.08 < t < 0.92:
            return accent
    elif pattern == "belly_glow":
        if r > 0.2:
            return mix(c, light, 0.75)
    elif pattern == "parr":
        if abs(r) < 0.45 and int((t * 7.0) % 1.0 * 10) < 4 and 0.15 < t < 0.8:
            return mix(c, accent, 0.55)
        if r < -0.3 and hash2(x, y, 2) < 0.15:
            return light
    elif pattern == "spots":
        if hash2(x, y, 3) < 0.13 and 0.1 < t < 0.9:
            return accent
    elif pattern == "scales":
        if ((x + y) % 3 == 0 or (x - y) % 3 == 0) and abs(r) < 0.8:
            return mix(c, accent, 0.35)
    elif pattern == "gradient":
        return mix(mix(body, accent, 1.0 - t), c, 0.35)
    elif pattern == "bones":
        if abs(r) < 0.12 or ((int(u * 1.3) % 2 == 0) and abs(r) < 0.55 and 0.15 < t < 0.85):
            return mix(c, accent, 0.6)
    elif pattern == "patches":
        n = math.sin(u * 0.9 + 1.3) + math.cos(v * 1.4 + u * 0.4)
        if n > 0.6:
            return accent
    elif pattern == "bars":
        if int(t * 12) % 3 == 0 and r < 0.5 and 0.2 < t < 0.85:
            return accent
    elif pattern == "glow_dots":
        if hash2(x, y, 4) < 0.12:
            return light
    elif pattern == "rainbow":
        hues = [hexc("#E86A6A"), hexc("#E8C86A"), hexc("#6AE88A"), hexc("#6AA8E8"), hexc("#A86AE8")]
        return mix(c, hues[int((t * 5 + r * 1.5) % 5)], 0.55)
    elif pattern == "ember":
        if hash2(x, y, 5) < 0.12 and r < 0.3:
            return accent
    elif pattern == "stars":
        if hash2(x, y, 6) < 0.11:
            return accent
    elif pattern == "zigzag":
        if abs(r - 0.25 * math.sin(t * 18)) < 0.14 and 0.1 < t < 0.9:
            return accent
    elif pattern == "streaks":
        if abs((r * 3 + t * 6) % 2 - 1) < 0.18 and r < 0.4:
            return accent
    elif pattern == "plates":
        if r < -0.55 and int(t * 10) % 2 == 0:
            return accent
    elif pattern == "memory":
        # 体の真ん中に、淡い光の帯と、白いきらめき
        if abs(r) < 0.25:
            return mix(c, light, 0.6)
        if hash2(x, y, 7) < 0.09:
            return accent
    elif pattern == "lord":
        if r < -0.6 and int(t * 9) % 2 == 0 and 0.2 < t < 0.8:
            return light                                   # 背の結晶
        if abs(r) < 0.18 and 0.1 < t < 0.85:
            return mix(c, light, 0.35)
        if hash2(x, y, 8) < 0.07:
            return shade(c, 1.25)
    elif pattern == "fluffy":
        if r > 0.3:
            return mix(c, (255, 255, 255, 255), 0.5)
    return c


# ---------------------------------------------------------------- 形の違う生き物（エイ・エビ・クラゲ）
def draw_ray(cv, spec):
    colors = spec[10]
    body, back, belly, fin, accent, light = [hexc(c) if c else None for c in colors]
    cx, cy = 18.5, 12.5
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            # 上から見た形：横に広い円盤に、とがった翼の先（前は丸く、後ろはしぼむ）
            wing = 12.5 - max(0.0, dy) * 0.55
            front = 7.0 if dy < 0 else 9.0
            if (dx / wing) ** 2 + (dy / front) ** 2 <= 1.0:
                edge = math.sqrt((dx / wing) ** 2 + (dy / front) ** 2)
                c = mix(body, back, min(1.0, edge * 0.9))
                if hash2(x, y, 9) < 0.12 and edge < 0.85:
                    c = accent
                if edge > 0.9:
                    c = shade(back, 0.85)
                cv.put(x, y, c)
    # 尾（左下へ長く。とげの光）
    for i in range(15):
        tx, ty = int(cx - 2 - i * 0.8), int(cy + 8 + i * 0.75)
        cv.put(tx, ty, shade(back, 0.9), "fin")
        if i % 5 == 2:
            cv.put(tx, ty, accent, "fin")
    # 目（前の縁の近く）
    cv.put(int(cx) - 3, int(cy) - 4, hexc("#141418"), "eye")
    cv.put(int(cx) + 2, int(cy) - 4, hexc("#141418"), "eye")


def draw_shrimp(cv, spec):
    colors = spec[10]
    body, back, belly, fin, accent, light = [hexc(c) if c else None for c in colors]
    # 曲がった体（節を8つ）
    cx, cy = 15.0, 17.0
    for i in range(9):
        a = math.radians(200 - i * 22)
        rad = 7.5
        px, py = cx + math.cos(a) * rad, cy + math.sin(a) * rad * 0.8
        size = 3.2 - i * 0.22
        for y in range(S):
            for x in range(S):
                if (x + 0.5 - px) ** 2 + (y + 0.5 - py) ** 2 <= size ** 2:
                    c = mix(body, back, 0.25 if i % 2 else 0.0)
                    if hash2(x, y, 10) < 0.12:
                        c = light
                    cv.put(x, y, c)
    # 頭と長い手と触角
    hx, hy = cx + math.cos(math.radians(200)) * 7.5, cy + math.sin(math.radians(200)) * 6
    for i in range(12):
        cv.put(int(hx + 1 + i), int(hy - 2 - i * 0.5), shade(fin, 0.9), "fin")      # 長い手
        cv.put(int(hx + 2 + i * 1.1), int(hy - 4 - i * 0.9), shade(fin, 0.8), "fin")  # 触角
    cv.put(int(hx) + 1, int(hy) - 1, hexc("#141418"), "eye")
    # 尾の扇
    tx, ty = cx + math.cos(math.radians(200 - 8 * 22)) * 7.5, cy + math.sin(math.radians(200 - 8 * 22)) * 6
    for dx in range(-2, 3):
        cv.put(int(tx) + dx, int(ty) + 2, shade(fin, 1.0), "fin")


def draw_jelly(cv, spec):
    colors = spec[10]
    body, back, belly, fin, accent, light = [hexc(c) if c else None for c in colors]
    cx, cy = 16.0, 12.0
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if dy <= 1 and dx * dx / 100.0 + dy * dy / 64.0 <= 1.0:
                edge = math.sqrt(dx * dx / 100.0 + dy * dy / 64.0)
                c = mix(belly, body, edge)
                if edge > 0.82:
                    c = shade(back, 1.0)
                if abs(dx) < 3 and -5 < dy < -1:
                    c = mix(c, light, 0.6)
                cv.put(x, y, (c[0], c[1], c[2], 225))
    # 結んだ糸の足
    for k, sx in enumerate((-7, -4, -1, 2, 5, 8)):
        for i in range(16):
            x = int(cx + sx + math.sin(i * 0.6 + k) * 1.2)
            y = int(cy + 2 + i)
            if y < S - 1:
                cv.put(x, y, accent if i % 3 else light, "fin")
    # となりの足と結んだ所
    cv.put(int(cx) - 2, int(cy) + 9, light, "fin")
    cv.put(int(cx) + 3, int(cy) + 11, light, "fin")


def draw(spec):
    cv = Canvas()
    fid, shape_name, stars = spec[0], spec[9], spec[3]
    seed = sum(ord(ch) for ch in fid)
    glow = stars >= 4
    outline = shade(hexc(spec[10][1]), 0.45)
    if shape_name == "ray":
        draw_ray(cv, spec)
    elif shape_name == "shrimp":
        draw_shrimp(cv, spec)
    elif shape_name == "jelly":
        draw_jelly(cv, spec)
    elif shape_name == "tiny":
        # 小魚は3匹の群れ
        for i, (cx, cy) in enumerate(((11.0, 20.0), (19.5, 12.5), (21.0, 23.0))):
            draw_fish(cv, spec, cx, cy, scale=1.0, seed=seed + i, glow=False)
        if spec[11] == "belly_glow" or spec[10][5]:
            rng = random.Random(seed)
            for _ in range(3):
                gx, gy = rng.randrange(2, 30), rng.randrange(2, 30)
                if cv.kind[gy][gx] is None:
                    cv.put(gx, gy, hexc(spec[10][5] or "#FFFFFF"), "glint")
    else:
        draw_fish(cv, spec, 16.0, 16.0, seed=seed, glow=glow)
    cv.outline(outline)
    return cv.img


def preview(images, out):
    cols = 10
    rows = (len(images) + cols - 1) // cols
    cell = S * 4 + 8
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (54, 60, 70, 255))
    for i, (name, img) in enumerate(images):
        x, y = (i % cols) * cell + 4, (i // cols) * cell + 4
        sheet.alpha_composite(img.resize((S * 4, S * 4), Image.NEAREST), (x, y))
    sheet.save(out)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    made = []
    for spec in FISH:
        img = draw(spec)
        img.save(OUT / (spec[0] + ".png"))
        made.append((spec[0], img))
    if "--preview" in sys.argv:
        preview(made, sys.argv[sys.argv.index("--preview") + 1])
    print(f"{len(made)} fish icons → {OUT}")
