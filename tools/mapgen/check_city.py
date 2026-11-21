"""コネリアの街全体の通しの確かめ（11の施設の構造物を、地図を組むときの位置に並べる）。

各 PHASE の確かめは施設ごとだったので、ここでは、すべての構造物ファイルを世界の座標に並べた1つの街で、
都市のルール（建築の文書 Tab 1 の④⑤⑦⑧⑨）と食い違っていないかを確かめる。

  - 位置：構造物どうしの重なり（置く順番で形が変わる所）、ゾーニングの図の道と広場（まだ作っていない道）に
          建物がはみ出していないか、城壁（半径112〜116）と城壁沿いの道（半径107〜112）にかかる所
  - 高さの順番：施設ごとのいちばん高い所と、④の順番・⑨の表
  - 灯りの並び：灯りの種類と数、古代の結晶灯の置き場所、入口の横の壁のランタン、大きな吊り灯、暗くて湧ける所
  - つながる道：ゾーニングの図の道と広場（まだ作っていない所は、道の高さの地面として足す）だけを通って、
                中央広場から跳ばずに各施設の中へ入れるか

座標は世界の座標（大結晶が x 0・z 0。x が東、z が南）。高さ y は道のブロックが 0（足もとが 1）。

使い方:
  python3 tools/mapgen/check_city.py                 # 確かめて、結果を書き出す（はじめの形と、育ったあとの形）
  python3 tools/mapgen/check_city.py --render DIR    # 上から見た地図・南から見た高さ・夜の灯り・つながる道の絵も DIR に書く
  python3 tools/mapgen/check_city.py --render DIR --aerial   # 鳥瞰の絵も描く（光線で描くので数分かかる）
"""
import json
import math
import os
import pickle
import sys
from collections import Counter, defaultdict, deque

import nbtlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
CACHE = os.path.join(os.path.expanduser("~"), ".cache", "amemaru-mapgen", "city")

# ---------------------------------------------------------------- 置き場所（docs/map_style.md の「置き方（地図を組むとき）」）
# (構造物, 世界の (x, y, z)（構造物の (0, 0, 0) を置く所。y は道のブロックが 0）, 施設, 重ねる形なら土台の構造物)
# 並びは、地図を組むときに置く順番（後の構造物が、前の構造物の上に置かれる）
PLACEMENTS = [
    ("guild_hall", (6, 0, -100), "ギルド", None),
    ("personal_storage", (22, -7, -82), "個人倉庫", None),            # ギルドの後に置く
    ("weapon_workshop", (79, 0, -26), "武器工房", None),
    ("armor_workshop", (60, 0, 4), "防具工房", None),
    ("accessory_workshop", (22, 0, -26), "装飾工房", None),
    ("market_north", (-18, 0, 22), "商店街", None),
    ("market_south", (-18, 0, 60), "商店街", None),
    ("free_market", (-48, 0, 60), "フリーマーケット", None),
    ("church", (-105, 0, -22), "結びの教会", None),
    ("church_garden", (-96, 0, 16), "結びの教会", None),
    ("fishing_pond", (-95, -9, 23), "釣り堀", None),                 # 記憶の庭の後に置く
    ("water_gate", (-131, -9, 48), "釣り堀", None),
    ("fishing_alley", (-66, -1, 66), "釣り堀", None),
    ("casino", (5, -3, 68), "カジノ", None),                          # 商店街の南の後に置く
    ("tower_entrance", (-22, 1, -177), "塔の入口", None),
    ("tower_plaza", (-23, -1, -147), "塔の入口", None),
    # ---- 育ったあとに重ねる形（土台と同じ所）
    ("weapon_forge_lit", (92, 1, -23), "武器工房", "weapon_workshop"),      # 工房を置いた角から (13, 1, 3)
    ("market_restaurant_open", (-18, 0, 22), "商店街", "market_north"),
    ("free_market_expanded", (-48, 0, 60), "フリーマーケット", "free_market"),
    ("church_counsel", (-105, 0, -22), "結びの教会", "church"),
    ("fishing_lakeside", (-131, -9, 48), "釣り堀", "water_gate"),
    ("fishing_pond_frozen", (-95, -9, 23), "釣り堀", "fishing_pond"),
    ("casino_highlow", (5, -3, 68), "カジノ", "casino"),
    ("casino_fishrace", (5, -3, 68), "カジノ", "casino"),
    ("tower_plaza_monument", (-23, -1, -147), "塔の入口", "tower_plaza"),
]
FACILITIES = ["ギルド", "武器工房", "防具工房", "装飾工房", "商店街", "個人倉庫", "フリーマーケット", "結びの教会", "釣り堀",
              "カジノ", "塔の入口"]

# ---------------------------------------------------------------- ゾーニングの図（Tab 1 ⑧）の道と広場。まだ作っていない所も含む
# 道に入るブロックの範囲（x0, z0, x1, z1。両端を含む。ゾーニングの図の四角と、各施設の置き方の座標から）
ROADS = {
    "塔の道（北の大通り）": (-5, -116, 5, -22),
    "市場通り（南の大通り）": (-4, 22, 4, 115),
    "工房通り（東の大通り）": (22, -4, 115, 4),
    "祈りの道（西の大通り）": (-59, -4, -22, 4),
    "環状路・北の西": (-59, -59, -6, -53), "環状路・北の東": (6, -59, 59, -53),
    "環状路・南の西": (-59, 53, -5, 59), "環状路・南の東": (5, 53, 59, 59),
    "環状路・西の北": (-59, -52, -53, -5), "環状路・西の南": (-59, 5, -53, 52),
    "環状路・東の北": (53, -52, 59, -5), "環状路・東の南": (53, 5, 59, 52),
    "教会前広場": (-70, -12, -60, 12),
    "ギルド前の小広場": (6, -86, 13, -70),
    "カジノの前の小広場": (5, 71, 12, 97), "カジノの北の路地": (5, 68, 46, 70),
    "釣り堀・教会の裏の道": (-72, 23, -60, 25), "釣り堀・路地（南北）": (-65, 66, -63, 77),
    "釣り堀・路地（東西）": (-62, 75, -49, 77),
    "塔前広場": (-23, -147, 23, -116),
}
# 道の真ん中（何も置かない所。⑤）：塔の道は真ん中7、大通りと環状路は真ん中5（交差点と中央広場は除く）
ROAD_CENTERS = {
    "塔の道（北の大通り）": (-3, -116, 3, -23),
    "市場通り（南の大通り）": (-2, 23, 2, 115),
    "工房通り（東の大通り）": (23, -2, 115, 2),
    "祈りの道（西の大通り）": (-59, -2, -23, 2),
    "環状路・北": (-58, -58, 58, -54), "環状路・南": (-58, 54, 58, 58),
    "環状路・西": (-58, -53, -54, 53), "環状路・東": (54, -53, 58, 53),
}
PLAZA_R = 22.5           # 中央広場（直径45）
WALL_IN, WALL_OUT = 112, 116      # 城壁（内側の半径112、厚さ4）
WALLROAD_IN = 107                 # 城壁沿いの道（幅5。半径107〜112）
# 城壁の外にあってよい所：北門の外の塔の入口と塔前広場、南西の水門と湖
OUTSIDE_OK = {"塔の入口", "釣り堀"}

GROUND = ("grass_block", {})
LOT = ("__lot__", {})     # 道でも施設でもない所（あとで家や木を置く所）。歩けない所として扱う
AIR = ("air", {})


def in_rect(x, z, r):
    x0, z0, x1, z1 = r
    return x0 <= x <= x1 and z0 <= z <= z1


def road_at(x, z):
    """ゾーニングの図の道・広場の名前（なければ None）"""
    if x * x + z * z <= PLAZA_R * PLAZA_R:
        return "中央広場"
    for k, r in ROADS.items():
        if in_rect(x, z, r):
            return k
    rr = math.hypot(x, z)
    if WALLROAD_IN <= rr < WALL_IN:
        return "城壁沿いの道"
    return None


def center_at(x, z):
    for k, r in ROAD_CENTERS.items():
        if in_rect(x, z, r):
            return k
    return None


# ---------------------------------------------------------------- 構造物を読む
def load(name):
    """構造物ファイルの (x, y, z) → (名前, 性質) と、大きさ。空気も入れる（置いたときに前のブロックを消す所）"""
    path = os.path.join(SRC, name + ".nbt")
    os.makedirs(CACHE, exist_ok=True)
    cpath = os.path.join(CACHE, name + ".pickle")
    stamp = os.path.getmtime(path)
    if os.path.exists(cpath):
        with open(cpath, "rb") as f:
            data = pickle.load(f)
        if data.get("stamp") == stamp:
            return data["blocks"], data["size"], data["entities"]
    t = nbtlib.load(path)
    pal = []
    for p in t["palette"]:
        props = tuple(sorted((str(k), str(v)) for k, v in p.get("Properties", {}).items()))
        pal.append((str(p["Name"]).split(":")[-1], props))
    blocks = {}
    for b in t["blocks"]:
        x, y, z = (int(c) for c in b["pos"])
        blocks[(x, y, z)] = pal[int(b["state"])]
    size = tuple(int(c) for c in t["size"])
    ents = []
    for e in t.get("entities", []):
        bp = tuple(int(c) for c in e["blockPos"])
        ents.append((str(e["nbt"].get("id", "")).split(":")[-1], bp))
    with open(cpath, "wb") as f:
        pickle.dump({"stamp": stamp, "blocks": blocks, "size": size, "entities": ents}, f)
    return blocks, size, ents


def props(st):
    return dict(st[1])


# ---------------------------------------------------------------- 街を組む
class City:
    """並べた街。world[(x, y, z)] = 状態（空気も入れる）。owner[(x, y, z)] = その所を最後に書いた構造物"""

    def __init__(self, stage="start"):
        self.stage = stage
        self.world = {}
        self.owner = {}
        self.fac_of = {}
        self.boxes = {}
        self.entities = []
        self.writes = {}          # 構造物 → 書いた所（空気も）
        for name, (ox, oy, oz), fac, base in PLACEMENTS:
            if base is not None and stage == "start":
                continue
            blocks, size, ents = load(name)
            self.boxes[name] = ((ox, oy, oz), (ox + size[0] - 1, oy + size[1] - 1, oz + size[2] - 1))
            self.fac_of[name] = fac
            mine = {}
            for (x, y, z), st in blocks.items():
                p = (x + ox, y + oy, z + oz)
                mine[p] = st
            self.writes[name] = mine
            for p, st in mine.items():
                self.world[p] = st
                self.owner[p] = name
            for kind_, (x, y, z) in ents:
                self.entities.append((kind_, (x + ox, y + oy, z + oz), name))

    # ---- 見る
    def get(self, x, y, z):
        st = self.world.get((x, y, z))
        if st is not None:
            return st
        if y == 0:
            return GROUND if road_at(x, z) else LOT
        if y < 0:
            return ("dirt", ())
        return AIR

    def name(self, x, y, z):
        return self.get(x, y, z)[0]

    def facility_at(self, p):
        o = self.owner.get(p)
        return self.fac_of.get(o) if o else None


# ---------------------------------------------------------------- 重なり（置く順番で形が変わる所）
def overlaps(city):
    names = list(city.writes)
    out = []
    for i, a in enumerate(names):
        (ax0, ay0, az0), (ax1, ay1, az1) = city.boxes[a]
        for b in names[i + 1:]:
            (bx0, by0, bz0), (bx1, by1, bz1) = city.boxes[b]
            if ax1 < bx0 or bx1 < ax0 or ay1 < by0 or by1 < ay0 or az1 < bz0 or bz1 < az0:
                continue
            base_b = [p[3] for p in PLACEMENTS if p[0] == b][0]
            if base_b == a or city.fac_of[a] == city.fac_of[b] and base_b is not None:
                continue          # 重ねる形とその土台
            wa, wb = city.writes[a], city.writes[b]
            both = set(wa) & set(wb)
            diff = [p for p in both if wa[p] != wb[p]]
            # 後の構造物（b）が、前の構造物（a）の空気でないブロックを書き換える所
            replaced = [p for p in diff if wa[p][0] != "air"]
            erased = [p for p in replaced if wb[p][0] == "air"]
            changed = [p for p in replaced if wb[p][0] != "air"]
            filled = [p for p in diff if wa[p][0] == "air"]
            out.append({
                "a": a, "b": b, "box_overlap": True, "both": len(both), "differ": len(diff),
                "erased": len(erased), "changed": len(changed), "filled": len(filled),
                "erased_examples": sorted(erased)[:8],
                "changed_examples": [(p, wa[p][0], wb[p][0]) for p in sorted(changed)[:8]],
                "changed_kinds": Counter((wa[p][0], wb[p][0]) for p in changed).most_common(8),
                "erased_kinds": Counter(wa[p][0] for p in erased).most_common(8),
                "ys": (min((p[1] for p in diff), default=None), max((p[1] for p in diff), default=None)),
            })
    return out


# ---------------------------------------------------------------- ブロックの種類
FLOWERS = {"poppy", "dandelion", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "blue_orchid",
           "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "grass", "short_grass", "fern", "pink_petals",
           "sweet_berry_bush", "dead_bush", "tall_grass", "large_fern", "lilac", "rose_bush", "peony", "sunflower"}
NONSOLID = ("_stairs", "_slab", "_fence", "_fence_gate", "_wall", "_pane", "_trapdoor", "_door", "_carpet", "_sign",
            "_banner", "_bed", "_button", "_pressure_plate", "candle", "_torch", "torch", "lantern", "chain", "end_rod",
            "lightning_rod", "potted_", "flower_pot", "bell", "lectern", "amethyst_cluster", "iron_bars", "ladder",
            "anvil", "grindstone", "campfire", "cauldron", "brewing_stand", "composter", "hopper", "scaffolding",
            "tripwire_hook", "tripwire", "cobweb", "vine", "azalea", "decorated_pot", "stonecutter", "enchanting_table",
            "chest", "rail", "glass", "_leaves", "dirt_path", "farmland", "head", "skull", "candle_cake", "cake",
            "lily_pad", "sea_pickle", "_coral", "bamboo", "sugar_cane", "flower", "petals", "carpet", "conduit",
            "daylight_detector", "repeater", "comparator", "redstone_wire", "lever", "seagrass", "kelp")
GLOW15 = {"sea_lantern", "glowstone", "shroomlight", "jack_o_lantern", "lava", "beacon", "redstone_lamp",
          "ochre_froglight", "pearlescent_froglight", "verdant_froglight"}
EMIT = {"lantern": 15, "sea_lantern": 15, "glowstone": 15, "shroomlight": 15, "jack_o_lantern": 15, "campfire": 15,
        "torch": 14, "wall_torch": 14, "end_rod": 14, "soul_lantern": 10, "soul_campfire": 10, "amethyst_cluster": 5,
        "ochre_froglight": 15, "pearlescent_froglight": 15, "verdant_froglight": 15, "fire": 15, "lava": 15,
        "furnace": 0, "blast_furnace": 0, "smoker": 0}
PLANTS = FLOWERS | {"flowering_azalea", "azalea", "sweet_berry_bush"}


def full_cube(n):
    if n in ("air", "cave_air", "void_air", "structure_void", "water", "lava"):
        return False
    if n in FLOWERS:
        return False
    return not any(t in n for t in NONSOLID)


def emit(st):
    n, p = st[0], dict(st[1])
    if n.endswith("candle") or n == "candle":
        return 3 * int(p.get("candles", 1)) if p.get("lit") == "true" else 0
    if n.endswith("campfire") and p.get("lit") == "false":
        return 0
    if n in ("furnace", "blast_furnace", "smoker"):
        return 13 if p.get("lit") == "true" else 0
    return EMIT.get(n, 0)


# ---------------------------------------------------------------- 施設ごとの大きさと高さ
def footprints(city):
    """施設ごとの、地面より上（y ≥ 1）のブロックの範囲と、いちばん高い所"""
    out = {}
    for p, st in city.world.items():
        if st[0] == "air":
            continue
        fac = city.facility_at(p)
        d = out.setdefault(fac, {"x0": 10 ** 9, "x1": -10 ** 9, "z0": 10 ** 9, "z1": -10 ** 9, "top": -99,
                                 "top_at": None, "top_block": None, "rmax": 0.0, "rmax_at": None, "blocks": 0})
        d["blocks"] += 1
        x, y, z = p
        if y >= 1:
            d["x0"], d["x1"] = min(d["x0"], x), max(d["x1"], x)
            d["z0"], d["z1"] = min(d["z0"], z), max(d["z1"], z)
        if y > d["top"]:
            d["top"], d["top_at"], d["top_block"] = y, (x, z), st[0]
        r = math.hypot(x, z)
        if r > d["rmax"] and y >= 0:
            d["rmax"], d["rmax_at"] = r, (x, y, z)
    return out


def ring_heights(city, r_max=36.0):
    """中央広場のまわりの最初の1列（広場の縁から r_max まで）にある建物の、列ごとのいちばん高い所（④「棟18まで」）"""
    cols = {}
    for (x, y, z), st in city.world.items():
        if st[0] == "air" or y < 1:
            continue
        r = math.hypot(x, z)
        if not (PLAZA_R < r <= r_max):
            continue
        if road_at(x, z) and road_at(x, z) != "中央広場":
            continue
        k = (x, z)
        if y > cols.get(k, (-1, None))[0]:
            cols[k] = (y, st[0], city.facility_at((x, y, z)))
    return cols


# ---------------------------------------------------------------- 道・広場・城壁にかかる所
ALLOWED_ON_ROAD = ("lantern", "chain", "_fence", "_wall", "polished_diorite", "quartz_pillar", "sea_lantern", "tinted_glass",
                   "_slab", "_stairs", "_sign", "_banner", "barrel", "potted_", "flower_pot", "grass_block", "azalea",
                   "bell", "composter", "hay_block", "_trapdoor", "_carpet", "water_cauldron", "cauldron")


def road_use(city):
    """ゾーニングの図の道・広場に、施設が置いたもの（地面 y 0 と、その上）"""
    paving, above, centers, odd = defaultdict(Counter), defaultdict(Counter), defaultdict(list), defaultdict(list)
    for (x, y, z), st in city.world.items():
        if st[0] == "air":
            continue
        rd = road_at(x, z)
        if rd is None:
            continue
        fac = city.facility_at((x, y, z))
        if y == 0:
            paving[(rd, fac)][st[0]] += 1
        elif y >= 1:
            above[(rd, fac)][st[0]] += 1
            c = center_at(x, z)
            if c is not None:
                centers[c].append(((x, y, z), st[0], fac))
            if not any(t in st[0] for t in ALLOWED_ON_ROAD) and rd not in ("中央広場",):
                odd[(rd, fac)].append(((x, y, z), st[0]))
    return paving, above, centers, odd


def wall_use(city):
    """城壁（半径112〜116）と城壁沿いの道（半径107〜112）と、城壁の外に施設が置いたもの"""
    out = defaultdict(Counter)
    for (x, y, z), st in city.world.items():
        if st[0] == "air" or y < 0:
            continue
        r = math.hypot(x, z)
        fac = city.facility_at((x, y, z))
        if r >= WALL_OUT:
            out[("城壁の外", fac)][st[0]] += 1
        elif r >= WALL_IN:
            out[("城壁の所", fac)][st[0]] += 1
        elif r >= WALLROAD_IN and y >= 1:
            out[("城壁沿いの道", fac)][st[0]] += 1
    return out


# ---------------------------------------------------------------- 灯り
def is_hanging(st):
    return dict(st[1]).get("hanging") == "true"


def lamp_kinds(city):
    """灯りを形で分ける：結晶灯・再建のランタン灯・壁のランタン・天井から吊るすランタン・置いたランタン・ろうそく ほか"""
    W = city.world
    found = defaultdict(list)
    for (x, y, z), st in W.items():
        n = st[0]
        if n == "sea_lantern" or n == "tinted_glass":
            if all(city.name(x, y - k, z) == "quartz_pillar" for k in (1, 2, 3)) and city.name(x, y - 4, z) == "polished_diorite":
                found["古代の結晶灯（点）" if n == "sea_lantern" else "古代の結晶灯（消）"].append((x, y - 4, z))
            elif n == "sea_lantern":
                found["結晶（建物の中・紋章など）"].append((x, y, z))
            continue
        if n in ("lantern", "soul_lantern"):
            if not is_hanging(st):
                found["置いたランタン" if n == "lantern" else "魂のランタン"].append((x, y, z))
                continue
            yy = y + 1
            while city.name(x, yy, z) == "chain":
                yy += 1
            top = city.get(x, yy, z)
            if top[0].endswith("_fence") or top[0] == "iron_bars":
                # 腕木：付け根をたどる
                kind_ = None
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    for step in range(1, 4):
                        q = (x + dx * step, yy, z + dz * step)
                        nq = city.name(*q)
                        if nq.endswith("_fence") or nq == "iron_bars":
                            below = [city.name(q[0], yy - k, q[2]) for k in (1, 2, 3)]
                            if all(b.endswith("_fence") for b in below):
                                kind_ = "再建のランタン灯"
                                break
                            continue
                        if full_cube(nq) or nq.endswith("_stairs") or nq.endswith("_slab") or nq.endswith("_wall"):
                            kind_ = kind_ or "壁のランタン"
                        break
                    if kind_ == "再建のランタン灯":
                        break
                found[kind_ or "柵から吊るすランタン"].append((x, y, z))
            else:
                found["天井から吊るすランタン" if n == "lantern" else "魂のランタン"].append((x, y, z))
            continue
        if n.endswith("candle") or n == "candle":
            if dict(st[1]).get("lit") == "true":
                found["ろうそく（灯）"].append((x, y, z))
            continue
        if n in ("campfire", "soul_campfire"):
            found["たき火（煙突・炉）" if dict(st[1]).get("lit") != "false" else "たき火（消）"].append((x, y, z))
            continue
        e = emit(st)
        if e:
            found[n].append((x, y, z))
    return found


def chandeliers(city, hung, n_min=8):
    """吊るすランタンが 7×7（高さの差2まで）の中に n_min 以上集まる所（大きな吊り灯）"""
    best = []
    for p in hung:
        grp = [q for q in hung if abs(q[0] - p[0]) <= 3 and abs(q[2] - p[2]) <= 3 and abs(q[1] - p[1]) <= 2]
        best.append((len(grp), p))
    best.sort(reverse=True)
    out, used = [], []
    for n, p in best:
        if n < n_min:
            break
        if any(abs(p[0] - u[0]) <= 3 and abs(p[2] - u[2]) <= 3 for u in used):
            continue
        used.append(p)
        out.append((p, n, city.facility_at(p)))
    return out


def roofed(city, x, y, z, up=40):
    for yy in range(y + 1, y + up):
        n = city.name(x, yy, z)
        if n == "air":
            continue
        if full_cube(n) or n.endswith("_stairs") or n.endswith("_slab") or n.endswith("_leaves"):
            return True
    return False


STEP4 = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def enclosed(city, x, y, z, reach=10):
    """足もとの高さで、東西南北のどちらにも reach までに壁（普通のブロック・窓・扉・柵）がある所（部屋の中）"""
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for k in range(1, reach + 1):
            n = city.name(x + dx * k, y, z + dz * k)
            if full_cube(n) or n.endswith("_pane") or n.endswith("_door") or n.endswith("_fence") or n == "iron_bars" \
                    or n.endswith("_wall") or "glass" in n:
                break
        else:
            return False
    return True


def outside_doors(city):
    """外へ開く扉（扉の前か後ろの1〜3マスに、空の見える所がある扉の下半分）"""
    out = []
    for (x, y, z), st in city.world.items():
        n = st[0]
        if not n.endswith("_door") or dict(st[1]).get("half") != "lower":
            continue
        f = dict(st[1]).get("facing")
        dx, dz = STEP4[f]
        sides = []
        for sgn in (1, -1):
            for k in (1, 2, 3):
                cx, cz = x + dx * sgn * k, z + dz * sgn * k
                if full_cube(city.name(cx, y, cz)):
                    break
                if not roofed(city, cx, y, cz):
                    sides.append(sgn)
                    break
        if sides:
            out.append(((x, y, z), n, sides[0] * 1, f))
    return out


def door_lamps(city, doors, lamps):
    """外へ開く扉ごとに、いちばん近い外の灯り（壁のランタン・再建のランタン灯・結晶灯・軒下の吊るす灯り）までの水平の距離。
    高さは扉の床から −1〜+6 の灯りだけを見る"""
    near = lamps.get("壁のランタン", []) + lamps.get("再建のランタン灯", []) + lamps.get("柵から吊るすランタン", []) \
        + [(x, y + 4, z) for (x, y, z) in lamps.get("古代の結晶灯（点）", [])] \
        + [p for p in lamps.get("天井から吊るすランタン", []) if not roofed(city, p[0], p[1], p[2], up=3)
           or not roofed(city, p[0], p[1] - 1, p[2])]
    out = []
    for (x, y, z), n, sgn, f in doors:
        ds = [max(abs(lx - x), abs(lz - z)) for (lx, ly, lz) in near if -1 <= ly - y <= 6]
        out.append(((x, y, z), n, city.facility_at((x, y, z)), min(ds) if ds else None))
    return out


# ---------------------------------------------------------------- 明るさと、暗くて湧ける所
def compute_light(city, pad=16):
    W = city.world
    light, q = {}, deque()
    for p, st in W.items():
        e = emit(st)
        if e:
            light[p] = e
            q.append(p)
    while q:
        x, y, z = q.popleft()
        lv = light[(x, y, z)] - 1
        if lv <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if full_cube(city.name(*n)):
                continue
            if light.get(n, 0) < lv:
                light[n] = lv
                q.append(n)
    return light


def floor_ok(st):
    n, p = st[0], dict(st[1])
    if n in GLOW15 or n == "__lot__":
        return False
    if n.endswith("_slab"):
        return p.get("type") in ("top", "double")
    if n.endswith("_stairs"):
        return p.get("half") == "top"
    return full_cube(n)


def spawn_empty(st):
    n = st[0]
    return n in ("air", "cave_air", "structure_void") or n in FLOWERS or n.endswith("_sign") or n.endswith("_banner")


NATURAL = {"grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "rooted_dirt", "sand", "gravel", "mud"}


def dark_spots(city, light):
    """暗くて湧ける所（ゲームに近い数え方）。足もとのブロックを施設が置いた所だけを数え、所で分ける"""
    out = []
    for (x, y, z), st in city.world.items():
        if st[0] == "air" or not floor_ok(st):
            continue
        c = (x, y + 1, z)
        if spawn_empty(city.get(*c)) and spawn_empty(city.get(x, y + 2, z)) and light.get(c, 0) == 0:
            if math.hypot(x, z) >= WALL_OUT:
                k = "城壁の外（湖のまわりなど）"
            elif st[0] in NATURAL and y <= 0 and not road_at(x, z):
                k = "施設の外まわりの草地（あとで家や木を置く所）"
            elif y + 1 >= 4 and not (roofed(city, x, y + 1, z) and enclosed(city, x, y + 1, z)):
                k = "屋根・高い所"
            elif road_at(x, z):
                k = "道・広場"
            else:
                k = "施設の中・舗装"
            out.append((c, st[0], city.facility_at((x, y, z)), k))
    return out


# ---------------------------------------------------------------- 歩く（跳ばずに）
IRON_OK = False      # True にすると、鉄の扉も通れる（鍵を持つ働く人として歩く）
JUMP_OK = False      # True にすると、1段だけ跳んで上がってよい（戻れない所が、跳べば戻れるかを見るため）


def free(city, x, y, z):
    st = city.get(x, y, z)
    n = st[0]
    if n in ("air", "structure_void"):
        return True
    if n.endswith("_door") and (n != "iron_door" or IRON_OK) or n.endswith("_sign") or n.endswith("_banner") \
            or n.endswith("carpet"):
        return True
    if n in ("tripwire_hook", "moss_carpet", "pink_petals") or n.endswith("_fence_gate"):
        return True
    if n.endswith("_pressure_plate"):
        return True
    if n in PLANTS and n not in ("flowering_azalea", "azalea"):
        return True
    return False


def support(city, x, y, z):
    st = city.get(x, y, z)
    n = st[0]
    if n in ("air", "structure_void", "chain", "tripwire_hook", "pink_petals", "water", "__lot__") or n.endswith("_door") \
            or n.endswith("_sign") or n.endswith("_banner") or n in PLANTS or n.endswith("_pressure_plate"):
        return False
    if n.endswith("_trapdoor") and dict(st[1]).get("open") == "true":
        return False
    if n.endswith("_fence") or n.endswith("_wall") or n.endswith("_fence_gate") or n == "iron_bars":
        return False
    if n in ("lantern", "soul_lantern", "campfire", "bell", "end_rod") or n.endswith("candle") \
            or "potted" in n or n == "flower_pot":
        return False
    return True


def stand(city, x, y, z):
    if not (free(city, x, y, z) and free(city, x, y + 1, z)):
        return False
    if city.name(x, y - 1, z).endswith("carpet"):
        return support(city, x, y - 2, z)
    return support(city, x, y - 1, z)


def step_to(city, a, dx, dz):
    """a から (dx, dz) の向きへ1歩。行ける所（walk と同じ決まり：上は階段か下付きの半ブロックだけ、下は2まで）"""
    x, y, z = a
    for dy in (0, 1, -1, -2):
        nx, ny, nz = x + dx, y + dy, z + dz
        if not stand(city, nx, ny, nz):
            continue
        if dy == 1:
            st = city.get(nx, ny - 1, nz)
            p = dict(st[1])
            stair = st[0].endswith("_stairs") and p.get("half") == "bottom" \
                or st[0].endswith("_slab") and p.get("type") == "bottom"
            if not (stair or JUMP_OK) or not free(city, x, y + 2, z):
                continue
        if dy < 0 and not all(free(city, nx, yy, nz) for yy in range(ny, y + 2)):
            continue
        return (nx, ny, nz)
    return None


def back_reach(city, goal, bounds):
    """goal へ歩いて行ける所（逆にたどる）"""
    (x0, x1), (y0, y1), (z0, z1) = bounds
    seen = {goal}
    q = deque([goal])
    while q:
        gx, gy, gz = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, -1, 1, 2):
                a = (gx - dx, gy - dy, gz - dz)
                if a in seen or not (x0 <= a[0] <= x1 and y0 <= a[1] <= y1 and z0 <= a[2] <= z1):
                    continue
                if not stand(city, *a):
                    continue
                if step_to(city, a, dx, dz) == (gx, gy, gz):
                    seen.add(a)
                    q.append(a)
    return seen


def walk(city, starts, bounds):
    (x0, x1), (y0, y1), (z0, z1) = bounds
    seen = set(starts)
    q = deque(starts)
    via = {}
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1, -2):
                nx, ny, nz = x + dx, y + dy, z + dz
                if not (x0 <= nx <= x1 and y0 <= ny <= y1 and z0 <= nz <= z1):
                    continue
                if (nx, ny, nz) in seen or not stand(city, nx, ny, nz):
                    continue
                if dy == 1:
                    st = city.get(nx, ny - 1, nz)
                    p = dict(st[1])
                    stair = st[0].endswith("_stairs") and p.get("half") == "bottom" \
                        or st[0].endswith("_slab") and p.get("type") == "bottom"
                    if not (stair or JUMP_OK):
                        continue
                    if not free(city, x, y + 2, z):
                        continue
                if dy < 0 and not all(free(city, nx, yy, nz) for yy in range(ny, y + 2)):
                    continue
                seen.add((nx, ny, nz))
                via[(nx, ny, nz)] = (x, y, z)
                q.append((nx, ny, nz))
                break
    return seen, via


def walkable_cells(city, bounds):
    """施設が置いた所で、立てるマス（足もとのブロックを施設が置いた所）"""
    (x0, x1), (y0, y1), (z0, z1) = bounds
    out = {}
    for (x, y, z), st in city.world.items():
        if st[0] == "air" or not (x0 <= x <= x1 and y0 <= y + 1 <= y1 and z0 <= z <= z1):
            continue
        if stand(city, x, y + 1, z):
            out[(x, y + 1, z)] = city.facility_at((x, y, z))
    return out


# ---------------------------------------------------------------- まとめて確かめる
BOUNDS = ((-140, 130), (-12, 60), (-180, 125))


# ---------------------------------------------------------------- 各 PHASE の確かめで決めた行き先（世界の (x, 足もと, z)）
def _tower(x, z, y):
    return (x, y, z)


def _casino(u, v, y):          # カジノの平面図（u 0 = 世界の x 13、v 0 = z 72）
    return (13 + u, y, 72 + v)


def _church(u, v, y):          # 教会の構造物の中（(0, 0) = 世界の x −105、z −22）
    return (u - 105, y, v - 22)


def _fm(u, v, y):              # フリーマーケットの平面図（(0, 0) = 世界の x −48、z 62）
    return (u - 48, y, v + 62)


def _storage(u, v, y):         # 個人倉庫の受付棟の平面図（u 0 = 世界の x 41、v は北へ。v 0 = z −63）
    return (41 + u, y, -63 - v)


def _fish(x, z, y):
    return (x, y, z)


G_T, G_C, BT_C, F2_C = 4, 3, -1, 10
GOALS = {
    "塔の入口": {k: _tower(*v) for k, v in {
        "広場のいちばん上の段": (0, -144, 4), "広場の西の端": (-22, -120, 1), "広場の東の端": (22, -138, 3),
        "記念碑の場所": (-13, -130, 2), "掲示板の前": (11, -130, 2), "門の前": (0, -149, 4), "通路": (0, -154, 4),
        "受付の窓口の前": (-4, -152, 4), "受付の中（係）": (-6, -152, 4), "受付の書見台": (-11, -156, 4), "衛兵の間": (9, -155, 4),
        "広間の入口": (0, -158, 4), "西の側廊": (-11, -166, 4), "東の側廊": (11, -170, 4), "柱の裏（西）": (-11, -172, 4),
        "転移の円": (0, -168, 7), "輪の内（くぼみ）": (0, -174, 7), "バルドの立つ所": (12, -165, 4), "書見台の前": (10, -165, 4),
        "控えの間": (-17, -165, 4), "補給の隅": (-17, -172, 4), "門番の部屋": (17, -163, 4), "踏破の記録の間": (17, -173, 4),
        "西の塔の1階": (-15, -154, 4), "東の塔の1階": (15, -154, 4), "西の塔の踊り場（+15）": (-15, -155, 16),
        "東の塔の踊り場（+15）": (16, -154, 16), "歩廊（西）": (-12, -153, 16), "歩廊（東）": (12, -153, 16),
        "歩廊（紋章の板の裏）": (0, -154, 16)}.items()},
    "カジノ": {k: _casino(*v) for k, v in {
        "入口の間": (3, 12, G_C), "両替の窓口の前": (3, 10, G_C), "景品の交換所": (3, 21, G_C), "交換の台の係": (6, 19, G_C),
        "ルーレットのまわり": (11, 12, G_C), "ジゼルの立つ所": (18, 12, G_C), "酒場の台の前": (16, 4, G_C),
        "酒場の係": (16, 2, G_C), "休み場": (10, 5, G_C), "両替の係（働く人の扉から）": (3, 8, G_C),
        "カードの卓の前（側廊）": (9, 20, G_C), "VIPルーム": (24, 12, G_C), "秘密の階段の上": (28, 9, G_C),
        "秘密の階段の下": (23, 9, BT_C), "噂の間": (25, 12, BT_C), "金庫をのぞく所": (23, 14, BT_C),
        "働く人の場所": (23, 5, G_C), "地下室": (25, 4, BT_C), "2階の踊り場": (23, 21, F2_C), "宴の間": (15, 12, F2_C),
        "舞台": (20, 12, F2_C + 1), "支配人室": (3, 12, F2_C), "表の露台": (-1, 12, F2_C), "帳場": (3, 6, F2_C),
        "働く人の部屋": (3, 20, F2_C), "催しの道具の部屋": (25, 4, F2_C), "ジゼルの部屋": (25, 13, F2_C),
        "北西の小塔の張り出し": (0, 1, F2_C)}.items()} | {k: _fish(*v) for k, v in {
        "北の路地": (20, 69, 1), "東の裏の通路": (44, 90, 1), "南の小道": (30, 98, 1), "裏口の外": (45, 77, 1)}.items()},
    "結びの教会": {k: _church(*v) for k, v in {
        "入口の中": (32, 22, 3), "長椅子の間の通路": (25, 22, 3), "呼び戻しの円の真ん中（復活の場所）": (13, 22, 3),
        "祭壇の前（セレナの立つ所）": (5, 22, 4), "北の側廊の奥": (10, 15, 3), "南の側廊の奥": (10, 29, 3),
        "これまでの記録の書見台の前（ティナ）": (31, 29, 3), "控室": (4, 15, 3), "書庫": (6, 29, 3), "書庫の古い書の前": (7, 28, 3),
        "鐘楼の入口": (27, 11, 3), "鐘の間": (27, 8, 22), "鐘の下": (29, 9, 22), "渡り廊下": (5, 12, 3),
        "療養所の1階": (5, 7, 3), "療養所の2階の病室": (5, 8, 8), "マルグリットの部屋": (5, 4, 8),
        "療養所の東の扉の外": (11, 5, 1), "北の庭の花壇の前": (20, 10, 1), "北の小道の西の端": (10, 2, 1),
        "南の小道": (25, 35, 1), "記憶の庭の北の門": (21, 38, 1), "名を刻む石の前": (21, 49, 1),
        "記憶の庭の東の門": (33, 46, 1), "記憶の庭の南西": (14, 50, 1), "記憶の庭の西の腰かけの前": (17, 48, 1),
        "書庫の机の前": (4, 28, 3), "相談所の待つ長椅子の前": (11, 29, 3)}.items()},
    "フリーマーケット": {k: _fm(*v) for k, v in {
        "北の門": (14, 0, 1), "西の門": (0, 14, 1), "東の門": (29, 14, 1), "泉の北": (14, 10, 1), "泉の南": (15, 19, 1),
        "休み場の腰かけの前": (14, 23, 1), "南東の卓": (23, 25, 1), "荷置き場": (25, 4, 1), "市場監督の立ち位置": (20, 2, 1),
        "北西の通路": (2, 5, 1), "南西の通路": (2, 24, 1)}.items()},
    "個人倉庫": {k: _storage(*v) for k, v in {
        "広間（入口の中）": (8, 1, 3), "ハーヴィの窓口の前": (8, 6, 3), "受け取り口の前": (12, 6, 3), "渡り廊下": (-2, 2, 3),
        "ギルドの東の扉の中": (-4, 2, 3), "地下1層の踊り場": (11, 1, -1), "地下1層の廊下の西（鉄格子の前）": (-2, 2, -1),
        "小部屋一": (2, 5, -1), "小部屋二": (6, 5, -1), "小部屋三": (10, 5, -1), "東の裏通り": (18, 5, 1),
        "北の裏通り": (5, 18, 1), "建物の間のすき間": (-2, 6, 1)}.items()},
    "釣り堀": {k: _fish(*v) for k, v in {
        "入口の門": (-61, 56, 1), "広場の真ん中": (-64, 56, 1), "釣り大会の札の前": (-65, 54, 1), "上の台（北）": (-68, 39, 2),
        "カウンターの前": (-67, 40, 2), "ポッカの立つ所（カウンターの内側）": (-65, 40, 2), "店の中の手引きの書見台の前": (-64, 44, 2),
        "店の扉の前（台）": (-64, 48, 2), "下の桟橋の真ん中": (-79, 49, -1), "桟橋の先（T）": (-84, 47, -1),
        "水辺の小屋の中": (-74, 54, -1), "アーチの下の桟橋の先": (-79, 42, -1), "北の遊歩道の西の端": (-93, 35, 1),
        "石の橋の上": (-93, 42, 1), "西の小道の南の端": (-93, 50, 1), "南の遊歩道": (-75, 63, 1), "南西の古い堰の上": (-84, 65, 1),
        "南東の出口": (-64, 66, 1), "記憶の庭の東の門の前": (-71, 24, 1), "記憶の庭の道の東の端（環状路）": (-60, 24, 1),
        "北の入口の道": (-69, 29, 1), "水門の内の口（城壁沿いの道）": (-89, 65, 1), "水門のアーチの中（内の半分）": (-93, 66, 1),
        "路地の曲がり角": (-64, 76, 1), "フリーマーケットの西の門の前": (-49, 76, 1)}.items()},
}
# 育ったあとの形では、PHASE 10・11 の確かめと同じく、重ねた物の置かれた所を、その前の所に置き換える
GROWN_POP = {"塔の入口": ["記念碑の場所"], "カジノ": ["宴の間"]}
GROWN_ADD = {
    "塔の入口": {"記念碑の前（碑文を読む所）": _tower(-10, -130, 2)},
    "カジノ": {k: _casino(*v) for k, v in {
        "ハイ＆ローの係（卓の奥）": (9, 24, G_C), "ハイ＆ローの卓の前": (9, 21, G_C), "魚レースの北の見物": (12, 8, F2_C),
        "魚レースの南の見物": (12, 18, F2_C), "宴の間（北）": (15, 7, F2_C)}.items()},
}
GOALS_GROWN = {
    "釣り堀（湖畔の釣り場）": {k: _fish(*v) for k, v in {
        "水門のアーチの中（外の半分）": (-97, 66, 1), "水門の外の口": (-102, 66, 1), "岸の板の道": (-100, 72, 1),
        "大会の台": (-104, 77, -1), "長い桟橋の先（T）": (-118, 74, -1), "受付のカウンターの前": (-98, 76, 1),
        "受付の小屋の中": (-95, 76, 2)}.items()},
}
GOALS_STAFF = {
    "個人倉庫（働く人）": {k: _storage(*v) for k, v in {
        "ハーヴィの立ち位置": (8, 8, 3), "事務の机": (3, 11, 3), "土間": (12, 13, 1), "地下1層の踊り場（働く人）": (13, 14, -1),
        "地下2層の古代の間": (6, 10, -5), "古代の廊下": (-4, 14, -5), "保管庫の北の通路": (-12, 14, -1),
        "保管庫の通路A（鉄格子の内）": (-6, 2, -1), "保管庫の奥の台": (-16, 2, -1), "保管庫の通路C": (-14, 10, -1),
        "2階の通り": (8, 14, 8), "台帳の間": (6, 6, 8), "ハーヴィの部屋": (2, 14, 8), "荷置き場": (13, 8, 8),
        "荷揚げ口の前": (15, 8, 8)}.items()},
}


def components(cells):
    """マスの集まりを、となり（前後左右と、上下に1つ）でつながった塊に分ける"""
    cells = set(cells)
    out = []
    while cells:
        s = cells.pop()
        grp, q = [s], deque([s])
        while q:
            x, y, z = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (0, 1, -1):
                    n = (x + dx, y + dy, z + dz)
                    if n in cells:
                        cells.remove(n)
                        grp.append(n)
                        q.append(n)
        out.append(grp)
    return out


FALLING = {"gravel", "sand", "red_sand", "suspicious_sand", "suspicious_gravel", "anvil", "chipped_anvil", "damaged_anvil",
           "dragon_egg"} | {c + "_concrete_powder" for c in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                                                              "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
                                                              "red", "black")}
FALL_THROUGH = {"air", "cave_air", "void_air", "water", "lava", "fire", "soul_fire"}


def falling_blocks(city):
    """下が空気・水の、崩れるブロック（砂利・砂・金床など）。ゲームの中では建てた直後に落ちて、形が変わる
    （書き出しで「直した所」に見え、建て直すたびに落ちる）"""
    out = []
    for (x, y, z), st in city.world.items():
        if st[0] in FALLING and city.name(x, y - 1, z) in FALL_THROUGH:
            out.append(((x, y, z), st[0], city.facility_at((x, y, z)), city.name(x, y - 1, z)))
    return sorted(out)


def check(stage, verbose=True):
    city = City(stage)
    res = {"stage": stage}
    pr = print if verbose else (lambda *a, **k: None)
    pr(f"===== {stage}（{'はじめの形' if stage == 'start' else '育ったあとの形（重ねる形をすべて重ねる）'}）")
    pr("並べた構造物:", len(city.writes), " ブロック（空気を除く）:", sum(1 for s in city.world.values() if s[0] != "air"))
    fall = falling_blocks(city)
    res["falling"] = fall
    pr("\n[崩れるブロック]（砂利・砂・金床などで、下が空気か水。建てるとすぐ落ちる）", len(fall))
    for f in fall[:20]:
        pr("  ", f)

    # ---- 位置
    ov = overlaps(city)
    res["overlaps"] = ov
    pr("\n[重なり] 箱が重なる組:", len(ov))
    for o in ov:
        pr(f"  {o['a']} → {o['b']}: 両方が書く所 {o['both']}、違う所 {o['differ']}（後が消す {o['erased']}、"
           f"後が置き換える {o['changed']}、空気を埋める {o['filled']}）y {o['ys']}")
        if o["changed_kinds"]:
            pr("     置き換え:", o["changed_kinds"])
        if o["erased_kinds"]:
            pr("     消す:", o["erased_kinds"])
    fp = footprints(city)
    res["footprints"] = fp
    pr("\n[範囲と高さ] 施設：x・z の範囲（地面より上）、いちばん高い所（道から）、中央からいちばん遠い所")
    for fac in FACILITIES:
        d = fp.get(fac)
        if d:
            pr(f"  {fac}: x {d['x0']}〜{d['x1']}、z {d['z0']}〜{d['z1']}、高さ {d['top']}（{d['top_block']} @{d['top_at']}）、"
               f"中央から {d['rmax']:.1f}（{d['rmax_at']}）")
    paving, above, centers, odd = road_use(city)
    res["centers"] = {k: len(v) for k, v in centers.items()}
    pr("\n[道の真ん中に置いた物]（⑤：真ん中には何も置かない）")
    for k, v in centers.items():
        pr(f"  {k}: {len(v)}", Counter((b, f) for _, b, f in v).most_common(10), v[:4])
    pr("\n[道・広場の上に施設が置いた物]（地面より上。灯り・腰かけ・花壇などのほか）")
    for k, v in sorted(odd.items(), key=lambda kv: -len(kv[1])):
        pr(f"  {k}: {len(v)}", Counter(b for _, b in v).most_common(8), v[:3])
    pr("\n[道・広場の地面（y 0）を施設が舗装した所]")
    for k, v in sorted(paving.items()):
        pr(f"  {k}: {sum(v.values())}", v.most_common(5))
    wu = wall_use(city)
    pr("\n[城壁・城壁沿いの道・城壁の外]")
    for k, v in sorted(wu.items()):
        pr(f"  {k}: {sum(v.values())}", v.most_common(6))
    res["wall"] = {f"{a}/{b}": sum(v.values()) for (a, b), v in wu.items()}

    # ---- 高さ（中央広場のまわり）
    rh = ring_heights(city)
    over18 = {k: v for k, v in rh.items() if v[0] > 18}
    pr("\n[中央広場のまわりの最初の1列（広場の縁から13まで）で、高さ18を超える所]（④：棟18まで）", len(over18))
    byfac = defaultdict(list)
    for (x, z), (y, b, f) in over18.items():
        byfac[f].append(((x, z), y, b))
    for f, v in byfac.items():
        pr(f"  {f}: {len(v)}", Counter((y, b) for _, y, b in v).most_common(8), sorted(v)[:4])
    res["ring_over18"] = {f: len(v) for f, v in byfac.items()}

    # ---- 灯り
    lamps = lamp_kinds(city)
    res["lamps"] = {k: dict(Counter(city.facility_at(p) or "?" for p in v)) for k, v in lamps.items()}
    pr("\n[灯りの種類と数]")
    for k, v in sorted(lamps.items(), key=lambda kv: -len(kv[1])):
        pr(f"  {k}: {len(v)}", Counter(city.facility_at(p) or "?" for p in v).most_common())
    cl = []
    for p in lamps.get("古代の結晶灯（点）", []) + lamps.get("古代の結晶灯（消）", []):
        cl.append((p, road_at(p[0], p[2]), city.facility_at(p)))
    res["crystal_lamps"] = cl
    bad_cl = [c for c in cl if c[1] not in ("市場通り（南の大通り）", "塔の道（北の大通り）", "工房通り（東の大通り）",
                                             "祈りの道（西の大通り）", "中央広場", "塔前広場", "教会前広場")]
    pr("\n[古代の結晶灯の置き場所]（⑦：大通り・中央広場・塔前広場・教会前広場）", len(cl), "決まりの外:", bad_cl)
    for road in sorted(set(c[1] for c in cl if c[1])):
        ps = sorted((c[0] for c in cl if c[1] == road), key=lambda q: (q[2], q[0]))
        pr(f"  {road}: {len(ps)}", [(q[0], q[2]) for q in ps])
    hung = lamps.get("天井から吊るすランタン", []) + lamps.get("柵から吊るすランタン", [])
    ch = chandeliers(city, hung)
    res["chandeliers"] = ch
    pr("\n[大きな吊り灯（吊るすランタンが 7×7 に8つ以上）]（⑦：カジノ・装飾工房・ギルドの広間だけ）")
    for c in ch:
        pr("  ", c)
    doors = outside_doors(city)
    dl = door_lamps(city, doors, lamps)
    res["doors"] = len(doors)
    res["door_lamps"] = dl
    far = [d for d in dl if d[3] is None or d[3] > 4]
    pr(f"\n[外へ開く扉] {len(doors)}。そばの灯りまで4より遠い扉:", len(far))
    for d in far:
        pr("  ", d)

    light = compute_light(city)
    dark = dark_spots(city, light)
    cat = Counter((f, k) for _, _, f, k in dark)
    res["dark"] = {f"{f}/{k}": n for (f, k), n in cat.items()}
    pr("\n[暗くて湧ける所]（施設が置いた足もと）", len(dark))
    for (f, k), n in sorted(cat.items(), key=lambda kv: (kv[0][1], kv[0][0] or "")):
        pr(f"  {k} / {f}: {n}")
    for d in [d for d in dark if d[3] in ("道・広場", "施設の中・舗装")][:20]:
        pr("   ", d)

    # ---- つながる道
    start = (0, 1, 0)
    reach, via = walk(city, [start], BOUNDS)
    back = back_reach(city, start, BOUNDS)
    cells = walkable_cells(city, BOUNDS)
    pr("\n[つながる道] 中央広場の真ん中から、ゾーニングの図の道と広場（まだ作っていない所は道の高さの地面）と施設の中だけを"
       "通って、跳ばずに歩ける所（行き）と、中央広場へ戻れる所（帰り）")
    # 施設と道の境目（施設が置いた所と、まだ作っていない道の地面がとなり合い、行き来できる所）
    edges = defaultdict(lambda: defaultdict(list))
    for p, fac in cells.items():
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = step_to(city, p, dx, dz)
            if q is None or q in cells:
                continue
            if city.owner.get((q[0], q[1] - 1, q[2])) is not None:
                continue
            rd = road_at(q[0], q[2]) or "?"
            edges[fac][rd].append((p, q))
    res["walk"] = {}
    for fac in FACILITIES:
        mine = [p for p, f in cells.items() if f == fac]
        indoor = [p for p in mine if roofed(city, *p)]
        r_all = sum(1 for p in mine if p in reach)
        r_in = sum(1 for p in indoor if p in reach)
        rb_in = sum(1 for p in indoor if p in reach and p in back)
        conn = {rd: len(v) for rd, v in edges.get(fac, {}).items()}
        both = {rd: sum(1 for (p, q) in v if p in reach and p in back) for rd, v in edges.get(fac, {}).items()}
        ys = Counter(p[1] for v in edges.get(fac, {}).values() for (p, q) in v)
        res["walk"][fac] = {"cells": len(mine), "reach": r_all, "indoor": len(indoor), "indoor_reach": r_in,
                            "indoor_reach_back": rb_in, "edges": conn, "edges_both": both, "edge_feet_y": dict(ys)}
        pr(f"  {fac}: 屋根の下で立てる所 {len(indoor)}（行ける {r_in}、行けて戻れる {rb_in}）")
        pr(f"     道との境目（行き来できるマス）: {conn}  そのうち行き帰りとも中央広場とつながる: {both}  足もとの高さ: {dict(ys)}")
    # 外へ開く扉の、外の側へ行けて戻れるか（鉄の扉は働く人だけなので除く）
    door_bad = []
    for (x, y, z), n, sgn, f in doors:
        if n == "iron_door":
            continue
        dx, dz = STEP4[f]
        sides = [(x + dx, y, z + dz), (x - dx, y, z - dz)]
        ok = any(sd in reach and sd in back for sd in sides)
        if not ok:
            door_bad.append(((x, y, z), n, city.facility_at((x, y, z)), [(sd in reach, sd in back) for sd in sides]))
    res["doors_unreached"] = door_bad
    pr(f"  外へ開く扉（鉄の扉を除く）で、どちらの側にも行き帰りできない扉: {len(door_bad)}")
    for d in door_bad:
        pr("    ", d)
    # 各 PHASE の確かめで決めた行き先へ、中央広場から行けて、戻れるか
    res["goals"] = {}
    goal_sets = {f: dict(g) for f, g in GOALS.items()}
    if stage == "grown":
        for f, ks in GROWN_POP.items():
            for k in ks:
                goal_sets[f].pop(k)
        for f, add in GROWN_ADD.items():
            goal_sets[f].update(add)
        goal_sets.update(GOALS_GROWN)
    pr("  [各 PHASE の行き先]（中央広場から行き・帰り）")
    for fac, goals in goal_sets.items():
        bad = [(k, p, p in reach, p in back) for k, p in goals.items() if not (p in reach and p in back)]
        res["goals"][fac] = {"n": len(goals), "bad": bad}
        pr(f"     {fac}: {len(goals)} か所", "すべて行き帰りできる" if not bad else f"できない所 {bad}")
    # 下りられるが、跳ばないと上がって戻れない所（客として歩く・働く人として歩く）
    res["oneway"] = {}
    global IRON_OK, JUMP_OK
    ow_guest = []
    for label, iron in (("客", False), ("働く人（鉄の扉も通る）", True)):
        IRON_OK = iron
        r2, _ = walk(city, [start], BOUNDS) if iron else (reach, None)
        b2 = back_reach(city, start, BOUNDS) if iron else back
        ow = [p for p in r2 if p not in b2]
        if not iron:
            ow_guest = ow
        byf = defaultdict(list)
        for p in ow:
            byf[cells.get(p) or road_at(p[0], p[2]) or "?"].append(p)
        res["oneway"][label] = {f: len(v) for f, v in byf.items()}
        if iron:
            for fac, goals in GOALS_STAFF.items():
                bad = [(k, p, p in r2, p in b2) for k, p in goals.items() if not (p in r2 and p in b2)]
                res["goals"][fac] = {"n": len(goals), "bad": bad}
                pr(f"     {fac}: {len(goals)} か所", "すべて行き帰りできる" if not bad else f"できない所 {bad}")
        pr(f"  [{label}] 行けるが、中央広場へ跳ばずに戻れない所: {len(ow)}")
        for f, ps in byf.items():
            groups = sorted(components(ps), key=len, reverse=True)
            nat = sum(1 for p in ps if city.name(p[0], p[1] - 1, p[2]) in NATURAL)
            pr(f"     {f}: {len(ps)}（うち土・草・砂の上 {nat}）", [(len(g), min(g), max(g)) for g in groups[:5]])
    IRON_OK = False
    # 戻れない所は、1段だけ跳んでよいなら戻れるか（土の岸・氷・湖の岸など、ふつうの遊び方で戻れるか）
    JUMP_OK = True
    b3 = back_reach(city, start, BOUNDS)
    JUMP_OK = False
    still = [p for p in ow_guest if p not in b3]
    res["oneway_jump"] = len(still)
    pr(f"  [客] 戻れない所のうち、1段だけ跳んでも戻れない所: {len(still)}", sorted(still)[:10])
    return city, res


def main():
    out_dir = None
    if "--render" in sys.argv:
        out_dir = sys.argv[sys.argv.index("--render") + 1]
    results = {}
    for stage in ("start", "grown"):
        city, res = check(stage)
        results[stage] = res
        print()
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
        with open(os.path.join(out_dir, "city_check.json"), "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=1, default=str)
        import city_figures
        city_figures.draw_all(sys.modules[__name__], {"start": City("start")}, out_dir, aerial="--aerial" in sys.argv)
        print("絵を書き出しました:", out_dir)


if __name__ == "__main__":
    main()
