"""コネリアの街並み（Phase 5）：11の施設のまわりの、道・中央広場と大結晶・城壁と門・結界の柱・家・倉庫・灯り・木・飾り。

建築の文書 Tab 1 の都市のルール（③素材・④高さ・⑤道・⑥屋根・⑦看板と街灯と植栽・⑧ゾーニング）のとおりに、11の施設の
構造物（tools/mapgen/build_*.py）が置かれていない所を埋める。民間の家は中に入らないので、内装を作らない（11月21日のご指示）。
家の扉は手では開かない（amemaru:house_door_*）。家の中は空洞で、見えない灯り（minecraft:light）を1つ置き、暗くて敵が
湧ける所を作らない。

座標は世界の座標（大結晶が x 0・z 0。x が東、z が南）。y は道のブロックが 0（足もとが 1）。
街の箱（x −144〜135、z −184〜127）を 48×48 の区画に分けて、構造物ファイル town/t<i>_<j>.nbt（高さは y −7〜40）に書き出す。
地図を組むときは、地面を作った後、11の施設より先に置く（施設が上に重なる。CityCommands・check_city.py の PLACEMENTS）。

使い方:
  python3 tools/mapgen/build_city.py                    # Modの data/amemaru/structures/map/conelia/town/ を書き直す
  python3 tools/mapgen/build_city.py /tmp/out --render /tmp/out   # 別の所に書き出して、上から見た地図も描く
"""
import gzip
import io
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_city as cc  # noqa: E402
from mapgen.arch import Palette, Pyramid, Roof, gable_wall  # noqa: E402
from mapgen.fixtures import crystal_lamp, lamp_post  # noqa: E402
from mapgen.world import (DATA_VERSION, World, block, fence, lantern, log, pane, sign_nbt, slab, stairs,  # noqa: E402
                          trapdoor, wall)

import nbtlib  # noqa: E402
from nbtlib.tag import Compound, Int, List, String  # noqa: E402

X0, Z0 = -144, -184          # 街の箱の北西の角
XS, ZS = 288, 336            # 区画の数ぶん（6×7 区画）
Y0, H = -7, 48               # 構造物の底の高さと、高さ
TILE = 48
OUT = os.path.join(cc.SRC, "town")

R_PLAZA = 22.5
R_WALLROAD = 107.0
R_WALL_IN, R_WALL_OUT = 112.0, 116.0
WALL_TOP = 12                # 城壁の上（通路の床のブロック）
GATES = {"north": 0.0, "east": 90.0, "south": 180.0}     # 門の向き（北から時計回りの角度）
GATE_HALF = {"north": 5, "east": 4, "south": 4}           # 門の口の半分の幅（道の幅の半分）

pal = Palette(seed=2026, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = random.Random(1121)


# ================================================================ 材料（都市のルール③⑤）
def ancient():
    """古代の白い石畳：磨いた閃緑岩6・閃緑岩2・方解石1・灰色の石のつぎはぎ1"""
    r = rng.random()
    return block("polished_diorite" if r < 0.6 else "diorite" if r < 0.8 else "calcite" if r < 0.9 else
                 ("andesite" if rng.random() < 0.5 else "stone_bricks"))


def gray_stone():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで"""
    r = rng.random()
    return block("stone_bricks" if r < 0.82 else "cracked_stone_bricks" if r < 0.92 else "mossy_stone_bricks" if r < 0.97
                 else "andesite")


def band():
    """端の帯：石レンガと安山岩"""
    return block("stone_bricks" if rng.random() < 0.6 else "polished_andesite" if rng.random() < 0.6 else "andesite")


def alley_stone():
    """路地：丸石・砂利・土の道・粗い土"""
    r = rng.random()
    return block("cobblestone" if r < 0.45 else "gravel" if r < 0.6 else "dirt_path" if r < 0.85 else "coarse_dirt")


# ================================================================ 街の World（世界の座標で書く。施設の所には書かない）
class Town(World):
    def __init__(self, city):
        super().__init__(XS, H, ZS)
        self.city = city
        self.owned = set()          # 施設が書いた所（空気も）
        self.foot = set()           # 施設が書いた (x, z)（地面より上の何か。家を置かない所）
        for name, mine in city.writes.items():
            for (x, y, z), st in mine.items():
                self.owned.add((x, y, z))
                if y >= 1 or st[0] != "air":
                    self.foot.add((x, z))
        self.used = set()           # 街が使った (x, z)（家・倉庫・塔など。重ねない）
        self.surface = {}           # (x, z) → 地面の種類（"road"・"plaza"・"lot"・"yard"・"alley" など）

    def loc(self, x, y, z):
        return x - X0, y - Y0, z - Z0

    def put(self, x, y, z, state, nbt=None):
        if (x, y, z) in self.owned:
            return
        super().put(*self.loc(x, y, z), state, nbt)

    def get(self, x, y, z):
        return super().get(*self.loc(x, y, z))

    def name_at(self, x, y, z):
        return super().name_at(*self.loc(x, y, z))

    def put_if_empty(self, x, y, z, state):
        if self.get(x, y, z) is None:
            self.put(x, y, z, state)

    def clear(self, x, y, z):
        super().clear(*self.loc(x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, state)

    def free(self, x0, z0, x1, z1, margin=0):
        """四角（両端を含む）に、施設・街の建物・道・小道がないか。まわり margin には、施設と建物がないか（道はよい）"""
        for x in range(x0 - margin, x1 + margin + 1):
            for z in range(z0 - margin, z1 + margin + 1):
                if (x, z) in self.foot or (x, z) in self.used:
                    return False
                core = x0 <= x <= x1 and z0 <= z <= z1
                if core and (cc.road_at(x, z) is not None or self.surface.get((x, z)) != "lot"):
                    return False
                if math.hypot(x, z) >= R_WALLROAD - 1:
                    return False
        return True

    def take(self, x0, z0, x1, z1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.used.add((x, z))


# ================================================================ 道と広場
def paving(t):
    """城壁の内側の地面（y 0）：道・広場・城壁沿いの道・空き地（草）"""
    for x in range(X0, X0 + XS):
        for z in range(Z0, Z0 + ZS):
            r = math.hypot(x, z)
            name = cc.road_at(x, z)
            st = None
            if r <= R_PLAZA:
                st = plaza_floor(x, z, r)
                t.surface[(x, z)] = "plaza"
            elif name is not None and name != "城壁沿いの道":
                center = cc.center_at(x, z)
                if name.startswith("環状路"):
                    edge = not center
                    st = band() if edge else (ancient() if rng.random() < 0.7 else gray_stone())
                elif center:
                    st = ancient()
                    if name.startswith("塔の道") and x == 0 and z % 8 == 0:
                        st = block("chiseled_quartz_block")
                else:
                    st = band()
                t.surface[(x, z)] = "road"
            elif R_WALLROAD <= r < R_WALL_IN:
                st = block("cobblestone" if rng.random() < 0.5 else "stone_bricks" if rng.random() < 0.7 else "mossy_cobblestone")
                t.surface[(x, z)] = "wallroad"
            elif r < R_WALL_IN:
                st = block("grass_block", snowy=False)
                t.surface[(x, z)] = "lot"
            elif r >= R_WALL_OUT + 0.5 and gate_road(x, z):
                st = block("dirt_path") if rng.random() < 0.7 else block("coarse_dirt")
                t.surface[(x, z)] = "outroad"
            if st is not None:
                t.put(x, 0, z, st)
    corner_cuts(t)


def gate_road(x, z):
    """東門・南門の外へ続く土の道"""
    return (abs(z) <= 3 and x > 0) or (abs(x) <= 3 and z > 0)


def corner_cuts(t):
    """大通りと環状路の交差点の角を、2ブロック斜めに切る（角切り）"""
    # 交差点の4つの角：大通りの端（±5 か ±4）と環状路の端（53 か 59）が出会う所
    cuts = []
    for s in (-1, 1):
        half = 5 if s < 0 else 4          # 北は塔の道（幅11）
        for e in (53, 59):
            for side in (-1, 1):
                cuts.append(((half + 1) * side, s * (e - 1 if e == 53 else e + 1), side, s * (1 if e == 59 else -1)))
    for (x, z, dx, dz) in cuts:
        for k in range(2):
            for j in range(2 - k):
                px, pz = x + dx * k, z + dz * j
                if t.surface.get((px, pz)) == "lot":
                    t.put(px, 0, pz, band())
                    t.surface[(px, pz)] = "road"


def plaza_floor(x, z, r):
    """中央広場：大結晶を中心にした同心円の白い石畳（古代の模様）"""
    if r > R_PLAZA - 1.0:
        return block("quartz_bricks")
    if 6.5 <= r < 7.5 or 14.0 <= r < 15.0:
        return block("smooth_quartz")
    if 15.0 <= r < 15.6 or 13.4 <= r < 14.0:
        return block("polished_andesite")
    a = math.degrees(math.atan2(z, x)) % 45.0
    if 7.5 <= r < 13.4 and (a < 1.6 or a > 43.4):
        return block("polished_diorite")         # 8本の放射の線
    return ancient()


# ================================================================ 中央広場：大結晶・記憶の石・寄付の台座・舞台の空き地・灯り・ベンチ・花壇
def plaza(t):
    # 台座（6段）：外から、磨いた閃緑岩の縁・石英レンガ・なめらかな石英。段ごとに石英の階段で上がる
    tiers = [(1, 6.8), (2, 5.9), (3, 5.0), (4, 4.2), (5, 3.6), (6, 3.6)]
    for (y, rad) in tiers:
        for x in range(-8, 9):
            for z in range(-8, 9):
                d = math.hypot(x, z)
                if d <= rad:
                    if y == 1:
                        st = block("polished_diorite") if d > rad - 1 else block("quartz_bricks")
                    elif y in (5, 6):
                        st = block("chiseled_quartz_block") if (y == 5 and (abs(x) == 3 or abs(z) == 3)) else block("quartz_bricks")
                    else:
                        st = block("smooth_quartz") if d > rad - 1 else block("quartz_bricks")
                    t.put(x, y, z, st)
    # 台座の四方の階段（2段目まで上がれる。その上は結晶の根）
    for (dx, dz, f) in ((0, -1, "south"), (0, 1, "north"), (-1, 0, "east"), (1, 0, "west")):
        for k, (y, rad) in enumerate(tiers[1:3]):
            for w in (-1, 0, 1):
                x = int(round(dx * (rad + 0.6))) + (w if dx == 0 else 0)
                z = int(round(dz * (rad + 0.6))) + (w if dz == 0 else 0)
                t.put(x, y, z, stairs("quartz", f))
    # 大結晶：y 7〜30（24段）。ひし形の断面で、下から広がって上へ細くなる。外は水色のガラス、内は光る芯
    def radius(y):
        if y <= 9:
            return 2 + (y - 7) // 2
        if y <= 14:
            return 4
        return max(0, 4 - (y - 14) // 4)
    for y in range(7, 31):
        r = radius(y)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                m = abs(dx) + abs(dz)
                if m > r:
                    continue
                if y >= 29:
                    st = block("white_stained_glass")
                elif m == r:
                    st = block("light_blue_stained_glass")
                else:
                    st = block("sea_lantern")
                t.put(dx, y, dz, st)
    t.put(0, 31, 0, block("end_rod", facing="up"))
    # 根もとの小さな結晶（台座の上の四隅）と、まわりに浮かぶ欠片
    for (dx, dz) in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        for y in range(7, 10):
            t.put(dx, y, dz, block("light_blue_stained_glass"))
        t.put(dx, 10, dz, block("white_stained_glass"))
    for (dx, dz, y) in ((7, 0, 16), (-7, 0, 18), (0, 7, 17), (0, -7, 15)):
        t.put(dx, y, dz, block("light_blue_stained_glass"))
        t.put(dx, y + 1, dz, block("white_stained_glass"))
    # 記憶の石（大結晶の西。右クリックで、見逃した出来事を自分の画面だけで見直せる）
    t.put(-8, 1, 0, block("amemaru:memory_stone"))
    t.put(-8, 1, -1, slab("quartz", "bottom"))
    t.put(-8, 1, 1, slab("quartz", "bottom"))
    t.put(-9, 1, 0, slab("quartz", "bottom"))
    # 寄付の台座（広場の南東の縁。寄付の仕組みは Phase 7。いまは台座だけ）
    for x in range(14, 17):
        for z in range(14, 17):
            t.put(x, 1, z, slab("smooth_quartz", "bottom"))
    t.put(15, 1, 15, block("chiseled_quartz_block"))
    t.put(15, 2, 15, block("lectern", facing="north", has_book=False, powered=False))
    # 舞台の空き地（北西の縁。寄付で建つまでは石畳の空き地。四隅に目印の石）
    for x in range(-21, -11):
        for z in range(-21, -11):
            d = math.hypot(x, z)
            if 14.6 <= d <= R_PLAZA - 1.0:
                t.put(x, 0, z, block("stone_bricks") if (x + z) % 2 == 0 else block("polished_andesite"))
    for (x, z) in ((-19, -11), (-11, -19), (-16, -9), (-9, -16)):
        if math.hypot(x, z) < R_PLAZA - 1:
            t.put(x, 1, z, block("chiseled_quartz_block"))
    # 古代の結晶灯：斜め4か所（半径9）と、4つの入口の両側
    for sx in (-1, 1):
        for sz in (-1, 1):
            crystal_lamp(t, 7 * sx, 1, 7 * sz)
    for s in (-1, 1):
        crystal_lamp(t, 7, 1, 19 * s)
        crystal_lamp(t, -7, 1, 19 * s)
        crystal_lamp(t, 19 * s, 1, 7)
        crystal_lamp(t, 19 * s, 1, -7)
    # ベンチ（斜めの向きに、大結晶を向いて座る。トウヒの階段と、ひじ掛けの看板）
    for (bx, bz, f, ax) in ((-12, -4, "west", "z"), (12, 4, "east", "z"), (4, -12, "north", "x"), (-4, 12, "south", "x")):
        for k in range(3):
            x = bx + (k if ax == "x" else 0)
            z = bz + (k if ax == "z" else 0)
            t.put(x, 1, z, stairs("spruce", f))
        ends = ((bx - 1, bz), (bx + 3, bz)) if ax == "x" else ((bx, bz - 1), (bx, bz + 3))
        for (x, z) in ends:
            side = "west" if ax == "x" and x < bx else "east" if ax == "x" else "north" if z < bz else "south"
            t.put(x, 1, z, block("spruce_wall_sign", facing=side, waterlogged=False), sign_nbt(["", "", "", ""]))
    # 花壇（斜め4方向の弧。縁は石英の半ブロック）
    flowers = ["poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium"]
    for x in range(-22, 23):
        for z in range(-22, 23):
            d = math.hypot(x, z)
            if abs(x) > 6 and abs(z) > 6 and not (-21 <= x <= -11 and -21 <= z <= -11) and not (13 <= x <= 17 and 13 <= z <= 17):
                if 16.4 < d <= 18.2:
                    t.put(x, 0, z, block("grass_block", snowy=False))
                    t.put(x, 1, z, block(rng.choice(flowers)))
                elif 15.8 < d <= 16.4 or 18.2 < d <= 18.8:
                    t.put(x, 1, z, slab("smooth_quartz", "bottom"))
    # 広場の木（花の咲いたツツジ。斜めの花壇の外の角）
    for (x, z) in ((20, -9), (-20, 9), (9, 20), (-9, -20)):
        azalea(t, x, z)


def azalea(t, x, z, y=1):
    t.put(x, y, z, block("flowering_azalea"))


# ================================================================ 城壁・門・城壁の塔
def wall_and_gates(t):
    for x in range(-118, 119):
        for z in range(-118, 119):
            r = math.hypot(x, z)
            if not (R_WALL_IN <= r < R_WALL_OUT):
                continue
            ang = (math.degrees(math.atan2(x, -z)) + 360) % 360     # 北 0、東 90、南 180、西 270
            gate = None
            for g, a in GATES.items():
                gx, gz = gate_axis(g)
                along = x if g in ("north", "south") else z
                if abs(((ang - a) + 180) % 360 - 180) < 20 and abs(along) <= GATE_HALF[g]:
                    gate = g
            # 土台（y −2..0）
            for y in range(-2, 1):
                t.put(x, y, z, block("polished_diorite") if y == 0 else block("stone"))
            for y in range(1, WALL_TOP + 1):
                if gate and y <= 8:
                    continue      # 門の口（高さ8）
                if gate and y == 9:
                    st = block("chiseled_quartz_block") if abs(x if gate in ("north", "south") else z) == 0 else block("polished_diorite")
                elif y <= 6:
                    st = block("polished_diorite") if rng.random() < 0.72 else block("diorite")
                else:
                    st = gray_stone()
                t.put(x, y, z, st)
            # 胸壁（外のふちに、1つおきの凸）と、内のふちの低い手すり
            if r >= R_WALL_OUT - 1.0:
                tan = round((ang % 360) * math.pi / 180 * r)
                if tan % 3 != 0:
                    t.put(x, WALL_TOP + 1, z, gray_stone())
            elif r < R_WALL_IN + 1.0:
                t.put(x, WALL_TOP + 1, z, wall("stone_brick"))
                # 通路の灯り：内の手すりの上に、ランタン（12ブロックごと）
                tan = round((ang % 360) * math.pi / 180 * r)
                if tan % 12 == 0 and not gate:
                    t.put(x, WALL_TOP + 2, z, lantern(False))
            if gate:
                t.put(x, 0, z, ancient())
    # 門のまわりの道：門の口の床（白い石畳）
    for g in GATES:
        towers_of_gate(t, g)
    for a in (45, 135, 222, 315):
        diag_tower(t, a)


def gate_axis(g):
    return {"north": (0, -1), "east": (1, 0), "south": (0, 1)}[g]


def tower(t, cx, cz, half=3, top=22, roof=True, stair_side=None):
    """城壁の塔（四角、石。上に緑青の銅の四角すいの屋根）。(cx, cz) は真ん中、half は半分の幅"""
    for x in range(cx - half, cx + half + 1):
        for z in range(cz - half, cz + half + 1):
            t.used.add((x, z))
            edge = abs(x - cx) == half or abs(z - cz) == half
            for y in range(-2, 1):
                t.put(x, y, z, block("polished_diorite"))
            for y in range(1, top - 5):
                if edge:
                    corner = abs(x - cx) == half and abs(z - cz) == half
                    if corner:
                        st = block("polished_diorite") if y <= 6 else block("polished_andesite")
                    else:
                        st = (block("polished_diorite") if rng.random() < 0.7 else block("diorite")) if y <= 6 else gray_stone()
                    t.put(x, y, z, st)
                else:
                    t.put(x, y, z, block("stone_bricks") if y in (WALL_TOP, top - 6) else block("air"))
            # 細い窓（矢狭間）
            if edge and not (abs(x - cx) == half and abs(z - cz) == half):
                mid = (x == cx) or (z == cz)
                if mid:
                    for y in (9, 10, 15, 16):
                        if y < top - 6:
                            t.put(x, y, z, block("air"))
            # 見えない灯り（塔の中は入れないが、暗くしない）
    t.put(cx, 3, cz, block("light", level=15, waterlogged=False))
    t.put(cx, WALL_TOP + 2, cz, block("light", level=15, waterlogged=False))
    # 上のふちの帯と、緑青の銅の屋根（四角すい）
    y_eave = top - 5
    for x in range(cx - half - 1, cx + half + 2):
        for z in range(cz - half - 1, cz + half + 2):
            edge = abs(x - cx) == half + 1 or abs(z - cz) == half + 1
            if edge:
                t.put(x, y_eave - 1, z, block("stone_bricks"))
    if roof:
        p = Pyramid(t, Palette(roof="waxed_oxidized_cut_copper", roof_block="waxed_oxidized_copper"), cx, cz, half + 1, y_eave,
                    pitch=1, thickness=2)
        p.build()
        t.put(cx, top, cz, block("lightning_rod", facing="up", powered=False, waterlogged=False))


def towers_of_gate(t, g):
    """門の両脇の塔（高さ22、緑青の銅の屋根）"""
    half = GATE_HALF[g]
    off = half + 4
    # 東と南の門の塔は、真ん中が半径115（内の面が城壁の内の面 112 にそろい、城壁沿いの道にはみ出さない。11月21日）。
    # 北の門の塔は、外（z −116 から北）が塔前広場の構造物の箱（地面から +16 まで、すべて書く）なので、城壁の内へ
    # 3つ出す（真ん中 z −112）
    if g == "north":
        for s in (-1, 1):
            tower(t, s * off, -112, half=3, top=22)
    elif g == "south":
        for s in (-1, 1):
            tower(t, s * off, 115, half=3, top=22)
    else:
        for s in (-1, 1):
            tower(t, 115, s * off, half=3, top=22)


def diag_tower(t, a):
    """斜めの4か所の塔（高さ18）。真ん中は半径117.5（四角の内の角が城壁の上で、城壁沿いの道にはみ出さない）。南西の塔は、
    水門の構造物の箱（x −84 から西。箱の中をすべて書く）にかからないよう、少し南へ寄せる（222度）。

    城壁の上の通路へ上がる階段は作らない（城壁の上は見張りの通路で、プレイヤーは上がらない。【補完】11月21日。斜めの
    階段は、ブロックの格子では段の間に1マスの穴ができ、上れなかった）。
    """
    r = 117.5
    cx = int(round(math.sin(math.radians(a)) * r))
    cz = int(round(-math.cos(math.radians(a)) * r))
    tower(t, cx, cz, half=3, top=18)


def facing_of(dx, dz):
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


# ================================================================ 結界の柱（8本）
def barrier_pillars(t):
    """環状路の4つの角（外の斜め）と、大通りとの4つの交差点の脇。古代の白い石の柱に、結晶の頭"""
    spots = [(-62, -62), (62, -62), (62, 62), (-62, 62), (8, -62), (62, 8), (-8, 62), (-62, -8)]
    for (x, z) in spots:
        if (x, z) in t.foot:
            continue
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.put(x + dx, 1, z + dz, block("polished_diorite") if (dx or dz) else block("chiseled_quartz_block"))
                t.used.add((x + dx, z + dz))
        for y in range(2, 7):
            t.put(x, y, z, block("quartz_pillar", axis="y") if y not in (2, 6) else block("chiseled_quartz_block"))
        for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            t.put(x + dx, 2, z + dz, stairs("quartz", f))
        t.put(x, 7, z, block("light_blue_stained_glass"))
        t.put(x, 8, z, block("sea_lantern"))
        t.put(x, 9, z, block("light_blue_stained_glass"))
        t.put(x, 10, z, block("end_rod", facing="up"))


# ================================================================ 家（2階＋屋根裏。内装なし）
FACE = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
POST = log("spruce", "y", stripped=True)


def house(t, x0, z0, length, facing, gable_front=False, eave=11, chimney=False, door_wood="spruce", seed=0, max_top=None):
    """家。(x0, z0) は壁の外の北西の角。length は通りに沿う長さ（平入り）か奥行き（妻入り）。奥行き（棟を横切る幅）は7。

    facing は通りの側（正面）。gable_front なら三角の壁を通りに向ける（妻入り）。軒の高さ eave（10 か 11）。
    max_top を決めると、屋根（縁取りの頂を含む）をその高さまでにする（中央広場を囲む最初の1列は 18。④）。
    """
    r = random.Random(seed)
    across = 7
    along_street = facing in ("north", "south")
    if gable_front:
        wx, wz = (across, length) if along_street else (length, across)
    else:
        wx, wz = (length, across) if along_street else (across, length)
    x1, z1 = x0 + wx - 1, z0 + wz - 1
    t.take(x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    ridge = "x" if wx > wz or (wx == wz and along_street) else "z"
    if wx == across and wz > across:
        ridge = "z"
    elif wz == across and wx > across:
        ridge = "x"
    top_wall = eave - 1
    # 床（y 1）と、まわりの腰石
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.put(x, 1, z, block("polished_andesite") if edge else block("spruce_planks"))
            for y in range(2, top_wall + 1):
                if not edge:
                    t.put(x, y, z, block("air"))
    # 壁：1階は石（付け柱は柱と同じ所に、磨いた安山岩）、2階は木組み（柱は4ごと、間は漆喰）
    def post_at(i, n):
        return i == 0 or i == n - 1 or i % 4 == 0
    sides = []
    for z in (z0, z1):
        sides.append(("x", z, "north" if z == z0 else "south"))
    for x in (x0, x1):
        sides.append(("z", x, "west" if x == x0 else "east"))
    for (axis, fixed, out) in sides:
        n = wx if axis == "x" else wz
        for i in range(n):
            x = x0 + i if axis == "x" else fixed
            z = fixed if axis == "x" else z0 + i
            p = post_at(i, n)
            for y in range(2, top_wall + 1):
                if y <= 5:
                    st = block("polished_andesite") if p else gray_stone()
                elif y == 6:
                    st = log("spruce", axis, stripped=True) if not p else POST
                else:
                    st = POST if p else block("calcite")
                t.put(x, y, z, st)
            t.put(x, eave, z, log("spruce", axis, stripped=True) if not p else POST)
    # 窓と扉：正面・背面・横
    door_side = facing
    for (axis, fixed, out) in sides:
        n = wx if axis == "x" else wz
        mid = n // 2
        for i in range(1, n - 1):
            if post_at(i, n):
                continue
            x = x0 + i if axis == "x" else fixed
            z = fixed if axis == "x" else z0 + i
            dx, dz = FACE[out]
            window_col = (i % 4 == 2) or (n == 7 and i in (2, 4)) if n > 5 else i == mid
            is_door = out == door_side and i == (mid if n % 4 != 1 else (mid - 2 if mid % 4 == 0 else mid))
            if out == door_side and n == 7:
                is_door = i == 3
            if is_door:
                face = out
                t.put(x, 2, z, block("amemaru:house_door_" + door_wood, facing=face, half="lower", hinge="left", open=False, powered=False))
                t.put(x, 3, z, block("amemaru:house_door_" + door_wood, facing=face, half="upper", hinge="left", open=False, powered=False))
                t.put(x, 4, z, block("dark_oak_planks"))
                t.put(x + dx, 1, z + dz, stairs("stone_brick", OPP[out]))
                # 入口の横の壁のランタン（腕木5、灯り3）
                lx, lz = (x + 1, z) if axis == "x" else (x, z + 1)
                if post_at(i + 1, n):
                    lx, lz = (x - 1, z) if axis == "x" else (x, z - 1)
                lantern_arm(t, lx, lz, out)
                continue
            if window_col:
                # 1階の窓（2段目から：y 3..4）と、2階の窓（y 8..9）。ガラスは白い色ガラス（中は見えない）
                for y in (3, 4):
                    if not (out == door_side and abs(i - (3 if n == 7 else mid)) <= 0):
                        t.put(x, y, z, pane("white_stained_glass_pane"))
                for y in (8, 9) if eave >= 11 else (8,):
                    t.put(x, y, z, pane("white_stained_glass_pane"))
                # 2階の窓の下の花箱（ツツジの葉）と、両脇の雨戸（開いたトウヒのトラップドア）
                if out in (door_side, OPP[door_side]) or r.random() < 0.5:
                    t.put(x + dx, 7, z + dz, block("flowering_azalea_leaves" if r.random() < 0.5 else "azalea_leaves",
                                                     distance=1, persistent=True, waterlogged=False))
                for s in (-1, 1):
                    sx, sz = (x + s, z) if axis == "x" else (x, z + s)
                    for y in ((8, 9) if eave >= 11 else (8,)):
                        t.put(sx + dx, y, sz + dz, trapdoor("spruce", out, "bottom", True))
    # 屋根（赤い瓦。横に1進むと2上がる。軒の出1、妻の出1、縁はダークオーク）
    roof = Roof(t, pal, x0, x1, z0, z1, eave, ridge=ridge, pitch=2, side_overhang=1, end_overhang=1)
    skip = None
    if chimney:
        cx_, cz_ = ((x1 - 2, (z0 + z1) // 2) if ridge == "x" else ((x0 + x1) // 2, z1 - 2))
        def skip(sx, sy, sz):
            return (sx, sz) == (cx_, cz_)
    if max_top is not None:
        base_skip = skip

        def capped(sx, sy, sz):
            return sy > max_top or (base_skip is not None and base_skip(sx, sy, sz))
        skip = capped
    roof.build(skip=skip)
    # 妻の三角の壁（漆喰に柱と梁。屋根裏の小窓）
    for at_l in ((x0, x1) if ridge == "x" else (z0, z1)):
        rows = gable_wall(t, pal, roof, at_l, posts=(), beam_rows=())
        if rows:
            mid_c = (z0 + z1) // 2 if ridge == "x" else (x0 + x1) // 2
            y = rows[min(2, len(rows) - 1)][0]
            px, pz = (at_l, mid_c) if ridge == "x" else (mid_c, at_l)
            t.put(px, y, pz, pane("white_stained_glass_pane"))
    if chimney:
        for y in range(2, roof.ridge_y + 2):
            if y <= 20:
                t.put(cx_, y, cz_, block("bricks"))
        t.put(cx_, min(20, roof.ridge_y + 1), cz_, block("bricks"))
    # 中の見えない灯り（中は入れないが、暗くしない）
    t.put((x0 + x1) // 2, 3, (z0 + z1) // 2, block("light", level=15, waterlogged=False))
    return (x0, z0, x1, z1)


def lantern_arm(t, x, z, out):
    """入口の横の壁のランタン：濃い木の柵の腕木（y 5）、付け根の下にトウヒの持ち送り、先から鎖でランタン（y 3）"""
    dx, dz = FACE[out]
    t.put(x + dx, 5, z + dz, fence("dark_oak"))
    t.put(x + dx * 2, 5, z + dz * 2, fence("dark_oak"))
    t.put(x + dx, 4, z + dz, stairs("spruce", OPP[out], "top"))
    t.put(x + dx * 2, 4, z + dz * 2, block("chain", axis="y", waterlogged=False))
    t.put(x + dx * 2, 3, z + dz * 2, lantern(True))


# ================================================================ 家の並び
def row(t, start, end, line, axis, facing, setback=1, lengths=(9, 13), gable_every=3, seed=0, plaza_ring=False):
    """通りに沿って家を並べる。axis は通りの向き（"x" は東西の通り）。line は家の正面の壁の座標（通りの側）。

    家の間は、長屋（0）・すき間2（土の道）・路地3（2〜3軒ごと）。施設・道・ほかの家と重なる所は飛ばす。"""
    r = random.Random(seed)
    pos = start
    n = 0
    built = []
    since_alley = 0
    while pos < end:
        gable = (n % gable_every == gable_every - 1)
        length = r.choice(lengths)
        across = 7
        if gable:
            frontage, depth = across, length
        else:
            frontage, depth = length, across
        if pos + frontage - 1 > end:
            break
        if axis == "x":
            x0 = pos
            z0 = line if facing == "south" else line - depth + 1
            if facing == "south":
                z0 = line - depth + 1
            else:
                z0 = line
            box = (x0, z0, x0 + frontage - 1, z0 + depth - 1)
        else:
            z0 = pos
            x0 = line - depth + 1 if facing == "east" else line
            box = (x0, z0, x0 + depth - 1, z0 + frontage - 1)
        if t.free(*box, margin=1):
            eave = 11 if r.random() < 0.6 else 10
            chim = r.random() < 0.4
            wood = "spruce" if r.random() < 0.7 else "dark_oak"
            top = None
            if first_ring(box):
                eave, chim, top = 9, False, 18      # 中央広場を囲む最初の1列は棟18まで（④）
            house(t, box[0], box[1], length, facing, gable_front=gable, eave=eave, chimney=chim,
                  door_wood=wood, seed=seed * 100 + n, max_top=top)
            built.append(box)
            n += 1
            since_alley += 1
            gap = 0 if (since_alley < 2 and r.random() < 0.35) else (3 if since_alley >= 2 else 2)
            if gap >= 2:
                since_alley = 0 if gap == 3 else since_alley
                alley(t, box, axis, facing, gap, pos + frontage)
            pos += frontage + gap
        else:
            pos += 2
    return built


def first_ring(box):
    """家の箱（屋根の出1を入れて）が、中央広場の縁から13まで（半径 22.5+13.5）にかかるか（④の最初の1列）"""
    x0, z0, x1, z1 = box
    # 箱の中で中央にいちばん近い所
    cx = 0 if x0 - 1 <= 0 <= x1 + 1 else (x0 - 1 if x0 - 1 > 0 else x1 + 1)
    cz = 0 if z0 - 1 <= 0 <= z1 + 1 else (z0 - 1 if z0 - 1 > 0 else z1 + 1)
    return math.hypot(cx, cz) <= R_PLAZA + 13.5


def alley(t, box, axis, facing, gap, at):
    """家と家の間の、裏へ抜ける道（幅2〜3。丸石・砂利・土の道）"""
    x0, z0, x1, z1 = box
    if axis == "x":
        for x in range(at, at + gap):
            for z in range(z0, z1 + 1):
                if t.surface.get((x, z)) == "lot" and (x, z) not in t.used:
                    t.put(x, 0, z, alley_stone())
                    t.surface[(x, z)] = "alley"
    else:
        for z in range(at, at + gap):
            for x in range(x0, x1 + 1):
                if t.surface.get((x, z)) == "lot" and (x, z) not in t.used:
                    t.put(x, 0, z, alley_stone())
                    t.surface[(x, z)] = "alley"


# ================================================================ 倉庫（倉庫地区。2階＋地下、黒い瓦、大きな扉）
def warehouse(t, x0, z0, wx, wz, door_side, seed):
    r = random.Random(seed)
    x1, z1 = x0 + wx - 1, z0 + wz - 1
    if not t.free(x0, z0, x1, z1, margin=1):
        return None
    t.take(x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    eave = 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.put(x, 1, z, block("polished_andesite") if edge else block("spruce_planks"))
            for y in range(2, eave):
                if edge:
                    i = (x - x0) if z in (z0, z1) else (z - z0)
                    n = wx if z in (z0, z1) else wz
                    post = i % 4 == 0 or i == n - 1
                    if y <= 6:
                        st = block("polished_andesite") if post else gray_stone()
                    elif y == 7:
                        st = POST if post else log("spruce", "x" if z in (z0, z1) else "z", stripped=True)
                    else:
                        st = POST if post else block("spruce_planks")
                    t.put(x, y, z, st)
                else:
                    t.put(x, y, z, block("air"))
            if edge:
                t.put(x, eave, z, log("spruce", "x" if z in (z0, z1) else "z", stripped=True))
    # 地下の小窓（鉄格子。地下があると外から分かる）
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (z0, z1):
            t.put(x, 1, z, block("iron_bars", north=False, south=False, east=True, west=True, waterlogged=False))
    # 大きな扉（幅3×高さ4。濃い木の板で閉じた扉に見せる）と、上の木の看板
    dx, dz = FACE[door_side]
    if door_side in ("north", "south"):
        zf = z0 if door_side == "north" else z1
        cx = (x0 + x1) // 2
        for x in range(cx - 1, cx + 2):
            for y in range(2, 6):
                t.put(x, y, zf, block("dark_oak_planks") if x != cx else log("dark_oak", "y"))
        t.put(cx, 6, zf + dz, block("spruce_wall_sign", facing=door_side, waterlogged=False), sign_nbt(["資材倉庫", "", "", ""]))
        lantern_arm(t, cx + 2, zf, door_side)
        lantern_arm(t, cx - 2, zf, door_side)
    else:
        xf = x0 if door_side == "west" else x1
        cz = (z0 + z1) // 2
        for z in range(cz - 1, cz + 2):
            for y in range(2, 6):
                t.put(xf, y, z, block("dark_oak_planks") if z != cz else log("dark_oak", "y"))
        t.put(xf + dx, 6, cz, block("spruce_wall_sign", facing=door_side, waterlogged=False), sign_nbt(["資材倉庫", "", "", ""]))
        lantern_arm(t, xf, cz + 2, door_side)
        lantern_arm(t, xf, cz - 2, door_side)
    # 窓（2階。上の段）
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (z0, z1):
            for y in (9, 10):
                t.put(x, y, z, pane("glass_pane"))
    roof = Roof(t, Palette(roof="deepslate_tile", roof_block="deepslate_tiles", trim="dark_oak", post="spruce", beam="spruce",
                           plaster="calcite", frame="spruce"), x0, x1, z0, z1, eave + 1,
                ridge="x" if wx >= wz else "z", pitch=1, side_overhang=1, end_overhang=1)
    roof.build()
    for at_l in ((x0, x1) if wx >= wz else (z0, z1)):
        gable_wall(t, pal, roof, at_l, infill="spruce_planks")
    t.put((x0 + x1) // 2, 3, (z0 + z1) // 2, block("light", level=15, waterlogged=False))
    # まわりの木箱と樽（壁に寄せて、1か所に3〜5個）
    side_x = x1 + 1
    for k, z in enumerate(range(z0 + 1, z0 + 1 + r.randint(3, 5))):
        st = block("barrel", facing="up", open=False) if k % 2 == 0 else block("spruce_planks")
        t.put(side_x, 1, z, st)
        if k % 3 == 0:
            t.put(side_x, 2, z, block("barrel", facing="up", open=False))
    return (x0, z0, x1, z1)


# ================================================================ 住宅地区の小広場と井戸
def small_plaza(t, cx, cz, half=4):
    if not t.free(cx - half, cz - half, cx + half, cz + half):
        return False
    t.take(cx - half, cz - half, cx + half, cz + half)
    for x in range(cx - half, cx + half + 1):
        for z in range(cz - half, cz + half + 1):
            t.put(x, 0, z, block("cobblestone") if rng.random() < 0.7 else block("mossy_cobblestone"))
            t.surface[(x, z)] = "smallplaza"
    # 井戸：石レンガの縁（3×3）、中は水、上に柱4本と屋根（レンガの半ブロック）、鎖と桶（大釜）
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            if x == cx and z == cz:
                for y in range(-3, 1):
                    t.put(x, y, z, block("water", level=0))
                t.put(x, -4, z, block("stone"))
            else:
                t.put(x, 1, z, block("stone_bricks") if (x + z) % 2 == 0 else block("mossy_stone_bricks"))
                for y in range(-3, 1):
                    t.put(x, y, z, block("stone_bricks"))
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for y in (2, 3, 4):
            t.put(cx + dx, y, cz + dz, fence("spruce"))
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) == 2 or abs(z - cz) == 2:
                t.put(x, 5, z, slab("brick", "bottom"))
            else:
                t.put(x, 5, z, block("bricks"))
    t.put(cx, 6, cz, slab("brick", "bottom"))
    t.put(cx, 4, cz, block("chain", axis="y", waterlogged=False))
    t.put(cx, 3, cz, lantern(True))
    # まわりのベンチと灯り
    lamp_post(t, cx - half, 1, cz - half, arms=("south", "east"))
    lamp_post(t, cx + half, 1, cz + half, arms=("north", "west"))
    return True


# ================================================================ 木・裏庭・菜園
def leaf_ok(t, x, y, z):
    """木の葉を置いてよい所：道・広場・城壁沿いの道の上には出さない（通りの側を刈りこんだ木）。小道と路地の上は
    頭より上（y 4 から）だけ"""
    if cc.road_at(x, z) is not None:
        return False
    if t.surface.get((x, z)) in ("alley", "smallplaza") and y < 4:
        return False
    return t.get(x, y, z) is None


def oak(t, x, z, kind_="oak", height=None):
    h = height or rng.randint(4, 6)
    leaf = block(kind_ + "_leaves", distance=1, persistent=True, waterlogged=False)
    for y in range(1, h + 1):
        t.put(x, y, z, log(kind_))
    for dy in (-1, 0, 1):
        rr = 2 if dy < 1 else 1
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) == rr and abs(dz) == rr and rng.random() < 0.6:
                    continue
                if leaf_ok(t, x + dx, h + dy, z + dz):
                    t.put(x + dx, h + dy, z + dz, leaf)
    t.put(x, h + 2, z, leaf)
    t.used.add((x, z))


def spruce_tree(t, x, z):
    h = rng.randint(6, 8)
    leaf = block("spruce_leaves", distance=1, persistent=True, waterlogged=False)
    for y in range(1, h + 1):
        t.put(x, y, z, log("spruce"))
    for k, y in enumerate(range(h - 4, h + 2)):
        rr = 2 if k in (0, 2) else 1
        if y >= h:
            rr = 1 if y == h else 0
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) + abs(dz) > rr + (1 if rr == 2 else 0):
                    continue
                if leaf_ok(t, x + dx, y, z + dz):
                    t.put(x + dx, y, z + dz, leaf)
    t.put(x, h + 2, z, leaf)
    t.used.add((x, z))


def yards(t):
    """家の裏の空き地：花の畑、木、花の咲いたツツジ。

    畑に作物（小麦・ニンジン・ジャガイモ）は植えない（11月21日）。作物は時間で育ち、建てた直後に一部が消えることもあり
    （CIの自動テストで、ニンジンの畑のマスが空気に変わった）、建てた形と比べる「直した所」の確かめに出てしまうため。
    """
    lots = [(x, z) for (x, z), s in t.surface.items() if s == "lot" and (x, z) not in t.used and (x, z) not in t.foot]
    rng2 = random.Random(31)
    rng2.shuffle(lots)
    planted = set()
    for (x, z) in lots[: len(lots) // 9]:
        if (x, z) in planted or (x, z) in t.used:
            continue
        r = math.hypot(x, z)
        near_wall = r > R_WALLROAD - 9
        if near_wall and rng2.random() < 0.5:
            if all(((x + dx, z + dz) not in t.used and (x + dx, z + dz) not in t.foot) for dx in range(-2, 3) for dz in range(-2, 3)):
                (spruce_tree if rng2.random() < 0.4 else oak)(t, x, z)
                for dx in range(-3, 4):
                    for dz in range(-3, 4):
                        planted.add((x + dx, z + dz))
            continue
        k = rng2.random()
        if k < 0.10 and all(((x + dx, z + dz) not in t.used and (x + dx, z + dz) not in t.foot) for dx in range(-2, 3) for dz in range(-2, 3)):
            oak(t, x, z, "oak" if rng2.random() < 0.7 else "birch")
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    planted.add((x + dx, z + dz))
        elif k < 0.16:
            # 花の畑（3×4。根の張った土の畝に、2種類の花を1列ずつ交互に。畝の間はシダ）
            cells = [(x + dx, z + dz) for dx in range(3) for dz in range(4)]
            if all(t.surface.get(c) == "lot" and c not in t.used and c not in t.foot and c not in planted for c in cells):
                kinds = rng2.sample(["allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "red_tulip",
                                     "white_tulip", "pink_tulip", "orange_tulip"], 2)
                for (cx, cz) in cells:
                    row = cz - z
                    t.put(cx, 0, cz, block("rooted_dirt"))
                    t.put(cx, 1, cz, block("fern") if cx - x == 1 and row % 2 == 1 else block(kinds[row % 2]))
                    planted.add((cx, cz))
                    t.used.add((cx, cz))
        elif k < 0.30:
            azalea(t, x, z)
            planted.add((x, z))
            t.used.add((x, z))


# ================================================================ 灯り（暗くて湧ける所をなくす）
def street_lamps(t):
    """大通りの端の帯に古代の結晶灯（12ごと、左右互い違い）、環状路と城壁沿いの道に再建のランタン灯（8〜10ごと）"""
    # 塔の道（端の帯 x ±4..5）・工房通り・市場通り（商店街の中は施設が灯りを持つ）・祈りの道
    spots = [(5 if (z // 12) % 2 == 0 else -5, z) for z in range(-110, -24, 12)]
    spots += [(x, 4 if (x // 12) % 2 == 0 else -4) for x in range(26, 112, 12)]
    spots += [(x, 4 if (x // 12) % 2 == 0 else -4) for x in range(-50, -24, 12)]
    spots += [(4 if (z // 12) % 2 == 0 else -4, z) for z in range(26, 112, 12)]
    for (x, z) in spots:
        if cc.center_at(x, z) is None:
            crystal_lamp(t, x, 1, z)
    # 環状路（外の縁の1列。9ごと。交差点の近くは飛ばす）。腕木は道に沿う向き（ランタンは縁の帯の上。⑤）
    for k in range(-50, 51, 9):
        for (x, z, arm) in ((k, -59, "east"), (k, 59, "west"), (-59, k, "north"), (59, k, "south")):
            if abs(k) < 8:
                continue
            lamp_post(t, x, 1, z, arms=(arm,))
    # 城壁沿いの道（外の縁、城壁の足もと。8度ごと）
    for a in range(0, 360, 5):
        r = R_WALL_IN - 0.6
        x = int(round(math.sin(math.radians(a)) * r))
        z = int(round(-math.cos(math.radians(a)) * r))
        if any(abs(((a - ga) + 180) % 360 - 180) < 8 for ga in GATES.values()) or (a % 90) in (40, 45, 50):
            continue
        if (x, z) in t.foot or (x, z) in t.used:
            continue
        nx, nz = -x / r, -z / r
        arm = facing_of(nx, nz)
        lamp_post(t, x, 1, z, arms=(arm,))


def fill_dark(t, city_stage="grown"):
    """光を計算して、暗くて湧ける所に、見えない灯り（minecraft:light）を置く（なくなるまで）。

    街が置いた足もとは、すべて。施設が置いた足もとは、施設だけのときは明るかったのに、街（城壁・家など）が光をさえぎって
    暗くなった所だけ（そばの、街が書いてよい空いたマスに置く）。施設だけのときから暗い所は、施設の決まりのまま。
    """
    base = {}
    for name, mine in t.city.writes.items():
        for p, st in mine.items():
            base[p] = st
    base_light = compute_light(base)
    for it in range(6):
        cells = world_cells(t)
        light = compute_light(cells)
        dark = []
        for (x, y, z), st in cells.items():
            if not cc.floor_ok(st):
                continue
            c = (x, y + 1, z)
            if not (cc.spawn_empty(cells.get(c, ("air", ()))) and cc.spawn_empty(cells.get((x, y + 2, z), ("air", ())))
                    and light.get(c, 0) == 0):
                continue
            if (x, y, z) in t.owned:
                if base_light.get(c, 0) > 0:
                    dark.append((c, False))
            elif t.get(x, y, z) is not None:
                dark.append((c, True))
        if not dark:
            return it
        placed = set()
        for (c, own) in sorted(dark):
            x, y, z = c
            if any(abs(x - px) + abs(y - py) + abs(z - pz) <= 8 for (px, py, pz) in placed):
                continue
            spot = c if own else free_near(t, cells, c)
            if spot is None:
                continue
            t.put(*spot, block("light", level=15, waterlogged=False))
            placed.add(spot)
    return -1


def solid_cell(cells, p):
    """光をさえぎるマス（地面の y 0 から下は、何も書いていなくても詰まっている）"""
    st = cells.get(p)
    if st is None:
        return p[1] <= 0
    return cc.full_cube(st[0])


def free_near(t, cells, c, reach=5):
    """c から光が届く（さえぎるマスを通らない）近くで、街が書いてよい空いたマス（施設が書いていない所）"""
    from collections import deque
    seen = {c}
    q = deque([(c, 0)])
    while q:
        p, d = q.popleft()
        if p != c and p not in t.owned and cells.get(p) is None and p[1] >= 1:
            return p
        if d >= reach:
            continue
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
            n = (p[0] + dx, p[1] + dy, p[2] + dz)
            if n in seen or solid_cell(cells, n):
                continue
            seen.add(n)
            q.append((n, d + 1))
    return None


def world_cells(t):
    """街と施設を重ねた世界（施設が上）"""
    cells = {}
    for (lx, ly, lz), st in t.blocks.items():
        cells[(lx + X0, ly + Y0, lz + Z0)] = st
    for name, mine in t.city.writes.items():
        for p, st in mine.items():
            cells[p] = st
    return cells


def compute_light(cells):
    from collections import deque
    light, q = {}, deque()
    for p, st in cells.items():
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
            if solid_cell(cells, n):
                continue
            if light.get(n, 0) < lv:
                light[n] = lv
                q.append(n)
    return light


def emit(st):
    if st[0] == "light":
        return int(dict(st[1]).get("level", 15))
    return cc.emit(st)


# ================================================================ 案内板（中央広場の入口・交差点・門の内側）
def signpost(t, x, z, lines_by_face):
    """柱（皮をはいだトウヒの丸太3段）に、行き先の向きのトウヒの看板"""
    for y in (1, 2, 3):
        t.put(x, y, z, log("spruce", "y", stripped=True))
    t.put(x, 4, z, slab("dark_oak", "bottom"))
    for face, lines in lines_by_face.items():
        dx, dz = FACE[face]
        t.put(x + dx, 2 if face in ("north", "south") else 3, z + dz, block("spruce_wall_sign", facing=face, waterlogged=False),
              sign_nbt(lines))
    t.used.add((x, z))


def signposts(t):
    north = ["↑ 冒険者ギルド", "↑ 北門", "↑ 塔の門", ""]
    east = ["→ 工房通り", "→ 武器・防具工房", "→ 東門", ""]
    south = ["↓ 商店街", "↓ フリーマーケット", "↓ カジノ・南門", ""]
    west = ["← 結びの教会", "← 釣り堀", "← 記憶の庭", ""]
    # 中央広場の4つの入口（道の右手）
    signpost(t, 7, -21, {"south": north, "west": ["中央広場", "大結晶", "", ""]})
    signpost(t, 21, 7, {"west": east, "north": ["中央広場", "大結晶", "", ""]})
    signpost(t, -7, 21, {"north": south, "east": ["中央広場", "大結晶", "", ""]})
    signpost(t, -21, -7, {"east": west, "south": ["中央広場", "大結晶", "", ""]})
    # 門の内側
    signpost(t, -8, -104, {"south": ["↓ 中央広場", "↓ 冒険者ギルド", "", ""], "east": ["↑ 北門", "塔前広場・塔の門", "", ""]})
    signpost(t, 104, -7, {"west": ["← 中央広場", "← 工房通り", "", ""], "north": ["東門", "街の外へ", "", ""]})
    signpost(t, -7, 104, {"north": ["↑ 中央広場", "↑ 商店街", "", ""], "east": ["南門", "街の外へ", "", ""]})


# ================================================================ 書き出し
def save_tiles(t, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    groups = {}
    for (x, y, z), state in t.blocks.items():
        groups.setdefault((x // TILE, z // TILE), []).append((x, y, z, state))
    written = []
    for i in range(XS // TILE):
        for j in range(ZS // TILE):
            cells = sorted(groups.get((i, j), []), key=lambda c: (c[1], c[2], c[0]))
            path = os.path.join(out_dir, "t%d_%d.nbt" % (i, j))
            if not cells:
                if os.path.exists(path):
                    os.remove(path)
                continue
            palette, index, entries = [], {}, []
            for (x, y, z, state) in cells:
                if state not in index:
                    index[state] = len(palette)
                    palette.append(state)
                e = {"state": Int(index[state]), "pos": List[Int]([Int(x - i * TILE), Int(y), Int(z - j * TILE)])}
                if (x, y, z) in t.nbt:
                    e["nbt"] = t.nbt[(x, y, z)]
                entries.append(Compound(e))
            pal_ = []
            for name, props in palette:
                c = {"Name": String(name if ":" in name else "minecraft:" + name)}
                if props:
                    # 性質は名前の順に（書き出すたびに同じファイルになるように。性質の組の並びは、Python の文字列のハッシュで変わる）
                    c["Properties"] = Compound({k: String(v) for k, v in sorted(props)})
                pal_.append(Compound(c))
            root = nbtlib.File({"DataVersion": Int(DATA_VERSION), "size": List[Int]([Int(TILE), Int(H), Int(TILE)]),
                                "palette": List[Compound](pal_), "blocks": List[Compound](entries),
                                "entities": List[Compound]([])}, gzipped=True)
            # 圧縮の時刻を 0 にして、同じ設計からは同じファイルができるようにする（mapgen/world.py と同じ）
            raw = io.BytesIO()
            root.write(raw)
            with open(path, "wb") as f, gzip.GzipFile(filename="", mode="wb", fileobj=f, mtime=0) as g:
                g.write(raw.getvalue())
            written.append(("t%d_%d" % (i, j), (X0 + i * TILE, Y0, Z0 + j * TILE)))
    return written


# ================================================================ 組み立て
def lane(t, x0, z0, x1, z1):
    """住宅地区の小道（幅3。丸石・砂利・土の道）。家を置かない所にする"""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if t.surface.get((x, z)) == "lot" and (x, z) not in t.foot and (x, z) not in t.used:
                t.put(x, 0, z, alley_stone())
                t.surface[(x, z)] = "alley"


def residential(t):
    """家の並び（住宅地区は北西が中心。各地区の通りの裏にも）。通りと小道の両側に、通りを向けて並べる"""
    houses = []
    # ---- A：北西の外（塔の道の西、環状路の北）
    lane(t, -62, -84, -6, -82)
    houses += row(t, -104, -64, -7, "z", "east", seed=1)
    houses += row(t, -84, -10, -61, "x", "south", seed=2)
    houses += row(t, -60, -10, -81, "x", "north", seed=3)
    houses += row(t, -50, -10, -85, "x", "south", seed=4)
    # ---- C：環状路の内側の北西の区画
    houses += row(t, -51, -10, -51, "x", "north", seed=5)
    houses += row(t, -44, -26, -7, "z", "east", seed=6)
    houses += row(t, -51, -24, -6, "x", "south", seed=7)
    houses += row(t, -44, -14, -51, "z", "west", seed=8)
    # ---- D：環状路の内側の北東の区画
    houses += row(t, 14, 50, -51, "x", "north", seed=9)
    houses += row(t, -50, -26, 7, "z", "west", seed=10)
    houses += row(t, -44, -8, 51, "z", "east", seed=11)
    # ---- E：環状路の内側の南西の区画
    houses += row(t, -51, -22, 6, "x", "north", seed=12)
    houses += row(t, -51, -22, 51, "x", "south", seed=13)
    houses += row(t, 14, 44, -51, "z", "west", seed=15)
    # ---- F：環状路の内側の南東の区画
    houses += row(t, 20, 50, 6, "x", "north", seed=16)
    houses += row(t, 20, 50, 51, "x", "south", seed=17)
    houses += row(t, 14, 44, 51, "z", "east", seed=18)
    # ---- G：東の外（工房通りの南、防具工房の東と南）
    lane(t, 62, 42, 94, 44)
    houses += row(t, 28, 52, 61, "z", "west", seed=19)
    houses += row(t, 83, 100, 6, "x", "north", seed=20)
    houses += row(t, 62, 94, 41, "x", "south", seed=21)
    houses += row(t, 62, 94, 45, "x", "north", seed=22)
    # ---- H：南東の外（カジノの東）
    lane(t, 48, 80, 86, 82)
    houses += row(t, 50, 92, 61, "x", "north", seed=23)
    houses += row(t, 50, 86, 79, "x", "south", seed=24)
    houses += row(t, 48, 70, 83, "x", "north", seed=25)
    # ---- I：西の外（教会の北）と、北東の外（工房通りの北）
    houses += row(t, -56, -46, -61, "z", "east", seed=26)
    houses += row(t, 61, 77, -6, "x", "south", seed=27)
    houses += row(t, -46, -20, 93, "x", "north", seed=28)
    return houses


def plaza_ring(t):
    """中央広場を囲む最初の1列の低い家（棟18まで：軒9。広場の斜めの角に）"""
    built = []
    for (x0, z0, length, facing, s_) in ((-25, -24, 9, "south", 1), (-30, 17, 9, "north", 2), (21, 17, 9, "north", 3)):
        across = 7
        wx, wz = (length, across) if facing in ("north", "south") else (across, length)
        if t.free(x0, z0, x0 + wx - 1, z0 + wz - 1, margin=1):
            house(t, x0, z0, length, facing, eave=9, chimney=False, seed=900 + s_, max_top=18)
            built.append((x0, z0, x0 + wx - 1, z0 + wz - 1))
    return built


def build():
    city = cc.City("grown", town=False)
    t = Town(city)
    paving(t)
    plaza(t)
    wall_and_gates(t)
    barrier_pillars(t)
    signposts(t)
    # 倉庫地区（北東）：資材倉庫3棟
    wh = []
    for (region, wx, wz, side, s) in (((60, -80, 72, -60), 13, 15, "south", 1), ((74, -62, 86, -40), 13, 13, "west", 2),
                                      ((61, -50, 71, -30), 15, 13, "north", 3)):
        rx0, rz0, rx1, rz1 = region
        for z0 in range(rz0, rz1 + 1):
            b = None
            for x0 in range(rx0, rx1 + 1):
                if all(math.hypot(cx, cz) < R_WALLROAD - 2 for cx in (x0, x0 + wx - 1) for cz in (z0, z0 + wz - 1)):
                    b = warehouse(t, x0, z0, wx, wz, side, s)
                    if b:
                        break
            if b:
                wh.append(b)
                break
    houses = plaza_ring(t)
    houses += residential(t)
    for (cx, cz) in ((-84, -82), (-36, 30), (36, 30), (80, 90), (32, -40), (90, -40)):
        small_plaza(t, cx, cz)
    street_lamps(t)
    yards(t)
    t.finalize()
    filled = fill_dark(t)
    return t, houses, wh, filled


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = os.path.join(args[0], "town") if args else OUT
    t, houses, wh, filled = build()
    written = save_tiles(t, out)
    print("家:", len(houses), "倉庫:", len(wh), "区画:", len(written), "ブロック:", len(t.blocks), "暗い所を埋めた回数:", filled)
    with open(os.path.join(out, "placements.txt"), "w", encoding="utf-8") as f:
        for name, (x, y, z) in written:
            f.write("%s %d %d %d\n" % (name, x, y, z))
    if "--render" in sys.argv:
        import city_figures  # noqa: F401
        render_dir = sys.argv[sys.argv.index("--render") + 1]
        top_map(t, os.path.join(render_dir, "town_map.png"))


def top_map(t, path, scale=2):
    from PIL import Image
    import floor_figures as ff
    cells = world_cells(t)
    top = {}
    for (x, y, z), st in cells.items():
        if st[0] in ("air", "light", "barrier") or st[0] in ff.SKIP_TOP:
            continue
        if y > top.get((x, z), (-99, None))[0]:
            top[(x, z)] = (y, st[0])
    img = Image.new("RGB", (XS * scale, ZS * scale), (40, 60, 40))
    px = img.load()
    for (x, z), (y, name) in top.items():
        col = ff.COLORS.get(name) or EXTRA.get(name) or (200, 0, 200)
        f = 1.0 + max(-0.3, min(0.3, (y - 2) * 0.02))
        c = tuple(max(0, min(255, int(v * f))) for v in col)
        lx, lz = x - X0, z - Z0
        if 0 <= lx < XS and 0 <= lz < ZS:
            for dx in range(scale):
                for dz in range(scale):
                    px[lx * scale + dx, lz * scale + dz] = c
    img.save(path)
    print("描きました:", path)


EXTRA = {"bricks": (150, 70, 60), "brick_stairs": (150, 70, 60), "brick_slab": (150, 70, 60), "calcite": (223, 224, 220),
         "deepslate_tiles": (55, 55, 60), "deepslate_tile_stairs": (55, 55, 60), "deepslate_tile_slab": (55, 55, 60),
         "waxed_oxidized_cut_copper_stairs": (80, 160, 130), "waxed_oxidized_copper": (80, 160, 130),
         "waxed_oxidized_cut_copper_slab": (80, 160, 130), "waxed_oxidized_cut_copper": (80, 160, 130),
         "dark_oak_planks": (65, 43, 20), "spruce_planks": (115, 85, 50), "stripped_spruce_log": (115, 90, 55),
         "light_blue_stained_glass": (102, 153, 216), "white_stained_glass": (240, 240, 240), "end_rod": (240, 240, 240),
         "cobblestone": (127, 127, 127), "dirt_path": (148, 122, 65), "farmland": (100, 70, 40), "wheat": (200, 180, 80),
         "carrots": (230, 140, 40), "potatoes": (100, 160, 60), "flowering_azalea": (120, 150, 80), "spruce_leaves": (60, 90, 60),
         "flowering_azalea_leaves": (130, 150, 90), "azalea_leaves": (90, 130, 60), "lightning_rod": (200, 120, 80),
         "quartz_stairs": (236, 230, 223), "smooth_quartz_slab": (236, 230, 223), "quartz_slab": (236, 230, 223),
         "lectern": (150, 110, 60), "amemaru:memory_stone": (230, 240, 255), "barrel": (120, 85, 50), "spruce_log": (80, 60, 40),
         "birch_leaves": (96, 140, 60), "spruce_wall_sign": (115, 85, 50), "stone_brick_stairs": (122, 121, 122)}


if __name__ == "__main__":
    main()
