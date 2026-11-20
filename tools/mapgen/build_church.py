"""コネリアの結びの教会（PHASE 8。建築の文書「PHASE 8：結びの教会」の設計どおり）。

古代の「記憶守」の流れをくむ教会で、療養所を併設する（Phase 0-A）。司祭セレナ・アステルと見習いティナがいて、
倒れた冒険者は、ここで「呼び戻しの儀」によって大結晶から引き戻される（復活の場所）。
祈りの道（西の大通り）の突き当たり（環状路の外）に、正面を東（中央広場の方）へ向けて建つ。
  - 礼拝堂：身廊（中の幅9）と両側の側廊（幅3）を、8ごとの柱の列とアーチで分ける。3つの間に長椅子と呼び戻しの円、
    4つ目の間が内陣（祭壇と呼び戻しの結晶、後ろに丸い色ガラスの窓）。身廊の屋根は2：1の緑青の銅（軒 +14、棟 +28）、
    側廊は片流れ。中は木の小屋組み（陸梁と真束）が見える。床から陸梁まで12（都市のルール④の礼拝堂の天井12）。
  - 鐘楼：正面の北の角（9×9）。下の12段は古代の白い石、上は灰色の石。らせん階段（幅2）で鐘の間（床 +21）へ。
    緑青の銅の尖り屋根の先（光の棒）が +40（街の高さの順番で2番目）。
  - 療養所の棟：北西に離して建て（間2）、控室から渡り廊下でつなぐ。2階建て（1階は治療の間、2階は病室とマルグリットの部屋）。
  - 控室（内陣の北）と書庫（内陣の南）。教会前広場（白い石畳、正面へ2段）。北の庭と、南の小道（記憶の庭へ）。
  - 正面の入口には扉を付けない（呼び戻しの儀で戻る人のため、昼も夜も開いている【補完】）。
  - 記憶の庭（南）は別の構造物（church_garden.nbt）。塔から帰らなかった人々の名を刻む石と、桜【提案】。
  - 中に入れる所の樽は、鍵をかける（Lock）。チェスト・かまど・醸造台は置かない。

座標：平面図の (u, v) は、u が西→東（u=0 が世界の x −105）、v が北→南（v=0 が世界の z −22）。
世界の座標では x = u − 105、z = v − 22。高さ y は道のブロックが 0（足もとが 1）。礼拝堂の床のブロックは y=2（足もと +3）、
内陣の床 y=3（足もと +4）。構造物の (0, 0, 0) は世界の (x −105, 道のブロック, z −22)。構造物の中では X = u、Y = y、Z = v。
記憶の庭の構造物の (0, 0, 0) は世界の (x −96, 道のブロック, z 16)（教会の構造物から u 9・v 38）。庭の中の座標は (gu, gv)。

使い方: python3 tools/mapgen/build_church.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
  - <出力>/church.nbt（46×41×38）と church_garden.nbt（25×12×18）
  - --ci：自動の確認の写真の座標（CiScreenshots の MAP_VIEWS の行）を出す
ゲームの中では /place template amemaru:map/conelia/church と amemaru:map/conelia/church_garden で置ける。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from nbtlib.tag import Compound, String

from mapgen.arch import Palette, Pyramid, Roof, gable_wall
from mapgen.fixtures import crystal_lamp, hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, lectern_nbt, log, pane, sign_nbt, slab,
                          stairs, trapdoor, wall)

W, H, D = 46, 41, 38
ORIGIN = (-105, 0, -22)          # 構造物の (0, 0, 0) の世界の座標（y は道のブロック）
GARDEN_AT = (9, 0, 38)           # 記憶の庭の構造物を置く所（教会の構造物から）
GW, GH, GD = 25, 12, 18
LOCK = "amemaru:church_lock"     # 樽の鍵（この名前のアイテムを持たないと開かない）

FL, FT = 2, 3                    # 礼拝堂の床のブロック・足もと
CFL, CFT = 3, 4                  # 内陣
AISLE_TOP = 9                    # 側廊の外壁の上
NAVE_TOP = 14                    # 身廊の壁（アーケードの上）の上。屋根の軒の縁取りも +14
ROOF_Y0 = 15                     # 身廊の屋根のいちばん下（陸梁の高さ）
FACADE, APSE = 33, 1             # 正面の壁（x −72）と後ろの壁（x −104）の u
PIERS = (9, 17, 25)              # 柱の列の u（x −96・−88・−80）。9 は内陣のアーチ
BAYS = {1: (26, 32), 2: (18, 24), 3: (10, 16), 4: (2, 8)}
AXIS = 22                        # 身廊の軸の v（祈りの道の真ん中 z 0）
VN, VS = 13, 31                  # 側廊の外壁（z −9・9）
RN, RS = 17, 27                  # 身廊と側廊の境（アーケード。z −5・5）
TU0, TU1, TV0, TV1 = 25, 33, 4, 12      # 鐘楼（x −80..−72、z −18..−10）
SU0, SU1, SV0, SV1 = 1, 8, 2, 10        # 療養所の棟（x −104..−97、z −20..−12）
S1, S2, SC = 2, 7, 12            # 療養所の1階・2階の床のブロック（足もと +3・+8）と、2階の天井（屋根裏の床）
BELL_FLOOR = 21                  # 鐘の間の床のブロック（足もと +22）
TOWER_TOP = 28                   # 鐘楼の壁の上（尖り屋根の軒 +28）
CIRCLE = (13, AXIS)              # 呼び戻しの円の真ん中（復活の場所。足もと +3）

pal = Palette(seed=808, roof="waxed_oxidized_cut_copper", roof_block="waxed_oxidized_copper", trim="dark_oak", post="spruce",
              beam="dark_oak", plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)

COPPER = "waxed_oxidized_cut_copper"
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def put(u, v, y, state, nbt=None):
    w.put(u, y, v, state, nbt)


def get(u, v, y):
    return w.get(u, y, v)


def name_at(u, v, y):
    st = w.get(u, y, v)
    return st[0] if st else "air"


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state() if callable(state) else state)


def frame(u, v, y, facing, item, rotation=0):
    w.item_frame(u, y, v, facing, item, rotation=rotation)


def wall_lamp(u, v, y, out, reach=2, chain=1, style="wood"):
    """壁 (u, v) から out の向きへ腕木を出したランタン。腕木の高さ y、ランタンは y - 1 - chain"""
    wall_lantern(w, u, y, v, out, style=style, reach=reach, chain=chain)


def hang(u, v, y, chain=1):
    """y は鎖のいちばん上（上のブロックの下）。ランタンは y - chain。chain 0 なら、ランタンを y に直に吊る"""
    hang_lantern(w, u, y, v, chain=chain)


def wall_sign(u, v, y, facing, lines, wood="spruce"):
    put(u, v, y, block(wood + "_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))


def door_pair(u, v, y, facing, axis):
    """2枚の扉（y が下の段）。axis は扉を並べる向き（"u" か "v"）"""
    cells = ((u, v, "left"), (u + 1, v, "right")) if axis == "u" else ((u, v, "left"), (u, v + 1, "right"))
    for (a, b, hinge) in cells:
        put(a, b, y, door("spruce", facing, False, hinge))
        put(a, b, y + 1, door("spruce", facing, True, hinge))


def one_door(u, v, y, facing, wood="spruce", hinge="left"):
    put(u, v, y, door(wood, facing, False, hinge))
    put(u, v, y + 1, door(wood, facing, True, hinge))


def barrel(u, v, y, facing="up"):
    put(u, v, y, block("barrel", facing=facing, open=False),
        Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))


def bed(u, v, y, facing, color):
    """寝台。(u, v) が頭の側、facing は足から頭への向き"""
    du, dv = STEP[facing]
    put(u, v, y, block(color + "_bed", facing=facing, part="head", occupied=False))
    put(u - du, v - dv, y, block(color + "_bed", facing=facing, part="foot", occupied=False))


def candle(u, v, y, n=1):
    put(u, v, y, block("candle", candles=n, lit=True, waterlogged=False))


def shutter(u, v, y, facing):
    """開いた雨戸（facing の反対の側に板が来る。壁の外のマスに置き、板を壁に付ける）"""
    put(u, v, y, trapdoor("spruce", facing, "bottom", True))


# ---------------------------------------------------------------- 材料（都市のルール③⑤）
def gray_stone():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで"""
    r = rng.random()
    if r < 0.80:
        return block("stone_bricks")
    if r < 0.90:
        return block("cracked_stone_bricks")
    if r < 0.96:
        return block("mossy_stone_bricks")
    return block("andesite")


def white_stone():
    """古代の層の白い石：磨いた閃緑岩・閃緑岩・方解石"""
    r = rng.random()
    if r < 0.66:
        return block("polished_diorite")
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def white_paving():
    """古代の白い石畳（灰色の石のつぎはぎ1割）"""
    r = rng.random()
    if r < 0.55:
        return block("polished_diorite")
    if r < 0.75:
        return block("diorite")
    if r < 0.88:
        return block("calcite")
    return block("stone_bricks") if r < 0.95 else block("andesite")


def path_paving():
    """小道：砂利と丸石、ところどころ粗い土と石レンガ"""
    r = rng.random()
    if r < 0.45:
        return block("gravel")
    if r < 0.75:
        return block("cobblestone")
    return block("coarse_dirt") if r < 0.9 else block("stone_bricks")


def grass():
    return block("grass_block", snowy=False)


def wall_block(y, ancient_top=3):
    """壁の石：土台（y ≦ ancient_top。床の上1段まで）は古代の白い石、その上は灰色の石"""
    return white_stone() if y <= ancient_top else gray_stone()


# ---------------------------------------------------------------- 地面
def ground():
    for u in range(W):
        for v in range(D):
            put(u, v, 0, grass())
    # 教会前広場（x −70..−60、z −12..12）：白い石畳。真ん中の帯（祈りの道の続き）は磨いた閃緑岩と石英、縁は石レンガ
    for u in range(35, W):
        for v in range(10, 35):
            if v in (10, 34):
                put(u, v, 0, block("stone_bricks"))
            elif AXIS - 1 <= v <= AXIS + 1:
                put(u, v, 0, block("quartz_bricks") if v == AXIS and u % 4 == 1 else block("polished_diorite"))
            else:
                put(u, v, 0, white_paving())
    # 北の小道：広場の北の角から鐘楼の東と北を回り、北の庭と療養所の入口へ
    for u in range(34, 38):
        for v in range(1, 10):
            put(u, v, 0, path_paving())
    for u in range(9, 38):
        for v in range(1, 4):
            put(u, v, 0, path_paving())
    for u in range(9, 12):
        for v in range(4, 8):
            put(u, v, 0, path_paving())
    # 南の小道：広場の南の角から、教会の南を通って記憶の庭の北の門（u 20..22）へ
    for u in range(20, 35):
        for v in range(34, 37):
            put(u, v, 0, path_paving())
    for u in range(20, 23):
        put(u, 37, 0, path_paving())


# ---------------------------------------------------------------- 礼拝堂の壁
def nave_top(v):
    """身廊の屋根の、その列のいちばん上の階段の y（棟の列は棟のブロックの上）。屋根の外は None"""
    if not 16 <= v <= 28:
        return None
    col = min(v - 16, 28 - v)
    if col == 6:
        return ROOF_Y0 + 12
    return ROOF_Y0 + 2 * col + 1


def lean_top(v):
    """側廊の片流れの屋根の階段の y（v 12 と 32 は軒の出）"""
    if 12 <= v <= 16:
        return AISLE_TOP + (v - 12)
    if 28 <= v <= 32:
        return AISLE_TOP + (32 - v)
    return None


def church_shell():
    # 土台（古代の白い石、2段）と床
    for u in range(APSE, FACADE + 1):
        for v in range(VN, VS + 1):
            put(u, v, 0, white_stone())
            put(u, v, 1, white_stone())
            put(u, v, FL, block("stone_bricks"))
    # 側廊の外壁（北 v 13・南 v 31）
    for u in range(APSE, FACADE + 1):
        for v in (VN, VS):
            for y in range(0, AISLE_TOP + 1):
                put(u, v, y, wall_block(y))
    # 正面（u 33）と後ろの壁（u 1）：側廊の所は片流れの線まで、身廊の所は破風（屋根の面より1高い笠石）まで
    for u in (FACADE, APSE):
        for v in range(VN, VS + 1):
            top = max(t for t in (nave_top(v), lean_top(v), AISLE_TOP) if t is not None)
            for y in range(0, top + 1):
                put(u, v, y, wall_block(y))
            if v == AXIS:
                put(u, v, top + 1, block("chiseled_stone_bricks"))
                put(u, v, top + 2, wall("stone_brick"))
            else:
                put(u, v, top + 1, stairs("stone_brick", "south" if v < AXIS else "north"))
    # 内陣の両わきの壁（u 2..8、v 17・27）：控室・書庫との境。上は身廊の壁と同じ +14 まで
    for v in (RN, RS):
        for u in range(BAYS[4][0], BAYS[4][1] + 1):
            for y in range(FL, NAVE_TOP + 1):
                put(u, v, y, gray_stone())
    # 側廊の西の端の壁（u 9）：控室・書庫との境。片流れの屋根の下まで
    for v in list(range(VN + 1, RN)) + list(range(RS + 1, VS)):
        for y in range(FL, lean_top(v)):
            put(9, v, y, gray_stone())


def arch_row(fixed, a_lo, a_hi, y_spring, axis, face_lo, face_hi, top):
    """丸いアーチの開口：a_lo..a_hi（axis の向き）の上に、y_spring から上がるアーチの石（逆さの階段と、真ん中の上付きのハーフ）と、
    その上の壁（top まで）。fixed は、もう一方の座標（axis が "u" なら v、"v" なら u）"""
    n = a_hi - a_lo + 1
    half = (n - 1) // 2

    def p(a, y, st):
        if axis == "u":
            put(a, fixed, y, st)
        else:
            put(fixed, a, y, st)

    for i in range(n):
        a = a_lo + i
        k = min(i, n - 1 - i)
        face = face_lo if i < n / 2 else face_hi
        if n % 2 == 1 and k == half:
            p(a, y_spring + k, slab("stone_brick", "top"))
        else:
            p(a, y_spring + k, stairs("stone_brick", face, "top"))
        for y in range(y_spring + k + 1, top + 1):
            p(a, y, gray_stone())


def arcade():
    """身廊と側廊の境：柱（u 17・25 と、内陣のアーチの u 9）と、幅7のアーチ。アーチの上は身廊の壁（+14 まで）"""
    for v in (RN, RS):
        for u in PIERS:
            put(u, v, FL, block("polished_andesite"))
            for y in range(FT, 8):
                put(u, v, y, block("stone_bricks"))
            put(u, v, 8, block("chiseled_stone_bricks"))
            for y in range(9, NAVE_TOP + 1):
                put(u, v, y, gray_stone())
        for (lo, hi) in (BAYS[1], BAYS[2], BAYS[3]):
            arch_row(v, lo, hi, 8, "u", "west", "east", NAVE_TOP)
    # 側廊を横切る石のアーチ（柱の列 u 17・25。片流れの屋根を受ける）
    for u in PIERS[1:]:
        for (lo, hi) in ((VN + 1, RN - 1), (RS + 1, VS - 1)):
            arch_row(u, lo, hi, 7, "v", "north", "south", 0)
            for v in range(lo, hi + 1):
                k = min(v - lo, hi - v)
                for y in range(7 + k + 1, lean_top(v)):
                    put(u, v, y, gray_stone())
    # 内陣のアーチ（u 9、v 18..26、幅9）と、その上の壁（屋根の下まで）
    arch_row(9, RN + 1, RS - 1, 9, "v", "north", "south", NAVE_TOP)


# ---------------------------------------------------------------- 窓・入口
GLASS = {"b": "blue_stained_glass_pane", "l": "light_blue_stained_glass_pane", "y": "yellow_stained_glass_pane",
         "w": "white_stained_glass_pane", "p": "purple_stained_glass_pane", "c": "cyan_stained_glass_pane"}


def glass(c):
    return block(GLASS[c], north=False, south=False, east=False, west=False, waterlogged=False)


# 側廊の窓の色（下から上へ。左の列・真ん中の列・右の列）
LANCET = {1: ["lblb", "bylyw", "lblb"], 2: ["lcbc", "bylyw", "lcbc"], 3: ["lplp", "bylyp", "lplp"]}


def lancet(face_v, uc, pattern):
    """側廊の外壁（v）の、幅3の色ガラスの窓（y 4..7、真ん中の列は y 8 まで）。上の角は逆さの階段で丸める。外に窓台"""
    for i, col in enumerate(pattern):
        u = uc - 1 + i
        for k, c in enumerate(col):
            put(u, face_v, 4 + k, glass(c))
    put(uc - 1, face_v, 8, stairs("stone_brick", "west", "top"))
    put(uc + 1, face_v, 8, stairs("stone_brick", "east", "top"))
    out = -1 if face_v == VN else 1
    for u in range(uc - 1, uc + 2):
        put(u, face_v + out, 3, stairs("quartz", "south" if face_v == VN else "north", "top"))


def rose(face_u, vc, yc, r=2):
    """丸い色ガラスの窓（半径 r）。真ん中は黄、内の輪は青、外の輪は水色、縁は白。まわりに石の輪"""
    for dv in range(-r - 1, r + 2):
        for dy in range(-r - 1, r + 2):
            d = math.hypot(dv, dy)
            if d <= r + 0.5:
                c = "y" if d < 0.5 else "b" if d < 1.5 else "l" if d < 2.2 else "w"
                put(face_u, vc + dv, yc + dy, glass(c))
            elif d <= r + 1.3:
                put(face_u, vc + dv, yc + dy,
                    block("chiseled_quartz_block") if (dv == 0 or dy == 0) else block("polished_diorite"))


def knot(face_u, vc, yc):
    """「結び目の紋」：石英のひし形の輪（結び目）と、下へ垂れる2本の端。輪の真ん中に紫水晶（大結晶のかけら）"""
    for (dv, dy) in ((0, 0), (-1, 1), (1, 1), (0, 2), (-1, -1), (1, -1)):
        put(face_u, vc + dv, yc + dy, block("quartz_bricks"))
    put(face_u, vc, yc + 1, block("amethyst_block"))


def windows():
    # 側廊の窓（北は、鐘楼の後ろになる1つ目の間をのぞく）
    for bay, uc in ((1, 29), (2, 21), (3, 13)):
        if bay != 1:
            lancet(VN, uc, LANCET[bay])
        lancet(VS, uc, LANCET[bay])
    # 控室と書庫の小さな窓（白い色ガラス、2段）
    for (v, u) in ((VN, 3), (VS, 4), (VS, 6)):
        for y in (4, 5):
            put(u, v, y, glass("w"))
    # 正面の側廊の小窓
    for v in (15, 29):
        for y in (4, 5, 6):
            put(FACADE, v, y, glass("l" if y != 5 else "w"))
    # 丸い窓：正面（入口の屋根の上）と後ろ（祭壇の上）。縁は古代の白い石
    rose(FACADE, AXIS, 14)
    rose(APSE, AXIS, 11)
    # 正面の破風の「結び目の紋」（石英の浮き彫り。教会の印）
    knot(FACADE, AXIS, 22)


def portal():
    """正面の入口（幅3、高さ5の丸いアーチ）。扉は付けない（呼び戻しの儀で戻る人のため、昼も夜も開いている【補完】）"""
    for v in range(AXIS - 1, AXIS + 2):
        for y in range(FT, FT + 4):
            put(FACADE, v, y, AIR)
        put(FACADE, v, FL, block("polished_diorite"))
    put(FACADE, AXIS - 1, FT + 4, stairs("stone_brick", "north", "top"))
    put(FACADE, AXIS + 1, FT + 4, stairs("stone_brick", "south", "top"))
    put(FACADE, AXIS, FT + 4, AIR)
    put(FACADE, AXIS, FT + 5, block("chiseled_stone_bricks"))
    # 入口の枠：両わきの石英の柱と、その上の模様入りの石英
    for v in (AXIS - 2, AXIS + 2):
        for y in range(FT, FT + 4):
            put(FACADE, v, y, block("quartz_pillar", axis="y"))
        put(FACADE, v, FT + 4, block("chiseled_quartz_block"))
    # 入口の前の小さな切妻（古代の白い石の柱2本と、石英の破風）。破風の真ん中に名の板
    for v in (AXIS - 2, AXIS + 2):
        put(FACADE + 1, v, FL, block("polished_diorite"))
        for y in range(FT, FT + 4):
            put(FACADE + 1, v, y, block("quartz_pillar", axis="y"))
        put(FACADE + 1, v, FT + 4, block("chiseled_quartz_block"))
    y8 = FT + 5
    put(FACADE + 1, AXIS - 3, y8, stairs("quartz", "south"))
    put(FACADE + 1, AXIS + 3, y8, stairs("quartz", "north"))
    for v in range(AXIS - 2, AXIS + 3):
        put(FACADE + 1, v, y8, block("chiseled_quartz_block") if abs(v - AXIS) == 2 else block("quartz_bricks"))
    put(FACADE + 1, AXIS - 2, y8 + 1, stairs("quartz", "south"))
    put(FACADE + 1, AXIS + 2, y8 + 1, stairs("quartz", "north"))
    for v in range(AXIS - 1, AXIS + 2):
        put(FACADE + 1, v, y8 + 1, block("quartz_bricks"))
    put(FACADE + 1, AXIS - 1, y8 + 2, stairs("quartz", "south"))
    put(FACADE + 1, AXIS + 1, y8 + 2, stairs("quartz", "north"))
    put(FACADE + 1, AXIS, y8 + 2, block("chiseled_quartz_block"))
    # 小さな切妻の下（アーチの前）に吊るす灯りと、入口の両わきの壁のランタン（都市のルール⑦：入口の横）
    put(FACADE + 1, AXIS, FT + 4, lantern(True))
    for v in (AXIS - 4, AXIS + 4):
        wall_lamp(FACADE, v, 7, "east", reach=2, chain=1)
    wall_sign(FACADE + 2, AXIS, y8, "east", ["", "結びの教会", "", ""])


# ---------------------------------------------------------------- 控え壁
def buttress(u, v, out, top):
    """壁の外の控え壁（1×1）。top の段の上に、壁の方を背にした階段の笠"""
    for y in range(0, top + 1):
        put(u, v, y, wall_block(y))
    put(u, v, top + 1, stairs("stone_brick", OPP[out]))


def buttresses():
    for u in PIERS:
        if not (TU0 <= u <= TU1):
            buttress(u, VN - 1, "north", 7)
        buttress(u, VS + 1, "south", 7)
    # 正面の控え壁（身廊と側廊の境の前、入口の段の両わき）と、角
    for v in (RN, RS):
        for y in range(0, 14):
            put(FACADE + 1, v, y, wall_block(y))
        put(FACADE + 1, v, 14, stairs("stone_brick", "west"))
    buttress(FACADE + 1, VS, "east", 6)
    buttress(APSE - 1, VN, "west", 6)
    buttress(APSE - 1, VS, "west", 6)
    buttress(APSE, VS + 1, "south", 6)


# ---------------------------------------------------------------- 屋根
def roofs():
    roof = Roof(w, pal, x0=APSE + 1, x1=FACADE - 1, z0=RN, z1=RS, y0=ROOF_Y0, ridge="x", pitch=2, side_overhang=1,
                end_overhang=0, ends=(), thickness=3, kick=False)
    roof.build()
    # 屋根の裏（中から見える下の2段）はトウヒの板：礼拝堂の天井は、木の小屋組みと板の尖った形（参考 church_02）
    for u in range(APSE + 1, FACADE):
        for v in range(RN, RS + 1):
            col = min(v - 16, 28 - v)
            if col == 6:
                put(u, v, 24, block("spruce_planks"))
                continue
            top = ROOF_Y0 + 2 * col + 1
            for y in (top - 3, top - 2):
                if y >= ROOF_Y0 and name_at(u, v, y) == "waxed_oxidized_copper":
                    put(u, v, y, block("spruce_planks"))
    # 側廊の片流れ（緑青の銅の階段。下にトウヒの板。v 12・32 は軒の出）
    for u in range(APSE + 1, FACADE):
        for v in range(12, 17):
            if TU0 <= u <= TU1 and v <= TV1:
                continue
            y = lean_top(v)
            put(u, v, y, stairs(COPPER, "south"))
            if v > VN:
                put(u, v, y - 1, block("spruce_planks"))
        for v in range(28, 33):
            y = lean_top(v)
            put(u, v, y, stairs(COPPER, "north"))
            if v < VS:
                put(u, v, y - 1, block("spruce_planks"))
    # 片流れの軒の下の石の持ち送り（側廊の外壁の上、控え壁と鐘楼と渡り廊下の所をのぞく）
    for u in range(APSE + 1, FACADE):
        if u in PIERS:
            continue
        if not (TU0 <= u <= TU1):
            put(u, VN - 1, AISLE_TOP - 1, stairs("stone_brick", "south", "top"))
        put(u, VS + 1, AISLE_TOP - 1, stairs("stone_brick", "north", "top"))
    # 内陣のアーチの上の壁を、屋根の下まで
    for v in range(RN, RS + 1):
        for y in range(NAVE_TOP + 1, 30):
            if roof.inside(9, y, v) and get(9, v, y) is None:
                put(9, v, y, gray_stone())
    return roof


def trusses():
    """身廊の小屋組み：陸梁（+15）、真束、陸梁の端の持ち送り、真束の両わきの方杖。柱の列と間の真ん中ごと"""
    for u in (29, 25, 21, 17, 13, 5):
        for v in range(RN + 1, RS):
            put(u, v, ROOF_Y0, log("dark_oak", "z"))
        for y in range(ROOF_Y0 + 1, 24):
            if get(u, AXIS, y) is None:
                put(u, AXIS, y, log("dark_oak", "y"))
        put(u, RN + 1, NAVE_TOP, stairs("dark_oak", "north", "top"))
        put(u, RS - 1, NAVE_TOP, stairs("dark_oak", "south", "top"))
        put(u, AXIS - 1, ROOF_Y0 + 1, stairs("dark_oak", "south", "top"))
        put(u, AXIS + 1, ROOF_Y0 + 1, stairs("dark_oak", "north", "top"))


# ---------------------------------------------------------------- 鐘楼
SW_CORNER = {(26, 10), (26, 11), (27, 10), (27, 11)}   # 入口の床（らせんの下を空ける）


def tower():
    for u in range(TU0, TU1 + 1):
        for v in range(TV0, TV1 + 1):
            edge = u in (TU0, TU1) or v in (TV0, TV1)
            corner = u in (TU0, TU1) and v in (TV0, TV1)
            for y in range(0, TOWER_TOP + 1):
                if edge:
                    if y <= 11:
                        st = block("quartz_bricks") if corner else white_stone()
                    elif y == 12:
                        st = block("chiseled_quartz_block") if corner else block("polished_diorite")
                    else:
                        st = block("stone_bricks") if corner else gray_stone()
                    put(u, v, y, st)
                elif y <= 1:
                    put(u, v, y, white_stone())
            if not edge:
                put(u, v, FL, block("polished_andesite"))
    # 帯：古代の層の上（+12）と、鐘の間の床（+21）に、外へ出た段（南は礼拝堂の側廊の屋根）
    for y in (12, BELL_FLOOR):
        for u in range(TU0 - 1, TU1 + 2):
            for v in range(TV0 - 1, TV1 + 1):
                ring = u in (TU0 - 1, TU1 + 1) or v == TV0 - 1
                if not ring or (u in (TU0 - 1, TU1 + 1) and v == TV0 - 1):
                    continue
                face = "west" if u == TU0 - 1 else "east" if u == TU1 + 1 else "north"
                put(u, v, y, stairs("stone_brick", OPP[face], "top"))
    # 芯（らせん階段の真ん中の柱）
    fill(28, 30, 7, 9, FL, BELL_FLOOR - 1, block("stone_bricks"))
    spiral()
    # 鐘の間の床（階段の口をあける）と、口のまわりの柵
    for u in range(TU0 + 1, TU1):
        for v in range(TV0 + 1, TV1):
            if u in (31, 32) and 5 <= v <= 9 or (u, v) in ((30, 5), (30, 6)):
                continue
            put(u, v, BELL_FLOOR, block("spruce_planks"))
    for v in range(7, 10):
        put(30, v, BELL_FLOOR + 1, fence("dark_oak"))
    for u in (31, 32):
        put(u, 10, BELL_FLOOR + 1, fence("dark_oak"))
    # 鐘の間の開口（各面に幅3、+22..+25。上の角は丸め、下に腰の柵）
    for face in ("north", "south", "east", "west"):
        for k in range(3):
            if face in ("north", "south"):
                u, v = 28 + k, (TV0 if face == "north" else TV1)
            else:
                u, v = (TU0 if face == "west" else TU1), 7 + k
            for y in range(BELL_FLOOR + 1, BELL_FLOOR + 5):
                put(u, v, y, AIR)
            put(u, v, BELL_FLOOR + 1, fence("dark_oak"))
        if face in ("north", "south"):
            v = TV0 if face == "north" else TV1
            put(28, v, BELL_FLOOR + 4, stairs("stone_brick", "west", "top"))
            put(30, v, BELL_FLOOR + 4, stairs("stone_brick", "east", "top"))
        else:
            u = TU0 if face == "west" else TU1
            put(u, 7, BELL_FLOOR + 4, stairs("stone_brick", "north", "top"))
            put(u, 9, BELL_FLOOR + 4, stairs("stone_brick", "south", "top"))
    # 鐘：梁から吊るす（教会の品＝鐘楼の鐘そのもの。都市のルール⑦）と、梁から吊るす灯り
    for u in range(TU0 + 1, TU1):
        put(u, 8, BELL_FLOOR + 5, log("dark_oak", "x"))
    put(29, 8, BELL_FLOOR + 4, block("bell", attachment="ceiling", facing="north", powered=False))
    put(27, 8, BELL_FLOOR + 4, lantern(True))
    put(31, 8, BELL_FLOOR + 4, lantern(True))
    # 細い窓（らせん階段の頭の高さ）
    for (u, v, y0, n) in ((TU1, 8, 9, 2), (TU1, 8, 20, 1), (29, TV0, 13, 2), (TU0, 8, 15, 2), (29, TV1, 17, 2)):
        for y in range(y0, y0 + n):
            put(u, v, y, pane())
    # 入口：北の側廊（1つ目の間）から、側廊の壁（v 13）の口を通り、鐘楼の南の壁（v 12）の扉へ
    for y in (FT, FT + 1):
        put(27, VN, y, AIR)
    put(27, VN, FT + 2, slab("stone_brick", "top"))
    one_door(27, TV1, FT, "south")
    # 尖り屋根（緑青の銅。軒 +28、先 +39）と光の棒（+40）
    spire = Pyramid(w, pal, cx=29, cz=8, r=5, y0=TOWER_TOP, pitch=2, thickness=2, mat=COPPER, mat_block=COPPER)
    top = spire.build()
    put(29, 8, top, block("end_rod", facing="up"))
    # 尖り屋根の根もとを囲む4つの小さな尖り（軒の角。参考 church_01）
    for (u, v) in ((TU0 - 1, TV0 - 1), (TU1 + 1, TV0 - 1), (TU0 - 1, TV1 + 1), (TU1 + 1, TV1 + 1)):
        put(u, v, TOWER_TOP + 1, block("chiseled_stone_bricks"))
        put(u, v, TOWER_TOP + 2, wall("stone_brick"))
        put(u, v, TOWER_TOP + 3, wall("stone_brick"))
        put(u, v, TOWER_TOP + 4, block("end_rod", facing="up"))
    return spire


def spiral():
    """鐘楼のらせん階段：芯のまわりの幅2の輪。直線の3段ずつ上り、角は踊り場。南西の角（入口）から時計回り（東→北→西→南）。
    1回りめは下を石で埋め（入口の南西の角だけ空ける）、2回りめは下に逆さの階段（裏の面）を付ける。いちばん上の段（+21）から
    鐘の間の床へ出る。芯のくぼみに灯り"""
    seq = []
    for u in (28, 29, 30):
        seq.append(("run", [(u, 10), (u, 11)], "east"))
    seq.append(("land", [(31, 10), (31, 11), (32, 10), (32, 11)], None))
    for v in (9, 8, 7):
        seq.append(("run", [(31, v), (32, v)], "north"))
    seq.append(("land", [(31, 5), (31, 6), (32, 5), (32, 6)], None))
    for u in (30, 29, 28):
        seq.append(("run", [(u, 5), (u, 6)], "west"))
    seq.append(("land", [(26, 5), (26, 6), (27, 5), (27, 6)], None))
    for v in (7, 8, 9):
        seq.append(("run", [(26, v), (27, v)], "south"))
    seq.append(("land", [(26, 10), (26, 11), (27, 10), (27, 11)], None))

    def under(u, v, y, facing):
        if y <= 14 and (u, v) not in SW_CORNER:
            for yy in range(FL + 1, y):
                if get(u, v, yy) is None:
                    put(u, v, yy, block("stone_bricks"))
        elif facing:
            put(u, v, y - 1, stairs("stone_brick", OPP[facing], "top"))
        else:
            put(u, v, y - 1, slab("stone_brick", "top"))

    y = FL
    i = 0
    while True:
        kind_, cells, facing = seq[i % len(seq)]
        if kind_ == "run":
            y += 1
            for (u, v) in cells:
                put(u, v, y, stairs("stone_brick", facing))
                under(u, v, y, facing)
            if y == BELL_FLOOR:
                break
        elif i > 0:
            for (u, v) in cells:
                put(u, v, y, block("stone_bricks"))
                under(u, v, y, None)
        i += 1
    # 芯のくぼみの灯り（段の上の頭の高さ）
    for (u, v, y) in ((29, 9, 6), (30, 8, 9), (29, 7, 12), (28, 8, 15), (29, 9, 18), (30, 8, 20)):
        put(u, v, y, lantern(False))
    # 入口の上（1回りめの南西の踊り場の裏）から吊るす灯り
    hang(26, 11, 12, chain=2)


# ---------------------------------------------------------------- 療養所の棟と渡り廊下
def sanatorium():
    """1階は灰色の石、2階は木組みと漆喰。1階：治療の間（寝台2つ・薬の棚・待つ長椅子）と西の壁ぞいの階段。
    2階：病室（寝台2つ）と、北の小さな部屋（マルグリットの部屋。花）。屋根裏は入らない（灯りだけ）"""
    for u in range(SU0, SU1 + 1):
        for v in range(SV0, SV1 + 1):
            edge = u in (SU0, SU1) or v in (SV0, SV1)
            corner = u in (SU0, SU1) and v in (SV0, SV1)
            for y in (0, 1):
                put(u, v, y, white_stone() if edge else block("stone_bricks"))
            if not edge:
                put(u, v, S1, block("spruce_planks"))
                put(u, v, S2, block("spruce_planks"))
                put(u, v, SC, block("spruce_planks"))
                continue
            for y in range(S1, S2):
                put(u, v, y, gray_stone())
            post = corner or (u in (SU0, SU1) and v == 6) or (v in (SV0, SV1) and u in (4, 5))
            for y in range(S2, SC + 1):
                if y in (S2, SC):
                    put(u, v, y, log("spruce", "z" if u in (SU0, SU1) else "x", stripped=True))
                elif post:
                    put(u, v, y, log("spruce", "y", stripped=True))
                else:
                    put(u, v, y, block("calcite"))
    # 階段（西の壁ぞい、幅2。北の v 4 から南へ上る。いちばん上の段 v 8 が2階の床の高さ。上の床は v 5..7 をあける）
    for k, v in enumerate((4, 5, 6, 7, 8)):
        y = S1 + 1 + k
        for u in (2, 3):
            put(u, v, y, stairs("spruce", "south"))
            if v <= 5:
                for yy in range(S1 + 1, y):
                    put(u, v, yy, block("spruce_planks"))
    for u in (2, 3):
        for v in (5, 6, 7):
            put(u, v, S2, AIR)
    # 1階：治療の間
    bed(6, 3, S1 + 1, "north", "white")
    bed(7, 3, S1 + 1, "north", "white")
    barrel(4, 3, S1 + 1)
    barrel(5, 3, S1 + 1)
    put(4, 3, S1 + 2, block("potted_fern"))
    candle(5, 3, S1 + 2, 2)
    for v in (7, 8):
        put(7, v, S1 + 1, stairs("spruce", "east"))
    for (u, v) in ((2, 9), (3, 9), (2, 8)):
        barrel(u, v, S1 + 1)
    barrel(2, 9, S1 + 2)
    for u in range(4, 8):
        for v in range(5, 7):
            put(u, v, S1 + 1, block("white_carpet") if (u + v) % 2 else block("light_blue_carpet"))
    hang(5, 5, S2 - 1, chain=0)
    hang(5, 8, S2 - 1, chain=0)
    # 2階：階段の口の柵、北の部屋との仕切り（漆喰、扉 u 5）
    for v in (6, 7):
        put(4, v, S2 + 1, fence("spruce"))
    for u in range(2, 8):
        for y in range(S2 + 1, SC):
            put(u, 5, y, block("calcite"))
    put(4, 5, SC - 1, log("spruce", "x", stripped=True))
    one_door(5, 5, S2 + 1, "south")
    # 病室（寝台2つ。足を向かい合わせ）
    bed(7, 6, S2 + 1, "north", "light_blue")
    bed(7, 9, S2 + 1, "south", "light_blue")
    barrel(6, 6, S2 + 1)
    put(6, 6, S2 + 2, block("potted_azure_bluet"))
    barrel(6, 9, S2 + 1)
    candle(6, 9, S2 + 2, 1)
    hang(5, 7, SC - 1, chain=0)
    # マルグリットの部屋（u 2..7、v 3..4）：寝台、机と腰かけ、花の鉢
    bed(2, 3, S2 + 1, "west", "pink")
    put(6, 3, S2 + 1, block("spruce_planks"))
    put(6, 3, S2 + 2, block("potted_red_tulip"))
    put(6, 4, S2 + 1, stairs("spruce", "south"))
    put(7, 3, S2 + 1, block("spruce_planks"))
    put(7, 3, S2 + 2, block("potted_oxeye_daisy"))
    put(4, 3, S2 + 1, block("spruce_planks"))
    put(4, 3, S2 + 2, block("potted_allium"))
    put(4, 4, S2 + 1, block("pink_carpet"))
    put(3, 4, S2 + 1, block("pink_carpet"))
    hang(4, 4, SC - 1, chain=0)
    # 屋根裏（入らない）の灯り
    for (u, v) in ((3, 4), (6, 8)):
        put(u, v, SC + 1, lantern(False))
    # 屋根（南北に棟、2：1の緑青の銅。妻は北と南。木組みの妻）
    roof = Roof(w, pal, x0=SU0, x1=SU1, z0=SV0, z1=SV1, y0=SC + 1, ridge="z", pitch=2, side_overhang=1,
                end_overhang=1, ends=("start", "end"), thickness=2, kick=False)
    roof.build(skip=lambda x, y, z: SU0 <= x <= SU1 and SV0 <= z <= SV1 and y <= SC)
    for at in (SV0, SV1):
        gable_wall(w, pal, roof, at, posts=(4, 5))
    # 窓（1階 +4..+5、2階 +9..+10）と、2階の雨戸
    win1 = ((SU1, 3), (SU1, 8), (3, SV0), (6, SV0), (SU0, 8))
    win2 = ((SU1, 3), (SU1, 4), (SU1, 8), (3, SV0), (6, SV0), (3, SV1), (6, SV1), (SU0, 4), (SU0, 8))
    for (u, v) in win1:
        for y in (4, 5):
            put(u, v, y, pane())
    for (u, v) in win2:
        for y in (9, 10):
            put(u, v, y, pane())
    for (u, v, f) in ((9, 2, "east"), (9, 5, "east"), (9, 7, "east"), (9, 9, "east"), (2, 1, "north"), (4, 1, "north"),
                      (5, 1, "north"), (7, 1, "north"), (2, 11, "south"), (4, 11, "south"), (5, 11, "south"),
                      (7, 11, "south"), (0, 3, "west"),
                      (0, 5, "west"), (0, 7, "west"), (0, 9, "west")):
        for y in (9, 10):
            shutter(u, v, y, f)
    # 花の箱（マルグリットの部屋の東の窓の下）
    for v in (3, 4):
        put(SU1 + 1, v, 8, trapdoor("spruce", "east", "top", False))
    put(SU1 + 1, 3, 9, block("potted_red_tulip"))
    put(SU1 + 1, 4, 9, block("potted_pink_tulip"))
    # 入口（東の面、v 5）と外の2段、扉の上の灯り
    one_door(SU1, 5, FT, "east")
    put(SU1 + 1, 5, 1, block("stone_bricks"))
    put(SU1 + 1, 5, 2, stairs("stone_brick", "west"))
    put(SU1 + 2, 5, 1, stairs("stone_brick", "west"))
    for v in (4, 6):
        put(SU1 + 1, v, 1, block("stone_bricks"))
        put(SU1 + 1, v, 2, slab("stone_brick", "bottom"))
    wall_lamp(SU1, 6, 6, "east", reach=2, chain=1)
    # 渡り廊下（療養所の南の扉と、控室の北の扉をつなぐ。中の幅2：u 5..6、v 11..12）
    for v in (11, 12):
        for u in range(4, 8):
            put(u, v, 0, white_stone())
            put(u, v, 1, white_stone())
            put(u, v, 6, slab(COPPER, "bottom"))
        for u in (5, 6):
            put(u, v, FL, block("spruce_planks"))
        for u in (4, 7):
            for y in range(FL, 6):
                put(u, v, y, pane() if y == 4 else gray_stone())
    put(5, 11, 5, lantern(True))
    door_pair(5, SV1, FT, "south", "u")
    door_pair(5, VN, FT, "south", "u")


# ---------------------------------------------------------------- 中
def interior():
    # 床：身廊は白い石の市松、柱の列の間は磨いた安山岩、側廊は石レンガと磨いた安山岩
    for u in range(BAYS[3][0], FACADE):
        for v in range(RN + 1, RS):
            put(u, v, FL, block("polished_diorite") if (u + v) % 2 else block("calcite"))
        for v in (RN, RS):
            if u not in PIERS:
                put(u, v, FL, block("polished_andesite"))
        for v in list(range(VN + 1, RN)) + list(range(RS + 1, VS)):
            put(u, v, FL, block("stone_bricks") if (u // 2 + v) % 3 else block("polished_andesite"))
    # 呼び戻しの円（3つ目の間の真ん中。呼び戻しの儀で戻った人が立つ所＝復活の場所）
    cu, cv = CIRCLE
    for u in range(cu - 3, cu + 4):
        for v in range(cv - 3, cv + 4):
            d = math.hypot(u - cu, v - cv)
            if d <= 0.5:
                put(u, v, FL, block("chiseled_quartz_block"))
            elif d <= 1.6:
                put(u, v, FL, block("quartz_bricks"))
            elif d <= 2.6:
                put(u, v, FL, block("smooth_quartz"))
            elif d <= 3.2:
                put(u, v, FL, block("polished_diorite"))
    # 真ん中の通路の敷物（入口から呼び戻しの円の手前まで。水色、縁は白）
    for u in range(17, FACADE):
        for v in range(AXIS - 1, AXIS + 2):
            put(u, v, FT, block("light_blue_carpet") if v == AXIS else block("white_carpet"))
    # 長椅子（1つ目・2つ目の間。祭壇を向く）
    for u in (31, 29, 27, 23, 21, 19):
        for v in list(range(RN + 1, AXIS - 1)) + list(range(AXIS + 2, RS)):
            put(u, v, FT, stairs("spruce", "east"))
    # 内陣（u 2..8、床 +3）：段、床、祭壇、呼び戻しの結晶、ろうそくの台
    for u in range(BAYS[4][0], BAYS[4][1] + 1):
        for v in range(RN + 1, RS):
            put(u, v, CFL, block("quartz_bricks") if (u + v) % 2 == 0 else block("polished_diorite"))
    for v in range(RN + 1, RS):
        put(9, v, CFL, stairs("quartz", "west"))
    put(3, AXIS, CFT, block("chiseled_quartz_block"))
    put(3, AXIS - 1, CFT, stairs("quartz", "south", "top"))
    put(3, AXIS + 1, CFT, stairs("quartz", "north", "top"))
    put(3, AXIS, CFT + 1, block("amethyst_cluster", facing="up", waterlogged=False))
    candle(3, AXIS - 1, CFT + 1, 3)
    candle(3, AXIS + 1, CFT + 1, 3)
    # 祭壇の後ろの壁の「結び目の紋」（石英の浮き彫り。丸い窓の下）
    knot(APSE, AXIS, CFT + 1)
    for (u, v) in ((7, RN + 1), (7, RS - 1), (2, RN + 1), (2, RS - 1)):
        put(u, v, CFT, block("polished_diorite"))
        candle(u, v, CFT + 1, 4)
    # 控室（u 2..8、v 14..16）：内陣・北の側廊の端・渡り廊下への扉、衣装の棚、着替えの台、儀の手順の書見台、鈴の額
    for u in range(2, 9):
        for v in range(14, 17):
            put(u, v, FL, block("spruce_planks"))
    one_door(9, 15, FT, "east")
    put(5, RN, CFL, block("quartz_bricks"))
    one_door(5, RN, CFT, "south")
    put(5, RN - 1, FT, stairs("spruce", "south"))
    for u in (7, 8):
        barrel(u, 14, FT, "south")
        barrel(u, 14, FT + 1, "south")
    for v in (15, 16):
        put(2, v, FT, stairs("spruce", "west", "top"))
    candle(2, 15, FT + 1, 2)
    frame(2, 16, FT + 2, "east", "bell")
    put(6, 16, FT, block("lectern", facing="north", has_book=True, powered=False),
        lectern_nbt("呼び戻しの儀", "セレナ・アステル",
                    ["呼び戻しの儀\n\n鈴を三度。\n結晶に手を置き、\n倒れた人の糸を\nたどる。",
                     "糸が見えたら、\n名を呼ぶ。\n大結晶の光が、\nその人を連れて\n帰ってくる。"]))
    hang(5, 15, 10, chain=2)
    # 書庫（u 2..8、v 28..30）：本棚、読む机と腰かけ、記憶守の古い書の書見台、古い地図の額
    for u in range(2, 9):
        for v in range(28, 31):
            put(u, v, FL, block("spruce_planks"))
    one_door(9, 29, FT, "east")
    put(5, RS, CFL, block("quartz_bricks"))
    one_door(5, RS, CFT, "north")
    put(5, RS + 1, FT, stairs("spruce", "north"))
    for u in range(2, 9):
        if u in (4, 6):
            continue
        for y in (FT, FT + 1, FT + 2):
            put(u, 30, y, block("bookshelf"))
    for y in (FT, FT + 1):
        put(2, 28, y, block("bookshelf"))
        put(2, 29, y, block("bookshelf"))
    put(3, 29, FT, block("spruce_planks"))
    candle(3, 29, FT + 1, 1)
    put(4, 29, FT, stairs("spruce", "east"))
    put(8, 28, FT, block("lectern", facing="west", has_book=True, powered=False),
        lectern_nbt("記憶守の古い書（写し）", "結びの教会",
                    ["記憶守の古い書\n（写し）\n\n古い字が多く、\nまだ半分も\n読めていない。",
                     "（ここから先は、\n古い字で\n書かれていて\n読めない）"]))
    frame(3, 28, FT + 2, "south", "map")
    hang(5, 29, 10, chain=2)
    # 灯り：陸梁から吊るすランタン（身廊と内陣）、側廊の壁のランタン
    for u in (29, 25, 21, 17, 13, 5):
        for v in (RN + 2, RS - 2):
            hang(u, v, ROOF_Y0 - 1, chain=4)
    for u in (31, 23, 15):
        wall_lamp(u, VN, 7, "south", reach=2, chain=1)
        wall_lamp(u, VS, 7, "north", reach=2, chain=1)
    # これまでの記録（物語の振り返り）の書見台：入口の近くの南の側廊（ティナが案内する）
    put(31, 30, FT, block("lectern", facing="north", has_book=True, powered=False),
        lectern_nbt("これまでの記録", "見習いティナ",
                    ["これまでの記録\n\nコネリアに来た\n冒険者たちの\n歩みを、ここに\n書きとめる。",
                     "見習いティナ：\n「あ、あの……\n読みたい日を\n言ってください」"]))


# ---------------------------------------------------------------- まわり
def surroundings():
    # 正面の2段（磨いた閃緑岩の階段。入口の前、控え壁の間）
    for v in range(RN + 1, RS):
        put(FACADE + 1, v, 1, block("polished_diorite"))
        if abs(v - AXIS) != 2:
            put(FACADE + 1, v, 2, stairs("polished_diorite", "west"))
        put(FACADE + 2, v, 1, stairs("polished_diorite", "west"))
    # 古代の結晶灯（広場の左右）
    for v in (16, 28):
        crystal_lamp(w, 41, 1, v)
    # 旗（黄の地に白い菱と白い縁）：入口の両わきの控え壁に
    for v in (RN, RS):
        put(FACADE + 2, v, 9, block("yellow_wall_banner", facing="east"), banner_nbt([("mr", "white"), ("bo", "white")]))
    # 広場の腰かけ（北と南の縁。広場の真ん中を向く）
    for (v, f) in ((11, "north"), (33, "south")):
        for u in (38, 39, 43, 44):
            put(u, v, 1, stairs("stone_brick", f))
    # 正面の植え込み（側廊の前）
    for v in (14, 15, 16, 28, 29, 30):
        put(FACADE + 1, v, 1, block("azalea_leaves", persistent=True, distance=1, waterlogged=False))
    # 北の庭：療養所の薬草の花壇（低い木の縁）、木、長椅子、ランタン灯
    for u in range(19, 23):
        for v in range(7, 10):
            put(u, v, 0, block("rooted_dirt") if (u + v) % 3 == 0 else grass())
            put(u, v, 1, block(("allium", "azure_bluet", "oxeye_daisy", "cornflower")[(u * 3 + v) % 4]))
    for u in (18, 23):
        for v in (7, 8, 9):
            put(u, v, 1, block("spruce_trapdoor", facing="east" if u == 23 else "west", half="bottom", open=True,
                               powered=False, waterlogged=False))
    tree(15, 7)
    for u in (20, 21):
        put(u, 11, 1, stairs("spruce", "south"))
    lamp_post(w, 12, 1, 4, arms=("north",))
    lamp_post(w, 38, 1, 6, arms=("west",))
    # 南の小道のランタン灯
    lamp_post(w, 30, 1, 37, arms=("north",))
    lamp_post(w, 19, 1, 34, arms=("east",))
    # まわりの草地のランタン灯（教会の裏・療養所の西・北の小道の東・広場の北と南の角。暗い所を作らない）
    for (u, v, arm) in ((0, 19, "north"), (0, 5, "south"), (5, 35, "north"), (26, 0, "south"), (42, 2, "south"),
                        (42, 36, "north")):
        lamp_post(w, u, 1, v, arms=(arm,))


def tree(u, v, h=5):
    for y in range(1, h + 1):
        put(u, v, y, log("oak", "y"))
    for du in range(-3, 4):
        for dv in range(-3, 4):
            for y in (h - 1, h, h + 1):
                r = abs(du) + abs(dv)
                if y == h + 1 and r > 1 or y < h + 1 and r > 3:
                    continue
                if du == 0 and dv == 0 and y <= h:
                    continue
                if get(u + du, v + dv, y) is None:
                    put(u + du, v + dv, y, block("oak_leaves", persistent=True, distance=1, waterlogged=False))


# ---------------------------------------------------------------- 記憶の庭（別の構造物）
g = World(GW, GH, GD)


def gput(u, v, y, state, nbt=None):
    g.put(u, y, v, state, nbt)


def gget(u, v, y):
    return g.get(u, y, v)


G_PLAZA = (12, 8)                # 広場の真ん中
G_STELE = 13                     # 名を刻む石の v


def garden():
    """記憶の庭：低い石の塀（北と東に門）、北の門からの道と東の門からの道、丸い広場、名を刻む石、桜、腰かけ、灯り、白と青の花"""
    for u in range(GW):
        for v in range(GD):
            gput(u, v, 0, grass())
    # 道と広場
    for u in range(11, 14):
        for v in range(0, 6):
            gput(u, v, 0, path_paving())
    for u in range(15, GW):
        for v in range(7, 10):
            gput(u, v, 0, path_paving())
    cu, cv = G_PLAZA
    for u in range(cu - 4, cu + 5):
        for v in range(cv - 4, cv + 5):
            d = math.hypot(u - cu, v - cv)
            if d <= 0.5:
                gput(u, v, 0, block("chiseled_stone_bricks"))
            elif d <= 2.6:
                gput(u, v, 0, white_paving())
            elif d <= 3.6:
                gput(u, v, 0, block("mossy_stone_bricks") if rng.random() < 0.3 else block("stone_bricks"))
    # 塀（低い石の塀。角と門の柱は石レンガ）
    gates = {(u, 0) for u in range(11, 14)} | {(GW - 1, v) for v in range(7, 10)}
    posts = {(0, 0), (GW - 1, 0), (0, GD - 1), (GW - 1, GD - 1), (10, 0), (14, 0), (GW - 1, 6), (GW - 1, 10),
             (0, 8), (12, GD - 1), (6, 0), (18, 0), (6, GD - 1), (18, GD - 1), (0, 4), (0, 13)}
    for u in range(GW):
        for v in range(GD):
            if not (u in (0, GW - 1) or v in (0, GD - 1)) or (u, v) in gates:
                continue
            if (u, v) in posts:
                gput(u, v, 1, block("stone_bricks"))
                gate_post = (u, v) in ((10, 0), (14, 0), (GW - 1, 6), (GW - 1, 10))
                if gate_post:
                    gput(u, v, 2, block("stone_bricks"))
                    gput(u, v, 3, block("chiseled_stone_bricks"))
                else:
                    gput(u, v, 2, slab("stone_brick", "bottom"))
            else:
                gput(u, v, 1, wall("mossy_stone_brick" if rng.random() < 0.35 else "stone_brick"))
    # 門の梁と、名の吊り看板、梁から吊るす灯り
    for u in range(10, 15):
        gput(u, 0, 4, log("dark_oak", "x"))
    for v in range(6, 11):
        gput(GW - 1, v, 4, log("dark_oak", "z"))
    sign_lines = ["", "記憶の庭", "", ""]
    gput(12, 0, 3, block("spruce_hanging_sign", rotation=8, attached=False, waterlogged=False),
         sign_nbt(sign_lines, sign_lines, hanging=True))
    gput(GW - 1, 8, 3, block("spruce_hanging_sign", rotation=12, attached=False, waterlogged=False),
         sign_nbt(sign_lines, sign_lines, hanging=True))
    for (u, v) in ((11, 0), (13, 0), (GW - 1, 7), (GW - 1, 9)):
        gput(u, v, 3, lantern(True))
    # 名を刻む石（広場の南。北の門の方を向く）：真ん中の高い石（名の板）と、両わきの低い石（刻んだ名の面）。台に、ろうそく
    sv = G_STELE
    for u in range(9, 16):
        gput(u, sv, 1, block("polished_diorite"))
        gput(u, sv + 1, 1, block("stone_bricks"))
    for u in (10, 11, 13, 14):
        gput(u, sv, 2, block("chiseled_quartz_block") if u in (10, 14) else block("smooth_quartz"))
        gput(u, sv, 3, block("quartz_pillar", axis="y") if u in (10, 14) else block("smooth_quartz"))
        gput(u, sv, 4, slab("quartz", "bottom") if u in (10, 14) else block("polished_diorite"))
    gput(11, sv, 5, stairs("polished_diorite", "east"))
    gput(13, sv, 5, stairs("polished_diorite", "west"))
    for y in range(2, 6):
        gput(12, sv, y, block("polished_diorite") if y != 3 else block("calcite"))
    gput(12, sv, 6, block("chiseled_quartz_block"))
    gput(12, sv, 7, slab("quartz", "bottom"))
    gput(12, sv - 1, 3, block("spruce_wall_sign", facing="north", waterlogged=False),
         sign_nbt(["塔から帰らなかった", "人々の名を、", "ここに刻む", ""]))
    for u in (9, 15):
        gput(u, sv, 2, block("candle", candles=4, lit=True, waterlogged=False))
    # 石の前の白い花
    for u, f in zip(range(9, 16), ("azure_bluet", "lily_of_the_valley", "white_tulip", "lily_of_the_valley",
                                   "white_tulip", "lily_of_the_valley", "azure_bluet")):
        if gget(u, sv - 1, 1) is None:
            gput(u, sv - 1, 1, block(f))
    # 桜（石の後ろに1本と、北西・南東・南西に）
    for (u, v, h) in ((12, 16, 5), (4, 4, 4), (21, 13, 4), (3, 14, 3)):
        cherry(u, v, h)
    # 腰かけ（広場の西と東。石の方を向く）
    for v in (10, 11):
        gput(7, v, 1, stairs("spruce", "west"))
        gput(17, v, 1, stairs("spruce", "east"))
    # 灯り（再建のランタン灯）
    lamp_post(g, 8, 1, 3, arms=("east",))
    lamp_post(g, 3, 1, 9, arms=("east",))
    lamp_post(g, 21, 1, 12, arms=("north",))
    lamp_post(g, 16, 1, 15, arms=("west",))
    # 花（白と青：記憶の花）。塀の内に沿って
    flowers = ("lily_of_the_valley", "azure_bluet", "cornflower", "oxeye_daisy", "white_tulip")
    k = 0
    for u in range(1, GW - 1):
        for v in range(1, GD - 1):
            if not (u in (1, GW - 2) or v in (1, GD - 2)):
                continue
            if gget(u, v, 1) is not None or gget(u, v, 0)[0] != "grass_block":
                continue
            if (u * 7 + v * 3) % 5 < 2:
                gput(u, v, 1, block(flowers[k % len(flowers)]))
                k += 1


def cherry(u, v, h):
    """桜：幹と、平たく広がる花の冠（下に少し垂れる）、根もとの花びら"""
    for y in range(1, h + 1):
        gput(u, v, y, log("cherry", "y"))
    for (du, dv, f) in ((1, 0, "x"), (-1, 0, "x"), (0, 1, "z"), (0, -1, "z")):
        if (du + dv + u + v) % 2 == 0:
            gput(u + du, v + dv, h, log("cherry", f))
    for du in range(-3, 4):
        for dv in range(-3, 4):
            d = math.hypot(du, dv)
            for y, rmax in ((h, 1.5), (h + 1, 2.6), (h + 2, 2.0), (h + 3, 1.0)):
                if d > rmax or not (0 <= u + du < GW and 0 <= v + dv < GD):
                    continue
                if du == 0 and dv == 0 and y <= h:
                    continue
                if gget(u + du, v + dv, y) is None:
                    gput(u + du, v + dv, y, block("cherry_leaves", persistent=True, distance=1, waterlogged=False))
            if 2.0 < d <= 2.6 and (du * 5 + dv * 3) % 3 == 0 and 0 <= u + du < GW and 0 <= v + dv < GD:
                if gget(u + du, v + dv, h) is None:
                    gput(u + du, v + dv, h, block("cherry_leaves", persistent=True, distance=1, waterlogged=False))
    for du in range(-2, 3):
        for dv in range(-2, 3):
            if (du, dv) == (0, 0) or not (1 <= u + du < GW - 1 and 1 <= v + dv < GD - 1):
                continue
            if gget(u + du, v + dv, 1) is None and gget(u + du, v + dv, 0)[0] == "grass_block" and (du * 3 + dv) % 2 == 0:
                gput(u + du, v + dv, 1, block("pink_petals", facing=("north", "east", "south", "west")[(du + dv) % 4],
                                             flower_amount=1 + (du * dv) % 4))


# ---------------------------------------------------------------- 組み立て
def build():
    rng.seed(808)
    ground()
    church_shell()
    arcade()
    windows()
    portal()
    buttresses()
    roof = roofs()
    trusses()
    sp = tower()
    sanatorium()
    interior()
    surroundings()
    w.finalize()
    rng.seed(809)
    garden()
    g.finalize()
    return roof, sp


ROOF, SPIRE = build()

# ---------------------------------------------------------------- 見本の絵（教会の構造物の中の座標。y=0 が地面のブロック、足もとが 1。
# 記憶の庭は教会の構造物から u 9・v 38 に並べて描く）
VIEWS = {
    "front": ((60.0, 5.0, 22.5), (28.0, 14.0, 20.5)),         # 祈りの道の突き当たりから、正面と鐘楼
    "tower": ((48.0, 8.0, -7.0), (29.0, 20.0, 8.0)),           # 北東から鐘楼と北の小道
    "south": ((44.0, 6.0, 44.0), (15.0, 9.0, 28.0)),           # 南東（広場の南の角の先）から、礼拝堂の南の面と記憶の庭
    "aerial": ((70.0, 50.0, 70.0), (20.0, 8.0, 24.0)),         # 上から（南東）
    "back": ((-14.0, 10.0, -6.0), (8.0, 10.0, 14.0)),          # 北西から療養所と内陣の後ろ
    "nave": ((32.5, 4.62, 22.5), (6.0, 6.5, 22.5)),            # 入口から身廊と祭壇
    "chancel": ((15.5, 4.62, 22.5), (2.0, 7.5, 22.5)),         # 呼び戻しの円の後ろから、内陣と丸い窓
    "aisle": ((24.5, 4.62, 24.5), (31.5, 4.0, 30.5)),          # 身廊から南の側廊へ（アーチ、これまでの記録の書見台、窓）
    "bell": ((26.5, 22.62, 10.5), (31.0, 24.5, 6.0)),          # 鐘の間
    "sanatorium": ((5.5, 8.62, 9.5), (5.5, 8.4, 3.0)),         # 療養所の2階（病室と、マルグリットの部屋の扉）
    "garden": ((21.5, 2.62, 39.5), (21.5, 3.0, 52.0)),         # 記憶の庭の北の門を入った所から、名を刻む石と桜
    "memorial": ((21.5, 2.62, 47.2), (21.5, 3.6, 51.5)),       # 名を刻む石の前
}


def combined_world():
    """教会と記憶の庭を並べた写し（見本の絵と、確かめのため）"""
    cw = World(W, max(H, GH), GARDEN_AT[2] + GD)
    for p, st in w.blocks.items():
        cw.blocks[p] = st
        if p in w.nbt:
            cw.nbt[p] = w.nbt[p]
    cw.entities = list(w.entities)
    ox, oy, oz = GARDEN_AT
    for (x, y, z), st in g.blocks.items():
        cw.blocks[(x + ox, y + oy, z + oz)] = st
        if (x, y, z) in g.nbt:
            cw.nbt[(x + ox, y + oy, z + oz)] = g.nbt[(x, y, z)]
    return cw


def cut_world(world, y_cut):
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut]
    return cw


def render_views(out_dir, width=960, height=540, fov=70, only=None):
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    cw = combined_world()
    sc = Scene(cw, ground_y=1)
    for name, (eye, target) in VIEWS.items():
        if only and name not in only:
            continue
        sc.render(eye=eye, target=target, width=width, height=height, fov=fov,
                  path=os.path.join(out_dir, "church_%s.png" % name))
        print("描きました:", name)
    for key, y_cut in (("cut1", 7), ("cut2", 12), ("cut3", 17)):
        if only and key not in only:
            continue
        Scene(cut_world(cw, y_cut), ground_y=1).render(eye=(23.0, 110.0, 28.0), target=(23.0, 0.0, 27.95),
                                                        width=width, height=width, fov=32,
                                                        path=os.path.join(out_dir, "church_%s.png" % key))
        print("描きました:", key)


CI_SHOTS = ["front", "tower", "south", "aerial", "back", "nave", "chancel", "aisle", "bell", "sanatorium", "garden",
            "memorial"]


def ci_views():
    """自動の確認の写真の目と見る所（教会を置いた所から）。CiScreenshots の MAP_VIEWS と同じ"""
    return [(shot, VIEWS[shot][0], VIEWS[shot][1]) for shot in CI_SHOTS]


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
    path = os.path.join(out, "church.nbt")
    n = w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "もの", len(w.entities), "大きさ", (W, H, D))
    path = os.path.join(out, "church_garden.nbt")
    n = g.save_structure(path)
    print("書き出しました:", path, "ブロック", len(g.blocks), "種類", n, "もの", len(g.entities), "大きさ", (GW, GH, GD))
    if render_dir:
        render_views(render_dir, only=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_church_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
