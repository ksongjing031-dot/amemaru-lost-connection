"""塔の9階（build_floor9.py が組み立てる設計データ）を確かめる（11月28日【補完】）。

使い方: python3 tools/mapgen/check_floor9.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも。柵・塀・板ガラスは越えられない）
  1. 肖像画をまだ戻していない：入口から、城下町・外庭・練兵場・庭園・本館の1階・4つの塔の上・屋根へ行ける。建国の肖像3つと建国の碑、
     建国の組の札3つ（隊長室・噴水・食料庫）と、歌と鷹の組の札（庭園の東屋・練兵場の物見台・南西の塔の上）、まぎらわしい札2つ、
     最後の家族の札のひとつ（北東の塔の上）、名のある魔物「霜蹄の旗手」に届く。2階（踊り場・礼拝堂・書庫）へは行けない
  2. 建国の肖像がそろう（大階段の氷の門が開く）：踊り場・礼拝堂（歌と鷹の肖像3つ・祭壇の札）・書庫（系譜の書・奥の書庫の札・
     名のある魔物「綴じ手」）へ行ける。前廊と肖像の回廊へは行けない
  3. 歌と鷹の肖像もそろう（回廊の扉が開く）：前廊・肖像の回廊（最後の家族の肖像3つ・女王の肖像）へ行ける。女王の書斎へは行けない
  4. 女王の名を呼ぶ（隠し扉が開く）：女王の書斎の書簡3通と、内がわの留め金に届く
  5. 封印も開く：近衛の回廊を通って、近衛の間のまん中・近衛の円・出口の円・次の階への円へ行ける
  6. 湧く所・守り手の出る所・札の台・肖像画・手がかりが、決まった所にあってブロックに埋まっていない
"""
import math
import os
import sys
from collections import deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor9 as B  # noqa: E402

PASSABLE = {"light", "spruce_sign", "spruce_wall_sign", "snow", "soul_wall_torch", "white_tulip", "lily_of_the_valley", "cobweb",
            "white_carpet", "blue_carpet", "light_blue_carpet", "blue_wall_banner"}
BLOCKED_SUFFIX = ("_wall", "_fence", "_pane", "_bed")
BLOCKED = {"chain", "iron_bars", "end_rod", "lantern", "soul_lantern", "lightning_rod", "amemaru:village_statue", "amemaru:tower_chest",
           "amemaru:floor_seal", "amemaru:royal_portrait", "amemaru:name_plate_stand", "amemaru:lineage_stele",
           "amemaru:lineage_book", "amemaru:queen_letter", "amemaru:study_latch", "amemaru:waypoint", "amemaru:travel_stone",
           "spruce_leaves", "campfire", "flower_pot", "cauldron", "water_cauldron", "lectern", "grindstone", "anvil"}


def tables(f):
    passable = np.zeros(len(f.states), dtype=bool)
    standable = np.zeros(len(f.states), dtype=bool)
    for i, st in enumerate(f.states):
        if st is None or st[0] in PASSABLE or st[0].endswith("_candle"):
            passable[i] = True
        elif st[0] not in BLOCKED and not st[0].endswith(BLOCKED_SUFFIX):
            standable[i] = True
    return passable, standable


def box_set(v, f, box, state):
    x0, y0, z0, x1, y1, z1 = box
    i = 0 if state is None else f.idx(state)
    v[min(x0, x1):max(x0, x1) + 1, min(y0, y1):max(y0, y1) + 1, min(z0, z1):max(z0, z1) + 1] = i


def volume(f, data, founding=False, chapel=False, queen=False, seal=False):
    v = f.v.copy()
    groups = {g["id"]: g for g in data["lineage"]["groups"]}
    if founding:
        box_set(v, f, groups["founding"]["door"]["box"], None)
    if chapel:
        box_set(v, f, groups["chapel"]["door"]["box"], None)
    if queen:
        box_set(v, f, data["lineage"]["queen"]["door"]["box"], None)
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
                if stand[nx, yy, nz] and yy >= B.G - 9:     # 堀の底（奈落）へ落ちたら戻される
                    nxt = (nx, yy, nz)
            if nxt is not None and not seen[nxt]:
                seen[nxt] = True
                q.append(nxt)
    return seen


def near(seen, pos, radius=2.2, dy=2):
    x, y, z = (int(math.floor(c)) for c in pos)
    r = int(math.ceil(radius))
    return bool(seen[max(0, x - r):x + r + 1, max(0, y - dy):y + dy + 1, max(0, z - r):z + r + 1].any())


def main():
    f = B.build()
    data = B.floor_json(f)
    ex, ez = B.ENTRY
    start = (ex, B.G + 1, ez - 8)
    lin = data["lineage"]
    plates = {p["id"]: p["pos"] for p in lin["plates"]}
    portraits = {p["id"]: p["pos"] for p in lin["portraits"]}
    clues = {c["id"]: c["pos"] for c in lin["clues"]}
    guardians = {g["id"]: g["pos"] for g in data["guardians"]}
    waypoints = {w["id"]: w["pos"] for w in data["waypoints"]}
    problems = []

    def expect(seen, label, pos, want=True, radius=2.2, dy=2):
        got = near(seen, pos, radius, dy)
        if got != want:
            problems.append(("届かない" if want else "届いてしまう") + "：" + label + " " + str(pos))

    # 1. まだ何も戻していない
    seen = reach(f, volume(f, data), start)
    for pid in ("bertram", "rosalinde", "edmund", "sigrid", "halvard", "albrecht", "liselotte", "helga", "tobias"):
        expect(seen, "札 " + pid, plates[pid])
    for pid in ("bertram", "rosalinde", "edmund"):
        expect(seen, "建国の肖像 " + pid, portraits[pid])
    expect(seen, "建国の碑", clues["founding"])
    expect(seen, "名のある魔物 霜蹄の旗手", guardians["hoof_bearer"], radius=4, dy=3)
    for wid in ("gatehouse", "hall"):
        expect(seen, "中継の灯 " + wid, waypoints[wid])
    for pid in ("sigrid", "halvard", "albrecht"):
        expect(seen, "（まだ）歌と鷹の肖像 " + pid, portraits[pid], want=False)
    expect(seen, "（まだ）系譜の書", clues["book"], want=False)
    expect(seen, "（まだ）札 oswin", plates["oswin"], want=False)
    expect(seen, "（まだ）札 elvira", plates["elvira"], want=False)
    expect(seen, "（まだ）踊り場", (384, B.F2 + 1, 310), want=False)
    for ch in data["chests"]:
        pass
    print("1. はじめ：届く所", int(seen.sum()))
    # 2. 建国の肖像がそろった
    seen = reach(f, volume(f, data, founding=True), start)
    for pid in ("sigrid", "halvard", "albrecht"):
        expect(seen, "歌と鷹の肖像 " + pid, portraits[pid])
    expect(seen, "系譜の書", clues["book"])
    expect(seen, "札 oswin（奥の書庫）", plates["oswin"])
    expect(seen, "札 elvira（祭壇）", plates["elvira"])
    expect(seen, "名のある魔物 綴じ手", guardians["binder"], radius=4, dy=3)
    expect(seen, "中継の灯 library", waypoints["library"])
    expect(seen, "（まだ）前廊", (384, B.F2 + 1, 280), want=False)
    for pid in ("oswin", "elvira", "liselotte"):
        expect(seen, "（まだ）最後の家族の肖像 " + pid, portraits[pid], want=False)
    print("2. 建国の肖像のあと：届く所", int(seen.sum()))
    # 3. 歌と鷹の肖像もそろった
    seen = reach(f, volume(f, data, founding=True, chapel=True), start)
    for pid in ("oswin", "elvira", "liselotte"):
        expect(seen, "最後の家族の肖像 " + pid, portraits[pid])
    expect(seen, "女王の肖像", lin["queen"]["pos"])
    expect(seen, "中継の灯 gallery", waypoints["gallery"])
    for r in data["relics"]:
        expect(seen, "（まだ）女王の書簡 " + r["id"], r["pos"], want=False)
    expect(seen, "（まだ）近衛の間", (B.ARENA[0], B.G + 1, B.ARENA[1]), want=False)
    print("3. 歌と鷹の肖像のあと：届く所", int(seen.sum()))
    # 4. 女王の名を呼んだ（隠し扉が開く）
    seen = reach(f, volume(f, data, founding=True, chapel=True, queen=True), start)
    for r in data["relics"]:
        expect(seen, "女王の書簡 " + r["id"], r["pos"])
    expect(seen, "内がわの留め金", lin["queen"]["latch"])
    print("4. 隠し扉のあと：届く所", int(seen.sum()))
    # 5. 封印も開く
    seen = reach(f, volume(f, data, founding=True, chapel=True, queen=True, seal=True), start)
    br = data["boss_room"]
    expect(seen, "近衛の間のまん中", br["arena"]["center"], radius=3)
    expect(seen, "近衛の円", br["spawn"]["pos"], radius=3)
    expect(seen, "出口の円", br["exit"]["center"], radius=2)
    expect(seen, "次の階への円", br["next"]["center"], radius=2)
    for spot in data["boss_marks"]["guard_spots"]:
        expect(seen, "近衛の立つ所", spot, radius=1.5)
    print("5. 封印のあと：届く所", int(seen.sum()))
    # 6. 置いた物の場所
    passable, standable = tables(f)
    for zn in data["spawns"]:
        x, y, z = zn["center"]
        cells = 0
        r = zn["radius"]
        for dx in range(-r, r + 1, 2):
            for dz in range(-r, r + 1, 2):
                if dx * dx + dz * dz > r * r:
                    continue
                for dy in range(-2, 3):
                    yy = y + dy
                    st_f = f.v[x + dx, yy, z + dz]
                    st_b = f.v[x + dx, yy - 1, z + dz]
                    st_h = f.v[x + dx, yy + 1, z + dz]
                    if passable[st_f] and passable[st_h] and standable[st_b]:
                        cells += 1
                        break
        if cells < 3:
            problems.append("湧く所に立てる所が少ない：%s（%d）" % (zn["id"], cells))
    for g in data["guardians"]:
        x, y, z = (int(math.floor(c)) for c in g["pos"])
        if f.v[x, y, z] != 0 and not passable[f.v[x, y, z]]:
            problems.append("守り手の出る所がブロックの中：" + g["id"])
    for p in lin["plates"]:
        if f.name_at(*p["pos"]) != "amemaru:name_plate_stand":
            problems.append("札の台がない：" + p["id"])
    for p in lin["portraits"]:
        if f.name_at(*p["pos"]) != "amemaru:royal_portrait":
            problems.append("肖像画がない：" + p["id"])
        x, y, z = p["pos"]
        dx, dz = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[p["facing"]]
        for k in range(1, 5):
            if f.v[x + dx, y + k, z + dz] == 0 or not standable[f.v[x + dx, y + k, z + dz]]:
                problems.append("肖像画の後ろの壁がない：%s（%d段目）" % (p["id"], k))
                break
    if problems:
        print("問題：")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
