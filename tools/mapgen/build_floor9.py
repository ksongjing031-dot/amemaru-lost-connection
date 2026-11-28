"""塔の9階「氷結した王城」の地形と城を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor9.json）を書き出す。

Phase 0-A 9章の9階（確定）：凍った王城。大広間、凍った兵士、王家の書庫。王家の肖像画の顔はすべて空白。雑魚は凍れる近衛兵・氷の騎兵・
書架霊（ページが舞う亡霊。魔法攻撃）・霜の猟犬・忘れられた侍女。ボスは氷の近衛長ヴァルグリム（今も女王を守っている近衛隊長の残響。
部下を守る「盾の誓い」を使うため、部下と引き離すと崩れる）。ギミックは王家の系譜（城中に散らばった名前の札を集め、肖像画の空白に正しく
戻す）。古代記録「女王の書簡（三通）」。9日目。ロストの侵食：名前と顔の空白。22章：9階の隠し部屋にある女王の書簡3通。

使い方:
  python3 tools/mapgen/build_floor9.py                        # Modの structures/map/floor9/ と floors/floor9.json を書き直す
  python3 tools/mapgen/build_floor9.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

決まり（11月28日【補完】。docs/phase8_castle.md）：
  - 768×768（8階より広い）、高さ112。地面は y30（立つのは 31）。城の2階の床は y40、屋根（城壁の上）は y50、塔の上は y66
  - 区画：入口 → 城下町の跡 → 堀と跳ね橋 → 門楼 → 外庭（凍った兵士の隊列・噴水・厩舎・犬舎）→ 西の練兵場（名のある魔物「霜蹄の旗手」）・
    東の王家の庭園 → 本館1階（大広間・兵舎と武器庫・厨房と使用人の間・北の宝物庫跡）→ 2階（礼拝堂・王家の書庫と奥の書庫（名のある魔物
    「綴じ手」）・肖像の回廊）→ 近衛の回廊 → 近衛の間（ボス）。4つの角の塔と、屋根の上の城壁の道。肖像の回廊の東の端の女王の肖像の奥に、
    隠し部屋「女王の書斎」（女王の書簡3通）
  - 王家の系譜（lineage）：肖像画の組が3つ（大広間の建国の肖像3・礼拝堂の歌と鷹の肖像3・肖像の回廊の最後の家族3）。名前の札は城じゅうの
    札の台（11。王家の9人と、まぎらわしい札2つ）。手がかりは大広間の建国の碑と、書庫の系譜の書。組を戻すと、大階段の氷の門（2階へ）・
    回廊の扉が開く。封印は「系譜がすべて戻った（みんな）＋名のある魔物2体（人ごと）」（lineage+guardians）
  - 女王の肖像は札がない。最後の家族が戻ったあと、肖像の前で女王の名を呼ぶ（チャット）と、少しの間だけ奥の扉が開く
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapgen.world import DATA_VERSION, block, lectern_nbt, sign_nbt  # noqa: E402
from mapgen.tower import NEXT_DX, next_circle  # noqa: E402

import nbtlib  # noqa: E402
from nbtlib.tag import Compound, Float, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

FLOOR = 9
SIZE = 768
HEIGHT = 112
PIECE = 48
ORIGIN = (36864 - SIZE // 2, 60, -SIZE // 2)
G = 30                  # 地面・1階の床のブロック（立つのは 31）
F2 = G + 10             # 2階の床のブロック（立つのは 41）
F3 = G + 20             # 屋根（城壁の上の道）の床のブロック（立つのは 51）
TOP = G + 36            # 塔の上の床のブロック
CHEST_LOOT = "amemaru:chests/floor9_castle"

ENTRY = (384, 744)
MOAT = (586, 600)                   # 堀（z0, z1）。底は y8。落ちると中継の灯か入口へ戻される（abyss_y）
MOAT_FLOOR = G - 22
BRIDGE = (379, 389)                 # 跳ね橋（x0, x1）
GATE = (356, 563, 412, 585)         # 門楼（x0, z0, x1, z1）
BAILEY = (172, 454, 596, 561)       # 外庭の中（x0, z0, x1, z1）。まわりは高い城壁
TRAIN = (44, 330, 160, 560)         # 西の練兵場
GARDEN = (608, 330, 724, 560)       # 東の王家の庭園
KEEP = (248, 232, 520, 453)         # 本館（外の壁の面。壁は2つの厚さ）
HALL = (348, 420)                   # 大広間の東西の壁（壁の外の面。中は 350〜418）
WEST_CORR = (340, 347)              # 西の翼の廊下（x0, x1）
EAST_CORR = (421, 428)              # 東の翼の廊下
ROOMS_Z = [(300, 336), (338, 374), (376, 412), (414, 451)]   # 翼の部屋（南北）
NORTH_Z = (234, 297)                # 北の棟の中（1階・2階）
STAIR = (375, 393)                  # 大階段（x0, x1）。z333 から北へ上る（z324 で 2階）
LANDING_Z = (300, 323)              # 2階の踊り場（大広間の北の上）
GALLERY = (262, 488, 234, 254)      # 肖像の回廊の中（x0, x1, z0, z1）。北の壁は本館の北の壁
STUDY = (493, 517, 246, 272)        # 女王の書斎（隠し部屋）の中。東の壁は本館の東の壁（窓から庭園が見える）
ARENA = (384, 152)                  # 近衛の間のまん中。床は地面と同じ G（近衛の回廊が2階から下る）
ARENA_R = 44
TOWERS = {"sw": (248, 453), "se": (520, 453), "nw": (248, 232), "ne": (520, 232)}
TOWER_R = 12


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


R = Rand(20261128 + 9)

WALL_STONE = [("stone_bricks", 7), ("cracked_stone_bricks", 2), ("polished_andesite", 1)]
ROCK = [("stone", 5), ("andesite", 2), ("tuff", 1), ("calcite", 1)]
CLIFF = [("packed_ice", 3), ("stone", 4), ("snow_block", 2), ("andesite", 1)]


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
        self.chests = []
        self.waypoints = []
        self.guardians = []
        self.seal_cells = []
        self.story_zones = []
        self.relics = []
        self.statues = []
        self.portraits = []          # (id, group, pos, facing)
        self.plates = []             # (id, pos)
        self.clues = []              # (id, pos)
        self.doors = {}              # group → box
        self.queen = None            # (pos, facing)
        self.queen_door = None       # box
        self.latch = None
        self.travel_stone = None
        self.exit_circle = None
        self.boss_marks = {}
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

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        xa, xb = sorted((x0, x1))
        ya, yb = sorted((y0, y1))
        za, zb = sorted((z0, z1))
        xa, ya, za = max(0, xa), max(0, ya), max(0, za)
        xb, yb, zb = min(SIZE - 1, xb), min(HEIGHT - 1, yb), min(SIZE - 1, zb)
        if xa > xb or ya > yb or za > zb:
            return
        self.v[xa:xb + 1, ya:yb + 1, za:zb + 1] = 0 if state is None else self.idx(state)
        if self.nbt:
            for key in [k for k in self.nbt if xa <= k[0] <= xb and ya <= k[1] <= yb and za <= k[2] <= zb]:
                self.nbt.pop(key, None)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)


# ================================================================ 小さな部品

def stairs(mat, facing, half="bottom"):
    return block(mat + "_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(mat, kind="bottom"):
    return block(mat + "_slab", type=kind, waterlogged=False)


def wall_block(mat):
    return block(mat + "_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False)


def pane(name):
    return block(name, north=False, south=False, east=False, west=False, waterlogged=False)


def fence(wood):
    return block(wood + "_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def lantern(hanging=False, soul=True):
    return block("soul_lantern" if soul else "lantern", hanging=hanging, waterlogged=False)


def light(level=12):
    return block("light", level=level, waterlogged=False)


def snow_layer(n=1):
    return block("snow", layers=n)


def candle(n=3, lit=True, color="white"):
    return block(color + "_candle", candles=n, lit=lit, waterlogged=False)


def sign(f, x, y, z, rotation, lines):
    f.put(x, y, z, block("spruce_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))


def wall_sign(f, x, y, z, facing, lines):
    f.put(x, y, z, block("spruce_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))


def chest(f, x, y, z, facing, guardian=""):
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing), thing(len(f.chests), ""))
    f.chests.append(((x, y, z), guardian))


def waypoint(f, wid, name, x, y, z, yaw):
    index = len(f.waypoints)
    f.put(x, y, z, block("amemaru:waypoint"), thing(index, wid))
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, y, z], "arrive": {"pos": [ax + 0.5, y, az + 0.5], "yaw": yaw}})


def statue(f, x, y, z, skin, pose, yaw, scale=1.0):
    """凍った人の像（6階と同じブロックの実体。顔は白く抜けている＝名前と顔の空白）"""
    tag = Compound({"id": String("amemaru:village_statue"), "skin": String(skin), "pose": String(pose), "yaw": Float(yaw),
                    "scale": Float(scale), "memory": String("")})
    f.put(x, y, z, block("amemaru:village_statue"), tag)
    f.statues.append((x, y, z, skin, pose, yaw, scale))


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


def stone(x, y, z, table=WALL_STONE, salt=0):
    return block(R.pick(table, x, y, z, salt))


def wall_x(f, x0, x1, z, y0, y1, salt=1, table=WALL_STONE):
    """東西の壁（x0〜x1、ある z）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(y0, y1 + 1):
            f.put(x, y, z, stone(x, y, z, table, salt))


def wall_z(f, z0, z1, x, y0, y1, salt=1, table=WALL_STONE):
    """南北の壁（z0〜z1、ある x）"""
    for z in range(min(z0, z1), max(z0, z1) + 1):
        for y in range(y0, y1 + 1):
            f.put(x, y, z, stone(x, y, z, table, salt))


def inner_wall_x(f, x0, x1, z, y0, y1):
    """部屋の間の壁（白い石。帯と腰板）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(y0, y1 + 1):
            k = y - y0
            st = "polished_diorite" if k == 0 else "quartz_bricks" if k == 4 else "calcite"
            f.put(x, y, z, block(st))


def inner_wall_z(f, z0, z1, x, y0, y1):
    for z in range(min(z0, z1), max(z0, z1) + 1):
        for y in range(y0, y1 + 1):
            k = y - y0
            st = "polished_diorite" if k == 0 else "quartz_bricks" if k == 4 else "calcite"
            f.put(x, y, z, block(st))


def opening(f, x0, z0, x1, z1, y0, h):
    """壁に口をあける（下から h 段）"""
    f.clear(x0, y0, z0, x1, y0 + h - 1, z1)


def arch_x(f, x0, x1, z0, z1, y0, h, mat="stone_brick"):
    """東西の壁の、アーチの口（x0〜x1。上の両端は階段で丸める）"""
    opening(f, x0, z0, x1, z1, y0, h)
    for z in range(min(z0, z1), max(z0, z1) + 1):
        f.put(x0, y0 + h - 1, z, stairs(mat, "east", "top"))
        f.put(x1, y0 + h - 1, z, stairs(mat, "west", "top"))


def arch_z(f, z0, z1, x0, x1, y0, h, mat="stone_brick"):
    """南北の壁の、アーチの口（z0〜z1）"""
    opening(f, x0, z0, x1, z1, y0, h)
    for x in range(min(x0, x1), max(x0, x1) + 1):
        f.put(x, y0 + h - 1, z0, stairs(mat, "south", "top"))
        f.put(x, y0 + h - 1, z1, stairs(mat, "north", "top"))


def floor_area(f, x0, z0, x1, z1, y, kind="hall"):
    """床を張る（kind：hall 白と灰の市松・wood 板・stone 石畳・library 濃い板）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if kind == "hall":
                st = "smooth_quartz" if (x // 2 + z // 2) % 2 == 0 else "polished_andesite"
            elif kind == "wood":
                st = "spruce_planks"
            elif kind == "library":
                st = "dark_oak_planks" if (x + z // 3) % 5 else "spruce_planks"
            elif kind == "chapel":
                st = "polished_diorite" if (x + z) % 2 else "calcite"
            else:
                st = R.pick([("stone_bricks", 5), ("polished_andesite", 3), ("cracked_stone_bricks", 1)], x, z, 7)
            f.put(x, y, z, block(st))


def frost(f, x0, z0, x1, z1, y, amount=0.08, salt=0):
    """床のすみに薄い雪と、ところどころの氷（凍った城）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if f.v[x, y, z] != 0:
                continue
            below = f.name_at(x, y - 1, z)
            if below is None or below.endswith("_slab") or below.endswith("_stairs") or below.endswith("carpet") \
                    or below.startswith("amemaru:") or below in ("snow", "light"):
                continue
            r = R.at(x, y, z, 300 + salt)
            if r < amount:
                f.put(x, y, z, snow_layer(1 if r < amount * 0.7 else 2))
            elif r < amount * 1.25:
                f.put(x, y - 1, z, block("ice") if R.at(x, z, 301) < 0.7 else block("packed_ice"))


def icicles(f, x0, z0, x1, z1, y_ceiling, amount=0.012, salt=0):
    """天井のつらら（光る白い棒。凍った城の灯りにもなる）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if R.at(x, z, 400 + salt) < amount and f.v[x, y_ceiling - 1, z] == 0 and f.v[x, y_ceiling, z] != 0:
                f.put(x, y_ceiling - 1, z, block("end_rod", facing="down"))


def chandelier(f, x, z, y_ceiling, drop=3, size=2):
    """氷の燭台（鎖で吊るした、氷と青い灯の輪）"""
    for k in range(1, drop + 1):
        f.put(x, y_ceiling - k, z, block("chain", axis="y", waterlogged=False))
    y = y_ceiling - drop - 1
    f.put(x, y, z, block("sea_lantern"))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        f.put(x + dx * size, y, z + dz * size, lantern(hanging=False))
        for s in range(1, size):
            f.put(x + dx * s, y, z + dz * s, block("packed_ice"))
    f.put(x, y - 1, z, block("end_rod", facing="down"))


def banner(f, x, y, z, facing, color="blue"):
    f.put(x, y, z, block(color + "_wall_banner", facing=facing))


def table(f, x0, z0, x1, z1, y, wood="spruce"):
    """長い机（四すみは皮をむいた丸太の脚、ほかは上の半ブロックの板）"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            corner = x in (x0, x1) and z in (z0, z1)
            f.put(x, y + 1, z, block("stripped_" + wood + "_log", axis="y") if corner else slab(wood, "top"))


def chair(f, x, y, z, facing, wood="spruce"):
    """いす（階段。背は facing の反対）"""
    f.put(x, y + 1, z, stairs(wood, facing))


def bed(f, x, y, z, facing, color="white"):
    """寝台（足 → 頭が facing）"""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    f.put(x, y + 1, z, block(color + "_bed", facing=facing, part="foot", occupied=False))
    f.put(x + dx, y + 1, z + dz, block(color + "_bed", facing=facing, part="head", occupied=False))


def bookshelf_wall(f, x0, z0, x1, z1, y0, h):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y0 + h):
                r = R.at(x, y, z, 500)
                f.put(x, y, z, block("bookshelf") if r < 0.86 else block("chiseled_bookshelf", facing="north",
                                                                            slot_0_occupied=False, slot_1_occupied=False,
                                                                            slot_2_occupied=False, slot_3_occupied=False,
                                                                            slot_4_occupied=False, slot_5_occupied=False)
                      if r < 0.93 else block("spruce_planks"))


def lamp_post(f, x, z, y, h=4):
    """外の灯（石の柱に青い灯。雪をかぶる）"""
    for k in range(h):
        f.put(x, y + 1 + k, z, wall_block("stone_brick") if k < h - 1 else block("polished_deepslate"))
    f.put(x, y + h + 1, z, lantern())
    f.put(x, y + h + 2, z, snow_layer(1))
    f.reserved[x, z] = True


def light_grid(f, x0, z0, x1, z1, y, step=7, level=11):
    """見えない灯（暗くなりすぎないように）"""
    for x in range(min(x0, x1) + step // 2, max(x0, x1), step):
        for z in range(min(z0, z1) + step // 2, max(z0, z1), step):
            if f.v[x, y, z] == 0:
                f.put(x, y, z, light(level))
                f.lights += 1


# ================================================================ 地形（歩ける所は平らな y30。ほかは切り立った雪の山）

def playable_mask():
    m = np.zeros((SIZE, SIZE), dtype=bool)

    def rect(x0, z0, x1, z1):
        m[x0:x1 + 1, z0:z1 + 1] = True

    rect(240, 601, 528, 762)        # 入口の広場と、城下町の跡
    rect(240, 567, 528, 585)        # 堀の北の岸（城壁の前）
    rect(166, 448, 602, 566)        # 外庭（まわりの城壁を含む）
    rect(44, 330, 166, 560)         # 西の練兵場
    rect(602, 330, 724, 560)        # 東の王家の庭園
    rect(234, 218, 534, 467)        # 本館と4つの塔
    rect(150, 350, 248, 360)        # 兵舎の通用口から練兵場への小道
    rect(520, 350, 618, 360)        # 使用人の通用口から庭園への小道
    rect(377, 190, 391, 234)        # 近衛の回廊（本館の北の壁から、近衛の間へ）
    xs = np.arange(SIZE) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    m |= np.hypot(X - ARENA[0], Z - ARENA[1]) < ARENA_R + 6
    return m


def terrain(f):
    n = SIZE
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    play = playable_mask()
    f.play = play
    # 山：歩ける所から離れるほど高い（切り立った崖から始まる）。上は雪と氷
    from scipy import ndimage  # noqa: PLC0415
    dist = ndimage.distance_transform_edt(~play)
    mtn = G + 16 + 22.0 * smoothstep(0.0, 40.0, dist) + 7.0 * smooth_noise(n, 29, 91) + 3.0 * smooth_noise(n, 9, 92)
    h = np.where(play, G, np.clip(np.round(mtn), G + 12, HEIGHT - 10)).astype(int)
    # 堀（z586〜600。城下町の北と、城壁の前の岸のあいだ）
    moat = (Z >= MOAT[0]) & (Z < MOAT[1] + 1) & (X >= 240) & (X < 529)
    h = np.where(moat, MOAT_FLOOR, h)
    f.moat = moat
    f.height = h


def fill_ground(f):
    """歩ける所は表の4段、山は表の3段と崖の面だけ埋める（構造物を小さくするため）"""
    h = f.height
    hmin = h.copy()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        hmin = np.minimum(hmin, np.roll(np.roll(h, dx, axis=0), dz, axis=1))
    for x in range(SIZE):
        for z in range(SIZE):
            top = int(h[x, z])
            bottom = max(1, min(top, int(hmin[x, z])) - 3)
            if f.moat[x, z]:
                # 堀の底：凍った水と岩
                for y in range(bottom, top):
                    f.put(x, y, z, block(R.pick(ROCK, x, y, z, 2)))
                f.put(x, top, z, block("packed_ice") if R.at(x, z, 3) < 0.7 else block("blue_ice"))
                continue
            if f.play[x, z]:
                for y in range(bottom, top):
                    f.put(x, y, z, block("stone") if y < top - 1 else block("dirt"))
                f.put(x, top, z, block("snow_block"))
                continue
            for y in range(bottom, top):
                f.put(x, y, z, block(R.pick(CLIFF, x, y, z, 4)))
            f.put(x, top, z, block("snow_block") if R.at(x, z, 5) < 0.8 else block("packed_ice"))
            if R.at(x, z, 6) < 0.25:
                f.put(x, top + 1, z, snow_layer(1 + int(R.at(x, z, 7) * 3)))


def edge_walls(f):
    """階のいちばん外の2列は、山の上からさらに高い壁（抜け道をふさぐ）"""
    n = SIZE
    for x in range(n):
        for z in range(n):
            if min(x, z, n - 1 - x, n - 1 - z) >= 2:
                continue
            top = int(f.height[x, z])
            for y in range(max(1, top - 2), min(HEIGHT - 3, top + 12)):
                f.put(x, y, z, block(R.pick(CLIFF, x, y, z, 7)))


# ================================================================ 入口・城下町の跡・堀と跳ね橋・門楼

def entrance(f):
    cx, cz = ENTRY
    return_circle(f, cx, G, cz)
    tx, tz = cx - 8, cz - 6
    f.put(tx, G + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    sign(f, tx + 1, G + 1, tz, 12, ["道しるべの石", "", "触れると、ともした", "中継の灯へ"])
    f.travel_stone = (tx, G + 1, tz)
    sign(f, cx + 2, G + 1, cz + 4, 8, ["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"])
    sign(f, cx + 5, G + 1, cz - 12, 0, ["9階", "氷結した王城", "", "名を失った城"])
    chest(f, cx + 11, G + 1, cz - 4, "west")
    for (dx, dz) in ((-10, -12), (10, -12), (-7, 4), (7, 4)):
        lamp_post(f, cx + dx, cz + dz, G)
    # 広場の石畳
    for x in range(cx - 16, cx + 17):
        for z in range(cz - 18, cz + 12):
            if f.play[x, z] and math.hypot(x - cx, z - cz) > 3.5:
                f.put(x, G, z, block("polished_andesite") if (x + z) % 3 else block("stone_bricks"))
    f.story_zones.append({"id": "enter_gate", "box": [cx - 16, G, cz - 18, cx + 16, G + 6, cz + 10]})


HOUSES = [
    # (x0, z0, 幅, 奥行き, 戸の向き)  城下町の家（凍って、屋根が半分落ちている）
    (262, 606, 12, 10, "south"), (280, 606, 10, 10, "south"), (300, 606, 14, 10, "south"), (322, 608, 10, 9, "south"),
    (396, 606, 12, 10, "south"), (414, 606, 10, 11, "south"), (432, 606, 12, 10, "south"), (452, 608, 14, 10, "south"),
    (478, 606, 12, 11, "south"), (262, 622, 12, 12, "north"), (300, 622, 14, 12, "north"), (340, 620, 10, 12, "east"),
    (420, 620, 10, 12, "west"), (452, 622, 14, 12, "north"), (486, 622, 12, 12, "north"),
    (262, 648, 14, 12, "south"), (284, 648, 12, 14, "south"), (330, 650, 12, 10, "east"), (430, 650, 12, 10, "west"),
    (470, 648, 14, 12, "south"), (494, 648, 12, 14, "south"),
    (262, 676, 12, 10, "north"), (282, 676, 14, 12, "north"), (452, 676, 14, 12, "north"), (476, 676, 12, 10, "north"),
    (262, 700, 12, 12, "south"), (286, 700, 12, 12, "south"), (318, 702, 12, 10, "east"), (440, 702, 12, 10, "west"),
    (466, 700, 12, 12, "south"), (492, 700, 12, 12, "south"),
]


def house(f, x0, z0, w, d, door, k):
    x1, z1 = x0 + w - 1, z0 + d - 1
    wall_h = 5
    collapsed = R.at(k, 1, 600) < 0.55
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            f.put(x, G, z, block("spruce_planks") if not edge else block("stone_bricks"))
            for y in range(G + 1, G + wall_h + 1):
                if edge:
                    corner = x in (x0, x1) and z in (z0, z1)
                    if corner:
                        st = block("stripped_spruce_log", axis="y")
                    elif y <= G + 2:
                        st = stone(x, y, z, salt=11)
                    else:
                        st = block("white_terracotta") if R.at(x, y, z, 601) < 0.85 else block("packed_ice")
                    if collapsed and y >= G + 4 and R.at(x, y, z, 602) < 0.35:
                        continue
                    f.put(x, y, z, st)
                else:
                    f.put(x, y, z, None)
            f.reserved[x, z] = True
    # 窓（凍った板ガラス）
    for y in (G + 3,):
        for x in range(x0 + 2, x1 - 1, 3):
            for z in (z0, z1):
                if f.name_at(x, y, z) not in (None, "stripped_spruce_log"):
                    f.put(x, y, z, pane("white_stained_glass_pane"))
        for z in range(z0 + 2, z1 - 1, 3):
            for x in (x0, x1):
                if f.name_at(x, y, z) not in (None, "stripped_spruce_log"):
                    f.put(x, y, z, pane("white_stained_glass_pane"))
    # 戸口
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    if door == "south":
        opening(f, mx, z1, mx + 1, z1, G + 1, 3)
    elif door == "north":
        opening(f, mx, z0, mx + 1, z0, G + 1, 3)
    elif door == "east":
        opening(f, x1, mz, x1, mz + 1, G + 1, 3)
    else:
        opening(f, x0, mz, x0, mz + 1, G + 1, 3)
    # 屋根（切妻。落ちた所は穴）
    ridge_along_x = w >= d
    span = d if ridge_along_x else w
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            off = (z - z0) if ridge_along_x else (x - x0)
            k2 = min(off + 1, span - off)
            y = G + wall_h + max(0, k2)
            if collapsed and R.at(x, z, 603) < 0.45:
                continue
            f.put(x, y, z, block("spruce_planks") if R.at(x, z, 604) < 0.7 else block("snow_block"))
    # 中：机・樽・凍った暖炉
    f.put(x0 + 1, G + 1, z0 + 1, block("barrel", facing="up", open=False))
    f.put(x1 - 1, G + 1, z0 + 1, block("blast_furnace", facing="south", lit=False))
    table(f, mx - 1, mz, mx + 1, mz, G)
    if R.at(k, 2, 605) < 0.5:
        f.put(x0 + 1, G + 1, z1 - 1, snow_layer(3))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if f.v[x, G + 1, z] == 0 and R.at(x, z, 606) < 0.12:
                f.put(x, G + 1, z, snow_layer(1))
    f.put(mx, G + wall_h - 1, mz, light(10))


def town(f):
    """城下町の跡：凍った家並み・市場の広場（凍った井戸と、凍った町の人たち）・まっすぐな大通り"""
    # 大通り（入口から跳ね橋まで）と横の通り
    for z in range(MOAT[1] + 1, ENTRY[1] - 12):
        for x in range(378, 391):
            f.put(x, G, z, block("polished_andesite") if (x + z) % 4 else block("stone_bricks"))
            f.reserved[x, z] = True
    for zc in (640, 690):
        for z in range(zc - 3, zc + 4):
            for x in range(256, 514):
                if f.play[x, z]:
                    f.put(x, G, z, block("stone_bricks") if (x + z) % 5 else block("cracked_stone_bricks"))
                    f.reserved[x, z] = True
    for k, (x0, z0, w, d, door) in enumerate(HOUSES):
        house(f, x0, z0, w, d, door, k)
    # 市場の広場
    mx, mz = 384, 665
    for x in range(mx - 14, mx + 15):
        for z in range(mz - 14, mz + 15):
            if math.hypot(x - mx, z - mz) < 14:
                f.put(x, G, z, block("polished_diorite") if (x + z) % 2 else block("calcite"))
                f.reserved[x, z] = True
    # 凍った井戸
    for x in range(mx - 2, mx + 3):
        for z in range(mz - 2, mz + 3):
            if max(abs(x - mx), abs(z - mz)) == 2:
                f.put(x, G + 1, z, block("stone_bricks"))
            else:
                f.put(x, G, z, block("blue_ice"))
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(G + 2, G + 5):
            f.put(mx + dx, y, mz + dz, fence("spruce"))
    for x in range(mx - 2, mx + 3):
        for z in range(mz - 2, mz + 3):
            f.put(x, G + 5, z, slab("spruce"))
    # 凍った町の人たち（市場の途中で、時が止まったまま）
    for (dx, dz, skin, pose, yaw) in ((-7, -4, "merchant", "haggle", 90.0), (-5, -4, "buyer", "haggle", 270.0),
                                      (6, 3, "mother", "turn", 200.0), (7, 5, "child_girl", "reach", 20.0),
                                      (-6, 7, "old_man", "stand", 160.0), (4, -8, "man", "point", 0.0)):
        statue(f, mx + dx, G + 1, mz + dz, skin, pose, yaw, 0.8 if skin.startswith("child") else 1.0)
    # 露店の屋台（雪をかぶった布）
    for (sx, sz) in ((mx - 10, mz - 6), (mx + 9, mz - 6), (mx - 10, mz + 6), (mx + 9, mz + 6)):
        for y in range(G + 1, G + 4):
            f.put(sx, y, sz, fence("spruce"))
            f.put(sx + 2, y, sz, fence("spruce"))
        for x in range(sx - 1, sx + 4):
            for z in range(sz - 1, sz + 2):
                f.put(x, G + 4, z, block("light_blue_wool") if (x + sz) % 2 else block("white_wool"))
        f.put(sx + 1, G + 1, sz, block("barrel", facing="up", open=False))
    for (lx, lz) in ((370, 620), (398, 620), (370, 700), (398, 700), (300, 640), (468, 640), (300, 690), (468, 690)):
        lamp_post(f, lx, lz, G)
    chest(f, 268, G + 1, 652, "east")
    chest(f, 498, G + 1, 704, "west")
    chest(f, 458, G + 1, 680, "south")
    f.story_zones.append({"id": "town", "box": [mx - 16, G, mz - 16, mx + 16, G + 6, mz + 16]})


def moat_and_bridge(f):
    """堀（落ちると、中継の灯か入口へ戻される）と、凍って下りたままの跳ね橋（鎖は門楼へ）"""
    x0, x1 = BRIDGE
    for z in range(MOAT[0] - 1, MOAT[1] + 2):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1)
            f.put(x, G, z, block("dark_oak_planks") if not edge else block("stripped_dark_oak_log", axis="z"))
            f.put(x, G - 1, z, block("dark_oak_slab", type="top", waterlogged=False))
            if edge:
                f.put(x, G + 1, z, fence("dark_oak"))
            f.reserved[x, z] = True
    # 鎖（門楼の上から、橋の先へ斜めに）
    for k in range(14):
        z = MOAT[1] - k
        y = G + 2 + k * 10 // 14
        for x in (x0, x1):
            f.put(x, y, z, block("chain", axis="z", waterlogged=False))
    # 堀の底の、凍った水のきらめき
    for x in range(242, 527):
        for z in range(MOAT[0], MOAT[1] + 1):
            if R.at(x, z, 610) < 0.01:
                f.put(x, MOAT_FLOOR + 1, z, block("end_rod", facing="up"))
    sign(f, x1 + 2, G + 1, MOAT[1] + 3, 8, ["跳ね橋", "", "落ちると、入口か", "ともした灯へ戻される"])


def gatehouse(f):
    """門楼：2つの丸い塔と、まん中の門の棟（上げたままの落とし格子）。中を抜けると外庭"""
    x0, z0, x1, z1 = GATE
    for (cx, cz) in ((364, 574), (404, 574)):
        for x in range(cx - 9, cx + 10):
            for z in range(cz - 9, cz + 10):
                d = math.hypot(x - cx, z - cz)
                if d < 8.5:
                    for y in range(G, G + 16):
                        f.put(x, y, z, stone(x, y, z, salt=20) if d >= 7.0 or y == G else None)
                    f.put(x, G + 16, z, block("deepslate_tiles") if d >= 6.0 else block("stone_bricks"))
                    if d >= 7.0 and R.at(x, z, 620) < 0.5:
                        f.put(x, G + 17, z, block("stone_brick_wall", up=True, north="none", south="none", east="none", west="none",
                                                   waterlogged=False))
                    f.reserved[x, z] = True
        for k in range(0, 360, 60):
            a = math.radians(k)
            wx, wz = int(round(cx + math.cos(a) * 7.5)), int(round(cz + math.sin(a) * 7.5))
            f.put(wx, G + 9, wz, pane("light_blue_stained_glass_pane"))
    # 門の棟
    for x in range(372, 397):
        for z in range(z0, z1 + 1):
            for y in range(G, G + 13):
                inside = 378 <= x <= 390 and y <= G + 9
                f.put(x, y, z, None if inside and y > G else stone(x, y, z, salt=21))
            f.put(x, G, z, block("stone_bricks"))
            f.put(x, G + 13, z, block("stone_brick_wall", up=True, north="none", south="none", east="none", west="none",
                                       waterlogged=False) if (x % 2 == 0 and z in (z0, z1)) else None)
            f.reserved[x, z] = True
    for z in (z0, z1):
        arch_x(f, 378, 390, z, z, G + 1, 9)
    # 落とし格子（上げたまま。上に格子の先が見える）
    for x in range(378, 391):
        for y in (G + 8, G + 9):
            f.put(x, y, (z0 + z1) // 2, block("iron_bars", north=False, south=False, east=True, west=True, waterlogged=False))
    for x in (379, 389):
        f.put(x, G + 6, z1 + 1, lantern())
        banner(f, x, G + 7, z1, "south")
    f.put(384, G + 7, (z0 + z1) // 2 + 3, light(12))
    f.put(384, G + 7, (z0 + z1) // 2 - 3, light(12))


# ================================================================ 名前の札の台・肖像画・手がかり

PLATES = {
    # 札: (名前, 組) 王家の9人と、まぎらわしい札2つ（王家ではない人の名前。肖像画にはまらない）
    "bertram": ("ベルトラム", "founding"), "rosalinde": ("ロザリンデ", "founding"), "edmund": ("エドムント", "founding"),
    "sigrid": ("シグリット", "chapel"), "halvard": ("ハルヴァルト", "chapel"), "albrecht": ("アルブレヒト", "chapel"),
    "oswin": ("オスヴィン", "family"), "elvira": ("エルヴィーラ", "family"), "liselotte": ("リーゼロッテ", "family"),
    "helga": ("ヘルガ", ""), "tobias": ("トビアス", ""),
}
GROUPS = [
    # (組, 名前, 開く扉の名前)
    ("founding", "建国の肖像", "stair_gate"),
    ("chapel", "歌と鷹の肖像", "gallery_door"),
    ("family", "最後の家族", ""),
]


def plate_stand(f, pid, x, y, z, facing="south"):
    """名前の札の台（右クリックで、その札をもらう）"""
    f.put(x, y, z, block("amemaru:name_plate_stand", facing=facing), thing(len(f.plates), "plate:" + pid))
    f.plates.append((pid, (x, y, z)))
    f.reserved[x, z] = True


def portrait(f, pid, group, x, y, z, facing):
    """肖像画（顔は空白）。ブロックは名札の台座（右クリックで札をはめる）で、絵はその上の壁に3×4で描く（block entity の絵）"""
    f.put(x, y, z, block("amemaru:royal_portrait", facing=facing, restored=False), thing(len(f.portraits), "portrait:" + pid))
    f.portraits.append((pid, group, (x, y, z), facing))


def clue(f, cid, kind, x, y, z, facing):
    """手がかり（建国の碑・系譜の書）。触れると、王家の人の名前と、肖像画に描かれた物が分かる"""
    f.put(x, y, z, block("amemaru:" + kind, facing=facing), thing(len(f.clues), "clue:" + cid))
    f.clues.append((cid, (x, y, z)))


# ================================================================ 外庭・練兵場・庭園

def bailey(f):
    """外庭：まわりの高い城壁、凍った噴水、凍った兵士の隊列（まじって、目を覚ます近衛兵）、厩舎と犬舎、西と東の門"""
    x0, z0, x1, z1 = BAILEY
    for x in range(166, 603):
        for z in range(448, 567):
            if f.play[x, z]:
                f.put(x, G, z, block("snow_block") if R.at(x, z, 700) < 0.85 else block("packed_ice"))
                f.reserved[x, z] = True
    top = G + 14
    # 城壁（西・東・南。北は本館と、塔へつなぐ壁）
    for x in range(166, 603):
        for z in range(448, 567):
            west = 166 <= x <= 171 and z >= 448
            east = 597 <= x <= 602 and z >= 448
            south = 563 <= z <= 566
            north = 448 <= z <= 452 and (x <= 238 or x >= 530)
            if not (west or east or south or north):
                continue
            if south and 356 <= x <= 412:
                continue        # 門楼
            for y in range(G, top + 1):
                f.put(x, y, z, stone(x, y, z, salt=30) if y > G + 1 else block("deepslate_bricks"))
            edge = x in (166, 602) or z in (566, 448)
            if edge and (x + z) % 2 == 0:
                f.put(x, top + 1, z, block("stone_brick_wall", up=True, north="none", south="none", east="none", west="none",
                                         waterlogged=False))
            f.put(x, top, z, block("snow_block") if not edge else stone(x, top, z, salt=31))
    # 西と東の門（練兵場・庭園へ）
    for (gx0, gx1) in ((166, 171), (597, 602)):
        arch_z(f, 499, 507, gx0, gx1, G + 1, 8)
        for z in range(499, 508):
            for x in range(gx0, gx1 + 1):
                f.put(x, G, z, block("stone_bricks"))
        for z in (498, 508):
            inner = gx0 - 1 if gx0 > 300 else gx1 + 1
            f.put(inner, G + 6, z, block("soul_wall_torch", facing="west" if gx0 > 300 else "east"))
    sign(f, 175, G + 1, 509, 4, ["西の練兵場", "", "近衛の騎兵が", "まだ走っている"])
    sign(f, 593, G + 1, 509, 12, ["東の王家の庭園", "", "姫たちの", "凍った庭"])
    # 凍った噴水（まん中の像の顔も空白）
    fx, fz = 384, 508
    for x in range(fx - 7, fx + 8):
        for z in range(fz - 7, fz + 8):
            d = math.hypot(x - fx, z - fz)
            if 5.5 <= d < 7.0:
                f.put(x, G + 1, z, block("polished_diorite"))
                f.put(x, G + 2, z, slab("smooth_quartz"))
            elif d < 5.5:
                f.put(x, G, z, block("blue_ice"))
                f.put(x, G + 1, z, block("ice") if R.at(x, z, 701) < 0.5 else None)
    for y in range(G + 1, G + 5):
        f.put(fx, y, fz, block("quartz_pillar", axis="y"))
    f.put(fx, G + 5, fz, block("packed_ice"))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        f.put(fx + dx, G + 4, fz + dz, block("ice"))
        f.put(fx + dx * 2, G + 3, fz + dz * 2, block("ice"))
    statue(f, fx, G + 6, fz, "noble_woman", "pray", 0.0, 0.9)
    plate_stand(f, "rosalinde", fx, G + 1, fz + 6, "south")
    # 凍った兵士の隊列（3列×8。顔は空白の兜。ところどころの空いた所に、目を覚ます近衛兵が並ぶ）
    for row, z in enumerate((526, 531, 536)):
        for k in range(8):
            x = 366 + k * 5
            if (row, k) in ((0, 2), (1, 5), (2, 1), (2, 6)):
                continue        # 近衛兵が並ぶ所（spawns の soldiers）
            statue(f, x, G + 1, z, "royal_guard", "guard", 180.0)
    f.story_zones.append({"id": "soldiers", "box": [360, G, 520, 408, G + 6, 542]})
    for x in (200, 568):
        banner(f, x, G + 9, 563 - 1, "north")
    # 厩舎（西の城壁ぞい。東は開いている）
    stables(f, 182, 466, 250, 550)
    # 犬舎（東の城壁ぞい。柵の囲いと犬小屋）
    kennels(f, 518, 466, 586, 550)
    for (lx, lz) in ((300, 470), (468, 470), (300, 545), (468, 545), (230, 500), (538, 500)):
        lamp_post(f, lx, lz, G)
    chest(f, 330, G + 1, 470, "south")
    chest(f, 440, G + 1, 470, "south")
    # 本館の前の石段と石畳
    for x in range(360, 409):
        for z in range(454, 470):
            f.put(x, G, z, block("polished_andesite") if (x + z) % 3 else block("stone_bricks"))
    for k, z in enumerate((454, 455, 456)):
        for x in range(366, 403):
            f.put(x, G + 1, z, stairs("stone_brick", "north") if k == 2 else block("stone_bricks"))
            if k == 0:
                f.put(x, G + 2, z, slab("stone_brick"))
    waypoint(f, "gatehouse", "門楼の内", 384, G + 1, 556, 180)


def stables(f, x0, z0, x1, z1):
    roof = G + 7
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.put(x, G, z, block("spruce_planks") if (x + z) % 4 else block("hay_block", axis="y") if R.at(x, z, 710) < 0.1
                  else block("spruce_planks"))
            back = x == x0
            for y in range(G + 1, roof):
                if back or z in (z0, z1):
                    f.put(x, y, z, stone(x, y, z, salt=32) if y < G + 3 else block("spruce_planks"))
            f.put(x, roof, z, block("spruce_planks") if R.at(x, z, 711) < 0.8 else block("snow_block"))
            f.put(x, roof + 1, z, snow_layer(2))
    # 馬房の仕切り（柵）と飼い葉
    for z in range(z0 + 6, z1, 8):
        for x in range(x0 + 1, x0 + 12):
            f.put(x, G + 1, z, fence("spruce"))
        f.put(x0 + 2, G + 1, z + 3, block("hay_block", axis="y"))
        f.put(x0 + 1, G + 1, z + 4, block("cauldron"))
    for z in range(z0, z1 + 1, 7):
        for y in range(G + 1, roof):
            f.put(x1, y, z, block("stripped_spruce_log", axis="y"))
    plate_stand(f, "tobias", x0 + 3, G + 1, z0 + 30, "east")
    sign(f, x0 + 4, G + 1, z0 + 28, 4, ["厩舎", "", "氷の馬は", "主を待っている"])
    for z in range(z0 + 4, z1, 12):
        f.put(x0 + 6, roof - 1, z, lantern(hanging=True))


def kennels(f, x0, z0, x1, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            f.put(x, G, z, block("coarse_dirt") if R.at(x, z, 720) < 0.3 else block("snow_block"))
            if x in (x0, x1) or z in (z0, z1):
                if not (x == x0 and z0 + 36 <= z <= z0 + 46):
                    f.put(x, G + 1, z, fence("spruce"))
                    f.put(x, G + 2, z, fence("spruce"))
    for (hx, hz) in ((x1 - 8, z0 + 6), (x1 - 8, z0 + 24), (x1 - 8, z0 + 60), (x1 - 8, z0 + 76)):
        for x in range(hx - 2, hx + 3):
            for z in range(hz - 2, hz + 3):
                edge = max(abs(x - hx), abs(z - hz)) == 2
                for y in range(G + 1, G + 4):
                    if edge and not (x == hx - 2 and z == hz):
                        f.put(x, y, z, block("spruce_planks"))
                f.put(x, G + 4, z, block("dark_oak_slab", type="bottom", waterlogged=False))
        f.put(hx, G + 1, hz, block("white_carpet"))
    for k in range(6):
        f.put(x0 + 10 + k * 8, G + 1, z0 + 40, block("bone_block", axis="y"))
    sign(f, x0 - 1, G + 1, z0 + 34, 12, ["犬舎", "", "霜の猟犬に", "気をつけて"])


def training_ground(f):
    """西の練兵場：的・木の人形・弓の的の並び・武器小屋・物見台。名のある魔物「霜蹄の旗手」"""
    x0, z0, x1, z1 = TRAIN
    for x in range(x0, x1 + 7):
        for z in range(z0, z1 + 1):
            if f.play[x, z]:
                f.put(x, G, z, block("coarse_dirt") if R.at(x, z, 730) < 0.25 else block("snow_block"))
                f.reserved[x, z] = True
    # 柵のふち（山の崖の前）
    for z in range(z0, z1 + 1):
        f.put(x0, G + 1, z, fence("spruce"))
    for x in range(x0, x1 + 1):
        f.put(x, G + 1, z0, fence("spruce"))
        f.put(x, G + 1, z1, fence("spruce"))
    # 木の人形（柵の柱に干し草と的）
    for k in range(10):
        dx = x0 + 14 + (k % 5) * 18
        dz = z0 + 30 + (k // 5) * 26
        f.put(dx, G + 1, dz, fence("spruce"))
        f.put(dx, G + 2, dz, block("hay_block", axis="y"))
        f.put(dx, G + 3, dz, block("target", power=0))
        f.put(dx - 1, G + 2, dz, fence("spruce"))
        f.put(dx + 1, G + 2, dz, fence("spruce"))
    # 弓の的の並び（西の端）
    for z in range(z0 + 100, z0 + 200, 9):
        for y in range(G + 1, G + 4):
            f.put(x0 + 3, y, z, block("target", power=0) if y == G + 2 else block("hay_block", axis="y"))
    # 武器小屋
    sx, sz = x0 + 8, z1 - 30
    for x in range(sx, sx + 14):
        for z in range(sz, sz + 12):
            edge = x in (sx, sx + 13) or z in (sz, sz + 11)
            f.put(x, G, z, block("spruce_planks"))
            for y in range(G + 1, G + 5):
                if edge and not (z == sz and sx + 5 <= x <= sx + 7 and y <= G + 3):
                    f.put(x, y, z, block("spruce_planks") if y > G + 1 else stone(x, y, z, salt=33))
            f.put(x, G + 5, z, block("spruce_slab", type="bottom", waterlogged=False))
    for k in range(4):
        f.put(sx + 2 + k * 3, G + 1, sz + 10, block("grindstone", face="floor", facing="north"))
        f.put(sx + 2 + k * 3, G + 2, sz + 10, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    chest(f, sx + 12, G + 1, sz + 2, "west")
    # 物見台（南東の角。段の上に、名前の札）
    bx, bz = x1 - 30, z1 - 26
    for k in range(4):
        for x in range(bx, bx + 22):
            for z in range(bz + k * 3, bz + k * 3 + 3):
                for y in range(G + 1, G + 2 + k):
                    f.put(x, y, z, block("spruce_planks") if y == G + 1 + k else block("stone_bricks"))
    for x in range(bx, bx + 22, 3):
        for y in range(G + 5, G + 9):
            f.put(x, y, bz + 11, fence("spruce"))
    for x in range(bx, bx + 22):
        for z in range(bz + 9, bz + 13):
            f.put(x, G + 9, z, block("blue_wool") if x % 2 else block("white_wool"))
    plate_stand(f, "halvard", bx + 11, G + 5, bz + 10, "south")
    # 名のある魔物「霜蹄の旗手」の広場（凍った旗）
    lx, lz = 104, 430
    for x in range(lx - 14, lx + 15):
        for z in range(lz - 14, lz + 15):
            if math.hypot(x - lx, z - lz) < 14:
                f.put(x, G, z, block("packed_ice") if R.at(x, z, 731) < 0.3 else block("snow_block"))
                for y in range(G + 1, G + 4):
                    f.put(x, y, z, None)
    for y in range(G + 1, G + 10):
        f.put(lx, y, lz - 10, block("stripped_spruce_log", axis="y"))
    for y in range(G + 6, G + 10):
        f.put(lx + 1, y, lz - 10, block("blue_wool"))
        f.put(lx + 2, y, lz - 10, block("blue_wool") if y > G + 6 else block("white_wool"))
    chest(f, lx - 6, G + 1, lz - 8, "south", guardian="hoof_bearer")
    f.guardians.append({"id": "hoof_bearer", "enemy": "amemaru:ice_cavalry", "variant": "amemaru:ice_cavalry_bearer",
                        "pos": [lx + 0.5, G + 1, lz + 3.5], "respawn_seconds": 300, "activation": 30, "pillar": -1})
    sign(f, lx + 6, G + 1, lz + 12, 8, ["練兵場", "", "霜蹄の旗手が", "今も号令を待つ"])
    f.story_zones.append({"id": "training", "box": [x0, G, z0, x1, G + 8, z1]})
    # 兵舎の通用口からの小道
    for x in range(150, 249):
        for z in range(351, 360):
            f.put(x, G, z, block("stone_bricks") if (x + z) % 3 else block("snow_block"))
            for y in range(G + 1, G + 5):
                f.put(x, y, z, None)
            f.reserved[x, z] = True
    for x in range(160, 248, 14):
        lamp_post(f, x, 350, G, 3)


def garden(f):
    """東の王家の庭園：凍った生け垣の小道・凍った池・東屋（名前の札）・遊ぶ姫と王子の凍った像"""
    x0, z0, x1, z1 = GARDEN
    for x in range(x0 - 6, x1 + 1):
        for z in range(z0, z1 + 1):
            if f.play[x, z]:
                f.put(x, G, z, block("snow_block"))
                f.reserved[x, z] = True
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # 生け垣（雪をかぶったトウヒの葉）の格子と小道
    for x in range(x0 + 4, x1 - 3):
        for z in range(z0 + 4, z1 - 3):
            on_path = (x - x0) % 22 in (0, 1, 2) or (z - z0) % 22 in (0, 1, 2) or abs(x - cx) <= 2 or abs(z - cz) <= 2
            if on_path:
                f.put(x, G, z, block("polished_diorite") if (x + z) % 2 else block("calcite"))
                continue
            ring = ((x - x0) % 22 in (3, 21)) or ((z - z0) % 22 in (3, 21))
            if ring:
                f.put(x, G + 1, z, block("spruce_leaves", distance=1, persistent=True, waterlogged=False))
                f.put(x, G + 2, z, snow_layer(2))
            elif R.at(x, z, 740) < 0.08:
                f.put(x, G + 1, z, block("white_tulip") if R.at(x, z, 741) < 0.5 else block("lily_of_the_valley"))
    # 凍った池
    px, pz = cx, cz + 60
    for x in range(px - 9, px + 10):
        for z in range(pz - 7, pz + 8):
            d = math.hypot((x - px) / 9.0, (z - pz) / 7.0)
            if d < 1.0:
                f.put(x, G, z, block("ice") if d < 0.85 else block("packed_ice"))
                f.put(x, G + 1, z, None)
    # 東屋
    ax, az = cx, cz - 20
    for x in range(ax - 6, ax + 7):
        for z in range(az - 6, az + 7):
            d = max(abs(x - ax), abs(z - az))
            f.put(x, G + 1, z, block("smooth_quartz") if d <= 5 else None)
            if d == 5 and (x - ax) % 5 == 0 and (z - az) % 5 == 0:
                for y in range(G + 2, G + 7):
                    f.put(x, y, z, block("quartz_pillar", axis="y"))
            if d <= 6:
                f.put(x, G + 7, z, block("quartz_slab", type="bottom", waterlogged=False) if d == 6 else block("smooth_quartz"))
            if d <= 3:
                f.put(x, G + 8, z, block("quartz_slab", type="bottom", waterlogged=False) if d == 3 else block("smooth_quartz"))
    f.put(ax, G + 9, az, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    table(f, ax - 1, az, ax + 1, az, G + 1, "birch")
    plate_stand(f, "sigrid", ax, G + 2, az - 3, "south")
    f.put(ax, G + 6, az, lantern(hanging=True))
    # 遊ぶ子どもたちの凍った像（姫と王子。輪を回して遊んでいる）
    for (dx, dz, skin, pose, yaw) in ((-8, 10, "child_girl", "reach", 120.0), (-4, 13, "child_boy", "point", 300.0),
                                      (5, 11, "child_girl", "turn", 220.0)):
        statue(f, ax + dx, G + 1, az + dz, skin, pose, yaw, 0.8)
    for z in range(z0 + 40, z1 - 20, 40):
        for x in (x0 + 8, x1 - 8):
            lamp_post(f, x, z, G, 3)
    chest(f, x1 - 6, G + 1, z0 + 6, "west")
    chest(f, x0 + 10, G + 1, z1 - 8, "east")
    f.story_zones.append({"id": "garden", "box": [x0, G, z0, x1, G + 8, z1]})
    # 使用人の通用口からの小道
    for x in range(520, 619):
        for z in range(351, 360):
            f.put(x, G, z, block("stone_bricks") if (x + z) % 3 else block("snow_block"))
            for y in range(G + 1, G + 5):
                f.put(x, y, z, None)
            f.reserved[x, z] = True


# ================================================================ 本館：外の壁・床・屋根

def keep_shell(f):
    kx0, kz0, kx1, kz1 = KEEP
    f.clear(kx0, G + 1, kz0, kx1, F3 + 24, kz1)
    f.fill(kx0, G - 3, kz0, kx1, G - 1, kz1, block("stone"))
    f.fill(kx0, G, kz0, kx1, G, kz1, block("stone_bricks"))
    f.reserved[kx0 - 2:kx1 + 3, kz0 - 2:kz1 + 3] = True
    # 外の壁（2つの厚さ。足もとは黒い石の帯、2階の床の高さにも帯）
    for y in range(G, F3):
        for x in range(kx0, kx1 + 1):
            for z in (kz0, kz0 + 1, kz1 - 1, kz1):
                f.put(x, y, z, stone(x, y, z, salt=40) if G + 1 < y != F2 else block("polished_deepslate"))
        for z in range(kz0, kz1 + 1):
            for x in (kx0, kx0 + 1, kx1 - 1, kx1):
                f.put(x, y, z, stone(x, y, z, salt=41) if G + 1 < y != F2 else block("polished_deepslate"))
    # 2階の床（大広間の吹き抜けをのぞく）
    f.fill(kx0 + 2, F2, kz0 + 2, kx1 - 2, F2, kz1 - 2, block("spruce_planks"))
    f.clear(HALL[0] + 2, F2, LANDING_Z[1] + 1, HALL[1] - 2, F2, kz1 - 2)
    # 屋根（平ら。外のふちに胸壁）
    f.fill(kx0, F3, kz0, kx1, F3, kz1, block("stone_bricks"))
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            edge = x in (kx0, kx1) or z in (kz0, kz1)
            if edge:
                f.put(x, F3 + 1, z, stone(x, F3 + 1, z, salt=42) if (x + z) % 2 == 0 else None)
            elif R.at(x, z, 800) < 0.3 and not (HALL[0] - 1 <= x <= HALL[1] + 1):
                f.put(x, F3 + 1, z, snow_layer(1 + int(R.at(x, z, 801) * 2)))
    # 窓（1階と2階。外の壁に、淡い青のガラス）
    for y0 in (G + 3, F2 + 3):
        for x in range(kx0 + 10, kx1 - 9, 9):
            if HALL[0] - 2 <= x <= HALL[1] + 2 and y0 == G + 3:
                continue
            for z in (kz0, kz0 + 1, kz1 - 1, kz1):
                for y in range(y0, y0 + 4):
                    f.put(x, y, z, block("light_blue_stained_glass"))
                    f.put(x + 1, y, z, block("light_blue_stained_glass"))
        for z in range(kz0 + 10, kz1 - 9, 9):
            for x in (kx0, kx0 + 1, kx1 - 1, kx1):
                for y in range(y0, y0 + 4):
                    f.put(x, y, z, block("light_blue_stained_glass"))
                    f.put(x, y, z + 1, block("light_blue_stained_glass"))
    # 正面の入口（大広間へ。大きなアーチ）
    arch_z(f, kz1 - 1, kz1, 378, 390, G + 1, 9)
    for x in (376, 392):
        for y in range(G + 1, G + 11):
            f.put(x, y, kz1 + 1, block("quartz_pillar", axis="y"))
    for x in range(375, 394):
        f.put(x, G + 11, kz1 + 1, block("smooth_quartz"))
    for x in (373, 395):
        banner(f, x, G + 8, kz1 + 1, "south")
    for x in (380, 388):
        f.put(x, G + 6, kz1 + 1, block("soul_wall_torch", facing="south"))


# ================================================================ 大広間（1階。2階までの吹き抜け）

FOUNDERS = [("bertram", 350, 366, "east"), ("rosalinde", 350, 414, "east"), ("edmund", 418, 390, "west")]


def great_hall(f):
    hx0, hx1 = HALL[0] + 2, HALL[1] - 2      # 中 350〜418
    hz0, hz1 = 300, KEEP[3] - 2              # 中 300〜451
    # 東西の壁（2つの厚さ。屋根まで）と北の壁
    for x in (HALL[0], HALL[0] + 1, HALL[1] - 1, HALL[1]):
        inner_wall_z(f, 298, hz1, x, G + 1, F3 - 1)
    for z in (298, 299):
        inner_wall_x(f, HALL[0], HALL[1], z, G + 1, F3 - 1)
    floor_area(f, hx0, hz0, hx1, hz1, G, "hall")
    # 柱（2列。白い石英の芯に、氷がからみつく）
    for x in (357, 411):
        for z in range(336, 445, 12):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    f.put(x + dx, G + 1, z + dz, block("polished_deepslate"))
                    for y in range(G + 2, F3):
                        core = dx == 0 and dz == 0
                        f.put(x + dx, y, z + dz, block("quartz_pillar", axis="y") if core
                              else block("packed_ice") if R.at(x + dx, y, z + dz, 810) < 0.35 else block("smooth_quartz"))
                    f.put(x + dx, F3 - 1, z + dz, block("chiseled_quartz_block"))
    # 柱のあいだの壁に旗
    for z in range(342, 445, 12):
        banner(f, hx0, G + 9, z, "east")
        banner(f, hx1, G + 9, z, "west")
    # 絨毯（入口から大階段へ）
    for z in range(334, hz1 + 1):
        for x in range(382, 387):
            f.put(x, G + 1, z, block("blue_carpet") if 383 <= x <= 385 else block("white_carpet"))
    # 燭台（氷と青い灯）
    for z in (352, 380, 408, 436):
        chandelier(f, 384, z, F3, 4, 3)
    for z in (366, 422):
        chandelier(f, 368, z, F3, 5, 2)
        chandelier(f, 400, z, F3, 5, 2)
    # 宴の机（壁ぞい）
    for (z0, z1) in ((340, 356), (370, 386), (400, 416), (428, 444)):
        table(f, hx0 + 2, z0, hx0 + 3, z1, G, "dark_oak")
        table(f, hx1 - 3, z0, hx1 - 2, z1, G, "dark_oak")
        for z in range(z0 + 1, z1, 3):
            f.put(hx0 + 3, G + 2, z, candle(2 + z % 3, True, "light_blue"))
            f.put(hx1 - 3, G + 2, z, candle(2 + z % 3, True, "light_blue"))
    # 踊ったまま凍った貴族たち（顔は空白）
    for (ax, az, yaw) in ((368, 372, 90.0), (400, 372, 270.0), (368, 400, 90.0), (400, 400, 270.0), (372, 428, 90.0),
                          (396, 428, 270.0)):
        statue(f, ax, G + 1, az, "noble_man", "dance_lead", yaw)
        statue(f, ax + (2 if yaw == 90.0 else -2), G + 1, az, "noble_woman", "dance_follow", yaw + 180.0)
    # 大階段の下の、凍った近衛兵の像
    for x in (372, 396):
        statue(f, x, G + 1, 335, "royal_guard", "salute", 0.0)
    # 建国の肖像（顔の空白の肖像画3つ）と、建国の碑
    for pid, x, z, facing in FOUNDERS:
        portrait(f, pid, "founding", x, G + 2, z, facing)
        f.put(x, G + 1, z, block("polished_deepslate"))
    clue(f, "founding", "lineage_stele", 384, G + 1, 341, "south")
    # 大階段（z333 から北へ。z324 で2階の踊り場）
    sx0, sx1 = STAIR
    for k in range(10):
        z = 333 - k
        for x in range(sx0, sx1 + 1):
            for y in range(G + 1, G + 1 + k):
                f.put(x, y, z, block("stone_bricks"))
            f.put(x, G + 1 + k, z, stairs("polished_andesite", "north"))
        for x in (sx0 - 1, sx1 + 1):
            for y in range(G + 1, G + 2 + k):
                f.put(x, y, z, block("polished_diorite"))
            f.put(x, G + 2 + k, z, wall_block("polished_diorite"))
    # 2階の踊り場（大広間の北の上）：手すりと、大階段の上の氷の門（建国の肖像がそろうと開く）
    for x in range(hx0, hx1 + 1):
        if sx0 <= x <= sx1:
            continue
        f.put(x, F2 + 1, LANDING_Z[1], wall_block("polished_diorite"))
    for x in range(sx0, sx1 + 1):
        for y in range(F2 + 1, F2 + 7):
            f.put(x, y, LANDING_Z[1], block("blue_ice"))
    f.doors["stair_gate"] = [sx0, F2 + 1, LANDING_Z[1], sx1, F2 + 6, LANDING_Z[1]]
    floor_area(f, hx0, LANDING_Z[0], hx1, LANDING_Z[1], F2, "hall")
    # 1階の口（大広間 → 東西の廊下・北の宝物庫跡）
    for (z0, z1) in ((360, 363), (430, 433)):
        arch_z(f, z0, z1, HALL[0], HALL[0] + 1, G + 1, 5)
        arch_z(f, z0, z1, HALL[1] - 1, HALL[1], G + 1, 5)
    arch_x(f, 380, 388, 298, 299, G + 1, 6)
    # 2階の口（踊り場 → 礼拝堂・書庫）
    arch_z(f, 305, 309, HALL[0], HALL[0] + 1, F2 + 1, 5)
    arch_z(f, 305, 309, HALL[1] - 1, HALL[1], F2 + 1, 5)
    # 踊り場の下（1階）の灯りと、天井のつらら
    light_grid(f, hx0, 300, hx1, 322, G + 6, 8, 12)
    light_grid(f, hx0, 324, hx1, hz1, G + 10, 9, 13)
    # 11月28日：天井が高く、上の灯だけでは床がほとんど真っ暗だった（CI の写真で、肖像画の名札と敵が見えなかった）。頭の少し上にも灯
    light_grid(f, hx0, 324, hx1, hz1, G + 5, 7, 14)
    icicles(f, hx0, 324, hx1, hz1, F3, 0.01, 1)
    frost(f, hx0, hz0, hx1, hz1, G + 1, 0.05, 1)
    waypoint(f, "hall", "大広間", 370, G + 1, 446, 180)
    f.story_zones.append({"id": "hall", "box": [hx0, G, 330, hx1, F3, hz1]})


# ================================================================ 西の翼（兵舎）と東の翼（使用人）

def wing_rooms(f, side):
    """翼の1階：廊下と4つの部屋（西：兵舎・食堂・武器庫・隊長室／東：厨房・食料庫・洗濯場・侍女の部屋）"""
    if side == "west":
        cx0, cx1 = WEST_CORR
        part = cx0 - 1
        rx0, rx1 = KEEP[0] + 2, part - 1
    else:
        cx0, cx1 = EAST_CORR
        part = cx1 + 1
        rx0, rx1 = part + 1, KEEP[2] - 2
    floor_area(f, cx0, 300, cx1, 451, G, "stone")
    inner_wall_z(f, 298, 451, part, G + 1, F2 - 1)
    inner_wall_x(f, KEEP[0] + 2 if side == "west" else HALL[1] + 1, HALL[0] - 1 if side == "west" else KEEP[2] - 2, 298, G + 1, F2 - 1)
    inner_wall_x(f, KEEP[0] + 2 if side == "west" else HALL[1] + 1, HALL[0] - 1 if side == "west" else KEEP[2] - 2, 299, G + 1, F2 - 1)
    for (z0, z1) in ROOMS_Z[:-1]:
        inner_wall_x(f, rx0, rx1, z1 + 1, G + 1, F2 - 1)
    for (z0, z1) in ROOMS_Z:
        mid = (z0 + z1) // 2
        arch_z(f, mid - 1, mid + 1, part, part, G + 1, 4, "polished_diorite")
    # 北の棟への口
    arch_x(f, cx0 + 2, cx1 - 2, 298, 299, G + 1, 4, "polished_diorite")
    for z in range(306, 451, 12):
        f.put(cx0 + 3, F2 - 1, z, lantern(hanging=True))
    light_grid(f, cx0, 300, cx1, 451, G + 5, 6, 11)
    frost(f, cx0, 300, cx1, 451, G + 1, 0.06, 2 if side == "west" else 3)
    if side == "west":
        barracks(f, rx0, rx1, *ROOMS_Z[0])
        mess_hall(f, rx0, rx1, *ROOMS_Z[1])
        armory(f, rx0, rx1, *ROOMS_Z[2])
        captain_room(f, rx0, rx1, *ROOMS_Z[3])
    else:
        kitchen(f, rx0, rx1, *ROOMS_Z[0])
        pantry(f, rx0, rx1, *ROOMS_Z[1])
        laundry(f, rx0, rx1, *ROOMS_Z[2])
        maids_room(f, rx0, rx1, *ROOMS_Z[3])


def barracks(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "wood")
    for x in range(x0 + 2, x1 - 2, 6):
        for z in (z0 + 2, z1 - 3):
            bed(f, x, G, z, "south" if z == z0 + 2 else "north", "blue")
            f.put(x + 1, G + 1, z, block("barrel", facing="up", open=False))
    for x in range(x0 + 4, x1 - 4, 12):
        statue(f, x, G + 1, (z0 + z1) // 2, "royal_guard", "guard", 90.0)
    chest(f, x0 + 2, G + 1, (z0 + z1) // 2, "east")
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.07, 10)
    icicles(f, x0, z0, x1, z1, F2, 0.008, 10)


def mess_hall(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "wood")
    for k, z in enumerate(range(z0 + 6, z1 - 4, 8)):
        table(f, x0 + 10, z, x1 - 8, z + 1, G, "spruce")
        for x in range(x0 + 11, x1 - 8, 2):
            chair(f, x, G, z - 1, "north")
            chair(f, x, G, z + 2, "south")
    # 凍った暖炉（西の壁）
    for z in range(z0 + 12, z0 + 17):
        for y in range(G + 1, G + 6):
            f.put(x0, y, z, block("deepslate_bricks"))
    f.put(x0 + 1, G + 1, z0 + 14, block("campfire", lit=False, facing="east", signal_fire=False, waterlogged=False))
    f.put(x0 + 1, G + 2, z0 + 14, block("ice"))
    # 兵舎の通用口（西の外の壁。練兵場への小道）
    arch_z(f, 353, 357, KEEP[0], KEEP[0] + 1, G + 1, 5)
    sign(f, x0 + 3, G + 1, 351, 4, ["兵舎の通用口", "", "練兵場へ"])
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.06, 11)


def armory(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "stone")
    for z in range(z0 + 3, z1 - 2, 6):
        for x in range(x0 + 4, x1 - 4, 3):
            f.put(x, G + 1, z, block("grindstone", face="floor", facing="north") if x % 2 else block("anvil", facing="north"))
            f.put(x, G + 2, z, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    for x in range(x0 + 2, x1 - 1, 9):
        f.put(x, G + 1, z1 - 1, block("smithing_table"))
        f.put(x + 1, G + 1, z1 - 1, block("barrel", facing="up", open=False))
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.06, 12)


def captain_room(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "wood")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x0 + 6 <= x <= x0 + 26 and z0 + 8 <= z <= z0 + 28:
                f.put(x, G + 1, z, block("blue_carpet"))
    mx, mz = x0 + 16, z0 + 18
    table(f, mx - 3, mz - 1, mx + 3, mz + 1, G, "dark_oak")
    f.put(mx - 2, G + 2, mz, candle(3, True, "white"))
    plate_stand(f, "bertram", mx, G + 2, mz, "south")
    chair(f, mx, G, mz + 2, "north", "dark_oak")
    bookshelf_wall(f, x0, z0 + 4, x0, z1 - 4, G + 1, 4)
    bed(f, x1 - 3, G, z1 - 4, "north", "blue")
    banner(f, x1, G + 5, mz, "west")
    statue(f, mx + 6, G + 1, mz - 6, "royal_guard", "salute", 270.0)
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.05, 13)


def kitchen(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "stone")
    # かまどの壁（東）
    for z in range(z0 + 3, z1 - 2, 4):
        f.put(x1, G + 1, z, block("smoker", facing="west", lit=False))
        f.put(x1, G + 2, z, block("furnace", facing="west", lit=False))
        f.put(x1 - 1, G + 1, z + 2, block("cauldron"))
    for x in range(x0 + 8, x1 - 8):
        table(f, x, (z0 + z1) // 2 - 1, x, (z0 + z1) // 2 + 1, G, "spruce")
    for x in range(x0 + 10, x1 - 10, 5):
        f.put(x, G + 2, (z0 + z1) // 2, block("flower_pot"))
        for k in range(1, 3):
            f.put(x, F2 - k, (z0 + z1) // 2, block("chain", axis="y", waterlogged=False))
    for (dx, dz, pose, yaw) in ((12, 6, "serve", 180.0), (30, 12, "serve", 90.0), (48, 8, "sweep", 270.0)):
        statue(f, x0 + dx, G + 1, z0 + dz, "maid", pose, yaw)
    chest(f, x0 + 2, G + 1, z0 + 2, "south")
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.07, 14)
    icicles(f, x0, z0, x1, z1, F2, 0.01, 14)


def pantry(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "wood")
    for z in range(z0 + 3, z1 - 2, 5):
        for x in range(x0 + 3, x1 - 12):
            if (x - x0) % 9 in (0, 1):
                continue
            f.put(x, G + 1, z, block("barrel", facing="up", open=False) if R.at(x, z, 820) < 0.6 else block("brown_wool"))
            if R.at(x, z, 821) < 0.4:
                f.put(x, G + 2, z, block("barrel", facing="up", open=False))
    plate_stand(f, "edmund", x0 + 4, G + 1, z1 - 2, "north")
    # 使用人の通用口（東の外の壁。庭園への小道）
    arch_z(f, 353, 357, KEEP[2] - 1, KEEP[2], G + 1, 5)
    sign(f, x1 - 3, G + 1, 351, 12, ["使用人の通用口", "", "王家の庭園へ"])
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.06, 15)


def laundry(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "stone")
    for x in range(x0 + 4, x1 - 3, 6):
        f.put(x, G + 1, z0 + 3, block("water_cauldron", level=3))
        f.put(x + 1, G + 1, z0 + 3, block("white_wool"))
    for z in range(z0 + 10, z1 - 4, 7):
        for x in range(x0 + 4, x1 - 4):
            f.put(x, G + 4, z, block("chain", axis="x", waterlogged=False))
            if R.at(x, z, 830) < 0.4:
                f.put(x, G + 3, z, block("white_carpet") if R.at(x, z, 831) < 0.6 else block("light_blue_carpet"))
        for x in (x0 + 3, x1 - 3):
            for y in range(G + 1, G + 5):
                f.put(x, y, z, fence("spruce"))
    statue(f, x0 + 20, G + 1, z1 - 6, "maid", "sweep", 0.0)
    light_grid(f, x0, z0, x1, z1, G + 6, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.06, 16)


MAID_DIARY = [
    "侍女の日記\n\n寒い朝。暖炉の火が、また消えていた。姫さま――イゼルナさまが、窓の外の白いものを見ておられた。",
    "陛下となられたイゼルナさまは、毎晩、書き物机に向かわれる。わたしたちの名前を、ひとりずつ書き留めておられるのだ。",
    "今朝、廊下でお声をかけられた。「あなたは、だれだったかしら」。……わたしの名前を、お呼びにならなかった。",
    "それでも、わたしは覚えている。あのお方の名を。イゼルナさま。もし陛下がご自分の名を忘れても、だれかが呼べば、きっと――",
]


def maids_room(f, x0, x1, z0, z1):
    floor_area(f, x0, z0, x1, z1, G, "wood")
    for x in range(x0 + 3, x1 - 3, 5):
        bed(f, x, G, z1 - 3, "north", "white")
        f.put(x + 1, G + 1, z1 - 2, block("spruce_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False))
    plate_stand(f, "helga", x0 + 9, G + 2, z1 - 2, "north")
    f.put(x0 + 9, G + 1, z1 - 2, block("barrel", facing="up", open=False))
    # 侍女の日記（書見台。女王の名が読める）
    f.put(x0 + 6, G + 1, z0 + 6, block("lectern", facing="south", has_book=True, powered=False),
          lectern_nbt("侍女の日記", "名を忘れた侍女", MAID_DIARY))
    table(f, x0 + 12, z0 + 4, x0 + 18, z0 + 8, G, "birch")
    f.put(x0 + 15, G + 2, z0 + 6, candle(2, True, "white"))
    statue(f, x0 + 24, G + 1, z0 + 10, "maid", "serve", 200.0)
    statue(f, x0 + 40, G + 1, z0 + 16, "maid", "sweep", 120.0)
    light_grid(f, x0, z0, x1, z1, G + 5, 8, 11)
    frost(f, x0, z0, x1, z1, G + 1, 0.06, 17)
    f.story_zones.append({"id": "maids", "box": [x0, G, z0, x1, G + 6, z1]})


# ================================================================ 北の棟（1階）：衛兵詰所・宝物庫跡・貯蔵庫

def north_strip(f):
    nz0, nz1 = NORTH_Z
    for x in (HALL[0], HALL[0] + 1, HALL[1] - 1, HALL[1]):
        inner_wall_z(f, nz0, nz1, x, G + 1, F2 - 1)
    for x in (HALL[0], HALL[0] + 1):
        opening(f, x, 262, x, 266, G + 1, 4)
    for x in (HALL[1] - 1, HALL[1]):
        opening(f, x, 262, x, 266, G + 1, 4)
    # 衛兵詰所（西）
    gx0, gx1 = KEEP[0] + 2, HALL[0] - 1
    floor_area(f, gx0, nz0, gx1, nz1, G, "stone")
    for z in range(nz0 + 6, nz1 - 4, 10):
        table(f, gx0 + 20, z, gx0 + 40, z + 1, G, "spruce")
        for x in range(gx0 + 21, gx0 + 40, 3):
            chair(f, x, G, z - 1, "north")
    for x in range(gx0 + 50, gx1 - 4, 4):
        f.put(x, G + 1, nz0 + 2, block("grindstone", face="floor", facing="south"))
        f.put(x, G + 2, nz0 + 2, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    light_grid(f, gx0, nz0, gx1, nz1, G + 5, 8, 11)
    frost(f, gx0, nz0, gx1, nz1, G + 1, 0.07, 20)
    # 宝物庫跡（まん中。空の台座と、こぼれた金貨）
    tx0, tx1 = HALL[0] + 2, HALL[1] - 2
    floor_area(f, tx0, nz0, tx1, nz1, G, "hall")
    for x in range(tx0 + 6, tx1 - 5, 10):
        for z in (nz0 + 8, nz1 - 8):
            f.put(x, G + 1, z, block("polished_deepslate"))
            f.put(x, G + 2, z, block("chiseled_quartz_block") if R.at(x, z, 840) < 0.5 else None)
            if R.at(x, z, 841) < 0.3:
                f.put(x + 1, G + 1, z, block("gold_block") if R.at(x, z, 842) < 0.3 else block("raw_gold_block"))
    for x in range(tx0 + 2, tx1 - 1, 17):
        for y in range(G + 1, F2):
            f.put(x, y, (nz0 + nz1) // 2, block("quartz_pillar", axis="y"))
    chest(f, tx0 + 3, G + 1, nz0 + 3, "south")
    chest(f, tx1 - 3, G + 1, nz0 + 3, "south")
    light_grid(f, tx0, nz0, tx1, nz1, G + 5, 8, 12)
    frost(f, tx0, nz0, tx1, nz1, G + 1, 0.05, 21)
    f.story_zones.append({"id": "treasury", "box": [tx0, G, nz0, tx1, G + 6, nz1]})
    # 貯蔵庫（東）
    sx0, sx1 = HALL[1] + 1, KEEP[2] - 2
    floor_area(f, sx0, nz0, sx1, nz1, G, "wood")
    for z in range(nz0 + 3, nz1 - 2, 6):
        for x in range(sx0 + 6, sx1 - 20):
            if (x - sx0) % 8 in (0, 1):
                continue
            f.put(x, G + 1, z, block("barrel", facing="up", open=False))
            if R.at(x, z, 850) < 0.5:
                f.put(x, G + 2, z, block("barrel", facing="up", open=False))
    light_grid(f, sx0, nz0, sx1, nz1, G + 5, 8, 11)
    frost(f, sx0, nz0, sx1, nz1, G + 1, 0.06, 22)


# ================================================================ 2階：礼拝堂・王家の書庫・前廊・肖像の回廊・女王の書斎

CHAPEL_PORTRAITS = [("sigrid", 340), ("halvard", 376), ("albrecht", 412)]
FAMILY_PORTRAITS = [("oswin", 300), ("elvira", 340), ("liselotte", 428)]


def second_floor_walls(f):
    """2階の北の壁（礼拝堂と書庫の北・踊り場の北）"""
    for z in (298, 299):
        inner_wall_x(f, KEEP[0] + 2, KEEP[2] - 2, z, F2 + 1, F3 - 1)
    # 踊り場 → 前廊の扉（歌と鷹の肖像がそろうと、氷が消える）
    for x in range(380, 389):
        for z in (298, 299):
            for y in range(F2 + 1, F2 + 7):
                f.put(x, y, z, block("blue_ice"))
    f.doors["gallery_door"] = [380, F2 + 1, 298, 388, F2 + 6, 299]


def chapel(f):
    x0, x1 = KEEP[0] + 2, HALL[0] - 1
    z0, z1 = 300, KEEP[3] - 2
    floor_area(f, x0, z0, x1, z1, F2, "chapel")
    aisle = (296, 302)
    for z in range(z0, z1 + 1):
        for x in range(aisle[0], aisle[1] + 1):
            f.put(x, F2 + 1, z, block("light_blue_carpet") if aisle[0] < x < aisle[1] else block("white_carpet"))
    # 長いす
    for z in range(326, 424, 4):
        for x in list(range(x0 + 12, aisle[0] - 3)) + list(range(aisle[1] + 4, x1 - 6)):
            f.put(x, F2 + 1, z, stairs("spruce", "south"))
    # 祭壇（南の端。2段上がる）
    for k in range(2):
        for x in range(x0 + 20 - k * 2, x1 - 18 + k * 2):
            for z in range(z1 - 14 + k * 2, z1 + 1):
                f.put(x, F2 + 1 + k, z, block("polished_diorite") if k == 0 else block("smooth_quartz"))
    ax = (aisle[0] + aisle[1]) // 2
    for x in range(ax - 3, ax + 4):
        f.put(x, F2 + 3, z1 - 6, block("quartz_slab", type="top", waterlogged=False) if x not in (ax - 3, ax + 3)
              else block("chiseled_quartz_block"))
    plate_stand(f, "elvira", ax, F2 + 4, z1 - 6, "north")
    for x in (ax - 2, ax + 2):
        f.put(x, F2 + 4, z1 - 6, candle(3, True, "light_blue"))
    # 南の壁のステンドグラス（青と白）
    for x in range(ax - 8, ax + 9):
        for y in range(F2 + 3, F3 - 1):
            for z in (KEEP[3] - 1, KEEP[3]):
                c = "blue_stained_glass" if (x + y) % 3 == 0 else "light_blue_stained_glass" if (x + y) % 3 == 1 \
                    else "white_stained_glass"
                f.put(x, y, z, block(c))
    # 祈ったまま凍った神官たち
    for (dx, dz, yaw) in ((-6, -10, 180.0), (6, -10, 180.0), (-10, -20, 160.0)):
        statue(f, ax + dx, F2 + 1, z1 + dz, "priest", "pray", yaw)
    # 歌と鷹の肖像（西の壁）
    for pid, z in CHAPEL_PORTRAITS:
        portrait(f, pid, "chapel", x0, F2 + 2, z, "east")
        f.put(x0, F2 + 1, z, block("polished_deepslate"))
    for z in range(z0 + 6, z1 - 6, 18):
        chandelier(f, ax, z, F3, 3, 2)
    for z in range(z0 + 10, z1 - 10, 12):
        banner(f, x1, F2 + 6, z, "west")
    chest(f, x1 - 2, F2 + 1, z0 + 4, "west")
    # 11月28日：肖像画の前が暗すぎないよう、灯を低く・密に（大広間と同じ）
    light_grid(f, x0, z0, x1, z1, F2 + 4, 7, 13)
    frost(f, x0, z0, x1, z1, F2 + 1, 0.05, 30)
    icicles(f, x0, z0, x1, z1, F3, 0.008, 30)
    f.story_zones.append({"id": "chapel", "box": [x0, F2, z0, x1, F3, z1]})


def library(f):
    x0, x1 = HALL[1] + 1, KEEP[2] - 2
    z0, z1 = 300, KEEP[3] - 2
    inner_z = 413            # 奥の書庫との仕切り
    floor_area(f, x0, z0, x1, z1, F2, "library")
    # 本棚の並び（南北の通路。奥ほど背が高い）
    for x in range(x0 + 6, x1 - 4, 6):
        for z in range(z0 + 8, inner_z - 4):
            if (z - z0) % 22 in (0, 1, 2) or abs(z - 360) <= 6 and abs(x - 470) <= 14:
                continue
            for y in range(F2 + 1, F2 + 6):
                f.put(x, y, z, block("bookshelf") if R.at(x, y, z, 900) < 0.88 else block("spruce_planks"))
    bookshelf_wall(f, x1, z0 + 2, x1, z1 - 2, F2 + 1, 8)
    # 系譜の書（まん中の台）
    cx, cz = 470, 360
    for x in range(cx - 5, cx + 6):
        for z in range(cz - 5, cz + 6):
            if max(abs(x - cx), abs(z - cz)) <= 4:
                f.put(x, F2 + 1, z, block("blue_carpet") if max(abs(x - cx), abs(z - cz)) <= 2 else block("white_carpet"))
    clue(f, "book", "lineage_book", cx, F2 + 1, cz, "south")
    for (dx, dz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        f.put(cx + dx, F2 + 1, cz + dz, block("polished_deepslate"))
        f.put(cx + dx, F2 + 2, cz + dz, lantern())
    for z in (z0 + 6, z0 + 30):
        table(f, x0 + 4, z, x0 + 10, z + 2, F2, "dark_oak")
        f.put(x0 + 7, F2 + 2, z + 1, candle(3, True, "white"))
    # 奥の書庫（南。名のある魔物「綴じ手」と、名前の札）
    for x in range(x0, x1 + 1):
        for y in range(F2 + 1, F3):
            f.put(x, y, inner_z, block("dark_oak_planks") if y < F2 + 8 else block("bookshelf"))
    arch_z(f, inner_z, inner_z, 465, 475, F2 + 1, 6, "dark_oak")
    opening(f, 465, inner_z, 475, inner_z, F2 + 1, 6)
    for x in range(x0 + 4, x1 - 3, 5):
        for z in range(inner_z + 4, z1 - 2):
            if abs(x - 470) <= 7 and inner_z + 6 <= z <= z1 - 8:
                continue
            for y in range(F2 + 1, F2 + 7):
                f.put(x, y, z, block("bookshelf") if R.at(x, y, z, 901) < 0.85 else block("cobweb"))
    table(f, 467, z1 - 6, 473, z1 - 4, F2, "dark_oak")
    plate_stand(f, "oswin", 470, F2 + 2, z1 - 5, "north")
    chest(f, 462, F2 + 1, z1 - 3, "north", guardian="binder")
    f.guardians.append({"id": "binder", "enemy": "amemaru:shelf_wraith", "variant": "amemaru:shelf_wraith_binder",
                        "pos": [470.5, F2 + 2.0, inner_z + 16.5], "respawn_seconds": 300, "activation": 22, "pillar": -1})
    sign(f, 478, F2 + 1, inner_z - 2, 8, ["奥の書庫", "", "綴じ手が", "頁をめくっている"])
    for z in range(z0 + 10, z1 - 6, 16):
        chandelier(f, 470, z, F3, 2, 2)
    waypoint(f, "library", "王家の書庫", 436, F2 + 1, 316, 180)
    light_grid(f, x0, z0, x1, z1, F2 + 6, 7, 12)
    frost(f, x0, z0, x1, z1, F2 + 1, 0.04, 31)
    f.story_zones.append({"id": "library", "box": [x0, F2, z0, x1, F3, inner_z]})


def antechamber(f):
    """前廊：踊り場の北の扉から、肖像の回廊へ（両がわに凍った近衛兵の像が並ぶ）"""
    x0, x1 = 378, 390
    z0, z1 = GALLERY[3] + 3, 297
    for z in range(GALLERY[3] + 1, 300):
        for x in (x0 - 2, x0 - 1, x1 + 1, x1 + 2):
            for y in range(F2 + 1, F3):
                f.put(x, y, z, stone(x, y, z, salt=50))
    floor_area(f, x0, z0 - 2, x1, z1, F2, "hall")
    for z in range(z0, z1 + 1):
        f.put(384, F2 + 1, z, block("blue_carpet"))
        f.put(383, F2 + 1, z, block("blue_carpet"))
        f.put(385, F2 + 1, z, block("blue_carpet"))
    for z in range(z0 + 3, z1 - 2, 6):
        statue(f, x0 + 1, F2 + 1, z, "royal_guard", "guard", 270.0)
        statue(f, x1 - 1, F2 + 1, z, "royal_guard", "guard", 90.0)
        f.put(x0, F2 + 5, z + 3, block("soul_wall_torch", facing="east"))
        f.put(x1, F2 + 5, z + 3, block("soul_wall_torch", facing="west"))
    waypoint(f, "gallery", "肖像の回廊の前", 380, F2 + 1, 290, 0)
    light_grid(f, x0, z0, x1, z1, F2 + 6, 6, 12)


def gallery(f):
    """肖像の回廊：北の壁に最後の家族の肖像3つと、まん中に近衛の回廊への封印。東の端に女王の肖像（札はない）"""
    gx0, gx1, gz0, gz1 = GALLERY
    for z in (gz1 + 1, gz1 + 2):
        inner_wall_x(f, gx0 - 2, gx1 + 2, z, F2 + 1, F3 - 1)
    for x in (gx0 - 2, gx0 - 1, gx1 + 1, gx1 + 2):
        inner_wall_z(f, gz0 - 2, gz1 + 2, x, F2 + 1, F3 - 1)
    arch_x(f, 380, 388, gz1 + 1, gz1 + 2, F2 + 1, 6)
    floor_area(f, gx0, gz0, gx1, gz1, F2, "hall")
    for x in range(gx0, gx1 + 1):
        for z in range(gz0 + 8, gz0 + 13):
            f.put(x, F2 + 1, z, block("blue_carpet") if gz0 + 9 <= z <= gz0 + 11 else block("white_carpet"))
    # 北の壁（本館の北の壁）の窓は、肖像画のあいだだけ
    for x in range(gx0 + 6, gx1 - 5, 20):
        if any(abs(x - px) < 8 for _, px in FAMILY_PORTRAITS) or abs(x - 384) < 10:
            continue
        for y in range(F2 + 3, F2 + 8):
            for z in (KEEP[1], KEEP[1] + 1):
                f.put(x, y, z, block("light_blue_stained_glass"))
                f.put(x + 1, y, z, block("light_blue_stained_glass"))
    for pid, x in FAMILY_PORTRAITS:
        portrait(f, pid, "family", x, F2 + 2, gz0, "south")
        f.put(x, F2 + 1, gz0, block("polished_deepslate"))
    # 女王の肖像（東の端の壁。顔も名も空白）と、その横の隠し扉
    qz = (gz0 + gz1) // 2
    f.put(gx1, F2 + 1, qz, block("polished_deepslate"))
    f.put(gx1, F2 + 2, qz, block("amemaru:royal_portrait", facing="west", restored=False), thing(0, "queen"))
    f.queen = ((gx1, F2 + 2, qz), "west")
    f.queen_door = [gx1 + 1, F2 + 1, qz + 4, gx1 + 4, F2 + 3, qz + 5]
    for x in range(gx1 + 1, gx1 + 5):
        for z in (qz + 4, qz + 5):
            for y in range(F2 + 1, F2 + 4):
                f.put(x, y, z, block("calcite"))
    for x in (gx1 - 2,):
        for z in (qz - 3, qz + 3):
            f.put(x, F2 + 1, z, candle(4, True, "white"))
    for x in range(gx0 + 10, gx1 - 9, 24):
        chandelier(f, x, gz0 + 10, F3, 3, 2)
    for x in range(gx0 + 4, gx1 - 3, 12):
        banner(f, x, F2 + 7, gz1, "north")
    # 近衛の回廊への封印（北の壁のまん中）
    for x in range(382, 387):
        for y in range(F2 + 1, F2 + 5):
            f.put(x, y, KEEP[1] + 1, None)
            f.put(x, y, KEEP[1], block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"),
                                                                                    "floor": Int(FLOOR)}))
            f.seal_cells.append((x, y, KEEP[1]))
    sign(f, 389, F2 + 1, gz0 + 1, 0, ["近衛の回廊", "", "この先は", "女王を守る者の間"])
    light_grid(f, gx0, gz0, gx1, gz1, F2 + 6, 7, 12)
    frost(f, gx0, gz0, gx1, gz1, F2 + 1, 0.04, 40)
    icicles(f, gx0, gz0, gx1, gz1, F3, 0.006, 40)
    f.story_zones.append({"id": "gallery", "box": [gx0, F2, gz0, gx1, F3, gz1]})


def study(f):
    """女王の書斎（隠し部屋）：書き物机（一通目）・窓辺の腰かけ（二通目）・鏡台（三通目）。凍ったドレスと、内がわの留め金"""
    sx0, sx1, sz0, sz1 = STUDY
    for z in (sz0 - 2, sz0 - 1, sz1 + 1, sz1 + 2):
        inner_wall_x(f, sx0 - 2, KEEP[2] - 2, z, F2 + 1, F3 - 1)
    for x in (sx0 - 2, sx0 - 1):
        inner_wall_z(f, sz0 - 2, sz1 + 2, x, F2 + 1, F3 - 1)
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            f.put(x, F2, z, block("dark_oak_planks") if (x + z) % 4 else block("spruce_planks"))
            for y in range(F2 + 1, F3):
                f.put(x, y, z, None)
            if sx0 + 3 <= x <= sx1 - 3 and sz0 + 3 <= z <= sz1 - 3:
                f.put(x, F2 + 1, z, block("light_blue_carpet") if (x + z) % 2 else block("white_carpet"))
    # 隠し扉の通り道（回廊の東の壁 → 書斎の西の壁）。ふだんはふさがっている（女王の名を呼ぶと開く）
    qd = f.queen_door
    for x in range(qd[0], qd[3] + 1):
        for z in range(qd[2], qd[5] + 1):
            f.put(x, F2, z, block("dark_oak_planks"))
    f.put(sx0, F2 + 2, qd[2] - 1, block("amemaru:study_latch", facing="east"), thing(0, "latch"))
    f.latch = (sx0, F2 + 2, qd[2] - 1)
    # 書き物机（一通目）
    dx, dz = sx0 + 6, sz0 + 3
    table(f, dx - 2, dz, dx + 2, dz + 1, F2, "dark_oak")
    f.put(dx + 1, F2 + 2, dz, candle(1, True, "white"))
    f.put(dx - 1, F2 + 2, dz, block("amemaru:queen_letter", facing="south"), thing(0, "letter_1"))
    chair(f, dx, F2, dz + 2, "north", "dark_oak")
    bookshelf_wall(f, sx0, sz0, sx0 + 10, sz0, F2 + 1, 4)
    # 窓辺の腰かけ（二通目。東の窓から庭園が見える）
    wx, wz = sx1 - 1, (sz0 + sz1) // 2
    for z in range(wz - 3, wz + 4):
        f.put(wx, F2 + 1, z, stairs("dark_oak", "east"))
        for y in range(F2 + 2, F2 + 7):
            for x in (KEEP[2] - 1, KEEP[2]):
                f.put(x, y, z, block("light_blue_stained_glass"))
    f.put(wx, F2 + 2, wz + 1, block("amemaru:queen_letter", facing="west"), thing(1, "letter_2"))
    # 鏡台（三通目）と、凍ったドレス
    mx, mz = sx0 + 16, sz1 - 1
    for x in range(mx - 2, mx + 3):
        f.put(x, F2 + 1, mz, block("dark_oak_planks"))
    f.put(mx, F2 + 2, mz, block("amemaru:queen_letter", facing="north"), thing(2, "letter_3"))
    for y in range(F2 + 2, F2 + 6):
        f.put(mx - 1, y, mz, pane("white_stained_glass_pane"))
        f.put(mx + 1, y, mz, pane("white_stained_glass_pane"))
    statue(f, sx0 + 4, F2 + 1, sz1 - 4, "noble_woman", "stand", 45.0, 1.0)
    bed(f, sx1 - 6, F2, sz0 + 3, "north", "light_blue")
    chest(f, sx1 - 3, F2 + 1, sz0 + 2, "west")
    for (lx, lz) in ((sx0 + 3, sz0 + 3), (sx1 - 3, sz1 - 3)):
        f.put(lx, F3 - 1, lz, lantern(hanging=True))
    light_grid(f, sx0, sz0, sx1, sz1, F2 + 6, 6, 11)
    frost(f, sx0, sz0, sx1, sz1, F2 + 1, 0.09, 41)
    icicles(f, sx0, sz0, sx1, sz1, F3, 0.03, 41)
    letters = [("letter_1", (dx - 1, F2 + 2, dz)), ("letter_2", (wx, F2 + 2, wz + 1)), ("letter_3", (mx, F2 + 2, mz))]
    for k, (lid, pos) in enumerate(letters):
        f.relics.append({"id": lid, "pos": list(pos), "items": ["amemaru:queen_letter_%d" % (k + 1)],
                         "flag": "amemaru:story/queen_letter_%d" % (k + 1), "event": "amemaru:floor9/letter_%d" % (k + 1),
                         "record": "amemaru:f09_queen_letters"})
    f.story_zones.append({"id": "study", "box": [sx0, F2, sz0, sx1, F3, sz1]})


# ================================================================ 屋根と塔

def hall_roof(f):
    """大広間の屋根（切妻。濃い灰青の瓦に雪）"""
    x0, x1 = HALL[0] - 1, HALL[1] + 1
    for z in range(296, KEEP[3] + 2):
        for x in range(x0, x1 + 1):
            k = min(x - x0, x1 - x)
            top = F3 + 1 + k // 2
            for y in range(F3 + 1, top + 1):
                gable = z in (296, KEEP[3] + 1)
                f.put(x, y, z, block("deepslate_tiles") if not gable else stone(x, y, z, salt=60))
            f.put(x, top + 1, z, snow_layer(2) if R.at(x, z, 910) < 0.7 else None)


def tower(f, name, cx, cz, door_1f, roof_angle):
    """丸い塔（外の半径12、壁2）。中はらせん階段（1回り24段）。1階・屋根・塔の上に口"""
    r_out, r_in = TOWER_R, TOWER_R - 2
    for x in range(cx - r_out - 1, cx + r_out + 2):
        for z in range(cz - r_out - 1, cz + r_out + 2):
            d = math.hypot(x - cx, z - cz)
            if d >= r_out + 0.5:
                continue
            f.reserved[max(0, x), max(0, z)] = True
            for y in range(G - 2, TOP + 1):
                if d >= r_in + 0.5:
                    f.put(x, y, z, stone(x, y, z, salt=61) if y > G + 1 else block("deepslate_bricks"))
                elif d <= 2.5:
                    f.put(x, y, z, block("polished_andesite") if y < TOP else block("stone_bricks"))
                else:
                    f.put(x, y, z, None if G < y < TOP else block("stone_bricks"))
            # 塔の上：胸壁
            if d >= r_in + 0.5:
                ang = math.atan2(z - cz, x - cx)
                f.put(x, TOP + 1, z, stone(x, TOP + 1, z, salt=62) if int(round(ang * 8 / math.pi)) % 2 == 0 else None)
                f.put(x, TOP + 2, z, None)
            else:
                f.put(x, TOP + 1, z, snow_layer(1) if R.at(x, z, 920) < 0.3 else None)
    # らせん階段：段 s（0〜35）は y = G + 1 + s、角度は roof_angle − 19段ぶんから始まる（19段目＝屋根の高さで、屋根の口の前）
    step_ang = 2 * math.pi / 24
    start = roof_angle - 19 * step_ang
    last = TOP - G - 2
    for x in range(cx - r_in, cx + r_in + 1):
        for z in range(cz - r_in, cz + r_in + 1):
            d = math.hypot(x - cx, z - cz)
            if not (2.5 < d < r_in + 0.5):
                continue
            a = (math.atan2(z - cz, x - cx) - start) % (2 * math.pi)
            base = int(a / step_ang)
            for turn in range(3):
                s = base + 24 * turn
                if s > last:
                    break
                y = G + 1 + s
                f.put(x, y, z, block("stone_bricks") if s % 6 else block("polished_andesite"))
                if s >= last - 4:
                    f.put(x, TOP, z, None)          # 上の床の、階段の出口
    # 1階の口・屋根の口・窓
    def door_at(angle, y0, h):
        for x in range(cx - r_out - 1, cx + r_out + 2):
            for z in range(cz - r_out - 1, cz + r_out + 2):
                d = math.hypot(x - cx, z - cz)
                if not (r_in - 0.5 <= d < r_out + 0.6):
                    continue
                a = math.atan2(z - cz, x - cx)
                diff = abs((a - angle + math.pi) % (2 * math.pi) - math.pi)
                if diff * d <= 1.6:
                    for y in range(y0, y0 + h):
                        f.put(x, y, z, None)
    door_at(door_1f, G + 1, 4)
    door_at(roof_angle, F3 + 1, 3)
    for k in range(10):
        a = start + step_ang * (k * 3.6 + 2)
        y = G + 4 + int(k * 3.6)
        if y >= TOP - 2:
            break
        wx, wz = int(round(cx + math.cos(a) * (r_out - 0.5))), int(round(cz + math.sin(a) * (r_out - 0.5)))
        f.put(wx, y, wz, block("light_blue_stained_glass"))
        f.put(wx, y + 1, wz, block("light_blue_stained_glass"))
    for k in range(12):
        a = k / 12 * 2 * math.pi
        lx, lz = int(round(cx + math.cos(a) * 6)), int(round(cz + math.sin(a) * 6))
        for y in range(G + 6, TOP - 2, 9):
            if f.v[lx, y, lz] == 0:
                f.put(lx, y, lz, light(11))
    # 塔の上
    if name == "sw":
        plate_stand(f, "albrecht", cx + 4, TOP + 1, cz, "west")
    elif name == "ne":
        plate_stand(f, "liselotte", cx - 4, TOP + 1, cz, "east")
    else:
        chest(f, cx + (4 if name == "nw" else -4), TOP + 1, cz, "west" if name == "nw" else "east")
    banner_x, banner_z = int(round(cx + math.cos(roof_angle + math.pi) * 3)), int(round(cz + math.sin(roof_angle + math.pi) * 3))
    for y in range(TOP + 1, TOP + 6):
        f.put(banner_x, y, banner_z, block("stripped_spruce_log", axis="y"))
    f.put(banner_x, TOP + 6, banner_z, block("blue_wool"))
    f.story_zones.append({"id": "tower_" + name, "box": [cx - r_in, TOP, cz - r_in, cx + r_in, TOP + 5, cz + r_in]})


def towers(f):
    # 1階の口：南の2つは外庭へ、北の2つは本館の北の棟へ。屋根の口は本館の内がわ（屋根）へ
    tower(f, "sw", *TOWERS["sw"], door_1f=math.pi / 2, roof_angle=-math.pi / 4)
    tower(f, "se", *TOWERS["se"], door_1f=math.pi / 2, roof_angle=-3 * math.pi / 4)
    tower(f, "nw", *TOWERS["nw"], door_1f=math.pi / 4, roof_angle=math.pi / 4)
    tower(f, "ne", *TOWERS["ne"], door_1f=3 * math.pi / 4, roof_angle=3 * math.pi / 4)


# ================================================================ 近衛の回廊と、近衛の間（ボス）

def guard_corridor(f):
    """封印の北：2階の高さで本館を出て、石段を下り、近衛の間の南の口へ"""
    x0, x1 = 378, 390
    z_top, z_bottom = KEEP[1] - 1, ARENA[1] + ARENA_R + 1
    for z in range(z_bottom, z_top + 1):
        k = max(0, min(10, (KEEP[1] - 10) - z))          # z222 から北へ下る
        floor_y = F2 - k
        for x in range(x0, x1 + 1):
            wall = x in (x0, x1)
            for y in range(G - 2, F2 + 9):
                if y < floor_y:
                    f.put(x, y, z, block("stone_bricks"))
                elif y == floor_y:
                    if wall:
                        f.put(x, y, z, block("deepslate_bricks"))
                    elif k > 0 and floor_y > G and (KEEP[1] - 10) - z <= 10:
                        f.put(x, y, z, stairs("polished_andesite", "south"))
                    else:
                        f.put(x, y, z, block("polished_andesite") if (x + z) % 3 else block("stone_bricks"))
                elif y <= floor_y + 7:
                    f.put(x, y, z, stone(x, y, z, salt=70) if wall else None)
                elif y == floor_y + 8:
                    f.put(x, y, z, stone(x, y, z, salt=71))
            f.reserved[x, z] = True
        if z % 6 == 0:
            f.put(x0 + 1, floor_y + 5, z, block("soul_wall_torch", facing="east"))
            f.put(x1 - 1, floor_y + 5, z, block("soul_wall_torch", facing="west"))
        if z % 8 == 3:
            for x in (x0, x1):
                f.put(x, floor_y + 3, z, block("light_blue_stained_glass"))
                f.put(x, floor_y + 4, z, block("light_blue_stained_glass"))
    f.story_zones.append({"id": "corridor", "box": [x0, G, z_bottom, x1, F2 + 8, z_top]})


ARENA_GUARD = (384, 130)         # 近衛長の立つ「近衛の円」
ARENA_SPOTS = [(377, 125), (391, 125), (378, 137), (390, 137)]


def arena(f):
    cx, cz = ARENA
    r = ARENA_R
    ceil = G + 30
    for x in range(cx - r - 3, cx + r + 4):
        for z in range(cz - r - 3, cz + r + 4):
            d = math.hypot(x - cx, z - cz)
            if d >= r + 2.5:
                continue
            f.reserved[x, z] = True
            for y in range(G - 2, G):
                f.put(x, y, z, block("stone"))
            if d >= r + 0.5:
                for y in range(G, ceil + 2):
                    f.put(x, y, z, stone(x, y, z, salt=80) if y > G + 1 else block("deepslate_bricks"))
                continue
            # 床：氷と白い石の輪
            ring = int(d) % 8
            st = "blue_ice" if ring == 0 else "polished_diorite" if ring in (1, 7) else "smooth_quartz" if d < 10 else \
                "polished_andesite" if (int(math.degrees(math.atan2(z - cz, x - cx)) / 15) % 2) else "stone_bricks"
            f.put(x, G, z, block(st))
            for y in range(G + 1, ceil):
                f.put(x, y, z, None)
            f.put(x, ceil, z, block("packed_ice") if d < r * 0.4 else stone(x, ceil, z, salt=81))
            f.put(x, ceil + 1, z, block("deepslate_tiles"))
    # 柱（8本。氷の巻きついた石）
    for k in range(8):
        a = k / 8 * 2 * math.pi + math.pi / 8
        px, pz = int(round(cx + math.cos(a) * 34)), int(round(cz + math.sin(a) * 34))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz > 5:
                    continue
                for y in range(G + 1, ceil):
                    f.put(px + dx, y, pz + dz, block("packed_ice") if (y + dx) % 5 == 0 else block("stone_bricks"))
        f.put(px, G + 8, pz + 3 if pz < cz else pz - 3, light(13))
    # 近衛の円（近衛長が立つ所。女王の間の扉の前）
    gx, gz = ARENA_GUARD
    for x in range(gx - 8, gx + 9):
        for z in range(gz - 8, gz + 9):
            d = math.hypot(x - gx, z - gz)
            if 6.5 <= d < 7.5:
                f.put(x, G, z, block("lapis_block"))
            elif d < 1.5:
                f.put(x, G, z, block("chiseled_quartz_block"))
    # 女王の間の扉（北の壁。凍って開かない）
    for x in range(cx - 7, cx + 8):
        for y in range(G + 1, G + 16):
            z = cz - r
            edge = abs(x - cx) >= 6 or y >= G + 14
            f.put(x, y, z, block("quartz_pillar", axis="y") if edge else block("blue_ice") if (x + y) % 2 else block("packed_ice"))
    for (dx, yaw) in ((-9, 180.0), (9, 180.0)):
        statue(f, cx + dx, G + 1, cz - r + 3, "royal_guard", "guard", 0.0)
    # 南の口（近衛の回廊から）
    for x in range(380, 389):
        for z in range(cz + r - 1, cz + r + 4):
            for y in range(G + 1, G + 8):
                f.put(x, y, z, None)
            f.put(x, G, z, block("polished_andesite"))
    # 出口の円と次の階への円（扉の前）
    ex, ez = cx - NEXT_DX, cz - r + 8
    return_circle(f, ex, G, ez)
    next_circle(f.put, ex + NEXT_DX, G, ez)
    f.exit_circle = (ex, ez)
    # 壁の灯
    for k in range(32):
        a = k / 32 * 2 * math.pi
        x = int(round(cx + math.cos(a) * (r - 1)))
        z = int(round(cz + math.sin(a) * (r - 1)))
        f.put(x, G + 6, z, light(12))
        if k % 4 == 0:
            f.put(x, G + 12, z, light(13))
    light_grid(f, cx - r + 4, cz - r + 4, cx + r - 4, cz + r - 4, G + 14, 12, 12)
    # 11月28日：天井が高く（30）、壁の灯から遠い部屋の真ん中が真っ暗で、近衛長と部下が影にしか見えなかった。頭の少し上にも灯
    light_grid(f, cx - r + 4, cz - r + 4, cx + r - 4, cz + r - 4, G + 5, 7, 14)
    icicles(f, cx - r, cz - r, cx + r, cz + r, ceil, 0.01, 50)
    f.boss_marks = {
        "guard_circle": [[gx, G, gz]],
        "guard_spots": [[x, G + 1, z] for (x, z) in ARENA_SPOTS],
        "reinforce": [[int(round(cx + math.cos(k / 6 * 2 * math.pi) * 38)), G + 1,
                       int(round(cz + math.sin(k / 6 * 2 * math.pi) * 38))] for k in range(6)],
        "throne": [[cx, G + 1, cz - r + 2]],
        "arena_center": [[cx, G, cz]],
    }


# ================================================================ つながる形（柵・塀・板ガラス・鉄格子）

NONSOLID_SUFFIX = ("_slab", "_stairs", "_carpet", "_candle", "_torch", "_sign", "_banner", "_trapdoor", "_bed", "_button")
NONSOLID = {"snow", "light", "lantern", "soul_lantern", "chain", "end_rod", "water", "lectern", "campfire", "flower_pot",
            "lightning_rod", "grindstone", "anvil", "cauldron", "water_cauldron", "cobweb", "white_tulip", "lily_of_the_valley",
            "spruce_leaves", "hay_block_x"}


def connect_kind(name):
    if name is None:
        return None
    if name.endswith("_wall"):
        return "wall"
    if name.endswith("_pane") or name == "glass_pane":
        return "pane"
    if name.endswith("_fence"):
        return "fence"
    if name == "iron_bars":
        return "bars"
    return None


def finalize(f):
    """柵・塀・板ガラス・鉄格子の向きを、まわりに合わせる（構造物として置くと、ゲームは形を計算し直さないため）"""
    def solid(n):
        return n is not None and n not in NONSOLID and not n.endswith(NONSOLID_SUFFIX) and not n.startswith("amemaru:")

    kinds = {}
    for i, st in enumerate(f.states):
        if st is not None:
            k = connect_kind(st[0])
            if k:
                kinds[i] = k
    if not kinds:
        return
    mask = np.isin(f.v, np.array(list(kinds.keys()), dtype=np.uint16))
    for x, y, z in zip(*np.nonzero(mask)):
        x, y, z = int(x), int(y), int(z)
        st = f.states[f.v[x, y, z]]
        name, kind = st[0], kinds[int(f.v[x, y, z])]
        sides = {}
        for face, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            n = f.name_at(x + dx, y, z + dz)
            nk = connect_kind(n)
            ok = (nk is not None and (nk == kind or {nk, kind} <= {"pane", "bars"} or {nk, kind} <= {"wall"})) \
                or (kind == "fence" and nk == "fence") or (n is not None and solid(n) and nk is None)
            sides[face] = ok
        nbt = f.nbt.get((x, y, z))
        if kind == "wall":
            above = f.name_at(x, y + 1, z)
            straight = (sides["north"] and sides["south"] and not sides["east"] and not sides["west"]) or \
                       (sides["east"] and sides["west"] and not sides["north"] and not sides["south"])
            f.put(x, y, z, block(name, up=not straight or (above is not None and above not in ("snow",)), waterlogged=False,
                                 **{k: ("low" if v else "none") for k, v in sides.items()}), nbt)
        else:
            f.put(x, y, z, block(name, waterlogged=False, **sides), nbt)


# ================================================================ 敵の出る所

SPAWNS = [
    # (id, 敵, x, y, z, 半径, 数, 湧き直す秒, 群れ, 近づくと動く距離)
    # 城下町
    ("town_hounds_w", "frost_hound", 300, G + 1, 662, 14, 3, 80, (2, 3), 40),
    ("town_hounds_e", "frost_hound", 468, G + 1, 664, 14, 3, 80, (2, 3), 40),
    ("town_hounds_s", "frost_hound", 330, G + 1, 712, 12, 2, 80, (2, 2), 40),
    ("town_maids", "forgotten_maid", 384, G + 1, 690, 12, 2, 90, (1, 2), 36),
    ("town_guards", "frozen_guard", 384, G + 1, 614, 7, 2, 90, (1, 2), 30),
    # 堀の北の岸
    ("bank_guards_w", "frozen_guard", 330, G + 1, 576, 8, 2, 90, (1, 2), 30),
    ("bank_guards_e", "frozen_guard", 440, G + 1, 576, 8, 2, 90, (1, 2), 30),
    # 外庭
    ("soldiers", "frozen_guard", 384, G + 1, 531, 16, 4, 90, (2, 4), 30),
    ("bailey_cavalry_w", "ice_cavalry", 300, G + 1, 500, 20, 1, 100, (1, 1), 44),
    ("bailey_cavalry_e", "ice_cavalry", 468, G + 1, 500, 20, 1, 100, (1, 1), 44),
    ("stables_cavalry", "ice_cavalry", 222, G + 1, 508, 14, 2, 100, (1, 1), 40),
    ("kennel_hounds", "frost_hound", 552, G + 1, 508, 14, 4, 80, (2, 3), 40),
    ("bailey_maid", "forgotten_maid", 384, G + 1, 472, 10, 1, 90, (1, 1), 36),
    # 練兵場
    ("train_cavalry_n", "ice_cavalry", 100, G + 1, 372, 20, 2, 100, (1, 1), 44),
    ("train_cavalry_s", "ice_cavalry", 120, G + 1, 500, 20, 2, 100, (1, 1), 44),
    ("train_guards", "frozen_guard", 70, G + 1, 520, 12, 3, 90, (1, 2), 32),
    # 王家の庭園
    ("garden_maids", "forgotten_maid", 666, G + 1, 446, 16, 2, 90, (1, 2), 36),
    ("garden_hounds_s", "frost_hound", 650, G + 1, 520, 16, 3, 80, (2, 3), 40),
    ("garden_hounds_n", "frost_hound", 690, G + 1, 362, 14, 3, 80, (2, 3), 40),
    # 大広間
    ("hall_guards_w", "frozen_guard", 370, G + 1, 418, 10, 2, 90, (1, 2), 30),
    ("hall_guards_e", "frozen_guard", 398, G + 1, 418, 10, 2, 90, (1, 2), 30),
    ("hall_maid", "forgotten_maid", 384, G + 1, 380, 10, 1, 90, (1, 1), 34),
    # 兵舎・食堂・武器庫・西の廊下
    ("barracks_guards", "frozen_guard", 292, G + 1, 318, 14, 3, 90, (1, 2), 30),
    ("mess_guards", "frozen_guard", 292, G + 1, 356, 14, 2, 90, (1, 2), 30),
    ("armory_guards", "frozen_guard", 270, G + 1, 394, 12, 2, 90, (1, 2), 30),
    ("west_hall_hounds", "frost_hound", 344, G + 1, 400, 4, 2, 80, (2, 2), 30),
    # 厨房・食料庫・洗濯場
    ("kitchen_maids", "forgotten_maid", 474, G + 1, 318, 14, 2, 90, (1, 2), 34),
    ("pantry_hounds", "frost_hound", 474, G + 1, 356, 14, 2, 80, (2, 2), 34),
    ("laundry_maids", "forgotten_maid", 474, G + 1, 394, 14, 2, 90, (1, 2), 34),
    # 北の棟
    ("treasury_guards", "frozen_guard", 384, G + 1, 268, 14, 3, 90, (1, 2), 30),
    ("guardroom_guards", "frozen_guard", 300, G + 1, 266, 14, 2, 90, (1, 2), 30),
    ("storage_hounds", "frost_hound", 470, G + 1, 266, 14, 3, 80, (2, 3), 34),
    # 2階
    ("chapel_wraiths", "shelf_wraith", 298, F2 + 2, 370, 16, 2, 90, (1, 2), 32),
    ("chapel_maid", "forgotten_maid", 298, F2 + 1, 412, 12, 1, 90, (1, 1), 32),
    ("library_wraiths_n", "shelf_wraith", 450, F2 + 2, 332, 14, 2, 90, (1, 2), 30),
    ("library_wraiths_s", "shelf_wraith", 492, F2 + 2, 392, 14, 2, 90, (1, 2), 30),
    ("library_guard", "frozen_guard", 440, F2 + 1, 400, 8, 1, 90, (1, 1), 28),
    ("ante_guards", "frozen_guard", 384, F2 + 1, 278, 8, 2, 90, (1, 2), 26),
    ("gallery_wraiths_w", "shelf_wraith", 320, F2 + 2, 244, 12, 2, 90, (1, 2), 30),
    ("gallery_wraiths_e", "shelf_wraith", 450, F2 + 2, 244, 12, 2, 90, (1, 2), 30),
    ("gallery_guards", "frozen_guard", 384, F2 + 1, 246, 8, 2, 90, (1, 2), 26),
    # 屋根の上
    ("roof_guards_w", "frozen_guard", 290, F3 + 1, 380, 16, 2, 100, (1, 2), 30),
    ("roof_guards_e", "frozen_guard", 480, F3 + 1, 380, 16, 2, 100, (1, 2), 30),
    ("roof_hounds_n", "frost_hound", 384, F3 + 1, 262, 14, 2, 100, (2, 2), 30),
    # 近衛の回廊
    ("corridor_guards", "frozen_guard", 384, F2 + 1, 226, 5, 2, 100, (1, 2), 24),
]


def spawn_zones():
    out = []
    for (id_, enemy, x, y, z, radius, max_, respawn, group, activation) in SPAWNS:
        out.append({"id": id_, "enemy": "amemaru:" + enemy, "center": [x, y, z], "radius": radius, "max": max_,
                    "respawn_seconds": respawn, "group": list(group), "activation": activation})
    return out


# ================================================================ 階の定義

def floor_json(f):
    cx, cz = ARENA
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    ex, ez = ENTRY
    exc, ezc = f.exit_circle
    groups = []
    for gid, name, door in GROUPS:
        g = {"id": gid, "name": name}
        if door:
            g["door"] = {"box": f.doors[door], "block": "minecraft:blue_ice"}
        g["guards"] = [list(p) for p in GUARD_SPOTS[gid]]
        groups.append(g)
    qpos, qfacing = f.queen
    return {
        "_comment": "塔の9階「氷結した王城」（tools/mapgen/build_floor9.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 2,
        "name": "氷結した王城",
        "chapter_title": "第2章　凍てついた記憶",
        "recommended_level": [36, 43],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor9/",
        "piece_size": PIECE,
        "entrance": {"pos": [ex + 0.5, G + 1, ez - 7.5], "yaw": 180},
        "return_circle": {"center": [ex + 0.5, G + 1, ez + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:valgrim",
            "name": "近衛の間",
            "spawn": {"pos": [ARENA_GUARD[0] + 0.5, G + 1, ARENA_GUARD[1] + 0.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, G + 1, cz + 0.5], "radius": ARENA_R + 3.0, "y_min": G - 2, "y_max": G + 29},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 34,
            "respawn_seconds": 150,
            "max_players": 8,
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1800,
            "exit": {"center": [exc + 0.5, G + 1, ezc + 0.5], "radius": 2.6},
            "next": {"center": [exc + NEXT_DX + 0.5, G + 1, ezc + 0.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "王家の名が、肖像画に戻った。近衛の回廊の封印が、静かにほどけていく……",
        "spawns": spawn_zones(),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        # 凍った王城（青みがかった灰の霧・オーロラの空・雪）。堀へ落ちると、ともした中継の灯か入口へ戻される
        "environment": {"fog": [30, 180], "fog_color": "#B9C2D6", "sky": "aurora", "weather": "snow"},
        "abyss_y": G - 10,
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        "seal_requires": "lineage+guardians",
        "lineage": {
            "groups": groups,
            "portraits": [{"id": pid, "name": PLATES[pid][0], "group": group, "pos": list(pos), "facing": facing}
                          for pid, group, pos, facing in f.portraits],
            "plates": [{"id": pid, "name": PLATES[pid][0], "pos": list(pos)} for pid, pos in f.plates],
            "clues": [{"id": cid, "pos": list(pos)} for cid, pos in f.clues],
            "queen": {"pos": list(qpos), "facing": qfacing, "door": {"box": f.queen_door, "block": "minecraft:calcite"},
                      "latch": list(f.latch), "names": ["イゼルナ", "いぜるな", "izeruna", "izerna"],
                      "area": [qpos[0] - 10, qpos[1] - 2, qpos[2] - 9, qpos[0] + 1, qpos[1] + 4, qpos[2] + 9], "open_seconds": 20},
        },
        "relics": f.relics,
        "boss_marks": f.boss_marks,
        "story_zones": f.story_zones,
    }


GUARD_SPOTS = {
    # 札を間違えたときに目を覚ます近衛兵の出る所（組ごと）
    "founding": [(360, G + 1, 390), (408, G + 1, 404)],
    "chapel": [(270, F2 + 1, 358), (270, F2 + 1, 394)],
    "family": [(320, F2 + 1, 246), (420, F2 + 1, 246)],
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
    edge_walls(f)
    entrance(f)
    town(f)
    moat_and_bridge(f)
    gatehouse(f)
    bailey(f)
    training_ground(f)
    garden(f)
    keep_shell(f)
    great_hall(f)
    wing_rooms(f, "west")
    wing_rooms(f, "east")
    north_strip(f)
    second_floor_walls(f)
    chapel(f)
    library(f)
    antechamber(f)
    gallery(f)
    study(f)
    hall_roof(f)
    towers(f)
    guard_corridor(f)
    arena(f)
    finalize(f)
    return f


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--render" in sys.argv:
        args = [a for a in args if a != sys.argv[sys.argv.index("--render") + 1]] or args[:1]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor9") if out else os.path.join(RES, "structures", "map", "floor9")
    json_path = os.path.join(out, "floor9.json") if out else os.path.join(RES, "amemaru", "floors", "floor9.json")
    f = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", int(np.count_nonzero(f.v)), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians], "肖像画:", len(f.portraits), "札:", len(f.plates),
          "像:", len(f.statues), "見えない灯:", f.lights)
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor9_figures
        floor9_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
