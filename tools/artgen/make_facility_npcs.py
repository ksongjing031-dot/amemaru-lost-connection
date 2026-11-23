"""Phase 7 の施設の担当NPCの見た目（プレイヤーと同じ形の 64×64 のスキン）を作る。

11月23日に決定：重要なNPCでも、見た目はそれほどこだわらなくてよい。立体的なモデルでなく、人の形のスキンと顔の絵で見分けられれば
十分（作り込みは会話・物語・仕組みを優先する）。形の作り方は tools/artgen/make_npc_skins.py の Skin を使う。

使い方: python3 tools/artgen/make_facility_npcs.py [--preview 出力.png]

作るもの：textures/entity/npc/<名前>.png と、表情の差分 <名前>_<表情>.png（顔の正面だけが違う）
  pokka（ポッカ爺。釣り堀の主人。Phase 0-A 8章：70代、陽気な釣り好き。【補完】白い髪とふさふさの白いひげ、日に焼けた肌、
         麦わら帽子、釣り堀の色（青緑と白）のシャツに、ポケットの多い生成りの釣りベスト、まくった袖、深緑のゴム長靴）
  gisele（ジゼル・ノワール。カジノの支配人。Phase 0-A 8章：年齢不詳。洒落た女性。【補完】長い黒髪、右に寄せた小さな紫の帽子と
          金の帯と黒い羽根、カジノの色（紫・金・黒。docs/map_style.md）のドレス、黒い長い手袋、金の耳飾りと首飾り、赤い口紅）
  petra（ペトラ・ポム。料理人。Phase 0-A 8章：20代。マルタの娘。魚料理が得意。【補完】赤茶の髪をうしろで結び、白い料理人の帽子、
         料理屋の色（橙。tools/mapgen/build_market.py）の前かけ、まくった白い袖、緑の目とそばかす）
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
    """支配人ジゼル・ノワール（11月23日の直し：大人びた、きちんとした支配人の姿に）。
    黒い仕立ての上着に、紫の胴着と白い襟飾り、金の飾りボタン。黒髪は高くまとめて金の櫛で留める。"""
    s = Skin(True, 113)
    r = s.rng
    hair, hair_d, sheen, hair_l = "#231A2E", "#150F1C", "#4B3466", "#6A4C8C"
    skin, skin_s, skin_d = "#F2D5C4", "#E2BBA8", "#C99A86"
    coat, coat_l, coat_d = "#1F1A24", "#34293E", "#120E16"
    plum, plum_l, plum_d = "#5B2A73", "#7C3E99", "#3F1C52"
    gold, gold_l, gold_d = "#E3BD5A", "#F6DE8E", "#A9873A"
    lace, lace_d = "#F2EEF0", "#C9C2CC"
    gem, gem_l = "#9C4DCC", "#D9A6F2"
    skirt, skirt_l, skirt_d = "#4A1F5E", "#6A2E85", "#321440"
    glove, glove_l = "#16121A", "#2E2636"
    shoe = "#141018"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, sheen if (x + y) % 5 == 0 else hair, 0.04)
        if face == "bottom":
            return noisy(r, hair_d, 0.04)
        if face == "front":
            return skin
        if face == "back":
            if y == 7:
                return noisy(r, hair_d, 0.04)                      # 結い上げた髪の生えぎわ
            return noisy(r, sheen if (x * 3 + y) % 7 == 0 else hair, 0.04)
        # 横：耳の上まで髪。耳もとに金の小さな耳飾り
        if y <= 3 or (face == "right" and x <= 1) or (face == "left" and x >= 6):
            return noisy(r, sheen if (x + y) % 6 == 0 else hair, 0.04)
        if y == 5 and ((face == "right" and x == 3) or (face == "left" and x == 4)):
            return gold
        return skin_s if y == 7 else skin
    s.head(head)

    def hat(face, x, y, w, h):
        # 高くまとめた髪（頭のうしろの上に髷）と、金の櫛。前は流した前髪の厚み
        if face == "top":
            if 2 <= x <= 5 and y >= 4:
                return noisy(r, hair_l if (x + y) % 3 == 0 else sheen, 0.04)   # 髷の上の照り
            return None
        if face == "bottom":
            return None
        if face == "back":
            if 2 <= x <= 5 and 1 <= y <= 4:
                if y == 2 and x in (2, 5):
                    return gold_l                                  # 櫛の両端
                if y == 2:
                    return gold                                    # 金の櫛
                return noisy(r, sheen if (x + y) % 2 else hair, 0.04)
            return None
        if face == "front":
            if y == 1 and 1 <= x <= 5:
                return noisy(r, sheen if x % 2 else hair, 0.04)    # 前髪の厚み
            if y == 2 and 4 <= x <= 6:
                return noisy(r, hair, 0.04)                        # 右へ流した前髪
            return None
        if face in ("right", "left") and y <= 2 and (x >= 4 if face == "right" else x <= 3):
            return noisy(r, hair, 0.04)                            # 耳の上へ流した髪
        return None
    s.hat(hat)

    def body(face, x, y, w, h):
        if face == "front":
            if y == 0:
                return lace if 3 <= x <= 4 else (coat_l if x in (1, 6) else coat)      # 襟もと
            if 1 <= y <= 3 and 3 <= x <= 4:
                if y == 2 and x == 3:
                    return gem                                     # 紫の石のブローチ
                if y == 2 and x == 4:
                    return gem_l
                return lace if (x + y) % 2 else lace_d            # 白い襟飾り（ひだ）
            if 1 <= y <= 3 and x in (2, 5):
                return plum_l                                      # 胴着の縁
            if 4 <= y <= 7 and 2 <= x <= 5:
                if x in (2, 5) and y in (4, 6):
                    return gold                                    # 二列の金ボタン
                return noisy(r, plum if x in (3, 4) else plum_d, 0.03)   # 紫の胴着
            if y == 8:
                return gold_d if 2 <= x <= 5 else gold             # 腰の金の飾り帯
            if y >= 9 and 2 <= x <= 5:
                return noisy(r, skirt_l if x in (3, 4) and y == 9 else skirt, 0.03)  # 上着の合わせから見えるスカート
            # 黒い上着（左右）。肩と縁に明るい面
            if x in (1, 6) and y <= 7:
                return coat_l
            if y == 11:
                return gold_d                                      # 上着のすその金の縁
            return noisy(r, coat, 0.03)
        if face == "back":
            if y == 8:
                return gold
            if y >= 9:
                return noisy(r, coat_d if x in (3, 4) else coat, 0.03)   # 上着のすその切れ込み
            if x in (3, 4) and 2 <= y <= 7:
                return coat_l                                      # 背中の縫い目
            return noisy(r, coat, 0.03)
        if face == "top":
            return coat_l if x in (0, 7) else coat
        if face == "bottom":
            return skirt_d
        if y == 8:
            return gold
        return noisy(r, coat_l if y <= 1 else coat, 0.03)
    s.body(body)

    def arm(face, x, y, w, h):
        if face == "top":
            return gold if y in (0, 3) else coat_l                  # 肩の金の飾り縁
        if face == "bottom":
            return glove
        if y == 0:
            return gold
        if y <= 5:
            return noisy(r, coat_l if (face == "front" and x == 0) else coat, 0.03)
        if y == 6:
            return plum_l                                          # 袖口の紫
        if y == 7:
            return gold                                            # 袖口の金
        return glove_l if (face == "front" and x == 1) else glove  # 黒い長い手袋（照り）
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return skirt_d
        if face == "bottom":
            return shoe
        if y >= 10:
            if y == 10 and face == "front" and x == 1:
                return gold                                        # 靴の金の留め金
            return shoe
        if y == 9:
            return gold_d                                          # すその金の縁
        # 長いスカート：たてのひだ（明るい・暗いの縞）
        if face == "front":
            return noisy(r, skirt_l if x == 1 else (skirt_d if x == 3 else skirt), 0.03)
        return noisy(r, skirt_d if x % 2 else skirt, 0.03)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": sheen, "S": skin, "s": skin_s, "B": "#5A3A50", "L": "#1A1020", "E": "#6B3FA0",
           "M": "#A8283C", "m": "#7A1A2C", "D": "#4A3040"}
    faces = {
        # 細い眉、切れ長の目（濃いまつげと紫の瞳）、鼻すじの影、閉じた赤い唇、左の目もとのほくろ
        "": ["H H h H H H H H", "H h H H H h H H", "H S S S S S S H", "S B B S S B B S",
             "S L E S S E L S", "S s S S s S D S", "S S S M M S S S", "S S S S S S S S"],
        # 目を細めた笑い（口もとだけ品よくほころぶ）
        "laugh": ["H H h H H H H H", "H h H H H h H H", "H S S S S S S H", "S B B S S B B S",
                  "S L L S S L L S", "S s S S s S D S", "S S m M M m S S", "S S S S S S S S"],
    }
    return s, pal, faces


def petra():
    s = Skin(True, 131)
    r = s.rng
    hair, hair_d = "#B8673A", "#8E4A28"
    skin, skin_d = "#F2CFB4", "#DDB096"
    cap, cap_d = "#F6F4EE", "#D9D5CA"
    shirt, shirt_d = "#F0E6D2", "#D6C9AE"
    apron, apron_d, apron_l = "#E2822E", "#B9661F", "#F2A24E"
    skirt, skirt_d = "#5B3A2A", "#3F281C"
    boot = "#4A3324"

    def head(face, x, y, w, h):
        if face == "top":
            return noisy(r, hair, 0.05)
        if face == "bottom":
            return noisy(r, hair_d, 0.05)
        if face == "front":
            return skin
        if face == "back":
            return noisy(r, hair if y < 7 else hair_d, 0.05)       # うしろで結んだ髪
        if y <= 2 or (face == "right" and x <= 1) or (face == "left" and x >= 6):
            return noisy(r, hair, 0.05)
        return skin
    s.head(head)

    def hat(face, x, y, w, h):
        # 白い料理人の帽子（低い形）と、うしろで結んだ髪の房
        if face == "top":
            return noisy(r, cap, 0.03)
        if face == "bottom":
            return None
        if y <= 1:
            return noisy(r, cap if y == 0 else cap_d, 0.03)
        if face == "back" and 2 <= y <= 6 and 3 <= x <= 4:
            return noisy(r, hair_d, 0.05)                          # 髪の房
        return None
    s.hat(hat)

    def body(face, x, y, w, h):
        if face == "front":
            if y <= 1 and 2 <= x <= 5:
                return skin if y == 0 else shirt
            if 1 <= x <= 6 and y >= 2:
                if y == 5:
                    return apron_d                                 # 前かけのひも
                if y in (8, 9) and x in (2, 5):
                    return apron_l                                 # ポケット
                return noisy(r, apron, 0.04)                       # 橙の前かけ（料理屋の色）
            return noisy(r, shirt, 0.04)
        if face == "back":
            if y == 5:
                return apron_d
            return noisy(r, shirt if y < 10 else shirt_d, 0.04)
        if face in ("top", "bottom"):
            return shirt_d
        return noisy(r, shirt, 0.04) if y != 5 else apron_d
    s.body(body)

    def arm(face, x, y, w, h):
        if face == "top":
            return shirt
        if face == "bottom":
            return skin_d
        if y <= 4:
            return noisy(r, shirt, 0.04)                           # まくった白い袖
        if y == 5:
            return shirt_d
        return noisy(r, skin, 0.04)
    s.arm("right", arm)
    s.arm("left", arm)

    def leg(face, x, y, w, h):
        if face == "top":
            return skirt_d
        if face == "bottom":
            return boot
        if y <= 6:
            return noisy(r, skirt if (x + y) % 4 else skirt_d, 0.04)
        if y >= 10:
            return boot
        return noisy(r, "#3A2A20", 0.04)
    s.leg("right", leg)
    s.leg("left", leg)
    pal = {"H": hair, "h": hair_d, "S": skin, "s": skin_d, "W": "#FFFFFF", "E": "#3C5A2A", "L": "#3A2418", "M": "#C8504A",
           "T": "#FFFFFF", "P": "#F0A8A0", "F": "#D8A070"}
    faces = {
        # 明るい緑の目、そばかす、にこやかな口
        "": ["H H H H H H H H", "H h S S S S h H", "S L L S S L L S", "S W E S S E W S",
             "S F S S S S F S", "S P S s s S P S", "S S S M M S S S", "S S S S S S S S"],
        "laugh": ["H H H H H H H H", "H h S S S S h H", "S S S S S S S S", "S L L S S L L S",
                  "S F S S S S F S", "S P S s s S P S", "S S M T T M S S", "S S S M M S S S"],
    }
    return s, pal, faces


NPCS = {"pokka": pokka, "gisele": gisele, "petra": petra}


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
