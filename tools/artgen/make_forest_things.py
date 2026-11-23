"""2階「忘れられた森」の仕掛けのブロックと品の絵・モデルを作る（Phase 8 その1）。

  python3 tools/artgen/make_forest_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only antidote,knot_lantern,warden_light,elder_branch_staff_icon

ブロック（JavaModel の立体のモデル。絵は models/block と同じ名前の textures/block/<名前>.png）：
  forest_bell      古代の風鈴：白い古代石の台に、青銅の鳥居のような枠。まん中に鐘、その下に光る結晶の舌
                   （Phase 0-A 9章「風鈴のような古代の装置」）。鳴らすと、正しい道の灯台からこだまが返る
  knot_post        結び目の灯台：彫った木の柱と、灯をさげる鉤。柱に赤い結び紐
  knot_post_lit    （灯った）鉤に結び目の灯がさがり、淡い青緑に光る
  waypoint         中継の灯：白い古代石の灯籠。火袋の中で、黄緑の灯が燃える
  travel_stone     道しるべの石：苔むした立石に、青緑に光る矢じりの文字
品（16×16 の形を描き、enhance_icons.py で 32×32 にする）：
  antidote               解毒薬：緑の薬の丸瓶に、葉の札
  knot_lantern           結び目の灯：小さな青銅の灯に、淡い青緑の光。上に赤い結び紐
  warden_light           森番の灯：苔の生えた青銅のランタンに、黄緑の光（森番の古樹の枝の灯と同じ）
  elder_branch_staff_icon 古樹の枝杖の持ち物の絵（make_gear_rare_f2.py が使う）
状態のアイコン（18×18）：
  mob_effect/antidote    毒よけ：若葉と、白い十字のしずく
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from make_forest_models import FOREST, ForestPainter  # noqa: E402
from materials import RAMPS, hsh, pick, ramp  # noqa: E402

FOREST["white_stone"] = ramp("#6F6B60", "#8D887B", "#A9A496", "#C2BDAF", "#D8D4C8", "#E8E5DC", "#F5F3EC")
FOREST["cord"] = RAMPS["cord"]
CREDIT = "AMEMARU: 2階の仕掛け（tools/artgen/make_forest_things.py で生成）"


class ThingPainter(ForestPainter):
    def paint_white_stone(self, c):
        tone = self.std(c, "white_stone", base=3.6, noise=0.25, blob=0.4, up=1.1, down=1.0, specks=0.03)
        tone += self.grooves(c)
        if c.n[1] > 0.6 and self.blob(c, 1.5, 200 + self.eid(c)) < c.el.tags.get("moss", 0.0):
            return pick(FOREST["moss"], 3.0 + (self.tex(c, 201) - 0.5) * 1.4)
        return pick(FOREST["white_stone"], tone)

    def paint_cord(self, c):
        tone = 3.3 + (self.tex(c, 211) - 0.5) * 0.5 + (0.6 if (c.i + c.j) % 2 else -0.4)
        return pick(RAMPS["cord"], tone)

    def paint_glyph(self, c):
        # 矢じりの文字（板）：板の中の位置で、上向きの矢じりと横線を描く
        u = (c.i + 0.5) / c.W - 0.5
        v = (c.j + 0.5) / c.H
        arrow = abs(abs(u) - (v - 0.05) * 0.5) < 0.09 and v < 0.55
        stem = abs(u) < 0.07 and 0.2 < v < 0.95
        bar = abs(v - 0.75) < 0.05 and abs(u) < 0.3
        if not (arrow or stem or bar):
            return None
        return pick(FOREST["glow_knot"], 4.4 + (self.tex(c, 221) - 0.5) * 0.6)


def box(m, name, frm, to, mat, **kw):
    return m.box(name, frm, to, mat, **kw)


# ---------------------------------------------------------------- ブロック

def build_forest_bell():
    m = JavaModel("forest_bell", 64, 64)
    box(m, "base", (3, 0, 5), (13, 2, 11), "white_stone", tags={"moss": 0.3})
    box(m, "base_step", (4, 2, 6), (12, 2.5, 10), "white_stone")
    for k, x in enumerate((3.5, 11)):
        box(m, f"post_{k}", (x, 2.5, 7), (x + 1.5, 14, 9), "bronze")
    box(m, "beam", (2.5, 14, 6.5), (13.5, 15.5, 9.5), "bronze", tags={"rivets": [(4.25, 14.75), (11.75, 14.75)]})
    box(m, "beam_cap", (3, 15.5, 7), (13, 16, 9), "pipe", tags={"axis": "x"})
    box(m, "cord", (7.75, 12, 7.75), (8.25, 14, 8.25), "cord")
    box(m, "bell_top", (6.5, 11, 6.5), (9.5, 12, 9.5), "bronze")
    box(m, "bell", (6, 8.5, 6), (10, 11, 10), "bronze")
    box(m, "bell_lip", (5.5, 8, 5.5), (10.5, 8.5, 10.5), "bronze")
    box(m, "tongue", (7.5, 5, 7.5), (8.5, 8, 8.5), "glow_knot", glow=True)
    # 枠の左右にさがる小さな結晶の管（風鈴）
    for k, x in enumerate((4.5, 11)):
        box(m, f"chime_{k}", (x, 10, 7.75), (x + 0.5, 14, 8.25), "glow_knot", glow=True)
    return m


def build_knot_post(lit):
    m = JavaModel("knot_post_lit" if lit else "knot_post", 64, 64)
    box(m, "foot", (5.5, 0, 5.5), (10.5, 1.5, 10.5), "white_stone", tags={"moss": 0.4})
    box(m, "post", (7, 1.5, 7), (9, 14, 9), "carved", tags={"grain": "y"})
    box(m, "post_cap", (6.5, 14, 6.5), (9.5, 15, 9.5), "carved")
    box(m, "arm", (9, 13, 7.5), (13, 14, 8.5), "bronze")
    box(m, "hook", (12, 11.5, 7.75), (12.5, 13, 8.25), "bronze")
    # 柱に巻いた赤い結び紐（結び目の印）
    box(m, "tie", (6.75, 9, 6.75), (9.25, 10, 9.25), "cord")
    box(m, "tie_end", (8.75, 7.5, 6.6), (9.5, 9, 7.1), "cord")
    if lit:
        box(m, "lantern_cord", (12, 10, 7.75), (12.5, 11.5, 8.25), "cord")
        box(m, "lantern_cap", (10.5, 9.5, 6.5), (14, 10, 9.5), "bronze")
        box(m, "lantern", (11, 6.5, 7), (13.5, 9.5, 9), "glow_knot", glow=True)
        box(m, "lantern_base", (10.5, 6, 6.5), (14, 6.5, 9.5), "bronze")
    return m


def build_waypoint():
    m = JavaModel("waypoint", 64, 64)
    box(m, "base", (3, 0, 3), (13, 2, 13), "white_stone", tags={"moss": 0.35})
    box(m, "pillar", (5.5, 2, 5.5), (10.5, 8, 10.5), "white_stone", tags={"grooves": [5.5]})
    box(m, "shelf", (4, 8, 4), (12, 9, 12), "white_stone")
    for k, (x, z) in enumerate(((4, 4), (10.5, 4), (4, 10.5), (10.5, 10.5))):
        box(m, f"corner_{k}", (x, 9, z), (x + 1.5, 13, z + 1.5), "white_stone")
    box(m, "flame", (6, 9, 6), (10, 12.5, 10), "glow_eye", glow=True)
    box(m, "flame_tip", (7, 12.5, 7), (9, 13.5, 9), "glow_eye", glow=True)
    box(m, "roof", (3, 13, 3), (13, 14.5, 13), "white_stone", tags={"moss": 0.5})
    box(m, "roof_top", (5.5, 14.5, 5.5), (10.5, 15.5, 10.5), "white_stone", tags={"moss": 0.5})
    box(m, "jewel", (7.25, 15.5, 7.25), (8.75, 16, 8.75), "bronze")
    return m


def build_travel_stone():
    m = JavaModel("travel_stone", 64, 64)
    box(m, "base", (3, 0, 4), (13, 1, 12), "white_stone", tags={"moss": 0.5})
    box(m, "stone", (4.5, 1, 5.5), (11.5, 11, 10.5), "white_stone", tags={"moss": 0.4})
    box(m, "stone_top", (5.5, 11, 6), (10.5, 13, 10), "white_stone", tags={"moss": 0.8})
    box(m, "stone_tip", (6.5, 13, 6.5), (9.5, 14, 9.5), "white_stone", tags={"moss": 0.8})
    m.plane("glyph_n", (5.5, 2.5, 5.45), (10.5, 9.5, 5.45), "glyph", facing="north", glow=True)
    m.plane("glyph_s", (5.5, 2.5, 10.55), (10.5, 9.5, 10.55), "glyph", facing="south", glow=True)
    box(m, "plate", (6.5, 10, 5.3), (9.5, 10.8, 5.5), "bronze")
    return m


BLOCKS = [build_forest_bell, lambda: build_knot_post(False), lambda: build_knot_post(True), build_waypoint,
          build_travel_stone]


# ---------------------------------------------------------------- 品（16×16）

ANTIDOTE = [
    "................",
    "......oooo......",
    ".....occCCo.....",
    "......oooo......",
    "......oYYo......",
    ".....ogGGgo.....",
    "...ooHLLLLDoo...",
    "..oHLLLLLLLLDo..",
    ".oHLLmLLLLLLLDo.",
    ".oHLLLffFfLLLDo.",
    ".oLLLLfFFFfLLDo.",
    ".oLLLLLfFfLLDDo.",
    ".oDLLLLLfLLLDDo.",
    "..oDDLLLLLLDDo..",
    "...ooDDDDDDoo...",
    ".....oooooo.....",
]
ANTIDOTE_PAL = {"o": "#2A2520", "c": "#B07A46", "C": "#8A5A2E", "g": "#BFD8E0", "G": "#E8F4F8", "Y": "#C8D2D8",
                "L": "#4FA83C", "H": "#9EE07A", "D": "#2B6A26", "m": "#D8FFC0", "f": "#F2EEE4", "F": "#7FC25A"}

KNOT_LANTERN = [
    "................",
    ".......rr.......",
    "......r..r......",
    ".......rr.......",
    "......rbbr......",
    "......oBBo......",
    ".....oBBBBo.....",
    "....oBgggGBo....",
    "....oBgGWgBo....",
    "....oBgWWgBo....",
    "....oBgGWgBo....",
    "....oBgggGBo....",
    ".....oBBBBo.....",
    "......oDDo......",
    ".......rr.......",
    "........r.......",
]
KNOT_LANTERN_PAL = {"o": "#2E2A20", "B": "#A8823A", "D": "#6E5424", "g": "#58C3A3", "G": "#9CF0D2", "W": "#F0FFF8",
                    "r": "#C8402C", "b": "#E2714F"}

WARDEN_LIGHT = [
    "................",
    "......oooo......",
    ".....o....o.....",
    "......oooo......",
    "....mmMBBMmm....",
    "....oBBBBBBo....",
    "....oBoyyoBo....",
    "....oBy11yBo....",
    "....oy1WW1yo....",
    "....oy1WW1yo....",
    "....oBy11yBo....",
    "....oBoyyoBo....",
    "....oBBBBBBo....",
    ".....oDDDDo.....",
    "......oooo......",
    "................",
]
WARDEN_LIGHT_PAL = {"o": "#2A2418", "B": "#8E7A3E", "D": "#5E4E26", "y": "#B6C63B", "1": "#D8E461", "W": "#FAFDCF",
                    "m": "#47672D", "M": "#6F9843"}

# 古樹の枝杖（左下が石突き、右上が先。ねじれた枝の柄に、先は2本の小枝が光る節を抱える。若葉が2枚）
STAFF = [
    "........l..lL...",
    "......l.LtGkt.L.",
    ".......tGKKGkl..",
    "......tGKWWKGt..",
    ".....lkGKWKGkl..",
    "......tkGGGkt...",
    ".....ppbtkkt....",
    "....pbbp........",
    "...rbRr.........",
    "...bRr..........",
    "..rbRr..........",
    "..bRr...........",
    ".rbRr...........",
    ".bRr............",
    "bRr.............",
    "Rr..............",
]
STAFF_PAL = {"b": "#705F48", "R": "#463A2C", "r": "#887659", "k": "#5A4B39", "t": "#887659",
             "G": "#58C3A3", "K": "#B1F1DA", "W": "#FFFFFF", "l": "#4C8444", "L": "#79B150",
             "p": "#557263", "B": "#A07A42"}


def rgba(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def paint_rows(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(pal[ch]))
    return img


ICONS = {
    "antidote": (ANTIDOTE, ANTIDOTE_PAL),
    "knot_lantern": (KNOT_LANTERN, KNOT_LANTERN_PAL),
    "warden_light": (WARDEN_LIGHT, WARDEN_LIGHT_PAL),
    "elder_branch_staff_icon": (STAFF, STAFF_PAL),
}


def effect_antidote():
    from make_phase2_art import Canvas, hexc
    c = Canvas(18, 18)
    leaf = hexc("7FD35A")
    leaf_d = hexc("3E8A2E")
    c.poly([(3, 14), (6, 6), (12, 3), (15, 4), (13, 10), (7, 15)], leaf)
    c.line([(4, 14), (13, 5)], leaf_d, 1)
    for (x, y) in ((7, 11), (9, 9), (11, 7)):
        c.px(x - 1, y - 1, leaf_d)
    white = hexc("F4FFF0")
    c.rect(12, 11, 13, 16, white)
    c.rect(10, 13, 15, 14, white)
    c.outline_alpha()
    return c.img


# ---------------------------------------------------------------- 書き出し

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    built = []
    for build in BLOCKS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        tex = m.paint(ThingPainter(m), frames=1)[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
        m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                    extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
        built.append((m, tex))
    write_json(os.path.join(base, "blockstates/forest_bell.json"), {"variants": {"": {"model": "amemaru:block/forest_bell"}}})
    write_json(os.path.join(base, "blockstates/knot_post.json"), {"variants": {
        "lit=false": {"model": "amemaru:block/knot_post"}, "lit=true": {"model": "amemaru:block/knot_post_lit"}}})
    write_json(os.path.join(base, "blockstates/waypoint.json"), {"variants": {"": {"model": "amemaru:block/waypoint"}}})
    write_json(os.path.join(base, "blockstates/travel_stone.json"), {"variants": {"": {"model": "amemaru:block/travel_stone"}}})
    for name in ("forest_bell", "knot_post", "waypoint", "travel_stone"):
        write_json(os.path.join(base, "models/item", name + ".json"), {"parent": "amemaru:block/" + name})
    for name, (rows, pal) in ICONS.items():
        paint_rows(rows, pal).save(os.path.join(base, "textures/item", name + ".png"))
        if not name.endswith("_icon"):
            write_json(os.path.join(base, "models/item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}})
    effect_antidote().save(os.path.join(base, "textures/mob_effect/antidote.png"))
    if out:
        check(built, out)
    print("ok")


def check(built, out):
    from PIL import ImageDraw
    from render import render_many
    from make_forest_models import sheet_font
    os.makedirs(out, exist_ok=True)
    tiles = []
    for m, tex in built:
        for yaw in (150, -30):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=22, scale=14, size=(280, 300), center=(8, 8, 8)))
        tiles.append(render_many([(m, tex)], yaw=150, pitch=22, scale=14, size=(280, 300), center=(8, 8, 8), darkness=0.2))
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    s = Image.new("RGBA", (280 * cols, 300 * rows + 24), (52, 60, 52, 255))
    for k, t in enumerate(tiles):
        s.alpha_composite(t, (280 * (k % cols), 24 + 300 * (k // cols)))
    ImageDraw.Draw(s).text((6, 2), "風鈴・灯台・灯台（灯った）・中継の灯・道しるべの石（斜め前・斜め後ろ・暗い所）", fill=(235, 235, 220),
                           font=sheet_font(15))
    s.save(os.path.join(out, "forest_things_check.png"))


if __name__ == "__main__":
    main()
