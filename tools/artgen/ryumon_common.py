"""流紋シリーズ（3〜4階の職業ごとの装備。Phase 8 その2）の色と塗り方。

make_gear_ryumon_armor.py（6職業の防具）と make_gear_ryumon_weapons.py（武器と盾）が使う。

シリーズの印【補完】：
  - 水路の青銅（あたたかい青銅に、緑青のさび）と、流水の結晶（水色。コモンなので光らない）
  - 「流れの紋」：青銅の板や布のすそに、波打つ線が流れる（3階の水路の水の筋）
職業ごとの主な色（0-A 20章の向き）：
  ナイト＝青銅の重い鎧と水色の外套／ウィザード＝藍の長衣と銀の波／ヒーラー＝白い法衣・金・水色の帯と鈴／
  アーチャー＝茶の革・緑の外套・白い羽根／ローグ＝黒い装束と水路の青緑の布／モンク＝生成りの道着・藍の帯と袴
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import Painter, RAMPS, hexc, pick  # noqa: E402


def ramp(*hexes):
    return [hexc(h) for h in hexes]


RAMPS.update({
    # 水路の青銅（掃除機兵と番人の体の色。あたたかい青銅）と、そのさび（緑青）
    "wbronze": ramp("#2E1F10", "#4D341A", "#704D26", "#966A35", "#B98948", "#D6A962", "#EDCB8C"),
    "verdigris": ramp("#1F3D36", "#2C5A4C", "#3B7563", "#4F927A", "#6AAF93", "#8DCAAE", "#B6E2CB"),
    # 流水の結晶（光らない）
    "flow": ramp("#0F4256", "#17627A", "#1F849D", "#2FA6BE", "#58C6D8", "#93E2EC", "#D8FBFF"),
    "navy": ramp("#0F1730", "#172447", "#1F3260", "#2A4379", "#385893", "#4B6FAD", "#6A8CC4"),
    "silver": ramp("#4A4F58", "#6B717B", "#8D939C", "#AEB4BC", "#CBD0D6", "#E2E6EA", "#F5F7F9"),
    "gold": ramp("#4A3410", "#6E4F18", "#94701F", "#B89130", "#D3AE48", "#E8CB70", "#F7E6A6"),
    "white": ramp("#7C8088", "#9EA2A9", "#BDC0C5", "#D5D7DA", "#E6E7E8", "#F1F2F2", "#FBFBFA"),
    "aqua": ramp("#174A55", "#1F6573", "#2A8293", "#3E9FAF", "#5DBAC6", "#86D3DA", "#B8EAEE"),
    "black": ramp("#0B0C0F", "#131519", "#1B1E24", "#252932", "#323743", "#434A58", "#5A6273"),
    "teal": ramp("#0E2E2D", "#164240", "#1E5654", "#286B68", "#358380", "#4A9C98", "#68B6B1"),
    "hunter": ramp("#16261A", "#1F3424", "#29442E", "#355739", "#436B46", "#568157", "#6E9A6C"),
    "gi": ramp("#6F6858", "#8E8672", "#ABA38C", "#C4BCA4", "#D8D1BA", "#E7E1CD", "#F3EFE2"),
    "indigo": ramp("#111733", "#19214A", "#222D60", "#2D3B78", "#3B4C90", "#4F62A8", "#6A7FC0"),
    "straw": ramp("#5A4A22", "#7A6530", "#9A8240", "#B59C52", "#CBB46A", "#DCC988", "#EADDAA"),
    "feather": ramp("#6E7276", "#8F9397", "#B0B4B7", "#CDD0D2", "#E2E4E5", "#F0F1F1", "#FAFAFA"),
    "whitewood": ramp("#7A7066", "#9A9084", "#B8AFA2", "#D0C8BC", "#E2DCD2", "#EEEAE3", "#F8F6F2"),
    # 牙ナマズのひげ（弓の弦・草鞋の鼻緒）
    "whisker": ramp("#1E1A16", "#2C2620", "#3C342C", "#4E453A", "#62584B", "#786C5D", "#908372"),
})

CLOTHS = ("navy", "white", "aqua", "black", "teal", "hunter", "gi", "indigo", "straw")


def wave_row(i, period=6.0, amp=0.9, phase=0.0):
    """横の位置 i での波の高さ（整数）。"""
    return int(round(amp * math.sin(2 * math.pi * (i + phase) / period)))


class RyumonPaint:
    """流紋シリーズの材質。Painter（武器）と ArmorPainter（防具）の両方に混ぜて使う。"""

    # ---------------------------------------------------------------- 青銅
    def flowlines(self, c):
        """「流れの紋」：表・裏・横の面に、波打つ溝が数本流れる（tags: flow = 間の広さ）。"""
        spacing = c.el.tags.get("flow")
        if not spacing or abs(float(c.n[1])) > 0.6:
            return 0.0
        row = c.j + wave_row(c.i, period=7.0, amp=1.0, phase=self.eid(c) * 1.7)
        k = row % spacing
        if k == spacing // 2:
            return -1.6
        if k == spacing // 2 + 1:
            return 0.7
        return 0.0

    def scales(self, c):
        """うろこ（肩当て・腰の垂れ）：3ピクセルごとの段に、ずらした半円のふち。"""
        if not c.el.tags.get("scales") or abs(float(c.n[1])) > 0.6:
            return 0.0
        band = c.j // 3
        k = c.j % 3
        col = (c.i + (band % 2) * 2) % 4
        if k == 2 and col != 1:
            return -1.0
        if k == 0 and col == 1:
            return 0.5
        return 0.0

    def emboss_ring(self, c):
        """表の面のふちを明るく、中をくぼませる（ハンドルの輪・膝の渦の当て板）。ring が "+z" なら +z の面（盾の表）。"""
        ring = c.el.tags.get("ring")
        if not ring:
            return 0.0
        if (ring == "+z" and c.n[2] < 0.6) or (ring != "+z" and c.n[2] > -0.6):
            return 0.0
        d = c.edge_dist()
        if min(d.values()) == 0:
            return 0.9
        return -1.1

    def planks(self, c):
        """水門の扉の板：表・裏に、4ピクセルごとの横の継ぎ目。"""
        step = c.el.tags.get("planks")
        if not step or abs(float(c.n[2])) < 0.6:
            return 0.0
        y = math.floor(c.p[1] + 1e-3)
        if y % step == 0:
            return -1.0
        if y % step == 1:
            return 0.35
        return 0.0

    def cog(self, c):
        """かじられた歯車の歯：上のふちに、1つおきの欠け。"""
        if not c.el.tags.get("cog") or c.face.d in ("up", "down"):
            return 0.0
        d = c.edge_dist()
        if d["top"] == 0 and c.i % 2 == 1:
            return -1.8
        return 0.0

    def patina(self, c, col, tone, salt=700):
        """緑青：くぼみのような小さな斑と、下を向いた面に少し（多すぎると、まだらの迷彩に見える）。"""
        p = self.blob(c, 1.0, salt + self.eid(c))
        if p < 0.05 or (c.n[1] < -0.6 and p < 0.3):
            return pick(RAMPS["verdigris"], tone - 0.4)
        return col

    def paint_wbronze(self, c):
        tags = c.el.tags
        tone = self.std(c, "wbronze", base=tags.get("base", 3.6), noise=0.12, blob=0.18, up=1.4, down=1.0)
        tone += self.rivet(c) + self.flowlines(c) + self.scales(c) + self.emboss_ring(c) + self.planks(c) + self.cog(c)
        if tags.get("edge"):
            tone += self.edge(c, 1.3)  # 研いだ刃のふち（武器だけ）
        breaths = tags.get("breaths")
        if breaths is not None and c.n[2] < -0.6 and int(np.floor(c.p[1])) == int(breaths) and c.i % 2 == 1 \
                and 0 < c.i < c.W - 1:
            tone -= 2.2  # 息の穴
        if tags.get("edge_bright") and c.face.d not in ("up", "down"):
            d = c.edge_dist()
            if d[tags["edge_bright"]] == 0:
                tone += 1.2
        col = pick(RAMPS["wbronze"], tone)
        if tags.get("no_patina"):
            return col
        return self.patina(c, col, tone)

    def paint_darkbronze(self, c):
        tone = self.std(c, "wbronze", base=2.1, noise=0.2, blob=0.3, up=1.2, down=0.8)
        tone += self.rivet(c) + self.flowlines(c)
        return self.patina(c, pick(RAMPS["wbronze"], tone), tone, salt=720)

    # ---------------------------------------------------------------- 結晶・金属
    def paint_flowc(self, c):
        """流水の結晶：左上が明るい面、右下が深い色。ふちにきらめき（光らない）。"""
        tone = 3.6 + (self.tex(c, 91) - 0.5) * 0.5 + self.bevel(c, up=1.4, down=1.0)
        if (c.i + c.j) == 0:
            tone += 1.2
        if c.el.tags.get("channel") and c.face.d not in ("up", "down"):
            # 刃の溝の水：縦にゆれる明るい筋
            if (c.j + wave_row(c.i, period=5.0, amp=0.8)) % 4 == 0:
                tone += 1.0
        return pick(RAMPS["flow"], tone)

    def paint_silver(self, c):
        tone = self.std(c, "silver", base=3.8, noise=0.15, blob=0.2, up=1.5, down=1.0)
        tone += self.rivet(c)
        return pick(RAMPS["silver"], tone)

    def paint_gold(self, c):
        tone = self.std(c, "gold", base=3.8, noise=0.15, blob=0.2, up=1.5, down=1.0)
        tone += self.rivet(c)
        return pick(RAMPS["gold"], tone)

    def paint_whisker(self, c):
        tone = self.std(c, "whisker", base=3.2, noise=0.2, blob=0.0, up=0.8, down=0.6)
        tone += (0.5, -0.5)[(c.i + c.j) % 2] if c.el.tags.get("twist") else 0.0
        return pick(RAMPS["whisker"], tone)

    def paint_whitewood(self, c):
        tone = self.std(c, "whitewood", base=3.6, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c)
        return pick(RAMPS["whitewood"], tone)

    def paint_feather(self, c):
        """羽根：白い羽根に、まん中の軸と、先の水色の帯。"""
        tone = self.std(c, "feather", base=4.0, noise=0.3, blob=0.1, up=0.6, down=0.6)
        d = c.edge_dist()
        tags = c.el.tags
        if c.face.d not in ("up", "down"):
            along = tags.get("along", "i")
            pos = c.i if along == "i" else c.j
            length = c.W if along == "i" else c.H
            across = d["top"] if along == "i" else d["left"]
            width = c.H if along == "i" else c.W
            if width >= 2 and across == width // 2:
                tone -= 0.9  # 羽根の軸
            tip = tags.get("tip", "end")
            from_tip = (length - 1 - pos) if tip == "end" else pos
            if from_tip in (1, 2):
                return pick(RAMPS["flow"], 3.6 + (self.tex(c, 93) - 0.5) * 0.4)
            if (pos + across) % 3 == 0:
                tone -= 0.5  # 羽枝のすじ
        return pick(RAMPS["feather"], tone)

    # ---------------------------------------------------------------- 布
    def cloth(self, c, ramp_name, base=3.0):
        tags = c.el.tags
        tone = tags.get("base", base) + (self.tex(c, 61) - 0.5) * 0.3 + (self.blob(c, 2.0, 62) - 0.5) * 0.3
        tone += 0.22 if (c.i + 2 * c.j) % 4 == 0 else 0.0  # 織り目
        tone += self.bevel(c, up=0.7, down=0.8)
        tone += self.wrap(c)
        if tags.get("pleats") and c.face.d not in ("up", "down") and c.i % 2 == 0:
            tone -= 1.0  # 袴のひだ
        if tags.get("toe_split") and c.n[2] < -0.6 and c.i == 1:
            tone -= 1.6  # 足袋の割れたつま先
        col = pick(RAMPS[ramp_name], tone)
        d = c.edge_dist()
        side = c.face.d not in ("up", "down")
        trim = tags.get("trim")
        if trim and side and d["bottom"] == 0:
            col = pick(RAMPS[trim], 4.2 + (self.tex(c, 63) - 0.5) * 0.4)
        trim_top = tags.get("trim_top")
        if trim_top and side and d["top"] == 0:
            col = pick(RAMPS[trim_top], 4.2 + (self.tex(c, 64) - 0.5) * 0.4)
        trim_sides = tags.get("trim_sides")
        if trim_sides and c.face.d in ("front", "back") and (d["left"] == 0 or d["right"] == 0):
            col = pick(RAMPS[trim_sides], 4.0 + (self.tex(c, 65) - 0.5) * 0.4)
        wave = tags.get("wave")
        if wave and side:
            wave_ramp, height = wave
            yy = height + wave_row(c.i, period=6.0, amp=0.9, phase=self.eid(c) * 1.3)
            if d["bottom"] == yy:
                col = pick(RAMPS[wave_ramp], 4.5)
            elif d["bottom"] == yy - 1 and tags.get("wave_shadow"):
                col = pick(RAMPS[ramp_name], tone - 0.8)
        lining = tags.get("lining")
        if lining and c.n[2] < -0.6:
            col = pick(RAMPS[lining], 3.2 if d["bottom"] == 0 else 3.8 + (self.tex(c, 66) - 0.5) * 0.4)
        under = tags.get("under")
        if under and c.face.d == "down":
            col = pick(RAMPS[under], 3.4)
        split = tags.get("split")
        if split and c.n[2] < -0.6 and abs(c.p[0]) < 0.75:
            col = pick(RAMPS[split], 3.7)
        stars = tags.get("stars")
        if stars and c.n[2] < -0.6 and d["bottom"] == 1 and (c.i % 4) == 1:
            col = pick(RAMPS[stars], 5.0)
        emblem = tags.get("emblem")
        if emblem and c.face.d == "back":
            pattern, ink, row0 = emblem
            r = c.j - row0
            off = (c.W - len(pattern[0])) // 2
            if 0 <= r < len(pattern) and 0 <= c.i - off < len(pattern[0]):
                ch = pattern[r][c.i - off]
                if ch == "#":
                    col = pick(RAMPS[ink], 3.6)
                elif ch == "@":
                    col = pick(RAMPS[ink], 5.0)
        lapel = tags.get("lapel")
        if lapel and c.n[2] < -0.6:
            # 道着の打ち合わせ：上から V の字に下がる2本の線（右前）
            x = c.i - c.W / 2.0
            k = c.j * 0.5
            if abs(abs(x + 0.5) - (c.W / 2.0 - 0.5 - k)) < 0.6 and c.j <= c.H - 3:
                col = pick(RAMPS[lapel], tags.get("lapel_tone", 2.6))
        return col

    def paint_navy(self, c):
        return self.cloth(c, "navy", 3.0)

    def paint_white(self, c):
        return self.cloth(c, "white", 5.0)

    def paint_aqua(self, c):
        return self.cloth(c, "aqua", 3.4)

    def paint_black(self, c):
        return self.cloth(c, "black", 3.0)

    def paint_teal(self, c):
        return self.cloth(c, "teal", 3.2)

    def paint_hunter(self, c):
        return self.cloth(c, "hunter", 3.2)

    def paint_gi(self, c):
        return self.cloth(c, "gi", 3.8)

    def paint_indigo(self, c):
        return self.cloth(c, "indigo", 3.0)

    def paint_straw(self, c):
        tone = 3.4 + (self.tex(c, 67) - 0.5) * 0.4 + self.bevel(c, up=0.7, down=0.7)
        tone += 0.5 if (c.i + c.j) % 2 == 0 else -0.3  # 編み目
        return pick(RAMPS["straw"], tone)
