"""Phase 6 その2：4職業の武器（見習い・古代石）と、古代石シリーズ（軽装）の防具【仮素材】。

  python3 tools/artgen/make_gear_phase6.py <リポジトリ> [確認画像の出力先]

武器（JavaModel。手に持つと立体、持ち物の枠では16×16の絵）：
  見習いの鈴杖・見習いの弓・見習いの双短剣・見習いの籠手（ギルドが貸す練習用：明るい木・黄土色の革・赤い結び紐・真鍮の札）
  古代石の鈴杖・古代石の弓・古代石の双短剣・古代石の籠手（道標の石・苔鉄・生成りの毛の紐・光らない結晶）
防具（GeckoLib）：古代石シリーズ（軽装）＝ 額当て・胴着・脚衣・軽靴（アーチャー・ローグ・モンク）。
  1階の道標の石の小さな板を、苔で染めた毛と革の旅装に縫い付けた、動きやすい装備。

双短剣は1つのアイテムで、手には右の1本だけが見える（左手の1本は、持ち物の絵にだけ描く）【補完】。
同じ名前・同じ大きさで描き直したファイルを置けば、コードを変えずに差し替えられる。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import RAMPS, hexc, pick  # noqa: E402
from pixelart import Pixel  # noqa: E402
import weapons  # noqa: E402
from make_gear_apprentice import ApprenticePainter, cord_tail, rental_tag  # noqa: E402
from make_gear_ancient_stone import AncientPainter  # noqa: E402
from make_gear_ancient_armor import (ArmorPainter, H, B, RA, LA, RL, LL, RB, LB, new_armor, pair)  # noqa: E402

CREDIT_APPRENTICE = "AMEMARU: 見習い装備（Phase 6 その2。tools/artgen/make_gear_phase6.py で生成）"
CREDIT_ANCIENT = "AMEMARU: 古代石シリーズ（Phase 6 その2。tools/artgen/make_gear_phase6.py で生成）"


class BronzeMixin:
    def paint_bronze(self, c):
        tone = self.std(c, "bronze", base=3.9, noise=0.15, blob=0.25, up=1.5, down=1.0)
        return pick(RAMPS["bronze"], tone)


class Phase6ApprenticePainter(BronzeMixin, ApprenticePainter):
    pass


class Phase6AncientPainter(BronzeMixin, AncientPainter):
    pass


# ================================================================ 鈴杖（ヒーラー）

def build_apprentice_bell_staff():
    m = JavaModel("apprentice_bell_staff", 64, 64)
    m.box("butt", (7, -9, 7), (9, -8, 9), "darksteel")
    m.box("shaft", (7.25, -8, 7.25), (8.75, 17, 8.75), "ash", tags={"grain": "y"})
    m.box("grip", (7.1, 1, 7.1), (8.9, 6, 8.9), "leather", tags={"wrap": True})
    m.box("collar", (6.75, 17, 6.75), (9.25, 18, 9.25), "darksteel")
    # 先：曲がった腕から、真鍮の鈴を下げる（練習用の小さな鈴）
    m.box("crook_up", (7.25, 18, 7.25), (8.75, 21, 8.75), "ash", tags={"grain": "y"})
    m.box("crook_arm", (8.75, 19.75, 7.4), (12.5, 21, 8.6), "ash")
    m.box("hook", (11.6, 18.6, 7.75), (12.1, 19.75, 8.25), "darksteel")
    m.box("bell_crown", (11.1, 17.9, 7.25), (12.6, 18.6, 8.75), "bronze")
    m.box("bell_body", (10.6, 15.6, 6.75), (13.1, 17.9, 9.25), "bronze")
    m.box("bell_lip", (10.2, 15.0, 6.35), (13.5, 15.6, 9.65), "bronze")
    m.box("clapper", (11.6, 14.2, 7.75), (12.1, 15.0, 8.25), "darksteel")
    m.box("cord_band", (7.0, 20.0, 7.0), (9.0, 20.75, 9.0), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail", (7.4, 20.0, 8.0), 2.6, -22.5)
    rental_tag(m, "tag", (7.2, 19.9), w=1.5, h=2.0)
    return m


def build_ancient_bell_staff():
    m = JavaModel("ancient_stone_bell_staff", 64, 64)
    m.box("spike", (7.5, -8, 7.5), (8.5, -6, 8.5), "iron")
    m.box("core", (7.6, -6, 7.6), (8.4, 16, 8.4), "iron")
    for k, (y0, y1) in enumerate(((-6, -3.5), (-3, -0.5), (6.5, 9), (9.5, 12), (12.5, 15))):
        w = 1.1 if k % 2 == 0 else 1.0
        m.box(f"ring_{k}", (8 - w, y0, 8 - w), (8 + w, y1, 8 + w), "stone", tags={"grooves": [(y0 + y1) / 2]})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "wool", tags={"wrap": True})
    # 先：道標の小さなまぐさ（横の石）から、石の鈴を2つ下げる。まん中に結晶の欠片
    m.box("post", (7.25, 15, 7.25), (8.75, 19, 8.75), "stone", tags={"grooves": [17.0]})
    m.box("lintel", (4.5, 19, 7.0), (11.5, 20.5, 9.0), "stone", tags={"grooves": [19.75]})
    m.box("lintel_cap", (5.5, 20.5, 7.25), (10.5, 21.25, 8.75), "stone")
    m.box("shard", (7.4, 21.25, 7.4), (8.6, 23.25, 8.6), "crystal_dim")
    for side, x in (("l", 5.25), ("r", 10.75)):
        m.box(f"chain_{side}", (x - 0.25, 17.5, 7.75), (x + 0.25, 19, 8.25), "iron")
        m.box(f"bell_{side}", (x - 1.0, 15.75, 7.0), (x + 1.0, 17.5, 9.0), "stone")
        m.box(f"bell_lip_{side}", (x - 1.3, 15.25, 6.7), (x + 1.3, 15.75, 9.3), "stone")
    return m


# ================================================================ 弓（アーチャー）

def _bow_limbs(m, mat, edge_mat, prefix=""):
    """弓の腕：握りから上下へ、前（+x）へ反ってのびる。弦は後ろ（−x）。"""
    # 上の腕
    segs = ((5, 9, 8.0), (9, 13, 8.6), (13, 16.5, 9.4), (16.5, 19, 10.3))
    for k, (y0, y1, x) in enumerate(segs):
        m.box(f"{prefix}upper_{k}", (x - 0.6, y0, 7.4), (x + 0.6, y1, 8.6), mat, tags={"grain": "y"})
        m.box(f"{prefix}lower_{k}", (x - 0.6, -y1 + 5, 7.4), (x + 0.6, -y0 + 5, 8.6), mat, tags={"grain": "y"})
    m.box(f"{prefix}tip_top", (10.0, 19, 7.6), (11.2, 20.2, 8.4), edge_mat)
    m.box(f"{prefix}tip_bottom", (10.0, -15.2, 7.6), (11.2, -14, 8.4), edge_mat)


def build_apprentice_bow():
    m = JavaModel("apprentice_bow", 64, 64)
    m.box("grip", (7.0, 0, 7.2), (8.8, 5, 8.8), "leather", tags={"wrap": True})
    _bow_limbs(m, "ash", "darksteel")
    m.box("string", (6.2, -14.6, 7.9), (6.4, 19.6, 8.1), "wool")
    m.box("arrow_rest", (8.8, 4.2, 7.6), (9.4, 4.8, 8.4), "darksteel")
    m.box("cord_band", (6.8, 4.9, 7.0), (9.0, 5.6, 9.0), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail", (7.2, 4.9, 8.0), 2.4, -22.5)
    rental_tag(m, "tag", (7.0, 4.8), w=1.5, h=2.0)
    return m


def build_ancient_bow():
    m = JavaModel("ancient_stone_bow", 64, 64)
    m.box("grip", (7.0, 0, 7.2), (8.8, 5, 8.8), "wool", tags={"wrap": True})
    m.box("grip_plate", (8.8, 0.5, 7.0), (9.6, 4.5, 9.0), "stone", tags={"grooves": [2.5]})
    m.box("shard", (9.6, 2.0, 7.5), (10.2, 3.0, 8.5), "crystal_dim")
    _bow_limbs(m, "iron", "stone")
    m.box("string", (6.2, -14.6, 7.9), (6.4, 19.6, 8.1), "wool")
    for k, y in enumerate((9.0, -4.0)):
        m.box(f"band_{k}", (8.0, y, 7.2), (10.0, y + 1.2, 8.8), "stone")
    return m


# ================================================================ 双短剣（ローグ）

def build_apprentice_daggers():
    m = JavaModel("apprentice_daggers", 64, 64)
    m.box("pommel", (7.25, -2, 7.25), (8.75, -0.75, 8.75), "darksteel")
    m.box("grip", (7.4, -0.75, 7.4), (8.6, 4, 8.6), "leather", tags={"wrap": True})
    m.box("guard", (5.5, 4, 7.25), (10.5, 5, 8.75), "darksteel")
    m.box("blade", (7.0, 5, 7.6), (9.0, 12.5, 8.4), "steel", tags={"edge": True})
    m.box("blade_tip", (7.5, 12.5, 7.7), (8.5, 14, 8.3), "steel", tags={"edge": True})
    m.box("cord_knot", (7.3, -2.8, 7.3), (8.7, -2.0, 8.7), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail", (8.0, -2.8, 8.0), 2.0, 22.5)
    return m


def build_ancient_daggers():
    m = JavaModel("ancient_stone_daggers", 64, 64)
    m.box("pommel", (7.25, -2, 7.25), (8.75, -0.75, 8.75), "stone")
    m.box("grip", (7.4, -0.75, 7.4), (8.6, 4, 8.6), "wool", tags={"wrap": True})
    m.box("guard", (5.75, 4, 7.25), (10.25, 5, 8.75), "stone", tags={"grooves": [4.5]})
    m.box("core", (7.6, 5, 7.6), (8.4, 13.5, 8.4), "iron", tags={"edge_iron": True})
    m.box("slab_l", (6.9, 5, 7.65), (7.6, 12, 8.35), "stone", tags={"chisel": True})
    m.box("slab_r", (8.4, 5, 7.65), (9.1, 12, 8.35), "stone", tags={"chisel": True})
    m.box("tip", (7.7, 13.5, 7.7), (8.3, 14.5, 8.3), "iron")
    m.box("shard", (7.6, 4.1, 6.9), (8.4, 4.9, 7.25), "crystal_dim")
    return m


# ================================================================ 籠手（モンク）

def build_apprentice_gauntlets():
    m = JavaModel("apprentice_gauntlets", 64, 64)
    # 手（握り）を包む：手首の革の筒・甲の鉄板・指の節の板
    m.box("cuff", (5.25, -2, 5.25), (10.75, 2, 10.75), "leather", tags={"stitch": True})
    m.box("cuff_band", (5.0, 1.5, 5.0), (11.0, 2.25, 11.0), "darksteel")
    m.box("back_plate", (5.5, 2.25, 4.75), (10.5, 6.5, 6.0), "darksteel")
    m.box("palm", (5.5, 2.25, 6.0), (10.5, 6.5, 10.5), "leather")
    m.box("knuckles", (5.25, 6.5, 4.5), (10.75, 7.75, 10.75), "darksteel")
    for k, x in enumerate((5.75, 7.25, 8.75)):
        m.box(f"knuckle_{k}", (x, 7.75, 4.75), (x + 1.25, 8.25, 6.0), "steel")
    m.box("cord_band", (5.0, -1.0, 5.0), (11.0, -0.25, 11.0), "cord", tags={"twist": True})
    rental_tag(m, "tag", (10.6, -0.3), z=6.0, w=1.4, h=1.8)
    return m


def build_ancient_gauntlets():
    m = JavaModel("ancient_stone_gauntlets", 64, 64)
    m.box("cuff", (5.25, -2, 5.25), (10.75, 2, 10.75), "wool", tags={"wrap": True})
    m.box("cuff_ring", (5.0, 1.5, 5.0), (11.0, 2.5, 11.0), "iron")
    m.box("back_plate", (5.5, 2.5, 4.5), (10.5, 6.5, 6.0), "stone", tags={"grooves": [4.5]})
    m.box("palm", (5.5, 2.5, 6.0), (10.5, 6.5, 10.5), "leather")
    m.box("shard", (7.4, 3.9, 4.1), (8.6, 5.1, 4.5), "crystal_dim")
    m.box("knuckles", (5.25, 6.5, 4.25), (10.75, 8.0, 10.75), "stone")
    m.box("knuckle_band", (5.0, 6.75, 4.0), (11.0, 7.5, 6.0), "iron")
    return m


# ================================================================ 持ち物の絵（16×16）

APP = {"W": "#F0F1EE", "L": "#CFD2D3", "M": "#AEB2B6", "F": "#7E8288", "g": "#4A4C52", "h": "#8A8D94",
       "b": "#C08B56", "n": "#7A4E2C", "r": "#D9573A", "R": "#8E2A1C", "a": "#E3C796", "A": "#B08A5A",
       "q": "#7C5A36", "y": "#E2C385", "Y": "#AD8247", "s": "#F2EAD8"}
ANC = {"s": "#B7C3C8", "S": "#D8E1E3", "t": "#7C8A92", "T": "#606D75", "u": "#454F56", "i": "#434A47",
       "I": "#757E79", "k": "#232827", "w": "#E9DFC8", "v": "#BCAF96", "c": "#BFDDE0", "C": "#72A4AE",
       "L": "#654430", "l": "#3A261A"}
OUT_APP = "#1D1D21"
OUT_ANC = "#16191C"

ICON_ART = {
    "apprentice_bell_staff": (APP, OUT_APP, """
................
..........aaaa..
.........a...a..
.........a..yy..
.........a.yYYy.
.........a.yYYy.
........aq.YYYY.
.......aq...rg..
......aq....R...
.....nb.........
....nb..........
...aq...........
..aq............
.aq.............
.g..............
................
"""),
    "ancient_stone_bell_staff": (ANC, OUT_ANC, """
................
.........SssT...
.........tCct...
.......sSsssstT.
.......t.ii..t..
.......S.ii..S..
......ss.it..tt.
.....iI...i.....
....wv..........
...vw...........
...Ii...........
..sT............
.iI.............
.k..............
................
................
"""),
    "apprentice_bow": (APP, OUT_APP, """
................
.........aq.....
..........aq....
...........ab...
....W.......ab..
.....W......ab..
......W.....nb..
.......W....nb..
........W...nb..
.........W.rab..
..........WRab..
...........ab...
..........aq....
.........aq.....
................
................
"""),
    "ancient_stone_bow": (ANC, OUT_ANC, """
................
.........St.....
..........Ii....
...........Ii...
....w.......Ii..
.....w......sT..
......w.....Ss..
.......w....vw..
........w...wv..
.........w..sT..
..........w.Ii..
...........Ii...
..........Ii....
.........St.....
................
................
"""),
    "apprentice_daggers": (APP, OUT_APP, """
................
................
..W..........W..
...L........L...
....M......M....
.....L....L.....
......M..M......
.......gg.......
......g..g......
.....g.nn.g.....
......n..n......
.....b....b.....
....n......n....
...r........r...
..R..........R..
................
"""),
    "ancient_stone_daggers": (ANC, OUT_ANC, """
................
................
..S..........S..
...s........s...
....I......I....
.....s....s.....
......I..I......
.......tt.......
......t..t......
.....s.vv.s.....
......w..w......
.....v....v.....
....w......w....
...T........T...
................
................
"""),
    "apprentice_gauntlets": (APP, OUT_APP, """
................
................
....LMLM........
...LMLMLg.......
...ggggggg......
...gFFFFFg......
...gFhhhFg......
...gggggggg.....
....bbbbbbn.....
....bnnnnbn.....
....rrrrrrR.....
....bbbbbbn.....
....bnnnnbn.....
................
................
................
"""),
    "ancient_stone_gauntlets": (ANC, OUT_ANC, """
................
................
....SsSs........
...SsSsSt.......
...ssssssT......
...sTTCTTT......
...sTccTTT......
...IiiiiiiI.....
....LLLLLLl.....
....LllllLl.....
....wvwvwvw.....
....LLLLLLl.....
....LllllLl.....
................
................
................
"""),
}


def icon(name):
    palette, outline, text = ICON_ART[name]
    rows = [r for r in text.strip("\n").splitlines()]
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError(f"{name}：16×16になっていない（{[len(r) for r in rows]}）")
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            p.put(x, y, hexc(palette[ch]))
    p.outline(outline)
    return p.icon()


# ================================================================ 持ち方

def display(kind):
    """手に持ったときの向きと大きさ（見習いの杖の持ち方をもとにした）。"""
    if kind == "bell_staff":
        rot, tr, fs, ts, ty = [0, -80, 5], [1.13, 0.5, 1.13], 0.45, 0.75, 2.25
    elif kind == "bow":
        # 弓：握りを手に。腕を前へのばすと（射る構え）、弓が縦に立ち、反りが前・弦が自分の側に来る
        rot, tr, fs, ts, ty = [0, -90, 10], [1.5, 2.0, 1.13], 0.5, 0.8, 1.5
    elif kind == "daggers":
        rot, tr, fs, ts, ty = [0, -75, 5], [1.13, 2.5, 1.13], 0.55, 0.7, 2.5
    else:  # gauntlets：握った手を包む
        rot, tr, fs, ts, ty = [0, -90, 0], [1.13, 3.0, 0.5], 0.6, 0.85, 1.0
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "firstperson_righthand": {"rotation": rot, "translation": tr, "scale": [fs] * 3},
        "firstperson_lefthand": {"rotation": [rot[0], -rot[1], -rot[2]], "translation": tr, "scale": [fs] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }


WEAPONS = (
    (build_apprentice_bell_staff, "bell_staff", Phase6ApprenticePainter, CREDIT_APPRENTICE),
    (build_apprentice_bow, "bow", Phase6ApprenticePainter, CREDIT_APPRENTICE),
    (build_apprentice_daggers, "daggers", Phase6ApprenticePainter, CREDIT_APPRENTICE),
    (build_apprentice_gauntlets, "gauntlets", Phase6ApprenticePainter, CREDIT_APPRENTICE),
    (build_ancient_bell_staff, "bell_staff", Phase6AncientPainter, CREDIT_ANCIENT),
    (build_ancient_bow, "bow", Phase6AncientPainter, CREDIT_ANCIENT),
    (build_ancient_daggers, "daggers", Phase6AncientPainter, CREDIT_ANCIENT),
    (build_ancient_gauntlets, "gauntlets", Phase6AncientPainter, CREDIT_ANCIENT),
)


# ================================================================ 古代石シリーズ（軽装）

def build_light():
    g = new_armor("ancient_stone_light")
    # 額当て：革の帯に、道標の石の小さな板（まん中に光らない結晶）。後ろで結んだ毛の紐の垂れ
    g.cube(H, "band", (-4, 29.5, -4), (8, 2, 8), "leather_dark", inflate=0.6)
    g.cube(H, "plate", (-1.5, 29, -5.1), (3, 3, 1), "stone", tags={"grooves": [30.5]})
    g.cube(H, "shard", (-0.5, 30, -5.6), (1, 1, 1), "crystal_dim")
    pair(g, H, H, "knot_tail", (-1.25, 26.5, 4.4), (1, 3, 1), "wool")
    # 胴着：苔の毛の上着に、革の胸当て・石のボタン・帯と小物入れ。肩に石の小さな肩当て
    g.cube(B, "shirt", (-4, 12, -2), (8, 12, 4), "wool_moss", inflate=0.45)
    g.cube(B, "vest", (-4, 13, -2), (8, 10, 4), "leather", inflate=0.75)
    g.cube(B, "collar", (-3.5, 22, -3), (7, 2, 6), "wool_moss", tags={"trim_bottom": True})
    for k, y in enumerate((20.5, 18.0, 15.5)):
        g.cube(B, f"button_{k}", (-0.5, y, -3.5), (1, 1, 1), "stone")
    g.cube(B, "belt", (-5, 11.5, -3), (10, 2, 6), "leather_dark")
    g.cube(B, "buckle", (-1, 11.5, -3.6), (2, 2, 1), "iron", tags={"buckle": True})
    g.cube(B, "pouch", (2.25, 9.5, -3.6), (2, 3, 1), "leather_dark")
    pair(g, RA, LA, "sleeve", (-8, 15, -2), (4, 9, 4), "wool_moss", inflate=0.4)
    pair(g, RA, LA, "pauldron", (-8.5, 21, -2.5), (5, 2, 5), "stone", inflate=0.2, tags={"grooves": [22.0]})
    pair(g, RA, LA, "bracer", (-8.5, 12, -2.5), (5, 3, 5), "leather_dark")
    # 脚衣：毛の脚衣と、ひざの巻き布・すねの革の当て
    pair(g, RL, LL, "breeches", (-3.9, 3, -2), (4, 9, 4), "wool_moss", inflate=0.45)
    pair(g, RL, LL, "knee_wrap", (-3.9, 5.5, -2), (4, 1, 4), "wool", inflate=0.65)
    pair(g, RL, LL, "shin_guard", (-3.4, 2.5, -2.75), (3, 3, 1), "leather")
    # 軽靴：やわらかい革の靴と、足首の巻き布
    pair(g, RB, LB, "shoe", (-3.9, 0, -2), (4, 3, 4), "leather", inflate=0.5)
    pair(g, RB, LB, "shoe_toe", (-3.4, -0.5, -3.4), (3, 1, 1), "leather")
    pair(g, RB, LB, "ankle_wrap", (-3.9, 2.5, -2), (4, 1, 4), "wool", inflate=0.65)
    return g


LIGHT_ICON_PALETTE = {
    "A": "#D8E1E3", "a": "#B7C3C8", "b": "#98A6AD", "c": "#7C8A92", "d": "#606D75",
    "C": "#BFDDE0", "q": "#72A4AE", "W": "#E9DFC8", "w": "#BCAF96",
    "L": "#654430", "l": "#3A261A", "G": "#90A67E", "g": "#738A64", "h": "#5B7150", "H": "#475A3F",
    "B": "#C79459", "r": "#A97644", "R": "#8A5C34", "D": "#6A4426", "i": "#757E79",
}
LIGHT_OUTLINE = "#121810"
LIGHT_ICON_ART = {
    "ancient_stone_circlet": """
................
................
................
................
....LLLLLLLL....
...LllaAbllLL...
..Ll..aCqb..lL..
..Ll..bqqc..lL..
..L...abcd...L..
..L...........L.
...L.........L..
....L.......WW..
.....LLLLLLLW...
............W...
................
................
""",
    "ancient_stone_jerkin": """
................
...ab......ba...
..abcGGWWGGcba..
..bcRBBhhBBRcb..
..hgRBBaABBRgh..
..hgRBBhhBBRgh..
..hgRBBaABBRgh..
..hgRBBhhBBRgh..
..hgRBBaABBRgh..
..LLlllLLlllLL..
..hgRBBiiBBRgh..
...gRBBhhBlLgh..
...ghhhhhhlLh...
................
................
................
""",
    "ancient_stone_breeches": """
................
....LllLLllL....
....hgGGGGgh....
....hgGGGGgh....
....hgGhhGgh....
...hhgGhhGghh...
...hhgG..Gghh...
...WWwW..WwWW...
...hhgG..Gghh...
...RBBR..RBBR...
...RBBR..RBBR...
...hhgg..gghh...
................
................
................
................
""",
    "ancient_stone_shoes": """
................
................
................
................
....WwWw..WwWw..
....RBBR..RBBR..
....RrrD..RrrD..
...rRrrR.rRrrR..
..BrRrrDBrRrrD..
.BBrrrrDBrrrrD..
.RRRRDDDRRRRDDD.
................
................
................
................
................
""",
}


def light_icon(name):
    rows = [r for r in LIGHT_ICON_ART[name].strip("\n").splitlines()]
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError(f"{name}：16×16になっていない（{[len(r) for r in rows]}）")
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, hexc(LIGHT_ICON_PALETTE[ch]))
    p.outline(LIGHT_OUTLINE)
    return p.icon()


def write_item_model(repo, name):
    path = os.path.join(repo, "src/main/resources/assets/amemaru/models/item", name + ".json")
    weapons.write_json(path, {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}})


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_phase6"
    os.makedirs(out, exist_ok=True)
    for build, kind, painter_class, credit in WEAPONS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(painter_class(m), frames=1)
        weapons.export_weapon(repo, m.name, m, frames, icon(m.name), kind, credit, display=display(kind))
        icon(m.name).big(8).save(os.path.join(out, m.name + "_icon.png"))
        print("wrote", m.name, "部品", len(m.elements))
    g = build_light()
    g.pack()
    frames = g.paint(ArmorPainter(g), frames=1)
    used = max(c.uv[1] + c.block()[1] for c in g.elements)
    print(g.name, "部品", len(g.elements), "絵の使った高さ", used, "/", g.tex_h)
    g.save(repo, "armor", frames)
    base = os.path.join(repo, "src/main/resources/assets/amemaru/textures/item")
    for name in LIGHT_ICON_ART:
        light_icon(name).save(os.path.join(base, name + ".png"))
        light_icon(name).big(8).save(os.path.join(out, name + "_icon.png"))
        write_item_model(repo, name)
    print("wrote light armor icons and models")


if __name__ == "__main__":
    main()
