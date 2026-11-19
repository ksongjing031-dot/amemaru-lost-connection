"""設計データの見本の絵（バニラのテクスチャで、光と影をつけて描く）。

ゲームを起動しなくても、形と色を確かめられるようにするための道具。ゲームの中の見た目（光の具合・遠くのぼかしなど）とは少し違う。
テクスチャは、初めて使うときに GitHub の Minecraft の素材の写し（1.20.1）から取ってきて、手元にためておく
（~/.cache/amemaru-mapgen）。リポジトリには入れない。

1ブロックを 4×4×4 の小さな箱に分けて、階段・ハーフ・柵・塀・トラップドア・板ガラスの形を表す。
ランタン・鎖・看板・旗・額縁・防具立てなどの小さな部品は、models.py の模型（ゲームと同じ形とテクスチャ）で描いて重ねる。
"""
import math
import os
import urllib.request

import numpy as np
from PIL import Image

from .models import build_pieces, is_model_block
from .world import kind, props_of

S = 4  # 1ブロックを何等分するか
CACHE = os.path.expanduser("~/.cache/amemaru-mapgen/1.20.1/block")
SOURCE = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.1/assets/minecraft/textures/block/{}.png"

FACES = ("east", "west", "up", "down", "south", "north")
FACE_LIGHT = np.array([0.6, 0.6, 1.0, 0.5, 0.8, 0.8], dtype=np.float32)
GRASS_TINT = np.array([0.57, 0.74, 0.35], dtype=np.float32)
FOLIAGE_TINT = np.array([0.47, 0.67, 0.18], dtype=np.float32)
WATER_TINT = np.array([0.25, 0.46, 0.89], dtype=np.float32)

WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "bamboo")
BASE = {
    "brick": "bricks", "stone_brick": "stone_bricks", "mossy_stone_brick": "mossy_stone_bricks", "cobblestone": "cobblestone",
    "mossy_cobblestone": "mossy_cobblestone", "polished_andesite": "polished_andesite", "andesite": "andesite",
    "smooth_quartz": "quartz_block_bottom", "quartz": "quartz_block_side", "smooth_sandstone": "sandstone_top",
    "sandstone": "sandstone", "deepslate_tile": "deepslate_tiles", "deepslate_brick": "deepslate_bricks",
    "polished_deepslate": "polished_deepslate", "cobbled_deepslate": "cobbled_deepslate", "mud_brick": "mud_bricks",
    "granite": "granite", "polished_granite": "polished_granite", "diorite": "diorite", "polished_diorite": "polished_diorite",
    "stone": "stone", "smooth_stone": "smooth_stone", "blackstone": "blackstone", "polished_blackstone": "polished_blackstone",
    "polished_blackstone_brick": "polished_blackstone_bricks", "end_stone_brick": "end_stone_bricks",
    "nether_brick": "nether_bricks", "red_nether_brick": "red_nether_bricks", "prismarine": "prismarine",
    "cut_copper": "cut_copper", "weathered_cut_copper": "weathered_cut_copper", "oxidized_cut_copper": "oxidized_cut_copper",
    "red_sandstone": "red_sandstone", "smooth_red_sandstone": "red_sandstone_top", "tuff": "tuff", "calcite": "calcite",
    "petrified_oak": "oak_planks", "quartz_brick": "quartz_bricks", "smooth_quartz_block": "quartz_block_bottom",
}
for _w in WOODS:
    BASE[_w] = _w + "_planks"

# 色だけで描くもの（光を出すものは emissive）
SOLID_COLORS = {
    "lantern": ((60, 52, 46), (255, 196, 96), True),
    "soul_lantern": ((50, 60, 70), (120, 220, 255), True),
    "chain": ((52, 58, 72), None, False),
    "end_rod": ((246, 240, 228), None, True),
    "lightning_rod": ((214, 120, 80), None, False),
    "torch": ((255, 200, 90), None, True),
    "bell": ((222, 176, 60), None, False),
    "wall_torch": ((255, 200, 90), None, True),
    "chest": ((150, 104, 52), None, False),
    "candle": ((236, 224, 196), (255, 214, 120), True),
    "white_candle": ((236, 224, 196), (255, 214, 120), True),
    "red_candle": ((180, 50, 40), (255, 190, 110), True),
    "red_bed": ((170, 40, 36), None, False),
    "lime_bed": ((120, 180, 50), None, False),
    "white_bed": ((230, 230, 230), None, False),
    "brown_bed": ((120, 80, 50), None, False),
    "blue_wall_banner": ((52, 72, 170), None, False),
    "red_wall_banner": ((164, 40, 34), None, False),
    "black_wall_banner": ((30, 30, 34), None, False),
    "white_wall_banner": ((236, 236, 236), None, False),
    "spruce_wall_sign": ((114, 84, 48), None, False),
    "spruce_wall_hanging_sign": ((114, 84, 48), None, False),
    "spruce_hanging_sign": ((114, 84, 48), None, False),
    "potted_azalea_bush": ((90, 120, 60), None, False),
    "potted_red_tulip": ((90, 120, 60), None, False),
}

_TEX_CACHE = {}


def texture(name):
    """16x16 の RGBA（0〜1）。動くテクスチャは最初の1枚。"""
    if name.startswith("waxed_"):
        name = name[len("waxed_"):]
    if name in _TEX_CACHE:
        return _TEX_CACHE[name]
    path = os.path.join(CACHE, name + ".png")
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        try:
            with urllib.request.urlopen(SOURCE.format(name), timeout=20) as response:
                data = response.read()
            with open(path, "wb") as f:
                f.write(data)
        except Exception:
            img = np.zeros((16, 16, 4), dtype=np.float32)
            img[..., 0] = 1.0
            img[..., 2] = 1.0
            img[..., 3] = 1.0
            _TEX_CACHE[name] = img
            return img
    img = Image.open(path).convert("RGBA")
    if img.height > img.width:
        img = img.crop((0, 0, img.width, img.width))
    if img.size != (16, 16):
        img = img.resize((16, 16), Image.NEAREST)
    arr = np.asarray(img, dtype=np.float32) / 255.0
    _TEX_CACHE[name] = arr
    return arr


def _tinted(name, tint, rows=None):
    arr = texture(name).copy()
    if rows is None:
        arr[..., :3] *= tint
    else:
        # 草の横：上の数行だけ草の色（灰色の重ね絵を、色で近づける）
        arr[:rows, :, :3] = arr[:rows, :, :3] * 0.35 + tint * 0.65
    return arr


def face_textures(name, props):
    """6面（東・西・上・下・南・北）のテクスチャ。"""
    k = kind(name)

    def all6(t):
        return [t] * 6

    def column(side, end, axis):
        if axis == "x":
            return [end, end, side, side, side, side]
        if axis == "z":
            return [side, side, side, side, end, end]
        return [side, side, end, end, side, side]

    if k in ("stairs", "slab", "wall"):
        mat = name.rsplit("_", 1)[0]
        base = BASE.get(mat, mat)
        if mat == "smooth_stone":
            return [texture("smooth_stone_slab_side")] * 2 + [texture("smooth_stone")] * 2 + [texture("smooth_stone_slab_side")] * 2
        if mat == "sandstone":
            return [texture("sandstone")] * 2 + [texture("sandstone_top"), texture("sandstone_bottom")] + [texture("sandstone")] * 2
        return all6(texture(base))
    if k in ("fence", "gate"):
        mat = name.replace("_fence_gate", "").replace("_fence", "")
        return all6(texture(BASE.get(mat, mat + "_planks")))
    if k == "trapdoor":
        return all6(texture(name))
    if k == "door":
        part = "top" if props.get("half") == "upper" else "bottom"
        return all6(texture(name + "_" + part))
    if k == "pane":
        return all6(texture("glass" if name == "glass_pane" else name.replace("_pane", "")))
    if k == "leaves":
        if name in ("azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"):
            return all6(texture(name))
        tint = np.array([0.38, 0.6, 0.38], dtype=np.float32) if name == "spruce_leaves" else FOLIAGE_TINT
        return all6(_tinted(name, tint))
    if k == "carpet":
        return all6(texture(name.replace("_carpet", "_wool")) if name != "moss_carpet" else texture("moss_block"))
    if k == "flower":
        if name in ("grass", "fern"):
            return all6(_tinted(name, GRASS_TINT))
        return all6(texture(name))
    if name.endswith("_log") or name.endswith("_wood") or name.endswith("_stem") or name.endswith("_hyphae"):
        base = name.replace("_wood", "_log").replace("_hyphae", "_stem")
        side = texture(base)
        if name.endswith("_wood") or name.endswith("_hyphae"):
            return all6(side)
        return column(side, texture(base + "_top"), props.get("axis", "y"))
    special = {
        "grass_block": lambda: [_tinted("grass_block_side", GRASS_TINT, rows=4)] * 2 + [_tinted("grass_block_top", GRASS_TINT),
                                                                                      texture("dirt")] + [_tinted("grass_block_side", GRASS_TINT, rows=4)] * 2,
        "dirt_path": lambda: [texture("dirt_path_side")] * 2 + [texture("dirt_path_top"), texture("dirt")] + [texture("dirt_path_side")] * 2,
        "smooth_sandstone": lambda: all6(texture("sandstone_top")),
        "smooth_quartz": lambda: all6(texture("quartz_block_bottom")),
        "smooth_red_sandstone": lambda: all6(texture("red_sandstone_top")),
        "quartz_block": lambda: all6(texture("quartz_block_side")),
        "sandstone": lambda: [texture("sandstone")] * 2 + [texture("sandstone_top"), texture("sandstone_bottom")] + [texture("sandstone")] * 2,
        "bookshelf": lambda: [texture("bookshelf")] * 2 + [texture("oak_planks")] * 2 + [texture("bookshelf")] * 2,
        "hay_block": lambda: [texture("hay_block_side")] * 2 + [texture("hay_block_top")] * 2 + [texture("hay_block_side")] * 2,
        "barrel": lambda: [texture("barrel_side")] * 2 + [texture("barrel_top"), texture("barrel_bottom")] + [texture("barrel_side")] * 2,
        "water": lambda: all6(_tinted("water_still", WATER_TINT)),
        "glass": lambda: all6(texture("glass")),
        "chiseled_quartz_block": lambda: [texture("chiseled_quartz_block")] * 2 + [texture("chiseled_quartz_block_top")] * 2 + [texture("chiseled_quartz_block")] * 2,
        "chiseled_stone_bricks": lambda: all6(texture("chiseled_stone_bricks")),
        "smooth_stone": lambda: all6(texture("smooth_stone")),
        "mud_bricks": lambda: all6(texture("mud_bricks")),
        "moss_block": lambda: all6(texture("moss_block")),
        "campfire": lambda: all6(texture("campfire_log")),
        "flower_pot": lambda: all6(texture("flower_pot")),
        "lectern": lambda: [texture("lectern_sides")] * 2 + [texture("lectern_top"), texture("oak_planks")] + [texture("lectern_front")] * 2,
        "cartography_table": lambda: [texture("cartography_table_side1")] * 2 + [texture("cartography_table_top"), texture("dark_oak_planks")] + [texture("cartography_table_side2")] * 2,
        "smoker": lambda: [texture("smoker_side")] * 2 + [texture("smoker_top"), texture("smoker_bottom")] + [texture("smoker_front")] * 2,
        "furnace": lambda: [texture("furnace_side")] * 2 + [texture("furnace_top")] * 2 + [texture("furnace_front")] * 2,
        "crafting_table": lambda: [texture("crafting_table_side")] * 2 + [texture("crafting_table_top"), texture("oak_planks")] + [texture("crafting_table_front")] * 2,
        "cauldron": lambda: [texture("cauldron_side")] * 2 + [texture("cauldron_top"), texture("cauldron_bottom")] + [texture("cauldron_side")] * 2,
        "quartz_pillar": lambda: [texture("quartz_pillar")] * 2 + [texture("quartz_pillar_top")] * 2 + [texture("quartz_pillar")] * 2,
        "tinted_glass": lambda: all6(texture("tinted_glass")),
        "smithing_table": lambda: [texture("smithing_table_side")] * 2 + [texture("smithing_table_top"),
                                   texture("smithing_table_bottom")] + [texture("smithing_table_front")] * 2,
        "grindstone": lambda: all6(texture("grindstone_side")),
        "anvil": lambda: [texture("anvil")] * 2 + [texture("anvil_top"), texture("anvil")] + [texture("anvil")] * 2,
        "chipped_anvil": lambda: [texture("anvil")] * 2 + [texture("chipped_anvil_top"), texture("anvil")] + [texture("anvil")] * 2,
        "damaged_anvil": lambda: [texture("anvil")] * 2 + [texture("damaged_anvil_top"), texture("anvil")] + [texture("anvil")] * 2,
        "water_cauldron": lambda: [texture("cauldron_side")] * 2 + [_tinted("water_still", WATER_TINT),
                                   texture("cauldron_bottom")] + [texture("cauldron_side")] * 2,
        "magma_block": lambda: all6(texture("magma")),
        "raw_iron_block": lambda: all6(texture("raw_iron_block")),
        "pumpkin": lambda: [texture("pumpkin_side")] * 2 + [texture("pumpkin_top")] * 2 + [texture("pumpkin_side")] * 2,
        "melon": lambda: [texture("melon_side")] * 2 + [texture("melon_top")] * 2 + [texture("melon_side")] * 2,
        "composter": lambda: [texture("composter_side")] * 2 + [texture("composter_top"),
                              texture("composter_bottom")] + [texture("composter_side")] * 2,
        "cake": lambda: [texture("cake_side")] * 2 + [texture("cake_top"), texture("cake_bottom")] + [texture("cake_side")] * 2,
    }
    if name == "loom":
        return _oriented(texture("loom_front"), texture("loom_side"), texture("loom_top"), texture("loom_bottom"),
                         props.get("facing", "north"))
    if name == "blast_furnace":
        front = texture("blast_furnace_front_on" if props.get("lit") == "true" else "blast_furnace_front")
        return _oriented(front, texture("blast_furnace_side"), texture("blast_furnace_top"), texture("blast_furnace_top"),
                         props.get("facing", "north"))
    if name in special:
        return special[name]()
    return all6(texture(name))


def _oriented(front, side, top, bottom, facing):
    """前の面を facing の向きに置いた6面（東・西・上・下・南・北）。"""
    faces = [side, side, top, bottom, side, side]
    faces[{"east": 0, "west": 1, "south": 4, "north": 5}.get(facing, 5)] = front
    return faces


def shape_mask(name, props):
    """4x4x4 の、埋まっている小さな箱（[x][y][z]）。"""
    k = kind(name)
    m = np.zeros((S, S, S), dtype=bool)
    h = S // 2
    f = props.get("facing", "north")
    if name.endswith("_wall_sign") or name.endswith("_wall_banner"):
        # 壁に貼る板：向きと反対の側（壁の側）に薄い板
        side = {"north": (slice(None), slice(S - 1, S)), "south": (slice(None), slice(0, 1)),
                "west": (slice(S - 1, S), slice(None)), "east": (slice(0, 1), slice(None))}[f]
        ys = slice(1, 3) if name.endswith("_wall_sign") else slice(0, S)
        m[side[0], ys, side[1]] = True
        return m
    if name.endswith("_hanging_sign"):
        if f in ("north", "south"):
            m[:, 0:3, 1:3] = True
        else:
            m[1:3, 0:3, :] = True
        return m
    if name.endswith("_bed"):
        m[:, 0:2, :] = True
        return m
    if name.endswith("candle"):
        m[1:3, 0:2, 1:3] = True
        return m
    if name == "bell":
        m[1:3, 0:3, 1:3] = True
        return m
    if name == "campfire":
        m[:, 0:2, :] = True
        return m
    if k in ("cube", "leaves", "glass"):
        m[:] = True
    elif k == "slab":
        t = props.get("type", "bottom")
        if t == "double":
            m[:] = True
        elif t == "top":
            m[:, h:, :] = True
        else:
            m[:, :h, :] = True
    elif k == "stairs":
        top = props.get("half") == "top"
        if top:
            m[:, h:, :] = True
            ys = slice(0, h)
        else:
            m[:, :h, :] = True
            ys = slice(h, S)
        facing = props.get("facing", "north")
        shape = props.get("shape", "straight")
        quads = _stair_quads(facing, shape)
        for qx, qz in quads:
            m[qx * h:(qx + 1) * h, ys, qz * h:(qz + 1) * h] = True
    elif k == "fence":
        c = 2
        m[c, :, c] = True
        for d, (sx, sz) in (("north", (c, slice(0, c))), ("south", (c, slice(c + 1, S))), ("west", (slice(0, c), c)),
                            ("east", (slice(c + 1, S), c))):
            if props.get(d) == "true":
                m[sx, 1, sz] = True
                m[sx, 3, sz] = True
    elif k == "gate":
        axis_x = props.get("facing") in ("north", "south")
        if axis_x:
            m[:, 1:4, 2] = True
        else:
            m[2, 1:4, :] = True
    elif k == "wall":
        if props.get("up") == "true":
            m[1:3, :, 1:3] = True
        for d, (sx, sz) in (("north", (slice(1, 3), slice(0, 2))), ("south", (slice(1, 3), slice(2, S))),
                            ("west", (slice(0, 2), slice(1, 3))), ("east", (slice(2, S), slice(1, 3)))):
            v = props.get(d, "none")
            if v != "none":
                m[sx, 0:(S if v == "tall" else 3), sz] = True
    elif k == "pane":
        c = 2
        m[c, :, c] = True
        for d, (sx, sz) in (("north", (c, slice(0, c))), ("south", (c, slice(c + 1, S))), ("west", (slice(0, c), c)),
                            ("east", (slice(c + 1, S), c))):
            if props.get(d) == "true":
                m[sx, :, sz] = True
    elif k == "trapdoor":
        if props.get("open") == "true":
            f = props.get("facing", "north")
            if f == "north":
                m[:, :, S - 1] = True
            elif f == "south":
                m[:, :, 0] = True
            elif f == "west":
                m[S - 1, :, :] = True
            else:
                m[0, :, :] = True
        elif props.get("half") == "top":
            m[:, S - 1, :] = True
        else:
            m[:, 0, :] = True
    elif k == "door":
        f = props.get("facing", "north")
        if f == "north":
            m[:, :, S - 1] = True
        elif f == "south":
            m[:, :, 0] = True
        elif f == "west":
            m[S - 1, :, :] = True
        else:
            m[0, :, :] = True
    elif k == "carpet":
        m[:, 0, :] = True
    elif k == "lantern":
        if props.get("hanging") == "true":
            m[1:3, 1:3, 1:3] = True
            m[2, 3, 2] = True
        else:
            m[1:3, 0:2, 1:3] = True
            m[2, 2, 2] = True
    elif k in ("chain", "rod", "torch"):
        m[2, :, 2] = True if k != "torch" else False
        if k == "torch":
            m[2, 0:3, 2] = True
    elif k == "flower":
        m[1:3, 0:2, 1:3] = True
        m[2, 2, 2] = True
    elif k == "button":
        m[1:3, 0, 1:3] = True
    elif k == "pot":
        m[1:3, 0:3, 1:3] = True
    elif k == "candle":
        m[2, 0:2, 2] = True
    else:
        m[:] = True
    return m


def _stair_quads(facing, shape):
    """階段の上（または下）の半分のうち、埋まる四分の一（qx, qz）。0 が西・北、1 が東・南。"""
    half = {"north": [(0, 0), (1, 0)], "south": [(0, 1), (1, 1)], "west": [(0, 0), (0, 1)], "east": [(1, 0), (1, 1)]}
    ccw = {"north": "west", "west": "south", "south": "east", "east": "north"}
    cw = {v: k for k, v in ccw.items()}
    opp = {"north": "south", "south": "north", "west": "east", "east": "west"}
    if shape == "straight":
        return half[facing]
    if shape in ("outer_left", "outer_right"):
        side = ccw[facing] if shape == "outer_left" else cw[facing]
        return [q for q in half[facing] if q in half[side]]
    side = ccw[facing] if shape == "inner_left" else cw[facing]
    extra = [q for q in half[opp[facing]] if q in half[side]]
    return half[facing] + extra


PIECE_FACES = ("west", "east", "down", "up", "north", "south")   # 2*軸 + (負の側 0 / 正の側 1)


def _face_st(face, p, frm, to):
    """模型の面の上の点 p（1/16 の単位）→ 面の上の位置 (s, t)。s は右へ、t は下へ 0〜1。"""
    def ratio(a, lo, hi):
        d = hi - lo
        return np.clip((a - lo) / d, 0.0, 1.0) if abs(d) > 1e-9 else np.full(a.shape, 0.5)
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    if face == "up":
        return ratio(x, frm[0], to[0]), ratio(z, frm[2], to[2])
    if face == "down":
        return ratio(x, frm[0], to[0]), 1.0 - ratio(z, frm[2], to[2])
    if face == "north":
        return 1.0 - ratio(x, frm[0], to[0]), 1.0 - ratio(y, frm[1], to[1])
    if face == "south":
        return ratio(x, frm[0], to[0]), 1.0 - ratio(y, frm[1], to[1])
    if face == "west":
        return ratio(z, frm[2], to[2]), 1.0 - ratio(y, frm[1], to[1])
    return 1.0 - ratio(z, frm[2], to[2]), 1.0 - ratio(y, frm[1], to[1])


def _rotate_st(s, t, rot):
    """面のテクスチャの回転（時計回り）。"""
    if rot == 90:
        return t, 1.0 - s
    if rot == 180:
        return 1.0 - s, 1.0 - t
    if rot == 270:
        return 1.0 - t, s
    return s, t


class Scene:
    """設計データを、光線で描ける形（小さな箱の格子）にする。"""

    def __init__(self, world, ground_y=None):
        self.world = world
        states = sorted(set(world.blocks.values()))
        self.states = [None] + states
        index = {s: i + 1 for i, s in enumerate(states)}
        n = len(self.states)
        self.tex = np.zeros((n, 6, 16, 16, 4), dtype=np.float32)
        self.emissive = np.zeros(n, dtype=bool)
        self.flat = np.zeros((n, 3), dtype=np.float32)
        self.has_flat = np.zeros(n, dtype=bool)
        masks = {}
        for i, state in enumerate(states, start=1):
            name, props = state[0], props_of(state)
            if name in SOLID_COLORS:
                base, glow, emissive = SOLID_COLORS[name]
                if props.get("lit") == "false":
                    glow, emissive = None, False       # 火のついていないろうそく
                color = np.array(glow if glow else base, dtype=np.float32) / 255.0
                self.flat[i] = color
                self.has_flat[i] = True
                self.emissive[i] = emissive
                self.tex[i, :, :, :, :3] = color
                self.tex[i, :, :, :, 3] = 1.0
            else:
                faces = face_textures(name, props)
                for f in range(6):
                    self.tex[i, f] = faces[f]
                if kind(name) == "flower":
                    # 花は小さな箱で描くので、テクスチャの色の平均で塗る
                    t = faces[0]
                    a = t[..., 3] > 0.5
                    color = t[..., :3][a].mean(axis=0) if a.any() else np.array([0.8, 0.2, 0.2])
                    self.tex[i, :, :, :, :3] = color
                    self.tex[i, :, :, :, 3] = 1.0
                if kind(name) in ("fence", "gate", "wall", "stairs", "slab", "door") or name.endswith("_log"):
                    # 透けない（ドアの窓の穴などは、形が粗いので塗りつぶす）
                    self.tex[i, :, :, :, 3] = np.maximum(self.tex[i, :, :, :, 3], 1.0)
            # 模型で描くもの（ランタン・看板・旗など）は、小さな箱の格子には入れない（_trace_pieces で描く）
            masks[i] = np.zeros((S, S, S), dtype=bool) if is_model_block(name) else shape_mask(name, props)
        self.cells = np.zeros((world.w * S, world.h * S, world.d * S), dtype=np.uint16)
        for (x, y, z), state in world.blocks.items():
            i = index[state]
            sub = self.cells[x * S:(x + 1) * S, y * S:(y + 1) * S, z * S:(z + 1) * S]
            sub[masks[i]] = i
        self.pieces = build_pieces(world)
        self._corners = np.array([p.corners for p in self.pieces]) if self.pieces else np.zeros((0, 8, 3))
        self.ground_y = (ground_y if ground_y is not None else 0) * S

    # ---------------------------------------------------------------- 光線
    def trace(self, o, d):
        """o, d：小さな箱の単位の始点と向き（N,3）。当たった t・軸・番号・面・テクスチャの位置を返す。"""
        n = o.shape[0]
        G = np.array(self.cells.shape, dtype=np.float64)
        d = np.where(np.abs(d) < 1e-9, 1e-9, d)
        inv = 1.0 / d
        t1 = (0.0 - o) * inv
        t2 = (G - o) * inv
        tmin_axis = np.minimum(t1, t2)
        tmin = tmin_axis.max(axis=1)
        tmax = np.maximum(t1, t2).min(axis=1)
        valid = tmax > np.maximum(tmin, 0.0)
        t_start = np.maximum(tmin, 0.0) + 1e-6
        entry_axis = tmin_axis.argmax(axis=1)
        p = o + d * t_start[:, None]
        cell = np.clip(np.floor(p).astype(np.int64), 0, (G - 1).astype(np.int64))
        step = np.where(d > 0, 1, -1).astype(np.int64)
        t_delta = np.abs(inv)
        t_max = (cell + (step > 0)) * 1.0
        t_max = (t_max - o) * inv
        hit = np.zeros(n, dtype=bool)
        hit_t = np.full(n, np.inf)
        hit_axis = np.zeros(n, dtype=np.int64)
        hit_id = np.zeros(n, dtype=np.int64)
        hit_face = np.zeros(n, dtype=np.int64)
        hit_uv = np.zeros((n, 2), dtype=np.int64)
        t_enter = t_start.copy()
        last_axis = np.where(tmin > 0, entry_axis, np.abs(d).argmax(axis=1))
        active = np.nonzero(valid)[0]
        cells = self.cells
        gx, gy, gz = cells.shape
        while active.size:
            c = cell[active]
            ids = cells[c[:, 0], c[:, 1], c[:, 2]]
            occ = ids > 0
            if occ.any():
                idx = active[occ]
                ids_o = ids[occ].astype(np.int64)
                ax = last_axis[idx]
                st = step[idx, ax]
                face = np.where(ax == 0, np.where(st < 0, 0, 1), np.where(ax == 1, np.where(st < 0, 2, 3), np.where(st < 0, 4, 5)))
                ph = (o[idx] + d[idx] * t_enter[idx][:, None]) / S
                fr = ph - np.floor(ph)
                u = np.where(ax == 1, fr[:, 0], np.where(ax == 0, fr[:, 2], fr[:, 0]))
                v = np.where(ax == 1, fr[:, 2], 1.0 - fr[:, 1])
                ui = np.clip((u * 16).astype(np.int64), 0, 15)
                vi = np.clip((v * 16).astype(np.int64), 0, 15)
                alpha = self.tex[ids_o, face, vi, ui, 3]
                opaque = alpha >= 0.5
                if opaque.any():
                    j = idx[opaque]
                    hit[j] = True
                    hit_t[j] = t_enter[j]
                    hit_axis[j] = ax[opaque]
                    hit_id[j] = ids_o[opaque]
                    hit_face[j] = face[opaque]
                    hit_uv[j, 0] = ui[opaque]
                    hit_uv[j, 1] = vi[opaque]
                    keep = np.ones(active.size, dtype=bool)
                    keep[np.nonzero(occ)[0][opaque]] = False
                    active = active[keep]
            if not active.size:
                break
            a = active
            ax = t_max[a].argmin(axis=1)
            t_enter[a] = t_max[a, ax]
            cell[a, ax] += step[a, ax]
            t_max[a, ax] += t_delta[a, ax]
            last_axis[a] = ax
            ca = cell[a]
            out = (ca[:, 0] < 0) | (ca[:, 0] >= gx) | (ca[:, 1] < 0) | (ca[:, 1] >= gy) | (ca[:, 2] < 0) | (ca[:, 2] >= gz)
            active = a[~out]
        return hit, hit_t, hit_axis, hit_id, hit_face, hit_uv

    # ---------------------------------------------------------------- 模型の部品
    def _trace_pieces(self, eye_b, dirs, depth, width, height, fwd, right, up, tan, aspect):
        """模型の箱（ランタン・看板・旗・額縁など）を、手前にあるものだけ描く。ブロックの単位。"""
        n = dirs.shape[0]
        det = np.zeros(n, dtype=bool)
        col_out = np.zeros((n, 3), dtype=np.float32)
        nrm_out = np.zeros((n, 3), dtype=np.float64)
        emis_out = np.zeros(n, dtype=bool)
        shade_out = np.ones(n, dtype=bool)
        depth = depth.copy()
        if not self.pieces:
            return det, depth, col_out, nrm_out, emis_out, shade_out
        v = self._corners - eye_b
        zc = v @ fwd
        xc = v @ right
        yc = v @ up
        for i, piece in enumerate(self.pieces):
            zi = zc[i]
            if (zi <= 0.05).all():
                continue
            if (zi > 0.05).all():
                sx = (xc[i] / (zi * tan * aspect) + 1.0) * 0.5 * width
                sy = (1.0 - yc[i] / (zi * tan)) * 0.5 * height
                x0 = max(int(math.floor(sx.min())) - 1, 0)
                x1 = min(int(math.ceil(sx.max())) + 1, width)
                y0 = max(int(math.floor(sy.min())) - 1, 0)
                y1 = min(int(math.ceil(sy.max())) + 1, height)
            else:
                x0, y0, x1, y1 = 0, 0, width, height
            if x0 >= x1 or y0 >= y1:
                continue
            gy, gx = np.mgrid[y0:y1, x0:x1]
            idx = (gy * width + gx).ravel()
            near = float(zi.min())
            idx = idx[depth[idx] > near - 0.05]
            if not idx.size:
                continue
            o_l = piece.Ainv @ (eye_b - piece.b)
            d_l = dirs[idx] @ piece.Ainv.T
            d_l = np.where(np.abs(d_l) < 1e-12, 1e-12, d_l)
            inv = 1.0 / d_l
            t1 = (-1.0 - o_l) * inv
            t2 = (1.0 - o_l) * inv
            tmn = np.minimum(t1, t2)
            tmx = np.maximum(t1, t2)
            tn = tmn.max(axis=1)
            tf = tmx.min(axis=1)
            ok = (tf >= tn) & (tn > 1e-4) & (tn < depth[idx])
            if not ok.any():
                continue
            sel = np.nonzero(ok)[0]
            k = tmn[sel].argmax(axis=1)
            neg = d_l[sel, k] > 0
            L = o_l + d_l[sel] * tn[sel, None]
            half = np.maximum((piece.to - piece.frm) / 2.0, 0.002)
            pm = (piece.frm + piece.to) / 2.0 + L * half
            fidx = 2 * k + np.where(neg, 0, 1)
            hit_ok = np.zeros(sel.size, dtype=bool)
            col = np.zeros((sel.size, 3), dtype=np.float32)
            emis = np.zeros(sel.size, dtype=bool)
            for fi in np.unique(fidx):
                fname = PIECE_FACES[fi]
                face = piece.faces.get(fname)
                if face is None:
                    continue
                m = np.nonzero(fidx == fi)[0]
                tex, uv, rot, tint, em = face
                s_, t_ = _face_st(fname, pm[m], piece.frm, piece.to)
                s_, t_ = _rotate_st(s_, t_, rot)
                u = uv[0] + s_ * (uv[2] - uv[0])
                w_ = uv[1] + t_ * (uv[3] - uv[1])
                th, tw = tex.shape[0], tex.shape[1]
                ui = np.clip(np.floor(u * tw / 16.0).astype(np.int64), 0, tw - 1)
                vi = np.clip(np.floor(w_ * th / 16.0).astype(np.int64), 0, th - 1)
                texel = tex[vi, ui]
                a = texel[:, 3] >= 0.5
                rgb = texel[:, :3] * (tint if tint is not None else 1.0)
                hit_ok[m[a]] = True
                col[m[a]] = rgb[a]
                emis[m[a]] = em
            if not hit_ok.any():
                continue
            g = idx[sel[hit_ok]]
            depth[g] = tn[sel[hit_ok]]
            det[g] = True
            col_out[g] = col[hit_ok]
            kk = k[hit_ok]
            nl = np.zeros((kk.size, 3))
            nl[np.arange(kk.size), kk] = np.where(neg[hit_ok], -1.0, 1.0)
            nw = nl @ piece.Ainv
            nw /= np.maximum(np.linalg.norm(nw, axis=1, keepdims=True), 1e-9)
            nrm_out[g] = nw
            emis_out[g] = emis[hit_ok]
            shade_out[g] = piece.shade
        return det, depth, col_out, nrm_out, emis_out, shade_out

    # ---------------------------------------------------------------- 絵
    def render(self, eye, target, width=1280, height=720, fov=55.0, sun=(-0.45, 0.8, 0.55), shadows=True, path=None):
        """eye・target はブロックの単位。絵（PIL.Image）を返す。"""
        eye = np.array(eye, dtype=np.float64) * S
        target = np.array(target, dtype=np.float64) * S
        forward = target - eye
        forward /= np.linalg.norm(forward)
        right = np.cross(forward, np.array([0.0, 1.0, 0.0]))
        right /= np.linalg.norm(right)
        up = np.cross(right, forward)
        tan = math.tan(math.radians(fov) / 2.0)
        aspect = width / height
        xs = (np.arange(width) + 0.5) / width * 2.0 - 1.0
        ys = 1.0 - (np.arange(height) + 0.5) / height * 2.0
        px, py = np.meshgrid(xs * tan * aspect, ys * tan)
        dirs = forward[None, None, :] + px[..., None] * right[None, None, :] + py[..., None] * up[None, None, :]
        dirs = dirs.reshape(-1, 3)
        dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
        origins = np.repeat(eye[None, :], dirs.shape[0], axis=0)

        hit, t, axis, ids, face, uv = self.trace(origins, dirs)
        n = dirs.shape[0]
        # 地面の平らな面より下で当たったもの（形の外から、地面の下に回りこんだ光線）は、地面に当たったことにする
        going_down = dirs[:, 1] < -1e-4
        t_plane = np.where(going_down, (self.ground_y - origins[:, 1]) / np.where(going_down, dirs[:, 1], -1.0), np.inf)
        under = hit & going_down & (t > t_plane + 0.05)
        hit = hit & ~under
        # 模型の部品（ランタン・看板・旗・額縁など）：ブロックや地面より手前にあるものを描く
        eye_b = eye / S
        depth = np.full(n, np.inf)
        depth[hit] = t[hit] / S
        ground_pix = ~hit & going_down
        depth[ground_pix] = t_plane[ground_pix] / S
        det, det_t, det_col, det_nrm, det_emis, det_shade = self._trace_pieces(
            eye_b, dirs, depth, width, height, forward, right, up, tan, aspect)
        hit = hit & ~det
        color = np.zeros((n, 3), dtype=np.float32)
        # 空：地平線は明るく、上は青く
        sky_t = np.clip(dirs[:, 1] * 1.6, 0.0, 1.0)[:, None]
        sky = (1 - sky_t) * np.array([0.78, 0.86, 0.95]) + sky_t * np.array([0.42, 0.62, 0.92])
        color[:] = sky
        # 地面（形の外）：草
        miss = ~hit & ~det
        down = miss & (dirs[:, 1] < -1e-4)
        if down.any():
            tg = (self.ground_y - origins[down, 1]) / dirs[down, 1]
            gp = origins[down] + dirs[down] * tg[:, None]
            grass = _tinted("grass_block_top", GRASS_TINT)
            gu = (np.floor(gp[:, 0] / S * 16) % 16).astype(np.int64)
            gv = (np.floor(gp[:, 2] / S * 16) % 16).astype(np.int64)
            gcol = grass[gv, gu, :3] * 0.95
            dist = tg / S
            fog = np.clip((dist - 40.0) / 160.0, 0.0, 0.85)[:, None]
            color[down] = gcol * (1 - fog) + sky[down] * fog

        if hit.any():
            hi = np.nonzero(hit)[0]
            texel = self.tex[ids[hi], face[hi], uv[hi, 1], uv[hi, 0], :3]
            light = FACE_LIGHT[face[hi]].copy()
            if shadows:
                sun_v = np.array(sun, dtype=np.float64)
                sun_v /= np.linalg.norm(sun_v)
                normals = np.zeros((hi.size, 3))
                ax = axis[hi]
                normal_sign = np.where(np.isin(face[hi], (0, 2, 4)), 1.0, -1.0)
                normals[np.arange(hi.size), ax] = normal_sign
                lit_side = (normals @ sun_v) > 0
                p = origins[hi] + dirs[hi] * t[hi, None] + normals * 0.02
                shadow_hit = np.zeros(hi.size, dtype=bool)
                cand = np.nonzero(lit_side)[0]
                if cand.size:
                    sh, _, _, _, _, _ = self.trace(p[cand], np.repeat(sun_v[None, :], cand.size, axis=0))
                    shadow_hit[cand] = sh
                sunlit = lit_side & ~shadow_hit
                light = light * np.where(sunlit, 1.0, 0.68)
            emissive = self.emissive[ids[hi]]
            shade = np.where(emissive[:, None], 1.0, light[:, None])
            col = texel * shade
            dist = t[hi] / S
            fog = np.clip((dist - 60.0) / 200.0, 0.0, 0.6)[:, None]
            color[hi] = col * (1 - fog) + sky[hi] * fog
        if det.any():
            di = np.nonzero(det)[0]
            nw = det_nrm[di]
            ax = np.abs(nw).argmax(axis=1)
            pos = nw[np.arange(di.size), ax] > 0
            fid = np.where(ax == 0, np.where(pos, 0, 1), np.where(ax == 1, np.where(pos, 2, 3), np.where(pos, 4, 5)))
            light = np.where(det_shade[di], FACE_LIGHT[fid], 0.9).astype(np.float64)
            if shadows:
                sun_v = np.array(sun, dtype=np.float64)
                sun_v /= np.linalg.norm(sun_v)
                lit_side = ((nw @ sun_v) > 0) | ~det_shade[di]
                p = (eye_b + dirs[di] * det_t[di, None] + nw * 0.01) * S
                shadow_hit = np.zeros(di.size, dtype=bool)
                cand = np.nonzero(lit_side)[0]
                if cand.size:
                    sh, _, _, _, _, _ = self.trace(p[cand], np.repeat(sun_v[None, :], cand.size, axis=0))
                    shadow_hit[cand] = sh
                light = light * np.where(lit_side & ~shadow_hit, 1.0, 0.68)
            shade = np.where(det_emis[di][:, None], 1.0, light[:, None])
            col = det_col[di] * shade
            fog = np.clip((det_t[di] - 60.0) / 200.0, 0.0, 0.6)[:, None]
            color[di] = col * (1 - fog) + sky[di] * fog
        img = (np.clip(color, 0, 1) * 255).astype(np.uint8).reshape(height, width, 3)
        image = Image.fromarray(img, "RGB")
        if path:
            os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
            image.save(path)
        return image
