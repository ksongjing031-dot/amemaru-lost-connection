"""9階「氷結した王城」の仕掛けのブロック・王家の肖像画・持ち物の絵・頁の弾・城の人々の像の肌を作る（11月28日【補完】）。

  python3 tools/artgen/make_castle_things.py [リポジトリ] [確認画像の出力先]
  python3 tools/artgen/enhance_icons.py . --only castle_ice,guard_ice,...   # 持ち物の絵（16×16）を32×32に（下の ICONS）

ブロック（JavaModel の立体のモデル。絵は models/block と同じ名前の textures/block/<名前>.png。表は南向き）：
  royal_portrait           王家の肖像画の名札の台座：濃い胡桃の棚に金の縁。まん中に霜銀の名札（空白）。絵は上の壁に、ブロックの
                           実体が描く（client/render/PortraitRenderer。textures/block/castle/portrait_<人>.png）
  royal_portrait_restored  （名前が戻った）名札が金色に光る
  name_plate_stand         名前の札の台：白い石の小さな台に、斜めの霜銀の札受け。札の上に、銀の名札が一枚
  lineage_stele            建国の碑：白い石の碑。表に、淡い青に光る文字の行と、雪の花の紋
  lineage_book             王家の系譜の書：胡桃の書見台に、開いた大きな本（頁のふちに霜）
  queen_letter             女王の書簡：たたんだ手紙と、青い封蝋（雪の花の印）。机の上に置く
  study_latch              書斎の留め金：壁の鉄の板と、引き手の輪
肖像画（96×128。金の額ごと描く。顔は白く抜けている）：
  portrait_<人>.png        名前がない間：絵の上に霜がかかり、色がくすむ
  portrait_<人>_named.png  名前が戻った：霜が溶けて、色が戻る（顔は白いまま。顔のまわりに、うすい金の光）
  portrait_queen.png       女王イゼルナ：白い冠と白い衣。名札はない（名前は声で呼ぶ）
持ち物（16×16）：castle_ice・guard_ice・ice_horseshoe・torn_page・hound_collar・lace_scrap・royal_seal・name_plate・
  queen_letter_item（女王の書簡3通に共通）
弾：textures/entity/page_shard.png（書架霊の頁。16×16）
人の像の肌（64×64。make_village_things.py の person）：royal_guard・noble_man・noble_woman・maid・priest（と _statue）
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from make_iceforest_things import IceThingPainter, write_json  # noqa: E402
from materials import hsh, pick, ramp  # noqa: E402

CREDIT = "AMEMARU: 9階の仕掛け（tools/artgen/make_castle_things.py で生成）"
CASTLE = {
    "castle_stone": ramp("#5E6470", "#767D8A", "#9099A6", "#ACB4C0", "#C6CDD6", "#DDE2E8", "#F2F5F8"),
    "walnut_dark": ramp("#140D08", "#20150D", "#2E1E13", "#3E2A1B", "#513824", "#684A31", "#836142"),
    "gilt": ramp("#5A3E12", "#7C5A1C", "#A07A2A", "#C49C3C", "#DDBB58", "#F0D684", "#FFF0BC"),
    "glow_gilt": ramp("#9A7A2A", "#C09A3A", "#DDBB55", "#F2D77A", "#FBEAA8", "#FFF6D6", "#FFFFFF"),
    "frost_silver": ramp("#4A5668", "#6A788C", "#8C9AAE", "#B0BCCC", "#CFD8E4", "#E6EDF4", "#F8FBFE"),
    "parchment": ramp("#7A6A4E", "#968466", "#B19F80", "#C9B998", "#DCCFB2", "#ECE3CC", "#F8F3E6"),
    "ink": ramp("#0E1018", "#161A26", "#1E2434", "#283044", "#343E56", "#424E6A", "#55627E"),
    "wax_blue": ramp("#0E1C46", "#162A62", "#20397E", "#2C4A9A", "#3E60B2", "#587ACA", "#7C9ADE"),
    "glow_script": ramp("#2E6E96", "#3E86AE", "#58A2C6", "#7CBCDA", "#A6D6EA", "#D2ECF6", "#FFFFFF"),
    "iron_dark": ramp("#121418", "#1C1F24", "#272B32", "#343942", "#444A54", "#58606C", "#717A88"),
    "royal_blue": ramp("#0C1430", "#121E46", "#1A2A5E", "#243878", "#304890", "#425CA8", "#5A76C0"),
}


class CastleThingPainter(IceThingPainter):
    def paint_castle_stone(self, c):
        tone = self.std(c, "castle_stone", base=4.0, noise=0.2, blob=0.3, up=1.1, down=1.0, specks=0.02)
        tone += self.grooves(c)
        if c.el.tags.get("script") and c.n[2] > 0.6:
            # 碑の表：淡い青に光る文字の行（短い棒の並び）と、上の雪の花の紋
            u = (c.i + 0.5) / c.W
            v = (c.j + 0.5) / c.H
            row = int(v * 9)
            if 0.12 < u < 0.88 and 2 <= row <= 7 and (int(v * 18) % 2 == 0):
                if hsh(int(u * 14), row, 61) < 0.72:
                    return pick(CASTLE["glow_script"], 4.2 + (self.tex(c, 62) - 0.5) * 0.6)
            du, dv = u - 0.5, v - 0.11
            r = math.hypot(du, dv)
            if r < 0.09 and (abs(du) < 0.015 or abs(dv) < 0.015 or abs(abs(du) - abs(dv)) < 0.015):
                return pick(CASTLE["glow_script"], 4.6)
        return pick(CASTLE["castle_stone"], tone)

    def paint_walnut_dark(self, c):
        tone = self.std(c, "walnut_dark", base=3.4, noise=0.25, blob=0.2, up=1.2, down=1.0)
        if c.el.tags.get("grain") and (c.i + int(hsh(c.j, 0, 63) * 3)) % 4 == 0:
            tone -= 0.6
        return pick(CASTLE["walnut_dark"], tone)

    def paint_gilt(self, c):
        tone = 4.0 + self.bevel(c, up=1.4, down=1.0) + (self.tex(c, 64) - 0.5) * 0.4
        if (c.i + c.j) % 5 == 0:
            tone += 0.6
        return pick(CASTLE["gilt"], tone)

    def paint_glow_gilt(self, c):
        tone = 4.6 + self.bevel(c, up=1.0, down=0.4)
        return pick(CASTLE["glow_gilt"], tone)

    def paint_glow_script(self, c):
        tone = 4.6 + self.bevel(c, up=1.0, down=0.4)
        return pick(CASTLE["glow_script"], tone)

    def paint_plate_blank(self, c):
        """空白の名札：霜銀に、白く抜けた名前の帯"""
        tone = 4.2 + self.bevel(c, up=1.2, down=1.0)
        if c.n[2] > 0.6 and 1 <= c.i < c.W - 1 and 1 <= c.j < c.H - 1:
            return pick(CASTLE["frost_silver"], 5.6)
        return pick(CASTLE["frost_silver"], tone)

    def paint_plate_named(self, c):
        """名前の戻った名札：金の板に、暗い刻みの文字"""
        tone = 4.6 + self.bevel(c, up=1.0, down=0.6)
        if c.n[2] > 0.6 and 1 <= c.i < c.W - 1 and c.j == c.H // 2 and c.i % 2 == 0:
            return pick(CASTLE["gilt"], 2.0)
        return pick(CASTLE["glow_gilt"], tone)

    def paint_parchment(self, c):
        tone = self.std(c, "parchment", base=4.6, noise=0.2, blob=0.3, up=0.6, down=0.8)
        if c.el.tags.get("lines") and c.n[1] > 0.6:
            # 開いた頁の上：細いインクの行（1つおき。ところどころ途切れる）
            if 0 < c.i < c.W - 1 and c.j % 2 == 1 and 0 < c.j < c.H - 1 and hsh(c.i, c.j, 65) < 0.75:
                return pick(CASTLE["parchment"], 2.2)
        if c.el.tags.get("frost") and (c.i + c.j) % 3 == 0 and min(c.i, c.j, c.W - 1 - c.i, c.H - 1 - c.j) <= 1:
            return pick(CASTLE["frost_silver"], 5.8)
        return pick(CASTLE["parchment"], tone)

    def paint_wax_blue(self, c):
        tone = 3.6 + self.bevel(c, up=1.6, down=0.8)
        if c.n[1] > 0.6 and (c.i == c.W // 2 or c.j == c.H // 2):
            tone += 1.4   # 雪の花の印
        return pick(CASTLE["wax_blue"], tone)

    def paint_iron_dark(self, c):
        tone = self.std(c, "iron_dark", base=3.4, noise=0.25, blob=0.3, up=1.4, down=1.0)
        if c.el.tags.get("rivets") and (c.i in (1, c.W - 2)) and (c.j in (1, c.H - 2)):
            tone += 2.0
        return pick(CASTLE["iron_dark"], tone)

    def paint_royal_blue(self, c):
        tone = self.std(c, "royal_blue", base=3.6, noise=0.2, blob=0.3, up=1.0, down=1.0)
        return pick(CASTLE["royal_blue"], tone)


def box(m, name, frm, to, mat, **kw):
    return m.box(name, frm, to, mat, **kw)


# ---------------------------------------------------------------- ブロック（立体。表は南 +z、壁は北 z=0）

def build_royal_portrait(restored):
    m = JavaModel("royal_portrait_restored" if restored else "royal_portrait", 64, 64)
    box(m, "back", (1, 2, 0), (15, 12, 1), "walnut_dark", tags={"grain": True})
    box(m, "ledge", (0.5, 10.5, 0), (15.5, 12, 3.5), "walnut_dark", tags={"grain": True})
    box(m, "ledge_trim", (0.5, 10.8, 3.5), (15.5, 11.6, 3.8), "gilt")
    box(m, "corbel_l", (2, 6, 0), (4, 10.5, 2.5), "walnut_dark")
    box(m, "corbel_r", (12, 6, 0), (14, 10.5, 2.5), "walnut_dark")
    box(m, "plate_frame", (4.5, 4, 1), (11.5, 9, 1.6), "gilt")
    box(m, "plate", (5, 4.5, 1.6), (11, 8.5, 2.0), "plate_named" if restored else "plate_blank", glow=restored)
    box(m, "drop", (7, 2, 1), (9, 4, 1.8), "gilt")
    return m


def build_name_plate_stand():
    m = JavaModel("name_plate_stand", 64, 64)
    box(m, "base", (3, 0, 3), (13, 2, 13), "castle_stone", tags={"grooves": [1.0]})
    box(m, "column", (5.5, 2, 5.5), (10.5, 7, 10.5), "castle_stone")
    box(m, "cap", (4, 7, 4), (12, 8, 12), "castle_stone")
    tilt = ((8, 8, 8), "x", -22.5)
    box(m, "rest", (4, 8, 5), (12, 9, 11), "frost_silver", rot=tilt)
    box(m, "plate", (5, 9, 5.8), (11, 9.5, 10.2), "plate_blank", rot=tilt, glow=True)
    box(m, "lip", (4, 8, 11), (12, 9.8, 11.6), "frost_silver", rot=tilt)
    return m


def build_lineage_stele():
    m = JavaModel("lineage_stele", 64, 64)
    box(m, "plinth", (1, 0, 3), (15, 2, 13), "castle_stone", tags={"grooves": [1.0]})
    box(m, "slab", (2, 2, 5), (14, 14, 11), "castle_stone", tags={"script": True})
    box(m, "crown", (3, 14, 5.5), (13, 15.5, 10.5), "castle_stone")
    box(m, "crown_top", (5.5, 15.5, 6), (10.5, 16, 10), "castle_stone")
    box(m, "flower", (7, 15.4, 7.5), (9, 16.4, 8.5), "glow_script", glow=True)
    return m


def build_lineage_book():
    m = JavaModel("lineage_book", 64, 64)
    box(m, "foot", (4, 0, 4), (12, 1.5, 12), "walnut_dark", tags={"grain": True})
    box(m, "post", (6.5, 1.5, 6.5), (9.5, 10, 9.5), "walnut_dark", tags={"grain": True})
    tilt = ((8, 10.5, 8), "x", 22.5)
    box(m, "desk", (2.5, 10, 3), (13.5, 11, 13), "walnut_dark", rot=tilt)
    box(m, "cover", (2.5, 11, 3.2), (13.5, 11.6, 12.8), "royal_blue", rot=tilt)
    box(m, "page_l", (2.8, 11.6, 3.6), (7.9, 12.6, 12.4), "parchment", tags={"lines": True, "frost": True}, rot=tilt)
    box(m, "page_r", (8.1, 11.6, 3.6), (13.2, 12.6, 12.4), "parchment", tags={"lines": True, "frost": True}, rot=tilt)
    box(m, "spine", (7.7, 11.6, 3.6), (8.3, 12.2, 12.4), "royal_blue", rot=tilt)
    box(m, "ribbon", (7.6, 10.2, 12.4), (8.4, 11.8, 14.2), "wax_blue", rot=tilt)
    return m


def build_queen_letter():
    m = JavaModel("queen_letter", 64, 64)
    turn = ((8, 0, 8), "y", 22.5)
    box(m, "letter", (3.5, 0, 4.5), (12.5, 0.8, 11.5), "parchment", rot=turn)
    box(m, "flap", (4, 0.8, 5), (12, 1.1, 8.5), "parchment", rot=turn)
    box(m, "seal", (7, 0.8, 7.2), (9.2, 1.5, 9.4), "wax_blue", rot=turn)
    box(m, "ribbon", (3.4, 0.2, 9.5), (12.6, 0.9, 10.3), "wax_blue", rot=turn)
    return m


def build_study_latch():
    m = JavaModel("study_latch", 64, 64)
    box(m, "plate", (4, 3, 0), (12, 13, 1), "iron_dark", tags={"rivets": True})
    box(m, "bar", (7, 4, 1), (9, 12, 2), "iron_dark")
    box(m, "ring_top", (5.5, 4.5, 2), (10.5, 5.5, 3), "frost_silver")
    box(m, "ring_l", (5.5, 2.5, 2), (6.5, 4.5, 3), "frost_silver")
    box(m, "ring_r", (9.5, 2.5, 2), (10.5, 4.5, 3), "frost_silver")
    box(m, "ring_bottom", (5.5, 1.5, 2), (10.5, 2.5, 3), "frost_silver")
    return m


BLOCKS = [lambda: build_royal_portrait(False), lambda: build_royal_portrait(True), build_name_plate_stand, build_lineage_stele,
          build_lineage_book, build_queen_letter, build_study_latch]

# 表が南向き（facing=south で回さない）
SOUTH_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


# ---------------------------------------------------------------- 肖像画（96×128）

# 人: (髪, 衣, 衣の飾り, 背景, 描かれた物)
PEOPLE_ART = {
    "bertram": ("#5A4030", "#5A2A3A", "#C9A03A", "#2A2E4A", "stone"),
    "rosalinde": ("#C8A060", "#E2E6F0", "#8FA8D8", "#2E3A5A", "snowcrown"),
    "edmund": ("#6A4A30", "#2E4A6A", "#C9A03A", "#3A2E28", "map"),
    "sigrid": ("#D8B45A", "#7A3A5A", "#E6C46A", "#2A2440", "harp"),
    "halvard": ("#4A3A2A", "#3E5A3A", "#C9A03A", "#2E3A30", "hawk"),
    "albrecht": ("#2E2A2A", "#2A2E5A", "#B8C8E8", "#141A30", "scope"),
    "oswin": ("#6A5038", "#22336A", "#C9A03A", "#2A2A3A", "shield"),
    "elvira": ("#A06A40", "#5A6E9A", "#F2F0EA", "#2E3446", "lily"),
    "liselotte": ("#C89A50", "#6A8AC2", "#F2F0EA", "#283448", "bird"),
    "queen": ("#EDEFF5", "#E8ECF4", "#B8C8E8", "#1E2440", "crown"),
}


def _frame(img):
    """金の額（6ピクセル。外は暗い縁、内は明るい縁。四隅に飾り）"""
    d = ImageDraw.Draw(img)
    W, H = img.size
    golds = ["#4A3410", "#7C5A1C", "#C49C3C", "#E8CB70", "#C49C3C", "#A07A2A"]
    for k, col in enumerate(golds):
        d.rectangle([k, k, W - 1 - k, H - 1 - k], outline=col)
    for (x, y) in ((3, 3), (W - 4, 3), (3, H - 4), (W - 4, H - 4)):
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill="#F0D684", outline="#7C5A1C")
    # 上のまん中の、雪の花の飾り
    cx = W // 2
    d.ellipse([cx - 4, 0, cx + 4, 7], fill="#F0D684", outline="#7C5A1C")


def _figure(img, who, named):
    hair, robe, trim, back, thing = PEOPLE_ART[who]
    d = ImageDraw.Draw(img)
    W, H = img.size
    # 背景：上から下へ暗くなる幕と、うしろの柱の影
    top = np.array(Image.new("RGB", (1, 1), back).getpixel((0, 0)), dtype=float)
    for y in range(6, H - 6):
        t = (y - 6) / (H - 12)
        col = tuple(int(v) for v in top * (1.15 - 0.45 * t))
        d.line([(6, y), (W - 7, y)], fill=col)
    d.rectangle([10, 10, 18, H - 30], fill=tuple(int(v) for v in top * 0.75))
    d.rectangle([W - 19, 10, W - 11, H - 30], fill=tuple(int(v) for v in top * 0.75))
    # 体（肩から下。衣）
    cx = W // 2
    d.polygon([(cx - 30, H - 7), (cx - 26, 82), (cx - 12, 70), (cx + 12, 70), (cx + 26, 82), (cx + 30, H - 7)], fill=robe)
    d.polygon([(cx - 4, 70), (cx + 4, 70), (cx + 6, H - 7), (cx - 6, H - 7)], fill=trim)
    d.line([(cx - 12, 70), (cx - 26, 82)], fill=trim, width=2)
    d.line([(cx + 12, 70), (cx + 26, 82)], fill=trim, width=2)
    # 首と、白く抜けた顔（輪郭だけ、ほんの少し残る）
    d.rectangle([cx - 5, 62, cx + 5, 72], fill="#E8D6C8" if who != "queen" else "#F4F0F0")
    d.ellipse([cx - 15, 30, cx + 15, 66], fill=hair)
    d.ellipse([cx - 11, 36, cx + 11, 64], fill="#F7FAFD", outline="#DCE6F0")
    if who == "queen":
        # 長い白い髪と、白い冠
        d.rectangle([cx - 16, 44, cx - 11, 78], fill=hair)
        d.rectangle([cx + 11, 44, cx + 16, 78], fill=hair)
    # 描かれた物
    if thing == "stone":       # 冠と、足もとの大きな礎石
        _crown(d, cx, 30, "#E2C060")
        d.rectangle([cx - 26, H - 26, cx - 4, H - 10], fill="#9AA0A8", outline="#5E6470")
        d.line([(cx - 26, H - 18), (cx - 4, H - 18)], fill="#6E747C")
    elif thing == "snowcrown":  # 雪の花の冠
        for k in range(5):
            x = cx - 12 + k * 6
            d.ellipse([x - 3, 26, x + 3, 32], fill="#F8FBFF", outline="#8FA8D8")
    elif thing == "map":       # 机の地図と、羽ペン
        d.rectangle([cx - 28, H - 28, cx + 28, H - 12], fill="#D8C8A0", outline="#7A6A4E")
        for k in range(4):
            d.line([(cx - 24 + k * 13, H - 25), (cx - 18 + k * 13, H - 15)], fill="#6A8A5A")
        d.line([(cx + 16, H - 34), (cx + 28, H - 14)], fill="#F2F0EA", width=2)
    elif thing == "harp":      # 竪琴（弦は金）
        d.arc([cx + 8, 76, cx + 34, 116], 200, 340, fill="#C49C3C", width=3)
        d.line([(cx + 10, 112), (cx + 32, 112)], fill="#A07A2A", width=2)
        for k in range(5):
            d.line([(cx + 13 + k * 4, 84), (cx + 13 + k * 4, 111)], fill="#F0D684")
    elif thing == "hawk":      # 腕の鷹（目が金）
        d.ellipse([cx + 14, 78, cx + 30, 96], fill="#6A5040")
        d.polygon([(cx + 28, 82), (cx + 34, 84), (cx + 28, 86)], fill="#E0B040")
        d.point((cx + 25, 83), fill="#FFD04A")
        d.rectangle([cx + 12, 94, cx + 30, 98], fill="#8A6A48")
    elif thing == "scope":     # 夜空と、望遠鏡
        for k in range(9):
            d.point((14 + (k * 23) % (W - 30), 14 + (k * 37) % 40), fill="#E8F0FF")
        d.line([(cx - 30, 90), (cx - 8, 76)], fill="#C49C3C", width=4)
        d.ellipse([cx - 33, 88, cx - 27, 94], fill="#2A2E40", outline="#C49C3C")
    elif thing == "shield":    # 王の杖と、うしろの近衛の盾
        for k, x in enumerate((14, W - 26)):
            d.polygon([(x, 84), (x + 12, 84), (x + 12, 100), (x + 6, 108), (x, 100)], fill="#3A5A9A", outline="#B8C8E8")
        d.line([(cx + 20, 64), (cx + 20, H - 10)], fill="#C49C3C", width=3)
        d.ellipse([cx + 16, 58, cx + 24, 66], fill="#E8CB70")
        _crown(d, cx, 30, "#E2C060")
    elif thing == "lily":      # 白い百合の花束
        for k in range(3):
            x = cx - 22 + k * 7
            d.line([(x, 112), (x + 2, 90)], fill="#5A8A4A", width=2)
            d.polygon([(x - 3, 90), (x + 2, 82), (x + 7, 90), (x + 2, 94)], fill="#F8F8F2", outline="#C8C8C0")
    elif thing == "bird":      # 指先の青い小鳥
        d.line([(cx + 10, 100), (cx + 24, 90)], fill="#E8D6C8", width=3)
        d.ellipse([cx + 20, 80, cx + 30, 88], fill="#3A78D0")
        d.polygon([(cx + 30, 83), (cx + 34, 84), (cx + 30, 86)], fill="#E0B040")
    elif thing == "crown":     # 女王：白い冠と、白い衣の雪の花
        _crown(d, cx, 28, "#F4F8FF", outline="#B8C8E8")
        for k in range(3):
            x = cx - 16 + k * 16
            d.ellipse([x - 3, 96, x + 3, 102], fill="#F8FBFF", outline="#B8C8E8")
    if named:
        # 名前が戻った：顔のまわりに、うすい金の光
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse([cx - 22, 24, cx + 22, 74], fill=(255, 226, 150, 90))
        glow = glow.filter(ImageFilter.GaussianBlur(5))
        img.alpha_composite(glow)
        d = ImageDraw.Draw(img)
        d.ellipse([cx - 11, 36, cx + 11, 64], fill="#FBFCFE", outline="#F0D684")


def _crown(d, cx, y, col, outline="#7C5A1C"):
    d.polygon([(cx - 12, y + 6), (cx - 12, y - 2), (cx - 6, y + 3), (cx, y - 5), (cx + 6, y + 3), (cx + 12, y - 2), (cx + 12, y + 6)],
              fill=col, outline=outline)


def _frost(img, amount, seed):
    """霜：色をくすませ、白い結晶の点と、すみの霜の模様をかける"""
    a = np.array(img).astype(float)
    W, H = img.size
    lum = a[..., :3].mean(axis=2, keepdims=True)
    pale = np.array([214, 226, 240], dtype=float)
    a[..., :3] = a[..., :3] * (1 - amount) + (lum * 0.5 + pale * 0.5) * amount
    for y in range(6, H - 6):
        for x in range(6, W - 6):
            edge = min(x - 6, y - 6, W - 7 - x, H - 7 - y)
            r = hsh(x, y, seed)
            if r < 0.035 or (edge < 10 and r < 0.35 * (1 - edge / 10.0)):
                a[y, x, :3] = a[y, x, :3] * 0.3 + np.array([240, 246, 252]) * 0.7
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def portrait(who, named):
    img = Image.new("RGBA", (96, 128), (0, 0, 0, 255))
    _figure(img, who, named)
    if not named:
        img = _frost(img, 0.55, 700 + sum(map(ord, who)))
    _frame(img)
    return img


# ---------------------------------------------------------------- 持ち物の絵（16×16）

def icon_from(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal:
                px[x, y] = Image.new("RGB", (1, 1), pal[ch]).getpixel((0, 0)) + (255,)
    return img


ICONS = {
    # 王城の霜氷：青白い、ぎざぎざの氷のかけら
    "castle_ice": (["................",
                    ".......o........",
                    "......oWo.......",
                    ".....oWHWo......",
                    "....oWHHWbo.....",
                    "...oWHWWbbbo....",
                    "..oWHWWbbbbbo...",
                    "..obWWbbbbBbo...",
                    "...obbbbbBBo....",
                    "...obbbbBBbo....",
                    "....obbBBbo.....",
                    ".....obBbo......",
                    "......obo.......",
                    ".......o........",
                    "................",
                    "................"],
                   {"o": "#2E4A6A", "W": "#E8F2FA", "H": "#FFFFFF", "b": "#9CC4E4", "B": "#6A9AC8"}),
    # 近衛の氷：盾の形に割れた、青い氷
    "guard_ice": (["................",
                   "...oooooooooo...",
                   "...oHWWWWWWbo...",
                   "...oWHWWWWbbo...",
                   "...oWWbBBbbbo...",
                   "...oWWBccBbbo...",
                   "...oWbBccBbbo...",
                   "...oWbbBBbbbo...",
                   "....oWbbbbbo....",
                   "....oWbbbbbo....",
                   ".....oWbbbo.....",
                   "......obbo......",
                   ".......oo.......",
                   "................",
                   "................",
                   "................"],
                  {"o": "#1E2E5A", "W": "#C8DCF0", "H": "#FFFFFF", "b": "#6A8EC8", "B": "#3A5A9A", "c": "#E8CB70"}),
    # 氷の蹄鉄
    "ice_horseshoe": (["................",
                       "................",
                       "....oooooooo....",
                       "...oWHWWWWWbo...",
                       "..oWHo....obbo..",
                       "..oWo......obo..",
                       "..oWo......obo..",
                       "..oWo.o..o.obo..",
                       "..oHo......obo..",
                       "..oWo......obo..",
                       "..oWo.o..o.obo..",
                       "..oWbo....obbo..",
                       "...obo....obo...",
                       "...ooo....ooo...",
                       "................",
                       "................"],
                      {"o": "#2E4A6A", "W": "#D8EAF6", "H": "#FFFFFF", "b": "#8EB6DA"}),
    # ほどけた頁：めくれた古い紙。名前のところが白い
    "torn_page": (["................",
                   "...ooooooooo....",
                   "...oPPPPPPPPo...",
                   "...oPkkkPkkPo...",
                   "...oPPPPPPPPo...",
                   "...oPkkWWWkPo...",
                   "...oPPPPPPPPo...",
                   "...oPkkPkkkPo...",
                   "...oPPPPPPPPo...",
                   "...oPkkkkWWPo...",
                   "...oPPPPPPPPo...",
                   "...oPkkPPPdo....",
                   "...oPPPPPPdo....",
                   "...ooooooodo....",
                   "..........oo....",
                   "................"],
                  {"o": "#5A4A30", "P": "#ECE3CC", "k": "#5A6072", "W": "#FFFFFF", "d": "#B19F80"}),
    # 猟犬の青い首輪：青い革の輪と、名札の金具（空白）
    "hound_collar": (["................",
                      "................",
                      ".....oooooo.....",
                      "....obbbbbbo....",
                      "...obBo..oBbo...",
                      "..obBo....oBbo..",
                      "..obo......obo..",
                      "..obo......obo..",
                      "..obo......obo..",
                      "..obBo....oBbo..",
                      "...obBooooBbo...",
                      "....obbsSsbbo...",
                      ".....oosWsoo....",
                      ".......sss......",
                      "................",
                      "................"],
                     {"o": "#121E46", "b": "#2C4A9A", "B": "#5A76C0", "s": "#8C9AAE", "S": "#CFD8E4", "W": "#FFFFFF"}),
    # 前掛けのレース：白いレースの切れ端（ふちが波）
    "lace_scrap": (["................",
                    "................",
                    "..o.o.o.o.o.o...",
                    "..oWoWoWoWoWo...",
                    "..oWWWWWWWWWo...",
                    "..oWgWgWgWgWo...",
                    "..oWWWWWWWWWo...",
                    "..oWWgWWWgWWo...",
                    "..oWWWWWWWWWo...",
                    "..oWgWgWgWgWo...",
                    "..oWWWWWWWWWo...",
                    "..oWoWoWoWoWo...",
                    "..o.o.o.o.o.o...",
                    "................",
                    "................",
                    "................"],
                   {"o": "#8A8E98", "W": "#F8F8F6", "g": "#C8CCD4"}),
    # 王家の封蝋：青い封蝋のかけら（雪の花の印）
    "royal_seal": (["................",
                    "................",
                    ".....oooooo.....",
                    "....obbbbbbo....",
                    "...obBbbbbBbo...",
                    "..obbbbWbbbbo...",
                    "..obbWbWbWbbo...",
                    "..obbbWWWbbbo...",
                    "..obWWWHWWWbo...",
                    "..obbbWWWbbbo...",
                    "..obbWbWbWbbo...",
                    "...obbbWbbbo....",
                    "....obbbbbo.....",
                    ".....ooooo......",
                    "................",
                    "................"],
                   {"o": "#0E1C46", "b": "#2C4A9A", "B": "#587ACA", "W": "#C8D8F4", "H": "#FFFFFF"}),
    # 名前の札：銀の札に、刻まれた名前（細い線）と、上の鎖の輪
    "name_plate": (["................",
                    ".......ss.......",
                    "......s..s......",
                    ".......ss.......",
                    "..ooooooooooo...",
                    "..oSHHHHHHHSo...",
                    "..oHSSSSSSSHo...",
                    "..oHSkkSkkSHo...",
                    "..oHSSkkSSSHo...",
                    "..oHSSSSSSSHo...",
                    "..oSHHHHHHHSo...",
                    "..ooooooooooo...",
                    "................",
                    "................",
                    "................",
                    "................"],
                   {"o": "#4A5668", "S": "#B0BCCC", "H": "#E6EDF4", "k": "#4A5668", "s": "#8C9AAE"}),
    # 女王の書簡：たたんだ手紙と、青い封蝋
    "queen_letter_item": (["................",
                           "................",
                           "................",
                           "..ooooooooooooo.",
                           "..oPPPPPPPPPPPo.",
                           "..oPdPPPPPPPdPo.",
                           "..oPPdPPPPPdPPo.",
                           "..oPPPdPbPdPPPo.",
                           "..oPPPPbBbPPPPo.",
                           "..oPPPPPbPPPPPo.",
                           "..oPPPPPPPPPPPo.",
                           "..oPPPPPPPPPPPo.",
                           "..ooooooooooooo.",
                           "................",
                           "................",
                           "................"],
                          {"o": "#7A6A4E", "P": "#F2ECDC", "d": "#C9B998", "b": "#2C4A9A", "B": "#7C9ADE"}),
}


def page_shard():
    """書架霊の頁（弾）：光るふちの、古い紙。文字の行"""
    rows = ["................",
            "..oooooooooooo..",
            "..oPPPPPPPPPPo..",
            "..oPkkPkkkPPPo..",
            "..oPPPPPPPPPPo..",
            "..oPkkkPkkPPPo..",
            "..oPPPPPPPPPPo..",
            "..oPkPkkkPkkPo..",
            "..oPPPPPPPPPPo..",
            "..oPkkPkkkkPPo..",
            "..oPPPPPPPPPPo..",
            "..oPkkkPPkkPPo..",
            "..oPPPPPPPPPPo..",
            "..oooooooooooo..",
            "................",
            "................"]
    return icon_from(rows, {"o": "#A6D6EA", "P": "#F4EEDC", "k": "#5A7090"})


# ---------------------------------------------------------------- 城の人々（像の肌）

CASTLE_PEOPLE = {
    # 近衛兵：青い外套に、白い近衛の胸当て（tabard）と、銀の兜
    "royal_guard": dict(slim=False, hair="#3A3024", skin="#E4C0A0", top="#2E4A86", pants="#2A3046", shoes="#22242C",
                        tabard="#C9D6E6", hat="helm", hat_c="#B8C2CE"),
    # 貴族の男：紫の上着と金の縁取り、ひげ
    "noble_man": dict(slim=False, hair="#5A4030", skin="#E8C4A2", top="#3A2A5A", pants="#2A2438", shoes="#2A2018", beard="#5A4030",
                      trim="#C9A03A"),
    # 貴婦人：淡い衣と、紫の肩掛け、長い髪
    "noble_woman": dict(slim=True, hair="#C8A060", skin="#F2D6BE", top="#E8E0F0", pants="#D8D0E4", shoes="#6E6A86", shawl="#6E5AA8",
                        trim="#8FA8D8", long_hair=True),
    # 侍女：紺の服に白い前掛け、白い帽子
    "maid": dict(slim=True, hair="#4A3628", skin="#F0D2B8", top="#2A2E3E", pants="#2A2E3E", shoes="#1E2028", apron="#F4F2EE",
                 hat="cap", hat_c="#F4F2EE"),
    # 司祭：白い衣と金の縁取り、白いひげ
    "priest": dict(slim=False, hair="#8A8A8A", skin="#E2BE9E", top="#E8E4D8", pants="#D8D2C4", shoes="#6A5A48", beard="#9A9A9A",
                   trim="#C9A03A"),
}


# ---------------------------------------------------------------- ボスのレア「近衛の誓盾」（ナイトの盾。エピック）

def build_royal_oath_shield():
    """近衛長の塔の大盾を、人が持てる大きさにしたもの。青地に霜銀のふち、表に剣と雪の花の誓いの紋、まん中に光る誓いの石"""
    m = JavaModel("royal_oath_shield_model", 64, 64)
    rows = ((-6.0, 7.0, 6.0), (-8.0, -6.0, 5.0), (-9.5, -8.0, 3.5))
    for k, (y0, y1, half) in enumerate(rows):
        m.box(f"body_{k}", (-half, y0, 1.0), (half, y1, 2.75), "royal_blue")
    m.box("rim_l", (-6.5, -6.0, 0.85), (-6.0, 7.0, 3.0), "frost_silver")
    m.box("rim_r", (6.0, -6.0, 0.85), (6.5, 7.0, 3.0), "frost_silver")
    m.box("rim_top", (-6.5, 7.0, 0.85), (6.5, 7.6, 3.0), "frost_silver")
    m.box("rim_low_l", (-5.5, -8.0, 0.9), (-5.0, -6.0, 2.95), "frost_silver")
    m.box("rim_low_r", (5.0, -8.0, 0.9), (5.5, -6.0, 2.95), "frost_silver")
    m.box("rim_tip", (-3.5, -10.0, 0.9), (3.5, -9.5, 2.95), "frost_silver")
    # 誓いの紋：たての剣と鍔、まわりの雪の花の4つの腕
    m.box("sword", (-0.5, -7.0, 2.75), (0.5, 5.0, 3.15), "frost_silver")
    m.box("sword_guard", (-2.5, 2.5, 2.75), (2.5, 3.2, 3.2), "gilt")
    m.box("sword_pommel", (-0.75, 5.0, 2.75), (0.75, 6.0, 3.2), "gilt")
    for k, (x0, y0, x1, y1) in enumerate(((-4.5, -1.0, -2.5, 0.0), (2.5, -1.0, 4.5, 0.0), (-0.4, -4.5, 0.4, -3.0))):
        m.box(f"flake_{k}", (x0, y0, 2.75), (x1, y1, 3.1), "frost_silver")
    m.box("gem", (-1.0, -1.5, 3.1), (1.0, 0.5, 3.6), "glow_script", glow=True)
    # 裏：握り
    m.box("grip_bracket_top", (-1, 1.5, -3), (1, 2.5, 1), "iron_dark")
    m.box("grip_bracket_low", (-1, -2.5, -3), (1, -1.5, 1), "iron_dark")
    m.box("grip", (-1, -2.5, -4.5), (1, 2.5, -3), "walnut_dark", tags={"grain": True})
    return m


def export_shield(repo):
    import weapons
    from make_gear_ancient_stone import SHIELD_FIRST, SHIELD_BLOCK, SHIELD_GUI
    m = build_royal_oath_shield()
    m.cull_hidden_faces()
    m.pack()
    frames = m.paint(CastleThingPainter(m), frames=1)
    normal, blocking = weapons.shield_display(SHIELD_FIRST, SHIELD_BLOCK, third_scale=0.95, gui=SHIELD_GUI)
    weapons.export_shield(repo, "royal_oath_shield", m, frames, CREDIT, normal, blocking)
    return m, frames[0]


# ---------------------------------------------------------------- 書き出し

def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else None
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    built = []
    for build in BLOCKS:
        m = build()
        m.cull_hidden_faces()
        m.pack()
        tex = m.paint(CastleThingPainter(m), frames=1)[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures/block", m.name + ".png"))
        m.save_json(os.path.join(base, "models/block", m.name + ".json"), "amemaru:block/" + m.name, CREDIT,
                    extra={"parent": "minecraft:block/block", "render_type": "minecraft:cutout"})
        built.append((m, tex))
    # 向き（表は南）。肖像画は名前が戻ると、金の名札のモデル
    portrait_variants = {}
    for f, y in SOUTH_Y.items():
        for restored in (False, True):
            v = {"model": "amemaru:block/royal_portrait" + ("_restored" if restored else "")}
            if y:
                v["y"] = y
            portrait_variants["facing=%s,restored=%s" % (f, "true" if restored else "false")] = v
    write_json(os.path.join(base, "blockstates/royal_portrait.json"), {"variants": portrait_variants})
    for name in ("name_plate_stand", "lineage_stele", "lineage_book", "queen_letter", "study_latch"):
        variants = {}
        for f, y in SOUTH_Y.items():
            v = {"model": "amemaru:block/" + name}
            if y:
                v["y"] = y
            variants["facing=" + f] = v
        write_json(os.path.join(base, "blockstates", name + ".json"), {"variants": variants})
    for name in ("royal_portrait", "name_plate_stand", "lineage_stele", "lineage_book", "queen_letter", "study_latch"):
        write_json(os.path.join(base, "models/item", name + ".json"), {"parent": "amemaru:block/" + name})
    # 肖像画
    pdir = os.path.join(base, "textures/block/castle")
    os.makedirs(pdir, exist_ok=True)
    for who in PEOPLE_ART:
        portrait(who, False).save(os.path.join(pdir, "portrait_%s.png" % who))
        if who != "queen":
            portrait(who, True).save(os.path.join(pdir, "portrait_%s_named.png" % who))
    # 持ち物・弾
    for name, (rows, pal) in ICONS.items():
        icon_from(rows, pal).save(os.path.join(base, "textures/item", name + ".png"))
    page_shard().save(os.path.join(base, "textures/entity/page_shard.png"))
    # 城の人々
    from make_village_things import person
    vdir = os.path.join(base, "textures/entity/village")
    for name, spec in CASTLE_PEOPLE.items():
        person(name, spec, False).save(os.path.join(vdir, name + ".png"))
        person(name, spec, True).save(os.path.join(vdir, name + "_statue.png"))
    shield = export_shield(repo)
    if out:
        check(base, built + [shield], out)
    print("ok：ブロック", len(built), "肖像画", len(PEOPLE_ART), "持ち物", len(ICONS), "人", len(CASTLE_PEOPLE))


def check(base, built, out):
    from render import render_many
    os.makedirs(out, exist_ok=True)
    tiles = []
    for m, tex in built:
        tiles.append(render_many([(m, tex)], yaw=150, pitch=20, scale=9.0, center=(8, 7, 8), size=(200, 200)))
    sheet = Image.new("RGBA", (200 * len(tiles), 200), (44, 52, 64, 255))
    for k, im in enumerate(tiles):
        sheet.alpha_composite(im, (200 * k, 0))
    sheet.save(os.path.join(out, "castle_blocks_check.png"))
    pdir = os.path.join(base, "textures/block/castle")
    names = sorted(f for f in os.listdir(pdir) if f.endswith(".png"))
    ps = Image.new("RGBA", (100 * 10, 132 * ((len(names) + 9) // 10)), (30, 30, 36, 255))
    for k, f in enumerate(names):
        ps.alpha_composite(Image.open(os.path.join(pdir, f)).convert("RGBA"), (100 * (k % 10) + 2, 132 * (k // 10) + 2))
    ps.save(os.path.join(out, "castle_portraits_check.png"))
    icons = Image.new("RGBA", (40 * (len(ICONS) + 1), 40), (60, 64, 72, 255))
    for k, name in enumerate(list(ICONS) + ["page"]):
        path = os.path.join(base, "textures/item", name + ".png") if name != "page" else os.path.join(base, "textures/entity/page_shard.png")
        im = Image.open(path).convert("RGBA").resize((32, 32), Image.NEAREST)
        icons.alpha_composite(im, (40 * k + 4, 4))
    icons.save(os.path.join(out, "castle_icons_check.png"))


if __name__ == "__main__":
    main()
