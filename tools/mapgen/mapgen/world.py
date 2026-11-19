"""マップの設計データ（ブロックの置き場所）と、構造物ファイル（.nbt）への書き出し。

座標：x は東、y は上、z は南。ブロックは (名前, 性質) の組で表す（名前に minecraft: は付けない）。
柵・塀・板ガラスのつながりと、階段の角の形は、書き出す前に finalize() で周りを見て決める
（構造物として置くと、ゲームは中のブロックの形を計算し直さないため）。
"""
import gzip
import io
import math
import os

import nbtlib
from nbtlib.tag import Byte, Compound, Double, Float, Int, List, String

DATA_VERSION = 3465  # Minecraft 1.20.1

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
CW = {v: k for k, v in CCW.items()}


def block(name, **props):
    """ブロックの状態。性質は文字列にそろえる。"""
    return (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))


def props_of(state):
    return dict(state[1])


# ---------------------------------------------------------------- よく使うブロック
def stairs(mat, facing, half="bottom"):
    """階段。facing は高い側（背）の向き。屋根なら棟の方を向ける。"""
    return block(mat + "_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(mat, kind="bottom"):
    return block(mat + "_slab", type=kind, waterlogged=False)


def log(wood, axis="y", stripped=False):
    return block(("stripped_" if stripped else "") + wood + "_log", axis=axis)


def wood_block(wood, axis="y", stripped=False):
    return block(("stripped_" if stripped else "") + wood + "_wood", axis=axis)


def fence(wood):
    return block(wood + "_fence", north=False, south=False, east=False, west=False, waterlogged=False)


def wall(mat):
    return block(mat + "_wall", north="none", south="none", east="none", west="none", up=True, waterlogged=False)


def pane(name="glass_pane"):
    return block(name, north=False, south=False, east=False, west=False, waterlogged=False)


def trapdoor(wood, facing, half="bottom", open_=False):
    return block(wood + "_trapdoor", facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def lantern(hanging=False):
    return block("lantern", hanging=hanging, waterlogged=False)


def leaves(name):
    return block(name, distance=1, persistent=True, waterlogged=False)


def door(wood, facing, upper, hinge="left"):
    return block(wood + "_door", facing=facing, half="upper" if upper else "lower", hinge=hinge, open=False, powered=False)


AIR = block("air")


# ---------------------------------------------------------------- 追加データ（看板の文字・旗の模様）
def sign_nbt(front, back=None, color="black", glow=False, hanging=False):
    """看板の文字（1.20.1 の形）。front・back は4行までの文字列のリスト。"""
    import json
    from nbtlib.tag import Byte

    def side(lines):
        lines = (list(lines) + ["", "", "", ""])[:4]
        return Compound({
            "messages": List[String]([String(json.dumps({"text": t}, ensure_ascii=False)) for t in lines]),
            "color": String(color),
            "has_glowing_text": Byte(1 if glow else 0),
        })
    return Compound({
        "id": String("minecraft:hanging_sign" if hanging else "minecraft:sign"),
        "front_text": side(front),
        "back_text": side(back if back is not None else []),
        "is_waxed": Byte(1),
    })


DYE = {"white": 0, "orange": 1, "magenta": 2, "light_blue": 3, "yellow": 4, "lime": 5, "pink": 6, "gray": 7,
       "light_gray": 8, "cyan": 9, "purple": 10, "blue": 11, "brown": 12, "green": 13, "red": 14, "black": 15}


def banner_nbt(patterns):
    """旗の模様。patterns は (模様の記号, 色の名前) のリスト（例：("bo", "white") は白い縁）。"""
    return Compound({
        "id": String("minecraft:banner"),
        "Patterns": List[Compound]([Compound({"Pattern": String(p), "Color": Int(DYE[c])}) for p, c in patterns]),
    })

# ---------------------------------------------------------------- もの（エンティティ）：額縁と防具立て
FACING_ID = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
STEP = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
# 防具立ての向き（体の正面の向き → ゲームの Rotation の角度。0 が南、90 が西、180 が北、-90 が東）
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}


def item_stack(item, count=1, name=None, color=None, base=None, patterns=None):
    """アイテム（1.20.1 の形）。name を渡すと、その名前を付ける（斜体にしない）。

    color は革の防具の色（0xRRGGBB）。base・patterns は盾の地の色と模様（旗と同じ記号。例：[("bo", "black")]）。
    item に (名前, {"color": ...}) の組を渡してもよい（防具立てと額縁から使う）。
    """
    import json
    if isinstance(item, tuple):
        item, opts = item
        return item_stack(item, count, name=opts.get("name", name), color=opts.get("color"), base=opts.get("base"),
                          patterns=opts.get("patterns"))
    c = Compound({"id": String("minecraft:" + item), "Count": Byte(count)})
    tag = Compound({})
    display = Compound({})
    if name:
        display["Name"] = String(json.dumps({"text": name, "italic": False}, ensure_ascii=False))
    if color is not None:
        display["color"] = Int(int(color))
    if len(display):
        tag["display"] = display
    if base is not None or patterns:
        bet = Compound({"Base": Int(DYE[base or "white"])})
        bet["Patterns"] = List[Compound]([Compound({"Pattern": String(p), "Color": Int(DYE[col])})
                                          for p, col in (patterns or [])])
        tag["BlockEntityTag"] = bet
    if len(tag):
        c["tag"] = tag
    return c


def item_id(item):
    """item（名前、または (名前, 追加) の組）→ 名前。"""
    return item[0] if isinstance(item, tuple) else item


class Entity:
    """構造物に入れる「もの」。pos は構造物の中の位置（小数）、cell はそのマス、nbt はゲームの形のデータ。
    kind・facing・item は見本の絵と確かめの道具で使う。"""

    def __init__(self, kind_, pos, cell, nbt, facing=None, item=None, items=()):
        self.kind, self.pos, self.cell, self.nbt = kind_, pos, cell, nbt
        self.facing, self.item, self.items = facing, item, tuple(items)


# 形のあるブロックの見分け（つながりの計算と、見本の絵で使う）
def kind(name):
    if name == "air":
        return "air"
    if name.endswith("_stairs"):
        return "stairs"
    if name.endswith("_slab"):
        return "slab"
    if name.endswith("_fence_gate"):
        return "gate"
    if name.endswith("_fence"):
        return "fence"
    if name.endswith("_wall") and not name.endswith("_wall_torch") and not name.endswith("_wall_banner") and not name.endswith("_wall_sign"):
        return "wall"
    if name.endswith("_pane") or name == "iron_bars":
        return "pane"
    if name.endswith("_trapdoor"):
        return "trapdoor"
    if name.endswith("_door"):
        return "door"
    if name.endswith("_carpet") or name == "moss_carpet":
        return "carpet"
    if name in ("lantern", "soul_lantern"):
        return "lantern"
    if name in ("chain",):
        return "chain"
    if name in ("end_rod", "lightning_rod"):
        return "rod"
    if name.endswith("_leaves"):
        return "leaves"
    if name in ("glass",) or name.endswith("_stained_glass"):
        return "glass"
    if name in FLOWERS:
        return "flower"
    if name in ("torch", "wall_torch"):
        return "torch"
    if name.endswith("_button"):
        return "button"
    if name.startswith("potted_") or name == "flower_pot":
        return "pot"
    if name == "candle" or name.endswith("_candle"):
        return "candle"
    return "cube"


FLOWERS = {"poppy", "dandelion", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "blue_orchid",
           "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "grass", "fern", "sweet_berry_bush"}

class World:
    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.blocks = {}
        self.nbt = {}     # ブロックの追加データ（看板の文字・旗の模様など）。(x, y, z) → nbtlib の Compound
        self.entities = []  # もの（額縁・防具立て）。Entity のリスト

    # ---- 置く・消す
    def inside(self, x, y, z):
        return 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d

    def put(self, x, y, z, state, nbt=None):
        if self.inside(x, y, z):
            self.nbt.pop((x, y, z), None)
            if state[0] == "air":
                self.blocks.pop((x, y, z), None)
            else:
                self.blocks[(x, y, z)] = state
                if nbt is not None:
                    self.nbt[(x, y, z)] = nbt

    def put_if_empty(self, x, y, z, state):
        if (x, y, z) not in self.blocks:
            self.put(x, y, z, state)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def name_at(self, x, y, z):
        state = self.blocks.get((x, y, z))
        return state[0] if state else "air"

    def clear(self, x, y, z):
        self.blocks.pop((x, y, z), None)
        self.nbt.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, state)

    def clear_box(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.clear(x, y, z)

    # ---- もの（エンティティ）
    def item_frame(self, x, y, z, facing, item=None, rotation=0, name=None, glow=False):
        """額縁。(x, y, z) は額縁のあるマス（支えるブロックの手前の空いたマス）。facing は額縁の表が向く方
        （支えるブロックから離れる向き。台の上に寝かせるなら "up"）。固定（Fixed）なので、壊れず、中身も取れない。"""
        sx, sy, sz = STEP[facing]
        pos = (x + 0.5 - sx * 0.46875, y + 0.5 - sy * 0.46875, z + 0.5 - sz * 0.46875)
        nbt = Compound({
            "id": String("minecraft:glow_item_frame" if glow else "minecraft:item_frame"),
            "Facing": Byte(FACING_ID[facing]),
            "ItemRotation": Byte(rotation % 8),
            "ItemDropChance": Float(0.0),
            "Fixed": Byte(1),
            "Invisible": Byte(0),
            "Invulnerable": Byte(1),
            "TileX": Int(x), "TileY": Int(y), "TileZ": Int(z),
        })
        if item:
            nbt["Item"] = item_stack(item, name=name)
        self.entities.append(Entity("item_frame", pos, (x, y, z), nbt, facing=facing, item=item_id(item) if item else None))

    def armor_stand(self, x, y, z, facing="south", hand=None, offhand=None, head=None, chest=None, legs=None,
                    feet=None, small=False, invisible=False, base=True, pose=None, name=None):
        """防具立て。(x, y, z) は立つマス（下のブロックの上）。facing は体の正面の向き。
        手と防具は取れず（DisabledSlots）、壊れない（Invulnerable）。pose は {"RightArm": (x, y, z)} の形の角度。"""
        empty = Compound({})

        def st(item, nm=None):
            return item_stack(item, name=nm) if item else empty
        nbt = Compound({
            "id": String("minecraft:armor_stand"),
            "ShowArms": Byte(1),
            "NoBasePlate": Byte(0 if base else 1),
            "Small": Byte(1 if small else 0),
            "Invisible": Byte(1 if invisible else 0),
            "Invulnerable": Byte(1),
            "NoGravity": Byte(1),
            "DisabledSlots": Int(4144959),
            "Rotation": List[Float]([Float(YAW[facing]), Float(0.0)]),
            "HandItems": List[Compound]([st(hand, name), st(offhand)]),
            "ArmorItems": List[Compound]([st(feet), st(legs), st(chest), st(head)]),
        })
        if pose:
            nbt["Pose"] = Compound({k: List[Float]([Float(a) for a in v]) for k, v in pose.items()})
        self.entities.append(Entity("armor_stand", (x + 0.5, float(y), z + 0.5), (x, y, z), nbt, facing=facing,
                                    item=item_id(hand) if hand else None,
                                    items=[item_id(i) for i in (hand, offhand, head, chest, legs, feet) if i]))

    def entity_cells(self):
        return {e.cell for e in self.entities}

    # ---- 仕上げ：つながりと階段の角
    def _is_full(self, x, y, z):
        state = self.blocks.get((x, y, z))
        if state is None:
            return False
        k = kind(state[0])
        if k == "cube":
            return True
        if k == "slab" and props_of(state).get("type") == "double":
            return True
        return False

    def finalize(self):
        updates = {}
        for (x, y, z), state in self.blocks.items():
            k = kind(state[0])
            if k in ("fence", "wall", "pane"):
                updates[(x, y, z)] = self._connect(x, y, z, state, k)
            elif k == "stairs":
                updates[(x, y, z)] = self._stair_shape(x, y, z, state)
            elif state[0] == "dirt_path":
                # 上にふさぐブロックがある土の道は、置いたときにゲームが土に変えるので、先に土にしておく
                above = self.blocks.get((x, y + 1, z))
                if above is not None and kind(above[0]) not in ("air", "carpet", "flower", "lantern", "gate", "chain",
                                                                 "torch", "button", "candle", "pot"):
                    updates[(x, y, z)] = block("dirt")
        self.blocks.update(updates)

    def _connects_to(self, k, own_name, nx, ny, nz):
        other = self.blocks.get((nx, ny, nz))
        if other is None:
            return False
        ok = kind(other[0])
        if ok == "cube":
            return True
        if k == "fence":
            if ok == "fence":
                # 木の柵どうし、ネザーレンガの柵どうしだけがつながる
                return ("nether_brick" in other[0]) == ("nether_brick" in own_name)
            return ok == "gate"
        if k == "wall":
            return ok in ("wall", "pane", "gate")
        if k == "pane":
            return ok in ("pane", "wall")
        return False

    def _connect(self, x, y, z, state, k):
        props = props_of(state)
        links = {}
        for direction, (dx, _, dz) in DIRS.items():
            links[direction] = self._connects_to(k, state[0], x + dx, y, z + dz)
        if k == "wall":
            above = self.blocks.get((x, y + 1, z))
            for direction in DIRS:
                if links[direction]:
                    props[direction] = "tall" if above is not None and kind(above[0]) in ("cube", "wall") else "low"
                else:
                    props[direction] = "none"
            straight = (links["north"] and links["south"] and not links["east"] and not links["west"]) or \
                       (links["east"] and links["west"] and not links["north"] and not links["south"])
            above_post = above is not None and kind(above[0]) in ("wall", "lantern", "rod", "torch", "chain", "fence")
            props["up"] = "true" if (not straight or above_post) else "false"
        else:
            for direction in DIRS:
                props[direction] = "true" if links[direction] else "false"
        return (state[0], tuple(sorted(props.items())))

    def _stair_at(self, x, y, z):
        state = self.blocks.get((x, y, z))
        if state is not None and kind(state[0]) == "stairs":
            return props_of(state)
        return None

    def _stair_shape(self, x, y, z, state):
        """ゲームの StairBlock.getStairsShape と同じ決まり。"""
        p = props_of(state)
        facing, half = p["facing"], p["half"]

        def can_take(direction):
            dx, _, dz = DIRS[direction]
            other = self._stair_at(x + dx, y, z + dz)
            return other is None or other["facing"] != facing or other["half"] != half

        shape = "straight"
        dx, _, dz = DIRS[facing]
        front = self._stair_at(x + dx, y, z + dz)
        if front is not None and front["half"] == half and _axis(front["facing"]) != _axis(facing) \
                and can_take(OPPOSITE[front["facing"]]):
            shape = "outer_left" if front["facing"] == CCW[facing] else "outer_right"
        else:
            bx, _, bz = DIRS[OPPOSITE[facing]]
            back = self._stair_at(x + bx, y, z + bz)
            if back is not None and back["half"] == half and _axis(back["facing"]) != _axis(facing) and can_take(back["facing"]):
                shape = "inner_left" if back["facing"] == CCW[facing] else "inner_right"
        p["shape"] = shape
        return (state[0], tuple(sorted(p.items())))

    # ---- 書き出し
    def bounds(self):
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        return (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))

    def save_structure(self, path, with_air=True):
        """構造物ファイル（.nbt）に書き出す。with_air なら、置いたときに範囲の中を片づける。"""
        palette, index = [], {}

        def pidx(state):
            if state not in index:
                index[state] = len(palette)
                palette.append(state)
            return index[state]

        entries = []
        if with_air:
            pidx(AIR)
        for x in range(self.w):
            for y in range(self.h):
                for z in range(self.d):
                    state = self.blocks.get((x, y, z))
                    if state is None:
                        if not with_air:
                            continue
                        state = AIR
                    entry = {"state": Int(pidx(state)), "pos": List[Int]([Int(x), Int(y), Int(z)])}
                    if (x, y, z) in self.nbt:
                        entry["nbt"] = self.nbt[(x, y, z)]
                    entries.append(Compound(entry))
        pal = []
        for name, props in palette:
            c = {"Name": String("minecraft:" + name)}
            if props:
                c["Properties"] = Compound({k: String(v) for k, v in props})
            pal.append(Compound(c))
        root = nbtlib.File({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(self.w), Int(self.h), Int(self.d)]),
            "palette": List[Compound](pal),
            "blocks": List[Compound](entries),
            "entities": List[Compound]([
                Compound({
                    "pos": List[Double]([Double(v) for v in e.pos]),
                    "blockPos": List[Int]([Int(v) for v in e.cell]),
                    "nbt": e.nbt,
                }) for e in self.entities]),
        }, gzipped=True)
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        # 圧縮の時刻を 0 にして、同じ設計からは同じファイルができるようにする（リポジトリの差分を出さないため）
        raw = io.BytesIO()
        root.write(raw)
        with open(path, "wb") as f, gzip.GzipFile(filename="", mode="wb", fileobj=f, mtime=0) as g:
            g.write(raw.getvalue())
        return len(palette)


def _axis(direction):
    return "x" if direction in ("east", "west") else "z"


def turn(direction, quarter):
    """向きを、時計回りに quarter 回（90度ずつ）回す。"""
    for _ in range(quarter % 4):
        direction = CW[direction]
    return direction


def dist2(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])
