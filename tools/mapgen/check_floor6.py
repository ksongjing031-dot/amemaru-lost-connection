"""塔の6階（build_floor6.py が組み立てる設計データ）を確かめる（11月25日【補完】）。

使い方: python3 tools/mapgen/check_floor6.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも）
  1. 封印を閉じたまま：入口から、中継の灯・宝箱・守り手・記憶の氷像・熱の炉・日記・道しるべの石・封印の前・見張りの塔の上へ
     歩いて行ける。雪の広場（ボスの部屋）へは入れない
  2. 封印を通れば、広場のまん中・記憶の結晶の台（3つ）・出口の円・次の階への円へ行ける
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor6 as B  # noqa: E402

PASSABLE = {"light", "spruce_sign", "spruce_wall_sign", "lantern", "snow", "amemaru:village_statue", "spruce_door",
            "spruce_pressure_plate", "ladder", "flower_pot", "candle"}


def solid_table(f):
    solid = np.zeros(len(f.states), dtype=bool)
    for i, st in enumerate(f.states):
        if st is not None:
            solid[i] = st[0] not in PASSABLE
    return solid


def volume(f, seal_open):
    v = f.v.copy()
    if seal_open:
        for (x, y, z) in f.seal_cells:
            v[x, y, z] = 0
    return v


def reach(f, v, start):
    solid = solid_table(f)[v]
    p = ~solid
    below = np.zeros_like(p)
    below[:, 1:, :] = solid[:, :-1, :]
    head = np.zeros_like(p)
    head[:, :-1, :] = p[:, 1:, :]
    ladder_ids = np.array([st is not None and st[0] == "ladder" for st in f.states], dtype=bool)
    ladder = ladder_ids[v]
    stand = (p & head & below) | ladder
    sx, sy, sz = start
    seen = np.zeros_like(stand)
    seen[sx, sy, sz] = True
    q = deque([(sx, sy, sz)])
    H = B.HEIGHT
    while q:
        x, y, z = q.popleft()
        # はしご：上へのぼれる
        if ladder[x, y, z] and y + 1 < H and (ladder[x, y + 1, z] or stand[x, y + 1, z]) and not seen[x, y + 1, z]:
            seen[x, y + 1, z] = True
            q.append((x, y + 1, z))
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < B.SIZE and 0 <= nz < B.SIZE):
                continue
            nxt = None
            if stand[nx, y, nz]:
                nxt = (nx, y, nz)
            elif y + 2 < H and stand[nx, y + 1, nz] and p[x, y + 2, z]:
                nxt = (nx, y + 1, nz)
            elif p[nx, y, nz] and p[nx, y + 1, nz]:
                yy = y - 1
                while yy > 0 and p[nx, yy, nz] and not stand[nx, yy, nz]:
                    yy -= 1
                if stand[nx, yy, nz]:
                    nxt = (nx, yy, nz)
            if nxt is not None and not seen[nxt]:
                seen[nxt] = True
                q.append(nxt)
    return seen


def near(seen, pos, radius=3.2, dy=3):
    x, y, z = (int(math.floor(c)) for c in pos)
    r = int(math.ceil(radius))
    return bool(seen[max(0, x - r):x + r + 1, max(0, y - dy):y + dy + 1, max(0, z - r):z + r + 1].any())


def main():
    f = B.build()
    data = B.floor_json(f)
    problems = []
    ex, ez = B.ENTRY
    start = (ex, B.GROUND + 1, ez - 8)
    cx, cz = B.ARENA
    seen = reach(f, volume(f, False), start)
    print("封印を閉じたまま、入口から立てる所:", int(seen.sum()))
    targets = [("中継の灯 " + w["name"], w["pos"]) for w in data["waypoints"]]
    for g in data["guardians"]:
        targets.append(("守り手 " + g["id"], g["pos"]))
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    for m in data["memory_statues"]:
        targets.append(("記憶の氷像 " + m["name"], m["pos"]))
    for h in data["hearths"]:
        targets.append(("熱の炉 " + h["name"], h["pos"]))
    targets.append(("日記", data["tablet"]["pos"]))
    targets.append(("道しるべの石", data["travel_stone"]))
    seal = data["boss_room"]["seal"]
    targets.append(("封印の前", [(seal[0][0] + seal[1][0]) // 2, seal[0][1], seal[1][2] + 2]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（閉じたまま）行けない：" + name + " " + str(pos))
    if near(seen, (cx, B.GROUND + 1, cz), radius=20):
        problems.append("封印を通らずに広場へ入れる")
    seen = reach(f, volume(f, True), start)
    pts = [("広場のまん中", data["boss_marks"]["plaza_center"][0]), ("出口の円", data["boss_room"]["exit"]["center"]),
           ("次の階への円", data["boss_room"]["next"]["center"])]
    pts += [("記憶の結晶の台 %d" % i, d) for i, d in enumerate(data["boss_marks"]["crystal_spots"])]
    for name, pos in pts:
        if not near(seen, pos):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))
    solid = solid_table(f)[f.v]
    for zone in data["spawns"]:
        x, y, z = (int(math.floor(c)) for c in zone["center"])
        if solid[x, y, z]:
            problems.append("湧く所のまん中がブロックの中：" + zone["id"])
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]), "記憶の氷像:", len(data["memory_statues"]), "熱の炉:", len(data["hearths"]))
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
