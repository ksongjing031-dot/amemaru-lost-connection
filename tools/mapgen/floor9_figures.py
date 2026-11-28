"""塔の9階の見本の絵（build_floor9.py の --render から呼ぶ。11月28日【補完】）。

  floor9_map.png      上から見た地図（いちばん上のブロックの色と陰。肖像画・名前の札の台・手がかり・宝箱・中継の灯・守り手・湧く所・封印・
                      近衛の間）
  floor9_plan1.png    本館の1階の平面（y39 で切った図。部屋と家具・肖像画・札の台）
  floor9_plan2.png    本館の2階の平面（y49 で切った図。礼拝堂・書庫・前廊・肖像の回廊・女王の書斎）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor8_figures import color_of as color8

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = {
    "amemaru:royal_portrait": (220, 180, 60), "amemaru:name_plate_stand": (250, 230, 120), "amemaru:lineage_stele": (250, 250, 250),
    "amemaru:lineage_book": (160, 110, 60), "amemaru:queen_letter": (255, 240, 200), "amemaru:study_latch": (160, 130, 90),
    "amemaru:village_statue": (205, 228, 242), "amemaru:waypoint": (120, 230, 255), "amemaru:tower_chest": (190, 140, 60),
    "amemaru:floor_seal": (255, 80, 200), "deepslate_tiles": (60, 62, 70), "deepslate_bricks": (72, 72, 76),
    "calcite": (224, 226, 220), "lapis_block": (36, 70, 150), "blue_carpet": (50, 60, 160), "light_blue_carpet": (90, 170, 220),
    "white_carpet": (236, 236, 236), "spruce_leaves": (60, 90, 60), "hay_block": (170, 150, 40), "target": (220, 200, 180),
    "coarse_dirt": (110, 80, 56), "bookshelf": (110, 80, 50), "chiseled_bookshelf": (120, 90, 60), "dark_oak_planks": (66, 44, 22),
    "light_blue_stained_glass": (120, 180, 230), "blue_stained_glass": (60, 80, 200), "white_stained_glass": (240, 240, 240),
    "end_rod": (250, 250, 240), "blue_wool": (50, 60, 160), "light_blue_wool": (90, 170, 220),
    "spruce_stairs": (114, 84, 48), "dark_oak_stairs": (66, 44, 22), "cobweb": (220, 220, 220), "white_tulip": (236, 240, 230),
    "lily_of_the_valley": (236, 240, 230), "spruce_fence": (114, 84, 48), "dark_oak_fence": (66, 44, 22), "brown_wool": (114, 72, 40),
    "grindstone": (140, 140, 140), "iron_bars": (110, 110, 110), "water_cauldron": (60, 90, 160), "spruce_trapdoor": (114, 84, 48),
}
SKIP = {"light", "spruce_sign", "spruce_wall_sign", "lantern", "soul_lantern", "snow", "soul_wall_torch", "chain"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    c = MORE.get(state[0])
    if c is not None:
        return c
    if state[0].endswith("_wall_banner"):
        return (50, 60, 160)
    if state[0].endswith("_candle"):
        return (200, 230, 250)
    if state[0].endswith("_bed"):
        return (230, 230, 240)
    return color8(state)


def heights(f, y_max):
    n = f.v.shape[0]
    skip = np.array([s is None or s[0] in SKIP for s in f.states], dtype=bool)
    solid = ~skip[f.v[:, :y_max, :]]
    rev = solid[:, ::-1, :]
    has = rev.any(axis=1)
    top = np.where(has, y_max - 1 - rev.argmax(axis=1), 0)
    return top, has


def render(f, y_max, box=None, scale=2):
    top, has = heights(f, y_max)
    n = f.v.shape[0]
    xi, zi = np.meshgrid(np.arange(n), np.arange(n), indexing="ij")
    idx = f.v[xi, top, zi]
    pal = np.array([color_of(s) for s in f.states], dtype=float)
    rgb = pal[idx]
    rgb[~has] = (10, 12, 16)
    shade = 0.72 + 0.28 * np.clip((top - 20) / 50.0, 0, 1.2)
    d = np.zeros((n, n))
    d[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade = shade * (1 + np.clip(d * 0.06, -0.3, 0.3))
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    if box:
        x0, z0, x1, z1 = box
        rgb = rgb[x0:x1, z0:z1]
    w, h = rgb.shape[0], rgb.shape[1]
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((w * scale, h * scale), Image.NEAREST)
    return img


def label(dr, font, x, z, text, color, scale, off=(0, 0)):
    px, pz = (x + off[0]) * scale, (z + off[1]) * scale
    dr.text((px + 4, pz - 8), text, fill=color, font=font, stroke_width=2, stroke_fill=(0, 0, 0))


def overlay(img, f, data, scale, box=None, level=None):
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 13, index=0)
    ox, oz = (box[0], box[1]) if box else (0, 0)

    def at(p):
        return p[0] - ox, p[2] - oz

    def on_level(p):
        return level is None or abs(p[1] - level) <= 6
    lin = data["lineage"]
    for p in lin["portraits"]:
        if on_level(p["pos"]):
            x, z = at(p["pos"])
            dr.rectangle(((x - 1.5) * scale, (z - 1.5) * scale, (x + 1.5) * scale, (z + 1.5) * scale), outline=(255, 210, 60), width=3)
            label(dr, font, x, z, "肖 " + p["name"], (255, 220, 120), scale)
    q = lin["queen"]["pos"]
    if on_level(q):
        x, z = at(q)
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 2) * scale, (z + 2) * scale), outline=(255, 120, 220), width=3)
        label(dr, font, x, z, "女王の肖像", (255, 150, 230), scale, (-14, -3))
    for p in lin["plates"]:
        if on_level(p["pos"]):
            x, z = at(p["pos"])
            dr.ellipse(((x - 1.5) * scale, (z - 1.5) * scale, (x + 1.5) * scale, (z + 1.5) * scale), fill=(250, 230, 100),
                       outline=(0, 0, 0))
            label(dr, font, x, z, "札 " + p["name"], (250, 240, 160), scale)
    for c in lin["clues"]:
        if on_level(c["pos"]):
            x, z = at(c["pos"])
            dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 2) * scale, (z + 2) * scale), fill=(255, 255, 255), outline=(0, 0, 0))
            label(dr, font, x, z, "手がかり（" + ("建国の碑" if c["id"] == "founding" else "系譜の書") + "）", (255, 255, 255), scale)
    for ch in data["chests"]:
        if on_level(ch["pos"]):
            x, z = at(ch["pos"])
            dr.rectangle(((x - 1.5) * scale, (z - 1.5) * scale, (x + 1.5) * scale, (z + 1.5) * scale), fill=(200, 140, 40),
                         outline=(0, 0, 0))
    for wp in data["waypoints"]:
        if on_level(wp["pos"]):
            x, z = at(wp["pos"])
            dr.ellipse(((x - 2.5) * scale, (z - 2.5) * scale, (x + 2.5) * scale, (z + 2.5) * scale), fill=(120, 230, 255),
                       outline=(0, 0, 0))
            label(dr, font, x, z, wp["name"], (150, 240, 255), scale)
    for g in data["guardians"]:
        if on_level(g["pos"]):
            x, z = at(g["pos"])
            dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 60, 60), width=3)
            label(dr, font, x, z, "名のある魔物 " + g["id"], (255, 120, 120), scale)
    ecol = {"frozen_guard": (120, 160, 255), "ice_cavalry": (60, 220, 255), "shelf_wraith": (220, 120, 255),
            "frost_hound": (240, 240, 240), "forgotten_maid": (255, 170, 200)}
    for zn in data["spawns"]:
        if on_level(zn["center"]):
            x, z = at(zn["center"])
            r = zn["radius"]
            dr.ellipse(((x - r) * scale, (z - r) * scale, (x + r) * scale, (z + r) * scale),
                       outline=ecol.get(zn["enemy"].split(":")[1], (255, 0, 255)), width=1)
    sx0, sy0, sz0 = data["boss_room"]["seal"][0]
    if on_level((0, sy0, 0)):
        dr.rectangle(((sx0 - ox - 1) * scale, (sz0 - oz - 1) * scale, (sx0 - ox + 6) * scale, (sz0 - oz + 2) * scale), fill=(255, 80, 200))
        label(dr, font, sx0 - ox, sz0 - oz, "封印", (255, 120, 220), scale)
    for door_name, gid in (("大階段の氷の門", "founding"), ("回廊の扉", "chapel")):
        for g in lin["groups"]:
            if g["id"] == gid and "door" in g:
                b = g["door"]["box"]
                if on_level((0, b[1], 0)):
                    dr.rectangle(((b[0] - ox) * scale, (b[2] - oz - 1) * scale, (b[3] - ox + 1) * scale, (b[5] - oz + 2) * scale),
                                 outline=(80, 200, 255), width=3)
                    label(dr, font, b[0] - ox, b[2] - oz, door_name, (140, 220, 255), scale, (0, -3))
    return img


def render_all(f, data, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    scale = 2
    img = render(f, f.v.shape[1], scale=scale)
    overlay(img, f, data, scale).save(os.path.join(out_dir, "floor9_map.png"))
    box = (230, 180, 540, 470)
    import build_floor9 as B
    for name, y_cut, lvl in (("floor9_plan1.png", B.G + 9, B.G + 1), ("floor9_plan2.png", B.F2 + 9, B.F2 + 1)):
        img = render(f, y_cut, box=box, scale=3)
        overlay(img, f, data, 3, box=box, level=lvl).save(os.path.join(out_dir, name))
    print("絵：", out_dir)
