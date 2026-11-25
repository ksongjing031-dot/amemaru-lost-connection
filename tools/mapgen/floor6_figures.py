"""塔の6階の見本の絵（build_floor6.py の --render から呼ぶ。11月25日【補完】）。

  floor6_map.png      上から見た地図（いちばん上のブロックの色と陰。記憶の氷像・熱の炉・宝箱・中継の灯・守り手・湧く所・封印・
                      ボスの広場・記憶の結晶の台）
  floor6_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。氷像は氷の柱で代わりに描く）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor5_figures import MORE as MORE5, SWAP as SWAP5, color_of as color5
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = dict(MORE5)
MORE.update({
    "snow_block": (240, 246, 250), "snow": (246, 250, 252), "ice": (150, 190, 245), "packed_ice": (140, 175, 235),
    "blue_ice": (110, 160, 240), "spruce_planks": (114, 84, 49), "spruce_log": (58, 37, 16), "stripped_spruce_log": (115, 89, 52),
    "spruce_leaves": (70, 98, 70), "dark_oak_planks": (66, 43, 20), "deepslate_tiles": (54, 54, 56), "cobblestone": (122, 122, 122),
    "stone_bricks": (122, 121, 122), "gravel": (131, 127, 126), "coarse_dirt": (119, 85, 59), "packed_mud": (142, 106, 79),
    "bricks": (150, 97, 83), "red_terracotta": (143, 61, 46), "hay_block": (166, 136, 38), "red_wool": (160, 39, 34),
    "yellow_wool": (248, 197, 39), "blue_wool": (53, 57, 157), "green_wool": (84, 109, 27), "orange_wool": (240, 118, 19),
    "amemaru:village_statue": (190, 235, 255), "amemaru:village_hearth": (255, 120, 40), "amemaru:floor_seal": (255, 230, 120),
    "polished_diorite": (192, 193, 194), "polished_andesite": (132, 134, 133),
})
SWAP = dict(SWAP5)
SWAP.update({"amemaru:village_hearth": block("furnace", facing="south", lit=True),
             "amemaru:village_statue": block("packed_ice"), "amemaru:floor_seal": block("light_blue_stained_glass"),
             "snow_block": block("white_concrete")})
SKIP = {"light", "spruce_sign", "spruce_wall_sign", "lantern", "snow"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    c = MORE.get(state[0])
    return c if c is not None else color5(state)


def top_map(f, data, path, y_max, scale=2):
    n = f.v.shape[0]
    skip = np.array([s is None or s[0] in SKIP for s in f.states], dtype=bool)
    solid = ~skip[f.v[:, :y_max, :]]
    rev = solid[:, ::-1, :]
    has = rev.any(axis=1)
    top = np.where(has, y_max - 1 - rev.argmax(axis=1), 0)
    xi, zi = np.meshgrid(np.arange(n), np.arange(n), indexing="ij")
    idx = f.v[xi, top, zi]
    pal = np.array([color_of(s) for s in f.states], dtype=float)
    rgb = pal[idx]
    rgb[~has] = (10, 12, 16)
    shade = 0.75 + 0.25 * np.clip((top - 8) / 40.0, 0, 1.2)
    d = np.zeros((n, n))
    d[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade = shade * (1 + np.clip(d * 0.08, -0.3, 0.3))
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)
    ecol = {"frost_wolf": (90, 160, 255), "statue_echo": (0, 200, 220), "frost_bird": (200, 120, 255), "frost_bear": (255, 120, 60)}
    for zn in data["spawns"]:
        cx, _, cz = zn["center"]
        r = zn["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale),
                   outline=ecol.get(zn["enemy"].split(":")[1], (255, 0, 255)), width=2)
    for wp in data["waypoints"]:
        x, _, z = wp["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(230, 200, 0), width=3)
        dr.text(((x + 5) * scale, (z + 2) * scale), wp["name"], fill=(120, 90, 0), font=font)
    for c in data["chests"]:
        x, _, z = c["pos"]
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                     outline=(255, 60, 60) if c.get("guardian") else (255, 150, 0), width=2)
    for g in data["guardians"]:
        x, _, z = g["pos"]
        dr.rectangle(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 0, 0), width=3)
        dr.text(((x + 5) * scale, (z - 10) * scale), g["id"], fill=(200, 0, 0), font=font)
    for m in data["memory_statues"]:
        x, _, z = m["pos"]
        dr.ellipse(((x - 5) * scale, (z - 5) * scale, (x + 5) * scale, (z + 5) * scale), outline=(0, 120, 255), width=4)
        dr.text(((x + 6) * scale, (z - 6) * scale), "記憶：" + m["name"], fill=(0, 70, 200), font=font)
    for h in data["hearths"]:
        x, _, z = h["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), fill=(255, 110, 30))
        dr.text(((x + 5) * scale, (z + 2) * scale), h["name"], fill=(200, 60, 0), font=font)
    b = data["boss_room"]
    cx, _, cz = b["arena"]["center"]
    r = b["arena"]["radius"]
    dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
    (sx0, _, sz0), (sx1, _, sz1) = b["seal"]
    dr.rectangle((sx0 * scale, (sz0 - 1) * scale, (sx1 + 1) * scale, (sz1 + 2) * scale), fill=(255, 200, 40))
    for (x, _, z) in data["boss_marks"]["crystal_spots"]:
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), outline=(255, 120, 220), width=3)
    x, _, z = data["tablet"]["pos"]
    dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), outline=(120, 60, 0), width=3)
    dr.text(((x + 4) * scale, (z + 3) * scale), "日記", fill=(120, 60, 0), font=font)
    dr.text((10, 8), "6階　凍結した村（上から）", fill=(20, 30, 60), font=big)
    img.save(path)
    return path


def sub_world(f, x0, y0, z0, x1, y1, z1):
    w = World(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    v = f.v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    xs, ys, zs = np.nonzero(v)
    for x, y, z in zip(xs.tolist(), ys.tolist(), zs.tolist()):
        st = f.states[v[x, y, z]]
        if st is None or st[0] in ("light", "snow"):
            continue
        if st[0] == "amemaru:village_statue":
            w.blocks[(x, y, z)] = block("packed_ice")
            w.blocks[(x, y + 1, z)] = block("light_blue_stained_glass")
            continue
        w.blocks[(x, y, z)] = SWAP.get(st[0], st)
    return w


VIEWS = [
    ("gate", (250, 4, 470, 380, 50, 610), (312, 34, 606), (312, 18, 520)),
    ("market", (260, 4, 340, 370, 50, 450), (312, 36, 446), (312, 18, 388)),
    ("west", (120, 4, 300, 280, 50, 440), (262, 36, 436), (190, 18, 360)),
    ("chapel", (170, 4, 200, 290, 60, 300), (270, 34, 300), (210, 22, 232)),
    ("plaza", (250, 4, 50, 380, 60, 190), (312, 40, 186), (312, 18, 96)),
    ("pasture", (420, 4, 280, 540, 50, 400), (430, 34, 398), (480, 18, 330)),
]


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor6_map.png"), 79))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor6_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(160.0, 420.0, 0.2), ground_fog=(160.0, 420.0, 0.2))
        print("描きました:", path)
