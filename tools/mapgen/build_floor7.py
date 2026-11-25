"""塔の7階「氷の森林」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor7.json）を書き出す。

Phase 0-A 9章の7階（確定）：鏡のような氷の木々の森。氷に映る自分の姿が、ときどき別の仲間の姿になる。雑魚は写し身（プレイヤーの
姿を真似る。名前が「？？？」）・氷角鹿・つらら蝙蝠・吹雪の精。ボスは鏡像獣リフレクタ（パーティメンバーの写し身を作る。ロスト状態
「薄れ」の初登場）。ギミックは、氷の鏡を動かして光を導く迷路と、最奥の「合言葉の扉」（パーティ全員が同じ合言葉をチャットに打つと
開く）。古代記録は「森林監視所の記録」（「侵食域では、隣にいる者が誰か分からなくなる。合言葉を決めよ」）。7日目（リリアが
プレイヤーを忘れる）。ロストの侵食：映り込みが間違っている。名前が読めなくなる。

使い方:
  python3 tools/mapgen/build_floor7.py                        # Modの structures/map/floor7/ と floors/floor7.json を書き直す
  python3 tools/mapgen/build_floor7.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

決まり（11月25日【補完】。docs/phase8_iceforest.md）：
  - 672×672（6階より広い）、高さ96。まわりは雪の山。森を東西に横切る「氷の崖」が2本あり、門は光の迷路を解くと開く
  - 区画：南の入口の林（鏡の木立）→ 入口の鏡の庭（光の迷路1。氷の崖1の門）→ 分かれ道 → 西の鏡の庭（光の迷路2。氷の崖2の西の門）・
    西の林（名のある魔物「白角」）／東の鏡の庭（光の迷路3。氷の崖2の東の門）・東の凍った滝（名のある魔物「吹雪の大精」）→
    森林監視所の跡（古代記録）→ 合言葉の扉（封印。氷の崖3）→ 鏡の湖（ボス）
  - 封印は「光の迷路をすべて解いた（みんな）＋名のある魔物2体（人ごと）＋合言葉（人ごと）」（lights+guardians+passphrase）
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
from nbtlib.tag import Compound, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

FLOOR = 7
SIZE = 672
HEIGHT = 96
PIECE = 48
ORIGIN = (28672 - SIZE // 2, 60, -SIZE // 2)
GROUND = 16             # 森の地面のブロック（立つのは 17）
CHEST_LOOT = "amemaru:chests/floor7_iceforest"

ENTRY = (336, 644)
LAKE = (336, 110)
LAKE_R = 34             # 鏡の湖の氷の面の半径
BARRIERS = [500, 300, 176]          # 氷の崖（z）。3本目の真ん中が合言葉の扉（封印）
GATE1 = (330, 342)                  # 崖1の門（x0, x1）
GATE2_W = (196, 204)
GATE2_E = (468, 476)
SEAL = (331, 341)                   # 崖3の封印（x0, x1）
STATION = (336, 246)
GARDENS = {
    # id: (名前, 中心, 光の泉（中心からのずれ）, 向き, 受け石, 曲がり角（ずれ）, おとりの鏡, 氷の柱, 開く門）
    "entry": ("入口の鏡の庭", (336, 532), (-5, 8), "north", (-8, -4), [(-5, 2), (3, 2), (3, -4)], [(0, 5), (-2, -1)],
              [(5, 5), (-6, -6), (6, -1)], "gate1"),
    "west": ("西の鏡の庭", (196, 400), (8, 5), "west", (-8, 4), [(2, 5), (2, -2), (-4, -2), (-4, 4)],
             [(5, -5), (-1, 1), (5, 2)], [(-2, -5), (-6, 0), (6, -3), (0, 3), (-1, -6), (4, 6)], "gate2w"),
    "east": ("東の鏡の庭", (472, 400), (2, 8), "north", (8, 1), [(2, 4), (-5, 4), (-5, -3), (4, -3), (4, 1)],
             [(-2, 1), (2, -6), (-6, 0), (6, 5)], [(3, 6), (-3, 6), (-7, -6), (0, -1), (6, -6), (-2, -5), (2, 2), (7, -2)], "gate2e"),
}

# 道（折れ線, 半幅）
MAIN_SOUTH = [(336, 640), (336, 560), (336, 541)]
MAIN_MID = [(336, 523), (336, 498), (336, 470)]
WEST_ROAD = [(336, 470), (300, 452), (250, 430), (204, 415), (196, 409)]
EAST_ROAD = [(336, 470), (380, 452), (430, 432), (468, 415), (472, 409)]
WEST_NORTH = [(196, 391), (200, 350), (200, 302), (200, 296), (232, 280), (290, 262), (330, 252)]
EAST_NORTH = [(472, 391), (472, 350), (472, 302), (472, 296), (440, 280), (382, 262), (342, 252)]
STATION_NORTH = [(336, 240), (336, 200), (336, 179)]
LAKE_ROAD = [(336, 173), (336, 146)]
WEST_GLADE = [(196, 409), (150, 418), (110, 426), (92, 430)]
EAST_FALLS = [(472, 409), (520, 420), (566, 428), (586, 432)]
PATHS = [(MAIN_SOUTH, 3.0), (MAIN_MID, 2.6), (WEST_ROAD, 2.2), (EAST_ROAD, 2.2), (WEST_NORTH, 2.0), (EAST_NORTH, 2.0),
         (STATION_NORTH, 2.6), (LAKE_ROAD, 2.6), (WEST_GLADE, 1.8), (EAST_FALLS, 1.8)]
WEST_LAIR = (92, 432)
EAST_LAIR = (588, 432)


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


R = Rand(20261125 + 7)

ROCK = [("stone", 5), ("andesite", 2), ("tuff", 1), ("calcite", 1)]
PATH_STONE = [("packed_ice", 3), ("snow_block", 3), ("polished_diorite", 1), ("calcite", 1)]
CLIFF = [("packed_ice", 6), ("blue_ice", 2), ("snow_block", 2), ("calcite", 1)]
CROWN = [("white_stained_glass", 4), ("light_blue_stained_glass", 3), ("ice", 2), ("glass", 1)]


def thing(index=0, group=""):
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


def smooth_noise(n, cell, seed):
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


def smooth1(x, seed, cell=23.0):
    """1つの向きの、なめらかな値のノイズ（−1〜1）"""
    i = int(math.floor(x / cell))
    t = x / cell - i
    t = t * t * (3 - 2 * t)
    a = R.at(i, seed, 90) * 2 - 1
    b = R.at(i + 1, seed, 90) * 2 - 1
    return a * (1 - t) + b * t


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
        self.reserved = np.zeros((SIZE, SIZE), dtype=bool)
        self.path = np.zeros((SIZE, SIZE), dtype=np.uint8)
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.seal_cells = []
        self.story_zones = []
        self.mazes = []
        self.answers = {}          # 光の迷路の解き方（鏡ごとの向き。check_floor7.py が使う。階の定義には書かない）
        self.fences = []
        self.travel_stone = None
        self.tablet = None
        self.exit_circle = None
        self.passphrase_area = None
        self.tree_count = 0

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

    def top(self, x, z):
        return int(self.height[x, z])


def snow_layer(n=1):
    return block("snow", layers=n)


def lantern(hanging=False):
    return block("lantern", hanging=hanging, waterlogged=False)


def fence(f, x, y, z, mat="spruce_fence"):
    f.put(x, y, z, block(mat, north=False, south=False, east=False, west=False, waterlogged=False))
    f.fences.append((x, y, z))


def sign(f, x, y, z, rotation, lines):
    f.put(x, y, z, block("spruce_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))


def chest(f, x, y, z, facing, guardian=""):
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing), thing(len(f.chests), ""))
    f.chests.append(((x, y, z), guardian))


def waypoint(f, wid, name, x, y, z, yaw):
    index = len(f.waypoints)
    f.put(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


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
                for yy in range(y + 1, y + 4):
                    f.put(x, yy, z, None)


def flatten(f, x0, z0, x1, z1, surface="snow_block", clear=5):
    """四角い所を GROUND の高さにならす（下は土と石でうめ、上は clear ブロックあける）"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            top = f.top(x, z)
            for y in range(min(top, GROUND) - 2, GROUND):
                f.put(x, y, z, block("dirt" if y >= GROUND - 2 else "stone"))
            f.put(x, GROUND, z, block(surface))
            for y in range(GROUND + 1, max(top, GROUND) + clear + 1):
                f.put(x, y, z, None)
            f.height[x, z] = GROUND
            f.reserved[x, z] = True


def lamp(f, x, z, tall=3):
    """氷の灯籠（白い柱と、上に淡い灯）"""
    y = f.top(x, z) + 1
    for k in range(tall):
        f.put(x, y + k, z, block("calcite") if k < tall - 1 else block("polished_diorite_wall", up=True, north="none", south="none",
                                                                          east="none", west="none", waterlogged=False))
    f.put(x, y + tall, z, block("sea_lantern"))
    f.put(x, y + tall + 1, z, snow_layer(2))
    f.reserved[x, z] = True


# ================================================================ 地形

def terrain(f):
    n = SIZE
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    h = GROUND + 1.4 * smooth_noise(n, 64, 71) + 0.8 * smooth_noise(n, 23, 72) + 0.3 * smooth_noise(n, 9, 73)
    edge = np.minimum(np.minimum(X, n - X), np.minimum(Z, n - Z))
    ridge = 44.0 + 10.0 * smooth_noise(n, 31, 74) + 4.0 * smooth_noise(n, 11, 75)
    h += ridge * (1.0 - smoothstep(0.0, 52.0, edge)) ** 1.5
    path_d = np.full((n, n), 1e9)
    for pts, half in PATHS:
        d = polyline_distance(pts) - half
        path_d = np.minimum(path_d, d)
        f.path[(d <= 0) & (f.path == 0)] = 1 if half >= 2.6 else 2
    w = 1.0 - smoothstep(0.0, 7.0, path_d)
    h = h * (1 - w) + GROUND * w
    flats = [(ENTRY[0], ENTRY[1] - 6, 14, 24), (STATION[0], STATION[1], 18, 28), (LAKE[0], LAKE[1], LAKE_R + 10, LAKE_R + 22),
             (WEST_LAIR[0], WEST_LAIR[1], 12, 22), (EAST_LAIR[0], EAST_LAIR[1], 12, 22), (336, 188, 12, 18)]
    for gid, (_n, (gx, gz), *_rest) in GARDENS.items():
        flats.append((gx, gz, 13, 20))
    for (cx, cz, r0, r1) in flats:
        d = np.hypot(X - cx, Z - cz)
        ww = 1.0 - smoothstep(r0, r1, d)
        h = h * (1 - ww) + GROUND * ww
    f.height = np.clip(np.round(h), 4, HEIGHT - 8).astype(int)
    f.height = np.where(f.path > 0, GROUND, f.height)
    for gid, (_n, (gx, gz), *_rest) in GARDENS.items():
        f.height[gx - 10:gx + 11, gz - 10:gz + 11] = GROUND
    lx, lz = LAKE
    d = np.hypot(X - lx, Z - lz)
    f.lake = d
    f.height = np.where(d < LAKE_R + 8, GROUND, f.height)


def fill_ground(f):
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
                    st = R.pick(CLIFF, x, y, z, 3)
                elif y >= top - 2:
                    st = "dirt"
                else:
                    st = R.pick(ROCK, x, y, z, 2)
                f.put(x, y, z, block(st))
            if f.lake[x, z] < LAKE_R:
                f.put(x, top, z, block("ice" if R.at(x // 3, z // 3, 5) < 0.6 else "packed_ice"))
                f.put(x, top - 1, z, block("packed_ice"))
                f.reserved[x, z] = True
                continue
            kind = f.path[x, z]
            if kind:
                f.put(x, top, z, block(R.pick(PATH_STONE, x, z, 6)))
                if R.at(x, z, 7) < 0.25:
                    f.put(x, top + 1, z, snow_layer(1))
                f.reserved[x, z] = True
            elif cliff and R.at(x, z, 10) < 0.5:
                f.put(x, top, z, block("packed_ice"))
            else:
                f.put(x, top, z, block("snow_block"))
                if R.at(x, z, 12) < 0.6 and top < HEIGHT - 2:
                    f.put(x, top + 1, z, snow_layer(1 + int(R.at(x, z, 13) * 2.2)))


# ================================================================ 氷の崖と門

def barriers(f):
    """森を東西に横切る、登れない氷の崖（高さ14。上はぎざぎざ）。門と封印の所だけあける"""
    openings = {BARRIERS[0]: [GATE1], BARRIERS[1]: [GATE2_W, GATE2_E], BARRIERS[2]: [SEAL]}
    for bz in BARRIERS:
        for x in range(0, SIZE):
            if any(a - 2 <= x <= b + 2 for a, b in openings[bz]):
                continue
            for dz in range(-2, 3):
                z = bz + dz
                top = f.top(x, z)
                hh = 13 + int(R.at(x // 2, z, 40) * 4) - abs(dz)
                for y in range(top + 1, min(HEIGHT - 2, max(GROUND + hh, top + 10))):
                    f.put(x, y, z, block(R.pick(CLIFF, x, y, z, 41)))
                f.reserved[x, z] = True
        # 門の両側の柱（太い氷の柱）
        for a, b in openings[bz]:
            for x in (a - 2, a - 1, b + 1, b + 2):
                for dz in range(-2, 3):
                    for y in range(min(GROUND + 1, f.top(x, bz + dz) + 1), GROUND + 16):
                        f.put(x, y, bz + dz, block("blue_ice" if (y + dz) % 4 else "packed_ice"))
                    f.reserved[x, bz + dz] = True
            for x in range(a - 2, b + 3):
                for dz in range(-2, 3):
                    for y in range(GROUND + 10, GROUND + 13):
                        f.put(x, y, bz + dz, block("packed_ice"))
                    f.reserved[x, bz + dz] = True
            for x in range(a, b + 1):
                for dz in range(-2, 3):
                    f.reserved[x, bz + dz] = True
                    f.height[x, bz + dz] = GROUND
        # 崖の手前と奥の、木のない帯（木の冠を伝って崖の上へ登れないように。幅は8〜13で、ゆるやかに揺れる）
        for x in range(SIZE):
            for side in (-1, 1):
                w = 8 + int(round((smooth1(x, bz * 2 + side) * 0.5 + 0.5) * 5))
                z0, z1 = (bz - w, bz) if side < 0 else (bz, bz + w)
                f.reserved[x, max(0, z0):z1 + 1] = True


def gate_box(name):
    if name == "gate1":
        a, b, bz = GATE1[0], GATE1[1], BARRIERS[0]
    elif name == "gate2w":
        a, b, bz = GATE2_W[0], GATE2_W[1], BARRIERS[1]
    else:
        a, b, bz = GATE2_E[0], GATE2_E[1], BARRIERS[1]
    return [a, GROUND + 1, bz - 1, b, GROUND + 9, bz + 1]


def gates(f):
    """光の迷路で開く氷の門（はじめは閉じている：青い氷）"""
    for name in ("gate1", "gate2w", "gate2e"):
        x0, y0, z0, x1, y1, z1 = gate_box(name)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    f.put(x, y, z, block("blue_ice"))
                f.put(x, GROUND, z, block("polished_diorite"))


# ================================================================ 光の迷路（鏡の庭）

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def reflect(d, slash):
    """LightMazeService.reflect と同じ（「／」：東→北・北→東・西→南・南→西。「＼」：東→南・南→東・西→北・北→西）"""
    table = {("east", True): "north", ("north", True): "east", ("west", True): "south", ("south", True): "west",
             ("east", False): "south", ("south", False): "east", ("west", False): "north", ("north", False): "west"}
    return table[(d, slash)]


def trace(source, direction, target, mirrors, walls, limit=8):
    """庭の中の光をたどる（ずれの座標）。mirrors：{ずれ: slash}。届いたら (True, 通ったマス)"""
    x, z = source
    d = direction
    cells = []
    for _ in range(200):
        dx, dz = DIRS[d]
        x, z = x + dx, z + dz
        if (x, z) == target:
            cells.append((x, z))
            return True, cells
        if (x, z) in mirrors:
            cells.append((x, z))
            d = reflect(d, mirrors[(x, z)])
            continue
        if (x, z) in walls or max(abs(x), abs(z)) >= limit:
            return False, cells
        cells.append((x, z))
    return False, cells


def light_garden(f, gid):
    name, (cx, cz), src, direction, target, turns, decoys, pillars, gate = GARDENS[gid]
    y = GROUND + 1
    # 解き方：曲がり角ごとに、入ってくる向きと出ていく向きから、鏡の向きを決める
    answer = {}
    d = direction
    x, z = src
    for (tx, tz) in turns:
        out = None
        if tx == x:
            out = "south" if tz > z else "north"
        elif tz == z:
            out = "east" if tx > x else "west"
        assert out == d, (gid, "曲がり角まで、まっすぐ行けません", (x, z), (tx, tz), d, out)
        x, z = tx, tz
        nxt = turns[turns.index((tx, tz)) + 1] if turns.index((tx, tz)) + 1 < len(turns) else target
        if nxt[0] == x:
            want = "south" if nxt[1] > z else "north"
        else:
            want = "east" if nxt[0] > x else "west"
        slash = [s for s in (True, False) if reflect(d, s) == want]
        assert slash, (gid, "鏡で曲がれません", d, want)
        answer[(tx, tz)] = slash[0]
        d = want
    walls = set(pillars)
    mirrors = dict(answer)
    ok, cells = trace(src, direction, target, mirrors, walls)
    assert ok, (gid, "解き方で光が届きません", cells)
    for c in pillars + decoys:
        assert c not in cells, (gid, "光の道の上に柱かおとりがあります", c)
    # はじめの向き：解き方から、1つおきに逆にする（おとりは交互）
    start = {}
    for k, c in enumerate(turns):
        start[c] = answer[c] if k % 2 == 1 else not answer[c]
    for k, c in enumerate(decoys):
        start[c] = k % 2 == 0
    ok0, _ = trace(src, direction, target, start, walls)
    if ok0:
        start[turns[1]] = not start[turns[1]]
        ok0, _ = trace(src, direction, target, start, walls)
    assert not ok0, (gid, "はじめから光が届いてしまいます")
    # 庭：床（氷と雪の市松）・まわりの低い氷の塀（南に出入り口）・柱・灯
    for dx in range(-10, 11):
        for dz in range(-10, 11):
            wx, wz = cx + dx, cz + dz
            ring = max(abs(dx), abs(dz))
            f.reserved[wx, wz] = True
            for yy in range(y, y + 6):
                f.put(wx, yy, wz, None)
            if ring <= 7:
                f.put(wx, GROUND, wz, block("packed_ice" if (dx + dz) % 2 == 0 else "snow_block"))
            elif ring == 8:
                f.put(wx, GROUND, wz, block("polished_diorite"))
                gap = abs(dz) == 8 and abs(dx) <= 1
                if not gap:
                    f.put(wx, y, wz, block("packed_ice"))
                    f.put(wx, y + 1, wz, block("packed_ice"))
                    f.put(wx, y + 2, wz, snow_layer(2))
            else:
                f.put(wx, GROUND, wz, block("snow_block"))
    for (px, pz) in pillars:
        f.put(cx + px, y, cz + pz, block("packed_ice"))
        f.put(cx + px, y + 1, cz + pz, block("blue_ice"))
        f.put(cx + px, y + 2, cz + pz, snow_layer(3))
    # 光の泉・鏡・受け石（どの階のどの迷路か）
    sx, sz = cx + src[0], cz + src[1]
    f.put(sx, y, sz, block("amemaru:light_spring", facing=direction), thing(0, gid))
    f.put(sx, y + 1, sz, None)
    f.put(sx, y + 2, sz, None)
    mirror_list = []
    for k, c in enumerate(turns + decoys):
        mx, mz = cx + c[0], cz + c[1]
        f.put(mx, y, mz, block("amemaru:ice_mirror", slash=start[c]), thing(k, gid))
        mirror_list.append({"pos": [mx, y, mz], "slash": start[c]})
    txx, tzz = cx + target[0], cz + target[1]
    f.put(txx, y, tzz, block("amemaru:light_target", lit=False), thing(0, gid))
    f.put(txx, y + 1, tzz, block("packed_ice"))
    for (lx, lz) in ((-9, -9), (9, -9), (-9, 9), (9, 9)):
        lamp(f, cx + lx, cz + lz, 2)
    sign(f, cx + 3, y, cz + 10, 0, [name, "", "鏡を回して", "光を受け石へ"])
    f.answers[gid] = [answer[c] for c in turns] + [start[c] for c in decoys]
    f.mazes.append({"id": gid, "name": name, "source": [sx, y, sz], "direction": direction, "target": [txx, y, tzz],
                    "mirrors": mirror_list, "door": {"box": gate_box(gate), "block": "minecraft:blue_ice"}})


# ================================================================ 入口・監視所・名のある魔物・合言葉の扉・鏡の湖

def entrance(f):
    cx, cz = ENTRY
    return_circle(f, cx, GROUND, cz)
    for x in range(cx - 16, cx + 17):
        for z in range(cz - 16, cz + 6):
            f.reserved[x, z] = True
    tx, tz = cx - 8, cz - 6
    f.put(tx, GROUND + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, GROUND + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, GROUND + 1, tz)
    sign(f, cx + 2, GROUND + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 5, GROUND + 1, cz - 12, 0, ["7階", "氷の森林", "", "鏡に映る仲間に、ご用心"])
    chest(f, cx + 12, GROUND + 1, cz - 4, "west")
    for (dx, dz) in ((-10, -12), (10, -12), (-7, 2), (7, 2)):
        lamp(f, cx + dx, cz + dz)
    f.story_zones.append({"id": "mirror_grove", "box": [cx - 8, GROUND, 566, cx + 8, GROUND + 6, 586]})


def station(f):
    """森林監視所の跡（崩れた石の見張り所。中に古代記録の書見台）"""
    cx, cz = STATION
    x0, x1, z0, z1 = cx - 8, cx + 8, cz - 7, cz + 7
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 3, z1 + 4):
            f.reserved[x, z] = True
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            f.put(x, GROUND, z, block("deepslate_tiles" if not edge else "deepslate_bricks"))
            for y in range(GROUND + 1, GROUND + 8):
                broken = R.at(x, z, y, 50) < (0.15 + 0.08 * (y - GROUND))
                if edge and not broken:
                    f.put(x, y, z, block("deepslate_bricks" if R.at(x, y, z, 51) < 0.7 else "cracked_deepslate_bricks"))
                else:
                    f.put(x, y, z, None)
    for y in range(GROUND + 1, GROUND + 4):
        for x in range(cx - 1, cx + 2):
            f.put(x, y, z1, None)
    # 見張りの塔（北東の角。半分崩れている）
    for y in range(GROUND + 1, GROUND + 14):
        for (x, z) in ((x1, z0), (x1 - 1, z0), (x1, z0 + 1)):
            if y < GROUND + 11 or R.at(x, y, z, 52) < 0.5:
                f.put(x, y, z, block("deepslate_bricks"))
    f.put(cx, GROUND + 1, cz - 3, block("lectern", facing="south", has_book=False, powered=False))
    f.tablet = (cx, GROUND + 1, cz - 3)
    chest(f, x0 + 2, GROUND + 1, z0 + 2, "south")
    chest(f, x1 - 2, GROUND + 1, z1 - 2, "north")
    f.put(cx - 4, GROUND + 1, cz - 4, lantern())
    f.put(cx + 4, GROUND + 1, cz - 4, lantern())
    sign(f, cx + 3, GROUND + 1, z1 + 2, 0, ["森林監視所の跡", "", "古い記録が", "残っているらしい"])
    f.story_zones.append({"id": "watch_station", "box": [x0, GROUND, z0, x1, GROUND + 6, z1]})


def lairs(f):
    """西の林の空き地（氷角鹿の主「白角」）と、東の凍った滝（吹雪の大精）"""
    wx, wz = WEST_LAIR
    for x in range(wx - 12, wx + 13):
        for z in range(wz - 12, wz + 13):
            if math.hypot(x - wx, z - wz) < 12:
                f.reserved[x, z] = True
                f.put(x, GROUND, z, block("snow_block"))
                for y in range(GROUND + 1, GROUND + 8):
                    f.put(x, y, z, None)
    for k in range(10):
        a = k / 10 * math.tau
        x = int(round(wx + math.cos(a) * 10))
        z = int(round(wz + math.sin(a) * 10))
        ice_tree(f, x, GROUND + 1, z, 9 + k % 3)
    chest(f, wx, GROUND + 1, wz - 5, "south", guardian="white_horn")
    f.guardians.append({"id": "white_horn", "enemy": "amemaru:ice_stag", "variant": "amemaru:ice_stag_lord",
                        "pos": [wx + 0.5, GROUND + 1, wz + 4.5], "respawn_seconds": 300, "activation": 26, "pillar": -1})
    sign(f, wx + 4, GROUND + 1, wz + 7, 8, ["白角の空き地", "", "氷の角の主が", "ここを守っている"])
    ex, ez = EAST_LAIR
    for x in range(ex - 13, ex + 14):
        for z in range(ez - 13, ez + 14):
            if math.hypot(x - ex, z - ez) < 13:
                f.reserved[x, z] = True
                f.put(x, GROUND, z, block("ice" if math.hypot(x - ex, z - ez) < 6 else "packed_ice"))
                for y in range(GROUND + 1, GROUND + 10):
                    f.put(x, y, z, None)
    # 東の岩壁（雪をかぶった岩と氷。高さ20くらい）と、そこから流れ落ちたまま凍りついた滝
    for z in range(ez - 18, ez + 19):
        bow = int(round(((z - ez) / 18.0) ** 2 * 5))
        for x in range(ex + 12 - bow, ex + 20 - bow):
            if not (0 <= x < SIZE):
                continue
            top_y = GROUND + 18 + int(R.at(x // 2, z // 2, 70) * 5) - abs(z - ez) // 6
            for y in range(max(1, f.top(x, z) - 2), top_y):
                f.put(x, y, z, block(R.pick([("stone", 4), ("packed_ice", 3), ("calcite", 1), ("andesite", 1)], x, y, z, 71)))
            f.put(x, top_y, z, block("snow_block"))
            f.put(x, top_y + 1, z, snow_layer(2))
            f.reserved[x, z] = True
    for z in range(ez - 6, ez + 7):
        for y in range(GROUND + 1, GROUND + 21):
            depth = 1 + (GROUND + 21 - y) // 7 + (1 if (z + y) % 5 == 0 else 0)
            for x in range(ex + 12 - depth, ex + 13):
                f.put(x, y, z, block("blue_ice" if (y + z) % 3 else "packed_ice"))
    chest(f, ex - 2, GROUND + 1, ez - 6, "south", guardian="great_spirit")
    f.guardians.append({"id": "great_spirit", "enemy": "amemaru:blizzard_spirit", "variant": "amemaru:blizzard_great_spirit",
                        "pos": [ex + 0.5, GROUND + 3, ez + 0.5], "respawn_seconds": 300, "activation": 28, "pillar": -1})
    sign(f, ex - 6, GROUND + 1, ez + 7, 8, ["凍った滝", "", "吹雪の大精が", "ここに棲む"])


def passphrase_door(f):
    """合言葉の扉（崖3の真ん中。ボスの部屋の封印）と、その前の言う所"""
    a, b = SEAL
    bz = BARRIERS[2]
    for x in range(a, b + 1):
        for y in range(GROUND + 1, GROUND + 10):
            f.put(x, y, bz, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, bz))
        for dz in (-2, -1, 1, 2):
            for y in range(GROUND + 1, GROUND + 10):
                f.put(x, y, bz + dz, None)
    # 扉の前の、言う所（円い白い床と、合言葉の碑）
    cx, cz = (a + b) // 2, bz + 8
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if d <= 6.5:
                f.put(x, GROUND, z, block("smooth_quartz" if d < 5 else "polished_diorite"))
                for y in range(GROUND + 1, GROUND + 6):
                    f.put(x, y, z, None)
                f.reserved[x, z] = True
    for x in (cx - 6, cx + 6):
        for y in range(GROUND + 1, GROUND + 4):
            f.put(x, y, cz, block("calcite"))
        f.put(x, GROUND + 4, cz, block("sea_lantern"))
    sign(f, cx - 3, GROUND + 1, cz + 5, 8, ["合言葉の扉", "", "パーティの全員で", "同じ言葉をチャットへ"])
    sign(f, cx + 3, GROUND + 1, cz + 5, 8, ["", "侵食域では", "隣にいる者が", "誰か分からなくなる"])
    f.passphrase_area = [cx - 6, GROUND, bz + 2, cx + 6, GROUND + 6, cz + 6]
    f.story_zones.append({"id": "passphrase_door", "box": [cx - 6, GROUND, bz + 3, cx + 6, GROUND + 6, cz + 6]})


def lake(f):
    """鏡の湖（ボスの部屋）。凍った湖面のまわりに、鏡の木を輪に並べる"""
    cx, cz = LAKE
    for k in range(16):
        a = k / 16 * math.tau
        x = int(round(cx + math.cos(a) * (LAKE_R + 4)))
        z = int(round(cz + math.sin(a) * (LAKE_R + 4)))
        if abs(x - cx) <= 6 and z > cz:
            continue    # 南の封印の道はあける
        if abs(x - cx) <= 14 and z < cz:
            continue    # 北の岸（出口の円）もあける
        ice_tree(f, x, GROUND + 1, z, 11 + k % 4)
    for x in range(cx - LAKE_R - 8, cx + LAKE_R + 9):
        for z in range(cz - LAKE_R - 8, cz + LAKE_R + 9):
            if 0 <= x < SIZE and 0 <= z < SIZE and math.hypot(x - cx, z - cz) < LAKE_R + 8:
                f.reserved[x, z] = True
    # 湖の真ん中の、鏡の模様（床に埋めた青い氷の輪と、光る石。ボスの目じるし。床と同じ高さ）
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 0.6:
                f.put(x, GROUND, z, block("sea_lantern"))
            elif 2.5 <= d <= 3.5:
                f.put(x, GROUND, z, block("blue_ice"))
    # 出口の円と次の階への円（湖の北の岸）
    ex, ez = cx - 4, cz - LAKE_R - 4
    for x in range(ex - 6, ex + NEXT_DX + 7):
        for z in range(ez - 6, ez + 7):
            f.height[x, z] = GROUND
            f.put(x, GROUND, z, block("snow_block"))
            for y in range(GROUND + 1, GROUND + 6):
                f.put(x, y, z, None)
            f.reserved[x, z] = True
    return_circle(f, ex, GROUND, ez)
    next_circle(f.put, ex + NEXT_DX, GROUND, ez)
    f.exit_circle = (ex, ez)
    # 湖のまわりの見えない灯
    for k in range(24):
        a = k / 24 * math.tau
        x = int(round(cx + math.cos(a) * (LAKE_R - 3)))
        z = int(round(cz + math.sin(a) * (LAKE_R - 3)))
        f.put(x, GROUND + 4, z, block("light", level=11, waterlogged=False))


def scatter_chests(f):
    spots = [(352, 600, "west"), (318, 560, "east"), (300, 488, "east"), (380, 486, "west"), (250, 446, "south"),
             (430, 446, "south"), (150, 380, "east"), (530, 380, "west"), (230, 330, "south"), (440, 330, "south"),
             (300, 220, "east"), (380, 220, "west")]
    for (x, z, facing) in spots:
        y = f.top(x, z) + 1
        for yy in range(y, y + 3):
            f.put(x, yy, z, None)
        chest(f, x, y, z, facing)
        f.reserved[x, z] = True


def waypoints(f):
    for (wid, name, x, z, yaw) in (("gate1", "崖の門の北", 341, 488, 180), ("west", "西の鏡の庭", 207, 418, 180),
                                   ("east", "東の鏡の庭", 483, 418, 180), ("station", "森林監視所の跡", 347, 262, 180)):
        flatten(f, x - 2, z - 3, x + 2, z + 2, "polished_diorite")
        waypoint(f, wid, name, x, GROUND + 1, z, yaw)


# ================================================================ 氷の木（鏡のような木）

def ice_tree(f, x, y, z, h):
    """透きとおった氷の幹と、白と淡い青のガラスの冠（鏡のように光を返す）"""
    if not (2 <= x < SIZE - 2 and 2 <= z < SIZE - 2) or y + h + 3 >= HEIGHT:
        return
    for k in range(h):
        f.put(x, y + k, z, block("packed_ice" if k < h // 2 else "ice"))
    for k in range(3, h + 2):
        yy = y + k
        r = max(0, int((h + 2 - k) * 0.38))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + (1 if r > 1 else 0):
                    continue
                if (dx, dz) == (0, 0) and k < h:
                    continue
                if f.v[x + dx, yy, z + dz] == 0 and R.at(x + dx, yy, z + dz, 60) < 0.86:
                    f.put(x + dx, yy, z + dz, block(R.pick(CROWN, x + dx, yy, z + dz, 61)))
    f.put(x, y + h + 2, z, block("white_stained_glass"))
    f.put(x, y + h + 3, z, snow_layer(1))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            f.reserved[x + dx, z + dz] = True


def spruce(f, x, y, z, h):
    for k in range(h):
        f.put(x, y + k, z, block("spruce_log", axis="y"))
    for k in range(2, h + 1):
        yy = y + k
        r = max(0, int((h - k) * 0.42) + (1 if k % 2 == 0 else 0))
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


def trees(f):
    n = SIZE
    count = 0
    for k in range(16000):
        x = 6 + int(R.at(k, 1, 300) * (n - 12))
        z = 6 + int(R.at(k, 2, 300) * (n - 12))
        if f.reserved[max(0, x - 2):x + 3, max(0, z - 2):z + 3].any():
            continue
        top = f.top(x, z)
        if top > HEIGHT - 26:
            continue
        if R.at(k, 3, 300) > 0.7:
            continue
        if R.at(k, 4, 300) < 0.72:
            ice_tree(f, x, top + 1, z, 8 + int(R.at(k, 5, 300) * 8))
        else:
            spruce(f, x, top + 1, z, 7 + int(R.at(k, 6, 300) * 6))
        count += 1
    return count


CONNECT_SKIP = {"snow", "spruce_leaves", "lantern", "spruce_sign", "light", "sea_lantern"}


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
            sides[face] = bool(n is not None and (n.endswith("fence") or solid(x + dx, y, z + dz)))
        f.put(x, y, z, block(st[0], waterlogged=False, **sides))


# ================================================================ 階の定義

def spawn_zones(f):
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=44, air=0):
        y = f.top(cx, cz) + 1 + air
        return {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
                "respawn_seconds": respawn, "group": list(group), "activation": activation}
    return [zone(*z) for z in SPAWNS]


def keep_spawns_clear(f):
    """湧く所のまん中のまわり（7×7）には、木を植えない"""
    for z in SPAWNS:
        cx, cz = z[2], z[3]
        f.reserved[max(0, cx - 3):cx + 4, max(0, cz - 3):cz + 4] = True


SPAWNS = [
        # 南の林（鏡の木立）
    # (id, 敵, x, z, 半径, 数, 湧き直す秒, 群れ, 近づくと動く距離, 宙に浮く高さ)
    # 南の林（鏡の木立）
    ("grove_mimics", "mimic", 336, 600, 10, 2, 80, (1, 2), 30),
    ("grove_stags", "ice_stag", 300, 610, 12, 3, 70, (2, 3)),
    ("grove_bats", "icicle_bat", 372, 590, 12, 3, 70, (2, 3), 44, 6),
    ("entry_garden_bats", "icicle_bat", 336, 548, 10, 2, 80, (1, 2), 44, 7),
    # 崖1と崖2の間
    ("fork_mimics", "mimic", 336, 470, 10, 2, 80, (1, 2), 30),
    ("west_road_stags", "ice_stag", 270, 440, 12, 3, 70, (2, 3)),
    ("east_road_stags", "ice_stag", 404, 440, 12, 3, 70, (2, 3)),
    ("west_garden_spirits", "blizzard_spirit", 196, 430, 12, 2, 80, (1, 2), 44, 4),
    ("east_garden_spirits", "blizzard_spirit", 472, 430, 12, 2, 80, (1, 2), 44, 4),
    ("west_woods_bats", "icicle_bat", 130, 400, 14, 3, 70, (2, 3), 44, 6),
    ("east_woods_bats", "icicle_bat", 540, 400, 14, 3, 70, (2, 3), 44, 6),
    ("west_woods_stags", "ice_stag", 120, 470, 14, 3, 70, (2, 3)),
    ("east_woods_stags", "ice_stag", 560, 470, 14, 3, 70, (2, 3)),
    ("west_mimics", "mimic", 200, 360, 8, 2, 80, (1, 2), 30),
    ("east_mimics", "mimic", 472, 360, 8, 2, 80, (1, 2), 30),
    # 崖2の北（監視所の森）
    ("north_w_spirits", "blizzard_spirit", 250, 270, 12, 2, 80, (1, 2), 44, 4),
    ("north_e_spirits", "blizzard_spirit", 420, 270, 12, 2, 80, (1, 2), 44, 4),
    ("station_mimics", "mimic", 336, 228, 10, 3, 80, (1, 2), 30),
    ("north_stags", "ice_stag", 290, 230, 12, 3, 70, (2, 3)),
    ("north_bats", "icicle_bat", 380, 236, 12, 3, 70, (2, 3), 44, 6),
    ("door_mimics", "mimic", 336, 200, 8, 2, 80, (1, 2), 30),
]


def floor_json(f):
    cx, cz = LAKE
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    exc, ezc = f.exit_circle
    return {
        "_comment": "塔の7階「氷の森林」（tools/mapgen/build_floor7.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 2,
        "name": "氷の森林",
        "chapter_title": "第2章　凍てついた記憶",
        "recommended_level": [28, 35],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor7/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, GROUND + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, GROUND + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:reflecta",
            "name": "鏡の湖",
            "spawn": {"pos": [cx + 0.5, GROUND + 1, cz - 8.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, GROUND + 1, cz + 0.5], "radius": 52.0, "y_min": GROUND - 2, "y_max": GROUND + 28},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 30,
            "respawn_seconds": 150,
            "max_players": 8,
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1800,
            "exit": {"center": [exc + 0.5, GROUND + 1, ezc + 0.5], "radius": 2.6},
            "next": {"center": [exc + NEXT_DX + 0.5, GROUND + 1, ezc + 0.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "合言葉が、そろった。凍った扉が、あなたたちを通す……",
        # 古代記録（records/f07_watch_post.json と同じ文）
        "tablet": {"pos": list(f.tablet), "title": "森林監視所の記録",
                   "text": "森林監視所より通達。侵食域では、隣にいる者が誰か分からなくなる。合言葉を決めよ。"
                           "合言葉は、互いにしか分からない言葉にすること。名前は、侵食に最初に奪われる。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 氷の森（青白い霧）。空はオーロラ、雪が降る
        "environment": {"fog": [30, 170], "fog_color": "#BFD3EA", "sky": "aurora", "weather": "snow"},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        "seal_requires": "lights+guardians+passphrase",
        "light_mazes": f.mazes,
        "passphrase": {"area": f.passphrase_area, "seconds": 20},
        "story_zones": f.story_zones,
    }


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
    barriers(f)
    fill_ground(f)
    barriers(f)
    gates(f)
    entrance(f)
    for gid in GARDENS:
        light_garden(f, gid)
    station(f)
    lairs(f)
    passphrase_door(f)
    lake(f)
    scatter_chests(f)
    waypoints(f)
    keep_spawns_clear(f)
    f.tree_count = trees(f)
    finalize(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor7") if out else os.path.join(RES, "structures", "map", "floor7")
    json_path = os.path.join(out, "floor7.json") if out else os.path.join(RES, "amemaru", "floors", "floor7.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "光の迷路:", len(f.mazes), "木:", f.tree_count)
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor7_figures
        floor7_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
