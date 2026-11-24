"""スキルツリーの画面の絵（11月24日。星座のようなスキルツリー）。

使い方: python3 tools/artgen/make_tree_art.py   （リポジトリの一番上で）

作るもの：textures/gui/skill_tree.png（128×64）
  1段目（y 0〜15）：ステータスの印 16×16（体力・MP・攻撃・魔力・防御・素早さ・会心・そのほか）
  2段目（y 16〜31）：小さな節の玉 16×16（取れない・取れる・取った・光の輪）
  3段目（y 32〜55）：大きな節の玉 24×24（取れない・取れる・取った）と、核の輪 32×32（x 72〜103、y 32〜63）
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/amemaru/textures/gui/skill_tree.png")

img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def glyph_heart(ox, oy, col):
    pts = [(8, 14), (2, 8), (2, 5), (4, 3), (6, 3), (8, 5), (10, 3), (12, 3), (14, 5), (14, 8)]
    d.polygon([(ox + x, oy + y) for x, y in pts], fill=col)


def glyph_drop(ox, oy, col):
    d.polygon([(ox + 8, oy + 2), (ox + 12, oy + 8), (ox + 4, oy + 8)], fill=col)
    d.ellipse([ox + 4, oy + 6, ox + 12, oy + 14], fill=col)


def glyph_sword(ox, oy, col):
    d.line([(ox + 3, oy + 13), (ox + 13, oy + 3)], fill=col, width=2)
    d.line([(ox + 3, oy + 9), (ox + 7, oy + 13)], fill=col, width=2)
    d.point((ox + 13, oy + 2), fill=col)


def glyph_star(ox, oy, col):
    pts = []
    for i in range(10):
        r = 6.5 if i % 2 == 0 else 2.8
        a = math.radians(-90 + i * 36)
        pts.append((ox + 8 + r * math.cos(a), oy + 8.5 + r * math.sin(a)))
    d.polygon(pts, fill=col)


def glyph_shield(ox, oy, col):
    d.polygon([(ox + 3, oy + 3), (ox + 13, oy + 3), (ox + 13, oy + 8), (ox + 8, oy + 14), (ox + 3, oy + 8)], fill=col)


def glyph_wing(ox, oy, col):
    for i, y in enumerate((5, 8, 11)):
        d.line([(ox + 2 + i * 2, oy + y), (ox + 14, oy + y - 2)], fill=col, width=2)


def glyph_spark(ox, oy, col):
    d.polygon([(ox + 8, oy + 1), (ox + 10, oy + 6), (ox + 15, oy + 8), (ox + 10, oy + 10), (ox + 8, oy + 15), (ox + 6, oy + 10),
               (ox + 1, oy + 8), (ox + 6, oy + 6)], fill=col)


def glyph_ring(ox, oy, col):
    d.ellipse([ox + 3, oy + 3, ox + 13, oy + 13], outline=col, width=2)
    d.ellipse([ox + 7, oy + 7, ox + 9, oy + 9], fill=col)


WHITE = (255, 255, 255, 255)
for i, fn in enumerate([glyph_heart, glyph_drop, glyph_sword, glyph_star, glyph_shield, glyph_wing, glyph_spark, glyph_ring]):
    fn(i * 16, 0, WHITE)


def orb(ox, oy, size, fill, rim, inner_rim=None):
    d.ellipse([ox + 1, oy + 1, ox + size - 2, oy + size - 2], fill=fill, outline=rim, width=2 if size > 16 else 1)
    if inner_rim:
        d.ellipse([ox + 3, oy + 3, ox + size - 4, oy + size - 4], outline=inner_rim, width=1)


orb(0, 16, 16, (22, 26, 40, 255), (70, 80, 104, 255))
orb(16, 16, 16, (30, 44, 70, 255), (150, 200, 255, 255))
orb(32, 16, 16, (58, 46, 22, 255), (255, 214, 120, 255), (255, 240, 190, 160))
for r in range(8):
    a = int(120 * (1 - r / 8))
    d.ellipse([48 + r // 2, 16 + r // 2, 63 - r // 2, 31 - r // 2], outline=(255, 230, 150, a), width=1)

orb(0, 32, 24, (30, 22, 42, 255), (96, 80, 116, 255))
orb(24, 32, 24, (48, 34, 74, 255), (210, 170, 255, 255))
orb(48, 32, 24, (70, 46, 30, 255), (255, 210, 110, 255), (255, 240, 200, 180))
d.ellipse([72, 32, 103, 63], fill=(20, 24, 40, 255), outline=(255, 214, 120, 255), width=2)
d.ellipse([75, 35, 100, 60], outline=(255, 240, 200, 120), width=1)
img.save(OUT)
print(OUT)
