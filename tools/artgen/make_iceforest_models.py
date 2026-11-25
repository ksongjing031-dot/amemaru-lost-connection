"""7階「氷の森林」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月25日【補完】）。

  python3 tools/artgen/make_iceforest_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の7階：鏡のような氷の木々の森。氷に映る自分の姿が、ときどき別の仲間の姿になる。雑魚は写し身（プレイヤーの姿を真似る。
名前が「？？？」）・氷角鹿・つらら蝙蝠・吹雪の精。ボスは鏡像獣リフレクタ（パーティメンバーの写し身を作る）。第2章の色は
氷青・白銀・オーロラの緑紫。作るもの（ファイルの名前は敵のIDと同じ）:
  ice_stag         氷角鹿：細い脚の鹿。霜の毛並み（白と淡い青）に、透きとおった氷の角（枝分かれ）。目は淡い水色に光る
  icicle_bat       つらら蝙蝠：夜の色の小さな体と、大きな膜の翼。翼のふちに、つららが下がる
  blizzard_spirit  吹雪の精：雪の衣のフードをかぶった、顔のない精。顔の闇に2つの光る目。脚はなく、裾が吹雪の尾になって流れる。
                   衣のふちに、淡い紫のオーロラ色
  reflecta         鏡像獣リフレクタ：獅子のような大きな獣。体は氷と鏡の板でできていて、たてがみは鏡の板が放射状に並ぶ。背に
                   プリズムの結晶（淡い紫と水色に光る）、尾の先にもプリズム。目は白く光る
写し身は、写した人と同じ姿（プレイヤーのモデル。client/render/MimicRenderer.java）。
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import loop, once, posed, sheet_font  # noqa: E402
from make_village_models import VILLAGE, VillagePainter  # noqa: E402
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

ICEFOREST = {
    # 鏡の板（白銀。縁は明るく、面に斜めの映り込みの帯）
    "mirror": ramp("#4E5A6C", "#6C7A8E", "#8C9AAE", "#AEBBCC", "#CCD7E4", "#E6EDF4", "#FFFFFF"),
    # 雪の衣（白にうすい紫）
    "snow_robe": ramp("#6E6A86", "#8A86A2", "#A6A2BC", "#C2BED4", "#D8D5E6", "#EAE8F2", "#FAF9FD"),
    # 蝙蝠の体・翼の膜（夜の青）
    "bat": ramp("#0C1018", "#141A26", "#1E2636", "#2A3448", "#38445C", "#4A5874", "#5E6E8E"),
    # 光る：プリズム（淡い紫〜水色）
    "glow_prism": ramp("#5A3E9A", "#7356BA", "#8E74D2", "#A996E4", "#C6BAF0", "#E2DCFA", "#FFFFFF"),
}
for _k, _v in ICEFOREST.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class IceForestPainter(VillagePainter):
    def paint_mirror(self, c):
        """鏡の板：なめらかで、面に斜めの明るい映り込みの帯。縁は明るい"""
        tone = 4.2 + self.bevel(c, up=1.4, down=1.0)
        band = (c.i + c.j * 2 + self.eid(c)) % 9
        if band in (0, 1):
            tone += 1.2
        elif band == 5:
            tone -= 0.6
        return pick(ICEFOREST["mirror"], tone)

    def paint_snow_robe(self, c):
        strand = hsh(int(np.floor(c.p[0] * 1.5 + c.p[2])), int(np.floor(c.p[1] / 2.0)), 941)
        tone = 4.3 + (strand - 0.5) * 0.9 + self.bevel(c, up=0.8, down=0.9)
        if c.el.tags.get("hem") and c.edge_dist()["bottom"] <= 1 and c.i % 3 != 1:
            return None   # すそはちぎれた吹雪のように
        return pick(ICEFOREST["snow_robe"], tone)

    def paint_bat(self, c):
        tone = self.std(c, "bat", base=3.4, noise=0.4, blob=0.3, up=1.0, down=0.8)
        if c.el.tags.get("membrane") and (c.i % 4 == 0):
            tone += 0.9   # 翼の骨の筋
        return pick(ICEFOREST["bat"], tone)

    def paint_glow_prism(self, c):
        return self.paint_glow(c, "glow_prism", base=4.2)

    def ramp(self, name):
        return ICEFOREST.get(name) or VILLAGE.get(name) or super().ramp(name)


# ================================================================ 氷角鹿

def build_ice_stag():
    g = GeoModel("ice_stag", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 14, 0), parent="root")
    g.bone("neck", (0, 17, -6), parent="body")
    g.bone("head", (0, 22, -9), parent="neck")
    g.bone("antlers", (0, 25, -10), parent="head")
    for name, x, z in (("leg_fr", -2, -5), ("leg_fl", 2, -5), ("leg_br", -2, 5), ("leg_bl", 2, 5)):
        g.bone(name, (x, 12, z), parent="body")
    g.bone("tail", (0, 17, 7), parent="body")
    g.cube("body", "body", (-3, 11, -7), (6, 7, 14), "frost_fur")
    g.cube("body", "chest", (-2, 10, -8), (4, 6, 3), "frost_fur")
    g.cube("neck", "neck", (-1, 15, -9), (3, 8, 3), "frost_fur", rot=(-25, 0, 0), pivot=(0, 17, -7))
    g.cube("head", "head", (-2, 20, -13), (4, 4, 6), "frost_fur")
    g.cube("head", "snout", (-1, 20, -16), (3, 3, 3), "frost_fur")
    g.cube("head", "nose", (-1, 21, -17), (2, 1, 1), "frost_dark")
    g.cube("head", "ear_r", (-4, 23, -10), (2, 1, 1), "frost_fur", rot=(0, 0, 20), pivot=(-2, 23, -10))
    g.cube("head", "ear_l", (2, 23, -10), (2, 1, 1), "frost_fur", rot=(0, 0, -20), pivot=(2, 23, -10))
    g.cube("head", "eye_r", (-2.1, 22, -12), (0, 1, 1), "glow_frost", glow=True)
    g.cube("head", "eye_l", (2.1, 22, -12), (0, 1, 1), "glow_frost", glow=True)
    # 氷の角（左右に枝分かれ）
    for side, sx in (("r", -1), ("l", 1)):
        g.cube("antlers", "beam_" + side, (sx * 1.5 - 0.5, 24, -11), (1, 7, 1), "ice", rot=(-10, 0, sx * 25), pivot=(sx * 1.5, 24, -10.5))
        g.cube("antlers", "tine_a_" + side, (sx * 3.5 - 0.5, 27, -11), (1, 4, 1), "ice", rot=(-20, 0, -sx * 10), pivot=(sx * 3.5, 27, -10.5))
        g.cube("antlers", "tine_b_" + side, (sx * 4.5 - 0.5, 29, -10), (1, 3, 1), "ice", rot=(25, 0, sx * 35), pivot=(sx * 4.5, 29, -9.5))
        g.cube("antlers", "tine_c_" + side, (sx * 2.5 - 0.5, 28, -12), (1, 3, 1), "ice", rot=(-45, 0, 0), pivot=(sx * 2.5, 28, -11.5))
    for name, x, z in (("leg_fr", -2, -5), ("leg_fl", 2, -5), ("leg_br", -2, 5), ("leg_bl", 2, 5)):
        g.cube(name, name, (x - 1, 2, z - 1), (2, 10, 2), "frost_fur")
        g.cube(name, name + "_hoof", (x - 1, 0, z - 1), (2, 2, 2), "frost_dark")
    g.cube("tail", "tail", (-1, 15, 7), (2, 3, 2), "frost_fur", rot=(30, 0, 0), pivot=(0, 17, 7))
    return g


def ice_stag_anims():
    walk = {
        "leg_fr": {"rotation": {0: [25, 0, 0], 0.4: [-25, 0, 0], 0.8: [25, 0, 0]}},
        "leg_bl": {"rotation": {0: [25, 0, 0], 0.4: [-25, 0, 0], 0.8: [25, 0, 0]}},
        "leg_fl": {"rotation": {0: [-25, 0, 0], 0.4: [25, 0, 0], 0.8: [-25, 0, 0]}},
        "leg_br": {"rotation": {0: [-25, 0, 0], 0.4: [25, 0, 0], 0.8: [-25, 0, 0]}},
        "neck": {"rotation": {0: [0, 0, 0], 0.4: [4, 0, 0], 0.8: [0, 0, 0]}},
    }
    t = {
        "idle": loop(3.0, {
            "neck": {"rotation": {0: [0, 0, 0], 1.2: [6, 0, 0], 1.8: [6, 15, 0], 2.4: [0, 0, 0], 3.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.2: [0, 0, 20], 0.4: [0, 0, -20], 0.6: [0, 0, 0], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(0.8, walk),
        # 角で突く（頭を下げて、前へ）
        "attack": once(1.0, {
            "neck": {"rotation": {0: [0, 0, 0], 0.6: [35, 0, 0], 0.75: [-10, 0, 0], 1.0: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.6: [0, 0, 1], 0.75: [0, 0, -2], 1.0: [0, 0, 0]}},
        }),
        # 突進（頭を低く下げたまま、前足をかく）
        "skill_charge": once(1.4, {
            "neck": {"rotation": {0: [0, 0, 0], 0.4: [45, 0, 0], 1.2: [45, 0, 0], 1.4: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 0.3: [-40, 0, 0], 0.5: [10, 0, 0], 0.7: [-40, 0, 0], 0.9: [10, 0, 0], 1.4: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.4: [-6, 0, 0], 1.2: [-6, 0, 0], 1.4: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 90]}, "position": {0: [0, 0, 0], 1.0: [0, -6, 0]}},
            "neck": {"rotation": {0: [0, 0, 0], 1.0: [30, 0, 0]}},
        }, hold=True),
    }
    return {"animation.ice_stag." + k: v for k, v in t.items()}


# ================================================================ つらら蝙蝠

def build_icicle_bat():
    g = GeoModel("icicle_bat", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 8, 0), parent="root")
    g.bone("head", (0, 11, -1), parent="body")
    g.bone("wing_right", (-2, 10, 0), parent="body")
    g.bone("wing_left", (2, 10, 0), parent="body")
    g.bone("feet", (0, 6, 0), parent="body")
    g.cube("body", "body", (-2, 6, -2), (4, 5, 4), "bat")
    g.cube("head", "head", (-2, 11, -3), (4, 3, 3), "bat")
    g.cube("head", "ear_r", (-2, 14, -2), (1, 2, 1), "bat")
    g.cube("head", "ear_l", (1, 14, -2), (1, 2, 1), "bat")
    g.cube("head", "eye_r", (-1.5, 12, -3.1), (1, 1, 0), "glow_frost", glow=True)
    g.cube("head", "eye_l", (0.5, 12, -3.1), (1, 1, 0), "glow_frost", glow=True)
    g.cube("head", "fang", (-0.5, 10, -3), (1, 1, 1), "ice")
    for side, sx in (("r", -1), ("l", 1)):
        bone = "wing_right" if sx < 0 else "wing_left"
        x0 = -12 if sx < 0 else 2
        g.cube(bone, "wing_" + side, (x0, 9, -1), (10, 1, 6), "bat", tags={"membrane": True})
        g.cube(bone, "wing_tip_" + side, (x0 - 3 if sx < 0 else x0 + 10, 9, 0), (3, 1, 4), "bat", tags={"membrane": True})
        for k, dx in enumerate((2, 5, 8)):
            x = (x0 + 10 - dx) if sx < 0 else (x0 + dx - 1)
            g.cube(bone, "icicle_%s%d" % (side, k), (x, 6, 1), (1, 3, 1), "ice")
    g.cube("feet", "feet", (-1, 5, 0), (2, 1, 1), "frost_dark")
    return g


def icicle_bat_anims():
    flap = {"wing_right": {"rotation": {0: [0, 0, 30], 0.2: [0, 0, -35], 0.4: [0, 0, 30]}},
            "wing_left": {"rotation": {0: [0, 0, -30], 0.2: [0, 0, 35], 0.4: [0, 0, -30]}},
            "body": {"position": {0: [0, 0, 0], 0.2: [0, 0.6, 0], 0.4: [0, 0, 0]}}}
    t = {
        "idle": loop(0.4, flap),
        "walk": loop(0.32, {**flap, "body": {"rotation": {0: [15, 0, 0], 0.32: [15, 0, 0]}}}),
        "attack": once(0.7, {
            "head": {"rotation": {0: [0, 0, 0], 0.4: [-25, 0, 0], 0.5: [30, 0, 0], 0.7: [0, 0, 0]}},
            "wing_right": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, 45], 0.7: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, -45], 0.7: [0, 0, 0]}},
        }),
        # つららを撃つ（翼を大きく開いて、前へ打ち振る）
        "skill_needle": once(1.0, {
            "wing_right": {"rotation": {0: [0, 0, 0], 0.6: [0, -30, 60], 0.75: [0, 20, -40], 1.0: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.6: [0, 30, -60], 0.75: [0, -20, 40], 1.0: [0, 0, 0]}},
        }),
        "death": once(1.0, {
            "body": {"rotation": {0: [0, 0, 0], 0.8: [0, 0, 170]}, "position": {0: [0, 0, 0], 0.8: [0, -5, 0]}},
        }, hold=True),
    }
    return {"animation.icicle_bat." + k: v for k, v in t.items()}


# ================================================================ 吹雪の精

def build_blizzard_spirit():
    g = GeoModel("blizzard_spirit", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), parent="root")
    g.bone("head", (0, 22, 0), parent="body")
    g.bone("arm_right", (-5, 20, 0), parent="body")
    g.bone("arm_left", (5, 20, 0), parent="body")
    g.bone("tail", (0, 10, 0), parent="body")
    g.cube("body", "robe", (-4, 10, -3), (8, 12, 6), "snow_robe")
    g.cube("body", "collar", (-5, 20, -4), (10, 2, 8), "snow_robe")
    g.cube("body", "aurora_trim", (-4.5, 10, -3.5), (9, 1, 7), "glow_prism", glow=True)
    g.cube("head", "hood", (-4, 21, -4), (8, 8, 8), "snow_robe")
    g.cube("head", "hood_tip", (-2, 29, -1), (4, 3, 4), "snow_robe", rot=(-20, 0, 0), pivot=(0, 29, 1))
    g.cube("head", "face", (-3, 22, -4.2), (6, 6, 1), "frost_dark")
    g.cube("head", "eye_r", (-2, 25, -4.4), (1, 1, 0), "glow_frost", glow=True)
    g.cube("head", "eye_l", (1, 25, -4.4), (1, 1, 0), "glow_frost", glow=True)
    g.cube("arm_right", "sleeve_r", (-7, 13, -2), (3, 8, 3), "snow_robe", tags={"hem": True})
    g.cube("arm_left", "sleeve_l", (4, 13, -2), (3, 8, 3), "snow_robe", tags={"hem": True})
    g.cube("tail", "hem_a", (-3, 4, -2), (6, 6, 5), "snow_robe", tags={"hem": True})
    g.cube("tail", "hem_b", (-2, 0, 0), (4, 5, 4), "snow_robe", tags={"hem": True}, rot=(15, 0, 0), pivot=(0, 4, 1))
    return g


def blizzard_spirit_anims():
    float_ = {"body": {"position": {0: [0, 0, 0], 1.0: [0, 1.2, 0], 2.0: [0, 0, 0]}},
              "tail": {"rotation": {0: [8, 0, -6], 1.0: [14, 0, 6], 2.0: [8, 0, -6]}},
              "arm_right": {"rotation": {0: [-10, 0, 8], 1.0: [-14, 0, 12], 2.0: [-10, 0, 8]}},
              "arm_left": {"rotation": {0: [-10, 0, -8], 1.0: [-14, 0, -12], 2.0: [-10, 0, -8]}}}
    t = {
        "idle": loop(2.0, float_),
        "walk": loop(2.0, {**float_, "body": {"rotation": {0: [12, 0, 0], 2.0: [12, 0, 0]},
                                              "position": {0: [0, 0, 0], 1.0: [0, 1.2, 0], 2.0: [0, 0, 0]}}}),
        "attack": once(0.8, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-120, 0, 0], 0.6: [-30, 0, 0], 0.8: [0, 0, 0]}},
        }),
        # 氷のつぶて（両手を前へ）
        "skill_needle": once(1.0, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.6: [-90, -20, 0], 0.75: [-70, 0, 0], 1.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.6: [-90, 20, 0], 0.75: [-70, 0, 0], 1.0: [0, 0, 0]}},
        }),
        # 吹雪（両腕を大きく広げ、くるりと回る）
        "skill_blizzard": once(2.2, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, 100], 1.8: [0, 0, 100], 2.2: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, -100], 1.8: [0, 0, -100], 2.2: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 180, 0], 2.0: [0, 360, 0], 2.2: [0, 360, 0]}},
        }),
        "death": once(1.4, {
            "body": {"scale": {0: [1, 1, 1], 1.2: [0.2, 1.4, 0.2]}, "position": {0: [0, 0, 0], 1.2: [0, 4, 0]}},
        }, hold=True),
    }
    return {"animation.blizzard_spirit." + k: v for k, v in t.items()}


# ================================================================ 鏡像獣リフレクタ（ボス）

def build_reflecta():
    g = GeoModel("reflecta", 256, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 20, 0), parent="root")
    g.bone("neck", (0, 26, -10), parent="body")
    g.bone("head", (0, 30, -15), parent="neck")
    g.bone("jaw", (0, 27, -17), parent="head")
    g.bone("mane", (0, 30, -12), parent="neck")
    g.bone("spines", (0, 30, 0), parent="body")
    for name, x, z in (("leg_fr", -6, -9), ("leg_fl", 6, -9), ("leg_br", -6, 9), ("leg_bl", 6, 9)):
        g.bone(name, (x, 18, z), parent="body")
    g.bone("tail", (0, 26, 13), parent="body")
    # 胴（鏡と氷の板）
    g.cube("body", "torso", (-7, 17, -12), (14, 12, 25), "mirror")
    g.cube("body", "belly", (-6, 15, -10), (12, 3, 21), "frost_dark")
    g.cube("body", "plate_r", (-8, 19, -9), (1, 8, 16), "ice")
    g.cube("body", "plate_l", (7, 19, -9), (1, 8, 16), "ice")
    # 首とたてがみ（鏡の板が放射状に）
    g.cube("neck", "neck", (-4, 24, -16), (8, 9, 8), "mirror", rot=(-20, 0, 0), pivot=(0, 26, -12))
    for k in range(10):
        ang = -90 + k * 20
        g.cube("mane", "mane_%d" % k, (-1, 32, -13), (2, 9, 1), "mirror", rot=(0, 0, ang), pivot=(0, 31, -12.5))
    # 頭・あご・光る目
    g.cube("head", "skull", (-5, 27, -24), (10, 8, 10), "mirror")
    g.cube("head", "muzzle", (-3, 26, -28), (6, 5, 4), "ice")
    g.cube("head", "brow", (-5, 34, -24), (10, 2, 3), "ice")
    g.cube("head", "eye_r", (-4, 31, -24.2), (2, 1, 0), "glow_prism", glow=True)
    g.cube("head", "eye_l", (2, 31, -24.2), (2, 1, 0), "glow_prism", glow=True)
    g.cube("head", "ear_r", (-6, 34, -18), (2, 4, 2), "mirror", rot=(0, 0, 25), pivot=(-5, 34, -17))
    g.cube("head", "ear_l", (4, 34, -18), (2, 4, 2), "mirror", rot=(0, 0, -25), pivot=(5, 34, -17))
    g.cube("jaw", "jaw", (-3, 24, -28), (6, 3, 6), "ice")
    g.cube("jaw", "fang_r", (-2, 23, -28), (1, 2, 1), "ice")
    g.cube("jaw", "fang_l", (1, 23, -28), (1, 2, 1), "ice")
    # 背のプリズム（光る）
    for k, (z, h) in enumerate(((-8, 7), (-2, 9), (4, 8), (9, 6))):
        g.cube("spines", "prism_%d" % k, (-1, 29, z), (2, h, 2), "glow_prism", glow=True, rot=(-15, 0, 0), pivot=(0, 29, z + 1))
        g.cube("spines", "shard_%d" % k, (-2, 29, z + 1), (4, 2, 1), "ice")
    # 脚と氷の爪
    for name, x, z in (("leg_fr", -6, -9), ("leg_fl", 6, -9), ("leg_br", -6, 9), ("leg_bl", 6, 9)):
        g.cube(name, name, (x - 2.5, 3, z - 2.5), (5, 15, 5), "mirror")
        g.cube(name, name + "_paw", (x - 3, 0, z - 4), (6, 3, 7), "frost_dark")
        g.cube(name, name + "_claw", (x - 2, 0, z - 5), (4, 2, 1), "ice")
    # 尾（先にプリズム）
    g.cube("tail", "tail", (-1.5, 24, 13), (3, 3, 12), "mirror", rot=(-25, 0, 0), pivot=(0, 26, 13))
    g.cube("tail", "tail_prism", (-2, 30, 23), (4, 5, 4), "glow_prism", glow=True, rot=(-25, 0, 0), pivot=(0, 26, 13))
    return g


def reflecta_anims():
    walk = {
        "leg_fr": {"rotation": {0: [20, 0, 0], 0.7: [-20, 0, 0], 1.4: [20, 0, 0]}},
        "leg_bl": {"rotation": {0: [20, 0, 0], 0.7: [-20, 0, 0], 1.4: [20, 0, 0]}},
        "leg_fl": {"rotation": {0: [-20, 0, 0], 0.7: [20, 0, 0], 1.4: [-20, 0, 0]}},
        "leg_br": {"rotation": {0: [-20, 0, 0], 0.7: [20, 0, 0], 1.4: [-20, 0, 0]}},
        "tail": {"rotation": {0: [0, -10, 0], 0.7: [0, 10, 0], 1.4: [0, -10, 0]}},
    }
    t = {
        "idle": loop(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.5: [0, -0.6, 0], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [0, 0, 0], 1.6: [0, 20, 0], 2.4: [0, -15, 0], 3.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, -10, 0], 1.5: [0, 10, 0], 3.0: [0, -10, 0]}},
            "mane": {"scale": {0: [1, 1, 1], 1.5: [1.05, 1.05, 1.05], 3.0: [1, 1, 1]}},
        }),
        "walk": loop(1.4, walk),
        # 鏡爪（右前足を高く上げて、前を払う）
        "attack": once(1.6, {
            "leg_fr": {"rotation": {0: [0, 0, 0], 1.0: [-80, 0, -20], 1.2: [30, 0, 20], 1.6: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.0: [-10, 15, 0], 1.2: [6, -15, 0], 1.6: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 1.0: [25, 0, 0], 1.3: [0, 0, 0]}},
        }),
        # 鏡片の雨（身をかがめ、背のプリズムを震わせて、前へ放つ）
        "skill_shards": once(1.8, {
            "body": {"rotation": {0: [0, 0, 0], 1.2: [12, 0, 0], 1.4: [-8, 0, 0], 1.8: [0, 0, 0]}},
            "spines": {"scale": {0: [1, 1, 1], 1.2: [1.2, 1.35, 1.2], 1.4: [1, 1, 1]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [-20, 0, 0], 1.4: [10, 0, 0], 1.8: [0, 0, 0]}},
        }),
        # 屈折の光（首をもたげ、口を開いて、光を吐く）
        "skill_prism": once(2.4, {
            "neck": {"rotation": {0: [0, 0, 0], 0.8: [-25, 0, 0], 2.0: [-10, 0, 0], 2.4: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 1.6: [40, 0, 0], 2.2: [40, 0, 0], 2.4: [0, 0, 0]}},
            "mane": {"scale": {0: [1, 1, 1], 1.6: [1.25, 1.25, 1.25], 2.4: [1, 1, 1]}},
        }),
        # 乱反射（後ろ足で立ち上がり、地面を打つ）
        "skill_scatter": once(2.0, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [-30, 0, 0], 1.5: [8, 0, 0], 2.0: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.0: [0, 4, 0], 1.5: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 1.0: [-60, 0, 0], 1.5: [10, 0, 0], 2.0: [0, 0, 0]}},
            "leg_fl": {"rotation": {0: [0, 0, 0], 1.0: [-60, 0, 0], 1.5: [10, 0, 0], 2.0: [0, 0, 0]}},
        }),
        # 鏡写し（たてがみの鏡を大きく広げ、ぐるりと回す）
        "skill_mirror": once(2.4, {
            "mane": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 360]}, "scale": {0: [1, 1, 1], 0.8: [1.5, 1.5, 1.5], 2.0: [1.5, 1.5, 1.5], 2.4: [1, 1, 1]}},
            "neck": {"rotation": {0: [0, 0, 0], 0.8: [-30, 0, 0], 2.0: [-30, 0, 0], 2.4: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.8: [35, 0, 0], 2.0: [35, 0, 0], 2.4: [0, 0, 0]}},
        }),
        "death": once(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.4: [0, -8, 0]}, "rotation": {0: [0, 0, 0], 1.4: [0, 0, 20]}},
            "neck": {"rotation": {0: [0, 0, 0], 1.4: [30, 0, 0]}},
            "mane": {"scale": {0: [1, 1, 1], 1.0: [1.6, 1.6, 1.6], 2.6: [0, 0, 0]}},
            "spines": {"scale": {0: [1, 1, 1], 2.0: [0, 0, 0]}},
        }, hold=True),
    }
    return {"animation.reflecta." + k: v for k, v in t.items()}


MODELS = [(build_ice_stag, ice_stag_anims), (build_icicle_bat, icicle_bat_anims), (build_blizzard_spirit, blizzard_spirit_anims),
          (build_reflecta, reflecta_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(IceForestPainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "ice_stag": dict(scale=6.0, center=(0, 16, -4), size=(300, 280)),
        "icicle_bat": dict(scale=9.0, center=(0, 9, 0), size=(300, 200)),
        "blizzard_spirit": dict(scale=6.5, center=(0, 15, 0), size=(240, 280)),
        "reflecta": dict(scale=3.6, center=(0, 22, -4), size=(320, 300)),
    }
    poses = {
        "ice_stag": {"neck": ((40, 0, 0),)},
        "icicle_bat": {"wing_right": ((0, -30, 60),), "wing_left": ((0, 30, -60),)},
        "blizzard_spirit": {"arm_right": ((0, 0, 100),), "arm_left": ((0, 0, -100),)},
        "reflecta": {"jaw": ((40, 0, 0),), "neck": ((-25, 0, 0),), "mane": ((0, 0, 0), 1.4)},
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
    path = os.path.join(out_dir, "iceforest_models_check.png")
    sheet.save(path)
    return path


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models = build_all(repo)
    if len(sys.argv) > 2:
        print(check_sheet(models, sys.argv[2]))
    print("models written:", ", ".join(models))


if __name__ == "__main__":
    main()
