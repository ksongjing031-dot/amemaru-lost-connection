"""16×16のドット絵を、部品ごとに置いて描く小さな道具（武器の持ち物の絵用）。

  p = Pixel()
  p.put(x, y, "#F0F1EE")           # 1ピクセル
  p.diag_band(t0, t1, cols)        # 左下→右上の斜めの帯（cols は帯の横の並びの色）
  p.outline("#1E1E22")             # 塗った所のまわりの透明な所に、ふちどりを付ける
  icon = p.icon()

座標は左上が (0, 0)。斜めの軸は A(t) = (t + ox, 15 - t + oy)（t が大きいほど右上）。
"""
import numpy as np

from icons import Icon
from materials import hexc


class Pixel:
    def __init__(self, ox=0, oy=0):
        self.px = {}
        self.ox, self.oy = ox, oy
        self.protect = set()

    def axis(self, t):
        return (t + self.ox, 15 - t + self.oy)

    def put(self, x, y, col):
        if 0 <= x < 16 and 0 <= y < 16 and col is not None:
            self.px[(x, y)] = hexc(col) if isinstance(col, str) else col

    def get(self, x, y):
        return self.px.get((x, y))

    def erase(self, x, y):
        self.px.pop((x, y), None)

    def diag_band(self, t0, t1, cols, dx0=-1):
        """軸の上を t0..t1 まで。各行に cols を左から並べる（いちばん左は 軸のx + dx0）。cols は関数でもよい（t を受け取る）。"""
        for t in range(t0, t1 + 1):
            x, y = self.axis(t)
            row = cols(t) if callable(cols) else cols
            for k, col in enumerate(row):
                self.put(x + dx0 + k, y, col)

    def cross(self, t, half, cols_lit, cols_dark):
        """軸の t の所に、軸と直角（左上→右下）の棒（鍔）を置く。half は片側の長さ。"""
        x, y = self.axis(t)
        for u in range(-half, half + 1):
            self.put(x + u, y + u, cols_lit)
            self.put(x + u + 1, y + u, cols_dark)

    def outline(self, col, diagonal=False):
        col = hexc(col) if isinstance(col, str) else col
        filled = set(self.px)
        adds = set()
        for (x, y) in filled:
            nbrs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
            if diagonal:
                nbrs += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
            for dx, dy in nbrs:
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    adds.add(q)
        for q in adds:
            self.px[q] = col

    def icon(self):
        img = np.zeros((16, 16, 4), dtype=np.uint8)
        for (x, y), c in self.px.items():
            img[y, x] = (*c[:3], 255)
        return Icon(img)
