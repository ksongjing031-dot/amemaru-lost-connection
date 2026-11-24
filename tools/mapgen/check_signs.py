"""塔の階の看板が、読める向きか確かめる（11月24日のご指示「すべての階層で壁側に看板が向いていて見づらいところがあります」）。

使い方: python3 tools/mapgen/check_signs.py [階 ...]   （階を書かなければ 1〜5 階）

構造物ファイル（src/main/resources/data/amemaru/structures/map/floorN/）を読み、看板ごとに、字の面
（立て看板は rotation、壁の看板は facing の向き。この塔の看板は字を表にだけ書いている）を、仕掛けをすべて解いた形
（扉・門・封印を開けた形）で見る：
  1. すぐ前のマスが、ふさがっていない
  2. 前の3マスのうちに壁があって、後ろは空いている（壁を向いている）ことがない
  3. 人が来る側を向いている：入口から歩いて（1段は上がれる・下りは8段まで）看板のまわり（4ブロック・高さ ±2・看板から
     見える所）にはじめて着くあたりが、字の面の側（正面から 70 度のうち）にある
おかしい看板は、直す向き（人が来る側にいちばん近く、前の3マスに壁のない向き。16 方位）を添えて出す。終わりの値はおかしい看板の数。
"""
import glob
import json
import math
import os
import sys
from collections import deque

import nbtlib
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STRUCT = os.path.join(ROOT, "src/main/resources/data/amemaru/structures/map")
FLOORS = os.path.join(ROOT, "src/main/resources/data/amemaru/amemaru/floors")

# 当たり判定のない（または、立つのにも見るのにも邪魔にならない）もの
PASSABLE_WORDS = ("_sign", "light", "grass", "fern", "bluet", "cornflower", "dandelion", "lily_of_the_valley", "oxeye_daisy", "poppy",
                  "mushroom", "carpet", "banner", "vine", "lichen", "button", "water", "sound_plate", "floor_seal", "chain", "end_rod",
                  "lightning_rod")
SOLID_EXCEPTIONS = ("mushroom_block", "grass_block", "mushroom_stem")
FACING = {"south": 0, "west": 4, "north": 8, "east": 12}
NAMES = ["南", "南南西", "南西", "西南西", "西", "西北西", "北西", "北北西", "北", "北北東", "北東", "東北東", "東", "東南東", "南東", "南南東"]
APPROACH_RADIUS = 4
FACE_COS = math.cos(math.radians(70))


def passable(name):
    short = name.split(":", 1)[1]
    if any(w in short for w in SOLID_EXCEPTIONS):
        return False
    return short == "air" or any(w in short for w in PASSABLE_WORDS)


def load(floor):
    with open(os.path.join(FLOORS, "floor%d.json" % floor), encoding="utf-8") as fh:
        d = json.load(fh)
    sx, sy, sz = d["size"]
    piece = d["piece_size"]
    solid = np.zeros((sx, sy + 8, sz), dtype=bool)
    signs = []
    for path in glob.glob(os.path.join(STRUCT, "floor%d" % floor, "p*_*.nbt")):
        px, pz = (int(v) for v in os.path.basename(path)[1:-4].split("_"))
        t = nbtlib.load(path)
        pal = []
        for e in t["palette"]:
            props = {str(k): str(v) for k, v in e.get("Properties", {}).items()}
            pal.append((str(e["Name"]), props))
        for b in t["blocks"]:
            x, y, z = (int(v) for v in b["pos"])
            x += px * piece
            z += pz * piece
            name, props = pal[int(b["state"])]
            if not (0 <= x < sx and 0 <= y < solid.shape[1] and 0 <= z < sz):
                continue
            if not passable(name):
                solid[x, y, z] = True
            if name.endswith("_sign"):
                text = []
                nbt = b.get("nbt")
                if nbt is not None and "front_text" in nbt:
                    for line in nbt["front_text"]["messages"]:
                        s = str(line)
                        try:
                            s = json.loads(s)
                            s = s if isinstance(s, str) else s.get("text", "")
                        except (ValueError, AttributeError):
                            pass
                        text.append(s)
                if "rotation" in props:
                    rot, wall = int(props["rotation"]), False
                elif "facing" in props:
                    rot, wall = FACING[props["facing"]], True
                else:
                    continue
                signs.append({"pos": (x, y, z), "rot": rot, "wall": wall, "name": name, "text": [s for s in text if s]})
    # 仕掛けをすべて解いたときの形で見る（壁画・歌・水門の扉、仕掛け石の門、ボスの部屋の封印を開ける）
    boxes = []
    for m in d.get("murals", []) + d.get("songs", []):
        if "door" in m:
            boxes.append(m["door"]["box"])
    for g in d.get("floodgates", []):
        for door in g.get("doors") or []:
            boxes.append(door["box"])
    for sw in d.get("switches", []):
        (a0, b0, c0), (a1, b1, c1) = sw["gate"]
        boxes.append([a0, b0, c0, a1, b1, c1])
    (a0, b0, c0), (a1, b1, c1) = d["boss_room"]["seal"]
    boxes.append([a0, b0, c0, a1, b1, c1])
    for x0, y0, z0, x1, y1, z1 in boxes:
        solid[min(x0, x1):max(x0, x1) + 1, min(y0, y1):max(y0, y1) + 1, min(z0, z1):max(z0, z1) + 1] = False
    entrance = tuple(int(math.floor(v)) for v in d["entrance"]["pos"])
    return solid, signs, entrance


def front_vec(rot):
    a = math.radians(rot * 22.5)
    return -math.sin(a), math.cos(a)


def stand_grid(solid):
    stand = np.zeros_like(solid)
    stand[:, 1:-1, :] = ~solid[:, 1:-1, :] & ~solid[:, 2:, :] & solid[:, :-2, :]
    return stand


def walk(stand, start):
    """入口から歩いた歩数（立てる所ごと。行けない所は -1）。1段は上がれる・下りは8段まで。"""
    sx, sy, sz = stand.shape
    dist = np.full(stand.shape, -1, dtype=np.int32)
    x, y, z = start
    for dy in range(0, 4):
        if 0 <= y - dy < sy and stand[x, y - dy, z]:
            y -= dy
            break
        if 0 <= y + dy < sy and stand[x, y + dy, z]:
            y += dy
            break
    if not stand[x, y, z]:
        return dist
    dist[x, y, z] = 0
    q = deque([(x, y, z)])
    steps = (0, 1, -1, -2, -3, -4, -5, -6, -7, -8)
    while q:
        x, y, z = q.popleft()
        d = dist[x, y, z] + 1
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < sx and 0 <= nz < sz):
                continue
            for dy in steps:
                ny = y + dy
                if 0 < ny < sy - 1 and stand[nx, ny, nz]:
                    if dist[nx, ny, nz] < 0:
                        dist[nx, ny, nz] = d
                        q.append((nx, ny, nz))
                    break
    return dist


def visible(solid, pos, cell):
    """看板（の真ん中）から、そのマスに立つ人の目（足もとから 1.6）まで、固いブロックにさえぎられずに見えるか。"""
    x0, y0, z0 = pos
    ax, ay, az = x0 + 0.5, y0 + 0.5, z0 + 0.5
    bx, by, bz = cell[0] + 0.5, cell[1] + 1.6, cell[2] + 0.5
    n = int(max(abs(bx - ax), abs(by - ay), abs(bz - az)) * 4) + 1
    for i in range(1, n):
        t = i / n
        x, y, z = int(math.floor(ax + (bx - ax) * t)), int(math.floor(ay + (by - ay) * t)), int(math.floor(az + (bz - az) * t))
        if (x, y, z) == (x0, y0, z0):
            continue
        if 0 <= x < solid.shape[0] and 0 <= y < solid.shape[1] and 0 <= z < solid.shape[2] and solid[x, y, z]:
            return False
    return True


def approach(dist, pos, solid=None):
    """看板のまわり（看板から見える所）で、入口からはじめて着くあたり（いちばん早く着く所から3歩のうちの所）の、
    看板から見た向き（平均）。着けなければ None。"""
    x0, y0, z0 = pos
    cells = []
    for dx in range(-APPROACH_RADIUS, APPROACH_RADIUS + 1):
        for dz in range(-APPROACH_RADIUS, APPROACH_RADIUS + 1):
            r = math.hypot(dx, dz)
            if r < 1 or r > APPROACH_RADIUS:
                continue
            best = None
            for dy in range(-2, 3):
                x, y, z = x0 + dx, y0 + dy, z0 + dz
                if not (0 <= x < dist.shape[0] and 0 <= y < dist.shape[1] and 0 <= z < dist.shape[2]):
                    continue
                d = dist[x, y, z]
                if d >= 0 and (best is None or d < best) and (solid is None or visible(solid, pos, (x, y, z))):
                    best = d
            if best is not None:
                cells.append((best, dx / r, dz / r))
    if not cells:
        return None
    first = min(c[0] for c in cells)
    vx = sum(c[1] for c in cells if c[0] <= first + 3)
    vz = sum(c[2] for c in cells if c[0] <= first + 3)
    n = math.hypot(vx, vz)
    if n < 1e-6:
        return None
    return vx / n, vz / n


def wall_ahead(solid, pos, rot, reach):
    fx, fz = front_vec(rot)
    x, y, z = pos
    for k in range(1, reach + 1):
        nx, nz = x + int(round(fx * k)), z + int(round(fz * k))
        if not (0 <= nx < solid.shape[0] and 0 <= nz < solid.shape[2]):
            return k
        if solid[nx, y, nz] or solid[nx, y + 1, nz]:
            return k
    return None


def best_rotation(vec):
    dx, dz = vec
    # front_vec(rot) = (-sin a, cos a) なので a = atan2(-dx, dz)
    a = math.degrees(math.atan2(-dx, dz)) % 360
    return int(round(a / 22.5)) % 16


def check(floor):
    solid, signs, entrance = load(floor)
    dist = walk(stand_grid(solid), entrance)
    bad = []
    for s in sorted(signs, key=lambda s: s["pos"]):
        pos, rot = s["pos"], s["rot"]
        reasons = []
        ahead = wall_ahead(solid, pos, rot, 3)
        behind = wall_ahead(solid, pos, (rot + 8) % 16, 3)
        if ahead == 1 and not s["wall"]:
            reasons.append("すぐ前が壁")
        elif ahead is not None and behind is None:
            reasons.append("%dマス前が壁で、後ろは空いている" % ahead)
        come = approach(dist, pos, solid)
        want = None
        if come is None:
            reasons.append("入口から歩いて行けない")
        else:
            fx, fz = front_vec(rot)
            want = best_rotation(come)
            if fx * come[0] + fz * come[1] < FACE_COS and not s["wall"]:
                # 壁の看板は、付けた壁と反対にしか向けないので、来る側は見ない
                reasons.append("人が来る側（%s）に背を向けている" % NAMES[want])
        if reasons:
            bad.append((s, suggest(solid, pos, want, rot), reasons))
    return signs, bad


def suggest(solid, pos, want, rot):
    """直す向き：人が来る側にいちばん近く、前の3マスに壁のない向き（来る側がわからなければ、今の向きの反対から探す）。"""
    target = want if want is not None else (rot + 8) % 16
    order = sorted(range(16), key=lambda r: (min((r - target) % 16, (target - r) % 16), r % 2))
    for r in order:
        if wall_ahead(solid, pos, r, 3) is None:
            return r
    return target


def main():
    floors = [int(a) for a in sys.argv[1:]] or [1, 2, 3, 4, 5]
    total = 0
    for floor in floors:
        signs, bad = check(floor)
        print("%d階：看板 %d 枚・おかしい看板 %d 枚" % (floor, len(signs), len(bad)))
        for s, want, reasons in bad:
            x, y, z = s["pos"]
            label = "／".join(s["text"][:2]) or "（字なし）"
            print("  (%d, %d, %d) %s 向き %s(%d) → 直す向き %s(%d)：%s「%s」" % (
                x, y, z, s["name"].split(":")[1], NAMES[s["rot"]], s["rot"], NAMES[want], want, "・".join(reasons), label))
        total += len(bad)
    return total


if __name__ == "__main__":
    sys.exit(1 if main() else 0)
