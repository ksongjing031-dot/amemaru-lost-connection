"""霜銀シリーズの武器と盾（第2章・6階からの職業ごとの装備。コモン）。11月26日に形から作り直した。

  python3 tools/artgen/make_gear_frost_weapons.py <リポジトリ> [確認画像の出力先]

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」【補完】。
前は流紋シリーズの武器の形を霜銀の色に塗り直していた。凍結した村の冬の道具として作り直した：
  剣    ＝まっすぐな霜銀の刃と氷の稜・雪の結晶の枝の鍔・つららの柄頭・毛皮の輪
  大剣  ＝幅の広い霜銀の刃に、両のふちのつららの歯（氷）・氷の稜・毛皮を巻いた太い鍔
  杖    ＝白樺の柄の先に、氷の八つの腕の結晶（雪の結晶）と霜銀の輪・赤い編みの握り
  鈴杖  ＝白樺の柄の先に、霜銀のアーチと、長さのちがう3本の氷の鈴（つらら）・てっぺんに小さな雪の結晶
  弓    ＝白樺の腕・霜銀の巻いた弭と外へ向く氷のとげ・灰の毛皮の握り・雪の結晶の玉
  双短剣＝霜銀の刃に氷のふち・雪の結晶の枝の鍔・灰の毛皮の握り・雪の結晶の柄頭
  籠手  ＝氷青の編みのミトンに、白い毛皮の口と、霜銀のこぶしの板・氷の鋲
  盾    ＝六角の白樺の盾に霜銀のふちと、氷の八つの腕の結晶の飾り
持ち方と大きさは流紋シリーズと同じ（握りの位置・長さ）。色と塗り方は frost_common.py、持ち物の絵は frost_icons.py。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter  # noqa: E402
import weapons  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from frost_common import FrostPaint  # noqa: E402
from make_gear_ancient_stone import weapon_display, SHIELD_FIRST, SHIELD_BLOCK, SHIELD_GUI  # noqa: E402
from make_gear_phase6 import display as phase6_display, BOW_STRING_X  # noqa: E402

CREDIT = "AMEMARU: 霜銀シリーズ（11月26日に形から作り直した。tools/artgen/make_gear_frost_weapons.py で生成）"
NOSNOW = {"no_snow": True}


class FrostWeaponPainter(FrostPaint, RyumonPaint, Painter):
    pass


def star8(m, name, cx, cy, arm, z0, z1, mat, width=0.8, tips=None):
    """氷の八つの腕の結晶（z の面に立つ）：縦・横・ななめ2本の棒。tips を渡すと、縦と横の先に小さな粒。"""
    h = width / 2.0
    m.box(name + "_v", (cx - h, cy - arm, z0), (cx + h, cy + arm, z1), mat)
    m.box(name + "_h", (cx - arm, cy - h, z0), (cx + arm, cy + h, z1), mat)
    d = arm * 0.8
    m.box(name + "_d1", (cx - h, cy - d, z0 + 0.02), (cx + h, cy + d, z1 - 0.02), mat, rot=((cx, cy, (z0 + z1) / 2), "z", 45))
    m.box(name + "_d2", (cx - h, cy - d, z0 + 0.02), (cx + h, cy + d, z1 - 0.02), mat, rot=((cx, cy, (z0 + z1) / 2), "z", -45))
    if tips:
        for k, (x, y) in enumerate(((cx, cy + arm), (cx, cy - arm), (cx - arm, cy), (cx + arm, cy))):
            m.box(f"{name}_tip_{k}", (x - 0.5, y - 0.5, z0 - 0.1), (x + 0.5, y + 0.5, z1 + 0.1), tips)


# ================================================================ 剣（ナイト）

def build_sword():
    m = JavaModel("frost_silver_sword", 64, 64)
    # 柄頭：霜銀の玉と、下へ垂れるつらら。握り：暗い革に、上に灰の毛皮の輪
    m.box("pommel", (7.2, -3.4, 7.2), (8.8, -1.2, 8.8), "fsilver", tags=NOSNOW)
    m.box("pommel_icicle", (7.6, -4.6, 7.6), (8.4, -3.4, 8.4), "icec")
    m.box("grip", (7.4, -1.2, 7.4), (8.6, 3.6, 8.6), "leather_dark", tags={"wrap": True})
    m.box("fur_ring", (7.05, 3.6, 7.05), (8.95, 4.4, 8.95), "greyfur")
    # 鍔：横の棒と、両はしで上と下へ分かれる雪の結晶の枝・まん中の氷の結晶
    m.box("guard", (4.6, 4.4, 7.4), (11.4, 5.4, 8.6), "fsilver", tags=NOSNOW)
    for side, x in (("l", 4.6), ("r", 10.6)):
        m.box(f"branch_up_{side}", (x, 5.4, 7.6), (x + 0.8, 6.8, 8.4), "fsilver", tags=NOSNOW)
        m.box(f"branch_dn_{side}", (x, 3.0, 7.6), (x + 0.8, 4.4, 8.4), "fsilver", tags=NOSNOW)
        m.box(f"branch_tip_{side}", (x - 0.6 if side == "l" else x + 0.8, 4.5, 7.7), (x if side == "l" else x + 1.4, 5.3, 8.3), "icec")
    m.box("guard_gem", (7.4, 4.5, 7.1), (8.6, 5.7, 8.9), "icec")
    # 刃：まっすぐな幅の広い霜銀（両のふちは研いだ明るい線）・まん中の氷の稜・根元の小さな氷のとげ
    m.box("blade", (6.5, 5.4, 7.6), (9.5, 19.5, 8.4), "fsilver", tags={"edge": "both", "no_snow": True})
    m.box("ridge", (7.6, 6.0, 7.45), (8.4, 18.5, 8.55), "icec")
    m.box("barb_l", (5.8, 6.2, 7.7), (6.5, 7.6, 8.3), "icec")
    m.box("barb_r", (9.5, 6.2, 7.7), (10.2, 7.6, 8.3), "icec")
    m.box("tip_0", (7.0, 19.5, 7.7), (9.0, 20.75, 8.3), "fsilver", tags={"edge": "both", "no_snow": True})
    m.box("tip_1", (7.45, 20.75, 7.75), (8.55, 21.75, 8.25), "fsilver", tags=NOSNOW)
    m.box("tip_2", (7.8, 21.75, 7.8), (8.2, 22.5, 8.2), "icec")
    return m


# ================================================================ 大剣（ナイト）

def build_greatsword():
    m = JavaModel("frost_silver_greatsword", 64, 64)
    m.box("pommel", (6.9, -4.75, 6.9), (9.1, -2.25, 9.1), "fsilver", tags={"lames": 2, "no_snow": True})
    m.box("pommel_cap", (7.4, -5.5, 7.4), (8.6, -4.75, 8.6), "icec")
    m.box("grip", (7.4, -2.25, 7.4), (8.6, 7.4, 8.6), "leather_dark", tags={"wrap": True})
    m.box("fur_wrap", (6.7, 6.8, 6.7), (9.3, 8.0, 9.3), "snowfur")
    # 鍔：太い霜銀の横木。両はしは下へ曲がる。まん中に氷の結晶
    m.box("guard", (4.0, 8, 6.9), (12, 10.25, 9.1), "fsilver", tags={"lames": 2, "no_snow": True})
    m.box("guard_end_l", (3.3, 7.25, 7.1), (4.0, 10.25, 8.9), "fsilver", tags=NOSNOW)
    m.box("guard_end_r", (12, 7.25, 7.1), (12.7, 10.25, 8.9), "fsilver", tags=NOSNOW)
    m.box("guard_gem", (7.25, 8.4, 6.6), (8.75, 9.9, 9.4), "icec")
    # 刃：幅の広い霜銀。まん中に氷の稜。両のふちに、3ピクセルごとのつららの歯（氷）
    m.box("blade", (5.6, 10.25, 7.3), (10.4, 27.0, 8.7), "fsilver", tags={"edge": "both", "no_snow": True})
    m.box("ridge", (7.5, 11.0, 7.15), (8.5, 26.0, 8.85), "icec")
    for k, y in enumerate((12.0, 15.0, 18.0, 21.0, 24.0)):
        m.box(f"tooth_l_{k}", (4.9, y, 7.55), (5.6, y + 1.4, 8.45), "icec")
        m.box(f"tooth_r_{k}", (10.4, y + 1.5, 7.55), (11.1, y + 2.9, 8.45), "icec")
    m.box("tip_0", (6.3, 27.0, 7.4), (9.7, 28.5, 8.6), "fsilver", tags={"edge": "both", "no_snow": True})
    m.box("tip_1", (7.1, 28.5, 7.5), (8.9, 29.75, 8.5), "fsilver", tags=NOSNOW)
    m.box("tip_2", (7.7, 29.75, 7.7), (8.3, 30.5, 8.3), "icec")
    return m


# ================================================================ 杖（ウィザード）

def build_staff():
    m = JavaModel("frost_silver_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6.5, 8.5), "fsilver", tags=NOSNOW)
    m.box("spike_tip", (7.75, -8.75, 7.75), (8.25, -8, 8.25), "icec")
    m.box("shaft", (7.6, -6.5, 7.6), (8.4, 15.5, 8.4), "birch", tags={"grain": "y"})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "berry", tags={"knit": True})
    for k, y in enumerate((-5.0, 6.0, 11.0)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "fsilver", tags=NOSNOW)
    # 先：霜銀の輪の上に、氷の八つの腕の結晶（雪の結晶）。腕の先に小さな粒、まん中に大きな氷
    m.box("collar", (6.9, 15.5, 6.9), (9.1, 16.5, 9.1), "fsilver", tags=NOSNOW)
    star8(m, "flake", 8.0, 20.5, 3.6, 7.6, 8.4, "icec", width=0.8, tips="fsilver")
    m.box("heart", (7.1, 19.6, 7.1), (8.9, 21.4, 8.9), "icec")
    return m


# ================================================================ 鈴杖（ヒーラー）

def build_bell_staff():
    m = JavaModel("frost_silver_bell_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6, 8.5), "fsilver", tags=NOSNOW)
    m.box("shaft", (7.6, -6, 7.6), (8.4, 16.5, 8.4), "birch", tags={"grain": "y"})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "frostblue", tags={"knit": True})
    for k, y in enumerate((-4.0, 7.0, 12.0)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "fsilver", tags=NOSNOW)
    # 先：霜銀のアーチ（上の横木と左右の柱）・長さのちがう3本の氷の鈴（つらら）・てっぺんの小さな雪の結晶
    m.box("arch_top", (4.75, 21.5, 7.5), (11.25, 22.5, 8.5), "fsilver", tags=NOSNOW)
    m.box("arch_l", (4.75, 16.5, 7.5), (5.75, 21.5, 8.5), "fsilver", tags=NOSNOW)
    m.box("arch_r", (10.25, 16.5, 7.5), (11.25, 21.5, 8.5), "fsilver", tags=NOSNOW)
    m.box("arch_base", (5.25, 16.5, 7.6), (10.75, 17.25, 8.4), "fsilver", tags=NOSNOW)
    for k, (x, y0) in enumerate(((6.6, 18.75), (7.6, 17.6), (8.6, 18.25))):
        m.box(f"chain_{k}", (x + 0.25, 20.75, 7.85), (x + 0.55, 21.5, 8.15), "fsilver", tags=NOSNOW)
        m.box(f"chime_{k}", (x, y0, 7.6), (x + 0.8, 20.75, 8.4), "icec")
    star8(m, "top_flake", 8.0, 24.0, 1.4, 7.7, 8.3, "icec", width=0.6)
    return m


# ================================================================ 弓（アーチャー）

def build_bow():
    """腕と弦の形は流紋の弓と同じ（持ち方の決まり。弦は +x の側）。腕は白樺、弭は霜銀の巻き、弦は霜銀の糸。"""
    m = JavaModel("frost_silver_bow", 64, 64)
    m.box("grip", (7.0, 0, 7.2), (8.8, 5, 8.8), "greyfur")
    m.box("grip_plate", (8.8, 0.5, 7.0), (9.6, 4.5, 9.0), "fsilver", tags={"lames": 2, "no_snow": True})
    m.box("rest_gem", (9.6, 2.0, 7.5), (10.2, 3.0, 8.5), "icec")
    segs = ((5, 9, 8.0), (9, 13, 8.6), (13, 16.5, 9.4), (16.5, 19, 10.3))
    for k, (y0, y1, x) in enumerate(segs):
        m.box(f"upper_{k}", (x - 0.6, y0, 7.4), (x + 0.6, y1, 8.6), "birch", tags={"grain": "y"})
        m.box(f"lower_{k}", (x - 0.6, -y1 + 5, 7.4), (x + 0.6, -y0 + 5, 8.6), "birch", tags={"grain": "y"})
    # 弭：霜銀の巻いた先（内へ曲がる）と、外へ向く氷のとげ
    m.box("tip_top", (10.0, 19, 7.6), (11.2, 20.2, 8.4), "fsilver", tags=NOSNOW)
    m.box("curl_top", (9.0, 20.2, 7.65), (10.6, 21.0, 8.35), "fsilver", tags=NOSNOW)
    m.box("tip_bottom", (10.0, -15.2, 7.6), (11.2, -14, 8.4), "fsilver", tags=NOSNOW)
    m.box("curl_bottom", (9.0, -16.0, 7.65), (10.6, -15.2, 8.35), "fsilver", tags=NOSNOW)
    m.box("spike_top", (7.6, 16.0, 7.75), (8.6, 16.8, 8.25), "icec")
    m.box("spike_bottom", (7.6, -11.8, 7.75), (8.6, -11.0, 8.25), "icec")
    m.box("string", (BOW_STRING_X[0], -14.9, 7.88), (BOW_STRING_X[1], 19.9, 8.12), "fsilver", tags=NOSNOW)
    for k, y in enumerate((9.0, -4.0)):
        m.box(f"band_{k}", (8.0, y, 7.2), (10.0, y + 1.2, 8.8), "fsilver", tags=NOSNOW)
    return m


# ================================================================ 双短剣（ローグ）

def build_daggers():
    """大きさは流紋の双短剣と同じ。霜銀の刃に氷のふち、雪の結晶の枝の鍔。"""
    m = JavaModel("frost_silver_daggers", 64, 64)
    m.box("pommel", (7.2, -2.6, 7.2), (8.8, -1.2, 8.8), "fsilver", tags=NOSNOW)
    m.box("pommel_gem", (7.6, -3.2, 7.6), (8.4, -2.6, 8.4), "icec")
    m.box("grip", (7.35, -1.2, 7.35), (8.65, 4, 8.65), "greyfur")
    m.box("guard", (5.5, 4, 7.3), (10.5, 5.0, 8.7), "fsilver", tags=NOSNOW)
    m.box("prong_l", (5.5, 5.0, 7.5), (6.3, 6.4, 8.5), "fsilver", tags=NOSNOW)
    m.box("prong_r", (9.7, 5.0, 7.5), (10.5, 6.4, 8.5), "fsilver", tags=NOSNOW)
    for k, (x0, y0, x1, y1, z) in enumerate(((6.8, 5.0, 9.2, 9.5, 0.35), (7.1, 9.5, 8.9, 13.0, 0.32),
                                             (7.4, 13.0, 8.6, 15.75, 0.28), (7.7, 15.75, 8.3, 17.5, 0.22))):
        m.box(f"blade_{k}", (x0, y0, 8 - z), (x1, y1, 8 + z), "fsilver", tags={"edge": "both", "no_snow": True})
    m.box("ice_edge_l", (6.5, 5.6, 7.75), (6.8, 9.0, 8.25), "icec")
    m.box("ice_edge_r", (9.2, 5.6, 7.75), (9.5, 9.0, 8.25), "icec")
    m.box("tip", (7.85, 17.5, 7.85), (8.15, 18.25, 8.15), "icec")
    return m


# ================================================================ 籠手（モンク）

def build_gauntlets():
    """腕の座標で作る（流紋の籠手と同じ。両手にはめる）。氷青の編みのミトンに、霜銀のこぶしの板と氷の鋲。"""
    m = JavaModel("frost_silver_gauntlets", 64, 64)
    m.box("cuff", (5.3, 4.0, 5.3), (10.7, 6.2, 10.7), "snowfur")
    m.box("mitten", (5.6, 6.2, 5.6), (10.4, 10.4, 10.4), "frostblue", tags={"knit": True})
    m.box("thumb", (10.4, 7.0, 6.4), (11.3, 9.4, 8.4), "frostblue", tags={"knit": True})
    m.box("knuckles", (5.35, 9.4, 5.35), (10.65, 11.0, 10.65), "fsilver", tags={"lames": 2, "no_snow": True})
    for k, x in enumerate((5.8, 7.45, 9.1)):
        m.box(f"stud_{k}", (x, 9.7, 4.85), (x + 1.1, 10.8, 5.4), "icec")
    m.box("back_plate", (6.4, 6.8, 5.1), (9.6, 9.0, 5.6), "fsilver", tags=NOSNOW)
    return m


# ================================================================ 盾（ナイト）

def build_shield():
    """六角の白樺の盾（上と下がとがる）。表（+z）に霜銀のふちと、氷の八つの腕の結晶の飾り。裏に握り。"""
    m = JavaModel("frost_silver_shield_model", 64, 64)
    rows = ((6.0, 7.5, 2.0), (4.5, 6.0, 4.0), (-4.5, 4.5, 5.75), (-6.0, -4.5, 4.0), (-7.5, -6.0, 2.0))
    for k, (y0, y1, half) in enumerate(rows):
        m.box(f"body_{k}", (-half, y0, 1.0), (half, y1, 2.75), "birch", tags={"grain": "y"})
    # ふち：霜銀（左右と、ななめの段）
    m.box("rim_l", (-6.25, -4.5, 0.85), (-5.75, 4.5, 2.95), "fsilver", tags=NOSNOW)
    m.box("rim_r", (5.75, -4.5, 0.85), (6.25, 4.5, 2.95), "fsilver", tags=NOSNOW)
    for k, (y0, y1, half) in enumerate(((4.5, 6.0, 4.0), (6.0, 7.5, 2.0), (-6.0, -4.5, 4.0), (-7.5, -6.0, 2.0))):
        m.box(f"rim_step_l_{k}", (-half - 0.5, y0, 0.9), (-half, y1, 2.9), "fsilver", tags=NOSNOW)
        m.box(f"rim_step_r_{k}", (half, y0, 0.9), (half + 0.5, y1, 2.9), "fsilver", tags=NOSNOW)
    m.box("rim_top", (-2.0, 7.5, 0.9), (2.0, 8.0, 2.9), "fsilver", tags=NOSNOW)
    m.box("rim_bottom", (-2.0, -8.0, 0.9), (2.0, -7.5, 2.9), "fsilver", tags=NOSNOW)
    # 表の飾り：氷の八つの腕の結晶と、まん中の霜銀の輪
    star8(m, "flake", 0.0, 0.0, 4.5, 2.75, 3.1, "icec", width=0.9)
    m.box("boss", (-1.25, -1.25, 2.75), (1.25, 1.25, 3.4), "fsilver", tags={"ring": "+z", "no_snow": True})
    m.box("boss_gem", (-0.6, -0.6, 3.4), (0.6, 0.6, 3.7), "icec")
    # 裏：握り
    m.box("grip_bracket_top", (-1, 1.5, -3), (1, 2.5, 1), "fsilver_dark")
    m.box("grip_bracket_low", (-1, -2.5, -3), (1, -1.5, 1), "fsilver_dark")
    m.box("grip", (-1, -2.5, -4.5), (1, 2.5, -3), "leather_dark", tags={"wrap": True})
    return m


WEAPONS = (
    (build_sword, "sword", lambda: weapon_display("sword"), "霜銀の剣（ナイト）",
     "まっすぐな霜銀の刃と、まん中の氷の稜\n雪の結晶の枝の鍔・つららの柄頭"),
    (build_greatsword, "greatsword", lambda: weapon_display("greatsword"), "霜銀の大剣（ナイト）",
     "幅の広い霜銀の刃に、両のふちのつららの歯\n毛皮を巻いた太い鍔・氷の稜"),
    (build_staff, "staff", lambda: weapon_display("staff"), "霜銀の杖（ウィザード）",
     "白樺の柄の先に、氷の八つの腕の結晶\n霜銀の輪・赤い編みの握り"),
    (build_bell_staff, "bell_staff", lambda: phase6_display("bell_staff"), "霜銀の鈴杖（ヒーラー）",
     "霜銀のアーチと、3本の氷の鈴（つらら）\nてっぺんの小さな雪の結晶・氷青の握り"),
    (build_bow, "bow", lambda: phase6_display("bow"), "霜銀の弓（アーチャー）",
     "白樺の腕・霜銀の巻いた弭と氷のとげ\n灰の毛皮の握り・霜銀の弦"),
    (build_daggers, "daggers", lambda: phase6_display("daggers"), "霜銀の双短剣（ローグ）",
     "霜銀の刃に氷のふち・雪の結晶の枝の鍔\n灰の毛皮の握り"),
    (build_gauntlets, "gauntlets", lambda: phase6_display("gauntlets"), "霜銀の籠手（モンク）",
     "氷青の編みのミトンと白い毛皮の口\n霜銀のこぶしの板・氷の鋲（両手にはめる）"),
)


def main():
    import frost_icons
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_frost"
    os.makedirs(out, exist_ok=True)
    rows = []
    for build, kind, disp, label, note in WEAPONS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(FrostWeaponPainter(m), frames=1)
        icon = frost_icons.icon(m.name)
        weapons.export_weapon(repo, m.name, m, frames, icon, kind, CREDIT, display=disp())
        rows.append({"label": label, "rarity": "コモン（工房で作る）・Lv24", "note": note, "model": m, "tex": frames[0],
                     "icon": icon})
        print("wrote", m.name, "部品", len(m.elements))
    m = build_shield()
    m.cull_hidden_faces()
    m.pack()
    frames = m.paint(FrostWeaponPainter(m), frames=1)
    normal, blocking = weapons.shield_display(SHIELD_FIRST, SHIELD_BLOCK, third_scale=0.95, gui=SHIELD_GUI)
    weapons.export_shield(repo, "frost_silver_shield", m, frames, CREDIT, normal, blocking)
    rows.append({"label": "霜銀の盾（ナイト）", "rarity": "コモン（工房で作る）・Lv24",
                 "note": "六角の白樺の盾・霜銀のふち\n表に氷の八つの腕の結晶と霜銀の輪", "model": m,
                 "tex": frames[0], "gui": SHIELD_GUI})
    print("wrote frost_silver_shield 部品", len(m.elements))
    from setsheet import make_set_sheet
    from materials import RAMPS
    palette = [
        ("霜銀", [RAMPS["fsilver"][i] for i in (1, 2, 3, 4, 5, 6)]),
        ("氷の結晶（光らない）", [RAMPS["icec"][i] for i in (2, 3, 4, 5, 6)]),
        ("白樺・毛皮・編み（赤と氷青）", [RAMPS["birch"][4], RAMPS["snowfur"][5], RAMPS["greyfur"][3],
                                RAMPS["berry"][3], RAMPS["frostblue"][3], RAMPS["leather_dark"][3]]),
    ]
    path = os.path.join(repo, "docs", "gear", "frost_silver_sheet.png")
    make_set_sheet(path, "霜銀シリーズの武器と盾（第2章の職業ごとの装備）",
                   "コモン・光らない｜11月26日に形から作り直した：霜銀・氷の結晶・雪の結晶の枝・白樺と毛皮", rows, palette=palette)
    import shutil
    shutil.copy(path, os.path.join(out, "frost_silver_weapons_sheet.png"))


if __name__ == "__main__":
    main()
