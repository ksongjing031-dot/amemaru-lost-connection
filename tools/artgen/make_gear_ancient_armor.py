"""古代石シリーズの防具（GeckoLib）：重鎧（ナイト：兜・胴鎧・脚甲・鉄靴）と法衣（ウィザード：頭巾・法衣・下衣・旅靴）。

  python3 tools/artgen/make_gear_ancient_armor.py <リポジトリ> [確認画像の出力先]

座標は Bedrock（体の右が −x、表が −z）。ボーンの名前と位置は GeckoLib の防具の決まり（armorHead など）。
1つのシリーズ＝1つのモデル。着た場所のボーンだけが表示される（兜なら armorHead）。
仕様書：docs/gear/ancient_stone_armor.md
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from materials import Painter, RAMPS, pick  # noqa: E402

ARMOR_BONES = (("armorHead", (0, 24, 0)), ("armorBody", (0, 24, 0)), ("armorRightArm", (-5, 22, 0)),
               ("armorLeftArm", (5, 22, 0)), ("armorRightLeg", (-1.9, 12, 0)), ("armorLeftLeg", (1.9, 12, 0)),
               ("armorRightBoot", (-1.9, 12, 0)), ("armorLeftBoot", (1.9, 12, 0)))
H, B, RA, LA, RL, LL, RB, LB = (b for b, _ in ARMOR_BONES)

PIECE_BONES = {"head": (H,), "chest": (B, RA, LA), "legs": (RL, LL), "feet": (RB, LB)}

GOLD = (196, 170, 104)
GOLD_LIGHT = (234, 214, 150)
MOSS_COL = (75, 97, 64)


def new_armor(name):
    g = GeoModel(name, 128, 64)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    return g


def pair(g, right_bone, left_bone, name, origin, size, mat, **kw):
    """右の部品を置き、左に写す（名前_r・名前_l）。
    足と靴は、まん中で左右が重なるので、左をほんの少し大きくしてちらつきを防ぐ。"""
    c = g.cube(right_bone, name + "_r", origin, size, mat, **kw)
    extra = 0.03 if right_bone in (RL, RB) else 0.0
    g.mirror_cube(c, left_bone, name + "_l", extra_inflate=extra)
    return c


# ---------------------------------------------------------------- 重鎧（ナイト）

def build_plate():
    g = new_armor("ancient_stone_plate")
    # 兜：苔鉄の鉢・段になった石の稜線（後ろへ回って首まで）・額の板（文字の溝と結晶）
    #     ・横一本の覗き穴（額の板と顔の板のすき間）・息の穴の並ぶ顔の板・頬当て・首当て
    g.cube(H, "helm_shell", (-4, 24, -4), (8, 8, 8), "iron", inflate=1.0, tags={"rivets_side": True})
    g.cube(H, "crest_base", (-1.5, 33, -5), (3, 1, 10), "stone")
    g.cube(H, "crest_top", (-1, 34, -4), (2, 1, 8), "stone")
    g.cube(H, "crest_back", (-1.5, 26, 5), (3, 7, 1), "stone", tags={"grooves": [30.0]})
    g.cube(H, "brow", (-5, 28, -5.5), (10, 3, 1), "stone", inflate=0.25, tags={"grooves": [29.5]})
    g.cube(H, "brow_shard", (-0.5, 29, -6.25), (1, 1, 1), "crystal_dim")
    g.cube(H, "faceplate", (-5, 23.5, -5.5), (10, 3, 1), "stone", inflate=0.25, tags={"breaths": 25.0})
    pair(g, H, H, "cheek", (-5.75, 23.5, -5), (1, 5, 7), "stone")
    g.cube(H, "neckguard", (-5, 22.5, 4.5), (10, 3, 1), "stone", inflate=0.25, tags={"grooves": [24.0]})
    # 胴：苔鉄の枠・段になった石の胸板（上は広く、下は細い。逆三角の形）・結晶をはめた石の円盤
    #     ・腹の帯・革の帯と苔鉄の留め金・腰の垂れ・段になった背板・背の帯
    g.cube(B, "body_shell", (-4, 12, -2), (8, 12, 4), "iron", inflate=1.01)
    g.cube(B, "chest_upper", (-4, 19, -3.75), (8, 5, 1), "stone", tags={"grooves": [22.0]})
    g.cube(B, "chest_lower", (-3, 15, -3.75), (6, 4, 1), "stone")
    g.cube(B, "medallion", (-1.5, 17.5, -4.5), (3, 3, 1), "stone")
    g.cube(B, "medallion_shard", (-0.5, 18, -5), (1, 2, 1), "crystal_dim")
    g.cube(B, "fauld", (-4.5, 13, -3.5), (9, 2, 1), "stone")
    g.cube(B, "belt", (-5, 11, -3.5), (10, 2, 7), "leather_dark", inflate=0.25)
    g.cube(B, "buckle", (-1, 11, -4.25), (2, 2, 1), "iron", tags={"buckle": True})
    pair(g, B, B, "tasset", (-5, 8, -3.5), (4, 3, 1), "stone")
    g.cube(B, "back_upper", (-4, 18, 2.75), (8, 6, 1), "stone", tags={"grooves": [21.0]})
    g.cube(B, "back_lower", (-3, 14, 2.75), (6, 4, 1), "stone", tags={"grooves": [16.0]})
    g.cube(B, "spine", (-0.5, 12, 3.75), (1, 12, 1), "iron", tags={"rivets": [(0.0, 15.5), (0.0, 21.5)]})
    # 腕：苔鉄の籠手・2枚重ねの石の肩当て（外へ下がる）・その下の毛のふさ・肘当て・手首の石の輪
    pair(g, RA, LA, "arm_shell", (-8, 12, -2), (4, 12, 4), "iron", inflate=0.5)
    pair(g, RA, LA, "pauldron_top", (-9.5, 23, -3), (6, 2, 6), "stone", rot=(0, 0, -22.5), pivot=(-5, 24, 0))
    pair(g, RA, LA, "pauldron_low", (-9.75, 21, -3), (5, 2, 6), "stone", rot=(0, 0, -22.5), pivot=(-5, 22, 0))
    pair(g, RA, LA, "fringe", (-9, 18.5, -3), (6, 1, 6), "wool")
    pair(g, RA, LA, "elbow", (-9.25, 15, -1.5), (1, 3, 3), "stone")
    pair(g, RA, LA, "cuff", (-9, 12, -3), (6, 2, 6), "stone")
    # 脚甲：苔鉄の腿・石の腿当て（左右は離す）・前に出る膝当てと外の小さなひれ・ふくらはぎの石
    pair(g, RL, LL, "leg_shell", (-3.9, 4, -2), (4, 8, 4), "iron", inflate=0.5)
    pair(g, RL, LL, "cuisse", (-3.4, 7.5, -3), (3, 4, 1), "stone", tags={"grooves": [9.5]})
    pair(g, RL, LL, "knee", (-3.4, 5, -3.5), (3, 3, 2), "stone")
    pair(g, RL, LL, "knee_fin", (-5, 5.5, -2.5), (1, 2, 2), "stone")
    pair(g, RL, LL, "calf", (-3.4, 5, 2), (3, 5, 1), "stone")
    # 鉄靴：苔鉄の靴・段々のつま先（道標の段）・かかとの石・足首の石の輪
    pair(g, RB, LB, "boot_shell", (-3.9, 0, -2), (4, 4, 4), "iron", inflate=1.0)
    pair(g, RB, LB, "toe_cap", (-3.4, -1, -4), (3, 2, 1), "stone")
    pair(g, RB, LB, "toe_step", (-2.9, -1, -5), (2, 1, 1), "stone")
    pair(g, RB, LB, "heel", (-3.4, -1, 3), (3, 2, 1), "stone")
    pair(g, RB, LB, "ankle", (-5.4, 4, -3.5), (7, 1, 7), "stone")
    return g


# ---------------------------------------------------------------- 法衣（ウィザード）

def build_robe():
    g = new_armor("ancient_stone_robe")
    # 頭巾：顔の前は開ける（横・後ろ・上の布）。顔のまわりのふちは生成りの裏地。
    #       てっぺんは段になってとがる（道標の段と同じ形）・あごの横の石の玉の重り
    g.cube(H, "hood_top", (-5, 32, -5), (10, 1, 10), "wool_moss")
    pair(g, H, H, "hood_side", (-5, 23, -5), (1, 9, 10), "wool_moss", tags={"lining_front": True})
    g.cube(H, "hood_back", (-4, 23, 4), (8, 9, 1), "wool_moss")
    g.cube(H, "hood_brim", (-4, 31, -5), (8, 1, 1), "wool_moss", tags={"lining_front": True, "lining_under": True})
    g.cube(H, "hood_peak", (-2, 33, -1), (4, 1, 6), "wool_moss")
    g.cube(H, "hood_peak_top", (-1, 34, 1.5), (2, 1, 3), "wool_moss")
    pair(g, H, H, "hood_bead", (-5.25, 22, -4.75), (1, 1, 1), "stone")
    # 法衣：胴・前の合わせ（生成りの裏地）・石のトグル2つ・2段の肩のケープ（前のすそに金の糸の星）と石の留め具
    #       ・帯・腰の横に下げた「星灯のランタン」・背中に流派の印（金の糸のランタン）
    g.cube(B, "robe_body", (-4, 12, -2), (8, 12, 4), "wool_moss", inflate=0.75, tags={"emblem_back": True})
    g.cube(B, "opening", (-0.5, 12, -3), (1, 7, 1), "wool")
    for k, y in enumerate((17, 15)):
        g.cube(B, f"toggle_{k}", (-1.5, y, -3.25), (3, 1, 1), "stone")
    g.cube(B, "capelet", (-6, 19, -3.5), (12, 4, 7), "wool_moss", tags={"trim_bottom": True, "stars": True})
    g.cube(B, "capelet_top", (-5.5, 22, -4), (11, 2, 8), "wool_moss", tags={"trim_bottom": True})
    g.cube(B, "clasp", (-3.5, 22, -4.5), (2, 2, 1), "stone")  # 留め具は右の肩寄り（首飾りの玉とぶつからない）
    g.cube(B, "belt", (-5, 11.75, -3.5), (10, 2, 7), "leather_dark")
    g.cube(B, "lantern_hook", (-6.25, 9.75, -0.5), (1, 2, 1), "iron", inflate=-0.2)
    g.cube(B, "lantern_cap", (-6.75, 8.75, -1), (2, 1, 2), "iron")
    g.cube(B, "lantern_glass", (-6.75, 6.75, -1), (2, 2, 2), "crystal_dim", tags={"lantern": True})
    g.cube(B, "lantern_base", (-6.75, 5.75, -1), (2, 1, 2), "iron")
    # 袖：段になって広がる釣り鐘の袖口（すそは生成りの裏地）
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "wool_moss", inflate=0.5)
    pair(g, RA, LA, "cuff", (-9, 11, -3), (6, 3, 6), "wool_moss")
    pair(g, RA, LA, "cuff_bell", (-9.5, 10.25, -3.5), (7, 1, 7), "wool_moss", tags={"trim_bottom": True})
    # 下衣：前の割れた長い裾（脚ごとに動く）。割れ目は生成りの裏地。すそは広がり、前のふちに石の重り
    pair(g, RL, LL, "skirt", (-3.9, 3, -2), (4, 9, 4), "wool_moss", inflate=0.75, tags={"split_lining": True})
    pair(g, RL, LL, "hem", (-4.4, 2, -3), (5, 2, 6), "wool_moss", inflate=0.5,
         tags={"trim_bottom": True, "split_lining": True})
    pair(g, RL, LL, "hem_weight", (-4.9, 2, -3.5), (1, 1, 1), "stone")
    # 旅靴：革の靴・少し丸いつま先・苔鉄の留め金のついた革の帯
    pair(g, RB, LB, "boot", (-3.9, 0, -2), (4, 4, 4), "leather", inflate=0.6)
    pair(g, RB, LB, "boot_toe", (-3.4, -0.6, -3.6), (3, 1, 1), "leather")
    pair(g, RB, LB, "boot_strap", (-4.9, 1, -3), (6, 1, 6), "leather_dark")
    pair(g, RB, LB, "boot_buckle", (-2.4, 1, -3.25), (1, 1, 1), "iron")
    return g


# ---------------------------------------------------------------- 塗り

EMBLEM = ("...##...",
          "...@@...",
          "...##...")


class ArmorPainter(Painter):
    # ゲームの中の向き（表が −z、体の右が +x）で、正面から見て左上・手前から光が当たる
    light = np.array([0.5, 0.85, -0.5]) / np.linalg.norm([0.5, 0.85, -0.5])

    def paint_stone(self, c):
        tone = self.std(c, "stone", base=3.2, noise=0.15, blob=0.35, up=1.3, down=1.1, specks=0.025)
        tone += self.grooves(c)
        breaths = c.el.tags.get("breaths")
        if breaths is not None and c.n[2] < -0.6 and int(np.floor(c.p[1])) == int(breaths) and c.i % 2 == 1 \
                and 0 < c.i < c.W - 1:
            tone -= 2.2  # 息の穴
        if abs(c.n[0]) > 0.6:
            tone += 0.3
        col = pick(RAMPS["stone"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 300 + self.eid(c)) < 0.12:
            col = MOSS_COL
        return col

    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.5, noise=0.2, blob=0.35, up=1.4, down=0.9)
        tone += self.rivet(c)
        if c.el.tags.get("rivets_side") and abs(c.n[0]) > 0.6 and abs(c.p[1] - 28.5) < 0.6 and abs(c.p[2]) < 0.6:
            tone += 2.3
        if c.el.tags.get("buckle") and c.n[2] < -0.6:
            tone = 5.0 if (c.i, c.j) == (0, 0) else 3.2  # 留め金：角が光る
        col = pick(RAMPS["iron"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 500 + self.eid(c)) < 0.18:
            col = MOSS_COL
        return col

    def paint_wool_moss(self, c):
        tone = 3.0 + (self.tex(c, 61) - 0.5) * 0.3 + (self.blob(c, 2.0, 62) - 0.5) * 0.3
        tone += 0.25 if (c.i + 2 * c.j) % 4 == 0 else 0.0  # 織り目
        tone += self.bevel(c, up=0.7, down=0.8)
        col = pick(RAMPS["wool_moss"], tone)
        tags = c.el.tags
        d = c.edge_dist()
        cream = pick(RAMPS["wool"], 4.0 + (self.tex(c, 63) - 0.5) * 0.4)
        if tags.get("trim_bottom") and d["bottom"] == 0 and c.face.d not in ("up", "down"):
            col = cream
        if tags.get("lining_front") and c.n[2] < -0.6:
            col = pick(RAMPS["wool"], 3.0 if d["bottom"] == 0 else 3.4 + (self.tex(c, 64) - 0.5) * 0.4)
        if tags.get("lining_under") and c.face.d == "down":
            col = pick(RAMPS["wool"], 3.4)
        if tags.get("split_lining") and c.n[2] < -0.6 and abs(c.p[0]) < 0.75:
            col = pick(RAMPS["wool"], 3.7)
        if tags.get("stars") and c.n[2] < -0.6 and d["bottom"] == 1 and int(np.floor(c.p[0])) in (-4, 3):
            col = GOLD_LIGHT  # 金の糸の星（ケープの前のすそ）
        if tags.get("emblem_back") and c.face.d == "back":
            # 星灯の印：金の糸で縫ったランタン（ケープと帯のあいだ）
            r = c.j - 6
            if 0 <= r < len(EMBLEM):
                ch = EMBLEM[r][c.i]
                if ch == "#":
                    col = GOLD
                elif ch == "@":
                    col = GOLD_LIGHT
            if (c.i, c.j) in ((1, 6), (6, 8)):
                col = GOLD  # 小さな星（ランタンの光）
        return col

    def paint_leather(self, c):
        tone = self.std(c, "leather", base=3.0, noise=0.2, blob=0.2, up=0.8, down=0.6)
        return pick(RAMPS["leather"], tone)

    def paint_crystal_dim(self, c):
        tone = 3.2 + (self.tex(c, 81) - 0.5) * 0.6 + self.bevel(c, up=1.6, down=0.8)
        if c.el.tags.get("lantern") and c.face.d not in ("up", "down"):
            # ランタンのガラス：まん中に、消えた芯の影
            if c.i == c.W // 2 and c.j == c.H - 1:
                return (58, 52, 44)
        return pick(RAMPS["crystal_dim"], tone)


# ---------------------------------------------------------------- 確認画像

VIEWS = ((180, 0, "正面"), (-90, 0, "右の横"), (0, 0, "背中"), (-145, 14, "斜め"))


def preview(g, tex, out, tag, scale=10):
    from render import render_many
    from PIL import Image
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    ims = [render_many([(man, mtex), (g, tex)], yaw=y, pitch=p, scale=scale, size=(260, 400), center=(0, 16, 0))
           for y, p, _ in VIEWS]
    s = Image.new("RGBA", (260 * 4, 400), (40, 44, 52, 255))
    for k, im in enumerate(ims):
        s.alpha_composite(im, (260 * k, 0))
    s.save(os.path.join(out, tag + ".png"))


def paint(g):
    g.pack()
    return g.paint(ArmorPainter(g), frames=1)


def subset(g, bones):
    """着た場所のボーンだけのモデル（部位ごとの絵）。"""
    from types import SimpleNamespace
    return SimpleNamespace(elements=[c for c in g.elements if c.bone in bones])


SETS = {
    "plate": {
        "build": build_plate, "title": "③ 古代石の重鎧（ナイト）",
        "subtitle": "コモン・光らない ／ GeckoLib（armorHead・armorBody・両腕・両脚・両足のボーン） ／ 128×64の絵",
        "pieces": (("兜", "head", "ancient_stone_helm"), ("胴鎧", "chest", "ancient_stone_cuirass"),
                   ("脚甲", "legs", "ancient_stone_greaves"), ("鉄靴", "feet", "ancient_stone_sabatons")),
        "note": "1階の見張り石と同じ道標の石を、\n苔鉄の枠にはめた重い鎧。\n段の形・文字の溝・\n光らない結晶がシリーズの印。\n継ぎ目にツノモフの毛。",
        "piece_note": "兜：段の稜線・覗き穴・息の穴\n胴：逆三角の段の胸板・結晶の円盤\n脚：離した腿当て・前に出る膝\n足：道標の段のつま先",
        "details": (("兜の正面（覗き穴と息の穴）", ("head",), (0, 28.5, -2), 180, 0, 21),
                    ("2枚重ねの肩当てと毛のふさ", ("chest",), (7.5, 19.5, 0), -125, 12, 22),
                    ("膝当てと外のひれ", ("legs", "feet"), (2.2, 5.5, -1.5), -150, 8, 22),
                    ("段々のつま先と足首の輪", ("feet",), (2.0, 1.5, -1.0), -105, 10, 26)),
        "palette": (("道標の石", RAMPS["stone"]), ("苔鉄・苔", RAMPS["iron"][1:] + [MOSS_COL]),
                    ("ツノモフの毛・黒い革・結晶", [RAMPS["wool"][4], RAMPS["wool"][5], RAMPS["leather_dark"][2],
                                         RAMPS["leather_dark"][4], RAMPS["crystal_dim"][3], RAMPS["crystal_dim"][5]])),
    },
    "robe": {
        "build": build_robe, "title": "④ 古代石の法衣（ウィザード）",
        "subtitle": "コモン・光らない ／ GeckoLib（armorHead・armorBody・両腕・両脚・両足のボーン） ／ 128×64の絵",
        "pieces": (("頭巾", "head", "ancient_stone_hood"), ("法衣", "chest", "ancient_stone_robe"),
                   ("下衣", "legs", "ancient_stone_legwear"), ("旅靴", "feet", "ancient_stone_boots")),
        "note": "ツノモフの毛を苔で染めた、\n星灯の見習いの旅の法衣。\n生成りの裏地・石のトグル・\n腰に「星灯のランタン」。\n背中に金の糸の流派の印。",
        "piece_note": "頭巾：段のてっぺん・裏地のふち\n法衣：ケープ・トグル・ランタン\n下衣：前の割れた長い裾\n旅靴：革の帯と苔鉄の留め金",
        "details": (("頭巾（裏地のふちと石の重り）", ("head",), (0, 28, -2), 200, 6, 20),
                    ("腰の「星灯のランタン」", ("chest",), (5.75, 9.5, -0.5), -140, 10, 30),
                    ("背中の印（金の糸）", ("chest",), (0, 17, 2), 0, 0, 22),
                    ("割れた裾と旅靴", ("legs", "feet"), (1.9, 2.5, -1.5), -150, 8, 22)),
        "palette": (("苔で染めた毛", RAMPS["wool_moss"]),
                    ("生成りの裏地・金の糸", [RAMPS["wool"][3], RAMPS["wool"][4], RAMPS["wool"][5], GOLD, GOLD_LIGHT]),
                    ("革・苔鉄・石・結晶", [RAMPS["leather"][2], RAMPS["leather"][4], RAMPS["leather_dark"][3],
                                     RAMPS["iron"][4], RAMPS["stone"][4], RAMPS["crystal_dim"][4]])),
    },
}


def make_sheet(key, g, tex, path):
    import armor_icons
    from setsheet import make_set_sheet
    cfg = SETS[key]
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    full = [(man, mtex), (g, tex)]
    icons = [(label, armor_icons.icon(item)) for label, _, item in cfg["pieces"]]
    piece_views = [(label, -145, 14, [(man, mtex), (subset(g, PIECE_BONES[slot]), tex)])
                   for label, slot, _ in cfg["pieces"]]
    items = [
        {"label": "フルセット", "rarity": "コモン", "note": cfg["note"], "pairs": full,
         "views": [(lab, y, p) for y, p, lab in VIEWS], "icons": icons},
        {"label": "部位ごと", "rarity": "着た場所だけ表示", "note": cfg["piece_note"], "pairs": full,
         "views": piece_views, "icons": icons, "icon_cell": 40},
    ]
    details = []
    for label, slots, center, yaw, pitch, scale in cfg["details"]:
        bones = tuple(b for s_ in slots for b in PIECE_BONES[s_])
        details.append({"label": label, "pairs": [(man, mtex), (subset(g, bones), tex)], "center": center,
                        "yaw": yaw, "pitch": pitch, "scale": scale})
    make_set_sheet(path, cfg["title"], cfg["subtitle"], items, details=details, palette=cfg["palette"], row_h=420)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_armor"
    os.makedirs(out, exist_ok=True)
    import armor_icons
    for key in ("plate", "robe"):
        g = SETS[key]["build"]()
        frames = paint(g)
        used = max(c.uv[1] + c.block()[1] for c in g.elements)
        print(g.name, "部品", len(g.elements), "絵の使った高さ", used, "/", g.tex_h)
        g.save(repo, "armor", frames)
        preview(g, frames[0], out, key + "_draft")
        make_sheet(key, g, frames[0], os.path.join(repo, "docs/gear", "ancient_stone_%s_sheet.png" % key))
    armor_icons.save_all(repo)


if __name__ == "__main__":
    main()
