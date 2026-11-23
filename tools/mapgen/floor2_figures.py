"""塔の2階の見本の絵（build_floor2.py の --render から呼ぶ）。

  floor2_map.png      上から見た地図（いちばん上のブロックの色と陰。区画・迷いの森の箱・鐘・灯台・中継の灯・宝箱・湧く所）
  floor2_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene）
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor_figures import COLORS
from mapgen.world import World, block

MORE = {
    "dark_oak_leaves": (44, 88, 30), "azalea_leaves": (92, 124, 46), "flowering_azalea_leaves": (120, 120, 80),
    "spruce_leaves": (54, 84, 54), "spruce_log": (84, 62, 38), "birch_leaves": (110, 150, 70), "birch_log": (216, 215, 210),
    "mangrove_roots": (80, 62, 40), "rooted_dirt": (144, 104, 76), "mycelium": (112, 98, 110), "podzol": (106, 76, 40),
    "mushroom_stem": (204, 196, 180), "red_mushroom_block": (200, 46, 40), "brown_mushroom_block": (150, 112, 82),
    "shroomlight": (240, 150, 70), "waxed_weathered_cut_copper": (100, 150, 120), "waxed_oxidized_cut_copper": (80, 160, 140),
    "waxed_weathered_copper": (100, 150, 120), "waxed_exposed_copper": (160, 120, 100), "waxed_oxidized_copper": (80, 160, 140),
    "waxed_exposed_cut_copper_slab": (160, 120, 100), "waxed_oxidized_cut_copper_slab": (80, 160, 140),
    "moss_carpet": (89, 109, 45), "azalea": (90, 120, 50), "cave_vines": (70, 100, 40), "cave_vines_plant": (70, 100, 40),
    "spruce_planks": (114, 84, 48), "stripped_spruce_log": (140, 110, 70), "spruce_stairs": (114, 84, 48),
    "spruce_slab": (114, 84, 48), "lectern": (160, 120, 70), "barrel": (130, 90, 50), "crafting_table": (130, 90, 50),
    "amemaru:forest_bell": (90, 255, 230), "amemaru:knot_post": (255, 120, 60), "amemaru:waypoint": (255, 255, 120),
    "amemaru:travel_stone": (120, 255, 255), "amemaru:floor_seal": (255, 230, 160), "amemaru:tower_chest": (255, 200, 40),
    "clay": (160, 166, 179), "dirt": (134, 96, 67), "glass_pane": (200, 220, 230),
}
SKIP_TOP = {"barrier", "grass", "fern", "large_fern", "moss_carpet", "brown_mushroom", "red_mushroom", "azalea",
            "cave_vines", "cave_vines_plant", "spruce_sign", "lantern"}
LEAVES = {"dark_oak_leaves", "azalea_leaves", "flowering_azalea_leaves", "spruce_leaves", "birch_leaves"}
FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"


def top_map(f, data, path, scale=2, ground=False):
    n = f.w
    cols = {}
    for (x, y, z), st in f.blocks.items():
        name = st[0]
        if name in SKIP_TOP or name == "air":
            continue
        if ground and (name in LEAVES or name.endswith("_log") or name in ("mangrove_roots", "dark_oak_wood")):
            continue
        if y > cols.get((x, z), (-1, None))[0]:
            cols[(x, z)] = (y, name)
    top = np.zeros((n, n), dtype=int)
    rgb = np.zeros((n, n, 3), dtype=float)
    for (x, z), (y, name) in cols.items():
        top[x, z] = y
        rgb[x, z] = MORE.get(name, COLORS.get(name, (200, 0, 200)))
    shade = np.ones((n, n))
    dzx = np.zeros((n, n))
    dzx[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade += np.clip(dzx * 0.12, -0.35, 0.35)
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    for w in data["lost_woods"]:
        x0, _, z0, x1, _, z1 = w["box"]
        dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(255, 255, 255), width=2)
        dr.text((x0 * scale + 4, z0 * scale + 2), w["name"], fill=(255, 255, 255), font=font)
        bx, _, bz = w["bell"]
        dr.ellipse(((bx - 3) * scale, (bz - 3) * scale, (bx + 3) * scale, (bz + 3) * scale), outline=(90, 255, 230), width=3)
        for e in w["exits"]:
            ex0, _, ez0, ex1, _, ez1 = e["box"]
            col = (80, 255, 80) if e["correct"] else (255, 70, 70)
            dr.rectangle((ex0 * scale, ez0 * scale, (ex1 + 1) * scale, (ez1 + 1) * scale), outline=col, width=2)
            px, _, pz = e["post"]
            dr.rectangle(((px - 1) * scale, (pz - 1) * scale, (px + 1) * scale, (pz + 1) * scale), fill=(255, 140, 60))
        ex, _, ez = w["entry"]["pos"]
        dr.ellipse(((ex - 2) * scale, (ez - 2) * scale, (ex + 2) * scale, (ez + 2) * scale), outline=(0, 200, 255), width=2)
    for wp in data["waypoints"]:
        x, _, z = wp["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 255, 0), width=3)
        dr.text(((x + 5) * scale, (z - 4) * scale), wp["name"], fill=(255, 255, 120), font=font)
    for c in data["chests"]:
        x, _, z = c["pos"]
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                     outline=(255, 60, 60) if c.get("guardian") else (255, 170, 0), width=2)
    colors = {"mayoi_bi": (255, 240, 160), "juhihei": (170, 130, 90), "pokodake": (255, 90, 90), "hakage_kitsune": (120, 255, 120)}
    for z in data["spawns"]:
        cx, _, cz = z["center"]
        r = z["radius"]
        col = colors.get(z["enemy"].split(":")[1], (255, 0, 255))
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=col, width=1)
        for p in z.get("route", []):
            dr.rectangle(((p[0] - 1) * scale, (p[2] - 1) * scale, (p[0] + 1) * scale, (p[2] + 1) * scale), outline=col)
    for g in data.get("guardians", []):
        x, _, z = g["pos"]
        dr.rectangle(((x - 3) * scale, (z - 3) * scale, (x + 3) * scale, (z + 3) * scale), outline=(255, 0, 0), width=3)
    b = data["boss_room"]
    cx, _, cz = b["arena"]["center"]
    r = b["arena"]["radius"]
    dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
    for zz in data.get("story_zones", []):
        x0, _, z0, x1, _, z1 = zz["box"]
        dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(200, 160, 255), width=2)
    e = data["entrance"]["pos"]
    dr.rectangle(((e[0] - 2) * scale, (e[2] - 2) * scale, (e[0] + 2) * scale, (e[2] + 2) * scale), outline=(0, 255, 255), width=2)
    img.save(path)
    return path


def sub_world(f, x0, y0, z0, x1, y1, z1):
    w = World(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    swap = {"amemaru:lost_blank": block("white_concrete"), "amemaru:floor_seal": block("yellow_stained_glass"),
            "amemaru:tower_chest": block("barrel", facing="up", open=False),
            "amemaru:forest_bell": block("bell", attachment="floor", facing="north", powered=False),
            "amemaru:knot_post": block("lantern", hanging=False, waterlogged=False),
            "amemaru:waypoint": block("sea_lantern"), "amemaru:travel_stone": block("chiseled_stone_bricks")}
    for (x, y, z), st in f.blocks.items():
        if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
            if st[0] in ("barrier", "air"):
                continue
            st = swap.get(st[0], st)
            w.blocks[(x - x0, y - y0, z - z0)] = st
    return w


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor2_map.png")))
    print("描きました:", top_map(f, data, os.path.join(out, "floor2_ground.png"), ground=True))
    try:
        from mapgen.render import Scene
    except Exception as e:  # noqa: BLE001
        print("絵の道具が使えません:", e)
        return
    ex, _, ez = data["return_circle"]["center"]
    ex, ez = int(ex), int(ez)
    hx, hz = 216, 258
    cx, cz = 216, 56
    views = [
        ("entrance", (ex - 30, 4, ez - 50, ex + 30, 60, ez + 14), (ex + 6, 22, ez + 10), (ex - 2, 18, ez - 30)),
        ("hub", (hx - 40, 4, hz - 40, hx + 40, 60, hz + 40), (hx + 2, 32, hz + 16), (hx + 12, 19, hz - 12)),
        ("hollow", (14, 0, 226, 104, 60, 314), (96, 30, 300), (54, 12, 266)),
        ("grove", (36, 8, 50, 134, 63, 150), (120, 40, 140), (80, 26, 92)),
        ("ravine", (344, 0, 180, 412, 60, 300), (360, 26, 290), (382, 10, 228)),
        ("woods_north", (170, 4, 124, 264, 60, 210), (216, 24, 214), (216, 18, 170)),
        ("clearing", (cx - 36, 4, cz - 36, cx + 36, 60, cz + 50), (cx + 4, 30, cz + 46), (cx, 18, cz)),
        ("seal", (cx - 20, 4, 80, cx + 20, 50, 120), (cx + 4, 22, 112), (cx, 21, 96)),
    ]
    for name, (x0, y0, z0, x1, y1, z1), eye, target in views:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor2_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path)
        print("描きました:", path)
