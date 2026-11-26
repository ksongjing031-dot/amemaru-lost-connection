"""ゲームの中で直して書き出した所を、設計データに取り込む（11月21日）。

ゲームの中で /amemaru admin city export を使うと、ワールドのフォルダの generated/amemaru/city/edits/ に、構造物ごとの
直し（<構造物>.json）ができる。そのフォルダ（かファイル）を渡すと、確かめてから tools/mapgen/edits/<構造物>.json に重ねる
（同じマスは、渡した方を使う）。そのあと、表示される build_*.py で構造物ファイルを作り直すと、直しが入る（mapgen/edits.py。
作り直しても直しは残る）。最後に check_city.py で街全体を確かめる。

  python3 -I tools/mapgen/import_edits.py <フォルダかファイル> ...          # 取り込む
  python3 -I tools/mapgen/import_edits.py <フォルダかファイル> ... --dry-run  # 確かめるだけ（書かない）

送ってもらったファイルは、そのまま信じない：形・構造物の名前・マスの範囲・ブロックの書き方・中身（SNBT）・「もの」の種類を確かめ、
おかしなファイルは取り込まない。
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import nbtlib  # noqa: E402

from mapgen import edits  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
STRUCTURES = os.path.join(ROOT, "src", "main", "resources", "data", "amemaru", "structures", "map", "conelia")
# 構造物 → それを書き出す道具
SCRIPTS = {
    "guild_hall": "build_guild.py", "personal_storage": "build_storage.py",
    "weapon_workshop": "build_weapon.py", "weapon_forge_lit": "build_weapon.py",
    "armor_workshop": "build_armor.py", "accessory_workshop": "build_accessory.py",
    "market_north": "build_market.py", "market_south": "build_market.py", "market_restaurant_open": "build_market.py",
    "free_market": "build_freemarket.py", "free_market_expanded": "build_freemarket.py",
    "church": "build_church.py", "church_garden": "build_church.py", "church_counsel": "build_church.py",
    "fishing_pond": "build_fishing.py", "water_gate": "build_fishing.py", "fishing_alley": "build_fishing.py",
    "fishing_lakeside": "build_fishing.py", "fishing_pond_frozen": "build_fishing.py",
    "casino": "build_casino.py", "casino_highlow": "build_casino.py", "casino_new_machines": "build_casino.py", "casino_parlor": "build_casino.py",
    "tower_entrance": "build_tower.py", "tower_plaza": "build_tower.py", "tower_plaza_monument": "build_tower.py",
}
MAX_FILE = 8 * 1024 * 1024


def sources(paths):
    for p in paths:
        if os.path.isdir(p):
            for name in sorted(os.listdir(p)):
                full = os.path.join(p, name)
                if name.endswith(".json") and os.path.isfile(full) and not os.path.islink(full):
                    yield full
        elif os.path.isfile(p) and not os.path.islink(p):
            yield p
        else:
            raise SystemExit("見つかりません: " + p)


def size_of(structure):
    t = nbtlib.load(os.path.join(STRUCTURES, structure + ".nbt"))
    return tuple(int(v) for v in t["size"])


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("paths", nargs="+", help="送ってもらったフォルダ（generated/amemaru/city/edits）か、その中の .json")
    ap.add_argument("--dry-run", action="store_true", help="確かめるだけで、書かない")
    args = ap.parse_args()

    done, scripts, bad = [], set(), 0
    for path in sources(args.paths):
        label = os.path.basename(path)
        try:
            if os.path.getsize(path) > MAX_FILE:
                raise ValueError("大きすぎます")
            data = edits.read(path)
            name = data["structure"]
            if name not in SCRIPTS or not os.path.isfile(os.path.join(STRUCTURES, name + ".nbt")):
                raise ValueError("知らない構造物です: " + name)
            if label != name + ".json":
                raise ValueError("ファイルの名前と構造物（%s）が違います" % name)
            sx, sy, sz = size_of(name)
            for pos in list(data["cells"]) + list(data["entities"]):
                if not (0 <= pos[0] < sx and 0 <= pos[1] < sy and 0 <= pos[2] < sz):
                    raise ValueError("マス %r が構造物（%d×%d×%d）の外です" % (pos, sx, sy, sz))
            for pos, (state, _) in data["cells"].items():
                if ":" in state[0] and not state[0].startswith("amemaru:"):
                    print("  注意：%s のマス %r は、ほかのModのブロックです（%s）" % (name, pos, state[0]))
        except (ValueError, KeyError, TypeError, OSError) as e:
            print("取り込みません：%s（%s）" % (label, e))
            bad += 1
            continue
        # 前に取り込んだ直しに重ねる（同じマスは新しい方）
        cur = edits.load(name) or {"cells": {}, "entities": {}}
        cells = dict(cur["cells"])
        entities = dict(cur["entities"])
        new_cells = sum(1 for p in data["cells"] if p not in cells)
        cells.update(data["cells"])
        entities.update(data["entities"])
        if not args.dry_run:
            edits.write(os.path.join(edits.EDITS_DIR, name + ".json"), name, data["facility"], cells, entities)
        done.append(name)
        scripts.add(SCRIPTS[name])
        print("%s：マス %d（新しく %d）・「もの」のマス %d → 合わせて マス %d・「もの」のマス %d" % (
            name, len(data["cells"]), new_cells, len(data["entities"]), len(cells), len(entities)))
    if done:
        verb = "確かめました（書いていません）" if args.dry_run else "取り込みました（%s）" % os.path.relpath(edits.EDITS_DIR, ROOT)
        print("%s：%s" % (verb, "、".join(done)))
        print("作り直す：" + "  ".join("python3 tools/mapgen/" + s for s in sorted(scripts)))
        print("そのあと：python3 tools/mapgen/check_city.py（街全体の確かめ）")
    if bad:
        sys.exit(1)


if __name__ == "__main__":
    main()
