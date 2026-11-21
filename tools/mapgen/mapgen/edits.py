"""ゲームの中で直して書き出した所（/amemaru admin city export）を、構造物ファイルに重ねる。

直しは tools/mapgen/edits/<構造物>.json に置く（import_edits.py で取り込む）。World.save_structure が書き出すときに重ねるので、
build_*.py で作り直しても、直した所が残る。ファイルの形は、ゲームが書き出す（ワールドの generated/amemaru/city/edits/）ものと同じ：

  {"format": "amemaru-city-edits", "version": 1, "structure": "guild_hall", "facility": "guild", "dataVersion": 3465,
   "cells": [{"pos": [x, y, z], "state": "minecraft:oak_stairs[facing=north,...]", "nbt": "{...}"}, ...],
   "entityCells": [{"cell": [x, y, z], "entities": [{"pos": [x, y, z], "nbt": "{...}"}, ...]}, ...]}

・cells：そのマスのブロック（nbt は看板の文字などの中身。SNBT）。構造物の中の位置。空気でも書く（そのマスを空にする）。
・entityCells：そのマスにいる「もの」（額縁・防具立てなど）を、entities に入れ替える（空なら、そのマスの「もの」を消す）。
  pos は構造物の中の位置（小数）、nbt はゲームの形（SNBT）。
"""
import json
import math
import os
import re
import sys

import nbtlib

FORMAT = "amemaru-city-edits"
VERSION = 1
# 直しの置き場所（確かめるときは、環境変数 MAPGEN_EDITS_DIR で別の所にできる）
EDITS_DIR = os.environ.get("MAPGEN_EDITS_DIR") or os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "edits")

_STATE = re.compile(r"^(?:([a-z0-9_.-]+):)?([a-z0-9_/.-]+)(?:\[([a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*)?\])?$")
# 書き出せる「もの」（ゲームの CityEdits が見るもの）
ENTITY_IDS = {"minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:armor_stand",
              "minecraft:block_display", "minecraft:item_display", "minecraft:text_display"}


def parse_state(text):
    """"minecraft:oak_stairs[facing=north]" → ("oak_stairs", (("facing", "north"),))。minecraft 以外は名前に名前空間を残す。"""
    m = _STATE.match(text)
    if not m:
        raise ValueError("ブロックの書き方が違います: " + text)
    ns, name, props = m.group(1) or "minecraft", m.group(2), m.group(3)
    if ns != "minecraft":
        name = ns + ":" + name
    pairs = tuple(sorted(tuple(p.split("=", 1)) for p in props.split(","))) if props else ()
    return (name, pairs)


def state_text(state):
    name, props = state
    full = name if ":" in name else "minecraft:" + name
    return full + ("[" + ",".join(k + "=" + v for k, v in props) + "]" if props else "")


def parse_snbt(text, what):
    value = nbtlib.parse_nbt(text)
    if not isinstance(value, nbtlib.Compound):
        raise ValueError(what + "の中身が {…} ではありません")
    return value


def read(path):
    """直しのファイルを読んで確かめる。→ {"structure", "facility", "cells": {(x,y,z): (state, nbt|None)},
    "entities": {(x,y,z): [(pos, nbt), ...]}}。おかしな所があれば ValueError。"""
    with open(path, encoding="utf-8") as f:
        root = json.load(f)
    if not isinstance(root, dict) or root.get("format") != FORMAT or root.get("version") != VERSION:
        raise ValueError("直しのファイルの形が違います（format・version）")
    name = root.get("structure")
    if not isinstance(name, str) or not re.fullmatch(r"[a-z0-9_]+", name):
        raise ValueError("構造物の名前がありません")
    cells = {}
    for cell in root.get("cells", []):
        pos = _ints(cell.get("pos"), "pos")
        state = parse_state(cell["state"])
        nbt = parse_snbt(cell["nbt"], "ブロック") if "nbt" in cell else None
        cells[pos] = (state, nbt)
    entities = {}
    for cell in root.get("entityCells", []):
        c = _ints(cell.get("cell"), "cell")
        items = []
        for one in cell.get("entities", []):
            pos = tuple(float(v) for v in one["pos"])
            if len(pos) != 3 or not all(math.isfinite(v) for v in pos):
                raise ValueError("「もの」の位置がおかしい: %r" % (one["pos"],))
            if tuple(math.floor(v) for v in pos) != c:
                raise ValueError("「もの」の位置 %r がマス %r の中にありません" % (pos, c))
            nbt = parse_snbt(one["nbt"], "「もの」")
            if str(nbt.get("id", "")) not in ENTITY_IDS:
                raise ValueError("書き出せない「もの」です: %s" % nbt.get("id"))
            items.append((pos, nbt))
        entities[c] = items
    return {"structure": name, "facility": root.get("facility", ""), "cells": cells, "entities": entities}


def write(path, structure, facility, cells, entities, data_version=3465):
    """直しのファイルを書く（ゲームと同じ形。並びは y・z・x の順）。"""
    def order(p):
        return (p[1], p[2], p[0])
    out = {"format": FORMAT, "version": VERSION, "structure": structure, "facility": facility, "dataVersion": data_version,
           "cells": [], "entityCells": []}
    for pos in sorted(cells, key=order):
        state, nbt = cells[pos]
        c = {"pos": list(pos), "state": state_text(state)}
        if nbt is not None:
            c["nbt"] = nbtlib.serialize_tag(nbt, compact=True)
        out["cells"].append(c)
    for c in sorted(entities, key=order):
        out["entityCells"].append({"cell": list(c), "entities": [
            {"pos": list(pos), "nbt": nbtlib.serialize_tag(nbt, compact=True)} for pos, nbt in entities[c]]})
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    # 1マス1行にする（リポジトリの差分を読みやすく）
    lines = ["{"]
    for key in ("format", "version", "structure", "facility", "dataVersion"):
        lines.append("  %s: %s," % (json.dumps(key), json.dumps(out[key], ensure_ascii=False)))
    for key in ("cells", "entityCells"):
        items = [json.dumps(item, ensure_ascii=False, separators=(", ", ": ")) for item in out[key]]
        tail = "," if key == "cells" else ""
        if items:
            lines.append("  %s: [\n    %s\n  ]%s" % (json.dumps(key), ",\n    ".join(items), tail))
        else:
            lines.append("  %s: []%s" % (json.dumps(key), tail))
    lines.append("}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def load(structure):
    """構造物 structure の直し（tools/mapgen/edits/<structure>.json）。なければ None。"""
    path = os.path.join(EDITS_DIR, structure + ".json")
    if not os.path.isfile(path):
        return None
    data = read(path)
    if data["structure"] != structure:
        raise ValueError("%s：構造物の名前が違います（%s）" % (path, data["structure"]))
    return data


def _ints(value, what):
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, int) and not isinstance(v, bool) for v in value):
        raise ValueError(what + " は整数3つです: %r" % (value,))
    return tuple(value)


def warn(message):
    print("[edits] " + message, file=sys.stderr)
