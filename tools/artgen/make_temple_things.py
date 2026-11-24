"""4階「崩壊した神殿」の仕掛けのブロック・物語の品・レア装備の絵とモデルを作る（11月24日【補完】）。

  python3 tools/artgen/make_temple_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only knight_helm_relic,harold_tag,prayer_bell_staff_icon

ブロック：
  mural_panel   壁画のパネル：白い石に浮き彫りした壁画の一枚（16×16）。12枚で一つの絵（192×16）になる：
                第一時代（0〜2）手をつないだ人々が大きな結晶を囲み、同じ一本の光の糸を握る ／
                大断絶（3〜6）塔の根元の輪の装置がまばゆく光り、糸が焼け切れ、人々が背を向け合って散る ／
                第二時代（7〜11）切れた糸の端をもう一度結び、新しい塔が建ち、結び目の灯が街に灯る。
                向き（facing）× 回り（turn：表の絵を90度ずつ回す）× 絵の番号（piece。12〜15は無地）× 灯（lit）。
                lit でない絵（「一枚の絵」の灯る前）は、色のない薄い浮き彫り
  sound_plate   音の床：白い石の板（高さ2ピクセル）に、色の宝石（白・青・金・紅）と彫った輪。光ると輪が色に光る
  chime_stone   鈴の石：白い石の台の柱に、淡い紫の結晶の鈴
  knight_relic  結晶騎士団の遺品：石の台に、白い冑（青い結晶の飾り）と、折れた槍と、認識票
品（16×16 を描き、enhance_icons.py で 32×32 にする）：knight_helm_relic・harold_tag・prayer_bell_staff_icon
レア装備（GeckoLib）：prayer_bell_staff 祈り手の鈴（白い石の輪に、白・青・金・紅の結晶の鈴。use で鈴が揺れて光る）
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel  # noqa: E402
from javamodel import JavaModel  # noqa: E402
from make_forest_things import paint_rows  # noqa: E402
from make_forest_models import posed, sheet_font  # noqa: E402
from make_temple_models import TEMPLE, TemplePainter  # noqa: E402
from materials import RAMPS, hsh, pick  # noqa: E402

CREDIT = "AMEMARU: 4階の仕掛け（tools/artgen/make_temple_things.py で生成）"
PIECES = 12
NOTE_NAMES = ("white", "blue", "gold", "red")
NOTE_RGB = {"white": (236, 240, 244), "blue": (70, 150, 228), "gold": (240, 196, 72), "red": (220, 74, 66)}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


# ================================================================ 壁画（192×16 の一枚の絵）

STONE = (214, 205, 186)
CARVE = (96, 78, 60)
CARVE_HI = (150, 128, 100)
GOLD = (248, 206, 86)
CRYSTAL = (140, 214, 238)
CRYSTAL_HI = (236, 250, 255)
FIRE = (236, 120, 60)
FIRE_HI = (255, 214, 120)
TOWER = (118, 104, 88)

PERSON = ["..#..", ".###.", "..#..", "#####", ".###.", ".###.", ".#.#.", ".#.#."]       # 腕を広げて手をつなぐ
AWAY = ["..#..", ".###.", "..#..", ".###.", "#.#.#", ".###.", ".#.#.", "#...#"]         # 背を向けて走り去る
FALLEN = ["........", "........", "........", "........", "...##...", "#######.", ".#.#..#.", "........"]
KNEEL = ["..#..", ".###.", "..#..", ".####", ".##..", ".###.", "##...", "#....."[:5]]   # ひざをついて糸を結ぶ


def mural_strip():
    W, H = PIECES * 16, 16
    img = Image.new("RGB", (W, H), STONE)
    px = img.load()
    # 石の地（細かいまだら）と、上下のふち
    for x in range(W):
        for y in range(H):
            n = hsh(x, y, 701)
            v = int((n - 0.5) * 16)
            px[x, y] = tuple(max(0, min(255, c + v)) for c in STONE)
        px[x, 0] = (150, 140, 122)
        px[x, 15] = (140, 130, 112)
        px[x, 1] = (232, 224, 206)
        px[x, 14] = (182, 172, 152)

    def sprite(rows, x0, y0, col=CARVE, flip=False):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    xx = x0 + (len(row) - 1 - i if flip else i)
                    if 0 <= xx < W and 0 <= y0 + j < H:
                        px[xx, y0 + j] = col
                        # 浮き彫りの光（左上）
                        if 0 <= xx - 1 < W and px[xx - 1, y0 + j] == STONE:
                            pass

    def put(x, y, col):
        if 0 <= x < W and 0 <= y < H:
            px[x, y] = col

    def hline(x0, x1, y, col, gaps=()):
        for x in range(x0, x1 + 1):
            if x not in gaps:
                put(x, y, col)

    ground = 13
    for x in range(2, W - 2):
        put(x, ground, (176, 164, 140))
    # ---- 第一時代（0〜2）：手をつないで、大きな結晶を囲む。みんなで一本の光の糸を握る
    people = [2, 7, 12, 30, 35, 40, 45]
    for x in people:
        sprite(PERSON, x, 5)
    hline(2, 46, 8, GOLD, gaps=())
    # 大きな結晶（まんなか）と台
    cx = 23
    for y in range(3, 12):
        half = min(y - 2, 12 - y, 3)
        for x in range(cx - half, cx + half + 1):
            put(x, y, CRYSTAL)
    for y in range(4, 9):
        put(cx - 1, y, CRYSTAL_HI)
    for x in range(cx - 4, cx + 5):
        put(x, 12, CARVE_HI)
    # ---- 大断絶（3〜6）：塔の根元の輪の装置が光り、糸が焼け切れ、人々が背を向け合って散る
    tx = 54
    for y in range(1, 12):
        w = 1 if y < 4 else 2
        for x in range(tx - w, tx + w + 1):
            put(x, y, TOWER)
    for a in range(0, 360, 20):
        r = 3.2
        put(int(round(tx + r * math.cos(math.radians(a)))), int(round(10 + r * math.sin(math.radians(a)) * 0.7)), FIRE_HI)
    for k, (dx, dy) in enumerate(((-6, -3), (6, -3), (-7, 1), (7, 1), (-4, -6), (4, -6))):
        for t in range(1, 3):
            put(tx + dx + (t if dx > 0 else -t), 10 + dy, FIRE_HI if k % 2 else FIRE)
    # 焼け切れる糸（切れ目と火の粉）
    for x in range(62, 112):
        if (x - 62) % 7 in (3, 4):
            continue
        put(x, 8, GOLD if (x - 62) % 7 < 2 else FIRE)
    for k in range(10):
        put(64 + k * 5, 6 + (k % 3), FIRE_HI)
    # 背を向け合って散る人々（倒れた人も）
    for x, flip in ((74, True), (82, False), (92, True), (100, False), (110, True)):
        sprite(AWAY, x, 5, flip=flip)
    sprite(FALLEN, 86, 6)
    # ---- 第二時代（7〜11）：切れた糸を結び直し、新しい塔が建ち、結び目の灯が街に灯る
    sprite(PERSON, 114, 5)
    sprite(PERSON, 122, 5)
    hline(114, 126, 8, GOLD)
    put(120, 7, GOLD)
    put(121, 8, (255, 240, 170))   # 結び目
    put(120, 9, GOLD)
    nx = 140
    for y in range(1, 12):
        w = 1 if y < 3 else 2
        for x in range(nx - w, nx + w + 1):
            put(x, y, (150, 136, 112))
    for y in range(3, 12, 3):
        hline(nx - 4, nx + 4, y, CARVE_HI)            # 足場
    put(nx, 0 + 1, GOLD)
    # 街の屋根と、結び目の灯（金の点を糸がつなぐ）
    roofs = [(150, 5, 8), (157, 4, 9), (164, 6, 7), (171, 4, 10), (179, 5, 8), (186, 4, 9)]
    for x0, w, top in roofs:
        for x in range(x0, x0 + w):
            for y in range(top, ground):
                put(x, y, CARVE if y > top else CARVE_HI)
    lamps = [(152, 6), (159, 5), (166, 7), (173, 5), (181, 6), (188, 5)]
    for i in range(len(lamps) - 1):
        (x0, y0), (x1, y1) = lamps[i], lamps[i + 1]
        for t in range(0, x1 - x0 + 1):
            put(x0 + t, int(round(y0 + (y1 - y0) * t / max(1, x1 - x0))) - 1, (220, 186, 96))
    for (x, y) in lamps:
        put(x, y - 1, (255, 236, 150))
    for x in (130, 134):
        sprite(PERSON, x, 5)
    hline(130, 138, 8, GOLD)
    # 浮き彫りの光と影（彫った線の左上を明るく、右下を暗く）
    out = img.copy()
    po = out.load()
    for x in range(1, W - 1):
        for y in range(2, H - 2):
            c = px[x, y]
            if c in (CARVE, TOWER) and px[x - 1, y - 1] not in (CARVE, TOWER):
                po[x, y] = CARVE_HI if c == CARVE else (150, 136, 118)
    return out


def dim(img):
    """灯る前：色のない、薄い浮き彫り"""
    a = np.asarray(img).astype(float)
    gray = a.mean(axis=2, keepdims=True)
    stone = np.array(STONE, dtype=float)
    out = stone + (gray - stone.mean()) * 0.45
    out = np.repeat(out, 3, axis=2) if out.shape[2] == 1 else out
    out = np.clip(out * 0.92, 0, 255)
    return Image.fromarray(out.astype(np.uint8), "RGB")


def mural_side():
    img = Image.new("RGB", (16, 16))
    p = img.load()
    for x in range(16):
        for y in range(16):
            v = int((hsh(x, y, 711) - 0.5) * 18)
            base = (196, 188, 170) if 1 <= x <= 14 and 1 <= y <= 14 else (160, 150, 132)
            p[x, y] = tuple(max(0, min(255, c + v)) for c in base)
    return img


def mural_model(texture_front, turn):
    side = "amemaru:block/mural/side"
    faces = {}
    for d in ("east", "south", "west", "up", "down"):
        faces[d] = {"texture": "#side", "uv": [0, 0, 16, 16], "cullface": d}
    faces["north"] = {"texture": "#front", "uv": [0, 0, 16, 16], "cullface": "north"}
    if turn:
        faces["north"]["rotation"] = 90 * turn
    return {"credit": CREDIT, "parent": "minecraft:block/block",
            "textures": {"particle": side, "side": side, "front": texture_front},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def build_murals(base):
    strip = mural_strip()
    tex_dir = os.path.join(base, "textures/block/mural")
    os.makedirs(tex_dir, exist_ok=True)
    mural_side().save(os.path.join(tex_dir, "side.png"))
    blank = Image.new("RGB", (16, 16))
    bp = blank.load()
    for x in range(16):
        for y in range(16):
            bp[x, y] = tuple(max(0, min(255, c + int((hsh(x, y, 721) - 0.5) * 14))) for c in STONE)
    blank.save(os.path.join(tex_dir, "blank.png"))
    for p in range(PIECES):
        piece = strip.crop((p * 16, 0, p * 16 + 16, 16))
        piece.save(os.path.join(tex_dir, "piece_%d.png" % p))
        dim(piece).save(os.path.join(tex_dir, "piece_%d_dim.png" % p))
    model_dir = os.path.join(base, "models/block/mural")
    for p in range(PIECES):
        for lit in (True, False):
            tex = "amemaru:block/mural/piece_%d%s" % (p, "" if lit else "_dim")
            for t in range(4):
                write_json(os.path.join(model_dir, "p%d_t%d%s.json" % (p, t, "" if lit else "_dim")), mural_model(tex, t))
    write_json(os.path.join(model_dir, "blank.json"), mural_model("amemaru:block/mural/blank", 0))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            for piece in range(16):
                for turn in range(4):
                    if piece < PIECES:
                        m = "amemaru:block/mural/p%d_t%d%s" % (piece, turn, "" if lit else "_dim")
                    else:
                        m = "amemaru:block/mural/blank"
                    v = {"model": m}
                    if rot:
                        v["y"] = rot
                    variants["facing=%s,lit=%s,piece=%d,turn=%d" % (facing, "true" if lit else "false", piece, turn)] = v
    write_json(os.path.join(base, "blockstates/mural_panel.json"), {"variants": variants})
    write_json(os.path.join(base, "models/item/mural_panel.json"), {"parent": "amemaru:block/mural/p1_t0"})
    return strip


# ================================================================ 音の床

def plate_top(note, lit):
    img = Image.new("RGB", (16, 16))
    p = img.load()
    col = NOTE_RGB[note]
    for x in range(16):
        for y in range(16):
            v = int((hsh(x, y, 731) - 0.5) * 14)
            base = (226, 222, 212)
            if x in (0, 15) or y in (0, 15):
                base = (176, 170, 158)
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 < d < 5.8:
                base = tuple(int(c * 0.9 + 40) for c in col) if lit else (150, 144, 132)
            if d < 2.6:
                base = tuple(min(255, int(c * (1.15 if lit else 0.8))) for c in col)
                if lit and d < 1.2:
                    base = (255, 255, 255)
            p[x, y] = tuple(max(0, min(255, c + v)) for c in base)
    return img


def plate_side():
    img = Image.new("RGB", (16, 16))
    p = img.load()
    for x in range(16):
        for y in range(16):
            v = int((hsh(x, y, 741) - 0.5) * 14)
            p[x, y] = tuple(max(0, min(255, c + v)) for c in (188, 182, 170))
    return img


def build_plates(base):
    tex_dir = os.path.join(base, "textures/block/sound_plate")
    os.makedirs(tex_dir, exist_ok=True)
    plate_side().save(os.path.join(tex_dir, "side.png"))
    variants = {}
    for k, note in enumerate(NOTE_NAMES):
        for lit in (False, True):
            name = "%s%s" % (note, "_lit" if lit else "")
            plate_top(note, lit).save(os.path.join(tex_dir, name + ".png"))
            side = "amemaru:block/sound_plate/side"
            top = "amemaru:block/sound_plate/" + name
            faces = {d: {"texture": "#side", "uv": [0, 14, 16, 16]} for d in ("north", "east", "south", "west")}
            faces["up"] = {"texture": "#top", "uv": [0, 0, 16, 16]}
            faces["down"] = {"texture": "#side", "uv": [0, 0, 16, 16], "cullface": "down"}
            write_json(os.path.join(base, "models/block/sound_plate", name + ".json"),
                       {"credit": CREDIT, "parent": "minecraft:block/block",
                        "textures": {"particle": top, "side": side, "top": top},
                        "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": faces}]})
            variants["lit=%s,note=%d" % ("true" if lit else "false", k)] = {"model": "amemaru:block/sound_plate/" + name}
    write_json(os.path.join(base, "blockstates/sound_plate.json"), {"variants": variants})
    write_json(os.path.join(base, "models/item/sound_plate.json"), {"parent": "amemaru:block/sound_plate/blue_lit"})


# ================================================================ 鈴の石・遺品（立体のモデル）

class ThingsPainter(TemplePainter):
    def paint_white_stone(self, c):
        tone = self.std(c, "marble", base=4.2, noise=0.25, blob=0.4, up=1.1, down=1.0, specks=0.03)
        return pick(TEMPLE["marble"], tone)

    def paint_steel_white(self, c):
        tone = self.std(c, "marble", base=4.6, noise=0.2, blob=0.3, up=1.6, down=1.2)
        if self.tex(c, 751) > 0.95:
            tone += 1.0
        return pick(RAMPS["steel"], tone)

    def paint_wood_old(self, c):
        tone = self.std(c, "walnut", base=3.2, noise=0.3, blob=0.2, up=1.0, down=1.0)
        if c.i % 3 == 0:
            tone -= 0.6
        return pick(RAMPS["walnut"], tone)

    def paint_tag_silver(self, c):
        tone = 4.2 + (self.tex(c, 761) - 0.5) * 0.6 + self.bevel(c, up=1.2, down=1.0)
        if c.face.d == "up" and c.j == 1 and 0 < c.i < c.W - 1:
            tone -= 1.6   # 刻まれた名前の線
        return pick(RAMPS["steel"], tone)

    def paint_slit(self, c):
        return pick(TEMPLE["pit"], 1.4)

    def paint_glow_bell(self, c):
        tone = 3.9 + (self.tex(c, 771) - 0.5) * 0.5
        if c.edge_dist()["left"] == 0:
            tone += 1.2
        return pick(TEMPLE["glow_note_white"], tone - 0.4) if c.el.tags.get("white") else pick(RAMPS["glow_lavender"], tone)


RAMPS["glow_lavender"] = TEMPLE["glow_note_white"][:3] + [(198, 170, 240), (220, 200, 250), (238, 228, 255), (255, 255, 255)]


def build_chime():
    m = JavaModel("chime_stone", 64, 64)
    m.box("base", (3, 0, 3), (13, 3, 13), "white_stone")
    m.box("base_top", (4, 3, 4), (12, 4, 12), "white_stone")
    m.box("pillar", (6, 4, 6), (10, 9, 10), "white_stone")
    m.box("cup", (5, 9, 5), (11, 10, 11), "gold")
    m.box("bell", (5.5, 10, 5.5), (10.5, 14, 10.5), "glow_bell", glow=True)
    m.box("bell_lip", (5, 10, 5), (11, 10.5, 11), "glow_bell", glow=True)
    m.box("knob", (7, 14, 7), (9, 15, 9), "gold")
    return m


def build_relic():
    m = JavaModel("knight_relic", 64, 64)
    m.box("base", (2, 0, 2), (14, 2, 14), "white_stone")
    # 冑（白い鋼。面頬の細いすき間。頭の上に青い結晶の飾り）
    m.box("helm", (4, 2, 5), (10, 7, 11), "steel_white")
    m.box("helm_rim", (3.5, 2, 4.5), (10.5, 3, 11.5), "steel_white")
    m.box("visor", (4.5, 4, 4.9), (9.5, 4.5, 5.1), "slit")
    m.box("crest", (6.5, 7, 6), (7.5, 9, 10), "glow_crystal", glow=True)
    # 折れた槍（台に立てかける）
    m.box("spear", (11.5, 2, 9.5), (12.5, 12, 10.5), "wood_old", rot=((12, 2, 10), "z", 22.5))
    m.box("spear_band", (11.3, 9, 9.3), (12.7, 10, 10.7), "gold", rot=((12, 2, 10), "z", 22.5))
    # 認識票（鎖ごと、台の上に）
    m.box("tag", (4, 2, 12), (7, 2.5, 13.5), "tag_silver")
    m.box("chain", (7, 2, 12.5), (10, 2.3, 12.8), "tag_silver")
    return m


def save_java(base, m, painter_cls):
    m.cull_hidden_faces()
    m.pack()
    tex = m.paint(painter_cls(m), frames=1)[0]
    Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
    m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
    return m, tex


# ================================================================ 祈り手の鈴（GeckoLib のレア装備）

BELL = "prayer_bell_staff"
BELL_COLORS = (("white", -3.5, 0), ("blue", -1.2, 1), ("gold", 1.2, 2), ("red", 3.5, 3))


def build_bell_staff():
    g = GeoModel(BELL, 64, 64)
    g.bone("staff", (0, 0, 0))
    g.bone("ring", (0, 13, 0), parent="staff")
    g.cube("staff", "butt", (-1, -16, -1), (2, 2, 2), "gold")
    g.cube("staff", "shaft_0", (-0.5, -14, -0.5), (1, 14, 1), "ivory")
    g.cube("staff", "grip", (-1, -6, -1), (2, 5, 2), "singer_blue", inflate=0.1)
    g.cube("staff", "shaft_1", (-0.5, 0, -0.5), (1, 9, 1), "ivory")
    g.cube("staff", "collar", (-1, 8, -1), (2, 1, 2), "gold")
    # 白い石の輪（上が開いた四角い輪を、細い石で組む）
    g.cube("ring", "ring_bottom", (-5, 9, -0.5), (10, 1, 1), "marble")
    g.cube("ring", "ring_left", (4, 9, -0.5), (1, 8, 1), "marble")
    g.cube("ring", "ring_right", (-5, 9, -0.5), (1, 8, 1), "marble")
    g.cube("ring", "ring_top", (-5, 17, -0.5), (10, 1, 1), "marble")
    g.cube("ring", "jewel", (-1, 17.5, -1), (2, 2, 2), "glow_halo", glow=True)
    for name, x, k in BELL_COLORS:
        bone = "bell_" + name
        g.bone(bone, (x, 16.5, 0), parent="ring")
        g.cube(bone, bone + "_cord", (x - 0.5, 14, -0.25), (1, 3, 0), "gold")
        g.cube(bone, bone + "_bell", (x - 1, 11, -1), (2, 3, 2), "glow_note_" + name, glow=True)
    return g


BELL_ANIMS = {
    "animation.%s.idle" % BELL: {"loop": True, "animation_length": 1.0, "bones": {}},
    "animation.%s.use" % BELL: {
        "animation_length": 0.8,
        "bones": {("bell_" + n): {"rotation": {"0.0": [0, 0, 0], "%.2f" % (0.08 + k * 0.05): [0, 0, (-1) ** k * 24],
                                               "%.2f" % (0.3 + k * 0.05): [0, 0, (-1) ** (k + 1) * 14], "0.8": [0, 0, 0]},
                                  "scale": {"0.0": [1, 1, 1], "0.15": [1.3, 1.3, 1.3], "0.8": [1, 1, 1]}}
                  for n, _, k in BELL_COLORS},
    },
}

BELL_ICON = [
    "................",
    "...oooooooooo...",
    "...oSSSSSSSSo...",
    "...oS.gooo.So...",
    "...oS.g..g.So...",
    "...oSwo..oRSo...",
    "...oS.bo.G.So...",
    "...oSSSSSSSSo...",
    "....oooYYooo....",
    ".......IY.......",
    ".......IY.......",
    "......oBBo......",
    "......oBBo......",
    ".......IY.......",
    ".......IY.......",
    ".......YY.......",
]
BELL_ICON_PAL = {"o": "#3A3428", "S": "#E6E0D2", "g": "#C9A03A", "w": "#F4F6F8", "b": "#3F9CE6", "G": "#EDC75A", "R": "#D2423A",
                 "Y": "#C9A03A", "I": "#E8E2D4", "B": "#2C5D8D"}

HELM_ICON = [
    "................",
    ".......cc.......",
    "......cCCc......",
    ".....oooooo.....",
    "....oWWWWWWo....",
    "...oWWWWWWWWo...",
    "...oWHWWWWWWo...",
    "...oWWWWWWWWo...",
    "...oWkkkkkkWo...",
    "...oWWWWWWWWo...",
    "...oWWWsWWWWo...",
    "...oWWWsWWWWo...",
    "...ooWWWWWWoo...",
    "....oGGGGGGo....",
    ".....oooooo.....",
    "................",
]
HELM_ICON_PAL = {"o": "#2E3238", "W": "#D8DDE2", "H": "#FFFFFF", "k": "#1A1C20", "s": "#9AA2AA", "c": "#6CBDF4", "C": "#D2EEFD",
                 "G": "#C9A03A"}

TAG_ICON = [
    "................",
    "..o.o...........",
    ".o...o..........",
    "..o.o.o.........",
    "......o.o.......",
    ".......o.o......",
    "........oo......",
    ".......oSSSSo...",
    "......oSHSSSSo..",
    "......oSkkkkSo..",
    "......oSSSSSSo..",
    "......oSkkkSSo..",
    "......oSSSSdSo..",
    "......oSSSdSSo..",
    ".......oooooo...",
    "................",
]
TAG_ICON_PAL = {"o": "#3A3E44", "S": "#B8C0C8", "H": "#F2F6FA", "k": "#5A6068", "d": "#7A8088"}


def export_bell(repo, g, frames):
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    g.save(repo, "item", frames, animations=BELL_ANIMS)
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/%s_icon" % BELL}}
    write_json(os.path.join(base, "models/item/%s.json" % BELL), {
        "loader": "forge:separate_transforms", "gui_light": "front",
        "base": {"parent": "amemaru:item/%s_3d" % BELL},
        "perspectives": {"gui": flat, "fixed": flat, "ground": flat},
    })
    with open(os.path.join(base, "models/item/elder_branch_staff_3d.json"), encoding="utf-8") as fp:
        display = json.load(fp)["display"]
    write_json(os.path.join(base, "models/item/%s_3d.json" % BELL), {
        "credit": "AMEMARU: 4階のレア装備（tools/artgen/make_temple_things.py で生成）",
        "parent": "builtin/entity", "gui_light": "front",
        "textures": {"particle": "amemaru:item/%s_icon" % BELL}, "display": display,
    })


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    strip = build_murals(base)
    build_plates(base)
    built = []
    for m in (build_chime(), build_relic()):
        built.append(save_java(base, m, ThingsPainter))
    write_json(os.path.join(base, "blockstates/chime_stone.json"), {"variants": {"": {"model": "amemaru:block/chime_stone"}}})
    write_json(os.path.join(base, "models/item/chime_stone.json"), {"parent": "amemaru:block/chime_stone"})
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "amemaru:block/knight_relic"}
        if rot:
            v["y"] = rot
        variants["facing=" + facing] = v
    write_json(os.path.join(base, "blockstates/knight_relic.json"), {"variants": variants})
    write_json(os.path.join(base, "models/item/knight_relic.json"), {"parent": "amemaru:block/knight_relic"})
    for name in ("silent_statue", "prayer_echo", "crystal_beetle", "broken_beast", "cantare"):
        write_json(os.path.join(base, "models/item/%s_spawn_egg.json" % name), {"parent": "minecraft:item/template_spawn_egg"})
    for name, rows, pal in (("knight_helm_relic", HELM_ICON, HELM_ICON_PAL), ("harold_tag", TAG_ICON, TAG_ICON_PAL),
                            (BELL + "_icon", BELL_ICON, BELL_ICON_PAL)):
        paint_rows(rows, pal).save(os.path.join(base, "textures/item", name + ".png"))
        if not name.endswith("_icon"):
            write_json(os.path.join(base, "models/item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}})
    g = build_bell_staff()
    g.pack()
    frames = g.paint(TemplePainter(g), frames=1)
    export_bell(repo, g, frames)
    if out:
        check(repo, strip, built, g, frames[0], out)
    print("ok")


def check(repo, strip, built, bell, bell_tex, out):
    from render import render_many
    os.makedirs(out, exist_ok=True)
    big = strip.resize((strip.size[0] * 6, strip.size[1] * 6), Image.NEAREST)
    dimmed = dim(strip).resize(big.size, Image.NEAREST)
    tiles = []
    for m, tex in built:
        for yaw in (160, -30):
            tiles.append(render_many([(m, tex)], yaw=yaw, pitch=22, scale=14, size=(260, 280), center=(8, 7, 8)))
    for yaw in (180, 150):
        tiles.append(render_many([(bell, bell_tex)], yaw=yaw, pitch=8, scale=9, size=(260, 280), center=(0, 2, 0)))
    tiles.append(render_many([(posed(bell, {"bell_white": ((0, 0, 24),), "bell_gold": ((0, 0, 24),),
                                            "bell_blue": ((0, 0, -24),), "bell_red": ((0, 0, -24),)}), bell_tex)],
                             yaw=170, pitch=8, scale=9, size=(260, 280), center=(0, 2, 0)))
    W = max(big.size[0], 260 * len(tiles))
    sheet = Image.new("RGBA", (W, big.size[1] * 2 + 330 + 160), (40, 44, 48, 255))
    sheet.paste(big, (0, 26))
    sheet.paste(dimmed, (0, 26 + big.size[1] + 8))
    y = 26 + big.size[1] * 2 + 24
    for k, t in enumerate(tiles):
        sheet.alpha_composite(t, (260 * k, y))
    plates = Image.new("RGBA", (8 * 80, 80), (40, 44, 48, 255))
    for k, note in enumerate(NOTE_NAMES):
        for j, lit in enumerate((False, True)):
            plates.paste(plate_top(note, lit).resize((64, 64), Image.NEAREST), ((k * 2 + j) * 80 + 8, 8))
    sheet.alpha_composite(plates, (0, y + 290))
    d = ImageDraw.Draw(sheet)
    d.text((6, 4), "壁画（12枚で一枚の絵：上は灯った絵・下は灯る前）・鈴の石・遺品・祈り手の鈴・音の床（白・青・金・紅、消えた・光った）",
           fill=(235, 235, 220), font=sheet_font(16))
    sheet.save(os.path.join(out, "temple_things_check.png"))


if __name__ == "__main__":
    main()
