"""11月23日の直し（店の品を実用的なものにする）で増やした品の絵（16×16）とモデルを作る。

「色々な店があるがどこで使うかわからないものが多いので実用的なものを置く」のご指示で増やした品：

  healing_potion_medium  回復薬（中）：回復薬（小）より大きな丸い瓶に赤い薬、首に金の輪
  mana_water_medium      マナ水（中）：同じ瓶に青い水と光の粒、首に金の輪
  herb_tea               香草茶：白い陶器の茶碗に緑の茶、湯気
  return_feather         帰還の羽根：白い羽根の先が青く光る（塔の門の光の色）
  repair_kit             携帯修理具：革の小袋から、小さな金づちの頭がのぞく
  hand_lantern           手提げランタン：真鍮の枠に、あたたかい灯り
  lantern_vial           灯りの小瓶：丸い小瓶に、光の粒の入った明るい黄色

ここでは16×16の形を描き、そのあと tools/artgen/enhance_icons.py で32×32の細かい絵にする（ほかの持ち物の絵と同じ）。

  python3 tools/artgen/make_fix_items.py && python3 tools/artgen/enhance_icons.py . --only healing_potion_medium,...
"""
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"

BOTTLE = [
    "................",
    "......oooo......",
    ".....occCCo.....",
    "......oooo......",
    "......oYYo......",
    ".....ogGGgo.....",
    "...ooHLLLLDoo...",
    "..oHLLLLLLLLDo..",
    ".oHLLmLLLLLLLDo.",
    ".oHLLLLtttLLLDo.",
    ".oLLLLtttttLLDo.",
    ".oLLLLLtttLLDDo.",
    ".oDLLLLLLLLLDDo.",
    "..oDDLLLLLLDDo..",
    "...ooDDDDDDoo...",
    ".....oooooo.....",
]
BOTTLE_COMMON = {"o": "#2A2520", "c": "#B07A46", "C": "#8A5A2E", "g": "#BFD8E0", "G": "#E8F4F8", "Y": "#E3BD5A",
                 "t": "#F2EEE4"}

TEA = [
    "................",
    "....s..s........",
    ".....s..s.......",
    "....s..s........",
    "................",
    "..oooooooooo....",
    ".oWWWWWWWWWWo...",
    ".oWggGGGGggWoooo",
    ".oWgGGGGGGgWo..o",
    ".oWWggggggWWo..o",
    "..oWWWWWWWWoooo.",
    "..oWWWWWWWWo....",
    "...oWWWWWWo.....",
    ".oooooooooooo...",
    ".oDDDDDDDDDDo...",
    "..oooooooooo....",
]
TEA_PAL = {"o": "#3A3330", "W": "#F2EEE6", "D": "#C9BFB0", "g": "#6E9A3A", "G": "#94C04E", "s": "#D8E2E6"}

FEATHER = [
    "................",
    "............obo.",
    "...........obBo.",
    "..........obBbo.",
    ".........owBbo..",
    "........owWwo...",
    ".......owWwo....",
    "......owWwWo....",
    ".....owWwWo.....",
    "....owWwWo......",
    "...owWwWo.......",
    "...oWwWo........",
    "..oWwo..........",
    "..qo............",
    ".q..............",
    "q...............",
]
FEATHER_PAL = {"o": "#4A5566", "w": "#DDE6EE", "W": "#FFFFFF", "b": "#6CC8F0", "B": "#B8ECFF", "q": "#8A7A60"}

KIT = [
    "................",
    "........oooo....",
    ".......oSSSSo...",
    ".......oShhSo...",
    "........oSSo....",
    "....ooo..oho....",
    "...oLLLooohoo...",
    "..oLlllLLLLLLo..",
    "..oLlLLLLLLLLo..",
    "..oLLLLLbbLLLo..",
    "..oLLLLLbYLLLo..",
    "..oLLLLLLLLLLo..",
    "..oDLLLLLLLLDo..",
    "...oDDDDDDDDo...",
    "....oooooooo....",
    "................",
]
KIT_PAL = {"o": "#2E2218", "L": "#8E5A32", "l": "#B07A46", "D": "#5E3A20", "S": "#B8C0C8", "h": "#7A858F",
           "b": "#3A2A1C", "Y": "#E3BD5A"}

LANTERN = [
    "................",
    "......oooo......",
    ".....o....o.....",
    "......oooo......",
    ".....oBBBBo.....",
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
LANTERN_PAL = {"o": "#3A2C18", "B": "#B8923A", "D": "#7A5E26", "y": "#F2C060", "1": "#FFE08A", "W": "#FFF8E0"}

VIAL = [
    "................",
    "................",
    "......oooo......",
    "......occo......",
    "......oooo......",
    ".......oo.......",
    ".....oo11oo.....",
    "....o1yyyy1o....",
    "...o1yyWWyy1o...",
    "...oyyWWWWyyo...",
    "...oyyWWWWyyo...",
    "...o1yyWWyyDo...",
    "....oDyyyyDo....",
    ".....ooDDoo.....",
    ".......oo.......",
    "................",
]
VIAL_PAL = {"o": "#4A3C20", "c": "#B07A46", "1": "#FFF4C0", "y": "#FFD84A", "W": "#FFFFF0", "D": "#D8A82A"}


def rgba(text):
    text = text.lstrip("#")
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def paint(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(pal[ch]))
    return img


def bottle(liquid, light, dark, sparkle):
    pal = dict(BOTTLE_COMMON)
    pal.update({"L": liquid, "H": light, "D": dark, "m": sparkle})
    return paint(BOTTLE, pal)


ITEMS = {
    "healing_potion_medium": lambda: bottle("#D8303A", "#FF8C8C", "#8E1B26", "#FFC0C0"),
    "mana_water_medium": lambda: bottle("#2F6FD8", "#8CC0FF", "#1B3F8E", "#FFFFFF"),
    "herb_tea": lambda: paint(TEA, TEA_PAL),
    "return_feather": lambda: paint(FEATHER, FEATHER_PAL),
    "repair_kit": lambda: paint(KIT, KIT_PAL),
    "hand_lantern": lambda: paint(LANTERN, LANTERN_PAL),
    "lantern_vial": lambda: paint(VIAL, VIAL_PAL),
}


def main():
    for name, make in ITEMS.items():
        path = os.path.join(ROOT, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        make().save(path)
        model = os.path.join(ROOT, "models", "item", name + ".json")
        os.makedirs(os.path.dirname(model), exist_ok=True)
        with open(model, "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, f, indent=2)
            f.write("\n")
        print(path)


if __name__ == "__main__":
    main()
