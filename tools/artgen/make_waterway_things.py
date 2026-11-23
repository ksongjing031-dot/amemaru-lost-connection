"""3階「古代の水路」の仕掛けのブロックと品の絵・モデルを作る（Phase 8 その2）。

  python3 tools/artgen/make_waterway_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only gnawed_cog,murky_gel,waterway_bronze,catfish_whisker,flow_crystal

ブロック（JavaModel の立体のモデル。絵は models/block と同じ名前の textures/block/<名前>.png）：
  floodgate_valve       水門のハンドル：石の台から立ち上がる青銅の管の上に、まるいハンドル（輪と4本の柄）。
                        赤い目印の札（閉じている）。表（北）を向く。向きは blockstates で回す
  floodgate_valve_open  （開いた）ハンドルが45度回り、管の窓が青く光る
品（16×16 の形を描き、enhance_icons.py で 32×32 にする）：
  gnawed_cog        かじられた歯車：青銅の歯車。歯がところどころ欠け、かじった跡
  murky_gel         澱みのゼリー：にごった緑の水のかたまり。中に小さな歯車のかけら
  waterway_bronze   水路の青銅：緑青の浮いた青い青銅の板。リベットの穴
  catfish_whisker   ナマズのひげ：くるりと巻いた、長いひげ
  flow_crystal      流水の結晶：青い結晶。中に水の流れの模様
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from make_forest_things import ThingPainter, paint_rows  # noqa: E402
from make_waterway_models import WATER  # noqa: E402
from materials import RAMPS, pick  # noqa: E402

CREDIT = "AMEMARU: 3階の仕掛け（tools/artgen/make_waterway_things.py で生成）"


class ValvePainter(ThingPainter):
    def paint_bronze_teal(self, c):
        tone = self.std(c, "bronze", base=3.1, noise=0.25, blob=0.5, up=1.4, down=1.0)
        tone += self.rivet(c)
        if self.blob(c, 1.4, 341 + self.eid(c)) < 0.3:
            return pick(RAMPS["bronze"], 3.6 + (self.tex(c, 342) - 0.5))
        return pick(WATER["bronze_teal"], tone)

    def paint_stone_g(self, c):
        tone = self.std(c, "stone", base=3.2, noise=0.3, blob=0.5, up=1.3, down=1.1, specks=0.04)
        return pick(WATER["stone_g"], tone)

    def paint_tag_red(self, c):
        tone = 3.4 + (self.tex(c, 351) - 0.5) * 0.5 + self.bevel(c, up=0.8, down=0.8)
        return pick(RAMPS["cord"], tone)

    def paint_glow_water(self, c):
        return self.paint_glow(c, "glow_water", base=4.3)

    def paint_dark_window(self, c):
        return pick(WATER["brush"], 1.6 + (self.tex(c, 352) - 0.5) * 0.5)


def build_valve(opened):
    m = JavaModel("floodgate_valve_open" if opened else "floodgate_valve", 64, 64)
    m.box("base", (2, 0, 2), (14, 2, 14), "stone_g")
    m.box("base_step", (3, 2, 3), (13, 3, 13), "stone_g")
    m.box("pipe", (6, 3, 6), (10, 11, 10), "pipe", tags={"axis": "y"})
    m.box("pipe_ring", (5.5, 6, 5.5), (10.5, 7, 10.5), "bronze_teal")
    # 管の窓（開くと青く光る）
    if opened:
        m.box("window", (6.75, 8, 5.9), (9.25, 10, 6.1), "glow_water", glow=True)
    else:
        m.box("window", (6.75, 8, 5.9), (9.25, 10, 6.1), "dark_window")
    m.box("hub", (7, 11, 6), (9, 13, 10), "bronze_teal")
    # ハンドル（北を向いた輪。開くと45度回る：輪の柄の向きだけ変える）
    rim = "bronze_teal"
    m.box("rim_top", (3, 15, 4.5), (13, 16, 5.5), rim)
    m.box("rim_bottom", (3, 8, 4.5), (13, 9, 5.5), rim)
    m.box("rim_left", (3, 9, 4.5), (4, 15, 5.5), rim)
    m.box("rim_right", (12, 9, 4.5), (13, 15, 5.5), rim)
    if opened:
        m.box("spoke_a", (4, 11.5, 4.75), (12, 12.5, 5.25), "bronze")
        m.box("spoke_b", (7.5, 9, 4.75), (8.5, 15, 5.25), "bronze")
    else:
        m.box("spoke_a", (4, 9.5, 4.75), (12, 10.5, 5.25), "bronze")
        m.box("spoke_b", (4, 13.5, 4.75), (12, 14.5, 5.25), "bronze")
    m.box("axle", (7.25, 11.25, 5.5), (8.75, 12.75, 6), "bronze")
    m.box("knob", (11.5, 15.5, 4.25), (12.5, 16, 5.75), "bronze")
    # 目印の札（閉じている間だけ、赤い札がさがる）
    if not opened:
        m.box("tag_cord", (7.75, 6, 4.9), (8.25, 8, 5.1), "cord")
        m.box("tag", (6.5, 4, 4.8), (9.5, 6, 5.2), "tag_red")
    return m


# ---------------------------------------------------------------- 品（16×16）

GNAWED_COG = [
    "................",
    "......oo..o.....",
    "...oo.oBBo.oo...",
    "...oBBBBBBoBo...",
    "...oBMMMMMMBo...",
    ".ooBMMLLLLMMBoo.",
    ".oBMMLDDDDLMMBo.",
    "..oBMLDooDLMBo..",
    "..oBMLDooDLMBo..",
    ".oBMMLDDDDLMMBo.",
    ".ooBMMLLLLMM.o..",
    "...oBMMMMMo.....",
    "...oBBoBBBo.o...",
    "...oo.oBBo.oo...",
    "......o.oo......",
    "................",
]
GNAWED_COG_PAL = {"o": "#2A2418", "B": "#8E6E32", "M": "#B08A44", "L": "#D2AE62", "D": "#6A5226"}

MURKY_GEL = [
    "................",
    "................",
    ".......oo.......",
    ".....ooHHoo.....",
    "....oHHMMMMo....",
    "...oHMMMMMMMo...",
    "...oMMMMDMMMo...",
    "..oHMMMDcDMMMo..",
    "..oMMMMMDMMMMo..",
    "..oMMMMMMMMDMo..",
    "..oMMgMMMMMMMo..",
    "...oMMMMMMMDo...",
    "...oDMMMMMDDo...",
    "....ooDDDDoo....",
    "......oooo......",
    "................",
]
MURKY_GEL_PAL = {"o": "#0E1C1A", "H": "#86C2A8", "M": "#3E7A68", "D": "#24503F", "c": "#B08A44", "g": "#7A5A2A"}

WATERWAY_BRONZE = [
    "................",
    "................",
    "..oooooooooooo..",
    "..oLLLLLLLLLTo..",
    "..oLhMMMMMMhTo..",
    "..oLMMMMTTMMTo..",
    "..oLMTMMMMMMTo..",
    "..oLMMMMMTMMTo..",
    "..oLMMTMMMMMTo..",
    "..oLMMMMMMTMTo..",
    "..oLhMMMMMMhTo..",
    "..oTTTTTTTTTDo..",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]
WATERWAY_BRONZE_PAL = {"o": "#13231F", "L": "#7EB0A0", "M": "#4A786C", "T": "#386057", "D": "#2A4A42", "h": "#1E3530"}

CATFISH_WHISKER = [
    "................",
    "............oo..",
    "...........oLDo.",
    "..........oLDo..",
    ".........oLDo...",
    "........oLDo....",
    ".......oLDo.....",
    "......oLDo......",
    ".....oLDo.......",
    "....oLDo........",
    "...oLDo.........",
    "..oLDo...oo.....",
    "..oLDooooLDo....",
    "...oLLLLLDo.....",
    "....ooooooo.....",
    "................",
]
CATFISH_WHISKER_PAL = {"o": "#191A12", "L": "#7A7558", "D": "#40402D"}

FLOW_CRYSTAL = [
    "................",
    ".......oo.......",
    "......oWLo......",
    ".....oWLBLo.....",
    ".....oLBLBo.....",
    "....oWLBwBLo....",
    "....oLBwBBLo....",
    "...oWLBBwBBLo...",
    "...oLBBBBwBLo...",
    "...oLBwBBBBLo...",
    "....oLBwBBLo....",
    "....oLBBwBLo....",
    ".....oLBBLo.....",
    "......oLLo......",
    ".......oo.......",
    "................",
]
FLOW_CRYSTAL_PAL = {"o": "#0E2A4A", "W": "#E6F6FF", "L": "#7CC4F0", "B": "#2E7EC2", "w": "#B6E4FA"}

ICONS = {
    "gnawed_cog": (GNAWED_COG, GNAWED_COG_PAL),
    "murky_gel": (MURKY_GEL, MURKY_GEL_PAL),
    "waterway_bronze": (WATERWAY_BRONZE, WATERWAY_BRONZE_PAL),
    "catfish_whisker": (CATFISH_WHISKER, CATFISH_WHISKER_PAL),
    "flow_crystal": (FLOW_CRYSTAL, FLOW_CRYSTAL_PAL),
}


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
    for opened in (False, True):
        m = build_valve(opened)
        m.cull_hidden_faces()
        m.pack()
        tex = m.paint(ValvePainter(m), frames=1)[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
        m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                    extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
        built.append((m, tex))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for opened in (False, True):
            v = {"model": "amemaru:block/floodgate_valve" + ("_open" if opened else "")}
            if rot:
                v["y"] = rot
            variants["facing=%s,open=%s" % (facing, "true" if opened else "false")] = v
    write_json(os.path.join(base, "blockstates/floodgate_valve.json"), {"variants": variants})
    write_json(os.path.join(base, "models/item/floodgate_valve.json"), {"parent": "amemaru:block/floodgate_valve"})
    for name, (rows, pal) in ICONS.items():
        paint_rows(rows, pal).save(os.path.join(base, "textures/item", name + ".png"))
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
        for yaw in (180, 150, -30):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=22, scale=14, size=(280, 300), center=(8, 8, 8)))
    s = Image.new("RGBA", (280 * 6, 300 + 24), (52, 60, 52, 255))
    for k, t in enumerate(tiles):
        s.alpha_composite(t, (280 * k, 24))
    ImageDraw.Draw(s).text((6, 2), "水門のハンドル（閉じている：正面・斜め前・斜め後ろ）・（開いた）", fill=(235, 235, 220), font=sheet_font(15))
    s.save(os.path.join(out, "waterway_things_check.png"))


if __name__ == "__main__":
    main()
