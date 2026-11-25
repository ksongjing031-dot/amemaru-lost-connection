"""AMEMARU: LOST CONNECTION の街のNPCの立体のモデル（GeckoLib）を作る（11月22日のご指示）。

ご指示：キャラクターのデザインはもっとこだわりたい。キャラクターに凹凸をつけて立体的にする。女性キャラはもっとかわいらしく、
男性はもっとかっこよくする。別の既存modのキャラクターなどを資料として作成する。

使い方: python3 tools/artgen/make_npc_models.py [リポジトリ] [--preview 出力のフォルダ] [--only 名前]

作るもの（assets/amemaru）：
  geo/entity/npc/<名前>.geo.json            モデル（骨は root・body・head・eyelids・right_arm・left_arm・right_leg・left_leg と、人ごとの飾り）
  textures/entity/npc/<名前>.png            絵（モデルの単位の2倍の細かさ）と、表情の差分 <名前>_<表情>.png（顔だけが違う）
  animations/entity/npc/<名前>.animation.json  idle（息・まばたき・髪とマントの揺れ・持ち物の構え）と walk

見た目は Phase 0-A 8章のとおり（年・髪・服・持ち物・色）。資料にした既存のmodと、どこを参考にしたかは、建築の文書の
「NPCのデザイン（11月22日）」のタブにある（形の作り方だけを参考にし、ほかのmodの人物の見た目はまねない）。
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from npcgen import (CLEAR, D, FaceDesign, NpcModel, hash01, hexc, kf, mix, paint_hd, preview_sheet, rgba,
                    save_model, shade)


# ================================================================ 塗り方（どの人にも使う）

def vshade(ctx, c, top=0.05, bottom=0.10):
    """縦の面は上を少し明るく、下を少し暗く。上の面は明るく、下の面は暗く。"""
    d = ctx.face.d
    if d == "up":
        return shade(c, 1.05)
    if d == "down":
        return shade(c, 0.80)
    t = ctx.j / max(1, ctx.H - 1)
    return shade(c, 1.0 + top * (1 - t) - bottom * t)


def name_seed(name):
    """部品の名前から決まる数（FNV-1a）。何度作り直しても同じ絵になるように、メモリの番地（id）は使わない。"""
    h = 2166136261
    for byte in str(name).encode("utf-8"):
        h = ((h ^ byte) * 16777619) & 0xFFFFFFFF
    return h & 0xFFFF


def grain(ctx, c, amount=0.05, salt=0):
    n = hash01(ctx.i, ctx.j, name_seed(ctx.el.name), salt) - 0.5
    return shade(c, 1.0 + n * 2 * amount)


def hair_color(ctx, pal, salt=0, ring_y=None):
    """髪：毛の流れ（2マスごとの筋）と、天使の輪（明るい帯）。"""
    base, dark, light = pal["hair"], pal["hair_d"], pal["hair_l"]
    d = ctx.face.d
    if d in ("up", "down"):
        k = hash01(int(ctx.p[0] * 2), int(ctx.p[2] * 2), salt)
    else:
        k = hash01(ctx.i // 2, salt, 7)
    c = mix(base, dark, 0.35) if k < 0.3 else (mix(base, light, 0.25) if k > 0.85 else base)
    if ring_y is not None and d not in ("up", "down") and abs(ctx.p[1] - ring_y) < 0.55:
        c = mix(c, light, 0.65)
    return vshade(ctx, c, 0.06, 0.12)


def edge_band(ctx, width):
    """面のふちからの近さ（D 倍のマスで）。"""
    e = ctx.edge_dist()
    return min(e["left"], e["right"], e["top"], e["bottom"]) < width


# ================================================================ リリア

LILIA_PAL = {
    "skin": hexc("FADAC2"), "skin_d": hexc("EBBB9C"), "blush": hexc("F4A39E"),
    "hair": hexc("8C5130"), "hair_d": hexc("66391F"), "hair_l": hexc("B9774A"),
    "vest": hexc("8DC05C"), "vest_d": hexc("6E9F43"), "vest_l": hexc("ABD67C"),
    "skirt": hexc("5F8E3D"), "skirt_d": hexc("4B7430"),
    "cream": hexc("F5EDD8"), "cream_d": hexc("E1D4B6"),
    "ribbon": hexc("4F8F3B"), "ribbon_l": hexc("6FB257"),
    "tights": hexc("4E3A2E"), "boot": hexc("7C4E2D"), "boot_d": hexc("5B371E"), "boot_l": hexc("9A6741"),
    "cover": hexc("71392A"), "cover_d": hexc("552A1E"), "gold": hexc("D8B45C"), "page": hexc("F1E5C6"),
    "leaf": hexc("4FA65C"), "leaf_d": hexc("327A40"),
}

LILIA_FACE_PAL = {
    "L": hexc("3B2418"), "H": hexc("FFFFFF"), "a": hexc("2F6E45"), "b": hexc("4E9A5E"), "c": hexc("8CCB7E"),
    "p": hexc("17301F"), "w": hexc("FDFDFB"), "B": hexc("A86A42"), "m": hexc("B45E62"), "M": hexc("E08B8E"),
}

LILIA_EYES = {
    "normal": ["L L L L",
               "L H a a",
               "w a p b",
               "w b b c",
               ". c c ."],
    "smile": [". . . .",
              ". L L .",
              "L . . L",
              ". . . .",
              ". . . ."],
    "surprised": ["L L L L",
                  "w H a w",
                  "w a p w",
                  "w b b w",
                  ". w w ."],
    "worried": [". . L L",
                "L L a a",
                "w H p b",
                "w b b c",
                ". c c ."],
    "closed": [". . . .",
               ". . . .",
               ". . . .",
               "L L L L",
               ". L L ."],
}

LILIA_BROWS = {
    "normal": [". . . .", "B B B ."],
    "surprised": ["B B B .", ". . . ."],
    "worried": [". . B B", "B B . ."],
    "smile": [". . . .", "B B B ."],
}

LILIA_MOUTHS = {
    "normal": ["m m"],
    "smile": ["m . . m", ". m m ."],
    "surprised": [". m m .", "m M M m", ". m m ."],
    "worried": ["m . m", ". m ."],
}


def lilia():
    """リリア・フェルン（22歳）。栗色の髪を一つに結び、若草色の受付服に羊歯の葉のブローチ。分厚い冒険者名簿を抱える。

    かわいらしく：頭を少し大きく（9）、目を大きく（2倍の細かさで縦5）、ほおの赤み、前髪・横髪・ポニーテール・アホ毛を立体に、
    ベストと肩のふくらみ・スカートの裾の段、胸の小さなふくらみ、リボンとブローチを立体に。
    """
    g = NpcModel("lilia", 128, 128)
    g.face_center_y = 26
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 11, 0), "root")
    g.bone("head", (0, 21, 0), "body")
    g.bone("eyelids", (0, 24, -4.5), "head")
    g.bone("hair_tail", (0, 28, 4.5), "head")
    g.bone("hair_tail2", (0, 23.2, 5.9), "hair_tail")
    g.bone("ahoge", (0, 30, -1), "head")
    g.bone("skirt", (0, 12, 0), "body")
    g.bone("right_arm", (-5, 19.5, 0), "body")
    g.bone("left_arm", (5, 19.5, 0), "body")
    g.bone("book", (2.5, 16, -4), "body")
    g.bone("right_leg", (-1.75, 11, 0), "root")
    g.bone("left_leg", (1.75, 11, 0), "root")

    # 足（茶色のタイツと、折り返しのあるブーツ）
    for side, x in (("right", -3.25), ("left", 0.25)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -1.5), (3, 11, 3), "tights")
        g.part(side + "_leg", "boot_" + side, (x, 0, -1.5), (3, 4, 3), "boot", inflate=0.3)
        g.part(side + "_leg", "cuff_" + side, (x, 3.6, -1.5), (3, 1, 3), "boot_cuff", inflate=0.45)
    # 胴（クリームのブラウス）と、若草色のベスト（前の真ん中をV字に開ける）・胸・えり・リボン・ブローチ
    g.part("body", "torso", (-3.5, 11, -2), (7, 10, 4), "blouse")
    g.part("body", "vest", (-3.5, 12, -2), (7, 9, 4), "vest", inflate=0.25)
    g.part("body", "bust", (-2.5, 16, -2.85), (5, 2, 1), "vest_bust", rot=(-12, 0, 0), pivot=(0, 18, -2.2))
    g.part("body", "collar", (-2.5, 20.2, -2), (5, 1, 4), "collar", inflate=0.3)
    g.part("body", "ribbon_knot", (-0.5, 19.4, -2.75), (1, 1, 1), "ribbon")
    g.part("body", "ribbon_r", (-2.2, 19.0, -2.6), (2, 2, 0), "ribbon_wing", rot=(0, 0, -14), pivot=(-0.5, 20, -2.6))
    g.part("body", "ribbon_l", (0.2, 19.0, -2.6), (2, 2, 0), "ribbon_wing", rot=(0, 0, 14), pivot=(0.5, 20, -2.6))
    g.part("body", "ribbon_tail_r", (-1.2, 17.4, -2.62), (1, 2, 0), "ribbon_wing", rot=(0, 0, 10), pivot=(-0.5, 19.4, -2.62))
    g.part("body", "ribbon_tail_l", (0.2, 17.4, -2.62), (1, 2, 0), "ribbon_wing", rot=(0, 0, -10), pivot=(0.5, 19.4, -2.62))
    g.part("body", "brooch", (1.1, 17.8, -3.05), (2, 2, 0), "brooch", rot=(0, 0, -18), pivot=(2.1, 18.8, -3.05))
    # スカート（上と、少し広い裾の2段）
    g.part("skirt", "skirt_top", (-4, 7.5, -2.5), (8, 5, 5), "skirt")
    g.part("skirt", "skirt_hem", (-4.5, 5.5, -3), (9, 3, 6), "skirt_hem")
    # 腕（細い3。肩のふくらみと、クリームの袖口）
    for side, x in (("right", -6.5), ("left", 3.5)):
        g.part(side + "_arm", "arm_" + side, (x, 11, -1.5), (3, 10, 3), "arm")
        g.part(side + "_arm", "puff_" + side, (x, 18.6, -1.5), (3, 2, 3), "puff", inflate=0.4)
        g.part(side + "_arm", "cuff_arm_" + side, (x, 12.2, -1.5), (3, 1, 3), "arm_cuff", inflate=0.3)
    # 抱えた冒険者名簿（分厚い革の本。金の角）
    g.part("book", "book", (-0.5, 12.8, -5.6), (4, 6, 3), "book", rot=(6, -10, 3), pivot=(1.5, 15.8, -4.1))
    # 頭（9）と髪
    g.part("head", "head", (-4.5, 21, -4.5), (9, 9, 9), "head")
    g.part("head", "hair_shell", (-4.5, 22, -4.5), (9, 8, 9), "hair_shell", inflate=0.5)
    # 前髪：長さと奥行きの違う5本の束（まん中の束は目の間まで）
    for k, (x0, w, bottom, z0) in enumerate(((-4.5, 2, 25.9, -5.35), (-2.5, 2, 26.8, -5.15), (-0.5, 1, 25.6, -5.3),
                                            (0.5, 2, 26.8, -5.15), (2.5, 2, 25.9, -5.35))):
        g.part("head", "bangs_%d" % k, (x0, bottom, z0), (w, round(29.6 - bottom) if 29.6 - bottom >= 3 else 3, 1), "bangs")
    g.part("head", "crown", (-3.5, 30.2, -3.5), (7, 1, 7), "crown")
    g.part("head", "lock_r", (-5.35, 22.4, -4.4), (1, 6, 2), "lock")
    g.part("head", "lock_l", (4.35, 22.4, -4.4), (1, 6, 2), "lock")
    g.part("hair_tail", "tie", (-1, 26.8, 4.4), (2, 2, 1), "tie", inflate=0.15)
    g.part("hair_tail", "tail", (-1.5, 22.6, 4.9), (3, 5, 2), "tail")
    g.part("hair_tail2", "tail_low", (-1.5, 18.2, 4.9), (3, 5, 2), "tail")
    g.part("hair_tail2", "tail_tip", (-1, 16.2, 5.2), (2, 2, 2), "tail")
    g.part("ahoge", "ahoge", (-0.5, 30.2, -1.4), (1, 2, 1), "ahoge", rot=(0, 0, 24), pivot=(0, 30.2, -1))
    # まぶた（ふだんは頭の中に隠れていて、まばたきのときだけ顔の前に出る）
    g.part("eyelids", "eyelids", (-3.5, 22.5, -4.0), (7, 3, 0), "eyelids")
    g.pose = {"left_arm": (-62, 34, -4), "right_arm": (0, 0, 5), "hair_tail": (16, 0, 0), "hair_tail2": (-12, 0, 0)}
    g.pack()

    P = LILIA_PAL
    face = FaceDesign(18, 18, P["skin"], LILIA_FACE_PAL, eye_row=9, eye_col=3, eyes=LILIA_EYES, mouths=LILIA_MOUTHS,
                      brows=LILIA_BROWS, mouth_row=15, brow_row=6, blush=(P["blush"], 14, (2, 3)))

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 5:
                        return rgba(hair_color(ctx, P, 3))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    return rgba(c if c is not None else vshade(ctx, P["skin"], 0.0, 0.06))
                if d == "up" or (d in ("left", "right", "back") and y > 22.6):
                    return rgba(hair_color(ctx, P, 3))
                return rgba(vshade(ctx, P["skin_d"] if d == "down" else P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 9, "closed")
                return rgba(c if c is not None else P["skin"])
            if m == "hair_shell":
                if d == "front" and y < 28.4:
                    return CLEAR
                if d == "down":
                    return CLEAR
                if d in ("left", "right") and y < 24.2 and ctx.p[2] < -2.6:
                    return CLEAR   # 顔の横（横髪の所）は開ける
                return rgba(hair_color(ctx, P, 5, ring_y=28.6))
            if m == "bangs":
                if d in ("front", "left", "right", "back"):
                    # 束の先：まん中ほど長く、両はしを1マス切る（とがった毛先）
                    if ctx.j >= ctx.H - 1 and (ctx.i == 0 or ctx.i == ctx.W - 1):
                        return CLEAR
                if d == "down":
                    return rgba(shade(P["hair_d"], 0.9))
                return rgba(hair_color(ctx, P, 9, ring_y=28.6))
            if m == "lock":
                if d in ("front", "back", "left", "right"):
                    e = ctx.edge_dist()
                    if ctx.j >= ctx.H - 2 and (e["left"] == 0 or e["right"] == 0):
                        return CLEAR
                return rgba(hair_color(ctx, P, 11))
            if m == "crown":
                return rgba(hair_color(ctx, P, 15)) if ctx.face.d != "down" else CLEAR
            if m in ("tail", "ahoge"):
                return rgba(hair_color(ctx, P, 13))
            if m == "tie":
                return rgba(vshade(ctx, P["ribbon_l"] if ctx.j % 3 == 0 else P["ribbon"]))
            if m == "blouse":
                return rgba(grain(ctx, vshade(ctx, P["cream"]), 0.03))
            if m == "vest":
                if d == "front":
                    # 前の真ん中をV字に開ける（上ほど広い）
                    cx = abs(ctx.i - (ctx.W - 1) / 2)
                    open_w = max(0.0, (ctx.H * 0.55 - ctx.j) * 0.55)
                    if cx < open_w:
                        return CLEAR
                    if cx < open_w + 1.2:
                        return rgba(P["vest_d"])
                    if ctx.j in (12, 13) and 2 <= ctx.i <= ctx.W - 3:
                        return rgba(P["vest_d"])          # 腰の切り替え
                if d == "down":
                    return rgba(P["vest_d"])
                if d == "up":
                    return CLEAR
                c = P["vest_l"] if edge_band(ctx, 1) and d == "front" else P["vest"]
                return rgba(grain(ctx, vshade(ctx, c), 0.04))
            if m == "vest_bust":
                return rgba(grain(ctx, vshade(ctx, P["vest"], 0.08, 0.14), 0.03))
            if m == "collar":
                return rgba(vshade(ctx, P["cream"]))
            if m == "ribbon":
                return rgba(vshade(ctx, P["ribbon"]))
            if m == "ribbon_wing":
                e = ctx.edge_dist()
                return rgba(P["ribbon_l"] if e["top"] == 0 else (P["ribbon"] if e["bottom"] > 0 else shade(P["ribbon"], 0.8)))
            if m == "brooch":
                # 羊歯の葉：真ん中の茎と、左右の小葉。金のふち
                cx = ctx.i - 1.5
                cy = ctx.j
                if abs(cx) < 0.6 and cy >= 1:
                    return rgba(P["leaf_d"])
                if abs(cx) <= 1.6 - cy * 0.15 and cy <= 3:
                    return rgba(P["leaf"])
                return rgba(P["gold"]) if (cy == 0 or abs(cx) > 1.4) and cy < 4 else CLEAR
            if m == "skirt" or m == "skirt_hem":
                if d == "up":
                    return CLEAR if m == "skirt_hem" else rgba(P["skirt_d"])
                if d == "down":
                    return rgba(shade(P["skirt_d"], 0.7))
                pleat = (ctx.i % 4 == 0)
                c = P["skirt_d"] if pleat else P["skirt"]
                if m == "skirt_hem" and ctx.j in (ctx.H - 3,):
                    c = P["cream"]
                return rgba(grain(ctx, vshade(ctx, c, 0.04, 0.12), 0.03))
            if m == "arm":
                if y < 12.4:
                    return rgba(vshade(ctx, P["skin"], 0.0, 0.08))      # 手
                if d == "up":
                    return rgba(P["vest"])
                return rgba(grain(ctx, vshade(ctx, P["vest"]), 0.04))
            if m == "puff":
                return rgba(grain(ctx, vshade(ctx, P["vest_l"], 0.06, 0.16), 0.03))
            if m == "arm_cuff":
                return rgba(vshade(ctx, P["cream"]))
            if m == "tights":
                return rgba(grain(ctx, vshade(ctx, P["tights"]), 0.03))
            if m == "boot":
                if d == "down":
                    return rgba(P["boot_d"])
                if d == "up":
                    return CLEAR
                c = P["boot_d"] if ctx.j >= ctx.H - 2 else P["boot"]
                return rgba(grain(ctx, vshade(ctx, c), 0.05))
            if m == "boot_cuff":
                if d in ("up", "down"):
                    return CLEAR if d == "down" else rgba(P["boot_d"])
                return rgba(vshade(ctx, P["boot_l"]))
            if m == "book":
                if d == "front" or d == "back":
                    e = ctx.edge_dist()
                    if (e["left"] <= 1 and e["top"] <= 1) or (e["right"] <= 1 and e["top"] <= 1) or \
                            (e["left"] <= 1 and e["bottom"] <= 1) or (e["right"] <= 1 and e["bottom"] <= 1):
                        return rgba(P["gold"])
                    if d == "front" and 2 <= ctx.j <= 3 and 2 <= ctx.i <= ctx.W - 3:
                        return rgba(P["gold"])                      # 題の金の線
                    return rgba(grain(ctx, P["cover"], 0.05))
                if d == "left":
                    return rgba(grain(ctx, P["cover_d"], 0.05))     # 背
                # 小口（紙の重なり）
                return rgba(P["page"] if ctx.i % 2 == 0 or d in ("up", "down") else shade(P["page"], 0.9))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "smile", "surprised", "worried"]


# ================================================================ どの人にも使う小物

def cloth(ctx, c, amount=0.04):
    return grain(ctx, vshade(ctx, c), amount)


def star_at(ctx, cell=8, salt=0, keep=0.55):
    """星の刺繍（小さな＋）を、面にまばらに置く。星の上なら True。"""
    ci, cj = ctx.i // cell, ctx.j // cell
    if hash01(ci, cj, salt) > keep:
        return False
    oi = cell // 2 + int((hash01(ci, cj, salt + 1) - 0.5) * (cell - 4))
    oj = cell // 2 + int((hash01(ci, cj, salt + 2) - 0.5) * (cell - 4))
    di, dj = ctx.i - (ci * cell + oi), ctx.j - (cj * cell + oj)
    return abs(di) + abs(dj) <= 1 and not (abs(di) == 1 and abs(dj) == 1)


def sprite_at(rows, ctx, ci, cj, pal):
    """面の (ci, cj) を左上にして、小さな絵を置く。絵の上なら色、外なら None。"""
    j = ctx.j - cj
    if j < 0 or j >= len(rows):
        return None
    line = rows[j].split()
    i = ctx.i - ci
    if i < 0 or i >= len(line) or line[i] == ".":
        return None
    return pal[line[i]]


def male_face_pal(skin_d, lash, white, iris, pupil, brow, lip, extra=None):
    pal = {"L": lash, "w": white, "i": iris, "p": pupil, "S": skin_d, "B": brow, "m": lip, "t": hexc("F2EEE6")}
    pal.update(extra or {})
    return pal


# ================================================================ セレナ

SERENA_PAL = {
    "skin": hexc("F8DCCA"), "skin_d": hexc("E7BEA6"), "blush": hexc("F0B0AA"),
    "hair": hexc("E8DDBD"), "hair_d": hexc("C9B98F"), "hair_l": hexc("FBF6E7"),
    "robe": hexc("F6F5F1"), "robe_d": hexc("DAD8D2"), "blue": hexc("9FC3E4"), "blue_d": hexc("7AA3CC"),
    "silver": hexc("D6DCE4"), "silver_d": hexc("A7B1BE"), "shoe": hexc("C9CED6"),
}

SERENA_FACE_PAL = {
    "L": hexc("6A5552"), "H": hexc("FFFFFF"), "a": hexc("4E77A8"), "b": hexc("76A0CF"), "c": hexc("B4D2EE"),
    "p": hexc("2A3C58"), "w": hexc("FDFDFB"), "B": hexc("C2AD80"), "m": hexc("BC7378"), "M": hexc("E39A9C"),
}

SERENA_EYES = {
    # おだやか：上のまぶたを少し下げ、目じりのまつ毛を長く
    "normal": ["L . . .",
               ". L L L",
               "L H a a",
               "w a p b",
               ". c c ."],
    "smile": [". . . .",
              ". . . .",
              ". L L .",
              "L . . L",
              ". . . ."],
    "closed": [". . . .",
               ". . . .",
               ". . . .",
               "L L L L",
               ". L L ."],
}
SERENA_BROWS = {"normal": [". . . .", ". B B B"], "smile": [". . . .", ". B B B"]}
SERENA_MOUTHS = {"normal": ["m . . m", ". m m ."], "smile": ["m . . m", ". M M ."]}


def serena():
    """セレナ・アステル（41歳）。銀に近い淡い金髪。星の刺繍の白い司祭服、手に小さな銀の鈴。色：白・淡い青・銀。

    やさしく落ち着いた大人の女性：長い髪を背中まで立体に流し、前髪は分けて横へ。袖の広い司祭服と、淡い青の肩掛け（ストラ）に
    銀の星。両手を前で合わせて鈴を持つ。
    """
    g = NpcModel("serena", 128, 128)
    g.face_center_y = 27
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), "root")
    g.bone("head", (0, 22, 0), "body")
    g.bone("eyelids", (0, 25, -4.5), "head")
    g.bone("hair_back", (0, 29, 4.4), "head")
    g.bone("robe", (0, 12, 0), "body")
    g.bone("right_arm", (-5, 20.5, 0), "body")
    g.bone("left_arm", (5, 20.5, 0), "body")
    g.bone("bell", (0, 12.5, -6), "body")
    g.bone("right_leg", (-1.75, 12, 0), "root")
    g.bone("left_leg", (1.75, 12, 0), "root")
    for side, x in (("right", -3.25), ("left", 0.25)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -1.5), (3, 12, 3), "leg")
    g.part("body", "torso", (-3.5, 12, -2), (7, 10, 4), "robe_top")
    g.part("body", "bust", (-2.5, 17, -2.8), (5, 2, 1), "robe_bust", rot=(-12, 0, 0), pivot=(0, 19, -2.1))
    g.part("body", "sash", (-3.5, 12, -2), (7, 2, 4), "sash", inflate=0.3)
    g.part("body", "collar", (-2.5, 21.2, -2), (5, 1, 4), "collar", inflate=0.35)
    g.part("body", "clasp", (-0.5, 20.6, -2.6), (1, 1, 0), "clasp")
    # 肩から前に垂らす淡い青の肩掛け（銀の星）
    g.part("body", "stole_r", (-2.6, 3, -3.35), (1, 18, 0), "stole")
    g.part("body", "stole_l", (1.6, 3, -3.35), (1, 18, 0), "stole")
    # 司祭服の裾（上と、少し広い下の2段。足元まで）
    g.part("robe", "robe_mid", (-4, 6, -2.6), (8, 7, 5), "robe")
    g.part("robe", "robe_low", (-4.5, 0.5, -3), (9, 6, 6), "robe_hem")
    for side, x in (("right", -6.5), ("left", 3.5)):
        g.part(side + "_arm", "arm_" + side, (x, 12, -1.5), (3, 10, 3), "sleeve")
        g.part(side + "_arm", "bell_sleeve_" + side, (x - 0.5, 12, -2), (4, 4, 4), "bell_sleeve")
        g.part(side + "_arm", "hand_" + side, (x + 0.5, 10.5, -1), (2, 2, 2), "hand")
    # 小さな銀の鈴（持ち手と、淡い青のひも）
    g.part("bell", "bell_body", (-1, 10.6, -6.8), (2, 2, 2), "bell_metal", inflate=0.15)
    g.part("bell", "bell_top", (-0.5, 12.6, -6.3), (1, 2, 1), "bell_metal")
    g.part("bell", "bell_ribbon", (-0.5, 9.6, -6.9), (1, 1, 0), "stole")
    # 頭（9）と髪：分けた前髪・顔のわきの長い髪・背中まで流れる後ろ髪
    g.part("head", "head", (-4.5, 22, -4.5), (9, 9, 9), "head")
    g.part("head", "hair_shell", (-4.5, 23, -4.5), (9, 8, 9), "hair_shell", inflate=0.5)
    for k, (x0, w, bottom, z0) in enumerate(((-4.5, 2, 25.6, -5.3), (-2.5, 2, 26.6, -5.2), (0.5, 2, 26.6, -5.2),
                                            (2.5, 2, 25.6, -5.3))):
        g.part("head", "bangs_%d" % k, (x0, bottom, z0), (w, 4, 1), "bangs")
    g.part("head", "lock_r", (-5.4, 16.2, -4.2), (1, 12, 2), "lock")
    g.part("head", "lock_l", (4.4, 16.2, -4.2), (1, 12, 2), "lock")
    g.part("hair_back", "hair_back", (-4.5, 13, 4.3), (9, 17, 2), "hair_long")
    g.part("head", "hair_pin", (3.0, 28.4, -5.0), (2, 2, 0), "pin", rot=(0, 0, 15), pivot=(4, 29.4, -5.0))
    g.part("eyelids", "eyelids", (-3.5, 23.5, -4.0), (7, 3, 0), "eyelids")
    g.pose = {"right_arm": (-36, -30, 0), "left_arm": (-36, 30, 0), "hair_back": (6, 0, 0)}
    g.pack()

    P = SERENA_PAL
    face = FaceDesign(18, 18, P["skin"], SERENA_FACE_PAL, eye_row=9, eye_col=3, eyes=SERENA_EYES, mouths=SERENA_MOUTHS,
                      brows=SERENA_BROWS, mouth_row=15, brow_row=6, blush=(P["blush"], 14, (2,)))

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 5:
                        return rgba(hair_color(ctx, P, 21))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    return rgba(c if c is not None else vshade(ctx, P["skin"], 0.0, 0.06))
                if d == "up" or (d in ("left", "right", "back") and y > 23.6):
                    return rgba(hair_color(ctx, P, 21))
                return rgba(vshade(ctx, P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 9, "closed")
                return rgba(c if c is not None else P["skin"])
            if m == "hair_shell":
                if (d == "front" and y < 29.2) or d == "down":
                    return CLEAR
                if d in ("left", "right") and y < 25.0 and ctx.p[2] < -2.4:
                    return CLEAR
                return rgba(hair_color(ctx, P, 23, ring_y=29.4))
            if m == "bangs":
                if d != "up" and ctx.j >= ctx.H - 1 and (ctx.i == 0 or ctx.i == ctx.W - 1):
                    return CLEAR
                return rgba(hair_color(ctx, P, 25, ring_y=29.4))
            if m in ("lock", "hair_long"):
                if d not in ("up", "down") and ctx.j >= ctx.H - 2 and (ctx.i % 4 in (0, 3)):
                    return CLEAR       # 毛先をばらす
                return rgba(hair_color(ctx, P, 27))
            if m == "pin":
                cx, cy = ctx.i - 1.5, ctx.j - 1.5
                return rgba(P["silver"]) if abs(cx) + abs(cy) <= 2.0 else CLEAR
            if m == "robe_top":
                if d == "front" and abs(ctx.i - (ctx.W - 1) / 2) < 0.8 and ctx.j < ctx.H - 4:
                    return rgba(P["blue"])      # 前の合わせの線
                return rgba(cloth(ctx, P["robe"], 0.02))
            if m == "robe_bust":
                return rgba(grain(ctx, vshade(ctx, P["robe"], 0.06, 0.14), 0.02))
            if m == "sash":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(vshade(ctx, P["blue"] if ctx.j not in (0, ctx.H - 1) else P["blue_d"]))
            if m == "collar":
                return rgba(vshade(ctx, P["blue"]))
            if m == "clasp":
                return rgba(P["silver"])
            if m == "stole":
                if star_at(ctx, cell=4, salt=31, keep=0.7):
                    return rgba(P["silver"])
                return rgba(P["blue_d"] if ctx.i in (0, ctx.W - 1) else P["blue"])
            if m in ("robe", "robe_hem"):
                if d == "up":
                    return CLEAR if m == "robe_hem" else rgba(P["robe_d"])
                if d == "down":
                    return rgba(P["robe_d"])
                if m == "robe_hem" and ctx.j >= ctx.H - 3:
                    return rgba(P["blue"] if ctx.j < ctx.H - 1 else P["blue_d"])
                if star_at(ctx, cell=7, salt=41, keep=0.35):
                    return rgba(P["silver_d"])
                fold = (ctx.i % 5 == 0)
                return rgba(cloth(ctx, P["robe_d"] if fold else P["robe"], 0.02))
            if m == "sleeve":
                return rgba(cloth(ctx, P["robe"], 0.02))
            if m == "bell_sleeve":
                if d == "up":
                    return CLEAR
                if d == "down":
                    return rgba(P["robe_d"])
                if ctx.j >= ctx.H - 2:
                    return rgba(P["blue"])
                return rgba(cloth(ctx, P["robe"], 0.02))
            if m == "hand":
                return rgba(vshade(ctx, P["skin"], 0.0, 0.08))
            if m == "leg":
                return rgba(vshade(ctx, P["shoe"] if y < 1.5 else P["robe_d"]))
            if m == "bell_metal":
                k = 1.15 if ctx.i % 3 == 0 else 1.0
                return rgba(shade(vshade(ctx, P["silver"], 0.1, 0.25), k))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "smile"]


# ================================================================ ガルド

GARD_PAL = {
    "skin": hexc("DCA985"), "skin_d": hexc("BF8B66"), "stubble": hexc("B48B72"), "scar": hexc("EBC2B0"),
    "hair": hexc("8E9095"), "hair_d": hexc("67696E"), "hair_l": hexc("B8BABE"),
    "crimson": hexc("8E1D26"), "crimson_d": hexc("681219"), "crimson_l": hexc("B03A3F"),
    "iron": hexc("3A3D43"), "iron_d": hexc("24262A"), "iron_l": hexc("6E737C"),
    "tunic": hexc("33363C"), "tunic_d": hexc("24262B"), "leather": hexc("4A3324"), "leather_l": hexc("6A4A33"),
    "gold": hexc("C9A24E"), "raven": hexc("15161A"), "steel": hexc("B9C0C8"),
}

RAVEN = [". . . . . . . . R R . .",
         ". . . . . . . R R R R .",
         ". . . . . . R R R . . R",
         "R . . . . R R R R . . .",
         "R R . . R R R R R . . .",
         ". R R R R R R R . . . .",
         ". . R R R R R R . . . .",
         ". . . R R R R . . . . .",
         ". . . . R . R . . . . .",
         ". . . R . . . R . . . ."]


def gard():
    """ガルド・レイヴン（54歳）。灰色の短髪、左頰に古傷、黒鉄の片腕甲（右腕）。旧騎士団の深紅のマント。モチーフはワタリガラス。

    かっこよく：肩幅を広く（胴9）、深紅の肩掛けと高い襟、背中にワタリガラスの紋のマント、右腕に黒鉄の籠手と肩当て、
    鋭い目と太い眉、立体の鼻。左の腰にドランの打った剣（左手をつか頭にのせる）【補完】。
    """
    g = NpcModel("gard", 128, 128)
    g.face_center_y = 28
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), "root")
    g.bone("head", (0, 24, 0), "body")
    g.bone("eyelids", (0, 27, -4), "head")
    g.bone("cloak", (0, 23.5, 3), "body")
    g.bone("sword", (5.2, 12.5, -1), "body")
    g.bone("right_arm", (-6.5, 22, 0), "body")
    g.bone("left_arm", (6.5, 22, 0), "body")
    g.bone("right_leg", (-2, 12, 0), "root")
    g.bone("left_leg", (2, 12, 0), "root")
    for side, x in (("right", -4), ("left", 0)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -2), (4, 12, 4), "trousers")
        g.part(side + "_leg", "boot_" + side, (x, 0, -2), (4, 7, 4), "boot", inflate=0.3)
        g.part(side + "_leg", "knee_" + side, (x + 0.5, 6.2, -2.6), (3, 2, 1), "knee_guard", inflate=0.15)
    g.part("body", "torso", (-4.5, 12, -2.5), (9, 12, 5), "tunic")
    g.part("body", "belt", (-4.5, 12.5, -2.5), (9, 2, 5), "belt", inflate=0.3)
    g.part("body", "buckle", (-1, 12.6, -3.1), (2, 2, 0), "buckle")
    g.part("body", "mantle", (-5.5, 21, -3), (11, 3, 6), "mantle", inflate=0.2)
    g.part("body", "collar_back", (-4, 23.5, 1.6), (8, 3, 1), "collar")
    g.part("body", "collar_r", (-4.6, 23.5, -1.2), (1, 2, 3), "collar")
    g.part("body", "collar_l", (3.6, 23.5, -1.2), (1, 2, 3), "collar")
    g.part("body", "clasp", (-1, 22.3, -3.35), (2, 2, 0), "raven_clasp")
    g.part("cloak", "cloak", (-5, 5, 2.7), (10, 18, 1), "cloak")
    g.part("cloak", "cloak_low", (-5.5, 3.5, 3.0), (11, 4, 1), "cloak_hem")
    # 右腕：黒鉄の籠手（前腕）・手甲・肩当て
    g.part("right_arm", "arm_r", (-8.5, 12, -2), (4, 12, 4), "sleeve")
    g.part("right_arm", "vambrace", (-8.5, 12.5, -2), (4, 6, 4), "iron", inflate=0.35)
    g.part("right_arm", "gauntlet", (-8.5, 10.6, -2), (4, 2, 4), "iron_dark", inflate=0.45)
    g.part("right_arm", "pauldron", (-9.2, 20.6, -2.6), (5, 3, 5), "pauldron", inflate=0.2)
    g.part("left_arm", "arm_l", (4.5, 12, -2), (4, 12, 4), "sleeve")
    g.part("left_arm", "glove_l", (4.5, 10.6, -2), (4, 3, 4), "glove", inflate=0.25)
    # 左の腰の剣（ドランの打った剣）
    g.part("sword", "scabbard", (4.7, 2.5, -1.5), (1, 10, 2), "scabbard")
    g.part("sword", "guard", (3.7, 12.5, -1.5), (3, 1, 2), "sword_guard")
    g.part("sword", "grip", (4.7, 13.5, -1), (1, 3, 1), "grip")
    g.part("sword", "pommel", (4.2, 16.5, -1.5), (2, 1, 2), "sword_guard")
    # 頭（8）：短い灰色の髪（上が立つ）・立体の鼻
    g.part("head", "head", (-4, 24, -4), (8, 8, 8), "head")
    g.part("head", "hair_cap", (-4, 29, -4), (8, 3, 8), "hair_cap", inflate=0.4)
    g.part("head", "hair_back", (-4, 25.5, -1), (8, 4, 5), "hair_side", inflate=0.3)
    for k, (x0, h, z0) in enumerate(((-3.5, 2, -4.6), (-1.5, 3, -4.4), (0.5, 2, -4.6), (2.0, 2, -4.3))):
        g.part("head", "spike_%d" % k, (x0, 31.4, z0), (2, h, 2), "hair_spike")
    g.part("head", "nose", (-0.5, 26.4, -4.7), (1, 2, 1), "nose")
    g.part("eyelids", "eyelids", (-3, 26, -3.5), (6, 2, 0), "eyelids")
    g.pose = {"left_arm": (-22, 12, -6), "sword": (28, 0, -6), "cloak": (6, 0, 0), "right_arm": (0, 0, 4)}
    g.pack()

    P = GARD_PAL
    fp = male_face_pal(P["skin_d"], hexc("2B2220"), hexc("ECE8E2"), hexc("5B6B7B"), hexc("1E2430"), hexc("5E6064"),
                       hexc("8E5A4C"), {"s": P["scar"]})
    eyes = {"normal": ["L L L L", "w i p L", ". S S ."],
            "stern": ["L L L L", "w p i L", ". S S ."],
            "closed": [". . . .", "L L L L", ". . . ."]}
    brows = {"normal": ["B B . .", ". B B B"], "stern": [". . . .", "B B B B"]}
    mouths = {"normal": ["m m m m"], "stern": [". m m .", "m . . m"]}

    def scar(i, j, expr):
        if (i, j) in ((13, 8), (13, 9), (12, 10), (12, 11), (11, 12)):
            return P["scar"]
        return None

    face = FaceDesign(16, 16, P["skin"], fp, eye_row=8, eye_col=2, eyes=eyes, mouths=mouths, brows=brows,
                      mouth_row=13, brow_row=6, extras=[scar])

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 3:
                        return rgba(hair_color(ctx, P, 41))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    if c is not None:
                        return rgba(c)
                    base = P["skin"]
                    if ctx.j >= 12 and hash01(ctx.i, ctx.j, 3) < 0.35:
                        base = mix(P["skin"], P["stubble"], 0.55)
                    return rgba(vshade(ctx, base, 0.0, 0.08))
                if d == "up" or (d in ("left", "right", "back") and y > 29.0):
                    return rgba(hair_color(ctx, P, 41))
                return rgba(vshade(ctx, P["skin_d"] if d == "down" else P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 8, "closed")
                return rgba(c if c is not None else P["skin"])
            if m in ("hair_cap", "hair_spike", "hair_side"):
                if m == "hair_cap" and d == "front" and y < 30.6:
                    return CLEAR
                if m == "hair_cap" and d == "down":
                    return CLEAR
                if m == "hair_side" and d in ("front", "down"):
                    return CLEAR
                return rgba(hair_color(ctx, P, 43))
            if m == "nose":
                return rgba(vshade(ctx, P["skin"] if d != "down" else P["skin_d"], 0.04, 0.12))
            if m == "tunic":
                if d == "front" and 4 <= ctx.i <= ctx.W - 5 and ctx.j in (5, 6) and False:
                    return rgba(P["iron_l"])
                return rgba(cloth(ctx, P["tunic"], 0.04))
            if m == "belt":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["leather"], 0.06))
            if m == "buckle":
                e = ctx.edge_dist()
                return rgba(P["gold"]) if min(e.values()) == 0 else rgba(P["leather"])
            if m == "mantle":
                if d == "down":
                    return rgba(P["crimson_d"])
                c = P["crimson_l"] if d == "up" else P["crimson"]
                if d != "up" and ctx.j >= ctx.H - 2:
                    c = P["gold"] if ctx.j == ctx.H - 2 else P["crimson_d"]
                return rgba(grain(ctx, vshade(ctx, c, 0.04, 0.12), 0.04))
            if m == "collar":
                return rgba(cloth(ctx, P["crimson_d"], 0.04))
            if m == "raven_clasp":
                e = ctx.edge_dist()
                return rgba(P["iron_l"] if min(e.values()) == 0 else P["raven"])
            if m in ("cloak", "cloak_hem"):
                if d == "back" and m == "cloak":
                    c = sprite_at(RAVEN, ctx, ctx.W // 2 - 6, 6, {"R": P["raven"]})
                    if c is not None:
                        return rgba(c)
                if d in ("back", "front", "left", "right") and m == "cloak_hem" and ctx.j >= ctx.H - 2:
                    return rgba(P["gold"] if ctx.j == ctx.H - 2 else P["crimson_d"])
                if d == "down":
                    return rgba(P["crimson_d"])
                return rgba(grain(ctx, vshade(ctx, P["crimson"], 0.06, 0.16), 0.05))
            if m == "sleeve":
                if y < 12.6:
                    return rgba(vshade(ctx, P["skin"]))
                return rgba(cloth(ctx, P["tunic"], 0.04))
            if m in ("iron", "iron_dark", "pauldron", "knee_guard"):
                if d == "up" and m == "iron":
                    return CLEAR
                base = P["iron_d"] if m == "iron_dark" else P["iron"]
                e = ctx.edge_dist()
                if min(e.values()) == 0:
                    base = P["iron_l"]
                if m == "pauldron" and d == "up" and 3 <= ctx.i <= 6 and 3 <= ctx.j <= 6:
                    base = P["raven"]
                if m == "iron" and d in ("front", "back", "left", "right") and ctx.j % 4 == 0:
                    base = P["iron_d"]          # 板の重なり
                return rgba(grain(ctx, vshade(ctx, base, 0.12, 0.2), 0.05))
            if m == "glove":
                if d == "up":
                    return CLEAR
                return rgba(cloth(ctx, P["leather"], 0.05))
            if m == "trousers":
                return rgba(cloth(ctx, P["tunic_d"], 0.04))
            if m == "boot":
                if d == "up":
                    return CLEAR
                return rgba(grain(ctx, vshade(ctx, P["iron_d"] if ctx.j < ctx.H - 2 else P["raven"], 0.08, 0.1), 0.04))
            if m == "scabbard":
                e = ctx.edge_dist()
                return rgba(P["gold"] if e["bottom"] <= 1 or e["top"] <= 0 else cloth(ctx, P["leather"], 0.04))
            if m == "sword_guard":
                return rgba(vshade(ctx, P["steel"], 0.1, 0.25))
            if m == "grip":
                return rgba(P["leather_l"] if ctx.j % 2 == 0 else P["leather"])
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "stern"]


# ================================================================ ドラン

DORAN_PAL = {
    "skin": hexc("D99A78"), "skin_d": hexc("BA7B5A"), "skin_l": hexc("E8B293"),
    "hair": hexc("9C4A28"), "hair_d": hexc("74341A"), "hair_l": hexc("C26A3C"),
    "shirt": hexc("2E2A28"), "shirt_d": hexc("1F1C1B"), "apron": hexc("8A5A34"), "apron_d": hexc("6B4426"),
    "burn": hexc("2A1C14"), "copper": hexc("B8743E"), "copper_l": hexc("D99A5E"), "orange": hexc("E08A3A"),
    "band": hexc("5A3B26"), "iron": hexc("50545A"), "iron_l": hexc("80868E"), "wood": hexc("7A5636"),
    "trousers": hexc("3B3532"), "boot": hexc("2B2522"),
}


def doran():
    """ドラン・ベルク（56歳）。筋骨たくましい大男。赤茶のひげを編み込み、焦げ跡だらけの革エプロン。右手の古い金槌には
    二つの名前が刻まれている。色：赤銅・煤黒・橙。

    かっこよく：胴を広く厚く（10×13×6）、太い腕（5）。まくった袖と太い前腕。立体のひげ（口ひげと、銅の輪をつけた2本の三つ編み）と
    鼻。革のエプロンは体から浮かせて、肩ひもと銅の鋲。頭に革の鉢巻き【補完】。
    """
    g = NpcModel("doran", 128, 128)
    g.face_center_y = 28
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 11, 0), "root")
    g.bone("head", (0, 24, 0), "body")
    g.bone("eyelids", (0, 28, -4), "head")
    g.bone("beard", (0, 25, -4), "head")
    g.bone("right_arm", (-7.5, 22.5, 0), "body")
    g.bone("left_arm", (7.5, 22.5, 0), "body")
    g.bone("hammer", (-7.5, 11.5, -0.5), "right_arm")
    g.bone("right_leg", (-2.5, 11, 0), "root")
    g.bone("left_leg", (2.5, 11, 0), "root")
    for side, x in (("right", -5), ("left", 0)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -2.5), (5, 11, 5), "trousers")
        g.part(side + "_leg", "boot_" + side, (x, 0, -2.5), (5, 4, 5), "boot", inflate=0.35)
    g.part("body", "torso", (-5, 11, -3), (10, 13, 6), "shirt")
    g.part("body", "belt", (-5, 11.5, -3), (10, 2, 6), "belt", inflate=0.3)
    g.part("body", "apron", (-4.5, 5, -3.7), (9, 17, 1), "apron")
    g.part("body", "strap_r", (-4, 21.5, -3.4), (1, 3, 7), "strap")
    g.part("body", "strap_l", (3, 21.5, -3.4), (1, 3, 7), "strap")
    for side, x in (("right", -10), ("left", 5)):
        g.part(side + "_arm", "arm_" + side, (x, 11, -2.5), (5, 13, 5), "arm")
        g.part(side + "_arm", "roll_" + side, (x, 18.2, -2.5), (5, 2, 5), "roll", inflate=0.4)
        g.part(side + "_arm", "wrap_" + side, (x, 11.6, -2.5), (5, 2, 5), "wrap", inflate=0.25)
    # 金槌（右手。頭を下に）：柄・頭・銅の帯
    g.part("hammer", "handle", (-8, 4, -1), (1, 9, 1), "handle")
    g.part("hammer", "hammer_head", (-9.5, 2, -2), (4, 2, 3), "hammer_head")
    # 頭（8）：赤茶の髪と革の鉢巻き・ひげ・鼻
    g.part("head", "head", (-4, 24, -4), (8, 8, 8), "head")
    g.part("head", "hair_cap", (-4, 29.5, -4), (8, 3, 8), "hair_cap", inflate=0.4)
    g.part("head", "headband", (-4, 29.0, -4), (8, 1, 8), "headband", inflate=0.55)
    g.part("head", "brows", (-3.5, 28.6, -4.55), (7, 1, 1), "brows")
    g.part("head", "nose", (-1, 26.3, -4.9), (2, 2, 1), "nose")
    g.part("beard", "mustache", (-3.5, 25.2, -4.85), (7, 1, 1), "beard", inflate=0.1)
    g.part("beard", "beard", (-4, 20.6, -5.4), (8, 5, 2), "beard")
    g.part("beard", "braid_r", (-3, 17.0, -5.0), (1, 4, 1), "braid", inflate=0.15)
    g.part("beard", "braid_l", (2, 17.0, -5.0), (1, 4, 1), "braid", inflate=0.15)
    g.part("eyelids", "eyelids", (-3, 27, -3.5), (6, 2, 0), "eyelids")
    g.pose = {"right_arm": (-6, 0, 8), "left_arm": (0, 0, -8)}
    g.pack()

    P = DORAN_PAL
    fp = male_face_pal(P["skin_d"], hexc("2A1E18"), hexc("F1ECE4"), hexc("5C3A22"), hexc("1C130E"), P["hair"],
                       hexc("8A4E3C"))
    eyes = {"normal": ["L L L", "w p i", ". S ."], "grin": [". . .", "L L L", ". . ."],
            "closed": [". . .", "L L L", ". . ."]}
    face = FaceDesign(16, 16, P["skin"], fp, eye_row=8, eye_col=3, eyes=eyes, mouths={"normal": ["m m"]},
                      brows={"normal": [". . ."]}, mouth_row=13, brow_row=6)

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 2:
                        return rgba(hair_color(ctx, P, 51))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    if c is not None:
                        return rgba(c)
                    base = P["skin"]
                    if ctx.j == 11 and ctx.i in (3, 4, 11, 12):
                        base = P["skin_l"]          # 赤いほお
                    return rgba(vshade(ctx, base, 0.0, 0.08))
                if d == "up" or (d == "back" and y > 25.0) or (d in ("left", "right") and y > 27.0 and ctx.p[2] > -1.5):
                    return rgba(hair_color(ctx, P, 51))
                return rgba(vshade(ctx, P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 6, "closed")
                return rgba(c if c is not None else P["skin"])
            if m == "hair_cap":
                if (d == "front" and y < 31.4) or d == "down":
                    return CLEAR
                return rgba(hair_color(ctx, P, 53))
            if m == "headband":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["band"], 0.05))
            if m == "brows":
                if d == "down":
                    return rgba(P["hair_d"])
                if d == "front" and ctx.i in (6, 7):
                    return CLEAR
                return rgba(hair_color(ctx, P, 55))
            if m == "nose":
                return rgba(vshade(ctx, P["skin_l"] if d == "front" else P["skin"], 0.04, 0.12))
            if m in ("beard", "braid"):
                if m == "beard" and d == "front" and ctx.el.name == "beard" and expr == "grin" and ctx.j in (1, 2) \
                        and 4 <= ctx.i <= ctx.W - 5:
                    return rgba(hexc("F4EFE6") if ctx.j == 1 else hexc("5A2A20"))   # 笑った口（歯）
                if m == "braid" and ctx.j % 3 == 2 and d != "up":
                    return rgba(P["copper_l"])             # 銅の輪
                return rgba(hair_color(ctx, P, 57))
            if m == "shirt":
                return rgba(cloth(ctx, P["shirt"], 0.05))
            if m == "belt":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["band"], 0.05))
            if m == "apron":
                if d == "up":
                    return rgba(P["apron_d"])
                # 焦げ跡（暗いしみと、少し明るいふち）
                k = hash01(ctx.i // 2, ctx.j // 2, 61)
                if k < 0.05:
                    return rgba(P["burn"])
                if k < 0.11:
                    return rgba(mix(P["apron_d"], P["burn"], 0.4))
                e = ctx.edge_dist()
                if d == "front" and min(e["left"], e["right"]) == 0:
                    return rgba(P["apron_d"])
                if d == "front" and ctx.j < 3 and ctx.i in (2, ctx.W - 3):
                    return rgba(P["copper_l"])             # 銅の鋲
                return rgba(grain(ctx, vshade(ctx, P["apron"], 0.05, 0.14), 0.06))
            if m == "strap":
                return rgba(cloth(ctx, P["apron_d"], 0.05))
            if m == "arm":
                if y < 18.4:
                    c = P["skin"] if hash01(ctx.i, ctx.j, 5) > 0.12 else mix(P["skin"], P["skin_d"], 0.6)   # 太い前腕（毛）
                    return rgba(vshade(ctx, c, 0.04, 0.1))
                return rgba(cloth(ctx, P["shirt"], 0.05))
            if m == "roll":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["shirt_d"], 0.05))
            if m == "wrap":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(P["band"] if ctx.j % 2 == 0 else P["apron_d"])
            if m == "trousers":
                return rgba(cloth(ctx, P["trousers"], 0.04))
            if m == "boot":
                if d == "up":
                    return CLEAR
                if d in ("front",) and ctx.j == 2:
                    return rgba(P["copper"])
                return rgba(cloth(ctx, P["boot"], 0.05))
            if m == "handle":
                return rgba(grain(ctx, vshade(ctx, P["wood"]), 0.08))
            if m == "hammer_head":
                if d in ("front", "back") and ctx.j in (1, 2) and 1 <= ctx.i <= ctx.W - 2:
                    return rgba(P["iron_l"] if ctx.i % 2 else P["iron"])     # 刻まれた二つの名前
                if d in ("left", "right") and ctx.i in (0, ctx.W - 1):
                    return rgba(P["copper"])
                return rgba(grain(ctx, vshade(ctx, P["iron"], 0.12, 0.22), 0.06))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "grin"]


# ================================================================ バルド

BARD_PAL = {
    "skin": hexc("D8AB8C"), "skin_d": hexc("B98C6E"), "stubble": hexc("A49488"),
    "hair": hexc("5E6066"), "hair_d": hexc("44464B"), "hair_l": hexc("A3A5AA"), "white": hexc("D4D4D2"),
    "coat": hexc("2F3B4E"), "coat_d": hexc("222B3A"), "coat_l": hexc("44536A"), "trim": hexc("A9ADB2"),
    "trim_d": hexc("7E8289"), "cape": hexc("5D6064"), "cape_d": hexc("44474B"), "scarf": hexc("6E2E2A"),
    "leather": hexc("4A3628"), "boot": hexc("2A2522"), "shaft": hexc("6C4E33"), "steel": hexc("C3C9D0"),
    "steel_d": hexc("8C949D"), "crest": hexc("9FC6D8"),
}


def bard():
    """門番バルド（50代）。第七次遠征の数少ない生き残りで、ガルドの元部下。無口。「……行くのか」。

    見た目は8章に細かい決まりがないので【補完】：古い騎士団の上着（色あせた紺に、くすんだ銀のふち。胸に結晶騎士団の色あせた紋）、
    片方の肩に古びた短い肩掛け、首に暗い赤の襟巻き。白の混じった灰色の髪をうしろへなでつけ、無精ひげ。塔の門を守る長い槍を
    まっすぐに立てて持つ。
    """
    g = NpcModel("bard", 128, 128)
    g.face_center_y = 28
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), "root")
    g.bone("head", (0, 24, 0), "body")
    g.bone("eyelids", (0, 27, -4), "head")
    g.bone("cape", (2.5, 23.5, 2.5), "body")
    g.bone("spear", (-6.5, 12, -3.5), "body")
    g.bone("right_arm", (-6, 22, 0), "body")
    g.bone("left_arm", (6, 22, 0), "body")
    g.bone("right_leg", (-2, 12, 0), "root")
    g.bone("left_leg", (2, 12, 0), "root")
    for side, x in (("right", -4), ("left", 0)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -2), (4, 12, 4), "trousers")
        g.part(side + "_leg", "boot_" + side, (x, 0, -2), (4, 6, 4), "boot", inflate=0.3)
    g.part("body", "torso", (-4, 12, -2), (8, 12, 4), "coat")
    g.part("body", "coat_skirt", (-4.5, 5.5, -2.6), (9, 7, 5), "coat_skirt")
    g.part("body", "belt", (-4, 12.5, -2), (8, 2, 4), "belt", inflate=0.35)
    g.part("body", "crest", (0.8, 19.2, -2.1), (2, 2, 0), "crest")
    g.part("body", "scarf", (-4, 22.4, -2), (8, 2, 4), "scarf", inflate=0.5)
    g.part("body", "scarf_tail", (-2.6, 17.6, -2.9), (2, 5, 0), "scarf", rot=(0, 0, 8), pivot=(-1.6, 22.6, -2.9))
    g.part("cape", "cape", (-0.5, 12.5, 2.3), (5, 11, 1), "cape", inflate=0.15)
    g.part("cape", "cape_shoulder", (0.5, 22.2, -2.4), (5, 2, 5), "cape", inflate=0.25)
    for side, x in (("right", -8), ("left", 4)):
        g.part(side + "_arm", "arm_" + side, (x, 12, -2), (4, 12, 4), "sleeve")
        g.part(side + "_arm", "cuff_" + side, (x, 12.6, -2), (4, 2, 4), "cuff", inflate=0.3)
        g.part(side + "_arm", "glove_" + side, (x, 10.6, -2), (4, 2, 4), "glove", inflate=0.2)
    # 長い槍（地面から頭の上まで。まっすぐに立てる）
    g.part("spear", "shaft", (-7, 0, -4), (1, 31, 1), "shaft")
    g.part("spear", "socket", (-7, 30.5, -4), (1, 2, 1), "steel", inflate=0.2)
    g.part("spear", "blade", (-7.5, 32.5, -4), (2, 5, 1), "blade")
    g.part("spear", "wing", (-9, 30.6, -4), (5, 1, 1), "steel")
    g.part("head", "head", (-4, 24, -4), (8, 8, 8), "head")
    g.part("head", "hair_cap", (-4, 29, -4), (8, 3, 8), "hair_cap", inflate=0.35)
    g.part("head", "hair_back", (-4, 25.4, 0.5), (8, 4, 4), "hair_back", inflate=0.3)
    g.part("head", "nose", (-0.5, 26.2, -4.6), (1, 2, 1), "nose")
    g.part("eyelids", "eyelids", (-3, 26, -3.5), (6, 2, 0), "eyelids")
    g.pose = {"right_arm": (-24, -8, 0), "cape": (0, 0, 0)}
    g.pack()

    P = BARD_PAL
    fp = male_face_pal(P["skin_d"], hexc("26201E"), hexc("E6E1DA"), hexc("6B6A5E"), hexc("1F1E1A"), hexc("6E7075"),
                       hexc("80584C"))
    eyes = {"normal": ["L L L L", "S w p L", ". S S ."], "closed": [". . . .", "L L L L", ". . . ."]}
    brows = {"normal": ["B B B .", ". . B B"]}
    face = FaceDesign(16, 16, P["skin"], fp, eye_row=8, eye_col=2, eyes=eyes, mouths={"normal": ["m m m m"]},
                      brows=brows, mouth_row=13, brow_row=6)

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 3:
                        return rgba(hair_color(ctx, P, 71))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    if c is not None:
                        return rgba(c)
                    base = P["skin"]
                    if ctx.j >= 11 and (ctx.i * 3 + ctx.j) % 3 == 0:
                        base = P["stubble"]       # 白の混じった無精ひげ
                    if ctx.j == 11 and ctx.i in (2, 3, 12, 13):
                        base = P["skin_d"]        # 目の下のしわ
                    return rgba(vshade(ctx, base, 0.0, 0.08))
                if d == "up" or (d in ("left", "right", "back") and y > 29.0):
                    return rgba(hair_color(ctx, P, 71))
                return rgba(vshade(ctx, P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 8, "closed")
                return rgba(c if c is not None else P["skin"])
            if m in ("hair_cap", "hair_back"):
                if (m == "hair_cap" and d == "front" and y < 30.8) or d == "down":
                    return CLEAR
                if m == "hair_back" and d == "front":
                    return CLEAR
                c = hair_color(ctx, P, 73)
                if hash01(ctx.i // 2, 9, 77) > 0.86:
                    c = mix(c, P["white"], 0.45)     # 白い筋
                return rgba(c)
            if m == "nose":
                return rgba(vshade(ctx, P["skin"], 0.04, 0.12))
            if m in ("coat", "coat_skirt"):
                if m == "coat_skirt" and d == "up":
                    return CLEAR
                if d == "front" and abs(ctx.i - (ctx.W - 1) / 2) < 1.0:
                    return rgba(P["trim"] if m == "coat" else P["coat_d"])     # 前の合わせ（銀のふち）と裾の割れ
                e = ctx.edge_dist()
                if m == "coat_skirt" and d != "down" and e["bottom"] <= 1:
                    return rgba(P["trim_d"])
                worn = hash01(ctx.i // 2, ctx.j // 2, 79) < 0.08
                return rgba(cloth(ctx, mix(P["coat"], P["coat_l"], 0.5) if worn else P["coat"], 0.04))
            if m == "belt":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["leather"], 0.05))
            if m == "crest":
                cx, cy = abs(ctx.i - 1.5), abs(ctx.j - 1.5)
                if cx + cy <= 2.0:
                    return rgba(mix(P["crest"], P["coat"], 0.35))    # 色あせた結晶の紋
                return CLEAR
            if m == "scarf":
                if d in ("up", "down") and ctx.el.name == "scarf":
                    return rgba(shade(P["scarf"], 0.85))
                return rgba(cloth(ctx, P["scarf"], 0.06))
            if m == "cape":
                if d == "down":
                    return rgba(P["cape_d"])
                e = ctx.edge_dist()
                if ctx.el.name == "cape" and d != "up" and e["bottom"] <= 1 and (ctx.i % 3 == 0):
                    return CLEAR                       # 古びて裂けた裾
                return rgba(cloth(ctx, P["cape"], 0.07))
            if m == "sleeve":
                return rgba(cloth(ctx, P["coat"], 0.05))
            if m == "cuff":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(vshade(ctx, P["trim_d"] if ctx.j == 0 else P["coat_d"]))
            if m == "glove":
                if d == "up":
                    return CLEAR
                return rgba(cloth(ctx, P["leather"], 0.05))
            if m == "trousers":
                return rgba(cloth(ctx, P["coat_d"], 0.04))
            if m == "boot":
                if d == "up":
                    return CLEAR
                return rgba(cloth(ctx, P["boot"], 0.05))
            if m == "shaft":
                return rgba(grain(ctx, vshade(ctx, P["shaft"]), 0.08))
            if m == "steel":
                return rgba(vshade(ctx, P["steel_d"], 0.1, 0.2))
            if m == "blade":
                # 槍の穂先：上ほど細く
                if d in ("front", "back"):
                    half = (ctx.W / 2) * (1 - ctx.j / ctx.H * 0.0) if ctx.j > 3 else (ctx.W / 2) * (ctx.j + 1) / 4
                    if abs(ctx.i - (ctx.W - 1) / 2) > half:
                        return CLEAR
                return rgba(vshade(ctx, P["steel"] if ctx.i < ctx.W // 2 else P["steel_d"], 0.1, 0.1))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal"]


# ================================================================ ノア

NOA_PAL = {
    "skin": hexc("F6E2D6"), "skin_d": hexc("E6C9B8"), "blush": hexc("F0B4B0"),
    "hair": hexc("D6D9E0"), "hair_d": hexc("A9AEBA"), "hair_l": hexc("F5F6F9"),
    "navy": hexc("1F2A4E"), "navy_d": hexc("141B35"), "navy_l": hexc("34447C"),
    "gold": hexc("E8D08A"), "gold_d": hexc("BFA45E"),
    "white": hexc("ECEEF2"), "white_d": hexc("C9CDD6"),
    "tights": hexc("2A2F45"), "boot": hexc("3A3F5A"), "boot_d": hexc("262A3E"),
}

NOA_FACE_PAL = {
    "L": hexc("4A4C5A"), "H": hexc("FFFFFF"), "a": hexc("5E6B8E"), "b": hexc("8190B4"), "c": hexc("B9C3DC"),
    "p": hexc("232A40"), "w": hexc("FDFDFB"), "B": hexc("B4B8C2"), "m": hexc("B87A80"), "M": hexc("E3A1A4"),
}

# 片方の瞳だけ淡い金色（Phase 0-A 8章）：左目（見る人の右）の瞳の色を、金に置きかえる
NOA_GOLD_EYE = {hexc("5E6B8E"): hexc("B8913E"), hexc("8190B4"): hexc("E2C36C"), hexc("B9C3DC"): hexc("F6E6B0")}

NOA_EYES = {
    "normal": ["L L L L",
               "L H a a",
               "w a p b",
               "w b b c",
               ". c c ."],
    "smile": [". . . .",
              ". L L .",
              "L . . L",
              ". . . .",
              ". . . ."],
    "sad": [". . . .",
            "L L L L",
            "w H p b",
            "w b b c",
            ". c c ."],
    "closed": [". . . .",
               ". . . .",
               ". . . .",
               "L L L L",
               ". L L ."],
}
NOA_BROWS = {"normal": [". . . .", ". B B ."], "smile": [". . . .", ". B B ."], "sad": [". . B B", "B B . ."]}
NOA_MOUTHS = {"normal": ["m m"], "smile": ["m . . m", ". m m ."], "sad": [". m m .", "m . . m"]}

# 外套のふちの「結び目に似た文字の模様」（淡金。4マスごとにくり返す）【補完】
KNOT_GLYPH = [". g .",
              "g . g",
              ". g ."]


def noa():
    """ノア（13歳ほど）。白に近い灰色の髪、片方の瞳だけ淡い金色。どの時代とも違う外套に、結び目に似た文字の模様。
    色：白灰・淡金・夜空の紺。

    かわいらしく、少し不思議に：小柄（頭9・胴8・足9）。肩までの白灰の髪と長い横髪、夜空の紺の外套（高い襟・背に下ろしたフード・
    前を開けた裾）に淡金の結び目の模様、中は白い服。足音がしない子なので、靴はやわらかい布の短靴【補完】。
    """
    g = NpcModel("noa", 128, 128)
    g.face_center_y = 22
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 9, 0), "root")
    g.bone("head", (0, 17, 0), "body")
    g.bone("eyelids", (0, 20, -4.5), "head")
    g.bone("hair_back", (0, 24, 4.4), "head")
    g.bone("coat", (0, 9, 0), "body")
    g.bone("hood", (0, 16, 2.2), "body")
    g.bone("right_arm", (-5, 16, 0), "body")
    g.bone("left_arm", (5, 16, 0), "body")
    g.bone("right_leg", (-1.5, 9, 0), "root")
    g.bone("left_leg", (1.5, 9, 0), "root")
    for side, x in (("right", -3), ("left", 0)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -1.5), (3, 9, 3), "tights")
        g.part(side + "_leg", "boot_" + side, (x, 0, -1.5), (3, 3, 3), "boot", inflate=0.3)
    # 胴（白い服）と、夜空の紺の外套（前をV字に開ける）・高い襟・胸の結び目の留め具
    g.part("body", "torso", (-3.5, 9, -2), (7, 8, 4), "tunic")
    g.part("body", "coat_top", (-3.5, 9.5, -2), (7, 7, 4), "coat", inflate=0.3)
    g.part("body", "collar", (-3, 16.0, -2.2), (6, 2, 4), "coat_collar", inflate=0.4)
    g.part("body", "knot", (-0.5, 14.6, -2.75), (1, 1, 1), "gold_knot")
    g.part("hood", "hood", (-3, 13.0, 1.9), (6, 3, 2), "hood", inflate=0.2)
    # 外套の裾（ひざの上まで。前は開けて、白い服が見える）
    g.part("coat", "coat_low", (-4, 3.5, -2.6), (8, 6, 5), "coat_hem")
    # 腕（細い3。紺の袖と、淡金の袖口）
    for side, x in (("right", -6.5), ("left", 3.5)):
        g.part(side + "_arm", "arm_" + side, (x, 8, -1.5), (3, 8, 3), "sleeve")
        g.part(side + "_arm", "cuff_" + side, (x, 8.6, -1.5), (3, 1, 3), "cuff", inflate=0.35)
        g.part(side + "_arm", "hand_" + side, (x + 0.5, 6.8, -1), (2, 2, 2), "hand")
    # 頭（9）と髪：不ぞろいの前髪・長い横髪・肩までの後ろ髪
    g.part("head", "head", (-4.5, 17, -4.5), (9, 9, 9), "head")
    g.part("head", "hair_shell", (-4.5, 18, -4.5), (9, 8, 9), "hair_shell", inflate=0.5)
    for k, (x0, w, bottom, z0) in enumerate(((-4.5, 2, 22.0, -5.3), (-2.5, 2, 22.8, -5.15), (-0.5, 1, 21.6, -5.3),
                                            (0.5, 2, 22.6, -5.15), (2.5, 2, 22.2, -5.3))):
        g.part("head", "bangs_%d" % k, (x0, bottom, z0), (w, max(3, round(25.6 - bottom)), 1), "bangs")
    g.part("head", "crown", (-3.5, 26.2, -3.5), (7, 1, 7), "crown")
    g.part("head", "lock_r", (-5.35, 15.4, -4.3), (1, 8, 2), "lock")
    g.part("head", "lock_l", (4.35, 15.4, -4.3), (1, 8, 2), "lock")
    g.part("hair_back", "hair_back", (-4.5, 15.6, 4.3), (9, 7, 2), "hair_long")
    g.part("eyelids", "eyelids", (-3.5, 18.5, -4.0), (7, 3, 0), "eyelids")
    g.pose = {"right_arm": (-6, 0, 3), "left_arm": (-14, 0, -3), "hair_back": (5, 0, 0), "hood": (8, 0, 0)}
    g.pack()

    P = NOA_PAL
    face = FaceDesign(18, 18, P["skin"], NOA_FACE_PAL, eye_row=9, eye_col=3, eyes=NOA_EYES, mouths=NOA_MOUTHS,
                      brows=NOA_BROWS, mouth_row=15, brow_row=6, blush=(P["blush"], 14, (2,)))

    def knot_band(ctx, rows_from_bottom=3):
        """下の端の帯に、結び目の模様。模様の上なら True。"""
        j = ctx.j - (ctx.H - rows_from_bottom)
        if j < 0 or j >= 3:
            return False
        line = KNOT_GLYPH[j].split()
        return line[ctx.i % 4] == "g" if ctx.i % 4 < 3 else False

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 5:
                        return rgba(hair_color(ctx, P, 71))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    if c is not None and ctx.i >= 9:
                        c = NOA_GOLD_EYE.get(c, c)
                    return rgba(c if c is not None else vshade(ctx, P["skin"], 0.0, 0.06))
                if d == "up" or (d in ("left", "right", "back") and y > 18.6):
                    return rgba(hair_color(ctx, P, 71))
                return rgba(vshade(ctx, P["skin_d"] if d == "down" else P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 9, "closed")
                return rgba(c if c is not None else P["skin"])
            if m == "hair_shell":
                if d == "front" and y < 24.4:
                    return CLEAR
                if d == "down":
                    return CLEAR
                if d in ("left", "right") and y < 20.2 and ctx.p[2] < -2.6:
                    return CLEAR
                return rgba(hair_color(ctx, P, 73, ring_y=24.6))
            if m == "bangs":
                if d in ("front", "left", "right", "back"):
                    if ctx.j >= ctx.H - 1 and (ctx.i == 0 or ctx.i == ctx.W - 1):
                        return CLEAR
                if d == "down":
                    return rgba(shade(P["hair_d"], 0.9))
                return rgba(hair_color(ctx, P, 75, ring_y=24.6))
            if m == "lock":
                if d in ("front", "back", "left", "right"):
                    e = ctx.edge_dist()
                    if ctx.j >= ctx.H - 2 and (e["left"] == 0 or e["right"] == 0):
                        return CLEAR
                return rgba(hair_color(ctx, P, 77))
            if m == "crown":
                return rgba(hair_color(ctx, P, 79)) if d != "down" else CLEAR
            if m == "hair_long":
                if d == "down":
                    return rgba(P["hair_d"])
                if d in ("front", "back") and ctx.j >= ctx.H - 1 and ctx.i % 3 == 1:
                    return CLEAR                                   # 毛先を不ぞろいに
                return rgba(hair_color(ctx, P, 81))
            if m == "tunic":
                return rgba(cloth(ctx, P["white"], 0.03))
            if m in ("coat", "coat_hem"):
                if d == "front":
                    cx = abs(ctx.i - (ctx.W - 1) / 2)
                    if m == "coat":
                        open_w = max(0.0, (ctx.H * 0.7 - ctx.j) * 0.5)
                    else:
                        open_w = 1.6                               # 裾は前を開ける
                    if cx < open_w:
                        return CLEAR
                    if cx < open_w + 1.2:
                        return rgba(P["gold_d"] if m == "coat_hem" else P["gold"])   # 前のふちの淡金の線
                if d == "up":
                    return CLEAR if m == "coat_hem" else rgba(P["navy_d"])
                if d == "down":
                    return rgba(P["navy_d"])
                if m == "coat_hem" and knot_band(ctx):
                    return rgba(P["gold"])
                if m == "coat_hem" and ctx.j == ctx.H - 4:
                    return rgba(P["gold_d"])
                c = P["navy_l"] if (d in ("left", "right") and edge_band(ctx, 1)) else P["navy"]
                if star_at(ctx, cell=7, salt=83, keep=0.18):
                    c = P["navy_l"]                                # 夜空のような、かすかな光の粒
                return rgba(grain(ctx, vshade(ctx, c, 0.05, 0.12), 0.03))
            if m == "coat_collar":
                if d == "down":
                    return CLEAR
                if d == "up":
                    return rgba(P["navy_d"])
                return rgba(P["gold"] if ctx.j == 0 else vshade(ctx, P["navy"]))
            if m == "gold_knot":
                return rgba(vshade(ctx, P["gold"], 0.1, 0.1))
            if m == "hood":
                if d == "front":
                    return rgba(P["navy_d"])
                c = P["gold"] if ctx.j == ctx.H - 1 and d == "back" else P["navy"]
                return rgba(grain(ctx, vshade(ctx, c), 0.03))
            if m == "sleeve":
                if y < 7.2:
                    return rgba(vshade(ctx, P["skin"], 0.0, 0.08))
                if d == "up":
                    return rgba(P["navy"])
                return rgba(grain(ctx, vshade(ctx, P["navy"]), 0.03))
            if m == "cuff":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(P["gold"] if ctx.j == 0 else P["gold_d"])
            if m == "hand":
                return rgba(vshade(ctx, P["skin"], 0.0, 0.08))
            if m == "tights":
                return rgba(grain(ctx, vshade(ctx, P["tights"]), 0.03))
            if m == "boot":
                if d == "up":
                    return CLEAR
                if d == "down":
                    return rgba(P["boot_d"])
                return rgba(cloth(ctx, P["boot"] if ctx.j < ctx.H - 1 else P["boot_d"], 0.04))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "smile", "sad"]


# ================================================================ エルネスト

ERNESTO_PAL = {
    "skin": hexc("E9C4A8"), "skin_d": hexc("CFA184"), "skin_l": hexc("F3D6C0"), "wrinkle": hexc("C89A7E"),
    "hair": hexc("EDEDEA"), "hair_d": hexc("BDBDB6"), "hair_l": hexc("FFFFFF"),
    "coat": hexc("E4D9BC"), "coat_d": hexc("C6B892"), "coat_l": hexc("F2EBD6"),
    "brown": hexc("5A3B26"), "brown_d": hexc("3E281A"), "brown_l": hexc("7A5338"),
    "brass": hexc("C9A24A"), "brass_d": hexc("9C7A30"), "brass_l": hexc("ECCB74"),
    "vest": hexc("6B4A30"), "shirt": hexc("F2EEE6"),
    "trousers": hexc("4C3B2E"), "boot": hexc("3A2A1E"),
    "stone": hexc("8E8C86"), "stone_d": hexc("6A6862"), "glyph": hexc("8FD3FF"),
}


def ernesto():
    """エルネスト（78歳）。白いもじゃもじゃの髪と眉。丸眼鏡を三枚重ね。ポケットだらけの外套に、石板の欠片。
    色：生成り・焦げ茶・真鍮。

    好奇心のかたまりの、やせた老学者：もじゃもじゃの白髪（頭の上と横と後ろに、ふくらんだ房）、太い白い眉、大きな鼻。
    丸眼鏡の三枚重ねは、跳ね上げ式の真鍮の枠【補完】（1枚は目の前、2枚は額へ跳ね上げた形）。生成りの外套は、胸と腰と裾に
    焦げ茶のポケット（真鍮のボタン）。中は焦げ茶のベストと白いシャツ。左手に、古代文字が淡く光る石板の欠片【補完】。
    """
    g = NpcModel("ernesto", 128, 128)
    g.face_center_y = 27
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 11, 0), "root")
    g.bone("head", (0, 23, 0), "body")
    g.bone("eyelids", (0, 26.5, -4), "head")
    g.bone("glasses_up", (0, 29.0, -4.8), "head")
    g.bone("coat", (0, 11, 0), "body")
    g.bone("right_arm", (-6, 21.5, 0), "body")
    g.bone("left_arm", (6, 21.5, 0), "body")
    g.bone("tablet", (6, 10, -2), "left_arm")
    g.bone("right_leg", (-2, 11, 0), "root")
    g.bone("left_leg", (2, 11, 0), "root")
    for side, x in (("right", -4), ("left", 0)):
        g.part(side + "_leg", "leg_" + side, (x, 0, -2), (4, 11, 4), "trousers")
        g.part(side + "_leg", "boot_" + side, (x, 0, -2), (4, 3, 4), "boot", inflate=0.3)
    # 胴（白いシャツに焦げ茶のベスト）と、生成りの外套（前を開ける）・襟・ポケット
    g.part("body", "torso", (-4, 11, -2.5), (8, 12, 5), "vest")
    g.part("body", "coat_top", (-4, 11.5, -2.5), (8, 11, 5), "coat", inflate=0.35)
    g.part("body", "lapels", (-3.5, 21.4, -2.6), (7, 2, 5), "coat_collar", inflate=0.45)
    g.part("body", "pocket_chest", (1.4, 18.0, -3.0), (2, 2, 0), "pocket")
    g.part("body", "pocket_r", (-4.2, 13.0, -3.0), (3, 3, 0), "pocket")
    g.part("body", "pocket_l", (1.2, 13.0, -3.0), (3, 3, 0), "pocket")
    g.part("coat", "coat_low", (-4.5, 3.0, -3.0), (9, 9, 6), "coat_hem")
    g.part("coat", "pocket_low_r", (-4.4, 6.0, -3.1), (3, 3, 0), "pocket")
    g.part("coat", "pocket_low_l", (1.4, 6.0, -3.1), (3, 3, 0), "pocket")
    g.part("coat", "fragment", (2.0, 8.4, -3.5), (1, 1, 1), "stone_bit", rot=(0, 0, 20), pivot=(2.5, 8.9, -3.0))
    g.part("body", "fragment2", (1.7, 19.6, -3.3), (1, 1, 1), "stone_bit", rot=(0, 0, -15), pivot=(2.2, 20.1, -3.0))
    # 腕（やせた4。生成りの袖と焦げ茶の袖口）
    for side, x in (("right", -8), ("left", 4)):
        g.part(side + "_arm", "arm_" + side, (x, 11, -2), (4, 11, 4), "sleeve")
        g.part(side + "_arm", "cuff_" + side, (x, 11.2, -2), (4, 2, 4), "cuff", inflate=0.35)
        g.part(side + "_arm", "hand_" + side, (x + 0.5, 9.4, -1.5), (3, 2, 3), "hand")
    # 左手の石板の欠片（古代文字が淡く光る）
    g.part("tablet", "tablet", (4.6, 6.8, -4.4), (3, 4, 1), "tablet", rot=(-6, -12, 4), pivot=(6.1, 8.8, -3.9))
    # 頭（8）：もじゃもじゃの白髪・太い眉・大きな鼻
    g.part("head", "head", (-4, 23, -4), (8, 8, 8), "head")
    g.part("head", "hair_cap", (-4, 28.6, -4), (8, 3, 8), "hair_cap", inflate=0.8)
    g.part("head", "tuft_r", (-5.4, 24.6, -2.6), (2, 4, 5), "tuft", inflate=0.5)
    g.part("head", "tuft_l", (3.4, 24.6, -2.6), (2, 4, 5), "tuft", inflate=0.5)
    g.part("head", "tuft_back", (-4, 24.2, 2.6), (8, 5, 2), "tuft", inflate=0.5)
    for k, (x0, y0, z0) in enumerate(((-3.6, 31.0, -3.0), (0.4, 31.3, -1.4), (-1.6, 31.1, 1.0), (1.6, 30.8, 1.8))):
        g.part("head", "tuft_top_%d" % k, (x0, y0, z0), (3, 2, 3), "tuft", inflate=0.25)
    g.part("head", "brow_r", (-3.7, 28.0, -4.6), (3, 1, 1), "brows", inflate=0.3)
    g.part("head", "brow_l", (0.7, 28.0, -4.6), (3, 1, 1), "brows", inflate=0.3)
    g.part("head", "nose", (-0.5, 25.6, -4.9), (1, 2, 1), "nose", inflate=0.15)
    # 丸眼鏡の三枚重ね：目の前の1枚（真鍮の丸い枠）と、その上に重ねて押し上げた2枚（額と、髪の生えぎわ）
    g.part("head", "lens_r", (-3.6, 25.0, -4.75), (3, 3, 0), "lens")
    g.part("head", "lens_l", (0.6, 25.0, -4.75), (3, 3, 0), "lens")
    g.part("head", "bridge", (-0.6, 26.6, -4.75), (1, 1, 0), "brass_bit")
    for k, (y0, z0, tilt) in enumerate(((28.3, -5.0, -10), (30.4, -5.35, -24))):
        g.part("glasses_up", "lens_up_r%d" % k, (-3.6, y0, z0), (3, 3, 0), "lens", rot=(tilt, 0, 0), pivot=(0, y0, z0))
        g.part("glasses_up", "lens_up_l%d" % k, (0.6, y0, z0), (3, 3, 0), "lens", rot=(tilt, 0, 0), pivot=(0, y0, z0))
        g.part("glasses_up", "bridge_up%d" % k, (-0.6, y0 + 1.6, z0), (1, 1, 0), "brass_bit", rot=(tilt, 0, 0), pivot=(0, y0, z0))
    g.part("eyelids", "eyelids", (-3, 26, -3.5), (6, 2, 0), "eyelids")
    g.pose = {"left_arm": (-42, 10, -4), "right_arm": (-8, -4, 4), "tablet": (0, 0, 0)}
    g.pack()

    P = ERNESTO_PAL
    fp = male_face_pal(P["wrinkle"], hexc("3A2E28"), hexc("F2EEE6"), hexc("6B8A5E"), hexc("1E2A1A"), P["hair_d"],
                       hexc("9A6A5A"))
    eyes = {"normal": ["L L L", "w p i", ". S ."],
            "excited": ["w w w", "w p w", ". w ."],
            "worried": [". L L", "w p i", ". S ."],
            "closed": [". . .", "L L L", ". . ."]}
    mouths = {"normal": ["m m"], "excited": ["m . . m", ". m m ."], "worried": [". m m .", "m . . m"]}

    def wrinkles(i, j, expr):
        # 目じりと額の小じわ、ほうれい線
        if (j == 4 and 4 <= i <= 11 and i % 3 != 0) or (j == 12 and i in (4, 11)) or (j == 13 and i in (4, 11)):
            return P["wrinkle"]
        return None

    face = FaceDesign(16, 16, P["skin"], fp, eye_row=8, eye_col=3, eyes=eyes, mouths=mouths, brows={"normal": [". . ."]},
                      mouth_row=13, brow_row=6, extras=[wrinkles])

    def painter(expr):
        def paint(ctx):
            m = ctx.el.mat
            d = ctx.face.d
            y = ctx.p[1]
            if m == "head":
                if d == "front":
                    if ctx.j <= 1:
                        return rgba(hair_color(ctx, P, 91))
                    c = face.pixel(ctx.i, ctx.j, expr)
                    if c is not None:
                        return rgba(c)
                    return rgba(vshade(ctx, P["skin"], 0.0, 0.08))
                if d == "up" or (d == "back" and y > 24.6) or (d in ("left", "right") and y > 27.0 and ctx.p[2] > -1.5):
                    return rgba(hair_color(ctx, P, 91))
                return rgba(vshade(ctx, P["skin"], 0.0, 0.1))
            if m == "eyelids":
                c = face.pixel(ctx.i + 2, ctx.j + 8, "closed")
                return rgba(c if c is not None else P["skin"])
            if m in ("hair_cap", "tuft"):
                if m == "hair_cap" and ((d == "front" and y < 30.6) or d == "down"):
                    return CLEAR
                if d == "down" and m == "tuft":
                    return rgba(P["hair_d"])
                # もじゃもじゃ：明るい毛・影の毛・抜けた所（すき間）をまだらに
                k = hash01(ctx.i, ctx.j, name_seed(ctx.el.name), 93)
                if k < 0.07 and ctx.edge_dist()["top"] == 0:
                    return CLEAR
                c = P["hair_l"] if k > 0.72 else (P["hair_d"] if k < 0.25 else P["hair"])
                return rgba(vshade(ctx, c, 0.04, 0.1))
            if m == "brows":
                if d == "down":
                    return rgba(P["hair_d"])
                k = hash01(ctx.i, ctx.j, name_seed(ctx.el.name), 95)
                return rgba(P["hair_l"] if k > 0.5 else P["hair"])
            if m == "nose":
                return rgba(vshade(ctx, P["skin_l"] if d == "front" else P["skin"], 0.04, 0.14))
            if m == "lens":
                # 真鍮の丸い枠（角を落とした輪）。中は透かす（ガラスの光を1つ）
                e = ctx.edge_dist()
                corner = (e["left"] == 0 or e["right"] == 0) and (e["top"] == 0 or e["bottom"] == 0)
                if corner:
                    return CLEAR
                if min(e["left"], e["right"], e["top"], e["bottom"]) == 0:
                    return rgba(P["brass_l"] if e["top"] == 0 else P["brass"])
                if ctx.i == 1 and ctx.j == 1:
                    return rgba(hexc("FFFFFF"), 170)
                return CLEAR
            if m == "brass_bit":
                return rgba(P["brass"])
            if m == "vest":
                if y > 21.0 and d == "front" and 6 <= ctx.i <= 9:
                    return rgba(P["shirt"])                        # えり元の白いシャツ
                if d == "front" and ctx.i in (7, 8) and ctx.j % 4 == 1 and y < 21:
                    return rgba(P["brass"])                        # ベストのボタン
                return rgba(cloth(ctx, P["vest"], 0.04))
            if m in ("coat", "coat_hem"):
                if d == "front":
                    cx = abs(ctx.i - (ctx.W - 1) / 2)
                    open_w = 3.2 if m == "coat" else 2.4
                    if cx < open_w:
                        return CLEAR
                    if cx < open_w + 1.0:
                        return rgba(P["coat_d"])
                if d == "up":
                    return CLEAR if m == "coat_hem" else rgba(P["coat_d"])
                if d == "down":
                    return rgba(P["coat_d"])
                if m == "coat_hem" and ctx.j >= ctx.H - 2:
                    return rgba(mix(P["coat_d"], P["brown"], 0.35))   # 裾のすす汚れ
                if hash01(ctx.i // 3, ctx.j // 3, 97) < 0.05:
                    return rgba(mix(P["coat"], P["brown_l"], 0.25))   # 古いしみ
                return rgba(grain(ctx, vshade(ctx, P["coat"], 0.05, 0.12), 0.04))
            if m == "coat_collar":
                if d == "down":
                    return CLEAR
                return rgba(cloth(ctx, P["coat_l"], 0.03))
            if m == "pocket":
                e = ctx.edge_dist()
                if e["top"] == 0:
                    return rgba(P["brown_d"])                       # ふた
                if e["top"] == 1 and abs(ctx.i - (ctx.W - 1) / 2) < 1:
                    return rgba(P["brass_l"])                       # 真鍮のボタン
                return rgba(cloth(ctx, P["brown"], 0.05))
            if m == "stone_bit":
                return rgba(P["glyph"] if (d == "front" and ctx.i == ctx.j) else vshade(ctx, P["stone"]))
            if m == "sleeve":
                if d == "up":
                    return rgba(P["coat_d"])
                return rgba(grain(ctx, vshade(ctx, P["coat"], 0.05, 0.12), 0.04))
            if m == "cuff":
                if d in ("up", "down"):
                    return CLEAR
                return rgba(cloth(ctx, P["brown"], 0.04))
            if m == "hand":
                c = P["skin"] if hash01(ctx.i, ctx.j, 99) > 0.15 else P["skin_d"]   # しみのある、年をとった手
                return rgba(vshade(ctx, c, 0.04, 0.1))
            if m == "tablet":
                if d == "front":
                    # 古代文字の行（淡く光る水色）
                    if ctx.j in (1, 3, 5) and 1 <= ctx.i <= ctx.W - 2 and hash01(ctx.i, ctx.j, 101) > 0.3:
                        return rgba(P["glyph"])
                    e = ctx.edge_dist()
                    if e["right"] == 0 and e["top"] <= 1:
                        return CLEAR                                # 欠けた角
                return rgba(grain(ctx, vshade(ctx, P["stone"] if d != "down" else P["stone_d"], 0.05, 0.12), 0.08))
            if m == "trousers":
                return rgba(cloth(ctx, P["trousers"], 0.04))
            if m == "boot":
                if d == "up":
                    return CLEAR
                return rgba(cloth(ctx, P["boot"], 0.05))
            return rgba((255, 0, 255))
        return paint

    return g, painter, ["normal", "excited", "worried"]


# ================================================================ 書き出し

CHARACTERS = {
    "lilia": lilia,
    "serena": serena,
    "gard": gard,
    "doran": doran,
    "bard": bard,
    "noa": noa,
    "ernesto": ernesto,
}


def build(name, repo, preview_dir=None):
    g, painter, exprs = CHARACTERS[name]()
    textures = {}
    for expr in exprs:
        textures[expr] = paint_hd(g, painter(expr))
    from npc_anims import animations_for
    save_model(repo, g, textures, animations_for(name, g))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        preview_sheet(g, textures["normal"], os.path.join(preview_dir, name + ".png"))
        for expr in exprs[1:]:
            preview_sheet(g, textures[expr], os.path.join(preview_dir, name + "_" + expr + ".png"))
    return g


def main():
    args = [a for a in sys.argv[1:]]
    repo = "."
    preview = None
    only = None
    i = 0
    while i < len(args):
        if args[i] == "--preview":
            preview = args[i + 1]
            i += 2
        elif args[i] == "--only":
            only = args[i + 1]
            i += 2
        else:
            repo = args[i]
            i += 1
    for name in CHARACTERS:
        if only and name != only:
            continue
        build(name, repo, preview)
        print("built", name)


if __name__ == "__main__":
    main()
