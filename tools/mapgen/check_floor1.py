"""塔の1階（build_floor1.py が組み立てる設計データ）を、歩いて確かめる（11月22日【補完】）。

使い方: python3 tools/mapgen/check_floor1.py

確かめること（ゲームに近い歩き方：1段は跳んで上がれる・2段は上がれない・下りは何段でも・水は泳げる・つるは上れる）
  1. 入口（戻りの円の前）から、3本の石柱の核・2つの仕掛け石・10個の古代の宝箱・広間の封印の前まで歩いて行ける
  2. 奥の院（東の遺跡の石柱のある庭）には、門の格子が閉じている間は外から入れない。門がひらけば入れる
  3. 奥の院の中から、出口の階段と水の溜まりを通って、入口まで戻れる（中に閉じこめられない）
  4. 西の丘の台地には、巻き道を通らないと上がれない（巻き道をふさぐと、台地へ行けない）
  5. 光る苔の洞の中に、暗い所（空の光も灯りも届かない、湧ける床）がない
  6. 砂利・砂の下が空いていない（置いたときに落ちない）
"""
import math
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_floor1 as B  # noqa: E402
from mapgen.world import kind  # noqa: E402

PASS = {"water", "vine", "glow_lichen", "light", "moss_carpet", "spruce_sign", "oak_sign"}
LADDERS = {"vine", "ladder"}


class Walker:
    def __init__(self, f, gate_open=False, blocked=()):
        self.f = f
        self.gate_open = gate_open
        self.blocked = set(blocked)       # 通れないことにする (x, z)（巻き道をふさぐ確かめ）

    def name(self, x, y, z):
        st = self.f.get(x, y, z)
        return "air" if st is None else st[0]

    def passable(self, x, y, z):
        if (x, z) in self.blocked:
            return False
        if not self.f.inside(x, y, z):
            return y >= self.f.h        # 空の上は通れる・階の外は通れない
        n = self.name(x, y, z)
        if n == "air" or n in PASS:
            return True
        if n == "amemaru:tower_gate":
            return self.gate_open
        k = kind(n)
        return k in ("flower", "carpet")

    def standable(self, x, y, z):
        """(x, y, z) に足を置いて立てるか（足もとの y-1 が立てる床で、足と頭のマスが空いている）"""
        if y < 1 or not self.f.inside(x, y - 1, z):
            return False
        below = self.name(x, y - 1, z)
        if below == "air" or below in PASS or below == "amemaru:floor_seal":
            return False
        if below == "amemaru:tower_gate" and self.gate_open:
            return False
        if kind(below) in ("flower", "carpet"):
            return False
        return self.passable(x, y, z) and self.passable(x, y + 1, z)

    def tall_below(self, x, y, z):
        """足もとが塀・柵（高さ1.5。跳んでも上に乗れない）"""
        return kind(self.name(x, y - 1, z)) in ("wall", "fence", "gate")

    def in_water(self, x, y, z):
        return self.name(x, y, z) == "water"

    def neighbors(self, p):
        x, y, z = p
        out = []
        climbing = self.name(x, y, z) in LADDERS or self.in_water(x, y, z)
        if climbing and self.passable(x, y + 1, z) and self.passable(x, y + 2, z):
            out.append((x, y + 1, z))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < self.f.w and 0 <= nz < self.f.d):
                continue
            # 1段上がる（頭の上の高さも空いている。塀・柵の上には跳んでも乗れない）
            if self.standable(nx, y + 1, nz) and self.passable(x, y + 2, z) and not self.tall_below(nx, y + 1, nz):
                out.append((nx, y + 1, nz))
            # 跳んで、1つ上のつるにつかまる
            if self.name(nx, y + 1, nz) in LADDERS and self.passable(x, y + 2, z) and self.passable(nx, y + 2, nz):
                out.append((nx, y + 1, nz))
            # 同じ高さ
            if self.standable(nx, y, nz) or (self.in_water(nx, y, nz) and self.passable(nx, y + 1, nz)) \
                    or (self.name(nx, y, nz) in LADDERS and self.passable(nx, y + 1, nz)):
                out.append((nx, y, nz))
                continue
            # 下りる（落ちる。足と頭のマスが空いている間）
            if not (self.passable(nx, y, nz) and self.passable(nx, y + 1, nz)):
                continue
            for ny in range(y - 1, max(0, y - 24), -1):
                if not self.passable(nx, ny, nz):
                    break
                if self.standable(nx, ny, nz) or self.in_water(nx, ny, nz):
                    out.append((nx, ny, nz))
                    break
        return out

    def reach(self, start, limit=3_000_000):
        seen = {start}
        q = deque([start])
        while q and len(seen) < limit:
            p = q.popleft()
            for n in self.neighbors(p):
                if n not in seen:
                    seen.add(n)
                    q.append(n)
        return seen


def stand_near(f, seen, target, radius=2.6):
    """target（x, y, z）のそばに立てる所があるか（手が届く：横に2.6・高さ±2）"""
    tx, ty, tz = target
    for (x, y, z) in seen:
        if abs(x - tx) <= 3 and abs(z - tz) <= 3 and abs(y - ty) <= 2:
            if math.hypot(x + 0.5 - (tx + 0.5), z + 0.5 - (tz + 0.5)) <= radius:
                return True
    return False


def main():
    f, cores, seal = B.build()
    ex, ez = B.ENTRY
    start = (ex, B.ENTRY_FLOOR + 1, ez - 8)
    problems = []
    w = Walker(f)
    seen = w.reach(start)
    print("入口から立てる所:", len(seen))
    targets = [("石柱の核 %d" % (i + 1), c) for i, c in enumerate(cores)]
    targets += [("仕掛け石 %d" % (i + 1), s) for i, s in enumerate(f.switches)]
    targets += [("古代の宝箱 %d" % (i + 1), c) for i, c in enumerate(f.chests)]
    sx = min(c[0] for c in seal)
    targets.append(("広間の封印の前", (B.HALL[0], B.BOSS_FLOOR + 1, B.GATE_Z + 2)))
    sanctum_targets = {"石柱の核 2", "古代の宝箱 %d" % (f.chests.index(next(c for c in f.chests if B.SANCTUM[0] < c[0] < B.SANCTUM[2]
                                                                    and B.SANCTUM[1] < c[2] < B.SANCTUM[3])) + 1)}
    for name, t in targets:
        ok = stand_near(f, seen, t)
        inside = name in sanctum_targets
        if inside:
            print("  %-12s %s（門が閉じている間）" % (name, "入れてしまう" if ok else "入れない（正しい）"))
            if ok:
                problems.append(name + "：門が閉じているのに、外から行ける")
        else:
            print("  %-12s %s" % (name, "行ける" if ok else "行けない"))
            if not ok:
                problems.append(name + "：入口から歩いて行けない")
    # 門がひらいたとき
    wo = Walker(f, gate_open=True)
    seen_open = wo.reach(start)
    for name, t in targets:
        if name in sanctum_targets:
            ok = stand_near(f, seen_open, t)
            print("  %-12s 門がひらくと %s" % (name, "行ける" if ok else "行けない"))
            if not ok:
                problems.append(name + "：門がひらいても行けない")
    # 奥の院の中から、入口へ戻れる（門が閉じたまま）
    core = cores[1]
    inside_start = None
    for dx, dz in ((-1, 0), (0, 1), (0, -1), (-2, 0)):
        p = (core[0] + dx, core[1], core[2] + dz)
        if w.standable(*p):
            inside_start = p
            break
    back = w.reach(inside_start) if inside_start else set()
    ok = start in back or any(abs(x - start[0]) <= 1 and abs(z - start[2]) <= 1 for (x, y, z) in back)
    print("奥の院の中から入口へ戻れる（門は閉じたまま）:", "はい" if ok else "いいえ")
    if not ok:
        problems.append("奥の院の中から出られない")
    # 西の丘：巻き道をふさぐと、台地へ行けない
    wl = Walker(f, blocked={c for c in f.ledge})
    seen_l = wl.reach(start)
    plateau = [(x, y, z) for (x, y, z) in seen_l if f.in_hill_top[x, z] and y >= B.HILL_TOP]
    print("巻き道をふさいだとき、台地に立てる所:", len(plateau))
    if plateau:
        problems.append("巻き道を通らずに、西の丘の台地へ上がれる：%s" % (plateau[:3],))
    # 湧く所：真ん中と巡回の点に立てて、巡回の点どうしが歩いてつながる
    data = B.floor_json(cores, seal, f)
    for zone in data["spawns"]:
        pts = [tuple(int(v) for v in zone["center"])] + [tuple(p) for p in zone.get("route", [])]
        bad = [p for p in pts if not w.standable(*p) and not w.in_water(*p)]
        if bad:
            problems.append("湧く所 %s の立てない点：%s" % (zone["id"], bad))
            continue
        if zone.get("route"):
            region = w.reach(pts[1], limit=400_000)
            lost = [p for p in pts[2:] if p not in region]
            if lost:
                problems.append("湧く所 %s の巡回の点へ歩いて行けない：%s" % (zone["id"], lost))
    for g in data.get("guardians", []):
        gp = (int(g["pos"][0]), int(g["pos"][1]), int(g["pos"][2]))
        if not w.standable(*gp) or gp not in seen:
            problems.append("守り手 %s の出る所に立てないか、入口から行けない：%s" % (g["id"], gp))
    print("湧く所:", len(data["spawns"]), "守り手:", len(data.get("guardians", [])))
    # 落ちるブロック（砂利・砂）の下が空いている所（置くと落ちる。柱の底なら奈落へ、洞の天井なら通路へ）
    falling = [p for p, st in f.blocks.items() if st[0] in ("gravel", "sand", "red_sand") and f.get(p[0], p[1] - 1, p[2]) is None]
    print("下が空いた砂利・砂:", len(falling))
    if falling:
        problems.append("下が空いた砂利・砂がある：%s" % (falling[:5],))
    # 洞の暗い所（空の光も灯りも届かない、湧ける床）
    dark = cave_dark_spots(f)
    print("洞の暗くて湧ける所:", len(dark))
    if dark:
        problems.append("洞に暗くて湧ける所がある：%s" % (dark[:5],))
    print()
    if problems:
        print("直すこと:")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("すべて確かめました。")


EMIT = {"lantern": 15, "sea_lantern": 15, "glow_lichen": 7, "amemaru:tower_pillar": 0, "amemaru:tower_switch": 2,
        "amemaru:tower_chest": 3, "amemaru:tower_gate": 6}


def cave_dark_spots(f):
    """西の丘の洞のまわりだけ：空の光（上が開いている所から）と灯りの光を広げて、どちらも0の、湧ける床を数える"""
    a = math.radians(B.CAVE_MOUTH)
    cx = int(B.HILL[0] + math.cos(a) * 12)
    cz = int(B.HILL[1] + math.sin(a) * 12)
    box = (cx - 16, 4, cz - 16, cx + 16, 30, cz + 16)
    x0, y0, z0, x1, y1, z1 = box

    def opaque(x, y, z):
        st = f.get(x, y, z)
        if st is None:
            return False
        k = kind(st[0])
        return k == "cube" and st[0] not in ("water", "glass", "amemaru:tower_gate") and not st[0].endswith("_leaves") \
            and not st[0].endswith("stained_glass")
    light = {}
    q = deque()
    # 空の光：上が開いている柱の、いちばん上の空いた所から下へ
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(B.HEIGHT - 1, y0 - 1, -1):
                if opaque(x, y, z):
                    break
                light[(x, y, z)] = 15
                q.append((x, y, z))
    for (p, st) in f.blocks.items():
        x, y, z = p
        if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1 and EMIT.get(st[0], 0) > 0:
            light[p] = max(light.get(p, 0), EMIT[st[0]])
            q.append(p)
    while q:
        x, y, z = q.popleft()
        lv = light[(x, y, z)] - 1
        if lv <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if not (x0 <= n[0] <= x1 and y0 <= n[1] <= y1 and z0 <= n[2] <= z1) or opaque(*n):
                continue
            if light.get(n, 0) < lv:
                light[n] = lv
                q.append(n)
    dark = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0 + 1, y1):
                below = f.get(x, y - 1, z)
                if below is None or not opaque(x, y - 1, z):
                    continue
                if f.get(x, y, z) is None and f.get(x, y + 1, z) is None and light.get((x, y, z), 0) == 0:
                    dark.append((x, y, z))
    return dark


if __name__ == "__main__":
    main()
