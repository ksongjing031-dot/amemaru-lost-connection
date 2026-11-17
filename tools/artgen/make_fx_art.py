"""AMEMARU: LOST CONNECTION のスキルの演出の仮素材（テクスチャ）を作る。

使い方: python3 tools/artgen/make_fx_art.py <assets/amemaru のフォルダ>

作るもの（どれも白と透明度で描く。色はゲームの中で頂点の色をかけて付ける）:
  textures/fx/glow.png           やわらかな光の玉（64x64）
  textures/fx/ring.png           細い光の輪（128x128）。波紋
  textures/fx/wall.png           下が明るく上へ消える縦のグラデーション（64x64。横にくり返す）。波紋の壁・光の柱
  textures/fx/beam.png           光の帯（64x16。v の真ん中が明るい）。糸・軌跡
  textures/fx/lightning.png      芯の細い光の帯（64x16）。稲妻
  textures/fx/chain.png          鎖の輪（64x16。横にくり返す）。縛鎖
  textures/fx/slash.png          斬撃の弧（128x32。v=0 が外側の刃先、u=1 が振り終わり）
  textures/fx/sigil_*.png        魔法陣（256x256）：arcane（詠唱）・frost（氷）・star（星）・oath（誓い）・thunder（雷）
  textures/fx/dome.png           六角の網目（128x64。横にくり返す）。障壁・ドーム
  textures/fx/ice.png            氷の面（32x32。半透明）
  textures/fx/star.png           4つのとがりの星の光（64x64）
  textures/fx/snowflake.png      雪の結晶の印（32x32）
  textures/fx/crack.png          ひびの入った盾の印（32x32）
  textures/fx/shield.png         盾の紋（64x64）
  textures/particle/*.png        パーティクル（16x16）：glow・spark・shard・star・rune_0〜3・snow
  particles/*.json               パーティクルの定義（テクスチャの一覧）

同じ名前・同じ大きさで描き直したファイルを置けば、コードを変えずに差し替えられる（docs/phase2.md の8章）。
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"
SS = 4  # なめらかにするため、4倍の大きさで描いてから縮める


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def from_alpha(alpha, rgb=(255, 255, 255)):
    """透明度の配列（0〜1）から、白い画像を作る。"""
    a = np.clip(alpha, 0.0, 1.0)
    h, w = a.shape
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., 0] = rgb[0]
    out[..., 1] = rgb[1]
    out[..., 2] = rgb[2]
    out[..., 3] = np.round(a * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w]
    return (x + 0.5) / w, (y + 0.5) / h


class Big:
    """4倍の大きさの白い線の画像（縮めて使う）。"""

    def __init__(self, size):
        self.size = size
        self.n = size * SS
        self.img = Image.new("L", (self.n, self.n), 0)
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        """0〜1 の座標を、大きな画像のピクセルへ。"""
        return (x * self.n, y * self.n)

    def circle(self, cx, cy, r, width, value=255):
        n = self.n
        self.d.ellipse([(cx - r) * n, (cy - r) * n, (cx + r) * n, (cy + r) * n], outline=value, width=max(1, int(width * n)))

    def disc(self, cx, cy, r, value=255):
        n = self.n
        self.d.ellipse([(cx - r) * n, (cy - r) * n, (cx + r) * n, (cy + r) * n], fill=value)

    def line(self, pts, width, value=255):
        self.d.line([self.p(x, y) for x, y in pts], fill=value, width=max(1, int(width * self.n)), joint="curve")

    def poly(self, pts, width, value=255, fill=None):
        pts = [self.p(x, y) for x, y in pts]
        if fill is not None:
            self.d.polygon(pts, fill=fill)
        self.d.line(pts + [pts[0]], fill=value, width=max(1, int(width * self.n)), joint="curve")

    def result(self):
        small = self.img.resize((self.size, self.size), Image.LANCZOS)
        return np.asarray(small, dtype=np.float32) / 255.0


def soft_glow(alpha, radius):
    """線のまわりに、うすい光をにじませる。"""
    from PIL import ImageFilter
    img = Image.fromarray(np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8), "L")
    blur = np.asarray(img.filter(ImageFilter.GaussianBlur(radius)), dtype=np.float32) / 255.0
    return np.clip(np.maximum(alpha, blur * 1.6), 0, 1)


def polar(cx, cy, r, angle):
    return (cx + math.cos(angle) * r, cy + math.sin(angle) * r)


def rune_glyph(big, cx, cy, size, seed, width):
    """3x3 の点をつないだ、このModの紋字（実在の文字ではない）。"""
    rnd = random.Random(seed)
    pts = [(cx + (i % 3 - 1) * size, cy + (i // 3 - 1) * size) for i in range(9)]
    strokes = rnd.randint(2, 4)
    used = rnd.sample(range(9), 4)
    for s in range(strokes):
        a, b = rnd.sample(used + [4], 2)
        big.line([pts[a], pts[b]], width)
    if rnd.random() < 0.5:
        big.disc(pts[used[0]][0], pts[used[0]][1], width * 0.9)


# ---------------------------------------------------------------- 形のテクスチャ
def make_glow():
    u, v = grid(64, 64)
    r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) / 0.5
    a = np.clip(1.0 - r, 0, 1) ** 2.2
    return save(from_alpha(a), "textures/fx/glow.png")


def make_ring():
    u, v = grid(128, 128)
    r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) / 0.5
    a = np.exp(-((r - 0.9) / 0.035) ** 2) + 0.35 * np.exp(-((r - 0.86) / 0.09) ** 2) + 0.12 * np.clip(r / 0.9, 0, 1) ** 6 * (r < 0.9)
    return save(from_alpha(a), "textures/fx/ring.png")


def make_wall():
    u, v = grid(64, 64)
    # v=0（上）で0、v=1（下）で1。縦のすじ（横にくり返せる周期）
    a = v ** 1.7 * (0.72 + 0.18 * np.sin(u * 2 * math.pi * 4) + 0.1 * np.sin(u * 2 * math.pi * 9 + 1.3))
    a += 0.35 * np.exp(-((v - 0.97) / 0.04) ** 2)
    return save(from_alpha(a), "textures/fx/wall.png")


def make_beam():
    u, v = grid(64, 16)
    a = np.exp(-((v - 0.5) / 0.2) ** 2) * (0.85 + 0.15 * np.sin(u * 2 * math.pi * 2))
    return save(from_alpha(a), "textures/fx/beam.png")


def make_lightning():
    u, v = grid(64, 16)
    a = np.exp(-((v - 0.5) / 0.08) ** 2) + 0.4 * np.exp(-((v - 0.5) / 0.28) ** 2)
    return save(from_alpha(a), "textures/fx/lightning.png")


def make_chain():
    w, h = 64, 16
    big = Image.new("L", (w * SS, h * SS), 0)
    d = ImageDraw.Draw(big)
    # 正面から見た輪（だ円）と、横から見た輪（棒）を交互に
    d.ellipse([3 * SS, 2 * SS, 29 * SS, 14 * SS], outline=255, width=3 * SS)
    d.rounded_rectangle([27 * SS, 6 * SS, 61 * SS, 10 * SS], radius=2 * SS, fill=200)
    d.line([30 * SS, 7 * SS, 58 * SS, 7 * SS], fill=255, width=SS)
    small = big.resize((w, h), Image.LANCZOS)
    a = np.asarray(small, dtype=np.float32) / 255.0
    return save(from_alpha(soft_glow(a, 1.0) * 0.95), "textures/fx/chain.png")


def make_slash():
    u, v = grid(128, 32)
    edge = np.exp(-((v - 0.12) / 0.06) ** 2) + 0.55 * np.exp(-((v - 0.32) / 0.22) ** 2) * (1 - v)
    ramp = np.clip(u / 0.85, 0, 1) ** 1.4
    tip = np.where(u > 0.93, np.clip((1 - u) / 0.07, 0, 1), 1.0)
    a = edge * ramp * tip
    return save(from_alpha(a), "textures/fx/slash.png")


def make_star():
    u, v = grid(64, 64)
    x, y = u - 0.5, v - 0.5
    r = np.sqrt(x ** 2 + y ** 2)
    streak_h = np.exp(-(y / 0.018) ** 2) * np.clip(1 - np.abs(x) / 0.5, 0, 1) ** 1.5
    streak_v = np.exp(-(x / 0.018) ** 2) * np.clip(1 - np.abs(y) / 0.5, 0, 1) ** 1.5
    core = np.exp(-(r / 0.09) ** 2)
    halo = 0.35 * np.exp(-(r / 0.22) ** 2)
    a = np.maximum(np.maximum(streak_h, streak_v), core) + halo
    return save(from_alpha(a), "textures/fx/star.png")


def snowflake(big, cx, cy, r, width, branches=True):
    for i in range(6):
        ang = i * math.pi / 3 - math.pi / 2
        end = polar(cx, cy, r, ang)
        big.line([(cx, cy), end], width)
        if branches:
            for t in (0.45, 0.72):
                base = polar(cx, cy, r * t, ang)
                for side in (-1, 1):
                    big.line([base, polar(base[0], base[1], r * 0.28 * (1.1 - t * 0.5), ang + side * 0.8)], width * 0.8)


def make_snowflake():
    big = Big(32)
    snowflake(big, 0.5, 0.5, 0.42, 0.07)
    a = big.result()
    return save(from_alpha(soft_glow(a, 0.8)), "textures/fx/snowflake.png")


def shield_points(cx, top, w, h):
    return [(cx - w / 2, top), (cx + w / 2, top), (cx + w / 2, top + h * 0.5), (cx, top + h), (cx - w / 2, top + h * 0.5)]


def make_crack():
    big = Big(32)
    big.poly(shield_points(0.5, 0.1, 0.7, 0.8), 0.07)
    big.line([(0.52, 0.12), (0.42, 0.36), (0.6, 0.5), (0.44, 0.72), (0.5, 0.88)], 0.07)
    big.line([(0.6, 0.5), (0.78, 0.44)], 0.05)
    a = big.result()
    return save(from_alpha(soft_glow(a, 0.8)), "textures/fx/crack.png")


def make_shield():
    big = Big(64)
    big.poly(shield_points(0.5, 0.12, 0.62, 0.76), 0.05)
    big.poly(shield_points(0.5, 0.2, 0.46, 0.58), 0.025)
    big.line([(0.5, 0.24), (0.5, 0.72)], 0.04)
    big.line([(0.32, 0.4), (0.68, 0.4)], 0.04)
    a = big.result()
    return save(from_alpha(soft_glow(a, 2.0)), "textures/fx/shield.png")


def make_ice():
    rnd = random.Random(21)
    u, v = grid(32, 32)
    base = 0.45 + 0.1 * np.sin((u + v) * 9.0) + 0.08 * np.sin(u * 23.0 - v * 7.0)
    band = 0.35 * np.exp(-(((u - v) - 0.15) / 0.08) ** 2)
    a = base + band
    rgb = np.zeros((32, 32, 4), dtype=np.float32)
    rgb[..., 0] = 0.82 + 0.18 * band
    rgb[..., 1] = 0.94 + 0.06 * band
    rgb[..., 2] = 1.0
    rgb[..., 3] = np.clip(a, 0, 1)
    img = Image.fromarray(np.round(rgb * 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    for _ in range(4):
        x, y = rnd.randint(2, 29), rnd.randint(2, 29)
        pts = [(x, y)]
        for _ in range(3):
            x += rnd.randint(-6, 6)
            y += rnd.randint(-6, 6)
            pts.append((x, y))
        d.line(pts, fill=(255, 255, 255, 230), width=1)
    d.rectangle([0, 0, 31, 31], outline=(255, 255, 255, 235))
    return save(img, "textures/fx/ice.png")


def make_dome():
    w, h = 128, 64
    big = Image.new("L", (w * SS, h * SS), 0)
    d = ImageDraw.Draw(big)
    size = 16 * SS  # 六角の半径
    hw = math.sqrt(3) * size
    rows = int(h * SS / (1.5 * size)) + 2
    cols = int(w * SS / hw) + 2
    for row in range(-1, rows):
        for col in range(-1, cols + 1):
            cx = col * hw + (hw / 2 if row % 2 else 0)
            cy = row * 1.5 * size
            pts = [(cx + math.cos(math.pi / 6 + k * math.pi / 3) * size, cy + math.sin(math.pi / 6 + k * math.pi / 3) * size)
                   for k in range(6)]
            d.line(pts + [pts[0]], fill=255, width=2 * SS)
    small = big.resize((w, h), Image.LANCZOS)
    a = np.asarray(small, dtype=np.float32) / 255.0
    a = soft_glow(a, 1.2) * 0.9 + 0.1
    return save(from_alpha(a), "textures/fx/dome.png")


# ---------------------------------------------------------------- 魔法陣（256x256）
def sigil_frame(big, seed, runes=16, inner=0.66):
    big.circle(0.5, 0.5, 0.47, 0.012)
    big.circle(0.5, 0.5, 0.43, 0.006)
    for i in range(runes):
        ang = i * 2 * math.pi / runes
        cx, cy = polar(0.5, 0.5, 0.45, ang)
        rune_glyph(big, cx, cy, 0.009, seed + i, 0.004)
    big.circle(0.5, 0.5, inner / 2, 0.008)


def finish_sigil(big, name):
    a = big.result()
    a = soft_glow(a, 2.5)
    return save(from_alpha(a), "textures/fx/%s.png" % name)


def make_sigil_arcane():
    big = Big(256)
    sigil_frame(big, 100)
    for k in range(2):
        pts = [polar(0.5, 0.5, 0.32, -math.pi / 2 + k * math.pi + i * 2 * math.pi / 3) for i in range(3)]
        big.poly(pts, 0.007)
    big.circle(0.5, 0.5, 0.16, 0.006)
    for i in range(6):
        cx, cy = polar(0.5, 0.5, 0.32, -math.pi / 2 + i * math.pi / 3)
        big.disc(cx, cy, 0.012)
    big.disc(0.5, 0.5, 0.02)
    return finish_sigil(big, "sigil_arcane")


def make_sigil_frost():
    big = Big(256)
    sigil_frame(big, 200)
    hexagon = [polar(0.5, 0.5, 0.3, i * math.pi / 3) for i in range(6)]
    big.poly(hexagon, 0.006)
    snowflake(big, 0.5, 0.5, 0.29, 0.01)
    for i in range(6):
        cx, cy = polar(0.5, 0.5, 0.385, i * math.pi / 3 + math.pi / 6)
        big.poly([polar(cx, cy, 0.03, i * math.pi / 3 + math.pi / 6 + k * math.pi / 2) for k in range(4)], 0.005)
    return finish_sigil(big, "sigil_frost")


def make_sigil_star():
    big = Big(256)
    sigil_frame(big, 300)
    pts = [polar(0.5, 0.5, 0.33 if i % 2 == 0 else 0.14, -math.pi / 2 + i * math.pi / 8) for i in range(16)]
    big.poly(pts, 0.007)
    for i in range(8):
        cx, cy = polar(0.5, 0.5, 0.385, -math.pi / 2 + i * math.pi / 4 + math.pi / 8)
        pts = [polar(cx, cy, 0.022 if k % 2 == 0 else 0.009, -math.pi / 2 + k * math.pi / 4) for k in range(8)]
        big.poly(pts, 0.004, fill=255)
    big.disc(0.5, 0.5, 0.03)
    return finish_sigil(big, "sigil_star")


def make_sigil_oath():
    big = Big(256)
    sigil_frame(big, 400, runes=12)
    big.poly(shield_points(0.5, 0.3, 0.24, 0.36), 0.009)
    big.line([(0.5, 0.33), (0.5, 0.62)], 0.007)
    big.line([(0.41, 0.42), (0.59, 0.42)], 0.007)
    for i in range(4):
        ang = i * math.pi / 2 + math.pi / 4
        a = polar(0.5, 0.5, 0.2, ang)
        b = polar(0.5, 0.5, 0.33, ang)
        big.line([a, b], 0.008)
        big.line([polar(b[0], b[1], 0.03, ang + math.pi / 2), polar(b[0], b[1], 0.03, ang - math.pi / 2)], 0.006)
    return finish_sigil(big, "sigil_oath")


def make_sigil_thunder():
    big = Big(256)
    sigil_frame(big, 500, runes=10)
    zig = []
    for i in range(37):
        ang = i * 2 * math.pi / 36
        zig.append(polar(0.5, 0.5, 0.3 if i % 2 == 0 else 0.26, ang))
    big.line(zig, 0.006)
    pts = [polar(0.5, 0.5, 0.22, -math.pi / 2 + i * 2 * math.pi / 3) for i in range(3)]
    big.poly(pts, 0.007)
    big.line([(0.53, 0.36), (0.46, 0.5), (0.54, 0.5), (0.47, 0.64)], 0.012)
    return finish_sigil(big, "sigil_thunder")


# ---------------------------------------------------------------- パーティクル（16x16）
def particle_glow():
    u, v = grid(16, 16)
    r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2) / 0.5
    return save(from_alpha(np.clip(1 - r, 0, 1) ** 1.8), "textures/particle/glow.png")


def particle_spark():
    u, v = grid(16, 16)
    a = np.exp(-((v - 0.5) / 0.14) ** 2) * u ** 1.4
    return save(from_alpha(a), "textures/particle/spark.png")


def particle_shard():
    img = Image.new("RGBA", (16 * SS, 16 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = SS
    d.polygon([(8 * s, 1 * s), (13 * s, 6 * s), (10 * s, 15 * s), (4 * s, 11 * s), (3 * s, 5 * s)], fill=(225, 245, 255, 235))
    d.polygon([(8 * s, 1 * s), (13 * s, 6 * s), (8 * s, 8 * s)], fill=(255, 255, 255, 255))
    d.polygon([(4 * s, 11 * s), (8 * s, 8 * s), (10 * s, 15 * s)], fill=(190, 225, 245, 230))
    small = img.resize((16, 16), Image.LANCZOS)
    return save(small, "textures/particle/shard.png")


def particle_star():
    u, v = grid(16, 16)
    x, y = u - 0.5, v - 0.5
    r = np.sqrt(x ** 2 + y ** 2)
    a = np.maximum(np.exp(-(y / 0.06) ** 2) * np.clip(1 - np.abs(x) / 0.5, 0, 1) ** 1.2,
                   np.exp(-(x / 0.06) ** 2) * np.clip(1 - np.abs(y) / 0.5, 0, 1) ** 1.2)
    a = np.maximum(a, np.exp(-(r / 0.14) ** 2))
    return save(from_alpha(a), "textures/particle/star.png")


def particle_runes():
    out = []
    for i in range(4):
        big = Big(16)
        rune_glyph(big, 0.5, 0.5, 0.3, 900 + i * 7, 0.11)
        a = big.result()
        out.append(save(from_alpha(soft_glow(a, 0.6)), "textures/particle/rune_%d.png" % i))
    return out


def particle_snow():
    big = Big(16)
    snowflake(big, 0.5, 0.5, 0.42, 0.1, branches=False)
    a = big.result()
    return save(from_alpha(soft_glow(a, 0.5)), "textures/particle/snow.png")


def particle_defs():
    out = []
    defs = {
        "glow": ["amemaru:glow"],
        "spark": ["amemaru:spark"],
        "shard": ["amemaru:shard"],
        "star": ["amemaru:star"],
        "rune": ["amemaru:rune_%d" % i for i in range(4)],
        "snow": ["amemaru:snow"],
    }
    for name, textures in defs.items():
        path = os.path.join(ROOT, "particles", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"textures": textures}, f, indent=2)
            f.write("\n")
        out.append(path)
    return out


if __name__ == "__main__":
    files = [make_glow(), make_ring(), make_wall(), make_beam(), make_lightning(), make_chain(), make_slash(), make_star(),
             make_snowflake(), make_crack(), make_shield(), make_ice(), make_dome(), make_sigil_arcane(), make_sigil_frost(),
             make_sigil_star(), make_sigil_oath(), make_sigil_thunder(), particle_glow(), particle_spark(), particle_shard(),
             particle_star(), particle_snow()]
    files += particle_runes()
    files += particle_defs()
    for f in files:
        print("wrote", f)
