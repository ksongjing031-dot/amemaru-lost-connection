"""塔の3階（build_floor3.py が組み立てる設計データ）を確かめる（Phase 8 その2【補完】）。

使い方: python3 tools/mapgen/check_floor3.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも・水は泳げる。
階段・板・柵のような形のブロックは、通れない固いものとして数える）
  1. 水が流れ出さない：水門の水の区画が「満ちている・引いた」どの組み合わせでも、水のとなり（東西南北と下）に空気がない
     （置いたとき・水を戻したときに、水が流れ出して、ほかの所を水びたしにしないように）
  2. 水門を1つも開けていないとき：入口から、水門①の2つのハンドル・西の貯水槽・東の水車の間・ネズミの巣穴まで歩いて行ける。
     白い流れ（水門②の扉の奥）と、ボスの部屋（封印の奥）へは入れない
  3. 水門をすべて開けたとき：入口から、すべての宝箱・中継の灯・水門のハンドル・守り手・古代記録の掲示・封印の前まで行ける。
     封印を通れば、ボスの部屋の中から戻りの円まで行ける
  4. ハンドルの前（向いている方の1マス）に立てる
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor3 as B  # noqa: E402

PASSABLE = {"water", "spruce_sign", "dark_oak_wall_sign"}
DIRS = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}


def tables(f):
    n = len(f.states)
    solid = np.zeros(n, dtype=bool)
    wet = np.zeros(n, dtype=bool)
    for i, st in enumerate(f.states):
        if st is None:
            continue
        name = st[0]
        props = dict(st[1])
        wet[i] = name == "water" or props.get("waterlogged") == "true"
        solid[i] = name not in PASSABLE
    return solid, wet


def state_volume(f, open_gates, seal_open):
    """水門 open_gates を開けたときの立体（v のコピー）"""
    v = f.v.copy()
    water = f.idx(B.WATER)
    drained = set()
    for g in B_GATES(f):
        if g["id"] in open_gates:
            drained.update(g["drains"])
            for door in g.get("doors", []):
                x0, y0, z0, x1, y1, z1 = door["box"]
                v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = 0
    for sec in f.water_sections:
        if sec["id"] in drained:
            x0, low, z0, x1, high, z1 = sec["box"]
            sub = v[x0:x1 + 1, low + 1:high + 1, z0:z1 + 1]
            sub[sub == water] = 0
    if seal_open:
        for (x, y, z) in f.seal_cells:
            v[x, y, z] = 0
    return v


def B_GATES(f):
    return B.floor_json(f)["floodgates"]


def water_leaks(f, v):
    """水のとなり（東西南北と下）が空気の所"""
    water = f.idx(B.WATER)
    w = v == water
    air = v == 0
    leaks = []
    for (dx, dy, dz) in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
        nb = np.zeros_like(air)
        xs = slice(max(0, -dx), B.SIZE - max(0, dx))
        xd = slice(max(0, dx), B.SIZE - max(0, -dx))
        ys = slice(max(0, -dy), B.HEIGHT - max(0, dy))
        yd = slice(max(0, dy), B.HEIGHT - max(0, -dy))
        zs = slice(max(0, -dz), B.SIZE - max(0, dz))
        zd = slice(max(0, dz), B.SIZE - max(0, -dz))
        nb[xs, ys, zs] = air[xd, yd, zd]
        bad = w & nb
        if bad.any():
            pts = np.argwhere(bad)
            leaks.extend([tuple(int(c) for c in p) + ((dx, dy, dz),) for p in pts[:5]])
            if len(pts) > 5:
                leaks.append(("…", len(pts)))
    return leaks


class Walker:
    def __init__(self, f, v):
        solid_t, wet_t = tables(f)
        self.solid = solid_t[v]
        self.water = wet_t[v] & ~self.solid | (v == f.idx(B.WATER))
        self.passable = ~self.solid
        p = self.passable
        below_solid = np.zeros_like(p)
        below_solid[:, 1:, :] = self.solid[:, :-1, :]
        head = np.zeros_like(p)
        head[:, :-1, :] = p[:, 1:, :]
        self.stand = p & head & (below_solid | self.water)

    def reach(self, start):
        sx, sy, sz = start
        if not self.stand[sx, sy, sz]:
            for dy in range(-2, 3):
                if 0 <= sy + dy < B.HEIGHT and self.stand[sx, sy + dy, sz]:
                    sy += dy
                    break
        seen = np.zeros_like(self.stand)
        seen[sx, sy, sz] = True
        q = deque([(sx, sy, sz)])
        st, pa, wa = self.stand, self.passable, self.water
        H = B.HEIGHT
        while q:
            x, y, z = q.popleft()
            nexts = []
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (0 <= nx < B.SIZE and 0 <= nz < B.SIZE):
                    continue
                if st[nx, y, nz]:
                    nexts.append((nx, y, nz))
                elif y + 2 < H and st[nx, y + 1, nz] and pa[x, y + 2, z]:
                    nexts.append((nx, y + 1, nz))
                elif pa[nx, y, nz] and y + 1 < H and pa[nx, y + 1, nz]:
                    yy = y - 1
                    while yy > 0 and pa[nx, yy, nz] and not st[nx, yy, nz]:
                        yy -= 1
                    if yy > 0 and st[nx, yy, nz]:
                        nexts.append((nx, yy, nz))
            if wa[x, y, z]:
                for yy in (y + 1, y - 1):
                    if 0 < yy < H - 1 and st[x, yy, z]:
                        nexts.append((x, yy, z))
            for c in nexts:
                if not seen[c]:
                    seen[c] = True
                    q.append(c)
        return seen


def near(seen, pos, radius=3.2, dy=3):
    x, y, z = pos
    r = int(math.ceil(radius))
    sub = seen[max(0, x - r):x + r + 1, max(0, y - dy):y + dy + 1, max(0, z - r):z + r + 1]
    return bool(sub.any())


def main():
    f = B.build()
    data = B.floor_json(f)
    problems = []
    gate_ids = ["first", "deep", "moat"]
    # 1. 水が流れ出さない（水門の組み合わせごと。排水は、ボスの部屋だけ）
    combos = [(), ("first",), ("first", "deep"), ("first", "deep", "moat"), ("drain",), ("first", "deep", "moat", "drain")]
    for combo in combos:
        v = state_volume(f, set(combo), seal_open=False)
        leaks = water_leaks(f, v)
        if leaks:
            problems.append("水が流れ出す（開いた水門 %s）：%s" % (combo or "なし", leaks[:8]))
    ex, ez = B.ENTRY
    start = (ex, B.UP + 1, ez - 8)
    # 2. 水門を開けていないとき
    v = state_volume(f, set(), seal_open=False)
    seen = Walker(f, v).reach(start)
    print("水門を開けていないとき、入口から立てる所:", int(seen.sum()))
    must = [("水門①のハンドル（西）", data["floodgates"][0]["valves"][0]), ("水門①のハンドル（東）", data["floodgates"][0]["valves"][1]),
            ("監督機兵", [int(c) for c in data["guardians"][0]["pos"]]), ("ネズミの親分", [int(c) for c in data["guardians"][2]["pos"]]),
            ("古代記録の掲示", data["tablet"]["pos"]), ("中継の灯 広間", data["waypoints"][0]["pos"]),
            ("中継の灯 西の貯水槽", data["waypoints"][1]["pos"]), ("中継の灯 東の水車の間", data["waypoints"][2]["pos"])]
    for name, pos in must:
        if not near(seen, tuple(pos)):
            problems.append("（水門を開けていないとき）行けない：" + name + " " + str(pos))
    # 白い流れは、大水門の前の回廊（ネズミの巣穴から。堀を泳いで渡る）からも行ける。水門②の扉は、沈んだ区画からの近道
    sunken_seen = seen[B.SUNKEN[0]:B.SUNKEN[2] + 1, B.LOW + 1:B.LOW + 3, B.SUNKEN[1]:B.SUNKEN[3] + 1]
    print("水門を開けていないとき、沈んだ区画の床に立てる所（泳いで行ける水の底）:", int(sunken_seen.sum()))
    cx, cz = B.ARENA
    if near(seen, (cx, B.LOW + 1, cz), radius=18):
        problems.append("封印を通らずに、ボスの部屋へ入れる（水門を開けていないとき）")
    # 3. 水門をすべて開けたとき
    v = state_volume(f, set(gate_ids), seal_open=False)
    seen = Walker(f, v).reach(start)
    print("水門をすべて開けたとき、入口から立てる所:", int(seen.sum()))
    targets = []
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    for wp in data["waypoints"]:
        targets.append(("中継の灯 " + wp["name"], wp["pos"]))
    for g in data["floodgates"]:
        if g["id"] == "drain":
            continue
        for k, p in enumerate(g["valves"]):
            targets.append(("ハンドル %s %d" % (g["id"], k), p))
    for g in data["guardians"]:
        if g["id"] == "lord":
            targets.append(("主ナマズの溜まりのそば", [int(g["pos"][0]), B.LOW + 1, int(g["pos"][2]) + 10]))
        else:
            targets.append(("守り手 " + g["id"], [int(c) for c in g["pos"]]))
    targets.append(("古代記録の掲示", data["tablet"]["pos"]))
    targets.append(("道しるべの石", data["travel_stone"]))
    seal = data["boss_room"]["seal"]
    sx = (seal[0][0] + seal[1][0]) // 2
    targets.append(("封印の前", [sx, seal[0][1], seal[0][2] + 1]))
    zone = data["story_zones"][0]["box"]
    targets.append(("物語の場所 " + data["story_zones"][0]["id"], [(zone[0] + zone[3]) // 2, B.LOW + 1, (zone[2] + zone[5]) // 2]))
    for name, pos in targets:
        if not near(seen, tuple(pos)):
            problems.append("（水門をすべて開けたとき）行けない：" + name + " " + str(pos))
    if near(seen, (cx, B.LOW + 1, cz), radius=18):
        problems.append("封印を通らずに、ボスの部屋へ入れる（水門をすべて開けたとき）")
    # 封印を通る：ボスの部屋の中・戻りの円・排水のハンドル
    v = state_volume(f, set(gate_ids), seal_open=True)
    seen = Walker(f, v).reach(start)
    ex_c = data["boss_room"]["exit"]["center"]
    for name, pos in (("ボスの部屋のまん中", [cx, B.LOW + 1, cz]), ("ボスの部屋の戻りの円", [int(ex_c[0]), int(ex_c[1]), int(ex_c[2])]),
                      ("排水のハンドル（西）", data["floodgates"][3]["valves"][0]), ("排水のハンドル（東）", data["floodgates"][3]["valves"][1])):
        if not near(seen, tuple(pos)):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))
    # 4. ハンドルの前に立てる
    w = Walker(f, state_volume(f, set(gate_ids) | {"drain"}, seal_open=True))
    for g in data["floodgates"]:
        for k, (x, y, z) in enumerate(g["valves"]):
            st = f.get(x, y, z)
            facing = dict(st[1]).get("facing", "north")
            dx, dz = DIRS[facing]
            if not (w.stand[x + dx, y, z + dz] or w.stand[x + dx, y - 1, z + dz] or w.stand[x + dx, y + 1, z + dz]):
                problems.append("ハンドルの前に立てない：%s %d %s" % (g["id"], k, (x, y, z)))
    # 守り手の湧く所が、固いブロックの中でない
    for g in data["guardians"]:
        x, y, z = (int(math.floor(c)) for c in g["pos"])
        if w.solid[x, y, z]:
            problems.append("守り手の所がブロックの中：" + g["id"])
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]))
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
