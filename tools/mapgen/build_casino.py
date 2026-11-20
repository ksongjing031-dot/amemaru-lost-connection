"""コネリアのカジノ「ラッキーノット」（PHASE 10。建築の文書「PHASE 10：カジノ」の設計どおり）。

遊戯館「ラッキーノット」は、冒険者と旅人が集まる社交場（Phase 0-A）。支配人はジゼル・ノワール。3日目に開場し、
はじめはルーレット（0が一つの形式）と景品の交換だけ。寄付の共同事業②で「ハイ＆ロー」（カードの新しい遊び）、
⑥で「魚レース」（釣った魚を走らせて賭ける）が足される（育つ前・育った後。都市のルール⑩）。
市場通りの東側、南門の近くに、正面を西（市場通り）へ向けて建つ。前に小広場、北に路地、東に裏の通路、南に城壁沿いの道。

  - 建物 26×30（南北×東西。都市のルール⑨）。1階は灰色の石、2階は木組みと漆喰。床は1階 +2（足もと +3）・
    2階 +9（足もと +10）、軒 +14。前の棟と奥の棟は急な寄棟（2：1、棟 +24）、広間の棟は 1：1 の切妻（棟 +22、
    いちばん上の半ブロック +23）。屋根は黒い瓦（深層岩タイル）で、軒と破風の縁は金色（なめらかな砂岩）。
  - 正面の真ん中（入口の間、幅8）は石で、急な切妻（2：1、棟 +24）を通りへ向ける。破風に大看板と「運命の輪」の丸窓。
    1階は古代の白い石のアーチの入口（両開きの扉と、脇と上のガラス）、2階は支配人室から出る表の露台（2本の柱で受ける）。
  - 正面の両端の角に、2階から上の小塔（4×4、持ち送りで受ける）。四角すいの屋根の頂（+22）に旗（+23）。
  - 1階：入口の間 → 大広間（真ん中にルーレットの卓と大きな吊り灯、北の側廊に酒場と休み場、南の側廊にカードの卓の場所）
    → 奥の舞台のアーチ → VIPルーム。入口の間の北に両替の窓口（受付）、南に景品の交換所。奥の北は働く人の場所
    （裏口・樽の置き場・働く人の階段）、奥の南は2階への階段。
  - 2階：大広間の上は「宴の間」（屋根の小屋組みが見える高い部屋。⑥のあとは魚レースの間）。前に帳場・支配人室
    （表の露台）・働く人の部屋、奥にジゼルの部屋。
  - 地下（秘密の階）：VIPルームの本棚の扉から下りる。噂の間・初代の金庫。働く人の階段の下は酒と道具の地下室
    （土台の鉄格子の小窓。都市のルール③）。
  - 中に入れる所の樽は、鍵をかける（Lock）。チェスト・かまど・醸造台は置かない。

座標：世界の座標（大結晶が x 0・z 0。x が東、z が南）。高さ y は道のブロックが 0（足もとが 1）。
建物の中の座標 (u, v) は、u が西→東（u=0 が世界の x 13、正面の壁）、v が北→南（v=0 が世界の z 72、北の壁）。
構造物の (0, 0, 0) は世界の (x 5, 道のブロック − 3, z 68)。構造物の中では X = x − 5、Y = y + 3、Z = z − 68。

使い方: python3 tools/mapgen/build_casino.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
  - <出力>/casino.nbt（43×29×36）・casino_highlow.nbt（ハイ＆ロー。同じ所に重ねる）・casino_fishrace.nbt（魚レース。
    同じ所に重ねる）
  - --ci：自動の確認の写真の座標（CiScreenshots の MAP_VIEWS の行）を出す
ゲームの中では /place template amemaru:map/conelia/casino・casino_highlow・casino_fishrace で置ける。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from nbtlib.tag import Compound, String

from mapgen.arch import Palette, Roof
from mapgen.fixtures import hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, lectern_nbt, log, pane, sign_nbt,
                          slab, stairs, trapdoor, wall)

X0, Z0, YOFF = 5, 68, 3          # 構造物の (0, 0, 0) の世界の x・z と、y のずれ（Y = y + YOFF）
W, H, D = 43, 29, 36             # x 5..47、y −3..25、z 68..103
BX, BZ = 13, 72                  # 建物の北西の角（u 0, v 0）
LOCK = "amemaru:casino_lock"     # 樽の鍵（この名前のアイテムを持たないと開かない）

FL, FT = 2, 3                    # 1階の床のブロック・足もと
CL, FT2 = 9, 10                  # 2階の床のブロック（1階の天井）・足もと
EV = 14                          # 軒の梁（2階の天井）
BF, BT = -2, -1                  # 地下の床のブロック・足もと
R0 = 15                          # 屋根のいちばん下
WALL_R = 107                     # 城壁沿いの道（中心からこの半径より外）は書かない
AX = (12, 13)                    # 正面の軸の v（入口の扉・絨毯・ルーレットの卓）
TABLE = (14.5, 12.5)             # ルーレットの卓の真ん中 (u, v)
COLS = ((12, 6), (17, 6), (12, 19), (17, 19))   # 大広間の柱 (u, v)

pal = Palette(seed=1010, roof="deepslate_tile", roof_block="deepslate_tiles", trim="dark_oak", post="spruce",
              beam="spruce", plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)
GOLD = "smooth_sandstone"        # 金の縁（屋根の軒と破風）
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


# ---------------------------------------------------------------- 置く道具（世界の x・z と、道からの y）
def put(x, z, y, state, nbt=None):
    X, Y, Z = x - X0, y + YOFF, z - Z0
    if 0 <= X < W and 0 <= Z < D and 0 <= Y < H:
        w.put(X, Y, Z, state, nbt)


def get(x, z, y):
    return w.get(x - X0, y + YOFF, z - Z0)


def name_at(x, z, y):
    st = get(x, z, y)
    return st[0] if st else "air"


def B(u, v):
    """建物の中の (u, v) → 世界の (x, z)"""
    return BX + u, BZ + v


def bput(u, v, y, state, nbt=None):
    put(BX + u, BZ + v, y, state, nbt)


def bget(u, v, y):
    return get(BX + u, BZ + v, y)


def bname(u, v, y):
    return name_at(BX + u, BZ + v, y)


def bfill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                bput(u, v, y, state() if callable(state) else state)


def frame(u, v, y, facing, item, rotation=0, name=None):
    x, z = B(u, v)
    w.item_frame(x - X0, y + YOFF, z - Z0, facing, item, rotation=rotation, name=name)


def stand(u, v, y, facing, **kw):
    x, z = B(u, v)
    w.armor_stand(x - X0, y + YOFF, z - Z0, facing=facing, **kw)


def wall_lamp(u, v, y, out, reach=2, chain=1, style="wood"):
    """壁 (u, v) から out の向きへ腕木を出したランタン。腕木の高さ y、ランタンは y - 1 - chain"""
    x, z = B(u, v)
    wall_lantern(w, x - X0, y + YOFF, z - Z0, out, style=style, reach=reach, chain=chain)


def hang(u, v, y, chain=1):
    """y は鎖のいちばん上（上のブロックの下）。ランタンは y - chain"""
    x, z = B(u, v)
    hang_lantern(w, x - X0, y + YOFF, z - Z0, chain=chain)


def hang_down(u, v, y_lamp):
    """上のいちばん近いブロック（屋根の下・梁）から、鎖で y_lamp まで吊るランタン"""
    y = y_lamp + 1
    while bget(u, v, y) is None and y < H - YOFF:
        y += 1
    for yy in range(y_lamp + 1, y):
        bput(u, v, yy, block("chain", axis="y", waterlogged=False))
    bput(u, v, y_lamp, lantern(True))


def post_lamp(x, z, arms=("south",)):
    lamp_post(w, x - X0, 1 + YOFF, z - Z0, arms=arms)


def wall_sign(u, v, y, facing, lines, wood="spruce", color="black"):
    bput(u, v, y, block(wood + "_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines, color=color))


def hanging_sign(u, v, y, facing, lines):
    """壁から出す吊り看板（spruce_wall_hanging_sign）。facing は看板の面の向き（壁に沿う向き）"""
    bput(u, v, y, block("spruce_wall_hanging_sign", facing=facing, waterlogged=False),
         sign_nbt(lines, lines, hanging=True))


def banner(u, v, y, facing, color, patterns):
    bput(u, v, y, block(color + "_wall_banner", facing=facing), banner_nbt(patterns))


def door_pair(u, v, y, facing, axis, wood="spruce"):
    """2枚の扉（y が下の段）。axis は扉を並べる向き（"u" か "v"）"""
    cells = ((u, v, "left"), (u + 1, v, "right")) if axis == "u" else ((u, v, "left"), (u, v + 1, "right"))
    for (a, b, hinge) in cells:
        bput(a, b, y, door(wood, facing, False, hinge))
        bput(a, b, y + 1, door(wood, facing, True, hinge))


def one_door(u, v, y, facing, wood="spruce", hinge="left"):
    bput(u, v, y, door(wood, facing, False, hinge))
    bput(u, v, y + 1, door(wood, facing, True, hinge))


def barrel(u, v, y, facing="up"):
    bput(u, v, y, block("barrel", facing=facing, open=False),
         Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))


def candle(u, v, y, n=1, color=""):
    bput(u, v, y, block((color + "_" if color else "") + "candle", candles=n, lit=True, waterlogged=False))


def chair(u, v, y, facing, wood="spruce"):
    """椅子（階段）。facing は背もたれの側（座る人は反対の向きを見る）"""
    bput(u, v, y, stairs(wood, facing))


# ---------------------------------------------------------------- 材料（都市のルール③⑤）
def gray():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで"""
    r = rng.random()
    if r < 0.80:
        return block("stone_bricks")
    if r < 0.90:
        return block("cracked_stone_bricks")
    if r < 0.96:
        return block("mossy_stone_bricks")
    return block("andesite")


def white():
    """古代の層の白い石：磨いた閃緑岩・閃緑岩・方解石"""
    r = rng.random()
    if r < 0.66:
        return block("polished_diorite")
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def white_paving():
    """古代の白い石畳（灰色の石のつぎはぎ1割）"""
    r = rng.random()
    if r < 0.55:
        return block("polished_diorite")
    if r < 0.75:
        return block("diorite")
    if r < 0.88:
        return block("calcite")
    return block("stone_bricks") if r < 0.95 else block("andesite")


def band_paving():
    """端の帯（石レンガと安山岩）"""
    return block("stone_bricks") if rng.random() < 0.7 else block("polished_andesite")


def lane_paving():
    """路地・裏の通路：丸石・砂利・土の道"""
    r = rng.random()
    if r < 0.45:
        return block("cobblestone")
    if r < 0.75:
        return block("gravel")
    if r < 0.9:
        return block("stone_bricks")
    return block("coarse_dirt")


def grass():
    return block("grass_block", snowy=False)


def sx(u, v):
    """梁・横木の丸太の向き：壁に沿う向き"""
    return "x" if v in (0, 25) else "z"


def beam(axis):
    return log("spruce", axis, stripped=True)


POST = log("spruce", "y", stripped=True)


def outside_wall(x, z):
    """城壁沿いの道（書かない所）か"""
    return math.hypot(x, z) >= WALL_R


def gate_block(x, z):
    """南門の前の東の区画（x 5..12、z 98..103。南門と一緒に作る。書かない）"""
    return x <= 12 and z >= 98


# ---------------------------------------------------------------- 地面（前の小広場・北の路地・裏の通路・南の小道）
FRONT_X = 12                     # 前の小広場の東の端（建物の正面の前の1列。花壇と段）


def ground():
    for x in range(X0, X0 + W):
        for z in range(Z0, Z0 + D):
            if outside_wall(x, z) or gate_block(x, z):
                continue
            for y in (-3, -2, -1):
                put(x, z, y, block("dirt"))
            if BX <= x <= BX + 29 and BZ <= z <= BZ + 25:
                continue                                   # 建物（shell が置く）
            if z <= 70:
                st = lane_paving()                         # 北の路地（z 68..70）
            elif x <= FRONT_X and z <= 97:
                # 前の小広場（x 5..12、z 71..97）：古代の白い石畳。通りの側の1列と北の端は端の帯、正面の軸は白い帯
                if x == X0 or z == 71:
                    st = band_paving()
                elif z in (84, 85):
                    st = block("quartz_bricks") if x in (6, 9) else block("polished_diorite")
                else:
                    st = white_paving()
            elif z == 71:
                st = band_paving()                         # 北の面の足もと（雨だれの帯）
            elif x >= 43 and z <= 99:
                st = lane_paving()                         # 東の裏の通路（x 43..46）と、南の小道への角
            elif z in (98, 99):
                st = lane_paving()                         # 南の小道（城壁沿いの道へ）
            else:
                st = grass()
            put(x, z, 0, st)


# ---------------------------------------------------------------- 建物の骨組み（土台・壁・床・梁）
SIDE_POSTS = (0, 4, 7, 12, 17, 22, 25, 29)      # 北と南の壁の、2階の柱の u
FRONT_POSTS = (0, 4, 8, 17, 21, 25)              # 正面と背面の壁の、2階の柱の v（真ん中の入口の間 v 9..16 は石）
PAV = (9, 16)                                    # 入口の間（正面の真ん中の石の部分）の v


def ext_wall(u, v):
    return u in (0, 29) or v in (0, 25)


def shell():
    for u in range(30):
        for v in range(26):
            # 土台の下（−3）と、床の下の詰め物（地下の部屋はあとで空ける）
            bput(u, v, -3, block("stone_bricks"))
            for y in (-2, -1, 0, 1):
                bput(u, v, y, block("stone_bricks") if ext_wall(u, v) else block("dirt"))
            bput(u, v, FL, block("spruce_planks"))
            bput(u, v, CL, block("spruce_planks"))
            if not ext_wall(u, v):
                continue
            corner = u in (0, 29) and v in (0, 25)
            pav = u == 0 and PAV[0] <= v <= PAV[1]
            # 土台（道から2段。古代の白い石、角は石英レンガ）
            for y in (0, 1):
                bput(u, v, y, block("quartz_bricks") if corner else white())
            # 1階（+2..+8）：灰色の石。入口の間の正面は古代の白い石
            for y in range(FL, CL):
                if pav:
                    bput(u, v, y, block("quartz_bricks") if v in PAV else white())
                else:
                    bput(u, v, y, block("stone_bricks") if corner else gray())
            # 2階の床の梁（+9）と、2階（+10..+13）、軒の梁（+14）
            if pav:
                bput(u, v, CL, block("chiseled_sandstone") if v in PAV else block("cut_sandstone"))
                for y in range(FT2, EV):
                    bput(u, v, y, block("stone_bricks") if v in PAV else gray())
                bput(u, v, EV, block("stone_bricks"))
                continue
            along = "x" if v in (0, 25) else "z"
            bput(u, v, CL, beam(along))
            bput(u, v, EV, beam(along))
            posts = SIDE_POSTS if v in (0, 25) else FRONT_POSTS
            a = u if v in (0, 25) else v
            for y in range(FT2, EV):
                bput(u, v, y, POST if a in posts else block("calcite"))
    # 中の壁：u 7（入口の側と大広間）・u 22（大広間と奥）、入口の間の両脇 v 9・16、奥の仕切り v 7・18
    for v in range(1, 25):
        for u in (7, 22):
            for y in range(FT, CL):
                bput(u, v, y, block("stone_bricks"))
            for y in range(FT2, EV):
                bput(u, v, y, POST if v in (6, 19, 9, 16, 7, 18) else block("calcite"))
    for u in range(1, 7):
        for v in PAV:
            for y in list(range(FT, CL)) + list(range(FT2, EV)):
                bput(u, v, y, block("calcite"))
    for u in range(23, 29):
        for v in (7, 18):
            for y in list(range(FT, CL)) + list(range(FT2, EV)):
                bput(u, v, y, block("calcite"))
    # 2階の天井（+14）：前の棟と奥の棟の部屋の上（屋根裏の床。下付きの半ブロックで、屋根裏に敵が湧かないように）
    for u in list(range(1, 7)) + list(range(23, 29)):
        for v in range(1, 25):
            bput(u, v, EV, slab("spruce", "bottom"))


# ---------------------------------------------------------------- 屋根（都市のルール⑥：黒い瓦、カジノは金の縁）
rpal = Palette(seed=1011, roof="deepslate_tile", roof_block="deepslate_tiles", trim=GOLD)


class Hip:
    """四方へ下がる寄棟（四角すい）。x0..x1・z0..z1 は軒の出を含む範囲（世界の x・z）。y0 は屋根のいちばん下。
    軒の下には金の縁（なめらかな砂岩の逆さ階段）。cap があれば、棟の上に半ブロック"""

    def __init__(self, x0, x1, z0, z1, y0, pitch, cap=True):
        self.x0, self.x1, self.z0, self.z1, self.y0, self.pitch, self.cap = x0, x1, z0, z1, y0, pitch, cap
        self.kx, self.kz = (x1 - x0) // 2, (z1 - z0) // 2
        self.km = min(self.kx, self.kz)

    def k(self, x, z):
        return min(x - self.x0, self.x1 - x, z - self.z0, self.z1 - z)

    def top(self, x, z):
        return self.y0 + self.pitch * self.k(x, z) + self.pitch - 1

    def inside(self, x, y, z):
        if not (self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1):
            return False
        return self.y0 - 1 <= y < self.top(x, z)

    def ridge_y(self):
        return self.y0 + self.pitch * self.km + self.pitch - 1

    def build(self, skip):
        for x in range(self.x0, self.x1 + 1):
            for z in range(self.z0, self.z1 + 1):
                dx = min(x - self.x0, self.x1 - x)
                dz = min(z - self.z0, self.z1 - z)
                k = min(dx, dz)
                top = self.top(x, z)
                for y in range(max(self.y0, top - 3), top):
                    if not skip(x, y, z):
                        put(x, z, y, block("deepslate_tiles"))
                if k >= self.km:
                    if not skip(x, top, z):
                        put(x, z, top, block("deepslate_tiles"))
                    if self.cap and not skip(x, top + 1, z):
                        put(x, z, top + 1, slab("deepslate_tile"))
                    continue
                if dx <= dz:
                    facing = "east" if x - self.x0 <= self.x1 - x else "west"
                else:
                    facing = "south" if z - self.z0 <= self.z1 - z else "north"
                if not skip(x, top, z):
                    put(x, z, top, stairs("deepslate_tile", facing))
                # 屋根の筋（2段ごとに外へ少し出た段）
                if self.pitch == 1 and k % 2 == 1 and 0 < k < self.km - 1:
                    ox, oz = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[facing]
                    if not skip(x + ox, top - 1, z + oz) and get(x + ox, z + oz, top - 1) is None:
                        put(x + ox, z + oz, top - 1, stairs("deepslate_tile", facing, "top"))
                if k == 0 and not skip(x, self.y0 - 1, z):
                    put(x, z, self.y0 - 1, stairs(GOLD, facing, "top"))


def U(u):
    return BX + u - X0


def V(v):
    return BZ + v - Z0


# 真ん中の帯（入口の間 → ルーレット → VIPルームの軸）：東西の棟、2：1、棟 +24。正面と背面に破風（金の縁）
band = Roof(w, rpal, U(0), U(29), V(PAV[0]), V(PAV[1]), R0 + YOFF, ridge="x", pitch=2, side_overhang=1, end_overhang=1,
            ridge_cap=False, trim_block=GOLD)
# 大広間の棟：南北の棟、1：1（棟 +22、半ブロック +23）。北と南に破風
hall_roof = Roof(w, rpal, U(8), U(21), V(0), V(25), R0 + YOFF, ridge="z", pitch=1, side_overhang=1, end_overhang=1,
                 trim_block=GOLD)
# 前の棟・奥の棟：寄棟 1：1（棟 +19、半ブロック +20）
front_hip = Hip(BX - 1, BX + 8, BZ - 1, BZ + 26, R0, 1)
back_hip = Hip(BX + 21, BX + 30, BZ - 1, BZ + 26, R0, 1)
# 正面の両端の小塔（4×4、u −1..2）：壁 +10..+16、四角すいの屋根 2：1（頂 +22）
TURRETS = ((-1, -1), (-1, 23))
turret_roofs = [Hip(BX + u0 - 1, BX + u0 + 4, BZ + v0 - 1, BZ + v0 + 4, 17, 2, cap=False) for (u0, v0) in TURRETS]
TURRET_TOP = 16


def in_turret(x, z, y):
    for (u0, v0) in TURRETS:
        if BX + u0 <= x <= BX + u0 + 3 and BZ + v0 <= z <= BZ + v0 + 3 and y <= TURRET_TOP:
            return True
    return False


def in_building(x, z, y):
    return BX <= x <= BX + 29 and BZ <= z <= BZ + 25 and y < R0


ALL_ROOFS = ("band", "hall", "front", "back", "t0", "t1")


def roof_obj(k):
    return {"band": band, "hall": hall_roof, "front": front_hip, "back": back_hip, "t0": turret_roofs[0],
            "t1": turret_roofs[1]}[k]


def inside_roof(k, x, y, z):
    r = roof_obj(k)
    if isinstance(r, Roof):
        return r.inside(x - X0, y + YOFF, z - Z0)
    return r.inside(x, y, z)


def roof_skip(me):
    others = [k for k in ALL_ROOFS if k != me]

    def skip(x, y, z):
        if in_building(x, z, y) or in_turret(x, z, y):
            return True
        return any(inside_roof(k, x, y, z) for k in others)
    return skip


def roof_skip_xyz(me):
    """Roof（構造物の座標で呼ぶ）の skip"""
    s = roof_skip(me)
    return lambda X, Y, Z: s(X + X0, Y - YOFF, Z + Z0)


def dark_rim(roof):
    """切妻の縁（屋根の面より1段高い縁）を黒（磨いた深層岩）にする。外の破風板（逆さ階段）だけが金の線になる"""
    for l in (roof.l0, roof.l1):
        for c in range(roof.c0, roof.c1 + 1):
            for Y in range(roof.y0, roof.ridge_y + 3):
                X, Z = roof.xz(c, l)
                st = w.get(X, Y, Z)
                if st is None:
                    continue
                if st[0] == GOLD:
                    w.put(X, Y, Z, block("polished_deepslate"))
                elif st[0] == GOLD + "_stairs" and dict(st[1]).get("half") == "bottom":
                    w.put(X, Y, Z, (GOLD.replace(GOLD, "polished_deepslate_stairs"), st[1]))
                elif st[0] == GOLD + "_slab":
                    w.put(X, Y, Z, ("polished_deepslate_slab", st[1]))


def roof_boards():
    """部屋から見える屋根の下の面（いちばん下の瓦のブロック）を、トウヒの板（野地板）にする：宴の間・支配人室・ジゼルの部屋"""
    areas = [(8, 21, 1, 24), (1, 6, 10, 15), (23, 28, 9, 16)]
    for (u0, u1, v0, v1) in areas:
        for u in range(u0, u1 + 1):
            for v in range(v0, v1 + 1):
                y = EV + 1
                while y < 25 and bget(u, v, y) is None:
                    y += 1
                if y < 25 and bname(u, v, y) == "deepslate_tiles":
                    bput(u, v, y, block("spruce_planks"))


def fill_turret_tops():
    """小塔の四角すいの屋根の中（詰めた壁の上 +17 から屋根の下まで）を詰める（中に空いた所を残さない）"""
    for t in turret_roofs:
        for x in range(t.x0, t.x1 + 1):
            for z in range(t.z0, t.z1 + 1):
                for y in range(TURRET_TOP + 1, t.top(x, z)):
                    if in_turret(x, z, TURRET_TOP) and get(x, z, y) is None:
                        put(x, z, y, block("deepslate_tiles"))


def roofs():
    front_hip.build(roof_skip("front"))
    back_hip.build(roof_skip("back"))
    hall_roof.build(skip=roof_skip_xyz("hall"))
    band.build(skip=roof_skip_xyz("band"))
    for i, t in enumerate(turret_roofs):
        t.build(roof_skip("t%d" % i))
    dark_rim(band)
    dark_rim(hall_roof)
    # 真ん中の帯の破風の頂の縁（+25）は置かない（カジノの高さは 24 まで。都市のルール④）
    for (X, Y, Z) in [p for p in w.blocks if p[1] - YOFF > 24]:
        w.put(X, Y, Z, AIR)


def gable_rows(roof, y_from):
    """Roof の破風の壁の行 [(y, lo, hi)]（c の範囲。軒の出は除く）。y は道からの高さ"""
    rows = []
    y = y_from
    while True:
        ext = roof.gable_extent(y + YOFF)
        if ext is None:
            break
        lo, hi = max(ext[0], roof.c0 + roof.so), min(ext[1], roof.c1 - roof.so)
        if lo > hi:
            break
        rows.append((y, lo, hi))
        y += 1
    return rows


def gables():
    # 真ん中の帯の正面の破風（u 0）：灰色の石。いちばん下の段（+15）は大看板の台、上に「運命の輪」の丸窓（+17..+20）
    for (y, lo, hi) in gable_rows(band, R0):
        for Zc in range(lo, hi + 1):
            v = Zc + Z0 - BZ
            bput(0, v, y, block("stone_bricks") if v in PAV else gray())
            bput(29, v, y, POST if v in PAV or (v in (12, 13) and y == R0) else block("calcite"))
        if y in (R0, R0 + 4):
            for Zc in range(lo, hi + 1):
                bput(29, Zc + Z0 - BZ, y, beam("z"))
    # 大広間の破風（v 0・25）：木組みと漆喰。柱は大広間の柱の列（u 12・17）にそろえる
    for (y, lo, hi) in gable_rows(hall_roof, R0):
        for Xc in range(lo, hi + 1):
            u = Xc + X0 - BX
            for v in (0, 25):
                if y == R0 or y == R0 + 4:
                    bput(u, v, y, beam("x"))
                else:
                    bput(u, v, y, POST if u in (12, 17) else block("calcite"))


def turrets():
    """正面の両端の小塔（4×4、u −1..2）。2階の床（+9）から +16 まで。外へ出たマスの下は石の持ち送り（+7・+8）で受ける。
    2階（+10..+13）は外の2面だけが木組みの壁で、中は部屋の張り出し（建物の角の壁も空ける）。+14..+16 は詰めた壁"""
    for (u0, v0) in TURRETS:
        out_v = v0 if v0 < 0 else v0 + 3          # 外へ出た北か南の面の v
        for u in range(u0, u0 + 4):
            for v in range(v0, v0 + 4):
                edge = u in (u0, u0 + 3) or v in (v0, v0 + 3)
                corner = u in (u0, u0 + 3) and v in (v0, v0 + 3)
                outer = u == u0 or v == out_v
                along = "z" if u in (u0, u0 + 3) else "x"
                if u < 0 or v < 0 or v > 25:
                    bput(u, v, CL, block("spruce_planks"))
                    if u < 0 and (v < 0 or v > 25):
                        bput(u, v, CL - 1, block("stone_bricks"))
                        bput(u, v, CL - 2, stairs("stone_brick", "east", "top"))
                    else:
                        face = "east" if u < 0 else ("south" if v < 0 else "north")
                        bput(u, v, CL - 1, stairs("stone_brick", face, "top"))
                for y in range(FT2, EV):
                    bput(u, v, y, (POST if corner else block("calcite")) if outer else AIR)
                for y in range(EV, TURRET_TOP + 1):
                    if y in (EV, TURRET_TOP) and edge and not corner:
                        bput(u, v, y, beam(along))
                    elif edge:
                        bput(u, v, y, POST if corner else block("calcite"))
                    else:
                        bput(u, v, y, block("calcite"))


# ---------------------------------------------------------------- 外観（窓・入口・露台・破風の大看板と丸窓）
FACE_OUT = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}
FACE_IN = {"west": "east", "east": "west", "north": "south", "south": "north"}


def wcell(face, a, d=0):
    """壁の面の a 番目のマス（d は外への出）。west・east は u 0・29 の面で a = v、north・south は v 0・25 の面で a = u"""
    du, dv = FACE_OUT[face]
    if face == "west":
        return (0 + du * d, a)
    if face == "east":
        return (29 + du * d, a)
    if face == "north":
        return (a, 0 + dv * d)
    return (a, 25 + dv * d)


def arch_window(face, a0, width, y0=FT + 1, y1=FT + 4, keystone=True, sill=True, arch=True):
    """1階の石の壁のアーチの窓（ガラスは壁の面）。上の段の両はしは石のまま（角を丸める）。外に石の窓台、上に要石"""
    for i in range(width):
        for y in range(y0, y1 + 1):
            if arch and y == y1 and width >= 3 and i in (0, width - 1):
                continue
            u, v = wcell(face, a0 + i)
            bput(u, v, y, pane())
        if sill:
            u, v = wcell(face, a0 + i, 1)
            bput(u, v, y0 - 1, stairs("stone_brick", FACE_IN[face], "top"))
    if keystone:
        for i in range(width):
            if width % 2 == 1 and i != width // 2:
                continue
            u, v = wcell(face, a0 + i)
            bput(u, v, y1 + 1, block("chiseled_stone_bricks"))


def timber_window(face, a, width=1, y0=FT2 + 1, height=2, box=False):
    """2階の木組みの窓（幅1か2、高さ2）。下に濃い木の窓台、box なら花箱（花の咲いたツツジ）"""
    for i in range(width):
        u, v = wcell(face, a + i)
        for y in range(y0, y0 + height):
            bput(u, v, y, pane())
        ou, ov = wcell(face, a + i, 1)
        if box:
            bput(ou, ov, y0 - 1, leaves("flowering_azalea_leaves"))
        else:
            bput(ou, ov, y0 - 1, trapdoor("dark_oak", FACE_OUT_NAME[face], "top", False))


FACE_OUT_NAME = {"west": "west", "east": "east", "north": "north", "south": "south"}


def iron_slot(face, a):
    """地下の鉄格子の小窓（土台の上の段、+1）"""
    u, v = wcell(face, a)
    bput(u, v, 1, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))


def facade():
    # ---- 正面（西）
    for a0 in (1, 5, 18, 22):
        arch_window("west", a0, 3)
    for a0 in (1, 5, 18, 22):                       # 窓の上の日よけ（紫と黄のしま）
        for i in range(3):
            bput(-1, a0 + i, CL - 1, block("purple_wool" if (a0 + i) % 2 else "yellow_wool"))
    for a in (4, 8, 17, 21):                        # 1階の付け柱（2階の柱の下）
        for y in range(FL, CL):
            bput(0, a, y, block("polished_andesite"))
    for a in (5, 7, 18, 20):
        timber_window("west", a, box=True)
    for (u, v) in ((0, 3), (0, 22)):
        for y in range(FT2, EV):
            bput(u, v, y, block("calcite"))
    # 入口の間（v 9..16）：古代の白い石のアーチ（幅4・高さ5、上の角を丸める）、両開きの扉、脇と上のガラス
    for v in (10, 15):
        for y in range(FL, CL - 1):
            bput(0, v, y, block("quartz_pillar", axis="y"))
        bput(0, v, CL - 1, block("chiseled_quartz_block"))
    for v in range(11, 15):
        for y in range(FT, FT + 5):
            if y == FT + 4 and v in (11, 14):
                bput(0, v, y, block("cut_sandstone"))
                continue
            if v in (12, 13) and y <= FT + 1:
                continue
            bput(0, v, y, pane())
    door_pair(0, 12, FT, "west", "v", wood="dark_oak")
    for v in (12, 13):
        bput(0, v, CL - 1, block("chiseled_sandstone"))           # 金の要石
    for v in (11, 14):
        bput(0, v, CL - 1, block("cut_sandstone"))
    # 2階：支配人室から表の露台への扉と、両脇の窓
    door_pair(0, 12, FT2, "west", "v", wood="dark_oak")
    for v in (10, 15):
        for y in (FT2 + 1, FT2 + 2):
            bput(0, v, y, pane())
        bput(0, v, FT2 + 3, block("chiseled_stone_bricks"))
    # 表の露台（u −2..−1、v 10..15、床 +9）：2本の白い柱（u −2、v 10・15）で受ける。手すりは閃緑岩の塀
    for v in (10, 15):
        bput(-2, v, 1, block("quartz_bricks"))
        for y in range(2, CL - 1):
            bput(-2, v, y, block("quartz_pillar", axis="y"))
        bput(-2, v, CL - 1, block("chiseled_quartz_block"))
    for v in range(11, 15):
        bput(-2, v, CL - 1, log("dark_oak", "z"))
    for u in (-2, -1):
        for v in range(10, 16):
            bput(u, v, CL, block("polished_diorite"))
    for v in range(10, 16):
        bput(-2, v, FT2, wall("diorite"))
    for v in (10, 15):
        bput(-1, v, FT2, wall("diorite"))
        bput(-2, v, FT2 + 1, lantern(False))
    # 入口の段（2段。下の段 u −2 は柱の間、上の段 u −1 は露台の下いっぱい）
    for v in range(11, 15):
        bput(-2, v, 1, stairs("polished_diorite", "east"))
    for v in range(10, 16):
        bput(-1, v, 1, block("polished_diorite"))
        bput(-1, v, FL, stairs("polished_diorite", "east"))
    # 露台の床から、鎖で灯りを吊る（入口の上）
    for v in (11, 14):
        hang(-1, v, CL - 1)
    # 破風：大看板（+15・+16。トウヒの板の台と看板）と「運命の輪」の丸窓（+17..+20、真ん中は金の軸）
    for v in range(10, 16):
        for y in (R0, R0 + 1):
            bput(-1, v, y, block("spruce_planks"))
        bput(-1, v, R0 - 1, stairs("spruce", "east", "top"))
    wall_sign(-2, 12, R0 + 1, "west", ["", "遊戯館", "", ""])
    wall_sign(-2, 13, R0 + 1, "west", ["", "ラッキーノット", "", ""])
    wall_sign(-2, 12, R0, "west", ["さあ、", "運命の輪を", "", ""])
    wall_sign(-2, 13, R0, "west", ["", "回しましょう", "", ""])
    for (y, vs) in ((R0 + 2, (12, 13)), (R0 + 3, (11, 12, 13, 14)), (R0 + 4, (11, 12, 13, 14)), (R0 + 5, (12, 13))):
        for v in vs:
            hub = y in (R0 + 3, R0 + 4) and v in (12, 13)
            bput(0, v, y, block("chiseled_sandstone") if hub else pane())
    for v in (11, 14):
        for y in (R0 + 2, R0 + 5):
            bput(0, v, y, block("chiseled_stone_bricks"))
    # 小塔の窓（外の2面、+11..+12）
    for (u0, v0) in TURRETS:
        out_v = v0 if v0 < 0 else v0 + 3
        for y in (FT2 + 1, FT2 + 2):
            for v in (v0 + 1, v0 + 2):
                bput(u0, v, y, pane())
            for u in (u0 + 1, u0 + 2):
                bput(u, out_v, y, pane())
    # ---- 北の面（v 0）・南の面（v 25）
    for face in ("north", "south"):
        arch_window(face, 1, 3)
        for a0 in (9, 14, 19):
            if face == "north" and a0 in (14, 19):
                arch_window(face, a0, 2, y0=FT + 3, y1=FT + 4, keystone=False, arch=False)   # 酒場の棚の上の高い窓
            else:
                arch_window(face, a0, 2, arch=False)
        arch_window(face, 27, 1, y0=FT + 2, y1=FT + 3, keystone=False, arch=False)
        for a in (5, 9, 10, 14, 15, 19, 20, 23, 24, 27):
            timber_window(face, a)
    arch_window("north", 23, 2, y0=FT + 2, y1=FT + 3, keystone=False, arch=False)
    arch_window("south", 23, 2, y0=FT + 2, y1=FT + 3, keystone=False, arch=False)
    # 大広間の破風の窓（宴の間の明かり取り。+16..+18）
    for v in (0, 25):
        for u in range(13, 17):
            for y in (R0 + 1, R0 + 2):
                bput(u, v, y, pane())
        for u in (14, 15):
            bput(u, v, R0 + 3, pane())
    # ---- 背面（東）
    one_door(29, 5, FT, "east", wood="spruce")
    bput(30, 5, FL, stairs("stone_brick", "west"))         # 裏口の外の2段（足もと +3 → +1）
    bput(30, 5, 1, block("stone_bricks"))
    bput(31, 5, 1, stairs("stone_brick", "west"))
    bput(31, 5, 0, block("stone_bricks"))
    arch_window("east", 2, 2, y0=FT + 2, y1=FT + 3, keystone=False, arch=False)
    arch_window("east", 12, 2, arch=False)
    arch_window("east", 21, 2, arch=False)
    for a in (2, 3, 12, 13, 21, 22):
        timber_window("east", a)
    for v in (12, 13):                               # 帯の破風の窓（ジゼルの部屋の上）
        for y in (R0 + 2, R0 + 3):
            bput(29, v, y, pane())
    # 地下の鉄格子の小窓（酒と道具の地下室。都市のルール③）
    for a in (24, 27):
        iron_slot("north", a)
    for a in (2, 3):
        iron_slot("east", a)


# ---------------------------------------------------------------- 1階：入口の間・両替の窓口・景品の交換所
def carpet(u, v, color, y=FT):
    bput(u, v, y, block(color + "_carpet"))


def entrance():
    # 床：入口の間は白い石と方解石の市松、真ん中に赤い絨毯（入口 → ルーレット → VIPルームの軸）
    for u in range(1, 7):
        for v in range(10, 16):
            bput(u, v, FL, block("polished_diorite") if (u + v) % 2 == 0 else block("calcite"))
    for u in range(0, 7):
        for v in AX:
            carpet(u, v, "red")
    # 北の壁（v 9）：両替の窓口（u 2..5）。台（濃い木）の上に鉄格子、上に濃い木の梁
    for u in range(2, 6):
        bput(u, 9, FT, block("dark_oak_planks"))
        for y in (FT + 1, FT + 2):
            bput(u, 9, y, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
        bput(u, 9, FT + 3, log("dark_oak", "x"))
    wall_sign(1, 10, FT + 2, "south", ["チップの両替", "1日の上限まで", "（受付）", ""])
    # 南の壁（v 16）：景品の交換所へのアーチ（u 2..5、上の角を丸める）
    for u in range(2, 6):
        for y in range(FT, FT + 4):
            if y == FT + 3 and u in (2, 5):
                continue
            bput(u, 16, y, AIR)
    for u in (2, 5):
        bput(u, 16, FT + 3, block("chiseled_stone_bricks"))
    # 東の壁（u 7）：大広間への大きなアーチ（v 10..15、高さ6、上の角を丸める）。両脇は金の柱
    for v in range(10, 16):
        for y in range(FT, FT + 5):
            if y == FT + 4 and v in (10, 15):
                continue
            bput(7, v, y, AIR)
    for v in (9, 16):
        for y in range(FT, CL):
            bput(7, v, y, block("cut_sandstone") if y not in (FT, CL - 1) else block("chiseled_sandstone"))
    for v in range(10, 16):
        bput(7, v, CL - 1, block("chiseled_sandstone") if v in AX else block("cut_sandstone"))
    # きまりの書見台（入口の右手。チップ・上限・景品・卓）【補完】
    bput(6, 10, FT, block("lectern", facing="south", has_book=True, powered=False),
         lectern_nbt("ラッキーノットのきまり", "ジゼル・ノワール", [
             "遊戯館ラッキーノットへ、ようこそ。\n\n遊べるのは、街の通貨（ノット）で買ったチップだけです。チップは受付（北の窓口）で。1日に買える数には上限があります。",
             "はじめの遊びはルーレット。0が一つの輪です。\n\n4人以上で一つの卓を囲むと、街の結びが少し強くなります。",
             "景品は、景品の交換所（南）で、チップと取りかえます。見た目の品、称号、留め糸。強さに関わる品はありません。",
             "カジノナイトの夜は、配当に少しおまけがつきます。\n\n勝っても負けても、笑って帰ること。それがこの館のきまりです。\n――支配人 ジゼル",
         ]))
    # 吊り灯（入口の間の真ん中。鎖で2つ）
    for v in AX:
        hang(3, v, CL - 1, chain=1)
    # 掲示板（入口の左手の壁。今日の催し）
    wall_sign(1, 15, FT + 2, "north", ["今日の催し", "カジノナイトは", "広場の告知で", ""])


def cashier():
    """両替の窓口の奥（受付。働く人の側）：窓口の内の係の立つ所、チップの樽、金庫、帳面"""
    for u in range(1, 7):
        for v in range(1, 9):
            bput(u, v, FL, block("spruce_planks"))
    # 窓口の内：係の立つ所（u 3..4、v 8）。その後ろの台に、チップの樽（鍵）と天秤（大釜）
    for u in (1, 6):
        barrel(u, 8, FT, "up")
    bput(1, 7, FT, block("cauldron"))
    # 金庫（鉄のブロックと、上に重い重量感知板）と帳面の机
    bput(1, 2, FT, block("iron_block"))
    bput(1, 2, FT + 1, block("heavy_weighted_pressure_plate", power=0))
    bput(1, 3, FT, block("iron_block"))
    for u in (3, 4, 5):
        bput(u, 1, FT, block("dark_oak_planks"))
    bput(4, 1, FT + 1, block("lectern", facing="south", has_book=True, powered=False),
         lectern_nbt("両替の帳面", "受付", ["チップの両替の記録。\n\n1日の上限に届いた人の名前に、印をつけること。"]))
    candle(3, 1, FT + 1, 2)
    chair(4, 2, FT, "north", "dark_oak")
    for v in (4, 5, 6):
        barrel(1, v, FT, "east")
    candle(5, 1, FT + 1, 1)
    # 働く人の扉（大広間の北の側廊へ。u 7、v 3）
    one_door(7, 3, FT, "east", wood="spruce")
    hang(3, 5, CL - 1, chain=1)
    hang(5, 3, CL - 1, chain=1)


def prize_room():
    """景品の交換所：景品の台（額縁）、見た目の品の防具立て、交換の台（係は台の内）"""
    for u in range(1, 7):
        for v in range(17, 25):
            bput(u, v, FL, block("dark_oak_planks"))
    # 大広間の南の側廊からの入口（u 7、v 21..22）
    for v in (21, 22):
        for y in range(FT, FT + 3):
            bput(7, v, y, AIR)
    # 交換の台（u 5、v 18..20）。係は台の内（u 6、v 19）
    for v in (18, 19, 20):
        bput(5, v, FT, block("dark_oak_planks"))
    bput(5, 20, FT + 1, block("candle", candles=3, lit=True, waterlogged=False))
    wall_sign(6, 17, FT + 2, "south", ["景品の交換", "チップと", "取りかえます", ""])
    # 景品の台（白い石の台の上に、上向きの額縁）：称号の札・留め糸・旗の模様・見た目の帽子
    prizes = [((2, 18), "name_tag", "称号の札"), ((3, 18), "string", "留め糸（見本）"),
              ((2, 20), "flower_banner_pattern", "旗の模様"), ((3, 20), "painting", "飾りの絵")]
    for (uv, item, nm) in prizes:
        bput(uv[0], uv[1], FT, block("polished_diorite"))
        frame(uv[0], uv[1], FT + 1, "up", item, name=nm)
    # 見た目の品：染めた革の服を着せた防具立て（紫と黄、黒）
    stand(2, 23, FT, "east", head=("leather_helmet", {"color": 0x7F3FB2}), chest=("leather_chestplate", {"color": 0x7F3FB2}),
          legs=("leather_leggings", {"color": 0x1D1D21}), feet=("leather_boots", {"color": 0x1D1D21}))
    stand(4, 23, FT, "north", head=("golden_helmet", {}), chest=("leather_chestplate", {"color": 0xFED83D}),
          legs=("leather_leggings", {"color": 0x7F3FB2}), feet=("leather_boots", {"color": 0xFED83D}))
    # 壁の額縁（柱の内の面）と値段の札
    frame(1, 21, FT + 2, "east", "music_disc_cat", name="楽の円盤")
    wall_sign(6, 24, FT + 2, "north", ["景品の値段は", "交換の台で", "たずねてね", ""])
    hang(3, 19, CL - 1, chain=1)
    hang(3, 22, CL - 1, chain=1)


# ---------------------------------------------------------------- 1階：大広間（ルーレット・大きな吊り灯・酒場・休み場・カードの卓の場所）
def tdist(u, v):
    return math.hypot(u - TABLE[0], v - TABLE[1])


# ルーレットの輪（12の目）：北の真ん中から時計回り。G＝0（緑）、R＝赤、B＝黒
POCKETS = [(14, 11), (15, 11), (16, 11), (16, 12), (16, 13), (16, 14), (15, 14), (14, 14), (13, 14), (13, 13), (13, 12),
           (13, 11)]
POCKET_COLORS = ["green"] + ["red" if i % 2 == 1 else "black" for i in range(1, 12)]
GISELE = (18, 12)                # ジゼルの立つ所（ルーレットの東。西を向く）
CARD_BAYS = ((9, 10), (14, 15))  # カードの卓の u（南の側廊、v 22..23）


def hall():
    # 床：側廊はトウヒの板、身廊は濃い木の板。ルーレットのまわりに白い輪と金の輪（参考画像 casino_01 の丸い模様）
    for u in range(8, 22):
        for v in range(1, 25):
            d = tdist(u, v)
            if 7 <= v <= 18:
                if d <= 3.0:
                    st = block("polished_deepslate")
                elif d <= 4.3:
                    st = block("polished_diorite")
                elif d <= 5.2:
                    st = block("cut_sandstone")
                else:
                    st = block("dark_oak_planks")
            else:
                st = block("spruce_planks")
            bput(u, v, FL, st)
    # 絨毯の軸：入口のアーチ → ルーレットの白い輪、ルーレットの東 → VIPルームのアーチ
    for v in AX:
        for u in range(8, 22):
            if tdist(u, v) > 4.3 or u >= 19:
                carpet(u, v, "red")
    # 柱（金の帯つき）と、天井の梁（濃い木の丸太。柱の列に沿って格子）
    for (u, v) in COLS:
        bput(u, v, FT, block("chiseled_sandstone"))
        for y in range(FT + 1, CL - 1):
            bput(u, v, y, log("dark_oak", "y"))
        bput(u, v, CL - 1, block("chiseled_sandstone"))
    for u in range(8, 22):
        for v in (6, 19):
            if (u, v) not in COLS:
                bput(u, v, CL - 1, log("dark_oak", "x"))
    for v in range(1, 25):
        for u in (12, 17):
            if (u, v) not in COLS and v not in (6, 19):
                bput(u, v, CL - 1, log("dark_oak", "z"))
    # 柱に下げた赤い布（身廊の側。参考画像 casino_01）
    for (u, v) in COLS:
        side = "south" if v == 6 else "north"
        banner(u, v + (1 if v == 6 else -1), CL - 2, side, "red", [("bo", "yellow"), ("mr", "yellow")])
    roulette()
    chandelier()
    bar()
    lounge()
    card_bays()
    vip_stage()
    # 側廊の灯り（梁から吊る）と、身廊の四隅の灯り
    for (u, v) in ((10, 3), (10, 22), (19, 22), (15, 22), (9, 9), (9, 16), (20, 9), (20, 16)):
        hang(u, v, CL - 1, chain=1)


def roulette():
    """ルーレットの卓（直径6の丸。外の輪は緑の布の縁、内の輪は12の目、真ん中は金の軸）"""
    for u in range(12, 18):
        for v in range(10, 16):
            d = tdist(u, v)
            if d > 3.0:
                continue
            if d <= 1.0:
                bput(u, v, FT, block("chiseled_sandstone"))
                bput(u, v, FT + 1, block("light_weighted_pressure_plate", power=0))
            elif (u, v) in POCKETS:
                bput(u, v, FT, block("polished_blackstone"))
                carpet(u, v, POCKET_COLORS[POCKETS.index((u, v))], FT + 1)
            else:
                bput(u, v, FT, block("dark_oak_planks"))
                carpet(u, v, "green", FT + 1)


def chandelier():
    """ルーレットの真上の大きな吊り灯（外の輪 6×6 と内の輪 4×4 の濃い木の柵、鎖、ランタン、ろうそく）"""
    y_out, y_in = CL - 2, CL - 3          # 外の輪 +7、内の輪 +6
    for u in range(12, 18):
        for v in range(10, 16):
            on_out = (u in (12, 17) or v in (10, 15))
            on_in = 13 <= u <= 16 and 11 <= v <= 14 and (u in (13, 16) or v in (11, 14))
            if on_out and (u, v) not in ((12, 10), (17, 10), (12, 15), (17, 15)):
                bput(u, v, y_out, fence("dark_oak"))
            if on_in:
                bput(u, v, y_in, fence("dark_oak"))
    for (u, v) in ((12, 11), (17, 11), (12, 14), (17, 14)):
        bput(u, v, CL - 1, block("chain", axis="y", waterlogged=False))
    for (u, v) in ((13, 11), (16, 11), (13, 14), (16, 14)):
        bput(u, v, CL - 1, block("chain", axis="y", waterlogged=False))
        bput(u, v, y_out, block("chain", axis="y", waterlogged=False))
        bput(u, v, y_in - 1, lantern(True))
    for (u, v) in ((14, 10), (15, 10), (14, 15), (15, 15), (12, 12), (12, 13), (17, 12), (17, 13)):
        bput(u, v, y_out - 1, lantern(True))
    for (u, v) in ((13, 10), (16, 10), (13, 15), (16, 15), (12, 11), (17, 14)):
        candle(u, v, y_out + 1, 3)
    for u in (14, 15):
        for v in AX:
            bput(u, v, CL - 1, block("chain", axis="y", waterlogged=False))
            bput(u, v, y_out, lantern(True))


def bar():
    """北の側廊の酒場（u 13..21）：奥の棚（樽と壺とろうそく）、係の通り（v 2）、台（v 3）。立ち飲み"""
    for u in range(13, 22):
        if u in (17,):
            continue
        barrel(u, 1, FT, "south")
        bput(u, 1, FT + 1, slab("spruce", "bottom"))
        if u % 2 == 0:
            bput(u, 1, FT + 2, block("decorated_pot", facing="south", waterlogged=False))
        else:
            candle(u, 1, FT + 2, 2)
    bput(17, 1, FT, block("brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=False))
    for u in range(14, 21):
        bput(u, 3, FT, block("dark_oak_planks"))
    bput(13, 3, FT, block("spruce_fence_gate", facing="east", in_wall=False, open=False, powered=False))
    bput(21, 3, FT, block("dark_oak_planks"))
    for u in (15, 18, 20):
        candle(u, 3, FT + 1, 1)
    bput(16, 3, FT + 1, block("potted_azalea_bush"))
    # 働く人の扉（奥の北の働く人の場所へ。u 22、v 2）
    one_door(22, 2, FT, "west", wood="spruce")
    for u in (15, 19):
        hang(u, 3, CL - 1, chain=1)


def lounge():
    """北の側廊の西の間（u 8..11）：休み場。窓の下の長椅子と、低い卓、腰かけ"""
    for u in range(8, 12):
        bput(u, 1, FT, stairs("dark_oak", "north"))
    bput(9, 3, FT, block("dark_oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False))
    bput(10, 3, FT, block("dark_oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False))
    candle(9, 3, FT + 1, 2)
    chair(9, 4, FT, "south", "dark_oak")
    chair(10, 4, FT, "south", "dark_oak")
    bput(8, 4, FT, block("potted_azalea_bush"))
    bput(11, 4, FT, block("potted_flowering_azalea_bush"))


def card_bays(after=False, target=None):
    """南の側廊のカードの卓（2×2、v 22..23）。係は卓の奥（v 24）。
    はじめ（after=False）：布（白い絨毯）をかけた卓と、鎖の仕切り、準備中の札。共同事業②のあと：緑の布と、カードと札"""
    P = target or bput
    for (u0, u1) in CARD_BAYS:
        for u in (u0, u1):
            for v in (22, 23):
                P(u, v, FT, block("dark_oak_planks"))
        if not after:
            for u in (u0, u1):
                for v in (22, 23):
                    P(u, v, FT + 1, block("white_carpet"))
            for u in (u0 - 1, u1 + 1):
                P(u, 21, FT, fence("dark_oak"))
                P(u, 21, FT + 1, lantern(False))
            for u in (u0, u1):
                P(u, 21, FT, block("chain", axis="x", waterlogged=False))
            P(u0, 24, FT + 2, block("spruce_wall_sign", facing="north", waterlogged=False),
              sign_nbt(["ハイ＆ロー", "ただいま準備中", "（共同事業②）", ""]))
        else:
            for u in (u0, u1):
                P(u, 21, FT, AIR)
                P(u, 22, FT + 1, block("green_carpet"))
            for u in (u0 - 1, u1 + 1):
                P(u, 21, FT, AIR)
                P(u, 21, FT + 1, AIR)
            P(u0, 24, FT + 2, block("spruce_wall_sign", facing="north", waterlogged=False),
              sign_nbt(["ハイ＆ロー", "次のカードは", "上か下か", ""]))
            P(u1, 24, FT + 2, block("spruce_wall_sign", facing="north", waterlogged=False),
              sign_nbt(["4人から", "卓を囲もう", "", ""]))


def vip_stage():
    """奥の壁（u 22）：VIPルームへのアーチ（v 11..14、上の角を丸める）を、舞台のように飾る（金の柱と帯、赤と紫の布）"""
    for v in range(11, 15):
        for y in range(FT, FT + 5):
            if y == FT + 4 and v in (11, 14):
                continue
            bput(22, v, y, AIR)
    for v in (10, 15):
        for y in range(FT, CL - 1):
            bput(22, v, y, block("chiseled_sandstone") if y == FT else block("cut_sandstone"))
    for v in range(10, 16):
        bput(22, v, CL - 1, block("chiseled_sandstone"))
    for v in (11, 14):
        bput(22, v, FT + 4, block("cut_sandstone"))
    for v in (8, 9):
        banner(21, v, FT + 4, "west", "red", [("bo", "yellow"), ("cbo", "red")])
    for v in (16, 17):
        banner(21, v, FT + 4, "west", "red", [("bo", "yellow"), ("cbo", "red")])
    wall_sign(21, 12, CL - 1, "west", ["", "VIP", "ルーム", ""])
    wall_sign(21, 13, CL - 1, "west", ["4〜6人の組で", "貸し切り", "", ""])
    for v in (10, 15):
        bput(21, v - (1 if v == 10 else -1), FT, block("potted_flowering_azalea_bush"))


# ---------------------------------------------------------------- 1階の奥：VIPルーム・働く人の場所・2階への階段・暖炉の煙突
CHIMNEY = (24, 27, 17, 19)       # 暖炉の煙突の石（u 24..27、v 17..19）。煙の通り道は u 25..26、v 18


def chimney():
    """VIPルーム（1階）とジゼルの部屋（2階）の暖炉と、その煙突（土台から +21 まで。奥の棟の棟 +19 より上）"""
    u0, u1, v0, v1 = CHIMNEY
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            for y in range(-3, 22):
                flue = u in (25, 26) and v == 18 and y >= FT
                bput(u, v, y, AIR if flue else (block("polished_andesite") if y == 21 else block("stone_bricks")))
    for (fl, ft) in ((FL, FT), (CL, FT2)):
        for u in (25, 26):
            bput(u, 18, fl, block("polished_deepslate"))
            bput(u, 18, ft, block("campfire", facing="north", lit=True, signal_fire=False, waterlogged=False))
            bput(u, 17, ft, AIR)
            bput(u, 17, ft + 1, AIR)
            bput(u, 17, ft + 2, stairs("stone_brick", "south", "top"))
        for u in (24, 27):
            candle(u, 17, ft + 2, 2)


def vip_room():
    """VIPルーム（u 23..28、v 11..16）：組で貸し切る卓（緑の布、6つの椅子）、東の長椅子、暖炉、酒の棚。
    北の本棚の壁（v 10）の東の端の扉から、秘密の階段（地下へ）"""
    for u in range(23, 29):
        for v in range(11, 18):
            if not (24 <= u <= 27 and v == 17):
                bput(u, v, FL, block("dark_oak_planks"))
    for u in range(24, 28):
        for v in range(12, 16):
            carpet(u, v, "red")
    for u in (25, 26):
        for v in (13, 14):
            bput(u, v, FT, block("dark_oak_planks"))
            carpet(u, v, "green", FT + 1)
    for (u, v, f) in ((24, 13, "west"), (24, 14, "west"), (27, 13, "east"), (27, 14, "east"), (25, 12, "north"),
                      (26, 15, "south")):
        bput(u, v, FT, stairs("dark_oak", f))
    for v in (15, 16):
        bput(28, v, FT, stairs("dark_oak", "east"))
    barrel(23, 16, FT, "up")
    barrel(23, 17, FT, "up")
    bput(23, 16, FT + 1, block("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
    bput(28, 17, FT, block("potted_azalea_bush"))
    # 北の本棚の壁（v 10）と、東の端の扉（秘密の階段へ）
    for u in range(23, 29):
        for y in range(FT, CL):
            bput(u, 10, y, block("bookshelf") if y <= FT + 2 else block("calcite"))
    one_door(28, 10, FT, "north", wood="dark_oak")
    for (u, v) in ((25, 13), (26, 14)):
        hang(u, v, CL - 1, chain=1)
    for (u, v) in ((23, 12), (28, 14)):
        hang(u, v, CL - 1, chain=1)


def secret_stair():
    """秘密の階段（u 23..28、v 8..9）：VIPルームの本棚の扉（u 28、v 10）から入り、西へ3段で地下（足もと −1）へ"""
    for u in range(23, 29):
        for v in (8, 9):
            for y in range(BF + 1, CL):
                bput(u, v, y, AIR)
            bput(u, v, BF, block("polished_andesite"))
    for v in (8, 9):
        bput(28, v, FL, block("dark_oak_planks"))          # いちばん上（足もと +3）
        bput(28, v, 1, block("stone_bricks"))
        bput(28, v, 0, block("stone_bricks"))
        bput(28, v, -1, block("stone_bricks"))
        bput(27, v, 1, stairs("stone_brick", "east"))
        bput(27, v, 0, block("stone_bricks"))
        bput(27, v, -1, block("stone_bricks"))
        bput(26, v, 0, stairs("stone_brick", "east"))
        bput(26, v, -1, block("stone_bricks"))
        bput(25, v, -1, stairs("stone_brick", "east"))
    for v in (8, 9):                                   # 地下の西の端の壁（大広間の土台の下）
        for y in range(BT, FL):
            bput(22, v, y, block("stone_bricks"))
    hang(26, 9, CL - 1, chain=3)


def staff_back():
    """奥の北（u 23..28、v 1..6）：働く人の場所。地下室への階段（v 1..2、東へ下りる）と柵、裏口（u 29、v 5）への通り、
    酒と道具の樽（鍵）"""
    for u in range(23, 29):
        for v in range(1, 7):
            bput(u, v, FL, block("spruce_planks"))
    for v in (1, 2):
        for u in range(25, 29):
            for y in range(BF + 1, CL):
                bput(u, v, y, AIR)
        bput(25, v, 1, stairs("stone_brick", "west"))
        bput(25, v, 0, block("stone_bricks"))
        bput(26, v, 0, stairs("stone_brick", "west"))
        bput(26, v, -1, block("stone_bricks"))
        bput(27, v, -1, stairs("stone_brick", "west"))
        bput(27, v, BF, block("stone_bricks"))
        bput(28, v, BF, block("stone_bricks"))
    for u in range(25, 29):
        bput(u, 3, FT, fence("spruce"))
    for u in (23, 24, 25):
        barrel(u, 6, FT, "up")
    barrel(23, 6, FT + 1, "up")
    bput(26, 6, FT, block("crafting_table"))
    for u in (27, 28):
        bput(u, 6, FT, block("dark_oak_planks"))
    candle(28, 6, FT + 1, 1)
    bput(27, 6, FT + 1, block("decorated_pot", facing="north", waterlogged=False))
    hang(24, 4, CL - 1, chain=1)
    hang(27, 5, CL - 1, chain=1)


def public_stair():
    """奥の南（u 23..28、v 19..24）：2階への階段。大広間の南の側廊からアーチ（u 22、v 22..24）で入り、
    v 23..24 を東へ4段（u 24..27、足もと +7）、踊り場（u 28、v 21..24）、v 21..22 を西へ3段（u 27..25）で2階（足もと +10）"""
    for u in range(23, 29):
        for v in range(19, 25):
            if not (24 <= u <= 27 and v == 19):
                bput(u, v, FL, block("stone_bricks"))
    for v in (22, 23, 24):
        for y in range(FT, FT + 4):
            bput(22, v, y, AIR)
    for v in (23, 24):
        for i, u in enumerate((24, 25, 26, 27)):
            bput(u, v, FT + i, stairs("spruce", "east"))
            for y in range(FT, FT + i):
                bput(u, v, y, block("spruce_planks"))
    for v in range(21, 25):
        for y in range(FT, FT + 4):
            bput(28, v, y, block("spruce_planks"))
    for v in (21, 22):
        for i, u in enumerate((27, 26, 25)):
            bput(u, v, FT + 4 + i, stairs("spruce", "west"))
            for y in range(FT, FT + 4 + i):
                bput(u, v, y, block("spruce_planks"))
    # 2階の床の穴（上り口の頭の上）と手すり
    for u in (26, 27):
        for v in (21, 22):
            bput(u, v, CL, AIR) if u == 26 else None
    for v in (21, 22):
        for u in (26, 27, 28):
            bput(u, v, CL, AIR)
    for u in (26, 27, 28):
        bput(u, 20, FT2, fence("spruce"))
        bput(u, 23, FT2, fence("spruce"))
    hang(25, 23, CL - 1, chain=1)
    wall_sign(21, 23, FT + 4, "west", ["↑ 2階", "宴の間", "", ""])


# ---------------------------------------------------------------- 2階：宴の間・帳場・支配人室・働く人の部屋・ジゼルの部屋・催しの道具の部屋
STAGE = (19, 21, 10, 15)         # 宴の間の東の小さな舞台（u 19..21、v 10..15、床の上の半ブロック）


def inner_gables():
    """真ん中の帯の屋根の下を、支配人室・宴の間・ジゼルの部屋で区切る壁（u 7・22 の、屋根の下の三角）"""
    for (y, lo, hi) in gable_rows(band, R0):
        for Zc in range(lo, hi + 1):
            v = Zc + Z0 - BZ
            for u in (7, 22):
                if bget(u, v, y) is None:
                    bput(u, v, y, beam("z") if (y - R0) % 4 == 0 else block("calcite"))


def banquet():
    """宴の間（u 8..21、v 1..24、床 +9）：屋根の小屋組みが見える高い部屋。北と南に長い卓、真ん中は空けて（人が集まる所。
    ⑥のあとは魚レース）、東に小さな舞台。西の壁ぎわに長椅子"""
    for u in range(8, 22):
        for v in range(1, 25):
            border = u in (8, 21) or v in (1, 24)
            bput(u, v, CL, block("dark_oak_planks") if border else block("spruce_planks"))
    # 小屋組み：柱の列（v 6・19）の上に、陸梁（+14）と真束。柱は2階も通す
    for v in (6, 19):
        for u in range(8, 22):
            bput(u, v, EV, log("dark_oak", "x"))
        for u in (14, 15):
            for y in range(R0, R0 + 4):
                if bget(u, v, y) is None:
                    bput(u, v, y, log("dark_oak", "y"))
    for (u, v) in COLS:
        for y in range(FT2, EV):
            bput(u, v, y, log("dark_oak", "y"))
    # 長い卓（白い布、ろうそく）と椅子
    for (vt, vn, vs) in ((3, 2, 4), (22, 21, 23)):
        for u in range(10, 18):
            bput(u, vt, FT2, block("dark_oak_planks"))
            if u % 3:
                bput(u, vt, FT2 + 1, block("white_carpet"))
            else:
                candle(u, vt, FT2 + 1, 3)
            if u not in (13, 14):
                bput(u, vn, FT2, stairs("spruce", "north"))
                bput(u, vs, FT2, stairs("spruce", "south"))
    # 西の壁ぎわの長椅子と低い卓（舞台の方を向く）
    for v in range(9, 17):
        if v not in AX:
            bput(8, v, FT2, stairs("dark_oak", "west"))
    for v in (10, 15):
        bput(9, v, FT2, trapdoor("dark_oak", "west", "top", False))
    # 小さな舞台（下付きの半ブロックの床、ふちは金色）と、後ろの壁の紫の布
    u0, u1, v0, v1 = STAGE
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            bput(u, v, FT2, slab("smooth_sandstone", "bottom") if u == u0 else slab("dark_oak", "bottom"))
    for v in (10, 15):
        banner(21, v, FT2 + 3, "west", "purple", [("bo", "yellow"), ("mc", "yellow")])
    # 扉：帳場（u 7、v 5）・支配人室（u 7、v 12..13）・働く人の部屋（u 7、v 20）・催しの道具の部屋（u 22、v 3）・
    # ジゼルの部屋（u 22、v 16。舞台の南の脇）・階段の踊り場（u 22、v 21..22。口）
    one_door(7, 5, FT2, "west")
    door_pair(7, 12, FT2, "west", "v", wood="dark_oak")
    one_door(7, 20, FT2, "west")
    one_door(22, 3, FT2, "east")
    one_door(22, 16, FT2, "east", wood="dark_oak")
    for v in (21, 22):
        for y in range(FT2, FT2 + 3):
            bput(22, v, y, AIR)
    # 灯り：陸梁から鎖で吊る灯り
    for (u, v) in ((11, 6), (18, 6), (11, 19), (18, 19)):
        hang(u, v, EV - 1, chain=1)
    for v in (3, 22):
        hang_down(9, v, FT2 + 3)
        hang_down(20, v, FT2 + 3)


def front_rooms_2f():
    # 帳場（u 1..6、v 1..8。北西の小塔の張り出しつき）：勘定の机、帳面、金庫、硬貨の樽（鍵）
    for (u, v) in ((0, 0), (0, 1), (1, 0)):
        bput(u, v, CL, block("spruce_planks"))
    for u in (2, 3, 4):
        bput(u, 4, FT2, block("dark_oak_planks"))
    bput(3, 4, FT2 + 1, block("lectern", facing="north", has_book=True, powered=False),
         lectern_nbt("勘定の帳面", "帳場", ["胴元の取り分は、賭けのおよそ3%。\n\nカジノナイトのおまけは、別の欄に書くこと。"]))
    candle(2, 4, FT2 + 1, 2)
    candle(4, 4, FT2 + 1, 1)
    chair(3, 5, FT2, "south", "dark_oak")
    bput(6, 1, FT2, block("iron_block"))
    for v in (6, 7, 8):
        barrel(1, v, FT2, "east")
    bput(0, 0, FT2, stairs("spruce", "north"))
    hang(3, 3, EV - 1, chain=1)
    hang(4, 7, EV - 1, chain=1)
    # 支配人室（u 1..6、v 10..15。屋根の下まで高く、「運命の輪」の丸窓）：ジゼルの机、本棚、長椅子、表の露台への扉
    for u in range(1, 7):
        for v in range(10, 16):
            bput(u, v, CL, block("dark_oak_planks"))
            bput(u, v, EV, AIR)
    for u in range(1, 6):
        for v in AX:
            carpet(u, v, "purple", FT2)
    for v in (11, 12, 13, 14):
        bput(4, v, FT2, block("dark_oak_planks"))
        bput(4, v, FT2 + 1, AIR)
    candle(4, 11, FT2 + 1, 3)
    bput(4, 14, FT2 + 1, block("potted_flowering_azalea_bush"))
    chair(5, 12, FT2, "east", "dark_oak")
    for v in range(10, 16):
        if v not in AX:
            bput(6, v, FT2, block("bookshelf"))
            bput(6, v, FT2 + 1, block("bookshelf"))
    for v in (10, 15):
        bput(1, v, FT2, stairs("dark_oak", "north" if v == 10 else "south"))
    for v in AX:
        hang_down(3, v, FT2 + 3)
    wall_sign(1, 11, FT2 + 2, "east", ["支配人", "ジゼル・ノワール", "", ""])
    # 働く人の部屋（u 1..6、v 17..24。南西の小塔の張り出しつき）：係の服の防具立て、腰かけ、卓、洗い場、樽（鍵）
    for (u, v) in ((0, 24), (0, 25), (1, 25)):
        bput(u, v, CL, block("spruce_planks"))
    black = 0x1D1D21
    stand(2, 18, FT2, "south", chest=("leather_chestplate", {"color": black}), legs=("leather_leggings", {"color": black}),
          feet=("leather_boots", {"color": black}), head=("leather_helmet", {"color": 0x7F3FB2}))
    stand(4, 18, FT2, "south", chest=("leather_chestplate", {"color": black}), legs=("leather_leggings", {"color": black}),
          feet=("leather_boots", {"color": black}))
    for u in (3, 4, 5):
        bput(u, 22, FT2, block("spruce_planks"))
    candle(4, 22, FT2 + 1, 2)
    for u in (3, 5):
        chair(u, 23, FT2, "south")
    chair(4, 21, FT2, "north")
    bput(6, 24, FT2, block("water_cauldron", level=3))
    for v in (20, 21):
        barrel(1, v, FT2, "east")
    bput(0, 25, FT2, stairs("spruce", "south"))
    hang(3, 20, EV - 1, chain=1)
    hang(4, 23, EV - 1, chain=1)


def back_rooms_2f():
    # 催しの道具の部屋（u 23..28、v 1..6）：カジノナイトの道具。蓄音機と音符ブロック、旗を織る機、樽（鍵）、重ねた腰かけ
    bput(28, 1, FT2, block("jukebox", has_record=False))
    bput(27, 1, FT2, block("note_block", instrument="harp", note=0, powered=False))
    bput(28, 2, FT2, block("note_block", instrument="bell", note=12, powered=False))
    bput(23, 1, FT2, block("loom", facing="south"))
    for u in (24, 25):
        barrel(u, 1, FT2, "up")
    for v in (5, 6):
        barrel(28, v, FT2, "west")
    bput(26, 6, FT2, stairs("spruce", "south"))
    bput(26, 6, FT2 + 1, stairs("spruce", "south"))
    hang(25, 4, EV - 1, chain=1)
    # ジゼルの部屋（u 23..28、v 8..17。屋根の下まで高い）：紫の寝台、衣装の棚、化粧の台、暖炉（2階）、東の窓
    for u in range(23, 29):
        for v in range(8, 18):
            if not (24 <= u <= 27 and v == 17):
                bput(u, v, CL, block("dark_oak_planks"))
            if 9 <= v <= 16:
                bput(u, v, EV, AIR)
    for u in range(24, 28):
        for v in range(11, 16):
            carpet(u, v, "purple", FT2)
    bput(28, 9, FT2, block("purple_bed", facing="north", part="head", occupied=False))
    bput(28, 10, FT2, block("purple_bed", facing="north", part="foot", occupied=False))
    bput(27, 8, FT2, block("dark_oak_planks"))
    candle(27, 8, FT2 + 1, 1)
    for u in (23, 24):
        for y in (FT2, FT2 + 1):
            bput(u, 8, y, block("spruce_planks"))
            bput(u, 9, y, trapdoor("spruce", "north", "bottom", True))
    bput(28, 14, FT2, block("dark_oak_planks"))
    bput(28, 14, FT2 + 1, block("potted_flowering_azalea_bush"))
    for (u, v) in ((24, 16), (27, 16)):
        chair(u, v, FT2, "north", "dark_oak")
    for v in AX:
        hang_down(25, v, FT2 + 3)


# ---------------------------------------------------------------- 地下（秘密の階）：噂の間・初代の金庫・酒と道具の地下室
def room_box(u0, u1, v0, v1, floor, wallf):
    """地下の部屋：床（−2）、中（−1..1）を空け、まわりの壁（土のまま・空いた所）を wallf で置き直す"""
    for u in range(u0 - 1, u1 + 2):
        for v in range(v0 - 1, v1 + 2):
            inside = u0 <= u <= u1 and v0 <= v <= v1
            for y in range(BF, FL):
                if inside:
                    bput(u, v, y, floor() if y == BF else AIR)
                elif bname(u, v, y) in ("dirt", "stone_bricks") and not ext_wall(u, v):
                    bput(u, v, y, wallf())


def basement():
    # 酒と道具の地下室（u 23..28、v 1..6）：働く人の階段の下。寝かせた酒の樽（鍵）、台、鉄格子の小窓
    room_box(23, 28, 1, 6, lambda: block("stone_bricks"), gray)
    for v in (1, 2):
        bput(25, v, 1, stairs("stone_brick", "west"))
        bput(25, v, 0, block("stone_bricks"))
        bput(25, v, -1, block("stone_bricks"))
        bput(26, v, 0, stairs("stone_brick", "west"))
        bput(26, v, -1, block("stone_bricks"))
        bput(27, v, -1, stairs("stone_brick", "west"))
        for u in (23, 24):
            for y in (-1, 0, 1):
                bput(u, v, y, block("stone_bricks"))
    for v in (4, 5, 6):
        barrel(23, v, BT, "east")
        barrel(23, v, BT + 1, "east")
    for u in (25, 26, 27):
        barrel(u, 6, BT, "north")
    bput(28, 5, BT, block("dark_oak_planks"))
    candle(28, 5, BT + 1, 2)
    hang(25, 4, 1, chain=0)
    hang(28, 3, 1, chain=0)
    # 秘密の階段の下（u 23..24、v 8..9）から、噂の間への扉（u 24、v 10）
    one_door(24, 10, BT, "south", wood="dark_oak")
    # 噂の間（u 23..28、v 11..16）：古代の白い石の壁。丸い卓、地図の壁、噂の帳面、青い灯（魂のランタン）
    room_box(23, 28, 11, 16, lambda: block("polished_deepslate"), white)
    for u in (25, 26):
        for v in (13, 14):
            bput(u, v, BT, block("dark_oak_planks"))
    candle(25, 13, BT + 1, 3)
    candle(26, 14, BT + 1, 2)
    for (u, v, f) in ((24, 13, "west"), (24, 14, "west"), (27, 13, "east"), (27, 14, "east")):
        bput(u, v, BT, stairs("dark_oak", f))
    for v in (12, 13, 14, 15):
        for y in (BT, BT + 1):
            frame(28, v, y, "west", "map" if (v + y) % 2 else "paper")
    bput(23, 16, BT, block("lectern", facing="east", has_book=True, powered=False),
         lectern_nbt("噂の帳面", "ジゼル", [
             "噂は、ここに集まる。\n\n街の噂、塔の噂、ほかの冒険者の噂。だれが何を見たかは、上の卓で、わたしに聞くこと。",
             "噂は、半分が本当で、半分が願い。\n\nどちらの半分かを見分けるのは、聞いた人の仕事。\n――ジゼル",
         ]))
    for (u, v) in ((23, 11), (28, 16)):
        bput(u, v, BT, block("soul_lantern", hanging=False, waterlogged=False))
    hang(25, 12, 1, chain=0)
    hang(26, 15, 1, chain=0)
    # 初代の金庫（u 17..21、v 10..15。大広間の下）：閉じた鉄の扉（u 22、v 12）と、のぞける鉄格子の窓。金と、空のお守りの台
    room_box(17, 21, 10, 15, lambda: block("polished_andesite"), lambda: block("quartz_bricks"))
    for y in (BT, BT + 1):
        bput(22, 12, y, block("iron_door", facing="west", half="lower" if y == BT else "upper", hinge="left",
                                open=False, powered=False))
    for v in (14, 15):
        bput(22, v, BT + 1, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    bput(18, 12, BT, block("chiseled_quartz_block"))
    bput(18, 13, BT, block("chiseled_quartz_block"))
    wall_sign(17, 12, BT + 1, "east", ["初代のお守りは", "ここにあった", "", "0に三度愛された人へ"])
    for (u, v) in ((17, 10), (17, 15), (21, 10)):
        bput(u, v, BT, block("gold_block"))
    for v in (10, 11):
        barrel(19, v, BT, "up")
        barrel(20, v, BT, "up")
    hang(19, 14, 1, chain=0)
    hang(20, 11, 1, chain=0)


# ---------------------------------------------------------------- まわりと飾り（前の小広場・北の路地・東の裏の通路・南の小道。飾りは最後）
FLAG = [("mr", "yellow"), ("cbo", "black"), ("bo", "white")]     # カジノの旗：紫の地、黄の菱形、黒の飾り縁、白い縁（⑦）


def tree(x, z, h=5, kind="oak"):
    """オークかトウヒの木（城壁沿いの空き地。都市のルール⑦）"""
    for y in range(1, h + 1):
        put(x, z, y, log(kind, "y"))
    top = h + 1
    leaf = leaves(kind + "_leaves")
    for dy, r in ((-2, 2), (-1, 2), (0, 1), (1, 1)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and r == 2:
                    continue
                if (dx, dz) == (0, 0) and dy < 1:
                    continue
                if not outside_wall(x + dx, z + dz) and get(x + dx, z + dz, top + dy) is None:
                    put(x + dx, z + dz, top + dy, leaf)
    put(x, z, top + 1, leaf)


def surroundings():
    # ---- 前の小広場：入口の両脇の花壇（正面の壁ぎわ、窓の下）と、再建のランタン灯（両腕）2本、腰かけ、案内の立て札
    for z0 in (73, 77, 90, 94):
        for z in range(z0, z0 + 3):
            put(FRONT_X, z, 0, grass())
            put(FRONT_X, z, 1, block(("allium", "poppy", "oxeye_daisy")[(z - z0) % 3]) if z0 in (77, 90)
                else block("flowering_azalea"))
    for z in (80, 89):
        post_lamp(8, z, arms=("north", "south"))
    for z in (73, 74, 95, 96):
        put(6, z, 1, stairs("spruce", "west"))
    for z in (72, 75, 94, 97):
        put(6, z, 1, block("potted_azalea_bush"))
    put(6, 82, 1, fence("spruce"))
    put(6, 82, 2, block("spruce_sign", rotation=4, waterlogged=False),
        sign_nbt(["遊戯館", "ラッキーノット", "ひらいています", "（3日目から）"], ["→ 市場通り", "← 正面の入口", "", ""]))
    # 吊り看板（露台の北の柱から、道の方へ）と、その下の品（金のリンゴ。⑦）
    hanging_sign(-2, 9, FT + 1, "west", ["遊戯館", "ラッキーノット", "", ""])
    frame(-2, 9, FT, "north", "golden_apple")
    # 旗（3枚。⑦の「1施設に1〜3枚」）：両端の小塔の頂（道の方を向く）と、南の面（南門から見える所）
    for (u, v) in ((0, 0), (0, 25)):
        bput(u, v, 23, block("purple_banner", rotation=4), banner_nbt(FLAG))
    banner(5, 26, FT + 3, "south", "purple", FLAG)
    post_lamp(11, 71, arms=("north", "west"))
    post_lamp(14, 101, arms=("north", "east"))
    # ---- 北の路地（z 68..70）：北の面の壁のランタン
    for u in (5, 15, 20, 26):
        wall_lamp(u, 0, FT + 3, "north")
    # ---- 東の裏の通路（x 43..46）：裏口の壁のランタン、荷の樽と木箱、手押し車
    wall_lamp(29, 6, FT + 3, "east")
    for (x, z) in ((43, 79), (43, 80)):
        put(x, z, 1, block("barrel", facing="up", open=False),
            Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)}))
    put(43, 81, 1, block("spruce_planks"))
    put(43, 81, 2, trapdoor("spruce", "east", "top", False))
    for z in (84, 85):
        put(44, z, 1, slab("spruce", "top"))
        put(45, z, 1, trapdoor("dark_oak", "west", "bottom", True))
    put(44, 84, 2, block("hay_block", axis="y"))
    post_lamp(46, 90, arms=("west",))
    wall_lamp(29, 21, FT + 3, "east")
    # ---- 南の小道（z 98..99）：南の面の壁のランタン、ランタン灯、木（城壁沿いの空き地）と腰かけ
    for u in (9, 19):
        wall_lamp(u, 25, FT + 3, "south")
    post_lamp(31, 100, arms=("west", "east"))
    tree(21, 102, 5, "oak")
    tree(27, 100, 6, "spruce")
    for x in (17, 18):
        put(x, 100, 1, stairs("spruce", "south"))


# ---------------------------------------------------------------- 寄付で足す遊び（重ねる形。都市のルール⑩の「育つ前・育った後」）
HL = World(W, H, D)              # 共同事業②「ハイ＆ロー」：南の側廊のカードの卓
HL_OWNED = set()
FR = World(W, H, D)              # 共同事業⑥「魚レース」：宴の間の真ん中の水の走路
FR_OWNED = set()
RACE = (9, 19, 9, 17)            # 魚レースの台（u 9..19、v 9..17）。走路は v 10・12・14・16、仕切りは v 11・13・15
LANE_COLORS = ("red", "yellow", "lime", "light_blue")


def overlay_put(world, owned):
    def p(u, v, y, state, nbt=None):
        x, z = B(u, v)
        X, Y, Z = x - X0, y + YOFF, z - Z0
        world.put(X, Y, Z, state, nbt)
        owned.add((X, Y, Z))
    return p


def overlay_frame(world, owned, u, v, y, facing, item, name=None):
    x, z = B(u, v)
    world.item_frame(x - X0, y + YOFF, z - Z0, facing, item, name=name)
    owned.add((x - X0, y + YOFF, z - Z0))


def highlow_overlay():
    """共同事業②のあと：布をはずした卓（緑の布）、鎖の仕切りをはずし、札をかけ替え、卓にカードとチップ（額縁）"""
    hput = overlay_put(HL, HL_OWNED)
    card_bays(after=True, target=hput)
    for (u0, u1) in CARD_BAYS:
        for (u, item, nm) in ((u0, "paper", "カード"), (u1, "gold_nugget", "チップ")):
            hput(u, 23, FT + 1, AIR)
            overlay_frame(HL, HL_OWNED, u, 23, FT + 1, "up", item, name=nm)


def fishrace_overlay():
    """共同事業⑥のあと：宴の間の真ん中に、魚を走らせる水の走路（4本、長さ9）。西が出発、東の金の壁がゴール（舞台が見張りの台）"""
    fput = overlay_put(FR, FR_OWNED)
    u0, u1, v0, v1 = RACE
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            for y in (FT2, FT2 + 1):
                fput(u, v, y, AIR)
            if u == u1:
                fput(u, v, FT2, block("chiseled_sandstone") if v in AX else block("cut_sandstone"))
            elif u == u0 or v in (9, 11, 13, 15, 17):
                fput(u, v, FT2, block("polished_andesite") if v in (9, 17) or u == u0 else block("polished_deepslate"))
            else:
                fput(u, v, FT2, block("water", level=0))
    # 出発の印（走路ごとの色のろうそく。1〜4番）と、四隅の灯り、ゴールの札
    for i, v in enumerate((10, 12, 14, 16)):
        fput(u0, v, FT2 + 1, block(LANE_COLORS[i] + "_candle", candles=1, lit=True, waterlogged=False))
    for (u, v) in ((u0, v0), (u0, v1), (u1, v0), (u1, v1)):
        fput(u, v, FT2 + 1, fence("dark_oak"))
        fput(u, v, FT2 + 2, lantern(False))
    fput(u1, 13, FT2 + 1, block("spruce_sign", rotation=4, waterlogged=False), sign_nbt(["", "ゴール", "", ""]))
    # 舞台の後ろの壁に、遊び方の札
    fput(21, 12, FT2 + 2, block("spruce_wall_sign", facing="west", waterlogged=False),
         sign_nbt(["魚レース", "釣った魚を", "走らせて", "賭ける"]))
    fput(21, 13, FT2 + 2, block("spruce_wall_sign", facing="west", waterlogged=False),
         sign_nbt(["1番 赤", "2番 黄", "3番 緑", "4番 水色"]))


def checked_world(which="base"):
    """確かめと見本の絵に使う写し：base（はじめ）・highlow（②のあと）・fishrace（⑥のあと）・both（両方のあと）"""
    layers = []
    if which in ("highlow", "both"):
        layers.append((HL, HL_OWNED))
    if which in ("fishrace", "both"):
        layers.append((FR, FR_OWNED))
    return overlaid(w, *layers) if layers else w


def overlaid(base, *layers):
    """重ねる形を、はじめの形の上に置いたあとの写し（見本の絵と確かめのため）"""
    cw = World(W, H, D)
    cw.blocks = dict(base.blocks)
    cw.nbt = dict(base.nbt)
    cw.entities = list(base.entities)
    for (lw, owned) in layers:
        for c in owned:
            if c in lw.blocks:
                cw.blocks[c] = lw.blocks[c]
                if c in lw.nbt:
                    cw.nbt[c] = lw.nbt[c]
                else:
                    cw.nbt.pop(c, None)
            else:
                cw.blocks.pop(c, None)
                cw.nbt.pop(c, None)
        cw.entities += list(lw.entities)
    return cw


def cut_world(world, y_cut):
    """y_cut（道からの高さ）より上を取りのぞいた写し（真上からの絵のため）"""
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut + YOFF:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut + YOFF]
    return cw


def render_quick(out, names=None, world=None):
    from mapgen.render import Scene
    os.makedirs(out, exist_ok=True)
    world = world or w
    for (wd, views) in ((world, VIEWS), (checked_world("highlow"), VIEWS_HIGHLOW),
                        (checked_world("fishrace"), VIEWS_FISHRACE)):
        todo = [k for k in views if not names or k in names]
        if not todo:
            continue
        sc = Scene(wd, ground_y=YOFF + 1)
        sc_low = None
        for k in todo:
            e, t = views[k]
            if e[1] < YOFF + 1 or t[1] < YOFF + 1:   # 地下の絵：見せかけの地面を、地下の床より下へ
                sc_low = sc_low or Scene(wd, ground_y=0)
                use = sc_low
            else:
                use = sc
            use.render(eye=e, target=t, width=960, height=540, fov=70, path=os.path.join(out, "casino_%s.png" % k))
            print("描きました:", k)
    for key, y_cut in (("cut0", 2), ("cut1", 9), ("cut2", 15)):
        if names and key not in names:
            continue
        cx, cz = BX + 15 - X0, BZ + 13 - Z0
        Scene(cut_world(world, y_cut), ground_y=0 if y_cut <= FL else YOFF + 1).render(
            eye=(cx, 110.0, cz + 0.01), target=(cx, 0.0, cz), width=900, height=900, fov=28,
            path=os.path.join(out, "casino_%s.png" % key))
        print("描きました:", key)


def S(x, y, z):
    """世界の座標 → 構造物の中の座標（見本の絵の目と見る所）"""
    return (x - X0, y + YOFF, z - Z0)


def Bv(u, y, v):
    """建物の中の (u, 高さ, v) → 構造物の中の座標"""
    return S(BX + u, y, BZ + v)


VIEWS = {
    "front": (S(-6.0, 2.62, 84.5), S(13.0, 12.0, 84.5)),          # 市場通りの向かい（本屋の前）から正面
    "gate": (S(-1.0, 2.62, 104.0), S(20.0, 10.0, 86.0)),          # 南門の内から（右手にカジノ）
    "north": (S(10.0, 6.0, 58.0), S(28.0, 10.0, 80.0)),           # 環状路の南の辺から、北の面と路地
    "aerial": (S(-4.0, 30.0, 110.0), S(27.0, 6.0, 84.0)),         # 上から（南西）
    "back": (S(60.0, 14.0, 66.0), S(30.0, 8.0, 86.0)),            # 北東から、背面と東の裏の通路
    "porch": (S(4.0, 3.62, 79.0), S(13.0, 6.5, 85.0)),            # 前の小広場から、入口（露台と柱、吊り看板）
    "vestibule": (Bv(1.5, FT + 1.62, 13.0), Bv(5.5, FT + 1.2, 8.0)),       # 入口の間：両替の窓口と、大広間へのアーチ
    "prize": (Bv(6.4, FT + 1.62, 17.6), Bv(1.5, FT + 0.8, 22.5)),          # 景品の交換所
    "in_hall": (Bv(7.5, FT + 1.62, 12.5), Bv(22.0, FT + 2.0, 12.5)),       # 大広間のアーチから、ルーレットと奥の舞台
    "in_bar": (Bv(10.5, FT + 1.62, 17.5), Bv(19.0, FT + 1.5, 2.0)),        # 身廊の南西から、北の酒場
    "in_cards": (Bv(19.5, FT + 1.62, 10.5), Bv(10.0, FT + 0.5, 23.0)),     # 身廊の北東から、南のカードの卓（準備中）
    "vip": (Bv(22.5, FT + 1.62, 12.5), Bv(28.5, FT + 0.6, 14.5)),          # 奥の舞台のアーチから、VIPルーム
    "secret": (Bv(28.5, FT + 1.62, 9.5), Bv(23.0, BT + 0.4, 8.6)),         # 本棚の扉の内から、秘密の階段
    "rumor": (Bv(23.6, BT + 1.62, 11.6), Bv(28.6, BT + 0.9, 14.0)),        # 噂の間：地図の壁と丸い卓
    "vault": (Bv(23.6, BT + 1.62, 15.4), Bv(17.5, BT + 0.4, 12.0)),        # 噂の間から、鉄格子ごしに初代の金庫
    "banquet": (Bv(8.6, FT2 + 1.62, 12.5), Bv(21.0, FT2 + 3.0, 12.5)),     # 宴の間（西から東の舞台）
    "office": (Bv(5.6, FT2 + 1.62, 12.5), Bv(0.5, FT2 + 5.5, 12.5)),       # 支配人室：「運命の輪」の丸窓
    "gisele": (Bv(23.5, FT2 + 1.62, 15.6), Bv(28.5, FT2 + 0.8, 9.0)),      # ジゼルの部屋
    "stair": (Bv(20.5, FT + 1.62, 23.4), Bv(27.0, FT + 4.0, 22.0)),        # 南の側廊から、2階への階段
}
VIEWS_HIGHLOW = {
    "highlow": (Bv(12.4, FT + 2.4, 20.6), Bv(9.6, FT + 0.6, 22.8)),        # 共同事業②のあと：ハイ＆ローの卓
}
VIEWS_FISHRACE = {
    "fishrace": (Bv(8.5, FT2 + 2.62, 12.5), Bv(19.5, FT2 + 0.2, 13.0)),    # 共同事業⑥のあと：魚レースの走路（出発の側から）
    "fishrace_side": (Bv(14.5, FT2 + 3.2, 19.5), Bv(14.5, FT2 + 0.2, 11.0)),  # 南の見物の所から
}


def build():
    rng.seed(1010)
    ground()
    shell()
    roofs()
    gables()
    turrets()
    fill_turret_tops()
    facade()
    entrance()
    cashier()
    prize_room()
    hall()
    chimney()
    vip_room()
    secret_stair()
    staff_back()
    public_stair()
    inner_gables()
    banquet()
    front_rooms_2f()
    back_rooms_2f()
    roof_boards()
    basement()
    surroundings()
    w.finalize()


build()
highlow_overlay()
HL.finalize()
fishrace_overlay()
FR.finalize()

CI_SHOTS = ["front", "gate", "aerial", "back", "in_hall", "in_bar", "vip", "rumor", "banquet", "highlow", "fishrace"]


def ci_views():
    """自動の確認の写真の目と見る所（カジノの地面の所から。構造物は y を YOFF だけ下げて、地面を草の高さにそろえて置く）。
    CiScreenshots の MAP_VIEWS と同じ。最後の2枚は、ハイ＆ロー・魚レースを重ねたあと"""
    allv = dict(VIEWS)
    allv.update(VIEWS_HIGHLOW)
    allv.update(VIEWS_FISHRACE)
    out = []
    for shot in CI_SHOTS:
        e, t = allv[shot]
        out.append((shot, (e[0], e[1] - YOFF, e[2]), (t[0], t[1] - YOFF, t[2])))
    return out


def owned(X, Y, Z):
    """この構造物が書くマス（何も置いていないマスは空気で書く）。書かない所：城壁沿いの道（半径107より外）、
    南門の前の東の区画（x 5..12、z 98..103）、茶と香辛料の店（商店街の南の東の角の店）の南の横の地面より上（z 68..69、x 5..17：
    横の入口の段・吊り看板・屋根の妻の出）"""
    x, y, z = X + X0, Y - YOFF, Z + Z0
    if outside_wall(x, z) or gate_block(x, z):
        return False
    if z <= 69 and x <= 17 and y >= 1:
        return False
    return True


def save_all(out):
    """3つの構造物を書き出す"""
    res = []
    path = os.path.join(out, "casino.nbt")
    n = w.save_structure(path, owned=owned)
    res.append((path, len(w.blocks), n, len(w.entities), (W, H, D)))
    path = os.path.join(out, "casino_highlow.nbt")
    n = HL.save_structure(path, owned=lambda x, y, z: (x, y, z) in HL_OWNED)
    res.append((path, len(HL_OWNED), n, len(HL.entities), (W, H, D)))
    path = os.path.join(out, "casino_fishrace.nbt")
    n = FR.save_structure(path, owned=lambda x, y, z: (x, y, z) in FR_OWNED)
    res.append((path, len(FR_OWNED), n, len(FR.entities), (W, H, D)))
    return res


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
    if "--quick" in args:
        i = args.index("--quick")
        render_quick(args[i + 1], names=set(args[i + 2].split(",")) if len(args) > i + 2 else None)
        sys.exit(0)
    render_dir = None
    only = None
    ci = "--ci" in args
    if ci:
        args.remove("--ci")
    if "--render" in args:
        i = args.index("--render")
        render_dir = args[i + 1]
        del args[i:i + 2]
    if "--only" in args:
        i = args.index("--only")
        only = set(args[i + 1].split(","))
        del args[i:i + 2]
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    for (path, nb, npal, nent, size) in save_all(out):
        print("書き出しました:", path, "ブロック", nb, "種類", npal, "もの", nent, "大きさ", size)
    if render_dir:
        render_quick(render_dir, names=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_casino_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
