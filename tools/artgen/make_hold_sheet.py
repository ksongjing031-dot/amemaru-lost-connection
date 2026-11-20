"""手に持ったときの確認画像（docs/gear/hold_sheet.png・docs/gear/hold_edge.png）。

hold_sheet.png：剣と大剣の6つを、三人称（体の右から）と一人称で、前と後を並べて描く。
hold_edge.png ：片刃の2つ（見張りの石刃・欠け牙の大剣）の刃の向きを、前と後で並べる（刃の線を赤で描く）。

「後」の持ち方は書き出したモデル（models/item/<名前>_3d.json）から読む。「前」は weapons.hold_display を掛ける前の、
それぞれの作り手の持ち方（weapon_display・base_display）。変換の順は Minecraft と同じ：
  三人称：体の向き → 右腕の付け根と傾き（物を持つ腕は前へ18度）→ x −90度・y 180度 → 手の先へ (1/16, 2/16, −10/16)
          → display（移動 → 回転 X・Y・Z → 拡大）→ −0.5 → モデル/16（ItemInHandLayer と ItemRenderer）
  一人称：カメラの前の (0.56, −0.52, −0.72) → display → −0.5 → モデル/16（ItemInHandRenderer。縦の視野70度）
一人称は、腕の揺れ・歩いたときの揺れのない、止まっているときの形。

使い方：python3 tools/artgen/make_hold_sheet.py [repo] [出力のフォルダ（docs/gear）]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from javamodel import rot_matrix  # noqa: E402
from render import display_transform, render, view_rotation  # noqa: E402

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

# 並べる順（剣3つ、大剣3つ）と、表の名前
ORDER = [("apprentice_sword", "見習いの剣"), ("ancient_stone_sword", "古代石の剣"), ("sentinel_blade", "見張りの石刃（片刃）"),
         ("apprentice_greatsword", "見習いの大剣"), ("ancient_stone_greatsword", "古代石の大剣"),
         ("chipped_fang_greatsword", "欠け牙の大剣（片刃）")]
# 片刃の刃の部品（名前の頭）。その部品の +x の面が刃
EDGE_PARTS = {"sentinel_blade": "seg_", "chipped_fang_greatsword": "fang_edge_"}

BG = (30, 33, 40)
INK, INK2 = (240, 240, 240), (190, 194, 204)
EDGE_RED = (230, 52, 52)


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size, index=0)


# ---------------------------------------------------------------- 持ち方（前と後）
def load_display(repo, name):
    path = os.path.join(repo, "src/main/resources/assets/amemaru/models/item", name + "_3d.json")
    with open(path, encoding="utf-8") as fp:
        return json.load(fp)["display"]


def collect(repo):
    """6つの武器：名前 → {model, tex, kind, before, after}"""
    import make_gear_ancient_stone as an
    import make_gear_apprentice as ap
    import make_gear_rare_f1 as rf
    out = {}
    for mod in (ap, an):
        for it in mod.build_all():
            if it["kind"] in ("sword", "greatsword"):
                name = it["model"].name
                out[name] = {"model": it["model"], "tex": it["tex"], "kind": it["kind"],
                             "before": mod.weapon_display(it["kind"])}
    for it in rf.build_all():
        if it["kind"] in ("sword", "greatsword"):
            out[it["name"]] = {"model": it["model"], "tex": it["tex"], "kind": it["kind"],
                               "before": rf.base_display(it["name"], it["kind"])}
    for name, v in out.items():
        v["after"] = load_display(repo, name)
    return out


def model_points(model):
    return np.array([c for el in model.elements for f in el.faces.values() for c in f.corners_world()])


def blade_length(model, display):
    """持ったときの長さ（ブロック）：モデルの刃の向き（y）の長さ × 拡大"""
    pts = model_points(model)
    return float(pts[:, 1].max() - pts[:, 1].min()) * display["scale"][0] / 16.0


def edge_line(model, name):
    """片刃の刃の線（モデルのピクセル）。刃の部品ごとの +x の面の、厚みのまん中の線をつなぐ。"""
    prefix = EDGE_PARTS.get(name)
    if not prefix:
        return None
    segs = []
    for el in model.elements:
        if el.name.startswith(prefix):
            pts = np.array([c for f in el.faces.values() for c in f.corners_world()])
            lo, hi = pts.min(axis=0), pts.max(axis=0)
            segs.append((lo[1], hi[1], hi[0], (lo[2] + hi[2]) / 2))
    segs.sort()
    line = []
    for y0, y1, x, z in segs:
        line += [np.array([x, y0, z]), np.array([x, y1, z])]
    return line


# ---------------------------------------------------------------- 三人称（体の右から）
TO_WORLD = np.diag([1.0, -1.0, -1.0])        # 南を向いた体のモデル空間（y が下、−z が前）→ ワールド
ROOT = np.array([0.0, 1.501, 0.0])           # モデル空間の原点は足もとから1.501（ブロック）
ARM_PIVOT = np.array([-5.0, 2.0, 0.0]) / 16.0
ARM_XROT = -18.0                             # 物を持っている腕（HumanoidModel の −π/10）
HAND = np.array([1.0, 2.0, -10.0]) / 16.0
SIDE_YAW = 90                                # 体の右（−x）から見る：画面の右が前（+z）、上が上（+y）


def third_person_fn(display):
    """モデルのピクセル → ワールドのピクセル（足もとが原点、y が上、体は +z を向く）"""
    pf0, df0 = display_transform(display)
    A = rot_matrix("x", ARM_XROT)
    L = rot_matrix("x", -90) @ rot_matrix("y", 180)

    def point(p):
        v = ARM_PIVOT + A @ (L @ (HAND + pf0(p) / 16.0))
        return (TO_WORLD @ v + ROOT) * 16.0

    def direction(n):
        v = TO_WORLD @ (A @ (L @ df0(n)))
        return v / np.linalg.norm(v)
    return point, direction


def draw_body(d, ox, oy, s, outline=(128, 132, 140)):
    """体（2ブロック）を右から。画面の右が前（+z）、(ox, oy) が足もと。右腕は前へ18度。"""
    gray, front = (178, 182, 190), (150, 155, 165)

    def box(z0, z1, y0, y1, fill):
        d.rectangle((ox + z0 * s, oy - y1 * s, ox + z1 * s, oy - y0 * s), fill=fill, outline=outline)
    box(-2, 2, 0, 12, gray)       # 脚
    box(-2, 2, 12, 24, gray)      # 胴
    box(-4, 4, 24, 32, gray)      # 頭
    a = math.radians(-ARM_XROT)   # 右腕：肩（y 22、z 0）を軸に前へ
    pts = []
    for (z, y) in ((-2, 24), (2, 24), (2, 12), (-2, 12)):
        dy = y - 22
        zz = z * math.cos(a) - dy * math.sin(a)
        yy = 22 + z * math.sin(a) + dy * math.cos(a)
        pts.append((ox + zz * s, oy - yy * s))
    d.polygon(pts, fill=front, outline=outline)


def side_project(p, center, s, size):
    """ワールドのピクセル → 体の右から見た画面（render の yaw 90・正投影と同じ）"""
    w, h = size
    V = view_rotation(SIDE_YAW, 0) @ (np.asarray(p, dtype=float) - center)
    return (w / 2 + V[0] * s, h / 2 - V[1] * s)


def third_person_panel(model, tex, display, size, s, center=None, edge=None, ruler=True):
    """center：画面のまん中に来るワールドの点（ピクセル）。None なら、足もとが左下に来る。
    edge：刃の線（モデルのピクセル）を赤で描く。"""
    w, h = size
    if center is None:
        ox, oy = 0.27 * w, h - 18
        center = np.array([0.0, (oy - h / 2) / s, (w / 2 - ox) / s])
    center = np.asarray(center, dtype=float)
    ox, oy = side_project((0, 0, 0), center, s, size)
    img = Image.new("RGBA", size, (236, 239, 244, 255))
    d = ImageDraw.Draw(img)
    d.line((0, oy, w, oy), fill=(150, 150, 150), width=2)
    draw_body(d, ox, oy, s)
    if ruler:
        for b in (1, 2, 3):
            y = oy - b * 16 * s
            if 0 < y < h:
                d.line((8, y, 18, y), fill=(120, 120, 120), width=1)
                d.text((22, y), f"{b}", font=font(12), fill=(110, 110, 110), anchor="lm")
    pf, df = third_person_fn(display)
    img.alpha_composite(render(model, tex, yaw=SIDE_YAW, pitch=0, scale=s, size=size, center=center,
                               transform=(pf, df), light="world", ss=2))
    if edge:
        d = ImageDraw.Draw(img)
        pts = [side_project(pf(p), center, s, size) for p in edge]
        for a, b in zip(pts[0::2], pts[1::2]):
            d.line((a, b), fill=EDGE_RED, width=3)
        mid = pts[len(pts) // 2]
        n = df(np.array([1.0, 0.0, 0.0]))    # 刃の向き（モデルの +x）
        tip = side_project(pf(edge[len(edge) // 2]) + n * (26.0 / s), center, s, size)
        arrow(d, mid, tip, EDGE_RED)
        d.text((tip[0] + 4, tip[1] + 2), "刃", font=font(18, True), fill=EDGE_RED, anchor="lm")
    return img


def arrow(d, a, b, color, width=3):
    d.line((a, b), fill=color, width=width)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    for da in (2.6, -2.6):
        d.line((b, (b[0] + 9 * math.cos(ang + da), b[1] + 9 * math.sin(ang + da))), fill=color, width=width)


# ---------------------------------------------------------------- 一人称
FP_W, FP_H = 854, 480
FP_F = (FP_H / 2) / math.tan(math.radians(35))
FP_ARM = np.array([0.56, -0.52, -0.72]) * 16.0
FP_P = 400.0


def fp_project(p):
    """手の枠（ピクセル）→ 一人称の画面（854×480）。カメラは原点で −z を見る。"""
    q = np.asarray(p, dtype=float)
    k = FP_P / (FP_P - (q[2] + FP_P))
    s = FP_F / FP_P
    return (FP_W / 2 + q[0] * k * s, FP_H / 2 - q[1] * k * s)


def first_person_image(model, tex, display, edge=None):
    pf0, df = display_transform(display)

    def pf(p):
        return pf0(p) + FP_ARM
    img = render(model, tex, scale=FP_F / FP_P, size=(FP_W, FP_H), center=(0, 0, -FP_P), perspective=FP_P,
                 transform=(pf, df), light="world", ss=2)
    bg = Image.new("RGBA", (FP_W, FP_H), (150, 185, 235, 255))
    d = ImageDraw.Draw(bg)
    d.rectangle((0, FP_H * 0.48, FP_W, FP_H), fill=(140, 140, 140, 255))
    d.line((FP_W / 2 - 7, FP_H / 2, FP_W / 2 + 7, FP_H / 2), fill=(30, 30, 30, 255), width=2)
    d.line((FP_W / 2, FP_H / 2 - 7, FP_W / 2, FP_H / 2 + 7), fill=(30, 30, 30, 255), width=2)
    d.rectangle((FP_W / 2 - 182, FP_H - 44, FP_W / 2 + 182, FP_H - 4), outline=(20, 20, 40, 255), width=3)
    bg.alpha_composite(img)
    facing = None
    if edge:
        d = ImageDraw.Draw(bg)
        n = df(np.array([1.0, 0.0, 0.0]))
        mid = pf(edge[len(edge) // 2])
        facing = float(np.dot(n, -mid / np.linalg.norm(mid)))    # 1 に近いほど刃が自分の方
        pts = [fp_project(pf(p)) for p in edge]
        for a, b in zip(pts[0::2], pts[1::2]):
            if facing > 0:
                d.line((a, b), fill=EDGE_RED, width=4)
            else:
                dashed(d, a, b, EDGE_RED, width=4)
    return bg, facing


def dashed(d, a, b, color, width=3, on=7, off=6):
    L = math.hypot(b[0] - a[0], b[1] - a[1])
    if L < 1e-6:
        return
    t = 0.0
    while t < L:
        t1 = min(t + on, L)
        d.line(((a[0] + (b[0] - a[0]) * t / L, a[1] + (b[1] - a[1]) * t / L),
                (a[0] + (b[0] - a[0]) * t1 / L, a[1] + (b[1] - a[1]) * t1 / L)), fill=color, width=width)
        t = t1 + off


# ---------------------------------------------------------------- 絵1：6つの持ち方（前と後）
def make_sheet(path, data):
    tw, th = 260, 190            # 三人称
    fw, fh = 260, 146            # 一人称（画面全体を縮める）
    margin, gap, label_w = 32, 12, 178
    W = margin * 2 + label_w + 4 * tw + 3 * gap + 16
    row_h = th + 14
    top = 168
    H = top + len(ORDER) * row_h + 16
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((margin, 24), "手に持ったときの大きさと刃の向き（剣と大剣、前と後）", font=font(30, True), fill=INK)
    d.text((margin, 70), "三人称は体の右から（体は2ブロック、目盛りは地面からのブロック）。一人称は止まっているとき。\n"
                         "剣は前の1.35倍、大剣は前の1.7倍（握りの所はそのまま）。片刃の石刃と大剣は、刃が前（三人称は下、一人称は向こう）を向く",
           font=font(15), fill=INK2, spacing=6)
    x0 = margin + label_w
    heads = ["三人称・前", "三人称・後", "一人称・前", "一人称・後"]
    for c, head in enumerate(heads):
        cx = x0 + c * (tw + gap) + (16 if c >= 2 else 0)
        d.text((cx, top - 30), head, font=font(16, True), fill=INK)
    for r, (name, label) in enumerate(ORDER):
        v = data[name]
        y = top + r * row_h
        l0 = blade_length(v["model"], v["before"]["thirdperson_righthand"])
        l1 = blade_length(v["model"], v["after"]["thirdperson_righthand"])
        k = v["after"]["thirdperson_righthand"]["scale"][0] / v["before"]["thirdperson_righthand"]["scale"][0]
        name_lines = label.replace("（", "\n（")
        d.text((margin, y + 4), name_lines, font=font(18, True), fill=INK, spacing=4)
        d.text((margin, y + 64), f"長さ（三人称）\n約{l0:.1f} → 約{l1:.1f}ブロック\n（{k:.2f}倍）", font=font(14), fill=INK2,
               spacing=4)
        for c, when in enumerate(("before", "after")):
            img = third_person_panel(v["model"], v["tex"], v[when]["thirdperson_righthand"], (tw, th), 3.0)
            sheet.paste(img.convert("RGB"), (x0 + c * (tw + gap), y))
        for c, when in enumerate(("before", "after")):
            img, _ = first_person_image(v["model"], v["tex"], v[when]["firstperson_righthand"])
            cx = x0 + (2 + c) * (tw + gap) + 16
            sheet.paste(img.resize((fw, fh), Image.LANCZOS).convert("RGB"), (cx, y + (th - fh) // 2))
    sheet.save(path)
    return sheet


# ---------------------------------------------------------------- 絵2：片刃の向き（前と後）
def make_edge_sheet(path, data):
    names = [("sentinel_blade", "見張りの石刃"), ("chipped_fang_greatsword", "欠け牙の大剣")]
    fw, fh = 330, 300            # 一人称：画面の右側（x 470〜854、y 60〜480）を切り出す
    crop = (470, 60, 854, 480)
    tw, th = 330, 300
    margin, gap = 32, 14
    W = margin * 2 + 4 * fw + 3 * gap + 20
    row_h = 40 + 26 + fh + 30
    H = 128 + 2 * row_h + 10
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((margin, 24), "片刃の刃の向き（前と後）", font=font(30, True), fill=INK)
    d.text((margin, 70), "赤い線が刃（切れる側）。一人称の点線は、刃が向こう側にあって見えないこと。\n"
                         "前は、一人称で刃が自分の方、三人称で刃が上を向いていた。後は、刃が前（一人称は向こう、三人称は下）を向く",
           font=font(15), fill=INK2, spacing=6)
    for r, (name, label) in enumerate(names):
        v = data[name]
        edge = edge_line(v["model"], name)
        y = 128 + r * row_h
        d.text((margin, y), label, font=font(20, True), fill=INK)
        x = margin
        for col, (view, when) in enumerate((("fp", "before"), ("fp", "after"), ("tp", "before"), ("tp", "after"))):
            cx = x + col * (fw + gap) + (20 if col >= 2 else 0)
            head = ("一人称" if view == "fp" else "三人称") + "・" + ("前" if when == "before" else "後")
            disp = v[when]
            if view == "fp":
                img, facing = first_person_image(v["model"], v["tex"], disp["firstperson_righthand"], edge)
                img = img.crop(crop).resize((fw, fh), Image.LANCZOS)
                note = "刃が自分の方" if facing > 0 else "刃が向こう（背が自分の方）"
            else:
                tp = disp["thirdperson_righthand"]
                pf, _ = third_person_fn(tp)
                pts = np.array([pf(p) for p in model_points(v["model"])])
                c = (pts.min(axis=0) + pts.max(axis=0)) / 2
                img = third_person_panel(v["model"], v["tex"], tp, (tw, th), 6.0 if name == "sentinel_blade" else 4.6,
                                         center=c, edge=edge, ruler=False)
                n = third_person_fn(tp)[1](np.array([1.0, 0.0, 0.0]))
                note = "刃が上" if n[1] > 0.5 else ("刃が下" if n[1] < -0.5 else "刃が横")
            d.text((cx, y + 36), head, font=font(16, True), fill=INK)
            d.text((cx + fw, y + 38), note, font=font(14), fill=(255, 140, 140) if when == "before" else (140, 220, 160),
                   anchor="ra")
            sheet.paste(img.convert("RGB"), (cx, y + 66))
    sheet.save(path)
    return sheet


def main():
    repo = sys.argv[1] if len(sys.argv) > 1 else "."
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(repo, "docs/gear")
    os.makedirs(out, exist_ok=True)
    data = collect(repo)
    for name, _ in ORDER:
        v = data[name]
        l0 = blade_length(v["model"], v["before"]["thirdperson_righthand"])
        l1 = blade_length(v["model"], v["after"]["thirdperson_righthand"])
        print(f"{name:26s} 三人称の長さ {l0:.2f} → {l1:.2f} ブロック")
    make_sheet(os.path.join(out, "hold_sheet.png"), data)
    make_edge_sheet(os.path.join(out, "hold_edge.png"), data)
    print("書き出しました:", os.path.join(out, "hold_sheet.png"), os.path.join(out, "hold_edge.png"))


if __name__ == "__main__":
    main()
