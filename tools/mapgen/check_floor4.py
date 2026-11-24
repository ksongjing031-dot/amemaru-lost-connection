"""塔の4階（build_floor4.py が組み立てる設計データ）を確かめる（11月24日【補完】）。

使い方: python3 tools/mapgen/check_floor4.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも。
階段・板・柵のような形のブロックは、通れない固いものとして数える）
  1. 壁画も音の順番も解いていないとき：入口から、前庭・回廊の壁画「第一時代」・浮石の庭・聖獣の庭の壁画「第二時代」・
     納骨堂（結晶騎士団の遺品）・鐘楼まで歩いて行ける。祈りの間（回廊の扉の奥）とボスの部屋（封印の奥）へは入れない
  2. 扉をすべて開けたとき：すべての宝箱・中継の灯・守り手・壁画のパネルの前・鈴の石と音の床・古代記録の碑文・封印の前まで行ける
  3. 封印を通れば、ボスの部屋の中・4つの音の床・出口の円・次の階への円まで行ける
  4. 落ちると世界の外（台地の下の何もない所）へ出てしまう穴がない
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor4 as B  # noqa: E402

# 当たり判定のないもの（見えない灯・看板・草・苔のじゅうたん・花・音の床は板として立てる）
PASSABLE = {"light", "birch_sign", "grass", "fern", "oxeye_daisy", "azure_bluet", "lily_of_the_valley", "moss_carpet",
            "white_carpet", "white_banner"}
DIRS = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}


def solid_table(f):
    n = len(f.states)
    solid = np.zeros(n, dtype=bool)
    for i, st in enumerate(f.states):
        if st is None:
            continue
        solid[i] = st[0] not in PASSABLE
    return solid


def volume(f, open_doors, seal_open):
    v = f.v.copy()
    data = B.floor_json(f)
    if open_doors:
        for m in data["murals"]:
            if "door" in m:
                x0, y0, z0, x1, y1, z1 = m["door"]["box"]
                v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = 0
        for s in data["songs"]:
            x0, y0, z0, x1, y1, z1 = s["door"]["box"]
            v[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = 0
    if seal_open:
        for (x, y, z) in f.seal_cells:
            v[x, y, z] = 0
    return v


class Walker:
    def __init__(self, f, v):
        self.solid = solid_table(f)[v]
        p = ~self.solid
        self.passable = p
        below = np.zeros_like(p)
        below[:, 1:, :] = self.solid[:, :-1, :]
        head = np.zeros_like(p)
        head[:, :-1, :] = p[:, 1:, :]
        self.stand = p & head & below

    def reach(self, start):
        sx, sy, sz = start
        if not self.stand[sx, sy, sz]:
            for dy in range(-2, 3):
                if self.stand[sx, sy + dy, sz]:
                    sy += dy
                    break
        seen = np.zeros_like(self.stand)
        seen[sx, sy, sz] = True
        q = deque([(sx, sy, sz)])
        st, pa = self.stand, self.passable
        H = B.HEIGHT
        holes = []
        while q:
            x, y, z = q.popleft()
            for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (0 <= nx < B.SIZE and 0 <= nz < B.SIZE):
                    continue
                nxt = None
                if st[nx, y, nz]:
                    nxt = (nx, y, nz)
                elif y + 2 < H and st[nx, y + 1, nz] and pa[x, y + 2, z]:
                    nxt = (nx, y + 1, nz)
                elif pa[nx, y, nz] and y + 1 < H and pa[nx, y + 1, nz]:
                    yy = y - 1
                    while yy > 0 and pa[nx, yy, nz] and not st[nx, yy, nz]:
                        yy -= 1
                    if yy > 0 and st[nx, yy, nz]:
                        nxt = (nx, yy, nz)
                    elif yy <= 0 and len(holes) < 20:
                        holes.append((nx, y, nz))
                if nxt is not None and not seen[nxt]:
                    seen[nxt] = True
                    q.append(nxt)
        return seen, holes


def near(seen, pos, radius=3.2, dy=3):
    x, y, z = pos
    r = int(math.ceil(radius))
    sub = seen[max(0, x - r):x + r + 1, max(0, y - dy):y + dy + 1, max(0, z - r):z + r + 1]
    return bool(sub.any())


def front(pos, facing):
    dx, dz = DIRS[facing]
    return [pos[0] + dx, pos[1] - 2, pos[2] + dz]


def main():
    f = B.build()
    data = B.floor_json(f)
    problems = []
    ex, ez = B.ENTRY
    start = (ex, B.GROUND + 1, ez - 8)
    cx, cz = B.ARENA
    # 1. 扉を開けていないとき
    seen, holes = Walker(f, volume(f, False, False)).reach(start)
    print("扉を開けていないとき、入口から立てる所:", int(seen.sum()))
    for h in holes:
        problems.append("世界の外へ落ちる穴：" + str(h))
    must = [("中継の灯 前庭", data["waypoints"][[w["id"] for w in data["waypoints"]].index("plaza")]["pos"]),
            ("中継の灯 浮石の庭", data["waypoints"][[w["id"] for w in data["waypoints"]].index("float")]["pos"]),
            ("中継の灯 聖獣の庭", data["waypoints"][[w["id"] for w in data["waypoints"]].index("terrace")]["pos"]),
            ("中継の灯 納骨堂", data["waypoints"][[w["id"] for w in data["waypoints"]].index("crypt")]["pos"]),
            ("結晶騎士団の遺品", data["relics"][0]["pos"]), ("古代記録の碑文", data["tablet"]["pos"])]
    for m in data["murals"]:
        if m["id"] in ("first", "second"):
            must.append(("壁画 " + m["id"] + " の前", front(m["panels"][0]["pos"], "south")))
    for name, pos in must:
        if not near(seen, tuple(pos)):
            problems.append("（扉を開けていないとき）行けない：" + name + " " + str(pos))
    chapel_mid = [420, B.GROUND + 1, 150]
    if near(seen, tuple(chapel_mid), radius=8):
        problems.append("回廊の扉を開けずに、祈りの間へ入れる")
    if near(seen, (cx, B.GROUND + 1, cz), radius=18):
        problems.append("封印を通らずに、ボスの部屋へ入れる（扉を開けていないとき）")
    # 2. 扉をすべて開けたとき
    seen, holes = Walker(f, volume(f, True, False)).reach(start)
    print("扉をすべて開けたとき、入口から立てる所:", int(seen.sum()))
    for h in holes:
        problems.append("世界の外へ落ちる穴（扉を開けたとき）：" + str(h))
    targets = []
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, c["pos"]))
    for wp in data["waypoints"]:
        targets.append(("中継の灯 " + wp["name"], wp["pos"]))
    for g in data["guardians"]:
        targets.append(("守り手 " + g["id"], [int(math.floor(c)) for c in g["pos"]]))
    for m in data["murals"]:
        facing = "west" if m.get("display") else "south"
        for k, p in enumerate(m["panels"]):
            targets.append(("壁画 %s %d の前" % (m["id"], k), front(p["pos"], facing)))
    for s in data["songs"]:
        targets.append(("鈴の石 " + s["id"], s["chime"]))
        for k, p in enumerate(s["plates"]):
            targets.append(("音の床 %s %d" % (s["id"], k), p))
    targets.append(("道しるべの石", data["travel_stone"]))
    seal = data["boss_room"]["seal"]
    sx = (seal[0][0] + seal[1][0]) // 2
    targets.append(("封印の前", [sx, seal[0][1], seal[0][2] + 1]))
    for z in data["story_zones"]:
        b = z["box"]
        targets.append(("物語の場所 " + z["id"], [(b[0] + b[3]) // 2, b[1] + 1, (b[2] + b[5]) // 2]))
    for name, pos in targets:
        if not near(seen, tuple(pos), radius=3.5, dy=4):
            problems.append("（扉をすべて開けたとき）行けない：" + name + " " + str(pos))
    if near(seen, (cx, B.GROUND + 1, cz), radius=18):
        problems.append("封印を通らずに、ボスの部屋へ入れる（扉をすべて開けたとき）")
    # 3. 封印を通る
    seen, _ = Walker(f, volume(f, True, True)).reach(start)
    ex_c = data["boss_room"]["exit"]["center"]
    nx_c = data["boss_room"]["next"]["center"]
    pts = [("ボスの部屋のまん中", [cx, B.GROUND + 1, cz]), ("出口の円", [int(ex_c[0]), int(ex_c[1]), int(ex_c[2])]),
           ("次の階への円", [int(nx_c[0]), int(nx_c[1]), int(nx_c[2])])]
    for k, p in enumerate(data["boss_marks"]["sound_plates"]):
        pts.append(("ボスの音の床 %d" % k, p))
    for name, pos in pts:
        if not near(seen, tuple(pos)):
            problems.append("（封印を通ったとき）行けない：" + name + " " + str(pos))
    # 守り手の湧く所が、固いブロックの中でない
    solid = solid_table(f)[f.v]
    for g in data["guardians"]:
        x, y, z = (int(math.floor(c)) for c in g["pos"])
        if solid[x, y, z] or solid[x, y + 1, z]:
            problems.append("守り手の所がブロックの中：" + g["id"])
    for zone in data["spawns"]:
        x, y, z = (int(math.floor(c)) for c in zone["center"])
        if solid[x, y, z]:
            problems.append("湧く所のまん中がブロックの中：" + zone["id"] + " " + str((x, y, z)))
    print("宝箱:", len(data["chests"]), "中継の灯:", len(data["waypoints"]), "守り手:", [g["id"] for g in data["guardians"]],
          "湧く所:", len(data["spawns"]), "壁画:", [(m["id"], len(m["panels"])) for m in data["murals"]])
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
