"""塔の5階「古代守護者の祭壇」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor5.json）を書き出す。

Phase 0-A 9章の5階（確定）：深い奈落の上の円形の祭壇。4本の柱と、上へ続く大扉。天井の星は、よく見ると格子の結び目。
雑魚は監視眼ウォッチャー（浮かぶ目。見つかると守護者が強くなる）・防衛兵センチネル（盾持ち）・修復ドローン（守護者を回復する
ので最優先）。ボスは古代守護者（第1章の章ボス・6人パーティ）。ギミックは4つの「結び台」と「接続判定」。
物語は5日目：古代文明の滅亡とリンク・コアの存在が判明。ガルドが正式な冒険者と認める。ロストの侵食：守護者の左腕が白く欠ける。

使い方:
  python3 tools/mapgen/build_floor5.py                        # Modの structures/map/floor5/ と floors/floor5.json を書き直す
  python3 tools/mapgen/build_floor5.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

決まり（11月24日【補完】。docs/phase8_altar.md）：
  - 576×576（4階より広い）、高さ96。奈落の洞の中に、浮かぶ石の道と台が続く（祭壇への道は、歩ける広さで1階くらい。区画5つ）。
    天井（高さ92）は暗い岩に、12マスごとの格子の線と、その結び目に光る石（星に見えて、よく見ると格子の結び目）
  - 区画：入口の岩棚 → 監視の回廊（浮かぶ石の道。監視眼）→ 分かれ道の台 → 東：防衛兵の砦（城壁と内郭）／西：修復の工廠
    （機械の台。ドローン）→ 祭壇の前庭 → 円形の祭壇（ボス）
  - 4本の「接続の柱」（1階と同じ石柱の仕組み）：監視の大眼を倒す（回廊の西の台）・防衛隊長を倒す（砦の内郭）・
    修復ドローンのぜんまい3つ（工廠）・前庭の仕掛け石2つを12秒のうちに押して開く祠の中。4本が灯ると祭壇の封印が開く
  - 奈落（abyss_y 24）：落ちると、ともした中継の灯（なければ入口）へ戻される（最大HPの15%）
  - 祭壇：半径36の円。外まわりに低い欄干。斜め4か所に結界の柱、東西南北の半径18に結び台、まん中に結び目の模様。
    北に「上へ続く大扉」、その前に出口の円と次の階への円
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

FLOOR = 5
SIZE = 576
HEIGHT = 96
PIECE = 48
ORIGIN = (20480 - SIZE // 2, 40, -SIZE // 2)
GROUND = 48             # 道と台の床のブロック（立つのは 49）
CEIL = 92               # 天井
ABYSS_Y = 24            # これより下へ落ちたら戻す
CHEST_LOOT = "amemaru:chests/floor5_ancient"

ENTRY = (288, 548)
JUNCTION = (288, 392)
JUNCTION_R = 15
BASTION = (360, 288, 500, 392)          # 防衛兵の砦（x0, z0, x1, z1）
KEEP = (420, 304, 472, 346)             # 内郭
WORKSHOP = (70, 288, 216, 392)          # 修復の工廠
FORECOURT = (204, 148, 372, 204)        # 祭壇の前庭
ARENA = (288, 72)
ARENA_R = 36
SEAL_Z = 112


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


R = Rand(20261124 + 5)

ALTAR_STONE = [("polished_deepslate", 3), ("deepslate_tiles", 3), ("deepslate_bricks", 2), ("cracked_deepslate_tiles", 1)]
PATH_STONE = [("smooth_stone", 3), ("polished_andesite", 3), ("andesite", 1), ("calcite", 2), ("cracked_stone_bricks", 1)]
ROCK = [("deepslate", 4), ("cobbled_deepslate", 2), ("tuff", 2), ("blackstone", 1)]
WALL = [("stone_bricks", 3), ("cracked_stone_bricks", 2), ("polished_andesite", 2), ("calcite", 1)]
BRONZE = [("waxed_oxidized_copper", 2), ("waxed_weathered_copper", 2), ("waxed_oxidized_cut_copper", 1)]
CEILING = [("deepslate_tiles", 4), ("polished_blackstone", 2), ("blackstone", 2)]


def thing(index=0, group=""):
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


def wall_state(name, **sides):
    props = {"north": "none", "south": "none", "east": "none", "west": "none", "up": True, "waterlogged": False}
    props.update(sides)
    return block(name, **props)


def stairs(mat, facing, half="bottom"):
    return block(mat + "_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(mat, kind="bottom"):
    return block(mat + "_slab", type=kind, waterlogged=False)


class Floor:
    def __init__(self):
        self.v = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)
        self.carved = np.zeros((SIZE, HEIGHT, SIZE), dtype=bool)
        self.states = [None]
        self.index = {}
        self.nbt = {}
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.pillars = []
        self.switch_stones = []
        self.gate = None
        self.seal_cells = []
        self.story_zones = []
        self.signs = []
        self.daises = []
        self.barrier_pillars = []
        self.travel_stone = None
        self.walk = np.zeros((SIZE, SIZE), dtype=bool)      # 歩ける床（散らばる飾りや湧く所の確かめに）

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
        self.carved[x, y, z] = state is None
        if nbt is not None:
            self.nbt[(x, y, z)] = nbt
        else:
            self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.states[self.v[x, y, z]]

    def name_at(self, x, y, z):
        s = self.get(x, y, z)
        return None if s is None else s[0]


def sign(f, x, y, z, rotation, lines):
    f.put(x, y, z, block("birch_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))
    f.signs.append((x, y, z))


def chest(f, x, y, z, facing, guardian=""):
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing), thing(len(f.chests), ""))
    f.chests.append(((x, y, z), guardian))


def waypoint(f, wid, name, x, y, z, facing_yaw, look_at=None):
    index = len(f.waypoints)
    f.put(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    yaw = facing_yaw
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    if look_at is not None:
        yaw = round(math.degrees(math.atan2(-(look_at[0] - (ax + 0.5)), look_at[1] - (az + 0.5))), 1)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


def lantern_post(f, x, z, y=GROUND, tall=3, mat="deepslate_brick_wall"):
    for k in range(1, tall + 1):
        f.put(x, y + k, z, wall_state(mat))
    f.put(x, y + tall + 1, z, block("soul_lantern", hanging=False, waterlogged=False))


# ================================================================ 浮かぶ石（島・道）

def island(f, cx, cz, r, top=GROUND, table=PATH_STONE, salt=0, depth=1.0, edge=ROCK):
    """円い浮かぶ台。上は敷石、下は岩が尖って奈落へ垂れる"""
    ri = int(math.ceil(r)) + 1
    for x in range(cx - ri, cx + ri + 1):
        for z in range(cz - ri, cz + ri + 1):
            d = math.hypot(x - cx, z - cz) + (R.at(x, z, salt, 1) - 0.5) * 1.2
            if d > r:
                continue
            under = int((r - d) * 0.9 * depth + 2 + R.at(x, z, salt, 2) * 3)
            for y in range(max(1, top - under), top):
                f.put(x, y, z, block(R.pick(edge, x, y, z, salt + 3)))
            f.put(x, top, z, block(R.pick(table if d < r - 1.2 else edge, x, z, salt + 4)))
            f.walk[x, z] = True


def rect_platform(f, x0, z0, x1, z1, top=GROUND, table=PATH_STONE, salt=0, depth=6):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = min(x - x0, x1 - x, z - z0, z1 - z)
            under = depth + int(R.at(x, z, salt, 5) * 4) - (3 if edge < 2 else 0)
            for y in range(max(1, top - under), top):
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, salt + 6)))
            f.put(x, top, z, block(R.pick(table, x, z, salt + 7)))
            f.walk[x, z] = True


def walkway(f, a, b, width=5, top=GROUND, salt=0, rail=0.55):
    """2点を結ぶ浮かぶ石の道（幅 width）。両側の欄干は rail の割合だけ残る（崩れている）"""
    (ax, az), (bx, bz) = a, b
    length = math.hypot(bx - ax, bz - az)
    steps = int(length * 2) + 1
    half = width / 2.0
    dx, dz = (bx - ax) / max(1e-6, length), (bz - az) / max(1e-6, length)
    nx, nz = -dz, dx
    cells = set()
    for i in range(steps + 1):
        t = i / steps
        cx, cz = ax + (bx - ax) * t, az + (bz - az) * t
        for k in range(-int(half * 2), int(half * 2) + 1):
            off = k / 2.0
            x, z = int(round(cx + nx * off)), int(round(cz + nz * off))
            cells.add((x, z, abs(off) > half - 0.6))
    edge_cells = {(x, z) for x, z, e in cells if e}
    for x, z, _ in cells:
        if not (0 <= x < SIZE and 0 <= z < SIZE):
            continue
        if f.walk[x, z] and f.v[x, top, z] != 0:
            continue
        for y in range(top - 3, top):
            if f.v[x, y, z] == 0:
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, salt + 8)))
        f.put(x, top, z, block(R.pick(PATH_STONE, x, z, salt + 9)))
        f.walk[x, z] = True
    for (x, z) in edge_cells:
        if 0 <= x < SIZE and 0 <= z < SIZE and f.v[x, top + 1, z] == 0 and R.at(x, z, salt, 10) < rail:
            # まわりがもう広い台なら欄干は置かない
            near = sum(1 for ox in (-2, 0, 2) for oz in (-2, 0, 2) if 0 <= x + ox < SIZE and 0 <= z + oz < SIZE and f.walk[x + ox, z + oz])
            if near < 9:
                f.put(x, top + 1, z, wall_state("polished_deepslate_wall"))


# ================================================================ 洞（外の壁・天井）

def cavern(f):
    """外まわりの岩壁と、格子の星の天井"""
    for x in range(SIZE):
        for z in range(SIZE):
            edge = min(x, z, SIZE - 1 - x, SIZE - 1 - z)
            if edge <= 3:
                for y in range(0, CEIL + 1):
                    f.put(x, y, z, block(R.pick(ROCK, x, y, z, 11)))
            # 天井（2段）
            f.put(x, CEIL, z, block(R.pick(CEILING, x, z, 12)))
            f.put(x, CEIL + 1, z, block("deepslate"))
            # 格子の線（12マスごと）と、結び目の光る石
            on_x = x % 12 == 6
            on_z = z % 12 == 6
            if on_x and on_z:
                f.put(x, CEIL, z, block("sea_lantern"))
                for (ox, oz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    f.put(x + ox, CEIL, z + oz, block("polished_blackstone_bricks"))
            elif on_x or on_z:
                f.put(x, CEIL, z, block("polished_blackstone"))
    # 壁から突き出た岩と、壁の小さな光（遠くの壁に見える星）
    for k in range(260):
        side = k % 4
        t = int(R.at(k, 1, 13) * (SIZE - 8)) + 4
        y = int(R.at(k, 2, 13) * (CEIL - 10)) + 4
        x, z = {0: (4, t), 1: (SIZE - 5, t), 2: (t, 4), 3: (t, SIZE - 5)}[side]
        size = 1 + int(R.at(k, 3, 13) * 3)
        for ox in range(-size, size + 1):
            for oz in range(-size, size + 1):
                for oy in range(-size, size + 1):
                    if ox * ox + oz * oz + oy * oy <= size * size + 1:
                        f.put(x + ox, y + oy, z + oz, block(R.pick(ROCK, x + ox, y + oy, z + oz, 14)))
        if R.at(k, 4, 13) < 0.3:
            f.put(x, y, z, block("glow_lichen", down=False, up=False, north=True, south=False, east=False, west=False, waterlogged=False))
    # 奈落の中の浮かぶ岩（深さが分かるように。道の下の遠く）
    for k in range(70):
        bx = 20 + int(R.at(k, 1, 15) * (SIZE - 40))
        bz = 20 + int(R.at(k, 2, 15) * (SIZE - 40))
        by = 6 + int(R.at(k, 3, 15) * 26)
        size = 2 + int(R.at(k, 4, 15) * 4)
        for x in range(bx - size, bx + size + 1):
            for z in range(bz - size, bz + size + 1):
                for y in range(by - size, by + 2):
                    d = math.hypot(x - bx, z - bz) + (by - y) * 0.7
                    if d <= size and f.v[x, y, z] == 0:
                        f.put(x, y, z, block(R.pick(ROCK, x, y, z, 16)))


# ================================================================ 入口の岩棚

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


def entrance(f):
    cx, cz = ENTRY
    rect_platform(f, cx - 40, cz - 28, cx + 40, SIZE - 5, salt=20)
    for x in range(cx - 40, cx + 41):
        for z in range(cz - 28, SIZE - 4):
            if math.hypot((x - cx) / 40.0, (z - cz - 8) / 36.0) > 1.0:
                for y in range(1, GROUND + 1):
                    if not (min(x, z, SIZE - 1 - x, SIZE - 1 - z) <= 3):
                        f.put(x, y, z, None)
                f.walk[x, z] = False
    return_circle(f, cx, GROUND, cz)
    tx, tz = cx - 9, cz - 6
    f.put(tx, GROUND + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, GROUND + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, GROUND + 1, tz)
    sign(f, cx + 2, GROUND + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 3, GROUND + 1, cz - 14, 8, ["5階", "古代守護者の祭壇", "", "奈落に落ちると戻される"])
    for (dx, dz) in ((-18, -16), (18, -16), (-22, 6), (22, 6)):
        lantern_post(f, cx + dx, cz + dz)
    chest(f, cx + 24, GROUND + 1, cz - 2, "west")
    f.story_zones.append({"id": "abyss_edge", "box": [cx - 30, GROUND, cz - 26, cx + 30, GROUND + 6, cz - 16]})


# ================================================================ 監視の回廊（浮かぶ石の道）

GALLERY_PATH = [(288, 520), (288, 500), (250, 482), (214, 456), (226, 428), (262, 410), (288, 404)]


def gallery(f):
    for a, b in zip(GALLERY_PATH, GALLERY_PATH[1:]):
        walkway(f, a, b, width=5, salt=30)
    for (x, z) in GALLERY_PATH[1:-1]:
        island(f, x, z, 6, salt=31)
    # 西の台（監視の大眼。接続の柱 A）・東の台（宝箱）
    island(f, 168, 470, 15, salt=32, depth=1.4)
    walkway(f, (214, 456), (180, 466), width=4, salt=33)
    island(f, 352, 470, 10, salt=34)
    walkway(f, (288, 500), (344, 474), width=4, salt=35)
    chest(f, 356, GROUND + 1, 474, "west")
    pillar(f, 0, 160, 466, "east", "great_eye")
    f.guardians.append({"id": "great_eye", "enemy": "amemaru:watcher", "variant": "amemaru:watcher_great",
                        "pos": [170.5, GROUND + 4, 474.5], "respawn_seconds": 300, "activation": 30, "pillar": 0})
    chest(f, 176, GROUND + 1, 480, "north", guardian="great_eye")
    # 監視の塔（道の脇の小さな台に、細い石の柱と見張りの台）。白く欠けた塔はロストの侵食
    for (x, z, node) in ((240, 470, (250, 482)), (228, 442, (226, 428)), (276, 424, (262, 410))):
        island(f, x, z, 3, salt=36 + x)
        walkway(f, node, (x, z), width=3, salt=37 + x)
        for y in range(GROUND + 1, GROUND + 9):
            f.put(x, y, z, block("polished_deepslate"))
        for ox in (-1, 0, 1):
            for oz in (-1, 0, 1):
                f.put(x + ox, GROUND + 9, z + oz, block("deepslate_tiles"))
        f.put(x, GROUND + 10, z, block("amemaru:lost_blank") if x == 276 else block("sea_lantern"))


# ================================================================ 南の脇道（記録の間・崩れた見張り台）

def side_areas(f):
    """南西の「記録の間」（崩れた書庫の台。本棚と記録の石板）と、南東の「崩れた見張り台」（高い段と壊れた見張りの塔）。
    どちらも行き止まりの寄り道で、宝箱と素材の多い所【補完】"""
    # 記録の間
    rect_platform(f, 70, 460, 196, 536, table=[("polished_deepslate", 3), ("deepslate_tiles", 2), ("calcite", 1)], salt=100)
    walkway(f, (ENTRY[0] - 34, ENTRY[1] - 6), (196, 512), width=5, salt=101)
    for k in range(9):
        x = 82 + k * 13
        for z in (474, 494, 514):
            if R.at(k, z, 102) < 0.25:
                continue
            for dx in range(0, 7):
                h = 3 + int(R.at(x + dx, z, 103) * 2)
                for y in range(GROUND + 1, GROUND + 1 + h):
                    f.put(x + dx, y, z, block("bookshelf") if y < GROUND + h else block("dark_oak_slab", type="bottom", waterlogged=False))
    for (x, z) in ((90, 528), (140, 468), (186, 530)):
        chest(f, x, GROUND + 1, z, "north" if z > 500 else "south")
    sign(f, 132, GROUND + 1, 526, 8, ["記録の間", "", "崩れた書庫。古い", "記録の石板が並ぶ"])
    for (x, z) in ((110, 470), (160, 470), (110, 524), (160, 524)):
        lantern_post(f, x, z, mat="polished_blackstone_wall")
    # 崩れた見張り台（高い段。階段で上がる）
    rect_platform(f, 392, 446, 520, 532, salt=110)
    walkway(f, (ENTRY[0] + 34, ENTRY[1] - 6), (392, 512), width=5, salt=111)
    hx0, hz0, hx1, hz1 = 440, 452, 512, 500
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            for y in range(GROUND + 1, GROUND + 7):
                f.put(x, y, z, block(R.pick(WALL, x, y, z, 112)) if (x in (hx0, hx1) or z in (hz0, hz1)) else block("stone"))
            f.put(x, GROUND + 7, z, block(R.pick(PATH_STONE, x, z, 113)))
    for i in range(6):
        z = hz1 + 6 - i
        for x in range(470, 477):
            for y in range(GROUND + 1, GROUND + 1 + i):
                f.put(x, y, z, block("stone_bricks"))
            f.put(x, GROUND + 1 + i, z, stairs("stone_brick", "south"))
            for y in range(GROUND + 2 + i, GROUND + 6 + i):
                f.put(x, y, z, None)
    for x in range(470, 477):
        for y in range(GROUND + 8, GROUND + 11):
            f.put(x, y, hz1, None)
        f.put(x, GROUND + 7, hz1, block("stone_bricks"))
    tx, tz = 500, 462
    for y in range(GROUND + 8, GROUND + 20):
        for (ox, oz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            if y < GROUND + 14 or (ox, oz) != (2, 2):
                f.put(tx + ox, y, tz + oz, block("polished_deepslate"))
    for ox in range(-2, 3):
        for oz in range(-2, 3):
            if R.at(ox, oz, 114) < 0.7:
                f.put(tx + ox, GROUND + 20, tz + oz, block("deepslate_tile_slab", type="bottom", waterlogged=False))
    f.put(tx, GROUND + 8, tz, block("amemaru:lost_blank"))
    for (x, z) in ((452, 462), (505, 492), (402, 520)):
        chest(f, x, GROUND + 8 if hx0 <= x <= hx1 and hz0 <= z <= hz1 else GROUND + 1, z, "south")
    sign(f, 473, GROUND + 1, hz1 + 9, 8, ["崩れた見張り台", "", "上の段から、奈落の", "向こうまで見渡せる"])


# ================================================================ 分かれ道の台

def junction(f):
    cx, cz = JUNCTION
    island(f, cx, cz, JUNCTION_R, salt=40, depth=1.6)
    for (x, z) in [(cx + int(round(math.cos(a) * (JUNCTION_R - 3))), cz + int(round(math.sin(a) * (JUNCTION_R - 3))))
                   for a in [k * math.pi / 3 for k in range(6)]]:
        lantern_post(f, x, z)
    waypoint(f, "junction", "分かれ道の台", cx, GROUND + 1, cz + 6, 0, look_at=(cx, cz - 20))
    sign(f, cx + 2, GROUND + 1, cz + 8, 0, ["分かれ道", "", "東：防衛兵の砦", "西：修復の工廠"])
    walkway(f, (cx + JUNCTION_R - 2, cz - 4), (BASTION[0] + 2, cz - 30), width=5, salt=41)
    walkway(f, (cx - JUNCTION_R + 2, cz - 4), (WORKSHOP[2] - 2, cz - 30), width=5, salt=42)


# ================================================================ 防衛兵の砦（東）

def bastion(f):
    x0, z0, x1, z1 = BASTION
    rect_platform(f, x0, z0, x1, z1, table=[("stone_bricks", 3), ("polished_andesite", 3), ("cracked_stone_bricks", 1)], salt=50)
    # 城壁（高さ6。上に胸壁）。西と北に門
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                west_gate = x == x0 and abs(z - 362) <= 3
                north_gate = z == z0 and abs(x - 430) <= 3
                if west_gate or north_gate:
                    continue
                for y in range(GROUND + 1, GROUND + 7):
                    f.put(x, y, z, block(R.pick(WALL, x, y, z, 51)))
                if (x + z) % 2 == 0:
                    f.put(x, GROUND + 7, z, block("stone_bricks"))
    # 門の上の飾りと、門の内側の見張り台（角の塔）
    for (gx, gz, axis) in ((x0, 362, "z"), (430, z0, "x")):
        for k in range(-4, 5):
            x, z = (gx, gz + k) if axis == "z" else (gx + k, gz)
            f.put(x, GROUND + 7, z, block("chiseled_stone_bricks"))
            f.put(x, GROUND + 8, z, block("chiseled_stone_bricks") if abs(k) == 4 else block("stone_brick_slab", type="bottom",
                                                                                                waterlogged=False))
    for (tx, tz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for x in range(tx - 3, tx + 4):
            for z in range(tz - 3, tz + 4):
                if 0 <= x < SIZE and 0 <= z < SIZE:
                    for y in range(GROUND - 4, GROUND + 12):
                        f.put(x, y, z, block(R.pick(WALL, x, y, z, 52)))
                    if (x + z) % 2 == 0:
                        f.put(x, GROUND + 12, z, block("stone_bricks"))
        f.put(tx, GROUND + 12, tz, block("sea_lantern"))
    # 内郭（接続の柱 B と防衛隊長）
    kx0, kz0, kx1, kz1 = KEEP
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            f.put(x, GROUND, z, block("polished_deepslate") if (x + z) % 3 else block("deepslate_tiles"))
            if x in (kx0, kx1) or z in (kz0, kz1):
                if z == kz1 and abs(x - (kx0 + kx1) // 2) <= 2:
                    continue
                for y in range(GROUND + 1, GROUND + 5):
                    f.put(x, y, z, block("deepslate_bricks"))
    pillar(f, 1, (kx0 + kx1) // 2, kz0 + 8, "south", "captain")
    f.guardians.append({"id": "captain", "enemy": "amemaru:sentinel", "variant": "amemaru:sentinel_captain",
                        "pos": [(kx0 + kx1) // 2 + 0.5, GROUND + 1, kz0 + 22.5], "respawn_seconds": 300, "activation": 28, "pillar": 1})
    chest(f, kx1 - 3, GROUND + 1, kz0 + 3, "west", guardian="captain")
    # 兵舎（盾の立てかけ）と、訓練の木偶
    for k in range(6):
        x = x0 + 10 + k * 7
        for z in (z1 - 8, z1 - 7):
            f.put(x, GROUND + 1, z, block("polished_andesite"))
        f.put(x, GROUND + 2, z1 - 8, block("anvil", facing="east"))
    chest(f, x0 + 6, GROUND + 1, z0 + 6, "south")
    chest(f, x1 - 6, GROUND + 1, z1 - 6, "north")
    waypoint(f, "bastion", "防衛兵の砦", x0 + 14, GROUND + 1, 372, -90, look_at=(430, 330))
    for (lx, lz) in ((x0 + 8, z0 + 8), (x1 - 8, z0 + 8), (x0 + 8, z1 - 12), (x1 - 8, z1 - 12), (440, 360)):
        lantern_post(f, lx, lz)


# ================================================================ 修復の工廠（西）

def workshop(f):
    x0, z0, x1, z1 = WORKSHOP
    # いくつかの台を、青銅の橋でつなぐ
    pads = [(x0 + 28, z0 + 74, 16), (x0 + 72, z0 + 70, 14), (x0 + 110, z0 + 60, 13), (x0 + 40, z0 + 28, 15), (x0 + 96, z0 + 22, 14)]
    for k, (x, z, r) in enumerate(pads):
        island(f, x, z, r, table=[("polished_deepslate", 2), ("waxed_weathered_cut_copper", 1), ("smooth_stone", 2)], salt=60 + k)
    links = [(0, 1), (1, 2), (0, 3), (3, 4), (2, 4)]
    for a, b in links:
        walkway(f, pads[a][:2], pads[b][:2], width=4, salt=65 + a * 5 + b, rail=0.8)
    # 機械（青銅の箱・歯車の柱・作業台）
    for k, (x, z, r) in enumerate(pads):
        for m in range(3):
            mx = x + int((R.at(k, m, 70) - 0.5) * r)
            mz = z + int((R.at(k, m, 71) - 0.5) * r)
            if not f.walk[mx, mz] or math.hypot(mx - x, mz - z) > r - 3:
                continue
            h = 2 + int(R.at(k, m, 72) * 3)
            for y in range(GROUND + 1, GROUND + 1 + h):
                f.put(mx, y, mz, block(R.pick(BRONZE, mx, y, mz, 73)))
            f.put(mx, GROUND + 1 + h, mz, block("lightning_rod", facing="up", powered=False, waterlogged=False))
        f.put(x + 3, GROUND + 1, z - 3, block("smithing_table"))
    # 接続の柱 C（ドローンのぜんまい3つ）
    px, pz, _ = pads[2]
    pillar(f, 2, px + 4, pz - 2, "west", None, item=("amemaru:drone_spring", 3))
    for (x, z, r) in pads[:2] + pads[3:]:
        chest(f, x - 2, GROUND + 1, z + r - 4, "north")
    waypoint(f, "workshop", "修復の工廠", pads[0][0] + 4, GROUND + 1, pads[0][1] - 4, 180, look_at=(pads[1][0], pads[1][1]))
    for (x, z, r) in pads:
        lantern_post(f, x - r + 3, z, mat="polished_blackstone_wall")
    walkway(f, (WORKSHOP[2] - 2, JUNCTION[1] - 30), pads[1][:2], width=4, salt=74)
    f.workshop_pads = pads


# ================================================================ 祭壇の前庭

SWITCHES = [(266, 168), (310, 168)]


def forecourt(f):
    x0, z0, x1, z1 = FORECOURT
    rect_platform(f, x0, z0, x1, z1, table=ALTAR_STONE, salt=80, depth=8)
    # 砦と工廠からの道
    walkway(f, (430, BASTION[1] + 1), (x1 - 4, z1 - 10), width=5, salt=81)
    walkway(f, (f.workshop_pads[4][0], f.workshop_pads[4][1]), (x0 + 4, z1 - 10), width=5, salt=82)
    # 祠（接続の柱 D）。前を格子の門がふさぐ。仕掛け石2つを12秒のうちに押すと、1分開く
    sx0, sz0, sx1, sz1 = 278, 182, 298, 200
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            f.put(x, GROUND, z, block("polished_deepslate"))
            if x in (sx0, sx1) or z == sz1:
                for y in range(GROUND + 1, GROUND + 7):
                    f.put(x, y, z, block("deepslate_bricks"))
            f.put(x, GROUND + 7, z, block("deepslate_tile_slab", type="bottom", waterlogged=False))
    gate = []
    for x in range(sx0 + 1, sx1):
        for y in range(GROUND + 1, GROUND + 7):
            if abs(x - (sx0 + sx1) // 2) <= 2 and y <= GROUND + 4:
                f.put(x, y, sz0, block("amemaru:tower_gate", open=False))
                gate.append((x, y, sz0))
            else:
                f.put(x, y, sz0, block("deepslate_bricks"))
    f.gate = ([min(c[0] for c in gate), GROUND + 1, sz0], [max(c[0] for c in gate), GROUND + 4, sz0])
    pillar(f, 3, (sx0 + sx1) // 2, sz1 - 5, "north", None)
    for i, (wx, wz) in enumerate(SWITCHES):
        for x in range(wx - 2, wx + 3):
            for z in range(wz - 2, wz + 3):
                if math.hypot(x - wx, z - wz) <= 2.3:
                    f.put(x, GROUND, z, block("polished_deepslate"))
        f.put(wx, GROUND + 1, wz, block("amemaru:tower_switch", lit=False), thing(i, "shrine"))
        f.switch_stones.append((wx, GROUND + 1, wz))
    sign(f, 288, GROUND + 1, sz0 - 2, 8, ["接続の祠", "", "東と西の仕掛け石を", "時を合わせて押せ"])
    waypoint(f, "forecourt", "祭壇の前庭", 240, GROUND + 1, 186, 180, look_at=(288, 120))
    for (lx, lz) in ((x0 + 6, z0 + 6), (x1 - 6, z0 + 6), (x0 + 6, z1 - 6), (x1 - 6, z1 - 6)):
        lantern_post(f, lx, lz)
    chest(f, x0 + 3, GROUND + 1, z0 + 3, "south")
    chest(f, x1 - 3, GROUND + 1, z0 + 3, "south")
    f.story_zones.append({"id": "lattice_sky", "box": [x0, GROUND, z0, x1, GROUND + 8, z1]})
    # 祭壇への石の道（封印の門まで）
    for x in range(280, 297):
        for z in range(SEAL_Z, z0 + 1):
            for y in range(GROUND - 4, GROUND):
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, 83)))
            f.put(x, GROUND, z, block(R.pick(ALTAR_STONE, x, z, 84)))
            f.walk[x, z] = True
        for z in range(SEAL_Z + 2, z0, 6):
            if x in (280, 296):
                lantern_post(f, x, z, mat="polished_deepslate_wall")


# ================================================================ 接続の柱（1階と同じ石柱の仕組み）

PILLAR_LINES = [
    "見張る眼は、つなぐ手の数を数えている",
    "盾は、背にいる者のためにある",
    "直すことは、忘れないことだ",
    "四つの柱が灯るとき、祭壇は試しの場となる",
]
PILLAR_HINTS = {
    0: "柱の核は、まだ眠っている。西の台の大きな眼が、ずっとこちらを見張っている……",
    1: "柱の核は、まだ眠っている。砦の内郭で、隊長の盾が道をふさいでいる……",
    2: "柱の核の歯車が止まっている。修復ドローンのぜんまいが3つあれば、動きそうだ",
}


def pillar(f, index, px, pz, facing, guardian, item=None):
    fx, fz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    g = GROUND
    # 柱（3×3、高さ12。黒い石に翠の筋）。正面に核の台
    for dy in range(0, 13):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = px + dx - fx * 2, pz + dz - fz * 2
                mat = block("chiseled_deepslate") if dy in (4, 9) else block("polished_deepslate")
                if dy == 12 and dx != 0 and dz != 0:
                    continue
                f.put(x, g + dy, z, mat)
    for dy in range(1, 11):
        if dy not in (4, 9):
            f.put(px - fx, g + dy, pz - fz, block("lime_stained_glass"))
    ax, az = px + fx, pz + fz
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            f.put(ax + dx, g, az + dz, block("chiseled_polished_blackstone") if dx == 0 and dz == 0 else block("polished_blackstone"))
    f.put(ax, g + 1, az, block("amemaru:tower_pillar"), Compound({"id": String("amemaru:tower_pillar"), "floor": Int(FLOOR),
                                                                  "index": Int(index)}))
    for y in range(g + 2, g + 6):
        f.put(ax, y, az, None)
    entry = {"pos": [ax, g + 1, az], "line": PILLAR_LINES[index]}
    if guardian:
        entry["requires"] = {"type": "guardian", "guardian": guardian, "hint": PILLAR_HINTS[index]}
    elif item:
        entry["requires"] = {"type": "items", "item": item[0], "count": item[1], "hint": PILLAR_HINTS[index]}
    while len(f.pillars) <= index:
        f.pillars.append(None)
    f.pillars[index] = entry


# ================================================================ 円形の祭壇（ボス）

def arena(f):
    cx, cz = ARENA
    for x in range(cx - ARENA_R - 3, cx + ARENA_R + 4):
        for z in range(cz - ARENA_R - 3, cz + ARENA_R + 4):
            d = math.hypot(x - cx, z - cz)
            if d > ARENA_R + 0.5:
                continue
            under = int((ARENA_R - d) * 0.6) + 4
            for y in range(max(1, GROUND - under), GROUND):
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, 90)))
            ring = int(d) % 8
            if d > ARENA_R - 1.5:
                f.put(x, GROUND, z, block("polished_deepslate"))
            elif ring == 0:
                f.put(x, GROUND, z, block("chiseled_deepslate"))
            else:
                f.put(x, GROUND, z, block(R.pick(ALTAR_STONE, x, z, 91)))
            f.walk[x, z] = True
            for y in range(GROUND + 1, CEIL - 2):
                if f.v[x, y, z] != 0 and f.name_at(x, y, z) not in ("sea_lantern",):
                    f.put(x, y, z, None)
    # 欄干（南の入口と北の大扉の前はあける）
    for k in range(720):
        a = k / 720 * math.tau
        x = int(round(cx + math.cos(a) * ARENA_R))
        z = int(round(cz + math.sin(a) * ARENA_R))
        deg = (math.degrees(a) + 360) % 360
        if 80 <= deg <= 100 or 260 <= deg <= 280:
            continue
        f.put(x, GROUND + 1, z, wall_state("polished_deepslate_wall"))
    # まん中の結び目の模様（金）
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 4.5:
                knot = abs(abs(x - cx) - abs(z - cz)) <= 0 or d < 1.2 or 3.4 < d <= 4.5
                f.put(x, GROUND, z, block("gold_block") if knot and d > 0.6 else block("chiseled_polished_blackstone"))
    f.center = (cx, GROUND + 1, cz)
    # 結び台（東西南北の半径18）
    for (dx, dz) in ((0, -18), (18, 0), (0, 18), (-18, 0)):
        x, z = cx + dx, cz + dz
        for ox in (-1, 0, 1):
            for oz in (-1, 0, 1):
                f.put(x + ox, GROUND, z + oz, block("chiseled_quartz_block") if (ox == 0 or oz == 0) else block("gold_block"))
        f.put(x, GROUND + 1, z, block("amemaru:binding_dais", lit=False))
        f.daises.append([x, GROUND + 1, z])
    # 結界の柱（斜め4か所。半径31。3×3、高さ18。上に翠の結晶）
    for k in range(4):
        a = math.radians(45 + 90 * k)
        x = int(round(cx + math.cos(a) * 30))
        z = int(round(cz + math.sin(a) * 30))
        for y in range(GROUND + 1, GROUND + 19):
            for ox in (-1, 0, 1):
                for oz in (-1, 0, 1):
                    f.put(x + ox, y, z + oz, block("chiseled_deepslate") if y % 6 == 0 else block("polished_deepslate"))
        for y in range(GROUND + 19, GROUND + 23):
            f.put(x, y, z, block("emerald_block") if y < GROUND + 22 else block("end_rod", facing="up"))
        f.put(x, GROUND + 20, z + 1, block("amethyst_cluster", facing="south", waterlogged=False))
        f.barrier_pillars.append([x, GROUND + 18, z])
    # 上へ続く大扉（北）
    dz0 = cz - ARENA_R - 2
    for x in range(cx - 18, cx + 19):
        for z in range(dz0 - 6, dz0 + 3):
            for y in range(GROUND - 6, GROUND + 1):
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, 92)))
            f.put(x, GROUND, z, block("polished_deepslate"))
            f.walk[x, z] = True
    for x in range(cx - 16, cx + 17):
        for y in range(GROUND + 1, GROUND + 34):
            frame = abs(x - cx) >= 12 or y >= GROUND + 28
            if frame:
                f.put(x, y, dz0 - 5, block("chiseled_deepslate") if (x + y) % 7 == 0 else block("polished_deepslate"))
            else:
                # 閉じた大扉（翠の格子の線）
                line = (x - cx) % 4 == 0 or (y - GROUND) % 5 == 0
                f.put(x, y, dz0 - 5, block("lime_stained_glass") if line else block("deepslate_tiles"))
    for y in range(GROUND + 1, GROUND + 28):
        for x in (cx - 13, cx + 13):
            f.put(x, y, dz0 - 4, block("chiseled_deepslate"))
    # 出口の円と次の階への円（大扉の前）
    return_circle(f, cx, GROUND, cz - 24)
    next_circle(f.put, cx + NEXT_DX, GROUND, cz - 24)
    # 封印の門（南の入口）
    for x in range(cx - 4, cx + 5):
        for z in range(cz + ARENA_R - 2, SEAL_Z + 1):
            f.put(x, GROUND, z, block("polished_deepslate"))
            for y in range(GROUND - 4, GROUND):
                f.put(x, y, z, block(R.pick(ROCK, x, y, z, 93)))
            f.walk[x, z] = True
    for x in range(cx - 4, cx + 5):
        for y in range(GROUND + 1, GROUND + 7):
            f.put(x, y, SEAL_Z, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, SEAL_Z))
        for y in range(GROUND + 7, GROUND + 10):
            f.put(x, y, SEAL_Z, block("chiseled_deepslate"))
    for x in (cx - 5, cx + 5):
        for y in range(GROUND + 1, GROUND + 10):
            f.put(x, y, SEAL_Z, block("polished_deepslate"))
    # 見えない灯（祭壇はいつも明るく）
    for x in range(cx - ARENA_R + 2, cx + ARENA_R - 1, 5):
        for z in range(cz - ARENA_R + 2, cz + ARENA_R - 1, 5):
            if math.hypot(x - cx, z - cz) < ARENA_R - 2 and f.v[x, GROUND + 4, z] == 0:
                f.put(x, GROUND + 4, z, block("light", level=14, waterlogged=False))


def ambient_lights(f):
    y = GROUND + 3
    for x in range(4, SIZE - 4, 7):
        for z in range(4, SIZE - 4, 7):
            if f.walk[x, z] and f.v[x, y, z] == 0 and f.v[x, y - 1, z] == 0 and f.v[x, GROUND, z] != 0:
                f.put(x, y, z, block("light", level=12, waterlogged=False))


# ================================================================ 階の定義

def spawn_zones():
    def zone(id_, enemy, cx, y, cz, radius, max_, respawn, group, activation=44):
        return {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
                "respawn_seconds": respawn, "group": list(group), "activation": activation}
    g = GROUND + 1
    air = GROUND + 4
    return [
        zone("gallery_eyes_a", "watcher", 270, air, 492, 10, 2, 70, (1, 2)),
        zone("gallery_eyes_b", "watcher", 222, air, 444, 10, 3, 70, (1, 2)),
        zone("gallery_eyes_c", "watcher", 256, air, 416, 10, 2, 70, (1, 2)),
        zone("gallery_east", "sentinel", 352, g, 470, 6, 2, 80, (1, 2)),
        zone("gallery_drones", "repair_drone", 246, air, 480, 8, 2, 80, (1, 1)),
        zone("junction_eyes", "watcher", 288, air, 380, 10, 2, 70, (1, 2)),
        zone("bastion_gate", "sentinel", 380, g, 362, 8, 3, 70, (2, 3)),
        zone("bastion_yard_w", "sentinel", 400, g, 320, 10, 3, 70, (2, 3)),
        zone("bastion_yard_e", "sentinel", 485, g, 370, 8, 3, 70, (2, 3)),
        zone("bastion_drones", "repair_drone", 450, air, 372, 8, 2, 80, (1, 2)),
        zone("bastion_eyes", "watcher", 440, air + 4, 300, 10, 2, 70, (1, 1)),
        zone("workshop_drones_a", "repair_drone", 98, air, 362, 10, 3, 70, (1, 2)),
        zone("workshop_drones_b", "repair_drone", 166, air, 350, 10, 3, 70, (1, 2)),
        zone("workshop_guards", "sentinel", 140, g, 358, 8, 3, 70, (1, 2)),
        zone("workshop_eyes", "watcher", 110, air + 2, 316, 12, 2, 70, (1, 2)),
        zone("bridge_east", "watcher", 400, air, 245, 10, 2, 70, (1, 2)),
        zone("bridge_west", "watcher", 187, air, 252, 10, 2, 70, (1, 2)),
        # 南西の記録の間・南東の崩れた見張り台
        zone("archive_eyes", "watcher", 120, air + 2, 480, 12, 3, 70, (1, 2)),
        zone("archive_guards", "sentinel", 150, g, 505, 10, 3, 70, (1, 2)),
        zone("archive_drones", "repair_drone", 95, air, 500, 10, 2, 80, (1, 1)),
        zone("lookout_guards", "sentinel", 470, g + 7, 476, 10, 3, 70, (1, 2)),
        zone("lookout_eyes", "watcher", 420, air, 480, 12, 3, 70, (1, 2)),
        zone("forecourt_guards", "sentinel", 288, g, 160, 12, 4, 70, (2, 3)),
        zone("forecourt_drones", "repair_drone", 240, air, 165, 8, 2, 80, (1, 1)),
        zone("forecourt_eyes", "watcher", 340, air + 2, 170, 10, 2, 70, (1, 2)),
    ]


def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    data = {
        "_comment": "塔の5階「古代守護者の祭壇」（tools/mapgen/build_floor5.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 1,
        "name": "古代守護者の祭壇",
        "chapter_title": "第1章　失われた古代領域",
        "recommended_level": [9, 12],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor5/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, GROUND + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, GROUND + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:ancient_guardian",
            "name": "円形の祭壇",
            "spawn": {"pos": [cx + 0.5, GROUND + 1, cz - 8.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, GROUND + 1, cz + 0.5], "radius": ARENA_R - 1.0, "y_min": GROUND - 2, "y_max": GROUND + 24},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 26,
            "respawn_seconds": 150,
            "max_players": 8,
            # 第1章の章ボス（6人パーティ）。ひとりでも2人ぶんのHP（11月24日）【補完】
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 2100,
            "exit": {"center": [cx + 0.5, GROUND + 1, cz - 23.5], "radius": 2.6},
            "next": {"center": [cx + NEXT_DX + 0.5, GROUND + 1, cz - 23.5], "radius": 2.6},
        },
        "pillars": f.pillars,
        "pillars_done": "四本の接続の柱が灯った。祭壇への封印が、静かにほどけていく……",
        "spawns": spawn_zones(),
        "guardians": f.guardians,
        "switches": [{"id": "shrine", "stones": [list(s) for s in f.switch_stones], "window_seconds": 12, "open_seconds": 60,
                      "gate": [list(f.gate[0]), list(f.gate[1])]}],
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 奈落の洞（暗い青緑。遠くは闇に沈む）
        "environment": {"fog": [24, 150], "fog_color": "#16242E"},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        "seal_requires": "pillars",
        "abyss_y": ABYSS_Y,
        "boss_marks": {"binding_daises": f.daises, "altar_center": [list(f.center)], "barrier_pillars": f.barrier_pillars},
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
    cavern(f)
    entrance(f)
    gallery(f)
    side_areas(f)
    junction(f)
    bastion(f)
    workshop(f)
    forecourt(f)
    arena(f)
    ambient_lights(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor5") if out else os.path.join(RES, "structures", "map", "floor5")
    json_path = os.path.join(out, "floor5.json") if out else os.path.join(RES, "amemaru", "floors", "floor5.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "柱:", len(f.pillars), "結び台:", len(f.daises))
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor5_figures
        floor5_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
