"""水門の鍵盾（3階のボス「大水門の番人ハイドラム」のレア装備）のモデル・絵・確認画像を作る。

  python3 tools/artgen/make_floodgate_key_shield.py <リポジトリ> [確認画像の出力先]

モデルは盾の中心が (0, 0)、表が +z（バニラの盾と同じ置き方。握りは −z 側）。1ピクセル＝1/16ブロック。
デザインの考え方と部品の表は docs/gear/floodgate_key_shield.md。
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from javamodel import JavaModel, save_frames  # noqa: E402

NAME = "floodgate_key_shield"
TEX = 128

# ---------------------------------------------------------------- 形

WHEEL_C = (0.0, -1.0)   # ハンドルの中心
ARCH_C = (0.0, 3.0)     # アーチの中心
ARCH_R = (6.0, 9.0)     # アーチの内側・外側の半径


def build():
    m = JavaModel(NAME + "_model", TEX, TEX)

    # 扉（面）：苔鉄の裏板。表は板と板の間の暗い溝、裏は盾の裏面になる
    m.box("leaf", (-5, -9, 1), (5, 6, 2), "iron_back", tags={"part": "leaf"})
    # 扉の縦板（3枚）。間の1ピクセルは溝、レールとの間の1ピクセルは水路
    for k, x in enumerate((-4, -1, 2)):
        m.box(f"slat_{k}", (x, -9, 2), (x + 2, 5, 3), "iron", tags={"part": "slat"})
    # 横の帯（上＝まぐさ、下）。リベットで縦板を留める
    m.box("band_top", (-4, 5, 2), (4, 6, 3.5), "iron", tags={"part": "band", "rivets": "x"})
    m.box("band_low", (-4, -8, 2), (4, -7, 3.5), "iron", tags={"part": "band", "rivets": "x"})

    # 柱（左右）：古代石を3段に積む。表へ張り出し、下は扉より長く伸びて足になる
    for k, (y0, y1) in enumerate(((-11, -5), (-5, 2), (2, 8))):
        pier = m.box(f"pier_l{k}", (-7, y0, 0), (-5, y1, 4), "stone", tags={"part": "pier"})
        m.mirror_x(pier, f"pier_r{k}")
    # 柱の上の受け石（アーチを受ける肩）と、下の台石：外形を「門」にする
    impost = m.box("impost_l", (-8, 7, -0.5), (-5, 8, 4.5), "stone", tags={"part": "impost"})
    m.mirror_x(impost, "impost_r")
    plinth = m.box("plinth_l", (-7.5, -11, -0.5), (-5, -10, 4.5), "stone", tags={"part": "plinth"})
    m.mirror_x(plinth, "plinth_r")
    # レール（苔鉄）：柱の内側の角に付けた、扉と水路の溝金具
    rail = m.box("rail_l", (-5.5, -9, 4), (-4.75, 8, 4.5), "iron", tags={"part": "rail"})
    m.mirror_x(rail, "rail_r")
    # 水を戻す管（裏）：柱の裏を上る
    pipe = m.box("pipe_l", (-6.75, -11, -2), (-5.25, 8.5, 0), "pipe", tags={"part": "pipe", "axis": "y"})
    m.mirror_x(pipe, "pipe_r")
    # 管の曲がり（下）：柱の下をくぐって表へ出る。表の口が光る
    elbow = m.box("elbow_l", (-6.75, -12, -2), (-5.25, -11, 4.5), "pipe", tags={"part": "elbow", "axis": "z"})
    m.mirror_x(elbow, "elbow_r")
    mouth = m.plane("mouth_l", (-6.5, -11.85, 4.53), (-5.5, -11.15, 4.53), "water", glow=True,
                    tags={"flow": "down"})
    m.mirror_x(mouth, "mouth_r")
    # 管の金具の帯
    for k, y in enumerate((-7.5, -1.0, 5.0)):
        clamp = m.box(f"clamp_l{k}", (-7, y, -2.25), (-5, y + 1, 0.0), "iron", tags={"part": "clamp"})
        m.mirror_x(clamp, f"clamp_r{k}")
    # 管の小窓（中の水が見える）
    for k, y in enumerate((-5.0, 2.0)):
        win = m.plane(f"window_l{k}", (-6.5, y, -2.03), (-5.5, y + 2, -2.03), "water", facing="north", glow=True,
                      tags={"flow": "up"})
        m.mirror_x(win, f"window_r{k}")

    # 表の水路（光る水）：レールの内側の溝を、上から下へ流れる
    ch = m.box("channel_l", (-5, -9, 2), (-4, 8, 2.5), "water", glow=True, tags={"flow": "down"})
    m.mirror_x(ch, "channel_r")

    # 敷居（下）：古代石
    m.box("sill", (-5, -11, 0), (5, -9, 4), "stone", tags={"part": "sill"})

    # アーチ下の石（紋章を彫る）
    m.box("tympanum", (-4, 6, 1.5), (4, 10, 3), "stone", tags={"part": "tympanum"})

    # アーチ：古代石を5つ、扇形に組む（22.5度刻み）
    cx, cy = ARCH_C
    r0, r1 = ARCH_R
    rm = (r0 + r1) / 2
    t = 3.0
    for k, theta in enumerate((135.0, 112.5, 90.0, 67.5, 45.0)):
        a = math.radians(theta)
        px, py = cx + rm * math.cos(a), cy + rm * math.sin(a)
        if theta == 90.0:
            # 要石：一回り大きく、一段前に出る
            m.box("keystone", (px - 1.5, cy + r0 - 0.5, 0), (px + 1.5, cy + r1 + 1.0, 5), "stone",
                  tags={"part": "keystone"})
            continue
        m.box(f"voussoir_{k}", (px - t / 2, py - (r1 - r0) / 2, 0), (px + t / 2, py + (r1 - r0) / 2, 4), "stone",
              rot=((px, py, 2.0), "z", theta - 90.0), tags={"part": "voussoir"})
    # 要石の結晶（ひし形）
    ky = cy + (r0 + r1) / 2
    m.box("crystal", (-0.9, ky - 0.9, 5), (0.9, ky + 0.9, 5.9), "crystal", rot=((0, ky, 5), "z", 45.0),
          glow=True)

    # ハンドル（鍵）：八角形の輪・十字のスポーク・軸の頭
    wx, wy = WHEEL_C
    z0, z1 = 4.0, 5.0
    m.box("axle", (wx - 1, wy - 1, 3), (wx + 1, wy + 1, 4), "iron", tags={"part": "axle"})
    m.box("ring_top", (wx - 2, wy + 4, z0), (wx + 2, wy + 5, z1), "iron_worn", tags={"part": "ring"})
    m.box("ring_bot", (wx - 2, wy - 5, z0), (wx + 2, wy - 4, z1), "iron_worn", tags={"part": "ring"})
    m.box("ring_l", (wx - 5, wy - 2, z0), (wx - 4, wy + 2, z1), "iron_worn", tags={"part": "ring"})
    m.box("ring_r", (wx + 4, wy - 2, z0), (wx + 5, wy + 2, z1), "iron_worn", tags={"part": "ring"})
    s = 2 * 4.5 * math.tan(math.radians(22.5))
    d = 4.5 * math.cos(math.radians(45))
    for k, (sx, sy, ang) in enumerate(((1, 1, -45.0), (-1, 1, 45.0), (-1, -1, -45.0), (1, -1, 45.0))):
        ox, oy = wx + sx * d, wy + sy * d
        m.box(f"ring_d{k}", (ox - s / 2 - 0.1, oy - 0.5, z0), (ox + s / 2 + 0.1, oy + 0.5, z1), "iron_worn",
              rot=((ox, oy, z0), "z", ang), tags={"part": "ring"})
    m.box("spoke_v", (wx - 1, wy - 4, z0 + 0.1), (wx + 1, wy + 4, z1 - 0.1), "iron_worn", tags={"part": "spoke"})
    m.box("spoke_h", (wx - 4, wy - 1, z0 + 0.1), (wx + 4, wy + 1, z1 - 0.1), "iron_worn", tags={"part": "spoke"})
    m.box("hub", (wx - 2, wy - 3, z0), (wx + 2, wy + 2, 6), "iron_worn", tags={"part": "hub"})
    # 鍵穴：45度に傾けた小さな四角（丸い頭）と、その下の縦の溝
    hy = wy + 0.4
    m.plane("keyhole_head", (-0.9, hy - 0.9, 6.03), (0.9, hy + 0.9, 6.03), "keyhole", rot=((0, hy, 6.03), "z", 45.0),
            glow=True, tags={"part": "head"})
    m.plane("keyhole_slot", (-0.5, wy - 2.4, 6.04), (0.5, hy - 0.3, 6.04), "keyhole", glow=True, tags={"part": "slot"})

    # 裏：補強の横木・握り・腕帯・軸のナット
    m.box("brace_top", (-5, 6, 0), (5, 7, 1), "iron", tags={"part": "brace", "rivets": "x"})
    m.box("brace_low", (-5, -8, 0), (5, -7, 1), "iron", tags={"part": "brace", "rivets": "x"})
    m.box("nut", (wx - 1, wy - 1, 0), (wx + 1, wy + 1, 1), "iron_worn", tags={"part": "nut"})
    m.box("grip_bracket_top", (-1, 2, -3), (1, 3, 1), "iron", tags={"part": "bracket"})
    m.box("grip_bracket_low", (-1, -4, -3), (1, -3, 1), "iron", tags={"part": "bracket"})
    m.box("grip", (-1, -4, -5), (1, 3, -3), "leather", tags={"part": "grip"})
    m.box("strap", (-3.5, 4, -1), (3.5, 5.5, 0), "leather", tags={"part": "strap"})
    stp = m.box("strap_staple_l", (-4.5, 3.75, -1.5), (-3.5, 5.75, 1), "iron", tags={"part": "staple"})
    m.mirror_x(stp, "strap_staple_r")
    return m


# ---------------------------------------------------------------- 色と模様

def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*colors):
    return [hexc(c) for c in colors]


RAMPS = {
    # 古代石（水路の石。青みのある灰色）
    "stone": ramp("#2C3338", "#454F56", "#606D75", "#7C8A92", "#98A6AD", "#B7C3C8", "#D8E1E3"),
    # 濡れた古代石（水位より下。暗く、緑がかる）
    "stone_wet": ramp("#202826", "#323E39", "#45544C", "#5A6A60", "#6F8174", "#889A8B", "#A6B6A6"),
    # 苔鉄（緑がかった暗い鉄）
    "iron": ramp("#16191A", "#232827", "#323836", "#434A47", "#59615D", "#757E79", "#9AA39E"),
    "iron_wet": ramp("#121615", "#1D2421", "#29322D", "#38433C", "#4B584F", "#647266", "#849486"),
    # 手で磨かれた苔鉄（ハンドル）
    "worn": ramp("#1C2321", "#303B37", "#48554F", "#62706A", "#808E87", "#A4B1AA", "#CFD9D3"),
    # 革（後から付けた握りと腕帯）
    "leather": ramp("#26170E", "#402617", "#5C3922", "#784C2E", "#94623B", "#B17F50", "#CFA06C"),
    # 魔力の水（光る）
    "water": ramp("#0A4A80", "#146AAA", "#2390D2", "#3FB4EC", "#79D5F7", "#BBEDFF", "#F2FDFF"),
    "crystal": ramp("#185E9E", "#2D8FD2", "#5CC3F2", "#9DE6FF", "#D9F8FF", "#FFFFFF", "#FFFFFF"),
}
MOSS = [hexc("#3B4D35"), hexc("#4B6140"), hexc("#5E7650"), hexc("#728A5E")]
RUST = [hexc("#5E3B27"), hexc("#7C4E31"), hexc("#9C653C")]
SCALE = hexc("#C9D3CC")  # 水位の跡（白い水あか）

BEVEL_LIGHT = np.array([-0.5, 0.85, 0.5]) / np.linalg.norm([-0.5, 0.85, 0.5])
WATERLINE = -4.6
FLOW_PERIOD = 8


def hsh(*args):
    x = 0x9E3779B97F4A7C15
    for a in args:
        x ^= (int(a) * 0x100000001B3 + 0x9E3779B9 + (x << 6) + (x >> 2)) & 0xFFFFFFFFFFFFFFFF
        x = (x * 0xff51afd7ed558ccd) & 0xFFFFFFFFFFFFFFFF
        x ^= x >> 29
    return (x & 0xFFFFFF) / float(0x1000000)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def pick(r, tone):
    return r[max(0, min(len(r) - 1, int(round(tone))))]


class Painter:
    def __init__(self, model):
        self.index = {id(e): k for k, e in enumerate(model.elements)}
        self.cracks = {}

    def __call__(self, c):
        rgb = getattr(self, "paint_" + c.el.mat)(c)
        if rgb is None:
            return (0, 0, 0, 0)
        return (*rgb, 255)

    # ---- 共通 ----
    def eid(self, c):
        return self.index[id(c.el)]

    def tex(self, c, salt):
        """1ピクセルごとのばらつき（0〜1）。"""
        return hsh(self.eid(c), "nsewud".index(c.face.d[0]) + (6 if c.face.d == "south" else 0), c.i, c.j, salt)

    def blob(self, c, size, salt):
        """まとまった斑（モデルの中の位置で決める。隣の面とつながる）。"""
        q = c.p - c.n * 0.25
        return hsh(int(math.floor(q[0] / size)), int(math.floor(q[1] / size)), int(math.floor(q[2] / size)), salt)

    def bevel(self, c, up=1.2, down=1.0):
        """面のふち：光の来る側のふちは明るく、反対側は暗く（面取りした角に見える）。"""
        tone = 0.0
        dirs = c.edge_dirs()
        dist = c.edge_dist()
        for side in ("left", "right", "top", "bottom"):
            if dist[side] != 0:
                continue
            if side in ("left", "right") and c.W <= 1:
                continue
            if side in ("top", "bottom") and c.H <= 1:
                continue
            dot = float(np.dot(dirs[side], BEVEL_LIGHT))
            if dot > 0.2:
                tone += up
            elif dot < -0.2:
                tone -= down
        return max(-1.6, min(1.8, tone))

    def water_state(self, c):
        """水位の跡：線より下は濡れて暗い。線の上の1ピクセルは白い水あか。"""
        line = WATERLINE + 0.3 * math.sin(c.p[0] * 1.3 + c.p[2] * 0.7)
        y = c.p[1]
        if y < line - 0.5:
            return "wet"
        if y < line + 0.5:
            return "line"
        return "dry"

    # ---- 古代石 ----
    def paint_stone(self, c):
        tone = 3.15 + (self.blob(c, 2.0, self.eid(c)) - 0.5) * 0.45 + (self.tex(c, 1) - 0.5) * 0.2
        r = self.tex(c, 2)
        if r < 0.035:
            tone -= 0.9
        elif r > 0.975:
            tone += 0.7
        tone += self.bevel(c, up=1.3, down=1.1)
        tone += self.crack(c)
        if abs(c.n[0]) > 0.6:
            tone += 0.45
        part = c.el.tags.get("part")
        if part == "tympanum" and c.face.d == "south":
            tone -= 0.3
            if int(math.floor(c.p[0])) == 0 and c.p[1] > 7:
                tone -= 1.0
        if part == "keystone" and c.face.d == "south":
            tone += 0.4
        state = self.water_state(c)
        col = pick(RAMPS["stone"], tone)
        if state == "wet":
            col = mix(col, pick(RAMPS["stone_wet"], tone), 0.6)
        elif state == "line" and abs(c.n[1]) < 0.5:
            col = mix(col, SCALE, 0.4)
        # 苔：上を向いた面と、いちばん下（水に浸かっていた所）に、まとまって生える
        up_face = c.n[1] > 0.6
        bottom = c.p[1] < -10.2
        if (up_face and self.blob(c, 1.0, 300 + self.eid(c)) < (0.32 if state == "wet" else 0.13)) or \
                (bottom and state == "wet" and self.blob(c, 1.0, 400) < 0.18):
            col = MOSS[1 + (1 if up_face else 0) + (1 if self.tex(c, 5) > 0.7 else 0) - (1 if self.tex(c, 6) < 0.25 else 0)]
        return col

    def crack(self, c):
        """大きな面に、短いひびを1本。"""
        fkey = (self.eid(c), c.face.d)
        if fkey not in self.cracks:
            pts = set()
            seed = fkey[0] * 7 + len(c.face.d)
            if c.W * c.H >= 12 and hsh(seed, 7) < 0.6:
                x = 1 + int(hsh(seed, 8) * max(1, c.W - 2))
                y = int(hsh(seed, 9) * max(1, c.H - 3))
                dx = 1 if hsh(seed, 10) < 0.5 else -1
                for step in range(2 + int(hsh(seed, 11) * 2)):
                    pts.add((x, y))
                    y += 1
                    if hsh(seed, 12, step) < 0.5:
                        x += dx
            self.cracks[fkey] = pts
        return -1.3 if (c.i, c.j) in self.cracks[fkey] else 0.0

    # ---- 苔鉄 ----
    def iron_color(self, c, tone, moss_chance=0.18):
        state = self.water_state(c)
        col = pick(RAMPS["iron"], tone)
        if state == "wet":
            col = mix(col, pick(RAMPS["iron_wet"], tone), 0.6)
        elif state == "line" and abs(c.n[1]) < 0.5:
            col = mix(col, SCALE, 0.3)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 500 + self.eid(c)) < moss_chance:
            col = MOSS[0 if self.tex(c, 7) < 0.5 else 1]
        return col

    def paint_iron(self, c):
        part = c.el.tags.get("part")
        tone = 3.0 + (self.blob(c, 2.0, 20 + self.eid(c)) - 0.5) * 0.35 + (self.tex(c, 21) - 0.5) * 0.2
        if part == "slat":
            # 縦板：ばらつきのない落ち着いた面にして、前のハンドルを目立たせる（光の来る側のふちだけ明るい）
            tone = 2.6 + self.bevel(c, up=1.1, down=0.4)
            return self.iron_color(c, tone, moss_chance=0.0)
        tone += self.bevel(c, up=1.6, down=0.9)
        tone += self.rivet(c)
        return self.iron_color(c, tone)

    def rivet(self, c):
        """帯と横木のリベット（左右対称の位置）。"""
        if c.el.tags.get("rivets") != "x" or abs(c.n[2]) < 0.5:
            return 0.0
        cols = (-4, -1, 0, 3) if c.el.tags.get("part") == "band" else (-5, -1, 0, 4)
        return 2.3 if int(math.floor(c.p[0])) in cols else 0.0

    def paint_iron_back(self, c):
        if c.face.d == "south":
            # 縦板の間の溝から見える奥（暗い）
            return pick(RAMPS["iron"], 0.9 + (self.tex(c, 31) - 0.5) * 0.4)
        x = int(math.floor(c.p[0]))
        y = int(math.floor(c.p[1]))
        tone = 2.9 + (self.blob(c, 2.0, 32) - 0.5) * 0.35
        if c.face.d == "north":
            if x in (-2, 1):
                tone -= 1.2                                # 板の継ぎ目
                if (y + 9) % 3 == 1:
                    tone += 3.4                            # 継ぎ目のリベット
            elif y in (5, -9) and x in (-4, 3):
                tone += 2.6
        tone += self.bevel(c, up=1.2, down=0.8)
        return self.iron_color(c, tone, moss_chance=0.0)

    def paint_iron_worn(self, c):
        tone = 3.2 + (self.tex(c, 41) - 0.5) * 0.25
        part = c.el.tags.get("part")
        if part == "hub" and c.n[2] > 0.6:
            tone = 1.8  # 鍵穴の板：すすけて暗い（光る鍵穴が目立つ）
        elif part == "spoke":
            tone = 2.7 + (0.3 if c.n[2] > 0.6 else -0.3)
        elif c.n[2] > 0.6:
            tone += 1.1  # 手で回して磨かれた輪の表
        elif abs(c.n[2]) < 0.4:
            tone -= 0.5
        tone += self.bevel(c, up=1.8, down=0.9)
        col = pick(RAMPS["worn"], tone)
        if self.water_state(c) == "wet":
            col = mix(col, pick(RAMPS["iron_wet"], tone), 0.35)
        return col

    def paint_pipe(self, c):
        axis = c.el.tags.get("axis", "y")
        a = np.array({"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[axis], dtype=float)
        du = c.du / (np.linalg.norm(c.du) or 1)
        if abs(float(np.dot(c.n, a))) > 0.9:
            tone = 2.2 if (0 < c.i < c.W - 1 and 0 < c.j < c.H - 1) else 3.6
        else:
            across_u = abs(float(np.dot(du, a))) < 0.5
            n_across = c.W if across_u else c.H
            pos = c.i if across_u else c.j
            t = (pos + 0.5) / n_across - 0.5
            tone = 2.6 + 1.5 * (1 - abs(t) * 2)  # 丸い管に見える明暗（まん中が明るい）
        tone += (self.tex(c, 51) - 0.5) * 0.25
        return self.iron_color(c, tone, moss_chance=0.25)

    # ---- 革（後から付けたもの。水の跡はない） ----
    def paint_leather(self, c):
        part = c.el.tags.get("part")
        tone = 3.0 + (self.tex(c, 61) - 0.5) * 0.3
        if part == "grip":
            tone += (0.7, 0.1, -1.0)[(c.i + c.j) % 3]   # 斜めに巻いた革紐
        else:
            if c.j == 0 and c.i % 2 == 1 and abs(c.n[2]) > 0.5:
                tone += 2.0                              # 縫い目
            if c.j == c.H - 1:
                tone -= 0.7
        tone += self.bevel(c, up=0.8, down=0.6)
        return pick(RAMPS["leather"], tone)

    # ---- 光る水・結晶・鍵穴 ----
    def flow_tone(self, c, period=FLOW_PERIOD):
        flow = c.el.tags.get("flow", "down")
        s = -c.p[1] if flow == "down" else c.p[1]
        shift = c.frame * period / max(1, c.frames)
        cell = int(math.floor(s - shift)) % period
        wave = (3.0, 3.6, 4.4, 4.0, 3.2, 2.5, 2.2, 2.6)[cell]
        lane = int(math.floor(c.p[0] * 2)) + int(math.floor(c.p[2] * 2))
        sparkle = hsh(int(math.floor(s - shift)) // period, int(math.floor(s - shift)) % period, lane, 71) < 0.07
        return 6.0 if sparkle else wave + (hsh(lane, 72) - 0.5) * 0.4

    def paint_water(self, c):
        tone = self.flow_tone(c)
        if abs(c.n[2]) < 0.5 and c.el.tags.get("flow") == "down" and c.face.d not in ("up", "down"):
            tone -= 0.6  # 溝の側面
        return pick(RAMPS["water"], tone)

    def paint_crystal(self, c):
        if c.face.d == "south":
            tone = (4.6, 3.3, 3.3, 2.0)[min(3, c.j * 2 + c.i)]
        else:
            tone = 2.6 + (0.6 if c.n[1] > 0.3 else 0.0)
        return pick(RAMPS["crystal"], tone)

    def paint_keyhole(self, c):
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * c.frame / max(1, c.frames))
        if c.el.tags.get("part") == "head":
            tone = 5.0 + pulse * 0.8
        else:
            tone = 4.4 - 0.5 * c.j + pulse * 0.6
        return pick(RAMPS["water"], tone)


SHEET = {
    "title": "水門の鍵盾",
    "subtitle": "盾（ナイト）・エピック・3階のボス「大水門の番人ハイドラム」のレア装備　｜　幅16×高さ25×奥行き11ピクセル（1ピクセル＝1/16ブロック）",
    "details": [
        {"label": "ハンドルと鍵穴", "center": (0, -1, 5), "yaw": -20, "pitch": 10, "scale": 26,
         "note": "輪は手で磨かれて明るく、鍵穴の板はすすけて暗い"},
        {"label": "アーチと要石の結晶", "center": (0, 9.5, 2.5), "yaw": -25, "pitch": 18, "scale": 24,
         "note": "石を22.5度ずつ傾けて組んだアーチ"},
        {"label": "裏：握り・腕帯・水を戻す管", "center": (0, -0.5, -1), "yaw": 155, "pitch": 12, "scale": 16,
         "note": "管の小窓から、上へ戻る水が見える"},
        {"label": "足と管の口", "center": (4.5, -9.5, 2.5), "yaw": -30, "pitch": 24, "scale": 22,
         "note": "柱の下をくぐった管が表に口を開ける"},
    ],
    "palette": [
        ("主：古代石（濡れると暗く・緑に）", [RAMPS["stone"][i] for i in (1, 2, 3, 4, 5, 6)]),
        ("副：苔鉄（手で磨かれた所は明るい）", [RAMPS["iron"][i] for i in (1, 2, 3, 4, 5)] + [RAMPS["worn"][5]]),
        ("差し色：結晶の水（光る）", [RAMPS["water"][i] for i in (1, 2, 3, 4, 5, 6)]),
        ("裏の革（後から付けた握り）", [RAMPS["leather"][i] for i in (1, 2, 3, 4, 5)]),
    ],
}


def export(m, frames, repo):
    """Modの素材として書き出す（モデル3つ・絵・アニメーションの設定）。"""
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    models = os.path.join(base, "models/item")
    textures = os.path.join(base, "textures/item")
    tex_ref = "amemaru:item/" + NAME + "_model"
    credit = "AMEMARU: 水門の鍵盾（tools/artgen/make_floodgate_key_shield.py で生成。docs/gear/floodgate_key_shield.md）"
    m.save_json(os.path.join(models, NAME + "_model.json"), tex_ref, credit, extra={"gui_light": "front"})
    import json
    for suffix, display, extra in (("", display_normal(), {"overrides": [
            {"predicate": {"blocking": 1}, "model": "amemaru:item/" + NAME + "_blocking"}]}),
            ("_blocking", display_blocking(), {})):
        data = {"parent": "amemaru:item/" + NAME + "_model", "display": display}
        data.update(extra)
        with open(os.path.join(models, NAME + suffix + ".json"), "w", encoding="utf-8") as fp:
            json.dump(data, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
    save_frames(frames, os.path.join(textures, NAME + "_model.png"), frametime=4)


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/fks"
    os.makedirs(out, exist_ok=True)
    m = build()
    m.cull_hidden_faces()
    used = m.pack()
    frames = m.paint(Painter(m), frames=8)
    print("部品:", len(m.elements), "面:", sum(len(e.faces) for e in m.elements), "絵の使った高さ:", used)
    export(m, frames, repo)
    from sheet import make_sheet
    info = dict(SHEET, gui=display_normal()["gui"])
    make_sheet(m, frames[0], info, os.path.join(out, NAME + "_sheet.png"))
    docs = os.path.join(repo, "docs", "gear")
    os.makedirs(docs, exist_ok=True)
    make_sheet(m, frames[0], info, os.path.join(docs, NAME + "_sheet.png"))




# ---------------------------------------------------------------- 持ったときの向き（display）

def display_normal():
    """ふだん（構えていない）。バニラの盾と同じ置き方で、少し大きい分だけ縮める。"""
    return {
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.1, 1.1, 1.1]},
        "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.1, 1.1, 1.1]},
        "gui": {"rotation": [15, -25, -5], "translation": [3, 2.5, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.5, 0.5, 0.5]},
        "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
    }


def display_blocking():
    """構えたとき（バニラの盾の構えと同じ動き）。"""
    d = display_normal()
    d.update({
        "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.1, 1.1, 1.1]},
        "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.1, 1.1, 1.1]},
    })
    return d


def gui_icon(m, tex, gui, px=2, ss=1):
    """持ち物の枠（16×16。GUIの大きさ2なら32×32）に描いたときの見た目。"""
    from render import render, display_transform
    return render(m, tex, scale=px, size=(16 * px, 16 * px), ss=ss, transform=display_transform(gui), light="gui")


if __name__ == "__main__":
    main()
