"""守護者シリーズの防具（5階「古代守護者の祭壇」の職業ごとの装備。エピック）：6職業 × 頭・胴・脚・足（GeckoLib）。

  python3 tools/artgen/make_gear_guardian_armor.py <リポジトリ> [確認画像の出力先] [職業,職業] [--preview]

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」【補完】。
前は流紋シリーズの形を守護者の色に塗り直していた。古代守護者（白い石と青銅の巨人・胸の翠の記録核）の姿から、職業ごとに
形を作り直した。翠の記録核（立方体の結晶）は光る（エピックの光る部分）：
  ナイト    ＝角ばった石の兜（左右に立つ石の刃・横一本の翠の覗き穴）・胸の翠の核と青銅の枠・立方体の肩当て・石の垂れ・背の石の背びれ
  ウィザード＝段の石の尖り帽（ピラミッド）と、てっぺんに浮かぶ翠の立方体・四角の青銅の襟と翠の垂れ飾り・背の格子の石板
  ヒーラー  ＝白いヴェールと、頭の後ろの八角の青銅の後光・額の翠・円い胸当てと石の数珠・石のタイルのすそ・石の底のサンダル
  アーチャー＝石の帽子と、右目の翠の片眼鏡・左に扇に開いた石の羽根・石の小札の胴・背の石の矢筒（翠のふた）
  ローグ    ＝顔をすべておおう石の面（縦一本の翠のすじ）と角ばった頭巾・右肩だけの石の肩当て・翠の玉のななめの帯・背の石の肩掛け
  モンク    ＝石の鉢巻（翠の玉3つ・後ろに石の房）・袖のない道着と大きな石の数珠（翠の玉）・腕の石の輪・石の下駄
1つの職業＝1つのモデル（geo/armor/guardian_<職業>.geo.json。光る所は guardian_<職業>_glowmask.png）。
色と塗り方は guardian_common.py。持ち物の絵（16×16 → enhance_icons.py で32×32）は guardian_icons.py。仕様書：docs/gear/guardian.md
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from make_gear_ancient_armor import ArmorPainter, ARMOR_BONES, H, B, RA, LA, RL, LL, RB, LB, PIECE_BONES, VIEWS, \
    pair, subset  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from guardian_common import GuardianPaint, KNOT_EMBLEM  # noqa: E402


class GuardianArmorPainter(GuardianPaint, RyumonPaint, ArmorPainter):
    def paint_void(self, c):
        return (16, 18, 20)


def new_armor(name, tex_h=128):
    g = GeoModel(name, 128, tex_h)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    return g


def octagon(g, bone, name, center, apothem, z, mat, side=5, glow=False):
    """八角の輪（頭の後ろの後光など）：8本の棒を、輪の中心のまわりに置く（Bedrock の座標）。"""
    cx, cy = center
    for k in range(8):
        a = math.radians(90 + 45 * k)
        mx, my = cx + apothem * math.cos(a), cy + apothem * math.sin(a)
        rot = (0, 0, -45 * k % 360) if k % 4 else None
        g.cube(bone, f"{name}_{k}", (mx - side / 2.0, my - 0.5, z), (side, 1, 1), mat,
               rot=rot, pivot=(mx, my, z + 0.5) if rot else None, glow=glow)


# ================================================================ ナイト：守護者の外装（重鎧）

def build_knight():
    g = new_armor("guardian_knight")
    # 兜：積んだ石の角ばった兜・てっぺんの青銅の帯・左右に立つ石の刃（外へ開く）・横一本の翠の覗き穴（光る）・格子の顔の板
    g.cube(H, "helm_shell", (-4, 24, -4), (8, 8, 8), "gstone", inflate=1.0, tags={"steps": 3})
    g.cube(H, "cap", (-4.5, 33, -4.5), (9, 1, 9), "gbronze", inflate=0.1)
    g.cube(H, "cap_stud", (-0.5, 34.1, -0.5), (1, 1, 1), "jade", glow=True)
    pair(g, H, H, "fin", (-7, 27, -2), (2, 7, 4), "gstone", rot=(0, 0, -16), pivot=(-5.5, 27, 0),
         tags={"edge_bright": "top", "steps": 3})
    pair(g, H, H, "fin_tip", (-7, 34, -1.5), (2, 2, 3), "gbronze", rot=(0, 0, -16), pivot=(-5.5, 27, 0))
    g.cube(H, "brow", (-5, 28.5, -5.6), (10, 3, 1), "gstone", inflate=0.15, tags={"edge_bright": "top"})
    g.cube(H, "visor", (-4, 27.5, -5.3), (8, 1, 1), "jade", glow=True)
    g.cube(H, "faceplate", (-5, 24, -5.6), (10, 3, 1), "gstone", inflate=0.15, tags={"lattice": 3})
    g.cube(H, "neck", (-5, 22.5, 4.5), (10, 3, 1), "gstone", inflate=0.15, tags={"steps": 2})
    # 外装：暗い石の下地・格子の胸板・青銅の枠の中の翠の記録核（光る）・青銅の帯・3枚の石の垂れ（前と後ろ）・背の石の背びれ
    g.cube(B, "shell", (-4, 12, -2), (8, 12, 4), "gstone_dark", inflate=1.01, tags={"steps": 3})
    g.cube(B, "breastplate", (-4.5, 15, -3.8), (9, 8, 1), "gstone", tags={"lattice": 3, "edge_bright": "top"})
    g.cube(B, "core_frame", (-2, 17, -4.6), (4, 4, 1), "gbronze")
    g.cube(B, "core", (-1, 18, -5.1), (2, 2, 1), "jade", glow=True)
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "gbronze", inflate=0.2, tags={"steps": 2})
    for k, (x, y, h) in enumerate(((-4.5, 7, 4), (-1.5, 6, 5), (1.5, 7, 4))):
        g.cube(B, f"fauld_{k}", (x, y, -3.7), (3, h, 1), "gstone", tags={"lattice": 3})
        g.cube(B, f"fauld_back_{k}", (x, y, 2.7), (3, h, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "spine_fin", (-1, 13, 2.9), (2, 13, 2), "gstone", tags={"steps": 2, "edge_bright": "top"})
    g.cube(B, "spine_line", (-0.5, 15, 4.75), (1, 8, 1), "jade", glow=True)
    # 腕：暗い石の籠手・立方体の石の肩当て（青銅の帯・外に翠の鋲）・格子の腕当てと青銅の輪
    pair(g, RA, LA, "arm_shell", (-8, 12, -2), (4, 12, 4), "gstone_dark", inflate=0.5)
    pair(g, RA, LA, "pauldron", (-10, 18.5, -3.5), (6, 6, 7), "gstone", tags={"steps": 3, "edge_bright": "top"})
    pair(g, RA, LA, "pauldron_band", (-10.25, 20.5, -3.75), (6, 1, 7), "gbronze", inflate=0.1)
    pair(g, RA, LA, "pauldron_stud", (-10.6, 21.75, -0.5), (1, 1, 1), "jade", glow=True)
    pair(g, RA, LA, "vambrace", (-9, 12, -3), (6, 4, 6), "gstone", tags={"lattice": 3})
    pair(g, RA, LA, "vambrace_ring", (-9.25, 15.5, -3.25), (6, 1, 6), "gbronze", inflate=0.1)
    # 脚甲：暗い石の腿・格子の石の前板・青銅の膝当てと翠の鋲
    pair(g, RL, LL, "leg_shell", (-3.9, 4, -2), (4, 8, 4), "gstone_dark", inflate=0.5)
    pair(g, RL, LL, "plate", (-3.6, 6.5, -3.1), (3, 5, 1), "gstone", tags={"lattice": 3})
    pair(g, RL, LL, "knee", (-3.6, 4.5, -3.5), (3, 2, 1), "gbronze")
    pair(g, RL, LL, "knee_stud", (-2.6, 5, -4.0), (1, 1, 1), "jade", glow=True)
    # 鉄靴：角ばった石の靴（段の溝）・青銅のつま先・かかとの石のひれ
    pair(g, RB, LB, "boot", (-4.1, 0, -2.6), (4, 4, 5), "gstone", inflate=0.5, tags={"steps": 2})
    pair(g, RB, LB, "toe_cap", (-3.6, -0.5, -4.0), (3, 2, 1), "gbronze")
    pair(g, RB, LB, "heel_fin", (-2.6, 0, 2.7), (1, 3, 2), "gstone")
    return g


# ================================================================ ウィザード：守護者の外装（法衣）

def build_wizard():
    g = new_armor("guardian_wizard")
    # 尖り帽：段の石の尖り帽（ピラミッド）・青銅の帯・てっぺんの青銅の台の上に浮かぶ翠の立方体（光る）
    g.cube(H, "band", (-4.5, 29, -4.5), (9, 2, 9), "gbronze", inflate=0.1)
    g.cube(H, "tier_0", (-4.5, 31, -4.5), (9, 2, 9), "gstone", tags={"edge_bright": "top"})
    g.cube(H, "tier_1", (-3.5, 33, -3.5), (7, 2, 7), "gstone", tags={"edge_bright": "top"})
    g.cube(H, "tier_2", (-2.5, 35, -2.5), (5, 2, 5), "gstone", tags={"edge_bright": "top"})
    g.cube(H, "tier_3", (-1.5, 37, -1.5), (3, 2, 3), "gstone", tags={"edge_bright": "top"})
    g.cube(H, "stand", (-0.5, 39, -0.5), (1, 1, 1), "gbronze")
    g.cube(H, "float_core", (-1, 40.5, -1), (2, 2, 2), "jade", glow=True)
    g.cube(H, "brow_gem", (-0.5, 29.5, -4.9), (1, 1, 1), "jade", glow=True)
    # 法衣：白い法衣（背に結び目の格子の印）・四角の青銅の襟・翠の立方体の垂れ飾り（光る）・青銅の帯・背の格子の石板
    g.cube(B, "robe", (-4, 12, -2), (8, 12, 4), "ivory", inflate=0.75,
           tags={"split": "gbronze", "emblem": (KNOT_EMBLEM, "gbronze", 2)})
    g.cube(B, "collar_front", (-4.5, 21.5, -3.9), (9, 2, 1), "gbronze")
    g.cube(B, "collar_back", (-4.5, 21.5, 2.9), (9, 2, 1), "gbronze")
    pair(g, B, B, "collar_side", (-4.9, 21.5, -3), (1, 2, 6), "gbronze")
    g.cube(B, "pendant_chain", (-0.5, 17.5, -4.2), (1, 4, 1), "gbronze", inflate=-0.15)
    g.cube(B, "pendant", (-1, 15.5, -4.6), (2, 2, 2), "jade", glow=True)
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "gbronze")
    g.cube(B, "tablet", (-3, 13, 3.3), (6, 7, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "tablet_core", (-0.5, 15.5, 4.2), (1, 1, 1), "jade", glow=True)
    # 腕：白い袖・2枚重ねの平たい石の肩の板・青銅の袖口
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "ivory", inflate=0.5)
    pair(g, RA, LA, "slab_top", (-9.5, 21.5, -3), (5, 2, 6), "gstone", rot=(0, 0, -15), pivot=(-5, 23, 0),
         tags={"edge_bright": "bottom"})
    pair(g, RA, LA, "slab_low", (-9.75, 19.25, -2.5), (4, 2, 5), "gstone", rot=(0, 0, -15), pivot=(-5, 21, 0),
         tags={"edge_bright": "bottom"})
    pair(g, RA, LA, "cuff", (-8.5, 12, -2.5), (5, 2, 5), "gbronze", inflate=0.2)
    # 下衣：白いすそ（すそに青銅の四角の連なり）と、前に下がる格子の石の板
    pair(g, RL, LL, "skirt", (-3.9, 2, -2), (4, 10, 4), "ivory", inflate=0.75, tags={"border": "gbronze"})
    pair(g, RL, LL, "panel", (-3.5, 4, -3.3), (3, 6, 1), "gstone", tags={"lattice": 3})
    # 布靴：白い靴・石の底（厚い）・青銅のつま先の板
    pair(g, RB, LB, "shoe", (-3.9, 0.5, -2), (4, 2, 4), "ivory", inflate=0.6)
    pair(g, RB, LB, "sole", (-3.9, -0.6, -2.6), (4, 1, 5), "gstone", inflate=0.1)
    pair(g, RB, LB, "toe_plate", (-3.4, 0.6, -3.15), (3, 1, 1), "gbronze")
    return g


# ================================================================ ヒーラー：守護者の外装（白衣）

def build_healer():
    g = new_armor("guardian_healer")
    # 白頭巾：肩までの白いヴェール・額の青銅の輪と翠（光る）・頭の後ろの八角の青銅の後光（てっぺんに翠）
    g.cube(H, "veil_top", (-5, 32, -5), (10, 1, 10), "ivory")
    pair(g, H, H, "veil_side", (-5, 21, -5), (1, 11, 10), "ivory", tags={"trim": "gbronze"})
    g.cube(H, "veil_back", (-4, 21, 4), (8, 11, 1), "ivory", tags={"trim": "gbronze"})
    g.cube(H, "circlet", (-4.5, 29.5, -5.3), (9, 1, 1), "gbronze")
    g.cube(H, "circlet_gem", (-0.5, 30.3, -5.75), (1, 1, 1), "jade", glow=True)
    octagon(g, H, "halo", (0, 30.5), 7.0, 5.6, "gbronze", side=5)
    g.cube(H, "halo_gem", (-0.5, 37.0, 5.35), (1, 1, 1), "jade", glow=True)
    # 白衣：白い衣・肩の白い掛け（青銅のふち）・円い青銅の胸当て（石の円盤と翠）・2本の石の数珠（先に翠）・青銅の帯
    g.cube(B, "robe", (-4, 12, -2), (8, 12, 4), "ivory", inflate=0.75, tags={"split": "gbronze"})
    g.cube(B, "mantle", (-5.5, 20.5, -3.5), (11, 3, 7), "ivory", tags={"trim": "gbronze"})
    g.cube(B, "gorget", (-3.5, 17, -4.1), (7, 5, 1), "gbronze", tags={"edge_bright": "top"})
    g.cube(B, "gorget_disk", (-2.5, 17.5, -4.55), (5, 4, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "gorget_core", (-0.5, 19, -5.0), (1, 1, 1), "jade", glow=True)
    for side, x in (("r", -2.5), ("l", 1.5)):
        for k, y in enumerate((15.5, 14.0)):
            g.cube(B, f"bead_{side}_{k}", (x, y, -4.3), (1, 1, 1), "gstone", inflate=0.1)
        g.cube(B, f"bead_{side}_end", (x, 12.5, -4.3), (1, 1, 1), "jade", glow=True)
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "gbronze")
    # 袖：白い広い袖口（青銅のふち）と石の腕輪
    pair(g, RA, LA, "sleeve", (-8, 14, -2), (4, 10, 4), "ivory", inflate=0.5)
    pair(g, RA, LA, "wide_cuff", (-9, 12, -3), (6, 3, 6), "ivory", tags={"trim": "gbronze"})
    pair(g, RA, LA, "bracelet", (-8.75, 15, -2.75), (5, 1, 5), "gstone", inflate=0.2)
    # 白下衣：白いすそと、前と横に下がる格子の石のタイル
    pair(g, RL, LL, "skirt", (-3.9, 2, -2), (4, 10, 4), "ivory", inflate=0.75, tags={"border": "gbronze"})
    pair(g, RL, LL, "tile_front", (-3.6, 4, -3.3), (3, 7, 1), "gstone", tags={"lattice": 3})
    pair(g, RL, LL, "tile_side", (-5.0, 5, -1.5), (1, 6, 3), "gstone", tags={"lattice": 3})
    # 白靴：白い布の足・石の底・青銅の紐と足首の輪
    pair(g, RB, LB, "foot", (-3.9, 0, -2), (4, 2, 4), "ivory", inflate=0.5)
    pair(g, RB, LB, "sole", (-3.9, -0.6, -2.6), (4, 1, 5), "gstone", inflate=0.1)
    pair(g, RB, LB, "strap", (-4.4, 1.0, -2.5), (5, 1, 5), "gbronze", inflate=0.1)
    pair(g, RB, LB, "ankle_ring", (-4.4, 2.6, -2.5), (5, 1, 5), "gbronze", inflate=0.1)
    return g


# ================================================================ アーチャー：守護者の外装（革鎧）

def build_archer():
    g = new_armor("guardian_archer")
    # 羽根帽：石の帽子（段）と前のつば・右目の翠の片眼鏡（光る。青銅の枠）・左に扇に開いた3枚の石の羽根（先に翠）
    g.cube(H, "cap", (-4.5, 29.5, -4.5), (9, 3, 9), "gstone", inflate=0.1, tags={"steps": 2})
    g.cube(H, "cap_top", (-3.5, 32.5, -3.5), (7, 1, 7), "gstone")
    g.cube(H, "brim", (-4.5, 29, -6.2), (9, 1, 2), "gstone", tags={"edge_bright": "bottom"})
    g.cube(H, "band", (-4.5, 29.4, -4.5), (9, 1, 9), "gbronze", inflate=0.25)
    g.cube(H, "monocle_rim", (-3.75, 26.25, -5.2), (2, 2, 1), "gbronze", inflate=0.25)
    g.cube(H, "monocle", (-3.5, 26.5, -5.55), (2, 2, 1), "jade", glow=True)
    g.cube(H, "monocle_arm", (-4.6, 27, -5.0), (1, 3, 1), "gbronze", inflate=-0.1)
    for k, (z, h, ang) in enumerate(((-1.5, 6, -10), (0.0, 7, -24), (1.5, 6, -38))):
        g.cube(H, f"plume_{k}", (4.5, 30, z), (1, h, 1), "gstone", rot=(0, 0, ang), pivot=(5, 30, z + 0.5),
               tags={"edge_bright": "top"})
        g.cube(H, f"plume_tip_{k}", (4.5, 30 + h, z), (1, 1, 1), "jade", rot=(0, 0, ang), pivot=(5, 30, z + 0.5),
               glow=True)
    # 革鎧：暗い革の胴・前と後ろの石の小札（格子）・右の肩だけの石の板・背の石の矢筒（段・青銅の帯・翠のふた・白い矢羽根）
    g.cube(B, "vest", (-4, 12, -2), (8, 12, 4), "leather_dark", inflate=0.6, tags={"stitch": True})
    g.cube(B, "lamellar", (-4.5, 14, -3.6), (9, 8, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "lamellar_back", (-4.5, 13, 2.6), (9, 8, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "gbronze", inflate=0.15)
    g.cube(B, "quiver", (0, 12, 3.6), (3, 11, 3), "gstone", rot=(0, 0, -16), pivot=(1.5, 17.5, 5.1), tags={"steps": 3})
    g.cube(B, "quiver_band", (-0.25, 20, 3.35), (3, 1, 3), "gbronze", inflate=0.25, rot=(0, 0, -16), pivot=(1.5, 17.5, 5.1))
    g.cube(B, "quiver_cap", (0.5, 23, 4.1), (2, 1, 2), "jade", glow=True, rot=(0, 0, -16), pivot=(1.5, 17.5, 5.1))
    g.cube(B, "fletching", (0.25, 24, 3.85), (2, 2, 2), "feather", rot=(0, 0, -16), pivot=(1.5, 17.5, 5.1),
           tags={"along": "j", "tip": "end"})
    g.cube(RA, "shoulder_r", (-9.5, 21, -3), (5, 2, 6), "gstone", rot=(0, 0, -18), pivot=(-5, 22, 0),
           tags={"edge_bright": "bottom"})
    # 腕：暗い革の袖。弓を持つ左の腕に、格子の石の腕当てと青銅の輪
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "leather_dark", inflate=0.4)
    g.cube(LA, "bracer", (3.5, 12, -2.5), (5, 4, 5), "gstone", tags={"lattice": 3})
    g.cube(LA, "bracer_ring", (3.25, 16, -2.75), (5, 1, 5), "gbronze", inflate=0.25)
    # 革脚絆：暗い革・石のすね当てと翠の鋲
    pair(g, RL, LL, "leggings", (-3.9, 3, -2), (4, 9, 4), "leather_dark", inflate=0.45)
    pair(g, RL, LL, "shin_plate", (-3.4, 4, -3.0), (3, 5, 1), "gstone", tags={"lattice": 3})
    pair(g, RL, LL, "shin_stud", (-2.4, 7.5, -3.45), (1, 1, 1), "jade", glow=True)
    # 長靴：暗い革の長靴・青銅の前の板・石のかかと
    pair(g, RB, LB, "boot", (-3.9, 0, -2), (4, 5, 4), "leather_dark", inflate=0.55)
    pair(g, RB, LB, "greave", (-3.4, 0.5, -2.85), (3, 4, 1), "gbronze")
    pair(g, RB, LB, "heel", (-3.4, -0.6, 1.0), (3, 1, 2), "gstone")
    return g


# ================================================================ ローグ：守護者の外装（黒装束）

def build_rogue():
    g = new_armor("guardian_rogue")
    # 覆面：角ばった黒い頭巾（てっぺんが尖る）・顔をすべておおう石の面と、縦一本の翠のすじ（光る）
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "obsidian")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "obsidian", tags={"trim": "gbronze"})
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "obsidian")
    g.cube(H, "hood_peak", (-2.5, 33, -4), (5, 1, 8), "obsidian")
    g.cube(H, "hood_peak_2", (-1, 34, -2.5), (2, 1, 5), "obsidian")
    g.cube(H, "faceplate", (-4, 23.5, -5.2), (8, 8, 1), "gstone", tags={"edge_bright": "top"})
    g.cube(H, "face_slit", (-0.5, 24.5, -5.6), (1, 6, 1), "jade", glow=True)
    # 黒装束：黒い胴・右肩だけの2枚の石の肩当て・ななめの青銅の帯と翠の玉3つ（光る）・背の石の肩掛け（重なる板）
    g.cube(B, "garb", (-4, 12, -2), (8, 12, 4), "obsidian", inflate=0.6, tags={"trim": "gbronze"})
    g.cube(B, "sash", (-0.5, 11.5, -3.9), (1, 14, 1), "gbronze", rot=(0, 0, 35), pivot=(0, 18, -3.4))
    for k, y in enumerate((14.5, 17.5, 20.5)):
        g.cube(B, f"sash_bead_{k}", (-0.5, y, -4.35), (1, 1, 1), "jade", glow=True, rot=(0, 0, 35), pivot=(0, 18, -3.4))
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "obsidian", inflate=0.2, tags={"trim": "gbronze"})
    g.cube(B, "mantle", (-4.5, 14, 2.8), (9, 9, 1), "obsidian")
    g.cube(B, "mantle_plate_0", (-4, 18, 3.3), (8, 5, 1), "gstone", tags={"lattice": 3})
    g.cube(B, "mantle_plate_1", (-3, 13.5, 3.3), (6, 4, 1), "gstone", tags={"lattice": 3})
    g.cube(RA, "shoulder_top", (-9.5, 21, -3), (6, 2, 6), "gstone", rot=(0, 0, -16), pivot=(-5, 22, 0), tags={"edge_bright": "bottom"})
    g.cube(RA, "shoulder_low", (-9.75, 19.25, -3), (5, 2, 6), "gstone", rot=(0, 0, -16), pivot=(-5, 20, 0), tags={"edge_bright": "bottom"})
    # 腕：黒い袖・石の前腕の当て・青銅の手首の輪
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "obsidian", inflate=0.4)
    pair(g, RA, LA, "forearm", (-8.5, 13, -2.5), (5, 3, 5), "gstone", tags={"lattice": 3})
    pair(g, RA, LA, "wrist_ring", (-8.5, 12, -2.5), (5, 1, 5), "gbronze", inflate=0.15)
    # 黒脚絆：黒い脚絆・石の膝当て・腿の翠のすじ（光る）
    pair(g, RL, LL, "leggings", (-3.9, 3, -2), (4, 9, 4), "obsidian", inflate=0.45)
    pair(g, RL, LL, "knee_plate", (-3.5, 5, -3.0), (3, 3, 1), "gstone", tags={"edge_bright": "top"})
    pair(g, RL, LL, "thigh_rune", (-2.4, 8.5, -2.95), (1, 3, 1), "jade", glow=True)
    # 足袋：黒い割れたつま先の足袋・石の底・青銅の足首の輪
    pair(g, RB, LB, "tabi", (-3.9, 0, -2), (4, 3, 4), "obsidian", inflate=0.55)
    pair(g, RB, LB, "tabi_toe", (-3.4, -0.55, -3.6), (3, 1, 1), "obsidian", tags={"toe_split": True})
    pair(g, RB, LB, "sole", (-3.9, -0.7, -2.6), (4, 1, 5), "gstone")
    pair(g, RB, LB, "ankle_ring", (-4.4, 2.5, -2.5), (5, 1, 5), "gbronze", inflate=0.1)
    return g


# ================================================================ モンク：守護者の外装（道着）

def build_monk():
    g = new_armor("guardian_monk")
    # 鉢巻：石の鉢巻（格子）・前に翠の玉3つ（まん中が大きい。光る）・後ろの結び目から下がる2本の石の房
    g.cube(H, "band", (-4.5, 28, -4.5), (9, 2, 9), "gstone", inflate=0.15, tags={"lattice": 3})
    g.cube(H, "bead_mid", (-1, 28, -5.6), (2, 2, 1), "jade", glow=True)
    pair(g, H, H, "bead_side", (-3.0, 28.5, -5.3), (1, 1, 1), "jade", glow=True)
    g.cube(H, "knot", (-1, 28, 4.6), (2, 2, 1), "gbronze")
    pair(g, H, H, "tassel", (-1.6, 24.5, 4.7), (1, 4, 1), "gstone", rot=(8, 0, 6), pivot=(-1.1, 28.5, 5.2), tags={"steps": 2})
    # 道着：袖のない白い道着（青銅のふち）・大きな石の数珠（首から前へU字に下がり、下に翠の玉）・青銅の格子の帯
    g.cube(B, "vest", (-4, 12, -2), (8, 12, 4), "ivory", inflate=0.7, tags={"trim": "gbronze", "split": "gbronze"})
    for side in ("r", "l"):
        sx = 1 if side == "l" else -1
        for k, (x, y, z) in enumerate(((4.0, 21.5, -2.5), (3.1, 20.0, -3.7), (1.7, 18.6, -4.0))):
            bx = x * sx - 1.0
            # 数珠の玉は少し暗い石にする（明るい石だと、白い道着にとけて前から見えなかった。11月26日）
            g.cube(B, f"rosary_{side}_{k}", (bx, y, z - 1.0), (2, 2, 2), "gstone", inflate=0.15, tags={"base": 2.3})
    g.cube(B, "rosary_jade", (-1, 16.8, -5.0), (2, 2, 2), "jade", glow=True)
    g.cube(B, "rosary_back", (-4, 21.5, 2.2), (8, 2, 2), "gstone", tags={"lattice": 3})
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "gbronze", tags={"lattice": 3})
    # 腕：袖はない（肌）。肩の小さな石の板と、前腕の石の輪・青銅の輪
    pair(g, RA, LA, "shoulder", (-8.5, 22, -2.5), (5, 2, 5), "ivory", tags={"trim": "gbronze"})
    pair(g, RA, LA, "ring_stone", (-8.5, 13, -2.5), (5, 1, 5), "gstone", inflate=0.2)
    pair(g, RA, LA, "ring_bronze", (-8.5, 15, -2.5), (5, 1, 5), "gbronze", inflate=0.2)
    # 袴：白い袴（ひだ）・すその石のおもり
    pair(g, RL, LL, "hakama", (-3.9, 3, -2), (4, 9, 4), "ivory", inflate=0.9, tags={"pleats": True, "border": "gbronze"})
    pair(g, RL, LL, "hem_weight", (-4.9, 2.3, -3.1), (1, 1, 1), "gstone")
    # 草鞋：石の下駄（厚い台と2本の歯）・翠の鼻緒（光る）・白い巻き布
    pair(g, RB, LB, "wrap", (-3.9, 0.5, -2), (4, 2, 4), "ivory", inflate=0.5, tags={"wrap": True})
    pair(g, RB, LB, "platform", (-3.9, -0.6, -2.6), (4, 1, 5), "gstone", inflate=0.1)
    pair(g, RB, LB, "tooth_front", (-3.9, -1.6, -1.8), (4, 1, 1), "gstone_dark")
    pair(g, RB, LB, "tooth_back", (-3.9, -1.6, 1.2), (4, 1, 1), "gstone_dark")
    pair(g, RB, LB, "thong", (-2.4, 0.4, -2.95), (1, 1, 1), "jade", glow=True)
    return g


SETS = {
    "knight": {"build": build_knight, "title": "守護者の外装（ナイト）",
               "pieces": (("兜", "head", "guardian_knight_helm"), ("外装（重鎧）", "chest", "guardian_knight_shell"),
                          ("脚甲", "legs", "guardian_knight_greaves"), ("鉄靴", "feet", "guardian_knight_sabatons")),
               "note": "積んだ白い石の角ばった兜に、\n左右に立つ石の刃と翠の覗き穴。\n胸に青銅の枠の翠の記録核。\n立方体の肩当て・背の石の背びれ。"},
    "wizard": {"build": build_wizard, "title": "守護者の外装（ウィザード）",
               "pieces": (("尖り帽", "head", "guardian_wizard_hat"), ("外装（法衣）", "chest", "guardian_wizard_shell"),
                          ("下衣", "legs", "guardian_wizard_legwear"), ("布靴", "feet", "guardian_wizard_shoes")),
               "note": "段の石の尖り帽（ピラミッド）と、\nてっぺんに浮かぶ翠の立方体。\n四角の青銅の襟と翠の垂れ飾り。\n背に格子の石板・平たい石の肩の板。"},
    "healer": {"build": build_healer, "title": "守護者の外装（ヒーラー）",
               "pieces": (("白頭巾", "head", "guardian_healer_hood"), ("外装（白衣）", "chest", "guardian_healer_shell"),
                          ("白下衣", "legs", "guardian_healer_legwear"), ("白靴", "feet", "guardian_healer_shoes")),
               "note": "肩までの白いヴェールと、頭の後ろの\n八角の青銅の後光（てっぺんに翠）。\n円い胸当てと、先に翠の石の数珠。\n石のタイルのすそ・石の底の靴。"},
    "archer": {"build": build_archer, "title": "守護者の外装（アーチャー）",
               "pieces": (("羽根帽", "head", "guardian_archer_cap"), ("外装（革鎧）", "chest", "guardian_archer_shell"),
                          ("革脚絆", "legs", "guardian_archer_leggings"), ("長靴", "feet", "guardian_archer_boots")),
               "note": "石の帽子と、右目の翠の片眼鏡。\n左に扇に開いた石の羽根（先に翠）。\n石の小札の胴・右肩の石の板。\n背に石の矢筒（翠のふた）。"},
    "rogue": {"build": build_rogue, "title": "守護者の外装（ローグ）",
              "pieces": (("覆面", "head", "guardian_rogue_mask"), ("外装（黒装束）", "chest", "guardian_rogue_shell"),
                         ("黒脚絆", "legs", "guardian_rogue_leggings"), ("足袋", "feet", "guardian_rogue_tabi")),
              "note": "顔をすべておおう石の面と、\n縦一本の翠のすじ。角ばった頭巾。\n右肩だけの石の肩当て・翠の玉の帯。\n背に重なる石の肩掛け。"},
    "monk": {"build": build_monk, "title": "守護者の外装（モンク）",
             "pieces": (("鉢巻", "head", "guardian_monk_headband"), ("外装（道着）", "chest", "guardian_monk_shell"),
                        ("袴", "legs", "guardian_monk_hakama"), ("草鞋", "feet", "guardian_monk_sandals")),
             "note": "石の鉢巻に翠の玉3つと石の房。\n袖のない道着と、大きな石の数珠\n（下に翠の玉）。前腕の石の輪。\n白い袴と、石の下駄。"},
}


def paint(g):
    g.pack()
    return g.paint(GuardianArmorPainter(g), frames=1)


def preview(g, tex, path, scale=10):
    from render import render_many
    from PIL import Image
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    ims = [render_many([(man, mtex), (g, tex)], yaw=y, pitch=p, scale=scale, size=(260, 460), center=(0, 20, 0))
           for y, p, _ in VIEWS]
    s = Image.new("RGBA", (260 * 4, 460), (40, 44, 52, 255))
    for k, im in enumerate(ims):
        s.alpha_composite(im, (260 * k, 0))
    s.save(path)


def make_sheet(repo, key, g, tex, path):
    import guardian_icons
    from setsheet import make_set_sheet
    cfg = SETS[key]
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    full = [(man, mtex), (g, tex)]
    icons = [(label, guardian_icons.icon(item)) for label, _, item in cfg["pieces"]]
    piece_views = [(label, -145, 14, [(man, mtex), (subset(g, PIECE_BONES[slot]), tex)])
                   for label, slot, _ in cfg["pieces"]]
    job = cfg["title"].split("（")[1].rstrip("）")
    items = [
        {"label": "フルセット", "rarity": "エピック・Lv22・" + job + "専用", "note": cfg["note"], "pairs": full,
         "views": [(lab, y, p) for y, p, lab in VIEWS], "icons": icons},
        {"label": "部位ごと", "rarity": "着た場所だけ表示（翠は光る）", "note": "", "pairs": full, "views": piece_views,
         "icons": icons, "icon_cell": 40},
    ]
    make_set_sheet(path, cfg["title"], "守護者シリーズ（5階・古代守護者のレア）／ 11月26日に形から作り直した【補完】／ 128×128の絵",
                   items, row_h=460)


def save_icons(repo, keys):
    import json
    import guardian_icons
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for key in keys:
        for _, _, name in SETS[key]["pieces"]:
            guardian_icons.icon(name).save(os.path.join(base, "textures/item", name + ".png"))
            with open(os.path.join(base, "models/item", name + ".json"), "w", encoding="utf-8") as fp:
                json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, fp,
                          ensure_ascii=False, indent=2)
                fp.write("\n")


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_guardian"
    os.makedirs(out, exist_ok=True)
    only = sys.argv[3].split(",") if len(sys.argv) > 3 and not sys.argv[3].startswith("--") else list(SETS)
    preview_only = "--preview" in sys.argv
    for key in only:
        g = SETS[key]["build"]()
        frames = paint(g)
        used = max(c.uv[1] + c.block()[1] for c in g.elements)
        print(g.name, "部品", len(g.elements), "光る部品", sum(1 for c in g.elements if c.glow), "絵の使った高さ", used, "/", g.tex_h)
        preview(g, frames[0], os.path.join(out, "guardian_%s_preview.png" % key))
        if preview_only:
            continue
        g.save(repo, "armor", frames)
        make_sheet(repo, key, g, frames[0], os.path.join(repo, "docs/gear", "guardian_%s_sheet.png" % key))
    if not preview_only:
        save_icons(repo, only)


if __name__ == "__main__":
    main()
