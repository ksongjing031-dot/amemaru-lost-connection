"""塔の1階「始まりの草原」の地形を組み立て、構造物ファイル（48×48×48 の区画ごと）と階の定義（floors/floor1.json）を書き出す。

Phase 0-A 9章の1階：なだらかな草原と小川、半分埋もれた古代の道標の石柱。空をよく見ると、地平線に薄い格子の線が見える
（この階が「再現された世界」だという最初の手がかり）。雑魚はツノモフ・石くれ兵・結晶蜂・欠け牙の狼。ボスは草原の主ヴェルドホーン。
ギミックは、草原に散らばる3本の石柱を探して起動すると、ボスへの道が開く。古代記録「道標の石板・一」。
ロストの侵食：草むらの一部が白く抜け落ちている。

使い方:
  python3 tools/mapgen/build_floor1.py                     # Modの data/amemaru/structures/map/floor1/ と floors/floor1.json を書き直す
  python3 tools/mapgen/build_floor1.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く（上から見た地図・ボスの広間・石柱・入口）

広さは 384×384（地上の高さの幅 48）。管理者が /amemaru admin map build floor 1 で、塔のディメンションの x = 4096 のあたりに建てる
（区画ごとに 64 個の構造物を並べる）。座標はすべて階の (0, 0, 0)（構造物の北西の下の角）から。y は 0 が構造物の底で、
草原の地面はおよそ 10〜18、ボスの広間の床は 14。

決まり（Phase 5 で決めたこと【補完】）：
  - 階のまわりは、なだらかに盛り上がる丘で囲み、いちばん外に薄い水色の板ガラスの格子（8ブロックごとの線）を立てる。
    格子の内側にバリアを置いて、外へは出られない
  - 入口（塔の門から着く所）は南の真ん中の古代の円い台。台の真ん中の「戻りの円」に立っていると、塔の門へ戻る
  - 古代の道が、入口から北の真ん中の「草原の主の広間」（円い広間。屋根のある列柱の回廊と、外の壁）まで続く。
    道の脇には半分埋もれた道標。道は小川を古い石橋で渡る
  - 3本の石柱は、西の丘・東の野の遺跡・北西の林。石柱の前の台に「古代の石柱の核」（右クリックで起動）
  - 広間の南の門は「封印の光の幕」。3本の石柱をすべて起動した人だけが通れる。門の前の広場に「道標の石板・一」
  - 敵は、湧く所（spawns）ごとに出る。人がいる所の近くだけ
"""
import json
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapgen.world import (DATA_VERSION, World, block, lantern, leaves, log, pane, sign_nbt, slab, stairs, wall)  # noqa: E402

import nbtlib  # noqa: E402
from nbtlib.tag import Byte, Compound, Int, List, String  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "src", "main", "resources", "data", "amemaru")

SIZE = 384
HEIGHT = 48
PIECE = 48
ORIGIN = (4096 - SIZE // 2, 60, -SIZE // 2)     # 塔のディメンションの中の (0, 0, 0)
WATER = 9                                       # 小川の水面の高さ
BOSS_FLOOR = 14                                 # 広間の床のブロックの高さ（足もとは 15）
ENTRY_FLOOR = 12                                # 入口の台の床のブロックの高さ

# 主な場所（x, z）
ENTRY = (192, 352)                              # 入口の台の真ん中（戻りの円）
HALL = (192, 58)                                # 草原の主の広間の真ん中
GATE_Z = 90                                     # 広間の南の門の封印の面（z）
TABLET = (192, 103)                             # 道標の石板・一
PILLARS = [(64, 232), (318, 196), (96, 104)]    # 西の丘・東の野の遺跡・北西の林
PILLAR_FACING = ["east", "west", "south"]       # 石柱の台が向く方（道から来る人の方）
PILLAR_LINES = ["この塔は、つながる者のために建てられた", "ひとりの手では、門は動かない", "手をつなげ。道は、その先にある"]

ROAD = [(192, 343), (190, 322), (184, 300), (174, 280), (172, 262), (178, 244), (188, 226), (196, 210), (204, 194),
        (207, 176), (204, 158), (198, 140), (192, 124), (192, 112)]
STREAM = [(30, 52), (48, 64), (70, 84), (92, 120), (112, 150), (134, 176), (160, 196), (196, 208), (226, 222),
          (252, 246), (276, 272), (300, 296), (322, 318), (336, 334)]

WHITE_STONE = [("polished_diorite", 6), ("diorite", 2), ("calcite", 1), ("andesite", 1)]   # 古代の白い石（街と同じ割合）
ROAD_STONE = [("stone_bricks", 32), ("mossy_stone_bricks", 24), ("cracked_stone_bricks", 14), ("cobblestone", 10),
              ("mossy_cobblestone", 12), ("polished_andesite", 8)]


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


R = Rand(20261121)


def smooth_noise(n, cell, seed):
    """n×n の、なめらかな値のノイズ（格子の大きさ cell）。値は −1〜1"""
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
    """各マスの真ん中から、折れ線までの距離と、折れ線の上のいちばん近い所の、始まりからの長さ"""
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


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Floor(World):
    def __init__(self):
        super().__init__(SIZE, HEIGHT, SIZE)
        self.height = None
        self.road_d = None
        self.stream_d = None
        self.reserved = set()      # 草や木を生やさない所（建物・道・石柱のまわり）
        self.lost = set()          # ロストの欠け（白く抜け落ちた所）の (x, z)

    def top(self, x, z):
        return int(self.height[x, z])

    def surface_free(self, x, z):
        """地面の上に何もないか"""
        y = self.top(x, z)
        return self.get(x, y + 1, z) is None


# ================================================================ 地形
def terrain(f):
    n = SIZE
    h = 12.0 + 4.5 * smooth_noise(n, 64, 11) + 2.2 * smooth_noise(n, 29, 12) + 0.9 * smooth_noise(n, 13, 13)
    xs = np.arange(n) + 0.5
    X, Z = np.meshgrid(xs, xs, indexing="ij")
    edge = np.minimum(np.minimum(X, n - X), np.minimum(Z, n - Z))
    h += 11.0 * (1.0 - smoothstep(0.0, 30.0, edge)) ** 1.6       # まわりの丘
    stream_d, _, _ = polyline_distance(STREAM)
    road_d, road_along, road_len = polyline_distance(ROAD)
    f.stream_d, f.road_d = stream_d, road_d
    # 小川の谷（水面の1つ上まで下げる）
    bank = WATER + 1.0
    h = np.where(stream_d < 9.0, bank + (np.maximum(h, bank) - bank) * smoothstep(2.6, 9.0, stream_d), h)
    # 平らな所：入口の台・ボスの広間・石柱のまわり
    def flatten(cx, cz, r0, r1, level):
        nonlocal h
        d = np.hypot(X - cx, Z - cz)
        w = 1.0 - smoothstep(r0, r1, d)
        h = h * (1 - w) + level * w
    flatten(ENTRY[0], ENTRY[1], 15, 26, ENTRY_FLOOR)
    flatten(HALL[0], HALL[1], 47, 60, BOSS_FLOOR)
    flatten(TABLET[0], TABLET[1] + 4, 14, 24, BOSS_FLOOR)
    for (px, pz) in PILLARS:
        level = float(np.round(h[px, pz]))
        flatten(px + 0.5, pz + 0.5, 7, 13, level)
    # 道：道の真ん中の高さを、道に沿ってなだらかにして、そのまわりに広げる
    samples = []
    pts = ROAD
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        seg = math.hypot(x1 - x0, z1 - z0)
        steps = max(1, int(seg))
        for k in range(steps):
            t = k / steps
            samples.append((x0 + (x1 - x0) * t, z0 + (z1 - z0) * t))
    samples.append(pts[-1])
    sh = np.array([h[min(n - 1, int(x)), min(n - 1, int(z))] for x, z in samples])
    win = 14
    smooth = np.array([sh[max(0, i - win):i + win + 1].mean() for i in range(len(sh))])
    idx = np.clip((road_along / max(road_len, 1e-6) * (len(samples) - 1)).round().astype(int), 0, len(samples) - 1)
    road_h = smooth[idx]
    w = 1.0 - smoothstep(3.5, 9.0, road_d)
    h = h * (1 - w) + road_h * w
    f.height = np.clip(np.round(h), 7, 30).astype(int)
    # 小川の上は、水面の1つ上より高くしない（谷の底）
    f.height = np.where(stream_d < 2.6, WATER - 1 - (stream_d < 1.4).astype(int), f.height)
    f.height = np.where((stream_d >= 2.6) & (stream_d < 4.0), np.minimum(f.height, WATER + 1), f.height)


def fill_ground(f):
    """地面の柱（上は草・道・岸の砂など、下は土と石）と、小川の水"""
    n = SIZE
    hmin = f.height.copy()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        hmin = np.minimum(hmin, np.roll(np.roll(f.height, dx, axis=0), dz, axis=1))
    lost_mask = lost_patches(f)
    for x in range(n):
        for z in range(n):
            top = int(f.height[x, z])
            bottom = max(1, min(top, int(hmin[x, z])) - 3)
            sd = f.stream_d[x, z]
            rd = f.road_d[x, z]
            for y in range(bottom, top):
                if y >= top - 2:
                    st = "dirt" if R.at(x, y, z, 1) < 0.85 else "coarse_dirt"
                else:
                    st = R.pick([("stone", 6), ("andesite", 2), ("dirt", 2), ("gravel", 1)], x, y, z, 2)
                f.put(x, y, z, block(st))
            if sd < 2.6:
                bed = R.pick([("gravel", 5), ("sand", 3), ("clay", 1), ("mossy_cobblestone", 1)], x, z, 3)
                f.put(x, top, z, block(bed))
                for y in range(top + 1, WATER + 1):
                    f.put(x, y, z, block("water", level=0))
                f.reserved.add((x, z))
                continue
            if (x, z) in lost_mask:
                f.put(x, top, z, block("amemaru:lost_blank"))
                f.lost.add((x, z))
                f.reserved.add((x, z))
                continue
            if rd < 2.7:
                st = R.pick(ROAD_STONE, x, z, 4)
                if R.at(x, z, 5) < 0.06:
                    st = R.pick([("grass_block", 3), ("coarse_dirt", 1), ("moss_block", 1)], x, z, 6)
                f.put(x, top, z, block(st, snowy=False) if st == "grass_block" else block(st))
                f.reserved.add((x, z))
                continue
            if rd < 3.6 and R.at(x, z, 7) < 0.55:
                st = R.pick([("cobblestone", 2), ("mossy_cobblestone", 3), ("coarse_dirt", 2), ("gravel", 1)], x, z, 8)
                f.put(x, top, z, block(st))
                continue
            if sd < 3.8:
                st = R.pick([("sand", 3), ("gravel", 2), ("grass_block", 3), ("coarse_dirt", 1)], x, z, 9)
                f.put(x, top, z, block(st, snowy=False) if st == "grass_block" else block(st))
                continue
            f.put(x, top, z, block("grass_block", snowy=False))


def lost_patches(f):
    """ロストの欠け：白く抜け落ちた草むら（欠け牙の狼のいる所に多く、ほかにも点々と）"""
    out = set()
    rng = random.Random(77)
    centers = [(56, 300, 9), (70, 318, 5), (40, 284, 4), (332, 84, 9), (346, 102, 5), (316, 70, 4),
               (150, 236, 3), (238, 312, 3), (286, 150, 3), (120, 60, 3), (270, 360, 2), (110, 340, 3), (344, 252, 3)]
    noise = smooth_noise(SIZE, 5, 21)
    for (cx, cz, r) in centers:
        for x in range(cx - r - 3, cx + r + 4):
            for z in range(cz - r - 3, cz + r + 4):
                if not (0 <= x < SIZE and 0 <= z < SIZE):
                    continue
                d = math.hypot(x - cx, z - cz)
                if d + noise[x, z] * 2.4 < r and f.road_d[x, z] > 4 and f.stream_d[x, z] > 3:
                    out.add((x, z))
        # 欠けのまわりの、点々とした小さな欠け
        for _ in range(r * 2):
            a = rng.uniform(0, math.tau)
            dd = r + rng.uniform(1, 4)
            x, z = int(cx + math.cos(a) * dd), int(cz + math.sin(a) * dd)
            if 0 <= x < SIZE and 0 <= z < SIZE and f.road_d[x, z] > 4 and f.stream_d[x, z] > 3:
                out.add((x, z))
    return out


# ================================================================ 小さな部品
def white(x, y, z, salt=0):
    return block(R.pick(WHITE_STONE, x, y, z, 30 + salt))


def boulder(f, cx, cz, r, salt):
    top = f.top(cx, cz)
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            for y in range(top - 1, top + r + 1):
                d = math.hypot(x - cx, (y - top) * 1.3, z - cz)
                if d < r + 0.35 * (R.at(x, y, z, salt) - 0.5) and 0 <= x < SIZE and 0 <= z < SIZE:
                    mat = R.pick([("stone", 4), ("andesite", 3), ("mossy_cobblestone", 3), ("cobblestone", 1)], x, y, z, salt + 1)
                    f.put(x, y, z, block(mat))
                    f.reserved.add((x, z))
    # 上に少し苔
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            for y in range(top + r + 1, top - 2, -1):
                st = f.get(x, y, z)
                if st is not None:
                    if f.get(x, y + 1, z) is None and R.at(x, z, salt + 2) < 0.35:
                        f.put(x, y + 1, z, block("moss_carpet"))
                    break


def tree(f, x, z, kind_, salt):
    top = f.top(x, z)
    if f.get(x, top, z) is None or f.get(x, top, z)[0] not in ("grass_block", "moss_block", "coarse_dirt"):
        return False
    hgt = 4 + int(R.at(x, z, salt) * 3)
    wood = "birch" if kind_ == "birch" else "oak"
    leaf = block(wood + "_leaves", distance=1, persistent=True, waterlogged=False)
    for y in range(top + 1, top + hgt + 1):
        f.put(x, y, z, log(wood))
    crown = top + hgt
    for dy in range(-2, 2):
        r = 2 if dy < 0 else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and (dy == 1 or R.at(x + dx, crown + dy, z + dz, salt + 1) < 0.5):
                    continue
                pos = (x + dx, crown + dy, z + dz)
                if f.get(*pos) is None:
                    f.put(*pos, leaf)
    f.put(x, crown + 2, z, leaf)
    f.reserved.add((x, z))
    return True


def grove(f, cx, cz, r, count, salt, birch=0.3):
    rng = random.Random(salt)
    placed = []
    for _ in range(count * 6):
        if len(placed) >= count:
            break
        a = rng.uniform(0, math.tau)
        d = math.sqrt(rng.random()) * r
        x, z = int(cx + math.cos(a) * d), int(cz + math.sin(a) * d)
        if not (8 <= x < SIZE - 8 and 8 <= z < SIZE - 8):
            continue
        if f.road_d[x, z] < 6 or f.stream_d[x, z] < 5 or (x, z) in f.reserved:
            continue
        if any(math.hypot(x - px, z - pz) < 5 for px, pz in placed):
            continue
        if tree(f, x, z, "birch" if rng.random() < birch else "oak", salt + len(placed)):
            placed.append((x, z))


def waystone(f, x, z, salt):
    """道の脇の、半分埋もれた古い道標（低い石の柱が少し傾いている）"""
    top = f.top(x, z)
    hgt = 2 + int(R.at(x, z, salt) * 3)
    lean = (1, 0) if R.at(x, z, salt + 1) < 0.5 else (0, 1)
    for i in range(hgt + 1):
        ox, oz = (lean[0], lean[1]) if i >= hgt - 1 and hgt >= 3 else (0, 0)
        mat = "chiseled_stone_bricks" if i == hgt else R.pick([("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 1)],
                                                              x, top + i, z, salt + 2)
        f.put(x + ox, top + i, z + oz, block(mat))
    f.reserved.add((x, z))
    if R.at(x, z, salt + 3) < 0.6:
        f.put(x + lean[0] * (1 if hgt >= 3 else 0), top + hgt + 1, z + lean[1] * (1 if hgt >= 3 else 0), block("moss_carpet"))


def crystal_outcrop(f, cx, cz, salt):
    """翠の結晶の群れ（結晶蜂の巣のあたり）。真ん中は光る石で、まわりに緑のガラスの結晶が突き出す"""
    top = f.top(cx, cz)
    f.put(cx, top, cz, block("sea_lantern"))
    rng = random.Random(salt)
    for k in range(7):
        a = rng.uniform(0, math.tau)
        d = rng.uniform(0.8, 3.2)
        x, z = int(round(cx + math.cos(a) * d)), int(round(cz + math.sin(a) * d))
        t = f.top(x, z)
        hgt = rng.randint(1, 4)
        for y in range(t + 1, t + 1 + hgt):
            f.put(x, y, z, block("green_stained_glass"))
        if rng.random() < 0.5:
            f.put(x, t, z, block("prismarine"))
        f.reserved.add((x, z))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x, z = cx + dx, cz + dz
        t = f.top(x, z)
        f.put(x, t + 1, z, block("green_stained_glass"))
        f.put(x, t + 2, z, block("green_stained_glass")) if R.at(x, z, salt) < 0.5 else None
    f.reserved.add((cx, cz))


# ================================================================ 石柱（3本）
def pillar(f, index):
    px, pz = PILLARS[index]
    facing = PILLAR_FACING[index]
    g = f.top(px, pz)
    fx, fz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    # まわりの古い敷石（円く。ところどころ草に埋もれる）
    for x in range(px - 6, px + 7):
        for z in range(pz - 6, pz + 7):
            d = math.hypot(x - px, z - pz)
            if d <= 5.5 and R.at(x, z, 40 + index) < 0.8:
                f.put(x, f.top(x, z), z, white(x, 0, z, index))
                f.reserved.add((x, z))
            elif d <= 6.5 and R.at(x, z, 41 + index) < 0.35:
                f.put(x, f.top(x, z), z, block("mossy_cobblestone"))
    # 大きな石柱（3×3、高さ10。上のほうは少し傾いて、苔とつる）
    lean = (-fx, -fz)
    for dy in range(0, 11):
        ox, oz = (lean[0], lean[1]) if dy >= 7 else (0, 0)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = px + dx + ox - fx * 2, pz + dz + oz - fz * 2
                if dy == 10 and (dx != 0 and dz != 0):
                    continue
                if dy in (3, 8):
                    mat = block("chiseled_stone_bricks")
                elif dy >= 9:
                    mat = block("mossy_stone_bricks")
                else:
                    mat = white(x, g + dy, z, 5 + index)
                f.put(x, g + dy, z, mat)
                f.reserved.add((x, z))
    # 石柱の正面の、縦の翠の筋（緑のガラス）
    for dy in range(1, 8):
        if dy != 3:
            f.put(px - fx, g + dy, pz - fz, block("green_stained_glass"))
    # つる（石柱の横の面）
    side = (fz, fx)
    for dy in range(2, 9):
        if R.at(px, dy, pz, 50 + index) < 0.7:
            x, z = px - fx * 2 + side[0] * 2, pz - fz * 2 + side[1] * 2
            face = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[side]
            props = {"north": False, "south": False, "east": False, "west": False, "up": False}
            props[face] = True
            if f.get(x, g + dy, z) is None:
                f.put(x, g + dy, z, block("vine", **props))
    # 台：石柱の前（3×3の段。真ん中に核）
    ax, az = px + fx * 1, pz + fz * 1
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            f.put(ax + dx, g, az + dz, block("chiseled_stone_bricks") if dx == 0 and dz == 0 else block("polished_andesite"))
    core = (ax, g + 1, az)
    f.put(*core, block("amemaru:tower_pillar"), Compound({"id": String("amemaru:tower_pillar"), "floor": Int(1), "index": Int(index)}))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (dx, dz) == (-fx, -fz):
            continue
        toward_core = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]
        f.put(ax + dx, g + 1, az + dz, stairs("polished_andesite", toward_core))
    for y in range(g + 2, g + 6):
        f.put(ax, y, az, block("air"))
    return core


# ================================================================ 入口の台
def entrance(f):
    cx, cz = ENTRY
    y = ENTRY_FLOOR
    for x in range(cx - 10, cx + 11):
        for z in range(cz - 10, cz + 11):
            d = math.hypot(x - cx, z - cz)
            if d > 9.5:
                continue
            for yy in range(y + 1, y + 4):
                if f.get(x, yy, z) is not None and f.get(x, yy, z)[0] not in ("water",):
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
                st = white(x, y, z, 60)
            else:
                st = block("polished_andesite") if d <= 8.6 else block("stone_bricks")
            f.put(x, y, z, st)
            f.height[x, z] = y
            f.reserved.add((x, z))
    # 四隅の灯り（塀の柱の上にランタン）
    for (dx, dz) in ((6, 6), (-6, 6), (6, -6), (-6, -6)):
        x, z = cx + dx, cz + dz
        f.put(x, y + 1, z, wall("stone_brick"))
        f.put(x, y + 2, z, wall("stone_brick"))
        f.put(x, y + 3, z, lantern(False))
    # 戻りの円の案内（円の南。北から戻ってくる人に見える向き）と、着いた人の前の階の名前の看板
    f.put(cx, y + 1, cz + 4, block("spruce_sign", rotation=8, waterlogged=False),
          sign_nbt(["塔の門へ戻る円", "", "円の中に立つと", "塔の門へ戻ります"]))
    f.put(cx + 2, y + 1, cz - 11, block("spruce_sign", rotation=0, waterlogged=False),
          sign_nbt(["1階", "始まりの草原", "", "古い道は北へ"]))


# ================================================================ 草原の主の広間
def hall(f):
    cx, cz = HALL
    y0 = BOSS_FLOOR
    R_ARENA, R_COL, R_WALL0, R_WALL1, R_ROOF = 24.5, 25.5, 30.5, 32.5, 33.5
    gate_x = range(cx - 2, cx + 3)
    gate_y = range(y0 + 1, y0 + 7)

    def polar(x, z):
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        return math.hypot(dx, dz), math.degrees(math.atan2(dz, dx)) % 360.0

    # 床と、上を片づける
    for x in range(cx - 36, cx + 37):
        for z in range(cz - 36, cz + 37):
            r, a = polar(x, z)
            if r > R_ROOF + 0.5:
                continue
            for yy in range(y0 + 1, HEIGHT):
                f.put(x, yy, z, block("air"))
            f.height[x, z] = y0
            f.reserved.add((x, z))
            if r <= R_ARENA:
                # 古代の白い敷石。輪の模様（r 7・16）、真ん中の結び目の紋
                if 6.5 <= r < 7.5 or 15.5 <= r < 16.5:
                    st = block("polished_andesite")
                elif r < 2.5:
                    st = block("chiseled_stone_bricks") if r < 1.5 else block("polished_andesite")
                elif R.at(x, z, 70) < 0.06:
                    st = block("moss_block")
                elif R.at(x, z, 71) < 0.08:
                    st = block("cracked_stone_bricks")
                else:
                    st = white(x, y0, z, 72)
            elif r <= R_WALL0:
                st = block("polished_andesite") if R.at(x, z, 73) < 0.7 else block("stone_bricks")
            else:
                st = block("stone_bricks")
            f.put(x, y0, z, st)
            for yy in range(y0 - 3, y0):
                f.put(x, yy, z, block("stone"))
    # 結び目の紋（真ん中。2つの輪が交わる形を、翠のガラスの線で）
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            for (ox, oz) in ((-2.5, 0), (2.5, 0)):
                d = math.hypot(x + 0.5 - (cx + ox), z + 0.5 - cz)
                if 3.0 <= d < 3.9:
                    f.put(x, y0, z, block("green_stained_glass"))
                    f.put(x, y0 - 1, z, block("sea_lantern"))
    # 外の壁（2つの厚さ、高さ10）。ベイごとに窓（バリアでふさぐ）
    cols = [a for a in np.arange(0, 360, 22.5) if abs(((a - 90) + 180) % 360 - 180) > 1]
    cols += [90 - 9.5, 90 + 9.5]
    bays = [(a + 11.25) % 360 for a in np.arange(0, 360, 22.5)]
    for x in range(cx - 36, cx + 37):
        for z in range(cz - 36, cz + 37):
            r, a = polar(x, z)
            if R_WALL0 <= r < R_WALL1:
                in_gate = x in gate_x and z >= cz + 26
                for yy in range(y0 + 1, y0 + 11):
                    if in_gate and yy in gate_y:
                        continue
                    window = False
                    for b in bays:
                        diff = abs(((a - b) + 180) % 360 - 180)
                        if diff * math.pi / 180 * r < 1.6 and y0 + 3 <= yy <= y0 + 5 and abs(((b - 90) + 180) % 360 - 180) > 15:
                            window = True
                    if window:
                        f.put(x, yy, z, block("barrier") if r >= R_WALL0 + 1.0 else block("air"))
                        continue
                    if yy == y0 + 1:
                        st = block("mossy_stone_bricks") if R.at(x, yy, z, 74) < 0.5 else block("stone_bricks")
                    elif yy == y0 + 10:
                        st = block("chiseled_stone_bricks") if R.at(x, z, 75) < 0.15 else block("stone_bricks")
                    else:
                        st = block(R.pick([("stone_bricks", 5), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)], x, yy, z, 76))
                    f.put(x, yy, z, st)
    # 窓の下の段（外から見たときの石の縁）
    # 列柱（2×2ほど。土台と柱頭は彫った石、柱は白い石）
    for a in cols:
        ax = cx + math.cos(math.radians(a)) * R_COL
        az = cz + math.sin(math.radians(a)) * R_COL
        for x in range(int(ax) - 2, int(ax) + 3):
            for z in range(int(az) - 2, int(az) + 3):
                if math.hypot(x + 0.5 - ax, z + 0.5 - az) > 1.25:
                    continue
                for yy in range(y0 + 1, y0 + 10):
                    if yy in (y0 + 1, y0 + 9):
                        st = block("chiseled_stone_bricks")
                    else:
                        st = white(x, yy, z, 77)
                    f.put(x, yy, z, st)
    # 柱の上の梁と、屋根（回廊の上）。屋根の縁に低い胸壁
    for x in range(cx - 36, cx + 37):
        for z in range(cz - 36, cz + 37):
            r, a = polar(x, z)
            if R_ARENA <= r < R_ROOF:
                f.put(x, y0 + 10, z, block("stone_bricks") if R.at(x, z, 78) < 0.75 else block("mossy_stone_bricks"))
                if r < R_COL + 1.2:
                    f.put(x, y0 + 10, z, block("polished_andesite"))
                if r >= R_WALL1 - 0.2:
                    f.put(x, y0 + 11, z, slab("stone_brick") if R.at(x, z, 79) < 0.8 else slab("mossy_stone_brick"))
                elif R.at(x, z, 80) < 0.08:
                    f.put(x, y0 + 11, z, block("moss_carpet"))
    # 列柱の間のアーチ（梁の下に、逆さの階段で弧をつくる）
    for i, a in enumerate(sorted(cols)):
        b = sorted(cols)[(i + 1) % len(cols)]
        span = (b - a) % 360
        if span > 30 or abs(((a + span / 2 - 90) + 180) % 360 - 180) < 6:
            continue
        for k in range(1, 12):
            t = k / 12.0
            ang = math.radians(a + span * t)
            x = int(math.floor(cx + math.cos(ang) * R_COL))
            z = int(math.floor(cz + math.sin(ang) * R_COL))
            if f.get(x, y0 + 9, z) is None:
                rise = 1 if 0.2 < t < 0.8 else 0
                f.put(x, y0 + 9, z, block("polished_andesite"))
                if rise == 0 and f.get(x, y0 + 8, z) is None:
                    f.put(x, y0 + 8, z, slab("polished_andesite", "top"))
    # 外の壁の控え壁（列柱と同じ向きに、外へ1つ張り出す。上は彫った石）と、つる
    for a in cols:
        if abs(((a - 90) + 180) % 360 - 180) < 14:
            continue
        ang = math.radians(a)
        for rr in (R_WALL1 + 0.3, R_WALL1 + 1.1):
            x = int(math.floor(cx + math.cos(ang) * rr))
            z = int(math.floor(cz + math.sin(ang) * rr))
            top_y = y0 + 9 if rr > R_WALL1 + 0.8 else y0 + 10
            for yy in range(y0 + 1, top_y + 1):
                f.put(x, yy, z, block("chiseled_stone_bricks") if yy == top_y else
                      block(R.pick([("stone_bricks", 3), ("mossy_stone_bricks", 2)], x, yy, z, 84)))
            for yy in range(y0 - 2, y0 + 1):
                f.put(x, yy, z, block("stone_bricks"))
    for x in range(cx - 36, cx + 37):
        for z in range(cz - 36, cz + 37):
            r, a = polar(x, z)
            if R_WALL1 <= r < R_WALL1 + 1.0 and f.get(x, y0 + 4, z) is None and R.at(x, z, 85) < 0.22:
                dx, dz = x + 0.5 - cx, z + 0.5 - cz
                face = ("west" if dx > 0 else "east") if abs(dx) > abs(dz) else ("north" if dz > 0 else "south")
                props = {"north": False, "south": False, "east": False, "west": False, "up": False}
                props[face] = True
                for yy in range(y0 + 3 + int(R.at(x, z, 86) * 4), y0 + 10):
                    if f.get(x, yy, z) is None:
                        f.put(x, yy, z, block("vine", **props))
    # 回廊の床の、崩れた石のかけら
    for x in range(cx - 33, cx + 34):
        for z in range(cz - 33, cz + 34):
            r, a = polar(x, z)
            if R_COL + 1.4 <= r < R_WALL0 - 0.2 and f.get(x, y0 + 1, z) is None and R.at(x, z, 87) < 0.05 and abs(x - cx) > 4:
                f.put(x, y0 + 1, z, block(R.pick([("cobblestone", 1), ("mossy_cobblestone", 2), ("stone_brick_slab", 0)], x, z, 88)))
    # 門のアーチ（門の上の段と、かなめ石）
    for x in gate_x:
        for z in range(cz + 29, cz + 34):
            r, _ = polar(x, z)
            if R_WALL0 - 0.5 <= r < R_WALL1 + 0.5:
                f.put(x, y0 + 7, z, block("chiseled_stone_bricks") if x == cx else block("polished_andesite"))
    # 門の通り道（回廊の中から外まで、床から6段を空ける）
    for x in gate_x:
        for z in range(cz + 26, cz + 35):
            for yy in gate_y:
                f.put(x, yy, z, block("air"))
            f.put(x, y0, z, block("polished_andesite"))
    # 封印の光の幕（門の外の面）
    seal_cells = []
    for x in gate_x:
        for yy in gate_y:
            f.put(x, yy, GATE_Z, block("amemaru:floor_seal", sealed=False), Compound({"id": String("amemaru:floor_seal"), "floor": Int(1)}))
            seal_cells.append((x, yy, GATE_Z))
    # 回廊の灯り（ベイごとに、屋根から吊るす）
    for b in bays:
        if abs(((b - 90) + 180) % 360 - 180) < 12:
            continue
        x = int(math.floor(cx + math.cos(math.radians(b)) * 28.5))
        z = int(math.floor(cz + math.sin(math.radians(b)) * 28.5))
        f.put(x, y0 + 9, z, block("chain", axis="y", waterlogged=False))
        f.put(x, y0 + 8, z, lantern(True))
    # 出口の円（北の奥。戦いの後、ここに立つと塔の門へ戻る）
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
    # 門の前の広場（半円の敷石）と、道標の石板・一
    tx, tz = TABLET
    for x in range(cx - 14, cx + 15):
        for z in range(GATE_Z + 1, GATE_Z + 22):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - GATE_Z)
            if d > 14.5:
                continue
            for yy in range(y0 + 1, y0 + 6):
                if f.get(x, yy, z) is not None:
                    f.put(x, yy, z, block("air"))
            f.put(x, y0, z, white(x, y0, z, 81) if R.at(x, z, 82) < 0.85 else block("mossy_cobblestone"))
            f.height[x, z] = y0
            f.reserved.add((x, z))
            for yy in range(y0 - 3, y0):
                if f.get(x, yy, z) is None:
                    f.put(x, yy, z, block("stone"))
    # 石板：横3・高さ3の立った石（真ん中に古代文字の彫り）、両脇に低い柱
    for dx in (-1, 0, 1):
        for dy in (1, 2, 3):
            st = block("chiseled_stone_bricks") if (dx == 0 and dy == 2) else white(tx + dx, y0 + dy, tz, 83)
            if dy == 3 and dx != 0:
                st = slab("stone_brick")
            f.put(tx + dx, y0 + dy, tz, st)
    f.put(tx, y0 + 4, tz, block("moss_carpet"))
    for dx in (-3, 3):
        f.put(tx + dx, y0 + 1, tz, wall("mossy_stone_brick"))
        f.put(tx + dx, y0 + 2, tz, lantern(False))
    # 門の両脇の灯り
    for dx in (-5, 5):
        f.put(cx + dx, y0 + 1, GATE_Z + 2, wall("stone_brick"))
        f.put(cx + dx, y0 + 2, GATE_Z + 2, wall("stone_brick"))
        f.put(cx + dx, y0 + 3, GATE_Z + 2, lantern(False))
    return seal_cells


# ================================================================ 石橋（道が小川を渡る所）
def bridge(f):
    pts = []
    for i, ((x0, z0), (x1, z1)) in enumerate(zip(ROAD, ROAD[1:])):
        seg = math.hypot(x1 - x0, z1 - z0)
        for k in range(int(seg)):
            t = k / seg
            pts.append((x0 + (x1 - x0) * t, z0 + (z1 - z0) * t, (x1 - x0) / seg, (z1 - z0) / seg))
    deck = WATER + 2
    for (px, pz, ux, uz) in pts:
        x, z = int(px), int(pz)
        if f.stream_d[x, z] > 5.5:
            continue
        nx, nz = -uz, ux
        for s in np.arange(-3.0, 3.01, 0.5):
            bx, bz = int(math.floor(px + nx * s)), int(math.floor(pz + nz * s))
            if not (0 <= bx < SIZE and 0 <= bz < SIZE):
                continue
            edge = abs(s) >= 2.9
            f.put(bx, deck, bz, block("stone_bricks") if not edge else block("mossy_stone_bricks"))
            for yy in range(deck + 1, deck + 4):
                if f.get(bx, yy, bz) is not None and not edge:
                    f.put(bx, yy, bz, block("air"))
            if edge:
                f.put(bx, deck + 1, bz, wall("mossy_stone_brick") if R.at(bx, bz, 90) < 0.5 else wall("stone_brick"))
            if f.stream_d[bx, bz] < 2.6:
                # 水の上：橋の下に小さなアーチ（端だけ石を下ろす）
                if abs(s) >= 2.4:
                    for yy in range(WATER - 2, deck):
                        f.put(bx, yy, bz, block("stone_bricks"))
            else:
                for yy in range(f.top(bx, bz) + 1, deck):
                    f.put(bx, yy, bz, block("stone_bricks"))
            f.height[bx, bz] = max(f.height[bx, bz], deck) if f.stream_d[bx, bz] >= 2.6 else f.height[bx, bz]
            f.reserved.add((bx, bz))


# ================================================================ 遺跡（東の野・道ばた）
def ruins(f):
    # 東の野の遺跡（石柱 P1 のまわり）：崩れた壁の四角と、折れた列柱、倒れた柱
    px, pz = PILLARS[1]
    x0, z0, x1, z1 = px - 9, pz - 8, px + 9, pz + 8
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                g = f.top(x, z)
                hgt = int(R.at(x, z, 100) * 4) - 1
                if (x == x0 and abs(z - pz) <= 2) or (z == z1 and abs(x - px) <= 2):
                    hgt = -1      # 入口の切れ目
                for yy in range(g + 1, g + 1 + max(0, hgt)):
                    f.put(x, yy, z, block(R.pick([("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 2)], x, yy, z, 101)))
                if hgt >= 0:
                    f.put(x, g, z, block("stone_bricks"))
                f.reserved.add((x, z))
    for (dx, dz, hgt) in ((-5, -4, 5), (5, -4, 2), (-5, 4, 3), (5, 4, 6)):
        x, z = px + dx, pz + dz
        g = f.top(x, z)
        for yy in range(g + 1, g + 1 + hgt):
            f.put(x, yy, z, white(x, yy, z, 102) if yy < g + hgt else block("chiseled_stone_bricks"))
        f.reserved.add((x, z))
    # 倒れた柱（横向きの石英の柱）
    g = f.top(px + 2, pz + 12)
    for dx in range(0, 6):
        f.put(px - 1 + dx, g + 1, pz + 12, block("quartz_pillar", axis="x") if dx % 3 else block("chiseled_stone_bricks"))
        f.reserved.add((px - 1 + dx, pz + 12))
    # 道ばたの古い門（道をまたぐ、2本の柱と梁）
    for (gx, gz) in ((184, 300), (204, 158)):
        g = f.top(gx, gz)
        span = 4
        for side in (-span, span):
            for yy in range(g + 1, g + 7):
                f.put(gx + side, yy, gz, white(gx + side, yy, gz, 103) if yy < g + 6 else block("chiseled_stone_bricks"))
            f.reserved.add((gx + side, gz))
        for dx in range(-span, span + 1):
            if R.at(gx, gz, dx, 104) < 0.85 or abs(dx) == span:
                f.put(gx + dx, g + 7, gz, block("stone_bricks") if abs(dx) < span else block("mossy_stone_bricks"))
    # 北西の林の古い祠の跡（石柱 P2 の後ろ）
    px, pz = PILLARS[2]
    for dx in range(-4, 5):
        x, z = px + dx, pz - 7
        g = f.top(x, z)
        for yy in range(g + 1, g + 1 + int(R.at(x, z, 105) * 3)):
            f.put(x, yy, z, block("mossy_stone_bricks"))


# ================================================================ 草原に散らばる木・茂み・岩
def scatter(f):
    rng = random.Random(909)
    keep_out = [(ENTRY[0], ENTRY[1], 26), (HALL[0], HALL[1], 50), (TABLET[0], TABLET[1], 20)] + [(px, pz, 11) for px, pz in PILLARS]

    def ok(x, z, road=7, stream=5):
        if not (10 <= x < SIZE - 10 and 10 <= z < SIZE - 10) or (x, z) in f.reserved:
            return False
        if f.road_d[x, z] < road or f.stream_d[x, z] < stream:
            return False
        return all(math.hypot(x - cx, z - cz) > r for cx, cz, r in keep_out)
    trees = 0
    for _ in range(900):
        if trees >= 70:
            break
        x, z = rng.randrange(10, SIZE - 10), rng.randrange(10, SIZE - 10)
        if ok(x, z, 9, 7) and tree(f, x, z, "birch" if rng.random() < 0.3 else "oak", 700 + trees):
            trees += 1
    bushes = 0
    leaf = block("oak_leaves", distance=1, persistent=True, waterlogged=False)
    for _ in range(1500):
        if bushes >= 140:
            break
        x, z = rng.randrange(10, SIZE - 10), rng.randrange(10, SIZE - 10)
        if not ok(x, z, 5, 4):
            continue
        g = f.top(x, z)
        if f.get(x, g, z) is None or f.get(x, g, z)[0] != "grass_block" or f.get(x, g + 1, z) is not None:
            continue
        size = rng.randint(1, 3)
        for dx in range(0, size):
            for dz in range(0, 1 + (size > 2)):
                gx, gz = x + dx, z + dz
                gg = f.top(gx, gz)
                if f.get(gx, gg + 1, gz) is None and (gx, gz) not in f.reserved:
                    f.put(gx, gg + 1, gz, leaf)
                    if size == 3 and dx == 1 and f.get(gx, gg + 2, gz) is None:
                        f.put(gx, gg + 2, gz, leaf)
                    f.reserved.add((gx, gz))
        bushes += 1
    rocks = 0
    for _ in range(600):
        if rocks >= 34:
            break
        x, z = rng.randrange(12, SIZE - 12), rng.randrange(12, SIZE - 12)
        if ok(x, z, 6, 5):
            boulder(f, x, z, rng.choice((1, 1, 1, 2)), 1000 + rocks * 7)
            rocks += 1


def ponds(f):
    """小川のはじまりの泉（北西の岩の間）と、終わりの池（南東。池の向こうは白く抜け落ちている）"""
    for (cx, cz, r, lost_side) in ((STREAM[0][0] - 2, STREAM[0][1] - 4, 6.5, False), (STREAM[-1][0] + 4, STREAM[-1][1] + 4, 8.0, True)):
        for x in range(int(cx - r - 4), int(cx + r + 5)):
            for z in range(int(cz - r - 4), int(cz + r + 5)):
                if not (3 <= x < SIZE - 3 and 3 <= z < SIZE - 3):
                    continue
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz) + 0.9 * math.sin(x * 0.7 + z * 0.4)
                g = f.top(x, z)
                if d < r:
                    bed = WATER - 2 if d < r - 2 else WATER - 1
                    for yy in range(bed + 1, g + 4):
                        if f.get(x, yy, z) is not None:
                            f.put(x, yy, z, block("air"))
                    f.put(x, bed, z, block(R.pick([("gravel", 3), ("sand", 2), ("clay", 1)], x, z, 1100)))
                    for yy in range(bed + 1, WATER + 1):
                        f.put(x, yy, z, block("water", level=0))
                    for yy in range(bed - 3, bed):
                        if f.get(x, yy, z) is None:
                            f.put(x, yy, z, block("dirt"))
                    f.height[x, z] = bed
                    f.reserved.add((x, z))
                elif d < r + 2.5:
                    # 岸：水面の高さにそろえ、砂と石
                    for yy in range(WATER + 1, g + 4):
                        if f.get(x, yy, z) is not None:
                            f.put(x, yy, z, block("air"))
                    f.put(x, WATER, z, block(R.pick([("sand", 2), ("mossy_cobblestone", 1), ("gravel", 1)], x, z, 1101)))
                    for yy in range(WATER - 3, WATER):
                        if f.get(x, yy, z) is None:
                            f.put(x, yy, z, block("dirt"))
                    f.height[x, z] = WATER
                    f.reserved.add((x, z))
                elif lost_side and d < r + 7 and (x + z) > (cx + cz) + 2:
                    # 池の向こう（南東）は、白く抜け落ちている：水が白い欠けへ消えていく
                    f.put(x, g, z, block("amemaru:lost_blank"))
                    for yy in range(g + 1, g + 3):
                        if f.get(x, yy, z) is not None and f.get(x, yy, z)[0] in ("grass", "fern", "tall_grass", "oak_leaves"):
                            f.put(x, yy, z, block("air"))
                    f.lost.add((x, z))
                    f.reserved.add((x, z))
        if not lost_side:
            for k in range(6):
                a = k * math.tau / 6 + 0.4
                bx, bz = int(cx + math.cos(a) * (r + 2.2)), int(cz + math.sin(a) * (r + 2.2))
                boulder(f, bx, bz, 1, 1200 + k)


# ================================================================ まわりの格子（地平線の薄い線）とバリア
def boundary(f):
    pane_state = pane("light_blue_stained_glass_pane")
    n = SIZE
    for i in range(n):
        for (x, z) in ((i, 0), (i, n - 1), (0, i), (n - 1, i)):
            g = f.top(x, z)
            along = i
            for yy in range(g + 1, HEIGHT - 1):
                if along % 8 == 0 or yy % 8 == 0:
                    f.put(x, yy, z, pane_state)
        # バリアは格子から1つ空けて置く（格子の板ガラスが、見えないバリアの方へつながらないように）
        for (x, z) in ((i, 2), (i, n - 3), (2, i), (n - 3, i)):
            if 2 <= x <= n - 3 and 2 <= z <= n - 3:
                g = f.top(x, z)
                for yy in range(g + 1, HEIGHT):
                    if f.get(x, yy, z) is None:
                        f.put(x, yy, z, block("barrier"))


# ================================================================ 草・花
def vegetation(f):
    flowers = smooth_noise(SIZE, 11, 31)
    kinds = smooth_noise(SIZE, 23, 32)
    for x in range(2, SIZE - 2):
        for z in range(2, SIZE - 2):
            if (x, z) in f.reserved:
                continue
            g = f.top(x, z)
            st = f.get(x, g, z)
            if st is None or st[0] != "grass_block" or f.get(x, g + 1, z) is not None:
                continue
            r = R.at(x, z, 200)
            if flowers[x, z] > 0.45 and r < 0.30:
                k = kinds[x, z]
                name = "dandelion" if k < -0.4 else "oxeye_daisy" if k < -0.1 else "azure_bluet" if k < 0.2 else "cornflower" if k < 0.5 else "poppy"
                f.put(x, g + 1, z, block(name))
            elif r < 0.30:
                f.put(x, g + 1, z, block("grass"))
            elif r < 0.34 and f.get(x, g + 2, z) is None:
                f.put(x, g + 1, z, block("tall_grass", half="lower"))
                f.put(x, g + 2, z, block("tall_grass", half="upper"))
            elif r < 0.36:
                f.put(x, g + 1, z, block("fern"))
    # ロストの欠けのまわりの草は、白く枯れたように少なくする（欠けの上には何も生えない）
    for (x, z) in f.lost:
        g = f.top(x, z)
        if f.get(x, g + 1, z) is not None and f.get(x, g + 1, z)[0] in ("grass", "fern", "tall_grass"):
            f.put(x, g + 1, z, block("air"))


# ================================================================ 階の定義（floors/floor1.json）
def floor_json(cores, seal_cells):
    xs = [c[0] for c in seal_cells]
    ys = [c[1] for c in seal_cells]
    zs = [c[2] for c in seal_cells]
    hx, hz = HALL
    y = BOSS_FLOOR + 1
    data = {
        "_comment": "塔の1階「始まりの草原」（tools/mapgen/build_floor1.py が書き出す。手で直すときは、スクリプトを直して作り直す）",
        "floor": 1,
        "chapter": 1,
        "name": "始まりの草原",
        "chapter_title": "第1章　失われた古代領域",
        "recommended_level": [1, 4],
        "origin": list(ORIGIN),
        "size": [SIZE, HEIGHT, SIZE],
        "structure": "amemaru:map/floor1/",
        "piece_size": PIECE,
        "entrance": {"pos": [ENTRY[0] + 0.5, ENTRY_FLOOR + 1, ENTRY[1] - 8.5], "yaw": 180},
        "return_circle": {"center": [ENTRY[0] + 0.5, ENTRY_FLOOR + 1, ENTRY[1] + 0.5], "radius": 2.6},
        "boss_room": {
            "boss": "amemaru:veldhorn",
            "name": "草原の主の広間",
            "spawn": {"pos": [hx + 0.5, y, hz - 6.5], "yaw": 0},
            "arena": {"center": [hx + 0.5, y, hz + 0.5], "radius": 30.0, "y_min": BOSS_FLOOR - 1, "y_max": BOSS_FLOOR + 14},
            "seal": [[min(xs), min(ys), min(zs)], [max(xs), max(ys), max(zs)]],
            "start_radius": 20,
            "respawn_seconds": 120,
            "max_players": 8,
            "hp_per_extra_player": 0.6,
            "exit": {"center": [hx + 0.5, y, hz - 19.5], "radius": 2.6},
        },
        "pillars": [{"pos": list(c), "line": PILLAR_LINES[i]} for i, c in enumerate(cores)],
        "pillars_done": "",
        "tablet": {"pos": [TABLET[0], BOSS_FLOOR + 2, TABLET[1]], "title": "道標の石板・一",
                   "text": "ひとりで来たのなら、引き返せ。この先は、手をつなぐ者のための道"},
        "spawns": spawn_zones(),
    }
    return data


def spawn_zones():
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=56, route=None, y=14):
        z = {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
             "respawn_seconds": respawn, "group": list(group), "activation": activation}
        if route:
            z["route"] = [[x, y, zz] for (x, zz) in route]
        return z
    return [
        zone("meadow_south", "tsunomofu", 150, 300, 20, 7, 40, (3, 5)),
        zone("meadow_east", "tsunomofu", 252, 296, 20, 7, 40, (3, 5)),
        zone("meadow_west", "tsunomofu", 112, 196, 18, 6, 40, (3, 4)),
        zone("meadow_north", "tsunomofu", 268, 140, 18, 6, 45, (3, 4)),
        zone("road_south", "ishikure_soldier", 180, 280, 6, 2, 50, (1, 1), 64,
             route=[(186, 304), (175, 280), (173, 262), (179, 244)]),
        zone("road_north", "ishikure_soldier", 202, 160, 6, 2, 50, (1, 1), 64,
             route=[(206, 182), (206, 166), (201, 146), (194, 128)]),
        zone("ruins_east", "ishikure_soldier", PILLARS[1][0], PILLARS[1][1], 6, 2, 60, (1, 1), 56,
             route=[(PILLARS[1][0] - 7, PILLARS[1][1] - 6), (PILLARS[1][0] + 7, PILLARS[1][1] - 6),
                    (PILLARS[1][0] + 7, PILLARS[1][1] + 6), (PILLARS[1][0] - 7, PILLARS[1][1] + 6)]),
        zone("crystals_east", "crystal_bee", 282, 236, 10, 4, 45, (1, 2)),
        zone("crystals_west", "crystal_bee", 128, 142, 10, 4, 45, (1, 2)),
        zone("crystals_north", "crystal_bee", 300, 104, 10, 3, 50, (1, 2)),
        zone("lost_west", "kakekiba_wolf", 58, 300, 16, 3, 60, (1, 2)),
        zone("lost_north", "kakekiba_wolf", 330, 86, 16, 3, 60, (1, 2)),
    ]


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
    terrain(f)
    fill_ground(f)
    bridge(f)
    for (cx, cz, r, s) in ((140, 330, 2, 1), (236, 330, 2, 2), (300, 270, 3, 3), (40, 200, 2, 4), (230, 120, 2, 5),
                           (150, 90, 2, 6), (330, 140, 2, 7), (100, 270, 1, 8), (270, 200, 1, 9), (60, 140, 2, 10)):
        boulder(f, cx, cz, r, 300 + s)
    hall_seal = hall(f)
    entrance(f)
    cores = [pillar(f, i) for i in range(3)]
    ruins(f)
    for (cx, cz, s) in ((282, 236, 1), (128, 142, 2), (300, 104, 3)):
        crystal_outcrop(f, cx, cz, 400 + s)
    # 道標（道に沿って、左右かわるがわる）
    pts = []
    for (x0, z0), (x1, z1) in zip(ROAD, ROAD[1:]):
        seg = math.hypot(x1 - x0, z1 - z0)
        for k in range(int(seg)):
            t = k / seg
            pts.append((x0 + (x1 - x0) * t, z0 + (z1 - z0) * t, (x1 - x0) / seg, (z1 - z0) / seg))
    for i in range(14, len(pts) - 10, 26):
        px_, pz_, ux, uz = pts[i]
        side = 1 if (i // 26) % 2 == 0 else -1
        x, z = int(px_ - uz * 4.5 * side), int(pz_ + ux * 4.5 * side)
        if f.stream_d[x, z] > 4 and (x, z) not in f.reserved:
            waystone(f, x, z, 500 + i)
    for (cx, cz, r, count, s, birch) in ((304, 58, 16, 9, 1, 0.3), (60, 160, 14, 7, 2, 0.4), (330, 176, 10, 5, 3, 0.2),
                                         (120, 304, 12, 6, 4, 0.5), (262, 344, 12, 6, 5, 0.3), (96, 92, 14, 8, 6, 0.6),
                                         (236, 70, 8, 4, 7, 0.2), (30, 360, 10, 5, 8, 0.3), (350, 290, 10, 4, 9, 0.4),
                                         (150, 40, 9, 4, 10, 0.5)):
        grove(f, cx, cz, r, count, 600 + s * 17, birch)
    scatter(f)
    ponds(f)
    boundary(f)
    vegetation(f)
    f.finalize()
    return f, cores, hall_seal


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else None
    struct_dir = os.path.join(out, "floor1") if out else os.path.join(RES, "structures", "map", "floor1")
    json_path = os.path.join(out, "floor1.json") if out else os.path.join(RES, "amemaru", "floors", "floor1.json")
    f, cores, seal = build()
    count = save_pieces(f, struct_dir)
    data = floor_json(cores, seal)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", len(f.blocks), "石柱の核:", cores, "封印:", len(seal))
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor_figures
        floor_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
