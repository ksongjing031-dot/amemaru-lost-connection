"""コネリアのフリーマーケット（PHASE 7。建築の文書「PHASE 7：フリーマーケット」の設計どおり）。

ギルド公認の、冒険者のための取引区画（Phase 0-A）。市場監督フィン・ロッソが露店の区画を貸し出し、相場表を自分で作っている。
市場通りの西の裏（環状路の外）。市場通りからは本屋と革屋の間の通り抜け（渡り廊下の下）を抜けて東の門へ、環状路からは北の門へ。
  - 30×30（x -48..-19、z 62..91）を低い石の塀で囲む。門は北（環状路から）・東（通り抜けから）・西（釣り堀への路地）。
  - 真ん中に古い泉（噴水）。北の門 → 泉 → 南の休み場の木が1本の軸で、泉を東西に横切る中央通路の両側に露店の島が4つ。
  - 露店の区画は20。はじめは12（中央通路の両側の8と、北西・南西の塀ぎわの4）で、寄付で島の裏の8が開く（育つ前・育った後）。
  - 露店の型は7つ（平らな布の日よけ・布のテント・木の切妻屋根・品を掛ける壁のある店・荷車・敷物・台だけ）を、色と品で変える。
    屋根のない型（荷車・敷物・台だけ）は、はじめに3つ、増えたあとに6つ。
  - 中に入れる所の樽は、鍵をかける（Lock。ただで物を入れられる入れ物にしない）。チェスト・かまど・醸造台は置かない。

座標：平面図の (u, v) は、u が西→東（西の塀が u=0、東の塀が u=29）、v が北→南（北の塀が v=0、南の塀が v=29）。
世界の座標では x = u - 48、z = v + 62。高さ y は道のブロックが 0（足もとが 1）。北の塀の外の2列（v -2・-1）は環状路の端の帯。
構造物の (0, 0, 0) は世界の (x -48, 道のブロック, z 60)。構造物の中では X = u、Y = y、Z = v + 2。

使い方: python3 tools/mapgen/build_freemarket.py [出力のフォルダ] [--render 絵のフォルダ] [--only 名前,...] [--ci]
  - <出力>/free_market.nbt（はじめの形、30×10×32）と free_market_expanded.nbt（寄付で露店が増えたあと。同じ所に重ねる）
  - --ci：自動の確認の写真の座標（CiScreenshots の MAP_VIEWS の行）を出す
ゲームの中では /place template amemaru:map/conelia/free_market で置ける。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from nbtlib.tag import Compound, String

from mapgen.arch import Palette
from mapgen.fixtures import hang_lantern, lamp_post
from mapgen.world import (AIR, World, banner_nbt, block, fence, lantern, leaves, lectern_nbt, log, sign_nbt, slab,
                          stairs, trapdoor, wall)

W, H, D = 30, 10, 32
ORIGIN = (-48, 0, 60)            # 構造物の (0, 0, 0) の世界の座標（y は道のブロック）
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

pal = Palette(seed=707, roof="brick", roof_block="bricks", trim="dark_oak", post="spruce", beam="spruce",
              plaster="calcite", frame="spruce")
rng = pal.rng
LOCK = "amemaru:free_market_lock"   # 樽の鍵（この名前のアイテムを持たないと開かない）


def XZ(u, v):
    return u, v + 2


class Plan:
    """1つの形（はじめ・増えたあと）の世界。"""

    def __init__(self):
        self.w = World(W, H, D)

    def put(self, u, v, y, state, nbt=None):
        x, z = XZ(u, v)
        if 0 <= x < W and 0 <= z < D and 0 <= y < H:
            self.w.put(x, y, z, state, nbt)

    def get(self, u, v, y):
        x, z = XZ(u, v)
        return self.w.get(x, y, z)

    def name(self, u, v, y):
        st = self.get(u, v, y)
        return st[0] if st else "air"

    def frame(self, u, v, y, facing, item, rotation=0, name=None):
        x, z = XZ(u, v)
        self.w.item_frame(x, y, z, facing, item, rotation=rotation, name=name)

    def stand(self, u, v, y, facing, **kw):
        x, z = XZ(u, v)
        self.w.armor_stand(x, y, z, facing=facing, **kw)


# ---------------------------------------------------------------- 材料（都市のルール③⑤）
def grid_paving(u, v):
    """フリーマーケットの中の広場：石レンガの線（5ごと）と丸石の格子（再建の層。都市のルール⑤）"""
    if u % 5 == 2 or v % 5 == 2:
        r = rng.random()
        return block("stone_bricks") if r < 0.85 else block("cracked_stone_bricks")
    r = rng.random()
    if r < 0.78:
        return block("cobblestone")
    if r < 0.92:
        return block("mossy_cobblestone")
    return block("andesite")


def band_paving():
    """環状路の端の帯（北の塀の外）：石レンガと安山岩"""
    r = rng.random()
    if r < 0.62:
        return block("stone_bricks")
    if r < 0.86:
        return block("polished_andesite")
    return block("cracked_stone_bricks")


def white_stone():
    """古い泉（古代の層の白い石）：磨いた閃緑岩6・閃緑岩2・方解石1"""
    r = rng.random()
    if r < 0.66:
        return block("polished_diorite")
    if r < 0.88:
        return block("diorite")
    return block("calcite")


def wall_stone():
    return wall("mossy_stone_brick") if rng.random() < 0.22 else wall("stone_brick")


def barrel(facing="up"):
    """樽（鍵つき。中は開けられない）"""
    return block("barrel", facing=facing, open=False), Compound({"id": String("minecraft:barrel"), "Lock": String(LOCK)})


def put_barrel(p, u, v, y, facing="up"):
    st, nbt = barrel(facing)
    p.put(u, v, y, st, nbt)


def crate(p, u, v, y, wood="spruce"):
    """木箱（板のブロック。開けられない）"""
    p.put(u, v, y, block(wood + "_planks"))


def wall_sign(p, u, v, y, facing, lines, wood="spruce"):
    p.put(u, v, y, block(wood + "_wall_sign", facing=facing, waterlogged=False), sign_nbt(lines))


def hanging_sign(p, u, v, y, rotation, front, back=None, wood="spruce"):
    p.put(u, v, y, block(wood + "_hanging_sign", rotation=rotation, attached=False, waterlogged=False),
          sign_nbt(front, back if back is not None else front, hanging=True))


def pole_sign(p, u, v, rotation, lines, wood="spruce"):
    """柵の柱（1段）の上に立てた看板（屋根のない露店の札）"""
    p.put(u, v, 1, fence(wood))
    p.put(u, v, 2, block(wood + "_sign", rotation=rotation, waterlogged=False), sign_nbt(lines))


ROT = {"south": 0, "west": 4, "north": 8, "east": 12}    # 立てた看板・吊り看板の rotation（表が向く方）


# ---------------------------------------------------------------- 区画（露店）
class Plot:
    """露店の区画。u0..u0+w-1、v0..v0+d-1。facing は客が立つ側（表）の向き（"north" か "south"）。"""

    def __init__(self, num, key, u0, v0, w, d, facing, stage, style, colors, wood, title, goods):
        self.num, self.key = num, key
        self.u0, self.v0, self.w, self.d = u0, v0, w, d
        self.facing, self.stage, self.style = facing, stage, style
        self.colors, self.wood, self.title, self.goods = colors, wood, title, goods

    def cell(self, a, b):
        """a：表から見て左→右（西→東）、b：表の列が 0、奥へ増える"""
        u = self.u0 + a
        v = self.v0 + self.d - 1 - b if self.facing == "south" else self.v0 + b
        return u, v

    def cells(self):
        return [(u, v) for u in range(self.u0, self.u0 + self.w) for v in range(self.v0, self.v0 + self.d)]

    @property
    def back(self):
        """奥の向き"""
        return OPP[self.facing]


# 木：面（台・屋根・荷台・腰かけ）はどの区画もトウヒ。ここの木は縁（柱・日よけの垂れ・車輪・引き手・横木）の木（都市のルール③）
SURFACE = "spruce"
#           番号 記号 u0  v0 w  d  表       段      型        色                  縁の木    品の名前
PLOTS = [
    Plot(1, "J", 23, 10, 4, 3, "south", "base", "awning", ("red", "white"), "spruce", "鉱石と延べ棒", "ore"),
    Plot(2, "R", 23, 17, 4, 3, "north", "base", "tent", ("green", "white"), "spruce", "薬と薬草", "herb"),
    Plot(3, "I", 20, 10, 3, 3, "south", "base", "gable", None, "spruce", "結晶と宝石の欠片", "gem"),
    Plot(4, "Q", 20, 17, 3, 3, "north", "base", "table", None, "dark_oak", "魚の干物", "fish"),
    Plot(5, "D", 7, 10, 3, 3, "south", "base", "tent", ("orange", "white"), "spruce", "布と敷物", "cloth"),
    Plot(6, "L", 7, 17, 3, 3, "north", "base", "awning", ("blue", "white"), "spruce", "パンと焼き菓子", "bread"),
    Plot(7, "C", 3, 10, 4, 3, "south", "base", "booth", None, "dark_oak", "古道具", "gear"),
    Plot(8, "K", 3, 17, 4, 3, "north", "base", "gable", None, "dark_oak", "ろうそくと灯り", "candle"),
    Plot(9, "F", 7, 1, 4, 3, "south", "base", "cart", None, "spruce", "野菜と果物", "veg"),
    Plot(10, "E", 1, 1, 4, 3, "south", "base", "rug", ("red", "yellow"), "spruce", "鉢と陶器", "pot"),
    Plot(11, "O", 1, 26, 4, 3, "north", "base", "booth", None, "spruce", "地図と巻物", "map"),
    Plot(12, "P", 7, 26, 4, 3, "north", "base", "awning", ("yellow", "white"), "spruce", "はちみつと花", "honey"),
    Plot(13, "h", 23, 7, 4, 3, "north", "more", "awning", ("purple", "white"), "spruce", "お守りと小物", "charm"),
    Plot(14, "g", 20, 7, 3, 3, "north", "more", "rug", ("light_gray", "gray"), "spruce", "骨と魔物の素材", "bone"),
    Plot(15, "t", 23, 20, 4, 3, "south", "more", "gable", None, "spruce", "革と毛皮", "leather"),
    Plot(16, "s", 20, 20, 3, 3, "south", "more", "cart", None, "dark_oak", "薪と炭", "wood"),
    Plot(17, "b", 7, 7, 3, 3, "north", "more", "tent", ("cyan", "white"), "spruce", "染め粉", "dye"),
    Plot(18, "a", 3, 7, 4, 3, "north", "more", "table", None, "spruce", "焼き魚と汁物", "snack"),
    Plot(19, "n", 7, 20, 3, 3, "south", "more", "booth", None, "spruce", "釣り道具", "fishing"),
    Plot(20, "m", 3, 20, 4, 3, "south", "more", "awning", ("lime", "white"), "spruce", "種と苗", "seed"),
]
BASE = [p for p in PLOTS if p.stage == "base"]
MORE = [p for p in PLOTS if p.stage == "more"]

# 品：台の上に置くブロック（counter）、台の上に寝かせる額縁（frames）、奥の棚（back）
GOODS = {
    "ore": dict(counter=[], frames=["iron_ingot", "gold_ingot", "copper_ingot"],
                back=[block("raw_iron_block"), "crate", block("raw_copper_block")]),
    "herb": dict(counter=[block("potted_fern"), block("potted_azure_bluet")], frames=["glass_bottle", "sugar"],
                 back=[block("water_cauldron", level=3), "crate", block("potted_cornflower")]),
    "gem": dict(counter=[block("amethyst_cluster", facing="up", waterlogged=False)], frames=["amethyst_shard", "emerald"],
                back=[block("amethyst_block")]),
    "fish": dict(counter=[], frames=["cod", "salmon", "dried_kelp"], back=[block("dried_kelp_block"), "barrel"]),
    "gear": dict(counter=[], frames=["arrow", "iron_axe"], wall=["iron_sword", "bow"]),
    "bread": dict(counter=[block("cake", bites=0)], frames=["bread", "cookie"],
                  back=[block("hay_block", axis="y"), "crate", block("hay_block", axis="y")]),
    "cloth": dict(counter=[block("red_carpet"), block("light_blue_carpet")], frames=["string"],
                  back=[block("red_wool"), block("yellow_wool"), block("light_blue_wool"), block("white_wool")]),
    "candle": dict(counter=[block("candle", candles=3, lit=True, waterlogged=False),
                            block("red_candle", candles=2, lit=True, waterlogged=False)], frames=["candle"],
                   back=["crate", block("lantern", hanging=False, waterlogged=False), "crate", "barrel"]),
    "veg": dict(cart=[block("melon"), block("pumpkin", ), block("hay_block", axis="y"), block("pumpkin")],
                frames=["apple", "carrot", "potato", "beetroot"]),
    "pot": dict(rug=[block("decorated_pot", facing="south", waterlogged=False), block("potted_red_tulip"),
                     block("decorated_pot", facing="south", waterlogged=False)],
                frames=["brick", "clay_ball"]),
    "map": dict(counter=[], frames=["paper", "feather"], wall=["map", "book"]),
    "honey": dict(counter=[block("potted_dandelion"), block("honeycomb_block")], frames=["honey_bottle", "honeycomb"],
                  back=[block("honey_block"), "crate", block("bee_nest", facing="south", honey_level=0)]),
    "charm": dict(counter=[], frames=["rabbit_foot", "feather", "amethyst_shard"], back=["crate", "barrel", "crate"]),
    "bone": dict(rug=[block("bone_block", axis="y"), block("slime_block"), block("bone_block", axis="y")],
                 frames=["bone", "string", "slime_ball"]),
    "leather": dict(counter=[], frames=["leather", "rabbit_hide"], back=[block("brown_wool"), "crate", block("brown_wool")]),
    "wood": dict(cart=[log("oak", "x"), log("spruce", "x"), block("coal_block"), log("birch", "x")],
                 frames=["charcoal", "stick"]),
    "dye": dict(counter=[block("potted_poppy"), block("potted_blue_orchid")], frames=["red_dye", "yellow_dye", "blue_dye"],
                back=[block("lime_wool"), block("magenta_wool"), block("cyan_wool")]),
    "snack": dict(table=[block("water_cauldron", level=3)], frames=["cooked_cod", "baked_potato", "bread"]),
    "fishing": dict(counter=[], frames=["tropical_fish_bucket", "string"], wall=["fishing_rod"]),
    "seed": dict(counter=[block("potted_oak_sapling"), block("potted_spruce_sapling")],
                 frames=["wheat_seeds", "pumpkin_seeds", "melon_seeds"],
                 back=[block("hay_block", axis="y"), "crate", block("potted_azalea_bush")]),
}


def put_good(p, u, v, y, g):
    if g == "crate":
        crate(p, u, v, y)
    elif g == "barrel":
        put_barrel(p, u, v, y)
    else:
        p.put(u, v, y, g)


def deck(p, plot, wood):
    """露店の床（板）"""
    for (u, v) in plot.cells():
        p.put(u, v, 0, block(wood + "_planks"))


def counter_row(p, plot, top=True):
    """表の列：台（両はしは柱の台）。台の上の真ん中に品。"""
    wood = SURFACE
    for a in range(plot.w):
        u, v = plot.cell(a, 0)
        if a in (0, plot.w - 1):
            p.put(u, v, 1, block(wood + "_planks"))
        elif plot.style == "awning" and a % 2 == 0:
            put_barrel(p, u, v, 1, plot.facing)
        else:
            p.put(u, v, 1, log(wood, "x", stripped=True))


def corner_posts(p, plot, top_y=3, front_from=2):
    """四すみの柱：奥の柱は地面から、表の柱は台の上から"""
    for a in (0, plot.w - 1):
        u, v = plot.cell(a, plot.d - 1)
        for y in range(1, top_y + 1):
            p.put(u, v, y, fence(plot.wood))
        u, v = plot.cell(a, 0)
        for y in range(front_from, top_y + 1):
            p.put(u, v, y, fence(plot.wood))


def counter_goods(p, plot, goods):
    """台の上（真ん中の列）に品のブロックと、寝かせた額縁"""
    mids = [plot.cell(a, 0) for a in range(1, plot.w - 1)]
    blocks_ = list(goods.get("counter", []))
    frames = list(goods.get("frames", []))
    for i, (u, v) in enumerate(mids):
        if blocks_:
            p.put(u, v, 2, blocks_.pop(0))
        elif frames:
            p.frame(u, v, 2, "up", frames.pop(0), rotation=(i * 2) % 8)
    return frames


def back_row(p, plot, goods):
    """奥の列：品の棚（木箱・鍵つきの樽・品のブロック）"""
    items = list(goods.get("back", []))
    for a in range(1, plot.w - 1):
        u, v = plot.cell(a, plot.d - 1)
        if items:
            put_good(p, u, v, 1, items.pop(0))
    return items


def side_frames(p, plot, frames):
    """残りの額縁は、奥の棚の上に寝かせる"""
    for a in range(1, plot.w - 1):
        if not frames:
            return
        u, v = plot.cell(a, plot.d - 1)
        if p.get(u, v, 1) is not None and p.get(u, v, 2) is None and \
                p.get(u, v, 1)[0] in ("spruce_planks", "oak_planks", "dark_oak_planks", "barrel", "hay_block",
                                      "brown_wool", "dried_kelp_block", "raw_iron_block", "raw_copper_block",
                                      "amethyst_block", "honey_block", "lime_wool", "magenta_wool", "cyan_wool",
                                      "red_wool", "yellow_wool", "light_blue_wool", "white_wool"):
            p.frame(u, v, 2, "up", frames.pop(0), rotation=a % 8)


def plot_sign(p, plot, y=3):
    """表の真ん中に吊り看板（区画の番号と品）"""
    a = plot.w // 2 if plot.w % 2 else plot.w // 2 - 1
    u, v = plot.cell(a, 0)
    hanging_sign(p, u, v, y, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""],
                 ["区画 %d" % plot.num, plot.title, "", ""])


def seller_light(p, plot, y=3):
    """売り場の奥の上に吊るした灯り"""
    a = plot.w // 2
    u, v = plot.cell(a, 1)
    p.put(u, v, y, lantern(True))


def style_awning(p, plot):
    """平らな布の日よけ：柱4本、台、上にハーフと2色のしまの布"""
    goods = GOODS[plot.goods]
    counter_row(p, plot)
    corner_posts(p, plot)
    frames = counter_goods(p, plot, goods)
    back_row(p, plot, goods)
    side_frames(p, plot, frames)
    c1, c2 = plot.colors
    for a in range(plot.w):
        for b in range(plot.d):
            u, v = plot.cell(a, b)
            p.put(u, v, 4, slab(SURFACE, "bottom"))
            p.put(u, v, 5, block((c1 if a % 2 == 0 else c2) + "_carpet"))
    # 表の縁：日よけの垂れ（開いたトラップドア）
    for a in range(plot.w):
        u, v = plot.cell(a, -1)
        p.put(u, v, 4, trapdoor(plot.wood, plot.facing, "top", True))
    plot_sign(p, plot)
    seller_light(p, plot)


def style_tent(p, plot):
    """布のテント：四すみの柱の上に、色の羊毛の屋根（真ん中が1段高い）"""
    goods = GOODS[plot.goods]
    counter_row(p, plot)
    corner_posts(p, plot)
    frames = counter_goods(p, plot, goods)
    back_row(p, plot, goods)
    side_frames(p, plot, frames)
    c1, c2 = plot.colors
    mid = [plot.w // 2] if plot.w % 2 else [plot.w // 2 - 1, plot.w // 2]
    for b in range(plot.d):
        for a in range(plot.w):
            u, v = plot.cell(a, b)
            if a in mid:
                p.put(u, v, 5, block(c1 + "_wool"))
            else:
                p.put(u, v, 4, block(c2 + "_wool"))
    a = mid[0]
    u, v = plot.cell(a, 0)
    hanging_sign(p, u, v, 4, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""])
    u, v = plot.cell(mid[-1], 1)
    p.put(u, v, 4, lantern(True))


def style_gable(p, plot):
    """木の切妻屋根：棟は奥へ通る（表から見ると三角）。2進んで1上がる（露店の屋根。都市のルール⑥）"""
    goods = GOODS[plot.goods]
    counter_row(p, plot)
    corner_posts(p, plot)
    frames = counter_goods(p, plot, goods)
    back_row(p, plot, goods)
    side_frames(p, plot, frames)
    wood = SURFACE
    # 屋根は表へ1つ出す（妻の出1）。奥は背中合わせの区画があるので出さない
    for b in range(-1, plot.d):
        for a in range(plot.w):
            u, v = plot.cell(a, b)
            if a == 0:
                p.put(u, v, 4, stairs(wood, "east"))
            elif a == plot.w - 1:
                p.put(u, v, 4, stairs(wood, "west"))
            else:
                p.put(u, v, 5, slab(wood, "bottom"))
    # 奥の妻：棟の下の横木（縁の木）
    for a in range(1, plot.w - 1):
        u, v = plot.cell(a, plot.d - 1)
        p.put(u, v, 4, log(plot.wood, "x"))
    a = (plot.w - 1) // 2
    u, v = plot.cell(a, 0)
    hanging_sign(p, u, v, 4, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""])
    u, v = plot.cell(plot.w // 2, 1)
    p.put(u, v, 4, lantern(True))


def style_booth(p, plot):
    """品を掛ける壁のある店：奥に板の壁（額縁の品）、平らな板の屋根、台"""
    goods = GOODS[plot.goods]
    wood = SURFACE
    counter_row(p, plot)
    corner_posts(p, plot)
    frames = counter_goods(p, plot, goods)
    wall_items = list(goods.get("wall", []))
    for a in range(plot.w):
        u, v = plot.cell(a, plot.d - 1)
        for y in (1, 2, 3):
            if a in (0, plot.w - 1):
                continue
            p.put(u, v, y, block(wood + "_planks"))
    # 奥の壁の額縁（表へ向く。売り手の頭の上の高さ）
    for a in range(1, plot.w - 1):
        if wall_items:
            u, v = plot.cell(a, plot.d - 2)
            p.frame(u, v, 3, plot.facing, wall_items.pop(0))
    for a in range(plot.w):
        for b in range(plot.d):
            u, v = plot.cell(a, b)
            p.put(u, v, 4, slab(wood, "bottom"))
    for a in range(plot.w):
        u, v = plot.cell(a, -1)
        p.put(u, v, 4, trapdoor(plot.wood, plot.facing, "top", True))
    if plot.goods == "gear":
        u, v = plot.cell(plot.w - 2, 1)
        p.stand(u, v, 1, plot.facing, head="chainmail_helmet", chest="iron_chestplate")
    # 灯り：売り手の入る口（左の横）の上に吊るす（奥の壁の額縁と重ねない）
    u, v = plot.cell(0, 1)
    p.put(u, v, 3, lantern(True))
    if plot.goods == "map":
        u, v = plot.cell(1, 1)
        p.put(u, v, 1, block("lectern", facing=plot.facing, has_book=True, powered=False),
              lectern_nbt("コネリアのまわりの地図", "地図の露店", ["コネリアのまわり\n\n北：塔の道と北門\n東：工房通りと東門\n"
                                                                "南：市場通りと南門\n西：祈りの道と水門"]))
    side_frames(p, plot, frames)
    plot_sign(p, plot)


def style_cart(p, plot):
    """荷車の露店（屋根なし）：荷台と車輪、品を山に積む。札は柱の上"""
    goods = GOODS[plot.goods]
    wood, trim = SURFACE, plot.wood
    # 荷台（表の2列、真ん中）
    loads = list(goods.get("cart", []))
    for a in ([1, 2] if plot.w == 4 else [1]):
        for b in (0, 1):
            u, v = plot.cell(a, b)
            p.put(u, v, 1, slab(wood, "top"))
            if loads:
                p.put(u, v, 2, loads.pop(0))
    # 車輪（荷台の両わきの開いたトラップドア）と、引き手（奥の柵）
    for b in (1,):
        u, v = plot.cell(0, b)
        p.put(u, v, 1, trapdoor(trim, "west", "bottom", True))
        u, v = plot.cell(plot.w - 1, b)
        p.put(u, v, 1, trapdoor(trim, "east", "bottom", True))
    # 札と、売り手の腰かけ、品の額縁を寝かせた木箱
    u, v = plot.cell(0, 0)
    pole_sign(p, u, v, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""])
    frames = list(goods.get("frames", []))
    u, v = plot.cell(plot.w - 1, 0)
    crate(p, u, v, 1)
    if frames:
        p.frame(u, v, 2, "up", frames.pop(0))
    u, v = plot.cell(plot.w - 1, plot.d - 1)
    crate(p, u, v, 1)
    if frames:
        p.frame(u, v, 2, "up", frames.pop(0))
    u, v = plot.cell(1, plot.d - 1)
    p.put(u, v, 1, stairs(wood, plot.back))
    u, v = plot.cell(0, plot.d - 1)
    p.put(u, v, 1, fence(trim))
    p.put(u, v, 2, lantern(False))


def style_rug(p, plot):
    """敷物の露店（屋根なし）：2色の敷物の上に品。右の列は売り手が入る口（敷物だけ）。奥に売り手の腰かけ、柵の上の灯り"""
    goods = GOODS[plot.goods]
    c1, c2 = plot.colors
    items = list(goods.get("rug", []))
    frames = list(goods.get("frames", []))
    for a in range(plot.w):
        for b in range(plot.d - 1):
            u, v = plot.cell(a, b)
            p.put(u, v, 1, block((c1 if (a + b) % 2 == 0 else c2) + "_carpet"))
    for a in range(plot.w - 1):
        if items:
            u, v = plot.cell(a, 0)
            p.put(u, v, 1, items.pop(0))
    u, v = plot.cell(0, 1)
    crate(p, u, v, 1)
    if frames:
        p.frame(u, v, 2, "up", frames.pop(0))
    # 奥：札の柱、売り手の腰かけ、品を寝かせた木箱、灯りの柱
    u, v = plot.cell(0, plot.d - 1)
    pole_sign(p, u, v, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""])
    u, v = plot.cell(1, plot.d - 1)
    p.put(u, v, 1, stairs("spruce", plot.back))
    for a in range(2, plot.w - 1):
        u, v = plot.cell(a, plot.d - 1)
        crate(p, u, v, 1)
        if frames:
            p.frame(u, v, 2, "up", frames.pop(0))
    u, v = plot.cell(plot.w - 1, plot.d - 1)
    p.put(u, v, 1, fence("spruce"))
    p.put(u, v, 2, fence("spruce"))
    p.put(u, v, 3, lantern(False))


def style_table(p, plot):
    """台だけの露店（屋根なし）：柵の脚に灯り、板の台に品、奥に鍵つきの樽と腰かけ、柱の上の札"""
    goods = GOODS[plot.goods]
    wood = SURFACE
    frames = list(goods.get("frames", []))
    tables = list(goods.get("table", []))
    u, v = plot.cell(0, 0)
    p.put(u, v, 1, fence(plot.wood))
    p.put(u, v, 2, lantern(False))
    for a in range(1, plot.w):
        u, v = plot.cell(a, 0)
        p.put(u, v, 1, block(wood + "_planks"))
        if tables:
            p.put(u, v, 2, tables.pop(0))
        elif frames:
            p.frame(u, v, 2, "up", frames.pop(0), rotation=a)
    for a in range(2, plot.w):
        u, v = plot.cell(a, plot.d - 1)
        put_barrel(p, u, v, 1)
        if frames:
            p.frame(u, v, 2, "up", frames.pop(0))
    u, v = plot.cell(1, plot.d - 1)
    p.put(u, v, 1, stairs(wood, plot.back))
    u, v = plot.cell(0, plot.d - 1)
    pole_sign(p, u, v, ROT[plot.facing], ["区画 %d" % plot.num, plot.title, "", ""])


STYLES = {"awning": style_awning, "tent": style_tent, "gable": style_gable, "booth": style_booth,
          "cart": style_cart, "rug": style_rug, "table": style_table}


def empty_plot(p, plot):
    """空き区画（はじめの形）：縁を磨いた安山岩で囲み、真ん中に札"""
    for (u, v) in plot.cells():
        edge = u in (plot.u0, plot.u0 + plot.w - 1) or v in (plot.v0, plot.v0 + plot.d - 1)
        if edge:
            p.put(u, v, 0, block("polished_andesite"))
    a = plot.w // 2 if plot.w % 2 else plot.w // 2 - 1
    u, v = plot.cell(a, 1)
    pole_sign(p, u, v, ROT[plot.facing], ["区画 %d" % plot.num, "空き", "寄付で", "ひらきます"])


# ---------------------------------------------------------------- 地面・塀・門
NORTH_GATE = range(13, 17)      # 北の門（u）
EW_GATE = range(13, 17)         # 東と西の門（v）
PILLARS = (0, 2, 7, 12, 17, 22, 27, 29)


def ground(p):
    for u in range(W):
        for v in (-2, -1):
            p.put(u, v, 0, band_paving())
        for v in range(0, 30):
            p.put(u, v, 0, grid_paving(u, v))


def walls(p):
    """低い石の塀（石レンガの塀1段）と、5ごとの石の柱（上に灯りか笠）"""
    def gate(u, v):
        return (v == 0 and u in NORTH_GATE) or (u in (0, W - 1) and v in EW_GATE)
    ring = [(u, 0) for u in range(W)] + [(u, 29) for u in range(W)] + \
           [(0, v) for v in range(1, 29)] + [(W - 1, v) for v in range(1, 29)]
    for i, (u, v) in enumerate(ring):
        if gate(u, v):
            continue
        p.put(u, v, 0, block("stone_bricks"))
        pillar = (v in (0, 29) and u in PILLARS) or (u in (0, W - 1) and v in PILLARS)
        if pillar:
            p.put(u, v, 1, block("stone_bricks"))
            p.put(u, v, 2, block("chiseled_stone_bricks"))
            lit = (u + v) % 2 == 1 or (u, v) in ((0, 0), (29, 0), (0, 29), (29, 29))
            p.put(u, v, 3, lantern(False) if lit else slab("stone_brick", "bottom"))
        else:
            p.put(u, v, 1, wall_stone())


def gate_frame(p, cells, axis, side_out, lines_out, lines_in, banners=True, height=6):
    """門：両わきに石の台と皮をはいだトウヒの柱、上に横木と笠、吊り看板、旗（黄と橙のしま）、横木の下の灯り。
    cells は門の口のマス（並び順）、axis は横木の向き、side_out は外の向き。"""
    first, last = cells[0], cells[-1]
    du, dv = (1, 0) if axis == "x" else (0, 1)
    posts = [(first[0] - du, first[1] - dv), (last[0] + du, last[1] + dv)]
    for (u, v) in posts:
        p.put(u, v, 1, block("stone_bricks"))
        for y in range(2, height):
            p.put(u, v, y, log("spruce", "y", stripped=True))
        p.put(u, v, height, log("spruce", axis, stripped=True))
        p.put(u, v, height + 1, slab("dark_oak", "bottom"))
    for (u, v) in cells:
        p.put(u, v, height, log("spruce", axis, stripped=True))
        p.put(u, v, height + 1, stairs("dark_oak", side_out) if False else slab("dark_oak", "bottom"))
    # 吊り看板（真ん中の2つ）と、両はしの灯り
    rot = ROT[side_out]
    mid = cells[len(cells) // 2 - 1: len(cells) // 2 + 1]
    for i, (u, v) in enumerate(mid):
        hanging_sign(p, u, v, height - 1, rot, lines_out[i], lines_in[i])
    for (u, v) in (cells[0], cells[-1]):
        p.put(u, v, height - 1, block("chain", axis="y", waterlogged=False))
        p.put(u, v, height - 2, lantern(True))
    if banners:
        ou, ov = STEP[side_out]
        for i, (u, v) in enumerate(posts):
            base, stripe = ("yellow", "orange") if i == 0 else ("orange", "yellow")
            p.put(u + ou, v + ov, height - 1, block(base + "_wall_banner", facing=side_out),
                  banner_nbt([("ss", stripe), ("bo", "white")]))
    return posts


def gates(p):
    # 北の門（環状路から）。外に旗、柱の外の面にエメラルドの額縁（都市のルール⑦の品）
    posts = gate_frame(p, [(u, 0) for u in NORTH_GATE], "x", "north",
                       [["フリー", "マーケット", "", ""], ["ギルド公認", "取引区画", "", ""]],
                       [["環状路", "", "", ""], ["↑ 市場通り", "（東）", "", ""]])
    for (u, v) in posts:
        p.frame(u, v - 1, 3, "north", "emerald")
    # 東の門（通り抜けから）。旗は置かない（旗は北の門の2枚。東の入口の目じるしは、通り抜けの上の渡り廊下の旗2枚（PHASE 5）。
    # 都市のルール⑦の「1施設に1〜3枚」）
    gate_frame(p, [(W - 1, v) for v in EW_GATE], "z", "east",
               [["フリー", "マーケット", "", ""], ["露店の広場", "", "", ""]],
               [["→ 市場通り", "", "", ""], ["中央広場", "", "", ""]], banners=False)
    # 西の門（釣り堀への路地）
    gate_frame(p, [(0, v) for v in EW_GATE], "z", "west",
               [["釣り堀への", "路地", "", ""], ["フリー", "マーケット", "", ""]],
               [["← 釣り堀", "", "", ""], ["フリー", "マーケット", "", ""]], banners=False, height=5)


# ---------------------------------------------------------------- 古い泉（噴水）
FOUNTAIN = (12, 17)             # 縁の u（v も同じ）。水は 13..16、真ん中の柱は 14..15
PILLAR = ((14, 14), (15, 14), (14, 15), (15, 15))


def fountain(p):
    a0, a1 = FOUNTAIN
    for u in range(a0, a1 + 1):
        for v in range(a0, a1 + 1):
            rim = u in (a0, a1) or v in (a0, a1)
            if rim:
                p.put(u, v, 0, block("polished_diorite"))
                p.put(u, v, 1, white_stone())
            else:
                p.put(u, v, 0, block("calcite") if (u + v) % 2 else block("polished_diorite"))
                if (u, v) in PILLAR:
                    p.put(u, v, 1, block("quartz_bricks"))
                    p.put(u, v, 2, block("quartz_pillar", axis="y"))
                    p.put(u, v, 3, block("chiseled_quartz_block"))
                    p.put(u, v, 4, block("water", level=0))
                else:
                    p.put(u, v, 1, block("water", level=0))
    # 苔（古い泉）：縁の四すみと、ところどころ
    for (u, v) in ((a0, a0), (a1, a1), (a0, a1 - 2), (a1, a0 + 1)):
        p.put(u, v, 2, block("moss_carpet"))


def fountain_flow(world):
    """見本の絵だけ：柱の上の水が四方へ落ちる流れ（ゲームでは置いたあとに水が流れてこうなる）。
    絵の道具は水を箱で描くので、落ちる水は柱に沿った薄い板（水色のガラス板）で表す"""
    for (u, v, ax) in ((13, 14, "z"), (13, 15, "z"), (16, 14, "z"), (16, 15, "z"),
                       (14, 13, "x"), (15, 13, "x"), (14, 16, "x"), (15, 16, "x")):
        x, z = XZ(u, v)
        for y in (2, 3, 4):
            if ax == "z":
                st = block("light_blue_stained_glass_pane", north=(v == 15), south=(v == 14), east=False, west=False,
                           waterlogged=False)
            else:
                st = block("light_blue_stained_glass_pane", east=(u == 14), west=(u == 15), north=False, south=False,
                           waterlogged=False)
            world.put(x, y, z, st)


# ---------------------------------------------------------------- 市場監督の小屋と相場表（北の門の東）
def booth(p):
    """市場監督フィン・ロッソの小屋：台の後ろが立ち位置。西の面が相場表（北の門から入ると見える）。屋根は黄と橙のしまの布。"""
    u0, u1, v0, v1 = 18, 23, 1, 3
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 1):
            p.put(u, v, 0, block("spruce_planks"))
    for (u, v) in ((u0, v0), (u1, v0), (u0, v1), (u1, v1)):
        for y in (1, 2, 3):
            p.put(u, v, y, log("spruce", "y", stripped=True))
    for u in range(u0 + 1, u1):
        for y in (1, 2, 3):
            p.put(u, v0, y, block("spruce_planks"))
    for y in (1, 2, 3):
        p.put(u0, 2, y, block("spruce_planks"))
    # 相場表（西の面の看板6枚と、上の題の看板）
    board = [(1, 3, ["相場表", "市場監督", "フィン・ロッソ", ""]),
             (2, 3, ["相場は", "生き物なんだ", "", ""]),
             (3, 3, ["本日の相場は", "市場の手帳で", "", ""]),
             (1, 2, ["出品 2％", "売れたら 5％", "", ""]),
             (2, 2, ["露店で買うと", "手数料は半分", "", ""]),
             (3, 2, ["露店の区画", "1〜12 貸し出し", "（寄付で20まで）", ""])]
    for v, y, lines in board:
        wall_sign(p, u0 - 1, v, y, "west", lines, wood="dark_oak")
    p.frame(u0 - 1, 2, 1, "west", "emerald")
    # 台（表）、書見台の台帳、呼び鈴、腰かけ
    for u in range(u0 + 1, u1):
        p.put(u, v1, 1, block("spruce_planks"))
    p.put(u0 + 1, v1, 2, block("bell", attachment="floor", facing="south", powered=False))
    p.frame(u0 + 3, v1, 2, "up", "writable_book")
    p.frame(u0 + 4, v1, 2, "up", "emerald", rotation=1)
    p.put(u0 + 1, 2, 1, block("lectern", facing="south", has_book=True, powered=False),
          lectern_nbt("露店の台帳", "フィン・ロッソ", ["露店の台帳\n\n区画 1〜12：貸し出し中\n区画 13〜20：寄付で開く\n\n"
                                                       "相場は生き物なんだ。\n毎朝、相場表を書き直す。"]))
    p.put(u1 - 1, 2, 1, stairs("spruce", "north"))
    # 屋根：黄と橙のしまの布（フリーマーケットの色）。表へ1つ出す
    for u in range(u0, u1 + 1):
        for v in range(v0, v1 + 2):
            p.put(u, v, 4, slab("spruce", "bottom"))
            p.put(u, v, 5, block(("yellow" if u % 2 == 0 else "orange") + "_carpet"))
    for u in range(u0, u1 + 1):
        p.put(u, v1 + 2, 4, trapdoor("spruce", "south", "top", True))
    hanging_sign(p, u0 + 2, v1 + 1, 3, ROT["south"], ["市場監督", "フィン・ロッソ", "", ""], ["市場監督", "", "", ""])
    hanging_sign(p, u0 + 3, v1 + 1, 3, ROT["south"], ["露店の", "貸し出し", "", ""], ["露店の", "貸し出し", "", ""])
    p.put(u0 + 2, 2, 3, lantern(True))


# ---------------------------------------------------------------- 荷置き場（北東の角）：売り手の箱と手押し車
def cargo(p):
    # 西の山（麻袋2と木箱2）
    p.put(24, 1, 1, block("brown_wool"))
    p.put(24, 1, 2, block("brown_wool"))
    crate(p, 25, 1, 1)
    crate(p, 25, 1, 2)
    # 東の山（鍵つきの樽3と木箱2）。間の u 26 は空ける
    put_barrel(p, 27, 1, 1)
    put_barrel(p, 28, 1, 1)
    put_barrel(p, 28, 1, 2)
    crate(p, 28, 2, 1)
    crate(p, 28, 3, 1)
    # 手押し車（荷台と車輪、引き手）
    for v in (2, 3):
        p.put(26, v, 1, slab("spruce", "top"))
    p.put(26, 2, 2, block("hay_block", axis="y"))
    p.put(26, 3, 2, block("composter", level=0))
    p.put(25, 2, 1, trapdoor("dark_oak", "west", "bottom", True))
    p.put(27, 2, 1, trapdoor("dark_oak", "east", "bottom", True))
    p.put(26, 4, 1, fence("spruce"))
    # 札：北の塀の柱（u 27）の南の面に掛ける
    p.put(27, 1, 2, block("spruce_wall_sign", facing="south", waterlogged=False), sign_nbt(["露店の", "荷置き場", "", ""]))


# ---------------------------------------------------------------- 休み場（南の真ん中と南東）
def rest(p):
    # 木：石レンガの植え込み（u 13..16、v 25..28）、真ん中に2×2の幹と、ツツジの葉の冠
    for u in range(13, 17):
        for v in range(25, 29):
            edge = u in (13, 16) or v in (25, 28)
            if edge:
                p.put(u, v, 1, slab("stone_brick", "bottom") if v == 28 else stairs("stone_brick", OPP[
                    "north" if v == 25 else "west" if u == 13 else "east" if u == 16 else "south"]))
            else:
                p.put(u, v, 0, block("grass_block", snowy=False))
    for u in (14, 15):
        for v in (26, 27):
            for y in range(1, 5):
                p.put(u, v, y, log("oak", "y"))
    for u in range(11, 19):
        for v in range(23, 30):
            for y in (5, 6, 7, 8):
                du, dv = abs(u - 14.5), abs(v - 26.5)
                r = du * du + dv * dv
                if y == 5 and r <= 12 or y == 6 and r <= 14 or y == 7 and r <= 9 or y == 8 and r <= 3:
                    if (u, v) in ((14, 26), (15, 26), (14, 27), (15, 27)) and y <= 5:
                        continue
                    if v >= 29 and y < 7:
                        continue
                    p.put(u, v, y, block("flowering_azalea_leaves" if rng.random() < 0.45 else "azalea_leaves",
                                         persistent=True, distance=1, waterlogged=False))
    for (u, v) in ((14, 26), (15, 27)):
        p.put(u, v, 5, log("oak", "y"))
    # 植え込みの北の腰かけ（泉の方を向く）と、植え込みの中の花
    for u in (13, 14, 15, 16):
        p.put(u, 24, 1, stairs("spruce", "south"))
    p.put(12, 24, 1, trapdoor("spruce", "west", "bottom", True))
    p.put(17, 24, 1, trapdoor("spruce", "east", "bottom", True))
    # 南東：食べる卓と腰かけ、花壇、水桶、灯り
    for (u, v) in ((21, 26), (25, 26)):
        p.put(u, v, 1, fence("spruce"))
        p.put(u, v, 2, block("white_carpet"))
        p.put(u - 1, v, 1, stairs("spruce", "west"))
        p.put(u + 1, v, 1, stairs("spruce", "east"))
    flowers = ["poppy", "cornflower", "oxeye_daisy", "allium", "dandelion"]
    for i, u in enumerate(range(19, 29)):
        p.put(u, 28, 0, block("grass_block", snowy=False))
        p.put(u, 28, 1, block(flowers[i % len(flowers)]))
    for u in range(19, 29):
        p.put(u, 27, 1, trapdoor("spruce", "north", "bottom", True))
    p.put(28, 25, 1, block("water_cauldron", level=3))
    p.put(28, 24, 1, block("hay_block", axis="y"))


# ---------------------------------------------------------------- 灯り（再建のランタン灯と、腰かけ）
LAMPS = [((10, 8), ("east",)), ((19, 8), ("west",)), ((10, 21), ("east",)), ((19, 21), ("west",)),
         ((24, 24), ("north", "west")), ((11, 27), ("north",))]


def lights(p):
    for (u, v), arms in LAMPS:
        x, z = XZ(u, v)
        lamp_post(p.w, x, 1, z, arms=arms)


def benches(p):
    """泉を向く腰かけ（広場の北と南の、中央の道の両わき）。ひじ掛けは道の側に1つ"""
    for (u0, v, f, arm) in ((10, 9, "south", 12), (18, 9, "south", 17), (10, 20, "north", 12), (18, 20, "north", 17)):
        for u in (u0, u0 + 1):
            p.put(u, v, 1, stairs("spruce", OPP[f]))
        p.put(arm, v, 1, trapdoor("spruce", "east" if arm == 12 else "west", "bottom", True))


GARLANDS = (3, 9, 20, 26)        # 中央通路を渡す灯りの綱（u）。島の表の露店の、表の柱から柱へ


def garlands(p):
    """中央通路の上に渡した灯りの綱：表の柱を +5 まで伸ばし、柵の横木に鎖でランタンを2つ吊るす（下の空きは2）"""
    for u in GARLANDS:
        for v in (12, 17):
            for y in range(1, 6):
                st = p.get(u, v, y)
                if y == 1 and st is not None and st[0] != "air":
                    continue                      # 台（板）はそのまま
                p.put(u, v, y, fence("spruce"))
        for v in range(13, 17):
            p.put(u, v, 5, fence("spruce"))
        for v in (14, 15):
            p.put(u, v, 4, block("chain", axis="y", waterlogged=False))
            p.put(u, v, 3, lantern(True))


def gate_roof(p):
    """北の門の小さな屋根（横木の上に、濃い木の階段の切妻）"""
    for u in range(11, 19):
        p.put(u, -1, 6, stairs("dark_oak", "south"))
        p.put(u, 1, 6, stairs("dark_oak", "north"))
        p.put(u, 0, 7, slab("dark_oak", "bottom"))


# ---------------------------------------------------------------- 組み立て
def build(expanded=False):
    rng.seed(707)
    p = Plan()
    ground(p)
    walls(p)
    gates(p)
    fountain(p)
    booth(p)
    cargo(p)
    for plot in PLOTS:
        if plot.stage == "base" or expanded:
            STYLES[plot.style](p, plot)
        else:
            empty_plot(p, plot)
    rest(p)
    benches(p)
    lights(p)
    garlands(p)
    gate_roof(p)
    p.w.finalize()
    return p


base = build(False)
more = build(True)


def overlay():
    """増えたあとに重ねる構造物：増える8区画の箱（地面から屋根まで、日よけの垂れを含む1つ外まで）だけ。"""
    keep = set()
    for plot in MORE:
        for u in range(plot.u0 - 1, plot.u0 + plot.w + 1):
            for v in range(plot.v0 - 1, plot.v0 + plot.d + 1):
                for y in range(0, 7):
                    keep.add((u, v, y))
    ov = World(W, H, D)
    owned = set()
    for (u, v, y) in keep:
        x, z = XZ(u, v)
        if not (0 <= x < W and 0 <= z < D):
            continue
        st_more, st_base = more.w.get(x, y, z), base.w.get(x, y, z)
        inside = any(pl.u0 <= u < pl.u0 + pl.w and pl.v0 <= v < pl.v0 + pl.d for pl in MORE)
        if inside or st_more != st_base:
            owned.add((x, y, z))
            if st_more is not None:
                ov.blocks[(x, y, z)] = st_more
                if (x, y, z) in more.w.nbt:
                    ov.nbt[(x, y, z)] = more.w.nbt[(x, y, z)]
    base_cells = {e.cell for e in base.w.entities}
    ov.entities = [e for e in more.w.entities if e.cell not in base_cells]
    return ov, owned


OVERLAY, OVERLAY_OWNED = overlay()


# ---------------------------------------------------------------- 見本の絵（構造物の中の座標。y=0 が地面のブロック、足もとが 1）
# 商店街の南（market_south.nbt）は、構造物の (0, 0, 0) が世界の (x -18, z 60)。フリーマーケットから東へ 30
SOUTH_AT = (30, 0, 0)
VIEWS = {
    "north_gate": ((14.5, 3.6, -6.0), (14.5, 2.8, 14.0)),       # 環状路から北の門：門の看板と旗、泉、休み場の木
    "east_gate": ((38.0, 2.62, 17.0), (15.0, 3.0, 17.0)),       # 商店街の通り抜け（渡り廊下の下）から東の門と中央通路
    "lane": ((27.5, 2.62, 17.0), (12.0, 2.6, 17.0)),            # 中央通路を東の門から西へ：両側の露店と泉
    "fountain": ((21.4, 3.3, 23.6), (12.5, 2.2, 13.0)),         # 南東から泉と、北西・南西の島
    "booth": ((14.5, 2.62, 9.5), (19.5, 3.0, 3.6)),             # 北の門を入ってすぐ：市場監督の小屋と相場表
    "rest": ((14.5, 3.3, 21.4), (14.5, 4.0, 30.0)),             # 泉の南から休み場の木と腰かけ
    "aerial": ((-8.0, 21.0, 41.0), (15.5, 0.0, 14.5)),          # 上から（南西）。奥に商店街の南
    "west": ((-3.5, 3.0, 17.0), (20.0, 3.0, 17.0)),             # 西の門の外（釣り堀への路地）から
}
VIEWS_MORE = {
    "more_aerial": VIEWS["aerial"],
    "more_north": ((1.6, 2.62, 7.5), (28.0, 2.4, 7.5)),         # 北の通路：島の裏に開いた露店
    "more_south": ((28.0, 2.62, 26.0), (1.0, 2.4, 26.0)),       # 南の通路
}
CI_SHOTS = [("north_gate", "north_gate"), ("east_gate", "east_gate"), ("lane", "lane"), ("fountain", "fountain"),
            ("booth", "booth"), ("rest", "rest"), ("aerial", "aerial"), ("more_aerial", "more_aerial"),
            ("more_north", "more_north")]


def ci_views():
    """自動の確認の写真の目と見る所（フリーマーケットを置いた所から）。CiScreenshots の MAP_VIEWS と同じ"""
    allv = dict(VIEWS)
    allv.update(VIEWS_MORE)
    return [(shot, allv[name][0], allv[name][1]) for shot, name in CI_SHOTS]


def with_flow(world):
    cw = World(world.w, world.h, world.d)
    cw.blocks = dict(world.blocks)
    cw.nbt = dict(world.nbt)
    cw.entities = list(world.entities)
    fountain_flow(cw)
    return cw


def combined_world(world):
    """商店街の南を東に並べた写し（東の門の見本の絵）"""
    import build_market as bm
    W2 = SOUTH_AT[0] + bm.south.w.w
    D2 = max(D, bm.south.w.d)
    cw = World(W2, max(H, bm.south.w.h), D2)
    for (x, y, z), st in bm.south.w.blocks.items():
        if st[0] == "structure_void":
            continue
        cw.blocks[(x + SOUTH_AT[0], y, z)] = st
        if (x, y, z) in bm.south.w.nbt:
            cw.nbt[(x + SOUTH_AT[0], y, z)] = bm.south.w.nbt[(x, y, z)]
    for e in bm.south.w.entities:
        from mapgen.world import Entity
        cw.entities.append(Entity(e.kind, (e.pos[0] + SOUTH_AT[0], e.pos[1], e.pos[2]),
                                  (e.cell[0] + SOUTH_AT[0], e.cell[1], e.cell[2]), e.nbt, facing=e.facing, item=e.item,
                                  items=getattr(e, "items", ())))
    for (x, y, z), st in world.blocks.items():
        cw.blocks[(x, y, z)] = st
        if (x, y, z) in world.nbt:
            cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities += list(world.entities)
    return cw


def cut_world(world, y_cut):
    cw = World(world.w, world.h, world.d)
    for (x, y, z), st in world.blocks.items():
        if y < y_cut:
            cw.blocks[(x, y, z)] = st
            if (x, y, z) in world.nbt:
                cw.nbt[(x, y, z)] = world.nbt[(x, y, z)]
    cw.entities = [e for e in world.entities if e.cell[1] < y_cut]
    return cw


def render_views(out_dir, width=960, height=540, fov=70, only=None):
    from mapgen.render import Scene
    os.makedirs(out_dir, exist_ok=True)
    wb, wm = with_flow(base.w), with_flow(more.w)
    need_combined = not only or any(n in only for n in ("east_gate", "aerial", "more_aerial", "north_gate"))
    scenes = {}
    if need_combined:
        scenes["base"] = Scene(combined_world(wb), ground_y=1)
        scenes["more"] = Scene(combined_world(wm), ground_y=1)
    else:
        scenes["base"] = Scene(wb, ground_y=1)
        scenes["more"] = Scene(wm, ground_y=1)
    for key, views in (("base", VIEWS), ("more", VIEWS_MORE)):
        for name, (eye, target) in views.items():
            if only and name not in only:
                continue
            scenes[key].render(eye=eye, target=target, width=width, height=height, fov=fov,
                               path=os.path.join(out_dir, "freemarket_%s.png" % name))
            print("描きました:", name)
    for key, world in (("cut", wb), ("more_cut", wm)):
        name = key
        if only and name not in only:
            continue
        Scene(cut_world(world, 4), ground_y=1).render(eye=(14.5, 60.0, 16.0), target=(14.5, 0.0, 15.95), width=width,
                                                      height=width, fov=34,
                                                      path=os.path.join(out_dir, "freemarket_%s.png" % name))
        print("描きました:", name)
    for key, world in (("top", wb), ("more_top", wm)):
        name = key
        if only and name not in only:
            continue
        Scene(world, ground_y=1).render(eye=(14.5, 70.0, 16.0), target=(14.5, 0.0, 15.95), width=width, height=width,
                                        fov=30, path=os.path.join(out_dir, "freemarket_%s.png" % name))
        print("描きました:", name)


def save_overlay(path):
    ov = World(W, H, D)
    ov.blocks, ov.nbt, ov.entities = OVERLAY.blocks, OVERLAY.nbt, OVERLAY.entities
    owned = OVERLAY_OWNED
    return ov.save_structure(path, owned=lambda x, y, z: (x, y, z) in owned)


if __name__ == "__main__":
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    args = sys.argv[1:]
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
    path = os.path.join(out, "free_market.nbt")
    n = base.w.save_structure(path)
    print("書き出しました:", path, "ブロック", len(base.w.blocks), "種類", n, "もの", len(base.w.entities), "大きさ", (W, H, D))
    path = os.path.join(out, "free_market_expanded.nbt")
    n = save_overlay(path)
    print("書き出しました:", path, "ブロック", len(OVERLAY.blocks), "種類", n, "もの", len(OVERLAY.entities))
    if render_dir:
        render_views(render_dir, only=only)
    if ci:
        for shot, e, t in ci_views():
            print('"map_freemarket_%s"' % shot, "{%s}," % ", ".join(("%g" % round(c, 2)) for c in e + t))
