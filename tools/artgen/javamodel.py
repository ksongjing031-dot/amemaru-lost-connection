"""Java版のアイテム・ブロックのモデル（Blockbenchの「Java Block/Item」と同じ形式）を、コードで組み立てる道具。

  m = JavaModel("floodgate_key_shield_model", tex_w=128, tex_h=128)
  m.box("rail_l", (-7, -12, 0), (-5, 9, 4), "iron")                    # 普通の箱
  m.box("arch_1", (..), (..), "stone", rot=((x, y, z), "z", 22.5))     # 1軸・22.5度刻みの傾き
  m.plane("keyhole", (-2, -3, 6.02), (2, 1, 6.02), "glow", glow=True)   # 厚みのない板（前だけ）
  m.cull_hidden_faces(); m.pack()
  frames = m.paint(painter, frames=8)                                   # 絵（水の流れは複数のコマ）
  m.save_json(path, texture="amemaru:item/...", display={...})

・絵は「1ピクセル＝モデルの1/16ブロック」にそろえる（面の大きさのまま絵の場所を取る）。
・光る部品は {"forge_data": {"block_light": 15, "sky_light": 15}}（Forge 1.20.1）で暗い所でも明るく見せる。
・見えない面（ほかの部品に完全に隠れる面）は書き出さない（絵の場所の節約と、ちらつき防止）。
"""
import json
import math

import numpy as np
from PIL import Image

FACE_DIRS = ("north", "south", "east", "west", "up", "down")
NORMALS = {
    "north": (0.0, 0.0, -1.0), "south": (0.0, 0.0, 1.0),
    "east": (1.0, 0.0, 0.0), "west": (-1.0, 0.0, 0.0),
    "up": (0.0, 1.0, 0.0), "down": (0.0, -1.0, 0.0),
}
ALLOWED_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


def face_corners(frm, to, d):
    """面の4つの角（Minecraftの FaceInfo と同じ順番）。絵の (u1,v1),(u1,v2),(u2,v2),(u2,v1) に対応する。"""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if d == "down":
        return [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]
    if d == "up":
        return [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)]
    if d == "north":
        return [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)]
    if d == "south":
        return [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)]
    if d == "west":
        return [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)]
    if d == "east":
        return [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)]
    raise ValueError(d)


def rot_matrix(axis, angle_deg):
    a = math.radians(angle_deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=float)
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=float)
    if axis == "z":
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=float)
    raise ValueError(axis)


class Face:
    def __init__(self, element, d):
        self.element = element
        self.d = d
        self.uv = None          # 絵の中の場所（ピクセル）：(u0, v0, u1, v1)
        self.size = None        # 絵の大きさ（ピクセル）：(W, H)

    def corners_local(self):
        return [np.array(c, dtype=float) for c in face_corners(self.element.frm, self.element.to, self.d)]

    def corners_world(self):
        return [self.element.to_world(c) for c in self.corners_local()]

    def normal_world(self):
        return self.element.rotate_dir(np.array(NORMALS[self.d]))

    def dims(self):
        c = self.corners_local()
        return float(np.linalg.norm(c[3] - c[0])), float(np.linalg.norm(c[1] - c[0]))


class Element:
    def __init__(self, name, frm, to, mat, rot=None, glow=False, plane=False, tags=None, faces=None):
        self.name = name
        self.frm = tuple(float(v) for v in frm)
        self.to = tuple(float(v) for v in to)
        for a, b in zip(self.frm, self.to):
            if b < a:
                raise ValueError(f"{name}: from は to 以下にしてください")
        self.mat = mat
        self.glow = glow
        self.plane = plane
        self.tags = dict(tags or {})
        self.rot = None
        self.R = np.eye(3)
        self.origin = np.zeros(3)
        if rot:
            origin, axis, angle = rot
            if float(angle) not in ALLOWED_ANGLES:
                raise ValueError(f"{name}: 傾きは -45〜45 の22.5度刻みだけです（{angle}）")
            self.rot = (tuple(float(v) for v in origin), axis, float(angle))
            self.R = rot_matrix(axis, float(angle))
            self.origin = np.array(origin, dtype=float)
        wanted = faces if faces is not None else FACE_DIRS
        self.faces = {d: Face(self, d) for d in wanted}

    def to_world(self, p):
        p = np.asarray(p, dtype=float)
        if self.rot is None:
            return p
        return self.R @ (p - self.origin) + self.origin

    def to_local(self, p):
        p = np.asarray(p, dtype=float)
        if self.rot is None:
            return p
        return self.R.T @ (p - self.origin) + self.origin

    def rotate_dir(self, v):
        return self.R @ np.asarray(v, dtype=float)

    def contains(self, p, eps=1e-4):
        q = self.to_local(p)
        return all(self.frm[i] + eps < q[i] < self.to[i] - eps for i in range(3))


class JavaModel:
    def __init__(self, name, tex_w=64, tex_h=64):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.elements = []

    # ---- 組み立て ----
    def box(self, name, frm, to, mat, rot=None, glow=False, tags=None, faces=None):
        el = Element(name, frm, to, mat, rot=rot, glow=glow, tags=tags, faces=faces)
        self.elements.append(el)
        return el

    def plane(self, name, frm, to, mat, facing="south", rot=None, glow=False, tags=None):
        """厚みのない板（片面だけ）。デカール（鍵穴の光など）に使う。"""
        el = Element(name, frm, to, mat, rot=rot, glow=glow, plane=True, tags=tags, faces=(facing,))
        self.elements.append(el)
        return el

    def mirror_x(self, el, name):
        """左右に写した部品（x を反転。傾きの向きも反転する）。"""
        frm = (-el.to[0], el.frm[1], el.frm[2])
        to = (-el.frm[0], el.to[1], el.to[2])
        rot = None
        if el.rot:
            (ox, oy, oz), axis, angle = el.rot
            rot = ((-ox, oy, oz), axis, angle if axis == "x" else -angle)
        faces = tuple(("west" if d == "east" else "east" if d == "west" else d) for d in el.faces)
        new = Element(name, frm, to, el.mat, rot=rot, glow=el.glow, plane=el.plane, tags=dict(el.tags, mirrored=True),
                      faces=faces)
        self.elements.append(new)
        return new

    # ---- 見えない面を消す ----
    def cull_hidden_faces(self, step=0.5):
        solids = [e for e in self.elements if not e.plane]
        for el in self.elements:
            if el.plane:
                continue
            for d in list(el.faces):
                face = el.faces[d]
                c = face.corners_world()
                n = face.normal_world()
                w, h = face.dims()
                nu = max(1, int(math.ceil(w / step)))
                nv = max(1, int(math.ceil(h / step)))
                hidden = True
                for i in range(nu):
                    for j in range(nv):
                        a = (i + 0.5) / nu
                        b = (j + 0.5) / nv
                        p = c[0] + (c[3] - c[0]) * a + (c[1] - c[0]) * b + n * 0.05
                        if not any(o is not el and o.contains(p) for o in solids):
                            hidden = False
                            break
                    if not hidden:
                        break
                if hidden:
                    del el.faces[d]

    # ---- 絵の場所を割り当てる ----
    def pack(self, padding=0):
        faces = [f for e in self.elements for f in e.faces.values()]
        for f in faces:
            w, h = f.dims()
            f.size = (max(1, int(math.ceil(w - 1e-6))), max(1, int(math.ceil(h - 1e-6))))
        order = sorted(faces, key=lambda f: (-f.size[1], -f.size[0]))
        x = y = row_h = 0
        for f in order:
            W, H = f.size
            if x + W > self.tex_w:
                x = 0
                y += row_h + padding
                row_h = 0
            if y + H > self.tex_h:
                raise RuntimeError(f"{self.name}：絵（{self.tex_w}×{self.tex_h}）に面が入りきりません")
            f.uv = (x, y, x + W, y + H)
            x += W + padding
            row_h = max(row_h, H)
        return y + row_h

    # ---- 絵を描く ----
    def paint(self, painter, frames=1):
        """painter(ctx) → (r, g, b, a)。ctx は面の1ピクセルの情報（下の FaceTexel）。光る部品だけ、コマごとに描き直す。"""
        images = []
        base = np.zeros((self.tex_h, self.tex_w, 4), dtype=np.uint8)
        for el in self.elements:
            if el.glow and frames > 1:
                continue
            for f in el.faces.values():
                self._paint_face(base, f, painter, 0, frames)
        for k in range(frames):
            img = base.copy()
            for el in self.elements:
                if el.glow and frames > 1:
                    for f in el.faces.values():
                        self._paint_face(img, f, painter, k, frames)
            images.append(img)
        return images

    def _paint_face(self, img, f, painter, frame, frames):
        u0, v0, u1, v1 = f.uv
        W, H = f.size
        c = f.corners_world()
        n = f.normal_world()
        du = (c[3] - c[0])
        dv = (c[1] - c[0])
        for j in range(H):
            for i in range(W):
                p = c[0] + du * ((i + 0.5) / W) + dv * ((j + 0.5) / H)
                ctx = FaceTexel(f, i, j, W, H, p, n, du, dv, frame, frames)
                img[v0 + j, u0 + i] = painter(ctx)

    # ---- 書き出し ----
    def json_elements(self, texture_key="0"):
        out = []
        for el in self.elements:
            if not el.faces:
                continue
            e = {"name": el.name, "from": [r4(v) for v in el.frm], "to": [r4(v) for v in el.to]}
            if el.rot:
                origin, axis, angle = el.rot
                e["rotation"] = {"angle": angle, "axis": axis, "origin": [r4(v) for v in origin]}
            faces = {}
            for d in FACE_DIRS:
                if d not in el.faces:
                    continue
                f = el.faces[d]
                u0, v0, u1, v1 = f.uv
                sx = 16.0 / self.tex_w
                sy = 16.0 / self.tex_h
                faces[d] = {"uv": [r4(u0 * sx), r4(v0 * sy), r4(u1 * sx), r4(v1 * sy)], "texture": "#" + texture_key}
            e["faces"] = faces
            if el.glow:
                e["forge_data"] = {"block_light": 15, "sky_light": 15}
            if el.plane or el.glow:
                e["shade"] = not el.glow
            out.append(e)
        return out

    def save_json(self, path, texture, credit, display=None, extra=None):
        data = {"credit": credit, "texture_size": [self.tex_w, self.tex_h]}
        if extra:
            data.update(extra)
        data["textures"] = {"0": texture, "particle": texture}
        data["elements"] = self.json_elements()
        if display:
            data["display"] = display
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(data, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
        return data


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


class FaceTexel:
    """面の1ピクセルの情報。

    i, j      : 面の中の位置（左上が 0,0。横の面は「上」が上、上の面は北が上）
    W, H      : 面の大きさ（ピクセル）
    p, n      : モデルの中の位置（ピクセル単位）と、外向きの向き
    du, dv    : 面の横・縦の向き（モデルの中）
    frame     : アニメーションのコマ（0〜frames-1）
    """
    __slots__ = ("face", "el", "i", "j", "W", "H", "p", "n", "du", "dv", "frame", "frames")

    def __init__(self, face, i, j, W, H, p, n, du, dv, frame, frames):
        self.face = face
        self.el = face.element
        self.i, self.j, self.W, self.H = i, j, W, H
        self.p, self.n, self.du, self.dv = p, n, du, dv
        self.frame, self.frames = frame, frames

    def edge_dirs(self):
        """面のふちの外向き（左・右・上・下）の、モデルの中の向き。"""
        u = self.du / (np.linalg.norm(self.du) or 1)
        v = self.dv / (np.linalg.norm(self.dv) or 1)
        return {"left": -u, "right": u, "top": -v, "bottom": v}

    def edge_dist(self):
        return {"left": self.i, "right": self.W - 1 - self.i, "top": self.j, "bottom": self.H - 1 - self.j}


def save_frames(frames, path, frametime=None):
    """コマを縦に並べた1枚の絵（Minecraftのアニメーションの形）と .mcmeta を書く。"""
    h, w = frames[0].shape[:2]
    strip = np.concatenate(frames, axis=0) if len(frames) > 1 else frames[0]
    Image.fromarray(strip, "RGBA").save(path)
    if len(frames) > 1:
        with open(path + ".mcmeta", "w", encoding="utf-8") as fp:
            json.dump({"animation": {"frametime": frametime or 2, "interpolate": False}}, fp, indent=2)
            fp.write("\n")
