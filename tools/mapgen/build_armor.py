"""コネリアの防具工房（PHASE 3。建築の文書「PHASE 3：防具工房」の平面図どおり）。

結晶騎士団の甲冑工房だった建物を、ベルタ・オークスが受け継いだ店と工房（Phase 0-A）。
正面は北（工房通り）で、三角の壁を通りに向ける（妻入り）。西は環状路に向く角の建物なので、西の屋根に小さな妻を出す（都市のルール⑥）。
1階は石（騎士団の時代の灰色の石）、2階は木組みと漆喰で、正面へ1つ張り出す（持ち送りで受ける）。屋根は南北に棟が通る
1：1の黒い瓦（深層岩タイル）。中は、前が店（防具立ての列・飾り窓・試着と採寸の場所・盾の棚・型紙の壁・カウンター）、
後ろが工房（金床・小さな炉・強化の作業台・素材の棚）。炉の煙突は裏の妻の真ん中（深層岩レンガ）。
西の壁の外は、低い石の塀で囲んだ半屋外の作業場（壁ぎわの半分に片流れの屋根）で、防具職人のプレイヤーも使う。
外寸 15×17、軒（屋根裏の床）+11、棟 +18（道のブロックからの段数）。

座標：構造物の中で x が東、y が上、z が南。文書の平面図の (u, v) とは X = u + 4、Z = v + 3 の関係
（u は西→東、v は正面＝北→裏＝南）。世界の座標では、構造物の (0, 0, 0) が (x 60, 道の高さ, z 4)。
建物は x 64〜78、z 7〜23。y=0 が地面（道・石畳・作業場）。1階の床のブロック y=1（+1）、2階 y=6（+6）、屋根裏 y=11（+11）。

使い方: python3 tools/mapgen/build_armor.py [出力のフォルダ] [--render 絵のフォルダ]
  - <出力>/armor_workshop.nbt（構造物ファイル。出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - <絵のフォルダ>/armor_*.png（見本の絵）
ゲームの中では /place template amemaru:map/conelia/armor_workshop で置ける（置いた範囲の中は片づく）。
型紙の壁は、章が進むと額縁に型紙が1枚ずつ増える（PATTERNS と patterns_world を参照。Phase 5 で額縁の中身を入れ替える）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.facade import Orient, cell, origin, window
from mapgen.fixtures import candles, hang_lantern, lamp_post, wall_lantern
from mapgen.world import (World, banner_nbt, block, door, fence, lantern, leaves, log, sign_nbt, slab, stairs,
                          trapdoor, wall)

W, H, D = 22, 24, 23          # u -4..17、y 0..23、v -3..19
Y1, Y2, YA = 1, 6, 11          # 床のブロック（1階・2階・屋根裏）
ROOF_Y0 = 10                   # 屋根の軒先（壁から1つ外）の高さ。壁の上の線は +11

pal = Palette(seed=53, roof="deepslate_tile", roof_block="deepslate_tiles", trim="dark_oak", post="spruce",
              beam="spruce", plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)


def XZ(u, v):
    """平面図の (u, v) → 構造物の (X, Z)"""
    return u + 4, v + 3


def put(u, v, y, state, nbt=None):
    x, z = XZ(u, v)
    w.put(x, y, z, state, nbt)


def get(u, v, y):
    x, z = XZ(u, v)
    return w.get(x, y, z)


def fill(u0, u1, v0, v1, y0, y1, state):
    for u in range(min(u0, u1), max(u0, u1) + 1):
        for v in range(min(v0, v1), max(v0, v1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                put(u, v, y, state() if callable(state) else state)


PLAN_DIR = {"street": "north", "back": "south", "west": "west", "east": "east"}   # 平面図の向き → 世界の向き
OUT = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}       # 世界の向き → (du, dv)


def wall_lamp(u, v, y, out):
    """壁（平面図の (u, v)）から出す腕木の灯り（都市のルール⑦の壁のランタン）。out は世界の向き。"""
    x, z = XZ(u, v)
    wall_lantern(w, x, y, z, out)


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


def shield(base, *patterns):
    """模様入りの盾（額縁・防具立てに渡す形）。patterns は旗と同じ記号と色の組。"""
    return ("shield", {"base": base, "patterns": list(patterns)})


def armor_set(mat, color=None, **swap):
    """防具の一揃い（head・chest・legs・feet）。color は革の色。swap で1つだけ替える（例：head="iron_helmet"）。"""
    def it(piece):
        name = "%s_%s" % (mat, piece)
        return (name, {"color": color}) if (color is not None and mat == "leather") else name
    out = {"head": it("helmet"), "chest": it("chestplate"), "legs": it("leggings"), "feet": it("boots")}
    out.update(swap)
    return out


# 店の色（都市のルール⑦：灰と赤）。旗と看板の盾は「灰の地に赤い十字と赤い縁」
SHOP_BANNER = [("cs", "red"), ("ms", "red")]
SHOP_SHIELD = shield("gray", ("cs", "red"), ("ms", "red"), ("bo", "red"))
# 騎士団の旗（青と白。ギルドの旗のもとになった色）【補完】
KNIGHT_BANNER = [("bo", "white"), ("mc", "white"), ("ts", "white")]


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


def apron_paving():
    """店の前の石畳（工房通りの端と同じ石）"""
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
    """半屋外の作業場の地面：踏み固めた土と石"""
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


IRON_BARS = block("iron_bars", north=False, south=False, east=False, west=False, waterlogged=False)


# ---------------------------------------------------------------- 窓（平面図の座標で）
def win(face, line, a0, width, y0, height, **kw):
    """face の壁（北・南は v=line、西・東は u=line）に、a0 から幅 width の窓。a0 は北南の壁では u の小さい方、東西の壁では v の小さい方。"""
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        window(w, pal, face, plane, x0, width, y0, height, **kw)
    else:
        plane, zmin = XZ(line, a0)
        window(w, pal, face, plane, zmin, width, y0, height, **kw)


def plain_window(face, line, a0, width, y0, height):
    win(face, line, a0, width, y0, height, shutters=False, sill=False, lintel=False, glass_depth=0)


def arched_window(face, line, a0, width=3, y0=Y1 + 1, height=3, sill=True):
    """石の壁のアーチの窓：壁の面にガラス、上の両角を逆さの階段で丸める、外に石の窓台。"""
    plain_window(face, line, a0, width, y0, height)
    O = Orient(face)
    yt = y0 + height - 1
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        ox, oz = origin(face, x0, x0 + width - 1, plane)
    else:
        plane, zmin = XZ(line, a0)
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


def house_window(face, u, v, width=1, box=True):
    """2階の窓：高さ2（+8・+9）、雨戸なし。通りに向く窓（北・西）の下に花箱"""
    if face in ("north", "south"):
        plain_window(face, v, u, width, Y2 + 2, 2)
        cells = [(u + i, v) for i in range(width)]
    else:
        plain_window(face, u, v, width, Y2 + 2, 2)
        cells = [(u, v + i) for i in range(width)]
    if box:
        for (cu, cv) in cells:
            flower_box(face, cu, cv)


# ---------------------------------------------------------------- 地面
def ground():
    for u in range(-4, 18):
        for v in range(-3, 20):
            if v <= -1:
                put(u, v, 0, apron_paving())                 # 前の石畳（v -3 は工房通りの南の端の帯）
            elif v >= 17 or u >= 15:
                put(u, v, 0, alley())                        # 東の路地と裏通り
            elif u <= -1:
                put(u, v, 0, yard_ground() if v <= 15 else alley())   # 半屋外の作業場（v 16 は塀）
            else:
                put(u, v, 0, block("cobblestone"))           # 建物の下


# ---------------------------------------------------------------- 床
def floors():
    for u in range(1, 14):
        # 1階（+1）：店はトウヒの板、工房は石（炉と金床のまわり）
        for v in range(1, 16):
            if v <= 8:
                put(u, v, Y1, block("spruce_planks"))
            else:
                put(u, v, Y1, block("polished_andesite") if (u + v) % 2 else block("stone_bricks"))
        # 2階（+6）：トウヒの板。v 4・8・12 は1階の天井に見える梁（東西）。階段の口（u 1..2、v 12..14）はあける
        for v in range(0, 16):
            if u <= 2 and 12 <= v <= 14:
                continue
            put(u, v, Y2, beam("x") if v in (4, 8, 12) else block("spruce_planks"))
        # 屋根裏（+11）
        for v in range(-1, 16):
            put(u, v, YA, block("spruce_planks"))
    # 試着と採寸の場所：磨いた安山岩の3×3と、真ん中の赤い敷物（立つ所）
    fill(10, 12, 4, 6, Y1, Y1, block("polished_andesite"))


# ---------------------------------------------------------------- 外壁（1階は石）
def plinth_or(stone, y):
    return block("polished_andesite") if y == Y1 else stone


def stone_walls():
    for y in range(Y1, Y2):
        for u in range(0, 15):
            put(u, 0, y, plinth_or(gray_stone(), y))
            put(u, 16, y, plinth_or(gray_stone(), y))
        for v in range(0, 17):
            put(0, v, y, plinth_or(gray_stone(), y))
            put(14, v, y, plinth_or(gray_stone(), y))
    # 角の石（明るい安山岩と石レンガを交互に）
    for (u, v) in ((0, 0), (14, 0), (0, 16), (14, 16)):
        for y in range(Y1, Y2):
            put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))


# ---------------------------------------------------------------- 2階の木組み（正面は1つ張り出す）
NORTH_POSTS = (0, 5, 9, 14)            # 正面の柱（1階の石の柱の真上）
SIDE_POSTS = (-1, 3, 7, 11, 16)        # 西と東の壁の柱


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
    run("north", -1, 0, 14, NORTH_POSTS)
    run("south", 16, 0, 14, (0, 3, 11, 14))
    run("west", 0, -1, 16, SIDE_POSTS)
    run("east", 14, -1, 16, SIDE_POSTS)
    # 張り出しの下の持ち送り（トウヒの逆さの階段。1階の石の柱の上）
    for u in NORTH_POSTS:
        put(u, -1, Y2 - 1, stairs(pal.frame, "south", "top"))
    # 2階の窓（雨戸なし）。通り（北）と環状路（西）に向く窓の下に花箱
    house_window("north", 2, -1, width=2)
    house_window("north", 7, -1)
    house_window("north", 11, -1, width=2)
    for v in (1, 5, 13):
        house_window("west", 0, v)
    for v in (5, 13):
        house_window("east", 14, v, box=False)
    for u in (2, 12):
        house_window("south", u, 16, box=False)


# ---------------------------------------------------------------- 正面：飾り窓・入口・盾・看板・旗
def front():
    # 飾り窓（幅3・高さ3、上の角を丸める）。西は騎士団の甲冑、東は鎖の鎧
    arched_window("north", 0, 2, 3, Y1 + 1, 3)
    arched_window("north", 0, 10, 3, Y1 + 1, 3)
    # 入口（幅3）：真ん中に扉、両脇に鉄格子の明かり窓、上に鉄格子の欄間と、アーチの肩、要石（騎士団の紋の石）
    put(7, 0, Y1 + 1, door(pal.frame, "north", False, hinge="left"))
    put(7, 0, Y1 + 2, door(pal.frame, "north", True, hinge="left"))
    for u in (6, 8):
        for y in (Y1 + 1, Y1 + 2):
            put(u, 0, y, IRON_BARS)
    put(7, 0, Y1 + 3, IRON_BARS)
    put(8, 0, Y1 + 3, stairs("stone_brick", "east", "top"))
    put(6, 0, Y1 + 3, stairs("stone_brick", "west", "top"))
    put(7, 0, Y1 + 4, block("chiseled_stone_bricks"))
    # 入口の踏み段（道 0 → 床 +1）
    for u in (6, 7, 8):
        put(u, -1, Y1, stairs("stone_brick", "south"))
    # 正面の壁に、模様入りの盾（店の品の見本。都市のルール⑦）：角の柱（目の高さ）と、飾り窓の上
    put_shields = [(1, Y1 + 2, SHOP_SHIELD), (13, Y1 + 2, shield("red", ("mr", "gray"), ("mc", "red"))),
                   (3, Y2 - 1, shield("red", ("bt", "gray"), ("bo", "gray"))),
                   (11, Y2 - 1, shield("gray", ("rud", "red"), ("bo", "red")))]
    for (u, y, sh) in put_shields:
        frame(u, -1, y, "north", sh)
    # 入口の両脇（端の帯）に、鎧を着せた防具立て（門番の甲冑。店の看板の代わり）【提案】
    for u in (5, 9):
        stand(u, -1, Y1, "north", **armor_set("iron", legs="chainmail_leggings"))
    # 吊り看板（北西の角の柱から、通りの方へ。道から4）。下の盾（u=1）が看板の下に見せる品
    put(1, -1, 4, block("spruce_wall_hanging_sign", facing="east", waterlogged=False),
        sign_nbt(["防具工房", "オークス", "", ""], ["防具工房", "オークス", "", ""], hanging=True))
    # 張り出しの下から鎖で吊る灯り（入口の両脇）
    hang(6, -1, Y2 - 1)
    hang(8, -1, Y2 - 1)
    # 旗（灰に赤い十字と赤い縁）：2階の両端の漆喰に
    for u in (1, 13):
        put(u, -2, 9, block("gray_wall_banner", facing="north"), banner_nbt(SHOP_BANNER))


# ---------------------------------------------------------------- 西・東・裏の開口
def openings():
    # 西（作業場へ）：戸と、階段の窓
    put(0, 10, Y1 + 1, door(pal.frame, "west", False))
    put(0, 10, Y1 + 2, door(pal.frame, "west", True))
    put(0, 10, Y1 + 3, stairs("stone_brick", "west", "top"))
    plain_window("west", 0, 14, 1, Y1 + 2, 2)
    # 裏（南、裏通りへ）：裏口（素材の搬入）と、階段の下の窓
    put(12, 16, Y1 + 1, door(pal.frame, "south", False))
    put(12, 16, Y1 + 2, door(pal.frame, "south", True))
    put(12, 16, Y1 + 3, stairs("stone_brick", "south", "top"))
    put(12, 17, Y1, stairs("stone_brick", "north"))
    plain_window("south", 16, 3, 1, Y1 + 2, 2)
    # 東（路地）と裏の壁のランタン
    wall_lamp(14, 8, 5, "east")
    wall_lamp(13, 16, 5, "south")


# ---------------------------------------------------------------- 屋根
main_roof = Roof(w, pal, XZ(0, 0)[0], XZ(14, 0)[0], XZ(0, -1)[1], XZ(0, 16)[1], ROOF_Y0, ridge="z", pitch=1)
# 西の小さな妻（環状路に向ける。都市のルール⑥の角の建物）：幅5、2：1、棟 +17
cross_roof = Roof(w, pal, XZ(0, 0)[0], XZ(7, 0)[0], XZ(0, 1)[1], XZ(0, 5)[1], ROOF_Y0, ridge="x", pitch=2,
                  ends=("start",))
ROOFS = [main_roof, cross_roof]


def chimney_zone(u, v, y):
    """炉の煙突（工房の奥の東寄り。u 9..10、v 14..15）：裏の壁の内側を上がり、屋根の東の面から出る"""
    return 9 <= u <= 10 and 14 <= v <= 15 and y >= 11


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        u, v = x - 4, z - 3
        if chimney_zone(u, v, y):
            return True
        if 0 <= u <= 14 and -1 <= v <= 16 and y < YA:
            return True       # 建物の中（屋根裏の床より下）には、屋根の部品を置かない
        if 1 <= u <= 13 and 0 <= v <= 15 and y == YA:
            return True       # 屋根裏の床は残す（2階の天井に瓦の裏が見えないように）
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))


def gables():
    # 正面の妻（v=-1。張り出しの面）：木組み。+14 に梁。梁の下は柱を2つおき（u 3・5・9・11）に立て、真ん中に幅1×高さ2の窓
    # （屋根裏の明かり）。梁の上は真ん中に束（u 7）を立てる
    gable_wall(w, pal, main_roof, XZ(0, -1)[1], posts=tuple(XZ(u, 0)[0] for u in (3, 5, 9, 11)), beam_rows=(14,))
    plain_window("north", -1, 7, 1, 12, 2)
    put(7, -2, 11, slab(pal.frame, "top"))
    for y in (15, 16, 17):
        put(7, -1, y, SPRUCE_LOG_Y)
    # 裏の妻（v=16）：木組み。真ん中に屋根裏の荷の口（扉の形の窓）と、その上の荷揚げの梁（鎖と鉤）
    gable_wall(w, pal, main_roof, XZ(0, 16)[1], posts=(XZ(3, 0)[0], XZ(11, 0)[0]), beam_rows=(14,))
    for y in (12, 13):
        put(7, 16, y, block("spruce_trapdoor", facing="south", half="bottom", open=True, powered=False,
                            waterlogged=False))
    put(6, 16, 12, SPRUCE_LOG_Y)
    put(6, 16, 13, SPRUCE_LOG_Y)
    put(8, 16, 12, SPRUCE_LOG_Y)
    put(8, 16, 13, SPRUCE_LOG_Y)
    put(7, 16, 15, beam("z"))
    put(7, 17, 15, beam("z"))
    put(7, 18, 15, beam("z"))
    put(7, 18, 14, block("chain", axis="y", waterlogged=False))
    put(7, 18, 13, block("chain", axis="y", waterlogged=False))
    # 西の小さな妻（u=0）：木組み。柱は v 2・4、真ん中に幅1×高さ2の窓
    gable_wall(w, pal, cross_roof, XZ(0, 0)[0], posts=(XZ(0, 2)[1], XZ(0, 4)[1]))
    plain_window("west", 0, 3, 1, 12, 2)
    put(-1, 3, 11, slab(pal.frame, "top"))


# ---------------------------------------------------------------- 炉の煙突
def chimney():
    # 炉の煙道（u 9..10、v=15）：炉の覆いの上から、2階（寝室の暖かい壁）と屋根裏を通る。深層岩レンガ
    for u in (9, 10):
        for y in range(Y1 + 4, 12):
            put(u, 15, y, block("deepslate_bricks"))
    # 屋根の上の煙突（u 9..10、v 14..15、2×2）：屋根裏から棟の上 +19 まで。上に焚き火（干し草なし：小さな炉の低い煙）
    for u in (9, 10):
        for v in (14, 15):
            for y in range(12, 20):
                put(u, v, y, block("deepslate_bricks") if rng.random() < 0.85 else block("cracked_deepslate_bricks"))
    for (u, v) in ((9, 14), (10, 14), (10, 15)):
        put(u, v, 20, slab("deepslate_brick", "bottom"))
    put(9, 15, 20, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
    # 根もとの水切り（屋根の面に沿う石の階段）
    for u in (9, 10):
        put(u, 13, 17 if u == 9 else 16, stairs("deepslate_brick", "south"))


# ---------------------------------------------------------------- 1階の中：店
def shop():
    # 床の軸（入口からカウンターのベルタまで）：灰・赤・灰の敷物
    for v in range(1, 8):
        put(6, v, Y1 + 1, block("gray_carpet"))
        put(7, v, Y1 + 1, block("red_carpet"))
        put(8, v, Y1 + 1, block("gray_carpet"))
    # 西の飾り窓：騎士団の時代の甲冑（ベルタが最後に打った騎士団の鎧）【補完】。東の飾り窓：鎖の鎧
    stand(3, 1, Y1 + 1, "north", hand="iron_sword", **armor_set("iron"))
    stand(11, 1, Y1 + 1, "north", **armor_set("chainmail"))
    # 防具立ての列（西の壁ぎわ。通路の方を向く）：革・鉄・ダイヤ
    stand(1, 3, Y1 + 1, "east", **armor_set("leather"))
    stand(1, 5, Y1 + 1, "east", **armor_set("iron", feet="chainmail_boots"))
    stand(1, 7, Y1 + 1, "east", **armor_set("diamond"))
    # 防具立ての間の壁：兜の額縁と、騎士団の古い旗（青と白）
    frame(1, 4, Y1 + 3, "east", "iron_helmet")
    frame(1, 6, Y1 + 3, "east", "chainmail_helmet")
    put(1, 5, Y1 + 4, block("blue_wall_banner", facing="east"), banner_nbt(KNIGHT_BANNER))
    # 盾の棚（東の壁、飾り窓のそば）
    racks = [shield("red", ("bo", "gray")), shield("gray", ("bt", "red")),
             shield("white", ("hh", "red"), ("bo", "gray")), "shield"]
    for i, (v, y) in enumerate(((1, Y1 + 3), (2, Y1 + 3), (1, Y1 + 2), (2, Y1 + 2))):
        frame(13, v, y, "west", racks[i])
    # 試着と採寸の場所（東）：赤い敷物の上に立つ。腰かけ（靴を履き替える）、合わせ中の品を着せた防具立て
    put(11, 5, Y1 + 1, block("red_carpet"))
    put(13, 6, Y1 + 1, stairs("spruce", "east"))
    put(13, 7, Y1 + 1, stairs("spruce", "east"))
    stand(12, 3, Y1 + 1, "west", chest=("leather_chestplate", {"color": 0x6E6E6E}), head="chainmail_helmet")
    # 型紙の壁（東の壁）：章ごとに1枚ずつ増える。初めは第1章の1枚だけ（PATTERNS）
    for i, v in enumerate(PATTERN_SLOTS):
        item, nm = PATTERNS[i] if i < PATTERNS_SHOWN else (None, None)
        frame(13, v, Y1 + 3, "west", item, name=nm)
    put(13, 4, Y1 + 4, block("spruce_wall_sign", facing="west", waterlogged=False),
        sign_nbt(["型紙", "", "", ""], ["型紙", "", "", ""]))
    # カウンター（v=8）：西は棚（樽2段）、u=4 は客の側から使う作業台（MOD の作業台の代わりの鍛冶台）、
    # u 5..9 はカウンター（皮をはいだトウヒの丸太に、濃い木の天板。1.5 の高さ）、u=10 は柵の門、東は棚
    for u in (1, 2, 3, 11, 12, 13):
        put(u, 8, Y1 + 1, block("barrel", facing="north", open=False))
        put(u, 8, Y1 + 2, block("barrel", facing="up", open=False))
    put(4, 8, Y1 + 1, block("smithing_table"))
    for u in range(5, 10):
        put(u, 8, Y1 + 1, log("spruce", "x", stripped=True))
        put(u, 8, Y1 + 2, slab(pal.trim, "bottom"))
    put(10, 8, Y1 + 1, block("spruce_fence_gate", facing="north", open=False, powered=False, in_wall=False))
    x, z = XZ(5, 8)
    candles(w, x, Y1 + 3, z, n=2, lit=True)
    frame(8, 8, Y1 + 3, "up", "iron_helmet")
    # 灯り（2階の床の梁から鎖で吊る）
    for (u, v) in ((3, 4), (11, 4), (7, 5)):
        hang(u, v, Y2 - 1)


# 型紙の壁：額縁の場所（東の壁、v 3..6）と、章ごとの型紙（第1章から順に）
PATTERN_SLOTS = (3, 4, 5, 6)
PATTERNS = [("sentry_armor_trim_smithing_template", "第1章の型紙"),
            ("dune_armor_trim_smithing_template", "第2章の型紙"),
            ("coast_armor_trim_smithing_template", "第3章の型紙"),
            ("wild_armor_trim_smithing_template", "第4章の型紙")]
PATTERNS_SHOWN = 1


# ---------------------------------------------------------------- 1階の中：工房
def workshop():
    # 2階への階段（u 1..2。v 15 から北へ上る：+2..+6。いちばん上の段 v 11 は2階の床の高さ）
    for (v, y) in ((15, 2), (14, 3), (13, 4), (12, 5), (11, 6)):
        for u in (1, 2):
            put(u, v, y, stairs("spruce", "north"))
            for yy in range(Y1 + 1, y):
                put(u, v, yy, block("spruce_planks"))
    # 階段の下の物入れ（革と布）
    put(1, 15, Y1, block("spruce_planks"))
    # ベルタの金床（入口からの軸の上。カウンターの奥）
    put(7, 11, Y1 + 1, block("anvil", facing="east"))
    # 奥（裏の壁ぎわ）：織機（当て布と革ひも）・焼き入れの水・強化の作業台（入口からの軸の先。MOD の作業台の代わりの
    # 鍛冶台）・溶鉱炉・小さな炉（腰の高さの炉床に焚き火、上に覆い）・砥石
    put(4, 15, Y1 + 1, block("loom", facing="north"))
    put(6, 15, Y1 + 1, block("water_cauldron", level=3))
    put(7, 15, Y1 + 1, block("smithing_table"))
    put(8, 15, Y1 + 1, block("blast_furnace", facing="north", lit=True))
    put(8, 15, Y1 + 2, block("deepslate_bricks"))
    for u in (9, 10):
        put(u, 15, Y1 + 1, block("deepslate_bricks"))
        put(u, 15, Y1 + 3, block("deepslate_bricks"))
        put(u, 14, Y1 + 3, stairs("deepslate_brick", "south", "top"))     # 炉の上の覆い
    put(9, 15, Y1 + 2, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))
    put(10, 15, Y1 + 2, block("deepslate_bricks"))
    put(11, 14, Y1 + 1, block("grindstone", face="floor", facing="north"))
    # 道具の掛け台（裏の壁）
    frame(4, 15, Y1 + 2, "north", "shears")
    frame(6, 15, Y1 + 3, "north", "iron_ingot")
    # 素材の棚（東の壁ぎわ、裏口のそば）：樽と箱、鉄の塊、鎖（鎖かたびらの材料）、羊毛（当て物）
    for v in (9, 10, 11, 14, 15):
        put(13, v, Y1 + 1, block("barrel", facing="west", open=False))
        put(13, v, Y1 + 2, block("barrel", facing="up", open=False))
    put(13, 12, Y1 + 1, block("chest", facing="west", type="single", waterlogged=False))
    put(13, 12, Y1 + 2, block("white_wool"))
    put(13, 13, Y1 + 1, block("iron_block"))
    put(13, 13, Y1 + 2, block("raw_iron_block"))
    for (v, item) in ((9, "leather"), (10, "iron_ingot"), (11, "chain")):
        frame(12, v, Y1 + 2, "west", item)
    for y in (Y1 + 3, Y1 + 4):
        put(11, 13, y, block("chain", axis="y", waterlogged=False))
    # 灯り
    for (u, v) in ((4, 10), (10, 10), (5, 13), (11, 12)):
        hang(u, v, Y2 - 1)


# ---------------------------------------------------------------- 2階：ベルタの仕立ての部屋と寝室
def upstairs():
    # 間仕切り：仕立ての部屋と奥の間（v=8、扉は u=2）、階段の廊下と寝室（u=3、扉は v=10）
    for u in range(1, 14):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(u, 8, y, block("spruce_planks"))
    put(2, 8, Y2 + 1, door(pal.frame, "north", False))
    put(2, 8, Y2 + 2, door(pal.frame, "north", True))
    for v in range(9, 16):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(3, v, y, block("spruce_planks"))
    put(3, 10, Y2 + 1, door(pal.frame, "east", False))
    put(3, 10, Y2 + 2, door(pal.frame, "east", True))
    # 階段の口の手すり（南の端。東は間仕切り、西は外壁）
    put(1, 15, Y2 + 1, fence(pal.frame))
    put(2, 15, Y2 + 1, fence(pal.frame))
    # 仕立ての部屋：裁ち台（革を広げる）、型紙を描く台、寸法の帳面（書見台）、記録の本棚、仕立て中の革鎧
    for u in range(5, 10):
        for v in (3, 4):
            put(u, v, Y2 + 1, slab("spruce", "top"))
    for u in (5, 6):
        for v in (3, 4):
            put(u, v, Y2 + 2, block("brown_carpet"))
    frame(8, 3, Y2 + 2, "up", "shears", rotation=1)
    frame(8, 4, Y2 + 2, "up", "paper")
    frame(9, 3, Y2 + 2, "up", "leather")
    put(7, 5, Y2 + 1, stairs("spruce", "south"))
    put(7, 2, Y2 + 1, stairs("spruce", "north"))
    put(1, 6, Y2 + 1, block("cartography_table"))
    put(12, 6, Y2 + 1, block("lectern", facing="west", has_book=True, powered=False))
    for v in (1, 2):
        for y in (Y2 + 1, Y2 + 2):
            put(13, v, y, block("bookshelf"))
    put(1, 1, Y2 + 1, block("chest", facing="east", type="single", waterlogged=False))
    stand(3, 4, Y2 + 1, "east", base=False, chest=("leather_chestplate", {"color": 0x8E3A2E}))
    for (u, v) in ((4, 4), (10, 4)):
        hang(u, v, ROOF_Y0)
    # 奥の間（寝室と小さな台所）：寝台、衣装の樽、箱、食卓と椅子、燻製器（煙突の前）
    put(12, 14, Y2 + 1, block("gray_bed", facing="south", part="foot", occupied=False))
    put(12, 15, Y2 + 1, block("gray_bed", facing="south", part="head", occupied=False))
    put(13, 10, Y2 + 1, block("barrel", facing="west", open=False))
    put(13, 11, Y2 + 1, block("barrel", facing="west", open=False))
    put(13, 12, Y2 + 1, block("chest", facing="west", type="single", waterlogged=False))
    put(8, 15, Y2 + 1, block("smoker", facing="north", lit=False))
    put(7, 15, Y2 + 1, slab("spruce", "top"))
    put(9, 11, Y2 + 1, slab("spruce", "top"))
    put(9, 12, Y2 + 1, stairs("spruce", "north"))
    x, z = XZ(9, 11)
    candles(w, x, Y2 + 2, z, n=1, lit=True)
    for u in range(5, 9):
        for v in range(11, 14):
            put(u, v, Y2 + 1, block("gray_carpet"))
    hang(8, 12, ROOF_Y0)
    hang(1, 10, ROOF_Y0)
    # 屋根裏（入らない物置）の灯り
    for (u, v) in ((4, 5), (10, 11)):
        put(u, v, YA + 1, lantern(False))


# ---------------------------------------------------------------- まわり：半屋外の作業場・路地・裏通り
def surroundings():
    # 半屋外の作業場の低い石の塀（u=-4 と v=16。柱は2段）。北の門（通りの角から）と南の門（裏通りへ）
    for v in range(0, 17):
        put(-4, v, 1, wall("stone_brick"))
    for u in range(-3, 0):
        put(u, 16, 1, wall("stone_brick"))
    put(-3, 0, 1, wall("stone_brick"))
    for (u, v) in ((-4, 0), (-3, 0), (-4, 4), (-4, 8), (-4, 12), (-4, 16)):
        put(u, v, 2, wall("stone_brick"))
    for u in (-2, -1):
        put(u, 0, 1, block("spruce_fence_gate", facing="north", open=False, powered=False, in_wall=False))
    put(-2, 16, 1, block("spruce_fence_gate", facing="south", open=False, powered=False, in_wall=True))
    # 門の看板（防具職人の作業場）
    put(-3, -1, 2, block("spruce_wall_sign", facing="north", waterlogged=False),
        sign_nbt(["防具職人の", "作業場", "", ""], ["防具職人の", "作業場", "", ""]))
    # 壁ぎわの片流れの屋根（u -2..-1、v 7..15）：本館と同じ黒い瓦。外の端は濃い木の柱と梁で受ける
    for v in range(7, 16):
        put(-1, v, 5, stairs(pal.roof, "east"))
        put(-2, v, 4, stairs(pal.roof, "east"))
        put(-2, v, 3, log(pal.trim, "z", stripped=True))
    for v in (7, 11, 15):
        put(-2, v, 1, log(pal.trim, "y", stripped=True))
        put(-2, v, 2, log(pal.trim, "y", stripped=True))
    # 屋根の下：作業台（防具職人のプレイヤーが使う。MOD の作業台の代わりの鍛冶台）、道具、織機、材料
    put(-1, 8, 1, block("smithing_table"))
    put(-1, 12, 1, block("smithing_table"))
    frame(-1, 9, 3, "west", "shears")
    frame(-1, 11, 3, "west", "iron_ingot")
    put(-1, 13, 1, block("loom", facing="west"))
    put(-1, 14, 1, block("barrel", facing="up", open=False))
    put(-1, 15, 1, block("barrel", facing="up", open=False))
    put(-1, 15, 2, block("barrel", facing="up", open=False))
    hang(-1, 7, 4)
    hang(-1, 14, 4)
    # 屋根のない半分（塀ぎわに寄せ、建物の側を通り道に空ける）：板金を打つ金床、焼き入れの水桶、磨く砥石、
    # 塗りかけの盾（壁の額縁）
    put(-3, 3, 1, block("anvil", facing="north"))
    put(-3, 4, 1, block("water_cauldron", level=3))
    put(-3, 6, 1, block("grindstone", face="floor", facing="east"))
    frame(-1, 2, 3, "west", "shield")
    frame(-1, 4, 3, "west", shield("gray", ("bo", "red")))
    # 交差点の角の再建のランタン灯（両腕：通りと環状路）
    x, z = XZ(-4, -2)
    lamp_post(w, x, 1, z, arms=("north", "east"))
    # 裏通り：裏口の脇の樽
    put(10, 17, 1, block("barrel", facing="up", open=False))


# ---------------------------------------------------------------- 組み立て
def build():
    ground()
    floors()
    stone_walls()
    timber_walls()
    front()
    openings()
    shop()
    workshop()
    upstairs()
    roofs()
    gables()
    chimney()
    surroundings()
    w.finalize()


build()


def patterns_world(shown):
    """型紙の壁に shown 枚（1〜4）の型紙を掛けた形（章が進んだあと）。見本の絵と文書のため。"""
    pw = World(W, H, D)
    pw.blocks = dict(w.blocks)
    pw.nbt = dict(w.nbt)
    keep = set()
    for v in PATTERN_SLOTS:
        x, z = XZ(13, v)
        keep.add((x, Y1 + 3, z))
    pw.entities = [e for e in w.entities if not (e.kind == "item_frame" and e.cell in keep)]
    for i, v in enumerate(PATTERN_SLOTS):
        item, nm = PATTERNS[i] if i < shown else (None, None)
        x, z = XZ(13, v)
        pw.item_frame(x, Y1 + 3, z, "west", item, name=nm)
    return pw


# 見本の絵を描く向き：目の位置と見る所（構造物の中の座標。y=0 が地面）。CI の写真（CiScreenshots.java の MAP_VIEWS）と同じ
VIEWS = {
    "front": ((11, 5, -22), (11, 9, 9)),            # 工房通りの向かいから：張り出しの2階と妻
    "corner": ((-9, 6, -11), (8, 8, 8)),            # 交差点（北西）から：通りと環状路の両方の顔
    "west": ((-20, 5, 14), (2, 6, 10)),             # 環状路（西）から：半屋外の作業場と西の小さな妻
    "close": ((4, 2.8, -9), (7, 3.5, 2)),           # 入口の近く：飾り窓、盾、吊り看板、入口
    "aerial": ((-7, 25, -12), (9, 8, 10)),          # 上から：屋根と西の妻、炉の煙突
    "back": ((24, 14, 34), (10, 8, 12)),            # 裏（南東）から：裏口と煙突
}
# 中の絵：目の位置と見る所（ゲームの中で歩いて見る高さ）
INSIDE = {
    "shop": ((11.5, 3.6, 4.2), (11.5, 3.0, 12.0)),      # 入口から：床の軸の先のカウンターとベルタの金床、奥の炉
    "fitting": ((12.0, 3.6, 10.2), (17.6, 3.3, 5.0)),   # 試着と採寸の場所、型紙の壁、盾の棚
    "armory": ((12.5, 3.6, 6.5), (4.0, 3.0, 7.5)),      # 防具立ての列と騎士団の旗
    "work": ((12.5, 3.7, 12.6), (11.0, 2.8, 18.5)),     # 工房：金床、炉、素材の棚
    "yard": ((2.7, 3.7, 1.6), (1.4, 1.2, 14.0)),        # 半屋外の作業場（北の門の上から南へ）
    "upstairs": ((15.5, 8.6, 9.5), (6.0, 7.6, 5.0)),    # 2階の仕立ての部屋
}


def cut_world(y_cut, world=None):
    world = world or w
    cw = World(W, H, D)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut]
    return cw


def render_views(out_dir, width=960, height=540, fov=70, only=None):
    from PIL import Image
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    scene = Scene(w, ground_y=1)
    images = []
    for name, (eye, target) in VIEWS.items():
        if only and name not in only:
            continue
        image = scene.render(eye=eye, target=target, width=width, height=height, fov=fov,
                             path=os.path.join(out_dir, "armor_%s.png" % name))
        images.append(image)
        print("描きました:", name)
    if len(images) == 6:
        sheet = Image.new("RGB", (width * 3, height * 2))
        for i, image in enumerate(images):
            sheet.paste(image, ((i % 3) * width, (i // 3) * height))
        sheet.save(os.path.join(out_dir, "armor_sheet.png"))
    # 切って真上から見る絵（上が北、下が裏）：1階は +6 の下、2階は屋根裏の床の下
    for name, cut in (("cut_1f", Y2), ("cut_2f", YA)):
        if only and name not in only:
            continue
        Scene(cut_world(cut), ground_y=1).render(eye=(11.0, 52, 11.6), target=(11.0, 0, 11.5), width=width,
                                                 height=height + 140, fov=40,
                                                 path=os.path.join(out_dir, "armor_%s.png" % name))
        print("描きました:", name)
    for name, (eye, target) in INSIDE.items():
        if only and ("in_" + name) not in only:
            continue
        scene.render(eye=eye, target=target, width=width, height=height, fov=80,
                     path=os.path.join(out_dir, "armor_in_%s.png" % name))
        print("描きました:", "in_" + name)
    # 第4章のあとの型紙の壁
    if not only or "in_fitting_ch4" in only:
        Scene(patterns_world(4), ground_y=1).render(eye=INSIDE["fitting"][0], target=INSIDE["fitting"][1], width=width,
                                                    height=height, fov=80,
                                                    path=os.path.join(out_dir, "armor_in_fitting_ch4.png"))
        print("描きました: in_fitting_ch4")


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
    path = os.path.join(out, "armor_workshop.nbt")
    n = w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "もの", len(w.entities), "大きさ", (W, H, D))
    if render_dir:
        render_views(render_dir, only=only)
