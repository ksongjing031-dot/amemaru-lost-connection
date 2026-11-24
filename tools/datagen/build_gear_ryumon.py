"""Phase 8 その2：3〜4階の職業ごとの装備「流紋シリーズ」のデータを書き出す。

使い方: python3 tools/datagen/build_gear_ryumon.py

11月23日に決めたこと：最序盤（1〜2階）は複数の職業が同じ防具を着てよいが、3階からは職業ごとの固有防具と武器にする。
- 防具：6職業それぞれに、頭・胴・脚・足の4つ（その職業だけが着られる。見た目のシリーズも職業ごとに1つ）。
- 武器：6職業それぞれに1つ（ナイトは片手剣と両手剣、それに盾）。
- 工房で、3階の素材（水路の青銅・かじられた歯車・澱みのゼリー・ナマズのひげ・流水の結晶）から作る。
見た目の向きは 0-A 20章：ナイトは重い金属鎧、ウィザードはローブ、ヒーラーは白い法衣と鈴、アーチャーは軽い革と羽根飾り、
ローグは黒い軽装と布、モンクは道着と籠手。シリーズの名前「流紋（りゅうもん）」と、数値・素材は【補完】。
数値は、11月24日のバランスの調節（docs/phase8_balance.md）のあとの古代石シリーズの1.3〜1.4倍に、職業の役目に合う
ステータスを足した。Lv7（3階のおすすめ Lv5〜8 のうしろ）から。

書き出すもの：equipment/*.json、recipes/workshop/*.json、tags/items/weapons/<職業>.json、item_types.json、言語。
見た目（モデル・絵・持ち物の絵）は tools/artgen/make_gear_ryumon.py。
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/amemaru")
MANIFEST = os.path.join(ROOT, "src/main/resources/amemaru/item_types.json")

LEVEL = 15  # 11月24日：レベルの上限を上げたので 7 → 15
SERIES = "流紋シリーズ"
JOB_NAMES = {"knight": "ナイト", "wizard": "ウィザード", "healer": "ヒーラー", "archer": "アーチャー", "rogue": "ローグ",
             "monk": "モンク"}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def ing(*pairs):
    return [{"item": "amemaru:" + item, "count": count} for item, count in pairs]


# 武器：(ID, 種類, 職業, 名前, 数値, 材料, 熟練度, 経験, 修理, 説明)
WEAPONS = [
    ("ryumon_sword", "sword", "knight", "流紋の剣", {"attack": 19},
     ing(("waterway_bronze", 5), ("gnawed_cog", 2), ("moss_iron", 2), ("flow_crystal", 1)), 2, 18,
     ing(("waterway_bronze", 1), ("moss_iron", 1)),
     "水路の青銅を打ち延ばした片手剣。刃の溝に流水の結晶を溶かし込み、振ると水の筋が走る。"),
    ("ryumon_greatsword", "greatsword", "knight", "流紋の大剣", {"attack": 31},
     ing(("waterway_bronze", 7), ("gnawed_cog", 3), ("moss_iron", 3), ("flow_crystal", 1)), 3, 24,
     ing(("waterway_bronze", 2), ("moss_iron", 1)),
     "水門の扉の板を思わせる、幅の広い両手剣。背に流れ落ちる滝の溝が彫ってある。"),
    ("ryumon_staff", "staff", "wizard", "流紋の杖", {"magic": 24, "mp": 55},
     ing(("waterway_bronze", 3), ("murky_gel", 3), ("crystal_shard", 2), ("flow_crystal", 2)), 3, 22,
     ing(("waterway_bronze", 1), ("crystal_shard", 1)),
     "青銅の渦の先に、水の玉を閉じ込めた流水の結晶を浮かせた杖。星灯の水読みの写し。"),
    ("ryumon_bell_staff", "bell_staff", "healer", "流紋の鈴杖", {"magic": 22, "mp": 55, "heal_power": 5},
     ing(("waterway_bronze", 4), ("murky_gel", 2), ("crystal_shard", 1), ("flow_crystal", 2)), 3, 22,
     ing(("waterway_bronze", 1), ("crystal_shard", 1)),
     "白い輪に、青銅の小さな鈴を三つ下げた杖。鳴らすと、水面に落ちるしずくの音がする。"),
    ("ryumon_bow", "bow", "archer", "流紋の弓", {"attack": 18, "crit": 3},
     ing(("catfish_whisker", 3), ("waterway_bronze", 3), ("moss_iron", 1), ("flow_crystal", 1)), 3, 20,
     ing(("catfish_whisker", 1), ("waterway_bronze", 1)),
     "青銅の弭（ゆはず）の反り弓。弦は牙ナマズのひげを撚ったもので、水に濡れても伸びない。"),
    ("ryumon_daggers", "daggers", "rogue", "流紋の双短剣", {"attack": 18, "crit": 3},
     ing(("waterway_bronze", 4), ("catfish_whisker", 2), ("chipped_fang", 2), ("flow_crystal", 1)), 3, 20,
     ing(("waterway_bronze", 1), ("catfish_whisker", 1)),
     "魚のひれの形に反った、二本ひと組の青銅の短剣。刃のふちに流れの紋が浮いている。"),
    ("ryumon_gauntlets", "fist", "monk", "流紋の籠手", {"attack": 18, "hp": 30},
     ing(("waterway_bronze", 5), ("gnawed_cog", 3), ("flow_crystal", 1)), 3, 20,
     ing(("waterway_bronze", 1), ("gnawed_cog", 1)),
     "拳に歯車の歯を並べた青銅の籠手。打ち込むと、水車のように重さが乗る。"),
]

# 盾（ナイト）
SHIELD = ("ryumon_shield", "流紋の盾", {"defense": 7, "hp": 25},
          ing(("waterway_bronze", 6), ("gnawed_cog", 3), ("flow_crystal", 1)), 3, 22,
          ing(("waterway_bronze", 2), ("gnawed_cog", 1)),
          "水門の扉の板金を盾にしたもの。表に、渦をまく流れの紋が打ち出してある。")

# 防具：職業 → (シリーズのID, [(ID, 場所, 名前, 数値, 材料, 熟練度, 経験, 修理, 説明), ...])
ARMOR = {
    "knight": ("ryumon_knight", 520, [
        ("ryumon_knight_helm", "head", "流紋の兜", {"defense": 4, "hp": 18},
         ing(("waterway_bronze", 4), ("gnawed_cog", 3), ("moss_iron", 2)), 2, 16, ing(("waterway_bronze", 1)),
         "波頭のとさかを立てた青銅の兜。ほおの板に流れの紋が刻んである。"),
        ("ryumon_knight_cuirass", "chest", "流紋の胴鎧", {"defense": 6, "hp": 30},
         ing(("waterway_bronze", 6), ("gnawed_cog", 4), ("moss_iron", 2), ("flow_crystal", 1)), 3, 24,
         ing(("waterway_bronze", 2), ("moss_iron", 1)),
         "胸に、水門のハンドルの形の円盤と流水の結晶をはめた青銅の鎧。肩は魚のうろこのように重なる。"),
        ("ryumon_knight_greaves", "legs", "流紋の脚甲", {"defense": 5, "hp": 24},
         ing(("waterway_bronze", 5), ("gnawed_cog", 3), ("moss_iron", 2)), 3, 20, ing(("waterway_bronze", 1)),
         "膝に渦の形の当て板をつけた青銅の脚甲。"),
        ("ryumon_knight_sabatons", "feet", "流紋の鉄靴", {"defense": 3, "hp": 12},
         ing(("waterway_bronze", 3), ("gnawed_cog", 2), ("murky_gel", 2)), 2, 14, ing(("waterway_bronze", 1)),
         "継ぎ目を澱みのゼリーでふさいだ、水の入らない鉄靴。"),
    ]),
    "wizard": ("ryumon_wizard", 340, [
        ("ryumon_wizard_hat", "head", "流紋の尖り帽", {"defense": 2, "mp": 25, "magic": 3},
         ing(("tsunomofu_fluff", 4), ("murky_gel", 3), ("catfish_whisker", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "深い水の色に染めた、つばの広い尖り帽。帯に銀の糸で波が縫ってある。"),
        ("ryumon_wizard_robe", "chest", "流紋の長衣", {"defense": 4, "mp": 40, "magic": 4},
         ing(("tsunomofu_fluff", 6), ("murky_gel", 4), ("catfish_whisker", 2), ("flow_crystal", 1)), 3, 24,
         ing(("tsunomofu_fluff", 2), ("murky_gel", 1)),
         "星灯の水読みが着る、藍の長衣。腰に流水の結晶のランタンを下げる。"),
        ("ryumon_wizard_legwear", "legs", "流紋の下衣", {"defense": 3, "mp": 25, "magic": 2},
         ing(("tsunomofu_fluff", 5), ("murky_gel", 3), ("catfish_whisker", 1)), 3, 18, ing(("tsunomofu_fluff", 1)),
         "すそに銀の波を縫った、前の割れた長い下衣。"),
        ("ryumon_wizard_shoes", "feet", "流紋の布靴", {"defense": 2, "mp": 15, "speed": 1},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 2), ("waterway_bronze", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "つま先の反った、やわらかい布の靴。"),
    ]),
    "healer": ("ryumon_healer", 340, [
        ("ryumon_healer_hood", "head", "流紋の白頭巾", {"defense": 3, "mp": 20, "heal_power": 1},
         ing(("tsunomofu_fluff", 4), ("murky_gel", 2), ("waterway_bronze", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "白い頭巾。額の金の輪に、青銅の小さな鈴がひとつ下がる。"),
        ("ryumon_healer_robe", "chest", "流紋の白法衣", {"defense": 4, "mp": 30, "hp": 15, "heal_power": 2},
         ing(("tsunomofu_fluff", 6), ("murky_gel", 3), ("waterway_bronze", 2), ("flow_crystal", 1)), 3, 24,
         ing(("tsunomofu_fluff", 2), ("waterway_bronze", 1)),
         "白い法衣。肩掛けのふちに小さな鈴が並び、歩くたびに澄んだ音がする。"),
        ("ryumon_healer_legwear", "legs", "流紋の白下衣", {"defense": 3, "mp": 20, "hp": 10},
         ing(("tsunomofu_fluff", 5), ("murky_gel", 3), ("catfish_whisker", 1)), 3, 18, ing(("tsunomofu_fluff", 1)),
         "すそに水色の波を縫った、白い長い下衣。"),
        ("ryumon_healer_shoes", "feet", "流紋の白靴", {"defense": 2, "mp": 10, "speed": 1},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 2), ("catfish_whisker", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "金の留め金の白い靴。"),
    ]),
    "archer": ("ryumon_archer", 300, [
        ("ryumon_archer_cap", "head", "流紋の羽根帽", {"defense": 2, "hp": 12, "crit": 2},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 2), ("catfish_whisker", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "鞣した革の帽子に、白い長い羽根を立てたもの。"),
        ("ryumon_archer_jerkin", "chest", "流紋の狩り胴着", {"defense": 5, "hp": 25, "crit": 1},
         ing(("tsunomofu_fluff", 5), ("murky_gel", 3), ("waterway_bronze", 2), ("flow_crystal", 1)), 3, 22,
         ing(("tsunomofu_fluff", 2), ("waterway_bronze", 1)),
         "革の胴着に緑の外套。胸の革帯に、矢羽根の飾りと青銅の留め金。"),
        ("ryumon_archer_leggings", "legs", "流紋の革脚絆", {"defense": 4, "hp": 18, "speed": 1},
         ing(("tsunomofu_fluff", 4), ("murky_gel", 3), ("gnawed_cog", 1)), 3, 18, ing(("tsunomofu_fluff", 1)),
         "腿に革の当てをつけた、動きやすい脚絆。"),
        ("ryumon_archer_boots", "feet", "流紋の長靴", {"defense": 2, "hp": 8, "speed": 2},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 2), ("waterway_bronze", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "ひざ下まである、折り返しの革の長靴。"),
    ]),
    "rogue": ("ryumon_rogue", 300, [
        ("ryumon_rogue_mask", "head", "流紋の覆面", {"defense": 2, "hp": 10, "crit": 2},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 3), ("catfish_whisker", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "目のまわりだけを開けた黒い頭巾と覆面。結び目の布が、うしろに長く流れる。"),
        ("ryumon_rogue_garb", "chest", "流紋の黒装束", {"defense": 4, "hp": 22, "speed": 1, "crit": 1},
         ing(("tsunomofu_fluff", 5), ("murky_gel", 4), ("catfish_whisker", 2), ("flow_crystal", 1)), 3, 22,
         ing(("tsunomofu_fluff", 2), ("murky_gel", 1)),
         "澱みのゼリーで黒く染めた軽い装束。腰の布と、たすきの短剣差し。"),
        ("ryumon_rogue_leggings", "legs", "流紋の黒脚絆", {"defense": 3, "hp": 16, "speed": 1},
         ing(("tsunomofu_fluff", 4), ("murky_gel", 3), ("gnawed_cog", 1)), 3, 18, ing(("tsunomofu_fluff", 1)),
         "すねに布を巻いた、音のしない黒い脚絆。"),
        ("ryumon_rogue_tabi", "feet", "流紋の足袋", {"defense": 2, "hp": 8, "speed": 3},
         ing(("tsunomofu_fluff", 3), ("murky_gel", 2), ("catfish_whisker", 1)), 2, 14, ing(("tsunomofu_fluff", 1)),
         "つま先の割れた、底のうすい黒い足袋。"),
    ]),
    "monk": ("ryumon_monk", 320, [
        ("ryumon_monk_headband", "head", "流紋の鉢巻", {"defense": 2, "hp": 15, "crit": 1},
         ing(("tsunomofu_fluff", 3), ("catfish_whisker", 1), ("murky_gel", 1)), 2, 12, ing(("tsunomofu_fluff", 1)),
         "額に青銅の小さな板を縫いつけた鉢巻。結んだ端がうしろに垂れる。"),
        ("ryumon_monk_gi", "chest", "流紋の道着", {"defense": 5, "hp": 30, "speed": 1},
         ing(("tsunomofu_fluff", 6), ("murky_gel", 2), ("waterway_bronze", 1), ("flow_crystal", 1)), 3, 22,
         ing(("tsunomofu_fluff", 2), ("murky_gel", 1)),
         "生成りの厚い道着に、藍の帯。背に流れの紋が染めぬいてある。"),
        ("ryumon_monk_hakama", "legs", "流紋の袴", {"defense": 4, "hp": 22},
         ing(("tsunomofu_fluff", 5), ("murky_gel", 3), ("gnawed_cog", 1)), 3, 18, ing(("tsunomofu_fluff", 1)),
         "すそを絞った、藍の袴。"),
        ("ryumon_monk_sandals", "feet", "流紋の草鞋", {"defense": 2, "hp": 10, "speed": 2},
         ing(("tsunomofu_fluff", 2), ("catfish_whisker", 1), ("gnawed_cog", 2)), 2, 12, ing(("tsunomofu_fluff", 1)),
         "ナマズのひげで編んだ鼻緒の草鞋。足首に布を巻く。"),
    ]),
}


def main():
    lang = {}
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    weapon_ids = {w["id"] for w in manifest["weapons"]}
    shield_ids = {s["id"] for s in manifest["shields"]}
    series_ids = {a["series"] for a in manifest["armor"]}
    tags = {}
    for item, kind, job, name, stats, recipe, mastery, exp, repair, desc in WEAPONS:
        write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
            "slot": "weapon", "generation": 1, "series": SERIES + "（" + JOB_NAMES[job] + "）", "rarity": "common",
            "required_level": LEVEL, "jobs": ["amemaru:" + job], "stats": stats,
            "durability": 520 if kind == "greatsword" else 480, "binding": "none", "repair": repair, "description": desc})
        write(os.path.join(DATA, "recipes/workshop", item + ".json"), {
            "type": "amemaru:workshop", "workshop": "weapon", "mastery": mastery, "mastery_exp": exp, "ingredients": recipe,
            "result": {"item": "amemaru:" + item}})
        tags.setdefault(job, []).append("amemaru:" + item)
        if item not in weapon_ids:
            manifest["weapons"].append({"id": item, "kind": kind})
        lang["item.amemaru." + item] = name
    item, name, stats, recipe, mastery, exp, repair, desc = SHIELD
    write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
        "slot": "offhand", "generation": 1, "series": SERIES + "（ナイト）", "rarity": "common", "required_level": LEVEL,
        "jobs": ["amemaru:knight"], "stats": stats, "durability": 560, "binding": "none", "repair": repair,
        "description": desc})
    write(os.path.join(DATA, "recipes/workshop", item + ".json"), {
        "type": "amemaru:workshop", "workshop": "armor", "mastery": mastery, "mastery_exp": exp, "ingredients": recipe,
        "result": {"item": "amemaru:" + item}})
    if item not in shield_ids:
        manifest["shields"].append({"id": item})
    lang["item.amemaru." + item] = name
    for job, (series, durability, pieces) in ARMOR.items():
        slots = {}
        for item, slot, name, stats, recipe, mastery, exp, repair, desc in pieces:
            write(os.path.join(DATA, "amemaru/equipment", item + ".json"), {
                "slot": slot, "generation": 1, "series": SERIES + "（" + JOB_NAMES[job] + "）", "rarity": "common",
                "required_level": LEVEL, "jobs": ["amemaru:" + job], "stats": stats,
                "durability": durability if slot == "chest" else durability - 40, "binding": "none", "repair": repair,
                "description": desc})
            write(os.path.join(DATA, "recipes/workshop", item + ".json"), {
                "type": "amemaru:workshop", "workshop": "armor", "mastery": mastery, "mastery_exp": exp,
                "ingredients": recipe, "result": {"item": "amemaru:" + item}})
            slots[slot] = item
            lang["item.amemaru." + item] = name
        if series not in series_ids:
            manifest["armor"].append({"series": series, "pieces": slots})
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
    print("流紋シリーズ：", count, "品（武器", len(WEAPONS), "・盾1・防具", count - len(WEAPONS) - 1, "）")


if __name__ == "__main__":
    main()
