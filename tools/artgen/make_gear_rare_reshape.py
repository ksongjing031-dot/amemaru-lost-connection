"""6〜8階のボスのレア装備を、形から作り直す（11月26日）：村長の毛皮（防具一式）・鏡氷の短剣・湖主の髭弓。

  python3 tools/artgen/make_gear_rare_reshape.py <リポジトリ> [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py .        # 持ち物の絵（16×16）を32×32に

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」【補完】。
前は、村長の毛皮＝古代石の法衣の形の塗り直し、鏡氷の短剣＝霜銀の双短剣の塗り直し、湖主の髭弓＝霜銀の弓の塗り直しだった。
  村長の毛皮  ＝村長オルドの冬の装い（ボスのモデルと同じ紺の外套・白い毛皮・金）：丸い毛皮の帽子と金の紋章・肩まで広がる毛皮の襟・
                前のまん中を下る金の縁取り・村長の鎖と紋章の円盤（氷の灯の青が光る）・ひだのある長いすそ・毛皮の口の革の長靴
  鏡氷の短剣  ＝鏡のたてがみから削り出した、面のある鏡の結晶の刃（左の面は白く映り、右の面は紫）・刃のまん中の紫の光の筋・
                刃に寄りそう、少しずれた「写しの刃」（Phase 0-A「刃に映る自分の姿が、ほんの一瞬遅れて動く」）・鏡の破片の三日月の鍔
  湖主の髭弓  ＝湖主ムーネ（大きなナマズ）の体（ボスのモデルと同じ色）：灰白のうろこの腕・先からさらに伸びて巻く、光るひげ・
                光るひげの弦・淡い青のひれを巻いた握りの白い真珠と、背の三本のひれ
エピックの光る所：村長の紋章の円盤／鏡氷の刃の紫の筋と写しの刃／髭弓のひげと弦（forge_data の明るさ15）。
持ち物の絵は rare_icons.py。仕様書：docs/gear/rare_f7.md・rare_f8.md・docs/phase8_village.md（村長の毛皮）
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geomodel import GeoModel, mannequin, paint_mannequin  # noqa: E402
from javamodel import JavaModel  # noqa: E402
from materials import Painter, RAMPS, hexc, hsh, pick  # noqa: E402
import weapons  # noqa: E402
from make_gear_ancient_armor import ArmorPainter, ARMOR_BONES, H, B, RA, LA, RL, LL, RB, LB, PIECE_BONES, VIEWS, \
    pair, subset  # noqa: E402
from make_gear_phase6 import display as phase6_display, BOW_STRING_X  # noqa: E402
from ryumon_common import RyumonPaint  # noqa: E402
from frost_common import FrostPaint  # noqa: E402

CREDIT = "AMEMARU: 6〜8階のボスのレア（11月26日に形から作り直した。tools/artgen/make_gear_rare_reshape.py で生成）"


def ramp(*hexes):
    return [hexc(h) for h in hexes]


RAMPS.update({
    # 村長の毛皮（ボスのオルドの外套の紺と、毛皮の白灰）
    "mayor_coat": ramp("#0E1426", "#162038", "#1F2C4C", "#2A3A62", "#384A78", "#4A5E90", "#6276A8"),
    "mayor_fur": ramp("#6A665E", "#837E74", "#9C968B", "#B5AFA3", "#CBC6BB", "#DFDBD2", "#F0EDE7"),
    "frostglow": ramp("#3A7BA0", "#4F95BB", "#6BB0D3", "#8DC9E6", "#B2DEF2", "#D7F0FA", "#F4FCFF"),
    # 鏡氷（白銀の鏡と、淡い紫のプリズム）
    "mirror": ramp("#3A3A52", "#56567A", "#77779E", "#9C9CC0", "#C2C2DE", "#E2E2F2", "#FBFBFF"),
    "prism": ramp("#4A2E86", "#6340A8", "#7E57C6", "#9B76DC", "#B99AEC", "#D7C2F7", "#F3EBFF"),
    # 湖主（ボスのムーネのモデルと同じ：灰白のうろこ・淡い青のひれ・光るひげ）と、真珠
    "lakescale": ramp("#545C68", "#6E7884", "#8C96A2", "#AAB4BE", "#C8D0D8", "#E2E8EE", "#F8FAFC"),
    "lakefin": ramp("#3E5C78", "#527494", "#6A8EAE", "#86A8C6", "#A4C2DA", "#C2DAEA", "#E0F0FA"),
    "whiskerglow": ramp("#1E7C8E", "#2A98AC", "#40B4C8", "#66CEDE", "#94E2EE", "#C6F2F8", "#FFFFFF"),
    "pearl": ramp("#8A8896", "#A7A5B3", "#C2C1CD", "#D9D8E2", "#E9E9F0", "#F5F5FA", "#FFFFFF"),
    "bone": ramp("#4A4234", "#6C6250", "#8E846D", "#AFA68C", "#CBC3A8", "#E1DAC2", "#F3EEDD"),
})


class RarePaint:
    # ---------------------------------------------------------------- 村長の毛皮
    def paint_mayor_coat(self, c):
        tone = self.std(c, "mayor_coat", base=3.4, noise=0.25, blob=0.3, up=1.1, down=1.0)
        if c.el.tags.get("folds") and abs(float(c.n[1])) < 0.6:
            along = c.p[0] if abs(float(c.n[2])) > 0.5 else c.p[2]
            if int(round(along)) % 2 == 0:
                tone -= 0.8  # 外套のひだ
        col = pick(RAMPS["mayor_coat"], tone)
        d = c.edge_dist()
        trim = c.el.tags.get("gold_trim")
        if trim and c.face.d not in ("up", "down") and d["bottom"] == 0:
            col = pick(RAMPS["gold"], 4.2)
        if c.el.tags.get("center_trim") and c.n[2] < -0.6 and abs(c.p[0]) < 1.0:
            col = pick(RAMPS["gold"], 4.4 if abs(c.p[0]) < 0.5 else 3.2)
        return col

    def paint_mayor_fur(self, c):
        return self.fur(c, "mayor_fur", 4.0)

    def paint_frostglow(self, c):
        tone = 4.2 + (self.tex(c, 391) - 0.5) * 0.4 + (1.2 if (c.i + c.j) == 0 else 0.0)
        return pick(RAMPS["frostglow"], tone)

    # ---------------------------------------------------------------- 鏡氷
    def paint_mirror(self, c):
        """鏡の面：左を向いた面は明るく映り、右を向いた面は暗い。ななめの映りこみの筋。"""
        tone = 3.8 + self.bevel(c, up=1.4, down=1.2) + (self.tex(c, 381) - 0.5) * 0.3
        if c.n[0] < -0.5:
            tone += 1.2
        elif c.n[0] > 0.5:
            tone -= 0.9
        if c.face.d not in ("up", "down") and (c.i + c.j) % 5 == 0:
            tone += 1.0  # 映りこみの筋
        return pick(RAMPS["mirror"], tone)

    def paint_prism(self, c):
        tone = 4.2 + (self.tex(c, 383) - 0.5) * 0.6 + (1.3 if (c.i + c.j) == 0 else 0.0)
        return pick(RAMPS["prism"], tone)

    def paint_echo(self, c):
        """写しの刃：淡い紫の、すけるような刃（光る。ところどころ抜けている）。"""
        if hsh(self.eid(c), c.i, c.j, 385) < 0.18:
            return None
        tone = 5.0 + (self.tex(c, 386) - 0.5) * 0.8
        return pick(RAMPS["prism"], tone)

    # ---------------------------------------------------------------- 湖主
    def paint_lakescale(self, c):
        tone = self.std(c, "lakescale", base=3.2, noise=0.15, blob=0.2, up=1.3, down=0.9)
        tone += self.scales(c)
        if c.el.tags.get("belly") and c.face.d in ("down", "left"):
            tone += 1.2
        return pick(RAMPS["lakescale"], tone)

    def paint_whiskerglow(self, c):
        tone = 4.4 + (self.tex(c, 393) - 0.5) * 0.5 + ((0.6, -0.4)[(c.i + c.j) % 2] if c.el.tags.get("twist") else 0.0)
        return pick(RAMPS["whiskerglow"], tone)

    def paint_pearl(self, c):
        tone = 4.4 + self.bevel(c, up=1.4, down=1.4) + (1.2 if (c.i + c.j) == 0 else 0.0)
        return pick(RAMPS["pearl"], tone)

    def paint_lakefin(self, c):
        """ひれ：淡い青。骨のすじ（1つおきに明るい）。"""
        tone = 3.4 + (0.9 if c.j % 2 == 0 else -0.2) + self.bevel(c, up=0.8, down=0.8)
        if c.el.tags.get("wrap"):
            tone += self.wrap(c) - 1.2
        return pick(RAMPS["lakefin"], tone)


class RareArmorPainter(RarePaint, FrostPaint, RyumonPaint, ArmorPainter):
    pass


class RareWeaponPainter(RarePaint, FrostPaint, RyumonPaint, Painter):
    pass


# ================================================================ 村長の毛皮（全職業の防具一式）

def build_mayor_fur():
    g = GeoModel("mayor_fur", 128, 128)
    for b, pivot in ARMOR_BONES:
        g.bone(b, pivot)
    # 帽：丸い毛皮の帽子（平らなてっぺん）・前に金の村の紋章（氷の灯の青の玉）
    g.cube(H, "hat", (-5, 28.5, -5), (10, 5, 10), "mayor_fur", inflate=0.1)
    g.cube(H, "hat_top", (-4, 33.5, -4), (8, 1, 8), "mayor_fur")
    g.cube(H, "badge", (-1.5, 29.5, -5.65), (3, 3, 1), "gold")
    g.cube(H, "badge_gem", (-0.5, 30.5, -6.05), (1, 1, 1), "frostglow", glow=True)
    # 外套：紺の外套（ひだ）・前のまん中を下る金の縁取り・肩まで広がる白い毛皮の襟・村長の金の鎖と、紋章の円盤（光る）・革の帯
    g.cube(B, "coat", (-4, 12, -2), (8, 12, 4), "mayor_coat", inflate=0.9, tags={"folds": True, "center_trim": True})
    g.cube(B, "collar", (-6.5, 21, -4.5), (13, 4, 9), "mayor_fur", inflate=0.1)
    for k, (x, y) in enumerate(((-3.5, 20.25), (-2.5, 19.0), (-1.5, 18.0), (1.5, 18.0), (2.5, 19.0), (3.5, 20.25))):
        g.cube(B, f"chain_{k}", (x - 0.5, y, -3.75), (1, 1, 1), "gold", inflate=-0.1)
    g.cube(B, "medallion", (-1.5, 15.5, -4.1), (3, 3, 1), "gold", tags={"rivets": [(0.0, 16.0)]})
    g.cube(B, "medallion_gem", (-0.5, 16.5, -4.5), (1, 1, 1), "frostglow", glow=True)
    g.cube(B, "belt", (-5, 11.5, -3.5), (10, 2, 7), "leather_dark", inflate=0.3)
    g.cube(B, "buckle", (-1.5, 11.5, -4.35), (3, 2, 1), "gold")
    pair(g, RA, LA, "sleeve", (-8, 12, -2), (4, 12, 4), "mayor_coat", inflate=0.6, tags={"folds": True})
    pair(g, RA, LA, "cuff", (-8.75, 12, -2.75), (5, 3, 5), "mayor_fur", inflate=0.3)
    # 脚衣：ひだのある長いすそ（すそは金の縁取り）
    pair(g, RL, LL, "skirt", (-3.9, 3, -2), (4, 9, 4), "mayor_coat", inflate=0.95,
         tags={"folds": True, "gold_trim": True})
    pair(g, RL, LL, "hem_fur", (-4.4, 2, -2.5), (5, 1, 5), "mayor_fur", inflate=0.55)
    # 長靴：毛皮の口の暗い革の長靴・金の留め金
    pair(g, RB, LB, "boot", (-3.9, 0, -2), (4, 5, 4), "leather_dark", inflate=0.6)
    pair(g, RB, LB, "boot_toe", (-3.4, -0.6, -3.7), (3, 2, 1), "leather_dark")
    pair(g, RB, LB, "boot_fur", (-4.4, 4.5, -2.5), (5, 2, 5), "mayor_fur", inflate=0.35)
    pair(g, RB, LB, "boot_clasp", (-2.4, 2.5, -2.95), (1, 1, 1), "gold")
    return g


MAYOR_PIECES = (("帽", "head", "mayor_fur_hat"), ("外套", "chest", "mayor_fur_coat"), ("脚衣", "legs", "mayor_fur_leggings"),
                ("長靴", "feet", "mayor_fur_boots"))


# ================================================================ 鏡氷の短剣（ローグの双短剣）

def build_mirror_daggers():
    """大きさは流紋・霜銀の双短剣と同じ。面のある鏡の結晶の刃と紫の光の筋・少しずれた写しの刃・鏡の破片の三日月の鍔。"""
    m = JavaModel("mirror_ice_daggers", 64, 64)
    m.box("pommel", (7.3, -2.8, 7.3), (8.7, -1.4, 8.7), "mirror")
    m.box("pommel_shard", (7.7, -3.6, 7.7), (8.3, -2.8, 8.3), "prism", glow=True)
    m.box("grip", (7.35, -1.4, 7.35), (8.65, 4, 8.65), "nightsnow", tags={"wrap": True})
    m.box("guard", (6.2, 4.0, 7.3), (9.8, 4.9, 8.7), "mirror")
    m.box("crescent_l", (5.0, 4.6, 7.45), (6.2, 5.6, 8.55), "mirror")
    m.box("crescent_l2", (4.4, 5.6, 7.55), (5.2, 7.0, 8.45), "mirror")
    m.box("crescent_r", (9.8, 4.6, 7.45), (11.0, 5.6, 8.55), "mirror")
    m.box("crescent_r2", (10.8, 5.6, 7.55), (11.6, 7.0, 8.45), "mirror")
    # 刃：段の鏡の結晶（左の面は白く、右の面は紫に）と、まん中の紫の光の筋
    for k, (x0, y0, x1, y1, z) in enumerate(((6.7, 4.9, 9.3, 8.5, 0.45), (7.0, 8.5, 9.0, 12.0, 0.4),
                                             (7.3, 12.0, 8.7, 15.0, 0.35), (7.6, 15.0, 8.4, 17.25, 0.3))):
        m.box(f"blade_{k}", (x0, y0, 8 - z), (x1, y1, 8 + z), "mirror", tags={"edge": "left"})
    m.box("tip", (7.85, 17.25, 7.8), (8.15, 18.5, 8.2), "prism", glow=True)
    m.box("core", (7.8, 5.4, 7.5), (8.2, 15.5, 8.5), "prism", glow=True)
    # 写しの刃：右に少しずれた、すけるような淡い紫の刃（光る）
    m.box("echo_0", (9.5, 6.0, 7.85), (10.1, 11.0, 8.15), "echo", glow=True)
    m.box("echo_1", (9.2, 11.0, 7.88), (9.7, 14.5, 8.12), "echo", glow=True)
    m.box("echo_2", (8.9, 14.5, 7.9), (9.2, 16.5, 8.1), "echo", glow=True)
    return m


# ================================================================ 湖主の髭弓（アーチャー）

def build_whisker_bow():
    """腕と弦の形は流紋・霜銀の弓と同じ（弦は +x の側）。灰白のうろこの腕（ボスのムーネと同じ色）・先から伸びて巻く光るひげ・光るひげの弦。"""
    m = JavaModel("lake_lord_whisker_bow", 64, 64)
    m.box("grip", (7.0, 0, 7.2), (8.8, 5, 8.8), "lakescale", tags={"scales": True})
    m.box("grip_wrap", (6.8, 1.0, 7.0), (9.0, 4.0, 9.0), "lakefin", tags={"wrap": True})
    m.box("pearl", (8.9, 1.9, 7.4), (10.1, 3.1, 8.6), "pearl")
    # 背の三本の骨のひれ（握りの外。−x の側）
    for k, (y0, y1, x0) in enumerate(((0.5, 4.5, 6.3), (1.0, 4.0, 5.6), (1.5, 3.5, 4.9))):
        m.box(f"fin_{k}", (x0, y0, 7.85), (x0 + 0.7, y1, 8.15), "lakefin")
    segs = ((5, 9, 8.0, 0.65), (9, 13, 8.6, 0.6), (13, 16.5, 9.4, 0.5), (16.5, 19, 10.3, 0.4))
    for k, (y0, y1, x, w) in enumerate(segs):
        m.box(f"upper_{k}", (x - w, y0, 8 - w), (x + w, y1, 8 + w), "lakescale", tags={"scales": True, "belly": True})
        m.box(f"lower_{k}", (x - w, -y1 + 5, 8 - w), (x + w, -y0 + 5, 8 + w), "lakescale", tags={"scales": True, "belly": True})
    # 先：腕の先から、さらに上（下）へ伸びて外へ巻く光るひげ
    m.box("whisker_top_0", (10.0, 19.0, 7.8), (10.6, 22.5, 8.2), "whiskerglow", glow=True, tags={"twist": True})
    m.box("whisker_top_1", (10.6, 22.5, 7.8), (12.4, 23.1, 8.2), "whiskerglow", glow=True)
    m.box("whisker_top_2", (12.0, 20.6, 7.8), (12.6, 22.5, 8.2), "whiskerglow", glow=True)
    # 下は、Java のモデルの決まり（−16 より下に出せない）で短く巻く
    m.box("whisker_bot_0", (10.0, -16.0, 7.8), (10.6, -14.0, 8.2), "whiskerglow", glow=True, tags={"twist": True})
    m.box("whisker_bot_1", (10.6, -16.0, 7.8), (12.4, -15.4, 8.2), "whiskerglow", glow=True)
    m.box("whisker_bot_2", (12.0, -15.4, 7.8), (12.6, -13.6, 8.2), "whiskerglow", glow=True)
    m.box("string", (BOW_STRING_X[0], -14.9, 7.88), (BOW_STRING_X[1], 19.9, 8.12), "whiskerglow", glow=True)
    m.box("nock_top", (10.5, 18.6, 7.7), (11.15, 19.0, 8.3), "pearl")
    m.box("nock_bottom", (10.5, -14.0, 7.7), (11.15, -13.6, 8.3), "pearl")
    return m


def export_mayor(repo, out):
    import json
    import rare_icons
    from setsheet import make_set_sheet
    g = build_mayor_fur()
    g.pack()
    frames = g.paint(RareArmorPainter(g), frames=1)
    g.save(repo, "armor", frames)
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for _, _, name in MAYOR_PIECES:
        rare_icons.icon(name).save(os.path.join(base, "textures/item", name + ".png"))
        with open(os.path.join(base, "models/item", name + ".json"), "w", encoding="utf-8") as fp:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + name}}, fp,
                      ensure_ascii=False, indent=2)
            fp.write("\n")
    man = mannequin()
    mtex = man.paint(paint_mannequin, frames=1)[0]
    tex = frames[0]
    full = [(man, mtex), (g, tex)]
    icons = [(label, rare_icons.icon(item)) for label, _, item in MAYOR_PIECES]
    piece_views = [(label, -145, 14, [(man, mtex), (subset(g, PIECE_BONES[slot]), tex)]) for label, slot, _ in MAYOR_PIECES]
    items = [
        {"label": "フルセット", "rarity": "エピック・Lv26・全職業", "pairs": full, "views": [(lab, y, p) for y, p, lab in VIEWS],
         "icons": icons, "note": "村長オルドの冬の装い（ボスと同じ\n紺の外套・白い毛皮・金）。丸い毛皮の\n帽子と金の紋章・肩まで広がる毛皮の\n襟・村長の鎖と紋章の円盤（光る）。"},
        {"label": "部位ごと", "rarity": "着た場所だけ表示（紋章の青は光る）", "note": "", "pairs": full, "views": piece_views,
         "icons": icons, "icon_cell": 40},
    ]
    make_set_sheet(os.path.join(repo, "docs/gear", "mayor_fur_sheet.png"), "村長の毛皮（6階のボスのレア）",
                   "エピック ／ 11月26日に形から作り直した【補完】／ 128×128の絵", items, row_h=440)
    print("wrote mayor_fur 部品", len(g.elements), "光る部品", sum(1 for c in g.elements if c.glow))


RARE_WEAPONS = {
    "mirror_ice_daggers": (build_mirror_daggers, "daggers", "鏡氷の短剣（ローグ）", "エピック・Lv30（リフレクタのレア）",
                           "面のある鏡の結晶の刃（左は白く映り、右は紫）\nまん中の紫の光の筋・少しずれた写しの刃（光る）\n"
                           "鏡の破片の三日月の鍔", "mirror_ice_daggers_sheet.png", "鏡氷の短剣（7階のボスのレア）"),
    "lake_lord_whisker_bow": (build_whisker_bow, "bow", "湖主の髭弓（アーチャー）", "エピック・Lv34（ムーネのレア）",
                              "灰白のうろこの腕・先から伸びて巻く光るひげ\n光るひげの弦・握りの白い真珠と三本の淡い青のひれ",
                              "lake_lord_whisker_bow_sheet.png", "湖主の髭弓（8階のボスのレア）"),
}


def export_rare_weapon(repo, name):
    """武器1つを書き出し、docs/gear の確認画像も作る。(モデルの絵, 持ち物の絵, 光る部品の数) を返す（PIL の絵）。"""
    import rare_icons
    from PIL import Image
    from setsheet import make_set_sheet
    build, kind, label, rarity, note, sheet, sheet_title = RARE_WEAPONS[name]
    m = build()
    m.cull_hidden_faces()
    m.pack()
    frames = m.paint(RareWeaponPainter(m), frames=1)
    icon = rare_icons.icon(m.name)
    weapons.export_weapon(repo, m.name, m, frames, icon, kind, CREDIT, display=phase6_display(kind))
    row = {"label": label, "rarity": rarity, "note": note, "model": m, "tex": frames[0], "icon": icon}
    make_set_sheet(os.path.join(repo, "docs/gear", sheet), sheet_title, "エピック・光る所あり｜11月26日に形から作り直した【補完】", [row])
    lit = sum(1 for e in m.elements if e.glow)
    print("wrote", m.name, "部品", len(m.elements), "光る部品", lit)
    return Image.fromarray(frames[0], "RGBA"), icon.image(), lit


def export_weapons(repo):
    for name in RARE_WEAPONS:
        export_rare_weapon(repo, name)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_rare"
    os.makedirs(out, exist_ok=True)
    export_mayor(repo, out)
    export_weapons(repo)


if __name__ == "__main__":
    main()
