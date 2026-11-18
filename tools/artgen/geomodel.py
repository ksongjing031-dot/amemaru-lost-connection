"""GeckoLib（Bedrock形式）のモデルを、箱のUV（Minecraftの皮と同じ並び）でコードから組み立てる道具。

防具・体に表示するアクセサリー・GeckoLibの武器に使う。

  g = GeoModel("ancient_stone_plate", 128, 128)
  g.bone("armorHead", (0, 24, 0))
  g.cube("armorHead", "helm", (-4, 24, -4), (8, 8, 8), "iron", inflate=1.0)
  g.cube("armorRightArm", "pauldron", (-9, 21, -3), (5, 2, 6), "stone", rot=(0, 0, 22.5), pivot=(-6.5, 22, 0))
  g.pack(); frames = g.paint(painter); g.save(repo, "armor", frames)

座標は Bedrock（Blockbench）のまま：体の右が −x、表（顔）が −z、上が +y、1ピクセル＝1/16ブロック。
GeckoLib は読み込むときに x を反転し、x と y の回転の向きも反転する（BakedModelFactory）。
絵を塗るときと確認画像を描くときは、その反転を済ませた「ゲームの中の向き」（体の右が +x、表が −z）で扱う。

箱のUV（大きさ w・h・d、場所 u・v）：
  右の面 (u, v+d) d×h ／ 表 (u+d, v+d) w×h ／ 左の面 (u+d+w, v+d) d×h ／ 裏 (u+2d+w, v+d) w×h
  上 (u+d, v) w×d ／ 下 (u+d+w, v) w×d
それぞれ外から見た向き（横の面は上が上。上の面は表が下）。
"""
import json
import math
import os

import numpy as np
from PIL import Image

from javamodel import JavaModel, rot_matrix

MIRROR = np.diag([-1.0, 1.0, 1.0])


FACE_NORMALS = {"right": (-1, 0, 0), "front": (0, 0, -1), "left": (1, 0, 0), "back": (0, 0, 1),
                "up": (0, 1, 0), "down": (0, -1, 0)}


class BoxFace:
    def __init__(self, cube, d, corner0, du, dv, uv, size):
        self.element = cube
        self._n = np.array(FACE_NORMALS[d], dtype=float)
        self.d = d
        self._c0 = np.array(corner0, dtype=float)
        self._du = np.array(du, dtype=float)
        self._dv = np.array(dv, dtype=float)
        self.uv = uv
        self.size = size

    def corners_world(self):
        c0 = self._c0
        c3 = c0 + self._du
        c1 = c0 + self._dv
        c2 = c1 + self._du
        return [self.element.to_world(p) for p in (c0, c1, c2, c3)]

    def normal_world(self):
        return self.element.rotate_dir(self._n)

    def dims(self):
        return float(np.linalg.norm(self._du)), float(np.linalg.norm(self._dv))


class GeoCube:
    """Bedrockの箱1つ。faces は塗る・描くための面（ゲームの中の向きに直したもの）。"""

    def __init__(self, bone, name, origin, size, mat, inflate=0.0, rot=None, pivot=None, glow=False, tags=None):
        self.bone = bone
        self.name = name
        self.origin = tuple(float(v) for v in origin)
        self.size = tuple(int(round(v)) for v in size)
        if any(abs(a - b) > 1e-6 for a, b in zip(self.size, size)):
            raise ValueError(f"{name}: 箱のUVのため、大きさは整数にしてください（{size}）")
        self.mat = mat
        self.inflate = float(inflate)
        self.rot = tuple(float(v) for v in rot) if rot else None
        self.pivot = tuple(float(v) for v in pivot) if pivot else None
        self.glow = glow
        self.plane = False
        self.tags = dict(tags or {})
        self.faces = {}
        self.uv = None
        # 塗るときの「from・to」（ゲームの中の向き。刃のふちなどの計算用）
        x0, y0, z0 = self.origin
        w, h, d = self.size
        i = self.inflate
        self.frm = (-(x0 + w) - i, y0 - i, z0 - i)
        self.to = (-x0 + i, y0 + h + i, z0 + d + i)
        # GeckoLibの回転：ピボットの x を反転し、x と y の角度を反転して、Z・Y・X の順に掛ける
        self.R = np.eye(3)
        self.origin_j = np.zeros(3)
        if self.rot:
            rx, ry, rz = self.rot
            self.R = rot_matrix("z", rz) @ rot_matrix("y", -ry) @ rot_matrix("x", -rx)
            px, py, pz = self.pivot
            self.origin_j = np.array([-px, py, pz], dtype=float)

    # 塗る・描く側から使う（JavaModel の Element と同じ名前）
    def to_world(self, p_bedrock):
        q = MIRROR @ np.asarray(p_bedrock, dtype=float)
        if self.rot:
            q = self.R @ (q - self.origin_j) + self.origin_j
        return q

    def rotate_dir(self, v_bedrock):
        q = MIRROR @ np.asarray(v_bedrock, dtype=float)
        return self.R @ q if self.rot else q

    def build_faces(self, u, v):
        x0, y0, z0 = self.origin
        w, h, d = self.size
        i = self.inflate
        X0, Y0, Z0 = x0 - i, y0 - i, z0 - i
        X1, Y1, Z1 = x0 + w + i, y0 + h + i, z0 + d + i
        W, H, D = X1 - X0, Y1 - Y0, Z1 - Z0
        self.uv = (u, v)
        f = {}
        # 右の面（−x）：外から見て、右が表（−z）
        f["right"] = BoxFace(self, "right", (X0, Y1, Z1), (0, 0, -D), (0, -H, 0), (u, v + d, u + d, v + d + h), (d, h))
        f["front"] = BoxFace(self, "front", (X0, Y1, Z0), (W, 0, 0), (0, -H, 0), (u + d, v + d, u + d + w, v + d + h), (w, h))
        f["left"] = BoxFace(self, "left", (X1, Y1, Z0), (0, 0, D), (0, -H, 0), (u + d + w, v + d, u + 2 * d + w, v + d + h), (d, h))
        f["back"] = BoxFace(self, "back", (X1, Y1, Z1), (-W, 0, 0), (0, -H, 0),
                            (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), (w, h))
        f["up"] = BoxFace(self, "up", (X0, Y1, Z1), (W, 0, 0), (0, 0, -D), (u + d, v, u + d + w, v + d), (w, d))
        f["down"] = BoxFace(self, "down", (X0, Y0, Z1), (W, 0, 0), (0, 0, -D), (u + d + w, v, u + d + 2 * w, v + d), (w, d))
        # 大きさ0の面（厚み0の板）は外す
        self.faces = {k: fc for k, fc in f.items() if fc.size[0] > 0 and fc.size[1] > 0}

    def block(self):
        w, h, d = self.size
        return 2 * (w + d), d + h


class GeoModel(JavaModel):
    def __init__(self, name, tex_w=128, tex_h=128):
        super().__init__(name, tex_w, tex_h)
        self.bones = []   # (name, pivot, parent)
        self.bone_index = {}

    def bone(self, name, pivot, parent=None):
        self.bones.append((name, tuple(float(v) for v in pivot), parent))
        self.bone_index[name] = len(self.bones) - 1
        return self

    def cube(self, bone, name, origin, size, mat, inflate=0.0, rot=None, pivot=None, glow=False, tags=None):
        if bone not in self.bone_index:
            raise KeyError(f"ボーンがありません：{bone}")
        c = GeoCube(bone, name, origin, size, mat, inflate=inflate, rot=rot, pivot=pivot, glow=glow, tags=tags)
        self.elements.append(c)
        return c

    def mirror_cube(self, c, bone, name, extra_inflate=0.0):
        """左右に写す（Bedrockの x を反転。z の回転と y の回転の向きも反転）。

        extra_inflate：左右の足のように、まん中で重なる部品は、片方だけほんの少し大きくして、
        同じ面がちらつく（Zファイティング）のを防ぐ。"""
        x0 = c.origin[0]
        w = c.size[0]
        origin = (-(x0 + w), c.origin[1], c.origin[2])
        rot = None
        pivot = None
        if c.rot:
            rx, ry, rz = c.rot
            rot = (rx, -ry, -rz)
            pivot = (-c.pivot[0], c.pivot[1], c.pivot[2])
        return self.cube(bone, name, origin, c.size, c.mat, inflate=c.inflate + extra_inflate, rot=rot, pivot=pivot,
                         glow=c.glow, tags=dict(c.tags))

    def cull_hidden_faces(self, step=0.5):
        pass  # 箱のUVは6面ともそろっている必要があるので、面は消さない

    def pack(self, padding=0):
        cubes = sorted(self.elements, key=lambda c: (-c.block()[1], -c.block()[0]))
        x = y = row_h = 0
        for c in cubes:
            bw, bh = c.block()
            if x + bw > self.tex_w:
                x = 0
                y += row_h + padding
                row_h = 0
            if y + bh > self.tex_h:
                raise RuntimeError(f"{self.name}：絵（{self.tex_w}×{self.tex_h}）に箱が入りきりません")
            c.build_faces(x, y)
            x += bw + padding
            row_h = max(row_h, bh)
        return y + row_h

    def geo_json(self):
        bones = []
        for name, pivot, parent in self.bones:
            b = {"name": name, "pivot": [r4(v) for v in pivot]}
            if parent:
                b["parent"] = parent
            cubes = []
            for c in self.elements:
                if c.bone != name:
                    continue
                e = {"origin": [r4(v) for v in c.origin], "size": list(c.size), "uv": list(c.uv)}
                if c.inflate:
                    e["inflate"] = r4(c.inflate)
                if c.rot:
                    e["pivot"] = [r4(v) for v in c.pivot]
                    e["rotation"] = [r4(v) for v in c.rot]
                cubes.append(e)
            if cubes:
                b["cubes"] = cubes
            bones.append(b)
        pts = np.array([p for c in self.elements for f in c.faces.values() for p in f.corners_world()])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.name,
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": r4(max(2.0, math.ceil(max(hi[0] - lo[0], hi[2] - lo[2]) / 16 + 1))),
                    "visible_bounds_height": r4(max(2.0, math.ceil((hi[1] - lo[1]) / 16 + 1))),
                    "visible_bounds_offset": [0, r4((hi[1] + lo[1]) / 32), 0],
                },
                "bones": bones,
            }],
        }

    def save(self, repo, kind, frames, animations=None):
        """geo/<kind>/<名前>.geo.json・textures/<kind>/<名前>.png（光る所は _glowmask.png）・animations/<kind>/<名前>.animation.json。"""
        base = os.path.join(repo, "src/main/resources/assets/amemaru")
        os.makedirs(os.path.join(base, "geo", kind), exist_ok=True)
        with open(os.path.join(base, "geo", kind, self.name + ".geo.json"), "w", encoding="utf-8") as fp:
            json.dump(self.geo_json(), fp, ensure_ascii=False, indent=1)
            fp.write("\n")
        tex = frames[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(base, "textures", kind, self.name + ".png"))
        glow = np.zeros_like(tex)
        any_glow = False
        for c in self.elements:
            if not c.glow:
                continue
            for f in c.faces.values():
                u0, v0, u1, v1 = f.uv
                glow[v0:v1, u0:u1] = tex[v0:v1, u0:u1]
                any_glow = True
        glow_path = os.path.join(base, "textures", kind, self.name + "_glowmask.png")
        if any_glow:
            Image.fromarray(glow, "RGBA").save(glow_path)
        elif os.path.exists(glow_path):
            os.remove(glow_path)
        anim = animations or {"animation.%s.idle" % self.name: {"loop": True, "animation_length": 1.0, "bones": {}}}
        os.makedirs(os.path.join(base, "animations", kind), exist_ok=True)
        with open(os.path.join(base, "animations", kind, self.name + ".animation.json"), "w", encoding="utf-8") as fp:
            json.dump({"format_version": "1.8.0", "animations": anim}, fp, ensure_ascii=False, indent=1)
            fp.write("\n")


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


# ---------------------------------------------------------------- 確認画像用の人の体（防具を着せて描く）

def mannequin(skin=None):
    """プレイヤーの体（頭・胴・腕・足）。防具の確認画像で、着た状態を見るため。"""
    g = GeoModel("mannequin", 64, 64)
    for b in ("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"):
        g.bone(b, (0, 0, 0))
    g.cube("head", "head", (-4, 24, -4), (8, 8, 8), "skin_head")
    g.cube("body", "body", (-4, 12, -2), (8, 12, 4), "skin_body")
    g.cube("rightArm", "arm_r", (-8, 12, -2), (4, 12, 4), "skin_arm")
    g.cube("leftArm", "arm_l", (4, 12, -2), (4, 12, 4), "skin_arm")
    g.cube("rightLeg", "leg_r", (-3.9, 0, -2), (4, 12, 4), "skin_leg")
    g.cube("leftLeg", "leg_l", (-0.1, 0, -2), (4, 12, 4), "skin_leg")
    g.pack()
    return g


def paint_mannequin(c):
    """飾りのない人形（明るい灰色の布と肌）。顔だけ目を入れる。"""
    m = c.el.mat
    base = {"skin_head": (196, 160, 128), "skin_body": (120, 128, 138), "skin_arm": (196, 160, 128),
            "skin_leg": (92, 96, 104)}[m]
    k = 1.0
    if m == "skin_arm" and c.p[1] > 20:
        base = (120, 128, 138)  # 袖
    if m == "skin_head":
        if c.face.d == "up" or (c.face.d != "down" and c.p[1] > 30.5) or c.face.d == "back":
            base = (70, 52, 40)  # 髪
        if c.face.d == "front" and abs(c.p[1] - 27.5) < 0.5 and abs(abs(c.p[0]) - 2.5) < 0.6:
            base = (40, 40, 60)  # 目
    if c.edge_dist()["bottom"] == 0:
        k = 0.9
    return tuple(int(v * k) for v in base) + (255,)
