"""コネリアの塔の入口（PHASE 11。建築の文書「PHASE 11：塔の入口」の設計どおり）。

塔の門（古代の転移門。門番バルド。Phase 0-A）は、北門を出た先の塔前広場の奥に、塔の足もととして建つ（都市のルール⑧）。
正面は南（北門の方）。北の大通り（塔の道）からまっすぐ、北門 → 塔前広場 → 塔の入口が一直線に並ぶ。
  - 門の棟（正面）：左右の塔（9×9。胴は +20、上の段は一回り細い7×7、緑青の銅の四角すい 2：1 の屋根の先が +36＝正面の高さ）と、
    その間の門の壁（胸壁の凸まで +17。塔は門の壁の約2倍）。真ん中に大きな丸いアーチ（幅9、高さ11）と、上げた落とし格子。
    アーチの上に、模様入りの石で彫った古代の紋章（結界の紋：円・四角・十字。真ん中に結晶）の板が +28 まで立ち上がる。
    アーチの左右は、受付（西。通路に向いた窓口）と衛兵の間（東）。左右の塔のらせん階段で、門の上の歩廊（+15）へ出る。
  - 広間（天井16）：古代の白い柱（3×3）を、東西16・南北8の柱割りで4本。奥の塔の壁に、転移門（白い石の輪と結晶の灯り）を
    はめ込み、3段上がった台の上に転移の円。輪の向こうに、封じられた塔の大扉が見える。台の東に門番バルドの書見台、
    その後ろの東の壁に「塔の灯り」（章の色のろうそく20本。ひらいた階だけ灯る【提案】）。両側と南の高窓から光が入る。
  - 両翼（1階建て）：西は控えの間（パーティーの待ち合わせ）と補給の隅、東は門番の部屋と踏破の記録の間。
  - 屋根（都市のルール⑥。緑青の銅だけ）：広間は寄棟 1：1（軒 +21、棟 +30）、両翼は広間の壁に掛ける片流れ 1：1（教会の側廊と同じ）、
    左右の塔は四角すい 2：1。
  - 塔の基部の壁：建物の奥いっぱいに、塔の丸い基部の南の弧が +48 まで立ち上がる（段になった3つの帯、8ごとの付け柱、
    縦一列の窓、上の冠と結晶の灯り）。上はあとで足す（いちばん上の半ブロックの段の上に、塔の胴の構造物を置く）。
  - 塔前広場（47×32）：白い石畳。塔の入口に向かって、ゆるい段で3段上がる（+0 → +3）。真ん中は塔の道の続き（8ごとに
    模様入りの石英）。両脇に、塔へ向かって少しずつ高くなる古代の石柱の列（5・7・9・11）と、結晶灯。西に記念碑の場所、東に掲示板。
  - 1週目の共同の目標（古代石5,000個）を達したら、記念碑を重ねて置く（tower_plaza_monument.nbt。都市のルール⑩）。
  - 中に入れる所の樽は、鍵をかける（Lock）。チェスト・かまど・醸造台は置かない。

座標：世界の座標（大結晶が x 0・z 0。x が東、z が南）。高さ y は道のブロックが 0（足もとが 1）。
建物の床のブロックは y 3（足もと 4）。広間の天井のブロックは y 20。
構造物（どちらも、この中の設計を合わせた形から切り出す）：
  - tower_entrance.nbt（45×48×30）：(0, 0, 0) は世界の (x −22, y 1, z −177)。X = x + 22、Y = y − 1、Z = z + 177。
    足もとの地面（y 0）は書かない（ゲームの地面の上に置く）。
  - tower_plaza.nbt（47×18×32）：(0, 0, 0) は世界の (x −23, y −1, z −147)。X = x + 23、Y = y + 1、Z = z + 147。
  - tower_plaza_monument.nbt（塔前広場と同じ大きさで、同じ所に重ねる。記念碑のマスだけを書く）

使い方: python3 tools/mapgen/build_tower.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
  - --ci：自動の確認の写真の座標（CiScreenshots の MAP_VIEWS の行）を出す
ゲームの中では /place template amemaru:map/conelia/tower_entrance（足もとの地面の1つ上）・tower_plaza・tower_plaza_monument で置ける。
"""
import copy
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from nbtlib.tag import Compound, Int, List, String

from mapgen.arch import Palette, Pyramid
from mapgen.fixtures import crystal_lamp, hang_lantern, wall_lantern
from mapgen.world import (AIR, Entity, World, banner_nbt, block, door, fence, lantern, lectern_nbt, log, pane, sign_nbt,
                          slab, stairs, trapdoor, wall)

CX0, CY0, CZ0 = -23, -1, -177    # 合わせた形（確かめと見本の絵）の (0, 0, 0)：x −23..23、y −1..48、z −177..−116
CW, CH, CD = 47, 50, 62
EX0, EY0, EZ0 = -22, 1, -177     # 塔の入口の構造物
EW, EH, ED = 45, 48, 30
PX0, PY0, PZ0 = -23, -1, -147    # 塔前広場の構造物
PLW, PLH, PLD = 47, 18, 32
LOCK = "amemaru:tower_lock"      # 樽の鍵（この名前のアイテムを持たないと開かない）

FL, FT = 3, 4                    # 床のブロック・足もと
CEIL = 20                        # 広間の天井のブロック（広間の中は y 4..19 の16）
WALK = 15                        # 門の上の歩廊の床（足もと 16）
LOW_CEIL = 9                     # 受付・衛兵の間・両翼の天井
TW_BODY = 20                     # 左右の塔の胴の上
TW_TOP = 26                      # 上の段の壁の上（屋根は 2：1 の四角すいで +27..+36）
APEX = 36                        # 左右の塔の屋根の先（正面の高さ）
BASE_TOP = 48                    # 塔の基部の壁の上（半ブロックの段）
Z_TF, Z_TB = -148, -156          # 左右の塔の正面・裏
Z_GF, Z_GB = -150, -157          # 門の壁の正面・裏（裏は広間の南の壁）
RING_Z, RING_Y = -171, 11        # 転移門の輪の面（塔の壁から1つ出る）と、真ん中の高さ
BASE_C = (0, -215)               # 塔の丸い基部の真ん中
BANDS = ((20, 43.5), (34, 41.5), (BASE_TOP, 39.5))   # 基部の3つの帯（その高さまでの半径。上の帯ほど2ずつ細い）
PILASTERS = (-20, -12, -4, 4, 12, 20)                # 基部の付け柱（8ごと）
COLS = ((-8, -161), (8, -161), (-8, -169), (8, -169))  # 広間の柱（3×3）の真ん中
RISERS = {-126: 1, -134: 2, -141: 3}                 # 塔前広場の段（その列の高さ）
LAMPS = ((6, -118), (-6, -118), (-6, -124), (6, -130), (-6, -136), (6, -142),       # 広場の結晶灯：塔の道の帯の両脇（入口の対のあと、12ごと互い違い）
         (-15, -124), (15, -124), (-15, -136), (15, -136), (-21, -146), (21, -146))   # と、広場の両脇
PILLARS = ((-121, 5), (-129, 7), (-137, 9), (-144, 11))  # 広場の石柱の列の z と高さ（両脇 x ±19）

C = World(CW, CH, CD)            # 合わせた形
M = World(CW, CH, CD)            # 記念碑（重ねる形）
M_OWNED = set()                  # 記念碑の書くマス（合わせた形の座標）
copper = Palette(seed=1111, roof="waxed_oxidized_cut_copper", roof_block="waxed_oxidized_copper", trim="quartz")
rng = copper.rng
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


# ---------------------------------------------------------------- 置く道具（世界の x・z と、道からの y）
def put(x, z, y, state, nbt=None, w=None):
    (w or C).put(x - CX0, y - CY0, z - CZ0, state, nbt)


def get(x, z, y, w=None):
    return (w or C).get(x - CX0, y - CY0, z - CZ0)


def name_at(x, z, y):
    st = get(x, z, y)
    return st[0] if st else "air"


def fill(x0, x1, z0, z1, y0, y1, state, w=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(x, z, y, state() if callable(state) else state, w=w)


def frame(x, z, y, facing, item, rotation=0, name=None, w=None):
    (w or C).item_frame(x - CX0, y - CY0, z - CZ0, facing, item, rotation=rotation, name=name)


def stand(x, z, y, facing, w=None, **kw):
    (w or C).armor_stand(x - CX0, y - CY0, z - CZ0, facing=facing, **kw)


def wall_lamp(x, z, y, out, reach=2, chain=1, style="iron"):
    """壁 (x, z) から out の向きへ腕木を出したランタン。腕木の高さ y、ランタンは y − 1 − chain"""
    wall_lantern(C, x - CX0, y - CY0, z - CZ0, out, style=style, reach=reach, chain=chain)


def hang_down(x, z, y_lamp):
    """上のいちばん近いブロック（天井）から、鎖で y_lamp まで吊るランタン"""
    y = y_lamp + 1
    while get(x, z, y) is None and y < CH + CY0 - 1:
        y += 1
    for yy in range(y_lamp + 1, y):
        put(x, z, yy, block("chain", axis="y", waterlogged=False))
    put(x, z, y_lamp, lantern(True))


def crystal(x, z, y_base, lit=True, w=None):
    """古代の結晶灯（高さ6。都市のルール⑦）。y_base は台の高さ（地面の上）"""
    crystal_lamp(w or C, x - CX0, y_base - CY0, z - CZ0, lit=lit)


def wall_sign(x, z, y, facing, lines, wood="spruce", w=None):
    put(x, z, y, block(wood + "_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines), w=w)


def banner(x, z, y, facing, color, patterns):
    put(x, z, y, block(color + "_wall_banner", facing=facing), banner_nbt(patterns))


def one_door(x, z, y, facing, wood="dark_oak", hinge="left"):
    put(x, z, y, door(wood, facing, False, hinge))
    put(x, z, y + 1, door(wood, facing, True, hinge))


def barrel(x, z, y, facing="up"):
    put(x, z, y, block("barrel", facing=facing, open=False),
        Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))


def candle(x, z, y, n=1, color="", lit=True):
    put(x, z, y, block((color + "_" if color else "") + "candle", candles=n, lit=lit, waterlogged=False))


def chair(x, z, y, facing, wood="spruce"):
    """椅子（階段）。facing は背もたれの側（座る人は反対の向きを見る）"""
    put(x, z, y, stairs(wood, facing))


# ---------------------------------------------------------------- 材料（都市のルール③⑤⑥：古代の層は白い石、屋根は緑青の銅）
QB = block("quartz_bricks")
CQ = block("chiseled_quartz_block")
SQ = block("smooth_quartz")
CUT = block("waxed_oxidized_cut_copper")     # 青緑（古代の紋の色）
SL = block("sea_lantern")                     # 結晶の灯り
PD = block("polished_diorite")
STONE = block("stone_bricks")
GLASS = "glass_pane"


def QP(axis="y"):
    return block("quartz_pillar", axis=axis)


def white():
    """古代の層の白い石：磨いた閃緑岩・閃緑岩・方解石"""
    r = rng.random()
    if r < 0.66:
        return PD
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def white_paving():
    """古代の白い石畳（灰色の石のつぎはぎ1割）"""
    r = rng.random()
    if r < 0.55:
        return PD
    if r < 0.75:
        return block("diorite")
    if r < 0.88:
        return block("calcite")
    return STONE if r < 0.95 else block("andesite")


def road_paving():
    """塔の道の続き（真ん中の帯）：磨いた閃緑岩を主に、閃緑岩と方解石"""
    r = rng.random()
    if r < 0.7:
        return PD
    return block("diorite") if r < 0.88 else block("calcite")


def floor_tile():
    """建物の中の床：磨いた閃緑岩と方解石"""
    return PD if rng.random() < 0.8 else block("calcite")


# ---------------------------------------------------------------- 形の決まり
def terrace(z):
    """塔前広場の段の高さ（道のブロックの y）。z −116..−125 が 0、−126..−133 が 1、−134..−140 が 2、−141.. が 3"""
    if z >= -125:
        return 0
    if z >= -133:
        return 1
    if z >= -140:
        return 2
    return 3


def band_r(y):
    for top, r in BANDS:
        if y <= top:
            return r
    return BANDS[-1][1]


def base_face(x, y):
    """塔の基部の壁の、その高さの南の面（壁のいちばん南のマスの z）。箱の奥（z −177）より奥なら −177（いちばん奥の列は壁）"""
    r = band_r(y)
    d = r * r - x * x
    if d < 0:
        return -177
    return max(math.floor(BASE_C[1] + math.sqrt(d)), -177)


def in_base(x, z, y):
    return -22 <= x <= 22 and z <= base_face(x, y) and 1 <= y <= BASE_TOP


def ring_d(x, y):
    return math.hypot(x, y - RING_Y)


def ring_open(x, y):
    """転移門の輪の内（通れる所）"""
    return ring_d(x, y) < 4.6 and y >= FL + 4


def ring_cell(x, y):
    return 4.6 <= ring_d(x, y) <= 6.6


def arch_open(x, y):
    """門のアーチ（幅9）の内。y 4..12 は |x|≤4、13 は ≤3、14 は ≤2"""
    if y < FT or y > 14:
        return False
    if y <= 12:
        return abs(x) <= 4
    return abs(x) <= (3 if y == 13 else 2)


# ---------------------------------------------------------------- 地面（塔前広場の段と、建物の下）
def paving(x, z):
    if abs(x) == 23:
        return QB                                       # 広場の縁の帯
    if abs(x) <= 5:
        if abs(x) == 5:
            return QB                                   # 塔の道の帯の縁
        if x == 0 and z % 8 == 0:
            return CQ                                   # 8ごとの模様入りの石英（塔の道と同じ）
        return road_paving()
    return white_paving()


def ground():
    for x in range(-23, 24):
        for z in range(-147, -115):
            t = terrace(z)
            put(x, z, -1, block("dirt"))
            for y in range(0, t):
                put(x, z, y, white() if abs(x) == 23 else block("dirt"))
            if z in RISERS:
                put(x, z, t, stairs("quartz" if abs(x) <= 5 else "polished_diorite", "north"))
            else:
                put(x, z, t, paving(x, z))
    # 門の前（左右の塔の間、z −148..−149）：いちばん上の段の続き
    for x in range(-13, 14):
        for z in (-148, -149):
            for y in (1, 2):
                put(x, z, y, block("dirt"))
            put(x, z, FL, paving(x, z))


# ---------------------------------------------------------------- 塔の基部の壁（奥いっぱい、+48 まで）
def base_stone(y):
    """基部の帯ごとの石（門の棟の、まだらの白い石と分ける）：下の帯は石英のレンガ、真ん中の帯は方解石、上の帯は石英のレンガ"""
    if y <= BANDS[0][0]:
        return QB if rng.random() < 0.9 else PD
    if y <= BANDS[1][0]:
        return block("calcite") if rng.random() < 0.9 else block("diorite")
    return QB if rng.random() < 0.9 else block("calcite")


def base_wall():
    for x in range(-22, 23):
        for y in range(1, BASE_TOP + 1):
            zf = base_face(x, y)
            for z in range(-177, zf + 1):
                if y == BASE_TOP:
                    put(x, z, y, slab("polished_diorite", "bottom"))
                else:
                    put(x, z, y, base_stone(y))
    # 帯の段（下の帯の上の出っ張り）：水切りの階段
    for y0 in (BANDS[0][0] + 1, BANDS[1][0] + 1):
        for x in range(-22, 23):
            lo, hi = base_face(x, y0) + 1, base_face(x, y0 - 1)
            if x in PILASTERS:
                hi += 1
            for z in range(lo, hi + 1):
                put(x, z, y0, stairs("quartz", "north"))
    # 付け柱（8ごと。下から上へ通す）
    for x in PILASTERS:
        for y in range(1, BASE_TOP):
            if abs(x) <= 7 and y <= CEIL:
                continue                                 # 広間の中の真ん中は、転移門の輪
            z = base_face(x, y) + 1
            if y in (BANDS[0][0] + 1, BANDS[1][0] + 1):
                continue                                 # 水切りの段
            st = QB if y <= 2 else CQ if y >= 45 else QP("y")
            put(x, z, y, st)
    # 裏（北の面。あとで塔の基部の続きがつながる所）：帯の境の模様入りの線と、付け柱の筋（面と同じ高さ）
    for x in range(-22, 23):
        for y in (BANDS[0][0], BANDS[1][0]):
            put(x, -177, y, CQ)
    for x in PILASTERS:
        for y in range(1, BASE_TOP):
            if y not in (BANDS[0][0], BANDS[1][0]):
                put(x, -177, y, QP("y"))
    # 冠（+45 の持ち送り、+46..+47 の帯、+48 の半ブロック）
    for x in range(-22, 23):
        zf = base_face(x, 45)
        if zf + 1 <= -148:
            put(x, zf + 1, 45, stairs("quartz", "north", "top"))
            for y in (46, 47):
                put(x, zf + 1, y, CQ if x % 4 == 0 else QB)
            put(x, zf + 1, BASE_TOP, slab("polished_diorite", "bottom"))
    # 結晶の灯り（+44。柱の間の真ん中）と、縦一列の窓（中は詰まっているので、遮光ガラス）
    for xc in (0, -8, 8, -16, 16):
        zf = base_face(xc, 44)
        put(xc, zf, 44, SL)
        put(xc, zf, 43, CQ)
    for (xc, rows) in ((0, ((30, 34), (37, 40))), (-8, ((31, 33), (37, 40))), (8, ((31, 33), (37, 40))),
                       (-17, ((16, 18), (23, 26), (31, 33), (37, 40))), (17, ((16, 18), (23, 26), (31, 33), (37, 40)))):
        for (y0, y1) in rows:
            for y in range(y0, y1 + 1):
                zf = base_face(xc, y)
                put(xc, zf, y, block("tinted_glass"))
            put(xc, base_face(xc, y0 - 1), y0 - 1, QB)
            put(xc, base_face(xc, y1 + 1), y1 + 1, QB)


# ---------------------------------------------------------------- 左右の塔（9×9。胴 +20、上の段 7×7、銅の屋根 2：1 の先 +36）
def towers():
    for s in (-1, 1):
        x0, x1 = sorted((14 * s, 22 * s))
        for x in range(x0, x1 + 1):
            for z in range(Z_TB, Z_TF + 1):
                edge = x in (x0, x1) or z in (Z_TB, Z_TF)
                corner = x in (x0, x1) and z in (Z_TB, Z_TF)
                for y in range(1, TW_BODY + 1):
                    if edge:
                        if y <= 2:
                            st = QB                                   # 土台
                        elif corner and (y // 2) % 2 == 0:
                            st = QB                                   # 角の石
                        elif y in (WALK, TW_BODY):
                            st = CQ if corner else QB                 # 帯（歩廊の高さ）と軒の帯
                        else:
                            st = white()
                        put(x, z, y, st)
                    elif y <= 2:
                        put(x, z, y, STONE)
                    elif y == FL:
                        put(x, z, y, floor_tile())
                    elif y >= 19:
                        put(x, z, y, white())                         # らせん階段の上は詰める
        # 胴の上の水切り（上の段のまわり、+21）
        xi0, xi1 = sorted((15 * s, 21 * s))
        for x in range(x0, x1 + 1):
            for z in range(Z_TB, Z_TF + 1):
                if xi0 <= x <= xi1 and Z_TB + 1 <= z <= Z_TF - 1:
                    continue
                if z == Z_TF:
                    f = "north"
                elif z == Z_TB:
                    f = "south"
                else:
                    f = "east" if x == x0 else "west"
                put(x, z, TW_BODY + 1, stairs("quartz", f))
        # 上の段（7×7、+21..+26）
        for x in range(xi0, xi1 + 1):
            for z in range(Z_TB + 1, Z_TF):
                edge = x in (xi0, xi1) or z in (Z_TB + 1, Z_TF - 1)
                corner = x in (xi0, xi1) and z in (Z_TB + 1, Z_TF - 1)
                for y in range(TW_BODY + 1, TW_TOP + 1):
                    if edge:
                        put(x, z, y, QB if corner or y == TW_TOP else white())
                    else:
                        put(x, z, y, white())
        # 上の段の窓（遮光ガラス。中は詰まっている）
        xc = 18 * s
        for (x, z) in ((xc, Z_TF - 1), (xc, Z_TB + 1), (21 * s, -152), (15 * s, -152)):
            for y in (23, 24, 25):
                put(x, z, y, block("tinted_glass"))
            put(x, z, 22, QB)
        # 緑青の銅の四角すいの屋根（2：1。軒の縁は石英の逆さ階段。先 +36）
        X, Y, Z = 18 * s - CX0, TW_TOP + 1 - CY0, -152 - CZ0
        Pyramid(C, copper, X, Z, 4, Y, pitch=2, thickness=9, mat="waxed_oxidized_cut_copper",
                mat_block="waxed_oxidized_copper").build()
        put(18 * s, -152, APEX, slab("waxed_oxidized_cut_copper", "bottom"))   # 先（半ブロック。上に湧かない）
        spiral(s)
        # 矢狭間（らせん階段の頭の高さ）
        for (x, z, ys) in ((xc, Z_TF, (9, 10)), (xc, Z_TF, (14, 15)), (22 * s, -152, (12, 13)), (22 * s, -152, (16, 17)),
                           (xc, Z_TB, (15, 16))):
            for y in ys:
                put(x, z, y, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
        # 1階の扉（受付・衛兵の間から）と、歩廊へ出る口
        one_door(14 * s, -154, FT, "east" if s > 0 else "west")
        for y in (WALK + 1, WALK + 2):
            put(14 * s, -154, y, AIR)


def spiral(s):
    """左右の塔のらせん階段：芯（3×3）のまわりの幅2の輪。直線の3段ずつ上り、角は踊り場。
    北の内の角（1階の扉）から、塔の内側の面を南へ → 正面 → 外の面 → 裏、と回って、+15 の踊り場（1階の扉の真上）から歩廊へ出る。
    1回りの下は石で埋め、1階の扉の上の踊り場の下は半ブロック。芯のくぼみに灯り"""
    def X(u):
        return u * s

    def F(f):
        return f if s > 0 else {"east": "west", "west": "east"}.get(f, f)
    start = [(15, -155), (15, -154), (16, -155), (16, -154)]
    seq = []
    for z in (-153, -152, -151):
        seq.append(("run", [(15, z), (16, z)], "south"))
    seq.append(("land", [(15, -150), (15, -149), (16, -150), (16, -149)], None))
    for u in (17, 18, 19):
        seq.append(("run", [(u, -150), (u, -149)], "east"))
    seq.append(("land", [(20, -150), (20, -149), (21, -150), (21, -149)], None))
    for z in (-151, -152, -153):
        seq.append(("run", [(20, z), (21, z)], "north"))
    seq.append(("land", [(20, -155), (20, -154), (21, -155), (21, -154)], None))
    for u in (19, 18, 17):
        seq.append(("run", [(u, -155), (u, -154)], "west"))
    seq.append(("land", start, None))
    y = FL
    for kind_, cells, facing in seq:
        if kind_ == "run":
            y += 1
        for (u, z) in cells:
            put(X(u), z, y, stairs("polished_diorite", F(facing)) if facing else PD)
            if (u, z) in start:
                put(X(u), z, y - 1, slab("polished_diorite", "top"))
            else:
                for yy in range(FT, y):
                    put(X(u), z, yy, white())
    # 芯（3×3）と、くぼみの灯り（段の上の頭の高さ）
    for u in (17, 18, 19):
        for z in (-153, -152, -151):
            for yy in range(FT, 19):
                put(X(u), z, yy, white())
    for (u, z, yy) in ((17, -152, 7), (18, -151, 10), (19, -152, 13), (18, -153, 16)):
        put(X(u), z, yy, lantern(False))


# ---------------------------------------------------------------- 門の壁（アーチ・落とし格子・受付・衛兵の間・紋章・歩廊）
def gate():
    for x in range(-13, 14):
        for z in range(Z_GB, Z_GF + 1):
            for y in range(1, WALK + 1):
                put(x, z, y, white())
            put(x, z, WALK, PD if abs(x) > 5 or z <= -152 else white())
    # 通路（幅9、アーチの形のまま奥まで）
    for z in range(Z_GB, Z_GF + 1):
        for x in range(-4, 5):
            put(x, z, FL, QB if abs(x) == 4 else (CQ if x == 0 and z in (-151, -156) else road_paving()))
            for y in range(FT, 15):
                if arch_open(x, y):
                    put(x, z, y, AIR)
        # アーチの角をなめらかに（逆さの階段）
        for (x, y) in ((4, 13), (3, 14), (2, 15)):
            for sx in (1, -1):
                put(x * sx, z, y, stairs("quartz", "east" if sx > 0 else "west", "top"))
    # アーチの石（正面と裏の面）：石英のレンガ、要石は模様入り
    for z in (Z_GF, Z_GB):
        for x in range(-6, 7):
            for y in range(9, 17):
                d = math.hypot(x, y - 10)
                if arch_open(x, y) or d > 6.3 or y < 10:
                    continue
                if (abs(x), y) in ((4, 13), (3, 14), (2, 15)):
                    continue
                put(x, z, y, CQ if x == 0 else QB)
        put(0, z, 15, CQ)
    # 落とし格子（上げてある。アーチの上の方に下の端が見える）
    bars = block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)
    for x in range(-3, 4):
        put(x, Z_GF - 1, 13, bars)
    for x in range(-2, 3):
        put(x, Z_GF - 1, 14, bars)
    for x in (-4, -2, 0, 2, 4):
        put(x, Z_GF - 1, 12, bars)
    # 受付（西）と衛兵の間（東）：x ±6..±13、z −151..−156、天井 +9
    for s in (-1, 1):
        for x in range(6, 14):
            for z in range(-156, -150):
                put(x * s, z, FL, floor_tile())
                for y in range(FT, LOW_CEIL):
                    put(x * s, z, y, AIR)
                put(x * s, z, LOW_CEIL, white())
        one_door(5 * s, -155, FT, "west" if s > 0 else "east")
        for x in (8 * s, 11 * s):
            for y in (5, 6, 7):
                put(x, Z_GF, y, bars)
    # 受付の窓口（通路に向く）
    for z in (-152, -153):
        put(-5, z, FT, slab("spruce", "top"))
        for y in (5, 6):
            put(-5, z, y, AIR)
    # 紋章の板（x −5..5、z −150..−151、+16..+28）：結界の紋（円・四角・十字。真ん中に結晶）
    crest = ["..ccccc..",
             ".c..t..c.",
             "c.qqtqq.c",
             "c.q.t.q.c",
             "ctttLtttc",
             "c.q.t.q.c",
             "c.qqtqq.c",
             ".c..t..c.",
             "..ccccc.."]
    for z in (Z_GF, Z_GF - 1):
        for x in range(-5, 6):
            put(x, z, 16, QB)
            put(x, z, 26, CQ if x % 2 == 0 else QB)
            for y in range(17, 26):
                put(x, z, y, QB if abs(x) == 5 else white())
        for x in range(-4, 5):
            put(x, z, 27, white() if abs(x) < 4 else QB)
        for x in range(-2, 3):
            put(x, z, 28, QB)
        put(-4, z, 27, stairs("quartz", "east"))
        put(4, z, 27, stairs("quartz", "west"))
        put(-2, z, 28, stairs("quartz", "east"))
        put(2, z, 28, stairs("quartz", "west"))
    for i, row in enumerate(crest):
        y = 25 - i
        for j, ch in enumerate(row):
            x = j - 4
            st = {"c": CQ, "q": QP("x" if i in (2, 6) else "y"), "t": CUT, "L": SL,
                  ".": block("polished_andesite")}[ch]
            put(x, Z_GF, y, st)
    # 胸壁（正面、紋章の板の左右）と、歩廊の灯り
    for s in (-1, 1):
        for x in range(6, 14):
            put(x * s, Z_GF, WALK + 1, white())
            if x % 2 == 0:
                put(x * s, Z_GF, WALK + 2, QB)
        for x in (9, 13):
            put(x * s, Z_GF, WALK + 2, lantern(False))
    # 歩廊の奥（広間の南の壁が +20 まで立ち上がる。歩廊から広間をのぞく高窓）
    for x in range(-13, 14):
        for y in range(WALK + 1, CEIL + 1):
            put(x, Z_GB, y, white() if y < CEIL else QB)
    for s in (-1, 1):
        for x in (8, 9, 10):
            for y in (17, 18, 19):
                put(x * s, Z_GB, y, pane(GLASS))
    # 正面の灯り（アーチの左右の腕木のランタン）と旗
    for s in (-1, 1):
        wall_lamp(7 * s, Z_GF, 11, "south", reach=2, chain=1)
        banner(10 * s, Z_GF + 1, 13, "south", "white", CREST_BANNER)


CREST_BANNER = [("mr", "cyan"), ("mc", "white"), ("sc", "cyan"), ("bo", "cyan")]


# ---------------------------------------------------------------- 広間（天井16）
def hall_inside(x, z, y):
    return abs(x) <= 13 and Z_GB > z > base_face(x, y) and FT <= y < CEIL


def hall():
    for s in (-1, 1):
        x = 14 * s
        for y in range(1, CEIL + 1):
            for z in range(base_face(x, y) + 1, Z_GB + 1):
                put(x, z, y, QB if y <= 2 or y == CEIL else white())
    for x in range(-13, 14):
        for z in range(base_face(x, FL) + 1, Z_GB):
            for y in (1, 2):
                put(x, z, y, STONE)
            if abs(x) <= 6:
                st = QB if abs(x) == 6 else (CQ if x == 0 and z in (-159, -163) else road_paving())
            else:
                st = floor_tile()
            put(x, z, FL, st)
            put(x, z, CEIL, white())
    # 天井の梁（柱の列の上）
    for x in range(-13, 14):
        for z in (-161, -169):
            if z > base_face(x, CEIL):
                put(x, z, CEIL, QP("x"))
    for x in (-8, 8):
        for z in range(base_face(x, CEIL) + 1, Z_GB):
            put(x, z, CEIL, QP("z"))
    # 柱（3×3）：台・柱身（青緑の帯）・柱頭
    for (xc, zc) in COLS:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = xc + dx, zc + dz
                put(x, z, FL, QB)
                put(x, z, FT, PD)
                for y in range(FT + 1, CEIL - 2):
                    put(x, z, y, CUT if y == 11 else QP("y"))
                put(x, z, CEIL - 2, CQ)
                put(x, z, CEIL - 1, QB)
        for (dx, dz, f) in ((-2, 0, "east"), (2, 0, "west"), (0, -2, "south"), (0, 2, "north")):
            for k in (-1, 0, 1):
                ax, az = (xc + dx, zc + k) if dx else (xc + k, zc + dz)
                put(ax, az, CEIL - 1, stairs("quartz", f, "top"))
                put(ax, az, FT, stairs("quartz", OPP[f]))
    # 高窓（東西の壁、翼の屋根より上）
    for s in (-1, 1):
        for zs in ((-160, -159), (-165, -164), (-171, -170)):
            for z in zs:
                for y in (17, 18, 19):
                    put(14 * s, z, y, pane(GLASS))


# ---------------------------------------------------------------- 転移門の台・輪・転移の円・封じられた大扉
def platform():
    """3段（z −163・−164・−165）と台（z −166..−171、上 y 6）。幅 x −6..6。台のいちばん奥の列に輪が立つ"""
    for x in range(-6, 7):
        put(x, -163, FT, stairs("quartz", "north"))
        put(x, -164, FT, white())
        put(x, -164, FT + 1, stairs("quartz", "north"))
        for y in (FT, FT + 1):
            put(x, -165, y, white())
        put(x, -165, FT + 2, stairs("quartz", "north"))
        for z in range(-171, -165):
            for y in (FT, FT + 1):
                put(x, z, y, white())
            put(x, z, FT + 2, PD if abs(x) < 6 else QB)
    # 転移の円（台の上の床。真ん中は青緑）
    for x in range(-3, 4):
        for z in range(-171, -165):
            d = math.hypot(x, z + 168)
            if 1.5 <= d <= 2.5:
                put(x, z, FT + 2, CQ)
            elif d < 1.0:
                put(x, z, FT + 2, CUT)
    # 輪（台の奥の列 z −171。塔の壁から1つ出る。外は青緑の銅、内は模様入りの石英と、十字の向きに結晶の灯り）
    for x in range(-7, 8):
        for y in range(FT + 1, 19):
            if ring_cell(x, y):
                a = math.atan2(y - RING_Y, x)
                sec = int(round(a / (math.pi / 8))) % 16
                if ring_d(x, y) < 5.6:
                    st = SL if sec in (0, 4, 8, 12) else CQ
                else:
                    st = CUT
                put(x, RING_Z, y, st)
            elif ring_open(x, y):
                put(x, RING_Z, y, AIR)
    # 輪の奥のくぼみ（輪の内の形のまま、塔の壁の中へ4つ）と、封じられた塔の大扉
    for z in (-172, -173, -174, -175):
        for x in range(-4, 5):
            put(x, z, FT + 2, PD)
            for y in range(FT + 3, 17):
                if ring_open(x, y):
                    put(x, z, y, AIR)
    for x in range(-4, 5):
        for y in range(FT + 3, 17):
            if not ring_open(x, y):
                continue
            edge = not all(ring_open(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                st = CQ
            elif x == 0:
                st = block("dark_prismarine")                    # 2枚の扉の合わせ目
            elif (x + y) % 3 == 0:
                st = block("waxed_oxidized_copper")
            else:
                st = CUT
            put(x, -176, y, st)
    put(0, -176, RING_Y, CQ)


# ---------------------------------------------------------------- 両翼（1階建て。外の壁 x ±21、中 x ±15..±20、屋根は外へ下がる片流れ）
def wing_inside(x, z, y):
    return 15 <= abs(x) <= 20 and Z_GB > z > base_face(x, y) and FT <= y < LOW_CEIL


def wings():
    for s in (-1, 1):
        for a in range(15, 23):
            x = a * s
            for z in range(base_face(x, FL) + 1, Z_GB + 1):
                if a == 22:
                    continue
                if a == 21 or z == Z_GB:
                    for y in range(1, LOW_CEIL):
                        put(x, z, y, QB if y <= 2 else white())
                else:
                    for y in (1, 2):
                        put(x, z, y, STONE)
                    put(x, z, FL, floor_tile())
        # 屋根（片流れ 1：1。教会の側廊と同じ。外の軒 +9 から、広間の側 +16 へ。軒の下に石英の持ち送り）
        up = "west" if s > 0 else "east"
        for a in range(15, 23):
            x = a * s
            yr = LOW_CEIL + (22 - a)
            for z in range(Z_GB, -178, -1):
                if z <= base_face(x, yr):
                    break
                if a <= 21:
                    for y in range(LOW_CEIL, yr):
                        put(x, z, y, white())
                put(x, z, yr, stairs("waxed_oxidized_cut_copper", up))
                if a == 22 and z > base_face(x, LOW_CEIL - 1):
                    put(x, z, LOW_CEIL - 1, stairs("quartz", up, "top"))
        # 外の窓
        for z in (-160, -164, -170):
            for y in (5, 6):
                put(21 * s, z, y, pane(GLASS))
        # 外の壁の足もとの水切り（軒の下。地面に敵が湧かないように半ブロックで覆う）
        for z in range(base_face(22 * s, 1) + 1, Z_GB + 1):
            put(22 * s, z, 1, slab("polished_diorite", "bottom"))


# ---------------------------------------------------------------- 広間の屋根（寄棟 1：1。軒 +21、棟 +30、半ブロック +31）
def in_tower(x, z):
    """左右の塔（胴・上の段・屋根）の範囲"""
    return 14 <= abs(x) <= 22 and Z_TB <= z <= Z_TF


def hall_roof():
    """広間の寄棟（都市のルール⑥：幅12以上は 1：1）。範囲は軒の出を含む x −15..15、z −174..−156。
    北の面は塔の基部の壁に入る所を置かない（壁にぶつかって終わる）。南西・南東の角は左右の塔に入る所を置かない。
    屋根の下（広間の天井 +20 の上）は詰める"""
    x0, x1, z0, z1, y0 = -15, 15, -174, Z_TB, CEIL + 1
    km = min((x1 - x0) // 2, (z1 - z0) // 2)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if in_tower(x, z):
                continue
            dx, dz = min(x - x0, x1 - x), min(z - z0, z1 - z)
            k = min(dx, dz)
            top = y0 + k

            def free(y):
                return not in_base(x, z, y)
            if abs(x) <= 14 and z <= Z_GB:
                for y in range(y0, top):
                    if free(y):
                        put(x, z, y, white())
            if k >= km:
                if free(top):
                    put(x, z, top, block("waxed_oxidized_copper"))
                if free(top + 1):
                    put(x, z, top + 1, slab("waxed_oxidized_cut_copper", "bottom"))
                continue
            if dx <= dz:
                facing = "east" if x - x0 <= x1 - x else "west"
            else:
                facing = "south" if z - z0 <= z1 - z else "north"
            if free(top):
                put(x, z, top, stairs("waxed_oxidized_cut_copper", facing))
            if k == 0 and free(y0 - 1) and not (abs(x) <= 14 and z <= Z_GB):
                put(x, z, y0 - 1, stairs("quartz", facing, "top"))


# ---------------------------------------------------------------- 受付（西）と衛兵の間（東）
def guard_rooms():
    # 受付：通路に向いた窓口、入塔の記録の書見台、塔の地図、鍵の樽
    wall_sign(-4, -154, 6, "east", ["受付", "入塔の記録", "", "パーティーの代表が名を"])
    put(-7, -151, FT, slab("spruce", "top"))
    put(-8, -151, FT, slab("spruce", "top"))
    chair(-8, -152, FT, "south")
    put(-7, -151, FT + 1, block("candle", candles=2, lit=True, waterlogged=False))
    put(-12, -156, FT, block("lectern", facing="east", has_book=True, powered=False),
        lectern_nbt("入塔の記録", "受付", ["入塔の記録\n\n塔へ向かうパーティーは、ここに代表の名と人数を書くこと。\n\n戻ったら、戻ったと書くこと。",
                                          "記録のない者を、門は探しに行けない。\n\n── 門番"]))
    for x in (-9, -10):
        frame(x, -156, 6, "south", "map")
    for (x, z) in ((-13, -151), (-13, -152)):
        barrel(x, z, FT)
    barrel(-13, -151, FT + 1)
    hang_down(-9, -153, 7)
    # 衛兵の間：予備の鎧の防具立て、武器掛け、机と腰かけ、鍵の樽
    for z in (-151, -153):
        stand(13, z, FT, "west", head="iron_helmet", chest="iron_chestplate", legs="iron_leggings", feet="iron_boots")
    for (x, item) in ((8, "iron_sword"), (9, "crossbow"), (10, "iron_sword")):
        frame(x, -156, 6, "south", item, rotation=1 if item == "iron_sword" else 0)
    for x in (8, 9):
        put(x, -153, FT, slab("spruce", "top"))
    chair(7, -153, FT, "west")
    chair(10, -153, FT, "east")
    put(8, -153, FT + 1, block("candle", candles=1, lit=True, waterlogged=False))
    for (x, z) in ((13, -156), (12, -156)):
        barrel(x, z, FT)
    hang_down(9, -152, 7)


# ---------------------------------------------------------------- 広間の中（旗・腰かけ・塔の灯り・バルドの書見台・灯り）
FLOOR_COLORS = (("lime", "第1章", "1〜5階"), ("light_blue", "第2章", "6〜10階"), ("orange", "第3章", "11〜15階"),
                ("white", "第4章", "16〜20階"))


def hall_inside_things():
    # 南の壁（入口の上）の旗
    for s in (-1, 1):
        banner(7 * s, Z_GB - 1, 13, "north", "white", CREST_BANNER)
    # 西の側廊：待つ人の腰かけ（壁を背に）
    for z in (-163, -164, -165, -166, -167):
        chair(-13, z, FT, "west")
    for z in (-162, -168):
        put(-13, z, FT, slab("spruce", "top"))
    # 塔の灯り（東の壁）：4段×5本。下から第1章。ひらいた階だけ灯る（いまは1階だけ）
    for y in range(5, 15):
        for z in range(-168, -161):
            put(14, z, y, block("polished_andesite") if -167 <= z <= -163 and 5 <= y <= 13 else QB)
    for i, (color, ch, floors) in enumerate(FLOOR_COLORS):
        ys = 5 + 2 * i
        for k in range(5):
            z = -167 + k
            put(13, z, ys, slab("quartz", "top"))
            candle(13, z, ys + 1, 1, color, lit=(i == 0 and k == 0))
        wall_sign(13, -168, ys + 1, "west", [ch, floors])
    wall_sign(13, -165, 15, "west", ["塔の灯り", "ひらいた階に", "灯がともる"])
    wall_sign(13, -162, FT + 2, "west", ["次にひらく階", "", "（管理者の予告）"])
    # 門番バルドの書見台（台の東。バルドは書見台の東に立つ）
    put(11, -165, FT, block("lectern", facing="west", has_book=True, powered=False),
        lectern_nbt("塔の門の心得", "門番", ["塔の門の心得\n\n一、転移の円に立ち、行く階を選ぶ。\n二、ひらいた階にだけ行ける。",
                                           "三、戻るときは、階の入口の円から。\n四、受付に記録を残すこと。"]))


# ---------------------------------------------------------------- 両翼の中
def wing_rooms():
    for s in (-1, 1):
        one_door(14 * s, -160, FT, "east" if s > 0 else "west")
        # 仕切り（z −167）と扉
        for a in range(15, 21):
            for y in range(FT, LOW_CEIL):
                put(a * s, -167, y, white())
        one_door(17 * s, -167, FT, "north")
    # 西：控えの間（長い机と地図、腰かけ、パーティー募集の掲示）
    for z in range(-164, -159):
        for x in (-18, -17):
            put(x, z, FT, block("stripped_spruce_wood", axis="y"))
        chair(-19, z, FT, "west")
        chair(-16, z, FT, "east")
    for (x, z) in ((-18, -163), (-17, -161)):
        frame(x, z, FT + 1, "up", "map")
    put(-18, -160, FT + 1, block("candle", candles=3, lit=True, waterlogged=False))
    wall_sign(-18, -158, 6, "north", ["パーティー募集", "", "行く階と人数を", "ここに書いて待つ"])
    wall_sign(-16, -158, 6, "north", ["控えの間", "", "出発の前に", "ここで待ち合わせ"])
    for (x, z) in ((-20, -165), (-20, -166)):
        barrel(x, z, FT)
    hang_down(-17, -162, 7)
    # 西：補給の隅（鍵の樽、水の大釜、砥石）
    for (x, z) in ((-20, -168), (-20, -169), (-20, -170), (-19, -168)):
        barrel(x, z, FT)
    barrel(-20, -168, FT + 1)
    put(-15, -169, FT, block("water_cauldron", level=3))
    put(-15, -171, FT, block("grindstone", face="floor", facing="north"))
    wall_sign(-16, -168, 6, "north", ["補給の隅", "", "出発の前に", "水と装備を確かめる"])
    hang_down(-17, -171, 7)
    # 東：門番の部屋（寝台・机・古い鎧・本棚・鍵の樽）
    put(20, -165, FT, block("gray_bed", facing="north", part="head", occupied=False))
    put(20, -164, FT, block("gray_bed", facing="north", part="foot", occupied=False))
    for z in (-159, -160):
        put(20, z, FT, slab("spruce", "top"))
    chair(19, -159, FT, "east")
    put(20, -160, FT + 1, block("candle", candles=1, lit=True, waterlogged=False))
    stand(16, -166, FT, "east", head="iron_helmet", chest="chainmail_chestplate", legs="chainmail_leggings",
          feet="iron_boots", offhand="shield")
    for y in (FT, FT + 1):
        put(20, -166, y, block("bookshelf"))
    barrel(20, -162, FT)
    hang_down(18, -162, 7)
    # 東：踏破の記録の間（本棚・記録の書見台）
    for z in range(-175, -167):
        for y in (FT, FT + 1, FT + 2):
            if z > base_face(20, y):
                put(20, z, y, block("bookshelf"))
    put(16, -171, FT, block("lectern", facing="east", has_book=True, powered=False),
        lectern_nbt("踏破の記録", "門番", ["踏破の記録\n\n各階を初めて越えた日と、その仲間の名を書く。",
                                         "第1章\n1階　\n2階　\n3階　\n4階　\n5階　",
                                         "第2章\n6階　\n7階　\n8階　\n9階　\n10階　",
                                         "第3章\n11階　\n12階　\n13階　\n14階　\n15階　",
                                         "第4章\n16階　\n17階　\n18階　\n19階　\n20階　"]))
    hang_down(17, -171, 7)


# ---------------------------------------------------------------- 塔前広場（石柱の列・結晶灯・腰かけ・掲示板・記念碑の場所）
def pillar(xc, zc, h):
    """古代の石柱（細い尖塔。塔へ向かって高くなる）：台（模様入りの石英と、内へ上がる階段）、石英の柱身、
    逆さの階段が4方へ開く柱頭、結晶の灯り（シーランタン）、青緑の笠"""
    t = terrace(zc)
    put(xc, zc, t + 1, CQ)
    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        put(xc + dx, zc + dz, t + 1, stairs("quartz", f))
        put(xc + dx, zc + dz, t + h - 1, stairs("quartz", f, "top"))
    for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        put(xc + dx, zc + dz, t + 1, slab("quartz", "bottom"))
    for y in range(t + 2, t + h - 1):
        put(xc, zc, y, QP("y"))
    put(xc, zc, t + h - 1, CQ)
    put(xc, zc, t + h, SL)
    put(xc, zc, t + h + 1, slab("waxed_oxidized_cut_copper", "bottom"))


def plaza():
    for (zc, h) in PILLARS:
        for xc in (-19, 19):
            pillar(xc, zc, h)
    # 結晶灯（真ん中の帯の両脇、12ごとに互い違い）
    for (x, z) in LAMPS:
        crystal(x, z, terrace(z) + 1)
    # 腰かけ（広場の縁。中を向く）
    for s in (-1, 1):
        for z in (-117, -118, -119, -123, -124, -125):
            chair(22 * s, z, terrace(z) + 1, "east" if s > 0 else "west")
    # 掲示板（東。塔の知らせ）
    for z in (-132, -128):
        for y in (2, 3, 4):
            put(13, z, y, log("spruce", "y"))
    for z in range(-131, -128):
        for y in (3, 4):
            put(13, z, y, block("spruce_planks"))
        put(13, z, 5, slab("spruce", "bottom"))
    for z in (-132, -128):
        put(13, z, 5, slab("spruce", "bottom"))
    wall_sign(12, -131, 4, "west", ["塔の知らせ", "", "ひらいた階・", "次にひらく階"])
    wall_sign(12, -130, 4, "west", ["パーティー募集は", "広間の控えの間へ"])
    wall_sign(12, -129, 4, "west", ["夜の塔前広場は", "灯りのそばで", "待つこと"])
    # 記念碑の場所（西。1週目の共同の目標を達したら、記念碑が立つ）：石英のレンガの縁の四角
    for x in range(-15, -10):
        for z in range(-132, -127):
            if x in (-15, -11) or z in (-132, -128):
                put(x, z, 1, QB)
            else:
                put(x, z, 1, PD)


# ---------------------------------------------------------------- 灯り（広間・通路・門の前・歩廊）
def lights():
    # 広間：天井から鎖で吊るランタン（側廊と身廊）
    for (x, z) in ((-11, -159), (11, -159), (-11, -165), (-11, -171), (11, -171), (0, -160), (-4, -163), (4, -163)):
        hang_down(x, z, 11 if abs(x) < 6 else 9)
    # 通路：天井（アーチの上）から
    hang_down(0, -153, 11)
    # 門の前の左右（塔の内側の面）の腕木のランタン
    for s in (-1, 1):
        wall_lamp(14 * s, -148, 8, "west" if s > 0 else "east", reach=2, chain=1)


# ---------------------------------------------------------------- 記念碑（1週目の共同の目標のあとに重ねる形）
def monument():
    def mput(x, z, y, st, nbt=None):
        put(x, z, y, st, nbt, w=M)
        M_OWNED.add((x - CX0, y - CY0, z - CZ0))
    # 台（5×3、2段）
    for x in range(-15, -10):
        for z in range(-131, -128):
            mput(x, z, 2, block("polished_andesite") if x in (-15, -11) else STONE)
    for x in range(-14, -11):
        mput(x, -130, 3, block("chiseled_stone_bricks"))
    # 碑（古代石：苔むした石・模様入りの石のレンガ）
    for y in range(4, 8):
        for x in (-14, -13, -12):
            st = block("mossy_stone_bricks") if (x + y) % 3 == 0 else (block("chiseled_stone_bricks") if y == 7 else STONE)
            mput(x, -130, y, st)
    mput(-13, -130, 8, block("chiseled_stone_bricks"))
    mput(-13, -130, 9, block("amethyst_cluster", facing="up", waterlogged=False))
    mput(-11, -130, 6, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["はじまりの週", "古代石 5,000個", "集めた冒険者に", "感謝をこめて"]))
    mput(-11, -130, 5, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["コネリアの人々", "より"]))


def build():
    rng.seed(1111)
    ground()
    plaza()
    base_wall()
    hall()
    wings()
    towers()
    gate()
    guard_rooms()
    platform()
    hall_inside_things()
    wing_rooms()
    hall_roof()
    lights()
    C.finalize()
    monument()
    M.finalize()


build()


# ---------------------------------------------------------------- 書き出し・確かめ・見本の絵
def S(x, y, z):
    """世界の座標 → 合わせた形の中の座標（見本の絵の目と見る所）"""
    return (x - CX0, y - CY0, z - CZ0)


VIEWS = {
    "approach": (S(0.5, 2.62, -117.5), S(0.5, 20.0, -165.0)),     # 北門の外から、塔前広場ごしに塔の入口
    "front": (S(0.5, 3.62, -128.5), S(0.5, 14.0, -152.0)),        # 広場の2つめの段から、門のアーチと紋章
    "aerial": (S(-36.0, 44.0, -108.0), S(0.0, 14.0, -150.0)),     # 上から（南西）
    "east": (S(44.0, 12.0, -128.0), S(8.0, 18.0, -160.0)),        # 東から（左右の塔・東の翼・基部の弧）
    "back": (S(30.0, 46.0, -196.0), S(0.0, 22.0, -160.0)),        # 北東の上から（基部の上の面・屋根）
    "passage": (S(0.5, 5.62, -147.5), S(0.5, 9.0, -172.0)),       # 門の前から、通路ごしに広間と転移門
    "hall": (S(0.5, 5.62, -158.5), S(0.5, 11.0, -173.0)),         # 広間の入口から、転移門
    "ring": (S(0.5, 8.62, -166.5), S(0.5, 10.0, -177.0)),         # 台の上（転移の円）から、輪の向こうの封じられた大扉
    "board": (S(4.5, 8.62, -166.5), S(13.0, 8.0, -165.0)),        # 台の上から、東の側廊のバルドの書見台と塔の灯り
    "aisle": (S(-12.5, 5.62, -159.5), S(-4.0, 9.0, -171.0)),      # 西の側廊から、柱ごしに転移門
    "reception": (S(-6.5, 5.62, -155.5), S(-12.0, 5.0, -151.0)),  # 受付
    "guard": (S(6.5, 5.62, -155.5), S(12.5, 5.0, -151.0)),        # 衛兵の間
    "waiting": (S(-15.5, 5.62, -158.5), S(-19.0, 4.5, -166.0)),   # 控えの間
    "supply": (S(-15.5, 5.62, -168.5), S(-20.0, 4.5, -172.0)),    # 補給の隅
    "keeper": (S(15.5, 5.62, -158.5), S(19.0, 4.5, -166.0)),      # 門番の部屋
    "records": (S(17.5, 5.62, -168.5), S(19.0, 4.5, -175.0)),     # 踏破の記録の間
    "stair": (S(15.5, 5.62, -154.5), S(17.0, 9.0, -149.0)),       # 東の塔の1階から、らせん階段
    "walk": (S(12.5, 16.62, -153.5), S(-6.0, 18.0, -153.5)),      # 門の上の歩廊（東から西）
    "plaza": (S(-12.5, 2.62, -116.5), S(8.0, 9.0, -146.0)),       # 広場の南西から（石柱の列と、塔の入口）
}
VIEWS_MONUMENT = {
    "monument": (S(-4.5, 3.62, -125.5), S(-13.0, 5.0, -130.0)),   # 1週目の共同の目標のあと：記念碑
}


def checked_world(which="base"):
    """確かめと見本の絵に使う写し：base（はじめ）・monument（記念碑を重ねたあと）。建物の下と外の地面（y 0）を足す"""
    cw = World(CW, CH, CD)
    cw.blocks = dict(C.blocks)
    cw.nbt = dict(C.nbt)
    cw.entities = list(C.entities)
    for x in range(-23, 24):
        for z in range(-177, -147):
            if (x - CX0, 0 - CY0, z - CZ0) not in cw.blocks:
                cw.blocks[(x - CX0, 0 - CY0, z - CZ0)] = block("grass_block", snowy=False)
                cw.blocks[(x - CX0, -1 - CY0, z - CZ0)] = block("dirt")
    if which == "monument":
        for c in M_OWNED:
            if c in M.blocks:
                cw.blocks[c] = M.blocks[c]
                if c in M.nbt:
                    cw.nbt[c] = M.nbt[c]
                else:
                    cw.nbt.pop(c, None)
            else:
                cw.blocks.pop(c, None)
                cw.nbt.pop(c, None)
        cw.entities += list(M.entities)
    return cw




def cut_world(world, y_cut):
    """y_cut（道からの高さ）より上を取りのぞいた写し（真上からの絵のため）"""
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut - CY0:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut - CY0]
    return cw


def render_quick(out, names=None):
    from mapgen.render import Scene
    os.makedirs(out, exist_ok=True)
    wd = checked_world("base")
    for (world, views) in ((wd, VIEWS), (None, VIEWS_MONUMENT)):
        todo = [k for k in views if not names or k in names]
        if not todo:
            continue
        sc = Scene(world or checked_world("monument"), ground_y=1 - CY0)
        for k in todo:
            e, t = views[k]
            sc.render(eye=e, target=t, width=960, height=540, fov=70, path=os.path.join(out, "tower_%s.png" % k))
            print("描きました:", k)
    for key, y_cut in (("cut0", 5), ("cut1", 17)):
        if names and key not in names:
            continue
        cx, cz = 0 - CX0, -146 - CZ0
        Scene(cut_world(wd, y_cut), ground_y=1 - CY0).render(
            eye=(cx, 170.0, cz + 0.01), target=(cx, 0.0, cz), width=900, height=1200, fov=24,
            path=os.path.join(out, "tower_%s.png" % key))
        print("描きました:", key)


def split(world, x0, y0, z0, w_, h_, d_, owned=None):
    """合わせた形から、箱（世界の (x0, y0, z0) から w_×h_×d_）を切り出した World"""
    out = World(w_, h_, d_)
    ox, oy, oz = x0 - CX0, y0 - CY0, z0 - CZ0
    for (x, y, z), st in world.blocks.items():
        if owned is not None and (x, y, z) not in owned:
            continue
        X, Y, Z = x - ox, y - oy, z - oz
        if out.inside(X, Y, Z):
            out.blocks[(X, Y, Z)] = st
            if (x, y, z) in world.nbt:
                out.nbt[(X, Y, Z)] = world.nbt[(x, y, z)]
    for e in world.entities:
        X, Y, Z = e.cell[0] - ox, e.cell[1] - oy, e.cell[2] - oz
        if not out.inside(X, Y, Z):
            continue
        nbt = copy.deepcopy(e.nbt)
        if "TileX" in nbt:
            nbt["TileX"], nbt["TileY"], nbt["TileZ"] = Int(X), Int(Y), Int(Z)
        out.entities.append(Entity(e.kind, (e.pos[0] - ox, e.pos[1] - oy, e.pos[2] - oz), (X, Y, Z), nbt,
                                   facing=e.facing, item=e.item, items=e.items))
    return out


def in_box(x, y, z, x0, y0, z0, w_, h_, d_):
    return x0 <= x < x0 + w_ and y0 <= y < y0 + h_ and z0 <= z < z0 + d_


def lost_cells():
    """どちらの構造物の箱にも入らないブロック（あってはいけない）"""
    bad = []
    for (X, Y, Z) in C.blocks:
        x, y, z = X + CX0, Y + CY0, Z + CZ0
        if not (in_box(x, y, z, EX0, EY0, EZ0, EW, EH, ED) or in_box(x, y, z, PX0, PY0, PZ0, PLW, PLH, PLD)):
            bad.append((x, y, z, C.blocks[(X, Y, Z)][0]))
    for e in C.entities:
        x, y, z = e.cell[0] + CX0, e.cell[1] + CY0, e.cell[2] + CZ0
        if not (in_box(x, y, z, EX0, EY0, EZ0, EW, EH, ED) or in_box(x, y, z, PX0, PY0, PZ0, PLW, PLH, PLD)):
            bad.append((x, y, z, e.kind))
    return bad


def save_all(out):
    """3つの構造物を書き出す"""
    bad = lost_cells()
    if bad:
        raise SystemExit("構造物の箱からはみ出したブロック: %d %s" % (len(bad), bad[:10]))
    res = []
    ent = split(C, EX0, EY0, EZ0, EW, EH, ED)
    path = os.path.join(out, "tower_entrance.nbt")
    n = ent.save_structure(path)
    res.append((path, len(ent.blocks), n, len(ent.entities), (EW, EH, ED)))
    plz = split(C, PX0, PY0, PZ0, PLW, PLH, PLD)
    path = os.path.join(out, "tower_plaza.nbt")
    n = plz.save_structure(path)
    res.append((path, len(plz.blocks), n, len(plz.entities), (PLW, PLH, PLD)))
    mon = split(M, PX0, PY0, PZ0, PLW, PLH, PLD)
    owned_p = {(x - (PX0 - CX0), y - (PY0 - CY0), z - (PZ0 - CZ0)) for (x, y, z) in M_OWNED}
    path = os.path.join(out, "tower_plaza_monument.nbt")
    n = mon.save_structure(path, owned=lambda x, y, z: (x, y, z) in owned_p)
    res.append((path, len(owned_p), n, len(mon.entities), (PLW, PLH, PLD)))
    return res


CI_SHOTS = ["approach", "plaza", "aerial", "east", "passage", "hall", "ring", "board", "reception", "waiting", "walk",
            "monument"]


def ci_views():
    """自動の確認の写真の目と見る所（塔の入口の構造物を置いた所の足もとの地面（世界の x −22, y 0, z −177）から）"""
    out = []
    allv = dict(VIEWS)
    allv.update(VIEWS_MONUMENT)
    for shot in CI_SHOTS:
        e, t = allv[shot]
        out.append((shot, (e[0] - 1, e[1] - 1, e[2]), (t[0] - 1, t[1] - 1, t[2])))
    return out


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
    render_dir = None
    only = None
    ci = "--ci" in args
    if ci:
        args.remove("--ci")
    if "--render" in args:
        i = args.index("--render")
        render_dir = args[i + 1]
        del args[i:i + 2]
    if "--only" in args:
        i = args.index("--only")
        only = set(args[i + 1].split(","))
        del args[i:i + 2]
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    for (path, nb, npal, nent, size) in save_all(out):
        print("書き出しました:", path, "ブロック", nb, "種類", npal, "もの", nent, "大きさ", size)
    if render_dir:
        render_quick(render_dir, names=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_tower_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
