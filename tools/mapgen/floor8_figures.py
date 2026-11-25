"""塔の8階の見本の絵（build_floor8.py の --render から呼ぶ。11月25日【補完】）。

  floor8_map.png      上から見た地図（いちばん上のブロックの色と陰。湖の鐘・入江の鐘・歌の碑・薄氷（水色の斜線）・氷の下の影・
                      湖底の祠と湖底の道（点線）・宝箱・中継の灯・守り手・湧く所・封印・湖心）
  floor8_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。湖の氷・薄氷・鐘・歌の碑・石板は、似たバニラのブロックで
                      代わりに描く）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor7_figures import MORE as MORE7, SWAP as SWAP7, color_of as color7
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = dict(MORE7)
MORE.update({
    "amemaru:lake_ice": (150, 188, 222), "amemaru:thin_ice": (118, 176, 214), "water": (44, 70, 120),
    "black_concrete": (12, 14, 20), "prismarine_bricks": (99, 171, 158), "dark_prismarine": (51, 91, 75),
    "amemaru:lake_bell": (240, 200, 70), "amemaru:song_stele": (250, 250, 240), "amemaru:lake_tablet": (120, 130, 140),
    "chain": (60, 64, 76), "white_stained_glass_pane": (232, 240, 248), "stripped_birch_log": (196, 176, 118),
    "amethyst_block": (133, 97, 191), "amethyst_cluster": (163, 126, 220), "deepslate": (80, 80, 82),
    "polished_deepslate": (72, 72, 74), "mud": (60, 57, 61), "clay": (160, 166, 179), "mossy_stone_bricks": (115, 121, 105),
    "cracked_stone_bricks": (118, 117, 118), "smooth_quartz": (235, 229, 222), "quartz_bricks": (234, 229, 221),
    "quartz_slab": (235, 229, 222), "diorite": (188, 188, 188), "soul_lantern": (90, 210, 220),
})
SWAP = dict(SWAP7)
SWAP.update({"amemaru:lake_ice": block("ice"), "amemaru:thin_ice": block("light_blue_stained_glass"),
             "amemaru:lake_bell": block("gold_block"), "amemaru:song_stele": block("quartz_block"),
             "amemaru:lake_tablet": block("polished_deepslate")})
SKIP = {"light", "spruce_sign", "lantern", "soul_lantern", "snow"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    c = MORE.get(state[0])
    return c if c is not None else color7(state)


def top_map(f, data, path, y_max, scale=2):
    import build_floor8 as B
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
    # 氷の下の影は、氷ごしに暗く見える
    rgb[f.shadow & (top == B.GROUND)] *= 0.55
    shade = 0.75 + 0.25 * np.clip((top - 8) / 40.0, 0, 1.2)
    d = np.zeros((n, n))
    d[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade = shade * (1 + np.clip(d * 0.08, -0.3, 0.3))
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)
    # 薄氷（斜線）
    xs, zs = np.nonzero(f.thin)
    for x, z in zip(xs.tolist(), zs.tolist()):
        if (x + z) % 4 == 0:
            dr.point(((x + 0.5) * scale, (z + 0.5) * scale), fill=(255, 255, 255))
    ecol = {"silent_fish": (40, 90, 200), "voice_eater": (200, 60, 200), "frost_crane": (40, 170, 90), "fog_wraith": (230, 120, 40)}
    for zn in data["spawns"]:
        cx, _, cz = zn["center"]
        r = zn["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale),
                   outline=ecol.get(zn["enemy"].split(":")[1], (255, 0, 255)), width=2)
    # 鐘と歌の碑
    for g in data["bells"]:
        sx, _, sz = g["stele"]
        dr.rectangle(((sx - 3) * scale, (sz - 3) * scale, (sx + 3) * scale, (sz + 3) * scale), fill=(255, 255, 255), outline=(0, 0, 0))
        dr.text(((sx + 5) * scale, (sz - 9) * scale), g["name"] + "の碑", fill=(30, 30, 30), font=font)
        for b in g["bells"]:
            x, _, z = b["pos"]
            dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), fill=(255, 205, 40), outline=(90, 60, 0), width=2)
            dr.text(((x + 5) * scale, (z + 3) * scale), b["name"], fill=(90, 60, 0), font=font)
        for r in g.get("raises", []):
            x0, _, z0, x1, _, z1 = r["box"]
            dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), outline=(255, 140, 0), width=2)
        for o in g.get("opens", []):
            x0, _, z0, x1, _, z1 = o["box"]
            dr.rectangle(((x0 - 1) * scale, (z0 - 1) * scale, (x1 + 2) * scale, (z1 + 2) * scale), fill=(60, 120, 255))
    for x, _, z in data["boss_marks"]["arena_bells"]:
        dr.ellipse(((x - 3) * scale, (z - 3) * scale, (x + 3) * scale, (z + 3) * scale), fill=(255, 205, 40), outline=(90, 60, 0), width=2)
    # 湖底の祠と湖底の道（地面の下。点線）
    for z in range(192, 262, 3):
        dr.rectangle((359 * scale, z * scale, 362 * scale, (z + 1) * scale), fill=(255, 255, 255))
    dr.rectangle((347 * scale, 262 * scale, 374 * scale, 285 * scale), outline=(255, 255, 255), width=2)
    dr.text((376 * scale, 266 * scale), "湖底の祠（下）", fill=(255, 255, 255), font=font)
    for wp in data["waypoints"]:
        x, _, z = wp["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(230, 200, 0), width=3)
        dr.text(((x + 5) * scale, (z + 2) * scale), wp["name"], fill=(255, 230, 120), font=font)
    for c in data["chests"]:
        x, _, z = c["pos"]
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                     outline=(255, 60, 60) if c.get("guardian") else (255, 150, 0), width=2)
    for g in data["guardians"]:
        x, _, z = g["pos"]
        dr.rectangle(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 0, 0), width=3)
        dr.text(((x + 5) * scale, (z - 10) * scale), g["id"], fill=(200, 0, 0), font=font)
    b = data["boss_room"]
    cx, _, cz = b["arena"]["center"]
    r = b["arena"]["radius"]
    dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
    (sx0, _, sz0), (sx1, _, sz1) = b["seal"]
    dr.rectangle((sx0 * scale, (sz0 - 1) * scale, (sx1 + 1) * scale, (sz1 + 2) * scale), fill=(255, 200, 40))
    x, _, z = data["tablet"]["pos"]
    dr.text(((x + 16) * scale, (z - 2) * scale), "湖底の祠の石板", fill=(255, 255, 255), font=font)
    dr.text((10, 8), "8階　沈黙の湖（上から）", fill=(230, 240, 255), font=big)
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
        w.blocks[(x, y, z)] = SWAP.get(st[0], st)
    return w


VIEWS = [
    ("entrance", (290, 4, 590, 430, 60, 712), (360, 36, 702), (360, 22, 630)),
    ("cove", (250, 4, 610, 360, 50, 700), (330, 36, 690), (300, 22, 648)),
    ("lake", (250, 4, 380, 470, 60, 610), (360, 44, 600), (360, 20, 470)),
    ("stele", (320, 4, 400, 400, 50, 470), (360, 34, 462), (360, 28, 428)),
    ("shrine_top", (320, 4, 260, 400, 50, 330), (360, 40, 322), (362, 22, 292)),
    ("shrine_hall", (344, 4, 258, 376, 30, 300), (360, 13, 281), (360, 10, 264)),
    ("arena", (300, 4, 60, 420, 70, 196), (360, 34, 172), (360, 22, 112)),
]


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor8_map.png"), f.v.shape[1] - 1))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor8_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(120.0, 380.0, 0.25),
                  ground_fog=(120.0, 380.0, 0.25))
        print("描きました:", path)
