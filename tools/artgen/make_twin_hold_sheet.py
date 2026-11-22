"""両手の武器と弓の持ち方の確認画像（docs/gear/twin_hold_sheet.png）。11月23日。

籠手（両手にはめる）・双短剣（両手に1本ずつ）・弓（弦が自分の側）を、見習いと古代石で並べる。
  三人称・正面：体と両腕（利き手は物を持つ腕で前へ18度、もう片方は下ろした腕）。
  三人称・横：弓は「射る構え」（腕を前へ90度）も描く。
  一人称：籠手は素手の腕を両方描いてはめる（client/render/TwinWeapons.java と同じ置き方）。双短剣は両手に持つ。
変換の順は Minecraft と同じ（make_hold_sheet.py の説明を見る）。左手は ItemTransform#apply と同じく、回転の y・z と
移動の x を逆にする。

使い方：python3 tools/artgen/make_twin_hold_sheet.py [repo] [出力のフォルダ（docs/gear）]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from javamodel import face_corners, rot_matrix, NORMALS, FACE_DIRS  # noqa: E402
from render import render_many  # noqa: E402
import make_gear_phase6 as g6  # noqa: E402

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
BG = (30, 33, 40)
INK, INK2 = (240, 240, 240), (190, 194, 204)


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size, index=0)


# ---------------------------------------------------------------- 4×4 の変換（ブロック単位）
def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def R(axis, deg):
    m = np.eye(4)
    m[:3, :3] = rot_matrix(axis, deg)
    return m


def S(k):
    m = np.eye(4)
    m[:3, :3] *= k
    return m


def zyx(z, y, x):
    """ModelPart の回転（rotationZYX：Z・Y・X の順に掛けた行列。角度はラジアン）"""
    return R("z", math.degrees(z)) @ R("y", math.degrees(y)) @ R("x", math.degrees(x))


def display_matrix(d, left=False):
    """ItemTransform#apply（左手は回転の y・z と移動の x を逆にする）→ −0.5 → モデル/16"""
    rx, ry, rz = d["rotation"]
    tx, ty, tz = d["translation"]
    if left:
        ry, rz, tx = -ry, -rz, -tx
    return T(tx / 16, ty / 16, tz / 16) @ R("x", rx) @ R("y", ry) @ R("z", rz) @ S(d["scale"][0]) @ T(-0.5, -0.5, -0.5) @ S(1 / 16)


# ---------------------------------------------------------------- 描く物（面を変換して並べる）
class _Face:
    def __init__(self, corners, normal, uv):
        self._c, self._n, self.uv = corners, normal, uv

    def corners_world(self):
        return self._c

    def normal_world(self):
        return self._n


class _El:
    def __init__(self, faces, glow=False):
        self.faces = {i: f for i, f in enumerate(faces)}
        self.glow = glow


class _Model:
    def __init__(self, elements):
        self.elements = elements


def place(model, M, px=True):
    """JavaModel の面を、4×4 の M（モデルのピクセル → ブロック）で変換した物にする（結果はピクセル）"""
    els = []
    A = M[:3, :3]
    Ninv = np.linalg.inv(A).T
    for el in model.elements:
        faces = []
        for f in el.faces.values():
            cs = [(M @ np.append(c, 1.0))[:3] * 16.0 for c in f.corners_world()]
            n = Ninv @ f.normal_world()
            faces.append(_Face(cs, n / np.linalg.norm(n), f.uv))
        els.append(_El(faces, el.glow))
    return _Model(els)


def box_model(frm, to, M):
    """色1つの箱（frm・to はピクセル、M はピクセル/16 → ブロックの変換）"""
    A = M[:3, :3]
    Ninv = np.linalg.inv(A).T
    faces = []
    for d in FACE_DIRS:
        cs = [(M @ np.append(np.array(c) / 16.0, 1.0))[:3] * 16.0 for c in face_corners(frm, to, d)]
        n = Ninv @ np.array(NORMALS[d])
        faces.append(_Face(cs, n / np.linalg.norm(n), (0, 0, 4, 4)))
    return _Model([_El(faces)])


def flat_tex(rgb):
    t = np.zeros((16, 16, 4), dtype=float)
    t[..., :3] = rgb
    t[..., 3] = 255
    return t


SKIN, SHIRT, PANTS, HAIR = flat_tex((214, 170, 132)), flat_tex((92, 132, 170)), flat_tex((70, 74, 110)), flat_tex((96, 64, 40))


# ---------------------------------------------------------------- 三人称
TO_WORLD = T(0, 1.501, 0) @ np.diag([1.0, -1.0, -1.0, 1.0])     # 体のモデル空間（y が下、−z が前）→ ワールド（体は +z を向く）
ARM = {"right": ((-5.0, 2.0, 0.0), (-3, -2, -2), (1, 10, 2)), "left": ((5.0, 2.0, 0.0), (-1, -2, -2), (3, 10, 2))}


def arm_matrix(side, xrot_deg):
    pivot, _, _ = ARM[side]
    return TO_WORLD @ T(*(np.array(pivot) / 16)) @ zyx(0.0, 0.0, math.radians(xrot_deg))


def body_parts(arms):
    """arms：{"right": xRot, "left": xRot}（度）"""
    parts = [
        (box_model((-4, -8, -4), (4, 0, 4), TO_WORLD), HAIR),
        (box_model((-4, 0, -2), (4, 12, 2), TO_WORLD), SHIRT),
        (box_model((-2, 0, -2), (2, 12, 2), TO_WORLD @ T(-1.9 / 16, 12 / 16, 0)), PANTS),
        (box_model((-2, 0, -2), (2, 12, 2), TO_WORLD @ T(1.9 / 16, 12 / 16, 0)), PANTS),
    ]
    for side, xr in arms.items():
        _, frm, to = ARM[side]
        parts.append((box_model(frm, to, arm_matrix(side, xr)), SKIN))
    # 顔の向きの印（目）
    parts.append((box_model((-2.6, -4.6, -4.2), (-1.2, -3.4, -4.0), TO_WORLD), flat_tex((30, 30, 40))))
    parts.append((box_model((1.2, -4.6, -4.2), (2.6, -3.4, -4.0), TO_WORLD), flat_tex((30, 30, 40))))
    return parts


def held_matrix(side, xrot_deg, display):
    """手に持つ物（ItemInHandLayer）：腕 → x −90・y 180 → 手の先 → display"""
    left = side == "left"
    key = "thirdperson_lefthand" if left else "thirdperson_righthand"
    return (arm_matrix(side, xrot_deg) @ R("x", -90) @ R("y", 180) @ T((-1 if left else 1) / 16, 2 / 16, -10 / 16)
            @ display_matrix(display[key], left))


def third_person(item, sides, arms, yaw, size, scale, center, pitch=0.0):
    pairs = body_parts(arms)
    for side in sides:
        pairs.append((place(item["model"], held_matrix(side, arms[side], item["display"])), item["tex"]))
    img = render_many(pairs, yaw=yaw, pitch=pitch, scale=scale, size=size, center=center, light="world", ss=2)
    bg = Image.new("RGBA", size, (226, 232, 240, 255))
    bg.alpha_composite(img)
    return bg


# ---------------------------------------------------------------- 一人称
FP_W, FP_H = 854, 480
FP_F = (FP_H / 2) / math.tan(math.radians(35))
FP_P = 400.0


def fp_arm_matrix(side):
    """ItemInHandRenderer#renderPlayerArm（振り0・下げ0）→ PlayerRenderer#renderHand の腕（xRot 0・zRot ±0.1）"""
    f = 1.0 if side == "right" else -1.0
    pivot, _, _ = ARM[side]
    m = (T(f * 0.64000005, -0.6, -0.71999997) @ R("y", f * 45.0) @ T(f * -1.0, 3.6, 3.5) @ R("z", f * 120.0)
         @ R("x", 200.0) @ R("y", f * -135.0) @ T(f * 5.6, 0, 0))
    return m @ T(*(np.array(pivot) / 16)) @ zyx(0.1 * f, 0.0, 0.0)


def fp_item_matrix(side, display):
    """ふつうの物（振り0・下げ0）：applyItemArmTransform → display"""
    left = side == "left"
    f = -1.0 if left else 1.0
    key = "firstperson_lefthand" if left else "firstperson_righthand"
    return T(f * 0.56, -0.52, -0.72) @ display_matrix(display[key], left)


def worn_matrix(side):
    """TwinWeapons#renderWorn：腕の座標 → (∓1/16, 0.5, 0) → −0.5 → モデル/16"""
    return T((-1.0 if side == "right" else 1.0) / 16, 0.5, 0) @ T(-0.5, -0.5, -0.5) @ S(1 / 16)


def first_person(item, kind, sides):
    pairs = []
    for side in sides:
        if kind == "gauntlets":
            am = fp_arm_matrix(side)
            _, frm, to = ARM[side]
            pairs.append((box_model(frm, to, am), SKIN))
            pairs.append((place(item["model"], am @ worn_matrix(side)), item["tex"]))
        else:
            pairs.append((place(item["model"], fp_item_matrix(side, item["display"])), item["tex"]))
    img = render_many(pairs, scale=FP_F / FP_P, size=(FP_W, FP_H), center=(0, 0, -FP_P), perspective=FP_P, light="world", ss=2)
    bg = Image.new("RGBA", (FP_W, FP_H), (150, 185, 235, 255))
    d = ImageDraw.Draw(bg)
    d.rectangle((0, FP_H * 0.48, FP_W, FP_H), fill=(140, 140, 140, 255))
    d.line((FP_W / 2 - 7, FP_H / 2, FP_W / 2 + 7, FP_H / 2), fill=(30, 30, 30, 255), width=2)
    d.line((FP_W / 2, FP_H / 2 - 7, FP_W / 2, FP_H / 2 + 7), fill=(30, 30, 30, 255), width=2)
    d.rectangle((FP_W / 2 - 182, FP_H - 44, FP_W / 2 + 182, FP_H - 4), outline=(20, 20, 40, 255), width=3)
    bg.alpha_composite(img)
    return bg


# ---------------------------------------------------------------- まとめ
ORDER = [("apprentice_gauntlets", "見習いの籠手", "gauntlets"), ("ancient_stone_gauntlets", "古代石の籠手", "gauntlets"),
         ("apprentice_daggers", "見習いの双短剣", "daggers"), ("ancient_stone_daggers", "古代石の双短剣", "daggers"),
         ("apprentice_bow", "見習いの弓", "bow"), ("ancient_stone_bow", "古代石の弓", "bow")]


def collect():
    out = {}
    for build, kind, painter_class, _credit in g6.WEAPONS:
        if kind == "bell_staff":
            continue
        m = build()
        m.cull_hidden_faces()
        m.pack()
        frames = m.paint(painter_class(m), frames=1)
        out[m.name] = {"model": m, "tex": np.asarray(frames[0]).astype(float) if not isinstance(frames[0], np.ndarray) else frames[0],
                       "display": g6.display(kind), "kind": kind}
    return out


def make_sheet(path, data):
    tw, th = 300, 300
    fw, fh = 427, 240
    margin, gap, label_w = 28, 12, 150
    W = margin * 2 + label_w + 2 * tw + fw + 3 * gap
    row_h = th + 16
    top = 140
    H = top + len(ORDER) * row_h + 10
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((margin, 22), "両手の武器と弓の持ち方（11月23日）", font=font(28, True), fill=INK)
    d.text((margin, 64), "籠手は両手にはめる・双短剣は両手に1本ずつ（もう片方の手が空いているとき）。弓は弦が自分の側。\n"
                         "三人称の体は2ブロック。弓の「横」は射る構え（腕を前へ90度）。一人称は止まっているとき。",
           font=font(15), fill=INK2, spacing=6)
    x0 = margin + label_w
    for c, head in enumerate(("三人称・正面", "三人称・横", "一人称")):
        d.text((x0 + c * (tw + gap), top - 28), head, font=font(16, True), fill=INK)
    for r, (name, label, kind) in enumerate(ORDER):
        v = data[name]
        y = top + r * row_h
        d.text((margin, y + 6), label, font=font(18, True), fill=INK)
        if kind == "bow":
            front = third_person(v, ["right"], {"right": -18.0, "left": 0.0}, 0, (tw, th), 7.0, (0, 20, 0))
            side = third_person(v, ["right"], {"right": -90.0, "left": 0.0}, 90, (tw, th), 7.0, (0, 22, 4))
            fp = first_person(v, kind, ["right"])
        else:
            front = third_person(v, ["right", "left"], {"right": -18.0, "left": 0.0}, 0, (tw, th), 7.0, (0, 16, 0))
            side = third_person(v, ["right", "left"], {"right": -18.0, "left": 0.0}, 90, (tw, th), 7.0, (0, 16, 0))
            fp = first_person(v, kind, ["right", "left"])
        sheet.paste(front.convert("RGB"), (x0, y))
        sheet.paste(side.convert("RGB"), (x0 + tw + gap, y))
        sheet.paste(fp.resize((fw, fh), Image.LANCZOS).convert("RGB"), (x0 + 2 * (tw + gap), y + (th - fh) // 2))
    sheet.save(path)
    return sheet


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(repo, "docs/gear")
    os.makedirs(out, exist_ok=True)
    data = collect()
    path = os.path.join(out, "twin_hold_sheet.png")
    make_sheet(path, data)
    print("書き出しました:", path)


if __name__ == "__main__":
    main()
