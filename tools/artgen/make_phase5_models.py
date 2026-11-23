"""AMEMARU: LOST CONNECTION の Phase 5 の仮モデル（GeckoLib）を作る。

使い方: python3 tools/artgen/make_phase5_models.py [assets/amemaru のフォルダ] [--preview 出力.png]

作るもの（ファイルの名前・ボーンの名前・アニメーションの名前は、本番のモデルでも同じにする。docs/phase5.md の表）:
  geo/entity/veldhorn.geo.json, animations/entity/veldhorn.animation.json, textures/entity/veldhorn.png（_glowmask.png）
      1階のボス「草原の主ヴェルドホーン」（巨大な角の獣。苔むした古い石を背負い、角の先に翠の結晶。Phase 0-A 9章）
  ボーン：root・body・hump・head・jaw・horn_left・horn_right・tail・leg_front_left・leg_front_right・leg_back_left・leg_back_right
  アニメーション：idle・walk・attack（角で薙ぐ）・skill_charge（頭を下げて地をかき、突っ込む）・skill_stomp（前足を上げて踏みつける）・
      skill_roar（頭を上げて吠える。ツノモフを呼ぶ）・death
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geo import Model, save_anim  # noqa: E402

ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
ROOT = ARGS[0] if ARGS and not ARGS[0].endswith(".png") else "src/main/resources/assets/amemaru"


def loop(length, bones):
    return {"loop": True, "length": length, "bones": bones}


def once(length, bones, hold=False):
    out = {"length": length, "bones": bones}
    if hold:
        out["loop"] = "hold_on_last_frame"
    return out


def veldhorn():
    fur, fur_d, fur_l = "#76704A", "#575132", "#958E5E"
    mane, mane_d = "#5E6E38", "#46542A"
    stone, moss = "#9A988C", "#6E8A4A"
    ivory, ivory_d = "#E8DFC4", "#BFB38E"
    crystal, crystal_glow = "#5FE3A0", "#9CFFC8"
    hoof, nose, eye = "#2E2A22", "#2A2620", "#FFD27A"
    brass = "#C9A24A"
    m = Model("veldhorn", 256, 256, bounds=(5.0, 4.0, 1.5), seed=505)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 30, 0], "root")
    m.cube("body", [-13, 20, -20], [26, 22, 40], fur, pattern="fluff")
    m.cube("body", [-12, 18, -14], [24, 3, 28], fur_d, pattern="fluff")          # 腹の毛
    m.cube("body", [-13.5, 22, -8], [1, 12, 20], fur_l, pattern="fluff")          # 脇の毛（左右）
    m.cube("body", [12.5, 22, -8], [1, 12, 20], fur_l, pattern="fluff")
    # 肩のこぶ（長い毛）と、背中に背負った古い石（苔）
    m.bone("hump", [0, 40, -14], "body")
    m.cube("hump", [-14, 30, -24], [28, 18, 16], mane, pattern="fluff")
    m.cube("hump", [-12, 46, -22], [24, 4, 12], mane_d, pattern="fluff")
    m.cube("hump", [-15, 24, -25], [30, 8, 6], mane, pattern="fluff")             # 胸の前の長い毛
    m.cube("body", [-9, 42, -6], [18, 4, 12], stone, pattern="stone")
    m.cube("body", [-7, 46, -4], [12, 3, 8], stone, pattern="stone")
    m.cube("body", [-8, 42, 7], [14, 3, 9], stone, pattern="stone")
    m.cube("body", [-9.5, 45.5, -5], [8, 1, 6], moss, pattern="fluff")
    m.cube("body", [1, 44.5, 8], [6, 1, 6], moss, pattern="fluff")
    m.cube("body", [-2, 49, -2], [3, 3, 3], crystal, glow=crystal_glow, pattern="crystal")   # 石から生えた翠の結晶
    m.cube("body", [3, 47, 2], [2, 4, 2], crystal, glow=crystal_glow, pattern="crystal")
    # 頭（低く前に突き出す）
    m.bone("head", [0, 34, -24], "body")
    m.cube("head", [-8, 24, -38], [16, 16, 15], fur, pattern="fluff")
    m.cube("head", [-6, 22, -45], [12, 11, 8], fur_l, pattern="fluff")           # 鼻づら
    m.cube("head", [-4, 25, -46], [8, 5, 1], nose)
    m.cube("head", [-9, 36, -30], [18, 6, 8], mane, pattern="fluff")              # 頭の上の毛
    m.cube("head", [-8.5, 31, -36], [1, 2, 3], eye, glow=eye)
    m.cube("head", [7.5, 31, -36], [1, 2, 3], eye, glow=eye)
    m.cube("head", [-3, 38, -37], [6, 2, 6], brass, pattern="metal")             # 額の真鍮の輪（古代の飾り）
    m.bone("jaw", [0, 24, -36], "head")
    m.cube("jaw", [-5, 19, -44], [10, 4, 9], fur_d, pattern="fluff")
    m.cube("jaw", [-4, 15, -40], [8, 5, 6], mane_d, pattern="fluff")             # あごひげ
    # 角：横へ張り出し、上へ曲がって前を向く。先は翠の結晶
    for side, sign in (("horn_left", 1), ("horn_right", -1)):
        m.bone(side, [sign * 8, 36, -32], "head")
        x0 = 8 if sign > 0 else -18
        m.cube(side, [x0, 34, -34], [10, 6, 6], ivory, pattern="stone")
        x1 = 16 if sign > 0 else -21
        m.cube(side, [x1, 38, -35], [5, 8, 5], ivory, pattern="stone")
        x2 = 17 if sign > 0 else -20.5
        m.cube(side, [x2, 44, -38], [3.5 if False else 4, 4, 7], ivory_d, pattern="stone")
        x3 = 17.5 if sign > 0 else -20
        m.cube(side, [x3, 45, -44], [3, 3, 6], crystal, glow=crystal_glow, pattern="crystal")
        x4 = 15 if sign > 0 else -18
        m.cube(side, [x4, 35, -33.5], [3, 2, 5], brass, pattern="metal")          # 角の根元の真鍮の輪
    # しっぽ
    m.bone("tail", [0, 38, 20], "body")
    m.cube("tail", [-2, 27, 19], [4, 12, 4], fur, pattern="fluff")
    m.cube("tail", [-3, 22, 18], [6, 6, 6], mane_d, pattern="fluff")
    # 足（太い。ひざに長い毛、ひづめ）
    for name, x, z in [("leg_front_left", 8, -12), ("leg_front_right", -8, -12), ("leg_back_left", 8, 13),
                       ("leg_back_right", -8, 13)]:
        m.bone(name, [x, 24, z], "root")
        m.cube(name, [x - 4, 3, z - 4], [8, 21, 8], fur_d, pattern="fluff")
        m.cube(name, [x - 5, 9, z - 5], [10, 5, 10], mane, pattern="fluff")
        m.cube(name, [x - 4, 0, z - 4], [8, 3, 8], hoof, pattern="stone")
    m.save(ROOT, "entity")

    # ---------------------------------------------------------------- アニメーション
    walk_len = 1.0
    walk = {
        "body": {"position": {0: [0, 0, 0], 0.25: [0, 0.8, 0], 0.5: [0, 0, 0], 0.75: [0, 0.8, 0], 1.0: [0, 0, 0]},
                 "rotation": {0: [0, 0, -1.5], 0.5: [0, 0, 1.5], 1.0: [0, 0, -1.5]}},
        "head": {"rotation": {0: [3, 0, 0], 0.5: [-3, 0, 0], 1.0: [3, 0, 0]}},
        "tail": {"rotation": {0: [10, -8, 0], 0.5: [10, 8, 0], 1.0: [10, -8, 0]}},
    }
    for name, sign in [("leg_front_left", 1), ("leg_back_right", 1), ("leg_front_right", -1), ("leg_back_left", -1)]:
        walk[name] = {"rotation": {0: [22 * sign, 0, 0], 0.5: [-22 * sign, 0, 0], 1.0: [22 * sign, 0, 0]}}
    save_anim(ROOT, "entity", "veldhorn", {
        "idle": loop(4.0, {
            "body": {"scale": {0: [1, 1, 1], 2.0: [1.01, 1.025, 1.0], 4.0: [1, 1, 1]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [4, -6, 0], 2.0: [6, 0, 0], 3.0: [4, 6, 0], 4.0: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 2.0: [4, 0, 0], 2.4: [0, 0, 0]}},
            "tail": {"rotation": {0: [12, -10, 0], 2.0: [12, 10, 0], 4.0: [12, -10, 0]}},
            "hump": {"scale": {0: [1, 1, 1], 2.0: [1.02, 1.04, 1.02], 4.0: [1, 1, 1]}}}),
        "walk": loop(walk_len, walk),
        # 角で横に薙ぐ（予兆0.8秒：頭を右へ引いて、左へ振り抜く）
        "attack": once(1.2, {
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-8, 35, -10], 0.8: [-8, 38, -12], 0.95: [10, -40, 14],
                                  1.2: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.7: [0, 10, 0], 0.95: [0, -12, 0], 1.2: [0, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 0.7: [-10, 0, 0], 1.0: [10, 0, 0], 1.2: [0, 0, 0]}}}),
        # 突進：頭を下げ、前足で地をかく（予兆1.4秒）→ 突っ込む姿勢のまま（走りは歩きのアニメーション）
        "skill_charge": once(2.6, {
            "head": {"rotation": {0: [0, 0, 0], 0.4: [28, 0, 0], 2.4: [28, 0, 0], 2.6: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.4: [0, -3, -1], 2.4: [0, -3, -1], 2.6: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.4: [6, 0, 0], 2.4: [6, 0, 0], 2.6: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.4: [0, -1.5, 1], 1.4: [0, -1.5, 1], 1.5: [0, -1, -1], 2.4: [0, -1, -1],
                                  2.6: [0, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 0.4: [-30, 0, 0], 0.6: [20, 0, 0], 0.8: [-30, 0, 0], 1.0: [20, 0, 0],
                                            1.2: [-30, 0, 0], 1.4: [0, 0, 0]}},
            "tail": {"rotation": {0: [12, 0, 0], 0.4: [50, 0, 0], 2.4: [50, 0, 0], 2.6: [12, 0, 0]}}}),
        # 踏みつけ：前足を上げて立ち上がり、地面に叩きつける（予兆1.2秒）
        "skill_stomp": once(1.8, {
            "root": {"rotation": {0: [0, 0, 0], 1.0: [-24, 0, 0], 1.2: [-26, 0, 0], 1.35: [4, 0, 0], 1.6: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.0: [0, 2, 4], 1.2: [0, 2.5, 4], 1.35: [0, -1, 0], 1.6: [0, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 1.0: [-50, 0, 0], 1.2: [-55, 0, 0], 1.35: [10, 0, 0], 1.6: [0, 0, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 1.0: [-45, 0, 0], 1.2: [-50, 0, 0], 1.35: [10, 0, 0], 1.6: [0, 0, 0]}},
            "leg_back_left": {"rotation": {0: [0, 0, 0], 1.0: [24, 0, 0], 1.2: [26, 0, 0], 1.35: [-4, 0, 0], 1.6: [0, 0, 0]}},
            "leg_back_right": {"rotation": {0: [0, 0, 0], 1.0: [24, 0, 0], 1.2: [26, 0, 0], 1.35: [-4, 0, 0], 1.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [-15, 0, 0], 1.35: [15, 0, 0], 1.8: [0, 0, 0]}}}),
        # 吠える：頭を上げ、口を開ける（ツノモフを呼ぶ）
        "skill_roar": once(2.0, {
            "head": {"rotation": {0: [0, 0, 0], 0.4: [-35, 0, 0], 1.6: [-35, 0, 0], 2.0: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [25, 0, 0], 1.6: [25, 0, 0], 2.0: [0, 0, 0]}},
            "hump": {"scale": {0: [1, 1, 1], 0.4: [1.06, 1.08, 1.06], 1.6: [1.06, 1.08, 1.06], 2.0: [1, 1, 1]}},
            "body": {"rotation": {0: [0, 0, 0], 0.4: [-6, 0, 0], 1.6: [-6, 0, 0], 2.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [12, 0, 0], 0.4: [60, 0, 0], 1.6: [60, 0, 0], 2.0: [12, 0, 0]}}}),
        "death": once(2.0, {
            "root": {"rotation": {0: [0, 0, 0], 0.8: [0, 0, 0], 1.4: [0, 0, 80]},
                     "position": {0: [0, 0, 0], 0.8: [0, -2, 0], 1.4: [0, 8, 0], 2.0: [0, 6, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-30, 0, 0], 1.4: [20, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.6: [25, 0, 0], 1.4: [5, 0, 0]}},
            "leg_front_left": {"rotation": {0: [0, 0, 0], 0.8: [20, 0, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 0.8: [20, 0, 0]}}}, hold=True),
    })


# ================================================================ 確かめの絵（箱を面の平均の色で塗って描く）
def preview(out, kind="entity", name="veldhorn"):
    from PIL import Image, ImageDraw
    geo = json.load(open(os.path.join(ROOT, "geo", kind, name + ".geo.json"), encoding="utf-8"))
    tex = Image.open(os.path.join(ROOT, "textures", kind, name + ".png")).convert("RGBA")
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    sx, sy = tex.size[0] / tw, tex.size[1] / th

    def avg(x0, y0, w, h):
        box = tex.crop((int(x0 * sx), int(y0 * sy), int((x0 + w) * sx), int((y0 + h) * sy)))
        px = [p for p in box.getdata() if p[3] > 0]
        if not px:
            return (200, 0, 200)
        return tuple(sum(p[i] for p in px) // len(px) for i in range(3))

    faces = []
    for b in g["bones"]:
        for c in b.get("cubes", []):
            (x, y, z), (w, h, d) = c["origin"], c["size"]
            u, v = c["uv"]
            inf = c.get("inflate", 0)
            x0, y0, z0, x1, y1, z1 = x - inf, y - inf, z - inf, x + w + inf, y + h + inf, z + d + inf
            cols = {"top": avg(u + d, v, w, d), "front": avg(u + d, v + d, w, h), "right": avg(u, v + d, d, h),
                    "left": avg(u + d + w, v + d, d, h), "back": avg(u + 2 * d + w, v + d, w, h)}
            faces += [
                ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], cols["top"], 1.0),
                ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], cols["front"], 0.8),
                ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], cols["back"], 0.6),
                ([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], cols["right"], 0.7),
                ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], cols["left"], 0.9),
            ]
    views = []
    for yaw in (35, 145, -90):
        img = Image.new("RGB", (420, 360), (120, 150, 110))
        dr = ImageDraw.Draw(img)
        cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        pitch = math.radians(20)

        def proj(p):
            x, y, z = p
            rx = x * cy - z * sy_
            rz = x * sy_ + z * cy
            ry = y * math.cos(pitch) - rz * math.sin(pitch)
            depth = y * math.sin(pitch) + rz * math.cos(pitch)
            return 210 + rx * 4.2, 300 - ry * 4.2, depth
        drawn = []
        for quad, col, light in faces:
            pts = [proj(p) for p in quad]
            depth = sum(p[2] for p in pts) / 4
            drawn.append((depth, pts, col, light))
        for depth, pts, col, light in sorted(drawn, key=lambda t: -t[0]):
            dr.polygon([(p[0], p[1]) for p in pts], fill=tuple(int(c * light) for c in col), outline=(30, 30, 30))
        views.append(img)
    sheet = Image.new("RGB", (420 * len(views), 360))
    for i, v in enumerate(views):
        sheet.paste(v, (420 * i, 0))
    sheet.save(out)


if __name__ == "__main__":
    veldhorn()
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("ok")
