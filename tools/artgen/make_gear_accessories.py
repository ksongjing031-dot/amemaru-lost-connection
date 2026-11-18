"""体に表示するアクセサリー（アンコモン）：草原の首飾り・石くれの腕輪・道標のお守り。

  python3 tools/artgen/make_gear_accessories.py <リポジトリ> [確認画像の出力先]

GeckoLib の防具と同じボーン（armorBody＝胸・腰、armorRightArm＝右の手首）に入れる。
アンコモンの決まり：光らない。コモンより少しだけ飾りが多い（玉・苔鉄の輪・文字の溝）。

胸の防具を着ているときは、防具の上に見えるように、描くときに胴を横 1.3・前後 1.45 倍、
腕を横・前後 1.25 倍に広げる（AccessoryCurioRenderer）。首飾りの角は、重鎧の円盤と法衣の留め具の間に来る高さにする。
仕様書：docs/gear/accessories.md
"""
import os
import sys
from types import SimpleNamespace

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from materials import Painter, RAMPS, pick, hsh  # noqa: E402
from make_gear_ancient_armor import ARMOR_BONES, ArmorPainter  # noqa: E402

ARMORED_BODY = (1.3, 1.0, 1.45)
ARMORED_ARM = (1.25, 1.0, 1.25)


def new_accessory(name):
    g = GeoModel(name, 32, 32)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    return g


def gcube(g, bone, name, origin, size, mat, rot=None, pivot=None, **kw):
    """ゲームの中の向き（体の右が +x、表が −z）で書いた箱を、Bedrock の値にして足す。"""
    x0, y0, z0 = origin
    w = size[0]
    b_rot = b_pivot = None
    if rot:
        b_rot = (-rot[0], -rot[1], rot[2])
        b_pivot = (-pivot[0], pivot[1], pivot[2])
    return g.cube(bone, name, (-(x0 + w), y0, z0), size, mat, rot=b_rot, pivot=b_pivot, **kw)


# ---------------------------------------------------------------- 草原の首飾り

def build_necklace():
    g = new_accessory("grassland_necklace")
    B = "armorBody"
    # ツノモフの毛を編んだ紐：首の後ろを回り、胸の前でV字に下りる
    gcube(g, B, "cord_back", (-3, 23.4, 1.9), (6, 1, 1), "cord", inflate=-0.2)
    for side, sx in (("r", 1), ("l", -1)):
        gcube(g, B, f"cord_side_{side}", (sx * 3.25 - 0.5, 23.4, -2.2), (1, 1, 4), "cord", inflate=-0.2)
        # 肩から胸のまん中へ斜めに下りる紐。とちゅうに小さな緑の玉
        cx, cy = sx * 1.6, 23.0
        gcube(g, B, f"cord_v_{side}", (cx - 1.75, cy - 0.5, -2.45), (4, 1, 1), "cord", inflate=-0.2,
              rot=(0, 0, sx * 30), pivot=(cx, cy, -2.0))
        gcube(g, B, f"bead_small_{side}", (sx * 2.1 - 0.5, 23.05, -2.75), (1, 1, 1), "bead", inflate=-0.15)
    # 緑の玉（草原の石）と、柔角の飾り（ツノモフの角。根元が太く、先が丸く曲がる）
    gcube(g, B, "bead", (-0.5, 21.9, -2.8), (1, 1, 1), "bead", inflate=0.15)
    gcube(g, B, "horn_base", (-1, 20.6, -3.0), (2, 1, 1), "horn", inflate=0.2)
    gcube(g, B, "horn_mid", (-0.75, 19.6, -2.9), (1, 1, 1), "horn", inflate=0.2, rot=(0, 0, -22.5),
          pivot=(0, 20.6, -2.4))
    gcube(g, B, "horn_tip", (0.1, 18.9, -2.85), (1, 1, 1), "horn", inflate=-0.05, rot=(0, 0, -45),
          pivot=(0.4, 19.8, -2.3), tags={"tip": True})
    return g


# ---------------------------------------------------------------- 石くれの腕輪（右の手首）

def build_bracelet():
    g = new_accessory("ishikure_bracelet")
    A = "armorRightArm"
    # ゲームの中では右腕は x 4..8（Bedrock の −8..−4）。手首の少し上（手袋や袖口より上）
    y0 = 14.5
    gcube(g, A, "seg_front", (4.5, y0, -2.75), (3, 2, 1), "stone", inflate=0.15, tags={"grooves": [y0 + 1.0]})
    gcube(g, A, "seg_back", (4.5, y0, 1.75), (3, 2, 1), "stone", inflate=0.15, tags={"grooves": [y0 + 1.0]})
    gcube(g, A, "seg_out", (7.75, y0, -1.5), (1, 2, 3), "stone", inflate=0.15)
    gcube(g, A, "seg_in", (3.25, y0, -1.5), (1, 2, 3), "stone", inflate=0.15)
    # 外の石に、石くれ兵の「目」の溝と、光らない結晶の欠片
    gcube(g, A, "eye", (8.6, y0 + 0.5, -0.5), (1, 1, 1), "crystal_dim", inflate=-0.1)
    # 苔鉄の輪：石と石をつなぐ角の小さな輪
    for k, (x, z) in enumerate(((7.75, -2.5), (7.75, 1.5), (3.25, -2.5), (3.25, 1.5))):
        gcube(g, A, f"link_{k}", (x, y0 + 0.5, z), (1, 1, 1), "iron", inflate=0.12)
    return g


# ---------------------------------------------------------------- 道標のお守り（左の腰）

def build_charm():
    g = new_accessory("waystone_charm")
    B = "armorBody"
    x = -5.0  # ゲームの中の左の腰（−x）。体の横 x=−4 に付く
    gcube(g, B, "cord", (x + 0.5, 10.6, -0.5), (1, 2, 1), "cord", inflate=-0.25)
    gcube(g, B, "ring", (x + 0.5, 10.0, -0.5), (1, 1, 1), "iron", inflate=-0.05)
    gcube(g, B, "cap_2", (x + 0.5, 9.4, -0.5), (1, 1, 1), "stone", inflate=-0.1)
    gcube(g, B, "cap", (x, 8.6, -1), (2, 1, 2), "stone", inflate=-0.25)
    gcube(g, B, "body", (x, 5.0, -1), (2, 4, 2), "stone", inflate=0.1, tags={"grooves": [7.75, 6.25], "charm": True})
    gcube(g, B, "base", (x, 4.2, -1), (2, 1, 2), "stone", inflate=0.3)
    # ツノモフの毛の房（生成り）。お守りの下に下がる
    gcube(g, B, "tassel_knot", (x + 0.5, 3.4, -0.5), (1, 1, 1), "cord", inflate=-0.1)
    gcube(g, B, "tassel", (x + 0.5, 1.6, -0.5), (1, 2, 1), "wool", inflate=0.1, tags={"tassel": True})
    return g


# ---------------------------------------------------------------- 塗り

class AccessoryPainter(Painter):
    light = ArmorPainter.light

    def paint_stone(self, c):
        tone = self.std(c, "stone", base=3.3, noise=0.15, blob=0.3, up=1.3, down=1.1, specks=0.02)
        if c.el.tags.get("grooves") and c.face.d not in ("up", "down"):
            for gy in c.el.tags["grooves"]:
                if int(np.floor(c.p[1])) == int(np.floor(gy)) and hsh(c.i, int(gy * 4), 7) > 0.25:
                    tone -= 1.5
        if abs(c.n[0]) > 0.6:
            tone += 0.3
        col = pick(RAMPS["stone"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 300 + self.eid(c)) < 0.2:
            col = (75, 97, 64)
        return col

    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.6, noise=0.2, blob=0.3, up=1.4, down=0.9)
        return pick(RAMPS["iron"], tone)

    def paint_cord(self, c):
        # 編んだ毛の紐：生成りと草色が交互に
        k = (c.i + c.j) % 3
        return pick(RAMPS["wool"], 4.2) if k != 2 else (124, 150, 92)

    def paint_bead(self, c):
        tone = 3.0 + self.bevel(c, up=1.6, down=0.8)
        return pick([(34, 70, 44), (46, 96, 58), (66, 130, 78), (96, 166, 104), (140, 204, 140), (190, 232, 180)], tone)

    def paint_wool(self, c):
        tone = 4.2 + (self.tex(c, 71) - 0.5) * 0.5 + self.bevel(c, up=0.6, down=0.8)
        if c.el.tags.get("tassel") and c.j == c.H - 1 and c.i % 2 == 1:
            return None  # 房の先はぎざぎざ
        if c.el.tags.get("tassel") and c.face.d not in ("up", "down") and c.i % 2 == 0:
            tone -= 0.5  # 毛の筋
        return pick(RAMPS["wool"], tone)

    def paint_crystal_dim(self, c):
        return pick(RAMPS["crystal_dim"], 4.0 + self.bevel(c, up=1.4, down=0.8))

    def paint_horn(self, c):
        tone = self.std(c, "bone", base=4.2, noise=0.15, blob=0.2, up=1.0, down=1.0)
        if c.el.tags.get("tip"):
            tone += 0.6
        return pick(RAMPS["bone"], tone)


# ---------------------------------------------------------------- 持ち物の絵（16×16）

ICON = {"W": "#E9DFC8", "w": "#BCAF96", "g": "#7C965C", "G": "#4E9A60", "h": "#B4E0A8", "x": "#2E5E3A",
        "B": "#E1DAC2", "b": "#CBC3A8", "n": "#8E846D",
        "A": "#D8E1E3", "a": "#B7C3C8", "s": "#98A6AD", "c": "#7C8A92", "d": "#606D75", "e": "#454F56",
        "i": "#757E79", "k": "#434A47", "m": "#5E7650"}

ART = {
    "grassland_necklace": """
................
....gWgWgWgW....
...W........g...
..g..........W..
..W..........g..
..g..........W..
...W........g...
....gW....Wg....
......WggW......
.......hG.......
......bBBn......
.......BBn......
........Bn......
.........B......
................
................
""",
    "ishikure_bracelet": """
................
................
................
.....kcaacck....
...kd.......dk..
..ia.........ai.
..sa.........as.
..cd.........dc.
..ik.........ki.
...kaAAAAAAAak..
...isdsssdssi...
...kcccccccck...
....kddddddk....
................
................
................
""",
    "waystone_charm": """
................
......WgWg......
.....g....W.....
.....W....g.....
......gWWg......
.......ik.......
.......Aa.......
......aAac......
......aedc......
......abmc......
......aedc......
......bccd......
.....aAabcd.....
.....cddddd.....
.......Ww.......
.......wW.......
""",
}


def icon(name):
    from pixelart import Pixel
    from materials import hexc
    rows = [r for r in ART[name].strip("\n").splitlines()]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, hexc(ICON[ch]))
    p.outline("#16191C")
    return p.icon()


# ---------------------------------------------------------------- 確認画像

def armored(g, on):
    """胸の防具を着ているときの広げ方（AccessoryCurioRenderer と同じ）。ボーンの中心で、横と前後を広げる。"""
    if not on:
        return g
    pivots = {name: np.array([-p[0], p[1], p[2]], dtype=float) for name, p, _ in g.bones}
    out = []
    for c in g.elements:
        s = ARMORED_BODY if c.bone == "armorBody" else ARMORED_ARM if "Arm" in c.bone else (1, 1, 1)
        out.append(_Scaled(c, pivots[c.bone], np.array(s, dtype=float)))
    return SimpleNamespace(elements=out)


class _Scaled:
    def __init__(self, c, pivot, s):
        self._c, self._pv, self._s = c, pivot, s
        self.faces = {k: _ScaledFace(f, self) for k, f in c.faces.items()}

    def __getattr__(self, k):
        return getattr(self._c, k)


class _ScaledFace:
    def __init__(self, f, owner):
        self._f, self._o = f, owner

    def __getattr__(self, k):
        return getattr(self._f, k)

    def corners_world(self):
        o = self._o
        return [(q - o._pv) * o._s + o._pv for q in self._f.corners_world()]

    def normal_world(self):
        n = self._f.normal_world() / self._o._s
        return n / np.linalg.norm(n)


def build_all():
    out = []
    for build in (build_necklace, build_bracelet, build_charm):
        g = build()
        g.pack()
        frames = g.paint(AccessoryPainter(g), frames=1)
        out.append((g, frames))
    return out


def draft(out_dir):
    from render import render_many
    from PIL import Image
    import make_gear_ancient_armor as AA
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    plate = AA.build_plate()
    ptex = AA.paint(plate)[0]
    robe = AA.build_robe()
    rtex = AA.paint(robe)[0]
    chest = AA.PIECE_BONES["chest"] + AA.PIECE_BONES["legs"]
    plate_c = AA.subset(plate, chest)
    robe_c = AA.subset(robe, chest)
    accs = build_all()
    tiles = []
    for wear, on in (([], False), ([(plate_c, ptex)], True), ([(robe_c, rtex)], True)):
        pairs = [(man, mtex)] + wear + [(armored(g, on), f[0]) for g, f in accs]
        for yaw, pitch in ((180, 0), (-145, 14), (-60, 10)):
            tiles.append(render_many(pairs, yaw=yaw, pitch=pitch, scale=12, size=(300, 380), center=(0, 17, 0)))
    s = Image.new("RGBA", (300 * 3, 380 * 3), (40, 44, 52, 255))
    for k, im in enumerate(tiles):
        s.alpha_composite(im, (300 * (k % 3), 380 * (k // 3)))
    s.save(os.path.join(out_dir, "accessories_draft.png"))


NOTES = {
    "grassland_necklace": ("草原の首飾り", "ツノモフの毛を編んだ紐（生成りと草色）\nに、緑の玉と、ツノモフの柔角の飾り\n角は重鎧の円盤と法衣の留め具の間"),
    "ishikure_bracelet": ("石くれの腕輪", "石くれ兵の石を4つ、苔鉄の輪でつないだ\nごつい腕輪（右の手首の少し上）\n外の石に石くれ兵の目（光らない結晶）"),
    "waystone_charm": ("道標のお守り", "段のある小さな道標の柱を、毛の紐と\n苔鉄の輪で左の腰に下げる。文字の溝2本\n下にツノモフの毛の房"),
}


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_acc"
    os.makedirs(out, exist_ok=True)
    import make_gear_ancient_armor as AA
    from setsheet import make_set_sheet
    accs = build_all()
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for g, frames in accs:
        g.save(repo, "accessory", frames)
        icon(g.name).save(os.path.join(base, "textures/item", g.name + ".png"))
    draft(out)
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    rows = []
    for g, frames in accs:
        label, note = NOTES[g.name]
        rows.append({"label": label, "rarity": "アンコモン（光らない）", "rarity_color": (120, 220, 120), "note": note,
                     "pairs": [(man, mtex), (g, frames[0])], "icon": icon(g.name),
                     "views": [("正面", 180, 0), ("右の横", -90, 0), ("左の横", 90, 0), ("斜め", -145, 14)]})
    plate = AA.build_plate()
    ptex = AA.paint(plate)[0]
    robe = AA.build_robe()
    rtex = AA.paint(robe)[0]
    chest = AA.PIECE_BONES["chest"] + AA.PIECE_BONES["legs"]
    on = [(armored(g, True), f[0]) for g, f in accs]
    off = [(g, f[0]) for g, f in accs]
    details = [
        {"label": "首飾り（柔角と緑の玉）", "pairs": [(man, mtex)] + off, "center": (0, 21, -2), "yaw": 180, "pitch": 6,
         "scale": 30},
        {"label": "重鎧の上に着けたとき", "pairs": [(man, mtex), (AA.subset(plate, chest), ptex)] + on, "center": (0, 15, 0),
         "yaw": -150, "pitch": 10, "scale": 13},
        {"label": "法衣の上に着けたとき", "pairs": [(man, mtex), (AA.subset(robe, chest), rtex)] + on, "center": (0, 15, 0),
         "yaw": 150, "pitch": 10, "scale": 13},
        {"label": "腕輪とお守り（前から）", "pairs": [(man, mtex)] + off, "center": (0, 11.5, -1), "yaw": 165, "pitch": 10,
         "scale": 15},
    ]
    R = RAMPS
    palette = [("ツノモフの毛の紐・柔角・緑の玉", [R["wool"][4], R["wool"][5], (124, 150, 92), R["bone"][4], R["bone"][5],
                                         (66, 130, 78), (140, 204, 140)]),
               ("石くれの石・苔", [R["stone"][i] for i in (2, 3, 4, 5, 6)] + [(75, 97, 64)]),
               ("苔鉄の輪", [R["iron"][i] for i in (2, 3, 4, 5)])]
    sheet_path = os.path.join(repo, "docs", "gear", "accessories_sheet.png")
    make_set_sheet(sheet_path, "⑥ 体に表示するアクセサリー（1階）",
                   "アンコモン：光らない・動かない｜胸の防具を着ているときは、防具の上に見えるように少し外へ広げて描く",
                   rows, details=details, palette=palette)
    import shutil
    shutil.copy(sheet_path, os.path.join(out, "accessories_sheet.png"))


if __name__ == "__main__":
    main()
