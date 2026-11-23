"""街のNPCの立体のモデル（GeckoLib）を組み立てる道具（11月22日のご指示「キャラクターに凹凸をつけて立体的にする」）。

make_npc_models.py から使う。ここには、どの人にも使うものを置く：

- 2倍の細かさで塗る（{@link D}）：モデルの1ピクセル（1/16ブロック）を、絵の2×2で描く。geo の texture_width は
  モデルの単位のまま（UVは0〜1に直して読まれる）なので、絵だけ2倍の大きさにすれば、顔の目や刺繍を細かく描ける。
- 骨（ボーン）の向きをつけた確認画像（アニメーションの最初の形で描く）。
- 体の部品（頭・胴・腕・足・スカート・髪）と、顔（目・口・眉）の描き方。

座標は Bedrock（Blockbench）のまま：体の右が −x、顔が −z、上が +y、単位はピクセル（1/16ブロック）。
塗るときの位置 ctx.p は「ゲームの中の向き」（体の右が +x）。顔の正面の絵は、前から見た向き（i が小さいほど、見る人の左＝その人の右）。
"""
import json
import math
import os
import types

import numpy as np
from PIL import Image

from geomodel import GeoModel
from javamodel import FaceTexel, rot_matrix
import render

D = 2


# ---------------------------------------------------------------- 色

def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def rgba(c, a=255):
    return (int(c[0]), int(c[1]), int(c[2]), a)


CLEAR = (0, 0, 0, 0)


def hash01(*vals):
    """位置から決まる 0〜1 の数（絵のむらに使う。毎回同じになる）。"""
    h = 2166136261
    for v in vals:
        h ^= int(v) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- モデル

class NpcModel(GeoModel):
    """GeoModel に、骨の初めの向き（確認画像用）と、骨の親子を足したもの。"""

    def __init__(self, name, tex_w=128, tex_h=128):
        super().__init__(name, tex_w, tex_h)
        self.parents = {}
        self.pose = {}       # 骨 → (rx, ry, rz)（Bedrock の角度。アニメーションの最初の形）

    def bone(self, name, pivot, parent=None):
        super().bone(name, pivot, parent)
        self.parents[name] = parent
        return self

    def pivot_of(self, bone):
        return self.bones[self.bone_index[bone]][1]

    def part(self, bone, name, origin, size, mat, inflate=0.0, rot=None, pivot=None, **tags):
        """箱を1つ足す。tags は塗るときに使う（色・模様・飾り）。"""
        return self.cube(bone, name, origin, size, mat, inflate=inflate, rot=rot, pivot=pivot, tags=tags)


def bone_matrix(model, bone):
    """骨の向き（親から順に掛ける）を、ゲームの中の向き（x を反転）の 4×4 で返す。"""
    m = np.eye(4)
    chain = []
    b = bone
    while b:
        chain.append(b)
        b = model.parents.get(b)
    for b in reversed(chain):
        rot = model.pose.get(b)
        if not rot:
            continue
        rx, ry, rz = rot
        R = rot_matrix("z", rz) @ rot_matrix("y", -ry) @ rot_matrix("x", -rx)
        px, py, pz = model.pivot_of(b)
        o = np.array([-px, py, pz], dtype=float)
        t = np.eye(4)
        t[:3, :3] = R
        t[:3, 3] = o - R @ o
        m = m @ t
    return m


class posed:
    """with posed(model): の間、骨の向きをつけた形で描く（塗るときは使わない）。"""

    def __init__(self, model):
        self.model = model
        self.saved = []

    def __enter__(self):
        for c in self.model.elements:
            m = bone_matrix(self.model, c.bone)
            if np.allclose(m, np.eye(4)):
                continue
            old_w, old_d = c.to_world, c.rotate_dir

            def to_world(self_c, p, _m=m, _old=old_w):
                q = _old(p)
                return _m[:3, :3] @ q + _m[:3, 3]

            def rotate_dir(self_c, v, _m=m, _old=old_d):
                return _m[:3, :3] @ _old(v)

            self.saved.append((c, old_w, old_d))
            c.to_world = types.MethodType(to_world, c)
            c.rotate_dir = types.MethodType(rotate_dir, c)
        return self.model

    def __exit__(self, *exc):
        for c, old_w, old_d in self.saved:
            c.to_world = old_w
            c.rotate_dir = old_d
        return False


def paint_hd(model, painter):
    """絵を D 倍の細かさで塗る。painter(ctx) → (r, g, b, a)。ctx.W・ctx.H は D 倍の大きさ。"""
    img = np.zeros((model.tex_h * D, model.tex_w * D, 4), dtype=np.uint8)
    for el in model.elements:
        for f in el.faces.values():
            u0, v0, u1, v1 = f.uv
            W, H = f.size
            c = f.corners_world()
            n = f.normal_world()
            du = c[3] - c[0]
            dv = c[1] - c[0]
            for j in range(H * D):
                for i in range(W * D):
                    p = c[0] + du * ((i + 0.5) / (W * D)) + dv * ((j + 0.5) / (H * D))
                    ctx = FaceTexel(f, i, j, W * D, H * D, p, n, du, dv, 0, 1)
                    img[v0 * D + j, u0 * D + i] = painter(ctx)
    return img


def render_hd(model, tex, **kw):
    """D 倍の絵で描く（UV を D 倍にしてから描き、もとに戻す）。"""
    saved = []
    for el in model.elements:
        for f in el.faces.values():
            saved.append((f, f.uv))
            f.uv = tuple(v * D for v in f.uv)
    try:
        return render.render(model, tex, **kw)
    finally:
        for f, uv in saved:
            f.uv = uv


def preview_sheet(model, tex, path, title_h=0, scale=7.0, faces_only=False):
    """前・斜め・横・後ろと、顔の大写しを1枚に並べる。"""
    # 描く道具の yaw 0 は後ろから見る向き。前・斜め前・横・後ろの順に並べる
    views = [(180, 0), (215, 0), (270, 0), (0, 0)]
    size = (int(30 * scale), int(44 * scale))
    imgs = []
    with posed(model):
        for yaw, pitch in views:
            imgs.append(render_hd(model, tex, yaw=yaw, pitch=pitch, scale=scale, size=size, center=(0, 17, 0),
                                  light="world"))
        face = render_hd(model, tex, yaw=200, pitch=-4, scale=scale * 2.4, size=(size[0], size[1]),
                         center=(0, model.face_center_y, -2), light="world")
    sheet = Image.new("RGBA", (size[0] * 5, size[1]), (40, 44, 52, 255))
    for k, im in enumerate(imgs + [face]):
        sheet.alpha_composite(im, (k * size[0], 0))
    sheet.save(path)
    return sheet


# ---------------------------------------------------------------- 顔

class FaceDesign:
    """顔の正面の絵（D 倍の細かさ）。目・眉・口を、表情ごとの小さな絵で置く。

    eyes   : {表情: 目の絵（行の並び。右目＝見る人の左。左目は左右を返して置く）}
    rows   : 目の上の行（顔の上から、D 倍の絵の行）
    cols   : 右目の左の列（見る人の左から）。左目は顔の幅から返す
    pal    : 文字 → 色（'.' は塗らない＝肌）
    """

    def __init__(self, width, height, skin, pal, eye_row, eye_col, eyes, mouths, brows, mouth_row, brow_row,
                 blush=None, extras=None):
        self.w, self.h = width, height
        self.skin = skin
        self.pal = pal
        self.eye_row, self.eye_col = eye_row, eye_col
        self.eyes, self.mouths, self.brows = eyes, mouths, brows
        self.mouth_row, self.brow_row = mouth_row, brow_row
        self.blush = blush
        self.extras = extras or []

    def pixel(self, i, j, expr):
        """顔の (i, j) の色。None なら肌。"""
        for fn in self.extras:
            c = fn(i, j, expr)
            if c is not None:
                return c
        eye = self.eyes.get(expr) or self.eyes["normal"]
        c = self._sprite(eye, i, j, self.eye_row, self.eye_col, mirror=True)
        if c is not None:
            return c
        brow = self.brows.get(expr) or self.brows["normal"]
        c = self._sprite(brow, i, j, self.brow_row, self.eye_col, mirror=True)
        if c is not None:
            return c
        mouth = self.mouths.get(expr) or self.mouths["normal"]
        mw = len(mouth[0].split())
        c = self._sprite(mouth, i, j, self.mouth_row, (self.w - mw) // 2, mirror=False)
        if c is not None:
            return c
        if self.blush and expr != "worried":
            bc, brow_j, cols = self.blush
            if j == brow_j and (i in cols or (self.w - 1 - i) in cols):
                return bc
        return None

    def _sprite(self, rows, i, j, row0, col0, mirror):
        h = len(rows)
        if not (row0 <= j < row0 + h):
            return None
        line = rows[j - row0].split()
        w = len(line)
        if col0 <= i < col0 + w:
            ch = line[i - col0]
        elif mirror and col0 <= (self.w - 1 - i) < col0 + w:
            ch = line[(self.w - 1 - i) - col0]
        else:
            return None
        if ch == ".":
            return None
        return self.pal[ch]


# ---------------------------------------------------------------- アニメーション

def kf(pairs):
    return {r4(t): [r4(v) for v in val] for t, val in pairs}


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def save_model(repo, model, textures, animations):
    """geo/entity/npc/<名前>.geo.json・textures/entity/npc/<名前>[_<表情>].png・animations/entity/npc/<名前>.animation.json。"""
    base = os.path.join(repo, "src/main/resources/assets/amemaru")
    for sub in ("geo/entity/npc", "textures/entity/npc", "animations/entity/npc"):
        os.makedirs(os.path.join(base, sub), exist_ok=True)
    geo = model.geo_json()
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.npc_" + model.name
    with open(os.path.join(base, "geo/entity/npc", model.name + ".geo.json"), "w", encoding="utf-8") as fp:
        json.dump(geo, fp, ensure_ascii=False, indent=1)
        fp.write("\n")
    for expr, img in textures.items():
        suffix = "" if expr == "normal" else "_" + expr
        Image.fromarray(img, "RGBA").save(os.path.join(base, "textures/entity/npc", model.name + suffix + ".png"),
                                          optimize=True)
    with open(os.path.join(base, "animations/entity/npc", model.name + ".animation.json"), "w", encoding="utf-8") as fp:
        json.dump({"format_version": "1.8.0", "animations": animations}, fp, ensure_ascii=False, indent=1)
        fp.write("\n")
