"""11月25日：第2章（6〜10階）の職業ごとの装備「霜銀シリーズ」と、生産職どうしの部品（補強金具・台座布・結晶細工）を書き出す。

使い方: python3 tools/datagen/build_gear_frost_silver.py

決まっていること（Phase 0-A）：
- 20章「装備の世代」：第2章 凍てつく記憶。主な素材は霜銀・氷晶・凍て毛皮。「霜銀シリーズ → 女王シリーズ（10階のレア）」。対応レベル 15〜28
- 19章「生産職」：第2章以降の装備は、別の生産職が作る部品を一つ使う。武器には結晶細工、防具には補強金具、アクセサリーには
  台座布。三すくみにして、一人では全部そろわないようにする【提案】（docs/phase3.md で「第2章の装備と一緒に作る」としていたもの）
  - 補強金具：武器職人が作る（武器工房）→ 防具と盾に使う
  - 台座布：防具職人が作る（防具工房）→ アクセサリーに使う（6階のアクセサリーは tools/datagen/build_floor_accessories.py）
  - 結晶細工：装飾師が作る（装飾工房）→ 武器に使う
- 11月23日に決定：3階からは職業ごとの固有の防具と武器。【11月25日に決定】防具は一式（頭・胴・脚・足）そろえた形
- 20章：職業ごとに見た目の方向性を変える（ナイトは重い金属鎧、ウィザードはローブ、ヒーラーは白い法衣と鈴、アーチャーは軽い革と
  羽根飾り、ローグは黒い軽装と布、モンクは道着と籠手）

こちらで決めたこと【補完】：
- 第2世代・コモン（工房で作る品。大成功で品質が上がる）。Lv24 から（6階のおすすめ Lv24〜31 のはじめ。守護者シリーズは Lv22）
- 数値は、流紋シリーズの同じ品の約1.45倍（守護者シリーズ〈エピック・Lv22〉の素の数値を少し上回るくらい）。会心・速さのような
  小さな数は、はね上がらないよう +1 まで
- 素材は6階の霜銀・氷晶・凍て毛皮・霜狼の牙・凍て鳥の羽根。重い鎧と武器の金属は霜銀、ローブや軽装は凍て毛皮と羽根が多い
- 部品は、どれも2つずつできる（熟練度3。第1章の上限でも作れる）

書き出すもの：equipment/*.json、recipes/workshop/*.json、tags/items/weapons/<職業>.json、item_types.json、言語。
見た目（モデル・絵・持ち物の絵）は tools/artgen/make_frost_silver_gear.py。
"""
import json
import os
from collections import OrderedDict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/amemaru")
MANIFEST = os.path.join(ROOT, "src/main/resources/amemaru/item_types.json")

LEVEL = 24
GENERATION = 2
SERIES = "霜銀シリーズ"
JOB_NAMES = {"knight": "ナイト", "wizard": "ウィザード", "healer": "ヒーラー", "archer": "アーチャー", "rogue": "ローグ",
             "monk": "モンク"}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def ing(*pairs):
    return [{"item": "amemaru:" + item, "count": count} for item, count in pairs]


# ---------------------------------------------------------------- 生産職どうしの部品（19章の三すくみ）

# (ID, 名前, 作る工房, 材料, できる数, 熟練度, 経験, 説明)
COMPONENTS = [
    ("reinforcement_fitting", "補強金具", "weapon", ing(("frost_silver", 2), ("moss_iron", 1)), 2, 3, 10,
     "霜銀を打ち出した、角当てと留め鋲の一揃い。武器職人が作り、防具職人が鎧や盾の継ぎ目に使う。"),
    ("pedestal_cloth", "台座布", "armor", ing(("frozen_fur", 1), ("tsunomofu_fluff", 2)), 2, 3, 10,
     "宝飾をのせる、厚く織った小さな布。防具職人が織り、装飾師が首飾りやお守りの台座に使う。"),
    ("crystal_filigree", "結晶細工", "accessory", ing(("ice_crystal", 2), ("crystal_shard", 1)), 2, 3, 10,
     "氷晶を細く削り、銀線で編んだ飾り。装飾師が作り、武器職人が刃や杖の芯に埋め込む。"),
]

# ---------------------------------------------------------------- 武器（結晶細工を1つ使う）

# (ID, 種類, 職業, 名前, 数値, 材料, 熟練度, 経験, 耐久, 修理, 説明)
WEAPONS = [
    ("frost_silver_sword", "sword", "knight", "霜銀の剣", {"attack": 28},
     ing(("frost_silver", 5), ("frost_fang", 2), ("ice_crystal", 1), ("crystal_filigree", 1)), 4, 26, 550,
     ing(("frost_silver", 1), ("ice_crystal", 1)),
     "霜銀を鍛えた片手剣。刃の根もとに結晶細工を埋め、振ると白い霜の筋が残る。"),
    ("frost_silver_greatsword", "greatsword", "knight", "霜銀の大剣", {"attack": 45},
     ing(("frost_silver", 7), ("frost_fang", 3), ("ice_crystal", 1), ("crystal_filigree", 1)), 5, 32, 600,
     ing(("frost_silver", 2), ("ice_crystal", 1)),
     "雪原の門扉ほどもある、白銀の両手剣。背の溝に、溶けない氷晶が一列に並ぶ。"),
    ("frost_silver_staff", "staff", "wizard", "霜銀の杖", {"magic": 35, "mp": 80},
     ing(("frost_silver", 3), ("ice_crystal", 3), ("ice_feather", 2), ("crystal_filigree", 1)), 5, 30, 550,
     ing(("frost_silver", 1), ("ice_crystal", 1)),
     "霜銀の枝の先に、氷晶の玉を結晶細工で吊った杖。玉の中に、かすかなオーロラが揺れる。"),
    ("frost_silver_bell_staff", "bell_staff", "healer", "霜銀の鈴杖", {"magic": 32, "mp": 80, "heal_power": 7},
     ing(("frost_silver", 4), ("ice_crystal", 2), ("ice_feather", 1), ("crystal_filigree", 1)), 5, 30, 550,
     ing(("frost_silver", 1), ("ice_crystal", 1)),
     "白銀の輪に、氷晶の小さな鈴を三つ下げた杖。鳴らすと、雪の降る夜のように静かな音がする。"),
    ("frost_silver_bow", "bow", "archer", "霜銀の弓", {"attack": 26, "crit": 4},
     ing(("frost_silver", 3), ("ice_feather", 3), ("frozen_fur", 1), ("crystal_filigree", 1)), 5, 28, 550,
     ing(("frost_silver", 1), ("ice_feather", 1)),
     "霜銀の弭（ゆはず）に、凍て鳥の羽根を飾った反り弓。握りは凍て毛皮で巻いてあり、冷えた手でも滑らない。"),
    ("frost_silver_daggers", "daggers", "rogue", "霜銀の双短剣", {"attack": 26, "crit": 4},
     ing(("frost_silver", 4), ("frost_fang", 2), ("ice_feather", 1), ("crystal_filigree", 1)), 5, 28, 550,
     ing(("frost_silver", 1), ("frost_fang", 1)),
     "霜狼の牙の形に反った、二本ひと組の白銀の短剣。刃のふちに、薄い霜が浮く。"),
    ("frost_silver_gauntlets", "fist", "monk", "霜銀の籠手", {"attack": 26, "hp": 44},
     ing(("frost_silver", 5), ("frozen_fur", 2), ("frost_fang", 1), ("crystal_filigree", 1)), 5, 28, 550,
     ing(("frost_silver", 1), ("frozen_fur", 1)),
     "拳に霜銀の板を重ね、手首を凍て毛皮で包んだ籠手。打ち込むと、雪煙が上がる。"),
]

# 盾（ナイト。防具工房なので補強金具）
SHIELD = ("frost_silver_shield", "霜銀の盾", {"defense": 10, "hp": 36},
          ing(("frost_silver", 6), ("frozen_fur", 2), ("ice_crystal", 1), ("reinforcement_fitting", 1)), 5, 30, 640,
          ing(("frost_silver", 2), ("frozen_fur", 1)),
          "霜銀の板を丸く打ち出した盾。表に、六つの枝をもつ雪の結晶の紋。")

# ---------------------------------------------------------------- 防具（補強金具を1つ使う。職業ごとに一式）

# 職業 → (シリーズのID, 胴の耐久, [(ID, 場所, 名前, 数値, 材料, 熟練度, 経験, 修理, 説明), ...])
FIT = ("reinforcement_fitting", 1)
ARMOR = OrderedDict([
    ("knight", ("frost_silver_knight", 600, [
        ("frost_silver_knight_helm", "head", "霜銀の兜", {"defense": 6, "hp": 26},
         ing(("frost_silver", 4), ("frozen_fur", 1), ("frost_fang", 1), FIT), 4, 22, ing(("frost_silver", 1)),
         "白銀の兜。とさかに沿って、凍て毛皮の房が流れる。"),
        ("frost_silver_knight_cuirass", "chest", "霜銀の胴鎧", {"defense": 9, "hp": 44},
         ing(("frost_silver", 6), ("frozen_fur", 2), ("ice_crystal", 1), FIT), 5, 30, ing(("frost_silver", 2), ("frozen_fur", 1)),
         "胸に雪の結晶の紋を打ち出した、霜銀の重い鎧。襟は凍て毛皮で、吹雪の中でも首が冷えない。"),
        ("frost_silver_knight_greaves", "legs", "霜銀の脚甲", {"defense": 7, "hp": 35},
         ing(("frost_silver", 5), ("frozen_fur", 1), ("frost_fang", 1), FIT), 5, 26, ing(("frost_silver", 1)),
         "膝に霜狼の牙の形の当て板をつけた脚甲。"),
        ("frost_silver_knight_sabatons", "feet", "霜銀の鉄靴", {"defense": 4, "hp": 17},
         ing(("frost_silver", 3), ("frozen_fur", 2), FIT), 4, 20, ing(("frost_silver", 1)),
         "内側に凍て毛皮を張った、雪に沈まない鉄靴。"),
    ])),
    ("wizard", ("frost_silver_wizard", 390, [
        ("frost_silver_wizard_hat", "head", "霜銀の尖り帽", {"defense": 3, "mp": 36, "magic": 4},
         ing(("frozen_fur", 3), ("ice_feather", 2), ("frost_silver", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "夜空の色の尖り帽。つばのふちに、霜銀の糸で雪の結晶が縫ってある。"),
        ("frost_silver_wizard_robe", "chest", "霜銀の長衣", {"defense": 6, "mp": 58, "magic": 6},
         ing(("frozen_fur", 5), ("ice_feather", 2), ("ice_crystal", 2), FIT), 5, 30, ing(("frozen_fur", 2), ("ice_feather", 1)),
         "星灯の雪読みが着る、深い藍の長衣。腰に、氷晶の灯りを下げる。"),
        ("frost_silver_wizard_legwear", "legs", "霜銀の下衣", {"defense": 4, "mp": 36, "magic": 3},
         ing(("frozen_fur", 4), ("ice_feather", 2), ("frost_silver", 1), FIT), 5, 24, ing(("frozen_fur", 1)),
         "すそに銀の雪を縫った、前の割れた長い下衣。"),
        ("frost_silver_wizard_shoes", "feet", "霜銀の布靴", {"defense": 3, "mp": 22, "speed": 1},
         ing(("frozen_fur", 3), ("ice_feather", 1), ("frost_silver", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "毛皮を折り返した、やわらかい布の靴。"),
    ])),
    ("healer", ("frost_silver_healer", 390, [
        ("frost_silver_healer_hood", "head", "霜銀の白頭巾", {"defense": 4, "mp": 29, "heal_power": 1},
         ing(("frozen_fur", 3), ("ice_feather", 1), ("frost_silver", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "白い頭巾。額の銀の輪に、氷晶の小さな鈴がひとつ下がる。"),
        ("frost_silver_healer_robe", "chest", "霜銀の白法衣", {"defense": 6, "mp": 44, "hp": 22, "heal_power": 3},
         ing(("frozen_fur", 5), ("ice_crystal", 2), ("frost_silver", 1), FIT), 5, 30, ing(("frozen_fur", 2), ("ice_crystal", 1)),
         "雪のように白い法衣。肩掛けのふちに氷晶の鈴が並び、歩くたびに澄んだ音がする。"),
        ("frost_silver_healer_legwear", "legs", "霜銀の白下衣", {"defense": 4, "mp": 29, "hp": 15},
         ing(("frozen_fur", 4), ("ice_feather", 1), ("frost_silver", 1), FIT), 5, 24, ing(("frozen_fur", 1)),
         "すそに淡い青で雪を縫った、白い長い下衣。"),
        ("frost_silver_healer_shoes", "feet", "霜銀の白靴", {"defense": 3, "mp": 15, "speed": 1},
         ing(("frozen_fur", 3), ("ice_feather", 1), ("frost_silver", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "銀の留め金の、毛皮の白い靴。"),
    ])),
    ("archer", ("frost_silver_archer", 350, [
        ("frost_silver_archer_cap", "head", "霜銀の羽根帽", {"defense": 3, "hp": 17, "crit": 3},
         ing(("frozen_fur", 2), ("ice_feather", 3), FIT), 4, 20, ing(("ice_feather", 1)),
         "毛皮の帽子に、凍て鳥の氷青の長い羽根を立てたもの。"),
        ("frost_silver_archer_jerkin", "chest", "霜銀の狩り胴着", {"defense": 7, "hp": 36, "crit": 2},
         ing(("frozen_fur", 4), ("ice_feather", 2), ("frost_silver", 2), FIT), 5, 28, ing(("frozen_fur", 2), ("frost_silver", 1)),
         "革の胴着に、白い毛皮の外套。胸の革帯に、羽根の飾りと霜銀の留め金。"),
        ("frost_silver_archer_leggings", "legs", "霜銀の革脚絆", {"defense": 6, "hp": 26, "speed": 1},
         ing(("frozen_fur", 4), ("ice_feather", 1), ("frost_fang", 1), FIT), 5, 24, ing(("frozen_fur", 1)),
         "腿に毛皮の当てをつけた、動きやすい脚絆。"),
        ("frost_silver_archer_boots", "feet", "霜銀の長靴", {"defense": 3, "hp": 12, "speed": 2},
         ing(("frozen_fur", 3), ("ice_feather", 1), ("frost_silver", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "ひざ下まである、毛皮の折り返しの長靴。"),
    ])),
    ("rogue", ("frost_silver_rogue", 350, [
        ("frost_silver_rogue_mask", "head", "霜銀の覆面", {"defense": 3, "hp": 15, "crit": 3},
         ing(("frozen_fur", 2), ("ice_feather", 2), ("frost_fang", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "目のまわりだけを開けた、雪の夜の色の頭巾と覆面。"),
        ("frost_silver_rogue_garb", "chest", "霜銀の黒装束", {"defense": 6, "hp": 32, "speed": 1, "crit": 2},
         ing(("frozen_fur", 4), ("ice_feather", 2), ("frost_fang", 2), FIT), 5, 28, ing(("frozen_fur", 2), ("frost_fang", 1)),
         "雪に溶けこむよう、灰と白にまだらに染めた軽い装束。たすきに霜銀の短剣差し。"),
        ("frost_silver_rogue_leggings", "legs", "霜銀の黒脚絆", {"defense": 4, "hp": 23, "speed": 1},
         ing(("frozen_fur", 4), ("ice_feather", 1), ("frost_fang", 1), FIT), 5, 24, ing(("frozen_fur", 1)),
         "すねに毛皮を巻いた、音のしない脚絆。"),
        ("frost_silver_rogue_tabi", "feet", "霜銀の足袋", {"defense": 3, "hp": 12, "speed": 3},
         ing(("frozen_fur", 3), ("ice_feather", 1), FIT), 4, 20, ing(("frozen_fur", 1)),
         "底に凍て毛皮を張った、雪の上でも音のしない足袋。"),
    ])),
    ("monk", ("frost_silver_monk", 370, [
        ("frost_silver_monk_headband", "head", "霜銀の鉢巻", {"defense": 3, "hp": 22, "crit": 2},
         ing(("frozen_fur", 2), ("frost_fang", 1), ("frost_silver", 1), FIT), 4, 18, ing(("frozen_fur", 1)),
         "額に霜銀の小さな板を縫いつけた鉢巻。結んだ端が、うしろに垂れる。"),
        ("frost_silver_monk_gi", "chest", "霜銀の道着", {"defense": 7, "hp": 44, "speed": 1},
         ing(("frozen_fur", 5), ("frost_silver", 2), ("ice_crystal", 1), FIT), 5, 28, ing(("frozen_fur", 2), ("frost_silver", 1)),
         "白い厚手の道着に、毛皮の肩当てと藍の帯。背に雪の結晶が染めぬいてある。"),
        ("frost_silver_monk_hakama", "legs", "霜銀の袴", {"defense": 6, "hp": 32},
         ing(("frozen_fur", 4), ("frost_fang", 1), ("frost_silver", 1), FIT), 5, 24, ing(("frozen_fur", 1)),
         "すそを毛皮の脚絆で絞った、藍の袴。"),
        ("frost_silver_monk_sandals", "feet", "霜銀の草鞋", {"defense": 3, "hp": 15, "speed": 2},
         ing(("frozen_fur", 2), ("frost_fang", 1), ("frost_silver", 1), FIT), 4, 18, ing(("frozen_fur", 1)),
         "足首を凍て毛皮で包んだ、鼻緒の草鞋。"),
    ])),
])


def recipe(workshop, mastery, exp, ingredients, item, count=1):
    result = {"item": "amemaru:" + item}
    if count > 1:
        result["count"] = count
    return {"type": "amemaru:workshop", "workshop": workshop, "mastery": mastery, "mastery_exp": exp, "ingredients": ingredients,
            "result": result}


def main():
    lang = OrderedDict()
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    weapon_ids = {w["id"] for w in manifest["weapons"]}
    shield_ids = {s["id"] for s in manifest["shields"]}
    material_ids = {m["id"] for m in manifest["materials"]}
    series_ids = {a["series"] for a in manifest["armor"]}
    tags = {}
    for item, name, workshop, materials, count, mastery, exp, desc in COMPONENTS:
        write(os.path.join(DATA, "recipes/workshop", item + ".json"), recipe(workshop, mastery, exp, materials, item, count))
        if item not in material_ids:
            manifest["materials"].append({"id": item, "rarity": "uncommon"})
        lang["item.amemaru." + item] = name
        lang["item.amemaru." + item + ".desc"] = desc
    for item, kind, job, name, stats, materials, mastery, exp, durability, repair, desc in WEAPONS:
        write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
            "slot": "weapon", "generation": GENERATION, "series": SERIES + "（" + JOB_NAMES[job] + "）", "rarity": "common",
            "required_level": LEVEL, "jobs": ["amemaru:" + job], "stats": stats, "durability": durability, "binding": "none",
            "repair": repair, "description": desc})
        write(os.path.join(DATA, "recipes/workshop", item + ".json"), recipe("weapon", mastery, exp, materials, item))
        tags.setdefault(job, []).append("amemaru:" + item)
        if item not in weapon_ids:
            manifest["weapons"].append({"id": item, "kind": kind})
        lang["item.amemaru." + item] = name
    item, name, stats, materials, mastery, exp, durability, repair, desc = SHIELD
    write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
        "slot": "offhand", "generation": GENERATION, "series": SERIES + "（ナイト）", "rarity": "common", "required_level": LEVEL,
        "jobs": ["amemaru:knight"], "stats": stats, "durability": durability, "binding": "none", "repair": repair,
        "description": desc})
    write(os.path.join(DATA, "recipes/workshop", item + ".json"), recipe("armor", mastery, exp, materials, item))
    if item not in shield_ids:
        manifest["shields"].append({"id": item})
    lang["item.amemaru." + item] = name
    for job, (series, durability, pieces) in ARMOR.items():
        slots = OrderedDict()
        for item, slot, name, stats, materials, mastery, exp, repair, desc in pieces:
            write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
                "slot": slot, "generation": GENERATION, "series": SERIES + "（" + JOB_NAMES[job] + "）", "rarity": "common",
                "required_level": LEVEL, "jobs": ["amemaru:" + job], "stats": stats,
                "durability": durability if slot == "chest" else durability - 40, "binding": "none", "repair": repair,
                "description": desc})
            write(os.path.join(DATA, "recipes/workshop", item + ".json"), recipe("armor", mastery, exp, materials, item))
            slots[slot] = item
            lang["item.amemaru." + item] = name
        if series not in series_ids:
            manifest["armor"].append({"series": series, "pieces": dict(slots)})
    write(MANIFEST, manifest)
    for job, items in tags.items():
        path = os.path.join(DATA, "tags/items/weapons", job + ".json")
        data = json.load(open(path, encoding="utf-8"))
        for item in items:
            if item not in data["values"]:
                data["values"].append(item)
        write(path, data)
    for code in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", code + ".json")
        data = json.load(open(path, encoding="utf-8"))
        data.update(lang)
        write(path, data)
    count = len(WEAPONS) + 1 + sum(len(p) for _, _, p in ARMOR.values())
    print("霜銀シリーズ：", count, "品（武器", len(WEAPONS), "・盾1・防具", count - len(WEAPONS) - 1, "）・部品", len(COMPONENTS))


if __name__ == "__main__":
    main()
