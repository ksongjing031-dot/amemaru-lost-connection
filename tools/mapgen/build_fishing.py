"""コネリアの釣り堀（PHASE 9。建築の文書「PHASE 9：釣り堀」の設計どおり）。

古代の貯水池。いまは街の人の憩いの場で、主人はポッカ爺（Phase 0-A）。釣りのミニゲーム・魚図鑑・魚の売却の場所で、
3日目の夜に第1回の釣り大会。寄付（共同事業①、4日目から）で城壁の外の湖に「湖畔の釣り場」が開く。
場所は城壁の南西の内側（水辺地区）。北は記憶の庭、東は環状路の南西の角、南西は城壁沿いの道と水門。

構造物は5つ：
  - fishing_pond.nbt：釣り堀（池・古い石の縁とアーチ・湧き口と石の橋・上の台とポッカの釣り具屋・下の桟橋・水辺の小屋・
    入口の広場・遊歩道）と、記憶の庭の東の門から環状路への道。x -95..-60、z 23..66
  - fishing_alley.nbt：釣り堀の南東の出口から、フリーマーケットの西の門への路地
  - water_gate.nbt：城壁の水門（閉じた形）と、城壁沿いの道の水路の上の橋、城壁の外の湖
  - fishing_lakeside.nbt：寄付のあとに重ねる「湖畔の釣り場」（湖の桟橋・大会の台・受付の小屋、水門の通り道と堰を開ける）
  - fishing_pond_frozen.nbt：第2章（Phase 0-A の魚図鑑「一部が凍った釣り堀の氷下の魚」）に重ねる、池の北の半分の氷

座標：平面図の (u, v) は、u が西→東（x = u - 95）、v が北→南（z = v + 23）。高さ y は道のブロックが 0（足もとが 1）。
池の水面のブロックは y -3（水面は道より約3低い）。構造物の (0, 0, 0) は世界の (x -95, 道のブロック - 9, z 23)。
構造物の中では X = u、Y = y + 9、Z = v。
使い方: python3 tools/mapgen/build_fishing.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
ゲームの中では /place template amemaru:map/conelia/fishing_pond で置ける（道に立って ~ ~-10 ~）。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.fixtures import hang_lantern, lamp_post, wall_lantern
from nbtlib.tag import Compound, String

from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, lectern_nbt, log, pane, sign_nbt,
                          slab, stairs, trapdoor, wall)

X0, Z0 = -95, 23                 # 平面図の (0, 0) の世界の x・z
W, H, D = 36, 25, 44
YOFF = 9                         # 構造物の Y = y + YOFF
WL = -3                          # 池の水面のブロックの高さ
BED = -8                         # いちばん深い所の底
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

pal = Palette(seed=909, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = pal.rng
LOCK = "amemaru:fishing_lock"    # 樽の鍵（この名前のアイテムを持たないと開かない）
WATER = block("water", level=0)

w = World(W, H, D)


def put(u, v, y, state, nbt=None):
    if 0 <= u < W and 0 <= v < D and -YOFF <= y < H - YOFF:
        w.put(u, y + YOFF, v, state, nbt)


def get(u, v, y):
    return w.get(u, y + YOFF, v)


def name_at(u, v, y):
    st = get(u, v, y)
    return st[0] if st else "air"


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state)


def frame(u, v, y, facing, item, rotation=0, name=None):
    w.item_frame(u, y + YOFF, v, facing, item, rotation=rotation, name=name)


def wl(u, v, y, out, reach=2, chain=1, style="wood"):
    wall_lantern(w, u, y + YOFF, v, out, style=style, reach=reach, chain=chain)


def hang(u, v, y, chain=1):
    hang_lantern(w, u, y + YOFF, v, chain=chain)


def post_lamp(u, v, y, arms):
    """再建のランタン灯。台の下が低い（斜面）ときは、台の下を道の高さまで石で埋める"""
    gy = surface(u, v)
    if gy is not None and gy < y - 1:
        for yy in range(gy + 1, y):
            put(u, v, yy, gray_stone())
    lamp_post(w, u, y + YOFF, v, arms=arms)


def wsign(u, v, y, facing, lines, wood="spruce"):
    put(u, v, y, block(wood + "_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))


def hsign(u, v, y, rotation, front, back=None, wood="spruce"):
    """吊り看板（上のブロックから下げる）"""
    put(u, v, y, block(wood + "_hanging_sign", rotation=rotation, attached=False, waterlogged=False),
        sign_nbt(front, back or front, hanging=True))


def barrel(u, v, y, facing="up"):
    """鍵つきの樽（中に物を入れられない）"""
    put(u, v, y, block("barrel", facing=facing, open=False),
        Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))


def wet(state):
    """水の中に置く形（水を抱いた状態）"""
    name, props = state
    p = dict(props)
    if "waterlogged" in p:
        p["waterlogged"] = "true"
    return (name, tuple(sorted(p.items())))


# ---------------------------------------------------------------- 範囲（城壁沿いの道・記憶の庭は書かない）
def radius(u, v):
    return math.hypot(u + X0, v + Z0)


def in_garden(u, v):
    """記憶の庭（PHASE 8。x -96..-72、z 16..33）"""
    return u <= 23 and v <= 10


def mine(u, v):
    """この構造物が書くマス（城壁沿いの道（半径107から）と記憶の庭は書かない。古い堰より西の水路は水門の構造物）"""
    if u <= 10 and 38 <= v <= 43:
        return False
    return 0 <= u < W and 0 <= v < D and radius(u, v) < 107 and not in_garden(u, v)


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


def white_stone(moss=0.0):
    """古代の層の白い石：磨いた閃緑岩・閃緑岩・方解石（水ぎわは苔の石を混ぜる）"""
    r = rng.random()
    if r < moss:
        return block("mossy_stone_bricks") if rng.random() < 0.5 else block("mossy_cobblestone")
    if r < 0.62:
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


def grid_paving():
    """広場：石レンガと丸石（再建の層）"""
    r = rng.random()
    if r < 0.55:
        return block("stone_bricks")
    if r < 0.75:
        return block("cobblestone")
    if r < 0.87:
        return block("andesite")
    return block("cracked_stone_bricks")


def dirt_path():
    """遊歩道（自然の岸の側）：土の道と粗い土、ところどころ砂利"""
    r = rng.random()
    if r < 0.62:
        return block("dirt_path")
    if r < 0.86:
        return block("coarse_dirt")
    return block("gravel")


def alley_paving():
    """路地：丸石・砂利・土の道・粗い土（都市のルール⑤）"""
    r = rng.random()
    if r < 0.4:
        return block("cobblestone")
    if r < 0.65:
        return block("gravel")
    if r < 0.85:
        return block("dirt_path")
    return block("coarse_dirt")


def grass():
    return block("grass_block", snowy=False)


def shore_block():
    """水ぎわ（水面の高さ）：砂利・砂・粘土"""
    r = rng.random()
    if r < 0.45:
        return block("gravel")
    if r < 0.8:
        return block("sand")
    return block("clay")


def bank_block():
    """岸の斜面：草・粗い土・根の土"""
    r = rng.random()
    if r < 0.7:
        return grass()
    if r < 0.9:
        return block("coarse_dirt")
    return block("rooted_dirt")


# ---------------------------------------------------------------- 池の形
# 水面（行ごとの u の範囲）。北（v 14）と東（u 24）は古代の石の縁が残り、西と南は崩れて自然の岸になった
POND_ROWS = {
    15: (11, 23), 16: (10, 23), 17: (9, 23), 18: (8, 23), 19: (8, 23), 20: (8, 23),
    21: (8, 23), 22: (7, 23), 23: (7, 23), 24: (6, 23), 25: (6, 23), 26: (6, 23),
    27: (6, 22), 28: (7, 22), 29: (7, 21), 30: (8, 21), 31: (8, 20), 32: (9, 20),
    33: (9, 19), 34: (10, 18), 35: (10, 17), 36: (11, 16), 37: (11, 15), 38: (11, 14), 39: (12, 14),
}
ANCIENT = (10, 23, 15, 26)        # 古い貯水池の四角（深い）。北の縁 v 14・東の縁 u 24
ARM = {(u, v) for u in range(1, 8) for v in (18, 19, 20)}          # 西の古代の湧き口からの入り江
CHANNEL = {(u, v) for u in range(12, 15) for v in (40, 41)}         # 南西の入り江から水門へ（u 11 は古い堰）
WEIR = [(11, 40), (11, 41)]


def is_water(u, v):
    if v in POND_ROWS:
        a, b = POND_ROWS[v]
        if a <= u <= b:
            return True
    return (u, v) in ARM or (u, v) in CHANNEL


WATER_CELLS = {(u, v) for u in range(W) for v in range(D) if is_water(u, v)}
NORTH_EDGE = [(u, 14) for u in range(10, 25)]       # 北の古い石の縁（道の高さの笠石）
EAST_EDGE = [(24, v) for v in range(15, 27)]        # 東の古い石の縁


def water_dist():
    """水のマスから、岸（水でないマス）までの近さ（深さを決める）"""
    dist = {}
    for (u, v) in WATER_CELLS:
        best = 9
        for du in range(-5, 6):
            for dv in range(-5, 6):
                if (u + du, v + dv) not in WATER_CELLS:
                    best = min(best, max(abs(du), abs(dv)))
        dist[(u, v)] = best
    return dist


WDIST = water_dist()


def land_dist():
    """陸のマスから、いちばん近い水までの近さ（岸の斜面の高さを決める）"""
    dist = {}
    for u in range(W):
        for v in range(D):
            if (u, v) in WATER_CELLS:
                continue
            best = 9
            for du in range(-4, 5):
                for dv in range(-4, 5):
                    if (u + du, v + dv) in WATER_CELLS:
                        best = min(best, max(abs(du), abs(dv)))
            dist[(u, v)] = best
    return dist


LDIST = land_dist()


def depth(u, v):
    """水の深さ（1〜5）。古い貯水池の四角は深く、崩れた岸のそばは浅い"""
    u0, u1, v0, v1 = ANCIENT
    if u0 <= u <= u1 and v0 <= v <= v1:
        return 5
    if (u, v) in ARM:
        return 2 if u <= 3 else 3
    if (u, v) in CHANNEL:
        return 3
    return min(5, 1 + WDIST[(u, v)])


# ---------------------------------------------------------------- 道と広場の場所（平面図）
def rect(u0, u1, v0, v1):
    return {(u, v) for u in range(u0, u1 + 1) for v in range(v0, v1 + 1)}


GARDEN_PATH = rect(24, 35, 0, 2)          # 記憶の庭の東の門 → 環状路の西の辺
NORTH_GATE_PATH = rect(25, 27, 3, 10)     # 北の入口（記憶の庭の道から）
NORTH_WALK = rect(1, 27, 11, 13)          # 北の遊歩道（記憶の庭の南の塀ぞい）
WEST_WALK = rect(1, 3, 14, 28) - rect(1, 3, 17, 21)   # 西の小道（自然の岸）→ 城壁沿いの道。入り江は石の橋
BRIDGE = rect(1, 3, 17, 21)               # 湧き口の入り江の石の橋
DECK = rect(25, 28, 14, 23) | rect(25, 33, 24, 28)    # 上の台（板の高さ y 1）
SHOP = rect(29, 33, 15, 23)               # ポッカの釣り具屋（5×9）
PLAZA = rect(27, 35, 29, 38)              # 入口の広場（環状路の南西の角から）
SOUTH_WALK = rect(15, 26, 39, 41)         # 南の遊歩道
SW_LINK = rect(11, 17, 42, 43)            # 南西で城壁沿いの道へ
SE_EXIT = rect(30, 32, 39, 43)            # 南東の出口 → フリーマーケットへの路地
PATHS = GARDEN_PATH | NORTH_GATE_PATH | NORTH_WALK | WEST_WALK | SOUTH_WALK | SW_LINK | SE_EXIT
FLAT = PATHS | DECK | SHOP | PLAZA | BRIDGE | set(NORTH_EDGE) | set(EAST_EDGE)


def ground():
    """地面：道の高さの草と道、池の底と水、岸の斜面、地面の下の土と石（置いたとき下に穴をあけない）"""
    for u in range(W):
        for v in range(D):
            if not mine(u, v):
                continue
            if (u, v) in WATER_CELLS:
                d = depth(u, v)
                bottom = WL - d
                for y in range(-YOFF, bottom):
                    put(u, v, y, block("stone") if y < bottom - 1 else block("dirt"))
                put(u, v, bottom, block("clay") if rng.random() < 0.25 else (block("gravel") if d >= 2 else block("sand")))
                for y in range(bottom + 1, WL + 1):
                    put(u, v, y, WATER)
                continue
            # 陸：斜面の高さ（水ぎわは水面の高さ、そこから1段ずつ上がる）。道と台と縁は道の高さ
            ld = LDIST.get((u, v), 9)
            top = 0 if (u, v) in FLAT else max(WL, min(0, WL - 1 + ld))
            for y in range(-YOFF, top):
                put(u, v, y, block("stone") if y < -4 else block("dirt"))
            if (u, v) in FLAT:
                put(u, v, top, grass())
            elif top == WL:
                put(u, v, top, shore_block())
            elif top < 0:
                put(u, v, top, bank_block())
            else:
                put(u, v, top, grass())


def paving():
    for (u, v) in PATHS:
        if not mine(u, v):
            continue
        if (u, v) in NORTH_WALK and u >= 8 or (u, v) in NORTH_GATE_PATH:
            put(u, v, 0, white_paving())      # 古い貯水池の側：古代の白い石畳
        elif (u, v) in GARDEN_PATH:
            put(u, v, 0, alley_paving())
        else:
            put(u, v, 0, dirt_path())
    for (u, v) in PLAZA:
        edge = u in (27, 35) or v in (29, 38)
        put(u, v, 0, block("polished_andesite") if edge else grid_paving())


def ancient_edges():
    """北と東に残る古い貯水池の石の壁：水の底から道の高さまで、白い石（水ぎわは苔）。上は笠石"""
    for (u, v) in NORTH_EDGE + EAST_EDGE:
        if not mine(u, v):
            continue
        for y in range(BED - 1, 0):
            if y == WL + 1:
                st = block("mossy_stone_bricks") if (u * 3 + v) % 4 == 0 else block("polished_diorite")
            elif y == WL + 2:
                st = block("polished_diorite") if (u + v) % 5 else block("quartz_bricks")
            else:
                st = block("polished_diorite") if (u * 5 + v * 3 + y) % 7 else block("diorite")
            put(u, v, y, st)
        put(u, v, 0, block("chiseled_stone_bricks") if (u + v) % 6 == 0 else block("polished_diorite"))


# ---------------------------------------------------------------- 小さな部品
def one_door(u, v, y, facing, wood="spruce", hinge="left"):
    put(u, v, y, door(wood, facing, False, hinge))
    put(u, v, y + 1, door(wood, facing, True, hinge))


def shutter(u, v, y, facing):
    """開いた雨戸（facing の反対の側に板が来る。壁の外のマスに置き、板を壁に付ける）"""
    put(u, v, y, trapdoor("spruce", facing, "bottom", True))


def bench(u, v, y, facing):
    """腰かけ（トウヒの階段2つと、両はしのひじ掛けの開いたトラップドア）。facing は座る人が向く方。
    席は facing と直角に2つ並ぶ：north・south なら (u, v)・(u+1, v)、east・west なら (u, v)・(u, v+1)。"""
    if facing in ("north", "south"):
        seats, arms = [(u, v), (u + 1, v)], [((u - 1, v), "west"), ((u + 2, v), "east")]
    else:
        seats, arms = [(u, v), (u, v + 1)], [((u, v - 1), "north"), ((u, v + 2), "south")]
    for (cu, cv) in seats:
        put(cu, cv, y, stairs("spruce", OPP[facing]))
    for (cu, cv), f in arms:
        put(cu, cv, y, trapdoor("spruce", f, "bottom", True))


def flag(u, v, y, facing, base="cyan"):
    """釣り堀の旗（青緑と白。都市のルール⑦）"""
    other = "white" if base == "cyan" else "cyan"
    put(u, v, y, block(base + "_wall_banner", facing=facing),
        banner_nbt([("mr", other), ("bs", other), ("bo", "white")]))


def tree_oak(u, v, y, h=5):
    """オーク：幹と、丸い葉の冠"""
    for k in range(h):
        put(u, v, y + k, log("oak", "y"))
    for du in range(-2, 3):
        for dv in range(-2, 3):
            for k, rmax in ((h - 2, 2.3), (h - 1, 2.3), (h, 1.5), (h + 1, 0.9)):
                if math.hypot(du, dv) > rmax or (du == 0 and dv == 0 and k < h):
                    continue
                if get(u + du, v + dv, y + k) is None and mine(u + du, v + dv):
                    put(u + du, v + dv, y + k, leaves("oak_leaves"))


def tree_cherry(u, v, y, h=4):
    """桜（都市のルール⑦の植栽。釣り堀と記憶の庭だけ）：幹と、平たく広がる花の冠、根もとの花びら"""
    for k in range(h):
        put(u, v, y + k, log("cherry", "y"))
    for (du, dv, f) in ((1, 0, "x"), (-1, 0, "x"), (0, 1, "z"), (0, -1, "z")):
        if (du + dv + u + v) % 2 == 0:
            put(u + du, v + dv, y + h - 1, log("cherry", f))
    for du in range(-3, 4):
        for dv in range(-3, 4):
            d = math.hypot(du, dv)
            for k, rmax in ((h - 1, 1.5), (h, 2.6), (h + 1, 2.0), (h + 2, 1.0)):
                if d > rmax or (du == 0 and dv == 0 and k < h):
                    continue
                if get(u + du, v + dv, y + k) is None and mine(u + du, v + dv):
                    put(u + du, v + dv, y + k, block("cherry_leaves", persistent=True, distance=1, waterlogged=False))
    for du in range(-2, 3):
        for dv in range(-2, 3):
            cu, cv = u + du, v + dv
            if (du, dv) == (0, 0) or not mine(cu, cv) or (cu, cv) in WATER_CELLS or (du * 3 + dv) % 2:
                continue
            gy = surface(cu, cv)
            if gy is not None and name_at(cu, cv, gy) in ("grass_block",) and get(cu, cv, gy + 1) is None:
                put(cu, cv, gy + 1, block("pink_petals", facing=("north", "east", "south", "west")[(du + dv) % 4],
                                          flower_amount=1 + (du * dv) % 4))


def azalea(u, v, y):
    put(u, v, y, block("flowering_azalea"))


def surface(u, v):
    """いちばん上の地面のブロックの高さ（水と飾りは数えない）"""
    for y in range(2, -YOFF - 1, -1):
        n = name_at(u, v, y)
        if n in ("grass_block", "dirt_path", "coarse_dirt", "gravel", "sand", "clay", "rooted_dirt", "dirt",
                 "polished_diorite", "diorite", "calcite", "stone_bricks", "cobblestone", "andesite"):
            return y
    return None


# ---------------------------------------------------------------- 古代の湧き口と石の橋（西の入り江）
SPRING = (6, 17)                 # 湧き口の口（水の出る所）


def spring_and_bridge():
    """西の入り江：北の岸に古代の湧き口（白い石の壁に、水の出る口と彫った石英）。西の小道は石のアーチの橋（幅3）で渡る"""
    su, sv = SPRING
    # 湧き口の壁（v 16..17、u 5..7）。水面から +1 まで
    for u in range(5, 8):
        for v in (16, 17):
            for y in range(WL, 2):
                put(u, v, y, white_stone(moss=0.25 if y <= -1 else 0.05))
    put(su, sv, 0, block("chiseled_quartz_block"))
    put(su, sv, 1, slab("quartz", "bottom"))
    for u in (5, 7):
        put(u, 17, 1, block("quartz_bricks"))
        put(u, 17, 2, slab("polished_diorite", "bottom"))
    put(su, 16, 1, block("polished_diorite"))
    put(su, 16, 2, slab("polished_diorite", "bottom"))
    # 水の出る口：壁の中のくぼみ（上・下・左右・奥は石）に水を1つ。前（南）へあふれて、入り江に落ちる
    put(su, sv, -1, WATER)
    put(su, sv, -2, block("quartz_bricks"))
    put(su, 18, -1, AIR)
    put(su, 18, -2, AIR)
    # 札
    wsign(5, 18, 0, "south", ["古代の湧き口", "いまも冷たい水が", "わいている", ""])
    # 石の橋：u 0..4 の床（y 0）、アーチの下（v 18・20 の逆さ階段）、両わきの低い塀
    for u in range(0, 5):
        for v in range(17, 22):
            put(u, v, 0, block("stone_bricks") if (u + v) % 5 else block("mossy_stone_bricks"))
        put(u, 18, -1, stairs("stone_brick", "north", "top"))
        put(u, 20, -1, stairs("stone_brick", "south", "top"))
        put(u, 19, -1, AIR)
        for v in (17, 21):
            for y in range(BED - 1, 0):
                if (u, v) not in WATER_CELLS:
                    put(u, v, y, gray_stone())
    for v in range(17, 22):
        for y in range(WL, 0):
            if (0, v) not in WATER_CELLS:
                put(0, v, y, gray_stone())
    for u in (0, 4):
        for v in range(17, 22):
            put(u, v, 1, wall("stone_brick" if (v % 2) else "mossy_stone_brick"))
    for v in (17, 21):
        for u in (0, 4):
            put(u, v, 1, block("stone_bricks"))
            put(u, v, 2, slab("stone_brick", "bottom"))


# ---------------------------------------------------------------- 古代のアーチと、下をくぐる桟橋（北）
ARCH = (14, 18, 16)              # 柱の u（西・東）と v


def ancient_arch():
    """北の縁の古代のアーチ（崩れかけ）と、北の遊歩道からアーチの下をくぐって池へ出る木の桟橋（幅3）"""
    ua, ub, va = ARCH
    for u in (ua, ub):
        for y in range(BED - 1, 3):
            put(u, va, y, white_stone(moss=0.3 if y <= 0 else 0.08))
        put(u, va, 2, block("chiseled_stone_bricks") if u == ua else block("quartz_bricks"))
    put(ua + 1, va, 2, stairs("polished_diorite", "west", "top"))
    put(ub - 1, va, 2, stairs("polished_diorite", "east", "top"))
    for u in range(ua, ub + 1):
        put(u, va, 3, white_stone(moss=0.15))
    put((ua + ub) // 2, va, 3, block("chiseled_quartz_block"))
    put((ua + ub) // 2, va, 4, slab("quartz", "bottom"))
    put(ua, va, 4, block("mossy_stone_bricks"))
    put(ub, va, 4, wall("mossy_stone_brick"))
    # 崩れた跡：東の上の角が欠けて、石が水の底に落ちている
    put(ub, va, 3, block("cracked_stone_bricks"))
    put(ub + 1, va + 1, depth_bottom(ub + 1, va + 1) + 1, block("cracked_stone_bricks"))
    hang((ua + ub) // 2, va, 2, chain=0)
    # 桟橋：v 14 と 15 は階段（北の遊歩道 y 0 → 桟橋 y -2）、v 16..19 は板（水面の1段上）
    for u in range(ua + 1, ub):
        put(u, 14, 0, stairs("spruce", "north"))
        put(u, 15, -1, stairs("spruce", "north"))
        put(u, 15, -2, block("spruce_planks"))
        for v in range(16, 20):
            put(u, v, -2, block("spruce_planks"))
    for (u, v) in ((ua + 1, 19), (ub - 1, 19)):
        for y in range(depth_bottom(u, v) + 1, -2):
            put(u, v, y, log("spruce", "y", stripped=True))
        put(u, v, -1, fence("spruce"))
    # 小舟をつなぐ杭（桟橋の東の脇）
    put(ub - 1, 17, -1, fence("spruce"))


def depth_bottom(u, v):
    """水のマスの底のブロックの高さ"""
    return WL - depth(u, v) if (u, v) in WATER_CELLS else WL


# ---------------------------------------------------------------- 上の台・階段・下の桟橋
def upper_deck():
    """岸の上の木の台（y 1）。池の側に柵、北の端と南の端は1段の踏み段"""
    for (u, v) in DECK:
        put(u, v, 1, block("spruce_planks"))
        put(u, v, 0, block("dirt") if (u, v) not in set(EAST_EDGE) else get(u, v, 0))
    for v in range(14, 29):
        put(25, v, 1, log("spruce", "z", stripped=True))
    for u in range(25, 34):
        put(u, 28, 1, log("spruce", "x", stripped=True))
    # 池の側（u 25）の柵。階段の口（v 25..27）はあける
    for v in range(15, 25):
        put(25, v, 2, fence("spruce"))
    # 北の端（記憶の庭の道と北の遊歩道から）と、南の端（入口の広場へ）の踏み段
    for u in range(26, 29):
        put(u, 14, 1, stairs("spruce", "south"))
    for u in range(27, 32):
        put(u, 28, 1, stairs("spruce", "north"))
    put(25, 14, 2, fence("spruce"))
    # 台の灯り：柵の柱の上の灯り
    for v in (18, 23):
        put(25, v, 3, lantern(False))


PIER = rect(12, 21, 25, 27) | rect(11, 12, 23, 29) | rect(20, 21, 28, 29)


def lower_pier():
    """下の桟橋（水面の1段上、y -2）：台から幅3の階段（途中に踊り場）で下りる。先は T の形、南の枝は水辺の小屋へ"""
    # 階段：u 25（台の高さ y 1）→ u 24 踊り場（y 0）→ u 23（y 0）→ u 22（y -1）→ 桟橋
    for v in (25, 26, 27):
        put(25, v, 1, stairs("spruce", "east"))
        put(24, v, 0, block("spruce_planks"))
        for y in range(BED - 1, 0):
            if get(24, v, y) is None or name_at(24, v, y) == "water":
                put(24, v, y, white_stone(moss=0.2))
        put(23, v, 0, stairs("spruce", "east"))
        put(23, v, -1, block("spruce_planks"))
        put(22, v, -1, stairs("spruce", "east"))
        put(22, v, -2, block("spruce_planks"))
    for (u, v) in PIER:
        put(u, v, -2, block("spruce_planks"))
    # 柱（4ごと）：底から板の下まで
    for (u, v) in ((21, 25), (21, 27), (17, 25), (17, 27), (13, 25), (13, 27), (11, 23), (12, 23), (11, 29),
                   (12, 29), (23, 25), (23, 27), (21, 28)):
        for y in range(depth_bottom(u, v) + 1, -2 + (1 if u == 23 else 0)):
            put(u, v, y, log("spruce", "y", stripped=True))
    # 柱と横木のランタン灯（参考 fishing_01）：桟橋の両はしの柱に横木を渡し、真ん中に吊るす
    for u in (19, 15):
        for v in (25, 27):
            put(u, v, -1, fence("spruce"))
            put(u, v, 0, fence("spruce"))
            put(u, v, 1, fence("spruce"))
        for v in (25, 26, 27):
            put(u, v, 2, log("spruce", "z", stripped=True))
        put(u, 26, 1, lantern(True))
    # 桟橋の先（T）の灯り
    for v in (23, 29):
        put(11, v, -1, fence("spruce"))
        put(11, v, 0, lantern(False))
    # 舟をつなぐ杭
    for (u, v) in ((14, 27), (18, 27)):
        put(u, v, -1, fence("spruce"))


def boat(u0, v0, axis, length=4):
    """小舟（ブロックで作った手こぎの舟）。水面のすぐ上（y -2）。axis の向きに length、幅2"""
    for i in range(length):
        for j in range(2):
            u, v = (u0 + i, v0 + j) if axis == "x" else (u0 + j, v0 + i)
            end = i in (0, length - 1)
            if end:
                f = ("west" if i == 0 else "east") if axis == "x" else ("north" if i == 0 else "south")
                put(u, v, -2, stairs("spruce", f))
            else:
                put(u, v, -2, slab("spruce", "bottom"))
    # 櫂（舟の中に1本）
    mu, mv = (u0 + 1, v0) if axis == "x" else (u0, v0 + 1)
    put(mu, mv, -1, trapdoor("spruce", "north" if axis == "x" else "west", "bottom", False))


# ---------------------------------------------------------------- ポッカの釣り具屋（上の台の東、5×9）
SU0, SU1, SV0, SV1 = 29, 33, 15, 23
SF = 1                          # 床のブロック
SC = 6                          # 天井（梁）


def shop():
    """木組みの1階の店。西（台の側）の面に、カウンターの開口（品を売り、魚を買い取る）。ポッカの立つ所はカウンターの内側。
    南の面に扉（ポッカの出入り）。屋根は南北に棟の赤い瓦（都市のルール⑥）"""
    for u in range(SU0, SU1 + 1):
        for v in range(SV0, SV1 + 1):
            edge = u in (SU0, SU1) or v in (SV0, SV1)
            put(u, v, 0, gray_stone())
            put(u, v, SF, block("spruce_planks") if not edge else gray_stone())
            if not edge:
                put(u, v, SC, block("spruce_planks"))
                continue
            post = (u in (SU0, SU1) and v in (SV0, 19, SV1)) or (v in (SV0, SV1) and u in (SU0, SU1))
            for y in range(SF + 1, SC + 1):
                if y == SC:
                    put(u, v, y, log("spruce", "z" if u in (SU0, SU1) else "x", stripped=True))
                elif post:
                    put(u, v, y, log("spruce", "y", stripped=True))
                else:
                    put(u, v, y, block("calcite"))
    # カウンター（西の面 v 16..18）：高さ1.5（樽の上に半ブロック）。上は開口、上に日よけ
    for v in (16, 17, 18):
        put(SU0, v, 2, block("barrel", facing="west", open=False), Compound({"id": String("minecraft:barrel"),
                                                                             "Lock": String(LOCK)}))
        put(SU0, v, 3, slab("spruce", "bottom"))
        put(SU0, v, 4, AIR)
        put(SU0, v, 5, AIR)
        put(SU0 - 1, v, 5, slab("dark_oak", "top"))
    # 窓（北・東の面、+3..+4）と雨戸
    for (u, v, f) in ((31, SV0, "north"), (SU1, 17, "east"), (SU1, 21, "east")):
        for y in (3, 4):
            put(u, v, y, pane())
    for (u, v, f) in ((30, SV0 - 1, "north"), (32, SV0 - 1, "north"), (SU1 + 1, 16, "east"), (SU1 + 1, 18, "east"),
                      (SU1 + 1, 20, "east"), (SU1 + 1, 22, "east")):
        for y in (3, 4):
            shutter(u, v, y, f)
    # 扉（南の面、u 31）
    one_door(31, SV1, 2, "south")
    wl(32, SV1, 5, "south", reach=2, chain=1)
    # 屋根
    roof = Roof(w, pal, x0=SU0, x1=SU1, z0=SV0, z1=SV1, y0=SC + 1 + YOFF, ridge="z", pitch=2, side_overhang=1,
                end_overhang=1, ends=("start", "end"), thickness=2, kick=False)
    roof.build(skip=lambda x, y, z: SU0 <= x <= SU1 and SV0 <= z <= SV1 and y <= SC + YOFF)
    for at in (SV0, SV1):
        gable_wall(w, pal, roof, at, posts=(31,))
    # 中：カウンターの内側（ポッカの立つ所 u 30、v 17）、奥の棚（えさの樽）、生け簀（水の大釜）、竿の掛け台、手引きの書見台
    for v in (16, 17, 18):
        barrel(32, v, 2, "west")
    for v in (16, 18):
        barrel(32, v, 3, "west")
    put(32, 17, 3, block("potted_fern"))
    put(30, 21, 2, block("water_cauldron", level=3))
    put(32, 21, 2, block("lectern", facing="west", has_book=True, powered=False),
        lectern_nbt("釣り堀の手引き", "ポッカ", GUIDE_PAGES))
    put(32, 20, 2, block("spruce_planks"))
    put(32, 22, 2, block("spruce_planks"))
    put(32, 22, 3, block("candle", candles=2, lit=True, waterlogged=False))
    for v in (20, 22):
        put(31, v, 2, block("brown_carpet"))
    for v in (16, 17, 18):
        put(31, v, 2, block("cyan_carpet") if v != 17 else block("white_carpet"))
    hang(31, 17, 5, chain=0)
    hang(31, 21, 5, chain=0)
    for v in (17, 21):
        put(31, v, SC + 1, lantern(False))
    # 店の吊り看板（カウンターの北の柱から腕木）と、その下の品（生鱈）
    put(SU0 - 1, 15, 5, fence("dark_oak"))
    put(SU0 - 2, 15, 5, fence("dark_oak"))
    hsign(SU0 - 2, 15, 4, 4, ["ポッカの", "釣り具屋", "魚の買い取り", ""], ["ポッカの", "釣り具屋", "貸し竿あり", ""])
    frame(SU0 - 1, 19, 3, "west", "cod")
    # 外の面（v 20..22）の「今日の魚」の額（台から見える）
    frame(SU0 - 1, 20, 3, "west", "salmon")
    frame(SU0 - 1, 21, 3, "west", "tropical_fish")
    frame(SU0 - 1, 22, 3, "west", "pufferfish")
    wsign(SU0 - 1, 21, 4, "west", ["今日の魚", "", "釣れたら", "見せておくれ"])
    # 中の竿（貸し竿）の額
    frame(30, 22, 3, "north", "fishing_rod")
    return roof


GUIDE_PAGES = [
    "釣り堀の手引き\n\n竿は店で貸すよ。\n浮きが沈んだら\n合わせて、\n糸の張りを\n切らないように\n保つんだなぁ。",
    "釣れた魚は\nカウンターで\n買い取るよ。\n料理屋に\n回すのもいい。\n\n魚図鑑も\n見ておくれ。",
    "3日目の夜は\n釣り大会。\n大物を\n競うんだなぁ。\n\n城壁の水門の\n向こうの湖にも\nいつか行きたいなぁ。",
    "魚は、\n待てる人の\nとこに\n来るんだなぁ。\n\n　　ポッカ",
]


# ---------------------------------------------------------------- 水辺の小屋（南東の水ぎわ、5×3。床は桟橋の高さ）
HU0, HU1, HV0, HV1 = 19, 23, 30, 32
HF = -2


def shed():
    """水辺の倉庫（網・樽・櫂の置き場）。床は桟橋と同じ高さで、西の半分は水の上（柱で立つ）。壁は3段の低い小屋で、
    屋根は東西に棟の赤い瓦（都市のルール⑥）"""
    top = HF + 4
    for u in range(HU0, HU1 + 1):
        for v in range(HV0, HV1 + 1):
            edge = u in (HU0, HU1) or v in (HV0, HV1)
            corner = u in (HU0, HU1) and v in (HV0, HV1)
            put(u, v, HF, block("spruce_planks"))
            if (u, v) in WATER_CELLS:
                if corner or (u == 21 and v in (HV0, HV1)):
                    for y in range(depth_bottom(u, v) + 1, HF):
                        put(u, v, y, log("spruce", "y", stripped=True))
            else:
                for y in range(WL - 1, HF):
                    put(u, v, y, block("dirt") if not edge else gray_stone())
            if not edge:
                continue
            post = corner or (u == 21 and v in (HV0, HV1))
            for y in range(HF + 1, top + 1):
                if y == top:
                    put(u, v, y, log("spruce", "x" if v in (HV0, HV1) else "z", stripped=True))
                elif post:
                    put(u, v, y, log("spruce", "y", stripped=True))
                else:
                    put(u, v, y, block("spruce_planks"))
    # 扉（北の面、u 21。桟橋の南の枝から）と窓
    one_door(21, HV0, HF + 1, "north")
    for (u, v) in ((HU0, 31), (HU1, 31)):
        put(u, v, HF + 2, pane())
    put(20, HV1, HF + 2, pane())
    put(22, HV1, HF + 2, pane())
    # 屋根（東西に棟）
    roof = Roof(w, pal, x0=HU0, x1=HU1, z0=HV0, z1=HV1, y0=top + 1 + YOFF, ridge="x", pitch=2, side_overhang=1,
                end_overhang=1, ends=("start", "end"), thickness=2, kick=False)
    roof.build(skip=lambda x, y, z: HU0 <= x <= HU1 and HV0 <= z <= HV1 and y <= top + YOFF)
    for at in (HU0, HU1):
        gable_wall(w, pal, roof, at, posts=())
    # 中：網の繕い台、樽、櫂、吊るした灯り
    barrel(22, 31, HF + 1, "west")
    barrel(22, 31, HF + 2, "west")
    put(20, 31, HF + 1, block("crafting_table"))
    hang(21, 31, top + 2, chain=2)
    # 外：戸口の灯り
    wl(22, HV0, top, "north", reach=1, chain=0) if False else None
    put(22, HV0 - 1, HF + 1, fence("spruce"))
    put(22, HV0 - 1, HF + 2, lantern(False))
    return roof


# ---------------------------------------------------------------- 入口の広場と門（東、環状路の南西の角から）
def plaza():
    """入口の広場：東の端に門（柱と横木、吊り看板「釣り堀」、旗2枚、灯り、柱に生鱈の額）。釣り大会の知らせの札、腰かけ、灯り"""
    gate_cells = [(35, v) for v in range(31, 36)]
    posts = [(35, 30), (35, 36)]
    for (u, v) in posts:
        put(u, v, 1, block("stone_bricks"))
        for y in range(2, 6):
            put(u, v, y, log("spruce", "y", stripped=True))
        put(u, v, 6, log("spruce", "z", stripped=True))
        put(u, v, 7, slab("dark_oak", "bottom"))
    for (u, v) in gate_cells:
        put(u, v, 6, log("spruce", "z", stripped=True))
        put(u, v, 7, slab("dark_oak", "bottom"))
    hsign(35, 32, 5, 4, ["", "釣り堀", "", ""], ["", "環状路", "", ""])
    hsign(35, 34, 5, 4, ["ポッカの", "釣り堀", "", ""], ["↑ 北の門", "記憶の庭", "", ""])
    for v in (31, 35):
        put(35, v, 5, block("chain", axis="y", waterlogged=False))
        put(35, v, 4, lantern(True))
    flag(36, 30, 5, "east", "cyan") if False else None
    for (u, v) in posts:
        put(u - 1, v, 5, block("cyan_wall_banner" if v == 30 else "white_wall_banner", facing="west"),
            banner_nbt([("mr", "white" if v == 30 else "cyan"), ("bs", "white" if v == 30 else "cyan"),
                        ("bo", "white" if v == 30 else "cyan")]))
    frame(34, 30, 3, "west", "cod")
    # 釣り大会の知らせの札（広場の北西、台の踏み段の脇）
    for v in (30, 31):
        put(28, v, 1, log("spruce", "y", stripped=True))
        put(28, v, 2, log("spruce", "y", stripped=True))
        put(28, v, 3, slab("dark_oak", "bottom"))
    wsign(29, 30, 2, "east", ["釣り大会", "3日目の夜", "大物を競う", ""])
    wsign(29, 31, 2, "east", ["大物の記録", "まだ", "だれも", ""])
    # 腰かけ（広場の南、北を向く）と灯り
    bench(29, 38, 1, "north")
    bench(33, 38, 1, "north")
    post_lamp(31, 34, 1, ("west", "east"))


# ---------------------------------------------------------------- 遊歩道の腰かけ・灯り・木・水草
def promenade():
    # 北の遊歩道：記憶の庭の塀ぞいに腰かけ（南の池を向く）と灯り
    for u in (4, 11, 20):
        bench(u, 11, 1, "south")
    for (u, v) in ((8, 11), (17, 11), (26, 11)):
        post_lamp(u, v, 1, ("south",))
    # 西の小道の灯り（西のふち）
    for v in (15, 26):
        post_lamp(0, v, 1, ("east",))
    # 南の遊歩道：池の側に腰かけ（北を向く）と灯り
    for u in (17, 22):
        bench(u, 39, 1, "north")
    for (u, v) in ((15, 38), (26, 39)):
        post_lamp(u, v, 1, ("south",) if v == 38 else ("west",))
    # 記憶の庭の道と北の入口の灯り
    post_lamp(31, 3, 1, ("north",))
    post_lamp(24, 4, 1, ("east",))
    # 南東の出口の灯り
    post_lamp(33, 41, 1, ("west",))


REEDS = ((7, 21), (6, 22), (5, 25), (5, 26), (6, 28), (7, 30), (8, 32), (9, 34), (10, 36), (16, 37), (17, 36),
         (19, 34), (20, 33))


def flower_bed(u0, u1, v0, v1, y=0, flowers=("azure_bluet", "oxeye_daisy", "cornflower", "dandelion")):
    """花壇：石畳を草に替え、花を植える（縁はトウヒの開いた落とし戸の板）"""
    k = 0
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            put(u, v, y, grass())
            put(u, v, y + 1, block(flowers[k % len(flowers)]))
            k += 1


def planting():
    # 桜（西の岸と、南の岸）。オーク（北東の芝と、北西の角）。花の咲いたツツジ
    tree_cherry(5, 24, surface(5, 24) + 1, h=4)
    tree_cherry(21, 36, surface(21, 36) + 1, h=4)
    tree_oak(32, 8, 1, h=5)
    tree_oak(5, 32, surface(5, 32) + 1, h=4)
    tree_oak(26, 42, 1, h=5)
    tree_cherry(9, 13 - 0, 1, h=4) if False else None
    flower_bed(29, 34, 29, 29)
    flower_bed(7, 9, 11, 11, flowers=("lily_of_the_valley", "azure_bluet"))
    flower_bed(14, 16, 11, 11, flowers=("white_tulip", "cornflower"))
    flower_bed(23, 24, 11, 11, flowers=("oxeye_daisy", "allium"))
    for (u, v) in ((8, 15), (24, 37), (26, 36), (4, 28), (9, 37), (29, 12), (34, 13), (6, 16), (24, 31)):
        gy = surface(u, v)
        if gy is not None and get(u, v, gy + 1) is None and name_at(u, v, gy) in ("grass_block", "coarse_dirt",
                                                                                    "rooted_dirt"):
            azalea(u, v, gy + 1)
    # 葦（さとうきび）：水面の高さの岸に
    for (u, v) in REEDS:
        if (u, v) in WATER_CELLS or name_at(u, v, WL) not in ("sand", "gravel", "clay"):
            continue
        if not any((u + du, v + dv) in WATER_CELLS for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            continue
        put(u, v, WL, block("sand"))
        for k in (1, 2) if (u + v) % 3 else (1, 2, 3):
            put(u, v, WL + k, block("sugar_cane", age=0))
    # 蓮の葉：入り江と西の池の静かな所
    for (u, v) in ((5, 19), (6, 18), (9, 23), (8, 26), (10, 30), (12, 33), (13, 35), (9, 28), (22, 16)):
        if (u, v) in WATER_CELLS and get(u, v, WL + 1) is None:
            put(u, v, WL + 1, block("lily_pad"))
    # 浅い所の水草
    for (u, v) in sorted(WATER_CELLS):
        if depth(u, v) == 2 and (u * 7 + v * 3) % 4 == 0 and name_at(u, v, WL - depth(u, v) + 1) == "water":
            put(u, v, WL - depth(u, v) + 1, block("seagrass"))
    # 底に沈んだ古代の柱（深い所）
    for u in range(16, 21):
        put(u, 21, BED + 1, block("quartz_pillar", axis="x"))
    put(15, 21, BED + 1, block("chiseled_quartz_block"))
    put(20, 22, BED + 1, block("polished_diorite"))


LADDERS = ((12, 15, "south"), (21, 15, "south"), (23, 18, "west"), (23, 23, "west"))


def ladders():
    """古い石の壁のはしご（水から道の高さの縁まで。池に落ちた人が上がれる）"""
    for (u, v, f) in LADDERS:
        for y in range(WL - 1, 0):
            st = block("ladder", facing=f, waterlogged=(y <= WL))
            put(u, v, y, st)


def barrels_and_nets():
    """樽と木箱と網は、使う人が分かる所に3〜5（都市のルール⑦。釣り堀は魚の樽と網）"""
    # 上の台の階段の脇：魚の樽（ふたの上に生鱈の額）
    for (u, v) in ((26, 24), (27, 24)):
        barrel(u, v, 2)
    barrel(26, 24, 3)
    frame(27, 24, 3, "up", "cod")
    # 水辺の小屋の東の外（広場の側の土手）：干した網（柵の枠にくもの巣）
    for v in (30, 32):
        for y in (-1, 0, 1):
            put(25, v, y, fence("spruce"))
    for y in (-1, 0):
        put(25, 31, y, block("cobweb"))
    put(25, 31, 1, fence("spruce"))
    # 桟橋の先（T）の樽2つ（えさ）
    barrel(12, 29, -1)
    barrel(12, 23, -1)


def deck_benches():
    """上の台の腰かけ（池を向く。釣り大会を見る所）"""
    bench(27, 15, 2, "west") if False else None
    for v in (16, 20):
        bench(26, v, 2, "west")


def bait_bed():
    """北東の芝：ポッカのえさの土床（堆肥箱と粗い土。みみずを育てる）"""
    for u in range(29, 32):
        for v in range(5, 7):
            put(u, v, 0, block("coarse_dirt") if (u + v) % 2 else block("rooted_dirt"))
    for u in (29, 31):
        put(u, 4, 1, block("composter", level=7))
    put(32, 5, 1, trapdoor("spruce", "west", "bottom", True))
    put(32, 6, 1, trapdoor("spruce", "west", "bottom", True))
    bench(33, 10, 1, "west")


def weir():
    """南西の入り江の出口の古い堰（水門へ続く水路の口）。石の枠の間に、濃い木の堰板を底から水面の上まで落とす（閉じている）"""
    for v in (39, 42):
        for y in range(BED - 1, 1):
            put(11, v, y, white_stone() if y < 0 else block("polished_diorite"))
    for v in (40, 41):
        for y in range(BED - 1, WL - 2):
            put(11, v, y, block("stone"))
        for y in range(WL - 2, WL + 2):
            put(11, v, y, block("dark_oak_planks"))
        put(11, v, WL + 2, block("polished_diorite"))
        put(11, v, 0, block("chiseled_stone_bricks"))


def build():
    rng.seed(909)
    ground()
    paving()
    ancient_edges()
    weir()
    spring_and_bridge()
    ancient_arch()
    upper_deck()
    lower_pier()
    boat(14, 28, "x")
    boat(19, 17, "z")
    shop_roof = shop()
    shed_roof = shed()
    plaza()
    promenade()
    planting()
    ladders()
    barrels_and_nets()
    deck_benches()
    bait_bed()
    w.finalize()
    return shop_roof, shed_roof


SHOP_ROOF, SHED_ROOF = build()


# ================================================================ 水門と湖（water_gate.nbt）
# 世界の座標で書く。構造物の (0, 0, 0) は世界の (x -131, 道のブロック - 9, z 48)。X = x + 131、Y = y + 9、Z = z - 48
GX0, GZ0 = -131, 48
GW, GH, GD = 48, 34, 48
WALL_IN, WALL_OUT, ROAD_IN = 112, 116, 107       # 城壁の内側・外側の半径、城壁沿いの道の内側の半径
WALL_TOP = 11                                    # 城壁のいちばん上のブロック（歩く所。足もとは +12）
CH_Z = (63, 64)                                  # 水路の z
LEDGE_Z = (61, 62, 65, 66)                       # 水路の両わきの岸の道（水門のアーチをくぐる。寄付のあとに通れる）
GH_X0, GH_X1, GH_Z0, GH_Z1 = -99, -91, 57, 70    # 水門の門の建物（城壁をまたぐ）
PORT_X = -95                                     # 落とし格子
SLUICE_X = -85                                   # 水門の堰板（水路の内側の口）
CANAL_X0 = -103                                  # 外の水路の西の端（湖へ）
LAKE_C, LAKE_R = (-113.0, 73.0), 13.5            # 湖の真ん中と半径

g = World(GW, GH, GD)
G_OWNED = set()


def gput(x, z, y, state, nbt=None):
    X, Z = x - GX0, z - GZ0
    if 0 <= X < GW and 0 <= Z < GD and -YOFF <= y < GH - YOFF:
        g.put(X, y + YOFF, Z, state, nbt)
        G_OWNED.add((X, Z))


def gget(x, z, y):
    return g.get(x - GX0, y + YOFF, z - GZ0)


def gname(x, z, y):
    st = gget(x, z, y)
    return st[0] if st else "air"


def rr(x, z):
    return math.hypot(x, z)


def hsh(x, y, z):
    """決まった形の乱れ（0〜1）。石の混ぜ方が縞にならないように"""
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def lake_dist(x, z):
    """湖の岸からの近さ（負なら水の中）。でこぼこは決まった形"""
    dx, dz = x - LAKE_C[0], z - LAKE_C[1]
    a = math.atan2(dz, dx)
    edge = LAKE_R + 1.5 * math.sin(3 * a + 0.7) + 0.9 * math.cos(5 * a)
    return math.hypot(dx, dz) - edge


def in_lake(x, z):
    return lake_dist(x, z) < 0 and rr(x, z) >= WALL_OUT + 3 and x <= -102


def in_gatehouse(x, z):
    return GH_X0 <= x <= GH_X1 and GH_Z0 <= z <= GH_Z1


def channel_band(x, z):
    """水路と両わきの岸の道（堰板から外の水路の端まで）"""
    return GH_Z0 < z < GH_Z1 and z in CH_Z + LEDGE_Z and CANAL_X0 <= x <= SLUICE_X


def outside(x, z):
    return rr(x, z) >= WALL_OUT


def is_wall(x, z):
    return WALL_IN <= rr(x, z) < WALL_OUT and not in_gatehouse(x, z)


def is_road(x, z):
    return ROAD_IN <= rr(x, z) < WALL_IN and not in_gatehouse(x, z)


def wall_stone(x, z, y):
    """城壁の石：下の6段は古代の白い石、上の6段は灰色の石（直した跡）"""
    k = hsh(x, y, z)
    if y <= 5:
        return block("polished_diorite") if k < 0.62 else (block("diorite") if k < 0.9 else block("calcite"))
    if k < 0.76:
        return block("stone_bricks")
    return block("cracked_stone_bricks") if k < 0.86 else (block("mossy_stone_bricks") if k < 0.95 else block("andesite"))


def gate_ground():
    """地面：城壁の外は草と土（湖のまわりは、水面の高さから広い段で上がる）、城壁沿いの道は丸石と石レンガ、湖は水"""
    for X in range(GW):
        for Z in range(GD):
            x, z = X + GX0, Z + GZ0
            r = rr(x, z)
            if channel_band(x, z) or in_gatehouse(x, z):
                continue
            if r < ROAD_IN:
                continue
            if in_lake(x, z):
                d = -lake_dist(x, z)
                dep = max(1, min(6, int(1 + d * 0.8)))
                bottom = WL - dep
                for y in range(-YOFF, bottom):
                    gput(x, z, y, block("stone") if y < bottom - 1 else block("dirt"))
                gput(x, z, bottom, block("gravel") if hsh(x, 0, z) < 0.7 else block("clay"))
                for y in range(bottom + 1, WL + 1):
                    gput(x, z, y, WATER)
                continue
            if is_road(x, z):
                for y in range(-4, 0):
                    gput(x, z, y, block("dirt") if y > -3 else block("stone"))
                k = hsh(x, 0, z)
                gput(x, z, 0, block("cobblestone") if k < 0.55 else (block("stone_bricks") if k < 0.9 else
                                                                      block("mossy_cobblestone")))
                continue
            if is_wall(x, z):
                continue
            # 城壁の外の地面：湖の岸は水面の高さから、広い段で上がる
            ld = lake_dist(x, z) if rr(x, z) >= WALL_OUT + 2 else 9
            top = WL if ld < 1.2 else (WL + 1 if ld < 3.0 else (WL + 2 if ld < 5.0 else 0))
            for y in range(-YOFF, top):
                gput(x, z, y, block("stone") if y < -4 else block("dirt"))
            k = hsh(x, 1, z)
            if top == WL:
                gput(x, z, top, block("sand") if k < 0.75 else block("gravel"))
            else:
                gput(x, z, top, grass() if k < 0.85 or top == 0 else block("coarse_dirt"))


def gate_wall():
    """城壁（高さ12、厚さ4。上は歩ける通路と、外の縁の胸壁）"""
    for X in range(GW):
        for Z in range(GD):
            x, z = X + GX0, Z + GZ0
            if not is_wall(x, z):
                continue
            r = rr(x, z)
            for y in range(-5, WALL_TOP + 1):
                gput(x, z, y, block("stone") if y < -1 else wall_stone(x, z, y))
            if r >= WALL_OUT - 1.1:
                gput(x, z, WALL_TOP + 1, wall_stone(x, z, 8))
                if hsh(x, 7, z) < 0.5:
                    gput(x, z, WALL_TOP + 2, wall_stone(x, z, 9))
            else:
                gput(x, z, WALL_TOP, block("stone_bricks") if hsh(x, 3, z) < 0.8 else block("polished_andesite"))


def gate_channel():
    """水路：堰板（閉じている）→ 城壁沿いの道の下（石のアーチ）→ 水門のアーチ（落とし格子）→ 外の水路 → 湖。
    両わきの岸の道（幅2、道の高さ）は、城壁沿いの道から水門のアーチをくぐって、城壁の外の水路の口まで続く"""
    for x in range(CANAL_X0, SLUICE_X + 1):
        for z in CH_Z:
            for y in range(-YOFF, WL - 2):
                gput(x, z, y, block("stone"))
            gput(x, z, WL - 2, block("gravel"))
            if x == SLUICE_X:
                continue
            for y in range(WL - 1, WL + 1):
                gput(x, z, y, WATER)
        for z in LEDGE_Z:
            for y in range(-YOFF, 0):
                gput(x, z, y, block("stone") if y < WL - 2 else (white_stone() if z in (62, 65) else block("dirt")))
            edge = z in (62, 65)
            k = hsh(x, 2, z)
            gput(x, z, 0, block("polished_andesite") if edge else (block("stone_bricks") if k < 0.7 else
                                                                    block("cobblestone")))
    # 堰板（水路の内側の口。濃い木の板を、底から水面の上まで落とす。閉じている）。両わきは石の柱
    for z in CH_Z:
        for y in range(WL - 2, WL + 2):
            gput(SLUICE_X, z, y, block("dark_oak_planks"))
        gput(SLUICE_X, z, WL + 2, block("polished_diorite"))
        gput(SLUICE_X, z, 0, block("chiseled_stone_bricks"))
    # 城壁沿いの道の下の水路：道の床（y 0）は石の橋、下はアーチ（逆さの階段）
    for x in range(-91, SLUICE_X):
        for z in CH_Z:
            if is_road(x, z) or rr(x, z) < ROAD_IN:
                gput(x, z, 0, block("stone_bricks"))
                gput(x, z, -1, stairs("stone_brick", "north" if z == CH_Z[0] else "south", "top"))
    # 外の水路の口：南北の岸は石（湖へ下りる水路の縁）
    for x in range(CANAL_X0, GH_X0):
        for z in (60, 67):
            for y in range(-YOFF, 1):
                gput(x, z, y, block("stone") if y < WL - 2 else white_stone())
            gput(x, z, 1, wall("stone_brick" if hsh(x, 5, z) < 0.6 else "mossy_stone_brick"))
    # 水路の口から西へ、湖の水まで掘る（湖の水が落とし格子の前まで来る。両わきの土の岸が水を止める）
    for z in CH_Z:
        x = CANAL_X0 - 1
        while gname(x, z, WL) != "water" and x > GX0:
            for y in range(WL + 1, 1):
                gput(x, z, y, AIR)
            for y in range(WL - 1, WL + 1):
                gput(x, z, y, WATER)
            gput(x, z, WL - 2, block("gravel"))
            x -= 1


def gatehouse():
    """水門の門の建物（城壁をまたぐ。x -99..-91、z 57..70）：下の6段は古代の白い石、上は灰色の石。水路と両わきの岸の道を
    丸いアーチ（幅6）でくぐらせ、真ん中に落とし格子（閉じている）。上に胸壁と、巻き上げ小屋（緑青の銅の屋根）"""
    top = WALL_TOP + 1
    for x in range(GH_X0, GH_X1 + 1):
        for z in range(GH_Z0, GH_Z1 + 1):
            for y in range(-YOFF + 2, top + 1):
                gput(x, z, y, block("stone") if y < WL - 3 else wall_stone(x, z, y))
            edge = x in (GH_X0, GH_X1) or z in (GH_Z0, GH_Z1)
            if edge:
                gput(x, z, top + 1, wall_stone(x, z, 8))
                if (x + z) % 2 == 0:
                    gput(x, z, top + 2, slab("stone_brick", "bottom"))
            else:
                gput(x, z, top, block("stone_bricks"))
    # 角の付け柱（古代の白い石を上まで通す）と、2つの面の帯（+6、灰色の石との境目）
    for x in (GH_X0, GH_X1):
        for z in (GH_Z0, GH_Z1):
            for y in range(0, top + 1):
                gput(x, z, y, block("polished_diorite") if y % 4 else block("quartz_bricks"))
    for z in range(GH_Z0, GH_Z1 + 1):
        for x in (GH_X0, GH_X1):
            if gname(x, z, 6) not in ("air",):
                gput(x, z, 6, block("chiseled_stone_bricks") if z % 3 == 0 else block("polished_andesite"))
    # アーチ（z 61..66）：水の上から +4 まで空け、上を丸める（+3・+4 の逆さの階段）。真ん中の水路は +5 まで
    for x in range(GH_X0, GH_X1 + 1):
        for z in LEDGE_Z + CH_Z:
            lo = WL + 1 if z in CH_Z else 1
            hi = 5 if z in CH_Z else (4 if z in (62, 65) else 3)
            for y in range(lo, hi + 1):
                gput(x, z, y, AIR)
            if z in CH_Z:
                for y in range(-YOFF + 2, WL - 2):
                    gput(x, z, y, block("stone"))
                gput(x, z, WL - 2, block("gravel"))
                for y in range(WL - 1, WL + 1):
                    gput(x, z, y, WATER)
            else:
                for y in range(-YOFF + 2, 0):
                    gput(x, z, y, block("stone") if y < WL - 2 else white_stone())
                gput(x, z, 0, block("polished_andesite") if z in (62, 65) else block("stone_bricks"))
        gput(x, 61, 4, stairs("polished_diorite", "north", "top"))
        gput(x, 66, 4, stairs("polished_diorite", "south", "top"))
        gput(x, 62, 5, stairs("polished_diorite", "north", "top"))
        gput(x, 65, 5, stairs("polished_diorite", "south", "top"))
        for z in CH_Z:
            gput(x, z, 6, block("polished_diorite"))
    # 両方の面のアーチの縁（石英）と要石、外の面の古代の紋（結び目の形）
    for x, out in ((GH_X0, -1), (GH_X1, 1)):
        for (z, y) in ((60, 1), (60, 2), (60, 3), (60, 4), (61, 5), (62, 6), (63, 7), (64, 7), (65, 6), (66, 5),
                       (67, 4), (67, 3), (67, 2), (67, 1)):
            gput(x, z, y, block("quartz_bricks"))
        gput(x, 63, 7, block("chiseled_quartz_block"))
        gput(x, 64, 7, block("chiseled_quartz_block"))
    for (z, y) in ((63, 10), (64, 10), (62, 11), (65, 11), (63, 12), (64, 12)):
        gput(GH_X0, z, y, block("quartz_bricks"))
    gput(GH_X0, 63, 11, block("amethyst_block"))
    gput(GH_X0, 64, 11, block("amethyst_block"))
    # 落とし格子（鉄格子。閉じている。水の底から +5 まで）
    for z in LEDGE_Z + CH_Z:
        hi = 5 if z in CH_Z else (4 if z in (62, 65) else 3)
        lo = WL - 1 if z in CH_Z else 1
        for y in range(lo, hi + 1):
            gput(PORT_X, z, y, block("iron_bars", north=False, south=False, east=False, west=False,
                                     waterlogged=y <= WL))
    # アーチの中の灯り（内の半分と外の半分。天井から吊るす）
    for x in (-92, -98):
        for z in (62, 65):
            gput(x, z, 4, lantern(True))
    # 巻き上げ小屋（上の真ん中。x -96..-94、z 61..66）と、緑青の銅の屋根（棟の上 +21。城門の塔の22より低い）
    x0, x1, z0, z1 = -96, -94, 61, 66
    base = top + 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            gput(x, z, top, block("spruce_planks") if not edge else block("stone_bricks"))
            for y in range(base, base + 3):
                if not edge:
                    gput(x, z, y, AIR)
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                gput(x, z, y, log("spruce", "y", stripped=True) if corner else
                     (log("spruce", "z" if x in (x0, x1) else "x", stripped=True) if y == base + 2 else
                      block("calcite")))
    for x in (x0, x1):
        for z in CH_Z:
            gput(x, z, base + 1, pane())
    for z in CH_Z:
        gput(-95, z, base, block("chain", axis="y", waterlogged=False))
        gput(-95, z, base + 1, block("chain", axis="y", waterlogged=False))
    gput(-95, 62, base, lantern(False))
    cpal = Palette(seed=910, roof="waxed_oxidized_cut_copper", roof_block="waxed_oxidized_copper", trim="dark_oak",
                   post="spruce", beam="dark_oak", plaster="calcite", frame="spruce")
    # 破風の縁と破風板は付けない（付けると縁の頂が +22・+23 になり、城門の塔の22を超える）。いちばん上は棟の笠 +21
    roof = Roof(g, cpal, x0=x0 - GX0, x1=x1 - GX0, z0=z0 - GZ0, z1=z1 - GZ0, y0=base + 3 + YOFF, ridge="z", pitch=2,
                side_overhang=1, end_overhang=1, ends=(), thickness=2, kick=False)
    roof.build(skip=lambda X, Y, Z: x0 - GX0 <= X <= x1 - GX0 and z0 - GZ0 <= Z <= z1 - GZ0 and Y < base + 3 + YOFF)
    for at in (z0, z1):
        gable_wall(g, cpal, roof, at - GZ0, posts=())
    for X in range(x0 - GX0 - 1, x1 - GX0 + 2):
        for Z in range(z0 - GZ0 - 1, z1 - GZ0 + 2):
            G_OWNED.add((X, Z))
    # 旗（釣り堀の青緑と白）は、寄付のあとに重ねる形で掛ける
    return roof


def lake_life():
    """湖のまわり：葦・蓮の葉・トウヒの木。湖の真ん中の底に、古代の石の輪（湖の主のすみかの名残り【補完】）"""
    cx, cz = int(LAKE_C[0]), int(LAKE_C[1])
    for a in range(0, 360, 30):
        x = cx + int(round(3.2 * math.cos(math.radians(a))))
        z = cz + int(round(3.2 * math.sin(math.radians(a))))
        if gname(x, z, WL) != "water":
            continue
        b = WL
        while gname(x, z, b - 1) == "water":
            b -= 1
        gput(x, z, b, block("chiseled_quartz_block") if a % 90 == 0 else block("polished_diorite"))
        if a % 60 == 0 and gname(x, z, b + 1) == "water" and b + 1 < WL - 1:
            gput(x, z, b + 1, block("quartz_pillar", axis="y"))
    for X in range(GW):
        for Z in range(GD):
            x, z = X + GX0, Z + GZ0
            if gname(x, z, WL) == "water" and gname(x, z, WL + 1) == "air" and hsh(x, 9, z) < 0.035:
                gput(x, z, WL + 1, block("lily_pad"))
            if gname(x, z, WL) == "sand" and hsh(x, 4, z) < 0.12 and gname(x, z, WL + 1) == "air":
                if any(gname(x + dx, z + dz, WL) == "water" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    for k in (1, 2):
                        gput(x, z, WL + k, block("sugar_cane", age=0))
    for (x, z, h) in ((-128, 63, 6), (-129, 82, 5), (-118, 91, 5), (-104, 89, 6), (-121, 54, 5), (-108, 52, 4)):
        if gname(x, z, 0) != "grass_block":
            continue
        for k in range(1, h + 1):
            gput(x, z, k, log("spruce", "y"))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for k, rmax in ((h - 2, 2.2), (h - 1, 1.9), (h, 1.2), (h + 1, 0.6)):
                    if 0 < math.hypot(dx, dz) <= rmax and gname(x + dx, z + dz, k) == "air":
                        gput(x + dx, z + dz, k, leaves("spruce_leaves"))
        gput(x, z, h + 1, leaves("spruce_leaves"))
        gput(x, z, h + 2, leaves("spruce_leaves"))


def wall_lights():
    """城壁沿いの道を照らす、城壁の内の面の壁のランタン（腕木と持ち送り。8〜10ごと）"""
    for (x, z) in ((-101, 50), (-97, 55), (-90, 70), (-87, 76)):
        # 城壁の内の面のブロック（内側の隣は道）を探す
        best = None
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                cx, cz = x + dx, z + dz
                if is_wall(cx, cz) and rr(cx, cz) < WALL_IN + 1:
                    for out, (ox, oz) in (("east", (1, 0)), ("north", (0, -1)), ("south", (0, 1))):
                        if is_road(cx + ox, cz + oz) and is_road(cx + 2 * ox, cz + 2 * oz):
                            d = abs(dx) + abs(dz)
                            if best is None or d < best[0]:
                                best = (d, cx, cz, out)
        if best:
            _, cx, cz, out = best
            wall_lantern(g, cx - GX0, 4 + YOFF, cz - GZ0, out, reach=2, chain=1)
            ox, oz = STEP[out]
            for k in (1, 2):
                G_OWNED.add((cx + ox * k - GX0, cz + oz * k - GZ0))


def wall_top_lights():
    """城壁の上の通路の灯り：外の縁の胸壁の上に、ランタンを8〜10ごと"""
    placed = []
    for X in range(GW):
        for Z in range(GD):
            x, z = X + GX0, Z + GZ0
            if not is_wall(x, z) or rr(x, z) < WALL_OUT - 1.1:
                continue
            if gname(x, z, WALL_TOP + 1) == "air" or gname(x, z, WALL_TOP + 2) != "air":
                continue
            if any(abs(x - a) + abs(z - b) < 8 for (a, b) in placed):
                continue
            gput(x, z, WALL_TOP + 2, lantern(False))
            placed.append((x, z))


def gate_signs():
    """札：城壁沿いの道から、水門の内の口の両わきの柱に"""
    gput(-90, 60, 2, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["水門", "（閉じています）", "", ""]))
    gput(-90, 67, 2, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["湖畔の釣り場", "寄付で", "ひらきます", "（共同事業①）"]))


def build_gate():
    rng.seed(911)
    gate_ground()
    gate_wall()
    gate_channel()
    roof = gatehouse()
    lake_life()
    wall_lights()
    wall_top_lights()
    gate_signs()
    g.finalize()
    return roof


GATE_ROOF = build_gate()


# ================================================================ 湖畔の釣り場（寄付のあとに重ねる。fishing_lakeside.nbt）
# 水門の構造物と同じ所に重ねる。書くマスだけを書く（ほかは元のまま）
LK = World(GW, GH, GD)
LK_OWNED = set()


def lput(x, z, y, state, nbt=None):
    X, Z = x - GX0, z - GZ0
    if 0 <= X < GW and 0 <= Z < GD and -YOFF <= y < GH - YOFF:
        LK.put(X, y + YOFF, Z, state, nbt)
        LK_OWNED.add((X, y + YOFF, Z))


def base_name(x, z, y):
    """重ねる前の名前（水門の構造物。x -84 は釣り堀の構造物）"""
    if x == -84:
        return name_at(x - X0, z - Z0, y)
    return gname(x, z, y)


def lake_top(x, z):
    for y in range(2, -YOFF - 1, -1):
        n = base_name(x, z, y)
        if n not in ("air", "water", "lily_pad", "sugar_cane", "seagrass"):
            return y
    return None


def lk_barrel(x, z, y, facing="up"):
    lput(x, z, y, block("barrel", facing=facing, open=False),
         Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))


LK_DECK = (-106, -101, 72, 80)           # 大会の台（水面の1段上、y -2）
LK_PIER = (-117, -107, 75, 77)           # 湖へのびる長い桟橋
LK_HEAD = (-119, -118, 71, 81)           # 桟橋の先（T）
LK_WALK = (-101, -99, 67, 79)            # 岸の板の道（y 0）：水門の南の岸の道から南へ
HUT = (-97, -93, 74, 78)                 # 受付・計量の小屋（5×5、床 y 1）


def lakeside():
    # ---- 水を通す：釣り堀の古い堰と、水門の堰板と、落とし格子
    for x in (-84, SLUICE_X):
        for z in CH_Z:
            for y in range(WL - 2, WL + 1):
                lput(x, z, y, WATER)
            lput(x, z, WL + 1, AIR)
    for z in LEDGE_Z + CH_Z:
        hi = 5 if z in CH_Z else (4 if z in (62, 65) else 3)
        lo = WL - 1 if z in CH_Z else 1
        for y in range(lo, hi + 1):
            lput(PORT_X, z, y, WATER if y <= WL else AIR)
    # 落とし格子は上げた形：アーチのいちばん上に、鉄格子の下の端だけを見せる
    for z in CH_Z:
        lput(PORT_X, z, 5, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    # ---- 内の札を替える
    lput(-90, 60, 2, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["水門", "（開いています）", "", ""]))
    lput(-90, 67, 2, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["← 湖畔の釣り場", "水門の岸の道を", "くぐって", ""]))
    # ---- 外の水路の南の岸の塀をあけ、岸の板の道（幅3、y 0）を南へ
    for x in (-101, -100):
        lput(x, 67, 1, AIR)
    x0, x1, z0, z1 = LK_WALK
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t = lake_top(x, z)
            lput(x, z, 0, block("spruce_planks") if z > 67 else block("polished_andesite"))
            if t is not None and t < -1 and z > 67:
                for y in range(t + 1, 0):
                    corner = (x in (x0, x1) and (z - z0) % 4 == 0)
                    lput(x, z, y, log("spruce", "y", stripped=True) if corner else AIR)
                    if not corner and base_name(x, z, y) == "water":
                        lput(x, z, y, WATER)
    for z in range(68, z1 + 1):
        lput(x1 + 1, z, 0, log("spruce", "z", stripped=True)) if lake_top(x1 + 1, z) in (None, -1, -2, -3) else None
    # ---- 大会の台（y -2）と、板の道から下りる階段（幅3。y 0 → y -2）
    dx0, dx1, dz0, dz1 = LK_DECK
    for x in range(dx0, dx1 + 1):
        for z in range(dz0, dz1 + 1):
            lput(x, z, -2, block("spruce_planks"))
            for y in range(-1, 2):
                if base_name(x, z, y) not in ("air",):
                    lput(x, z, y, AIR)
            t = lake_top(x, z)
            corner = x in (dx0, dx1) and (z - dz0) % 4 == 0
            if corner and t is not None:
                for y in range(t + 1, -2):
                    lput(x, z, y, log("spruce", "y", stripped=True))
    for z in (74, 75, 76):
        lput(-100, z, -1, stairs("spruce", "east"))
        lput(-100, z, -2, block("spruce_planks"))
        lput(-100, z, 0, AIR)
        lput(-99, z, 0, stairs("spruce", "east"))
    # ---- 長い桟橋と、先の T
    for (ax0, ax1, az0, az1) in (LK_PIER, LK_HEAD):
        for x in range(ax0, ax1 + 1):
            for z in range(az0, az1 + 1):
                lput(x, z, -2, block("spruce_planks"))
                lput(x, z, -1, AIR)
    for x in range(LK_PIER[0], LK_PIER[1] + 1, 4):
        for z in (LK_PIER[2], LK_PIER[3]):
            b = lake_top(x, z)
            for y in range((b if b is not None else WL - 4) + 1, -2):
                lput(x, z, y, log("spruce", "y", stripped=True))
    for z in (71, 76, 81):
        for x in LK_HEAD[:2]:
            b = lake_top(x, z)
            for y in range((b if b is not None else WL - 4) + 1, -2):
                lput(x, z, y, log("spruce", "y", stripped=True))
    # 柱と横木のランタン灯（桟橋の両はし、4ごと）と、先の灯り
    for x in (-110, -114):
        for z in (LK_PIER[2], LK_PIER[3]):
            for y in (-1, 0, 1):
                lput(x, z, y, fence("spruce"))
        for z in range(LK_PIER[2], LK_PIER[3] + 1):
            lput(x, z, 2, log("spruce", "z", stripped=True))
        lput(x, 76, 1, lantern(True))
    for z in (71, 81):
        lput(-119, z, -1, fence("spruce"))
        lput(-119, z, 0, lantern(False))
    lput(-118, 76, -1, fence("spruce"))
    lput(-118, 76, 0, lantern(False))
    # 台の灯り（四すみの柱の上）
    for (x, z) in ((dx0, dz0), (dx0, dz1)):
        for y in (-1, 0):
            lput(x, z, y, fence("spruce"))
        lput(x, z, 1, lantern(False))
    # ---- 受付・計量の小屋（5×5、床 y 1。西の面にカウンター）。まわり（北の庭と、カウンターの前）は道の高さにならす
    hx0, hx1, hz0, hz1 = HUT
    hm = (hz0 + hz1) // 2
    for x in range(hx0 - 1, hx1 + 2):
        for z in range(hz0 - 3, hz1 + 2):
            if in_gatehouse(x, z) or (hx0 <= x <= hx1 and hz0 <= z <= hz1) or is_wall(x, z):
                continue
            t = lake_top(x, z)
            if t is not None and t < 0:
                for y in range(t + 1, 0):
                    lput(x, z, y, block("dirt"))
                lput(x, z, 0, grass())
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            t = lake_top(x, z)
            for y in range((t if t is not None else 0), 1):
                lput(x, z, y, gray_stone())
            edge = x in (hx0, hx1) or z in (hz0, hz1)
            corner = x in (hx0, hx1) and z in (hz0, hz1)
            lput(x, z, 1, block("spruce_planks") if not edge else gray_stone())
            if not edge:
                for y in range(2, 5):
                    lput(x, z, y, AIR)
                lput(x, z, 5, block("spruce_planks"))
                continue
            for y in range(2, 6):
                if y == 5:
                    lput(x, z, y, log("spruce", "z" if x in (hx0, hx1) else "x", stripped=True))
                elif corner or (z == hm and x in (hx0, hx1)):
                    lput(x, z, y, log("spruce", "y", stripped=True))
                else:
                    lput(x, z, y, block("calcite"))
    # カウンター（西の面、真ん中の3つ）：高さ1.5、上は開口と日よけ
    for z in (hm - 1, hm, hm + 1):
        lk_barrel(hx0, z, 2, "west")
        lput(hx0, z, 3, slab("spruce", "bottom"))
        lput(hx0, z, 4, AIR)
        lput(hx0 - 1, z, 5, slab("dark_oak", "top"))
    # 扉（北の面）と前の踏み段、窓（東・南）
    lput(-95, hz0, 2, door("spruce", "north", False))
    lput(-95, hz0, 3, door("spruce", "north", True))
    lput(-95, hz0 - 1, 1, stairs("stone_brick", "south"))
    for (x, z) in ((hx1, hm), (-95, hz1)):
        lput(x, z, 3, pane())
        lput(x, z, 4, pane())
    # 屋根（南北に棟、赤い瓦）
    hroof = Roof(LK, pal, x0=hx0 - GX0, x1=hx1 - GX0, z0=hz0 - GZ0, z1=hz1 - GZ0, y0=6 + YOFF, ridge="z", pitch=2,
                 side_overhang=1, end_overhang=1, ends=("start", "end"), thickness=2, kick=False)
    hroof.build(skip=lambda X, Y, Z: hx0 - GX0 <= X <= hx1 - GX0 and hz0 - GZ0 <= Z <= hz1 - GZ0 and Y < 6 + YOFF)
    for at in (hz0, hz1):
        gable_wall(LK, pal, hroof, at - GZ0, posts=())
    for c in list(LK.blocks):
        LK_OWNED.add(c)
    # 中：大物の記録帳の書見台、計量の桶（水の大釜）、魚の樽、天井から吊る灯りと屋根裏の灯り
    lput(-94, hm - 1, 2, block("lectern", facing="west", has_book=True, powered=False),
         lectern_nbt("大物の記録帳", "ポッカ", RECORD_PAGES))
    lput(-94, hm + 1, 2, block("water_cauldron", level=3))
    lk_barrel(-94, hm, 2, "up")
    lput(-95, hm, 4, lantern(True))
    lput(-95, hm, 6, lantern(False))
    # 看板・旗・額（小屋の西の面。吊り看板の下に生鱈、外の面に大会の札）
    lput(hx0 - 1, hz0, 5, fence("dark_oak"))
    lput(hx0 - 2, hz0, 5, fence("dark_oak"))
    lput(hx0 - 2, hz0, 4, block("spruce_hanging_sign", rotation=4, attached=False, waterlogged=False),
         sign_nbt(["湖畔の", "釣り場", "受付・計量", ""], ["湖畔の", "釣り場", "大会の会場", ""], hanging=True))
    LK.item_frame(hx0 - 1 - GX0, 3 + YOFF, hz1 - GZ0, "west", "cod")
    LK_OWNED.add((hx0 - 1 - GX0, 3 + YOFF, hz1 - GZ0))
    lput(hx0 - 1, hz1, 4, block("spruce_wall_sign", facing="west", waterlogged=False),
         sign_nbt(["釣り大会", "大物の記録は", "中の帳面に", ""]))
    lput(hx1 + 1, hm, 4, block("cyan_wall_banner", facing="east"),
         banner_nbt([("mr", "white"), ("bs", "white"), ("bo", "white")]))
    # 腰かけ（板の道の東の縁。湖を向く）と灯り、魚の樽、つないだ小舟
    for z in (69, 71):
        lput(-98, z, 1, stairs("spruce", "east"))
        lput(-98, z + 1, 1, stairs("spruce", "east"))
    for z in (68, 70, 73):
        lput(-98, z, 1, trapdoor("spruce", "north" if z == 68 else "south", "bottom", True)) if z != 70 else None
    for (x, z, arm) in ((-98, 80, "west"), (-102, 69, "east")):
        t = lake_top(x, z)
        for y in range((t if t is not None else 0) + 1, 1):
            lput(x, z, y, gray_stone())
        lamp_post(LK, x - GX0, 1 + YOFF, z - GZ0, arms=(arm,))
    # 外の水路の口の、南北の岸の塀の端の灯り
    for z in (60, 67):
        lput(-103, z, 2, lantern(False))
    for c in list(LK.blocks):
        LK_OWNED.add(c)
    for (x, z) in ((-102, 80), (-103, 80)):
        lk_barrel(x, z, -1)
    lk_barrel(-102, 80, 0)
    for i, x in enumerate(range(-111, -107)):
        for z in (78, 79):
            end = i in (0, 3)
            st = stairs("spruce", ("west" if i == 0 else "east")) if end else slab("spruce", "bottom")
            lput(x, z, -2, st)
    lput(-109, 78, -1, trapdoor("spruce", "north", "bottom", False))


def item_stack_name(item):
    return item


RECORD_PAGES = [
    "大物の記録帳\n\n湖畔の釣り場が\nひらいた。\n大会の日は、\nここで\n魚をはかるよ。",
    "いちばん大きい魚\n\n―\n\nいちばん珍しい魚\n\n―",
    "湖の主は、\nまだ\nだれも見ていない。\n\nいつか会いたい\nもんだなぁ。\n\n　　ポッカ",
]

lakeside()


# ================================================================ フリーマーケットへの路地（fishing_alley.nbt）
# 構造物の (0, 0, 0) は世界の (x -66, 道のブロック - 1, z 66)。X = x + 66、Y = y + 1、Z = z - 66
AX0, AZ0, AYOFF = -66, 66, 1
AW, AH, AD = 18, 8, 14
al = World(AW, AH, AD)
AL_OWNED = set()


def aput(x, z, y, state, nbt=None):
    X, Z = x - AX0, z - AZ0
    if 0 <= X < AW and 0 <= Z < AD and -AYOFF <= y < AH - AYOFF:
        al.put(X, y + AYOFF, Z, state, nbt)
        AL_OWNED.add((X, Z))


ALLEY = ({(x, z) for x in range(-65, -62) for z in range(67, 78)} |
         {(x, z) for x in range(-62, -48) for z in range(75, 78)} | {(-49, 78), (-50, 78)})


def alley():
    """釣り堀の南東の出口（x -65..-63）から南へ、z 75..77 を東へ、フリーマーケットの西の門（x -48、z 75..78）まで。
    幅3の路地（丸石・砂利・土の道。都市のルール⑤）。灯りは8〜10ごと、曲がり角に道しるべ"""
    rng.seed(912)
    for (x, z) in ALLEY:
        aput(x, z, -1, block("dirt"))
        aput(x, z, 0, alley_paving())
    for (x, z, arm) in ((-66, 70, "east"), (-60, 74, "south"), (-52, 74, "south")):
        aput(x, z, -1, block("dirt"))
        aput(x, z, 0, grass())
        lamp_post(al, x - AX0, 1 + AYOFF, z - AZ0, arms=(arm,))
        AL_OWNED.add((x - AX0, z - AZ0))
        dx, dz = STEP[arm]
        AL_OWNED.add((x + dx - AX0, z + dz - AZ0))
    # 道しるべ（曲がり角の西）
    aput(-66, 76, -1, block("dirt"))
    aput(-66, 76, 0, grass())
    for y in (1, 2, 3):
        aput(-66, 76, y, fence("spruce"))
    aput(-66, 76, 4, slab("dark_oak", "bottom"))
    aput(-65, 76, 3, block("spruce_wall_sign", facing="east", waterlogged=False),
         sign_nbt(["↑ 釣り堀", "→ フリーマーケット", "", ""]))
    aput(-66, 77, 2, block("spruce_wall_sign", facing="south", waterlogged=False),
         sign_nbt(["← 釣り堀", "→ 市場", "", ""])) if False else None
    al.finalize()


alley()


# ================================================================ 第2章の凍った池（fishing_pond_frozen.nbt）
FROZEN = World(W, H, D)
FROZEN_OWNED = set()
ICE_HOLES = ((14, 21), (19, 23), (12, 18), (20, 17))


def light_map(world):
    """ブロックの光（灯り・ろうそく）を広げる（見本の確かめと同じ数え方）"""
    from collections import deque
    from mapgen.world import kind as _kind
    emit = {"lantern": 15, "sea_lantern": 15, "end_rod": 14, "campfire": 15}
    clear = {"bell", "lectern", "pink_petals", "spruce_hanging_sign", "spruce_wall_sign", "cyan_wall_banner",
             "white_wall_banner", "flowering_azalea", "azalea", "water_cauldron", "composter", "cobweb",
             "sugar_cane", "lily_pad", "seagrass", "ladder", "water", "ice"}
    light, q = {}, deque()
    for p_, st in world.blocks.items():
        e = emit.get(st[0], 0)
        if st[0] == "candle":
            e = 3 * int(dict(st[1]).get("candles", 1))
        if e:
            light[p_] = e
            q.append(p_)
    while q:
        x, y, z = q.popleft()
        lv = light[(x, y, z)] - 1
        if lv <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            st = world.get(*n)
            if not (0 <= n[0] < world.w and 0 <= n[1] < world.h and 0 <= n[2] < world.d):
                continue
            if st is not None and _kind(st[0]) == "cube" and st[0] not in clear:
                continue
            if light.get(n, 0) < lv:
                light[n] = lv
                q.append(n)
    return light


def frozen_overlay():
    """第2章（Phase 0-A：魚図鑑の「一部が凍った釣り堀の氷下の魚」）：池の北の半分（古い貯水池の四角と北の入り江）の
    水面を氷にする。灯りのそば（明るさ 11 より上）は凍らない（とける）。釣り穴を4つ残す【補完】"""
    lm = light_map(w)
    for (u, v) in sorted(WATER_CELLS):
        if not (v <= 27 and u >= 8) or (u, v) in ICE_HOLES:
            continue
        if name_at(u, v, WL) != "water" or get(u, v, WL + 1) is not None and name_at(u, v, WL + 1) != "lily_pad":
            continue
        if lm.get((u, WL + YOFF, v), 0) > 10:
            continue
        FROZEN.put(u, WL + YOFF, v, block("ice"))
        FROZEN_OWNED.add((u, WL + YOFF, v))
        if name_at(u, v, WL + 1) == "lily_pad":
            FROZEN.put(u, WL + 1 + YOFF, v, AIR)
            FROZEN_OWNED.add((u, WL + 1 + YOFF, v))


frozen_overlay()


# ---------------------------------------------------------------- 確かめる世界（並べた写し）と行き先
# 並べた写しの座標：CX = x + 131、CY = y + 9、CZ = z - 23（x・z は世界の座標）
CW_, CH_, CD_ = 83, 34, 73


def checked_world(which="base"):
    """確かめる世界：釣り堀・水門と湖・路地を並べた写し。which は base（はじめ）・lakeside（湖畔の釣り場を重ねたあと）・
    frozen（第2章の氷を重ねたあと）"""
    cw = World(CW_, CH_, CD_)

    def copy_in(world, cols, ox, oy, oz, per_cell=False):
        for (x, y, z), st in world.blocks.items():
            if per_cell or (x, z) in cols:
                cw.blocks[(x + ox, y + oy, z + oz)] = st
                if (x, y, z) in world.nbt:
                    cw.nbt[(x + ox, y + oy, z + oz)] = world.nbt[(x, y, z)]
        for e in world.entities:
            import copy
            e2 = copy.copy(e)
            e2.pos = (e.pos[0] + ox, e.pos[1] + oy, e.pos[2] + oz)
            e2.cell = (e.cell[0] + ox, e.cell[1] + oy, e.cell[2] + oz)
            cw.entities.append(e2)

    copy_in(w, POND_COLS, X0 + 131, 0, Z0 - 23)
    copy_in(g, G_OWNED, GX0 + 131, 0, GZ0 - 23)
    copy_in(al, AL_OWNED, AX0 + 131, YOFF - AYOFF, AZ0 - 23)
    overlay, oxyz = None, None
    if which == "lakeside":
        overlay, owned_cells, oxyz = LK, LK_OWNED, (GX0 + 131, 0, GZ0 - 23)
    elif which == "frozen":
        overlay, owned_cells, oxyz = FROZEN, FROZEN_OWNED, (X0 + 131, 0, Z0 - 23)
    if overlay is not None:
        ox, oy, oz = oxyz
        for c in owned_cells:
            x, y, z = c
            st = overlay.blocks.get(c)
            t = (x + ox, y + oy, z + oz)
            cw.nbt.pop(t, None)
            if st is None:
                cw.blocks.pop(t, None)
            else:
                cw.blocks[t] = st
                if c in overlay.nbt:
                    cw.nbt[t] = overlay.nbt[c]
        for e in overlay.entities:
            import copy
            e2 = copy.copy(e)
            e2.pos = (e.pos[0] + ox, e.pos[1] + oy, e.pos[2] + oz)
            e2.cell = (e.cell[0] + ox, e.cell[1] + oy, e.cell[2] + oz)
            cw.entities.append(e2)
    return cw


def P2W(u, v, y):
    """釣り堀の平面図の (u, v, 足もと) → 世界の (x, z, 足もと)"""
    return (u + X0, v + Z0, y)


GOALS = {
    "入口の門": P2W(34, 33, 1), "広場の真ん中": P2W(31, 33, 1), "釣り大会の札の前": P2W(30, 31, 1),
    "上の台（北）": P2W(27, 16, 2), "カウンターの前": P2W(28, 17, 2), "ポッカの立つ所（カウンターの内側）": P2W(30, 17, 2),
    "店の中の手引きの書見台の前": P2W(31, 21, 2), "店の扉の前（台）": P2W(31, 25, 2),
    "下の桟橋の真ん中": P2W(16, 26, -1), "桟橋の先（T）": P2W(11, 24, -1), "水辺の小屋の中": P2W(21, 31, -1),
    "アーチの下の桟橋の先": P2W(16, 19, -1), "北の遊歩道の西の端": P2W(2, 12, 1), "石の橋の上": P2W(2, 19, 1),
    "西の小道の南の端": P2W(2, 27, 1), "南の遊歩道": P2W(20, 40, 1), "南西の古い堰の上": P2W(11, 42, 1),
    "南東の出口": P2W(31, 43, 1), "記憶の庭の東の門の前": P2W(24, 1, 1), "記憶の庭の道の東の端（環状路）": P2W(35, 1, 1),
    "北の入口の道": P2W(26, 6, 1),
    "水門の内の口（城壁沿いの道）": (-89, 65, 1), "水門のアーチの中（内の半分）": (-93, 66, 1),
    "路地の曲がり角": (-64, 76, 1), "フリーマーケットの西の門の前": (-49, 76, 1),
}
GOALS_LAKESIDE = {
    "水門のアーチの中（外の半分）": (-97, 66, 1), "水門の外の口": (-102, 66, 1), "岸の板の道": (-100, 72, 1),
    "大会の台": (-104, 77, -1), "長い桟橋の先（T）": (-118, 74, -1), "受付のカウンターの前": (-98, 76, 1),
    "受付の小屋の中": (-95, 76, 2),
}


# ---------------------------------------------------------------- 見本の絵（構造物の中の座標。X = u、Y = y + 9、Z = v）
APRON = 30                      # 見本の絵だけ：まわりに草の地面を敷く幅（池が地面の平らな面より低いため）


def preview_world(*parts):
    """見本の絵の世界：構造物を、釣り堀の構造物からずらす量で並べ、まわりに草の地面（Y 9）を敷く。
    parts は (世界, (dx, dy, dz), 書くマスの集まり (X, Z) か None)。地面の平らな面は Y 0 に置く"""
    import copy
    pw = World(W + APRON * 2 + 60, H + 30, D + APRON * 2 + 60)
    ox, oz = APRON + 40, APRON
    covered = set()
    for world, (dx, dy, dz), cols in parts:
        if cols is None:
            cols = {(x, z) for (x, _, z) in world.blocks}
        covered |= {(x + dx + ox, z + dz + oz) for (x, z) in cols}
    for gx in range(pw.w):
        for gz in range(pw.d):
            if (gx, gz) not in covered:
                pw.blocks[(gx, YOFF, gz)] = block("grass_block", snowy=False)
    for world, (dx, dy, dz), cols in parts:
        for (x, y, z), st in world.blocks.items():
            pw.blocks[(x + dx + ox, y + dy, z + dz + oz)] = st
            if (x, y, z) in world.nbt:
                pw.nbt[(x + dx + ox, y + dy, z + dz + oz)] = world.nbt[(x, y, z)]
        for e in world.entities:
            e2 = copy.copy(e)
            e2.pos = (e.pos[0] + dx + ox, e.pos[1] + dy, e.pos[2] + dz + oz)
            e2.cell = (e.cell[0] + dx + ox, e.cell[1] + dy, e.cell[2] + dz + oz)
            pw.entities.append(e2)
    return pw, (ox, oz)


GATE_AT = (GX0 - X0, 0, GZ0 - Z0)        # 水門の構造物を、釣り堀の構造物からずらす量（-36, 0, 25）
POND_COLS = {(u, v) for u in range(W) for v in range(D) if mine(u, v)}


ALLEY_AT = (AX0 - X0, YOFF - AYOFF, AZ0 - Z0)   # 路地の構造物を、釣り堀の構造物からずらす量


def applied(base, overlay, cells):
    """重ねる形を、元の世界の写しに重ねる（書くマスだけ）"""
    out = World(base.w, base.h, base.d)
    out.blocks = dict(base.blocks)
    out.nbt = dict(base.nbt)
    out.entities = list(base.entities) + list(overlay.entities)
    for c in cells:
        out.nbt.pop(c, None)
        if c in overlay.blocks:
            out.blocks[c] = overlay.blocks[c]
            if c in overlay.nbt:
                out.nbt[c] = overlay.nbt[c]
        else:
            out.blocks.pop(c, None)
    return out


def base_parts(which="base"):
    pond = applied(w, FROZEN, FROZEN_OWNED) if which == "frozen" else w
    gate = applied(g, LK, LK_OWNED) if which == "lakeside" else g
    if which == "lakeside":
        # 釣り堀の古い堰（x -84）は、水門の構造物の範囲の端（X 47）に重ねて書く
        pond = World(w.w, w.h, w.d)
        pond.blocks = dict(w.blocks)
        pond.nbt = dict(w.nbt)
        pond.entities = list(w.entities)
        for (X, Y, Z) in LK_OWNED:
            if X + GX0 == -84:
                u, v = -84 - X0, Z + GZ0 - Z0
                st = LK.blocks.get((X, Y, Z))
                if st is None:
                    pond.blocks.pop((u, Y, v), None)
                else:
                    pond.blocks[(u, Y, v)] = st
    return [(pond, (0, 0, 0), POND_COLS), (gate, GATE_AT, G_OWNED), (al, ALLEY_AT, AL_OWNED)]


VIEWS = {
    "entrance": ((44.0, 13.0, 34.0), (22.0, 9.0, 25.0)),      # 環状路の南西の角から、入口の門と池
    "aerial": ((20.0, 44.0, 10.0), (15.0, 5.0, 29.0)),          # 上から（北の高い所）：池の全体、右に釣り具屋、奥に城壁と水門
    "aerial_nw": ((-10.0, 30.0, 0.0), (20.0, 6.0, 28.0)),      # 上から（北西。記憶の庭の側）
    "deck": ((27.6, 12.9, 21.6), (9.0, 7.0, 31.0)),            # 上の台から池（南西）
    "pier": ((11.5, 9.62, 26.5), (28.0, 11.0, 20.0)),          # 下の桟橋の先から、台と釣り具屋
    "arch": ((16.5, 11.62, 12.5), (16.5, 7.0, 24.0)),          # 北の遊歩道から、アーチの下の桟橋
    "spring": ((8.4, 10.4, 22.4), (5.5, 8.6, 16.6)),           # 入り江の南東の岸から、湧き口と石の橋
    "west": ((2.0, 11.62, 28.5), (25.0, 10.0, 22.0)),           # 西の小道（桜とオークの間）から、池と台と釣り具屋
    "south": ((20.5, 11.62, 41.5), (16.0, 8.0, 18.0)),         # 南の遊歩道から北へ
    "shop": ((26.5, 12.62, 19.5), (30.5, 11.4, 17.5)),         # 台から釣り具屋のカウンター
    "shed": ((16.5, 9.62, 26.5), (21.5, 8.0, 31.0)),           # 下の桟橋から水辺の小屋
    "gate_in": ((14.0, 11.62, 47.0), (-1.0, 12.0, 40.0)),      # 釣り堀の南西（城壁沿いの道）から、水門の内側
    "gate_out": ((-18.0, 11.62, 52.0), (-1.0, 13.0, 41.0)),    # 城壁の外（湖の岸）から、水門の外の面
    "lake": ((-45.0, 45.0, 85.0), (-15.0, 6.0, 48.0)),         # 上から（南西）、湖と水門と釣り堀
    "alley": ((36.0, 11.62, 60.0), (30.0, 9.0, 45.0)),         # 路地の曲がり角から、釣り堀の南東の出口へ
}
VIEWS_LAKESIDE = {
    "lakeside": ((-8.0, 11.62, 47.0), (-18.0, 7.0, 54.0)),     # 水門の外の口から、岸の板の道と大会の台と長い桟橋
    "lakeside_pier": ((-22.0, 9.62, 52.5), (-2.0, 10.0, 52.5)),  # 長い桟橋の先から、受付の小屋と水門
    "lakeside_aerial": ((-48.0, 40.0, 82.0), (-12.0, 6.0, 50.0)),  # 上から（南西）、湖畔の釣り場
    "gate_open": ((14.0, 11.62, 47.0), (-1.0, 12.0, 40.0)),    # 水門の内側（落とし格子が上がり、堰が開いた）
}
VIEWS_FROZEN = {
    "frozen": ((22.5, 15.5, 13.0), (13.0, 6.0, 21.5)),         # 第2章：北の遊歩道の上から、凍った池の北の半分と釣り穴
}


def render_views(out_dir, width=960, height=540, fov=70, only=None):
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    for which, views in (("base", VIEWS), ("lakeside", VIEWS_LAKESIDE), ("frozen", VIEWS_FROZEN)):
        todo = [n for n in views if not only or n in only]
        if not todo:
            continue
        pw, (ox, oz) = preview_world(*base_parts(which))
        sc = Scene(pw, ground_y=0)
        for name in todo:
            eye, target = views[name]
            e = (eye[0] + ox, eye[1], eye[2] + oz)
            t = (target[0] + ox, target[1], target[2] + oz)
            sc.render(eye=e, target=t, width=width, height=height, fov=fov,
                      path=os.path.join(out_dir, "fishing_%s.png" % name))
            print("描きました:", name)
    pw, (ox, oz) = preview_world(*base_parts())
    for key, y_cut in (("cut0", YOFF + 1), ("cut1", YOFF + 3)):
        if only and key not in only:
            continue
        cw = World(pw.w, pw.h, pw.d)
        cw.blocks = {p: st for p, st in pw.blocks.items() if p[1] < y_cut}
        cw.nbt = {p: n for p, n in pw.nbt.items() if p[1] < y_cut}
        cw.entities = [e for e in pw.entities if e.cell[1] < y_cut]
        Scene(cw, ground_y=0).render(eye=(18.0 + ox, 90.0, 22.0 + oz), target=(18.0 + ox, 0.0, 21.95 + oz),
                                     width=width, height=width, fov=30, path=os.path.join(out_dir, "fishing_%s.png" % key))
        print("描きました:", key)


# ---------------------------------------------------------------- 書き出しと確かめの文字の図
def owned(x, y, z):
    return mine(x, z)


def plan_text():
    """上から見た文字の図（いちばん上のブロック）"""
    rows = []
    for v in range(D):
        line = ""
        for u in range(W):
            if not mine(u, v):
                line += "G" if in_garden(u, v) else "="
                continue
            top = None
            for yy in range(H - YOFF - 1, -YOFF - 1, -1):
                st = get(u, v, yy)
                if st:
                    top = (st[0], yy)
                    break
            n = top[0] if top else "air"
            ch = {"water": "~", "grass_block": ".", "dirt_path": "p", "coarse_dirt": "p", "gravel": ",", "sand": ",",
                  "clay": ",", "polished_diorite": "A", "diorite": "a", "calcite": "a",
                  "chiseled_stone_bricks": "A", "stone_bricks": "q", "cobblestone": "q", "andesite": "q",
                  "polished_andesite": "Q", "cracked_stone_bricks": "q", "rooted_dirt": "."}.get(n, "?")
            line += ch
        rows.append("%3d %s" % (v, line))
    return "\n".join(["    " + "".join(str(u % 10) for u in range(W))] + rows)


CI_SHOTS = ["entrance", "aerial", "deck", "pier", "arch", "spring", "west", "shop", "gate_in", "gate_out", "alley",
            "lakeside", "lakeside_pier", "gate_open", "frozen"]


def ci_views():
    """自動の確認の写真の目と見る所（釣り堀を置いた所から。釣り堀の構造物は道の9下に置くので、高さは Y - 9）。
    CiScreenshots の MAP_VIEWS と同じ。lakeside からの3枚は湖畔の釣り場を重ねたあと、frozen は第2章の氷を重ねたあと"""
    allv = dict(VIEWS)
    allv.update(VIEWS_LAKESIDE)
    allv.update(VIEWS_FROZEN)
    out = []
    for shot in CI_SHOTS:
        (ex, ey, ez), (tx, ty, tz) = allv[shot]
        out.append((shot, (ex, ey - YOFF, ez), (tx, ty - YOFF, tz)))
    return out


def save_all(out):
    """5つの構造物を書き出す"""
    res = []
    path = os.path.join(out, "fishing_pond.nbt")
    n = w.save_structure(path, owned=lambda x, y, z: (x, z) in POND_COLS)
    res.append((path, len(w.blocks), n, len(w.entities), (W, H, D)))
    path = os.path.join(out, "water_gate.nbt")
    n = g.save_structure(path, owned=lambda x, y, z: (x, z) in G_OWNED)
    res.append((path, len(g.blocks), n, len(g.entities), (GW, GH, GD)))
    path = os.path.join(out, "fishing_alley.nbt")
    n = al.save_structure(path, owned=lambda x, y, z: (x, z) in AL_OWNED)
    res.append((path, len(al.blocks), n, len(al.entities), (AW, AH, AD)))
    path = os.path.join(out, "fishing_lakeside.nbt")
    n = LK.save_structure(path, owned=lambda x, y, z: (x, y, z) in LK_OWNED)
    res.append((path, len(LK_OWNED), n, len(LK.entities), (GW, GH, GD)))
    path = os.path.join(out, "fishing_pond_frozen.nbt")
    n = FROZEN.save_structure(path, owned=lambda x, y, z: (x, y, z) in FROZEN_OWNED)
    res.append((path, len(FROZEN_OWNED), n, len(FROZEN.entities), (W, H, D)))
    return res


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
    if "--plan" in args:
        args.remove("--plan")
        print(plan_text())
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    for path, nb, n, ne, size in save_all(out):
        print("書き出しました:", path, "ブロック", nb, "種類", n, "もの", ne, "大きさ", size)
    if render_dir:
        render_views(render_dir, only=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_fishing_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
