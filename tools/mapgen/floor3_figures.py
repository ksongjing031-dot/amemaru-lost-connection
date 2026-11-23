"""塔の3階の見本の絵（build_floor3.py の --render から呼ぶ。Phase 8 その2）。

  floor3_upper.png / floor3_lower.png   上の層（床 26）・下の層（床 14）を上から見た図。区画・水の区画・水門のハンドル・
                                        中継の灯・宝箱・守り手・湧く所・封印・ボスの部屋
  floor3_<所>.png                       バニラのテクスチャで描いた絵（天井を切り取って、斜め上から。mapgen/render.py の Scene）
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor_figures import COLORS
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = {
    "deepslate_tiles": (54, 54, 56), "cracked_deepslate_tiles": (52, 52, 54), "polished_deepslate": (72, 72, 74),
    "deepslate_bricks": (70, 70, 72), "cracked_deepslate_bricks": (66, 66, 68), "chiseled_deepslate": (60, 60, 62),
    "smooth_stone": (158, 158, 158), "prismarine_bricks": (99, 171, 158), "dark_prismarine": (51, 91, 75),
    "waxed_oxidized_copper": (82, 162, 132), "waxed_oxidized_cut_copper": (80, 154, 128), "waxed_weathered_copper": (108, 153, 110),
    "waxed_weathered_cut_copper": (109, 145, 107), "spruce_planks": (114, 84, 48), "stripped_spruce_log": (115, 89, 52),
    "stripped_spruce_wood": (115, 89, 52), "stripped_dark_oak_log": (96, 76, 49), "bricks": (150, 97, 83),
    "light_blue_stained_glass": (102, 153, 216), "dark_oak_planks": (66, 43, 20), "lectern": (160, 120, 70),
    "barrel": (130, 90, 50), "cartography_table": (100, 80, 60), "chiseled_quartz_block": (231, 226, 218), "smooth_quartz": (235, 229, 222),
    "amemaru:lost_blank": (250, 250, 252), "amemaru:floor_seal": (255, 230, 120), "amemaru:tower_chest": (255, 190, 40),
    "amemaru:waypoint": (255, 255, 120), "amemaru:travel_stone": (120, 255, 255), "amemaru:floodgate_valve": (255, 120, 60),
    "soul_lantern": (100, 220, 230), "chain": (60, 60, 70), "amethyst_cluster": (180, 120, 220), "lightning_rod": (200, 120, 90),
    "spruce_slab": (114, 84, 48), "spruce_sign": (114, 84, 48), "dark_oak_wall_sign": (66, 43, 20),
    "deepslate_brick_stairs": (70, 70, 72), "stone_brick_stairs": (122, 121, 122), "stone_brick_slab": (122, 121, 122),
    "andesite_wall": (136, 136, 136),
}
SWAP = {"amemaru:lost_blank": block("white_concrete"), "amemaru:floor_seal": block("yellow_stained_glass"),
        "amemaru:tower_chest": block("barrel", facing="up", open=False), "amemaru:waypoint": block("sea_lantern"),
        "amemaru:travel_stone": block("chiseled_quartz_block"), "amemaru:floodgate_valve": block("lodestone")}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    name = state[0]
    c = MORE.get(name) or COLORS.get(name)
    if c is None:
        base = name.replace("_stairs", "").replace("_slab", "").replace("_wall", "")
        c = MORE.get(base) or COLORS.get(base) or (200, 0, 200)
    return c


def plan(f, data, path, y_lo, y_hi, title, scale=2):
    """y_hi から下へ、はじめの掘った所の床（固いブロックか水）の色。掘った所がない列は暗い色"""
    n = f.v.shape[0]
    carved = f.carved[:, y_lo:y_hi + 1, :]
    v = f.v[:, y_lo:y_hi + 1, :]
    rgb = np.zeros((n, n, 3), dtype=float)
    height = np.full((n, n), -1)
    pal = np.array([color_of(s) for s in f.states], dtype=float)
    water_i = f.index.get(block("water", level=0), -1)
    for yy in range(v.shape[1] - 1, -1, -1):
        open_here = carved[:, yy, :] & (v[:, yy, :] == 0)
        below_solid = (v[:, yy - 1, :] != 0) if yy > 0 else np.zeros((n, n), dtype=bool)
        hit = (height < 0) & open_here & below_solid
        idx = v[:, yy - 1, :] if yy > 0 else v[:, 0, :]
        rgb[hit] = pal[idx[hit]]
        height[hit] = yy + y_lo - 1
    # 水だけの列（上から水）
    wcol = (height < 0) & (v == water_i).any(axis=1)
    rgb[wcol] = (40, 90, 200)
    height[wcol] = y_lo
    rgb[height < 0] = (22, 24, 28)
    shade = 0.75 + 0.25 * np.clip((height - y_lo) / max(1, (y_hi - y_lo)), 0, 1)
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)
    for sec in data["water_sections"]:
        x0, _, z0, x1, _, z1 = sec["box"]
        dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(90, 200, 255), width=2)
        dr.text((x0 * scale + 4, z0 * scale + 2), "水：" + sec["id"], fill=(140, 220, 255), font=font)
    colors = {"first": (255, 120, 60), "deep": (255, 200, 60), "moat": (255, 90, 160), "drain": (120, 255, 160)}
    for g in data["floodgates"]:
        for k, (x, y, z) in enumerate(g["valves"]):
            if not (y_lo - 2 <= y <= y_hi + 2):
                continue
            dr.rectangle(((x - 3) * scale, (z - 3) * scale, (x + 3) * scale, (z + 3) * scale), outline=colors[g["id"]], width=3)
            dr.text(((x + 4) * scale, (z - 4) * scale), g["name"] + "（%d）" % (k + 1), fill=colors[g["id"]], font=font)
        for door in g.get("doors", []):
            x0, y0, z0, x1, y1, z1 = door["box"]
            if y_lo - 2 <= y0 <= y_hi + 2:
                dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), fill=(255, 200, 60))
    for wp in data["waypoints"]:
        x, y, z = wp["pos"]
        if y_lo - 2 <= y <= y_hi + 2:
            dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 255, 0), width=3)
            dr.text(((x + 5) * scale, (z + 2) * scale), wp["name"], fill=(255, 255, 120), font=font)
    for c in data["chests"]:
        x, y, z = c["pos"]
        if y_lo - 2 <= y <= y_hi + 2:
            dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                         outline=(255, 60, 60) if c.get("guardian") else (255, 170, 0), width=2)
    ecol = {"suimon_nezumi": (200, 180, 160), "yodomi_slime": (120, 220, 160), "soujikihei": (230, 170, 90), "kiba_namazu": (120, 160, 255)}
    for zn in data["spawns"]:
        cx, cy, cz = zn["center"]
        if not (y_lo - 4 <= cy <= y_hi + 2):
            continue
        r = zn["radius"]
        col = ecol.get(zn["enemy"].split(":")[1], (255, 0, 255))
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=col, width=1)
        for p in zn.get("route", []):
            dr.rectangle(((p[0] - 1) * scale, (p[2] - 1) * scale, (p[0] + 1) * scale, (p[2] + 1) * scale), outline=col)
    for g in data["guardians"]:
        x, y, z = g["pos"]
        if y_lo - 10 <= y <= y_hi + 2:
            dr.rectangle(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 0, 0), width=3)
            dr.text(((x + 5) * scale, (z - 10) * scale), g["id"], fill=(255, 100, 100), font=font)
    b = data["boss_room"]
    cx, cy, cz = b["arena"]["center"]
    if y_lo - 2 <= cy <= y_hi + 2:
        r = b["arena"]["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
        (sx0, sy, sz0), (sx1, _, sz1) = b["seal"]
        dr.rectangle((sx0 * scale, sz0 * scale, (sx1 + 1) * scale, (sz1 + 1) * scale), fill=(255, 230, 120))
    for zz in data.get("story_zones", []):
        x0, y0, z0, x1, y1, z1 = zz["box"]
        if y0 <= y_hi and y1 >= y_lo:
            dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(200, 160, 255), width=2)
    dr.text((10, 8), title, fill=(255, 255, 255), font=big)
    img.save(path)
    return path


def sub_world(f, x0, y0, z0, x1, y1, z1, drain=()):
    """範囲を切り出した World（Modのブロックは、近い色のバニラのブロックに置きかえる。drain の水の区画は水を抜く）"""
    w = World(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    v = f.v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1].copy()
    water = f.index.get(block("water", level=0), -1)
    for sec in f.water_sections:
        if sec["id"] in drain:
            bx0, low, bz0, bx1, high, bz1 = sec["box"]
            for x in range(max(bx0, x0), min(bx1, x1) + 1):
                for z in range(max(bz0, z0), min(bz1, z1) + 1):
                    for y in range(max(low + 1, y0), min(high, y1) + 1):
                        if v[x - x0, y - y0, z - z0] == water:
                            v[x - x0, y - y0, z - z0] = 0
    xs, ys, zs = np.nonzero(v)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        st = f.states[v[x, y, z]]
        if st is None:
            continue
        st = SWAP.get(st[0], st)
        if st[0] == "water":
            st = block("water", level=0)
        w.blocks[(x, y, z)] = st
    return w


VIEWS = [
    # (名前, 範囲 (x0, y0, z0, x1, y1, z1)、目、見る所、水を抜く区画)。天井を切り取る高さ（y1）は、天井の板の下
    ("dock", (206, 16, 396, 270, 31, 476), (262, 46, 476), (238, 26, 446), ()),
    ("canal", (214, 16, 296, 266, 31, 420), (262, 46, 420), (240, 25, 336), ()),
    ("hub", (202, 16, 226, 278, 32, 300), (270, 52, 300), (240, 26, 258), ()),
    ("cistern", (26, 16, 186, 150, 36, 334), (150, 56, 334), (88, 26, 250), ()),
    ("east", (330, 16, 196, 456, 37, 324), (330, 58, 324), (395, 26, 262), ()),
    ("sunken_flooded", (150, 4, 100, 330, 34, 240), (240, 62, 252), (240, 16, 160), ()),
    ("sunken_drained", (150, 4, 100, 330, 34, 240), (240, 62, 252), (240, 16, 160), ("sunken", "sunken_ne")),
    ("white", (20, 8, 20, 114, 27, 136), (112, 46, 136), (40, 15, 66), ("white_channel",)),
    ("gallery_flooded", (146, 10, 66, 334, 33, 110), (240, 52, 114), (240, 22, 82), ()),
    ("gallery_drained", (146, 10, 66, 334, 33, 110), (240, 52, 114), (240, 18, 82), ("moat",)),
    ("arena", (208, 10, 4, 272, 23, 76), (240, 46, 80), (240, 15, 32), ()),
    ("warren", (326, 20, 50, 470, 31, 204), (470, 52, 204), (410, 26, 110), ()),
]


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", plan(f, data, os.path.join(out, "floor3_upper.png"), 20, 40, "3階　上の層（床 26）"))
    print("描きました:", plan(f, data, os.path.join(out, "floor3_lower.png"), 4, 25, "3階　下の層（床 14）"))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target, drain in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1, drain)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor3_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(120.0, 400.0, 0.3), ground_fog=(120.0, 400.0, 0.3))
        print("描きました:", path)
