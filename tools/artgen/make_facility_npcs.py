"""Phase 7 の施設の担当NPCの見た目（プレイヤーと同じ形の 64×64 のスキン）を作る。

11月23日に決定：重要なNPCでも、見た目はそれほどこだわらなくてよい。立体的なモデルでなく、人の形のスキンと顔の絵で見分けられれば
十分（作り込みは会話・物語・仕組みを優先する）。形の作り方は tools/artgen/make_npc_skins.py の Skin を使う。

使い方: python3 tools/artgen/make_facility_npcs.py [--preview 出力.png]

作るもの：textures/entity/npc/<名前>.png と、表情の差分 <名前>_<表情>.png（顔の正面だけが違う）
  pokka（ポッカ爺。釣り堀の主人。Phase 0-A 8章：70代、陽気な釣り好き。【補完】白い髪とふさふさの白いひげ、日に焼けた肌、
         麦わら帽子、釣り堀の色（青緑と白）のシャツに、ポケットの多い生成りの釣りベスト、まくった袖、深緑のゴム長靴）
  gisele（ジゼル・ノワール。カジノの支配人。Phase 0-A 8章：年齢不詳。洒落た女性。【補完】長い黒髪、右に寄せた小さな紫の帽子と
          金の帯と黒い羽根、カジノの色（紫・金・黒。docs/map_style.md）のドレス、黒い長い手袋、金の耳飾りと首飾り、赤い口紅）
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_npc_skins import Skin, noisy, preview  # noqa: E402

ROOT = "src/main/resources/assets/amemaru"


def pokka():
    s = Skin(False, 97)
    r = s.rng
    hair, hair_d = "#EEEDE6", "#C9C7BC"
    skin, skin_d = "#C98E62", "#A8714C"
    straw, straw_d, band = "#E2C576", "#B8994E", "#2E8C82"
    shirt, shirt_l = "#3FA39A", "#E8F2F0"
    vest, vest_d, pocket = "#C9B98E", "#A6966E", "#B3A37A"
    pants, pants_d = "#6B5A44", "#4E412F"
    boot, boot_d = "#2F5A3A", "#1F3E28"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair, 0.05)
        if face == "bottom":
            return noisy(r, hair_d, 0.05)                        # ひげの下
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair if y < 6 else skin_d, 0.05)
        # 横：耳のまわりまで白い髪、下はひげ
        if y <= 1 or y >= 6 or (face == "right" and x <= 1) or (face == "left" and x >= 6):
            return noisy(r, hair, 0.05)
        return skin
    s.head(head)

    def hat(face, x, y, w, h):
        # 麦わら帽子：てっぺんと、まわりの上の2段。下から3段目に青緑の帯
        if face == "top":
            return noisy(r, straw, 0.08) if (x + y) % 3 else noisy(r, straw_d, 0.06)
        if face == "bottom":
            return None
        if y <= 1:
            return noisy(r, straw, 0.08) if (x + y) % 3 else noisy(r, straw_d, 0.06)
        if y == 2:
            return band
        return None
    s.hat(hat)

    def body(face, x, y, w, h):
        if face == "front":
            if y <= 1 and 2 <= x <= 5:
                return shirt_l if y == 0 else shirt                # えりもと
            # ベストの前（真ん中はシャツが見える）
            if 3 <= x <= 4:
                return shirt if (y // 2) % 2 else shirt_l
            if (x in (1, 6)) and y in (4, 5, 8, 9):
                return pocket                                    # ポケット
            if (x in (1, 6)) and y in (3, 7):
                return vest_d
            return noisy(r, vest if y < 10 else vest_d, 0.05)
        if face == "back":
            if y >= 10:
                return vest_d
            return noisy(r, vest, 0.05)
        if face == "top":
            return vest
        if face == "bottom":
            return vest_d
        return noisy(r, vest, 0.05)
    s.body(body)
    # 上着の層：胸まで垂れた白いひげ
    def beard(face, x, y, w, h):
        if face == "front" and 2 <= x <= 5 and y <= 1:
            return noisy(r, hair, 0.04)
        if face == "front" and 3 <= x <= 4 and y == 2:
            return noisy(r, hair_d, 0.04)
        return None
    s.body(beard, layer=True)

    def arm(face, x, y, w, h):
        if face == "top":
            return shirt
        if face == "bottom":
            return skin_d
        if y <= 4:
            # 青緑と白のチェックのシャツ（まくった袖）
            return shirt if (x + y) % 2 else shirt_l
        if y == 5:
            return shirt_l
        return noisy(r, skin, 0.05)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return pants_d
        if face == "bottom":
            return boot_d
        if y >= 7:
            return noisy(r, boot if y < 11 else boot_d, 0.04)     # ゴム長靴
        return noisy(r, pants, 0.05)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_d, "S": skin, "s": skin_d, "W": "#F4F2EA", "E": "#2A2622", "M": "#B0574A", "T": "#FFFFFF",
           "B": "#9AA0A0", "k": "#7E5A3E"}
    faces = {
        # 太い白い眉、目じりのしわ、ふさふさの口ひげと、白いあごひげ
        "": ["H H H H H H H H", "H h H S S H h H", "H H H S S H H H", "S k S S S S k S",
             "S E W S S W E S", "S S S s s S S S", "H H W W W W H H", "H H H H H H H H"],
        "laugh": ["H H H H H H H H", "H h H S S H h H", "H H H S S H H H", "S k S S S S k S",
                  "S E E S S E E S", "S S S s s S S S", "H W M T T M W H", "H H H H H H H H"],
    }
    return s, pal, faces


def gisele():
    s = Skin(True, 113)
    r = s.rng
    hair, hair_d, sheen = "#1E1726", "#120D18", "#4A2E66"
    skin, skin_d = "#F0D2C0", "#D9B4A0"
    dress, dress_d, dress_l = "#5A2D82", "#3E1D5C", "#7F3FB2"
    gold, gold_d = "#E8C25A", "#B8923A"
    glove = "#1A1620"
    stock, shoe = "#2A2230", "#141018"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair if (x + y) % 4 else sheen, 0.05)
        if face == "bottom":
            return noisy(r, hair_d, 0.05)
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair if (x + y) % 5 else sheen, 0.05)   # 長い黒髪（背中へ）
        # 横：耳の前まで髪、耳に金の耳飾り
        if y <= 2 or (face == "right" and x <= 2) or (face == "left" and x >= 5):
            return noisy(r, hair, 0.05)
        if y == 5 and ((face == "right" and x == 4) or (face == "left" and x == 3)):
            return gold
        return skin
    s.head(head)

    def hat(face, x, y, w, h):
        # 小さな紫の帽子（右に寄せてかぶる）に金の帯と、黒い羽根
        if face == "top":
            return noisy(r, dress, 0.05) if 1 <= x <= 5 and 1 <= y <= 5 else None
        if face == "bottom":
            return None
        if face == "front" and y == 0 and 1 <= x <= 5:
            return dress_l
        if face == "front" and y == 1 and 1 <= x <= 5:
            return gold
        if face == "right" and y <= 1:
            return dress if y == 0 else gold
        if face == "left" and y <= 2 and x >= 4:
            return "#101014" if y < 2 else gold                    # 羽根
        if face == "back" and y >= 1:
            return noisy(r, hair, 0.05) if y >= 2 else None        # 背中へ垂れる髪
        return None
    s.hat(hat)

    def body(face, x, y, w, h):
        if face == "front":
            if y == 0 and 2 <= x <= 5:
                return skin                                       # 首もと
            if y == 1 and 3 <= x <= 4:
                return gold                                       # 金の首飾り
            if y == 1:
                return skin
            if y == 2 and 1 <= x <= 6:
                return dress_l                                    # 胸の縁
            if 6 <= y <= 7:
                return gold if y == 6 else dress_d                # 腰の金の帯
            return noisy(r, dress if y < 10 else dress_d, 0.04)
        if face == "back":
            if y <= 6:
                return noisy(r, hair if (x + y) % 4 else sheen, 0.05)   # 背中の長い髪
            return noisy(r, dress, 0.04) if y != 7 else gold
        if face in ("top", "bottom"):
            return dress_d
        return noisy(r, dress, 0.04) if y != 6 else gold
    s.body(body)

    def arm(face, x, y, w, h):
        if face == "top":
            return dress
        if face == "bottom":
            return glove
        if y <= 3:
            return noisy(r, dress, 0.04)                          # 紫の袖
        if y == 4:
            return gold                                           # 袖口の金
        return glove                                              # 黒い長い手袋
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return dress_d
        if face == "bottom":
            return shoe
        if y <= 6:
            return noisy(r, dress_d if (x + y) % 3 else dress, 0.04)   # 長いドレスのすそ
        if y == 7:
            return gold
        if y >= 10:
            return shoe
        return stock
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": sheen, "S": skin, "s": skin_d, "W": "#FFFFFF", "E": "#3A1E52", "L": "#1A1018", "M": "#B8304A",
           "m": "#8E2038", "T": "#FFFFFF", "P": "#E8A0B0", "D": "#4A3040"}
    faces = {
        # 切れ長の目と長いまつげ、紫の瞳、赤い口紅、左の目もとのほくろ
        "": ["H H h H H H H H", "H H H H H H S H", "S L L S S L L S", "S W E S S E W S",
             "S S S S S S D S", "S P S s s S P S", "S S S M M S S S", "S S S S S S S S"],
        "laugh": ["H H h H H H H H", "H H H H H H S H", "S S S S S S S S", "S L L S S L L S",
                  "S S S S S S D S", "S P S s s S P S", "S S M T T M S S", "S S S m m S S S"],
    }
    return s, pal, faces


NPCS = {"pokka": pokka, "gisele": gisele}


def build(root):
    written = []
    for name, maker in NPCS.items():
        skin, pal, faces = maker()
        for expression, rows in faces.items():
            skin.face(rows, pal)
            path = os.path.join(root, "textures", "entity", "npc", name + ("_" + expression if expression else "") + ".png")
            skin.save(path)
            written.append(path)
    return written


if __name__ == "__main__":
    paths = build(ROOT)
    if "--preview" in sys.argv:
        preview(paths, sys.argv[sys.argv.index("--preview") + 1])
    print("\n".join(paths))
