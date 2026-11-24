"""塔の4階の見本の絵（build_floor4.py の --render から呼ぶ。11月24日【補完】）。

  floor4_map.png      上から見た地図（いちばん上のブロックの色と陰。区画・壁画・音の順番・遺品・中継の灯・宝箱・守り手・湧く所・封印・ボスの部屋）
  floor4_crypt.png    地下の納骨堂（床 10）を上から見た図
  floor4_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。屋根のある所は、屋根を切り取って斜め上から）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor3_figures import MORE as MORE3, color_of as color3
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = dict(MORE3)
MORE.update({
    "calcite": (223, 224, 220), "polished_diorite": (192, 193, 194), "diorite": (188, 188, 188), "quartz_pillar": (236, 230, 223),
    "quartz_bricks": (234, 229, 221), "smooth_sandstone": (223, 214, 170), "cut_sandstone": (217, 206, 159),
    "amethyst_block": (133, 97, 191), "polished_andesite": (132, 134, 133), "azalea_leaves": (92, 124, 46),
    "moss_block": (89, 109, 45), "rooted_dirt": (144, 104, 76), "coarse_dirt": (119, 85, 59), "gravel": (131, 127, 126),
    "mossy_cobblestone": (110, 118, 94), "mossy_stone_bricks": (115, 121, 105), "cracked_stone_bricks": (118, 117, 118),
    "chiseled_stone_bricks": (119, 118, 119), "gold_block": (246, 208, 61), "white_wool": (234, 236, 236),
    "white_glazed_terracotta": (188, 212, 202), "light_blue_glazed_terracotta": (94, 164, 208),
    "yellow_glazed_terracotta": (234, 192, 88), "red_glazed_terracotta": (181, 59, 53), "birch_stairs": (192, 175, 121),
    "oak_fence": (162, 130, 78), "bell": (222, 176, 60),
    "amemaru:mural_panel": (230, 120, 255), "amemaru:sound_plate": (120, 220, 255), "amemaru:chime_stone": (180, 140, 255),
    "amemaru:knight_relic": (255, 80, 80), "amemaru:next_circle": (140, 255, 200), "deepslate": (80, 80, 82),
    "birch_leaves": (96, 140, 60), "flowering_azalea_leaves": (120, 120, 80), "birch_log": (216, 215, 210), "oak_log": (109, 85, 50),
    "dirt_path": (148, 122, 65), "cobblestone": (127, 127, 127), "andesite": (136, 136, 136), "stone_brick": (122, 121, 122),
})
SWAP = {"amemaru:lost_blank": block("white_concrete"), "amemaru:floor_seal": block("yellow_stained_glass"),
        "amemaru:tower_chest": block("barrel", facing="up", open=False), "amemaru:waypoint": block("sea_lantern"),
        "amemaru:travel_stone": block("chiseled_quartz_block"), "amemaru:mural_panel": block("light_blue_glazed_terracotta", facing="north"),
        "amemaru:sound_plate": block("light_weighted_pressure_plate", power=0), "amemaru:chime_stone": block("amethyst_block"),
        "amemaru:knight_relic": block("iron_block")}
SKIP_TOP = {"light", "grass", "fern", "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "moss_carpet", "birch_sign",
            "white_carpet", "lantern", "soul_lantern"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    name = state[0]
    c = MORE.get(name)
    if c is None:
        base = name.replace("_stairs", "").replace("_slab", "").replace("_wall", "")
        c = MORE.get(base)
    return c if c is not None else color3(state)


def _marks(dr, data, scale, font, y_lo, y_hi):
    inside = lambda y: y_lo - 3 <= y <= y_hi + 3  # noqa: E731
    mcol = {"first": (255, 150, 60), "sunder": (255, 90, 160), "second": (120, 255, 160), "whole": (255, 255, 255)}
    for m in data["murals"]:
        pts = [p["pos"] for p in m["panels"] if inside(p["pos"][1])]
        if not pts:
            continue
        xs = [p[0] for p in pts]
        zs = [p[2] for p in pts]
        col = mcol.get(m["id"], (230, 120, 255))
        dr.rectangle(((min(xs) - 1) * scale, (min(zs) - 1) * scale, (max(xs) + 2) * scale, (max(zs) + 2) * scale), outline=col, width=3)
        dr.text(((max(xs) + 3) * scale, (min(zs) - 8) * scale), m["name"], fill=col, font=font)
        door = m.get("door")
        if door:
            x0, _, z0, x1, _, z1 = door["box"]
            dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), fill=col)
    for s in data["songs"]:
        x, y, z = s["chime"]
        if inside(y):
            dr.ellipse(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), fill=(180, 140, 255))
            dr.text(((x + 4) * scale, (z + 2) * scale), s["name"], fill=(200, 170, 255), font=font)
            for (px, _, pz) in s["plates"]:
                dr.rectangle((px * scale, pz * scale, (px + 1) * scale, (pz + 1) * scale), fill=(120, 220, 255))
            x0, _, z0, x1, _, z1 = s["door"]["box"]
            dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), fill=(180, 140, 255))
    for r in data["relics"]:
        x, y, z = r["pos"]
        if inside(y):
            dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), fill=(255, 80, 80))
            dr.text(((x + 4) * scale, (z - 4) * scale), "騎士団の遺品", fill=(255, 140, 140), font=font)
    for wp in data["waypoints"]:
        x, y, z = wp["pos"]
        if inside(y):
            dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 255, 0), width=3)
            dr.text(((x + 5) * scale, (z + 2) * scale), wp["name"], fill=(255, 255, 120), font=font)
    for c in data["chests"]:
        x, y, z = c["pos"]
        if inside(y):
            dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                         outline=(255, 60, 60) if c.get("guardian") else (255, 170, 0), width=2)
    ecol = {"silent_statue": (200, 200, 210), "prayer_echo": (150, 220, 255), "crystal_beetle": (190, 120, 255),
            "broken_beast": (255, 180, 90)}
    for zn in data["spawns"]:
        cx, cy, cz = zn["center"]
        if not inside(cy):
            continue
        r = zn["radius"]
        col = ecol.get(zn["enemy"].split(":")[1], (255, 0, 255))
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=col, width=1)
    for g in data["guardians"]:
        x, y, z = g["pos"]
        if inside(y):
            dr.rectangle(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 0, 0), width=3)
            dr.text(((x + 5) * scale, (z - 10) * scale), g["id"], fill=(255, 100, 100), font=font)
    b = data["boss_room"]
    cx, cy, cz = b["arena"]["center"]
    if inside(cy):
        r = b["arena"]["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
        (sx0, _, sz0), (sx1, _, sz1) = b["seal"]
        dr.rectangle((sx0 * scale, sz0 * scale, (sx1 + 1) * scale, (sz1 + 1) * scale), fill=(255, 230, 120))
        for key, col in (("exit", (255, 255, 255)), ("next", (140, 255, 200))):
            x, _, z = b[key]["center"]
            dr.ellipse(((x - 3) * scale, (z - 3) * scale, (x + 3) * scale, (z + 3) * scale), outline=col, width=3)
        for (px, _, pz) in data["boss_marks"]["sound_plates"]:
            dr.rectangle(((px - 1) * scale, (pz - 1) * scale, (px + 2) * scale, (pz + 2) * scale), outline=(120, 220, 255), width=2)
    for zz in data.get("story_zones", []):
        x0, y0, z0, x1, y1, z1 = zz["box"]
        if y0 <= y_hi and y1 >= y_lo:
            dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(200, 160, 255), width=2)


def top_map(f, data, path, scale=2):
    n = f.v.shape[0]
    skip = np.array([s is None or s[0] in SKIP_TOP for s in f.states], dtype=bool)
    solid = ~skip[f.v]
    h = f.v.shape[1]
    rev = solid[:, ::-1, :]
    has = rev.any(axis=1)
    top = np.where(has, h - 1 - rev.argmax(axis=1), 0)
    xi, zi = np.meshgrid(np.arange(n), np.arange(n), indexing="ij")
    idx = f.v[xi, top, zi]
    pal = np.array([color_of(s) for s in f.states], dtype=float)
    rgb = pal[idx]
    rgb[~has] = (22, 24, 28)
    shade = np.ones((n, n))
    d = np.zeros((n, n))
    d[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade += np.clip(d * 0.1, -0.35, 0.35)
    shade *= 0.7 + 0.3 * np.clip(top / 50.0, 0, 1)
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)
    _marks(dr, data, scale, font, 4, 72)
    dr.text((10, 8), "4階　崩壊した神殿（上から）", fill=(255, 255, 255), font=big)
    img.save(path)
    return path


def lower_plan(f, data, path, y_lo, y_hi, x0, z0, x1, z1, title, scale=4):
    carved = f.carved[x0:x1 + 1, y_lo:y_hi + 1, z0:z1 + 1]
    v = f.v[x0:x1 + 1, y_lo:y_hi + 1, z0:z1 + 1]
    nx, nz = v.shape[0], v.shape[2]
    rgb = np.zeros((nx, nz, 3), dtype=float)
    height = np.full((nx, nz), -1)
    pal = np.array([color_of(s) for s in f.states], dtype=float)
    for yy in range(v.shape[1] - 1, 0, -1):
        open_here = carved[:, yy, :] & (v[:, yy, :] == 0)
        below = v[:, yy - 1, :] != 0
        hit = (height < 0) & open_here & below
        rgb[hit] = pal[v[:, yy - 1, :][hit]]
        height[hit] = yy
    rgb[height < 0] = (22, 24, 28)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((nx * scale, nz * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)

    class Shift:
        def __init__(self, d):
            self.d = d

        def rectangle(self, box, **kw):
            self.d.rectangle(self._s(box), **kw)

        def ellipse(self, box, **kw):
            self.d.ellipse(self._s(box), **kw)

        def text(self, xy, t, **kw):
            self.d.text((xy[0] - x0 * scale, xy[1] - z0 * scale), t, **kw)

        def _s(self, box):
            a, b, c, e = box
            return (a - x0 * scale, b - z0 * scale, c - x0 * scale, e - z0 * scale)

    _marks(Shift(dr), data, scale, font, y_lo, y_hi)
    dr.text((10, 8), title, fill=(255, 255, 255), font=big)
    img.save(path)
    return path


def sub_world(f, x0, y0, z0, x1, y1, z1):
    w = World(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    v = f.v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    xs, ys, zs = np.nonzero(v)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        st = f.states[v[x, y, z]]
        if st is None or st[0] == "light":
            continue
        w.blocks[(x, y, z)] = SWAP.get(st[0], st)
    return w


VIEWS = [
    # (名前, 範囲 (x0, y0, z0, x1, y1, z1)、目、見る所)。屋根のある所は y1 で屋根を切る
    ("entrance", (196, 14, 300, 332, 48, 527), (264, 52, 527), (264, 27, 430)),
    ("plaza", (200, 14, 190, 330, 50, 320), (312, 60, 322), (262, 27, 248)),
    ("gallery", (300, 14, 196, 478, 32, 280), (372, 52, 300), (430, 26, 236)),
    ("chapel", (360, 14, 90, 482, 35, 222), (420, 62, 226), (420, 26, 130)),
    ("float", (24, 0, 220, 210, 64, 412), (206, 62, 418), (110, 22, 300)),
    ("terrace", (30, 20, 16, 222, 56, 240), (128, 74, 246), (126, 38, 100)),
    ("crypt", (226, 6, 296, 304, 15, 432), (265, 40, 440), (265, 11, 352)),
    ("tower", (352, 14, 322, 474, 54, 446), (354, 64, 446), (406, 32, 376)),
    ("arena", (226, 14, 36, 302, 46, 150), (264, 62, 152), (264, 26, 72)),
    ("ante", (236, 14, 100, 292, 34, 152), (248, 46, 154), (284, 27, 124)),
]


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor4_map.png")))
    print("描きました:", lower_plan(f, data, os.path.join(out, "floor4_crypt_plan.png"), 6, 18, 196, 270, 380, 432,
                                   "4階　地下の納骨堂（床 10）"))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor4_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(140.0, 420.0, 0.25), ground_fog=(140.0, 420.0, 0.25))
        print("描きました:", path)
