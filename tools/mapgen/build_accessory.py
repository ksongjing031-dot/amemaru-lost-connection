"""コネリアの装飾工房（PHASE 4。建築の文書「PHASE 4：装飾工房」の平面図どおり）。
古代結晶の細工所。結晶細工師オーレリアン・ヴェイルの店と工房で、結晶に宿る魔力を読み、込め直す（Phase 0-A）。
工房通りの北側、中央広場の角（環状路との交差点）の建物。正面は南（工房通り）で、棟は通りと平行（平入り）。
西の妻は環状路と中央広場に向け、2階を1つ張り出す（持ち送りで受ける）。正面の入口の上には急な小さな妻（2：1）を出す。
1階は灰色の石（再建の層）に、古代の白い石の細工（石英の窓まわり・要石・腰石）。2階は木組みと漆喰。屋根は赤い瓦（レンガ）の
1：1で、正面の西のショーウィンドウ（出窓）の小屋根だけ緑青の銅（都市のルール⑥の例外）。
中は、前が店（ガラスのカウンターと奥の額縁の棚、壁のガラスの飾り棚、出窓とアーチの窓の飾り、装飾師の作業台、依頼台、
天井の灯り）、後ろが工房（細工の机・磨きの砥石・結晶を読む台・素材の棚）。2階はオーレリアンの書斎と寝室、屋根裏は物置。
外寸 12×14、軒（屋根裏の床）+11、棟 +17（道のブロックからの段数）。
座標：構造物の中で x が東、y が上、z が南。文書の平面図の (u, v) とは X = u + 4、Z = 19 - v の関係
（u は西→東、v は正面＝南→裏＝北）。世界の座標では、構造物の (0, 0, 0) が (x 60, 道の高さ, z -26)。
建物は x 64〜75、z -20〜-7。西は角の前庭（x 60〜63）、東は素材の置き場（x 76〜78）、裏は庭（z -23〜-21）と裏通り。
y=0 が地面（道・石畳・庭）。1階の床のブロック y=1（+1）、2階 y=6（+6）、屋根裏 y=11（+11）。
使い方: python3 tools/mapgen/build_accessory.py [出力のフォルダ] [--render 絵のフォルダ]
  - <出力>/accessory_workshop.nbt（構造物ファイル。出力を省くと、Modの data/amemaru/structures/map/conelia/ に書く）
  - <絵のフォルダ>/accessory_*.png（見本の絵）
ゲームの中では /place template amemaru:map/conelia/accessory_workshop で置ける（置いた範囲の中は片づく）。
留め具の飾り（第3章＝11日目に枠が開く）は、CLASP_SHOWN と clasp_world を参照（Phase 5 で額縁の中身を入れる）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mapgen.arch import Palette, Roof, gable_wall
from mapgen.facade import Orient, cell, origin, window
from mapgen.fixtures import candles, hang_lantern, lamp_post, wall_lantern
from mapgen.world import (AIR, World, banner_nbt, block, door, fence, lantern, lectern_nbt, leaves, log, sign_nbt, slab,
                          stairs, trapdoor, wall)

W, H, D = 19, 24, 22          # u -4..14、y 0..23、v -2..19
Y1, Y2, YA = 1, 6, 11          # 床のブロック（1階・2階・屋根裏）
ROOF_Y0 = 10                   # 屋根の軒先（壁から1つ外）の高さ。壁の上の線は +11

pal = Palette(seed=67, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = pal.rng
w = World(W, H, D)


def XZ(u, v):
    """平面図の (u, v) → 構造物の (X, Z)"""
    return u + 4, 19 - v


def UV(x, z):
    """構造物の (X, Z) → 平面図の (u, v)"""
    return x - 4, 19 - z


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


# 平面図の向き → 世界の向き。v が増える向き（裏）は北
PLAN = {"street": "south", "back": "north", "west": "west", "east": "east"}
OUT = {"north": (0, 1), "south": (0, -1), "west": (-1, 0), "east": (1, 0)}       # 世界の向き → (du, dv)


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


def candle(u, v, y, n=1, color=None, lit=True):
    name = "candle" if color is None else color + "_candle"
    put(u, v, y, block(name, candles=n, lit=lit, waterlogged=False))


# 店の色（都市のルール⑦：水色と白）。旗は「水色の地に白い菱形（磨いた石）と白い縁」、看板の下に見せる品はアメジストの欠片
SHOP_BANNER = [("mr", "white"), ("mc", "light_blue"), ("bo", "white")]


# ---------------------------------------------------------------- 材料
def gray_stone():
    """再建の層の灰色の石：石レンガが主、ひび・苔・安山岩を合わせて2割まで（都市のルール③）"""
    r = rng.random()
    if r < 0.82:
        return block("stone_bricks")
    if r < 0.92:
        return block("cracked_stone_bricks")
    if r < 0.97:
        return block("mossy_stone_bricks")
    return block("andesite")


def ancient_paving():
    """古代の石畳（中央広場の側の前庭）：磨いた閃緑岩が主、閃緑岩と方解石（③⑤）"""
    r = rng.random()
    if r < 0.6:
        return block("polished_diorite")
    if r < 0.85:
        return block("diorite")
    return block("calcite")


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
    """素材の置き場の地面：踏み固めた土と石"""
    r = rng.random()
    if r < 0.4:
        return block("packed_mud")
    if r < 0.7:
        return block("cobblestone")
    return block("coarse_dirt")


SPRUCE_LOG_Y = log("spruce", "y", stripped=True)


def beam(axis):
    return log("spruce", axis, stripped=True)


def pane():
    return block("glass_pane", north=False, south=False, east=False, west=False, waterlogged=False)


# ---------------------------------------------------------------- 窓（平面図の座標で）
def win(face, line, a0, width, y0, height, **kw):
    """face の壁（南・北は v=line、西・東は u=line）に、a0 から幅 width の窓。a0 は南北の壁では u の小さい方、東西の壁では v の小さい方。"""
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        window(w, pal, face, plane, x0, width, y0, height, **kw)
    else:
        plane, zmax = XZ(line, a0)
        window(w, pal, face, plane, zmax - width + 1, width, y0, height, **kw)


def plain_window(face, line, a0, width, y0, height):
    win(face, line, a0, width, y0, height, shutters=False, sill=False, lintel=False, glass_depth=0)


def arched_window(face, line, a0, width=3, y0=Y1 + 1, height=3, sill=True, keystone=True):
    """石の壁のアーチの窓：壁の面にガラス、上の両角を逆さの階段（石英）で丸める、外に石英の窓台、上に要石（模様入りの石英）。"""
    plain_window(face, line, a0, width, y0, height)
    O = Orient(face)
    yt = y0 + height - 1
    if face in ("south", "north"):
        x0, plane = XZ(a0, line)
        ox, oz = origin(face, x0, x0 + width - 1, plane)
    else:
        plane, zmax = XZ(line, a0)
        ox, oz = origin(face, zmax - width + 1, zmax, plane)
    if width >= 3:
        lx, lz = cell(face, ox, oz, 0, 0)
        rx, rz = cell(face, ox, oz, width - 1, 0)
        w.put(lx, yt, lz, stairs("quartz", O.left, "top"))
        w.put(rx, yt, rz, stairs("quartz", O.right, "top"))
    if keystone and width % 2 == 1:
        kx, kz = cell(face, ox, oz, width // 2, 0)
        w.put(kx, yt + 1, kz, block("chiseled_quartz_block"))
    for i in range(width if sill else 0):
        x, z = cell(face, ox, oz, i, 1)
        w.put(x, y0 - 1, z, slab("quartz", "top"))


def flower_box(face, u, v, y=Y2 + 1):
    """窓の下の花箱（ツツジの葉）と、前の板"""
    du, dv = OUT[face]
    put(u + du, v + dv, y, leaves("flowering_azalea_leaves" if rng.random() < 0.6 else "azalea_leaves"))
    put(u + 2 * du, v + 2 * dv, y, trapdoor(pal.frame, face, "bottom", True))


def house_window(face, u, v, width=1, box=True, y0=Y2 + 2):
    """2階の窓：高さ2（+8・+9）。通りと環状路に向く窓の下に花箱"""
    if face in ("north", "south"):
        plain_window(face, v, u, width, y0, 2)
        cells = [(u + i, v) for i in range(width)]
    else:
        plain_window(face, u, v, width, y0, 2)
        cells = [(u, v + i) for i in range(width)]
    if box:
        for (cu, cv) in cells:
            flower_box(face, cu, cv)


# ---------------------------------------------------------------- 地面
def ground():
    for u in range(-4, 15):
        for v in range(-2, 20):
            if v >= 17:
                put(u, v, 0, alley())                         # 裏通り
            elif v <= -1:
                put(u, v, 0, ancient_paving() if u <= -1 else apron_paving())   # 前の石畳（角は古代の石）
            elif u <= -1:
                put(u, v, 0, ancient_paving() if v <= 13 else block("grass_block", snowy=False))  # 角の前庭
            elif u >= 12:
                put(u, v, 0, yard_ground() if v <= 13 else block("grass_block", snowy=False))     # 素材の置き場
            elif v >= 14:
                put(u, v, 0, block("grass_block", snowy=False))                                  # 裏の庭
            else:
                put(u, v, 0, block("cobblestone"))            # 建物の下


# ---------------------------------------------------------------- 床
def floors():
    for u in range(1, 11):
        # 1階（+1）：店はトウヒとダークオークの市松（参考 jewelry_01）、工房はトウヒの板
        for v in range(1, 13):
            if v <= 7:
                put(u, v, Y1, block("spruce_planks") if (u + v) % 2 else block("dark_oak_planks"))
            else:
                put(u, v, Y1, block("spruce_planks"))
        # 2階（+6）：トウヒの板。v 4・9 は1階の天井に見える梁（東西）。階段の口はあける
        for v in range(1, 13):
            if in_stairwell(u, v):
                continue
            put(u, v, Y2, beam("x") if v in (4, 9) else block("spruce_planks"))
        # 屋根裏（+11）
        for v in range(0, 14):
            put(u, v, YA, block("spruce_planks"))
    # 張り出しの2階の床（西の1階の壁の上、u=0）と、その上の屋根裏の床
    for v in range(1, 13):
        put(0, v, Y2, block("spruce_planks"))
    for v in range(0, 14):
        put(0, v, YA, block("spruce_planks"))
    # 店の床の軸（入口からカウンターまで）：水色の敷物
    for v in range(1, 6):
        for u in (5, 6):
            put(u, v, Y1 + 1, block("light_blue_carpet"))


def in_stairwell(u, v):
    """2階の床にあける階段の口（u 1..2、v 9..12）"""
    return 1 <= u <= 2 and 9 <= v <= 12


# ---------------------------------------------------------------- 外壁（1階は石）
def stone_walls():
    for y in range(Y1, Y2):
        for u in range(0, 12):
            put(u, 0, y, gray_stone())
            put(u, 13, y, gray_stone())
        for v in range(0, 14):
            put(0, v, y, gray_stone())
            put(11, v, y, gray_stone())
    # 腰石（いちばん下の段）は古代の白い石（磨いた閃緑岩）。角は石英レンガと石レンガを交互に（古代の細工）
    for u in range(0, 12):
        put(u, 0, Y1, block("polished_diorite"))
        put(u, 13, Y1, block("polished_diorite"))
    for v in range(0, 14):
        put(0, v, Y1, block("polished_diorite"))
        put(11, v, Y1, block("polished_diorite"))
    for (u, v) in ((0, 0), (11, 0), (0, 13), (11, 13)):
        for y in range(Y1 + 1, Y2):
            put(u, v, y, block("polished_andesite") if y % 2 else block("stone_bricks"))
    # 正面の付け柱（u 4・7）：2階の柱の真下。石英の柱で、古代の細工の店らしく
    for u in (4, 7):
        for y in range(Y1 + 1, Y2):
            put(u, 0, y, block("quartz_pillar", axis="y"))


# ---------------------------------------------------------------- 2階の木組み（西は1つ張り出す）
SOUTH_POSTS = (0, 4, 7, 11)            # 正面の柱（1階の付け柱の真上）
WEST_POSTS = (0, 4, 9, 13)             # 西（張り出し）と東の壁の柱
NORTH_POSTS = (0, 4, 7, 11)


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
    run("south", 0, -1, 11, (-1,) + SOUTH_POSTS[1:])
    run("north", 13, -1, 11, (-1,) + NORTH_POSTS[1:])
    run("west", -1, 0, 13, WEST_POSTS)
    run("east", 11, 0, 13, WEST_POSTS)
    # 張り出しの下の持ち送り（トウヒの逆さの階段。1階の石の壁から西へ）
    for v in WEST_POSTS + (2, 6, 11):
        put(-1, v, Y2 - 1, stairs(pal.frame, "east", "top"))
    # 2階の窓。通り（南）と環状路（西）に向く窓の下に花箱
    house_window("south", 2, 0)
    house_window("south", 5, 0, width=2, box=False)
    house_window("south", 9, 0)
    house_window("west", -1, 2)
    house_window("west", -1, 6, width=2)
    house_window("west", -1, 11)
    for v in (2, 11):
        house_window("east", 11, v, box=False)
    for u in (2, 6):
        house_window("north", u, 13, box=False)


# ---------------------------------------------------------------- 屋根
main_roof = Roof(w, pal, XZ(-1, 0)[0], XZ(11, 0)[0], XZ(0, 13)[1], XZ(0, 0)[1], ROOF_Y0, ridge="x", pitch=1,
                 ridge_cap=False)
# 正面の小さな妻（入口の上、u 4..7）：2：1、棟 +16（いちばん上は笠の半ブロック）
front_roof = Roof(w, pal, XZ(4, 0)[0], XZ(7, 0)[0], XZ(0, 6)[1], XZ(0, 0)[1], ROOF_Y0, ridge="z", pitch=2,
                  ends=("end",))
ROOFS = [main_roof, front_roof]


def chimney_zone(u, v, y):
    """暖炉の煙突（2階の寝室の暖炉の上。u 8..9、v 12）：屋根裏を通って、屋根の北の面から出る"""
    return 8 <= u <= 9 and v == 12 and y >= YA


def roof_skip(me):
    others = [r for r in ROOFS if r is not me]

    def skip(x, y, z):
        u, v = UV(x, z)
        if chimney_zone(u, v, y):
            return True
        if -1 <= u <= 11 and 0 <= v <= 13 and y < YA:
            return True       # 建物の中（屋根裏の床より下）には、屋根の部品を置かない
        if 0 <= u <= 10 and 1 <= v <= 12 and y == YA:
            return True       # 屋根裏の床は残す
        return any(o.inside(x, y, z) for o in others)
    return skip


def roofs():
    for r in ROOFS:
        r.build(skip=roof_skip(r))


def gables():
    # 西の妻（u=-1。張り出しの面）：木組み。+14 に梁。梁の下は柱を v 3・5・8・10 に立て、真ん中に屋根裏の窓（幅2×高さ2）
    gable_wall(w, pal, main_roof, XZ(-1, 0)[0], posts=tuple(XZ(0, v)[1] for v in (3, 5, 8, 10)), beam_rows=(14,))
    plain_window("west", -1, 6, 2, 12, 2)
    for v in (6, 7):
        put(-2, v, 11, slab(pal.frame, "top"))
    for y in (15, 16):
        put(-1, 6, y, SPRUCE_LOG_Y)
        put(-1, 7, y, SPRUCE_LOG_Y)
    # 東の妻（u=11）：木組み。柱は v 4・9
    gable_wall(w, pal, main_roof, XZ(11, 0)[0], posts=(XZ(0, 4)[1], XZ(0, 9)[1]), beam_rows=(14,))
    plain_window("east", 11, 6, 2, 12, 2)
    # 正面の小さな妻（v=0、u 4..7）：漆喰に、屋根裏の窓（幅2×高さ2）
    gable_wall(w, pal, front_roof, XZ(0, 0)[1], posts=())
    plain_window("south", 0, 5, 2, 11, 2)
    for u in (5, 6):
        put(u, -1, 10, slab(pal.frame, "top"))


# ---------------------------------------------------------------- 正面（南）：出窓・入口・日よけ・看板
def front():
    # 西の出窓（ショーウィンドウ）：u 0..4、v=-1 に張り出す。腰は石英レンガ、ガラスは3面、小屋根は緑青の銅（ロウ引き）
    for u in range(0, 5):
        put(u, -1, Y1, block("quartz_bricks"))
        for y in range(Y1 + 1, Y1 + 4):
            put(u, -1, y, pane())
        put(u, -1, Y1 + 4, stairs("waxed_oxidized_cut_copper", "north"))
    put(-1, -1, Y1 + 4, stairs("waxed_oxidized_cut_copper", "east"))
    put(5, -1, Y1 + 4, stairs("waxed_oxidized_cut_copper", "west"))
    # 出窓の中（壁を抜いた u 1..3、v=0）：飾り台。真ん中に晶洞の切り口（玄武岩・方解石・アメジスト）
    for u in (1, 2, 3):
        for y in range(Y1 + 1, Y1 + 4):
            put(u, 0, y, AIR)
    put(1, 0, Y1 + 1, block("calcite"))
    put(2, 0, Y1 + 1, block("amethyst_block"))
    put(3, 0, Y1 + 1, block("calcite"))
    put(2, 0, Y1 + 2, block("amethyst_cluster", facing="up", waterlogged=False))
    put(1, 0, Y1 + 2, block("medium_amethyst_bud", facing="up", waterlogged=False))
    frame(3, 0, Y1 + 2, "up", "diamond", rotation=1)
    put(2, 0, Y1 + 4, block("quartz_pillar", axis="x"))
    # 入口（u 5..6）：濃い木の両開きの扉。上に石英の欄間（明かり）と要石
    put(5, 0, Y1 + 1, door(pal.trim, "south", False, hinge="left"))
    put(5, 0, Y1 + 2, door(pal.trim, "south", True, hinge="left"))
    put(6, 0, Y1 + 1, door(pal.trim, "south", False, hinge="right"))
    put(6, 0, Y1 + 2, door(pal.trim, "south", True, hinge="right"))
    put(5, 0, Y1 + 3, stairs("quartz", "east", "top"))
    put(6, 0, Y1 + 3, stairs("quartz", "west", "top"))
    put(5, 0, Y1 + 4, block("chiseled_quartz_block"))
    put(6, 0, Y1 + 4, block("chiseled_quartz_block"))
    # 入口の踏み段（道 0 → 床 +1）
    for u in (5, 6):
        put(u, -1, Y1, stairs("quartz", "north"))
    # 日よけ（扉の上、u 5..6、2列で外へ1段下がる）：濃い木の跳ね上げ戸の台に、水色と白の市松の敷物
    # （参考 jewelry_02 の日よけ。色は旗と同じ）
    for u in (5, 6):
        for k, v in enumerate((-1, -2)):
            cloth = "light_blue_carpet" if (u + k) % 2 else "white_carpet"
            put(u, v, Y1 + 4 - k, trapdoor(pal.trim, "north", "top", False))
            put(u, v, Y1 + 5 - k, block(cloth))
    # 東の窓（u 8..10）：石の壁のアーチの窓（店の大きな窓は床の1段目から）
    arched_window("south", 0, 8, 3, Y1 + 1, 3)
    # 吊り看板（東の付け柱 u=7 から、入口の前へ。道から4）と、看板の下の品（アメジストの欠片）
    put(7, -1, 4, block("spruce_wall_hanging_sign", facing="west", waterlogged=False),
        sign_nbt(["装飾工房", "ヴェイル", "", ""], ["装飾工房", "ヴェイル", "", ""], hanging=True))
    frame(7, -1, 2, "south", "amethyst_shard")
    # 壁のランタン（正面の東の角から、通りへ）
    wall_lamp(11, 0, 5, "south")
    # 旗（水色に白い菱形と白い縁）：2階の正面の両端の柱の間
    for u in (1, 10):
        put(u, -1, 9, block("light_blue_wall_banner", facing="south"), banner_nbt(SHOP_BANNER))


# ---------------------------------------------------------------- 西（環状路と中央広場）・東・裏の開口
def openings():
    # 西の1階：アーチの窓2つ（店の飾りと、工房の明かり）。妻に旗、角に壁のランタン
    arched_window("west", 0, 1, 3, Y1 + 1, 3)
    arched_window("west", 0, 10, 3, Y1 + 1, 3)
    put(-2, 4, 13, block("light_blue_wall_banner", facing="west"), banner_nbt(SHOP_BANNER))
    # 張り出しの下から鎖で吊る灯り（環状路の側）
    hang(-1, 3, Y2 - 1)
    hang(-1, 10, Y2 - 1)
    # 東（素材の置き場へ）：通用口
    put(11, 10, Y1 + 1, door(pal.frame, "east", False))
    put(11, 10, Y1 + 2, door(pal.frame, "east", True))
    put(11, 10, Y1 + 3, stairs("stone_brick", "east", "top"))
    plain_window("east", 11, 5, 1, Y1 + 2, 2)
    # 裏（北、庭へ）：裏口と工房の窓（細工の机の明かり）
    put(9, 13, Y1 + 1, door(pal.frame, "north", False))
    put(9, 13, Y1 + 2, door(pal.frame, "north", True))
    put(9, 13, Y1 + 3, stairs("stone_brick", "north", "top"))
    put(9, 14, Y1, stairs("stone_brick", "south"))
    win("north", 13, 4, 3, Y1 + 2, 2, shutters=True, sill=True, lintel=False, glass_depth=0)
    wall_lamp(9, 13, 5, "north")


# ---------------------------------------------------------------- 暖炉の煙突
def chimney():
    # 暖炉の煙突（2×1）：屋根裏から棟の上 +19 まで。上に石の笠と、煙の焚き火
    for u in (8, 9):
        for y in range(YA, 19):
            put(u, 12, y, block("stone_bricks") if rng.random() < 0.85 else block("cracked_stone_bricks"))
    put(9, 12, 19, slab("stone_brick", "bottom"))
    put(8, 12, 19, block("campfire", lit=True, facing="north", signal_fire=False, waterlogged=False))


# ---------------------------------------------------------------- 1階の中：店
# 奥の額縁の棚（v=8 の壁の南の面。額縁は v=7 に掛ける）：(u, y, 品)。真ん中の上は留め具の枠
GEM_WALL = [(3, 4, "emerald"), (4, 4, "diamond"), (7, 4, "amethyst_shard"), (8, 4, "lapis_lazuli"),
            (3, 3, "gold_nugget"), (4, 3, "quartz"), (7, 3, "prismarine_crystals"), (8, 3, "echo_shard"),
            (5, 4, "gold_ingot"), (6, 4, "prismarine_shard")]
# 東の壁のガラスの飾り棚（u=10 に掛け、u=9 がガラス）：(v, 品)。首飾りとお守りの見立て
VITRINE = [(2, "ender_pearl"), (3, "nautilus_shell"), (4, "heart_of_the_sea")]
# 留め具の枠（第3章＝11日目に開く。Phase 0-A）：額縁の場所と、そのときに入れる品
CLASP_AT = (6, 7, 5)
CLASP_ITEM = ("chain", "留め具")
CLASP_SHOWN = False
# 依頼台の帳面（書見台の本）【補完】
REQUEST_BOOK = ("依頼の帳面", "オーレリアン・ヴェイル", [
    "依頼の帳面\n\n装飾師への頼みごとを、ここに書き残してください。\n腕の確かな装飾師が、順にお受けします。\n\n"
    "―― 装飾工房 オーレリアン・ヴェイル",
    "石の声を聞いてごらん。\n\nどんな石にも、古い時代の響きが眠っています。\n残響の欠片があれば、眠った響きを"
    "揺り起こすこともできます。",
])
# 結晶の古代文字の手習い（工房の書見台の本。エルネストに習っている。Phase 0-A）【補完】
SCRIPT_BOOK = ("結晶文字の手習い", "オーレリアン・ヴェイル", [
    "エルネスト先生に習った結晶の文字\n\n灯す、結ぶ、眠る、応える……\n\n読めない字が、まだ三つある。",
    "先生は「実に、実に興味深い！」と笑うばかりで、答えを教えてくれない。\n\n石の声を、もっとよく聞くこと。",
])


def shop():
    # 東の窓の内側の飾り台（u 8..10、v=1）：方解石の台に、金の品と小さな結晶
    for u in (8, 9, 10):
        put(u, 1, Y1 + 1, block("calcite"))
    frame(8, 1, Y1 + 2, "up", "gold_ingot", rotation=1)
    put(9, 1, Y1 + 2, block("small_amethyst_bud", facing="up", waterlogged=False))
    frame(10, 1, Y1 + 2, "up", "emerald")
    # 西の窓の内側の飾り台（u=1、v 1..3。中央広場から見える）：方解石の台に、育ちかけの結晶と宝石
    for v in (1, 2, 3):
        put(1, v, Y1 + 1, block("calcite"))
    put(1, 1, Y1 + 2, block("medium_amethyst_bud", facing="up", waterlogged=False))
    frame(1, 2, Y1 + 2, "up", "diamond")
    put(1, 3, Y1 + 2, block("large_amethyst_bud", facing="up", waterlogged=False))
    # 依頼台（西の壁、v 4..5）：書見台に依頼の帳面、壁に依頼の紙、上に札
    put(1, 4, Y1 + 1, block("lectern", facing="east", has_book=True, powered=False), lectern_nbt(*REQUEST_BOOK))
    frame(1, 5, Y1 + 2, "east", "paper", rotation=1)
    frame(1, 5, Y1 + 3, "east", "paper")
    frame(1, 4, Y1 + 3, "east", "writable_book")
    put(1, 5, Y1 + 4, block("spruce_wall_sign", facing="east", waterlogged=False),
        sign_nbt(["", "依頼台", "", ""], ["", "依頼台", "", ""]))
    # 東の壁のガラスの飾り棚（u 9..10、v 2..4）：濃い木の台、ガラス、上に濃い木の天板。品は壁（u=10）に掛ける。
    # v 5 は柵の門の前（オーレリアンがカウンターの内側へ入る所）としてあける
    for v in range(2, 5):
        put(9, v, Y1 + 1, block("dark_oak_planks"))
        put(10, v, Y1 + 1, block("dark_oak_planks"))
        put(9, v, Y1 + 2, pane())
        put(9, v, Y1 + 3, block("dark_oak_planks"))
        put(10, v, Y1 + 3, block("dark_oak_planks"))
    for (v, item) in VITRINE:
        frame(10, v, Y1 + 2, "west", item)
    for v in (2, 4):
        candle(9, v, Y1 + 4, n=2, color="white")
    # カウンター（v=6）：西は装飾師の作業台（MOD の作業台の代わりの鍛冶台。客の側から使う）、
    # u 3..8 はガラスのカウンター（下の床に宝石の原石を敷く。参考 jewelry_01）、東の端に柵の門
    put(1, 6, Y1 + 1, block("smithing_table"))
    put(2, 6, Y1 + 1, block("dark_oak_planks"))
    candle(2, 6, Y1 + 2, n=3, color="white")
    bed_of_gems = ["amethyst_block", "calcite", "amethyst_block", "gold_block", "calcite", "amethyst_block"]
    for i, u in enumerate(range(3, 9)):
        put(u, 6, Y1, block(bed_of_gems[i]))
        put(u, 6, Y1 + 1, block("glass"))
    put(9, 6, Y1 + 1, block("dark_oak_planks"))
    put(10, 6, Y1 + 1, block("dark_oak_fence_gate", facing="north", open=False, powered=False, in_wall=False))
    frame(3, 6, Y1 + 2, "up", "diamond", rotation=1)
    frame(4, 6, Y1 + 2, "up", "emerald")
    frame(8, 6, Y1 + 2, "up", "amethyst_shard", rotation=1)
    put(7, 6, Y1 + 2, block("bell", attachment="floor", facing="south", powered=False))   # 呼び鈴（客が鳴らす）
    candle(9, 6, Y1 + 2, n=2, color="light_blue")
    # 奥の額縁の棚（v=8）：トウヒの板の壁。u 1..2 は階段、u=10 は工房への口
    for u in range(3, 10):
        for y in range(Y1 + 1, Y2):
            put(u, 8, y, block("spruce_planks"))
    for y in range(Y1 + 1, Y2):
        put(3, 8, y, SPRUCE_LOG_Y)
        put(9, 8, y, SPRUCE_LOG_Y)
    put(10, 8, Y1 + 3, stairs(pal.frame, "west", "top"))
    put(10, 8, Y1 + 4, block("spruce_planks"))
    for (u, y, item) in GEM_WALL:
        frame(u, 7, y, "south", item)
    item, nm = CLASP_ITEM if CLASP_SHOWN else (None, None)
    frame(*CLASP_AT, "south", item, name=nm)
    put(5, 7, Y1 + 4, block("spruce_wall_sign", facing="south", waterlogged=False),
        sign_nbt(["留め具の枠", "", "準備中", ""] if not CLASP_SHOWN else ["留め具の枠", "", "", ""]))
    # 棚を照らす灯り（天井から直に吊る）
    for u in (3, 8):
        put(u, 7, Y1 + 4, lantern(True))
    # カウンターの真上の吊り灯り（シャンデリア：都市のルール⑦で装飾工房に許した大きな吊り灯り。参考 jewelry_01）：
    # 2本の鎖で濃い木の柵の横木を吊り、両端にろうそく、真ん中の下に2つのランタン（ガラスのカウンターを照らす）。
    # 横木はガラスのカウンターと同じ幅（u 3..8）。カウンターの上に跳び乗れないようにする役目もある
    for u in (5, 6):
        put(u, 6, Y2 - 1, block("chain", axis="y", waterlogged=False))
        put(u, 6, Y1 + 2, lantern(True))
    for u in range(3, 9):
        put(u, 6, Y1 + 3, fence(pal.trim))
    for u in (3, 8):
        candle(u, 6, Y1 + 4, n=3, color="white")
    # 装飾師の作業台の後ろの引き出し（u 1..2、v=7。樽を3段と天板）：作業に使う宝石と金具の小分け。
    # 天井まで詰めて、作業台の上からカウンターの内側へ下りられないようにする
    for u in (1, 2):
        for y in range(Y1 + 1, Y1 + 4):
            put(u, 7, y, block("barrel", facing="south", open=False))
        put(u, 7, Y1 + 4, block("dark_oak_planks"))


# ---------------------------------------------------------------- 1階の中：工房
def workshop():
    # 2階への階段（u 1..2、幅2。v 12 から南へ上る：+2..+6。いちばん上の段 v 8 は2階の床の高さ）
    for (v, y) in ((12, 2), (11, 3), (10, 4), (9, 5), (8, 6)):
        for u in (1, 2):
            put(u, v, y, stairs("spruce", "south"))
            for yy in range(Y1 + 1, y):
                put(u, v, yy, block("spruce_planks"))
    # 細工の机（裏の窓の下、u 4..7）：磨きの砥石、細工の作業台（MOD の作業台の代わりの鍛冶台）、拡大鏡（望遠鏡）、ろうそく
    put(4, 12, Y1 + 1, block("grindstone", face="floor", facing="east"))
    put(5, 12, Y1 + 1, block("smithing_table"))
    put(6, 12, Y1 + 1, block("dark_oak_planks"))
    put(7, 12, Y1 + 1, block("dark_oak_planks"))
    frame(6, 12, Y1 + 2, "up", "spyglass", rotation=3)
    candle(7, 12, Y1 + 2, n=2, color="white")
    put(5, 11, Y1 + 1, stairs("spruce", "south"))
    # 結晶を読む台（u=7、v=10）：石英の柱に、ともった古代結晶（シーランタン）と、まわりのアメジストの結晶
    put(7, 10, Y1 + 1, block("quartz_pillar", axis="y"))
    put(7, 10, Y1 + 2, block("sea_lantern"))
    put(6, 10, Y1 + 2, block("amethyst_cluster", facing="west", waterlogged=False))
    put(8, 10, Y1 + 2, block("amethyst_cluster", facing="east", waterlogged=False))
    put(7, 10, Y1 + 3, block("amethyst_cluster", facing="up", waterlogged=False))
    # 結晶の古代文字の手習い（書見台）
    put(8, 12, Y1 + 1, block("lectern", facing="south", has_book=True, powered=False), lectern_nbt(*SCRIPT_BOOK))
    # 素材の棚（東の壁ぎわ。v 10 は通用口、v 9 は棚の口からの通り道）：原石の樽と箱、壁に素材の見本
    put(10, 12, Y1 + 1, block("barrel", facing="west", open=False))
    put(10, 12, Y1 + 2, block("barrel", facing="up", open=False))
    put(10, 11, Y1 + 1, block("chest", facing="west", type="single", waterlogged=False))
    put(10, 11, Y1 + 2, block("barrel", facing="up", open=False))
    frame(10, 11, Y1 + 3, "west", "raw_gold")
    frame(10, 12, Y1 + 3, "west", "amethyst_shard")
    # 灯り
    for (u, v) in ((5, 10), (9, 11)):
        hang(u, v, Y2 - 1)


# ---------------------------------------------------------------- 2階：オーレリアンの書斎と寝室
def upstairs():
    # 間仕切り：書斎と寝室（v=8、扉は u=4）、階段と寝室（u=3）
    for u in range(3, 11):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(u, 8, y, block("spruce_planks"))
    put(4, 8, Y2 + 1, door(pal.frame, "north", False))
    put(4, 8, Y2 + 2, door(pal.frame, "north", True))
    for v in range(9, 13):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(3, v, y, block("spruce_planks"))
    # 階段の西（張り出しの床、u=0）の手すり
    for v in range(9, 13):
        put(0, v, Y2 + 1, fence(pal.frame))
    # 書斎：正面の窓の下の机と椅子、本棚、結晶の帳面（書見台）、自分の結晶を並べたガラスの棚（張り出しの壁）
    for u in (5, 6):
        put(u, 1, Y2 + 1, slab(pal.trim, "top"))
    put(5, 2, Y2 + 1, stairs("spruce", "north"))
    candle(6, 1, Y2 + 2, n=1, color="white")
    frame(5, 1, Y2 + 2, "up", "paper", rotation=1)
    for v in (4, 5, 6):
        for y in (Y2 + 1, Y2 + 2):
            put(10, v, y, block("bookshelf"))
    put(9, 7, Y2 + 1, block("lectern", facing="south", has_book=True, powered=False), lectern_nbt(*SCRIPT_BOOK))
    for v in (3, 4, 5):
        put(0, v, Y2 + 1, block("dark_oak_planks"))
        put(1, v, Y2 + 1, block("dark_oak_planks"))
        put(1, v, Y2 + 2, pane())
        put(0, v, Y2 + 3, slab(pal.trim, "bottom"))
        put(1, v, Y2 + 3, slab(pal.trim, "bottom"))
    for (v, item) in ((3, "amethyst_shard"), (4, "echo_shard"), (5, "prismarine_crystals")):
        frame(0, v, Y2 + 2, "east", item)
    for u in range(3, 9):
        for v in range(3, 6):
            put(u, v, Y2 + 1, block("light_blue_carpet"))
    for (u, v) in ((3, 4), (8, 4)):
        hang(u, v, ROOF_Y0)
    # 寝室：寝台（水色）、暖炉（裏の壁。上は煙突）、衣装の箱、枕もとの台
    put(5, 11, Y2 + 1, block("light_blue_bed", facing="north", part="foot", occupied=False))
    put(5, 12, Y2 + 1, block("light_blue_bed", facing="north", part="head", occupied=False))
    put(4, 12, Y2 + 1, block("spruce_planks"))
    candle(4, 12, Y2 + 2, n=1, color="white")
    for u in (7, 10):
        for y in range(Y2 + 1, ROOF_Y0 + 1):
            put(u, 12, y, block("stone_bricks"))
    for u in (8, 9):
        put(u, 12, Y2 + 1, block("campfire", lit=True, facing="south", signal_fire=False, waterlogged=False))
        put(u, 11, Y2 + 3, stairs("stone_brick", "north", "top"))
        for y in range(Y2 + 2, ROOF_Y0 + 1):
            put(u, 12, y, block("stone_bricks"))
    put(10, 9, Y2 + 1, block("chest", facing="west", type="single", waterlogged=False))
    put(10, 10, Y2 + 1, block("barrel", facing="west", open=False))
    for u in range(6, 10):
        for v in (9, 10):
            put(u, v, Y2 + 1, block("white_carpet"))
    # 屋根裏（入らない物置）の灯り
    for (u, v) in ((3, 6), (6, 3)):
        put(u, v, YA + 1, lantern(False))


# ---------------------------------------------------------------- まわり：角の前庭・素材の置き場・裏の庭
def surroundings():
    # 角の前庭（u -4..-1）：交差点の角の再建のランタン灯（両腕：通りと環状路）、道しるべ（交差点。都市のルール⑦）
    x, z = XZ(-4, -2)
    lamp_post(w, x, 1, z, arms=("north", "east"))
    for y in (1, 2, 3):
        put(-4, 3, y, fence("spruce"))
    put(-4, 2, 2, block("spruce_wall_sign", facing="south", waterlogged=False),
        sign_nbt(["↑ 冒険者ギルド", "← 中央広場", "→ 工房通り・東門", ""]))
    put(-4, 4, 2, block("spruce_wall_sign", facing="north", waterlogged=False),
        sign_nbt(["↑ 市場通り", "← 工房通り・東門", "→ 中央広場", ""]))
    # 張り出しの下：中央広場を向く腰かけと、窓の下の花壇（アリウム・ヤグルマギク・白いチューリップ）
    for v in (6, 7):
        put(-1, v, 1, stairs("spruce", "east"))
    for v in (5, 8):
        put(-1, v, 1, trapdoor(pal.frame, "south" if v == 5 else "north", "bottom", True))
    flowers = ["allium", "cornflower", "white_tulip"]
    for v in (1, 2, 3, 10, 11, 12):
        put(-1, v, 0, block("grass_block", snowy=False))
        put(-1, v, 1, block(flowers[v % 3]))
        put(-2, v, 1, trapdoor(pal.frame, "west", "bottom", True))
    # 素材の置き場（u 12..14）：低い石の塀（通りの側と東の路地の側）と門、壁ぎわの片流れの屋根（赤い瓦）
    for u in (12, 13, 14):
        if u != 13:
            put(u, 0, 1, wall("stone_brick"))
    put(13, 0, 1, block("spruce_fence_gate", facing="south", open=False, powered=False, in_wall=True))
    for v in range(1, 14):
        put(14, v, 1, wall("stone_brick"))
    for (u, v) in ((12, 0), (14, 0), (14, 4), (14, 9), (14, 13)):
        put(u, v, 2, wall("stone_brick"))
    put(12, -1, 2, block("spruce_wall_sign", facing="south", waterlogged=False),
        sign_nbt(["装飾工房", "素材の置き場", "", ""]))
    # 片流れの屋根（u 12..14、v 5..12）：建物の壁から外の塀へ下がる。外の端は塀の上の濃い木の梁で受ける
    # （通り道の u=13 に柱を立てない）
    for v in range(5, 13):
        put(12, v, 5, stairs(pal.roof, "west"))
        put(13, v, 4, stairs(pal.roof, "west"))
        put(14, v, 3, stairs(pal.roof, "west"))
        put(14, v, 2, log(pal.trim, "z", stripped=True))
    # 屋根の下と手前（建物の壁ぎわ u=12 に寄せ、u=13 を通り道に空ける）：原石を切る石切台、磨きの砥石、原石の樽と箱、
    # 切る前の結晶
    put(12, 1, 1, block("barrel", facing="up", open=False))
    put(12, 2, 1, block("grindstone", face="floor", facing="north"))
    put(12, 5, 1, block("barrel", facing="up", open=False))
    put(12, 6, 1, block("stonecutter", facing="east"))
    put(12, 7, 1, block("barrel", facing="up", open=False))
    put(12, 7, 2, block("barrel", facing="up", open=False))
    put(12, 8, 1, block("raw_copper_block"))
    put(12, 9, 1, block("amethyst_block"))
    put(12, 9, 2, block("amethyst_cluster", facing="up", waterlogged=False))
    put(12, 11, 1, block("chest", facing="east", type="single", waterlogged=False))
    put(12, 12, 1, block("barrel", facing="up", open=False))
    put(12, 8, 4, lantern(True))
    # 裏の庭（v 14..16）：花壇、裏口から裏通りの門への小道、低い石の塀
    # 庭の塀は建物の裏だけ（u -1..14）。西の前庭の北の端は、そのまま裏通りへ抜ける（行き止まりを作らない）
    for u in range(-1, 15):
        if u != 9:
            put(u, 16, 1, wall("stone_brick"))
    for v in (14, 15):
        put(-1, v, 1, wall("stone_brick"))
    put(9, 16, 1, block("spruce_fence_gate", facing="north", open=False, powered=False, in_wall=True))
    for v in range(14, 16):
        for u in (-4, -3, -2):
            put(u, v, 0, ancient_paving())
    # 裏通りの再建のランタン灯（片腕。前庭から裏通りへ抜ける所）
    x, z = XZ(-3, 17)
    lamp_post(w, x, 1, z, arms=("north",))
    for v in (14, 15):
        put(9, v, 0, block("dirt_path"))
    garden = ["allium", "cornflower", "oxeye_daisy", "lily_of_the_valley", "allium", "white_tulip"]
    for u in range(0, 8):
        put(u, 15, 1, block(garden[u % len(garden)]))
    for u in (2, 3):
        put(u, 14, 1, stairs("spruce", "south"))
    put(1, 14, 1, trapdoor(pal.frame, "west", "bottom", True))
    put(4, 14, 1, trapdoor(pal.frame, "east", "bottom", True))


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

def clasp_world():
    """留め具の枠が開いたあと（第3章＝11日目）の形：額縁に留め具を入れ、札の「準備中」を消す。見本の絵と文書のため。"""
    cw = World(W, H, D)
    cw.blocks = dict(w.blocks)
    cw.nbt = dict(w.nbt)
    x, z = XZ(CLASP_AT[0], CLASP_AT[1])
    cw.entities = [e for e in w.entities if not (e.kind == "item_frame" and e.cell == (x, CLASP_AT[2], z))]
    item, nm = CLASP_ITEM
    cw.item_frame(x, CLASP_AT[2], z, "south", item, name=nm)
    sx, sz = XZ(5, 7)
    cw.nbt[(sx, Y1 + 4, sz)] = sign_nbt(["留め具の枠", "", "", ""])
    return cw


# 見本の絵を描く向き：目の位置と見る所（構造物の中の座標。y=0 が地面）。CI の写真（CiScreenshots.java の MAP_VIEWS）と同じ
VIEWS = {
    "front": ((10, 5, 38), (10, 8.5, 12)),            # 工房通りの向かいから：出窓・入口・日よけ・正面の小さな妻
    "corner": ((-12, 6, 33), (9, 8, 12)),           # 交差点（南西）から：通りと中央広場の両方の顔
    "west": ((-22, 6, 13), (3, 8, 12)),             # 環状路（西）から：張り出しの2階と西の妻、角の前庭
    "close": ((3, 2.8, 28), (4, 3.2, 19)),          # 入口の近く：出窓の晶洞、呼び鈴、吊り看板、日よけ
    "aerial": ((-8, 27, 34), (9, 9, 12)),           # 上から：屋根と正面の小さな妻、出窓の銅
    "back": ((26, 14, -12), (9, 8, 10)),            # 裏（北東）から：裏の庭と素材の置き場、煙突
}
INSIDE = {
    "shop": ((10.0, 3.6, 18.6), (10.0, 3.3, 11.0)),       # 入口から：床の軸の先のガラスのカウンター、奥の額縁の棚、天井の灯り
    "counter": ((10.0, 3.9, 15.2), (10.0, 2.4, 13.2)),    # カウンターの上から：ガラスの下の原石、上の宝石、呼び鈴
    "vitrine": ((8.0, 3.6, 17.8), (14.5, 3.0, 15.0)),    # 東の壁のガラスの飾り棚と、東の窓の飾り台
    "desk": ((9.5, 3.6, 16.5), (4.5, 3.0, 15.0)),         # 西：依頼台と、中央広場の側の窓の飾り
    "work": ((7.6, 3.7, 10.6), (13.5, 3.0, 7.6)),         # 工房：細工の机、結晶を読む台、素材の棚
    "upstairs": ((13.5, 8.6, 13.0), (7.5, 7.6, 17.5)),    # 2階の書斎
    "bedroom": ((9.0, 8.6, 10.0), (13.0, 7.6, 7.5)),      # 2階の寝室と暖炉
    "yard": ((18.2, 6.6, 21.4), (16.8, 1.2, 11.5)),       # 素材の置き場（通りの門の上から北へ）
    "plaza_side": ((2.0, 2.6, 20.3), (1.5, 2.0, 8.5)),   # 角の前庭（交差点から北へ）
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
                             path=os.path.join(out_dir, "accessory_%s.png" % name))
        images.append(image)
        print("描きました:", name)
    if len(images) == 6:
        sheet = Image.new("RGB", (width * 3, height * 2))
        for i, image in enumerate(images):
            sheet.paste(image, ((i % 3) * width, (i // 3) * height))
        sheet.save(os.path.join(out_dir, "accessory_sheet.png"))
    # 切って真上から見る絵（上が北＝裏、下が正面）：1階は +6 の下、2階は屋根裏の床の下
    for name, cut in (("cut_1f", Y2), ("cut_2f", YA)):
        if only and name not in only:
            continue
        Scene(cut_world(cut), ground_y=1).render(eye=(9.5, 52, 11.0), target=(9.5, 0, 10.9), width=width,
                                                 height=height + 140, fov=40,
                                                 path=os.path.join(out_dir, "accessory_%s.png" % name))
        print("描きました:", name)
    for name, (eye, target) in INSIDE.items():
        if only and ("in_" + name) not in only:
            continue
        scene.render(eye=eye, target=target, width=width, height=height, fov=80,
                     path=os.path.join(out_dir, "accessory_in_%s.png" % name))
        print("描きました:", "in_" + name)
    # 留め具の枠：いま（準備中）と、第3章のあと。額縁の棚の正面から
    for name, world in (("clasp_now", w), ("clasp_ch3", None)):
        if only and name not in only:
            continue
        world = world or clasp_world()
        Scene(world, ground_y=1).render(eye=(6.2, 4.3, 14.4), target=(10.4, 4.7, 11.9), width=width, height=height,
                                        fov=70, path=os.path.join(out_dir, "accessory_%s.png" % name))
        print("描きました:", name)


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
    path = os.path.join(out, "accessory_workshop.nbt")
    n = w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(w.blocks), "種類", n, "もの", len(w.entities), "大きさ", (W, H, D))
    if render_dir:
        render_views(render_dir, only=only)
