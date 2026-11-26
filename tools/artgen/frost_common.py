"""霜銀シリーズ（第2章・6階からの職業ごとの装備）の色と塗り方（11月26日に形から作り直した）。

11月26日のご指示「階層が変わったときの装備が色しか変わっていないのはよくないので見た目をしっかりと変える」で、流紋の形の
塗り直しをやめ、凍結した村の冬の装いとして作り直した【補完】。make_gear_frost_armor.py（6職業の防具）と
make_gear_frost_weapons.py（武器と盾）が使う。

シリーズの印：
  - 霜銀（青みの白い金属。ふちに霜の白いきらめき・上を向いた面に薄い雪）
  - 雪の結晶の紋（六つの腕の星）
  - 白い毛皮のふち・綿入れのキルト・編み目のマフラー（村の冬の手仕事）
コモン（工房で作る品）なので、光る部分はない（20章：コモンは素朴な形）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from materials import RAMPS, hexc, hsh, pick  # noqa: E402


def ramp(*hexes):
    return [hexc(h) for h in hexes]


RAMPS.update({
    # 霜銀（青みの白い金属）と、その暗い下地
    "fsilver": ramp("#26303F", "#3C4A60", "#596D88", "#7F95B2", "#A9BDD4", "#D2E1EE", "#F2F8FD"),
    "fsilver_dark": ramp("#161B23", "#212833", "#2D3644", "#3B4656", "#4D5A6C", "#647285", "#7E8DA0"),
    # 白い毛皮・灰の毛皮
    "snowfur": ramp("#8F8B84", "#ADA9A2", "#C9C6BF", "#DFDCD6", "#EEECE7", "#F8F7F3", "#FFFFFF"),
    "greyfur": ramp("#34373C", "#4A4E54", "#62666D", "#7C8088", "#979BA2", "#B2B5BB", "#CDCFD3"),
    # 布：氷青・夜の雪（青紫）・炭・綿入れの生成り・木の実の赤・松の緑
    "frostblue": ramp("#33506A", "#4A6A86", "#6488A3", "#82A7C0", "#A5C4D8", "#C7DDEB", "#E6F1F7"),
    "nightsnow": ramp("#13132A", "#1D1D3D", "#292954", "#36366B", "#464683", "#5B5B9D", "#7676B8"),
    "charcoal": ramp("#111317", "#1A1D22", "#252930", "#31363F", "#3F4550", "#515864", "#68707D"),
    "padded": ramp("#6C675E", "#898377", "#A59F91", "#BEB8AA", "#D3CEC0", "#E4E0D5", "#F1EFE8"),
    "berry": ramp("#3B0E14", "#5C171F", "#7F212B", "#A02E38", "#BE434B", "#D6686A", "#EB9694"),
    "pine": ramp("#0F2219", "#173124", "#21412F", "#2C533C", "#3A684B", "#4E805E", "#689B76"),
    # 氷の結晶（光らない）・白樺の木・わら
    "icec": ramp("#2C5D76", "#3D7A95", "#5597B2", "#76B4CB", "#9BCFE1", "#C4E6F1", "#EAF8FC"),
    "birch": ramp("#5E574D", "#7F776B", "#A0978A", "#BDB6AA", "#D5CFC5", "#E7E3DC", "#F6F4F0"),
})

CLOTHS = ("frostblue", "nightsnow", "charcoal", "padded", "berry", "pine")

# 雪の結晶の紋（# が糸、@ がまん中の明るい糸）
SNOWFLAKE_7 = ("...#...",
               ".#.#.#.",
               "..###..",
               "###@###",
               "..###..",
               ".#.#.#.",
               "...#...")
SNOWFLAKE_5 = ("#.#.#",
               ".###.",
               "##@##",
               ".###.",
               "#.#.#")


def stamp(pattern, c, row0, col0=None):
    """面の (c.i, c.j) が、型 pattern の # か @ に当たるか（当たらなければ None）。col0 が None なら横はまん中。"""
    r = c.j - row0
    off = (c.W - len(pattern[0])) // 2 if col0 is None else col0
    if 0 <= r < len(pattern) and 0 <= c.i - off < len(pattern[0]):
        ch = pattern[r][c.i - off]
        if ch in "#@":
            return ch
    return None


class FrostPaint:
    """霜銀シリーズの材質。Painter（武器）と ArmorPainter（防具）の両方に混ぜて使う。"""

    # ---------------------------------------------------------------- 霜銀
    def lames(self, c):
        """板の重なり（lames = 間の広さ）：横の面に、段ごとの暗い継ぎ目と、その下の明るいふち。"""
        step = c.el.tags.get("lames")
        if not step or abs(float(c.n[1])) > 0.6:
            return 0.0
        k = c.j % step
        if k == step - 1:
            return -1.0
        if k == 0 and c.j > 0:
            return 0.45
        return 0.0

    def frost_sparkle(self, c):
        """霜のきらめき：ところどころの明るい点（多すぎない）。"""
        return 1.6 if self.tex(c, 171) > 0.965 else 0.0

    def engrave(self, c, tone):
        """表の面に彫った雪の結晶（tags: engrave = (型, 行) か (型, 行, 面)）。"""
        eg = c.el.tags.get("engrave")
        if not eg:
            return tone
        pattern, row0 = eg[0], eg[1]
        face = eg[2] if len(eg) > 2 else "front"
        if c.face.d != face:
            return tone
        ch = stamp(pattern, c, row0)
        if ch == "#":
            return tone - 1.5
        if ch == "@":
            return tone + 1.2
        return tone

    def snow_cap(self, c, col):
        """上を向いた面に、うっすら積もった雪（tags: no_snow で付けない）。"""
        if c.el.tags.get("no_snow") or c.n[1] < 0.6:
            return col
        if self.blob(c, 1.0, 177 + self.eid(c)) < 0.45:
            return pick(RAMPS["snowfur"], 5.0 + (self.tex(c, 178) - 0.5) * 1.2)
        return col

    def paint_fsilver(self, c):
        tags = c.el.tags
        tone = self.std(c, "fsilver", base=tags.get("base", 3.7), noise=0.14, blob=0.16, up=1.5, down=1.1)
        tone += self.rivet(c) + self.lames(c) + self.frost_sparkle(c) + self.emboss_ring(c)
        if tags.get("edge"):
            tone += self.edge(c, 1.4)
        if tags.get("edge_bright") and c.face.d not in ("up", "down"):
            if c.edge_dist()[tags["edge_bright"]] == 0:
                tone += 1.2
        tone = self.engrave(c, tone)
        return self.snow_cap(c, pick(RAMPS["fsilver"], tone))

    def paint_fsilver_dark(self, c):
        tone = self.std(c, "fsilver_dark", base=3.2, noise=0.2, blob=0.2, up=1.3, down=0.9)
        tone += self.rivet(c) + self.lames(c) + self.quilt(c)
        return pick(RAMPS["fsilver_dark"], tone)

    def paint_icec(self, c):
        """氷の結晶：左上が明るい面、右下が深い色。ふちにきらめき（光らない）。"""
        tone = 3.7 + (self.tex(c, 191) - 0.5) * 0.5 + self.bevel(c, up=1.4, down=1.0)
        if (c.i + c.j) == 0:
            tone += 1.3
        if c.i == c.W - 1 and c.j == c.H - 1 and c.W > 1:
            tone -= 0.8
        return pick(RAMPS["icec"], tone)

    def paint_birch(self, c):
        tone = self.std(c, "birch", base=3.8, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c) + self.wrap(c)
        if self.tex(c, 193) < 0.025 and c.face.d not in ("up", "down"):
            return pick(RAMPS["charcoal"], 3.6)  # 白樺の黒い節
        return pick(RAMPS["birch"], tone)

    # ---------------------------------------------------------------- 毛皮
    def fur(self, c, ramp_name, base=3.6):
        """毛皮：縦の毛の筋・下のふちは毛先がそろわない・先は明るい。"""
        tags = c.el.tags
        d = c.edge_dist()
        side = c.face.d not in ("up", "down")
        tone = tags.get("base", base) + (self.tex(c, 181) - 0.5) * 0.7
        col_noise = hsh(self.eid(c), c.i, 183, FACE_ORDER.get(c.face.d, 6))
        tone += (col_noise - 0.5) * 1.6  # 縦の毛の筋
        if c.face.d in ("up", "down"):
            tone += (hsh(self.eid(c), c.i, c.j, 184) - 0.5) * 1.2  # 上から見た毛のふさ
        tone += self.bevel(c, up=0.6, down=0.7)
        if side and d["bottom"] == 0 and col_noise < 0.4:
            return None if tags.get("ragged") else pick(RAMPS[ramp_name], tone - 1.4)  # そろわない毛先
        if side and d["top"] == 0:
            tone += 0.5
        return pick(RAMPS[ramp_name], tone)

    def paint_snowfur(self, c):
        return self.fur(c, "snowfur", 3.9)

    def paint_greyfur(self, c):
        return self.fur(c, "greyfur", 3.4)

    def paint_brownfur(self, c):
        return self.fur(c, "brownfur", 3.4)

    # ---------------------------------------------------------------- 布
    def quilt(self, c):
        """綿入れのキルト（tags: quilt）：ひし形の縫い目。"""
        if not c.el.tags.get("quilt") or c.face.d in ("up", "down"):
            return 0.0
        a = (c.i + c.j) % 4
        b = (c.i - c.j) % 4
        if a == 0 or b == 0:
            return -0.9
        if a == 2 and b == 2:
            return 0.5
        return 0.0

    def knit(self, c):
        """編み目（tags: knit）：縦に並ぶ V の字。"""
        if not c.el.tags.get("knit"):
            return 0.0
        return (0.55, -0.25, -0.55, 0.15)[(c.i % 2) * 2 + (c.j % 2) ^ (c.i % 2)]

    def fcloth(self, c, ramp_name, base=3.0):
        """霜銀シリーズの布：織り目・キルト・編み目・ふちの色・雪の結晶の紋（表にも裏にも）・横の縞。"""
        tags = c.el.tags
        tone = tags.get("base", base) + (self.tex(c, 161) - 0.5) * 0.3 + (self.blob(c, 2.0, 162) - 0.5) * 0.25
        if not tags.get("knit") and not tags.get("quilt"):
            tone += 0.2 if (c.i + 2 * c.j) % 4 == 0 else 0.0  # 織り目
        tone += self.bevel(c, up=0.7, down=0.8) + self.quilt(c) + self.knit(c) + self.wrap(c)
        if tags.get("pleats") and c.face.d not in ("up", "down") and c.i % 2 == 0:
            tone -= 0.9
        col = pick(RAMPS[ramp_name], tone)
        d = c.edge_dist()
        side = c.face.d not in ("up", "down")
        for key, edge in (("trim", "bottom"), ("trim_top", "top")):
            trim = tags.get(key)
            if trim and side and d[edge] == 0:
                col = pick(RAMPS[trim], 4.2 + (self.tex(c, 163) - 0.5) * 0.4)
        trim_sides = tags.get("trim_sides")
        if trim_sides and c.face.d in ("front", "back") and (d["left"] == 0 or d["right"] == 0):
            col = pick(RAMPS[trim_sides], 4.0 + (self.tex(c, 164) - 0.5) * 0.4)
        camo = tags.get("camo")
        if camo and side and self.blob(c, 2.0, 168 + self.eid(c)) < 0.28:
            col = pick(RAMPS[camo], 4.2 + (self.tex(c, 169) - 0.5) * 0.5)  # 雪の迷彩のまだら
        stripes = tags.get("stripes")
        if stripes and side:
            ink, every = stripes
            if (d["bottom"] % every) == 1:
                col = pick(RAMPS[ink], 4.0 + (self.tex(c, 165) - 0.5) * 0.4)
        fringe = tags.get("fringe")
        if fringe and side and d["bottom"] <= 1:
            if c.i % 2 == 1:
                return None if d["bottom"] == 0 else pick(RAMPS[ramp_name], tone - 0.6)
        lining = tags.get("lining")
        if lining and c.n[2] < -0.6:
            col = pick(RAMPS[lining], 3.2 if d["bottom"] == 0 else 3.8 + (self.tex(c, 166) - 0.5) * 0.4)
        under = tags.get("under")
        if under and c.face.d == "down":
            col = pick(RAMPS[under], 3.4)
        split = tags.get("split")
        if split and c.n[2] < -0.6 and abs(c.p[0]) < 0.75:
            col = pick(RAMPS[split], 3.7)
        toggles = tags.get("toggles")
        if toggles and c.n[2] < -0.6 and abs(c.p[0]) < 1.0 and c.j % 3 == 1:
            col = pick(RAMPS[toggles], 4.6 if c.p[0] < 0 else 3.4)  # 角のトグル（前の合わせ）
        emblem = tags.get("emblem")
        if emblem:
            pattern, ink, row0 = emblem[0], emblem[1], emblem[2]
            faces = emblem[3] if len(emblem) > 3 else ("back",)
            if c.face.d in faces:
                ch = stamp(pattern, c, row0)
                if ch == "#":
                    col = pick(RAMPS[ink], 3.8)
                elif ch == "@":
                    col = pick(RAMPS[ink], 5.2)
        diag = tags.get("diag")
        if diag and c.n[2] < -0.6:
            # ななめの打ち合わせ（右の肩から左の腰へ）と、その上のボタン
            ink, slope = diag
            x = c.i - c.j * slope
            if abs(x - (c.W - 2)) < 0.6:
                col = pick(RAMPS[ink], 2.2)
            elif abs(x - (c.W - 3)) < 0.6 and c.j % 3 == 1:
                col = pick(RAMPS["fsilver"], 5.0)
        return col

    def paint_frostblue(self, c):
        return self.fcloth(c, "frostblue", 3.3)

    def paint_nightsnow(self, c):
        return self.fcloth(c, "nightsnow", 3.0)

    def paint_charcoal(self, c):
        return self.fcloth(c, "charcoal", 3.0)

    def paint_padded(self, c):
        return self.fcloth(c, "padded", 3.8)

    def paint_berry(self, c):
        return self.fcloth(c, "berry", 3.3)

    def paint_pine(self, c):
        return self.fcloth(c, "pine", 3.2)

    def paint_snowcloth(self, c):
        return self.fcloth(c, "white", 4.6)

    # ---------------------------------------------------------------- わらの蓑・革の網
    def paint_strawcape(self, c):
        """蓑（わらの雨具）：縦のわらの筋。3ピクセルごとの段で重なり、段の下のふちは毛先がそろわない（透ける）。"""
        d = c.edge_dist()
        side = c.face.d not in ("up", "down")
        strand = hsh(self.eid(c), c.i, 197, FACE_ORDER.get(c.face.d, 6))
        tone = 3.2 + (strand - 0.5) * 2.2 + (self.tex(c, 198) - 0.5) * 0.4 + self.bevel(c, up=0.6, down=0.6)
        if c.i % 2 == 1:
            tone -= 0.7  # わらの筋のすき間
        if side:
            k = d["bottom"] % 3
            if k == 0 and strand < 0.35:
                if d["bottom"] == 0:
                    return None
                tone -= 1.6  # 段の下の、重なった影
            elif k == 2:
                tone += 0.6
        return pick(RAMPS["straw"], tone)

    def paint_leather(self, c):
        lattice = c.el.tags.get("lattice")
        if lattice and c.face.d in ("up", "down"):
            # かんじきの網：革紐の格子（すき間は透ける）
            if c.i % lattice != 0 and c.j % lattice != 0:
                return None
        from materials import Painter
        return Painter.paint_leather(self, c)


FACE_ORDER = {"right": 0, "front": 1, "left": 2, "back": 3, "up": 4, "down": 5}

RAMPS.setdefault("brownfur", ramp("#2A1C12", "#3E2A1B", "#553A25", "#6E4C31", "#8A623F", "#A67B52", "#C39769"))
