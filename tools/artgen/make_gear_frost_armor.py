"""霜銀シリーズの防具（第2章・6階からの職業ごとの装備）：6職業 × 頭・胴・脚・足（GeckoLib）。11月26日に形から作り直した。

  python3 tools/artgen/make_gear_frost_armor.py <リポジトリ> [確認画像の出力先] [職業,職業]

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」【補完】。
前は流紋シリーズの形を霜銀の色に塗り直していた。凍結した村の冬の装いとして、職業ごとに形から作り直した：
  ナイト    ＝丸い鉢と雪の結晶のとさか・T字の覗き穴・白い毛皮の襟と肩・横に重なる板の胸・丸い肩当て・雪の結晶の前垂れ
  ウィザード＝先が横へ垂れて毛皮の玉の付いた尖り帽と耳当て・トグル留めの長い外套と赤い編みのマフラー・腰の雪の書・毛皮のすそ
  ヒーラー  ＝顔のまわりを毛皮が囲む白頭巾（後ろへ長い尾）・氷青の大きな肩掛け（背に雪の結晶）・段のすそ・帯の氷の鈴・毛皮の長靴
  アーチャー＝耳当ての毛皮帽と雪眼鏡と羽根・白い雪の迷彩のポンチョ・腰の矢筒・綿入れの脚絆・白樺の枠のかんじき
  ローグ    ＝灰の毛皮のふちの頭巾と霜銀の面頬・氷の目・高い襟の外套（ななめの合わせ）・左肩だけの短い肩掛け・腿の短剣
  モンク    ＝赤い編みの鉢巻と白い毛皮の耳当て・綿入れの道着の上に蓑（わらの雨具）・脚絆・厚い靴下と霜銀の滑り止めの草鞋
1つの職業＝1つのモデル（geo/armor/frost_silver_<職業>.geo.json）。座標は Bedrock（体の右が −x、表が −z）。
色と塗り方は frost_common.py。持ち物の絵（16×16 → enhance_icons.py で32×32）は frost_icons.py。仕様書：docs/gear/frost_silver.md
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from make_gear_ancient_armor import ArmorPainter, ARMOR_BONES, H, B, RA, LA, RL, LL, RB, LB, PIECE_BONES, VIEWS, \
    pair, subset  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from frost_common import FrostPaint, SNOWFLAKE_5, SNOWFLAKE_7  # noqa: E402


class FrostArmorPainter(FrostPaint, RyumonPaint, ArmorPainter):
    def paint_void(self, c):
        return (14, 17, 22)


def new_armor(name, tex_h=128):
    g = GeoModel(name, 128, tex_h)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    return g


# ================================================================ ナイト：霜銀の重鎧

def build_knight():
    g = new_armor("frost_silver_knight")
    # 兜：丸い鉢（てっぺんは段で丸く）・雪の結晶のとさか（氷の結晶の六つの腕）・T字の覗き穴・耳の丸い当て板・首に白い毛皮の襟
    g.cube(H, "helm_shell", (-4, 24, -4), (8, 8, 8), "fsilver", inflate=1.0)
    g.cube(H, "dome", (-3, 33, -3), (6, 1, 6), "fsilver", inflate=0.45)
    g.cube(H, "dome_top", (-2, 34, -2), (4, 1, 4), "fsilver", inflate=0.25)
    g.cube(H, "crest_v", (-0.5, 35.25, -0.5), (1, 5, 1), "icec")
    g.cube(H, "crest_a", (-0.5, 35.25, -0.5), (1, 5, 1), "icec", rot=(0, 0, 60), pivot=(0, 37.75, 0))
    g.cube(H, "crest_b", (-0.5, 35.25, -0.5), (1, 5, 1), "icec", rot=(0, 0, -60), pivot=(0, 37.75, 0))
    g.cube(H, "crest_core", (-1, 37.25, -1), (2, 1, 2), "fsilver", inflate=0.15)
    g.cube(H, "faceplate", (-5, 24, -5.5), (10, 7, 1), "fsilver", inflate=0.2,
           tags={"rivets": [(-3.5, 25.0), (3.5, 25.0)], "edge_bright": "top"})
    g.cube(H, "slit_h", (-3.5, 28, -5.95), (7, 1, 1), "void")
    g.cube(H, "slit_v", (-0.5, 25, -5.95), (1, 3, 1), "void")
    pair(g, H, H, "rondel", (-5.75, 26, -1.5), (1, 3, 3), "fsilver", inflate=0.1)
    pair(g, H, H, "rondel_gem", (-6.15, 27, -0.5), (1, 1, 1), "icec")
    g.cube(H, "fur_collar", (-5.5, 22, -5.5), (11, 3, 11), "snowfur")
    # 胴：キルトの下着（暗い霜銀）・横に重なる3枚の胸の板と氷の結晶・白い毛皮の肩の襟・革の帯・雪の結晶の前垂れ（前と後ろ）・背の板
    g.cube(B, "gambeson", (-4, 12, -2), (8, 12, 4), "fsilver_dark", inflate=1.01, tags={"quilt": True})
    g.cube(B, "lame_0", (-4.5, 20, -3.8), (9, 3, 1), "fsilver", tags={"edge_bright": "bottom"})
    g.cube(B, "lame_1", (-4, 17, -3.65), (8, 3, 1), "fsilver", tags={"edge_bright": "bottom"})
    g.cube(B, "lame_2", (-3.5, 14, -3.5), (7, 3, 1), "fsilver", tags={"edge_bright": "bottom"})
    g.cube(B, "chest_gem", (-1, 18, -4.45), (2, 2, 1), "icec")
    g.cube(B, "fur_mantle", (-6, 21.5, -4), (12, 3, 8), "snowfur")
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "leather_dark", inflate=0.25)
    g.cube(B, "buckle", (-1, 11, -4.25), (2, 2, 1), "fsilver", tags={"no_snow": True})
    flake = {"trim": "silver", "emblem": (SNOWFLAKE_5, "white", 1, ("front", "back"))}
    g.cube(B, "tabard", (-3, 4, -4.15), (6, 7, 1), "frostblue", tags=flake)
    g.cube(B, "tabard_back", (-3, 4, 3.15), (6, 7, 1), "frostblue", tags=flake)
    g.cube(B, "back_plate", (-4.5, 14, 2.8), (9, 8, 1), "fsilver", tags={"lames": 3})
    # 腕：キルトの袖・丸い肩当て（段のふた・下に白い毛皮のふち）・肘当て・手首の板と毛皮
    pair(g, RA, LA, "arm_shell", (-8, 12, -2), (4, 12, 4), "fsilver_dark", inflate=0.5, tags={"quilt": True})
    pivot = (-5, 23, 0)
    pair(g, RA, LA, "pauldron", (-9.5, 19.5, -3.5), (6, 4, 7), "fsilver", rot=(0, 0, -12), pivot=pivot, tags={"lames": 2})
    pair(g, RA, LA, "pauldron_cap", (-9, 23.5, -3), (5, 1, 6), "fsilver", inflate=0.1, rot=(0, 0, -12), pivot=pivot)
    pair(g, RA, LA, "pauldron_fur", (-9.75, 18.75, -3.75), (6, 1, 7), "snowfur", inflate=0.25, rot=(0, 0, -12), pivot=pivot)
    pair(g, RA, LA, "elbow", (-9.25, 15.5, -1.5), (1, 2, 3), "fsilver")
    pair(g, RA, LA, "vambrace", (-9, 12, -3), (6, 3, 6), "fsilver", tags={"lames": 2})
    pair(g, RA, LA, "cuff_fur", (-9, 15, -3), (6, 1, 6), "snowfur", inflate=0.3)
    # 脚甲：キルトの腿・重なる腿の板・外の板・とがった膝当てと氷のとげ
    pair(g, RL, LL, "leg_shell", (-3.9, 4, -2), (4, 8, 4), "fsilver_dark", inflate=0.5, tags={"quilt": True})
    pair(g, RL, LL, "cuisse", (-3.4, 7.5, -3), (3, 4, 1), "fsilver", tags={"lames": 2})
    pair(g, RL, LL, "thigh_side", (-4.9, 6, -2), (1, 5, 4), "fsilver", tags={"lames": 2})
    pair(g, RL, LL, "knee", (-3.6, 4.5, -3.7), (3, 3, 2), "fsilver", tags={"edge_bright": "top"})
    pair(g, RL, LL, "knee_spike", (-2.6, 5, -4.75), (1, 2, 1), "icec")
    # 鉄靴：重なる板の靴・とがったつま先・白い毛皮の折り返し・かかとの氷の拍車
    pair(g, RB, LB, "boot_shell", (-3.9, 0, -2), (4, 4, 4), "fsilver", inflate=0.8, tags={"lames": 2})
    pair(g, RB, LB, "toe", (-3.4, -0.8, -3.9), (3, 2, 2), "fsilver")
    pair(g, RB, LB, "toe_tip", (-2.9, -0.8, -4.9), (2, 1, 1), "fsilver", tags={"no_snow": True})
    pair(g, RB, LB, "fur_cuff", (-4.4, 3.6, -2.5), (5, 2, 5), "snowfur", inflate=0.3)
    pair(g, RB, LB, "spur", (-2.4, 0.5, 2.7), (1, 1, 2), "icec")
    return g


# ================================================================ ウィザード：霜銀の長衣（雪読みの外套）

def build_wizard():
    g = new_armor("frost_silver_wizard")
    # 尖り帽：頭を包む白い毛皮の帯と雪の結晶のピン・夜の雪の色の山・先は横へ垂れて、白い毛皮の玉・左右の耳当て
    g.cube(H, "fur_band", (-5, 29, -5), (10, 3, 10), "snowfur")
    g.cube(H, "pin", (-1.5, 29.5, -5.65), (3, 2, 1), "fsilver", tags={"no_snow": True})
    g.cube(H, "pin_gem", (-0.5, 30, -6.1), (1, 1, 1), "icec")
    g.cube(H, "cone_0", (-4, 32, -4), (8, 2, 8), "nightsnow", tags={"trim": "silver"})
    g.cube(H, "cone_1", (-3, 34, -3), (6, 2, 6), "nightsnow")
    g.cube(H, "flop_0", (-2.5, 35.5, -2.5), (5, 3, 5), "nightsnow", rot=(0, 0, 18), pivot=(0, 35.5, 0))
    g.cube(H, "flop_1", (-0.5, 37.5, -2), (4, 3, 4), "nightsnow", rot=(0, 0, 48), pivot=(1, 37.5, 0))
    g.cube(H, "flop_2", (3.0, 38.0, -1.5), (3, 2, 3), "nightsnow", rot=(0, 0, 78), pivot=(3.5, 38.0, 0))
    g.cube(H, "pompom", (3.25, 34.5, -1.5), (3, 3, 3), "snowfur", inflate=0.3)
    pair(g, H, H, "ear_flap", (-5.6, 24, -2), (1, 6, 4), "snowfur")
    # 長衣：トグル留めの長い外套（前に白樺のトグル3つと赤い紐）・赤い編みのマフラー（首に巻き、片方は前に、片方は背に垂れる）
    #       ・革の帯・右の腰に霜銀の鎖で下げた「雪の書」・背に銀の糸の雪の結晶
    g.cube(B, "coat", (-4, 12, -2), (8, 12, 4), "nightsnow", inflate=0.75,
           tags={"emblem": (SNOWFLAKE_7, "silver", 3)})
    for k, y in enumerate((19, 16, 13)):
        g.cube(B, f"toggle_{k}", (-1.5, y, -3.35), (3, 1, 1), "birch")
        g.cube(B, f"loop_{k}", (-0.5, y - 0.5, -3.1), (1, 2, 1), "berry")
    g.cube(B, "scarf", (-4.5, 21, -3.5), (9, 3, 7), "berry", inflate=0.15, tags={"knit": True})
    g.cube(B, "scarf_front", (1, 12, -3.95), (2, 9, 1), "berry", tags={"knit": True, "fringe": True})
    g.cube(B, "scarf_back", (-3.5, 14, 3.2), (2, 7, 1), "berry", tags={"knit": True, "fringe": True})
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "leather_dark")
    g.cube(B, "book_chain", (-6.1, 10, -0.5), (1, 2, 1), "fsilver", inflate=-0.2, tags={"no_snow": True})
    g.cube(B, "book", (-7, 6, -2), (2, 4, 4), "leather_dark", tags={"stitch": True})
    g.cube(B, "book_pages", (-7.1, 6.5, -1.5), (1, 3, 3), "birch")
    g.cube(B, "book_flake", (-7.6, 7.5, -0.5), (1, 1, 1), "icec")
    # 袖：夜の雪の袖・白い毛皮の袖口
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "nightsnow", inflate=0.5)
    pair(g, RA, LA, "cuff_fur", (-8.5, 12, -2.5), (5, 2, 5), "snowfur", inflate=0.35)
    # 下衣：すねまでの外套のすそ（前は割れて銀）・白い毛皮のすそ
    pair(g, RL, LL, "skirt", (-3.9, 3, -2), (4, 9, 4), "nightsnow", inflate=0.75, tags={"split": "silver"})
    pair(g, RL, LL, "hem_fur", (-4.4, 1.5, -2.5), (5, 2, 5), "snowfur", inflate=0.55)
    # 布靴：厚いフェルトの長靴（前に雪の結晶）・丸いつま先・毛皮の口
    pair(g, RB, LB, "felt_boot", (-3.9, 0, -2), (4, 5, 4), "frostblue", inflate=0.55,
         tags={"base": 2.4, "emblem": (SNOWFLAKE_5, "white", 0, ("front",))})
    pair(g, RB, LB, "felt_toe", (-3.4, -0.55, -3.6), (3, 2, 1), "frostblue", tags={"base": 2.4})
    pair(g, RB, LB, "boot_fur", (-4.4, 4.5, -2.5), (5, 1, 5), "snowfur", inflate=0.35)
    return g


# ================================================================ ヒーラー：霜銀の白法衣（雪の看とり手）

def build_healer():
    g = new_armor("frost_silver_healer")
    # 白頭巾：白い頭巾（てっぺんから後ろへ尖り、長い尾が背に垂れて、先に氷の鈴）・顔のまわりを白い毛皮が大きく囲む
    #         ・額に霜銀の雪の結晶の留め具
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "snowcloth")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "snowcloth")
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "snowcloth")
    g.cube(H, "hood_point", (-2.5, 33, -1), (5, 1, 6), "snowcloth")
    g.cube(H, "tail", (-1, 21, 5), (2, 12, 1), "snowcloth", rot=(-12, 0, 0), pivot=(0, 33, 5), tags={"trim": "frostblue"})
    g.cube(H, "tail_bell", (-0.5, 19.5, 5.4), (1, 2, 1), "icec", rot=(-12, 0, 0), pivot=(0, 33, 5))
    g.cube(H, "ruff_top", (-6, 31, -6.5), (12, 2, 2), "snowfur")
    pair(g, H, H, "ruff_side", (-6.5, 22.5, -6.5), (2, 9, 2), "snowfur")
    g.cube(H, "ruff_chin", (-4.5, 22, -6.5), (9, 1, 2), "snowfur")
    g.cube(H, "brooch", (-1, 30, -6.9), (2, 2, 1), "fsilver", tags={"no_snow": True})
    g.cube(H, "brooch_gem", (-0.5, 30.5, -7.3), (1, 1, 1), "icec")
    # 白法衣：白い法衣・氷青の大きな肩掛け（ひじまで。すそに銀・背に雪の結晶）・霜銀の帯と、前に下がる3つの氷の鈴
    g.cube(B, "robe", (-4, 12, -2), (8, 12, 4), "snowcloth", inflate=0.75, tags={"split": "frostblue"})
    g.cube(B, "poncho", (-6.5, 15, -4), (13, 9, 8), "frostblue",
           tags={"base": 2.6, "trim": "silver", "emblem": (SNOWFLAKE_7, "white", 1, ("back",))})
    g.cube(B, "poncho_collar", (-4.5, 23.5, -3.5), (9, 1, 7), "snowfur", inflate=0.2)
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "fsilver", tags={"no_snow": True})
    for k, (x, y, h) in enumerate(((-2.5, 9.0, 2), (-0.5, 8.0, 3), (1.5, 9.0, 2))):
        g.cube(B, f"chime_{k}", (x, y, -4.1), (1, h, 1), "icec")
        g.cube(B, f"chime_cap_{k}", (x - 0.25, y + h, -4.35), (1, 1, 1), "fsilver", inflate=0.15, tags={"no_snow": True})
    # 袖：白い袖・氷青の編みのミトン（毛皮の口）
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "snowcloth", inflate=0.5)
    pair(g, RA, LA, "mitten", (-8.5, 11, -2.5), (5, 3, 5), "frostblue", tags={"knit": True})
    pair(g, RA, LA, "mitten_fur", (-8.75, 14, -2.75), (5, 1, 5), "snowfur", inflate=0.35)
    # 白下衣：2段に広がるすそ（それぞれのすそに氷青の帯）
    pair(g, RL, LL, "tier_1", (-4.4, 6, -2.5), (5, 6, 5), "snowcloth", inflate=0.45, tags={"trim": "frostblue"})
    pair(g, RL, LL, "tier_2", (-4.9, 2, -3), (6, 4, 6), "snowcloth", inflate=0.35,
         tags={"trim": "frostblue", "stripes": ("frostblue", 4)})
    # 白靴：白い毛皮の長靴・前に下がる毛皮の玉2つ・霜銀の留め紐
    pair(g, RB, LB, "fur_boot", (-3.9, 0, -2), (4, 4, 4), "snowfur", inflate=0.6)
    pair(g, RB, LB, "boot_tie", (-4.4, 2.5, -2.5), (5, 1, 5), "fsilver", inflate=0.15, tags={"no_snow": True})
    pair(g, RB, LB, "pom_0", (-3.4, 1.5, -3.6), (1, 1, 1), "snowfur", inflate=0.2)
    pair(g, RB, LB, "pom_1", (-1.9, 1.0, -3.6), (1, 1, 1), "snowfur", inflate=0.2)
    return g


# ================================================================ アーチャー：霜銀の狩り装束（雪原の狩人）

def build_archer():
    g = new_armor("frost_silver_archer")
    # 羽根帽：丸い灰の毛皮帽（前の折り返し）・左右に下りた耳当て・額に雪眼鏡（氷の色の2つのレンズと霜銀の枠）・右に白い羽根2本
    g.cube(H, "hat", (-4.5, 29, -4.5), (9, 4, 9), "greyfur", inflate=0.15)
    g.cube(H, "hat_top", (-3.5, 33, -3.5), (7, 1, 7), "greyfur")
    g.cube(H, "hat_fold", (-5, 28, -5.5), (10, 2, 2), "greyfur")
    pair(g, H, H, "ear_flap", (-5.6, 23.5, -2.5), (1, 6, 5), "greyfur")
    g.cube(H, "goggle_band", (-5.1, 30.25, -4.6), (10, 1, 9), "leather_dark", inflate=0.05)
    pair(g, H, H, "goggle", (-4, 30, -6), (3, 2, 1), "icec")
    pair(g, H, H, "goggle_rim", (-4.25, 29.75, -5.5), (3, 2, 1), "fsilver", inflate=0.25, tags={"no_snow": True})
    g.cube(H, "feather_0", (-6.1, 31, -2), (1, 6, 2), "feather", rot=(-15, 0, 10), pivot=(-5.6, 31, -1),
           tags={"along": "j", "tip": "start"})
    g.cube(H, "feather_1", (-6.0, 31, 0), (1, 5, 2), "feather", rot=(-30, 0, 18), pivot=(-5.5, 31, 1),
           tags={"along": "j", "tip": "start"})
    # 狩り胴着：白い雪の迷彩のポンチョ（腰まで）・灰の毛皮の襟・背に下ろした頭巾・右の腰の矢筒（白い矢羽根）
    g.cube(B, "jerkin", (-4, 12, -2), (8, 12, 4), "frostblue", inflate=0.6, tags={"quilt": True, "base": 2.6})
    g.cube(B, "poncho", (-6, 13, -4), (12, 10, 8), "snowcloth", tags={"camo": "frostblue", "trim": "frostblue"})
    g.cube(B, "fur_collar", (-4.5, 22.5, -3.5), (9, 2, 7), "greyfur", inflate=0.2)
    g.cube(B, "hood_down", (-3.5, 20.5, 4), (7, 3, 2), "snowcloth", tags={"trim_top": "frostblue"})
    g.cube(B, "quiver", (-6.5, 3.5, 1.5), (2, 9, 3), "leather", tags={"stitch": True})
    g.cube(B, "quiver_band", (-6.75, 10, 1.25), (2, 1, 3), "fsilver", inflate=0.25, tags={"no_snow": True})
    g.cube(B, "arrows", (-6.25, 12.5, 2.0), (1, 1, 2), "birch")
    g.cube(B, "fletching", (-6.5, 13.5, 1.75), (2, 2, 2), "feather", tags={"along": "j", "tip": "end"})
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 1, 7), "leather_dark")
    # 腕：編みの袖・灰の毛皮の袖口。弓を持つ左の腕に、霜銀の帯の革の腕当て
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 11, 4), "frostblue", inflate=0.4, tags={"knit": True, "base": 2.8})
    pair(g, RA, LA, "cuff_fur", (-8.5, 12, -2.5), (5, 2, 5), "greyfur", inflate=0.3)
    g.cube(LA, "bracer_l", (3.5, 14, -2.5), (5, 4, 5), "leather_dark", tags={"stitch": True})
    g.cube(LA, "bracer_band", (3.25, 15.5, -2.75), (5, 1, 5), "fsilver", inflate=0.3, tags={"no_snow": True})
    # 革脚絆：綿入れの脚絆と、十字に巻いた革の紐
    pair(g, RL, LL, "trousers", (-3.9, 3, -2), (4, 9, 4), "frostblue", inflate=0.5, tags={"quilt": True, "base": 2.2})
    for k, y in enumerate((9.0, 5.0)):
        pair(g, RL, LL, f"strap_{k}", (-3.9, y, -2), (4, 1, 4), "leather_dark", inflate=0.55)
    # 長靴：革の長靴（灰の毛皮の口）と、白樺の枠に革を張ったかんじき
    pair(g, RB, LB, "boot", (-3.9, 0, -2), (4, 4, 4), "leather", inflate=0.55)
    pair(g, RB, LB, "boot_fur", (-4.4, 3.5, -2.5), (5, 2, 5), "greyfur", inflate=0.3)
    pair(g, RB, LB, "shoe_rail_out", (-5.15, -1, -5.5), (1, 1, 10), "birch")
    pair(g, RB, LB, "shoe_rail_in", (-0.85, -1, -5.5), (1, 1, 10), "birch")
    pair(g, RB, LB, "shoe_front", (-4.15, -1, -6.5), (3, 1, 1), "birch")
    pair(g, RB, LB, "shoe_back", (-4.15, -1, 4.5), (3, 1, 1), "birch")
    pair(g, RB, LB, "shoe_web", (-4.15, -1.1, -5.5), (3, 1, 10), "leather", tags={"lattice": 2})
    return g


# ================================================================ ローグ：霜銀の黒装束（吹雪の影）

def build_rogue():
    g = new_armor("frost_silver_rogue")
    # 覆面：炭の色の頭巾（顔のまわりに灰の毛皮）・口と鼻をおおう霜銀の面頬（横の息の筋）・氷の色の目の覆い
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "charcoal")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "charcoal", tags={"lining": "frostblue"})
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "charcoal")
    g.cube(H, "hood_peak", (-1.5, 33, -4), (3, 1, 8), "charcoal")
    g.cube(H, "ruff_top", (-5, 30.5, -5.6), (10, 2, 1), "greyfur")
    pair(g, H, H, "ruff_side", (-5.5, 23.5, -5.6), (1, 7, 1), "greyfur")
    g.cube(H, "faceguard", (-4, 23.5, -5.3), (8, 3, 1), "fsilver", inflate=0.1, tags={"lames": 1, "no_snow": True})
    pair(g, H, H, "eye", (-3.5, 27.5, -5.2), (3, 1, 1), "icec")
    # 黒装束：高い襟の外套（ななめの合わせと霜銀のボタン）・胸に2本の革帯と霜銀の留め金・ななめの帯に投げ短剣4本
    #         ・左の肩だけの短い肩掛け（毛皮のふち）
    g.cube(B, "coat", (-4, 12, -2), (8, 12, 4), "charcoal", inflate=0.75, tags={"diag": ("frostblue", 0.45), "trim": "frostblue"})
    g.cube(B, "collar", (-4.5, 22, -3.5), (9, 4, 7), "charcoal", inflate=0.1, tags={"trim_top": "greyfur", "lining": "frostblue"})
    for k, y in enumerate((18.5, 15.0)):
        g.cube(B, f"strap_{k}", (-5, y, -3.5), (10, 1, 7), "leather_dark", inflate=0.1)
        g.cube(B, f"strap_buckle_{k}", (-3.5, y - 0.25, -3.85), (1, 1, 1), "fsilver", inflate=0.2, tags={"no_snow": True})
    g.cube(B, "baldric", (-0.5, 11.5, -3.95), (1, 13, 1), "leather_dark", rot=(0, 0, -35), pivot=(0, 18, -3.45))
    for k, y in enumerate((14.0, 16.5, 19.0, 21.5)):
        g.cube(B, f"knife_{k}", (-0.5, y, -4.35), (1, 2, 1), "fsilver", rot=(0, 0, -35), pivot=(0, 18, -3.45),
               tags={"no_snow": True})
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "leather_dark", inflate=0.2)
    g.cube(LA, "half_cape", (3.25, 16, -3.25), (6, 8, 6), "charcoal", inflate=0.1,
           tags={"trim_top": "greyfur", "trim": "frostblue"})
    # 腕：炭の袖・右の前腕に霜銀の腕当て（隠し刃）
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "charcoal", inflate=0.4)
    g.cube(RA, "bracer_r", (-8.5, 12, -2.5), (5, 4, 5), "fsilver", tags={"lames": 2, "no_snow": True})
    g.cube(RA, "hidden_blade", (-6.5, 12.5, -5.5), (1, 1, 3), "fsilver", tags={"edge": "both", "no_snow": True})
    # 黒脚絆：細い炭の脚絆・霜銀の膝当て・右の腿の短剣の鞘
    pair(g, RL, LL, "leggings", (-3.9, 3, -2), (4, 9, 4), "charcoal", inflate=0.45)
    pair(g, RL, LL, "knee_plate", (-3.5, 5, -3.0), (3, 3, 1), "fsilver", tags={"edge_bright": "top", "no_snow": True})
    g.cube(RL, "sheath", (-5.1, 6, -1), (1, 5, 2), "leather_dark", tags={"stitch": True})
    g.cube(RL, "sheath_hilt", (-5.1, 11, -0.5), (1, 2, 1), "fsilver", tags={"no_snow": True})
    # 足袋：すねまでの割れたつま先の長い足袋（中に灰の毛皮）・十字に巻いた革紐
    pair(g, RB, LB, "tabi", (-3.9, 0, -2), (4, 5, 4), "charcoal", inflate=0.55, tags={"wrap": True})
    pair(g, RB, LB, "tabi_toe", (-3.4, -0.55, -3.6), (3, 1, 1), "charcoal", tags={"toe_split": True})
    pair(g, RB, LB, "tabi_fur", (-4.4, 4.5, -2.5), (5, 1, 5), "greyfur", inflate=0.35)
    return g


# ================================================================ モンク：霜銀の道着（雪山の修行者）

def build_monk():
    g = new_armor("frost_silver_monk")
    # 鉢巻：赤い編みの太い鉢巻・額の霜銀の板・頭の上を通る霜銀の輪と、白い毛皮の丸い耳当て・後ろの短い結び
    g.cube(H, "band", (-4.5, 28, -4.5), (9, 2, 9), "berry", inflate=0.15, tags={"knit": True})
    g.cube(H, "plate", (-1.5, 27.75, -5.05), (3, 2, 1), "fsilver", tags={"no_snow": True})
    g.cube(H, "arc_top", (-4.5, 32.3, -0.5), (9, 1, 1), "fsilver")
    pair(g, H, H, "arc_side", (-5.3, 28.3, -0.5), (1, 4, 1), "fsilver", tags={"no_snow": True})
    pair(g, H, H, "muff", (-6.0, 25.5, -1.5), (2, 3, 3), "snowfur", inflate=0.2)
    g.cube(H, "knot", (-1, 27.5, 4.6), (2, 2, 1), "berry", tags={"knit": True})
    g.cube(H, "tail", (-0.5, 25.0, 4.8), (1, 3, 1), "berry", rot=(15, 0, 0), pivot=(0, 28, 5.3), tags={"knit": True})
    # 道着：綿入れの道着（ひし形の縫い目）・松の緑の襟・帯・肩と背をおおう蓑（わらの雨具。毛先がそろわない）
    g.cube(B, "gi", (-4, 12, -2), (8, 12, 4), "padded", inflate=0.75, tags={"quilt": True})
    g.cube(B, "collar", (-4.5, 21.5, -3.5), (9, 2, 7), "pine", inflate=0.1)
    g.cube(B, "obi", (-5, 11, -3.5), (10, 2, 7), "pine", tags={"base": 2.6})
    g.cube(B, "obi_knot", (-1.5, 10.5, -4.25), (3, 2, 1), "pine", tags={"base": 3.0})
    pair(g, B, B, "mino_shoulder", (-6.5, 18, -3.75), (3, 6, 8), "strawcape")
    g.cube(B, "mino_back", (-5.5, 10, 3.3), (11, 14, 1), "strawcape")
    g.cube(B, "mino_neck", (-3.5, 23, 2.25), (7, 1, 2), "strawcape")
    # 腕：手首までの綿入れの袖・白い巻き布
    pair(g, RA, LA, "sleeve", (-8, 14, -2), (4, 10, 4), "padded", inflate=0.6, tags={"quilt": True})
    pair(g, RA, LA, "wraps", (-8, 12, -2), (4, 3, 4), "white", inflate=0.4, tags={"wrap": True, "base": 3.4})
    # 袴：藍に近い氷青の袴（ひだ）と、すねの生成りの脚絆（赤い紐で十字に結ぶ）
    pair(g, RL, LL, "hakama", (-3.9, 6, -2), (4, 6, 4), "frostblue", inflate=0.85, tags={"pleats": True, "base": 1.8})
    pair(g, RL, LL, "gaiter", (-3.9, 3, -2), (4, 3, 4), "padded", inflate=0.6, tags={"quilt": True})
    pair(g, RL, LL, "gaiter_cord", (-3.9, 4.5, -2), (4, 1, 4), "berry", inflate=0.7, tags={"wrap": True})
    # 草鞋：わらの底・赤い鼻緒・厚い編みの靴下・底の霜銀の滑り止め（4つのとげ）
    pair(g, RB, LB, "sock", (-3.9, 0, -2), (4, 3, 4), "snowcloth", inflate=0.5, tags={"knit": True, "base": 4.0})
    pair(g, RB, LB, "sole", (-3.9, -0.6, -2.6), (4, 1, 5), "straw")
    pair(g, RB, LB, "thong", (-2.4, 0.4, -2.9), (1, 1, 1), "berry")
    for k, (x, z) in enumerate(((-3.4, -2.2), (-1.4, -2.2), (-3.4, 1.4), (-1.4, 1.4))):
        pair(g, RB, LB, f"cleat_{k}", (x, -1.3, z), (1, 1, 1), "fsilver", inflate=-0.15, tags={"no_snow": True})
    return g


SETS = {
    "knight": {"build": build_knight, "title": "霜銀の重鎧（ナイト）",
               "pieces": (("兜", "head", "frost_silver_knight_helm"), ("胴鎧", "chest", "frost_silver_knight_cuirass"),
                          ("脚甲", "legs", "frost_silver_knight_greaves"), ("鉄靴", "feet", "frost_silver_knight_sabatons")),
               "note": "丸い鉢に、雪の結晶のとさか（氷の結晶）。\nT字の覗き穴・白い毛皮の襟と肩。\n横に重なる板の胸と、丸い肩当て。\n雪の結晶の前垂れ・氷のとげの膝。"},
    "wizard": {"build": build_wizard, "title": "霜銀の長衣（ウィザード）",
               "pieces": (("尖り帽", "head", "frost_silver_wizard_hat"), ("長衣", "chest", "frost_silver_wizard_robe"),
                          ("下衣", "legs", "frost_silver_wizard_legwear"), ("布靴", "feet", "frost_silver_wizard_shoes")),
               "note": "先が横へ垂れて毛皮の玉の付いた尖り帽。\nトグル留めの夜の雪の色の外套と、\n赤い編みのマフラー・腰の雪の書。\n毛皮のすそと、フェルトの長靴。"},
    "healer": {"build": build_healer, "title": "霜銀の白法衣（ヒーラー）",
               "pieces": (("白頭巾", "head", "frost_silver_healer_hood"), ("白法衣", "chest", "frost_silver_healer_robe"),
                          ("白下衣", "legs", "frost_silver_healer_legwear"), ("白靴", "feet", "frost_silver_healer_shoes")),
               "note": "顔のまわりを白い毛皮が囲む頭巾\n（後ろへ長い尾と氷の鈴）。\n氷青の大きな肩掛け（背に雪の結晶）・\n帯の氷の鈴・編みのミトン・段のすそ。"},
    "archer": {"build": build_archer, "title": "霜銀の狩り装束（アーチャー）",
               "pieces": (("羽根帽", "head", "frost_silver_archer_cap"), ("狩り胴着", "chest", "frost_silver_archer_jerkin"),
                          ("革脚絆", "legs", "frost_silver_archer_leggings"), ("長靴", "feet", "frost_silver_archer_boots")),
               "note": "耳当ての毛皮帽に雪眼鏡と白い羽根。\n白い雪の迷彩のポンチョ・腰の矢筒。\n綿入れの脚絆と十字の革紐。\n白樺の枠に革を張ったかんじき。"},
    "rogue": {"build": build_rogue, "title": "霜銀の黒装束（ローグ）",
              "pieces": (("覆面", "head", "frost_silver_rogue_mask"), ("黒装束", "chest", "frost_silver_rogue_garb"),
                         ("黒脚絆", "legs", "frost_silver_rogue_leggings"), ("足袋", "feet", "frost_silver_rogue_tabi")),
              "note": "灰の毛皮のふちの頭巾と、霜銀の面頬・\n氷の色の目の覆い。高い襟の外套\n（ななめの合わせ）と投げ短剣の帯。\n左肩だけの肩掛け・腿の短剣・長い足袋。"},
    "monk": {"build": build_monk, "title": "霜銀の道着（モンク）",
             "pieces": (("鉢巻", "head", "frost_silver_monk_headband"), ("道着", "chest", "frost_silver_monk_gi"),
                        ("袴", "legs", "frost_silver_monk_hakama"), ("草鞋", "feet", "frost_silver_monk_sandals")),
             "note": "赤い編みの鉢巻と、白い毛皮の耳当て。\n綿入れの道着の上に、わらの蓑。\n袴と、赤い紐で結んだ脚絆。\n厚い靴下と、霜銀の滑り止めの草鞋。"},
}


def paint(g):
    g.pack()
    return g.paint(FrostArmorPainter(g), frames=1)


def preview(g, tex, path, scale=10):
    from render import render_many
    from PIL import Image
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    ims = [render_many([(man, mtex), (g, tex)], yaw=y, pitch=p, scale=scale, size=(260, 440), center=(0, 19, 0))
           for y, p, _ in VIEWS]
    s = Image.new("RGBA", (260 * 4, 440), (40, 44, 52, 255))
    for k, im in enumerate(ims):
        s.alpha_composite(im, (260 * k, 0))
    s.save(path)


def make_sheet(repo, key, g, tex, path):
    import frost_icons
    from setsheet import make_set_sheet
    cfg = SETS[key]
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    full = [(man, mtex), (g, tex)]
    icons = [(label, frost_icons.icon(item)) for label, _, item in cfg["pieces"]]
    piece_views = [(label, -145, 14, [(man, mtex), (subset(g, PIECE_BONES[slot]), tex)])
                   for label, slot, _ in cfg["pieces"]]
    job = cfg["title"].split("（")[1].rstrip("）")
    items = [
        {"label": "フルセット", "rarity": "コモン・Lv24・" + job + "専用", "note": cfg["note"], "pairs": full,
         "views": [(lab, y, p) for y, p, lab in VIEWS], "icons": icons},
        {"label": "部位ごと", "rarity": "着た場所だけ表示", "note": "", "pairs": full, "views": piece_views,
         "icons": icons, "icon_cell": 40},
    ]
    make_set_sheet(path, cfg["title"], "霜銀シリーズ（第2章の職業ごとの装備）／ 11月26日に形から作り直した【補完】／ 128×128の絵",
                   items, row_h=440)


def save_icons(repo, keys):
    """防具の持ち物の絵（16×16。このあと enhance_icons.py で32×32にする）と、アイテムのモデル。"""
    import json
    import frost_icons
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for key in keys:
        for _, _, name in SETS[key]["pieces"]:
            frost_icons.icon(name).save(os.path.join(base, "textures/item", name + ".png"))
            with open(os.path.join(base, "models/item", name + ".json"), "w", encoding="utf-8") as fp:
                json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, fp,
                          ensure_ascii=False, indent=2)
                fp.write("\n")


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_frost"
    os.makedirs(out, exist_ok=True)
    only = sys.argv[3].split(",") if len(sys.argv) > 3 else list(SETS)
    preview_only = "--preview" in sys.argv
    for key in only:
        g = SETS[key]["build"]()
        frames = paint(g)
        used = max(c.uv[1] + c.block()[1] for c in g.elements)
        print(g.name, "部品", len(g.elements), "絵の使った高さ", used, "/", g.tex_h)
        preview(g, frames[0], os.path.join(out, "frost_%s_preview.png" % key))
        if preview_only:
            continue
        g.save(repo, "armor", frames)
        make_sheet(repo, key, g, frames[0], os.path.join(repo, "docs/gear", "frost_silver_%s_sheet.png" % key))
    if not preview_only:
        save_icons(repo, only)


if __name__ == "__main__":
    main()
