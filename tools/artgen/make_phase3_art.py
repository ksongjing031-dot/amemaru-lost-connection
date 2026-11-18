"""AMEMARU: LOST CONNECTION の Phase 3 の仮素材（アイテムの絵・手に持った武器の立体・工房の作業台・演出）を作る。

使い方: python3 tools/artgen/make_phase3_art.py <assets/amemaru のフォルダ>

作るもの（同じ名前・同じ大きさで描き直したファイルを置けば、コードを変えずに差し替えられる。docs/phase3.md の表）:
  textures/item/<アイテム>.png          持ち物のアイコン（16x16）。武器・防具・アクセサリー・素材・結晶果
  textures/item/<武器>_model.png        手に持った武器の立体の面（32x32）
  models/item/<アイテム>.json            アイテムのモデル（立体の武器は、持ち物ではアイコン・手では立体）
  textures/block/<工房>_top.png・_side.png、models/block・blockstates   工房の作業台
  textures/entity/crystal_needle.png     結晶蜂の針（16x16）
  textures/fx/telegraph.png              敵の予兆の地面の帯（32x32。白と透明度で描き、色はゲームでつける）
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/amemaru"


def rgb(hex_color):
    h = hex_color.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def save(img, rel):
    if isinstance(img, Canvas):
        img = img.img
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


class Canvas:
    def __init__(self, w=16, h=16):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.p = self.img.load()
        self.w, self.h = w, h

    def px(self, x, y, color, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            c = rgb(color) if isinstance(color, str) else color
            self.p[x, y] = (c[0], c[1], c[2], a)

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, color)

    def line(self, x0, y0, x1, y1, color):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            t = i / max(1, n)
            self.px(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color)

    def disc(self, cx, cy, r, color, edge=None):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.px(x, y, edge if edge and d > r - 1 else color)

    def outline(self, color="#1A1F2B"):
        """中身のある画素のまわりに、1画素の暗いふちを足す（アイコンを読みやすくする）。"""
        src = self.img.copy().load()
        for y in range(self.h):
            for x in range(self.w):
                if src[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and src[nx, ny][3] > 0:
                            self.px(x, y, color)
                            break
        return self


# ================================================================ アイコン（16x16）

def sword_icon(blade, guard, grip, wide=False, glow=None):
    c = Canvas()
    # 刃：左下（柄）から右上（切っ先）への斜め
    for i in range(10):
        x, y = 4 + i, 11 - i
        c.px(x, y, blade)
        c.px(x + 1, y, shade(rgb(blade), 0.8))
        if wide:
            c.px(x, y - 1, shade(rgb(blade), 1.1))
            c.px(x + 1, y + 1, shade(rgb(blade), 0.7))
    if glow:
        for i in range(2, 9, 2):
            c.px(4 + i, 11 - i, glow)
    # 鍔
    c.line(2, 10, 5, 13, guard)
    # 柄と柄頭
    c.line(1, 14, 3, 12, grip)
    c.px(0, 15, guard)
    return c.outline()


def staff_icon(shaft, head, gem):
    c = Canvas()
    c.line(2, 14, 11, 5, shaft)
    c.line(3, 14, 12, 5, shade(rgb(shaft), 0.75))
    c.disc(12.5, 3.5, 2.6, gem, edge=shade(rgb(gem), 0.7))
    c.px(12, 3, "#FFFFFF")
    c.line(10, 6, 11, 7, head)
    c.line(13, 6, 14, 5, head)
    return c.outline()


def shield_icon(face, rim, emblem):
    c = Canvas()
    for y in range(2, 15):
        half = 6 if y < 10 else 6 - (y - 9)
        c.rect(8 - half, y, 7 + half, y, face)
    for y in range(2, 15):
        half = 6 if y < 10 else 6 - (y - 9)
        c.px(8 - half, y, rim)
        c.px(7 + half, y, rim)
    c.rect(2, 2, 13, 2, rim)
    c.rect(7, 5, 8, 11, emblem)
    c.rect(5, 7, 10, 8, emblem)
    return c.outline()


def armor_icon(kind, main, accent, gem=None):
    c = Canvas()
    if kind == "head":
        c.rect(4, 4, 11, 10, main)
        c.rect(5, 3, 10, 3, main)
        c.rect(5, 8, 10, 9, shade(rgb(main), 0.6))
        c.rect(7, 2, 8, 4, accent)
        if gem:
            c.rect(7, 6, 8, 6, gem)
    elif kind == "chest":
        c.rect(3, 3, 12, 13, main)
        c.rect(1, 3, 3, 7, main)
        c.rect(12, 3, 14, 7, main)
        c.rect(6, 3, 9, 4, (0, 0, 0, 0))
        for x in range(6, 10):
            c.p[x, 3] = (0, 0, 0, 0)
        c.rect(7, 5, 8, 12, accent)
        if gem:
            c.rect(7, 7, 8, 8, gem)
    elif kind == "legs":
        c.rect(4, 3, 11, 5, accent)
        c.rect(4, 6, 7, 14, main)
        c.rect(8, 6, 11, 14, main)
        c.rect(7, 6, 8, 14, shade(rgb(main), 0.7))
    else:
        c.rect(3, 9, 7, 13, main)
        c.rect(9, 9, 13, 13, main)
        c.rect(2, 12, 7, 13, accent)
        c.rect(9, 12, 14, 13, accent)
    return c.outline()


def ring_icon(band, gem):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 9)
            if 3.0 <= d <= 5.2:
                c.px(x, y, band if d < 4.3 else shade(rgb(band), 0.7))
    c.disc(8, 4, 2.4, gem, edge=shade(rgb(gem), 0.65))
    c.px(7, 3, "#FFFFFF")
    return c.outline()


def necklace_icon(cord, gem):
    c = Canvas()
    for i in range(12):
        x = 2 + i
        y = int(3 + 6 * math.sin(math.pi * i / 11))
        c.px(x, y, cord)
    c.disc(8, 12, 2.5, gem, edge=shade(rgb(gem), 0.65))
    c.px(7, 11, "#FFFFFF")
    return c.outline()


def bracelet_icon(band, gem):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.0, (y + 0.5 - 8) / 3.6
            d = math.hypot(dx, dy)
            if 0.62 <= d <= 1.0:
                c.px(x, y, band if y < 8 else shade(rgb(band), 0.75))
    c.rect(7, 4, 8, 5, gem)
    return c.outline()


def charm_icon(stone, cord, gem):
    c = Canvas()
    c.line(8, 1, 8, 4, cord)
    c.rect(5, 5, 10, 12, stone)
    c.rect(6, 6, 9, 11, shade(rgb(stone), 1.1))
    c.rect(7, 7, 8, 9, gem)
    c.line(7, 13, 6, 15, cord)
    c.line(8, 13, 9, 15, cord)
    return c.outline()


def material_icon(kind, color, accent=None, seed=1):
    r = random.Random(seed)
    c = Canvas()
    col = rgb(color)
    if kind == "stone":
        pts = [(3, 6), (7, 3), (12, 4), (13, 9), (10, 13), (4, 12)]
        poly_fill(c, pts, col, r)
    elif kind == "ingot":
        for y in range(6, 12):
            for x in range(2 + (11 - y) // 2, 14 - (y - 6) // 2):
                c.px(x, y, shade(col, 1.15 if y < 8 else 0.9))
        if accent:
            for _ in range(7):
                c.px(r.randint(3, 12), r.randint(6, 9), accent)
    elif kind == "shard":
        pts = [(8, 1), (12, 7), (9, 15), (5, 9)]
        poly_fill(c, pts, col, r, crystal=True)
    elif kind == "fluff":
        c.disc(8, 9, 5.5, color)
        for _ in range(14):
            c.px(r.randint(3, 12), r.randint(4, 13), shade(col, r.choice([0.85, 1.1])))
    elif kind == "needle":
        c.line(2, 14, 13, 3, color)
        c.line(3, 14, 13, 4, shade(col, 0.75))
        c.px(14, 2, "#FFFFFF")
    elif kind == "fang":
        for y in range(3, 14):
            w = max(1, (14 - y) // 3)
            c.rect(7 - w, y, 7 + w, y, color)
        c.rect(5, 3, 9, 5, accent or "#F4F7FB")
    elif kind == "echo":
        pts = [(8, 2), (12, 8), (8, 14), (4, 8)]
        poly_fill(c, pts, col, r, crystal=True)
        for y in range(16):
            for x in range(16):
                if c.p[x, y][3] and (x + y) % 5 == 0:
                    c.px(x, y, "#FFFFFF")
    elif kind == "horn":
        for i in range(10):
            x, y = 4 + i, 13 - int(i * 1.1)
            c.rect(x, y, x + 1, y + max(0, 2 - i // 4), color)
        c.px(14, 2, accent or "#FFFFFF")
    elif kind == "core":
        c.disc(8, 8, 5.0, color, edge=shade(col, 0.6))
        c.disc(8, 8, 2.2, accent or "#FFFFFF")
    return c.outline()


def poly_fill(c, pts, col, r, crystal=False):
    def inside(x, y):
        n = len(pts)
        inside_flag = False
        j = n - 1
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                inside_flag = not inside_flag
            j = i
        return inside_flag
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                f = 1.0 + (0.18 if crystal and (x - y) % 4 == 0 else 0) + r.uniform(-0.08, 0.06)
                if x + y < 14:
                    f += 0.08
                c.px(x, y, shade(col, f))


def fruit_icon(color):
    c = Canvas()
    col = rgb(color)
    c.disc(8, 9.5, 5.2, shade(col, 1.0), edge=shade(col, 0.6))
    c.disc(6.5, 8, 1.6, shade(col, 1.35))
    c.px(6, 7, "#FFFFFF")
    c.line(8, 4, 9, 2, "#5E8A4A")
    c.rect(9, 2, 11, 3, "#6BE36B")
    # 結晶の筋
    for i in range(3):
        c.px(9 + i, 10 + i, shade(col, 1.3))
    return c.outline()


# ================================================================ 立体の武器（Java のブロック・アイテムのモデル）

def box(frm, to, tex_rect, name):
    """tex_rect: 32x32 の絵の上の、その箱に使う四角（u0, v0, u1, v1。0〜16の単位）。"""
    u0, v0, u1, v1 = tex_rect
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        faces[face] = {"uv": [u0, v0, u1, v1], "texture": "#body"}
    return {"name": name, "from": frm, "to": to, "faces": faces}


def weapon_model_texture(regions):
    """regions：[(名前, 色, 模様)] を 32x32 に 8x8 ずつ並べる。戻り値：名前 → 0〜16 の四角。"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    rects = {}
    r = random.Random(5)
    for i, (name, color, pattern) in enumerate(regions):
        gx, gy = (i % 4) * 8, (i // 4) * 8
        col = rgb(color)
        for y in range(8):
            for x in range(8):
                f = 1.0 + r.uniform(-0.07, 0.07)
                if pattern == "edge" and y == 0:
                    f = 1.35
                if pattern == "crystal" and (x + y) % 3 == 0:
                    f = 1.25
                if pattern == "wrap" and y % 2 == 0:
                    f = 0.75
                p[gx + x, gy + y] = shade(col, f) + (255,)
        rects[name] = [gx / 2.0 + 0.5, gy / 2.0 + 0.5, gx / 2.0 + 3.5, gy / 2.0 + 3.5]
    return img, rects


HAND_TRANSFORMS = {
    "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}


def flat_icon_model(icon):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"amemaru:item/{icon}"}}


def weapon_3d(name, elements, regions, transforms=None, emissive=()):
    img, rects = weapon_model_texture(regions)
    save(img, f"textures/item/{name}_model.png")
    els = []
    for el_name, frm, to, region in elements:
        el = box(frm, to, rects[region], el_name)
        if region in emissive:
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
        els.append(el)
    model_3d = {
        "credit": "AMEMARU Phase 3 placeholder (tools/artgen/make_phase3_art.py)",
        "textures": {"body": f"amemaru:item/{name}_model", "particle": f"amemaru:item/{name}_model"},
        "elements": els,
        "display": transforms or HAND_TRANSFORMS,
    }
    write_json(f"models/item/{name}_3d.json", model_3d)
    write_json(f"models/item/{name}.json", {
        "loader": "forge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": f"amemaru:item/{name}_3d"},
        "perspectives": {
            "gui": flat_icon_model(name),
            "fixed": flat_icon_model(name),
            "ground": flat_icon_model(name)
        }
    })


def weapons():
    stone, dark, moss, crystal, grip, trim = "#9A978A", "#6B695F", "#5E8A4A", "#8FD3FF", "#5A3E2A", "#C9B27A"
    # 古代石の剣
    weapon_3d("ancient_stone_sword", [
        ("blade", [7, 5, 7.5], [9, 16, 8.5], "blade"),
        ("tip", [7.5, 16, 7.5], [8.5, 17.5, 8.5], "blade"),
        ("guard", [5, 4, 7], [11, 5, 9], "guard"),
        ("grip", [7.5, 0, 7.5], [8.5, 4, 8.5], "grip"),
        ("pommel", [7, -1, 7], [9, 0, 9], "guard"),
        ("moss", [7, 6, 7.4], [9, 7, 8.6], "moss"),
    ], [("blade", stone, "edge"), ("guard", dark, None), ("grip", grip, "wrap"), ("moss", moss, None)])
    save(sword_icon("#A8A598", "#6B695F", "#5A3E2A"), "textures/item/ancient_stone_sword.png")
    # 古代石の大剣
    weapon_3d("ancient_stone_greatsword", [
        ("blade", [6.5, 6, 7.25], [9.5, 24, 8.75], "blade"),
        ("tip", [7, 24, 7.25], [9, 26, 8.75], "blade"),
        ("fuller", [7.6, 7, 7.0], [8.4, 22, 9.0], "dark"),
        ("guard", [4, 4.5, 6.5], [12, 6, 9.5], "guard"),
        ("grip", [7.25, -2, 7.25], [8.75, 4.5, 8.75], "grip"),
        ("pommel", [6.75, -3, 6.75], [9.25, -2, 9.25], "guard"),
    ], [("blade", stone, "edge"), ("dark", dark, None), ("guard", trim, None), ("grip", grip, "wrap")])
    save(sword_icon("#A8A598", "#C9B27A", "#5A3E2A", wide=True), "textures/item/ancient_stone_greatsword.png")
    # 古代石の杖
    staff_t = dict(HAND_TRANSFORMS)
    staff_t["thirdperson_righthand"] = {"rotation": [0, -90, 0], "translation": [0, 2, 0.5], "scale": [0.85, 0.85, 0.85]}
    staff_t["thirdperson_lefthand"] = {"rotation": [0, 90, 0], "translation": [0, 2, 0.5], "scale": [0.85, 0.85, 0.85]}
    weapon_3d("ancient_stone_staff", [
        ("shaft", [7.5, -6, 7.5], [8.5, 17, 8.5], "wood"),
        ("ring", [7, 15, 7], [9, 16, 9], "stone"),
        ("head", [6.5, 16, 6.5], [9.5, 18, 9.5], "stone"),
        ("crystal", [7, 18, 7], [9, 21, 9], "crystal"),
        ("foot", [7.25, -7, 7.25], [8.75, -6, 8.75], "stone"),
    ], [("wood", grip, "wrap"), ("stone", stone, None), ("crystal", crystal, "crystal")], staff_t, emissive=("crystal",))
    save(staff_icon("#7B5A3A", "#9A978A", "#8FD3FF"), "textures/item/ancient_stone_staff.png")
    # 見張りの石刃（レア：刃に光る古い命令の文字）
    weapon_3d("sentinel_blade", [
        ("blade", [7, 5, 7.5], [9, 17, 8.5], "blade"),
        ("runes", [7.6, 7, 7.3], [8.4, 15, 8.7], "rune"),
        ("tip", [7.5, 17, 7.5], [8.5, 18.5, 8.5], "blade"),
        ("guard", [4.5, 4, 7], [11.5, 5, 9], "guard"),
        ("grip", [7.5, 0, 7.5], [8.5, 4, 8.5], "grip"),
        ("core", [7.25, -1.5, 7.25], [8.75, 0, 8.75], "rune"),
    ], [("blade", "#7F7D73", "edge"), ("rune", "#6FE3D0", None), ("guard", dark, None), ("grip", grip, "wrap")],
        emissive=("rune",))
    save(sword_icon("#8C8A7E", "#6B695F", "#5A3E2A", glow="#6FE3D0"), "textures/item/sentinel_blade.png")
    # 欠け牙の大剣（レア：白く欠けた牙を埋めた刃）
    weapon_3d("chipped_fang_greatsword", [
        ("blade", [6.5, 6, 7.25], [9.5, 24, 8.75], "blade"),
        ("fang", [9.5, 10, 7.5], [10.5, 16, 8.5], "fang"),
        ("fang2", [5.5, 15, 7.5], [6.5, 20, 8.5], "fang"),
        ("tip", [7, 24, 7.25], [9, 26, 8.75], "blade"),
        ("guard", [4, 4.5, 6.5], [12, 6, 9.5], "guard"),
        ("grip", [7.25, -2, 7.25], [8.75, 4.5, 8.75], "grip"),
    ], [("blade", "#5B6270", "edge"), ("fang", "#F4F7FB", None), ("guard", "#3F4550", None), ("grip", grip, "wrap")],
        emissive=("fang",))
    save(sword_icon("#6C7383", "#F4F7FB", "#3F4550", wide=True, glow="#F4F7FB"), "textures/item/chipped_fang_greatsword.png")
    # 蜂晶の杖（GeckoLib。手に持つと立体、持ち物でも立体）
    write_json("models/item/bee_crystal_staff.json", {
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {"particle": "amemaru:item/bee_crystal_staff"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.7, 0.7, 0.7]},
            "firstperson_righthand": {"rotation": [-10, 0, -15], "translation": [2, 1, -1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [-10, 0, 15], "translation": [2, 1, -1], "scale": [0.55, 0.55, 0.55]},
            "gui": {"rotation": [20, -30, -40], "translation": [0, -1, 0], "scale": [0.45, 0.45, 0.45]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, -40], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [1, 1, 1]}
        }
    })
    # 古代石の盾（見習いの盾と同じ形。絵だけ替える）
    shield = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    sp = shield.load()
    r = random.Random(9)
    face, rim, emblem, back, handle = rgb("#8F8C80"), rgb("#5E5C52"), rgb("#6FE3D0"), rgb("#6B695F"), rgb("#5A3E2A")
    for y in range(22):
        for x in range(12):
            edge = x in (0, 11) or y in (0, 21)
            col = rim if edge else face
            if not edge and 4 <= x <= 7 and 6 <= y <= 15 or (2 <= x <= 9 and 9 <= y <= 11):
                col = emblem
            sp[x, y] = shade(col, 1.0 + r.uniform(-0.08, 0.06)) + (255,)
            sp[12 + x, y] = shade(back, 1.0 + r.uniform(-0.08, 0.06)) + (255,)
    for y in range(22):
        sp[24, y] = rim + (255,)
    for y in range(22, 23):
        for x in range(12):
            sp[x, y] = rim + (255,)
    for y in range(6):
        for x in range(26, 32):
            sp[x, y] = handle + (255,)
    save(shield, "textures/item/ancient_stone_shield_model.png")
    apprentice = json.load(open(os.path.join(ROOT, "models/item/apprentice_shield.json"), encoding="utf-8"))
    blocking = json.load(open(os.path.join(ROOT, "models/item/apprentice_shield_blocking.json"), encoding="utf-8"))
    tex = {"shield": "amemaru:item/ancient_stone_shield_model", "particle": "amemaru:item/ancient_stone_shield_model"}
    model = dict(apprentice)
    model["textures"] = tex
    model["overrides"] = [{"predicate": {"blocking": 1}, "model": "amemaru:item/ancient_stone_shield_blocking"}]
    write_json("models/item/ancient_stone_shield.json", model)
    block_model = dict(blocking)
    block_model["textures"] = tex
    write_json("models/item/ancient_stone_shield_blocking.json", block_model)
    save(shield_icon("#8F8C80", "#5E5C52", "#6FE3D0"), "textures/item/ancient_stone_shield.png")


def armor_and_accessories():
    plate = ("#8F8C80", "#C9B27A", "#6FE3D0")
    robe = ("#3E5A8C", "#C9B27A", "#8FD3FF")
    for item, kind, colors in [
        ("ancient_stone_helm", "head", plate), ("ancient_stone_cuirass", "chest", plate),
        ("ancient_stone_greaves", "legs", plate), ("ancient_stone_sabatons", "feet", plate),
        ("ancient_stone_hood", "head", robe), ("ancient_stone_robe", "chest", robe),
        ("ancient_stone_legwear", "legs", robe), ("ancient_stone_boots", "feet", robe),
    ]:
        save(armor_icon(kind, *colors), f"textures/item/{item}.png")
        write_json(f"models/item/{item}.json", flat_icon_model(item))
    save(ring_icon("#7C8C6A", "#F06A4A"), "textures/item/moss_iron_battle_ring.png")
    save(ring_icon("#7C8C6A", "#8FD3FF"), "textures/item/moss_iron_wisdom_ring.png")
    save(necklace_icon("#7A5B3A", "#6BE36B"), "textures/item/grassland_necklace.png")
    save(bracelet_icon("#8C8A7E", "#6FE3D0"), "textures/item/ishikure_bracelet.png")
    save(charm_icon("#9AA4B5", "#C9B27A", "#8FD3FF"), "textures/item/waystone_charm.png")
    for item in ["moss_iron_battle_ring", "moss_iron_wisdom_ring", "grassland_necklace", "ishikure_bracelet", "waystone_charm"]:
        write_json(f"models/item/{item}.json", flat_icon_model(item))


def materials():
    items = [
        ("ancient_stone", "stone", "#8F9AA6", None),
        ("moss_iron", "ingot", "#7C8079", "#5E8A4A"),
        ("crystal_shard", "shard", "#8FD3FF", None),
        ("tsunomofu_fluff", "fluff", "#F3EBDD", None),
        ("bee_stinger", "needle", "#8FD3FF", None),
        ("chipped_fang", "fang", "#E8E2D2", "#F4F7FB"),
        ("echo_fragment", "echo", "#B9A4FF", None),
        ("soft_horn", "horn", "#B58A5C", "#F3EBDD"),
        ("ishikure_core", "core", "#3E8F86", "#BFFFF4"),
    ]
    for i, (item, kind, color, accent) in enumerate(items):
        save(material_icon(kind, color, accent, seed=i + 3), f"textures/item/{item}.png")
        write_json(f"models/item/{item}.json", flat_icon_model(item))
    for item, color in [("crystal_fruit_crimson", "#E8473B"), ("crystal_fruit_azure", "#4D7CFF"),
                        ("crystal_fruit_jade", "#3DCB72"), ("crystal_fruit_amber", "#E3A33B"),
                        ("crystal_fruit_white", "#E9F0F7"), ("crystal_fruit_gold", "#F5D33F"),
                        ("crystal_fruit_violet", "#A65CF2")]:
        save(fruit_icon(color), f"textures/item/{item}.png")
        write_json(f"models/item/{item}.json", flat_icon_model(item))
    for egg in ["tsunomofu", "ishikure_soldier", "crystal_bee", "kakekiba_wolf"]:
        write_json(f"models/item/{egg}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


def workbenches():
    themes = {
        "weapon_workbench": ("#8A5A3A", "#C98A4A", "#9A978A", "anvil"),
        "armor_workbench": ("#6E5A44", "#8FA2B8", "#C9B27A", "plates"),
        "accessory_workbench": ("#5E4A6E", "#C07CFF", "#8FD3FF", "gems"),
    }
    for name, (wood, accent, metal, motif) in themes.items():
        r = random.Random(len(name))
        top = Image.new("RGBA", (16, 16))
        tp = top.load()
        for y in range(16):
            for x in range(16):
                f = 1.0 + r.uniform(-0.07, 0.05) - (0.12 if y % 4 == 3 else 0)
                tp[x, y] = shade(rgb(wood), f) + (255,)
        cloth = rgb(accent)
        for y in range(3, 13):
            for x in range(3, 13):
                tp[x, y] = shade(cloth, 1.0 + r.uniform(-0.05, 0.05)) + (255,)
        if motif == "anvil":
            for x in range(5, 11):
                tp[x, 6] = rgb(metal) + (255,)
                tp[x, 7] = shade(rgb(metal), 0.8) + (255,)
            for y in range(8, 11):
                tp[7, y] = shade(rgb(metal), 0.7) + (255,)
                tp[8, y] = shade(rgb(metal), 0.7) + (255,)
        elif motif == "plates":
            for y in range(5, 11):
                for x in range(5, 11):
                    if (x + y) % 3:
                        tp[x, y] = shade(rgb(metal), 1.0 + 0.1 * ((x + y) % 2)) + (255,)
        else:
            for cx, cy in [(6, 6), (10, 7), (7, 10)]:
                for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1)]:
                    tp[cx + dx, cy + dy] = rgb(metal) + (255,)
                tp[cx, cy] = (255, 255, 255, 255)
        save(top, f"textures/block/{name}_top.png")
        side = Image.new("RGBA", (16, 16))
        spx = side.load()
        for y in range(16):
            for x in range(16):
                f = 1.0 + r.uniform(-0.07, 0.05)
                if y < 4:
                    spx[x, y] = shade(rgb(wood), f * 1.05) + (255,)
                elif x < 3 or x > 12:
                    spx[x, y] = shade(rgb(wood), f * 0.85) + (255,)
                else:
                    spx[x, y] = (0, 0, 0, 0)
        for x in range(16):
            spx[x, 3] = shade(cloth, 0.9) + (255,)
        save(side, f"textures/block/{name}_side.png")
        tex = f"amemaru:block/{name}"
        write_json(f"models/block/{name}.json", {
            "parent": "minecraft:block/block",
            "textures": {"top": tex + "_top", "side": tex + "_side", "particle": tex + "_side"},
            "elements": [
                {"from": [0, 12, 0], "to": [16, 16, 16], "faces": {
                    "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                    "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "north": {"uv": [0, 0, 16, 4], "texture": "#side"},
                    "south": {"uv": [0, 0, 16, 4], "texture": "#side"},
                    "east": {"uv": [0, 0, 16, 4], "texture": "#side"},
                    "west": {"uv": [0, 0, 16, 4], "texture": "#side"}}},
            ] + [
                {"from": [x, 0, z], "to": [x + 3, 12, z + 3], "faces": {
                    face: {"uv": [0, 4, 3, 16], "texture": "#side"} for face in ("north", "south", "east", "west", "down")}}
                for x, z in [(1, 1), (12, 1), (1, 12), (12, 12)]
            ]
        })
        write_json(f"blockstates/{name}.json", {"variants": {
            f"facing={facing}": {"model": f"amemaru:block/{name}", "y": rot}
            for facing, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}})
        write_json(f"models/item/{name}.json", {"parent": f"amemaru:block/{name}"})


def misc():
    # 結晶蜂の針
    needle = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    np_ = needle.load()
    for x in range(16):
        width = 1 if x < 12 else 0
        for dy in range(-width, width + 1):
            a = 255 if dy == 0 else 140
            np_[x, 8 + dy] = (200 + x * 3, 235, 255, a)
    for y in range(6, 11):
        np_[0, y] = (140, 200, 255, 180)
    save(needle, "textures/entity/crystal_needle.png")
    # 予兆の帯（真ん中が濃く、ふちがはっきり）
    tele = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    tp = tele.load()
    for y in range(32):
        for x in range(32):
            edge = min(x, 31 - x)
            a = 110 + int(60 * math.sin(math.pi * y / 31.0))
            if edge < 2:
                a = 235
            elif (x + y) % 8 < 2:
                a = min(255, a + 40)
            tp[x, y] = (255, 255, 255, a)
    save(tele, "textures/fx/telegraph.png")


if __name__ == "__main__":
    weapons()
    armor_and_accessories()
    materials()
    workbenches()
    misc()
    print("art written to", ROOT)
