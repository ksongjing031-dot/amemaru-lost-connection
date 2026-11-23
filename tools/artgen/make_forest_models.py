"""2階「忘れられた森」の敵とボスのモデル（GeckoLib）・アニメーション・絵を作る（Phase 8 その1）。

  python3 tools/artgen/make_forest_models.py <リポジトリ> [確認画像の出力先]

Phase 0-A 9章の2階：古代の金属の管に根を巻きつけた巨木の森。霧が濃く、光るキノコと風鈴のような古代の装置がある。
作るもの（ファイルの名前は敵のIDと同じ。geo/entity・animations/entity・textures/entity）:
  mayoi_bi        迷い灯：道から外れた方へ誘う光の玉。4枚の花びらのような殻と、うしろへたなびく光の尾
  juhihei         樹皮兵：古代の植樹管理人形。樹皮の板を古代の青銅の骨組みに張った人形。苗を植える鋤の腕と、
                  熊手の腕。背中に種の筒。頭に小さな若葉。目は琥珀色のすき間
  pokodake        ポコダケ：赤いかさに白い斑点の大きなキノコ。かさの裏のひだが淡い緑に光る
  hakage_kitsune  葉影ギツネ：葉の影の色の毛のキツネ。尾の先が葉の形にふさふさ分かれる。目は黄緑に光る
  old_ward        森番の古樹オールドウォード：古代の管に根を巻きつけた大木。幹の顔（光る目と口の割れ目）、
                  枝の腕（右の枝に森番の灯をさげる）、苔とつる。幹に光る節の受け口が3つ
  old_ward_knot   古樹の光る節：樹皮の帯で縛った、淡い青緑に光るこぶ（ボスの幹にくっつく弱点）

どれもアニメーションは idle・walk・attack・death と、敵ごとの技（skill_…）。名前は Java の actionNames と同じ。
"""
import json
import math
import os
import sys
from types import SimpleNamespace

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from javamodel import rot_matrix  # noqa: E402
from materials import Painter, RAMPS, hexc, hsh, mix, pick, ramp  # noqa: E402

# ---------------------------------------------------------------- 森の色（暗い → 明るい、7段）

FOREST = {
    # 古い樹皮（灰色がかった焦げ茶）
    "bark": ramp("#221C17", "#33291F", "#463A2C", "#5A4B39", "#705F48", "#887659", "#A3916F"),
    # 苔
    "moss": ramp("#1F2E19", "#2B3F1F", "#385225", "#47672D", "#587E36", "#6F9843", "#8CB355"),
    # 葉（濃い緑）
    "leaf": ramp("#14261A", "#1C3420", "#254528", "#2F5831", "#3B6C39", "#4C8444", "#64A055"),
    # 古代の管（青銅に緑青）
    "pipe": ramp("#1B2522", "#273632", "#344A43", "#456157", "#5A7B6E", "#779A89", "#9EBDAA"),
    # 彫った木（樹皮兵の頭）
    "carved": ramp("#3A2A1A", "#533C25", "#6E5232", "#896841", "#A48153", "#BE9C6B", "#D6B98A"),
    # キノコのかさ（赤）
    "cap": ramp("#3E0C0A", "#5E140F", "#821D16", "#A2281D", "#BE3A28", "#D65A3E", "#E8815E"),
    # キノコの柄・斑点（生成り）
    "stem": ramp("#5E5646", "#7C735F", "#9A917A", "#B7AE95", "#CFC7AE", "#E2DCC6", "#F3EFE0"),
    # 葉影ギツネの毛（葉の影の色：緑がかった灰）
    "fox": ramp("#161D16", "#1F291F", "#293628", "#344534", "#425741", "#536B50", "#698563"),
    # キツネの胸・口もと（淡い）
    "fox_light": ramp("#4E4C3E", "#6B6854", "#8A866C", "#A8A386", "#C3BEA0", "#D9D5BA", "#ECE9D6"),
    # キツネの尾の先（若葉）
    "fox_leaf": ramp("#243F1C", "#2F5323", "#3C682B", "#4C7F35", "#5F9840", "#79B150", "#98C968"),
    # 迷い灯の殻（葉脈だけ残ったほおずきの萼：くすんだ金茶）
    "husk": ramp("#4A3519", "#664A24", "#836131", "#A07A42", "#BA9558", "#D2B27A", "#E8D2A4"),
    # 暗い穴（目のくぼみ・口・節の受け口）
    "hollow": ramp("#0A0806", "#120E0A", "#1A140E", "#231B13", "#2C2318", "#362B1E", "#403324"),
    # 光る：迷い灯（あたたかい白金）
    "glow_lure": ramp("#7A5A12", "#A57D1E", "#CFA232", "#EDC75A", "#FBE38E", "#FFF3C8", "#FFFFFF"),
    # 光る：古樹の節・森の糸（淡い青緑）
    "glow_knot": ramp("#2C7A66", "#3DA085", "#58C3A3", "#82DFC0", "#B1F1DA", "#DBFCEF", "#FFFFFF"),
    # 光る：古樹の目・森番の灯（淡い黄緑の光）
    "glow_eye": ramp("#6A761C", "#909F28", "#B6C63B", "#D8E461", "#EFF697", "#FAFDCF", "#FFFFFF"),
    # 光る：キノコのひだ（淡い緑）
    "glow_gill": ramp("#335F32", "#467E43", "#5F9E58", "#7EBD73", "#A3D894", "#CBEEC0", "#F0FFEC"),
    # 光る：琥珀色（樹皮兵の目）
    "glow_amber": RAMPS["glow_amber"],
}


class ForestPainter(Painter):
    def ramp(self, name):
        return FOREST.get(name) or RAMPS[name]

    # ---- 樹皮：縦の割れ目。上を向いた面と北側に苔 ----
    def paint_bark(self, c):
        side = abs(float(c.n[1])) < 0.5
        tone = 3.0 + (self.tex(c, 11) - 0.5) * 0.35 + (self.blob(c, 3.0, 12) - 0.5) * 0.5
        tone += self.bevel(c, up=0.9, down=0.9)
        if side:
            # 割れ目：横の位置で列を決め、縦に長く途切れながら走る
            along = c.p[0] + c.p[2]
            col = int(math.floor(along * 0.5))
            run = int(math.floor(c.p[1] / 5.0 + hsh(col, 3) * 3))
            if hsh(col, run, 21) < 0.34:
                tone -= 1.6
            elif hsh(col + 1, run, 21) < 0.34:
                tone += 0.5   # 割れ目のふち（盛り上がり）
        mossy = c.el.tags.get("moss", 0.0)
        if c.n[1] > 0.6 and self.blob(c, 2.0, 30 + self.eid(c)) < 0.55 + mossy:
            return pick(FOREST["moss"], 3.2 + (self.tex(c, 31) - 0.5) * 1.4 + self.bevel(c, 0.6, 0.6))
        if mossy and side and self.blob(c, 2.5, 40 + self.eid(c)) < mossy:
            return pick(FOREST["moss"], 2.6 + (self.tex(c, 41) - 0.5) * 1.2)
        if c.el.tags.get("lost") and self.blob(c, 2.0, 50) < c.el.tags["lost"]:
            # ロストの白い欠け（白く抜けた所）
            return pick(RAMPS["glow_lost"], 4.0 + (self.tex(c, 51) - 0.5))
        return pick(FOREST["bark"], tone)

    def paint_moss(self, c):
        tone = 3.0 + (self.tex(c, 61) - 0.5) * 1.5 + self.bevel(c, up=0.7, down=0.7)
        if c.el.tags.get("fringe") and c.face.d not in ("up", "down") and c.j == c.H - 1 and c.i % 2 == 1:
            return None   # 垂れた苔のふち（ぎざぎざ）
        return pick(FOREST["moss"], tone)

    def paint_leaf(self, c):
        d = c.edge_dist()
        edge = min(d.values())
        if edge == 0 and hsh(self.eid(c), c.i, c.j, FACE_ID.get(c.face.d, 6), 71) < 0.42:
            return None   # 葉のかたまりのふちは、ところどころ抜く
        tone = 2.9 + (self.blob(c, 1.6, 72 + self.eid(c)) - 0.5) * 1.6 + (self.tex(c, 73) - 0.5) * 0.6
        if c.n[1] > 0.6:
            tone += 0.8
        elif c.n[1] < -0.6:
            tone -= 1.2
        if self.tex(c, 74) > 0.93:
            tone += 1.4   # 光を受けた葉
        return pick(FOREST["leaf"], tone)

    def paint_vine(self, c):
        if hsh(self.eid(c), c.j, 75) < 0.18:
            return None
        tone = 2.8 + (self.tex(c, 76) - 0.5) * 1.4
        if c.j % 3 == 0:
            tone += 1.0   # 小さな葉
        return pick(FOREST["leaf"], tone)

    def paint_pipe(self, c):
        tone = self.std(c, "pipe", base=3.0, noise=0.2, blob=0.3, up=1.4, down=1.0)
        axis = c.el.tags.get("axis", "x")
        k = {"x": 0, "y": 1, "z": 2}[axis]
        along = c.p[k]
        if abs(float(c.n[k])) < 0.5:
            ring = (along - c.el.frm[k]) % 8.0
            if ring < 1.0:
                tone -= 1.1   # 継ぎ目の輪
            elif ring < 2.0:
                tone += 0.8
                if hsh(int(along), int(c.p[(k + 1) % 3] * 2), 81) < 0.25:
                    tone += 1.0   # リベット
        if self.blob(c, 2.0, 82 + self.eid(c)) < 0.22:
            return pick(RAMPS["bronze"], 3.2 + (self.tex(c, 83) - 0.5))   # 緑青の下の青銅
        return pick(FOREST["pipe"], tone)

    def paint_bronze(self, c):
        tone = self.std(c, "bronze", base=3.3, noise=0.2, blob=0.3, up=1.5, down=1.0)
        tone += self.rivet(c)
        if self.blob(c, 1.5, 90 + self.eid(c)) < 0.18:
            return pick(FOREST["pipe"], 3.6)   # 緑青
        return pick(RAMPS["bronze"], tone)

    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.0, noise=0.2, blob=0.35, up=1.5, down=0.9)
        return pick(RAMPS["iron"], tone)

    def paint_carved(self, c):
        tone = self.std(c, "carved", base=3.0, noise=0.25, blob=0.25, up=1.0, down=0.9)
        tone += self.grain(c)
        return pick(FOREST["carved"], tone)

    def paint_hollow(self, c):
        tone = 2.0 + (self.tex(c, 95) - 0.5) * 1.0
        d = c.edge_dist()
        if min(d.values()) == 0:
            tone += 1.5
        return pick(FOREST["hollow"], tone)

    def paint_dark(self, c):
        return pick(FOREST["hollow"], 1.0 + (self.tex(c, 96) - 0.5) * 0.6)

    # ---- キノコ ----
    def paint_cap(self, c):
        if c.n[1] < -0.6:
            # かさの裏（ひだ）：放射状の筋
            ang = math.atan2(c.p[2], c.p[0])
            line = int(math.floor((ang + math.pi) / (2 * math.pi) * 28))
            return pick(FOREST["stem"], 2.2 + (0.8 if line % 2 else -0.4))
        spot = self.blob(c, 2.2, 101) < 0.27 and self.blob(c, 1.1, 102) < 0.8
        if spot:
            tone = 5.0 + (self.tex(c, 103) - 0.5) * 0.8 + self.bevel(c, up=0.6, down=0.8)
            return pick(FOREST["stem"], tone)
        tone = 3.2 + (self.tex(c, 104) - 0.5) * 0.5 + self.bevel(c, up=1.0, down=1.0)
        if c.n[1] > 0.6:
            tone += 0.6
        if abs(float(c.n[1])) < 0.5 and c.j >= c.H - 1:
            tone -= 0.9   # かさのふちの影
        return pick(FOREST["cap"], tone)

    def paint_stem(self, c):
        tone = 3.6 + (self.tex(c, 111) - 0.5) * 0.4 + self.bevel(c, up=0.7, down=0.8)
        # 縦の繊維
        if abs(float(c.n[1])) < 0.5 and hsh(int(math.floor(c.p[0] + c.p[2] * 1.3)), 112) < 0.25:
            tone -= 0.6
        face = c.el.tags.get("face")
        if face and c.face.d == "front":
            # 顔：まん丸の黒い目と、小さな口（ゲームの中の x：表から見て左右）
            x, y = c.p[0], c.p[1]
            for ex in face["eyes"]:
                if abs(x - ex) < 0.6 and face["eye_y"] - 1.0 < y < face["eye_y"] + 1.0:
                    return pick(FOREST["hollow"], 1.0 if y < face["eye_y"] + 0.5 else 4.0)
            if abs(x) < 0.6 and abs(y - face["mouth_y"]) < 0.5:
                return pick(FOREST["hollow"], 2.0)
            for ex in face["eyes"]:
                if abs(x - ex) < 1.1 and abs(y - (face["eye_y"] - 2.0)) < 0.5:
                    return pick(FOREST["cap"], 5.6)   # ほっぺ
        if c.n[1] < -0.6:
            tone -= 1.0
        return pick(FOREST["stem"], tone)

    # ---- キツネ ----
    def paint_fox(self, c):
        strand = hsh(int(math.floor(c.p[0] * 2 + c.p[2])), int(math.floor(c.p[1] / 1.5)), 121)
        tone = 3.0 + (strand - 0.5) * 1.0 + self.bevel(c, up=0.9, down=0.9)
        # 葉の影のまだら（暗い葉の形）と、木漏れ日（明るい小さな斑）
        if self.blob(c, 2.2, 122 + self.eid(c) % 3) < 0.26:
            tone -= 1.2
        elif self.blob(c, 1.3, 125) < 0.13:
            tone += 1.6
        if c.n[1] > 0.6:
            tone += 0.5
        if c.el.tags.get("paw") and c.p[1] < 1.5:
            return pick(FOREST["hollow"], 3.0)
        return pick(FOREST["fox"], tone)

    def paint_fox_light(self, c):
        tone = 3.6 + (self.tex(c, 131) - 0.5) * 0.6 + self.bevel(c, up=0.7, down=0.8)
        return pick(FOREST["fox_light"], tone)

    def paint_fox_leaf(self, c):
        d = c.edge_dist()
        if c.el.tags.get("tips") and c.face.d not in ("up", "down") and d["top"] == 0 and c.i % 2 == 0:
            return None   # 葉先のぎざぎざ
        tone = 3.2 + (self.tex(c, 141) - 0.5) * 0.8 + self.bevel(c, up=1.0, down=0.8)
        # 葉脈（まん中の明るい筋）
        if c.face.d in ("front", "back", "up") and c.W >= 3 and c.i == c.W // 2:
            tone += 1.4
        return pick(FOREST["fox_leaf"], tone)

    # ---- 迷い灯 ----
    def paint_husk(self, c):
        # 透けた萼：板の形（下の板は付け根へ、上の板はてっぺんへ細る）の中に、葉脈だけを残す
        u = (c.i + 0.5) / c.W - 0.5           # −0.5〜0.5
        v = (c.j + 0.5) / c.H                 # 0（上）〜1（下）
        low = c.el.tags.get("part") == "low"
        half = 0.5 * ((1.0 - 0.78 * v) if low else (0.22 + 0.78 * v))
        if abs(u) > half + 0.02:
            return None
        edge = abs(u) > half - 0.13
        rib = abs(u) < 0.07
        k = c.i + (c.j if low else c.H - 1 - c.j) * 2
        net = (k % 4 == 0) or ((c.i - c.j) % 4 == 0 and hsh(self.eid(c), c.i, c.j, 181) < 0.6)
        if not (edge or rib or net):
            return None
        tone = 3.6 + (self.tex(c, 182) - 0.5) * 0.8 + (0.8 if rib else 0.0) - (0.6 if edge else 0.0)
        return pick(FOREST["husk"], tone)

    def paint_glow_lure(self, c):
        tone = 4.0 + (self.tex(c, 151) - 0.5) * 0.6
        if min(c.edge_dist().values()) >= 1:
            tone += 1.6
        return pick(FOREST["glow_lure"], tone)

    def paint_glow_knot(self, c):
        tone = 3.9 + (self.tex(c, 161) - 0.5) * 0.6
        d = c.edge_dist()
        if min(d.values()) >= 1:
            tone += 1.2
        if min(d.values()) >= 2:
            tone += 0.8
        return pick(FOREST["glow_knot"], tone)

    def paint_glow_eye(self, c):
        tone = 4.4 + (self.tex(c, 171) - 0.5) * 0.4
        if min(c.edge_dist().values()) >= 1:
            tone += 1.4
        return pick(FOREST["glow_eye"], tone)

    def paint_glow_gill(self, c):
        ang = math.atan2(c.p[2], c.p[0])
        line = int(math.floor((ang + math.pi) / (2 * math.pi) * 28))
        r = math.hypot(c.p[0], c.p[2])
        tone = 3.4 + (0.9 if line % 2 else -0.6) + (0.8 if r < 2.0 else 0.0)
        return pick(FOREST["glow_gill"], tone)

    def paint_glow_amber(self, c):
        return self.paint_glow(c, "glow_amber", base=4.2)


FACE_ID = {"right": 0, "front": 1, "left": 2, "back": 3, "up": 4, "down": 5}


# ---------------------------------------------------------------- 小さな道具

def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": fmt(bones)}


def once(length, bones, hold=False):
    out = {"animation_length": length, "bones": fmt(bones)}
    if hold:
        out["loop"] = "hold_on_last_frame"
    return out


def fmt(bones):
    out = {}
    for bone, channels in bones.items():
        out[bone] = {}
        for ch, frames in channels.items():
            out[bone][ch] = {f"{float(t):.4g}": list(v) for t, v in frames.items()}
    return out


def anims(name, table):
    return {f"animation.{name}.{k}": v for k, v in table.items()}


# ================================================================ 迷い灯

# 殻の板（側ごとに下の板と上の板）。下の板は付け根（y 1）から外へ開き、上の板は y 6 から内へすぼまる（ほおずきの形）
HUSK_TILT = 31.0


def build_mayoi_bi():
    g = GeoModel("mayoi_bi", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("orb", (0, 6, 0), parent="root")
    g.bone("core", (0, 6, 0), parent="orb")
    g.bone("husk_n", (0, 1, -1), parent="orb")
    g.bone("husk_s", (0, 1, 1), parent="orb")
    g.bone("husk_e", (-1, 1, 0), parent="orb")
    g.bone("husk_w", (1, 1, 0), parent="orb")
    g.bone("tail", (0, 6, 3), parent="orb")
    g.bone("tail_end", (0, 6, 6), parent="tail")
    # 芯：あたたかい金色の実（丸く見えるように、十字に足す）
    g.cube("core", "core", (-2, 4, -2), (4, 4, 4), "glow_lure", glow=True)
    g.cube("core", "core_x", (-3, 5, -1), (6, 2, 2), "glow_lure", glow=True)
    g.cube("core", "core_y", (-1, 3, -1), (2, 6, 2), "glow_lure", glow=True)
    g.cube("core", "core_z", (-1, 5, -3), (2, 2, 6), "glow_lure", glow=True)
    # 殻：葉脈だけが残った、透けた萼（がく）。下の板は外へ、上の板は内へ傾く
    t = HUSK_TILT
    g.cube("husk_n", "husk_n_low", (-4, 1, -1), (8, 6, 0), "husk", rot=(t, 0, 0), pivot=(0, 1, -1), tags={"part": "low"})
    g.cube("husk_n", "husk_n_up", (-4, 6, -4.1), (8, 6, 0), "husk", rot=(-t, 0, 0), pivot=(0, 6, -4.1), tags={"part": "up"})
    g.cube("husk_s", "husk_s_low", (-4, 1, 1), (8, 6, 0), "husk", rot=(-t, 0, 0), pivot=(0, 1, 1), tags={"part": "low"})
    g.cube("husk_s", "husk_s_up", (-4, 6, 4.1), (8, 6, 0), "husk", rot=(t, 0, 0), pivot=(0, 6, 4.1), tags={"part": "up"})
    g.cube("husk_e", "husk_e_low", (-1, 1, -4), (0, 6, 8), "husk", rot=(0, 0, -t), pivot=(-1, 1, 0), tags={"part": "low"})
    g.cube("husk_e", "husk_e_up", (-4.1, 6, -4), (0, 6, 8), "husk", rot=(0, 0, t), pivot=(-4.1, 6, 0), tags={"part": "up"})
    g.cube("husk_w", "husk_w_low", (1, 1, -4), (0, 6, 8), "husk", rot=(0, 0, t), pivot=(1, 1, 0), tags={"part": "low"})
    g.cube("husk_w", "husk_w_up", (4.1, 6, -4), (0, 6, 8), "husk", rot=(0, 0, -t), pivot=(4.1, 6, 0), tags={"part": "up"})
    # てっぺんの小さな柄と、底の付け根
    g.cube("orb", "stalk", (-0.5, 11, -0.5), (1, 2, 1), "carved")
    g.cube("orb", "stalk_tip", (0.5, 12.5, -0.5), (1, 1, 1), "carved")
    g.cube("orb", "base", (-1, 0.5, -1), (2, 1, 2), "carved")
    # 尾：うしろへたなびく光の粒（だんだん小さく）
    g.cube("tail", "tail_a", (-1, 5, 4), (2, 2, 2), "glow_lure", glow=True)
    g.cube("tail_end", "tail_b", (-0.5, 5.5, 7), (1, 1, 1), "glow_lure", glow=True)
    g.cube("tail_end", "tail_c", (-0.5, 6.5, 9), (1, 1, 1), "glow_lure", glow=True, inflate=-0.2)
    return g


def mayoi_bi_anims():
    open_ = {"husk_n": [40, 0, 0], "husk_s": [-40, 0, 0], "husk_e": [0, 0, -40], "husk_w": [0, 0, 40]}
    breathe = {k: [x * 0.12 for x in v] for k, v in open_.items()}
    t = {
        "idle": loop(2.4, dict({
            "orb": {"position": {0: [0, 0, 0], 1.2: [0, 1.2, 0], 2.4: [0, 0, 0]},
                    "rotation": {0: [0, 0, 0], 2.4: [0, 90, 0]}},
            "core": {"scale": {0: [1, 1, 1], 0.6: [1.1, 1.1, 1.1], 1.2: [1, 1, 1], 1.8: [1.1, 1.1, 1.1], 2.4: [1, 1, 1]}},
            "tail": {"rotation": {0: [0, -12, 0], 1.2: [0, 12, 0], 2.4: [0, -12, 0]}},
            "tail_end": {"rotation": {0: [0, 15, 0], 1.2: [0, -15, 0], 2.4: [0, 15, 0]}},
        }, **{k: {"rotation": {0: [0, 0, 0], 1.2: v, 2.4: [0, 0, 0]}} for k, v in breathe.items()})),
        "walk": loop(1.2, {
            "orb": {"position": {0: [0, 0, 0], 0.6: [0, 1.0, 0], 1.2: [0, 0, 0]},
                    "rotation": {0: [10, 0, 0], 0.6: [10, 0, 0], 1.2: [10, 0, 0]}},
            "tail": {"rotation": {0: [10, -18, 0], 0.6: [10, 18, 0], 1.2: [10, -18, 0]}},
        }),
        # 光の火花：殻が開いて実がふくらみ（予兆0.5秒）、はじける
        "attack": once(0.9, dict({
            "core": {"scale": {0: [1, 1, 1], 0.45: [1.5, 1.5, 1.5], 0.55: [0.8, 0.8, 0.8], 0.9: [1, 1, 1]}},
            "orb": {"position": {0: [0, 0, 0], 0.45: [0, 0.6, 0], 0.55: [0, 0, 0]}},
        }, **{k: {"rotation": {0: [0, 0, 0], 0.45: v, 0.55: [x * 1.2 for x in v], 0.9: [0, 0, 0]}}
              for k, v in open_.items()})),
        # 消える：殻が開いて落ち、実が小さくなって消える
        "death": once(1.0, dict({
            "core": {"scale": {0: [1, 1, 1], 0.3: [1.6, 1.6, 1.6], 1.0: [0, 0, 0]}},
            "tail": {"scale": {0: [1, 1, 1], 0.5: [0, 0, 0]}},
            "orb": {"position": {0: [0, 0, 0], 1.0: [0, -3, 0]}},
        }, **{k: {"rotation": {0: [0, 0, 0], 0.8: [x * 2.0 for x in v]}} for k, v in open_.items()}), hold=True),
    }
    return anims("mayoi_bi", t)


# ================================================================ 樹皮兵

def build_juhihei():
    g = GeoModel("juhihei", 128, 128)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 13, 0), parent="root")
    g.bone("head", (0, 26, 0), parent="body")
    g.bone("sprout", (0, 33, 0), parent="head")
    g.bone("arm_right", (-7.5, 24, 0), parent="body")   # 鋤の腕（体の右 = −x）
    g.bone("arm_left", (7.5, 24, 0), parent="body")     # 熊手の腕
    g.bone("leg_right", (-3.5, 13, 0), parent="root")
    g.bone("leg_left", (3.5, 13, 0), parent="root")
    # 足：樹皮の脚と、青銅の靴
    for bone, x0 in (("leg_right", -6.5), ("leg_left", 0.5)):
        g.cube(bone, bone + "_shin", (x0, 2, -2.5), (6, 11, 5), "bark", tags={"moss": 0.05})
        g.cube(bone, bone + "_shoe", (x0 - 0.5, 0, -3.5), (7, 2, 7), "bronze")
        g.cube(bone, bone + "_band", (x0 - 0.25, 7, -2.75), (6, 1, 5), "bronze", inflate=0.25)
    # 胴：樹皮の板。青銅の骨組みの帯。肩に苔。胸に植えた苗の数を刻んだ板
    g.cube("body", "torso", (-7, 13, -4.5), (14, 13, 9), "bark", tags={"moss": 0.25})
    g.cube("body", "belt", (-7, 13, -4.5), (14, 2, 9), "bronze", inflate=0.3)
    g.cube("body", "collar", (-6, 25, -4), (12, 1, 8), "bronze", inflate=0.3)
    g.cube("body", "chest_plate", (-3, 17, -5.2), (6, 6, 1), "carved", tags={"grain": "y"})
    g.cube("body", "chest_glyph", (-1, 19, -5.6), (2, 2, 1), "glow_amber", glow=True)
    g.cube("body", "moss_right", (-8, 24, -4), (5, 3, 8), "moss", tags={"fringe": True})
    g.cube("body", "moss_left", (3, 24, -4), (5, 3, 8), "moss", tags={"fringe": True})
    # 背中の種の筒（古代の青銅の筒）と、肩へのびる管
    g.cube("body", "tank", (-3.5, 14, 4.5), (7, 10, 4), "pipe", tags={"axis": "y"})
    g.cube("body", "tank_cap", (-2.5, 24, 5), (5, 2, 3), "bronze")
    g.cube("body", "tank_pipe", (-7, 21, 5.5), (4, 2, 2), "pipe", tags={"axis": "x"})
    # 頭：彫った木の頭。琥珀色の目のすき間。頭の上に若葉
    g.cube("head", "head", (-4.5, 26, -4.5), (9, 7, 9), "carved", tags={"grain": "y"})
    g.cube("head", "brow", (-5, 30, -5), (10, 1, 2), "bark")
    g.cube("head", "eye", (-3, 28, -5.1), (6, 1, 1), "glow_amber", glow=True)
    g.cube("head", "chin", (-3.5, 26, -5), (7, 1, 1), "bronze")
    g.cube("sprout", "stalk", (-0.5, 33, -0.5), (1, 3, 1), "leaf")
    g.cube("sprout", "leaf_a", (0.5, 35, -0.5), (3, 1, 1), "fox_leaf")
    g.cube("sprout", "leaf_b", (-3.5, 36, -0.5), (3, 1, 1), "fox_leaf")
    # 鋤の腕（右）：太い腕と、苗を植える鋤の刃
    g.cube("arm_right", "upper_r", (-11, 16, -2.5), (4, 9, 5), "bark", tags={"moss": 0.1})
    g.cube("arm_right", "shoulder_r", (-11.5, 22, -3), (5, 4, 6), "bronze")
    g.cube("arm_right", "fore_r", (-11, 9, -2), (4, 7, 4), "bark")
    g.cube("arm_right", "spade_neck", (-10, 6, -1), (2, 3, 2), "bronze")
    g.cube("arm_right", "spade", (-12.5, 0, -1), (7, 7, 1), "iron", tags={"edge": "both"})
    # 熊手の腕（左）：細い腕と、3本の木の爪
    g.cube("arm_left", "upper_l", (7, 16, -2), (4, 9, 4), "bark", tags={"moss": 0.1})
    g.cube("arm_left", "shoulder_l", (6.5, 22, -2.5), (5, 4, 5), "bronze")
    g.cube("arm_left", "fore_l", (7.5, 10, -1.5), (3, 6, 3), "bark")
    for k, x in enumerate((7, 8.5, 10)):
        g.cube("arm_left", f"claw_{k}", (x, 6, -1.5 - (k % 2)), (1, 4, 1), "carved")
    return g


def juhihei_anims():
    t = {
        "idle": loop(3.0, {
            "body": {"rotation": {0: [0, 0, 0], 1.5: [2, 0, 0], 3.0: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [0, 12, 0], 2.0: [0, -10, 0], 3.0: [0, 0, 0]}},
            "sprout": {"rotation": {0: [0, 0, -8], 1.5: [0, 0, 8], 3.0: [0, 0, -8]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 1.5: [-3, 0, 2], 3.0: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.5: [-3, 0, -2], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(1.4, {
            "leg_right": {"rotation": {0: [20, 0, 0], 0.7: [-20, 0, 0], 1.4: [20, 0, 0]}},
            "leg_left": {"rotation": {0: [-20, 0, 0], 0.7: [20, 0, 0], 1.4: [-20, 0, 0]}},
            "arm_right": {"rotation": {0: [-14, 0, 0], 0.7: [14, 0, 0], 1.4: [-14, 0, 0]}},
            "arm_left": {"rotation": {0: [14, 0, 0], 0.7: [-14, 0, 0], 1.4: [14, 0, 0]}},
            "body": {"position": {0: [0, 0, 0], 0.35: [0, 0.8, 0], 0.7: [0, 0, 0], 1.05: [0, 0.8, 0], 1.4: [0, 0, 0]},
                     "rotation": {0: [0, -4, 0], 0.7: [0, 4, 0], 1.4: [0, -4, 0]}},
            "sprout": {"rotation": {0: [0, 0, -10], 0.7: [0, 0, 10], 1.4: [0, 0, -10]}},
        }),
        # 鋤の横なぎ（予兆0.7秒：右の腕を後ろへ引き、前へ振り下ろす）
        "attack": once(1.2, {
            "arm_right": {"rotation": {0: [0, 0, 0], 0.6: [40, -30, 30], 0.7: [45, -32, 32], 0.85: [-70, 20, -10],
                                       1.2: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 0.7: [0, 18, 0], 0.85: [6, -16, 0], 1.2: [0, 0, 0]}},
        }),
        # 根の叩きつけ（予兆1.2秒：両腕を高く上げ、地面を叩く）
        "skill_slam": once(1.8, {
            "arm_right": {"rotation": {0: [0, 0, 0], 1.0: [-160, 0, -10], 1.2: [-165, 0, -12], 1.35: [-30, 0, 0],
                                       1.8: [0, 0, 0]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.0: [-160, 0, 10], 1.2: [-165, 0, 12], 1.35: [-30, 0, 0],
                                      1.8: [0, 0, 0]}},
            "body": {"rotation": {0: [0, 0, 0], 1.0: [-14, 0, 0], 1.2: [-16, 0, 0], 1.35: [24, 0, 0], 1.8: [0, 0, 0]},
                     "position": {0: [0, 0, 0], 1.2: [0, 1, 1], 1.35: [0, -1.5, -1], 1.8: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [-12, 0, 0], 1.35: [10, 0, 0], 1.8: [0, 0, 0]}},
        }),
        "death": once(1.8, {
            "root": {"rotation": {0: [0, 0, 0], 0.4: [-8, 0, 0], 1.2: [80, 0, 0]},
                     "position": {0: [0, 0, 0], 1.2: [0, 1, -2]}},
            "head": {"rotation": {0: [0, 0, 0], 1.2: [-30, 0, 10]}},
            "arm_right": {"rotation": {0: [0, 0, 0], 1.2: [-80, 0, 20]}},
            "arm_left": {"rotation": {0: [0, 0, 0], 1.2: [-80, 0, -20]}},
            "sprout": {"rotation": {0: [0, 0, 0], 1.4: [0, 0, 60]}},
        }, hold=True),
    }
    return anims("juhihei", t)


# ================================================================ ポコダケ

def build_pokodake():
    g = GeoModel("pokodake", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("stem", (0, 0, 0), parent="root")
    g.bone("cap", (0, 10, 0), parent="stem")
    for k, (x, z) in enumerate(((-3, -3), (3, -3), (-3, 3), (3, 3))):
        g.bone(f"foot_{k}", (x, 1, z), parent="root")
        g.cube(f"foot_{k}", f"foot_{k}", (x - 1.5 + (0.5 if x > 0 else -0.5), 0, z - 1.5 + (0.5 if z > 0 else -0.5)),
               (3, 1, 3), "stem")
    # 柄：太い生成りの柄。表に、まん丸の目と小さな口
    g.cube("stem", "stem", (-4, 1, -4), (8, 9, 8), "stem",
           tags={"face": {"eyes": (-2.0, 2.0), "eye_y": 7.0, "mouth_y": 4.5}})
    g.cube("stem", "ring", (-4.5, 8, -4.5), (9, 1, 9), "stem", inflate=0.1)
    # かさ：赤い大きなかさに白い斑点。裏のひだは淡い緑に光る
    g.cube("cap", "cap_rim", (-7.5, 10, -7.5), (15, 3, 15), "cap")
    g.cube("cap", "cap_mid", (-6, 13, -6), (12, 2, 12), "cap")
    g.cube("cap", "cap_top", (-4, 15, -4), (8, 1, 8), "cap")
    g.cube("cap", "gills", (-7, 9.9, -7), (14, 0, 14), "glow_gill", glow=True)
    return g


def pokodake_anims():
    t = {
        "idle": loop(2.0, {
            "cap": {"scale": {0: [1, 1, 1], 1.0: [1.04, 0.95, 1.04], 2.0: [1, 1, 1]},
                    "rotation": {0: [0, 0, -2], 1.0: [0, 0, 2], 2.0: [0, 0, -2]}},
            "stem": {"scale": {0: [1, 1, 1], 1.0: [1.02, 0.98, 1.02], 2.0: [1, 1, 1]}},
        }),
        "walk": loop(0.8, {
            "root": {"position": {0: [0, 0, 0], 0.4: [0, 2, 0], 0.8: [0, 0, 0]}},
            "cap": {"scale": {0: [1.06, 0.92, 1.06], 0.4: [0.96, 1.06, 0.96], 0.8: [1.06, 0.92, 1.06]}},
        }),
        # 頭突き（ふつうの攻撃）
        "attack": once(0.8, {
            "cap": {"rotation": {0: [0, 0, 0], 0.35: [-12, 0, 0], 0.5: [25, 0, 0], 0.8: [0, 0, 0]}},
            "stem": {"rotation": {0: [0, 0, 0], 0.35: [-6, 0, 0], 0.5: [10, 0, 0], 0.8: [0, 0, 0]}},
        }),
        # 毒の胞子（予兆0.8秒：かさが縮こまって、ぶわっと膨らむ）
        "skill_spore": once(1.4, {
            "cap": {"scale": {0: [1, 1, 1], 0.7: [1.18, 0.7, 1.18], 0.8: [1.2, 0.68, 1.2], 0.95: [0.85, 1.3, 0.85],
                              1.4: [1, 1, 1]}},
            "stem": {"scale": {0: [1, 1, 1], 0.8: [1.15, 0.85, 1.15], 0.95: [0.92, 1.1, 0.92], 1.4: [1, 1, 1]}},
        }),
        "death": once(1.6, {
            "cap": {"rotation": {0: [0, 0, 0], 1.2: [35, 0, 20]}, "position": {0: [0, 0, 0], 1.2: [0, -4, 0]}},
            "stem": {"scale": {0: [1, 1, 1], 1.2: [1.2, 0.5, 1.2]}},
        }, hold=True),
    }
    return anims("pokodake", t)


# ================================================================ 葉影ギツネ

def build_hakage_kitsune():
    g = GeoModel("hakage_kitsune", 128, 64)
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 8, 0), parent="root")
    g.bone("head", (0, 10, -6), parent="body")
    g.bone("ear_right", (-2, 13, -8), parent="head")
    g.bone("ear_left", (2, 13, -8), parent="head")
    g.bone("tail", (0, 10, 6), parent="body")
    g.bone("tail_tip", (0, 13.5, 11.5), parent="tail")
    for name, x, z in (("leg_front_right", -2, -4), ("leg_front_left", 2, -4), ("leg_back_right", -2, 4),
                       ("leg_back_left", 2, 4)):
        g.bone(name, (x, 6, z), parent="root")
        g.cube(name, name, (x - 1, 0, z - 1), (2, 6, 2), "fox", tags={"paw": True})
    g.cube("body", "body", (-3, 6, -6.5), (6, 6, 13), "fox")
    g.cube("body", "chest", (-2, 5.5, -7), (4, 5, 4), "fox_light")
    g.cube("body", "ruff", (-3.5, 9, -7), (7, 3, 3), "fox")
    g.cube("head", "head", (-3, 8, -11.5), (6, 5, 5), "fox")
    g.cube("head", "snout", (-1.5, 8, -14.5), (3, 2, 3), "fox_light")
    g.cube("head", "nose", (-0.5, 9.5, -14.9), (1, 1, 1), "dark")
    g.cube("head", "eye_r", (-2.6, 11, -11.6), (1, 1, 0), "glow_eye", glow=True)
    g.cube("head", "eye_l", (1.6, 11, -11.6), (1, 1, 0), "glow_eye", glow=True)
    g.cube("head", "cheek_r", (-4, 8, -10.5), (1, 3, 3), "fox_light")
    g.cube("head", "cheek_l", (3, 8, -10.5), (1, 3, 3), "fox_light")
    g.cube("ear_right", "ear_r", (-3.5, 13, -9.5), (2, 4, 1), "fox")
    g.cube("ear_right", "ear_r_in", (-3, 13, -9.6), (1, 3, 0), "fox_light")
    g.cube("ear_left", "ear_l", (1.5, 13, -9.5), (2, 4, 1), "fox")
    g.cube("ear_left", "ear_l_in", (2, 13, -9.6), (1, 3, 0), "fox_light")
    # 尾：太いふさふさの尾。先は若葉の色で、葉の形に分かれる
    g.cube("tail", "tail", (-2.5, 8, 6), (5, 5, 7), "fox", rot=(32, 0, 0), pivot=(0, 10, 6))
    g.cube("tail_tip", "tail_tip", (-3, 12, 11), (6, 6, 6), "fox_leaf", rot=(55, 0, 0), pivot=(0, 13.5, 11.5),
           tags={"tips": True})
    g.cube("tail_tip", "tail_leaf", (-1.5, 17.5, 13.5), (3, 3, 2), "fox_leaf", rot=(55, 0, 0), pivot=(0, 13.5, 11.5),
           tags={"tips": True})
    return g


def hakage_kitsune_anims():
    walk = {}
    for name, ph in (("leg_front_right", 1), ("leg_back_left", 1), ("leg_front_left", -1), ("leg_back_right", -1)):
        walk[name] = {"rotation": {0: [30 * ph, 0, 0], 0.25: [-30 * ph, 0, 0], 0.5: [30 * ph, 0, 0]}}
    walk["body"] = {"position": {0: [0, 0, 0], 0.125: [0, 0.6, 0], 0.25: [0, 0, 0], 0.375: [0, 0.6, 0], 0.5: [0, 0, 0]}}
    walk["tail"] = {"rotation": {0: [-10, -10, 0], 0.25: [-10, 10, 0], 0.5: [-10, -10, 0]}}
    walk["head"] = {"rotation": {0: [4, 0, 0], 0.25: [-2, 0, 0], 0.5: [4, 0, 0]}}
    t = {
        "idle": loop(3.0, {
            "tail": {"rotation": {0: [0, -14, 0], 1.5: [0, 14, 0], 3.0: [0, -14, 0]}},
            "tail_tip": {"rotation": {0: [0, -10, 0], 1.5: [0, 10, 0], 3.0: [0, -10, 0]}},
            "ear_right": {"rotation": {0: [0, 0, 0], 2.0: [0, 0, 0], 2.15: [0, 0, -25], 2.3: [0, 0, 0]}},
            "ear_left": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 0], 0.75: [0, 0, 25], 0.9: [0, 0, 0]}},
            "body": {"scale": {0: [1, 1, 1], 1.5: [1.02, 1.03, 1.0], 3.0: [1, 1, 1]}},
            "head": {"rotation": {0: [0, 0, 0], 1.0: [0, 10, 0], 2.2: [0, -10, 0], 3.0: [0, 0, 0]}},
        }),
        "walk": loop(0.5, walk),
        # 噛みつき（予兆0.25秒：低く構えて、前へ飛びかかる）
        "attack": once(0.6, {
            "body": {"position": {0: [0, 0, 0], 0.2: [0, -1, 1.5], 0.3: [0, 0.5, -3], 0.6: [0, 0, 0]},
                     "rotation": {0: [0, 0, 0], 0.2: [6, 0, 0], 0.3: [-8, 0, 0], 0.6: [0, 0, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.2: [-15, 0, 0], 0.3: [20, 0, 0], 0.6: [0, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.2: [25, 0, 0], 0.6: [0, 0, 0]}},
        }),
        "death": once(1.2, {
            "root": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 90]}, "position": {0: [0, 0, 0], 0.6: [0, 2, 0]}},
            "head": {"rotation": {0: [0, 0, 0], 0.6: [20, 0, 0]}},
            "tail": {"rotation": {0: [0, 0, 0], 0.8: [-30, 0, 0]}},
        }, hold=True),
    }
    return anims("hakage_kitsune", t)


# ================================================================ 森番の古樹オールドウォード

# 光る節の受け口（Bedrockの座標。ゲームの中の場所は OldWard#knotPosition と同じ：x は同じ、z は向きが逆、y はそのまま）
KNOT_SOCKETS = [
    ("socket_front", (-9.0, 30.0, -19.0), "front"),    # 表（顔の下、少し右）
    ("socket_back", (6.0, 54.0, 15.0), "back"),        # 裏の高い所
    ("socket_side", (-20.0, 20.0, 4.0), "side"),       # 体の右の低い所
]


def build_old_ward():
    g = GeoModel("old_ward", 512, 512)
    g.bone("root", (0, 0, 0))
    g.bone("roots", (0, 0, 0), parent="root")
    g.bone("root_front", (0, 2, -18), parent="roots")
    g.bone("trunk", (0, 0, 0), parent="root")
    g.bone("upper", (0, 46, 0), parent="trunk")
    g.bone("face", (0, 46, -16), parent="upper")
    g.bone("jaw", (0, 42, -16), parent="face")
    g.bone("branch_right", (-14, 58, 0), parent="upper")
    g.bone("branch_left", (14, 58, 0), parent="upper")
    g.bone("lantern", (-30, 57, -1), parent="branch_right")
    g.bone("crown", (0, 72, 0), parent="upper")
    # 根：まわりへ広がる太い根。古代の管を巻きこむ
    for k, (x, z, w, d, ry) in enumerate(((-24, -8, 10, 18, 25), (16, -10, 10, 16, -30), (-26, 6, 12, 10, -15),
                                          (16, 8, 12, 10, 20), (-6, 16, 12, 14, 0))):
        g.cube("roots", f"root_{k}", (x, 0, z), (w, 6, d), "bark", rot=(0, ry, 0), pivot=(x + w / 2, 0, z + d / 2),
               tags={"moss": 0.15})
    g.cube("root_front", "root_front", (-7, 0, -34), (14, 6, 18), "bark", tags={"moss": 0.2})
    g.cube("root_front", "root_front_tip", (-4, 0, -40), (8, 4, 7), "bark")
    # 四隅の張り根（幹の角から地面へ斜めにのびる）
    for k, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        x, z = sx * 20, sz * 20
        g.cube("trunk", f"buttress_{k}", (x - 4, 0, z - 4), (8, 26, 8), "bark",
               rot=(sz * 14, 0, -sx * 14), pivot=(x, 0, z), tags={"moss": 0.2})
    # 古代の管：根の下から地面へ半分うまる。根が上から巻きつく
    g.cube("roots", "pipe_front", (-30, -2, -30), (60, 5, 5), "pipe", tags={"axis": "x"})
    g.cube("roots", "pipe_side", (24, -2, -24), (5, 5, 46), "pipe", tags={"axis": "z"})
    g.cube("roots", "pipe_flange_a", (-31, -2.5, -30.5), (2, 6, 6), "bronze")
    g.cube("roots", "pipe_flange_b", (23.5, -2.5, 20), (6, 6, 2), "bronze")
    g.cube("roots", "root_over_pipe_a", (-18, 2, -33), (6, 5, 10), "bark", tags={"moss": 0.2})
    g.cube("roots", "root_over_pipe_b", (21, 2, -2), (10, 5, 6), "bark")
    # 幹：下ほど太い。表に顔。光る節の受け口
    g.cube("trunk", "trunk_base", (-20, 0, -20), (40, 20, 40), "bark", tags={"moss": 0.1})
    g.cube("trunk", "trunk_mid", (-18, 20, -18), (36, 26, 36), "bark", tags={"moss": 0.05})
    g.cube("upper", "trunk_up", (-15, 46, -15), (30, 18, 30), "bark", tags={"moss": 0.08})
    g.cube("upper", "trunk_top", (-12, 64, -12), (24, 10, 24), "bark", tags={"moss": 0.3})
    # 幹の縦の筋（樹皮の盛り上がり）
    for k, (x, z, w, d, y0, h) in enumerate(((-21, -8, 1, 3, 4, 34), (20, 6, 1, 4, 2, 38), (-6, 18, 4, 1, 6, 36),
                                             (8, -19, 3, 1, 4, 18), (-16, 9, 1, 3, 46, 20), (15, -6, 1, 4, 44, 24),
                                             (2, 15, 3, 1, 46, 22))):
        g.cube("trunk" if y0 < 40 else "upper", f"ridge_{k}", (x, y0, z), (w, h, d), "bark")
    # 幹にからむ管（地面から幹の中へ）
    g.cube("trunk", "pipe_climb", (14, 2, -22), (5, 30, 5), "pipe", tags={"axis": "y"})
    g.cube("trunk", "pipe_climb_cap", (13.5, 31, -22.5), (6, 2, 6), "bronze")
    # 光るキノコ（幹の段に生える小さなキノコ）
    for k, (x, y, z) in enumerate(((-21, 12, 2), (-19, 33, -10), (18, 26, 12), (10, 47, 15.5), (-16.5, 58, 4))):
        g.cube("trunk" if y < 46 else "upper", f"shroom_{k}", (x - 1.5, y, z - 1.5), (3, 1, 3), "glow_gill", glow=True)
        g.cube("trunk" if y < 46 else "upper", f"shroom_stem_{k}", (x - 0.5, y - 1, z - 0.5), (1, 1, 1), "stem")
    # 顔：光る目と、口の割れ目（あごは動く）。目の上の重いまぶた（樹皮）
    g.cube("face", "eye_socket_r", (-13, 49, -16.5), (10, 8, 2), "hollow")
    g.cube("face", "eye_socket_l", (3, 49, -16.5), (10, 8, 2), "hollow")
    g.cube("face", "eye_r", (-11, 50, -17), (6, 4, 1), "glow_eye", glow=True)
    g.cube("face", "eye_l", (5, 50, -17), (6, 4, 1), "glow_eye", glow=True)
    g.cube("face", "brow_r", (-15, 56, -18), (13, 3, 3), "bark", rot=(0, 0, -8), pivot=(-8, 57, -16), tags={"moss": 0.6})
    g.cube("face", "brow_l", (2, 56, -18), (13, 3, 3), "bark", rot=(0, 0, 8), pivot=(8, 57, -16), tags={"moss": 0.6})
    g.cube("face", "nose", (-2.5, 45, -19), (5, 9, 4), "bark")
    # 口は幹の中ほど（表の面 z −18）の少し手前
    g.cube("jaw", "mouth", (-10, 38, -19.4), (20, 6, 2), "hollow")
    g.cube("jaw", "lip", (-11, 36, -20), (22, 2, 3), "bark", tags={"moss": 0.3})
    for k, x in enumerate((-8, -3, 2, 6)):
        g.cube("jaw", f"tooth_{k}", (x, 42, -19.8), (2, 2, 1), "bark")
    # 光る節の受け口（暗い穴。節が壊れると見える）
    for name, (x, y, z), side in KNOT_SOCKETS:
        if side == "side":
            g.cube("trunk", name, (x - 1.5, y - 4, z - 4), (2, 8, 8), "hollow")
        elif side == "back":
            g.cube("upper", name, (x - 4, y - 4, z - 0.5), (8, 8, 2), "hollow")
        else:
            g.cube("trunk", name, (x - 4, y - 4, z - 0.5), (8, 8, 2), "hollow")
    # 枝の腕（右：下へ曲がる大枝と指のような小枝。森番の灯をさげる／左：上へのびる大枝）
    g.cube("branch_right", "limb_r", (-42, 55, -3.5), (28, 7, 7), "bark", tags={"moss": 0.4})
    g.cube("branch_right", "elbow_r", (-46, 44, -4), (8, 16, 8), "bark", tags={"moss": 0.2})
    for k, (dx, dz) in enumerate(((-1, -3), (2, 0), (-1, 3))):
        g.cube("branch_right", f"twig_r{k}", (-44 + dx, 36, -1 + dz), (3, 8, 3), "bark")
    g.cube("branch_right", "leaves_r", (-50, 60, -8), (16, 8, 14), "leaf")
    g.cube("branch_right", "vine_r0", (-25, 45, -1), (1, 10, 0), "vine")
    g.cube("branch_right", "vine_r1", (-36, 41, 2), (1, 14, 0), "vine")
    g.cube("lantern", "chain", (-30.5, 50, -1.5), (1, 5, 1), "iron")
    g.cube("lantern", "lantern_cap", (-32.5, 48, -3.5), (5, 2, 5), "bronze")
    g.cube("lantern", "lantern_glass", (-32, 43, -3), (4, 5, 4), "glow_eye", glow=True)
    g.cube("lantern", "lantern_base", (-32.5, 42, -3.5), (5, 1, 5), "bronze")
    g.cube("branch_left", "limb_l", (14, 56, -3.5), (22, 7, 7), "bark", tags={"moss": 0.4})
    g.cube("branch_left", "rise_l", (32, 62, -4), (8, 18, 8), "bark", tags={"moss": 0.2})
    g.cube("branch_left", "twig_l0", (38, 74, -2), (8, 3, 3), "bark")
    g.cube("branch_left", "leaves_l", (28, 78, -10), (20, 10, 18), "leaf")
    g.cube("branch_left", "vine_l0", (22, 46, 1), (1, 10, 0), "vine")
    # 冠：ふぞろいの葉のかたまりを重ねる。つるが垂れる
    for k, (x, y, z, w, h, d) in enumerate(((-30, 76, -22, 56, 16, 46), (-40, 70, -30, 26, 14, 22),
                                             (8, 72, -32, 26, 12, 20), (-14, 90, -16, 30, 11, 28),
                                             (-28, 86, 2, 22, 10, 22), (-10, 70, 18, 36, 14, 16),
                                             (-48, 72, -6, 16, 12, 18), (22, 80, -4, 18, 12, 22),
                                             (4, 88, 8, 18, 8, 16), (-6, 100, -6, 12, 5, 12))):
        g.cube("crown", f"canopy_{k}", (x, y, z), (w, h, d), "leaf")
    for k, (x, z, y, h) in enumerate(((-24, -30.5, 70, 14), (-8, -22.5, 76, 10), (12, -32.5, 72, 16), (26, -20, 74, 12),
                                      (-30, 10, 76, 12), (-44, -8, 72, 10))):
        g.cube("crown", f"vine_c{k}", (x, y - h, z), (1, h, 0), "vine")
    return g


def old_ward_anims():
    sway = {0: [0, 0, -1.2], 3.0: [0, 0, 1.2], 6.0: [0, 0, -1.2]}
    t = {
        "idle": loop(6.0, {
            "crown": {"rotation": sway},
            "upper": {"rotation": {0: [0, 0, 0.5], 3.0: [0, 0, -0.5], 6.0: [0, 0, 0.5]}},
            "branch_right": {"rotation": {0: [0, 0, 0], 3.0: [0, 0, 3], 6.0: [0, 0, 0]}},
            "branch_left": {"rotation": {0: [0, 0, 0], 3.0: [0, 0, -3], 6.0: [0, 0, 0]}},
            "lantern": {"rotation": {0: [0, 0, 6], 1.5: [0, 0, -6], 3.0: [0, 0, 6], 4.5: [0, 0, -6], 6.0: [0, 0, 6]}},
            "jaw": {"position": {0: [0, 0, 0], 2.8: [0, 0, 0], 3.2: [0, -1, 0], 3.6: [0, 0, 0], 6.0: [0, 0, 0]}},
        }),
        "walk": loop(6.0, {"crown": {"rotation": sway}}),
        # 枝の薙ぎ払い（予兆0.9秒：右の大枝を後ろへ引き、前へ薙ぐ）
        "skill_sweep": once(1.6, {
            "branch_right": {"rotation": {0: [0, 0, 0], 0.8: [0, -45, 8], 0.9: [0, -48, 8], 1.1: [0, 70, -5],
                                          1.6: [0, 0, 0]}},
            "upper": {"rotation": {0: [0, 0, 0], 0.9: [0, -10, 0], 1.1: [0, 12, 0], 1.6: [0, 0, 0]}},
            "lantern": {"rotation": {0: [0, 0, 0], 0.9: [0, 0, 25], 1.2: [0, 0, -35], 1.6: [0, 0, 0]}},
        }),
        # 根の割れ目（予兆1.2秒：前の根を持ち上げ、地面へ叩きつける）
        "skill_crack": once(1.8, {
            "root_front": {"rotation": {0: [0, 0, 0], 1.1: [28, 0, 0], 1.2: [30, 0, 0], 1.3: [-6, 0, 0], 1.8: [0, 0, 0]}},
            "upper": {"rotation": {0: [0, 0, 0], 1.1: [-6, 0, 0], 1.3: [5, 0, 0], 1.8: [0, 0, 0]}},
            "jaw": {"position": {0: [0, 0, 0], 1.1: [0, -2, 0], 1.3: [0, 0, 0]}},
        }),
        # 根の牢（予兆1.4秒：両の枝を高く上げ、ふり下ろす）
        "skill_cage": once(2.0, {
            "branch_right": {"rotation": {0: [0, 0, 0], 1.2: [0, 0, -38], 1.4: [0, 0, -40], 1.55: [0, 0, 15], 2.0: [0, 0, 0]}},
            "branch_left": {"rotation": {0: [0, 0, 0], 1.2: [0, 0, 38], 1.4: [0, 0, 40], 1.55: [0, 0, -15], 2.0: [0, 0, 0]}},
            "crown": {"rotation": {0: [0, 0, 0], 1.4: [-4, 0, 0], 1.6: [4, 0, 0], 2.0: [0, 0, 0]}},
        }),
        # 迷い灯を呼ぶ（冠をゆすって、口を開ける）
        "skill_call": once(1.6, {
            "crown": {"rotation": {0: [0, 0, 0], 0.2: [0, 0, 5], 0.4: [0, 0, -5], 0.6: [0, 0, 5], 0.8: [0, 0, -5],
                                   1.0: [0, 0, 3], 1.6: [0, 0, 0]}},
            "jaw": {"position": {0: [0, 0, 0], 0.3: [0, -3, 0], 1.3: [0, -3, 0], 1.6: [0, 0, 0]}},
            "branch_right": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, -15], 1.3: [0, 0, -15], 1.6: [0, 0, 0]}},
            "branch_left": {"rotation": {0: [0, 0, 0], 0.4: [0, 0, 15], 1.3: [0, 0, 15], 1.6: [0, 0, 0]}},
        }),
        # 怯み（光る節を3つとも壊された：8秒、幹が傾いて冠と枝が垂れる）
        "skill_stun": once(8.0, {
            "upper": {"rotation": {0: [0, 0, 0], 0.5: [9, 0, 5], 7.2: [9, 0, 5], 8.0: [0, 0, 0]}},
            "crown": {"rotation": {0: [0, 0, 0], 0.6: [8, 0, -4], 7.2: [8, 0, -4], 8.0: [0, 0, 0]}},
            "branch_right": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, 28], 7.2: [0, 0, 28], 8.0: [0, 0, 0]}},
            "branch_left": {"rotation": {0: [0, 0, 0], 0.6: [0, 0, -28], 7.2: [0, 0, -28], 8.0: [0, 0, 0]}},
            "jaw": {"position": {0: [0, 0, 0], 0.5: [0, -2, 0], 7.2: [0, -2, 0], 8.0: [0, 0, 0]}},
        }),
        "death": once(3.0, {
            "upper": {"rotation": {0: [0, 0, 0], 1.0: [-6, 0, 3], 2.2: [-38, 0, 12]},
                      "position": {0: [0, 0, 0], 2.2: [0, -6, 8]}},
            "crown": {"rotation": {0: [0, 0, 0], 2.2: [-20, 0, 8]}},
            "branch_right": {"rotation": {0: [0, 0, 0], 1.6: [0, 0, 40]}},
            "branch_left": {"rotation": {0: [0, 0, 0], 1.6: [0, 0, -40]}},
            "jaw": {"position": {0: [0, 0, 0], 0.8: [0, -3, 0]}},
        }, hold=True),
    }
    t["attack"] = t["skill_sweep"]
    return anims("old_ward", t)


# ================================================================ 古樹の光る節

def build_old_ward_knot():
    g = GeoModel("old_ward_knot", 64, 64)
    g.bone("root", (0, 0, 0))
    g.bone("knot", (0, 6, 0), parent="root")
    g.bone("core", (0, 6, 0), parent="knot")
    g.cube("core", "core", (-4, 2, -4), (8, 8, 8), "glow_knot", glow=True)
    # 樹皮の帯：まん中を巻く帯と、縦の帯2本（光るこぶを縛っている）
    g.cube("knot", "band_mid", (-5, 5, -5), (10, 2, 10), "bark")
    g.cube("knot", "band_x", (-1, 1, -5), (2, 10, 10), "bark")
    g.cube("knot", "band_z", (-5, 1, -1), (10, 10, 2), "bark", inflate=0.05)
    return g


def old_ward_knot_anims():
    pulse = {0: [1, 1, 1], 0.6: [1.12, 1.12, 1.12], 1.2: [1, 1, 1]}
    t = {
        "idle": loop(1.2, {"core": {"scale": pulse}}),
        "walk": loop(1.2, {"core": {"scale": pulse}}),
        "attack": once(0.5, {"core": {"scale": {0: [1, 1, 1], 0.2: [1.3, 1.3, 1.3], 0.5: [1, 1, 1]}}}),
        "death": once(0.6, {"core": {"scale": {0: [1, 1, 1], 0.15: [1.5, 1.5, 1.5], 0.6: [0, 0, 0]}},
                            "knot": {"scale": {0: [1, 1, 1], 0.6: [0.2, 0.2, 0.2]}}}, hold=True),
    }
    return anims("old_ward_knot", t)


MODELS = [
    (build_mayoi_bi, mayoi_bi_anims),
    (build_juhihei, juhihei_anims),
    (build_pokodake, pokodake_anims),
    (build_hakage_kitsune, hakage_kitsune_anims),
    (build_old_ward, old_ward_anims),
    (build_old_ward_knot, old_ward_knot_anims),
]


# ---------------------------------------------------------------- 確認画像用：ボーンを回した形

def posed(g, bone_rot):
    """bone_rot：{ボーン: ((rx, ry, rz) Bedrockの回転, 大きさ, (dx, dy, dz) 動かす量)}。親のボーンの動きも子に掛ける。"""
    info = {name: (np.array([-p[0], p[1], p[2]], dtype=float), parent) for name, p, parent in g.bones}

    def chain(bone):
        out = []
        while bone:
            out.append(bone)
            bone = info[bone][1]
        return out   # 自分 → 親 → …

    def move_fn(bone):
        steps = []
        for b in chain(bone):
            if b in bone_rot:
                v = bone_rot[b]
                rot = v[0]
                s = v[1] if len(v) > 1 else 1.0
                off = v[2] if len(v) > 2 else (0, 0, 0)
                rx, ry, rz = rot
                R = rot_matrix("z", rz) @ rot_matrix("y", -ry) @ rot_matrix("x", -rx)
                steps.append((R, info[b][0], s, np.array([-off[0], off[1], off[2]], dtype=float)))

        def f(q):
            for R, pv, s, off in steps:
                q = R @ ((q - pv) * s) + pv + off
            return q
        return f

    out = []
    for c in g.elements:
        f = move_fn(c.bone)
        out.append(_Posed(c, f))
    return SimpleNamespace(elements=out)


class _Posed:
    def __init__(self, c, f):
        self._c, self._f = c, f
        self.faces = {k: _PosedFace(fc, self) for k, fc in c.faces.items()}

    def __getattr__(self, k):
        return getattr(self._c, k)


class _PosedFace:
    def __init__(self, fc, owner):
        self._fc, self._o = fc, owner

    def __getattr__(self, k):
        return getattr(self._fc, k)

    def corners_world(self):
        return [self._o._f(q) for q in self._fc.corners_world()]

    def normal_world(self):
        c = self.corners_world()
        n = np.cross(c[3] - c[0], c[1] - c[0])
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            return self._fc.normal_world()
        n = n / ln
        if np.dot(n, self._fc.normal_world()) < -0.5:
            n = -n
        return n


# ---------------------------------------------------------------- 書き出しと確認画像

def build_all(repo):
    out = {}
    for build, anim in MODELS:
        g = build()
        g.pack()
        frames = g.paint(ForestPainter(g), frames=1)
        g.save(repo, "entity", frames, animations=anim())
        out[g.name] = (g, frames[0])
    return out


def sheet_font(size):
    from PIL import ImageFont
    return ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", size, index=0)


def check_sheet(models, out_dir):
    from PIL import Image, ImageDraw
    from render import render_many
    os.makedirs(out_dir, exist_ok=True)
    sheets = []
    layout = {
        "mayoi_bi": dict(scale=16, center=(0, 6, 0), size=(300, 300)),
        "juhihei": dict(scale=7.5, center=(0, 17, 0), size=(300, 340)),
        "pokodake": dict(scale=13, center=(0, 8, 0), size=(300, 300)),
        "hakage_kitsune": dict(scale=11, center=(0, 8, 0), size=(320, 260)),
        "old_ward": dict(scale=3.1, center=(0, 52, 0), size=(420, 440)),
        "old_ward_knot": dict(scale=16, center=(0, 6, 0), size=(260, 260)),
    }
    poses = {
        "mayoi_bi": {"husk_n": ((40, 0, 0),), "husk_s": ((-40, 0, 0),), "husk_e": ((0, 0, -40),),
                     "husk_w": ((0, 0, 40),), "core": ((0, 0, 0), 1.5)},
        "juhihei": {"arm_right": ((-160, 0, -10),), "arm_left": ((-160, 0, 10),), "body": ((-14, 0, 0),)},
        "pokodake": {"cap": ((0, 0, 0), 1.15)},
        "hakage_kitsune": {"body": ((6, 0, 0), 1.0, (0, -1, 1.5)), "head": ((-15, 0, 0),), "tail": ((25, 0, 0),)},
        "old_ward": {"branch_right": ((0, 0, -40),), "branch_left": ((0, 0, 40),), "root_front": ((30, 0, 0),)},
        "old_ward_knot": {"core": ((0, 0, 0), 1.12)},
    }
    for name, (g, tex) in models.items():
        lay = layout[name]
        tiles = []
        for yaw, pitch in ((150, 14), (-30, 14), (180, 0), (90, 0)):
            tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, **lay))
        tiles.append(render_many([(posed(g, poses[name]), tex)], yaw=150, pitch=14, **lay))
        tiles.append(render_many([(g, tex)], yaw=150, pitch=14, darkness=0.2, **lay))
        w, h = lay["size"]
        s = Image.new("RGBA", (w * len(tiles), h + 22), (52, 60, 52, 255))
        for k, im in enumerate(tiles):
            s.alpha_composite(im, (w * k, 22))
        ImageDraw.Draw(s).text((6, 2), name + "  （斜め前・斜め後ろ・正面・横・技の形・暗い所）", fill=(235, 235, 220),
                               font=sheet_font(15))
        sheets.append(s)
    W = max(s.size[0] for s in sheets)
    H = sum(s.size[1] for s in sheets)
    sheet = Image.new("RGBA", (W, H), (40, 44, 40, 255))
    y = 0
    for s in sheets:
        sheet.alpha_composite(s, (0, y))
        y += s.size[1]
    path = os.path.join(out_dir, "forest_models_check.png")
    sheet.save(path)
    # テクスチャも並べて
    tex_tiles = [Image.fromarray(tex, "RGBA") for _, (g, tex) in models.items()]
    tw = sum(min(t.size[0], 512) for t in tex_tiles) + 10 * len(tex_tiles)
    th = max(t.size[1] for t in tex_tiles)
    ts = Image.new("RGBA", (tw, th), (90, 90, 90, 255))
    x = 0
    for t in tex_tiles:
        ts.alpha_composite(t, (x, 0))
        x += t.size[0] + 10
    ts.save(os.path.join(out_dir, "forest_textures_check.png"))
    return path


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models = build_all(repo)
    if len(sys.argv) > 2:
        print(check_sheet(models, sys.argv[2]))
    print("models written:", ", ".join(models))


if __name__ == "__main__":
    main()
