"""9階「氷結した王城」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月28日【補完】）。

  python3 tools/artgen/make_castle_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の9階：凍った王城。大広間、凍った兵士、王家の書庫。王家の肖像画の顔はすべて空白（ロストの侵食：名前と顔の空白）。
雑魚は凍れる近衛兵・氷の騎兵・書架霊（ページが舞う亡霊）・霜の猟犬・忘れられた侍女。ボスは氷の近衛長ヴァルグリム。第2章の色は
氷青・白銀。9階は、近衛の青と、氷の白銀。どの人の形も、顔は白く抜けている。作るもの（ファイルの名前は敵のIDと同じ）:
  frozen_guard    凍れる近衛兵：氷の鎧と青い外套。右手に氷の斧槍、左手に塔の盾（青地に銀の雪の花）。兜の中の顔は白い空白。
                  ボーン frost（像の間の氷の殻）・eyes（目を覚ますと、空白の顔に青い目の光）・oath（近衛長とつながる胸の光）
  ice_cavalry     氷の騎兵：氷の馬（透きとおる淡い青、たてがみは白）に乗った近衛の騎士。長い氷の槍と、小さな盾。
                  ボーン banner（霜蹄の旗手だけ。背に凍った青い旗）
  shelf_wraith    書架霊：ほどけた頁でできた衣の亡霊。フードの中は白い空白。まわりを頁が舞う。
                  ボーン tome（綴じ手だけ。胸の前に開いた大きな本）
  frost_hound     霜の猟犬：痩せた白い狩りの犬。近衛の青い首輪。目は淡い青
  forgotten_maid  忘れられた侍女：紺の服に白い前掛けと帽子の、透きとおる侍女。顔は白い空白。手に銀の盆
  valgrim         氷の近衛長ヴァルグリム：大きな近衛の隊長。氷の鎧に銀の縁、青い外套。兜の青い羽飾り（凍ってもなお青い）。
                  右手に氷の長槍、左手に塔の大盾（近衛の誓いの紋：雪の花と剣）。顔は白い空白に、青い目の光
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import loop, once, posed, sheet_font  # noqa: E402
from make_lake_models import LAKE, LakePainter  # noqa: E402
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

CASTLE = {
    # 近衛の氷の鎧（白銀に、うすい青）
    "guard_plate": ramp("#4E5C70", "#68788E", "#8494AA", "#A2B2C6", "#C0CEDE", "#DCE6F0", "#F6FAFD"),
    # 近衛の青い外套
    "guard_cloth": ramp("#0C1430", "#121E46", "#1A2A5E", "#243878", "#304890", "#425CA8", "#5A76C0"),
    # 盾の面（深い青）
    "shield_face": ramp("#0E1A3E", "#16264E", "#1E3262", "#284078", "#34508E", "#4462A6", "#5A78BC"),
    # 銀の縁・飾り
    "silver": ramp("#4A5668", "#6A788C", "#8C9AAE", "#B0BCCC", "#CFD8E4", "#E6EDF4", "#F8FBFE"),
    # 白く抜けた顔
    "blank_face": ramp("#C8D2DE", "#D6DEE8", "#E2E8F0", "#ECF0F6", "#F4F7FA", "#FAFBFD", "#FFFFFF"),
    # 氷の馬（透きとおる淡い青）
    "ice_horse": ramp("#4A7094", "#6088AC", "#7AA2C4", "#96BCD8", "#B4D2E8", "#D2E6F4", "#F0F8FE"),
    # 頁の衣（古い紙。文字の行）
    "page_robe": ramp("#6E6250", "#8A7C66", "#A6987E", "#C0B396", "#D6CBB0", "#E8E0CA", "#F6F2E6"),
    # 霜の猟犬の毛（白灰）
    "hound_fur": ramp("#5A6270", "#727B8A", "#8C95A4", "#A8B0BC", "#C2C9D2", "#DAE0E6", "#F2F5F8"),
    # 侍女の紺の服と、白い前掛け
    "maid_dress": ramp("#0E1018", "#161A26", "#1E2434", "#283044", "#343E56", "#424E6A", "#55627E"),
    "apron": ramp("#A8ACB4", "#BCC0C8", "#CDD1D8", "#DCDFE4", "#E8EAEE", "#F2F3F5", "#FCFCFD"),
    "maid_hair": ramp("#1E140E", "#2C1E14", "#3C2A1C", "#4E3826", "#624832", "#7A5C42", "#967454"),
    # 隊長の青い羽飾り
    "plume": ramp("#0A1C5A", "#12287A", "#1C3896", "#2A4CB2", "#3E64CA", "#5A80DC", "#86A6EC"),
    # 光る：近衛の青い光（目・誓い）、文字の光
    "glow_oath": ramp("#1E4A9A", "#2A62BA", "#3E7ED2", "#5E9CE4", "#88BAF0", "#B6D6F8", "#E6F2FF"),
    "glow_script": ramp("#2E6E96", "#3E86AE", "#58A2C6", "#7CBCDA", "#A6D6EA", "#D2ECF6", "#FFFFFF"),
}
for _k, _v in CASTLE.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class CastleGeo(GeoModel):
    """大きさを整数に丸める（箱のUVのため。大きなボスは、人の形を1.55倍にしてから丸める）。"""

    def cube(self, bone, name, origin, size, mat, **kw):
        size = tuple(0 if abs(v) < 0.5 else max(1, int(v + 0.5)) for v in size)
        return super().cube(bone, name, origin, size, mat, **kw)


class CastlePainter(LakePainter):
    def paint_guard_plate(self, c):
        tone = self.std(c, "guard_plate", base=4.2, noise=0.25, blob=0.35, up=1.3, down=1.0)
        if c.el.tags.get("bands") and abs(float(c.n[1])) < 0.6 and c.j % 4 == 0:
            tone -= 0.9   # 鎧の板の重なり
        if (c.i + c.j * 3) % 11 == 0:
            tone += 0.8   # 氷のきらめき
        return pick(CASTLE["guard_plate"], tone)

    def paint_guard_cloth(self, c):
        tone = self.std(c, "guard_cloth", base=3.6, noise=0.25, blob=0.3, up=1.1, down=1.0)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(np.floor(along + 0.5)) % 3 == 0:
                tone -= 0.7
        if c.el.tags.get("hem") and c.edge_dist().get("bottom", 1) == 0:
            if c.i % 3 == 1:
                return None   # 凍ってちぎれたすそ
            return pick(CASTLE["silver"], 4.8)
        return pick(CASTLE["guard_cloth"], tone)

    def paint_shield_face(self, c):
        tone = self.std(c, "shield_face", base=3.6, noise=0.2, blob=0.2, up=1.0, down=1.0)
        if c.el.tags.get("emblem") and abs(float(c.n[2])) > 0.6:
            u = (c.i + 0.5) / c.W - 0.5
            v = (c.j + 0.5) / c.H - 0.45
            r = (u * u + v * v) ** 0.5
            # 縁の銀
            if min(c.i, c.j, c.W - 1 - c.i, c.H - 1 - c.j) == 0:
                return pick(CASTLE["silver"], 4.6)
            # まん中の雪の花（六つの腕）と、たての剣（大盾だけ）
            if r < 0.28 and (abs(u) < 0.04 or abs(abs(u) * 1.7 - abs(v)) < 0.05):
                return pick(CASTLE["silver"], 5.4)
            if c.el.tags.get("sword") and abs(u) < 0.03 and -0.4 < v < 0.45:
                return pick(CASTLE["silver"], 5.0)
        return pick(CASTLE["shield_face"], tone)

    def paint_frost_shell(self, c):
        """像の間の氷の殻：透きとおる氷。ところどころ白い霜の筋"""
        tone = 4.4 + (self.tex(c, 48) - 0.5) * 0.6 + self.bevel(c, up=1.0, down=0.6)
        frost = (c.i * 2 + c.j + int(hsh(c.j, 0, 49) * 3)) % 9 == 0
        rgb = pick(CASTLE["ice_horse"], tone + (1.2 if frost else 0.0))
        return (*rgb[:3], 190 if frost else 110)

    def paint_silver(self, c):
        tone = 4.2 + self.bevel(c, up=1.4, down=1.0) + (self.tex(c, 41) - 0.5) * 0.4
        return pick(CASTLE["silver"], tone)

    def paint_blank_face(self, c):
        tone = 5.0 + self.bevel(c, up=0.4, down=0.6) + (self.tex(c, 42) - 0.5) * 0.3
        return pick(CASTLE["blank_face"], tone)

    def paint_ice_horse(self, c):
        tone = 4.0 + (self.tex(c, 43) - 0.5) * 0.6 + self.bevel(c, up=1.1, down=0.9)
        if (c.i * 2 + c.j) % 9 == 0:
            tone += 0.9
        rgb = pick(CASTLE["ice_horse"], tone)
        return (*rgb[:3], 225)

    def paint_page_robe(self, c):
        """頁の衣：古い紙を重ねた筋。文字の行（ところどころ名前のところが白く抜ける）。すそはちぎれる"""
        tone = self.std(c, "page_robe", base=4.4, noise=0.25, blob=0.35, up=0.9, down=1.0)
        if abs(float(c.n[1])) < 0.6:
            if c.j % 3 == 1 and 0 < c.i < c.W - 1 and c.i % 4 != 0 and hsh(c.i, c.j, 44 + self.eid(c)) < 0.55:
                return pick(CASTLE["page_robe"], 2.2) if hsh(c.i // 3, c.j, 45) > 0.15 else pick(CASTLE["blank_face"], 6.0)
            if c.i % 6 == 0:
                tone -= 0.6   # 頁の重なり
        if c.el.tags.get("hem") and c.edge_dist().get("bottom", 1) <= 1 and (c.i % 3 != 1):
            return None
        rgb = pick(CASTLE["page_robe"], tone)
        return (*rgb[:3], 235)

    def paint_hound_fur(self, c):
        strand = hsh(int(np.floor(c.p[0] * 2 + c.p[2])), int(np.floor(c.p[1] / 1.5)), 46)
        tone = 4.2 + (strand - 0.5) * 1.0 + self.bevel(c, up=0.9, down=1.0)
        if c.n[1] < -0.6:
            tone -= 1.0
        return pick(CASTLE["hound_fur"], tone)

    def paint_maid_dress(self, c):
        tone = self.std(c, "maid_dress", base=3.6, noise=0.25, blob=0.3, up=1.1, down=1.0)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(np.floor(along + 0.5)) % 3 == 0:
                tone -= 0.6
        rgb = pick(CASTLE["maid_dress"], tone)
        alpha = 200 if not (c.el.tags.get("hem") and c.edge_dist().get("bottom", 1) <= 1) else 120
        return (*rgb[:3], alpha)

    def paint_apron(self, c):
        tone = self.std(c, "apron", base=4.6, noise=0.2, blob=0.2, up=0.8, down=0.8)
        if c.el.tags.get("lace") and c.edge_dist().get("bottom", 1) == 0 and c.i % 2 == 1:
            return None
        rgb = pick(CASTLE["apron"], tone)
        return (*rgb[:3], 215)

    def paint_maid_hair(self, c):
        tone = self.std(c, "maid_hair", base=3.8, noise=0.3, blob=0.3, up=1.0, down=1.0)
        rgb = pick(CASTLE["maid_hair"], tone)
        return (*rgb[:3], 215)

    def paint_plume(self, c):
        strand = hsh(int(np.floor(c.p[0] * 2)), int(np.floor(c.p[2] * 2)), 47)
        tone = 4.0 + (strand - 0.5) * 1.2 + self.bevel(c, up=0.8, down=0.8)
        return pick(CASTLE["plume"], tone)

    def paint_glow_oath(self, c):
        return self.paint_glow(c, "glow_oath", base=4.4)

    def paint_glow_script(self, c):
        return self.paint_glow(c, "glow_script", base=4.4)

    def ramp(self, name):
        return CASTLE.get(name) or LAKE.get(name) or super().ramp(name)


# ================================================================ 人の形の共通（脚・胴・腕・頭）

def humanoid(g, top, scale=1.0, plate="guard_plate", cloth="guard_cloth"):
    """近衛の形：脚 → 胴 → 腕 → 頭（兜と白い顔）。top は肩の高さ（scale 1 のとき 24）"""
    s = scale
    g.bone("root", (0, 0, 0))
    g.bone("leg_right", (-2.0 * s, 12 * s, 0), parent="root")
    g.bone("leg_left", (2.0 * s, 12 * s, 0), parent="root")
    g.bone("body", (0, 12 * s, 0), parent="root")
    g.bone("cape", (0, 24 * s, 2.0 * s), parent="body")
    g.bone("head", (0, 24 * s, 0), parent="body")
    g.bone("arm_right", (-5.5 * s, 22 * s, 0), parent="body")
    g.bone("arm_left", (5.5 * s, 22 * s, 0), parent="body")
    for name, x in (("leg_right", -2.0), ("leg_left", 2.0)):
        g.cube(name, name, ((x - 2) * s, 2 * s, -2 * s), (4 * s, 10 * s, 4 * s), plate, tags={"bands": True})
        g.cube(name, name + "_boot", ((x - 2.3) * s, 0, -2.6 * s), (4.6 * s, 3 * s, 5 * s), plate)
    g.cube("body", "torso", (-4 * s, 12 * s, -2.5 * s), (8 * s, 12 * s, 5 * s), plate, tags={"bands": True})
    g.cube("body", "skirt", (-4.5 * s, 9 * s, -3 * s), (9 * s, 4 * s, 6 * s), cloth, tags={"folds": True})
    g.cube("body", "belt", (-4.3 * s, 12 * s, -2.8 * s), (8.6 * s, 1 * s, 5.6 * s), "silver")
    g.cube("body", "tabard", (-2.5 * s, 13 * s, -2.8 * s), (5 * s, 10 * s, 0.4 * s), cloth)
    g.cube("cape", "cape", (-4.5 * s, 6 * s, 2.5 * s), (9 * s, 18 * s, 1 * s), cloth, tags={"folds": True, "hem": True})
    g.cube("body", "pauldron_r", (-7 * s, 21 * s, -3 * s), (4 * s, 3 * s, 6 * s), plate)
    g.cube("body", "pauldron_l", (3 * s, 21 * s, -3 * s), (4 * s, 3 * s, 6 * s), plate)
    g.cube("arm_right", "arm_right", (-7.5 * s, 12 * s, -2 * s), (4 * s, 10 * s, 4 * s), plate, tags={"bands": True})
    g.cube("arm_left", "arm_left", (3.5 * s, 12 * s, -2 * s), (4 * s, 10 * s, 4 * s), plate, tags={"bands": True})
    g.cube("head", "helm", (-4 * s, 24 * s, -4 * s), (8 * s, 8 * s, 8 * s), plate)
    g.cube("head", "face", (-3 * s, 25 * s, -4.1 * s), (6 * s, 5 * s, 0), "blank_face")
    g.cube("head", "visor_top", (-4.2 * s, 30 * s, -4.4 * s), (8.4 * s, 1 * s, 1 * s), "silver")
    g.cube("head", "crest", (-0.5 * s, 32 * s, -3 * s), (1 * s, 2 * s, 7 * s), "silver")


# ================================================================ 凍れる近衛兵

def build_frozen_guard():
    g = CastleGeo("frozen_guard", 128, 128)
    humanoid(g, 24)
    g.bone("halberd", (-5.5, 13, -1), parent="arm_right")
    g.bone("shield", (7.5, 16, -2), parent="arm_left")
    g.bone("frost", (0, 0, 0), parent="body")
    g.bone("eyes", (0, 27, -4), parent="head")
    g.bone("oath", (0, 19, -3), parent="body")
    # 氷の斧槍（柄は霜銀、先は氷の刃と穂先）
    g.cube("halberd", "shaft", (-6.5, 2, -2), (1, 26, 1), "silver")
    g.cube("halberd", "blade", (-6.8, 23, -4.5), (1.6, 5, 3), "ice")
    g.cube("halberd", "spike", (-6.5, 28, -2), (1, 4, 1), "ice")
    g.cube("halberd", "hook", (-6.6, 24, -1), (1.2, 2, 2), "ice")
    # 塔の盾（青地に銀の雪の花）
    g.cube("shield", "shield", (7.6, 8, -6), (1.5, 14, 9), "shield_face", tags={"emblem": True})
    g.cube("shield", "shield_rim", (7.4, 7.5, -6.5), (1.2, 15, 10), "silver", inflate=0.01)
    # 像の間の氷の殻（透きとおる。目を覚ますと割れて消える）
    g.cube("frost", "frost_body", (-4.5, 11.5, -3), (9, 13, 6), "frost_shell", inflate=0.4)
    g.cube("frost", "frost_head", (-4.5, 23.5, -4.5), (9, 9, 9), "frost_shell", inflate=0.4)
    g.cube("frost", "frost_legs", (-4.5, 0, -3), (9, 12, 6), "frost_shell", inflate=0.3)
    # 白い顔の中の、青い目の光（目を覚ましたとき）
    g.cube("eyes", "eye_r", (-2.5, 27, -4.2), (1.5, 1, 0), "glow_oath", glow=True)
    g.cube("eyes", "eye_l", (1, 27, -4.2), (1.5, 1, 0), "glow_oath", glow=True)
    # 近衛長とつながる胸の光
    g.cube("oath", "oath_mark", (-1.5, 17.5, -3.1), (3, 3, 0), "glow_oath", glow=True)
    return g


def guard_walk():
    return {
        "leg_right": {"rotation": {0: [20, 0, 0], 0.6: [-20, 0, 0], 1.2: [20, 0, 0]}},
        "leg_left": {"rotation": {0: [-20, 0, 0], 0.6: [20, 0, 0], 1.2: [-20, 0, 0]}},
        "arm_right": {"rotation": {0: [-10, 0, 0], 0.6: [-4, 0, 0], 1.2: [-10, 0, 0]}},
        "arm_left": {"rotation": {0: [-40, 20, 0], 1.2: [-40, 20, 0]}},
        "cape": {"rotation": {0: [8, 0, 0], 0.6: [4, 0, 0], 1.2: [8, 0, 0]}},
    }


def frozen_guard_anims():
    t = {
        # 像：動かない（盾を構えたまま）
        "idle": loop(4.0, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 4.0: [-40, 20, 0]}},
            "arm_right": {"rotation": {0: [-14, 0, 0], 4.0: [-14, 0, 0]}},
        }),
        "walk": loop(1.2, guard_walk()),
        # 盾打ち（予兆 0.8 秒：盾を引いて、前へ突き出す）
        "attack": once(1.2, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.7: [-30, 50, 0], 0.85: [-80, -10, 0], 1.2: [-40, 20, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.7: [0, -20, 0], 0.85: [0, 20, 0], 1.2: [0, 0, 0]}},
        }),
        # 斧槍の突き（予兆 1.0 秒：引いて、まっすぐ突く）
        "skill_thrust": once(1.4, {
            "arm_right": {"rotation": {0: [-14, 0, 0], 0.9: [-60, 0, 0], 1.05: [-95, 0, 0], 1.4: [-14, 0, 0]},
                          "position": {0: [0, 0, 0], 0.9: [0, 0, 2], 1.05: [0, 0, -3], 1.4: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.9: [0, 15, 0], 1.05: [8, -10, 0], 1.4: [0, 0, 0]}},
        }),
        # 目を覚ます（氷をふるい落とし、顔を上げる）
        "skill_wake": once(1.0, {
            "body": {"rotation": {0: [6, 0, 0], 0.15: [6, 0, 3], 0.3: [6, 0, -3], 0.45: [4, 0, 2], 1.0: [0, 0, 0]}},
            "head": {"rotation": {0: [20, 0, 0], 0.7: [-8, 0, 0], 1.0: [0, 0, 0]}},
        }),
        "skill_guard": once(1.0, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.3: [-80, 0, 0], 0.8: [-80, 0, 0], 1.0: [-40, 20, 0]}},
        }),
        "death": once(1.6, {
            "body": {"rotation": {0: [0, 0, 0], 0.4: [-8, 0, 0], 1.2: [80, 0, 6]}, "position": {0: [0, 0, 0], 1.2: [0, -9, -3]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.2: [-40, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [20, 0, 10]}},
        }, hold=True),
    }
    return {"animation.frozen_guard." + k: v for k, v in t.items()}


# ================================================================ 氷の騎兵

def build_ice_cavalry():
    g = CastleGeo("ice_cavalry", 256, 128)
    g.bone("root", (0, 0, 0))
    g.bone("horse", (0, 14, 0), parent="root")
    g.bone("neck", (0, 20, -8), parent="horse")
    g.bone("horse_head", (0, 28, -12), parent="neck")
    for name, x, z in (("leg_fr", -3, -7), ("leg_fl", 3, -7), ("leg_br", -3, 7), ("leg_bl", 3, 7)):
        g.bone(name, (x, 14, z), parent="root")
    g.bone("tail", (0, 20, 10), parent="horse")
    g.bone("rider", (0, 23, 0), parent="horse")
    g.bone("rider_head", (0, 35, 0), parent="rider")
    g.bone("arm_right", (-4.5, 34, 0), parent="rider")
    g.bone("arm_left", (4.5, 34, 0), parent="rider")
    g.bone("lance", (-5.5, 26, -1), parent="arm_right")
    g.bone("banner", (0, 34, 3), parent="rider")
    # 馬（氷）：胴・首・頭・たてがみ・脚・尾
    g.cube("horse", "barrel", (-4.5, 14, -10), (9, 9, 20), "ice_horse")
    g.cube("horse", "barding", (-5, 15, -9), (10, 7, 8), "guard_cloth", tags={"folds": True, "hem": True})
    g.cube("neck", "neck", (-2, 19, -15), (4, 11, 5), "ice_horse", rot=(35, 0, 0), pivot=(0, 20, -10))
    g.cube("neck", "mane", (-0.6, 22, -13), (1.2, 10, 3), "ice", rot=(35, 0, 0), pivot=(0, 20, -10))
    g.cube("horse_head", "horse_head", (-2.5, 26, -20), (5, 5, 9), "ice_horse")
    g.cube("horse_head", "chamfron", (-2.6, 27.5, -20.2), (5.2, 3.5, 5), "silver")
    g.cube("horse_head", "ear_r", (-2, 31, -13), (1, 2, 1), "ice_horse")
    g.cube("horse_head", "ear_l", (1, 31, -13), (1, 2, 1), "ice_horse")
    g.cube("horse_head", "eye_r", (-2.6, 29, -16), (0, 1, 1), "glow_oath", glow=True)
    g.cube("horse_head", "eye_l", (2.6, 29, -16), (0, 1, 1), "glow_oath", glow=True)
    for name, x, z in (("leg_fr", -3, -7), ("leg_fl", 3, -7), ("leg_br", -3, 7), ("leg_bl", 3, 7)):
        g.cube(name, name, (x - 1.5, 2, z - 1.5), (3, 12, 3), "ice_horse")
        g.cube(name, name + "_hoof", (x - 1.8, 0, z - 1.8), (3.6, 2, 3.6), "silver")
    g.cube("tail", "tail", (-1, 10, 10), (2, 11, 2), "ice", rot=(20, 0, 0), pivot=(0, 20, 10))
    # 乗り手（近衛の騎士。腰から上）
    g.cube("rider", "saddle", (-3.5, 23, -3), (7, 2, 7), "leather_dark")
    g.cube("rider", "rider_legs", (-5, 18, -2.5), (10, 6, 5), "guard_plate", tags={"bands": True})
    g.cube("rider", "rider_torso", (-3.5, 25, -2.2), (7, 10, 4.4), "guard_plate", tags={"bands": True})
    g.cube("rider", "rider_tabard", (-2.2, 25.5, -2.5), (4.4, 9, 0.4), "guard_cloth")
    g.cube("rider", "rider_cape", (-4, 21, 2.2), (8, 14, 1), "guard_cloth", tags={"folds": True, "hem": True})
    g.cube("rider_head", "rider_helm", (-3.5, 35, -3.5), (7, 7, 7), "guard_plate")
    g.cube("rider_head", "rider_face", (-2.5, 36, -3.6), (5, 4, 0), "blank_face")
    g.cube("rider_head", "rider_crest", (-0.5, 42, -2.5), (1, 2, 6), "silver")
    g.cube("arm_right", "rider_arm_r", (-6.5, 25, -1.7), (3.4, 10, 3.4), "guard_plate")
    g.cube("arm_left", "rider_arm_l", (3.1, 25, -1.7), (3.4, 10, 3.4), "guard_plate")
    g.cube("arm_left", "rider_shield", (6.6, 22, -4), (1.2, 8, 6), "shield_face", tags={"emblem": True})
    # 長い氷の槍（前へ倒して構える）
    g.cube("lance", "lance_grip", (-6, 24, -3), (1.4, 1.4, 6), "silver")
    g.cube("lance", "lance", (-5.9, 24.1, -26), (1.2, 1.2, 23), "ice")
    g.cube("lance", "lance_tip", (-6.1, 23.9, -30), (1.6, 1.6, 4), "ice")
    # 旗手の、背の凍った青い旗
    g.cube("banner", "banner_pole", (-0.5, 30, 3), (1, 22, 1), "silver")
    g.cube("banner", "banner_cloth", (0.5, 42, 3.2), (0.6, 9, 8), "guard_cloth", tags={"hem": True})
    g.cube("banner", "banner_flower", (1.15, 45, 5.5), (0, 3, 3), "glow_oath", glow=True)
    return g


def ice_cavalry_anims():
    gallop = {
        "leg_fr": {"rotation": {0: [30, 0, 0], 0.35: [-30, 0, 0], 0.7: [30, 0, 0]}},
        "leg_fl": {"rotation": {0: [-30, 0, 0], 0.35: [30, 0, 0], 0.7: [-30, 0, 0]}},
        "leg_br": {"rotation": {0: [-30, 0, 0], 0.35: [30, 0, 0], 0.7: [-30, 0, 0]}},
        "leg_bl": {"rotation": {0: [30, 0, 0], 0.35: [-30, 0, 0], 0.7: [30, 0, 0]}},
        "horse": {"position": {0: [0, 0, 0], 0.175: [0, 0.8, 0], 0.35: [0, 0, 0], 0.525: [0, 0.8, 0], 0.7: [0, 0, 0]}},
        "neck": {"rotation": {0: [0, 0, 0], 0.35: [6, 0, 0], 0.7: [0, 0, 0]}},
        "tail": {"rotation": {0: [10, 0, 0], 0.35: [25, 0, 0], 0.7: [10, 0, 0]}},
    }
    lance_down = {"arm_right": {"rotation": {0: [-20, 0, 0]}}}
    t = {
        "idle": loop(3.0, {
            "neck": {"rotation": {0: [0, 0, 0], 1.5: [6, 0, 0], 3.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [10, -10, 0], 1.5: [10, 10, 0], 3.0: [10, -10, 0]}},
            "arm_right": {"rotation": {0: [-20, 0, 0], 3.0: [-20, 0, 0]}},
        }),
        "walk": loop(0.7, {**gallop, **lance_down}),
        # 蹄の踏み鳴らし（前足を高く上げて、地面を打つ）
        "skill_stomp": once(1.3, {
            "horse": {"rotation": {0: [0, 0, 0], 0.7: [-30, 0, 0], 0.9: [6, 0, 0], 1.3: [0, 0, 0]},
                      "position": {0: [0, 0, 0], 0.7: [0, 4, 3], 0.9: [0, 0, 0], 1.3: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 0.7: [-70, 0, 0], 0.9: [10, 0, 0], 1.3: [0, 0, 0]}},
            "leg_fl": {"rotation": {0: [0, 0, 0], 0.7: [-60, 0, 0], 0.9: [10, 0, 0], 1.3: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [-20, 0, 0], 1.3: [-20, 0, 0]}},
        }),
        # 槍の突撃（構えて、首を下げる。走るのは移動のアニメ）
        "skill_charge": once(1.6, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.6: [0, 0, 0], 1.6: [0, 0, 0]}},
            "neck": {"rotation": {0: [0, 0, 0], 0.6: [18, 0, 0], 1.6: [18, 0, 0]}},
            "rider": {"rotation": {0: [0, 0, 0], 0.6: [14, 0, 0], 1.6: [14, 0, 0]}},
        }),
        # 壁にぶつかって転ぶ（よろめいて、膝をつく）
        "skill_stagger": once(3.0, {
            "horse": {"rotation": {0: [0, 0, 0], 0.3: [10, 0, 18], 2.6: [10, 0, 18], 3.0: [0, 0, 0]},
                      "position": {0: [0, 0, 0], 0.3: [0, -4, 0], 2.6: [0, -4, 0], 3.0: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 0.3: [-80, 0, 0], 2.6: [-80, 0, 0], 3.0: [0, 0, 0]}},
            "leg_fl": {"rotation": {0: [0, 0, 0], 0.3: [-80, 0, 0], 2.6: [-80, 0, 0], 3.0: [0, 0, 0]}},
            "rider": {"rotation": {0: [0, 0, 0], 0.3: [-20, 0, -10], 2.6: [-20, 0, -10], 3.0: [0, 0, 0]}},
        }),
        # 旗を振って号令（旗手）
        "skill_rally": once(1.6, {
            "rider": {"rotation": {0: [0, 0, 0], 0.4: [-10, 0, 0], 1.2: [-10, 0, 0], 1.6: [0, 0, 0]}},
            "banner": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, 25], 0.8: [0, 0, -25], 1.2: [0, 0, 25], 1.6: [0, 0, 0]}},
            "horse": {"rotation": {0: [0, 0, 0], 0.4: [-25, 0, 0], 1.0: [-25, 0, 0], 1.6: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 0.4: [-60, 0, 0], 1.0: [-60, 0, 0], 1.6: [0, 0, 0]}},
            "leg_fl": {"rotation": {0: [0, 0, 0], 0.4: [-50, 0, 0], 1.0: [-50, 0, 0], 1.6: [0, 0, 0]}},
        }),
        "attack": once(1.0, {
            "arm_right": {"rotation": {0: [-20, 0, 0], 0.5: [-60, 0, 0], 0.65: [10, 0, 0], 1.0: [-20, 0, 0]}},
        }),
        "death": once(2.0, {
            "root": {"rotation": {0: [0, 0, 0], 1.4: [0, 0, 80]}, "position": {0: [0, 0, 0], 1.4: [0, -4, 0]}},
        }, hold=True),
    }
    return {"animation.ice_cavalry." + k: v for k, v in t.items()}


# ================================================================ 書架霊

def build_shelf_wraith():
    g = CastleGeo("shelf_wraith", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), parent="root")
    g.bone("head", (0, 22, 0), parent="body")
    g.bone("arm_right", (-4.5, 20, 0), parent="body")
    g.bone("arm_left", (4.5, 20, 0), parent="body")
    g.bone("hem", (0, 8, 0), parent="body")
    g.bone("pages", (0, 16, 0), parent="root")
    g.bone("tome", (0, 15, -5), parent="body")
    g.cube("head", "hood", (-4, 21, -4), (8, 8, 8), "page_robe")
    g.cube("head", "face", (-3, 22, -4.05), (6, 5, 0), "blank_face")
    g.cube("body", "robe", (-4, 9, -3), (8, 12, 6), "page_robe")
    g.cube("hem", "hem", (-5, 1, -4), (10, 8, 8), "page_robe", tags={"hem": True})
    for side, x0 in (("right", -6.5), ("left", 4.5)):
        g.cube("arm_" + side, "arm_" + side, (x0, 11, -1), (2, 10, 2), "page_robe")
    # まわりを舞う頁（板）
    for k, (x, y, z, ry) in enumerate(((-9, 14, -2, 20), (8, 18, 1, -30), (-6, 22, 5, 60), (7, 11, -5, -10), (0, 26, 7, 45))):
        g.cube("pages", "page_%d" % k, (x, y, z), (3, 4, 0), "page_robe", rot=(10, ry, 0), pivot=(x + 1.5, y + 2, z))
    # 綴じ手の、開いた大きな本と、光る文字
    g.cube("tome", "tome_cover", (-5, 12, -8), (10, 7, 1), "guard_cloth")
    g.cube("tome", "tome_pages", (-4.5, 12.5, -8.6), (9, 6, 0.6), "page_robe")
    g.cube("tome", "tome_glow", (-3.5, 14, -8.8), (7, 3, 0), "glow_script", glow=True)
    return g


def shelf_wraith_anims():
    drift = {
        "body": {"position": {0: [0, 0, 0], 1.5: [0, 1.0, 0], 3.0: [0, 0, 0]}},
        "hem": {"rotation": {0: [0, -8, 0], 1.5: [0, 8, 0], 3.0: [0, -8, 0]}},
        "pages": {"rotation": {0: [0, 0, 0], 3.0: [0, 360, 0]}},
        "arm_right": {"rotation": {0: [10, 0, 0], 1.5: [20, 0, 0], 3.0: [10, 0, 0]}},
        "arm_left": {"rotation": {0: [10, 0, 0], 1.5: [20, 0, 0], 3.0: [10, 0, 0]}},
    }
    t = {
        "idle": loop(3.0, drift),
        "walk": loop(3.0, drift),
        "attack": once(0.9, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-110, 0, 0], 0.9: [0, 0, 0]}},
        }),
        # 頁を撃つ（片手を前へ。頁が速く回る）
        "skill_needle": once(1.0, {
            "arm_right": {"rotation": {0: [10, 0, 0], 0.6: [-100, 0, 0], 0.8: [-80, 0, 0], 1.0: [10, 0, 0]}},
            "pages": {"rotation": {0: [0, 0, 0], 1.0: [0, 540, 0]}},
        }),
        # 頁の嵐（両手を上げ、頁が大きく広がって回る）
        "skill_storm": once(1.6, {
            "arm_right": {"rotation": {0: [10, 0, 0], 0.6: [-160, 0, 10], 1.3: [-160, 0, 10], 1.6: [10, 0, 0]}},
            "arm_left": {"rotation": {0: [10, 0, 0], 0.6: [-160, 0, -10], 1.3: [-160, 0, -10], 1.6: [10, 0, 0]}},
            "pages": {"rotation": {0: [0, 0, 0], 1.6: [0, 900, 0]},
                      "scale": {0: [1, 1, 1], 0.6: [1.6, 1.2, 1.6], 1.3: [1.6, 1.2, 1.6], 1.6: [1, 1, 1]}},
        }),
        "death": once(1.4, {
            "body": {"scale": {0: [1, 1, 1], 1.2: [0.05, 0.05, 0.05]}, "position": {0: [0, 0, 0], 1.2: [0, 6, 0]}},
            "pages": {"scale": {0: [1, 1, 1], 1.2: [2.4, 0.5, 2.4]}},
        }, hold=True),
    }
    return {"animation.shelf_wraith." + k: v for k, v in t.items()}


# ================================================================ 霜の猟犬

def build_frost_hound():
    g = CastleGeo("frost_hound", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 10, 0), parent="root")
    g.bone("neck", (0, 12, -6), parent="body")
    g.bone("head", (0, 14, -8), parent="neck")
    g.bone("jaw", (0, 12.5, -10), parent="head")
    for name, x, z in (("leg_fr", -1.8, -5), ("leg_fl", 1.8, -5), ("leg_br", -1.8, 5), ("leg_bl", 1.8, 5)):
        g.bone(name, (x, 9, z), parent="root")
    g.bone("tail", (0, 11, 7), parent="body")
    g.cube("body", "chest", (-2.5, 8, -7), (5, 6, 6), "hound_fur")
    g.cube("body", "belly", (-2, 8.5, -1), (4, 4.5, 8), "hound_fur")
    g.cube("neck", "neck", (-1.5, 11, -10), (3, 4, 4), "hound_fur", rot=(-25, 0, 0), pivot=(0, 12, -7))
    g.cube("neck", "collar", (-1.8, 10.8, -9.2), (3.6, 1.2, 3.6), "guard_cloth", rot=(-25, 0, 0), pivot=(0, 12, -7))
    g.cube("neck", "collar_tag", (-0.5, 9.8, -9.6), (1, 1, 0.4), "silver")
    g.cube("head", "skull", (-2, 12.5, -12), (4, 4, 4), "hound_fur")
    g.cube("head", "snout", (-1, 12.5, -15.5), (2, 2, 3.5), "hound_fur")
    g.cube("head", "nose", (-0.5, 13.8, -15.6), (1, 0.8, 0.5), "frost_dark")
    g.cube("head", "ear_r", (-2, 16.5, -10), (1, 2, 1), "hound_fur", rot=(-35, 0, -15), pivot=(-1.4, 16.5, -9.5))
    g.cube("head", "ear_l", (1, 16.5, -10), (1, 2, 1), "hound_fur", rot=(-35, 0, 15), pivot=(1.4, 16.5, -9.5))
    g.cube("head", "eye_r", (-2.05, 14.8, -11), (0, 1, 1), "glow_frost", glow=True)
    g.cube("head", "eye_l", (2.05, 14.8, -11), (0, 1, 1), "glow_frost", glow=True)
    g.cube("jaw", "jaw", (-1, 11.5, -15), (2, 1, 3), "hound_fur")
    for name, x, z in (("leg_fr", -1.8, -5), ("leg_fl", 1.8, -5), ("leg_br", -1.8, 5), ("leg_bl", 1.8, 5)):
        g.cube(name, name, (x - 0.9, 0, z - 0.9), (1.8, 9, 1.8), "hound_fur")
    g.cube("tail", "tail", (-0.6, 9, 7), (1.2, 1.2, 7), "hound_fur", rot=(35, 0, 0), pivot=(0, 11, 7))
    return g


def frost_hound_anims():
    run = {
        "leg_fr": {"rotation": {0: [35, 0, 0], 0.25: [-35, 0, 0], 0.5: [35, 0, 0]}},
        "leg_fl": {"rotation": {0: [-35, 0, 0], 0.25: [35, 0, 0], 0.5: [-35, 0, 0]}},
        "leg_br": {"rotation": {0: [-35, 0, 0], 0.25: [35, 0, 0], 0.5: [-35, 0, 0]}},
        "leg_bl": {"rotation": {0: [35, 0, 0], 0.25: [-35, 0, 0], 0.5: [35, 0, 0]}},
        "body": {"position": {0: [0, 0, 0], 0.125: [0, 0.6, 0], 0.25: [0, 0, 0], 0.375: [0, 0.6, 0], 0.5: [0, 0, 0]}},
        "tail": {"rotation": {0: [0, -15, 0], 0.25: [0, 15, 0], 0.5: [0, -15, 0]}},
    }
    t = {
        "idle": loop(2.0, {
            "body": {"position": {0: [0, 0, 0], 1.0: [0, 0.3, 0], 2.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, -10, 0], 1.0: [0, 10, 0], 2.0: [0, -10, 0]}},
            "jaw": {"rotation": {0: [10, 0, 0], 1.0: [18, 0, 0], 2.0: [10, 0, 0]}},
        }),
        "walk": loop(0.5, run),
        "attack": once(0.7, {
            "neck": {"rotation": {0: [0, 0, 0], 0.35: [-15, 0, 0], 0.45: [20, 0, 0], 0.7: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.35: [40, 0, 0], 0.45: [0, 0, 0], 0.7: [0, 0, 0]}},
        }),
        # 飛びかかり（身を沈めて、跳ぶ）
        "skill_pounce": once(1.4, {
            "body": {"rotation": {0: [0, 0, 0], 0.7: [10, 0, 0], 0.9: [-25, 0, 0], 1.4: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.7: [0, -2, 0], 0.9: [0, 1, 0], 1.4: [0, 0, 0]}},
            "leg_fr": {"rotation": {0: [0, 0, 0], 0.9: [-60, 0, 0], 1.4: [0, 0, 0]}},
            "leg_fl": {"rotation": {0: [0, 0, 0], 0.9: [-60, 0, 0], 1.4: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.9: [40, 0, 0], 1.4: [0, 0, 0]}},
        }),
        # 遠吠え（首を上げる）
        "skill_howl": once(1.4, {
            "neck": {"rotation": {0: [0, 0, 0], 0.4: [-50, 0, 0], 1.1: [-50, 0, 0], 1.4: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [30, 0, 0], 1.1: [30, 0, 0], 1.4: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 0, 90]}, "position": {0: [0, 0, 0], 1.0: [0, -5, 0]}},
        }, hold=True),
    }
    return {"animation.frost_hound." + k: v for k, v in t.items()}


# ================================================================ 忘れられた侍女

def build_forgotten_maid():
    g = CastleGeo("forgotten_maid", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), parent="root")
    g.bone("skirt", (0, 12, 0), parent="body")
    g.bone("head", (0, 23, 0), parent="body")
    g.bone("arm_right", (-4, 21.5, 0), parent="body")
    g.bone("arm_left", (4, 21.5, 0), parent="body")
    g.bone("tray", (6, 15, -5), parent="arm_left")
    g.cube("skirt", "skirt", (-4.5, 0, -3.5), (9, 13, 7), "maid_dress", tags={"folds": True, "hem": True})
    g.cube("skirt", "apron", (-3.5, 2, -3.7), (7, 11, 0.4), "apron", tags={"lace": True})
    g.cube("body", "torso", (-3.5, 13, -2), (7, 10, 4), "maid_dress")
    g.cube("body", "bib", (-2.5, 14, -2.3), (5, 6, 0.4), "apron")
    g.cube("body", "collar", (-3, 22, -2.4), (6, 1, 4.8), "apron")
    g.cube("head", "head", (-3.5, 23, -3.5), (7, 7, 7), "maid_hair")
    g.cube("head", "face", (-2.5, 23.5, -3.6), (5, 5, 0), "blank_face")
    g.cube("head", "cap", (-3.8, 29, -3.8), (7.6, 1.5, 7.6), "apron", tags={"lace": True})
    g.cube("head", "bun", (-1.5, 26, 3.4), (3, 3, 2), "maid_hair")
    for side, x0 in (("right", -6), ("left", 3.5)):
        g.cube("arm_" + side, "arm_" + side, (x0, 13, -1.25), (2.5, 9, 2.5), "maid_dress")
        g.cube("arm_" + side, "cuff_" + side, (x0 - 0.1, 13, -1.35), (2.7, 1.5, 2.7), "apron")
    # 銀の盆（左手で、前に）
    g.cube("tray", "tray", (3, 14, -9), (7, 0.6, 6), "silver")
    g.cube("tray", "cup", (5.5, 14.6, -7.5), (1.5, 1.5, 1.5), "apron")
    return g


def forgotten_maid_anims():
    glide = {
        "body": {"position": {0: [0, 0, 0], 1.0: [0, 0.4, 0], 2.0: [0, 0, 0]}},
        "skirt": {"rotation": {0: [0, -4, 0], 1.0: [0, 4, 0], 2.0: [0, -4, 0]}},
        "arm_left": {"rotation": {0: [-55, 10, 0], 2.0: [-55, 10, 0]}},
    }
    t = {
        "idle": loop(2.0, {**glide, "head": {"rotation": {0: [8, 0, 0], 1.0: [10, 10, 0], 2.0: [8, 0, 0]}}}),
        "walk": loop(2.0, glide),
        # 盆で叩く（盆を振りかぶって、前を払う）
        "skill_tray": once(1.0, {
            "arm_left": {"rotation": {0: [-55, 10, 0], 0.5: [-120, 60, 0], 0.65: [-60, -50, 0], 1.0: [-55, 10, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.5: [0, 25, 0], 0.65: [0, -20, 0], 1.0: [0, 0, 0]}},
        }),
        # 介抱（かがんで、両手を差しのべる）
        "skill_tend": once(6.0, {
            "body": {"rotation": {0: [0, 0, 0], 0.6: [25, 0, 0], 5.4: [25, 0, 0], 6.0: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 0.6: [-70, 0, 0], 5.4: [-70, 0, 0], 6.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [-55, 10, 0], 0.6: [-70, 0, 0], 5.4: [-70, 0, 0], 6.0: [-55, 10, 0]}},
        }),
        # 逃げる（身をすくめる）
        "skill_flee": once(0.8, {
            "body": {"rotation": {0: [0, 0, 0], 0.3: [-10, 0, 0], 0.8: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 0.3: [-120, 0, 20], 0.8: [0, 0, 0]}},
        }),
        "attack": once(1.0, {
            "arm_left": {"rotation": {0: [-55, 10, 0], 0.5: [-120, 60, 0], 0.65: [-60, -50, 0], 1.0: [-55, 10, 0]}},
        }),
        "death": once(1.6, {
            "body": {"scale": {0: [1, 1, 1], 1.4: [0.05, 1.2, 0.05]}, "position": {0: [0, 0, 0], 1.4: [0, 4, 0]}},
        }, hold=True),
    }
    return {"animation.forgotten_maid." + k: v for k, v in t.items()}


# ================================================================ 氷の近衛長ヴァルグリム（ボス）

def build_valgrim():
    g = CastleGeo("valgrim", 256, 128)
    humanoid(g, 24, scale=1.55, plate="guard_plate", cloth="guard_cloth")
    s = 1.55
    g.bone("spear", (-5.5 * s, 13 * s, -1 * s), parent="arm_right")
    g.bone("great_shield", (7.5 * s, 16 * s, -2 * s), parent="arm_left")
    g.bone("plume", (0, 33 * s, 0), parent="head")
    g.bone("eyes", (0, 27 * s, -4 * s), parent="head")
    # 銀の縁（隊長）
    g.cube("body", "trim_chest", (-4.2 * s, 22 * s, -2.8 * s), (8.4 * s, 1 * s, 5.6 * s), "silver")
    g.cube("body", "gorget", (-3.5 * s, 23 * s, -3 * s), (7 * s, 1.5 * s, 6 * s), "silver")
    g.cube("body", "fur", (-5 * s, 23.5 * s, -0.5 * s), (10 * s, 1.5 * s, 4 * s), "fur_collar")
    # 兜の青い羽飾り（凍ってもなお青い）
    g.cube("plume", "plume", (-0.8 * s, 33 * s, -1 * s), (1.6 * s, 5 * s, 9 * s), "plume", rot=(-20, 0, 0),
           pivot=(0, 33 * s, 0))
    g.cube("plume", "plume_tail", (-0.6 * s, 31 * s, 6 * s), (1.2 * s, 4 * s, 3 * s), "plume", rot=(30, 0, 0),
           pivot=(0, 33 * s, 6 * s))
    # 白い顔の、青い目の光
    g.cube("eyes", "eye_r", (-2.5 * s, 27 * s, -4.2 * s), (1.5 * s, 1 * s, 0), "glow_oath", glow=True)
    g.cube("eyes", "eye_l", (1 * s, 27 * s, -4.2 * s), (1.5 * s, 1 * s, 0), "glow_oath", glow=True)
    # 氷の長槍
    g.cube("spear", "spear_shaft", (-6.5 * s, 0, -2 * s), (1 * s, 32 * s, 1 * s), "silver")
    g.cube("spear", "spear_head", (-6.8 * s, 32 * s, -2.4 * s), (1.6 * s, 7 * s, 1.8 * s), "ice")
    g.cube("spear", "spear_wings", (-7 * s, 30 * s, -3.5 * s), (2 * s, 1.5 * s, 4 * s), "ice")
    # 塔の大盾（近衛の誓いの紋：雪の花と剣）
    g.cube("great_shield", "great_shield", (7.6 * s, 4 * s, -7 * s), (1.5 * s, 20 * s, 11 * s), "shield_face",
           tags={"emblem": True, "sword": True})
    g.cube("great_shield", "great_shield_rim", (7.4 * s, 3.5 * s, -7.5 * s), (1.2 * s, 21 * s, 12 * s), "silver", inflate=0.02)
    g.cube("great_shield", "oath_gem", (9.15 * s, 15 * s, -2 * s), (0.2 * s, 2 * s, 2 * s), "glow_oath", glow=True)
    return g


def valgrim_anims():
    s = 1.55
    shield_up = {"arm_left": {"rotation": {0: [-40, 20, 0]}}}
    t = {
        "idle": loop(4.0, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 4.0: [-40, 20, 0]}},
            "arm_right": {"rotation": {0: [-14, 0, 0], 4.0: [-14, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 2.0: [0, -0.4, 0], 4.0: [0, 0, 0]}},
            "plume": {"rotation": {0: [0, 0, 0], 2.0: [0, 6, 0], 4.0: [0, 0, 0]}},
        }),
        "walk": loop(1.4, {**guard_walk(), **shield_up}),
        # 槍の突き（予兆 1.2 秒：引いて、長く突く）
        "skill_thrust": once(1.7, {
            "arm_right": {"rotation": {0: [-14, 0, 0], 1.0: [-55, 0, 0], 1.2: [-95, 0, 0], 1.7: [-14, 0, 0]},
                          "position": {0: [0, 0, 0], 1.0: [0, 0, 3], 1.2: [0, 0, -5], 1.7: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.0: [0, 18, 0], 1.2: [10, -12, 0], 1.7: [0, 0, 0]}},
            **shield_up,
        }),
        # 盾打ち（予兆 1.0 秒：大盾を引いて、前へ叩きつける）
        "skill_bash": once(1.5, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.9: [-30, 60, 0], 1.0: [-85, -15, 0], 1.5: [-40, 20, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.9: [0, -25, 0], 1.0: [8, 22, 0], 1.5: [0, 0, 0]}},
        }),
        # 氷槍の列（槍を地面に突き立てる）
        "skill_spears": once(2.0, {
            "arm_right": {"rotation": {0: [-14, 0, 0], 1.2: [-165, 0, 10], 1.45: [-40, 0, 0], 2.0: [-14, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.2: [-10, 0, 0], 1.45: [20, 0, 0], 2.0: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.45: [0, -2, 0], 2.0: [0, 0, 0]}},
            **shield_up,
        }),
        # 誓いの構え（大盾を前に立て、身を低く）
        "skill_guard": once(5.6, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.4: [-80, 0, 0], 5.2: [-80, 0, 0], 5.6: [-40, 20, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.4: [0, -2, 0], 5.2: [0, -2, 0], 5.6: [0, 0, 0]}},
        }),
        # 集え（槍を高く掲げる）
        "skill_rally": once(1.6, {
            "arm_right": {"rotation": {0: [-14, 0, 0], 0.5: [-170, 0, 0], 1.2: [-170, 0, 0], 1.6: [-14, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.5: [-20, 0, 0], 1.2: [-20, 0, 0], 1.6: [0, 0, 0]}},
            **shield_up,
        }),
        # 近衛の行進（槍を前へ倒し、盾を構えて、足並みをそろえる）
        "skill_march": once(4.0, {
            "arm_right": {"rotation": {0: [-14, 0, 0], 0.6: [-80, 0, 0], 4.0: [-80, 0, 0]}},
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.6: [-80, 0, 0], 4.0: [-80, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 0], 1.0: [25, 0, 0], 1.4: [0, 0, 0], 1.8: [25, 0, 0], 2.2: [0, 0, 0],
                                       2.6: [25, 0, 0], 3.0: [0, 0, 0], 3.4: [25, 0, 0], 4.0: [0, 0, 0]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 0.8: [25, 0, 0], 1.2: [0, 0, 0], 1.6: [25, 0, 0], 2.0: [0, 0, 0], 2.4: [25, 0, 0],
                                      2.8: [0, 0, 0], 3.2: [25, 0, 0], 3.6: [0, 0, 0], 4.0: [0, 0, 0]}},
        }),
        # 誓いが崩れる（槍を落とし、膝をつく。8秒）
        "skill_crumble": once(8.0, {
            "body": {"position": {0: [0, 0, 0], 0.6: [0, -7 * s, 0], 7.4: [0, -7 * s, 0], 8.0: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.6: [22, 0, 0], 7.4: [22, 0, 0], 8.0: [0, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 0.6: [-90, 0, 0], 7.4: [-90, 0, 0], 8.0: [0, 0, 0]},
                          "position": {0: [0, 0, 0], 0.6: [0, -5 * s, 3 * s], 7.4: [0, -5 * s, 3 * s], 8.0: [0, 0, 0]}},
            "leg_left": {"position": {0: [0, 0, 0], 0.6: [0, -2 * s, 0], 7.4: [0, -2 * s, 0], 8.0: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [-14, 0, 0], 0.6: [10, 0, 10], 7.4: [10, 0, 10], 8.0: [-14, 0, 0]}},
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.6: [-20, 0, -10], 7.4: [-20, 0, -10], 8.0: [-40, 20, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [30, 0, 0], 7.4: [30, 0, 0], 8.0: [0, 0, 0]}},
        }),
        "attack": once(1.5, {
            "arm_left": {"rotation": {0: [-40, 20, 0], 0.9: [-30, 60, 0], 1.0: [-85, -15, 0], 1.5: [-40, 20, 0]}},
        }),
        "death": once(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.0: [0, -7 * s, 0]}, "rotation": {0: [0, 0, 0], 1.0: [24, 0, 0], 2.6: [70, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.0: [-90, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, -5 * s, 3 * s]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [30, 0, 0]}},
            "arm_left": {"rotation": {0: [-40, 20, 0], 1.0: [0, 0, -30]}},
        }, hold=True),
    }
    return {"animation.valgrim." + k: v for k, v in t.items()}


MODELS = [(build_frozen_guard, frozen_guard_anims), (build_ice_cavalry, ice_cavalry_anims), (build_shelf_wraith, shelf_wraith_anims),
          (build_frost_hound, frost_hound_anims), (build_forgotten_maid, forgotten_maid_anims), (build_valgrim, valgrim_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(CastlePainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "frozen_guard": dict(scale=6.0, center=(0, 16, 0), size=(240, 300)),
        "ice_cavalry": dict(scale=4.6, center=(0, 22, -4), size=(300, 300)),
        "shelf_wraith": dict(scale=7.0, center=(0, 15, 0), size=(240, 280)),
        "frost_hound": dict(scale=9.0, center=(0, 9, -3), size=(260, 220)),
        "forgotten_maid": dict(scale=7.5, center=(0, 15, 0), size=(220, 280)),
        "valgrim": dict(scale=3.8, center=(0, 26, 0), size=(300, 340)),
    }
    poses = {
        "frozen_guard": {"arm_right": ((-95, 0, 0),), "arm_left": ((-40, 20, 0),)},
        "ice_cavalry": {"arm_right": ((0, 0, 0),)},
        "shelf_wraith": {"arm_right": ((-160, 0, 10),), "arm_left": ((-160, 0, -10),)},
        "frost_hound": {"jaw": ((40, 0, 0),), "leg_fr": ((-60, 0, 0),), "leg_fl": ((-60, 0, 0),)},
        "forgotten_maid": {"arm_left": ((-55, 10, 0),)},
        "valgrim": {"arm_right": ((-95, 0, 0),), "arm_left": ((-80, 0, 0),)},
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
    path = os.path.join(out_dir, "castle_models_check.png")
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
