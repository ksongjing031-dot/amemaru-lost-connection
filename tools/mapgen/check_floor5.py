"""塔の5階（build_floor5.py が組み立てる設計データ）を確かめる（11月24日【補完】）。

使い方: python3 tools/mapgen/check_floor5.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも。奈落へ落ちるのは、ゲームでは
中継の灯へ戻されるので、ここでは「行けない所」として数えない）
  1. 祠の門と封印を閉じたまま：入口から、4つの中継の灯・接続の柱A〜Cの前・仕掛け石2つ・宝箱（祠の中をのぞく）・守り手・封印の前へ
     歩いて行ける。祭壇（ボスの部屋）と祠の中へは入れない
  2. 祠の門を開けると、接続の柱Dの前へ行ける
  3. 封印を通れば、祭壇のまん中・4つの結び台・出口の円・次の階への円へ行ける
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor5 as B  # noqa: E402

PASSABLE = {"light", "birch_sign", "soul_lantern", "lime_stained_glass"}


def solid_table(f):
    solid = np.zeros(len(f.states), dtype=bool)
    for i, st in enumerate(f.states):
        if st is not None:
            solid[i] = st[0] not in PASSABLE
    return solid


def volume(f, gate_open, seal_open):
    v = f.v.copy()
    if gate_open:
        (x0, y0, z0), (x1, y1, z1) = f.gate
        v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = 0
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
    stand = p & head & below
    sx, sy, sz = start
    seen = np.zeros_like(stand)
    seen[sx, sy, sz] = True
    q = deque([(sx, sy, sz)])
    H = B.HEIGHT
    while q:
        x, y, z = q.popleft()
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
                if yy > B.ABYSS_Y and stand[nx, yy, nz]:
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
    seen = reach(f, volume(f, False, False), start)
    print("門と封印を閉じたまま、入口から立てる所:", int(seen.sum()))
    targets = [("中継の灯 " + w["name"], w["pos"]) for w in data["waypoints"]]
    for i, p in enumerate(data["pillars"][:3]):
        targets.append(("接続の柱 %d の前" % i, p["pos"]))
    for i, s in enumerate(data["switches"][0]["stones"]):
        targets.append(("仕掛け石 %d" % i, s))
    for g in data["guardians"]:
        x, y, z = g["pos"]
        targets.append(("守り手 " + g["id"], [x, B.GROUND + 1, z]))
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    targets.append(("道しるべの石", data["travel_stone"]))
    seal = data["boss_room"]["seal"]
    targets.append(("封印の前", [(seal[0][0] + seal[1][0]) // 2, seal[0][1], seal[1][2] + 1]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（閉じたまま）行けない：" + name + " " + str(pos))
    if near(seen, (cx, B.GROUND + 1, cz), radius=20):
        problems.append("封印を通らずに祭壇へ入れる")
    if near(seen, data["pillars"][3]["pos"], radius=1.5, dy=1):
        problems.append("祠の門を開けずに、接続の柱 D へ行ける")
    seen = reach(f, volume(f, True, False), start)
    if not near(seen, data["pillars"][3]["pos"], radius=2.5):
        problems.append("祠の門を開けても、接続の柱 D へ行けない")
    seen = reach(f, volume(f, True, True), start)
    marks = data["boss_marks"]
    pts = [("祭壇のまん中", marks["altar_center"][0]), ("出口の円", data["boss_room"]["exit"]["center"]),
           ("次の階への円", data["boss_room"]["next"]["center"])]
    pts += [("結び台 %d" % i, d) for i, d in enumerate(marks["binding_daises"])]
    for name, pos in pts:
        if not near(seen, pos):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))
    solid = solid_table(f)[f.v]
    for zone in data["spawns"]:
        x, y, z = (int(math.floor(c)) for c in zone["center"])
        if solid[x, y, z]:
            problems.append("湧く所のまん中がブロックの中：" + zone["id"])
        below = [yy for yy in range(B.ABYSS_Y, y + 1) if solid[x, yy, z]]
        if not below:
            problems.append("湧く所の下に床がない（奈落）：" + zone["id"])
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]), "柱:", len(data["pillars"]))
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
