"""塔の6階「凍結した村」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor6.json）を書き出す。

Phase 0-A 9章の6階（確定）：一瞬のまま凍りついた雪の村。母の手を取ろうとする子ども、市場で値切る商人などが氷像になっている。
空にオーロラ。雑魚は霜狼・氷像の残響（村人の氷像の一部が近づくと動く）・凍て鳥（吹雪を起こす）・霜の欠け熊（大型）。
ボスは村長の残響オルド（家々から「記憶の結晶」を3つ集めて渡すと、思い出して戦いをやめる）。ギミックは記憶の解凍（熱の結晶を
運んで氷像を溶かすと、その日の村の短い場面が見える。運ぶ人は足が遅くなるので、ほかの人が護衛する）。古代記録は「村の子どもの
日記」。物語は6日目（ロストがコネリアにも及ぶ）。ロストの侵食：氷像の顔が空白。第2章の色は氷青・白銀・オーロラの緑紫。

使い方:
  python3 tools/mapgen/build_floor6.py                        # Modの structures/map/floor6/ と floors/floor6.json を書き直す
  python3 tools/mapgen/build_floor6.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

決まり（11月25日【補完】。docs/phase8_village.md）：
  - 624×624（5階より広い）、高さ80。空は開いていて（オーロラはクライアントが描く）、まわりは雪の山。
  - 区画：南の村はずれの門 → 雪の通り（家並みと宿屋「雪灯亭」）→ 市場の広場（パン屋）→ 西：子どもの家・凍った川と水車小屋・
    川向こうの林（霜狼の群れ長）／北西：鍛冶屋と教会／東：牧場と納屋（霜の欠け熊の主）／北東：凍った池と氷釣りの小屋 →
    北の通り → 凍りついた門（封印）→ 村長の家と雪の広場（ボス）
  - 記憶の氷像4つ（市場の値切り・母と子・鐘を鳴らす若者・村長の呼びかけ）を、熱の炉3つ（宿屋の暖炉・パン屋のかまど・
    鍛冶屋の炉）からもらう熱の結晶で溶かす。4つとも溶け、名のある魔物2体を倒すと、封印を通れる（statues+guardians）
  - ボスの広場のまわりの3つの家（西の家・東の家・村長の家）に、戦いの間「記憶の結晶」が現れる
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapgen.world import DATA_VERSION, block, sign_nbt  # noqa: E402
from mapgen.tower import NEXT_DX, next_circle  # noqa: E402

import nbtlib  # noqa: E402
from nbtlib.tag import Compound, Float, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

FLOOR = 6
SIZE = 624
HEIGHT = 80
PIECE = 48
ORIGIN = (24576 - SIZE // 2, 60, -SIZE // 2)
GROUND = 16             # 村の地面のブロック（立つのは 17）
ICE_Y = GROUND - 1      # 凍った川・池の氷の面
CHEST_LOOT = "amemaru:chests/floor6_village"

ENTRY = (312, 596)
GATE_Z = 574
MARKET = (312, 392)
MARKET_R = 23
ARENA = (312, 100)
ARENA_R = 26            # 広場の敷石の半径
PLAZA = (262, 60, 362, 140)   # 広場の囲い（x0, z0, x1, z1）
SEAL_Z = 140
POND = (478, 188)
POND_R = 24

# 凍った川（西）。折れ線と、氷の半幅
RIVER = [(170, -4), (176, 60), (158, 140), (166, 220), (150, 300), (156, 380), (140, 470), (152, 560), (146, 628)]
RIVER_HALF = 7.0

# 道（折れ線, 半幅）
STREET = [(312, 600), (312, 368)]
NORTH_ROAD = [(312, 368), (312, 146)]
WEST_LANE = [(289, 398), (262, 410), (238, 410), (210, 392), (188, 352), (176, 322), (130, 322), (96, 330)]
NW_LANE = [(298, 352), (282, 304), (262, 276), (236, 246), (226, 236)]
EAST_LANE = [(335, 392), (380, 374), (420, 356), (440, 352)]
NE_LANE = [(326, 340), (380, 296), (436, 236), (466, 212)]
SOUTH_BRIDGE_LANE = [(306, 520), (240, 520), (190, 524), (110, 520), (84, 512)]
SE_LANE = [(315, 506), (360, 500), (392, 486), (424, 488)]
WEST_NORTH = [(130, 322), (112, 260), (100, 196)]
LOOKOUT_LANE = [(436, 236), (432, 168), (430, 128)]
PATHS = [(STREET, 3.6), (NORTH_ROAD, 3.0), (WEST_LANE, 2.2), (NW_LANE, 2.2), (EAST_LANE, 2.2), (NE_LANE, 2.0),
         (SOUTH_BRIDGE_LANE, 1.8), (SE_LANE, 1.8), (WEST_NORTH, 1.8), (LOOKOUT_LANE, 1.6)]
LOOKOUT = (430, 118)

# 家（id, x0, z0, 幅, 奥行き, 戸口の向き, 種類, 階の数）
HOUSES = [
    # 雪の通りの西
    ("w1", 288, 548, 12, 10, "e", "home", 1), ("w2", 288, 530, 12, 10, "e", "home", 1),
    ("w3", 290, 500, 10, 12, "e", "home", 2), ("w4", 288, 478, 12, 10, "e", "home", 1),
    ("w5", 290, 436, 10, 12, "e", "home", 1), ("w6", 290, 456, 10, 10, "e", "shop", 1),
    # 雪の通りの東（宿屋「雪灯亭」は2階建て）
    ("e1", 324, 548, 12, 10, "w", "home", 1), ("e2", 324, 528, 12, 10, "w", "home", 1),
    ("e3", 324, 500, 12, 12, "w", "home", 1), ("inn", 324, 454, 16, 20, "w", "inn", 2),
    ("e4", 324, 432, 12, 10, "w", "shop", 1),
    # 市場のまわり
    ("bakery", 338, 380, 12, 12, "w", "bakery", 1), ("m1", 272, 380, 12, 10, "e", "shop", 1),
    ("m2", 332, 350, 12, 10, "s", "home", 1), ("m3", 280, 350, 12, 10, "s", "home", 1),
    # 西の小道（子どもの家）・川のそば
    ("child", 228, 418, 10, 9, "n", "child", 1), ("wl1", 250, 378, 10, 9, "s", "home", 1),
    ("wl2", 196, 404, 10, 10, "e", "home", 1),
    # 川向こう（西の林の小さな集落）
    ("x1", 86, 296, 10, 10, "s", "home", 1), ("x2", 70, 346, 10, 9, "e", "home", 1),
    # 北の通り
    ("n1", 290, 316, 10, 10, "e", "home", 1), ("n2", 324, 312, 10, 12, "w", "home", 1),
    ("n3", 290, 282, 10, 10, "e", "home", 2), ("n4", 324, 276, 10, 12, "w", "home", 1),
    ("n5", 288, 222, 12, 10, "e", "home", 1), ("n6", 324, 216, 12, 10, "w", "home", 1),
    ("n7", 290, 186, 10, 10, "e", "home", 1),
    # 北西：鍛冶屋
    ("smithy", 260, 254, 14, 11, "s", "smithy", 1),
    # 東の小道・牧場の手前
    ("el1", 372, 380, 10, 9, "n", "home", 1), ("el2", 400, 340, 10, 9, "s", "home", 1),
    # 村のはずれ：南東の農家・南西の木こりの小屋・北西の猟師の家・池のそば・北の通りの東・西の小道
    ("f1", 392, 470, 12, 10, "w", "home", 1), ("f2", 424, 494, 10, 10, "n", "home", 1), ("f3", 398, 506, 12, 9, "n", "shop", 1),
    ("l1", 66, 498, 12, 10, "e", "shop", 1), ("h1", 82, 150, 10, 10, "s", "home", 1), ("h2", 112, 174, 10, 9, "w", "home", 1),
    ("p1", 444, 160, 10, 9, "e", "home", 1), ("n8", 324, 186, 10, 10, "w", "home", 1), ("wl3", 256, 420, 10, 9, "n", "home", 1),
    ("wl4", 214, 362, 10, 9, "s", "home", 1),
    # 雪の広場（ボス）の西と東の家（戦いの間、記憶の結晶が現れる）と、村長の家
    ("plaza_w", 266, 93, 13, 15, "e", "crystal", 1), ("plaza_e", 346, 93, 13, 15, "w", "crystal", 1),
    ("mayor", 297, 62, 31, 15, "s", "mayor", 2),
]

# 熱の炉（id, 名前, 家）と、記憶の氷像（id, 名前, 場面のイベント）
HEARTHS = [("inn", "雪灯亭の暖炉"), ("bakery", "パン屋のかまど"), ("smithy", "鍛冶屋の炉")]
MEMORIES = [
    ("market", "市場の値切り", (300, 392), 90.0),
    ("mother", "母と子", (232, 410), 180.0),
    ("ringer", "鐘を鳴らす若者", (224, 236), 90.0),
    ("mayor_call", "村長の呼びかけ", (312, 162), 0.0),
]


class Rand:
    def __init__(self, seed):
        self.seed = seed

    def at(self, *key):
        h = self.seed
        for k in key:
            h = (h * 1000003) ^ (int(k) & 0xFFFFFFFF)
            h = (h ^ (h >> 13)) * 0x5BD1E995 & 0xFFFFFFFF
        return ((h ^ (h >> 15)) & 0xFFFFFF) / float(0x1000000)

    def pick(self, table, *key):
        total = sum(w for _, w in table)
        r = self.at(*key) * total
        for name, w in table:
            r -= w
            if r < 0:
                return name
        return table[-1][0]


R = Rand(20261125 + 6)

ROCK = [("stone", 5), ("andesite", 2), ("tuff", 1), ("cobblestone", 1)]
STREET_STONE = [("cobblestone", 4), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("andesite", 2), ("gravel", 1)]
LANE_STONE = [("coarse_dirt", 2), ("cobblestone", 2), ("gravel", 2), ("packed_mud", 1)]
ICE = [("ice", 5), ("packed_ice", 3), ("blue_ice", 1)]


def thing(index=0, group=""):
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


def smooth_noise(n, cell, seed):
    """n×n の、なめらかな値のノイズ（格子の大きさ cell）。値は −1〜1"""
    rng = np.random.default_rng(seed)
    g = n // cell + 3
    grid = rng.uniform(-1.0, 1.0, (g, g))
    xs = np.arange(n) / cell
    i = np.floor(xs).astype(int)
    fr = xs - i
    fr = fr * fr * (3 - 2 * fr)
    a = grid[i][:, i]
    b = grid[i + 1][:, i]
    c = grid[i][:, i + 1]
    d = grid[i + 1][:, i + 1]
    fx = fr[:, None]
    fz = fr[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def polyline_distance(points, n=SIZE):
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    best = np.full((n, n), 1e9)
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        dx, dz = x1 - x0, z1 - z0
        seg = math.hypot(dx, dz)
        t = np.clip(((X - x0) * dx + (Z - z0) * dz) / (seg * seg), 0.0, 1.0)
        d = np.hypot(X - (x0 + t * dx), Z - (z0 + t * dz))
        best = np.minimum(best, d)
    return best


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Floor:
    def __init__(self):
        self.v = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)
        self.states = [None]
        self.index = {}
        self.nbt = {}
        self.height = None
        self.reserved = np.zeros((SIZE, SIZE), dtype=bool)   # 木や飾りを置かない所（家・道・広場）
        self.path = np.zeros((SIZE, SIZE), dtype=np.uint8)    # 1：大通り 2：小道
        self.river = None
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.signs = []
        self.seal_cells = []
        self.story_zones = []
        self.statues = []          # (x, y, z, skin, pose, yaw, scale, memory)
        self.hearths = []          # (id, name, (x, y, z))
        self.memories = []         # (id, name, (x, y, z), yaw)
        self.crystal_spots = []
        self.houses = {}
        self.fences = []
        self.panes = []
        self.travel_stone = None
        self.tablet = None
        self.center = None

    def idx(self, state):
        i = self.index.get(state)
        if i is None:
            i = len(self.states)
            self.states.append(state)
            self.index[state] = i
        return i

    def put(self, x, y, z, state, nbt=None):
        if not (0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE):
            return
        self.v[x, y, z] = 0 if state is None else self.idx(state)
        if nbt is not None:
            self.nbt[(x, y, z)] = nbt
        else:
            self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        if not (0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE):
            return None
        return self.states[self.v[x, y, z]]

    def name_at(self, x, y, z):
        s = self.get(x, y, z)
        return None if s is None else s[0]

    def empty(self, x, y, z):
        return 0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE and self.v[x, y, z] == 0

    def top(self, x, z):
        return int(self.height[x, z])


def fence(f, x, y, z, mat="spruce_fence"):
    f.put(x, y, z, block(mat, north=False, south=False, east=False, west=False, waterlogged=False))
    f.fences.append((x, y, z))


def pane(f, x, y, z, mat="light_blue_stained_glass_pane"):
    f.put(x, y, z, block(mat, north=False, south=False, east=False, west=False, waterlogged=False))
    f.panes.append((x, y, z))


def stairs(mat, facing, half="bottom"):
    return block(mat + "_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(mat, kind="bottom"):
    return block(mat + "_slab", type=kind, waterlogged=False)


def snow_layer(n=1):
    return block("snow", layers=n)


def lantern(hanging=False):
    return block("lantern", hanging=hanging, waterlogged=False)


def sign(f, x, y, z, rotation, lines):
    f.put(x, y, z, block("spruce_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))
    f.signs.append((x, y, z))


def wall_sign(f, x, y, z, facing, lines):
    f.put(x, y, z, block("spruce_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))
    f.signs.append((x, y, z))


def chest(f, x, y, z, facing, guardian=""):
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing), thing(len(f.chests), ""))
    f.chests.append(((x, y, z), guardian))


def waypoint(f, wid, name, x, y, z, yaw, look_at=None):
    index = len(f.waypoints)
    f.put(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    if look_at is not None:
        yaw = round(math.degrees(math.atan2(-(look_at[0] - (ax + 0.5)), look_at[1] - (az + 0.5))), 1)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


def statue(f, x, y, z, skin, pose, yaw, scale=1.0, memory=""):
    """凍った村人の氷像（ブロックの実体。tower の VillageStatueBlock が人の形に描く。顔は白く抜けている＝ロストの侵食）"""
    tag = Compound({"id": String("amemaru:village_statue"), "skin": String(skin), "pose": String(pose), "yaw": Float(yaw),
                    "scale": Float(scale), "memory": String(memory)})
    f.put(x, y, z, block("amemaru:village_statue"), tag)
    f.statues.append((x, y, z, skin, pose, yaw, scale, memory))


# ================================================================ 地形

def terrain(f):
    n = SIZE
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    h = GROUND + 1.2 * smooth_noise(n, 64, 61) + 0.7 * smooth_noise(n, 23, 62) + 0.3 * smooth_noise(n, 9, 63)
    edge = np.minimum(np.minimum(X, n - X), np.minimum(Z, n - Z))
    # まわりの雪の山（よじ登れない高さまで）
    ridge = 40.0 + 10.0 * smooth_noise(n, 31, 64) + 4.0 * smooth_noise(n, 11, 65)
    h += ridge * (1.0 - smoothstep(0.0, 52.0, edge)) ** 1.5
    # 東の林の丘・北西の教会の丘（なだらか）
    h += 5.0 * smoothstep(500, 600, X)
    h += 3.0 * (1.0 - smoothstep(20, 70, np.hypot(X - 186, Z - 236)))
    # 村の中：平らに（道と家の敷地は GROUND）
    path_d = np.full((n, n), 1e9)
    for pts, half in PATHS:
        d = polyline_distance(pts) - half
        path_d = np.minimum(path_d, d)
        f.path[(d <= 0) & (f.path == 0)] = 1 if half >= 3.0 else 2
    w = 1.0 - smoothstep(0.0, 8.0, path_d)
    h = h * (1 - w) + GROUND * w
    # 広い所（市場・入口・教会の前・牧場の入口・広場）
    for (cx, cz, r0, r1) in ((MARKET[0], MARKET[1], MARKET_R + 2, MARKET_R + 12), (ENTRY[0], ENTRY[1] - 8, 16, 26),
                             (230, 236, 16, 26), (440, 350, 10, 18), (ARENA[0], ARENA[1], 48, 62), (POND[0], POND[1], 30, 44)):
        d = np.hypot(X - cx, Z - cz)
        ww = 1.0 - smoothstep(r0, r1, d)
        h = h * (1 - ww) + GROUND * ww
    for hid, x0, z0, w_, d_, _door, _kind, _st in HOUSES:
        bd = np.maximum(np.abs(X - (x0 + w_ / 2.0)) - w_ / 2.0, np.abs(Z - (z0 + d_ / 2.0)) - d_ / 2.0)
        ww = 1.0 - smoothstep(1.0, 7.0, bd)
        h = h * (1 - ww) + GROUND * ww
    # 牧場・納屋の囲い
    bd = np.maximum(np.abs(X - 480) - 42, np.abs(Z - 340) - 42)
    ww = 1.0 - smoothstep(0.0, 8.0, bd)
    h = h * (1 - ww) + GROUND * ww
    # 凍った川（氷の面は ICE_Y。岸はなだらか）と池
    rd = polyline_distance(RIVER)
    f.river = rd
    bank = ICE_Y + 1.0
    h = np.where(rd < RIVER_HALF + 8.0, bank + (np.maximum(h, bank) - bank) * smoothstep(RIVER_HALF + 1.0, RIVER_HALF + 8.0, rd), h)
    pd = np.hypot(X - POND[0], Z - POND[1])
    f.pond = pd
    h = np.where(pd < POND_R + 6.0, bank + (np.maximum(h, bank) - bank) * smoothstep(POND_R, POND_R + 6.0, pd), h)
    f.height = np.clip(np.round(h), 4, HEIGHT - 6).astype(int)
    f.height = np.where(rd < RIVER_HALF, ICE_Y, f.height)
    f.height = np.where(pd < POND_R, ICE_Y, f.height)
    # 道・家・広場は、きっちり GROUND
    f.height = np.where(f.path > 0, np.maximum(GROUND, np.where(rd < RIVER_HALF + 1, f.height, GROUND)), f.height)
    for hid, x0, z0, w_, d_, _door, _kind, _st in HOUSES:
        f.height[x0 - 2:x0 + w_ + 2, z0 - 2:z0 + d_ + 2] = GROUND
    px0, pz0, px1, pz1 = PLAZA
    f.height[px0:px1 + 1, pz0:pz1 + 1] = GROUND
    mx, mz = MARKET
    f.height[mx - MARKET_R - 2:mx + MARKET_R + 3, mz - MARKET_R - 2:mz + MARKET_R + 3] = np.where(
        np.hypot(X[mx - MARKET_R - 2:mx + MARKET_R + 3, mz - MARKET_R - 2:mz + MARKET_R + 3] - mx,
                 Z[mx - MARKET_R - 2:mx + MARKET_R + 3, mz - MARKET_R - 2:mz + MARKET_R + 3] - mz) < MARKET_R + 3,
        GROUND, f.height[mx - MARKET_R - 2:mx + MARKET_R + 3, mz - MARKET_R - 2:mz + MARKET_R + 3])


def fill_ground(f):
    """地面の柱（上は雪・道の石・氷、下は土と石）"""
    n = SIZE
    hmin = f.height.copy()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        hmin = np.minimum(hmin, np.roll(np.roll(f.height, dx, axis=0), dz, axis=1))
    for x in range(n):
        for z in range(n):
            top = int(f.height[x, z])
            bottom = max(1, min(top, int(hmin[x, z])) - 3)
            cliff = top - int(hmin[x, z]) >= 3
            for y in range(bottom, top):
                if cliff and y >= int(hmin[x, z]) - 1:
                    st = R.pick(ROCK, x, y, z, 3) if (y + int(R.at(x // 5, z // 5, 4) * 3)) % 7 else "packed_ice"
                elif y >= top - 2:
                    st = "dirt"
                else:
                    st = R.pick(ROCK, x, y, z, 2)
                f.put(x, y, z, block(st))
            rd = f.river[x, z]
            if rd < RIVER_HALF or f.pond[x, z] < POND_R:
                f.put(x, top, z, block(R.pick(ICE, x // 2, z // 3, 5)))
                f.put(x, top - 1, z, block("packed_ice"))
                f.reserved[x, z] = True
                continue
            kind = f.path[x, z]
            if kind == 1:
                f.put(x, top, z, block(R.pick(STREET_STONE, x, z, 6)))
                if R.at(x, z, 7) < 0.22:
                    f.put(x, top + 1, z, snow_layer(1))
                f.reserved[x, z] = True
            elif kind == 2:
                f.put(x, top, z, block(R.pick(LANE_STONE, x, z, 8)))
                if R.at(x, z, 9) < 0.35:
                    f.put(x, top + 1, z, snow_layer(1))
                f.reserved[x, z] = True
            else:
                if cliff and R.at(x, z, 10) < 0.5:
                    f.put(x, top, z, block("stone" if R.at(x, z, 11) < 0.6 else "packed_ice"))
                else:
                    f.put(x, top, z, block("snow_block"))
                    if R.at(x, z, 12) < 0.55 and top < HEIGHT - 2:
                        f.put(x, top + 1, z, snow_layer(1 + int(R.at(x, z, 13) * 2.2)))


# ================================================================ 家

def roof_rows(x0, z0, x1, z1):
    """切妻の屋根の段（外から内へ）。棟は長い方の向き"""
    along_x = (x1 - x0) >= (z1 - z0)
    rows = []
    if along_x:
        a, b = z0 - 1, z1 + 1
        k = 0
        while a <= b:
            rows.append((k, [(x, a) for x in range(x0 - 1, x1 + 2)], [(x, b) for x in range(x0 - 1, x1 + 2)] if a != b else [],
                         a, b))
            a += 1
            b -= 1
            k += 1
    else:
        a, b = x0 - 1, x1 + 1
        k = 0
        while a <= b:
            rows.append((k, [(a, z) for z in range(z0 - 1, z1 + 2)], [(b, z) for z in range(z0 - 1, z1 + 2)] if a != b else [],
                         a, b))
            a += 1
            b -= 1
            k += 1
    return along_x, rows


def door_cell(x0, z0, x1, z1, door):
    if door == "e":
        return x1, (z0 + z1) // 2, "east"
    if door == "w":
        return x0, (z0 + z1) // 2, "west"
    if door == "n":
        return (x0 + x1) // 2, z0, "north"
    return (x0 + x1) // 2, z1, "south"


OUT = {"east": (1, 0), "west": (-1, 0), "north": (0, -1), "south": (0, 1)}


def house(f, hid, x0, z0, w, d, door, kind, stories):
    x1, z1 = x0 + w - 1, z0 + d - 1
    y0 = GROUND
    wall_h = 4
    top = y0 + wall_h * stories
    warm = kind in ("inn", "bakery", "smithy")
    plank = {"mayor": "dark_oak_planks", "crystal": "spruce_planks"}.get(kind, "spruce_planks")
    frame = "stripped_dark_oak_log" if kind == "mayor" else "stripped_spruce_log"
    roof = {"inn": "dark_oak_planks", "mayor": "deepslate_tiles", "smithy": "cobblestone", "bakery": "bricks"}.get(
        kind, "dark_oak_planks" if (x0 + z0) % 3 else "spruce_planks")
    glass = "glass_pane" if warm else "light_blue_stained_glass_pane"
    f.houses[hid] = (x0, z0, x1, z1, y0, top)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            f.reserved[x, z] = True
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0 - 3, y0):
                f.put(x, y, z, block("cobblestone"))
            f.put(x, y0, z, block("spruce_planks" if kind != "smithy" else "stone_bricks"))
            for y in range(y0 + 1, top + 1):
                f.put(x, y, z, None)
    dx, dz, dface = door_cell(x0, z0, x1, z1, door)
    for y in range(y0 + 1, top + 1):
        level = (y - y0 - 1) // wall_h
        course = (y - y0 - 1) % wall_h
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                corner = x in (x0, x1) and z in (z0, z1)
                if corner:
                    st = block(frame, axis="y")
                elif course == 0 and level == 0:
                    st = block("cobblestone" if kind != "mayor" else "stone_bricks")
                elif course == wall_h - 1:
                    st = block(frame, axis="x" if z in (z0, z1) else "z")
                else:
                    st = block(plank)
                    # 窓（角から2つ目から、3つおき）
                    along = (x - x0) if z in (z0, z1) else (z - z0)
                    length = (x1 - x0) if z in (z0, z1) else (z1 - z0)
                    if course in (1, 2) and 2 <= along <= length - 2 and along % 3 == 2:
                        f.put(x, y, z, None)
                        pane(f, x, y, z, glass)
                        continue
                f.put(x, y, z, st)
    # 上の階の床と、はしご
    for s in range(1, stories):
        fy = y0 + wall_h * s
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                f.put(x, fy, z, block("spruce_planks"))
        lx, lz = x0 + 1, z0 + 1
        if (dx, dz) == (lx, lz) or door in ("w", "n"):
            lx, lz = x1 - 1, z1 - 1
        for y in range(fy - wall_h + 1, fy + 1):
            f.put(lx, y, lz, block("ladder", facing="south" if lz == z0 + 1 else "north", waterlogged=False))
        f.put(lx, fy, lz, block("ladder", facing="south" if lz == z0 + 1 else "north", waterlogged=False))
    # 戸口（開いた戸）
    ox, oz = OUT[dface]
    for y in (y0 + 1, y0 + 2):
        f.put(dx, y, dz, None)
    hinge_face = {"east": "west", "west": "east", "north": "south", "south": "north"}[dface]
    f.put(dx, y0 + 1, dz, block("spruce_door", facing=hinge_face, half="lower", hinge="left", open=True, powered=False))
    f.put(dx, y0 + 2, dz, block("spruce_door", facing=hinge_face, half="upper", hinge="left", open=True, powered=False))
    f.put(dx + ox, y0, dz + oz, block("spruce_planks"))
    f.put(dx + ox, y0 + 1, dz + oz, None)
    # 戸口の上の小さなひさし（暖かい家は、ひさしの上に灯）
    f.put(dx + ox, y0 + 3, dz + oz, slab("spruce", "top"))
    f.put(dx + ox, y0 + 4, dz + oz, lantern(False) if warm else snow_layer(1))
    # 屋根（段々。段の上に雪）
    along_x, rows = roof_rows(x0, z0, x1, z1)
    last_k = rows[-1][0]
    for k, row_a, row_b, a, b in rows:
        y = top + 1 + k
        for (x, z) in row_a + row_b:
            f.put(x, y, z, block(roof))
            if y + 1 < HEIGHT:
                f.put(x, y + 1, z, snow_layer(2 if k < last_k else 3))
        # 妻壁（三角）をふさぐ
        if along_x:
            for z in range(a + 1, b):
                for xx in (x0, x1):
                    f.put(xx, y, z, block(plank))
        else:
            for x in range(a + 1, b):
                for zz in (z0, z1):
                    f.put(x, y, zz, block(plank))
    # 屋根の内側（屋根裏）にかかった雪を消す
    for k, row_a, row_b, a, b in rows:
        y = top + 1 + k + 1
        if along_x:
            for x in range(x0 - 1, x1 + 2):
                for z in range(a + 1, b):
                    if f.name_at(x, y, z) == "snow":
                        f.put(x, y, z, None)
        else:
            for z in range(z0 - 1, z1 + 2):
                for x in range(a + 1, b):
                    if f.name_at(x, y, z) == "snow":
                        f.put(x, y, z, None)
    # 煙突（暖かい家だけ煙が出る）
    cxh, czh = (x0 + 1, (z0 + z1) // 2) if along_x else ((x0 + x1) // 2, z0 + 1)
    ridge_y = top + 1 + last_k
    for y in range(y0 + 1, ridge_y + 3):
        f.put(cxh, y, czh, block("bricks" if warm else "cobblestone"))
    if warm:
        f.put(cxh, ridge_y + 3, czh, block("campfire", lit=True, signal_fire=False, facing="north", waterlogged=False))
    else:
        f.put(cxh, ridge_y + 3, czh, snow_layer(2))
    interior(f, hid, x0, z0, x1, z1, y0, door, kind, stories, (cxh, czh))


def interior(f, hid, x0, z0, x1, z1, y0, door, kind, stories, chimney):
    y = y0 + 1
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    hx, hz = chimney
    # 暖炉（煙突の前）
    fx = hx + (1 if hx == x0 + 1 and (x1 - x0) >= (z1 - z0) else 0)
    fz = hz + (1 if hz == z0 + 1 and (x1 - x0) < (z1 - z0) else 0)
    if kind in ("inn", "bakery", "smithy"):
        idx = len(f.hearths)
        name = dict(HEARTHS)[hid]
        hz_ = fz
        hx_ = fx
        if kind == "smithy":
            hx_, hz_ = x0 + 3, z0 + 3
        f.put(hx_, y, hz_, block("amemaru:village_hearth", facing="south" if (x1 - x0) < (z1 - z0) else "east"), thing(idx, "hearth:" + hid))
        f.hearths.append((hid, name, (hx_, y, hz_)))
    elif kind not in ("crystal",):
        f.put(fx, y, fz, block("campfire", lit=False, signal_fire=False, facing="north", waterlogged=False))
    # 家具
    rnd = R.at(x0, z0, 70)
    if kind in ("home", "child", "mayor", "crystal"):
        # 寝台
        bx, bz = x1 - 1, z0 + 1
        f.put(bx, y, bz, block("light_blue_bed", facing="south", part="head", occupied=False))
        f.put(bx, y, bz + 1, block("light_blue_bed", facing="south", part="foot", occupied=False))
        # 卓といす
        tx, tz = cx, cz + (1 if rnd < 0.5 else 0)
        fence(f, tx, y, tz, "spruce_fence")
        f.put(tx, y + 1, tz, block("spruce_pressure_plate", powered=False))
        f.put(tx - 1, y, tz, stairs("spruce", "west"))
        f.put(tx + 1, y, tz, stairs("spruce", "east"))
        # 棚と樽
        f.put(x0 + 1, y, z1 - 1, block("barrel", facing="up", open=False))
        f.put(x0 + 1, y + 1, z1 - 1, block("flower_pot"))
        if x1 - x0 >= 9:
            f.put(x1 - 1, y, z1 - 1, block("bookshelf"))
            f.put(x1 - 1, y + 1, z1 - 1, block("bookshelf"))
        # 凍った床（氷のしみ）
        for k in range(4):
            ix = x0 + 1 + int(R.at(x0, z0, k, 71) * (x1 - x0 - 1))
            iz = z0 + 1 + int(R.at(x0, z0, k, 72) * (z1 - z0 - 1))
            if f.v[ix, y, iz] == 0:
                f.put(ix, y0, iz, block("packed_ice"))
        f.put(cx, y0 + 3 if stories == 1 else y0 + 3, cz, lantern(True))
    elif kind == "shop":
        for z in range(z0 + 2, z1 - 1):
            f.put(x0 + 3 if door == "e" else x1 - 3, y, z, block("stripped_spruce_wood", axis="y"))
        f.put(x0 + 1, y, z0 + 1, block("barrel", facing="up", open=False))
        f.put(x0 + 1, y, z1 - 1, block("barrel", facing="up", open=False))
        f.put(x1 - 1, y, z0 + 1, block("composter", level=0))
        f.put(cx, y0 + 3, cz, lantern(True))
    elif kind == "inn":
        for (tx, tz) in ((x0 + 4, z0 + 4), (x0 + 4, z1 - 4), (x0 + 9, z0 + 6), (x0 + 9, z1 - 6)):
            fence(f, tx, y, tz, "spruce_fence")
            f.put(tx, y + 1, tz, block("spruce_pressure_plate", powered=False))
            f.put(tx, y, tz - 1, stairs("spruce", "north"))
            f.put(tx, y, tz + 1, stairs("spruce", "south"))
        for z in range(z0 + 3, z1 - 2):
            f.put(x1 - 3, y, z, block("stripped_dark_oak_wood", axis="y"))
        f.put(x1 - 1, y, z0 + 2, block("barrel", facing="up", open=False))
        f.put(x1 - 1, y + 1, z0 + 2, block("barrel", facing="up", open=False))
        for (lx, lz) in ((x0 + 4, z0 + 5), (x0 + 9, z1 - 5), (x1 - 3, cz)):
            f.put(lx, y0 + 3, lz, lantern(True))
        # 2階の客室（寝台）
        uy = y0 + 4 + 1
        for k, bz in enumerate(range(z0 + 2, z1 - 2, 5)):
            f.put(x1 - 2, uy, bz, block("red_bed", facing="south", part="head", occupied=False))
            f.put(x1 - 2, uy, bz + 1, block("red_bed", facing="south", part="foot", occupied=False))
        f.put(cx, uy + 2, cz, lantern(True))
    elif kind == "bakery":
        f.put(x0 + 2, y, z1 - 2, block("hay_block", axis="y"))
        f.put(x0 + 3, y, z1 - 2, block("barrel", facing="up", open=False))
        for z in range(z0 + 3, z1 - 2):
            f.put(x0 + 4, y, z, block("stripped_spruce_wood", axis="y"))
        f.put(cx, y0 + 3, cz, lantern(True))
    elif kind == "smithy":
        f.put(x0 + 6, y, z0 + 3, block("anvil", facing="east"))
        f.put(x0 + 8, y, z0 + 3, block("grindstone", face="floor", facing="north"))
        f.put(x0 + 10, y, z0 + 3, block("smithing_table"))
        f.put(x0 + 3, y + 1, z0 + 3, block("cobblestone_wall", north="none", south="none", east="none", west="none", up=True,
                                           waterlogged=False))
        f.put(x1 - 1, y, z1 - 1, block("barrel", facing="up", open=False))
        f.put(cx, y0 + 3, cz, lantern(True))
    if kind == "mayor":
        # 村長の机（記憶の結晶が現れる所）と本棚
        for x in range(cx - 2, cx + 3):
            f.put(x, y, z0 + 4, block("dark_oak_planks"))
        f.put(cx, y + 1, z0 + 4, None)
        f.crystal_spots.append([cx, y + 1, z0 + 4])
        for x in range(x0 + 3, x1 - 2):
            if abs(x - cx) > 4 and x % 2 == 0:
                f.put(x, y, z0 + 1, block("bookshelf"))
                f.put(x, y + 1, z0 + 1, block("bookshelf"))
        f.put(cx, y + 1, z0 + 2, lantern(False))
        for x in (cx - 1, cx + 1):
            f.put(x, y, z0 + 5, stairs("dark_oak", "north"))
    if kind == "crystal":
        # 戦いの間、記憶の結晶が現れる台（奥の壁の前）
        px = x0 + 2 if door == "e" else x1 - 2
        f.put(px, y, cz, block("polished_diorite"))
        f.crystal_spots.append([px, y + 1, cz])


# ================================================================ 入口（村はずれの門）

def return_circle(f, cx, y, cz):
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 0.6:
                f.put(x, y, z, block("chiseled_quartz_block"))
            elif d <= 1.7:
                f.put(x, y, z, block("quartz_bricks"))
            elif d <= 2.7:
                f.put(x, y, z, block("smooth_quartz"))
            elif d <= 3.3:
                f.put(x, y, z, block("polished_diorite"))
            if d <= 3.4:
                f.put(x, y + 1, z, None)


def entrance(f):
    cx, cz = ENTRY
    return_circle(f, cx, GROUND, cz)
    for x in range(cx - 18, cx + 19):
        for z in range(cz - 18, cz + 6):
            f.reserved[x, z] = True
    tx, tz = cx - 9, cz - 6
    f.put(tx, GROUND + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, GROUND + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, GROUND + 1, tz)
    sign(f, cx + 2, GROUND + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 5, GROUND + 1, cz - 13, 0, ["6階", "凍結した村", "", "氷像の村人に、ご用心"])
    chest(f, cx + 13, GROUND + 1, cz - 4, "west")
    # 村はずれの柵と門（丸太の柵。門の上に、凍った鐘）
    for x in range(196, 430):
        if abs(x - cx) <= 4:
            continue
        top = f.top(x, GATE_Z)
        if f.river[x, GATE_Z] < RIVER_HALF + 1:
            continue
        for y in range(top + 1, top + 4):
            f.put(x, y, GATE_Z, block("spruce_log", axis="y"))
        f.put(x, top + 4, GATE_Z, snow_layer(2) if x % 3 else block("spruce_fence", north=False, south=False, east=False,
                                                                      west=False, waterlogged=False))
    for x in (cx - 5, cx + 5):
        for y in range(GROUND + 1, GROUND + 8):
            f.put(x, y, GATE_Z, block("stripped_spruce_log", axis="y"))
    for x in range(cx - 6, cx + 7):
        f.put(x, GROUND + 8, GATE_Z, block("spruce_log", axis="x"))
        f.put(x, GROUND + 9, GATE_Z, snow_layer(3))
    f.put(cx, GROUND + 7, GATE_Z, block("bell", attachment="ceiling", facing="north", powered=False))
    wall_sign(f, cx + 3, GROUND + 6, GATE_Z + 1, "south", ["", "ノルヴァスの", "北の村", ""])
    # 見張り小屋（門の西）
    for x in range(cx - 16, cx - 9):
        for z in range(GATE_Z - 7, GATE_Z - 1):
            edge = x in (cx - 16, cx - 10) or z in (GATE_Z - 7, GATE_Z - 2)
            for y in range(GROUND + 1, GROUND + 4):
                f.put(x, y, z, block("spruce_planks") if edge else None)
            f.put(x, GROUND + 4, z, block("spruce_slab", type="bottom", waterlogged=False))
            f.put(x, GROUND + 5, z, snow_layer(2))
    for y in (GROUND + 1, GROUND + 2):
        f.put(cx - 10, y, GATE_Z - 4, None)
    statue(f, cx - 13, GROUND + 1, GATE_Z - 4, "guard", "stand", 90.0)
    for (dx, dz) in ((-12, -14), (12, -14), (-8, 2), (8, 2)):
        lamp(f, cx + dx, cz + dz)
    f.story_zones.append({"id": "village_gate", "box": [cx - 8, GROUND, GATE_Z - 14, cx + 8, GROUND + 6, GATE_Z - 4]})


def lamp(f, x, z, tall=3):
    y0 = f.top(x, z)
    for k in range(1, tall + 1):
        fence(f, x, y0 + k, z, "spruce_fence")
    f.put(x, y0 + tall + 1, z, lantern(False))
    f.reserved[x, z] = True


# ================================================================ 雪の通り・市場

def street(f):
    cx = STREET[0][0]
    # 通りの灯と、凍った村人
    for z in range(560, 420, -16):
        for x in (cx - 5, cx + 5):
            lamp(f, x, z)
    poses = [("shovel", "man", 90.0), ("snowball", "child_boy", 200.0), ("snowball", "child_girl", 20.0), ("carry", "woman", 180.0),
             ("talk", "man", 270.0), ("talk", "woman", 90.0), ("wave", "old_man", 0.0), ("sweep", "woman", 180.0)]
    spots = [(cx - 2, 552), (cx + 2, 540), (cx + 1, 534), (cx - 2, 512), (cx - 1, 488), (cx + 1, 488), (cx + 2, 462), (cx - 2, 440)]
    for (x, z), (pose, skin, yaw) in zip(spots, poses):
        statue(f, x, GROUND + 1, z, skin, pose, yaw, 0.8 if skin.startswith("child") else 1.0)
    # 洗濯ものを干す人（w4 の前）
    for x in range(300, 304):
        f.put(x, GROUND + 3, 476, block("white_wool") if x % 2 else block("light_blue_wool"))
    fence(f, 299, GROUND + 1, 476)
    fence(f, 299, GROUND + 2, 476)
    fence(f, 304, GROUND + 1, 476)
    fence(f, 304, GROUND + 2, 476)
    statue(f, 302, GROUND + 1, 474, "woman", "reach_up", 0.0)
    chest(f, 306, GROUND + 1, 430, "east")
    wall_sign(f, 323, GROUND + 4, 464, "west", ["", "宿屋", "雪灯亭", ""])
    waypoint(f, "inn", "雪灯亭", 318, GROUND + 1, 466, 90, look_at=(324, 464))


def market(f):
    cx, cz = MARKET
    for x in range(cx - MARKET_R - 1, cx + MARKET_R + 2):
        for z in range(cz - MARKET_R - 1, cz + MARKET_R + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= MARKET_R:
                ring = int(d) % 6
                st = "stone_bricks" if ring else "polished_andesite"
                if R.at(x, z, 80) < 0.15:
                    st = "cracked_stone_bricks"
                f.put(x, GROUND, z, block(st))
                f.put(x, GROUND + 1, z, snow_layer(1) if R.at(x, z, 81) < 0.12 else None)
                f.reserved[x, z] = True
    # 井戸（凍った水）
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) == 2 or abs(z - cz) == 2:
                f.put(x, GROUND + 1, z, block("cobblestone"))
                f.put(x, GROUND + 2, z, block("cobblestone_slab", type="bottom", waterlogged=False))
            else:
                f.put(x, GROUND, z, block("ice"))
                f.put(x, GROUND - 1, z, block("ice"))
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz + 2)):
        for y in range(GROUND + 2, GROUND + 5):
            fence(f, x, y, z, "spruce_fence")
    for x in range(cx - 2, cx + 3):
        f.put(x, GROUND + 5, cz - 2 + (x - cx + 2), None)
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - z - (cx - cz)) <= 0:
                f.put(x, GROUND + 5, z, block("spruce_planks"))
    # 屋台（布の屋根・品物）
    stalls = [(cx - 14, cz - 12, "red_wool"), (cx - 14, cz + 6, "yellow_wool"), (cx + 9, cz - 14, "blue_wool"),
              (cx + 10, cz + 8, "green_wool"), (cx - 4, cz + 14, "orange_wool")]
    goods = ["melon", "pumpkin", "hay_block", "barrel", "composter"]
    for k, (sx, sz, cloth) in enumerate(stalls):
        for (ox, oz) in ((0, 0), (4, 0), (0, 3), (4, 3)):
            for y in range(GROUND + 1, GROUND + 4):
                fence(f, sx + ox, y, sz + oz, "spruce_fence")
        for x in range(sx, sx + 5):
            for z in range(sz, sz + 4):
                f.put(x, GROUND + 4, z, block(cloth))
                f.put(x, GROUND + 5, z, snow_layer(1))
        for x in range(sx + 1, sx + 4):
            f.put(x, GROUND + 1, sz + 1, block("spruce_planks"))
            g = goods[(k + x) % len(goods)]
            f.put(x, GROUND + 2, sz + 1, block(g) if g not in ("barrel", "composter") else (
                block("barrel", facing="up", open=False) if g == "barrel" else block("composter", level=0)))
    # 値切る商人（記憶の氷像）と、まわりで見ている人
    statue(f, cx - 12, GROUND + 1, cz - 9, "buyer", "point", 0.0)
    statue(f, cx + 11, GROUND + 1, cz - 11, "child_boy", "reach", 300.0, 0.8)
    statue(f, cx + 12, GROUND + 1, cz + 5, "woman", "carry", 180.0)
    statue(f, cx - 6, GROUND + 1, cz + 12, "old_man", "talk", 180.0)
    statue(f, cx + 3, GROUND + 1, cz - 6, "man", "wave", 30.0)
    statue(f, cx + 5, GROUND + 1, cz + 3, "woman", "talk", 250.0)
    for (dx, dz) in ((-18, -15), (18, -15), (-18, 15), (18, 15)):
        lamp(f, cx + dx, cz + dz, 4)
    waypoint(f, "market", "市場の広場", cx - 4, GROUND + 1, cz + 18, 180, look_at=(cx, cz))
    chest(f, cx + 16, GROUND + 1, cz - 18, "south")
    sign(f, cx - 3, GROUND + 1, cz - 4, 8, ["市場の広場", "", "値切り声のまま", "凍った市場"])
    f.story_zones.append({"id": "frozen_market", "box": [cx - 14, GROUND, cz - 14, cx + 14, GROUND + 6, cz + 14]})


# ================================================================ 記憶の氷像

def memories(f):
    poses = {"market": ("merchant", "haggle"), "mother": ("mother", "turn"), "ringer": ("ringer", "pull"), "mayor_call": ("ordo", "call")}
    for mid, name, (x, z), yaw in MEMORIES:
        y = f.top(x, z) + 1
        skin, pose = poses[mid]
        # 台（氷の台と、霜の輪）
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                d = math.hypot(dx, dz)
                if d <= 2.4:
                    f.put(x + dx, y - 1, z + dz, block("packed_ice") if d > 1.2 else block("blue_ice"))
                    if f.name_at(x + dx, y, z + dz) == "snow":
                        f.put(x + dx, y, z + dz, None)
                    f.reserved[x + dx, z + dz] = True
        statue(f, x, y, z, skin, pose, yaw, 1.15 if mid == "mayor_call" else 1.0, memory=mid)
        f.memories.append((mid, name, (x, y, z), yaw))
    # 母と子：子どもは母の手を取ろうとしている
    mx, my, mz = f.memories[1][2]
    statue(f, mx - 1, my, mz + 1, "child_girl", "reach", 0.0, 0.75)
    # 村長の呼びかけ：まわりで聞いている村人
    ox, oy, oz = f.memories[3][2]
    for k, (dx, dz, skin, yaw) in enumerate(((-4, 5, "man", 180.0), (3, 6, "woman", 170.0), (0, 7, "old_man", 180.0),
                                             (-6, 2, "child_boy", 200.0))):
        statue(f, ox + dx, f.top(ox + dx, oz + dz) + 1, oz + dz, skin, "listen", yaw, 0.8 if skin.startswith("child") else 1.0)


# ================================================================ 西：子どもの家・凍った川と水車小屋・川向こう

def west(f):
    # 子どもの家の古代記録（日記）
    x0, z0, x1, z1, y0, _top = f.houses["child"]
    tx, tz = x0 + 2, z1 - 2
    f.put(tx, y0 + 1, tz, block("lectern", facing="north", has_book=False, powered=False))
    f.tablet = (tx, y0 + 1, tz)
    statue(f, x1 - 3, y0 + 1, z0 + 3, "child_girl", "sit_write", 180.0, 0.75)
    f.put(x1 - 3, y0 + 1, z0 + 4, block("spruce_planks"))
    wall_sign(f, x0 + 4, y0 + 3, z0 - 1, "north", ["", "ちいさな家", "", ""])
    # 橋（川の東の小道から、川向こうへ）と、南の橋
    for (bx0, bx1, bz) in ((136, 184, 322), (126, 176, 520)):
        for x in range(bx0, bx1 + 1):
            for z in range(bz - 2, bz + 3):
                if f.river[x, z] < RIVER_HALF + 3:
                    f.put(x, GROUND, z, block("spruce_planks"))
                    for y in range(GROUND + 1, GROUND + 3):
                        f.put(x, y, z, None)
                    f.reserved[x, z] = True
            for z in (bz - 3, bz + 3):
                if f.river[x, z] < RIVER_HALF + 2:
                    fence(f, x, GROUND + 1, z, "spruce_fence")
    # 水車小屋（東の岸）と、凍った水車（川の上）
    house_box(f, 163, 326, 174, 337, "e", y0=GROUND)
    mx = 162
    for y in range(GROUND, GROUND + 9):
        for z in range(328, 336):
            d = math.hypot(y - (GROUND + 4), z - 331.5)
            if 3.2 <= d <= 4.3 or (abs(z - 331.5) < 0.6) or (abs(y - (GROUND + 4)) < 0.6):
                f.put(mx - 1, y, z, block("stripped_spruce_log", axis="x") if d < 4.4 else None)
    for z in range(327, 337):
        f.put(mx - 1, ICE_Y, z, block("blue_ice"))
    chest(f, 172, GROUND + 1, 334, "west")
    statue(f, 166, GROUND + 1, 330, "old_man", "carry", 90.0)
    # 川向こうの林（霜狼の群れ長のねぐら：岩の張り出しと、骨と、宝箱）
    den = (96, 340)
    for x in range(den[0] - 8, den[0] + 9):
        for z in range(den[1] - 6, den[1] + 7):
            d = math.hypot((x - den[0]) / 8.0, (z - den[1]) / 6.0)
            if d <= 1.0:
                ytop = f.top(x, z)
                for y in range(ytop + 1, ytop + 6):
                    shell = d > 0.72 or y >= ytop + 5
                    if shell and not (z > den[1] + 2 and abs(x - den[0]) <= 2):
                        f.put(x, y, z, block(R.pick([("stone", 3), ("packed_ice", 2), ("cobblestone", 1)], x, y, z, 90)))
                    else:
                        f.put(x, y, z, None)
                f.reserved[x, z] = True
    f.put(den[0] - 2, f.top(den[0] - 2, den[1]) + 1, den[1], block("bone_block", axis="x"))
    chest(f, den[0], f.top(den[0], den[1] - 3) + 1, den[1] - 3, "south", guardian="pack_leader")
    gy = f.top(den[0], den[1] + 10) + 1
    f.guardians.append({"id": "pack_leader", "enemy": "amemaru:frost_wolf", "variant": "amemaru:frost_wolf_leader",
                        "pos": [den[0] + 0.5, gy, den[1] + 10.5], "respawn_seconds": 300, "activation": 26, "pillar": -1})
    sign(f, den[0] + 4, f.top(den[0] + 4, den[1] + 9) + 1, den[1] + 9, 8, ["群れ長のねぐら", "", "白い牙の狼が", "ここを守っている"])
    chest(f, 74, GROUND + 1, 300, "east")


def house_box(f, x0, z0, x1, z1, door, y0=None):
    """小さな小屋（水車小屋・氷釣りの小屋など。屋根は板）。y0 を決めたときは、床の下を地面まで埋める"""
    if y0 is None:
        y0 = f.top((x0 + x1) // 2, (z0 + z1) // 2)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.reserved[x, z] = True
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(min(f.top(x, z), y0) - 2, y0):
                f.put(x, y, z, block("cobblestone"))
            f.put(x, y0, z, block("spruce_planks"))
            for y in range(y0 + 1, y0 + 5):
                f.put(x, y, z, block("stripped_spruce_log", axis="y") if edge and x in (x0, x1) and z in (z0, z1)
                      else block("spruce_planks") if edge else None)
            f.put(x, y0 + 5, z, block("spruce_slab", type="bottom", waterlogged=False))
            f.put(x, y0 + 6, z, snow_layer(2))
    dx, dz, face = door_cell(x0, z0, x1, z1, door)
    for y in (y0 + 1, y0 + 2):
        f.put(dx, y, dz, None)
    f.put(x0 + 1, y0 + 3, z0 + 1, lantern(True))


# ================================================================ 北西：鍛冶屋と教会

def chapel(f):
    x0, z0, x1, z1 = 192, 214, 216, 246
    y0 = GROUND
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.reserved[x, z] = True
            for y in range(y0 - 3, y0):
                f.put(x, y, z, block("stone_bricks"))
            f.put(x, y0, z, block("polished_andesite") if (x + z) % 2 else block("stone_bricks"))
            for y in range(y0 + 1, y0 + 12):
                edge = x in (x0, x1) or z in (z0, z1)
                if edge:
                    window = (y in range(y0 + 3, y0 + 8)) and ((z - z0) % 5 == 2 if x in (x0, x1) else (x - x0) % 6 == 3)
                    st = block("light_blue_stained_glass") if window else block(
                        "chiseled_stone_bricks" if y == y0 + 11 else "stone_bricks")
                    f.put(x, y, z, st)
                else:
                    f.put(x, y, z, None)
    # 屋根（段々の石の瓦。東西に棟）
    for k in range(0, 9):
        y = y0 + 12 + k
        for x in range(x0 - 1, x1 + 2):
            for z in (z0 - 1 + k, z1 + 1 - k):
                if z0 - 1 + k <= z1 + 1 - k:
                    f.put(x, y, z, block("deepslate_tiles"))
                    f.put(x, y + 1, z, snow_layer(2))
        for z in range(z0 + k, z1 - k + 1):
            for xx in (x0, x1):
                f.put(xx, y, z, block("stone_bricks"))
    for k in range(0, 9):
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 + k, z1 - k + 1):
                if f.name_at(x, y0 + 13 + k, z) == "snow":
                    f.put(x, y0 + 13 + k, z, None)
    # 鐘の塔（東の端）
    tx0, tz0 = x1 - 4, (z0 + z1) // 2 - 3
    for x in range(tx0, tx0 + 7):
        for z in range(tz0, tz0 + 7):
            for y in range(y0 + 1, y0 + 26):
                edge = x in (tx0, tx0 + 6) or z in (tz0, tz0 + 6)
                open_ = y in range(y0 + 19, y0 + 23) and not (x in (tx0, tx0 + 6) and z in (tz0, tz0 + 6))
                f.put(x, y, z, block("stone_bricks") if edge and not open_ else None)
            f.put(x, y0 + 18, z, block("stone_bricks"))
            f.put(x, y0 + 26, z, block("deepslate_tiles"))
            f.put(x, y0 + 27, z, snow_layer(3))
    f.put(tx0 + 3, y0 + 22, tz0 + 3, block("bell", attachment="ceiling", facing="east", powered=False))
    for y in range(y0 + 19, y0 + 22):
        f.put(tx0 + 3, y, tz0 + 3, None)
    # 戸口（東）
    for z in range((z0 + z1) // 2 - 1, (z0 + z1) // 2 + 2):
        for y in range(y0 + 1, y0 + 5):
            f.put(x1, y, z, None)
            f.put(tx0, y, z, None)
            f.put(tx0 + 6, y, z, None)
    # 長いす・祭壇
    for z in range(z0 + 3, z1 - 2, 3):
        for x in range(x0 + 3, x1 - 7):
            if abs(z - (z0 + z1) // 2) > 1:
                f.put(x, y0 + 1, z, stairs("spruce", "west"))
    for z in range((z0 + z1) // 2 - 2, (z0 + z1) // 2 + 3):
        f.put(x0 + 2, y0 + 1, z, block("chiseled_quartz_block"))
    f.put(x0 + 2, y0 + 2, (z0 + z1) // 2, block("candle", candles=3, lit=False, waterlogged=False))
    for (lx, lz) in ((x0 + 6, z0 + 3), (x0 + 6, z1 - 3), (x1 - 8, z0 + 3), (x1 - 8, z1 - 3)):
        f.put(lx, y0 + 6, lz, lantern(True))
    chest(f, x0 + 2, y0 + 1, z0 + 2, "east")
    statue(f, x0 + 4, y0 + 1, (z0 + z1) // 2 + 3, "old_man", "pray", 270.0)
    statue(f, x0 + 4, y0 + 1, (z0 + z1) // 2 - 3, "woman", "pray", 270.0)
    # 教会の前の広場と墓地
    for x in range(x1 + 1, x1 + 22):
        for z in range(z0 + 2, z1 - 1):
            f.put(x, GROUND, z, block(R.pick(STREET_STONE, x, z, 95)))
            f.put(x, GROUND + 1, z, None)
            f.reserved[x, z] = True
    for k in range(10):
        gx = x0 - 8 + (k % 5) * 3
        gz = z0 + 2 + (k // 5) * 6
        if f.top(gx, gz) <= GROUND + 4:
            yy = f.top(gx, gz) + 1
            f.put(gx, yy, gz, block("cobblestone_wall", north="none", south="none", east="none", west="none", up=True,
                                    waterlogged=False))
            f.put(gx, yy + 1, gz, snow_layer(2))
    waypoint(f, "chapel", "教会の前", x1 + 14, GROUND + 1, (z0 + z1) // 2 + 4, 180, look_at=(x1, (z0 + z1) // 2))
    sign(f, x1 + 4, GROUND + 1, z1 - 3, 4, ["村の教会", "", "鐘を鳴らそうとした", "若者がいる"])


# ================================================================ 東：牧場と納屋・北東：凍った池

def pasture(f):
    x0, z0, x1, z1 = 440, 300, 522, 382
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            fence(f, x, f.top(x, z) + 1, z, "spruce_fence")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if x == x0 and abs(z - 352) <= 2:
                continue
            fence(f, x, f.top(x, z) + 1, z, "spruce_fence")
    # 納屋
    bx0, bz0, bx1, bz1 = 462, 312, 486, 330
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            f.reserved[x, z] = True
            f.put(x, GROUND, z, block("spruce_planks"))
            for y in range(GROUND + 1, GROUND + 7):
                edge = x in (bx0, bx1) or z in (bz0, bz1)
                corner = x in (bx0, bx1) and z in (bz0, bz1)
                if edge:
                    f.put(x, y, z, block("stripped_spruce_log", axis="y") if corner else block(
                        "red_terracotta" if y < GROUND + 6 else "spruce_planks"))
                else:
                    f.put(x, y, z, None)
    for k in range(0, 11):
        y = GROUND + 7 + k
        for x in range(bx0 - 1, bx1 + 2):
            for z in (bz0 - 1 + k, bz1 + 1 - k):
                if bz0 - 1 + k <= bz1 + 1 - k:
                    f.put(x, y, z, block("spruce_planks"))
                    f.put(x, y + 1, z, snow_layer(2))
        for z in range(bz0 + k, bz1 - k + 1):
            for xx in (bx0, bx1):
                f.put(xx, y, z, block("spruce_planks"))
        for x in range(bx0 - 1, bx1 + 2):
            for z in range(bz0 + k, bz1 - k + 1):
                if f.name_at(x, y + 1, z) == "snow":
                    f.put(x, y + 1, z, None)
    for z in range(318, 325):
        for y in range(GROUND + 1, GROUND + 5):
            f.put(bx0, y, z, None)
    for (hx, hz) in ((480, 316), (481, 316), (480, 317), (484, 326), (484, 327)):
        f.put(hx, GROUND + 1, hz, block("hay_block", axis="y"))
    chest(f, 482, GROUND + 1, 322, "west", guardian="bear_elder")
    f.put(474, GROUND + 4, 321, lantern(True))
    gy = f.top(486, 358) + 1
    f.guardians.append({"id": "bear_elder", "enemy": "amemaru:frost_bear", "variant": "amemaru:frost_bear_elder",
                        "pos": [486.5, gy, 358.5], "respawn_seconds": 300, "activation": 28, "pillar": -1})
    sign(f, x0 - 2, GROUND + 1, 348, 4, ["村の牧場", "", "大きな熊の足あとが", "続いている"])
    statue(f, 452, GROUND + 1, 360, "man", "shovel", 300.0)
    waypoint(f, "pasture", "牧場の入口", 430, GROUND + 1, 348, 90, look_at=(470, 340))


def pond(f):
    cx, cz = POND
    # 氷釣りの小屋と、凍った釣り人
    house_box(f, cx + 18, cz + 14, cx + 25, cz + 20, "w")
    chest(f, cx + 23, f.top(cx + 22, cz + 18) + 1, cz + 18, "west")
    for (dx, dz, yaw) in ((-6, 4, 0.0), (5, -7, 120.0)):
        x, z = cx + dx, cz + dz
        f.put(x, ICE_Y, z, block("water", level=0))
        statue(f, x + 1, ICE_Y + 1, z, "man", "fish", yaw)
    sign(f, cx - 4, f.top(cx - 4, cz + POND_R + 3) + 1, cz + POND_R + 3, 0, ["凍った池", "", "凍て鳥が", "群れている"])


# ================================================================ 北の通りと、凍りついた門（封印）・雪の広場（ボス）

def north(f):
    cx = NORTH_ROAD[0][0]
    for z in range(350, 180, -18):
        for x in (cx - 5, cx + 5):
            lamp(f, x, z)
    statue(f, cx + 2, GROUND + 1, 300, "man", "carry", 0.0)
    statue(f, cx - 2, GROUND + 1, 252, "woman", "wave", 180.0)
    chest(f, cx - 6, GROUND + 1, 240, "east")
    chest(f, 420, f.top(420, 248) + 1, 248, "north")
    chest(f, 560, f.top(560, 470) + 1, 470, "west")
    chest(f, 236, GROUND + 1, 300, "south")


def plaza(f):
    cx, cz = ARENA
    px0, pz0, px1, pz1 = PLAZA
    inside_house = np.zeros((SIZE, SIZE), dtype=bool)
    for hid in ("plaza_w", "plaza_e", "mayor"):
        hx0, hz0, hx1, hz1, _y0, _top = f.houses[hid]
        inside_house[hx0 - 1:hx1 + 2, hz0 - 1:hz1 + 2] = True
    # 囲い（石の塀。高さ4。上に雪）
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            f.reserved[x, z] = True
            if inside_house[x, z]:
                continue
            edge = x in (px0, px1) or z in (pz0, pz1)
            if edge:
                for y in range(GROUND + 1, GROUND + 5):
                    f.put(x, y, z, block("stone_bricks" if y < GROUND + 4 else "cobblestone_wall", **(
                        {} if y < GROUND + 4 else dict(north="none", south="none", east="none", west="none", up=True,
                                                         waterlogged=False))))
                f.put(x, GROUND + 5, z, None)
            else:
                d = math.hypot(x - cx, z - cz)
                if d <= ARENA_R:
                    ring = int(d) % 7
                    f.put(x, GROUND, z, block("polished_andesite" if ring == 0 else "stone_bricks"))
                    f.put(x, GROUND + 1, z, None)
                else:
                    f.put(x, GROUND, z, block("snow_block"))
                    f.put(x, GROUND + 1, z, snow_layer(1))
    # まん中の凍った泉
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = math.hypot(x - cx, z - cz)
            if d <= 3.4:
                f.put(x, GROUND, z, block("ice") if d < 2.6 else block("polished_diorite"))
                if 2.6 <= d <= 3.4:
                    f.put(x, GROUND + 1, z, block("polished_diorite_slab", type="bottom", waterlogged=False))
    for y in range(GROUND + 1, GROUND + 5):
        f.put(cx, y, cz, block("packed_ice") if y < GROUND + 4 else block("blue_ice"))
    f.center = (cx, GROUND + 1, cz)
    # 広場の灯（12本）
    for k in range(12):
        a = k / 12 * math.tau
        x = int(round(cx + math.cos(a) * (ARENA_R - 2)))
        z = int(round(cz + math.sin(a) * (ARENA_R - 2)))
        if abs(x - cx) <= 4:
            continue     # 南の封印の道と、北の村長の家の戸口はあける
        lamp(f, x, z, 3)
    # 出口の円と次の階への円（村長の家の前）
    ex, ez = cx - 4, cz - 18
    return_circle(f, ex, GROUND, ez)
    next_circle(f.put, ex + NEXT_DX, GROUND, ez)
    f.exit_circle = (ex, ez)
    # 封印（南の塀の門。凍りついた門）
    for x in range(cx - 4, cx + 5):
        for y in range(GROUND + 1, GROUND + 7):
            f.put(x, y, SEAL_Z, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, SEAL_Z))
    for x in (cx - 5, cx + 5):
        for y in range(GROUND + 1, GROUND + 9):
            f.put(x, y, SEAL_Z, block("packed_ice"))
    for x in range(cx - 5, cx + 6):
        f.put(x, GROUND + 7, SEAL_Z, block("blue_ice"))
        f.put(x, GROUND + 8, SEAL_Z, block("packed_ice"))
    sign(f, cx + 7, GROUND + 1, SEAL_Z + 3, 0, ["凍りついた門", "", "記憶の氷像を", "すべて溶かすと開く"])
    # 見えない灯（広場はいつも明るく）
    for x in range(px0 + 3, px1 - 2, 6):
        for z in range(pz0 + 3, pz1 - 2, 6):
            if f.v[x, GROUND + 4, z] == 0:
                f.put(x, GROUND + 4, z, block("light", level=12, waterlogged=False))


# ================================================================ 村のはずれ（畑・木こりの小屋・見張りの塔）と、家のまわりの飾り

def outskirts(f):
    # 南東の畑（柵で囲った、雪の下の畑。かかし）
    for (x0, z0, x1, z1) in ((436, 440, 476, 470), (380, 528, 420, 556)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                f.reserved[x, z] = True
                edge = x in (x0, x1) or z in (z0, z1)
                top = f.top(x, z)
                if edge:
                    if not (x == x0 and abs(z - (z0 + z1) // 2) <= 1):
                        fence(f, x, top + 1, z, "spruce_fence")
                elif (z - z0) % 3 == 0:
                    f.put(x, top, z, block("farmland", moisture=0))
                    f.put(x, top + 1, z, snow_layer(2))
        sx, sz = (x0 + x1) // 2, (z0 + z1) // 2
        top = f.top(sx, sz)
        fence(f, sx, top + 1, sz)
        fence(f, sx, top + 2, sz)
        f.put(sx, top + 3, sz, block("carved_pumpkin", facing="south"))
    chest(f, 404, GROUND + 1, 516, "north")
    statue(f, 446, f.top(446, 456) + 1, 456, "man", "shovel", 90.0)
    # 南西の木こりの小屋（丸太の山）
    for k in range(5):
        lx, lz = 86 + (k % 3) * 4, 490 + (k // 3) * 5
        top = f.top(lx, lz)
        for dx in range(3):
            for dy in range(2 - (dx == 1 and False)):
                f.put(lx + dx, top + 1 + dy, lz, block("spruce_log", axis="z"))
                f.put(lx + dx, top + 1 + dy, lz + 1, block("spruce_log", axis="z"))
        f.put(lx + 1, top + 3, lz, snow_layer(2))
        for dx in range(-1, 4):
            for dz in range(-1, 3):
                f.reserved[lx + dx, lz + dz] = True
    chest(f, 72, GROUND + 1, 494, "south")
    statue(f, 92, f.top(92, 504) + 1, 504, "man", "carry", 90.0)
    # 北西の猟師の家（干した毛皮）
    for x in range(94, 100):
        f.put(x, GROUND + 3, 162, block("white_wool") if x % 2 else block("light_gray_wool"))
    fence(f, 93, GROUND + 1, 162)
    fence(f, 93, GROUND + 2, 162)
    fence(f, 100, GROUND + 1, 162)
    fence(f, 100, GROUND + 2, 162)
    chest(f, 90, GROUND + 1, 164, "north")
    # 北東の見張りの塔（はしごで上がる。上に宝箱。村とオーロラがよく見える）
    tx, tz = LOOKOUT
    base = f.top(tx, tz)
    for x in range(tx - 3, tx + 4):
        for z in range(tz - 3, tz + 4):
            f.reserved[x, z] = True
            edge = x in (tx - 3, tx + 3) or z in (tz - 3, tz + 3)
            corner = x in (tx - 3, tx + 3) and z in (tz - 3, tz + 3)
            for y in range(base + 1, base + 17):
                if corner:
                    f.put(x, y, z, block("spruce_log", axis="y"))
                elif edge and y in (base + 6, base + 12):
                    f.put(x, y, z, block("spruce_planks"))
                elif edge and y == base + 17 - 1:
                    fence(f, x, y, z)
            f.put(x, base + 15, z, block("spruce_planks") if (x, z) != (tx, tz + 2) else block(
                "ladder", facing="north", waterlogged=False))
    for y in range(base + 1, base + 16):
        f.put(tx, y, tz + 2, block("ladder", facing="north", waterlogged=False))
        f.put(tx, y, tz + 3, block("spruce_planks"))
    for x in range(tx - 4, tx + 5):
        for z in range(tz - 4, tz + 5):
            f.put(x, base + 19, z, block("spruce_slab", type="bottom", waterlogged=False))
            f.put(x, base + 20, z, snow_layer(2))
    for (x, z) in ((tx - 3, tz - 3), (tx + 3, tz - 3), (tx - 3, tz + 3), (tx + 3, tz + 3)):
        for y in range(base + 17, base + 19):
            f.put(x, y, z, block("spruce_log", axis="y"))
    chest(f, tx - 1, base + 16, tz - 1, "south")
    f.put(tx + 1, base + 18, tz + 1, lantern(True))
    f.story_zones.append({"id": "aurora_lookout", "box": [tx - 3, base + 15, tz - 3, tx + 3, base + 19, tz + 3]})
    sign(f, tx + 1, f.top(tx + 1, tz + 6) + 1, tz + 6, 0, ["見張りの塔", "", "上からは、村と", "オーロラが見える"])


def props(f):
    """家のまわりの薪の山と樽（戸口と道をふさがない所だけ）"""
    for hid, (x0, z0, x1, z1, y0, _top) in f.houses.items():
        if hid in ("plaza_w", "plaza_e", "mayor", "inn"):
            continue
        r = R.at(x0, z0, 400)
        # 薪の山：家の横の壁ぞい（戸口のない側）
        spots = [(x1 + 1, z0 + 1, "z"), (x0 - 1, z0 + 1, "z"), (x0 + 1, z1 + 1, "x"), (x0 + 1, z0 - 1, "x")]
        sx, sz, axis = spots[int(r * 4)]
        cells = [(sx, sz + k) for k in range(3)] if axis == "z" else [(sx + k, sz) for k in range(3)]
        if all(f.path[x, z] == 0 and f.v[x, y0 + 1, z] in (0, f.index.get(snow_layer(1), -1), f.index.get(snow_layer(2), -1),
                                                            f.index.get(snow_layer(3), -1)) for x, z in cells):
            for (x, z) in cells:
                f.put(x, y0 + 1, z, block("stripped_spruce_log", axis="x" if axis == "z" else "z"))
                f.put(x, y0 + 2, z, block("stripped_spruce_log", axis="x" if axis == "z" else "z") if r < 0.6 else snow_layer(2))
                if r < 0.6:
                    f.put(x, y0 + 3, z, snow_layer(1))


# ================================================================ 木と岩

def trees(f):
    n = SIZE
    count = 0
    for k in range(9000):
        x = 8 + int(R.at(k, 1, 300) * (n - 16))
        z = 8 + int(R.at(k, 2, 300) * (n - 16))
        if f.reserved[max(0, x - 2):x + 3, max(0, z - 2):z + 3].any():
            continue
        top = f.top(x, z)
        if top > HEIGHT - 20 or f.river[x, z] < RIVER_HALF + 3 or f.pond[x, z] < POND_R + 3:
            continue
        # 村の中は少なく、まわりの林は多く
        village = 200 < x < 440 and 60 < z < 590
        woods = x < 140 or x > 500 or z < 60 or (z > 560)
        p = 0.08 if village else (0.75 if woods else 0.3)
        if R.at(k, 3, 300) > p:
            continue
        spruce(f, x, top + 1, z, 7 + int(R.at(k, 4, 300) * 7))
        count += 1
    return count


def spruce(f, x, y, z, h):
    for k in range(h):
        f.put(x, y + k, z, block("spruce_log", axis="y"))
    for k in range(2, h + 1):
        yy = y + k
        r = max(0, int((h - k) * 0.42) + (1 if k % 2 == 0 else 0))
        if k == h:
            r = 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + (1 if r > 1 else 0) or (dx == 0 and dz == 0 and k < h):
                    continue
                if f.v[x + dx, yy, z + dz] == 0:
                    f.put(x + dx, yy, z + dz, block("spruce_leaves", distance=1, persistent=True, waterlogged=False))
                    if f.v[x + dx, yy + 1, z + dz] == 0 and k % 2 == 0:
                        f.put(x + dx, yy + 1, z + dz, snow_layer(1))
    f.put(x, y + h, z, block("spruce_leaves", distance=1, persistent=True, waterlogged=False))
    f.put(x, y + h + 1, z, snow_layer(2))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            f.reserved[x + dx, z + dz] = True


# ================================================================ つなぎ目（柵・板ガラス）

CONNECT_SKIP = {"snow", "spruce_leaves", "lantern", "ladder", "spruce_sign", "spruce_wall_sign", "campfire", "spruce_door", "light",
                "amemaru:village_statue", "bell", "candle", "flower_pot", "spruce_pressure_plate"}


def finalize(f):
    def solid(x, y, z):
        n = f.name_at(x, y, z)
        return n is not None and n not in CONNECT_SKIP and not n.endswith("_slab") and not n.endswith("_stairs")

    for (x, y, z) in f.fences:
        st = f.get(x, y, z)
        if st is None or not st[0].endswith("fence"):
            continue
        sides = {}
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            n = f.name_at(x + dx, y, z + dz)
            sides[face] = bool(n is not None and (n.endswith("fence") or n.endswith("fence_gate") or solid(x + dx, y, z + dz)))
        f.put(x, y, z, block(st[0], waterlogged=False, **sides))
    for (x, y, z) in f.panes:
        st = f.get(x, y, z)
        if st is None or not st[0].endswith("pane"):
            continue
        sides = {}
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            n = f.name_at(x + dx, y, z + dz)
            sides[face] = bool(n is not None and (n.endswith("pane") or solid(x + dx, y, z + dz)))
        f.put(x, y, z, block(st[0], waterlogged=False, **sides))


# ================================================================ 階の定義

def spawn_zones(f):
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=44, air=0):
        y = f.top(cx, cz) + 1 + air
        return {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
                "respawn_seconds": respawn, "group": list(group), "activation": activation}
    return [
        # 村はずれ・雪の通り
        zone("gate_wolves", "frost_wolf", 340, 548, 10, 3, 70, (2, 3)),
        zone("street_statues_s", "statue_echo", 312, 520, 6, 2, 80, (1, 2), 30),
        zone("street_statues_n", "statue_echo", 312, 452, 6, 2, 80, (1, 2), 30),
        zone("street_wolves", "frost_wolf", 276, 490, 10, 3, 70, (2, 3)),
        zone("street_birds", "frost_bird", 330, 492, 10, 2, 80, (1, 2), 44, 6),
        # 市場
        zone("market_statues_w", "statue_echo", 300, 386, 7, 3, 80, (1, 2), 30),
        zone("market_statues_e", "statue_echo", 324, 400, 7, 3, 80, (1, 2), 30),
        zone("market_birds", "frost_bird", 312, 380, 12, 2, 80, (1, 2), 44, 8),
        # 西（子どもの家・川・川向こう）
        zone("lane_wolves", "frost_wolf", 214, 398, 10, 3, 70, (2, 3)),
        zone("river_wolves", "frost_wolf", 196, 340, 10, 3, 70, (2, 3)),
        zone("west_wolves_a", "frost_wolf", 110, 300, 12, 4, 70, (2, 4)),
        zone("west_wolves_b", "frost_wolf", 100, 390, 12, 3, 70, (2, 3)),
        zone("west_statues", "statue_echo", 80, 320, 6, 2, 80, (1, 2), 30),
        zone("south_bridge", "frost_wolf", 200, 530, 10, 3, 70, (2, 3)),
        zone("south_woods_bear", "frost_bear", 90, 470, 12, 1, 120, (1, 1)),
        # 北西（鍛冶屋・教会）
        zone("smithy_statues", "statue_echo", 268, 276, 6, 2, 80, (1, 2), 30),
        zone("chapel_statues", "statue_echo", 228, 246, 8, 3, 80, (1, 2), 30),
        zone("chapel_birds", "frost_bird", 206, 230, 12, 2, 80, (1, 2), 44, 10),
        zone("graveyard_wolves", "frost_wolf", 180, 260, 10, 3, 70, (2, 3)),
        # 東（牧場）
        zone("east_lane_wolves", "frost_wolf", 392, 368, 10, 3, 70, (2, 3)),
        zone("pasture_bears", "frost_bear", 500, 368, 14, 2, 110, (1, 1)),
        zone("pasture_bears_n", "frost_bear", 470, 296, 12, 1, 120, (1, 1)),
        zone("east_woods_bears", "frost_bear", 556, 330, 14, 2, 120, (1, 1)),
        zone("pasture_birds", "frost_bird", 486, 340, 14, 2, 80, (1, 2), 44, 8),
        # 北東（凍った池）
        zone("pond_birds_a", "frost_bird", 470, 182, 14, 3, 70, (1, 2), 44, 6),
        zone("pond_birds_b", "frost_bird", 492, 200, 12, 2, 70, (1, 2), 44, 6),
        zone("pond_wolves", "frost_wolf", 440, 226, 10, 3, 70, (2, 3)),
        zone("ne_bear", "frost_bear", 520, 150, 12, 1, 120, (1, 1)),
        # 北の通り
        zone("north_statues_a", "statue_echo", 312, 300, 6, 2, 80, (1, 2), 30),
        zone("north_statues_b", "statue_echo", 312, 232, 6, 3, 80, (1, 2), 30),
        zone("north_wolves", "frost_wolf", 340, 260, 10, 3, 70, (2, 3)),
        zone("north_birds", "frost_bird", 296, 200, 12, 2, 80, (1, 2), 44, 8),
        zone("seal_statues", "statue_echo", 312, 176, 6, 2, 80, (1, 2), 30),
        # 村のはずれ
        zone("farm_wolves", "frost_wolf", 420, 470, 12, 3, 70, (2, 3)),
        zone("farm_statues", "statue_echo", 400, 492, 6, 2, 80, (1, 2), 30),
        zone("camp_bear", "frost_bear", 96, 530, 12, 1, 120, (1, 1)),
        zone("camp_wolves", "frost_wolf", 110, 490, 10, 3, 70, (2, 3)),
        zone("hunter_wolves", "frost_wolf", 104, 210, 12, 3, 70, (2, 3)),
        zone("hunter_statues", "statue_echo", 92, 168, 6, 2, 80, (1, 2), 30),
        zone("lookout_birds", "frost_bird", 430, 136, 12, 2, 80, (1, 2), 44, 10),
        zone("lookout_wolves", "frost_wolf", 432, 178, 10, 3, 70, (2, 3)),
    ]


def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    exc, ezc = f.exit_circle
    data = {
        "_comment": "塔の6階「凍結した村」（tools/mapgen/build_floor6.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 2,
        "name": "凍結した村",
        "chapter_title": "第2章　凍てついた記憶",
        "recommended_level": [24, 31],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor6/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, GROUND + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, GROUND + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:ordo",
            "name": "雪の広場",
            "spawn": {"pos": [cx + 0.5, GROUND + 1, cz - 6.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, GROUND + 1, cz + 0.5], "radius": 50.0, "y_min": GROUND - 2, "y_max": GROUND + 24},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 24,
            "respawn_seconds": 150,
            "max_players": 8,
            # 第2章のはじめのボス（4人パーティくらい）。ひとりでも2人ぶんのHP【補完】
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1800,
            "exit": {"center": [exc + 0.5, GROUND + 1, ezc + 0.5], "radius": 2.6},
            "next": {"center": [exc + NEXT_DX + 0.5, GROUND + 1, ezc + 0.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "記憶の氷像が、すべて溶けた。凍りついた門が、静かにほどけていく……",
        "tablet": {"pos": list(f.tablet), "title": "村の子どもの日記",
                   "text": "きょう、おかあさんが、わたしの名前を呼ばなくなった。村の大人たちも、少しずつ、だれかの名前を忘れていく。"
                           "雪がふるたびに。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 雪の村（白く明るい霧。遠くの山は白くかすむ）。空はオーロラ（クライアントが描く。weather：雪が降る）
        "environment": {"fog": [40, 200], "fog_color": "#C9D9EC", "sky": "aurora", "weather": "snow"},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        "seal_requires": "statues+guardians",
        "hearths": [{"id": hid, "name": name, "pos": list(pos)} for hid, name, pos in f.hearths],
        "memory_statues": [{"id": mid, "name": name, "pos": list(pos), "event": "amemaru:floor6/memory_" + mid}
                           for mid, name, pos, _yaw in f.memories],
        "boss_marks": {"crystal_spots": f.crystal_spots, "plaza_center": [list(f.center)]},
        "story_zones": f.story_zones,
    }
    return data


# ================================================================ 書き出し

def save_pieces(f, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    names = [None] + [s for s in f.states[1:]]
    written = 0
    for px in range(SIZE // PIECE):
        for pz in range(SIZE // PIECE):
            sub = f.v[px * PIECE:(px + 1) * PIECE, :, pz * PIECE:(pz + 1) * PIECE]
            xs, ys, zs = np.nonzero(sub)
            order = np.lexsort((xs, zs, ys))
            palette, index = [], {}
            entries = []
            for k in order:
                x, y, z = int(xs[k]), int(ys[k]), int(zs[k])
                state = names[sub[x, y, z]]
                if state is None or state[0] == "air":
                    continue
                if state not in index:
                    index[state] = len(palette)
                    palette.append(state)
                e = {"state": Int(index[state]), "pos": List[Int]([Int(x), Int(y), Int(z)])}
                wx, wz = x + px * PIECE, z + pz * PIECE
                if (wx, y, wz) in f.nbt:
                    e["nbt"] = f.nbt[(wx, y, wz)]
                entries.append(Compound(e))
            pal = []
            for name, props in palette:
                c = {"Name": String(name if ":" in name else "minecraft:" + name)}
                if props:
                    c["Properties"] = Compound({k: String(v) for k, v in props})
                pal.append(Compound(c))
            root = nbtlib.File({
                "DataVersion": Int(DATA_VERSION),
                "size": List[Int]([Int(PIECE), Int(HEIGHT), Int(PIECE)]),
                "palette": List[Compound](pal),
                "blocks": List[Compound](entries),
                "entities": List[Compound]([]),
            }, gzipped=True)
            root.save(os.path.join(out_dir, "p%d_%d.nbt" % (px, pz)))
            written += 1
    return written


def build():
    f = Floor()
    terrain(f)
    fill_ground(f)
    for hid, x0, z0, w, d, door, kind, stories in HOUSES:
        house(f, hid, x0, z0, w, d, door, kind, stories)
    entrance(f)
    street(f)
    market(f)
    west(f)
    chapel(f)
    pasture(f)
    pond(f)
    north(f)
    plaza(f)
    outskirts(f)
    props(f)
    memories(f)
    f.tree_count = trees(f)
    finalize(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor6") if out else os.path.join(RES, "structures", "map", "floor6")
    json_path = os.path.join(out, "floor6.json") if out else os.path.join(RES, "amemaru", "floors", "floor6.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "氷像:", len(f.statues), "炉:", len(f.hearths),
          "記憶:", len(f.memories), "木:", f.tree_count)
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor6_figures
        floor6_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
