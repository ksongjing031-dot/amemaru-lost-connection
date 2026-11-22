"""Phase 6 その2：4職業（ヒーラー・アーチャー・ローグ・モンク）の武器と防具のデータを書き出す。

使い方: python3 tools/datagen/build_gear6.py

- 武器：見習い（ギルドが貸す・Lv1）と古代石シリーズ（工房で作る・Lv5）を、鈴杖・弓・二刀の短剣・籠手で1つずつ。
- 防具：古代石シリーズ（軽装）：額当て・胴着・脚絆・短靴（アーチャー・ローグ・モンク）。ヒーラーは法衣（ウィザードと同じ）を着る【提案】。
- 書き出すもの：equipment/*.json、recipes/workshop/*.json、tags/items/weapons/<職業>.json、item_types.json、言語、法衣の jobs。
数値は、見習いの剣（攻撃6）・杖（魔力8）と、古代石の剣（攻撃14）・杖（魔力17）に合わせた【補完】。
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/amemaru")
MANIFEST = os.path.join(ROOT, "src/main/resources/amemaru/item_types.json")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def ing(item, count):
    return {"item": "amemaru:" + item, "count": count}


# (ID, 種類, 職業, 名前, 見習いの数値, 古代石の数値, 古代石の材料, 古代石の修理, 見習いの説明, 古代石の説明)
WEAPONS = [
    ("bell_staff", "bell_staff", "healer", ("見習いの鈴杖", "古代石の鈴杖"),
     {"magic": 8, "mp": 20}, {"magic": 16, "mp": 40, "heal_power": 3},
     [ing("ancient_stone", 2), ing("crystal_shard", 4), ing("moss_iron", 2)],
     [ing("moss_iron", 1), ing("crystal_shard", 1)],
     "ギルドが新しい冒険者に貸す鈴杖。先の小さな鈴は、教会で清めてある。",
     "古代石の輪に、結晶の鈴を下げた杖。鳴らすと、澄んだ音が長く残る。"),
    ("bow", "bow", "archer", ("見習いの弓", "古代石の弓"),
     {"attack": 6}, {"attack": 13, "crit": 2},
     [ing("moss_iron", 3), ing("bee_stinger", 4), ing("tsunomofu_fluff", 2)],
     [ing("moss_iron", 1), ing("tsunomofu_fluff", 1)],
     "ギルドが新しい冒険者に貸す短い弓。弦は何度も張り替えてある。",
     "苔鉄の芯に古代石の握りをはめ、結晶蜂の針で矢じりを研いだ弓。"),
    ("daggers", "daggers", "rogue", ("見習いの双短剣", "古代石の双短剣"),
     {"attack": 6}, {"attack": 13, "crit": 2},
     [ing("moss_iron", 4), ing("chipped_fang", 3)],
     [ing("moss_iron", 1), ing("chipped_fang", 1)],
     "ギルドが新しい冒険者に貸す、二本ひと組の短剣。",
     "欠けた牙を研ぎ出した刃に、苔鉄の柄を巻いた二本の短剣。"),
    ("gauntlets", "fist", "monk", ("見習いの籠手", "古代石の籠手"),
     {"attack": 6, "hp": 20}, {"attack": 13, "hp": 40},
     [ing("moss_iron", 3), ing("ancient_stone", 4)],
     [ing("moss_iron", 1), ing("ancient_stone", 1)],
     "ギルドが新しい冒険者に貸す、革に鉄の鋲を打った籠手。",
     "拳の甲に古代石の板を重ねた籠手。打つたびに、石が低く鳴る。"),
]

# 古代石シリーズ（軽装）：ID・場所・名前・数値・材料・修理・説明・熟練度・経験
LIGHT = [
    ("ancient_stone_circlet", "head", "古代石の額当て", {"defense": 3, "hp": 15, "crit": 1},
     [ing("tsunomofu_fluff", 3), ing("chipped_fang", 2), ing("moss_iron", 1)], [ing("tsunomofu_fluff", 1)],
     "鞣した革に古代石の小板を縫いつけた額当て。羽根が一本さしてある。", 1, 9),
    ("ancient_stone_jerkin", "chest", "古代石の胴着", {"defense": 5, "hp": 30, "speed": 1},
     [ing("tsunomofu_fluff", 5), ing("chipped_fang", 3), ing("moss_iron", 2)], [ing("tsunomofu_fluff", 2), ing("moss_iron", 1)],
     "胸と背に古代石の板を当てた、動きやすい革の胴着。", 2, 14),
    ("ancient_stone_breeches", "legs", "古代石の脚絆", {"defense": 4, "hp": 20, "speed": 1},
     [ing("tsunomofu_fluff", 4), ing("chipped_fang", 2), ing("moss_iron", 1)], [ing("tsunomofu_fluff", 1)],
     "すねに革の帯を巻いた脚絆。走っても音がしない。", 1, 11),
    ("ancient_stone_shoes", "feet", "古代石の短靴", {"defense": 2, "hp": 10, "speed": 2},
     [ing("tsunomofu_fluff", 3), ing("ancient_stone", 2)], [ing("tsunomofu_fluff", 1)],
     "底に古代石の薄板を仕込んだ、柔らかい短靴。", 1, 8),
]
LIGHT_JOBS = ["amemaru:archer", "amemaru:rogue", "amemaru:monk"]
LIGHT_SERIES = "ancient_stone_light"


def main():
    lang = {}
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    weapon_ids = {w["id"] for w in manifest["weapons"]}
    for key, kind, job, names, app_stats, anc_stats, recipe, repair, app_desc, anc_desc in WEAPONS:
        app = "apprentice_" + key
        anc = "ancient_stone_" + key
        write(os.path.join(DATA, "amemaru/equipment", app + ".json"), {
            "slot": "weapon", "generation": 1, "series": "見習い装備", "rarity": "common", "required_level": 1,
            "jobs": ["amemaru:" + job], "stats": app_stats, "fixed": True, "durability": 0, "binding": "bound",
            "description": app_desc})
        write(os.path.join(DATA, "amemaru/equipment", anc + ".json"), {
            "slot": "weapon", "generation": 1, "series": "古代石シリーズ", "rarity": "common", "required_level": 5,
            "jobs": ["amemaru:" + job], "stats": anc_stats, "durability": 400, "binding": "none", "repair": repair,
            "description": anc_desc})
        write(os.path.join(DATA, "recipes/workshop", anc + ".json"), {
            "type": "amemaru:workshop", "workshop": "weapon", "mastery": 1, "mastery_exp": 10, "ingredients": recipe,
            "result": {"item": "amemaru:" + anc}})
        write(os.path.join(DATA, "tags/items/weapons", job + ".json"), {
            "replace": False, "values": ["amemaru:" + app, "amemaru:" + anc]})
        for item in (app, anc):
            if item not in weapon_ids:
                manifest["weapons"].append({"id": item, "kind": kind})
                weapon_ids.add(item)
        lang["item.amemaru." + app] = names[0]
        lang["item.amemaru." + anc] = names[1]
    pieces = {}
    for item, slot, name, stats, recipe, repair, desc, mastery, exp in LIGHT:
        write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
            "slot": slot, "generation": 1, "series": "古代石シリーズ（軽装）", "rarity": "common", "required_level": 5,
            "jobs": LIGHT_JOBS, "stats": stats, "durability": 280, "binding": "none", "repair": repair, "description": desc})
        write(os.path.join(DATA, "recipes/workshop", item + ".json"), {
            "type": "amemaru:workshop", "workshop": "armor", "mastery": mastery, "mastery_exp": exp, "ingredients": recipe,
            "result": {"item": "amemaru:" + item}})
        pieces[slot] = item
        lang["item.amemaru." + item] = name
    if not any(a["series"] == LIGHT_SERIES for a in manifest["armor"]):
        manifest["armor"].append({"series": LIGHT_SERIES, "pieces": pieces})
    comment = manifest["_comment"]
    for i, line in enumerate(comment):
        if line.startswith("weapons.kind"):
            comment[i] = ("weapons.kind：sword（片手剣）・greatsword（両手剣）・staff（杖）・bell_staff（鈴杖）・bow（弓）・daggers（二刀の短剣）・"
                          "fist（籠手）。model が geckolib なら GeckoLib で描く（動く・光る武器）")
    write(MANIFEST, manifest)
    # ヒーラーは法衣を着る【提案】
    for item in ("ancient_stone_hood", "ancient_stone_robe", "ancient_stone_legwear", "ancient_stone_boots"):
        path = os.path.join(DATA, "amemaru/equipment", item + ".json")
        data = json.load(open(path, encoding="utf-8"))
        if "amemaru:healer" not in data["jobs"]:
            data["jobs"].append("amemaru:healer")
        data["series"] = "古代石シリーズ（法衣）"
        write(path, data)
    lang.update({
        "weapon_kind.amemaru.bell_staff": "鈴杖",
        "weapon_kind.amemaru.bow": "弓",
        "weapon_kind.amemaru.daggers": "二刀の短剣",
        "weapon_kind.amemaru.fist": "籠手",
    })
    for code in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", code + ".json")
        data = json.load(open(path, encoding="utf-8"))
        data.update(lang)
        write(path, data)
    print("gear ok:", len(WEAPONS) * 2, "weapons,", len(LIGHT), "armor pieces")


if __name__ == "__main__":
    main()
