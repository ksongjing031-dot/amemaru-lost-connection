"""3階「古代の水路」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（Phase 8 その2）。

  python3 tools/artgen/make_waterway_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の3階：地下の古代の水路網。水門、水車、青い結晶の灯。水没した区画もある。
作るもの（ファイルの名前は敵のIDと同じ。geo/entity・animations/entity・textures/entity）:
  suimon_nezumi   水門ネズミ：灰色の毛の大ネズミ。桃色の耳と長い尾、黄ばんだ前歯、赤く光る小さな目。背中に、かじった歯車のかけら
  yodomi_slime    澱みスライム：濁った緑がかった水のかたまり。体に沈んだ歯車と落ち葉が透けて見える。うつろな淡い目
  soujikihei      水路掃除機兵：緑青の浮いた青銅の清掃機械。正面に厚い装甲の板（リベット）、足もとに回るブラシの筒、
                  右肩に水の噴射の筒、頭はまるい覆いに青緑に光るひとつ目。足は2本のキャタピラ
  kiba_namazu     牙ナマズ：泥色の大ナマズ。平たい大きな頭、長いひげ4本、上あごの白い牙、淡い腹、背びれと尾びれ
  hydram          大水門の番人ハイドラム：石と青銅の大きな番人。右腕は水の大砲（青銅の砲身と輪、青く光る砲口）、
                  左腕は鍵の形の青銅の籠手をはめた石の拳。胸に青く光る炉心（青銅の枠）。水が満ちている間は、
                  胸を水の外套（ボーン mantle。水が引くと隠れる）がおおう。頭は兜の形で、目の細いすき間が光る。背中に水門の輪
どれもアニメーションは idle・walk・attack・death と、敵ごとの技（skill_…）。名前は Java の actionNames と同じ。
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import FOREST, ForestPainter, anims, check_sheet as _unused, loop, once, posed, sheet_font  # noqa: E402,F401
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

WATER = {
    # ネズミの毛（灰色）・腹（明るい灰）・足（暗い）・耳と尾（くすんだ桃色）
    "rat": ramp("#24221F", "#34312C", "#47433C", "#5B564D", "#706A5F", "#878074", "#A09A8D"),
    "rat_light": ramp("#4C4740", "#665F56", "#80786D", "#9A9285", "#B2AA9C", "#C8C1B3", "#DDD8CC"),
    "rat_dark": ramp("#141311", "#1E1C19", "#2A2723", "#37332E", "#454039", "#544E46", "#655E54"),
    "pink": ramp("#4D2A2A", "#683838", "#844848", "#9E5A58", "#B6706A", "#CB8A82", "#DEA79E"),
    # 澱んだ水（にごった緑がかった青）
    "murk": ramp("#0F1E1C", "#17302B", "#20433B", "#2B574C", "#386D5E", "#4B8573", "#66A08B"),
    # ナマズ（泥色の緑茶）・腹（淡い黄土）・ひれ（暗い茶）
    "catfish": ramp("#191A12", "#24251A", "#313223", "#40402D", "#504F38", "#636046", "#7A7558"),
    "catfish_belly": ramp("#4D473A", "#665F4C", "#80775F", "#998F73", "#B1A788", "#C7BE9F", "#DCD5B9"),
    "fin": ramp("#1E1812", "#2A221A", "#372D22", "#45392B", "#544636", "#655443", "#776552"),
    # 緑青の浮いた青銅（掃除機兵・番人）
    "bronze_teal": ramp("#13231F", "#1E3530", "#2A4A42", "#386057", "#4A786C", "#609384", "#7EB0A0"),
    # 正面の装甲の板（暗い青銅）
    "plate": ramp("#1E1A14", "#2C261D", "#3B3326", "#4C4231", "#5E523D", "#73654C", "#8B7B5E"),
    # 回るブラシ（暗い毛と、すり減った先）
    "brush": ramp("#0F0E0C", "#1A1815", "#26231F", "#34302A", "#443F37", "#575047", "#6C6459"),
    # 番人の石（青みの灰。積んだ石の目地）
    "stone_g": ramp("#26292B", "#33373A", "#42474A", "#52585B", "#646B6E", "#788083", "#90989A"),
    # 水の外套（青い水。白い泡の筋）
    "water_mantle": ramp("#103C66", "#16508A", "#1E66A8", "#2E7EC2", "#4A9AD6", "#78B8E6", "#B6DCF4"),
    # 光る：炉心（青白い）・水の砲口・ひとつ目（青緑）・ネズミの目（赤）・ナマズの目（黄）・スライムの目（淡い緑）
    "glow_core": ramp("#1E5FA8", "#2A7DCC", "#3F9CE6", "#6CBDF4", "#A0D8FA", "#D2EEFD", "#FFFFFF"),
    "glow_water": ramp("#1F7A8C", "#2A98AA", "#3FB6C6", "#6CD0DC", "#A0E4EC", "#D2F4F8", "#FFFFFF"),
    "glow_rat": ramp("#5A1010", "#7A1A16", "#9C2820", "#C0402E", "#DE6446", "#F09474", "#FCCBB4"),
    "glow_fish": ramp("#6A5A12", "#8E7A1C", "#B29C2A", "#D2BE48", "#EAD878", "#F8EEB0", "#FFFFFF"),
    "glow_murk": ramp("#3E6A4E", "#558A66", "#70A880", "#90C49C", "#B4DCBC", "#D6F0DC", "#FFFFFF"),
}
for _k, _v in WATER.items():
    if _k.startswith("glow_"):
        RAMPS[_k] = _v


class WaterPainter(ForestPainter):
    def ramp(self, name):
        return WATER.get(name) or FOREST.get(name) or RAMPS[name]

    # ---- 毛 ----
    def _fur(self, c, name, salt):
        strand = hsh(int(math.floor(c.p[0] * 2 + c.p[2])), int(math.floor(c.p[1] / 1.2)), salt)
        tone = 3.0 + (strand - 0.5) * 1.1 + self.bevel(c, up=0.8, down=0.9)
        if c.n[1] > 0.6:
            tone += 0.4
        elif c.n[1] < -0.6:
            tone -= 0.8
        return pick(WATER[name], tone)

    def paint_rat(self, c):
        if self.blob(c, 2.5, 301 + self.eid(c) % 2) < 0.15:
            return pick(WATER["rat"], 1.9)   # 毛のむら（暗い）
        return self._fur(c, "rat", 302)

    def paint_rat_light(self, c):
        return self._fur(c, "rat_light", 303)

    def paint_rat_dark(self, c):
        return self._fur(c, "rat_dark", 304)

    def paint_pink(self, c):
        tone = self.std(c, "pink", base=3.2, noise=0.3, blob=0.2, up=0.8, down=0.8)
        if c.el.tags.get("rings") and c.j % 2 == 0:
            tone -= 0.6   # 尾のうろこの輪
        return pick(WATER["pink"], tone)

    def paint_bone(self, c):
        tone = self.std(c, "bone", base=4.2, noise=0.15, blob=0.2, up=1.0, down=1.0)
        return pick(RAMPS["bone"], tone)

    # ---- 澱んだ水 ----
    def paint_murk(self, c):
        tone = 3.0 + (self.blob(c, 2.4, 311) - 0.5) * 1.4 + (self.tex(c, 312) - 0.5) * 0.3
        tone += self.bevel(c, up=1.1, down=1.0)
        if c.n[1] > 0.6:
            tone += 0.8   # 上の面は明るい（水面のつや）
            if self.tex(c, 313) > 0.92:
                return pick(WATER["water_mantle"], 5.6)   # つやの白い点
        d = c.edge_dist()
        # 沈んだ歯車（輪と歯）・落ち葉（茶色の斑）
        gx, gy = c.el.tags.get("gear", (None, None)) if c.face.d == "front" else (None, None)
        if gx is not None:
            r = math.hypot(c.p[0] - gx, c.p[1] - gy)
            if 1.4 < r < 2.6 or (r < 3.3 and r > 2.6 and int((math.atan2(c.p[1] - gy, c.p[0] - gx) + math.pi) * 3) % 2 == 0):
                return pick(WATER["bronze_teal"], 2.4 + (self.tex(c, 314) - 0.5))
        if self.blob(c, 1.6, 315) < 0.07:
            return pick(FOREST["husk"], 2.2 + (self.tex(c, 316) - 0.5))
        if min(d.values()) == 0 and self.tex(c, 317) < 0.3:
            tone -= 0.6
        return pick(WATER["murk"], tone)

    # ---- ナマズ ----
    def paint_catfish(self, c):
        tone = self.std(c, "catfish", base=3.0, noise=0.3, blob=0.6, blob_size=1.6, up=1.0, down=1.0)
        if c.n[1] > 0.6 and self.blob(c, 1.3, 321) < 0.25:
            tone += 1.0   # 背中のまだら（明るい）
        if self.tex(c, 322) < 0.05:
            tone -= 1.0
        return pick(WATER["catfish"], tone)

    def paint_catfish_belly(self, c):
        tone = self.std(c, "catfish_belly", base=3.4, noise=0.3, blob=0.3, up=0.6, down=0.8)
        return pick(WATER["catfish_belly"], tone)

    def paint_fin(self, c):
        tone = 3.0 + (0.7 if c.j % 2 == 0 else -0.3) + (self.tex(c, 331) - 0.5) * 0.4
        d = c.edge_dist()
        if d.get("top", 1) == 0 and c.i % 2 == 1 and c.face.d not in ("up", "down"):
            return None   # ひれのふちのぎざぎざ
        return pick(WATER["fin"], tone)

    # ---- 青銅・装甲・ブラシ ----
    def paint_bronze_teal(self, c):
        tone = self.std(c, "bronze_teal", base=3.1, noise=0.25, blob=0.5, up=1.4, down=1.0)
        tone += self.rivet(c)
        if self.blob(c, 1.4, 341 + self.eid(c)) < 0.16:
            return pick(RAMPS["bronze"], 3.4 + (self.tex(c, 342) - 0.5))   # 緑青の下の地金
        rings = c.el.tags.get("rings")
        if rings and abs(float(c.n[2])) < 0.6:
            k = {"x": 0, "y": 1, "z": 2}[rings]
            if int(math.floor(c.p[k])) % 4 == 0:
                tone += 1.0
        return pick(WATER["bronze_teal"], tone)

    def paint_plate(self, c):
        tone = self.std(c, "plate", base=3.0, noise=0.2, blob=0.4, up=1.6, down=1.2)
        tone += self.rivet(c)
        if self.facing(c):
            # 縦のすじ（打ち出しの板）
            if int(math.floor(c.p[0])) % 4 == 0:
                tone -= 0.5
            if self.tex(c, 351) < 0.04:
                tone += 1.6   # 傷の光り
        return pick(WATER["plate"], tone)

    def paint_brush(self, c):
        tone = 2.8 + (0.9 if (c.i + c.j * 2) % 3 == 0 else -0.2) + (self.tex(c, 361) - 0.5) * 0.6
        if c.face.d in ("front", "down"):
            tone += 0.8   # すり減った毛先
        return pick(WATER["brush"], tone)

    # ---- 石（番人） ----
    def paint_stone_g(self, c):
        tone = self.std(c, "stone_g", base=3.1, noise=0.3, blob=0.5, blob_size=2.5, up=1.3, down=1.1, specks=0.03)
        # 積んだ石の目地（4ピクセルの段、互いちがい）
        row = int(math.floor(c.p[1] / 4.0))
        along = c.p[0] if abs(float(c.n[0])) < 0.5 else c.p[2]
        if c.n[1] < 0.6 and c.n[1] > -0.6:
            if (c.p[1] % 4.0) < 0.6 or ((along + row * 3.0) % 7.0) < 0.6:
                tone -= 1.1
        if self.blob(c, 2.0, 371 + self.eid(c)) < 0.12:
            return pick(FOREST["moss"], 2.6 + (self.tex(c, 372) - 0.5))   # 湿った苔
        return pick(WATER["stone_g"], tone)

    def paint_water_mantle(self, c):
        # 流れ落ちる水：縦の筋が下へずれながら続き、ところどころ白い泡
        col = int(math.floor(c.p[0] + c.p[2] * 0.7))
        flow = (c.p[1] + hsh(col, 381) * 6.0) % 6.0
        tone = 3.2 + (0.9 if flow < 1.0 else -0.2) + (self.tex(c, 382) - 0.5) * 0.4
        if flow < 0.5 and self.tex(c, 383) < 0.5:
            tone = 5.4   # 泡
        tone += self.bevel(c, up=0.6, down=0.4)
        return pick(WATER["water_mantle"], tone)

    def paint_glow_core(self, c):
        tone = 4.0 + (self.tex(c, 391) - 0.5) * 0.4
        d = c.edge_dist()
        if min(d.values()) >= 1:
            tone += 1.2
        if min(d.values()) >= 3:
            tone += 1.0
        return pick(WATER["glow_core"], tone)

    def paint_glow_water(self, c):
        return self.paint_glow(c, "glow_water", base=4.2)

    def paint_glow_rat(self, c):
        return self.paint_glow(c, "glow_rat", base=4.2)

    def paint_glow_fish(self, c):
        return self.paint_glow(c, "glow_fish", base=4.4)

    def paint_glow_murk(self, c):
        return self.paint_glow(c, "glow_murk", base=4.2)

    def paint_glow_teal(self, c):
        return self.paint_glow(c, "glow_teal", base=4.3)


# ================================================================ 水門ネズミ

def build_suimon_nezumi():
    g = GeoModel("suimon_nezumi", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 4, 0), parent="root")
    g.bone("head", (0, 5, -5), parent="body")
    g.bone("ear_right", (-2, 8, -7), parent="head")
    g.bone("ear_left", (2, 8, -7), parent="head")
    g.bone("tail", (0, 5, 5), parent="body")
    g.bone("tail_tip", (0, 3.5, 10.5), parent="tail")
    for name, x, z in (("leg_front_right", -2, -3), ("leg_front_left", 2, -3), ("leg_back_right", -2, 3),
                       ("leg_back_left", 2, 3)):
        g.bone(name, (x, 2, z), parent="root")
        g.cube(name, name, (x - 1, 0, z - 1), (2, 2, 2), "rat_dark")
    g.cube("body", "body", (-3, 2, -5), (6, 5, 10), "rat")
    g.cube("body", "belly", (-2.5, 1.8, -4.5), (5, 1, 9), "rat_light")
    g.cube("body", "hump", (-2.5, 6.5, -2), (5, 1, 6), "rat")
    g.cube("body", "cog", (-1.5, 7, 0), (3, 1, 3), "bronze_teal")
    g.cube("head", "head", (-2.5, 3, -10), (5, 4, 5), "rat")
    g.cube("head", "snout", (-1.5, 3, -12.5), (3, 2, 3), "rat_light")
    g.cube("head", "nose", (-0.5, 4.8, -12.9), (1, 1, 1), "pink")
    g.cube("head", "teeth", (-1, 2.2, -12.6), (2, 1, 0), "bone")
    g.cube("head", "eye_r", (-2.6, 5.5, -10.1), (1, 1, 0), "glow_rat", glow=True)
    g.cube("head", "eye_l", (1.6, 5.5, -10.1), (1, 1, 0), "glow_rat", glow=True)
    g.cube("ear_right", "ear_r", (-3.5, 7, -8), (2, 2, 1), "pink")
    g.cube("ear_left", "ear_l", (1.5, 7, -8), (2, 2, 1), "pink")
    g.cube("tail", "tail", (-0.5, 4.5, 5), (1, 1, 6), "pink", rot=(-20, 0, 0), pivot=(0, 5, 5), tags={"rings": True})
    g.cube("tail_tip", "tail_tip", (-0.5, 3, 10.5), (1, 1, 6), "pink", rot=(-8, 0, 0), pivot=(0, 3.5, 10.5), tags={"rings": True})
    return g


def suimon_nezumi_anims():
    walk = {}
    for name, ph in (("leg_front_right", 1), ("leg_back_left", 1), ("leg_front_left", -1), ("leg_back_right", -1)):
        walk[name] = {"rotation": {0: [35 * ph, 0, 0], 0.15: [-35 * ph, 0, 0], 0.3: [35 * ph, 0, 0]}}
    walk["body"] = {"position": {0: [0, 0, 0], 0.075: [0, 0.4, 0], 0.15: [0, 0, 0], 0.225: [0, 0.4, 0], 0.3: [0, 0, 0]}}
    walk["tail"] = {"rotation": {0: [0, -15, 0], 0.15: [0, 15, 0], 0.3: [0, -15, 0]}}
    t = {
        "idle": loop(2.0, {
            "head": {"rotation": {0: [0, 0, 0], 0.2: [-6, 4, 0], 0.4: [0, 0, 0], 0.6: [-6, -4, 0], 0.8: [0, 0, 0], 2.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, -10, 0], 1.0: [0, 10, 0], 2.0: [0, -10, 0]}},
            "tail_tip": {"rotation": {0: [0, -10, 0], 1.0: [0, 10, 0], 2.0: [0, -10, 0]}},
            "ear_right": {"rotation": {0: [0, 0, 0], 1.4: [0, 0, 0], 1.5: [0, 0, -20], 1.6: [0, 0, 0]}},
        }),
        "walk": loop(0.3, walk),
        "attack": once(0.6, {
            "body": {"position": {0: [0, 0, 0], 0.25: [0, -0.5, 1.5], 0.35: [0, 0.4, -2.5], 0.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.25: [-20, 0, 0], 0.35: [20, 0, 0], 0.6: [0, 0, 0]}},
        }),
        # 飛びかかり（低く伏せてから、前へ）
        "skill_charge": once(1.2, {
            "body": {"position": {0: [0, 0, 0], 0.6: [0, -1, 1.5], 0.7: [0, 0.5, -3], 1.2: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.6: [8, 0, 0], 0.7: [-10, 0, 0], 1.2: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.6: [30, 0, 0], 1.2: [0, 0, 0]}},
        }),
        # 子分を呼ぶ（後ろ足で立って鳴く）
        "skill_call": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 0.3: [-45, 0, 0], 0.9: [-45, 0, 0], 1.2: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 0.3: [0, 2, 2], 0.9: [0, 2, 2], 1.2: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.4: [-25, 0, 0], 0.6: [-35, 0, 0], 0.8: [-25, 0, 0], 1.2: [0, 0, 0]}},
        }),
        "death": once(1.0, {
            "root": {"rotation": {0: [0, 0, 0], 0.5: [0, 0, 180]}, "position": {0: [0, 0, 0], 0.5: [0, 4, 0]}},
            "leg_front_right": {"rotation": {0: [0, 0, 0], 0.6: [-40, 0, 0]}},
            "leg_back_left": {"rotation": {0: [0, 0, 0], 0.6: [40, 0, 0]}},
        }, hold=True),
    }
    return anims("suimon_nezumi", t)


# ================================================================ 澱みスライム

def build_yodomi_slime():
    g = GeoModel("yodomi_slime", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 0, 0), parent="root")
    g.bone("top", (0, 14, 0), parent="body")
    g.bone("face", (0, 8, -9), parent="body")
    g.cube("body", "base", (-9, 0, -9), (18, 14, 18), "murk", tags={"gear": (3.5, 5.0)})
    g.cube("top", "dome", (-7, 14, -7), (14, 3, 14), "murk")
    g.cube("top", "dome_top", (-4.5, 17, -4.5), (9, 1, 9), "murk")
    g.cube("body", "drip_r", (-9.5, 1, -3), (1, 4, 3), "murk")
    g.cube("body", "drip_l", (8.5, 0, 2), (1, 5, 2), "murk")
    g.cube("face", "eye_r", (-5, 8, -9.15), (3, 3, 0), "glow_murk", glow=True)
    g.cube("face", "eye_l", (2, 8, -9.15), (3, 3, 0), "glow_murk", glow=True)
    g.cube("face", "mouth", (-3, 4, -9.15), (6, 1, 0), "dark")
    return g


def yodomi_slime_anims():
    t = {
        "idle": loop(2.4, {
            "body": {"scale": {0: [1, 1, 1], 1.2: [1.04, 0.95, 1.04], 2.4: [1, 1, 1]}},
            "top": {"position": {0: [0, 0, 0], 1.2: [0, -0.6, 0], 2.4: [0, 0, 0]}},
        }),
        "walk": loop(0.8, {
            "body": {"scale": {0: [1.1, 0.88, 1.1], 0.3: [0.92, 1.12, 0.92], 0.5: [0.98, 1.02, 0.98], 0.8: [1.1, 0.88, 1.1]},
                     "position": {0: [0, 0, 0], 0.3: [0, 3, 0], 0.5: [0, 2, 0], 0.8: [0, 0, 0]}},
        }),
        "attack": once(0.8, {
            "body": {"scale": {0: [1, 1, 1], 0.4: [1.15, 0.85, 0.9], 0.55: [0.9, 1.05, 1.25], 0.8: [1, 1, 1]},
                     "position": {0: [0, 0, 0], 0.4: [0, 0, 1], 0.55: [0, 0, -3], 0.8: [0, 0, 0]}},
        }),
        # 跳ねて押しつぶす（予兆1.1秒：ちぢんで、高く跳び、つぶれる）
        "skill_slam": once(1.6, {
            "body": {"scale": {0: [1, 1, 1], 0.6: [1.25, 0.7, 1.25], 0.8: [0.85, 1.2, 0.85], 1.1: [0.9, 1.1, 0.9], 1.2: [1.45, 0.55, 1.45],
                               1.6: [1, 1, 1]},
                     "position": {0: [0, 0, 0], 0.6: [0, 0, 0], 0.9: [0, 14, 0], 1.1: [0, 8, 0], 1.2: [0, 0, 0], 1.6: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "body": {"scale": {0: [1, 1, 1], 0.8: [1.5, 0.2, 1.5]}},
        }, hold=True),
    }
    return anims("yodomi_slime", t)


# ================================================================ 水路掃除機兵

def build_soujikihei():
    g = GeoModel("soujikihei", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 6, 0), parent="root")
    g.bone("head", (0, 21, 0), parent="body")
    g.bone("plate", (0, 9, -6), parent="body")
    g.bone("brush", (0, 3, -9), parent="body")
    g.bone("nozzle", (-7, 17, 0), parent="body")
    g.bone("track_right", (-5.5, 2, 0), parent="root")
    g.bone("track_left", (5.5, 2, 0), parent="root")
    # キャタピラ
    for bone, x0 in (("track_right", -8), ("track_left", 3)):
        g.cube(bone, bone, (x0, 0, -6), (5, 4, 12), "darksteel")
        g.cube(bone, bone + "_wheel_f", (x0 + 0.5, 0.5, -5.5), (4, 3, 2), "bronze")
        g.cube(bone, bone + "_wheel_b", (x0 + 0.5, 0.5, 3.5), (4, 3, 2), "bronze")
    # 胴（緑青の青銅）・背中の水の筒
    g.cube("body", "base", (-6, 4, -5), (12, 3, 10), "bronze_teal")
    g.cube("body", "torso", (-5.5, 7, -4.5), (11, 13, 9), "bronze_teal", tags={"rivets": [(-4, 18), (4, 18), (-4, 9), (4, 9)]})
    g.cube("body", "tank", (-3, 9, 4.5), (6, 10, 3), "pipe", tags={"axis": "y"})
    g.cube("body", "tank_cap", (-2, 19, 5), (4, 1, 2), "bronze")
    g.cube("body", "collar", (-5, 20, -4), (10, 1, 8), "bronze")
    # 正面の装甲の板（厚い。リベット）
    g.cube("plate", "plate", (-7, 6, -7), (14, 13, 2), "plate", tags={"rivets": [(-5.5, 17.5), (5.5, 17.5), (-5.5, 7.5), (5.5, 7.5)]})
    g.cube("plate", "plate_top", (-6, 19, -7), (12, 1, 2), "plate")
    g.cube("plate", "plate_slit", (-4, 14, -7.2), (8, 1, 0), "dark")
    # 頭：まるい覆いと、青緑に光るひとつ目
    g.cube("head", "dome", (-4, 21, -4), (8, 4, 8), "bronze_teal")
    g.cube("head", "dome_top", (-2.5, 25, -2.5), (5, 1, 5), "bronze_teal")
    g.cube("head", "eye_ring", (-2, 21.5, -4.6), (4, 3, 1), "bronze")
    g.cube("head", "eye", (-1, 22, -4.8), (2, 2, 0), "glow_teal", glow=True)
    g.cube("head", "antenna", (2, 26, -0.5), (1, 3, 1), "bronze")
    # 回るブラシの筒（足もとの前）と腕
    g.cube("brush", "brush", (-7, 1, -11), (14, 4, 4), "brush")
    g.cube("brush", "brush_axle", (-8, 2.5, -9.5), (16, 1, 1), "bronze")
    g.cube("brush", "brush_arm_r", (-8, 3, -9), (1, 4, 3), "bronze_teal")
    g.cube("brush", "brush_arm_l", (7, 3, -9), (1, 4, 3), "bronze_teal")
    # 右肩の水の噴射の筒
    g.cube("nozzle", "shoulder", (-9, 15, -2.5), (3, 4, 5), "bronze_teal")
    g.cube("nozzle", "barrel", (-9.5, 15.5, -8), (3, 3, 6), "pipe", tags={"axis": "z"})
    g.cube("nozzle", "muzzle", (-9, 16, -8.4), (2, 2, 0), "glow_water", glow=True)
    return g


def soujikihei_anims():
    t = {
        "idle": loop(2.0, {
            "head": {"rotation": {0: [0, 0, 0], 0.6: [0, 25, 0], 1.2: [0, -25, 0], 2.0: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 1.0: [0, 0.3, 0], 2.0: [0, 0, 0]}},
        }),
        "walk": loop(0.8, {
            "brush": {"rotation": {0: [0, 0, 0], 0.8: [-360, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.2: [0, 0.3, 0], 0.4: [0, 0, 0], 0.6: [0, 0.3, 0], 0.8: [0, 0, 0]},
                     "rotation": {0: [0, -2, 0], 0.4: [0, 2, 0], 0.8: [0, -2, 0]}},
        }),
        # ブラシの薙ぎ払い（体をひねって、回るブラシで前を払う）
        "attack": once(1.2, {
            "body": {"rotation": {0: [0, 0, 0], 0.7: [0, 30, 0], 0.85: [0, -35, 0], 1.2: [0, 0, 0]}},
            "brush": {"rotation": {0: [0, 0, 0], 0.7: [-360, 0, 0], 1.2: [-1080, 0, 0]}},
        }),
        # 水の噴射（肩の筒を前へ向けて、ためて、反動で下がる）
        "skill_jet": once(1.8, {
            "nozzle": {"rotation": {0: [0, 0, 0], 0.4: [-8, 0, 0], 1.2: [-8, 0, 0], 1.3: [10, 0, 0], 1.8: [0, 0, 0]},
                       "position": {0: [0, 0, 0], 1.2: [0, 0, 0], 1.3: [0, 0, 1.5], 1.8: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 1.2: [0, -0.5, 0], 1.3: [0, 0, 1], 1.8: [0, 0, 0]}},
        }),
        "death": once(1.6, {
            "root": {"rotation": {0: [0, 0, 0], 0.4: [-6, 0, 0], 1.0: [85, 0, 0]}, "position": {0: [0, 0, 0], 1.0: [0, 0, -4]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [0, 40, 0]}},
        }, hold=True),
    }
    return anims("soujikihei", t)


# ================================================================ 牙ナマズ

def build_kiba_namazu():
    g = GeoModel("kiba_namazu", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 5, 0), parent="root")
    g.bone("head", (0, 5, -8), parent="body")
    g.bone("jaw", (0, 3, -9), parent="head")
    g.bone("whisker_right", (-5, 4, -15), parent="head")
    g.bone("whisker_left", (5, 4, -15), parent="head")
    g.bone("tail", (0, 5, 8), parent="body")
    g.bone("tail_fin", (0, 5, 15), parent="tail")
    g.bone("fin_right", (-5, 3, -5), parent="body")
    g.bone("fin_left", (5, 3, -5), parent="body")
    g.cube("body", "body", (-5, 1, -8), (10, 8, 16), "catfish")
    g.cube("body", "belly", (-4.5, 0.8, -7), (9, 1, 14), "catfish_belly")
    g.cube("body", "dorsal", (-0.5, 9, -4), (1, 3, 7), "fin")
    g.cube("head", "head", (-6.5, 1, -16), (13, 7, 8), "catfish")
    g.cube("head", "brow", (-6, 8, -15), (12, 1, 6), "catfish")
    g.cube("jaw", "jaw", (-6, 0, -16.5), (12, 2, 8), "catfish_belly")
    for k, x in enumerate((-4.5, -2, 1, 3.5)):
        g.cube("head", f"fang_{k}", (x, 0, -16.2), (1, 2, 1), "bone")
    g.cube("head", "eye_r", (-6.6, 5, -13), (0, 2, 2), "glow_fish", glow=True)
    g.cube("head", "eye_l", (6.6, 5, -13), (0, 2, 2), "glow_fish", glow=True)
    g.cube("whisker_right", "whisker_r1", (-13, 3.5, -15.5), (8, 1, 1), "catfish", rot=(0, -20, -10), pivot=(-5, 4, -15))
    g.cube("whisker_right", "whisker_r2", (-10, 1.5, -15), (5, 1, 1), "catfish", rot=(0, -35, 15), pivot=(-5, 2, -15))
    g.cube("whisker_left", "whisker_l1", (5, 3.5, -15.5), (8, 1, 1), "catfish", rot=(0, 20, 10), pivot=(5, 4, -15))
    g.cube("whisker_left", "whisker_l2", (5, 1.5, -15), (5, 1, 1), "catfish", rot=(0, 35, -15), pivot=(5, 2, -15))
    g.cube("fin_right", "fin_r", (-9, 2, -6), (4, 1, 4), "fin", rot=(0, 0, 20), pivot=(-5, 3, -5))
    g.cube("fin_left", "fin_l", (5, 2, -6), (4, 1, 4), "fin", rot=(0, 0, -20), pivot=(5, 3, -5))
    g.cube("tail", "tail", (-3.5, 2, 8), (7, 6, 7), "catfish")
    g.cube("tail_fin", "tail_fin", (-0.5, -1, 14), (1, 12, 7), "fin")
    return g


def kiba_namazu_anims():
    swim = {
        "body": {"rotation": {0: [0, -6, 0], 0.6: [0, 6, 0], 1.2: [0, -6, 0]}},
        "tail": {"rotation": {0: [0, 18, 0], 0.6: [0, -18, 0], 1.2: [0, 18, 0]}},
        "tail_fin": {"rotation": {0: [0, 20, 0], 0.6: [0, -20, 0], 1.2: [0, 20, 0]}},
        "fin_right": {"rotation": {0: [0, 0, 10], 0.6: [0, 0, -10], 1.2: [0, 0, 10]}},
        "fin_left": {"rotation": {0: [0, 0, -10], 0.6: [0, 0, 10], 1.2: [0, 0, -10]}},
    }
    t = {
        "idle": loop(3.0, {
            "body": {"position": {0: [0, 0, 0], 1.5: [0, 0.5, 0], 3.0: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 8, 0], 1.5: [0, -8, 0], 3.0: [0, 8, 0]}},
            "whisker_right": {"rotation": {0: [0, 0, 0], 1.5: [0, -8, 6], 3.0: [0, 0, 0]}},
            "whisker_left": {"rotation": {0: [0, 0, 0], 1.5: [0, 8, -6], 3.0: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 0], 2.3: [8, 0, 0], 2.6: [0, 0, 0]}},
        }),
        "walk": loop(1.2, swim),
        "attack": once(0.6, {
            "jaw": {"rotation": {0: [0, 0, 0], 0.25: [30, 0, 0], 0.35: [0, 0, 0], 0.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.25: [-15, 0, 0], 0.35: [10, 0, 0], 0.6: [0, 0, 0]}},
        }),
        # 跳ね上がり（体を反らせて口を開け、跳ぶ）
        "skill_leap": once(2.2, {
            "body": {"rotation": {0: [0, 0, 0], 0.8: [20, 0, 0], 1.0: [-30, 0, 0], 1.6: [25, 0, 0], 2.2: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.8: [35, 0, 0], 1.3: [35, 0, 0], 1.4: [0, 0, 0], 2.2: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.8: [-25, 0, 0], 1.0: [30, 0, 0], 2.2: [0, 0, 0]}},
        }),
        # 陸でもがく
        "skill_flop": once(2.5, {
            "root": {"rotation": {0: [0, 0, 0], 0.2: [0, 0, 70], 0.5: [0, 0, 80], 0.8: [0, 0, 60], 1.1: [0, 0, 85], 1.4: [0, 0, 65],
                                  1.7: [0, 0, 80], 2.5: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.3: [0, 30, 0], 0.6: [0, -30, 0], 0.9: [0, 30, 0], 1.2: [0, -30, 0], 2.5: [0, 0, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.4: [20, 0, 0], 0.8: [0, 0, 0], 1.2: [20, 0, 0], 2.5: [0, 0, 0]}},
        }),
        "death": once(1.4, {
            "root": {"rotation": {0: [0, 0, 0], 0.8: [0, 0, 180]}, "position": {0: [0, 0, 0], 0.8: [0, 8, 0]}},
            "jaw": {"rotation": {0: [0, 0, 0], 0.8: [25, 0, 0]}},
        }, hold=True),
    }
    return anims("kiba_namazu", t)


# ================================================================ 大水門の番人ハイドラム

def build_hydram():
    g = GeoModel("hydram", 256, 256)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 44, 0), parent="root")
    g.bone("torso", (0, 50, 0), parent="body")
    g.bone("head", (0, 84, 0), parent="torso")
    g.bone("mantle", (0, 68, -13), parent="torso")
    g.bone("core", (0, 69, -12), parent="torso")
    g.bone("arm_right", (-22, 80, 0), parent="torso")
    g.bone("cannon", (-23, 56, 0), parent="arm_right")
    g.bone("arm_left", (22, 80, 0), parent="torso")
    g.bone("fist", (23, 44, 0), parent="arm_left")
    g.bone("wheel", (0, 70, 13), parent="torso")
    g.bone("leg_right", (-9, 44, 0), parent="root")
    g.bone("leg_left", (9, 44, 0), parent="root")
    # 足：石の柱と、青銅の足
    for bone, x in (("leg_right", -9), ("leg_left", 9)):
        g.cube(bone, bone, (x - 6, 5, -6), (12, 39, 12), "stone_g")
        g.cube(bone, bone + "_knee", (x - 6.5, 22, -6.5), (13, 4, 13), "bronze_teal", tags={"rivets": [(x - 4, 24), (x + 4, 24)]})
        g.cube(bone, bone + "_foot", (x - 7, 0, -9), (14, 5, 16), "bronze_teal")
    # 腰と胴
    g.cube("body", "waist", (-15, 40, -9), (30, 10, 18), "stone_g")
    g.cube("body", "belt", (-16, 44, -10), (32, 3, 20), "bronze_teal", tags={"rivets": [(-12, 45.5), (0, 45.5), (12, 45.5)]})
    g.cube("torso", "torso", (-19, 50, -12), (38, 34, 24), "stone_g")
    g.cube("torso", "chest_band", (-19.5, 77, -12.5), (39, 3, 25), "bronze_teal")
    # 胸の炉心（青銅の枠）。水の外套（mantle）がその上をおおう
    g.cube("core", "core_frame", (-7, 62, -13.5), (14, 14, 2), "bronze_teal", tags={"rivets": [(-5.5, 74.5), (5.5, 74.5), (-5.5, 63.5),
                                                                                                    (5.5, 63.5)]})
    g.cube("core", "core", (-5, 64, -14), (10, 10, 1), "glow_core", glow=True)
    g.cube("mantle", "mantle", (-11, 56, -16), (22, 26, 2), "water_mantle")
    g.cube("mantle", "mantle_top", (-12, 80, -16.5), (24, 3, 3), "water_mantle")
    # 肩
    g.cube("arm_right", "shoulder_r", (-28, 74, -10), (10, 12, 20), "stone_g")
    g.cube("arm_right", "shoulder_r_cap", (-29, 84, -9), (11, 3, 18), "bronze_teal")
    g.cube("arm_left", "shoulder_l", (18, 74, -10), (10, 12, 20), "stone_g")
    g.cube("arm_left", "shoulder_l_cap", (18, 84, -9), (11, 3, 18), "bronze_teal")
    # 右腕：水の大砲（前へ向いた青銅の砲身と輪。青く光る砲口）
    g.cube("arm_right", "upper_r", (-28, 60, -7), (10, 16, 14), "stone_g")
    g.cube("cannon", "barrel", (-29, 50, -24), (12, 12, 30), "bronze_teal", tags={"rings": "z"})
    g.cube("cannon", "barrel_ring_f", (-30, 49, -22), (14, 14, 3), "bronze")
    g.cube("cannon", "barrel_ring_b", (-30, 49, 2), (14, 14, 3), "bronze")
    g.cube("cannon", "muzzle", (-27, 52, -24.5), (8, 8, 0), "glow_water", glow=True)
    g.cube("cannon", "pipe_r", (-21, 60, -6), (2, 8, 2), "pipe", tags={"axis": "y"})
    # 左腕：石の拳に、鍵の形の青銅の籠手
    g.cube("arm_left", "upper_l", (18, 58, -7), (10, 18, 14), "stone_g")
    g.cube("fist", "fore_l", (18.5, 40, -7.5), (11, 18, 15), "stone_g")
    g.cube("fist", "gauntlet", (17.5, 44, -8.5), (13, 8, 17), "bronze_teal", tags={"rivets": [(20, 48), (28, 48)]})
    g.cube("fist", "fist", (18, 30, -8), (12, 10, 16), "stone_g")
    g.cube("fist", "key_bit", (29.5, 33, -3), (3, 5, 6), "bronze")
    # 頭：兜の形。目の細いすき間が光る。てっぺんに青銅のとさか
    g.cube("head", "head", (-9, 84, -9), (18, 14, 18), "stone_g")
    g.cube("head", "visor", (-9.5, 89, -9.5), (19, 4, 2), "bronze_teal")
    g.cube("head", "eyes", (-7, 90, -9.7), (14, 2, 0), "glow_teal", glow=True)
    g.cube("head", "crest", (-2, 98, -10), (4, 4, 20), "bronze_teal")
    g.cube("head", "jaw_plate", (-7, 84, -10), (14, 3, 2), "bronze_teal")
    # 背中の水門の輪
    g.cube("wheel", "wheel_rim_t", (-10, 78, 12), (20, 2, 3), "bronze_teal")
    g.cube("wheel", "wheel_rim_b", (-10, 60, 12), (20, 2, 3), "bronze_teal")
    g.cube("wheel", "wheel_rim_r", (-10, 62, 12), (2, 16, 3), "bronze_teal")
    g.cube("wheel", "wheel_rim_l", (8, 62, 12), (2, 16, 3), "bronze_teal")
    g.cube("wheel", "wheel_spoke_v", (-1, 62, 12.5), (2, 16, 2), "bronze")
    g.cube("wheel", "wheel_spoke_h", (-8, 69, 12.5), (16, 2, 2), "bronze")
    return g


def hydram_anims():
    t = {
        "idle": loop(4.0, {
            "torso": {"rotation": {0: [0, 0, 0], 2.0: [2, 0, 0], 4.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.3: [0, 10, 0], 2.7: [0, -10, 0], 4.0: [0, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 2.0: [-3, 0, 2], 4.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 2.0: [-3, 0, -2], 4.0: [0, 0, 0]}},
            "wheel": {"rotation": {0: [0, 0, 0], 4.0: [0, 0, 30]}},
        }),
        "walk": loop(2.0, {
            "leg_right": {"rotation": {0: [18, 0, 0], 1.0: [-18, 0, 0], 2.0: [18, 0, 0]}},
            "leg_left": {"rotation": {0: [-18, 0, 0], 1.0: [18, 0, 0], 2.0: [-18, 0, 0]}},
            "arm_left": {"rotation": {0: [10, 0, 0], 1.0: [-10, 0, 0], 2.0: [10, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.5: [0, -1.5, 0], 1.0: [0, 0, 0], 1.5: [0, -1.5, 0], 2.0: [0, 0, 0]},
                     "rotation": {0: [0, -3, 0], 1.0: [0, 3, 0], 2.0: [0, -3, 0]}},
        }),
        "attack": once(1.4, {
            "arm_left": {"rotation": {0: [0, 0, 0], 0.8: [-60, -30, -20], 1.0: [40, 20, 10], 1.4: [0, 0, 0]}},
            "torso": {"rotation": {0: [0, 0, 0], 0.8: [0, -20, 0], 1.0: [0, 20, 0], 1.4: [0, 0, 0]}},
        }),
        # 水の大砲（右腕を前へ向けて、ためて、撃った反動で下がる）
        "skill_cannon": once(2.2, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.5: [-12, 6, 0], 1.6: [-12, 6, 0], 1.7: [10, 0, 0], 2.2: [0, 0, 0]}},
            "torso": {"rotation": {0: [0, 0, 0], 0.5: [0, 12, 0], 1.6: [0, 12, 0], 1.7: [-6, 12, 0], 2.2: [0, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 1.6: [0, 0, 0], 1.7: [0, 0, 3], 2.2: [0, 0, 0]}},
        }),
        # 水の薙ぎ払い（左の拳を大きく振る）
        "skill_sweep": once(1.8, {
            "arm_left": {"rotation": {0: [0, 0, 0], 1.0: [-40, -70, -50], 1.1: [-45, -72, -52], 1.25: [-30, 60, -20], 1.8: [0, 0, 0]}},
            "torso": {"rotation": {0: [0, 0, 0], 1.0: [0, -35, 0], 1.25: [0, 35, 0], 1.8: [0, 0, 0]}},
        }),
        # 渦（両腕を上げて、水を呼ぶ）
        "skill_whirl": once(2.4, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.8: [-140, 0, -20], 1.8: [-140, 0, -20], 2.4: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.8: [-140, 0, 20], 1.8: [-140, 0, 20], 2.4: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.8: [-20, 0, 0], 1.8: [-20, 0, 0], 2.4: [0, 0, 0]}},
            "wheel": {"rotation": {0: [0, 0, 0], 2.4: [0, 0, 360]}},
        }),
        # 水門ネズミを呼ぶ（拳で地面を打つ）
        "skill_call": once(1.8, {
            "arm_left": {"rotation": {0: [0, 0, 0], 0.7: [-150, 0, 0], 0.9: [-20, 0, 0], 1.8: [0, 0, 0]}},
            "torso": {"rotation": {0: [0, 0, 0], 0.7: [-10, 0, 0], 0.9: [18, 0, 0], 1.8: [0, 0, 0]}},
        }),
        # よろめく（水が引いた：片ひざをつく）
        "skill_stagger": once(3.0, {
            "body": {"position": {0: [0, 0, 0], 0.4: [0, -10, 0], 2.6: [0, -10, 0], 3.0: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.4: [14, 0, -4], 2.6: [14, 0, -4], 3.0: [0, 0, 0]}},
            "leg_right": {"rotation": {0: [0, 0, 0], 0.4: [-70, 0, 0], 2.6: [-70, 0, 0], 3.0: [0, 0, 0]}},
            "leg_left": {"rotation": {0: [0, 0, 0], 0.4: [40, 0, 0], 2.6: [40, 0, 0], 3.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 0.4: [-30, 0, 10], 2.6: [-30, 0, 10], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.4: [25, 0, 0], 2.6: [25, 0, 0], 3.0: [0, 0, 0]}},
        }),
        "death": once(2.4, {
            "body": {"position": {0: [0, 0, 0], 0.8: [0, -12, 0], 1.8: [0, -30, 4]},
                     "rotation": {0: [0, 0, 0], 0.8: [10, 0, 0], 1.8: [70, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.8: [30, 0, 0]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 1.8: [-40, 0, -30]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.8: [-40, 0, 30]}},
        }, hold=True),
    }
    return anims("hydram", t)


MODELS = [(build_suimon_nezumi, suimon_nezumi_anims), (build_yodomi_slime, yodomi_slime_anims),
          (build_soujikihei, soujikihei_anims), (build_kiba_namazu, kiba_namazu_anims), (build_hydram, hydram_anims)]


def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(WaterPainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def check_sheet(models, out_dir):
    from PIL import Image, ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "suimon_nezumi": dict(scale=16, center=(0, 5, -2), size=(300, 240)),
        "yodomi_slime": dict(scale=10, center=(0, 9, 0), size=(300, 280)),
        "soujikihei": dict(scale=8.5, center=(0, 13, 0), size=(300, 320)),
        "kiba_namazu": dict(scale=8.5, center=(0, 5, 0), size=(340, 240)),
        "hydram": dict(scale=2.9, center=(0, 52, 0), size=(420, 440)),
    }
    poses = {
        "suimon_nezumi": {"body": ((-45, 0, 0), 1.0, (0, 2, 2)), "head": ((-25, 0, 0),)},
        "yodomi_slime": {"body": ((0, 0, 0), 1.0, (0, 0, 0)), "top": ((0, 0, 0), 1.0, (0, -1, 0))},
        "soujikihei": {"nozzle": ((-8, 0, 0),), "body": ((0, 30, 0),)},
        "kiba_namazu": {"body": ((-30, 0, 0),), "jaw": ((35, 0, 0),)},
        "hydram": {"arm_right": ((-12, 6, 0),), "arm_left": ((-140, 0, 20),), "mantle": ((0, 0, 0), 0.0)},
    }
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
    path = os.path.join(out_dir, "waterway_models_check.png")
    sheet.save(path)
    tex_tiles = [Image.fromarray(tex, "RGBA") for _, (g, tex) in models.items()]
    tw = sum(t.size[0] for t in tex_tiles) + 10 * len(tex_tiles)
    th = max(t.size[1] for t in tex_tiles)
    ts = Image.new("RGBA", (tw, th), (90, 90, 90, 255))
    x = 0
    for t in tex_tiles:
        ts.alpha_composite(t, (x, 0))
        x += t.size[0] + 10
    ts.save(os.path.join(out_dir, "waterway_textures_check.png"))
    return path


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models = build_all(repo)
    if len(sys.argv) > 2:
        print(check_sheet(models, sys.argv[2]))
    print("models written:", ", ".join(models))


if __name__ == "__main__":
    main()
