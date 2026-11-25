"""フリーマーケットの露店の区画と市場監督の小屋の位置を、データに書き出す（Phase 6）。

書き出すもの：data/amemaru/amemaru/market_stalls/free_market.json
  - 位置はどれも、街の真ん中（大結晶の場所。/amemaru admin city build の origin）からのずれ（x が東、z が南、y は道のブロックが 0）。
    フリーマーケットの構造物の (0, 0, 0) は街の真ん中から (-48, 0, 60)、平面図の (u, v) は構造物の (u, v + 2)
  - stalls：区画ごとの番号・段（base：はじめの12、more：寄付で開く8）・元の品の名前・範囲（box。右クリックで露店の画面）・
    店の名前を浮かべて出す所（name）・出品した品を浮かべて見せる所（displays。最大3）
  - booth：市場監督フィン・ロッソの小屋（右クリックで取引所。書見台と呼び鈴は、ゲームのふつうの動きのまま）
品を見せる所は、売り場（表の台のすぐ奥の列）の台より上で、ブロックのない所（品は小さく浮かべるので、上に吊るした灯りがあってもよい）。
荷車のように奥の列が品で埋まっている区画は、表の台の品の上【補完】。
使い方: python3 -I tools/datagen/build_stalls.py
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "mapgen"))
os.chdir(os.path.join(ROOT, "tools", "mapgen"))

import build_freemarket as fm  # noqa: E402

PLACE = (-48, 0, 60)          # 構造物の (0, 0, 0) の、街の真ん中からのずれ（CityCommands の free_market）
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "amemaru", "amemaru", "market_stalls", "free_market.json")


def world(u, v, y):
    x, z = fm.XZ(u, v)
    return [PLACE[0] + x, PLACE[1] + y, PLACE[2] + z]


def air(p, u, v, y):
    st = p.get(u, v, y)
    return st is None or st[0] in ("air", "structure_void")


def roof_top(p, plot):
    top = 1
    for (u, v) in plot.cells():
        for y in range(1, fm.H):
            if not air(p, u, v, y):
                top = max(top, y)
    return top


def displays(p, plot):
    mids = list(range(1, plot.w - 1)) or [plot.w // 2]
    spots = []
    for b in (1, 0):
        for a in mids:
            u, v = plot.cell(a, b)
            for y in (2, 3):
                if air(p, u, v, y):
                    offsets = (0.3, 0.7) if len(mids) == 1 else (0.5,)
                    for off in offsets:
                        # a の向き（西→東）に沿って並べる。v は区画の真ん中の行
                        spots.append([round(world(u, v, y)[0] + off, 2), round(world(u, v, y)[1] + 0.3, 2),
                                      round(world(u, v, y)[2] + 0.5, 2)])
                    break
        if len(spots) >= 3:
            break
    return spots[:3]


def main():
    stalls = []
    for plot in fm.PLOTS:
        p = fm.base if plot.stage == "base" else fm.more
        u1, v1 = plot.u0 + plot.w - 1, plot.v0 + plot.d - 1
        lo = world(plot.u0, plot.v0, 0)
        hi = world(u1, v1, 0)
        top = roof_top(p, plot)
        # 店の名前：表の縁の真ん中、屋根より上
        fu = plot.u0 + plot.w / 2.0
        fv = plot.v0 + (plot.d - 0.2 if plot.facing == "south" else 0.2)
        x, z = fm.XZ(0, 0)
        name = [round(PLACE[0] + fu, 2), round(PLACE[1] + top + 1.6, 2), round(PLACE[2] + 2 + fv, 2)]
        stalls.append({
            "num": plot.num,
            "stage": plot.stage,
            "title": plot.title,
            "facing": plot.facing,
            "box": {"from": [lo[0], 1, lo[2]], "to": [hi[0], top, hi[2]]},
            "name": name,
            "displays": displays(p, plot),
        })
    booth_lo = world(17, 1, 1)
    booth_hi = world(23, 4, 3)
    doc = {
        "_comment": "フリーマーケットの露店の区画と、市場監督の小屋（Phase 6）。位置は街の真ん中からのずれ。"
                    "tools/datagen/build_stalls.py が tools/mapgen/build_freemarket.py の区画から書き出す",
        "booth": {"box": {"from": booth_lo, "to": booth_hi},
                  "except": [world(19, 2, 1), world(19, 3, 2)]},
        "stalls": stalls,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    for s in stalls:
        print(s["num"], s["stage"], s["title"], s["box"], "name", s["name"], "displays", len(s["displays"]))


if __name__ == "__main__":
    main()
