"""コネリアの個人倉庫（PHASE 6。建築の文書「PHASE 6：個人倉庫」の設計どおり）。

ギルドが管理する個人保管庫（Phase 0-A）。古代の「封印箱」を流用していて、契約した本人の糸にしか開かない（Phase 0-A）。
冒険者ギルドの東に地上の受付棟（17×17、地上2階）を建て、保管庫の本体は、ギルドと受付棟の下の地下2層に置く。
  - 受付棟：古代の白い石の土台の上に灰色の石の1階、木組みと漆喰の2階。屋根は東西に棟が通る1：1の赤い瓦で、入口の上に
    南へ急な破風（2：1）。東の妻に荷揚げの梁と滑車、2階の荷揚げ口（倉庫の印）。外寸 17×17、軒 +12、棟 +20。
    正面は南（環状路）。ギルドとは、ギルドの東の扉から渡り廊下（中の幅3、長さ2）でつなぐ。荷は東の裏通りから入る。
  - 地下1層：利用者の廊下と小部屋3つ（受付棟の下）。ギルドの下の保管庫（封印箱144の棚）は、境の壁の鉄格子から見える。
  - 地下2層：古代の保管庫（受付棟の下の古代の間と、ギルドの下の保管庫へ上がる古代の廊下）。働く人だけが通る。

座標：平面図の (u, v) は、u が西→東（受付棟の西の壁が u=0、東の壁が u=16）、v が正面＝南→裏＝北（正面の壁が v=0、
裏の壁が v=16）。世界の座標では x = 41 + u、z = -63 - v（x は東、z は南）。高さ y は道のブロックが 0（道の足もとが 1）。
受付棟の1階の床のブロック y=2（足もと +3）、2階 y=7（+8）、屋根裏 y=12（軒 +12）。地下1層の床 y=-2（足もと -1）、
地下2層の床 y=-6（足もと -5）。荷の土間（1階の北東）だけは床が道と同じ y=0。
構造物の (0, 0, 0) は世界の (x 22, 道のブロック - 7, z -82)。構造物の中では X = u + 19、Y = y + 7、Z = 19 - v。

置く順番：ギルド（guild_hall）→ 個人倉庫。倉庫は、ギルドの東の扉の外の段と、地下の保管庫の所のギルドの土台の詰め物
（y 0..1）を上書きする。ギルドの軒（x 39 の +13・+14）とギルドの建物の中、地面の下の土は書かない（owned。置いても消さない）。

使い方: python3 tools/mapgen/build_storage.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
  - <出力>/personal_storage.nbt（出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - --ci：自動の確認の写真の座標（CiScreenshots の MAP_VIEWS の行）を出す
ゲームの中では /place template amemaru:map/conelia/personal_storage で置ける（40×30×23）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.fixtures import hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, log, pane, sign_nbt, slab, stairs,
                          trapdoor)

W, H, D = 40, 30, 23
ORIGIN = (22, -7, -82)          # 構造物の (0, 0, 0) の世界の座標（y は道のブロックからの段数）
F1, Y1 = 3, 2                   # 1階の足もと・床のブロック
F2, Y2 = 8, 7                   # 2階
YA = 12                         # 屋根裏の床（軒 +12）
ROOF_Y0 = 11                    # 屋根の軒先（壁から1つ外）の高さ。壁の上の桁は +11
B1F, B1Y = -1, -2               # 地下1層
B2F, B2Y = -5, -6               # 地下2層

pal = Palette(seed=606, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)
AIR_CELLS = set()               # はっきり「空ける」と決めたマス（構造物の座標）。地下の部屋など（owned）


def XZ(u, v):
    """平面図の (u, v) → 構造物の (X, Z)"""
    return u + 19, 19 - v


def YS(y):
    return y + 7


def put(u, v, y, state, nbt=None):
    x, z = XZ(u, v)
    w.put(x, YS(y), z, state, nbt)
    if state[0] == "air" and w.inside(x, YS(y), z):
        AIR_CELLS.add((x, YS(y), z))


def get(u, v, y):
    x, z = XZ(u, v)
    return w.get(x, YS(y), z)


def name_at(u, v, y):
    st = get(u, v, y)
    return st[0] if st else "air"


def is_air(u, v, y):
    x, z = XZ(u, v)
    return (x, YS(y), z) in AIR_CELLS


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state() if callable(state) else state)


def clear(u0, u1, v0, v1, y0, y1):
    fill(u0, u1, v0, v1, y0, y1, AIR)


def frame(u, v, y, facing, item, rotation=0, name=None):
    x, z = XZ(u, v)
    w.item_frame(x, YS(y), z, facing, item, rotation=rotation, name=name)


def wall_lamp(u, v, y, out, style="wood", reach=2, chain=1):
    """壁（平面図の (u, v)、高さ y）から out の向きへ出す腕木の灯り（⑦の壁のランタン）。"""
    x, z = XZ(u, v)
    wall_lantern(w, x, YS(y), z, out, style=style, reach=reach, chain=chain)


def hang(u, v, y, chain=0):
    """上のブロックから吊るす灯り。y は鎖のいちばん上（鎖がなければ灯りの高さ）。"""
    x, z = XZ(u, v)
    if chain:
        hang_lantern(w, x, YS(y), z, chain=chain)
    else:
        w.put(x, YS(y), z, lantern(True))


STEP = {"south": (0, -1), "north": (0, 1), "west": (-1, 0), "east": (1, 0)}   # 世界の向き → 平面図の (du, dv)
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


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
    """古代の層の白い石：磨いた閃緑岩6・閃緑岩2・方解石1"""
    r = rng.random()
    if r < 0.66:
        return block("polished_diorite")
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def ancient_wall():
    """地下2層の古代の壁：磨いた閃緑岩が主"""
    r = rng.random()
    if r < 0.84:
        return block("polished_diorite")
    if r < 0.95:
        return block("diorite")
    return block("calcite")


def apron():
    """受付棟の前の石畳（環状路の端の帯と同じ、再建の層の石）"""
    r = rng.random()
    if r < 0.6:
        return block("stone_bricks")
    if r < 0.85:
        return block("polished_andesite")
    return block("cracked_stone_bricks")


def alley():
    """裏通り（搬入）：丸石と土の道"""
    r = rng.random()
    if r < 0.5:
        return block("cobblestone")
    if r < 0.8:
        return block("dirt_path")
    return block("gravel")


def doma():
    """荷の土間：丸石・押し固めた泥・石レンガ"""
    r = rng.random()
    if r < 0.4:
        return block("cobblestone")
    if r < 0.65:
        return block("packed_mud")
    if r < 0.9:
        return block("stone_bricks")
    return block("gravel")


SPRUCE_Y = log("spruce", "y", stripped=True)


def beam(axis):
    return log("spruce", axis, stripped=True)


def bars():
    return block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)


def hook(facing):
    """鍵に見えるトリップワイヤーフック（⑦の個人倉庫の品。封印箱の錠）"""
    return block("tripwire_hook", attached=False, facing=facing, powered=False)


BOX = block("lodestone")                       # 封印箱（地下1層の棚。鉄の帯の箱に見える。開けられない）
ANCIENT_BOX = block("chiseled_quartz_block")   # 古代の封印箱（地下2層）
ANCIENT_LID = block("waxed_oxidized_cut_copper")


def wall_sign(u, v, y, facing, lines):
    put(u, v, y, block("spruce_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))


def hanging_sign(u, v, y, lines, rotation=0):
    put(u, v, y, block("spruce_hanging_sign", rotation=rotation, attached=False, waterlogged=False),
        sign_nbt(lines, lines, hanging=True))


# ---------------------------------------------------------------- 殻：部屋の中のマスのまわりを壁・床・天井で囲む
def shell(cells, material, skip=None):
    """cells（(u, v, y) の集まり）のまわりで、まだ何も置いていないマスに material(u, v, y) を置く。"""
    for (u, v, y) in cells:
        for du, dy, dv in N6:
            n = (u + du, v + dv, y + dy)
            if n in cells:
                continue
            if skip is not None and skip(*n):
                continue
            if get(*n) is None and not is_air(*n):
                put(*n, material(*n))


def box_cells(u0, u1, v0, v1, y0, y1):
    return {(u, v, y) for u in range(u0, u1 + 1) for v in range(v0, v1 + 1) for y in range(y0, y1 + 1)}


# ---------------------------------------------------------------- 地面
def ground():
    # 前の石畳（v -1..-3。渡り廊下の前から東の裏通りまで。南は環状路の北の端の帯）
    for u in range(-2, 21):
        for v in range(-3, 0):
            put(u, v, 0, apron())
    # 東の裏通り（u 17..20。荷の搬入。倉庫地区へ）と北の裏通り（v 17..19）
    for u in range(17, 21):
        for v in range(0, 20):
            put(u, v, 0, alley())
    for u in range(-2, 17):
        for v in range(17, 20):
            put(u, v, 0, alley())
    # 建物の間のすき間（幅2。u -2..-1、v 5..16）：砂利と苔
    for u in (-2, -1):
        for v in range(5, 17):
            r = rng.random()
            put(u, v, 0, block("gravel") if r < 0.55 else block("moss_block") if r < 0.8 else block("coarse_dirt"))
    # 受付棟の下（壁と床の下の土台）：丸石（y 1）と石（y 0）。地下の部屋と荷の土間は、あとで空ける
    for u in range(0, 17):
        for v in range(0, 17):
            put(u, v, 0, block("stone"))
            if 0 < u < 16 and 0 < v < 16:
                put(u, v, 1, block("cobblestone"))


# ---------------------------------------------------------------- 地下1層：利用者の廊下・小部屋・渡りの下・鉄格子
ROOMS = ((1, 3), (5, 7), (9, 11))       # 小部屋（u の範囲。v 5..7）
ROOM_NAMES = ("一", "二", "三")


def b1_users_cells():
    cells = box_cells(-2, 11, 1, 3, -1, 1)                    # 廊下（渡りの下 u -2..-1 を含む）
    for (ua, ub) in ROOMS:
        cells |= box_cells(ua, ub, 5, 7, -1, 1)
        cells |= {(ua + 1, 4, -1), (ua + 1, 4, 0)}           # 扉
    # 利用者の階段（1階の u 15 から西へ下りる。段は u 15（床と同じ高さ）・14・13・12）の上の空き
    for v in (1, 2):
        cells |= {(12, v, -1), (12, v, 0), (12, v, 1), (13, v, 0), (13, v, 1), (14, v, 1)}
    return cells


def basement_users():
    cells = b1_users_cells()

    def material(u, v, y):
        if y == B1Y:
            return block("stone_bricks")
        outer = v == 0 or (u == 0 and v >= 4) or (u in (-2, -1) and v == 4)
        if outer and y >= 1:
            return white_stone()                               # 外から見える土台（古代の白い石）
        return gray_stone()
    for c in cells:
        put(*c, AIR)
    shell(cells, material, skip=lambda u, v, y: u <= -3 or y >= Y1)
    # 床：廊下は石レンガと明るい安山岩の市松、小部屋はトウヒの板
    for (u, v, y) in cells:
        if y != B1F:
            continue
        if 1 <= v <= 3 or u >= 12:
            put(u, v, B1Y, block("polished_andesite") if (u + v) % 2 == 0 else block("stone_bricks"))
        else:
            put(u, v, B1Y, block("spruce_planks"))
    # 利用者の階段（地下の部分）：u 14（+1）、13（0）、12（-1）。踊り場は u 11
    for v in (1, 2):
        for (u, y) in ((15, 2), (14, 1), (13, 0), (12, -1)):
            put(u, v, y, stairs("stone_brick", "east"))
    # 小部屋：扉と札、呼び出しの封印箱（台の上。前に錠）、腰かけ、灯り、ろうそくの棚
    for i, (ua, ub) in enumerate(ROOMS):
        c = ua + 1
        put(c, 4, -1, door(pal.frame, "south", False))
        put(c, 4, 0, door(pal.frame, "south", True))
        frame(c, 3, 1, "south", "name_tag")                   # 扉の上の名札（参考画像 warehouse_01）
        wall_sign(ub, 3, 0, "south", ["", "小部屋 " + ROOM_NAMES[i], "", ""])
        put(c, 7, -1, block("polished_andesite"))
        put(c, 7, 0, BOX)
        put(c, 6, 0, hook("south"))
        put(c, 6, -1, stairs("spruce", "south"))
        put(ua, 7, -1, fence(pal.frame))
        put(ua, 7, 0, lantern(False))
        put(ub, 7, -1, slab("spruce", "top"))
        put(ub, 7, 0, block("candle", candles=2, lit=True, waterlogged=False))
    # 廊下：南の土台の鉄格子の小窓（外の光）、待つ人の長椅子、吊るした灯り
    for u in (2, 5, 11, 14):
        put(u, 0, 1, bars())
    put(-2, 0, 1, bars())
    put(0, 6, 1, bars())                                    # 小部屋一の西（建物の間のすき間へ）
    for u in (6, 7):
        put(u, 1, -1, stairs("spruce", "south"))
    # 廊下の北の壁の柱（4ごと。上に持ち送り）と、柱ごとの灯り（参考画像 warehouse_01）
    for u in (0, 4, 8):
        for y in (-1, 0, 1):
            put(u, 4, y, SPRUCE_Y)
        put(u, 3, 1, stairs(pal.frame, "north", "top"))
    for (u, v) in ((-2, 2), (0, 2), (4, 2), (8, 2)):
        hang(u, v, 1)
    # 鉄格子（ギルドの東の壁の線 u=-3、v 1..3）：保管庫の入口。鉄の扉（手では開かない）
    for v in (1, 2, 3):
        put(-3, v, B1Y, block("stone_bricks"))
    for v in (1, 3):
        for y in (-1, 0, 1):
            put(-3, v, y, bars())
    put(-3, 2, -1, door("iron", "east", False))
    put(-3, 2, 0, door("iron", "east", True))
    put(-3, 2, 1, bars())
    wall_sign(-1, 3, 1, "south", ["この先は", "保管庫", "立ち入りは", "ハーヴィまで"])


# ---------------------------------------------------------------- 地下1層：ギルドの下の保管庫（封印箱の棚）
VU0, VU1 = -18, -4                    # 保管庫の中（u）。西の壁 u=-19（x 22）、東の壁 u=-3（ギルドの東の壁の線）
RACKS = ((4, 5), (8, 9), (12, 13))    # 棚（v の2列。背中合わせ）。ギルドの柱（v 8・12）が中を通る
RACK_U = (-16, -6)                    # 棚の両端の柱
BOX_U = (-15, -14, -13, -12, -10, -9, -8, -7)
GUILD_COLUMNS = ((-11, 8), (-11, 12), (-19, 8), (-19, 12))   # ギルドの1階の柱（x 30・22、z -71・-75）
VAULT_STAIR = (-9, -7)                # 地下2層から上がる口（u -9..-7、v 14..15）


def vault():
    cells = box_cells(VU0, VU1, 1, 15, -1, 1)
    for c in cells:
        put(*c, AIR)

    def material(u, v, y):
        return block("stone_bricks") if y == B1Y else gray_stone()

    def skip(u, v, y):
        if y >= Y1:
            return True                                    # ギルドの床
        if y >= 1 and (u == -3 or v == 0):
            return True                                    # ギルドの東と南の壁（白い石の土台の段）
        return False
    shell(cells, material, skip=skip)
    # 床：石レンガ（騎士団の時代の床）。通路の真ん中に明るい安山岩の筋
    for u in range(VU0, VU1 + 1):
        for v in range(1, 16):
            if VAULT_STAIR[0] <= u <= VAULT_STAIR[1] and v >= 14:
                put(u, v, B1Y, AIR)
                continue
            st = block("polished_andesite") if v == 2 else (block("stone_bricks") if rng.random() < 0.85
                                                              else block("cracked_stone_bricks"))
            put(u, v, B1Y, st)
    # ギルドの柱（1階と同じ位置で下まで通す）：石の柱
    for (u, v) in GUILD_COLUMNS:
        for y in range(B1Y, 2):
            put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))
    # 棚：背中合わせの2列。端と真ん中に柱、間に封印箱を3段（1つの面に8×3＝24）
    counter = 0
    for (va, vb) in RACKS:
        for v in (va, vb):
            for u in RACK_U:
                for y in (-1, 0, 1):
                    put(u, v, y, SPRUCE_Y)
            if (-11, v) not in GUILD_COLUMNS:
                for y in (-1, 0, 1):
                    put(-11, v, y, SPRUCE_Y)
            for u in BOX_U:
                for y in (-1, 0, 1):
                    put(u, v, y, BOX)
        # 錠（真ん中の段）と、東の端の柱の番号の札
        for (v, out, face) in ((va, va - 1, "south"), (vb, vb + 1, "north")):
            for u in BOX_U:
                put(u, out, 0, hook(face))
            wall_sign(-6, out, 1, face, ["封印箱", "%d〜%d" % (counter + 1, counter + 24), "", ""])
            counter += 24
    # 階段の口の手すり（通路の東の側）
    for v in (14, 15):
        put(-6, v, -1, fence(pal.frame))
    # 奥（西の端）の台：ハーヴィが箱を確かめる所。鉄格子から通路の先に見える
    put(-18, 1, -1, block("lectern", facing="east", has_book=True, powered=False))
    put(-17, 1, -1, slab("spruce", "top"))
    put(-17, 1, 0, block("candle", candles=3, lit=True, waterlogged=False))
    # 灯り（ギルドの床から吊るす）
    for (u, v) in ((-14, 2), (-8, 2), (-17, 7), (-13, 6), (-9, 10), (-14, 15), (-5, 6), (-5, 11), (-17, 11),
                   (-17, 3), (-4, 15)):
        if get(u, v, 1) is None:
            hang(u, v, 1)


# ---------------------------------------------------------------- 地下2層：古代の保管庫
B2_HALL = (1, 12, 8, 15)               # 古代の間（u、v の範囲）
B2_CORR = (-9, 0, 14, 15)              # 古代の廊下（保管庫へ上がる階段まで）


def b2_cells():
    cells = box_cells(B2_HALL[0], B2_HALL[1], B2_HALL[2], B2_HALL[3], -5, -3)
    cells |= box_cells(B2_CORR[0], B2_CORR[1], B2_CORR[2], B2_CORR[3], -5, -3)
    return cells


def basement_ancient():
    cells = b2_cells()
    for c in cells:
        put(*c, AIR)

    def material(u, v, y):
        if y == B2Y:
            return block("polished_diorite")
        return ancient_wall()
    shell(cells, material)
    # 床：磨いた閃緑岩に、方解石の筋（真ん中の通り）
    for (u, v, y) in cells:
        if y == B2F:
            put(u, v, B2Y, block("calcite") if (v == 11 and u >= 1) or (v == 14 and u <= 0 and u % 2 == 0)
                else block("polished_diorite"))
    # 古代の柱（石英の柱、上に彫った石英の柱頭）
    for (u, v) in ((4, 11), (9, 11)):
        put(u, v, -5, block("quartz_pillar", axis="y"))
        put(u, v, -4, block("quartz_pillar", axis="y"))
        put(u, v, -3, block("chiseled_quartz_block"))
    # 古代の封印箱（壁にはめこんだ白い箱と、緑青の銅の蓋。前に錠）：まだ誰とも結ばれていない予備の箱
    niches = [(u, 16, "south") for u in (2, 4, 6)] + [(u, 7, "north") for u in (2, 4, 6, 8, 10, 12)]
    niches += [(u, 16, "south") for u in (-5, -3, -1)] + [(u, 13, "north") for u in (-5, -3, -1)]
    niches += [(13, v, "west") for v in (9, 12)]
    for (u, v, face) in niches:
        put(u, v, -5, ANCIENT_BOX)
        put(u, v, -4, ANCIENT_LID)
        du, dv = STEP[face]
        put(u + du, v + dv, -5, hook(face))
    # 西の壁（u=0、v 8..13）：結界の紋（円・四角・十字）と、両脇の古代の結晶灯
    for v in range(8, 14):
        for y in (-5, -4, -3):
            put(0, v, y, block("quartz_bricks"))
    for (v, y) in ((10, -4), (11, -4)):
        put(0, v, y, block("chiseled_quartz_block"))
    for (v, y) in ((10, -5), (11, -5), (10, -3), (11, -3), (9, -4), (12, -4)):
        put(0, v, y, block("quartz_pillar", axis="y" if y != -4 else "z"))
    for (v, y) in ((9, -5), (12, -5), (9, -3), (12, -3)):
        put(0, v, y, block("calcite"))
    for v in (8, 13):
        put(0, v, -4, block("sea_lantern"))
    # 壁の古代の灯（結晶）
    for (u, v, y) in ((13, 11, -4), (13, 14, -4), (-7, 13, -4), (-2, 16, -4), (3, 7, -4), (11, 7, -4), (7, 16, -4)):
        put(u, v, y, block("sea_lantern"))
    # 箱の台帳（騎士団の時代の書見台）
    put(11, 9, -5, block("lectern", facing="west", has_book=True, powered=False))
    # 保管庫へ上がる階段（u -7・-8・-9 の段。西へ上がり、u -10 で保管庫の床）。段の下は石英レンガ
    for v in (14, 15):
        for (u, y) in ((-7, -5), (-8, -4), (-9, -3)):
            put(u, v, y, stairs("quartz", "west"))
        put(-10, v, B1Y, stairs("stone_brick", "west"))
        put(-8, v, -5, block("quartz_bricks"))
        put(-9, v, -5, block("quartz_bricks"))
        put(-9, v, -4, block("quartz_bricks"))
        put(-9, v, -2, AIR)
        put(-8, v, -2, AIR)
        put(-7, v, -2, AIR)


# ---------------------------------------------------------------- 働く人の階段（荷の土間 → 地下1層の踊り場 → 地下2層）
def staff_down_stair():
    # 土間（足もと 1）から西へ6段：u 15（土間の床と同じ高さ y 0）、14（-1）、13（-2。地下1層の高さ）、12（-3）、11（-4）、
    # 10（-5）。地下2層の床 u 9
    air = {(14, 0), (13, -1), (13, 0), (12, -2), (12, -1), (11, -3), (11, -2), (10, -4), (10, -3)}
    cells = {(u, v, y) for (u, y) in air for v in (14, 15)}
    for c in cells:
        put(*c, AIR)

    def material(u, v, y):
        return block("stone_bricks") if y >= B1Y else ancient_wall()
    shell(cells, material, skip=lambda u, v, y: y >= 0 and 10 <= u <= 15 and 10 <= v <= 15)
    for v in (14, 15):
        for (u, y) in ((15, 0), (14, -1), (13, -2), (12, -3), (11, -4), (10, -5)):
            put(u, v, y, stairs("stone_brick", "east"))
        put(13, v, -3, block("stone_bricks"))
        put(11, v, -5, block("stone_bricks"))
        put(12, v, -5, block("stone_bricks"))
        put(12, v, -4, block("stone_bricks"))
        put(14, v, -2, block("stone_bricks"))
    # 段の上の天井になる土間の床（y 0）が砂利だと、ゲームの中で段の上へ落ちる（11月21日、書き出しの確かめで見つけた）。
    # 土間の床の材料のうち、落ちない丸石にする（乱数の順は変えないので、ほかのマスの柄はそのまま）【補完】
    for (u, y) in air:
        if (u, y + 1) in air:
            continue
        for v in (14, 15):
            if name_at(u, v, y + 1) == "gravel":
                put(u, v, y + 1, block("cobblestone"))


# ---------------------------------------------------------------- 受付棟の1階の壁（土台 y 1..2 は古代の白い石、1階 y 3..6 は灰色の石）
def stone_walls():
    for y in range(1, Y2):
        for u in range(0, 17):
            for v in (0, 16):
                put(u, v, y, white_stone() if y <= 2 else gray_stone())
        for v in range(0, 17):
            for u in (0, 16):
                put(u, v, y, white_stone() if y <= 2 else gray_stone())
    # 角の石：土台は石英レンガ、上は明るい安山岩と石レンガを交互に
    for (u, v) in ((0, 0), (16, 0), (0, 16), (16, 16)):
        for y in range(1, Y2):
            put(u, v, y, block("quartz_bricks") if y <= 2 else
                (block("polished_andesite") if y % 2 else block("stone_bricks")))
    # 1階の付け柱（2階の柱と同じ u 4・12。正面と裏）
    for u in (4, 12):
        for v in (0, 16):
            for y in range(Y1 + 1, Y2):
                put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))
    # 2階の床の高さの帯（梁）
    for u in range(0, 17):
        put(u, 0, Y2, beam("x"))
        put(u, 16, Y2, beam("x"))
    for v in range(0, 17):
        put(0, v, Y2, beam("z"))
        put(16, v, Y2, beam("z"))
    # 控え壁（正面と裏の、角と付け柱の外。堅牢な建物の印）
    for u in (0, 4, 12, 16):
        for (v, inward) in ((-1, "north"), (17, "south")):
            put(u, v, 1, block("polished_diorite"))
            put(u, v, 2, block("polished_diorite"))
            put(u, v, 3, block("stone_bricks"))
            put(u, v, 4, block("stone_bricks"))
            put(u, v, 5, stairs("stone_brick", inward))


def arched_window(face, line, a0, width, y0=F1, height=3, sill=True):
    """石の1階の窓（鉄格子）：上の両角を逆さの階段で丸める。face の壁の line（南北の壁は v、東西の壁は u）、a0 から幅 width。"""
    left, right = {"south": ("west", "east"), "north": ("east", "west"), "east": ("south", "north"),
                   "west": ("north", "south")}[face]
    du, dv = STEP[face]
    for i in range(width):
        a = a0 + i
        u, v = (a, line) if face in ("south", "north") else (line, a)
        for y in range(y0, y0 + height):
            put(u, v, y, bars())
        if sill:
            put(u + du, v + dv, y0 - 1, slab("stone_brick", "top"))
    if width >= 3:
        ua, va = (a0, line) if face in ("south", "north") else (line, a0)
        ub, vb = (a0 + width - 1, line) if face in ("south", "north") else (line, a0 + width - 1)
        lo_u, lo_v = (ua, va) if face in ("south", "west") else (ub, vb)
        hi_u, hi_v = (ub, vb) if face in ("south", "west") else (ua, va)
        put(lo_u, lo_v, y0 + height - 1, stairs("stone_brick", left, "top"))
        put(hi_u, hi_v, y0 + height - 1, stairs("stone_brick", right, "top"))


def small_window(face, u, v, y0, height=2):
    for y in range(y0, y0 + height):
        put(u, v, y, bars())


def openings():
    # 正面（v=0）：両脇の窓（柱の間いっぱいの幅3。鉄格子）
    arched_window("south", 0, 1, 3)
    arched_window("south", 0, 13, 3)
    # 入口（u 7..9）：真ん中に濃い木の扉、両脇と上に鉄格子（上げた落とし格子の下の端）、上に丸みと要石
    put(8, 0, F1, door(pal.trim, "south", False))
    put(8, 0, F1 + 1, door(pal.trim, "south", True))
    for y in (F1, F1 + 1, F1 + 2):
        put(7, 0, y, bars())
        put(9, 0, y, bars())
    put(8, 0, F1 + 2, bars())
    put(7, 0, F1 + 3, stairs("stone_brick", "west", "top"))
    put(9, 0, F1 + 3, stairs("stone_brick", "east", "top"))
    put(8, 0, F1 + 3, block("chiseled_stone_bricks"))
    # 踏み段（2段。道の足もと 1 → 段 2 → 入口 3）
    for u in (7, 8, 9):
        put(u, -1, 1, block("stone_bricks"))
        put(u, -1, 2, stairs("stone_brick", "north"))
        put(u, -2, 1, stairs("stone_brick", "north"))
    # 西（u=0）：渡り廊下からのアーチ（v 1..3）
    for v in (1, 2, 3):
        for y in (F1, F1 + 1, F1 + 2):
            put(0, v, y, AIR)
        put(0, v, Y1, block("polished_andesite"))
    put(0, 3, F1 + 2, stairs("stone_brick", "north", "top"))
    put(0, 1, F1 + 2, stairs("stone_brick", "south", "top"))
    put(0, 2, F1 + 3, block("chiseled_stone_bricks"))
    # 東（u=16）：広間の小窓、土間の小窓、荷の入口（v 13..15。下ろした落とし格子。働く人が上げる）
    small_window("east", 16, 2, F1 + 1)
    small_window("east", 16, 11, F1)
    for v in (13, 14, 15):
        for y in (1, 2, 3):
            put(16, v, y, bars())
    put(16, 13, 4, stairs("stone_brick", "south", "top"))
    put(16, 15, 4, stairs("stone_brick", "north", "top"))
    put(16, 14, 4, block("chiseled_stone_bricks"))
    # 裏（v=16）：働く人の裏口（鉄の扉。外から手では開かない）、事務の小窓、土間の小窓
    put(8, 16, F1, door("iron", "north", False))
    put(8, 16, F1 + 1, door("iron", "north", True))
    put(8, 16, F1 + 2, stairs("stone_brick", "north", "top"))
    small_window("north", 3, 16, F1 + 1)
    small_window("north", 13, 16, F1)


# ---------------------------------------------------------------- 2階の木組み（y 8..10。y 7 は床の帯、y 11 は桁）
def house_window(face, u, v, box=True):
    """2階の窓：幅1×高さ2（y 9..10）。下に花箱"""
    put(u, v, F2 + 1, pane())
    put(u, v, F2 + 2, pane())
    if box:
        du, dv = STEP[face]
        put(u + du, v + dv, F2, leaves("flowering_azalea_leaves" if rng.random() < 0.6 else "azalea_leaves"))
        put(u + 2 * du, v + 2 * dv, F2, trapdoor(pal.frame, face, "bottom", True))


def timber_walls():
    def run(face, line, posts):
        axis = "x" if face in ("south", "north") else "z"
        for a in range(0, 17):
            u, v = (a, line) if face in ("south", "north") else (line, a)
            put(u, v, ROOF_Y0, beam(axis))
            for y in range(F2, ROOF_Y0):
                put(u, v, y, SPRUCE_Y if a in posts else block(pal.plaster))
    run("south", 0, (0, 4, 8, 12, 16))
    run("north", 16, (0, 4, 8, 12, 16))
    run("west", 0, (0, 4, 8, 12, 16))
    run("east", 16, (0, 4, 7, 9, 12, 16))
    for u in (2, 6, 10, 14):
        house_window("south", u, 0)
    for u in (6, 10, 14):
        house_window("north", u, 16, box=False)
    for v in (6, 12):
        house_window("west", 0, v, box=False)
    for v in (2, 14):
        house_window("east", 16, v, box=False)
    # 荷揚げ口（東の妻の真ん中、2階）：下半分に柵の門（落ちないように）、両脇に開いた雨戸
    put(16, 8, F2, block("spruce_fence_gate", facing="east", open=False, powered=False, in_wall=False))
    put(16, 8, F2 + 1, AIR)
    for v in (7, 9):
        for y in (F2, F2 + 1):
            put(17, v, y, trapdoor(pal.frame, "east", "bottom", True))


# ---------------------------------------------------------------- 床
def floors():
    # 1階の広間（v 1..6）：明るい安山岩と石レンガの市松（2×2）。入口からカウンターへの軸は茶色の敷物
    for u in range(1, 16):
        for v in range(1, 7):
            put(u, v, Y1, block("polished_andesite") if ((u // 2) + (v // 2)) % 2 == 0 else block("stone_bricks"))
    for u in range(1, 16):
        put(u, 7, Y1, block("stone_bricks"))
    # カウンターの奥・事務・働く人の通り（v 8..15、u 1..8）：トウヒの板
    for u in range(1, 9):
        for v in range(8, 16):
            put(u, v, Y1, block("spruce_planks"))
    for u in range(9, 16):
        put(u, 8, Y1, block("spruce_planks"))
    # 荷の土間（u 10..15、v 10..15）：床は道と同じ高さ（y 0）。土間の中は y 1 から
    for u in range(10, 16):
        for v in range(10, 16):
            put(u, v, 0, doma())
            put(u, v, 1, AIR)
            put(u, v, Y1, AIR)
    # 土間の縁（u 9 と v 9）：2段の石の壁（上がカウンターの奥の床と同じ高さ）
    for v in range(9, 16):
        put(9, v, 1, block("stone_bricks"))
        put(9, v, Y1, block("stone_bricks"))
    for u in range(9, 16):
        put(u, 9, 1, block("stone_bricks"))
        put(u, 9, Y1, block("stone_bricks"))
    # 2階の床（y 7）：トウヒの板。v 4・12 は床に見える梁（1階の天井）
    for u in range(1, 16):
        for v in range(1, 16):
            put(u, v, Y2, beam("x") if v in (4, 12) else block("spruce_planks"))
    # 屋根裏の床（y 12）
    for u in range(1, 16):
        for v in range(1, 16):
            put(u, v, YA, block("spruce_planks"))
    # 1階の広間の天井の梁（v 2・5。東西の壁から壁へ。幅15の広間の太い梁）
    for u in range(1, 16):
        put(u, 2, Y2 - 1, beam("x"))
        put(u, 5, Y2 - 1, beam("x"))


# ---------------------------------------------------------------- 階段（1階）
def stairs_ground():
    # 利用者の階段の口（1階の u 12..14、v 1..2）と手すり
    for u in (12, 13, 14):
        for v in (1, 2):
            put(u, v, Y1, AIR)
        put(u, 3, F1, fence(pal.trim))
    for v in (1, 2):
        put(11, v, F1, fence(pal.trim))
    hanging_sign(15, 2, Y2 - 2, ["地下の小部屋", "↓", "", ""], rotation=4)
    # 働く人の上り階段（u 5..6、v 10 から北へ +3..+7。いちばん上の v 14 の段は2階の床と同じ高さ）。段の下はトウヒの板
    for u in (5, 6):
        for (v, y) in ((10, 3), (11, 4), (12, 5), (13, 6), (14, 7)):
            put(u, v, y, stairs(pal.frame, "north"))
            for yy in range(F1, y):
                put(u, v, yy, block("spruce_planks"))
        for v in (11, 12, 13):
            put(u, v, Y2, AIR)
        put(u, 10, F2, fence(pal.trim))
    for v in (11, 12, 13):
        put(7, v, F2, fence(pal.trim))
    # 土間への段（2段：u 9 の +2 は床と同じ高さ、u 10 の +1）と、土間の縁の手すり
    for v in (12, 13):
        put(9, v, 1, block("stone_bricks"))
        put(9, v, Y1, stairs("stone_brick", "west"))
        put(10, v, 1, stairs("stone_brick", "west"))
    for v in (10, 11, 14, 15):
        put(9, v, F1, fence(pal.trim))
    put(9, 9, F1, SPRUCE_Y)
    for y in range(F1, Y2):
        put(9, 9, y, SPRUCE_Y)
    # 地下へ下りる口（土間の u 13..14、v 14..15）の手すり
    for u in (13, 14):
        put(u, 13, 1, fence(pal.trim))
    for v in (14, 15):
        put(12, v, 1, fence(pal.trim))


# ---------------------------------------------------------------- 1階の中：広間とカウンター
def hall():
    # 敷物の軸（入口 → ハーヴィの窓口）
    for u in (7, 8, 9):
        for v in range(1, 7):
            put(u, v, F1, block("brown_carpet") if u == 8 else block("yellow_carpet") if v in (1, 6) else
                block("brown_carpet"))
    # 長椅子（西と東の壁ぎわ。東は地下への階段の上り口の前の2マスを空ける）
    for v in (4, 5, 6):
        put(1, v, F1, stairs(pal.frame, "west"))
    for v in (5, 6):
        put(15, v, F1, stairs(pal.frame, "east"))
    # 料金の札（西の壁）と、預かりの決まり（東の壁）
    wall_sign(1, 6, F1 + 2, "east", ["個人倉庫の料金", "契約 500ノット", "1ページ＝27枠", ""])
    wall_sign(1, 5, F1 + 2, "east", ["拡張", "2ページ目 5,000", "3ページ目 20,000", "4ページ目 60,000"])
    wall_sign(1, 4, F1 + 2, "east", ["拡張", "5ページ目 150,000", "上限 5ページ", "（135枠）"])
    wall_sign(15, 6, F1 + 2, "west", ["封印箱は", "ご契約の本人の", "糸にしか", "開きません"])
    wall_sign(15, 5, F1 + 2, "west", ["同じ倉庫を", "2か所から同時に", "開くことは", "できません"])
    wall_sign(15, 4, F1 + 2, "west", ["戦闘不能でも", "お預かりの品は", "なくなりません", "倉庫番ハーヴィ"])
    # カウンター（v=7、u 2..14）：トウヒの板と濃い木の天板（高さ1.5）、上は鉄格子。窓口は u 8（契約・拡張）と u 12（受け取り）
    for u in range(2, 15):
        put(u, 7, F1, block("spruce_planks"))
        put(u, 7, F1 + 1, slab(pal.trim, "bottom"))
        for y in (F1 + 2, F1 + 3):
            put(u, 7, y, bars())
    put(8, 7, F1 + 2, AIR)
    put(12, 7, F1 + 2, AIR)
    for u in (1, 15):
        for y in range(F1, Y2):
            put(u, 7, y, block("stone_bricks"))
    hanging_sign(8, 6, Y2 - 1, ["契約・拡張", "", "", ""])
    hanging_sign(12, 6, Y2 - 1, ["受け取り口", "（市場の品）", "", ""])
    # 灯り
    for (u, v) in ((4, 3), (12, 4), (8, 3)):
        hang(u, v, Y2 - 1)


# ---------------------------------------------------------------- 1階の中：カウンターの奥（働く人の所）
def staff_rooms():
    # 受け取りの棚（v=9、u 10..14。樽を2段。カウンターの奥の通りへ口を向ける。後ろは土間）
    for u in range(10, 15):
        for y in (F1, F1 + 1):
            put(u, 9, y, block("barrel", facing="south", open=False))
    put(15, 9, F1, block("stone_bricks"))
    put(15, 9, F1 + 1, block("stone_bricks"))
    # 事務の所（u 1..4、v 9..15）：机（書見台に契約の台帳）、椅子、帳簿の棚、暖炉、鍵掛け、金庫
    put(2, 10, F1, block("lectern", facing="south", has_book=True, powered=False))
    put(3, 10, F1, slab("spruce", "top"))
    put(3, 10, F1 + 1, block("candle", candles=2, lit=True, waterlogged=False))
    put(2, 11, F1, stairs(pal.frame, "north"))
    for v in (10, 11, 12, 13):
        put(1, v, F1, block("bookshelf"))
        put(1, v, F1 + 1, block("bookshelf"))
    for y in (F1 + 1, F1 + 2):
        put(4, 15, y, hook("south"))
    wall_sign(4, 15, F1 + 3, "south", ["", "鍵掛け", "", ""])
    frame(1, 11, F1 + 2, "east", "map")                      # 保管庫の棚の配置図（ハーヴィの机の横の壁）
    put(5, 15, F1, BOX)
    put(6, 15, F1, block("barrel", facing="up", open=False))
    put(6, 15, F1 + 1, block("barrel", facing="up", open=False))
    hang(3, 12, Y2 - 1)
    # 働く人の通り（u 7..8）：裏口から窓口へ
    hang(8, 12, Y2 - 1)
    # 荷の土間（u 10..15、v 10..15）：届いた荷、仕分けの台、荷車の手押し車、灯り
    for (u, v, y) in ((10, 14, 1), (10, 15, 1), (11, 15, 1), (10, 15, 2)):
        put(u, v, y, block("barrel", facing="up", open=False))
    for (u, v, y) in ((15, 10, 1), (15, 11, 1), (15, 10, 2)):
        put(u, v, y, block("hay_block", axis="y"))
    put(14, 10, 1, block("brown_wool"))
    for u in (11, 12):
        put(u, 11, 1, slab("spruce", "top"))
    put(11, 11, 2, block("candle", candles=1, lit=True, waterlogged=False))
    put(13, 11, 1, block("crafting_table"))
    for (u, v) in ((12, 12), (14, 11)):
        put(u, v, Y2 - 1, block("chain", axis="y", waterlogged=False))
        put(u, v, Y2 - 2, lantern(True))


# ---------------------------------------------------------------- 2階の中
def upstairs():
    # 間仕切り（板の壁。端と扉の脇は柱）
    for u in range(1, 11):
        for y in range(F2, YA):
            put(u, 9, y, block("spruce_planks"))
    for v in range(10, 16):
        for y in range(F2, YA):
            put(4, v, y, block("spruce_planks"))
    for v in range(1, 16):
        for y in range(F2, YA):
            put(11, v, y, block("spruce_planks"))
    for (u, v) in ((4, 9), (11, 9), (11, 15), (4, 15), (8, 9), (10, 9), (11, 13), (4, 13), (11, 1), (1, 9)):
        for y in range(F2, YA):
            put(u, v, y, SPRUCE_Y)
    # 扉：台帳の間（u 9、v 9）、ハーヴィの部屋（u 4、v 14）、荷置き場（u 11、v 14）
    put(9, 9, F2, door(pal.frame, "north", False))
    put(9, 9, F2 + 1, door(pal.frame, "north", True))
    put(4, 14, F2, door(pal.frame, "east", False))
    put(4, 14, F2 + 1, door(pal.frame, "east", True))
    put(11, 14, F2, door(pal.frame, "west", False))
    put(11, 14, F2 + 1, door(pal.frame, "west", True))
    # 台帳の間（u 1..10、v 1..8）：契約の台帳（本棚と書見台）、長い机と椅子、封印の箱
    for v in (1, 2, 3, 4, 5, 7, 8):
        put(1, v, F2, block("bookshelf"))
        put(1, v, F2 + 1, block("bookshelf"))
    for u in range(2, 8):
        put(u, 8, F2, block("bookshelf"))
        put(u, 8, F2 + 1, block("bookshelf"))
    for u in (4, 5, 6, 7):
        put(u, 4, F2, slab("spruce", "top"))
        put(u, 3, F2, stairs(pal.frame, "south"))
        put(u, 5, F2, stairs(pal.frame, "north"))
    put(5, 4, F2 + 1, block("candle", candles=3, lit=True, waterlogged=False))
    put(10, 2, F2, block("lectern", facing="west", has_book=True, powered=False))
    put(10, 5, F2, block("lectern", facing="west", has_book=True, powered=False))
    put(10, 7, F2, BOX)
    for (u, v) in ((5, 4), (8, 6), (3, 2)):
        hang(u, v, ROOF_Y0)
    # ハーヴィの部屋（u 1..3、v 10..15）：寝台、机（窓の下）、椅子、衣装の樽、敷物
    put(1, 13, F2, block("brown_bed", facing="north", part="head", occupied=False))
    put(1, 12, F2, block("brown_bed", facing="north", part="foot", occupied=False))
    put(1, 10, F2, slab("spruce", "top"))
    put(1, 10, F2 + 1, block("candle", candles=1, lit=True, waterlogged=False))
    put(2, 10, F2, stairs(pal.frame, "east"))
    put(1, 14, F2, block("barrel", facing="east", open=False))
    put(1, 14, F2 + 1, block("barrel", facing="east", open=False))
    for v in (12, 13):
        put(2, v, F2, block("brown_carpet"))
    hang(2, 12, ROOF_Y0)
    # 通り（u 5..10、v 14..15 と u 8..10、v 10..13）
    hang(8, 14, ROOF_Y0)
    # 荷置き場（u 12..15、v 1..15）：荷揚げ口の巻き上げ、届いた荷（樽・麻袋）
    put(15, 6, F2, block("grindstone", face="floor", facing="north"))
    for (u, v, y) in ((15, 1, F2), (15, 2, F2), (14, 1, F2), (15, 1, F2 + 1), (12, 1, F2), (13, 1, F2),
                      (15, 12, F2), (15, 13, F2), (15, 15, F2), (14, 15, F2), (15, 15, F2 + 1), (15, 13, F2 + 1)):
        put(u, v, y, block("barrel", facing="up", open=False))
    for (u, v, y) in ((15, 3, F2), (15, 4, F2), (15, 3, F2 + 1), (12, 15, F2), (13, 15, F2)):
        put(u, v, y, block("hay_block", axis="y") if (u + v) % 2 else block("brown_wool"))
    for (u, v) in ((13, 4), (13, 11)):
        hang(u, v, ROOF_Y0)
    # 屋根裏（入らない物置）の灯り
    for u in (4, 8, 12):
        put(u, 8, YA + 1, lantern(False))


# ---------------------------------------------------------------- 渡り廊下（ギルドの東の扉 u=-3 → 受付棟の西のアーチ u=0）
def passage():
    for u in (-2, -1):
        for v in (0, 4):
            for y in range(1, Y2):
                put(u, v, y, white_stone() if y <= 2 else gray_stone())
        for v in (1, 2, 3):
            put(u, v, Y1, block("polished_andesite"))
            for y in range(F1, Y2):
                put(u, v, y, AIR)
    for y in (F1 + 1, F1 + 2):
        put(-1, 0, y, bars())
    hang(-2, 2, Y2 - 1)


# ---------------------------------------------------------------- 屋根
MAIN_ROOF = Roof(w, pal, XZ(1, 0)[0], XZ(16, 0)[0], XZ(0, 16)[1], XZ(0, 0)[1], YS(ROOF_Y0), ridge="x", pitch=1,
                 side_overhang=1, end_overhang=1)
CROSS_ROOF = Roof(w, pal, XZ(6, 0)[0], XZ(10, 0)[0], XZ(0, 7)[1], XZ(0, 0)[1], YS(ROOF_Y0), ridge="z", pitch=2,
                  side_overhang=1, end_overhang=1, ends=("end",))
PASSAGE_ROOF = Roof(w, pal, XZ(-2, 0)[0], XZ(-1, 0)[0], XZ(0, 4)[1], XZ(0, 0)[1], YS(Y2), ridge="x", pitch=1,
                    side_overhang=1, end_overhang=0, ends=())
ROOFS = [MAIN_ROOF, CROSS_ROOF]
CHIMNEY = ((1, 15), (2, 15))


def plan_of(x, y, z):
    return x - 19, 19 - z, y - 7


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        u, v, yy = plan_of(x, y, z)
        if (u, v) in CHIMNEY:
            return True
        if 0 <= u <= 16 and 0 <= v <= 16 and yy < YA:
            return True                     # 建物の中（屋根裏の床より下）には、屋根の部品を置かない
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))
    PASSAGE_ROOF.build()
    # 西の妻（u=0）：木組み。柱は v 4・8・12、+16 に梁、屋根裏の小窓
    gable_wall(w, pal, MAIN_ROOF, XZ(0, 0)[0], posts=(XZ(0, 4)[1], XZ(0, 12)[1]), beam_rows=(YS(16),))
    put(0, 8, 13, pane())
    put(0, 8, 14, pane())
    # 東の妻（u=16）：木組み。荷揚げ口の上の屋根裏の口（v 8）と、両脇の柱 v 7・9
    gable_wall(w, pal, MAIN_ROOF, XZ(16, 0)[0], posts=(XZ(0, 4)[1], XZ(0, 7)[1], XZ(0, 9)[1], XZ(0, 12)[1]),
               beam_rows=(YS(16),))
    for y in (13, 14):
        put(16, 8, y, pane())
        for v in (7, 9):
            put(17, v, y, trapdoor(pal.frame, "east", "bottom", True))
    # 入口の上の破風（v=0、u 6..10）：木組み。真ん中に小窓
    gable_wall(w, pal, CROSS_ROOF, XZ(0, 0)[1], posts=(XZ(7, 0)[0], XZ(9, 0)[0]))
    put(8, 0, 13, pane())
    put(8, 0, 14, pane())
    # 煙突（事務の暖炉から。u 1..2、v 15。屋根の上 +17 まで）
    for (u, v) in CHIMNEY:
        for y in range(F1, 18):
            put(u, v, y, block("stone_bricks") if rng.random() < 0.85 else block("cracked_stone_bricks"))
    put(1, 15, F1, block("campfire", lit=True, facing="south", signal_fire=False, waterlogged=False))
    put(1, 15, F1 + 1, AIR)
    put(1, 14, F1 + 2, stairs("stone_brick", "north", "top"))
    put(2, 14, F1 + 2, stairs("stone_brick", "north", "top"))
    put(1, 15, 18, block("campfire", lit=True, facing="south", signal_fire=False, waterlogged=False))
    put(2, 15, 18, slab("stone_brick", "bottom"))


# ---------------------------------------------------------------- まわりと飾り（飾りは最後）
def surroundings():
    # 正面：入口の上の大看板（トウヒの板の台に看板を並べる）
    for u in (7, 8, 9):
        put(u, -1, Y2, block("spruce_planks"))
    wall_sign(7, -2, Y2, "south", ["", "コネリア", "", ""])
    wall_sign(8, -2, Y2, "south", ["", "個人倉庫", "", ""])
    wall_sign(9, -2, Y2, "south", ["", "ギルド管理", "", ""])
    # 吊り看板（入口の東の壁から。道から +5）と、その下の品（鍵に見えるトリップワイヤーフック）
    put(10, -1, 5, block("spruce_wall_hanging_sign", facing="east", waterlogged=False),
        sign_nbt(["個人倉庫", "倉庫番 ハーヴィ", "", ""], ["個人倉庫", "倉庫番 ハーヴィ", "", ""], hanging=True))
    frame(10, -1, 4, "south", "tripwire_hook")
    # 旗（茶と黄、白い縁。封印の丸）：入口の両脇と、東の妻
    for u in (5, 11):
        put(u, -1, 6, block("brown_wall_banner", facing="south"), banner_nbt([("mc", "yellow"), ("bo", "white")]))
    put(17, 10, 6, block("brown_wall_banner", facing="east"), banner_nbt([("mc", "yellow"), ("bo", "white")]))
    # 入口の西の壁のランタン、植木鉢
    wall_lamp(6, 0, 6, "south")
    put(6, -1, 1, block("potted_azalea_bush"))
    put(10, -1, 1, block("potted_azalea_bush"))
    # 東：荷揚げの梁（妻の頂の下、+17）、持ち送り、滑車（砥石）、鎖、吊った荷（干し草の俵）
    for u in (15, 16, 17, 18):
        put(u, 8, 17, beam("x"))
    put(17, 8, 16, stairs(pal.frame, "west", "top"))
    put(18, 8, 16, block("grindstone", face="ceiling", facing="east"))
    for y in range(10, 16):
        put(18, 8, y, block("chain", axis="y", waterlogged=False))
    put(18, 8, 9, block("hay_block", axis="y"))
    # 東の裏通り：荷車（荷台と車輪）、麻袋と木箱、壁のランタン、ランタン灯
    for v in (7, 8, 9):
        put(18, v, 1, slab("spruce", "top"))
        put(19, v, 1, trapdoor(pal.trim, "west", "bottom", True))
    put(18, 9, 2, block("brown_wool"))
    put(18, 7, 2, block("hay_block", axis="y"))
    for (v, st) in ((3, block("hay_block", axis="y")), (4, block("brown_wool")), (5, block("hay_block", axis="y"))):
        put(17, v, 1, st)
    put(17, 4, 2, block("hay_block", axis="y"))
    wall_lamp(16, 12, 6, "east")
    x, z = XZ(20, 2)
    lamp_post(w, x, YS(1), z, arms=("west",))
    # 裏：裏口の踊り場と段、小さな庇、灯り
    put(8, 17, 1, block("stone_bricks"))
    put(8, 17, 2, stairs("stone_brick", "south"))
    put(7, 17, 1, stairs("stone_brick", "east"))
    for u in (7, 8, 9):
        put(u, 17, F1 + 3, stairs(pal.trim, "south"))
    for u in (7, 9):
        put(u, 17, F1 + 2, stairs(pal.frame, "south", "top"))
    put(8, 17, F1 + 2, lantern(True))
    wall_lamp(14, 16, 6, "north")
    # 建物の間のすき間：薪置き場（受付棟の壁に寄せる）、北の門、灯り
    for v in range(8, 14):
        put(-1, v, 1, log("spruce", "z"))
        if 9 <= v <= 12:
            put(-1, v, 2, log("spruce", "z"))
    for u in (-2, -1):
        put(u, 16, 1, block("spruce_fence_gate", facing="north", open=False, powered=False, in_wall=False))
    wall_lamp(0, 14, 5, "west")


# ---------------------------------------------------------------- 組み立て
def build():
    ground()
    stone_walls()
    openings()
    timber_walls()
    floors()
    basement_users()
    vault()
    basement_ancient()
    staff_down_stair()
    stairs_ground()
    passage()
    hall()
    staff_rooms()
    upstairs()
    roofs()
    surroundings()
    w.finalize()


build()


def owned(x, y, z):
    """書き出すマスか（False なら置いても元のブロックを消さない）。受付棟から東（u >= 0）の地面から上はすべて。
    それより西（渡り廊下・すき間・ギルドの下の保管庫）と地面の下は、置いたマスと、はっきり空けたマスだけ。"""
    if (x, y, z) in w.blocks or (x, y, z) in AIR_CELLS:
        return True
    u, v, yy = plan_of(x, y, z)
    return u >= 0 and yy >= 0


# 見本の絵を描く向き：目の位置と見る所（ギルドと合わせた世界の写しの座標。ギルドの構造物と同じ：x-6、y（道のブロックが 7）、z+100）
def G(x, y, z):
    """世界の座標（x、道のブロックからの y、z）→ 合わせた写しの座標"""
    return (x - 6, y + 7, z + 100)


VIEWS = {
    "front": (G(45, 8, -41), G(47, 7.5, -68)),             # 環状路の南から：受付棟の正面、渡り廊下、ギルドの南東の角
    "front_close": (G(49.5, 4.6, -53.5), G(49.5, 6.2, -63)),  # 入口の正面（環状路の上）：大看板・吊り看板・旗
    "east": (G(70, 9, -52), G(56, 8.5, -71)),              # 環状路の北東の角から：荷揚げの妻と裏通り
    "back": (G(64, 14, -96), G(48, 8, -72)),               # 北東の上から：裏口と煙突、ギルドの裏
    "aerial": (G(68, 32, -42), G(45, 6, -74)),             # 上から（南東）：ギルドと受付棟の屋根
    "passage": (G(35.5, 6.5, -53), G(40.5, 5, -64)),       # 渡り廊下の外（ギルドの南東の角から）
    "hoist": (G(67, 7, -64), G(57, 12.5, -71)),            # 東の裏通りから見上げた荷揚げの妻（梁・滑車・荷揚げ口）
}
# 中の絵（目の高さは足もと +1.62）
INSIDE = {
    "hall": (G(49.5, 3 + 1.62, -63.6), G(49.5, 4.2, -72)),        # 入口から広間と窓口
    "counter": (G(49.5, 3 + 1.62, -70.8), G(49.5, 4.2, -63)),     # ハーヴィの立ち位置から、鉄格子の窓口ごしに広間
    "from_guild": (G(39.2, 3 + 1.62, -65.0), G(52, 4.2, -65.0)),  # 渡り廊下から広間へ
    "corridor": (G(52.3, -1 + 1.62, -64.6), G(36, -0.5, -65.5)),  # 地下1層の廊下（奥に保管庫の鉄格子）
    "room": (G(47.5, -1 + 1.62, -68.1), G(47.5, -0.6, -71)),      # 小部屋二
    "grille": (G(39.8, -1 + 1.62, -65.2), G(24, -0.2, -65.0)),    # 鉄格子から保管庫の通路
    "vault": (G(35.4, -1 + 1.62, -77.5), G(23, -0.5, -72)),       # 保管庫の中（北東の角から）
    "ancient": (G(50.5, -5 + 1.62, -77.5), G(41, -4.2, -72.5)),   # 地下2層の古代の間
    "ledger": (G(50.5, 8 + 1.62, -70.5), G(43, 7.8, -65.5)),        # 2階の台帳の間
    "loft": (G(53.6, 8 + 1.62, -77.6), G(57, 8.3, -68)),          # 2階の荷置き場と荷揚げ口
    "doma": (G(51.4, 1 + 1.62, -73.4), G(56.5, 1.6, -77.5)),      # 荷の土間（落とし格子と、地下へ下りる階段）
}


# 自動の確認（GitHub Actions）でゲームの中で撮る写真：名前と、上の VIEWS・INSIDE のどれか。CiScreenshots.java の
# MAP_SHOTS・MAP_VIEWS（ギルドを置いた所からの座標。y は地面のブロックの下の面から）と同じ並び
CI_SHOTS = (("front", "front"), ("entrance", "front_close"), ("east", "east"), ("hoist", "hoist"), ("back", "back"),
            ("aerial", "aerial"), ("hall", "in_hall"), ("counter", "in_counter"), ("corridor", "in_corridor"),
            ("room", "in_room"), ("grille", "in_grille"), ("vault", "in_vault"), ("ancient", "in_ancient"))
CI_AT = (ORIGIN[0] - 6, ORIGIN[1], ORIGIN[2] + 100)     # ギルドを置いた所からの、個人倉庫を置く所


def ci_views():
    """CI_SHOTS の目の位置と見る所を、CiScreenshots.java の MAP_VIEWS の形（ギルドの構造物の座標）で返す。"""
    rows = []
    for shot, key in CI_SHOTS:
        eye, target = INSIDE[key[3:]] if key.startswith("in_") else VIEWS[key]
        rows.append((shot, (eye[0], eye[1] - 7, eye[2]), (target[0], target[1] - 7, target[2])))
    return rows


def combined_world(with_guild=True):
    """ギルドと個人倉庫を、置く順番どおりに重ねた写し（見本の絵と確かめの道具で使う）。"""
    import contextlib
    import io
    cw = World(56, 45, 41)
    if with_guild:
        with contextlib.redirect_stdout(io.StringIO()):
            import build_guild as g
        for (x, y, z), st in g.w.blocks.items():
            cw.blocks[(x, y + 7, z)] = st
            if (x, y, z) in g.w.nbt:
                cw.nbt[(x, y + 7, z)] = g.w.nbt[(x, y, z)]
        from mapgen.world import Entity
        for e in g.w.entities:
            cw.entities.append(Entity(e.kind, (e.pos[0], e.pos[1] + 7, e.pos[2]), (e.cell[0], e.cell[1] + 7, e.cell[2]),
                                      e.nbt, facing=e.facing, item=e.item, items=e.items))
    ox, oz = ORIGIN[0] - 6, ORIGIN[2] + 100
    for x in range(W):
        for y in range(H):
            for z in range(D):
                if not owned(x, y, z):
                    continue
                p = (x + ox, y, z + oz)
                st = w.blocks.get((x, y, z))
                cw.blocks.pop(p, None)
                cw.nbt.pop(p, None)
                if st is not None and st[0] != "structure_void":
                    cw.blocks[p] = st
                    if (x, y, z) in w.nbt:
                        cw.nbt[p] = w.nbt[(x, y, z)]
    from mapgen.world import Entity
    cw.entities = [e for e in cw.entities if not owned(e.cell[0] - ox, e.cell[1], e.cell[2] - oz)
                   or not (0 <= e.cell[0] - ox < W and 0 <= e.cell[2] - oz < D)]
    for e in w.entities:
        cw.entities.append(Entity(e.kind, (e.pos[0] + ox, e.pos[1], e.pos[2] + oz), (e.cell[0] + ox, e.cell[1], e.cell[2] + oz),
                                  e.nbt, facing=e.facing, item=e.item, items=e.items))
    return cw


def cut_world(world, y_cut=None, keep=None):
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if (y_cut is None or y < y_cut) and (keep is None or keep(x, y, z)):
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if (y_cut is None or e.cell[1] < y_cut) and
                   (keep is None or keep(*e.cell))]
    return cw


def render_views(out_dir, width=960, height=540, fov=70, only=None):
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    world = combined_world()
    scene = Scene(world, ground_y=8)
    for name, (eye, target) in VIEWS.items():
        if only and name not in only:
            continue
        scene.render(eye=eye, target=target, width=width, height=height, fov=fov,
                     path=os.path.join(out_dir, "storage_%s.png" % name))
        print("描きました:", name)
    low = Scene(world, ground_y=-1)
    for name, (eye, target) in INSIDE.items():
        if only and ("in_" + name) not in only:
            continue
        low.render(eye=eye, target=target, width=width, height=height, fov=72, shadows=name not in (
            "corridor", "room", "grille", "vault", "ancient"),
            path=os.path.join(out_dir, "storage_in_%s.png" % name))
        print("描きました:", "in_" + name)
    # 床で切って真上から見た絵（各階）：(名前, 切る高さ, 地面, 真ん中の世界の x・z, 高さ, 画角)
    cuts = (("cut_1f", YS(F1 + 3), 8, (50, -71.5), 66, 31), ("cut_2f", YS(F2 + 3), 8, (50, -71.5), 66, 31),
            ("cut_b1", YS(B1F + 3), -1, (40, -71), 70, 31), ("cut_b2", YS(B2F + 3), -1, (43, -72), 32, 30))
    for name, y_cut, ground, (wx, wz), hgt, cfov in cuts:
        if only and name not in only:
            continue
        cx, _, cz = G(wx, 0, wz)
        Scene(cut_world(world, y_cut), ground_y=ground).render(eye=(cx, hgt, cz + 0.2), target=(cx, 0, cz),
                                                               width=width + 300, height=height + 100, fov=cfov,
                                                               shadows=name in ("cut_1f", "cut_2f"),
                                                               path=os.path.join(out_dir, "storage_%s.png" % name))
        print("描きました:", name)


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
    path = os.path.join(out, "personal_storage.nbt")
    n = w.save_structure(path, owned=owned)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "もの", len(w.entities), "大きさ", (W, H, D))
    if render_dir:
        render_views(render_dir, only=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_storage_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
