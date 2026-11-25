"""8階「沈黙の湖」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月25日【補完】）。

  python3 tools/artgen/make_lake_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の8階：音のない凍った湖。氷の下に巨大な影と、沈んだ祠が見える。雑魚は無音の大魚（氷の下から襲う）・声喰い（近くに
いると効果音が消える）・凍て鶴・氷霧の亡霊。ボスは声なき湖主ムーネ（ボスのレアは「湖主の髭弓」：ひげの弓）。第2章の色は氷青・白銀。
8階は、音を吸う灰白と、湖の底の青。作るもの（ファイルの名前は敵のIDと同じ）:
  silent_fish      無音の大魚：白い鱗の、舟ほどもある湖の魚。背びれと胸びれは淡い青で透け、目は白く濁っている
  voice_eater      声喰い：大きな口だけの、ふわふわ浮く白い影。目はない。口の奥（のど）が青く光る。うしろは霧の尾
  frost_crane      凍て鶴：白い羽に黒い風切り。長い首と脚、黄緑のくちばし。頭に淡い水色に光る霜の冠
  fog_wraith       氷霧の亡霊：霧の衣のフードの亡霊。顔は暗く、2つの目が淡く光る。すそはちぎれた霧。長い腕に氷の爪
  mune             声なき湖主ムーネ：白い鱗の巨大な魚（なまずに似る）。口のまわりに、光る長いひげが左右に2本ずつ。背に氷の
                   とげの列。目は白く濁っている（盲目の湖の主）
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import loop, once, posed, sheet_font  # noqa: E402
from make_iceforest_models import ICEFOREST, IceForestPainter  # noqa: E402
from make_village_models import VILLAGE  # noqa: E402
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

LAKE = {
    # 湖の魚の鱗（灰白。つやのある白に、うすい青）
    "lake_scale": ramp("#545C68", "#6E7884", "#8C96A2", "#AAB4BE", "#C8D0D8", "#E2E8EE", "#F8FAFC"),
    "lake_belly": ramp("#7E848C", "#989EA6", "#B2B8BE", "#CAD0D4", "#DEE2E6", "#EEF0F2", "#FCFDFE"),
    # ひれ（淡い青で透ける）
    "lake_fin": ramp("#3E5C78", "#527494", "#6A8EAE", "#86A8C6", "#A4C2DA", "#C2DAEA", "#E0F0FA"),
    # 凍て鶴
    "crane_white": ramp("#7C8692", "#98A2AE", "#B4BEC8", "#CED6DE", "#E2E8EE", "#F0F4F8", "#FFFFFF"),
    "crane_black": ramp("#08090C", "#101218", "#181B22", "#22262E", "#2E333C", "#3C424C", "#4C535E"),
    "crane_beak": ramp("#3A4022", "#50582E", "#68723C", "#828E4C", "#9EAA62", "#BAC47E", "#D6DEA0"),
    "crane_leg": ramp("#1A1C22", "#24272E", "#30343C", "#3E434C", "#4E545E", "#606772", "#747C88"),
    # 声喰い（白い影と、暗い口の中）
    "voice_white": ramp("#6C7280", "#868C9A", "#A2A8B4", "#BCC2CC", "#D4D8E0", "#E8EBF0", "#F8F9FB"),
    "voice_mouth": ramp("#04060C", "#080C16", "#0E1420", "#141C2C", "#1C2638", "#263246", "#324056"),
    # 霧の衣（灰青。ところどころ透けている）
    "wraith_mist": ramp("#4A5462", "#5E6878", "#747E8E", "#8A94A4", "#A2ACBA", "#BCC4D0", "#D6DCE4"),
    # 光る：湖の目・ひげ（淡い水色）・のどの青・霜の冠
    "glow_lake": ramp("#2E6E86", "#3E8AA4", "#58A8C2", "#7EC4DA", "#A8DCEC", "#D2F0F8", "#FFFFFF"),
    "glow_whisker": ramp("#1E7C8E", "#2A98AC", "#40B4C8", "#66CEDE", "#94E2EE", "#C6F2F8", "#FFFFFF"),
    "glow_voice": ramp("#1E3C9A", "#2A52BA", "#3E6CD2", "#5E8CE4", "#88AEF0", "#B6D0F8", "#E6F0FF"),
    "glow_crown": ramp("#5A8CA8", "#70A4C0", "#8ABCD4", "#A6D2E4", "#C4E4F0", "#E0F2FA", "#FFFFFF"),
}
for _k, _v in LAKE.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class LakePainter(IceForestPainter):
    def paint_lake_scale(self, c):
        """鱗：ずらして重なる小さな弧（下のふちが暗い）。上の面は明るく、ときどきつやの白い点"""
        tone = self.std(c, "lake_scale", base=4.0, noise=0.3, blob=0.4, blob_size=2.5, up=1.0, down=0.9)
        row = c.j // 2
        if c.j % 2 == 1 and (c.i + (row % 2) * 2) % 4 != 0:
            tone -= 0.8
        if c.n[1] > 0.6:
            tone += 0.6
            if self.tex(c, 811) > 0.95:
                return pick(LAKE["lake_scale"], 6.4)
        if self.blob(c, 3.0, 812) < 0.12:
            tone -= 0.7   # 古い傷のような、くすんだ斑
        return pick(LAKE["lake_scale"], tone)

    def paint_lake_belly(self, c):
        tone = self.std(c, "lake_belly", base=4.2, noise=0.25, blob=0.25, up=0.6, down=0.7)
        return pick(LAKE["lake_belly"], tone)

    def paint_lake_fin(self, c):
        tone = 3.6 + (0.7 if c.j % 2 == 0 else -0.3) + (self.tex(c, 821) - 0.5) * 0.4
        d = c.edge_dist()
        if c.face.d not in ("up", "down") and (d.get("top", 1) == 0 or d.get("bottom", 1) == 0) and c.i % 2 == 1:
            return None   # ひれのふちのぎざぎざ
        rgb = pick(LAKE["lake_fin"], tone)
        return (*rgb, 200)

    def paint_crane_white(self, c):
        tone = self.std(c, "crane_white", base=4.6, noise=0.3, blob=0.3, up=0.8, down=1.0)
        if c.el.tags.get("feather") and c.j % 3 == 0:
            tone -= 0.7   # 羽の重なりの筋
        return pick(LAKE["crane_white"], tone)

    def paint_crane_black(self, c):
        tone = self.std(c, "crane_black", base=3.2, noise=0.3, blob=0.2, up=1.0, down=0.6)
        if c.i % 2 == 0:
            tone += 0.5
        return pick(LAKE["crane_black"], tone)

    def paint_crane_beak(self, c):
        tone = self.std(c, "crane_beak", base=3.6, noise=0.3, blob=0.2, up=1.0, down=1.0)
        return pick(LAKE["crane_beak"], tone)

    def paint_crane_leg(self, c):
        tone = self.std(c, "crane_leg", base=3.0, noise=0.3, blob=0.2, up=0.8, down=0.8)
        if c.j % 2 == 0:
            tone -= 0.4   # 脚のうろこ
        return pick(LAKE["crane_leg"], tone)

    def paint_voice_white(self, c):
        tone = self.std(c, "voice_white", base=4.4, noise=0.4, blob=0.5, blob_size=3.0, up=0.9, down=1.1)
        if c.el.tags.get("wisp") and c.edge_dist().get("bottom", 1) == 0 and c.i % 2 == 0:
            return None
        return pick(LAKE["voice_white"], tone)

    def paint_voice_mouth(self, c):
        tone = 2.4 + (self.tex(c, 831) - 0.5) * 0.6
        d = c.edge_dist()
        if min(d.values()) == 0:
            tone -= 0.8
        return pick(LAKE["voice_mouth"], tone)

    def paint_wraith_mist(self, c):
        strand = hsh(int(np.floor(c.p[0] * 1.3 + c.p[2])), int(np.floor(c.p[1] / 2.0)), 841)
        tone = 4.0 + (strand - 0.5) * 1.1 + self.bevel(c, up=0.7, down=0.9)
        if self.blob(c, 2.0, 842) < 0.18:
            tone += 0.9   # 霧の濃いところ（明るい筋）
        if c.el.tags.get("hem") and c.edge_dist()["bottom"] <= 1 and c.i % 3 != 1:
            return None
        rgb = pick(LAKE["wraith_mist"], tone)
        return (*rgb, 215)

    def paint_glow_lake(self, c):
        return self.paint_glow(c, "glow_lake", base=4.4)

    def paint_glow_whisker(self, c):
        tone = 4.0 + (0.8 if c.j % 3 == 0 else 0.0) + (self.tex(c, 851) - 0.5) * 0.5
        return pick(LAKE["glow_whisker"], tone)

    def paint_glow_voice(self, c):
        return self.paint_glow(c, "glow_voice", base=4.2)

    def paint_glow_crown(self, c):
        return self.paint_glow(c, "glow_crown", base=4.4)

    def ramp(self, name):
        return LAKE.get(name) or ICEFOREST.get(name) or VILLAGE.get(name) or super().ramp(name)


# ================================================================ 無音の大魚

def build_silent_fish():
    g = GeoModel("silent_fish", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 6, 0), parent="root")
    g.bone("head", (0, 6, -8), parent="body")
    g.bone("jaw", (0, 3, -10), parent="head")
    g.bone("tail", (0, 6, 9), parent="body")
    g.bone("tail_fin", (0, 6, 16), parent="tail")
    g.bone("fin_right", (-5, 4, -4), parent="body")
    g.bone("fin_left", (5, 4, -4), parent="body")
    g.cube("body", "body", (-5, 1.5, -9), (10, 9, 18), "lake_scale")
    g.cube("body", "belly", (-4.5, 1.2, -8), (9, 1, 16), "lake_belly")
    g.cube("body", "dorsal", (-0.5, 10.5, -6), (1, 4, 11), "lake_fin", rot=(-12, 0, 0), pivot=(0, 10.5, 0))
    g.cube("head", "head", (-5.5, 2, -16), (11, 8, 8), "lake_scale")
    g.cube("head", "brow", (-5, 9, -15), (10, 1, 6), "lake_scale")
    g.cube("jaw", "jaw", (-5, 0, -16.5), (10, 3, 8), "lake_belly")
    g.cube("head", "eye_r", (-5.6, 6, -13), (0, 2, 2), "glow_lake", glow=True)
    g.cube("head", "eye_l", (5.6, 6, -13), (0, 2, 2), "glow_lake", glow=True)
    g.cube("head", "barbel_r", (-7, 2.5, -16), (3, 1, 1), "lake_fin", rot=(0, -25, 0), pivot=(-5, 3, -15.5))
    g.cube("head", "barbel_l", (4, 2.5, -16), (3, 1, 1), "lake_fin", rot=(0, 25, 0), pivot=(5, 3, -15.5))
    g.cube("fin_right", "fin_r", (-10, 3, -6), (5, 1, 5), "lake_fin", rot=(0, 0, 20), pivot=(-5, 4, -4))
    g.cube("fin_left", "fin_l", (5, 3, -6), (5, 1, 5), "lake_fin", rot=(0, 0, -20), pivot=(5, 4, -4))
    g.cube("tail", "tail", (-3.5, 3, 9), (7, 6, 7), "lake_scale")
    g.cube("tail_fin", "tail_fin", (-0.5, -1, 15), (1, 14, 8), "lake_fin")
    return g


def silent_fish_anims():
    swim = {
        "body": {"rotation": {0: [0, -5, 0], 0.7: [0, 5, 0], 1.4: [0, -5, 0]}},
        "tail": {"rotation": {0: [0, 16, 0], 0.7: [0, -16, 0], 1.4: [0, 16, 0]}},
        "tail_fin": {"rotation": {0: [0, 20, 0], 0.7: [0, -20, 0], 1.4: [0, 20, 0]}},
        "fin_right": {"rotation": {0: [0, 0, 10], 0.7: [0, 0, -10], 1.4: [0, 0, 10]}},
        "fin_left": {"rotation": {0: [0, 0, -10], 0.7: [0, 0, 10], 1.4: [0, 0, -10]}},
    }
    t = {
        "idle": loop(1.4, swim),
        "walk": loop(1.4, swim),
        # かみつく（口を大きく開けて、前へ）
        "attack": once(0.8, {
            "jaw": {"rotation": {0: [0, 0, 0], 0.3: [40, 0, 0], 0.45: [0, 0, 0], 0.8: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.3: [0, 0, 1], 0.45: [0, 0, -2], 0.8: [0, 0, 0]}},
        }),
        # 氷を割って跳び上がり、氷の上でばたばたもがく
        "skill_lunge": once(4.0, {
            "body": {"rotation": {0: [-50, 0, 0], 0.4: [-10, 0, 0], 0.6: [0, 0, 80], 1.0: [0, 0, 70], 1.4: [0, 0, 90], 1.8: [0, 0, 70],
                                  2.2: [0, 0, 90], 2.6: [0, 0, 70], 3.0: [0, 0, 90], 3.6: [0, 0, 75], 4.0: [0, 0, 80]},
                     "position": {0: [0, 4, 0], 0.4: [0, 8, 0], 0.6: [0, 2, 0], 4.0: [0, 2, 0]}},
            "tail": {"rotation": {0.6: [0, 0, 0], 0.8: [30, 0, 0], 1.0: [-30, 0, 0], 1.2: [30, 0, 0], 1.6: [-30, 0, 0], 2.0: [30, 0, 0],
                                  2.4: [-30, 0, 0], 2.8: [30, 0, 0], 3.2: [-30, 0, 0], 4.0: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [35, 0, 0], 0.6: [10, 0, 0], 4.0: [10, 0, 0]}},
        }, hold=True),
        # 氷の下へ戻る（頭から飛びこむ）
        "skill_dive": once(0.6, {
            "body": {"rotation": {0: [0, 0, 80], 0.2: [60, 0, 0], 0.6: [0, 0, 0]}, "position": {0: [0, 2, 0], 0.6: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 180]}, "position": {0: [0, 0, 0], 1.0: [0, 6, 0]}},
        }, hold=True),
    }
    return {"animation.silent_fish." + k: v for k, v in t.items()}


# ================================================================ 声喰い

def build_voice_eater():
    g = GeoModel("voice_eater", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 9, 0), parent="root")
    g.bone("jaw", (0, 6, 0), parent="body")
    g.bone("tail", (0, 9, 6), parent="body")
    g.cube("body", "head", (-6.5, 6, -6.5), (13, 9, 13), "voice_white")
    g.cube("body", "crown", (-5, 15, -5), (10, 2, 10), "voice_white")
    g.cube("body", "mouth", (-5, 6.5, -6.6), (10, 5, 0), "voice_mouth")
    g.cube("body", "throat", (-2, 7.5, -6.7), (4, 3, 0), "glow_voice", glow=True)
    for k, x in enumerate((-4.5, -2.5, -0.5, 1.5, 3.5)):
        g.cube("body", "tooth_%d" % k, (x, 10.5, -6.8), (1, 1, 0), "lake_belly")
    g.cube("jaw", "jaw", (-6, 2, -6), (12, 4, 12), "voice_white")
    g.cube("jaw", "jaw_mouth", (-5, 4, -6.1), (10, 2, 0), "voice_mouth")
    g.cube("tail", "wisp_1", (-3, 7, 6), (6, 5, 5), "voice_white", tags={"wisp": True})
    g.cube("tail", "wisp_2", (-2, 7.5, 10), (4, 3, 5), "voice_white", tags={"wisp": True}, rot=(-10, 0, 0), pivot=(0, 9, 10))
    g.cube("tail", "wisp_3", (-1, 8, 14), (2, 2, 4), "voice_white", tags={"wisp": True}, rot=(-20, 0, 0), pivot=(0, 9, 14))
    return g


def voice_eater_anims():
    float_ = {
        "body": {"position": {0: [0, 0, 0], 1.0: [0, 1.2, 0], 2.0: [0, 0, 0]}},
        "jaw": {"rotation": {0: [0, 0, 0], 1.0: [10, 0, 0], 2.0: [0, 0, 0]}},
        "tail": {"rotation": {0: [0, -12, 0], 1.0: [0, 12, 0], 2.0: [0, -12, 0]}},
    }
    t = {
        "idle": loop(2.0, float_),
        "walk": loop(2.0, float_),
        # かみつく
        "attack": once(0.8, {
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [45, 0, 0], 0.55: [0, 0, 0], 0.8: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.4: [0, 0, 1], 0.55: [0, 0, -2], 0.8: [0, 0, 0]}},
        }),
        # 声の礫を吐く（口を開け、のどが光る）
        "skill_needle": once(1.0, {
            "jaw": {"rotation": {0: [0, 0, 0], 0.6: [35, 0, 0], 0.8: [10, 0, 0], 1.0: [0, 0, 0]}},
            "body": {"scale": {0: [1, 1, 1], 0.6: [1.1, 0.9, 1.1], 0.8: [0.95, 1.05, 0.95], 1.0: [1, 1, 1]}},
        }),
        # 大きく息を吸いこむ（ふくらんで、口を大きく開ける）
        "skill_inhale": once(3.6, {
            "body": {"scale": {0: [1, 1, 1], 1.6: [1.35, 1.35, 1.35], 3.4: [1.35, 1.35, 1.35], 3.6: [1, 1, 1]}},
            "jaw": {"rotation": {0: [0, 0, 0], 1.6: [60, 0, 0], 3.4: [60, 0, 0], 3.6: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"scale": {0: [1, 1, 1], 1.0: [0.1, 0.1, 0.1]}, "position": {0: [0, 0, 0], 1.0: [0, -4, 0]}},
        }, hold=True),
    }
    return {"animation.voice_eater." + k: v for k, v in t.items()}


# ================================================================ 凍て鶴

def build_frost_crane():
    g = GeoModel("frost_crane", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 16, 0), parent="root")
    g.bone("neck", (0, 19, -4), parent="body")
    g.bone("head", (0, 29, -6), parent="neck")
    g.bone("wing_right", (-3.5, 19, -3), parent="body")
    g.bone("wing_left", (3.5, 19, -3), parent="body")
    g.bone("leg_right", (-1.5, 14, 0), parent="body")
    g.bone("leg_left", (1.5, 14, 0), parent="body")
    g.bone("tail", (0, 17, 5), parent="body")
    g.cube("body", "body", (-3.5, 13, -5), (7, 7, 11), "crane_white", tags={"feather": True})
    g.cube("body", "chest", (-3, 14, -6), (6, 5, 2), "crane_white")
    g.cube("neck", "neck_low", (-1, 18, -7), (2, 6, 2), "crane_white", rot=(-20, 0, 0), pivot=(0, 19, -5))
    g.cube("neck", "neck_high", (-1, 23, -8), (2, 6, 2), "crane_white", rot=(10, 0, 0), pivot=(0, 23, -7))
    g.cube("neck", "neck_band", (-1, 21, -7.5), (2, 3, 2), "crane_black", inflate=0.12, rot=(-5, 0, 0), pivot=(0, 22, -6.5))
    g.cube("head", "head", (-1.5, 28, -9), (3, 3, 4), "crane_white")
    g.cube("head", "crown", (-1.5, 31, -8.5), (3, 1, 3), "glow_crown", glow=True)
    g.cube("head", "beak", (-0.5, 28.5, -15), (1, 1, 6), "crane_beak")
    g.cube("head", "eye_r", (-1.6, 29.5, -8), (0, 1, 1), "glow_lake", glow=True)
    g.cube("head", "eye_l", (1.6, 29.5, -8), (0, 1, 1), "glow_lake", glow=True)
    for side, sx in (("right", -1), ("left", 1)):
        x0 = -4.5 if sx < 0 else 3.5
        g.cube("wing_" + side, "wing_" + side, (x0, 13.5, -4), (1, 6, 11), "crane_white", tags={"feather": True})
        g.cube("wing_" + side, "wingtip_" + side, (x0, 12.5, 4), (1, 5, 5), "crane_black")
    for side, x in (("right", -1.5), ("left", 1.5)):
        g.cube("leg_" + side, "leg_" + side, (x - 0.5, 1, -0.5), (1, 13, 1), "crane_leg")
        g.cube("leg_" + side, "foot_" + side, (x - 1, 0, -2), (2, 1, 3), "crane_leg")
    g.cube("tail", "tail", (-2.5, 14, 5), (5, 4, 4), "crane_black", rot=(15, 0, 0), pivot=(0, 17, 5))
    return g


def frost_crane_anims():
    walk = {
        "leg_right": {"rotation": {0: [25, 0, 0], 0.5: [-25, 0, 0], 1.0: [25, 0, 0]}},
        "leg_left": {"rotation": {0: [-25, 0, 0], 0.5: [25, 0, 0], 1.0: [-25, 0, 0]}},
        "neck": {"rotation": {0: [0, 0, 0], 0.5: [8, 0, 0], 1.0: [0, 0, 0]}},
        "body": {"position": {0: [0, 0, 0], 0.25: [0, 0.5, 0], 0.5: [0, 0, 0], 0.75: [0, 0.5, 0], 1.0: [0, 0, 0]}},
    }
    t = {
        "idle": loop(4.0, {
            "neck": {"rotation": {0: [0, 0, 0], 1.5: [0, 0, 0], 2.0: [5, 25, 0], 3.0: [5, -20, 0], 4.0: [0, 0, 0]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 0], 2.4: [-60, 0, 0], 3.4: [-60, 0, 0], 3.8: [0, 0, 0], 4.0: [0, 0, 0]}},
        }),
        "walk": loop(1.0, walk),
        # くちばしで突く
        "attack": once(0.9, {
            "neck": {"rotation": {0: [0, 0, 0], 0.5: [-25, 0, 0], 0.62: [55, 0, 0], 0.9: [0, 0, 0]}},
        }),
        # 低く翼を広げて、滑るように突進する
        "skill_charge": once(1.4, {
            "wing_right": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, 80], 1.2: [0, 0, 80], 1.4: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, -80], 1.2: [0, 0, -80], 1.4: [0, 0, 0]}},
            "neck": {"rotation": {0: [0, 0, 0], 0.4: [40, 0, 0], 1.2: [40, 0, 0], 1.4: [0, 0, 0]}},
        }),
        # 翼を大きく振って、前に吹雪を叩きつける
        "skill_gust": once(1.4, {
            "wing_right": {"rotation": {0: [0, 0, 0], 0.8: [0, 30, 110], 1.0: [0, -40, 40], 1.4: [0, 0, 0]}},
            "wing_left": {"rotation": {0: [0, 0, 0], 0.8: [0, -30, -110], 1.0: [0, 40, -40], 1.4: [0, 0, 0]}},
            "neck": {"rotation": {0: [0, 0, 0], 0.8: [-20, 0, 0], 1.0: [15, 0, 0], 1.4: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 90]}, "position": {0: [0, 0, 0], 1.0: [0, -10, 0]}},
            "neck": {"rotation": {0: [0, 0, 0], 1.0: [40, 0, 0]}},
        }, hold=True),
    }
    return {"animation.frost_crane." + k: v for k, v in t.items()}


# ================================================================ 氷霧の亡霊

def build_fog_wraith():
    g = GeoModel("fog_wraith", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), parent="root")
    g.bone("head", (0, 22, 0), parent="body")
    g.bone("arm_right", (-4.5, 20, 0), parent="body")
    g.bone("arm_left", (4.5, 20, 0), parent="body")
    g.bone("hem", (0, 8, 0), parent="body")
    g.cube("head", "hood", (-4, 21, -4), (8, 8, 8), "wraith_mist")
    g.cube("head", "face", (-3, 22, -4.05), (6, 5, 0), "frost_dark")
    g.cube("head", "eye_r", (-2, 24, -4.1), (1, 1, 0), "glow_lake", glow=True)
    g.cube("head", "eye_l", (1, 24, -4.1), (1, 1, 0), "glow_lake", glow=True)
    g.cube("body", "robe", (-4, 9, -3), (8, 12, 6), "wraith_mist")
    g.cube("hem", "hem", (-5, 1, -4), (10, 8, 8), "wraith_mist", tags={"hem": True})
    for side, sx in (("right", -1), ("left", 1)):
        x0 = -6.5 if sx < 0 else 4.5
        g.cube("arm_" + side, "arm_" + side, (x0, 11, -1), (2, 10, 2), "wraith_mist")
        g.cube("arm_" + side, "claw_" + side, (x0, 8, -1), (2, 3, 2), "ice")
    return g


def fog_wraith_anims():
    drift = {
        "body": {"position": {0: [0, 0, 0], 1.5: [0, 1.0, 0], 3.0: [0, 0, 0]}},
        "hem": {"rotation": {0: [0, -8, 0], 1.5: [0, 8, 0], 3.0: [0, -8, 0]}},
        "arm_right": {"rotation": {0: [10, 0, 0], 1.5: [20, 0, 0], 3.0: [10, 0, 0]}},
        "arm_left": {"rotation": {0: [10, 0, 0], 1.5: [20, 0, 0], 3.0: [10, 0, 0]}},
    }
    t = {
        "idle": loop(3.0, drift),
        "walk": loop(3.0, drift),
        # 冷たい手で触れる（両腕を前へ）
        "attack": once(0.9, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-120, 0, 0], 0.65: [-70, 0, 0], 0.9: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.5: [-120, 0, 0], 0.65: [-70, 0, 0], 0.9: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.5: [15, 0, 0], 0.9: [0, 0, 0]}},
        }),
        # 霧に溶ける
        "skill_blink": once(1.0, {
            "body": {"scale": {0: [1, 1, 1], 0.8: [0.2, 1.4, 0.2], 1.0: [1, 1, 1]}},
        }),
        "death": once(1.4, {
            "body": {"scale": {0: [1, 1, 1], 1.2: [0.05, 0.05, 0.05]}, "position": {0: [0, 0, 0], 1.2: [0, 6, 0]}},
        }, hold=True),
    }
    return {"animation.fog_wraith." + k: v for k, v in t.items()}


# ================================================================ 声なき湖主ムーネ（ボス）

def build_mune():
    g = GeoModel("mune", 256, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 10, 0), parent="root")
    g.bone("head", (0, 11, -18), parent="body")
    g.bone("jaw", (0, 6, -22), parent="head")
    g.bone("whisker_right", (-10, 7, -31), parent="head")
    g.bone("whisker_left", (10, 7, -31), parent="head")
    g.bone("tail", (0, 11, 18), parent="body")
    g.bone("tail_fin", (0, 11, 31), parent="tail")
    g.bone("fin_right", (-10, 6, -10), parent="body")
    g.bone("fin_left", (10, 6, -10), parent="body")
    g.bone("spines", (0, 20, 0), parent="body")
    g.cube("body", "body", (-10, 2, -18), (20, 17, 36), "lake_scale")
    g.cube("body", "belly", (-9, 1.5, -16), (18, 2, 32), "lake_belly")
    g.cube("head", "head", (-11, 3, -32), (22, 15, 14), "lake_scale")
    g.cube("head", "brow", (-10, 17, -31), (20, 2, 11), "lake_scale")
    g.cube("jaw", "jaw", (-10.5, 0, -33), (21, 4, 14), "lake_belly")
    for k, x in enumerate((-8, -4, 0, 4)):
        g.cube("jaw", "lip_%d" % k, (x, 3.5, -33.2), (3, 1, 1), "lake_belly")
    g.cube("head", "eye_r", (-11.1, 11, -27), (0, 3, 3), "glow_lake", glow=True)
    g.cube("head", "eye_l", (11.1, 11, -27), (0, 3, 3), "glow_lake", glow=True)
    # 光る長いひげ（左右に2本ずつ。長い方は下へ垂れる）
    for side, sx, bone in (("r", -1, "whisker_right"), ("l", 1, "whisker_left")):
        base = (-10 if sx < 0 else 10, 7, -31)
        x_long = base[0] - 22 if sx < 0 else base[0]
        g.cube(bone, "whisker_%s1" % side, (x_long, base[1] - 0.5, base[2] - 0.5), (22, 1, 1), "glow_whisker", glow=True,
               rot=(0, sx * 20, sx * 18), pivot=base)
        x_short = base[0] - 14 if sx < 0 else base[0]
        g.cube(bone, "whisker_%s2" % side, (x_short, base[1] - 3, base[2] + 1), (14, 1, 1), "glow_whisker", glow=True,
               rot=(0, sx * 40, -sx * 10), pivot=(base[0], base[1] - 2.5, base[2] + 1.5))
    # 背の氷のとげ（列）
    for k, (z, h) in enumerate(((-14, 5), (-8, 7), (-2, 8), (4, 7), (10, 5))):
        g.cube("spines", "spine_%d" % k, (-1, 19, z), (2, h, 3), "ice", rot=(-20, 0, 0), pivot=(0, 19, z + 1.5))
    g.cube("fin_right", "fin_r", (-20, 4, -14), (10, 1, 9), "lake_fin", rot=(0, 0, 25), pivot=(-10, 6, -10))
    g.cube("fin_left", "fin_l", (10, 4, -14), (10, 1, 9), "lake_fin", rot=(0, 0, -25), pivot=(10, 6, -10))
    g.cube("tail", "tail", (-7, 5, 18), (14, 12, 14), "lake_scale")
    g.cube("tail_fin", "tail_fin", (-1, -5, 29), (2, 30, 16), "lake_fin")
    return g


def mune_anims():
    swim = {
        "body": {"rotation": {0: [0, -4, 0], 1.2: [0, 4, 0], 2.4: [0, -4, 0]}},
        "tail": {"rotation": {0: [0, 12, 0], 1.2: [0, -12, 0], 2.4: [0, 12, 0]}},
        "tail_fin": {"rotation": {0: [0, 15, 0], 1.2: [0, -15, 0], 2.4: [0, 15, 0]}},
        "fin_right": {"rotation": {0: [0, 0, 8], 1.2: [0, 0, -8], 2.4: [0, 0, 8]}},
        "fin_left": {"rotation": {0: [0, 0, -8], 1.2: [0, 0, 8], 2.4: [0, 0, -8]}},
        "whisker_right": {"rotation": {0: [0, 0, 0], 1.2: [0, -8, 6], 2.4: [0, 0, 0]}},
        "whisker_left": {"rotation": {0: [0, 0, 0], 1.2: [0, 8, -6], 2.4: [0, 0, 0]}},
    }
    t = {
        "idle": loop(2.4, swim),
        "walk": loop(2.4, swim),
        "attack": once(1.0, {
            "jaw": {"rotation": {0: [0, 0, 0], 0.5: [30, 0, 0], 0.7: [0, 0, 0], 1.0: [0, 0, 0]}},
        }),
        # 氷を割って、穴から上へ出る
        "skill_rise": once(1.2, {
            "body": {"rotation": {0: [-40, 0, 0], 0.6: [-10, 0, 0], 1.2: [0, 0, 0]}, "position": {0: [0, -10, 0], 0.6: [0, 2, 0], 1.2: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [40, 0, 0], 1.0: [0, 0, 0]}},
        }),
        # 髭払い（頭を振って、光るひげで前を払う）
        "skill_sweep": once(1.6, {
            "head": {"rotation": {0: [0, 0, 0], 1.1: [0, 35, 0], 1.35: [0, -35, 0], 1.6: [0, 0, 0]}},
            "whisker_right": {"rotation": {0: [0, 0, 0], 1.1: [0, 30, 0], 1.35: [0, -40, 0], 1.6: [0, 0, 0]}},
            "whisker_left": {"rotation": {0: [0, 0, 0], 1.1: [0, 40, 0], 1.35: [0, -30, 0], 1.6: [0, 0, 0]}},
        }),
        # 尾打ち（体をしならせ、尾で氷を叩く）
        "skill_tail": once(1.8, {
            "body": {"rotation": {0: [0, 0, 0], 1.2: [0, 25, 0], 1.45: [0, -20, 0], 1.8: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 1.2: [-30, 40, 0], 1.45: [20, -50, 0], 1.8: [0, 0, 0]}},
            "tail_fin": {"rotation": {0: [0, 0, 0], 1.2: [0, 30, 0], 1.45: [0, -40, 0], 1.8: [0, 0, 0]}},
        }),
        # 静寂の帳（口を大きく開ける。声は出ない。ひげがゆっくり広がる）
        "skill_veil": once(2.4, {
            "jaw": {"rotation": {0: [0, 0, 0], 1.0: [45, 0, 0], 2.2: [45, 0, 0], 2.4: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [-15, 0, 0], 2.2: [-15, 0, 0], 2.4: [0, 0, 0]}},
            "whisker_right": {"rotation": {0: [0, 0, 0], 1.0: [0, -25, 25], 2.2: [0, -25, 25], 2.4: [0, 0, 0]}},
            "whisker_left": {"rotation": {0: [0, 0, 0], 1.0: [0, 25, -25], 2.2: [0, 25, -25], 2.4: [0, 0, 0]}},
        }),
        # 断ち波（氷の下で身をひるがえし、腹で氷を打つ）
        "skill_wave": once(2.0, {
            "body": {"rotation": {0: [0, 0, 0], 1.4: [0, 0, 60], 1.7: [0, 0, -10], 2.0: [0, 0, 0]}},
        }),
        "skill_dive": once(0.8, {
            "body": {"rotation": {0: [0, 0, 0], 0.4: [40, 0, 0], 0.8: [0, 0, 0]}, "position": {0: [0, 0, 0], 0.4: [0, -6, 0], 0.8: [0, 0, 0]}},
        }),
        # 帳が破れて動けない（ひっくり返りかけ、ひげが垂れる）
        "dazed": once(8.0, {
            "body": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 35], 7.4: [0, 0, 35], 8.0: [0, 0, 0]}},
            "whisker_right": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 40], 7.4: [0, 0, 40], 8.0: [0, 0, 0]}},
            "whisker_left": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, -40], 7.4: [0, 0, -40], 8.0: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.6: [20, 0, 0], 7.4: [20, 0, 0], 8.0: [0, 0, 0]}},
        }),
        "death": once(3.0, {
            "body": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 170]}, "position": {0: [0, 0, 0], 2.6: [0, -14, 0]}},
            "whisker_right": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 50]}},
            "whisker_left": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, -50]}},
        }, hold=True),
    }
    return {"animation.mune." + k: v for k, v in t.items()}


MODELS = [(build_silent_fish, silent_fish_anims), (build_voice_eater, voice_eater_anims), (build_frost_crane, frost_crane_anims),
          (build_fog_wraith, fog_wraith_anims), (build_mune, mune_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(LakePainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "silent_fish": dict(scale=7.0, center=(0, 6, -2), size=(300, 220)),
        "voice_eater": dict(scale=9.0, center=(0, 9, 2), size=(260, 220)),
        "frost_crane": dict(scale=6.5, center=(0, 16, -4), size=(260, 300)),
        "fog_wraith": dict(scale=7.5, center=(0, 14, 0), size=(240, 280)),
        "mune": dict(scale=3.2, center=(0, 10, -4), size=(340, 240)),
    }
    poses = {
        "silent_fish": {"jaw": ((40, 0, 0),)},
        "voice_eater": {"jaw": ((55, 0, 0),)},
        "frost_crane": {"wing_right": ((0, 30, 110),), "wing_left": ((0, -30, -110),)},
        "fog_wraith": {"arm_right": ((-120, 0, 0),), "arm_left": ((-120, 0, 0),)},
        "mune": {"jaw": ((45, 0, 0),), "whisker_right": ((0, -25, 25),), "whisker_left": ((0, 25, -25),)},
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
    path = os.path.join(out_dir, "lake_models_check.png")
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
