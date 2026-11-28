"""11月29日：キャンディー（しばらくステータスが上がる品）の絵を描く。

使い方: python3 tools/artgen/make_candies.py [--preview 出力.png]

作るもの：
  textures/item/candy_<名前>.png       持ち物の絵（32×32。料理と同じ細かさ）。両はしをねじった包み紙と、まん中の丸い飴。
                                       飴にはしま模様、包み紙は飴の色をうすくした色（すけて見える）
  models/item/candy_<名前>.json
  textures/mob_effect/candy_<種類>.png 効き目の印（18×18。ほかの状態の印と同じ大きさ）。上がるステータスの種類ごとの色
色は Java の ModEffects.candyColor と同じ（効き目の色と、包み紙の色をそろえる）。
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = "src/main/resources/assets/amemaru"

# ステータスの種類ごとの色（ModEffects.candyColor と同じ）
STAT_COLORS = {
    "hp": "#E85A6E", "mp": "#5A8CE8", "attack": "#E8503C", "magic": "#9A6AE8", "defense": "#8C9AB4", "speed": "#4CC8A0",
    "crit": "#F0C040", "crit_damage": "#F08A30", "heal_power": "#F0A0C8", "cooldown_reduction": "#60C8E8", "lost_resist": "#E8E8F4",
}
# 持ち物のキャンディー：名前 → 上がるステータス（data/amemaru/amemaru/consumables/candy_*.json と同じ）
CANDIES = {
    "candy_strength": "attack",
    "candy_wisdom": "magic",
    "candy_iron": "defense",
    "candy_gale": "speed",
    "candy_keen": "crit",
    "candy_vigor": "hp",
    "candy_mercy": "heal_power",
}


def rgb(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (255,)


def shade(c, k):
    """k > 0 で明るく（黄みへ）、k < 0 で暗く（青みへ）。"""
    if k >= 0:
        return mix(c, (255, 250, 228), min(1.0, k))
    return mix(c, (26, 30, 52), min(1.0, -k))


def outline(img, color_of):
    """描いた形のまわりに、となりの色を暗くした1ピクセルのふちをつける（真っ黒にしない）。"""
    src = img.copy()
    sp = src.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if sp[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and sp[nx, ny][3] > 128:
                    img.putpixel((x, y), color_of(sp[nx, ny]))
                    break


def candy(size, base_hex, scale):
    """size×size の絵に、両はしをねじったキャンディーを描く。scale は 32 の絵に対する大きさ（18 の絵は 0.56）。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    base = rgb(base_hex)
    wrap = mix(base, (255, 255, 255), 0.42)
    wrap_dark = shade(base, -0.15)
    cx = cy = (size - 1) / 2.0
    r = 7.0 * scale
    px = img.load()

    # 包み紙（左右の羽）：ねじり目から外へ広がる扇。折り目の線と、明るいふち
    for side in (-1, 1):
        pinch = cx + side * (r + 0.6 * scale)
        tip = cx + side * (cx - 1.5)
        for y in range(size):
            for x in range(size):
                t = (x - pinch) * side
                span = abs(tip - pinch)
                if t < 0 or t > span:
                    continue
                half = (1.6 + (t / span) * 6.4) * scale
                dy = y - cy
                if abs(dy) > half:
                    continue
                # 扇の折り目（ねじり目から放射）
                fold = math.atan2(dy, t + 0.01)
                stripe = math.sin(fold * 7.0) > 0.55
                c = shade(wrap, -0.12) if stripe else wrap
                if abs(dy) > half - 1.0 * scale:
                    c = shade(wrap, 0.25) if dy < 0 else shade(wrap, -0.2)
                if t > span - 1.0 * scale:
                    c = shade(wrap, 0.18)
                px[x, y] = c
        # ねじり目
        for y in range(int(cy - 1.6 * scale), int(cy + 1.6 * scale) + 1):
            x = int(round(pinch))
            if 0 <= x < size:
                px[x, y] = wrap_dark

    # 飴の玉：左上からの光で丸く。しま模様（ななめ）と、つやの点
    for y in range(size):
        for x in range(size):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            light = -(dx + dy) / (2.0 * r)                  # 左上が明るい
            k = 0.35 * light - 0.25 * (d / r) ** 3
            c = shade(base, k) if k >= 0 else shade(base, k)
            band = ((dx - dy) / max(1.0, r * 0.62)) % 2.0
            if 0.0 <= band < 0.55:
                c = mix(c, (255, 252, 246), 0.62)
            px[x, y] = c
    hx, hy = cx - r * 0.42, cy - r * 0.42
    for (ox, oy, t) in ((0, 0, 0.95), (1, 0, 0.6), (0, 1, 0.6)):
        x, y = int(round(hx + ox * scale)), int(round(hy + oy * scale))
        if 0 <= x < size and 0 <= y < size and px[x, y][3]:
            px[x, y] = mix(px[x, y], (255, 255, 255), t)

    outline(img, lambda c: shade(c, -0.55))
    return img


def write_model(name):
    path = os.path.join(ROOT, "models", "item", name + ".json")
    with open(path, "w", encoding="utf-8") as fp:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, fp, indent=2)
        fp.write("\n")


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    items = []
    for name, stat in CANDIES.items():
        img = candy(32, STAT_COLORS[stat], 1.0)
        path = os.path.join(ROOT, "textures", "item", name + ".png")
        img.save(path)
        write_model(name)
        items.append(img)
        print("wrote", path)
    effects = []
    for stat, color in STAT_COLORS.items():
        img = candy(18, color, 0.56)
        path = os.path.join(ROOT, "textures", "mob_effect", "candy_" + stat + ".png")
        img.save(path)
        effects.append(img)
        print("wrote", path)
    if preview:
        sheet = Image.new("RGBA", (len(STAT_COLORS) * 76 + 8, 180), (44, 56, 60, 255))
        for k, img in enumerate(items):
            sheet.alpha_composite(img.resize((64, 64), Image.NEAREST), (8 + k * 76, 8))
        for k, img in enumerate(effects):
            sheet.alpha_composite(img.resize((54, 54), Image.NEAREST), (8 + k * 76, 100))
        sheet.save(preview)
        print("preview", preview)


if __name__ == "__main__":
    main()
