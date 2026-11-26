"""守護者シリーズ（5階「古代守護者の祭壇」の職業ごとの装備。エピック）の色と塗り方（11月26日に形から作り直した）。

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」で、流紋の形の
塗り直しをやめ、古代守護者の姿から作り直した【補完】。make_gear_guardian_armor.py（6職業の防具）と
make_gear_guardian_weapons.py（武器）が使う。

シリーズの印（古代守護者と祭壇から）：
  - 白い石（あたたかい白。面のふちは明るく、表に「結び目の格子」の細い溝）と、落ち着いた青銅の帯
  - 翠の記録核（立方体の結晶。エピックなので光る＝glow の部品）
  - 角ばった板・四角の輪（後光）・浮かぶ石の板。曲線のない、組み上げた形
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import RAMPS, hexc, pick  # noqa: E402


def ramp(*hexes):
    return [hexc(h) for h in hexes]


RAMPS.update({
    "gstone": ramp("#3B3731", "#5B554C", "#7F786D", "#A39C90", "#C4BEB2", "#DED9CF", "#F3F1EA"),
    "gstone_dark": ramp("#1C1A18", "#2A2825", "#3B3834", "#4E4A45", "#645F59", "#7D7771", "#97918A"),
    "gbronze": ramp("#281C10", "#412E18", "#5D4322", "#7A5A2E", "#98733B", "#B68D50", "#D2AC6E"),
    "jade": ramp("#0D4636", "#14664E", "#1D8867", "#2BAA81", "#4FCC9E", "#8CE8C3", "#D3FFEE"),
    "ivory": ramp("#6B675D", "#8A857A", "#A7A294", "#C0BBAD", "#D6D2C5", "#E7E4DA", "#F4F2EB"),
    "obsidian": ramp("#0D0F11", "#15181B", "#1E2226", "#292E33", "#353B41", "#444B52", "#585F68"),
})


class GuardianPaint:
    """守護者シリーズの材質。Painter（武器）と ArmorPainter（防具）の両方に混ぜて使う。"""

    def lattice(self, c):
        """結び目の格子（tags: lattice = 間の広さ）：表・裏・横の面に、細い溝の格子。交わる所は少し明るい（結び目）。"""
        step = c.el.tags.get("lattice")
        if not step or c.face.d in ("up", "down"):
            return 0.0
        a = (c.i + 1) % step == 0
        b = (c.j + 1) % step == 0
        if a and b:
            return 0.8
        if a or b:
            return -0.8
        return 0.0

    def steps(self, c):
        """段の溝（tags: steps = 間の広さ）：横の面に、段ごとの細い暗い線（積み上げた石）。"""
        step = c.el.tags.get("steps")
        if not step or c.face.d in ("up", "down"):
            return 0.0
        return -0.9 if c.j % step == step - 1 else 0.0

    def paint_gstone(self, c):
        tags = c.el.tags
        tone = self.std(c, "gstone", base=tags.get("base", 4.0), noise=0.12, blob=0.2, up=1.2, down=1.2, specks=0.02)
        tone += self.lattice(c) + self.steps(c) + self.rivet(c) + self.grooves(c)
        if tags.get("edge"):
            tone += self.edge(c, 1.2)
        if tags.get("edge_bright") and c.face.d not in ("up", "down"):
            if c.edge_dist()[tags["edge_bright"]] == 0:
                tone += 1.0
        return pick(RAMPS["gstone"], tone)

    def paint_gstone_dark(self, c):
        tone = self.std(c, "gstone_dark", base=3.2, noise=0.18, blob=0.2, up=1.3, down=0.9)
        tone += self.lattice(c) + self.steps(c)
        return pick(RAMPS["gstone_dark"], tone)

    def paint_gbronze(self, c):
        tags = c.el.tags
        tone = self.std(c, "gbronze", base=tags.get("base", 3.7), noise=0.12, blob=0.15, up=1.5, down=1.0)
        tone += self.rivet(c) + self.steps(c)
        if tags.get("edge"):
            tone += self.edge(c, 1.3)
        return pick(RAMPS["gbronze"], tone)

    def paint_jade(self, c):
        """翠の記録核：左上が明るい面、まん中に深い芯、右下が暗い（光る部品）。"""
        tone = 4.0 + (self.tex(c, 291) - 0.5) * 0.4 + self.bevel(c, up=1.0, down=1.1)
        if (c.i + c.j) == 0:
            tone += 1.4
        if c.W >= 3 and c.H >= 3 and 0 < c.i < c.W - 1 and 0 < c.j < c.H - 1:
            tone += 0.6 if (c.i + c.j) % 2 == 0 else 0.0
        return pick(RAMPS["jade"], tone)

    def gcloth(self, c, ramp_name, base=3.0):
        """守護者の布：織り目・ふちの色・格子の縫い取り（lattice_ink）・背の印（emblem）。"""
        tags = c.el.tags
        tone = tags.get("base", base) + (self.tex(c, 261) - 0.5) * 0.3 + (self.blob(c, 2.0, 262) - 0.5) * 0.25
        tone += 0.2 if (c.i + 2 * c.j) % 4 == 0 else 0.0
        tone += self.bevel(c, up=0.7, down=0.8) + self.wrap(c)
        if tags.get("pleats") and c.face.d not in ("up", "down") and c.i % 2 == 0:
            tone -= 0.9
        col = pick(RAMPS[ramp_name], tone)
        d = c.edge_dist()
        side = c.face.d not in ("up", "down")
        for key, edge in (("trim", "bottom"), ("trim_top", "top")):
            trim = tags.get(key)
            if trim and side and d[edge] == 0:
                col = pick(RAMPS[trim], 4.0 + (self.tex(c, 263) - 0.5) * 0.4)
        border = tags.get("border")
        if border and side and d["bottom"] in (1, 2):
            # すその幾何学の帯（青銅の糸）：2段の、四角の連なり
            if d["bottom"] == 2 or (c.i % 3) == 1:
                col = pick(RAMPS[border], 3.8)
        ink = tags.get("lattice_ink")
        if ink and side:
            step = ink[1]
            if (c.i + 1) % step == 0 or (c.j + 1) % step == 0:
                col = pick(RAMPS[ink[0]], 3.4)
        split = tags.get("split")
        if split and c.n[2] < -0.6 and abs(c.p[0]) < 0.75:
            col = pick(RAMPS[split], 3.7)
        lining = tags.get("lining")
        if lining and c.n[2] < -0.6:
            col = pick(RAMPS[lining], 3.2 if d["bottom"] == 0 else 3.8)
        emblem = tags.get("emblem")
        if emblem:
            pattern, ink2, row0 = emblem[0], emblem[1], emblem[2]
            faces = emblem[3] if len(emblem) > 3 else ("back",)
            if c.face.d in faces:
                r = c.j - row0
                off = (c.W - len(pattern[0])) // 2
                if 0 <= r < len(pattern) and 0 <= c.i - off < len(pattern[0]):
                    ch = pattern[r][c.i - off]
                    if ch == "#":
                        col = pick(RAMPS[ink2], 3.6)
                    elif ch == "@":
                        col = pick(RAMPS["jade"], 4.6)
        return col

    def paint_ivory(self, c):
        return self.gcloth(c, "ivory", 4.0)

    def paint_obsidian(self, c):
        return self.gcloth(c, "obsidian", 3.0)


# 結び目の格子の印（背に縫う。# が青銅の糸、@ が翠の糸）
KNOT_EMBLEM = ("#.#.#.#",
               ".#####.",
               "##.@.##",
               ".#@@@#.",
               "##.@.##",
               ".#####.",
               "#.#.#.#")
