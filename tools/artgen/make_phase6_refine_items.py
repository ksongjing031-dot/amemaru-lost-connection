"""AMEMARU: LOST CONNECTION の Phase 6 その4 の厳選の素材の絵（16×16）とモデルを作る。

使い方: python3 tools/artgen/make_phase6_refine_items.py [assets/amemaru のフォルダ] [--preview 見本の画像]

作るもの（Phase 0-A 21章「厳選素材（世界観に合わせた名前）」）：
  textures/item/polishing_powder.png   研磨粉：結晶を磨く粉。淡い藤色の粉の小山と、きらめき
  textures/item/binding_thread.png     留め糸：響きを一つだけ結び留める細い糸。金色の糸の蝶結び
  textures/item/wavering_crystal.png   揺らぎの結晶：眠っている響きを揺り起こす結晶。青緑と紫の揺らぐ縞の結晶
  textures/item/clear_echo.png         澄んだ残響：響きの濁りがない上等のかけら。透きとおった白青の欠片と、まわりの淡い光
  models/item/<同じ名前>.json
残響の欠片（echo_fragment）は Phase 3 の絵のまま。
"""
import json
import os
import sys

from PIL import Image

ROOT = "src/main/resources/assets/amemaru"
PREVIEW = None
args = sys.argv[1:]
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
if args:
    ROOT = args[0]

ITEMS = {
    "polishing_powder": {
        "palette": {"o": "#2A2638", "p": "#C9C0E0", "P": "#9F95C0", "D": "#7C7299", "w": "#ECE8F8", "W": "#FFFFFF",
                    "c": "#8FD3FF"},
        "rows": [
            "................",
            "................",
            "..W.............",
            "............c...",
            "......W.........",
            ".......o........",
            "......owo.......",
            ".....owwpo...W..",
            "....owwppPo.....",
            "...owwpWpPPo....",
            "..owwppppPPPo...",
            ".owppPppcpPPDo..",
            "owpPPPpPPPPDDDo.",
            "oDDDDDDDDDDDDDo.",
            ".ooooooooooooo..",
            "................",
        ],
    },
    "binding_thread": {
        "palette": {"o": "#3A2610", "G": "#D9A23A", "g": "#B07A22", "y": "#FFE08A", "W": "#FFF6D6"},
        "rows": [
            "................",
            "..ooo......ooo..",
            ".oyGGo....oGGyo.",
            "oyG..Go..oG..Gyo",
            "oG....GooG....Go",
            "oG....oyyo....Go",
            "oGg..oyWWyo..gGo",
            ".oGGGGyWWyGGGGo.",
            "..oooooyyooooo..",
            "......oGGo......",
            ".....oGgoGo.....",
            "....oGgo.oGo....",
            "...oGgo...oGo...",
            "..oGgo.....oGo..",
            "..ooo.......oo..",
            "................",
        ],
    },
    "wavering_crystal": {
        "palette": {"o": "#1A2030", "t": "#5FE0D0", "T": "#2FA89C", "v": "#B48CFF", "V": "#7A58C8", "w": "#E8FFFF",
                    "W": "#FFFFFF"},
        "rows": [
            "................",
            ".......oo.......",
            "......owto......",
            ".....owttTo.....",
            ".....owtvTo.....",
            "....owtvvTTo....",
            "....owvvtTVo....",
            "....owtvvtTo..W.",
            "....owvttvVo....",
            "....ovtvvtVo....",
            "....owvtvvVo....",
            "..W..ovttVo.....",
            ".....ovvVVo.....",
            "......oVVo......",
            ".......oo.......",
            "................",
        ],
    },
    "clear_echo": {
        "palette": {"o": "#1A2633", "w": "#F2FBFF", "b": "#BFE6FF", "B": "#8CC8F0", "s": "#FFFFFF",
                    "h": "#DFF4FF80", "H": "#DFF4FF40"},
        "rows": [
            "................",
            ".......s........",
            "......HoH.......",
            ".....Howoh......",
            "....Howwbo......",
            "...Howwbbbo.....",
            "..howwwbbBBo....",
            ".showwbbbBBos...",
            "..howbbbBBBoh...",
            "...obbbBBBoH....",
            "....obbBBoH.....",
            ".....oBBoH......",
            "......ooH.......",
            ".......s........",
            "................",
            "................",
        ],
    },
}


def color(hex_code):
    h = hex_code.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    a = int(h[6:8], 16) if len(h) == 8 else 255
    return r, g, b, a


def draw(spec):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    palette = {k: color(v) for k, v in spec["palette"].items()}
    rows = spec["rows"]
    assert len(rows) == 16, "16行にしてください"
    for y, row in enumerate(rows):
        assert len(row) == 16, "16文字にしてください: %r" % row
        for x, ch in enumerate(row):
            if ch != ".":
                pixels[x, y] = palette[ch]
    return img


def main():
    images = {}
    for name, spec in ITEMS.items():
        img = draw(spec)
        images[name] = img
        path = os.path.join(ROOT, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        model = os.path.join(ROOT, "models", "item", name + ".json")
        os.makedirs(os.path.dirname(model), exist_ok=True)
        with open(model, "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, f, indent=2)
            f.write("\n")
    if PREVIEW:
        scale = 16
        sheet = Image.new("RGBA", (len(images) * (16 * scale + 16) + 16, 16 * scale + 32), (40, 46, 60, 255))
        for i, img in enumerate(images.values()):
            sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (16 + i * (16 * scale + 16), 16))
        sheet.save(PREVIEW)
    print("書き出した:", ", ".join(images))


if __name__ == "__main__":
    main()
