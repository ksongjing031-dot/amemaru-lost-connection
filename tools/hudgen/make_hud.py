"""AMEMARU: LOST CONNECTION の HUD テクスチャを作る。

出力: hud.png（256x256、GUIの1ピクセル＝画像の1ピクセル）
デザイン: 「結晶と糸」。濃い藍色の枠に、水色の結晶のふち。中央に六角の結晶（レベル）。
アトラスの位置は AmemaruHud.java の定数と一致させること。
"""
import math
import sys
from PIL import Image

W, H = 256, 256
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = c


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    return (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), c[3])


# ---------------------------------------------------------------- 色
OUTLINE = hexc("090C14")
BEVEL_LIGHT = hexc("7C95C0")
BEVEL_MID = hexc("4A5E86")
BEVEL_DARK = hexc("27324D")
INNER_EDGE = hexc("1E2840")
EMPTY_TOP = hexc("0A0F1C")
EMPTY = hexc("111830")
EMPTY_BOTTOM = hexc("161F3A")
CRYSTAL = hexc("9FE6FF")

# 1行ごとの色（上 → 下）。バーの中身は8行。
HP_ROWS = ["C4FFCF", "86F097", "52DC6E", "40CC5D", "33BA50", "26A345", "1D8C3A", "136A2B"]
HP_LOW_ROWS = ["FFC4C4", "FF8E8E", "F45E5E", "E64A4A", "D23B3B", "B92F2F", "992626", "741B1B"]
MP_ROWS = ["C8EBFF", "8ACFFF", "5AAEF8", "4A9CF0", "3B88E6", "2F72D2", "255DBA", "1A4590"]
GHOST_ROWS = ["FFFFFF", "FFFDF2", "FFF6DC", "FFF0CC", "FFE9BC", "FFE2AC", "F7D79A", "EBC985"]
EXP_ROWS = ["FFF0A6", "F7C24A", "C9861C"]

BAR_W, BAR_H = 80, 12


def bar_left(r, tip_left):
    """その行の左端（枠の外側）。tip_left なら左がとがる。"""
    if tip_left:
        return int(math.floor(abs(r - 5.5)))
    return 0


def bar_right(r, tip_left):
    if tip_left:
        return BAR_W - 1
    return BAR_W - 1 - int(math.floor(abs(r - 5.5)))


def bar_inside(r, x, tip_left):
    """中身（色が入るところ）か。行2〜9、枠と面取りの内側。"""
    if r < 2 or r > 9:
        return False
    return bar_left(r, tip_left) + 2 <= x <= bar_right(r, tip_left) - 2


def draw_bar_frame(ox, oy, tip_left):
    for r in range(BAR_H):
        xl, xr = bar_left(r, tip_left), bar_right(r, tip_left)
        for x in range(xl, xr + 1):
            if r == 0 or r == BAR_H - 1 or x == xl or x == xr:
                c = OUTLINE
            elif r == 1:
                # 中央の結晶に近いほど、水色の光
                dist = (xr - x) if tip_left else (x - xl)
                t = max(0.0, 1.0 - dist / 40.0)
                c = mix(BEVEL_LIGHT, hexc("B4ECFF"), t)
            elif r == BAR_H - 2:
                c = BEVEL_DARK
            elif x == xl + 1:
                c = BEVEL_LIGHT if r <= 5 else BEVEL_MID
            elif x == xr - 1:
                c = BEVEL_MID if r <= 5 else BEVEL_DARK
            else:
                # 空のところ：上は影、下に向かって少し明るい
                t = (r - 2) / 7.0
                c = EMPTY_TOP if r == 2 else mix(EMPTY, EMPTY_BOTTOM, t)
                # 目盛り（10% ごと）
                inner_l = bar_left(r, tip_left) + 2
                inner_r = bar_right(r, tip_left) - 2
                # 目盛りは四角い部分だけ
                rect_l = 7 if tip_left else 2
                rect_r = BAR_W - 3 if tip_left else BAR_W - 8
                span = rect_r - rect_l
                for k in range(1, 10):
                    tx = rect_l + round(span * k / 10)
                    if x == tx and 3 <= r <= 8:
                        c = hexc("1C2644")
            put(ox + x, oy + r, c)
    # 先端の結晶のキャップ（とがった部分の面取りを結晶の色に）
    for r in range(1, BAR_H - 1):
        xl, xr = bar_left(r, tip_left), bar_right(r, tip_left)
        if tip_left:
            x = xl + 1
            put(ox + x, oy + r, hexc("C6F1FF") if r <= 5 else hexc("4E9FDA"))
        else:
            x = xr - 1
            put(ox + x, oy + r, hexc("8FD8FF") if r <= 5 else hexc("2D6FB0"))
    # 結晶のきらめき（上の面取りに、水色の点）
    if tip_left:
        put(ox + 6, oy + 1, CRYSTAL)
        put(ox + 7, oy + 1, hexc("D6F6FF"))
        put(ox + BAR_W - 6, oy + 1, hexc("B8EEFF"))
    else:
        put(ox + BAR_W - 7, oy + 1, CRYSTAL)
        put(ox + BAR_W - 8, oy + 1, hexc("D6F6FF"))
        put(ox + 5, oy + 1, hexc("B8EEFF"))


def draw_bar_fill(ox, oy, tip_left, rows, sheen=True, alpha=255):
    for r in range(BAR_H):
        for x in range(BAR_W):
            if not bar_inside(r, x, tip_left):
                continue
            c = hexc(rows[r - 2], alpha)
            # 斜めのつや（糸のような細い光）
            if sheen and (x + r) % 12 in (0,) and 3 <= r <= 7:
                c = mix(c, hexc("FFFFFF", alpha), 0.18)
            # 左右の端を少し暗く（丸みを感じさせる）
            xl = bar_left(r, tip_left) + 2
            xr = bar_right(r, tip_left) - 2
            if x == xl or x == xr:
                c = shade(c, 0.86)
            put(ox + x, oy + r, c)


def draw_bar_glow(ox, oy, tip_left):
    """低HPのときに赤く光らせる、枠の形だけの白いマスク。"""
    for r in range(BAR_H):
        xl, xr = bar_left(r, tip_left), bar_right(r, tip_left)
        for x in range(xl, xr + 1):
            if r <= 1 or r >= BAR_H - 2 or x <= xl + 1 or x >= xr - 1:
                put(ox + x, oy + r, (255, 255, 255, 255))


# ---------------------------------------------------------------- 経験値のバー（182x5）
EXP_W, EXP_H = 182, 5


def exp_inside(r, x):
    return 1 <= r <= 3 and 1 <= x <= EXP_W - 2


def draw_exp_frame(ox, oy):
    for r in range(EXP_H):
        for x in range(EXP_W):
            corner = (r in (0, EXP_H - 1)) and (x in (0, EXP_W - 1))
            if corner:
                continue
            if r == 0 or r == EXP_H - 1 or x == 0 or x == EXP_W - 1:
                c = OUTLINE
            else:
                c = EMPTY_TOP if r == 1 else mix(EMPTY, EMPTY_BOTTOM, (r - 1) / 2)
                if (x - 1) % 18 == 0 and x not in (1, EXP_W - 2):
                    c = hexc("26314F")
            put(ox + x, oy + r, c)


def draw_exp_fill(ox, oy, rows, alpha=255):
    for r in range(EXP_H):
        for x in range(EXP_W):
            if exp_inside(r, x):
                c = hexc(rows[r - 1], alpha)
                if (x + r * 3) % 14 == 0 and r == 2:
                    c = mix(c, hexc("FFFFFF", alpha), 0.25)
                put(ox + x, oy + r, c)


# ---------------------------------------------------------------- 中央の結晶（24x26。上6ピクセルは冠の欠片）
GEM_W, GEM_H = 24, 20     # 六角の部分
GEM_TOP = 8               # 冠の高さ（六角はこの下から）
GEM_CANVAS_H = GEM_H + GEM_TOP


def gem_left(r):
    return int(round(5 * abs(r - 9.5) / 9.5))


def gem_right(r):
    return GEM_W - 1 - gem_left(r)


def gem_contains(r, x):
    return 0 <= r < GEM_H and gem_left(r) <= x <= gem_right(r)


# 冠の欠片（六角の上辺から上に伸びる、3本の結晶）。各行の (左端, 右端) を上から書く
SHARD_ROWS = {
    # 中央（とがった先から根元へ）
    "center": [(11, 12), (10, 13), (10, 13), (9, 14), (9, 14), (9, 14), (9, 14), (9, 14)],
    # 左（少し低い）
    "left": [None, None, None, (5, 6), (4, 7), (4, 7), (4, 7), (4, 8)],
    # 右（少し低い）
    "right": [None, None, None, (17, 18), (16, 19), (16, 19), (16, 19), (15, 19)],
}


def shard_pixels():
    """冠の欠片のピクセル：{(x, y): (どの欠片, 種類)}。y は 0..GEM_TOP-1。"""
    pts = {}
    for name, rows in SHARD_ROWS.items():
        filled = [(y, span) for y, span in enumerate(rows) if span]
        first_y = filled[0][0]
        for y, (x0, x1) in filled:
            for x in range(x0, x1 + 1):
                edge = x == x0 or x == x1 or y == first_y
                pts[(x, y)] = (name, "edge" if edge else "body")
    return pts


def gem_shape_contains(cy, x):
    """キャンバス座標で、冠を含む結晶の形の中か。"""
    if cy >= GEM_TOP:
        return gem_contains(cy - GEM_TOP, x)
    return (x, cy) in shard_pixels()


def draw_gem(ox, oy, glow=False):
    # 冠の欠片
    for (x, y), (name, kind) in shard_pixels().items():
        if kind == "edge":
            c = OUTLINE
        else:
            # 欠片ごとの中心より左は明るく、右は暗く（左上から光が当たる）
            mid = {"center": 11.5, "left": 6.0, "right": 17.5}[name]
            light = x < mid
            c = hexc(("F0FCFF" if glow else "CFF3FF") if light else ("94DAFF" if glow else "5AA9E6"))
        put(ox + x, oy + y, c)
    # 六角
    for r in range(GEM_H):
        xl, xr = gem_left(r), gem_right(r)
        y = oy + GEM_TOP + r
        for x in range(xl, xr + 1):
            if r == 0 or r == GEM_H - 1 or x == xl or x == xr:
                c = OUTLINE
            else:
                d_top = r - 1
                d_bot = GEM_H - 2 - r
                d_l = x - (xl + 1)
                d_r = (xr - 1) - x
                m = min(d_top, d_bot, d_l, d_r)
                if m <= 1:
                    upper = r <= 9
                    if d_top == m:
                        base = "E2F8FF" if glow else "BFEFFF"
                    elif d_bot == m:
                        base = "5DB0F0" if glow else "2E6FB2"
                    elif d_l == m:
                        base = ("C9F2FF" if glow else "9EDFFF") if upper else ("8FD3FF" if glow else "4E9FDA")
                    else:
                        base = ("A6E4FF" if glow else "6FC0F0") if upper else ("6BBDF5" if glow else "2D6FB0")
                    c = hexc(base)
                    if m == 1:
                        c = shade(c, 0.74)
                else:
                    # 面：上ほど明るい藍色。内側の六角のふちに、細い影
                    t = (r - 3) / (GEM_H - 6)
                    c = mix(hexc("21375F" if glow else "1C2D52"), hexc("0E172E"), max(0.0, min(1.0, t)))
                    if m == 2:
                        c = mix(c, hexc("0A1124"), 0.55)
            put(ox + x, y, c)
    # きらめき
    for (x, y, col) in [(8, 1, "FFFFFF"), (9, 1, "FFFFFF"), (8, 2, "E8FBFF"), (15, 2, "D6F6FF")]:
        put(ox + x, oy + GEM_TOP + y, hexc(col))
    put(ox + 11, oy + 2, hexc("FFFFFF"))


def draw_gem_glow(ox, oy):
    """結晶のまわりの光（28x30。結晶より上下左右に2ピクセル大きい）。"""
    for y in range(GEM_CANVAS_H + 4):
        for x in range(GEM_W + 4):
            gx, gy = x - 2, y - 2
            if gem_shape_contains(gy, gx):
                continue
            best = 9
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    if gem_shape_contains(gy + dy, gx + dx):
                        best = min(best, max(abs(dx), abs(dy)))
            if best == 1:
                put(ox + x, oy + y, hexc("A8EBFF", 170))
            elif best == 2:
                put(ox + x, oy + y, hexc("7FD8FF", 70))


def draw_gem_flash(ox, oy):
    for y in range(GEM_CANVAS_H):
        for x in range(GEM_W):
            if gem_shape_contains(y, x):
                put(ox + x, oy + y, (255, 255, 255, 255))


def draw_diamond(ox, oy, size=9):
    """パネルの角に置く、小さな結晶。"""
    c = size // 2
    for y in range(size):
        for x in range(size):
            d = abs(x - c) + abs(y - c)
            if d == c:
                put(ox + x, oy + y, OUTLINE)
            elif d < c:
                if d >= c - 1:
                    col = "BFEFFF" if (y < c or x < c) else "2E6FB2"
                else:
                    col = "6FC0F0" if y <= c else "3E86CC"
                put(ox + x, oy + y, hexc(col))
    put(ox + c - 1, oy + c - 1, hexc("FFFFFF"))


# ---------------------------------------------------------------- ホットバー（182x22）・選択枠（24x24）・オフハンド（22x22）
HB_W, HB_H = 182, 22
WELL = hexc("10172A")
WELL_DARK = hexc("0A0F1C")
WELL_LIGHT = hexc("2C3A5C")
HB_BG = hexc("161E33")


def draw_slot_well(ox, oy):
    """アイテムを置くくぼみ（18x18）。左上が暗く、右下が明るい。"""
    for y in range(18):
        for x in range(18):
            if x == 0 or y == 0:
                c = WELL_DARK
            elif x == 17 or y == 17:
                c = WELL_LIGHT
            else:
                c = WELL
            put(ox + x, oy + y, c)


def draw_hotbar(ox, oy):
    for y in range(HB_H):
        for x in range(HB_W):
            corner = (x in (0, HB_W - 1)) and (y in (0, HB_H - 1))
            if corner:
                continue
            if x == 0 or y == 0 or x == HB_W - 1 or y == HB_H - 1:
                c = OUTLINE
            elif y == 1:
                dist = abs(x - HB_W / 2)
                c = mix(hexc("9FDDF5"), BEVEL_LIGHT, min(1.0, dist / 70.0))
            elif y == HB_H - 2:
                c = BEVEL_DARK
            elif x == 1:
                c = BEVEL_MID
            elif x == HB_W - 2:
                c = BEVEL_DARK
            else:
                c = HB_BG
            put(ox + x, oy + y, c)
    for k in range(9):
        draw_slot_well(ox + 2 + 20 * k, oy + 2)
        if k < 8:
            # スロットの間の、糸の結び目のような小さな点
            put(ox + 21 + 20 * k, oy + 10, hexc("5E86B8"))
            put(ox + 21 + 20 * k, oy + 11, hexc("3C5A86"))


def draw_selection(ox, oy):
    """選んでいるスロットの枠（24x24）。明るい結晶のふちと、四隅のきらめき。"""
    S = 24
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            corner = (x in (0, S - 1)) and (y in (0, S - 1))
            if corner:
                continue
            if edge == 0:
                c = OUTLINE
            elif edge == 1:
                c = hexc("D8F6FF") if (x + y) < S else hexc("7CC8F2")
            elif edge == 2:
                c = hexc("6FC0F0", 200)
            elif edge == 3:
                c = hexc("8FD8FF", 70)
            else:
                continue
            put(ox + x, oy + y, c)
    for (x, y) in [(1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)]:
        put(ox + x, oy + y, hexc("FFFFFF"))


def draw_offhand(ox, oy):
    S = 22
    for y in range(S):
        for x in range(S):
            corner = (x in (0, S - 1)) and (y in (0, S - 1))
            if corner:
                continue
            if x == 0 or y == 0 or x == S - 1 or y == S - 1:
                c = OUTLINE
            elif y == 1:
                c = BEVEL_LIGHT
            elif y == S - 2 or x == S - 2:
                c = BEVEL_DARK
            elif x == 1:
                c = BEVEL_MID
            else:
                c = HB_BG
            put(ox + x, oy + y, c)
    draw_slot_well(ox + 2, oy + 2)


# ---------------------------------------------------------------- 配置（AmemaruHud.java と同じ）
LAYOUT = {
    "hp_frame": (0, 0),
    "mp_frame": (0, 12),
    "hp_fill": (0, 24),
    "hp_fill_low": (0, 36),
    "mp_fill": (0, 48),
    "hp_ghost": (0, 60),
    "mp_ghost": (0, 72),
    "hp_glow": (0, 84),
    "mp_glow": (80, 84),
    "exp_frame": (0, 96),
    "exp_fill": (0, 101),
    "exp_flash": (0, 106),
    "gem": (96, 0),
    "gem_lit": (120, 0),
    "gem_flash": (144, 0),
    "gem_glow": (96, 28),
    "diamond": (168, 0),
    "hotbar": (0, 112),
    "selection": (184, 112),
    "offhand": (208, 112),
}

draw_bar_frame(*LAYOUT["hp_frame"], tip_left=True)
draw_bar_frame(*LAYOUT["mp_frame"], tip_left=False)
draw_bar_fill(*LAYOUT["hp_fill"], tip_left=True, rows=HP_ROWS)
draw_bar_fill(*LAYOUT["hp_fill_low"], tip_left=True, rows=HP_LOW_ROWS)
draw_bar_fill(*LAYOUT["mp_fill"], tip_left=False, rows=MP_ROWS)
draw_bar_fill(*LAYOUT["hp_ghost"], tip_left=True, rows=GHOST_ROWS, sheen=False, alpha=235)
draw_bar_fill(*LAYOUT["mp_ghost"], tip_left=False, rows=GHOST_ROWS, sheen=False, alpha=235)
draw_bar_glow(*LAYOUT["hp_glow"], tip_left=True)
draw_bar_glow(*LAYOUT["mp_glow"], tip_left=False)
draw_exp_frame(*LAYOUT["exp_frame"])
draw_exp_fill(*LAYOUT["exp_fill"], rows=EXP_ROWS)
draw_exp_fill(*LAYOUT["exp_flash"], rows=["FFFFFF", "FFFFFF", "FFFFFF"])
draw_gem(*LAYOUT["gem"])
draw_gem(*LAYOUT["gem_lit"], glow=True)
draw_gem_flash(*LAYOUT["gem_flash"])
draw_gem_glow(*LAYOUT["gem_glow"])
draw_diamond(*LAYOUT["diamond"])
draw_hotbar(*LAYOUT["hotbar"])
draw_selection(*LAYOUT["selection"])
draw_offhand(*LAYOUT["offhand"])

out = sys.argv[1] if len(sys.argv) > 1 else "hud.png"
img.save(out)
print("saved", out)
