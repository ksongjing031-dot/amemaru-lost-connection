"""AMEMARU: LOST CONNECTION の Phase 5 のNPCの見た目（プレイヤーと同じ形の 64×64 のスキン）を作る。

※ 11月22日から、5人のNPCは凹凸のある立体のモデル（tools/artgen/make_npc_models.py）で描く。このスクリプトは同じ名前の絵
  （textures/entity/npc/<名前>.png）を書くので、そのまま動かすと立体のモデルの絵が消える。プレイヤーと同じ形のスキンの作り方の
  見本として残している（使うときは、出す所を変えること）。

使い方: python3 tools/artgen/make_npc_skins.py [assets/amemaru のフォルダ] [--preview 出力.png]

作るもの：textures/entity/npc/<名前>.png と、表情の差分 <名前>_<表情>.png（顔の正面だけが違う）
  lilia（リリア）・gard（ガルド）・serena（セレナ）・doran（ドラン）・bard（バルド）
見た目は Phase 0-A 8章のとおり（リリア：栗色の髪を一つに結び、若草色の受付服に羊歯の葉のブローチ。ガルド：灰色の短髪、左頰に古傷、
黒鉄の片腕甲、深紅のマント。セレナ：銀に近い淡い金髪、星の刺繍の白い司祭服。ドラン：赤茶のひげを編み込み、焦げ跡だらけの革エプロン。
バルド：第七次遠征の生き残り。古い騎士団の上着【補完】）。仮素材なので、同じ名前の絵に差し替えれば、そのまま使える。
"""
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "src/main/resources/assets/amemaru"


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


class Skin:
    def __init__(self, slim, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.slim = slim
        self.rng = random.Random(seed)

    def px(self, x, y, c):
        if c is None:
            return
        self.img.putpixel((x, y), hexc(c) if isinstance(c, str) else c)

    def box(self, u, v, w, h, d, face_fn):
        """箱のUV（Minecraft の形）に、面ごとに色を塗る。face_fn(面, x, y, 幅, 高さ) → 色（None は塗らない）"""
        faces = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
        }
        for name, (fx, fy, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    self.px(fx + x, fy + y, face_fn(name, x, y, fw, fh))

    def grid(self, u, v, rows, pal):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row.split()):
                if ch != ".":
                    self.px(u + x, v + y, pal[ch])

    # 部品の場所（64×64 のスキン）
    def head(self, fn):
        self.box(0, 0, 8, 8, 8, fn)

    def hat(self, fn):
        self.box(32, 0, 8, 8, 8, fn)

    def body(self, fn, layer=False):
        self.box(16, 32 if layer else 16, 8, 12, 4, fn)

    def arm(self, side, fn, layer=False):
        w = 3 if self.slim else 4
        if side == "right":
            self.box(40, 32 if layer else 16, w, 12, 4, fn)
        else:
            self.box(48 if layer else 32, 48, w, 12, 4, fn)

    def leg(self, side, fn, layer=False):
        if side == "right":
            self.box(0, 32 if layer else 16, 4, 12, 4, fn)
        else:
            self.box(0 if layer else 16, 48, 4, 12, 4, fn)

    def face(self, rows, pal):
        self.grid(8, 8, rows, pal)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


def noisy(rng, c, amount=0.06):
    return shade(hexc(c), 1.0 + rng.uniform(-amount, amount))


# ================================================================ リリア
def lilia():
    s = Skin(True, 11)
    r = s.rng
    hair, hair_d, hair_l = "#7A4A2A", "#5A341C", "#9C6538"
    skin, skin_d = "#F6DCC6", "#E9C3A8"
    green, green_d, green_l = "#8CC66B", "#679E4B", "#A9DA8A"
    cream, cream_d = "#F4EAD3", "#DCCDAE"
    brown, brown_d = "#7B5236", "#5A3B26"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair if (x + y) % 3 else hair_l, 0.04)
        if face == "bottom":
            return skin_d
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair_d if y >= 6 else hair, 0.05)
        # 横：上は髪、耳のあたりから下は肌
        if y <= 2 or (face == "right" and x <= 1) or (face == "left" and x >= 6):
            return noisy(r, hair, 0.05)
        return skin if y < 7 else skin_d
    s.head(head)
    # 帽子の層：横の髪のふくらみ、後ろで結んだ髪の根元（深い茶のリボン）
    def hat(face, x, y, w, h):
        if face == "back" and 2 <= x <= 5 and 5 <= y <= 7:
            return "#4E7A35" if y == 5 else hair
        if face in ("right", "left") and y <= 3:
            return noisy(r, hair, 0.05) if (x + y) % 2 == 0 else None
        if face == "top" and (x + 2 * y) % 5 == 0:
            return hair_l
        return None
    s.hat(hat)

    def body(face, x, y, w, h):
        if face == "front":
            if y == 0 and 2 <= x <= 5:
                return skin if 3 <= x <= 4 else cream
            if y <= 1:
                return cream
            if y == 7:
                return brown if x not in (3, 4) else "#C9A44A"
            if 3 <= x <= 4 and y < 7:
                return cream if y % 2 else cream_d
            if (x, y) == (1, 3):
                return "#2F7A35"          # 羊歯の葉のブローチ
            if (x, y) in ((1, 2), (2, 3), (1, 4)):
                return "#4E9A45"
            return noisy(r, green, 0.04) if y < 7 else noisy(r, green_d, 0.04)
        if face == "back":
            if y == 7:
                return brown
            return noisy(r, green, 0.04) if y < 7 else green_d
        if face in ("top", "bottom"):
            return green_d
        return green if y != 7 else brown
    s.body(body)
    # 上着の層：背中に下ろした結い髪
    def body_layer(face, x, y, w, h):
        if face == "back" and 3 <= x <= 4 and y <= 5:
            return noisy(r, hair if y < 5 else hair_d, 0.05)
        return None
    s.body(body_layer, layer=True)

    def arm(face, x, y, w, h):
        if face == "top":
            return green
        if face == "bottom":
            return skin_d
        if y >= 10:
            return skin
        if y == 9:
            return cream
        return noisy(r, green if y < 8 else green_d, 0.04)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return green_d
        if face == "bottom":
            return brown_d
        if y <= 4:
            return noisy(r, green_d, 0.04)
        if y <= 8:
            return cream if y != 8 else cream_d
        return brown if y < 11 else brown_d
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_l, "d": hair_d, "S": skin, "s": skin_d, "W": "#FFFFFF", "E": "#6B3E22", "e": "#3B2414",
           "M": "#C66A64", "m": "#A04E4A", "B": "#F3B6A6", "b": "#5A341C", "O": "#7A2E2E"}
    faces = {
        "": ["H H H H H H H H", "H H H H H H H H", "H h H H H H h H", "H S S S S S S H",
             "S W E S S E W S", "S S S S S S S S", "S S S M M S S S", "S S S S S S S S"],
        "smile": ["H H H H H H H H", "H H H H H H H H", "H h H H H H h H", "H S S S S S S H",
                  "S e e S S e e S", "S B S S S S B S", "S S M M M M S S", "S S S m m S S S"],
        "surprised": ["H H H H H H H H", "H H H H H H H H", "H b S S S S b H", "H S S S S S S H",
                      "S W E S S E W S", "S W e S S e W S", "S S S O O S S S", "S S S O O S S S"],
        "worried": ["H H H H H H H H", "H H H H H H H H", "H h H H H H h H", "H b S S S S b H",
                    "S W E S S E W S", "S S S S S S S S", "S S S m m S S S", "S S S S S S S S"],
    }
    return s, pal, faces


# ================================================================ ガルド
def gard():
    s = Skin(False, 23)
    r = s.rng
    hair, hair_d = "#8E8E8E", "#6A6A6A"
    skin, skin_d = "#D7A47F", "#B9855F"
    iron, iron_d, iron_l = "#3B3E44", "#26282C", "#686D75"
    crimson, crimson_d = "#8E1C24", "#6A1218"
    leather = "#4A3426"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair, 0.08)
        if face == "bottom":
            return skin_d
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair if y < 4 else hair_d, 0.08) if y < 6 else skin_d
        if y <= 1 or (face == "right" and x <= 1 and y <= 3) or (face == "left" and x >= 6 and y <= 3):
            return noisy(r, hair, 0.08)
        return skin if y < 7 else skin_d
    s.head(head)

    def body(face, x, y, w, h):
        if face == "front":
            if x in (0, 7):
                return crimson if y < 11 else crimson_d      # マントのふち
            if y == 8:
                return leather if x not in (3, 4) else "#9A8A6A"
            if (x, y) in ((3, 2), (4, 2), (2, 3), (5, 3), (3, 3), (4, 3), (3, 4), (4, 4)):
                return "#16171A"                             # ワタリガラスの紋
            if y in (1, 5) and x in (1, 6):
                return iron_l                                 # 鋲
            return noisy(r, iron if y < 8 else iron_d, 0.06)
        if face == "back":
            return noisy(r, crimson, 0.05) if y < 11 else crimson_d
        if face == "top":
            return crimson
        if face == "bottom":
            return iron_d
        return crimson if y < 11 else crimson_d
    s.body(body)
    s.body(lambda f, x, y, w, h: (crimson_d if y == 0 else None) if f in ("front", "back") and y <= 0 else None, layer=True)

    def right_arm(face, x, y, w, h):      # 黒鉄の片腕甲
        if face == "top":
            return crimson
        if face == "bottom":
            return iron_d
        if y <= 2:
            return crimson
        if y == 3:
            return iron_l
        return noisy(r, iron if (x + y) % 4 else iron_l, 0.06)
    def left_arm(face, x, y, w, h):
        if face == "top":
            return crimson
        if face == "bottom":
            return skin_d
        if y <= 7:
            return noisy(r, crimson, 0.05)
        if y == 8:
            return crimson_d
        return leather
    s.arm("right", right_arm)
    s.arm("left", left_arm)

    def leg(face, x, y, w, h):
        if face in ("top",):
            return iron_d
        if face == "bottom":
            return "#22160E"
        if y >= 8:
            return "#3B2A1E" if y < 11 else "#22160E"
        return noisy(r, "#2C2C32", 0.05)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_d, "S": skin, "s": skin_d, "W": "#E8E8E8", "E": "#3A3A3A", "X": "#A2604A",
           "M": "#7A4A3A", "G": "#9A9A9A", "b": "#4A4A4A"}
    faces = {
        "": ["H H H H H H H H", "H H H H H H H H", "S S S S S S S S", "S b b S S b b S",
             "S W E S S E W S", "S S S S S S X S", "S S M M M M X S", "S G S G G S G S"],
        "stern": ["H H H H H H H H", "H H H H H H H H", "S b S S S S b S", "S S b S S b S S",
                  "S W E S S E W S", "S S S S S S X S", "S S M M M M X S", "S G S G G S G S"],
    }
    return s, pal, faces


# ================================================================ セレナ
def serena():
    s = Skin(True, 37)
    r = s.rng
    hair, hair_d, hair_l = "#E6DCB8", "#C9BC92", "#F6F0D8"
    skin, skin_d = "#F6E0D0", "#E8C8B4"
    white, white_d = "#F4F6FA", "#D8DDE6"
    blue, blue_d = "#A8D4F0", "#7FB4DA"
    silver = "#C9CED6"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair if (x * y) % 4 else hair_l, 0.03)
        if face == "bottom":
            return skin_d
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair, 0.04)
        if y <= 6 or (face == "right" and x <= 2) or (face == "left" and x >= 5):
            return noisy(r, hair, 0.04)
        return skin_d
    s.head(head)
    s.hat(lambda f, x, y, w, h: (noisy(r, hair_d, 0.03) if f in ("right", "left", "back") and y >= 5 else None))

    def body(face, x, y, w, h):
        if face == "front":
            if y == 0:
                return blue if 2 <= x <= 5 else white
            if 3 <= x <= 4:
                return blue if y % 3 else blue_d
            if (x, y) in ((1, 3), (6, 6), (1, 9), (6, 10)):
                return silver                                  # 星の刺繍
            if (x, y) in ((1, 2), (0, 3), (2, 3), (1, 4), (6, 5), (5, 6), (7, 6), (6, 7)):
                return "#E3E8F0"
            return noisy(r, white, 0.02)
        if face == "back":
            return noisy(r, white if y > 0 else blue, 0.02)
        if face == "top":
            return white
        if face == "bottom":
            return white_d
        return noisy(r, white, 0.02)
    s.body(body)
    s.body(lambda f, x, y, w, h: (noisy(r, hair, 0.04) if f == "back" and y <= 4 and 1 <= x <= 6 else None), layer=True)

    def arm(face, x, y, w, h):
        if face == "top":
            return white
        if face == "bottom":
            return skin_d
        if y >= 10:
            return skin
        if y == 9:
            return blue
        return noisy(r, white, 0.02)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return white_d
        if face == "bottom":
            return silver
        if y == 9:
            return blue
        if y >= 10:
            return silver if y == 10 else "#A9AFB8"
        return noisy(r, white, 0.02)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_l, "d": hair_d, "S": skin, "W": "#FFFFFF", "E": "#6F8FAF", "e": "#4F6F8F", "M": "#D08A84",
           "B": "#F2C4B8", "b": "#B8A878"}
    faces = {
        "": ["H H H H H H H H", "H H H H H H H H", "H h S S S S h H", "H b S S S S b H",
             "H W E S S E W H", "H S S S S S S H", "d S S M M S S d", "d S S S S S S d"],
        "smile": ["H H H H H H H H", "H H H H H H H H", "H h S S S S h H", "H b S S S S b H",
                  "H e e S S e e H", "H B S S S S B H", "d S M M M M S d", "d S S S S S S d"],
    }
    return s, pal, faces


# ================================================================ ドラン
def doran():
    s = Skin(False, 53)
    r = s.rng
    hair, hair_d, braid = "#8E4A2A", "#6A3420", "#B5653A"
    skin, skin_d = "#D9A07A", "#B97E5A"
    shirt, shirt_d = "#B5653A", "#8E4A2A"
    apron, apron_d, scorch = "#6B4A32", "#4E3424", "#2E2018"
    soot = "#6A5A50"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair, 0.08)
        if face == "bottom":
            return noisy(r, hair, 0.08)                         # あごひげの下
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair if y < 5 else skin_d, 0.08)
        if y <= 1 or y >= 5 or (face == "right" and x <= 1) or (face == "left" and x >= 6):
            return noisy(r, hair, 0.08)
        return skin
    s.head(head)

    def body(face, x, y, w, h):
        if face == "front":
            if y <= 1:
                return shirt if not (2 <= x <= 5 and y == 0) else skin
            if x in (0, 7):
                return noisy(r, shirt, 0.05) if y < 8 else shirt_d
            # 革のエプロン（焦げ跡）
            if r.random() < 0.08 or (x, y) in ((2, 5), (5, 8), (4, 10), (2, 9)):
                return scorch
            return noisy(r, apron if y < 10 else apron_d, 0.06)
        if face == "back":
            if 2 <= x <= 5 and y == 6:
                return apron_d                                   # エプロンのひも
            return noisy(r, shirt, 0.05) if y < 9 else shirt_d
        if face == "top":
            return shirt
        if face == "bottom":
            return apron_d
        return noisy(r, shirt, 0.05)
    s.body(body)
    # 上着の層：胸まで垂れた編みひげ
    def beard(face, x, y, w, h):
        if face == "front" and 2 <= x <= 5 and y <= 1:
            return noisy(r, hair, 0.06)
        if face == "front" and 3 <= x <= 4 and y <= 3:
            return braid if y % 2 else hair
        return None
    s.body(beard, layer=True)

    def arm(face, x, y, w, h):
        if face == "top":
            return shirt
        if face == "bottom":
            return skin_d
        if y <= 3:
            return noisy(r, shirt, 0.05) if y < 3 else shirt_d   # まくった袖
        if r.random() < 0.10:
            return soot
        return noisy(r, skin, 0.05)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return apron_d
        if face == "bottom":
            return "#1E1814"
        if y >= 9:
            return "#2E2620"
        return noisy(r, "#4A3A2E", 0.05)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_d, "S": skin, "s": skin_d, "W": "#F0E8E0", "E": "#2E2A26", "B": braid, "M": "#7A3A2A",
           "T": "#FFFFFF", "b": "#5A2A18"}
    faces = {
        "": ["H H H H H H H H", "H H H H H H H H", "H b b S S b b H", "S S S S S S S S",
             "S W E S S E W S", "H S S s s S S H", "H H M M M M H H", "H H B H H B H H"],
        "grin": ["H H H H H H H H", "H H H H H H H H", "H b b S S b b H", "S S S S S S S S",
                 "S E E S S E E S", "H S S s s S S H", "H M T T T T M H", "H H B H H B H H"],
    }
    return s, pal, faces


# ================================================================ バルド
def bard():
    s = Skin(False, 71)
    r = s.rng
    hair, hair_d = "#6E6458", "#504840"
    skin, skin_d = "#C99272", "#A87454"
    tabard, tabard_d = "#7A2A2E", "#5A1C20"
    mail, mail_d = "#8A8F96", "#5E636A"
    leather = "#4A3426"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair, 0.08)
        if face == "bottom":
            return skin_d
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair, 0.08) if y < 6 else skin_d
        if y <= 2 or (face == "right" and x <= 1 and y <= 4) or (face == "left" and x >= 6 and y <= 4):
            return noisy(r, hair, 0.08)
        return skin if y < 7 else skin_d
    s.head(head)

    def body(face, x, y, w, h):
        if face == "front":
            if y == 0:
                return mail_d
            if x in (0, 7):
                return noisy(r, mail if (x + y) % 2 else mail_d, 0.05)
            if y == 8:
                return leather if x not in (3, 4) else "#A08A5A"
            if (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (2, 4), (5, 4)):
                return "#C9B27A"                                  # 色あせた騎士団の紋
            return noisy(r, tabard if y < 8 else tabard_d, 0.06)
        if face == "back":
            return noisy(r, tabard, 0.06) if 1 <= x <= 6 else mail
        if face == "top":
            return mail_d
        if face == "bottom":
            return tabard_d
        return noisy(r, mail if (x + y) % 2 else mail_d, 0.05)
    s.body(body)

    def arm(face, x, y, w, h):
        if face == "top":
            return mail_d
        if face == "bottom":
            return leather
        if y >= 9:
            return leather
        return noisy(r, mail if (x + y) % 2 else mail_d, 0.05)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return tabard_d
        if face == "bottom":
            return "#22160E"
        if y >= 9:
            return "#3B2A1E"
        if y <= 2:
            return tabard_d
        return noisy(r, "#6E737A" if (x + y) % 3 else "#4E535A", 0.05)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_d, "S": skin, "s": skin_d, "W": "#E0D8D0", "E": "#3A342E", "X": "#9A5E48",
           "M": "#7A4A3A", "G": "#8A8278", "b": "#3E3830"}
    faces = {
        "": ["H H H H H H H H", "H H H H H H H H", "H H S S S S H H", "S b b S S b b S",
             "S W E S S E W S", "S S X X s S S S", "S S S M M S S S", "S G S G G S G S"],
    }
    return s, pal, faces


NPCS = {"lilia": lilia, "gard": gard, "serena": serena, "doran": doran, "bard": bard}


def build(root):
    written = []
    for name, maker in NPCS.items():
        skin, pal, faces = maker()
        for expression, rows in faces.items():
            skin.face(rows, pal)
            path = os.path.join(root, "textures", "entity", "npc", name + ("_" + expression if expression else "") + ".png")
            skin.save(path)
            written.append(path)
        # 最後に塗った顔のまま残らないよう、ふつうの顔に戻しておく（次の表情の土台にしない）
    return written


def preview(paths, out):
    """正面から見た形（頭・体・腕・足の正面を並べる）を、8倍で並べた絵"""
    cards = []
    for p in paths:
        img = Image.open(p)
        slim = "lilia" in p or "serena" in p
        aw = 3 if slim else 4
        card = Image.new("RGBA", (16, 34), (0, 0, 0, 0))
        card.paste(img.crop((8, 8, 16, 16)), (4, 0))
        hat = img.crop((40, 8, 48, 16))
        card.alpha_composite(hat, (4, 0))
        card.paste(img.crop((20, 20, 28, 32)), (4, 8))
        card.alpha_composite(img.crop((20, 36, 28, 48)), (4, 8))
        card.paste(img.crop((44, 20, 44 + aw, 32)), (4 - aw, 8))
        card.paste(img.crop((36, 52, 36 + aw, 64)), (12, 8))
        card.paste(img.crop((4, 20, 8, 32)), (4, 20))
        card.paste(img.crop((20, 52, 24, 64)), (8, 20))
        cards.append(card.resize((16 * 8, 34 * 8), Image.NEAREST))
        # 後ろ
        back = Image.new("RGBA", (16, 34), (0, 0, 0, 0))
        back.paste(img.crop((24, 8, 32, 16)), (4, 0))
        back.alpha_composite(img.crop((56, 8, 64, 16)), (4, 0))
        back.paste(img.crop((32, 20, 40, 32)), (4, 8))
        back.alpha_composite(img.crop((32, 36, 40, 48)), (4, 8))
        cards.append(back.resize((16 * 8, 34 * 8), Image.NEAREST))
    sheet = Image.new("RGBA", (len(cards) * 136, 290), (70, 80, 90, 255))
    for i, c in enumerate(cards):
        sheet.alpha_composite(c, (i * 136 + 4, 9))
    sheet.save(out)


if __name__ == "__main__":
    paths = build(ROOT)
    if "--preview" in sys.argv:
        preview(paths, sys.argv[sys.argv.index("--preview") + 1])
    print("\n".join(paths))
