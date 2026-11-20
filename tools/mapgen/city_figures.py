"""街全体の通しの確かめの絵（check_city.py --render DIR から呼ぶ）。

  city_map.png      上から見た街：11の施設のブロックの色と、まだ作っていない道・広場・城壁・門・結界の柱
  city_heights.png  南から見た高さ：施設ごとの影絵と、都市のルール④の高さの順番
  city_lights.png   夜の灯り：中央広場から歩ける所の明るさ（ブロックの光）と、外の灯りの種類
  city_walk.png     つながる道：中央広場の真ん中から、跳ばずに行けて戻れる所
  city_aerial.png   鳥瞰（--aerial のときだけ。光線で描くので数分かかる）

座標は世界の座標（大結晶が x 0・z 0。x が東、z が南）。絵は北が上。
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from mapgen.render import SOLID_COLORS, face_textures

FONT_R = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

INK = (11, 11, 11)
INK2 = (82, 81, 78)
MUTED = (140, 138, 132)
SURF = (252, 252, 251)

# 地区の色（Tab 1 ⑧のゾーニングの図と同じ並び。色は分けるためだけで、名前は必ず文字でも書く）
DISTRICTS = ["冒険者地区", "商業地区", "娯楽地区", "宗教地区", "水辺地区", "倉庫地区"]
DISTRICT_RGB = {"冒険者地区": (42, 120, 214), "商業地区": (235, 104, 52), "娯楽地区": (27, 175, 122),
                "宗教地区": (237, 161, 0), "水辺地区": (232, 123, 164), "倉庫地区": (0, 131, 0)}
FAC_DISTRICT = {"ギルド": "冒険者地区", "塔の入口": "冒険者地区", "武器工房": "商業地区", "防具工房": "商業地区",
                "装飾工房": "商業地区", "商店街": "商業地区", "フリーマーケット": "商業地区", "カジノ": "娯楽地区",
                "結びの教会": "宗教地区", "釣り堀": "水辺地区", "個人倉庫": "倉庫地区"}
FAC_NAME = {"ギルド": "冒険者ギルド"}

# 地図の範囲（ブロック）と1ブロックの大きさ（px）
X0, X1, Z0, Z1 = -138, 124, -182, 121
SC = 4
ML, MT, MR, MB = 28, 112, 28, 176
MAP_W = (X1 - X0 + 1) * SC
MAP_H = (Z1 - Z0 + 1) * SC

THIN = {"chain", "lightning_rod", "end_rod", "tripwire_hook", "string", "lever", "candle", "air", "structure_void",
        "cave_air", "light"}

# 名前を置く所（世界の x・z）
FAC_LABELS = {
    "ギルド": (24, -80), "個人倉庫": (50, -71), "武器工房": (92, -14), "防具工房": (70, 15), "装飾工房": (31, -16),
    "商店街": (10, 38), "フリーマーケット": (-33, 76), "結びの教会": (-88, -4), "釣り堀": (-79, 49), "カジノ": (30, 86),
    "塔の入口": (0, -166),
}
ROAD_LABELS = [("塔の道", (0, -70)), ("工房通り", (46, 0)), ("市場通り", (0, 104)), ("祈りの道", (-40, 0)),
               ("環状路", (-30, -56)), ("環状路", (56, 32)), ("城壁沿いの道", (78, -78)), ("塔前広場", (0, -128)),
               ("中央広場", (0, 13)), ("湖", (-118, 82))]
GATES = {"北門": (0, -114), "東門": (114, 0), "南門": (0, 114)}
PILLARS = [(-9, -56), (56, -9), (9, 56), (-56, 9), (56, -56), (56, 56), (-56, 56), (-56, -56)]


def font(size, bold=False):
    try:
        return ImageFont.truetype(FONT_B if bold else FONT_R, size, index=0)
    except OSError:
        return ImageFont.load_default()


def P(x, z):
    """世界の (x, z)（ブロックの北西の角）→ 地図の絵の px"""
    return (ML + (x - X0) * SC, MT + (z - Z0) * SC)


def PC(x, z):
    """ブロックの真ん中"""
    return (ML + (x - X0 + 0.5) * SC, MT + (z - Z0 + 0.5) * SC)


def mix(c, w, t):
    return tuple(int(round(a * (1 - t) + b * t)) for a, b in zip(c, w))


# ---------------------------------------------------------------- ブロックの上の面の色
_RGB = {}


def top_rgb(st):
    c = _RGB.get(st)
    if c is not None:
        return c
    n, props = st[0], dict(st[1])
    try:
        if n in SOLID_COLORS:
            base, glow, _ = SOLID_COLORS[n]
            c = np.array(glow if glow else base, dtype=np.float64) / 255.0
        else:
            t = np.asarray(face_textures(n, props)[2], dtype=np.float64)
            a = t[..., 3] > 0.5
            c = t[..., :3][a].mean(axis=0) if a.any() else np.array([0.6, 0.6, 0.6])
    except Exception:
        c = np.array([0.6, 0.6, 0.6])
    _RGB[st] = c
    return c


def top_layer(city):
    """柱ごとのいちばん上のブロック（細い飾りは除く）：高さ・状態・書いた構造物"""
    W, D = X1 - X0 + 1, Z1 - Z0 + 1
    H = np.full((D, W), -99, dtype=np.int64)
    ST = np.empty((D, W), dtype=object)
    OWN = np.empty((D, W), dtype=object)
    for (x, y, z), st in city.world.items():
        if not (X0 <= x <= X1 and Z0 <= z <= Z1):
            continue
        n = st[0]
        if n in THIN or n.endswith("_sign") or n.endswith("_banner") or n.endswith("_button"):
            continue
        i, j = z - Z0, x - X0
        if y > H[i, j]:
            H[i, j] = y
            ST[i, j] = st
            OWN[i, j] = city.owner.get((x, y, z))
    return H, ST, OWN


def shaded_rgb(H, ST):
    """上の面の色に、北西からの光の陰と、高さの違う所の縁の線をつける（0〜1 の RGB）"""
    D, W = H.shape
    img = np.zeros((D, W, 3))
    has = H > -99
    for i, j in zip(*np.nonzero(has)):
        img[i, j] = top_rgb(ST[i, j])
    Hf = np.where(has, H, 0).astype(np.float64)
    pad = np.pad(Hf, 1, mode="edge")
    nw = pad[:-2, :-2]
    shade = 1.0 + 0.045 * np.clip(Hf - nw, -8, 8)
    shade *= 0.9 + 0.12 * np.clip(Hf / 40.0, 0.0, 1.0)
    edge = np.zeros_like(Hf, dtype=bool)
    for di, dj in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        nb = pad[1 + di:1 + di + D, 1 + dj:1 + dj + W]
        edge |= np.abs(Hf - nb) >= 3
    shade = np.where(edge & has, shade * 0.8, shade)
    return np.clip(img * shade[..., None], 0, 1), has


# ---------------------------------------------------------------- 下地：まだ作っていない道・広場・城壁と、施設のない土地
LOT_RGB = (233, 237, 225)
OUT_RGB = (219, 227, 207)
ROAD_RGB = (214, 207, 192)
WALL_RGB = (150, 146, 138)


def base_layer(cc):
    W, D = X1 - X0 + 1, Z1 - Z0 + 1
    img = np.zeros((D, W, 3), dtype=np.float64)
    kind = np.zeros((D, W), dtype=np.int8)        # 0 土地、1 城壁の外、2 道・広場、3 城壁
    for i in range(D):
        z = Z0 + i
        for j in range(W):
            x = X0 + j
            r = math.hypot(x + 0.5, z + 0.5)
            if cc.road_at(x, z):
                kind[i, j] = 2              # 門の所は、大通りが城壁を抜ける
            elif cc.WALL_IN <= r < cc.WALL_OUT:
                kind[i, j] = 3
            elif r >= cc.WALL_OUT:
                kind[i, j] = 1
    cols = {0: LOT_RGB, 1: OUT_RGB, 2: ROAD_RGB, 3: WALL_RGB}
    for k, c in cols.items():
        img[kind == k] = np.array(c) / 255.0
    return img, kind


def to_image(arr, scale=SC):
    a = (np.clip(arr, 0, 1) * 255).astype(np.uint8)
    im = Image.fromarray(a, "RGB")
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def canvas(title, subtitle, w=None, h=None):
    w = w or (ML + MAP_W + MR)
    h = h or (MT + MAP_H + MB)
    im = Image.new("RGB", (w, h), SURF)
    d = ImageDraw.Draw(im)
    d.text((ML, 22), title, font=font(26, True), fill=INK)
    y = 62
    for line in subtitle.split("\n"):
        d.text((ML, y), line, font=font(16), fill=INK2)
        y += 22
    return im, d


def halo_text(d, xy, text, f, fill=INK, halo=(255, 255, 255), anchor="mm", width=3):
    d.text(xy, text, font=f, fill=fill, anchor=anchor, stroke_width=width, stroke_fill=halo)


def label_box(d, xy, text, color, f):
    x, y = xy
    l, t, r, b = d.textbbox((x, y), text, font=f, anchor="mm")
    pad = 6
    d.rounded_rectangle((l - pad - 14, t - pad + 1, r + pad, b + pad), radius=6, fill=(255, 255, 255),
                        outline=color, width=2)
    d.rounded_rectangle((l - pad - 9, (t + b) / 2 - 5, l - pad + 1, (t + b) / 2 + 5), radius=2, fill=color)
    d.text((x, y), text, font=f, fill=INK, anchor="mm")


def north_and_scale(d, x, y, ink=INK, ink2=INK2):
    f = font(15)
    d.polygon([(x, y - 22), (x - 8, y - 2), (x + 8, y - 2)], fill=ink)
    d.text((x, y + 10), "北", font=font(16, True), fill=ink, anchor="mm")
    sx = x + 40
    d.line([(sx, y + 4), (sx + 50 * SC, y + 4)], fill=ink, width=2)
    for k in (0, 25, 50):
        d.line([(sx + k * SC, y - 2), (sx + k * SC, y + 10)], fill=ink, width=2)
    d.text((sx + 25 * SC, y + 22), "50ブロック", font=f, fill=ink2, anchor="mm")


def swatch(d, x, y, fill, text, outline=None, f=None):
    d.rounded_rectangle((x, y - 8, x + 16, y + 8), radius=3, fill=fill, outline=outline, width=1 if outline else 0)
    d.text((x + 24, y), text, font=f or font(15), fill=INK, anchor="lm")


def draw_common_marks(d, cc):
    """門・結界の柱・大結晶（どれもまだ作っていない）の印"""
    for name, (x, z) in GATES.items():
        halo_text(d, PC(x, z), name, font(16, True), fill=INK)
    for (x, z) in PILLARS:
        cx, cy = PC(x, z)
        d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=INK, outline=(255, 255, 255), width=2)
    cx, cy = PC(0, 0)
    d.polygon([(cx, cy - 11), (cx + 8, cy), (cx, cy + 11), (cx - 8, cy)], fill=(160, 200, 230), outline=INK)
    gx, gy = PC(-95, 64)
    halo_text(d, (gx - 6, gy - 16), "水門", font(16, True))


# ---------------------------------------------------------------- 1. 上から見た街
def draw_map(cc, city, path):
    H, ST, OWN = top_layer(city)
    rgb, has = shaded_rgb(H, ST)
    base, kind = base_layer(cc)
    img = np.where(has[..., None], rgb, base)
    im, d = canvas("11の施設を、地図を組むときの位置に並べた街（上から。北が上）",
                   "色は各構造物のいちばん上のブロック。灰色の帯は、まだ作っていない道・広場（ゾーニングの図の位置）と城壁"
                   "（半径112〜116）。\n薄い緑は、まだ何もない土地（家・木・倉庫地区を置く所）。")
    im.paste(to_image(img), (ML, MT))
    d = ImageDraw.Draw(im)
    # 中央広場の縁（半径22.5）
    cx, cy = PC(-0.5, -0.5)
    r = cc.PLAZA_R * SC
    d.ellipse((cx - r + 2, cy - r + 2, cx + r + 2, cy + r + 2), outline=(150, 145, 132), width=2)
    draw_common_marks(d, cc)
    fr = font(15)
    for name, (x, z) in ROAD_LABELS:
        halo_text(d, PC(x, z), name, fr, fill=INK2)
    fb = font(20, True)
    for fac, (x, z) in FAC_LABELS.items():
        label_box(d, PC(x, z), FAC_NAME.get(fac, fac), DISTRICT_RGB[FAC_DISTRICT[fac]], fb)
    # 凡例
    y0 = MT + MAP_H + 34
    f = font(15)
    swatch(d, ML, y0, ROAD_RGB, "まだ作っていない道と広場")
    swatch(d, ML + 250, y0, WALL_RGB, "まだ作っていない城壁")
    swatch(d, ML + 470, y0, LOT_RGB, "まだ何もない土地", outline=(190, 190, 180))
    d.ellipse((ML + 650, y0 - 5, ML + 660, y0 + 5), fill=INK, outline=(255, 255, 255), width=2)
    d.text((ML + 670, y0), "結界の柱（まだ。8本）", font=f, fill=INK, anchor="lm")
    x = ML
    for k in DISTRICTS:
        d.rounded_rectangle((x, y0 + 36, x + 16, y0 + 52), radius=3, fill=DISTRICT_RGB[k])
        d.text((x + 24, y0 + 44), k, font=f, fill=INK, anchor="lm")
        x += 150
    d.text((ML, y0 + 80), "名前の札の色の四角は地区（Tab 1 ⑧）。大結晶の◆と門の名前は、まだ作っていない所の目印。", font=f,
           fill=INK2, anchor="lm")
    north_and_scale(d, ML + MAP_W - 300, MT + MAP_H - 40)
    im.save(path)
    return H


# ---------------------------------------------------------------- 2. 南から見た高さ
HV = 8          # 高さ 1 ブロックの px（横の2倍）
EL_TOP = 54


def draw_heights(cc, city, path):
    W = X1 - X0 + 1
    prof = {}
    zsum = {}
    for (x, y, z), st in city.world.items():
        if st[0] in ("air", "structure_void") or y < 1 or not (X0 <= x <= X1):
            continue
        fac = city.facility_at((x, y, z))
        if fac is None:
            continue
        a = prof.setdefault(fac, np.full(W, -1, dtype=np.int64))
        j = x - X0
        if y > a[j]:
            a[j] = y
        s = zsum.setdefault(fac, [0.0, 0])
        s[0] += z
        s[1] += 1
    order = sorted(prof, key=lambda f: zsum[f][0] / zsum[f][1])        # 北（奥）から先に描く
    gw = MAP_W
    gh = EL_TOP * HV
    top = 120
    w = ML + gw + 250
    h = top + gh + 170
    im, d = canvas("南から見た街の高さ（施設ごとの影絵）",
                   "それぞれの施設の、いちばん高い所の線（奥の施設から順に重ねた）。高さは道から（道のブロックが 0）。"
                   "縦は横の2倍に伸ばしてある。", w=w, h=h)

    def Q(x, y):
        return (ML + (x - X0) * SC, top + (EL_TOP - y) * HV)

    # ルール④の高さの線
    rules = [(48, "48 塔の壁（いまの塔の入口）"), (40, "40 結びの教会の鐘楼"), (34, "34 冒険者ギルドの塔"),
             (30, "30 大結晶【提案】"), (24, "24 カジノ"), (22, "22 店・工房・倉庫・城門の塔"),
             (18, "18 中央広場を囲む1列目の棟"), (12, "12 城壁")]
    fr = font(14)
    for y, t in rules:
        x0, yy = Q(X0, y)
        x1, _ = Q(X1 + 1, y)
        for k in range(int(x0), int(x1), 10):
            d.line([(k, yy), (min(k + 5, x1), yy)], fill=(196, 194, 186), width=1)
        d.text((x1 + 10, yy), t, font=fr, fill=INK2, anchor="lm")
    # まだ作っていない大結晶と城壁（点線の箱）
    for (xa, xb, ytop, t) in ((-116, -113, 12, ""), (112, 115, 12, "")):
        a, b = Q(xa, ytop), Q(xb + 1, 0)
        for k in range(int(a[0]), int(b[0]) + 1, 6):
            d.point([(k, a[1])], fill=MUTED)
        d.rectangle((a[0], a[1], b[0], b[1]), outline=MUTED, width=1)
    halo_text(d, (Q(-114, 13.5)[0], Q(-114, 13.5)[1]), "城壁（まだ）", font(13), fill=INK2)
    # 施設の影絵
    outlines = []
    for fac in order:
        a = prof[fac]
        c = DISTRICT_RGB[FAC_DISTRICT[fac]]
        fill = mix(c, (255, 255, 255), 0.62)
        line = mix(c, (0, 0, 0), 0.15)
        cols = np.nonzero(a >= 1)[0]
        if not cols.size:
            continue
        # 続いている所ごとに多角形を描く
        runs = np.split(cols, np.nonzero(np.diff(cols) > 1)[0] + 1)
        for run in runs:
            pts = [Q(X0 + run[0], 0)]
            for j in run:
                pts.append(Q(X0 + j, a[j] + 1))
                pts.append(Q(X0 + j + 1, a[j] + 1))
            pts.append(Q(X0 + run[-1] + 1, 0))
            d.polygon(pts, fill=fill, outline=line)
            d.line(pts[1:-1], fill=line, width=2)
            outlines.append((pts[1:-1], line))
    # 手前の施設に隠れた所も、いちばん高い所の線だけは見えるように、線をもう一度上から描く
    for pts, line in outlines:
        d.line(pts, fill=line, width=2)
    a, b = Q(-2, 30), Q(3, 0)
    d.rectangle((a[0], a[1], b[0], b[1]), outline=INK2, width=1)
    for k in range(int(a[1]), int(b[1]), 8):
        d.line([(a[0], k), (b[0], k)], fill=(150, 150, 150), width=1)
    # 名前と高さ（いちばん高い所の上）
    placed = []
    fb = font(15, True)
    for fac in order:
        a = prof[fac]
        j = int(np.argmax(a))
        hmax = int(a[j])
        x, y = Q(X0 + j + 0.5, hmax + 1)
        text = f"{FAC_NAME.get(fac, fac)} {hmax}"
        ty = y - 14
        for (px, py) in placed:
            if abs(px - x) < 120 and abs(py - ty) < 20:
                ty = py - 22
        placed.append((x, ty))
        d.line([(x, y - 2), (x, ty + 8)], fill=INK2, width=1)
        halo_text(d, (x, ty), text, fb)
    # 地面と目盛り
    gx0, gy0 = Q(X0, 0)
    gx1, _ = Q(X1 + 1, 0)
    d.line([(gx0, gy0), (gx1, gy0)], fill=INK, width=2)
    d.text((gx0, gy0 + 18), "西", font=font(16, True), fill=INK, anchor="lm")
    d.text((gx1, gy0 + 18), "東", font=font(16, True), fill=INK, anchor="rm")
    for x in range(-125, 125, 25):
        px, _ = Q(x, 0)
        d.line([(px, gy0), (px, gy0 + 6)], fill=INK2, width=1)
        d.text((px, gy0 + 18), f"x {x}", font=font(12), fill=MUTED, anchor="mm")
    y0 = gy0 + 52
    x = ML
    for k in DISTRICTS:
        d.rounded_rectangle((x, y0 - 8, x + 16, y0 + 8), radius=3, fill=mix(DISTRICT_RGB[k], (255, 255, 255), 0.62),
                            outline=mix(DISTRICT_RGB[k], (0, 0, 0), 0.15), width=2)
        d.text((x + 24, y0), k, font=font(15), fill=INK, anchor="lm")
        x += 150
    d.text((ML, y0 + 34), "点線は都市のルール④の高さ。手前の施設に隠れた所は、線だけ見えている。しまの箱は、まだ作っていない大結晶（30）。",
           font=font(15), fill=INK2, anchor="lm")
    d.text((ML, y0 + 58), "ギルドの 35 は塔の先（34）の上の避雷針、鐘楼の 40 は銅の尖り（39）の上の光の棒。",
           font=font(15), fill=INK2, anchor="lm")
    halo_text(d, (Q(0.5, 32)[0], Q(0.5, 32)[1]), "大結晶（まだ）", font(13), fill=INK2)
    im.save(path)
    return {f: int(prof[f].max()) for f in prof}


# ---------------------------------------------------------------- 3・4 で使う：中央広場から歩ける所
def ground_cells(cells):
    """柱ごとに、いちばん低い足もと（1階・地面）"""
    best = {}
    for (x, y, z) in cells:
        k = (x, z)
        if k not in best or y < best[k]:
            best[k] = y
    return best


def night_base(H, ST, base):
    rgb, has = shaded_rgb(H, ST)
    img = np.where(has[..., None], rgb, base)
    gray = img.mean(axis=2, keepdims=True)
    img = (img * 0.35 + gray * 0.65) * 0.28 + np.array([0.02, 0.03, 0.07])
    return img


UNBUILT_DARK = (58, 66, 98)
DARK_FLOOR = (227, 73, 72)
LIGHT_RAMP = [(70, 44, 18), (120, 70, 20), (176, 104, 26), (222, 142, 40), (246, 186, 74), (255, 226, 140),
              (255, 246, 214)]


def light_rgb(lv):
    t = (lv - 1) / 14.0 * (len(LIGHT_RAMP) - 1)
    k = min(int(t), len(LIGHT_RAMP) - 2)
    return mix(LIGHT_RAMP[k], LIGHT_RAMP[k + 1], t - k)


def draw_lights(cc, city, light, lamps, both, path):
    H, ST, OWN = top_layer(city)
    base, kind = base_layer(cc)
    img = night_base(H, ST, base)
    ground = ground_cells(both)
    dark_floor = 0
    for (x, z), y in ground.items():
        if not (X0 <= x <= X1 and Z0 <= z <= Z1):
            continue
        lv = light.get((x, y, z), 0)
        if lv >= 1:
            img[z - Z0, x - X0] = np.array(light_rgb(lv)) / 255.0
        elif (x, y - 1, z) in city.world:
            img[z - Z0, x - X0] = np.array(DARK_FLOOR) / 255.0       # 施設の床で明るさ 0（あれば目立たせる）
            dark_floor += 1
        else:
            img[z - Z0, x - X0] = np.array(UNBUILT_DARK) / 255.0
    im, d = canvas("夜の灯り：中央広場から歩ける所の明るさと、外の灯り",
                   "色のついた所は、中央広場の真ん中から跳ばずに行けて戻れる所（いちばん低い階）。明るいほど黄色。"
                   "\nまだ作っていない道と広場は、まだ灯りがないので暗い（道を作るときに⑦の灯りを置く）。")
    im.paste(to_image(img), (ML, MT))
    d = ImageDraw.Draw(im)
    marks = [("古代の結晶灯（点）", "crystal"), ("再建のランタン灯", "post"), ("壁のランタン", "wall"),
             ("柵から吊るすランタン", "wall")]
    for k, sym in marks:
        for (x, y, z) in lamps.get(k, []):
            if not (X0 <= x <= X1 and Z0 <= z <= Z1):
                continue
            cx, cy = PC(x, z)
            if sym == "crystal":
                d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=(236, 248, 255), outline=(120, 200, 235), width=2)
            elif sym == "post":
                d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(255, 170, 60), outline=(255, 236, 200), width=2)
            else:
                d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(255, 150, 40))
    for fac, (x, z) in FAC_LABELS.items():
        halo_text(d, PC(x, z), FAC_NAME.get(fac, fac), font(18, True), fill=(255, 255, 255), halo=(20, 24, 40))
    y0 = MT + MAP_H + 34
    f = font(15)
    x = ML
    d.text((x, y0), "明るさ", font=f, fill=INK, anchor="lm")
    for lv in range(1, 16):
        bx = x + 60 + (lv - 1) * 22
        d.rectangle((bx, y0 - 9, bx + 20, y0 + 9), fill=light_rgb(lv))
        if lv in (1, 7, 15):
            d.text((bx + 10, y0 + 22), str(lv), font=font(13), fill=INK2, anchor="mm")
    bx = x + 60 + 15 * 22 + 30
    d.rectangle((bx, y0 - 9, bx + 20, y0 + 9), fill=UNBUILT_DARK)
    d.text((bx + 28, y0), "まだ作っていない道と広場（灯りはまだない）", font=f, fill=INK, anchor="lm")
    bx2 = bx + 360
    d.rectangle((bx2, y0 - 9, bx2 + 20, y0 + 9), fill=DARK_FLOOR)
    d.text((bx2 + 28, y0), f"施設の床で明るさ 0：{dark_floor} か所", font=f, fill=INK, anchor="lm")
    y1 = y0 + 50
    d.ellipse((ML, y1 - 6, ML + 12, y1 + 6), fill=(236, 248, 255), outline=(120, 200, 235), width=2)
    d.text((ML + 20, y1), "古代の結晶灯（大通り・広場）", font=f, fill=INK, anchor="lm")
    d.ellipse((ML + 270, y1 - 5, ML + 280, y1 + 5), fill=(255, 170, 60), outline=(200, 160, 110), width=2)
    d.text((ML + 290, y1), "再建のランタン灯（環状路・路地・店先）", font=f, fill=INK, anchor="lm")
    d.ellipse((ML + 590, y1 - 3, ML + 596, y1 + 3), fill=(255, 150, 40))
    d.text((ML + 604, y1), "壁のランタン・柵から吊るすランタン", font=f, fill=INK, anchor="lm")
    north_and_scale(d, ML + MAP_W - 300, MT + MAP_H - 40, ink=(240, 240, 240), ink2=(220, 220, 220))
    im.save(path)
    return dark_floor


def draw_walk(cc, city, both, oneway, doors, goals, path):
    H, ST, OWN = top_layer(city)
    rgb, has = shaded_rgb(H, ST)
    base, kind = base_layer(cc)
    img = np.where(has[..., None], rgb, base)
    gray = img.mean(axis=2, keepdims=True)
    img = (img * 0.25 + gray * 0.75) * 0.45 + 0.55
    ok = ground_cells(both)
    ow = ground_cells(oneway)
    OK_RGB = np.array((27, 175, 122)) / 255.0
    OW_RGB = np.array((235, 104, 52)) / 255.0
    for (x, z) in ok:
        if X0 <= x <= X1 and Z0 <= z <= Z1:
            img[z - Z0, x - X0] = img[z - Z0, x - X0] * 0.35 + OK_RGB * 0.65
    for (x, z) in ow:
        if (x, z) not in ok and X0 <= x <= X1 and Z0 <= z <= Z1:
            img[z - Z0, x - X0] = OW_RGB
    im, d = canvas("つながる道：中央広場の真ん中から、跳ばずに行けて戻れる所",
                   "緑は行けて戻れる所（いちばん低い階）、橙は下りられるが跳ばないと戻れない所（釣り堀の土の岸。1段跳べば戻れる）。"
                   "\nまだ作っていない道と広場は、道の高さの地面として歩いた。黒い点は外へ開く扉、◇は PHASE 6〜11 の確かめで決めた行き先。")
    im.paste(to_image(img), (ML, MT))
    d = ImageDraw.Draw(im)
    for (x, y, z) in doors:
        cx, cy = PC(x, z)
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=INK)
    for (x, y, z) in goals:
        cx, cy = PC(x, z)
        d.polygon([(cx, cy - 5), (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], fill=(255, 255, 255), outline=INK)
    cx, cy = PC(0, 0)
    d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=(255, 255, 255), outline=INK, width=3)
    halo_text(d, (cx, cy - 22), "出発", font(16, True))
    fb = font(19, True)
    for fac, (x, z) in FAC_LABELS.items():
        label_box(d, PC(x, z), FAC_NAME.get(fac, fac), DISTRICT_RGB[FAC_DISTRICT[fac]], fb)
    y0 = MT + MAP_H + 34
    swatch(d, ML, y0, (27, 175, 122), "行けて戻れる所")
    swatch(d, ML + 200, y0, (235, 104, 52), "跳ばないと戻れない所（1段跳べば戻れる）")
    d.ellipse((ML + 560, y0 - 3, ML + 566, y0 + 3), fill=INK)
    d.text((ML + 576, y0), "外へ開く扉", font=font(15), fill=INK, anchor="lm")
    d.polygon([(ML + 700, y0 - 5), (ML + 705, y0), (ML + 700, y0 + 5), (ML + 695, y0)], fill=(255, 255, 255), outline=INK)
    d.text((ML + 714, y0), "PHASE 6〜11 の行き先", font=font(15), fill=INK, anchor="lm")
    north_and_scale(d, ML + MAP_W - 300, y0 + 40)
    im.save(path)


# ---------------------------------------------------------------- 5. 鳥瞰（光線で描く）
AERIAL_LABELS = {          # 名前を置く所（世界の x・z と、高さ）
    "ギルド": (6, -80, 22), "個人倉庫": (56, -70, 25), "武器工房": (92, -15, 27), "防具工房": (70, 14, 25),
    "装飾工房": (31, -15, 24), "商店街": (-11, 40, 27), "フリーマーケット": (-33, 76, 13), "結びの教会": (-86, -4, 45),
    "釣り堀": (-78, 44, 14), "カジノ": (28, 86, 29), "塔の入口": (0, -160, 53),
}


def render_aerial(cc, city, path, width=1600, height=900):
    from mapgen.render import Scene
    from mapgen.world import World
    xa, xb, ya, yb, za, zb = -136, 122, -12, 52, -180, 118
    w = World(xb - xa + 1, yb - ya + 1, zb - za + 1)
    for (x, y, z), st in city.world.items():
        if st[0] in ("air", "structure_void") or not (xa <= x <= xb and ya <= y <= yb and za <= z <= zb):
            continue
        w.blocks[(x - xa, y - ya, z - za)] = st
    # まだ作っていない道と広場は、平らな明るい灰色（本物の石畳と見分けるため）。城壁は描かない
    road = ("smooth_stone", ())
    for x in range(xa, xb + 1):
        for z in range(za, zb + 1):
            if (x, 0, z) in city.world:
                continue
            if cc.road_at(x, z) and math.hypot(x, z) < cc.WALL_IN:
                w.blocks[(x - xa, 0 - ya, z - za)] = road
    sc = Scene(w, ground_y=1 - ya)
    eye = (62 - xa, 165 - ya, 205 - za)
    target = (-14 - xa, 0 - ya, -16 - za)
    # 釣り堀の池と、城壁の外の湖は地面より低いので、そこだけは地面の平らな面の下も描く
    sunken = [(-95 - xa, 23 - za, -60 - xa, 66 - za), (-131 - xa, 48 - za, -84 - xa, 95 - za)]
    fov = 50.0
    im = sc.render(eye, target, width=width, height=height, fov=fov, fog=(240.0, 400.0, 0.3),
                   ground_fog=(240.0, 400.0, 0.35), sunken=sunken)
    # 施設の名前（世界の点を、絵の上の点に写す。render と同じカメラ）
    e = np.array(eye, dtype=np.float64) + np.array([xa, ya, za])
    tg = np.array(target, dtype=np.float64) + np.array([xa, ya, za])
    fwd = (tg - e) / np.linalg.norm(tg - e)
    right = np.cross(fwd, np.array([0.0, 1.0, 0.0]))
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    tan = math.tan(math.radians(fov) / 2.0)
    aspect = width / height

    def proj(x, y, z):
        v = np.array([x, y, z], dtype=np.float64) - e
        zc = v @ fwd
        return ((v @ right) / (zc * tan * aspect) + 1) / 2 * width, (1 - (v @ up) / (zc * tan)) / 2 * height

    d = ImageDraw.Draw(im)
    fb = font(max(14, width // 80), True)
    for fac, (x, z, y) in AERIAL_LABELS.items():
        px, py = proj(x + 0.5, y, z + 0.5)
        label_box(d, (px, py), FAC_NAME.get(fac, fac), DISTRICT_RGB[FAC_DISTRICT[fac]], fb)
    for name, (x, z) in (("中央広場", (0, 0)), ("湖", (-112, 76))):
        px, py = proj(x + 0.5, 3, z + 0.5)
        halo_text(d, (px, py), name, font(max(12, width // 100)), fill=INK2)
    im.save(path)


def draw_all(cc, cities, out_dir, aerial=False):
    city = cities["start"]
    os.makedirs(out_dir, exist_ok=True)
    draw_map(cc, city, os.path.join(out_dir, "city_map.png"))
    heights = draw_heights(cc, city, os.path.join(out_dir, "city_heights.png"))
    light = cc.compute_light(city)
    lamps = cc.lamp_kinds(city)
    start = (0, 1, 0)
    reach, _ = cc.walk(city, [start], cc.BOUNDS)
    back = cc.back_reach(city, start, cc.BOUNDS)
    both = {p for p in reach if p in back}
    oneway = {p for p in reach if p not in back}
    draw_lights(cc, city, light, lamps, both, os.path.join(out_dir, "city_lights.png"))
    doors = [p for (p, n, s, f) in cc.outside_doors(city)]
    goals = [p for g in cc.GOALS.values() for p in g.values()]
    draw_walk(cc, city, both, oneway, doors, goals, os.path.join(out_dir, "city_walk.png"))
    if aerial:
        render_aerial(cc, city, os.path.join(out_dir, "city_aerial.png"))
    return heights
