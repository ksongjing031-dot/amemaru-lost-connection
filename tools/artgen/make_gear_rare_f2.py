"""2階のレア装備：古樹の枝杖（2階のボス「森番の古樹オールドウォード」のレア。杖：魔法が当たると少し回復。6%）。

  python3 tools/artgen/make_gear_rare_f2.py <リポジトリ> [確認画像の出力先]

Phase 0-A 20章の表「古樹の枝杖（杖：魔法が当たると少し回復）」。仕様書：docs/gear/rare_f2.md
  - 森番の古樹の枝を、そのまま杖にしたもの。ねじれた枝の柄に、古代の管の輪。柄の表を、淡い青緑に光る細い筋が走る
    （芯を通る古代の管の名残）。握りにはつるを巻く
  - 先は2本の小枝が、光る節（ボスの幹の節と同じ淡い青緑）を抱える。小枝の先に若葉
  - レアの決まり：光る部分がある。ふだんは動かない。攻撃・スキルのとき（use。サーバーから流す）だけ、節がふくらみ、
    小枝が少し開く（蜂晶の杖と同じ考え方）
  - 持ち物の枠は2Dのドット絵（make_forest_things.py の elder_branch_staff_icon を enhance_icons.py で 32×32 にしたもの）。
    手に持つときだけGeckoLibの3D（蜂晶の杖と同じ持ち方）
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from make_forest_models import FOREST, ForestPainter, posed, sheet_font  # noqa: E402
from materials import pick  # noqa: E402

NAME = "elder_branch_staff"
CREDIT = "AMEMARU: 2階のレア装備（tools/artgen/make_gear_rare_f2.py で生成。docs/gear/rare_f2.md）"


class StaffPainter(ForestPainter):
    def paint_vine_wrap(self, c):
        tone = 2.6 + (self.tex(c, 301) - 0.5) * 0.8 + self.wrap(c) * 1.2
        return pick(FOREST["leaf"], tone)


def build():
    g = GeoModel(NAME, 64, 64)
    g.bone("staff", (0, 0, 0))
    g.bone("head", (0, 11, 0), parent="staff")
    g.bone("twig_left", (1, 11, 0), parent="head")
    g.bone("twig_right", (-1, 11, 0), parent="head")
    g.bone("knot", (0, 15.5, 0), parent="head")
    # 石突き：根のこぶと、青銅の口金
    g.cube("staff", "butt", (-1.5, -17.5, -1.5), (3, 2, 3), "bark")
    g.cube("staff", "ferrule", (-1, -15.5, -1), (2, 1, 2), "bronze", inflate=0.2)
    # 柄：ねじれた枝（段ごとに少しずつずらす）。握りにつるを巻く
    g.cube("staff", "shaft_0", (-1, -15, -1), (2, 7, 2), "bark")
    g.cube("staff", "shaft_1", (-0.6, -8, -1.1), (2, 7, 2), "bark")
    g.cube("staff", "grip", (-0.8, -7.5, -1.3), (2, 5, 2), "vine_wrap", inflate=0.25, tags={"wrap": True})
    g.cube("staff", "shaft_2", (-1, -1, -0.8), (2, 7, 2), "bark")
    g.cube("staff", "shaft_3", (-0.7, 6, -1), (2, 5, 2), "bark")
    g.cube("staff", "knob", (0.9, 1, -0.6), (1, 1, 1), "bark")
    # 古代の管の輪と、柄の表を走る光る筋（芯を通る管の名残）
    g.cube("staff", "pipe_ring", (-1.5, 3, -1.5), (3, 1, 3), "pipe", tags={"axis": "y"})
    g.cube("staff", "vein", (0.05, 6.5, -1.25), (1, 4, 0), "glow_knot", glow=True)
    # 先：2本の小枝が光る節を抱える。先に若葉
    g.cube("twig_left", "twig_l", (0.5, 11, -0.5), (1, 6, 1), "bark", rot=(0, 0, -20), pivot=(1, 11, 0))
    g.cube("twig_left", "twig_l_tip", (2, 16.2, -0.5), (1, 3, 1), "bark", rot=(0, 0, 25), pivot=(2.5, 16.2, 0))
    g.cube("twig_left", "leaf_l", (2.2, 19, -1), (3, 1, 2), "fox_leaf", rot=(0, 0, -25), pivot=(2.5, 19, 0))
    g.cube("twig_right", "twig_r", (-1.5, 11, -0.5), (1, 6, 1), "bark", rot=(0, 0, 20), pivot=(-1, 11, 0))
    g.cube("twig_right", "twig_r_tip", (-3, 16.2, -0.5), (1, 3, 1), "bark", rot=(0, 0, -25), pivot=(-2.5, 16.2, 0))
    g.cube("twig_right", "leaf_r", (-5.2, 18.6, -1), (3, 1, 2), "fox_leaf", rot=(0, 0, 25), pivot=(-2.5, 19, 0))
    g.cube("knot", "knot_core", (-2, 13.5, -2), (4, 4, 4), "glow_knot", glow=True)
    g.cube("knot", "knot_band", (-2.5, 15, -2.5), (5, 1, 5), "bark")
    return g


ANIMATIONS = {
    "animation.%s.idle" % NAME: {"loop": True, "animation_length": 1.0, "bones": {}},
    "animation.%s.use" % NAME: {
        "animation_length": 0.7,
        "bones": {
            "knot": {"scale": {"0.0": [1, 1, 1], "0.12": [1.45, 1.45, 1.45], "0.45": [1.15, 1.15, 1.15], "0.7": [1, 1, 1]}},
            "twig_left": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, 0, -16], "0.5": [0, 0, -16], "0.7": [0, 0, 0]}},
            "twig_right": {"rotation": {"0.0": [0, 0, 0], "0.12": [0, 0, 16], "0.5": [0, 0, 16], "0.7": [0, 0, 0]}},
        },
    },
}


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def export(repo, g, frames):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    g.save(repo, "item", frames, animations=ANIMATIONS)
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/%s_icon" % NAME}}
    write_json(os.path.join(base, "models/item/%s.json" % NAME), {
        "loader": "forge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": "amemaru:item/%s_3d" % NAME},
        "perspectives": {"gui": flat, "fixed": flat, "ground": flat},
    })
    # 持ち方は蜂晶の杖と同じ（同じ杖の持ち方。先が小さいので一人称は少し大きく）
    with open(os.path.join(base, "models/item/bee_crystal_staff_3d.json"), encoding="utf-8") as fp:
        display = json.load(fp)["display"]
    for hand in ("firstperson_righthand", "firstperson_lefthand"):
        display[hand] = dict(display[hand], scale=[0.46, 0.46, 0.46])
    write_json(os.path.join(base, "models/item/%s_3d.json" % NAME), {
        "credit": CREDIT,
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {"particle": "amemaru:item/%s_icon" % NAME},
        "display": display,
    })


def sheet(repo, g, tex, out_path):
    from render import render_many
    tiles = []
    for yaw, pitch in ((180, 0), (90, 0), (150, 16), (-30, 16)):
        tiles.append(render_many([(g, tex)], yaw=yaw, pitch=pitch, scale=11, size=(220, 440), center=(0, 1, 0)))
    opened = posed(g, {"twig_left": ((0, 0, -16),), "twig_right": ((0, 0, 16),), "knot": ((0, 0, 0), 1.45)})
    tiles.append(render_many([(opened, tex)], yaw=160, pitch=10, scale=11, size=(220, 440), center=(0, 1, 0)))
    tiles.append(render_many([(g, tex)], yaw=150, pitch=16, scale=11, size=(220, 440), center=(0, 1, 0), darkness=0.2))
    s = Image.new("RGBA", (220 * len(tiles) + 200, 470), (30, 34, 41, 255))
    for k, t in enumerate(tiles):
        s.alpha_composite(t, (220 * k, 30))
    icon = Image.open(os.path.join(repo, "src/main/resources/assets/amemaru/textures/item/%s_icon.png" % NAME)).convert("RGBA")
    big = icon.resize((icon.size[0] * 5, icon.size[1] * 5), Image.NEAREST)
    s.alpha_composite(big, (220 * len(tiles) + 20, 140))
    d = ImageDraw.Draw(s)
    d.text((8, 4), "古樹の枝杖（正面・横・斜め前・斜め後ろ・use の途中・暗い所・持ち物の絵）", fill=(222, 228, 235), font=sheet_font(18))
    s.save(out_path)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    g = build()
    g.pack()
    frames = g.paint(StaffPainter(g), frames=1)
    export(repo, g, frames)
    if len(sys.argv) > 2:
        sheet(repo, g, frames[0], sys.argv[2])
    print("ok")


if __name__ == "__main__":
    main()
