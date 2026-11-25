"""AMEMARU: LOST CONNECTION の Phase 6 その3 の品の絵（16×16）とモデルを作る。

使い方: python3 tools/artgen/make_phase6_guild_items.py [assets/amemaru のフォルダ] [--preview 見本の画像]

作るもの：
  textures/item/record_book.png        記録帳：青い石板色の革の表紙、真鍮の角、真ん中に結晶の結び目の紋、右と下に紙の小口
  textures/item/keepsake_lilia.png     リリアゆかりの小物：羊歯の葉のブローチ（金の縁、象牙色の地に緑の葉）
  textures/item/keepsake_gard.png      ガルドゆかりの小物：ワタリガラスの羽根と、旧騎士団の深紅のリボン
  textures/item/keepsake_doran.png     ドランゆかりの小物：革ひもを通した、小さな金槌のお守り
  textures/item/keepsake_serena.png    セレナゆかりの小物：星の刺繍のリボンを結んだ、小さな銀の鈴
  textures/item/keepsake_ernesto.png   エルネストゆかりの小物：真鍮の丸眼鏡のレンズ（鎖つき）
  textures/item/keepsake_noa.png       ノアゆかりの小物：白灰と淡金の組紐に、夜空の紺の玉
  models/item/<同じ名前>.json
小物の形は、Phase 0-A 8章のNPCの見た目（羊歯の葉のブローチ・ワタリガラス・古い金槌・小さな銀の鈴・丸眼鏡・白灰と淡金と夜空の紺）から
選んだ【提案】。
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
    "record_book": {
        "palette": {"o": "#1E1A16", "S": "#2A3442", "b": "#3E5470", "B": "#C9A24A", "p": "#E8DDBF", "P": "#BFAF8A",
                    "c": "#5DA9FF", "C": "#8FD3FF", "W": "#E2FAFF", "L": "#C9A24A"},
        "rows": [
            "................",
            "...ooooooooooo..",
            "..oSSBbbbbbbBpo.",
            "..oSSbbbbbbbbpo.",
            "..oSSbbbbcbbbpo.",
            "..oSSbbbcCcbbpo.",
            "..oSSbbcCWCcbpo.",
            "..oSSbbbcCcbbpo.",
            "..oSSbbbbcbbbpo.",
            "..oSSbbbbbbbbpo.",
            "..oSSbLLLLLLbpo.",
            "..oSSbbbbbbbbpo.",
            "..oSSBbbbbbbBpo.",
            "..oSSppppppppPo.",
            "...ooooooooooo..",
            "................",
        ],
    },
    "keepsake_lilia": {
        "palette": {"o": "#5A4318", "Y": "#E2B64A", "y": "#F4EBD6", "L": "#2F7A3A", "G": "#6CC46A"},
        "rows": [
            "................",
            ".....oooooo.....",
            "...ooYYYYYYoo...",
            "..oYYyyyyyyYYo..",
            "..oYyyyyyyGyYo..",
            ".oYyyyyyyGLyyYo.",
            ".oYyyyyGLyLyyYo.",
            ".oYyyyLyGLyyyYo.",
            ".oYyyLGLyLyyyYo.",
            ".oYyyyLyGyyyyYo.",
            "..oYyGLyLyyyYo..",
            "..oYyyLyyyyyYo..",
            "...ooYYYYYYoo...",
            ".....oooooo.....",
            "................",
            "................",
        ],
    },
    "keepsake_gard": {
        "palette": {"k": "#1A1C24", "K": "#3C4A6E", "s": "#5A6A90", "r": "#8E1B26", "R": "#C8323C", "q": "#D9D2C0"},
        "rows": [
            "................",
            "...........kk...",
            "..........kKKk..",
            ".........kKsKk..",
            "........kKsKkk..",
            ".......kKsKkk...",
            "......kKsKkk....",
            ".....kKsKkk.....",
            "....kKsKkk......",
            "...kKsKkk.......",
            "...kksKk........",
            "..rRrkk.........",
            ".rRRrr..........",
            "..rr.r..........",
            ".q..............",
            "q...............",
        ],
    },
    "keepsake_doran": {
        "palette": {"i": "#3A3E46", "I": "#7C8490", "w": "#C8D0DA", "h": "#6B4426", "H": "#9A6A3E", "c": "#8A5A32"},
        "rows": [
            "................",
            "..ccc...........",
            ".c...c..........",
            ".c....c.........",
            "..c....iiiii....",
            "...c..iIIIIIi...",
            "....c.iIwwIIi...",
            ".....ciIIIIIi...",
            "......iiihii....",
            "........hH......",
            ".......hH.......",
            "......hH........",
            ".....hH.........",
            "....hH..........",
            "...hh...........",
            "................",
        ],
    },
    "keepsake_serena": {
        "palette": {"o": "#3A4250", "S": "#C9D1DC", "W": "#F4F8FF", "D": "#8D97A6", "r": "#E8EEF8", "R": "#B8C6E0",
                    "*": "#F2C94C"},
        "rows": [
            "................",
            "......rrrr......",
            ".....rR*Rrr.....",
            "......rrrr......",
            ".......oo.......",
            "......oSSo......",
            ".....oSWSSo.....",
            ".....oSWSSo.....",
            "....oSWSSSSo....",
            "....oSSSSSSo....",
            "...oSSSSSSSSo...",
            "...oDDDDDDDDo...",
            "....oooDDooo....",
            ".......oo.......",
            "................",
            "................",
        ],
    },
    "keepsake_ernesto": {
        "palette": {"o": "#4A3814", "B": "#C9A24A", "g": "#9FD4E0", "W": "#FFFFFF", "c": "#A8843A"},
        "rows": [
            "................",
            "....oooooo......",
            "...oBBBBBBo.....",
            "..oBgggggBBo....",
            "..oBgWggggBo....",
            "..oBgWggggBo....",
            "..oBggggggBo....",
            "..oBggggggBo....",
            "..oBBggggBBo....",
            "...oBBBBBBo.....",
            "....oooooo.c....",
            "..........c.c...",
            "...........c..c.",
            "..............c.",
            "...............c",
            "................",
        ],
    },
    "keepsake_noa": {
        "palette": {"w": "#E6E8EE", "p": "#E8D08A", "n": "#1D2A55", "N": "#3A4F9A"},
        "rows": [
            "................",
            ".....wpwpwp.....",
            "...pw......wp...",
            "..w..........p..",
            ".p............w.",
            ".w............p.",
            "p..............w",
            "w..............p",
            "p..............w",
            ".w............p.",
            ".p............w.",
            "..w....nn....p..",
            "...pw.nNNn.wp...",
            ".....wnNNnw.....",
            ".......nn.......",
            "................",
        ],
    },
}


def rgba(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def draw(spec):
    rows = spec["rows"]
    assert len(rows) == 16, len(rows)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(spec["palette"][ch]))
    return img


def main():
    textures = os.path.join(ROOT, "textures", "item")
    models = os.path.join(ROOT, "models", "item")
    os.makedirs(textures, exist_ok=True)
    os.makedirs(models, exist_ok=True)
    images = []
    for name, spec in ITEMS.items():
        img = draw(spec)
        img.save(os.path.join(textures, name + ".png"))
        with open(os.path.join(models, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, f, indent=2)
            f.write("\n")
        images.append(img)
    if PREVIEW:
        scale = 8
        sheet = Image.new("RGBA", (len(images) * (16 * scale + 8) + 8, 16 * scale + 16), (40, 44, 60, 255))
        for i, img in enumerate(images):
            sheet.paste(img.resize((16 * scale, 16 * scale), Image.NEAREST), (8 + i * (16 * scale + 8), 8), img.resize((16 * scale, 16 * scale), Image.NEAREST))
        sheet.save(PREVIEW)
    print("made", ", ".join(ITEMS))


if __name__ == "__main__":
    main()
