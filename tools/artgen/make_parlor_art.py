"""カジノの地下遊技場の絵とブロックのモデル（11月27日【補完】）。

  python3 tools/artgen/make_parlor_art.py [リポジトリ] [確認画像の出力先]

作るもの
  textures/gui/parlor/sym_<図柄>.png        スロットのリールの図柄（32×32）：seven・bar・grape・replay・cherry・bell・knot・melon・star
  models/block/parlor_<機種>[_lit].json      台（パチンコ3・スロット3）。正面は北。上に呼び出しランプ（lit は光る）
  textures/block/parlor_<機種>[_lit].png     台の絵（4コマで動く。液晶の図柄が回る・ランプが光る）
  models/block/parlor_base_p.json・_s.json  台の下の受け皿（パチンコは上皿・下皿・ハンドル、スロットは止めボタンとレバーとメダルの皿）
  models/block/parlor_board.json             島のデータの掲示板（7セグメントの数字が光る）
  models/block/parlor_stool.json             丸い腰かけ
  blockstates/parlor_*.json
部品は2倍の大きさ（ブロックは 0〜32）で組み、書き出すときに半分にする（tools/artgen/make_workbenches.py と同じ）。
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel, r4, save_frames  # noqa: E402
from materials import RAMPS, Painter, hsh, pick, ramp  # noqa: E402

CREDIT = "AMEMARU: カジノの地下遊技場（11月27日。tools/artgen/make_parlor_art.py で生成）"
TEX = 128
FRAMES = 4
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

MACHINES = {
    # id: (パチンコか, 枠の色, ふちの色, 液晶の色, 光の色)
    "p_tower": (True, "#1E3C78", "#F2C84A", "#08142E", "#7FD8FF"),
    "p_forest": (True, "#2C5A2E", "#F0E090", "#0C1E10", "#9CF08A"),
    "p_lake": (True, "#2A4A66", "#E4F2FF", "#06121E", "#9CD4FF"),
    "s_knot": (False, "#5A2A82", "#F2C84A", "#1A0E26", "#FF5AA8"),
    "s_star": (False, "#1C2A5C", "#F4D27A", "#0A1028", "#7AB8FF"),
    "s_moon": (False, "#3A3E4A", "#E8ECF4", "#0C0E16", "#C8D8FF"),
}


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except OSError:
        return ImageFont.load_default()


# ---------------------------------------------------------------- スロットの図柄（32×32）
def symbol(name, size=32):
    S = 4
    n = size * S
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    if name == "seven":
        pts = [(0.18, 0.16), (0.86, 0.16), (0.86, 0.32), (0.52, 0.88), (0.30, 0.88), (0.62, 0.34), (0.18, 0.34)]
        poly = [(x * n, y * n) for x, y in pts]
        d.polygon([(x + n * 0.03, y + n * 0.03) for x, y in poly], fill=(60, 0, 0, 255))
        d.polygon(poly, fill=(232, 40, 60, 255), outline=(90, 0, 10, 255), width=S * 2)
        d.line([(0.22 * n, 0.21 * n), (0.80 * n, 0.21 * n)], fill=(255, 200, 200, 255), width=S * 2)
    elif name == "bar":
        d.rounded_rectangle([0.08 * n, 0.30 * n, 0.92 * n, 0.70 * n], radius=0.08 * n, fill=(24, 24, 32, 255),
                            outline=(200, 200, 210, 255), width=S * 2)
        f = font(int(0.30 * n))
        d.text((c, c), "BAR", font=f, fill=(250, 250, 250, 255), anchor="mm")
    elif name == "grape":
        for (x, y) in ((0.36, 0.40), (0.56, 0.40), (0.46, 0.55), (0.66, 0.55), (0.26, 0.55), (0.36, 0.70), (0.56, 0.70),
                       (0.46, 0.84)):
            r = 0.11 * n
            d.ellipse([x * n - r, y * n - r, x * n + r, y * n + r], fill=(138, 76, 200, 255), outline=(60, 20, 90, 255), width=S)
            d.ellipse([x * n - r * 0.5, y * n - r * 0.6, x * n - r * 0.1, y * n - r * 0.2], fill=(220, 190, 255, 255))
        d.polygon([(0.46 * n, 0.28 * n), (0.70 * n, 0.12 * n), (0.80 * n, 0.26 * n)], fill=(60, 170, 70, 255))
        d.line([(0.46 * n, 0.30 * n), (0.48 * n, 0.14 * n)], fill=(90, 60, 30, 255), width=S * 2)
    elif name == "replay":
        d.rounded_rectangle([0.06 * n, 0.22 * n, 0.94 * n, 0.78 * n], radius=0.12 * n, fill=(40, 120, 220, 255),
                            outline=(10, 40, 100, 255), width=S * 2)
        f = font(int(0.20 * n))
        d.text((c, c), "REPLAY", font=f, fill=(255, 255, 255, 255), anchor="mm")
    elif name == "cherry":
        for (x, y) in ((0.34, 0.68), (0.64, 0.72)):
            r = 0.16 * n
            d.ellipse([x * n - r, y * n - r, x * n + r, y * n + r], fill=(224, 40, 70, 255), outline=(110, 0, 20, 255), width=S)
            d.ellipse([x * n - r * 0.55, y * n - r * 0.6, x * n - r * 0.15, y * n - r * 0.2], fill=(255, 200, 210, 255))
        d.line([(0.34 * n, 0.54 * n), (0.56 * n, 0.16 * n)], fill=(60, 140, 50, 255), width=S * 3)
        d.line([(0.64 * n, 0.58 * n), (0.56 * n, 0.16 * n)], fill=(60, 140, 50, 255), width=S * 3)
        d.polygon([(0.56 * n, 0.16 * n), (0.78 * n, 0.10 * n), (0.70 * n, 0.26 * n)], fill=(70, 170, 70, 255))
    elif name == "bell":
        d.polygon([(0.30 * n, 0.70 * n), (0.36 * n, 0.30 * n), (0.50 * n, 0.18 * n), (0.64 * n, 0.30 * n), (0.70 * n, 0.70 * n),
                   (0.80 * n, 0.76 * n), (0.20 * n, 0.76 * n)], fill=(240, 200, 48, 255), outline=(120, 80, 0, 255))
        d.ellipse([0.44 * n, 0.74 * n, 0.56 * n, 0.86 * n], fill=(200, 140, 20, 255))
        d.line([(0.42 * n, 0.32 * n), (0.38 * n, 0.66 * n)], fill=(255, 245, 190, 255), width=S * 2)
    elif name == "knot":
        col = (208, 120, 48, 255)
        for (x, y) in ((0.34, 0.40), (0.66, 0.40), (0.34, 0.66), (0.66, 0.66)):
            r = 0.17 * n
            d.ellipse([x * n - r, y * n - r, x * n + r, y * n + r], outline=col, width=S * 4)
        d.ellipse([c - 0.07 * n, c + 0.03 * n - 0.07 * n, c + 0.07 * n, c + 0.03 * n + 0.07 * n], fill=(255, 230, 160, 255))
    elif name == "melon":
        d.pieslice([0.08 * n, 0.0, 0.92 * n, 0.84 * n], 0, 180, fill=(40, 150, 60, 255))
        d.pieslice([0.14 * n, 0.06 * n, 0.86 * n, 0.78 * n], 0, 180, fill=(240, 240, 220, 255))
        d.pieslice([0.18 * n, 0.10 * n, 0.82 * n, 0.74 * n], 0, 180, fill=(230, 60, 70, 255))
        for (x, y) in ((0.34, 0.54), (0.50, 0.62), (0.66, 0.54), (0.42, 0.48), (0.58, 0.48)):
            d.ellipse([x * n - S * 2, y * n - S * 3, x * n + S * 2, y * n + S * 3], fill=(20, 20, 20, 255))
    elif name == "star":
        pts = []
        for k in range(10):
            a = -math.pi / 2 + k * math.pi / 5
            r = (0.42 if k % 2 == 0 else 0.18) * n
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        d.polygon(pts, fill=(250, 240, 200, 255), outline=(200, 160, 40, 255), width=S * 2)
    return img.resize((size, size), Image.LANCZOS)


SYMBOL_NAMES = {"7": "seven", "B": "bar", "G": "grape", "R": "replay", "C": "cherry", "L": "bell", "K": "knot", "S": "melon",
                "X": "star"}


# ---------------------------------------------------------------- 台の正面の絵（デカール）
def board_decal(mid, w, h, frame, lit):
    """パチンコの盤面：ガラスの中の釘、真ん中の液晶（図柄が回る）、ヘソ、アタッカー"""
    pach, frame_c, accent, screen, glow = MACHINES[mid]
    S = 4
    img = Image.new("RGBA", (w * S, h * S), rgb(frame_c) + (255,))
    d = ImageDraw.Draw(img)
    W, H = w * S, h * S
    # 盤面（ガラスの円）
    board = shade(rgb(frame_c), 0.55)
    d.ellipse([W * 0.04, H * 0.02, W * 0.96, H * 0.98], fill=shade(board, 0.8) + (255,), outline=rgb(accent) + (255,), width=S)
    # 釘（まばらに）と、玉の通り道の金の飾り
    for row in range(8):
        for col in range(8):
            x = W * (0.14 + col * 0.10 + (row % 2) * 0.05)
            y = H * (0.12 + row * 0.10)
            if 0.26 * W < x < 0.74 * W and 0.16 * H < y < 0.64 * H:
                continue
            if (x - W / 2) ** 2 / (W * 0.42) ** 2 + (y - H / 2) ** 2 / (H * 0.44) ** 2 > 1:
                continue
            d.ellipse([x - S * 0.5, y - S * 0.5, x + S * 0.5, y + S * 0.5], fill=(200, 200, 210, 255))
    d.arc([W * 0.10, H * 0.06, W * 0.90, H * 0.94], 200, 340, fill=rgb(accent) + (255,), width=S)
    # 液晶
    lx0, ly0, lx1, ly1 = W * 0.27, H * 0.17, W * 0.73, H * 0.62
    scr = rgb(screen)
    if lit:
        scr = tuple(int(v * 0.6 + c * 0.4) for v, c in zip(scr, rgb(glow)))
    d.rectangle([lx0, ly0, lx1, ly1], fill=scr + (255,), outline=(20, 20, 24, 255), width=S)
    # 背景（機種ごと）
    if mid == "p_tower":
        d.polygon([(W * 0.5, ly0 + S * 3), (W * 0.56, ly1 - S), (W * 0.44, ly1 - S)], fill=(90, 120, 190, 255))
    elif mid == "p_forest":
        for k in range(4):
            x = lx0 + (k + 0.5) * (lx1 - lx0) / 4
            d.polygon([(x, ly0 + S * 4), (x + S * 4, ly1 - S * 3), (x - S * 4, ly1 - S * 3)], fill=(40, 110, 50, 255))
    else:
        d.ellipse([lx0 + S * 3, ly1 - S * 8, lx1 - S * 3, ly1 + S * 4], fill=(60, 110, 160, 255))
    # 図柄（3つ。コマで回る。光っていれば 7 7 7）
    f = font(int((ly1 - ly0) * 0.48))
    digits = ["7", "7", "7"] if lit else [str((frame * 3 + k * 4) % 9 + 1) for k in range(3)]
    cols = [(255, 60, 60), (70, 150, 255), (255, 210, 60)]
    for k, t in enumerate(digits):
        x = lx0 + (k + 0.5) * (lx1 - lx0) / 3
        y = (ly0 + ly1) / 2
        col = (255, 70, 70) if lit else cols[(frame + k) % 3]
        d.text((x + S, y + S), t, font=f, fill=(0, 0, 0, 255), anchor="mm")
        d.text((x, y), t, font=f, fill=col + (255,), anchor="mm")
    # ヘソと電チューとアタッカー
    d.rectangle([W * 0.46, H * 0.70, W * 0.54, H * 0.74], fill=(240, 200, 60, 255))
    d.rectangle([W * 0.80, H * 0.56, W * 0.86, H * 0.62], fill=(220, 60, 90, 255))
    d.rectangle([W * 0.56, H * 0.80, W * 0.78, H * 0.84], fill=(255, 140, 40, 255) if lit else (120, 50, 30, 255))
    # 盤面の飾りのランプ（光っていれば虹色）
    for k in range(16):
        a = k / 16 * math.pi * 2
        x = W / 2 + math.cos(a) * W * 0.44
        y = H / 2 + math.sin(a) * H * 0.46
        if lit:
            hue = (k / 16 + frame / FRAMES) % 1
            col = hsv(hue)
        else:
            col = rgb(accent) if (k + frame) % 4 == 0 else shade(rgb(accent), 0.5)
        d.ellipse([x - S * 1.2, y - S * 1.2, x + S * 1.2, y + S * 1.2], fill=col + (255,))
    return img.resize((w, h), Image.LANCZOS)


def hsv(h, s=0.8, v=1.0):
    import colorsys
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


REEL_STRIPS = {
    "s_knot": ["G7CGRGLBRKGBRGGCLRG7R", "R7GLRGCBRGKGR7GLRKGBG", "7GKRLGBRCG7RGLGRBGKRG"],
    "s_star": ["RLBRLLCRRLRRSSLLRXC7L", "7LRSLRBLRSLRXLRSLRCLR", "7RLSRLBRLSRLXRLSRLBRL"],
}
REEL_STRIPS["s_moon"] = REEL_STRIPS["s_star"]


def slot_decal(mid, w, h, frame, lit):
    """スロットの正面：上の液晶（AT）か機種の看板と告知ランプ（Aタイプ）、真ん中のリールの窓（3×3の図柄）"""
    pach, frame_c, accent, screen, glow = MACHINES[mid]
    S = 4
    W, H = w * S, h * S
    img = Image.new("RGBA", (W, H), rgb(frame_c) + (255,))
    d = ImageDraw.Draw(img)
    # 上の液晶・看板
    top = rgb(screen)
    if lit:
        top = tuple(int(v * 0.5 + c * 0.5) for v, c in zip(top, rgb(glow)))
    d.rectangle([W * 0.08, H * 0.05, W * 0.92, H * 0.36], fill=top + (255,), outline=rgb(accent) + (255,), width=S)
    if mid == "s_knot":
        f = font(int(H * 0.11))
        d.text((W / 2, H * 0.20), "KNOT", font=f, fill=rgb(accent) + (255,), anchor="mm")
    elif mid == "s_star":
        for k in range(7):
            x = W * (0.14 + k * 0.12)
            y = H * (0.10 + (k * 37 % 5) * 0.045)
            d.ellipse([x - S, y - S, x + S, y + S], fill=(255, 250, 220, 255))
        d.polygon([(W * 0.5, H * 0.12), (W * 0.56, H * 0.32), (W * 0.44, H * 0.32)], fill=(120, 150, 220, 255))
    else:
        d.ellipse([W * 0.38, H * 0.08, W * 0.62, H * 0.32], fill=(230, 236, 250, 255))
        d.ellipse([W * 0.44, H * 0.06, W * 0.68, H * 0.30], fill=top + (255,))
    # リールの窓
    rx0, ry0, rx1, ry1 = W * 0.10, H * 0.42, W * 0.90, H * 0.92
    d.rectangle([rx0 - S, ry0 - S, rx1 + S, ry1 + S], fill=(10, 10, 12, 255))
    strips = REEL_STRIPS[mid]
    cw = (rx1 - rx0) / 3
    ch = (ry1 - ry0) / 3
    for r in range(3):
        x0 = rx0 + r * cw
        d.rectangle([x0 + S, ry0, x0 + cw - S, ry1], fill=(244, 240, 228, 255))
        base = (frame * 5 + r * 7) % 21 if not lit else strips[r].index("7")
        for row in range(3):
            code = strips[r][(base + row - 1) % 21]
            sym = symbol(SYMBOL_NAMES[code], 64).resize((int(ch * 0.9), int(ch * 0.9)), Image.LANCZOS)
            img.alpha_composite(sym, (int(x0 + (cw - sym.width) / 2), int(ry0 + row * ch + (ch - sym.height) / 2)))
    d = ImageDraw.Draw(img)
    # 有効ライン（中段）
    d.line([(rx0 - S * 2, (ry0 + ry1) / 2), (rx0, (ry0 + ry1) / 2)], fill=(255, 60, 80, 255), width=S)
    d.line([(rx1, (ry0 + ry1) / 2), (rx1 + S * 2, (ry0 + ry1) / 2)], fill=(255, 60, 80, 255), width=S)
    # 告知ランプ（Aタイプ）
    if mid == "s_knot":
        col = hsv((frame / FRAMES) % 1) if lit else (70, 30, 50)
        for (x, y) in ((0.06, 0.86), (0.10, 0.86), (0.06, 0.94), (0.10, 0.94)):
            d.ellipse([W * x - S * 1.5, H * y - S * 1.5, W * x + S * 1.5, H * y + S * 1.5], fill=col + (255,))
    return img.resize((w, h), Image.LANCZOS)


def lamp_decal(mid, w, h, frame, lit):
    """上の呼び出しランプ（光ると色が回る）"""
    pach, frame_c, accent, screen, glow = MACHINES[mid]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if lit:
        col = hsv((frame / FRAMES + 0.1) % 1, 0.7, 1.0)
        d.rectangle([0, 0, w, h], fill=col + (255,))
        d.rectangle([1, 1, w - 2, max(1, h // 3)], fill=(255, 255, 255, 255))
    else:
        col = shade(rgb(glow), 0.35)
        d.rectangle([0, 0, w, h], fill=col + (255,))
        d.rectangle([1, 1, w - 2, max(1, h // 3)], fill=shade(rgb(glow), 0.5) + (255,))
    return img


def board_screen(w, h, frame):
    """島の掲示板の画面（データの数字が光る）"""
    S = 4
    W, H = w * S, h * S
    img = Image.new("RGBA", (W, H), (10, 10, 14, 255))
    d = ImageDraw.Draw(img)
    f = font(int(H * 0.16))
    rows = (("BB", (255, 80, 90)), ("RB", (90, 160, 255)), ("G", (255, 220, 90)))
    for k, (label, col) in enumerate(rows):
        y = H * (0.18 + k * 0.30)
        d.text((W * 0.08, y), label, font=f, fill=col + (255,), anchor="lm")
        value = str((frame * 7 + k * 13) % 40 + (k * 100 if k == 2 else 0))
        d.text((W * 0.92, y), value, font=f, fill=(255, 90, 60, 255), anchor="rm")
    return img.resize((w, h), Image.LANCZOS)


# ---------------------------------------------------------------- 材質
EXTRA = {
    "chrome": ramp("#2A2C32", "#4A4E58", "#6E7480", "#9AA0AC", "#C4C9D2", "#E2E6EC", "#FAFBFD"),
    "plastic": ramp("#08080A", "#121216", "#1C1C22", "#26262E", "#32323C", "#42424E", "#565664"),
    "cushion": ramp("#3A0A10", "#5C1018", "#821A24", "#A82632", "#C83A44", "#E0606A", "#F4949A"),
    "tray": ramp("#3C4048", "#585E68", "#78808C", "#9AA2AE", "#BCC4CE", "#DADFE6", "#F4F6F9"),
}


class ParlorPainter(Painter):
    def __init__(self, model, mid, lit, decals):
        super().__init__(model)
        self.mid = mid
        self.lit = lit
        self.decals = decals     # 名前 → コマごとの絵
        pach, frame_c, accent, screen, glow = MACHINES.get(mid, (True, "#303040", "#C0C0C8", "#101018", "#80C0FF"))
        self.frame_ramp = ramp(*["#%02X%02X%02X" % shade(rgb(frame_c), f) for f in (0.35, 0.5, 0.7, 0.85, 1.0, 1.15, 1.3)])
        self.accent_ramp = ramp(*["#%02X%02X%02X" % shade(rgb(accent), f) for f in (0.35, 0.5, 0.7, 0.85, 1.0, 1.1, 1.2)])

    def r(self, name):
        return EXTRA.get(name) or RAMPS[name]

    def decal(self, c):
        key = c.el.tags.get("decal")
        if key and c.n[2] < -0.6:   # 正面（北）
            frames = self.decals[key]
            img = frames[c.frame % len(frames)]
            px = img.getpixel((min(img.width - 1, c.i * img.width // c.W), min(img.height - 1, c.j * img.height // c.H)))
            return px[:3] + (255,)
        return None

    def paint_body(self, c):
        d = self.decal(c)
        if d:
            return d
        tone = 3.2 + (self.tex(c, 3) - 0.5) * 0.3 + self.bevel(c, up=0.8, down=0.7)
        return pick(self.frame_ramp, tone)

    def paint_trim(self, c):
        tone = 3.6 + self.bevel(c, up=1.2, down=0.9) + (self.tex(c, 5) - 0.5) * 0.3
        return pick(self.accent_ramp, tone)

    def paint_screen(self, c):
        d = self.decal(c)
        if d:
            return d
        return pick(self.r("plastic"), 2.0)

    def paint_lamp(self, c):
        d = self.decal(c)
        if d:
            return d
        frames = self.decals.get("lamp")
        if frames:
            img = frames[c.frame % len(frames)]
            px = img.getpixel((min(img.width - 1, c.i * img.width // max(1, c.W)), min(img.height - 1, c.j * img.height // max(1, c.H))))
            return px[:3] + (255,)
        return pick(self.r("chrome"), 4.0)

    def paint_chrome(self, c):
        tone = 3.8 + self.bevel(c, up=1.6, down=1.0) + (self.tex(c, 7) - 0.5) * 0.4
        return pick(self.r("chrome"), tone)

    def paint_plastic(self, c):
        tone = 2.6 + self.bevel(c, up=0.8, down=0.6) + (self.tex(c, 9) - 0.5) * 0.3
        return pick(self.r("plastic"), tone)

    def paint_tray(self, c):
        tone = 3.6 + self.bevel(c, up=1.2, down=0.8)
        # 玉・メダル（上の面に、光る丸）
        if c.n[1] > 0.6 and c.el.tags.get("balls") and (c.i * 3 + c.j * 5) % 7 in (0, 1):
            return pick(self.r("chrome"), 5.6)
        return pick(self.r("tray"), tone)

    def paint_red(self, c):
        tone = 3.4 + self.bevel(c, up=1.4, down=0.9)
        return pick(self.r("cushion"), tone)

    def paint_blue(self, c):
        tone = 3.6 + self.bevel(c, up=1.4, down=0.9)
        return pick(ramp("#0A2848", "#10406E", "#1A5C98", "#2A78C0", "#4A98E0", "#78B8F0", "#B0D8FA"), tone)

    def paint_cushion(self, c):
        tone = 3.4 + (self.tex(c, 11) - 0.5) * 0.4 + self.bevel(c, up=0.9, down=0.8)
        return pick(self.r("cushion"), tone)

    def paint_board(self, c):
        d = self.decal(c)
        if d:
            return d
        return pick(self.r("plastic"), 2.4 + self.bevel(c, up=0.8, down=0.6))


# ---------------------------------------------------------------- 模型
def machine_model(mid):
    pach = MACHINES[mid][0]
    m = JavaModel(("parlor_" + mid), TEX, TEX)
    # 本体（正面は z = 4、北）
    m.box("body", (0, 0, 4), (32, 32, 32), "body")
    # 正面の盤面・リールの窓（少し奥まる）
    m.box("front", (3, 2, 3), (29, 31, 4), "screen", glow=True, tags={"decal": "front"})
    # ふちの金の枠
    m.box("trim_l", (1, 0, 2), (3, 32, 4), "trim")
    m.box("trim_r", (29, 0, 2), (31, 32, 4), "trim")
    m.box("trim_t", (1, 31, 2), (31, 32, 4), "trim")
    # 上の呼び出しランプ（データランプ）
    m.box("lamp_base", (9, 32, 12), (23, 34, 24), "plastic")
    m.box("lamp", (11, 34, 14), (21, 40, 22), "lamp", glow=True, tags={"decal": "lamp_front"})
    if not pach:
        # スロット：リールの窓の下の金のふち
        m.box("reel_sill", (3, 0, 2), (29, 2, 4), "trim")
    else:
        m.box("board_sill", (3, 0, 2), (29, 2, 4), "chrome")
    return m


def base_model(kind):
    m = JavaModel("parlor_base_" + kind, TEX, TEX)
    m.box("body", (0, 0, 6), (32, 32, 32), "plastic")
    if kind == "p":
        # 上皿（玉が見える）・下皿・ハンドル・玉貸しのボタン
        # 正面から見て右（西、x の小さい方）にハンドル、左（東）に玉貸しのボタン
        m.box("upper_tray", (2, 22, 0), (30, 28, 6), "tray", tags={"balls": True})
        m.box("lower_tray", (8, 6, 1), (28, 11, 6), "tray", tags={"balls": True})
        m.box("handle", (1, 9, 0), (7, 15, 6), "chrome")
        m.box("handle_grip", (2, 10, -1), (6, 14, 0), "red")
        m.box("lend", (23, 28, 2), (29, 30, 6), "blue")
    else:
        # 止めボタン3つ・MAX BET・レバー・メダルの受け皿
        # 正面から見て左（東、x の大きい方）にレバーと MAX BET、真ん中に止めボタン3つ
        m.box("panel", (1, 22, 2), (31, 30, 6), "plastic")
        for k, x in enumerate((7, 13, 19)):
            m.box("stop_%d" % k, (x, 24, 0), (x + 4, 27, 2), "blue")
        m.box("bet", (26, 27, 0), (29, 29, 2), "red")
        m.box("lever", (26, 23, -2), (28, 25, 2), "chrome")
        m.box("lever_ball", (25, 22, -4), (29, 26, -2), "red")
        m.box("medal_tray", (5, 4, 0), (27, 9, 6), "tray", tags={"balls": True})
    return m


def board_model():
    m = JavaModel("parlor_board", TEX, TEX)
    m.box("post", (14, 0, 14), (18, 6, 18), "chrome")
    m.box("panel", (2, 6, 12), (30, 30, 20), "board", glow=False)
    m.box("screen", (4, 8, 11), (28, 28, 12), "screen", glow=True, tags={"decal": "board"})
    return m


def stool_model():
    m = JavaModel("parlor_stool", 64, 64)
    m.box("foot", (10, 0, 10), (22, 1, 22), "chrome")
    m.box("pole", (15, 1, 15), (17, 14, 17), "chrome")
    m.box("seat", (9, 14, 9), (23, 18, 23), "cushion")
    return m


def strip_hidden_safe(m):
    try:
        from javamodel import strip_hidden
        strip_hidden(m)
    except ImportError:
        pass


def export(repo, name, m, frames, frametime=None, render_type="minecraft:cutout"):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    tex_ref = "amemaru:block/" + name
    elements = m.json_elements()
    for e in elements:
        e["from"] = [r4(v / 2.0) for v in e["from"]]
        e["to"] = [r4(v / 2.0) for v in e["to"]]
        if "rotation" in e:
            e["rotation"]["origin"] = [r4(v / 2.0) for v in e["rotation"]["origin"]]
    data = {"credit": CREDIT, "parent": "minecraft:block/block", "render_type": render_type, "texture_size": [m.tex_w, m.tex_h],
            "textures": {"0": tex_ref, "particle": tex_ref}, "elements": elements}
    with open(os.path.join(base, "models/block", name + ".json"), "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    path = os.path.join(base, "textures/block", name + ".png")
    save_frames(frames, path)
    if len(frames) > 1:
        with open(path + ".mcmeta", "w", encoding="utf-8") as fp:
            json.dump({"animation": {"frametime": frametime or 8}}, fp, indent=2)
            fp.write("\n")
    elif os.path.exists(path + ".mcmeta"):
        os.remove(path + ".mcmeta")


def blockstate(repo, name, model_of):
    """model_of(facing, lit) → 模型の名前（lit が None なら lit のない形）"""
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        if model_of(facing, None) is not None:
            v = {"model": model_of(facing, None)}
            if rot:
                v["y"] = rot
            variants["facing=" + facing] = v
        else:
            for lit in (False, True):
                v = {"model": model_of(facing, lit)}
                if rot:
                    v["y"] = rot
                variants["facing=%s,lit=%s" % (facing, str(lit).lower())] = v
    path = os.path.join(repo, "src/main/resources/assets/amemaru/blockstates", name + ".json")
    with open(path, "w", encoding="utf-8") as fp:
        json.dump({"variants": variants}, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    os.makedirs(os.path.join(base, "textures/gui/parlor"), exist_ok=True)
    for code, name in SYMBOL_NAMES.items():
        symbol(name, 32).save(os.path.join(base, "textures/gui/parlor", "sym_" + name + ".png"))
    for mid, spec in MACHINES.items():
        pach = spec[0]
        for lit in (False, True):
            m = machine_model(mid)
            strip_hidden_safe(m)
            m.pack()
            fw, fh = 26, 29
            front = [(board_decal if pach else slot_decal)(mid, fw, fh, k, lit) for k in range(FRAMES)]
            lamp = [lamp_decal(mid, 10, 6, k, lit) for k in range(FRAMES)]
            painter = ParlorPainter(m, mid, lit, {"front": front, "lamp_front": lamp, "lamp": lamp})
            frames = m.paint(painter, frames=FRAMES)
            name = "parlor_" + mid + ("_lit" if lit else "")
            export(repo, name, m, frames, frametime=3 if lit else 10)
            if out:
                os.makedirs(out, exist_ok=True)
                from render import render
                img = render(m, frames[0], yaw=200, pitch=20, scale=8.0, size=(420, 420), center=(16, 18, 16), light="gui", ss=2)
                img.save(os.path.join(out, name + "_preview.png"))
        blockstate(repo, "parlor_" + mid, lambda f, lit, mid=mid: None if lit is None else
                   "amemaru:block/parlor_" + mid + ("_lit" if lit else ""))
        print("wrote parlor_" + mid)
    for kind in ("p", "s"):
        m = base_model(kind)
        strip_hidden_safe(m)
        m.pack()
        frames = m.paint(ParlorPainter(m, "", False, {}), frames=1)
        export(repo, "parlor_base_" + kind, m, frames)
        blockstate(repo, "parlor_base_" + kind, lambda f, lit, kind=kind: "amemaru:block/parlor_base_" + kind)
        if out:
            from render import render
            img = render(m, frames[0], yaw=200, pitch=20, scale=8.0, size=(420, 420), center=(16, 16, 16), light="gui", ss=2)
            img.save(os.path.join(out, "parlor_base_" + kind + "_preview.png"))
    m = board_model()
    strip_hidden_safe(m)
    m.pack()
    frames = m.paint(ParlorPainter(m, "", False, {"board": [board_screen(24, 20, k) for k in range(FRAMES)]}), frames=FRAMES)
    export(repo, "parlor_board", m, frames, frametime=20)
    blockstate(repo, "parlor_board", lambda f, lit: "amemaru:block/parlor_board")
    m = stool_model()
    strip_hidden_safe(m)
    m.pack()
    frames = m.paint(ParlorPainter(m, "", False, {}), frames=1)
    export(repo, "parlor_stool", m, frames)
    path = os.path.join(base, "blockstates", "parlor_stool.json")
    with open(path, "w", encoding="utf-8") as fp:
        json.dump({"variants": {"": {"model": "amemaru:block/parlor_stool"}}}, fp, indent=2)
        fp.write("\n")
    print("wrote bases, board, stool, symbols")


if __name__ == "__main__":
    main()
