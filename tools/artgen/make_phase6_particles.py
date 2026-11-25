"""Phase 6 その2 の演出用パーティクルの絵（16×16。白で描き、色はパーティクルの色でつける）。

使い方: python3 tools/artgen/make_phase6_particles.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src/main/resources/assets/amemaru/textures/particle")


def save(img, name):
    os.makedirs(ROOT, exist_ok=True)
    img.save(os.path.join(ROOT, name + ".png"))


def feather():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 斜めの羽根：芯と、両側の羽
    for i in range(12):
        t = i / 11
        x = 3 + t * 10
        y = 13 - t * 10
        w = math.sin(t * math.pi) * 3.2
        a = int(255 * (0.55 + 0.45 * math.sin(t * math.pi)))
        d.line([(x - w * 0.7, y - w * 0.7), (x + w * 0.7, y + w * 0.7)], fill=(255, 255, 255, a), width=1)
    d.line([(2, 14), (13, 3)], fill=(255, 255, 255, 255), width=1)
    save(img, "feather")


def petal():
    big = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.ellipse([14, 6, 50, 58], fill=(255, 255, 255, 230))
    d.ellipse([22, 14, 42, 50], fill=(255, 255, 255, 255))
    big = big.rotate(35, resample=Image.BICUBIC)
    img = big.resize((16, 16), Image.LANCZOS)
    save(img, "petal")


def smoke():
    big = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    for cx, cy, r, a in [(32, 34, 22, 150), (22, 28, 14, 120), (42, 26, 13, 120), (30, 22, 12, 110)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, a))
    big = big.filter(ImageFilter.GaussianBlur(4))
    img = big.resize((16, 16), Image.LANCZOS)
    save(img, "smoke")


def bubble():
    big = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.ellipse([8, 8, 56, 56], outline=(255, 255, 255, 255), width=6)
    d.ellipse([12, 12, 52, 52], fill=(255, 255, 255, 60))
    d.ellipse([18, 16, 30, 28], fill=(255, 255, 255, 230))
    img = big.resize((16, 16), Image.LANCZOS)
    save(img, "bubble")


if __name__ == "__main__":
    feather()
    petal()
    smoke()
    bubble()
    print("particles ok")
