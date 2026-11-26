"""7階「氷の森林」の仕掛けのブロック・ボスのレア「鏡氷の短剣」・状態のアイコンの絵とモデルを作る（11月25日【補完】）。

  python3 tools/artgen/make_iceforest_things.py [リポジトリ] [確認画像の出力先]

ブロック（JavaModel の立体のモデル。絵は models/block と同じ名前の textures/block/<名前>.png）：
  light_spring       光の泉：白い石の水盤と柱の上に、光る結晶。北へ向いた光の口（向きは blockstates の facing で回す）
  ice_mirror         氷の鏡：氷の台に、斜めに立つ鏡の板（霜銀の枠）。上から見て「／」（南西から北東。y に +45 度）。
                     slash=false（「＼」）は、同じモデルを blockstates で 90 度回す
  light_target       光の受け石：白い石の立方体の四方に、光を受ける氷の目。上に小さな結晶
  light_target_lit   （灯った）目と結晶が、淡い金色に光る
品：
  mirror_ice_daggers 鏡氷の短剣（7階のボスのレア。エピック）：11月26日から、形から作り直した（make_gear_rare_reshape.py を呼ぶ）。
                     紫の部品は光る（forge_data の明るさ15）。持ち物の絵は16×16（続けて enhance_icons.py で32×32）
状態のアイコン（18×18）：
  mob_effect/lost_fade  薄れ：白く薄れていく人の胸像と、紫の「？」
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from make_forest_things import ThingPainter  # noqa: E402
from materials import pick, ramp  # noqa: E402

CREDIT = "AMEMARU: 7階の仕掛け（tools/artgen/make_iceforest_things.py で生成）"
ICE7 = {
    "calcite": ramp("#7C7A80", "#9A989E", "#B6B4BA", "#CFCDD2", "#E2E1E5", "#F0EFF2", "#FBFBFC"),
    "ice": ramp("#3E6E9A", "#5A8EBA", "#7AAED4", "#9CC8E8", "#BEDDF4", "#DDEEFA", "#FFFFFF"),
    "mirror": ramp("#4E5A6C", "#6C7A8E", "#8C9AAE", "#AEBBCC", "#CCD7E4", "#E6EDF4", "#FFFFFF"),
    "frost_silver": ramp("#4A5668", "#6A788C", "#8C9AAE", "#B0BCCC", "#CFD8E4", "#E6EDF4", "#F8FBFE"),
    "glow_light": ramp("#9A7A2A", "#C09A3A", "#DDBB55", "#F2D77A", "#FBEAA8", "#FFF6D6", "#FFFFFF"),
    "eye_dim": ramp("#24405E", "#2E5478", "#3C6A92", "#4E82AA", "#6A9CC2", "#8CB6D6", "#B0D0E8"),
    "glow_prism": ramp("#5A3E9A", "#7356BA", "#8E74D2", "#A996E4", "#C6BAF0", "#E2DCFA", "#FFFFFF"),
}


class IceThingPainter(ThingPainter):
    def paint_calcite(self, c):
        tone = self.std(c, "calcite", base=3.8, noise=0.2, blob=0.3, up=1.1, down=1.0, specks=0.02)
        tone += self.grooves(c)
        if c.el.tags.get("ring") and abs(c.n[1]) < 0.5:
            # 受け石の側面の、細い輪の彫り（まん中の目のまわり）
            u = (c.i + 0.5) / c.W - 0.5
            v = (c.j + 0.5) / c.H - 0.5
            r = (u * u + v * v) ** 0.5
            if 0.27 < r < 0.33:
                tone -= 1.4
        return pick(ICE7["calcite"], tone)

    def paint_ice(self, c):
        tone = 4.0 + self.bevel(c, up=1.6, down=0.6) + (self.tex(c, 301) - 0.5) * 0.6
        if (c.i + c.j * 2) % 7 == 0:
            tone += 0.9
        return pick(ICE7["ice"], tone)

    def paint_mirror(self, c):
        """鏡の板：なめらかな白銀。面に斜めの明るい映り込みの帯と、淡い紫のひとすじ（映り込みが間違っている）"""
        tone = 5.1 + self.bevel(c, up=0.8, down=1.2)
        if abs(c.n[1]) < 0.5 and c.el.name == "pane":
            band = (c.i - c.j) % 14
            if band in (0, 1, 2):
                tone += 0.9
            elif band == 7 and c.j % 3 != 0:
                return pick(ICE7["glow_prism"], 3.6)
            elif band in (6, 8):
                tone -= 0.5
        return pick(ICE7["mirror"], tone)

    def paint_frost_silver(self, c):
        tone = 4.0 + (self.tex(c, 311) - 0.5) * 0.3 + self.bevel(c, up=1.6, down=1.0)
        return pick(ICE7["frost_silver"], tone)

    def paint_glow_light(self, c):
        tone = 4.6 + self.bevel(c, up=1.2, down=0.4)
        if c.i == 0 or c.j == 0:
            tone += 0.6
        return pick(ICE7["glow_light"], tone)

    def paint_eye_dim(self, c):
        tone = 3.4 + self.bevel(c, up=1.4, down=0.6)
        return pick(ICE7["eye_dim"], tone)


def box(m, name, frm, to, mat, **kw):
    return m.box(name, frm, to, mat, **kw)


# ---------------------------------------------------------------- ブロック

def build_light_spring():
    m = JavaModel("light_spring", 64, 64)
    box(m, "basin", (2, 0, 2), (14, 3, 14), "calcite", tags={"grooves": [1.5]})
    box(m, "water", (3, 3, 3), (13, 3.5, 13), "ice")
    box(m, "column", (6, 3.5, 6), (10, 9, 10), "calcite", tags={"grooves": [6.5]})
    box(m, "crystal", (6.5, 9, 6.5), (9.5, 13, 9.5), "glow_light", glow=True)
    box(m, "crystal_tip", (7.25, 13, 7.25), (8.75, 14, 8.75), "glow_light", glow=True)
    # 光の口（北へ）。光はここから、決まった向きへまっすぐ出る
    box(m, "spout", (7, 10, 2), (9, 12, 6.5), "glow_light", glow=True)
    box(m, "spout_ring", (6.5, 9.5, 3.5), (9.5, 12.5, 4.5), "frost_silver")
    return m


def build_ice_mirror():
    """鏡の板は、ブロックのまん中を通る斜めの板（y に +45 度。上から見て「／」）。長さ18（ブロックの角の少し手前まで）"""
    m = JavaModel("ice_mirror", 64, 64)
    box(m, "base", (3, 0, 3), (13, 2, 13), "ice")
    box(m, "base_top", (4, 2, 4), (12, 2.5, 12), "frost_silver")
    rot = ((8, 8, 8), "y", 45)
    box(m, "pane", (-0.5, 2.5, 7.25), (16.5, 15, 8.75), "mirror", rot=rot)
    box(m, "frame_top", (-1, 15, 7), (17, 16, 9), "frost_silver", rot=rot)
    box(m, "post_a", (-1, 2.5, 7), (-0.5, 15, 9), "frost_silver", rot=rot)
    box(m, "post_b", (16.5, 2.5, 7), (17, 15, 9), "frost_silver", rot=rot)
    return m


def build_light_target(lit):
    m = JavaModel("light_target_lit" if lit else "light_target", 64, 64)
    box(m, "stone", (0, 0, 0), (16, 16, 16), "calcite", tags={"ring": True})
    mat = "glow_light" if lit else "eye_dim"
    box(m, "eye_n", (6, 6, -0.5), (10, 10, 0), mat, glow=lit)
    box(m, "eye_s", (6, 6, 16), (10, 10, 16.5), mat, glow=lit)
    box(m, "eye_w", (-0.5, 6, 6), (0, 10, 10), mat, glow=lit)
    box(m, "eye_e", (16, 6, 6), (16.5, 10, 10), mat, glow=lit)
    box(m, "crown", (6.5, 16, 6.5), (9.5, 17.5, 9.5), mat, glow=lit)
    return m


BLOCKS = [build_light_spring, build_ice_mirror, lambda: build_light_target(False), lambda: build_light_target(True)]


# ---------------------------------------------------------------- 鏡氷の短剣（11月26日に形から作り直した）

def daggers(base):
    """鏡氷の短剣：tools/artgen/make_gear_rare_reshape.py で作る（前は霜銀の双短剣の塗り替え）。(モデルの絵, 持ち物の絵, 光る部品の数)。"""
    import make_gear_rare_reshape
    repo = os.path.normpath(os.path.join(base, "..", "..", "..", "..", ".."))
    return make_gear_rare_reshape.export_rare_weapon(repo, "mirror_ice_daggers")


# ---------------------------------------------------------------- 状態のアイコン

def effect_lost_fade():
    from make_phase2_art import Canvas, hexc
    c = Canvas(18, 18)
    body = hexc("DCE6F0")
    faint = hexc("9FB0C4")
    # 胸像（丸い頭と、広い肩）。右半分は、白く薄れて点々に
    c.ellipse(4, 2, 10, 8, fill=body)
    c.poly([(1, 16), (2, 12), (5, 9), (9, 9), (12, 12), (13, 16)], body)
    for y in range(2, 17):
        for x in range(8, 14):
            if (x + y) % 2 == 0 and c.img.getpixel((x, y))[3] > 0:
                c.px(x, y, faint)
    # 顔のない所（白く抜けた顔）
    c.rect(5, 4, 8, 6, hexc("FFFFFF"))
    # 紫の「？」
    q = hexc("8E74D2")
    for (x, y) in ((13, 1), (14, 1), (15, 1), (16, 2), (16, 3), (15, 4), (14, 5), (14, 6), (14, 8)):
        c.px(x, y, q)
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
        tex = m.paint(IceThingPainter(m), frames=1)[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
        m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                    extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
        built.append((m, tex))
    write_json(os.path.join(base, "blockstates/light_spring.json"), {"variants": {
        "facing=north": {"model": "amemaru:block/light_spring"},
        "facing=east": {"model": "amemaru:block/light_spring", "y": 90},
        "facing=south": {"model": "amemaru:block/light_spring", "y": 180},
        "facing=west": {"model": "amemaru:block/light_spring", "y": 270}}})
    # 「＼」は「／」を時計回りに90度（上から見て）
    write_json(os.path.join(base, "blockstates/ice_mirror.json"), {"variants": {
        "slash=true": {"model": "amemaru:block/ice_mirror"},
        "slash=false": {"model": "amemaru:block/ice_mirror", "y": 90}}})
    write_json(os.path.join(base, "blockstates/light_target.json"), {"variants": {
        "lit=false": {"model": "amemaru:block/light_target"}, "lit=true": {"model": "amemaru:block/light_target_lit"}}})
    for name in ("light_spring", "ice_mirror", "light_target"):
        write_json(os.path.join(base, "models/item", name + ".json"), {"parent": "amemaru:block/" + name})
    model_tex, icon, lit = daggers(base)
    effect_lost_fade().save(os.path.join(base, "textures/mob_effect/lost_fade.png"))
    if out:
        check(built, model_tex, icon, out)
    print("ok（鏡氷の短剣の光る部品：%d）" % lit)


def check(built, model_tex, icon, out):
    from PIL import ImageDraw
    from render import render_many
    from make_forest_models import sheet_font
    os.makedirs(out, exist_ok=True)
    tiles = []
    for m, tex in built:
        for yaw, pitch in ((150, 22), (-30, 22), (180, 89)):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=pitch, scale=13, size=(250, 270), center=(8, 8, 8)))
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    s = Image.new("RGBA", (250 * cols, 270 * rows + 24 + 150), (52, 60, 70, 255))
    for k, t in enumerate(tiles):
        s.alpha_composite(t, (250 * (k % cols), 24 + 270 * (k // cols)))
    y = 24 + 270 * rows
    s.alpha_composite(icon.convert("RGBA").resize((128, 128), Image.NEAREST), (10, y + 10))
    s.alpha_composite(model_tex.convert("RGBA").resize((128, 128), Image.NEAREST), (160, y + 10))
    eff = Image.open(os.path.join(out, "..", "lost_fade_tmp.png")) if False else effect_lost_fade()
    s.alpha_composite(eff.convert("RGBA").resize((108, 108), Image.NEAREST), (310, y + 20))
    ImageDraw.Draw(s).text((6, 2), "光の泉・氷の鏡（真上から見て「／」）・光の受け石・灯った受け石（斜め前・斜め後ろ・真上）／鏡氷の短剣の絵・"
                                   "モデルの絵・状態「薄れ」", fill=(235, 235, 220), font=sheet_font(15))
    s.save(os.path.join(out, "iceforest_things_check.png"))


if __name__ == "__main__":
    main()
