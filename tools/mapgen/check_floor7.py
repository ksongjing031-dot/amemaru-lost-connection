"""塔の7階（build_floor7.py が組み立てる設計データ）を確かめる（11月25日【補完】）。

使い方: python3 tools/mapgen/check_floor7.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも）
  1. 門も封印も閉じたまま：入口から、入口の鏡の庭（光の泉・鏡・受け石）・宝箱・道しるべの石へ行ける。崖1の北へは行けない
  2. 崖1の門だけ開く：西と東の鏡の庭・名のある魔物2体・その宝箱へ行ける。崖2の北へは行けない
  3. 門をすべて開く：森林監視所（古代記録）・合言葉の言う所・封印の前・中継の灯へ行ける。鏡の湖へは入れない
  4. 封印も開く：湖のまん中・出口の円・次の階への円へ行ける
  5. 光の迷路：解き方の向きで、光が受け石に届く（ブロックをたどる。LightMazeService.trace と同じ決まり：受け石→鏡→光をさえぎる
     ブロック、の順に見る）。はじめの向きでは届かない
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor7 as B  # noqa: E402

PASSABLE = {"light", "spruce_sign", "lantern", "snow"}
# 光を通すブロック（canOcclude が false：氷・ガラス・灯・看板）
CLEAR = {"air", "light", "ice", "glass", "white_stained_glass", "light_blue_stained_glass", "lantern", "spruce_sign"}


def solid_table(f):
    solid = np.zeros(len(f.states), dtype=bool)
    for i, st in enumerate(f.states):
        if st is not None:
            solid[i] = st[0] not in PASSABLE
    return solid


def volume(f, gates, seal_open):
    v = f.v.copy()
    for m in f.mazes:
        if m["door"]["box"] and m["id"] in gates:
            x0, y0, z0, x1, y1, z1 = m["door"]["box"]
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


def beam(f, maze, slashes):
    """ブロックをたどって光を進める（LightMazeService.trace と同じ順）。届いたら True"""
    x, y, z = maze["source"]
    d = maze["direction"]
    target = tuple(maze["target"])
    mirrors = {tuple(m["pos"]): k for k, m in enumerate(maze["mirrors"])}
    for _ in range(160):
        dx, dz = B.DIRS[d]
        x, z = x + dx, z + dz
        if (x, y, z) == target:
            return True
        k = mirrors.get((x, y, z))
        if k is not None:
            d = B.reflect(d, slashes[k])
            continue
        st = f.get(x, y, z)
        if st is not None and st[0] not in CLEAR:
            return False
    return False


def main():
    f = B.build()
    data = B.floor_json(f)
    problems = []
    ex, ez = B.ENTRY
    start = (ex, B.GROUND + 1, ez - 8)
    lx, lz = B.LAKE
    mazes = {m["id"]: m for m in data["light_mazes"]}

    # 5. 光の迷路
    for m in data["light_mazes"]:
        sol = f.answers[m["id"]]
        if not beam(f, m, sol):
            problems.append("解き方で光が届かない：" + m["name"])
        if beam(f, m, [mm["slash"] for mm in m["mirrors"]]):
            problems.append("はじめから光が届く：" + m["name"])
        for k, mm in enumerate(m["mirrors"]):
            st = f.get(*mm["pos"])
            if st is None or st[0] != "amemaru:ice_mirror":
                problems.append("鏡がない：%s %d %s" % (m["name"], k, mm["pos"]))
        for key, want in (("source", "amemaru:light_spring"), ("target", "amemaru:light_target")):
            st = f.get(*m[key])
            if st is None or st[0] != want:
                problems.append("%sがない：%s %s" % (key, m["name"], m[key]))

    # 1. 門も封印も閉じたまま
    seen = reach(f, volume(f, set(), False), start)
    print("門も封印も閉じたまま、入口から立てる所:", int(seen.sum()))
    e = mazes["entry"]
    targets = [("入口の鏡の庭の光の泉", e["source"]), ("入口の鏡の庭の受け石", e["target"]), ("道しるべの石", data["travel_stone"])]
    targets += [("入口の鏡 %d" % k, mm["pos"]) for k, mm in enumerate(e["mirrors"])]
    for i, c in enumerate(data["chests"]):
        if c["pos"][2] > B.BARRIERS[0] + 3:
            targets.append(("宝箱 %d" % i, c["pos"]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（門を閉じたまま）行けない：" + name + " " + str(pos))
    if seen[:, :, :B.BARRIERS[0] - 3].any():
        problems.append("崖1の門を通らずに北へ行ける")

    # 2. 崖1の門だけ開く
    seen = reach(f, volume(f, {"entry"}, False), start)
    targets = [(m["name"] + "の光の泉", m["source"]) for m in (mazes["west"], mazes["east"])]
    targets += [(m["name"] + "の受け石", m["target"]) for m in (mazes["west"], mazes["east"])]
    for m in (mazes["west"], mazes["east"]):
        targets += [(m["name"] + "の鏡 %d" % k, mm["pos"]) for k, mm in enumerate(m["mirrors"])]
    for g in data["guardians"]:
        targets.append(("守り手 " + g["id"], g["pos"]))
    for i, c in enumerate(data["chests"]):
        if B.BARRIERS[1] + 3 < c["pos"][2] < B.BARRIERS[0] - 3:
            targets.append(("宝箱 %d" % i, c["pos"]))
    for w in data["waypoints"]:
        if B.BARRIERS[1] + 3 < w["pos"][2] < B.BARRIERS[0] - 3:
            targets.append(("中継の灯 " + w["name"], w["pos"]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（崖1の門だけ開く）行けない：" + name + " " + str(pos))
    if seen[:, :, :B.BARRIERS[1] - 3].any():
        problems.append("崖2の門を通らずに北へ行ける")

    # 3. 門をすべて開く
    seen = reach(f, volume(f, {"entry", "west", "east"}, False), start)
    targets = [("森林監視所の記録", data["tablet"]["pos"])]
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    for w in data["waypoints"]:
        targets.append(("中継の灯 " + w["name"], w["pos"]))
    a = data["passphrase"]["area"]
    targets.append(("合言葉の言う所", [(a[0] + a[3]) // 2, a[1] + 1, (a[2] + a[5]) // 2]))
    seal = data["boss_room"]["seal"]
    targets.append(("封印の前", [(seal[0][0] + seal[1][0]) // 2, seal[0][1], seal[1][2] + 2]))
    for z in data["story_zones"]:
        x0, y0, z0, x1, y1, z1 = z["box"]
        targets.append(("話の所 " + z["id"], [(x0 + x1) // 2, y0 + 1, (z0 + z1) // 2]))
    for name, pos in targets:
        if not near(seen, pos, radius=3.5, dy=4):
            problems.append("（門をすべて開く）行けない：" + name + " " + str(pos))
    if near(seen, (lx, B.GROUND + 1, lz), radius=30):
        problems.append("封印を通らずに鏡の湖へ入れる")
    # 合言葉の言う所は、封印の手前（南）でそろう
    if not (a[2] > seal[1][2]):
        problems.append("合言葉の言う所が封印の向こうにある")

    # 4. 封印も開く
    seen = reach(f, volume(f, {"entry", "west", "east"}, True), start)
    pts = [("湖のまん中", data["boss_room"]["arena"]["center"]), ("出口の円", data["boss_room"]["exit"]["center"]),
           ("次の階への円", data["boss_room"]["next"]["center"]), ("ボスの出る所", data["boss_room"]["spawn"]["pos"])]
    for name, pos in pts:
        if not near(seen, pos):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))

    solid = solid_table(f)[f.v]
    for zone in data["spawns"]:
        x, y, z = (int(math.floor(c)) for c in zone["center"])
        if solid[x, y, z]:
            problems.append("湧く所のまん中がブロックの中：" + zone["id"])
    for g in data["guardians"]:
        x, y, z = (int(math.floor(c)) for c in g["pos"])
        if solid[x, y, z]:
            problems.append("守り手の出る所がブロックの中：" + g["id"])
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]), "光の迷路:", len(data["light_mazes"]),
          "鏡:", sum(len(m["mirrors"]) for m in data["light_mazes"]))
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
