"""建物の部品：材料の組（Palette）、切妻の屋根（Roof）、四角すいの屋根（Pyramid）、切妻の三角の壁、飾りの小塔。

向きの決まり：壁の「外向き」（face）は north・south・west・east。屋根の階段の facing は高い側（棟の方）。
壁の立面と窓は facade.py にある。
"""
import random

from .world import block, lantern, log, slab, stairs, wall


class Palette:
    """建物の材料。"""

    def __init__(self, **kw):
        self.roof = kw.get("roof", "brick")            # 屋根：*_stairs・*_slab・*_wall と、ブロックの名前
        self.roof_block = kw.get("roof_block", "bricks")
        self.trim = kw.get("trim", "dark_oak")         # 破風・軒の縁取り（木）
        self.post = kw.get("post", "dark_oak")         # 柱（丸太）
        self.beam = kw.get("beam", "dark_oak")         # 梁（皮をはいだ丸太）
        self.plaster = kw.get("plaster", "calcite")    # 漆喰の壁
        self.plaster_low = kw.get("plaster_low", "smooth_sandstone")
        self.frame = kw.get("frame", "spruce")         # 窓の枠・雨戸・扉
        self.stone = kw.get("stone", "stone_brick")    # 土台の石
        self.stone_block = kw.get("stone_block", "stone_bricks")
        self.accent = kw.get("accent", "polished_deepslate")
        self.rng = random.Random(kw.get("seed", 7))


# ---------------------------------------------------------------- 屋根
class Roof:
    """切妻屋根。棟は ridge の軸（"x" か "z"）に走る。x0..x1・z0..z1 は壁の外のふち（屋根の下の範囲）。

    pitch 2 は、横に1進むと2上がる急な屋根（レンガのブロックの上に階段）。
    thickness は、階段の下に重ねるブロックの数（ほかの屋根と交わる所に、すき間ができないように）。
    ends は、縁取り（破風）をつける端（"start" は棟に沿う向きの小さい側、"end" は大きい側）。
    縁取りの端では、屋根の面より1段高い縁（濃い木）と、その外の破風板を置く。
    """

    def __init__(self, w, pal, x0, x1, z0, z1, y0, ridge="z", pitch=2, side_overhang=1, end_overhang=1, courses=True,
                 ends=("start", "end"), ridge_cap=True, thickness=3, kick=True, trim_block=None):
        self.w, self.pal = w, pal
        # 縁取りのブロック（ふつうは濃い木の板。カジノの金の縁は、なめらかな砂岩）
        self.trim_block = trim_block or pal.trim + "_planks"
        self.x0, self.x1, self.z0, self.z1, self.y0 = x0, x1, z0, z1, y0
        self.ridge, self.pitch = ridge, pitch
        self.so, self.eo = side_overhang, end_overhang
        self.courses, self.ends, self.ridge_cap = courses, ends, ridge_cap
        self.thickness, self.kick = thickness, kick
        if ridge == "z":
            self.c0, self.c1 = x0 - side_overhang, x1 + side_overhang   # 斜面を横切る向き
            self.l0, self.l1 = z0 - end_overhang, z1 + end_overhang     # 棟に沿う向き
        else:
            self.c0, self.c1 = z0 - side_overhang, z1 + side_overhang
            self.l0, self.l1 = x0 - end_overhang, x1 + end_overhang
        span = self.c1 - self.c0 + 1
        self.half = (span - 1) // 2
        self.odd = span % 2 == 1
        self.per_side = self.half if self.odd else self.half + 1
        self.center = self.c0 + self.half          # 棟の列（偶数の幅では、棟の2列の小さい方）
        self.ridge_y = self.y0 + pitch * self.half + (pitch - 1)   # 棟（または棟の横の階段）の高さ

    def xz(self, c, l):
        return (c, l) if self.ridge == "z" else (l, c)

    def cl(self, x, z):
        return (x, z) if self.ridge == "z" else (z, x)

    def up_dir(self, side):
        """side 0：c が小さい側の斜面。棟の方（階段の背）を向く。"""
        if self.ridge == "z":
            return "east" if side == 0 else "west"
        return "south" if side == 0 else "north"

    def col(self, c):
        """位置 c が、軒から何列目か（棟は half）。"""
        return min(c - self.c0, self.c1 - c)

    def stair_y(self, c):
        return self.y0 + self.pitch * self.col(c) + self.pitch - 1

    def inside(self, x, y, z):
        """(x, y, z) が、この屋根の面より下（屋根の中身）か。ほかの屋根と交わる所で、隠れる方を置かないために使う。"""
        c, l = self.cl(x, z)
        if not (self.c0 <= c <= self.c1 and self.l0 <= l <= self.l1):
            return False
        return self.y0 - 1 <= y < self.stair_y(c)

    def trimmed(self, l):
        return (l == self.l0 and "start" in self.ends) or (l == self.l1 and "end" in self.ends)

    def build(self, skip=None):
        """skip(x, y, z) が True の場所には置かない（ほかの屋根の中・塔の中など）。"""
        pal = self.pal
        roof, roof_block, trim = pal.roof, pal.roof_block, pal.trim
        for l in range(self.l0, self.l1 + 1):
            rim = self.trimmed(l)
            for side in (0, 1):
                up = self.up_dir(side)
                for i in range(self.per_side):
                    c = self.c0 + i if side == 0 else self.c1 - i
                    top = self.y0 + self.pitch * i + self.pitch - 1
                    if rim:
                        # 縁取り：屋根の面より1段高い、濃い木の縁
                        self._put(skip, c, top, l, block(self.trim_block))
                        self._put(skip, c, top + 1, l, stairs(trim, up))
                        continue
                    for y in range(max(self.y0, top - self.thickness), top):
                        self._put(skip, c, y, l, block(roof_block))
                    self._put(skip, c, top, l, stairs(roof, up))
                    # 屋根の筋（数段おきに、外へ少し出た段）
                    if self.courses and i % 2 == 1 and i < self.per_side - 1:
                        oc = c - 1 if side == 0 else c + 1
                        self._put_if_empty(skip, oc, top - 1, l, stairs(roof, up, "top"))
                # 軒：いちばん下の段の下に、縁取りの逆さ階段（持ち送り）。縁取りの端（浮いてしまう）には置かない
                if not rim:
                    c = self.c0 if side == 0 else self.c1
                    self._put(skip, c, self.y0 - 1, l, stairs(trim, up, "top"))
            # 棟
            if self.odd:
                c = self.center
                top = self.y0 + self.pitch * self.half
                if rim:
                    for y in range(top, top + self.pitch + 1):
                        self._put(skip, c, y, l, block(self.trim_block))
                    self._put(skip, c, top + self.pitch + 1, l, slab(trim, "bottom"))
                    continue
                for y in range(max(self.y0, top - self.thickness), top + self.pitch - 1):
                    self._put(skip, c, y, l, block(roof_block))
                if self.ridge_cap:
                    self._put(skip, c, top + self.pitch - 1, l, slab(roof, "bottom"))
            elif self.ridge_cap and not rim:
                for c in (self.center, self.center + 1):
                    self._put(skip, c, self.ridge_y + 1, l, slab(roof, "bottom"))

        # 破風板：縁の外に、逆さの階段
        for l, which, out in ((self.l0 - 1, "start", -1), (self.l1 + 1, "end", 1)):
            if which not in self.ends:
                continue
            for side in (0, 1):
                up = self.up_dir(side)
                for i in range(self.per_side):
                    c = self.c0 + i if side == 0 else self.c1 - i
                    top = self.y0 + self.pitch * i + self.pitch - 1
                    self._put(skip, c, top, l, stairs(trim, up, "top"))
                if self.kick:
                    # 軒の先の反り：縁のいちばん下を、外へ1つ伸ばす
                    c = self.c0 - 1 if side == 0 else self.c1 + 1
                    rl = l - out
                    self._put(skip, c, self.y0 + self.pitch - 1, rl, stairs(trim, up, "top"))
            if self.odd:
                top = self.y0 + self.pitch * self.half
                self._put(skip, self.center, top + self.pitch - 1, l, slab(trim, "top"))
                self._put(skip, self.center, top + self.pitch, l, block(self.trim_block))

    def apex(self, which="end"):
        """縁取りの端の頂（飾りの小塔を立てる所）の (x, y, z)。"""
        l = self.l1 + 1 if which == "end" else self.l0 - 1
        x, z = self.xz(self.center, l)
        return x, self.y0 + self.pitch * self.half + self.pitch + 1, z

    def eave_corner(self, which="end", side=0):
        """縁取りの端の、軒の先（反りの上）の (x, y, z)。"""
        l = self.l1 if which == "end" else self.l0
        c = self.c0 - 1 if side == 0 else self.c1 + 1
        x, z = self.xz(c, l)
        return x, self.y0 + self.pitch, z

    def _put(self, skip, c, y, l, state):
        x, z = self.xz(c, l)
        if skip and skip(x, y, z):
            return
        self.w.put(x, y, z, state)

    def _put_if_empty(self, skip, c, y, l, state):
        x, z = self.xz(c, l)
        if skip and skip(x, y, z):
            return
        self.w.put_if_empty(x, y, z, state)

    def gable_extent(self, y):
        """切妻の壁の高さ y で、壁を置ける c の範囲（屋根の下）。なければ None。"""
        if y < self.y0:
            return None
        k = (y - self.y0) // self.pitch + 1
        lo, hi = self.c0 + k, self.c1 - k
        if lo > hi:
            return None
        return lo, hi


class Pyramid:
    """四角すいの屋根（小塔・尖塔）。中心 (cx, cz)、軒の出を含む半径 r、軒の高さ y0。"""

    def __init__(self, w, pal, cx, cz, r, y0, pitch=2, thickness=2, mat=None, mat_block=None, rings=0, eave=True):
        self.w, self.pal = w, pal
        self.cx, self.cz, self.r, self.y0 = cx, cz, r, y0
        self.pitch, self.thickness = pitch, thickness
        self.mat = mat or pal.roof
        self.mat_block = mat_block or pal.roof_block
        self.rings, self.eave = rings, eave
        self.top_y = y0 + pitch * r + pitch - 1

    def ring(self, x, z):
        return max(abs(x - self.cx), abs(z - self.cz))

    def stair_y(self, x, z):
        k = self.r - self.ring(x, z)
        return self.y0 + self.pitch * k + self.pitch - 1

    def inside(self, x, y, z):
        if self.ring(x, z) > self.r:
            return False
        return self.y0 - 1 <= y < self.stair_y(x, z)

    def _facing(self, dx, dz):
        if abs(dz) >= abs(dx):
            return "south" if dz < 0 else "north"
        return "east" if dx < 0 else "west"

    def build(self, skip=None):
        w, trim = self.w, self.pal.trim
        for k in range(self.r + 1):
            rho = self.r - k
            top = self.y0 + self.pitch * k + self.pitch - 1
            for dx in range(-rho, rho + 1):
                for dz in range(-rho, rho + 1):
                    if max(abs(dx), abs(dz)) != rho:
                        continue
                    x, z = self.cx + dx, self.cz + dz
                    if rho == 0:
                        for y in range(max(self.y0, top - self.thickness), top + 1):
                            self._put(skip, x, y, z, block(self.mat_block))
                        continue
                    for y in range(max(self.y0, top - self.thickness), top):
                        self._put(skip, x, y, z, block(self.mat_block))
                    self._put(skip, x, top, z, stairs(self.mat, self._facing(dx, dz)))
                    if k == 0 and self.eave:
                        self._put(skip, x, self.y0 - 1, z, stairs(trim, self._facing(dx, dz), "top"))
            if self.rings and 0 < k < self.r and k % self.rings == 0:
                # 帯：段の下に、外へ出た縁取りの逆さ階段
                ro = rho + 1
                for dx in range(-ro, ro + 1):
                    for dz in range(-ro, ro + 1):
                        if max(abs(dx), abs(dz)) != ro:
                            continue
                        x, z = self.cx + dx, self.cz + dz
                        if w.get(x, top - self.pitch, z) is None:
                            self._put(skip, x, top - self.pitch, z, stairs(trim, self._facing(dx, dz), "top"))
        return self.top_y + 1

    def _put(self, skip, x, y, z, state):
        if skip and skip(x, y, z):
            return
        self.w.put(x, y, z, state)


def gable_wall(w, pal, roof, at_l, posts=(), beam_rows=(), infill=None):
    """切妻の三角の壁（屋根の下）を、漆喰で埋める。at_l は壁の位置（棟に沿う向きの座標）。

    いちばん下の段は梁。posts（c の位置）には柱、beam_rows（高さ）には横の梁を通す。三角の行 [(y, lo, hi)] を返す。
    """
    infill = infill or pal.plaster
    y = roof.y0
    rows = []
    while True:
        ext = roof.gable_extent(y)
        if ext is None:
            break
        lo, hi = ext
        lo = max(lo, roof.c0 + roof.so)
        hi = min(hi, roof.c1 - roof.so)
        if lo > hi:
            break
        rows.append((y, lo, hi))
        y += 1
    along_axis = "x" if roof.ridge == "z" else "z"
    for (y, lo, hi) in rows:
        for c in range(lo, hi + 1):
            x, z = roof.xz(c, at_l)
            if y == roof.y0 or y in beam_rows:
                w.put(x, y, z, log(pal.post, along_axis, stripped=True))
            elif c in posts:
                w.put(x, y, z, log(pal.post, "y", stripped=True))
            else:
                w.put(x, y, z, block(infill))
    return rows


# ---------------------------------------------------------------- 飾り
def finial(w, pal, x, y, z, height=5, top="lantern", spike=True):
    """尖った飾りの小塔：レンガの塀を重ね、途中に灰色の輪、上に灯り（と細い棒）。"""
    seq = ["polished_deepslate", "brick", "brick", "andesite", "brick", "brick", "andesite", "brick"]
    for k in range(height):
        w.put(x, y + k, z, wall(seq[k] if k < len(seq) else "brick"))
    h = y + height
    if top == "lantern":
        w.put(x, h, z, lantern(False))
        h += 1
    if spike:
        w.put(x, h, z, block("end_rod", facing="up"))
        h += 1
    return h
