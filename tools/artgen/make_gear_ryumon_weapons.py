"""流紋シリーズの武器と盾（Phase 8 その2。3〜4階の職業ごとの装備）【仮素材】。

  python3 tools/artgen/make_gear_ryumon_weapons.py <リポジトリ> [確認画像の出力先]

武器（JavaModel。手に持つと立体、持ち物の枠では16×16の絵 → enhance_icons.py で32×32）：
  流紋の剣（ナイト）・流紋の大剣（ナイト）・流紋の杖（ウィザード）・流紋の鈴杖（ヒーラー）・流紋の弓（アーチャー）・
  流紋の双短剣（ローグ）・流紋の籠手（モンク）
盾（JavaModel。枠でも3D）：流紋の盾（ナイト）
シリーズの印：水路の青銅（緑青のさび）・流水の結晶（光らない）・波打つ「流れの紋」。持ち方と大きさは古代石・Phase 6 と同じ。
仕様書：docs/gear/ryumon.md
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter  # noqa: E402
import weapons  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from make_gear_ancient_stone import weapon_display, SHIELD_FIRST, SHIELD_BLOCK, SHIELD_GUI  # noqa: E402
from make_gear_phase6 import display as phase6_display, BOW_STRING_X  # noqa: E402
import ryumon_icons  # noqa: E402

CREDIT = "AMEMARU: 流紋シリーズ（Phase 8 その2。tools/artgen/make_gear_ryumon_weapons.py で生成）"


class RyumonWeaponPainter(RyumonPaint, Painter):
    pass


def gem_pair(m, name, x0, y0, x1, y1, z_front, z_back, depth=0.3):
    """表と裏に、同じ流水の結晶をはめる。"""
    m.box(name + "_f", (x0, y0, z_front - depth), (x1, y1, z_front), "flowc")
    m.box(name + "_b", (x0, y0, z_back), (x1, y1, z_back + depth), "flowc")


# ================================================================ 剣（ナイト）

def build_sword():
    m = JavaModel("ryumon_sword", 64, 64)
    # 柄頭：青銅のおもりの表と裏に結晶。握り：暗い革に、ナマズのひげの紐を2か所
    m.box("pommel", (7, -3.4, 7.2), (9, -1.2, 8.8), "wbronze", tags={"no_patina": True})
    gem_pair(m, "pommel_gem", 7.5, -2.8, 8.5, -1.8, 7.2, 8.8)
    m.box("grip", (7.4, -1.2, 7.4), (8.6, 4.4, 8.6), "leather_dark", tags={"wrap": True})
    m.box("cord_0", (7.25, 0.4, 7.25), (8.75, 0.9, 8.75), "whisker", tags={"twist": True})
    m.box("cord_1", (7.25, 3.2, 7.25), (8.75, 3.7, 8.75), "whisker", tags={"twist": True})
    # 鍔：まん中の青銅と結晶、左右へ広がり、先が上へ反る（水しぶきの翼）
    m.box("guard", (6.4, 4.4, 6.9), (9.6, 5.8, 9.1), "wbronze")
    gem_pair(m, "guard_gem", 7.5, 4.6, 8.5, 5.6, 6.9, 9.1)
    m.box("wing_l", (4.6, 4.8, 7.2), (6.4, 5.8, 8.8), "wbronze")
    m.box("wing_l_tip", (3.8, 5.3, 7.35), (4.6, 6.9, 8.65), "wbronze", tags={"no_patina": True})
    m.box("wing_r", (9.6, 4.8, 7.2), (11.4, 5.8, 8.8), "wbronze")
    m.box("wing_r_tip", (11.4, 5.3, 7.35), (12.2, 6.9, 8.65), "wbronze", tags={"no_patina": True})
    # 刃：少し左右にゆれる青銅（流れ）。まん中の溝に流水の結晶（表と裏に水の筋）
    for k, (x0, y0, x1, y1) in enumerate(((6.5, 5.8, 9.5, 9.5), (6.3, 9.5, 9.3, 13), (6.5, 13, 9.5, 16.5),
                                          (6.7, 16.5, 9.3, 19.5))):
        m.box(f"blade_{k}", (x0, y0, 7.6), (x1, y1, 8.4), "wbronze", tags={"edge": "both", "no_patina": True})
    m.box("tip_0", (7.1, 19.5, 7.7), (8.9, 20.75, 8.3), "wbronze", tags={"edge": "both", "no_patina": True})
    m.box("tip_1", (7.5, 20.75, 7.75), (8.5, 21.75, 8.25), "wbronze", tags={"no_patina": True})
    m.box("tip_2", (7.8, 21.75, 7.8), (8.2, 22.5, 8.2), "wbronze", tags={"no_patina": True})
    m.box("channel", (7.6, 6.3, 7.45), (8.4, 18.5, 8.55), "flowc", tags={"channel": True})
    return m


# ================================================================ 大剣（ナイト）

def build_greatsword():
    m = JavaModel("ryumon_greatsword", 64, 64)
    # 柄頭：かじられた歯車の形の青銅。長い握り（暗い革）と青銅の輪2つ
    m.box("pommel", (6.75, -4.75, 6.75), (9.25, -2.25, 9.25), "wbronze", tags={"cog": True})
    m.box("pommel_cap", (7.25, -5.25, 7.25), (8.75, -4.75, 8.75), "wbronze")
    m.box("grip", (7.4, -2.25, 7.4), (8.6, 8, 8.6), "leather_dark", tags={"wrap": True})
    m.box("ring_0", (7.1, 0.75, 7.1), (8.9, 1.5, 8.9), "wbronze", tags={"no_patina": True})
    m.box("ring_1", (7.1, 4.75, 7.1), (8.9, 5.5, 8.9), "wbronze", tags={"no_patina": True})
    # 鍔：水門の梁のような太い青銅。両はしが上へ巻く。まん中に結晶
    m.box("guard", (4.0, 8, 6.6), (12, 10.25, 9.4), "wbronze", tags={"flow": 3})
    m.box("guard_end_l", (3.3, 8.75, 6.9), (4.0, 11.25, 9.1), "wbronze")
    m.box("guard_end_r", (12, 8.75, 6.9), (12.7, 11.25, 9.1), "wbronze")
    gem_pair(m, "guard_gem", 7.25, 8.4, 8.75, 9.9, 6.6, 9.4)
    # 刃：水門の扉の板（4ピクセルごとの継ぎ目とリベット）。左に暗い青銅の背、その横に「滝の溝」（流水の結晶）
    m.box("spine", (4.9, 10.25, 7.1), (5.8, 29.5, 8.9), "darkbronze",
          tags={"rivets": [(5.35, 13.0), (5.35, 19.0), (5.35, 25.0)]})
    m.box("slab", (5.8, 10.25, 7.3), (11.2, 28.25, 8.7), "wbronze", tags={"planks": 4})
    m.box("channel", (6.2, 11, 7.15), (7.0, 27.5, 8.85), "flowc", tags={"channel": True})
    m.box("edge", (11.2, 10.25, 7.55), (11.8, 27.5, 8.45), "wbronze", tags={"base": 4.6, "no_patina": True})
    m.box("slant", (5.5, 28.25, 7.4), (11.8, 29.6, 8.6), "wbronze", rot=((8.65, 28.9, 8.0), "z", -22.5),
          tags={"no_patina": True})
    m.box("slant_edge", (10.6, 26.9, 7.6), (12.1, 28.0, 8.4), "wbronze", rot=((11.35, 27.45, 8.0), "z", -22.5),
          tags={"base": 4.6, "no_patina": True})
    m.box("spine_cap", (4.9, 29.5, 7.2), (5.8, 30.25, 8.8), "darkbronze")
    return m


# ================================================================ 杖（ウィザード）

def build_staff():
    m = JavaModel("ryumon_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6.5, 8.5), "wbronze")
    m.box("spike_tip", (7.75, -8.75, 7.75), (8.25, -8, 8.25), "wbronze")
    m.box("shaft", (7.6, -6.5, 7.6), (8.4, 16, 8.4), "wbronze", tags={"base": 3.0})
    for k, y in enumerate((-5, 7, 12)):
        m.box(f"ring_{k}", (7.25, y, 7.25), (8.75, y + 1, 8.75), "wbronze", tags={"no_patina": True})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "navy", tags={"wrap": True})
    # 先：青銅の輪から4本の柱が、高さを変えて渦のように立ち、先を内へ曲げる。まん中に水の玉（流水の結晶）
    m.box("collar", (6.75, 15, 6.75), (9.25, 16, 9.25), "wbronze", tags={"no_patina": True})
    posts = (((6.0, 7.4), (6.9, 8.6), 19.5, (6.9, 7.6), (7.4, 8.4)),
             ((7.4, 9.1), (8.6, 10.0), 21.0, (7.6, 8.6), (8.4, 9.1)),
             ((9.1, 7.4), (10.0, 8.6), 22.5, (8.6, 7.6), (9.1, 8.4)),
             ((7.4, 6.0), (8.6, 6.9), 23.5, (7.6, 6.9), (8.4, 7.4)))
    for k, ((x0, z0), (x1, z1), top, (tx0, tz0), (tx1, tz1)) in enumerate(posts):
        m.box(f"post_{k}", (x0, 16, z0), (x1, top, z1), "wbronze", tags={"flow": 4})
        m.box(f"post_tip_{k}", (tx0, top - 0.75, tz0), (tx1, top, tz1), "wbronze", tags={"no_patina": True})
    m.box("orb", (6.9, 17.9, 6.9), (9.1, 20.1, 9.1), "flowc")
    m.box("orb_top", (7.4, 20.1, 7.4), (8.6, 20.6, 8.6), "flowc")
    # 柱から下がる銀の星（星灯の印）
    m.box("charm_chain", (9.75, 13.5, 7.9), (9.95, 16, 8.1), "silver")
    m.box("charm_star", (9.35, 12.4, 7.75), (10.35, 13.5, 8.25), "silver")
    return m


# ================================================================ 鈴杖（ヒーラー）

def build_bell_staff():
    m = JavaModel("ryumon_bell_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6, 8.5), "gold")
    m.box("shaft", (7.6, -6, 7.6), (8.4, 15.5, 8.4), "whitewood", tags={"grain": "y"})
    for k, y in enumerate((-4, 7, 12)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "gold")
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "aqua", tags={"wrap": True})
    # 先：白い四角の輪（角に金）・まん中に下がる水のしずく（流水の結晶）・輪の下の左右に青銅の鈴
    m.box("frame_bottom", (5.25, 15.5, 7.5), (10.75, 16.5, 8.5), "whitewood")
    m.box("frame_l", (5.0, 16.5, 7.5), (6.0, 21.5, 8.5), "whitewood")
    m.box("frame_r", (10.0, 16.5, 7.5), (11.0, 21.5, 8.5), "whitewood")
    m.box("frame_top", (5.25, 21.5, 7.5), (10.75, 22.5, 8.5), "whitewood")
    for side, x in (("l", 5.0), ("r", 10.0)):
        m.box(f"corner_top_{side}", (x, 21.5, 7.35), (x + 1, 22.5, 8.65), "gold")
        m.box(f"corner_low_{side}", (x, 15.5, 7.35), (x + 1, 16.5, 8.65), "gold")
    m.box("drop_hang", (7.85, 20.75, 7.85), (8.15, 21.5, 8.15), "gold")
    m.box("drop_tip", (7.5, 19.75, 7.5), (8.5, 20.75, 8.5), "flowc")
    m.box("drop", (7.1, 17.25, 7.25), (8.9, 19.75, 8.75), "flowc")
    m.box("finial", (7.5, 22.5, 7.5), (8.5, 23.5, 8.5), "gold")
    m.box("finial_tip", (7.75, 23.5, 7.75), (8.25, 24.25, 8.25), "gold")
    for side, x in (("l", 5.5), ("r", 10.5)):
        m.box(f"chain_{side}", (x - 0.15, 14.75, 7.85), (x + 0.15, 15.5, 8.15), "gold")
        m.box(f"bell_{side}", (x - 1.0, 13.0, 7.0), (x + 1.0, 14.75, 9.0), "wbronze",
              tags={"no_patina": True, "base": 4.0})
        m.box(f"bell_lip_{side}", (x - 1.3, 12.5, 6.7), (x + 1.3, 13.0, 9.3), "wbronze", tags={"no_patina": True})
    return m


# ================================================================ 弓（アーチャー）

def build_bow():
    """腕と弦の形は古代石の弓と同じ（持ち方の決まり。弦は +x の側）。腕はクルミ材、弭と帯は青銅、弦はナマズのひげ。"""
    m = JavaModel("ryumon_bow", 64, 64)
    m.box("grip", (7.0, 0, 7.2), (8.8, 5, 8.8), "leather_dark", tags={"wrap": True})
    m.box("grip_plate", (8.8, 0.5, 7.0), (9.6, 4.5, 9.0), "wbronze", tags={"flow": 3})
    m.box("rest_gem", (9.6, 2.0, 7.5), (10.2, 3.0, 8.5), "flowc")
    segs = ((5, 9, 8.0), (9, 13, 8.6), (13, 16.5, 9.4), (16.5, 19, 10.3))
    for k, (y0, y1, x) in enumerate(segs):
        m.box(f"upper_{k}", (x - 0.6, y0, 7.4), (x + 0.6, y1, 8.6), "walnut", tags={"grain": "y"})
        m.box(f"lower_{k}", (x - 0.6, -y1 + 5, 7.4), (x + 0.6, -y0 + 5, 8.6), "walnut", tags={"grain": "y"})
    m.box("tip_top", (10.0, 19, 7.6), (11.2, 20.2, 8.4), "wbronze", tags={"no_patina": True})
    m.box("tip_bottom", (10.0, -15.2, 7.6), (11.2, -14, 8.4), "wbronze", tags={"no_patina": True})
    m.box("string", (BOW_STRING_X[0], -14.9, 7.88), (BOW_STRING_X[1], 19.9, 8.12), "whisker")
    m.box("nock_top", (10.6, 18.6, 7.7), (11.15, 19.0, 8.3), "whisker", tags={"twist": True})
    m.box("nock_bottom", (10.6, -14.0, 7.7), (11.15, -13.6, 8.3), "whisker", tags={"twist": True})
    for k, y in enumerate((9.0, -4.0)):
        m.box(f"band_{k}", (8.0, y, 7.2), (10.0, y + 1.2, 8.8), "wbronze", tags={"no_patina": True})
    return m


# ================================================================ 双短剣（ローグ）

def build_daggers():
    """大きさは古代石の双短剣と同じ（柄頭から切っ先まで約20.6）。刃は魚のひれのように +x へ反る。"""
    m = JavaModel("ryumon_daggers", 64, 64)
    m.box("pommel", (7.2, -2.6, 7.2), (8.8, -1.2, 8.8), "wbronze", tags={"no_patina": True})
    m.box("grip", (7.35, -1.2, 7.35), (8.65, 4, 8.65), "leather_dark", tags={"wrap": True})
    m.box("cord", (7.2, 1.2, 7.2), (8.8, 1.8, 8.8), "whisker", tags={"twist": True})
    m.box("guard", (5.25, 4, 7.2), (10.75, 5.2, 8.8), "wbronze")
    m.box("gill_l", (4.75, 4.4, 7.35), (5.25, 5.8, 8.65), "wbronze")
    m.box("gill_r", (10.75, 4.4, 7.35), (11.25, 5.8, 8.65), "wbronze")
    for k, (x0, y0, x1, y1, z) in enumerate(((6.7, 5.2, 9.3, 9.5, 0.35), (7.1, 9.5, 9.9, 13.0, 0.35),
                                             (7.8, 13.0, 10.1, 15.5, 0.3), (8.6, 15.5, 10.0, 17.0, 0.25),
                                             (9.2, 17.0, 9.9, 18.0, 0.2))):
        m.box(f"blade_{k}", (x0, y0, 8 - z), (x1, y1, 8 + z), "wbronze", tags={"edge": "right", "no_patina": True})
    m.box("channel", (7.6, 5.6, 7.55), (8.2, 13.5, 8.45), "flowc", tags={"channel": True})
    return m


# ================================================================ 籠手（モンク）

def build_gauntlets():
    """腕の座標で作る（古代石の籠手と同じ。両手にはめる。左右は対称）。拳の前に、歯車の歯を3つ並べる。"""
    m = JavaModel("ryumon_gauntlets", 64, 64)
    m.box("cuff", (5.55, 4.4, 5.55), (10.45, 7.6, 10.45), "wbronze", tags={"flow": 3})
    m.box("cuff_ring", (5.3, 4.0, 5.3), (10.7, 5.0, 10.7), "darkbronze")
    m.box("hand", (5.6, 7.6, 5.6), (10.4, 10.3, 10.4), "leather_dark")
    m.box("back_out", (5.2, 7.7, 6.0), (5.6, 10.2, 10.0), "wbronze")
    m.box("back_in", (10.4, 7.7, 6.0), (10.8, 10.2, 10.0), "wbronze")
    m.box("knuckles", (5.35, 10.3, 5.35), (10.65, 11.2, 10.65), "wbronze", tags={"no_patina": True})
    for k, x in enumerate((5.8, 7.45, 9.1)):
        m.box(f"tooth_{k}", (x, 9.9, 4.85), (x + 1.1, 11.3, 5.4), "wbronze", tags={"no_patina": True, "base": 4.2})
    m.box("gem", (7.4, 8.2, 4.9), (8.6, 9.4, 5.6), "flowc")
    m.box("wrist_ring", (5.45, 7.4, 5.45), (10.55, 7.9, 10.55), "darkbronze")
    return m


# ================================================================ 盾（ナイト）

def build_shield():
    """凧形（上は平ら、下へとがる）の青銅の盾。表（+z）に流れの紋の帯3本と、青銅の輪の中の結晶。裏に握り。"""
    m = JavaModel("ryumon_shield_model", 64, 64)
    plate = {"flow": 4, "no_patina": True, "base": 3.8}
    m.box("top", (-5.5, 5, 1), (5.5, 7, 2.75), "wbronze", tags=plate)
    m.box("mid", (-5.5, -3, 1), (5.5, 5, 2.75), "wbronze", tags=plate)
    m.box("low_1", (-4.5, -5.5, 1.05), (4.5, -3, 2.7), "wbronze", tags=plate)
    m.box("low_2", (-3.25, -7.5, 1.1), (3.25, -5.5, 2.65), "wbronze", tags={"no_patina": True})
    m.box("low_3", (-1.75, -9, 1.15), (1.75, -7.5, 2.6), "wbronze", tags={"no_patina": True})
    m.box("tip", (-0.6, -10, 1.2), (0.6, -9, 2.55), "wbronze", tags={"no_patina": True})
    # ふち：暗い青銅（左右と上）
    m.box("rim_l", (-6, -3, 0.75), (-5.5, 7, 3.0), "darkbronze")
    m.box("rim_r", (5.5, -3, 0.75), (6, 7, 3.0), "darkbronze")
    m.box("rim_top", (-6, 7, 0.75), (6, 7.6, 3.0), "darkbronze", tags={"rivets": [(-4.0, 7.3), (0.0, 7.3), (4.0, 7.3)]})
    # 表の飾り：上の帯（水の筋）と、まん中の輪と結晶
    m.box("band", (-5, 4, 2.75), (5, 5, 3.1), "flowc", tags={"channel": True})
    m.box("boss_ring", (-2, -1.5, 2.75), (2, 2.5, 3.1), "wbronze", tags={"ring": "+z", "no_patina": True})
    m.box("boss_gem", (-1, -0.5, 3.1), (1, 1.5, 3.5), "flowc")
    # 裏：握り
    m.box("grip_bracket_top", (-1, 1.5, -3), (1, 2.5, 1), "darkbronze")
    m.box("grip_bracket_low", (-1, -2.5, -3), (1, -1.5, 1), "darkbronze")
    m.box("grip", (-1, -2.5, -4.5), (1, 2.5, -3), "leather_dark", tags={"wrap": True})
    return m


WEAPONS = (
    (build_sword, "sword", lambda: weapon_display("sword"), "流紋の剣（ナイト）",
     "ゆれる青銅の刃と、まん中の水の溝\n翼の鍔・ナマズのひげの紐の握り"),
    (build_greatsword, "greatsword", lambda: weapon_display("greatsword"), "流紋の大剣（ナイト）",
     "水門の扉の板の刃（継ぎ目とリベット）\n背に沿う「滝の溝」・歯車の柄頭"),
    (build_staff, "staff", lambda: weapon_display("staff"), "流紋の杖（ウィザード）",
     "渦に立つ4本の青銅の柱と水の玉\n藍の布の握り・下がる銀の星"),
    (build_bell_staff, "bell_staff", lambda: phase6_display("bell_staff"), "流紋の鈴杖（ヒーラー）",
     "白い柄と白い輪・金の角\nまん中に水のしずく・左右に青銅の鈴"),
    (build_bow, "bow", lambda: phase6_display("bow"), "流紋の弓（アーチャー）",
     "クルミ材の腕と青銅の弭・帯\n弦はナマズのひげ・握りに結晶"),
    (build_daggers, "daggers", lambda: phase6_display("daggers"), "流紋の双短剣（ローグ）",
     "魚のひれのように反る青銅の刃\nえらの形の鍔・水の溝"),
    (build_gauntlets, "gauntlets", lambda: phase6_display("gauntlets"), "流紋の籠手（モンク）",
     "流れの紋の青銅の筒・歯車の歯の拳\n甲に流水の結晶（両手にはめる）"),
)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_ryumon"
    os.makedirs(out, exist_ok=True)
    rows = []
    for build, kind, disp, label, note in WEAPONS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(RyumonWeaponPainter(m), frames=1)
        icon = ryumon_icons.icon(m.name)
        weapons.export_weapon(repo, m.name, m, frames, icon, kind, CREDIT, display=disp())
        rows.append({"label": label, "rarity": "コモン（工房で作る）・Lv7", "note": note, "model": m, "tex": frames[0],
                     "icon": icon})
        print("wrote", m.name, "部品", len(m.elements))
    m = build_shield()
    m.cull_hidden_faces()
    m.pack()
    frames = m.paint(RyumonWeaponPainter(m), frames=1)
    normal, blocking = weapons.shield_display(SHIELD_FIRST, SHIELD_BLOCK, third_scale=0.95, gui=SHIELD_GUI)
    weapons.export_shield(repo, "ryumon_shield", m, frames, CREDIT, normal, blocking)
    rows.append({"label": "流紋の盾（ナイト）", "rarity": "コモン（工房で作る）・Lv7",
                 "note": "凧形の青銅の盾・暗い青銅のふち\n表に水の筋の帯と、輪の中の結晶", "model": m,
                 "tex": frames[0], "gui": SHIELD_GUI})
    print("wrote ryumon_shield 部品", len(m.elements))
    from setsheet import make_set_sheet
    from materials import RAMPS
    palette = [
        ("水路の青銅と緑青", [RAMPS["wbronze"][i] for i in (1, 2, 3, 4, 5, 6)] + [RAMPS["verdigris"][3]]),
        ("流水の結晶（光らない）", [RAMPS["flow"][i] for i in (2, 3, 4, 5, 6)]),
        ("職業の色（藍・白と金・クルミ材・暗い革・ひげ）", [RAMPS["navy"][4], RAMPS["white"][5], RAMPS["gold"][4],
                                         RAMPS["walnut"][3], RAMPS["leather_dark"][3], RAMPS["whisker"][3]]),
    ]
    path = os.path.join(repo, "docs", "gear", "ryumon_weapons_sheet.png")
    make_set_sheet(path, "流紋シリーズの武器と盾（3〜4階の職業ごとの装備）",
                   "コモン・光らない｜共通：水路の青銅（緑青）・流水の結晶・波打つ流れの紋", rows, palette=palette)
    import shutil
    shutil.copy(path, os.path.join(out, "ryumon_weapons_sheet.png"))


if __name__ == "__main__":
    main()
