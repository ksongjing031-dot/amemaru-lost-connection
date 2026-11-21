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

11月22日に足したこと【補完】（「一日一層の感じにしては内容が薄く暇になってしまいます。もっとマップを複雑にしてもいい
かもしれません。現在30分程度で2人で一層をクリアできました。二人とも3レベ」というご指示から。2人で1時間半〜2時間、
ボスの前でレベル4〜5ほどを目安にする）：
  - 西の丘：上が平らな台地で、まわりは崖。南東のふもとから南・西を巻く古い石の道でだけ上がれる。台地に石柱と、
    その守り手「群れの長」（大きなツノモフ）。倒した人とパーティだけが石柱を起動できる。東の崖に、光る苔の洞と宝箱
  - 東の遺跡：崩れた外壁に囲まれた大きな遺跡。真ん中の広場の東に、石柱のある「奥の院」（高い壁で閉じた庭）。
    奥の院の門は古代の格子で、北の庭と南の庭の「仕掛け石」を12秒のうちに両方押すと、1分ひらく（2人なら同時に、
    1人なら走って）。奥の院の中の階段から壁の上へ上がり、北の水の溜まりへ飛び下りて出る
  - 北西の林：木が茂って見通しの悪い林。白い欠けが石柱の核を覆っていて、林の欠け牙の狼が落とす「光のかけら」を
    3つ持って触れると起動できる
  - 2本目の小川（西の泉から、入口の北を通って本流へ）と、2つ目の石橋。小川を渡る飛び石
  - 草原の生け垣と石積みの垣（ところどころ切れ目）。北東の結晶の丘と結晶の窪み
  - 古代の宝箱10個（人それぞれ1回ずつ開けられる。中身は宝箱の決まり chests/floor1_ancient）
  - 湧く所を増やした（丘の上の群れ・遺跡の中の巡回・林の狼・結晶の丘の蜂・南の野の群れ）
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
PILLARS = [(64, 232), (333, 195), (96, 104)]    # 西の丘・東の遺跡の奥の院・北西の林（11月22日、東の石柱を奥の院の中へ）
PILLAR_FACING = ["east", "west", "south"]       # 石柱の台が向く方（道から来る人の方）
PILLAR_LINES = ["この塔は、つながる者のために建てられた", "ひとりの手では、門は動かない", "手をつなげ。道は、その先にある"]

ROAD = [(192, 343), (190, 322), (184, 300), (174, 280), (172, 262), (178, 244), (188, 226), (196, 210), (204, 194),
        (207, 176), (204, 158), (198, 140), (192, 124), (192, 112)]
STREAM = [(30, 52), (48, 64), (70, 84), (92, 120), (112, 150), (134, 176), (160, 196), (196, 208), (226, 222),
          (252, 246), (276, 272), (300, 296), (322, 318), (336, 334)]

# ---- 11月22日【補完】
HILL = (62, 228)                                # 西の丘の真ん中
HILL_R = 19.5                                   # 台地の半径（この内は高さ HILL_TOP の平らな地面。まわりは崖）
HILL_TOP = 27                                   # 台地の地面の高さ
LEDGE_W = 4.0                                   # 巻き道の幅（台地の縁の外側）
LEDGE_FROM, LEDGE_TO = 40.0, 262.0              # 巻き道の角度（東が0・南が90・西が180・北が270。南東のふもとから南・西を回る）
CAVE_MOUTH = 12.0                               # 洞の口の角度（東の崖。巻き道の外）
STREAM2 = [(28, 268), (52, 274), (80, 286), (108, 298), (136, 306), (162, 311), (187, 312), (214, 310), (242, 302),
           (266, 290), (288, 284)]               # 2本目の小川（西の泉から、入口の北を通って本流へ）
RUINS = (288, 170, 347, 221)                    # 東の遺跡の外壁（x0, z0, x1, z1）
SANCTUM = (320, 184, 343, 207)                  # 奥の院の壁の外まわり（壁の厚さ 2、高さ 5）
GATE_ZS = (193, 196)                            # 奥の院の門（西の壁）の z の幅
SWITCHES = [(296, 177), (296, 214)]             # 仕掛け石（北の庭・南の庭）
SWITCH_WINDOW, GATE_OPEN = 12, 60               # 2つを押す秒数・門がひらく秒数
GUARDIAN = (56, 220)                            # 群れの長の出る所（西の丘の台地）
CRYSTAL_HILLS = [(318, 66, 5.0, 14.0), (346, 92, 4.0, 11.0), (300, 40, 3.5, 12.0)]   # 北東の結晶の丘（x, z, 高さ, 半径）
CHEST_LOOT = "amemaru:chests/floor1_ancient"
HOLLOW = (324, 60)                              # 北東の結晶の窪み（結晶の丘の上）

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
        self.ledge = set()         # 西の丘の巻き道の (x, z)
        self.in_hill_top = None    # 西の丘の台地の中か（SIZE×SIZE）
        self.hill_d = None         # 西の丘の真ん中からの距離（SIZE×SIZE）
        self.ruins_y = 15          # 東の遺跡の地面の高さ
        self.chests = []           # 古代の宝箱（x, y, z）。置いた順が番号
        self.switches = []         # 仕掛け石（x, y, z）
        self.gate = None           # 奥の院の門の格子（[x0, y0, z0], [x1, y1, z1]）
        self.guardian = None       # 群れの長の出る所（x, y, z）

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
    # 北東の結晶の丘（11月22日【補完】。なだらかな高まり）
    for (cx, cz, hh, rr) in CRYSTAL_HILLS:
        h += hh * (1.0 - smoothstep(rr * 0.35, rr, np.hypot(X - cx, Z - cz)))
    stream1_d, _, _ = polyline_distance(STREAM)
    stream2_d, _, _ = polyline_distance(STREAM2)
    stream_d = np.minimum(stream1_d, stream2_d)
    road_d, road_along, road_len = polyline_distance(ROAD)
    f.stream_d, f.road_d = stream_d, road_d
    f.stream1_d = stream1_d
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
    # 東の遺跡（11月22日）：外壁の中をひとつの高さにそろえる
    rx0, rz0, rx1, rz1 = RUINS
    f.ruins_y = int(np.round(h[(rx0 + rx1) // 2, (rz0 + rz1) // 2]))
    rcx, rcz = (rx0 + rx1 + 1) / 2.0, (rz0 + rz1 + 1) / 2.0
    box_d = np.maximum(np.abs(X - rcx) - (rx1 - rx0 + 1) / 2.0, np.abs(Z - rcz) - (rz1 - rz0 + 1) / 2.0)
    w_ruins = 1.0 - smoothstep(1.0, 12.0, box_d)
    h = h * (1 - w_ruins) + f.ruins_y * w_ruins
    for i, (px, pz) in enumerate(PILLARS):
        if i == 1:
            continue
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
    hill_terrain(f, X, Z)
    crystal_hollow_terrain(f, *HOLLOW)


def ledge_height(t):
    """巻き道の高さ（t は 0〜1：ふもと〜台地）"""
    return int(round(LEDGE_START_Y + (HILL_TOP - LEDGE_START_Y) * t))


LEDGE_START_Y = 15


def hill_polar(x, z):
    """西の丘の真ん中からの距離と角度（東が0・南が90。マスの真ん中で測る）"""
    dx, dz = x + 0.5 - HILL[0], z + 0.5 - HILL[1]
    return math.hypot(dx, dz), math.degrees(math.atan2(dz, dx)) % 360.0


def ledge_t(a):
    """角度 a が巻き道の中なら 0〜1、外なら None"""
    if LEDGE_FROM <= a <= LEDGE_TO:
        return (a - LEDGE_FROM) / (LEDGE_TO - LEDGE_FROM)
    return None


def hill_terrain(f, X, Z):
    """西の丘（11月22日【補完】）：台地（高さ HILL_TOP）と、まわりの崖・すそ・巻き道。巻き道でだけ上がれる
    （崖は2段以上の段になるので、跳んでも上がれない）"""
    global LEDGE_START_Y
    d = np.hypot(X - HILL[0], Z - HILL[1])
    a = np.degrees(np.arctan2(Z - HILL[1], X - HILL[0])) % 360.0
    noise = smooth_noise(SIZE, 7, 41)
    base = f.height.copy()
    # ふもとの高さ（巻き道の始まり）
    sx = int(HILL[0] + math.cos(math.radians(LEDGE_FROM)) * (HILL_R + LEDGE_W / 2 + 1))
    sz = int(HILL[1] + math.sin(math.radians(LEDGE_FROM)) * (HILL_R + LEDGE_W / 2 + 1))
    LEDGE_START_Y = int(base[sx, sz])
    # すそ（崖の下の、低い盛り上がり）：最大2段
    skirt = np.round(2.0 * (1.0 - smoothstep(HILL_R, HILL_R + 10.0, d))).astype(int)
    edge_r = HILL_R + noise * 2.2 + smooth_noise(SIZE, 3, 42) * 0.7   # 崖の縁（でこぼこ）
    in_top = d < edge_r
    f.height = np.where(in_top, HILL_TOP, np.where(d < HILL_R + 12.0, base + skirt, f.height))
    # 巻き道（崖の縁の外側の幅 LEDGE_W）
    f.ledge = set()
    for x in range(int(HILL[0] - HILL_R - 8), int(HILL[0] + HILL_R + 9)):
        for z in range(int(HILL[1] - HILL_R - 8), int(HILL[1] + HILL_R + 9)):
            r, ang = hill_polar(x, z)
            t = ledge_t(ang)
            if t is None or not (edge_r[x, z] <= r < HILL_R + 1.2 + LEDGE_W):
                continue
            f.height[x, z] = ledge_height(t)
            f.ledge.add((x, z))
    f.in_hill_top = in_top
    f.hill_d = d


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
            if f.hill_d[x, z] < HILL_R + 12.0:
                bottom = min(bottom, 10)        # 西の丘は中まで詰まった土と岩（洞を掘るため。11月22日）
            cliff = top - int(hmin[x, z]) >= 3  # 崖の面：岩を見せる
            sd = f.stream_d[x, z]
            rd = f.road_d[x, z]
            for y in range(bottom, top):
                if cliff and y >= int(hmin[x, z]) - 1:
                    # 崖の面：岩の横の筋（地層）と苔
                    band = (y + int(R.at(x // 4, z // 4, 5) * 2)) % 6
                    if band == 0:
                        st = R.pick([("tuff", 3), ("andesite", 1)], x, y, z, 6)
                    elif band == 3:
                        st = R.pick([("andesite", 3), ("polished_andesite", 0), ("stone", 1)], x, y, z, 7)
                    else:
                        st = R.pick([("stone", 6), ("andesite", 2), ("cobblestone", 1), ("mossy_cobblestone", 2)], x, y, z, 3)
                elif y >= top - 2:
                    st = "dirt" if R.at(x, y, z, 1) < 0.85 else "coarse_dirt"
                else:
                    st = R.pick([("stone", 6), ("andesite", 2), ("dirt", 2), ("gravel", 1)], x, y, z, 2)
                f.put(x, y, z, block(st))
            if (x, z) in f.ledge:
                # 西の丘の巻き道：古い石の道（11月22日）
                st = R.pick(ROAD_STONE, x, z, 4)
                f.put(x, top, z, block(st))
                f.reserved.add((x, z))
                continue
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
    # 東の遺跡は ruins_compound（11月22日に、奥の院のある大きな遺跡に作りかえた）
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
        if f.hill_d[x, z] < HILL_R + 12.0 and not f.in_hill_top[x, z]:
            return False                   # 西の丘の崖とすそ（木や岩を踏み台にして崖を上がれないように）
        rx0, rz0, rx1, rz1 = RUINS
        if rx0 - 4 <= x <= rx1 + 4 and rz0 - 4 <= z <= rz1 + 4:
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
    for (cx, cz, r, lost_side) in ((STREAM[0][0] - 2, STREAM[0][1] - 4, 6.5, False), (STREAM[-1][0] + 4, STREAM[-1][1] + 4, 8.0, True),
                                   (STREAM2[0][0] - 4, STREAM2[0][1] - 1, 5.5, False)):
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
        else:
            # 池の真ん中の小島（11月22日【補完】。白い欠けの方を向いた古代の宝箱）
            ix, iz = int(cx), int(cz)
            for x in range(ix - 2, ix + 3):
                for z in range(iz - 2, iz + 3):
                    if math.hypot(x - ix, z - iz) > 1.9:
                        continue
                    for yy in range(WATER - 3, WATER):
                        f.put(x, yy, z, block("dirt"))
                    f.put(x, WATER, z, block("moss_block") if (x + z) % 3 else block("sand"))
                    f.height[x, z] = WATER
                    f.reserved.add((x, z))
            f.put(ix - 1, WATER + 1, iz - 1, wall("mossy_stone_brick"))
            f.put(ix - 1, WATER + 2, iz - 1, lantern(False))
            chest(f, ix, WATER + 1, iz, "south")


# ================================================================ 11月22日に足した所【補完】
FACE_OF = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def chest(f, x, y, z, facing):
    """古代の宝箱（人それぞれ1度だけ開けられる）。置いた順が番号"""
    index = len(f.chests)
    f.put(x, y, z, block("amemaru:tower_chest", facing=facing),
          Compound({"id": String("amemaru:floor_thing"), "floor": Int(1), "index": Int(index), "group": String("")}))
    f.chests.append((x, y, z))
    f.reserved.add((x, z))
    return index


def glow_lichen(face):
    props = {"north": False, "south": False, "east": False, "west": False, "up": False, "down": False, "waterlogged": False}
    props[face] = True
    return block("glow_lichen", **props)


def solid_at(f, x, y, z):
    st = f.get(x, y, z)
    return st is not None and kind_of(st[0]) == "cube" and st[0] not in ("water", "barrier")


def kind_of(name):
    from mapgen.world import kind
    return kind(name)


def hill_features(f):
    """西の丘：巻き道の手すりと灯り・ふもとの案内・東の崖の光る苔の洞（奥に宝箱）・台地の西の見張り台の跡（宝箱）・台地の木"""
    # 巻き道の外の縁（外の地面より2段以上高い所）に、ところどころ崩れた低い石の手すり
    for (x, z) in sorted(f.ledge):
        top = f.top(x, z)
        outer = False
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in f.ledge or f.in_hill_top[nx, nz]:
                continue
            if f.height[nx, nz] <= top - 2:
                outer = True
        if outer and R.at(x, z, 1300) < 0.82:
            f.put(x, top + 1, z, wall("mossy_stone_brick") if R.at(x, z, 1301) < 0.5 else wall("stone_brick"))
    # 灯り（外の縁の柱の上にランタン。30度ごと）と、ふもとの案内
    for a in np.arange(LEDGE_FROM + 14.0, LEDGE_TO - 4.0, 30.0):
        r = HILL_R + 1.2 + LEDGE_W - 0.7
        x = int(math.floor(HILL[0] + 0.5 + math.cos(math.radians(a)) * r - 0.5))
        z = int(math.floor(HILL[1] + 0.5 + math.sin(math.radians(a)) * r - 0.5))
        if (x, z) not in f.ledge:
            continue
        top = f.top(x, z)
        f.put(x, top + 1, z, wall("stone_brick"))
        f.put(x, top + 2, z, wall("stone_brick"))
        f.put(x, top + 3, z, lantern(False))
    sa = math.radians(LEDGE_FROM - 4.0)
    sx = int(HILL[0] + math.cos(sa) * (HILL_R + 1.2 + LEDGE_W + 2.5))
    sz = int(HILL[1] + math.sin(sa) * (HILL_R + 1.2 + LEDGE_W + 2.5))
    f.put(sx, f.top(sx, sz) + 1, sz, block("spruce_sign", rotation=14, waterlogged=False),
          sign_nbt(["西の丘", "", "古い巻き道は", "丘の南を回って上へ"]))
    f.reserved.add((sx, sz))
    hill_cave(f)
    # 台地の西の見張り台の跡（崩れた石の輪。中に宝箱）
    a = math.radians(160.0)
    lx, lz = int(HILL[0] + math.cos(a) * 12.5), int(HILL[1] + math.sin(a) * 12.5)
    g = HILL_TOP
    for x in range(lx - 4, lx + 5):
        for z in range(lz - 4, lz + 5):
            d = math.hypot(x - lx, z - lz)
            if d <= 2.6:
                f.put(x, g, z, white(x, g, z, 1310))
                f.reserved.add((x, z))
            elif d <= 3.6:
                ang = math.degrees(math.atan2(z - lz, x - lx)) % 360.0
                if abs(((ang - 0.0) + 180) % 360 - 180) < 28:      # 台地の内側（東）が入口
                    f.put(x, g, z, white(x, g, z, 1311))
                    f.reserved.add((x, z))
                    continue
                hgt = 1 + int(R.at(x, z, 1312) * 3)
                for yy in range(g + 1, g + 1 + hgt):
                    f.put(x, yy, z, block(R.pick([("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 2)], x, yy, z, 1313)))
                f.reserved.add((x, z))
    chest(f, lx - 1, g + 1, lz, "east")
    f.put(lx + 1, g + 1, lz - 1, wall("mossy_stone_brick"))
    f.put(lx + 1, g + 2, lz - 1, lantern(False))
    # 台地の木（白樺と樫を少し）
    grove(f, HILL[0] + 4, HILL[1] + 4, 11, 5, 1320, birch=0.6)


def hill_cave(f):
    """東の崖の、光る苔の洞（11月22日【補完】）：口から丘の中へ掘った通路と、奥の円い洞。苔と翠の結晶と、天井の光る石。
    奥に古代の宝箱"""
    a = math.radians(CAVE_MOUTH)
    ux, uz = math.cos(a), math.sin(a)
    px, pz = -uz, ux
    mx = int(math.floor(HILL[0] + 0.5 + ux * (HILL_R + 3.0)))
    mz = int(math.floor(HILL[1] + 0.5 + uz * (HILL_R + 3.0)))
    floor_y = f.top(mx, mz)
    carved = set()

    def carve(x, y, z):
        if f.inside(x, y, z):
            f.put(x, y, z, block("air"))
            carved.add((x, y, z))
    # 通路（幅3・高さ3）：崖の外 r = HILL_R + 3 から、奥の洞の手前 r = 11 まで
    r = HILL_R + 3.0
    while r >= 10.5:
        cx, cz = HILL[0] + 0.5 + ux * r, HILL[1] + 0.5 + uz * r
        for s_ in (-1.2, -0.6, 0.0, 0.6, 1.2):
            x, z = int(math.floor(cx + px * s_)), int(math.floor(cz + pz * s_))
            for yy in range(floor_y + 1, floor_y + 4):
                carve(x, yy, z)
            f.put(x, floor_y, z, block(R.pick([("moss_block", 3), ("gravel", 1), ("mossy_cobblestone", 2), ("stone", 2)], x, z, 1330)))
        r -= 0.5
    # 奥の洞（半径5・高さ5の丸天井）
    cx, cz = HILL[0] + 0.5 + ux * 6.0, HILL[1] + 0.5 + uz * 6.0
    for x in range(int(cx) - 7, int(cx) + 8):
        for z in range(int(cz) - 7, int(cz) + 8):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if d > 5.6:
                continue
            ceil = floor_y + 1 + int(round(4.6 * math.sqrt(max(0.0, 1.0 - (d / 5.8) ** 2))))
            for yy in range(floor_y + 1, max(floor_y + 3, ceil) + 1):
                carve(x, yy, z)
            f.put(x, floor_y, z, block(R.pick([("moss_block", 4), ("mossy_cobblestone", 1), ("stone", 1)], x, z, 1331)))
            if d < 1.6:
                # 真ん中の小さな泉
                f.put(x, floor_y, z, block("water", level=0))
                f.put(x, floor_y - 1, z, block("gravel"))
    # 壁の苔・光る苔、天井の光る石、翠の結晶
    for (x, y, z) in sorted(carved):
        for (dx, dy, dz, face) in ((1, 0, 0, "east"), (-1, 0, 0, "west"), (0, 0, 1, "south"), (0, 0, -1, "north"), (0, 1, 0, "up")):
            n = (x + dx, y + dy, z + dz)
            if n in carved or not solid_at(f, *n):
                continue
            if R.at(*n, 1332) < 0.35:
                f.put(*n, block(R.pick([("moss_block", 2), ("mossy_cobblestone", 2), ("tuff", 1)], *n, 1333)))
            if f.get(x, y, z) is None and R.at(x, y, z, 1334 + dy) < (0.22 if face != "up" else 0.12):
                f.put(x, y, z, glow_lichen(face))
    for (x, y, z) in sorted(carved):
        if (x + z) % 4 == 0 and (x - z) % 3 == 0 and (x, y + 1, z) not in carved and solid_at(f, x, y + 1, z) and y >= floor_y + 3:
            f.put(x, y + 1, z, block("sea_lantern"))
    # 通路の天井にも、4つごとに光る石
    r = HILL_R + 1.0
    while r >= 11.0:
        x = int(math.floor(HILL[0] + 0.5 + ux * r))
        z = int(math.floor(HILL[1] + 0.5 + uz * r))
        f.put(x, floor_y + 4, z, block("sea_lantern"))
        r -= 4.0
    rng = random.Random(1335)
    for k in range(9):
        ang = rng.uniform(0, math.tau)
        d = rng.uniform(3.2, 5.0)
        x, z = int(math.floor(cx + math.cos(ang) * d)), int(math.floor(cz + math.sin(ang) * d))
        if (x, floor_y + 1, z) in carved and f.get(x, floor_y + 1, z) is None:
            for yy in range(floor_y + 1, floor_y + 1 + rng.randint(1, 2)):
                f.put(x, yy, z, block("green_stained_glass"))
    # 奥の宝箱（通路の方を向く）
    bx, bz = int(math.floor(cx - ux * 4.2)), int(math.floor(cz - uz * 4.2))
    f.put(bx, floor_y, bz, block("mossy_stone_bricks"))
    chest(f, bx, floor_y + 1, bz, "east")
    # 口の上のつる
    for s_ in (-2.0, -1.0, 0.0, 1.0, 2.0):
        x = int(math.floor(HILL[0] + 0.5 + ux * (HILL_R + 1.4) + px * s_))
        z = int(math.floor(HILL[1] + 0.5 + uz * (HILL_R + 1.4) + pz * s_))
        for yy in range(floor_y + 4, floor_y + 8):
            if f.get(x, yy, z) is None and f.get(x - 1, yy, z) is not None:
                f.put(x, yy, z, block("vine", north=False, south=False, east=False, west=True, up=False))
    f.cave_floor = floor_y


# ---------------------------------------------------------------- 東の遺跡（外壁・中庭・広場・奥の院・回廊）
def ruins_compound(f):
    x0, z0, x1, z1 = RUINS
    g = f.ruins_y
    sx0, sz0, sx1, sz1 = SANCTUM
    gz0, gz1 = GATE_ZS

    def ground(x, z, st):
        """地面をそろえて敷く（遺跡の中は高さ g）"""
        top = f.top(x, z)
        for yy in range(g + 1, max(g + 1, top + 4)):
            if f.get(x, yy, z) is not None:
                f.put(x, yy, z, block("air"))
        for yy in range(g - 3, g):
            if f.get(x, yy, z) is None:
                f.put(x, yy, z, block("stone"))
        f.put(x, g, z, st)
        f.height[x, z] = g
        f.reserved.add((x, z))

    def column(x, z, hgt, mats, salt, top_mat=None):
        for yy in range(g + 1, g + 1 + hgt):
            st = block(R.pick(mats, x, yy, z, salt))
            if top_mat and yy == g + hgt:
                st = block(top_mat)
            f.put(x, yy, z, st)

    old = [("mossy_stone_bricks", 3), ("stone_bricks", 3), ("cracked_stone_bricks", 2), ("mossy_cobblestone", 1)]
    # 地面：遺跡の中は古い敷石（ところどころ草）。奥の院と広場は白い石
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if not (0 <= x < SIZE and 0 <= z < SIZE):
                continue
            plaza = x0 < x < sx0 and 185 <= z <= 206
            r = R.at(x, z, 1400)
            if sx0 <= x <= sx1 and sz0 <= z <= sz1:
                st = white(x, g, z, 1401)
            elif plaza:
                st = white(x, g, z, 1402) if r < 0.6 else block(R.pick(ROAD_STONE, x, z, 1403)) if r < 0.9 else block("moss_block")
            elif r < 0.55:
                st = block(R.pick(ROAD_STONE, x, z, 1404))
            elif r < 0.62:
                st = block("moss_block")
            else:
                st = block("grass_block", snowy=False)
            ground(x, z, st)
    # 外壁（厚さ1・高さ2〜4。崩れた所は1。西の正面・北・南に口）
    openings = [("w", 193, 198), ("n", 326, 330), ("s", 326, 330)]

    def opening(side, t):
        return any(sd == side and a <= t <= b for sd, a, b in openings)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            side = "w" if x == x0 else "e" if x == x1 else "n" if z == z0 else "s"
            t = z if side in ("w", "e") else x
            if opening(side, t):
                continue
            hgt = 2 + int(R.at(x, z, 1410) * 3)
            if R.at(x // 3, z // 3, 1411) < 0.18:
                hgt = 1                        # 崩れて低くなった所（跳んで越えられる）
            column(x, z, hgt, old, 1412)
    # 西の正面の口の両脇の門柱
    for z in (192, 199):
        column(x0, z, 6, [("polished_diorite", 3), ("calcite", 1)], 1413, "chiseled_stone_bricks")
    # 中の壁（高さ3。中庭は広場への口だけ）
    inner = []
    inner += [(x, 184) for x in range(x0 + 1, 310) if not 300 <= x <= 302]        # 北の庭の南の壁（口 x 300〜302）
    inner += [(310, z) for z in range(z0 + 1, 185)]                                 # 北の庭の東の壁
    inner += [(x, 207) for x in range(x0 + 1, 310) if not 300 <= x <= 302]        # 南の庭の北の壁
    inner += [(310, z) for z in range(207, z1)]                                     # 南の庭の東の壁
    inner += [(318, z) for z in range(z0 + 1, 180)]                                 # 北の回廊の折れ（1つ目）
    inner += [(331, z) for z in range(175, 181)]                                    # 北の回廊の折れ（2つ目。奥の院の壁には付けない）
    inner += [(318, z) for z in range(212, z1)]                                     # 南の回廊の折れ
    inner += [(331, z) for z in range(211, 217)]
    inner += [(x, 177) for x in range(338, x1) if not 341 <= x <= 342]             # 北東の物置（口 x 341〜342）
    inner += [(338, z) for z in range(z0 + 1, 177)]
    for (x, z) in inner:
        hgt = 3 if R.at(x, z, 1420) < 0.85 else 4
        column(x, z, hgt, old, 1421)
    # 南の回廊の崩れた所（がれき）
    for x in range(334, 344):
        for z in range(214, 220):
            if R.at(x, z, 1425) < 0.3:
                f.put(x, g + 1, z, block(R.pick([("cobblestone", 2), ("mossy_cobblestone", 2), ("stone_brick_slab", 0)], x, z, 1426)))
    # 奥の院（厚さ2・高さ5の閉じた壁。西に門）
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            wall_cell = x <= sx0 + 1 or x >= sx1 - 1 or z <= sz0 + 1 or z >= sz1 - 1
            if not wall_cell:
                continue
            for yy in range(g + 1, g + 6):
                if x <= sx0 + 1 and gz0 <= z <= gz1 and yy <= g + 4:
                    continue
                if yy == g + 5:
                    st = block("chiseled_stone_bricks") if (x + z) % 5 == 0 else block("stone_bricks")
                elif yy == g + 1:
                    st = block("mossy_stone_bricks") if R.at(x, z, 1430) < 0.5 else block("stone_bricks")
                else:
                    st = white(x, yy, z, 1431) if R.at(x, yy, z, 1432) < 0.7 else block(R.pick(old, x, yy, z, 1433))
                f.put(x, yy, z, st)
    # 門の格子（古代の格子。閉じている）
    gate_cells = []
    for x in (sx0, sx0 + 1):
        for z in range(gz0, gz1 + 1):
            for yy in range(g + 1, g + 5):
                f.put(x, yy, z, block("amemaru:tower_gate", open=False))
                gate_cells.append((x, yy, z))
    f.gate = ([sx0, g + 1, gz0], [sx0 + 1, g + 4, gz1])
    for z in range(gz0 - 1, gz1 + 2):
        f.put(sx0 - 1, g + 5, z, block("chiseled_stone_bricks") if z in (gz0 - 1, gz1 + 1) else block("polished_andesite"))
    # 門の前の案内（広場の側）
    f.put(sx0 - 3, g + 1, gz1 + 3, block("spruce_sign", rotation=4, waterlogged=False),
          sign_nbt(["奥の院の格子", "北と南の庭の", "仕掛け石を", "時を合わせて押せ"]))
    # 仕掛け石（北の庭・南の庭）。まわりを円く磨いた石に
    f.switches = []
    for i, (wx, wz) in enumerate(SWITCHES):
        for x in range(wx - 2, wx + 3):
            for z in range(wz - 2, wz + 3):
                if math.hypot(x - wx, z - wz) <= 2.3:
                    f.put(x, g, z, block("polished_andesite"))
        f.put(wx, g + 1, wz, block("amemaru:tower_switch", lit=False),
              Compound({"id": String("amemaru:floor_thing"), "floor": Int(1), "index": Int(i), "group": String("ruins")}))
        f.switches.append((wx, g + 1, wz))
        for (dx, dz) in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
            f.put(wx + dx, g + 1, wz + dz, wall("mossy_stone_brick"))
    # 奥の院の中：出口の階段（北の壁の内側を東へ上がり、壁の上へ）と、北の回廊の水の溜まり
    for i, x in enumerate(range(335, 340)):
        for yy in range(g + 1, g + 1 + i):
            f.put(x, yy, sz0 + 2, block("stone_bricks"))
        f.put(x, g + 1 + i, sz0 + 2, stairs("stone_brick", "east"))
    for yy in range(g + 1, g + 6):
        f.put(340, yy, sz0 + 2, block("stone_bricks"))
    f.put(334, g + 1, sz0 + 3, block("spruce_sign", rotation=8, waterlogged=False),
          sign_nbt(["出口", "壁の上へ上がり", "北の水の溜まりへ", "飛び下りる"]))
    for x in range(337, 342):
        for z in range(sz0 - 4, sz0):
            f.put(x, g, z, block("water", level=0))
            f.put(x, g - 1, z, block("gravel"))
            f.put(x, g - 2, z, block("stone"))
    for x in range(336, 343):
        for z in range(sz0 - 5, sz0):
            if f.get(x, g, z) is not None and f.get(x, g, z)[0] != "water":
                f.put(x, g, z, block("mossy_cobblestone"))
    # 奥の院の宝箱と灯り
    chest_in_sanctum = (sx1 - 4, g + 1, sz1 - 4)
    for x in (sx0 + 3, sx1 - 3):
        for z in (sz0 + 3, sz1 - 3):
            if (x, z) == (340, sz0 + 3):
                continue
            f.put(x, g + 1, z, wall("stone_brick"))
            f.put(x, g + 2, z, lantern(False))
    # 北東の物置の宝箱
    chest(f, x1 - 2, g + 1, z0 + 2, "south")
    chest(f, *chest_in_sanctum, "west")
    # 広場の灯りと、中庭の木
    for (x, z) in ((300, 190), (300, 201), (314, 190), (314, 201)):
        f.put(x, g + 1, z, wall("stone_brick"))
        f.put(x, g + 2, z, wall("stone_brick"))
        f.put(x, g + 3, z, lantern(False))
    for (x, z) in ((292, 181), (303, 175), (292, 210), (304, 217)):
        f.put(x, g, z, block("grass_block", snowy=False))
        tree(f, x, z, "oak" if (x + z) % 2 else "birch", 1440 + x)
    # 壁のつる
    for (x, y, z), st in list(f.blocks.items()):
        if not (x0 <= x <= x1 and z0 <= z <= z1) or y <= g + 1 or kind_of(st[0]) != "cube" or "stone" not in st[0]:
            continue
        if sx0 - 3 <= x <= sx1 + 3 and sz0 - 3 <= z <= sz1 + 3:
            continue
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if f.get(nx, y, nz) is None and f.get(nx, g, nz) is not None and R.at(x, y, z, dx * 3 + dz + 1450) < 0.06:
                face = FACE_OF[(-dx, -dz)]
                for yy in range(y, max(g + 1, y - 3), -1):
                    if f.get(nx, yy, nz) is None:
                        props = {"north": False, "south": False, "east": False, "west": False, "up": False}
                        props[face] = True
                        f.put(nx, yy, nz, block("vine", **props))
    return gate_cells


# ---------------------------------------------------------------- 北西の林
def woods(f):
    """北西の林（11月22日【補完】）：木の茂った、見通しの悪い林。苔と羊歯とキノコ、倒れた木。白い欠けが石柱を囲む。
    根元のうろの切り株に宝箱、石柱の後ろの祠の跡に宝箱"""
    cx, cz = 92, 104
    p2x, p2z = PILLARS[2]
    rng = random.Random(1500)
    path_d, _, _ = polyline_distance(WOODS_PATH)

    def in_woods(x, z):
        if not (12 <= x < 170 and 12 <= z < 180):
            return False
        if ((x - cx) / 64.0) ** 2 + ((z - cz) / 68.0) ** 2 > 1.0:
            return False
        if math.hypot(x - HALL[0], z - HALL[1]) < 66 or math.hypot(x - TABLET[0], z - TABLET[1]) < 30:
            return False
        if f.stream_d[x, z] < 5 or f.road_d[x, z] < 8:
            return False
        if math.hypot(x - p2x, z - p2z) < 12.5 or path_d[x, z] < 2.6:
            return False
        return True
    # 林の地面（苔・ポドゾル・粗い土）
    floor_noise = smooth_noise(SIZE, 6, 51)
    for x in range(12, 170):
        for z in range(12, 180):
            if not in_woods(x, z) or (x, z) in f.reserved:
                continue
            g = f.top(x, z)
            st = f.get(x, g, z)
            if st is None or st[0] != "grass_block":
                continue
            v = floor_noise[x, z] + R.at(x, z, 1501) * 0.5
            if v > 0.55:
                f.put(x, g, z, block("podzol", snowy=False))
            elif v > 0.35:
                f.put(x, g, z, block("moss_block"))
            elif v < -0.6:
                f.put(x, g, z, block("coarse_dirt"))
    # 木（間を4〜5あけて、たくさん）
    placed = []
    for _ in range(9000):
        x, z = rng.randrange(14, 168), rng.randrange(14, 178)
        if not in_woods(x, z) or (x, z) in f.reserved:
            continue
        if any(abs(x - px) < 5 and abs(z - pz) < 5 and math.hypot(x - px, z - pz) < 4.4 for px, pz in placed[-400:]):
            continue
        g = f.top(x, z)
        st = f.get(x, g, z)
        if st is not None and st[0] == "podzol":
            f.put(x, g, z, block("grass_block", snowy=False))
        roll = rng.random()
        if roll < 0.16 and dark_oak(f, x, z, 1510 + len(placed)):
            placed.append((x, z))
        elif tree(f, x, z, "birch" if roll < 0.32 else "oak", 1600 + len(placed)):
            placed.append((x, z))
    # 倒れた木（苔をかぶった横の丸太）
    for k in range(16):
        for _ in range(40):
            x, z = rng.randrange(20, 160), rng.randrange(20, 170)
            if in_woods(x, z) and (x, z) not in f.reserved:
                break
        else:
            continue
        axis, (dx, dz) = (("x", (1, 0)) if rng.random() < 0.5 else ("z", (0, 1)))
        length = rng.randint(3, 6)
        cells = [(x + dx * i, z + dz * i) for i in range(length)]
        if any((a, b) in f.reserved or not in_woods(a, b) for a, b in cells):
            continue
        g = max(f.top(a, b) for a, b in cells)
        if any(g - f.top(a, b) > 1 for a, b in cells):
            continue
        for (a, b) in cells:
            f.put(a, g + 1, b, log("oak", axis))
            if rng.random() < 0.5:
                f.put(a, g + 2, b, block("moss_carpet"))
            f.reserved.add((a, b))
    # 羊歯・キノコ（キノコはポドゾルの上だけ）
    for x in range(12, 170):
        for z in range(12, 180):
            if not in_woods(x, z) or (x, z) in f.reserved:
                continue
            g = f.top(x, z)
            st = f.get(x, g, z)
            if st is None or f.get(x, g + 1, z) is not None:
                continue
            r = R.at(x, z, 1520)
            if st[0] == "podzol" and r < 0.08:
                f.put(x, g + 1, z, block("brown_mushroom" if r < 0.05 else "red_mushroom"))
            elif st[0] in ("grass_block", "moss_block", "podzol") and r < 0.16 and f.get(x, g + 2, z) is None:
                f.put(x, g + 1, z, block("large_fern", half="lower"))
                f.put(x, g + 2, z, block("large_fern", half="upper"))
            elif st[0] in ("grass_block", "moss_block", "podzol") and r < 0.40:
                f.put(x, g + 1, z, block("fern"))
            f.reserved.add((x, z))
    # 林の東の口から石柱への小道（粗い土と砂利）
    for x in range(98, 170):
        for z in range(96, 120):
            if path_d[x, z] < 1.3 and f.stream_d[x, z] > 3:
                g = f.top(x, z)
                for yy in range(g + 1, g + 3):
                    st = f.get(x, yy, z)
                    if st is not None and st[0] in ("fern", "large_fern", "grass", "tall_grass", "brown_mushroom", "red_mushroom", "moss_carpet"):
                        f.put(x, yy, z, block("air"))
                f.put(x, g, z, block(R.pick([("coarse_dirt", 3), ("gravel", 1), ("dirt_path", 2)], x, z, 1530)))
    # 石柱のまわりの白い欠け（核を覆う。道の口はあける）
    for x in range(p2x - 11, p2x + 12):
        for z in range(p2z - 11, p2z + 12):
            d = math.hypot(x - p2x, z - p2z)
            if 7.5 <= d <= 11.0 and R.at(x, z, 1540) < 0.45 and not (x > p2x and abs(z - p2z) <= 2):
                g = f.top(x, z)
                if f.get(x, g + 1, z) is None:
                    f.put(x, g, z, block("amemaru:lost_blank"))
                    f.lost.add((x, z))
                    f.reserved.add((x, z))
    # 根元のうろの切り株（西の林。中に宝箱）
    sx, sz = 46, 118
    g = f.top(sx, sz)
    for x in range(sx - 2, sx + 3):
        for z in range(sz - 2, sz + 3):
            for yy in range(g + 1, g + 6):
                f.put(x, yy, z, block("air"))
            f.put(x, g, z, block("podzol", snowy=False))
            f.reserved.add((x, z))
    for (dx, dz) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1)):
        hgt = 3 if dz < 1 else 2
        for yy in range(g + 1, g + 1 + hgt):
            f.put(sx + dx, yy, sz + dz, wood_block_("dark_oak"))
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (-2, -1), (2, 1)):
        f.put(sx + dx, g + 1, sz + dz, log("dark_oak", "x" if dx else "z"))
    for x in range(sx - 1, sx + 2):
        for z in range(sz - 1, sz + 1):
            f.put(x, g + 4, z, block("moss_carpet") if (x, z) != (sx, sz) else block("air"))
    chest(f, sx, g + 1, sz, "south")
    # 石柱の後ろの祠の跡に宝箱（祠の石の手前）
    pg = f.top(p2x, p2z)
    chest(f, p2x + 3, pg + 1, p2z - 5, "south")


WOODS_PATH = [(166, 112), (146, 110), (128, 106), (112, 104), (104, 104)]   # 林の東の口から石柱への小道


def wood_block_(wood):
    from mapgen.world import wood_block
    return wood_block(wood)


def dark_oak(f, x, z, salt):
    """2×2の太い木（濃い樫）。上に広い葉"""
    cells = [(x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)]
    tops = [f.top(a, b) for a, b in cells]
    if max(tops) - min(tops) > 1 or any((a, b) in f.reserved for a, b in cells):
        return False
    for (a, b) in cells:
        st = f.get(a, f.top(a, b), b)
        if st is None or st[0] not in ("grass_block", "moss_block", "coarse_dirt", "podzol"):
            return False
    top = max(tops)
    hgt = 6 + int(R.at(x, z, salt) * 4)
    for (a, b) in cells:
        for y in range(f.top(a, b) + 1, top + hgt + 1):
            f.put(a, y, b, log("dark_oak"))
        f.reserved.add((a, b))
    leaf = block("dark_oak_leaves", distance=1, persistent=True, waterlogged=False)
    crown = top + hgt
    for dy in range(-2, 3):
        r = 3 if dy <= 0 else 2 if dy == 1 else 1
        for dx in range(-r, r + 2):
            for dz in range(-r, r + 2):
                ex = abs(dx - 0.5) - r
                ez = abs(dz - 0.5) - r
                if ex > -0.1 and ez > -0.1 and R.at(x + dx, crown + dy, z + dz, salt + 1) < 0.6:
                    continue
                pos = (x + dx, crown + dy, z + dz)
                if f.get(*pos) is None and HEIGHT - 1 > pos[1]:
                    f.put(*pos, leaf)
    return True


def big_oak(f, x, z, salt):
    """入口の近くの、ひとりで立つ大きな樫（11月22日【補完】）"""
    top = f.top(x, z)
    hgt = 8
    for (a, b) in ((x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)):
        for y in range(f.top(a, b) + 1, top + hgt + 1):
            f.put(a, y, b, log("oak"))
        f.reserved.add((a, b))
    leaf = block("oak_leaves", distance=1, persistent=True, waterlogged=False)
    for dy in range(-3, 3):
        r = 5 if dy <= -1 else 4 if dy == 0 else 3 if dy == 1 else 2
        for dx in range(-r, r + 2):
            for dz in range(-r, r + 2):
                d = math.hypot(dx - 0.5, dz - 0.5)
                if d > r + 0.3 or (d > r - 0.7 and R.at(x + dx, dy, z + dz, salt) < 0.4):
                    continue
                pos = (x + dx, top + hgt + dy, z + dz)
                if f.get(*pos) is None:
                    f.put(*pos, leaf)
    # 太い根（横の丸太）
    for (dx, dz, axis) in ((2, 0, "x"), (-1, 1, "x"), (0, -1, "z"), (1, 2, "z")):
        f.put(x + dx, f.top(x + dx, z + dz) + 1, z + dz, log("oak", axis))
        f.reserved.add((x + dx, z + dz))


# ---------------------------------------------------------------- 北東の結晶の窪み
def crystal_hollow_terrain(f, cx, cz):
    """結晶の窪みの地形（段々に3段下がる）"""
    h0 = int(f.height[cx, cz])
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x + 0.5 - (cx + 0.5), z + 0.5 - (cz + 0.5))
            if d <= 2.6:
                f.height[x, z] = h0 - 3
            elif d <= 3.7:
                f.height[x, z] = min(f.height[x, z], h0 - 2)
            elif d <= 4.8:
                f.height[x, z] = min(f.height[x, z], h0 - 1)


def crystal_hollow(f, cx, cz):
    g = f.top(cx, cz)
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            if d <= 4.8:
                top = f.top(x, z)
                f.put(x, top, z, block(R.pick([("moss_block", 2), ("calcite", 2), ("smooth_basalt", 0), ("tuff", 1), ("prismarine", 1)], x, z, 1600)))
                f.reserved.add((x, z))
            elif d <= 6.2 and R.at(x, z, 1601) < 0.35:
                top = f.top(x, z)
                for yy in range(top + 1, top + 2 + int(R.at(x, z, 1602) * 3)):
                    f.put(x, yy, z, block("green_stained_glass"))
                f.reserved.add((x, z))
    f.put(cx + 1, g, cz, block("sea_lantern"))
    f.put(cx - 1, g, cz + 1, block("sea_lantern"))
    chest(f, cx, g + 1, cz, "south")


# ---------------------------------------------------------------- 生け垣・石積みの垣
HEDGES = [
    # (種類, 折れ線)  入口の南の野・西の野・東の野。ところどころ切れ目（道・小川・建物のまわりはあける）
    ("wall", [(112, 336), (132, 330), (150, 322), (164, 326)]),
    ("hedge", [(214, 330), (232, 324), (252, 326), (272, 318), (292, 314)]),
    ("hedge", [(100, 262), (118, 254), (138, 250), (158, 244), (170, 236)]),
    ("wall", [(108, 214), (124, 222), (142, 220), (158, 210)]),
    ("wall", [(224, 268), (244, 262), (262, 252), (276, 240)]),
    ("hedge", [(232, 196), (248, 186), (266, 176), (280, 162)]),
    ("hedge", [(226, 142), (240, 130), (258, 124), (276, 118)]),
    ("wall", [(300, 250), (316, 258), (332, 262), (350, 258)]),
    ("hedge", [(150, 160), (162, 172), (168, 188)]),
]


def hedgerows(f):
    """草原の生け垣（葉、高さ2）と石積みの垣（高さ2、てっぺんに苔）。14マスごとに3マスの切れ目"""
    keep = [(ENTRY[0], ENTRY[1], 28), (HALL[0], HALL[1], 50), (TABLET[0], TABLET[1], 20)] + [(px, pz, 12) for px, pz in PILLARS]
    leaf = block("oak_leaves", distance=1, persistent=True, waterlogged=False)
    for n, (kind_, pts) in enumerate(HEDGES):
        cells = []
        for (xa, za), (xb, zb) in zip(pts, pts[1:]):
            seg = math.hypot(xb - xa, zb - za)
            steps = int(seg * 2)
            for k in range(steps):
                t = k / steps
                c = (int(xa + (xb - xa) * t), int(za + (zb - za) * t))
                if not cells or cells[-1] != c:
                    cells.append(c)
        for i, (x, z) in enumerate(cells):
            if i % 17 in (8, 9, 10):
                continue
            if (x, z) in f.reserved or f.road_d[x, z] < 5 or f.stream_d[x, z] < 4.5 or f.hill_d[x, z] < HILL_R + 10:
                continue
            if any(math.hypot(x - a, z - b) < r for a, b, r in keep):
                continue
            rx0, rz0, rx1, rz1 = RUINS
            if rx0 - 3 <= x <= rx1 + 3 and rz0 - 3 <= z <= rz1 + 3:
                continue
            g = f.top(x, z)
            if f.get(x, g + 1, z) is not None and f.get(x, g + 1, z)[0] not in ("grass", "tall_grass", "fern") + tuple(FLOWER_NAMES):
                continue
            for yy in (g + 1, g + 2):
                if kind_ == "hedge":
                    f.put(x, yy, z, leaf)
                else:
                    f.put(x, yy, z, block(R.pick([("mossy_cobblestone", 3), ("cobblestone", 2), ("stone", 1)], x, yy, z, 1700 + n)))
            if kind_ == "wall" and R.at(x, z, 1701) < 0.4:
                f.put(x, g + 3, z, block("moss_carpet"))
            f.reserved.add((x, z))


FLOWER_NAMES = ("dandelion", "oxeye_daisy", "azure_bluet", "cornflower", "poppy")


# ---------------------------------------------------------------- 飛び石・橋の下・池の小島・ひとりの樫
FORDS = [(STREAM2, 108, 298), (STREAM2, 242, 302), (STREAM, 252, 246), (STREAM, 70, 84), (STREAM, 300, 296)]


def fords(f):
    """小川を渡る飛び石（水面の高さの石を、1つおきに）"""
    for (line, fx, fz) in FORDS:
        # 小川の向き（いちばん近い区間）
        best = None
        for (xa, za), (xb, zb) in zip(line, line[1:]):
            seg = math.hypot(xb - xa, zb - za)
            t = max(0.0, min(1.0, ((fx - xa) * (xb - xa) + (fz - za) * (zb - za)) / (seg * seg)))
            px_, pz_ = xa + (xb - xa) * t, za + (zb - za) * t
            d = math.hypot(fx - px_, fz - pz_)
            if best is None or d < best[0]:
                best = (d, (xb - xa) / seg, (zb - za) / seg, px_, pz_)
        _, ux, uz, px_, pz_ = best
        nx, nz = -uz, ux
        for k in range(-5, 6):
            if k % 2:
                continue
            x, z = int(math.floor(px_ + nx * k + 0.5)), int(math.floor(pz_ + nz * k + 0.5))
            st = f.get(x, WATER, z)
            if st is not None and st[0] == "water":
                f.put(x, WATER, z, block(R.pick([("mossy_cobblestone", 2), ("stone", 2), ("andesite", 1)], x, z, 1750)))
                for yy in range(f.top(x, z) + 1, WATER):
                    f.put(x, yy, z, block("cobblestone"))


def bridge_chest(f):
    """本流の石橋の下（水の底）の宝箱：橋の真ん中の下の、いちばん深い所"""
    best = None
    for (x, z) in f.reserved:
        if f.stream1_d[x, z] < 1.4 and f.road_d[x, z] < 1.0:
            st = f.get(x, WATER + 2, z)
            if st is not None and "stone_brick" in st[0]:
                if best is None or f.stream1_d[x, z] + f.road_d[x, z] < best[0]:
                    best = (f.stream1_d[x, z] + f.road_d[x, z], x, z)
    if best is None:
        raise SystemExit("橋の下の宝箱の場所が見つかりません")
    _, x, z = best
    bed = f.top(x, z)
    chest(f, x, bed + 1, z, "north")


def lone_oak(f):
    """入口の西の、ひとりで立つ大きな樫（最初に見つける宝箱。樹の根元）"""
    x, z = 150, 336
    big_oak(f, x, z, 1800)
    g = f.top(x, z + 3)
    chest(f, x, g + 1, z + 3, "south")


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
PILLAR_REQUIRES = [
    {"type": "guardian", "guardian": "hill_alpha",
     "hint": "石柱の核は、まだ眠っている。丘の上の大きな毛玉が、ずっとこちらを見張っている……"},
    None,
    {"type": "items", "item": "amemaru:light_shard", "count": 3,
     "hint": "石柱の核を、白い欠けが覆っている。林の狼たちが、光のかけらを持ち去ったらしい"},
]


def floor_json(cores, seal_cells, f=None):
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
        "pillars": [dict({"pos": list(c), "line": PILLAR_LINES[i]}, **({"requires": PILLAR_REQUIRES[i]} if PILLAR_REQUIRES[i] else {}))
                    for i, c in enumerate(cores)],
        "pillars_done": "",
        "tablet": {"pos": [TABLET[0], BOSS_FLOOR + 2, TABLET[1]], "title": "道標の石板・一",
                   "text": "ひとりで来たのなら、引き返せ。この先は、手をつなぐ者のための道"},
        "spawns": spawn_zones(f),
    }
    if f is not None:
        # 11月22日【補完】：石柱の守り手・仕掛け石と門・古代の宝箱・階の中だけのドロップ
        gx, gz = GUARDIAN
        data["guardians"] = [{"id": "hill_alpha", "enemy": "amemaru:tsunomofu", "variant": "amemaru:tsunomofu_alpha",
                              "pos": [gx + 0.5, HILL_TOP + 1, gz + 0.5], "respawn_seconds": 180, "activation": 40, "pillar": 0}]
        data["switches"] = [{"id": "ruins", "stones": [list(p) for p in f.switches], "window_seconds": SWITCH_WINDOW,
                             "open_seconds": GATE_OPEN, "gate": [list(f.gate[0]), list(f.gate[1])]}]
        data["chests"] = [{"pos": list(c), "loot": CHEST_LOOT} for c in f.chests]
        data["extra_drops"] = [{"enemy": "amemaru:kakekiba_wolf", "item": "amemaru:light_shard", "chance": 35.0, "pillar": 2,
                                "keep": 3}]
    return data


def spawn_zones(f=None):
    def zone(id_, enemy, cx, cz, radius, max_, respawn, group, activation=56, route=None, y=None):
        if y is None:
            y = int(f.top(cx, cz)) + 1 if f is not None else 14
        z = {"id": id_, "enemy": "amemaru:" + enemy, "center": [cx, y, cz], "radius": radius, "max": max_,
             "respawn_seconds": respawn, "group": list(group), "activation": activation}
        if route:
            z["route"] = [[x, (int(f.top(x, zz)) + 1 if f is not None else y), zz] for (x, zz) in route]
        return z
    ry = (f.ruins_y + 1) if f is not None else 15
    return [
        # ツノモフ（群れ）：南の野・東の野・西の野・北の野・西の丘の上（守り手の群れ）。11月22日に南の2つを入口の側へ動かし、2つ足した
        zone("meadow_south", "tsunomofu", 128, 346, 16, 6, 40, (3, 5)),
        zone("meadow_southeast", "tsunomofu", 250, 340, 18, 7, 40, (3, 5)),
        zone("meadow_east", "tsunomofu", 250, 274, 16, 6, 40, (3, 4)),
        zone("meadow_west", "tsunomofu", 124, 236, 16, 6, 40, (3, 4)),
        zone("meadow_north", "tsunomofu", 262, 140, 18, 6, 45, (3, 4)),
        zone("meadow_center", "tsunomofu", 150, 196, 14, 5, 45, (2, 4)),
        zone("hill_herd", "tsunomofu", HILL[0] - 2, HILL[1] + 2, 12, 5, 50, (2, 3), 44, y=HILL_TOP + 1),
        # 石くれ兵（巡回）：道の南・道の北・東の遺跡の広場・遺跡の北の回廊・南の回廊
        zone("road_south", "ishikure_soldier", 180, 280, 6, 2, 50, (1, 1), 64,
             route=[(186, 304), (175, 280), (173, 262), (179, 244)]),
        zone("road_north", "ishikure_soldier", 202, 160, 6, 2, 50, (1, 1), 64,
             route=[(206, 182), (206, 166), (201, 146), (194, 128)]),
        zone("ruins_plaza", "ishikure_soldier", 304, 195, 6, 2, 50, (1, 1), 56,
             route=[(294, 188), (312, 188), (312, 203), (294, 203)], y=ry),
        zone("ruins_north", "ishikure_soldier", 326, 180, 6, 2, 55, (1, 1), 56,
             route=[(313, 182), (329, 182), (335, 179), (345, 179), (345, 188)], y=ry),
        zone("ruins_south", "ishikure_soldier", 326, 211, 6, 2, 55, (1, 1), 56,
             route=[(313, 209), (328, 209), (345, 209), (345, 219), (322, 219)], y=ry),
        # 結晶蜂：結晶のまわり（北東の結晶の丘に2つ）
        zone("crystals_east", "crystal_bee", 282, 236, 10, 4, 45, (1, 2)),
        zone("crystals_west", "crystal_bee", 128, 142, 10, 4, 45, (1, 2)),
        zone("crystals_north", "crystal_bee", 300, 104, 10, 3, 50, (1, 2)),
        zone("crystal_hills", "crystal_bee", 322, 62, 12, 4, 45, (1, 2)),
        zone("crystal_ridge", "crystal_bee", 346, 94, 10, 3, 50, (1, 2)),
        # 欠け牙の狼：白い欠けのまわり（北西の林に2つ。林の狼は光のかけらを落とす）
        zone("lost_west", "kakekiba_wolf", 58, 306, 14, 3, 60, (1, 2)),
        zone("lost_north", "kakekiba_wolf", 330, 86, 14, 3, 60, (1, 2)),
        zone("woods_east", "kakekiba_wolf", 120, 92, 14, 3, 50, (1, 2)),
        zone("woods_west", "kakekiba_wolf", 64, 116, 14, 3, 50, (1, 2)),
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
    lone_oak(f)
    f.gate_cells = ruins_compound(f)
    cores = [pillar(f, i) for i in range(3)]
    ruins(f)
    for (cx, cz, s) in ((282, 236, 1), (128, 142, 2), (300, 104, 3), (346, 96, 4), (304, 44, 5), (338, 50, 6), (314, 80, 7)):
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
    for (cx, cz, r, count, s, birch) in ((304, 58, 16, 9, 1, 0.3), (356, 144, 10, 5, 3, 0.2),
                                         (120, 330, 12, 6, 4, 0.5), (262, 344, 12, 6, 5, 0.3),
                                         (236, 70, 8, 4, 7, 0.2), (30, 360, 10, 5, 8, 0.3), (350, 290, 10, 4, 9, 0.4),
                                         (150, 40, 9, 4, 10, 0.5)):
        grove(f, cx, cz, r, count, 600 + s * 17, birch)
    woods(f)
    hill_features(f)
    crystal_hollow(f, *HOLLOW)
    hedgerows(f)
    scatter(f)
    ponds(f)
    fords(f)
    bridge_chest(f)
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
    data = floor_json(cores, seal, f)
    os.makedirs(os.path.dirname(json_path), exist_ok=True)
    with open(json_path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print("区画:", count, "ブロック:", len(f.blocks), "石柱の核:", cores, "封印:", len(seal), "宝箱:", len(f.chests),
          "仕掛け石:", f.switches, "門:", f.gate)
    if "--render" in sys.argv:
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        import floor_figures
        floor_figures.render_all(f, data, render_dir)


if __name__ == "__main__":
    main()
