"""コネリアの冒険者ギルド本館（見本）。

木組みと漆喰の壁、レンガの急な屋根、尖った飾りの小塔、真ん中の高い尖塔の、物語の街の大きな館。
南（+z）が正面。
  - 西棟：正面に大きな切妻を向け、角に張り出した小塔。
  - 本館：棟は東西。正面の右に、2つ目の切妻（壁から立ち上がる破風）と、しま模様の日よけ。
  - 塔：正面の真ん中。1階は入口のアーチ、屋根の上に鐘の部屋と尖塔。
1階は石の壁にアーチの窓、前に木の柱。2階は1つ外へ張り出し（持ち送りの上）、木組みと漆喰、花箱のついた窓。

使い方: python3 tools/mapgen/build_guild.py [出力のフォルダ] [--render 絵のフォルダ]
  - <出力>/guild_hall.nbt（構造物ファイル。出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - <絵のフォルダ>/guild_*.png（見本の絵。6つの向きと、まとめた1枚）
ゲームの中では /place template amemaru:map/conelia/guild_hall で置ける（48×48×40。置いた範囲の中は片づく）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Pyramid, Roof, finial, gable_wall
from mapgen.facade import compose, origin, stamp, wall_segments, window
from mapgen.world import AIR, World, block, fence, lantern, leaves, log, pane, slab, stairs, trapdoor, wall

W, H, D = 48, 48, 40
BAND = 6       # 1階と2階の間の梁
PLATE = 12     # 2階の上の梁
EAVE = 13      # 軒の高さ（屋根の y0）

pal = Palette(seed=11, post="spruce", frame="spruce", trim="dark_oak", plaster="calcite")
w = World(W, H, D)

# ---------------------------------------------------------------- 配置（座標）
# 西棟：2階の木組みの面 x=3..17・z=8..31。2階の漆喰の面は1つ内、1階の石の面は2つ内。
# 本館：2階の木組みの面 x=..44・z=15..29。
# 塔：土台 7x7（x=21..27・z=24..30、1〜2階）、その上に 5x5 の塔（x=22..26・z=25..29）。
# 正面の切妻：x=32..40。小塔：5x5（x=1..5・z=29..33）。
TOWER_BASE = (21, 0, 24, 27, PLATE, 30)
TOWER_SHAFT = (22, PLATE + 1, 25, 26, H - 1, 29)
TURRET = (1, 4, 29, 5, 19, 33)


def in_box(x, y, z, box):
    x0, y0, z0, x1, y1, z1 = box
    return x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1


# ---------------------------------------------------------------- 立面の部品
def gf(kind, n, lamp=False):
    """1階（上から y6〜y1 の6行）。層 0 は石の面、+1 は柱の面、-1・-2 は奥。"""
    top = "(L)" if lamp else "(.)"
    if kind == "post":
        return {1: ["="] + ["|"] * 5, 0: ["K"] + ["S"] * 5, -1: [" "] + ["S"] * 5}
    if kind == "stone" or n == 1:
        return {1: ["=" * n] + [" " * n] * 5, 0: ["K" * n] + ["S" * n] * 5}
    if n == 2:
        return {1: ["=="] + ["  "] * 5, 0: ["KK", "SS", "SS", "..", "..", "SS"], -1: ["  ", "  ", "  ", "gg", "gg", "AA"]}
    if kind == "arch":
        return {1: ["===", "[ ]"] + ["   "] * 4, 0: ["KKK", "SSS", top, "...", "...", "..."],
                -1: ["   ", "   ", "sss", "ggg", "ggg", "AAA"]}
    if kind == "door":
        return {1: ["===", "[ ]"] + ["   "] * 4, 0: ["KKK", "SSS", top, "...", "...", "..."],
                -1: ["   ", "   ", "sss", "sgs", "ses", "sds"]}
    if kind == "counter":
        return {1: ["===", "[ ]"] + ["   "] * 4, 0: ["KKK", "SSS", top, "...", "...", "..."],
                -1: ["   ", "   ", "sss", "...", "...", "kkk"],
                -2: ["   ", "   ", "   ", "BBB", "   ", "bbb"]}
    raise ValueError(kind)


def f2(kind, n):
    """2階（上から y12〜y6 の7行）。層 +1 は漆喰の面、+2 は木組みの面（張り出し）、+3 は花箱の前板。"""
    if kind == "post":
        return {2: ["=", "|", "|", "|", "|", "|", "v"], 1: ["K", "P", "P", "P", "P", "P", " "]}
    if kind == "lamp_post":
        # 柱の前に腕木を出して、灯りを吊る
        return {3: [" ", "F", "L", " ", " ", " ", " "], 2: ["=", "|", "|", "|", "|", "|", "v"], 1: ["K", "P", "P", "P", "P", "P", " "]}
    if kind == "plain":
        return {2: ["=" * n] + [" " * n] * 5 + ["v" * n], 1: ["K" * n] + ["P" * n] * 5 + [" " * n]}
    if n == 1:
        return {2: ["=", " ", " ", " ", "n", " ", "v"], 1: ["K", "P", "g", "g", "P", "P", " "]}
    if n == 2:
        return {2: ["==", "[]", "  ", "  ", "nn", "  ", "vv"], 1: ["KK", "PP", "gg", "gg", "PP", "PP", "  "]}
    if n == 3:
        # 白い漆喰の面に、上の角を丸めたアーチ、真ん中に細い窓（奥にガラス）、下に花箱
        return {2: ["===", "[ ]", "   ", "   ", "   ", "afa", "vvv"],
                1: ["KKK", "PPP", "P.P", "P.P", "P.P", "PPP", "   "],
                0: ["   ", "   ", "PgP", "PgP", "PgP", "   ", "   "]}
    if n == 4:
        return {2: ["====", "[  ]", "    ", "    ", "    ", "afaf", "vvvv"],
                1: ["KKKK", "PPPP", "P..P", "P..P", "P..P", "PPPP", "    "],
                0: ["    ", "    ", "PggP", "PggP", "PggP", "    ", "    "]}
    raise ValueError(n)


def facade(face, plane, gf_range=None, gf_posts=(), gf_bay=None, f2_range=None, f2_posts=(), f2_bay=None, lamps=()):
    """1つの壁の、1階と2階。plane は1階の石の面。gf_bay・f2_bay は (lo, hi, n) からベイの部品を返す関数。
    lamps は、2階の柱のうち、灯りを吊るす柱の位置。"""
    for rng, posts, bay, part, y_top in ((gf_range, gf_posts, gf_bay, gf, BAND), (f2_range, f2_posts, f2_bay, f2, PLATE)):
        if not rng:
            continue
        a0, a1 = rng
        parts = []
        for kind, n, s, e in wall_segments(a0, a1, posts, face):
            if kind == "post":
                parts.append(part("lamp_post" if (part is f2 and s in lamps) else "post", 1))
            else:
                parts.append(bay(min(s, e), max(s, e), n) if bay else part("plain" if part is f2 else "stone", n))
        x0, z0 = origin(face, a0, a1, plane)
        stamp(w, pal, face, x0, z0, y_top, compose(parts))


def default_gf(lamp_every=2):
    count = [0]

    def bay(lo, hi, n):
        count[0] += 1
        return gf("arch", n, lamp=(count[0] % lamp_every == 0))
    return bay


def default_f2(lo, hi, n):
    return f2("win", n)


# ---------------------------------------------------------------- 地面と床
def ground():
    for x in range(W):
        for z in range(D):
            w.put(x, 0, z, block("grass_block", snowy=False))


def floors():
    for (x0, x1, z0, z1) in ((6, 14, 11, 28), (16, 41, 18, 26)):
        w.fill(x0, 0, z0, x1, 0, z1, block("spruce_planks"))
    for (x0, x1, z0, z1) in ((5, 15, 10, 29), (16, 42, 17, 27), (22, 26, 25, 29)):
        w.fill(x0, BAND, z0, x1, BAND, z1, block("spruce_planks"))
    for (x0, x1, z0, z1) in ((5, 15, 10, 29), (17, 42, 17, 27)):
        w.fill(x0, PLATE, z0, x1, PLATE, z1, block("spruce_planks"))
    # 入口の広間（塔の土台の中）
    w.fill(22, 0, 25, 26, 0, 29, block("polished_andesite"))


# ---------------------------------------------------------------- 壁
def walls():
    # 本館の正面（南）
    def mh_front_gf(lo, hi, n):
        if lo == 17:
            return gf("door", n, lamp=True)
        if lo == 33:
            return gf("counter", n)
        if lo == 29:
            return gf("arch", n, lamp=True)
        return gf("arch", n) if n == 3 else gf("stone", n)
    facade("south", 27, (16, 20), (16, 20), mh_front_gf, (17, 20), (17, 20), default_f2)
    facade("south", 27, (28, 43), (28, 32, 36, 40, 43), mh_front_gf, (28, 44), (28, 32, 36, 40, 44), default_f2,
           lamps=(32, 40))
    # 本館の東
    facade("east", 42, (16, 28), (16, 18, 22, 26, 28), default_gf(), (15, 29), (15, 18, 22, 26, 29), default_f2, lamps=(26,))
    # 本館の北（裏）
    facade("north", 17, (16, 43), (16, 20, 24, 28, 32, 36, 40, 43), default_gf(3), (17, 44), (17, 20, 24, 28, 32, 36, 40, 44),
           default_f2)
    # 西棟の正面（南）
    facade("south", 29, (4, 16), (4, 6, 10, 14, 16), default_gf(1), (3, 17), (3, 6, 10, 14, 17), default_f2, lamps=(10,))
    # 西棟の西
    def ww_west_gf(lo, hi, n):
        if lo == 17:
            return gf("door", n, lamp=True)
        return gf("arch", n, lamp=(lo == 25)) if n == 3 else gf("stone", n)
    facade("west", 5, (9, 30), (9, 12, 16, 20, 24, 28, 30), ww_west_gf, (8, 31), (8, 12, 16, 20, 24, 28, 31), default_f2,
           lamps=(12, 20))
    # 西棟の北（裏）
    def ww_back_gf(lo, hi, n):
        if lo == 11:
            return gf("door", n, lamp=True)
        return gf("arch", n) if n == 3 else gf("stone", n)
    facade("north", 10, (4, 16), (4, 6, 10, 14, 16), ww_back_gf, (3, 17), (3, 6, 10, 14, 17), default_f2)
    # 西棟の東（本館の外に出ている所：裏と、正面の張り出し）
    facade("east", 15, (9, 16), (9, 12, 16), default_gf(), (8, 15), (8, 12, 15), default_f2)
    facade("east", 15, (28, 30), (28, 30), None, (29, 31), (29, 31), default_f2)


# ---------------------------------------------------------------- 屋根
ww_roof = Roof(w, pal, 3, 17, 7, 32, EAVE, ridge="z", pitch=2)
mh_roof = Roof(w, pal, 10, 45, 15, 29, EAVE, ridge="x", pitch=2, ends=("end",))
cg_roof = Roof(w, pal, 32, 40, 21, 30, EAVE, ridge="z", pitch=2, ends=("end",))
tr_roof = Pyramid(w, pal, 3, 31, 3, 20, pitch=3)


def dormer_roof(main, side, a0, a1, c_face, y_sill, height=2):
    """屋根の斜面から突き出す小さな窓の屋根（ドーマー）。side は斜面（0：座標の小さい側）、a0..a1 は棟に沿う幅、c_face は前の壁。

    奥行きは、ドーマーの屋根の奥のはしが、元の屋根の棟を越えない長さ（越えると、反対の斜面に穴があく）。
    """
    y0 = y_sill + height + 1
    if side == 0:
        depth = main.center - c_face - 1
    else:
        depth = c_face - (main.center + (0 if main.odd else 1)) - 1
    if main.ridge == "x":
        if side == 1:
            r = Roof(w, pal, a0, a1, c_face - depth, c_face, y0, ridge="z", ends=("end",), courses=False)
        else:
            r = Roof(w, pal, a0, a1, c_face, c_face + depth, y0, ridge="z", ends=("start",), courses=False)
    else:
        if side == 1:
            r = Roof(w, pal, c_face - depth, c_face, a0, a1, y0, ridge="x", ends=("end",), courses=False)
        else:
            r = Roof(w, pal, c_face, c_face + depth, a0, a1, y0, ridge="x", ends=("start",), courses=False)
    r.dormer = (main, side, a0, a1, c_face, y_sill, height, depth)
    return r


DORMERS = (
    dormer_roof(mh_roof, 0, 29, 31, 17, 21),                 # 本館の裏
    dormer_roof(mh_roof, 0, 37, 39, 17, 21),
    dormer_roof(ww_roof, 0, 13, 15, 5, 21),                  # 西棟の西の斜面
    dormer_roof(ww_roof, 0, 21, 23, 5, 21),
)
ROOFS = (ww_roof, mh_roof, cg_roof, tr_roof) + DORMERS


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        if in_box(x, y, z, TOWER_BASE) or in_box(x, y, z, TOWER_SHAFT) or in_box(x, y, z, TURRET):
            return True
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))
    # ドーマーの壁：前は窓、横（ほお）は漆喰
    for d in DORMERS:
        main, side, a0, a1, c_face, y_sill, height, depth = d.dormer
        if main.ridge == "x":
            face = "south" if side == 1 else "north"
        else:
            face = "east" if side == 1 else "west"
        step = -1 if side == 1 else 1        # 前から奥へ
        for k in range(depth + 1):
            c = c_face + step * k
            for a in (a0, a1):
                for y in range(y_sill, d.y0):
                    x, z = (a, c) if main.ridge == "x" else (c, a)
                    if k == 0:
                        w.put(x, y, z, log(pal.post, "y", stripped=True))
                    elif w.get(x, y, z) is None or y >= main.stair_y(c):
                        # ほお（横の壁）：元の屋根の面より上は、漆喰の壁
                        w.put(x, y, z, block(pal.plaster))
        for a in range(a0 + 1, a1):
            for y in range(y_sill, d.y0):
                x, z = (a, c_face) if main.ridge == "x" else (c_face, a)
                w.put(x, y, z, block(pal.plaster))
        for a in range(a0, a1 + 1):
            x, z = (a, c_face) if main.ridge == "x" else (c_face, a)
            w.put(x, d.y0 - 1, z, log(pal.post, "x" if main.ridge == "x" else "z", stripped=True))
        gable_wall(w, pal, d, c_face)
        window(w, pal, face, c_face, a0 + 1, a1 - a0 - 1, y_sill, height, shutters=False, lintel=False)
    # 切妻の三角の壁（2階の木組みより1つ外へ張り出し、下に持ち送り）
    gable_wall(w, pal, ww_roof, 32, posts=(8, 12), beam_rows=(20,))
    gable_wall(w, pal, ww_roof, 7, posts=(8, 12), beam_rows=(20,))
    gable_wall(w, pal, mh_roof, 45, posts=(20, 24), beam_rows=(20,))
    gable_wall(w, pal, cg_roof, 30, posts=(34, 38), beam_rows=(19,))
    for x in range(3, 18):
        w.put(x, PLATE, 32, stairs(pal.frame, "north", "top"))
        w.put(x, PLATE, 7, stairs(pal.frame, "south", "top"))
    for z in range(15, 30):
        w.put(45, PLATE, z, stairs(pal.frame, "west", "top"))
    for x in range(32, 41):
        w.put(x, PLATE, 30, stairs(pal.frame, "north", "top"))


# ---------------------------------------------------------------- 塔
def shell(x0, x1, z0, z1, y0, y1, fn):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ex, ez = x in (x0, x1), z in (z0, z1)
                if not (ex or ez):
                    continue
                st = fn(x, y, z, ex and ez, "x" if ez else "z")
                if st is not None:
                    w.put(x, y, z, st)


def tower():
    rng = pal.rng

    def base(x, y, z, corner, axis):
        if y <= 5:
            r = rng.random()
            return block("stone_bricks" if r < 0.75 else ("cracked_stone_bricks" if r < 0.9 else "mossy_stone_bricks"))
        if y in (BAND, PLATE):
            return log("spruce", axis, stripped=True)
        if corner:
            return log("spruce", "y", stripped=True)
        return block(pal.plaster)
    shell(21, 27, 24, 30, 1, PLATE, base)

    def shaft(x, y, z, corner, axis):
        if corner:
            return log("spruce", "y", stripped=True)
        if y in (19, 26, 32):
            return log("spruce", axis, stripped=True)
        return block(pal.plaster)
    shell(22, 26, 25, 29, PLATE + 1, 32, shaft)
    w.fill(23, 28, 26, 25, 28, 28, block("spruce_planks"))     # 鐘の部屋の床
    w.fill(22, 33, 25, 26, 33, 29, block("spruce_planks"))     # 塔の上の床
    w.fill(23, 32, 26, 25, 32, 28, block("spruce_planks"))     # 鐘の部屋の天井（鐘を吊る）
    # 鐘の部屋：四方にアーチの開口
    for face in ("south", "north", "east", "west"):
        if face in ("south", "north"):
            z = 29 if face == "south" else 25
            x0, z0 = origin(face, 23, 25, z)
        else:
            x = 26 if face == "east" else 22
            x0, z0 = origin(face, 26, 28, x)
        stamp(w, pal, face, x0, z0, 31, {0: ["[.]", "...", "..."], -1: ["   ", "   ", "   "]})
        stamp(w, pal, face, x0, z0, 28, {1: ["VVV"]})
    w.put(24, 31, 27, block("bell", attachment="ceiling", facing="south", powered=False))
    # 軒の帯（7x7）と、角の小さな尖り
    for x in range(21, 28):
        for z in range(24, 31):
            if x in (21, 27) or z in (24, 30):
                facing = "south" if z == 24 else ("north" if z == 30 else ("east" if x == 21 else "west"))
                w.put(x, 33, z, stairs(pal.trim, facing, "top"))
    for (x, z) in ((21, 24), (27, 24), (21, 30), (27, 30)):
        finial(w, pal, x, 34, z, height=4, top="lantern", spike=False)
    spire = Pyramid(w, pal, 24, 27, 2, 34, pitch=4, thickness=3, rings=0, eave=False)
    top = spire.build()
    finial(w, pal, 24, top, 27, height=0, top="lantern", spike=True)
    # 尖塔の段の角に、小さな尖り（濃い木）
    for rho, y in ((2, 38), (1, 42)):
        for dx in (-rho, rho):
            for dz in (-rho, rho):
                w.put(24 + dx, y, 27 + dz, fence(pal.trim))

    # 正面（南）の飾り：入口・2階の窓・丸い窓
    front = {
        1: ["       ", "       ", "       ", "       ", "       ", " afafa ", "  vvv  ",
            "       ", " F   F ", " L   L ", "       ", "       "],
        0: ["|=====|", "|P[P]P|", "|P...P|", "|P...P|", "|P...P|", "|PPPPP|", "=======",
            "SSScSSS", "SS(.)SS", "SS...SS", "SS...SS", "SS...SS"],
        -1: ["       ", "       ", "   g   ", "   g   ", "   g   ", "       ", "       ",
             "       ", "  (.)  ", "  ...  ", "  ...  ", "  ...  "],
        -2: ["       ", "       ", "       ", "       ", "       ", "       ", "       ",
             "       ", "  sss  ", "  sgs  ", "  ses  ", "  sds  "],
    }
    stamp(w, pal, "south", 21, 30, PLATE, front)
    # 2階の窓は3つ分：両脇も開ける
    for x in (23, 25):
        for y in range(8, 11):
            w.put(x, y, 30, AIR)
            w.put(x, y, 29, pane())
    w.put(23, 11, 31, stairs(pal.frame, "west", "top"))
    w.put(25, 11, 31, stairs(pal.frame, "east", "top"))
    for y in range(8, 11):
        w.put(22, y, 31, trapdoor(pal.frame, "south", "bottom", True))
        w.put(26, y, 31, trapdoor(pal.frame, "south", "bottom", True))
        w.put(24, y, 30, log("dark_oak", "y"))
    w.put(24, 11, 30, block(pal.plaster))
    for y in range(8, 11):
        w.put(22, y, 29, block(pal.plaster))
        w.put(26, y, 29, block(pal.plaster))
    for x in range(22, 27):
        w.put(x, 7, 32, trapdoor(pal.frame, "south", "bottom", True))
    # 塔の正面（屋根の上）：丸い窓と、小窓の対
    stamp(w, pal, "south", 22, 29, 24, {0: [" {.} ", " ggg ", " <.> "], -1: [" ggg ", " ggg ", " ggg "]})
    window(w, pal, "south", 29, 23, 1, 16, 2, shutters=False, sill=False, lintel=False)
    window(w, pal, "south", 29, 25, 1, 16, 2, shutters=False, sill=False, lintel=False)
    # 塔の横と裏：小窓
    for face, plane, a in (("west", 22, 27), ("east", 26, 27), ("north", 25, 24)):
        window(w, pal, face, plane, a, 1, 22, 2, shutters=False, sill=False, lintel=False)


# ---------------------------------------------------------------- 小塔（西棟の角）
def turret():
    cx, cz = 3, 31
    # 持ち送り（下へすぼまる）
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            ring = max(abs(dx), abs(dz))
            x, z = cx + dx, cz + dz
            facing = "south" if dz < 0 and abs(dz) >= abs(dx) else ("north" if dz > 0 and abs(dz) >= abs(dx) else
                                                                     ("east" if dx < 0 else "west"))
            if ring == 2:
                w.put(x, 6, z, stairs(pal.frame, facing, "top"))
            else:
                w.put(x, 6, z, block("spruce_planks"))
                if ring == 1:
                    w.put(x, 5, z, stairs("stone_brick", facing, "top"))
                else:
                    w.put(x, 5, z, block("stone_bricks"))
                    w.put(x, 4, z, stairs("stone_brick", "north", "top"))

    def body(x, y, z, corner, axis):
        if corner:
            return log("spruce", "y", stripped=True)
        if y in (7, PLATE, 19):
            return log("spruce", axis, stripped=True)
        return block(pal.plaster)
    shell(1, 5, 29, 33, 7, 19, body)
    w.fill(2, 12, 30, 4, 12, 32, block("spruce_planks"))
    w.fill(2, 19, 30, 4, 19, 32, block("spruce_planks"))
    for face, plane, a0, a1 in (("west", 1, 29, 33), ("south", 33, 1, 5)):
        window(w, pal, face, plane, 31 if face == "west" else 3, 1, 8, 3)
        window(w, pal, face, plane, 31 if face == "west" else 3, 1, 15, 3)
    for face, plane in (("north", 29), ("east", 5)):
        window(w, pal, face, plane, 31 if face == "east" else 3, 1, 15, 2, shutters=False)
    top = tr_roof.top_y + 1
    finial(w, pal, cx, top, cz, height=4, top="lantern", spike=True)


# ---------------------------------------------------------------- 飾り：切妻の窓・尖り・日よけ・煙突
def gable_details():
    # 西棟の正面（z=32）：真ん中に十字の大窓、両脇に窓、梁の上に小窓
    window(w, pal, "south", 32, 9, 3, 15, 4, shutters=False, mullion=1, transom=17)
    window(w, pal, "south", 32, 5, 2, 14, 2)
    window(w, pal, "south", 32, 14, 2, 14, 2)
    window(w, pal, "south", 32, 10, 1, 22, 2, shutters=False)
    # 西棟の裏（z=7）
    window(w, pal, "north", 7, 9, 3, 15, 4, shutters=False, mullion=1, transom=17)
    window(w, pal, "north", 7, 10, 1, 22, 2, shutters=False)
    # 本館の東（x=45）
    window(w, pal, "east", 45, 21, 3, 15, 4, shutters=False, mullion=1, transom=17)
    window(w, pal, "east", 45, 17, 2, 14, 2)
    window(w, pal, "east", 45, 26, 2, 14, 2)
    window(w, pal, "east", 45, 22, 1, 22, 2, shutters=False)
    # 正面の切妻（z=30）：十字の大窓
    window(w, pal, "south", 30, 35, 3, 14, 5, shutters=False, mullion=1, transom=17)
    # 頂の尖りと、軒の先の小さな尖り
    for r, which in ((ww_roof, "end"), (ww_roof, "start"), (mh_roof, "end"), (cg_roof, "end")):
        x, y, z = r.apex(which)
        finial(w, pal, x, y, z, height=5 if r is not cg_roof else 4, top="lantern", spike=True)
    for d in DORMERS:
        x, y, z = d.apex(d.ends[0])
        finial(w, pal, x, y, z, height=2, top="lantern", spike=False)
    for side in (0, 1):
        x, y, z = ww_roof.eave_corner("end", side)
        if not in_box(x, y, z, (0, 0, 27, 7, 30, 35)):
            finial(w, pal, x, y + 1, z, height=2, top="lantern", spike=False)
    # 日よけ（しま模様）：正面の切妻の下の、1階の上
    for x in range(32, 41):
        mat = "brick" if x % 2 == 0 else "smooth_quartz"
        w.put(x, 5, 29, stairs(mat, "north"))
        w.put(x, 4, 30, stairs(mat, "north"))
        w.put(x, 3, 31, slab(mat, "top"))
    for x in (32, 40):
        for y in (1, 2):
            w.put(x, y, 31, fence(pal.trim))
    # 煙突
    for (cx, cz, h) in ((36, 18, 27), (6, 13, 27)):
        for y in range(PLATE, h + 1):
            for dx in (0, 1):
                w.put(cx + dx, y, cz, block("bricks" if y < h else "stone_bricks"))
        w.put(cx, h + 1, cz, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
        w.put(cx + 1, h + 1, cz, slab("stone_brick", "bottom"))


# ---------------------------------------------------------------- 外の階段・中の通り道
def stairway():
    """本館の東の壁に沿う、木の外階段。南から北へ上がり、2階の戸口の前の踊り場へ。"""
    # 階段：y=1（z=29）から y=6（z=24）
    for k in range(6):
        y, z = 1 + k, 29 - k
        for x in (45, 46):
            w.put(x, y, z, stairs(pal.frame, "north"))
            if y > 1:
                w.put(x, y - 1, z, stairs(pal.frame, "south", "top"))
        w.put(47, y + 1, z, fence(pal.trim))
    # 踊り場：y=6（z=19..23）、柱で支える
    for z in range(19, 24):
        for x in (45, 46):
            w.put(x, 6, z, block("spruce_planks"))
        w.put(47, 7, z, fence(pal.trim))
    for z in (19, 23):
        for y in range(1, 6):
            w.put(47, y, z, log(pal.post, "y", stripped=True))
        w.put(47, 6, z, log(pal.post, "y", stripped=True))
        w.put(47, 7, z, fence(pal.trim))
        w.put(47, 8, z, fence(pal.trim))
        w.put(47, 9, z, fence(pal.trim))
    for z in range(19, 24):
        w.put(46, 5, z, stairs(pal.frame, "east", "top"))
    w.put(47, 7, 18, fence(pal.trim))
    # 踊り場の上の小さな屋根（東へ下がる）
    for z in range(18, 25):
        w.put(45, 11, z, block("bricks"))
        w.put(45, 12, z, stairs("brick", "west"))
        w.put(46, 11, z, stairs("brick", "west"))
        w.put(47, 10, z, stairs("brick", "west"))
        w.put(47, 9, z, stairs(pal.trim, "west", "top")) if z in (18, 24) else None
    w.put(47, 10, 18, stairs(pal.trim, "north", "top"))
    w.put(47, 10, 24, stairs(pal.trim, "south", "top"))
    # 2階の戸口（x=43 の漆喰の面、z=20）
    for y in (7, 8):
        w.put(44, y, 20, AIR)
        w.put(44, y, 21, AIR)
    w.put(43, 7, 20, block("spruce_door", facing="east", half="lower", hinge="left", open=False, powered=False))
    w.put(43, 8, 20, block("spruce_door", facing="east", half="upper", hinge="left", open=False, powered=False))
    w.put(44, 9, 21, AIR)
    w.put(46, 10, 21, lantern(True))
    # 塔の土台の奥：広間から本館へのアーチ
    for x in range(23, 26):
        for y in range(1, 4):
            w.put(x, y, 24, AIR)
    w.put(23, 4, 24, stairs("stone_brick", "west", "top"))
    w.put(25, 4, 24, stairs("stone_brick", "east", "top"))
    w.put(24, 4, 24, AIR)


# ---------------------------------------------------------------- 前庭
def yard():
    rng = pal.rng
    for z in range(31, D):
        for x in range(22, 27):
            r = rng.random()
            w.put(x, 0, z, block("stone_bricks" if r < 0.5 else ("polished_andesite" if r < 0.8 else "cobblestone")))
    for x in range(1, 46):
        for z in (34, 35):
            if w.name_at(x, 0, z) == "grass_block" and rng.random() < 0.9:
                w.put(x, 0, z, block("dirt_path"))
    for x in (19, 29):
        w.put(x, 1, 33, wall("stone_brick"))
        w.put(x, 2, 33, fence(pal.trim))
        w.put(x, 3, 33, fence(pal.trim))
        w.put(x, 4, 33, lantern(False))
    flowers = ["poppy", "allium", "cornflower", "azure_bluet", "oxeye_daisy", "pink_tulip"]
    for x in list(range(6, 18)) + list(range(29, 44)):
        if w.get(x, 1, 32) is None and w.get(x, 1, 33) is None and rng.random() < 0.55:
            w.put(x, 1, 32, block(rng.choice(flowers)))


# ---------------------------------------------------------------- 組み立て
ground()
floors()
walls()
roofs()
tower()
turret()
gable_details()
stairway()
yard()
w.finalize()

# 見本の絵を描く向き：目の位置と見る所（構造物の中の座標。y=0 が地面の草）。CI の写真（CiScreenshots.java）と同じ
VIEWS = {
    "front": ((24, 21, 68), (24, 24, 20)),
    "left": ((-8, 21, 54), (20, 19, 20)),
    "right": ((58, 21, 54), (27, 18, 20)),
    "close": ((12, 8, 46), (20, 9, 28)),
    "aerial": ((58, 44, 64), (24, 14, 20)),
    "back": ((52, 24, -18), (24, 18, 16)),
}


def render_views(out_dir, width=960, height=540, fov=70):
    """6つの向きの見本の絵と、1枚にまとめた絵を描く。"""
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
