"""塔の5階の見本の絵（build_floor5.py の --render から呼ぶ。11月24日【補完】）。

  floor5_map.png      上から見た地図（天井を切り取って、いちばん上のブロックの色と陰。接続の柱・仕掛け石・宝箱・中継の灯・
                      守り手・湧く所・封印・祭壇・結び台）
  floor5_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。天井を切り取って斜め上から）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor4_figures import MORE as MORE4, SWAP as SWAP4, color_of as color4
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = dict(MORE4)
MORE.update({
    "amemaru:binding_dais": (255, 220, 90), "amemaru:tower_pillar": (95, 227, 160), "amemaru:tower_switch": (255, 140, 60),
    "amemaru:tower_gate": (120, 230, 255), "chiseled_deepslate": (56, 56, 58), "deepslate_tile_slab": (54, 54, 56),
    "polished_blackstone": (53, 48, 56), "polished_blackstone_bricks": (48, 42, 50), "chiseled_polished_blackstone": (54, 48, 56),
    "tuff": (108, 109, 102), "cobbled_deepslate": (77, 77, 80), "blackstone": (42, 36, 41), "emerald_block": (42, 203, 87),
    "lime_stained_glass": (127, 204, 25), "bookshelf": (117, 94, 59), "dark_oak_slab": (66, 43, 20), "smithing_table": (60, 60, 70),
    "anvil": (68, 68, 68), "sea_lantern": (172, 199, 190), "end_rod": (240, 230, 220), "stone": (125, 125, 125),
})
SWAP = dict(SWAP4)
SWAP.update({"amemaru:binding_dais": block("light_weighted_pressure_plate", power=0),
             "amemaru:tower_pillar": block("emerald_block"), "amemaru:tower_switch": block("lodestone"),
             "amemaru:tower_gate": block("iron_bars", north=False, south=False, east=True, west=True, waterlogged=False)})
SKIP = {"light", "birch_sign", "soul_lantern"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    c = MORE.get(state[0])
    return c if c is not None else color4(state)


def top_map(f, data, path, y_max, scale=2):
    import build_floor5 as B
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
    shade = 0.45 + 0.55 * np.clip((top - 10) / float(B.GROUND - 10), 0, 1.2)
    d = np.zeros((n, n))
    d[1:, 1:] = top[1:, 1:] - top[:-1, :-1]
    shade = shade * (1 + np.clip(d * 0.08, -0.3, 0.3))
    rgb = np.clip(rgb * shade[:, :, None], 0, 255)
    img = Image.fromarray(rgb.transpose(1, 0, 2).astype(np.uint8), "RGB").resize((n * scale, n * scale), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 14, index=0)
    big = ImageFont.truetype(FONT, 22, index=0)
    for i, p in enumerate(data["pillars"]):
        x, _, z = p["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(95, 255, 160), width=3)
        dr.text(((x + 5) * scale, (z - 8) * scale), "柱" + "ABCD"[i], fill=(140, 255, 190), font=font)
    for s in data["switches"][0]["stones"]:
        x, _, z = s
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), fill=(255, 140, 60))
    for wp in data["waypoints"]:
        x, _, z = wp["pos"]
        dr.ellipse(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 255, 0), width=3)
        dr.text(((x + 5) * scale, (z + 2) * scale), wp["name"], fill=(255, 255, 120), font=font)
    for c in data["chests"]:
        x, _, z = c["pos"]
        dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale),
                     outline=(255, 60, 60) if c.get("guardian") else (255, 170, 0), width=2)
    ecol = {"watcher": (255, 120, 110), "sentinel": (180, 220, 255), "repair_drone": (120, 255, 190)}
    for zn in data["spawns"]:
        cx, _, cz = zn["center"]
        r = zn["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale),
                   outline=ecol.get(zn["enemy"].split(":")[1], (255, 0, 255)), width=1)
    for g in data["guardians"]:
        x, _, z = g["pos"]
        dr.rectangle(((x - 4) * scale, (z - 4) * scale, (x + 4) * scale, (z + 4) * scale), outline=(255, 0, 0), width=3)
        dr.text(((x + 5) * scale, (z - 10) * scale), g["id"], fill=(255, 100, 100), font=font)
    b = data["boss_room"]
    cx, _, cz = b["arena"]["center"]
    r = b["arena"]["radius"]
    dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
    (sx0, _, sz0), (sx1, _, sz1) = b["seal"]
    dr.rectangle((sx0 * scale, sz0 * scale, (sx1 + 1) * scale, (sz1 + 1) * scale), fill=(255, 230, 120))
    for (x, _, z) in data["boss_marks"]["binding_daises"]:
        dr.rectangle(((x - 1) * scale, (z - 1) * scale, (x + 2) * scale, (z + 2) * scale), outline=(255, 220, 90), width=3)
    dr.text((10, 8), "5階　古代守護者の祭壇（天井を切り取って上から）", fill=(255, 255, 255), font=big)
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
    ("entrance", (180, 20, 400, 400, 70, 571), (288, 74, 571), (270, 46, 470)),
    ("bastion", (340, 20, 270, 520, 70, 410), (330, 82, 420), (430, 50, 330)),
    ("workshop", (50, 20, 270, 240, 70, 410), (240, 80, 420), (130, 48, 340)),
    ("forecourt", (190, 20, 100, 390, 70, 230), (288, 78, 236), (288, 50, 150)),
    ("altar", (240, 20, 20, 336, 88, 120), (288, 84, 126), (288, 52, 66)),
    ("ceiling", (200, 40, 60, 376, 95, 240), (288, 50, 240), (288, 92, 120)),
]


def render_all(f, data, out, only=None):
    import build_floor5 as B
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor5_map.png"), B.CEIL - 2))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor5_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(160.0, 420.0, 0.2), ground_fog=(160.0, 420.0, 0.2))
        print("描きました:", path)
