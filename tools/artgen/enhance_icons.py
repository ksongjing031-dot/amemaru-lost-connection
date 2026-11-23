"""持ち物の絵（16×16）を、32×32 の細かい絵にする（11月23日）。

「アイテムの絵がのっぺりしている。デザインや色合いを細かくして質を上げる」のご指示で作った。元の16×16の絵の形と色は
そのまま使い、次のように描き足す：

  1. 2倍にする（Scale2x：ななめの線をなめらかに）。ふちの線は1ピクセルに細くする（細かく見える）
  2. 色ごとのかたまりに、丸みの陰影をつける（左上から光。かたまりのふちからの距離で、ふくらんで見える）
  3. 色の段は、暗い所は青みへ・明るい所は黄みへ少し色相をずらす（のっぺりした灰色にしない）
  4. 材質ごとの描き込み：金属＝光の筋と点・石＝細かいまだら・木＝木目・布や毛＝織り目・結晶や硝子＝面の線ときらめき・
     液＝縦のグラデーションと映り込み・紙＝繊維・葉＝葉脈
  5. ふちの線は、となりの色を暗くした色（光の側は少し明るく、影の側は濃く）。真っ黒の線にしない

材質は色から決め、絵ごとに上書きできる（HINTS）。もとの16×16の絵を作るスクリプトを流し直したら、このスクリプトも流す
（もう32×32の絵は飛ばす。16×16に戻った絵だけ作り直す）。

  python3 tools/artgen/enhance_icons.py [repo] [--sheet 確認画像.png] [--only 名前,名前] [--force]
"""
import colorsys
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

ITEM_DIR = "src/main/resources/assets/amemaru/textures/item"
# 3Dの武器の手に持つ絵（*_model）・光る所（*_glowmask）は16×16の持ち物の絵ではない
SKIP_SUFFIX = ("_model", "_glowmask")
# 絵のまま使う物（細かくしない）：GeckoLib の武器の絵（持ち物の絵は *_icon）など
SKIP = {"bee_crystal_staff", "elder_branch_staff"}

# 細くても Scale2x でなめらかにする絵
THICK = set()
# 太く見えても、そのまま2倍にする絵（先の小さな飾りが Scale2x でつぶれる杖・お守り）
FORCE_THIN = {"apprentice_bell_staff", "ancient_stone_bell_staff", "apprentice_staff", "ancient_stone_staff",
              "bee_crystal_staff_icon", "waystone_charm", "grassland_necklace", "keepsake_doran", "keepsake_ernesto",
              "elder_branch_staff_icon", "knot_lantern",
              "ryumon_staff", "ryumon_bell_staff"}
# 茶色の所を「木」として描く絵（ほかの茶色は革）
WOODEN = {"training_dummy", "apprentice_staff", "apprentice_bell_staff", "apprentice_bow", "ancient_stone_bow",
          "elder_branch_staff_icon",
          "apprentice_sword", "apprentice_greatsword", "keepsake_doran", "soft_horn", "market_notebook", "ryumon_bow"}

# 絵ごとの材質の上書き：{絵の名前: {"#rrggbb"（元の色）または "*": 材質}}
HINTS = {
    "ancient_stone": {"*": "stone"},
    "moss_iron": {"*": "metal"},
    "polishing_powder": {"*": "powder"},
    "tsunomofu_fluff": {"*": "fluff"},
    "soft_horn": {"*": "bone"},
    "grassland_horn": {"*": "bone"},
    "chipped_fang": {"*": "bone"},
    "binding_thread": {"*": "gold"},
    "blank_oath": {"*": "paper"},
    "record_book": {"*": "leather"},
    "market_notebook": {"*": "leather"},
    "training_dummy": {"*": "wood"},
    "keepsake_doran": {"*": "metal"},
    "ishikure_core": {"*": "crystal"},
    "ishikure_bracelet": {"*": "stone"},
    "light_shard": {"*": "crystal"},
    "clear_echo": {"*": "crystal"},
    "echo_fragment": {"*": "crystal"},
    "wavering_crystal": {"*": "crystal"},
    "crystal_shard": {"*": "crystal"},
    "bee_stinger": {"*": "crystal"},
}
# 流紋シリーズ（Phase 8 その2。ryumon_icons.py の色）：青銅と緑青は金属、流水の結晶は結晶、白・生成り・黒・緑・水色の布は布、
# わらは木、ナマズのひげは革（名前が ryumon_ で始まる絵すべてに使う）
RYUMON_HINT = {
    **{c: "metal" for c in ("#edcb8c", "#d6a962", "#b98948", "#966a35", "#704d26", "#6aaf93", "#3b7563",
                            "#5d6067", "#35373c", "#232327")},
    **{c: "crystal" for c in ("#d8fbff", "#93e2ec", "#2fa6be", "#17627a")},
    **{c: "cloth" for c in ("#fbfbfa", "#e6e7e8", "#bdc0c5", "#86d3da", "#568157", "#355739", "#1f3424",
                            "#434a58", "#252932", "#131519", "#f3efe2", "#d8d1ba", "#aba38c")},
    "#dcc988": "wood", "#b59c52": "wood", "#3c342c": "leather",
}


# ---------------------------------------------------------------- 色
def to_hls(c):
    return colorsys.rgb_to_hls(c[0] / 255.0, c[1] / 255.0, c[2] / 255.0)


def from_hls(h, l, s):
    r, g, b = colorsys.hls_to_rgb(h % 1.0, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))


def shift(c, k):
    """k 段だけ明るく（正）・暗く（負）。暗い所は青みへ、明るい所は黄みへ色相をずらす（色の強い所ほど少なく）"""
    h, l, s = to_hls(c)
    if k == 0:
        return tuple(c)
    step = 0.075
    nl = l + k * step * (1.0 - 0.35 * abs(l - 0.5))
    # 色相：暗く → 青（0.62）へ、明るく → 黄（0.14）へ。灰色（s 小）でも少しだけ色をつける
    target = 0.62 if k < 0 else 0.14
    dh = ((target - h + 0.5) % 1.0) - 0.5
    amount = min(1.0, 0.045 * abs(k)) * (0.6 if s > 0.25 else 1.0)
    nh = h + dh * amount
    ns = s * (1.0 + 0.08 * (-k if k < 0 else -0.6 * k)) + (0.03 * abs(k) if s < 0.12 else 0.0)
    return from_hls(nh, nl, ns)


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0


def material_of(c, hint):
    if hint:
        return hint
    h, l, s = to_hls(c)
    deg = h * 360
    if l > 0.86 and s < 0.25:
        return "paper"
    if s < 0.16:
        return "metal" if l > 0.25 else "stone"
    if 20 <= deg < 48 and s > 0.3 and l < 0.6:
        return "wood?"
    if 40 <= deg < 62 and s > 0.55 and l >= 0.45:
        return "gold"
    if 160 <= deg < 300 and l > 0.6:
        return "crystal"
    if 80 <= deg < 160 and s > 0.3:
        return "leaf"
    if deg < 20 or deg >= 330:
        return "cloth" if l < 0.5 else "liquid"
    return "cloth"


# ---------------------------------------------------------------- 形
def small_parts(a):
    """同じ色の小さなかたまり（16×16で5マス以下。十字・目・小さな宝石など）。Scale2x で形が崩れないように守る"""
    h, w = a.shape[:2]
    key = (a[..., 0].astype(np.int64) << 24) | (a[..., 1].astype(np.int64) << 16) | (a[..., 2].astype(np.int64) << 8) | a[..., 3]
    small = np.zeros((h, w), dtype=bool)
    for k in np.unique(key):
        if (k & 255) == 0:
            continue
        lab, n = ndimage.label(key == k)
        for i in range(1, n + 1):
            m = lab == i
            if m.sum() <= 5:
                small |= m
    return small


def scale2x(img):
    """Scale2x（EPX）：ななめの段をなめらかにして2倍にする。小さなかたまりのまわりは、そのまま2倍（形を守る）"""
    a = np.asarray(img).astype(np.int64)
    h, w = a.shape[:2]
    small = small_parts(np.asarray(img))
    guard = ndimage.binary_dilation(small, structure=np.ones((3, 3), dtype=bool))
    key = (a[..., 0] << 24) | (a[..., 1] << 16) | (a[..., 2] << 8) | a[..., 3]
    pad = np.pad(key, 1, mode="edge")
    P = key
    A = pad[0:h, 1:w + 1]
    B = pad[1:h + 1, 2:w + 2]
    C = pad[1:h + 1, 0:w]
    D = pad[2:h + 2, 1:w + 1]
    out = np.zeros((h * 2, w * 2), dtype=np.int64)
    out[0::2, 0::2] = np.where((C == A) & (C != D) & (A != B), A, P)
    out[0::2, 1::2] = np.where((A == B) & (A != C) & (B != D), B, P)
    out[1::2, 0::2] = np.where((D == C) & (D != B) & (C != A), C, P)
    out[1::2, 1::2] = np.where((B == D) & (B != A) & (D != C), D, P)
    near = np.repeat(np.repeat(P, 2, axis=0), 2, axis=1)
    g2 = np.repeat(np.repeat(guard, 2, axis=0), 2, axis=1)
    out = np.where(g2, near, out)
    rgba = np.stack([(out >> 24) & 255, (out >> 16) & 255, (out >> 8) & 255, out & 255], axis=-1).astype(np.uint8)
    return rgba


def is_outline_color(c):
    return lum(c) < 0.2


def is_thin(a16):
    """細い絵（剣・杖・弓・針など）か：ほとんどのマスが、ふちから1マス以内"""
    m = a16[..., 3] > 0
    d = ndimage.distance_transform_edt(np.pad(m, 1))[1:-1, 1:-1]
    return m.sum() > 0 and (d[m] <= 1.5).mean() > 0.8


def enhance(src, name):
    src = src.convert("RGBA")
    a16 = np.asarray(src)
    hint_map = HINTS.get(name) or (RYUMON_HINT if name.startswith("ryumon_") else {})
    thin = (is_thin(a16) or name in FORCE_THIN) and name not in THICK
    if thin:
        big = np.repeat(np.repeat(a16, 2, axis=0), 2, axis=1)
    else:
        big = scale2x(src)
    H, W = big.shape[:2]
    alpha = big[..., 3] > 0
    # 元のふちの線（暗くて、透明なマスか絵の外ととなり合う16×16のマス）を、32×32で見分ける
    a16a = a16[..., 3] > 0
    pad16 = np.pad(a16a, 1, constant_values=False)
    edge16 = a16a & ~(pad16[:-2, 1:-1] & pad16[2:, 1:-1] & pad16[1:-1, :-2] & pad16[1:-1, 2:])
    dark16 = np.array([[is_outline_color(tuple(a16[y, x, :3])) for x in range(16)] for y in range(16)])
    outline_colors = {tuple(a16[y, x, :3]) for y in range(16) for x in range(16) if a16a[y, x] and dark16[y, x] and edge16[y, x]}
    rgb = big[..., :3].astype(np.int32)
    is_out = alpha & np.array([[tuple(rgb[y, x]) in outline_colors for x in range(W)] for y in range(H)])
    # 外のふち（透明とのさかい）に接していない線のマスは、内の色にする（線を1ピクセルに）
    padA = np.pad(alpha, 1, constant_values=False)
    touches_outside = ~(padA[:-2, 1:-1] & padA[2:, 1:-1] & padA[1:-1, :-2] & padA[1:-1, 2:])
    padA8 = padA
    touches_outside8 = touches_outside | ~(padA8[:-2, :-2] & padA8[:-2, 2:] & padA8[2:, :-2] & padA8[2:, 2:])
    # 細い絵は線を細くしない（線で形を分けているので、細くすると部品がつながって見える）
    inner_line = is_out & ~touches_outside8 & (not thin)
    interior = alpha & ~is_out
    # 内の線を、となりの内の色で埋める（何度か広げる）
    fill = rgb.copy()
    known = interior.copy()
    for _ in range(3):
        if not (inner_line & ~known).any():
            break
        new_known = known.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh_known = np.roll(np.roll(known, dy, axis=0), dx, axis=1)
            sh_rgb = np.roll(np.roll(fill, dy, axis=0), dx, axis=1)
            take = inner_line & ~new_known & sh_known
            fill[take] = sh_rgb[take]
            new_known |= take
        known = new_known
    is_out = is_out & ~(inner_line & known)
    interior = alpha & ~is_out
    rgb = fill
    # 色のかたまり（元の色ごと）
    keys = (rgb[..., 0] << 16) | (rgb[..., 1] << 8) | rgb[..., 2]
    keys = np.where(interior, keys, -1)
    # 陰影：絵全体のふちからの距離（大きなふくらみ）＋ 色のかたまりのふちからの距離（小さなふくらみ）
    d_all = ndimage.distance_transform_edt(interior)
    d_loc = np.zeros_like(d_all)
    for k in np.unique(keys[keys >= 0]):
        m = keys == k
        d_loc[m] = ndimage.distance_transform_edt(m)[m]
    height = np.sqrt(np.minimum(d_all, 7.0)) * 0.9 + np.sqrt(np.minimum(d_loc, 4.0)) * 0.7
    height = ndimage.gaussian_filter(height, 0.9)
    gy, gx = np.gradient(height)
    nx, ny, nz = -gx, -gy, np.full_like(gx, 0.9)
    norm = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / norm, ny / norm, nz / norm
    L = np.array([-0.55, -0.62, 0.56])
    L = L / np.linalg.norm(L)
    diffuse = nx * L[0] + ny * L[1] + nz * L[2]
    # 左上ほど明るく、右下ほど暗く（絵全体の光の向き）
    yy, xx = np.mgrid[0:H, 0:W]
    ys, xs = np.nonzero(alpha)
    cy, cx = (ys.min() + ys.max()) / 2.0, (xs.min() + xs.max()) / 2.0
    span = max(ys.max() - ys.min(), xs.max() - xs.min(), 1) / 2.0
    tilt = -((xx - cx) + (yy - cy)) / (2.0 * span)
    shade_v = (diffuse - 0.62) * 3.2 + tilt * 0.9
    out = np.zeros((H, W, 4), dtype=np.uint8)
    out[..., 3] = big[..., 3]
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
    cache = {}

    def tone(c, k):
        key = (c, k)
        if key not in cache:
            cache[key] = shift(c, k)
        return cache[key]

    mats = {}
    for y in range(H):
        for x in range(W):
            if not interior[y, x]:
                continue
            c = tuple(int(v) for v in rgb[y, x])
            hx = "#%02x%02x%02x" % c
            mat = mats.get(c)
            if mat is None:
                mat = material_of(c, hint_map.get(hx) or hint_map.get("*"))
                if mat == "wood?":
                    mat = "wood" if name in WOODEN else "leather"
                mats[c] = mat
            v = shade_v[y, x]
            k = v
            n = 0.0
            if mat == "metal":
                # 光の筋（左上から右下へのななめ）と、細かい傷
                band = math.sin((x + y) * 0.55 + 0.6)
                k += 0.55 * max(0.0, band) - 0.15
                n = rng.uniform(-0.25, 0.25)
            elif mat == "stone":
                n = rng.uniform(-0.7, 0.7) if rng.random() < 0.35 else 0.0
            elif mat == "wood":
                # 木目：細い筋を、ところどころに（強すぎると縞に見える）
                g = math.sin((x * 0.35 + y * 1.0) * 1.3 + 2.0 * math.sin(x * 0.21 + y * 0.13))
                k += 0.5 if g > 0.92 else (-0.45 if g < -0.95 else 0.0)
            elif mat in ("cloth", "leather", "fluff"):
                k += (0.25 if (x + y) % 2 == 0 else -0.25) * (0.5 if mat == "cloth" else 0.35)
                if mat == "fluff":
                    n = rng.uniform(-0.5, 0.6)
            elif mat == "crystal":
                # 面の線：ななめの明るい面と暗い面
                facet = ((x * 3 + y * 2) // 9) % 3
                k += (0.6, -0.1, -0.5)[facet] * 0.8
            elif mat == "liquid":
                k += -((y - cy) / (span + 1e-6)) * 0.6
            elif mat == "paper":
                n = rng.uniform(-0.3, 0.3) if rng.random() < 0.3 else 0.0
            elif mat == "leaf":
                k += 0.4 if (x - y) % 5 == 0 else 0.0
            elif mat == "powder":
                n = rng.uniform(-0.8, 0.8) if rng.random() < 0.5 else 0.0
            elif mat == "gold":
                band = math.sin((x + y) * 0.7)
                k += 0.5 * max(0.0, band)
            elif mat == "bone":
                k += 0.3 * math.sin(y * 1.3 + x * 0.2) * 0.5
            k = int(round(max(-3.0, min(2.6, k + n))))
            out[y, x, :3] = tone(c, k)
            # きらめき（金属・金・結晶・液）：いちばん光の当たる所に、白に近い点
            if mat in ("metal", "gold", "crystal", "liquid") and diffuse[y, x] > 0.93 and tilt[y, x] > 0.15:
                out[y, x, :3] = tone(c, 4)
    # ふちの線：となりの内の色を暗くした色（光の側は少し明るい）
    for y in range(H):
        for x in range(W):
            if not (alpha[y, x] and is_out[y, x]):
                continue
            best = None
            for r in (1, 2):
                for dy in range(-r, r + 1):
                    for dx in range(-r, r + 1):
                        yy2, xx2 = y + dy, x + dx
                        if 0 <= yy2 < H and 0 <= xx2 < W and interior[yy2, xx2]:
                            best = tuple(int(v) for v in rgb[yy2, xx2])
                            break
                    if best:
                        break
                if best:
                    break
            if best is None:
                out[y, x, :3] = rgb[y, x]
                continue
            lit = (x - cx) + (y - cy) < 0
            c = shift(best, -4 if lit else -6)
            # 線は暗く保つ（もとの線の色と混ぜる。持ち物の枠の灰色の上でも形が分かるように）。細い絵は、線で形を見せるので、さらに暗く
            o = tuple(int(v) for v in rgb[y, x])
            t = (0.3 if lit else 0.16) * (0.4 if thin else 1.0)
            out[y, x, :3] = tuple(int(round(o[i] * (1 - t) + c[i] * t)) for i in range(3))
    return Image.fromarray(out, "RGBA")


def targets(repo, only=None, force=False):
    base = os.path.join(repo, ITEM_DIR)
    out = []
    for fn in sorted(os.listdir(base)):
        if not fn.endswith(".png"):
            continue
        name = fn[:-4]
        if name.endswith(SKIP_SUFFIX) or name in SKIP:
            continue
        if only and name not in only:
            continue
        if os.path.exists(os.path.join(base, fn + ".mcmeta")):
            continue
        img = Image.open(os.path.join(base, fn))
        if img.size != (16, 16) and not force:
            continue
        out.append((name, os.path.join(base, fn)))
    return out


def sheet(pairs, path, scale=4):
    font = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 11)
    cols = 6
    cw = 32 * scale * 2 + 24
    ch = 32 * scale + 26
    rows = (len(pairs) + cols - 1) // cols
    img = Image.new("RGB", (cols * cw + 8, rows * ch + 8), (139, 139, 139))
    d = ImageDraw.Draw(img)
    for i, (name, old, new) in enumerate(pairs):
        x = 8 + (i % cols) * cw
        y = 8 + (i // cols) * ch
        o = old.resize((32 * scale, 32 * scale), Image.NEAREST)
        n = new.resize((32 * scale, 32 * scale), Image.NEAREST)
        img.paste(o, (x, y), o)
        img.paste(n, (x + 32 * scale + 4, y), n)
        d.text((x, y + 32 * scale + 2), name[:28], fill=(20, 20, 20), font=font)
    img.save(path)


def main():
    args = [a for a in sys.argv[1:]]
    repo = args[0] if args and not args[0].startswith("--") else "."
    only = None
    if "--only" in args:
        only = set(args[args.index("--only") + 1].split(","))
    force = "--force" in args
    sheet_path = args[args.index("--sheet") + 1] if "--sheet" in args else None
    dry = "--dry" in args
    pairs = []
    for name, path in targets(repo, only, force):
        old = Image.open(path).convert("RGBA")
        if old.size != (16, 16):
            continue
        new = enhance(old, name)
        pairs.append((name, old, new))
        if not dry:
            new.save(path)
    if sheet_path:
        sheet(pairs, sheet_path)
    print("32×32 にした絵:", len(pairs), "（書き出し" + ("なし" if dry else "済み") + "）")


if __name__ == "__main__":
    main()
