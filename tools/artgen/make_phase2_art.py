"""AMEMARU: LOST CONNECTION の Phase 2 の仮素材（テクスチャ）を作る。

使い方: python3 tools/artgen/make_phase2_art.py <assets/amemaru のフォルダ>

作るもの（すべて「結晶と糸」のデザイン。濃い藍色・水色の結晶・金の糸）:
  textures/gui/skill_hud.png         スキル枠・ゲージ・詠唱のバー・スキルツリーの節（256x256。SkillHudArt.java と位置を合わせる）
  textures/gui/skill/<職業>/<名前>.png  スキルのアイコン（32x32）
  textures/gui/job/<職業>.png         職業の紋章（64x64）
  textures/item/*.png                見習い装備などのアイテム（16x16）
  textures/item/apprentice_shield_model.png  手に持った見習いの盾の立体の面（32x32。表 12x22・裏 12x22・ふち・持ち手）
  textures/block/oath_crystal*.png    誓約の結晶（16x16）
  textures/entity/training_dummy.png 訓練人形（64x64。プレイヤーのスキンと同じ並び）
  textures/entity/lamp_bolt.png      灯火弾（16x16）
  textures/mob_effect/*.png          状態のアイコン（18x18）

演出（陣・光・パーティクル）のテクスチャは tools/artgen/make_fx_art.py で作る。

同じ名前・同じ大きさで描き直したファイルを置けば、コードを変えずに差し替えられる（docs/phase2.md の8章）。
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    return (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), c[3])


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


# ---------------------------------------------------------------- 色
OUTLINE = hexc("090C14")
INDIGO_0 = hexc("0A0F1C")
INDIGO_1 = hexc("111830")
INDIGO_2 = hexc("1E2B4F")
INDIGO_3 = hexc("2C3E6E")
RIM = hexc("8FD3FF")
RIM_LIGHT = hexc("D6F6FF")
RIM_DIM = hexc("3E5274")
GOLD = hexc("FFC94D")
GOLD_LIGHT = hexc("FFF2A8")
GOLD_DARK = hexc("A87A1C")
WHITE = hexc("F4FAFF")
STEEL = hexc("C9D6EA")
STEEL_DARK = hexc("7C8CA8")
KNIGHT = hexc("6EA8FF")
WIZARD = hexc("B57CFF")
ICE = hexc("BFF2FF")
EMBER = hexc("FF8C40")
FIRE = hexc("FFD27A")
VIOLET = hexc("B57CFF")
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------- 六角（スキル枠）
HEX_W, HEX_H = 24, 26


def hex_points(ox, oy, w=HEX_W, h=HEX_H, inset=0):
    cx = ox + (w - 1) / 2.0
    top, bottom = oy + inset, oy + h - 1 - inset
    left, right = ox + inset, ox + w - 1 - inset
    q = (h - 1) / 4.0
    return [(cx, top), (right, oy + q + inset * 0.5), (right, oy + h - 1 - q - inset * 0.5),
            (cx, bottom), (left, oy + h - 1 - q - inset * 0.5), (left, oy + q + inset * 0.5)]


def hex_mask(w=HEX_W, h=HEX_H, inset=0):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).polygon(hex_points(0, 0, w, h, inset), fill=255)
    return m


def draw_hex_slot(atlas, ox, oy, rim, interior_top, interior_bottom, rim_light=None):
    outer = hex_mask()
    rim_m = hex_mask(inset=1)
    inner = hex_mask(inset=2)
    px = atlas.load()
    for y in range(HEX_H):
        for x in range(HEX_W):
            if inner.getpixel((x, y)):
                t = y / (HEX_H - 1)
                c = mix(interior_top, interior_bottom, t)
                # 中心ほど少し明るい（結晶の中の光）
                d = math.hypot(x - 11.5, y - 12.5) / 13.0
                c = mix(shade(c, 1.25), c, d)
                px[ox + x, oy + y] = c
            elif rim_m.getpixel((x, y)):
                c = rim
                if rim_light is not None and y < HEX_H / 2:
                    c = mix(rim_light, rim, y / (HEX_H / 2))
                px[ox + x, oy + y] = c
            elif outer.getpixel((x, y)):
                px[ox + x, oy + y] = OUTLINE


def draw_hex_fill(atlas, ox, oy, color, inset=2):
    m = hex_mask(inset=inset)
    px = atlas.load()
    for y in range(HEX_H):
        for x in range(HEX_W):
            if m.getpixel((x, y)):
                px[ox + x, oy + y] = color


def draw_hex_rim(atlas, ox, oy, color, width=2):
    outer = hex_mask(inset=0)
    inner = hex_mask(inset=width)
    px = atlas.load()
    for y in range(HEX_H):
        for x in range(HEX_W):
            if outer.getpixel((x, y)) and not inner.getpixel((x, y)):
                px[ox + x, oy + y] = color


def draw_hex_glow(atlas, ox, oy):
    w, h = HEX_W + 4, HEX_H + 4
    px = atlas.load()
    base = hex_mask()
    for y in range(h):
        for x in range(w):
            # 六角のふちからの近さで、光の強さを決める
            best = 99.0
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    sx, sy = x - 2 + dx, y - 2 + dy
                    if 0 <= sx < HEX_W and 0 <= sy < HEX_H and base.getpixel((sx, sy)):
                        best = min(best, math.hypot(dx, dy))
            inside = 0 <= x - 2 < HEX_W and 0 <= y - 2 < HEX_H and hex_mask(inset=1).getpixel((x - 2, y - 2))
            if best < 99 and not inside:
                a = int(200 * max(0.0, 1.0 - best / 2.6))
                if a > 0:
                    px[ox + x, oy + y] = (191, 239, 255, a)


# ---------------------------------------------------------------- 横長のバー
def draw_bar(atlas, ox, oy, w, h, rows, outline=True, frame_only=False):
    px = atlas.load()
    for y in range(h):
        for x in range(w):
            edge = y == 0 or y == h - 1 or x == 0 or x == w - 1
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if corner:
                continue
            if edge:
                if outline:
                    px[ox + x, oy + y] = OUTLINE
                continue
            if frame_only:
                t = (y - 1) / max(1, h - 3)
                px[ox + x, oy + y] = mix(INDIGO_0, INDIGO_2, t)
            else:
                px[ox + x, oy + y] = hexc(rows[min(len(rows) - 1, y - 1)])


# ---------------------------------------------------------------- スキルツリーの節
NODE = 36


def draw_node(atlas, ox, oy, rim, rim2, interior, glow=None, ornate=False):
    px = atlas.load()
    for y in range(NODE):
        for x in range(NODE):
            # 角を落とした四角
            cut = 3
            dx = min(x, NODE - 1 - x)
            dy = min(y, NODE - 1 - y)
            if dx + dy < cut:
                continue
            if dx + dy == cut or x in (0, NODE - 1) or y in (0, NODE - 1):
                px[ox + x, oy + y] = OUTLINE
            elif dx + dy == cut + 1 or x in (1, NODE - 2) or y in (1, NODE - 2):
                c = rim if y < NODE / 2 else rim2
                px[ox + x, oy + y] = c
            elif x in (2, NODE - 3) or y in (2, NODE - 3):
                px[ox + x, oy + y] = shade(rim2, 0.55)
            else:
                t = y / NODE
                px[ox + x, oy + y] = mix(interior, shade(interior, 0.7), t)
    if ornate:
        for (cx, cy) in ((1, 1), (NODE - 2, 1), (1, NODE - 2), (NODE - 2, NODE - 2)):
            for (ddx, ddy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                x, y = cx + ddx, cy + ddy
                if 0 <= x < NODE and 0 <= y < NODE:
                    px[ox + x, oy + y] = GOLD_LIGHT if (ddx, ddy) == (0, 0) else GOLD
    if glow is not None:
        for x in range(4, NODE - 4):
            px[ox + x, oy + 3] = mix(px[ox + x, oy + 3], glow, 0.5)


def draw_select(atlas, ox, oy, size=40):
    px = atlas.load()
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d <= 2:
                a = [230, 150, 70][d]
                px[ox + x, oy + y] = (191, 239, 255, a)


def draw_pip(atlas, ox, oy, fill, edge):
    px = atlas.load()
    pts = [(2, 0), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (2, 4)]
    for (x, y) in pts:
        is_edge = (x, y) in ((2, 0), (0, 2), (4, 2), (2, 4), (1, 1), (3, 1), (1, 3), (3, 3))
        px[ox + x, oy + y] = edge if is_edge else fill


def make_skill_hud():
    atlas = Image.new("RGBA", (256, 256), CLEAR)
    # スキル枠
    draw_hex_slot(atlas, 0, 0, RIM, INDIGO_2, INDIGO_0, RIM_LIGHT)
    draw_hex_slot(atlas, 24, 0, RIM_DIM, hexc("0D1326"), hexc("080B16"))
    draw_hex_fill(atlas, 48, 0, (5, 8, 16, 175), inset=2)
    draw_hex_rim(atlas, 72, 0, GOLD, 2)
    draw_hex_fill(atlas, 96, 0, (40, 60, 120, 120), inset=2)
    draw_hex_fill(atlas, 120, 0, (255, 255, 255, 150), inset=1)
    draw_hex_glow(atlas, 144, 0)
    # 専用ゲージ（中身は白。色はコードで染める）
    draw_bar(atlas, 0, 32, 87, 6, [], frame_only=True)
    draw_bar(atlas, 0, 38, 87, 6, ["FFFFFF", "F0F0F0", "D8D8D8", "B8B8B8"])
    # 詠唱のバー
    draw_bar(atlas, 0, 48, 102, 8, [], frame_only=True)
    draw_bar(atlas, 0, 56, 102, 8, ["F2D7FF", "D9B0FF", "B57CFF", "9A60F0", "7E48D6", "6236B0"])
    # スキルツリーの節
    draw_node(atlas, 0, 96, hexc("3A4560"), hexc("252D40"), hexc("12182A"))
    draw_node(atlas, 36, 96, RIM, hexc("4F8FC4"), INDIGO_1, glow=RIM_LIGHT)
    draw_node(atlas, 72, 96, RIM_LIGHT, RIM, INDIGO_2)
    draw_node(atlas, 108, 96, GOLD_LIGHT, GOLD, INDIGO_2)
    draw_node(atlas, 144, 96, GOLD_LIGHT, GOLD_DARK, hexc("2A1F3F"), ornate=True)
    draw_select(atlas, 184, 96)
    # 段階の印
    draw_pip(atlas, 0, 136, hexc("1A2236"), hexc("4A5E86"))
    draw_pip(atlas, 5, 136, RIM, RIM_LIGHT)
    draw_pip(atlas, 10, 136, hexc("2A2216"), GOLD_DARK)
    draw_pip(atlas, 15, 136, GOLD, GOLD_LIGHT)
    return save(atlas, "textures/gui/skill_hud.png")


# ---------------------------------------------------------------- アイコンの道具
class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), CLEAR)
        self.d = ImageDraw.Draw(self.img)
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((int(x), int(y)), c)

    def line(self, pts, c, width=1):
        self.d.line(pts, fill=c, width=width)

    def poly(self, pts, fill, outline=None):
        self.d.polygon(pts, fill=fill, outline=outline)

    def rect(self, x0, y0, x1, y1, fill, outline=None):
        self.d.rectangle([x0, y0, x1, y1], fill=fill, outline=outline)

    def ellipse(self, x0, y0, x1, y1, fill=None, outline=None, width=1):
        self.d.ellipse([x0, y0, x1, y1], fill=fill, outline=outline, width=width)

    def arc(self, box, start, end, c, width=1):
        self.d.arc(box, start, end, fill=c, width=width)

    def outline_alpha(self, c=OUTLINE):
        """描いた形のまわりに、1ピクセルの暗いふちをつける。"""
        src = self.img.copy()
        sp = src.load()
        for y in range(self.h):
            for x in range(self.w):
                if sp[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and sp[nx, ny][3] > 128:
                            self.img.putpixel((x, y), c)
                            break


def icon_base(accent):
    """スキルのアイコンの下地（32x32）：角を落とした藍色の板、職業の色のふち。"""
    c = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            dx = min(x, 31 - x)
            dy = min(y, 31 - y)
            if dx + dy < 3:
                continue
            if dx == 0 or dy == 0 or dx + dy == 3:
                c.px(x, y, OUTLINE)
            elif dx == 1 or dy == 1 or dx + dy == 4:
                c.px(x, y, mix(shade(accent, 1.15), shade(accent, 0.6), y / 31))
            else:
                t = y / 31
                base = mix(INDIGO_2, INDIGO_0, t)
                d = math.hypot(x - 15.5, y - 13) / 20
                c.px(x, y, mix(mix(base, accent, 0.18), base, d))
    return c


def symbol(draw_fn, accent):
    base = icon_base(accent)
    sym = Canvas(32, 32)
    draw_fn(sym)
    sym.outline_alpha()
    base.img.alpha_composite(sym.img)
    return base.img


def sword(c, x0, y0, x1, y1, blade=STEEL, edge=WHITE, width=3):
    c.line([(x0, y0), (x1, y1)], blade, width)
    c.line([(x0, y0), (x1, y1)], edge, 1)


def shield_shape(c, cx, top, w, h, fill, rim, emblem=None):
    pts = [(cx - w // 2, top), (cx + w // 2, top), (cx + w // 2, top + h * 0.55), (cx, top + h), (cx - w // 2, top + h * 0.55)]
    c.poly(pts, fill=fill, outline=rim)
    if emblem is not None:
        c.line([(cx, top + 3), (cx, top + h - 4)], emblem, 1)
        c.line([(cx - w // 2 + 3, top + h * 0.35), (cx + w // 2 - 3, top + h * 0.35)], emblem, 1)


def star(c, cx, cy, r_out, r_in, fill, points=5, rot=-90):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / points)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    c.poly(pts, fill=fill)


def sparkle(c, x, y, col=WHITE, size=2):
    for i in range(-size, size + 1):
        c.px(x + i, y, col)
        c.px(x, y + i, col)


# ---------------------------------------------------------------- スキルのアイコン
def k_call(c):
    # 角笛と、広がる声
    c.poly([(6, 18), (14, 13), (14, 23)], fill=GOLD)
    c.rect(4, 16, 7, 20, GOLD_DARK)
    for i, r in enumerate((6, 10, 14)):
        c.arc((14 - r, 18 - r, 14 + r, 18 + r), -50, 50, mix(GOLD_LIGHT, RIM, i / 2), 2)


def k_shield_bond(c):
    shield_shape(c, 10, 7, 10, 14, KNIGHT, WHITE)
    shield_shape(c, 22, 11, 10, 14, shade(KNIGHT, 0.8), WHITE)
    c.line([(12, 21), (16, 25), (20, 19)], GOLD, 2)
    sparkle(c, 16, 25, GOLD_LIGHT, 1)


def k_rampart(c):
    shield_shape(c, 16, 5, 20, 24, STEEL_DARK, WHITE)
    for y in (10, 15, 20):
        c.line([(8, y), (24, y)], OUTLINE, 1)
    for (x, y) in ((12, 10), (20, 10), (16, 15), (12, 20), (20, 20)):
        c.line([(x, y), (x, y + 5)], OUTLINE, 1)


def k_guardian_pride(c):
    shield_shape(c, 16, 6, 18, 22, KNIGHT, WHITE)
    star(c, 16, 15, 6, 2.5, GOLD_LIGHT)
    sparkle(c, 25, 6, WHITE, 2)


def k_iron_wall(c):
    shield_shape(c, 16, 5, 20, 24, STEEL, WHITE)
    for y in (11, 17):
        c.line([(8, y), (24, y)], STEEL_DARK, 2)
    for (x, y) in ((10, 8), (22, 8), (10, 14), (22, 14), (16, 22)):
        c.px(x, y, OUTLINE)
        c.px(x + 1, y, WHITE)


def k_charge_slash(c):
    for i, y in enumerate((9, 14, 19)):
        c.line([(3, y + 6), (11, y + 2)], mix(RIM, CLEAR, 0.3 + i * 0.2), 1)
    sword(c, 9, 24, 26, 7)
    c.line([(10, 19), (14, 23)], GOLD, 2)
    c.line([(6, 26), (9, 23)], hexc("6B4A2A"), 2)


def k_whirlwind(c):
    c.arc((4, 4, 28, 28), 200, 340, RIM_LIGHT, 2)
    c.arc((6, 6, 26, 26), 20, 160, RIM, 2)
    sword(c, 10, 22, 22, 10)
    c.line([(11, 17), (15, 21)], GOLD, 2)


def k_unyielding_vow(c):
    for r in (12, 9):
        c.ellipse(16 - r, 16 - r, 16 + r, 16 + r, outline=mix(GOLD, CLEAR, 0.4 if r == 12 else 0.1), width=1)
    sword(c, 16, 4, 16, 27, STEEL, WHITE, 3)
    c.line([(11, 21), (21, 21)], GOLD, 2)
    c.rect(15, 23, 17, 27, hexc("6B4A2A"))


def k_oath_cycle(c):
    c.arc((5, 5, 27, 27), 30, 300, GOLD, 3)
    c.poly([(27, 9), (22, 4), (21, 12)], fill=GOLD_LIGHT)
    c.ellipse(12, 12, 20, 20, fill=RIM)
    sparkle(c, 16, 16, WHITE, 1)


def k_unfallen_castle(c):
    c.rect(6, 12, 26, 28, STEEL)
    c.rect(4, 8, 10, 28, WHITE)
    c.rect(22, 8, 28, 28, WHITE)
    for x in (4, 7, 22, 25):
        c.rect(x, 5, x + 1, 8, WHITE)
    c.poly([(13, 28), (13, 20), (16, 17), (19, 20), (19, 28)], fill=INDIGO_0)
    star(c, 16, 6, 4, 1.8, GOLD_LIGHT)


def w_lamp_bolt(c):
    for i in range(6):
        c.ellipse(4 + i * 2, 18 - i, 8 + i * 2, 22 - i, fill=mix(EMBER, CLEAR, 0.75 - i * 0.1))
    c.ellipse(15, 7, 27, 19, fill=EMBER)
    c.ellipse(17, 9, 25, 17, fill=FIRE)
    c.ellipse(19, 11, 23, 15, fill=WHITE)


def w_meteor_shower(c):
    for (x, y, r) in ((22, 7, 4), (14, 15, 5), (24, 21, 3)):
        c.line([(x - 9, y - 7), (x, y)], mix(EMBER, CLEAR, 0.4), 2)
        c.ellipse(x - r, y - r, x + r, y + r, fill=EMBER)
        c.ellipse(x - r + 1, y - r + 1, x + r - 2, y + r - 2, fill=FIRE)


def w_mana_thread(c):
    c.ellipse(4, 18, 12, 26, fill=VIOLET)
    c.ellipse(20, 6, 28, 14, fill=RIM)
    pts = [(8, 22), (12, 16), (16, 18), (20, 12), (24, 10)]
    c.line(pts, mix(VIOLET, RIM, 0.5), 2)
    sparkle(c, 16, 18, WHITE, 1)


def w_swift_chant(c):
    c.poly([(9, 5), (23, 5), (16, 16)], fill=FIRE)
    c.poly([(16, 16), (9, 27), (23, 27)], fill=shade(FIRE, 0.75))
    c.line([(8, 4), (24, 4)], GOLD_DARK, 2)
    c.line([(8, 28), (24, 28)], GOLD_DARK, 2)
    c.line([(20, 8), (14, 16), (19, 16), (12, 25)], RIM_LIGHT, 2)


def w_mana_resonance(c):
    c.ellipse(4, 8, 20, 24, outline=VIOLET, width=2)
    c.ellipse(12, 8, 28, 24, outline=RIM, width=2)
    sparkle(c, 16, 16, WHITE, 2)
    sparkle(c, 7, 6, RIM_LIGHT, 1)
    sparkle(c, 26, 26, VIOLET, 1)


def w_frost_circle(c):
    c.ellipse(4, 4, 28, 28, outline=ICE, width=2)
    for a in range(0, 180, 60):
        r = math.radians(a)
        dx, dy = math.cos(r) * 9, math.sin(r) * 9
        c.line([(16 - dx, 16 - dy), (16 + dx, 16 + dy)], WHITE, 2)
    for a in range(0, 360, 60):
        r = math.radians(a)
        x, y = 16 + math.cos(r) * 6, 16 + math.sin(r) * 6
        c.px(x, y, RIM)


def w_chain_lightning(c):
    pts = [(5, 6), (13, 13), (9, 16), (19, 22), (15, 24), (27, 28)]
    c.line(pts, FIRE, 3)
    c.line(pts, WHITE, 1)
    for (x, y) in ((5, 6), (19, 22), (27, 28)):
        c.ellipse(x - 2, y - 2, x + 2, y + 2, fill=RIM_LIGHT)


def w_blink(c):
    for i, x in enumerate((6, 11)):
        c.ellipse(x, 10, x + 6, 22, fill=mix(VIOLET, CLEAR, 0.7 - i * 0.2))
    c.ellipse(17, 8, 25, 24, fill=VIOLET)
    star(c, 24, 9, 5, 2, WHITE, 4, -45)


def w_mana_spring(c):
    c.poly([(16, 4), (23, 16), (16, 27), (9, 16)], fill=RIM)
    c.ellipse(9, 13, 23, 27, fill=RIM)
    c.ellipse(12, 15, 17, 20, fill=RIM_LIGHT)
    sparkle(c, 24, 8, WHITE, 1)
    sparkle(c, 7, 9, VIOLET, 1)


def w_starfall(c):
    c.line([(4, 4), (18, 16)], mix(GOLD, CLEAR, 0.4), 4)
    star(c, 20, 18, 10, 4.5, GOLD)
    star(c, 20, 18, 5, 2.2, GOLD_LIGHT)
    sparkle(c, 7, 24, WHITE, 1)
    sparkle(c, 27, 6, WHITE, 1)


# ---- スキルの拡張（11月17日）：ナイト
def k_binding_chain(c):
    # 盾の縁から伸びる鎖と、引き寄せる鉤
    shield_shape(c, 8, 6, 10, 13, KNIGHT, WHITE)
    for i in range(4):
        x, y = 13 + i * 4, 15 + i * 3
        c.ellipse(x - 2, y - 1, x + 2, y + 1, outline=STEEL, width=1)
    c.line([(27, 25), (27, 29), (23, 29)], STEEL_DARK, 2)
    c.line([(27, 25), (24, 22)], WHITE, 1)


def k_parry(c):
    # 斜めの盾と、はじかれる火花
    c.poly([(6, 10), (16, 5), (22, 14), (12, 26)], fill=STEEL, outline=WHITE)
    c.line([(9, 12), (18, 18)], STEEL_DARK, 1)
    for (x, y, r) in ((24, 7, 2), (27, 12, 1), (21, 4, 1)):
        sparkle(c, x, y, GOLD_LIGHT, r)
    c.arc((14, 2, 30, 18), 280, 20, RIM_LIGHT, 2)


def k_oath_circle(c):
    # 地に立てた盾と、光の円陣
    c.ellipse(3, 18, 29, 29, outline=RIM, width=2)
    c.ellipse(7, 20, 25, 27, outline=mix(RIM, CLEAR, 0.4), width=1)
    shield_shape(c, 16, 4, 12, 17, KNIGHT, WHITE, emblem=GOLD_LIGHT)
    sparkle(c, 5, 8, WHITE, 1)
    sparkle(c, 27, 9, WHITE, 1)


def k_taunt_mastery(c):
    # にらむ目と、鈍った刃
    c.ellipse(5, 9, 27, 23, fill=mix(INDIGO_2, KNIGHT, 0.3), outline=WHITE)
    c.ellipse(12, 11, 20, 21, fill=hexc("FF5A40"))
    c.ellipse(14, 13, 18, 19, fill=OUTLINE)
    c.line([(20, 26), (29, 29)], STEEL_DARK, 2)
    c.line([(3, 4), (8, 7)], hexc("FF5A40"), 2)
    c.line([(29, 4), (24, 7)], hexc("FF5A40"), 2)


def k_heavy_slash(c):
    # 振りかぶった大剣と、溜まった力
    c.arc((2, 4, 30, 32), 200, 330, mix(EMBER, CLEAR, 0.2), 3)
    sword(c, 8, 26, 24, 6, STEEL, WHITE, 4)
    c.line([(9, 21), (14, 26)], GOLD, 2)
    for (x, y) in ((25, 4), (28, 9), (21, 2)):
        sparkle(c, x, y, FIRE, 1)


def k_breaker(c):
    # ひびの入った鎧と、拳
    c.poly([(6, 6), (26, 6), (24, 20), (16, 27), (8, 20)], fill=STEEL_DARK, outline=WHITE)
    c.line([(16, 6), (13, 12), (18, 15), (14, 22)], EMBER, 2)
    c.line([(18, 15), (23, 13)], EMBER, 1)


def k_oath_release(c):
    # 突き出した剣と、燃える誓気
    for i, r in enumerate((13, 10, 7)):
        c.ellipse(16 - r, 16 - r, 16 + r, 16 + r, outline=mix(GOLD, CLEAR, 0.2 + i * 0.25), width=1)
    sword(c, 4, 28, 27, 5, STEEL, WHITE, 3)
    c.line([(7, 21), (11, 25)], GOLD, 2)
    star(c, 26, 6, 4, 1.6, GOLD_LIGHT)


def k_leap_slash(c):
    # 弧を描いて跳ぶ剣と、着地の衝撃
    c.arc((3, 3, 29, 29), 180, 300, mix(RIM_LIGHT, CLEAR, 0.3), 2)
    sword(c, 12, 4, 20, 22, STEEL, WHITE, 3)
    c.line([(13, 9), (19, 7)], GOLD, 2)
    c.line([(4, 27), (28, 27)], EMBER, 2)
    for x in (8, 16, 24):
        c.line([(x, 26), (x - 2, 22)], FIRE, 1)


def k_fighting_spirit(c):
    # 燃える心臓
    c.poly([(16, 28), (5, 16), (5, 9), (10, 5), (16, 9), (22, 5), (27, 9), (27, 16)], fill=hexc("D8443A"), outline=WHITE)
    c.poly([(16, 22), (12, 15), (16, 9), (20, 15)], fill=FIRE)
    c.poly([(16, 19), (14, 15), (16, 12), (18, 15)], fill=GOLD_LIGHT)


# ---- スキルの拡張（11月17日）：ウィザード
def w_lamp_barrage(c):
    # 扇に広がる灯火
    for (x, y) in ((8, 22), (12, 13), (19, 9), (25, 14), (26, 23)):
        c.line([(16, 28), (x, y)], mix(EMBER, CLEAR, 0.5), 1)
        c.ellipse(x - 3, y - 3, x + 3, y + 3, fill=EMBER)
        c.ellipse(x - 2, y - 2, x + 1, y + 1, fill=FIRE)
    c.ellipse(13, 25, 19, 31, fill=VIOLET)


def w_light_veil(c):
    # 人を包む光の帳
    c.ellipse(3, 3, 29, 29, outline=GOLD_LIGHT, width=2)
    c.ellipse(6, 6, 26, 26, outline=mix(GOLD, CLEAR, 0.5), width=1)
    c.ellipse(13, 8, 19, 14, fill=WHITE)
    c.poly([(10, 24), (13, 15), (19, 15), (22, 24)], fill=WHITE)
    sparkle(c, 25, 7, GOLD_LIGHT, 2)


def w_star_burst(c):
    # 弾ける星
    for a in range(0, 360, 45):
        r = math.radians(a)
        c.line([(16 + math.cos(r) * 6, 16 + math.sin(r) * 6), (16 + math.cos(r) * 14, 16 + math.sin(r) * 14)],
               mix(GOLD_LIGHT, GOLD, (a % 90) / 90), 2)
    star(c, 16, 16, 7, 3, GOLD)
    star(c, 16, 16, 3.5, 1.5, WHITE)


def w_star_afterglow(c):
    # 星と、戻ってくる光の雫
    star(c, 11, 11, 8, 3.5, GOLD)
    c.arc((8, 8, 28, 28), 20, 110, MANA_BLUE, 2)
    c.poly([(23, 20), (26, 26), (20, 26)], fill=MANA_BLUE)
    c.ellipse(19, 23, 27, 30, fill=MANA_BLUE)


def w_icicle(c):
    # 氷の槍
    c.poly([(4, 28), (26, 4), (29, 7), (8, 30)], fill=ICE, outline=WHITE)
    c.line([(7, 27), (26, 6)], WHITE, 1)
    for (x, y) in ((3, 22), (10, 30), (2, 26)):
        c.px(x, y, RIM_LIGHT)


def w_frost_heart(c):
    # 氷の心臓
    c.poly([(16, 28), (4, 15), (4, 9), (10, 4), (16, 9), (22, 4), (28, 9), (28, 15)], fill=hexc("6FCBFF"), outline=WHITE)
    c.line([(10, 9), (14, 14), (12, 19)], WHITE, 1)
    c.line([(22, 9), (18, 15)], RIM_LIGHT, 1)


def w_thunderbolt(c):
    # 雲から落ちる雷
    c.ellipse(4, 3, 18, 12, fill=STEEL_DARK)
    c.ellipse(12, 2, 28, 13, fill=STEEL_DARK)
    pts = [(17, 12), (12, 19), (17, 19), (11, 29)]
    c.line(pts, FIRE, 3)
    c.line(pts, WHITE, 1)
    sparkle(c, 9, 28, FIRE, 2)


def w_frost_wave(c):
    # 足もとから広がる冷気の波
    for i, r in enumerate((13, 9, 5)):
        c.arc((16 - r, 22 - r * 0.6, 16 + r, 22 + r * 0.6), 180, 360, mix(WHITE, ICE, i / 2), 2)
    for x in (6, 12, 20, 26):
        c.poly([(x - 2, 26), (x, 18 + (x % 3) * 2), (x + 2, 26)], fill=ICE)
    c.line([(3, 27), (29, 27)], RIM, 1)


def w_deep_chill(c):
    # 深く沈む雪の結晶
    for a in range(0, 180, 30):
        r = math.radians(a)
        dx, dy = math.cos(r) * 11, math.sin(r) * 11
        c.line([(16 - dx, 16 - dy), (16 + dx, 16 + dy)], ICE if a % 60 == 0 else RIM, 2 if a % 60 == 0 else 1)
    c.ellipse(12, 12, 20, 20, fill=hexc("2C6FB0"), outline=WHITE)


# ---- 奥義のもう一つ（11月17日）
def k_heaven_cleave(c):
    # 空から突き立つ光の大剣と、地を走る裂け目
    for i in range(3):
        c.line([(16, 1 + i), (16, 2 + i)], mix(GOLD_LIGHT, CLEAR, 0.2 + i * 0.2), 1)
    c.poly([(13, 4), (19, 4), (18, 21), (16, 25), (14, 21)], fill=mix(GOLD_LIGHT, WHITE, 0.4), outline=GOLD)
    c.line([(16, 5), (16, 22)], WHITE, 1)
    c.rect(9, 3, 23, 4, GOLD)
    c.rect(15, 0, 17, 2, GOLD_DARK)
    c.line([(3, 28), (13, 26)], EMBER, 2)
    c.line([(19, 26), (29, 28)], EMBER, 2)
    c.line([(13, 26), (16, 29), (19, 26)], FIRE, 2)
    for (x, y) in ((6, 22), (26, 21), (9, 15), (24, 13)):
        sparkle(c, x, y, GOLD_LIGHT, 1)


def w_ice_garden(c):
    # 氷の花と、まわりを囲む氷の格子
    for x in (4, 9, 23, 28):
        c.line([(x, 6), (x, 28)], mix(ICE, CLEAR, 0.25), 2)
    c.line([(3, 28), (29, 28)], RIM, 1)
    for a in range(0, 360, 60):
        r = math.radians(a - 90)
        tip = (16 + math.cos(r) * 10, 17 + math.sin(r) * 10)
        left = (16 + math.cos(r - 0.45) * 4, 17 + math.sin(r - 0.45) * 4)
        right = (16 + math.cos(r + 0.45) * 4, 17 + math.sin(r + 0.45) * 4)
        c.poly([left, tip, right, (16, 17)], fill=ICE if a % 120 == 0 else hexc("8FD8FF"), outline=WHITE)
    c.ellipse(13, 14, 19, 20, fill=WHITE)
    sparkle(c, 16, 17, hexc("8FD8FF"), 1)


MANA_BLUE = hexc("7CC4FF")


SKILLS = {
    "knight/call": (k_call, KNIGHT),
    "knight/shield_bond": (k_shield_bond, KNIGHT),
    "knight/rampart": (k_rampart, KNIGHT),
    "knight/guardian_pride": (k_guardian_pride, hexc("8FD3FF")),
    "knight/iron_wall": (k_iron_wall, hexc("8FD3FF")),
    "knight/charge_slash": (k_charge_slash, hexc("FFB46E")),
    "knight/whirlwind": (k_whirlwind, hexc("FFB46E")),
    "knight/unyielding_vow": (k_unyielding_vow, hexc("FFB46E")),
    "knight/oath_cycle": (k_oath_cycle, hexc("FFB46E")),
    "knight/unfallen_castle": (k_unfallen_castle, GOLD),
    "wizard/lamp_bolt": (w_lamp_bolt, hexc("FFD27A")),
    "wizard/meteor_shower": (w_meteor_shower, hexc("FFD27A")),
    "wizard/mana_thread": (w_mana_thread, hexc("FFD27A")),
    "wizard/swift_chant": (w_swift_chant, hexc("FFD27A")),
    "wizard/mana_resonance": (w_mana_resonance, hexc("FFD27A")),
    "wizard/frost_circle": (w_frost_circle, hexc("9FE6FF")),
    "wizard/chain_lightning": (w_chain_lightning, hexc("9FE6FF")),
    "wizard/blink": (w_blink, hexc("9FE6FF")),
    "wizard/mana_spring": (w_mana_spring, hexc("9FE6FF")),
    "wizard/starfall": (w_starfall, GOLD),
    "knight/binding_chain": (k_binding_chain, KNIGHT),
    "knight/parry": (k_parry, KNIGHT),
    "knight/oath_circle": (k_oath_circle, KNIGHT),
    "knight/taunt_mastery": (k_taunt_mastery, hexc("8FD3FF")),
    "knight/heavy_slash": (k_heavy_slash, hexc("FFB46E")),
    "knight/breaker": (k_breaker, hexc("FFB46E")),
    "knight/oath_release": (k_oath_release, hexc("FFB46E")),
    "knight/leap_slash": (k_leap_slash, hexc("FFB46E")),
    "knight/fighting_spirit": (k_fighting_spirit, hexc("FFB46E")),
    "wizard/lamp_barrage": (w_lamp_barrage, hexc("FFD27A")),
    "wizard/light_veil": (w_light_veil, hexc("FFD27A")),
    "wizard/star_burst": (w_star_burst, hexc("FFD27A")),
    "wizard/star_afterglow": (w_star_afterglow, hexc("FFD27A")),
    "wizard/icicle": (w_icicle, hexc("9FE6FF")),
    "wizard/frost_heart": (w_frost_heart, hexc("9FE6FF")),
    "wizard/thunderbolt": (w_thunderbolt, hexc("9FE6FF")),
    "wizard/frost_wave": (w_frost_wave, hexc("9FE6FF")),
    "wizard/deep_chill": (w_deep_chill, hexc("9FE6FF")),
    "knight/heaven_cleave": (k_heaven_cleave, GOLD),
    "wizard/ice_garden": (w_ice_garden, GOLD),
}


def make_skill_icons():
    out = []
    for name, (fn, accent) in SKILLS.items():
        out.append(save(symbol(fn, accent), "textures/gui/skill/%s.png" % name))
    return out


# ---------------------------------------------------------------- 職業の紋章（64x64）
def emblem(accent, draw_fn):
    c = Canvas(64, 64)
    # 外側の結晶の枠（八角形）
    pts = []
    for i in range(8):
        a = math.radians(22.5 + i * 45)
        pts.append((31.5 + math.cos(a) * 31, 31.5 + math.sin(a) * 31))
    c.poly(pts, fill=OUTLINE)
    pts2 = [(31.5 + (p[0] - 31.5) * 0.93, 31.5 + (p[1] - 31.5) * 0.93) for p in pts]
    c.poly(pts2, fill=shade(accent, 0.75))
    pts3 = [(31.5 + (p[0] - 31.5) * 0.86, 31.5 + (p[1] - 31.5) * 0.86) for p in pts]
    c.poly(pts3, fill=INDIGO_1)
    for y in range(64):
        for x in range(64):
            p = c.img.getpixel((x, y))
            if p == INDIGO_1:
                d = math.hypot(x - 31.5, y - 26) / 32
                c.px(x, y, mix(mix(INDIGO_2, accent, 0.25), INDIGO_0, d))
    sym = Canvas(64, 64)
    draw_fn(sym)
    sym.outline_alpha()
    c.img.alpha_composite(sym.img)
    # 上のきらめき
    for i in range(-3, 4):
        c.px(31 + i, 4, mix(RIM_LIGHT, CLEAR, abs(i) / 4))
    return c.img


def knight_emblem(c):
    shield_shape(c, 32, 14, 28, 36, KNIGHT, WHITE)
    c.line([(32, 18), (32, 46)], WHITE, 2)
    c.line([(22, 28), (42, 28)], WHITE, 2)
    sword(c, 14, 50, 50, 14, STEEL, WHITE, 3)
    c.line([(18, 40), (24, 46)], GOLD, 3)


def wizard_emblem(c):
    c.line([(18, 52), (38, 20)], hexc("8A6A4A"), 4)
    c.line([(18, 52), (38, 20)], hexc("B89060"), 1)
    c.ellipse(34, 10, 48, 24, fill=VIOLET)
    c.ellipse(37, 13, 45, 21, fill=hexc("E2C8FF"))
    star(c, 22, 18, 7, 3, GOLD_LIGHT)
    star(c, 48, 42, 5, 2, RIM_LIGHT)
    sparkle(c, 44, 30, WHITE, 2)


def make_job_emblems():
    return [save(emblem(KNIGHT, knight_emblem), "textures/gui/job/knight.png"),
            save(emblem(WIZARD, wizard_emblem), "textures/gui/job/wizard.png")]


# ---------------------------------------------------------------- アイテム（16x16）
def item_canvas():
    return Canvas(16, 16)


def finish_item(c):
    c.outline_alpha()
    return c.img


def item_sword():
    c = item_canvas()
    c.line([(4, 11), (13, 2)], STEEL, 2)
    c.line([(5, 11), (13, 3)], WHITE, 1)
    c.line([(3, 9), (6, 12)], GOLD, 1)
    c.line([(2, 13), (4, 11)], hexc("6B4A2A"), 2)
    c.px(14, 1, RIM_LIGHT)
    return finish_item(c)


def item_greatsword():
    c = item_canvas()
    c.line([(4, 11), (14, 1)], STEEL, 3)
    c.line([(5, 11), (14, 2)], WHITE, 1)
    c.line([(2, 8), (7, 13)], GOLD, 2)
    c.line([(1, 14), (3, 12)], hexc("6B4A2A"), 2)
    c.px(8, 7, RIM)
    return finish_item(c)


def item_staff():
    c = item_canvas()
    c.line([(3, 14), (11, 4)], hexc("8A6A4A"), 2)
    c.line([(4, 14), (11, 5)], hexc("B89060"), 1)
    c.ellipse(9, 1, 14, 6, fill=VIOLET)
    c.px(11, 3, WHITE)
    c.px(13, 2, RIM_LIGHT)
    return finish_item(c)


def item_forget_water():
    c = item_canvas()
    c.rect(6, 1, 9, 3, hexc("A88060"))
    c.ellipse(3, 4, 12, 14, fill=hexc("CFE8FF", 200))
    c.ellipse(4, 8, 11, 13, fill=hexc("6EC8FF"))
    c.px(5, 6, WHITE)
    c.px(9, 10, RIM_LIGHT)
    return finish_item(c)


def item_blank_oath():
    c = item_canvas()
    c.rect(3, 2, 12, 13, hexc("F2EAD8"))
    for y in (5, 7, 9):
        c.line([(5, y), (10, y)], hexc("C8BCA0"), 1)
    c.ellipse(9, 10, 13, 14, fill=hexc("C83C3C"))
    c.px(11, 12, GOLD_LIGHT)
    return finish_item(c)


def item_job_change_token():
    c = item_canvas()
    c.ellipse(2, 2, 13, 13, fill=GOLD)
    c.ellipse(4, 4, 11, 11, fill=GOLD_DARK)
    c.poly([(7.5, 4), (10, 7.5), (7.5, 11), (5, 7.5)], fill=RIM)
    c.px(7, 6, WHITE)
    return finish_item(c)


def item_training_dummy():
    c = item_canvas()
    c.line([(8, 6), (8, 15)], hexc("6B4A2A"), 2)
    c.line([(3, 8), (13, 8)], hexc("6B4A2A"), 1)
    c.ellipse(5, 1, 11, 7, fill=hexc("D8B878"))
    c.rect(6, 7, 10, 12, hexc("C8A060"))
    c.px(7, 3, RIM)
    c.px(9, 3, RIM)
    return finish_item(c)


def make_items():
    out = []
    # 見習いの剣・大剣・杖の絵は、作り直した make_gear_apprentice.py が書く（ここでは書かない）
    for name, fn in (("forget_water", item_forget_water), ("blank_oath", item_blank_oath),
                     ("job_change_token", item_job_change_token), ("training_dummy", item_training_dummy)):
        out.append(save(fn(), "textures/item/%s.png" % name))
    return out


def make_shield_model():
    """見習いの盾を手に持ったときの立体の面（32x32）。models/item/apprentice_shield_model.json の uv と位置を合わせる。

    表: x0-11, y0-21 ／ 裏: x12-23, y0-21 ／ 縦のふち: x24, y0-21 ／ 横のふち: x0-11, y22 ／ 持ち手: x26-31, y0-5
    """
    rnd = random.Random(5)
    img = Image.new("RGBA", (32, 32), CLEAR)
    px = img.load()
    # 表：藍色の板、鋼のふち、金の十字と結晶
    for y in range(22):
        for x in range(12):
            edge = x in (0, 11) or y in (0, 21)
            if edge:
                c = STEEL if y < 11 else STEEL_DARK
            else:
                t = y / 21
                c = mix(shade(KNIGHT, 0.85), shade(KNIGHT, 0.5), t)
                if (x + y) % 9 == 0:
                    c = shade(c, 1.08)
            if not edge and (x in (5, 6) and 2 <= y <= 19 or y in (8, 9) and 2 <= x <= 9):
                c = GOLD if (x + y) % 2 else GOLD_LIGHT
            px[x, y] = c
    for (x, y) in ((5, 8), (6, 8), (5, 9), (6, 9)):
        px[x, y] = RIM_LIGHT
    for (x, y) in ((1, 1), (10, 1), (1, 20), (10, 20)):
        px[x, y] = WHITE
    # 裏：木の板と革の帯
    for y in range(22):
        for x in range(12):
            v = 0.85 + rnd.random() * 0.15
            c = shade(hexc("8A6A4A"), v)
            if x in (3, 7):
                c = shade(c, 0.75)
            if y in (6, 15):
                c = hexc("5A3A22")
            if x in (0, 11) or y in (0, 21):
                c = STEEL_DARK
            px[12 + x, y] = c
    # ふち
    for y in range(22):
        px[24, y] = STEEL if y < 11 else STEEL_DARK
    for x in range(12):
        px[x, 22] = STEEL
    # 持ち手
    for y in range(6):
        for x in range(6):
            px[26 + x, y] = shade(hexc("5A3A22"), 0.9 + rnd.random() * 0.1)
    return save(img, "textures/item/apprentice_shield_model.png")


# ---------------------------------------------------------------- 誓約の結晶（ブロック）
def make_block():
    rnd = random.Random(7)
    crystal = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            c = mix(hexc("DDF7FF"), hexc("4EA6E0"), t)
            if (x - y) % 7 == 0:
                c = mix(c, WHITE, 0.5)
            if rnd.random() < 0.06:
                c = mix(c, RIM_LIGHT, 0.6)
            crystal.putpixel((x, y), c)
    base = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 0.85 + rnd.random() * 0.15
            c = shade(hexc("3A4560"), v)
            if y in (0, 15) or x in (0, 15):
                c = shade(c, 0.7)
            if y == 3 and 2 <= x <= 13 or x == 8 and 4 <= y <= 11:
                c = GOLD
            base.putpixel((x, y), c)
    return [save(crystal, "textures/block/oath_crystal.png"), save(base, "textures/block/oath_crystal_base.png")]


# ---------------------------------------------------------------- 訓練人形（64x64 のスキンの並び）
def make_dummy():
    rnd = random.Random(11)
    img = Image.new("RGBA", (64, 64), CLEAR)
    straw = hexc("D8B878")
    straw_dark = hexc("A88450")
    sack = hexc("C9B08A")
    rope = hexc("6B4A2A")

    def fill(x0, y0, w, h, base, noise=0.12, bands=()):
        for y in range(h):
            for x in range(w):
                v = 1.0 - noise / 2 + rnd.random() * noise
                c = shade(base, v)
                if (x + y * 3) % 5 == 0:
                    c = mix(c, straw_dark, 0.35)
                if y in bands:
                    c = rope
                img.putpixel((x0 + x, y0 + y), c)

    # 頭（麻袋）: 上・下・右・前・左・後ろ
    for (x, y) in ((8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)):
        fill(x, y, 8, 8, sack, 0.1)
    # 顔：結晶の目と、縫い目の口
    for (x, y) in ((10, 11), (13, 11)):
        img.putpixel((x, y), RIM)
        img.putpixel((x, y + 1), hexc("4EA6E0"))
    for x in range(10, 14):
        img.putpixel((x, 14), rope if x % 2 == 0 else sack)
    # 体：上・下・右・前・左・後ろ
    fill(20, 16, 8, 4, straw)
    fill(28, 16, 8, 4, straw)
    fill(16, 20, 4, 12, straw, bands=(3, 8))
    fill(20, 20, 8, 12, straw, bands=(3, 8))
    fill(28, 20, 4, 12, straw, bands=(3, 8))
    fill(32, 20, 8, 12, straw, bands=(3, 8))
    # 胸の結晶（的の中心）
    for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        img.putpixel((24 + dx, 24 + dy), RIM if (dx, dy) != (0, 0) else WHITE)
    # 腕
    fill(44, 16, 4, 4, straw)
    fill(48, 16, 4, 4, straw)
    for x0 in (40, 44, 48, 52):
        fill(x0, 20, 4, 12, straw, bands=(5,))
    # 脚（支柱の木）
    fill(4, 16, 4, 4, rope, 0.2)
    fill(8, 16, 4, 4, rope, 0.2)
    for x0 in (0, 4, 8, 12):
        fill(x0, 20, 4, 12, hexc("8A6A4A"), 0.2)
    return save(img, "textures/entity/training_dummy.png")


def make_lamp_bolt():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            if d > 1:
                continue
            if d < 0.3:
                c = WHITE
            elif d < 0.6:
                c = mix(WHITE, FIRE, (d - 0.3) / 0.3)
            else:
                c = mix(FIRE, EMBER, (d - 0.6) / 0.4)
            a = int(255 * min(1.0, (1.0 - d) * 2.2))
            img.putpixel((x, y), (c[0], c[1], c[2], a))
    return save(img, "textures/entity/lamp_bolt.png")


# ---------------------------------------------------------------- 状態のアイコン（18x18）
def effect_icon(draw_fn):
    c = Canvas(18, 18)
    draw_fn(c)
    c.outline_alpha()
    return c.img


def e_stagger(c):
    star(c, 9, 9, 7, 3, GOLD)
    for (x, y) in ((3, 3), (15, 4), (4, 15)):
        sparkle(c, x, y, WHITE, 1)


def e_frost(c):
    for a in range(0, 180, 60):
        r = math.radians(a)
        c.line([(9 - math.cos(r) * 7, 9 - math.sin(r) * 7), (9 + math.cos(r) * 7, 9 + math.sin(r) * 7)], ICE, 2)
    c.px(9, 9, WHITE)


def e_rampart(c):
    shield_shape(c, 9, 2, 13, 15, STEEL_DARK, WHITE)
    c.line([(4, 7), (14, 7)], OUTLINE, 1)


def e_unyielding(c):
    sword(c, 9, 1, 9, 16, STEEL, WHITE, 3)
    c.line([(5, 12), (13, 12)], GOLD, 2)


def e_shield_bond(c):
    shield_shape(c, 7, 3, 9, 11, KNIGHT, WHITE)
    c.line([(10, 12), (13, 15), (16, 10)], GOLD, 2)


def e_fortress(c):
    c.rect(3, 6, 15, 16, STEEL)
    c.rect(2, 3, 6, 16, WHITE)
    c.rect(12, 3, 16, 16, WHITE)
    c.rect(7, 11, 11, 16, INDIGO_0)


def e_link_support(c):
    c.line([(2, 12), (6, 6), (11, 11), (16, 4)], GOLD, 2)
    star(c, 15, 4, 3, 1.3, GOLD_LIGHT)


def e_chanting(c):
    c.ellipse(2, 2, 16, 16, outline=VIOLET, width=2)
    star(c, 9, 9, 4, 1.8, hexc("E2C8FF"))


def e_frozen(c):
    c.rect(3, 3, 15, 15, hexc("BFF2FF", 190), outline=WHITE)
    c.line([(5, 12), (8, 7), (11, 10)], WHITE, 1)
    c.px(12, 5, WHITE)


def e_shock(c):
    pts = [(11, 1), (5, 9), (10, 9), (6, 17)]
    c.line(pts, FIRE, 3)
    c.line(pts, WHITE, 1)


def e_star_brand(c):
    star(c, 9, 9, 8, 3.3, GOLD)
    star(c, 9, 9, 3.5, 1.5, WHITE)


def e_armor_break(c):
    shield_shape(c, 9, 2, 13, 15, STEEL_DARK, WHITE)
    c.line([(9, 3), (7, 8), (11, 10), (8, 15)], EMBER, 2)


def e_barrier(c):
    c.ellipse(1, 1, 17, 17, outline=GOLD_LIGHT, width=2)
    c.ellipse(5, 5, 13, 13, fill=hexc("FFE9A0", 170))
    sparkle(c, 13, 4, WHITE, 1)


def e_ward(c):
    c.ellipse(1, 9, 17, 17, outline=RIM, width=1)
    shield_shape(c, 9, 1, 10, 12, KNIGHT, WHITE)


def e_parry(c):
    c.poly([(3, 6), (10, 2), (15, 9), (8, 16)], fill=STEEL, outline=WHITE)
    sparkle(c, 15, 3, GOLD_LIGHT, 1)


def e_charging(c):
    c.arc((1, 1, 17, 17), 200, 520, FIRE, 2)
    c.ellipse(6, 6, 12, 12, fill=EMBER)
    c.px(9, 9, WHITE)


def make_effects():
    out = []
    for name, fn in (("stagger", e_stagger), ("frost", e_frost), ("rampart", e_rampart), ("unyielding", e_unyielding),
                     ("shield_bond", e_shield_bond), ("fortress", e_fortress), ("link_support", e_link_support),
                     ("chanting", e_chanting), ("frozen", e_frozen), ("shock", e_shock), ("star_brand", e_star_brand),
                     ("armor_break", e_armor_break), ("barrier", e_barrier), ("ward", e_ward), ("parry", e_parry),
                     ("charging", e_charging)):
        out.append(save(effect_icon(fn), "textures/mob_effect/%s.png" % name))
    return out


if __name__ == "__main__":
    files = [make_skill_hud()]
    files += make_skill_icons()
    files += make_job_emblems()
    files += make_items()
    # 見習いの盾は make_gear_apprentice.py が作る（ここでは書かない）
    files += make_block()
    files.append(make_dummy())
    files.append(make_lamp_bolt())
    files += make_effects()
    for f in files:
        print("wrote", f)
