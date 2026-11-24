"""塔の3階「古代の水路」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor3.json）を書き出す。

Phase 0-A 9章の3階（確定）：地下に広がる古代の水路網。水門、水車、青い結晶の灯。水没した区画もある。雑魚は水路掃除機兵・
澱みスライム・牙ナマズ・水門ネズミ。ボスは大水門の番人ハイドラム（水の大砲。水位を下げると胸の炉心が露出して弱点になる）。
ギミックは、離れた部屋の水門を、複数人が声をかけ合って同時に操作し、水の流れを変える。古代記録「水路管理局の掲示」。
ロストの侵食：水が流れの途中で白い空白に消えている区画。

使い方:
  python3 tools/mapgen/build_floor3.py                       # Modの structures/map/floor3/ と floors/floor3.json を書き直す
  python3 tools/mapgen/build_floor3.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

広さは 480×480（1階の約1.6倍。11月23日のご指示「一層上がるたびに広く・複雑に」）、高さの幅 64。天井のある地下。
座標はすべて階の (0, 0, 0) から。歩ける所は2つの層：上の通路（床のブロックの高さ 26。立つのは 27）と、下の層（床 14。立つのは 15）。

決まり（Phase 8 その2【補完】。docs/phase8_waterway.md）：
  - 掘った所（空気と水）を決め、そのまわり2マスを壁にする（そのほかは何も置かない）
  - 区画は8つ：入口の船着き場・大水路・水路管理局の広間（中継の灯1・古代記録の掲示）・西の貯水槽・東の水車の間・
    沈んだ区画（下の層）・ネズミの巣穴・白い流れ（下の層。ロストの侵食）。北に大水門の前の回廊と、ボスの部屋（大水門）
  - 水門は3組と、ボスの部屋の排水：
      ①「西と東の水門」（西の貯水槽と東の水車の間の操作室）→ 沈んだ区画の水が引く
      ②「沈んだ区画の奥の水門」（北西と北東）→ 白い流れへの道の水が引き、水門の扉が上がる（①のあとでないと回らない）
      ③「大水門の前の水門」（回廊の西と東）→ 大水門の前の堀の水が引き、封印へ下りられる（②のあとでないと回らない）
      排水（ボスの部屋の東と西）→ ボスの部屋の水が引き、番人の炉心がむき出しになる
  - 水の区画（水門で引く水）は、まわりを壁・扉・ほかの水でかこむ（空気に触れると、置いたときに流れ出すため。
    check_floor3.py が確かめる）。水に沈む所には、水に浸かると形の変わるブロック（階段・板・柵など）を置かない
    （階段は水を含ませて置く。水門の水が引くと、水も抜ける）
  - 中継の灯は5つ（広間・西の貯水槽・東の水車の間・沈んだ区画・大水門の前）
"""
import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapgen.world import DATA_VERSION, block, lantern, sign_nbt  # noqa: E402
from mapgen.tower import NEXT_DX, next_circle  # noqa: E402

import nbtlib  # noqa: E402
from nbtlib.tag import Compound, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

FLOOR = 3
SIZE = 480
HEIGHT = 64
PIECE = 48
ORIGIN = (12288 - SIZE // 2, 60, -SIZE // 2)
UP = 26                 # 上の通路の床のブロック（立つのは 27）
LOW = 14                # 下の層の床のブロック（立つのは 15）
CANAL_BOTTOM = 20       # 上の水路の底（水は 21〜25）
CHEST_LOOT = "amemaru:chests/floor3_ancient"

# 区画（x0, z0, x1, z1。両端を含む）
DOCK = (216, 432, 264, 472)
ENTRY = (240, 452)                       # 戻りの円
CANAL = (220, 284, 260, 433)
CANAL_WATER = (233, 247)                 # 大水路の水の x
HUB = (240, 262)
HUB_R = 30
HUB_BASIN_R = 9
CISTERN = (30, 190, 144, 330)
CISTERN_BOTTOM = 19                      # 貯水槽の底（水は 20〜25）
EAST = (336, 200, 450, 320)
EAST_CHANNEL = (254, 268)                # 水車の水路の z
SUNKEN = (156, 110, 324, 225)            # 沈んだ区画（下の層）
RAMP = (232, 248)                        # 広間から沈んだ区画へ下りる坂の x（z は 208〜233）
WHITE = (24, 24, 110, 118)               # 白い流れ（下の層）
GALLERY = (150, 76, 330, 104)            # 大水門の前の回廊（上の層）
MOAT = (222, 258)                        # 堀の x（z は回廊と同じ）
SEAL_Z = 75                              # 封印の面
ARENA = (240, 40)                        # ボスの部屋（大水門）の真ん中。床は LOW
ARENA_R = 28
WARREN_DEN = (420, 66)


class Rand:
    """場所ごとに決まる乱数（作り直しても同じ形になる）"""

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


R = Rand(20261124)

UPPER_WALL = [("stone_bricks", 6), ("cracked_stone_bricks", 1), ("mossy_stone_bricks", 2), ("andesite", 1)]
LOWER_WALL = [("deepslate_bricks", 5), ("cracked_deepslate_bricks", 1), ("deepslate_tiles", 2), ("polished_deepslate", 1)]
FLOOR_STONE = [("polished_andesite", 4), ("stone_bricks", 3), ("andesite", 1), ("cracked_stone_bricks", 1)]
LOW_FLOOR = [("deepslate_tiles", 4), ("polished_deepslate", 2), ("cracked_deepslate_tiles", 1), ("deepslate_bricks", 2)]
BRONZE = [("waxed_oxidized_cut_copper", 4), ("waxed_weathered_cut_copper", 3), ("waxed_oxidized_copper", 1)]
WATER = block("water", level=0)
AIR = None

# 水に浸かると形の変わるブロック（水の区画の中では waterlogged=true で置く）
WATERLOGGABLE = ("_stairs", "_slab", "_wall", "_fence", "_pane", "lantern", "_sign", "chain", "iron_bars", "_trapdoor")


def thing(index=0, group=""):
    """階の仕掛け（中継の灯・道しるべの石・水門のハンドル）のブロックの「もの」。"""
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


# ================================================================ 立体（numpy）

class Floor:
    """ブロックの番号の立体。0 は何もない（空気）。carved は掘った所（空気・水・掘った所の中の飾り）"""

    def __init__(self):
        self.v = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)
        self.carved = np.zeros((SIZE, HEIGHT, SIZE), dtype=bool)
        self.states = [None]
        self.index = {}
        self.nbt = {}
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.floodgates = []
        self.water_sections = []
        self.travel_stone = None
        self.tablet = None
        self.seal_cells = []
        self.story_zones = []
        self.valves = {}         # 水門の id → [ハンドルの場所2つ]
        self.doors = {}          # 水門の id → [扉の箱]
        self.lost_zones = []     # ロストの侵食：（箱 (x0, z0, x1, z1)、高さ (y0, y1)、白い空白の面、向き）
        self.sections = []       # 水門の水の区画：(id, x0, z0, x1, z1, low, high)

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
        return self.states[self.v[x, y, z]]

    def name_at(self, x, y, z):
        s = self.get(x, y, z)
        return None if s is None else s[0]

    def box(self, x0, y0, z0, x1, y1, z1):
        x0, x1 = max(0, min(x0, x1)), min(SIZE - 1, max(x0, x1))
        y0, y1 = max(0, min(y0, y1)), min(HEIGHT - 1, max(y0, y1))
        z0, z1 = max(0, min(z0, z1)), min(SIZE - 1, max(z0, z1))
        return slice(x0, x1 + 1), slice(y0, y1 + 1), slice(z0, z1 + 1)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        """固いブロックで埋める（掘った所ではなくする）"""
        b = self.box(x0, y0, z0, x1, y1, z1)
        self.v[b] = 0 if state is None else self.idx(state)
        self.carved[b] = False

    def carve(self, x0, y0, z0, x1, y1, z1):
        """空気にする（掘った所）"""
        b = self.box(x0, y0, z0, x1, y1, z1)
        self.v[b] = 0
        self.carved[b] = True

    def water(self, x0, y0, z0, x1, y1, z1):
        b = self.box(x0, y0, z0, x1, y1, z1)
        self.v[b] = self.idx(WATER)
        self.carved[b] = True

    def deco(self, x, y, z, state, nbt=None):
        """掘った所の中に置く飾り（壁にはしない）"""
        self.put(x, y, z, state, nbt)
        if 0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE:
            self.carved[x, y, z] = True

    def solid(self, x, y, z, state):
        self.put(x, y, z, state)
        if 0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE:
            self.carved[x, y, z] = False

    def is_air(self, x, y, z):
        return self.carved[x, y, z] and self.v[x, y, z] == 0


def mix(f, table, salt, x0, y0, z0, x1, y1, z1, only_empty=False):
    """表のブロックを混ぜて埋める（場所ごとに決まる）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if only_empty and (f.v[x, y, z] != 0 or f.carved[x, y, z]):
                    continue
                f.solid(x, y, z, block(R.pick(table, x, y, z, salt)))


def floor_plate(f, x0, z0, x1, z1, y, table=FLOOR_STONE, salt=1):
    """床（掘った所の下のブロック）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            f.solid(x, y, z, block(R.pick(table, x, z, salt)))


def room(f, x0, z0, x1, z1, y, height, table=FLOOR_STONE, salt=1):
    """床 y と、その上 height マスの空気の部屋"""
    f.carve(x0, y + 1, z0, x1, y + height, z1)
    floor_plate(f, x0, z0, x1, z1, y, table, salt)


def circle_cells(cx, cz, r):
    out = []
    ri = int(math.ceil(r)) + 1
    for x in range(cx - ri, cx + ri + 1):
        for z in range(cz - ri, cz + ri + 1):
            if math.hypot(x + 0.5 - (cx + 0.5), z + 0.5 - (cz + 0.5)) <= r:
                out.append((x, z))
    return out


def stair_state(mat, facing, in_water=False):
    return block(mat + "_stairs", facing=facing, half="bottom", shape="straight", waterlogged=in_water)


# ================================================================ 灯（青い結晶の灯）

def lamp_post(f, x, z, y, tall=4):
    """青い結晶の灯：石の柱の上に、海晶の灯と青い硝子の笠"""
    for k in range(1, tall):
        f.deco(x, y + k, z, block("polished_andesite"))
    f.deco(x, y + tall, z, block("sea_lantern"))
    f.deco(x, y + tall + 1, z, block("light_blue_stained_glass"))


def wall_lamp(f, x, y, z):
    """壁に埋めた結晶の灯（壁の中の海晶の灯。掘った所の外）"""
    f.solid(x, y, z, block("sea_lantern"))


def hang_lamp(f, x, y, z, length=2):
    """天井から鎖でさがる魂の灯（青い火）"""
    for k in range(length):
        f.deco(x, y - k, z, block("chain", axis="y", waterlogged=False))
    f.deco(x, y - length, z, block("soul_lantern", hanging=True, waterlogged=False))


def crystal(f, x, y, z, facing="up"):
    f.deco(x, y, z, block("amethyst_cluster", facing=facing, waterlogged=False))


# ================================================================ 区画

def vault(f, x0, z0, x1, z1, y_floor, side, mid, axis="z"):
    """かまぼこ形の天井の部屋（床 y_floor の上を掘る。端の高さ side・まん中の高さ mid。axis はかまぼこの長い向き）"""
    if axis == "z":
        span = x1 - x0
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, span) * 2.0 - 1.0
            h = side + int(round((mid - side) * math.sqrt(max(0.0, 1.0 - t * t))))
            f.carve(x, y_floor + 1, z0, x, y_floor + h, z1)
    else:
        span = z1 - z0
        for z in range(z0, z1 + 1):
            t = (z - z0) / max(1, span) * 2.0 - 1.0
            h = side + int(round((mid - side) * math.sqrt(max(0.0, 1.0 - t * t))))
            f.carve(x0, y_floor + 1, z, x1, y_floor + h, z)


def channel(f, x0, z0, x1, z1, bottom=CANAL_BOTTOM, top=UP - 1, open_top=True):
    """水路（底 bottom、水は bottom+1〜top。open_top なら、水面の上の床の高さも空ける）"""
    f.water(x0, bottom + 1, z0, x1, top, z1)
    floor_plate(f, x0, z0, x1, z1, bottom, LOWER_WALL, salt=7)
    if open_top:
        f.carve(x0, top + 1, z0, x1, top + 1, z1)


def sign(f, x, y, z, rotation, lines):
    f.deco(x, y, z, block("spruce_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))


def chest(f, x, y, z, facing, guardian=""):
    index = len(f.chests)
    f.deco(x, y, z, block("amemaru:tower_chest", facing=facing),
           Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String("")}))
    f.chests.append(((x, y, z), guardian))
    return index


def waypoint(f, wid, name, x, y, z, facing_yaw, look_at=None):
    """中継の灯。着く所は灯の facing_yaw の側に2マス。look_at（階の x, z）があれば、着いた人はそちらを向く"""
    index = len(f.waypoints)
    f.deco(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    yaw = facing_yaw
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    if look_at is not None:
        yaw = round(math.degrees(math.atan2(-(look_at[0] - (ax + 0.5)), look_at[1] - (az + 0.5))), 1)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


def valve(f, gate, side, x, y, z, facing):
    """水門のハンドル（組 gate の side 番目。0 か 1）"""
    f.deco(x, y, z, block("amemaru:floodgate_valve", facing=facing, open=False), thing(side, gate))
    f.valves.setdefault(gate, [None, None])[side] = [x, y, z]


def return_circle(f, cx, y, cz):
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if d <= 0.6:
                f.solid(x, y, z, block("chiseled_quartz_block"))
            elif d <= 1.7:
                f.solid(x, y, z, block("quartz_bricks"))
            elif d <= 2.7:
                f.solid(x, y, z, block("smooth_quartz"))
            elif d <= 3.3:
                f.solid(x, y, z, block("prismarine_bricks"))


# ---------------------------------------------------------------- 入口の船着き場

def entrance(f):
    x0, z0, x1, z1 = DOCK
    vault(f, x0, z0, x1, z1, UP, 6, 10, axis="z")
    floor_plate(f, x0, z0, x1, z1, UP)
    # 船着き場（南の水）と、つないだ小舟
    hx0, hz0, hx1, hz1 = 228, 461, 252, 470
    channel(f, hx0, hz0, hx1, hz1)
    for x in range(hx0 - 1, hx1 + 2):
        for z in (hz0 - 1,):
            f.solid(x, UP, z, block("smooth_stone"))
    for x in range(236, 245):
        for z in range(464, 469):
            edge = x in (236, 244) or z in (464, 468)
            f.solid(x, 24, z, block("spruce_planks"))
            f.solid(x, 25, z, block("spruce_planks") if edge else block("stripped_spruce_wood", axis="y"))
    for z in range(465, 468):
        f.deco(240, UP, z, block("spruce_slab", type="bottom", waterlogged=False))
    for x in (230, 250):
        f.deco(x, UP + 1, hz0 - 1, block("andesite_wall", north="none", south="none", east="none", west="none", up=True,
                                         waterlogged=False))
    # 戻りの円と、着く所
    cx, cz = ENTRY
    return_circle(f, cx, UP, cz)
    # 灯・看板・道しるべの石
    for (dx, dz) in ((-18, -14), (18, -14), (-18, 6), (18, 6)):
        lamp_post(f, cx + dx, cz + dz, UP)
    sign(f, cx + 2, UP + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 3, UP + 1, z0 + 2, 0, ["3階", "古代の水路", "", "管理局は北へ"])
    tx, tz = cx - 8, cz - 6
    f.deco(tx, UP + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, UP + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, UP + 1, tz)
    for x in range(x0, x1 + 1, 6):
        wall_lamp(f, x, UP + 4, z1 + 1)
    chest(f, x0 + 3, UP + 1, z1 - 3, "east")


# ---------------------------------------------------------------- 大水路

def main_canal(f):
    x0, z0, x1, z1 = CANAL
    wx0, wx1 = CANAL_WATER
    vault(f, x0, z0, x1, z1, UP, 7, 12, axis="z")
    floor_plate(f, x0, z0, wx0 - 1, z1, UP)
    floor_plate(f, wx1 + 1, z0, x1, z1, UP)
    channel(f, wx0, z0, wx1, z1)
    # 水ぎわのふち石
    for z in range(z0, z1 + 1):
        f.solid(wx0 - 1, UP, z, block("smooth_stone"))
        f.solid(wx1 + 1, UP, z, block("smooth_stone"))
    # 橋
    for bz in (330, 370, 410):
        f.fill(wx0, UP, bz, wx1, UP, bz + 3, block("polished_andesite"))
        for x in range(wx0, wx1 + 1, 2):
            for z in (bz, bz + 3):
                f.deco(x, UP + 1, z, block("andesite_wall", north="none", south="none", east="none", west="none", up=True,
                                           waterlogged=False))
    # 岸の柱と、青い結晶の灯。天井からさがる魂の灯
    for z in range(z0 + 6, z1 - 2, 14):
        for x in (x0 + 1, x1 - 1):
            for y in range(UP + 1, UP + 8):
                f.solid(x, y, z, block("chiseled_stone_bricks") if y in (UP + 1, UP + 7) else block("stone_bricks"))
            wall_lamp(f, x, UP + 4, z)
        hang_lamp(f, (wx0 + wx1) // 2, UP + 11, z + 7, 2)
        lamp_post(f, wx0 - 3, z + 7, UP)
        lamp_post(f, wx1 + 3, z + 7, UP)
    # ロストの侵食：東の岸の下をくぐる小さな水路が、壁のくぼみの奥で白い空白に消えている
    sz0, sz1 = 340, 346
    channel(f, wx1 + 1, sz0, 269, sz1, open_top=False)
    f.carve(261, UP, sz0, 269, UP + 7, sz1)
    for z in range(sz0 - 1, sz1 + 2):
        f.deco(x1, UP + 1, z, block("andesite_wall", north="none", south="none", east="none", west="none", up=True,
                                    waterlogged=False))
    f.lost_zones.append(((263, 336, 276, 350), (19, 34), 270, "x+"))
    sign(f, x1 - 1, UP + 1, sz0 - 2, 4, ["水が", "白いところへ", "消えていく……", ""])
    # 水門ネズミのかじった跡（ところどころ）・宝箱
    chest(f, x0 + 2, UP + 1, 356, "east")


# ---------------------------------------------------------------- 水路管理局の広間（中継の灯1・古代記録）

def hub(f):
    cx, cz = HUB
    for (x, z) in circle_cells(cx, cz, HUB_R):
        d = math.hypot(x - cx, z - cz)
        top = UP + 7 + int(round(9 * math.sqrt(max(0.0, 1.0 - (d / HUB_R) ** 2))))
        f.carve(x, UP + 1, z, x, top, z)
        ring = int(d) % 6
        st = "polished_andesite" if ring in (0, 1) else ("stone_bricks" if R.at(x, z, 31) > 0.2 else "cracked_stone_bricks")
        if HUB_BASIN_R + 0.5 < d <= HUB_BASIN_R + 1.6:
            st = "chiseled_stone_bricks"
        f.solid(x, UP, z, block(st))
    # まん中の水盤（大水路の水が流れ込む）と、水時計の柱
    for (x, z) in circle_cells(cx, cz, HUB_BASIN_R):
        f.carve(x, UP, z, x, UP, z)
        f.water(x, CANAL_BOTTOM + 1, z, x, UP - 1, z)
        f.solid(x, CANAL_BOTTOM, z, block("deepslate_tiles"))
    wx0, wx1 = CANAL_WATER
    channel(f, wx0, cz + 5, wx1, CANAL[1])
    for y in range(CANAL_BOTTOM + 1, UP + 6):
        f.solid(cx, y, cz, block("prismarine_bricks") if y < UP + 5 else block("sea_lantern"))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        crystal(f, cx + dx, UP + 5, cz + dz, facing={(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)])
    crystal(f, cx, UP + 6, cz)
    # 水路管理局（水盤の北東）：石の小さな建物。中の机に「水路管理局の掲示」
    bx0, bz0, bx1, bz1 = 246, 240, 258, 251
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            edge = x in (bx0, bx1) or z in (bz0, bz1)
            for y in range(UP + 1, UP + 7):
                if edge:
                    corner = x in (bx0, bx1) and z in (bz0, bz1)
                    f.solid(x, y, z, block("polished_andesite") if corner or y == UP + 6 else block(R.pick(UPPER_WALL, x, y, z, 32)))
            f.solid(x, UP + 7, z, block("stone_brick_slab", type="bottom", waterlogged=False) if not edge else block("smooth_stone"))
    mx = (bx0 + bx1) // 2
    for x in range(mx - 1, mx + 2):
        for y in range(UP + 1, UP + 4):
            f.carve(x, y, bz1, x, y, bz1)
    for x in (bx0 + 3, bx1 - 3):
        f.solid(x, UP + 3, bz1, block("light_blue_stained_glass"))
    for z in (bz0 + 3, bz1 - 3):
        f.solid(bx0, UP + 3, z, block("light_blue_stained_glass"))
        f.solid(bx1, UP + 3, z, block("light_blue_stained_glass"))
    f.deco(mx, UP + 1, bz0 + 3, block("lectern", facing="south", has_book=False, powered=False))
    f.tablet = (mx, UP + 1, bz0 + 3)
    for x in range(mx - 3, mx + 4):
        f.solid(x, UP + 3, bz0, block("dark_oak_planks"))
    f.deco(mx, UP + 3, bz0 + 1, block("dark_oak_wall_sign", facing="south", waterlogged=False), sign_nbt(["水路管理局", "", "掲示", ""]))
    f.deco(bx0 + 2, UP + 1, bz0 + 2, block("barrel", facing="up", open=False))
    f.deco(bx0 + 2, UP + 2, bz0 + 2, block("soul_lantern", hanging=False, waterlogged=False))
    f.deco(bx1 - 2, UP + 1, bz0 + 2, block("cartography_table"))
    f.solid(mx, UP + 6, bz0 + 5, block("sea_lantern"))
    sign(f, mx + 3, UP + 1, bz1 + 1, 0, ["水路管理局", "", "中の机に", "古い掲示"])
    chest(f, bx1 - 1, UP + 1, bz1 - 2, "west")
    # 中継の灯（水盤の南西）
    waypoint(f, "hub", "管理局の広間", cx - 14, UP + 1, cz + 16, 0, look_at=(cx, cz))
    # 灯と、案内の看板
    for k in range(8):
        a = k / 8 * math.tau + 0.2
        x, z = int(round(cx + math.cos(a) * (HUB_R - 4))), int(round(cz + math.sin(a) * (HUB_R - 4)))
        if f.carved[x, UP + 1, z] and f.v[x, UP + 1, z] == 0 and not (bx0 - 1 <= x <= bx1 + 1 and bz0 - 1 <= z <= bz1 + 1):
            lamp_post(f, x, z, UP)
    for k in range(6):
        a = k / 6 * math.tau
        hang_lamp(f, int(round(cx + math.cos(a) * 14)), UP + 13, int(round(cz + math.sin(a) * 14)), 3)
    sign(f, cx - 22, UP + 1, cz - 3, 4, ["西：西の貯水槽", "", "水門の操作室", "（西）"])
    sign(f, cx + 8, UP + 1, cz + 8, 12, ["東：東の水車の間", "", "水門の操作室", "（東）"])
    sign(f, cx + 3, UP + 1, cz - 22, 8, ["北：沈んだ区画", "", "水が満ちている", "水門で水を引こう"])
    # 西と東の回廊
    room(f, 145, 257, cx - HUB_R + 6, 267, UP, 7)
    room(f, cx + HUB_R - 6, 257, EAST[0], 267, UP, 7)
    for x in list(range(150, 206, 10)) + list(range(278, 334, 10)):
        wall_lamp(f, x, UP + 4, 256)
        wall_lamp(f, x, UP + 4, 268)


# ---------------------------------------------------------------- 西の貯水槽（水門①の西のハンドル・監督機兵）

CISTERN_WALK_X = (44, 74, 104)
CISTERN_WALK_Z = (204, 234, 264, 294, 322)


def cistern(f):
    x0, z0, x1, z1 = CISTERN
    f.carve(x0, UP, z0, x1, UP + 16, z1)
    f.water(x0, CISTERN_BOTTOM + 1, z0, x1, UP - 1, z1)
    floor_plate(f, x0, z0, x1, z1, CISTERN_BOTTOM, LOW_FLOOR, salt=11)
    walk = lambda xa, za, xb, zb: [f.solid(x, UP, z, block(R.pick(FLOOR_STONE, x, z, 12)))   # noqa: E731
                                   for x in range(xa, xb + 1) for z in range(za, zb + 1)]
    walk(x0, z0 + 6, x0 + 3, z1 - 6)                               # 西の壁ぞいの通路
    for x in CISTERN_WALK_X:
        walk(x, z0 + 6, x + 2, z1 - 6)
    for z in CISTERN_WALK_Z:
        walk(x0, z, x1, z + 2)
    walk(126, 250, x1, 274)                                        # 東の着き場（回廊から）
    # 監督機兵の台（まん中）
    gx0, gz0, gx1, gz1 = 82, 240, 98, 256
    walk(gx0, gz0, gx1, gz1)
    walk(77, 247, gx0, 249)
    walk(gx1, 247, 104, 249)
    for (x, z) in ((gx0, gz0), (gx1, gz0), (gx0, gz1), (gx1, gz1)):
        lamp_post(f, x, z, UP)
    f.guardians.append({"id": "overseer", "enemy": "amemaru:soujikihei", "variant": "amemaru:soujikihei_overseer",
                        "pos": [90.5, UP + 1, 248.5], "respawn_seconds": 300, "activation": 36, "pillar": -1})
    chest(f, 90, UP + 1, gz0 + 1, "south", guardian="overseer")
    # 水の中の太い柱（天井まで。まん中に灯の帯）
    for cxp in (59, 89, 119):
        for czp in (219, 279, 309):
            for x in range(cxp - 1, cxp + 2):
                for z in range(czp - 1, czp + 2):
                    for y in range(CISTERN_BOTTOM + 1, UP + 17):
                        st = "sea_lantern" if y == UP + 6 and (x, z) == (cxp, czp - 1) else R.pick(LOWER_WALL if y < UP else UPPER_WALL, x, y, z, 13)
                        f.solid(x, y, z, block(st))
    # 通路の交わる所の灯
    for x in CISTERN_WALK_X:
        for z in CISTERN_WALK_Z[1:4]:
            if not (gx0 - 2 <= x <= gx1 + 2 and gz0 - 2 <= z <= gz1 + 2):
                lamp_post(f, x + 1, z + 1, UP, tall=3)
    for z in range(z0 + 4, z1, 12):
        wall_lamp(f, x0 - 1, UP + 5, z)
        wall_lamp(f, x1 + 1, UP + 5, z)
    for x in range(x0 + 4, x1, 12):
        wall_lamp(f, x, UP + 5, z0 - 1)
        wall_lamp(f, x, UP + 5, z1 + 1)
    for k, (x, z) in enumerate(((59, 249), (119, 249), (89, 232), (89, 300))):
        hang_lamp(f, x, UP + 15, z, 4)
    # 中継の灯（東の着き場）
    waypoint(f, "cistern", "西の貯水槽", 138, UP + 1, 268, 90, look_at=(90, 262))
    # 西の水門の操作室（水門①の西）
    rx0, rz0, rx1, rz1 = 14, 254, 29, 270
    room(f, rx0, rz0, rx1, rz1, UP, 6)
    for z in range(258, 267):
        for y in range(UP + 1, UP + 5):
            f.carve(rx1 + 1, y, z, rx1 + 1, y, z)
    valve(f, "first", 0, rx0 + 3, UP + 1, 262, "east")
    for z in (rz0 + 1, rz1 - 1):
        lamp_post(f, rx0 + 2, z, UP, tall=3)
    for x in range(rx0 + 1, rx1):
        f.solid(x, UP, 262, block("waxed_oxidized_cut_copper"))
    for y in range(UP + 1, UP + 6):
        f.solid(rx0 - 1, y, 262, block("waxed_oxidized_copper"))
    sign(f, rx0 + 6, UP + 1, 259, 12, ["西の水門の", "ハンドル", "東の水車の間の", "ハンドルと同時に"])
    chest(f, rx1 - 2, UP + 1, rz0 + 2, "west")
    chest(f, 44, UP + 1, 324, "north")


# ---------------------------------------------------------------- 東の水車の間（水門①の東のハンドル）

def east_hall(f):
    x0, z0, x1, z1 = EAST
    vault(f, x0, z0, x1, z1, UP, 12, 18, axis="x")
    floor_plate(f, x0, z0, x1, z1, UP)
    cz0, cz1 = EAST_CHANNEL
    channel(f, x0, cz0, x1, cz1)
    for x in range(x0, x1 + 1):
        f.solid(x, UP, cz0 - 1, block("smooth_stone"))
        f.solid(x, UP, cz1 + 1, block("smooth_stone"))
    # 橋
    for bx in (344, 378, 412, 444):
        f.fill(bx, UP, cz0, bx + 3, UP, cz1, block("polished_andesite"))
    # 水車（軸は南北。水に半分つかる）
    for wx in (361, 395, 429):
        wheel(f, wx, cz0 + 1, cz1 - 1)
    # 機械の台（南北の岸）と、銅の管
    for (mx, mz) in ((350, 220), (404, 224), (372, 300), (430, 296)):
        for x in range(mx - 4, mx + 5):
            for z in range(mz - 3, mz + 4):
                f.solid(x, UP + 1, z, block(R.pick(BRONZE, x, z, 21)))
        for y in range(UP + 2, UP + 7):
            f.solid(mx, y, mz, block("waxed_oxidized_copper"))
        f.solid(mx, UP + 7, mz, block("sea_lantern"))
        for (dx, dz) in ((-3, -2), (3, 2)):
            f.deco(mx + dx, UP + 2, mz + dz, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    for x in range(x0 + 4, x1, 10):
        wall_lamp(f, x, UP + 6, z0 - 1)
        wall_lamp(f, x, UP + 6, z1 + 1)
        lamp_post(f, x, cz0 - 3, UP)
        lamp_post(f, x + 5, cz1 + 3, UP)
    for x in range(x0 + 10, x1, 22):
        hang_lamp(f, x, UP + 16, (z0 + z1) // 2 - 20, 5)
        hang_lamp(f, x, UP + 16, (z0 + z1) // 2 + 20, 5)
    # 壁ぞいの銅の管（横に走る管と、床から天井へ上る管）
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            f.solid(x, UP + 6, z, block("waxed_oxidized_cut_copper"))
            if (x - x0) % 16 == 8:
                for y in range(UP + 1, UP + 12):
                    f.solid(x, y, z, block("waxed_oxidized_copper"))
    # 壁の大きな歯車（東の壁。止まっている）
    for (gz, r) in ((228, 6), (296, 5)):
        for k in range(48):
            a = k / 48 * math.tau
            for t in (r - 0.5, r):
                y = int(round(UP + 9 + math.sin(a) * t))
                z = int(round(gz + math.cos(a) * t))
                f.solid(x1, y, z, block("waxed_weathered_cut_copper"))
            if k % 6 == 0:
                y = int(round(UP + 9 + math.sin(a) * (r + 1)))
                z = int(round(gz + math.cos(a) * (r + 1)))
                f.solid(x1, y, z, block("waxed_weathered_copper"))
        f.solid(x1, UP + 9, gz, block("sea_lantern"))
    # 木箱の山（ところどころ）
    for (bx, bz) in ((346, 206), (366, 312), (402, 206), (440, 308), (428, 214), (352, 296)):
        for dx in range(2):
            for dz in range(2):
                f.solid(bx + dx, UP + 1, bz + dz, block("spruce_planks" if (dx + dz) % 2 else "stripped_spruce_wood", axis="y"))
        f.solid(bx, UP + 2, bz, block("barrel", facing="up", open=False))
    waypoint(f, "waterwheel", "東の水車の間", x0 + 6, UP + 1, 240, -90, look_at=(395, 262))
    # 東の水門の操作室（水門①の東）
    rx0, rz0, rx1, rz1 = x1 + 1, 230, 466, 246
    room(f, rx0, rz0, rx1, rz1, UP, 6)
    valve(f, "first", 1, rx1 - 3, UP + 1, 238, "west")
    for z in (rz0 + 1, rz1 - 1):
        lamp_post(f, rx1 - 2, z, UP, tall=3)
    for y in range(UP + 1, UP + 6):
        f.solid(rx1 + 1, y, 238, block("waxed_oxidized_copper"))
    sign(f, rx1 - 6, UP + 1, 235, 4, ["東の水門の", "ハンドル", "西の貯水槽の", "ハンドルと同時に"])
    chest(f, x1 - 3, UP + 1, z1 - 3, "north")
    chest(f, 386, UP + 2, 224, "south")
    # ネズミの巣穴への穴（北の壁）
    f.carve(418, UP + 1, z0 - 4, 422, UP + 4, z0)
    floor_plate(f, 418, z0 - 4, 422, z0, UP)
    sign(f, 424, UP + 1, z0 + 2, 8, ["この先", "ネズミの巣穴", "", "（群れに注意）"])


def wheel(f, cx, z0, z1, cy=UP - 1, r=7):
    """水車（x-y の面。軸は z）。半分は水の中"""
    for z in range(z0 - 2, z1 + 3):
        f.solid(cx, cy, z, block("stripped_dark_oak_log", axis="z"))
    for (dx, dy) in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        for z in (z0, z1):
            f.solid(cx + dx, cy + dy, z, block("spruce_planks"))
    for k in range(64):
        a = k / 64 * math.tau
        x = int(round(cx + math.cos(a) * r))
        y = int(round(cy + math.sin(a) * r))
        if y <= CANAL_BOTTOM:
            continue
        for z in (z0, z1):
            f.solid(x, y, z, block("stripped_spruce_log", axis="z"))
    for k in range(8):
        a = k / 8 * math.tau
        for t in np.arange(1.5, r + 0.1, 0.5):
            x = int(round(cx + math.cos(a) * t))
            y = int(round(cy + math.sin(a) * t))
            if y <= CANAL_BOTTOM:
                continue
            for z in (z0, z1):
                f.solid(x, y, z, block("spruce_planks"))
        # 水かき（軸の向きに長い板）
        x = int(round(cx + math.cos(a) * r))
        y = int(round(cy + math.sin(a) * r))
        if y > CANAL_BOTTOM:
            for z in range(z0, z1 + 1):
                f.solid(x, y, z, block("spruce_planks"))


# ---------------------------------------------------------------- 沈んだ区画（下の層。水門①で水が引く）

CHANNEL_A = (162, 160, 318, 165)      # 東西の深い水路（x0, z0, x1, z1）
CHANNEL_B = (236, 114, 241, 200)      # 南北の深い水路
POOL = (196, 132, 9)                  # 主ナマズの溜まり（x, z, 半径）


def sunken(f):
    x0, z0, x1, z1 = SUNKEN
    f.carve(x0, LOW + 1, z0, x1, LOW + 30, z1)
    floor_plate(f, x0, z0, x1, z1, LOW, LOW_FLOOR, salt=41)
    # 広間から下りる坂（2マスで1段。水に沈むので、階段は水を含ませて置く）
    rx0, rx1 = RAMP
    for z in range(208, 234):
        top = LOW + (z - 208) // 2
        f.carve(rx0, top + 1, z, rx1, UP + 12, z)
        for x in range(rx0, rx1 + 1):
            for y in range(LOW, top + 1):
                f.solid(x, y, z, block(R.pick(LOW_FLOOR, x, y, z, 42)))
            if (z - 208) % 2 == 0 and top > LOW:
                f.solid(x, top, z, stair_state("deepslate_brick", "south", in_water=top < UP))
    for z in range(208, 234, 4):
        for x in (rx0 - 1, rx1 + 1):
            top = LOW + (z - 208) // 2
            f.solid(x, top + 4, z, block("sea_lantern"))
    # 深い水路（水は 11〜14。いつも水がある）と、橋
    for (ax0, az0, ax1, az1) in (CHANNEL_A, CHANNEL_B):
        f.water(ax0, LOW - 3, az0, ax1, LOW, az1)
        floor_plate(f, ax0, az0, ax1, az1, LOW - 4, LOWER_WALL, salt=43)
    for bx in (184, 212, 272, 300):
        f.fill(bx, LOW, CHANNEL_A[1], bx + 3, LOW, CHANNEL_A[3], block("polished_deepslate"))
    for bz in (128, 186):
        f.fill(CHANNEL_B[0], LOW, bz, CHANNEL_B[2], LOW, bz + 3, block("polished_deepslate"))
    # 主ナマズの溜まり（深い。水が引いても干上がらない）
    px, pz, pr = POOL
    for (x, z) in circle_cells(px, pz, pr):
        f.water(x, LOW - 8, z, x, LOW, z)
        f.solid(x, LOW - 9, z, block("deepslate_tiles"))
    for (x, z) in circle_cells(px, pz, pr + 1.6):
        if math.hypot(x - px, z - pz) > pr + 0.5:
            f.solid(x, LOW, z, block("chiseled_deepslate"))
    f.guardians.append({"id": "lord", "enemy": "amemaru:kiba_namazu", "variant": "amemaru:kiba_namazu_lord",
                        "pos": [px + 0.5, LOW - 6, pz + 0.5], "respawn_seconds": 300, "activation": 32, "pillar": -1})
    chest(f, px, LOW + 1, pz + pr + 3, "north", guardian="lord")
    # 沈んだ町並み（古代の水路の管理の町。屋根は水の下）。区画を 20×18 の敷地に分け、家・崩れた家・像の広場を置く
    keep_out = [(CHANNEL_A[0] - 2, CHANNEL_A[1] - 2, CHANNEL_A[2] + 2, CHANNEL_A[3] + 2),
                (CHANNEL_B[0] - 2, CHANNEL_B[1] - 2, CHANNEL_B[2] + 2, CHANNEL_B[3] + 2),
                (px - pr - 3, pz - pr - 3, px + pr + 3, pz + pr + 3),
                (RAMP[0] - 12, 196, RAMP[1] + 6, 233),            # 坂の下と中継の灯
                (x0, z0, x0 + 12, z0 + 8), (x1 - 12, z0, x1, z0 + 8),   # 水門②のハンドルの前
                (x0, 116, x0 + 8, 134),                           # 白い流れへの扉の前
                (x1 - 8, 124, x1, 140)]                           # ネズミの巣穴からの坂の下
    k = 0
    for lx in range(x0 + 4, x1 - 14, 20):
        for lz in range(z0 + 6, z1 - 12, 18):
            lot = (lx + 3, lz + 3, lx + 16, lz + 14)
            if any(overlaps(lot, b) for b in keep_out):
                continue
            kind = R.at(lx, lz, 44)
            if kind < 0.68:
                house(f, *lot, 1 + (k % 3 == 0), k)
            elif kind < 0.84:
                house(f, *lot, 1, k, ruined=True)
            else:
                statue_plaza(f, *lot)
            k += 1
    # 通りの灯（海晶の灯の柱。水の中でも消えない）
    for x in range(x0 + 4, x1 - 2, 20):
        for z in range(z0 + 4, z1 - 2, 18):
            if f.carved[x, LOW + 1, z] and f.v[x, LOW + 1, z] == 0 and not in_channel(x, z):
                lamp_post(f, x, z, LOW, tall=4)
    # 水門②のハンドル（北西と北東のくぼみ）
    for side, (vx, facing) in enumerate(((x0 + 5, "south"), (x1 - 5, "south"))):
        f.carve(vx - 3, LOW + 1, z0 - 4, vx + 3, LOW + 6, z0 - 1)
        floor_plate(f, vx - 3, z0 - 4, vx + 3, z0 - 1, LOW, LOW_FLOOR)
        valve(f, "deep", side, vx, LOW + 1, z0 - 2, facing)
        for y in range(LOW + 1, LOW + 7):
            f.solid(vx, y, z0 - 5, block("waxed_oxidized_copper"))
        f.solid(vx - 3, LOW + 4, z0 - 5, block("sea_lantern"))
        f.solid(vx + 3, LOW + 4, z0 - 5, block("sea_lantern"))
    # 中継の灯（坂の下の西）
    waypoint(f, "sunken", "沈んだ区画", RAMP[0] - 6, LOW + 1, 204, 180, look_at=(240, 170))
    chest(f, 310, LOW + 1, 206, "west")
    chest(f, 164, LOW + 1, 200, "east")
    # 白い流れへの道（西の壁。水門②の扉）と、ネズミの巣穴からの坂（東の壁）は white_channel()・warren() で掘る
    f.sections.append(("sunken", x0, z0 - 4, x1, 233, LOW, UP - 1))


def overlaps(a, b):
    return not (a[2] < b[0] or b[2] < a[0] or a[3] < b[1] or b[3] < a[1])


def in_channel(x, z):
    for (ax0, az0, ax1, az1) in (CHANNEL_A, CHANNEL_B):
        if ax0 - 1 <= x <= ax1 + 1 and az0 - 1 <= z <= az1 + 1:
            return True
    px, pz, pr = POOL
    return math.hypot(x - px, z - pz) < pr + 2


def statue_plaza(f, x0, z0, x1, z1):
    """像の広場（古代の水路の番人の像。石の台と、海晶の灯の目）"""
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            f.solid(x, LOW, z, block("polished_deepslate" if (x + z) % 2 else "deepslate_tiles"))
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            f.solid(x, LOW + 1, z, block("chiseled_stone_bricks"))
    for y in range(LOW + 2, LOW + 6):
        f.solid(cx, y, cz, block("polished_andesite"))
    f.solid(cx, LOW + 6, cz, block("chiseled_stone_bricks"))
    f.solid(cx, LOW + 7, cz, block("sea_lantern"))
    for (dx, dz) in ((-1, 0), (1, 0)):
        f.solid(cx + dx, LOW + 5, cz + dz, block("waxed_oxidized_copper"))


def house(f, x0, z0, x1, z1, stories, salt, ruined=False):
    """沈んだ町の家（石の壁・窓の穴・戸口。2階建ては床が1枚。屋根はところどころ崩れている。ruined は壁が崩れ、屋根がない）"""
    h = 5 * stories
    wall_table = [("stone_bricks", 4), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2), ("bricks", 1)]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(LOW + 1, LOW + h + 1):
                if edge and not (ruined and y > LOW + 1 + int(R.at(x, z, 55 + salt) * h)):
                    f.solid(x, y, z, block(R.pick(wall_table, x, y, z, 50 + salt)))
            if ruined:
                continue
            if stories == 2 and not edge and not (x == x0 + 2 and z == z0 + 2):
                f.solid(x, LOW + 5, z, block("spruce_planks"))
            if R.at(x, z, 60 + salt) > 0.18 or edge:
                f.solid(x, LOW + h + 1, z, block("deepslate_tiles") if not edge else block("polished_deepslate"))
    # 窓（四角い穴）と戸口
    for floor_y in range(stories):
        wy = LOW + 3 + 5 * floor_y
        for x in range(x0 + 2, x1 - 1, 3):
            f.carve(x, wy, z0, x, wy, z0)
            f.carve(x, wy, z1, x, wy, z1)
        for z in range(z0 + 2, z1 - 1, 3):
            f.carve(x0, wy, z, x0, wy, z)
            f.carve(x1, wy, z, x1, wy, z)
    dx = (x0 + x1) // 2
    f.carve(dx, LOW + 1, z1, dx + 1, LOW + 3, z1)
    f.solid((x0 + x1) // 2, LOW, (z0 + z1) // 2, block("sea_lantern"))
    if R.at(x0, z0, 70 + salt) < 0.5:
        f.deco(x0 + 1, LOW + 1, z0 + 1, block("barrel", facing="up", open=False))


# ---------------------------------------------------------------- 白い流れへの道（水門②の扉と、水の満ちた道）

def white_channel(f):
    z0, z1 = 122, 128
    x0, x1 = WHITE[2] + 1, SUNKEN[0] - 1          # 111〜155
    room(f, x0, z0, x1, z1, LOW, 7, LOW_FLOOR, salt=81)
    # 扉（青銅の水門の扉。水門②が開くと上がる）
    door = block("waxed_oxidized_cut_copper")
    doors = []
    for (dx0, dx1) in ((x1 - 1, x1), (x0, x0 + 1)):
        f.fill(dx0, LOW + 1, z0, dx1, LOW + 7, z1, door)
        doors.append({"box": [dx0, LOW + 1, z0, dx1, LOW + 7, z1], "block": "minecraft:waxed_oxidized_cut_copper"})
    f.doors["deep"] = doors
    # 扉のあいだは水が満ちている（水門②で引く）
    f.sections.append(("white_channel", x0 + 2, z0, x1 - 2, z1, LOW, LOW + 7))
    for x in range(x0 + 4, x1 - 2, 8):
        f.solid(x, LOW + 4, z0 - 1, block("sea_lantern"))
        f.solid(x, LOW + 4, z1 + 1, block("sea_lantern"))
    # 区画の側の扉のそば：看板
    sign(f, x1 + 2, LOW + 1, z0 - 1, 4, ["水門の扉", "", "沈んだ区画の奥の", "水門で上がる"])


# ---------------------------------------------------------------- 白い流れ（下の層。ロストの侵食）

STREAM = [(108, 104), (94, 98), (82, 106), (68, 98), (58, 84), (48, 72), (36, 66), (28, 64)]


def white_flow(f):
    x0, z0, x1, z1 = WHITE
    # ほら穴（ふちを少しでこぼこに）
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            e = min(x - x0, x1 - x, z - z0, z1 - z)
            if e < 3 and R.at(x, z, 90) < 0.4 - e * 0.12:
                continue
            top = LOW + 14 + int(R.at(x // 4, z // 4, 91) * 6)
            f.carve(x, LOW + 1, z, x, top, z)
            st = "calcite" if R.at(x, z, 92) < 0.025 else R.pick(LOW_FLOOR, x, z, 93)
            if R.at(x, z, 94) < 0.08:
                st = "moss_block"
            f.solid(x, LOW, z, block(st))
    # 東の口（白い流れへの道から入る所）は、かならず掘る
    f.carve(x1 - 6, LOW + 1, 120, x1, LOW + 7, 130)
    floor_plate(f, x1 - 6, 120, x1, 130, LOW, LOW_FLOOR)
    # 流れ（幅4。水は 13〜14。底 12）
    for (ax, az), (bx, bz) in zip(STREAM, STREAM[1:]):
        n = int(max(abs(bx - ax), abs(bz - az)))
        for i in range(n + 1):
            t = i / max(1, n)
            px, pz = ax + (bx - ax) * t, az + (bz - az) * t
            for dx in range(-2, 2):
                for dz in range(-2, 2):
                    x, z = int(round(px)) + dx, int(round(pz)) + dz
                    f.water(x, LOW - 1, z, x, LOW, z)
                    f.solid(x, LOW - 2, z, block("deepslate_tiles"))
    # 流れの出どころ（東の壁の古代の管）
    for y in range(LOW + 1, LOW + 4):
        f.solid(x1 + 1, y, 104, block("waxed_oxidized_copper"))
    # 白い空白（流れの先。壁・床・天井が白く抜けている）
    f.lost_zones.append(((x0 - 3, 40, x0 + 22, 88), (LOW - 3, LOW + 18), x0, "x-"))
    for x in range(x0, x0 + 6):
        for z in range(58, 72):
            for y in range(LOW - 2, LOW + 12):
                if R.at(x, y, z, 95) < 0.85 - (x - x0) * 0.12:
                    f.solid(x, y, z, block("amemaru:lost_blank"))
    # 半分消えかけた古代のアーチと柱
    for (ax, az) in ((52, 56), (60, 92), (78, 70), (44, 96)):
        for y in range(LOW + 1, LOW + 9):
            for dz in (-3, 3):
                st = "amemaru:lost_blank" if R.at(ax, y, az + dz, 96) < (0.5 if ax < 56 else 0.15) else R.pick(UPPER_WALL, ax, y, az, 97)
                f.solid(ax, y, az + dz, block(st))
        for dz in range(-3, 4):
            st = "amemaru:lost_blank" if R.at(ax, dz, 98) < (0.6 if ax < 56 else 0.2) else "chiseled_stone_bricks"
            f.solid(ax, LOW + 9, az + dz, block(st))
    for (x, z) in ((70, 50), (90, 60), (60, 112), (96, 80), (40, 104)):
        lamp_post(f, x, z, LOW, tall=3)
    for (x, z) in ((80, 40), (100, 110), (34, 110)):
        crystal(f, x, LOW + 1, z)
    chest(f, 36, LOW + 1, 104, "east")
    f.story_zones.append({"id": "white_flow", "box": [x0, LOW - 2, 44, x0 + 30, LOW + 16, 88]})
    sign(f, 104, LOW + 1, 118, 8, ["白い流れ", "", "水が、どこかへ", "消えていく"])
    # 回廊へ上る階段（北東。東へ上がり、回廊の西のはしへ）
    sz0, sz1 = 86, 92
    for x in range(x1 - 1, 150):
        top = min(UP, LOW + max(0, x - (x1 - 1)))
        f.carve(x, top + 1, sz0, x, top + 6, sz1)
        for z in range(sz0, sz1 + 1):
            f.solid(x, top, z, block(R.pick(FLOOR_STONE, x, z, 99)))
            if LOW < top < UP and top > LOW + max(0, x - 1 - (x1 - 1)):
                f.solid(x, top, z, stair_state("stone_brick", "east"))
    for x in range(x1 + 2, 150, 6):
        top = min(UP, LOW + max(0, x - (x1 - 1)))
        f.solid(x, top + 4, sz0 - 1, block("sea_lantern"))


# ---------------------------------------------------------------- ネズミの巣穴（上の層。ネズミの親分）

WARREN_TUNNELS = [
    [(420, 199), (420, 172), (382, 172), (382, 132), (440, 132), (440, 76), (430, 70)],
    [(400, 172), (400, 150)],                    # 行き止まり（巣）
    [(440, 110), (462, 110)],                    # 行き止まり（宝箱）
    [(406, 66), (360, 66), (360, 90), (331, 90)],   # 親分の部屋から、回廊の東のはしへ
    [(382, 132), (347, 132)],                    # 沈んだ区画への坂の上
]


def warren(f):
    for path in WARREN_TUNNELS:
        for (ax, az), (bx, bz) in zip(path, path[1:]):
            xa, xb = sorted((ax, bx))
            za, zb = sorted((az, bz))
            room(f, xa - 1, za - 1, xb + 1, zb + 1, UP, 4, salt=101)
    # 親分の部屋（丸い）
    cx, cz = WARREN_DEN
    for (x, z) in circle_cells(cx, cz, 14):
        f.carve(x, UP + 1, z, x, UP + 8 - int(math.hypot(x - cx, z - cz) / 5), z)
        f.solid(x, UP, z, block(R.pick(FLOOR_STONE, x, z, 102)))
    # かじりためた歯車の山（青銅のかけら）
    for (x, z, h) in ((cx + 6, cz - 6, 3), (cx - 7, cz + 5, 2), (cx + 2, cz + 9, 2), (cx - 9, cz - 4, 2)):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                hh = h - abs(dx) - abs(dz) // 2
                for y in range(UP + 1, UP + 1 + max(0, hh)):
                    f.solid(x + dx, y, z + dz, block(R.pick(BRONZE, x + dx, y, z + dz, 103)))
    f.guardians.append({"id": "rat_boss", "enemy": "amemaru:suimon_nezumi", "variant": "amemaru:suimon_nezumi_boss",
                        "pos": [cx + 0.5, UP + 1, cz + 0.5], "respawn_seconds": 300, "activation": 30, "pillar": -1})
    chest(f, cx, UP + 1, cz - 11, "south", guardian="rat_boss")
    chest(f, 461, UP + 1, 110, "west")
    for (x, z) in ((cx - 10, cz), (cx + 10, cz), (cx, cz + 11)):
        lamp_post(f, x, z, UP, tall=3)
    for path in WARREN_TUNNELS:
        for (x, z) in path[1:-1]:
            f.solid(x, UP + 5, z, block("sea_lantern"))
    # 沈んだ区画へ下りる坂（水門①で水が引く。坂の水は区画と同じ）
    for x in range(SUNKEN[2] + 1, 347):
        top = min(UP, LOW + int(round((x - SUNKEN[2]) * (UP - LOW) / (346 - SUNKEN[2]))))
        f.carve(x, top + 1, 131, x, top + 4, 133)
        for z in range(131, 134):
            for y in range(LOW, top + 1):
                f.solid(x, y, z, block(R.pick(FLOOR_STONE, x, y, z, 104)))
            nxt = min(UP, LOW + int(round((x - 1 - SUNKEN[2]) * (UP - LOW) / (346 - SUNKEN[2]))))
            if top > nxt:
                f.solid(x, top, z, stair_state("stone_brick", "east", in_water=top < UP))
    f.sections.append(("sunken_ne", SUNKEN[2] + 1, 131, 346, 133, LOW, UP - 1))
    sign(f, 349, UP + 1, 130, 4, ["沈んだ区画へ", "", "水が引いていれば", "下りられる"])


# ---------------------------------------------------------------- 大水門の前の回廊と堀（水門③）・封印

def gallery(f):
    x0, z0, x1, z1 = GALLERY
    mx0, mx1 = MOAT
    vault(f, x0, z0, mx0 - 1, z1, UP, 8, 12, axis="x")
    vault(f, mx1 + 1, z0, x1, z1, UP, 8, 12, axis="x")
    floor_plate(f, x0, z0, mx0 - 1, z1, UP)
    floor_plate(f, mx1 + 1, z0, x1, z1, UP)
    # 堀（下の層まで深い。水門③で水が引く）
    f.carve(mx0, LOW + 1, z0, mx1, UP + 12, z1)
    floor_plate(f, mx0, z0, mx1, z1, LOW, LOW_FLOOR, salt=121)
    # 堀へ下りる階段（西と東から。水に沈むので、階段は水を含ませて置く）
    sz0, sz1 = 86, 94
    for k in range(UP - LOW):
        top = UP - 1 - k
        for (x, facing) in ((mx0 + k, "west"), (mx1 - k, "east")):
            for z in range(sz0, sz1 + 1):
                for y in range(LOW + 1, top + 1):
                    f.solid(x, y, z, block(R.pick(LOW_FLOOR, x, y, z, 122)))
                f.solid(x, top, z, stair_state("deepslate_brick", facing, in_water=True))
    f.sections.append(("moat", mx0, z0, mx1, z1, LOW, UP - 1))
    # 封印（堀の北の壁）と、ボスの部屋への通り道
    seal = []
    for x in range(236, 245):
        for y in range(LOW + 1, LOW + 8):
            f.deco(x, y, SEAL_Z, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            seal.append((x, y, SEAL_Z))
    f.seal_cells = seal
    room(f, 236, ARENA[1] + ARENA_R, 244, SEAL_Z - 1, LOW, 7, LOW_FLOOR, salt=123)
    for y in range(LOW + 1, LOW + 9):
        for x in (235, 245):
            f.solid(x, y, SEAL_Z, block("waxed_oxidized_copper"))
    for x in range(235, 246):
        f.solid(x, LOW + 8, SEAL_Z, block("waxed_oxidized_cut_copper"))
    # 水門③のハンドル（回廊の西と東の北の壁のくぼみ）
    for side, vx in enumerate((x0 + 11, x1 - 11)):
        f.carve(vx - 2, UP + 1, z0 - 3, vx + 2, UP + 5, z0 - 1)
        floor_plate(f, vx - 2, z0 - 3, vx + 2, z0 - 1, UP)
        valve(f, "moat", side, vx, UP + 1, z0 - 2, "south")
        for y in range(UP + 1, UP + 6):
            f.solid(vx, y, z0 - 4, block("waxed_oxidized_copper"))
    # 回廊の灯と、大水門の前の飾り（青銅の番人の像の台）
    for x in list(range(x0 + 6, mx0 - 2, 12)) + list(range(mx1 + 6, x1 - 2, 12)):
        wall_lamp(f, x, UP + 5, z0 - 1)
        wall_lamp(f, x, UP + 5, z1 + 1)
        lamp_post(f, x, z1 - 3, UP)
    for x in range(mx0 + 4, mx1 - 3, 8):
        hang_lamp(f, x, UP + 11, (z0 + z1) // 2, 6)
    for (x, z) in ((mx0 - 4, z0 + 2), (mx1 + 4, z0 + 2)):
        for y in range(UP + 1, UP + 4):
            f.solid(x, y, z, block("polished_deepslate"))
        f.solid(x, UP + 4, z, block("waxed_oxidized_copper"))
        f.solid(x, UP + 5, z, block("sea_lantern"))
    waypoint(f, "great_gate", "大水門の前", x0 + 4, UP + 1, z1 - 4, -90, look_at=(240, 90))
    sign(f, x0 + 6, UP + 1, z1 - 6, 4, ["大水門の前", "", "堀の水を引くと", "封印へ下りられる"])
    chest(f, x1 - 3, UP + 1, z1 - 3, "west")


# ---------------------------------------------------------------- ボスの部屋（大水門。浅い水に沈む。排水のハンドル）

def arena(f):
    cx, cz = ARENA
    for (x, z) in circle_cells(cx, cz, ARENA_R):
        d = math.hypot(x - cx, z - cz)
        top = LOW + 10 + int(round(12 * math.sqrt(max(0.0, 1.0 - (d / ARENA_R) ** 2))))
        f.carve(x, LOW + 1, z, x, top, z)
        ring = int(d) % 7
        st = "polished_deepslate" if ring == 0 else ("deepslate_tiles" if R.at(x, z, 131) > 0.15 else "cracked_deepslate_tiles")
        if abs(d - 12) < 0.6 or abs(d - 20) < 0.6:
            st = "prismarine_bricks"
        f.solid(x, LOW, z, block(st))
    for (x, z) in circle_cells(cx, cz, 3):
        f.solid(x, LOW, z, block("dark_prismarine"))
    # 大水門（北のくぼみ）：青銅の大きな扉と、その両わきの水のすだれ（ガラスの奥の水。流れない）
    gz = cz - ARENA_R
    f.carve(cx - 13, LOW + 1, gz - 3, cx + 13, LOW + 20, gz + 4)
    floor_plate(f, cx - 13, gz - 3, cx + 13, gz + 4, LOW, LOW_FLOOR, salt=132)
    back = gz - 4
    for x in range(cx - 8, cx + 9):
        for y in range(LOW + 1, LOW + 21):
            st = "waxed_oxidized_copper" if (x - cx) % 4 else "waxed_weathered_copper"
            if y in (LOW + 6, LOW + 12, LOW + 18):
                st = "waxed_oxidized_cut_copper"
            if x == cx:
                st = "polished_deepslate"
            f.solid(x, y, back, block(st))
    for wx in (cx - 11, cx + 11):
        for y in range(LOW + 2, LOW + 18):
            f.put(wx, y, back, WATER)
            f.carved[wx, y, back] = True
            f.solid(wx, y, back + 1, block("light_blue_stained_glass"))
        f.solid(wx, LOW + 1, back, block("prismarine_bricks"))
        f.solid(wx, LOW + 1, back + 1, block("prismarine_bricks"))
        f.solid(wx, LOW + 18, back + 1, block("sea_lantern"))
    # 排水のハンドル（東と西の台の上）
    for side, (vx, facing) in enumerate(((cx - ARENA_R + 4, "east"), (cx + ARENA_R - 4, "west"))):
        for x in range(vx - 1, vx + 2):
            for z in range(cz - 1, cz + 2):
                f.solid(x, LOW + 1, z, block("polished_deepslate"))
        valve(f, "drain", side, vx, LOW + 2, cz, facing)
        f.solid(vx + (-1 if facing == "east" else 1) * 2, LOW + 1, cz, block("sea_lantern"))
    # 灯（床に埋めた海晶の灯の輪・天井からさがる魂の灯）
    for k in range(12):
        a = k / 12 * math.tau
        x, z = int(round(cx + math.cos(a) * (ARENA_R - 2))), int(round(cz + math.sin(a) * (ARENA_R - 2)))
        f.solid(x, LOW, z, block("sea_lantern"))
    for k in range(6):
        a = k / 6 * math.tau + 0.5
        hang_lamp(f, int(round(cx + math.cos(a) * 15)), LOW + 19, int(round(cz + math.sin(a) * 15)), 4)
    # 戻りの円（北）と、その東どなりの次の階への円（金の縁。11月24日）
    return_circle(f, cx, LOW, cz - 20)
    next_circle(f.solid, cx + NEXT_DX, LOW, cz - 20)
    # 部屋の水（1マス。排水で引く）
    f.sections.append(("arena", cx - ARENA_R, gz - 3, cx + ARENA_R, SEAL_Z - 1, LOW, LOW + 1))


# ================================================================ 仕上げ

def flood_sections(f):
    """水門の水の区画を、水の満ちた形にする（掘った所の空気を水に。水に浸かると形の変わるブロックは水を含ませる）"""
    water = f.idx(WATER)
    for (sid, x0, z0, x1, z1, low, high) in f.sections:
        sl = (slice(x0, x1 + 1), slice(low + 1, high + 1), slice(z0, z1 + 1))
        air = f.carved[sl] & (f.v[sl] == 0)
        f.v[sl][air] = water
        sub = f.v[sl]
        for i in np.unique(sub):
            st = f.states[i] if i else None
            if st is None or not any(st[0].endswith(t) for t in WATERLOGGABLE):
                continue
            props = dict(st[1])
            if props.get("waterlogged") == "false":
                props["waterlogged"] = "true"
                wet = f.idx(block(st[0], **props))
                sub[sub == i] = wet
        f.v[sl] = sub
        f.water_sections.append({"id": sid, "box": [x0, low, z0, x1, high, z1], "low": low, "high": high})


def shell(f):
    """掘った所のまわり2マスを壁にする（上の層は石のれんが、下は深層岩のれんが）"""
    near = ndimage.binary_dilation(f.carved, structure=np.ones((3, 3, 3), dtype=bool), iterations=2)
    walls = near & ~f.carved & (f.v == 0)
    xs, ys, zs = np.nonzero(walls)
    upper = [f.idx(block(n)) for n, w in UPPER_WALL for _ in range(w)]
    lower = [f.idx(block(n)) for n, w in LOWER_WALL for _ in range(w)]
    h = ((xs * 73856093) ^ (ys * 19349663) ^ (zs * 83492791)) & 0xFFFF
    up_pick = np.array(upper)[h % len(upper)]
    low_pick = np.array(lower)[h % len(lower)]
    f.v[xs, ys, zs] = np.where(ys >= UP - 1, up_pick, low_pick)


def lost(f):
    """ロストの侵食：白い空白に近いほど、壁・床・天井のブロックが白く抜けている"""
    blank = f.idx(block("amemaru:lost_blank"))
    water = f.idx(WATER)
    for (x0, z0, x1, z1), (y0, y1), face, direction in f.lost_zones:
        for x in range(max(0, x0), min(SIZE, x1 + 1)):
            for z in range(max(0, z0), min(SIZE, z1 + 1)):
                for y in range(max(0, y0), min(HEIGHT, y1 + 1)):
                    i = f.v[x, y, z]
                    if i == 0 or i == water or f.carved[x, y, z]:
                        continue
                    dist = abs(x - face)
                    chance = 0.9 - dist * 0.07
                    if R.at(x, y, z, 141) < chance:
                        f.v[x, y, z] = blank


def wall_lights(f):
    """暗すぎる所に、壁の灯を足す（掘った空気の床から3マス上の壁に、海晶の灯を14マスおきに）"""
    lantern_i = f.idx(block("sea_lantern"))
    for x in range(2, SIZE - 2, 14):
        for z in range(2, SIZE - 2, 14):
            for y in (UP + 4, LOW + 4):
                if not f.carved[x, y, z]:
                    continue
                for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    for k in range(1, 12):
                        wx, wz = x + dx * k, z + dz * k
                        if not (0 <= wx < SIZE and 0 <= wz < SIZE):
                            break
                        if not f.carved[wx, y, wz]:
                            if f.v[wx, y, wz] != 0 and f.states[f.v[wx, y, wz]][0] in [n for n, _ in UPPER_WALL + LOWER_WALL]:
                                f.v[wx, y, wz] = lantern_i
                            break


def ambient_lights(f):
    """見えない灯（minecraft:light）を、掘った所に6マスおきに置く（11月24日：CI の写真で、3階がほとんど真っ暗だった）。

    地下の水路は空の光が届かないので、見える灯（海晶の灯・魂のランタン）だけでは、灯と灯の間が真っ暗になる。
    上の層は床から3マス上（立つ高さ＋2）、下の層も同じ。水の中（水門の区画が満ちているとき）は水を含ませる（抜けると外れる）。
    ボスの部屋と広間は明るく（15）、ほかは 12。灯どうしの間でも明るさ6くらいになる。"""
    water = f.idx(WATER)
    for base in (UP, LOW):
        y = base + 3
        for x in range(3, SIZE - 3, 6):
            for z in range(3, SIZE - 3, 6):
                if not f.carved[x, y, z]:
                    continue
                here = f.v[x, y, z]
                if here not in (0, water):
                    continue
                bright = (math.hypot(x - ARENA[0], z - ARENA[1]) <= ARENA_R + 2
                          or math.hypot(x - HUB[0], z - HUB[1]) <= HUB_R + 2)
                level = 15 if bright else 12
                f.v[x, y, z] = f.idx(block("light", level=level, waterlogged="true" if here == water else "false"))


# ================================================================ 階の定義（floors/floor3.json）

def spawn_zones(f):
    def zone(id_, enemy, cx, y, cz, radius, max_, respawn, group, activation=48, route=None, in_water=False):
        z = {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
             "respawn_seconds": respawn, "group": list(group), "activation": activation}
        if route:
            z["route"] = [[x, y, zz] for (x, zz) in route]
        if in_water:
            z["in_water"] = True
        return z
    u = UP + 1
    lo = LOW + 1
    return [
        # 大水路：ネズミの群れ・澱みスライム・巡回する掃除機兵
        zone("canal_rats_south", "suimon_nezumi", 226, u, 400, 8, 6, 60, (4, 6)),
        zone("canal_rats_north", "suimon_nezumi", 254, u, 322, 8, 6, 60, (4, 6)),
        zone("canal_slimes", "yodomi_slime", 226, u, 352, 8, 3, 50, (1, 2)),
        zone("canal_cleaner", "soujikihei", 254, u, 380, 6, 2, 55, (1, 1), route=[(254, 420), (254, 380), (254, 340), (254, 312)]),
        zone("canal_cleaner_w", "soujikihei", 225, u, 330, 6, 1, 55, (1, 1), route=[(225, 312), (225, 350), (225, 390)]),
        # 西の貯水槽：細い通路の上の澱みスライム・ネズミ
        zone("cistern_slimes_a", "yodomi_slime", 75, u, 220, 10, 3, 50, (1, 2), 44),
        zone("cistern_slimes_b", "yodomi_slime", 105, u, 300, 10, 3, 50, (1, 2), 44),
        zone("cistern_slimes_c", "yodomi_slime", 45, u, 265, 10, 3, 50, (1, 2), 44),
        zone("cistern_rats", "suimon_nezumi", 120, u, 265, 6, 5, 60, (3, 5), 44),
        zone("cistern_cleaner", "soujikihei", 31, u, 240, 4, 1, 60, (1, 1), 44, route=[(31, 206), (31, 240), (31, 300), (31, 320)]),
        # 東の水車の間：巡回する掃除機兵・ネズミ・スライム
        zone("east_cleaner_n", "soujikihei", 380, u, 232, 6, 2, 55, (1, 1), route=[(350, 236), (380, 236), (412, 236), (440, 240)]),
        zone("east_cleaner_s", "soujikihei", 400, u, 290, 6, 2, 55, (1, 1), route=[(440, 286), (400, 290), (366, 286), (344, 290)]),
        zone("east_cleaner_m", "soujikihei", 420, u, 210, 6, 1, 55, (1, 1), route=[(400, 210), (440, 212)]),
        zone("east_rats", "suimon_nezumi", 430, u, 214, 8, 6, 60, (4, 6)),
        zone("east_slimes", "yodomi_slime", 360, u, 306, 8, 3, 50, (1, 2)),
        # ネズミの巣穴：群れ
        zone("warren_a", "suimon_nezumi", 420, u, 180, 4, 6, 50, (4, 6), 36),
        zone("warren_b", "suimon_nezumi", 382, u, 150, 4, 6, 50, (4, 6), 36),
        zone("warren_c", "suimon_nezumi", 440, u, 105, 4, 6, 50, (4, 6), 36),
        zone("warren_d", "suimon_nezumi", 372, u, 70, 4, 5, 50, (3, 5), 36),
        # 沈んだ区画（水が引いてから）：深い水路の牙ナマズ・通りのスライム・掃除機兵
        zone("sunken_namazu_a", "kiba_namazu", 190, LOW - 1, 162, 5, 2, 60, (1, 1), 36, in_water=True),
        zone("sunken_namazu_b", "kiba_namazu", 290, LOW - 1, 162, 5, 2, 60, (1, 1), 36, in_water=True),
        zone("sunken_namazu_c", "kiba_namazu", 238, LOW - 1, 130, 3, 1, 60, (1, 1), 36, in_water=True),
        zone("sunken_namazu_d", "kiba_namazu", 238, LOW - 1, 190, 3, 1, 60, (1, 1), 36, in_water=True),
        zone("sunken_slimes_a", "yodomi_slime", 216, lo, 146, 8, 3, 50, (1, 2), 40),
        zone("sunken_slimes_b", "yodomi_slime", 290, lo, 122, 8, 3, 50, (1, 2), 40),
        zone("sunken_cleaner_a", "soujikihei", 204, lo, 176, 6, 1, 55, (1, 1), 40, route=[(176, 176), (204, 176), (228, 176)]),
        zone("sunken_cleaner_b", "soujikihei", 276, lo, 198, 6, 1, 55, (1, 1), 40, route=[(256, 198), (276, 198), (300, 198)]),
        zone("sunken_rats", "suimon_nezumi", 304, lo, 146, 6, 5, 60, (3, 5), 40),
        # 白い流れ：スライム・流れの牙ナマズ
        zone("white_slimes_a", "yodomi_slime", 74, lo, 80, 10, 3, 50, (1, 2), 44),
        zone("white_slimes_b", "yodomi_slime", 50, lo, 106, 8, 2, 50, (1, 2), 44),
        zone("white_namazu", "kiba_namazu", 84, LOW - 1, 104, 4, 1, 60, (1, 1), 36, in_water=True),
        # 大水門の前の回廊
        zone("gallery_cleaner_w", "soujikihei", 190, u, 96, 6, 1, 55, (1, 1), 40, route=[(170, 96), (195, 96), (214, 96)]),
        zone("gallery_cleaner_e", "soujikihei", 290, u, 96, 6, 1, 55, (1, 1), 40, route=[(266, 96), (290, 96), (320, 96)]),
        zone("gallery_rats", "suimon_nezumi", 300, u, 82, 6, 5, 60, (3, 5), 40),
    ]


def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    gates = [
        {"id": "first", "name": "西と東の水門", "valves": f.valves["first"], "drains": ["sunken", "sunken_ne"]},
        {"id": "deep", "name": "沈んだ区画の奥の水門", "valves": f.valves["deep"], "drains": ["white_channel"], "after": "first",
         "doors": f.doors["deep"]},
        {"id": "moat", "name": "大水門の前の水門", "valves": f.valves["moat"], "drains": ["moat"], "after": "deep"},
        {"id": "drain", "name": "排水口", "valves": f.valves["drain"], "drains": ["arena"]},
    ]
    ex, ez = ENTRY
    data = {
        "_comment": "塔の3階「古代の水路」（tools/mapgen/build_floor3.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 1,
        "name": "古代の水路",
        "chapter_title": "第1章　失われた古代領域",
        "recommended_level": [5, 8],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor3/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, UP + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, UP + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:hydram",
            "name": "大水門",
            "spawn": {"pos": [cx + 0.5, LOW + 1, cz + 0.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, LOW + 1, cz + 0.5], "radius": ARENA_R - 1.0, "y_min": LOW - 2, "y_max": LOW + 24},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 22,
            "respawn_seconds": 120,
            "max_players": 8,
            # 11月24日「ソロでは全然倒せない」：ひとりでも2人ぶんのHP【補完】
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1500,
            "exit": {"center": [cx + 0.5, LOW + 1, cz - 19.5], "radius": 2.6},
            "next": {"center": [cx + NEXT_DX + 0.5, LOW + 1, cz - 19.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "",
        "tablet": {"pos": list(f.tablet), "title": "水路管理局の掲示",
                   "text": "水路管理局より告知。この水路は、魔力の循環路を兼ねる。流れが止まれば、街は乾く。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 11月24日：暗すぎたので、霧を明るい青緑に・遠くに（水の上の湿った霧）
        "environment": {"fog": [18, 110], "fog_color": "#41707C"},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        # 3組の水門をすべて開き（みんなの分）、名のある魔物を3体とも倒す（人ごと）と、封印が開く【補完】
        "seal_requires": "floodgates+guardians",
        "water_sections": f.water_sections,
        "floodgates": gates,
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
    entrance(f)
    main_canal(f)
    hub(f)
    cistern(f)
    east_hall(f)
    sunken(f)
    white_channel(f)
    white_flow(f)
    warren(f)
    gallery(f)
    arena(f)
    flood_sections(f)
    shell(f)
    lost(f)
    wall_lights(f)
    ambient_lights(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor3") if out else os.path.join(RES, "structures", "map", "floor3")
    json_path = os.path.join(out, "floor3.json") if out else os.path.join(RES, "amemaru", "floors", "floor3.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "水門:", {k: v for k, v in f.valves.items()})
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor3_figures
        floor3_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
