"""持ち物の枠の絵（16×16のドット絵）を、文字の並びから作る。

  icon = Icon.parse('''
      ................
      ..............ab
      ...
  ''', {"a": "#F0F1EE", "b": "#8C9096"})
  icon.save(path)

「.」と空白は透明。1文字が1ピクセル。行は16個・1行16文字にそろえる（足りない分は透明で埋める）。
"""
import numpy as np
from PIL import Image

from materials import hexc


class Icon:
    def __init__(self, pixels):
        self.pixels = pixels  # (16, 16, 4) uint8

    @classmethod
    def parse(cls, text, palette, size=16):
        rows = [r.strip() for r in text.strip("\n").splitlines()]
        rows = [r for r in rows if r != ""]
        if len(rows) > size:
            raise ValueError(f"行が{size}より多い：{len(rows)}")
        img = np.zeros((size, size, 4), dtype=np.uint8)
        for y, row in enumerate(rows):
            if len(row) > size:
                raise ValueError(f"{y}行目が{size}文字より長い：{row}")
            for x, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch not in palette:
                    raise KeyError(f"色が決まっていない文字：{ch}（{y}行目）")
                col = palette[ch]
                rgb = hexc(col) if isinstance(col, str) else tuple(col)
                img[y, x] = (*rgb[:3], 255)
        return cls(img)

    def image(self):
        return Image.fromarray(self.pixels, "RGBA")

    def save(self, path):
        self.image().save(path)

    def big(self, k=8):
        return self.image().resize((16 * k, 16 * k), Image.NEAREST)
