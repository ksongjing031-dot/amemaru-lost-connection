"""コネリアの商店街（PHASE 5。建築の文書「PHASE 5：商店街」の設計どおり）。
南区の市場通り（中央広場 → 南門。幅9）の両側に並ぶ14軒の店と、路地・裏通り・小広場の露店。交易が細った今も続く市場（Phase 0-A）。
店は4つの型（A 平入り2階・B 妻入り2階・C 妻入り3階・D 角の平入り2階）の組み替えで、店ごとに色・看板・品・屋根の向きを変える。
マルタ・ポムの雑貨屋（中央広場の東の角）と、寄付で開くペトラ・ポムの料理屋（中央広場の西の角）のほかは、通りのにぎわいの店【補完】。

座標は世界の座標（大結晶が x 0・z 0。x が東、z が南）。市場通りは x -4..4（真ん中 -2..2 は古代の白い石畳、両側2つずつが端の帯）。
環状路（z 53..59）が通りを横切るので、構造物は2つに分ける：
  - market_north.nbt：z 22..53（中央広場の縁から環状路まで）、x -18..18。構造物の (0, 0, 0) は世界の (x -18, 道の高さ, z 22)
    z 53 は環状路の1列目：環状路に向く角の店（薬屋・布と仕立て）の横の入口の踏み段・横の吊り看板・屋根の出だけを置き、
    ほかは structure_void（環状路の石畳を消さない）
  - market_south.nbt：z 60..103（環状路から南門の前の小広場まで）、x -18..18。構造物の (0, 0, 0) は世界の (x -18, 道の高さ, z 60)
    東側の x 5..18、z 68..103 は structure_void にして、置いても消さない（カジノ（PHASE 10）の場所。ゾーニングの図では
    建物が x 9..39、z 68..94、前の広場への道が x 5..9。その南は、南門の前の東の区画）
  - market_restaurant_open.nbt：寄付で料理屋が開いたあとの形（x -18..-4、z 22..34）。北の構造物の上に重ねて置く
y=0 が地面（道・石畳）。店の1階の床のブロック y=1（+1）、2階 y=6（+6）、3階 y=11（+11）。
使い方: python3 tools/mapgen/build_market.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.fixtures import crystal_lamp, hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, log, sign_nbt, slab, stairs,
                          trapdoor, wall)

XMIN, XMAX = -18, 18
H = 26
Y1, Y2, Y3 = 1, 6, 11
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

pal = Palette(seed=505, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = pal.rng


# ---------------------------------------------------------------- 材料（都市のルール③⑤）
def gray_stone():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで"""
    r = rng.random()
    if r < 0.82:
        return block("stone_bricks")
    if r < 0.92:
        return block("cracked_stone_bricks")
    if r < 0.97:
        return block("mossy_stone_bricks")
    return block("andesite")


def ancient_paving():
    """古代の白い石畳：磨いた閃緑岩6・閃緑岩2・方解石1・灰色の石のつぎはぎ1"""
    r = rng.random()
    if r < 0.6:
        return block("polished_diorite")
    if r < 0.8:
        return block("diorite")
    if r < 0.9:
        return block("calcite")
    return block("stone_bricks") if r < 0.96 else block("andesite")


def band_paving():
    """端の帯：石レンガと安山岩"""
    r = rng.random()
    if r < 0.62:
        return block("stone_bricks")
    if r < 0.86:
        return block("polished_andesite")
    return block("cracked_stone_bricks")


def alley_paving():
    """路地と裏通り：丸石・砂利・土の道"""
    r = rng.random()
    if r < 0.5:
        return block("cobblestone")
    if r < 0.8:
        return block("dirt_path")
    return block("gravel")


def beam(axis):
    return log("spruce", axis, stripped=True)


POST = log("spruce", "y", stripped=True)


def pane():
    return block("glass_pane", north=False, south=False, east=False, west=False, waterlogged=False)


def candle(color=None, n=1):
    return block("candle" if color is None else color + "_candle", candles=n, lit=True, waterlogged=False)


# ---------------------------------------------------------------- 構造物（区画ごと）
class Seg:
    """1つの構造物。世界の座標 (x, y, z) で置く。"""

    def __init__(self, name, z0, z1):
        self.name, self.z0, self.z1 = name, z0, z1
        self.W, self.D = XMAX - XMIN + 1, z1 - z0 + 1
        self.w = World(self.W, H, self.D)

    def inside(self, x, z):
        return XMIN <= x <= XMAX and self.z0 <= z <= self.z1

    def sx(self, x, z):
        return x - XMIN, z - self.z0

    def put(self, x, y, z, state, nbt=None):
        if not self.inside(x, z):
            return
        a, b = self.sx(x, z)
        self.w.put(a, y, b, state, nbt)

    def get(self, x, y, z):
        if not self.inside(x, z):
            return None
        a, b = self.sx(x, z)
        return self.w.get(a, y, b)

    def frame(self, x, y, z, facing, item, rotation=0, name=None):
        a, b = self.sx(x, z)
        self.w.item_frame(a, y, b, facing, item, rotation=rotation, name=name)


# ---------------------------------------------------------------- 店の品と色（都市のルール⑦：店ごとに1色、看板の下に品）【補完】
# sign：吊り看板の2行。color：旗の色（白い縁）。ware：看板の下の額縁の品。ledge：店先のカウンターに並べる品（"frame:品" は額縁）。
# shelf：売り場の棚の品（額縁）。work：奥の仕事場の道具（ブロック）。floor：売り場の床の板。bed：2階の寝台の色。
TRADES = {
    "restaurant": dict(sign=("料理屋", "ペトラ"), color="orange", ware="cooked_cod", floor="spruce_planks", bed="orange",
                       ledge=["flower_pot:potted_red_tulip", "frame:bowl", "flower_pot:potted_dandelion"],
                       shelf=["cod", "salmon", "bread", "baked_potato"], work=["smoker", "cauldron", "barrel"]),
    "general": dict(sign=("雑貨屋", "マルタ"), color="green", ware="torch", floor="oak_planks", bed="green",
                    ledge=["frame:bread", "frame:torch", "frame:lead", "frame:glass_bottle"],
                    shelf=["bread", "torch", "lead", "glass_bottle", "string", "paper", "apple", "arrow"],
                    work=["barrel", "chest", "barrel"]),
    "bakery": dict(sign=("パン屋", ""), color="yellow", ware="bread", floor="birch_planks", bed="yellow",
                   ledge=["cake", "frame:bread", "frame:cookie"], shelf=["bread", "cookie", "pumpkin_pie", "wheat"],
                   work=["smoker", "hay_block", "barrel"]),
    "greengrocer": dict(sign=("八百屋", ""), color="lime", ware="carrot", floor="oak_planks", bed="lime",
                        ledge=["pumpkin", "melon", "frame:carrot"], shelf=["carrot", "potato", "beetroot", "apple"],
                        work=["composter", "barrel", "hay_block"]),
    "apothecary": dict(sign=("薬屋", ""), color="cyan", ware="glass_bottle", floor="spruce_planks", bed="cyan",
                       ledge=["flower_pot:potted_fern", "frame:honey_bottle", "flower_pot:potted_blue_orchid"],
                       shelf=["glass_bottle", "honey_bottle", "glow_berries", "sugar"],
                       work=["brewing_stand", "cauldron", "barrel"]),
    "tools": dict(sign=("道具屋", ""), color="gray", ware="iron_pickaxe", floor="spruce_planks", bed="gray",
                  ledge=["frame:iron_shovel", "frame:lantern", "frame:iron_axe"],
                  shelf=["iron_pickaxe", "iron_shovel", "fishing_rod", "shears", "lead", "compass"],
                  work=["grindstone", "barrel", "chest"]),
    "lights": dict(sign=("ろうそくと灯り", ""), color="brown", ware="lantern", floor="dark_oak_planks", bed="brown",
                   ledge=["candle:white", "candle:yellow", "candle:orange"],
                   shelf=["lantern", "candle", "honeycomb", "torch"], work=["barrel", "cauldron", "barrel"]),
    "cloth": dict(sign=("布と仕立て", ""), color="magenta", ware="white_wool", floor="birch_planks", bed="magenta",
                  ledge=["magenta_wool", "light_blue_wool", "yellow_wool"], shelf=["string", "white_wool", "leather", "feather"],
                  work=["loom", "barrel", "chest"]),
    "butcher": dict(sign=("肉と燻製", ""), color="red", ware="cooked_porkchop", floor="spruce_planks", bed="red",
                    ledge=["frame:cooked_beef", "frame:cooked_chicken", "hay_block"],
                    shelf=["cooked_porkchop", "cooked_beef", "cooked_mutton", "rabbit_stew"],
                    work=["smoker", "barrel", "barrel"]),
    "books": dict(sign=("本と地図", ""), color="purple", ware="map", floor="dark_oak_planks", bed="purple",
                  ledge=["frame:book", "frame:map", "frame:writable_book"], shelf=["book", "map", "paper", "feather"],
                  work=["cartography_table", "lectern", "bookshelf"]),
    "leather": dict(sign=("革と鞄", ""), color="black", ware="leather", floor="spruce_planks", bed="black",
                    ledge=["frame:leather", "frame:saddle", "frame:leather_boots"], shelf=["leather", "rabbit_hide", "string", "lead"],
                    work=["barrel", "chest", "barrel"]),
    "flowers": dict(sign=("花と種", ""), color="pink", ware="poppy", floor="birch_planks", bed="pink",
                    ledge=["flower_pot:potted_poppy", "flower_pot:potted_cornflower", "flower_pot:potted_allium"],
                    shelf=["wheat_seeds", "pumpkin_seeds", "beetroot_seeds", "bone_meal"],
                    work=["composter", "barrel", "flowering_azalea_leaves"]),
    "hardware": dict(sign=("金物屋", ""), color="light_gray", ware="bucket", floor="spruce_planks", bed="light_gray",
                     ledge=["frame:bucket", "frame:chain", "frame:iron_nugget"], shelf=["bucket", "chain", "iron_nugget", "shears"],
                     work=["cauldron", "barrel", "chest"]),
    "tea": dict(sign=("茶と香辛料", ""), color="blue", ware="cocoa_beans", floor="oak_planks", bed="blue",
                ledge=["frame:sugar", "frame:cocoa_beans", "frame:sweet_berries"],
                shelf=["cocoa_beans", "sweet_berries", "sugar", "honey_bottle"], work=["barrel", "hay_block", "barrel"]),
}


# ---------------------------------------------------------------- 店
class Shop:
    """1軒の店。side "W"（通りの西。正面は東）か "E"（通りの東。正面は西）。z0..z1 は通りに沿う範囲（間口 F）。奥行きは11。

    店の中の座標 (a, b)：a は通りから店を見て左→右（0..F-1）、b は正面の壁→裏の壁（0..10。2階の張り出しは b=-1）。
    kind："A" 平入り2階（張り出し）、"B" 妻入り2階、"C" 妻入り3階（張り出し）、"D" 角の平入り2階（張り出し。妻を横の道に向ける）。
    free：横の壁のうち、外に出ている側（世界の向き "north"・"south"。路地・角・広場）。
    mirror：左右を入れ替える（入口を、通りから見て左の端に寄せる）。chamfer：1階の正面の左の角を、2ブロック斜めに切る
    （大通りと環状路の交差点の角切り。都市のルール⑤）。切った所の上は、2階が持ち送りで張り出す（下の空きは5）。
    """
    D = 11

    def __init__(self, seg, key, side, z0, z1, kind, trade, free=(), door_wood="spruce", chimney=False, mirror=False,
                 chamfer=False):
        self.seg, self.key, self.side, self.z0, self.z1 = seg, key, side, z0, z1
        self.F = z1 - z0 + 1
        self.kind, self.t = kind, TRADES[trade]
        self.trade = trade
        self.free = set(free)
        self.door_wood = door_wood
        self.chimney = chimney
        self.jetty = kind in ("A", "C", "D")
        self.bf = -1 if self.jetty else 0                  # 2階の正面の壁の b
        self.front = "east" if side == "W" else "west"
        self.back = OPP[self.front]
        self.mirror, self.chamfer = mirror, chamfer
        self.left = "south" if side == "W" else "north"
        if mirror:
            self.left = OPP[self.left]
        self.right = OPP[self.left]
        self.floors = [Y1, Y2, Y3] if kind == "C" else [Y1, Y2]
        # 壁の上の梁（屋根の軒先の高さ）。2階建ては2階の壁を1段高くして軒 +11、棟 +18（都市のルール④の店の棟 18〜22。
        # 中央広場を囲む最初の列の雑貨屋・料理屋も、棟18までの中）。3階建ては軒 +15、棟 +21
        self.eave = self.floors[-1] + (5 if kind != "C" else 4)
        self.attic = self.eave + 1                         # 屋根裏の床
        F = self.F
        self.door_a = F - 2 if F <= 7 else F - 3            # 入口（両開きなら door_a と door_a+1）
        self.double = F >= 8
        self.win = (1, self.door_a - 2)                     # 店の窓（a の範囲）
        self.stair = (F - 3, F - 2)                        # 階段（a の2列）
        self.npc = (min(F // 2, self.stair[0] - 1) if F <= 6 else F // 2, 5)   # 店の人の立ち位置
        self.roofs = []

    # -- 座標と向き
    def xz(self, a, b):
        if self.mirror:
            a = self.F - 1 - a
        if self.side == "W":
            return -5 - b, self.z1 - a
        return 5 + b, self.z0 + a

    def cut(self, a, b):
        """角切りで、1階から取り除くマスか（正面の左の角から2ブロック斜め）。"""
        return self.chamfer and 0 <= a and 0 <= b and a + b < 2

    def d(self, rel):
        return {"front": self.front, "back": self.back, "left": self.left, "right": self.right}.get(rel, rel)

    def put(self, a, b, y, state, nbt=None):
        x, z = self.xz(a, b)
        self.seg.put(x, y, z, state, nbt)

    def get(self, a, b, y):
        x, z = self.xz(a, b)
        return self.seg.get(x, y, z)

    def frame(self, a, b, y, facing, item, rotation=0, name=None):
        x, z = self.xz(a, b)
        self.seg.frame(x, y, z, self.d(facing), item, rotation=rotation, name=name)

    def side_free(self, which):
        """which は "left"・"right"。その横の壁が外に出ているか。"""
        return self.d(which) in self.free

    def walls_at(self, y):
        """高さ y の外壁のマス (a, b)。"""
        bf = self.bf if y >= Y2 else 0
        cells = set()
        for a in range(self.F):
            cells.add((a, bf))
            cells.add((a, 10))
        for b in range(bf, 11):
            cells.add((0, b))
            cells.add((self.F - 1, b))
        return cells

    # -- 組み立て
    def build(self):
        self.foundation()
        self.lower_walls()
        for fy in self.floors[1:]:
            self.upper_walls(fy)
        self.inner_floors()
        self.front_ground_floor()
        self.upper_windows()
        self.back_and_sides()
        self.roof()
        if self.chimney:
            self.build_chimney()
        self.chamfer_corbels()
        self.interior()

    def foundation(self):
        for a in range(self.F):
            for b in range(0, 11):
                # 角切りの所は道（端の帯の石）
                self.put(a, b, 0, band_paving() if self.cut(a, b) else block("cobblestone"))

    def lower_walls(self):
        F = self.F
        cells = {c for c in self.walls_at(Y1) if not self.cut(*c)}
        corners = {(0, 0), (F - 1, 0), (0, 10), (F - 1, 10)}
        if self.chamfer:
            cells |= {(1, 1)}                         # 斜めの壁の真ん中の角の柱
            corners = (corners - {(0, 0)}) | {(1, 1), (0, 2)}   # (2, 0) は店の窓のまま
        for (a, b) in cells:
            self.put(a, b, Y1, block("polished_andesite"))
            for y in range(Y1 + 1, Y2):
                if (a, b) in corners:
                    self.put(a, b, y, block("polished_andesite") if y % 2 else block("stone_bricks"))
                else:
                    self.put(a, b, y, gray_stone())

    def chamfer_corbels(self):
        """角切りの上：2階の床の下に持ち送り（斜めの壁から）と、角の下に吊るす灯り。"""
        if not self.chamfer:
            return
        self.put(1, 0, Y2 - 1, stairs(pal.frame, self.back, "top"))
        self.put(0, 1, Y2 - 1, stairs(pal.frame, self.d("right"), "top"))
        self.put(0, 0, Y2 - 1, lantern(True))

    def ceiling(self, fy):
        """階 fy の天井のブロックの高さ（上の階の床か、屋根裏の床）。"""
        return self.attic if fy == self.floors[-1] else fy + 5

    def upper_walls(self, fy):
        """木組みの階：fy は床の高さ（梁の帯）、その上は柱と漆喰、いちばん上の段は桁（いちばん上の階は軒の梁 self.eave）。"""
        F, bf = self.F, self.bf
        posts_a = {0, F - 1} | ({F // 2} if F >= 8 else set())
        posts_b = {bf, 10, 4, 7}
        top = self.eave if fy == self.floors[-1] else fy + 4
        for (a, b) in self.walls_at(fy):
            along_a = b in (bf, 10) and a not in (0, F - 1)
            axis = self._axis_along_a() if along_a else self._axis_along_b()
            self.put(a, b, fy, beam(axis))
            self.put(a, b, top, beam(axis))
            is_post = (b in (bf, 10) and a in posts_a) or (a in (0, F - 1) and b in posts_b)
            for y in range(fy + 1, top):
                self.put(a, b, y, POST if is_post else block(pal.plaster))
        if self.jetty and fy == Y2:
            for a in sorted(posts_a):
                self.put(a, -1, Y2 - 1, stairs(pal.frame, self.back, "top"))

    def _axis_along_a(self):
        return "z"          # a は世界の z に沿う

    def _axis_along_b(self):
        return "x"

    def inner_floors(self):
        F = self.F
        wood = self.t["floor"]
        for a in range(1, F - 1):
            for b in range(1, 10):
                self.put(a, b, Y1, block(wood))
        for fy in self.floors[1:]:
            for a in range(1, F - 1):
                for b in range(self.bf + 1, 10):
                    self.put(a, b, fy, beam(self._axis_along_a()) if b == 5 else block("spruce_planks"))
        for a in range(1, F - 1):
            for b in range(self.bf + 1, 10):
                self.put(a, b, self.attic, block("spruce_planks"))

    # -- 正面の1階：店の窓と店先のカウンター、入口と踏み段、吊り看板と品の額縁
    def window_cols(self):
        a0, a1 = self.win
        return [a for a in range(a0, a1 + 1) if not self.cut(a, 0) and not self.cut(a, 1)]

    def front_ground_floor(self):
        for a in self.window_cols():
            for y in range(Y1 + 1, Y1 + 4):
                self.put(a, 0, y, pane())
            self.put(a, -1, Y1, slab("spruce", "top"))              # 店先のカウンター
            self.put(a, 0, Y1 + 4, beam(self._axis_along_a()))      # 窓の上の梁
        self.ledge_goods()
        da = self.door_a
        doors = [da, da + 1] if self.double else [da]
        for i, a in enumerate(doors):
            hinge = "left" if (i == 0) else "right"
            self.put(a, 0, Y1 + 1, door(self.door_wood, self.front, False, hinge=hinge))
            self.put(a, 0, Y1 + 2, door(self.door_wood, self.front, True, hinge=hinge))
            self.put(a, -1, Y1, stairs("stone_brick", self.back))
        pier = da - 1
        self.put(pier, -1, Y1 + 3, block("spruce_wall_hanging_sign", facing="south", waterlogged=False),
                 sign_nbt(list(self.t["sign"]) + ["", ""], list(self.t["sign"]) + ["", ""], hanging=True))
        self.frame(pier, -1, Y1 + 1, "front", self.t["ware"])

    def ledge_goods(self):
        goods = self.t["ledge"]
        for i, a in enumerate(self.window_cols()):
            g = goods[i % len(goods)]
            if g.startswith("frame:"):
                self.frame(a, -1, Y1 + 1, "up", g[6:], rotation=i)
            elif g.startswith("flower_pot:"):
                self.put(a, -1, Y1 + 1, block(g[11:]))
            elif g.startswith("candle:"):
                self.put(a, -1, Y1 + 1, candle(g[7:], 3))
            elif g == "cake":
                self.put(a, -1, Y1 + 1, block("cake", bites=0))
            elif g == "decorated_pot":
                self.put(a, -1, Y1 + 1, block("decorated_pot", facing=self.front, cracked=False, waterlogged=False))
            elif g in ("pumpkin", "melon", "hay_block") or g.endswith("_wool"):
                self.put(a, -1, Y1 + 1, block(g, axis="y") if g == "hay_block" else block(g))
            else:
                self.put(a, -1, Y1 + 1, block(g))

    # -- 上の階の窓（通りの側は花箱）と旗
    def upper_windows(self):
        F, bf = self.F, self.bf
        cols = [2, 3] if F <= 6 else ([2, 3, 4] if F == 7 else [1, 2, 5, 6])
        for fy in self.floors[1:]:
            for a in cols:
                for y in (fy + 2, fy + 3):
                    self.put(a, bf, y, pane())
                self.put(a, bf - 1, fy + 1, leaves("flowering_azalea_leaves" if rng.random() < 0.6 else "azalea_leaves"))
                self.put(a, bf - 2, fy + 1, trapdoor(pal.frame, self.front, "bottom", True))
            # 裏の窓（花箱なし）。煙突のある店は、煙突（a=2）をよける
            back = (2, 3) if F <= 7 else (2, 3, 5)
            if self.chimney:
                back = (3, 4)
            for a in back:
                for y in (fy + 2, fy + 3):
                    self.put(a, 10, y, pane())
        color = self.t["color"]
        self.put(0, bf - 1, Y2 + 3, block(color + "_wall_banner", facing=self.front),
                 banner_nbt([("mr", "white"), ("bo", "white")]))

    # -- 裏と横：裏口と踏み段、裏の窓、荷の樽。外に出ている横の壁には窓（と角の店は横の入口）
    SIDE_OPEN = 3       # 横の壁の、1階の入口（角の店）か窓の b。b=1..2 は売り場の棚、b=3 はカウンターの門の前の通り道

    def back_and_sides(self):
        F = self.F
        self.put(1, 10, Y1 + 1, door("spruce", self.back, False))
        self.put(1, 10, Y1 + 2, door("spruce", self.back, True))
        self.put(1, 11, Y1, stairs("stone_brick", self.front))
        bw = 3 if self.chimney else 2
        for y in (Y1 + 2, Y1 + 3):
            self.put(bw, 10, y, pane())
        for a, st in ((2, block("barrel", facing="up", open=False)), (3, block("hay_block", axis="y"))):
            if a < F - 1:
                self.put(a, 11, Y1, st)
        so = self.SIDE_OPEN
        for which in ("left", "right"):
            if not self.side_free(which):
                continue
            a = 0 if which == "left" else F - 1
            out = self.d(which)
            for b in (so, 7):
                for y in (Y1 + 1, Y1 + 2, Y1 + 3):
                    if self.kind == "D" and b == so and y < Y1 + 3:
                        continue
                    self.put(a, b, y, pane())
            if self.kind == "D":
                self.put(a, so, Y1 + 1, door(self.door_wood, out, False))
                self.put(a, so, Y1 + 2, door(self.door_wood, out, True))
                da, db = STEP[out]
                x, z = self.xz(a, so)
                self.seg.put(x + da, Y1, z + db, stairs("stone_brick", OPP[out]))
            for fy in self.floors[1:]:
                for b in (2, 3, 8) if self.kind in ("A", "D") else (2, 8):
                    for y in (fy + 2, fy + 3):
                        self.put(a, b, y, pane())
            # 角の店の横の壁に、2つ目の吊り看板（横の道から見えるように。入口の奥の側）
            if self.kind == "D":
                x, z = self.xz(a, so + 2)
                da, db = STEP[out]
                self.seg.put(x + da, Y1 + 3, z + db,
                             block("spruce_wall_hanging_sign", facing="east", waterlogged=False),
                             sign_nbt(list(self.t["sign"]) + ["", ""], list(self.t["sign"]) + ["", ""], hanging=True))

    # -- 屋根
    def footprint(self, x, z):
        """世界の (x, z) が、この店の外壁の内側（壁を含む。2階の張り出しも）か。返すのは (a, b) か None。"""
        if self.side == "W":
            b, a = -5 - x, self.z1 - z
        else:
            b, a = x - 5, z - self.z0
        if self.mirror:
            a = self.F - 1 - a
        if 0 <= a < self.F and self.bf <= b <= 10:
            return a, b
        return None

    def chimney_cell(self, x, z):
        return self.chimney and self.footprint(x, z) == (2, 10)

    def roof_skip(self, me):
        seg = self.seg

        def skip(sx, y, sz):
            x, z = sx + XMIN, sz + seg.z0
            if self.chimney_cell(x, z):
                return True
            ab = self.footprint(x, z)
            if ab is not None:
                a, b = ab
                if y < self.attic:
                    return True
                if y == self.attic and 0 < a < self.F - 1 and self.bf < b < 10:
                    return True
            return any(o.inside(sx, y, sz) for o in self.roofs if o is not me)
        return skip

    def roof(self):
        seg = self.seg
        xa, za = self.xz(0, self.bf)
        xb, zb = self.xz(self.F - 1, 10)
        X0, X1 = min(xa, xb) - XMIN, max(xa, xb) - XMIN
        Z0, Z1 = min(za, zb) - seg.z0, max(za, zb) - seg.z0
        if self.kind in ("A", "D"):
            ends = []
            if "north" in self.free:
                Z0 -= 1
                ends.append("start")
            if "south" in self.free:
                Z1 += 1
                ends.append("end")
            r = Roof(seg.w, pal, X0, X1, Z0, Z1, self.eave, ridge="z", pitch=1, side_overhang=1, end_overhang=0,
                     ends=tuple(ends), ridge_cap=True, kick=False)
            self.roofs = [r]
            r.build(skip=self.roof_skip(r))
            posts = tuple(self.xz(0, b)[0] - XMIN for b in (4, 7))
            for z in (self.z0, self.z1):
                gable_wall(seg.w, pal, r, z - seg.z0, posts=posts, beam_rows=(self.eave + 3,))
            # 外に出ている妻に、屋根裏の窓
            for which in ("left", "right"):
                if self.side_free(which):
                    a = 0 if which == "left" else self.F - 1
                    for b in (4, 5):
                        for y in (self.eave + 2,):
                            self.put(a, b, y, pane())
        else:
            r = Roof(seg.w, pal, X0, X1, Z0, Z1, self.eave, ridge="x", pitch=2, side_overhang=0, end_overhang=1,
                     ends=("start", "end"), ridge_cap=True, kick=False)
            self.roofs = [r]
            r.build(skip=self.roof_skip(r))
            mid = tuple(self.xz(a, 0)[1] - seg.z0 for a in ((self.F - 1) // 2, self.F // 2))
            for b in (self.bf, 10):
                x = self.xz(0, b)[0] - XMIN
                rows = gable_wall(seg.w, pal, r, x, posts=(), beam_rows=())
                # 妻の真ん中に屋根裏の窓（正面だけ）
                if b == self.bf:
                    for (y, lo, hi) in rows:
                        if y in (self.eave + 2, self.eave + 3):
                            for zc in mid:
                                if lo <= zc <= hi:
                                    seg.w.put(x, y, zc, pane())

    def build_chimney(self):
        top = max(r.ridge_y for r in self.roofs) + 3
        x, z = self.xz(2, 10)
        for y in range(Y1, top):
            self.seg.put(x, y, z, block("stone_bricks") if rng.random() < 0.85 else block("cracked_stone_bricks"))
        self.seg.put(x, top, z, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))

    # -- 中
    def stairs_up(self):
        """1階 → 2階の階段（右の2列、カウンターの後ろから裏へ上る）。2階の床に口をあけ、口の前に手すり。"""
        for k in range(5):
            b, y = 5 + k, Y1 + 1 + k
            for a in self.stair:
                self.put(a, b, y, stairs("spruce", self.back))
                for yy in range(Y1 + 1, y):
                    self.put(a, b, yy, block("spruce_planks"))
        for a in self.stair:
            for b in range(5, 9):
                self.put(a, b, Y2, AIR)
            if self.kind != "C":
                self.put(a, 4, Y2 + 1, fence("spruce"))
        if self.kind == "C":
            # 2階 → 3階：右の2列、正面の側から裏へ上る（1階 → 2階の口の手前）
            b0 = self.bf + 1
            for k in range(5):
                b, y = b0 + k, Y2 + 1 + k
                for a in self.stair:
                    self.put(a, b, y, stairs("spruce", self.back))
                    for yy in range(Y2 + 1, y):
                        self.put(a, b, yy, block("spruce_planks"))
            for a in self.stair:
                for b in range(b0, b0 + 4):
                    self.put(a, b, Y3, AIR)

    def counter(self):
        """カウンター（b=4）：皮をはいたダークオークの丸太に、ダークオークのハーフの天板（高さ1.5で、跳び越えられない）。
        左の端は柵の門（店の人の通り口）。"""
        for a in range(2, self.F - 1):
            self.put(a, 4, Y1 + 1, log("dark_oak", "z", stripped=True))
            self.put(a, 4, Y1 + 2, slab("dark_oak", "bottom"))
        self.put(1, 4, Y1 + 1, block("spruce_fence_gate", facing=self.front, open=False, powered=False, in_wall=False))

    def shelf_rows(self, a):
        """売り場の棚の b。左の列は b=1..2（b=3 はカウンターの門への通り道）、右の列は b=2..3（b=1 は入口の前）。
        横の壁が外に出ていて、その b に入口か窓があるときは、そこをあける。"""
        rows = (1, 2) if a == 1 else (2, 3)
        side = "left" if a == 1 else "right"
        if self.side_free(side):
            rows = tuple(b for b in rows if b != self.SIDE_OPEN)
        if self.chamfer and a == 1:
            rows = tuple(b for b in rows if b > 1)          # 角切りの柱 (1, 1) をよける
        return rows

    def cabinet(self, a, items, wall_items):
        """2段の樽の戸棚（高さ2。跳び乗れないので、カウンターへの踏み台にならない）。
        樽のふたは通路の側に向け、そこに品の額縁（段ごとに1つ）、上の横の壁にも品の額縁。"""
        toward = "right" if a == 1 else "left"
        ai = a + 1 if a == 1 else a - 1
        k = 0
        for i, b in enumerate(self.shelf_rows(a)):
            for y in (Y1 + 1, Y1 + 2):
                self.put(a, b, y, block("barrel", facing=self.d(toward), open=False))
                self.frame(ai, b, y, toward, items[k % len(items)])
                k += 1
            if wall_items:
                self.frame(a, b, Y1 + 3, toward, wall_items[i % len(wall_items)])

    def shelves(self, cols):
        """売り場の棚：2段の樽の戸棚（左右の列）。品は店の品を順に。"""
        items = list(self.t["shelf"])
        for n, a in enumerate(cols):
            rot = items[n * 2:] + items[:n * 2]
            self.cabinet(a, rot, rot[4:] + rot[:4])

    def back_room(self):
        """奥の仕事場：階段の左の列に、店の道具（左の端の列は裏口への通り道）。"""
        ac = self.stair[0] - 1
        work = self.t["work"]
        for i, b in enumerate((7, 8, 9)):
            name = work[i % len(work)]
            if name in ("smoker", "loom", "lectern", "grindstone", "cartography_table"):
                props = {"facing": self.front}
                if name == "smoker":
                    props.update(lit=True)
                if name == "grindstone":
                    props.update(face="floor")
                if name == "lectern":
                    props.update(has_book=False, powered=False)
                self.put(ac, b, Y1 + 1, block(name, **props))
            elif name == "barrel":
                self.put(ac, b, Y1 + 1, block("barrel", facing="up", open=False))
            elif name == "chest":
                self.put(ac, b, Y1 + 1, block("chest", facing=self.front, type="single", waterlogged=False))
            elif name == "brewing_stand":
                self.put(ac, b, Y1 + 1, block("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
            elif name == "composter":
                self.put(ac, b, Y1 + 1, block("composter", level=4))
            elif name == "hay_block":
                self.put(ac, b, Y1 + 1, block("hay_block", axis="y"))
            elif name == "decorated_pot":
                self.put(ac, b, Y1 + 1, block("decorated_pot", facing=self.front, cracked=False, waterlogged=False))
            elif name == "cauldron":
                self.put(ac, b, Y1 + 1, block("water_cauldron", level=3))
            elif name.endswith("_leaves"):
                self.put(ac, b, Y1 + 1, leaves(name))
            else:
                self.put(ac, b, Y1 + 1, block(name))

    def lights(self):
        F = self.F
        self.put(2, 2, Y2 - 1, lantern(True))
        if F >= 7:
            self.put(F - 3, 2, Y2 - 1, lantern(True))
        self.put(1, 7, Y2 - 1, lantern(True))
        for fy in self.floors[1:]:
            top = self.ceiling(fy) - 1                     # 天井から吊るす
            self.put(2, 2, top, lantern(True))
            self.put(1, 7, top, lantern(True))
        if self.get(2, 5, self.attic + 1) is None:          # 屋根裏の灯り（屋根の中の詰め物の所には置かない）
            self.put(2, 5, self.attic + 1, lantern(False))

    def upstairs_room(self):
        """2階（3階建ては3階）の住まい：寝台・箱・机と椅子・敷物。2階建ては2階、3階建ては2階が仕事部屋で3階が寝室。"""
        bed = self.t["bed"]
        sleep = self.floors[-1]
        self.put(1, 8, sleep + 1, block(bed + "_bed", facing=self.back, part="foot", occupied=False))
        self.put(1, 9, sleep + 1, block(bed + "_bed", facing=self.back, part="head", occupied=False))
        self.put(1, 7, sleep + 1, block("chest", facing=self.right, type="single", waterlogged=False))
        for fy in self.floors[1:]:
            b0 = self.bf + 1
            self.put(2, b0 + 2, fy + 1, fence("spruce"))
            self.put(2, b0 + 2, fy + 2, block("white_carpet"))
            self.put(1, b0 + 2, fy + 1, stairs("spruce", self.left))
            for a in range(1, self.stair[0]):
                for b in range(b0, b0 + 2):
                    if self.get(a, b, fy + 1) is None:
                        self.put(a, b, fy + 1, block(bed + "_carpet"))
            if fy != sleep:
                self.put(1, 8, fy + 1, block("barrel", facing="up", open=False))
                self.put(1, 9, fy + 1, block("crafting_table"))

    def interior(self):
        self.stairs_up()
        self.counter()
        cols = [1] if self.F <= 6 else [1, self.F - 2]
        self.shelves(cols)
        self.back_room()
        self.lights()
        self.upstairs_room()


# ---------------------------------------------------------------- 雑貨屋（マルタ・ポム）と料理屋（ペトラ・ポム）
class GeneralStore(Shop):
    """マルタ・ポムの雑貨屋（中央広場の東の角）。消耗品を売り、素材を買い取る（Phase 0-A）。
    売り場の右の端に「買い取り」の台、2階はリリアも食べに来る食卓【補完】。"""

    def shelves(self, cols):
        """左の列は売る品（消耗品）。右の列（入口の横、カウンターの右の端）は「素材の買い取り」の台：
        戸棚に素材の見本、上の壁に札。"""
        self.cabinet(1, list(self.t["shelf"]), ["lead", "string"])
        a = self.F - 2
        self.cabinet(a, ["raw_iron", "raw_copper", "coal", "flint"], [])
        b_sign, b_frame = self.shelf_rows(a)[-1], self.shelf_rows(a)[0]
        self.put(a, b_sign, Y1 + 3, block("spruce_wall_sign", facing=self.d("left"), waterlogged=False),
                 sign_nbt(["", "素材の", "買い取り", ""]))
        self.frame(a, b_frame, Y1 + 3, "left", "bone")

    def interior(self):
        super().interior()
        # 2階：食卓（リリアも食べに来る）と椅子3つ、窯（燻製器）
        b0 = self.bf + 1
        for a in (2, 3):
            self.put(a, b0 + 2, Y2 + 1, fence("spruce"))
            self.put(a, b0 + 2, Y2 + 2, block("white_carpet"))
        self.put(4, b0 + 2, Y2 + 1, stairs("spruce", self.right))
        self.put(2, b0 + 3, Y2 + 1, stairs("spruce", self.back))
        self.put(3, b0 + 1, Y2 + 1, stairs("spruce", self.front))
        self.put(self.F - 2, b0, Y2 + 1, block("smoker", facing=self.back, lit=True))


class Restaurant(Shop):
    """ペトラ・ポムの料理屋（中央広場の西の角）。寄付（共同事業の3番目、9日目ごろ）で開く（Phase 0-A）。
    is_open=False は開く前（窓に板、入口に「開店準備中」の札、中は荷）、True は開いたあと（卓・台所・広場の側の席）。"""

    def __init__(self, *args, is_open=False, **kw):
        super().__init__(*args, **kw)
        self.is_open = is_open

    def front_ground_floor(self):
        super().front_ground_floor()
        if self.is_open:
            return
        a0, a1 = self.win
        for a in range(a0, a1 + 1):
            for y in range(Y1 + 1, Y1 + 4):
                self.put(a, 0, y, block("oak_planks"))            # 窓に板（新しい板）
            self.put(a, -1, Y1 + 1, AIR)
        doors = [self.door_a, self.door_a + 1] if self.double else [self.door_a]
        for a in doors:                                            # 入口にも板（開くまでは裏口だけ）
            self.put(a, 0, Y1 + 1, block("oak_planks"))
            self.put(a, 0, Y1 + 2, block("oak_planks"))
        # 吊り看板は「開店準備中」、扉の横に寄付の知らせ
        pier = self.door_a - 1
        self.put(pier, -1, Y1 + 3, block("spruce_wall_hanging_sign", facing="south", waterlogged=False),
                 sign_nbt(["料理屋", "開店準備中", "", ""], ["料理屋", "開店準備中", "", ""], hanging=True))

    def ledge_goods(self):
        if self.is_open:
            super().ledge_goods()

    def back_and_sides(self):
        super().back_and_sides()
        if self.is_open:
            return
        # 開く前：横の入口と窓にも板。広場の側の入口に寄付の知らせ
        so = self.SIDE_OPEN
        for which in ("left", "right"):
            if not self.side_free(which):
                continue
            a = 0 if which == "left" else self.F - 1
            for b in (so, 7):
                for y in (Y1 + 1, Y1 + 2, Y1 + 3):
                    st = self.get(a, b, y)
                    if st is not None and (st[0] == "glass_pane" or st[0].endswith("_door")):
                        self.put(a, b, y, block("oak_planks"))
        a = self.F - 1 if self.side_free("right") else 0
        x, z = self.xz(a, so)
        out = self.d("right") if a == self.F - 1 else self.d("left")
        da, db = STEP[out]
        self.seg.put(x + da, Y1 + 2, z + db, block("spruce_wall_sign", facing=out, waterlogged=False),
                     sign_nbt(["料理屋", "開店準備中", "共同事業（寄付）で", "ひらきます"]))

    def interior(self):
        F = self.F
        self.stairs_up()
        self.lights()
        self.upstairs_room()
        if not self.is_open:
            # 開く前：空の店に、改装の荷（板・樽・干し草）。裏口から階段までの通り道はあける
            for (a, b) in ((1, 1), (1, 2), (2, 1), (3, 7)):
                self.put(a, b, Y1 + 1, block("barrel", facing="up", open=False))
            self.put(1, 1, Y1 + 2, block("barrel", facing="up", open=False))
            self.put(2, 2, Y1 + 1, block("stripped_spruce_wood", axis="x"))
            self.put(2, 1, Y1 + 2, block("spruce_slab", type="bottom", waterlogged=False))
            self.put(3, 6, Y1 + 1, block("hay_block", axis="x"))
            return
        # 開いたあと：前が食堂（卓2つと椅子）、カウンター（ほかの店と同じ b=4。階段は後ろから横に上る）、
        # 奥が台所（燻製器・焚き火の鍋・水の大釜・樽）。品書きは食堂の横の壁の額縁
        self.counter()
        # 椅子は卓の外の側と前の側（カウンターの側 b=3 には置かない：椅子と卓が、カウンターへの踏み台にならないように）
        for (a, b), sides in (((2, 2), (("left", (1, 2)), ("front", (2, 1)))),
                              ((4, 2), (("right", (5, 2)), ("front", (4, 1))))):
            self.put(a, b, Y1 + 1, fence("spruce"))
            self.put(a, b, Y1 + 2, block("white_carpet"))
            for back, (ca, cb) in sides:
                self.put(ca, cb, Y1 + 1, stairs("spruce", self.d(back)))     # 椅子：背もたれは卓と反対の側
        ac = self.stair[0] - 1
        self.put(ac, 7, Y1 + 1, block("smoker", facing=self.front, lit=True))
        self.put(ac, 8, Y1 + 1, block("campfire", lit=True, facing=self.front, signal_fire=False, waterlogged=False))
        self.put(ac, 9, Y1 + 1, block("water_cauldron", level=3))
        self.put(2, 9, Y1 + 1, block("barrel", facing="up", open=False))
        self.frame(1, 6, Y1 + 2, "right", "cod")                           # 台所の壁：その日の魚
        self.frame(1, 8, Y1 + 2, "right", "salmon")
        for i, item in enumerate(("cooked_cod", "baked_potato")):           # 品書き（食堂の左の壁）
            self.frame(1, 1 + i, Y1 + 3, "right", item)
        for i, item in enumerate(("cooked_salmon", "bread")):               # 品書き（食堂の右の壁）
            self.frame(self.F - 2, 1 + i, Y1 + 3, "left", item)


# ---------------------------------------------------------------- 通り・路地・裏通り・灯り・露店・渡り廊下
def street(seg, z0, z1):
    for z in range(z0, z1 + 1):
        for x in range(-4, 5):
            seg.put(x, 0, z, ancient_paving() if -2 <= x <= 2 else band_paving())


def lane(seg, x0, x1, z0, z1):
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            seg.put(x, 0, z, alley_paving())


def lamp(seg, x, z):
    sx, sz = seg.sx(x, z)
    crystal_lamp(seg.w, sx, 1, sz)


def bench(seg, x, z, facing):
    """腰かけ（トウヒの階段2つと、両はしのひじ掛けの開いたトラップドア）。facing は座る人が向く方。
    席は facing と直角に2つ並ぶ：north・south なら (x, z)・(x+1, z)、east・west なら (x, z)・(x, z+1)。"""
    if facing in ("north", "south"):
        seats, arms = [(x, z), (x + 1, z)], [((x - 1, z), "west"), ((x + 2, z), "east")]
    else:
        seats, arms = [(x, z), (x, z + 1)], [((x, z - 1), "north"), ((x, z + 2), "south")]
    for (cx, cz) in seats:
        seg.put(cx, 1, cz, stairs("spruce", OPP[facing]))
    for (cx, cz), f in arms:
        seg.put(cx, 1, cz, trapdoor("spruce", f, "bottom", True))


def flower_bed(seg, x0, x1, z0, z1, face=None):
    """花壇：石畳を草のブロックに替え、花を植える（人は花の上を歩ける）。face の側には、縁の開いたトラップドアの板。"""
    flowers = ["poppy", "cornflower", "oxeye_daisy", "allium", "dandelion"]
    i = 0
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            seg.put(x, 0, z, block("grass_block", snowy=False))
            seg.put(x, 1, z, block(flowers[i % len(flowers)]))
            i += 1
            if face is not None:
                da, db = STEP[face]
                nx, nz = x + da, z + db
                if not (x0 <= nx <= x1 and z0 <= nz <= z1):
                    seg.put(nx, 1, nz, trapdoor("spruce", face, "bottom", True))


def stall(seg, x0, z0, awning, goods, facing="east"):
    """小さな露店（3×3、高さ4）：四すみの柵の柱、前に台、上に日よけの布（2色のしま）。"""
    xs, zs = range(x0, x0 + 3), range(z0, z0 + 3)
    for x in (x0, x0 + 2):
        for z in (z0, z0 + 2):
            for y in (1, 2, 3):
                seg.put(x, y, z, fence("spruce"))
    for x in xs:
        for i, z in enumerate(zs):
            seg.put(x, 4, z, slab("spruce", "bottom"))
            seg.put(x, 5, z, block((awning[0] if (x + z) % 2 else awning[1]) + "_carpet"))
    fx = x0 + 2 if facing == "east" else x0
    for i, z in enumerate(zs):
        if z in (z0, z0 + 2):
            continue
        seg.put(fx, 1, z, slab("spruce", "top"))
    for i, z in enumerate(zs):
        seg.put(fx, 2, z, goods[i % len(goods)]) if seg.get(fx, 2, z) is None else None
    bx = x0 if facing == "east" else x0 + 2
    seg.put(bx, 1, z0 + 1, block("barrel", facing="up", open=False))


def alley_garland(seg, x, z0, z1, y=8):
    """路地の上に渡した横木と、吊るした灯り（2階どうしの間。下の空きは5以上）。"""
    for z in range(z0, z1 + 1):
        seg.put(x, y, z, fence("dark_oak"))
    zm = (z0 + z1) // 2
    seg.put(x, y - 1, zm, block("chain", axis="y", waterlogged=False))
    seg.put(x, y - 2, zm, lantern(True))


def signpost(seg, x, z, signs):
    """案内板：柵の柱（3段）に、行き先の向きのトウヒの看板。signs は [(向き, [行1, 行2, 行3])]。"""
    for y in (1, 2, 3):
        seg.put(x, y, z, fence("spruce"))
    seg.put(x, 4, z, slab("dark_oak", "bottom"))
    for facing, lines in signs:
        da, db = STEP[facing]
        seg.put(x + da, 2, z + db, block("spruce_wall_sign", facing=facing, waterlogged=False),
                sign_nbt(lines + [""] * (4 - len(lines))))


def bridge_room(seg, z0, z1, depth=4):
    """フリーマーケットへの通り抜け（z0..z1）の上の渡り廊下：2階の高さの小さな部屋（床 +6、下の空きは5）。
    両隣の店の2階から扉で入る。正面に妻、旗（フリーマーケットの黄と橙のしま）、下に門の吊り看板。"""
    xf, xb = -4, -4 - depth
    for z in range(z0, z1 + 1):
        for x in range(xb, xf + 1):
            seg.put(x, Y2, z, block("spruce_planks"))
        for x in (xf, xb):
            seg.put(x, Y2, z, beam("z"))
            seg.put(x, Y2 + 4, z, beam("z"))
            for y in range(Y2 + 1, Y2 + 4):
                seg.put(x, y, z, POST if z in (z0, z1) else block(pal.plaster))
    for z in (z0 + 1, z1 - 1):
        for y in (Y2 + 2, Y2 + 3):
            seg.put(xf, y, z, pane())
            seg.put(xb, y, z, pane())
    # 持ち送り（両はしの店の壁から）とアーチの肩
    for z, f in ((z0, "north"), (z1, "south")):
        seg.put(xf, Y2 - 1, z, stairs(pal.frame, OPP[f], "top"))
        seg.put(-5, Y2 - 1, z, stairs("stone_brick", OPP[f], "top"))
    sx0, sx1 = xb - XMIN, xf - XMIN
    r = Roof(seg.w, pal, sx0, sx1, z0 - seg.z0, z1 - seg.z0, Y2 + 4, ridge="x", pitch=2, side_overhang=0,
             end_overhang=1, ends=("start", "end"), ridge_cap=True, kick=False)

    def skip(sx, y, sz):
        x, z = sx + XMIN, sz + seg.z0
        return xb <= x <= xf and z0 <= z <= z1 and y <= Y2 + 4
    r.build(skip=skip)
    for x in (xf, xb):
        gable_wall(seg.w, pal, r, x - XMIN)
    for z in range(z0 + 1, z1):
        for x in range(xb + 1, xf):
            seg.put(x, Y2 + 5, z, block("spruce_planks"))
    seg.put(xf - 2, Y2 + 4, (z0 + z1) // 2, lantern(True))
    seg.put(xf + 1, Y2 + 3, z0, block("yellow_wall_banner", facing="east"),
            banner_nbt([("ss", "orange"), ("bo", "white")]))
    seg.put(xf + 1, Y2 + 3, z1, block("orange_wall_banner", facing="east"),
            banner_nbt([("ss", "yellow"), ("bo", "white")]))
    zm = (z0 + z1) // 2
    seg.put(-5, Y2 - 1, zm, block("spruce_hanging_sign", rotation=4, attached=False, waterlogged=False),
            sign_nbt(["フリー", "マーケット", "", ""], ["フリー", "マーケット", "", ""], hanging=True))
    seg.put(-5, Y2 - 1, zm + 1, block("spruce_hanging_sign", rotation=4, attached=False, waterlogged=False),
            sign_nbt(["→ 露店の", "広場", "", ""], ["← 市場通り", "", "", ""], hanging=True))
    return r


def connect_bridge(shop, which):
    """渡り廊下の側の、店の2階の横の壁に扉。"""
    a = 0 if which == "left" else shop.F - 1
    out = shop.d(which)
    shop.put(a, 1, Y2 + 1, door("spruce", out, False))
    shop.put(a, 1, Y2 + 2, door("spruce", out, True))


# ---------------------------------------------------------------- 区画の組み立て
def build_north(restaurant_open=False):
    seg = Seg("market_north", 22, 53)
    street(seg, 22, 52)
    lane(seg, -18, -16, 22, 52)
    lane(seg, 16, 18, 22, 52)
    # 中央広場の側の帯（料理屋と雑貨屋の前。広場の古代の石畳につながる）
    for z in (22, 23, 24):
        for x in list(range(-15, -4)) + list(range(5, 16)):
            seg.put(x, 0, z, ancient_paving())
    lane(seg, -15, -5, 32, 34)                      # 路地（料理屋の南。釣り堀への近道）
    lane(seg, 5, 15, 38, 40)                        # 路地（道具屋の南）
    shops = [
        Restaurant(seg, "W1", "W", 25, 31, "D", "restaurant", free=("north", "south"), chimney=True,
                   is_open=restaurant_open),
        Shop(seg, "W2", "W", 35, 40, "C", "bakery", free=("north",), chimney=True),
        Shop(seg, "W3", "W", 41, 46, "A", "greengrocer"),
        Shop(seg, "W4", "W", 47, 52, "D", "apothecary", free=("south",), chamfer=True),
        GeneralStore(seg, "E1", "E", 25, 31, "D", "general", free=("north",), door_wood="dark_oak"),
        Shop(seg, "E2", "E", 32, 37, "B", "tools", free=("south",)),
        Shop(seg, "E3", "E", 41, 46, "A", "lights", free=("north",)),
        Shop(seg, "E4", "E", 47, 52, "D", "cloth", free=("south",), mirror=True, chamfer=True),
    ]
    for sh in shops:
        sh.build()
    # 結晶灯（端の帯の内側の列。左右互い違い、入口の前は空ける）
    for (x, z) in ((3, 27), (-3, 33), (3, 39), (-3, 45), (3, 50)):
        lamp(seg, x, z)
    alley_garland(seg, -10, 32, 34)
    alley_garland(seg, 10, 38, 40)
    # 広場の側の帯：雑貨屋の前の腰かけ、料理屋の前（開いたあとは席）
    bench(seg, 11, 23, "north")                      # 中央広場（大結晶）を向く
    flower_bed(seg, 10, 14, 24, 24)                  # 腰かけの後ろ、雑貨屋の横の窓の下
    if restaurant_open:
        restaurant_terrace(seg)
    # 裏通りと広場の側の帯の角：再建のランタン灯（料理屋が開く前は、窓に板で暗い）
    sx, sz = seg.sx(-16, 22)
    lamp_post(seg.w, sx, 1, sz, arms=("east",))
    # 環状路の1列目（z 53）は、角の店の踏み段・看板・屋根の出のほかは置いても消さない
    for x in range(XMIN, XMAX + 1):
        for y in range(H):
            if seg.get(x, y, 53) is None:
                seg.put(x, y, 53, block("structure_void"))
    return seg, shops


def restaurant_terrace(seg):
    """料理屋が開いたあと：広場の側の席（卓2つと腰かけ、日よけ）と、看板の下の品。"""
    for x in (-13, -9):
        seg.put(x, 1, 23, fence("spruce"))
        seg.put(x, 2, 23, block("white_carpet"))
        seg.put(x - 1, 1, 23, stairs("spruce", "west"))
        seg.put(x + 1, 1, 23, stairs("spruce", "east"))
    for x in range(-14, -7):
        if seg.get(x, 4, 24) is not None:
            continue                    # 横の吊り看板（料理屋）の所は、日よけを切る
        seg.put(x, 4, 24, trapdoor("dark_oak", "south", "top", False))
        seg.put(x, 5, 24, block("orange_carpet" if x % 2 else "white_carpet"))
    for x in (-15, -7):
        seg.put(x, 1, 22, block("potted_red_tulip") if x == -15 else block("potted_dandelion"))


def build_south():
    seg = Seg("market_south", 60, 103)
    street(seg, 60, 103)
    lane(seg, -18, -16, 60, 103)
    lane(seg, 16, 18, 60, 67)
    for z in (60, 61):
        for x in list(range(-15, -4)) + list(range(5, 16)):
            seg.put(x, 0, z, band_paving())
    lane(seg, -15, -5, 75, 78)                      # フリーマーケットへの通り抜け
    shops = [
        Shop(seg, "W5", "W", 62, 67, "D", "butcher", free=("north",), chimney=True),
        Shop(seg, "W6", "W", 68, 74, "A", "books", free=("south",)),
        Shop(seg, "W7", "W", 79, 84, "B", "leather", free=("north",)),
        Shop(seg, "W8", "W", 85, 90, "A", "flowers"),
        Shop(seg, "W9", "W", 91, 96, "C", "hardware", free=("south",)),
        Shop(seg, "E5", "E", 62, 67, "D", "tea", free=("north", "south")),
    ]
    for sh in shops:
        sh.build()
    bridge_room(seg, 75, 78)
    connect_bridge(shops[1], "left")
    connect_bridge(shops[2], "right")
    # 南門の前の小広場（z 97..103）：露店2つ、腰かけ、再建のランタン灯
    for z in range(97, 104):
        for x in range(-15, -4):
            seg.put(x, 0, z, ancient_paving() if z > 97 else band_paving())
    flower_bed(seg, -13, -11, 97, 97, face="south")   # 金物屋の横の窓の下
    stall(seg, -14, 99, ("yellow", "white"), [block("melon"), block("pumpkin"), block("hay_block", axis="y")])
    stall(seg, -10, 99, ("light_blue", "white"), [block("barrel", facing="up", open=False), block("cake", bites=0),
                                                   block("flower_pot")])
    sx, sz = seg.sx(-6, 98)
    lamp_post(seg.w, sx, 1, sz, arms=("north", "east"))
    # 裏通りの角の、裏の壁の角から出した壁のランタン（環状路の側・フリーマーケットへの通り抜け・南門の小広場）
    for (x, z, out) in ((-15, 62, "north"), (-15, 79, "north"), (-15, 96, "south")):
        sx, sz = seg.sx(x, z)
        wall_lantern(seg.w, sx, Y2 - 1, sz, out)
    # 結晶灯：片側ごとに12、両側で互い違い（6ずつ）。入口の前の3マスはあける
    for (x, z) in ((3, 61), (-3, 66), (3, 72), (-3, 78), (3, 84), (-3, 90), (3, 96), (-3, 102)):
        lamp(seg, x, z)
    signpost(seg, 4, 60, [("south", ["↓ 南門", "→ 環状路（東）", ""]), ("north", ["↑ 中央広場", "← 環状路（西）", ""])])
    # カジノ（PHASE 10）とその前の道、南門の前の東の区画は、置いても消さない
    for z in range(68, 104):
        for x in range(5, XMAX + 1):
            for y in range(H):
                if seg.get(x, y, z) is None:
                    seg.put(x, y, z, block("structure_void"))
    for z in range(97, 104):
        for x in range(5, XMAX + 1):
            for y in range(H):
                seg.put(x, y, z, block("structure_void"))
    return seg, shops


def finalize(seg):
    seg.w.finalize()
    return seg


def restaurant_overlay():
    """料理屋が開いたあとの形だけを切り出した構造物（x -18..-4、z 22..34）。北の構造物の (0, 0, 0) から (0, 0, 0) に重ねる。"""
    seg_open, _ = build_north(restaurant_open=True)
    finalize(seg_open)
    ov = World(15, H, 13)
    for (x, y, z), st in seg_open.w.blocks.items():
        if x < 15 and z < 13:
            ov.blocks[(x, y, z)] = st
            if (x, y, z) in seg_open.w.nbt:
                ov.nbt[(x, y, z)] = seg_open.w.nbt[(x, y, z)]
    ov.entities = [e for e in seg_open.w.entities if e.cell[0] < 15 and e.cell[2] < 13]
    return ov, seg_open


north, north_shops = build_north()
finalize(north)
south, south_shops = build_south()
finalize(south)


# ---------------------------------------------------------------- 見本の絵（構造物の中の座標。y=0 が地面）。CI の写真（MAP_VIEWS）と同じ
# 北の構造物：x 0..36（世界の x -18..18）、z 0..31（世界の z 22..53。z 53 は環状路の1列目）。南：z 0..43（世界の z 60..103）
VIEWS_NORTH = {
    "north_street": ((18.5, 9.0, 38.0), (18.5, 4.0, 0.0)),       # 環状路から北へ：通りの両側の店と、その先の中央広場
    "north_west": ((22.5, 3.0, 22.0), (10.0, 6.0, 9.0)),         # 通りの東の端から西の店の並び（八百屋・パン屋・料理屋）
    "north_east": ((17.5, 4.0, 26.0), (27.0, 6.0, 8.0)),         # 通りの真ん中から東の店の並び（灯りの店・道具屋・雑貨屋）
    "north_plaza": ((18.5, 5.0, -14.0), (18.5, 5.0, 10.0)),      # 中央広場から南へ：料理屋と雑貨屋の角、市場通りの入口
    "north_aerial": ((38.0, 36.0, 48.0), (16.0, 2.0, 14.0)),     # 上から（南東）：屋根の向きと高さのリズム、裏通り
    "north_back": ((-6.0, 12.0, 38.0), (6.0, 7.0, 16.0)),        # 西の裏通りから：裏口・煙突・荷
}
VIEWS_SOUTH = {
    "south_street": ((18.5, 9.0, -8.0), (18.5, 3.0, 30.0)),      # 環状路から南へ：西の店の並びと、門の前の小広場
    "south_west": ((23.0, 4.0, 16.5), (10.0, 6.5, 16.5)),        # 通りの東の端から：本屋・渡り廊下・革屋
    "south_arch": ((19.0, 2.6, 17.0), (8.0, 3.5, 17.0)),         # 通りからフリーマーケットへの通り抜け（渡り廊下の下）
    "south_gate": ((9.0, 6.5, 50.0), (8.0, 2.5, 38.0)),          # 南門の側から小広場：露店2つとランタン灯
    "south_aerial": ((36.0, 34.0, -6.0), (8.0, 2.0, 24.0)),      # 上から（北東）
    "south_corner": ((27.0, 5.0, -10.0), (16.0, 4.0, 4.0)),      # 環状路の交差点（北東）から：角の店2軒と案内板
}


def shop_point(shop, A, B, y):
    """店の中の座標（a・b を小数にしたもの。マス (a, b) の真ん中は (a+0.5, b+0.5)）を、構造物の中の座標にする。"""
    if shop.mirror:
        A = shop.F - A
    if shop.side == "W":
        x, z = -4 - B, shop.z1 + 1 - A
    else:
        x, z = 5 + B, shop.z0 + A
    return (x - XMIN, y, z - shop.seg.z0)


def shop_by_key(key, shops):
    return next(sh for sh in shops if sh.key == key)


def inside_views():
    """店の中の見本の絵：(区画, 目, 見る所)。売り場は正面の壁の内側の真ん中から、まっすぐ奥（カウンターと階段）を見る。"""
    eye_y = Y1 + 1 + 1.62
    views = {}
    for name, key, shops, seg_key in (("general", "E1", north_shops, "north"), ("bakery", "W2", north_shops, "north"),
                                      ("tools", "E2", north_shops, "north"), ("books", "W6", south_shops, "south")):
        sh = shop_by_key(key, shops)
        views[name] = (seg_key, shop_point(sh, sh.F / 2, 1.1, eye_y), shop_point(sh, sh.F / 2 - 0.5, 6.0, Y1 + 1.9))
    sh = shop_by_key("E1", north_shops)
    views["general_2f"] = ("north", shop_point(sh, 4.6, 6.4, Y2 + 1 + 1.62), shop_point(sh, 2.4, 2.0, Y2 + 1.3))
    views["bridge"] = ("south", (12.5, Y2 + 2.7, 15.2), (12.0, Y2 + 1.4, 18.9))
    return views


INSIDE = inside_views()


def cut_world(world, y_cut):
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut]
    return cw


def visible(world):
    """見本の絵のための写し：structure_void（置いても何もしない所）を除く。"""
    cw = World(world.w, world.h, world.d)
    cw.blocks = {p: st for p, st in world.blocks.items() if st[0] != "structure_void"}
    cw.nbt = dict(world.nbt)
    cw.entities = list(world.entities)
    return cw


def render_views(out_dir, width=960, height=540, fov=70, only=None, open_world=None):
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    worlds = {"north": visible(north.w), "south": visible(south.w)}
    if open_world is not None:
        open_world = visible(open_world)
    scenes = {k: Scene(v, ground_y=1) for k, v in worlds.items()}
    for key, views in (("north", VIEWS_NORTH), ("south", VIEWS_SOUTH)):
        for name, (eye, target) in views.items():
            if only and name not in only:
                continue
            scenes[key].render(eye=eye, target=target, width=width, height=height, fov=fov,
                               path=os.path.join(out_dir, "market_%s.png" % name))
            print("描きました:", name)
    for key, y_cut, cam in (("north", Y2, ((18.5, 70, 15.5), (18.5, 0, 15.4))),
                            ("south", Y2, ((18.5, 80, 22.0), (18.5, 0, 21.9)))):
        name = "cut_%s" % key
        if only and name not in only:
            continue
        Scene(cut_world(worlds[key], y_cut), ground_y=1).render(eye=cam[0], target=cam[1], width=width,
                                                                 height=height + 200, fov=40,
                                                                 path=os.path.join(out_dir, "market_%s.png" % name))
        print("描きました:", name)
    for name, (key, eye, target) in INSIDE.items():
        if only and ("in_" + name) not in only:
            continue
        scenes[key].render(eye=eye, target=target, width=width, height=height, fov=72,
                           path=os.path.join(out_dir, "market_in_%s.png" % name))
        print("描きました:", "in_" + name)
    # 料理屋：開く前と開いたあと（中央広場の側から）
    for name in ("restaurant_before", "restaurant_after"):
        if only and name not in only:
            continue
        world = worlds["north"] if name.endswith("before") else open_world
        if world is None:
            continue
        Scene(world, ground_y=1).render(eye=(15.0, 3.8, -6.0), target=(6.5, 5.5, 5.0), width=width, height=height,
                                        fov=70, path=os.path.join(out_dir, "market_%s.png" % name))
        print("描きました:", name)
    if open_world is not None and (not only or "in_restaurant" in only):
        sh = shop_by_key("W1", north_shops)
        Scene(open_world, ground_y=1).render(eye=shop_point(sh, sh.F / 2, 1.1, Y1 + 1 + 1.62),
                                             target=shop_point(sh, sh.F / 2 - 0.5, 6.0, Y1 + 1.9), width=width, height=height,
                                             fov=80, path=os.path.join(out_dir, "market_in_restaurant.png"))
        print("描きました: in_restaurant")


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
    render_dir = None
    only = None
    if "--render" in args:
        i = args.index("--render")
        render_dir = args[i + 1]
        del args[i:i + 2]
    if "--only" in args:
        i = args.index("--only")
        only = set(args[i + 1].split(","))
        del args[i:i + 2]
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    for seg in (north, south):
        path = os.path.join(out, seg.name + ".nbt")
        n = seg.w.save_structure(path)
        print("書き出しました:", path, "ブロック", len(seg.w.blocks), "種類", n, "もの", len(seg.w.entities),
              "大きさ", (seg.W, H, seg.D))
    ov, open_seg = restaurant_overlay()
    path = os.path.join(out, "market_restaurant_open.nbt")
    n = ov.save_structure(path)
    print("書き出しました:", path, "ブロック", len(ov.blocks), "種類", n, "もの", len(ov.entities), "大きさ", (15, H, 13))
    if render_dir:
        render_views(render_dir, only=only, open_world=open_seg.w)
