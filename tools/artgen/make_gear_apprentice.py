"""見習い装備（ギルドが貸す、旧・結晶騎士団の練習用の装備）：剣・大剣・杖・盾。

  python3 tools/artgen/make_gear_apprentice.py <リポジトリ> [確認画像の出力先]

共通の印：赤い「結び紐」（仮登録の印）・すり減った騎士団のひし形の紋・明るい鉄と白っぽい木と黄土色の革。光る部分はない（コモン）。
仕様書：docs/gear/apprentice.md
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel  # noqa: E402
from materials import Painter, RAMPS, pick, hsh  # noqa: E402
import weapons  # noqa: E402

CREDIT = "AMEMARU: 見習い装備（tools/artgen/make_gear_apprentice.py で生成。docs/gear/apprentice.md）"


def cord_tail(m, name, top, length, angle):
    """結び紐の垂れ（上の点から下へ。少し開く）。"""
    x, y, z = top
    m.box(name, (x - 0.3, y - length, z - 0.3), (x + 0.3, y, z + 0.3), "cord", rot=((x, y, z), "z", angle),
          tags={"twist": True})


def rental_tag(m, name, top, z=8.0, w=2.0, h=2.5):
    """ギルドの貸出札：結び紐の先に下げた、真鍮の小さな札（穴と、打ち抜いた番号）。
    見習い装備は「ギルドが貸す練習用」なので、どれにも札が下がっている（返すときに番号で確かめる）。"""
    x, y = top
    m.box(name + "_string", (x - 0.15, y - 0.6, z - 0.15), (x + 0.15, y, z + 0.15), "cord")
    m.box(name, (x - w / 2, y - 0.6 - h, z - 0.15), (x + w / 2, y - 0.6, z + 0.15), "tag")


# ---------------------------------------------------------------- 剣

def build_sword():
    m = JavaModel("apprentice_sword", 64, 64)
    # 輪の柄頭（ドットの輪）。下に赤い結び紐を通す
    m.box("ring_top", (7, -1.5, 7.25), (9, -0.5, 8.75), "darksteel")
    m.box("ring_bottom", (7, -4.5, 7.25), (9, -3.5, 8.75), "darksteel")
    m.box("ring_left", (6, -3.5, 7.25), (7, -1.5, 8.75), "darksteel")
    m.box("ring_right", (9, -3.5, 7.25), (10, -1.5, 8.75), "darksteel")
    m.box("neck", (7.5, -0.5, 7.5), (8.5, 0, 8.5), "darksteel")
    m.box("cord_loop", (7.7, -3.5, 7.7), (8.3, -2.6, 8.3), "cord")
    m.box("cord_knot", (7.4, -5.6, 7.4), (8.6, -4.4, 8.6), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail_l", (7.75, -5.5, 8.0), 2.6, -22.5)
    cord_tail(m, "cord_tail_r", (8.25, -5.5, 8.0), 2.6, 22.5)
    rental_tag(m, "tag", (8.0, -5.6))
    # 握り：革巻きと、上下の金具
    m.box("grip", (7.25, 0.75, 7.25), (8.75, 4.25, 8.75), "leather", tags={"wrap": True})
    m.box("collar_low", (7, 0, 7), (9, 0.75, 9), "darksteel")
    m.box("collar_high", (7, 4.25, 7), (9, 5, 9), "darksteel")
    # 鍔：横の棒と両端の玉、まん中に騎士団の紋
    m.box("guard", (4.5, 5, 7.25), (11.5, 6.25, 8.75), "darksteel")
    m.box("guard_end_l", (4, 4.75, 7), (5, 6.5, 9), "darksteel")
    m.box("guard_end_r", (11, 4.75, 7), (12, 6.5, 9), "darksteel")
    m.box("ecusson", (6.5, 5, 7), (9.5, 8, 9), "darksteel", tags={"crest": (8.0, 6.5)})
    # 刃：芯（樋のある2ピクセル）と、薄い刃のふち。先は段々に細くなる
    m.box("blade", (7, 8, 7.5), (9, 19.5, 8.5), "steel", tags={"fuller": (9.5, 18.0)})
    m.box("edge_l", (6.5, 8, 7.65), (7, 19, 8.35), "steel", tags={"edge": "both"})
    m.box("edge_r", (9, 8, 7.65), (9.5, 19, 8.35), "steel", tags={"edge": "both"})
    m.box("tip_1", (7.25, 19.5, 7.55), (8.75, 20.75, 8.45), "steel", tags={"edge": "both"})
    m.box("tip_2", (7.6, 20.75, 7.65), (8.4, 21.75, 8.35), "steel", tags={"edge": "both"})
    return m


# ---------------------------------------------------------------- 大剣

def build_greatsword():
    m = JavaModel("apprentice_greatsword", 64, 64)
    # 車輪の形の柄頭
    m.box("pommel", (6.5, -4, 6.75), (9.5, -1.5, 9.25), "darksteel")
    m.box("pommel_boss", (7.25, -3.5, 6.5), (8.75, -2, 9.5), "darksteel")
    m.box("neck", (7.5, -1.5, 7.5), (8.5, -1, 8.5), "darksteel")
    # 長い両手の握り：下と上の2か所に革、間に金具
    m.box("grip_low", (7.25, -1, 7.25), (8.75, 3.25, 8.75), "leather", tags={"wrap": True})
    m.box("collar_mid", (7, 3.25, 7), (9, 3.75, 9), "darksteel")
    m.box("grip_high", (7.25, 3.75, 7.25), (8.75, 7.75, 8.75), "leather", tags={"wrap": True})
    m.box("collar_high", (7, 7.5, 7), (9, 8.25, 9), "darksteel")
    # 鍔：幅の広い棒。両端が下へ曲がる
    m.box("guard", (3.5, 8.25, 7.1), (12.5, 9.75, 8.9), "darksteel")
    m.box("guard_end_l", (2.5, 7.25, 7.25), (3.5, 9.75, 8.75), "darksteel")
    m.box("guard_end_r", (12.5, 7.25, 7.25), (13.5, 9.75, 8.75), "darksteel")
    m.box("guard_center", (6.5, 8, 6.9), (9.5, 10.5, 9.1), "darksteel", tags={"crest": (8.0, 9.25)})
    # 刃の根元：手を添えるための革巻き（リカッソ）と、受け止めの小さな突起
    m.box("ricasso", (6.75, 10.5, 7.45), (9.25, 13.5, 8.55), "leather", tags={"wrap": True})
    m.box("ricasso_band", (6.5, 13.5, 7.35), (9.5, 14, 8.65), "darksteel")
    m.box("lug_l", (5.5, 13.4, 7.6), (6.5, 14.4, 8.4), "darksteel")
    m.box("lug_r", (9.5, 13.4, 7.6), (10.5, 14.4, 8.4), "darksteel")
    # 刃
    m.box("blade", (6.75, 14, 7.4), (9.25, 29, 8.6), "steel", tags={"fuller": (15.0, 27.0)})
    m.box("edge_l", (6, 14, 7.6), (6.75, 28.5, 8.4), "steel", tags={"edge": "both"})
    m.box("edge_r", (9.25, 14, 7.6), (10, 28.5, 8.4), "steel", tags={"edge": "both"})
    m.box("tip_1", (7, 29, 7.5), (9, 30.25, 8.5), "steel", tags={"edge": "both"})
    m.box("tip_2", (7.5, 30.25, 7.6), (8.5, 31.25, 8.4), "steel", tags={"edge": "both"})
    # 赤い結び紐：鍔の右の端に結び、握りの横へ垂らす
    m.box("cord_knot", (12.6, 7.9, 7.4), (13.8, 9.1, 8.6), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail_b", (13.5, 8.0, 8.0), 2.8, 22.5)
    rental_tag(m, "tag", (13.0, 7.9))
    return m


# ---------------------------------------------------------------- 杖

def build_staff():
    m = JavaModel("apprentice_staff", 64, 64)
    m.box("butt", (7, -9, 7), (9, -8, 9), "darksteel")
    m.box("shaft", (7.25, -8, 7.25), (8.75, 17, 8.75), "ash", tags={"grain": "y"})
    m.box("grip", (7.1, 1, 7.1), (8.9, 6, 8.9), "leather", tags={"wrap": True})
    m.box("collar", (6.75, 17, 6.75), (9.25, 18, 9.25), "darksteel")
    # 燭台：受け皿と2本の腕。まん中に練習用の結晶（光らない）
    m.box("saucer", (5.5, 18, 5.5), (10.5, 18.75, 10.5), "darksteel")
    m.box("arm_l", (5.75, 18.75, 7.5), (6.75, 22.5, 8.5), "ash", tags={"grain": "y"})
    m.box("arm_r", (9.25, 18.75, 7.5), (10.25, 22.5, 8.5), "ash", tags={"grain": "y"})
    m.box("arm_tip_l", (6.0, 22.2, 7.6), (6.8, 23.0, 8.4), "ash", rot=((6.4, 22.6, 8.0), "z", -45.0))
    m.box("arm_tip_r", (9.2, 22.2, 7.6), (10.0, 23.0, 8.4), "ash", rot=((9.6, 22.6, 8.0), "z", 45.0))
    m.box("crystal", (7, 18.75, 7), (9, 22.5, 9), "crystal_dim")
    m.box("crystal_tip", (7.4, 22.5, 7.4), (8.6, 23.5, 8.6), "crystal_dim")
    # 赤い結び紐：腕と結晶をまとめて縛る。右の腕に結び目と垂れ
    m.box("cord_band", (5.6, 20, 7.3), (10.4, 20.75, 8.7), "cord", tags={"twist": True})
    m.box("cord_knot", (10.25, 19.7, 7.4), (11.25, 20.9, 8.6), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail", (10.75, 19.8, 8.0), 2.6, 22.5)
    rental_tag(m, "tag", (10.6, 19.7), w=1.5, h=2.0)
    return m


# ---------------------------------------------------------------- 盾

def build_shield():
    m = JavaModel("apprentice_shield_model", 64, 64)
    # 板6枚（外ほど短い）。表に、色あせた騎士団の紋（大きなひし形）を描く
    for k, (x0, half) in enumerate(((-6, 3.5), (-4, 5.5), (-2, 6.5), (0, 6.5), (2, 5.5), (4, 3.5))):
        m.box(f"plank_{k}", (x0, -half, 1), (x0 + 2, half, 2), "ash", tags={"grain": "y", "painted_crest": True})
    # 鉄の縁（八角形：縦横の4本と、45度の4本）
    r = 6.25
    s = 2 * r * math.tan(math.radians(22.5)) + 0.2
    m.box("rim_top", (-s / 2, r - 0.5, 0.75), (s / 2, r + 0.5, 2.6), "darksteel", tags={"rivets": [(0, r)]})
    m.box("rim_bottom", (-s / 2, -r - 0.5, 0.75), (s / 2, -r + 0.5, 2.6), "darksteel", tags={"rivets": [(0, -r)]})
    m.box("rim_left", (-r - 0.5, -s / 2, 0.75), (-r + 0.5, s / 2, 2.6), "darksteel", tags={"rivets": [(-r, 0)]})
    m.box("rim_right", (r - 0.5, -s / 2, 0.75), (r + 0.5, s / 2, 2.6), "darksteel", tags={"rivets": [(r, 0)]})
    d = r * math.cos(math.radians(45))
    for k, (sx, sy, ang) in enumerate(((1, 1, -45.0), (-1, 1, 45.0), (-1, -1, -45.0), (1, -1, 45.0))):
        cx, cy = sx * d, sy * d
        m.box(f"rim_d{k}", (cx - s / 2, cy - 0.5, 0.75), (cx + s / 2, cy + 0.5, 2.6), "darksteel",
              rot=((cx, cy, 1.5), "z", ang))
    # まん中の突起（段々の丸い頭）
    m.box("boss", (-2, -2, 2), (2, 2, 2.75), "darksteel")
    m.box("boss_2", (-1.25, -1.25, 2.75), (1.25, 1.25, 3.5), "darksteel")
    m.box("boss_3", (-0.5, -0.5, 3.5), (0.5, 0.5, 3.9), "darksteel")
    # 赤い結び紐：下の縁に結んで垂らす
    m.box("cord_knot", (-0.6, -7.6, 1.1), (0.6, -6.4, 2.3), "cord", tags={"twist": True})
    cord_tail(m, "cord_tail_l", (-0.25, -7.4, 1.7), 2.4, -22.5)
    cord_tail(m, "cord_tail_r", (0.25, -7.4, 1.7), 2.4, 22.5)
    rental_tag(m, "tag", (0.0, -7.6), z=1.7)
    # 裏：横の当て木2本と、握り
    m.box("batten_top", (-5, 2.5, 0), (5, 4, 1), "walnut", tags={"grain": "x"})
    m.box("batten_low", (-5, -4, 0), (5, -2.5, 1), "walnut", tags={"grain": "x"})
    m.box("grip_bracket_top", (-1, 1.5, -3), (1, 2.5, 1), "darksteel")
    m.box("grip_bracket_low", (-1, -2.5, -3), (1, -1.5, 1), "darksteel")
    m.box("grip", (-1, -2.5, -4.5), (1, 2.5, -3), "leather", tags={"wrap": True})
    return m


# ---------------------------------------------------------------- 色と模様

class ApprenticePainter(Painter):
    def paint_tag(self, c):
        """真鍮の札：上のまん中に紐の穴、表と裏に打ち抜いた番号（点2つ）。"""
        tone = self.std(c, "bronze", base=4.6, noise=0.15, blob=0.1, up=1.0, down=0.8)
        if self.facing(c) and c.H >= 3 and c.j == c.H - 2 and c.i == 0:
            tone -= 1.8  # 打ち抜いた番号（小さな点）
        return pick(RAMPS["bronze"], tone)

    def crest(self, c):
        """すり減った騎士団の紋：暗い鉄に、明るい引っかき傷のようなひし形の線。"""
        return -super().crest(c) * 1.1

    def paint_steel(self, c):
        tone = self.std(c, "steel", base=3.6, noise=0.15, blob=0.2, up=1.4, down=1.0)
        tone += self.edge(c, 1.4)
        if c.el.tags.get("edge") and self.facing(c) and hsh(int(c.p[1] * 2), self.eid(c), 77) < 0.08:
            tone -= 2.2  # 刃こぼれ
        fuller = c.el.tags.get("fuller")
        if fuller and self.facing(c) and fuller[0] <= c.p[1] <= fuller[1]:
            # 樋：光の来る側が明るく、反対側が暗い溝
            tone += 0.5 if c.i == 0 else -1.2
        return pick(RAMPS["steel"], tone)

    def paint_ash(self, c):
        if c.el.tags.get("painted_crest"):
            # 盾の板：落ち着いた面（継ぎ目は片側だけ暗い）に、色あせた騎士団の紋
            tone = 3.45 + (self.tex(c, 21) - 0.5) * 0.12 + self.bevel(c, up=0.35, down=0.9)
            g = self.grain(c)
            tone += min(0.0, g) * 0.6
            col = pick(RAMPS["ash"], tone)
            if c.n[2] > 0.6:
                dist = abs(c.p[0]) + abs(c.p[1])
                worn = self.blob(c, 1.5, 92) < 0.12
                if 3.4 <= dist < 4.4 and not worn:
                    col = tuple(int(col[i] * 0.28 + (46, 64, 118)[i] * 0.72) for i in range(3))
                elif 4.4 <= dist < 5.3 and not worn:
                    col = tuple(int(col[i] * 0.25 + (234, 232, 218)[i] * 0.75) for i in range(3))
            return col
        tone = self.std(c, "ash", base=3.5, noise=0.12, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c)
        return pick(RAMPS["ash"], tone)

    def paint_crystal_dim(self, c):
        tone = 4.0 + (1.2 if (c.i + c.j) == 0 else 0.0) - (0.9 if c.i == c.W - 1 else 0.0)
        tone += self.bevel(c, up=0.8, down=0.8)
        return pick(RAMPS["crystal_dim"], tone)


# ---------------------------------------------------------------- 持ち物の絵（16×16）

ICON = {"W": "#F0F1EE", "L": "#CFD2D3", "M": "#AEB2B6", "F": "#7E8288",
        "g": "#4A4C52", "h": "#8A8D94", "H": "#B4B7BC", "b": "#C08B56", "n": "#7A4E2C",
        "r": "#D9573A", "R": "#8E2A1C", "a": "#E3C796", "A": "#B08A5A", "q": "#7C5A36",
        "c": "#E2F4F5", "C": "#9CC6CC", "e": "#5E8F98"}
OUTLINE = "#1D1D21"


def icon_sword():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    p.diag_band(8, 13, lambda t: [S["W"], S["F"] if 9 <= t <= 12 else S["L"], S["M"]])
    x, y = p.axis(14)
    p.put(x - 1, y, S["W"])
    p.put(x, y, S["M"])
    for u in range(-2, 3):
        x, y = p.axis(7)
        p.put(x + u, y + u, S["h"])
        p.put(x + u + 1, y + u, S["g"])
    for t in (4, 5, 6):
        x, y = p.axis(t)
        p.put(x, y, S["b"] if t % 2 == 0 else S["n"])
        p.put(x + 1, y, S["n"] if t % 2 == 0 else S["b"])
    cx, cy = p.axis(2)
    for dx, dy, k in ((0, -1, "H"), (1, -1, "h"), (-1, 0, "h"), (1, 0, "g"), (-1, 1, "g"), (0, 1, "g")):
        p.put(cx + dx, cy + dy, S[k])
    p.outline(OUTLINE)
    p.erase(cx, cy)  # 輪の穴
    p.put(cx - 1, cy + 2, S["r"])
    p.put(cx, cy + 2, S["R"])
    p.put(cx - 2, cy + 1, S["r"])
    return p.icon()


def icon_greatsword():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    p.diag_band(7, 13, lambda t: [S["W"], S["L"], S["F"] if 8 <= t <= 12 else S["L"], S["M"]])
    x, y = p.axis(14)
    p.put(x - 1, y, S["W"])
    p.put(x, y, S["L"])
    p.put(x + 1, y, S["M"])
    for t in (5, 6):  # 刃の根元の革巻き
        x, y = p.axis(t)
        for k, col in enumerate((S["b"], S["n"], S["b"], S["n"])):
            p.put(x - 1 + k, y, col)
    for u in range(-3, 4):  # 長い鍔
        x, y = p.axis(4)
        p.put(x + u, y + u, S["h"])
        p.put(x + u + 1, y + u, S["g"])
    for t in (1, 2, 3):
        x, y = p.axis(t)
        p.put(x, y, S["b"] if t % 2 else S["n"])
        p.put(x + 1, y, S["n"] if t % 2 else S["b"])
    x, y = p.axis(0)
    p.put(x, y, S["h"])
    p.put(x + 1, y, S["g"])
    p.put(x, y - 1, S["H"])
    p.outline(OUTLINE)
    ex, ey = p.axis(4)
    ex += 4
    ey += 3
    p.put(ex, ey + 1, S["r"])
    p.put(ex + 1, ey + 1, S["R"])
    p.put(ex, ey + 2, S["r"])
    return p.icon()


def icon_staff():
    from pixelart import Pixel
    S = ICON
    p = Pixel()
    for t in range(1, 10):
        x, y = p.axis(t)
        p.put(x, y, S["a"])
        p.put(x + 1, y, S["q"])
    for t in (3, 4, 5):
        x, y = p.axis(t)
        p.put(x, y, S["b"])
        p.put(x + 1, y, S["n"])
    x, y = p.axis(0)
    p.put(x, y, S["h"])
    p.put(x + 1, y, S["g"])
    x, y = p.axis(10)
    p.put(x, y, S["h"])
    p.put(x + 1, y, S["g"])
    # 受け皿（軸と直角）
    for (px, py, k) in ((10, 4, "h"), (11, 4, "h"), (12, 5, "g"), (13, 5, "g"), (9, 3, "h")):
        p.put(px, py, S[k])
    # 燭台の腕（両側）と、まん中の結晶
    for (px, py, k) in ((10, 2, "a"), (11, 1, "A"), (14, 4, "a"), (15, 3, "A"),
                        (12, 3, "C"), (12, 2, "c"), (13, 2, "c"), (13, 3, "e"), (14, 1, "c"), (13, 1, "C")):
        p.put(px, py, S[k])
    p.outline(OUTLINE)
    # 赤い結び紐（腕と結晶をまとめる）
    for (px, py, k) in ((11, 2, "r"), (12, 3, "r"), (13, 4, "r"), (14, 5, "R")):
        p.put(px, py, S[k])
    return p.icon()


# ---------------------------------------------------------------- 持ち方

def weapon_display(kind):
    d = {
        "sword": ([0, -75, 5], [1.13, 1.5, 1.13], 0.52, 0.7, 2.7),
        "greatsword": ([0, -75, 10], [1.13, 1.0, 1.13], 0.45, 0.7, 2.0),
        "staff": ([0, -80, 5], [1.13, 0.5, 1.13], 0.45, 0.75, 2.25),
    }[kind]
    rot, tr, fs, ts, ty = d
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, ty, 0.5], "scale": [ts] * 3},
        "firstperson_righthand": {"rotation": rot, "translation": tr, "scale": [fs] * 3},
        "firstperson_lefthand": {"rotation": [rot[0], -rot[1], -rot[2]], "translation": tr, "scale": [fs] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }


SHIELD_FIRST = {"rotation": [-8, 165, 6], "translation": [12, 1, -12], "scale": [1.0, 1.0, 1.0]}
SHIELD_BLOCK = {"rotation": [0, 180, -5], "translation": [6, 6, -12], "scale": [1.1, 1.1, 1.1]}
SHIELD_GUI = {"rotation": [15, -25, -5], "translation": [4, 4.5, 0], "scale": [0.8, 0.8, 0.8]}


def build_all():
    items = []
    for build, kind, icon, label, note in (
            (build_sword, "sword", icon_sword, "見習いの剣", "輪の柄頭に赤い結び紐\n鍔のまん中に騎士団の紋\n樋のある明るい刃と刃こぼれ"),
            (build_greatsword, "greatsword", icon_greatsword, "見習いの大剣", "長い両手の握り（革2か所）\n刃の根元の革巻きと受けの突起\n鍔の端に赤い結び紐"),
            (build_staff, "staff", icon_staff, "見習いの杖", "燭台の形の先：受け皿と2本の腕\n練習用の結晶（光らない）\nを赤い紐で縛る"),
            (build_shield, "shield", None, "見習いの盾", "板を並べた丸い盾と八角形の縁\n色あせた騎士団の紋（ひし形）\n下の縁に赤い結び紐")):
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(ApprenticePainter(m), frames=1)
        items.append({"build": build, "kind": kind, "model": m, "frames": frames, "tex": frames[0],
                      "icon": icon() if icon else None, "label": label, "note": note})
    return items


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_apprentice"
    os.makedirs(out, exist_ok=True)
    items = build_all()
    for it in items:
        m = it["model"]
        if it["kind"] == "shield":
            normal, blocking = weapons.shield_display(SHIELD_FIRST, SHIELD_BLOCK, third_scale=1.0, gui=SHIELD_GUI)
            weapons.export_shield(repo, "apprentice_shield", m, it["frames"], CREDIT, normal, blocking)
            it["gui"] = SHIELD_GUI
        else:
            weapons.export_weapon(repo, m.name, m, it["frames"], it["icon"], it["kind"], CREDIT,
                                  display=weapon_display(it["kind"]))
    from setsheet import make_set_sheet
    from materials import RAMPS as R
    rows = []
    for it in items:
        row = {"label": it["label"], "rarity": "コモン（見習い装備）", "note": it["note"], "model": it["model"],
               "tex": it["tex"], "icon": it["icon"], "gui": it.get("gui")}
        if it["kind"] == "shield":
            row["views"] = [("正面", 0, 0), ("側面", -90, 0), ("背面", 180, 0), ("斜め", -35, 16)]
        else:
            row["views"] = [("正面", 0, 0), ("側面", -90, 0), ("背面", 180, 0), ("斜め", -35, 16)]
        rows.append(row)
    sw, gs, st, sh = (it["model"] for it in items)
    tx = {it["model"].name: it["tex"] for it in items}
    details = [
        {"label": "剣：輪の柄頭と結び紐", "model": sw, "tex": tx[sw.name], "center": (8, -2.5, 8), "yaw": -25, "pitch": 12, "scale": 26,
         "note": "仮登録の印の、赤い結び紐"},
        {"label": "剣：鍔の騎士団の紋", "model": sw, "tex": tx[sw.name], "center": (8, 6.5, 8), "yaw": -20, "pitch": 8, "scale": 30,
         "note": "すり減ったひし形の引っかき傷"},
        {"label": "大剣：刃の根元の革巻き", "model": gs, "tex": tx[gs.name], "center": (8, 11, 8), "yaw": -30, "pitch": 10, "scale": 20,
         "note": "手を添えて振るための革と突起"},
        {"label": "杖：燭台の先", "model": st, "tex": tx[st.name], "center": (8, 20.5, 8), "yaw": -30, "pitch": 16, "scale": 26,
         "note": "灯をともす練習の結晶"},
    ]
    palette = [
        ("主：明るい鉄（刃）と黒い鉄（金具）", [R["steel"][i] for i in (2, 3, 4, 5, 6)] + [R["darksteel"][i] for i in (2, 4)]),
        ("副：白っぽい木と黄土色の革", [R["ash"][i] for i in (2, 3, 4, 5)] + [R["leather"][i] for i in (2, 3, 4)]),
        ("差し色：赤い結び紐（仮登録の印）", [R["cord"][i] for i in (2, 3, 4, 5)]),
        ("紋：色あせた藍と白", [(52, 70, 120), (90, 104, 140), (226, 224, 210)]),
    ]
    sheet_path = os.path.join(repo, "docs", "gear", "apprentice_sheet.png")
    make_set_sheet(sheet_path, "見習い装備（ギルドが貸す、結晶騎士団の練習用の装備）",
                   "コモン・光る部分なし｜共通の印：赤い結び紐・すり減った騎士団の紋・明るい鉄と白っぽい木と黄土色の革",
                   rows, details=details, palette=palette)
    import shutil
    shutil.copy(sheet_path, os.path.join(out, "apprentice_sheet.png"))
    for it in items:
        if it["icon"] is not None:
            it["icon"].big(8).save(os.path.join(out, it["model"].name + "_icon.png"))


if __name__ == "__main__":
    main()
