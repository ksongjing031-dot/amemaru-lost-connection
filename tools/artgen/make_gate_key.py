"""5階のユニーク「第七の門鍵」の、体に表示するモデル・動き・持ち物の絵（11月24日）。

Phase 0-A 23章（確定）：守護者の記録核「第七の門鍵」（お守り）。小さな立方体の翠の結晶。まわりを細い環が回る。

  python3 tools/artgen/make_gate_key.py <リポジトリ> [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only seventh_gate_key

ユニークの決まり（docs/gear/README.md「レアリティの見せ方」）：専用のモデルと演出。
  - 左の腰から下がる真鍮の鎖の先に、角を下にした翠の立方体（記録核。光る）
  - そのまわりを、真鍮の細い環が傾いたまま回り続ける（4秒で1回り。環のボーン key_ring を動かす）
  - 立方体も、ゆっくり上下する（記録核が息をしているように）【補完】
持ち物の絵：斜め上から見た翠の立方体（3つの面の明るさを変える）と、それを斜めに囲む金の環。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from make_gear_accessories import armored, gcube  # noqa: E402
from make_gear_ancient_armor import ARMOR_BONES  # noqa: E402
from make_floor_accessory_art import FloorAccessoryPainter, hip_cord, ICONS  # noqa: E402
from materials import hexc  # noqa: E402

NAME = "seventh_gate_key"
CX, CY, CZ = -4.0, 7.6, 0.0      # 記録核のまん中（ゲームの中の向き：体の右が +x、表が −z）
RING_R = 2.6
RING_TILT = 24.0


def build():
    g = GeoModel(NAME, 64, 64)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    # ボーンの pivot は Bedrock の向き（x を反転）
    g.bone("key_core", (-CX, CY, CZ), parent="armorBody")
    g.bone("key_ring", (-CX, CY, CZ), parent="armorBody")
    hip_cord(g, "brass", "brass", x=-4.5)
    # 鎖の先の留め金（立方体の上の角をつかむ）
    gcube(g, "armorBody", "bail", (CX - 0.5, CY + 1.35, CZ - 0.5), (1, 1, 1), "brass", inflate=-0.2)
    # 記録核：角を下にした翠の立方体（Y に45度・X に35度まわす）。光る
    gcube(g, "key_core", "core", (CX - 1, CY - 1, CZ - 1), (2, 2, 2), "jade", inflate=-0.05,
          rot=(35.26, 45, 0), pivot=(CX, CY, CZ), glow=True)
    # 中の光の糸の結び目（いちばん明るい粒）
    gcube(g, "key_core", "knot", (CX - 0.5, CY - 0.5, CZ - 0.5), (1, 1, 1), "jade", inflate=0.12,
          rot=(35.26, 45, 0), pivot=(CX, CY, CZ), glow=True, tags={"bright": True})
    # 細い真鍮の環（12の部品で、水平な輪。ボーンを傾けて回す）
    import math
    for i in range(12):
        a = 2 * math.pi * i / 12
        px = CX + RING_R * math.cos(a)
        pz = CZ + RING_R * math.sin(a)
        deg = -math.degrees(a) + 90.0
        gcube(g, "key_ring", "ring_%d" % i, (px - 1.0, CY - 0.5, pz - 0.5), (2, 1, 1), "brass", inflate=-0.32,
              rot=(0, deg, 0), pivot=(px, CY, pz))
    return g


def animations():
    ring = {"0.0": [RING_TILT, 0, 0], "1.0": [RING_TILT, 90, 0], "2.0": [RING_TILT, 180, 0],
            "3.0": [RING_TILT, 270, 0], "4.0": [RING_TILT, 360, 0]}
    bob = {"0.0": [0, 0, 0], "2.0": [0, 0.25, 0], "4.0": [0, 0, 0]}
    return {"animation.%s.idle" % NAME: {
        "loop": True, "animation_length": 4.0,
        "bones": {"key_ring": {"rotation": ring, "position": bob}, "key_core": {"position": bob}}}}


class GateKeyPainter(FloorAccessoryPainter):
    def paint_jade(self, c):
        """深い翠の面に、まん中の光の糸の結び目（十字）。面のふちは暗く、立方体の角が分かるように。"""
        from make_floor_accessory_art import LOCAL
        from materials import pick
        if c.el.tags.get("bright"):
            return (200, 246, 220, 255)
        tone = 2.7 + self.bevel(c, up=1.2, down=0.9)
        edge = min(c.i, c.j, c.W - 1 - c.i, c.H - 1 - c.j)
        if edge == 0:
            tone -= 0.8
        if c.W >= 2 and c.H >= 2 and (c.i == c.W // 2 or c.j == c.H // 2) and edge > 0:
            tone += 1.6
        return pick(LOCAL["jade"], tone)


# ---------------------------------------------------------------- 持ち物の絵（16×16。enhance_icons.py で 32×32 にする）

ICONS[NAME] = ([
    "................",
    "................",
    ".....rRRRRRr....",
    "...RR..jj..RR...",
    "..R...jjjj...R..",
    ".R..jjjWjjjj..R.",
    ".R..KjjjjjjM..R.",
    "R...KKKjjMMM...R",
    "R...KKKKMMMM...R",
    ".RR.KKKKMMMM.RR.",
    "...RRRKKMMRRR...",
    "....KKRRRRMM....",
    "......KKMM......",
    "................",
    "................",
    "................",
], {"R": "#E8B23C", "r": "#A36F16", "j": "#86E2AE", "W": "#F2FFF8", "K": "#1A7A52", "M": "#0B3A24"})


def paint_icon():
    from PIL import Image
    rows, pal = ICONS[NAME]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*hexc(pal[ch]), 255))
    return img


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    repo = args[0] if args else "."
    out = args[1] if len(args) > 1 else "/tmp/gate_key"
    os.makedirs(out, exist_ok=True)
    g = build()
    g.pack()
    frames = g.paint(GateKeyPainter(g), frames=1)
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    if "--sheets" not in sys.argv:
        g.save(repo, "accessory", frames, animations=animations())
        paint_icon().save(os.path.join(base, "textures/item", NAME + ".png"))
    sheet(repo, out, g, frames)
    print("第七の門鍵：モデル・動き・持ち物の絵")


def sheet(repo, out, g, frames):
    import make_gear_ancient_armor as AA
    from setsheet import make_set_sheet
    from make_floor_accessory_art import disk_icon
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    plate = AA.build_plate()
    ptex = AA.paint(plate)[0]
    chest = AA.PIECE_BONES["chest"] + AA.PIECE_BONES["legs"]
    rows = [{"label": "第七の門鍵", "rarity": "ユニーク（専用のモデル・光る核・回る環）", "rarity_color": (255, 120, 120),
             "note": "左の腰の真鍮の鎖の先に、角を下にした\n翠の立方体（記録核）。まわりを細い\n真鍮の環が傾いたまま回る（4秒で1回り）",
             "pairs": [(man, mtex), (g, frames[0])], "icon": disk_icon(repo, NAME),
             "views": [("正面", 180, 0), ("左の横", 90, 0), ("斜め", 145, 14), ("後ろ", 0, 0)]}]
    details = [
        {"label": "近くで（環は止めた形）", "pairs": [(g, frames[0])], "center": (-CX, CY, CZ), "yaw": 150, "pitch": 18, "scale": 46},
        {"label": "重鎧の上に着けたとき", "pairs": [(man, mtex), (AA.subset(plate, chest), ptex), (armored(g, True), frames[0])],
         "center": (0, 12, 0), "yaw": 140, "pitch": 10, "scale": 13},
    ]
    path = os.path.join(repo, "docs", "gear", "seventh_gate_key_sheet.png")
    make_set_sheet(path, "第七の門鍵（守護者の記録核）", "Phase 0-A 23章：小さな立方体の翠の結晶。まわりを細い環が回る", rows, details=details)
    import shutil
    shutil.copy(path, os.path.join(out, os.path.basename(path)))


if __name__ == "__main__":
    main()
