"""壁の立面を、文字の絵で描いて置く道具。

外から壁を見た絵を、奥行きの層ごとに文字で書く。層 0 が基準の面（1階の石の面）、+1 が1つ外、-1 が1つ奥。
行は上から下、文字は外から見て左から右。空白は「何もしない」、'.' は「空ける」。
階段・トラップドアの向きは、壁の向き（外向き・内向き・左・右）から決める。
"""
from .world import (AIR, CCW, CW, DIRS, OPPOSITE, block, door, fence, lantern, leaves, log, pane, slab, stairs, trapdoor,
                    wall)


class Orient:
    """壁の向き。out は外向き、inn は内向き、right・left は外から見た右・左。"""

    def __init__(self, face):
        self.out = face
        self.inn = OPPOSITE[face]
        self.right = CCW[face]
        self.left = CW[face]
        self.along = "x" if face in ("north", "south") else "z"
        self.across = "z" if self.along == "x" else "x"


def origin(face, a_min, a_max, plane):
    """外から見て左はしのマス（層 0）の (x, z)。a_min..a_max は壁に沿う座標の範囲、plane は面の座標。"""
    if face == "south":
        return a_min, plane
    if face == "north":
        return a_max, plane
    if face == "east":
        return plane, a_max
    return plane, a_min


def cell(face, x0, z0, i, depth):
    rx, _, rz = DIRS[CCW[face]]
    ox, _, oz = DIRS[face]
    return x0 + rx * i + ox * depth, z0 + rz * i + oz * depth


def legend(pal):
    """文字 → ブロック（向きを受け取る関数）。"""
    rng = pal.rng

    def stone(O):
        r = rng.random()
        if r < 0.70:
            return block("stone_bricks")
        if r < 0.84:
            return block("cracked_stone_bricks")
        if r < 0.94:
            return block("mossy_stone_bricks")
        return block("andesite")

    def plaster(O):
        return block(pal.plaster)

    return {
        ".": lambda O: AIR,
        "P": plaster,
        "S": stone,
        "s": lambda O: block("stone_bricks"),
        "c": lambda O: block("chiseled_stone_bricks"),
        "A": lambda O: block("polished_andesite"),
        "K": lambda O: block(pal.frame + "_planks"),
        "D": lambda O: block(pal.trim + "_planks"),
        "|": lambda O: log(pal.post, "y", stripped=True),
        "I": lambda O: log(pal.trim, "y"),
        "=": lambda O: log(pal.post, O.along, stripped=True),
        "-": lambda O: log(pal.trim, O.along),
        "+": lambda O: log(pal.post, O.across, stripped=True),
        "g": lambda O: pane(),
        "(": lambda O: stairs("stone_brick", O.left, "top"),
        ")": lambda O: stairs("stone_brick", O.right, "top"),
        "[": lambda O: stairs(pal.frame, O.left, "top"),
        "]": lambda O: stairs(pal.frame, O.right, "top"),
        "{": lambda O: stairs(pal.trim, O.left, "top"),
        "}": lambda O: stairs(pal.trim, O.right, "top"),
        "<": lambda O: stairs(pal.trim, O.left, "bottom"),
        ">": lambda O: stairs(pal.trim, O.right, "bottom"),
        "v": lambda O: stairs(pal.frame, O.inn, "top"),        # 持ち送り（壁から外へ）
        "V": lambda O: stairs(pal.trim, O.inn, "top"),
        "w": lambda O: stairs("stone_brick", O.inn, "top"),
        "_": lambda O: stairs(pal.frame, O.inn, "bottom"),     # 段（壁の方が高い）
        "^": lambda O: stairs("stone_brick", O.inn, "bottom"),
        "n": lambda O: trapdoor(pal.trim, O.out, "top", False),
        "u": lambda O: trapdoor(pal.trim, O.out, "bottom", False),
        "l": lambda O: trapdoor(pal.trim, O.left, "bottom", True),    # 窓の左の縁（板は右側）
        "r": lambda O: trapdoor(pal.trim, O.right, "bottom", True),   # 窓の右の縁（板は左側）
        "h": lambda O: trapdoor(pal.frame, O.out, "bottom", True),    # 壁に沿う板（雨戸・花箱の前板）
        "H": lambda O: trapdoor(pal.frame, O.inn, "bottom", True),    # 外側の板（日よけのたれ）
        "t": lambda O: trapdoor(pal.trim, O.out, "bottom", True),     # 濃い木の、壁に沿う板
        "f": lambda O: leaves("flowering_azalea_leaves"),
        "a": lambda O: leaves("azalea_leaves"),
        "L": lambda O: lantern(True),
        "o": lambda O: lantern(False),
        "C": lambda O: block("chain", axis="y", waterlogged=False),
        "F": lambda O: fence(pal.trim),
        "W": lambda O: wall("brick"),
        "k": lambda O: slab(pal.frame, "top"),
        "j": lambda O: slab("stone_brick", "bottom"),
        "b": lambda O: block("barrel", facing="up", open=False),
        "B": lambda O: block("bookshelf"),
        "d": lambda O: door(pal.frame, O.out, False),
        "e": lambda O: door(pal.frame, O.out, True),
        "x": lambda O: door(pal.frame, O.out, False, hinge="right"),
        "y": lambda O: door(pal.frame, O.out, True, hinge="right"),
    }


def stamp(w, pal, face, x0, z0, y_top, layers, extra=None):
    """layers：{奥行き: [行（上から）]}。(x0, z0) は層 0 の左はしのマス、y_top は1行目の高さ。"""
    O = Orient(face)
    lg = legend(pal)
    if extra:
        lg.update(extra)
    for depth, rows in layers.items():
        for j, row in enumerate(rows):
            y = y_top - j
            for i, ch in enumerate(row):
                if ch == " ":
                    continue
                x, z = cell(face, x0, z0, i, depth)
                w.put(x, y, z, lg[ch](O))


def compose(parts):
    """左から右へ並べた部品（{奥行き: [行]}）を、1つの立面につなぐ。部品の高さ（行の数）はそろえる。"""
    depths = sorted(set(d for p in parts for d in p))
    nrows = max(len(rows) for p in parts for rows in p.values())
    widths = [max(len(r) for rows in p.values() for r in rows) for p in parts]
    out = {}
    for d in depths:
        rows = []
        for j in range(nrows):
            s = ""
            for p, width in zip(parts, widths):
                r = p.get(d)
                piece = r[j] if r is not None and j < len(r) else ""
                s += piece.ljust(width)
            rows.append(s)
        out[d] = rows
    return out


def wall_segments(a_min, a_max, posts, face):
    """壁を、柱（幅1）と柱の間（ベイ）に分ける。外から見て左から右の順の [(種類, 幅, 始まり, 終わり)]。"""
    posts = sorted(p for p in posts if a_min <= p <= a_max)
    segs = []
    a = a_min
    for p in posts:
        if p > a:
            segs.append(("bay", p - a, a, p - 1))
        segs.append(("post", 1, p, p))
        a = p + 1
    if a <= a_max:
        segs.append(("bay", a_max - a + 1, a, a_max))
    if face in ("north", "east"):
        # 外から見ると、座標の大きい方が左
        segs = [(k, n, a1, a0) for (k, n, a0, a1) in reversed(segs)]
    return segs


def window(w, pal, face, plane, a_min, width, y0, height, shutters=True, sill=True, lintel=True, glass_depth=-1,
           mullion=None, transom=None):
    """壁の面 plane に窓をあける：面は空け、ガラスは glass_depth の層。外の層に、両脇の雨戸・下の窓台・上のまぐさ。

    a_min..a_min+width-1 は窓の幅（壁に沿う座標）。mullion は縦の桟（外から見て左から何番目）、transom は横の桟の高さ。
    """
    x0, z0 = origin(face, a_min, a_min + width - 1, plane)
    O = Orient(face)
    for i in range(width):
        for j in range(height):
            y = y0 + j
            x, z = cell(face, x0, z0, i, 0)
            gx, gz = cell(face, x0, z0, i, glass_depth)
            if mullion is not None and i == mullion:
                w.put(x, y, z, log(pal.trim, "y"))
                w.put(gx, y, gz, log(pal.trim, "y"))
                continue
            if transom is not None and y == transom:
                w.put(x, y, z, block(pal.trim + "_planks"))
                w.put(gx, y, gz, block(pal.trim + "_planks"))
                continue
            if glass_depth != 0:
                w.put(x, y, z, AIR)
            w.put(gx, y, gz, pane())
    # 板ガラスが両はしでつながるように、ガラスの層の両脇を埋める（中から見える壁の裏）
    for j in range(height):
        for i in (-1, width):
            gx, gz = cell(face, x0, z0, i, glass_depth)
            if w.get(gx, y0 + j, gz) is None:
                w.put(gx, y0 + j, gz, block(pal.plaster))
    for j in range(height):
        if shutters:
            for i in (-1, width):
                x, z = cell(face, x0, z0, i, 1)
                w.put(x, y0 + j, z, trapdoor(pal.frame, O.out, "bottom", True))
    for i in range(width):
        x, z = cell(face, x0, z0, i, 1)
        if sill:
            w.put(x, y0 - 1, z, trapdoor(pal.trim, O.out, "top", False))
        if lintel:
            w.put(x, y0 + height, z, trapdoor(pal.trim, O.out, "bottom", False))
