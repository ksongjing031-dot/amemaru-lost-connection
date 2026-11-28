"""ボスごとのHPの帯の枠（11月27日）。

  python3 tools/artgen/make_boss_bars.py [リポジトリ] [確認画像の出力先]

11月27日のご指示「ボスのHPバーはオリジナルでそれぞれのボスのデザインにする」【補完】。256×24 の枠を、ボスごとの材質と飾りで描く
（textures/gui/boss_bar/<ボス>.png）。まん中の帯の所（x 20〜235・y 9〜14）は、ゲームが帯の色で塗るので、暗い縁取りだけ描いて
中は空けておく。描き方は client/BossHpBar.java（帯は何本あっても1本ぶんの高さ。残りの本数は名前の右に「×3」）。

  草原の主ヴェルドホーン  苔むした石の板と、両はしの象牙の角
  森番の古樹オールドウォード  木の皮の板（縦の木目）と、両はしの葉と光る節
  大水門の番人ハイドラム  青銅の板と鋲、両はしの水門のハンドル、水のしずく
  祈りの残響カンターレ  白い大理石と金の縁、両はしの金の鈴
  古代守護者  白い石の板と格子の溝、両はしの翠の立方体
  村長の残響オルド  暗い木の板に積もる雪と毛皮の縁、両はしの金の鎖の輪
  鏡像獣リフレクタ  面のある鏡の板と紫のふち、両はしのプリズムのかけら
  声なき湖主ムーネ  水の板と波紋、両はしの巻いた光るひげと真珠
"""
import math
import os
import sys

from PIL import Image, ImageDraw

W, H = 256, 24
SLOT = (19, 8, 236, 15)  # 帯の所の外側の縁（この内側は空ける）


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


OUT = hexc("0B0D12")


def plate(img, top, bottom, light, dark, x0=12, x1=243, y0=5, y1=18):
    """丸いはしの板。上から下へ明るい色 → 暗い色。ふちに暗い線、上に明るい線。"""
    d = ImageDraw.Draw(img)
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, (y1 - y0))
        col = mix(top, bottom, t)
        d.line([(x0 + 2, y), (x1 - 2, y)], fill=col)
    d.rounded_rectangle([x0, y0, x1, y1], radius=3, outline=OUT)
    d.line([(x0 + 3, y0 + 1), (x1 - 3, y0 + 1)], fill=light)
    d.line([(x0 + 3, y1 - 1), (x1 - 3, y1 - 1)], fill=dark)


def slot(img):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = SLOT
    d.rectangle([x0, y0, x1, y1], outline=OUT)
    d.line([(x0 + 1, y1 + 1), (x1, y1 + 1)], fill=hexc("FFFFFF", 60))
    # 中は空ける
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            img.putpixel((x, y), (0, 0, 0, 0))


def both_ends(img, draw_cap):
    """左のはしの飾りを描き、左右を写して右にも。"""
    cap = Image.new("RGBA", (24, H), (0, 0, 0, 0))
    draw_cap(cap)
    img.alpha_composite(cap, (0, 0))
    img.alpha_composite(cap.transpose(Image.FLIP_LEFT_RIGHT), (W - 24, 0))


def speckle(img, color, step, seed, y0=5, y1=18, x0=14, x1=242):
    import random
    rnd = random.Random(seed)
    for _ in range(step):
        x = rnd.randint(x0, x1)
        y = rnd.randint(y0, y1)
        if SLOT[0] <= x <= SLOT[2] and SLOT[1] <= y <= SLOT[3]:
            continue
        img.putpixel((x, y), color)


# ---------------------------------------------------------------- ボスごと

def veldhorn():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("8E826B"), hexc("5E5646"), hexc("B5A98E"), hexc("3E382E"))
    speckle(img, hexc("6F9A45"), 90, 1, y0=5, y1=8)
    speckle(img, hexc("4E6E32"), 40, 2)
    speckle(img, hexc("A99C82"), 50, 3)

    def cap(c):
        d = ImageDraw.Draw(c)
        # 象牙の角（外へ反り、上へ）
        pts = [(14, 14), (10, 15), (6, 13), (3, 9), (2, 5), (3, 2), (5, 4), (6, 8), (9, 11), (14, 12)]
        d.polygon(pts, fill=hexc("E8DCC0"), outline=OUT)
        d.line([(5, 5), (7, 9), (10, 12)], fill=hexc("FFF6E0"))
        d.line([(4, 3), (3, 6)], fill=hexc("C8B894"))
        # 根元の石
        d.rectangle([12, 9, 17, 17], fill=hexc("7A6F5A"), outline=OUT)
        d.point((14, 11), fill=hexc("6F9A45"))
    both_ends(img, cap)
    slot(img)
    return img


def old_ward():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("7A5634"), hexc("4A3220"), hexc("9C7448"), hexc("2E1E12"))
    d = ImageDraw.Draw(img)
    for x in range(16, 241, 7):
        d.line([(x, 6), (x + 1, 17)], fill=hexc("5B3E26"))
    speckle(img, hexc("5FAE4A"), 60, 4, y0=5, y1=7)

    def cap(c):
        d = ImageDraw.Draw(c)
        for (x, y, r) in ((6, 6, 4), (11, 4, 3), (4, 13, 3), (12, 15, 3)):
            d.ellipse([x - r, y - r, x + r, y + r], fill=hexc("5FAE4A"), outline=OUT)
            d.line([(x - r + 1, y), (x + r - 1, y)], fill=hexc("8FE07A"))
        d.ellipse([12, 8, 17, 13], fill=hexc("A8F0B0"), outline=OUT)
        d.point((14, 10), fill=hexc("FFFFFF"))
    both_ends(img, cap)
    slot(img)
    return img


def hydram():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("C08A3E"), hexc("7A5228"), hexc("E2B66A"), hexc("4E3418"))
    d = ImageDraw.Draw(img)
    for x in range(24, 236, 12):
        d.point((x, 6), fill=hexc("F2D08A"))
        d.point((x, 17), fill=hexc("F2D08A"))
    speckle(img, hexc("5E9E8A"), 30, 5)  # 緑青

    def cap(c):
        d = ImageDraw.Draw(c)
        cx, cy, r = 9, 11, 8
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=hexc("C08A3E"), width=2)
        d.ellipse([cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1], outline=OUT)
        for k in range(4):
            a = math.radians(45 + 90 * k)
            d.line([(cx, cy), (cx + math.cos(a) * r, cy + math.sin(a) * r)], fill=hexc("A87434"), width=1)
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=hexc("E2B66A"), outline=OUT)
        d.ellipse([17, 18, 20, 22], fill=hexc("5FC6F2"), outline=OUT)
    both_ends(img, cap)
    slot(img)
    return img


def cantare():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("F1EDE6"), hexc("C9C2B6"), hexc("FFFFFF"), hexc("9C958A"))
    d = ImageDraw.Draw(img)
    d.line([(16, 6), (239, 6)], fill=hexc("E2B65A"))
    d.line([(16, 17), (239, 17)], fill=hexc("E2B65A"))
    for x in range(30, 230, 16):
        d.line([(x, 7), (x + 5, 7)], fill=hexc("B8B0A4"))

    def cap(c):
        d = ImageDraw.Draw(c)
        # 金の鈴
        d.polygon([(4, 16), (6, 6), (12, 6), (14, 16)], fill=hexc("E2B65A"), outline=OUT)
        d.line([(7, 7), (6, 14)], fill=hexc("FFF0B8"))
        d.ellipse([7, 15, 11, 19], fill=hexc("C8962E"), outline=OUT)
        d.line([(9, 2), (9, 6)], fill=OUT)
        # 音符
        d.ellipse([15, 3, 18, 6], fill=hexc("B58CFF"), outline=OUT)
        d.line([(18, 4), (18, 0)], fill=OUT)
    both_ends(img, cap)
    slot(img)
    return img


def ancient_guardian():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("E9E4DA"), hexc("B7B0A4"), hexc("FFFFFF"), hexc("8E8A80"))
    d = ImageDraw.Draw(img)
    for x in range(18, 240, 9):
        d.line([(x, 6), (x + 4, 6)], fill=hexc("A39C90"))
        d.line([(x + 4, 17), (x + 8, 17)], fill=hexc("A39C90"))

    def cap(c):
        d = ImageDraw.Draw(c)
        d.rectangle([2, 4, 15, 19], fill=hexc("D6D0C4"), outline=OUT)
        d.line([(3, 5), (14, 5)], fill=hexc("FFFFFF"))
        d.rectangle([5, 7, 12, 15], fill=hexc("2BAA81"), outline=OUT)
        d.rectangle([6, 8, 9, 10], fill=hexc("8CE8C3"))
        d.point((7, 9), fill=hexc("D3FFEE"))
    both_ends(img, cap)
    slot(img)
    return img


def ordo():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("5E4430"), hexc("35251A"), hexc("7A5A40"), hexc("1E140E"))
    d = ImageDraw.Draw(img)
    # 積もる雪（上）と毛皮の縁（下）
    for x in range(14, 242):
        h = 2 + (1 if (x * 7) % 5 == 0 else 0)
        d.line([(x, 4), (x, 4 + h)], fill=hexc("F4F8FF"))
    for x in range(14, 242, 2):
        d.line([(x, 17), (x, 19 if x % 4 == 0 else 18)], fill=hexc("E8E2D6"))

    def cap(c):
        d = ImageDraw.Draw(c)
        for (x, y) in ((3, 8), (8, 12), (13, 8)):
            d.ellipse([x - 3, y - 3, x + 3, y + 3], outline=hexc("E2B65A"), width=2)
            d.ellipse([x - 4, y - 4, x + 4, y + 4], outline=OUT)
        d.ellipse([7, 15, 13, 21], fill=hexc("E2B65A"), outline=OUT)
        d.point((10, 18), fill=hexc("8FD3FF"))
    both_ends(img, cap)
    slot(img)
    return img


def reflecta():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("EEF2FF"), hexc("A8B4D8"), hexc("FFFFFF"), hexc("7C88B0"))
    d = ImageDraw.Draw(img)
    for x in range(20, 236, 14):
        d.line([(x, 17), (x + 7, 6)], fill=hexc("D8E0F8"))
    d.line([(14, 5), (241, 5)], fill=hexc("9A7CF0"))
    d.line([(14, 18), (241, 18)], fill=hexc("9A7CF0"))

    def cap(c):
        d = ImageDraw.Draw(c)
        d.polygon([(2, 12), (9, 1), (13, 12), (9, 22)], fill=hexc("C9A8FF"), outline=OUT)
        d.polygon([(9, 1), (13, 12), (9, 12)], fill=hexc("F0F4FF"))
        d.polygon([(12, 14), (17, 6), (19, 16)], fill=hexc("8FF0FF"), outline=OUT)
    both_ends(img, cap)
    slot(img)
    return img


def mune():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("5FA8C8"), hexc("2E6E9E"), hexc("A8F7FF"), hexc("1E4868"))
    d = ImageDraw.Draw(img)
    for x in range(16, 238, 10):
        d.arc([x, 4, x + 10, 10], 200, 340, fill=hexc("A8F7FF"))
        d.arc([x + 5, 13, x + 15, 19], 20, 160, fill=hexc("7FDDE8"))

    def cap(c):
        d = ImageDraw.Draw(c)
        d.arc([1, 1, 15, 15], 90, 350, fill=hexc("A8F7FF"), width=2)
        d.arc([4, 4, 12, 12], 120, 360, fill=hexc("D8FCFF"), width=1)
        d.ellipse([11, 14, 17, 20], fill=hexc("F4F2EB"), outline=OUT)
        d.point((13, 16), fill=hexc("FFFFFF"))
    both_ends(img, cap)
    slot(img)
    return img


def valgrim():
    """9階：近衛の青い帯に、白銀の鎖（盾の誓い）と、両端の盾"""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("3C5A9E"), hexc("1E3670"), hexc("9CC8FF"), hexc("12244E"))
    d = ImageDraw.Draw(img)
    for x in range(16, 238, 8):
        d.ellipse([x, 3, x + 5, 7], outline=hexc("EAF4FF"))
        d.ellipse([x + 4, 16, x + 9, 20], outline=hexc("C4DCF2"))

    def cap(c):
        d = ImageDraw.Draw(c)
        d.polygon([(2, 4), (16, 4), (16, 14), (9, 21), (2, 14)], fill=hexc("2C4A9A"), outline=OUT)
        d.line([(9, 6), (9, 17)], fill=hexc("EAF4FF"))
        d.line([(5, 10), (13, 10)], fill=hexc("EAF4FF"))
        d.point((9, 10), fill=hexc("9CC8FF"))
    both_ends(img, cap)
    slot(img)
    return img


def default():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    plate(img, hexc("3A3D4A"), hexc("22242C"), hexc("5A5E70"), hexc("14151A"))

    def cap(c):
        d = ImageDraw.Draw(c)
        d.polygon([(3, 12), (10, 4), (16, 12), (10, 20)], fill=hexc("E2B65A"), outline=OUT)
    both_ends(img, cap)
    slot(img)
    return img


BARS = {"veldhorn": veldhorn, "old_ward": old_ward, "hydram": hydram, "cantare": cantare,
        "ancient_guardian": ancient_guardian, "ordo": ordo, "reflecta": reflecta, "mune": mune, "valgrim": valgrim, "default": default}


def sheet(images, out):
    """確認画像：3倍にして、帯に色を入れて並べる。"""
    scale = 3
    rows = len(images)
    s = Image.new("RGBA", (W * scale + 40, rows * (H * scale + 18) + 10), (34, 36, 44, 255))
    colors = {"veldhorn": "E2B65A", "old_ward": "7ED957", "hydram": "4FB6F0", "cantare": "F2C94C", "ancient_guardian": "4FCC9E",
              "ordo": "BFEFFF", "reflecta": "C9A8FF", "mune": "A8F7FF", "valgrim": "9CC8FF", "default": "E0C89A"}
    for i, (name, img) in enumerate(images.items()):
        filled = img.copy()
        d = ImageDraw.Draw(filled)
        d.rectangle([20, 9, 20 + 150, 14], fill=hexc(colors[name]))
        d.rectangle([20 + 150, 9, 235, 14], fill=hexc("140F14", 224))
        big = filled.resize((W * scale, H * scale), Image.NEAREST)
        s.alpha_composite(big, (20, 10 + i * (H * scale + 18)))
    s.save(out)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out_dir = os.path.join(repo, "src/main/resources/assets/amemaru/textures/gui/boss_bar")
    os.makedirs(out_dir, exist_ok=True)
    images = {}
    for name, fn in BARS.items():
        img = fn()
        img.save(os.path.join(out_dir, name + ".png"))
        images[name] = img
    check = sys.argv[2] if len(sys.argv) > 2 else None
    if check:
        os.makedirs(check, exist_ok=True)
        sheet(images, os.path.join(check, "boss_bars_check.png"))
    print("wrote", len(images), "boss bars")


if __name__ == "__main__":
    main()
