"""塔の2階（build_floor2.py が組み立てる設計データ）を、歩いて確かめる（Phase 8 その1【補完】）。

使い方: python3 tools/mapgen/check_floor2.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも・水は泳げる・つるは上れる。
check_floor1.py と同じ歩き方）
  1. 入口から、3つの迷いの森の鐘と灯台・13個の宝箱・5つの中継の灯・空き地の封印の前・森番の日誌・守り手の所まで歩いて行ける
     （迷いの森の出口は、ふつうに通れることにする）
  2. 迷いの森の出口をすべてふさぐと、入口からは、胞子の窪地・白い木立・管の谷・空き地の前へ行けない
     （迷いの森を、まわりのやぶや段差から抜けられない）
  3. 白い木立からの近道は一方通行（広場から白い木立へは上がれない。2 で一緒に確かめる）
  4. 封印の内側（森番の空き地）へは、封印を通らないと入れない。空き地の中から戻りの円まで歩ける
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor2 as B  # noqa: E402
from check_floor1 import Walker, stand_near  # noqa: E402


def near_any(seen, cells, radius=2.6):
    return stand_near(None, seen, cells, radius)


def main():
    f, woods_json = B.build()
    data = B.floor_json(f, woods_json)
    ex, ez = B.ENTRY
    start = (ex, B.ENTRY_FLOOR + 1, ez - 8)
    problems = []
    seen = Walker(f).reach(start, limit=6_000_000)
    print("入口から立てる所:", len(seen))
    targets = []
    for w in data["lost_woods"]:
        targets.append(("鐘 " + w["name"], tuple(w["bell"])))
        for e in w["exits"]:
            targets.append(("灯台 " + e["id"], tuple(e["post"])))
    for i, c in enumerate(data["chests"]):
        targets.append(("宝箱 %d" % i, tuple(c["pos"])))
    for wp in data["waypoints"]:
        targets.append(("中継の灯 " + wp["name"], tuple(wp["pos"])))
    for g in data["guardians"]:
        x, y, z = g["pos"]
        targets.append(("守り手 " + g["id"], (int(x), int(y), int(z))))
    targets.append(("森番の日誌", tuple(data["tablet"]["pos"])))
    targets.append(("道しるべの石", tuple(data["travel_stone"])))
    seal = data["boss_room"]["seal"]
    sx = (seal[0][0] + seal[1][0]) // 2
    targets.append(("封印の前", (sx, seal[0][1], seal[0][2] + 1)))
    for name, pos in targets:
        if not stand_near(f, seen, pos, radius=3.2):
            problems.append("行けない：" + name + " " + str(pos))
    # 封印の内側へは入れない
    cx, cy, cz = data["boss_room"]["arena"]["center"]
    if any(abs(x - cx) < 20 and abs(z - cz) < 20 for (x, y, z) in seen):
        problems.append("封印を通らずに、森番の空き地へ入れる")
    # 迷いの森の出口をすべてふさぐ
    blocked = set()
    for w in data["lost_woods"]:
        for e in w["exits"]:
            x0, _, z0, x1, _, z1 = e["box"]
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    blocked.add((x, z))
    seen2 = Walker(f, blocked=blocked).reach(start, limit=6_000_000)
    print("出口をふさいだとき、入口から立てる所:", len(seen2))
    for name, (px, pz) in (("胞子の窪地", B.HOLLOW), ("白い木立", B.GROVE), ("管の谷", (380, 206)),
                           ("空き地の前", (216, B.SEAL_Z + 6))):
        if any(abs(x - px) <= 6 and abs(z - pz) <= 6 for (x, y, z) in seen2):
            problems.append("迷いの森を通らずに行ける：" + name)
    # 奥の区画から入口へ戻れる（閉じこめられない）。白い木立からは、近道で広場へ飛び下りられる
    for name, pos in (("胞子の窪地", (B.HOLLOW[0], None, B.HOLLOW[1])), ("白い木立", (B.GROVE[0], None, B.GROVE[1])),
                      ("管の谷の底", (int(data["guardians"][1]["pos"][0]), None, int(data["guardians"][1]["pos"][2]))),
                      ("空き地の前", (216, None, B.SEAL_Z + 6))):
        x, _, z = pos
        back = Walker(f).reach((x, f.top(x, z) + 1, z), limit=6_000_000)
        if not stand_near(f, back, start, radius=2.0):
            problems.append("入口へ戻れない：" + name)
    hx, hz = B.HUB
    grove_only = Walker(f, blocked=blocked).reach((B.GROVE[0], f.top(*B.GROVE) + 1, B.GROVE[1]), limit=6_000_000)
    if not stand_near(f, grove_only, (hx, f.top(hx, hz) + 1, hz), radius=3.0):
        problems.append("白い木立から、近道で広場へ下りられない")
    # 空き地の中から、戻りの円まで
    inside = Walker(f).reach((int(cx), int(cy), int(cz) + 6), limit=400_000)
    exc = data["boss_room"]["exit"]["center"]
    if not stand_near(f, inside, (int(exc[0]), int(exc[1]), int(exc[2])), radius=2.0):
        problems.append("空き地の中から、戻りの円へ行けない")
    if problems:
        print("問題:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("問題なし")


if __name__ == "__main__":
    main()
