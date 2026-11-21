"""装備の絵を塗るための共通の材質（色の段階と、面取り・ばらつき・飾りの塗り方）。

  class MyPainter(materials.Painter):
      def paint_stone(self, c): ...   # 材質ごとに上書きできる
  frames = model.paint(MyPainter(model), frames=1)

材質は、部品の mat（"stone"・"iron" など）で決まる。部品の tags で飾りを足す：
  grooves=[y, ...]        … 表と裏の面に、横の「文字の溝」（道標の文字）を彫る（ワールドのyの位置）
  rivets=[(x, y), ...]    … 表と裏の面のリベット
  wrap=True               … 斜めに巻いた紐・革（握り）
  stitch=True             … ふちの縫い目
  edge="left"/"right"/"both" … 刃のふち（研いだ明るい線）。面の左右のふち
  grain="y"/"x"           … 木目の向き
  crest=(x, y)            … ひし形の紋（すり減った刻印）
"""
import math

import numpy as np


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*colors):
    return [hexc(c) for c in colors]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def pick(r, tone):
    return r[max(0, min(len(r) - 1, int(round(tone))))]


def hsh(*args):
    x = 0x9E3779B97F4A7C15
    for a in args:
        x ^= (int(a) * 0x100000001B3 + 0x9E3779B9 + (x << 6) + (x >> 2)) & 0xFFFFFFFFFFFFFFFF
        x = (x * 0xff51afd7ed558ccd) & 0xFFFFFFFFFFFFFFFF
        x ^= x >> 29
    return (x & 0xFFFFFF) / float(0x1000000)


# ---------------------------------------------------------------- 色の段階（暗い → 明るい、7段）

RAMPS = {
    # 古代石：灰青色（素材の古代石の絵 #95A0AD に合わせる）
    "stone": ramp("#2C3338", "#454F56", "#606D75", "#7C8A92", "#98A6AD", "#B7C3C8", "#D8E1E3"),
    # 苔鉄：古代の鉄（素材の苔鉄の絵 #70736D・#8F938B に合わせる）
    "iron": ramp("#16191A", "#232827", "#323836", "#434A47", "#59615D", "#757E79", "#9AA39E"),
    # 手で磨かれた苔鉄
    "worn": ramp("#1C2321", "#303B37", "#48554F", "#62706A", "#808E87", "#A4B1AA", "#CFD9D3"),
    # 見習いの鉄：よく磨いた明るい鉄（少し暖かい灰色）
    "steel": ramp("#2E2F33", "#4A4C52", "#6A6D73", "#8C9096", "#AEB2B6", "#CFD2D3", "#F0F1EE"),
    # 鍛えた黒っぽい鉄（見習いの鍔・金具）
    "darksteel": ramp("#17171A", "#25252A", "#35363C", "#47494F", "#5D6067", "#7B7E85", "#A2A5AB"),
    # トネリコ（白っぽい木）
    "ash": ramp("#4A3420", "#6B4C2F", "#8C6741", "#AE8657", "#C9A473", "#E0C394", "#F2DDB4"),
    # クルミ（暗い木）
    "walnut": ramp("#24170F", "#3A2618", "#523723", "#6C4930", "#875D3E", "#A57750", "#C29467"),
    # 黄土色の革
    "leather": ramp("#2E1C10", "#4A2E1A", "#6A4426", "#8A5C34", "#A97644", "#C79459", "#E0B276"),
    # こげ茶の革（ベルト・帯）
    "leather_dark": ramp("#170F0A", "#271911", "#3A261A", "#4E3424", "#654430", "#7F583F", "#9C7052"),
    # 赤い結び紐（ギルドの仮登録の印）
    "cord": ramp("#3A0E0C", "#5E1712", "#86221A", "#AE3123", "#CC4B32", "#E2714F", "#F29A74"),
    # ツノモフの毛（生成り）
    "wool": ramp("#5A5040", "#7A6E5A", "#9C8F78", "#BCAF96", "#D6CAB0", "#E9DFC8", "#F7F0E0"),
    # 苔色に染めたツノモフの毛（法衣）
    "wool_moss": ramp("#1B2219", "#273224", "#364531", "#475A3F", "#5B7150", "#738A64", "#90A67E"),
    # 青銅（結晶蜂の殻）
    "bronze": ramp("#2E2012", "#4A341C", "#6A4C28", "#8C6636", "#AD8247", "#C9A15F", "#E2C385"),
    # 骨（欠け牙の大剣の柄）
    "bone": ramp("#4A4234", "#6C6250", "#8E846D", "#AFA68C", "#CBC3A8", "#E1DAC2", "#F3EEDD"),
    # 牙（白い。少し青い影）
    "fang": ramp("#5C6370", "#7D8592", "#9DA5B0", "#BCC3CB", "#D6DBE0", "#EAEDF0", "#FAFBFC"),
    # 狼の毛（灰青）
    "fur_wolf": ramp("#22262E", "#323843", "#444B57", "#5B6270", "#727A88", "#8D95A2", "#AAB1BC"),
    # 光らない結晶の欠片（コモン）
    "crystal_dim": ramp("#2A4A56", "#3D6673", "#558592", "#72A4AE", "#97C3C9", "#BFDDE0", "#E6F4F5"),
    # 光る：石くれ兵の核の青緑（見張りの石刃）
    "glow_teal": ramp("#0B5149", "#137266", "#1F9585", "#38B9A6", "#6FE3D0", "#B0F5EA", "#EFFFFC"),
    # 光る：ロストの白（欠け牙の大剣）
    "glow_lost": ramp("#7F8DA6", "#9AA8BF", "#B6C3D6", "#CFDAE9", "#DDEBFF", "#F0F6FF", "#FFFFFF"),
    # 光る：結晶蜂の結晶（淡い青）
    "glow_bee": ramp("#1E5E96", "#2F7EBE", "#4E9EDD", "#73BAF0", "#8FD3FF", "#C6EAFF", "#F5FBFF"),
    # 光る：結晶蜂の目の琥珀色
    "glow_amber": ramp("#7A4A0C", "#A36714", "#C98A1F", "#E8AA35", "#FFD46A", "#FFE7A8", "#FFF8E6"),
}
MOSS = [hexc("#3B4D35"), hexc("#4B6140"), hexc("#5E7650"), hexc("#728A5E")]
RUST = [hexc("#5E3B27"), hexc("#7C4E31"), hexc("#9C653C")]

FACE_INDEX = {"north": 0, "south": 1, "east": 2, "west": 3, "up": 4, "down": 5}


class Painter:
    """部品の mat ごとに paint_<mat> を呼ぶ。返す色が None なら透明。"""

    #: 面取りの光の向き（表がこちらを向く置き方で、左上・手前から当たる）
    light = np.array([-0.5, 0.85, 0.5]) / np.linalg.norm([-0.5, 0.85, 0.5])

    def __init__(self, model):
        self.index = {id(e): k for k, e in enumerate(model.elements)}
        self.cracks = {}

    def __call__(self, c):
        rgb = getattr(self, "paint_" + c.el.mat)(c)
        if rgb is None:
            return (0, 0, 0, 0)
        if len(rgb) == 4:
            return rgb
        return (*rgb, 255)

    # ---- 共通の道具 ----
    def eid(self, c):
        return self.index[id(c.el)]

    def tex(self, c, salt):
        """1ピクセルごとのばらつき（0〜1）。"""
        return hsh(self.eid(c), FACE_INDEX.get(c.face.d, 6), c.i, c.j, salt)

    def blob(self, c, size, salt):
        """まとまった斑（モデルの中の位置で決める。隣の面とつながる）。"""
        q = c.p - c.n * 0.25
        return hsh(int(math.floor(q[0] / size)), int(math.floor(q[1] / size)), int(math.floor(q[2] / size)), salt)

    def bevel(self, c, up=1.2, down=1.0):
        """面のふち：光の来る側は明るく、反対側は暗く（面取りした角に見える）。"""
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
            dot = float(np.dot(dirs[side], self.light))
            if dot > 0.2:
                tone += up
            elif dot < -0.2:
                tone -= down
        return max(-1.6, min(1.8, tone))

    def facing(self, c):
        """表・裏（大きな面）なら True。"""
        return abs(float(c.n[2])) > 0.6

    def std(self, c, ramp_name, base=3.0, noise=0.25, blob=0.35, blob_size=2.0, up=1.3, down=1.0, specks=0.0):
        tone = base + (self.tex(c, 11) - 0.5) * noise + (self.blob(c, blob_size, 12 + self.eid(c)) - 0.5) * blob
        if specks:
            r = self.tex(c, 13)
            if r < specks:
                tone -= 0.9
            elif r > 1 - specks * 0.6:
                tone += 0.7
        tone += self.bevel(c, up=up, down=down)
        return tone

    # ---- 飾り（tags） ----
    def grooves(self, c):
        """横の「文字の溝」：表・裏の面に、短い線が途切れながら並ぶ（道標の古代文字）。"""
        rows = c.el.tags.get("grooves")
        if not rows or not self.facing(c):
            return 0.0
        y = c.p[1]
        for k, gy in enumerate(rows):
            if math.floor(y) == math.floor(gy):
                # 文字の切れ目：2〜3ピクセルの線と1ピクセルの空き
                col = int(math.floor(c.p[0] * 1.0 + 0.001))
                if hsh(col, k, int(gy * 7)) < 0.25:
                    return 0.0
                return -1.6
            if math.floor(y) == math.floor(gy) - 1:
                return 0.5  # 溝の下のふち（光が当たる）
        return 0.0

    def rivet(self, c):
        pts = c.el.tags.get("rivets")
        if not pts or not self.facing(c):
            return 0.0
        for (rx, ry) in pts:
            if abs(c.p[0] - rx) < 0.5 and abs(c.p[1] - ry) < 0.5:
                return 2.3
        return 0.0

    def crest(self, c):
        """すり減ったひし形の紋（3×3の中に、ひし形の線）。"""
        at = c.el.tags.get("crest")
        if not at or not self.facing(c):
            return 0.0
        dx = int(math.floor(c.p[0] - at[0] + 1.5))
        dy = int(math.floor(at[1] - c.p[1] + 1.5))
        shape = (".#.", "#.#", ".#.")
        if 0 <= dx < 3 and 0 <= dy < 3 and shape[dy][dx] == "#":
            return -1.3 if hsh(dx, dy, 5) > 0.25 else -0.5  # ところどころすり減って薄い
        return 0.0

    def edge(self, c, amount=1.8):
        """刃のふち：研いだ明るい線（面の左右のふち）。"""
        side = c.el.tags.get("edge")
        if not side or not self.facing(c):
            return 0.0
        d = c.edge_dist()
        # 表と裏で左右が入れかわるので、ワールドのxで決める
        x = c.p[0]
        lo, hi = c.el.frm[0], c.el.to[0]
        if side in ("left", "both") and x - lo < 1.0:
            return amount
        if side in ("right", "both") and hi - x < 1.0:
            return amount
        return 0.0

    def wrap(self, c):
        if not c.el.tags.get("wrap"):
            return 0.0
        return (0.7, 0.1, -1.0)[(c.i + c.j) % 3]

    def stitch(self, c):
        if not c.el.tags.get("stitch") or not self.facing(c):
            return 0.0
        if c.j == 0 and c.i % 2 == 1:
            return 2.0
        return 0.0

    def grain(self, c):
        g = c.el.tags.get("grain")
        if not g:
            return 0.0
        q = c.p
        if g == "y":
            line = hsh(int(math.floor(q[0] * 2)), int(math.floor(q[2] * 2)), 41)
            run = hsh(int(math.floor(q[0] * 2)), int(math.floor(q[1] / 3)), 42)
        else:
            line = hsh(int(math.floor(q[1] * 2)), int(math.floor(q[2] * 2)), 41)
            run = hsh(int(math.floor(q[1] * 2)), int(math.floor(q[0] / 3)), 42)
        if line < 0.25 and run < 0.7:
            return -0.7
        return (line - 0.5) * 0.4

    # ---- よく使う材質 ----
    def paint_stone(self, c):
        tone = self.std(c, "stone", base=3.15, noise=0.2, blob=0.45, up=1.3, down=1.1, specks=0.035)
        tone += self.grooves(c) + self.crest(c) + self.rivet(c)
        if abs(c.n[0]) > 0.6:
            tone += 0.35
        col = pick(RAMPS["stone"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 300 + self.eid(c)) < 0.12:
            col = MOSS[1 + (1 if self.tex(c, 5) > 0.6 else 0)]
        return col

    def paint_iron(self, c):
        tone = self.std(c, "iron", base=3.0, noise=0.2, blob=0.35, up=1.6, down=0.9)
        tone += self.rivet(c) + self.grooves(c)
        col = pick(RAMPS["iron"], tone)
        if c.n[1] > 0.6 and self.blob(c, 1.0, 500 + self.eid(c)) < 0.18:
            col = MOSS[0 if self.tex(c, 7) < 0.5 else 1]
        return col

    def paint_worn(self, c):
        tone = self.std(c, "worn", base=3.2, noise=0.2, blob=0.2, up=1.8, down=0.9)
        if c.n[2] > 0.6:
            tone += 0.6
        return pick(RAMPS["worn"], tone)

    def paint_steel(self, c):
        tone = self.std(c, "steel", base=3.6, noise=0.15, blob=0.25, up=1.5, down=1.0)
        tone += self.edge(c, 1.5) + self.crest(c) + self.rivet(c)
        return pick(RAMPS["steel"], tone)

    def paint_darksteel(self, c):
        tone = self.std(c, "darksteel", base=3.2, noise=0.2, blob=0.2, up=1.7, down=0.9)
        tone += self.rivet(c) + self.crest(c)
        return pick(RAMPS["darksteel"], tone)

    def paint_ash(self, c):
        tone = self.std(c, "ash", base=3.4, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c) + self.crest(c)
        return pick(RAMPS["ash"], tone)

    def paint_walnut(self, c):
        tone = self.std(c, "walnut", base=3.2, noise=0.15, blob=0.2, up=0.9, down=0.8)
        tone += self.grain(c)
        return pick(RAMPS["walnut"], tone)

    def paint_leather(self, c):
        tone = self.std(c, "leather", base=3.0, noise=0.25, blob=0.2, up=0.8, down=0.6)
        tone += self.wrap(c) + self.stitch(c)
        return pick(RAMPS["leather"], tone)

    def paint_leather_dark(self, c):
        tone = self.std(c, "leather_dark", base=3.1, noise=0.25, blob=0.2, up=0.8, down=0.6)
        tone += self.wrap(c) + self.stitch(c)
        return pick(RAMPS["leather_dark"], tone)

    def paint_cord(self, c):
        tone = self.std(c, "cord", base=3.3, noise=0.2, blob=0.0, up=0.9, down=0.7)
        tone += (0.5, -0.5)[(c.i + c.j) % 2] if c.el.tags.get("twist") else 0.0
        return pick(RAMPS["cord"], tone)

    def paint_wool(self, c):
        tone = self.std(c, "wool", base=3.6, noise=0.35, blob=0.25, up=0.6, down=0.6)
        return pick(RAMPS["wool"], tone)

    def paint_crystal_dim(self, c):
        tone = 3.4 + (0.9 if (c.i + c.j) == 0 else 0.0) - (0.7 if c.i == c.W - 1 and c.j == c.H - 1 else 0.0)
        tone += self.bevel(c, up=0.8, down=0.8)
        return pick(RAMPS["crystal_dim"], tone)

    def paint_glow(self, c, ramp_name, base=4.0):
        tone = base + (self.tex(c, 81) - 0.5) * 0.6
        if (c.i + c.j) == 0:
            tone += 1.2
        return pick(RAMPS[ramp_name], tone)
