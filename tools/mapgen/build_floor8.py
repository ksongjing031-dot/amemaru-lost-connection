"""塔の8階「沈黙の湖」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor8.json）を書き出す。

Phase 0-A 9章の8階（確定）：音のない凍った湖。足音も環境音も消えている。氷の下に巨大な影と、沈んだ祠が見える。雑魚は無音の大魚
（氷の下から襲う。割れ目が予兆）・声喰い（近くにいると効果音が消える）・凍て鶴・氷霧の亡霊。ボスは声なき湖主ムーネ（「静寂」の間は
効果音・予兆表示・パーティHUDが消え、ボイスチャットの声かけだけが頼りになる）。ギミックは氷の割れ目（同じ場所に集まりすぎると割れる）と、
湖のまわりの鐘（正しい順番で鳴らすと音が戻り、祠への道が開く）。古代記録「湖底の祠の石板」（「沈黙は、争いを止めた。だが、歌も止めた」）。
8日目（この石板を持ち帰ると、セレナが「ロストで記憶が消える仕組み」を伝える）。ロストの侵食：音そのものが消えている。

使い方:
  python3 tools/mapgen/build_floor8.py                        # Modの structures/map/floor8/ と floors/floor8.json を書き直す
  python3 tools/mapgen/build_floor8.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

決まり（11月25日【補完】。docs/phase8_lake.md）：
  - 720×720（7階より広い）、高さ96。湖の氷の面は y24（立つのは 25）。氷の下は水（深い所で湖底 y10）。まわりは雪の山
  - 区画：南の岸（入口・入江の鐘3つ）→ 黒い裂け目（沈んだ石橋。入江の鐘で浮かぶ）→ 大湖（湖の鐘5つ・歌の碑・薄氷・氷の下の影・
    凍った釣り人たち）・西の葦原（名のある魔物「霜冠」）・東の声の洞（名のある魔物「千声喰い」）→ 湖底の祠（湖の鐘で氷のふたが開く。
    石板・歌い手の氷像・封印）→ 湖底の道（尾根の下）→ 湖心（ボス）
  - 封印は「鐘をすべて正しく鳴らした（みんな）＋名のある魔物2体（人ごと）」（bells+guardians）
  - 湖の鐘を鳴らすまで、階じゅうの音が消えている（environment.silent。鳴らすと、その日は音が戻る）
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

FLOOR = 8
SIZE = 720
HEIGHT = 96
PIECE = 48
ORIGIN = (32768 - SIZE // 2, 60, -SIZE // 2)
GROUND = 24             # 湖の氷と岸の地面のブロック（立つのは 25）
WATER_TOP = GROUND - 1  # 氷の下の水のいちばん上
CHEST_LOOT = "amemaru:chests/floor8_lake"

ENTRY = (360, 694)
GORGE = (604, 624)                  # 黒い裂け目（z0, z1）。水は y9〜16、両岸は y24
GORGE_WATER = 16
BRIDGE = (357, 363)                 # 沈んだ石橋（x0, x1）
LAKE_C = (360, 425)
LAKE_AB = (286, 168)                # 大湖のだ円の半径（東西・南北）
STELE = (360, 430)                  # 歌の碑（大湖のまん中の島）
SHRINE = (360, 290)                 # 湖底の祠の縦穴（氷のふたのまん中）
ARENA = (360, 128)
ARENA_R = 44
COVE = (300, 652)

# 湖の鐘（大湖のまわり。正しい順番は、歌の碑に出る。毎日かわる）・入江の鐘（南の岸）
MAIN_BELLS = [("moon", "月の鐘", "西の岬", (92, 470)), ("crane", "鶴の鐘", "北西の小島", (215, 287)),
              ("fish", "魚の鐘", "北東の小島", (505, 287)), ("flower", "花の鐘", "東の岸", (628, 470)),
              ("star", "星の鐘", "南の岸（石橋のそば）", (330, 598))]
COVE_BELLS = [("dawn", "朝の鐘", "入江の西", (282, 640)), ("noon", "昼の鐘", "入江の南", (300, 671)),
              ("night", "夜の鐘", "入江の東", (318, 640))]
COVE_STELE = (332, 664)
ARENA_BELLS = [(389, 99), (331, 99), (331, 157), (389, 157)]
SHRINE_DOOR = (363, GROUND, 290, 364, GROUND, 292)    # 祠の縦穴の、石段の上の口（湖の鐘で開く）

# 島（x, z, 半径）。湖の鐘の小島・歌の碑の島・景色の小島
ISLANDS = [(215, 287, 8), (505, 287, 8), (360, 430, 13), (252, 486, 5), (470, 372, 6), (156, 352, 5), (566, 548, 5),
           (430, 560, 4), (286, 330, 4), (604, 330, 5), (88, 470, 9), (632, 470, 9)]
# 薄氷（だ円：x, z, 半径x, 半径z）。2人以上が近くに乗ると割れる
THIN = [(328, 522, 22, 13), (402, 548, 17, 11), (318, 452, 9, 18), (402, 410, 10, 16), (300, 340, 24, 14), (430, 338, 22, 13),
        (360, 372, 15, 9), (182, 420, 18, 28), (140, 520, 18, 12), (540, 420, 18, 28), (584, 516, 18, 12), (250, 560, 20, 10),
        (470, 560, 20, 10), (205, 335, 12, 16), (515, 335, 12, 16)]
ARENA_THIN = [(345, 118, 8, 6), (380, 142, 7, 9), (350, 151, 6, 5), (376, 104, 8, 6), (334, 134, 5, 7)]
# 雪をかぶった厚い氷の道（折れ線, 半幅）。薄氷の上を通るときは、道が勝つ（道は割れない）
PATHS = [
    ([(360, 604), (360, 446)], 2.5),
    ([(360, 416), (360, 305)], 2.5),
    ([(346, 432), (240, 446), (150, 462), (100, 470)], 2.0),
    ([(350, 420), (290, 362), (222, 294)], 2.0),
    ([(370, 420), (430, 362), (498, 294)], 2.0),
    ([(374, 432), (480, 446), (570, 462), (620, 470)], 2.0),
    ([(360, 596), (336, 598)], 1.6),
    ([(100, 470), (80, 440), (62, 420)], 1.6),
    ([(620, 470), (650, 440), (672, 428)], 1.6),
]
# 氷の下の大きな影（魚の形。x, z, 向き（度。0 は東）, 長さ）
SHADOWS = [(250, 520, 25, 42), (474, 482, 200, 38), (300, 396, 330, 34), (440, 312, 150, 30), (560, 390, 250, 28)]
# 凍った釣り人たち（湖の上の氷像。腰かけて、氷の穴に糸を垂らしている）
FISHERS = (432, 506)
WEST_LAIR = (58, 420)               # 西の葦原（霜冠）
EAST_LAIR = (688, 425)              # 東の声の洞（千声喰い）


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


R = Rand(20261125 + 8)

ROCK = [("stone", 5), ("andesite", 2), ("tuff", 1), ("calcite", 1)]
CLIFF = [("packed_ice", 4), ("stone", 3), ("snow_block", 2), ("calcite", 1), ("andesite", 1)]
BED = [("gravel", 4), ("clay", 3), ("mud", 2), ("dirt", 1)]
LAKE_ICE = block("amemaru:lake_ice")


def thin_ice(crack=0):
    return block("amemaru:thin_ice", crack=crack)


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


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Floor:
    def __init__(self):
        self.v = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)
        self.states = [None]
        self.index = {}
        self.nbt = {}
        self.height = None          # 地面（陸）・氷の面（湖）のブロックの高さ
        self.kind = None            # 0 陸・1 大湖・2 湖心・3 入江・4 裂け目
        self.bed = None             # 湖底の高さ
        self.reserved = np.zeros((SIZE, SIZE), dtype=bool)
        self.path = np.zeros((SIZE, SIZE), dtype=bool)
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.seal_cells = []
        self.story_zones = []
        self.bell_groups = []
        self.relics = []
        self.hearths = []
        self.memories = []
        self.statues = []
        self.travel_stone = None
        self.tablet = None
        self.exit_circle = None
        self.tree_count = 0
        self.shaft = None
        self.lights = 0

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


def lantern(hanging=False, soul=False):
    return block("soul_lantern" if soul else "lantern", hanging=hanging, waterlogged=False)


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
    """四角い所を GROUND の高さの陸にする（下は土と石でうめ、上は clear ブロックあける）"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            top = f.top(x, z)
            for y in range(min(top, GROUND) - 3, GROUND):
                f.put(x, y, z, block("dirt" if y >= GROUND - 2 else "stone"))
            f.put(x, GROUND, z, block(surface))
            for y in range(GROUND + 1, max(top, GROUND) + clear + 1):
                f.put(x, y, z, None)
            f.height[x, z] = GROUND
            f.kind[x, z] = 0
            f.reserved[x, z] = True


def lamp(f, x, z, tall=3):
    """湖の灯（白い石の柱と、上に淡い灯）。音のない湖を、灯りだけが見張っている"""
    y = f.top(x, z) + 1
    for k in range(tall):
        f.put(x, y + k, z, block("calcite") if k < tall - 1 else block("polished_diorite_wall", up=True, north="none", south="none",
                                                                          east="none", west="none", waterlogged=False))
    f.put(x, y + tall, z, block("sea_lantern"))
    f.put(x, y + tall + 1, z, snow_layer(2))
    f.reserved[x, z] = True


# ================================================================ 地形

def terrain(f):
    """陸の高さ・湖と裂け目の場所・湖底の高さを決める"""
    n = SIZE
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    kind = np.zeros((n, n), dtype=np.uint8)
    # 大湖（だ円。ふちは少し揺れる）
    cx, cz = LAKE_C
    a, b = LAKE_AB
    t = np.sqrt(((X - cx) / a) ** 2 + ((Z - cz) / b) ** 2) + 0.05 * smooth_noise(n, 41, 81) + 0.025 * smooth_noise(n, 15, 82)
    # 尾根の南の端（湖の北の岸）は、ゆるく波うつ。祠と湖底の道のあたり（x330〜390）は z252 にそろえる
    zr = 252.0 + 7.0 * smooth_noise(n, 37, 92)[:, 0]
    xi = np.arange(n)
    zr = np.where((xi >= 322) & (xi <= 398), 252.0, zr)
    zr2 = zr[:, None] + 0.0 * Z
    f.ridge_z = zr
    lake = (t < 1.0) & (Z > zr2 + 3) & (Z < 592)
    f.lake_t = t
    # 島（陸にする）
    island = np.zeros((n, n), dtype=bool)
    for (ix, iz, r) in ISLANDS:
        d = np.hypot(X - ix, Z - iz) + 1.5 * smooth_noise(n, 7, 83 + ix % 7)
        island |= d < r
    lake &= ~island
    kind[lake] = 1
    # 湖心（ボスの湖）
    ra = np.hypot(X - ARENA[0], Z - ARENA[1])
    f.arena_r = ra
    kind[ra < ARENA_R] = 2
    # 入江（南の岸の小さな凍った入り江）
    rc = np.hypot(X - COVE[0], Z - COVE[1]) + 2.0 * smooth_noise(n, 9, 84)
    kind[rc < 15] = 3
    # 黒い裂け目（東西に横切る。両端は山の中）
    kind[(Z >= GORGE[0]) & (Z <= GORGE[1] + 1) & (X >= 2) & (X <= n - 2)] = 4

    # 陸の高さ：岸は低く、内へ行くほど少し高い。まわりは山。尾根（z 176〜258）と北（湖心のまわり）は山
    h = GROUND + 1.0 + 1.3 * smooth_noise(n, 37, 85) + 0.7 * smooth_noise(n, 13, 86)
    edge = np.minimum(np.minimum(X, n - X), np.minimum(Z, n - Z))
    mtn = 38.0 + 10.0 * smooth_noise(n, 29, 87) + 4.0 * smooth_noise(n, 11, 88)
    h += mtn * (1.0 - smoothstep(0.0, 44.0, edge)) ** 1.4
    north = Z < zr2
    ridge_h = GROUND + 18 + 9.0 * smooth_noise(n, 23, 89) + 3.0 * smooth_noise(n, 9, 90)
    hn = GROUND + (ridge_h - GROUND) * smoothstep(ARENA_R + 3.0, ARENA_R + 6.0, ra)
    h = np.where(north, np.maximum(np.where(ra < ARENA_R + 3, GROUND, h), hn), h)
    # 湖のすぐそばは低く（氷の面と同じか、1つ上）
    near = smoothstep(1.0, 1.10, t)
    h = np.where((Z > zr2) & (Z < 600) & ~north, GROUND + (h - GROUND) * near, h)
    # 島は少し盛り上がる
    for (ix, iz, r) in ISLANDS:
        d = np.hypot(X - ix, Z - iz)
        bump = (1.0 - smoothstep(r * 0.35, r * 1.05, d)) * (2.0 if r >= 8 else 1.2)
        h = np.where(island & (d < r + 2), GROUND + 1 + bump, h)
    # 南の岸のまわりは山。入口のまわりは平ら
    h = np.where(kind == 4, GROUND, h)
    f.height = np.clip(np.round(h), 4, HEIGHT - 8).astype(int)
    f.height[kind > 0] = GROUND
    f.height[(Z > 592) & (Z < 604) & (X > 30) & (X < n - 30)] = GROUND     # 裂け目の北の細い岸
    f.kind = kind
    # 湖底：まん中ほど深い
    bed = np.full((n, n), GROUND - 4, dtype=int)
    deep = 14 + np.round(6.0 * smoothstep(0.55, 1.0, t) + 1.2 * smooth_noise(n, 17, 91)).astype(int)
    bed = np.where(kind == 1, np.clip(deep, 13, GROUND - 3), bed)
    d_sh = np.hypot(X - SHRINE[0], Z - (SHRINE[1] - 8))
    bed = np.where((kind == 1) & (d_sh < 34), np.minimum(bed, np.round(6 + 7.0 * smoothstep(20.0, 34.0, d_sh)).astype(int)), bed)
    bed = np.where(kind == 2, np.clip(10 + np.round(9.0 * smoothstep(30.0, ARENA_R, ra)).astype(int), 9, GROUND - 3), bed)
    bed = np.where(kind == 3, GROUND - 4, bed)
    bed = np.where(kind == 4, 8, bed)
    f.bed = bed


def fill_ground(f):
    """陸は表の数段だけ、湖は湖底の下の数段だけ埋める（構造物を小さくするため）。ただし、となりに水がある所は、その水の底まで
    埋める（水が、下の空いた所へ流れないように）"""
    n = SIZE
    water = f.kind > 0
    low = np.where(water, f.bed, f.height)
    hmin = f.height.copy()
    lmin = low.copy()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        hmin = np.minimum(hmin, np.roll(np.roll(f.height, dx, axis=0), dz, axis=1))
        lmin = np.minimum(lmin, np.roll(np.roll(low, dx, axis=0), dz, axis=1))
    for x in range(n):
        for z in range(n):
            k = int(f.kind[x, z])
            top = int(f.height[x, z])
            bottom = max(1, min(int(lmin[x, z]) - 2, min(top, int(hmin[x, z])) - 3))
            if k == 4:
                # 裂け目：岩の底（y8）と黒い水（y9〜16）。上は空
                for y in range(bottom, 9):
                    f.put(x, y, z, block(R.pick(ROCK, x, y, z, 2)))
                for y in range(9, GORGE_WATER + 1):
                    f.put(x, y, z, block("water", level=0))
                f.reserved[x, z] = True
                continue
            if k > 0:
                bed = int(f.bed[x, z])
                for y in range(bottom, bed):
                    f.put(x, y, z, block(R.pick(ROCK, x, y, z, 2)))
                f.put(x, bed, z, block(R.pick(BED, x // 2, z // 2, 3)))
                for y in range(bed + 1, GROUND):
                    f.put(x, y, z, block("water", level=0))
                f.put(x, GROUND, z, LAKE_ICE)
                f.reserved[x, z] = True
                continue
            cliff = top - int(hmin[x, z]) >= 3
            for y in range(bottom, top):
                if cliff and y >= int(hmin[x, z]) - 1:
                    st = R.pick(CLIFF, x, y, z, 3)
                elif y >= top - 2:
                    st = "dirt"
                else:
                    st = R.pick(ROCK, x, y, z, 2)
                f.put(x, y, z, block(st))
            if cliff and R.at(x, z, 10) < 0.5:
                f.put(x, top, z, block("packed_ice"))
            else:
                f.put(x, top, z, block("snow_block"))
                if R.at(x, z, 12) < 0.55 and top < HEIGHT - 2:
                    f.put(x, top + 1, z, snow_layer(1 + int(R.at(x, z, 13) * 2.2)))


def barriers(f):
    """抜け道をふさぐ：階のいちばん外の2列は、山の上からさらに14ブロック高い壁。尾根の南の端（湖の北の岸）には、山の上まで
    続く高い氷の壁（湖の側の山を登って、尾根や湖心へ回りこめないように）"""
    n = SIZE
    for x in range(n):
        for z in range(n):
            if min(x, z, n - 1 - x, n - 1 - z) >= 2:
                continue
            top = f.top(x, z)
            for y in range(1, min(HEIGHT - 4, max(top, GROUND) + 14)):
                f.put(x, y, z, block(R.pick(CLIFF, x, y, z, 7)))
            f.reserved[x, z] = True
    for x in range(n):
        zr = int(round(f.ridge_z[x]))
        for z in range(zr - 3, zr + 1):
            top = f.top(x, z)
            for y in range(max(1, top - 2), min(HEIGHT - 4, max(top + 12, GROUND + 22))):
                f.put(x, y, z, block(R.pick(CLIFF, x, y, z, 8)))
            f.reserved[x, z] = True


def gorge_walls(f):
    """裂け目の両側は、まっすぐな岩の壁（陸の柱がそのまま壁になる）。南の壁には、落ちた人が上がれる石段（石橋の西。x338〜340）。
    水の中には、沈んだ石橋の柱と、崩れた橋の板が見えている（橋そのものは入江の鐘で浮かぶ）"""
    z0, z1 = GORGE
    for i in range(GROUND - GORGE_WATER):
        z = z1 - (GROUND - GORGE_WATER - 1) + i
        top = GORGE_WATER + 1 + i
        for x in range(338, 341):
            for y in range(8, top + 1):
                f.put(x, y, z, block("stone_bricks") if y < top else block("polished_andesite"))
            for y in range(top + 1, top + 4):
                f.put(x, y, z, None)
    for z in (z0 + 3, z0 + 8, z0 + 13, z0 + 18):
        for x in (BRIDGE[0], BRIDGE[0] + 1, BRIDGE[1] - 1, BRIDGE[1]):
            for y in range(8, GORGE_WATER + 1):
                f.put(x, y, z, block("mossy_stone_bricks" if (x + y) % 3 else "cracked_stone_bricks"))
    for z in range(z0 + 1, z1 + 1):
        for x in range(BRIDGE[0], BRIDGE[1] + 1):
            if R.at(x, z, 33) < 0.35 and f.name_at(x, 9, z) == "water":
                f.put(x, 9, z, block("mossy_stone_bricks"))


def bridge_raise():
    """入江の鐘で浮かぶ石橋（鳴らすと置く。進行日が変わると消える）。箱は裂け目の上（z0〜z1）だけにする：岸の地面と重なると、
    沈むときに岸の雪と石まで空気にしてしまう（11月25日）"""
    z0, z1 = GORGE
    x0, x1 = BRIDGE
    out = [{"box": [x0, GROUND, z0, x1, GROUND, z1], "block": "minecraft:stone_bricks"}]
    for z in (z0 + 3, z0 + 8, z0 + 13, z0 + 18):
        out.append({"box": [x0, GORGE_WATER + 1, z, x0 + 1, GROUND - 1, z], "block": "minecraft:stone_bricks"})
        out.append({"box": [x1 - 1, GORGE_WATER + 1, z, x1, GROUND - 1, z], "block": "minecraft:stone_bricks"})
    out.append({"box": [x0, GROUND + 1, z0, x0, GROUND + 1, z1], "block": "minecraft:stone_brick_wall"})
    out.append({"box": [x1, GROUND + 1, z0, x1, GROUND + 1, z1], "block": "minecraft:stone_brick_wall"})
    return out


# ================================================================ 湖の面（厚い氷の道・薄氷・雪・影）

def lake_surface(f):
    n = SIZE
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    water = (f.kind == 1) | (f.kind == 2) | (f.kind == 3)
    thin = np.zeros((n, n), dtype=bool)
    for (tx, tz, rx, rz) in THIN:
        d = ((X - tx) / rx) ** 2 + ((Z - tz) / rz) ** 2 + 0.15 * smooth_noise(n, 6, tx)
        thin |= d < 1.0
    arena_thin = np.zeros((n, n), dtype=bool)
    for (tx, tz, rx, rz) in ARENA_THIN:
        d = ((X - tx) / rx) ** 2 + ((Z - tz) / rz) ** 2 + 0.2 * smooth_noise(n, 5, tz)
        arena_thin |= d < 1.0
    thin = (thin & (f.kind == 1)) | (arena_thin & (f.kind == 2) & (f.arena_r < ARENA_R - 5))
    path = np.zeros((n, n), dtype=bool)
    for pts, half in PATHS:
        path |= polyline_distance(pts) <= half
    path &= water
    # 湖心のふち（r 39〜44）は、雪をかぶった厚い氷（鳴り石のある所）
    rim = (f.kind == 2) & (f.arena_r >= ARENA_R - 5)
    f.path = path | rim
    shadow_mask = np.zeros((n, n), dtype=bool)
    for (sx, sz, ang, length) in SHADOWS:
        shadow_mask |= fish_shape(X, Z, sx, sz, ang, length)
    f.shadow = shadow_mask & (f.kind == 1)
    thin &= ~f.path & ~f.shadow
    f.thin = thin
    for x in range(n):
        for z in range(n):
            if not water[x, z]:
                continue
            if f.path[x, z]:
                f.put(x, GROUND, z, LAKE_ICE)
                f.put(x, GROUND + 1, z, snow_layer(2 if R.at(x, z, 40) < 0.8 else 1))
            elif thin[x, z]:
                f.put(x, GROUND, z, thin_ice(0))
            else:
                f.put(x, GROUND, z, LAKE_ICE)
                if not f.shadow[x, z] and R.at(x // 2, z // 2, 41) < 0.12 and f.kind[x, z] != 2:
                    f.put(x, GROUND + 1, z, snow_layer(1))


def fish_shape(X, Z, cx, cz, angle, length):
    """上から見た魚の形（だ円の体・三角の尾・長いひげ）"""
    a = math.radians(angle)
    ux, uz = math.cos(a), math.sin(a)
    rx = (X - cx) * ux + (Z - cz) * uz          # 体の向き（+ が頭）
    ry = -(X - cx) * uz + (Z - cz) * ux
    half = length / 2.0
    width = length * 0.16
    body = (rx / half) ** 2 + (ry / width) ** 2 < 1.0
    tail = (rx < -half * 0.85) & (rx > -half * 1.25) & (np.abs(ry) < (-half * 0.85 - rx) * 1.1 + 0.8)
    whisker = np.zeros_like(body)
    for side in (-1, 1):
        for k in range(int(length * 0.5)):
            t = k / (length * 0.5)
            px = half * 0.9 + t * length * 0.45
            py = side * (width * 0.4 + t * width * 1.4 + math.sin(t * 5.0) * 1.2)
            wx = cx + px * ux - py * uz
            wz = cz + px * uz + py * ux
            whisker |= (np.abs(X - wx) < 0.7) & (np.abs(Z - wz) < 0.7)
    return body | tail | whisker


def shadows(f):
    """氷の下の大きな影（y19〜20。黒い石。透きとおった氷ごしに、湖の主のような影が見える）"""
    xs, zs = np.nonzero(f.shadow)
    for x, z in zip(xs.tolist(), zs.tolist()):
        if f.bed[x, z] < 18:
            for y in (19, 20):
                f.put(x, y, z, block("black_concrete"))


# ================================================================ 島・鐘・歌の碑

def islands(f):
    for (ix, iz, r) in ISLANDS:
        for dx in range(-r - 2, r + 3):
            for dz in range(-r - 2, r + 3):
                x, z = ix + dx, iz + dz
                if f.kind[x, z] == 0 and math.hypot(dx, dz) < r + 2:
                    f.reserved[x, z] = True
        # 岩をいくつか
        for k in range(max(1, r // 3)):
            ang = R.at(ix, iz, k, 50) * math.tau
            rr = r * (0.3 + 0.4 * R.at(ix, iz, k, 51))
            x = int(round(ix + math.cos(ang) * rr))
            z = int(round(iz + math.sin(ang) * rr))
            if f.kind[x, z] == 0 and (ix, iz) != STELE:
                y = f.top(x, z) + 1
                f.put(x, y, z, block("andesite"))
                if R.at(x, z, 52) < 0.6:
                    f.put(x, y + 1, z, block("stone"))
                f.put(x, y + 2, z, snow_layer(2))


def bell(f, x, z, gid, index, facing, name, where):
    """湖の鐘（石の台と、鐘のかかった枠。右クリックで鳴らす。音は出ないが、光の輪が広がる）"""
    y = max(f.top(x, z), GROUND) + 1
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            base = f.top(x + dx, z + dz) if f.kind[x + dx, z + dz] == 0 else GROUND + 1
            for yy in range(min(base, y - 1), y - 1):
                f.put(x + dx, yy, z + dz, block("stone"))
            f.put(x + dx, y - 1, z + dz, block("polished_andesite") if max(abs(dx), abs(dz)) < 2 else block("andesite"))
            for yy in range(y, y + 4):
                f.put(x + dx, yy, z + dz, None)
            f.height[x + dx, z + dz] = y - 1
            f.reserved[x + dx, z + dz] = True
    f.put(x, y, z, block("amemaru:lake_bell", facing=facing, lit=False), thing(index, gid))
    sx, sz = {"north": (0, 2), "south": (0, -2), "east": (-2, 0), "west": (2, 0)}[facing]
    rot = {"north": 8, "south": 0, "east": 4, "west": 12}[facing]
    sign(f, x + sx, y, z + sz, rot, [name, where, "", "右クリックで鳴らす"])
    return [x, y, z]


def bells(f):
    stele_pos = stele(f, STELE[0], STELE[1], "main", "歌の碑", ["湖の歌の碑", "", "触れると", "今日の歌が浮かぶ"])
    main = []
    for k, (mark, name, where, (x, z)) in enumerate(MAIN_BELLS):
        facing = "south" if z < 400 else "north" if z > 560 else ("east" if x < 360 else "west")
        main.append({"pos": bell(f, x, z, "main", k, facing, name, where), "mark": mark, "name": name, "where": where})
    shrine_cap = list(SHRINE_DOOR)
    f.bell_groups.append({"id": "main", "name": "湖の鐘", "stele": stele_pos, "seconds": 240, "sound": True,
                          "bells": main,
                          "opens": [{"box": shrine_cap, "block": "minecraft:blue_ice"}],
                          "hold": [SHRINE[0] - 14, 8, 260, SHRINE[0] + 14, GROUND - 1, SHRINE[1] + 6]})
    cove_stele = stele(f, COVE_STELE[0], COVE_STELE[1], "cove", "入江の歌の碑", ["入江の歌の碑", "", "触れると", "今日の歌が浮かぶ"])
    cove = []
    for k, (mark, name, where, (x, z)) in enumerate(COVE_BELLS):
        facing = "south" if z < COVE[1] else "north"
        cove.append({"pos": bell(f, x, z, "cove", k, facing, name, where), "mark": mark, "name": name, "where": where})
    f.bell_groups.insert(0, {"id": "cove", "name": "入江の鐘", "stele": cove_stele, "seconds": 90, "sound": False,
                             "bells": cove, "raises": bridge_raise()})


def stele(f, x, z, gid, name, lines):
    """歌の碑（3つ並んだ白い石の板。まん中の板に触れると、今日の歌（鐘の順番）が浮かぶ）"""
    y = max(f.top(x, z), GROUND) + 1
    for dx in range(-3, 4):
        for dz in range(-2, 3):
            if f.kind[x + dx, z + dz] == 0:
                for yy in range(f.top(x + dx, z + dz), y - 1):
                    f.put(x + dx, yy, z + dz, block("stone"))
            f.put(x + dx, y - 1, z + dz, block("polished_diorite") if abs(dx) < 3 and abs(dz) < 2 else block("diorite"))
            for yy in range(y, y + 7):
                f.put(x + dx, yy, z + dz, None)
            f.height[x + dx, z + dz] = y - 1
            f.reserved[x + dx, z + dz] = True
    for dx in (-2, 2):
        for yy in range(y, y + 3):
            f.put(x + dx, yy, z - 1, block("smooth_quartz" if yy < y + 2 else "quartz_slab", **({} if yy < y + 2 else {"type": "bottom", "waterlogged": False})))
    for yy in range(y + 1, y + 5):
        f.put(x, yy, z - 1, block("smooth_quartz"))
        f.put(x - 1, yy, z - 1, block("quartz_bricks") if yy < y + 4 else block("quartz_slab", type="bottom", waterlogged=False))
        f.put(x + 1, yy, z - 1, block("quartz_bricks") if yy < y + 4 else block("quartz_slab", type="bottom", waterlogged=False))
    f.put(x, y + 5, z - 1, block("sea_lantern"))
    f.put(x, y, z - 1, block("amemaru:song_stele", facing="south"), thing(0, gid))
    sign(f, x + 2, y, z + 1, 0, lines)
    return [x, y, z - 1]


# ================================================================ 湖底の祠・湖底の道・湖心

def shrine(f):
    """湖底の祠：氷のふた（湖の鐘で開く）の下の縦穴（まわる石段）、祠の広間（石板・歌い手の氷像・灯籠）、北の封印"""
    cx, cz = SHRINE
    # 縦穴（外 11×11、内 9×9。まん中に 5×5 の柱。石段は柱のまわりを下る）
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            outer = max(abs(x - cx), abs(z - cz)) == 5
            for y in range(7, GROUND):
                if outer:
                    f.put(x, y, z, block("prismarine_bricks" if y < 18 else "packed_ice"))
                else:
                    f.put(x, y, z, None)
            f.put(x, 7, z, block("dark_prismarine"))
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            for y in range(8, GROUND):
                f.put(x, y, z, block("prismarine_bricks"))
            f.put(cx, GROUND - 1, cz, block("sea_lantern"))
    # 石段：東（上の口の下）から南・西を回って下り、北が底（広間への口）。北東は壁（底へ落ちないように）
    steps = GROUND - 1 - 8
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            if max(abs(x - cx), abs(z - cz)) < 3:
                continue
            frac = (math.atan2(z - cz, x - cx) % math.tau) / math.tau
            if frac >= 0.93:
                for yy in range(8, GROUND):
                    f.put(x, yy, z, block("prismarine_bricks"))
                continue
            x0d, _, z0d, x1d, _, z1d = SHRINE_DOOR
            if x0d <= x <= x1d and z0d <= z <= z1d:
                y = GROUND - 1 - (z - z0d)
            elif frac < 0.08:
                y = GROUND - 3
            elif frac < 0.66:
                y = int(round(GROUND - 3 - (frac - 0.08) / 0.58 * (steps - 2)))
            else:
                y = 8
            for yy in range(8, y + 1):
                f.put(x, yy, z, block("prismarine_bricks") if yy < y else block("dark_prismarine"))
            for yy in range(y + 1, GROUND):
                f.put(x, yy, z, None)
    # 縦穴の屋根（氷の面と同じ y24。石段の上の口だけが、湖の鐘で開くふた）
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            f.put(x, GROUND, z, block("packed_ice"))
            f.put(x, GROUND + 1, z, None)
    x0d, _, z0d, x1d, _, z1d = SHRINE_DOOR
    for x in range(x0d, x1d + 1):
        for z in range(z0d, z1d + 1):
            f.put(x, GROUND, z, block("blue_ice"))
    for (lx, lz) in ((cx - 6, cz - 6), (cx + 6, cz - 6), (cx - 6, cz + 6), (cx + 6, cz + 6)):
        lamp(f, lx, lz, 2)
    f.shaft = (cx, cz)
    # 祠の広間（外 x347〜373・z262〜284・y8〜16。中は y9〜15）
    x0, x1, z0, z1 = cx - 13, cx + 13, 262, cz - 6
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(7, 17):
                if y in (7, 8) or y == 16 or edge:
                    st = "dark_prismarine" if y == 8 and not edge and (x + z) % 2 == 0 else "prismarine_bricks"
                    if edge and y in (11, 12) and (x + z) % 4 == 0:
                        st = "sea_lantern"
                    f.put(x, y, z, block(st))
                else:
                    f.put(x, y, z, None)
    # 縦穴と広間をつなぐ口（縦穴の北の壁）
    for x in range(cx - 2, cx + 3):
        for z in range(z1, cz - 4):
            for y in range(9, 13):
                f.put(x, y, z, None)
            f.put(x, 8, z, block("dark_prismarine"))
    # 柱と灯
    for x in (x0 + 3, x0 + 8, x1 - 8, x1 - 3):
        for z in (z0 + 4, z1 - 4):
            for y in range(9, 16):
                f.put(x, y, z, block("prismarine_bricks"))
            f.put(x, 15, z, block("sea_lantern"))
    # 北の祭壇（石板）
    for x in range(cx - 4, cx + 5):
        for z in range(z0 + 1, z0 + 4):
            f.put(x, 9, z, block("polished_deepslate"))
    f.put(cx, 10, z0 + 2, block("amemaru:lake_tablet", facing="south"), thing(0, "lake_tablet"))
    f.tablet = (cx, 10, z0 + 2)
    f.relics.append({"id": "lake_tablet", "pos": [cx, 10, z0 + 2], "items": ["amemaru:lake_shrine_tablet"],
                     "flag": "amemaru:story/lake_tablet_found", "event": "amemaru:floor8/tablet", "quest": "amemaru:lake_tablet"})
    for x in (cx - 3, cx + 3):
        f.put(x, 10, z0 + 2, block("soul_lantern", hanging=False, waterlogged=False))
    # 歌い手の氷像（記憶の氷像：祠の灯籠の熱の結晶で溶かすと、沈黙を選んだ日の場面）と、祠の灯籠（熱の炉）
    sx, sz = x0 + 5, z0 + 12
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            f.put(sx + dx, 8, sz + dz, block("blue_ice"))
    statue(f, sx, 9, sz, "singer", "sing", 270.0, memory="singer")
    f.memories.append(("singer", "最後の歌い手", (sx, 9, sz)))
    hx, hz = x1 - 5, z0 + 12
    f.put(hx, 9, hz, block("amemaru:village_hearth", facing="west"), thing(0, "shrine_brazier"))
    f.hearths.append(("shrine_brazier", "祠の灯籠", (hx, 9, hz)))
    sign(f, hx, 9, hz + 2, 8, ["祠の灯籠", "", "右クリックで", "熱の結晶をもらう"])
    # 北の封印（湖底の道へ）
    for x in range(cx - 1, cx + 2):
        for y in range(9, 12):
            f.put(x, y, z0, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, z0))
    sign(f, cx + 3, 9, z0 + 4, 8, ["湖底の道", "", "この先は", "湖の心"])
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.reserved[x, z] = True
    f.story_zones.append({"id": "shrine", "box": [x0 + 1, 9, z0 + 1, x1 - 1, 15, z1 - 1]})


def statue(f, x, y, z, skin, pose, yaw, scale=1.0, memory=""):
    """凍った人の氷像（6階と同じブロックの実体。顔は白く抜けている）"""
    tag = Compound({"id": String("amemaru:village_statue"), "skin": String(skin), "pose": String(pose), "yaw": Float(yaw),
                    "scale": Float(scale), "memory": String(memory)})
    f.put(x, y, z, block("amemaru:village_statue"), tag)
    f.statues.append((x, y, z, skin, pose, yaw, scale, memory))


def tunnel(f):
    """湖底の道：祠の北の封印から、湖の下（ガラスの壁）と尾根の下（石の道）を通り、湖心の南の岸へ上がる石段"""
    x0, x1 = 358, 362
    z_end = 261
    top = 176
    # 石段（z177 で y23、1つ南へ行くごとに1つ下る。z192 で y8）
    for k in range(17):
        z = top + k
        y = GROUND - k
        for x in range(x0, x1 + 1):
            wall = x in (x0, x1)
            for yy in range(max(7, y - 1), y + 6):
                if wall or yy == y + 5:
                    f.put(x, yy, z, block("stone_bricks"))
                elif yy <= y:
                    f.put(x, yy, z, block("stone_bricks") if yy < y else block("polished_andesite"))
                else:
                    f.put(x, yy, z, None)
            f.reserved[x, z] = True
    # まっすぐな道（y8 の床。中は y9〜11）
    for z in range(top + 16, z_end + 1):
        under_lake = f.kind[360, z] == 1 or z > 255
        for x in range(x0, x1 + 1):
            wall = x in (x0, x1)
            for yy in range(7, 13):
                if yy in (7, 8):
                    f.put(x, yy, z, block("polished_andesite" if yy == 8 else "stone_bricks"))
                elif yy == 12 or wall:
                    if under_lake:
                        f.put(x, yy, z, block("light_blue_stained_glass"))
                    else:
                        f.put(x, yy, z, block("stone_bricks" if (z + yy) % 7 else "chiseled_stone_bricks"))
                else:
                    f.put(x, yy, z, None)
            f.reserved[x, z] = True
        if z % 10 == 0 and not under_lake:
            f.put(x0 + 1, 11, z, lantern(hanging=True, soul=True))
    # 南の岸の口（石段の上のくぼみ）
    for x in range(354, 367):
        for z in range(168, 177):
            f.put(x, GROUND, z, block("polished_andesite") if 359 <= x <= 361 else block("snow_block"))
            for y in range(GROUND + 1, GROUND + 6):
                f.put(x, y, z, None)
            f.height[x, z] = GROUND
            f.kind[x, z] = 0
            f.reserved[x, z] = True
    for x in (x0, x1):
        f.put(x, GROUND + 1, top, block("stone_brick_wall", up=True, north="none", south="none", east="none", west="none",
                                         waterlogged=False))
        f.put(x, GROUND + 2, top, lantern())
    # 石段の入口（z176 は岸と同じ y24 の床）
    for x in range(x0 + 1, x1):
        f.put(x, GROUND, top, block("polished_andesite"))


def arena(f):
    """湖心（ボスの湖）：まわりは高い氷の崖。ふちに4つの鳴り石（鐘）。北の岸に出口の円と次の階への円"""
    cx, cz = ARENA
    for x in range(cx - ARENA_R - 4, cx + ARENA_R + 5):
        for z in range(cz - ARENA_R - 4, cz + ARENA_R + 5):
            d = math.hypot(x - cx, z - cz)
            if ARENA_R <= d < ARENA_R + 3:
                f.put(x, GROUND, z, block("snow_block"))
                for y in range(GROUND + 1, GROUND + 8):
                    f.put(x, y, z, None)
                f.height[x, z] = GROUND
            if d < ARENA_R + 3:
                f.reserved[x, z] = True
    # 湖のまん中の印（床と同じ高さの、氷の輪）
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if 2.5 <= d <= 3.5:
                f.put(x, GROUND, z, block("blue_ice"))
                f.put(x, GROUND + 1, z, None)
    # 鳴り石（4つ。ボスの「静寂の帳」を、2人で同時に鳴らすと破れる）
    for k, (x, z) in enumerate(ARENA_BELLS):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                f.put(x + dx, GROUND, z + dz, block("polished_andesite"))
                f.put(x + dx, GROUND + 1, z + dz, None)
        facing = "south" if z < cz else "north"
        f.put(x, GROUND + 1, z, block("amemaru:lake_bell", facing=facing, lit=False), thing(k, "arena"))
    # 出口の円と次の階への円（北の岸のくぼみ）
    ex, ez = cx - 4, cz - ARENA_R - 8
    for x in range(ex - 8, ex + NEXT_DX + 9):
        for z in range(ez - 8, cz - ARENA_R + 3):
            if f.kind[x, z] == 2:
                continue
            f.height[x, z] = GROUND
            f.put(x, GROUND, z, block("snow_block"))
            for y in range(GROUND - 3, GROUND):
                f.put(x, y, z, block("stone"))
            for y in range(GROUND + 1, GROUND + 8):
                f.put(x, y, z, None)
            f.reserved[x, z] = True
    return_circle(f, ex, GROUND, ez)
    next_circle(f.put, ex + NEXT_DX, GROUND, ez)
    f.exit_circle = (ex, ez)
    # 湖のまわりの見えない灯
    for k in range(28):
        a = k / 28 * math.tau
        x = int(round(cx + math.cos(a) * (ARENA_R - 6)))
        z = int(round(cz + math.sin(a) * (ARENA_R - 6)))
        f.put(x, GROUND + 5, z, block("light", level=12, waterlogged=False))
    for k in range(10):
        a = k / 10 * math.tau
        x = int(round(cx + math.cos(a) * 16))
        z = int(round(cz + math.sin(a) * 16))
        f.put(x, GROUND + 6, z, block("light", level=10, waterlogged=False))


# ================================================================ 南の岸・西の葦原・東の声の洞・凍った釣り人

def entrance(f):
    cx, cz = ENTRY
    flatten(f, cx - 14, cz - 16, cx + 14, cz + 6)
    return_circle(f, cx, GROUND, cz)
    tx, tz = cx - 8, cz - 6
    f.put(tx, GROUND + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, GROUND + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, GROUND + 1, tz)
    sign(f, cx + 2, GROUND + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 5, GROUND + 1, cz - 12, 0, ["8階", "沈黙の湖", "", "ここでは、音がしない"])
    chest(f, cx + 11, GROUND + 1, cz - 4, "west")
    for (dx, dz) in ((-10, -12), (10, -12), (-7, 2), (7, 2)):
        lamp(f, cx + dx, cz + dz)
    # 入口から石橋までの道（雪を踏み固めた道）
    for z in range(GORGE[1] + 2, cz - 15):
        for x in range(cx - 2, cx + 3):
            f.put(x, f.top(x, z), z, block("snow_block") if (x + z) % 3 else block("polished_diorite"))
            for y in range(f.top(x, z) + 1, f.top(x, z) + 4):
                f.put(x, y, z, None)
            f.reserved[x, z] = True
    sign(f, cx + 4, GROUND + 1, GORGE[1] + 4, 0, ["黒い裂け目", "", "沈んだ石橋は", "入江の鐘で浮かぶ"])
    f.story_zones.append({"id": "gorge", "box": [cx - 8, GROUND, GORGE[1] + 2, cx + 8, GROUND + 6, GORGE[1] + 10]})


def cove(f):
    """入江（南の岸の、小さな凍った入り江）と、そのまわりの朽ちた舟小屋"""
    cx, cz = COVE
    for x in range(cx - 22, cx + 23):
        for z in range(cz - 20, cz + 23):
            if f.kind[x, z] == 0 and math.hypot(x - cx, z - cz) < 24:
                f.reserved[x, z] = True
    # 舟小屋（南西。屋根が半分落ちている）
    bx, bz = cx - 22, cz + 10
    flatten(f, bx - 4, bz - 3, bx + 4, bz + 4)
    for x in range(bx - 3, bx + 4):
        for z in range(bz - 2, bz + 4):
            edge = x in (bx - 3, bx + 3) or z in (bz - 2, bz + 3)
            for y in range(GROUND + 1, GROUND + 5):
                if edge and not (z == bz - 2 and abs(x - bx) <= 1 and y < GROUND + 4):
                    if R.at(x, y, z, 60) < 0.82:
                        f.put(x, y, z, block("spruce_planks" if y < GROUND + 4 else "spruce_slab", **({} if y < GROUND + 4 else {"type": "bottom", "waterlogged": False})))
            if R.at(x, z, 61) < 0.45:
                f.put(x, GROUND + 5, z, block("spruce_slab", type="bottom", waterlogged=False))
    chest(f, bx + 1, GROUND + 1, bz + 2, "north")
    f.story_zones.append({"id": "cove", "box": [cx - 20, GROUND, cz - 18, cx + 36, GROUND + 6, cz + 20]})


def reed(f, x, z, h):
    y = f.top(x, z) + 1
    for k in range(h):
        f.put(x, y + k, z, block("chain", axis="y", waterlogged=False))
    f.put(x, y + h, z, block("white_stained_glass_pane", north=False, south=False, east=False, west=False, waterlogged=False))


def marsh(f):
    """西の葦原：凍った小さな沼と、白く凍った葦。名のある魔物「霜冠」（凍て鶴の長）の空き地"""
    for k in range(1600):
        x = 26 + int(R.at(k, 1, 70) * 70)
        z = 330 + int(R.at(k, 2, 70) * 230)
        if f.kind[x, z] != 0 or f.reserved[x, z] or f.top(x, z) > GROUND + 3:
            continue
        if R.at(k, 3, 70) < 0.5:
            reed(f, x, z, 1 + int(R.at(k, 4, 70) * 3))
            f.reserved[x, z] = True
    # 凍った小さな沼
    for (px, pz, r) in ((56, 372, 5), (66, 514, 5), (52, 470, 4)):
        for x in range(px - r - 2, px + r + 3):
            for z in range(pz - r - 2, pz + r + 3):
                if r <= math.hypot(x - px, z - pz) < r + 2.5 and f.kind[x, z] == 0:
                    for y in range(GROUND - 5, f.top(x, z)):
                        if f.v[x, y, z] == 0:
                            f.put(x, y, z, block("dirt"))
        for x in range(px - r, px + r + 1):
            for z in range(pz - r, pz + r + 1):
                if math.hypot(x - px, z - pz) < r and f.kind[x, z] == 0:
                    top = f.top(x, z)
                    for y in range(GROUND - 3, GROUND):
                        f.put(x, y, z, block("water", level=0))
                    f.put(x, GROUND - 4, z, block("mud"))
                    f.put(x, GROUND, z, LAKE_ICE)
                    for y in range(GROUND + 1, top + 4):
                        f.put(x, y, z, None)
                    f.height[x, z] = GROUND
                    f.reserved[x, z] = True
    wx, wz = WEST_LAIR
    for x in range(wx - 12, wx + 13):
        for z in range(wz - 12, wz + 13):
            if math.hypot(x - wx, z - wz) < 12 and f.kind[x, z] == 0:
                f.reserved[x, z] = True
                f.put(x, GROUND, z, block("snow_block"))
                f.height[x, z] = GROUND
                for y in range(GROUND + 1, GROUND + 8):
                    f.put(x, y, z, None)
    for k in range(18):
        a = k / 18 * math.tau
        x = int(round(wx + math.cos(a) * 11))
        z = int(round(wz + math.sin(a) * 11))
        if f.kind[x, z] == 0:
            reed(f, x, z, 3 + k % 2)
    chest(f, wx - 4, GROUND + 1, wz - 6, "south", guardian="crane_lord")
    f.guardians.append({"id": "crane_lord", "enemy": "amemaru:frost_crane", "variant": "amemaru:frost_crane_lord",
                        "pos": [wx + 0.5, GROUND + 1, wz + 3.5], "respawn_seconds": 300, "activation": 26, "pillar": -1})
    sign(f, wx + 5, GROUND + 1, wz + 8, 8, ["西の葦原", "", "凍て鶴の長", "霜冠がいる"])


def grotto(f):
    """東の声の洞：東の山にあいた洞穴。壁じゅうに、声を吸う紫の結晶。名のある魔物「千声喰い」"""
    gx, gz = EAST_LAIR
    for x in range(gx - 16, min(SIZE - 2, gx + 16)):
        for z in range(gz - 16, gz + 17):
            d = math.hypot(x - gx, z - gz)
            if d < 14:
                ceil = GROUND + 10 + int(3 * (1 - d / 14))
                for y in range(GROUND - 2, GROUND):
                    f.put(x, y, z, block("stone"))
                f.put(x, GROUND, z, block("polished_deepslate") if d < 6 else block("deepslate"))
                for y in range(GROUND + 1, ceil):
                    f.put(x, y, z, None)
                for y in range(ceil, ceil + 3):
                    f.put(x, y, z, block("deepslate"))
                f.height[x, z] = GROUND
                f.kind[x, z] = 0
                f.reserved[x, z] = True
            elif d < 16:
                top = max(f.top(x, z), GROUND + 14)
                for y in range(GROUND, top):
                    if f.v[x, y, z] == 0:
                        f.put(x, y, z, block("deepslate"))
                for y in range(GROUND + 1, GROUND + 9):
                    if R.at(x, y, z, 80) < 0.18:
                        f.put(x, y, z, block("amethyst_block"))
    # 入口（西へ。幅7）
    for x in range(gx - 30, gx - 12):
        for z in range(gz - 3, gz + 4):
            f.put(x, GROUND, z, block("deepslate") if x > gx - 20 else block("snow_block"))
            for y in range(GROUND + 1, GROUND + 7):
                f.put(x, y, z, None)
            for y in range(GROUND + 7, GROUND + 9):
                if x > gx - 22:
                    f.put(x, y, z, block("deepslate"))
            f.height[x, z] = GROUND
            f.kind[x, z] = 0
            f.reserved[x, z] = True
    for k in range(10):
        a = k / 10 * math.tau
        x = int(round(gx + math.cos(a) * 12))
        z = int(round(gz + math.sin(a) * 12))
        f.put(x, GROUND + 1, z, block("amethyst_cluster", facing="up", waterlogged=False))
    for (x, z) in ((gx - 5, gz - 5), (gx + 5, gz + 5), (gx - 5, gz + 6), (gx + 6, gz - 4)):
        f.put(x, GROUND + 5, z, block("light", level=9, waterlogged=False))
    chest(f, gx + 4, GROUND + 1, gz - 7, "south", guardian="voice_elder")
    f.guardians.append({"id": "voice_elder", "enemy": "amemaru:voice_eater", "variant": "amemaru:voice_eater_elder",
                        "pos": [gx + 0.5, GROUND + 2.5, gz + 0.5], "respawn_seconds": 300, "activation": 24, "pillar": -1})
    sign(f, gx - 14, GROUND + 1, gz + 4, 4, ["声の洞", "", "千の声を食べた", "声喰いがいる"])


def fishers(f):
    """凍った釣り人たち（湖の上に腰かけたまま凍った人たち。氷の穴に糸を垂らしている）"""
    fx, fz = FISHERS
    seats = [(-3, -2, "old_man", 90.0), (3, -1, "man", 270.0), (-2, 3, "child_boy", 30.0), (4, 4, "woman", 300.0)]
    holes = [(-1, -2), (1, -1), (0, 2), (2, 4)]
    for (dx, dz) in holes:
        f.put(fx + dx, GROUND, fz + dz, block("water", level=0))
        f.put(fx + dx, GROUND + 1, fz + dz, None)
    for (dx, dz, skin, yaw) in seats:
        f.put(fx + dx, GROUND + 1, fz + dz, None)
        statue(f, fx + dx, GROUND + 1, fz + dz, skin, "fish", yaw, 0.8 if skin.startswith("child") else 1.0)
    for dx in range(-7, 8):
        for dz in range(-6, 8):
            f.reserved[fx + dx, fz + dz] = True
    f.story_zones.append({"id": "fishers", "box": [fx - 7, GROUND, fz - 6, fx + 7, GROUND + 5, fz + 7]})


def scatter_chests(f):
    spots = [(384, 600, "west"), (250, 486, "east"), (470, 372, "west"), (156, 352, "east"), (566, 548, "west"),
             (430, 560, "north"), (286, 330, "south"), (604, 330, "west"), (208, 296, "east"), (512, 296, "west"),
             (372, 440, "west"), (100, 520, "east"), (620, 520, "west"), (300, 690, "east")]
    for (x, z, facing) in spots:
        y = max(f.top(x, z), GROUND) + 1
        if f.kind[x, z] != 0:
            f.put(x, GROUND, z, LAKE_ICE)
        for yy in range(y, y + 3):
            f.put(x, yy, z, None)
        chest(f, x, y, z, facing)
        f.reserved[x, z] = True


def waypoints(f):
    for (wid, name, x, z, yaw) in (("bridge", "石橋の北", 374, 598, 180), ("west", "西の葦原", 110, 432, 90),
                                   ("east", "東の声の洞", 640, 432, -90), ("shrine", "祠の上", 374, 304, 180)):
        if f.kind[x, z] == 0:
            flatten(f, x - 2, z - 3, x + 2, z + 2, "polished_diorite")
            y = GROUND + 1
        else:
            for xx in range(x - 2, x + 3):
                for zz in range(z - 3, z + 3):
                    f.put(xx, GROUND, zz, block("polished_diorite"))
                    f.put(xx, GROUND + 1, zz, None)
                    f.reserved[xx, zz] = True
            y = GROUND + 1
        waypoint(f, wid, name, x, y, z, yaw)


def path_lamps(f):
    """湖の道の灯（雪をかぶった厚い氷の道の、ところどころ）"""
    for pts, half in PATHS:
        for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
            seg = math.hypot(x1 - x0, z1 - z0)
            for k in range(1, int(seg // 26) + 1):
                t = k * 26 / seg
                x = int(round(x0 + (x1 - x0) * t + (z1 - z0) / seg * (half + 1.5)))
                z = int(round(z0 + (z1 - z0) * t - (x1 - x0) / seg * (half + 1.5)))
                if f.reserved[x, z] and f.kind[x, z] == 0:
                    continue
                if f.kind[x, z] in (1, 2):
                    f.put(x, GROUND, z, LAKE_ICE)
                    f.put(x, GROUND + 1, z, None)
                    if f.thin[x, z]:
                        continue
                lamp(f, x, z, 2)


def trees(f):
    """岸の、葉の落ちた白い木と、雪をかぶったトウヒ（少しだけ）"""
    n = SIZE
    count = 0
    for k in range(9000):
        x = 6 + int(R.at(k, 1, 300) * (n - 12))
        z = 6 + int(R.at(k, 2, 300) * (n - 12))
        if f.kind[x, z] != 0 or f.reserved[max(0, x - 2):x + 3, max(0, z - 2):z + 3].any():
            continue
        if (f.kind[max(0, x - 3):x + 4, max(0, z - 3):z + 4] != 0).any():
            continue
        top = f.top(x, z)
        if top > GROUND + 14 or top < GROUND:
            continue
        if R.at(k, 3, 300) > 0.45:
            continue
        if R.at(k, 4, 300) < 0.6:
            dead_tree(f, x, top + 1, z, 5 + int(R.at(k, 5, 300) * 5))
        else:
            spruce(f, x, top + 1, z, 6 + int(R.at(k, 6, 300) * 5))
        count += 1
    return count


def dead_tree(f, x, y, z, h):
    for k in range(h):
        f.put(x, y + k, z, block("stripped_birch_log", axis="y"))
    for k in range(2, h):
        if R.at(x, y + k, z, 301) < 0.35:
            dx, dz = [(1, 0), (-1, 0), (0, 1), (0, -1)][int(R.at(x, k, z, 302) * 4)]
            f.put(x + dx, y + k, z + dz, block("stripped_birch_log", axis="x" if dx else "z"))
            f.put(x + dx, y + k + 1, z + dz, snow_layer(1))
    f.put(x, y + h, z, snow_layer(2))
    f.reserved[x, z] = True


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


CONNECT_SKIP = {"snow", "spruce_leaves", "lantern", "soul_lantern", "spruce_sign", "light", "sea_lantern", "chain", "water",
                "amemaru:lake_bell", "amemaru:song_stele", "amemaru:lake_tablet"}


def finalize(f):
    """ガラス板の向きを、まわりに合わせる"""
    def solid(x, y, z):
        n = f.name_at(x, y, z)
        return n is not None and n not in CONNECT_SKIP and not n.endswith("_slab") and not n.endswith("_stairs")

    for x, y, z in zip(*np.nonzero(f.v == f.index.get(block("white_stained_glass_pane", north=False, south=False, east=False,
                                                                west=False, waterlogged=False), -1))):
        x, y, z = int(x), int(y), int(z)
        sides = {}
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            n = f.name_at(x + dx, y, z + dz)
            sides[face] = bool(n is not None and (n.endswith("pane") or solid(x + dx, y, z + dz)))
        if any(sides.values()):
            f.put(x, y, z, block("white_stained_glass_pane", waterlogged=False, **sides))


# ================================================================ 階の定義

SPAWNS = [
    # (id, 敵, x, z, 半径, 数, 湧き直す秒, 群れ, 近づくと動く距離, 宙に浮く高さ)
    # 南の岸
    ("shore_cranes", "frost_crane", 330, 676, 12, 3, 70, (2, 3)),
    ("cove_cranes", "frost_crane", 268, 660, 10, 2, 80, (1, 2)),
    # 大湖の南
    ("south_fish", "silent_fish", 330, 540, 14, 2, 80, (1, 2), 30),
    ("south_fish_e", "silent_fish", 410, 540, 14, 2, 80, (1, 2), 30),
    ("strip_cranes", "frost_crane", 420, 598, 10, 2, 80, (1, 2)),
    ("fishers_eaters", "voice_eater", 432, 520, 10, 2, 80, (1, 2), 36, 1),
    ("south_wraiths", "fog_wraith", 280, 560, 14, 2, 80, (1, 2), 40, 1),
    # 歌の碑のまわり
    ("stele_eaters", "voice_eater", 360, 456, 10, 2, 80, (1, 2), 36, 1),
    ("stele_fish_w", "silent_fish", 300, 430, 14, 2, 80, (1, 2), 30),
    ("stele_fish_e", "silent_fish", 420, 430, 14, 2, 80, (1, 2), 30),
    # 西
    ("west_fish", "silent_fish", 200, 440, 14, 2, 80, (1, 2), 30),
    ("west_wraiths", "fog_wraith", 160, 400, 14, 2, 80, (1, 2), 40, 1),
    ("marsh_cranes", "frost_crane", 62, 360, 12, 3, 70, (2, 3)),
    ("marsh_cranes_s", "frost_crane", 70, 520, 12, 3, 70, (2, 3)),
    ("moon_eaters", "voice_eater", 112, 486, 8, 2, 80, (1, 2), 36, 1),
    # 東
    ("east_fish", "silent_fish", 520, 440, 14, 2, 80, (1, 2), 30),
    ("east_wraiths", "fog_wraith", 560, 400, 14, 2, 80, (1, 2), 40, 1),
    ("flower_eaters", "voice_eater", 610, 486, 8, 2, 80, (1, 2), 36, 1),
    ("east_cranes", "frost_crane", 640, 520, 10, 2, 80, (1, 2)),
    # 北（祠のまわり・霧の濃い所）
    ("north_fish", "silent_fish", 360, 340, 14, 2, 80, (1, 2), 30),
    ("crane_isle_eaters", "voice_eater", 222, 300, 8, 2, 80, (1, 2), 36, 1),
    ("fish_isle_eaters", "voice_eater", 498, 300, 8, 2, 80, (1, 2), 36, 1),
    ("north_wraiths_w", "fog_wraith", 290, 300, 14, 2, 80, (1, 2), 40, 1),
    ("north_wraiths_e", "fog_wraith", 430, 300, 14, 2, 80, (1, 2), 40, 1),
    ("shrine_wraiths", "fog_wraith", 360, 320, 12, 2, 80, (1, 2), 40, 1),
]


def spawn_zones(f):
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=44, air=0):
        y = max(f.top(cx, cz), GROUND) + 1 + air
        return {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
                "respawn_seconds": respawn, "group": list(group), "activation": activation}
    return [zone(*z) for z in SPAWNS]


def keep_spawns_clear(f):
    for z in SPAWNS:
        cx, cz = z[2], z[3]
        f.reserved[max(0, cx - 3):cx + 4, max(0, cz - 3):cz + 4] = True


def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    exc, ezc = f.exit_circle
    return {
        "_comment": "塔の8階「沈黙の湖」（tools/mapgen/build_floor8.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 2,
        "name": "沈黙の湖",
        "chapter_title": "第2章　凍てついた記憶",
        "recommended_level": [32, 39],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor8/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, GROUND + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, GROUND + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:mune",
            "name": "湖心",
            "spawn": {"pos": [cx + 0.5, GROUND - 5, cz + 0.5], "yaw": 180},
            "arena": {"center": [cx + 0.5, GROUND + 1, cz + 0.5], "radius": 52.0, "y_min": 8, "y_max": GROUND + 28},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 34,
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
        "pillars_done": "湖に、音が戻った。祠の奥の扉が、静かに開く……",
        # 古代記録（records/f08_lake_shrine.json と同じ文）
        "tablet": {"pos": list(f.tablet), "title": "湖底の祠の石板",
                   "text": "沈黙は、争いを止めた。だが、歌も止めた。祠の守り手より。湖に音が戻る日まで、この石板を沈めておく。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 音のない湖（灰青の霧）。湖の鐘を鳴らすまで、階じゅうの音が消えている
        "environment": {"fog": [24, 140], "fog_color": "#AEB9C4", "silent": True},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        "seal_requires": "bells+guardians",
        "bells": f.bell_groups,
        "lake": {"ice_y": GROUND, "rescue_seconds": 4},
        "relics": f.relics,
        "hearths": [{"id": hid, "name": name, "pos": list(pos)} for hid, name, pos in f.hearths],
        "memory_statues": [{"id": mid, "name": name, "pos": list(pos), "event": "amemaru:floor8/memory_" + mid}
                           for mid, name, pos in f.memories],
        "boss_marks": {"arena_bells": [[x, GROUND + 1, z] for (x, z) in ARENA_BELLS], "arena_center": [[cx, GROUND, cz]]},
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
    fill_ground(f)
    barriers(f)
    gorge_walls(f)
    lake_surface(f)
    shadows(f)
    entrance(f)
    cove(f)
    islands(f)
    bells(f)
    shrine(f)
    tunnel(f)
    arena(f)
    marsh(f)
    grotto(f)
    fishers(f)
    scatter_chests(f)
    waypoints(f)
    path_lamps(f)
    keep_spawns_clear(f)
    f.tree_count = trees(f)
    finalize(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor8") if out else os.path.join(RES, "structures", "map", "floor8")
    json_path = os.path.join(out, "floor8.json") if out else os.path.join(RES, "amemaru", "floors", "floor8.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "鐘の組:", [g["id"] for g in f.bell_groups],
          "木:", f.tree_count)
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor8_figures
        floor8_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
