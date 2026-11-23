"""AMEMARU: LOST CONNECTION の Phase 5 のブロックとアイテムの絵（16×16）と、ブロックの状態・モデルを作る。

使い方: python3 tools/artgen/make_phase5_blocks.py [assets/amemaru のフォルダ]

作るもの：
  textures/block/tower_pillar_side.png・tower_pillar_top.png   古代の石柱の核（白い古代石に、翠の結晶の筋）
  textures/block/floor_seal.png                                 封印の光の幕（壊れたときの粒の色だけに使う。幕は描き手が描く）
  textures/block/lost_blank.png                                 ロストの欠け（白く抜け落ちた所）
  textures/block/memory_stone_side.png・memory_stone_top.png   大結晶の記憶の石（石英の台に、水色の結晶の線）
  textures/item/grassland_horn.png                              草原の角笛（1階のボスのレアのお守り）
  blockstates/・models/block/・models/item/                     上のブロックと、家の扉・スポーンエッグ
"""
import json
import os
import random
import sys

import numpy as np
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"


def hexc(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)] + [255], dtype=np.float64)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(path)
    return path


def stone(rng, base="#D8D4C8", jitter=10):
    img = np.zeros((16, 16, 4))
    b = hexc(base)
    for y in range(16):
        for x in range(16):
            n = rng.uniform(-jitter, jitter)
            img[y, x] = b + [n, n, n * 0.9, 0]
    return img


def put(img, x, y, col):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y, x] = hexc(col) if isinstance(col, str) else col


def tower_pillar(rng):
    side = stone(rng)
    # 古い石の縁（上下に彫った帯）
    for x in range(16):
        put(side, x, 0, "#B9B4A6")
        put(side, x, 1, "#E8E5DC")
        put(side, x, 14, "#E8E5DC")
        put(side, x, 15, "#AFAA9C")
    # 格子の彫り（古代文字の枠）
    for y in range(3, 13):
        put(side, 2, y, "#B4AE9F")
        put(side, 13, y, "#B4AE9F")
    for x in (3, 4, 11, 12):
        for y in (4, 7, 10):
            put(side, x, y, "#BDB7A8")
    # 真ん中の縦の結晶の筋（翠）
    for y in range(2, 14):
        put(side, 7, y, "#3FBF7F")
        put(side, 8, y, "#7FF2B8" if y % 3 else "#C8FFE4")
        put(side, 6, y, "#9C9788")
        put(side, 9, y, "#9C9788")
    top = stone(rng)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.2:
                put(top, x, y, "#C8FFE4")
            elif d < 3.6:
                put(top, x, y, "#5FE3A0")
            elif d < 4.5:
                put(top, x, y, "#9C9788")
            elif d > 7.2:
                put(top, x, y, "#B9B4A6")
    return side, top


def floor_seal(rng):
    img = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            v = 0.5 + 0.5 * np.sin((x + y) * 0.8)
            img[y, x] = [220 + 30 * v, 238 + 12 * v, 255, 255]
    return img


def lost_blank(rng):
    img = np.zeros((16, 16, 4))
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.08:
                img[y, x] = hexc("#E6EEF9")
            elif r < 0.12:
                img[y, x] = hexc("#DCE8F7")
            else:
                img[y, x] = hexc("#FBFDFF")
    # ところどころ、四角く欠けた跡
    for (x0, y0) in ((3, 4), (10, 11), (12, 2)):
        for dy in range(2):
            for dx in range(2):
                put(img, x0 + dx, y0 + dy, "#F2F7FF")
    return img


def memory_stone(rng):
    side = stone(rng, "#EDEAE2", 6)
    for x in range(16):
        put(side, x, 0, "#CFCAC0")
        put(side, x, 15, "#C6C1B6")
        put(side, x, 1, "#F7F5F0")
    # 結び目の紋（水色の結晶の線）
    knot = [(4, 5), (5, 4), (6, 4), (7, 5), (8, 6), (9, 7), (10, 8), (11, 9), (11, 10), (10, 11), (9, 11), (8, 10),
            (7, 9), (6, 8), (5, 7), (4, 6), (11, 5), (10, 4), (9, 4), (4, 9), (4, 10), (5, 11), (6, 11), (7, 10)]
    for (x, y) in knot:
        put(side, x, y, "#8FD3FF")
    put(side, 7, 7, "#E6F7FF")
    put(side, 8, 8, "#E6F7FF")
    top = stone(rng, "#EDEAE2", 6)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.5:
                put(top, x, y, "#E6F7FF")
            elif d < 4.0:
                put(top, x, y, "#8FD3FF")
            elif d < 4.8:
                put(top, x, y, "#BFB9AD")
    return side, top


def grassland_horn(rng):
    """角笛：象牙色の角（左下が吹き口、右上が開いた口）に、真鍮の輪と翠の結晶。ふちどりつき"""
    img = np.zeros((16, 16, 4))
    ivory, ivory_d, ivory_l = "#EFE6CF", "#C9BC98", "#FFF8E6"
    brass, brass_d = "#D4A94F", "#9C7432"
    path = [(3, 12), (4, 11), (5, 11), (6, 10), (7, 9), (8, 9), (9, 8), (10, 7), (11, 6), (11, 5), (12, 4)]
    width = [1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4]
    cells = {}
    for (x, y), w in zip(path, width):
        for k in range(-(w // 2), w - w // 2):
            cells[(x + k, y + k)] = ivory
            cells[(x + k, y + k + 1)] = ivory_d
            cells[(x + k, y + k - 1)] = ivory_l if k == -(w // 2) else ivory
    # 開いた口（右上）
    for (x, y) in ((12, 2), (13, 2), (13, 3), (14, 3), (14, 4), (11, 3), (12, 3)):
        cells[(x, y)] = ivory_l
    for (x, y) in ((13, 4), (12, 5), (14, 5)):
        cells[(x, y)] = "#7B6A4A"
    # 真鍮の輪
    for (x, y) in ((6, 9), (7, 10), (6, 11), (9, 6), (10, 7), (9, 8), (8, 7)):
        cells[(x, y)] = brass
    for (x, y) in ((7, 11), (10, 8)):
        cells[(x, y)] = brass_d
    # 吹き口と、翠の結晶
    cells[(2, 13)] = brass_d
    cells[(3, 13)] = brass
    cells[(8, 8)] = "#5FE3A0"
    cells[(9, 7)] = "#C8FFE4"
    # 吊り紐
    for (x, y) in ((4, 9), (4, 8), (5, 7), (6, 6), (7, 6)):
        cells[(x, y)] = "#6B8F4E"
    for (x, y), col in cells.items():
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
                out[y, x] = hexc("#2A2520")
    return out


def write_json(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def door_blockstate(model_prefix):
    variants = {}
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing, y0 in rot.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    name = f"{model_prefix}_{part}_{hinge}" + ("_open" if opened else "")
                    y = y0
                    if opened:
                        y = (y0 + (90 if hinge == "left" else 270)) % 360
                    v = {"model": name}
                    if y:
                        v["y"] = y
                    key = f"facing={facing},half={half},hinge={hinge},open={'true' if opened else 'false'}"
                    variants[key] = v
    return {"variants": variants}


def main():
    rng = random.Random(505)
    side, top = tower_pillar(rng)
    save(side, "textures/block/tower_pillar_side.png")
    save(top, "textures/block/tower_pillar_top.png")
    save(floor_seal(rng), "textures/block/floor_seal.png")
    save(lost_blank(rng), "textures/block/lost_blank.png")
    side, top = memory_stone(rng)
    save(side, "textures/block/memory_stone_side.png")
    save(top, "textures/block/memory_stone_top.png")
    save(grassland_horn(rng), "textures/item/grassland_horn.png")

    write_json("blockstates/tower_pillar.json", {"variants": {"": {"model": "amemaru:block/tower_pillar"}}})
    write_json("models/block/tower_pillar.json", {"parent": "minecraft:block/cube_column", "textures": {
        "end": "amemaru:block/tower_pillar_top", "side": "amemaru:block/tower_pillar_side"}})
    write_json("blockstates/floor_seal.json", {"variants": {
        "sealed=false": {"model": "amemaru:block/floor_seal"}, "sealed=true": {"model": "amemaru:block/floor_seal"}}})
    write_json("models/block/floor_seal.json", {"textures": {"particle": "amemaru:block/floor_seal"}})
    write_json("blockstates/lost_blank.json", {"variants": {"": {"model": "amemaru:block/lost_blank"}}})
    write_json("models/block/lost_blank.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "amemaru:block/lost_blank"}})
    write_json("blockstates/memory_stone.json", {"variants": {"": {"model": "amemaru:block/memory_stone"}}})
    write_json("models/block/memory_stone.json", {"parent": "minecraft:block/cube_column", "textures": {
        "end": "amemaru:block/memory_stone_top", "side": "amemaru:block/memory_stone_side"}})
    write_json("blockstates/house_door_spruce.json", door_blockstate("minecraft:block/spruce_door"))
    write_json("blockstates/house_door_dark_oak.json", door_blockstate("minecraft:block/dark_oak_door"))

    for name in ("tower_pillar", "lost_blank", "memory_stone"):
        write_json(f"models/item/{name}.json", {"parent": f"amemaru:block/{name}"})
    write_json("models/item/floor_seal.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:block/floor_seal"}})
    write_json("models/item/house_door_spruce.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/spruce_door"}})
    write_json("models/item/house_door_dark_oak.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/dark_oak_door"}})
    for name in ("veldhorn_spawn_egg", "npc_spawn_egg"):
        write_json(f"models/item/{name}.json", {"parent": "minecraft:item/template_spawn_egg"})
    write_json("models/item/grassland_horn.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/grassland_horn"}})
    print("ok")


if __name__ == "__main__":
    main()
