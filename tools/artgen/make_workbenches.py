"""3つの工房の作業台（武器・防具・装飾）のブロックのモデルと絵（11月23日）。

「作業台系のアイテムの見た目を変える」のご指示で、のっぺりした台（板と4本の脚、色の面）を作り直した。
どれも、天板・脚・前後左右の幕板・下の棚のある頑丈な台に、その工房の道具をのせる：

  武器の作業台：クルミの台に鉄の角金具。天板に小さな金床・槌・打ちかけの剣・砥石、下の棚に苔鉄の延べ棒
  防具の作業台：トネリコの台に革を敷き、打ち出しの胸当て・鋏・糸巻き、下の棚に重ねた鉄の板と革の巻き
  装飾の作業台：紫に染めた木の台にビロードを敷き、拡大鏡のついた灯り・宝石の皿・ピンセット、下の棚に小箱

部品は2倍の大きさ（ブロックは 0〜32）で組み、絵も2倍の細かさ（ブロックの1ピクセルに2×2）で塗る。書き出すときに
大きさを半分にする（ブロックの 0〜16）。天板は高さ10〜13、道具は16まで（WorkshopBlock の当たり判定と合わせる）。

  python3 tools/artgen/make_workbenches.py [repo] [確認画像の出力先]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel, r4, save_frames  # noqa: E402
from materials import RAMPS, Painter, hexc, hsh, pick, ramp  # noqa: E402

CREDIT = "AMEMARU: 工房の作業台（11月23日。tools/artgen/make_workbenches.py で生成）"
TEX = 128

RAMPS_EXTRA = {
    # 紫に染めた木（装飾工房）
    "plum": ramp("#1C1220", "#2B1B31", "#3D2745", "#51355B", "#664572", "#7E598A", "#9A73A4"),
    # ビロード（深い紫）
    "velvet": ramp("#1E0F2E", "#2D1645", "#3F1F5E", "#532A78", "#6A3792", "#8448AE", "#A563C8"),
    # 真鍮
    "brass": ramp("#3A2A0E", "#5A4216", "#7E5E1F", "#A47C2B", "#C69C3C", "#E0BD5E", "#F5DD92"),
    # 宝石：青・琥珀・翠・紅
    "gem_blue": RAMPS["glow_bee"],
    "gem_amber": RAMPS["glow_amber"],
    "gem_teal": RAMPS["glow_teal"],
    "gem_red": ramp("#4A0A12", "#6E1019", "#951A24", "#BA2A33", "#D9474A", "#EE7470", "#FBB0A8"),
    # レンズ（薄い水色）
    "glass": ramp("#3F6470", "#557F8C", "#6E9BA8", "#8DB8C3", "#AFD3DB", "#D3EBEF", "#F4FCFD"),
}


class BenchPainter(Painter):
    """作業台の材質。木は木目、金属は面取り、革とビロードは目の細かいむら。"""

    def r(self, name):
        return RAMPS_EXTRA.get(name) or RAMPS[name]

    def grain(self, c):
        """木目（"x"・"y"・"z"：木目の向き）"""
        g = c.el.tags.get("grain")
        if g != "z":
            return super().grain(c)
        q = c.p
        line = hsh(int(math.floor(q[0] * 2)), int(math.floor(q[1] * 2)), 41)
        run = hsh(int(math.floor(q[1] * 2)), int(math.floor(q[2] / 3)), 42)
        if line < 0.25 and run < 0.7:
            return -0.7
        return (line - 0.5) * 0.4

    def wood(self, c, name, base=3.2):
        tone = self.std(c, name if name in RAMPS else "walnut", base=base, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c)
        # 板の継ぎ目（天板の上の面：x の8ごと）
        if c.el.tags.get("planks") and c.n[1] > 0.6 and int(math.floor(c.p[0])) % 8 == 0:
            tone -= 1.3
        return tone

    def paint_walnut(self, c):
        return pick(self.r("walnut"), self.wood(c, "walnut"))

    def paint_ash(self, c):
        return pick(self.r("ash"), self.wood(c, "ash", 3.3))

    def paint_plum(self, c):
        tone = self.std(c, "walnut", base=3.2, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c)
        if c.el.tags.get("planks") and c.n[1] > 0.6 and int(math.floor(c.p[0])) % 8 == 0:
            tone -= 1.3
        return pick(self.r("plum"), tone)

    def paint_velvet(self, c):
        tone = 3.3 + (self.tex(c, 31) - 0.5) * 0.5 + self.bevel(c, up=0.6, down=0.7)
        # 布の端の縫い取り（金の糸）
        if c.n[1] > 0.6 and min(c.i, c.j, c.W - 1 - c.i, c.H - 1 - c.j) == 1 and (c.i + c.j) % 2 == 0:
            return pick(self.r("brass"), 4.2)
        return pick(self.r("velvet"), tone)

    def paint_brass(self, c):
        tone = self.std(c, "bronze", base=3.8, noise=0.15, blob=0.15, up=1.6, down=1.0)
        return pick(self.r("brass"), tone)

    def paint_glass(self, c):
        tone = 3.6 + (1.6 if (c.i + c.j) <= 1 else 0.0) - (0.8 if c.i + c.j >= c.W + c.H - 3 else 0.0)
        return pick(self.r("glass"), tone)

    def gem(self, c, name):
        tone = 3.4 + (1.8 if (c.i + c.j) == 0 else 0.0) + self.bevel(c, up=0.9, down=0.9)
        return pick(self.r(name), tone)

    def paint_gem_blue(self, c):
        return self.gem(c, "gem_blue")

    def paint_gem_amber(self, c):
        return self.gem(c, "gem_amber")

    def paint_gem_teal(self, c):
        return self.gem(c, "gem_teal")

    def paint_gem_red(self, c):
        return self.gem(c, "gem_red")

    def paint_bracket(self, c):
        """角金具：黒い鉄に、明るい鋲"""
        tone = self.std(c, "darksteel", base=3.0, noise=0.2, blob=0.2, up=1.6, down=0.9)
        if (c.i % 3 == 1) and (c.j % 3 == 1):
            tone += 2.4
        return pick(RAMPS["darksteel"], tone)

    def paint_ingot(self, c):
        tone = self.std(c, "iron", base=3.4, noise=0.2, blob=0.3, up=1.8, down=1.0)
        return pick(RAMPS["worn"], tone)


# ---------------------------------------------------------------- 台（3つで同じ形。木の材質だけ変える）
def table(m, wood, legs_wood=None, shelf_items=None):
    lw = legs_wood or wood
    # 天板（高さ10〜13 → 20〜26）。板の継ぎ目・まわりに少し張り出す
    m.box("top", (0, 20, 0), (32, 26, 32), wood, tags={"grain": "x", "planks": True})
    # 幕板（天板の下、脚の間）
    m.box("apron_n", (6, 16, 2.5), (26, 20, 4.5), lw, tags={"grain": "x"})
    m.box("apron_s", (6, 16, 27.5), (26, 20, 29.5), lw, tags={"grain": "x"})
    m.box("apron_w", (2.5, 16, 6), (4.5, 20, 26), lw, tags={"grain": "z"})
    m.box("apron_e", (27.5, 16, 6), (29.5, 20, 26), lw, tags={"grain": "z"})
    # 脚（四隅。下は少し太い足）
    for k, (x, z) in enumerate(((1, 1), (25, 1), (1, 25), (25, 25))):
        m.box(f"leg_{k}", (x + 0.5, 2, z + 0.5), (x + 5.5, 20, z + 5.5), lw, tags={"grain": "y"})
        m.box(f"foot_{k}", (x, 0, z), (x + 6, 2, z + 6), lw, tags={"grain": "y"})
    # 下の棚（高さ3〜4 → 6〜8）
    m.box("shelf", (4, 6, 4), (28, 8, 28), wood, tags={"grain": "x"})
    if shelf_items:
        shelf_items(m)


def strip_hidden(m):
    m.cull_hidden_faces()


# ---------------------------------------------------------------- 武器の作業台
def build_weapon():
    m = JavaModel("weapon_workbench_model", TEX, TEX)

    def shelf(m):
        # 苔鉄の延べ棒（3本）
        m.box("ingot_0", (8, 8, 9), (15, 10.5, 13), "ingot")
        m.box("ingot_1", (16, 8, 9), (23, 10.5, 13), "ingot")
        m.box("ingot_2", (11.5, 10.5, 9.5), (18.5, 13, 12.5), "ingot")
        # 炭の袋
        m.box("sack", (16, 8, 17), (24, 14, 24), "leather")
    table(m, "walnut", shelf_items=shelf)
    # 角金具（天板の四隅の上と横）
    for k, (x, z) in enumerate(((0, 0), (27, 0), (0, 27), (27, 27))):
        m.box(f"bracket_{k}", (x - 0.3, 19.5, z - 0.3), (x + 5.3, 26.4, z + 5.3), "bracket")
    # 金床（台の奥のまん中）：台座・くびれ・打つ面・角
    m.box("anvil_base", (12, 26, 13), (21, 27.5, 19), "darksteel")
    m.box("anvil_waist", (14, 27.5, 14.5), (19, 29.5, 17.5), "darksteel")
    m.box("anvil_face", (10, 29.5, 13), (23, 32, 19), "steel")
    m.box("anvil_horn", (6.5, 30, 14.5), (10, 31.6, 17.5), "steel")
    m.box("anvil_heel", (23, 30, 14.5), (25, 32, 17.5), "darksteel")
    # 槌（手前の左）
    m.box("hammer_handle", (3, 26, 23.4), (12.5, 27.2, 24.6), "ash", tags={"grain": "x"})
    m.box("hammer_head", (11.5, 26, 21.5), (14.5, 28.8, 26.5), "darksteel")
    # 打ちかけの剣（手前の右）と、砥石（奥の右）
    m.box("blade", (17, 26, 23), (29.5, 26.8, 25.2), "steel")
    m.box("blade_tip", (29.5, 26, 23.5), (30.5, 26.8, 24.7), "steel")
    m.box("tang", (14.5, 26, 23.6), (17, 26.6, 24.6), "darksteel")
    m.box("whetstone", (24, 26, 5), (30, 28, 10), "stone")
    # 奥の左：やっとこ（2本の柄）
    m.box("tongs_a", (3, 26, 5), (11, 26.8, 6), "darksteel")
    m.box("tongs_b", (3, 26.8, 6.5), (11, 27.6, 7.5), "darksteel")
    return m


# ---------------------------------------------------------------- 防具の作業台
def build_armor():
    m = JavaModel("armor_workbench_model", TEX, TEX)

    def shelf(m):
        # 重ねた鉄の板と、革の巻き
        m.box("plate_0", (6, 8, 7), (15, 9, 16), "steel")
        m.box("plate_1", (7, 9, 8), (14, 10, 15), "steel")
        m.box("plate_2", (6.5, 10, 7.5), (14.5, 11, 15.5), "darksteel")
        m.box("roll", (17, 8, 10), (26, 12, 14), "leather", tags={"wrap": True})
        m.box("roll_b", (17, 8, 17), (26, 12, 21), "leather_dark", tags={"wrap": True})
    table(m, "ash", legs_wood="walnut", shelf_items=shelf)
    # 天板に敷いた革
    m.box("hide", (3, 26, 3), (29, 26.5, 29), "leather", tags={"stitch": True})
    # 打ち出しの胸当て（まん中。ふくらみを段で）
    m.box("breast_0", (9, 26.5, 9), (23, 28, 22), "steel", tags={"rivets": []})
    m.box("breast_1", (10.5, 28, 10), (21.5, 29.5, 21), "steel")
    m.box("breast_2", (12.5, 29.5, 11.5), (19.5, 30.6, 19.5), "steel")
    m.box("breast_rim", (8.5, 26.5, 21), (23.5, 28.4, 23), "darksteel")
    # 鋏（手前の左）と、糸巻き（奥の右）、木槌（奥の左）
    m.box("shear_a", (3.5, 26.5, 24), (11, 27.1, 25), "steel")
    m.box("shear_b", (4.5, 26.5, 25.6), (11.5, 27.1, 26.6), "steel")
    m.box("shear_grip", (11, 26.5, 23.5), (14, 27.5, 27), "leather_dark")
    m.box("spool_bottom", (23.6, 26.5, 3.6), (28.9, 27.4, 8.9), "ash", tags={"grain": "x"})
    m.box("spool_thread", (24.2, 27.4, 4.2), (28.3, 30.1, 8.3), "cord", tags={"twist": True})
    m.box("spool_top", (23.6, 30.1, 3.6), (28.9, 31, 8.9), "ash", tags={"grain": "x"})
    m.box("mallet_handle", (3.5, 26.5, 5.5), (12, 27.5, 6.6), "walnut", tags={"grain": "x"})
    m.box("mallet_head", (11, 26.5, 3.8), (15, 29.5, 8.3), "ash", tags={"grain": "y"})
    return m


# ---------------------------------------------------------------- 装飾の作業台
def build_accessory():
    m = JavaModel("accessory_workbench_model", TEX, TEX)

    def shelf(m):
        # 小箱（ビロード張りの指輪の箱と、木の小箱）
        m.box("box_0", (7, 8, 9), (14, 12, 15), "plum")
        m.box("box_0_lid", (6.6, 12, 8.6), (14.4, 13, 15.4), "velvet")
        m.box("box_1", (17, 8, 9), (25, 11, 17), "walnut", tags={"grain": "x"})
        m.box("box_2", (17.5, 8, 19), (23.5, 10, 24), "brass")
    table(m, "plum", shelf_items=shelf)
    # ビロードの敷き布
    m.box("cloth", (4, 26, 4), (28, 26.6, 28), "velvet")
    # 宝石の皿（手前の左）と、宝石4つ
    m.box("tray", (5, 26.6, 17), (15, 27.6, 26), "brass")
    for k, (x, z, mat) in enumerate(((6.5, 18.5, "gem_blue"), (10, 19, "gem_amber"), (7, 22.5, "gem_teal"),
                                     (11, 22.5, "gem_red"))):
        m.box(f"gem_{k}", (x, 27.6, z), (x + 2.5, 29, z + 2.5), mat)
    # 拡大鏡のついた灯り（奥の右）：台座・柱・腕・レンズの輪とレンズ
    m.box("lamp_base", (20, 26.6, 4), (27, 27.8, 11), "brass")
    m.box("lamp_post", (23, 27.8, 7), (24.2, 31.4, 8.2), "brass")
    m.box("lamp_arm", (15, 30.4, 7), (24.2, 31.4, 8.2), "brass")
    m.box("lens_ring", (11.5, 27.5, 6.6), (16.5, 32, 8.6), "brass")
    m.box("lens", (12.3, 28.3, 7.2), (15.7, 31.2, 8.0), "glass")
    # ピンセットと、やすり（手前の右）
    m.box("tweezers", (18, 26.6, 22), (26, 27.1, 22.8), "steel")
    m.box("file", (18, 26.6, 24.5), (27, 27.3, 25.6), "darksteel")
    m.box("file_grip", (27, 26.6, 24.3), (29.5, 27.6, 25.8), "walnut")
    return m


BENCHES = (("weapon_workbench", build_weapon), ("armor_workbench", build_armor), ("accessory_workbench", build_accessory))


def export(repo, name, m, frames):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    tex_ref = "amemaru:block/" + name + "_model"
    elements = m.json_elements()
    # 2倍で組んだ大きさを、ブロックの大きさ（0〜16）に戻す
    for e in elements:
        e["from"] = [r4(v / 2.0) for v in e["from"]]
        e["to"] = [r4(v / 2.0) for v in e["to"]]
        if "rotation" in e:
            e["rotation"]["origin"] = [r4(v / 2.0) for v in e["rotation"]["origin"]]
    data = {"credit": CREDIT, "parent": "minecraft:block/block", "texture_size": [TEX, TEX],
            "textures": {"0": tex_ref, "particle": tex_ref}, "elements": elements}
    with open(os.path.join(base, "models/block", name + ".json"), "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    save_frames(frames, os.path.join(base, "textures/block", name + "_model.png"))


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    main_for(repo, out)


def main_for(repo, out=None):
    for name, build in BENCHES:
        m = build()
        strip_hidden(m)
        used = m.pack()
        frames = m.paint(BenchPainter(m), frames=1)
        export(repo, name, m, frames)
        print("wrote", name, "部品", len(m.elements), "絵の使った高さ", used, "/", TEX)
        if out:
            os.makedirs(out, exist_ok=True)
            from render import render
            img = render(m, frames[0], yaw=225 - 180, pitch=30, scale=8.0, size=(420, 420), center=(16, 16, 16),
                         light="gui", ss=2)
            img.save(os.path.join(out, name + "_preview.png"))


if __name__ == "__main__":
    main()
