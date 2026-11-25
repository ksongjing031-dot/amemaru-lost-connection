"""AMEMARU: LOST CONNECTION の Phase 6 の品の絵（16×16）とモデルを作る。

使い方: python3 tools/artgen/make_phase6_items.py [assets/amemaru のフォルダ]

作るもの：
  textures/item/healing_potion_small.png   回復薬（小）：小瓶に赤い薬、コルクの栓、白い札に赤い十字
  textures/item/mana_water_small.png       マナ水（小）：同じ小瓶に青い水と、中の小さな光の粒、札に青い星（真ん中が白い）
  textures/item/market_notebook.png        市場の手帳：革の表紙に金の硬貨の紋、紙の小口、赤いしおり
  models/item/healing_potion_small.json・mana_water_small.json・market_notebook.json
忘却の水（丸いフラスコに水色）と見分けやすいよう、角ばった小瓶で、色を濃くする【補完】。
"""
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"

SHAPE = [
    "................",
    "......oooo......",
    ".....occCCo.....",
    ".....occCCo.....",
    "......oooo......",
    "......ogGo......",
    ".....ogGGGo.....",
    "....oHLLLLDo....",
    "...oHLLLLLLDo...",
    "...oHtttttLDo...",
    "...oLtttttLDo...",
    "...oLtttttDDo...",
    "...oLtttttDDo...",
    "...oDtttttDDo...",
    "....oDDDDDDo....",
    ".....oooooo.....",
]

COMMON = {
    "o": "#2A2520",
    "c": "#C08A50",
    "C": "#7A5230",
    "g": "#FFFFFF",
    "G": "#CFE8F2",
    "t": "#F2E6C8",
}
KINDS = {
    "healing_potion_small": {"L": "#D8303A", "D": "#8E1B26", "H": "#FF8C8C", "m": "#D8303A", "sparkle": None},
    "mana_water_small": {"L": "#3A6FE0", "D": "#1D3A8E", "H": "#8CC4FF", "m": "#3A6FE0", "sparkle": [(10, 13), (9, 8), (5, 7)]},
}


def rgba(text, alpha=255):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def draw(kind):
    colors = dict(COMMON)
    colors.update({k: v for k, v in KINDS[kind].items() if k != "sparkle"})
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SHAPE):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(colors[ch]))
    # 札の印（5×5 の札の真ん中に、十字か星）：回復薬は赤い十字、マナ水は青い星（真ん中が白く光る）
    mark = rgba(colors["m"])
    for (x, y) in ((7, 10), (6, 11), (7, 11), (8, 11), (7, 12)):
        img.putpixel((x, y), mark)
    if kind == "mana_water_small":
        img.putpixel((7, 11), rgba("#E2FAFF"))
    for (x, y) in KINDS[kind]["sparkle"] or ():
        img.putpixel((x, y), rgba("#E2FAFF"))
    return img


# 市場の手帳：革の表紙の小さな手帳（左に背）、表に金の硬貨の紋、右と下に紙の小口、赤いしおりのひも【補完】
NOTEBOOK = [
    "................",
    "..ooooooooooo...",
    ".oSScccccccccoo.",
    ".oSScCCCCCCCcPo.",
    ".oSScCCCGGCCcPo.",
    ".oSScCCGggGCcPo.",
    ".oSScCCGgYGCcPo.",
    ".oSScCCGggGCcPo.",
    ".oSScCCCGGCCcPo.",
    ".oSScCCCCCCCcPo.",
    ".oSScCCCCCCCcPo.",
    ".oSSDDDDDDDDDPo.",
    ".oSSPPPPPPPPPPo.",
    "..oooooooRoooo..",
    ".........R......",
    ".........r......",
]
NOTEBOOK_COLORS = {
    "o": "#2A2520", "S": "#5A3418", "c": "#A8743A", "C": "#8B5A2B", "D": "#6E4420", "P": "#F2E6C8",
    "G": "#B08A2E", "g": "#E8C25A", "Y": "#FFF2A8", "R": "#C8303A", "r": "#8E1B26",
}


def draw_notebook():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(NOTEBOOK):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(NOTEBOOK_COLORS[ch]))
    return img


def main():
    path = os.path.join(ROOT, "textures", "item", "market_notebook.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    draw_notebook().save(path)
    with open(os.path.join(ROOT, "models", "item", "market_notebook.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/market_notebook"}}, f, indent=2)
        f.write("\n")
    print("作りました:", path)
    for kind in KINDS:
        path = os.path.join(ROOT, "textures", "item", kind + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        draw(kind).save(path)
        model = os.path.join(ROOT, "models", "item", kind + ".json")
        with open(model, "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + kind}}, f, indent=2)
            f.write("\n")
        print("作りました:", path)


if __name__ == "__main__":
    main()
