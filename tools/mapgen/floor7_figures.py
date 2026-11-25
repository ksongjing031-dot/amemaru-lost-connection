"""塔の7階の見本の絵（build_floor7.py の --render から呼ぶ。11月25日【補完】）。

  floor7_map.png      上から見た地図（いちばん上のブロックの色と陰。光の迷路の光の道（解き方）・鏡・宝箱・中継の灯・守り手・湧く所・
                      封印（合言葉の扉）・鏡の湖・氷の崖の門）
  floor7_<所>.png     バニラのテクスチャで描いた絵（mapgen/render.py の Scene。光の泉・鏡・受け石は、似たバニラのブロックで代わりに描く）
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from floor6_figures import MORE as MORE6, SWAP as SWAP6, color_of as color6
from mapgen.world import World, block

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
MORE = dict(MORE6)
MORE.update({
    "white_stained_glass": (232, 240, 248), "light_blue_stained_glass": (160, 205, 245), "glass": (210, 230, 240),
    "calcite": (223, 224, 220), "deepslate_bricks": (70, 70, 72), "cracked_deepslate_bricks": (64, 64, 66),
    "sea_lantern": (200, 230, 225), "amemaru:light_spring": (255, 250, 180), "amemaru:ice_mirror": (220, 240, 255),
    "amemaru:light_target": (255, 200, 90), "lectern": (160, 120, 70), "tuff": (108, 109, 102),
})
SWAP = dict(SWAP6)
SWAP.update({"amemaru:light_spring": block("sea_lantern"), "amemaru:ice_mirror": block("light_blue_stained_glass"),
             "amemaru:light_target": block("glowstone")})
SKIP = {"light", "spruce_sign", "lantern", "snow"}


def color_of(state):
    if state is None:
        return (0, 0, 0)
    c = MORE.get(state[0])
    return c if c is not None else color6(state)


def top_map(f, data, path, y_max, scale=2):
    import build_floor7 as B
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
    ecol = {"mimic": (0, 200, 220), "ice_stag": (90, 160, 255), "icicle_bat": (200, 120, 255), "blizzard_spirit": (255, 120, 60)}
    for zn in data["spawns"]:
        cx, _, cz = zn["center"]
        r = zn["radius"]
        dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale),
                   outline=ecol.get(zn["enemy"].split(":")[1], (255, 0, 255)), width=2)
    # 光の迷路：解き方の光の道（金色の線）と、鏡（解き方の向きの斜め線）
    for m in data["light_mazes"]:
        sol = f.answers[m["id"]]
        x, y, z = m["source"]
        dd = m["direction"]
        mirrors = {tuple(mm["pos"]): k for k, mm in enumerate(m["mirrors"])}
        pts = [(x, z)]
        for _ in range(60):
            dx, dz = B.DIRS[dd]
            x, z = x + dx, z + dz
            if [x, y, z] == m["target"]:
                pts.append((x, z))
                break
            k = mirrors.get((x, y, z))
            if k is not None:
                pts.append((x, z))
                dd = B.reflect(dd, sol[k])
        dr.line([((px + 0.5) * scale, (pz + 0.5) * scale) for px, pz in pts], fill=(255, 210, 40), width=3)
        for k, mm in enumerate(m["mirrors"]):
            mx, _, mz = mm["pos"]
            if sol[k]:
                seg = ((mx) * scale, (mz + 1) * scale, (mx + 1) * scale, (mz) * scale)
            else:
                seg = ((mx) * scale, (mz) * scale, (mx + 1) * scale, (mz + 1) * scale)
            dr.line(seg, fill=(40, 60, 160), width=3)
        sx, _, sz = m["source"]
        tx, _, tz = m["target"]
        dr.ellipse(((sx - 1) * scale, (sz - 1) * scale, (sx + 2) * scale, (sz + 2) * scale), fill=(255, 250, 120))
        dr.rectangle(((tx - 1) * scale, (tz - 1) * scale, (tx + 2) * scale, (tz + 2) * scale), outline=(255, 120, 0), width=3)
        cx = (sx + tx) // 2
        dr.text(((cx - 10) * scale, (min(sz, tz) - 18) * scale), m["name"], fill=(120, 60, 0), font=font)
        x0, _, z0, x1, _, z1 = m["door"]["box"]
        dr.rectangle((x0 * scale, z0 * scale, (x1 + 1) * scale, (z1 + 1) * scale), fill=(60, 120, 255))
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
    b = data["boss_room"]
    cx, _, cz = b["arena"]["center"]
    r = b["arena"]["radius"]
    dr.ellipse(((cx - r) * scale, (cz - r) * scale, (cx + r) * scale, (cz + r) * scale), outline=(255, 80, 255), width=2)
    (sx0, _, sz0), (sx1, _, sz1) = b["seal"]
    dr.rectangle((sx0 * scale, (sz0 - 1) * scale, (sx1 + 1) * scale, (sz1 + 2) * scale), fill=(255, 200, 40))
    a = data["passphrase"]["area"]
    dr.rectangle((a[0] * scale, a[2] * scale, (a[3] + 1) * scale, (a[5] + 1) * scale), outline=(0, 160, 90), width=3)
    dr.text(((a[3] + 3) * scale, (a[2] + 2) * scale), "合言葉の言う所", fill=(0, 110, 60), font=font)
    x, _, z = data["tablet"]["pos"]
    dr.rectangle(((x - 2) * scale, (z - 2) * scale, (x + 3) * scale, (z + 3) * scale), outline=(120, 60, 0), width=3)
    dr.text(((x + 4) * scale, (z + 3) * scale), "森林監視所の記録", fill=(120, 60, 0), font=font)
    dr.text((10, 8), "7階　氷の森林（上から）", fill=(20, 30, 60), font=big)
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
    ("grove", (276, 4, 520, 396, 60, 660), (336, 32, 652), (336, 20, 560)),
    ("entry_garden", (300, 4, 500, 372, 50, 566), (336, 40, 560), (336, 16, 528)),
    ("west_garden", (160, 4, 370, 232, 50, 436), (214, 38, 432), (196, 16, 398)),
    ("cliff_gate", (280, 4, 470, 392, 60, 540), (350, 30, 530), (336, 24, 500)),
    ("station", (300, 4, 214, 372, 60, 280), (336, 46, 272), (336, 17, 244)),
    ("passphrase_door", (300, 4, 150, 372, 60, 210), (336, 26, 204), (336, 22, 176)),
    ("lake", (270, 4, 50, 402, 70, 190), (336, 44, 172), (336, 16, 100)),
]


def render_all(f, data, out, only=None):
    os.makedirs(out, exist_ok=True)
    print("描きました:", top_map(f, data, os.path.join(out, "floor7_map.png"), f.v.shape[1] - 1))
    from mapgen.render import Scene
    for name, (x0, y0, z0, x1, y1, z1), eye, target in VIEWS:
        if only and name not in only:
            continue
        w = sub_world(f, x0, y0, z0, x1, y1, z1)
        sc = Scene(w, ground_y=0)
        e = (eye[0] - x0, eye[1] - y0, eye[2] - z0)
        t = (target[0] - x0, target[1] - y0, target[2] - z0)
        path = os.path.join(out, "floor7_%s.png" % name)
        sc.render(eye=e, target=t, width=960, height=540, fov=70, path=path, fog=(160.0, 420.0, 0.2), ground_fog=(160.0, 420.0, 0.2))
        print("描きました:", path)
