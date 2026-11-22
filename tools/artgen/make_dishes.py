"""Phase 7 その4：ペトラ・ポムの料理屋の料理の絵（32×32。ほかの持ち物と同じ細かさ）を描く。

使い方: python3 tools/artgen/make_dishes.py [--preview 出力.png]
作るもの：textures/item/dish/<名前>.png と、models/item/<名前>.json
"""
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = "src/main/resources/assets/amemaru"
OUT = os.path.join(ROOT, "textures", "item", "dish")


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def plate(d, color="#F4F1EA", rim="#C9C2B4", shadow="#9E968A", y0=12, y1=29):
    d.ellipse((2, y0 + 1, 29, y1 + 1), fill=rgb(shadow))
    d.ellipse((2, y0, 29, y1), fill=rgb(rim))
    d.ellipse((4, y0 + 2, 27, y1 - 2), fill=rgb(color))


def bowl(d, color="#8A5A34", dark="#5E3B21", y0=14):
    d.ellipse((3, y0, 28, y0 + 8), fill=rgb(dark))
    d.chord((3, y0 - 6, 28, y0 + 16), 0, 180, fill=rgb(color))
    d.ellipse((3, y0, 28, y0 + 8), fill=rgb(dark))


def fish(d, x0, y0, x1, y1, body, back, belly, eye="#202020", tail=True, flip=False):
    w = x1 - x0
    h = y1 - y0
    if tail:
        tx = x1 - 1 if not flip else x0 + 1
        d.polygon([(tx, y0 + h // 2), (tx + (5 if not flip else -5), y0 - 1), (tx + (5 if not flip else -5), y1 + 1)], fill=rgb(back))
    d.ellipse((x0, y0, x1 - (3 if not flip else 0) + (0 if not flip else 3), y1), fill=rgb(body))
    d.chord((x0, y0, x1, y1 + h // 2), 180, 360, fill=rgb(back))
    d.chord((x0 + 2, y0 + h // 2, x1 - 2, y1), 0, 180, fill=rgb(belly))
    ex = x0 + 3 if not flip else x1 - 4
    d.point((ex, y0 + h // 2 - 1), fill=rgb(eye))
    return w


def grilled_fish(d):
    plate(d)
    fish(d, 6, 15, 23, 23, "#C9A27A", "#8C6A48", "#E8D2B4")
    for x in (10, 14, 18):
        d.line((x, 16, x - 2, 22), fill=rgb("#4A3322"))
    d.ellipse((22, 22, 27, 26), fill=rgb("#F2D64B"))      # 檸檬
    d.point((24, 23), fill=rgb("#FFF4A0"))


def fish_soup(d):
    bowl(d)
    d.ellipse((5, 15, 26, 21), fill=rgb("#E8B46A"))
    for (x, y, c) in ((9, 17, "#F4F1EA"), (14, 16, "#E07A3A"), (19, 18, "#F4F1EA"), (12, 19, "#7CB04A"), (21, 16, "#E07A3A")):
        d.rectangle((x, y, x + 1, y + 1), fill=rgb(c))
    for x, h in ((10, 5), (16, 7), (21, 4)):                   # 湯気
        d.line((x, 12 - h, x + 1, 12), fill=(255, 255, 255, 150))


def herb_trout(d):
    plate(d, color="#EEF2E8")
    fish(d, 6, 15, 24, 23, "#B9C4C8", "#6F8A94", "#E4EAEA")
    for (x, y) in ((9, 17), (13, 18), (17, 17), (20, 19), (11, 21)):
        d.rectangle((x, y, x + 1, y), fill=rgb("#3E8A3A"))
    d.rectangle((24, 22, 26, 23), fill=rgb("#B83A5A"))         # ビーツ
    d.rectangle((23, 24, 25, 25), fill=rgb("#8E2A46"))


def simmered_fish(d):
    bowl(d, color="#3A3A44", dark="#26262E")
    d.ellipse((5, 15, 26, 21), fill=rgb("#6A3A1E"))
    fish(d, 8, 15, 22, 20, "#8A4A22", "#5A2E14", "#B06A3A", tail=True)
    d.point((12, 16), fill=rgb("#F0C080"))
    d.point((16, 17), fill=rgb("#F0C080"))


def honey_glazed(d):
    plate(d, color="#F6EFD8")
    fish(d, 6, 15, 24, 23, "#E6A93A", "#B87820", "#F6D27A")
    for (x, y) in ((10, 23), (15, 24), (19, 23)):
        d.line((x, y, x, y + 2), fill=rgb("#F2B830"))          # はちみつのしずく
    d.point((12, 17), fill=rgb("#FFF0B0"))
    d.point((17, 16), fill=rgb("#FFF0B0"))


def lake_feast(d):
    d.ellipse((1, 13, 30, 30), fill=rgb("#7A5A3A"))           # 木の大皿
    d.ellipse((3, 14, 28, 28), fill=rgb("#A67C52"))
    fish(d, 5, 15, 21, 22, "#D69A5A", "#9A6A38", "#F0CFA0")
    d.rectangle((19, 21, 25, 25), fill=rgb("#C98A3E"))         # パン
    d.line((20, 22, 24, 22), fill=rgb("#E8B470"))
    d.ellipse((7, 22, 12, 27), fill=rgb("#C8302E"))            # りんご
    d.point((9, 22), fill=rgb("#4A8A3A"))


DISHES = {
    "dish_grilled_fish": grilled_fish,
    "dish_fish_soup": fish_soup,
    "dish_herb_trout": herb_trout,
    "dish_simmered_fish": simmered_fish,
    "dish_honey_glazed": honey_glazed,
    "dish_lake_feast": lake_feast,
}


def build():
    os.makedirs(OUT, exist_ok=True)
    paths = []
    for name, draw in DISHES.items():
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        draw(ImageDraw.Draw(img))
        # 皿は下に寄せて描いたので、持ち物の枠の真ん中に見えるよう4つ上へずらす
        img = img.transform((32, 32), Image.AFFINE, (1, 0, 0, 0, 1, 4), resample=Image.NEAREST)
        path = os.path.join(OUT, name + ".png")
        img.save(path)
        paths.append(path)
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/dish/" + name}}
        with open(os.path.join(ROOT, "models", "item", name + ".json"), "w", encoding="utf-8") as f:
            f.write(json.dumps(model, indent=2) + "\n")
    return paths


if __name__ == "__main__":
    written = build()
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (len(written) * 136, 136), (60, 70, 85, 255))
        for i, p in enumerate(written):
            sheet.paste(Image.open(p).resize((128, 128), Image.NEAREST), (i * 136 + 4, 4), Image.open(p).resize((128, 128), Image.NEAREST))
        sheet.save(out)
    print("\n".join(written))
