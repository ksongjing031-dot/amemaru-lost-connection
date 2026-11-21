"""AMEMARU: LOST CONNECTION の塔の階の仕掛け（11月22日【補完】）の絵（16×16）と、ブロックの状態・モデルを作る。

使い方: python3 tools/artgen/make_floor_things.py [assets/amemaru のフォルダ]

作るもの：
  textures/block/tower_switch_side.png・_top.png・_bottom.png       古代の仕掛け石（白い古代石に、まるい紋の板）
  textures/block/tower_switch_side_lit.png・_top_lit.png           押して光っている間（紋と筋が水色に光る）
  textures/block/tower_gate.png・tower_gate_top.png                 古代の格子（古代石の格子と、すき間の薄い光の膜。半透明）
  textures/block/tower_chest_front.png・_side.png・_top.png・_bottom.png  古代の宝箱（白い古代石に、真鍮の金具と翠の結晶）
  textures/item/light_shard.png                                     光のかけら（1階の欠け牙の狼が落とす、白金色の光の粒）
  blockstates/・models/block/・models/item/                          上のブロックと品
色は古代の石柱の核（make_phase5_blocks.py）とそろえる：白い古代石 #D8D4C8、翠の結晶 #3FBF7F・#7FF2B8・#C8FFE4。
"""
import json
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"

STONE = "#D8D4C8"
STONE_D = "#B4AE9F"
STONE_DD = "#9C9788"
STONE_L = "#E8E5DC"
JADE_D = "#3FBF7F"
JADE = "#7FF2B8"
JADE_L = "#C8FFE4"
AQUA_D = "#3FA9C9"
AQUA = "#7FE3FF"
AQUA_L = "#E2FAFF"
BRASS = "#D4A94F"
BRASS_D = "#9C7432"
BRASS_L = "#F1D58C"
OUTLINE = "#2A2520"


def hexc(s, a=255):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)] + [a], dtype=np.float64)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(path)
    return path


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def stone(rng, base=STONE, jitter=9):
    img = np.zeros((16, 16, 4))
    b = hexc(base)
    for y in range(16):
        for x in range(16):
            n = rng.uniform(-jitter, jitter)
            img[y, x] = b + [n, n, n * 0.9, 0]
    return img


def put(img, x, y, col, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y, x] = hexc(col, a) if isinstance(col, str) else col


def ring(img, cx, cy, r0, r1, col):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r0 <= d < r1:
                put(img, x, y, col)


# ---------------------------------------------------------------- 古代の仕掛け石

def switch(rng, lit):
    glow, glow_l, glow_d = (AQUA, AQUA_L, AQUA_D) if lit else ("#A9C4BE", "#C9D8D3", "#8FA39E")
    side = stone(rng)
    for x in range(16):
        put(side, x, 0, STONE_L)
        put(side, x, 1, STONE_D)
        put(side, x, 15, "#AFAA9C")
    # 下の台座の帯（彫り）
    for x in range(16):
        put(side, x, 11, STONE_D)
        put(side, x, 12, STONE_L)
    # まんなかの紋（ひし形の枠と、縦の筋）
    for y in range(3, 10):
        k = 3 - abs(y - 6)
        put(side, 7 - k, y, STONE_DD)
        put(side, 8 + k, y, STONE_DD)
    for y in range(4, 9):
        put(side, 7, y, glow_d)
        put(side, 8, y, glow if y != 6 else glow_l)
    for x in (2, 13):
        for y in range(3, 10):
            put(side, x, y, "#C4BEB0")
    top = stone(rng)
    # ふちの帯
    for i in range(16):
        for j in (0, 15):
            put(top, i, j, STONE_D)
            put(top, j, i, STONE_D)
    # まるい紋の板
    ring(top, 7.5, 7.5, 5.2, 6.2, STONE_DD)
    ring(top, 7.5, 7.5, 4.2, 5.2, glow_d)
    ring(top, 7.5, 7.5, 3.0, 4.2, STONE_L)
    ring(top, 7.5, 7.5, 0.0, 3.0, STONE)
    # 紋（十字と点）
    for k in range(-2, 3):
        put(top, 7 + k if k < 0 else 8 + k - 1, 7, glow)
        put(top, 7, 7 + k if k < 0 else 8 + k - 1, glow)
    for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        put(top, x, y, glow_l)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(top, x, y, glow_d)
    bottom = stone(rng, base="#C9C4B6")
    return side, top, bottom


# ---------------------------------------------------------------- 古代の格子

def gate(rng):
    img = np.zeros((16, 16, 4))
    # すき間：薄い水色の光の膜（半透明）
    for y in range(16):
        for x in range(16):
            a = 70 + int(25 * np.sin((x + y * 0.6) * 0.8))
            img[y, x] = hexc(AQUA, a)
    # 縦の格子（古代石。3本と、両はし）
    for bx in (0, 5, 10, 15):
        for y in range(16):
            n = rng.uniform(-8, 8)
            for dx in (0, 1) if bx < 15 else (0,):
                x = bx + dx
                if x > 15:
                    continue
                c = hexc(STONE_L if dx == 0 else STONE_D) + [n, n, n, 0]
                img[y, x] = c
    # 横の帯（上・まんなか・下）
    for by in (0, 7, 15):
        for x in range(16):
            n = rng.uniform(-8, 8)
            img[by, x] = hexc(STONE if by != 7 else STONE_L) + [n, n, n, 0]
            if by == 7:
                img[8, x] = hexc(STONE_D) + [n, n, n, 0]
    # 交わる所の翠の鋲
    for bx in (5, 10):
        put(img, bx, 7, JADE)
        put(img, bx + 1, 8, JADE_D)
    # 格子の縦の筋の光
    for y in (3, 11):
        for bx in (5, 10):
            put(img, bx, y, JADE_L)
    top = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            top[y, x] = hexc(AQUA, 60)
    for bx in (0, 5, 10, 15):
        for y in range(16):
            n = rng.uniform(-8, 8)
            top[y, bx] = hexc(STONE_D) + [n, n, n, 0]
    return img, top


# ---------------------------------------------------------------- 古代の宝箱

def chest(rng):
    front = stone(rng)
    # ふたと箱の分かれ目（y=6）
    for x in range(16):
        put(front, x, 0, STONE_L)
        put(front, x, 5, STONE_D)
        put(front, x, 6, STONE_DD)
        put(front, x, 7, STONE_L)
        put(front, x, 15, "#AFAA9C")
    # 両はしの真鍮の帯
    for y in range(16):
        for x in (1, 14):
            put(front, x, y, BRASS if y % 4 else BRASS_L)
        put(front, 2, y, BRASS_D)
    # 錠（真ん中。真鍮の板と翠の結晶）
    for y in range(4, 10):
        for x in range(6, 10):
            put(front, x, y, BRASS if (x + y) % 3 else BRASS_L)
    for x in range(6, 10):
        put(front, x, 10, BRASS_D)
    put(front, 7, 6, JADE)
    put(front, 8, 6, JADE_L)
    put(front, 7, 7, JADE_D)
    put(front, 8, 7, JADE)
    # 箱の古代文字の彫り
    for (x, y) in ((4, 10), (4, 12), (11, 10), (11, 12), (5, 11), (10, 11)):
        put(front, x, y, STONE_DD)
    side = stone(rng)
    for x in range(16):
        put(side, x, 0, STONE_L)
        put(side, x, 5, STONE_D)
        put(side, x, 6, STONE_DD)
        put(side, x, 7, STONE_L)
        put(side, x, 15, "#AFAA9C")
    for y in range(16):
        for x in (1, 14):
            put(side, x, y, BRASS if y % 4 else BRASS_L)
    for (x, y) in ((7, 10), (8, 11), (7, 12), (8, 9)):
        put(side, x, y, JADE_D)
    top = stone(rng, base=STONE_L, jitter=7)
    for i in range(16):
        put(top, i, 1, BRASS)
        put(top, i, 14, BRASS)
        put(top, i, 2, BRASS_D)
    for y in range(5, 11):
        for x in range(5, 11):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 2.2:
                put(top, x, y, JADE_L if d < 1.2 else JADE)
            elif d < 3.4:
                put(top, x, y, JADE_D)
    bottom = stone(rng, base="#C9C4B6")
    return front, side, top, bottom


# ---------------------------------------------------------------- 光のかけら

def light_shard(rng):
    img = np.zeros((16, 16, 4))
    pts = [(8, 1), (12, 6), (10, 14), (5, 10), (5, 5)]

    def inside(x, y):
        n = len(pts)
        flag = False
        j = n - 1
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                flag = not flag
            j = i
        return flag
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                d = ((x - 8) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d < 1.6:
                    col = "#FFFFFF"
                elif d < 3.0:
                    col = "#FFF6C8"
                elif (x - y) % 4 == 0:
                    col = "#FFE89A"
                else:
                    col = "#F5D77A" if x + y < 17 else "#D9B65A"
                put(img, x, y, col)
    # ふちどり
    alpha = img[:, :, 3] > 0
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if alpha[y, x]:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and alpha[y + dy, x + dx]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y, x] = hexc(OUTLINE)
    # 光の点（ふちの外）
    for (x, y) in ((2, 3), (13, 2), (14, 11), (3, 13)):
        if out[y, x, 3] == 0:
            put(out, x, y, "#FFF6C8", 200)
    return out


# ---------------------------------------------------------------- モデル

def chest_model():
    """宝箱：箱（1,0,1〜15,9,15）・ふた（1,9,1〜15,13,15）・錠（7,6,0〜9,11,1）。正面は北。"""
    def faces(front_tex, uv_front, uv_side, uv_top):
        return {
            "north": {"uv": uv_front, "texture": front_tex},
            "south": {"uv": uv_side, "texture": "#side"},
            "east": {"uv": uv_side, "texture": "#side"},
            "west": {"uv": uv_side, "texture": "#side"},
            "up": {"uv": uv_top, "texture": "#top"},
            "down": {"uv": uv_top, "texture": "#bottom"},
        }
    body = {"name": "body", "from": [1, 0, 1], "to": [15, 9, 15],
            "faces": faces("#front", [1, 7, 15, 16], [1, 7, 15, 16], [1, 1, 15, 15])}
    lid = {"name": "lid", "from": [1, 9, 1], "to": [15, 13, 15],
           "faces": faces("#front", [1, 1, 15, 5], [1, 1, 15, 5], [1, 1, 15, 15])}
    latch = {"name": "latch", "from": [7, 6, 0.5], "to": [9, 11, 1], "faces": {
        "north": {"uv": [6, 4, 10, 10], "texture": "#front"},
        "east": {"uv": [6, 4, 7, 10], "texture": "#front"},
        "west": {"uv": [9, 4, 10, 10], "texture": "#front"},
        "up": {"uv": [6, 4, 10, 5], "texture": "#front"},
        "down": {"uv": [6, 9, 10, 10], "texture": "#front"},
    }}
    return {"parent": "minecraft:block/block", "textures": {
        "particle": "amemaru:block/tower_chest_side", "front": "amemaru:block/tower_chest_front",
        "side": "amemaru:block/tower_chest_side", "top": "amemaru:block/tower_chest_top",
        "bottom": "amemaru:block/tower_chest_bottom"}, "elements": [body, lid, latch]}


def main():
    rng = random.Random(1122)
    for lit in (False, True):
        side, top, bottom = switch(rng, lit)
        suffix = "_lit" if lit else ""
        save(side, f"textures/block/tower_switch_side{suffix}.png")
        save(top, f"textures/block/tower_switch_top{suffix}.png")
        if not lit:
            save(bottom, "textures/block/tower_switch_bottom.png")
    side, top = gate(rng)
    save(side, "textures/block/tower_gate.png")
    save(top, "textures/block/tower_gate_top.png")
    front, side, top, bottom = chest(rng)
    save(front, "textures/block/tower_chest_front.png")
    save(side, "textures/block/tower_chest_side.png")
    save(top, "textures/block/tower_chest_top.png")
    save(bottom, "textures/block/tower_chest_bottom.png")
    save(light_shard(rng), "textures/item/light_shard.png")

    write_json("blockstates/tower_switch.json", {"variants": {
        "lit=false": {"model": "amemaru:block/tower_switch"}, "lit=true": {"model": "amemaru:block/tower_switch_lit"}}})
    for suffix in ("", "_lit"):
        write_json(f"models/block/tower_switch{suffix}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"amemaru:block/tower_switch_top{suffix}", "side": f"amemaru:block/tower_switch_side{suffix}",
            "bottom": "amemaru:block/tower_switch_bottom"}})
    write_json("blockstates/tower_gate.json", {"variants": {
        "open=false": {"model": "amemaru:block/tower_gate"}, "open=true": {"model": "amemaru:block/tower_gate_open"}}})
    # 半透明（すき間の光の膜）。Forge のモデルの render_type
    write_json("models/block/tower_gate.json", {"parent": "minecraft:block/cube_column", "render_type": "minecraft:translucent",
                                                "textures": {"end": "amemaru:block/tower_gate_top", "side": "amemaru:block/tower_gate"}})
    # ひらいている間は見えない（形も当たりもない）
    write_json("models/block/tower_gate_open.json", {"textures": {"particle": "amemaru:block/tower_gate"}})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json("blockstates/tower_chest.json", {"variants": {
        f"facing={f}": ({"model": "amemaru:block/tower_chest", "y": y} if y else {"model": "amemaru:block/tower_chest"})
        for f, y in rot.items()}})
    write_json("models/block/tower_chest.json", chest_model())
    for name in ("tower_switch", "tower_gate", "tower_chest"):
        write_json(f"models/item/{name}.json", {"parent": f"amemaru:block/{name}"})
    write_json("models/item/light_shard.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/light_shard"}})
    print("ok")


if __name__ == "__main__":
    main()
