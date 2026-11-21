"""塔の階の見本の絵（build_floor1.py の --render から呼ぶ）。

  floor1_map.png      上から見た地図（いちばん上のブロックの色と、陰。道・小川・石柱・広間・入口・湧く所の印）
  floor1_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。範囲を切り出して描く）
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from mapgen.world import World, block

COLORS = {
    "grass_block": (106, 160, 72), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "sand": (219, 207, 163),
    "gravel": (136, 126, 126), "clay": (160, 166, 179), "water": (63, 118, 228), "stone": (125, 125, 125),
    "andesite": (136, 136, 136), "polished_andesite": (132, 135, 134), "diorite": (188, 188, 188),
    "polished_diorite": (192, 193, 194), "calcite": (223, 224, 220), "stone_bricks": (122, 121, 122),
    "mossy_stone_bricks": (115, 121, 105), "cracked_stone_bricks": (118, 117, 118), "chiseled_stone_bricks": (119, 118, 119),
    "cobblestone": (127, 127, 127), "mossy_cobblestone": (110, 118, 94), "moss_block": (89, 109, 45),
    "moss_carpet": (89, 109, 45), "oak_leaves": (60, 110, 40), "birch_leaves": (96, 140, 60), "oak_log": (109, 85, 50),
    "birch_log": (216, 215, 210), "amemaru:lost_blank": (250, 252, 255), "amemaru:tower_pillar": (95, 227, 160),
    "amemaru:floor_seal": (255, 230, 160), "green_stained_glass": (102, 127, 51), "sea_lantern": (172, 199, 190),
    "prismarine": (99, 156, 151), "quartz_bricks": (234, 229, 221), "smooth_quartz": (236, 230, 223),
    "chiseled_quartz_block": (231, 226, 218), "quartz_pillar": (235, 230, 222), "light_blue_stained_glass_pane": (102, 153, 216),
    "grass": (106, 160, 72), "tall_grass": (106, 160, 72), "fern": (96, 150, 64), "dandelion": (255, 236, 79),
    "poppy": (237, 48, 44), "oxeye_daisy": (214, 230, 230), "azure_bluet": (200, 210, 220), "cornflower": (70, 100, 220),
    "lantern": (200, 160, 80), "spruce_sign": (115, 85, 50), "stone_brick_wall": (122, 121, 122),
    "mossy_stone_brick_wall": (115, 121, 105), "stone_brick_slab": (122, 121, 122), "mossy_stone_brick_slab": (115, 121, 105),
    "polished_andesite_stairs": (132, 135, 134), "chain": (60, 60, 70), "vine": (60, 110, 40),
}
SKIP_TOP = {"barrier", "light_blue_stained_glass_pane", "grass", "tall_grass", "fern", "dandelion", "poppy", "oxeye_daisy",
            "azure_bluet", "cornflower", "moss_carpet", "vine", "chain"}


def top_map(f, data, path, scale=2):
    n = f.w
    top = np.zeros((n, n), dtype=int)
    rgb = np.zeros((n, n, 3), dtype=float)
    columns = {}
    for (x, y, z), st in f.blocks.items():
        if st[0] in SKIP_TOP:
            continue
        if y > columns.get((x, z), (-1, None))[0]:
            columns[(x, z)] = (y, st[0])
    for (x, z), (y, name) in columns.items():
        top[x, z] = y
        rgb[x, z] = COLORS.get(name, (200, 0, 200))
    # 陰（北西から光）
    shade = np.ones((n, n))
    dzx = np.zeros((n, n))
    dzx[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade += np.clip(dzx * 0.12, -0.35, 0.35)
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    colors = {"tsunomofu": (255, 240, 200), "ishikure_soldier": (160, 160, 160), "crystal_bee": (120, 255, 200),
              "kakekiba_wolf": (255, 80, 80)}
    for z in data["spawns"]:
        cx, _, cz = z["center"]
        r = z["radius"]
        col = colors.get(z["enemy"].split(":")[1], (255, 0, 255))
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=col, width=2)
        for p in z.get("route", []):
            dr.rectangle(((p[0] - 1) * scale, (p[2] - 1) * scale, (p[0] + 1) * scale, (p[2] + 1) * scale), outline=col)
    for i, p in enumerate(data["pillars"]):
        x, _, z = p["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 255, 0), width=3)
        dr.text(((x + 5) * scale, (z - 3) * scale), "P%d" % i, fill=(255, 255, 0))
    e = data["entrance"]["pos"]
    dr.rectangle(((e[0] - 2) * scale, (e[2] - 2) * scale, (e[0] + 2) * scale, (e[2] + 2) * scale), outline=(0, 255, 255), width=2)
    img.save(path)
    return path


def sub_world(f, x0, y0, z0, x1, y1, z1):
    """範囲を切り出した World（見本の絵のため。Modのブロックは、近い色のバニラのブロックに置きかえる）"""
    w = World(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    swap = {"amemaru:lost_blank": block("white_concrete"), "amemaru:tower_pillar": block("emerald_block"),
            "amemaru:floor_seal": block("yellow_stained_glass")}
    for (x, y, z), st in f.blocks.items():
        if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
            if st[0] == "barrier":
                continue
            st = swap.get(st[0], st)
            w.blocks[(x - x0, y - y0, z - z0)] = st
    return w


def render_all(f, data, out):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor1_map.png")))
    from mapgen.render import Scene
    hx, hz = 192, 58
    views = [
        # (名前, 範囲, 目, 見る所)
        ("hall_front", (hx - 40, 6, hz - 40, hx + 40, 47, hz + 66), (hx + 26, 34, hz + 92), (hx, 16, hz + 20)),
        ("hall_inside", (hx - 40, 6, hz - 40, hx + 40, 47, hz + 66), (hx + 2, 18, hz + 26), (hx - 6, 17, hz - 18)),
        ("hall_top", (hx - 40, 6, hz - 40, hx + 40, 47, hz + 66), (hx, 120, hz + 12), (hx, 10, hz + 11)),
    ]
    for (px, _, pz), i in zip([p["pos"] for p in data["pillars"]], range(3)):
        views.append(("pillar%d" % i, (px - 22, 4, pz - 22, px + 22, 40, pz + 22), (px + 14, 26, pz + 16), (px, 16, pz)))
    ex, _, ez = data["return_circle"]["center"]
    ex, ez = int(ex), int(ez)
    views.append(("entrance", (ex - 30, 4, ez - 60, ex + 30, 40, ez + 14), (ex + 6, 19, ez + 10), (ex - 4, 14, ez - 40)))
    views.append(("bridge", (150, 4, 170, 250, 40, 250), (176, 22, 236), (200, 11, 205)))
    for name, (x0, y0, z0, x1, y1, z1), eye, target in views:
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor1_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70 if name != "hall_top" else 50, path=path)
        print("描きました:", path)
