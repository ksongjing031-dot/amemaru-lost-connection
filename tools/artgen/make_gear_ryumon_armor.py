"""流紋シリーズの防具（Phase 8 その2。3〜4階の職業ごとの固有防具）：6職業 × 頭・胴・脚・足（GeckoLib）。

  python3 tools/artgen/make_gear_ryumon_armor.py <リポジトリ> [確認画像の出力先]

1つの職業＝1つのモデル（geo/armor/ryumon_<職業>.geo.json）。着た場所のボーンだけが表示される。
座標は Bedrock（体の右が −x、表が −z）。ボーンの名前と位置は GeckoLib の防具の決まり（armorHead など）。
色と塗り方は ryumon_common.py。持ち物の絵（16×16 → enhance_icons.py で32×32）もここで作る。
仕様書：docs/gear/ryumon.md
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from materials import RAMPS, hexc, pick  # noqa: E402
from pixelart import Pixel  # noqa: E402
from make_gear_ancient_armor import ArmorPainter, ARMOR_BONES, H, B, RA, LA, RL, LL, RB, LB, PIECE_BONES, VIEWS, \
    pair, subset  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402

CREDIT = "AMEMARU: 流紋シリーズ（Phase 8 その2。tools/artgen/make_gear_ryumon_armor.py で生成）"


class RyumonArmorPainter(RyumonPaint, ArmorPainter):
    def paint_void(self, c):
        return (16, 18, 22)


def new_armor(name, tex_h=64):
    g = GeoModel(name, 128, tex_h)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    return g


# 背中の印（布の色の上に、1ピクセルの線で縫う。# が暗い糸、@ が明るい糸）
EMBLEM_LAMP = ("...##...",
               "..#@@#..",
               "..#@@#..",
               "...##...",
               "..####..")
EMBLEM_DROP = ("...#....",
               "...##...",
               "..####..",
               "..#@##..",
               "...##...")
EMBLEM_WAVE = (".##...##",
               "#..#.#..",
               "....#...")


# ================================================================ ナイト：流紋の重鎧

def build_knight():
    g = new_armor("ryumon_knight")
    # 兜：青銅の鉢（横に流れの紋）・波頭のとさか（前から後ろへ高くなり、てっぺんで前へ巻く）・額の結晶
    #     ・覗き穴（暗い）・息の穴の顔の板・波の形のほお当て・うろこの首当て
    g.cube(H, "helm_shell", (-4, 24, -4), (8, 8, 8), "wbronze", inflate=1.0, tags={"flow": 4})
    g.cube(H, "crest_0", (-1, 33, -4), (2, 1, 9), "wbronze", tags={"base": 3.7})
    g.cube(H, "crest_1", (-1, 34, -1), (2, 1, 6), "wbronze", tags={"base": 3.8})
    g.cube(H, "crest_2", (-1, 35, 1), (2, 1, 4), "wbronze", tags={"base": 3.9})
    g.cube(H, "crest_3", (-1, 36, 2), (2, 1, 3), "wbronze", tags={"base": 4.0})
    g.cube(H, "crest_curl", (-1, 37, 0), (2, 1, 4), "wbronze", tags={"base": 4.1, "no_patina": True})
    g.cube(H, "crest_spray", (-1, 36, 0), (2, 1, 1), "flowc")
    g.cube(H, "brow", (-5, 28, -5.5), (10, 2, 1), "wbronze", inflate=0.2, tags={"rivets": [(-4.0, 29.0), (4.0, 29.0)]})
    g.cube(H, "brow_gem", (-0.5, 28.5, -6.0), (1, 1, 1), "flowc")
    g.cube(H, "slit", (-4, 27, -5.2), (8, 1, 1), "void")
    g.cube(H, "faceplate", (-5, 24, -5.5), (10, 3, 1), "wbronze", inflate=0.2, tags={"breaths": 25.0})
    pair(g, H, H, "cheek", (-5.75, 23, -5), (1, 5, 7), "wbronze", tags={"edge_bright": "bottom"})
    g.cube(H, "neckguard", (-5, 22, 4.5), (10, 3, 1), "wbronze", inflate=0.2, tags={"scales": True})
    # 胴：暗い鉄の下地に、流れの紋の青銅の胸板（上は広く、下は細い）・水門のハンドルの円盤と流水の結晶・首まわりの輪
    #     ・革の帯と青銅の留め金・うろこの腰の垂れ・背に水色の外套（すそに波）と肩の留め具
    g.cube(B, "body_shell", (-4, 12, -2), (8, 12, 4), "darksteel", inflate=1.01)
    g.cube(B, "breast_upper", (-4.5, 18.5, -3.75), (9, 5, 1), "wbronze", tags={"flow": 4})
    g.cube(B, "breast_lower", (-3.5, 14.5, -3.75), (7, 4, 1), "wbronze", tags={"flow": 4})
    g.cube(B, "gorget", (-3.5, 23.25, -3), (7, 1, 6), "wbronze")
    g.cube(B, "valve", (-2, 16, -4.6), (4, 4, 1), "wbronze", tags={"ring": True, "no_patina": True})
    g.cube(B, "valve_gem", (-1, 17, -5.05), (2, 2, 1), "flowc")
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "leather_dark", inflate=0.25)
    g.cube(B, "buckle", (-1, 11, -4.25), (2, 2, 1), "wbronze", tags={"no_patina": True})
    pair(g, B, B, "tasset", (-5, 8, -3.5), (4, 3, 1), "wbronze", tags={"scales": True})
    g.cube(B, "cape", (-4.5, 10, 3.25), (9, 13, 1), "teal", tags={"trim": "aqua", "wave": ("aqua", 3)})
    pair(g, B, B, "cape_clasp", (-4.75, 21.5, 2.9), (2, 2, 1), "wbronze", tags={"no_patina": True})
    # 腕：暗い鉄の籠手・魚のうろこのように3枚重なる肩当て（外へ下がる）・肘当て・流れの紋の腕当てと外の結晶
    pair(g, RA, LA, "arm_shell", (-8, 12, -2), (4, 12, 4), "darksteel", inflate=0.5)
    pair(g, RA, LA, "pauldron_top", (-9.5, 23, -3), (6, 2, 6), "wbronze", rot=(0, 0, -20), pivot=(-5, 24, 0),
         tags={"scales": True})
    pair(g, RA, LA, "pauldron_mid", (-9.75, 21.25, -3), (5, 2, 6), "wbronze", rot=(0, 0, -20), pivot=(-5, 22, 0),
         tags={"scales": True})
    pair(g, RA, LA, "pauldron_low", (-9.9, 19.5, -2.5), (4, 2, 5), "wbronze", rot=(0, 0, -20), pivot=(-5, 20.5, 0),
         tags={"scales": True})
    pair(g, RA, LA, "elbow", (-9.25, 15, -1.5), (1, 3, 3), "wbronze")
    pair(g, RA, LA, "vambrace", (-9, 12, -3), (6, 3, 6), "wbronze", tags={"flow": 3})
    pair(g, RA, LA, "vambrace_gem", (-9.6, 13, -0.5), (1, 1, 1), "flowc")
    # 脚甲：暗い鉄の腿・流れの紋の腿当て・渦の膝当て（輪がくぼむ）と外の小さなひれ・うろこのふくらはぎ
    pair(g, RL, LL, "leg_shell", (-3.9, 4, -2), (4, 8, 4), "darksteel", inflate=0.5)
    pair(g, RL, LL, "cuisse", (-3.4, 7.5, -3), (3, 4, 1), "wbronze", tags={"flow": 3})
    pair(g, RL, LL, "knee", (-3.4, 5, -3.6), (3, 3, 2), "wbronze", tags={"ring": True})
    pair(g, RL, LL, "knee_fin", (-5, 5.5, -2.5), (1, 2, 2), "wbronze")
    pair(g, RL, LL, "calf", (-3.4, 5, 2), (3, 5, 1), "wbronze", tags={"scales": True})
    # 鉄靴：青銅の靴・段のつま先・暗いかかと・足首の輪
    pair(g, RB, LB, "boot_shell", (-3.9, 0, -2), (4, 4, 4), "wbronze", inflate=1.0)
    pair(g, RB, LB, "toe_cap", (-3.4, -1, -4), (3, 2, 1), "wbronze")
    pair(g, RB, LB, "toe_step", (-2.9, -1, -5), (2, 1, 1), "wbronze", tags={"no_patina": True})
    pair(g, RB, LB, "heel", (-3.4, -1, 3), (3, 2, 1), "darkbronze")
    pair(g, RB, LB, "ankle", (-5.4, 4, -3.5), (7, 1, 7), "wbronze", tags={"edge_bright": "top"})
    return g


# ================================================================ ウィザード：流紋の長衣（星灯の水読み）

def build_wizard():
    g = new_armor("ryumon_wizard")
    # 尖り帽：広いつば（ふちに銀）・頭を包む山・銀の帯・後ろへ垂れる3段の先と、先の銀の星
    g.cube(H, "brim", (-6.5, 30.5, -6.5), (13, 1, 13), "navy", tags={"trim": "silver", "base": 2.6})
    g.cube(H, "crown", (-4.5, 31, -4.5), (9, 3, 9), "navy", inflate=0.1)
    g.cube(H, "band", (-4.5, 31.5, -4.5), (9, 1, 9), "teal", inflate=0.2, tags={"base": 3.6})
    g.cube(H, "cone_1", (-3.5, 34, -3.25), (7, 2, 7), "navy")
    g.cube(H, "cone_2", (-2.5, 36, -2), (5, 2, 5), "navy")
    g.cube(H, "cone_3", (-1.5, 38, -0.5), (3, 2, 3), "navy")
    g.cube(H, "cone_tip", (-1, 39.5, 1.5), (2, 2, 2), "navy", rot=(35, 0, 0), pivot=(0, 40, 1.5))
    g.cube(H, "tip_star", (-0.5, 40.0, 3.6), (1, 1, 1), "silver")
    # 長衣：藍の胴（背に銀の糸の「星灯」の印）・前の銀の合わせ・青銅のトグル・2段の肩掛け（すそに銀の星）・
    #       水路の青緑の帯と青銅の留め金・帯の垂れ・右の腰に流水の結晶のランタン
    g.cube(B, "robe_body", (-4, 12, -2), (8, 12, 4), "navy", inflate=0.75,
           tags={"emblem": (EMBLEM_LAMP, "silver", 5)})
    g.cube(B, "opening", (-0.5, 12, -3), (1, 9, 1), "silver")
    for k, y in enumerate((17, 15)):
        g.cube(B, f"toggle_{k}", (-1.5, y, -3.25), (3, 1, 1), "wbronze", tags={"no_patina": True})
    g.cube(B, "capelet", (-6, 19, -3.5), (12, 4, 7), "navy", tags={"trim": "silver", "stars": "silver"})
    g.cube(B, "capelet_top", (-5.5, 22, -4), (11, 2, 8), "navy")
    g.cube(B, "sash", (-5, 11.75, -3.5), (10, 2, 7), "teal")
    g.cube(B, "sash_buckle", (-1, 11.75, -4.25), (2, 2, 1), "wbronze", tags={"no_patina": True})
    g.cube(B, "sash_tail", (2, 7, -3.6), (2, 5, 1), "teal", tags={"trim": "aqua"})
    g.cube(B, "lantern_hook", (-6.25, 9.75, -0.5), (1, 2, 1), "wbronze")
    g.cube(B, "lantern_cap", (-6.75, 8.75, -1), (2, 1, 2), "wbronze")
    g.cube(B, "lantern_glass", (-6.75, 6.75, -1), (2, 2, 2), "flowc")
    g.cube(B, "lantern_base", (-6.75, 5.75, -1), (2, 1, 2), "wbronze")
    # 袖：藍の袖・銀のふちの袖口・広がる釣り鐘の袖口（裏は水色）
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "navy", inflate=0.5)
    pair(g, RA, LA, "cuff", (-9, 11, -3), (6, 3, 6), "navy", tags={"trim": "silver"})
    pair(g, RA, LA, "cuff_bell", (-9.5, 10, -3.5), (7, 1, 7), "navy", tags={"trim": "silver", "under": "aqua"})
    # 下衣：前の割れた長い裾（割れ目は銀）。すそに銀の波
    pair(g, RL, LL, "skirt", (-3.9, 4, -2), (4, 8, 4), "navy", inflate=0.75, tags={"split": "silver"})
    pair(g, RL, LL, "hem", (-4.4, 1, -3), (5, 3, 6), "navy", inflate=0.5,
         tags={"trim": "silver", "wave": ("silver", 1), "split": "silver"})
    # 布靴：濃い藍の靴・つま先の反り（銀の玉）・足首の銀の輪
    pair(g, RB, LB, "shoe", (-3.9, 0, -2), (4, 3, 4), "indigo", inflate=0.6)
    pair(g, RB, LB, "shoe_toe", (-2.9, -0.6, -3.8), (2, 1, 2), "indigo")
    pair(g, RB, LB, "shoe_curl", (-2.4, 0.4, -4.4), (1, 1, 1), "silver")
    pair(g, RB, LB, "ankle", (-4.4, 2.5, -2.5), (5, 1, 5), "silver")
    return g


# ================================================================ ヒーラー：流紋の白法衣（白い法衣と鈴）

def build_healer():
    g = new_armor("ryumon_healer")
    # 白頭巾：顔の前は開ける（裏地は水色）。額に金の輪と流水の結晶、両わきに金の鈴（耳飾りのように下がる）
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "white")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "white", tags={"lining": "aqua"})
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "white")
    g.cube(H, "hood_brim", (-4, 31, -5), (8, 1, 1), "white", tags={"lining": "aqua", "under": "aqua"})
    g.cube(H, "hood_peak", (-2, 33, -1), (4, 1, 6), "white")
    g.cube(H, "hood_peak_top", (-1, 34, 1.5), (2, 1, 3), "white")
    g.cube(H, "circlet", (-4, 30, -5.25), (8, 1, 1), "gold")
    g.cube(H, "circlet_gem", (-0.5, 29.8, -5.75), (1, 1, 1), "flowc")
    pair(g, H, H, "bell_hook", (-6.0, 26, -3.5), (1, 1, 1), "gold")
    pair(g, H, H, "bell", (-6.25, 24, -4), (1, 2, 2), "wbronze", tags={"no_patina": True, "base": 4.0})
    # 白法衣：白い胴（背に金の糸のしずくの印）・金のふちの2段の肩掛け・前に水色のストール（ふちは金）・
    #         肩掛けのすそに小さな鈴が3つ・金の紐の帯と結び目
    g.cube(B, "robe_body", (-4, 12, -2), (8, 12, 4), "white", inflate=0.75,
           tags={"emblem": (EMBLEM_DROP, "gold", 5)})
    g.cube(B, "capelet", (-6, 19.5, -3.5), (12, 3, 7), "white", tags={"trim": "gold"})
    g.cube(B, "capelet_top", (-5.5, 22, -4), (11, 2, 8), "white", tags={"trim": "gold"})
    pair(g, B, B, "stole", (-3, 8, -4.6), (2, 14, 1), "aqua", tags={"trim": "gold"})
    pair(g, B, B, "bell_side", (-5.5, 17, -4.6), (2, 2, 1), "wbronze", tags={"no_patina": True, "base": 4.2})
    g.cube(B, "bell_mid", (-1, 17, -4.7), (2, 2, 1), "wbronze", tags={"no_patina": True, "base": 4.2})
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "gold", tags={"base": 3.4})
    g.cube(B, "belt_knot", (-1, 10.5, -4.1), (2, 2, 1), "gold")
    # 袖：白い袖・金のふちの袖口・広がる袖口（裏は水色）
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "white", inflate=0.5)
    pair(g, RA, LA, "cuff", (-9, 11, -3), (6, 3, 6), "white", tags={"trim": "gold"})
    pair(g, RA, LA, "cuff_bell", (-9.5, 10, -3.5), (7, 1, 7), "white", tags={"trim": "gold", "under": "aqua"})
    # 白下衣：前の割れた長い裾（割れ目は水色）。すそに水色の波と金のふち
    pair(g, RL, LL, "skirt", (-3.9, 4, -2), (4, 8, 4), "white", inflate=0.75, tags={"split": "aqua"})
    pair(g, RL, LL, "hem", (-4.4, 1, -3), (5, 3, 6), "white", inflate=0.5,
         tags={"trim": "gold", "wave": ("aqua", 1), "split": "aqua"})
    # 白靴：白い靴・水色の帯と金の留め金
    pair(g, RB, LB, "shoe", (-3.9, 0, -2), (4, 3, 4), "white", inflate=0.6, tags={"base": 3.4})
    pair(g, RB, LB, "shoe_toe", (-3.4, -0.6, -3.6), (3, 1, 1), "white", tags={"base": 3.4})
    pair(g, RB, LB, "shoe_strap", (-4.9, 1, -3), (6, 1, 6), "aqua")
    pair(g, RB, LB, "shoe_buckle", (-2.4, 1, -3.25), (1, 1, 1), "gold")
    return g


# ================================================================ アーチャー：流紋の狩り装束（軽い革と羽根飾り）

def build_archer():
    g = new_armor("ryumon_archer")
    # 羽根帽：緑の帽子（すそに革の帯）・後ろへ高くなる山・前へ出るつば・右に白い長い羽根（先は水色）
    g.cube(H, "cap", (-4.5, 29.5, -4.5), (9, 3, 9), "hunter", inflate=0.1, tags={"trim": "leather"})
    g.cube(H, "cap_top", (-4, 32.5, -3.5), (8, 1, 7), "hunter")
    g.cube(H, "cap_peak", (-3, 33.5, -1.5), (6, 1, 5), "hunter")
    g.cube(H, "cap_point", (-2, 34.5, 1), (4, 1, 3), "hunter")
    g.cube(H, "visor", (-4, 29.5, -6.5), (8, 1, 2), "leather")
    g.cube(H, "feather", (-5.6, 31, -2), (1, 2, 9), "feather", rot=(-35, 0, 0), pivot=(-5.1, 31.5, -2),
           tags={"along": "i", "tip": "start"})
    g.cube(H, "feather_pin", (-5.75, 30.5, -2.5), (1, 1, 1), "wbronze", tags={"no_patina": True})
    # 狩り胴着：革の胴着・緑の短い外套（前は開いて革が見える）と後ろに下ろした頭巾・ななめの革帯・
    #           革の帯と青銅の留め金・右の腰の小物入れ・背の矢筒（ななめ。上に白い矢羽根）
    g.cube(B, "jerkin", (-4, 12, -2), (8, 12, 4), "leather", inflate=0.6, tags={"stitch": True})
    g.cube(B, "mantle", (-5, 18.5, -3.5), (10, 5, 7), "hunter", tags={"trim": "leather", "split": "leather"})
    g.cube(B, "hood_down", (-3.5, 20.5, 3.5), (7, 3, 2), "hunter")
    g.cube(B, "bandolier", (-0.5, 12.5, -3.85), (1, 13, 1), "leather_dark", rot=(0, 0, 35), pivot=(0, 18, -3.35))
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "leather_dark")
    g.cube(B, "buckle", (-1, 11, -4.25), (2, 2, 1), "wbronze", tags={"no_patina": True})
    g.cube(B, "pouch", (-5.5, 9, -1.5), (1, 3, 3), "leather")
    g.cube(B, "quiver", (0.5, 13, 3.25), (3, 9, 3), "leather_dark", rot=(0, 0, -20), pivot=(2, 17.5, 4.75),
           tags={"stitch": True})
    g.cube(B, "quiver_band", (0.25, 19, 3.0), (3, 1, 3), "wbronze", inflate=0.25, rot=(0, 0, -20), pivot=(2, 17.5, 4.75))
    g.cube(B, "arrows", (1, 22, 3.75), (2, 1, 2), "whitewood", rot=(0, 0, -20), pivot=(2, 17.5, 4.75))
    g.cube(B, "fletching", (0.75, 23, 3.5), (2, 2, 2), "feather", rot=(0, 0, -20), pivot=(2, 17.5, 4.75),
           tags={"along": "j", "tip": "end"})
    # 腕：緑の袖・革の肩当て・手首の革。弓を持つ左の腕に、長い革の腕当て
    pair(g, RA, LA, "sleeve", (-8, 14, -2), (4, 10, 4), "hunter", inflate=0.4)
    pair(g, RA, LA, "shoulder", (-8.5, 21, -2.5), (5, 2, 5), "leather", tags={"stitch": True})
    g.cube(RA, "cuff_r", (-8.5, 12, -2.5), (5, 2, 5), "leather_dark")
    g.cube(LA, "bracer_l", (3.5, 12, -2.5), (5, 5, 5), "leather_dark", tags={"stitch": True})
    g.cube(LA, "bracer_band", (3.25, 14, -2.75), (5, 1, 5), "wbronze", inflate=0.3, tags={"no_patina": True})
    # 革脚絆：緑の脚絆・腿の革の当て・膝の巻き革・右の腿の小さな袋
    pair(g, RL, LL, "leggings", (-3.9, 3, -2), (4, 9, 4), "hunter", inflate=0.45, tags={"base": 2.4})
    pair(g, RL, LL, "thigh_guard", (-3.4, 7, -2.75), (3, 4, 1), "leather", tags={"stitch": True})
    pair(g, RL, LL, "knee_wrap", (-3.9, 5, -2), (4, 1, 4), "leather_dark", inflate=0.65)
    g.cube(RL, "thigh_pouch", (-4.9, 8, -1), (1, 2, 2), "leather")
    # 長靴：ひざ下までの革の長靴・折り返し・留め帯と青銅の留め金
    pair(g, RB, LB, "boot", (-3.9, 0, -2), (4, 5, 4), "leather", inflate=0.55)
    pair(g, RB, LB, "boot_fold", (-4.4, 5, -2.5), (5, 2, 5), "leather", inflate=0.3, tags={"stitch": True})
    pair(g, RB, LB, "boot_toe", (-3.4, -0.5, -3.5), (3, 1, 1), "leather")
    pair(g, RB, LB, "boot_strap", (-4.4, 2, -2.5), (5, 1, 5), "leather_dark", inflate=0.1)
    pair(g, RB, LB, "boot_buckle", (-2.4, 2, -2.85), (1, 1, 1), "wbronze", tags={"no_patina": True})
    return g


# ================================================================ ローグ：流紋の黒装束（黒い軽装と布）

def build_rogue():
    g = new_armor("ryumon_rogue")
    # 覆面：黒い頭巾（目のまわりだけ開ける）・口と鼻をおおう青緑の布・後ろの結び目から長く流れる2本の布
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "black")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "black", tags={"lining": "teal"})
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "black")
    g.cube(H, "hood_brim", (-4, 30, -5), (8, 2, 1), "black", tags={"trim": "aqua"})
    g.cube(H, "mask", (-4, 24, -5), (8, 3, 1), "teal", tags={"base": 2.6, "trim_top": "teal"})
    g.cube(H, "knot", (-1, 26.5, 4.5), (2, 2, 1), "teal")
    # 布は後ろへ流れながら下がる（x のまわりの負の角で、先が下がる）
    g.cube(H, "tail_r", (-1.6, 25.5, 5), (1, 1, 7), "teal", rot=(-55, -10, 0), pivot=(-1.1, 26, 5))
    g.cube(H, "tail_l", (0.6, 25.0, 5), (1, 1, 6), "teal", rot=(-45, 10, 0), pivot=(1.1, 25.5, 5))
    # 黒装束：黒い胴（打ち合わせは青緑）・首の青緑の布・青緑の帯（左に結び目と2本の垂れ）・背にたすきと短剣の鞘
    g.cube(B, "garb", (-4, 12, -2), (8, 12, 4), "black", inflate=0.6, tags={"lapel": "teal", "lapel_tone": 4.2})
    g.cube(B, "scarf", (-4.5, 22, -3), (9, 2, 6), "teal", inflate=0.3)
    g.cube(B, "sash", (-5, 11.5, -3.5), (10, 2, 7), "teal", tags={"base": 2.6})
    g.cube(B, "sash_knot", (2.5, 10.5, -3.9), (2, 2, 1), "teal")
    g.cube(B, "sash_tail_1", (3.5, 6.5, -3.9), (1, 4, 1), "teal", tags={"trim": "aqua"})
    g.cube(B, "sash_tail_2", (2.3, 7.5, -3.85), (1, 3, 1), "teal", tags={"trim": "aqua"})
    g.cube(B, "baldric", (-0.5, 11.5, 3.35), (1, 14, 1), "leather_dark", rot=(0, 0, -35), pivot=(0, 18, 3.85))
    g.cube(B, "sheath", (1.5, 15, 3.9), (2, 6, 1), "leather_dark", rot=(0, 0, -35), pivot=(0, 18, 3.85),
           tags={"stitch": True})
    g.cube(B, "sheath_tip", (2, 14, 3.9), (1, 1, 1), "wbronze", rot=(0, 0, -35), pivot=(0, 18, 3.85),
           tags={"no_patina": True})
    # 腕：黒い袖・前腕の巻き布
    pair(g, RA, LA, "sleeve", (-8, 14, -2), (4, 10, 4), "black", inflate=0.4)
    pair(g, RA, LA, "wraps", (-8.5, 12, -2.5), (5, 4, 5), "white", tags={"wrap": True, "base": 2.2})
    # 黒脚絆：黒い脚絆・すねの巻き布・膝の革の当て
    pair(g, RL, LL, "leggings", (-3.9, 3, -2), (4, 9, 4), "black", inflate=0.45)
    pair(g, RL, LL, "shin_wraps", (-3.9, 4, -2), (4, 3, 4), "white", inflate=0.6, tags={"wrap": True, "base": 2.2})
    pair(g, RL, LL, "knee_pad", (-3.4, 6.5, -2.9), (3, 2, 1), "leather_dark")
    # 足袋：つま先の割れた黒い足袋と、足首の巻き布
    pair(g, RB, LB, "tabi", (-3.9, 0, -2), (4, 3, 4), "black", inflate=0.55, tags={"base": 3.4})
    pair(g, RB, LB, "tabi_toe", (-3.4, -0.55, -3.55), (3, 1, 1), "black", tags={"base": 3.4, "toe_split": True})
    pair(g, RB, LB, "ankle_wrap", (-3.9, 2.5, -2), (4, 1, 4), "white", inflate=0.7, tags={"wrap": True, "base": 2.2})
    return g


# ================================================================ モンク：流紋の道着（道着と籠手）

def build_monk():
    g = new_armor("ryumon_monk")
    # 鉢巻：藍の鉢巻・額に青銅の小さな板・後ろの結び目と、垂れる2本の端
    g.cube(H, "band", (-4.5, 28.5, -4.5), (9, 1, 9), "indigo", inflate=0.15)
    g.cube(H, "plate", (-1.5, 28, -4.95), (3, 2, 1), "wbronze", tags={"no_patina": True})
    g.cube(H, "knot", (-1, 28, 4.5), (2, 2, 1), "indigo")
    g.cube(H, "tail_r", (-1.5, 24.5, 4.8), (1, 4, 1), "indigo", rot=(18, 0, 6), pivot=(-1, 28.5, 5.3))
    g.cube(H, "tail_l", (0.5, 25.0, 4.8), (1, 3, 1), "indigo", rot=(12, 0, -6), pivot=(1, 28.5, 5.3))
    # 道着：生成りの道着（打ち合わせは藍。背に藍の波の印）・藍の帯（前に結び目と2本の垂れ）
    g.cube(B, "gi", (-4, 12, -2), (8, 12, 4), "gi", inflate=0.6,
           tags={"lapel": "indigo", "emblem": (EMBLEM_WAVE, "indigo", 4)})
    g.cube(B, "obi", (-5, 11, -3.5), (10, 2, 7), "indigo", tags={"base": 2.4})
    g.cube(B, "obi_knot", (-1.5, 10.5, -4.25), (3, 2, 1), "indigo", tags={"base": 2.8})
    g.cube(B, "obi_tail_1", (-1.25, 6.5, -4.1), (1, 4, 1), "indigo", tags={"base": 2.8})
    g.cube(B, "obi_tail_2", (0.25, 7, -4.1), (1, 3, 1), "indigo", tags={"base": 2.8})
    # 腕：ひじまでの道着の袖・前腕の白い巻き布（籠手は武器）
    pair(g, RA, LA, "sleeve", (-8, 17, -2), (4, 7, 4), "gi", inflate=0.65, tags={"trim": "gi"})
    pair(g, RA, LA, "wraps", (-8, 12, -2), (4, 5, 4), "white", inflate=0.4, tags={"wrap": True, "base": 3.2})
    # 袴：藍の袴（ひだ）。すそは絞って、生成りの紐で結ぶ
    pair(g, RL, LL, "hakama", (-3.9, 5, -2), (4, 7, 4), "indigo", inflate=0.9, tags={"pleats": True, "base": 3.5})
    pair(g, RL, LL, "hakama_low", (-3.9, 3, -2), (4, 2, 4), "indigo", inflate=0.7, tags={"pleats": True, "base": 3.5})
    pair(g, RL, LL, "hakama_tie", (-3.9, 2.5, -2), (4, 1, 4), "gi", inflate=0.75)
    # 草鞋：わらの底・ナマズのひげの鼻緒と足首の紐・足首の白い巻き布
    pair(g, RB, LB, "sole", (-3.9, -0.5, -2.5), (4, 1, 5), "straw")
    pair(g, RB, LB, "thong", (-2.4, 0.5, -2.75), (1, 1, 1), "whisker")
    pair(g, RB, LB, "strap", (-4.4, 0.5, -1), (5, 1, 1), "whisker", tags={"twist": True})
    pair(g, RB, LB, "heel_loop", (-3.4, 0.5, 2), (3, 1, 1), "whisker")
    pair(g, RB, LB, "ankle_wrap", (-3.9, 1.5, -2), (4, 2, 4), "white", inflate=0.6, tags={"wrap": True, "base": 3.2})
    return g


SETS = {
    "knight": {"build": build_knight, "title": "流紋の重鎧（ナイト）",
               "pieces": (("兜", "head", "ryumon_knight_helm"), ("胴鎧", "chest", "ryumon_knight_cuirass"),
                          ("脚甲", "legs", "ryumon_knight_greaves"), ("鉄靴", "feet", "ryumon_knight_sabatons")),
               "note": "水路の青銅を打った重い鎧。\n波頭のとさか・うろこの肩当て・\n胸に水門のハンドルと流水の結晶。\n背に水色の外套（すそに波）。"},
    "wizard": {"build": build_wizard, "title": "流紋の長衣（ウィザード）",
               "pieces": (("尖り帽", "head", "ryumon_wizard_hat"), ("長衣", "chest", "ryumon_wizard_robe"),
                          ("下衣", "legs", "ryumon_wizard_legwear"), ("布靴", "feet", "ryumon_wizard_shoes")),
               "note": "星灯の水読みの藍の長衣。\nつばの広い尖り帽（先が垂れる）・\n銀の波と星・背に星灯の印。\n腰に流水の結晶のランタン。"},
    "healer": {"build": build_healer, "title": "流紋の白法衣（ヒーラー）",
               "pieces": (("白頭巾", "head", "ryumon_healer_hood"), ("白法衣", "chest", "ryumon_healer_robe"),
                          ("白下衣", "legs", "ryumon_healer_legwear"), ("白靴", "feet", "ryumon_healer_shoes")),
               "note": "白い法衣に金のふちと水色の帯。\n頭巾の両わきと肩掛けに青銅の鈴。\n額に金の輪と流水の結晶。\n背に金の糸のしずくの印。"},
    "archer": {"build": build_archer, "title": "流紋の狩り装束（アーチャー）",
               "pieces": (("羽根帽", "head", "ryumon_archer_cap"), ("狩り胴着", "chest", "ryumon_archer_jerkin"),
                          ("革脚絆", "legs", "ryumon_archer_leggings"), ("長靴", "feet", "ryumon_archer_boots")),
               "note": "茶の革と緑の外套の軽い狩り装束。\n帽子に白い長い羽根（先は水色）。\n背に矢筒・左の腕に長い腕当て。\n折り返しの革の長靴。"},
    "rogue": {"build": build_rogue, "title": "流紋の黒装束（ローグ）",
              "pieces": (("覆面", "head", "ryumon_rogue_mask"), ("黒装束", "chest", "ryumon_rogue_garb"),
                         ("黒脚絆", "legs", "ryumon_rogue_leggings"), ("足袋", "feet", "ryumon_rogue_tabi")),
              "note": "黒い軽い装束に、水路の青緑の布。\n覆面の結び目から長く流れる布。\n背にたすきと短剣の鞘。\n腕とすねに巻き布、割れたつま先の足袋。"},
    "monk": {"build": build_monk, "title": "流紋の道着（モンク）",
             "pieces": (("鉢巻", "head", "ryumon_monk_headband"), ("道着", "chest", "ryumon_monk_gi"),
                        ("袴", "legs", "ryumon_monk_hakama"), ("草鞋", "feet", "ryumon_monk_sandals")),
             "note": "生成りの道着に藍の帯と袴。\n額に青銅の板の鉢巻（端が垂れる）。\n背に藍の波の印・前腕に白い巻き布。\nナマズのひげの鼻緒の草鞋。"},
}


def paint(g):
    g.pack()
    return g.paint(RyumonArmorPainter(g), frames=1)


def preview(g, tex, path, scale=10):
    from render import render_many
    from PIL import Image
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    ims = [render_many([(man, mtex), (g, tex)], yaw=y, pitch=p, scale=scale, size=(260, 420), center=(0, 18, 0))
           for y, p, _ in VIEWS]
    s = Image.new("RGBA", (260 * 4, 420), (40, 44, 52, 255))
    for k, im in enumerate(ims):
        s.alpha_composite(im, (260 * k, 0))
    s.save(path)


def make_sheet(key, g, tex, path):
    import ryumon_icons
    from setsheet import make_set_sheet
    cfg = SETS[key]
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    full = [(man, mtex), (g, tex)]
    icons = [(label, ryumon_icons.icon(item)) for label, _, item in cfg["pieces"]]
    piece_views = [(label, -145, 14, [(man, mtex), (subset(g, PIECE_BONES[slot]), tex)])
                   for label, slot, _ in cfg["pieces"]]
    items = [
        {"label": "フルセット", "rarity": "コモン・Lv7・" + cfg["title"].split("（")[1].rstrip("）") + "専用",
         "note": cfg["note"], "pairs": full, "views": [(lab, y, p) for y, p, lab in VIEWS], "icons": icons},
        {"label": "部位ごと", "rarity": "着た場所だけ表示", "note": "", "pairs": full, "views": piece_views,
         "icons": icons, "icon_cell": 40},
    ]
    make_set_sheet(path, cfg["title"], "流紋シリーズ（3〜4階の職業ごとの装備）／ GeckoLib ／ 128×64の絵", items, row_h=420)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_ryumon"
    os.makedirs(out, exist_ok=True)
    only = sys.argv[3].split(",") if len(sys.argv) > 3 else list(SETS)
    for key in only:
        g = SETS[key]["build"]()
        frames = paint(g)
        used = max(c.uv[1] + c.block()[1] for c in g.elements)
        print(g.name, "部品", len(g.elements), "絵の使った高さ", used, "/", g.tex_h)
        g.save(repo, "armor", frames)
        preview(g, frames[0], os.path.join(out, "ryumon_%s_preview.png" % key))
        make_sheet(key, g, frames[0], os.path.join(repo, "docs/gear", "ryumon_%s_sheet.png" % key))
    save_icons(repo)


def save_icons(repo):
    """防具の持ち物の絵（16×16。このあと enhance_icons.py で32×32にする）と、アイテムのモデル。"""
    import json
    import ryumon_icons
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for name in ryumon_icons.ARMOR_NAMES:
        ryumon_icons.icon(name).save(os.path.join(base, "textures/item", name + ".png"))
        with open(os.path.join(base, "models/item", name + ".json"), "w", encoding="utf-8") as fp:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, fp,
                      ensure_ascii=False, indent=2)
            fp.write("\n")


if __name__ == "__main__":
    main()
