"""塔の4階「崩壊した神殿」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor4.json）を書き出す。

Phase 0-A 9章の4階（確定）：崩れた柱と浮かぶ瓦礫の神殿。手をつないで大きな結晶を囲む古代人の壁画。顔の削れた石像。
雑魚は沈黙の石像（近づくと動き出す）・祈り手の残響（祈りの歌で仲間を回復させるので先に倒す）・結晶甲虫（硬いが魔法に弱い）・
欠けた聖獣（神殿の守護獣がロストで欠けたもの）。ボスは祈りの残響カンターレ（4体の歌い手で、歌の順番どおりに4つの音の床を
踏むと無防備になる）。ギミックは壁画パズル（回転するパネルを正しく並べると、第一時代から第二時代までの歴史が一枚の絵になる。
表：壁画の復元・音の順番）。古代記録「神殿の碑文」。物語は4日目：結晶騎士団の遺品（冑と認識票）。
ロストの侵食：壁画の人物が何人か白く消されている（誰かが、誰かを忘れた跡）。

使い方:
  python3 tools/mapgen/build_floor4.py                        # Modの structures/map/floor4/ と floors/floor4.json を書き直す
  python3 tools/mapgen/build_floor4.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

広さは 528×528（1階の約1.9倍。上の段と地下の層を足して、歩ける広さは1階の約2倍。11月23日の決まり）、高さの幅 72。空の見える神殿。
座標はすべて階の (0, 0, 0) から。歩ける所は3つの層：地面（床のブロックの高さ 24。立つのは 25）・地下の納骨堂（床 10）・
聖獣の庭の高い段（床 36）。

決まり（11月24日【補完】。docs/phase8_temple.md）：
  - 区画は9つと、ボスの部屋：入口の石段・崩れた参道（石像の並木）・前庭（中継の灯1）・壁画の回廊・祈りの間・浮石の庭・
    聖獣の庭（高い段）・地下の納骨堂（結晶騎士団の遺品）・崩れた鐘楼（白い壁画の間。ロストの侵食）。北に歌の大広間（ボス）
  - 壁画は3つ（回廊の「第一時代」3枚・祈りの間の奥の「大断絶」4枚・聖獣の庭の祠の「第二時代」5枚）。3つが戻ると、
    大広間の前室の「一枚の絵」（12枚）が灯り、封印が開く（あわせて、名のある魔物3体を倒した人だけ）
  - 祈りの間の「音の順番」：鈴の石を鳴らすと、4つの音の床が3つ光る。同じ順番で踏むと、奥の院の扉が開く（ボスの練習）
  - 外まわりは高い塀（落ちて外へ出ない）。浮石の庭は深い谷で、落ちると谷の底（床 4）から長い坂で前庭へ戻れる
  - 中継の灯は5つ（前庭・浮石の庭・聖獣の庭・納骨堂・祈りの間）
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

FLOOR = 4
SIZE = 528
HEIGHT = 72
PIECE = 48
ORIGIN = (16384 - SIZE // 2, 60, -SIZE // 2)
GROUND = 24             # 地面の床のブロック（立つのは 25）
LOW = 10                # 地下の納骨堂の床
HIGH = 36               # 聖獣の庭の床
CHASM = 4               # 浮石の庭の谷の底
CHEST_LOOT = "amemaru:chests/floor4_ancient"

ENTRY = (264, 500)                       # 戻りの円
AVENUE = (255, 296, 273, 478)            # 参道（x0, z0, x1, z1）
PLAZA = (264, 250)                       # 前庭のまん中
PLAZA_R = 44
GALLERY = (308, 232, 472, 268)           # 壁画の回廊
CHAPEL = (366, 96, 476, 200)             # 祈りの間（奥の院は北のはし）
FLOAT = (30, 226, 196, 404)              # 浮石の庭の谷
TERRACE = (36, 26, 214, 210)             # 聖獣の庭（高い段）
CRYPT = (204, 300, 326, 428)             # 地下の納骨堂
TOWER = (360, 330, 470, 440)             # 崩れた鐘楼
ARENA = (264, 74)                        # 歌の大広間のまん中
ARENA_R = 30
ANTE = (240, 106, 288, 146)              # 前室
SEAL_Z = 104                             # 封印の面


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


R = Rand(20261124 + 4)

TEMPLE_FLOOR = [("polished_diorite", 4), ("calcite", 3), ("smooth_stone", 2), ("cracked_stone_bricks", 1), ("diorite", 1)]
TEMPLE_WALL = [("calcite", 4), ("polished_diorite", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 2), ("mossy_stone_bricks", 1)]
WARM_FLOOR = [("smooth_sandstone", 3), ("cut_sandstone", 2), ("calcite", 2), ("smooth_stone", 1)]
GRASS = [("grass_block", 12), ("moss_block", 2), ("coarse_dirt", 1)]
PATH = [("dirt_path", 5), ("gravel", 1), ("cobblestone", 1), ("mossy_cobblestone", 1)]
GROUND_NAMES = {"grass_block", "moss_block", "coarse_dirt", "rooted_dirt"}
RUBBLE = [("cobblestone", 3), ("andesite", 2), ("calcite", 2), ("mossy_cobblestone", 2), ("gravel", 1)]
CRYPT_WALL = [("deepslate_bricks", 4), ("cracked_deepslate_bricks", 2), ("polished_deepslate", 2), ("deepslate_tiles", 1)]
CRYPT_FLOOR = [("deepslate_tiles", 4), ("polished_deepslate", 2), ("cracked_deepslate_tiles", 1)]
AIR = None


def thing(index=0, group=""):
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


def wall_state(name, **sides):
    props = {"north": "none", "south": "none", "east": "none", "west": "none", "up": True, "waterlogged": False}
    props.update(sides)
    return block(name, **props)


# ================================================================ 立体

class Floor:
    def __init__(self):
        self.v = np.zeros((SIZE, HEIGHT, SIZE), dtype=np.uint16)
        # わざと空けた所（掘った通路・階段など）。地下の囲いの岩は、ここを埋めない
        self.carved = np.zeros((SIZE, HEIGHT, SIZE), dtype=bool)
        self.states = [None]
        self.index = {}
        self.nbt = {}
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.murals = []
        self.songs = []
        self.relics = []
        self.plates = []
        self.seal_cells = []
        self.story_zones = []
        self.lost_zones = []
        self.travel_stone = None
        self.tablet = None
        self.signs = []
        self.doors = {}

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

    def box(self, x0, y0, z0, x1, y1, z1):
        x0, x1 = max(0, min(x0, x1)), min(SIZE - 1, max(x0, x1))
        y0, y1 = max(0, min(y0, y1)), min(HEIGHT - 1, max(y0, y1))
        z0, z1 = max(0, min(z0, z1)), min(SIZE - 1, max(z0, z1))
        return slice(x0, x1 + 1), slice(y0, y1 + 1), slice(z0, z1 + 1)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        b = self.box(x0, y0, z0, x1, y1, z1)
        self.v[b] = 0 if state is None else self.idx(state)
        self.carved[b] = state is None

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)


def mix(f, table, salt, x0, y0, z0, x1, y1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                f.put(x, y, z, block(R.pick(table, x, y, z, salt)))


def circle_cells(cx, cz, r):
    out = []
    ri = int(math.ceil(r)) + 1
    for x in range(cx - ri, cx + ri + 1):
        for z in range(cz - ri, cz + ri + 1):
            if math.hypot(x - cx, z - cz) <= r:
                out.append((x, z))
    return out


def stairs(mat, facing, half="bottom"):
    return block(mat + "_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(mat, kind="bottom"):
    return block(mat + "_slab", type=kind, waterlogged=False)


def sign(f, x, y, z, rotation, lines):
    f.put(x, y, z, block("birch_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))
    f.signs.append((x, y, z))


def lantern_post(f, x, z, y, tall=3):
    for k in range(1, tall + 1):
        f.put(x, y + k, z, wall_state("diorite_wall"))
    f.put(x, y + tall + 1, z, block("lantern", hanging=False, waterlogged=False))


def chest(f, x, y, z, facing, guardian=""):
    index = len(f.chests)
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing), thing(index, ""))
    f.chests.append(((x, y, z), guardian))
    return index


def waypoint(f, wid, name, x, y, z, facing_yaw, look_at=None):
    index = len(f.waypoints)
    f.put(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    yaw = facing_yaw
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    if look_at is not None:
        yaw = round(math.degrees(math.atan2(-(look_at[0] - (ax + 0.5)), look_at[1] - (az + 0.5))), 1)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


PLUS = ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1))


def column(f, x, z, y0, height, broken=False, salt=0, thick=True):
    """神殿の柱（3×3の台・十字の太い柱・3×3の柱頭）。broken なら途中で折れて、そばに倒れたかけら。thick でなければ1本の細い柱"""
    top = height
    if broken:
        top = max(2, int(height * (0.3 + 0.5 * R.at(x, z, salt, 1))))
    shaft = PLUS if thick else ((0, 0),)
    if thick:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                f.put(x + dx, y0 + 1, z + dz, block("chiseled_quartz_block" if dx == dz == 0 else "smooth_quartz"))
    else:
        f.put(x, y0 + 1, z, block("chiseled_quartz_block"))
    for y in range(y0 + 2, y0 + top):
        for (dx, dz) in shaft:
            # 折れた柱は、上のほうがまばらに欠ける
            if broken and (dx or dz) and y > y0 + top - 2 and R.at(x + dx, y, z + dz, salt, 3) < 0.5:
                continue
            f.put(x + dx, y, z + dz, block("quartz_pillar", axis="y"))
    if not broken:
        for dx in ((-1, 0, 1) if thick else (0,)):
            for dz in ((-1, 0, 1) if thick else (0,)):
                f.put(x + dx, y0 + top, z + dz, block("chiseled_quartz_block" if dx == dz == 0 else "smooth_quartz_slab",
                                                      **({} if dx == dz == 0 else {"type": "top", "waterlogged": False})))
    else:
        f.put(x, y0 + top, z, block("cracked_stone_bricks"))
        # 倒れたかけら（柱の向きに寝かせる）
        dx, dz = ((1, 0), (0, 1), (-1, 0), (0, -1))[int(R.at(x, z, salt, 2) * 4) % 4]
        for k in range(2, 5 if thick else 3):
            cx, cz = x + dx * k, z + dz * k
            if 0 <= cx < SIZE and 0 <= cz < SIZE and f.v[cx, y0 + 1, cz] == 0 and f.v[cx, y0, cz] != 0:
                f.put(cx, y0 + 1, cz, block("quartz_pillar", axis="x" if dx else "z"))


def faceless_statue(f, x, z, y0, facing="south"):
    """顔の削れた石像（台・体・削れた頭）。飾り（敵の沈黙の石像は別に湧く）"""
    f.put(x, y0 + 1, z, block("polished_andesite"))
    f.put(x, y0 + 2, z, block("calcite"))
    f.put(x, y0 + 3, z, block("calcite"))
    f.put(x, y0 + 4, z, block("smooth_quartz"))
    off = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[facing]
    # 削れた顔（前の面に、ひびの入った石）
    f.put(x + off[0], y0 + 4, z + off[1], block("andesite_button", face="wall", facing=facing, powered=False))
    for side in ((1, 0), (-1, 0)) if off[0] == 0 else ((0, 1), (0, -1)):
        f.put(x + side[0], y0 + 3, z + side[1], block("calcite_wall" if False else "diorite_wall", north="none", south="none",
                                                        east="none", west="none", up=True, waterlogged=False))


# ================================================================ 地面と塀

def terrain(f):
    """神殿の台地：地面の上の数段（土と石）。外まわりの高い塀"""
    # 台地（地面の下4段まで）
    for x in range(0, SIZE):
        for z in range(0, SIZE):
            f.put(x, GROUND, z, block(R.pick(GRASS, x, z, 1)))
    f.fill(0, GROUND - 4, 0, SIZE - 1, GROUND - 1, SIZE - 1, block("stone"))
    f.fill(0, GROUND - 1, 0, SIZE - 1, GROUND - 1, SIZE - 1, block("dirt"))
    # 外まわりの塀（崩れた神殿の囲い。高さ 12。落ちて外へ出ない）
    for x in range(0, SIZE):
        for z in range(0, SIZE):
            edge = min(x, z, SIZE - 1 - x, SIZE - 1 - z)
            if edge <= 2:
                top = GROUND + 12 - (1 if R.at(x, z, 3) < 0.2 else 0)
                for y in range(CHASM, top + 1):
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 4)))
                if edge == 2 and (x + z) % 9 == 0:
                    f.put(x, top + 1, z, block("chiseled_stone_bricks"))


def paved(f, x0, z0, x1, z1, y=GROUND, table=TEMPLE_FLOOR, salt=11, ruin=0.04):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if R.at(x, z, salt, 7) < ruin:
                f.put(x, y, z, block(R.pick(GRASS, x, z, 2)))
            else:
                f.put(x, y, z, block(R.pick(table, x, z, salt)))


def scatter_grass(f, x0, z0, x1, z1, y=GROUND, density=0.12, salt=21):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if f.v[x, y + 1, z] != 0:
                continue
            ground = f.name_at(x, y, z)
            if ground not in ("grass_block", "moss_block"):
                continue
            r = R.at(x, z, salt)
            if r < density * 0.6:
                f.put(x, y + 1, z, block("grass"))
            elif r < density * 0.8:
                f.put(x, y + 1, z, block("fern"))
            elif r < density * 0.86:
                f.put(x, y + 1, z, block(("oxeye_daisy", "azure_bluet", "lily_of_the_valley")[int(r * 1000) % 3]))
            elif r < density:
                f.put(x, y + 1, z, block("moss_carpet"))


# ================================================================ 入口と参道

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
    paved(f, cx - 26, cz - 22, cx + 26, cz + 16, table=WARM_FLOOR, salt=12, ruin=0.02)
    return_circle(f, cx, GROUND, cz)
    # 入口の両脇の大きな台と、こわれた鳥居のような門（2本の太い柱と、落ちた横木）
    for sx in (-1, 1):
        gx = cx + sx * 10
        for x in range(gx - 1, gx + 2):
            for z in range(cz - 18, cz - 15):
                for y in range(GROUND + 1, GROUND + 12):
                    f.put(x, y, z, block("quartz_pillar", axis="y") if (x == gx and z == cz - 17) else block("calcite"))
        f.put(gx, GROUND + 12, cz - 17, block("chiseled_quartz_block"))
    for x in range(cx - 11, cx + 12):
        if R.at(x, 5) < 0.75:
            f.put(x, GROUND + 12, cz - 17, block("smooth_quartz_slab", type="bottom", waterlogged=False))
    # 落ちた横木（地面に斜めに）
    for k in range(8):
        f.put(cx - 6 + k, GROUND + 1, cz - 12 + (k // 3), block("smooth_quartz"))
    sign(f, cx + 2, GROUND + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    # 11月24日：入口から来る人に背を向けていたので、1〜3階と同じく南（入口の側）へ向ける
    sign(f, cx + 3, GROUND + 1, cz - 14, 0, ["4階", "崩壊した神殿", "", "参道は北へ"])
    tx, tz = cx - 8, cz - 6
    f.put(tx, GROUND + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, GROUND + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, GROUND + 1, tz)
    for (dx, dz) in ((-20, -18), (20, -18), (-20, 10), (20, 10)):
        lantern_post(f, cx + dx, cz + dz, GROUND)
    chest(f, cx + 22, GROUND + 1, cz - 4, "west")


def avenue(f):
    x0, z0, x1, z1 = AVENUE
    paved(f, x0, z0, x1, z1, salt=13)
    # 縁石
    for z in range(z0, z1 + 1):
        f.put(x0 - 1, GROUND + 1, z, slab("smooth_stone"))
        f.put(x1 + 1, GROUND + 1, z, slab("smooth_stone"))
    # 柱の並木（ところどころ折れている）と、顔の削れた石像
    for i, z in enumerate(range(z0 + 6, z1 - 4, 12)):
        for x in (x0 - 3, x1 + 3):
            column(f, x, z, GROUND, 9, broken=R.at(x, z, 31) < 0.45, salt=i)
        if i % 2 == 1:
            faceless_statue(f, x0 - 6, z + 6, GROUND, "east")
            faceless_statue(f, x1 + 6, z + 6, GROUND, "west")
    # 参道の脇の崩れた建物（石の土台と、低い壁）
    for k, (bx0, bz0, bx1, bz1) in enumerate(((200, 330, 238, 360), (290, 340, 330, 372), (206, 396, 240, 430), (288, 404, 324, 440))):
        ruin(f, bx0, bz0, bx1, bz1, salt=40 + k)
    chest(f, 214, GROUND + 1, 345, "east")
    chest(f, 318, GROUND + 1, 422, "west")
    scatter_grass(f, 120, 280, 400, 480)


def ruin(f, x0, z0, x1, z1, salt=0, height=6):
    """崩れた建物：石の土台と、高さのばらばらな壁。中は草"""
    paved(f, x0, z0, x1, z1, salt=salt, ruin=0.25)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge:
                continue
            h = int(height * R.at(x, z, salt, 9) ** 0.7)
            # 入口（各辺のまん中は低い）
            if abs(x - (x0 + x1) // 2) <= 1 or abs(z - (z0 + z1) // 2) <= 1:
                h = 0
            for y in range(GROUND + 1, GROUND + 1 + h):
                f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, salt)))
    # 中の瓦礫
    for k in range(6):
        rx = x0 + 2 + int(R.at(salt, k, 1) * max(1, x1 - x0 - 4))
        rz = z0 + 2 + int(R.at(salt, k, 2) * max(1, z1 - z0 - 4))
        f.put(rx, GROUND + 1, rz, block(R.pick(RUBBLE, rx, rz, salt)))


# ================================================================ 前庭

def plaza(f):
    cx, cz = PLAZA
    for (x, z) in circle_cells(cx, cz, PLAZA_R):
        d = math.hypot(x - cx, z - cz)
        if d > PLAZA_R - 2:
            f.put(x, GROUND, z, block("polished_andesite"))
        elif int(d) % 8 == 0:
            f.put(x, GROUND, z, block("chiseled_quartz_block" if R.at(x, z, 2) < 0.1 else "smooth_quartz"))
        else:
            f.put(x, GROUND, z, block(R.pick(TEMPLE_FLOOR, x, z, 14)))
        for y in range(GROUND + 1, GROUND + 4):
            f.put(x, y, z, None)
    # まん中の割れた結晶の祭壇（3段の台と、欠けた大きな結晶）
    for r, h in ((7, 1), (5, 2), (3, 3)):
        for (x, z) in circle_cells(cx, cz, r):
            for y in range(GROUND + 1, GROUND + 1 + h):
                f.put(x, y, z, block("smooth_quartz" if y == GROUND + h else "calcite"))
    for y in range(GROUND + 4, GROUND + 9):
        f.put(cx, y, cz, block("amethyst_block"))
    for (dx, dz, top) in ((1, 0, 7), (-1, 0, 6), (0, 1, 5), (0, -1, 7)):
        for y in range(GROUND + 4, GROUND + top):
            f.put(cx + dx, y, cz + dz, block("amethyst_block"))
    f.put(cx, GROUND + 9, cz, block("amethyst_cluster", facing="up", waterlogged=False))
    # 欠けた所（ロスト）
    f.put(cx + 1, GROUND + 6, cz, block("amemaru:lost_blank"))
    # まわりの柱の輪（16本。半分は折れている）
    for k in range(16):
        a = k / 16 * math.tau
        x = int(round(cx + math.cos(a) * (PLAZA_R - 6)))
        z = int(round(cz + math.sin(a) * (PLAZA_R - 6)))
        # 道の前は空ける
        if min(abs(math.degrees(a) % 360 - d) for d in (0, 90, 180, 270, 360)) < 8:
            continue
        column(f, x, z, GROUND, 11, broken=k % 2 == 1, salt=k + 100)
    # 中継の灯1（祭壇の南）
    waypoint(f, "plaza", "前庭", cx, GROUND + 1, cz + 12, 0, look_at=(cx, cz))
    for (dx, dz) in ((-14, 14), (14, 14), (-14, -14), (14, -14)):
        lantern_post(f, cx + dx, cz + dz, GROUND)
    sign(f, cx + 2, GROUND + 1, cz + 14, 0, ["前庭", "", "北：歌の大広間", "東：壁画の回廊 西：浮石の庭"])
    chest(f, cx - 30, GROUND + 1, cz + 20, "east")


def causeway(f):
    """前庭から北の前室へ：柱の並ぶ石の道"""
    x0, x1 = 256, 272
    paved(f, x0, ANTE[3], x1, PLAZA[1] - PLAZA_R + 2, salt=15)
    for z in range(ANTE[3] + 4, PLAZA[1] - PLAZA_R, 8):
        for x in (x0 - 2, x1 + 2):
            column(f, x, z, GROUND, 10, broken=R.at(x, z, 77) < 0.3, salt=z)


# ================================================================ 壁画（パネルの組）

MURAL_NAMES = {"first": "第一時代の壁画", "sunder": "大断絶の壁画", "second": "第二時代の壁画", "whole": "一枚の絵"}
MURAL_TEXTS = {
    "first": "手をつないだ人々が、大きな結晶を囲んでいる。みな、同じ一本の光の糸を握っている。",
    "sunder": "塔の根元の輪の装置が、まばゆく光った。糸は焼け切れ、人々は背を向け合って、ちりぢりになる。",
    "second": "切れた糸の端を、人々がもう一度結んでいる。新しい塔が建ち、結び目の灯が街に灯る。",
}
# 壁画ごとの絵の部分（パネルの絵の番号。make_temple_things.py の絵と同じ並び）
MURAL_PIECES = {"first": [0, 1, 2], "sunder": [3, 4, 5, 6], "second": [7, 8, 9, 10, 11]}


def mural(f, mid, panels, facing, door=None, display=False):
    """壁画のパネルを置く。panels は [(x, y, z), …]（左から右）。正しい向きは 0。display でなければ、はじめは崩す"""
    pieces = MURAL_PIECES[mid] if not display else sum((MURAL_PIECES[k] for k in ("first", "sunder", "second")), [])
    out = []
    for i, (x, y, z) in enumerate(panels):
        piece = pieces[i]
        turn = 0 if display else 1 + int(R.at(x, y, z, 91) * 3) % 3
        f.put(x, y, z, block("amemaru:mural_panel", facing=facing, turn=turn, piece=piece, lit=not display),
              thing(i, "whole" if display else mid))
        out.append({"pos": [x, y, z], "piece": piece, "answer": 0})
    entry = {"id": "whole" if display else mid, "name": MURAL_NAMES["whole" if display else mid], "panels": out}
    if not display:
        entry["text"] = MURAL_TEXTS[mid]
    else:
        entry["display"] = True
    if door is not None:
        entry["door"] = {"box": list(door[0]) + list(door[1]), "block": "minecraft:chiseled_stone_bricks"}
        (x0, y0, z0), (x1, y1, z1) = door
        f.fill(x0, y0, z0, x1, y1, z1, block("chiseled_stone_bricks"))
    f.murals.append(entry)


# ================================================================ 壁画の回廊

def gallery(f):
    x0, z0, x1, z1 = GALLERY
    paved(f, x0, z0, x1, z1, table=WARM_FLOOR, salt=16, ruin=0.03)
    # 北の壁（壁画の面）と南の柱廊。屋根（ところどころ落ちている）
    for x in range(x0, x1 + 1):
        for y in range(GROUND + 1, GROUND + 9):
            f.put(x, y, z0, block(R.pick(TEMPLE_WALL, x, y, z0, 17)))
        if x % 6 == 0:
            column(f, x, z1 - 1, GROUND, 8, broken=R.at(x, 5, 18) < 0.25, salt=x, thick=False)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1):
            if R.at(x, z, 19) < 0.78:
                f.put(x, GROUND + 9, z, slab("smooth_stone"))
    # 東の端の壁（東の出口はない）
    for z in range(z0, z1 + 1):
        for y in range(GROUND + 1, GROUND + 9):
            f.put(x1, y, z, block(R.pick(TEMPLE_WALL, x1, y, z, 17)))
    # 北の壁の壁画（飾りの白く消された壁画：ロストの侵食の印。パズルは東のはし）
    for x in range(x0 + 6, x1 - 24, 9):
        for k in range(3):
            f.put(x + k, GROUND + 3, z0, block("chiseled_quartz_block" if R.at(x, k, 20) < 0.7 else "amemaru:lost_blank"))
    # 壁画「第一時代」（3枚。南を向く）と、祈りの間へ上る通路の扉
    mural(f, "first", [(x1 - 14 + k, GROUND + 3, z0) for k in range(3)], "south",
          door=((410, GROUND + 1, 214), (418, GROUND + 5, 216)))
    # 11月24日：壁画の壁（北）を向いていたので、回廊を来る人の側（南）へ向ける
    sign(f, x1 - 13, GROUND + 1, z0 + 2, 0, ["第一時代の壁画", "", "パネルを右クリックで", "回して、絵を戻そう"])
    # 祈りの間への通路（扉の北）。回廊の北の壁に入口
    for x in range(410, 419):
        for y in range(GROUND + 1, GROUND + 6):
            f.put(x, y, z0, None)
    paved(f, 410, CHAPEL[3], 418, z0, table=WARM_FLOOR, salt=22)
    for z in range(CHAPEL[3], z0):
        for x in (409, 419):
            for y in range(GROUND + 1, GROUND + 6):
                f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 23)))
        for x in range(410, 419):
            f.put(x, GROUND + 6, z, slab("smooth_stone"))
            for y in range(GROUND + 1, GROUND + 6):
                if f.name_at(x, y, z) is not None and not (214 <= z <= 216):
                    f.put(x, y, z, None)
    chest(f, x0 + 30, GROUND + 1, z0 + 2, "south")
    chest(f, x1 - 4, GROUND + 1, z1 - 3, "west")


# ================================================================ 祈りの間

def chapel(f):
    x0, z0, x1, z1 = CHAPEL
    inner_z = z0 + 16      # 奥の院との仕切り
    paved(f, x0, z0, x1, z1, table=TEMPLE_FLOOR, salt=24, ruin=0.02)
    # 壁（高さ 12）と、半分崩れた屋根
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                h = 12 if R.at(x, z, 25) > 0.12 else 6
                for y in range(GROUND + 1, GROUND + 1 + h):
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 25)))
            elif R.at(x, z, 26) < 0.55 and (x + z) % 2 == 0:
                f.put(x, GROUND + 12, z, slab("smooth_quartz"))
    # 南の入口（回廊からの通路）
    for x in range(410, 419):
        for y in range(GROUND + 1, GROUND + 6):
            f.put(x, y, z1, None)
    # 長椅子の列
    for z in range(inner_z + 14, z1 - 6, 4):
        for x in list(range(x0 + 6, 405)) + list(range(425, x1 - 5)):
            f.put(x, GROUND + 1, z, stairs("birch", "south"))
    # 奥の院との仕切りの壁と扉（音の順番で開く）
    for x in range(x0 + 1, x1):
        for y in range(GROUND + 1, GROUND + 9):
            f.put(x, y, inner_z, block(R.pick(TEMPLE_WALL, x, y, inner_z, 27)))
    door = ((418, GROUND + 1, inner_z), (422, GROUND + 4, inner_z))
    (dx0, dy0, dz0), (dx1, dy1, dz1) = door
    f.fill(dx0, dy0, dz0, dx1, dy1, dz1, block("chiseled_stone_bricks"))
    # 音の順番：鈴の石と、4つの音の床（扉の前）
    cz = inner_z + 8
    f.put(420, GROUND + 1, cz + 4, block("amemaru:chime_stone"), thing(0, "prayer"))
    plates = []
    for k in range(4):
        px = 414 + k * 4
        f.put(px, GROUND + 1, cz, block("amemaru:sound_plate", note=k, lit=False))
        plates.append([px, GROUND + 1, cz])
    f.songs.append({"id": "prayer", "name": "祈りの歌", "chime": [420, GROUND + 1, cz + 4], "plates": plates, "length": 3,
                    "door": {"box": [dx0, dy0, dz0, dx1, dy1, dz1], "block": "minecraft:chiseled_stone_bricks"}})
    sign(f, 422, GROUND + 1, cz + 4, 0, ["祈りの歌", "", "鈴の石を鳴らして", "同じ順番に床を踏む"])
    # 奥の院：祭壇・名のある魔物（祈りの導き手）・宝箱・壁画「大断絶」
    for x in range(410, 431):
        f.put(x, GROUND + 1, z0 + 3, block("smooth_quartz"))
    mural(f, "sunder", [(418 + k, GROUND + 3, z0) for k in range(4)], "south")
    # 11月24日：壁画の壁（北）を向いていたので、奥の院へ入って来る人の側（南）へ向ける
    sign(f, 417, GROUND + 1, z0 + 5, 0, ["大断絶の壁画", "", "パネルを回して", "絵を戻そう"])
    f.guardians.append({"id": "lead_echo", "enemy": "amemaru:prayer_echo", "variant": "amemaru:prayer_echo_leader",
                        "pos": [420.5, GROUND + 2, z0 + 9.5], "respawn_seconds": 300, "activation": 24, "pillar": -1})
    chest(f, 404, GROUND + 1, z0 + 4, "south", guardian="lead_echo")
    # 中継の灯5（祈りの間の南）
    waypoint(f, "chapel", "祈りの間", 440, GROUND + 1, z1 - 6, 180, look_at=(420, cz))
    for (lx, lz) in ((x0 + 4, z1 - 4), (x1 - 4, z1 - 4), (x0 + 4, inner_z + 4), (x1 - 4, inner_z + 4)):
        lantern_post(f, lx, lz, GROUND, 2)
    chest(f, x1 - 3, GROUND + 1, inner_z + 3, "west")


# ================================================================ 浮石の庭（谷と、浮かぶ瓦礫の島）

ISLANDS = [(176, 250, 12), (150, 290, 9), (118, 254, 8), (96, 312, 10), (60, 270, 9), (70, 360, 9), (130, 352, 11),
           (180, 330, 8), (44, 316, 6)]
BRIDGES = [((199, 250), (176, 250)), ((176, 250), (150, 290)), ((150, 290), (118, 254)), ((150, 290), (96, 312)),
           ((118, 254), (60, 270)), ((96, 312), (70, 360)), ((96, 312), (130, 352)), ((130, 352), (180, 330)),
           ((60, 270), (44, 316))]


def floating_garden(f):
    x0, z0, x1, z1 = FLOAT
    # 谷を掘る（地面から谷の底まで）。底は瓦礫と苔
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(CHASM + 1, GROUND + 1):
                f.put(x, y, z, None)
            f.put(x, CHASM, z, block(R.pick(RUBBLE, x, z, 41)))
            for y in range(CHASM - 2, CHASM):
                f.put(x, y, z, block("stone"))
    # 谷の壁（台地の崖）
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            for y in range(CHASM - 2, GROUND):
                if f.v[x, y, z] == 0:
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 42)))
    # 島（石の塊の上に、神殿の床）
    for k, (cx, cz, r) in enumerate(ISLANDS):
        for (x, z) in circle_cells(cx, cz, r):
            d = math.hypot(x - cx, z - cz)
            depth = int((r - d) * 1.2) + 2
            for y in range(GROUND - depth, GROUND):
                f.put(x, y, z, block(R.pick(RUBBLE, x, y, z, 43)))
            f.put(x, GROUND, z, block(R.pick(TEMPLE_FLOOR if d < r - 2 else GRASS, x, z, 44)))
        if k % 2 == 0:
            column(f, cx + r // 2, cz, GROUND, 8, broken=True, salt=k, thick=False)
    # 橋（細い石の橋。ところどころ欠けて、1マス跳ぶ）
    for k, ((ax, az), (bx, bz)) in enumerate(BRIDGES):
        steps = int(max(abs(bx - ax), abs(bz - az)))
        for i in range(steps + 1):
            t = i / max(1, steps)
            x = int(round(ax + (bx - ax) * t))
            z = int(round(az + (bz - az) * t))
            gap = k % 3 == 1 and steps // 2 - 1 <= i <= steps // 2 - 1 + 0
            for wx in range(x - 1, x + 2):
                for wz in range(z - 1, z + 2):
                    if gap or f.v[wx, GROUND, wz] != 0:
                        continue
                    f.put(wx, GROUND, wz, block("polished_diorite"))
    # 谷の底から前庭へ戻る坂（南東のすみ。谷の底から、地面の高さまで）
    rx = x1 - 6
    for i, z in enumerate(range(z1 - 4, z1 - 4 - (GROUND - CHASM) * 2, -1)):
        y = CHASM + 1 + i // 2
        for x in range(rx - 2, rx + 3):
            for yy in range(CHASM + 1, y + 1):
                f.put(x, yy, z, block("cobblestone"))
            for yy in range(y + 1, y + 4):
                f.put(x, yy, z, None)
    top_z = z1 - 4 - (GROUND - CHASM) * 2 + 1
    for z in range(top_z - 2, top_z + 3):
        for x in range(rx + 3, x1 + 1):
            f.put(x, GROUND, z, block("cobblestone"))
            for yy in range(GROUND + 1, GROUND + 4):
                f.put(x, yy, z, None)
    sign(f, rx, CHASM + 1, z1 - 2, 8, ["谷の底", "", "坂を上ると", "前庭へ戻れる"])
    # 中継の灯2（いちばん大きな島）
    cx, cz, _ = ISLANDS[6]
    waypoint(f, "float", "浮石の庭", cx, GROUND + 1, cz - 4, 0, look_at=(cx, cz))
    for (cx, cz, r) in (ISLANDS[3], ISLANDS[4], ISLANDS[7]):
        chest(f, cx, GROUND + 1, cz + r - 3, "north")
    # 浮かぶ瓦礫（空中の石の塊。飾り）
    for k in range(26):
        bx = x0 + int(R.at(k, 1, 45) * (x1 - x0))
        bz = z0 + int(R.at(k, 2, 45) * (z1 - z0))
        by = GROUND + 10 + int(R.at(k, 3, 45) * 26)
        size = 1 + int(R.at(k, 4, 45) * 3)
        for x in range(bx - size, bx + size + 1):
            for z in range(bz - size, bz + size + 1):
                for y in range(by - size // 2, by + 1):
                    if math.hypot(x - bx, z - bz) <= size + 0.3 and R.at(x, y, z, 46) < 0.85:
                        f.put(x, y, z, block(R.pick(RUBBLE, x, y, z, 47)))


# ================================================================ 聖獣の庭（高い段）

def terrace(f):
    x0, z0, x1, z1 = TERRACE
    # 段を盛る（地面から高い段まで）
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(GROUND + 1, HIGH):
                f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 51)) if (x in (x0, x1) or z in (z0, z1)) else block("stone"))
            f.put(x, HIGH, z, block(R.pick(GRASS, x, z, 52)))
    # 庭の道（十字）と、生け垣・聖獣の像
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    paved(f, mx - 3, z0 + 4, mx + 3, z1 - 1, y=HIGH, table=WARM_FLOOR, salt=53)
    paved(f, x0 + 4, mz - 3, x1 - 4, mz + 3, y=HIGH, table=WARM_FLOOR, salt=54)
    for k in range(24):
        hx = x0 + 6 + int(R.at(k, 1, 55) * (x1 - x0 - 12))
        hz = z0 + 6 + int(R.at(k, 2, 55) * (z1 - z0 - 12))
        if abs(hx - mx) < 6 or abs(hz - mz) < 6:
            continue
        for x in range(hx - 2, hx + 3):
            for z in range(hz - 1, hz + 2):
                for y in (HIGH + 1, HIGH + 2):
                    f.put(x, y, z, block("azalea_leaves", persistent=True, distance=1, waterlogged=False))
    for (sx, sz) in ((mx - 12, mz - 12), (mx + 12, mz - 12), (mx - 12, mz + 12), (mx + 12, mz + 12)):
        beast_statue(f, sx, sz, HIGH)
    # 祠（北のまん中）：壁画「第二時代」（5枚）と、奥の宝箱の扉
    sx0, sz0, sx1, sz1 = mx - 9, z0 + 2, mx + 9, z0 + 16
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            edge = x in (sx0, sx1) or z in (sz0, sz1)
            f.put(x, HIGH, z, block("smooth_quartz"))
            if edge and not (z == sz1 and abs(x - mx) <= 2):
                for y in range(HIGH + 1, HIGH + 8):
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 56)))
            f.put(x, HIGH + 8, z, block("smooth_quartz_slab", type="bottom", waterlogged=False))
    # 奥の小部屋の仕切り（扉は壁画で開く）
    for x in range(sx0 + 1, sx1):
        for y in range(HIGH + 1, HIGH + 8):
            f.put(x, y, sz0 + 5, block(R.pick(TEMPLE_WALL, x, y, sz0 + 5, 57)))
    mural(f, "second", [(mx - 2 + k, HIGH + 3, sz0 + 5) for k in range(5)], "south",
          door=((mx - 1, HIGH + 1, sz0 + 5), (mx + 1, HIGH + 2, sz0 + 5)))
    chest(f, mx, HIGH + 1, sz0 + 2, "south")
    # 11月24日：壁画の壁（北）を向いていたので、庭を来る人の側（南）へ向ける
    sign(f, mx + 3, HIGH + 1, sz0 + 7, 0, ["第二時代の壁画", "", "パネルを回して", "絵を戻そう"])
    f.guardians.append({"id": "old_beast", "enemy": "amemaru:broken_beast", "variant": "amemaru:broken_beast_elder",
                        "pos": [mx + 0.5, HIGH + 1, mz + 0.5], "respawn_seconds": 300, "activation": 30, "pillar": -1})
    chest(f, mx - 8, HIGH + 1, mz + 8, "east", guardian="old_beast")
    waypoint(f, "terrace", "聖獣の庭", mx + 6, HIGH + 1, z1 - 8, 180, look_at=(mx, mz))
    chest(f, x0 + 4, HIGH + 1, z1 - 4, "east")
    # 前庭から上る大階段（段の南東のすみ。南の地面から北へ上る）
    sx_a, sx_b = 198, 212
    for i in range(HIGH - GROUND):
        z = z1 + (HIGH - GROUND) - i
        y = GROUND + 1 + i
        for x in range(sx_a, sx_b + 1):
            for yy in range(GROUND + 1, y):
                f.put(x, yy, z, block("calcite"))
            f.put(x, y, z, stairs("polished_diorite", "north") if x not in (sx_a, sx_b) else block("polished_diorite"))
            for yy in range(y + 1, y + 4):
                f.put(x, yy, z, None)
        for x in (sx_a - 1, sx_b + 1):
            f.put(x, y, z, block("polished_diorite"))
            f.put(x, y + 1, z, wall_state("diorite_wall"))
    for x in range(sx_a, sx_b + 1):
        for yy in range(HIGH + 1, HIGH + 4):
            f.put(x, yy, z1, None)
    scatter_grass(f, x0, z0, x1, z1, y=HIGH, density=0.2, salt=58)


def beast_statue(f, x, z, y0):
    """守護獣の像（台と、すわった獅子のような形）"""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            f.put(x + dx, y0 + 1, z + dz, block("polished_andesite"))
    for (dx, dz, h) in ((0, 0, 3), (0, -1, 2), (0, 1, 2)):
        for y in range(y0 + 2, y0 + 2 + h):
            f.put(x + dx, y, z + dz, block("calcite"))
    f.put(x, y0 + 5, z - 1, block("calcite"))
    f.put(x, y0 + 5, z, block("gold_block") if R.at(x, z, 59) < 0.5 else block("amemaru:lost_blank"))


# ================================================================ 地下の納骨堂

def crypt(f):
    x0, z0, x1, z1 = CRYPT
    # 地下を埋めてから掘る（まわり2マスは壁）
    f.fill(x0 - 2, LOW - 2, z0 - 2, x1 + 2, GROUND - 1, z1 + 2, block("deepslate"))
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 3):
            for y in range(LOW - 2, GROUND - 4):
                edge = x < x0 or x > x1 or z < z0 or z > z1
                f.put(x, y, z, block(R.pick(CRYPT_WALL, x, y, z, 61)) if edge else block("deepslate"))
    # 長い通路（南北）と、両脇の小部屋
    mx = (x0 + x1) // 2
    hall = (mx - 5, z0 + 4, mx + 5, z1 - 4)
    cave(f, *hall, LOW, 6)
    for i, z in enumerate(range(z0 + 10, z1 - 10, 16)):
        for side in (-1, 1):
            rx0 = mx + side * 8 if side > 0 else mx - 8 - 18
            rx1 = rx0 + 18
            cave(f, rx0, z - 5, rx1, z + 5, LOW, 5)
            # 石の棺
            for k in range(3):
                bx = rx0 + 3 + k * 6
                for dz in (-2, 2):
                    for x in range(bx, bx + 3):
                        f.put(x, LOW + 1, z + dz, block("polished_deepslate"))
                        f.put(x, LOW + 2, z + dz, slab("polished_deepslate"))
            # 通路との入口
            for x in range(min(mx + side * 6, mx + side * 8), max(mx + side * 6, mx + side * 8) + 1):
                for y in range(LOW + 1, LOW + 4):
                    for dz in (-1, 0, 1):
                        f.put(x, y, z + dz, None)
            if i % 2 == 0:
                chest(f, rx1 - 1 if side > 0 else rx0 + 1, LOW + 1, z, "west" if side > 0 else "east")
    # 灯（壁の魂の灯と、見えない灯）
    for z in range(hall[1], hall[3], 8):
        for x in (hall[0], hall[2]):
            f.put(x, LOW + 4, z, block("soul_lantern", hanging=False, waterlogged=False))
    # 騎士団の野営跡（北のはしの部屋。門の大石像が守る）：遺品・倒れた槍・天幕の名残
    kx0, kz0, kx1, kz1 = mx - 10, z0 - 0, mx + 10, z0 + 14
    cave(f, kx0, kz0, kx1, kz1, LOW, 7)
    for x in range(mx - 2, mx + 3):
        for y in range(LOW + 1, LOW + 5):
            f.put(x, y, kz1, None)
    f.put(mx, LOW + 1, kz0 + 4, block("amemaru:knight_relic", facing="south"), thing(0, "knights"))
    f.relics.append({"id": "knights", "pos": [mx, LOW + 1, kz0 + 4],
                     "items": ["amemaru:knight_helm_relic", "amemaru:harold_tag"], "flag": "amemaru:story/knight_relics_found",
                     "event": "amemaru:floor4/relics", "quest": "amemaru:knight_relics"})
    for (dx, dz) in ((-6, 3), (6, 3), (-4, 9), (5, 8)):
        f.put(mx + dx, LOW + 1, kz0 + dz, block("white_carpet"))
        f.put(mx + dx, LOW + 2, kz0 + dz, block("skeleton_skull", rotation=int(R.at(dx, dz, 3) * 16)))
    for (dx, dz) in ((-8, 2), (8, 2)):
        for y in range(LOW + 1, LOW + 4):
            f.put(mx + dx, y, kz0 + dz, block("oak_fence", north=False, south=False, east=False, west=False, waterlogged=False))
        f.put(mx + dx, LOW + 4, kz0 + dz, block("white_banner", rotation=8))
    for x in range(kx0 + 1, kx1):
        f.put(x, LOW + 7, kz0 + 2, block("white_wool"))
    f.story_zones.append({"id": "knight_camp", "box": [kx0, LOW, kz0, kx1, LOW + 6, kz1]})
    sign(f, mx + 3, LOW + 1, kz1 - 1, 8, ["結晶騎士団", "第七次遠征", "野営の跡", ""])
    f.guardians.append({"id": "gate_statue", "enemy": "amemaru:silent_statue", "variant": "amemaru:silent_statue_gate",
                        "pos": [mx + 0.5, LOW + 1, kz1 + 4.5], "respawn_seconds": 300, "activation": 20, "pillar": -1})
    chest(f, kx1 - 2, LOW + 1, kz0 + 2, "west", guardian="gate_statue")
    waypoint(f, "crypt", "納骨堂", mx + 3, LOW + 1, z1 - 8, 180, look_at=(mx, z1 - 20))


def crypt_access(f):
    """納骨堂への2つの入口（前庭の南西の階段・鐘楼の西の階段）。前庭と鐘楼の床を敷いたあとで掘る"""
    # 前庭から下りる階段（南へ下りて、西の小部屋へつなぐ）
    top_z = PLAZA[1] + PLAZA_R - 12
    stair_down(f, 236, 242, top_z, GROUND, LOW, "south")
    tunnel(f, 236, top_z + (GROUND - LOW), 242, 308)
    # 鐘楼から下りる階段（西へ下りて、長い通路で東の小部屋へ）
    stair_down(f, 392, 396, TOWER[0] + 10, GROUND, LOW, "west", width_axis="z")
    tunnel(f, 286, 392, TOWER[0] + 10 - (GROUND - LOW), 396)
    sign(f, 240, GROUND + 1, top_z - 1, 0, ["地下の納骨堂", "", "結晶騎士団の", "野営の跡がある"])


def tunnel(f, x0, z0, x1, z1):
    """地下の通路（床 LOW、高さ3）。まわりを岩で囲む"""
    x0, x1 = min(x0, x1), max(x0, x1)
    z0, z1 = min(z0, z1), max(z0, z1)
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 3):
            for y in range(LOW - 2, LOW + 6):
                if f.v[x, y, z] == 0 and not f.carved[x, y, z]:
                    f.put(x, y, z, block(R.pick(CRYPT_WALL, x, y, z, 67)))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.put(x, LOW, z, block(R.pick(CRYPT_FLOOR, x, z, 62)))
            for y in range(LOW + 1, LOW + 4):
                f.put(x, y, z, None)
            if (x + z) % 9 == 0:
                f.put(x, LOW + 3, z, block("light", level=12, waterlogged=False))


def cave(f, x0, z0, x1, z1, y, height):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.put(x, y, z, block(R.pick(CRYPT_FLOOR, x, z, 63)))
            for yy in range(y + 1, y + 1 + height):
                f.put(x, yy, z, None)
            f.put(x, y + 1 + height, z, block(R.pick(CRYPT_WALL, x, z, y, 64)))


def stair_down(f, a0, a1, start, top, bottom, toward, width_axis="x"):
    """地面から地下へ下りる階段。toward の向きへ進むごとに1段下りる。まわりは岩で囲む（台地の下は空なので）"""
    n = top - bottom
    step = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[toward]
    facing = {"south": "north", "north": "south", "east": "west", "west": "east"}[toward]
    # 囲いの岩（空いている所だけ）
    for i in range(-1, n + 4):
        for a in range(a0 - 2, a1 + 3):
            if width_axis == "x":
                x, z = a, start + step[1] * i
            else:
                x, z = start + step[0] * i, a
            for yy in range(bottom - 2, top):
                if 0 <= x < SIZE and 0 <= z < SIZE and f.v[x, yy, z] == 0 and not f.carved[x, yy, z]:
                    f.put(x, yy, z, block(R.pick(CRYPT_WALL, x, yy, z, 66)))
    for i in range(n + 3):
        y = top - i
        for a in range(a0, a1 + 1):
            if width_axis == "x":
                x, z = a, start + step[1] * i
            else:
                x, z = start + step[0] * i, a
            floor_y = max(bottom, y)
            for yy in range(floor_y + 1, min(HEIGHT, floor_y + 5)):
                f.put(x, yy, z, None)
            if i < n:
                f.put(x, floor_y, z, stairs("polished_deepslate", facing) if i > 0 else block("polished_deepslate"))
                f.put(x, floor_y - 1, z, block("deepslate"))
            else:
                f.put(x, bottom, z, block(R.pick(CRYPT_FLOOR, x, z, 65)))
    # 地面に開いた所の手すり（上のはしからだけ入る）
    for i in range(1, 7):
        for a in (a0 - 1, a1 + 1):
            if width_axis == "x":
                x, z = a, start + step[1] * i
            else:
                x, z = start + step[0] * i, a
            if f.v[x, top, z] != 0 and f.v[x, top + 1, z] == 0:
                f.put(x, top + 1, z, wall_state("polished_deepslate_wall"))


# ================================================================ 崩れた鐘楼（白い壁画の間）

def bell_tower(f):
    x0, z0, x1, z1 = TOWER
    paved(f, x0, z0, x1, z1, salt=71, ruin=0.15)
    tx0, tz0, tx1, tz1 = 396, 366, 416, 386
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            edge = x in (tx0, tx1) or z in (tz0, tz1)
            if edge:
                top = GROUND + 26 if (x + z) % 7 else GROUND + 20
                if x == tx0 and abs(z - (tz0 + tz1) // 2) <= 1:
                    top = GROUND  # 入口（西）
                for y in range(GROUND + 1, top + 1):
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 72)))
                if x == tx0 and abs(z - (tz0 + tz1) // 2) <= 1:
                    for y in range(GROUND + 1, GROUND + 5):
                        f.put(x, y, z, None)
    # 窓（各面のまん中に、縦長の窓を2つずつ）
    mxt, mzt = (tx0 + tx1) // 2, (tz0 + tz1) // 2
    for wy in (GROUND + 9, GROUND + 16):
        for (wx, wz) in ((mxt, tz0), (mxt, tz1), (tx1, mzt)):
            for y in (wy, wy + 1):
                f.put(wx, y, wz, None)
    # 中の白い壁画の間（ロストの侵食。壁の人物が白く消されている）
    for x in range(tx0 + 1, tx1):
        f.put(x, GROUND + 3, tz0 + 1, block("chiseled_quartz_block" if R.at(x, 3, 73) < 0.5 else "amemaru:lost_blank"))
        f.put(x, GROUND + 4, tz0 + 1, block("smooth_quartz" if R.at(x, 4, 73) < 0.5 else "amemaru:lost_blank"))
    f.lost_zones.append(((tx0, tz0, tx1, tz0), (GROUND + 1, GROUND + 10)))
    f.story_zones.append({"id": "white_murals", "box": [tx0 + 1, GROUND, tz0 + 1, tx1 - 1, GROUND + 8, tz1 - 1]})
    sign(f, tx0 + 3, GROUND + 1, tz1 - 2, 8, ["白い壁画", "", "誰かが、誰かを", "忘れた跡"])
    # 上へのらせん階段（四すみを回る）と、てっぺんの見晴らし台・落ちた鐘
    y = GROUND + 1
    path = [(tx0 + 1, z) for z in range(tz1 - 1, tz0, -1)] + [(x, tz0 + 1) for x in range(tx0 + 2, tx1)] + \
           [(tx1 - 1, z) for z in range(tz0 + 2, tz1)] + [(x, tz1 - 1) for x in range(tx1 - 2, tx0 + 1, -1)]
    k = 0
    spiral = {}
    while y < GROUND + 18:
        x, z = path[k % len(path)]
        spiral[(x, z)] = y
        f.put(x, y, z, block("polished_diorite"))
        for yy in range(y + 1, y + 4):
            if f.name_at(x, yy, z) not in ("amemaru:lost_blank", "chiseled_quartz_block", "smooth_quartz"):
                f.put(x, yy, z, None)
        k += 1
        if k % 2 == 0:
            y += 1
    for x in range(tx0 + 1, tx1):
        for z in range(tz0 + 1, tz1):
            # 最後の数段の上は空けておく（頭がつかえないように）
            if f.v[x, GROUND + 18, z] == 0 and spiral.get((x, z), 0) < GROUND + 14:
                f.put(x, GROUND + 18, z, slab("smooth_stone"))
    f.put((tx0 + tx1) // 2, GROUND + 1, (tz0 + tz1) // 2, block("bell", attachment="floor", facing="north", powered=False))
    chest(f, tx1 - 2, GROUND + 19, tz1 - 2, "west")
    chest(f, x0 + 4, GROUND + 1, z1 - 4, "north")
    ruin(f, 430, 400, 466, 436, salt=74)


# ================================================================ 歌の大広間（ボス）と前室

def arena(f):
    cx, cz = ARENA
    # 円い広間（壁の高さ 18。屋根はない：空の見える広間）
    for (x, z) in circle_cells(cx, cz, ARENA_R + 2):
        d = math.hypot(x - cx, z - cz)
        if d > ARENA_R:
            for y in range(GROUND + 1, GROUND + 19):
                f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 81)))
            continue
        f.put(x, GROUND, z, block("smooth_quartz" if int(d) % 6 else "chiseled_quartz_block"))
        for y in range(GROUND + 1, GROUND + 19):
            f.put(x, y, z, None)
    # 4つの音の床（北・東・南・西。半径13。0 白・1 青・2 金・3 紅）と、歌い手の壁がん
    for note, (dx, dz) in enumerate(((0, -13), (13, 0), (0, 13), (-13, 0))):
        px, pz = cx + dx, cz + dz
        for x in range(px - 1, px + 2):
            for z in range(pz - 1, pz + 2):
                f.put(x, GROUND, z, block("polished_diorite"))
        f.put(px, GROUND + 1, pz, block("amemaru:sound_plate", note=note, lit=False))
        f.plates.append([px, GROUND + 1, pz])
        ax, az = cx + int(round(dx * (ARENA_R + 0.5) / 13)), cz + int(round(dz * (ARENA_R + 0.5) / 13))
        for y in range(GROUND + 1, GROUND + 7):
            f.put(ax, y, az, None)
        f.put(ax, GROUND + 1, az, block(("white_glazed_terracotta", "light_blue_glazed_terracotta", "yellow_glazed_terracotta",
                                         "red_glazed_terracotta")[note]))
    # 封印の門（南）。前室の北の入口まで通す
    gx0, gx1 = cx - 4, cx + 4
    for x in range(gx0, gx1 + 1):
        for z in range(cz + ARENA_R - 1, ANTE[1] + 1):
            f.put(x, GROUND, z, block("polished_diorite"))
            for y in range(GROUND + 1, GROUND + 7):
                f.put(x, y, z, None)
    for x in range(gx0, gx1 + 1):
        for y in range(GROUND + 1, GROUND + 7):
            f.put(x, y, SEAL_Z, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"),
                                                                                    "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, SEAL_Z))
    # 出口の円（北）と、次の階への円
    return_circle(f, cx, GROUND, cz - 20)
    next_circle(f.put, cx + NEXT_DX, GROUND, cz - 20)
    # 見えない灯（広間はいつも明るく）
    for (x, z) in circle_cells(cx, cz, ARENA_R - 2):
        if (x % 5 == 0) and (z % 5 == 0):
            f.put(x, GROUND + 4, z, block("light", level=14, waterlogged=False))


def antechamber(f):
    x0, z0, x1, z1 = ANTE
    paved(f, x0, z0, x1, z1, table=WARM_FLOOR, salt=82, ruin=0.0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                for y in range(GROUND + 1, GROUND + 11):
                    f.put(x, y, z, block(R.pick(TEMPLE_WALL, x, y, z, 83)))
            else:
                for y in range(GROUND + 1, GROUND + 11):
                    f.put(x, y, z, None)
            f.put(x, GROUND + 11, z, slab("smooth_quartz"))
    # 北は封印の門へ、南は参道（石の道）へ開く
    for x in range(260, 269):
        for y in range(GROUND + 1, GROUND + 7):
            f.put(x, y, z0, None)
            f.put(x, y, z1, None)
    for z in range(z0 - 2, z0 + 1):
        for x in range(260, 269):
            f.put(x, GROUND, z, block("polished_diorite"))
            for y in range(GROUND + 1, GROUND + 7):
                f.put(x, y, z, None)
    # 東の壁の「一枚の絵」（12枚。西を向く。すべての壁画が戻ると灯る）
    mural(f, "whole", [(x1, GROUND + 3, z0 + 9 + k) for k in range(12)], "west", display=True)
    sign(f, x1 - 1, GROUND + 1, z0 + 8, 4, ["一枚の絵", "", "三つの壁画を", "戻すと、灯る"])
    # 西の壁の古代記録「神殿の碑文」
    tx, tz = x0 + 1, (z0 + z1) // 2
    f.put(tx, GROUND + 1, tz, block("chiseled_quartz_block"))
    f.put(tx, GROUND + 2, tz, block("lectern", facing="east", has_book=False, powered=False))
    f.tablet = (tx, GROUND + 2, tz)
    sign(f, tx + 1, GROUND + 1, tz + 2, 12, ["神殿の碑文", "", "古い文字が", "刻まれている"])
    for (lx, lz) in ((x0 + 3, z0 + 3), (x1 - 3, z0 + 3), (x0 + 3, z1 - 3), (x1 - 3, z1 - 3)):
        lantern_post(f, lx, lz, GROUND, 2)


# ================================================================ 道・散らばる瓦礫・木

def path_strip(f, x0, z0, x1, z1, base=GROUND):
    """古い道（草地の所だけ。敷石や建物の床は上書きしない）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if f.name_at(x, base, z) in GROUND_NAMES:
                f.put(x, base, z, block(R.pick(PATH, x, z, 151)))
                for y in range(base + 1, base + 3):
                    if f.name_at(x, y, z) in ("grass", "fern", "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "moss_carpet"):
                        f.put(x, y, z, None)


def paths(f):
    """区画どうしをつなぐ古い道（11月24日【補完】）"""
    path_strip(f, 197, 247, 224, 253)                  # 前庭 → 浮石の庭の橋
    path_strip(f, 203, 222, 232, 227)                  # 前庭 → 聖獣の庭の大階段
    path_strip(f, 276, 315, 366, 319)                  # 参道 → 崩れた鐘楼（東へ）
    path_strip(f, 362, 315, 366, 332)                  #        （南へ折れて、鐘楼の広場へ）
    path_strip(f, 437, 269, 441, 332)                  # 壁画の回廊 → 崩れた鐘楼
    path_strip(f, 300, 268, 310, 272)                  # 前庭 → 壁画の回廊（西の口）


def small_tree(f, x, z, base, kind):
    """小さな木（白樺かツツジ）。幹 3〜5・葉の玉"""
    h = 3 + int(R.at(x, z, 161) * 3)
    log = block("birch_log", axis="y") if kind == "birch" else block("oak_log", axis="y")
    leaves = block("birch_leaves" if kind == "birch" else ("flowering_azalea_leaves" if R.at(x, z, 162) < 0.4 else "azalea_leaves"),
                   persistent=True, distance=1, waterlogged=False)
    for y in range(base + 1, base + 1 + h):
        f.put(x, y, z, log)
    top = base + h
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(-1, 2):
                if abs(dx) + abs(dz) + abs(dy) > 3 or (dx == dz == 0 and dy < 1):
                    continue
                if R.at(x + dx, top + dy, z + dz, 163) < 0.15:
                    continue
                cx, cy, cz = x + dx, top + dy, z + dz
                if f.v[cx, cy, cz] == 0:
                    f.put(cx, cy, cz, leaves)


def scatter_ruins(f):
    """広い草地に、倒れた柱・崩れた壁のかけら・瓦礫の山・小さな木を散らす。中継の灯・宝箱・守り手・看板・湧く所のまわりは空ける"""
    keep = np.zeros((SIZE, SIZE), dtype=bool)

    def reserve(x, z, r):
        keep[max(0, x - r):x + r + 1, max(0, z - r):z + r + 1] = True
    for wp in f.waypoints:
        reserve(wp["pos"][0], wp["pos"][2], 8)
    for (p, _) in f.chests:
        reserve(p[0], p[2], 4)
    for g in f.guardians:
        reserve(int(g["pos"][0]), int(g["pos"][2]), 7)
    for (x, _, z) in f.signs:
        reserve(x, z, 3)
    tx, _, tz = f.travel_stone
    reserve(tx, tz, 6)
    for zn in spawn_zones(f):
        reserve(int(zn["center"][0]), int(zn["center"][2]), 3)
    count = {}
    for gx in range(10, SIZE - 10, 11):
        for gz in range(10, SIZE - 10, 11):
            x = gx + int(R.at(gx, gz, 171) * 7) - 3
            z = gz + int(R.at(gx, gz, 172) * 7) - 3
            base = HIGH if (TERRACE[0] + 3 <= x <= TERRACE[2] - 3 and TERRACE[1] + 3 <= z <= TERRACE[3] - 3) else GROUND
            if keep[x - 4:x + 5, z - 4:z + 5].any():
                continue
            ok = True
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if f.name_at(x + dx, base, z + dz) not in GROUND_NAMES:
                        ok = False
                        break
                    for y in range(base + 1, base + 6):
                        n = f.name_at(x + dx, y, z + dz)
                        if n is not None and n not in ("grass", "fern", "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "moss_carpet"):
                            ok = False
                            break
                    if not ok:
                        break
                if not ok:
                    break
            if not ok:
                continue
            r = R.at(x, z, 173)
            if base == HIGH:
                # 聖獣の庭は庭らしく：木と生け垣が多く、倒れた柱は少なめ
                r = (0.62, 0.30, 0.45, 0.80, 0.85, 0.90, 0.97, 0.60, 0.75, 0.88)[int(R.at(x, z, 189) * 10) % 10]
            if r < 0.28:
                kind = "fallen"
                d = (1, 0) if R.at(x, z, 174) < 0.5 else (0, 1)
                n = 3 + int(R.at(x, z, 175) * 3)
                for k in range(-(n // 2), n - n // 2):
                    f.put(x + d[0] * k, base + 1, z + d[1] * k, block("quartz_pillar", axis="x" if d[0] else "z"))
                f.put(x + d[0] * (n - n // 2), base + 1, z + d[1] * (n - n // 2), block("chiseled_quartz_block"))
                # 根元（折れた柱の残り）
                sx, sz = x - d[0] * (n // 2 + 2), z - d[1] * (n // 2 + 2)
                f.put(sx, base + 1, sz, block("smooth_quartz"))
                if R.at(x, z, 176) < 0.6:
                    f.put(sx, base + 2, sz, block("quartz_pillar", axis="y"))
            elif r < 0.52:
                kind = "wall"
                d = (1, 0) if R.at(x, z, 177) < 0.5 else (0, 1)
                n = 3 + int(R.at(x, z, 178) * 4)
                for k in range(-(n // 2), n - n // 2):
                    h = max(1, int(4 * R.at(x, k, z, 179) ** 0.8))
                    for y in range(base + 1, base + 1 + h):
                        f.put(x + d[0] * k, y, z + d[1] * k, block(R.pick(TEMPLE_WALL, x + k, y, z, 180)))
                # 曲がり角
                if R.at(x, z, 181) < 0.5:
                    ex, ez = x + d[0] * (n - n // 2 - 1), z + d[1] * (n - n // 2 - 1)
                    for k in range(1, 3):
                        cx, cz = ex + d[1] * k, ez + d[0] * k
                        for y in range(base + 1, base + 1 + max(1, 3 - k)):
                            f.put(cx, y, cz, block(R.pick(TEMPLE_WALL, cx, y, cz, 182)))
            elif r < 0.72:
                kind = "rubble"
                for dx in range(-1, 2):
                    for dz in range(-1, 2):
                        h = 2 if dx == dz == 0 else (1 if R.at(x + dx, z + dz, 183) < 0.7 else 0)
                        for y in range(base + 1, base + 1 + h):
                            f.put(x + dx, y, z + dz, block(R.pick(RUBBLE, x + dx, y, z + dz, 184)))
                for (dx, dz) in ((2, 0), (-2, 1), (0, 2), (1, -2)):
                    if R.at(x + dx, z + dz, 185) < 0.5:
                        f.put(x + dx, base + 1, z + dz, slab(("cobblestone", "andesite", "stone_brick")[int(R.at(dx, dz, 186) * 3) % 3]))
            elif r < 0.94:
                kind = "tree"
                small_tree(f, x, z, base, "birch" if R.at(x, z, 187) < 0.45 else "azalea")
            else:
                kind = "statue"
                # 倒れた顔のない像（横たわる体と、離れて転がる頭）
                d = (1, 0) if R.at(x, z, 188) < 0.5 else (0, 1)
                for k in range(3):
                    f.put(x + d[0] * k, base + 1, z + d[1] * k, block("calcite"))
                f.put(x + d[0] * 4, base + 1, z + d[1] * 4 + (1 if d[0] else 0), block("smooth_quartz"))
                f.put(x - d[0], base + 1, z - d[1], block("polished_andesite"))
            count[kind] = count.get(kind, 0) + 1
    return count


def sky_rubble(f):
    """空に浮かぶ瓦礫（4階の目じるし。ボスの部屋の上は空ける）"""
    cx, cz = ARENA
    for k in range(48):
        bx = 20 + int(R.at(k, 1, 191) * (SIZE - 40))
        bz = 20 + int(R.at(k, 2, 191) * (SIZE - 40))
        if math.hypot(bx - cx, bz - cz) < ARENA_R + 10:
            continue
        if FLOAT[0] <= bx <= FLOAT[2] and FLOAT[1] <= bz <= FLOAT[3]:
            continue
        by = GROUND + 18 + int(R.at(k, 3, 191) * 26)
        size = 1 + int(R.at(k, 4, 191) * 3)
        for x in range(bx - size, bx + size + 1):
            for z in range(bz - size, bz + size + 1):
                for y in range(by - size // 2 - 1, by + 1):
                    if math.hypot(x - bx, z - bz) <= size + 0.3 - (by - y) * 0.6 and R.at(x, y, z, 192) < 0.88:
                        if f.v[x, y, z] == 0:
                            f.put(x, y, z, block(R.pick(RUBBLE if R.at(k, 5) < 0.6 else TEMPLE_WALL, x, y, z, 193)))
        # 上に草（浮かぶ島のてっぺん）
        if size >= 2 and R.at(k, 6, 191) < 0.5:
            for x in range(bx - size + 1, bx + size):
                for z in range(bz - size + 1, bz + size):
                    if f.v[x, by, z] != 0 and f.v[x, by + 1, z] == 0:
                        f.put(x, by, z, block("grass_block"))


# ================================================================ 灯・ロスト

def lost(f):
    blank = f.idx(block("amemaru:lost_blank"))
    for (x0, z0, x1, z1), (y0, y1) in f.lost_zones:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    i = f.v[x, y, z]
                    if i == 0:
                        continue
                    if R.at(x, y, z, 141) < 0.25:
                        f.v[x, y, z] = blank


def ambient_lights(f):
    """夜でも真っ暗にならないよう、歩ける所に見えない灯（12）を8マスおきに。地下と高い段も"""
    for (base, x0, z0, x1, z1) in ((GROUND, 0, 0, SIZE - 1, SIZE - 1), (LOW, CRYPT[0], CRYPT[1], CRYPT[2], CRYPT[3]),
                                   (HIGH, TERRACE[0], TERRACE[1], TERRACE[2], TERRACE[3]), (CHASM, FLOAT[0], FLOAT[1], FLOAT[2], FLOAT[3])):
        y = base + 3
        for x in range(x0 + 2, x1 - 1, 8):
            for z in range(z0 + 2, z1 - 1, 8):
                if f.v[x, y, z] == 0 and f.v[x, y - 1, z] == 0 and f.v[x, base, z] != 0:
                    f.put(x, y, z, block("light", level=12 if base != LOW else 13, waterlogged=False))


# ================================================================ 階の定義

def spawn_zones(f):
    def zone(id_, enemy, cx, y, cz, radius, max_, respawn, group, activation=48, route=None):
        z = {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
             "respawn_seconds": respawn, "group": list(group), "activation": activation}
        if route:
            z["route"] = [[x, y, zz] for (x, zz) in route]
        return z
    g = GROUND + 1
    lo = LOW + 1
    hi = HIGH + 1
    return [
        # 参道：石像（眠っている）と、聖獣
        zone("avenue_statues_s", "silent_statue", 264, g, 450, 9, 4, 70, (2, 3)),
        zone("avenue_statues_m", "silent_statue", 264, g, 400, 9, 4, 70, (2, 3)),
        zone("avenue_statues_n", "silent_statue", 264, g, 340, 9, 4, 70, (2, 3)),
        zone("avenue_beasts", "broken_beast", 220, g, 380, 10, 3, 60, (1, 2)),
        zone("avenue_ruins_e", "broken_beast", 310, g, 356, 10, 2, 60, (1, 2)),
        # 前庭：聖獣と祈り手
        zone("plaza_beasts", "broken_beast", 240, g, 230, 12, 3, 60, (1, 2)),
        zone("plaza_echoes", "prayer_echo", 290, g, 270, 10, 3, 55, (1, 2)),
        zone("plaza_statues", "silent_statue", 236, g, 272, 6, 2, 70, (1, 1)),
        # 壁画の回廊：祈り手と石像
        zone("gallery_echoes_w", "prayer_echo", 340, g, 250, 10, 3, 55, (1, 2)),
        zone("gallery_echoes_e", "prayer_echo", 420, g, 250, 10, 3, 55, (1, 2)),
        zone("gallery_statues", "silent_statue", 380, g, 260, 8, 3, 70, (1, 2)),
        # 祈りの間：祈り手の群れと甲虫
        zone("chapel_echoes_a", "prayer_echo", 390, g, 148, 10, 4, 50, (2, 3)),
        zone("chapel_echoes_b", "prayer_echo", 450, g, 160, 10, 4, 50, (2, 3)),
        zone("chapel_beetles", "crystal_beetle", 420, g, 185, 10, 3, 55, (1, 2)),
        # 浮石の庭：甲虫（島ごと）
        zone("float_beetles_a", "crystal_beetle", 150, g, 290, 7, 3, 55, (1, 2)),
        zone("float_beetles_b", "crystal_beetle", 96, g, 312, 7, 3, 55, (1, 2)),
        zone("float_beetles_c", "crystal_beetle", 130, g, 352, 8, 3, 55, (1, 2)),
        zone("float_beetles_d", "crystal_beetle", 60, g, 270, 7, 2, 55, (1, 2)),
        zone("float_echoes", "prayer_echo", 118, g, 254, 7, 2, 55, (1, 1)),
        zone("chasm_beetles", "crystal_beetle", 120, CHASM + 1, 300, 20, 3, 70, (1, 2)),
        # 聖獣の庭：聖獣と石像
        zone("terrace_beasts_w", "broken_beast", 80, hi, 120, 14, 3, 55, (1, 2)),
        zone("terrace_beasts_e", "broken_beast", 170, hi, 130, 14, 3, 55, (1, 2)),
        zone("terrace_statues", "silent_statue", 125, hi, 70, 10, 3, 70, (1, 2)),
        zone("terrace_echoes", "prayer_echo", 125, hi, 170, 10, 2, 55, (1, 2)),
        # 納骨堂：石像と甲虫
        zone("crypt_statues_s", "silent_statue", 265, lo, 400, 5, 3, 70, (1, 2), 32),
        zone("crypt_statues_n", "silent_statue", 265, lo, 340, 5, 3, 70, (1, 2), 32),
        zone("crypt_beetles", "crystal_beetle", 240, lo, 370, 8, 3, 55, (1, 2), 32),
        zone("crypt_echoes", "prayer_echo", 284, lo, 358, 8, 2, 55, (1, 1), 32),
        # 鐘楼：甲虫と聖獣
        zone("tower_beetles", "crystal_beetle", 380, g, 410, 10, 3, 55, (1, 2)),
        zone("tower_beasts", "broken_beast", 440, g, 350, 10, 2, 60, (1, 2)),
        zone("tower_echoes", "prayer_echo", 406, g + 18, 376, 6, 2, 55, (1, 1), 32),
        # 石の道：石像
        zone("causeway_statues", "silent_statue", 264, g, 175, 8, 3, 70, (1, 2)),
    ]


def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    data = {
        "_comment": "塔の4階「崩壊した神殿」（tools/mapgen/build_floor4.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 1,
        "name": "崩壊した神殿",
        "chapter_title": "第1章　失われた古代領域",
        "recommended_level": [15, 22],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor4/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, GROUND + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, GROUND + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:cantare",
            "name": "歌の大広間",
            "spawn": {"pos": [cx + 0.5, GROUND + 2, cz + 0.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, GROUND + 1, cz + 0.5], "radius": ARENA_R - 1.0, "y_min": GROUND - 2, "y_max": GROUND + 20},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 22,
            "respawn_seconds": 120,
            "max_players": 8,
            # 11月24日「ソロでは全然倒せない」：ひとりでも2人ぶんのHP【補完】
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1680,
            "exit": {"center": [cx + 0.5, GROUND + 1, cz - 19.5], "radius": 2.6},
            "next": {"center": [cx + NEXT_DX + 0.5, GROUND + 1, cz - 19.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "",
        "tablet": {"pos": list(f.tablet), "title": "神殿の碑文", "text": "われらは神を祀らない。祀るのは、人と人の間にあるものである。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 金色にかすむ、崩れた神殿の空（昼は明るく、遠くはかすむ）
        "environment": {"fog": [30, 170], "fog_color": "#CDBF96"},
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        # 3つの壁画をすべて戻し（みんなの分）、名のある魔物を3体とも倒す（人ごと）と、封印が開く【補完】
        "seal_requires": "murals+guardians",
        "murals": f.murals,
        "songs": f.songs,
        "relics": f.relics,
        "boss_marks": {"sound_plates": f.plates},
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
    floating_garden(f)
    crypt(f)
    terrace(f)
    entrance(f)
    avenue(f)
    plaza(f)
    causeway(f)
    gallery(f)
    chapel(f)
    bell_tower(f)
    crypt_access(f)
    antechamber(f)
    arena(f)
    paths(f)
    f.scatter = scatter_ruins(f)
    sky_rubble(f)
    scatter_grass(f, 3, 3, SIZE - 4, SIZE - 4, y=GROUND, density=0.1, salt=201)
    lost(f)
    ambient_lights(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor4") if out else os.path.join(RES, "structures", "map", "floor4")
    json_path = os.path.join(out, "floor4.json") if out else os.path.join(RES, "amemaru", "floors", "floor4.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("散らばる飾り:", f.scatter)
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "壁画:", [m["id"] for m in f.murals])
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor4_figures
        floor4_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
