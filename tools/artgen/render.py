"""Java版のモデル（javamodel.JavaModel）を、絵（テクスチャ）ごと描く小さな描画プログラム（確認画像用）。

Minecraftと同じ考え方で描く：
・面は片面だけ（裏から見た面は描かない）。絵はピクセルのまま（ぼかさない）。透明のピクセルは描かない。
・明るさは、Minecraftの「2つの光＋環境光」（光の強さ0.6・環境光0.4）。光る部品は明るさを変えない。
・正投影（Blockbenchの正面・側面と同じ）と、遠近つきの両方を描ける。
"""
import math

import numpy as np
from PIL import Image

from javamodel import rot_matrix


def normalize(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


# Minecraftの光（ワールドの中。Lighting.setupLevel と同じ向き）
MC_LIGHT_0 = normalize((0.2, 1.0, -0.7))
MC_LIGHT_1 = normalize((-0.2, 1.0, 0.7))


def mc_brightness(n, l0=MC_LIGHT_0, l1=MC_LIGHT_1):
    a = max(0.0, float(np.dot(n, l0)))
    b = max(0.0, float(np.dot(n, l1)))
    return min(1.0, (a + b) * 0.6 + 0.4)


def gui_flat_lights():
    """「gui_light: front」のときの光（Lighting.setupForFlatItems と同じ計算）。"""
    m = np.diag([1.0, -1.0, 1.0]) @ rot_matrix("y", -22.5) @ rot_matrix("x", 135.0)
    return normalize(m @ MC_LIGHT_0), normalize(m @ MC_LIGHT_1)


def view_rotation(yaw=0.0, pitch=0.0, roll=0.0):
    """モデル → 画面の向き。yaw（左右に回す）→ pitch（上から見下ろす）→ roll の順。カメラは −z の向きを見る。"""
    return rot_matrix("z", roll) @ rot_matrix("x", pitch) @ rot_matrix("y", yaw)


def display_transform(display):
    """アイテムの display（rotation・translation・scale）を、モデルのピクセル座標に掛ける関数にする。

    Minecraftの順番：モデル/16 → −0.5 → 拡大 → 回転（X・Y・Zの順に掛けた行列）→ 移動/16。結果はピクセル（×16）で返す。
    """
    rx, ry, rz = display.get("rotation", [0, 0, 0])
    tx, ty, tz = display.get("translation", [0, 0, 0])
    sx, sy, sz = display.get("scale", [1, 1, 1])
    R = rot_matrix("x", rx) @ rot_matrix("y", ry) @ rot_matrix("z", rz)
    S = np.diag([sx, sy, sz])
    T = np.array([tx, ty, tz]) / 16.0

    def point(p):
        q = np.asarray(p, dtype=float) / 16.0 - 0.5
        return (R @ (S @ q) + T) * 16.0

    def direction(n):
        v = R @ (np.linalg.inv(S).T @ np.asarray(n, dtype=float))
        return normalize(v)

    return point, direction


def collect_faces(model, texture):
    faces = []
    for el in model.elements:
        for f in el.faces.values():
            faces.append((el, f, [c.copy() for c in f.corners_world()], f.normal_world()))
    return faces


def render(model, texture, yaw=0.0, pitch=0.0, roll=0.0, scale=16.0, size=(512, 512), center=(0.0, 0.0, 0.0),
           ss=3, perspective=None, transform=None, light="world", silhouette=None, glow_boost=1.0, darkness=None):
    """モデルを描いて RGBA の Image を返す。

    texture   : numpy の (H, W, 4)。1コマ目を渡す。
    scale     : 1ピクセル（モデルの1/16ブロック）が画面の何ピクセルか
    center    : 画面の真ん中に来るモデルの点
    perspective : None なら正投影。数値ならカメラまでの距離（ピクセル単位）
    transform : (point_fn, dir_fn)。display の変換などを先に掛けるとき
    light     : "world"（Minecraftの光）・"gui"（gui_light: front）・"flat"（明るさを変えない）
    silhouette: 色を渡すと、形だけをその色で塗る
    darkness  : 0〜1。暗い所の見え方（光らない部品だけ暗くする）
    """
    w, h = size
    W, H = w * ss, h * ss
    color = np.zeros((H, W, 3), dtype=float)
    alpha = np.zeros((H, W), dtype=float)
    zbuf = np.full((H, W), -np.inf)
    Rv = view_rotation(yaw, pitch, roll)
    cx, cy = W / 2.0, H / 2.0
    s = scale * ss
    tex = texture.astype(float)
    if light == "gui":
        l0, l1 = gui_flat_lights()
    else:
        l0, l1 = MC_LIGHT_0, MC_LIGHT_1
    center = np.asarray(center, dtype=float)

    for el, f, corners, normal in collect_faces(model, texture):
        if transform:
            pf, df = transform
            corners = [pf(c) for c in corners]
            normal = df(normal)
        V = np.array([Rv @ (c - center) for c in corners])
        nview = Rv @ normal
        if perspective:
            # カメラは +z の perspective の位置から原点を見る
            depth = perspective - V[:, 2]
            if np.any(depth <= 1):
                continue
            k = perspective / depth
            sxs = cx + V[:, 0] * k * s
            sys_ = cy - V[:, 1] * k * s
            # 面がカメラを向いているか（遠近では位置で判定）
            cam = np.array([0.0, 0.0, perspective])
            if np.dot(nview, cam - V[0]) <= 1e-9:
                continue
            winv = 1.0 / depth
        else:
            if nview[2] <= 1e-9:
                continue
            sxs = cx + V[:, 0] * s
            sys_ = cy - V[:, 1] * s
            winv = np.ones(4)
        z = V[:, 2]
        u0, v0, u1, v1 = f.uv
        uvs = np.array([(u0, v0), (u0, v1), (u1, v1), (u1, v0)], dtype=float)
        if el.glow:
            bright = glow_boost
        elif light == "flat":
            bright = 1.0
        elif light == "gui":
            bright = mc_brightness(nview, l0, l1)
        else:
            bright = mc_brightness(normal, l0, l1)
        if darkness is not None and not el.glow:
            bright *= darkness
        for tri in ((0, 1, 2), (0, 2, 3)):
            _raster(color, alpha, zbuf, sxs[list(tri)], sys_[list(tri)], z[list(tri)], uvs[list(tri)], winv[list(tri)],
                    tex, (u0, v0, u1, v1), bright, silhouette)

    out = np.zeros((H, W, 4), dtype=float)
    out[..., :3] = color
    out[..., 3] = alpha
    # 縮小（ぼかしは縮小のときだけ。ふちが滑らかになる）
    out = out.reshape(h, ss, w, ss, 4).mean(axis=(1, 3))
    a = out[..., 3:4]
    rgb = np.where(a > 1e-6, out[..., :3] / np.maximum(a, 1e-6), 0)
    img = np.concatenate([rgb, a * 255.0], axis=2)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def _raster(color, alpha, zbuf, xs, ys, zs, uvs, winv, tex, bounds, bright, silhouette):
    H, W = alpha.shape
    minx = max(int(math.floor(xs.min())), 0)
    maxx = min(int(math.ceil(xs.max())), W - 1)
    miny = max(int(math.floor(ys.min())), 0)
    maxy = min(int(math.ceil(ys.max())), H - 1)
    if minx > maxx or miny > maxy:
        return
    px, py = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
    x0, x1, x2 = xs
    y0, y1, y2 = ys
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
    w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
    w2 = 1.0 - w0 - w1
    eps = -1e-6
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    # 遠近の補正（正投影では winv はすべて1）
    q0, q1, q2 = w0 * winv[0], w1 * winv[1], w2 * winv[2]
    qs = q0 + q1 + q2
    q0, q1, q2 = q0 / qs, q1 / qs, q2 / qs
    zz = q0 * zs[0] + q1 * zs[1] + q2 * zs[2]
    u = q0 * uvs[0, 0] + q1 * uvs[1, 0] + q2 * uvs[2, 0]
    v = q0 * uvs[0, 1] + q1 * uvs[1, 1] + q2 * uvs[2, 1]
    bu0, bv0, bu1, bv1 = bounds
    tu = np.clip(np.floor(u).astype(int), bu0, bu1 - 1)
    tv = np.clip(np.floor(v).astype(int), bv0, bv1 - 1)
    texel = tex[tv, tu]
    sub = (slice(miny, maxy + 1), slice(minx, maxx + 1))
    visible = inside & (texel[..., 3] >= 128) & (zz > zbuf[sub])
    if not visible.any():
        return
    zb = zbuf[sub]
    zb[visible] = zz[visible]
    zbuf[sub] = zb
    c = color[sub]
    if silhouette is not None:
        c[visible] = np.array(silhouette[:3], dtype=float)
    else:
        c[visible] = texel[..., :3][visible] * bright
    color[sub] = c
    a = alpha[sub]
    a[visible] = 1.0
    alpha[sub] = a
