"""コネリアの武器工房（PHASE 2。建築の文書「PHASE 2：武器工房」の平面図どおり）。

正面は南（工房通り）。西の半分は店（1階）とドランの住まい（2階）で、1階は石、2階は木組みと漆喰。
東の半分は炉の間（道と同じ高さの土間、天井まで6、梁の上は屋根まで吹き抜け）で、軒まで石の塊。
真ん中の防火の壁（石）が土台から屋根まで2つを分ける。屋根は東西に棟が通る1：1の黒い瓦（深層岩タイル）で、
入口の上だけ南へ急な破風（2：1）を出す。東の妻に深層岩レンガの炉の煙突（+22）、棟に煙出し（+21）、
裏に古代炉の古代の煙道（+20）。東は作業庭（柵で囲む）。
外寸 17×17、軒（屋根裏の床）+11、棟 +19（道のブロックからの段数）。

座標：構造物の中で x が東、y が上、z が南。文書の平面図の (u, v) とは X = u + 3、Z = 19 - v の関係
（u は西→東、v は正面＝南→裏＝北）。世界の座標では、構造物の (0, 0, 0) が (x 79, 道の高さ, z -26)。
y=0 が地面（道・石畳・土間）。店と住まいの1階の床のブロック y=1（+1）、2階 y=6（+6）、屋根裏 y=11（+11）。

使い方: python3 tools/mapgen/build_weapon.py [出力のフォルダ] [--render 絵のフォルダ]
  - <出力>/weapon_workshop.nbt（構造物ファイル。出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - <出力>/weapon_forge_lit.nbt（12日目の古代炉：火の入った形。古代炉のまわりだけ。下の FORGE_LIT_AT に重ねて置く）
  - <絵のフォルダ>/weapon_*.png（見本の絵）
ゲームの中では /place template amemaru:map/conelia/weapon_workshop で置ける（置いた範囲の中は片づく）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.facade import cell, origin, window
from mapgen.fixtures import candles, hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, log, pane, sign_nbt, slab, stairs,
                          trapdoor)

W, H, D = 25, 24, 22          # u -3..21、y 0..23、v -2..19
Y1, Y2, YA = 1, 6, 11          # 店と住まいの床のブロック（1階・2階・屋根裏）
YF, YB = 0, 7                  # 炉の間の土間と梁
ROOF_Y0 = 10                   # 屋根の軒先（壁から1つ外）の高さ。壁の上の線は +11

pal = Palette(seed=31, roof="deepslate_tile", roof_block="deepslate_tiles", trim="dark_oak", post="spruce",
              beam="spruce", plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)


def XZ(u, v):
    """平面図の (u, v) → 構造物の (X, Z)"""
    return u + 3, 19 - v


def put(u, v, y, state, nbt=None):
    x, z = XZ(u, v)
    w.put(x, y, z, state, nbt)


def get(u, v, y):
    x, z = XZ(u, v)
    return w.get(x, y, z)


def name_at(u, v, y):
    x, z = XZ(u, v)
    return w.name_at(x, y, z)


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state() if callable(state) else state)


PLAN_DIR = {"street": "south", "back": "north", "west": "west", "east": "east"}   # 平面図の向き → 世界の向き


def wall_lamp(u, v, y, out, style="wood", brace=None):
    """壁（平面図の (u, v)）から出す腕木の灯り。out は "street"・"back"・"west"・"east"。"""
    x, z = XZ(u, v)
    wall_lantern(w, x, y, z, PLAN_DIR[out], style=style)
    if brace:
        dx, dz = {"south": (0, 1), "north": (0, -1), "west": (-1, 0), "east": (1, 0)}[PLAN_DIR[out]]
        opp = {"south": "north", "north": "south", "west": "east", "east": "west"}[PLAN_DIR[out]]
        w.put(x + dx, y - 1, z + dz, stairs(brace, opp, "top"))


def hang(u, v, y, chain=1):
    """天井から鎖で吊るす灯り。y は鎖のいちばん上（天井のすぐ下）。"""
    x, z = XZ(u, v)
    hang_lantern(w, x, y, z, chain=chain)


def frame(u, v, y, facing, item, rotation=0, name=None):
    x, z = XZ(u, v)
    w.item_frame(x, y, z, facing, item, rotation=rotation, name=name)


def stand(u, v, y, facing, **kw):
    x, z = XZ(u, v)
    w.armor_stand(x, y, z, facing, **kw)


# ---------------------------------------------------------------- 材料
def gray_stone():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで（都市のルール③）"""
    r = rng.random()
    if r < 0.80:
        return block("stone_bricks")
    if r < 0.90:
        return block("cracked_stone_bricks")
    if r < 0.96:
        return block("mossy_stone_bricks")
    return block("andesite")


def ancient_stone():
    """古代の層：磨いた閃緑岩が主、閃緑岩（風化した所）を少し"""
    return block("polished_diorite") if rng.random() < 0.82 else block("diorite")


def apron_paving():
    """店の前の石畳（再建の層。道の端と同じ石レンガ）"""
    r = rng.random()
    if r < 0.66:
        return block("stone_bricks")
    if r < 0.86:
        return block("polished_andesite")
    return block("cracked_stone_bricks")


def alley():
    """路地と裏通り：丸石と土の道（③⑤）"""
    r = rng.random()
    if r < 0.5:
        return block("cobblestone")
    if r < 0.8:
        return block("dirt_path")
    return block("gravel")


def yard_ground():
    r = rng.random()
    if r < 0.4:
        return block("gravel")
    if r < 0.75:
        return block("coarse_dirt")
    if r < 0.9:
        return block("cobblestone")
    return block("dirt_path")


def forge_floor(u, v):
    """炉の間の土間：石と土。炉のまわりは石レンガ、古代炉の前は古代の白い石"""
    if 10 <= u <= 14 and 12 <= v <= 13:
        return block("polished_diorite") if (u + v) % 3 else block("calcite")
    if 12 <= u <= 15 and 6 <= v <= 10:
        return block("stone_bricks") if rng.random() < 0.8 else block("cracked_stone_bricks")
    r = rng.random()
    if r < 0.35:
        return block("packed_mud")
    if r < 0.6:
        return block("cobblestone")
    if r < 0.85:
        return block("coarse_dirt")
    return block("gravel")


SPRUCE_LOG_Y = log("spruce", "y", stripped=True)


def beam(axis):
    return log("spruce", axis, stripped=True)


OUT = {"south": (0, -1), "north": (0, 1), "west": (-1, 0), "east": (1, 0)}


# ---------------------------------------------------------------- 窓（平面図の座標で）
def win(face, line, a0, width, y0, height, **kw):
    """face の壁（南・北は v=line、西・東は u=line）に、a0 から幅 width の窓。a0 は南北の壁では u の小さい方、東西の壁では v の小さい方。"""
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        window(w, pal, face, plane, x0, width, y0, height, **kw)
    else:
        plane, zmin = XZ(line, a0 + width - 1)
        window(w, pal, face, plane, zmin, width, y0, height, **kw)


def arched_window(face, line, a0, width=3, y0=Y1 + 1, height=3, sill=True):
    """石の壁のアーチの窓：壁の面にガラス、上の両角を逆さの階段で丸める、外に石の窓台。"""
    from mapgen.facade import Orient
    win(face, line, a0, width, y0, height, shutters=False, sill=False, lintel=False, glass_depth=0)
    O = Orient(face)
    yt = y0 + height - 1
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        ox, oz = origin(face, x0, x0 + width - 1, plane)
    else:
        plane, zmin = XZ(line, a0 + width - 1)
        ox, oz = origin(face, zmin, zmin + width - 1, plane)
    if width >= 3:
        lx, lz = cell(face, ox, oz, 0, 0)
        rx, rz = cell(face, ox, oz, width - 1, 0)
        w.put(lx, yt, lz, stairs("stone_brick", O.left, "top"))
        w.put(rx, yt, rz, stairs("stone_brick", O.right, "top"))
    for i in range(width if sill else 0):
        x, z = cell(face, ox, oz, i, 1)
        w.put(x, y0 - 1, z, slab("stone_brick", "top"))


def flower_box(face, u, v, y=Y2 + 1):
    """窓の下の花箱（ツツジの葉）と、前の板"""
    du, dv = OUT[face]
    put(u + du, v + dv, y, leaves("flowering_azalea_leaves" if rng.random() < 0.6 else "azalea_leaves"))
    put(u + 2 * du, v + 2 * dv, y, trapdoor(pal.frame, face, "bottom", True))


def house_window(face, u, v, shutters=True):
    """2階の窓：幅1×高さ2（+8・+9）、両脇の雨戸、下の花箱。正面（通りの側）は木組みを見せるため雨戸なし"""
    if face in ("south", "north"):
        win(face, v, u, 1, Y2 + 2, 2, shutters=shutters, sill=False, lintel=False, glass_depth=0)
    else:
        win(face, u, v, 1, Y2 + 2, 2, shutters=shutters, sill=False, lintel=False, glass_depth=0)
    flower_box(face, u, v)


# ---------------------------------------------------------------- 地面
def ground():
    for u in range(-3, 22):
        for v in range(-2, 20):
            if -2 <= v <= -1 and u >= 0:
                put(u, v, 0, apron_paving())
            elif u < 0 or v >= 17:
                put(u, v, 0, alley())
            elif 0 <= u <= 16 and 0 <= v <= 16:
                if 9 <= u <= 15 and 1 <= v <= 15:
                    put(u, v, 0, forge_floor(u, v))          # 炉の間の土間（見える床）
                else:
                    put(u, v, 0, block("cobblestone"))       # 壁の下と、店の床の下
            else:
                put(u, v, 0, yard_ground())
    # 煙突の下
    fill(17, 18, 7, 9, 0, 0, block("deepslate_bricks"))


# ---------------------------------------------------------------- 床
def floors():
    # 店と住まいの1階（+1）：ポーチは石、中はトウヒの板
    for u in range(1, 8):
        for v in range(0, 2):
            put(u, v, Y1, block("polished_andesite") if (u + v) % 2 else block("stone_bricks"))
        for v in range(3, 16):
            put(u, v, Y1, block("spruce_planks"))
    # 2階（+6）：トウヒの板。v 4・8 は床に見える梁。v 9（2階の間仕切りの下）も梁にして、v 8 と2本でカウンターの上の太い梁にする。
    # 階段の口（u 1..2、v 10..13）はあけ、口の東の縁（u=3、間仕切りの下）は南北の梁
    for u in range(1, 8):
        for v in range(1, 16):
            if u <= 2 and 10 <= v <= 13:
                continue
            if u == 3 and 10 <= v <= 13:
                put(u, v, Y2, beam("z"))
                continue
            put(u, v, Y2, beam("x") if v in (4, 8, 9) else block("spruce_planks"))
    # 屋根裏（+11）
    for u in range(1, 8):
        for v in range(1, 16):
            put(u, v, YA, block("spruce_planks"))


# ---------------------------------------------------------------- 外壁（石）
def plinth_or(stone, y):
    return block("polished_andesite") if y == Y1 else stone


def stone_walls():
    # 西の半分の1階（+1..+5）：西の壁（ポーチの横を含む）、裏の壁、店の正面の壁（v=2）
    for y in range(Y1, Y2):
        for v in range(0, 17):
            put(0, v, y, plinth_or(gray_stone(), y))
        for u in range(0, 9):
            put(u, 16, y, plinth_or(gray_stone(), y))
            put(u, 2, y, plinth_or(gray_stone(), y))
    # 東の半分（炉の間）：軒まで石（+1..+10）
    for y in range(Y1, ROOF_Y0 + 1):
        for u in range(8, 17):
            put(u, 0, y, plinth_or(gray_stone(), y))
            put(u, 16, y, plinth_or(gray_stone(), y))
        for v in range(0, 17):
            put(16, v, y, plinth_or(gray_stone(), y))
    # 防火の壁（u=8）：土台から軒まで（その上は屋根を置いてから屋根の裏まで）
    for y in range(Y1, ROOF_Y0 + 1):
        for v in range(0, 17):
            put(8, v, y, block("stone_bricks") if y > Y1 else block("polished_andesite"))
    # 角の石（明るい安山岩と石レンガを交互に）
    for (u, v) in ((16, 0), (16, 16), (8, 0)):
        for y in range(Y1, ROOF_Y0 + 1):
            put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))
    for (u, v) in ((0, 0), (0, 16)):
        for y in range(Y1, Y2):
            put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))


# ---------------------------------------------------------------- 2階の木組み（西の半分）
def timber_walls():
    """2階（+6..+10）：+6 は床の高さの帯（梁）、+7..+9 は柱と漆喰、+10 は桁（梁）"""
    def run(face, line, a0, a1, posts):
        axis = "x" if face in ("south", "north") else "z"
        for a in range(a0, a1 + 1):
            u, v = (a, line) if face in ("south", "north") else (line, a)
            put(u, v, Y2, beam(axis))
            put(u, v, ROOF_Y0, beam(axis))
            for y in range(Y2 + 1, ROOF_Y0):
                put(u, v, y, SPRUCE_LOG_Y if a in posts else block(pal.plaster))
    run("south", 0, 0, 7, (0, 4))
    run("north", 16, 0, 7, (0, 4))
    run("west", 0, 0, 16, (0, 4, 8, 12, 16))
    # 2階の窓は雨戸なし（柱の間の漆喰を見せる）。下に花箱
    for u in (2, 6):
        house_window("south", u, 0, shutters=False)
        house_window("north", u, 16, shutters=False)
    for v in (2, 6, 10, 14):
        house_window("west", 0, v, shutters=False)


# ---------------------------------------------------------------- 正面：ポーチ・店の正面・炉の間のアーチ
def front():
    # ポーチの柱（u=4。根元は石）。上は2階の柱へ通る
    put(4, 0, Y1, block("polished_andesite"))
    for y in range(Y1 + 1, Y2):
        put(4, 0, y, SPRUCE_LOG_Y)
    # ポーチの西の端（u=0、v 0..1）は石の壁（stone_walls で置いた）。ポーチの天井は2階の床
    # 店の正面（v=2）：西の柱の間はアーチの窓（幅3）、東の柱の間は扉と両脇の細い窓
    arched_window("south", 2, 1, 3, Y1 + 1, 3, sill=False)
    put(6, 2, Y1 + 1, door(pal.frame, "south", False, hinge="left"))
    put(6, 2, Y1 + 2, door(pal.frame, "south", True, hinge="left"))
    put(6, 2, Y1 + 3, stairs("stone_brick", "south", "top"))
    for u in (5, 7):
        for y in (Y1 + 2, Y1 + 3):
            put(u, 2, y, pane())
    # 1段の踏み段（扉の前）
    for u in (5, 6, 7):
        put(u, -1, Y1, stairs("stone_brick", "north"))
    # ポーチの天井から、鎖で灯りを吊る
    hang(6, 1, Y2 - 1)
    hang(2, 1, Y2 - 1)
    # 柱から通りへ出した腕木の灯り（都市のルール⑦の壁のランタン。灯りは道から3）
    wall_lamp(4, 0, 5, "street")
    # 植木鉢
    put(1, 1, Y1 + 1, block("potted_azalea_bush"))
    put(3, 1, Y1 + 1, block("potted_azalea_bush"))
    # 吊り看板（店と炉の間の境の柱 u=8 から、通りの方へ。道から4）と、その下の額縁の鉄の剣（⑦）
    put(8, -1, 4, block("spruce_wall_hanging_sign", facing="east", waterlogged=False),
        sign_nbt(["武器工房", "ベルク", "", ""], ["武器工房", "ベルク", "", ""], hanging=True))
    frame(8, -1, 3, "south", "iron_sword", rotation=1)

    # 炉の間の正面（v=0、u 9..15）：幅7・高さ6のアーチ。腰の台（石＋トウヒのハーフ。道から1.5）
    for u in range(9, 16):
        for y in range(1, 7):
            put(u, 0, y, AIR)
        put(u, 0, 1, block("stone_bricks"))
        put(u, 0, 2, slab("spruce", "bottom"))
    # アーチの丸み（下から：両端の角、1つ内の角）と要石
    put(9, 0, 5, stairs("stone_brick", "west", "top"))
    put(15, 0, 5, stairs("stone_brick", "east", "top"))
    put(9, 0, 6, block("stone_bricks"))
    put(15, 0, 6, block("stone_bricks"))
    put(10, 0, 6, stairs("stone_brick", "west", "top"))
    put(14, 0, 6, stairs("stone_brick", "east", "top"))
    for u in range(9, 16):
        put(u, 0, 7, block("stone_bricks"))
    put(12, 0, 7, block("chiseled_stone_bricks"))
    # アーチの上の小さな煙抜き（鉄格子）と旗（赤と黒、白い縁。交わる剣の形）
    put(12, 0, 9, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    for u in (10, 14):     # 軒の水切り（+9）の下に掛ける。布は +7 まで垂れる（アーチの口にはかからない）
        put(u, -1, 8, block("red_wall_banner", facing="south"), banner_nbt([("cr", "black"), ("bo", "white")]))
    # 腰の台の表に、仕上がった武器（額縁）
    for (u, item) in ((10, "iron_sword"), (12, "iron_axe"), (14, "crossbow")):
        frame(u, -1, 1, "south", item, rotation=1 if item != "crossbow" else 0)


# ---------------------------------------------------------------- 東・裏・西の開口
def openings():
    # 東（u=16、作業庭へ）：戸、鉄格子の窓（煙抜き）、戸の横の灯り、旗
    put(16, 12, 1, door(pal.frame, "east", False))
    put(16, 12, 2, door(pal.frame, "east", True))
    put(16, 12, 3, stairs("stone_brick", "east", "top"))
    for v in (3, 14):
        for y in (2, 3):
            put(16, v, y, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    wall_lamp(16, 11, 5, "east")
    put(17, 4, 8, block("red_wall_banner", facing="east"), banner_nbt([("cr", "black"), ("bo", "white")]))
    # 裏（v=16）：店の側は裏口と窓。炉の間の側は古代炉の背（古代の石）
    put(4, 16, Y1 + 1, door(pal.frame, "north", False))
    put(4, 16, Y1 + 2, door(pal.frame, "north", True))
    put(4, 16, Y1 + 3, stairs("stone_brick", "north", "top"))
    put(4, 17, Y1, stairs("stone_brick", "south"))
    # 裏口の小さな庇（濃い木の階段3つを、両端の持ち送りで受ける）と灯り
    for u in (3, 4, 5):
        put(u, 17, Y1 + 4, stairs(pal.trim, "south"))
    for u in (3, 5):
        put(u, 17, Y1 + 3, stairs(pal.frame, "south", "top"))
    put(4, 17, Y1 + 3, lantern(True))
    for u in (2, 6):
        win("north", 16, u, 1, Y1 + 2, 2, shutters=False, sill=False, lintel=False, glass_depth=0)
        put(u, 17, Y1 + 1, slab("stone_brick", "top"))
    for u in range(10, 15):
        for y in range(Y1, ROOF_Y0 + 1):
            put(u, 16, y, ancient_stone() if 11 <= u <= 13 else block("quartz_pillar", axis="y"))
    # 西（u=0、路地へ）：階段の下り口の窓と、路地の灯り
    win("west", 0, 14, 1, Y1 + 2, 2, shutters=False, sill=False, lintel=False, glass_depth=0)
    wall_lamp(0, 8, 5, "west")
    # 防火の壁の開口：店から炉の間を見る鉄格子の窓（v 3..5、+2..+4）と、働く人の通り口（v 10..11）
    for v in (3, 4, 5):
        for y in (2, 3, 4):
            put(8, v, y, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    for v in (10, 11):
        for y in (2, 3, 4):
            put(8, v, y, AIR)
        put(8, v, 5, beam("z"))
        put(9, v, 1, stairs("stone_brick", "west"))        # 土間へ1段下りる


# ---------------------------------------------------------------- 屋根
main_roof = Roof(w, pal, 3, 19, 3, 19, ROOF_Y0, ridge="x", pitch=1)            # u 0..16、v 0..16
cross_roof = Roof(w, pal, 4, 10, 11, 19, ROOF_Y0, ridge="z", pitch=2, ends=("end",))   # u 1..7、v 0..8
ROOFS = [main_roof, cross_roof]


def chimney_zone(x, y, z):
    u, v = x - 3, 19 - z
    if 17 <= u <= 18 and 7 <= v <= 9:
        return True                                     # 炉の煙突
    if 7 <= u <= 8 and v == 8 and y >= YA:
        return True                                     # 住まいの煙突
    if 11 <= u <= 13 and v == 15 and y >= 6:
        return True                                     # 古代の煙道
    return False


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        u, v = x - 3, 19 - z
        if chimney_zone(x, y, z):
            return True
        if 0 <= u <= 16 and 0 <= v <= 16 and y < YA:
            return True       # 建物の中（屋根裏の床より下）には、屋根の部品を置かない
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))


def gables():
    # 西の妻（u=0）：木組み。柱は v 4・12、+14 に梁、真ん中に幅1×高さ2の窓
    gable_wall(w, pal, main_roof, XZ(0, 0)[0], posts=(XZ(0, 4)[1], XZ(0, 12)[1]), beam_rows=(14,))
    win("west", 0, 8, 1, 12, 2, shutters=False, sill=True, lintel=False, glass_depth=0)
    # 正面の破風（v=0）：木組み。柱は u 2・6、+14 に梁、真ん中に幅1×高さ2の窓
    gable_wall(w, pal, cross_roof, XZ(0, 0)[1], posts=(XZ(2, 0)[0], XZ(6, 0)[0]), beam_rows=(14,))
    win("south", 0, 4, 1, 12, 2, shutters=False, sill=True, lintel=False, glass_depth=0)
    # 東の妻（u=16）と防火の壁（u=8）：石で屋根の裏まで
    for v in range(0, 17):
        for (u, mat) in ((16, gray_stone), (8, lambda: block("stone_bricks"))):
            y = ROOF_Y0 + 1
            while y <= 19 and get(u, v, y) is None:      # 棟（+19）より上には積まない
                put(u, v, y, mat())
                y += 1
    # 東の妻の下の帯（石の階段の水切り）
    for v in range(1, 16):
        if 7 <= v <= 9 or get(17, v, ROOF_Y0 + 1) is not None:
            continue
        put(17, v, ROOF_Y0 + 1, stairs("stone_brick", "west", "top"))


# ---------------------------------------------------------------- 煙突・煙出し・古代の煙道
def chimneys():
    # 炉の煙突（東の妻の外、u 17..18、v 7..9。深層岩レンガ）。上に焚き火と干し草の俵（煙を高く上げる）
    for y in range(1, 21):
        for u in (17, 18):
            for v in (7, 8, 9):
                put(u, v, y, block("deepslate_bricks") if rng.random() < 0.85 else block("cracked_deepslate_bricks"))
    for u in (17, 18):
        for v in (7, 8, 9):
            put(u, v, 21, block("deepslate_bricks"))
    put(17, 8, 21, block("hay_block", axis="y"))
    put(17, 8, 22, block("campfire", lit=True, facing="north", signal_fire=True, waterlogged=False))
    for (u, v) in ((18, 7), (18, 8), (18, 9), (17, 7), (17, 9)):
        put(u, v, 22, slab("deepslate_brick", "bottom"))
    # 根もとの張り出し（土台の帯）
    for v in (6, 10):
        put(17, v, 1, stairs("deepslate_brick", "south" if v == 6 else "north"))
    # 住まいの煙突（防火の壁の上、u 7..8、v 8）。2階のかまどから
    for y in range(Y2 + 2, 21):
        put(7, 8, y, block("stone_bricks"))
        if y >= YA:
            put(8, 8, y, block("stone_bricks"))
    put(7, 8, 20, block("stone_bricks"))       # 干し草を敷かない：台所の煙は低く（炉の煙突の高い煙と見分ける）
    put(7, 8, 21, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
    put(8, 8, 21, slab("stone_brick", "bottom"))
    # 古代の煙道（古代炉の奥の半分の上、u 11..13、v 15）：古代の石。石英レンガの帯。上に（まだ火のない）焚き火
    for y in range(6, 20):
        for u in (11, 12, 13):
            put(u, 15, y, block("quartz_bricks") if y in (10, 15) else ancient_stone())
    for u in (11, 13):
        put(u, 15, 19, block("chiseled_quartz_block"))
        put(u, 15, 20, slab("quartz", "bottom"))
    put(12, 15, 19, block("hay_block", axis="y"))
    put(12, 15, 20, block("campfire", lit=False, facing="south", signal_fire=True, waterlogged=False))
    # 棟の煙出し（越屋根）：炉の間の真上（u 10..14、v 7..9）。棟を u 11..13 で切り、柱の上に小さな屋根
    for u in (11, 12, 13):
        for y in range(16, 20):
            put(u, 8, y, AIR)
    for u in (10, 12, 14):
        for v in (7, 9):
            put(u, v, 19, fence(pal.trim))
    for u in range(10, 15):
        put(u, 7, 20, stairs(pal.roof, "north"))
        put(u, 9, 20, stairs(pal.roof, "south"))
        put(u, 8, 20, block(pal.roof_block))
        put(u, 8, 21, slab(pal.roof, "bottom"))
    for v in (7, 8, 9):
        put(9, v, 20, stairs(pal.trim, "east", "top"))
        put(15, v, 20, stairs(pal.trim, "west", "top"))


# ---------------------------------------------------------------- 1階の中：店
def shop():
    # 床の軸（入口からカウンターのドランまで）：赤い敷物
    for v in range(3, 8):
        put(6, v, Y1 + 1, block("red_carpet"))
    # 武器の壁（西の壁いっぱい。額縁を2段）
    upper = ["iron_sword", "iron_axe", "bow", "trident", "crossbow", "iron_sword"]
    lower = ["stone_sword", "stone_axe", "iron_sword", "golden_sword", "iron_axe", "bow"]
    for i, v in enumerate(range(3, 9)):
        frame(1, v, Y1 + 3, "east", upper[i], rotation=1 if upper[i].endswith(("sword", "axe")) else 0)
        frame(1, v, Y1 + 2, "east", lower[i], rotation=1 if lower[i].endswith(("sword", "axe")) else 0)
    # 立てかける展示：武器を持たせた防具立て
    stand(2, 5, Y1 + 1, "east", hand="iron_sword", offhand="iron_sword")
    stand(2, 7, Y1 + 1, "east", hand="trident")
    # 吊るす展示：カウンターの前の梁（v=8、2階の床の梁）の下に、下向きの額縁で武器を吊るす（頭より上の +5）
    for (u, item) in ((2, "bow"), (4, "iron_sword"), (6, "crossbow")):
        frame(u, 8, Y2 - 1, "down", item, rotation=0)
    # 鉄格子の窓の前の低い展示台（上つきのハーフ）と、寝かせた武器
    for (v, item) in ((3, "iron_sword"), (4, "iron_axe"), (5, "golden_sword")):
        put(7, v, Y1 + 1, slab("spruce", "top"))
        frame(7, v, Y1 + 2, "up", item, rotation=1)
    # カウンター（v=9）：皮をはいだトウヒの丸太。u=3 は客の側から使う作業台（MOD の作業台の代わりの鍛冶台）、u=7 は柵の門
    for u in range(1, 7):
        put(u, 9, Y1 + 1, block("smithing_table") if u == 3 else log("spruce", "x", stripped=True))
    put(7, 9, Y1 + 1, block("spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False))
    put(1, 9, Y1 + 2, lantern(False))
    frame(5, 9, Y1 + 2, "up", "iron_sword", rotation=3)
    # 灯り
    for (u, v) in ((3, 4), (3, 7), (6, 5)):
        put(u, v, Y2 - 1, lantern(True))


# ---------------------------------------------------------------- 1階の中：カウンターの奥（働く人の所）
def staff():
    # 2階への階段（u 1..2。v 13 から南へ上る：+2..+6。いちばん上の段 v 9 は2階の床の高さ）
    for (v, y) in ((13, 2), (12, 3), (11, 4), (10, 5), (9, 6)):
        for u in (1, 2):
            put(u, v, y, stairs("spruce", "south"))
            for yy in range(Y1 + 1, y):
                put(u, v, yy, block("spruce_planks"))
    # 階段の下の物入れ（v 10..11 の下は低い戸棚）
    for u in (1, 2):
        put(u, 10, Y1 + 1, block("barrel", facing="south", open=False))
        put(u, 11, Y1 + 1, block("barrel", facing="south", open=False))
    # カウンターの後ろの見せ棚（v=12、u 5..7）：下は引き出し（樽）、上は板の壁。南の面に名品
    for u in (5, 6, 7):
        put(u, 12, Y1 + 1, block("barrel", facing="south", open=False))
        for y in range(Y1 + 2, Y2):
            put(u, 12, y, block("spruce_planks"))
    for (u, item) in ((5, "iron_sword"), (6, "diamond_sword"), (7, "iron_axe")):
        frame(u, 11, Y1 + 3, "south", item, rotation=1)
    # 材料の部屋（u 5..7、v 13..15）：鉄・鉱石・皮・柄の木
    put(7, 13, Y1 + 1, block("barrel", facing="west", open=False))
    put(7, 14, Y1 + 1, block("barrel", facing="west", open=False))
    put(7, 15, Y1 + 1, block("iron_block"))
    put(6, 15, Y1 + 1, block("barrel", facing="north", open=False))
    put(5, 15, Y1 + 1, block("chest", facing="south", type="single", waterlogged=False))
    put(7, 13, Y1 + 2, block("raw_iron_block"))
    put(7, 14, Y1 + 2, block("barrel", facing="up", open=False))
    put(6, 15, Y1 + 2, log("spruce", "x"))
    put(7, 15, Y1 + 2, block("coal_block"))
    put(6, 14, Y2 - 1, lantern(True))
    # 通り道の灯り
    put(4, 10, Y2 - 1, lantern(True))
    put(3, 14, Y2 - 1, lantern(True))


# ---------------------------------------------------------------- 炉の間
def forge():
    # 梁（+7）：v 4・8・12 に東西に渡す
    for v in (4, 8, 12):
        for u in range(9, 16):
            put(u, v, YB, beam("x"))
    # 正面の見せる所：砥石・見せる金床・焼き入れの水
    put(10, 2, 1, block("grindstone", face="floor", facing="north"))
    put(12, 3, 1, block("anvil", facing="north"))
    put(14, 2, 1, block("water_cauldron", level=3))
    # 職人の作業台（MOD の作業台の代わりの鍛冶台）3つと、その間の材料
    for v in (5, 7, 9):
        put(9, v, 1, block("smithing_table"))
    put(9, 6, 1, block("barrel", facing="up", open=False))
    put(9, 8, 1, block("chipped_anvil", facing="east"))
    # 日々の炉（東の壁、v 7..9）：腰の高さの炉床、焚き火の火と、両脇の溶鉱炉、上の覆い
    for v in (7, 8, 9):
        put(14, v, 1, block("stone_bricks"))
        put(15, v, 1, block("deepslate_bricks"))
    put(15, 8, 2, block("campfire", lit=True, facing="west", signal_fire=False, waterlogged=False))
    put(15, 7, 2, block("blast_furnace", facing="west", lit=True))
    put(15, 9, 2, block("blast_furnace", facing="west", lit=True))
    put(15, 7, 3, block("deepslate_bricks"))
    put(15, 9, 3, block("deepslate_bricks"))
    for v in (7, 8, 9):
        put(14, v, 4, stairs("deepslate_brick", "east", "top"))
        put(15, v, 4, block("deepslate_bricks"))
        put(15, v, 5, block("deepslate_bricks"))
    put(14, 8, 5, stairs("deepslate_brick", "east", "top"))
    put(15, 8, 6, block("deepslate_bricks"))
    put(12, 8, 1, block("anvil", facing="east"))
    put(14, 5, 1, block("water_cauldron", level=3))
    put(15, 10, 1, block("barrel", facing="up", open=False))
    put(15, 11, 1, block("barrel", facing="up", open=False))
    put(15, 11, 2, block("coal_block"))
    # 道具の掛け台（東の壁）
    for (v, item) in ((4, "shears"), (5, "iron_pickaxe")):
        frame(15, v, 3, "west", item)
    # 古代炉（奥、u 10..14、v 14..15）：古代の石の炉。両脇に石英の柱。口は鉄格子で閉じ、炉心は消えた結晶（遮光ガラス）
    for u in (10, 14):
        for v in (14, 15):
            for y in range(1, 6):
                put(u, v, y, block("quartz_pillar", axis="y"))
            put(u, v, 6, block("chiseled_quartz_block"))
    for u in (11, 12, 13):
        for v in (14, 15):
            for y in range(1, 6):
                put(u, v, y, block("polished_diorite"))
    put(12, 14, 2, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    put(12, 14, 3, block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False))
    put(12, 15, 2, block("blackstone"))
    put(12, 15, 3, block("blackstone"))
    put(11, 14, 4, block("quartz_bricks"))
    put(13, 14, 4, block("quartz_bricks"))
    put(12, 14, 4, block("tinted_glass"))
    put(11, 14, 5, block("quartz_bricks"))
    put(13, 14, 5, block("quartz_bricks"))
    put(12, 14, 5, block("chiseled_quartz_block"))
    for u in (11, 12, 13):
        put(u, 14, 6, slab("quartz", "bottom"))
    # 二人打ちの金床と、エミルの彫金台（未完の剣）
    put(12, 11, 1, block("anvil", facing="north"))
    put(9, 12, 1, block("cartography_table"))
    put(9, 12, 2, block("candle", candles=1, lit=False, waterlogged=False))
    put(9, 13, 1, block("stripped_spruce_wood", axis="y"))
    frame(9, 13, 2, "up", "iron_sword", rotation=1, name="未完の剣")
    # 灯り（梁から鎖で吊る）
    for (u, v) in ((11, 4), (13, 4), (10, 8), (11, 12), (13, 12)):
        hang(u, v, YB - 1)


# ---------------------------------------------------------------- 2階：ドランの住まい
def upstairs():
    # 間仕切り：居間と寝室の間（v=9、扉は u=5）、階段の囲い（u=3、v 10..13）、階段の口の手すり（v=14）
    for u in range(3, 8):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(u, 9, y, block("spruce_planks"))
    put(5, 9, Y2 + 1, door(pal.frame, "south", False))
    put(5, 9, Y2 + 2, door(pal.frame, "south", True))
    for v in range(10, 14):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(3, v, y, block("spruce_planks"))
    for u in (1, 2):
        put(u, 14, Y2 + 1, fence(pal.frame))
    # 居間と台所：かまど（燻製器）と調理台、食器の棚、食卓と椅子、酒の樽、窓ぎわの椅子
    put(7, 8, Y2 + 1, block("smoker", facing="west", lit=False))
    put(7, 7, Y2 + 1, slab("spruce", "top"))
    put(7, 7, Y2 + 2, block("barrel", facing="west", open=False))
    put(7, 5, Y2 + 1, block("barrel", facing="west", open=False))
    put(7, 6, Y2 + 1, block("barrel", facing="west", open=False))
    put(7, 5, Y2 + 2, block("flower_pot"))
    for u in (3, 4, 5):
        for v in (4, 5):
            put(u, v, Y2 + 1, slab("spruce", "top"))
    for u in (3, 5):
        put(u, 3, Y2 + 1, stairs("spruce", "south"))
        put(u, 6, Y2 + 1, stairs("spruce", "north"))
    put(1, 1, Y2 + 1, block("barrel", facing="east", open=False))
    put(1, 2, Y2 + 1, block("barrel", facing="east", open=False))
    put(1, 1, Y2 + 2, block("barrel", facing="up", open=False))
    put(6, 1, Y2 + 1, stairs("spruce", "north"))
    x, z = XZ(4, 4)
    candles(w, x, Y2 + 2, z, n=2, lit=True)       # 食卓のろうそく
    put(4, 4, ROOF_Y0, lantern(True))
    put(2, 7, ROOF_Y0, lantern(True))
    # 寝室：寝台、衣装箱、本棚、書き物の机（古代文字の写しと剣の図面）
    put(7, 14, Y2 + 1, block("red_bed", facing="north", part="foot", occupied=False))
    put(7, 15, Y2 + 1, block("red_bed", facing="north", part="head", occupied=False))
    put(7, 10, Y2 + 1, block("chest", facing="west", type="single", waterlogged=False))
    put(7, 11, Y2 + 1, block("barrel", facing="west", open=False))
    put(4, 15, Y2 + 1, block("bookshelf"))
    put(4, 15, Y2 + 2, block("bookshelf"))
    put(1, 15, Y2 + 1, block("lectern", facing="east", has_book=True, powered=False))
    put(2, 15, Y2 + 1, slab("spruce", "top"))
    frame(2, 15, Y2 + 2, "up", "map")
    put(3, 15, Y2 + 1, stairs("spruce", "east"))
    for u in range(4, 7):
        for v in range(11, 14):
            put(u, v, Y2 + 1, block("brown_carpet"))
    put(5, 12, ROOF_Y0, lantern(True))
    # 屋根裏（入らない物置）の灯り
    for (u, v) in ((3, 4), (4, 12)):
        put(u, v, YA + 1, lantern(False))


# ---------------------------------------------------------------- まわり：作業庭・路地・裏通り
def surroundings():
    # 作業庭の柵（u=21 と、v=0・16）と門
    for v in range(0, 17):
        put(21, v, 1, fence(pal.frame))
    for u in range(17, 22):
        put(u, 0, 1, fence(pal.frame))
        put(u, 16, 1, fence(pal.frame))
    put(19, 0, 1, block("spruce_fence_gate", facing="south", open=False, powered=False, in_wall=False))
    put(19, 16, 1, block("spruce_fence_gate", facing="north", open=False, powered=False, in_wall=False))
    # 薪（建物の壁に寄せて）
    for v in (1, 2, 3):
        put(17, v, 1, log("spruce", "z"))
        if v != 3:
            put(17, v, 2, log("spruce", "z"))
    # 炭の袋（麻袋：茶色の羊毛と干し草の俵）
    put(20, 1, 1, block("brown_wool"))
    put(20, 2, 1, block("hay_block", axis="y"))
    put(20, 3, 1, block("brown_wool"))
    put(20, 2, 2, block("brown_wool"))
    # 水桶
    put(17, 4, 1, block("water_cauldron", level=3))
    put(17, 5, 1, block("water_cauldron", level=3))
    # 鉱石の山と樽（裏通りの荷車から）
    put(20, 10, 1, block("barrel", facing="up", open=False))
    put(20, 11, 1, block("raw_iron_block"))
    put(20, 11, 2, block("barrel", facing="up", open=False))
    # 荷車（荷台と車輪）
    for v in (13, 14):
        put(19, v, 1, slab("spruce", "top"))
        put(20, v, 1, trapdoor(pal.trim, "west", "bottom", True))
    put(19, 13, 2, block("barrel", facing="up", open=False))
    # 作業庭の灯り（再建のランタン灯：石の台、柵の柱、腕木と灯り、笠）
    x, z = XZ(20, 6)
    lamp_post(w, x, 1, z, arms=("west",))
    # 裏通り：裏口の脇の樽（壁に寄せる）
    put(1, 17, 1, block("barrel", facing="up", open=False))
    put(2, 17, 1, block("barrel", facing="up", open=False))
    put(1, 17, 2, block("barrel", facing="up", open=False))
    # 炉の前（アーチの東の端）：鉱石の樽と炭の袋だけ
    put(16, -1, 1, block("barrel", facing="up", open=False))
    put(16, -2, 1, block("brown_wool"))


# ---------------------------------------------------------------- 組み立て
def build():
    ground()
    floors()
    stone_walls()
    timber_walls()
    front()
    openings()
    shop()
    staff()
    forge()
    upstairs()
    roofs()
    gables()
    chimneys()
    surroundings()
    w.finalize()


build()

# 12日目の古代炉（火の入った形）。この範囲だけを入れ替える構造物：平面図の u 10..14、v 13..16、+1..+20
FORGE_LIT_BOX = ((10, 13, 1), (14, 16, 20))


def forge_lit_world():
    """古代炉に火が入った形の、古代炉のまわりだけの構造物（置く所は FORGE_LIT_AT）。"""
    lit = World(W, H, D)
    lit.blocks = dict(w.blocks)
    lit.nbt = dict(w.nbt)
    (u0, v0, y0), (u1, v1, y1) = FORGE_LIT_BOX

    def lput(u, v, y, state):
        x, z = XZ(u, v)
        lit.put(x, y, z, state)
    lput(12, 14, 4, block("sea_lantern"))                 # 炉心：ともった結晶
    lput(12, 14, 2, AIR)                                  # 口：鉄格子を外す
    lput(12, 14, 3, AIR)
    lput(12, 15, 2, block("campfire", lit=True, facing="south", signal_fire=False, waterlogged=False))
    lput(12, 15, 1, block("magma_block"))
    lput(12, 15, 3, block("magma_block"))
    lput(12, 15, 20, block("campfire", lit=True, facing="south", signal_fire=True, waterlogged=False))
    # 範囲だけを切り出す
    part = World(u1 - u0 + 1, y1 - y0 + 1, v1 - v0 + 1)
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            for y in range(y0, y1 + 1):
                x, z = XZ(u, v)
                st = lit.blocks.get((x, y, z))
                if st is not None:
                    px, pz = u - u0, (19 - v) - (19 - v1)
                    part.put(px, y - y0, pz, st, lit.nbt.get((x, y, z)))
    return part, lit


# 置く所（構造物の中の座標）：古代炉の火の入った形の (0, 0, 0)
FORGE_LIT_AT = (XZ(10, 16)[0], 1, XZ(10, 16)[1])

# 見本の絵を描く向き：目の位置と見る所（構造物の中の座標。y=0 が地面）。CI の写真（CiScreenshots.java の MAP_VIEWS）と同じ
VIEWS = {
    "front": ((0, 5, 36), (9, 10, 14)),            # 工房通りの向かいから：正面の破風の上まで
    "gate": ((38, 7, 34), (14, 8, 11)),            # 東門の方（南東）から：東の妻の煙突と作業庭
    "west": ((-22, 6, 30), (9, 8, 11)),            # 西（中央広場の方）から：西の妻と破風
    "close": ((11, 2.8, 25), (10, 4, 18)),         # 入口の近く：ポーチ、吊り看板、炉の間のアーチ
    "aerial": ((-6, 30, 36), (11, 8, 10)),         # 上から：屋根の交わり、煙出し、煙突、古代の煙道
    "back": ((2, 15, -16), (12, 9, 8)),            # 裏（北西）から：裏口、古代の煙道
}
# 中の絵：目の位置と見る所（ゲームの中で歩いて見る高さ）
INSIDE = {
    "shop": ((9.5, 3.6, 16.5), (9.0, 3.0, 9.0)),        # 入口から：床の軸の先のカウンター、右に鉄格子の窓
    "forge": ((15.5, 2.6, 19.0), (15.5, 2.6, 3.5)),     # 炉の間のアーチの外から奥の古代炉
    "ancient": ((14.5, 2.6, 10.5), (14.5, 2.4, 3.0)),   # 二人打ちの金床と古代炉、エミルの彫金台
    "home": ((5.5, 8.6, 17.5), (8.5, 7.6, 11.0)),       # 2階の居間と台所
}


def cut_world(y_cut, world=None):
    world = world or w
    cw = World(W, H, D)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut]
    return cw


def render_views(out_dir, width=960, height=540, fov=70):
    from PIL import Image
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    scene = Scene(w, ground_y=1)
    images = []
    for name, (eye, target) in VIEWS.items():
        image = scene.render(eye=eye, target=target, width=width, height=height, fov=fov,
                             path=os.path.join(out_dir, "weapon_%s.png" % name))
        images.append(image)
        print("描きました:", name)
    sheet = Image.new("RGB", (width * 3, height * 2))
    for i, image in enumerate(images):
        sheet.paste(image, ((i % 3) * width, (i // 3) * height))
    sheet.save(os.path.join(out_dir, "weapon_sheet.png"))
    # 切って真上から見る絵（上が北、下が正面）：1階は +6 の下、2階は屋根裏の床の下
    for name, cut in (("cut_1f", 6), ("cut_forge", YB), ("cut_2f", YA)):
        Scene(cut_world(cut), ground_y=1).render(eye=(12.5, 52, 11.6), target=(12.5, 0, 11.5), width=width,
                                                 height=height + 140, fov=40,
                                                 path=os.path.join(out_dir, "weapon_%s.png" % name))
        print("描きました:", name)
    for name, (eye, target) in INSIDE.items():
        scene.render(eye=eye, target=target, width=width, height=height, fov=80,
                     path=os.path.join(out_dir, "weapon_in_%s.png" % name))
        print("描きました:", "in_" + name)
    # 12日目の古代炉
    _, lit = forge_lit_world()
    Scene(lit, ground_y=1).render(eye=INSIDE["ancient"][0], target=INSIDE["ancient"][1], width=width, height=height,
                                  fov=80, path=os.path.join(out_dir, "weapon_in_ancient_lit.png"))
    print("描きました: in_ancient_lit")


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
    render_dir = None
    if "--render" in args:
        i = args.index("--render")
        render_dir = args[i + 1]
        del args[i:i + 2]
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    path = os.path.join(out, "weapon_workshop.nbt")
    n = w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "もの", len(w.entities), "大きさ", (W, H, D))
    part, _ = forge_lit_world()
    lit_path = os.path.join(out, "weapon_forge_lit.nbt")
    n2 = part.save_structure(lit_path)
    print("書き出しました:", lit_path, "ブロック", len(part.blocks), "種類", n2, "大きさ", (part.w, part.h, part.d),
          "置く所（工房の構造物の中）", FORGE_LIT_AT)
    if render_dir:
        render_views(render_dir)
