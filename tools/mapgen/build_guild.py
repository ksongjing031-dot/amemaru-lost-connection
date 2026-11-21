"""コネリアの冒険者ギルド（PHASE 1。建築の文書「PHASE 1：冒険者ギルド」の平面図どおり）。

正面は西（塔の道）。1階は石（古代の白い石の土台の上に灰色の石）、2階は木組みと漆喰で、正面の南側は1つ外へ張り出す。
屋根は南北に棟が通る1：1の赤い瓦。正面の真ん中に破風（入口・表の露台）、北西の角に騎士団の見張りの塔（緑青の銅の屋根）。
外寸 33×25、軒 +14、棟 +27、塔 +34（道の足もとから）。

座標：構造物の中で x が東、y が上、z が南。文書の平面図の (u, v) とは X = v + 8、Z = u + 5 の関係。
世界の座標では、構造物の (0, 0, 0) が (x 6, 道の下の地面, z -100)。
y=0 が地面（石畳・草）、道の足もとが y=1。1階の足もと y=3（+2）、2階 y=10（+9）、屋根裏 y=15（+14）、鐘の間 y=25（+24）。

使い方: python3 tools/mapgen/build_guild.py [出力のフォルダ] [--render 絵のフォルダ]
  - <出力>/guild_hall.nbt（構造物ファイル。出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - <絵のフォルダ>/guild_*.png（見本の絵）
ゲームの中では /place template amemaru:map/conelia/guild_hall で置ける（36×38×41。置いた範囲の中は片づく）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Pyramid, Roof, finial, gable_wall
from mapgen.facade import cell, origin, window
from mapgen.fixtures import hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, leaves, log, pane, sign_nbt, slab, stairs,
                          trapdoor, wall)

W, H, D = 36, 38, 41
ROAD, F1, F2, FA, FB = 1, 3, 10, 15, 25      # 足もとの高さ
Y1, Y2, YA, YB = F1 - 1, F2 - 1, FA - 1, FB - 1   # 床のブロックの高さ（2・9・14・24）

pal = Palette(seed=23, post="spruce", frame="spruce", trim="dark_oak", plaster="calcite")
rng = pal.rng
w = World(W, H, D)


def XZ(u, v):
    """平面図の (u, v) → 構造物の (X, Z)"""
    return v + 8, u + 5


def put(u, v, y, state, nbt=None):
    x, z = XZ(u, v)
    w.put(x, y, z, state, nbt)


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state)


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


def white_stone():
    """古代の層の白い石：磨いた閃緑岩6・閃緑岩2・方解石1（都市のルール③⑤）"""
    r = rng.random()
    if r < 0.66:
        return block("polished_diorite")
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def paving():
    """古代の白い石畳：磨いた閃緑岩6・閃緑岩2・方解石1・灰色の石のつぎはぎ1"""
    r = rng.random()
    if r < 0.6:
        return block("polished_diorite")
    if r < 0.8:
        return block("diorite")
    if r < 0.9:
        return block("calcite")
    return block("stone_bricks")


SPRUCE_LOG_Y = log("spruce", "y", stripped=True)


def beam(axis):
    return log("spruce", axis, stripped=True)


# ---------------------------------------------------------------- 地面と小広場
def ground():
    for x in range(W):
        for z in range(D):
            w.put(x, 0, z, block("grass_block", snowy=False))
    # 小広場（x 0..7、z 13..29）：古代の白い石畳
    for x in range(0, 8):
        for z in range(13, 30):
            w.put(x, 0, z, paving())
    # 建物の下は石
    w.fill(8, 0, 5, 32, 0, 37, block("stone"))
    # 北の外階段へ行く道と、裏庭（台所の搬入口の前）
    for x in range(30, 36):
        for z in range(1, 5):
            w.put(x, 0, z, block("dirt_path"))
    for x in range(33, 36):
        for z in range(5, 21):
            w.put(x, 0, z, block("cobblestone") if rng.random() < 0.5 else block("dirt_path"))
    # 植え込みの帯（小広場の北と南、建物の南）
    flowers = ["poppy", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "red_tulip"]
    for (x0, x1, z0, z1) in ((5, 7, 5, 12), (5, 7, 30, 37), (8, 32, 39, 40)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if rng.random() < 0.45:
                    w.put(x, 1, z, block(rng.choice(flowers)))
    for (x, z) in ((6, 5), (6, 12), (6, 30), (6, 37)):
        w.put(x, 1, z, leaves("azalea_leaves"))


# ---------------------------------------------------------------- 床・土台・梁
def floors():
    # 土台の中（見えない所）と1階の床（y=2）
    w.fill(9, 1, 6, 31, 1, 36, block("cobblestone"))
    for u in range(1, 32):
        for v in range(1, 24):
            if u <= 8 and v <= 8:
                continue      # 塔
            put(u, v, Y1, hall_floor(u, v))
    # 2階の床（y=9）：吹き抜けと大階段の口を除く
    for u in range(1, 32):
        for v in range(0, 24):
            if u <= 8 and v <= 8:
                continue
            if 13 <= u <= 19 and 9 <= v <= 15:
                continue      # 吹き抜け
            if 13 <= u <= 19 and 21 <= v <= 23:
                continue      # 大階段の口
            put(u, v, Y2, block("dark_oak_planks") if (1 <= u <= 7 and 9 <= v <= 15) else block("spruce_planks"))
    # 正面の張り出しの2階の床（v -1..0、u 21..32）
    fill(21, 32, -1, -1, Y2, Y2, block("spruce_planks"))
    # 屋根裏の床（y=14）
    for u in range(1, 33):
        for v in range(-1, 24):
            if u <= 8 and v <= 8:
                continue
            if v == -1 and u < 21:
                continue
            put(u, v, YA, block("spruce_planks"))
    # 1階の天井の梁（y=8）：柱の通りに沿って
    for u in range(9, 32):
        put(u, 8, 8, beam("z"))      # v=8 の通り
    for u in range(1, 32):
        put(u, 16, 8, beam("z"))     # v=16 の通り
    for v in range(9, 24):
        put(8, v, 8, beam("x"))      # u=8 の通り（2階の北の部屋の間仕切りの下）
    for v in range(1, 24):
        put(24, v, 8, beam("x"))     # u=24 の通り（2階の南の部屋の間仕切りの下）
    for v in range(1, 16):
        put(12, v, 8, beam("x"))     # 吹き抜けと作戦室の北の縁
        put(20, v, 8, beam("x"))     # 南の縁
    for u in range(25, 32):
        put(u, 12, 8, beam("z"))     # 2階の事務室と資料室の間仕切りの下
    for v in range(1, 8):
        put(16, v, 8, beam("x"))     # 2階の酒場の上（休憩室とマスター室の間仕切りの下）


def hall_floor(u, v):
    if 1 <= u <= 11 and 9 <= v <= 17:
        return block("spruce_planks")          # 酒場
    if 1 <= u <= 11 and 19 <= v <= 23:
        return block("stone_bricks")           # 台所
    if 25 <= u <= 31 and 1 <= v <= 16:
        return block("spruce_planks")          # 談話の場・待合
    # 広間：磨いた安山岩と石レンガの市松（2×2）
    return block("polished_andesite") if ((u // 2) + (v // 2)) % 2 == 0 else block("stone_bricks")


# ---------------------------------------------------------------- 1階の石の壁
def stone_walls():
    """外壁：y 1..2 が古代の白い石の土台、y 3..8 が灰色の石。角は石英レンガ。"""
    for y in range(1, 9):
        for u in range(0, 33):
            for v in (0, 24):
                if v == 0 and u <= 8:
                    continue          # 塔
                put(u, v, y, white_stone() if y <= 2 else gray_stone())
        for v in range(0, 25):
            for u in (0, 32):
                if u == 0 and v <= 8:
                    continue
                put(u, v, y, white_stone() if y <= 2 else gray_stone())
    for (u, v) in ((32, 0), (32, 24), (0, 24)):
        for y in range(1, 9):
            put(u, v, y, block("quartz_bricks") if y <= 2 else block("stone_bricks"))
    # 2階の床の高さの帯（y=9）：外は梁
    for u in range(9, 33):
        put(u, 0, Y2, beam("z"))
    for u in range(0, 33):
        put(u, 24, Y2, beam("z"))
    for v in range(9, 25):
        put(0, v, Y2, beam("x"))
    for v in range(0, 25):
        put(32, v, Y2, beam("x"))


def arched_window(face, plane, a_min, width=3, y0=4, height=3):
    """石の壁のアーチの窓：壁の面にガラス、上の両角を逆さの階段で丸める、外に窓台。"""
    window(w, pal, face, plane, a_min, width, y0, height, shutters=False, sill=True, lintel=False, glass_depth=0)
    x0, z0 = origin(face, a_min, a_min + width - 1, plane)
    from mapgen.facade import Orient
    O = Orient(face)
    yt = y0 + height - 1
    if width >= 3:
        lx, lz = cell(face, x0, z0, 0, 0)
        rx, rz = cell(face, x0, z0, width - 1, 0)
        w.put(lx, yt, lz, stairs("stone_brick", O.left, "top"))
        w.put(rx, yt, rz, stairs("stone_brick", O.right, "top"))
    # 窓台は石
    for i in range(width):
        x, z = cell(face, x0, z0, i, 1)
        w.put(x, y0 - 1, z, slab("stone_brick", "top"))


def ground_floor_openings():
    # 正面（西、X=8）：北の窓、入口の両脇の細い窓、南の3つの窓
    arched_window("west", 8, 14, 3)                 # u 9..11
    for z in (18, 24):                              # u 13・19（ポーチの中）
        window(w, pal, "west", 8, z, 1, 4, 3, shutters=False, sill=False, lintel=False, glass_depth=0)
    for z in (26, 30, 34):                          # u 21..23・25..27・29..31
        arched_window("west", 8, z, 3)
    # 入口：幅3のアーチ（扉なし）。1段の段で広間へ
    for z in (20, 21, 22):
        for y in range(3, 7):
            w.put(8, y, z, AIR)
        w.put(8, 2, z, stairs("stone_brick", "east"))
    w.put(8, 6, 20, stairs("quartz", "north", "top"))
    w.put(8, 6, 22, stairs("quartz", "south", "top"))
    w.put(8, 7, 21, block("chiseled_quartz_block"))
    for y in range(3, 7):
        w.put(8, y, 19, block("quartz_pillar", axis="y"))
        w.put(8, y, 23, block("quartz_pillar", axis="y"))
    # 南（Z=37）：談話の場の窓と、右奥の窓
    for x in (9, 21, 25, 29):                       # v 1..3・13..15・17..19・21..23
        arched_window("south", 37, x, 3)
    # 北（Z=5）：酒場の窓（奥は外階段の陰なので開けない）
    for x in (17, 21):                              # v 9..11・13..15
        arched_window("north", 5, x, 3)
    # 裏（東、X=32）：台所の窓、大階段の下の食料庫の小窓、裏口
    arched_window("east", 32, 14, 3)                # u 9..11
    window(w, pal, "east", 32, 19, 1, 4, 2, shutters=False, sill=False, lintel=False, glass_depth=0)   # u 14
    w.put(32, 3, 11, door("spruce", "east", False))
    w.put(32, 4, 11, door("spruce", "east", True))
    w.put(33, 2, 11, stairs("stone_brick", "west"))
    w.put(34, 1, 11, stairs("stone_brick", "west"))
    # 個人倉庫の受付棟への扉（u=30、v=24）。受付棟ができるまでは、外へ降りる段を付けておく（PHASE 6 で受付棟の床につなぐ）
    w.put(32, 3, 35, door("spruce", "east", False))
    w.put(32, 4, 35, door("spruce", "east", True))
    w.put(33, 2, 35, stairs("stone_brick", "west"))
    w.put(34, 1, 35, stairs("stone_brick", "west"))


# ---------------------------------------------------------------- 正面の柱と張り出し
def front_posts_and_jetty():
    """正面の南側（u 20..32）：1階の石の前に柱（X=7）、2階の木組み（X=6）を持ち送りで1つ外へ。"""
    for z in (29, 33, 37):
        for y in range(1, 9):
            w.put(7, y, z, SPRUCE_LOG_Y)
        w.put(7, 0, z, block("stone_bricks"))
    # 帯：柱の上の梁（X=7）と、外の持ち送り（X=6）
    for z in range(25, 38):
        w.put(7, Y2, z, beam("z"))
        w.put(6, Y2, z, stairs(pal.frame, "east", "top"))
    # 2階の木組み：柱（X=6）、漆喰（X=7）、ガラス（X=8）
    posts = (25, 29, 33, 37)
    for z in range(25, 38):
        for y in range(F2, YA):
            if z in posts:
                w.put(6, y, z, SPRUCE_LOG_Y)
                w.put(7, y, z, block(pal.plaster))
            else:
                w.put(7, y, z, block(pal.plaster))
        w.put(6, YA, z, beam("z"))
        w.put(7, YA, z, beam("z"))
    for z0 in (26, 30, 34):
        bay_window_jetty(z0)
    for z in range(25, 38):
        for y in range(F2, YA):
            if w.get(8, y, z) is None:
                w.put(8, y, z, block(pal.plaster))
    # 張り出しの北の端（Z=25）は露台の南の壁：漆喰と柱
    for y in range(F2, YA):
        w.put(7, y, 25, block(pal.plaster))
    # 柱に腕木を出して灯りを吊る（Z=29、Z=37。都市のルール⑦の壁のランタン）
    for z in (29, 37):
        wall_lantern(w, 6, 12, z, "west")


def north_jetty():
    """塔と破風の間（u 9..11、Z 14..16）の2階も、南と同じく1つ外へ張り出す（軒の出を、ほかと同じ1にする）。
    梁の両端は、塔の帯（Z=13）と、ポーチの横の壁の上の露台の床（Z=17）で受ける。窓は1つ（北の回廊の西の端）。"""
    for z in (14, 15, 16):
        w.put(7, Y2, z, beam("z"))
        w.put(6, Y2, z, stairs(pal.frame, "east", "top"))
    for y in range(F2, YA):
        for z in (14, 16):
            w.put(6, y, z, SPRUCE_LOG_Y)
            w.put(7, y, z, block(pal.plaster))
            w.put(8, y, z, block(pal.plaster))
        w.put(7, y, 15, block(pal.plaster) if y in (F2, YA - 1) else AIR)
        w.put(8, y, 15, pane() if y in (11, 12) else block(pal.plaster))
    w.put(6, YA - 1, 15, beam("z"))
    w.put(6, F2, 15, leaves("flowering_azalea_leaves"))
    w.put(5, F2, 15, trapdoor(pal.frame, "west", "bottom", True))
    for z in (14, 15, 16):
        w.put(6, YA, z, beam("z"))
        w.put(7, YA, z, beam("z"))


def bay_window_jetty(z0):
    """張り出しの2階の柱の間（幅3）：上の両角に木のアーチ、真ん中に幅1×高さ2の窓、下に花箱。"""
    zc = z0 + 1
    for y in (11, 12):
        w.put(7, y, zc, AIR)
        w.put(8, y, zc, pane())
    for y in (11, 12):
        for z in (z0, z0 + 2):
            w.put(8, y, z, block(pal.plaster))
    w.put(6, 13, z0, stairs(pal.frame, "north", "top"))
    w.put(6, 13, z0 + 2, stairs(pal.frame, "south", "top"))
    w.put(6, 13, zc, AIR)
    # 花箱
    w.put(6, 10, z0, leaves("azalea_leaves"))
    w.put(6, 10, zc, leaves("flowering_azalea_leaves"))
    w.put(6, 10, z0 + 2, leaves("azalea_leaves"))
    w.put(5, 10, zc, trapdoor(pal.frame, "west", "bottom", True))


# ---------------------------------------------------------------- 2階の木組みの壁（正面の北・正面の張り出し以外）
def timber_wall(face, plane, a0, a1, posts, windows=(), doors=(), y_top=YA - 1, shutters=True):
    """平らな木組みの壁（下の石と同じ面）。posts は柱、windows は (a, 幅)、doors は a の位置。
    shutters が False なら、雨戸と窓台を付けない（屋根のある露台に面した壁。通る人の邪魔にならないように）。"""
    for a in range(a0, a1 + 1):
        x, z = (plane, a) if face in ("west", "east") else (a, plane)
        for y in range(F2, y_top + 1):
            w.put(x, y, z, SPRUCE_LOG_Y if a in posts else block(pal.plaster))
        w.put(x, YA, z, beam("z" if face in ("west", "east") else "x"))
    for (a, width) in windows:
        window(w, pal, face, plane, a, width, 11, 2, shutters=shutters, sill=shutters, lintel=False, glass_depth=0)
    for a in doors:
        x, z = (plane, a) if face in ("west", "east") else (a, plane)
        w.put(x, F2, z, door(pal.frame, face, False))
        w.put(x, F2 + 1, z, door(pal.frame, face, True))


def upper_walls():
    # 正面の破風の下（u 12..20、X=8）：作戦室。真ん中は表の露台への扉
    timber_wall("west", 8, 17, 25, posts=(17, 20, 22, 25), windows=((19, 1), (23, 1)), doors=(21,), shutters=False)
    # 南（Z=37）：西の角（X=6）から裏まで。暖炉の煙突（X 15..17）は石
    timber_wall("south", 37, 6, 32, posts=(6, 12, 20, 24, 28, 32), windows=((10, 1), (22, 1), (26, 1), (30, 1)))
    # 北（Z=5）：塔の東から裏まで。ガルドの部屋の扉（外階段の上）と、塔の方を見る窓
    timber_wall("north", 5, 17, 32, posts=(17, 20, 24, 28, 32), windows=((21, 2), (26, 1), (30, 1)), doors=(19,))
    # 裏（東、X=32）：南半分は受付棟と接するので窓なし
    timber_wall("east", 32, 5, 37, posts=tuple(range(5, 38, 4)), windows=((7, 1), (11, 1), (15, 1), (19, 1)))


# ---------------------------------------------------------------- 玄関ポーチと表の露台
def porch():
    """古代の白い石のポーチ（X 5..7、Z 17..25）。正面に幅7のアーチ、両脇に幅2のアーチ。
    上は表の露台：正面の破風の下の、屋根のある露台（前は手すり、北は壁、南は張り出しの端）。"""
    for (x, z) in ((5, 17), (5, 25)):
        for y in range(1, 9):
            w.put(x, y, z, block("quartz_pillar", axis="y") if 2 <= y <= 6 else block("quartz_bricks"))
    for z in range(17, 26):
        w.put(5, 8, z, block("polished_diorite"))
        w.put(5, 7, z, block("polished_diorite"))
    for x in (6, 7):
        for z in (17, 25):
            for y in range(6, 9):
                w.put(x, y, z, block("polished_diorite"))
            w.put(x, 1, z, block("polished_diorite"))
    # 正面のアーチ（Z 18..24）
    for z in range(18, 25):
        for y in range(2, 7):
            w.put(5, y, z, AIR)
    w.put(5, 6, 18, stairs("quartz", "north", "top"))
    w.put(5, 6, 24, stairs("quartz", "south", "top"))
    for z in (19, 23):
        w.put(5, 7, z, stairs("quartz", "north" if z == 19 else "south", "top"))
    for z in (20, 21, 22):
        w.put(5, 7, z, AIR)
    w.put(5, 8, 21, block("chiseled_quartz_block"))
    # 両脇のアーチ（X 6..7）
    for z in (17, 25):
        w.put(6, 5, z, stairs("quartz", "west", "top"))
        w.put(7, 5, z, stairs("quartz", "east", "top"))
        for y in range(2, 5):
            for x in (6, 7):
                w.put(x, y, z, AIR)
    # 床と段
    for x in range(5, 8):
        for z in range(18, 25):
            w.put(x, 1, z, block("polished_diorite"))
    for z in range(18, 25):
        w.put(4, 1, z, stairs("polished_diorite", "east"))
        w.put(5, 1, z, block("polished_diorite"))
    # 表の露台（床 y=9、手すり y=10、天井 y=14）。前の両端の柱はポーチの柱の上に立ち、破風の梁（y=13）を受ける
    for x in range(5, 8):
        for z in range(17, 26):
            w.put(x, Y2, z, block("spruce_planks"))
    for z in (17, 25):
        for y in range(Y2, YA - 1):
            w.put(5, y, z, SPRUCE_LOG_Y)
    for z in range(18, 25):
        w.put(5, F2, z, fence(pal.trim))
    for x in (6, 7):
        for y in range(F2, YA):
            w.put(x, y, 17, block(pal.plaster))
        for z in range(17, 25):
            w.put(x, YA, z, block("spruce_planks"))
    w.put(6, YA - 1, 21, lantern(True))
    # ポーチの天井から、鎖で灯りを吊る
    for z in (19, 23):
        hang_lantern(w, 6, 8, z)
    # 大看板（入口の上のアーチの帯）と旗
    w.put(4, 8, 21, block("spruce_wall_sign", facing="west", waterlogged=False),
          sign_nbt(["", "冒険者ギルド", "コネリア", ""], color="black"))
    for z in (17, 25):
        w.put(4, 7, z, block("blue_wall_banner", facing="west"), banner_nbt([("bo", "white"), ("mr", "white")]))
    # 吊り看板（北の柱から、塔の道の方へ）
    w.put(4, 5, 17, block("spruce_wall_hanging_sign", facing="north", waterlogged=False),
          sign_nbt(["冒険者ギルド", "", "", ""], ["冒険者ギルド", "", "", ""], hanging=True))
    # 吊り看板の下に、ギルドの品（地図）を額縁で見せる（⑦）。北の柱の西の面に掛け、塔の道の方を向く
    w.item_frame(4, 4, 17, "west", "map")


# ---------------------------------------------------------------- 塔（北西の角、9×9）
def tower():
    x0, x1, z0, z1 = 8, 16, 5, 13
    for y in range(1, 30):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    corner = x in (x0, x1) and z in (z0, z1)
                    if y <= 8:
                        st = block("quartz_bricks") if corner else white_stone()
                    else:
                        st = block("stone_bricks") if corner else gray_stone()
                    w.put(x, y, z, st)
    # 帯：古代の土台の上（y=9）と鐘の間の床の高さ（y=24）に、外へ出た段
    for y in (9, 24):
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)) and not (x == x1 + 1 or z == z1 + 1):
                    face = "west" if x == x0 - 1 else ("north" if z == z0 - 1 else None)
                    if face is None:
                        continue
                    w.put(x, y, z, stairs("stone_brick", "east" if face == "west" else "south", "top"))
    # 中の床と芯（らせん階段の真ん中）
    w.fill(9, 1, 6, 15, 1, 12, block("stone_bricks"))
    w.fill(9, 2, 6, 15, 2, 12, block("polished_andesite"))
    w.fill(11, 1, 8, 13, YB - 1, 10, block("stone_bricks"))
    spiral()
    # 扉：1階（南の面 u=8、v=2）、2階（東の面、ガルドの部屋へ）、屋根裏（南の面）
    for (x, y, z, facing) in ((10, F1, 13, "south"), (16, F2, 8, "east"), (12, FA, 13, "south")):
        w.put(x, y, z, door("spruce", facing, False))
        w.put(x, y + 1, z, door("spruce", facing, True))
    # 狭間（細い窓）：西と北の面
    for (x, y, z) in ((8, 13, 9), (8, 14, 9), (12, 12, 5), (12, 13, 5), (12, 22, 5), (12, 23, 5), (8, 19, 9), (8, 20, 9)):
        w.put(x, y, z, pane())
    # 鐘の間（床 y=24、開口 y 25..28）。開口の足もと（y 25）は手すり（柵）にして、外の屋根へ出られないようにする
    for (face, a0) in (("west", 8), ("north", 11), ("south", 11)):
        for k in range(3):
            for y in range(25, 28):
                x, z = ((8 if face == "west" else 16), a0 + k) if face in ("west", "east") else (a0 + k, (5 if face == "north" else 13))
                w.put(x, y, z, fence(pal.trim) if y == 25 else AIR)
        if face == "west":
            x = 8
            w.put(x, 27, 8, stairs("stone_brick", "north", "top"))
            w.put(x, 27, 10, stairs("stone_brick", "south", "top"))
            w.put(x, 28, 9, block("chiseled_stone_bricks"))
        else:
            z = 5 if face == "north" else 13
            w.put(11, 27, z, stairs("stone_brick", "west", "top"))
            w.put(13, 27, z, stairs("stone_brick", "east", "top"))
            w.put(12, 28, z, block("chiseled_stone_bricks"))
    for x in range(9, 16):
        for z in range(6, 13):
            if 9 <= x <= 13 and 6 <= z <= 7:
                continue      # 上ってきた階段の口（北の側と北西の角）
            if x in (9, 10) and z == 8:
                continue      # 最後の段（床に上がる階段）
            w.put(x, YB, z, block("spruce_planks"))
    for x in range(11, 14):
        w.put(x, FB, 8, fence(pal.trim))
    for z in (6, 7):
        w.put(14, FB, z, fence(pal.trim))
    # 鐘：真ん中の梁から吊る
    for x in range(9, 16):
        w.put(x, 29, 9, beam("x"))
    w.put(12, 28, 9, block("bell", attachment="ceiling", facing="west", powered=False))
    # 軒の帯と、緑青の銅の四角すい（先 +34）
    copper = Palette(seed=3, roof="waxed_oxidized_cut_copper", roof_block="waxed_oxidized_cut_copper", trim="dark_oak")
    top = Pyramid(w, copper, 12, 9, 5, 29, pitch=1, thickness=2).build()   # 軒は壁の上と同じ高さ、先は +34
    w.put(12, top, 9, block("lightning_rod", facing="up", powered=False, waterlogged=False))
    # 旗（西の面、鐘の間の下）
    w.put(7, 21, 9, block("blue_wall_banner", facing="west"), banner_nbt([("bo", "white"), ("mr", "white")]))
    # 中の灯り：踊り場の外の角に置く
    for (x, y, z) in ((9, F1, 6), (15, 7, 12), (9, 13, 6), (15, 17, 12), (15, 21, 6), (15, FB, 12)):
        w.put(x, y, z, lantern(False))


def spiral():
    """らせん階段（幅2）。1階の扉（+2）→ 2階（+9、東の面の扉）→ 屋根裏（+14、南の面の扉）→ 鐘の間（+24）。

    塔の中（X 9..15、Z 6..12）を、芯（X 11..13、Z 8..10）のまわりに回る。各「切れ」は幅2の2マス。
    """
    SW, SE, NE, NW = ("corner", (9, 10), (11, 12)), ("corner", (14, 15), (11, 12)), ("corner", (14, 15), (6, 7)), \
        ("corner", (9, 10), (6, 7))

    def south(x):
        return ("x", x, (11, 12), "east")

    def east(z):
        return ("z", z, (14, 15), "north")

    def north(x):
        return ("x", x, (6, 7), "west")

    def west(z):
        return ("z", z, (9, 10), "south")
    # （切れ, 足もとの高さ）
    path = [(SW, 3), (south(11), 4), (south(12), 5), (south(13), 6), (SE, 7), (east(10), 8), (east(9), 9), (east(8), 10),
            (NE, 10), (north(13), 11), (north(12), 12), (north(11), 13), (NW, 13), (west(8), 13), (west(9), 13),
            (west(10), 13), (SW, 13), (south(11), 14), (south(12), 15), (south(13), 16), (SE, 17), (east(10), 18),
            (east(9), 19), (east(8), 20), (NE, 21), (north(13), 22), (north(12), 23), (north(11), 24), (NW, 24),
            (west(8), 25)]
    prev = None
    prev_facing = None
    for (piece, feet) in path:
        if piece[0] == "corner":
            xs, zs = piece[1], piece[2]
            cells = [(x, z) for x in range(xs[0], xs[1] + 1) for z in range(zs[0], zs[1] + 1)]
            # 入ってくる側の2マス（前の切れに接する列）
            entry = {"east": [(xs[0], z) for z in range(zs[0], zs[1] + 1)],
                     "west": [(xs[1], z) for z in range(zs[0], zs[1] + 1)],
                     "north": [(x, zs[1]) for x in range(xs[0], xs[1] + 1)],
                     "south": [(x, zs[0]) for x in range(xs[0], xs[1] + 1)]}.get(prev_facing, [])
            rise = prev is not None and feet > prev
            for (x, z) in cells:
                if rise and (x, z) in entry:
                    w.put(x, feet - 1, z, stairs("stone_brick", prev_facing))
                else:
                    w.put(x, feet - 1, z, block("polished_andesite"))
                if feet - 2 >= 1:
                    w.put(x, feet - 2, z, block("stone_bricks"))
        else:
            axis, a, across, facing = piece
            rise = prev is not None and feet > prev
            for c in range(across[0], across[1] + 1):
                x, z = (a, c) if axis == "x" else (c, a)
                if rise:
                    w.put(x, feet - 1, z, stairs("stone_brick", facing))
                    if feet - 2 >= 1:
                        w.put(x, feet - 2, z, stairs("stone_brick", {"east": "west", "west": "east", "north": "south",
                                                                       "south": "north"}[facing], "top"))
                else:
                    w.put(x, feet - 1, z, block("polished_andesite"))
                    if feet - 2 >= 1:
                        w.put(x, feet - 2, z, block("stone_bricks"))
            prev_facing = facing
        prev = feet


# ---------------------------------------------------------------- 北の外階段
def north_stair():
    """北の壁の外の外階段（歩く所は Z 2..4、Z=1 は手すりの台）。
    地面（X=31 の側）から6段で踊り場（+6）、3段で上の踊り場（+9、ガルドの部屋の扉の前）。
    上の踊り場は4本の石の柱で受け、下はあけて、酒場の窓（X 17..19・21..23）の前をふさがない。"""
    # 下の6段：中は石で埋め、外の側（Z=1）は段に沿った石の台の上に手すり
    for (x, y) in ((31, 1), (30, 2), (29, 3), (28, 4), (27, 5), (26, 6)):
        for z in (2, 3, 4):
            w.put(x, y, z, stairs("stone_brick", "west"))
            for yy in range(1, y):
                w.put(x, yy, z, block("stone_bricks"))
        for yy in range(1, y + 1):
            w.put(x, yy, 1, block("stone_bricks"))
        w.put(x, y + 1, 1, fence(pal.trim))
    # 踊り場（+6）
    for x in (24, 25):
        for z in (1, 2, 3, 4):
            for yy in range(1, 6):
                w.put(x, yy, z, block("stone_bricks"))
            w.put(x, 6, z, block("polished_andesite") if z > 1 else block("stone_bricks"))
        w.put(x, 7, 1, fence(pal.trim))
    # 上の3段（下はあけた石の段。手すりの台の列 Z=1 も段にする）
    for (x, y) in ((23, 7), (22, 8), (21, 9)):
        for z in (1, 2, 3, 4):
            w.put(x, y, z, stairs("stone_brick", "west"))
            w.put(x, y - 1, z, stairs("stone_brick", "east", "top"))
        w.put(x, y + 1, 1, fence(pal.trim))
    # 上の踊り場（+9、X 16..20）
    for x in range(16, 21):
        for z in (1, 2, 3, 4):
            w.put(x, Y2, z, block("polished_andesite"))
        w.put(x, F2, 1, fence(pal.trim))
    for z in (2, 3, 4):
        w.put(16, F2, z, fence(pal.trim))
    # 支え：4本の柱と、柱の間の持ち送り（逆さの階段）。下に灯り
    for x in (16, 20):
        for z in (1, 4):
            for y in range(1, Y2):
                w.put(x, y, z, block("stone_bricks"))
    for z in (1, 4):
        w.put(17, Y2 - 1, z, stairs("stone_brick", "west", "top"))
        w.put(19, Y2 - 1, z, stairs("stone_brick", "east", "top"))
    for x in (16, 20):
        w.put(x, Y2 - 1, 2, stairs("stone_brick", "north", "top"))
        w.put(x, Y2 - 1, 3, stairs("stone_brick", "south", "top"))
    w.put(18, Y2 - 1, 2, lantern(True))
    # 上の踊り場の小さな屋根（壁から北へ下がる）
    for x in range(16, 22):
        w.put(x, 14, 4, stairs("brick", "south"))
        w.put(x, 13, 3, stairs("brick", "south"))
        w.put(x, 12, 2, stairs("brick", "south"))
        w.put(x, 11, 1, stairs("brick", "south"))
        w.put(x, 13, 4, block("bricks"))
        w.put(x, 12, 3, block("bricks"))
    for (x, z) in ((16, 1), (21, 1)):
        w.put(x, F2, z, SPRUCE_LOG_Y)
    w.put(17, 12, 4, lantern(True))
    # 地面からの入り口の灯り
    w.put(32, 1, 4, wall("stone_brick"))
    w.put(32, 2, 4, fence(pal.trim))
    w.put(32, 3, 4, lantern(False))


# ---------------------------------------------------------------- 屋根
main_roof = Roof(w, pal, 6, 32, 5, 38, YA, ridge="z", pitch=1, side_overhang=1, end_overhang=1)
pav_roof = Roof(w, pal, 5, 18, 17, 25, YA - 1, ridge="x", pitch=2, side_overhang=1, end_overhang=1, ends=("start",))


# 屋根の窓（壁から立ち上がるドーマー、幅3）：(斜面, z0, z1)。斜面 0 は正面（面は張り出しの漆喰の X=7）、1 は裏（面は X=32）。
# 正面は、張り出しの真ん中の窓の真上に1つ（左右の窓の上だと、破風の軒や南の端とぶつかる）。裏は2つ（2階の窓の上と、受付棟の上）。
DORMER_SPEC = ((0, 30, 32), (1, 14, 16), (1, 26, 28))


def dormer_roof(side, z0, z1):
    """屋根の窓の小さな屋根（1：1、本館と同じ傾き）。奥は本館の屋根の中に入って隠れる。"""
    if side == 0:
        r = Roof(w, pal, 7, 11, z0, z1, 18, ridge="x", pitch=1, ends=("start",), courses=False)
    else:
        r = Roof(w, pal, 27, 32, z0, z1, 18, ridge="x", pitch=1, ends=("end",), courses=False)
    r.side, r.zs = side, (z0, z1)
    return r


DORMERS = tuple(dormer_roof(*d) for d in DORMER_SPEC)
ROOFS = (main_roof, pav_roof) + DORMERS


def in_tower(x, y, z):
    return x <= 16 and z <= 13


def chimney_zone(x, y, z):
    return (15 <= x <= 17 and 38 <= z <= 39) or (33 <= x <= 34 and 7 <= z <= 8)


def main_cut(x, y, z):
    """本館の屋根を置かない所：屋根の窓の前。"""
    for (side, z0, z1) in DORMER_SPEC:
        if z0 <= z <= z1 and ((side == 0 and x <= 7) or (side == 1 and x >= 32)):
            return True
    return False


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        if in_tower(x, y, z) or chimney_zone(x, y, z):
            return True
        if 8 <= x <= 32 and 5 <= z <= 37 and y < YA:
            return True       # 建物の中（屋根裏の床より下）には、屋根の部品を置かない
        if me is main_roof and main_cut(x, y, z):
            return True
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))
    for d in DORMERS:
        dormer_walls(d)
    # 正面の破風の軒と本館の軒が出会う角（Z=16・26）：本館の軒の縁取りを通し、破風の下の縁取りは外す
    for z in (16, 26):
        w.put(5, YA - 1, z, stairs(pal.trim, "east", "top"))
        for x in (5, 6, 7):
            if w.name_at(x, YA - 2, z).endswith("_stairs"):
                w.put(x, YA - 2, z, AIR)
    # 塔と破風の間の小さな軒（Z 14..15）の北の端：塔の西の面に向かって縁を上げる
    for (x, y) in ((5, 14), (6, 15), (7, 16)):
        w.put(x, y, 13, block(pal.trim + "_planks"))
        w.put(x, y + 1, 13, stairs(pal.trim, "east"))
    w.put(5, YA - 1, 13, stairs(pal.trim, "east", "top"))


def dormer_walls(d):
    """屋根の窓の前の壁（下の壁の梁の上に立つ。両端は柱、真ん中に幅1×高さ2の窓）と三角の壁。中は屋根裏から歩いて窓まで行ける。"""
    z0, z1 = d.zs
    zc = (z0 + z1) // 2
    xf = 7 if d.side == 0 else 32
    for y in range(FA, d.y0):
        for z in (z0, z1):
            w.put(xf, y, z, SPRUCE_LOG_Y)
        w.put(xf, y, zc, block(pal.plaster))
    for y in (FA + 1, FA + 2):
        w.put(xf, y, zc, pane())
    gable_wall(w, pal, d, xf)
    for x in (range(8, 11) if d.side == 0 else range(28, 32)):
        for y in range(FA, d.y0):
            w.put(x, y, zc, AIR)


def gables():
    # 北の妻（Z=5、塔の東の部分だけ）
    y = main_roof.y0
    while True:
        ext = main_roof.gable_extent(y)
        if ext is None:
            break
        lo, hi = max(ext[0], 6), min(ext[1], 32)
        if lo > hi:
            break
        for x in range(max(lo, 17), hi + 1):
            if y == main_roof.y0 or y == 20:
                w.put(x, y, 5, beam("x"))
            elif x in (20, 24, 28):
                w.put(x, y, 5, SPRUCE_LOG_Y)
            else:
                w.put(x, y, 5, block(pal.plaster))
        y += 1
    window(w, pal, "north", 5, 22, 1, 16, 2, shutters=False, lintel=False)
    # 南の妻（Z=38、1つ外へ張り出し、下に持ち送り）。煙突（X 15..17）は石
    gable_wall(w, pal, main_roof, 38, posts=(12, 24, 28), beam_rows=(20,))
    for x in range(6, 33):
        if 14 <= x <= 18:
            continue
        w.put(x, YA - 1, 38, stairs(pal.frame, "north", "top"))
    window(w, pal, "south", 38, 20, 2, 16, 2, shutters=False, lintel=False)
    window(w, pal, "south", 38, 26, 1, 16, 2, shutters=False, lintel=False)
    # 正面の破風（X=5、表の露台の上）：いちばん下の梁は露台の前の梁。十字の桟の窓
    gable_wall(w, pal, pav_roof, 5, posts=(19, 23), beam_rows=(19,))
    window(w, pal, "west", 5, 20, 3, 15, 3, shutters=False, lintel=False, mullion=1, transom=16)
    # 頂の飾りの小塔：正面の破風の頂だけ（公共の建物の目印。南の妻の頂は煙突のすぐ横なので付けない）。
    # 破風板のいちばん上（上つきのハーフ）の上に立てる
    x, _, z = pav_roof.apex("start")
    finial(w, pal, x, pav_roof.y0 + pav_roof.pitch * pav_roof.half + pav_roof.pitch, z, height=3, top="lantern", spike=True)


# ---------------------------------------------------------------- 煙突
def chimneys():
    # 広間の暖炉の煙突（南の妻の外、X 14..18 → 上は X 15..17）
    for y in range(1, 30):
        for x in range(14, 19):
            if y >= YA and x in (14, 18):
                continue
            for z in (38, 39):
                w.put(x, y, z, block("stone_bricks") if rng.random() < 0.85 else block("cracked_stone_bricks"))
    for x in range(15, 18):
        w.put(x, 30, 38, block("bricks"))
        w.put(x, 30, 39, block("bricks"))
    w.put(16, 31, 38, block("campfire", lit=True, facing="north", signal_fire=True, waterlogged=False))
    w.put(16, 30, 38, block("hay_block", axis="y"))
    w.put(16, 31, 39, slab("stone_brick", "bottom"))
    for x in (15, 17):
        w.put(x, 31, 38, slab("stone_brick", "bottom"))
        w.put(x, 31, 39, slab("stone_brick", "bottom"))
    # 台所の煙突（裏の壁の外、X 33..34、Z 7..8）
    for y in range(1, 21):
        for x in (33, 34):
            for z in (7, 8):
                w.put(x, y, z, block("bricks") if y > 2 else block("stone_bricks"))
    w.put(33, 21, 7, block("campfire", lit=True, facing="north", signal_fire=True, waterlogged=False))
    w.put(33, 20, 7, block("hay_block", axis="y"))
    for (x, z) in ((34, 7), (33, 8), (34, 8)):
        w.put(x, 21, z, slab("brick", "bottom"))


# ---------------------------------------------------------------- 1階の中
def interior_ground():
    # 柱（吹き抜けの4隅、掲示板の壁の両端、酒場と台所の境）
    for (u, v) in ((12, 8), (20, 8), (12, 16), (20, 16), (8, 16), (24, 8), (24, 16)):
        for y in range(F1, 8):
            put(u, v, y, SPRUCE_LOG_Y)
    # 絨毯の軸（u 15..17、v 1..9）
    for u in range(15, 18):
        for v in range(1, 10):
            put(u, v, F1, block("red_carpet"))
    # 受付のカウンター（高さ1.5）
    for (u, v) in [(u, 10) for u in range(13, 20)] + [(13, v) for v in range(11, 14)] + [(19, v) for v in range(11, 14)]:
        put(u, v, F1, block("spruce_planks"))
        put(u, v, F1 + 1, slab("dark_oak", "bottom"))
    put(19, 12, F1 + 1, block("bell", attachment="floor", facing="east", powered=False))    # 交換の窓口の呼び鈴
    put(14, 10, F1 + 1, block("candle", candles=2, lit=True, waterlogged=False))
    # 受付の裏：棚と名簿の台
    for u in (14, 15, 17, 18):
        put(u, 15, F1, block("barrel", facing="west", open=False))
        put(u, 15, F1 + 1, block("bookshelf"))
    put(16, 15, F1, block("lectern", facing="west", has_book=True, powered=False))
    # 受付の看板（v=8 の梁から吊る）
    put(16, 8, 7, block("spruce_hanging_sign", rotation=4, attached=False, waterlogged=False),
        sign_nbt(["受付", "", "", ""], ["受付", "", "", ""], hanging=True))
    # 依頼の掲示板の壁（u=24、v 9..15）：北の面が依頼、南の面がパーティ募集
    for v in range(9, 16):
        for y in range(F1, F1 + 3):
            put(24, v, y, block("spruce_planks") if v != 12 else SPRUCE_LOG_Y)
        put(24, v, F1 + 3, beam("x"))
    for y in range(F1 + 3, 8):
        put(24, 12, y, SPRUCE_LOG_Y)
    for (v, y) in ((9, 3), (10, 4), (11, 3), (13, 4), (14, 3), (15, 4), (10, 5), (14, 5), (9, 5)):
        put(23, v, y, trapdoor("birch", "north", "bottom", True))
    for (v, y) in ((9, 4), (11, 5), (13, 3), (15, 5), (10, 3)):
        put(25, v, y, trapdoor("birch", "south", "bottom", True))
    put(23, 12, F1 + 3, block("spruce_wall_sign", facing="north", waterlogged=False), sign_nbt(["", "依頼の掲示板", "", ""]))
    put(25, 12, F1 + 3, block("spruce_wall_sign", facing="south", waterlogged=False), sign_nbt(["", "パーティ募集", "結び紐", ""]))
    # 暖炉（南の壁、v 7..9）
    for v in range(6, 11):
        for y in range(F1, 9):
            put(31, v, y, block("stone_bricks"))
    for v in (7, 8, 9):
        for y in (F1, F1 + 1):
            put(31, v, y, AIR)
    put(31, 8, F1, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
    for v in range(6, 11):
        put(30, v, F1 + 2, stairs("spruce", "south", "top"))
        put(30, v, Y1, block("stone_bricks"))
    # 談話の場：長椅子、敷物、冒険日誌の書見台
    for v in range(6, 11):
        put(27, v, F1, stairs("spruce", "north"))
    for u in range(28, 31):
        put(u, 5, F1, stairs("spruce", "west"))
        put(u, 11, F1, stairs("spruce", "east"))
    for u in range(28, 30):
        for v in range(6, 11):
            put(u, v, F1, block("red_carpet"))
    put(30, 13, F1, block("lectern", facing="west", has_book=True, powered=False))
    # 待合の長椅子（正面の窓の下、v=2）
    for u in (21, 22, 23, 25, 26, 27, 29, 30, 31):
        put(u, 1, F1, stairs("spruce", "west"))
    # 酒場：台所の壁ぎわの棚、カウンター、腰かけ、長い机
    for u in range(1, 7):
        put(u, 17, F1, block("barrel", facing="west", open=False))
        put(u, 17, F1 + 1, block("barrel", facing="west", open=False))
        put(u, 17, F1 + 2, block("spruce_slab", type="bottom", waterlogged=False))
    for u in range(1, 7):
        put(u, 15, F1, block("spruce_planks"))
        put(u, 15, F1 + 1, slab("dark_oak", "bottom"))
    for u in (2, 4, 6):
        put(u, 14, F1, fence("spruce"))
        put(u, 14, F1 + 1, block("brown_carpet"))
    for (u0, u1) in ((1, 4), (6, 9)):
        for u in range(u0, u1 + 1):
            put(u, 10, F1, block("spruce_slab", type="top", waterlogged=False))
            put(u, 9, F1, stairs("spruce", "west"))
            put(u, 11, F1, stairs("spruce", "east"))
        put(u0 + 1, 10, F1 + 1, block("candle", candles=3, lit=True, waterlogged=False))
    # 台所
    for u in range(1, 13):
        for y in range(F1, 8):
            put(u, 18, y, block("spruce_planks"))
    for v in range(18, 24):
        for y in range(F1, 8):
            put(12, v, y, block("spruce_planks"))
    for y in range(F1, 8):
        put(12, 18, y, SPRUCE_LOG_Y)
    put(7, 18, F1, door("spruce", "east", False))
    put(7, 18, F1 + 1, door("spruce", "east", True))
    put(12, 22, F1, door("spruce", "south", False))       # 大階段の下の食料庫へ
    put(12, 22, F1 + 1, door("spruce", "south", True))
    for u in (13, 14, 15):
        for y in range(F1, F1 + 5):
            put(u, 20, y, block("spruce_planks"))
    for u in (14, 15):
        put(u, 23, F1, block("barrel", facing="up", open=False))
    put(15, 21, F1, block("barrel", facing="up", open=False))
    put(15, 22, F1, block("barrel", facing="up", open=False))
    put(14, 23, F1 + 1, lantern(False))
    put(2, 23, F1, block("smoker", facing="west", lit=True))
    put(3, 23, F1, block("furnace", facing="west", lit=False))
    for u in (2, 3):
        for y in range(F1 + 1, 8):
            put(u, 23, y, block("bricks"))
    put(1, 20, F1, block("cauldron"))
    put(1, 21, F1, block("barrel", facing="up", open=False))
    for (u, v) in ((5, 21), (6, 21), (5, 22), (6, 22)):
        put(u, v, F1, block("spruce_planks"))
    put(5, 21, F1 + 1, block("crafting_table"))
    for u in (9, 10, 11):
        put(u, 23, F1, block("barrel", facing="up", open=False))
    for v in (19, 20, 21):
        put(11, v, F1, block("barrel", facing="up", open=False))
    put(11, 19, F1 + 1, block("barrel", facing="up", open=False))
    # 大階段（v 21..23、u=19 から北へ7段で2階へ）。広間の側（v=20）は側桁（段の下を板でふさぐ）の上に手すり
    for k, u in enumerate(range(19, 12, -1)):
        y = F1 + k
        for v in (21, 22, 23):
            put(u, v, y, stairs("spruce", "north"))
            if u >= 16:
                for yy in range(F1, y):
                    put(u, v, yy, block("spruce_planks"))
            else:
                put(u, v, y - 1, stairs("spruce", "south", "top"))
        for yy in range(F1, y + 1):
            put(u, 20, yy, block("spruce_planks"))
        put(u, 20, y + 1, fence(pal.trim))
    # 右奥：塔の地図の壁と、倉庫へ行く人の長椅子
    for u in range(22, 29):
        put(u, 23, F1, block("spruce_planks"))
        for y in range(F1 + 1, F1 + 4):
            put(u, 23, y, block("birch_planks"))
        put(u, 23, F1 + 4, block("spruce_planks"))
    for u in (22, 28):
        put(u, 22, F1, block("cartography_table"))
    put(25, 22, F1 + 4, block("spruce_wall_sign", facing="west", waterlogged=False), sign_nbt(["", "塔の地図", "", ""]))
    for u in (29, 30, 31):
        put(u, 20, F1, stairs("spruce", "east"))
    w.put(31, 6, 35, block("spruce_wall_sign", facing="west", waterlogged=False), sign_nbt(["", "個人倉庫", "→", ""]))
    w.put(10, 6, 14, block("spruce_wall_sign", facing="south", waterlogged=False), sign_nbt(["", "見張りの塔", "鐘の間", ""]))
    # 灯り：梁か、鎖で2階の床から吊るランタン
    for (u, v) in ((4, 12), (9, 12), (28, 4), (28, 13), (22, 4), (10, 4), (4, 21), (8, 21), (26, 19), (30, 18)):
        x, z = XZ(u, v)
        if w.get(x, 8, z) is None:
            w.put(x, 8, z, block("chain", axis="y", waterlogged=False))
        w.put(x, 7, z, lantern(True))


# ---------------------------------------------------------------- 2階の中
def partition(points, doors=(), y0=F2, y1=YA - 1):
    """間仕切り（板の壁、端と扉の両脇は柱）。points は (u, v) の並び。"""
    for (u, v) in points:
        for y in range(y0, y1 + 1):
            put(u, v, y, block("spruce_planks"))
    for (u, v, facing) in doors:
        put(u, v, y0, door("spruce", facing, False))
        put(u, v, y0 + 1, door("spruce", facing, True))


def interior_upper():
    # 吹き抜けの柱（2階）と手すり
    for (u, v) in ((12, 8), (20, 8), (12, 16), (20, 16)):
        for y in range(F2, YA):
            put(u, v, y, SPRUCE_LOG_Y)
    for v in range(9, 16):
        put(12, v, F2, fence(pal.trim))
        put(20, v, F2, fence(pal.trim))
    for u in range(13, 20):
        put(u, 16, F2, fence(pal.trim))
        put(u, 8, F2, fence(pal.trim))         # 作戦室の中の露台
    # 大きな吊り灯り（屋根裏の床の梁から）
    for y in (12, 13):
        put(16, 12, y, block("chain", axis="y", waterlogged=False))
    for (u, v) in ((15, 12), (17, 12), (16, 11), (16, 13)):
        put(u, v, 11, fence(pal.trim))
        put(u, v, 10, lantern(True))
    put(16, 12, 11, block("dark_oak_planks"))
    put(16, 12, 10, lantern(True))
    # 間仕切り
    partition([(8, v) for v in range(9, 24)], doors=((8, 12, "south"), (8, 20, "south")))
    for v in (16,):
        partition([(u, v) for u in range(1, 8)])
    partition([(24, v) for v in range(0, 24) if v not in (8, 16)], doors=((24, 6, "north"), (24, 18, "north")))
    partition([(u, 12) for u in range(25, 32)])
    partition([(12, v) for v in range(1, 8)], doors=((12, 4, "north"),))
    partition([(20, v) for v in range(1, 8)], doors=((20, 4, "south"),))
    for (u, v) in ((8, 16), (24, 8), (24, 16)):
        for y in range(F2, YA):
            put(u, v, y, SPRUCE_LOG_Y)
    # 大階段の口の手すり
    for u in range(13, 20):
        put(u, 20, F2, fence(pal.trim))
    for v in (21, 22, 23):
        put(20, v, F2, fence(pal.trim))
    # 作戦室：地図の机（製図台）
    for u in range(14, 19):
        put(u, 4, F2, block("cartography_table"))
    for u in (14, 16, 18):
        put(u, 3, F2, stairs("spruce", "west"))
        put(u, 5, F2, stairs("spruce", "east"))
    # ギルドマスター室：扉を向いた机、騎士団の11人をしのぶ台（ろうそく11本）、本棚
    for v in (11, 12, 13):
        put(4, v, F2, slab("dark_oak", "top"))
    put(3, 12, F2, stairs("dark_oak", "north"))
    put(4, 13, F2 + 1, block("candle", candles=1, lit=True, waterlogged=False))
    for (u, n) in ((1, 4), (2, 4), (3, 3)):
        put(u, 15, F2, block("chiseled_stone_bricks"))
        put(u, 15, F2 + 1, block("candle", candles=n, lit=True, waterlogged=False))
    for v in (9, 10):
        for y in range(F2, F2 + 3):
            put(1, v, y, block("bookshelf"))
    # 休憩室：寝台5つ
    for v in (18, 20, 22):
        put(1, v, F2, block("red_bed", facing="north", part="head", occupied=False))
        put(2, v, F2, block("red_bed", facing="north", part="foot", occupied=False))
    for v in (18, 22):
        put(7, v, F2, block("red_bed", facing="south", part="head", occupied=False))
        put(6, v, F2, block("red_bed", facing="south", part="foot", occupied=False))
    for v in (19, 21):
        put(1, v, F2, block("chest", facing="south", type="single", waterlogged=False))
    # 事務室：机2つ、本棚、暖炉
    for u in range(26, 30):
        put(u, 3, F2, slab("spruce", "top"))
        put(u, 9, F2, slab("spruce", "top"))
    for v in range(1, 5):
        for y in range(F2, F2 + 3):
            put(25, v, y, block("bookshelf"))
    for v in range(6, 11):
        for y in range(F2, YA):
            put(31, v, y, block("stone_bricks"))
    put(31, 8, F2, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
    put(31, 8, F2 + 1, AIR)
    # 資料室：本棚の列と読み物の机
    for u in range(26, 32):
        for y in range(F2, F2 + 3):
            put(u, 23, y, block("bookshelf"))
            put(u, 13, y, block("bookshelf"))
    for u in range(27, 30):
        put(u, 18, F2, slab("spruce", "top"))
    # 部屋の名前の札（回廊の側、扉の横の目の高さ）
    for (u, v, facing, text) in ((11, 5, "north", "作戦室"), (21, 3, "south", "作戦室"), (9, 13, "south", "ギルドマスター室"),
                                 (9, 21, "south", "休憩室"), (23, 7, "north", "事務室"), (23, 19, "north", "資料室")):
        put(u, v, F2 + 2, block("spruce_wall_sign", facing=facing, waterlogged=False), sign_nbt(["", text, "", ""]))
    # 灯り
    for (u, v) in ((4, 12), (4, 20), (28, 6), (28, 18), (10, 4), (22, 4), (10, 18), (22, 18), (16, 2)):
        put(u, v, 13, lantern(True))


# ---------------------------------------------------------------- 屋根裏の中
def wall_to_roof(u, v, y0=FA):
    """屋根裏の間仕切り：床から屋根の裏まで板で埋める。"""
    x, z = XZ(u, v)
    y = y0
    while y < H and w.get(x, y, z) is None:
        w.put(x, y, z, block("spruce_planks"))
        y += 1


def interior_attic():
    """屋根裏（屋根を置いたあとに作る。間仕切りを屋根の裏まで届かせるため）。"""
    # リリアの部屋（u 26..32、v 9..15。南は妻の壁）の間仕切り
    for v in range(8, 17):
        wall_to_roof(25, v)
    for u in range(26, 33):
        wall_to_roof(u, 8)
        wall_to_roof(u, 16)
    put(25, 12, FA, door("spruce", "north", False))
    put(25, 12, FA + 1, door("spruce", "north", True))
    put(30, 14, FA, block("lime_bed", facing="south", part="head", occupied=False))
    put(29, 14, FA, block("lime_bed", facing="south", part="foot", occupied=False))
    put(26, 9, FA, slab("spruce", "top"))
    put(27, 9, FA, slab("spruce", "top"))
    for u in range(27, 31):
        for v in range(10, 13):
            put(u, v, FA, block("lime_carpet"))
    put(26, 15, FA, block("chest", facing="north", type="single", waterlogged=False))
    put(27, 9, FA + 1, lantern(False))
    # 物置：樽と箱（裏の屋根の窓の前 u10・u22 はあける）
    for (u0, u1, v) in ((1, 6, 19), (12, 20, 19), (24, 24, 19), (26, 31, 19), (10, 11, 3)):
        for u in range(u0, u1 + 1):
            put(u, v, FA, block("barrel", facing="up", open=False) if (u + v) % 3 else block("chest", facing="west", type="single", waterlogged=False))
    for (u, v) in ((3, 11), (14, 6), (14, 18), (28, 18), (30, 5)):
        put(u, v, FA, lantern(False))


# ---------------------------------------------------------------- まわり
def surroundings():
    # 再建のランタン灯（小広場の北と南の角。都市のルール⑦の両腕の形：塔の道と小広場の両方を照らす）
    for z in (13, 29):
        lamp_post(w, 1, 1, z, arms=("west", "east"))
    # 長椅子（小広場の北と南の帯、小広場の方を向く）
    for (x, z, facing) in ((3, 11, "north"), (4, 11, "north"), (3, 31, "south"), (4, 31, "south")):
        w.put(x, 1, z, stairs("spruce", facing))
    # 裏口の外：樽と箱（台所の荷物）、灯り
    for (x, z) in ((33, 13), (34, 13), (33, 14)):
        w.put(x, 1, z, block("barrel", facing="up", open=False))
    w.put(34, 1, 14, block("hay_block", axis="y"))
    w.put(33, 5, 11, lantern(True))
    w.put(33, 6, 11, slab("brick", "bottom"))
    for z in (10, 11, 12):
        w.put(33, 6, z, slab("brick", "bottom"))
    # 北西の庭（塔の道の側）と、南の植え込みの帯（環状路の側）の再建のランタン灯。街全体の通しの確かめで、
    # この2か所が暗くて湧ける地面だったので足した（都市のルール⑦：塔の道の側は小広場の2本と同じ両腕、
    # 環状路の側は通りの端なので片腕。南の1本は、環状路を作るときの灯りの並びの1本目になる）
    lamp_post(w, 1, 1, 2, arms=("west", "east"))
    lamp_post(w, 13, 1, 39, arms=("south",))


# ---------------------------------------------------------------- 組み立て
def build():
    ground()
    floors()
    stone_walls()
    ground_floor_openings()
    front_posts_and_jetty()
    north_jetty()
    upper_walls()
    porch()
    tower()
    north_stair()
    interior_ground()
    interior_upper()
    roofs()
    gables()
    chimneys()
    interior_attic()
    surroundings()
    w.finalize()


build()

# 見本の絵を描く向き：目の位置と見る所（構造物の中の座標。y=0 が地面）。CI の写真（CiScreenshots.java の MAP_VIEWS）と同じ
VIEWS = {
    "front": ((-30, 16, 21), (18, 13, 21)),       # 正面（西）から
    "left": ((-22, 22, -18), (16, 12, 16)),       # 左前（北西）から：塔と北の妻、外階段
    "right": ((-20, 18, 62), (16, 12, 24)),       # 右前（南西）から：張り出しと南の妻、煙突
    "close": ((-15, 11, 21), (6, 13, 21)),        # 入口の近く：ポーチ、表の露台、破風
    "aerial": ((-16, 42, 54), (20, 12, 21)),      # 上から：屋根の交わりと屋根の窓
    "back": ((66, 26, -14), (20, 12, 18)),        # 裏（北東）から：裏口、台所の煙突、裏の屋根の窓
}
# 中の絵：目の位置と見る所（ゲームの中で歩いて見る高さ）
INSIDE = {
    "hall": ((9.5, 5.2, 21.5), (30, 4, 21.5)),          # 入口から広間と受付
    "tavern": ((21.5, 5.2, 15.5), (21.5, 4, 6)),        # 酒場
    "atrium": ((22.5, 11.6, 27.5), (19, 7, 19)),        # 2階の回廊から吹き抜け
    "master": ((20.5, 11.6, 12.5), (20, 10, 6)),        # ギルドマスター室
}


def cut_world(y_cut):
    """y_cut より上を外した設計データ（上から見て、その階の中を見る）。"""
    cw = World(W, H, D)
    for (x, y, z), st in w.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
    return cw


def render_views(out_dir, width=960, height=540, fov=70):
    """6つの向きの見本の絵と、1枚にまとめた絵。各階を上で切って真上から見る絵（1階・2階・屋根裏）と、中の絵も描く。"""
    from PIL import Image
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    scene = Scene(w, ground_y=1)
    images = []
    for name, (eye, target) in VIEWS.items():
        image = scene.render(eye=eye, target=target, width=width, height=height, fov=fov,
                             path=os.path.join(out_dir, "guild_%s.png" % name))
        images.append(image)
        print("描きました:", name)
    sheet = Image.new("RGB", (width * 3, height * 2))
    for i, image in enumerate(images):
        sheet.paste(image, ((i % 3) * width, (i // 3) * height))
    sheet.save(os.path.join(out_dir, "guild_sheet.png"))
    # 切って真上から見る絵（上が北、左が西＝正面）：1階は天井の梁の下、2階は屋根裏の床の下、屋根裏は y 18 の下
    for name, cut in (("cut_1f", 8), ("cut_2f", YA), ("cut_attic", 18)):
        Scene(cut_world(cut), ground_y=1).render(eye=(20, 64, 21.5), target=(20, 0, 21), width=width, height=height + 140,
                                                 fov=48, path=os.path.join(out_dir, "guild_%s.png" % name))
        print("描きました:", name)
    for name, (eye, target) in INSIDE.items():
        scene.render(eye=eye, target=target, width=width, height=height, fov=80,
                     path=os.path.join(out_dir, "guild_in_%s.png" % name))
        print("描きました:", "in_" + name)


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
    render_dir = None
    if "--render" in args:
        i = args.index("--render")
        render_dir = args[i + 1]
        del args[i:i + 2]
    out = args[0] if args else os.path.join(repo, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
    path = os.path.join(out, "guild_hall.nbt")
    n = w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "大きさ", (W, H, D))
    if render_dir:
        render_views(render_dir)
