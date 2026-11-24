"""塔の2階「忘れられた森」の地形を組み立て、構造物ファイル（48×48 の区画ごと）と階の定義（floors/floor2.json）を書き出す。

Phase 0-A 9章の2階：古代の金属の管に根を巻きつけた巨木の森。霧が濃く、光るキノコと風鈴のような古代の装置がある。
雑魚は迷い灯・樹皮兵・ポコダケ・葉影ギツネ。ボスは森番の古樹オールドウォード。ギミックは迷いの森（正しい道に
「結び目の灯」を置いていかないと同じ場所に戻される。鐘を鳴らす人と、こだまの方向を聞く人に分かれて道を探す）。
古代記録「森番の日誌」。物語：2日目、霧の奥でノアが初めて目撃される。ロストの侵食：帰り道を忘れた獣が同じ場所を
ぐるぐる歩く。白く抜けた木の影。

使い方:
  python3 tools/mapgen/build_floor2.py                       # Modの structures/map/floor2/ と floors/floor2.json を書き直す
  python3 tools/mapgen/build_floor2.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く

広さは 432×432（1階の約1.3倍。docs/phase8.md）、高さの幅 64。座標はすべて階の (0, 0, 0) から。地面はおよそ 16〜20、
胞子の窪地は 12、白い木立の台地は 26、管の谷の底は 8。

決まり（Phase 8 その1【補完】。docs/phase8_forest.md）：
  - 歩ける所（区画と道）をはっきり決め、そのほかは「やぶ」（3段の葉と幹）で埋める。やぶの2〜3マス奥に、見えない
    バリアの列を立てる（迷いの森を、まわりから抜けられないように）
  - 区画は7つ：入口の空き地・外れの森・森番の広場（中継の灯・森番の小屋）・胞子の窪地（西）・白い木立（北西の台地。
    ロストの侵食・葉隠れの長・ノアの目撃）・管の谷（東の深い谷。古代の管・苔衣の樹皮兵長）・森番の空き地（北。ボス）
  - 迷いの森は3つ（西・東・奥）。広場から入り、向こう側の出口のどれか1つだけが本当の道。3つとも結ぶと、空き地の封印が開く
  - 白い木立から広場へは、台地のふちから飛び下りる一方通行の近道（迷いの森を逆から抜けられないように）
  - 中継の灯は5つ（番号の順に、広場・胞子の窪地・白い木立・管の谷・空き地の前）。入口の道しるべの石から、ともした中で
    番号のいちばん大きい灯へ
"""
import json
import math
import os
import sys

import numpy as np
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapgen.world import DATA_VERSION, World, block, lantern, leaves, log, sign_nbt, slab, stairs, wall  # noqa: E402
from mapgen.tower import NEXT_DX, next_circle  # noqa: E402

import nbtlib  # noqa: E402
from nbtlib.tag import Compound, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

FLOOR = 2
SIZE = 432
HEIGHT = 64
PIECE = 48
ORIGIN = (8192 - SIZE // 2, 60, -SIZE // 2)
BASE = 18                                   # ふつうの地面の高さ

ENTRY = (216, 404)                          # 入口の台の真ん中（戻りの円）
ENTRY_FLOOR = 17
HUB = (216, 258)                            # 森番の広場の真ん中
HUB_R = 26
HUT = (230, 246)                            # 森番の小屋（古代記録「森番の日誌」）
HOLLOW = (58, 270)                          # 胞子の窪地の真ん中
HOLLOW_R = 40
HOLLOW_FLOOR = 12
GROVE = (84, 98)                            # 白い木立の台地の真ん中
GROVE_R = 46
GROVE_TOP = 26
NOA = (84, 56)                              # ノアが立つ所（白い木立の北のはずれ。story_events/floor2/noa_sighting.json と同じ）
CLEARING = (216, 56)                        # 森番の空き地（ボス）の真ん中
CLEARING_R = 30
SEAL_Z = 96                                 # 空き地の南の門（封印）の面
RAVINE = [(372, 372), (366, 330), (374, 290), (384, 250), (380, 206), (370, 164), (378, 124), (388, 86), (382, 52)]
RAVINE_HALF = 8.5                           # 谷の底の半分の幅
RAVINE_FLOOR = 8
RAMP_T0, RAMP_T1 = 0.40, 0.47                # 谷へ下りる坂の始まりと終わり（谷の長さの割合）
CHEST_LOOT = "amemaru:chests/floor2_ancient"

# 迷いの森（箱は階の座標。入口・鐘・出口）。正しい出口は1つ
WOODS = [
    {"id": "west", "name": "迷いの森・西", "box": (106, 226, 172, 290), "entry": (170, 258), "entry_dir": (-1, 0),
     "bell": (156, 258), "exits": [(108, 238), (108, 278)], "correct": 1, "decoys": 0, "listen": 12},
    {"id": "east", "name": "迷いの森・東", "box": (262, 224, 334, 292), "entry": (264, 258), "entry_dir": (1, 0),
     "bell": (278, 258), "exits": [(332, 234), (332, 258), (332, 282)], "correct": 0, "decoys": 1, "listen": 13},
    {"id": "north", "name": "迷いの森・奥", "box": (172, 128, 262, 206), "entry": (216, 204), "entry_dir": (0, -1),
     "bell": (216, 190), "exits": [(182, 130), (204, 130), (228, 130), (250, 130)], "correct": 2, "decoys": 2, "listen": 14},
]

WHITE_STONE = [("polished_diorite", 6), ("diorite", 2), ("calcite", 2), ("andesite", 1)]
OLD_STONE = [("mossy_stone_bricks", 5), ("stone_bricks", 3), ("cracked_stone_bricks", 2), ("mossy_cobblestone", 2)]


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


R = Rand(20261123)


def smooth_noise(n, cell, seed):
    rng = np.random.default_rng(seed)
    g = n // cell + 3
    grid = rng.uniform(-1.0, 1.0, (g, g))
    xs = np.arange(n) / cell
    i = np.floor(xs).astype(int)
    f = xs - i
    f = f * f * (3 - 2 * f)
    a = grid[i][:, i]
    b = grid[i + 1][:, i]
    c = grid[i][:, i + 1]
    d = grid[i + 1][:, i + 1]
    fx = f[:, None]
    fz = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def polyline_distance(points, n=SIZE):
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    best = np.full((n, n), 1e9)
    along = np.zeros((n, n))
    total = 0.0
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        dx, dz = x1 - x0, z1 - z0
        seg = math.hypot(dx, dz)
        t = np.clip(((X - x0) * dx + (Z - z0) * dz) / (seg * seg), 0.0, 1.0)
        px, pz = x0 + t * dx, z0 + t * dz
        d = np.hypot(X - px, Z - pz)
        m = d < best
        best = np.where(m, d, best)
        along = np.where(m, total + t * seg, along)
        total += seg
    return best, along, total


def circle_dist(cx, cz, n=SIZE):
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    return np.hypot(X - cx, Z - cz)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def thing(index=0, group=""):
    """階の仕掛け（鐘・灯台・中継の灯・道しるべの石）のブロックの「もの」。"""
    return Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String(group)})


class Floor(World):
    def __init__(self):
        super().__init__(SIZE, HEIGHT, SIZE)
        self.height = None
        self.walk = np.zeros((SIZE, SIZE), dtype=bool)     # 歩ける所
        self.open_ = np.zeros((SIZE, SIZE), dtype=bool)    # 歩ける所のうち、木を生やしてよい「森の中」
        self.path = np.zeros((SIZE, SIZE), dtype=bool)     # 道（木も草むらも置かない）
        self.region = np.zeros((SIZE, SIZE), dtype=np.int8)
        self.reserved = set()
        self.chests = []
        self.waypoints = []      # (id, name, pos, arrive, yaw)
        self.posts = {}          # 迷いの森の id → [灯台の場所]
        self.bells = {}
        self.guardians = []
        self.travel_stone = None
        self.tablet = None
        self.seal_cells = []
        self.lost = set()

    def top(self, x, z):
        return int(self.height[x, z])


# ================================================================ 歩ける所（区画と道）

PATHS = {
    # 入口 → 外れの森 → 広場の南
    "main": [(216, 396), (212, 376), (220, 352), (210, 326), (218, 300), (216, 284)],
    # 広場 → 迷いの森の入口
    "to_west": [(192, 258), (170, 258)],
    "to_east": [(240, 258), (264, 258)],
    "to_north": [(216, 232), (216, 204)],
    # 迷いの森・西の正しい出口 → 胞子の窪地 → 北へ → 白い木立
    "west_out": [(108, 278), (94, 276), (82, 272)],
    "hollow_north": [(52, 232), (44, 206), (52, 180), (60, 156), (70, 138)],
    # 白い木立の南東 → 台地のふち（飛び下りる所）→ 広場の北西（一方通行）
    "shortcut": [(118, 128), (138, 150), (152, 178), (160, 206), (176, 226), (194, 238), (204, 247)],
    # 迷いの森・東の正しい出口 → 谷のふち（西のふち。谷の 40% の所）
    "east_out": [(332, 234), (346, 232), (358, 238), (370, 245)],
    # 迷いの森・奥の正しい出口 → 空き地の前（封印）
    "north_out": [(228, 130), (224, 118), (218, 106), (216, SEAL_Z + 2)],
}
# 迷いの森の間違いの出口の先（少しだけ道が続き、やぶで終わる）
DEAD_ENDS = {"west": [(-8, 0)], "east": [(8, 0), (8, 0)], "north": [(0, -8), (0, -8), (0, -8)]}


def region_masks():
    """区画ごとの「広い歩ける所」（道とは別）。値は区画の番号。"""
    reg = np.zeros((SIZE, SIZE), dtype=np.int8)
    noise = smooth_noise(SIZE, 9, 71) * 3.0
    # 1 入口の空き地 / 2 外れの森 / 3 森番の広場 / 4 胞子の窪地 / 5 白い木立 / 6 管の谷 / 7 森番の空き地と前
    d = circle_dist(*ENTRY)
    reg[d < 15 + noise * 0.3] = 1
    # 外れの森：入口から広場の南までの、ふちの曲がった帯
    best, along, total = polyline_distance(PATHS["main"])
    reg[(reg == 0) & (best < 22 + noise)] = 2
    reg[circle_dist(*HUB) < HUB_R + noise * 0.4] = 3
    reg[circle_dist(*HOLLOW) < HOLLOW_R + noise] = 4
    reg[circle_dist(*GROVE) < GROVE_R + noise] = 5
    rb, ra, rt = polyline_distance(RAVINE)
    reg[rb < RAVINE_HALF + 7] = 6
    reg[circle_dist(*CLEARING) < CLEARING_R + 1] = 7
    return reg


def lay_out(f):
    """歩ける所を決める：区画の広い所（森の中）と、道"""
    reg = region_masks()
    f.region = reg
    f.walk |= reg > 0
    f.open_ |= np.isin(reg, (2, 4, 5))       # 外れの森・胞子の窪地・白い木立は、木の生えた森
    for name, pts in PATHS.items():
        best, _, _ = polyline_distance(pts)
        width = 2.2 if name in ("to_west", "to_east", "to_north", "west_out", "east_out", "north_out") else 2.6
        f.walk |= best < width
        f.path |= best < width - 0.4
    # 迷いの森の中の道は、woods() が掘る
    # 森の端のまわり（階の外のふち）は歩けない
    f.walk[:8, :] = False
    f.walk[-8:, :] = False
    f.walk[:, :8] = False
    f.walk[:, -8:] = False


# ================================================================ 地形

def terrain(f):
    n = SIZE
    h = BASE + smooth_noise(n, 37, 11) * 2.2 + smooth_noise(n, 13, 12) * 0.8
    # 区画ごとの高さ
    dh = circle_dist(*HUB)
    h = h + (BASE - h) * smoothstep(HUB_R + 10, HUB_R - 2, dh)
    de = circle_dist(*ENTRY)
    h = h + (ENTRY_FLOOR - h) * smoothstep(20, 10, de)
    # 胞子の窪地：おわん形に下がる（ふちから真ん中へ、なだらかに）
    dho = circle_dist(*HOLLOW)
    h = h + (HOLLOW_FLOOR - h) * smoothstep(HOLLOW_R + 4, HOLLOW_R - 22, dho)
    # 白い木立：台地（ふちは崖。南の上り坂と、南東の飛び下りる所）
    dg = circle_dist(*GROVE)
    plateau = smoothstep(GROVE_R + 3, GROVE_R + 1, dg)
    h = h + (GROVE_TOP - h) * plateau
    # 上り坂（窪地の北の道の終わり → 台地）
    best, along, total = polyline_distance(PATHS["hollow_north"])
    ramp_t = np.clip(along / total, 0.0, 1.0)
    on_ramp = best < 6.0
    ramp_h = 13.5 + ramp_t * (GROVE_TOP - 13.5)
    h = np.where(on_ramp & (dg > GROVE_R - 4), np.maximum(np.minimum(h, ramp_h + (best / 6.0) * 2), ramp_h), h)
    # 森番の空き地：平ら
    dc = circle_dist(*CLEARING)
    h = h + (BASE - h) * smoothstep(CLEARING_R + 10, CLEARING_R, dc)
    # 管の谷：深い谷（底は平ら。壁は急）
    rb, ra, rt = polyline_distance(RAVINE)
    floor_h = RAVINE_FLOOR + smooth_noise(n, 17, 13) * 0.7
    cut = smoothstep(RAVINE_HALF + 3.0, RAVINE_HALF, rb)
    h = h + (floor_h - h) * cut
    f.ravine_d = rb
    f.ravine_t = ra / rt
    # 階のふちは高い丘
    edge = np.minimum(np.minimum(np.arange(n)[:, None], np.arange(n)[None, :]),
                      np.minimum(n - 1 - np.arange(n)[:, None], n - 1 - np.arange(n)[None, :]))
    h = h + smoothstep(14, 2, edge) * 10
    f.height = np.round(h).astype(int)
    f.grove_d = dg
    f.hollow_d = dho


def smooth_walkable(f):
    """歩ける所の高さの差を1段までにする（道と区画の中。ゆっくり何度かならす）"""
    h = f.height.astype(float)
    walk = f.walk.copy()
    for _ in range(6):
        hn = ndimage.uniform_filter(h, size=3, mode="nearest")
        h = np.where(walk & ~f.cliff_ok, hn, h)
    f.height = np.round(h).astype(int)
    # まだ2段以上ちがう所は、低い方に合わせて削る（歩く所だけ）
    for _ in range(4):
        hh = f.height
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = np.roll(np.roll(hh, dx, axis=0), dz, axis=1)
            too = walk & ~f.cliff_ok & (hh - nb >= 2) & np.roll(np.roll(walk, dx, axis=0), dz, axis=1)
            hh = np.where(too, nb + 1, hh)
        f.height = hh


# ================================================================ 地面

def fill_ground(f):
    n = SIZE
    hmin = f.height.copy()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        hmin = np.minimum(hmin, np.roll(np.roll(f.height, dx, axis=0), dz, axis=1))
    moss = smooth_noise(n, 7, 21)
    pod = smooth_noise(n, 11, 22)
    for x in range(n):
        for z in range(n):
            top = int(f.height[x, z])
            bottom = max(1, min(top, int(hmin[x, z])) - 3)
            cliff = top - int(hmin[x, z]) >= 3
            reg = f.region[x, z]
            for y in range(bottom, top):
                if cliff and y >= int(hmin[x, z]) - 1:
                    band = (y + int(R.at(x // 4, z // 4, 5) * 2)) % 5
                    if band == 0:
                        st = R.pick([("tuff", 3), ("andesite", 1)], x, y, z, 6)
                    else:
                        st = R.pick([("stone", 4), ("mossy_cobblestone", 3), ("andesite", 1), ("cobblestone", 1)], x, y, z, 3)
                elif y >= top - 2:
                    st = "dirt" if R.at(x, y, z, 1) < 0.8 else "rooted_dirt"
                else:
                    st = R.pick([("stone", 6), ("andesite", 2), ("dirt", 2), ("tuff", 1)], x, y, z, 2)
                f.put(x, y, z, block(st))
            if f.path[x, z]:
                st = R.pick([("coarse_dirt", 4), ("podzol", 3), ("moss_block", 2), ("dirt_path", 3)], x, z, 4)
                if st == "podzol":
                    f.put(x, top, z, block("podzol", snowy=False))
                else:
                    f.put(x, top, z, block(st))
                f.reserved.add((x, z))
                continue
            if reg == 4:
                # 胞子の窪地：苔とポドゾル、菌糸
                st = R.pick([("moss_block", 4), ("podzol", 3), ("mycelium", 2), ("coarse_dirt", 1)], x, z, 7)
            elif reg == 6 and f.ravine_d[x, z] < RAVINE_HALF + 0.5:
                st = R.pick([("moss_block", 4), ("gravel", 2), ("coarse_dirt", 2), ("mossy_cobblestone", 2)], x, z, 8)
            elif moss[x, z] > 0.25:
                st = "moss_block"
            elif pod[x, z] > 0.35:
                st = "podzol"
            else:
                st = "grass_block"
            if st in ("grass_block", "podzol", "mycelium"):
                f.put(x, top, z, block(st, snowy=False))
            else:
                f.put(x, top, z, block(st))


# ================================================================ 木・やぶ

def log_at(f, x, y, z, wood="dark_oak", axis="y"):
    if f.inside(x, y, z):
        f.put(x, y, z, log(wood, axis))


def leaf_at(f, x, y, z, name="dark_oak_leaves"):
    if f.inside(x, y, z) and f.get(x, y, z) is None:
        f.put(x, y, z, leaves(name))


def small_tree(f, x, z, salt, kind="dark_oak"):
    g = f.top(x, z)
    if kind == "dark_oak":
        h = 5 + int(R.at(x, z, salt) * 3)
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(g + 1, g + h):
                    log_at(f, x + dx, y, z + dz)
        cy = g + h
        for dx in range(-3, 5):
            for dz in range(-3, 5):
                for dy in range(-1, 3):
                    d = math.hypot(dx - 0.5, dz - 0.5) + abs(dy) * 1.2
                    if d < 3.6 - (0.6 if dy == 2 else 0) and R.at(x + dx, cy + dy, z + dz, salt + 1) > 0.12:
                        leaf_at(f, x + dx, cy + dy, z + dz)
    else:   # spruce
        h = 7 + int(R.at(x, z, salt) * 5)
        for y in range(g + 1, g + h):
            log_at(f, x, y, z, "spruce")
        for y in range(g + 3, g + h + 2):
            r = max(0.6, (g + h + 2 - y) * 0.42)
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if math.hypot(dx, dz) <= r and (dx or dz or y >= g + h):
                        leaf_at(f, x + dx, y, z + dz, "spruce_leaves")


def giant_tree(f, x, z, r, h, salt, pipe=False, lost=0.0):
    """巨木：太い幹（半径 r）・根の張り出し・大きな冠。lost は白く抜けた割合（ロストの侵食）"""
    g = min(f.top(xx, zz) for xx in range(x - int(r), x + int(r) + 1) for zz in range(z - int(r), z + int(r) + 1)
            if 0 <= xx < SIZE and 0 <= zz < SIZE)
    wood = "dark_oak"

    def trunk_block(xx, yy, zz):
        if lost and R.at(xx, yy, zz, salt + 9) < lost:
            return block("amemaru:lost_blank")
        return log(wood, "y") if R.at(xx, yy, zz, salt) > 0.07 else block("dark_oak_wood", axis="y")

    for yy in range(g - 2, g + h):
        rr = r + (1.6 if yy < g + 2 else 0.8 if yy < g + 4 else 0.0) - max(0.0, (yy - g - h * 0.6) * 0.06)
        for xx in range(x - int(r) - 2, x + int(r) + 3):
            for zz in range(z - int(r) - 2, z + int(r) + 3):
                if math.hypot(xx - x, zz - z) <= rr and f.inside(xx, yy, zz):
                    f.put(xx, yy, zz, trunk_block(xx, yy, zz))
    # 根：まわりへ這う（マングローブの根と、根の張った土）
    for k in range(6):
        a = (k / 6.0 + R.at(x, z, salt + k) * 0.12) * math.tau
        length = r + 4 + R.at(x, z, salt + 20 + k) * 5
        for t in np.arange(r, length, 0.5):
            xx = int(round(x + math.cos(a) * t))
            zz = int(round(z + math.sin(a) * t))
            if not (0 <= xx < SIZE and 0 <= zz < SIZE) or f.path[xx, zz]:
                break
            gy = f.top(xx, zz)
            rise = 1 if t < length - 2 else 0
            for yy in range(gy + 1, gy + 1 + rise + (1 if t < r + 1.5 else 0)):
                f.put(xx, yy, zz, block("mangrove_roots", waterlogged=False))
            if R.at(xx, zz, salt + 30) < 0.5:
                f.put(xx, gy, zz, block("rooted_dirt"))
    # 冠：大きな葉のかたまりを3〜5つ重ねる。下につる・光る苔
    top_y = g + h
    leaf = "dark_oak_leaves" if not lost else "azalea_leaves"
    blobs = [(0, 0, 0, r * 2.6 + 3)]
    for k in range(3 + int(R.at(x, z, salt + 40) * 3)):
        a = R.at(x, z, salt + 41 + k) * math.tau
        dist = r + 2 + R.at(x, z, salt + 50 + k) * 4
        blobs.append((math.cos(a) * dist, -2 + R.at(x, z, salt + 60 + k) * 4, math.sin(a) * dist, r + 3 + R.at(x, z, salt + 70 + k) * 3))
    for (bx, by, bz, br) in blobs:
        cx, cy, cz = x + bx, top_y + by, z + bz
        for xx in range(int(cx - br) - 1, int(cx + br) + 2):
            for zz in range(int(cz - br) - 1, int(cz + br) + 2):
                for yy in range(int(cy - br * 0.45) - 1, int(cy + br * 0.5) + 2):
                    d = math.hypot((xx - cx) / br, (zz - cz) / br, (yy - cy) / (br * 0.5))
                    if d <= 1.0 and R.at(xx, yy, zz, salt + 80) > 0.08 and f.inside(xx, yy, zz):
                        if lost and R.at(xx, yy, zz, salt + 81) < lost * 0.6:
                            if f.get(xx, yy, zz) is None:
                                f.put(xx, yy, zz, block("amemaru:lost_blank"))
                        else:
                            leaf_at(f, xx, yy, zz, leaf)
                        if d > 0.8 and yy < cy and R.at(xx, yy, zz, salt + 82) < 0.05:
                            hang(f, xx, yy - 1, zz, 2 + int(R.at(xx, zz, salt + 83) * 5))
        # 枝（冠へのびる太い枝）
        if bx or bz:
            steps = int(math.hypot(bx, bz))
            for t in range(steps):
                xx = int(round(x + bx * t / steps))
                zz = int(round(z + bz * t / steps))
                yy = int(round(top_y - 3 + (by + 3) * t / steps))
                axis = "x" if abs(bx) > abs(bz) else "z"
                log_at(f, xx, yy, zz, wood, axis)
    # 幹の光るキノコ（棚のように張り出す光）
    for k in range(3):
        a = R.at(x, z, salt + 90 + k) * math.tau
        yy = g + 3 + int(R.at(x, z, salt + 95 + k) * (h - 6))
        xx = int(round(x + math.cos(a) * (r + 1)))
        zz = int(round(z + math.sin(a) * (r + 1)))
        if f.inside(xx, yy, zz) and f.get(xx, yy, zz) is None:
            f.put(xx, yy, zz, block("shroomlight"))
    if pipe:
        # 根に巻かれた古代の管（幹の横を通って地面へ）
        a = R.at(x, z, salt + 99) * math.tau
        for t in range(-int(r) - 6, int(r) + 7):
            xx = int(round(x + math.cos(a) * t + math.sin(a) * (r + 1.5)))
            zz = int(round(z + math.sin(a) * t - math.cos(a) * (r + 1.5)))
            if 0 <= xx < SIZE and 0 <= zz < SIZE and not f.path[xx, zz]:
                gy = f.top(xx, zz)
                f.put(xx, gy + 1, zz, block("waxed_weathered_cut_copper"))
                if t % 5 == 0:
                    f.put(xx, gy + 2, zz, block("waxed_exposed_cut_copper_slab", type="bottom", waterlogged=False))


def hang(f, x, y, z, length):
    """つる（葉の下から垂れる）。先は光る苔のこともある"""
    for k in range(length):
        yy = y - k
        if not f.inside(x, yy, z) or f.get(x, yy, z) is not None:
            return
        f.put(x, yy, z, block("cave_vines_plant", berries=(R.at(x, yy, z, 3) < 0.25)))
    yy = y - length
    if f.inside(x, yy, z) and f.get(x, yy, z) is None:
        f.put(x, yy, z, block("cave_vines", age=25, berries=R.at(x, z, 4) < 0.4))


def thicket(f, dist):
    """歩けない所：やぶ（3段の葉。ところどころ幹）。歩ける所から 2.5〜3.5 の所に、見えないバリアの列"""
    n = SIZE
    for x in range(1, n - 1):
        for z in range(1, n - 1):
            if f.walk[x, z]:
                continue
            g = f.top(x, z)
            d = dist[x, z]
            if 2.5 <= d < 3.5:
                for yy in range(g + 1, min(HEIGHT, g + 24)):
                    if f.get(x, yy, z) is None:
                        f.put(x, yy, z, block("barrier"))
                continue
            if d > 9 and R.at(x, z, 120) < 0.6:
                continue          # 奥は木だけ（ブロックを減らす）
            hgt = 3 + (1 if R.at(x, z, 121) < 0.35 else 0)
            name = R.pick([("dark_oak_leaves", 5), ("azalea_leaves", 2), ("flowering_azalea_leaves", 1), ("spruce_leaves", 1)],
                          x, z, 122)
            for yy in range(g + 1, g + 1 + hgt):
                if f.get(x, yy, z) is None:
                    f.put(x, yy, z, leaves(name))
            if R.at(x, z, 123) < 0.05:
                for yy in range(g + 1, g + hgt + 2):
                    f.put(x, yy, z, log("dark_oak"))


def scatter_trees(f, dist):
    """やぶの奥の大きな木と、歩ける森の中の木"""
    placed = []

    def far(x, z, gap):
        return all((x - px) ** 2 + (z - pz) ** 2 >= gap * gap for px, pz in placed)

    # 巨木（歩ける所の近くの、やぶの中）：道から見える所に
    for k in range(1600):
        x = int(R.at(k, 1, 130) * (SIZE - 40)) + 20
        z = int(R.at(k, 2, 130) * (SIZE - 40)) + 20
        d = dist[x, z]
        if f.walk[x, z] or d < 6 or d > 16 or not far(x, z, 24):
            continue
        if any(f.walk[xx, zz] for xx in range(x - 6, x + 7, 3) for zz in range(z - 6, z + 7, 3) if 0 <= xx < SIZE and 0 <= zz < SIZE) \
                and d < 6:
            continue
        r = 2 + int(R.at(k, 3, 130) * 2)
        lost = 0.18 if f.grove_d[x, z] < GROVE_R + 12 else 0.0
        giant_tree(f, x, z, r, 16 + int(R.at(k, 4, 130) * 12), 1000 + k * 7, pipe=R.at(k, 5, 130) < 0.25, lost=lost)
        placed.append((x, z))
    # 歩ける森の中の木（外れの森・胞子の窪地・白い木立）
    for k in range(9000):
        x = int(R.at(k, 6, 131) * SIZE)
        z = int(R.at(k, 7, 131) * SIZE)
        if not (12 <= x < SIZE - 12 and 12 <= z < SIZE - 12):
            continue
        if not f.open_[x, z] or f.path[x, z] or (x, z) in f.reserved:
            continue
        near_path = any(f.path[xx, zz] for xx in range(x - 3, x + 4) for zz in range(z - 3, z + 4))
        if near_path or not far(x, z, 7):
            continue
        reg = f.region[x, z]
        if reg == 4:
            giant_mushroom(f, x, z, 2000 + k)
        elif reg == 5:
            white_tree(f, x, z, 3000 + k)
        else:
            small_tree(f, x, z, 4000 + k, "dark_oak" if R.at(k, 8, 131) < 0.7 else "spruce")
        placed.append((x, z))
    return placed


def giant_mushroom(f, x, z, salt):
    """胞子の窪地の大きなキノコ（赤いかさ・白い斑点。かさの裏に光）"""
    g = f.top(x, z)
    h = 5 + int(R.at(x, z, salt) * 5)
    red = R.at(x, z, salt + 1) < 0.65
    for y in range(g + 1, g + h):
        f.put(x, y, z, block("mushroom_stem", up=False, down=False, north=True, south=True, east=True, west=True))
    r = 2 + int(R.at(x, z, salt + 2) * 2)
    cap = "red_mushroom_block" if red else "brown_mushroom_block"
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d <= r + 0.3:
                f.put(x + dx, g + h, z + dz, block(cap, up=True, down=False, north=True, south=True, east=True, west=True))
            if r - 0.7 < d <= r + 0.3 and red:
                f.put(x + dx, g + h - 1, z + dz, block(cap, up=False, down=True, north=True, south=True, east=True, west=True))
    for (dx, dz) in ((0, 0),):
        f.put(x + dx, g + h + 1, z + dz, block(cap, up=True, down=False, north=True, south=True, east=True, west=True))
    # かさの裏の光
    f.put(x + 1, g + h - 1, z, block("shroomlight"))
    f.put(x - 1, g + h - 1, z, block("shroomlight"))


def white_tree(f, x, z, salt):
    """白い木立の木（白樺の幹。葉や幹のところどころが、白く抜けている：ロストの侵食）"""
    g = f.top(x, z)
    h = 7 + int(R.at(x, z, salt) * 5)
    for y in range(g + 1, g + h):
        st = log("birch") if R.at(x, y, z, salt + 1) > 0.22 else block("amemaru:lost_blank")
        f.put(x, y, z, st)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                d = math.hypot(dx, dz) + abs(dy) * 0.9
                if d < 3.3 and R.at(x + dx, g + h + dy, z + dz, salt + 2) > 0.15:
                    if R.at(x + dx, g + h + dy, z + dz, salt + 3) < 0.25:
                        if f.get(x + dx, g + h + dy, z + dz) is None:
                            f.put(x + dx, g + h + dy, z + dz, block("amemaru:lost_blank"))
                    else:
                        leaf_at(f, x + dx, g + h + dy, z + dz, "birch_leaves")


# ================================================================ 迷いの森

def woods(f):
    """迷いの森を3つ掘る。箱の中は、道と空き地のほかはやぶ。入口の近くに鐘、向こう側の出口ごとに灯台"""
    specs = []
    for w in WOODS:
        x0, z0, x1, z1 = w["box"]
        # 箱の中を、いったん歩けない所にする
        f.walk[x0:x1 + 1, z0:z1 + 1] = False
        f.open_[x0:x1 + 1, z0:z1 + 1] = False
        ex, ez = w["entry"]
        bx, bz = w["bell"]
        segs = [[(ex, ez), (bx, bz)]]
        # 鐘から出口へ：くねくねした道（途中の点を、箱の中にばらまく）
        for k, (xx, zz) in enumerate(w["exits"]):
            pts = [(bx, bz)]
            steps = 3
            for s in range(1, steps + 1):
                t = s / (steps + 1)
                px = bx + (xx - bx) * t + (R.at(k, s, 140, x0) - 0.5) * (x1 - x0) * 0.45
                pz = bz + (zz - bz) * t + (R.at(k, s, 141, z0) - 0.5) * (z1 - z0) * 0.45
                px = min(max(px, x0 + 6), x1 - 6)
                pz = min(max(pz, z0 + 6), z1 - 6)
                pts.append((px, pz))
            pts.append((xx, zz))
            segs.append(pts)
        # 行き止まりの枝道（2〜3本）
        for k in range(3):
            sx, sz = segs[1 + k % len(w["exits"])][2]
            tx = x0 + 6 + R.at(k, 150, x0) * (x1 - x0 - 12)
            tz = z0 + 6 + R.at(k, 151, z0) * (z1 - z0 - 12)
            segs.append([(sx, sz), ((sx + tx) / 2 + 4, (sz + tz) / 2 - 3), (tx, tz)])
        for pts in segs:
            best, _, _ = polyline_distance(pts)
            box = np.zeros_like(f.walk)
            box[x0:x1 + 1, z0:z1 + 1] = True
            f.walk |= box & (best < 1.8)
            f.path |= box & (best < 1.2)
        # 鐘の空き地と、灯台のまわりの小さな空き地（灯台は出口の手前4マス・道の脇2マス）
        f.walk |= circle_dist(bx, bz) < 5.0
        posts = []
        for k, (xx, zz) in enumerate(w["exits"]):
            ox = -1 if xx <= x0 + 2 else 1 if xx >= x1 - 2 else 0
            oz = -1 if zz <= z0 + 2 else 1 if zz >= z1 - 2 else 0
            px = xx - ox * 4 + (2 if oz else 0)
            pz = zz - oz * 4 + (2 if ox else 0)
            f.walk[px - 2:px + 3, pz - 2:pz + 3] = True
            posts.append((px, pz, ox, oz))
        w["posts"] = posts
        # 入口：箱のふちを越えて、外の道へつなぐ
        edx, edz = w["entry_dir"]
        best, _, _ = polyline_distance([(ex - edx * 7, ez - edz * 7), (ex, ez)])
        f.walk |= best < 1.8
        f.path |= best < 1.2
        # 出口の外：正しい道はつながる（外の道まで）。間違いの道は少しだけ続いて、やぶで終わる
        dirs = DEAD_ENDS[w["id"]]
        for k, (xx, zz) in enumerate(w["exits"]):
            ddx, ddz = dirs[0]
            length = 6 if k == w["correct"] else 8
            ux, uz = (ddx > 0) - (ddx < 0), (ddz > 0) - (ddz < 0)
            best, _, _ = polyline_distance([(xx, zz), (xx + ux * length, zz + uz * length)])
            f.walk |= best < 1.8
            f.path |= best < 1.2
        specs.append(w)
    return specs


def woods_things(f):
    """鐘・灯台を置き、迷いの森の定義（JSON）を返す"""
    out = []
    for w in WOODS:
        x0, z0, x1, z1 = w["box"]
        bx, bz = w["bell"]
        g = f.top(bx, bz)
        clear(f, bx, g, bz, 1, 4)
        f.put(bx, g + 1, bz, block("amemaru:forest_bell"), thing(0, w["id"]))
        for dx, dz in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
            stump_lantern(f, bx + dx, bz + dz)
        exits = []
        for k, (xx, zz) in enumerate(w["exits"]):
            px, pz, ox, oz = w["posts"][k]
            gp = f.top(px, pz)
            clear(f, px, gp, pz, 0, 4)
            f.put(px, gp + 1, pz, block("amemaru:knot_post", lit=False), thing(k, w["id"]))
            # 出口の箱：ふちの外側1マスから3マス（道の幅＋1）
            if ox:
                box = [xx + (0 if ox > 0 else -3), 0, zz - 3, xx + (3 if ox > 0 else 0), HEIGHT - 1, zz + 3]
            else:
                box = [xx - 3, 0, zz + (0 if oz > 0 else -3), xx + 3, HEIGHT - 1, zz + (3 if oz > 0 else 0)]
            exits.append({"id": "%s_%d" % (w["id"], k), "box": box, "post": [px, gp + 1, pz], "correct": k == w["correct"]})
        ex, ez = w["entry"]
        ddx, ddz = w["entry_dir"]
        epos = [ex + ddx * 3 + 0.5, f.top(ex + ddx * 3, ez + ddz * 3) + 1, ez + ddz * 3 + 0.5]
        yaw = {(-1, 0): 90, (1, 0): -90, (0, -1): 180, (0, 1): 0}[(ddx, ddz)]
        out.append({"id": w["id"], "name": w["name"], "box": [x0, 0, z0, x1, HEIGHT - 1, z1],
                    "entry": {"pos": epos, "yaw": yaw}, "bell": [bx, g + 1, bz], "listen_distance": w["listen"],
                    "echo_delay": 4, "decoys": w["decoys"], "exits": exits})
        f.bells[w["id"]] = (bx, g + 1, bz)
    return out


def clear(f, x, g, z, r, up):
    for xx in range(x - r, x + r + 1):
        for zz in range(z - r, z + r + 1):
            for yy in range(g + 1, g + 1 + up):
                if f.inside(xx, yy, zz) and f.get(xx, yy, zz) is not None:
                    f.put(xx, yy, zz, block("air"))


def stump_lantern(f, x, z):
    g = f.top(x, z)
    clear(f, x, g, z, 0, 3)
    f.put(x, g + 1, z, log("dark_oak"))
    f.put(x, g + 2, z, lantern(False))


# ================================================================ 区画の飾りと仕掛け

def entrance(f):
    cx, cz = ENTRY
    y = ENTRY_FLOOR
    for x in range(cx - 10, cx + 11):
        for z in range(cz - 10, cz + 11):
            d = math.hypot(x - cx, z - cz)
            if d > 9.5:
                continue
            for yy in range(y + 1, y + 6):
                if f.get(x, yy, z) is not None:
                    f.put(x, yy, z, block("air"))
            if d <= 0.6:
                st = block("chiseled_quartz_block")
            elif d <= 1.7:
                st = block("quartz_bricks")
            elif d <= 2.7:
                st = block("smooth_quartz")
            elif d <= 3.3:
                st = block("polished_andesite")
            elif d <= 7.6:
                st = block(R.pick(WHITE_STONE, x, z, 60)) if R.at(x, z, 61) > 0.2 else block("moss_block")
            else:
                st = block("mossy_stone_bricks") if d <= 8.6 else block("moss_block")
            f.put(x, y, z, st)
            f.height[x, z] = y
            f.reserved.add((x, z))
    # まわりの光るキノコの輪
    for k in range(16):
        a = k / 16 * math.tau
        x, z = int(round(cx + math.cos(a) * 9.4)), int(round(cz + math.sin(a) * 9.4))
        if f.get(x, y + 1, z) is None:
            f.put(x, y + 1, z, block("red_mushroom" if k % 2 else "brown_mushroom"))
    for (dx, dz) in ((6, 6), (-6, 6), (6, -6), (-6, -6)):
        x, z = cx + dx, cz + dz
        f.put(x, y + 1, z, wall("mossy_stone_brick"))
        f.put(x, y + 2, z, wall("mossy_stone_brick"))
        f.put(x, y + 3, z, lantern(False))
    f.put(cx, y + 1, cz + 4, block("spruce_sign", rotation=8, waterlogged=False),
          sign_nbt(["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"]))
    f.put(cx + 2, y + 1, cz - 11, block("spruce_sign", rotation=0, waterlogged=False),
          sign_nbt(["2階", "忘れられた森", "", "霧の道は北へ"]))
    # 道しるべの石（ともした中継の灯へ）
    tx, tz = cx - 5, cz - 7
    f.put(tx, y + 1, tz, block("amemaru:travel_stone"), thing(0, "travel"))
    f.put(tx + 1, y + 1, tz, block("spruce_sign", rotation=12, waterlogged=False),
          sign_nbt(["道しるべの石", "", "触れると、ともした", "中継の灯へ"]))
    f.travel_stone = (tx, y + 1, tz)


def waypoint(f, wid, name, x, z, facing_yaw, look_at=None):
    """中継の灯。着く所は灯の facing_yaw の側に2マス。look_at（階の x, z）があれば、着いた人はそちらを向く"""
    f.walk[x - 3:x + 4, z - 3:z + 4] = True
    g = f.top(x, z)
    clear(f, x, g, z, 2, 4)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                f.put(x + dx, g, z + dz, block(R.pick(WHITE_STONE, x + dx, z + dz, 170)))
                f.reserved.add((x + dx, z + dz))
    index = len(f.waypoints)
    f.put(x, g + 1, z, block("amemaru:waypoint"), thing(index, wid))
    yaw = facing_yaw
    ax = x + (0 if yaw in (0, 180) else (2 if yaw == -90 else -2))
    az = z + (2 if yaw == 0 else -2 if yaw == 180 else 0)
    # 着いた人の向く先（幅3・長さ10）に木を生やさない（11月23日：白い木立では、着くと目の前が木の幹だった）
    fx, fz = {0: (0, 1), 180: (0, -1), 90: (-1, 0), -90: (1, 0)}[yaw]
    for i in range(1, 11):
        for w in (-1, 0, 1):
            vx, vz = ax + fx * i + fz * w, az + fz * i + fx * w
            if 0 <= vx < SIZE and 0 <= vz < SIZE:
                f.reserved.add((vx, vz))
    if look_at is not None:
        yaw = round(math.degrees(math.atan2(-(look_at[0] - (ax + 0.5)), look_at[1] - (az + 0.5))), 1)
    f.waypoints.append({"id": wid, "name": name, "pos": [x, g + 1, z],
                        "arrive": {"pos": [ax + 0.5, f.top(ax, az) + 1, az + 0.5], "yaw": yaw}})


def chest(f, x, z, facing, guardian=""):
    g = f.top(x, z)
    clear(f, x, g, z, 0, 2)
    index = len(f.chests)
    f.put(x, g + 1, z, block("amemaru:tower_chest", facing=facing),
          Compound({"id": String("amemaru:floor_thing"), "floor": Int(FLOOR), "index": Int(index), "group": String("")}))
    f.chests.append(((x, g + 1, z), guardian))
    f.reserved.add((x, z))
    return index


def hub(f):
    """森番の広場：まん中に中継の灯、北東に森番の小屋（古代記録）、まわりに巨木"""
    cx, cz = HUB
    dh = circle_dist(cx, cz)
    for x in range(cx - HUB_R, cx + HUB_R + 1):
        for z in range(cz - HUB_R, cz + HUB_R + 1):
            if dh[x, z] < 5.5:
                f.put(x, f.top(x, z), z, block(R.pick(OLD_STONE, x, z, 180)))
                f.reserved.add((x, z))
    waypoint(f, "hub", "森番の広場", cx, cz, 180)
    # 森番の小屋（小さな丸太小屋。中の机に日誌＝古代記録の石板の場所）
    hx, hz = HUT
    g = f.top(hx, hz)
    for x in range(hx - 4, hx + 5):
        for z in range(hz - 3, hz + 4):
            f.put(x, g, z, block("spruce_planks"))
            for yy in range(g + 1, g + 6):
                f.put(x, yy, z, block("air"))
            edge = x in (hx - 4, hx + 4) or z in (hz - 3, hz + 3)
            if edge:
                for yy in range(g + 1, g + 4):
                    corner = x in (hx - 4, hx + 4) and z in (hz - 3, hz + 3)
                    f.put(x, yy, z, log("spruce") if corner else block("stripped_spruce_log", axis="y"))
            f.reserved.add((x, z))
    # 戸口（南）と窓
    for yy in (g + 1, g + 2):
        f.put(hx, yy, hz + 3, block("air"))
    for x in (hx - 2, hx + 2):
        f.put(x, g + 2, hz + 3, block("glass_pane", north=False, south=False, east=True, west=True, waterlogged=False))
    # 屋根（トウヒの階段）
    for x in range(hx - 5, hx + 6):
        for k, z in enumerate(range(hz - 4, hz + 5)):
            dz = z - hz
            yy = g + 4 + (4 - abs(dz)) // 2
            if abs(dz) == 0:
                f.put(x, yy, z, slab("spruce", "bottom"))
            else:
                f.put(x, yy, z, stairs("spruce", "south" if dz < 0 else "north"))
            if x in (hx - 5, hx + 5) or abs(dz) == 4:
                continue
    f.put(hx - 3, g + 1, hz - 2, block("lectern", facing="south", has_book=False, powered=False))
    f.put(hx + 3, g + 1, hz - 2, block("barrel", facing="up", open=False))
    f.put(hx + 3, g + 2, hz - 2, lantern(False))
    f.put(hx - 3, g + 1, hz + 1, block("crafting_table"))
    f.tablet = (hx - 3, g + 1, hz - 2)
    # 11月24日：小屋の壁を向いていたので、広場の側（南）へ向ける
    f.put(hx + 1, g + 1, hz + 4, block("spruce_sign", rotation=0, waterlogged=False), sign_nbt(["森番の小屋", "", "机の上に", "古い日誌"]))
    # 広場の宝箱（小屋の裏）
    chest(f, hx, hz - 6, "south")
    # 広場の道標（3つの迷いの森へ）。11月24日：指す道の先を向いていて、南から来る人には裏しか見えなかったので、
    # 広場へ来る人の側（西の道標は南東・東の道標は南西・北の道標は南）へ向ける
    for (sx, sz, rot, lines) in ((cx - 9, cz + 2, 14, ["西：迷いの森・西", "", "胞子の窪地へ"]),
                                 (cx + 9, cz + 2, 2, ["東：迷いの森・東", "", "管の谷へ"]),
                                 (cx + 2, cz - 9, 0, ["北：迷いの森・奥", "", "森番の空き地へ"])):
        g2 = f.top(sx, sz)
        f.put(sx, g2 + 1, sz, block("spruce_sign", rotation=rot, waterlogged=False), sign_nbt(lines + [""]))


def hollow(f):
    cx, cz = HOLLOW
    waypoint(f, "hollow", "胞子の窪地", cx + 14, cz + 2, 90)
    chest(f, cx - 18, cz - 10, "east")
    chest(f, cx + 6, cz + 26, "north")
    # 光る苔とキノコ
    for k in range(400):
        x = int(cx + (R.at(k, 190) - 0.5) * HOLLOW_R * 2)
        z = int(cz + (R.at(k, 191) - 0.5) * HOLLOW_R * 2)
        if not (0 <= x < SIZE and 0 <= z < SIZE) or not f.walk[x, z] or f.path[x, z] or (x, z) in f.reserved:
            continue
        g = f.top(x, z)
        if f.get(x, g + 1, z) is None:
            f.put(x, g + 1, z, block("red_mushroom" if R.at(k, 192) < 0.5 else "brown_mushroom"))


def grove(f):
    """白い木立：台地の上。ロストの欠けの帯、ぐるぐる歩く狐の輪、葉隠れの長と宝箱、ノアの目撃の所"""
    cx, cz = GROVE
    waypoint(f, "grove", "白い木立", cx + 10, cz + 30, 180)
    # ロストの欠け（地面が白く抜けた帯）
    for x in range(cx - GROVE_R, cx + GROVE_R):
        for z in range(cz - GROVE_R, cz + GROVE_R):
            if not (0 <= x < SIZE and 0 <= z < SIZE) or not f.walk[x, z] or f.path[x, z]:
                continue
            d = math.hypot(x - (cx - 14), z - (cz - 18))
            if 9 < d < 12 and R.at(x, z, 200) < 0.55:
                f.put(x, f.top(x, z), z, block("amemaru:lost_blank"))
                f.lost.add((x, z))
                f.reserved.add((x, z))
    # ノアが立つ所：白く抜けた小さな円（木を生やさない）
    nx, nz = NOA
    for x in range(nx - 3, nx + 4):
        for z in range(nz - 3, nz + 4):
            d = math.hypot(x - nx, z - nz)
            if d <= 3.2:
                f.walk[x, z] = True
                f.reserved.add((x, z))
                if 1.5 < d and R.at(x, z, 205) < 0.7:
                    f.put(x, f.top(x, z), z, block("amemaru:lost_blank"))
                    f.lost.add((x, z))
    # ノアが見える空き地（11月23日、CIの写真で直した：手前の茂みと木で、霧の奥の少女が隠れていた）。物語の場所の箱
    # （floor_json の story_zones の noa_sighting）と、そこからノアまでの間に、木・茂み・背の高い草を生やさない。
    # ふちは少しぎざぎざにして、白い花を少し咲かせる
    for x in range(nx - 14, nx + 15):
        for z in range(nz - 2, nz + 25):
            if not (0 <= x < SIZE and 0 <= z < SIZE) or not f.walk[x, z] or f.path[x, z]:
                continue
            edge = min(x - (nx - 14), (nx + 14) - x, z - (nz - 2), (nz + 24) - z)
            if edge < 2 and R.at(x, z, 207) < 0.5:
                continue
            f.reserved.add((x, z))
            g = f.top(x, z)
            if (x, z) not in f.lost and R.at(x, z, 208) < 0.05 and f.get(x, g + 1, z) is None:
                f.put(x, g + 1, z, block("lily_of_the_valley"))
    chest(f, cx - 14, cz - 18, "south", guardian="fox_chief")
    chest(f, cx + 26, cz - 6, "west")
    f.guardians.append({"id": "fox_chief", "enemy": "amemaru:hakage_kitsune", "variant": "amemaru:hakage_kitsune_chief",
                        "pos": [cx - 14 + 0.5, f.top(cx - 14, cz - 14) + 1, cz - 14 + 0.5], "respawn_seconds": 300,
                        "activation": 36, "pillar": -1})
    # 飛び下りる所（台地のふち。広場の方へ）の目じるしと、崖の下の水たまり（落ちた人を受けとめる）
    sx, sz = PATHS["shortcut"][0]
    g = f.top(sx, sz)
    # 11月24日：台地を南西から来る人にも読めるよう、西へ向ける（読む人は、崖のある東を向く）
    f.put(sx - 2, g + 1, sz - 1, block("spruce_sign", rotation=4, waterlogged=False),
          sign_nbt(["この先は崖", "", "飛び下りると", "森番の広場へ"]))
    for x in range(SIZE):
        pass
    best, along, total = polyline_distance(PATHS["shortcut"][:3])
    for x in range(100, 160):
        for z in range(110, 170):
            if best[x, z] < 3.0 and GROVE_R + 2.5 < f.grove_d[x, z] < GROVE_R + 8 and f.height[x, z] < GROVE_TOP - 4:
                g = f.top(x, z)
                f.put(x, g, z, block("water", level=0))
                f.put(x, g - 1, z, block("clay"))
                f.reserved.add((x, z))


def ravine(f):
    """管の谷：谷の壁ぞいと、谷をまたぐ古代の管。底に巡回の樹皮兵、まん中の管の合流点に樹皮兵長と宝箱"""
    pts = RAVINE
    rb = f.ravine_d
    # 谷の底の小川（細い）と苔
    best, along, total = polyline_distance(pts)
    for x in range(SIZE):
        for z in range(SIZE):
            if best[x, z] < 1.2 and f.region[x, z] == 6:
                g = f.top(x, z)
                f.put(x, g, z, block("water", level=0))
                f.put(x, g - 1, z, block("gravel"))
                f.reserved.add((x, z))
    # 谷に沿う管（底の東の壁ぎわ）と、谷をまたぐ管（ふちの高さ）
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        seg = math.hypot(x1 - x0, z1 - z0)
        ux, uz = (x1 - x0) / seg, (z1 - z0) / seg
        nx, nz = -uz, ux
        for t in np.arange(0, seg, 0.5):
            px, pz = x0 + ux * t + nx * (RAVINE_HALF - 2.5), z0 + uz * t + nz * (RAVINE_HALF - 2.5)
            big_pipe_slice(f, px, pz, 1.4, RAVINE_FLOOR + 2)
    for k, tt in enumerate((0.18, 0.42, 0.63, 0.86)):
        cx, cz, ux, uz = ravine_point(tt)
        nx, nz = -uz, ux
        for s in np.arange(-RAVINE_HALF - 5, RAVINE_HALF + 5.5, 0.5):
            px, pz = cx + nx * s, cz + nz * s
            big_pipe_slice(f, px, pz, 1.9, BASE - 1, axis_horizontal=True)
    # 管の合流点（谷のまん中）：広い所に、壊れた古代の弁と宝箱。樹皮兵長
    cx, cz, ux, uz = ravine_point(0.52)
    cx, cz = int(round(cx)), int(round(cz))
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 7, cz + 8):
            if math.hypot(x - cx, z - cz) < 7.5:
                f.walk[x, z] = True
                g = f.top(x, z)
                f.put(x, g, z, block(R.pick(OLD_STONE, x, z, 210)))
                f.reserved.add((x, z))
    g = f.top(cx, cz)
    for yy in range(g + 1, g + 4):
        f.put(cx + 3, yy, cz, block("waxed_oxidized_copper"))
    f.put(cx + 3, g + 4, cz, block("waxed_oxidized_cut_copper_slab", type="bottom", waterlogged=False))
    chest(f, cx - 4, cz, "east", guardian="bark_captain")
    f.guardians.append({"id": "bark_captain", "enemy": "amemaru:juhihei", "variant": "amemaru:juhihei_captain",
                        "pos": [cx + 0.5, g + 1, cz + 0.5], "respawn_seconds": 300, "activation": 36, "pillar": -1})
    # 谷のふちの中継の灯（東の迷いの森から来た所）と、谷へ下りる根の坂（西の壁ぞいに、北へ下りる）
    wcx, wcz, wux, wuz = ravine_point(0.36)
    wnx, wnz = -wuz, wux
    waypoint(f, "ravine", "管の谷", int(round(wcx - wnx * 14)), int(round(wcz - wnz * 14)), -90)
    a = ravine_point(RAMP_T0)
    b = ravine_point(RAMP_T1)
    ramp(f, (a[0] + a[3] * 12.5, a[1] - a[2] * 12.5), (b[0] + b[3] * 5.5, b[1] - b[2] * 5.5), BASE, RAVINE_FLOOR + 1)
    # 谷の北のはて：宝箱（行き止まり）
    ex, ez, _, _ = ravine_point(0.97)
    chest(f, int(ex), int(ez), "south")


def ravine_point(t):
    pts = RAVINE
    lens = [math.hypot(x1 - x0, z1 - z0) for (x0, z0), (x1, z1) in zip(pts, pts[1:])]
    total = sum(lens)
    s = t * total
    for (x0, z0), (x1, z1), seg in zip(pts, pts[1:], lens):
        if s <= seg:
            return x0 + (x1 - x0) * s / seg, z0 + (z1 - z0) * s / seg, (x1 - x0) / seg, (z1 - z0) / seg
        s -= seg
    (x0, z0), (x1, z1) = pts[-2], pts[-1]
    seg = lens[-1]
    return x1, z1, (x1 - x0) / seg, (z1 - z0) / seg


def big_pipe_slice(f, px, pz, r, cy, axis_horizontal=False):
    """古代の管の輪切り（中心の高さ cy、半径 r）。横に寝た管は、上を歩ける"""
    x, z = int(round(px)), int(round(pz))
    if not (1 <= x < SIZE - 1 and 1 <= z < SIZE - 1):
        return
    for dy in range(-int(r) - 1, int(r) + 2):
        yy = cy + dy
        if abs(dy) <= r and f.inside(x, yy, z):
            ring = R.at(x, z, 220) < 0.12
            name = "waxed_exposed_copper" if ring else R.pick([("waxed_weathered_cut_copper", 5), ("waxed_oxidized_cut_copper", 3),
                                                                ("waxed_weathered_copper", 1)], x, yy, z, 221)
            if f.get(x, yy, z) is None or f.get(x, yy, z)[0] in ("dark_oak_leaves", "azalea_leaves", "barrier"):
                f.put(x, yy, z, block(name))
    if axis_horizontal:
        f.walk[x, z] = True


def ramp(f, a, b, y0, y1):
    """（谷へ下りる）根の坂：a から b へ、高さ y0 から y1 へ。1マスに1段まで"""
    ax, az = int(round(a[0])), int(round(a[1]))
    bx, bz = int(round(b[0])), int(round(b[1]))
    steps = max(abs(bx - ax), abs(bz - az), abs(y0 - y1) + 2)
    for s in range(steps + 1):
        t = s / steps
        x = int(round(ax + (bx - ax) * t))
        z = int(round(az + (bz - az) * t))
        y = int(round(y0 + (y1 - y0) * t))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                xx, zz = x + dx, z + dz
                for yy in range(y + 1, y + 5):
                    if f.inside(xx, yy, zz) and f.get(xx, yy, zz) is not None:
                        f.put(xx, yy, zz, block("air"))
                f.put(xx, y, zz, block("mangrove_roots", waterlogged=False) if (dx or dz) and R.at(xx, zz, 230) < 0.3
                      else block("rooted_dirt"))
                for yy in range(y - 4, y):
                    if f.inside(xx, yy, zz) and f.get(xx, yy, zz) is None:
                        f.put(xx, yy, zz, block("dirt"))
                f.height[xx, zz] = y
                f.walk[xx, zz] = True
                f.reserved.add((xx, zz))


def clearing(f):
    """森番の空き地：丸い空き地。まわりは巨木の壁。南の門に封印、北に戻りの円"""
    cx, cz = CLEARING
    y0 = BASE
    for x in range(cx - CLEARING_R - 2, cx + CLEARING_R + 3):
        for z in range(cz - CLEARING_R - 2, cz + CLEARING_R + 3):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if d > CLEARING_R + 0.5:
                continue
            for yy in range(y0 + 1, HEIGHT):
                if f.get(x, yy, z) is not None:
                    f.put(x, yy, z, block("air"))
            st = "moss_block" if R.at(x, z, 240) < 0.55 else "podzol"
            if 4.5 < d < 6.5 and R.at(x, z, 241) < 0.7:
                st = "rooted_dirt"
            f.put(x, y0, z, block(st, snowy=False) if st == "podzol" else block(st))
            f.height[x, z] = y0
            f.reserved.add((x, z))
    # 地面に埋まった古代の管（まん中の古樹から、まわりへ）
    for k in range(6):
        a = k / 6 * math.tau + 0.3
        for t in np.arange(7, CLEARING_R - 1, 0.5):
            x, z = int(round(cx + math.cos(a) * t)), int(round(cz + math.sin(a) * t))
            f.put(x, y0, z, block("waxed_weathered_cut_copper"))
    # まわりの灯（低い切り株のランタン）
    for k in range(10):
        a = k / 10 * math.tau
        x, z = int(round(cx + math.cos(a) * (CLEARING_R - 2))), int(round(cz + math.sin(a) * (CLEARING_R - 2)))
        if abs(x - cx) < 4 and z > cz:
            continue
        stump_lantern(f, x, z)
    # 戻りの円（北）
    ex, ez = cx, cz - 20
    for x in range(ex - 4, ex + 5):
        for z in range(ez - 4, ez + 5):
            d = math.hypot(x - ex, z - ez)
            if d <= 0.6:
                f.put(x, y0, z, block("chiseled_quartz_block"))
            elif d <= 1.7:
                f.put(x, y0, z, block("quartz_bricks"))
            elif d <= 2.7:
                f.put(x, y0, z, block("smooth_quartz"))
            elif d <= 3.3:
                f.put(x, y0, z, block("polished_diorite"))
    # 次の階への円（戻りの円の東どなり。金の縁。11月24日）
    next_circle(f.put, ex + NEXT_DX, y0, ez)
    # 南の門：2本の巨木の根のアーチ。あいだに封印の光の幕。空き地から門の外までの通り道
    gx0, gx1 = cx - 3, cx + 3
    for x in range(gx0, gx1 + 1):
        for z in range(cz + CLEARING_R - 3, SEAL_Z + 5):
            f.walk[x, z] = True
            f.put(x, y0, z, block(R.pick(OLD_STONE, x, z, 251)))
            for yy in range(y0 + 1, y0 + 9):
                if f.get(x, yy, z) is not None:
                    f.put(x, yy, z, block("air"))
            f.height[x, z] = y0
            f.reserved.add((x, z))
    for x in range(gx0 - 6, gx1 + 7):
        for z in range(SEAL_Z - 3, SEAL_Z + 4):
            f.walk[x, z] = gx0 <= x <= gx1
    for x in range(gx0, gx1 + 1):
        for z in range(SEAL_Z - 3, SEAL_Z + 4):
            g = f.top(x, z)
            f.put(x, g, z, block(R.pick(OLD_STONE, x, z, 250)))
            for yy in range(g + 1, g + 8):
                f.put(x, yy, z, block("air"))
            f.reserved.add((x, z))
    for x in (gx0 - 1, gx1 + 1):
        for yy in range(y0 + 1, y0 + 9):
            for z in range(SEAL_Z - 1, SEAL_Z + 2):
                f.put(x, yy, z, log("dark_oak"))
    for x in range(gx0 - 1, gx1 + 2):
        for z in range(SEAL_Z - 1, SEAL_Z + 2):
            f.put(x, y0 + 8, z, block("mangrove_roots", waterlogged=False))
    seal = []
    for x in range(gx0, gx1 + 1):
        for yy in range(y0 + 1, y0 + 8):
            f.put(x, yy, SEAL_Z, block("amemaru:floor_seal", sealed=False),
                  Compound({"id": String("amemaru:floor_seal"), "floor": Int(FLOOR)}))
            seal.append((x, yy, SEAL_Z))
    f.seal_cells = seal
    # 灯は門へ上る道の東のへり。着いた人は道の上で、封印の門の方を向く（11月23日：前は道の東の茂みのそばに着き、
    # 目の前が茂みだった）
    waypoint(f, "approach", "空き地の前", cx + 3, SEAL_Z + 8, 90, look_at=(cx + 0.5, SEAL_Z + 0.5))


def outer_chests(f):
    """外れの森と、迷いの森の中の宝箱（行き止まりの先などに）"""
    for (x, z, facing) in ((200, 362, "east"), (230, 334, "west"), (228, 386, "north")):
        if f.walk[x, z]:
            chest(f, x, z, facing)
    for w in WOODS:
        x0, z0, x1, z1 = w["box"]
        # 行き止まりの枝道の先
        tx = int(x0 + 6 + R.at(0, 150, x0) * (x1 - x0 - 12))
        tz = int(z0 + 6 + R.at(0, 151, z0) * (z1 - z0 - 12))
        if f.walk[tx, tz]:
            chest(f, tx, tz, "south")


# ================================================================ 階のふち・草

def boundary(f):
    n = SIZE
    for i in range(n):
        for (x, z) in ((i, 2), (i, n - 3), (2, i), (n - 3, i)):
            if 2 <= x <= n - 3 and 2 <= z <= n - 3:
                g = f.top(x, z)
                for yy in range(g + 1, HEIGHT):
                    if f.get(x, yy, z) is None:
                        f.put(x, yy, z, block("barrier"))


def vegetation(f):
    kinds = smooth_noise(SIZE, 13, 41)
    for x in range(3, SIZE - 3):
        for z in range(3, SIZE - 3):
            if not f.walk[x, z] or (x, z) in f.reserved:
                continue
            g = f.top(x, z)
            st = f.get(x, g, z)
            if st is None or f.get(x, g + 1, z) is not None:
                continue
            r = R.at(x, z, 260)
            ground = st[0]
            if f.path[x, z] or f.region[x, z] in (3, 7):
                continue      # 道と、広場・空き地の真ん中は、すっきりさせる
            if ground == "moss_block":
                if r < 0.25:
                    f.put(x, g + 1, z, block("moss_carpet"))
                elif r < 0.265:
                    f.put(x, g + 1, z, block("azalea"))
            elif ground in ("grass_block", "podzol"):
                if r < 0.13:
                    f.put(x, g + 1, z, block("fern" if kinds[x, z] > 0 else "grass"))
                elif r < 0.15 and f.get(x, g + 2, z) is None:
                    f.put(x, g + 1, z, block("large_fern", half="lower"))
                    f.put(x, g + 2, z, block("large_fern", half="upper"))
                elif r < 0.16:
                    f.put(x, g + 1, z, block("brown_mushroom"))
            elif ground == "mycelium" and r < 0.2:
                f.put(x, g + 1, z, block("red_mushroom" if r < 0.1 else "brown_mushroom"))


# ================================================================ 階の定義（floors/floor2.json）

def spawn_zones(f):
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=52, route=None, variant=None):
        y = f.top(cx, cz) + 1
        z = {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
             "respawn_seconds": respawn, "group": list(group), "activation": activation}
        if route:
            z["route"] = [[x, f.top(x, zz) + 1, zz] for (x, zz) in route]
        return z

    def ring(cx, cz, r, k=8):
        return [(int(round(cx + math.cos(a / k * math.tau) * r)), int(round(cz + math.sin(a / k * math.tau) * r))) for a in range(k)]
    hx, hz = HOLLOW
    gx, gz = GROVE
    out = [
        # 外れの森：迷い灯（道からそれた方へ誘う）・葉影ギツネ・ポコダケ
        zone("outer_lure_south", "mayoi_bi", 224, 372, 12, 3, 45, (1, 2)),
        zone("outer_lure_mid", "mayoi_bi", 204, 334, 12, 3, 45, (1, 2)),
        zone("outer_foxes", "hakage_kitsune", 228, 312, 12, 3, 60, (2, 3)),
        zone("outer_shrooms", "pokodake", 196, 350, 10, 3, 50, (1, 2)),
        # 迷いの森の中
        zone("west_woods", "hakage_kitsune", 136, 258, 10, 3, 60, (2, 2), 40),
        zone("east_woods", "juhihei", 300, 258, 6, 2, 60, (1, 1), 40, route=[(282, 258), (300, 248), (316, 258), (300, 270)]),
        zone("east_lure", "mayoi_bi", 312, 240, 8, 2, 50, (1, 2), 40),
        zone("north_woods", "hakage_kitsune", 216, 168, 12, 3, 60, (2, 3), 40),
        zone("north_lure", "mayoi_bi", 236, 150, 10, 3, 50, (1, 2), 40),
        # 胞子の窪地：ポコダケがたくさん
        zone("hollow_a", "pokodake", hx - 14, hz - 6, 10, 4, 45, (2, 3)),
        zone("hollow_b", "pokodake", hx + 8, hz + 14, 10, 4, 45, (2, 3)),
        zone("hollow_c", "pokodake", hx - 6, hz + 24, 8, 3, 50, (1, 2)),
        zone("hollow_lure", "mayoi_bi", hx + 4, hz - 18, 12, 3, 45, (1, 2)),
        zone("hollow_path", "hakage_kitsune", 50, 196, 10, 3, 60, (2, 2)),
        # 白い木立：帰り道を忘れた狐が、同じ輪をぐるぐる歩く（ロストの侵食）
        zone("grove_loop_a", "hakage_kitsune", gx + 12, gz + 8, 8, 2, 60, (1, 1), 44, route=ring(gx + 12, gz + 8, 7)),
        zone("grove_loop_b", "hakage_kitsune", gx - 18, gz + 14, 8, 2, 60, (1, 1), 44, route=ring(gx - 18, gz + 14, 6)),
        zone("grove_lure", "mayoi_bi", gx + 20, gz - 20, 12, 3, 45, (1, 2)),
        # 管の谷：巡回の樹皮兵（谷の底）・ポコダケ
        zone("ravine_south", "juhihei", 368, 320, 6, 2, 55, (1, 1), 44,
             route=[(366, 338), (368, 320), (374, 300), (380, 282)]),
        zone("ravine_mid", "juhihei", 382, 214, 6, 2, 55, (1, 1), 44,
             route=[(384, 240), (382, 220), (376, 196), (372, 176)]),
        zone("ravine_north", "juhihei", 380, 110, 6, 2, 55, (1, 1), 44,
             route=[(374, 140), (380, 120), (386, 96), (384, 76)]),
        zone("ravine_shrooms", "pokodake", 372, 268, 6, 3, 50, (1, 2), 44),
        # 空き地の前
        zone("approach", "juhihei", 220, 112, 6, 2, 60, (1, 1), 40, route=[(224, 120), (218, 106), (212, 114)]),
    ]
    return out


def floor_json(f, woods_json):
    cx, cz = CLEARING
    y = BASE + 1
    xs = [c[0] for c in f.seal_cells]
    ys = [c[1] for c in f.seal_cells]
    zs = [c[2] for c in f.seal_cells]
    data = {
        "_comment": "塔の2階「忘れられた森」（tools/mapgen/build_floor2.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": FLOOR,
        "chapter": 1,
        "name": "忘れられた森",
        "chapter_title": "第1章　失われた古代領域",
        "recommended_level": [6, 13],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor2/",
        "piece_size": PIECE,
        "entrance": {"pos": [ENTRY[0] + 0.5, ENTRY_FLOOR + 1, ENTRY[1] - 8.5], "yaw": 180},
        "return_circle": {"center": [ENTRY[0] + 0.5, ENTRY_FLOOR + 1, ENTRY[1] + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:old_ward",
            "name": "森番の空き地",
            "spawn": {"pos": [cx + 0.5, y, cz + 0.5], "yaw": 0},
            "arena": {"center": [cx + 0.5, y, cz + 0.5], "radius": CLEARING_R - 1.0, "y_min": BASE - 2, "y_max": BASE + 20},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 20,
            "respawn_seconds": 120,
            "max_players": 8,
            # 11月24日「ソロでは全然倒せない」：ひとりでも2人ぶんのHP、1人増えるごとに HP +60%、22分たつと怒る【補完】
            # （調整の PR #25 の値）
            "hp_min_players": 2,
            "hp_per_extra_player": 0.6,
            "attack_per_extra_player": 0.1,
            "pace_per_extra_player": 0.05,
            "enrage_seconds": 1320,
            "exit": {"center": [cx + 0.5, y, cz - 19.5], "radius": 2.6},
            "next": {"center": [cx + NEXT_DX + 0.5, y, cz - 19.5], "radius": 2.6},
        },
        "pillars": [],
        "pillars_done": "",
        "tablet": {"pos": list(f.tablet), "title": "森番の日誌",
                   "text": "木々の根は、地の下で細い糸を運んでいる。……最近、獣たちが帰り道を忘れる。"},
        "spawns": spawn_zones(f),
        "guardians": f.guardians,
        "chests": [dict({"pos": list(p), "loot": CHEST_LOOT}, **({"guardian": g} if g else {})) for p, g in f.chests],
        "environment": {"fog": [6, 46], "fog_color": "#8EA396"},
        "lost_woods": woods_json,
        "waypoints": f.waypoints,
        "travel_stone": list(f.travel_stone),
        # 11月24日「時間が掛からない」：名のある魔物（白い木立・管の谷の宝箱の守り手）を2体とも倒した印も要る【補完】
        "seal_requires": "lost_woods+guardians",
        # ノアから6〜22ブロック南（ノアが見える空き地の中。grove() の空き地と合わせる）
        "story_zones": [{"id": "noa_sighting", "box": [NOA[0] - 12, GROVE_TOP - 2, NOA[1] + 6, NOA[0] + 12, GROVE_TOP + 12,
                                                        NOA[1] + 22]}],
    }
    return data


# ================================================================ 書き出し

def save_pieces(f, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    groups = {}
    for (x, y, z), state in f.blocks.items():
        groups.setdefault((x // PIECE, z // PIECE), []).append((x, y, z, state))
    written = 0
    for px in range(SIZE // PIECE):
        for pz in range(SIZE // PIECE):
            cells = sorted(groups.get((px, pz), []), key=lambda c: (c[1], c[2], c[0]))
            palette, index = [], {}
            entries = []
            for (x, y, z, state) in cells:
                if state[0] == "air":
                    continue
                if state not in index:
                    index[state] = len(palette)
                    palette.append(state)
                e = {"state": Int(index[state]), "pos": List[Int]([Int(x - px * PIECE), Int(y), Int(z - pz * PIECE)])}
                if (x, y, z) in f.nbt:
                    e["nbt"] = f.nbt[(x, y, z)]
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
    lay_out(f)
    woods(f)
    # 管の谷・空き地の門・坂は、歩ける所に足す
    terrain(f)
    f.cliff_ok = np.zeros((SIZE, SIZE), dtype=bool)
    # 白い木立の台地のふち（崖）と、谷の壁は、ならさない
    f.cliff_ok |= (f.grove_d > GROVE_R - 2) & (f.grove_d < GROVE_R + 4)
    f.cliff_ok |= (f.ravine_d > RAVINE_HALF - 1) & (f.ravine_d < RAVINE_HALF + 4)
    smooth_walkable(f)
    fill_ground(f)
    entrance(f)
    hub(f)
    hollow(f)
    grove(f)
    ravine(f)
    clearing(f)
    woods_json = woods_things(f)
    outer_chests(f)
    dist = ndimage.distance_transform_edt(~f.walk)
    thicket(f, dist)
    scatter_trees(f, dist)
    boundary(f)
    vegetation(f)
    f.finalize()
    return f, woods_json


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor2") if out else os.path.join(RES, "structures", "map", "floor2")
    json_path = os.path.join(out, "floor2.json") if out else os.path.join(RES, "amemaru", "floors", "floor2.json")
    f, woods_json = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(f, woods_json)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", len(f.blocks), "宝箱:", len(f.chests), "中継の灯:", len(f.waypoints),
          "封印:", len(f.seal_cells), "守り手:", [g["id"] for g in f.guardians])
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor2_figures
        floor2_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
