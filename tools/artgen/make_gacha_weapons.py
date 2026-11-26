"""祈願（ガチャ）の★5：ガチャ限のユニーク武器（8本。11月27日）。

  python3 tools/artgen/make_gacha_weapons.py <リポジトリ> [確認画像の出力先]

11月27日のご指示「ガチャからはガチャ限のユニーク武器も作る」【補完】。ユニークの決まり（Rarity：専用モデルと専用エフェクト）に
合わせて、どれも形から作り、光る部品は4コマでゆっくり脈を打つ（光が柄から先へ流れる）。階ごとのボスの記憶から形を取る：
  1 大地角の大剣（ナイト）  ：苔むした大地の石の幅広の刃・琥珀に光る筋・鍔から反り返る象牙の二本の角・琥珀の柄頭
  2 千年樹の杖（ウィザード）：ねじれた生きた枝の柄・先で枝が抱く光る種・若葉・まわりを飛ぶ小さな光
  3 潮鳴りの弓（アーチャー）：青銅の管の腕（鋲の帯）・外側に光る水の筋・握りの横の小さな水門のハンドル・弭の光るしずく
  4 聖歌の大鈴（ヒーラー）  ：白い大理石の柄と金の環・金の三日月の枠から下がる大きな金の鈴・紫に光る舌・左右の音の羽（光る）
  5 翠晶の誓剣（ナイト）    ：白い石の刃（格子の溝・翠の光るすじ）・刃の根もとに浮かんで囲む翠の結晶の環・翠の柄頭
  6 雪灯の拳甲（モンク）    ：白い毛皮の口・暗い赤の革に巻いた金の鎖・霜銀のこぶしの板・甲の金の枠の中に光る雪灯の結晶
  7 鏡月の双刃（ローグ）    ：三日月に反った鏡の刃（内のふちにプリズムの光）・夜の色の鍔と柄・プリズムの柄頭
  8 静湖の真珠杖（ウィザード）：静氷の柄（霧の糸の握り）・先に浮かぶ大きな真珠・それを囲む波紋の環（光る）・垂れるひげの光の糸
持ち方と大きさは、その種類のいつもの武器と同じ（weapon_display・phase6_display）。持ち物の絵は16×16（enhance_icons.py で32×32）。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter, RAMPS, hexc, pick, ramp  # noqa: E402
from pixelart import Pixel  # noqa: E402
import weapons  # noqa: E402
from make_gear_ancient_stone import weapon_display  # noqa: E402
from make_gear_phase6 import display as phase6_display, BOW_STRING_X  # noqa: E402

CREDIT = "AMEMARU: 祈願のユニーク武器（11月27日。tools/artgen/make_gacha_weapons.py で生成。光る部品は4コマで脈を打つ）"
FRAMES = 4

RAMPS.update({
    # 1 大地角
    "mossstone": ramp("#2C2A22", "#433F33", "#5C5646", "#766F5B", "#8E866F", "#A9A189", "#C4BDA5"),
    "moss": ramp("#1E3314", "#2C4A1C", "#3E6526", "#527F32", "#6A9A42", "#86B456", "#A6CC74"),
    "horn": ramp("#6E604A", "#8C7C60", "#A99878", "#C4B494", "#DCCEB0", "#EDE3CB", "#FAF5E8"),
    "amber_glow": ramp("#7A3E06", "#A3580C", "#C97716", "#E89A28", "#FFBE48", "#FFDC8A", "#FFF3CF"),
    # 2 千年樹
    "livewood": ramp("#241608", "#382412", "#4E331C", "#664628", "#7E5A36", "#987248", "#B48C5E"),
    "leaf": ramp("#173A12", "#21521A", "#2E6C24", "#3E8A30", "#56A842", "#76C45C", "#9EDC80"),
    "seed_glow": ramp("#3E6A10", "#5A8A18", "#7EAE2A", "#A6D044", "#CDEA70", "#EAF8A8", "#FBFFE4"),
    # 3 潮鳴り
    "tidebronze": ramp("#22180E", "#3A2816", "#583C1E", "#7A5428", "#9C6E36", "#BC8C4C", "#D8AC6E"),
    "verdigris": ramp("#173027", "#22463A", "#2F5E4E", "#3F7A66", "#559A82", "#76B8A0", "#9CD2BE"),
    "water_glow": ramp("#0B2E5A", "#124A88", "#1E6CB8", "#3A92DE", "#66B8F2", "#A6DCFA", "#E4F6FF"),
    # 4 聖歌
    "marble": ramp("#6E6A62", "#8C877E", "#ABA69C", "#C6C1B6", "#DDD9CF", "#EEEBE4", "#FBFAF6"),
    "gold": ramp("#4E3206", "#6E4A0C", "#946814", "#BC8C22", "#DEB03C", "#F4CE68", "#FFEBA8"),
    "violet_glow": ramp("#3A1E72", "#542C9A", "#7240C2", "#9462E0", "#B88CF4", "#DABCFF", "#F4EAFF"),
    # 5 翠晶
    "whitestone": ramp("#4A463F", "#6A655C", "#8E887D", "#AFA99E", "#CDC8BD", "#E5E1D8", "#F7F5EF"),
    "bronze": ramp("#281C10", "#412E18", "#5D4322", "#7A5A2E", "#98733B", "#B68D50", "#D2AC6E"),
    "jade_glow": ramp("#0D4636", "#14664E", "#1D8867", "#2BAA81", "#4FCC9E", "#8CE8C3", "#D3FFEE"),
    "cloth": ramp("#6B675D", "#8A857A", "#A7A294", "#C0BBAD", "#D6D2C5", "#E7E4DA", "#F4F2EB"),
    # 6 雪灯
    "fur": ramp("#7A7E86", "#959AA2", "#AFB4BC", "#C7CCD2", "#DCE0E5", "#ECEFF2", "#F9FAFB"),
    "redleather": ramp("#2A0E0C", "#401614", "#58201C", "#702C26", "#8A3A30", "#A44E40", "#BE6656"),
    "fsilver": ramp("#4A5668", "#6A788C", "#8C9AAE", "#B0BCCC", "#CFD8E4", "#E6EDF4", "#F8FBFE"),
    "lamp_glow": ramp("#8A3A06", "#B4520C", "#DA7218", "#F49A30", "#FFC05A", "#FFE29A", "#FFF8DE"),
    # 7 鏡月
    "mirror": ramp("#3E4658", "#5A6478", "#7A8498", "#9CA6BA", "#C0C8D8", "#E0E6F0", "#FFFFFF"),
    "night": ramp("#0A0C16", "#121628", "#1C2138", "#262D4A", "#323A5E", "#424C74", "#56628E"),
    "prism_glow": ramp("#4A2C8A", "#6A44B4", "#8C66D8", "#9EA8F4", "#8FE6F4", "#C8F8FF", "#FFFFFF"),
    # 8 静湖
    "silentice": ramp("#3E4852", "#56626E", "#707C8A", "#8C98A6", "#A8B4C0", "#C4CED8", "#E0E8EE"),
    "mist": ramp("#76808C", "#8C96A2", "#A2ACB8", "#B8C2CC", "#CCD4DC", "#DEE4EA", "#EEF2F6"),
    "pearl": ramp("#8C8A84", "#A6A49C", "#BEBCB4", "#D2D0C8", "#E4E2DA", "#F2F0EA", "#FFFFFF"),
    "ripple_glow": ramp("#1E5A6E", "#2A7C94", "#3EA0B8", "#5CC2D6", "#8ADCEA", "#BCF0F8", "#EEFCFF"),
})

# 材質ごとの塗り方（glow：光る・4コマで脈を打つ。ほかは std の数）
STYLE = {
    "mossstone": {"base": 3.4, "noise": 0.35, "blob": 0.5, "specks": 0.03},
    "moss": {"base": 3.6, "noise": 0.5, "blob": 0.4},
    "horn": {"base": 4.0, "noise": 0.2, "blob": 0.25},
    "livewood": {"base": 3.4, "noise": 0.3, "blob": 0.35},
    "leaf": {"base": 3.8, "noise": 0.35, "blob": 0.3},
    "tidebronze": {"base": 3.8, "noise": 0.2, "blob": 0.2},
    "verdigris": {"base": 3.6, "noise": 0.4, "blob": 0.5},
    "marble": {"base": 4.2, "noise": 0.25, "blob": 0.45},
    "gold": {"base": 4.0, "noise": 0.15, "blob": 0.15, "up": 1.6},
    "whitestone": {"base": 4.0, "noise": 0.2, "blob": 0.3},
    "bronze": {"base": 3.8, "noise": 0.15, "blob": 0.15},
    "cloth": {"base": 3.8, "noise": 0.2, "blob": 0.0},
    "fur": {"base": 4.0, "noise": 0.6, "blob": 0.3, "up": 0.6, "down": 0.6},
    "redleather": {"base": 3.4, "noise": 0.25, "blob": 0.2},
    "fsilver": {"base": 4.0, "noise": 0.15, "blob": 0.15, "up": 1.5},
    "mirror": {"base": 4.4, "noise": 0.1, "blob": 0.2, "up": 1.8},
    "night": {"base": 3.2, "noise": 0.2, "blob": 0.2},
    "silentice": {"base": 3.8, "noise": 0.25, "blob": 0.35},
    "mist": {"base": 4.0, "noise": 0.3, "blob": 0.0},
    "pearl": {"base": 4.4, "noise": 0.12, "blob": 0.35, "up": 1.6},
}
GLOW = {"amber_glow", "seed_glow", "water_glow", "violet_glow", "jade_glow", "lamp_glow", "prism_glow", "ripple_glow"}


class GachaPainter(Painter):
    def __getattr__(self, name):
        if not name.startswith("paint_"):
            raise AttributeError(name)
        mat = name[6:]
        if mat in GLOW:
            return lambda c: self.pulse(c, mat)
        if mat not in STYLE:
            raise AttributeError(name)
        style = STYLE[mat]

        def paint(c):
            tone = self.std(c, mat, base=style.get("base", 3.4), noise=style.get("noise", 0.25), blob=style.get("blob", 0.3),
                            up=style.get("up", 1.2), down=style.get("down", 1.0), specks=style.get("specks", 0.0))
            tone += self.grooves(c) + self.rivet(c)
            if c.el.tags.get("edge"):
                tone += self.edge(c, 1.3)
            if c.el.tags.get("grain"):
                tone += self.grain(c)
            if c.el.tags.get("wrap"):
                tone += self.wrap(c)
            if c.el.tags.get("lattice") and c.face.d not in ("up", "down"):
                step = c.el.tags["lattice"]
                a = (c.i + 1) % step == 0
                b = (c.j + 1) % step == 0
                tone += 0.8 if a and b else (-0.8 if a or b else 0.0)
            if c.el.tags.get("bands") and c.face.d not in ("up", "down"):
                tone += -0.9 if c.j % c.el.tags["bands"] == 0 else 0.0
            return pick(RAMPS[mat], tone)
        return paint

    def pulse(self, c, mat):
        """光る部品：4コマで、柄から先へ光が流れるように明るさが動く。"""
        phase = 2.0 * math.pi * (c.frame / max(1, c.frames)) - float(c.p[1]) * 0.35
        tone = 4.3 + (self.tex(c, 81) - 0.5) * 0.5 + 0.75 * math.sin(phase)
        if (c.i + c.j) == 0:
            tone += 0.8
        return pick(RAMPS[mat], tone)


def glow(m, name, frm, to, mat, rot=None, tags=None):
    m.box(name, frm, to, mat, rot=rot, glow=True, tags=tags or {})


# ================================================================ 1 大地角の大剣（ナイトの両手剣）

def build_earth_horn():
    m = JavaModel("earth_horn_greatsword", 64, 64)
    glow(m, "pommel_amber", (7.3, -5.8, 7.3), (8.7, -4.4, 8.7), "amber_glow")
    m.box("pommel", (6.9, -4.4, 6.9), (9.1, -2.6, 9.1), "mossstone", tags={"edge": "both"})
    m.box("grip", (7.4, -2.6, 7.4), (8.6, 7.6, 8.6), "redleather", tags={"wrap": True})
    m.box("guard", (4.2, 7.6, 6.9), (11.8, 9.6, 9.1), "mossstone", tags={"edge": "both"})
    m.box("guard_moss", (4.4, 9.4, 6.8), (6.6, 9.9, 9.2), "moss")
    m.box("guard_moss_r", (9.6, 9.4, 6.8), (11.4, 9.8, 9.2), "moss")
    # 二本の象牙の角：鍔の両はしから外へ大きく張り出し、上へ反って、先は内へ戻る（三日月の形）
    for side, s in (("l", -1), ("r", 1)):
        cx = 8.0 + s * 3.8
        segs = ((0.0, 8.0, 1.6, 1.6, 1.7), (1.2, 9.4, 1.5, 1.8, 1.6), (2.2, 11.0, 1.3, 1.8, 1.5), (2.9, 12.6, 1.1, 1.8, 1.4),
                (3.2, 14.2, 1.0, 1.8, 1.3), (3.1, 15.8, 0.9, 1.6, 1.1), (2.6, 17.2, 0.8, 1.4, 1.0), (1.9, 18.4, 0.7, 1.1, 0.8))
        for k, (off, y, w, h, d) in enumerate(segs):
            x0 = cx + s * off
            m.box(f"horn_{side}_{k}", (min(x0, x0 + s * w), y, 8.0 - d / 2), (max(x0, x0 + s * w), y + h, 8.0 + d / 2), "horn",
                  tags={"edge": "both"} if k < 3 else {})
        tip = cx + s * 1.5
        m.box(f"horn_tip_{side}", (tip - 0.3, 19.4, 7.75), (tip + 0.3, 20.2, 8.25), "horn")
    # 刃：幅の広い大地の石・両のふちは明るい石・まん中に琥珀の光る筋・根もとの苔
    m.box("blade", (5.0, 9.6, 7.15), (11.0, 26.5, 8.85), "mossstone", tags={"edge": "both", "bands": 5})
    glow(m, "vein", (7.65, 10.4, 7.0), (8.35, 25.0, 9.0), "amber_glow")
    glow(m, "vein_l", (6.2, 13.0, 7.05), (6.7, 16.5, 8.95), "amber_glow")
    glow(m, "vein_r", (9.3, 17.5, 7.05), (9.8, 21.0, 8.95), "amber_glow")
    m.box("moss_l", (4.85, 9.6, 7.3), (5.4, 12.2, 8.7), "moss")
    m.box("moss_r", (10.6, 9.6, 7.3), (11.15, 11.4, 8.7), "moss")
    m.box("tip_0", (5.6, 26.5, 7.25), (10.4, 28.0, 8.75), "mossstone", tags={"edge": "both"})
    m.box("tip_1", (6.6, 28.0, 7.35), (9.4, 29.4, 8.65), "mossstone")
    m.box("tip_2", (7.4, 29.4, 7.5), (8.6, 30.4, 8.5), "mossstone")
    return m


# ================================================================ 2 千年樹の杖（ウィザード）

def build_millennium_tree():
    m = JavaModel("millennium_tree_staff", 64, 64)
    # ねじれた生きた枝の柄（少しずつ左右にずれる）
    m.box("root_foot", (7.2, -8.8, 7.2), (8.8, -7.2, 8.8), "livewood", tags={"grain": 1})
    for k, (y0, y1, dx) in enumerate(((-7.2, -3.0, 0.0), (-3.0, 0.0, 0.25), (6.0, 10.0, -0.2), (10.0, 14.2, 0.15))):
        m.box(f"shaft_{k}", (7.55 + dx, y0, 7.55), (8.45 + dx, y1, 8.45), "livewood", tags={"grain": 1})
    m.box("grip", (7.3, 0.0, 7.3), (8.7, 6.0, 8.7), "leaf", tags={"wrap": True})
    m.box("knot", (8.45, 8.0, 7.7), (9.0, 8.9, 8.3), "livewood")
    # 先：4本の枝が外へ開いて上へ曲がり、光る種を抱く
    m.box("crown", (7.0, 14.2, 7.0), (9.0, 15.6, 9.0), "livewood", tags={"grain": 1})
    m.box("branch_l", (5.4, 15.4, 7.6), (6.4, 20.6, 8.4), "livewood", rot=((5.9, 15.4, 8.0), "z", 22.5), tags={"grain": 1})
    m.box("branch_r", (9.6, 15.4, 7.6), (10.6, 20.6, 8.4), "livewood", rot=((10.1, 15.4, 8.0), "z", -22.5), tags={"grain": 1})
    m.box("branch_f", (7.6, 15.4, 5.4), (8.4, 20.2, 6.4), "livewood", rot=((8.0, 15.4, 5.9), "x", -22.5), tags={"grain": 1})
    m.box("branch_b", (7.6, 15.4, 9.6), (8.4, 20.2, 10.6), "livewood", rot=((8.0, 15.4, 10.1), "x", 22.5), tags={"grain": 1})
    glow(m, "seed", (6.6, 17.2, 6.6), (9.4, 20.4, 9.4), "seed_glow")
    glow(m, "seed_top", (7.3, 20.4, 7.3), (8.7, 21.2, 8.7), "seed_glow")
    # 若葉（枝先）と、まわりを飛ぶ小さな光
    m.box("leaf_l", (2.4, 20.0, 7.7), (4.6, 21.0, 8.3), "leaf")
    m.box("leaf_r", (11.4, 20.2, 7.7), (13.6, 21.2, 8.3), "leaf")
    m.box("leaf_top", (7.7, 21.2, 7.2), (8.3, 23.2, 8.8), "leaf")
    glow(m, "firefly_0", (3.6, 23.0, 7.7), (4.2, 23.6, 8.3), "seed_glow")
    glow(m, "firefly_1", (12.0, 17.0, 7.7), (12.6, 17.6, 8.3), "seed_glow")
    glow(m, "firefly_2", (10.2, 24.0, 7.7), (10.8, 24.6, 8.3), "seed_glow")
    return m


# ================================================================ 3 潮鳴りの弓（アーチャー）

def build_tide_chime():
    m = JavaModel("tide_chime_bow", 64, 64)
    m.box("grip", (6.9, 0, 7.1), (8.9, 5, 8.9), "tidebronze", tags={"bands": 2})
    # 握りの横（弦の反対）の小さな水門のハンドル
    m.box("valve_hub", (5.6, 2.0, 7.6), (6.9, 3.0, 8.4), "tidebronze")
    m.box("valve_h", (4.4, 2.25, 7.75), (5.6, 2.75, 8.25), "verdigris")
    m.box("valve_v", (4.75, 1.0, 7.75), (5.25, 4.0, 8.25), "verdigris")
    glow(m, "valve_core", (4.7, 2.2, 7.6), (5.3, 2.8, 8.4), "water_glow")
    segs = ((5, 9, 8.0), (9, 13, 8.6), (13, 16.5, 9.4), (16.5, 19, 10.3))
    for k, (y0, y1, x) in enumerate(segs):
        m.box(f"upper_{k}", (x - 0.8, y0, 7.2), (x + 0.8, y1, 8.8), "tidebronze", tags={"bands": 3})
        m.box(f"lower_{k}", (x - 0.8, -y1 + 5, 7.2), (x + 0.8, -y0 + 5, 8.8), "tidebronze", tags={"bands": 3})
        glow(m, f"water_up_{k}", (x - 1.05, y0 + 0.3, 7.6), (x - 0.8, y1 - 0.3, 8.4), "water_glow")
        glow(m, f"water_dn_{k}", (x - 1.05, -y1 + 5.3, 7.6), (x - 0.8, -y0 + 4.7, 8.4), "water_glow")
        m.box(f"rivet_up_{k}", (x - 0.95, y0 - 0.25, 7.05), (x + 0.95, y0 + 0.35, 8.95), "verdigris")
        m.box(f"rivet_dn_{k}", (x - 0.95, -y0 + 4.65, 7.05), (x + 0.95, -y0 + 5.25, 8.95), "verdigris")
    # 弭：管の口と光るしずく
    m.box("tip_top", (9.7, 19, 7.3), (11.3, 20.4, 8.7), "verdigris")
    glow(m, "drop_top", (10.1, 20.4, 7.6), (10.9, 21.4, 8.4), "water_glow")
    m.box("tip_bottom", (9.7, -15.4, 7.3), (11.3, -14, 8.7), "verdigris")
    glow(m, "drop_bottom", (10.1, -16.4, 7.6), (10.9, -15.4, 8.4), "water_glow")
    m.box("string", (BOW_STRING_X[0], -14.9, 7.88), (BOW_STRING_X[1], 19.9, 8.12), "mist")
    return m


# ================================================================ 4 聖歌の大鈴（ヒーラー）

def build_hymn_bell():
    m = JavaModel("hymn_great_bell", 64, 64)
    m.box("foot", (7.3, -8.2, 7.3), (8.7, -6.4, 8.7), "gold")
    m.box("shaft", (7.6, -6.4, 7.6), (8.4, 15.0, 8.4), "marble", tags={"grain": 1})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "cloth", tags={"wrap": True})
    for k, y in enumerate((-4.0, 7.0, 11.5)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.75, 8.7), "gold")
    # 金の三日月の枠（下が開く）と、てっぺんの星
    m.box("arch_l", (4.2, 15.0, 7.5), (5.2, 21.5, 8.5), "gold", tags={"edge": "both"})
    m.box("arch_r", (10.8, 15.0, 7.5), (11.8, 21.5, 8.5), "gold", tags={"edge": "both"})
    m.box("arch_top", (4.2, 21.5, 7.5), (11.8, 22.5, 8.5), "gold")
    m.box("arch_base", (6.8, 14.4, 7.2), (9.2, 15.4, 8.8), "gold")
    glow(m, "star", (7.4, 22.5, 7.6), (8.6, 23.9, 8.4), "violet_glow")
    m.box("star_h", (6.8, 22.9, 7.7), (9.2, 23.5, 8.3), "gold")
    # 大きな金の鈴（段の形）と、紫に光る舌
    m.box("hanger", (7.6, 20.4, 7.6), (8.4, 21.5, 8.4), "gold")
    m.box("bell_top", (6.9, 19.4, 6.9), (9.1, 20.4, 9.1), "gold")
    m.box("bell_mid", (6.3, 17.6, 6.3), (9.7, 19.4, 9.7), "gold", tags={"edge": "both"})
    m.box("bell_low", (5.8, 16.4, 5.8), (10.2, 17.6, 10.2), "gold")
    m.box("bell_lip", (5.5, 15.8, 5.5), (10.5, 16.4, 10.5), "gold")
    glow(m, "clapper", (7.5, 14.9, 7.5), (8.5, 15.8, 8.5), "violet_glow")
    # 左右の音の羽（光る羽根3枚ずつ。上の羽根ほど長く、外へ開く）
    for side, s in (("l", -1), ("r", 1)):
        x = 4.2 if s < 0 else 11.8
        for k, (y, length, angle) in enumerate(((19.6, 3.6, 45.0), (18.4, 3.0, 22.5), (17.2, 2.2, 22.5))):
            x0, x1 = (x - length, x) if s < 0 else (x, x + length)
            glow(m, f"wing_{side}_{k}", (x0, y, 7.85), (x1, y + 0.8, 8.15), "violet_glow",
                 rot=((x, y + 0.4, 8.0), "z", s * angle))   # z の正の向きは前から見て左回り：外のはしを上げる
    return m


# ================================================================ 5 翠晶の誓剣（ナイトの片手剣）

def build_jade_oath():
    m = JavaModel("jade_oath_sword", 64, 64)
    glow(m, "pommel", (7.3, -3.6, 7.3), (8.7, -2.2, 8.7), "jade_glow")
    m.box("pommel_cap", (7.1, -2.2, 7.1), (8.9, -1.6, 8.9), "bronze")
    m.box("grip", (7.4, -1.6, 7.4), (8.6, 3.6, 8.6), "cloth", tags={"wrap": True})
    m.box("guard", (4.6, 3.6, 7.3), (11.4, 4.8, 8.7), "whitestone", tags={"edge": "both"})
    m.box("guard_l", (4.0, 3.4, 7.2), (4.8, 5.0, 8.8), "bronze")
    m.box("guard_r", (11.2, 3.4, 7.2), (12.0, 5.0, 8.8), "bronze")
    # 刃：白い石（格子の溝）・まん中の翠の光るすじ・斜めに断った先
    m.box("blade", (6.6, 4.8, 7.55), (9.4, 19.6, 8.45), "whitestone", tags={"edge": "both", "lattice": 3})
    glow(m, "inlay", (7.75, 5.4, 7.4), (8.25, 18.4, 8.6), "jade_glow")
    m.box("tip_0", (6.9, 19.6, 7.6), (9.1, 21.0, 8.4), "whitestone", tags={"edge": "both"})
    m.box("tip_1", (7.4, 21.0, 7.7), (8.6, 22.2, 8.3), "whitestone")
    m.box("tip_2", (7.75, 22.2, 7.8), (8.25, 23.0, 8.2), "whitestone")
    # 刃の根もとを囲んで浮かぶ、翠の結晶の環（8つ）
    cx, cy, r = 8.0, 8.4, 3.4
    for k in range(8):
        a = 2 * math.pi * k / 8
        x = cx + r * math.cos(a)
        y = cy + r * math.sin(a)
        glow(m, f"halo_{k}", (x - 0.45, y - 0.45, 7.55), (x + 0.45, y + 0.45, 8.45), "jade_glow")
    return m


# ================================================================ 6 雪灯の拳甲（モンク。腕の座標）

def build_snowlight():
    m = JavaModel("snowlight_fists", 64, 64)
    m.box("fur_cuff", (5.0, 3.6, 5.0), (11.0, 5.0, 11.0), "fur")
    m.box("sleeve", (5.4, 5.0, 5.4), (10.6, 8.0, 10.6), "redleather", tags={"bands": 3})
    # 斜めに巻いた金の鎖（輪を互い違いに）
    for k, y in enumerate((5.2, 5.9, 6.6, 7.3)):
        x = 5.25 + k * 1.25
        m.box(f"chain_f_{k}", (x, y, 5.15), (x + 1.0, y + 0.6, 5.4), "gold")
        m.box(f"chain_b_{k}", (10.75 - k * 1.25 - 1.0, y, 10.6), (10.75 - k * 1.25, y + 0.6, 10.85), "gold")
    m.box("fist", (5.5, 8.0, 5.5), (10.5, 11.0, 10.5), "redleather", tags={"edge": "both"})
    m.box("knuckle_plate", (5.4, 9.4, 5.0), (10.6, 11.2, 5.6), "fsilver", tags={"edge": "both"})
    for k, x in enumerate((5.8, 7.45, 9.1)):
        m.box(f"stud_{k}", (x, 9.8, 4.7), (x + 1.1, 10.8, 5.0), "fsilver")
    # 甲の金の枠と、光る雪灯の結晶
    m.box("lamp_frame", (6.6, 8.3, 10.5), (9.4, 10.9, 11.1), "gold")
    glow(m, "lamp", (7.0, 8.7, 10.9), (9.0, 10.5, 11.4), "lamp_glow")
    m.box("lamp_cap", (7.3, 10.9, 10.6), (8.7, 11.4, 11.2), "gold")
    return m


# ================================================================ 7 鏡月の双刃（ローグ）

def build_mirror_moon():
    m = JavaModel("mirror_moon_daggers", 64, 64)
    glow(m, "pommel", (7.4, -3.0, 7.4), (8.6, -1.8, 8.6), "prism_glow")
    m.box("pommel_cap", (7.2, -1.8, 7.2), (8.8, -1.3, 8.8), "mirror")
    m.box("grip", (7.35, -1.3, 7.35), (8.65, 4.0, 8.65), "night", tags={"wrap": True})
    m.box("guard", (5.6, 4.0, 7.2), (10.4, 5.0, 8.8), "night", tags={"edge": "both"})
    glow(m, "guard_gem", (7.5, 4.1, 7.0), (8.5, 4.9, 9.0), "prism_glow")
    # 三日月に反った鏡の刃（同じ幅の帯が左へ反って、先で右へ戻る）と、内のふち（右）のプリズムの光
    n = 9
    for k in range(n):
        y0 = 5.0 + k * 1.5
        y1 = y0 + 1.5
        t = (k + 0.5) / n
        xc = 8.0 - 2.4 * math.sin(math.pi * t)
        half = 1.05 if k < n - 2 else (0.75 if k == n - 2 else 0.45)
        m.box(f"blade_{k}", (xc - half, y0, 7.65), (xc + half, y1, 8.35), "mirror", tags={"edge": "both"})
        if k < n - 1:
            glow(m, f"prism_{k}", (xc + half - 0.3, y0 + 0.15, 7.55), (xc + half, y1 - 0.15, 8.45), "prism_glow")
    m.box("back_spur", (5.3, 5.0, 7.75), (6.0, 6.4, 8.25), "mirror")
    return m


# ================================================================ 8 静湖の真珠杖（ウィザード）

def build_still_lake():
    m = JavaModel("still_lake_pearl_staff", 64, 64)
    m.box("foot", (7.35, -8.4, 7.35), (8.65, -6.6, 8.65), "silentice")
    m.box("shaft", (7.6, -6.6, 7.6), (8.4, 15.0, 8.4), "silentice", tags={"grain": 1})
    m.box("grip", (7.25, 0, 7.25), (8.75, 6, 8.75), "mist", tags={"wrap": True})
    for k, y in enumerate((-5.0, 7.0, 12.4)):
        m.box(f"ring_{k}", (7.3, y, 7.3), (8.7, y + 0.6, 8.7), "pearl")
    m.box("collar", (6.9, 15.0, 6.9), (9.1, 15.8, 9.1), "silentice")
    # 波紋の環（八角。まっすぐな4本と、45度に回した角の4本。光る）
    for name, frm, to in (("ring_bottom", (6.2, 15.8, 7.75), (9.8, 16.3, 8.25)), ("ring_top", (6.2, 23.7, 7.75), (9.8, 24.2, 8.25)),
                          ("ring_l", (4.1, 17.9, 7.75), (4.6, 22.1, 8.25)), ("ring_r", (11.4, 17.9, 7.75), (11.9, 22.1, 8.25))):
        glow(m, name, frm, to, "ripple_glow")
    for name, cx, cy, ang in (("corner_bl", 5.15, 16.85, -45.0), ("corner_br", 10.85, 16.85, 45.0),
                              ("corner_tl", 5.15, 23.15, 45.0), ("corner_tr", 10.85, 23.15, -45.0)):
        glow(m, name, (cx - 1.5, cy - 0.25, 7.75), (cx + 1.5, cy + 0.25, 8.25), "ripple_glow", rot=((cx, cy, 8.0), "z", ang))
    # 浮かぶ大きな真珠
    m.box("pearl", (6.5, 18.5, 6.5), (9.5, 21.5, 9.5), "pearl")
    m.box("pearl_glint", (6.9, 20.6, 6.4), (7.6, 21.2, 6.6), "pearl")
    # 垂れるひげの光の糸（左右に2本ずつ）
    glow(m, "whisker_l0", (3.6, 12.0, 7.9), (3.9, 16.0, 8.1), "ripple_glow", rot=((3.75, 16.0, 8.0), "z", -22.5))
    glow(m, "whisker_l1", (3.0, 8.6, 7.9), (3.3, 12.2, 8.1), "ripple_glow")
    glow(m, "whisker_r0", (12.1, 12.0, 7.9), (12.4, 16.0, 8.1), "ripple_glow", rot=((12.25, 16.0, 8.0), "z", 22.5))
    glow(m, "whisker_r1", (12.7, 8.6, 7.9), (13.0, 12.2, 8.1), "ripple_glow")
    m.box("finial", (7.6, 24.2, 7.6), (8.4, 25.2, 8.4), "pearl")
    return m


# ================================================================ 持ち物の絵（16×16）

def C(h):
    return hexc(h)


def icon_earth_horn():
    p = Pixel()
    # 刃（左下→右上の帯）：石・明るいふち・琥珀の筋
    p.diag_band(6, 14, lambda t: [C("#C4BDA5"), C("#8E866F"), C("#FFBE48") if t % 3 else C("#FFDC8A"), C("#766F5B"),
                                  C("#5C5646")], dx0=-2)
    x, y = p.axis(15)
    p.put(x - 1, y, C("#C4BDA5"))
    p.put(x, y, C("#A9A189"))
    p.cross(4, 3, C("#8E866F"), C("#5C5646"))      # 鍔
    for (px, py), col in (((1, 8), "#EDE3CB"), ((0, 7), "#FAF5E8"), ((1, 9), "#C4B494"),     # 左の角
                          ((7, 14), "#EDE3CB"), ((8, 15), "#FAF5E8"), ((6, 14), "#C4B494")):  # 右の角
        p.put(px, py, C(col))
    for t in (1, 2, 3):
        x, y = p.axis(t)
        p.put(x, y, C("#702C26") if t % 2 else C("#58201C"))
        p.put(x + 1, y, C("#401614"))
    x, y = p.axis(0)
    p.put(x, y, C("#FFBE48"))
    return p


def icon_millennium_tree():
    p = Pixel()
    for t in range(0, 10):
        x, y = p.axis(t)
        p.put(x, y, C("#7E5A36") if t % 3 else C("#664628"))
        p.put(x + 1, y, C("#4E331C"))
        if t in (3, 4, 5):
            p.put(x, y, C("#56A842"))
            p.put(x + 1, y, C("#2E6C24"))
    head = {(11, 1): "#3E8A30", (13, 0): "#56A842", (15, 2): "#3E8A30", (10, 4): "#4E331C", (14, 4): "#4E331C",
            (11, 3): "#7E5A36", (13, 5): "#7E5A36", (12, 2): "#CDEA70", (13, 2): "#EAF8A8", (12, 3): "#A6D044",
            (13, 3): "#CDEA70", (14, 3): "#7EAE2A", (12, 4): "#7EAE2A", (9, 1): "#EAF8A8", (15, 5): "#EAF8A8"}
    for (px, py), col in head.items():
        p.put(px, py, C(col))
    return p


def icon_tide_chime():
    p = Pixel()
    rows = [
        "................",
        ".......bBw......",
        ".........Bw.....",
        "..........Bw....",
        "...s.......Bw...",
        "....s......Bw...",
        ".....s......Bw..",
        "......s.....Bw..",
        ".......s...vBw..",
        "........s..VBw..",
        ".........s..Bw..",
        "..........s.Bw..",
        "...........sBw..",
        "...........Bw...",
        ".........bBw....",
        "................",
    ]
    pal = {"b": "#583C1E", "B": "#9C6E36", "w": "#66B8F2", "s": "#DEE4EA", "v": "#559A82", "V": "#E4F6FF"}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, C(pal[ch]))
    return p


def icon_hymn_bell():
    p = Pixel()
    for t in range(0, 9):
        x, y = p.axis(t)
        if t in (3, 4, 5):
            p.put(x, y, C("#D6D2C5"))
            p.put(x + 1, y, C("#A7A294"))
        else:
            p.put(x, y, C("#DDD9CF") if t % 2 else C("#C6C1B6"))
            p.put(x + 1, y, C("#ABA69C"))
    head = {
        (9, 0): "#F4CE68", (10, 0): "#DEB03C", (11, 0): "#F4CE68", (12, 0): "#B88CF4", (13, 0): "#F4CE68", (14, 0): "#DEB03C",
        (15, 0): "#F4CE68", (9, 1): "#BC8C22", (15, 1): "#BC8C22", (9, 2): "#BC8C22", (15, 2): "#BC8C22",
        (12, 1): "#DEB03C", (11, 2): "#F4CE68", (12, 2): "#FFEBA8", (13, 2): "#DEB03C",
        (10, 3): "#F4CE68", (11, 3): "#FFEBA8", (12, 3): "#F4CE68", (13, 3): "#DEB03C", (14, 3): "#BC8C22",
        (10, 4): "#DEB03C", (11, 4): "#F4CE68", (12, 4): "#DEB03C", (13, 4): "#BC8C22", (14, 4): "#946814",
        (12, 5): "#B88CF4", (7, 2): "#DABCFF", (8, 3): "#B88CF4",
    }
    for (px, py), col in head.items():
        p.put(px, py, C(col))
    return p


def icon_jade_oath():
    p = Pixel()
    p.diag_band(6, 14, lambda t: [C("#F7F5EF"), C("#8CE8C3") if t % 2 else C("#4FCC9E"), C("#CDC8BD"), C("#8E887D")], dx0=-1)
    x, y = p.axis(15)
    p.put(x, y, C("#E5E1D8"))
    p.cross(4, 2, C("#E5E1D8"), C("#8E887D"))
    for t in (1, 2, 3):
        x, y = p.axis(t)
        p.put(x, y, C("#E7E4DA") if t % 2 else C("#C0BBAD"))
        p.put(x + 1, y, C("#8A857A"))
    x, y = p.axis(0)
    p.put(x, y, C("#4FCC9E"))
    # 刃の根もとを囲む翠の環
    cx, cy = p.axis(6)
    for (dx, dy) in ((-2, -1), (-1, -3), (2, -2), (3, 0), (1, 2), (-2, 1)):
        p.put(cx + dx, cy + dy, C("#8CE8C3"))
    return p


def icon_snowlight():
    rows = [
        "................",
        "................",
        "...gGgGgGgGg....",
        "...SsSsSsSsSs...",
        "...rRRRRRRRRr...",
        "...rRRgLLgRRr...",
        "...rRRgllgRRr...",
        "...rRRRggRRRr...",
        "...GrRRrRRrRG...",
        "...rGRRrRRrGr...",
        "...rRGRrRRGRr...",
        "...FfFfFfFfFf...",
        "...fFfFfFfFfF...",
        "................",
        "................",
        "................",
    ]
    pal = {"g": "#BC8C22", "G": "#F4CE68", "S": "#E6EDF4", "s": "#8C9AAE", "r": "#58201C", "R": "#8A3A30", "L": "#FFE29A",
           "l": "#FFC05A", "F": "#F9FAFB", "f": "#C7CCD2"}
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, C(pal[ch]))
    return p


def icon_mirror_moon():
    rows = [
        "................",
        "................",
        "...M.........M..",
        "...mM.......Mm..",
        "...mMp.....pMm..",
        "...mMp.....pMm..",
        "....mMp...pMm...",
        ".....mMppMm.....",
        "......NcNc......",
        ".....NcpNcN.....",
        "......nk.kn.....",
        ".....nK...Kn....",
        "....nK.....Kn...",
        "...p.........p..",
        "................",
        "................",
    ]
    pal = {"M": "#FFFFFF", "m": "#9CA6BA", "p": "#8FE6F4", "N": "#323A5E", "c": "#C8F8FF", "n": "#262D4A", "k": "#424C74",
           "K": "#1C2138"}
    p = Pixel()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                p.put(x, y, C(pal[ch]))
    return p


def icon_still_lake():
    p = Pixel()
    for t in range(0, 9):
        x, y = p.axis(t)
        if t in (3, 4, 5):
            p.put(x, y, C("#DEE4EA"))
            p.put(x + 1, y, C("#A2ACB8"))
        else:
            p.put(x, y, C("#A8B4C0") if t % 2 else C("#8C98A6"))
            p.put(x + 1, y, C("#707C8A"))
    head = {}
    for k in range(10, 16):
        head[(k, 0)] = "#8ADCEA"
        head[(k, 6)] = "#3EA0B8"
    for k in range(0, 7):
        head[(10, k)] = "#5CC2D6"
        head[(15, k)] = "#3EA0B8"
    for corner in ((10, 0), (15, 0), (10, 6), (15, 6)):   # 八角の環（角を落とす）
        head.pop(corner, None)
    head.update({(12, 2): "#FFFFFF", (13, 2): "#F2F0EA", (12, 3): "#E4E2DA", (13, 3): "#D2D0C8", (12, 4): "#BEBCB4",
                 (13, 4): "#D2D0C8", (11, 3): "#E4E2DA", (14, 3): "#BEBCB4", (9, 7): "#8ADCEA", (8, 9): "#5CC2D6"})
    for (px, py), col in head.items():
        p.put(px, py, C(col))
    return p


ICONS = {
    "earth_horn_greatsword": icon_earth_horn, "millennium_tree_staff": icon_millennium_tree, "tide_chime_bow": icon_tide_chime,
    "hymn_great_bell": icon_hymn_bell, "jade_oath_sword": icon_jade_oath, "snowlight_fists": icon_snowlight,
    "mirror_moon_daggers": icon_mirror_moon, "still_lake_pearl_staff": icon_still_lake,
}

WEAPONS = (
    (build_earth_horn, "greatsword", lambda: weapon_display("greatsword"), "大地角の大剣（ナイト・1階）",
     "苔むした大地の石の幅広の刃と琥珀に光る筋\n鍔から反り返る象牙の二本の角"),
    (build_millennium_tree, "staff", lambda: weapon_display("staff"), "千年樹の杖（ウィザード・2階）",
     "ねじれた生きた枝の柄と若葉の握り\n枝が抱く光る種・まわりを飛ぶ小さな光"),
    (build_tide_chime, "bow", lambda: phase6_display("bow"), "潮鳴りの弓（アーチャー・3階）",
     "青銅の管の腕（鋲の帯）と外の光る水の筋\n握りの横の水門のハンドル・弭の光るしずく"),
    (build_hymn_bell, "bell_staff", lambda: phase6_display("bell_staff"), "聖歌の大鈴（ヒーラー・4階）",
     "白い大理石の柄と金の三日月の枠\n大きな金の鈴と紫の舌・左右の音の羽"),
    (build_jade_oath, "sword", lambda: weapon_display("sword"), "翠晶の誓剣（ナイト・5階）",
     "白い石の刃（格子の溝・翠のすじ）\n刃の根もとを囲んで浮かぶ翠の結晶の環"),
    (build_snowlight, "gauntlets", lambda: phase6_display("gauntlets"), "雪灯の拳甲（モンク・6階）",
     "白い毛皮の口・暗い赤の革に巻いた金の鎖\n霜銀のこぶしの板・甲に光る雪灯の結晶"),
    (build_mirror_moon, "daggers", lambda: phase6_display("daggers"), "鏡月の双刃（ローグ・7階）",
     "三日月に反った鏡の刃と内のプリズムの光\n夜の色の鍔と柄・プリズムの柄頭"),
    (build_still_lake, "staff", lambda: weapon_display("staff"), "静湖の真珠杖（ウィザード・8階）",
     "静氷の柄と霧の糸の握り・浮かぶ大きな真珠\n光る波紋の環・垂れるひげの光の糸"),
)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_gacha"
    os.makedirs(out, exist_ok=True)
    rows = []
    for build, kind, disp, label, note in WEAPONS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(GachaPainter(m), frames=FRAMES)
        p = ICONS[m.name]()
        p.outline("#14161A")
        icon = p.icon()
        weapons.export_weapon(repo, m.name, m, frames, icon, kind, CREDIT, display=disp(), frametime=5)
        rows.append({"label": label, "rarity": "ユニーク（祈願の★5）", "note": note, "model": m, "tex": frames[0], "icon": icon})
        print("wrote", m.name, "部品", len(m.elements), "光る部品", sum(1 for e in m.elements if e.glow))
    from setsheet import make_set_sheet
    path = os.path.join(repo, "docs", "gear", "gacha_sheet.png")
    make_set_sheet(path, "祈願の★5：ガチャ限のユニーク武器（1〜8階）",
                   "ユニーク・光る部品は4コマで脈を打つ｜11月27日：階ごとのボスの記憶から形を取った", rows)
    import shutil
    shutil.copy(path, os.path.join(out, "gacha_weapons_sheet.png"))


if __name__ == "__main__":
    main()
