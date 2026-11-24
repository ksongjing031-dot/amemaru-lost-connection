"""5階「古代守護者の祭壇」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（11月24日【補完】）。

  python3 tools/artgen/make_altar_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の5階：深い奈落の上の円形の祭壇。4本の柱と、上へ続く大扉。
作るもの（ファイルの名前は敵のIDと同じ。geo/entity・animations/entity・textures/entity）:
  watcher           監視眼ウォッチャー：金の枠に囲まれた、宙に浮かぶ大きな白い目。赤く光る瞳。上下のまぶた。左右に青銅のひれ
  sentinel          防衛兵センチネル：白い石と青銅の兵。翠に光る目のすき間の兜。左腕に大きな塔の盾（古代の紋）、右手に槍
  repair_drone      修復ドローン：青銅の小さな球に、4本の腕と回る羽根。下に翠に光る修理の腕
  ancient_guardian  古代守護者：白い石と青銅の巨人。胸に翠の記録核、兜の目のすき間が翠に光る。左腕はロストで白く欠けている。
                    背に4本の短い柱の飾り。結界の間だけ、まわりに翠の結晶が浮かぶ（ボーン barrier）
どれもアニメーションは idle・walk・attack・death と、敵ごとの技（skill_…）。名前は Java の actionNames と同じ。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import anims, loop, once, posed, sheet_font  # noqa: E402
from make_temple_models import LOST_WHITE, TEMPLE, TemplePainter  # noqa: E402
from materials import RAMPS, pick, ramp  # noqa: E402

ALTAR = {
    "glow_jade": ramp("#1E7A52", "#2A9A68", "#3FBC84", "#66D8A2", "#98ECC2", "#CCF8E2", "#FFFFFF"),
    "glow_red_eye": ramp("#7A1410", "#9C2218", "#C03A26", "#DE6446", "#F09474", "#FCCBB4", "#FFFFFF"),
    "eye_white": ramp("#7C766C", "#9A948A", "#B6B0A6", "#CEC9BF", "#E2DED6", "#F0EDE7", "#FBFAF7"),
}
for _k, _v in ALTAR.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class AltarPainter(TemplePainter):
    def paint_bronze(self, c):
        tone = self.std(c, "bronze", base=3.2, noise=0.25, blob=0.4, up=1.5, down=1.0)
        tone += self.rivet(c)
        return pick(RAMPS["bronze"], tone)

    def paint_lost_arm(self, c):
        """ロストで白く欠けた腕：ほとんど真っ白。ところどころ元の石が残る"""
        if self.blob(c, 1.5, 801 + self.eid(c)) < 0.72:
            return LOST_WHITE
        return pick(TEMPLE["marble"], 3.6 + (self.tex(c, 802) - 0.5))

    def paint_eye_white(self, c):
        tone = self.std(c, "eye_white", base=4.6, noise=0.2, blob=0.2, up=0.8, down=0.8)
        if c.face.d != "front" and self.tex(c, 811) < 0.06:
            return pick(ALTAR["glow_red_eye"], 1.6)   # 赤い血管
        return pick(ALTAR["eye_white"], tone)

    def paint_glow_jade(self, c):
        return self.paint_glow(c, "glow_jade", base=4.3)

    def paint_glow_red_eye(self, c):
        tone = 4.0 + (self.tex(c, 821) - 0.5) * 0.4
        d = c.edge_dist()
        if min(d.values()) >= 1:
            tone -= 1.6   # まん中は黒い瞳孔に近い赤
        return pick(ALTAR["glow_red_eye"], tone)

    def paint_shield(self, c):
        tone = self.std(c, "marble", base=4.0, noise=0.25, blob=0.35, up=1.2, down=1.0)
        if self.facing(c) and c.face.d == "front":
            # 古代の紋（菱形と、中心の結び目）
            cx, cy = c.W / 2.0, c.H / 2.0
            dx, dy = abs(c.i + 0.5 - cx), abs(c.j + 0.5 - cy)
            if abs(dx + dy * 0.7 - 3.2) < 0.55:
                return pick(RAMPS["bronze"], 3.6 + (self.tex(c, 831) - 0.5))
            if dx < 1.0 and dy < 1.0:
                return pick(ALTAR["glow_jade"], 4.0)
            if c.i in (0, c.W - 1) or c.j in (0, c.H - 1):
                return pick(RAMPS["bronze"], 3.0 + self.bevel(c))
        return pick(TEMPLE["marble"], tone)


# ================================================================ 監視眼ウォッチャー

def build_watcher():
    g = GeoModel("watcher", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 8, 0), parent="root")
    g.bone("lid_top", (0, 12, -3), parent="body")
    g.bone("lid_bottom", (0, 4, -3), parent="body")
    g.bone("fin_right", (-5, 8, 1), parent="body")
    g.bone("fin_left", (5, 8, 1), parent="body")
    g.cube("body", "eye", (-4, 4, -4), (8, 8, 8), "eye_white")
    g.cube("body", "iris", (-2, 6, -4.1), (4, 4, 0), "glow_red_eye", glow=True)
    g.cube("body", "frame_top", (-5, 12, -1), (10, 1, 3), "gold")
    g.cube("body", "frame_bottom", (-5, 3, -1), (10, 1, 3), "gold")
    g.cube("body", "frame_right", (-5, 4, -1), (1, 8, 3), "gold")
    g.cube("body", "frame_left", (4, 4, -1), (1, 8, 3), "gold")
    g.cube("body", "back", (-3, 5, 4), (6, 6, 2), "bronze", tags={"rivets": [(0, 8)]})
    g.cube("body", "tail", (-1, 0, 1), (2, 4, 2), "bronze")
    g.cube("body", "tail_tip", (-0.5, -2, 1.5), (1, 2, 1), "glow_jade", glow=True)
    g.cube("lid_top", "lid_t", (-4.5, 11, -4.6), (9, 2, 5), "marble_dark")
    g.cube("lid_bottom", "lid_b", (-4.5, 3, -4.6), (9, 2, 5), "marble_dark")
    g.cube("fin_right", "fin_r", (-9, 7, 0), (4, 3, 1), "bronze", rot=(0, 0, 15), pivot=(-5, 8, 0.5))
    g.cube("fin_left", "fin_l", (5, 7, 0), (4, 3, 1), "bronze", rot=(0, 0, -15), pivot=(5, 8, 0.5))
    return g


def watcher_anims():
    t = {
        "idle": loop(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.5: [0, 1.2, 0], 3.0: [0, 0, 0]}},
            "lid_top": {"position": {0: [0, 0, 0], 2.2: [0, 0, 0], 2.3: [0, -3.5, 0], 2.4: [0, 0, 0], 3.0: [0, 0, 0]}},
            "lid_bottom": {"position": {0: [0, 0, 0], 2.2: [0, 0, 0], 2.3: [0, 3.5, 0], 2.4: [0, 0, 0], 3.0: [0, 0, 0]}},
            "fin_right": {"rotation": {0: [0, 0, 0], 0.75: [0, 0, 12], 1.5: [0, 0, 0], 2.25: [0, 0, 12], 3.0: [0, 0, 0]}},
            "fin_left": {"rotation": {0: [0, 0, 0], 0.75: [0, 0, -12], 1.5: [0, 0, 0], 2.25: [0, 0, -12], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(1.0, {
            "body": {"rotation": {0: [12, 0, 0], 1.0: [12, 0, 0]}, "position": {0: [0, 0, 0], 0.5: [0, 0.6, 0], 1.0: [0, 0, 0]}},
            "fin_right": {"rotation": {0: [0, 0, 0], 0.25: [0, 0, 25], 0.5: [0, 0, 0], 0.75: [0, 0, 25], 1.0: [0, 0, 0]}},
            "fin_left": {"rotation": {0: [0, 0, 0], 0.25: [0, 0, -25], 0.5: [0, 0, 0], 0.75: [0, 0, -25], 1.0: [0, 0, 0]}},
        }),
        "attack": once(1.0, {
            "body": {"position": {0: [0, 0, 0], 0.7: [0, 0, 1], 0.8: [0, 0, -1.5], 1.0: [0, 0, 0]},
                     "scale": {0: [1, 1, 1], 0.7: [0.92, 0.92, 0.92], 0.8: [1.1, 1.1, 1.1], 1.0: [1, 1, 1]}},
        }),
        # 見つけた（まぶたを大きく開き、瞳が光る）
        "skill_spot": once(1.2, {
            "lid_top": {"position": {0: [0, 0, 0], 0.2: [0, 1.5, 0], 1.0: [0, 1.5, 0], 1.2: [0, 0, 0]}},
            "lid_bottom": {"position": {0: [0, 0, 0], 0.2: [0, -1.5, 0], 1.0: [0, -1.5, 0], 1.2: [0, 0, 0]}},
            "body": {"scale": {0: [1, 1, 1], 0.2: [1.2, 1.2, 1.2], 0.4: [1.05, 1.05, 1.05], 1.2: [1, 1, 1]}},
        }),
        "death": once(1.4, {
            "body": {"rotation": {0: [0, 0, 0], 1.4: [180, 90, 0]}, "position": {0: [0, 0, 0], 1.4: [0, -8, 0]},
                     "scale": {0: [1, 1, 1], 1.2: [1, 1, 1], 1.4: [0.2, 0.2, 0.2]}},
        }, hold=True),
    }
    return anims("watcher", t)


# ================================================================ 防衛兵センチネル

def build_sentinel():
    g = GeoModel("sentinel", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("leg_right", (-2.5, 13, 0), parent="root")
    g.bone("leg_left", (2.5, 13, 0), parent="root")
    g.bone("body", (0, 13, 0), parent="root")
    g.bone("head", (0, 29, 0), parent="body")
    g.bone("arm_right", (-7, 27, 0), parent="body")
    g.bone("arm_left", (7, 27, 0), parent="body")
    g.cube("leg_right", "leg_r", (-4.5, 0, -2.5), (4, 13, 5), "marble")
    g.cube("leg_left", "leg_l", (0.5, 0, -2.5), (4, 13, 5), "marble")
    g.cube("leg_right", "boot_r", (-5, 0, -3.5), (5, 3, 6), "bronze")
    g.cube("leg_left", "boot_l", (0, 0, -3.5), (5, 3, 6), "bronze")
    g.cube("body", "hips", (-5.5, 13, -3), (11, 4, 6), "bronze", tags={"rivets": [(-3, 15), (3, 15)]})
    g.cube("body", "torso", (-5.5, 17, -3), (11, 12, 6), "marble")
    g.cube("body", "chest_plate", (-5, 20, -3.6), (10, 7, 1), "bronze", tags={"rivets": [(-4, 26), (4, 26)]})
    g.cube("body", "core", (-1, 22, -3.9), (2, 2, 0), "glow_jade", glow=True)
    g.cube("head", "head", (-3.5, 29, -3.5), (7, 7, 7), "marble")
    g.cube("head", "helm_crest", (-0.5, 36, -4), (1, 2, 8), "bronze")
    g.cube("head", "visor", (-3, 32, -3.7), (6, 1, 0), "glow_jade", glow=True)
    g.cube("arm_right", "arm_r", (-10, 16, -2), (3, 12, 4), "marble")
    g.cube("arm_right", "pauldron_r", (-11, 25, -3), (5, 3, 6), "bronze")
    g.cube("arm_right", "spear", (-9, 14, -14), (1, 1, 22), "walnut")
    g.cube("arm_right", "spear_head", (-9.5, 13.5, -18), (2, 2, 4), "glow_jade", glow=True)
    g.cube("arm_left", "arm_l", (7, 16, -2), (3, 12, 4), "marble")
    g.cube("arm_left", "pauldron_l", (6, 25, -3), (5, 3, 6), "bronze")
    g.cube("arm_left", "shield", (9, 8, -8), (2, 22, 14), "shield")
    return g


def sentinel_anims():
    t = {
        "idle": loop(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.5: [0, -0.3, 0], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.5: [0, 10, 0], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(1.2, {
            "leg_right": {"rotation": {0: [18, 0, 0], 0.6: [-18, 0, 0], 1.2: [18, 0, 0]}},
            "leg_left": {"rotation": {0: [-18, 0, 0], 0.6: [18, 0, 0], 1.2: [-18, 0, 0]}},
            "arm_right": {"rotation": {0: [-10, 0, 0], 0.6: [10, 0, 0], 1.2: [-10, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.3: [0, -0.5, 0], 0.6: [0, 0, 0], 0.9: [0, -0.5, 0], 1.2: [0, 0, 0]}},
        }),
        # 盾打ち（盾を前へ突き出す）
        "attack": once(1.2, {
            "arm_left": {"rotation": {0: [0, 0, 0], 0.7: [0, 40, 0], 0.85: [-20, -25, 0], 1.2: [0, 0, 0]},
                         "position": {0: [0, 0, 0], 0.85: [0, 0, -3], 1.2: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.7: [0, 20, 0], 0.85: [0, -15, 0], 1.2: [0, 0, 0]}},
        }),
        # 槍の突進（構えて、突く）
        "skill_charge": once(1.6, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.9: [0, -10, 0], 1.05: [0, 0, 0], 1.6: [0, 0, 0]},
                          "position": {0: [0, 0, 0], 0.9: [0, 0, 3], 1.05: [0, 0, -4], 1.6: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.9: [8, 0, 0], 1.05: [14, 0, 0], 1.6: [0, 0, 0]}},
        }),
        # 兵を呼ぶ（槍を高く掲げる）
        "skill_call": once(1.6, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-160, 0, 0], 1.2: [-160, 0, 0], 1.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.5: [-25, 0, 0], 1.2: [-25, 0, 0], 1.6: [0, 0, 0]}},
        }),
        "death": once(1.8, {
            "body": {"rotation": {0: [0, 0, 0], 1.0: [80, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, -10, -4]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.0: [-60, 0, 0]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 1.0: [-40, 0, 0]}},
        }, hold=True),
    }
    return anims("sentinel", t)


# ================================================================ 修復ドローン

ROTORS = (("rotor_ne", 4, -4), ("rotor_nw", -4, -4), ("rotor_se", 4, 4), ("rotor_sw", -4, 4))


def build_repair_drone():
    g = GeoModel("repair_drone", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 5, 0), parent="root")
    g.bone("arm", (0, 3, -1), parent="body")
    g.cube("body", "ball", (-3, 3, -3), (6, 6, 6), "bronze", tags={"rivets": [(0, 7)]})
    g.cube("body", "eye", (-1, 5, -3.2), (2, 2, 0), "glow_jade", glow=True)
    g.cube("body", "band", (-3.5, 5.5, -3.5), (7, 1, 7), "gold")
    for name, x, z in ROTORS:
        g.cube("body", name + "_arm", (min(0, x), 7.5, z - 0.5) if x > 0 else (x, 7.5, z - 0.5), (abs(x), 1, 1), "bronze")
        g.bone(name, (x, 9, z), parent="body")
        g.cube(name, name + "_blade", (x - 2.5, 9, z - 0.5), (5, 0, 1), "marble_dark")
        g.cube(name, name + "_hub", (x - 0.5, 8.5, z - 0.5), (1, 1, 1), "gold")
    g.cube("arm", "arm_rod", (-0.5, 0, -1.5), (1, 3, 1), "bronze")
    g.cube("arm", "arm_tip", (-1, -1, -2), (2, 1, 2), "glow_jade", glow=True)
    return g


def repair_drone_anims():
    spin = {}
    for k, (name, _, _) in enumerate(ROTORS):
        sign = 1 if k % 2 == 0 else -1
        spin[name] = {"rotation": {0: [0, 0, 0], 0.25: [0, 360 * sign, 0]}}
    idle = dict(spin)
    idle["body"] = {"position": {0: [0, 0, 0], 0.125: [0, 0.3, 0], 0.25: [0, 0, 0]}}
    walk = dict(spin)
    walk["body"] = {"rotation": {0: [15, 0, 0], 0.25: [15, 0, 0]}}
    rep = {"arm": {"rotation": {0: [0, 0, 0], 0.3: [-60, 0, 0], 1.3: [-60, 0, 0], 1.6: [0, 0, 0]},
                   "scale": {0: [1, 1, 1], 0.3: [1, 1.6, 1], 1.3: [1, 1.6, 1], 1.6: [1, 1, 1]}}}
    t = {
        "idle": loop(0.25, idle),
        "walk": loop(0.25, walk),
        "attack": once(0.8, {"arm": {"rotation": {0: [0, 0, 0], 0.4: [-80, 0, 0], 0.6: [0, 0, 0]}},
                             "body": {"scale": {0: [1, 1, 1], 0.4: [1.15, 1.15, 1.15], 0.8: [1, 1, 1]}}}),
        "skill_repair": once(1.6, rep),
        "death": once(1.2, {"body": {"rotation": {0: [0, 0, 0], 1.2: [120, 0, 60]}, "position": {0: [0, 0, 0], 1.2: [0, -6, 0]}}},
                      hold=True),
    }
    return anims("repair_drone", t)


# ================================================================ 古代守護者（ボス）

def build_ancient_guardian():
    g = GeoModel("ancient_guardian", 256, 256)
    g.bone("root", (0, 0, 0))
    g.bone("leg_right", (-7, 30, 0), parent="root")
    g.bone("leg_left", (7, 30, 0), parent="root")
    g.bone("body", (0, 30, 0), parent="root")
    g.bone("head", (0, 68, -2), parent="body")
    g.bone("arm_right", (-16, 62, 0), parent="body")
    g.bone("arm_left", (16, 62, 0), parent="body")
    g.bone("core", (0, 52, -9), parent="body")
    g.bone("barrier", (0, 40, 0), parent="root")
    # 脚（太い石の柱の脚。青銅の膝当て）
    for bone, x0 in (("leg_right", -12), ("leg_left", 2)):
        g.cube(bone, bone + "_thigh", (x0, 14, -5), (10, 16, 10), "marble")
        g.cube(bone, bone + "_knee", (x0 - 0.5, 13, -6), (11, 4, 3), "bronze", tags={"rivets": [(x0 + 5, 15)]})
        g.cube(bone, bone + "_shin", (x0 + 0.5, 3, -4.5), (9, 11, 9), "marble")
        g.cube(bone, bone + "_foot", (x0 - 0.5, 0, -7), (11, 3, 12), "marble_dark")
    # 胴（腰の青銅の帯・大きな胸・肩の装甲）
    g.cube("body", "hips", (-12, 30, -6), (24, 8, 12), "bronze", tags={"rivets": [(-9, 34), (9, 34)]})
    g.cube("body", "torso", (-13, 38, -8), (26, 24, 16), "marble", tags={"folds": False})
    g.cube("body", "chest_rim", (-6, 47, -9), (12, 10, 1), "bronze", tags={"rivets": [(-5, 56), (5, 56)]})
    g.cube("body", "neck", (-4, 62, -3), (8, 4, 6), "marble_dark")
    g.cube("body", "shoulder_r", (-19, 58, -7), (9, 6, 14), "bronze")
    g.cube("body", "shoulder_l", (10, 58, -7), (9, 6, 14), "lost_arm")
    # 背の4本の短い柱（祭壇の柱の写し）
    for k, x in enumerate((-10, -4, 2, 8)):
        g.cube("body", "back_pillar_%d" % k, (x, 50 + (k % 2) * 3, 8), (2, 14, 2), "marble_dark")
        g.cube("body", "back_crystal_%d" % k, (x, 64 + (k % 2) * 3, 8), (2, 2, 2), "glow_jade", glow=True)
    # 胸の翠の記録核
    g.cube("core", "core", (-4, 48, -10), (8, 8, 2), "glow_jade", glow=True)
    # 頭（兜。目のすき間が翠に光る）
    g.cube("head", "head", (-6, 66, -8), (12, 12, 12), "marble")
    g.cube("head", "helm_top", (-6.5, 76, -8.5), (13, 3, 13), "bronze")
    g.cube("head", "eyes", (-4, 71, -8.2), (8, 1, 0), "glow_jade", glow=True)
    g.cube("head", "jaw", (-5, 66, -9), (10, 3, 2), "marble_dark")
    # 右腕（石と青銅。大きな拳）
    g.cube("arm_right", "arm_r_upper", (-23, 46, -5), (8, 14, 10), "marble")
    g.cube("arm_right", "arm_r_band", (-23.5, 44, -5.5), (9, 3, 11), "bronze")
    g.cube("arm_right", "arm_r_lower", (-23, 32, -5), (8, 12, 10), "marble")
    g.cube("arm_right", "fist_r", (-24, 24, -6), (10, 8, 12), "bronze", tags={"rivets": [(-19, 28)]})
    # 左腕（ロストで白く欠けている。先が欠けて短い）
    g.cube("arm_left", "arm_l_upper", (15, 46, -5), (8, 14, 10), "lost_arm")
    g.cube("arm_left", "arm_l_lower", (15, 34, -5), (8, 12, 10), "lost_arm")
    g.cube("arm_left", "fist_l", (14.5, 28, -5.5), (9, 6, 11), "lost_arm")
    # 結界の結晶（結界の間だけ見える）
    for k in range(8):
        a = math.radians(k * 45)
        x, z = 30 * math.cos(a), 30 * math.sin(a)
        g.cube("barrier", "shard_%d" % k, (x - 1.5, 34 + (k % 3) * 6, z - 1.5), (3, 7, 3), "glow_jade", rot=(0, 45, 0),
               pivot=(x, 34, z), glow=True)
    return g


def ancient_guardian_anims():
    t = {
        "idle": loop(4.0, {
            "body": {"position": {0: [0, 0, 0], 2.0: [0, -0.8, 0], 4.0: [0, 0, 0]}},
            "core": {"scale": {0: [1, 1, 1], 1.0: [1.15, 1.15, 1.0], 2.0: [1, 1, 1], 3.0: [1.15, 1.15, 1.0], 4.0: [1, 1, 1]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 2.4: [0, 0, 0], 2.5: [-8, 0, -6], 2.6: [0, 0, 0], 4.0: [0, 0, 0]}},
            "barrier": {"rotation": {0: [0, 0, 0], 4.0: [0, 360, 0]}},
        }),
        "walk": loop(2.4, {
            "leg_right": {"rotation": {0: [16, 0, 0], 1.2: [-16, 0, 0], 2.4: [16, 0, 0]}},
            "leg_left": {"rotation": {0: [-16, 0, 0], 1.2: [16, 0, 0], 2.4: [-16, 0, 0]}},
            "arm_right": {"rotation": {0: [-8, 0, 0], 1.2: [8, 0, 0], 2.4: [-8, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.6: [0, -1.5, 0], 1.2: [0, 0, 0], 1.8: [0, -1.5, 0], 2.4: [0, 0, 0]},
                     "rotation": {0: [0, -3, 0], 1.2: [0, 3, 0], 2.4: [0, -3, 0]}},
            "barrier": {"rotation": {0: [0, 0, 0], 2.4: [0, 216, 0]}},
        }),
        "attack": once(1.4, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.8: [-100, 0, 10], 1.0: [-20, 0, 0], 1.4: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.8: [0, 15, 0], 1.0: [0, -12, 0], 1.4: [0, 0, 0]}},
        }),
        # 腕の薙ぎ払い（右腕を大きく振る）
        "skill_sweep": once(2.0, {
            "arm_right": {"rotation": {0: [0, 0, 0], 1.1: [-50, -60, -60], 1.3: [-40, 60, -20], 2.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.1: [-30, 0, 30], 1.3: [-60, 0, -10], 2.0: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.1: [0, -30, 0], 1.3: [0, 30, 0], 2.0: [0, 0, 0]}},
        }),
        # 直線の光線（胸を張って、記録核から撃つ）
        "skill_beam": once(2.2, {
            "body": {"rotation": {0: [0, 0, 0], 1.4: [-12, 0, 0], 1.6: [6, 0, 0], 2.2: [0, 0, 0]}},
            "core": {"scale": {0: [1, 1, 1], 1.4: [1.6, 1.6, 1.2], 1.6: [0.9, 0.9, 1], 2.2: [1, 1, 1]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 1.4: [20, 0, 30], 2.2: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.4: [20, 0, -30], 2.2: [0, 0, 0]}},
        }),
        # 叩きつけ（両腕を振り上げて、地面へ）
        "skill_slam": once(2.0, {
            "arm_right": {"rotation": {0: [0, 0, 0], 1.2: [-170, 0, 10], 1.4: [-30, 0, 0], 2.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.2: [-170, 0, -10], 1.4: [-30, 0, 0], 2.0: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.2: [-12, 0, 0], 1.4: [20, 0, 0], 2.0: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.4: [0, -3, 0], 2.0: [0, 0, 0]}},
        }),
        # 結界（両腕を広げて天を仰ぐ）
        "skill_barrier": once(2.4, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.8: [-30, 0, 70], 2.0: [-30, 0, 70], 2.4: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.8: [-30, 0, -70], 2.0: [-30, 0, -70], 2.4: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.8: [-30, 0, 0], 2.0: [-30, 0, 0], 2.4: [0, 0, 0]}},
        }),
        # よろめく（結界が解けた：片ひざをつきかける）
        "skill_stagger": once(4.0, {
            "body": {"position": {0: [0, 0, 0], 0.4: [0, -8, 0], 3.6: [0, -8, 0], 4.0: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.4: [16, 0, -5], 3.6: [16, 0, -5], 4.0: [0, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 0.4: [-50, 0, 0], 3.6: [-50, 0, 0], 4.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.4: [25, 0, 0], 3.6: [25, 0, 0], 4.0: [0, 0, 0]}},
        }),
        # 接続判定（両腕を前へ、目が光る）
        "skill_judge": once(3.2, {
            "arm_right": {"rotation": {0: [0, 0, 0], 1.0: [-80, 0, 30], 2.8: [-80, 0, 30], 3.2: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.0: [-80, 0, -30], 2.8: [-80, 0, -30], 3.2: [0, 0, 0]}},
            "core": {"scale": {0: [1, 1, 1], 1.0: [1.3, 1.3, 1.1], 2.8: [1.5, 1.5, 1.2], 3.2: [1, 1, 1]}},
        }),
        # 膝をつく（集まるのを待つ間、ずっと膝をついたまま）
        "skill_kneel": once(64.0, {
            "body": {"position": {0: [0, 0, 0], 1.2: [0, -14, 0], 64.0: [0, -14, 0]},
                     "rotation": {0: [0, 0, 0], 1.2: [12, 0, 0], 64.0: [12, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 1.2: [-85, 0, 0], 64.0: [-85, 0, 0]},
                          "position": {0: [0, 0, 0], 1.2: [0, -6, -4], 64.0: [0, -6, -4]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 1.2: [-20, 0, 0], 64.0: [-20, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [30, 0, 0], 64.0: [30, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 1.2: [10, 0, 10], 64.0: [10, 0, 10]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.2: [10, 0, -10], 64.0: [10, 0, -10]}},
        }),
        "death": once(3.0, {
            "body": {"position": {0: [0, -14, 0], 2.0: [0, -26, 6]}, "rotation": {0: [12, 0, 0], 2.0: [70, 0, 0]}},
            "head": {"rotation": {0: [30, 0, 0], 2.0: [40, 0, 0]}},
            "leg_right": {"rotation": {0: [-85, 0, 0], 2.0: [-85, 0, 0]}},
            "core": {"scale": {0: [1, 1, 1], 2.0: [1.6, 1.6, 1.6], 3.0: [0.1, 0.1, 0.1]}},
        }, hold=True),
    }
    return anims("ancient_guardian", t)


MODELS = [(build_watcher, watcher_anims), (build_sentinel, sentinel_anims), (build_repair_drone, repair_drone_anims),
          (build_ancient_guardian, ancient_guardian_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(AltarPainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import Image, ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    layout = {
        "watcher": dict(scale=12.0, center=(0, 6, 0), size=(220, 220)),
        "sentinel": dict(scale=6.5, center=(0, 18, -2), size=(260, 300)),
        "repair_drone": dict(scale=16.0, center=(0, 5, 0), size=(220, 200)),
        "ancient_guardian": dict(scale=2.9, center=(0, 40, 0), size=(300, 320)),
    }
    poses = {
        "watcher": {"lid_top": ((0, 0, 0), 1.0, (0, 1.5, 0)), "lid_bottom": ((0, 0, 0), 1.0, (0, -1.5, 0))},
        "sentinel": {"arm_right": ((0, -10, 0), 1.0, (0, 0, -4)), "body": ((14, 0, 0),)},
        "repair_drone": {"arm": ((-60, 0, 0), 1.0)},
        "ancient_guardian": {"arm_right": ((-170, 0, 10),), "arm_left": ((-170, 0, -10),), "barrier": ((0, 0, 0), 1.0)},
    }
    sheets = []
    for name, (g, tex) in models.items():
        lay = layout[name]
        tiles = []
        for yaw, pitch in ((150, 14), (-30, 14), (180, 0), (90, 0)):
            tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, **lay))
        tiles.append(render_many([(posed(g, poses[name]), tex)], yaw=150, pitch=14, **lay))
        tiles.append(render_many([(g, tex)], yaw=150, pitch=14, darkness=0.2, **lay))
        w, h = lay["size"]
        s = Image.new("RGBA", (w * len(tiles), h + 22), (44, 56, 60, 255))
        for k, im in enumerate(tiles):
            s.alpha_composite(im, (w * k, 22))
        ImageDraw.Draw(s).text((6, 2), name + "  （斜め前・斜め後ろ・正面・横・技の形・暗い所）", fill=(235, 235, 220), font=sheet_font(15))
        sheets.append(s)
    W = max(s.size[0] for s in sheets)
    H = sum(s.size[1] for s in sheets)
    sheet = Image.new("RGBA", (W, H), (36, 44, 48, 255))
    y = 0
    for s in sheets:
        sheet.alpha_composite(s, (0, y))
        y += s.size[1]
    path = os.path.join(out_dir, "altar_models_check.png")
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
