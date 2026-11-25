"""6階「凍結した村」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月25日【補完】）。

  python3 tools/artgen/make_village_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の6階：一瞬のまま凍りついた雪の村。雑魚は霜狼・氷像の残響・凍て鳥（吹雪を起こす）・霜の欠け熊（大型）。
ボスは村長の残響オルド。第2章の色は氷青・白銀・オーロラの緑紫。作るもの（ファイルの名前は敵のIDと同じ）:
  frost_wolf   霜狼：1階の欠け牙の狼の形を、白く凍った毛の色に塗り直す（目は淡い水色に光る。欠けた所は白いまま）
  frost_bear   霜の欠け熊：大きな熊。霜の毛並み（白と淡い青）、丸い耳、太い前足。体のところどころが白く欠けている（ロスト）。
               骨の名前と動きは4階の欠けた聖獣と同じ（爪・跳びかかり・咆哮・忘れる）
  frost_bird   凍て鳥：白と氷青の羽の大きな鳥。氷の冠羽、長い尾羽。羽ばたいて浮き、羽を大きく広げて吹雪を起こす
  ordo         村長の残響オルド：背の高い老人の残響（淡い氷色の肌）。紺の長い外套に毛皮のえりと帽子、金の縁取り、長い白いひげ。
               右手に、氷の灯をともしたランタンの杖。目は淡い水色に光る
氷像の残響は、凍った村人の氷像と同じ形（プレイヤーと同じ形のモデル。tools/artgen/make_village_things.py の見た目）。
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import loop, once, posed, sheet_font  # noqa: E402
from make_temple_models import LOST_WHITE, TemplePainter, broken_beast_anims  # noqa: E402
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

VILLAGE = {
    "frost_fur": ramp("#5C6E86", "#7A8EA6", "#98AEC4", "#B6CADC", "#D0E0EE", "#E6F0F8", "#FAFCFF"),
    "frost_dark": ramp("#141A26", "#1E2636", "#2A344A", "#38445E", "#4A5874", "#5E6E8C", "#7686A4"),
    "ice": ramp("#3E6E9A", "#5A8EBA", "#7AAED4", "#9CC8E8", "#BEDDF4", "#DDEEFA", "#FFFFFF"),
    "coat": ramp("#0E1426", "#162038", "#1F2C4C", "#2A3A62", "#384A78", "#4A5E90", "#6276A8"),
    "fur_collar": ramp("#6A665E", "#837E74", "#9C968B", "#B5AFA3", "#CBC6BB", "#DFDBD2", "#F0EDE7"),
    "beard": ramp("#9AA4B0", "#AEB8C3", "#C2CBD4", "#D4DCE3", "#E4EAEF", "#F0F4F7", "#FCFDFE"),
    "echo_skin": ramp("#6A7A90", "#8496AC", "#9EB0C4", "#B6C6D8", "#CCDAE8", "#E0EAF4", "#F2F7FC"),
    "glow_frost": ramp("#2A7A9A", "#3E98B8", "#5AB6D2", "#80CEE4", "#AAE2F2", "#D4F2FA", "#FFFFFF"),
    "beak": ramp("#1C1E24", "#2A2C34", "#3A3D46", "#4C505A", "#60646E", "#767A84", "#8E929C"),
}
for _k, _v in VILLAGE.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class VillagePainter(TemplePainter):
    def paint_frost_fur(self, c):
        """霜の毛並み：短い筆の跡、上の面は明るい。ところどころ白く欠けている（ロスト）"""
        if c.el.tags.get("lost") and self.blob(c, 1.7, 901 + self.eid(c)) < 0.24:
            return LOST_WHITE
        strand = hsh(int(np.floor(c.p[0] * 2 + c.p[2])), int(np.floor(c.p[1] / 1.5)), 902)
        tone = 4.1 + (strand - 0.5) * 1.0 + self.bevel(c, up=0.9, down=0.9)
        if c.n[1] > 0.6:
            tone += 0.6
        if c.n[1] < -0.6:
            tone -= 1.1
        return pick(VILLAGE["frost_fur"], tone)

    def paint_frost_dark(self, c):
        return pick(VILLAGE["frost_dark"], self.std(c, "frost_dark", base=3.2, noise=0.3, blob=0.3, up=1.0, down=0.8))

    def paint_ice(self, c):
        tone = 4.0 + (self.tex(c, 911) - 0.5) * 0.8 + self.bevel(c, up=1.2, down=0.8)
        if (c.i + c.j) % 5 == 0:
            tone += 0.8   # 氷のきらめき
        return pick(VILLAGE["ice"], tone)

    def paint_coat(self, c):
        tone = self.std(c, "coat", base=3.6, noise=0.3, blob=0.35, up=1.2, down=1.0)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(np.floor(along + 0.5)) % 3 == 0:
                tone -= 0.8
        return pick(VILLAGE["coat"], tone)

    def paint_fur_collar(self, c):
        strand = hsh(int(np.floor(c.p[0] * 1.5)), int(np.floor(c.p[2] * 1.5)), int(np.floor(c.p[1] / 2.0)), 921)
        tone = 4.0 + (strand - 0.5) * 1.6 + self.bevel(c, up=0.8, down=0.8)
        return pick(VILLAGE["fur_collar"], tone)

    def paint_beard(self, c):
        strand = hsh(int(np.floor(c.p[0] * 2)), int(np.floor(c.p[1] / 2.5)), 931)
        if c.el.tags.get("tips") and c.edge_dist()["bottom"] == 0 and c.i % 2 == 1:
            return None
        return pick(VILLAGE["beard"], 4.2 + (strand - 0.5) * 1.2 + self.bevel(c, up=0.6, down=0.6))

    def paint_echo_skin(self, c):
        tone = self.std(c, "echo_skin", base=4.2, noise=0.25, blob=0.3, up=1.0, down=0.8)
        rgb = pick(VILLAGE["echo_skin"], tone)
        return (*rgb[:3], 235)

    def paint_glow_frost(self, c):
        return self.paint_glow(c, "glow_frost", base=4.3)

    def paint_beak(self, c):
        return pick(VILLAGE["beak"], self.std(c, "beak", base=3.4, noise=0.2, blob=0.2, up=1.2, down=1.0))


# ================================================================ 霜狼（欠け牙の狼の形を塗り直す）

def frost_recolor(img, glow=False):
    a = np.asarray(img.convert("RGBA")).astype(float)
    rgb = a[:, :, :3]
    lum = (rgb[:, :, 0] * 0.3 + rgb[:, :, 1] * 0.59 + rgb[:, :, 2] * 0.11) / 255.0
    white = (rgb.min(axis=2) > 225)
    out = a.copy()
    tint = np.array([0.80, 0.90, 1.0])
    if glow:
        cyan = np.array([140.0, 225.0, 245.0])
        out[:, :, :3] = np.clip(cyan[None, None, :] * (0.55 + lum[:, :, None] * 0.6), 0, 255)
    else:
        v = 0.45 + lum * 0.75
        out[:, :, :3] = np.clip(255.0 * tint[None, None, :] * v[:, :, None], 0, 255)
        out[white] = a[white]
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def frost_wolf(repo):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    with open(os.path.join(base, "geo/entity/kakekiba_wolf.geo.json"), encoding="utf-8") as fp:
        geo = json.load(fp)
    for g in geo.get("minecraft:geometry", []):
        g["description"]["identifier"] = "geometry.frost_wolf"
    with open(os.path.join(base, "geo/entity/frost_wolf.geo.json"), "w", encoding="utf-8") as fp:
        json.dump(geo, fp, ensure_ascii=False, indent=1)
        fp.write("\n")
    with open(os.path.join(base, "animations/entity/kakekiba_wolf.animation.json"), encoding="utf-8") as fp:
        anim = json.load(fp)
    anim["animations"] = {k.replace("animation.kakekiba_wolf.", "animation.frost_wolf."): v for k, v in anim["animations"].items()}
    with open(os.path.join(base, "animations/entity/frost_wolf.animation.json"), "w", encoding="utf-8") as fp:
        json.dump(anim, fp, ensure_ascii=False, indent=1)
        fp.write("\n")
    frost_recolor(Image.open(os.path.join(base, "textures/entity/kakekiba_wolf.png"))).save(
        os.path.join(base, "textures/entity/frost_wolf.png"))
    glow = os.path.join(base, "textures/entity/kakekiba_wolf_glowmask.png")
    if os.path.exists(glow):
        g = Image.open(glow).convert("RGBA")
        mask = np.asarray(g)[:, :, 3] > 0
        rec = np.asarray(frost_recolor(g, glow=True)).copy()
        rec[~mask] = 0
        Image.fromarray(rec, "RGBA").save(os.path.join(base, "textures/entity/frost_wolf_glowmask.png"))


# ================================================================ 霜の欠け熊

def build_frost_bear():
    g = GeoModel("frost_bear", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 16, 0), parent="root")
    g.bone("head", (0, 20, -12), parent="body")
    g.bone("jaw", (0, 17, -19), parent="head")
    g.bone("tail", (0, 20, 12), parent="body")
    g.bone("tail_tip", (0, 19, 14), parent="tail")
    g.cube("body", "body", (-7, 11, -10), (14, 12, 22), "frost_fur", tags={"lost": True})
    g.cube("body", "hump", (-6, 21, -9), (12, 4, 9), "frost_fur")
    g.cube("body", "chest", (-7.5, 10, -13), (15, 13, 4), "frost_fur", tags={"lost": True})
    g.cube("body", "ice_spikes", (-3, 23, -2), (6, 3, 8), "ice", glow=False)
    g.cube("head", "head", (-5, 15, -20), (10, 9, 9), "frost_fur", tags={"lost": True})
    g.cube("head", "muzzle", (-3, 15, -23), (6, 5, 4), "frost_fur")
    g.cube("head", "nose", (-1.5, 18.5, -23.3), (3, 2, 1), "frost_dark")
    g.cube("head", "eye_r", (-4, 20, -20.1), (2, 1, 0), "glow_frost", glow=True)
    g.cube("head", "eye_l", (2, 20, -20.1), (2, 1, 0), "glow_frost", glow=True)
    g.cube("head", "ear_r", (-5.5, 23, -16), (3, 3, 2), "frost_fur")
    g.cube("head", "ear_l", (2.5, 23, -16), (3, 3, 2), "frost_fur")
    g.cube("jaw", "jaw", (-3, 13.5, -22.5), (6, 2, 4), "frost_dark")
    g.cube("tail", "tail", (-1.5, 18, 12), (3, 3, 3), "frost_fur")
    g.cube("tail_tip", "tail_tip", (-1, 18.5, 14.5), (2, 2, 1), "frost_fur")
    for name, x, z in (("leg_front_right", -4.5, -7), ("leg_front_left", 4.5, -7), ("leg_back_right", -4.5, 8),
                       ("leg_back_left", 4.5, 8)):
        g.bone(name, (x, 14, z), parent="root")
        g.cube(name, name, (x - 2.5, 2, z - 2.5), (5, 12, 5), "frost_fur", tags={"lost": name.endswith("front_left")})
        g.cube(name, name + "_paw", (x - 3, 0, z - 3.5), (6, 3, 6), "frost_dark")
    return g


def frost_bear_anims():
    out = broken_beast_anims()
    return {k.replace("animation.broken_beast.", "animation.frost_bear."): v for k, v in out.items()}


# ================================================================ 凍て鳥

def build_frost_bird():
    g = GeoModel("frost_bird", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 8, 0), parent="root")
    g.bone("head", (0, 11, -5), parent="body")
    g.bone("wing_right", (-4, 10, -1), parent="body")
    g.bone("wing_left", (4, 10, -1), parent="body")
    g.bone("tail", (0, 8, 5), parent="body")
    g.bone("legs", (0, 5, 0), parent="body")
    g.cube("body", "body", (-3.5, 5, -5), (7, 7, 11), "frost_fur")
    g.cube("body", "breast", (-3, 4.5, -6), (6, 6, 3), "frost_fur")
    g.cube("head", "head", (-2.5, 10, -10), (5, 5, 5), "frost_fur")
    g.cube("head", "beak", (-1, 11, -13), (2, 2, 3), "beak")
    g.cube("head", "crest", (-0.5, 14, -9), (1, 4, 5), "ice", rot=(-30, 0, 0), pivot=(0, 14, -7))
    g.cube("head", "eye_r", (-2.6, 12.5, -9), (0, 1, 1), "glow_frost", glow=True)
    g.cube("head", "eye_l", (2.6, 12.5, -9), (0, 1, 1), "glow_frost", glow=True)
    g.cube("wing_right", "wing_r", (-15, 9, -4), (11, 1, 9), "frost_fur")
    g.cube("wing_right", "wing_r_tip", (-21, 9, -2), (6, 1, 7), "ice")
    g.cube("wing_left", "wing_l", (4, 9, -4), (11, 1, 9), "frost_fur")
    g.cube("wing_left", "wing_l_tip", (15, 9, -2), (6, 1, 7), "ice")
    g.cube("tail", "tail", (-2.5, 7, 5), (5, 1, 9), "ice", rot=(15, 0, 0), pivot=(0, 8, 5))
    g.cube("legs", "leg_r", (-2, 1, -1), (1, 4, 1), "beak")
    g.cube("legs", "leg_l", (1, 1, -1), (1, 4, 1), "beak")
    return g


def frost_bird_anims():
    flap = {"wing_right": {"rotation": {0: [0, 0, 25], 0.25: [0, 0, -30], 0.5: [0, 0, 25]}},
            "wing_left": {"rotation": {0: [0, 0, -25], 0.25: [0, 0, 30], 0.5: [0, 0, -25]}},
            "body": {"position": {0: [0, 0, 0], 0.25: [0, 0.8, 0], 0.5: [0, 0, 0]}},
            "legs": {"rotation": {0: [40, 0, 0], 0.5: [40, 0, 0]}}}
    t = {
        "idle": loop(0.5, flap),
        "walk": loop(0.4, {**flap, "body": {"rotation": {0: [12, 0, 0], 0.4: [12, 0, 0]}}}),
        "attack": once(0.8, {
            "head": {"rotation": {0: [0, 0, 0], 0.5: [-30, 0, 0], 0.6: [35, 0, 0], 0.8: [0, 0, 0]}},
            "wing_right": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, 40], 0.8: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, -40], 0.8: [0, 0, 0]}},
        }),
        "skill_needle": once(1.0, {
            "wing_right": {"rotation": {0: [0, 0, 0], 0.6: [0, -20, 50], 0.75: [0, 10, -30], 1.0: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.6: [0, 20, -50], 0.75: [0, -10, 30], 1.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.6: [-20, 0, 0], 1.0: [0, 0, 0]}},
        }),
        # 吹雪（羽を大きく広げて、ゆっくり羽ばたく）
        "skill_blizzard": once(2.2, {
            "wing_right": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, 60], 1.0: [0, 0, -20], 1.6: [0, 0, 60], 2.2: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, -60], 1.0: [0, 0, 20], 1.6: [0, 0, -60], 2.2: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.4: [-35, 0, 0], 1.8: [-35, 0, 0], 2.2: [0, 0, 0]}},
            "body": {"scale": {0: [1, 1, 1], 0.4: [1.12, 1.12, 1.12], 1.8: [1.12, 1.12, 1.12], 2.2: [1, 1, 1]}},
        }),
        "death": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 160]}, "position": {0: [0, 0, 0], 1.0: [0, -6, 0]}},
            "wing_right": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 70]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, -70]}},
        }, hold=True),
    }
    return {"animation.frost_bird." + k: v for k, v in t.items()}


# ================================================================ 村長の残響オルド（ボス）

def build_ordo():
    g = GeoModel("ordo", 256, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 18, 0), parent="root")
    g.bone("coat", (0, 24, 0), parent="body")
    g.bone("head", (0, 38, 0), parent="body")
    g.bone("arm_right", (-9.5, 36, 0), parent="body")
    g.bone("arm_left", (9.5, 36, 0), parent="body")
    g.bone("staff", (-11, 17, -2), parent="arm_right")
    g.bone("leg_right", (-3.5, 18, 0), parent="root")
    g.bone("leg_left", (3.5, 18, 0), parent="root")
    g.bone("aura", (0, 20, 0), parent="root")
    # 脚と長靴
    for name, x in (("leg_right", -3.5), ("leg_left", 3.5)):
        g.cube(name, name, (x - 2.5, 3, -2.5), (5, 15, 5), "coat")
        g.cube(name, name + "_boot", (x - 3, 0, -3.5), (6, 5, 7), "leather_dark")
    # 外套のすそ（ひだ）と胴
    g.cube("coat", "skirt", (-8.5, 8, -5.5), (17, 16, 11), "coat", tags={"folds": True})
    g.cube("body", "torso", (-8, 24, -5), (16, 13, 10), "coat", tags={"folds": True})
    g.cube("body", "belt", (-8.5, 24, -5.5), (17, 2, 11), "leather_dark")
    g.cube("body", "buckle", (-1.5, 24, -6), (3, 2, 1), "gold")
    g.cube("body", "trim", (-1, 8, -6), (2, 29, 1), "gold")
    g.cube("body", "collar", (-9.5, 34, -6.5), (19, 4, 13), "fur_collar")
    # 頭（淡い氷色の肌）・帽子・ひげ・光る目
    g.cube("head", "head", (-5, 38, -5), (10, 10, 10), "echo_skin")
    g.cube("head", "hat", (-5.5, 46, -5.5), (11, 5, 11), "fur_collar")
    g.cube("head", "hat_top", (-4, 51, -4), (8, 1, 8), "fur_collar")
    g.cube("head", "brow", (-4.5, 44, -5.6), (9, 1, 1), "beard")
    g.cube("head", "eye_r", (-3.5, 42.5, -5.1), (2, 1, 0), "glow_frost", glow=True)
    g.cube("head", "eye_l", (1.5, 42.5, -5.1), (2, 1, 0), "glow_frost", glow=True)
    g.cube("head", "beard", (-4.5, 33, -6), (9, 8, 2), "beard", tags={"tips": True})
    g.cube("head", "beard_long", (-3, 27, -6.5), (6, 6, 2), "beard", tags={"tips": True})
    g.cube("head", "mustache", (-4, 40, -6), (8, 1, 1), "beard")
    # 腕（外套の袖・毛皮の袖口・手）
    g.cube("arm_right", "arm_right", (-12.5, 20, -2.5), (5, 17, 5), "coat")
    g.cube("arm_right", "cuff_right", (-13, 20, -3), (6, 3, 6), "fur_collar")
    g.cube("arm_right", "hand_right", (-12, 16, -2), (4, 4, 4), "echo_skin")
    g.cube("arm_left", "arm_left", (7.5, 20, -2.5), (5, 17, 5), "coat")
    g.cube("arm_left", "cuff_left", (7, 20, -3), (6, 3, 6), "fur_collar")
    g.cube("arm_left", "hand_left", (8, 16, -2), (4, 4, 4), "echo_skin")
    # ランタンの杖（右手。下は地面まで、上に氷の灯）
    g.cube("staff", "pole", (-12, -2, -3), (2, 44, 2), "walnut")
    g.cube("staff", "lantern_cap", (-13.5, 47, -4.5), (5, 1, 5), "iron")
    g.cube("staff", "lantern", (-13, 42, -4), (4, 5, 4), "iron")
    g.cube("staff", "lantern_light", (-12.5, 42.5, -3.5), (3, 4, 3), "glow_frost", glow=True)
    # 記憶を取り戻すときに広がる、淡い光の輪（ふだんは小さく隠す）
    g.cube("aura", "aura", (-12, 0.2, -12), (24, 0, 24), "glow_frost", glow=True)
    return g


def ordo_anims():
    walk = {
        "leg_right": {"rotation": {0: [22, 0, 0], 0.6: [-22, 0, 0], 1.2: [22, 0, 0]}},
        "leg_left": {"rotation": {0: [-22, 0, 0], 0.6: [22, 0, 0], 1.2: [-22, 0, 0]}},
        "arm_left": {"rotation": {0: [-15, 0, 0], 0.6: [15, 0, 0], 1.2: [-15, 0, 0]}},
        "arm_right": {"rotation": {0: [-8, 0, 0], 0.6: [-4, 0, 0], 1.2: [-8, 0, 0]}},
        "coat": {"rotation": {0: [4, 0, 0], 0.6: [-4, 0, 0], 1.2: [4, 0, 0]}},
    }
    hide_aura = {"aura": {"scale": {0: [0, 0, 0]}}}
    t = {
        "idle": loop(4.0, {
            "body": {"rotation": {0: [4, 0, 0], 2.0: [6, 0, 0], 4.0: [4, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.6: [0, 0, 0], 2.0: [0, 25, 0], 2.8: [0, -20, 0], 3.4: [0, 0, 0], 4.0: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [-8, 0, 0], 4.0: [-8, 0, 0]}},
            **hide_aura,
        }),
        "walk": loop(1.2, {**walk, **hide_aura}),
        # 杖の薙ぎ払い（予兆 1.2 秒：杖を後ろへ引いて、前を横に払う）
        "attack": once(1.8, {
            "arm_right": {"rotation": {0: [-8, 0, 0], 1.1: [-60, 60, 30], 1.3: [-80, -70, -20], 1.8: [-8, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.1: [0, 35, 0], 1.3: [0, -40, 0], 1.8: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.1: [0, -20, 0], 1.3: [0, 20, 0], 1.8: [0, 0, 0]}},
            **hide_aura,
        }),
        # 氷柱の道（杖を高く上げて、地面へ突き立てる）
        "skill_icicle": once(2.0, {
            "arm_right": {"rotation": {0: [-8, 0, 0], 1.2: [-170, 0, 10], 1.4: [-60, 0, 0], 2.0: [-8, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.2: [-12, 0, 0], 1.4: [18, 0, 0], 2.0: [0, 0, 0]}},
            **hide_aura,
        }),
        # 吹雪（杖を掲げて、ぐるりと回る）
        "skill_blizzard": once(2.6, {
            "arm_right": {"rotation": {0: [-8, 0, 0], 0.6: [-175, 0, 0], 2.2: [-175, 0, 0], 2.6: [-8, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-40, 0, -60], 2.2: [-40, 0, -60], 2.6: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.2: [0, 180, 0], 2.2: [0, 360, 0], 2.6: [0, 360, 0]}},
            **hide_aura,
        }),
        # 疑いの眼差し（身を乗り出して、じっと見る）
        "skill_gaze": once(2.8, {
            "body": {"rotation": {0: [0, 0, 0], 0.6: [18, 0, 0], 2.4: [18, 0, 0], 2.8: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-12, 0, 0], 2.4: [-12, 0, 0], 2.8: [0, 0, 0]},
                     "scale": {0: [1, 1, 1], 0.6: [1.08, 1.08, 1.08], 2.4: [1.08, 1.08, 1.08], 2.8: [1, 1, 1]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-85, 15, 0], 2.4: [-85, 15, 0], 2.8: [0, 0, 0]}},
            **hide_aura,
        }),
        # 霜狼を呼ぶ（左手を高く上げる）
        "skill_call": once(2.0, {
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-165, 0, -15], 1.6: [-165, 0, -15], 2.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [-25, 0, 0], 1.6: [-25, 0, 0], 2.0: [0, 0, 0]}},
            **hide_aura,
        }),
        # 記憶の結晶を受け取る（よろめいて、左手を額に）
        "skill_receive": once(3.0, {
            "body": {"rotation": {0: [0, 0, 0], 0.5: [-14, 0, 8], 2.4: [-10, 0, 6], 3.0: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.5: [0, 0, 2], 3.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.5: [-150, -30, 0], 2.4: [-150, -30, 0], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.5: [20, 0, 0], 2.4: [15, 0, 0], 3.0: [0, 0, 0]}},
            "aura": {"scale": {0: [0, 0, 0], 0.5: [0.7, 1, 0.7], 2.4: [1.0, 1, 1.0], 3.0: [0, 0, 0]}},
        }),
        # 思い出す（ひざをつき、杖を置いて、頭を下げる）
        "skill_remember": once(4.0, {
            "body": {"position": {0: [0, 0, 0], 1.0: [0, -8, 0]}, "rotation": {0: [0, 0, 0], 1.0: [18, 0, 0], 4.0: [24, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.0: [-90, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, -6, 3]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, -2, 0]}},
            "arm_right": {"rotation": {0: [-8, 0, 0], 1.0: [-40, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [25, 0, 0], 4.0: [30, 0, 0]}},
            "aura": {"scale": {0: [0, 0, 0], 1.0: [1.2, 1, 1.2], 4.0: [1.6, 1, 1.6]}},
        }, hold=True),
        "death": once(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.0: [0, -8, 0]}, "rotation": {0: [0, 0, 0], 1.0: [24, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.0: [-90, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, -6, 3]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [30, 0, 0]}},
            "aura": {"scale": {0: [0, 0, 0], 1.0: [1.6, 1, 1.6], 3.0: [2.4, 1, 2.4]}},
        }, hold=True),
    }
    return {"animation.ordo." + k: v for k, v in t.items()}


MODELS = [(build_frost_bear, frost_bear_anims), (build_frost_bird, frost_bird_anims), (build_ordo, ordo_anims)]


def build_all(repo):
    frost_wolf(repo)
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(VillagePainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "frost_bear": dict(scale=6.0, center=(0, 14, -4), size=(300, 260)),
        "frost_bird": dict(scale=7.0, center=(0, 8, 0), size=(320, 220)),
        "ordo": dict(scale=4.2, center=(0, 26, 0), size=(260, 340)),
    }
    poses = {
        "frost_bear": {"body": ((-28, 0, 0), 1.0, (0, 3, 2)), "jaw": ((38, 0, 0),), "head": ((-20, 0, 0),)},
        "frost_bird": {"wing_right": ((0, 0, 60),), "wing_left": ((0, 0, -60),), "head": ((-35, 0, 0),)},
        "ordo": {"arm_right": ((-170, 0, 10),), "arm_left": ((-150, -30, 0),), "aura": ((0, 0, 0), 0.0)},
    }
    for name, (g, tex) in models.items():
        lay = layout[name]
        tiles = []
        for yaw, pitch in ((150, 14), (-30, 14), (180, 0), (90, 0)):
            tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, **lay))
        tiles.append(render_many([(posed(g, poses[name]), tex)], yaw=150, pitch=14, **lay))
        w, h = lay["size"]
        s = Image.new("RGBA", (w * len(tiles), h + 22), (44, 56, 60, 255))
        for k, im in enumerate(tiles):
            s.alpha_composite(im, (w * k, 22))
        ImageDraw.Draw(s).text((6, 2), name + "  （斜め前・斜め後ろ・正面・横・技の形）", fill=(235, 235, 220), font=sheet_font(15))
        sheets.append(s)
    W = max(s.size[0] for s in sheets)
    H = sum(s.size[1] for s in sheets)
    sheet = Image.new("RGBA", (W, H), (36, 44, 48, 255))
    y = 0
    for s in sheets:
        sheet.alpha_composite(s, (0, y))
        y += s.size[1]
    path = os.path.join(out_dir, "village_models_check.png")
    sheet.save(path)
    return path


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models = build_all(repo)
    if len(sys.argv) > 2:
        print(check_sheet(models, sys.argv[2]))
    print("models written:", ", ".join(["frost_wolf"] + list(models)))


if __name__ == "__main__":
    main()
