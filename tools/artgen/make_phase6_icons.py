"""Phase 6 その2：4職業（ヒーラー・アーチャー・ローグ・モンク）と、ナイト・ウィザードの足した段のアイコン【仮素材】。

使い方: python3 tools/artgen/make_phase6_icons.py   （リポジトリの一番上で）

作るもの（make_phase2_art.py と同じ「結晶と糸」の下地・色・ふち）:
  textures/gui/skill/<職業>/<名前>.png  スキルのアイコン（32x32）。128個
  textures/gui/job/<職業>.png         職業の紋章（64x64）。4つ
  textures/mob_effect/<名前>.png       状態のアイコン（18x18）。21個

同じ名前・同じ大きさで描き直したファイルを置けば、コードを変えずに差し替えられる。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from make_phase2_art import (CLEAR, EMBER, FIRE, GOLD, GOLD_DARK, GOLD_LIGHT, ICE, KNIGHT, OUTLINE, RIM, RIM_LIGHT, STEEL,  # noqa: E402
                             STEEL_DARK, VIOLET, WHITE, WIZARD, Canvas, effect_icon, emblem, hexc, mix, save, shade,
                             shield_shape, sparkle, star, sword, symbol)

# ---------------------------------------------------------------- 色
HEALER = hexc("8CF0B4")
ARCHER = hexc("9FD8FF")
ROGUE = hexc("B58CFF")
MONK = hexc("FF9A5C")

LIFE = hexc("B8FF9C")
LIFE_DARK = hexc("5FB85A")
SAGE = hexc("9CF0C8")
BELL = hexc("FFE7A0")
BELL_DARK = hexc("C89A3A")
PETAL = hexc("FFC8E0")
WOOD = hexc("8A6A4A")
WOOD_LIGHT = hexc("B89060")
FEATHER = hexc("E8FFF0")
WIND = hexc("A8F0C8")
MARK_RED = hexc("FF5A4A")
SHADOW = hexc("8A6CFF")
SHADOW_DARK = hexc("4A3A7A")
BLADE = hexc("D8D0FF")
VENOM = hexc("9CF060")
VENOM_DARK = hexc("4E8A2A")
SMOKE = hexc("9C98AA")
SMOKE_DARK = hexc("625E70")
FIST = hexc("FFB46E")
FIST_DARK = hexc("B8643A")
SKIN = hexc("F0C8A0")
KI = hexc("9FDCFF")
THUNDER = hexc("FFF2A0")
EARTH = hexc("C8A070")
EARTH_DARK = hexc("7A5A3A")
FROST = hexc("7FD8FF")


# ================================================================ 形の部品
def bell(c, cx, cy, s, col=BELL, rim=BELL_DARK):
    """鈴（cx, cy は上の輪、s は大きさ）。"""
    c.ellipse(cx - 1.5 * s / 4, cy - s / 4, cx + 1.5 * s / 4, cy + s / 4, outline=rim, width=1)
    top = cy + s / 5
    c.poly([(cx - s * 0.35, top + s * 0.2), (cx - s * 0.25, top), (cx + s * 0.25, top), (cx + s * 0.35, top + s * 0.2),
            (cx + s * 0.55, top + s * 0.75), (cx - s * 0.55, top + s * 0.75)], fill=col)
    c.line([(cx - s * 0.55, top + s * 0.75), (cx + s * 0.55, top + s * 0.75)], rim, 1)
    c.ellipse(cx - 1.5, top + s * 0.75 - 0.5, cx + 1.5, top + s * 0.75 + 2.5, fill=rim)
    c.line([(cx - s * 0.18, top + s * 0.18), (cx - s * 0.28, top + s * 0.6)], WHITE, 1)


def waves(c, cx, cy, radii, col, start=-50, end=50, width=1):
    for i, r in enumerate(radii):
        c.arc((cx - r, cy - r, cx + r, cy + r), start, end, mix(col, CLEAR, i * 0.25), width)


def cross(c, cx, cy, s, col=LIFE, light=WHITE):
    c.rect(cx - s // 3, cy - s, cx + s // 3, cy + s, col)
    c.rect(cx - s, cy - s // 3, cx + s, cy + s // 3, col)
    c.line([(cx, cy - s + 1), (cx, cy + s - 1)], light, 1)


def petal(c, cx, cy, r, angle, col=PETAL):
    a = math.radians(angle)
    x, y = cx + math.cos(a) * r, cy + math.sin(a) * r
    c.ellipse(x - 2, y - 2, x + 2, y + 2, fill=col)


def flower(c, cx, cy, r, col=PETAL, center=GOLD_LIGHT, n=5):
    for i in range(n):
        petal(c, cx, cy, r, i * 360 / n - 90, col)
    c.ellipse(cx - 1.5, cy - 1.5, cx + 1.5, cy + 1.5, fill=center)


def leaf(c, x, y, col=LIFE):
    c.poly([(x, y), (x + 3, y - 2), (x + 5, y + 1), (x + 2, y + 3)], fill=col)


def person(c, cx, top, col, s=1.0):
    r = 3 * s
    c.ellipse(cx - r, top, cx + r, top + 2 * r, fill=col)
    c.poly([(cx - 5 * s, top + 2 * r + 9 * s), (cx - 3 * s, top + 2 * r + 1), (cx + 3 * s, top + 2 * r + 1), (cx + 5 * s, top + 2 * r + 9 * s)],
           fill=col)


def arrow(c, x0, y0, x1, y1, shaft=WOOD_LIGHT, head=WHITE, fletch=FEATHER, width=1):
    c.line([(x0, y0), (x1, y1)], shaft, width)
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy) or 1
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    c.poly([(x1 + ux * 3, y1 + uy * 3), (x1 + px * 2, y1 + py * 2), (x1 - px * 2, y1 - py * 2)], fill=head)
    for i in (0, 2):
        bx, by = x0 + ux * i, y0 + uy * i
        c.line([(bx, by), (bx - ux * 2 + px * 2, by - uy * 2 + py * 2)], fletch, 1)
        c.line([(bx, by), (bx - ux * 2 - px * 2, by - uy * 2 - py * 2)], fletch, 1)


def bow(c, cx, cy, r, start=110, end=250, col=WOOD_LIGHT, string=WHITE):
    c.arc((cx - r, cy - r, cx + r, cy + r), start, end, col, 2)
    a0, a1 = math.radians(start), math.radians(end)
    c.line([(cx + math.cos(a0) * r, cy + math.sin(a0) * r), (cx + math.cos(a1) * r, cy + math.sin(a1) * r)], string, 1)


def dagger(c, x0, y0, x1, y1, blade=BLADE, edge=WHITE, hilt=SHADOW_DARK):
    c.line([(x0, y0), (x1, y1)], blade, 2)
    c.line([(x0, y0), (x1, y1)], edge, 1)
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy) or 1
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    c.line([(x0 + px * 3, y0 + py * 3), (x0 - px * 3, y0 - py * 3)], GOLD, 1)
    c.line([(x0, y0), (x0 - ux * 4, y0 - uy * 4)], hilt, 2)


def droplet(c, cx, cy, s, col=VENOM, light=WHITE):
    c.poly([(cx, cy - s * 1.6), (cx - s, cy), (cx + s, cy)], fill=col)
    c.ellipse(cx - s, cy - s, cx + s, cy + s, fill=col)
    c.px(cx - s / 3, cy - s / 3, light)


def cloud(c, cx, cy, s, col=SMOKE, dark=SMOKE_DARK):
    for (dx, dy, r) in ((-s, 1, s * 0.8), (s, 1, s * 0.8), (0, -s * 0.4, s), (0, s * 0.6, s * 0.8)):
        c.ellipse(cx + dx - r, cy + dy - r, cx + dx + r, cy + dy + r, fill=col)
    c.arc((cx - s * 1.6, cy - s, cx + s * 1.6, cy + s * 1.6), 20, 160, dark, 1)


def fist(c, cx, cy, s, col=FIST, dark=FIST_DARK):
    c.rect(cx - s, cy - s * 0.8, cx + s, cy + s * 0.8, col)
    for i in range(3):
        x = cx - s + (i + 1) * (2 * s) / 4
        c.line([(x, cy - s * 0.8), (x, cy - s * 0.2)], dark, 1)
    c.rect(cx - s, cy + s * 0.4, cx - s * 0.2, cy + s * 0.8, dark)


def palm(c, cx, cy, s, col=SKIN, dark=FIST_DARK):
    c.rect(cx - s * 0.8, cy - s * 0.4, cx + s * 0.8, cy + s, col)
    for i in range(4):
        x = cx - s * 0.6 + i * s * 0.4
        c.rect(x - 0.6, cy - s * 1.3, x + 0.6, cy - s * 0.4, col)
    c.line([(cx + s * 0.8, cy), (cx + s * 1.3, cy - s * 0.5)], col, 2)
    c.line([(cx - s * 0.8, cy + s), (cx + s * 0.8, cy + s)], dark, 1)


def swirl(c, cx, cy, r, col, turns=1.5, width=1):
    pts = []
    steps = 40
    for i in range(steps + 1):
        t = i / steps
        a = t * turns * math.pi * 2
        rr = r * t
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    c.line(pts, col, width)


def bolt(c, pts, col=THUNDER, core=WHITE):
    c.line(pts, col, 3)
    c.line(pts, core, 1)


def eye(c, cx, cy, w, h, col=WHITE, iris=ARCHER):
    c.ellipse(cx - w, cy - h, cx + w, cy + h, fill=col)
    c.ellipse(cx - h, cy - h, cx + h, cy + h, fill=iris)
    c.ellipse(cx - 1, cy - 1, cx + 1, cy + 1, fill=OUTLINE)


def ring(c, cx, cy, r, col, width=1):
    c.ellipse(cx - r, cy - r, cx + r, cy + r, outline=col, width=width)


def thread(c, pts, col=GOLD, width=1):
    c.line(pts, col, width)


def snowflake(c, cx, cy, r, col=ICE):
    for a in range(0, 180, 60):
        rad = math.radians(a)
        c.line([(cx - math.cos(rad) * r, cy - math.sin(rad) * r), (cx + math.cos(rad) * r, cy + math.sin(rad) * r)], col, 1)
    c.px(cx, cy, WHITE)


def comet(c, x, y, r, tail=(10, 8), col=FIRE, core=WHITE):
    c.line([(x - tail[0], y - tail[1]), (x, y)], mix(EMBER, CLEAR, 0.35), 3)
    c.line([(x - tail[0] * 0.7, y - tail[1] * 0.7), (x, y)], col, 1)
    c.ellipse(x - r, y - r, x + r, y + r, fill=col)
    c.ellipse(x - r / 2, y - r / 2, x + r / 2, y + r / 2, fill=core)


def greatsword(c, x0, y0, x1, y1):
    c.line([(x0, y0), (x1, y1)], STEEL, 4)
    c.line([(x0, y0), (x1, y1)], WHITE, 1)


def plus_mark(c, x, y, col=GOLD_LIGHT):
    c.line([(x - 2, y), (x + 2, y)], col, 1)
    c.line([(x, y - 2), (x, y + 2)], col, 1)


def up_arrow(c, x, y, col=GOLD_LIGHT):
    c.poly([(x, y - 3), (x - 3, y + 1), (x + 3, y + 1)], fill=col)
    c.rect(x - 1, y + 1, x + 1, y + 4, col)


def chain_links(c, x0, y0, x1, y1, n, col=STEEL):
    for i in range(n):
        t = (i + 0.5) / n
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        c.ellipse(x - 2, y - 1.5, x + 2, y + 1.5, outline=col, width=1)


# ================================================================ ヒーラー
def h_bell_heal(c):
    bell(c, 14, 4, 14)
    plus_mark(c, 25, 9, LIFE)
    waves(c, 14, 15, (11, 14), LIFE, -40, 40)


def h_prayer_circle(c):
    ring(c, 16, 18, 11, LIFE, 2)
    for a in range(0, 360, 60):
        petal(c, 16, 18, 11, a, LIFE)
    cross(c, 16, 18, 4)


def h_bond_mend(c):
    c.ellipse(3, 8, 13, 18, fill=SAGE)
    c.ellipse(19, 14, 29, 24, fill=SAGE)
    thread(c, [(10, 15), (14, 12), (18, 18), (22, 17)], GOLD, 1)
    cross(c, 8, 13, 2, WHITE, SAGE)
    sparkle(c, 24, 7, WHITE, 2)


def h_call_back(c):
    c.rect(4, 22, 28, 26, SHADOW_DARK)
    person(c, 16, 3, LIFE)
    for x in (8, 24):
        up_arrow(c, x, 13, GOLD_LIGHT)
    c.line([(6, 22), (26, 22)], GOLD, 1)


def h_life_thread(c):
    thread(c, [(4, 26), (9, 18), (13, 21), (18, 12), (23, 15), (28, 6)], GOLD, 2)
    c.ellipse(24, 3, 30, 9, fill=LIFE)
    c.ellipse(2, 23, 8, 29, fill=GOLD_LIGHT)
    sparkle(c, 16, 9, WHITE, 1)


def h_swelling_bell(c):
    ring(c, 16, 16, 13, mix(LIFE, CLEAR, 0.5), 1)
    ring(c, 16, 16, 10, LIFE, 1)
    bell(c, 16, 6, 13)
    plus_mark(c, 26, 25, LIFE)


def h_resonant_healing(c):
    bell(c, 9, 5, 9)
    bell(c, 22, 9, 9)
    waves(c, 16, 22, (6, 9, 12), LIFE, 200, 340)


def h_healing_rain(c):
    cloud(c, 16, 8, 5, hexc("D8F0FF"), hexc("8AA8C8"))
    for (x, y) in ((8, 17), (14, 21), (20, 18), (25, 23), (11, 26)):
        droplet(c, x, y, 1.6, LIFE)


def h_great_call(c):
    for r in (14, 10):
        ring(c, 16, 17, r, mix(GOLD_LIGHT, CLEAR, 0.3 if r == 14 else 0.0), 1)
    person(c, 10, 9, LIFE, 0.8)
    person(c, 22, 9, LIFE, 0.8)
    person(c, 16, 5, GOLD_LIGHT, 0.9)
    c.line([(16, 1), (16, 4)], WHITE, 1)


def h_mercy(c):
    c.poly([(16, 27), (5, 15), (5, 9), (10, 5), (16, 10), (22, 5), (27, 9), (27, 15)], fill=LIFE)
    c.line([(9, 9), (11, 7)], WHITE, 1)
    plus_mark(c, 16, 16, WHITE)


def h_aftercare(c):
    cross(c, 13, 14, 6)
    for (x, y) in ((24, 22), (21, 26), (26, 27)):
        c.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, fill=mix(LIFE, CLEAR, 0.3))
    waves(c, 13, 14, (10,), mix(LIFE, CLEAR, 0.3), 10, 80)


def h_bond_healing(c):
    for (x, y) in ((8, 8), (24, 8), (16, 23)):
        c.ellipse(x - 4, y - 4, x + 4, y + 4, fill=SAGE)
    thread(c, [(8, 8), (24, 8), (16, 23), (8, 8)], GOLD, 1)
    plus_mark(c, 16, 13, WHITE)


def h_overflowing_heal(c):
    c.poly([(9, 10), (23, 10), (21, 26), (11, 26)], fill=hexc("D8F0FF"))
    c.rect(10, 14, 22, 25, LIFE)
    for (x, y) in ((8, 7), (13, 4), (20, 5), (25, 8)):
        c.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, fill=LIFE)
    c.line([(12, 15), (12, 23)], WHITE, 1)


def h_full_heart(c):
    c.poly([(16, 26), (6, 15), (6, 9), (11, 6), (16, 10), (21, 6), (26, 9), (26, 15)], fill=hexc("6FA8FF"))
    c.rect(9, 15, 23, 18, hexc("BFE0FF"))
    sparkle(c, 25, 5, WHITE, 2)


def h_admonishing_bell(c):
    bell(c, 12, 4, 13, BELL)
    c.line([(20, 18), (28, 26)], MARK_RED, 2)
    c.line([(28, 18), (20, 26)], MARK_RED, 2)


def h_blessed_shield(c):
    shield_shape(c, 16, 6, 18, 22, GOLD, WHITE)
    cross(c, 16, 15, 4, WHITE, GOLD_LIGHT)
    sparkle(c, 26, 6, WHITE, 2)


def h_blessing_light(c):
    c.line([(16, 3), (16, 8)], BELL_DARK, 1)
    c.poly([(11, 8), (21, 8), (23, 22), (9, 22)], fill=BELL_DARK)
    c.poly([(12, 10), (20, 10), (21, 20), (11, 20)], fill=GOLD_LIGHT)
    c.ellipse(13, 12, 19, 18, fill=WHITE)
    for a in (-30, 0, 30, 150, 180, 210):
        r = math.radians(a)
        c.line([(16 + math.cos(r) * 10, 15 + math.sin(r) * 10), (16 + math.cos(r) * 13, 15 + math.sin(r) * 13)], GOLD, 1)


def h_purifying_chime(c):
    bell(c, 8, 9, 10)
    waves(c, 10, 17, (8, 12, 16), WHITE, -45, 45, 2)


def h_guardian_bell(c):
    shield_shape(c, 16, 9, 20, 20, hexc("3A5A8A"), WHITE)
    bell(c, 16, 6, 10)


def h_calming_circle(c):
    ring(c, 16, 18, 12, hexc("A8D8FF"), 2)
    ring(c, 16, 18, 7, hexc("A8D8FF"), 1)
    bell(c, 16, 6, 9, hexc("D8F0FF"), hexc("6A8AB8"))


def h_heavens_bell(c):
    for x in (6, 26):
        c.line([(x, 2), (x, 10)], mix(GOLD_LIGHT, CLEAR, 0.4), 1)
    bell(c, 16, 1, 18)
    c.line([(4, 29), (28, 29)], BELL_DARK, 2)
    waves(c, 16, 29, (6, 10), BELL, 200, 340)


def h_silence_sweep(c):
    c.arc((3, 6, 29, 32), 200, 340, WHITE, 3)
    c.arc((7, 10, 25, 28), 210, 330, SAGE, 2)
    for (x, y) in ((9, 22), (16, 20), (23, 22)):
        sparkle(c, x, y, WHITE, 1)


def h_bell_ward(c):
    for r in (13, 10):
        c.arc((16 - r, 18 - r, 16 + r, 18 + r), 180, 360, mix(GOLD_LIGHT, CLEAR, 0.2 if r == 13 else 0.0), 2)
    bell(c, 16, 10, 10)


def h_purity(c):
    droplet(c, 16, 18, 6, hexc("D8F0FF"))
    for (x, y) in ((7, 8), (25, 10), (24, 24)):
        sparkle(c, x, y, WHITE, 2)


def h_admonishing_echo(c):
    bell(c, 9, 6, 10)
    waves(c, 12, 15, (8, 12), MARK_RED, -40, 40)
    c.poly([(24, 20), (28, 20), (26, 26)], fill=MARK_RED)


def h_steady_prayer(c):
    c.rect(13, 6, 19, 26, hexc("D8F0FF"))
    c.poly([(10, 12), (16, 4), (22, 12)], fill=GOLD_LIGHT)
    shield_shape(c, 16, 14, 14, 14, mix(KNIGHT, CLEAR, 0.2), WHITE)


def h_bell_echo(c):
    bell(c, 16, 4, 12)
    for i, r in enumerate((10, 13)):
        c.arc((16 - r, 20 - r, 16 + r, 20 + r), 20, 160, mix(LIFE, CLEAR, i * 0.3), 1)
    plus_mark(c, 6, 24, LIFE)
    plus_mark(c, 26, 24, LIFE)


def h_bond_sanctuary(c):
    ring(c, 16, 17, 13, GOLD, 2)
    for a in range(-90, 270, 72):
        r = math.radians(a)
        person(c, 16 + math.cos(r) * 8, 13 + math.sin(r) * 8, SAGE, 0.45)
    star(c, 16, 17, 4, 1.8, GOLD_LIGHT)


# ================================================================ アーチャー
def a_piercing_shot(c):
    arrow(c, 4, 27, 26, 5, width=2)
    for i in range(3):
        c.line([(6 + i * 3, 20 - i * 3), (3 + i * 3, 17 - i * 3)], mix(ARCHER, CLEAR, 0.3), 1)


def a_marking_arrow(c):
    ring(c, 21, 11, 7, MARK_RED, 2)
    c.px(21, 11, MARK_RED)
    arrow(c, 4, 28, 18, 14)


def a_pinning_arrow(c):
    c.line([(3, 27), (29, 27)], EARTH_DARK, 2)
    arrow(c, 9, 8, 15, 25)
    arrow(c, 22, 6, 19, 25)
    chain_links(c, 6, 26, 26, 26, 4, STEEL)


def a_drill_arrow(c):
    arrow(c, 3, 16, 27, 16, width=2)
    for x in range(8, 24, 4):
        c.arc((x - 2, 12, x + 2, 20), 0, 180, ARCHER, 1)


def a_full_draw(c):
    bow(c, 10, 16, 12, 270 + 20, 90 - 20 + 360)
    c.line([(12, 4), (2, 16), (12, 28)], WHITE, 1)
    arrow(c, 3, 16, 26, 16)


def a_hawks_gaze(c):
    eye(c, 16, 16, 11, 5, WHITE, GOLD)
    c.poly([(5, 10), (16, 7), (27, 10), (16, 9)], fill=EARTH)
    sparkle(c, 26, 6, GOLD_LIGHT, 2)


def a_sniper_stance(c):
    ring(c, 16, 16, 11, ARCHER, 1)
    ring(c, 16, 16, 5, ARCHER, 1)
    for (x0, y0, x1, y1) in ((16, 2, 16, 9), (16, 23, 16, 30), (2, 16, 9, 16), (23, 16, 30, 16)):
        c.line([(x0, y0), (x1, y1)], WHITE, 1)
    c.px(16, 16, MARK_RED)


def a_sky_piercer(c):
    arrow(c, 16, 29, 16, 5, shaft=GOLD, head=WHITE, width=2)
    c.ellipse(9, 0, 23, 8, fill=mix(GOLD_LIGHT, CLEAR, 0.4))
    for x in (8, 24):
        c.line([(x, 24), (x, 14)], mix(GOLD_LIGHT, CLEAR, 0.5), 1)


def a_shooting_star(c):
    for i in range(4):
        c.line([(2 + i, 28 - i * 2), (22, 8)], mix(GOLD_LIGHT, CLEAR, 0.2 + i * 0.2), 1)
    star(c, 23, 8, 7, 3, GOLD_LIGHT)
    arrow(c, 6, 25, 20, 11, shaft=GOLD)


def a_hawk_eye(c):
    eye(c, 16, 16, 12, 6, WHITE, ARCHER)
    c.arc((3, 6, 29, 26), 200, 340, EARTH, 2)


def a_still_focus(c):
    c.line([(16, 4), (16, 28)], WHITE, 1)
    ring(c, 16, 16, 7, ARCHER, 2)
    c.rect(6, 26, 26, 28, EARTH_DARK)
    for x in (6, 26):
        c.line([(x, 20), (x, 25)], ARCHER, 1)


def a_long_shot(c):
    arrow(c, 3, 22, 25, 12)
    for (x, s) in ((6, 1), (14, 2), (24, 3)):
        c.line([(x, 28), (x, 28 - s * 2)], WHITE, 1)
    c.line([(3, 28), (29, 28)], ARCHER, 1)


def a_weak_point(c):
    c.poly([(16, 4), (28, 16), (16, 28), (4, 16)], None, MARK_RED)
    c.poly([(16, 10), (22, 16), (16, 22), (10, 16)], fill=MARK_RED)
    sparkle(c, 16, 16, WHITE, 1)


def a_focus_mastery(c):
    star(c, 16, 16, 12, 4, ARCHER, points=4, rot=-90)
    star(c, 16, 16, 6, 2, WHITE, points=4, rot=-45)


def a_spread_arrows(c):
    for (x1, y1) in ((26, 6), (28, 16), (26, 26)):
        arrow(c, 5, 16, x1, y1)


def a_triple_shot(c):
    for y in (8, 16, 24):
        arrow(c, 4, y, 26, y)


def a_burst_arrow(c):
    arrow(c, 3, 27, 16, 14)
    star(c, 21, 10, 8, 3.5, FIRE, points=8)
    c.ellipse(18, 7, 24, 13, fill=WHITE)


def a_back_leap(c):
    c.arc((4, 6, 26, 28), 200, 340, WIND, 2)
    c.poly([(5, 17), (2, 12), (9, 13)], fill=WIND)
    arrow(c, 12, 22, 29, 22)


def a_ricochet(c):
    pts = [(3, 26), (12, 8), (21, 24), (29, 6)]
    c.line(pts, ARCHER, 1)
    for (x, y) in pts[1:3]:
        sparkle(c, x, y, WHITE, 1)
    arrow(c, 24, 15, 29, 5)


def a_whirl_arrows(c):
    swirl(c, 16, 16, 12, WIND, 1.3, 2)
    arrow(c, 18, 18, 28, 6)
    arrow(c, 14, 14, 4, 26)


def a_arrow_rain(c):
    for (x, y) in ((6, 4), (13, 8), (20, 3), (26, 9), (9, 15), (23, 16)):
        arrow(c, x, y, x, y + 9)
    c.line([(3, 28), (29, 28)], EARTH_DARK, 2)


def a_arrow_storm(c):
    swirl(c, 16, 16, 13, mix(WIND, CLEAR, 0.2), 2.0, 1)
    for a in range(0, 360, 90):
        r = math.radians(a + 20)
        arrow(c, 16 + math.cos(r) * 4, 16 + math.sin(r) * 4, 16 + math.cos(r) * 12, 16 + math.sin(r) * 12)


def a_nimble_archer(c):
    c.poly([(4, 24), (12, 8), (16, 10), (10, 24)], fill=FEATHER)
    c.line([(12, 8), (7, 23)], WIND, 1)
    for i in range(3):
        c.line([(16 + i * 4, 14 + i * 3), (28, 14 + i * 3)], mix(WIND, CLEAR, i * 0.25), 1)


def a_rapid_mastery(c):
    for i, x in enumerate((6, 13, 20)):
        arrow(c, x, 26, x + 6, 6, shaft=mix(WOOD_LIGHT, GOLD, i / 2))
    sparkle(c, 27, 24, GOLD_LIGHT, 2)


def a_chained_arrows(c):
    for i in range(3):
        arrow(c, 4 + i * 7, 26 - i * 7, 10 + i * 7, 20 - i * 7)
    chain_links(c, 8, 26, 26, 8, 3, GOLD)


def a_wind_reading(c):
    for i, y in enumerate((9, 16, 23)):
        c.arc((4, y - 4, 22, y + 4), 180, 360, mix(WIND, CLEAR, i * 0.2), 2)
    arrow(c, 14, 24, 29, 9)


def a_quiver_master(c):
    c.poly([(8, 10), (20, 6), (24, 26), (12, 29)], fill=EARTH)
    c.line([(9, 13), (21, 9)], EARTH_DARK, 1)
    for x in (11, 15, 19):
        c.line([(x, 8), (x + 2, 1)], WOOD_LIGHT, 1)
        c.px(x + 2, 1, FEATHER)


def a_gale_dance(c):
    swirl(c, 16, 16, 13, WIND, 1.5, 2)
    bow(c, 16, 16, 8, 120, 240)
    for (x, y) in ((25, 6), (6, 26)):
        c.poly([(x, y), (x + 3, y - 3), (x + 1, y + 1)], fill=FEATHER)


# ================================================================ ローグ
def r_flurry(c):
    dagger(c, 6, 24, 18, 6)
    dagger(c, 26, 24, 14, 6)
    for (x, y) in ((8, 8), (24, 8), (16, 26)):
        sparkle(c, x, y, WHITE, 1)


def r_vital_strike(c):
    dagger(c, 5, 27, 22, 10)
    c.poly([(22, 6), (28, 9), (25, 14), (19, 11)], fill=MARK_RED)
    sparkle(c, 25, 9, WHITE, 1)


def r_shadow_run(c):
    person(c, 20, 6, SHADOW, 0.9)
    for i in range(3):
        c.line([(3 + i * 2, 12 + i * 5), (12 + i * 2, 12 + i * 5)], mix(SHADOW, CLEAR, 0.2 + i * 0.2), 2)


def r_shadow_strike(c):
    person(c, 12, 6, SHADOW_DARK, 1.0)
    person(c, 22, 8, SHADOW, 0.8)
    dagger(c, 18, 26, 26, 14)


def r_phantom_blades(c):
    for i, a in enumerate(range(0, 360, 72)):
        r = math.radians(a)
        dagger(c, 16 + math.cos(r) * 5, 16 + math.sin(r) * 5, 16 + math.cos(r) * 13, 16 + math.sin(r) * 13,
               blade=mix(BLADE, SHADOW, i / 5))


def r_shadow_stitch(c):
    person(c, 9, 10, SHADOW_DARK, 0.8)
    person(c, 23, 6, SAGE, 0.8)
    thread(c, [(9, 18), (14, 14), (18, 18), (23, 14)], BLADE, 1)


def r_shadow_double(c):
    person(c, 20, 5, SHADOW_DARK, 1.1)
    person(c, 12, 7, SHADOW, 1.1)


def r_shadow_burial(c):
    dagger(c, 6, 6, 26, 26)
    dagger(c, 26, 6, 6, 26)
    c.ellipse(9, 24, 23, 30, fill=SHADOW_DARK)


def r_backstab_mastery(c):
    person(c, 14, 6, SHADOW_DARK, 1.0)
    dagger(c, 27, 24, 20, 13)
    c.poly([(3, 14), (7, 11), (7, 17)], fill=MARK_RED)


def r_nimble(c):
    c.poly([(8, 26), (14, 20), (22, 20), (26, 26)], fill=SHADOW_DARK)
    for i in range(3):
        c.arc((6 + i * 3, 4 + i * 3, 26 - i * 3, 24 - i * 3), 200, 330, mix(SHADOW, CLEAR, i * 0.25), 1)


def r_fade(c):
    person(c, 16, 5, mix(SHADOW, CLEAR, 0.5), 1.1)
    cloud(c, 16, 24, 4, SMOKE_DARK, SHADOW_DARK)


def r_shadow_footwork(c):
    for (x, y) in ((8, 24), (16, 16), (24, 8)):
        c.ellipse(x - 3, y - 2, x + 3, y + 2, fill=SHADOW)
    c.line([(5, 28), (27, 6)], mix(SHADOW, CLEAR, 0.6), 1)


def r_dark_edge(c):
    dagger(c, 6, 26, 24, 8, blade=SHADOW, edge=BLADE)
    star(c, 25, 7, 4, 1.5, WHITE)


def r_thousand_blades(c):
    for a in range(0, 360, 45):
        r = math.radians(a)
        c.line([(16 + math.cos(r) * 4, 16 + math.sin(r) * 4), (16 + math.cos(r) * 14, 16 + math.sin(r) * 14)],
               mix(BLADE, SHADOW, (a % 90) / 90), 2)
    c.ellipse(12, 12, 20, 20, fill=SHADOW_DARK)
    sparkle(c, 16, 16, WHITE, 2)


def r_venom_blade(c):
    dagger(c, 6, 26, 24, 8, blade=VENOM, edge=WHITE)
    droplet(c, 24, 24, 3)


def r_corroding_edge(c):
    dagger(c, 6, 6, 26, 26, blade=VENOM)
    dagger(c, 26, 6, 6, 26, blade=VENOM_DARK)
    for (x, y) in ((16, 26), (10, 29)):
        c.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, fill=VENOM)


def r_toxic_burst(c):
    star(c, 16, 16, 13, 5, VENOM, points=8)
    c.ellipse(11, 11, 21, 21, fill=VENOM_DARK)
    for (x, y) in ((16, 14), (13, 18), (19, 18)):
        c.px(x, y, WHITE)


def r_poison_mist(c):
    cloud(c, 16, 15, 7, VENOM, VENOM_DARK)
    for (x, y) in ((8, 26), (16, 28), (24, 26)):
        c.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, fill=VENOM)


def r_binding_threads(c):
    for (x1, y1) in ((28, 6), (28, 16), (28, 26)):
        thread(c, [(4, 16), ((4 + x1) / 2, (16 + y1) / 2 + 2), (x1, y1)], BLADE, 1)
    c.ellipse(1, 13, 7, 19, fill=SHADOW)


def r_smoke_bomb(c):
    cloud(c, 16, 18, 8)
    c.ellipse(5, 4, 13, 12, fill=SHADOW_DARK)
    c.line([(12, 5), (15, 2)], FIRE, 1)


def r_numbing_needle(c):
    c.line([(4, 28), (26, 6)], VENOM, 1)
    c.line([(20, 12), (26, 6)], WHITE, 1)
    bolt(c, [(8, 6), (12, 11), (9, 12), (14, 18)], THUNDER, WHITE)


def r_envenom(c):
    dagger(c, 8, 27, 24, 9, blade=BLADE)
    droplet(c, 24, 20, 3)
    droplet(c, 13, 9, 2.5)


def r_toxin_mastery(c):
    c.poly([(11, 4), (21, 4), (20, 9), (25, 26), (7, 26), (12, 9)], fill=hexc("D8F0FF"))
    c.poly([(9, 18), (23, 18), (25, 26), (7, 26)], fill=VENOM)
    c.rect(11, 2, 21, 4, WOOD)


def r_corrosion(c):
    shield_shape(c, 16, 5, 18, 22, STEEL_DARK, STEEL)
    for (x, y) in ((12, 11), (19, 15), (14, 20)):
        c.ellipse(x - 2, y - 2, x + 2, y + 2, fill=VENOM)


def r_deep_toxin(c):
    droplet(c, 16, 20, 7, VENOM_DARK)
    droplet(c, 16, 20, 4, VENOM)


def r_exploit(c):
    droplet(c, 10, 20, 5)
    star(c, 23, 10, 6, 2.5, GOLD_LIGHT)


def r_contagion(c):
    for (x, y) in ((8, 10), (24, 10), (16, 24)):
        droplet(c, x, y, 3.5)
    c.line([(10, 12), (14, 21)], VENOM_DARK, 1)
    c.line([(22, 12), (18, 21)], VENOM_DARK, 1)


def r_poison_bloom(c):
    flower(c, 16, 16, 7, VENOM, VENOM_DARK, 6)
    c.ellipse(12, 12, 20, 20, fill=SHADOW)
    for (x, y) in ((5, 5), (27, 6), (26, 27)):
        c.ellipse(x - 1.5, y - 1.5, x + 1.5, y + 1.5, fill=VENOM)


# ================================================================ モンク
def m_jab_combo(c):
    fist(c, 12, 12, 5)
    fist(c, 21, 20, 5)
    for y in (10, 14):
        c.line([(2, y), (6, y)], WHITE, 1)


def m_palm_strike(c):
    palm(c, 14, 16, 6)
    waves(c, 14, 16, (10, 13), KI, -40, 40)


def m_spin_kick(c):
    c.arc((3, 3, 29, 29), 160, 380, FIST, 2)
    c.poly([(18, 14), (28, 10), (29, 14), (20, 18)], fill=SKIN)
    c.rect(26, 8, 30, 13, FIST_DARK)


def m_collapsing_fist(c):
    for i in range(3):
        c.line([(2, 12 + i * 4), (10, 12 + i * 4)], mix(FIST, CLEAR, 0.3 + i * 0.2), 2)
    fist(c, 18, 16, 7)
    star(c, 27, 16, 4, 1.6, WHITE)


def m_hakkei(c):
    palm(c, 13, 17, 6)
    for r in (8, 12):
        ring(c, 24, 15, r // 2, KI, 1)
    star(c, 24, 15, 3, 1.2, WHITE)


def m_rising_dragon(c):
    swirl(c, 16, 22, 9, FIST, 1.2, 2)
    fist(c, 16, 7, 5)
    c.line([(16, 12), (16, 26)], FIST_DARK, 2)


def m_hundred_fists(c):
    for (x, y, s) in ((9, 9, 4), (21, 8, 4), (14, 17, 5), (24, 20, 4), (8, 24, 3)):
        fist(c, x, y, s)


def m_mountain_breaker(c):
    c.poly([(2, 28), (10, 14), (16, 22), (22, 12), (30, 28)], fill=EARTH)
    c.line([(16, 22), (14, 28)], EARTH_DARK, 1)
    c.line([(22, 12), (25, 20)], EARTH_DARK, 1)
    fist(c, 16, 6, 5)


def m_thunder_fist(c):
    fist(c, 13, 18, 7)
    bolt(c, [(24, 2), (19, 11), (25, 11), (20, 20)])


def m_flowing_stance(c):
    swirl(c, 16, 16, 12, KI, 1.3, 2)
    palm(c, 16, 18, 4, SKIN)


def m_ki_share(c):
    palm(c, 9, 18, 5)
    person(c, 23, 8, SAGE, 0.8)
    thread(c, [(14, 15), (18, 13), (21, 15)], KI, 2)


def m_shrink_step(c):
    for i in range(3):
        c.ellipse(4 + i * 7, 22, 9 + i * 7, 27, fill=mix(FIST, CLEAR, 0.6 - i * 0.25))
    person(c, 25, 5, FIST, 0.8)


def m_ki_bullet(c):
    palm(c, 8, 18, 5)
    c.ellipse(17, 9, 29, 21, fill=KI)
    c.ellipse(20, 12, 26, 18, fill=WHITE)


def m_turning_throw(c):
    c.arc((4, 4, 28, 28), 180, 360, FIST, 2)
    c.poly([(26, 16), (30, 12), (22, 12)], fill=FIST)
    person(c, 9, 14, SHADOW_DARK, 0.7)


def m_ki_vortex(c):
    swirl(c, 16, 16, 13, KI, 2.0, 2)
    c.ellipse(13, 13, 19, 19, fill=WHITE)


def m_inch_punch(c):
    fist(c, 14, 16, 6)
    c.line([(21, 16), (24, 16)], WHITE, 1)
    star(c, 27, 16, 4, 1.6, GOLD_LIGHT)


def m_combo_mastery(c):
    for i, x in enumerate((7, 16, 25)):
        c.ellipse(x - 4, 12 - i * 2, x + 4, 20 - i * 2, fill=mix(FIST, GOLD_LIGHT, i / 2))
    c.line([(7, 16), (16, 14), (25, 12)], WHITE, 1)


def m_iron_body(c):
    person(c, 16, 3, STEEL, 1.4)
    c.line([(9, 18), (23, 18)], STEEL_DARK, 1)


def m_battle_aura(c):
    for r in (13, 10):
        ring(c, 16, 16, r, mix(FIST, CLEAR, 0.2 if r == 13 else 0.0), 2)
    fist(c, 16, 16, 5)


def m_flowing_ki(c):
    swirl(c, 16, 16, 12, KI, 1.5, 2)
    plus_mark(c, 16, 16, LIFE)


def m_finishing_blow(c):
    fist(c, 14, 16, 7)
    star(c, 25, 9, 6, 2.5, GOLD_LIGHT)


def m_allied_fists(c):
    fist(c, 10, 16, 5)
    fist(c, 22, 16, 5, hexc("FFD8A0"))
    sparkle(c, 16, 9, WHITE, 2)


def m_receiving_art(c):
    palm(c, 16, 17, 6)
    c.arc((4, 4, 28, 28), 200, 340, KI, 2)


def m_soft_way(c):
    for i in range(3):
        c.arc((4, 6 + i * 6, 28, 18 + i * 6), 0, 180, mix(KI, CLEAR, i * 0.25), 2)


def m_calm_breath(c):
    for i, r in enumerate((4, 8, 12)):
        ring(c, 16, 16, r, mix(KI, CLEAR, i * 0.3), 1)
    plus_mark(c, 16, 16, LIFE)


def m_reading_flow(c):
    eye(c, 16, 14, 10, 4, WHITE, KI)
    swirl(c, 16, 24, 5, KI, 1.0, 1)


def m_hundred_bond(c):
    for (x, y, s) in ((8, 8, 3), (24, 8, 3), (6, 22, 3), (26, 22, 3)):
        fist(c, x, y, s)
    fist(c, 16, 16, 6, GOLD_LIGHT, BELL_DARK)


def m_heaven_earth(c):
    c.ellipse(4, 4, 28, 28, fill=FIST)
    c.d.pieslice([4, 4, 28, 28], 90, 270, fill=KI)
    c.ellipse(13, 4, 19, 16, fill=FIST)
    c.ellipse(13, 16, 19, 28, fill=KI)
    c.ellipse(15, 8, 17, 10, fill=KI)
    c.ellipse(15, 21, 17, 23, fill=FIST)


# ================================================================ ナイト・ウィザード（足した段）
def k_iron_pledge(c):
    shield_shape(c, 11, 6, 12, 16, KNIGHT, WHITE)
    shield_shape(c, 21, 10, 12, 16, shade(KNIGHT, 0.8), WHITE)
    thread(c, [(6, 26), (16, 24), (26, 26)], GOLD, 2)


def k_steadfast_shield(c):
    shield_shape(c, 16, 4, 20, 24, STEEL, WHITE)
    c.rect(13, 10, 19, 22, KNIGHT)
    c.line([(4, 29), (28, 29)], EARTH_DARK, 2)


def k_shield_bash(c):
    shield_shape(c, 13, 6, 16, 20, KNIGHT, WHITE)
    star(c, 25, 15, 6, 2.5, GOLD_LIGHT, points=6)


def k_guardians_heart(c):
    c.poly([(16, 27), (6, 16), (6, 10), (11, 6), (16, 10), (21, 6), (26, 10), (26, 16)], fill=KNIGHT)
    shield_shape(c, 16, 10, 8, 10, WHITE, GOLD)


def k_great_wheel(c):
    c.arc((3, 3, 29, 29), 0, 300, STEEL, 2)
    greatsword(c, 7, 25, 25, 7)
    c.line([(8, 20), (12, 24)], GOLD, 2)


def k_greatsword_mastery(c):
    greatsword(c, 16, 4, 16, 26)
    c.line([(10, 21), (22, 21)], GOLD, 2)
    star(c, 25, 8, 4, 1.6, GOLD_LIGHT)


def k_earth_splitter(c):
    c.poly([(2, 28), (12, 18), (16, 24), (20, 16), (30, 28)], fill=EARTH)
    c.line([(16, 24), (16, 29)], EARTH_DARK, 2)
    greatsword(c, 16, 2, 16, 18)


def k_blade_chain(c):
    for i in range(3):
        greatsword(c, 5 + i * 8, 26, 11 + i * 8, 8)
    chain_links(c, 6, 18, 26, 18, 3, GOLD)


def w_star_ring(c):
    ring(c, 16, 16, 11, GOLD, 1)
    for a in range(-90, 270, 72):
        r = math.radians(a)
        star(c, 16 + math.cos(r) * 11, 16 + math.sin(r) * 11, 3.5, 1.4, GOLD_LIGHT)
    c.ellipse(13, 13, 19, 19, fill=WIZARD)


def w_star_guide(c):
    star(c, 12, 18, 8, 3.2, GOLD)
    for (x, y) in ((24, 6), (26, 14), (21, 25)):
        star(c, x, y, 3, 1.2, GOLD_LIGHT)


def w_comet(c):
    comet(c, 21, 11, 5, (14, 12))


def w_starry_sky(c):
    for (x, y, r) in ((7, 8, 3), (16, 5, 4), (25, 9, 3), (10, 19, 2.5), (22, 20, 4), (16, 27, 2)):
        star(c, x, y, r, r * 0.4, GOLD_LIGHT)


def w_blizzard(c):
    for (x, y, r) in ((8, 8, 4), (22, 6, 3), (15, 17, 5), (25, 22, 3), (7, 25, 3)):
        snowflake(c, x, y, r)
    for i in range(3):
        c.line([(3 + i * 9, 4), (9 + i * 9, 28)], mix(ICE, CLEAR, 0.6), 1)


def w_thunder_mastery(c):
    bolt(c, [(18, 2), (10, 15), (17, 15), (11, 29)])
    ring(c, 22, 22, 6, THUNDER, 1)


def w_absolute_zero(c):
    for a in range(0, 360, 45):
        r = math.radians(a)
        c.poly([(16 + math.cos(r) * 14, 16 + math.sin(r) * 14),
                (16 + math.cos(r + 0.25) * 6, 16 + math.sin(r + 0.25) * 6),
                (16 + math.cos(r - 0.25) * 6, 16 + math.sin(r - 0.25) * 6)], fill=ICE)
    c.ellipse(11, 11, 21, 21, fill=WHITE)
    snowflake(c, 16, 16, 4, FROST)


def w_permafrost(c):
    c.rect(3, 20, 29, 28, hexc("6A8AB8"))
    for x in (6, 12, 18, 24):
        c.poly([(x, 20), (x + 3, 10), (x + 5, 20)], fill=ICE)
    c.line([(3, 20), (29, 20)], WHITE, 1)


# ================================================================ 11月24日に足したスキル（スキルツリーの作り直し）
def flame(c, cx, cy, s, col=FIRE, core=GOLD_LIGHT):
    """上へ立つ炎（cx, cy は根もと、s は高さ）。"""
    c.poly([(cx, cy - s), (cx + s * 0.45, cy - s * 0.35), (cx + s * 0.35, cy), (cx - s * 0.35, cy), (cx - s * 0.45, cy - s * 0.35)],
           fill=col)
    c.poly([(cx, cy - s * 0.6), (cx + s * 0.22, cy - s * 0.2), (cx, cy), (cx - s * 0.22, cy - s * 0.2)], fill=core)


def a_quick_draw(c):
    bow(c, 10, 16, 11, 110, 250)
    for y in (9, 16, 23):
        arrow(c, 12, y, 29, y)


def a_flash_arrow(c):
    arrow(c, 3, 27, 22, 8)
    for a in range(0, 360, 45):
        r = math.radians(a)
        c.line([(24, 6), (24 + int(6 * math.cos(r)), 6 + int(6 * math.sin(r)))], THUNDER, 1)
    c.ellipse(21, 3, 27, 9, fill=WHITE)


def a_wind_cloak(c):
    person(c, 16, 6, ARCHER)
    swirl(c, 16, 17, 12, WIND, 1.2, 1)
    up_arrow(c, 26, 6, GOLD_LIGHT)


def a_hunters_focus(c):
    eye(c, 15, 11, 14, 8, WHITE, ARCHER)
    flame(c, 15, 30, 10)
    up_arrow(c, 27, 4, GOLD_LIGHT)


def a_arrow_fan(c):
    for deg in (-50, -25, 0, 25, 50):
        r = math.radians(deg - 90)
        arrow(c, 16, 28, 16 + int(14 * math.cos(r)), 28 + int(14 * math.sin(r)))


def h_bell_strike(c):
    c.line([(5, 28), (19, 12)], WOOD_LIGHT, 2)
    bell(c, 21, 4, 11)
    plus_mark(c, 7, 7, LIFE)
    sparkle(c, 27, 21, WHITE, 2)


def h_mending_light(c):
    c.ellipse(9, 9, 23, 23, fill=BELL)
    cross(c, 16, 16, 4, LIFE, LIFE_DARK)
    waves(c, 16, 16, (10, 13), LIFE, -60, 60)


def h_chorus_bell(c):
    bell(c, 9, 6, 10)
    bell(c, 22, 9, 10)
    waves(c, 16, 22, (9, 12), BELL, 200, 340)
    up_arrow(c, 27, 4, FIRE)


def h_swift_prayer(c):
    c.ellipse(10, 3, 22, 15, fill=BELL)
    for i, y in enumerate((18, 22, 26)):
        c.line([(4 + i * 3, y), (28, y)], mix(WIND, CLEAR, i * 0.2), 2)


def h_judgment_bell(c):
    bell(c, 16, 3, 16)
    bolt(c, [(7, 21), (11, 25), (8, 25), (12, 30)], THUNDER)
    bolt(c, [(25, 21), (21, 25), (24, 25), (20, 30)], THUNDER)


def r_throwing_knife(c):
    dagger(c, 6, 26, 24, 8)
    for i in range(3):
        c.line([(3 + i * 3, 29 - i * 3), (6 + i * 3, 29 - i * 3)], SMOKE, 1)


def r_twin_fangs(c):
    dagger(c, 6, 27, 22, 6)
    dagger(c, 26, 27, 10, 6, blade=SHADOW)


def r_venom_veil(c):
    cloud(c, 16, 13, 5, VENOM, VENOM_DARK)
    for x in (9, 16, 23):
        droplet(c, x, 25, 2)


def r_shadow_dance(c):
    person(c, 12, 5, SHADOW_DARK)
    person(c, 20, 7, SHADOW)
    flame(c, 26, 14, 8)


def r_hamstring(c):
    dagger(c, 4, 22, 26, 22)
    chain_links(c, 6, 28, 26, 28, 4, SMOKE)
    c.line([(14, 6), (18, 14)], MARK_RED, 2)


def m_kaisei_fist(c):
    fist(c, 14, 16, 9)
    cross(c, 25, 8, 3, LIFE, LIFE_DARK)
    swirl(c, 14, 16, 13, LIFE, 0.8, 1)


def m_kien(c):
    flame(c, 16, 29, 22, FIRE, GOLD_LIGHT)
    fist(c, 16, 20, 5)


def m_air_palm(c):
    palm(c, 9, 16, 8)
    for r in (6, 9, 12):
        c.arc((16 - r, 16 - r, 16 + r, 16 + r), -50, 50, KI, 1)


def m_chain_kicks(c):
    swirl(c, 16, 16, 13, MONK, 1.6, 2)
    for a in (0, 120, 240):
        r = math.radians(a)
        c.ellipse(16 + int(11 * math.cos(r)) - 2, 16 + int(11 * math.sin(r)) - 2, 16 + int(11 * math.cos(r)) + 2,
                  16 + int(11 * math.sin(r)) + 2, fill=FIST)


def m_back_strike(c):
    person(c, 11, 6, FIST_DARK)
    for y in (12, 17, 22):
        c.line([(18, y), (29, y)], KI, 1)
    c.poly([(24, 9), (30, 16), (24, 23)], fill=FIST)


def k_sword_pressure(c):
    sword(c, 6, 26, 18, 6)
    for r in (8, 11, 14):
        c.arc((14 - r, 16 - r, 14 + r, 16 + r), -60, 20, WHITE, 1)


def k_war_cry(c):
    person(c, 16, 7, KNIGHT)
    waves(c, 16, 12, (10, 13), GOLD_LIGHT, -70, 70)
    waves(c, 16, 12, (10, 13), GOLD_LIGHT, 110, 250)
    up_arrow(c, 27, 22, FIRE)


def k_shield_rush(c):
    shield_shape(c, 20, 6, 14, 18, STEEL, RIM)
    for y in (10, 15, 20):
        c.line([(2, y), (11, y)], WHITE, 1)


def k_crushing_blow(c):
    sword(c, 8, 4, 22, 22)
    for a in range(200, 340, 35):
        r = math.radians(a)
        c.line([(22, 24), (22 + int(7 * math.cos(r)), 24 - int(7 * math.sin(r)))], FIRE, 1)
    c.ellipse(19, 21, 25, 27, fill=GOLD_LIGHT)


def k_iron_resolve(c):
    shield_shape(c, 16, 4, 18, 22, STEEL_DARK, RIM)
    c.rect(6, 26, 26, 28, EARTH)
    up_arrow(c, 16, 10, FIRE)


def w_ice_shard(c):
    for (x, y) in ((8, 22), (16, 15), (24, 8)):
        c.poly([(x, y - 4), (x + 3, y), (x, y + 4), (x - 3, y)], fill=FROST, outline=WHITE)


def w_star_shield(c):
    c.ellipse(5, 5, 27, 27, outline=ICE, width=2)
    star(c, 16, 16, 7, 3, GOLD_LIGHT)
    for (x, y) in ((8, 9), (24, 11), (11, 24)):
        sparkle(c, x, y, WHITE, 1)


def w_mana_focus(c):
    for r in (12, 8):
        c.ellipse(16 - r, 16 - r, 16 + r, 16 + r, outline=VIOLET, width=1)
    c.ellipse(12, 12, 20, 20, fill=GOLD_LIGHT)
    up_arrow(c, 27, 4, FIRE)


def w_frost_mist(c):
    cloud(c, 16, 13, 5, ICE, FROST)
    snowflake(c, 9, 25, 3)
    snowflake(c, 23, 25, 3)


def w_light_lance(c):
    c.poly([(3, 29), (27, 5), (29, 3), (5, 27)], fill=GOLD_LIGHT)
    c.line([(3, 29), (29, 3)], WHITE, 1)
    sparkle(c, 27, 5, WHITE, 2)


def e_power_up(c):
    flame(c, 9, 17, 14, FIRE, GOLD_LIGHT)


SKILLS = {
    # ヒーラー
    "healer/bell_heal": (h_bell_heal, HEALER), "healer/prayer_circle": (h_prayer_circle, HEALER),
    "healer/bond_mend": (h_bond_mend, HEALER), "healer/call_back": (h_call_back, HEALER),
    "healer/life_thread": (h_life_thread, HEALER), "healer/swelling_bell": (h_swelling_bell, HEALER),
    "healer/resonant_healing": (h_resonant_healing, HEALER), "healer/healing_rain": (h_healing_rain, HEALER),
    "healer/great_call": (h_great_call, HEALER), "healer/mercy": (h_mercy, HEALER), "healer/aftercare": (h_aftercare, HEALER),
    "healer/bond_healing": (h_bond_healing, HEALER), "healer/overflowing_heal": (h_overflowing_heal, HEALER),
    "healer/full_heart": (h_full_heart, HEALER), "healer/admonishing_bell": (h_admonishing_bell, HEALER),
    "healer/blessed_shield": (h_blessed_shield, HEALER), "healer/blessing_light": (h_blessing_light, HEALER),
    "healer/purifying_chime": (h_purifying_chime, HEALER), "healer/guardian_bell": (h_guardian_bell, HEALER),
    "healer/calming_circle": (h_calming_circle, HEALER), "healer/heavens_bell": (h_heavens_bell, HEALER),
    "healer/silence_sweep": (h_silence_sweep, HEALER), "healer/bell_ward": (h_bell_ward, HEALER),
    "healer/purity": (h_purity, HEALER), "healer/admonishing_echo": (h_admonishing_echo, HEALER),
    "healer/steady_prayer": (h_steady_prayer, HEALER), "healer/bell_echo": (h_bell_echo, HEALER),
    "healer/bond_sanctuary": (h_bond_sanctuary, HEALER),
    # アーチャー
    "archer/piercing_shot": (a_piercing_shot, ARCHER), "archer/marking_arrow": (a_marking_arrow, ARCHER),
    "archer/pinning_arrow": (a_pinning_arrow, ARCHER), "archer/drill_arrow": (a_drill_arrow, ARCHER),
    "archer/full_draw": (a_full_draw, ARCHER), "archer/hawks_gaze": (a_hawks_gaze, ARCHER),
    "archer/sniper_stance": (a_sniper_stance, ARCHER), "archer/sky_piercer": (a_sky_piercer, ARCHER),
    "archer/shooting_star": (a_shooting_star, ARCHER), "archer/hawk_eye": (a_hawk_eye, ARCHER),
    "archer/still_focus": (a_still_focus, ARCHER), "archer/long_shot": (a_long_shot, ARCHER),
    "archer/weak_point": (a_weak_point, ARCHER), "archer/focus_mastery": (a_focus_mastery, ARCHER),
    "archer/spread_arrows": (a_spread_arrows, ARCHER), "archer/triple_shot": (a_triple_shot, ARCHER),
    "archer/burst_arrow": (a_burst_arrow, ARCHER), "archer/back_leap": (a_back_leap, ARCHER),
    "archer/ricochet": (a_ricochet, ARCHER), "archer/whirl_arrows": (a_whirl_arrows, ARCHER),
    "archer/arrow_rain": (a_arrow_rain, ARCHER), "archer/arrow_storm": (a_arrow_storm, ARCHER),
    "archer/nimble_archer": (a_nimble_archer, ARCHER), "archer/rapid_mastery": (a_rapid_mastery, ARCHER),
    "archer/chained_arrows": (a_chained_arrows, ARCHER), "archer/wind_reading": (a_wind_reading, ARCHER),
    "archer/quiver_master": (a_quiver_master, ARCHER), "archer/gale_dance": (a_gale_dance, ARCHER),
    # ローグ
    "rogue/flurry": (r_flurry, ROGUE), "rogue/vital_strike": (r_vital_strike, ROGUE), "rogue/shadow_run": (r_shadow_run, ROGUE),
    "rogue/shadow_strike": (r_shadow_strike, ROGUE), "rogue/phantom_blades": (r_phantom_blades, ROGUE),
    "rogue/shadow_stitch": (r_shadow_stitch, ROGUE), "rogue/shadow_double": (r_shadow_double, ROGUE),
    "rogue/shadow_burial": (r_shadow_burial, ROGUE), "rogue/backstab_mastery": (r_backstab_mastery, ROGUE),
    "rogue/nimble": (r_nimble, ROGUE), "rogue/fade": (r_fade, ROGUE), "rogue/shadow_footwork": (r_shadow_footwork, ROGUE),
    "rogue/dark_edge": (r_dark_edge, ROGUE), "rogue/thousand_blades": (r_thousand_blades, ROGUE),
    "rogue/venom_blade": (r_venom_blade, ROGUE), "rogue/corroding_edge": (r_corroding_edge, ROGUE),
    "rogue/toxic_burst": (r_toxic_burst, ROGUE), "rogue/poison_mist": (r_poison_mist, ROGUE),
    "rogue/binding_threads": (r_binding_threads, ROGUE), "rogue/smoke_bomb": (r_smoke_bomb, ROGUE),
    "rogue/numbing_needle": (r_numbing_needle, ROGUE), "rogue/envenom": (r_envenom, ROGUE),
    "rogue/toxin_mastery": (r_toxin_mastery, ROGUE), "rogue/corrosion": (r_corrosion, ROGUE),
    "rogue/deep_toxin": (r_deep_toxin, ROGUE), "rogue/exploit": (r_exploit, ROGUE), "rogue/contagion": (r_contagion, ROGUE),
    "rogue/poison_bloom": (r_poison_bloom, ROGUE),
    # モンク
    "monk/jab_combo": (m_jab_combo, MONK), "monk/palm_strike": (m_palm_strike, MONK), "monk/spin_kick": (m_spin_kick, MONK),
    "monk/collapsing_fist": (m_collapsing_fist, MONK), "monk/hakkei": (m_hakkei, MONK),
    "monk/rising_dragon": (m_rising_dragon, MONK), "monk/hundred_fists": (m_hundred_fists, MONK),
    "monk/mountain_breaker": (m_mountain_breaker, MONK), "monk/thunder_fist": (m_thunder_fist, MONK),
    "monk/flowing_stance": (m_flowing_stance, MONK), "monk/ki_share": (m_ki_share, MONK), "monk/shrink_step": (m_shrink_step, MONK),
    "monk/ki_bullet": (m_ki_bullet, MONK), "monk/turning_throw": (m_turning_throw, MONK), "monk/ki_vortex": (m_ki_vortex, MONK),
    "monk/inch_punch": (m_inch_punch, MONK), "monk/combo_mastery": (m_combo_mastery, MONK), "monk/iron_body": (m_iron_body, MONK),
    "monk/battle_aura": (m_battle_aura, MONK), "monk/flowing_ki": (m_flowing_ki, MONK),
    "monk/finishing_blow": (m_finishing_blow, MONK), "monk/allied_fists": (m_allied_fists, MONK),
    "monk/receiving_art": (m_receiving_art, MONK), "monk/soft_way": (m_soft_way, MONK), "monk/calm_breath": (m_calm_breath, MONK),
    "monk/reading_flow": (m_reading_flow, MONK), "monk/hundred_bond": (m_hundred_bond, MONK),
    "monk/heaven_earth": (m_heaven_earth, MONK),
    # ナイト・ウィザード（足した段）
    "knight/iron_pledge": (k_iron_pledge, KNIGHT), "knight/steadfast_shield": (k_steadfast_shield, KNIGHT),
    "knight/shield_bash": (k_shield_bash, KNIGHT), "knight/guardians_heart": (k_guardians_heart, KNIGHT),
    "knight/great_wheel": (k_great_wheel, KNIGHT), "knight/greatsword_mastery": (k_greatsword_mastery, KNIGHT),
    "knight/earth_splitter": (k_earth_splitter, KNIGHT), "knight/blade_chain": (k_blade_chain, KNIGHT),
    "wizard/star_ring": (w_star_ring, WIZARD), "wizard/star_guide": (w_star_guide, WIZARD), "wizard/comet": (w_comet, WIZARD),
    "wizard/starry_sky": (w_starry_sky, WIZARD), "wizard/blizzard": (w_blizzard, WIZARD),
    "wizard/thunder_mastery": (w_thunder_mastery, WIZARD), "wizard/absolute_zero": (w_absolute_zero, WIZARD),
    "wizard/permafrost": (w_permafrost, WIZARD),
    # 11月24日に足したスキル
    "archer/quick_draw": (a_quick_draw, ARCHER), "archer/flash_arrow": (a_flash_arrow, ARCHER),
    "archer/wind_cloak": (a_wind_cloak, ARCHER), "archer/hunters_focus": (a_hunters_focus, ARCHER),
    "archer/arrow_fan": (a_arrow_fan, ARCHER),
    "healer/bell_strike": (h_bell_strike, HEALER), "healer/mending_light": (h_mending_light, HEALER),
    "healer/chorus_bell": (h_chorus_bell, HEALER), "healer/swift_prayer": (h_swift_prayer, HEALER),
    "healer/judgment_bell": (h_judgment_bell, HEALER),
    "rogue/throwing_knife": (r_throwing_knife, ROGUE), "rogue/twin_fangs": (r_twin_fangs, ROGUE),
    "rogue/venom_veil": (r_venom_veil, ROGUE), "rogue/shadow_dance": (r_shadow_dance, ROGUE), "rogue/hamstring": (r_hamstring, ROGUE),
    "monk/kaisei_fist": (m_kaisei_fist, MONK), "monk/kien": (m_kien, MONK), "monk/air_palm": (m_air_palm, MONK),
    "monk/chain_kicks": (m_chain_kicks, MONK), "monk/back_strike": (m_back_strike, MONK),
    "knight/sword_pressure": (k_sword_pressure, KNIGHT), "knight/war_cry": (k_war_cry, KNIGHT),
    "knight/shield_rush": (k_shield_rush, KNIGHT), "knight/crushing_blow": (k_crushing_blow, KNIGHT),
    "knight/iron_resolve": (k_iron_resolve, KNIGHT),
    "wizard/ice_shard": (w_ice_shard, WIZARD), "wizard/star_shield": (w_star_shield, WIZARD),
    "wizard/mana_focus": (w_mana_focus, WIZARD), "wizard/frost_mist": (w_frost_mist, WIZARD),
    "wizard/light_lance": (w_light_lance, WIZARD),
}


# ================================================================ 職業の紋章（64x64）
def healer_emblem(c):
    c.line([(16, 54), (40, 18)], WOOD, 4)
    c.line([(16, 54), (40, 18)], WOOD_LIGHT, 1)
    bell(c, 42, 8, 22)
    for (x, y) in ((14, 18), (22, 30)):
        cross(c, x, y, 4)
    flower(c, 48, 48, 5, PETAL, GOLD_LIGHT)


def archer_emblem(c):
    bow(c, 30, 32, 22, 120, 240, WOOD_LIGHT, WHITE)
    arrow(c, 10, 32, 56, 32, width=2)
    for (x, y) in ((48, 12), (52, 50)):
        c.poly([(x, y), (x + 4, y - 4), (x + 2, y + 2)], fill=FEATHER)


def rogue_emblem(c):
    dagger(c, 12, 52, 44, 14)
    dagger(c, 52, 52, 20, 14, blade=VENOM)
    c.ellipse(26, 40, 38, 52, fill=SHADOW_DARK)
    droplet(c, 32, 48, 3)


def monk_emblem(c):
    swirl(c, 32, 32, 26, KI, 1.4, 2)
    fist(c, 32, 30, 11)
    star(c, 48, 14, 6, 2.5, GOLD_LIGHT)


# ================================================================ 状態のアイコン（18x18）
def e_poison(c):
    droplet(c, 9, 11, 5)


def e_hunter_mark(c):
    ring(c, 9, 9, 7, MARK_RED, 2)
    c.ellipse(7, 7, 11, 11, fill=MARK_RED)


def e_rooted(c):
    chain_links(c, 2, 14, 16, 14, 3, STEEL)
    c.line([(9, 2), (9, 12)], WOOD_LIGHT, 2)


def e_admonished(c):
    bell(c, 9, 1, 10)
    c.line([(12, 12), (16, 16)], MARK_RED, 2)


def e_blinded(c):
    cloud(c, 9, 9, 4)


def e_blessing(c):
    shield_shape(c, 9, 2, 13, 15, GOLD, WHITE)


def e_protected(c):
    ring(c, 9, 9, 7, hexc("E8F4FF"), 2)
    shield_shape(c, 9, 4, 8, 10, KNIGHT, WHITE)


def e_keen(c):
    star(c, 9, 9, 8, 3, GOLD_LIGHT, points=4)


def e_swift(c):
    for i, y in enumerate((5, 9, 13)):
        c.line([(2 + i * 2, y), (15, y)], mix(WIND, CLEAR, i * 0.2), 2)


def e_empowered(c):
    fist(c, 9, 9, 5)
    up_arrow(c, 15, 4, GOLD_LIGHT)


def e_aiming(c):
    ring(c, 9, 9, 6, ARCHER, 1)
    c.line([(9, 1), (9, 17)], WHITE, 1)
    c.line([(1, 9), (17, 9)], WHITE, 1)


def e_life_thread(c):
    thread(c, [(2, 15), (6, 9), (10, 12), (15, 3)], GOLD, 2)


def e_guardian_bell(c):
    bell(c, 9, 1, 12)


def e_star_ring(c):
    ring(c, 9, 9, 6, GOLD, 1)
    for a in range(-90, 270, 120):
        r = math.radians(a)
        star(c, 9 + math.cos(r) * 6, 9 + math.sin(r) * 6, 2.5, 1, GOLD_LIGHT)


def e_shadow_clone(c):
    person(c, 11, 2, SHADOW_DARK, 0.7)
    person(c, 7, 3, SHADOW, 0.7)


def e_venom_coat(c):
    dagger(c, 3, 15, 14, 4, blade=VENOM)
    droplet(c, 14, 13, 2)


def e_bow_dance(c):
    swirl(c, 9, 9, 7, WIND, 1.3, 2)


def e_evade(c):
    person(c, 11, 2, mix(SHADOW, CLEAR, 0.4), 0.7)
    c.line([(1, 8), (6, 8)], SHADOW, 1)
    c.line([(2, 12), (6, 12)], SHADOW, 1)


def e_endless_flow(c):
    c.ellipse(2, 2, 16, 16, fill=FIST)
    c.d.pieslice([2, 2, 16, 16], 90, 270, fill=KI)
    c.ellipse(7, 2, 11, 9, fill=FIST)
    c.ellipse(7, 9, 11, 16, fill=KI)


def e_renewal(c):
    cross(c, 9, 9, 6)
    leaf(c, 12, 14, LIFE_DARK)


def e_hidden(c):
    eye(c, 9, 9, 7, 3, SMOKE, SHADOW_DARK)
    c.line([(2, 15), (16, 3)], SHADOW, 2)


EFFECTS = {
    "poison": e_poison, "hunter_mark": e_hunter_mark, "rooted": e_rooted, "admonished": e_admonished, "blinded": e_blinded,
    "blessing": e_blessing, "protected": e_protected, "keen": e_keen, "swift": e_swift, "empowered": e_empowered,
    "aiming": e_aiming, "life_thread": e_life_thread, "guardian_bell": e_guardian_bell, "star_ring": e_star_ring,
    "shadow_clone": e_shadow_clone, "venom_coat": e_venom_coat, "bow_dance": e_bow_dance, "evade": e_evade,
    "endless_flow": e_endless_flow, "renewal": e_renewal, "hidden": e_hidden, "power_up": e_power_up,
}


def main():
    out = []
    for name, (fn, accent) in SKILLS.items():
        out.append(save(symbol(fn, accent), "textures/gui/skill/%s.png" % name))
    for job, accent, fn in (("healer", HEALER, healer_emblem), ("archer", ARCHER, archer_emblem), ("rogue", ROGUE, rogue_emblem),
                            ("monk", MONK, monk_emblem)):
        out.append(save(emblem(accent, fn), "textures/gui/job/%s.png" % job))
    for name, fn in EFFECTS.items():
        out.append(save(effect_icon(fn), "textures/mob_effect/%s.png" % name))
    print(len(SKILLS), "skill icons,", 4, "emblems,", len(EFFECTS), "effect icons")


if __name__ == "__main__":
    main()
