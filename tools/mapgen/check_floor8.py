"""塔の8階（build_floor8.py が組み立てる設計データ）を確かめる（11月25日【補完】）。

使い方: python3 tools/mapgen/check_floor8.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも。水の上は歩けず、泳いで抜け道を作れない）
  1. どの鐘も鳴らしていない：入口から、入江の鐘3つ・入江の歌の碑・道しるべの石・南の岸の宝箱へ行ける。裂け目の北へは行けない
  2. 入江の鐘を鳴らした（石橋が浮かぶ）：湖の鐘5つ・歌の碑・名のある魔物2体とその宝箱・中継の灯・湖の上の宝箱へ行ける。
     湖底の祠と湖心へは入れない
  3. 湖の鐘も鳴らした（祠のふたが開く）：祠の石板・歌い手の氷像・祠の灯籠・封印の前へ行ける。湖心へは入れない
  4. 封印も開く：湖底の道を通って、湖心のまん中・出口の円・次の階への円・鳴り石4つへ行ける
  5. 水：どの水も、横と下が水か固いブロック（空いた所へ流れ出さない）
  6. 湧く所・守り手の出る所がブロックの中でない。鐘・歌の碑・石板・熱の炉・氷像が、決まった所にある
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor8 as B  # noqa: E402

PASSABLE = {"light", "spruce_sign", "lantern", "soul_lantern", "snow"}
BLOCKED = {"water", "chain", "white_stained_glass_pane", "stone_brick_wall", "polished_diorite_wall", "amemaru:lake_bell",
           "amemaru:song_stele", "amemaru:lake_tablet", "amemaru:village_statue", "amemaru:village_hearth", "amemaru:tower_chest",
           "amethyst_cluster", "amemaru:floor_seal"}


def tables(f):
    passable = np.zeros(len(f.states), dtype=bool)
    standable = np.zeros(len(f.states), dtype=bool)
    for i, st in enumerate(f.states):
        if st is None or st[0] in PASSABLE:
            passable[i] = True
        elif st[0] not in BLOCKED:
            standable[i] = True
    return passable, standable


def box_set(v, f, box, state):
    x0, y0, z0, x1, y1, z1 = box
    i = 0 if state is None else f.idx(state)
    v[min(x0, x1):max(x0, x1) + 1, min(y0, y1):max(y0, y1) + 1, min(z0, z1):max(z0, z1) + 1] = i


def volume(f, data, cove, main, seal):
    v = f.v.copy()
    groups = {g["id"]: g for g in data["bells"]}
    if cove:
        for r in groups["cove"]["raises"]:
            name = r["block"].split(":")[1]
            st = B.block(name) if name != "stone_brick_wall" else B.block(name, up=True, north="none", south="none", east="none",
                                                                            west="none", waterlogged=False)
            box_set(v, f, r["box"], st)
    if main:
        for o in groups["main"]["opens"]:
            box_set(v, f, o["box"], None)
    if seal:
        for (x, y, z) in f.seal_cells:
            v[x, y, z] = 0
    return v


def reach(f, v, start):
    passable, standable = tables(f)
    p = passable[v]
    s = standable[v]
    below = np.zeros_like(p)
    below[:, 1:, :] = s[:, :-1, :]
    head = np.zeros_like(p)
    head[:, :-1, :] = p[:, 1:, :]
    stand = p & head & below
    sx, sy, sz = start
    seen = np.zeros_like(stand)
    if not stand[sx, sy, sz]:
        raise SystemExit("入口に立てない：" + str(start))
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


def water_leaks(f):
    water = np.array([st is not None and st[0] == "water" for st in f.states], dtype=bool)[f.v]
    passable, _ = tables(f)
    air = passable[f.v]
    leaks = []
    for (dx, dy, dz) in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
        nb = np.zeros_like(air)
        sx = slice(max(0, dx), B.SIZE + min(0, dx))
        sy = slice(max(0, dy), B.HEIGHT + min(0, dy))
        sz = slice(max(0, dz), B.SIZE + min(0, dz))
        tx = slice(max(0, -dx), B.SIZE + min(0, -dx))
        ty = slice(max(0, -dy), B.HEIGHT + min(0, -dy))
        tz = slice(max(0, -dz), B.SIZE + min(0, -dz))
        nb[tx, ty, tz] = air[sx, sy, sz]
        bad = water & nb
        xs, ys, zs = np.nonzero(bad)
        for k in range(min(5, len(xs))):
            leaks.append((int(xs[k]), int(ys[k]), int(zs[k]), (dx, dy, dz)))
        if len(xs) > 5:
            leaks.append(("ほか", int(len(xs)) - 5, (dx, dy, dz)))
    return leaks


def main():
    f = B.build()
    data = B.floor_json(f)
    problems = []
    ex, ez = B.ENTRY
    start = (ex, B.GROUND + 1, ez - 8)
    groups = {g["id"]: g for g in data["bells"]}

    # 6. 置き物
    for g in data["bells"]:
        st = f.get(*g["stele"])
        if st is None or st[0] != "amemaru:song_stele":
            problems.append("歌の碑がない：" + g["id"])
        for b in g["bells"]:
            st = f.get(*b["pos"])
            if st is None or st[0] != "amemaru:lake_bell":
                problems.append("鐘がない：" + b["name"] + " " + str(b["pos"]))
    for pos in data["boss_marks"]["arena_bells"]:
        st = f.get(*pos)
        if st is None or st[0] != "amemaru:lake_bell":
            problems.append("鳴り石がない：" + str(pos))
    for key, want in ((data["tablet"]["pos"], "amemaru:lake_tablet"), (data["hearths"][0]["pos"], "amemaru:village_hearth"),
                      (data["memory_statues"][0]["pos"], "amemaru:village_statue")):
        st = f.get(*key)
        if st is None or st[0] != want:
            problems.append("%s がない：%s" % (want, key))

    # 5. 水
    leaks = water_leaks(f)
    for lk in leaks:
        problems.append("水が流れ出す：" + str(lk))

    # 1. どの鐘も鳴らしていない
    seen = reach(f, volume(f, data, False, False, False), start)
    print("鐘を鳴らす前、入口から立てる所:", int(seen.sum()))
    targets = [("入江の歌の碑", groups["cove"]["stele"]), ("道しるべの石", data["travel_stone"])]
    targets += [(b["name"], b["pos"]) for b in groups["cove"]["bells"]]
    for i, c in enumerate(data["chests"]):
        if c["pos"][2] > B.GORGE[1] + 2:
            targets.append(("南の岸の宝箱 %d" % i, c["pos"]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（鐘の前）行けない：" + name + " " + str(pos))
    if seen[:, :, :B.GORGE[0]].any():
        problems.append("石橋なしで、裂け目の北へ行ける")

    # 2. 入江の鐘を鳴らした
    seen = reach(f, volume(f, data, True, False, False), start)
    print("石橋が浮かんだあと、立てる所:", int(seen.sum()))
    targets = [("歌の碑", groups["main"]["stele"])]
    targets += [(b["name"], b["pos"]) for b in groups["main"]["bells"]]
    for g in data["guardians"]:
        targets.append(("守り手 " + g["id"], g["pos"]))
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    for w in data["waypoints"]:
        targets.append(("中継の灯 " + w["name"], w["pos"]))
    for z in data["story_zones"]:
        if z["id"] == "shrine":
            continue
        x0, y0, z0, x1, y1, z1 = z["box"]
        targets.append(("話の所 " + z["id"], [(x0 + x1) // 2, y0 + 1, (z0 + z1) // 2]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（石橋のあと）行けない：" + name + " " + str(pos))
    if near(seen, data["tablet"]["pos"], radius=6, dy=4):
        problems.append("湖の鐘の前に、祠の中へ入れる")
    ac = data["boss_room"]["arena"]["center"]
    if near(seen, ac, radius=48, dy=6):
        problems.append("封印を通らずに湖心へ入れる")

    # 3. 湖の鐘も鳴らした
    seen = reach(f, volume(f, data, True, True, False), start)
    seal = data["boss_room"]["seal"]
    targets = [("祠の石板", data["tablet"]["pos"]), ("祠の灯籠", data["hearths"][0]["pos"]),
               ("歌い手の氷像", data["memory_statues"][0]["pos"]),
               ("封印の前", [(seal[0][0] + seal[1][0]) // 2, seal[0][1], seal[1][2] + 2])]
    z = [zz for zz in data["story_zones"] if zz["id"] == "shrine"][0]["box"]
    targets.append(("話の所 shrine", [(z[0] + z[3]) // 2, z[1], (z[2] + z[5]) // 2]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（湖の鐘のあと）行けない：" + name + " " + str(pos))
    if near(seen, ac, radius=48, dy=6):
        problems.append("封印を通らずに湖心へ入れる（祠のあと）")

    # 4. 封印も開く
    seen = reach(f, volume(f, data, True, True, True), start)
    pts = [("湖心のまん中", ac), ("出口の円", data["boss_room"]["exit"]["center"]), ("次の階への円", data["boss_room"]["next"]["center"])]
    pts += [("鳴り石 %d" % k, p) for k, p in enumerate(data["boss_marks"]["arena_bells"])]
    for name, pos in pts:
        if not near(seen, pos):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))

    passable, standable = tables(f)
    solid = ~passable[f.v]
    for zone in data["spawns"]:
        x, y, z = (int(math.floor(c)) for c in zone["center"])
        if solid[x, y, z]:
            problems.append("湧く所のまん中がブロックの中：" + zone["id"])
    for g in data["guardians"]:
        x, y, z = (int(math.floor(c)) for c in g["pos"])
        if solid[x, y, z]:
            problems.append("守り手の出る所がブロックの中：" + g["id"])
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]), "鐘:", {g["id"]: len(g["bells"]) for g in data["bells"]})
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
