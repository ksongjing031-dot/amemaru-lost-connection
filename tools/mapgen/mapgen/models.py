"""見本の絵で、小さな部品を本物の形で描くための「模型」。

4×4×4 の小さな箱では形が出ないもの（ランタン・鎖・焚き火・花・看板・旗・額縁・防具立てなど）は、
ゲームと同じ模型（箱の並びと、面ごとのテクスチャの位置）で描く。

- ランタン・鎖・焚き火・ろうそく・植木鉢・花・金床・砥石・書見台・大釜・鐘の台・エンドロッド・避雷針・トリップワイヤーフックは、
  ゲームの模型（assets/minecraft/blockstates と models）をそのまま読む。
- 看板・旗・チェスト・寝台・鐘の本体（ゲームが別に描くもの）と、額縁・防具立て（もの）は、ここで箱を組む。

模型とテクスチャは、初めて使うときに GitHub の Minecraft の素材の写し（1.20.1）から取ってきて、手元にためる
（~/.cache/amemaru-mapgen/1.20.1/assets）。リポジトリには入れない。

1つの箱（Piece）は、単位の箱（-1..1）から世界（ブロックの単位）への変換 A・b と、模型の座標（1/16）での
from・to、面ごとのテクスチャを持つ。絵を描く側（render.py）は、光線を箱の中の座標に移して当たりを調べる。
"""
import io
import json
import math
import os
import urllib.request

import numpy as np
from PIL import Image

from .world import props_of

ROOT = os.path.expanduser("~/.cache/amemaru-mapgen/1.20.1/assets")
SOURCE = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.1/assets/minecraft/{}"

WATER_TINT = np.array([0.25, 0.46, 0.89], dtype=np.float32)
FOLIAGE_TINT = np.array([0.47, 0.67, 0.18], dtype=np.float32)
GRASS_TINT = np.array([0.57, 0.74, 0.35], dtype=np.float32)

# 光を出すテクスチャ（日の光の影をつけず、明るいまま描く）
EMISSIVE = {"block/lantern", "block/soul_lantern", "block/campfire_fire", "block/soul_campfire_fire", "block/torch",
            "block/soul_torch", "block/sea_lantern", "block/glowstone", "block/end_rod", "__flame"}

# 本物の模型で描くブロック（ゲームの模型を読むもの）
MODEL_BLOCKS = {"lantern", "soul_lantern", "chain", "campfire", "soul_campfire", "flower_pot", "end_rod", "lightning_rod",
                "anvil", "chipped_anvil", "damaged_anvil", "grindstone", "lectern", "cauldron", "water_cauldron", "bell",
                "torch", "wall_torch", "poppy", "dandelion", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
                "lily_of_the_valley", "blue_orchid", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                "brewing_stand", "ladder", "stonecutter", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
                "small_amethyst_bud", "tripwire_hook"}


def is_model_block(name):
    """見本の絵で、模型（ここ）で描くブロックか。"""
    if name in MODEL_BLOCKS or name.startswith("potted_") or name == "candle" or name.endswith("_candle"):
        return True
    return is_custom_block(name)


def is_custom_block(name):
    """ゲームが別に描くブロック（ここで箱を組む）か。"""
    return (name == "chest" or name == "trapped_chest" or name.endswith("_bed") or name.endswith("_sign")
            or name.endswith("_banner"))


# ---------------------------------------------------------------- 素材を取ってくる
def _fetch(rel):
    path = os.path.join(ROOT, rel)
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        try:
            with urllib.request.urlopen(SOURCE.format(rel), timeout=20) as response:
                data = response.read()
        except Exception:
            return None
        with open(path, "wb") as f:
            f.write(data)
    with open(path, "rb") as f:
        return f.read()


_JSON = {}


def load_json(rel):
    if rel not in _JSON:
        data = _fetch(rel)
        _JSON[rel] = json.loads(data) if data else None
    return _JSON[rel]


_IMG = {}


def image(tex):
    """テクスチャ（"block/lantern"・"item/iron_sword"・"entity/banner_base" など）。RGBA の 0〜1、動くものは最初の1枚。"""
    tex = tex.replace("minecraft:", "")
    if tex in _IMG:
        return _IMG[tex]
    data = _fetch("textures/" + tex + ".png")
    if data is None:
        arr = np.zeros((16, 16, 4), dtype=np.float32)
        arr[..., 0] = arr[..., 2] = arr[..., 3] = 1.0
    else:
        img = Image.open(io.BytesIO(data)).convert("RGBA")
        if img.height > img.width and img.height % img.width == 0:
            img = img.crop((0, 0, img.width, img.width))
        arr = np.asarray(img, dtype=np.float32) / 255.0
    _IMG[tex] = arr
    return arr


# ---------------------------------------------------------------- 回転
def _rot(axis, deg):
    """右手系で axis のまわりに deg 度まわす行列。"""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float64)
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float64)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float64)


def block_rotation(x=0, y=0):
    """ブロックの状態の回転（x のあと y。上から見て時計回り）。"""
    return _rot("y", -y) @ _rot("x", -x)


YROT = {"north": 0, "east": 90, "south": 180, "west": 270}   # 北を向いた模型を、その向きにする y の回転


# ---------------------------------------------------------------- 箱
class Piece:
    """模型の箱1つ。faces は 面の名前 → (テクスチャ, uv(16の単位), 回転, 色合い, 光るか)。"""

    __slots__ = ("A", "Ainv", "b", "frm", "to", "faces", "shade", "corners")

    def __init__(self, cell, frm, to, faces, R=None, pivot=(8.0, 8.0, 8.0), elem_rot=None, shade=True):
        frm = np.array(frm, dtype=np.float64)
        to = np.array(to, dtype=np.float64)
        c = (frm + to) / 2.0
        h = np.maximum((to - frm) / 2.0, 0.002)
        M = np.eye(3)
        origin = np.array(pivot, dtype=np.float64)
        e_origin = c.copy()
        if elem_rot is not None:
            o = np.array(elem_rot.get("origin", (8, 8, 8)), dtype=np.float64)
            ang = float(elem_rot.get("angle", 0))
            ax = elem_rot.get("axis", "y")
            Re = _rot(ax, ang)
            scale = np.ones(3)
            if elem_rot.get("rescale"):
                f = 1.0 / max(math.cos(math.radians(ang)), 1e-6)
                for i, a in enumerate("xyz"):
                    if a != ax:
                        scale[i] = f
            M = np.diag(scale) @ Re
            e_origin = o
        else:
            e_origin = c.copy()
        Rb = np.eye(3) if R is None else np.asarray(R, dtype=np.float64)
        lin = Rb @ M
        A = lin @ np.diag(h) / 16.0
        center_model = M @ (c - e_origin) + e_origin            # 箱の中心（模型の座標、回転のあと）
        b = np.asarray(cell, dtype=np.float64) + (Rb @ (center_model - origin) + origin) / 16.0
        self.A = A
        self.Ainv = np.linalg.inv(A)
        self.b = b
        self.frm = frm
        self.to = to
        self.faces = faces
        self.shade = shade
        signs = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)], dtype=np.float64)
        self.corners = signs @ A.T + b


def _face(tex, uv, rot=0, tint=None, emissive=None):
    if isinstance(tex, str):
        name = tex.replace("minecraft:", "")
        arr = image(name)
        if emissive is None:
            emissive = name in EMISSIVE
    else:
        arr = tex
        emissive = bool(emissive)
    return (arr, tuple(float(v) for v in uv), int(rot) % 360, tint, emissive)


def default_uv(face, frm, to):
    f, t = frm, to
    return {
        "down": (f[0], 16 - t[2], t[0], 16 - f[2]),
        "up": (f[0], f[2], t[0], t[2]),
        "north": (16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]),
        "south": (f[0], 16 - t[1], t[0], 16 - f[1]),
        "west": (f[2], 16 - t[1], t[2], 16 - f[1]),
        "east": (16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]),
    }[face]


def px_uv(x0, y0, x1, y1, w, h):
    """テクスチャの画素の位置（左上 x0,y0 から右下 x1,y1）を、16 の単位の uv にする。"""
    return (x0 * 16.0 / w, y0 * 16.0 / h, x1 * 16.0 / w, y1 * 16.0 / h)


def box_uv(u, v, w, h, d, tw, th):
    """生きもの・ブロックの追加データの模型の、箱の展開図（よくある並び）。面の名前 → uv。"""
    return {
        "up": px_uv(u + d, v, u + d + w, v + d, tw, th),
        "down": px_uv(u + d + w, v, u + d + 2 * w, v + d, tw, th),
        "west": px_uv(u, v + d, u + d, v + d + h, tw, th),
        "north": px_uv(u + d, v + d, u + d + w, v + d + h, tw, th),
        "east": px_uv(u + d + w, v + d, u + 2 * d + w, v + d + h, tw, th),
        "south": px_uv(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, tw, th),
    }


# ---------------------------------------------------------------- ゲームの模型を読む
def _resolve_model(name):
    name = name.replace("minecraft:", "")
    chain = []
    seen = 0
    while name and seen < 12:
        m = load_json("models/" + name + ".json")
        if m is None:
            break
        chain.append(m)
        parent = m.get("parent")
        name = parent.replace("minecraft:", "") if parent and not parent.startswith("builtin/") else None
        seen += 1
    textures, elements = {}, []
    for m in reversed(chain):
        textures.update(m.get("textures", {}))
        if "elements" in m:
            elements = m["elements"]

    def resolve(ref):
        n = 0
        while ref.startswith("#") and n < 10:
            ref = textures.get(ref[1:], "")
            n += 1
        return ref
    return elements, resolve


def _match(when, props):
    if "OR" in when:
        return any(_match(w, props) for w in when["OR"])
    if "AND" in when:
        return all(_match(w, props) for w in when["AND"])
    return all(props.get(k) in str(v).split("|") for k, v in when.items())


def block_models(name, props):
    """ブロックの状態 → [(模型の名前, x, y)]。"""
    bs = load_json("blockstates/" + name + ".json")
    if bs is None:
        return []
    out = []
    if "variants" in bs:
        for key, v in bs["variants"].items():
            cond = {} if key == "" else dict(kv.split("=") for kv in key.split(","))
            if all(props.get(k) == val for k, val in cond.items()):
                v = v[0] if isinstance(v, list) else v
                out.append((v["model"], v.get("x", 0), v.get("y", 0)))
                break
    else:
        for part in bs.get("multipart", []):
            when = part.get("when")
            if when is None or _match(when, props):
                ap = part["apply"]
                ap = ap[0] if isinstance(ap, list) else ap
                out.append((ap["model"], ap.get("x", 0), ap.get("y", 0)))
    return out


def _tint_for(tex):
    t = tex.replace("minecraft:", "")
    if "water" in t:
        return WATER_TINT
    if "grass" in t or "fern" in t:
        return GRASS_TINT
    if "leaves" in t or "vine" in t:
        return FOLIAGE_TINT
    return None


def model_pieces(model, cell, x=0, y=0, R=None):
    """ゲームの模型 → Piece の並び。R を渡すと、x・y の代わりにその回転を使う。"""
    elements, resolve = _resolve_model(model)
    if R is None:
        R = block_rotation(x, y)
    pieces = []
    for el in elements:
        frm, to = el["from"], el["to"]
        faces = {}
        for fname, fd in el.get("faces", {}).items():
            tex = resolve(fd.get("texture", ""))
            if not tex:
                continue
            uv = fd.get("uv") or default_uv(fname, frm, to)
            tint = _tint_for(tex) if "tintindex" in fd else None
            faces[fname] = _face(tex, uv, fd.get("rotation", 0), tint)
        if faces:
            pieces.append(Piece(cell, frm, to, faces, R=R, elem_rot=el.get("rotation"), shade=el.get("shade", True)))
    return pieces


# ---------------------------------------------------------------- ゲームが別に描くもの（ブロック）
DYE = {"white": (249, 255, 254), "orange": (249, 128, 29), "magenta": (199, 78, 189), "light_blue": (58, 179, 218),
       "yellow": (254, 216, 61), "lime": (128, 199, 31), "pink": (243, 139, 170), "gray": (71, 79, 82),
       "light_gray": (157, 157, 151), "cyan": (22, 156, 156), "purple": (137, 50, 184), "blue": (60, 68, 170),
       "brown": (131, 84, 50), "green": (94, 124, 22), "red": (176, 46, 38), "black": (29, 29, 33)}
DYE_BY_ID = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
             "purple", "blue", "brown", "green", "red", "black"]
PATTERN = {"b": "base", "bo": "border", "cr": "cross", "sc": "straight_cross", "mr": "rhombus", "mc": "circle",
           "ts": "stripe_top", "bs": "stripe_bottom", "ls": "stripe_left", "rs": "stripe_right", "cs": "stripe_center",
           "ms": "stripe_middle", "dls": "stripe_downleft", "drs": "stripe_downright", "ss": "small_stripes",
           "cbo": "curly_border", "bri": "bricks", "gra": "gradient", "gru": "gradient_up", "tt": "triangle_top",
           "bt": "triangle_bottom", "tts": "triangles_top", "bts": "triangles_bottom", "ld": "diagonal_left",
           "rd": "diagonal_right", "lud": "diagonal_up_left", "rud": "diagonal_up_right", "hh": "half_horizontal",
           "hhb": "half_horizontal_bottom", "vh": "half_vertical", "vhr": "half_vertical_right",
           "tl": "square_top_left", "tr": "square_top_right", "bl": "square_bottom_left", "br": "square_bottom_right",
           "cre": "creeper", "sku": "skull", "flo": "flower", "moj": "mojang", "glb": "globe", "pig": "piglin"}
_BANNER = {}


def banner_texture(base_color, patterns):
    """旗の布のテクスチャ（64×64）。地の色に、模様を順に重ねる。"""
    key = (base_color, tuple(patterns))
    if key in _BANNER:
        return _BANNER[key]
    base = image("entity/banner_base")
    out = base.copy()
    out[..., :3] = base[..., :3] * (np.array(DYE[base_color], dtype=np.float32) / 255.0)
    for code, color in patterns:
        name = PATTERN.get(code)
        if not name:
            continue
        pat = image("entity/banner/" + name)
        a = pat[..., 3:4]
        col = pat[..., :3] * (np.array(DYE[color], dtype=np.float32) / 255.0)
        out[..., :3] = out[..., :3] * (1 - a) + col * a
    _BANNER[key] = out
    return out


def _banner_patterns(nbt):
    out = []
    if nbt is None:
        return out
    try:
        for p in nbt.get("Patterns", []):
            out.append((str(p["Pattern"]), DYE_BY_ID[int(p["Color"])]))
    except Exception:
        pass
    return out


def banner_pieces(name, props, nbt, cell):
    color = name.replace("_wall_banner", "").replace("_banner", "")
    tex = banner_texture(color, _banner_patterns(nbt))
    front = px_uv(1, 1, 21, 41, 64, 64)
    back = px_uv(22, 1, 42, 41, 64, 64)
    edge = px_uv(0, 1, 1, 41, 64, 64)
    flag_faces = {"south": _face(tex, front), "north": _face(tex, back), "west": _face(tex, edge),
                  "east": _face(tex, edge), "up": _face(tex, px_uv(1, 0, 21, 1, 64, 64)),
                  "down": _face(tex, px_uv(21, 0, 41, 1, 64, 64))}
    bar = {f: _face("block/dark_oak_planks", (0, 7, 16, 9)) for f in ("north", "south", "up", "down", "west", "east")}
    if name.endswith("_wall_banner"):
        # 南を向いた形（北の壁に掛かる）を、向きに合わせてまわす
        R = block_rotation(0, (YROT[props.get("facing", "south")] + 180) % 360)
        return [Piece(cell, (1.33, -12.0, 1.0), (14.67, 14.67, 1.67), flag_faces, R=R),
                Piece(cell, (1.33, 14.67, 0.33), (14.67, 16.0, 2.0), bar, R=R)]
    rot = int(props.get("rotation", "0")) * 22.5
    R = block_rotation(0, rot)
    pole = {f: _face("block/dark_oak_planks", (7, 0, 9, 16)) for f in ("north", "south", "up", "down", "west", "east")}
    return [Piece(cell, (1.33, 4.0, 7.33), (14.67, 30.67, 8.0), flag_faces, R=R),
            Piece(cell, (1.33, 30.67, 7.0), (14.67, 32.0, 8.67), bar, R=R),
            Piece(cell, (7.33, 0.0, 7.33), (8.67, 30.67, 8.67), pole, R=R)]


def _wood_of(name):
    for suffix in ("_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign"):
        if name.endswith(suffix):
            return name[: -len(suffix)]
    return "oak"


def sign_pieces(name, props, cell):
    wood = _wood_of(name)
    if name.endswith("_hanging_sign"):
        tex = image("entity/signs/hanging/" + wood)
        tw, th = tex.shape[1], tex.shape[0]
        board = {"south": _face(tex, px_uv(2, 14, 16, 24, tw, th)), "north": _face(tex, px_uv(18, 14, 32, 24, tw, th)),
                 "up": _face(tex, px_uv(2, 12, 16, 14, tw, th)), "down": _face(tex, px_uv(16, 12, 30, 14, tw, th)),
                 "west": _face(tex, px_uv(0, 14, 2, 24, tw, th)), "east": _face(tex, px_uv(16, 14, 18, 24, tw, th))}
        chain = {f: _face("block/chain", (0, 0, 3, 16)) for f in ("north", "south", "west", "east")}
        pieces = []
        if name.endswith("_wall_hanging_sign"):
            # 南を向いた形（棒は東西）を、向きに合わせてまわす
            R = block_rotation(0, (YROT[props.get("facing", "south")] + 180) % 360)
            bar = {"up": _face(tex, px_uv(4, 0, 20, 4, tw, th)), "down": _face(tex, px_uv(20, 0, 36, 4, tw, th)),
                   "north": _face(tex, px_uv(4, 4, 20, 6, tw, th)), "south": _face(tex, px_uv(24, 4, 40, 6, tw, th)),
                   "west": _face(tex, px_uv(0, 4, 4, 6, tw, th)), "east": _face(tex, px_uv(20, 4, 24, 6, tw, th))}
            pieces.append(Piece(cell, (0, 14, 6), (16, 16, 10), bar, R=R))
            top = 14.0
        else:
            rot = int(props.get("rotation", "0")) * 22.5
            R = block_rotation(0, rot)
            top = 16.0
        pieces.append(Piece(cell, (1, 0, 7), (15, 10, 9), board, R=R))
        for x in (3.5, 12.5):
            pieces.append(Piece(cell, (x - 0.75, 10, 8), (x + 0.75, top, 8), chain, R=R, shade=False))
            pieces.append(Piece(cell, (x, 10, 7.25), (x, top, 8.75), chain, R=R, shade=False))
        return pieces
    tex = image("entity/signs/" + wood)
    tw, th = tex.shape[1], tex.shape[0]
    board = {"south": _face(tex, px_uv(2, 2, 26, 14, tw, th)), "north": _face(tex, px_uv(28, 2, 52, 14, tw, th)),
             "up": _face(tex, px_uv(2, 0, 26, 2, tw, th)), "down": _face(tex, px_uv(26, 0, 50, 2, tw, th)),
             "west": _face(tex, px_uv(0, 2, 2, 14, tw, th)), "east": _face(tex, px_uv(26, 2, 28, 14, tw, th))}
    if name.endswith("_wall_sign"):
        R = block_rotation(0, (YROT[props.get("facing", "south")] + 180) % 360)
        return [Piece(cell, (0, 4.33, 0), (16, 12.33, 1.33), board, R=R)]
    rot = int(props.get("rotation", "0")) * 22.5
    R = block_rotation(0, rot)
    stick = {f: _face(tex, px_uv(0, 16, 2, 30, tw, th)) for f in ("north", "south", "west", "east", "up", "down")}
    return [Piece(cell, (0, 9.33, 7.33), (16, 17.33, 8.67), board, R=R),
            Piece(cell, (7.33, 0, 7.33), (8.67, 9.33, 8.67), stick, R=R)]


def chest_pieces(props, cell):
    tex = image("entity/chest/normal")
    R = block_rotation(0, YROT[props.get("facing", "north")])
    lid = {k: _face(tex, v) for k, v in box_uv(0, 0, 14, 5, 14, 64, 64).items()}
    base = {k: _face(tex, v) for k, v in box_uv(0, 19, 14, 10, 14, 64, 64).items()}
    lock = {k: _face(tex, v) for k, v in box_uv(0, 0, 2, 4, 1, 64, 64).items()}
    return [Piece(cell, (1, 0, 1), (15, 10, 15), base, R=R), Piece(cell, (1, 10, 1), (15, 14, 15), lid, R=R),
            Piece(cell, (7, 7, 0), (9, 11, 1), lock, R=R)]


def bed_pieces(name, props, cell):
    color = name.replace("_bed", "")
    tex = image("entity/bed/" + color)
    R = block_rotation(0, YROT[props.get("facing", "north")])
    head = props.get("part", "head") == "head"
    top = px_uv(6, 6, 22, 22, 64, 64) if head else px_uv(6, 28, 22, 44, 64, 64)
    side = px_uv(22, 6, 28, 22, 64, 64) if head else px_uv(22, 28, 28, 44, 64, 64)
    end = px_uv(6, 0, 22, 6, 64, 64) if head else px_uv(6, 22, 22, 28, 64, 64)
    mattress = {"up": _face(tex, top), "down": _face("block/oak_planks", (0, 0, 16, 16)),
                "west": _face(tex, side, 90), "east": _face(tex, side, 270), "north": _face(tex, end),
                "south": _face(tex, end)}
    leg = {f: _face(tex, px_uv(50, 3, 53, 6, 64, 64)) for f in ("north", "south", "west", "east", "up", "down")}
    z0, z1 = (0, 3) if head else (13, 16)
    return [Piece(cell, (0, 3, 0), (16, 9, 16), mattress, R=R),
            Piece(cell, (0, 0, z0), (3, 3, z1), leg, R=R), Piece(cell, (13, 0, z0), (16, 3, z1), leg, R=R)]


def bell_body_pieces(props, cell):
    tex = image("entity/bell/bell_body")
    body = {k: _face(tex, v) for k, v in box_uv(0, 0, 6, 7, 6, 32, 32).items()}
    lip = {k: _face(tex, v) for k, v in box_uv(0, 13, 8, 2, 8, 32, 32).items()}
    return [Piece(cell, (5, 6, 5), (11, 13, 11), body), Piece(cell, (4, 4, 4), (12, 6, 12), lip)]


def candle_flames(props, cell):
    """火のついたろうそくの小さな炎（ゲームでは粒で描くもの）。"""
    if props.get("lit") != "true":
        return []
    n = int(props.get("candles", "1"))
    spots = {1: [(8, 8)], 2: [(6, 7), (9, 8)], 3: [(8, 6), (6, 9), (10, 9)], 4: [(6, 6), (9, 6), (6, 9), (10, 10)]}[n]
    flame = np.zeros((4, 4, 4), dtype=np.float32)
    flame[..., 0], flame[..., 1], flame[..., 2], flame[..., 3] = 1.0, 0.82, 0.45, 1.0
    faces = {f: _face(flame, (0, 0, 16, 16), emissive=True) for f in ("north", "south", "west", "east", "up", "down")}
    return [Piece(cell, (x - 0.5, 6.5, z - 0.5), (x + 0.5, 8.0, z + 0.5), faces) for (x, z) in spots]


# ---------------------------------------------------------------- もの（額縁・防具立て）
def _item_texture(item):
    """額縁の中に平たく描くアイテムの絵。アイテムの模型の layer0（たいまつ・花は block/ の絵）を使い、
    ブロックのアイテム（羊毛など）は、そのブロックの模型の絵を使う。"""
    item = (item or "").replace("minecraft:", "")
    names = {"crossbow": "crossbow_standby", "filled_map": "filled_map", "map": "map", "trident": "trident",
             "shield": "shield", "clock": "clock_00", "compass": "compass_16"}
    if item in names:
        return "item/" + names[item]
    model = load_json("models/item/" + item + ".json")
    for _ in range(4):
        if model is None:
            break
        textures = model.get("textures", {})
        if "layer0" in textures:
            layers = [textures[k] for k in ("layer0", "layer1") if k in textures]
            plain = [t for t in layers if not t.endswith("_overlay")]    # 薬の瓶などは、色の重ね絵でない方
            return (plain or layers)[0].replace("minecraft:", "")
        parent = model.get("parent", "").replace("minecraft:", "")
        if parent.startswith("block/"):
            block_model = load_json("models/" + parent + ".json") or {}
            bt = block_model.get("textures", {})
            for key in ("all", "side", "texture", "front", "particle"):
                if key in bt and not bt[key].startswith("#"):
                    return bt[key].replace("minecraft:", "")
            break
        if not parent or parent.startswith("builtin/") or parent == "item/generated":
            break
        model = load_json("models/" + parent + ".json")
    return "item/" + item


def item_frame_pieces(entity):
    x, y, z = entity.cell
    f = entity.facing
    if f == "up":
        R = block_rotation(270, 0)
    elif f == "down":
        R = block_rotation(90, 0)
    else:
        R = block_rotation(0, YROT[f])
    out = model_pieces("block/item_frame", (x, y, z), R=R)    # 模型は北を向いた形
    if entity.item == "shield":
        out.extend(shield_frame_pieces(entity, R))
    elif entity.item:
        rot = 0
        try:
            rot = int(entity.nbt["ItemRotation"])
        except Exception:
            pass
        sprite = {"north": _face(_item_texture(entity.item), (0, 0, 16, 16))}
        out.append(Piece((x, y, z), (4, 4, 15.0), (12, 12, 15.0), sprite, R=R,
                         elem_rot={"origin": (8, 8, 15), "axis": "z", "angle": -45 * rot}, shade=True))
    return out


ARMOR_STAND = (   # (from, to)（体の中心が x=8・z=8、足もとが y=0。正面は南）
    ((2, 0, 2), (14, 1, 14), "block/smooth_stone"),
    ((5, 1, 7), (7, 12, 9), "block/oak_planks"), ((9, 1, 7), (11, 12, 9), "block/oak_planks"),
    ((4, 12, 7), (12, 14, 9), "block/oak_planks"),
    ((5, 14, 7), (7, 21, 9), "block/oak_planks"), ((9, 14, 7), (11, 21, 9), "block/oak_planks"),
    ((2, 21, 6.5), (14, 24, 9.5), "block/oak_planks"),
    ((7, 24, 7), (9, 31, 9), "block/oak_planks"),
    ((0, 12, 7), (2, 23, 9), "block/oak_planks"), ((14, 12, 7), (16, 23, 9), "block/oak_planks"),
)


def _size_uv(face, frm, to):
    """面の大きさに合わせた uv（テクスチャの左上から、16 までで切る）。"""
    dx, dy, dz = (abs(to[i] - frm[i]) for i in range(3))
    w, h = {"up": (dx, dz), "down": (dx, dz), "north": (dx, dy), "south": (dx, dy), "west": (dz, dy), "east": (dz, dy)}[face]
    return (0, 0, min(max(w, 0.5), 16), min(max(h, 0.5), 16))


def _hand_items(entity):
    """防具立ての (利き手, 反対の手) のアイテムの名前。"""
    out = []
    try:
        for it in entity.nbt["HandItems"]:
            out.append(str(it["id"]).replace("minecraft:", "") if "id" in it else None)
    except Exception:
        out = [getattr(entity, "item", None), None]
    while len(out) < 2:
        out.append(None)
    return out[0], out[1]


def armor_stand_pieces(entity):
    x, y, z = entity.cell
    R = block_rotation(0, (YROT[entity.facing] + 180) % 360)
    out = []
    no_base = False
    try:
        no_base = int(entity.nbt["NoBasePlate"]) == 1
    except Exception:
        pass
    for i, (frm, to, tex) in enumerate(ARMOR_STAND):
        if i == 0 and no_base:
            continue
        faces = {fc: _face(tex, _size_uv(fc, frm, to)) for fc in ("north", "south", "west", "east", "up", "down")}
        out.append(Piece((x, y, z), frm, to, faces, R=R))
    hand, off = _hand_items(entity)
    for it, hx in ((hand, 1.0), (off, 15.0)):
        if not it or it == "shield":
            continue
        sprite = {"west": _face(_item_texture(it), (0, 0, 16, 16)), "east": _face(_item_texture(it), (16, 0, 0, 16))}
        out.append(Piece((x, y, z), (hx, 10, 6), (hx, 20, 16), sprite, R=R))
    out.extend(armor_pieces(entity, R))
    return out


# ---------------------------------------------------------------- 着せた防具（ゲームの防具の層のテクスチャ）
# 防具の材料 → テクスチャの名前（models/armor/<名前>_layer_1・_layer_2）
ARMOR_MATERIAL = {"leather": "leather", "chainmail": "chainmail", "iron": "iron", "golden": "gold",
                  "diamond": "diamond", "netherite": "netherite", "turtle": "turtle"}
LEATHER_DEFAULT = 0xA06540     # 染めていない革の色

# 人の形の模型の箱（防具立ての中の座標。体の中心が x=8・z=8、足もとが y=0、正面は南）。
# (from, to, 展開図の左上 u・v、幅・高さ・奥行き、左右反転)。防具立ての右腕・右足は西（x の小さい方）。
HUMANOID = {
    "head": ((4, 24, 4), (12, 32, 12), (0, 0, 8, 8, 8), False),
    "hat": ((4, 24, 4), (12, 32, 12), (32, 0, 8, 8, 8), False),
    "body": ((4, 12, 6), (12, 24, 10), (16, 16, 8, 12, 4), False),
    "right_arm": ((0, 12, 6), (4, 24, 10), (40, 16, 4, 12, 4), False),
    "left_arm": ((12, 12, 6), (16, 24, 10), (40, 16, 4, 12, 4), True),
    "right_leg": ((4, 0, 6), (8, 12, 10), (0, 16, 4, 12, 4), False),
    "left_leg": ((8, 0, 6), (12, 12, 10), (0, 16, 4, 12, 4), True),
}
# 防具の場所 → (層, ふくらませる量、使う箱)
ARMOR_SLOTS = {
    "head": (1, 1.0, ("head",)),
    "chest": (1, 1.0, ("body", "right_arm", "left_arm")),
    "legs": (2, 0.5, ("body", "right_leg", "left_leg")),
    "feet": (1, 1.0, ("right_leg", "left_leg")),
}


def entity_box_faces(tex, uv_box, tw, th, mirror=False, tint=None):
    """生きものの模型の箱の展開図 → 面の名前 → 面。南を向いた形（正面＝南）。"""
    u, v, w, h, d = uv_box
    reg = {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + 2 * w, v + d),
        "west": (u, v + d, u + d, v + d + h),                 # 体の右側（南を向いた形では西）
        "south": (u + d, v + d, u + d + w, v + d + h),         # 正面
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),  # 体の左側
        "north": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),   # 背中
    }
    if mirror:
        reg["west"], reg["east"] = reg["east"], reg["west"]
        reg = {k: (r[2], r[1], r[0], r[3]) for k, r in reg.items()}
    return {k: _face(tex, px_uv(r[0], r[1], r[2], r[3], tw, th), tint=tint) for k, r in reg.items()}


def _armor_list(entity):
    """防具立ての (場所, アイテムの名前, 革の色) の並び。"""
    out = []
    try:
        items = list(entity.nbt["ArmorItems"])
    except Exception:
        return out
    for slot, it in zip(("feet", "legs", "chest", "head"), items):
        if "id" not in it:
            continue
        name = str(it["id"]).replace("minecraft:", "")
        color = None
        try:
            color = int(it["tag"]["display"]["color"])
        except Exception:
            pass
        out.append((slot, name, color))
    return out


def armor_pieces(entity, R):
    x, y, z = entity.cell
    out = []
    for slot, name, color in _armor_list(entity):
        if name == "turtle_helmet":
            mat = "turtle"
        else:
            mat = ARMOR_MATERIAL.get(name.split("_")[0])
        if not mat or slot not in ARMOR_SLOTS:
            continue
        layer, grow, parts = ARMOR_SLOTS[slot]
        base = "models/armor/%s_layer_%d" % (mat, layer)
        tint = None
        overlay = None
        if mat == "leather":
            c = LEATHER_DEFAULT if color is None else color
            tint = np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], dtype=np.float32) / 255.0
            overlay = base + "_overlay"
        tex = image(base)
        th, tw = tex.shape[0], tex.shape[1]
        for part in parts + (("hat",) if slot == "head" else ()):
            frm, to, uv_box, mirror = HUMANOID[part]
            g = grow + (0.5 if part == "hat" else 0.0)
            f2 = tuple(a - g for a in frm)
            t2 = tuple(a + g for a in to)
            out.append(Piece((x, y, z), f2, t2, entity_box_faces(base, uv_box, tw, th, mirror, tint), R=R))
            if overlay:
                g2 = g + 0.02
                f3 = tuple(a - g2 for a in frm)
                t3 = tuple(a + g2 for a in to)
                out.append(Piece((x, y, z), f3, t3, entity_box_faces(overlay, uv_box, tw, th, mirror), R=R))
    return out


# ---------------------------------------------------------------- 盾（模様は旗と同じ記号）
_SHIELD = {}


def _shield_design(nbt):
    """アイテムの NBT → (地の色, 模様の並び)。模様のない盾は None。"""
    try:
        bet = nbt["tag"]["BlockEntityTag"]
    except Exception:
        return None
    base = DYE_BY_ID[int(bet["Base"])] if "Base" in bet else "white"
    pats = []
    try:
        for p in bet.get("Patterns", []):
            pats.append((str(p["Pattern"]), DYE_BY_ID[int(p["Color"])]))
    except Exception:
        pass
    return base, pats


def shield_texture(design):
    """盾のテクスチャ（64×64）。模様がなければ、木の地の盾。"""
    if design is None:
        return image("entity/shield_base_nopattern")
    key = (design[0], tuple(design[1]))
    if key in _SHIELD:
        return _SHIELD[key]
    out = image("entity/shield_base").copy()
    layers = [("b", design[0])] + list(design[1])
    for code, color in layers:
        name = PATTERN.get(code)
        if not name:
            continue
        pat = image("entity/shield/" + name)
        a = pat[..., 3:4]
        col = pat[..., :3] * (np.array(DYE[color], dtype=np.float32) / 255.0)
        out[..., :3] = out[..., :3] * (1 - a) + col * a
    _SHIELD[key] = out
    return out


def _shield_faces(tex, scale=1.0):
    """盾の板（12×22×1）の面。正面は北（額縁の模型と同じ向き）。"""
    front = px_uv(1, 1, 13, 23, 64, 64)
    back = px_uv(14, 1, 26, 23, 64, 64)
    edge_v = px_uv(0, 1, 1, 23, 64, 64)
    edge_h = px_uv(1, 0, 13, 1, 64, 64)
    return {"north": _face(tex, front), "south": _face(tex, back), "west": _face(tex, edge_v),
            "east": _face(tex, edge_v), "up": _face(tex, edge_h), "down": _face(tex, edge_h)}


def shield_frame_pieces(entity, R):
    """額縁に入れた盾：ゲームの「fixed」の形（半分の大きさ）で、額縁の真ん中に正面を向けて置く。"""
    x, y, z = entity.cell
    design = None
    try:
        design = _shield_design(entity.nbt["Item"])
    except Exception:
        pass
    tex = shield_texture(design)
    rot = 0
    try:
        rot = int(entity.nbt["ItemRotation"])
    except Exception:
        pass
    return [Piece((x, y, z), (5.0, 2.5, 14.0), (11.0, 13.5, 14.5), _shield_faces(tex), R=R,
                  elem_rot={"origin": (8, 8, 15), "axis": "z", "angle": -45 * rot} if rot else None)]


# ---------------------------------------------------------------- まとめ
def block_pieces(name, props, nbt, cell):
    if name.endswith("_banner"):
        return banner_pieces(name, props, nbt, cell)
    if name.endswith("_sign"):
        return sign_pieces(name, props, cell)
    if name in ("chest", "trapped_chest"):
        return chest_pieces(props, cell)
    if name.endswith("_bed"):
        return bed_pieces(name, props, cell)
    pieces = []
    for model, x, y in block_models(name, props):
        pieces.extend(model_pieces(model, cell, x, y))
    if name == "bell":
        pieces.extend(bell_body_pieces(props, cell))
    if name == "candle" or name.endswith("_candle"):
        pieces.extend(candle_flames(props, cell))
    return pieces


def build_pieces(world):
    """世界の、模型で描くブロックともの → Piece の並び。"""
    pieces = []
    for (x, y, z), state in world.blocks.items():
        name = state[0]
        if not is_model_block(name):
            continue
        pieces.extend(block_pieces(name, props_of(state), world.nbt.get((x, y, z)), (x, y, z)))
    for e in getattr(world, "entities", []):
        if e.kind == "item_frame":
            pieces.extend(item_frame_pieces(e))
        elif e.kind == "armor_stand":
            pieces.extend(armor_stand_pieces(e))
    return pieces
