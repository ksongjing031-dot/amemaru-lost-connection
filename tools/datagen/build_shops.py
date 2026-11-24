"""商店街の店の品と値段・相場の基準価格を、1か所から書き出す（Phase 6）。

書き出すもの（どれも data/amemaru/amemaru/ の下。データパックで上書きできる）：
  - shops/<店>.json：店の名前・店主・売る品（値段）・買い取る品（基準価格に対する割合）
  - market_prices/default.json：相場の基準価格（Phase 0-A 20章「初期相場表の考え方【提案】」）

値段の決め方【補完】：
  - 店で売る品の基準価格は、売値の80%（Phase 0-A 20章。同じ品を2軒以上で売るときは、安い方の売値から）
  - 塔の素材は「1個を手に入れるまでの平均時間（分）× 第1章の1分あたりの稼ぎ（約33ノット）」。1分に3体倒せるとして、
    1体あたり11ノット ÷ 1体あたりの落ちる数。苔鉄40・結晶の欠片110は Phase 0-A の計算例のまま
  - 作った装備・アクセサリーは、材料の基準価格の合計 × 1.2（工房のレシピから計算。10ノット単位）
  - レアの装備は、素材と同じ計算（落ちる確率から）。草原の角笛は Phase 0-A の計算例のまま
  - 店の買い取りは、基準価格の50%（塔の素材は雑貨屋だけ。ほかの店は、自分の店で売る品を買い戻す）。
    買い取り値は売値より必ず安いので、買って売るだけでは増えない
  - 結晶果・見習い装備・光のかけら（石柱の核に使う、クエストの品）は取引できないので、表に入れない
使い方: python3 -I tools/datagen/build_shops.py
"""
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "amemaru")
OUT = os.path.join(DATA, "amemaru")

# 塔の素材（第1章）：1体あたりの落ちる数（ルートテーブルの確率 × 数の平均）と、敵の種類
PER_KILL = 11.0  # 33ノット/分 ÷ 3体/分
MATERIALS = {
    "amemaru:moss_iron": 40,            # Phase 0-A の計算例（1階の雑魚から30% → 1.1分 × 33）
    "amemaru:crystal_shard": 110,       # Phase 0-A の計算例（2階の雑魚から10% → 3.3分 × 33）
    "amemaru:ancient_stone": None,      # イシクレ兵から70%で1〜2個
    "amemaru:tsunomofu_fluff": None,    # ツノモフから70%で1〜2個
    "amemaru:bee_stinger": None,        # 結晶蜂から60%
    "amemaru:chipped_fang": None,       # カケキバ狼から50%
    "amemaru:echo_fragment": None,      # 1階の雑魚から3〜5%（平均4%）
    "amemaru:soft_horn": None,          # ツノモフから2%（レア）
    "amemaru:ishikure_core": None,      # イシクレ兵から1.5%（レア）
    # 厳選の素材（Phase 6 その4。Phase 0-A 21章「厳選素材」）【補完】
    "amemaru:polishing_powder": None,   # イシクレ兵から20%で1〜2個（採掘はまだないので、石の体を削った粉として）
    "amemaru:wavering_crystal": 280,    # 1階のボスから60%（1回5分 → 8.3分 × 33）。フリマでも
    "amemaru:binding_thread": 500,      # NPCの依頼のお礼だけ（釣り・カジノの景品は Phase 7）。少ないので高め
    "amemaru:clear_echo": 1500,         # ★4以上の分解と章のボスだけ
    # 3階の素材（Phase 8 その2）【補完】。3階の敵は1階の敵の3〜4倍の時間がかかるので、落ちる確率の計算より高め
    "amemaru:gnawed_cog": 30,           # 水門ネズミから60%で1〜2個・掃除機兵から25%
    "amemaru:murky_gel": 30,            # 澱みスライムから75%で1〜2個（分かれた小さいのからも35%）
    "amemaru:waterway_bronze": 90,      # 水路掃除機兵から60%で1〜2個・番人から3〜5個（めずらしい）
    "amemaru:catfish_whisker": 120,     # 牙ナマズから55%・番人から60%（めずらしい）
    "amemaru:flow_crystal": 600,        # 名のある魔物から35%・番人から60%・宝箱から20%（レア。流紋シリーズの芯）
    # 2階の素材（11月24日。階ごとのアクセサリーの材料）【補完】。2階の敵は1階の2〜3倍の時間がかかる
    "amemaru:old_bark": 25,             # 樹皮兵から55%で1〜2個
    "amemaru:glowcap": 25,              # ポコダケから55%で1〜2個
    "amemaru:wisp_wick": 60,            # 迷い灯から40%
    "amemaru:shade_tail": 70,           # 葉影ギツネから40%（素早く、倒しにくい）
    # 4階・5階の素材（11月24日）【補完】
    "amemaru:statue_shard": 35,         # 沈黙の石像から60%で1〜2個
    "amemaru:song_crystal": 45,         # 祈り手の残響から55%
    "amemaru:beetle_shell": 55,         # 結晶甲虫から50%（硬い）
    "amemaru:sacred_mane": 90,          # 欠けた聖獣から45%（強い）
    "amemaru:prayer_stone": 700,        # 名のある魔物から35%・カンターレから60%・宝箱から15%（レア）
    "amemaru:watcher_lens": 60,         # 監視眼から55%
    "amemaru:sentinel_plate": 60,       # 防衛兵から60%で1〜2個
    "amemaru:drone_spring": 70,         # 修復ドローンから50%
    "amemaru:jade_core_shard": 900,     # 名のある魔物から35%・古代守護者から100%（2〜3個）（レア。守護者シリーズの芯）
}
DROPS = {
    "amemaru:ancient_stone": 0.7 * 1.5,
    "amemaru:tsunomofu_fluff": 0.7 * 1.5,
    "amemaru:bee_stinger": 0.6,
    "amemaru:chipped_fang": 0.5,
    "amemaru:echo_fragment": 0.04,
    "amemaru:soft_horn": 0.02,
    "amemaru:ishikure_core": 0.015,
    "amemaru:polishing_powder": 0.2 * 1.5,
}
# レアの装備（落ちる確率から。素材と同じ計算）
RARE_GEAR = {
    "amemaru:sentinel_blade": 0.01,          # イシクレ兵から1%
    "amemaru:bee_crystal_staff": 0.01,       # 結晶蜂から1%
    "amemaru:chipped_fang_greatsword": 0.008,  # カケキバ狼から0.8%
}
FIXED = {
    "amemaru:grassland_horn": 2050,          # Phase 0-A の計算例（1回5分のボスから8% → 62分 × 33）
    "amemaru:elder_branch_staff": 3300,      # 2階のボスから6%（1回6分 → 100分 × 33）【補完】
    "amemaru:prayer_bell_staff": 4600,       # 4階のボスから5%（1回7分 → 140分 × 33）【補完】
    # 階ごとのレアのお守り（名のある魔物から5〜6%。11月24日）【補完】
    "amemaru:shade_tail_charm": 2400,
    "amemaru:water_mirror_charm": 2600,
    "amemaru:broken_seal_charm": 2800,
    "amemaru:altar_knot_charm": 3000,
}
# 表にない物：レアリティごとの基準額（×（1＋0.08×装備レベル）はゲームの中で計算する）【補完】
RARITY_BASE = {"common": 30, "uncommon": 100, "rare": 400, "epic": 1600, "legendary": 6000, "unique": 20000}

TOWER_MATERIALS = ["amemaru:ancient_stone", "amemaru:moss_iron", "amemaru:crystal_shard", "amemaru:tsunomofu_fluff",
                   "amemaru:bee_stinger", "amemaru:chipped_fang", "amemaru:echo_fragment", "amemaru:soft_horn",
                   "amemaru:ishikure_core", "amemaru:polishing_powder", "amemaru:wavering_crystal"]

# 店：(ファイルの名前, 名前, 店主, 売る品 [(品, 値段) か (品, 値段, 見た目の名前, 色)], 買い取る品（None は売る品を買い戻す）)
# 11月23日の直し（「色々な店があるがどこで使うかわからないものが多いので実用的なものを置く」）：どこにも使い道のない品
# （羊毛・糸・羽根・種・骨粉・鎖・ハサミ・方位磁石・時計・リード・ろうそく・カカオなど）を外し、回復・料理屋の材料・
# 贈り物・修理・帰還・灯り・工房の材料・見た目の服に入れ替えた。どの品にも USES の「使い道」を付ける（店の画面と説明文に出る）
TAILOR_INDIGO, TAILOR_NATURAL, LEATHER_BROWN = "#3B4A8C", "#E6DCC4", "#7A4E2A"
SHOPS = [
    ("general", "雑貨屋", "マルタ・ポム", [
        ("amemaru:healing_potion_small", 100), ("amemaru:mana_water_small", 120), ("amemaru:return_feather", 150),
        ("amemaru:repair_kit", 200), ("bread", 12), ("apple", 8)], TOWER_MATERIALS),
    ("bakery", "パン屋", "", [("bread", 12), ("cookie", 4), ("pumpkin_pie", 18)], None),
    ("greengrocer", "八百屋", "", [("potato", 5), ("carrot", 5), ("beetroot", 4), ("apple", 8), ("sweet_berries", 4),
                                  ("melon_slice", 3)], None),
    ("apothecary", "薬屋", "", [("amemaru:healing_potion_small", 100), ("amemaru:healing_potion_medium", 260),
                               ("amemaru:mana_water_small", 120), ("amemaru:mana_water_medium", 300), ("amemaru:antidote", 60),
                               ("honey_bottle", 24), ("glow_berries", 8)], None),
    ("tools", "道具屋", "", [("amemaru:return_feather", 150), ("amemaru:repair_kit", 200), ("spyglass", 120)], None),
    ("lights", "ろうそくと灯り", "", [("amemaru:hand_lantern", 300), ("amemaru:lantern_vial", 40)], None),
    ("cloth", "布と仕立て", "", [
        ("leather_helmet", 80, "tailor_hat_indigo", TAILOR_INDIGO), ("leather_chestplate", 120, "tailor_coat_indigo", TAILOR_INDIGO),
        ("leather_leggings", 100, "tailor_trousers_indigo", TAILOR_INDIGO), ("leather_boots", 70, "tailor_boots_indigo", TAILOR_INDIGO),
        ("leather_helmet", 80, "tailor_hat_natural", TAILOR_NATURAL),
        ("leather_chestplate", 120, "tailor_coat_natural", TAILOR_NATURAL),
        ("leather_leggings", 100, "tailor_trousers_natural", TAILOR_NATURAL),
        ("leather_boots", 70, "tailor_boots_natural", TAILOR_NATURAL)], None),
    ("butcher", "肉と燻製", "", [("cooked_beef", 20), ("cooked_porkchop", 20), ("cooked_mutton", 16), ("cooked_chicken", 14)], None),
    ("books", "本と地図", "", [("book", 30), ("paper", 6), ("map", 40)], None),
    ("leather", "革と鞄", "", [
        ("amemaru:repair_kit", 200),
        ("leather_helmet", 60, "leather_hat_brown", LEATHER_BROWN), ("leather_chestplate", 100, "leather_coat_brown", LEATHER_BROWN),
        ("leather_leggings", 80, "leather_trousers_brown", LEATHER_BROWN), ("leather_boots", 60, "leather_boots_brown", LEATHER_BROWN)],
     None),
    ("flowers", "花と種", "", [("poppy", 4), ("dandelion", 4), ("cornflower", 4), ("allium", 6)], None),
    ("hardware", "金物屋", "", [("amemaru:repair_kit", 200), ("amemaru:moss_iron", 80), ("amemaru:ancient_stone", 25),
                               ("amemaru:crystal_shard", 220)], None),
    ("tea", "茶と香辛料", "", [("amemaru:herb_tea", 30), ("sugar", 4), ("honey_bottle", 24), ("sweet_berries", 4)], None),
]
# 見た目の服の名前（店の画面と、買った品の名前）
LOOKS = {
    "tailor_hat_indigo": "仕立ての帽子（藍）", "tailor_coat_indigo": "仕立ての上着（藍）",
    "tailor_trousers_indigo": "仕立てのズボン（藍）", "tailor_boots_indigo": "仕立ての靴（藍）",
    "tailor_hat_natural": "仕立ての帽子（生成り）", "tailor_coat_natural": "仕立ての上着（生成り）",
    "tailor_trousers_natural": "仕立てのズボン（生成り）", "tailor_boots_natural": "仕立ての靴（生成り）",
    "leather_hat_brown": "革の帽子（茶）", "leather_coat_brown": "革の上着（茶）",
    "leather_trousers_brown": "革のズボン（茶）", "leather_boots_brown": "革の靴（茶）",
}
# 品ごとの使い道（言語ファイルの itemuse.<名前空間>.<名前>。店の画面の2行目と、持ち物の説明文に出る）【補完】
FOOD_HP, FOOD_MP = "食べるとHPが少し戻る", "食べるとMPが少し戻る"
USES = {
    "amemaru:healing_potion_small": "HPが25%戻る（戦いの最中に使える）・贈り物",
    "amemaru:healing_potion_medium": "HPが50%戻る（戦いの最中に使える）",
    "amemaru:mana_water_small": "MPが30%戻る（戦いの最中に使える）",
    "amemaru:mana_water_medium": "MPが60%戻る（戦いの最中に使える）",
    "amemaru:herb_tea": "飲むとMPが15%戻る（食べ物の仲間）",
    "amemaru:antidote": "毒を治し、そのあと30秒は毒にならない（2階のポコダケの胞子に）",
    "amemaru:return_feather": "塔の中で3秒持つと、街の塔の門へ帰る",
    "amemaru:repair_kit": "防具とホットバーの装備の耐久を30%ずつ戻す",
    "amemaru:hand_lantern": "手に持つと、まわりが明るくなる（暗い所で）",
    "amemaru:lantern_vial": "その場とまわりに、90秒の灯りをともす",
    "amemaru:moss_iron": "工房の材料（作る・強化・修理）・贈り物",
    "amemaru:ancient_stone": "工房の材料（古代石シリーズ・修理）・贈り物",
    "amemaru:crystal_shard": "工房の材料（+4からの強化）・贈り物",
    "minecraft:bread": FOOD_HP + "・料理屋の材料・贈り物",
    "minecraft:apple": FOOD_HP + "・料理屋の材料",
    "minecraft:carrot": FOOD_HP + "・料理屋の材料",
    "minecraft:potato": "料理屋の材料（魚と根菜のスープ）",
    "minecraft:beetroot": FOOD_HP + "・料理屋の材料",
    "minecraft:melon_slice": FOOD_HP,
    "minecraft:sweet_berries": FOOD_MP + "・贈り物",
    "minecraft:glow_berries": FOOD_MP,
    "minecraft:honey_bottle": "飲むとMPが少し戻る・料理屋の材料・贈り物",
    "minecraft:sugar": "料理屋の材料（魚の甘辛煮）",
    "minecraft:cookie": FOOD_MP + "・贈り物",
    "minecraft:pumpkin_pie": "食べるとHPが戻る",
    "minecraft:cooked_beef": "食べるとHPが戻る・贈り物",
    "minecraft:cooked_porkchop": "食べるとHPが戻る",
    "minecraft:cooked_mutton": FOOD_HP,
    "minecraft:cooked_chicken": FOOD_HP,
    "minecraft:book": "贈り物（本の好きな人に）",
    "minecraft:paper": "贈り物（本の好きな人に）",
    "minecraft:map": "使うと、まわりの地図になる（広い所で道に迷わない）",
    "minecraft:spyglass": "のぞくと遠くまで見える（広い所で道を探す）",
    "minecraft:poppy": "贈り物（花の好きな人に）",
    "minecraft:dandelion": "贈り物（花の好きな人に）",
    "minecraft:cornflower": "贈り物（花の好きな人に）",
    "minecraft:allium": "贈り物（花の好きな人に）",
}
LOOK_USE = "見た目だけの服（守りもステータスも変わらない）"
LANG = [os.path.join(ROOT, "src", "main", "resources", "assets", "amemaru", "lang", n) for n in ("ja_jp.json", "en_us.json")]
BUY_RATIO = 0.5
# フリーマーケットに出せない品（クエストの品）【補完】。結晶果・見習い装備・装備したボスのレア装備は、ゲームの中の決まりで出せない
# 2階の結び目の灯（鐘を鳴らすともらえる）と森番の灯（ボス戦で落ちる）も、その場の仕掛けの品なので出せない（Phase 8 その1）
UNTRADEABLE = ["amemaru:light_shard", "amemaru:knot_lantern", "amemaru:warden_light"]


def full(item):
    return item if ":" in item else "minecraft:" + item


def round_to(value, step):
    return int(step * round(value / step))


def material_prices():
    prices = {}
    for item, fixed in MATERIALS.items():
        if fixed is not None:
            prices[item] = fixed
        else:
            raw = PER_KILL / DROPS[item]
            prices[item] = round_to(raw, 10) if raw >= 100 else max(1, round_to(raw, 2) if raw < 20 else round_to(raw, 5))
    return prices


def crafted_prices(materials):
    prices = {}
    for path in sorted(glob.glob(os.path.join(DATA, "recipes", "workshop", "*.json"))):
        recipe = json.load(open(path, encoding="utf-8"))
        item = "amemaru:" + os.path.splitext(os.path.basename(path))[0]
        total = 0
        for ing in recipe["ingredients"]:
            if ing["item"] not in materials:
                raise SystemExit("基準価格のない材料: %s（%s）" % (ing["item"], item))
            total += materials[ing["item"]] * ing.get("count", 1)
        prices[item] = round_to(total * 1.2, 10)
    return prices


def main():
    materials = material_prices()
    prices = {}
    # 店で売る品：売値の80%（安い方の売値から）
    lowest = {}
    for _, _, _, sells, _ in SHOPS:
        for entry in sells:
            item, price = entry[0], entry[1]
            key = full(item)
            lowest[key] = min(lowest.get(key, price), price)
    for key, price in sorted(lowest.items()):
        prices[key] = max(1, round(price * 0.8))
    prices.update(materials)
    prices.update(crafted_prices(materials))
    for item, chance in RARE_GEAR.items():
        prices[item] = round_to(PER_KILL / chance, 10)
    prices.update(FIXED)

    os.makedirs(os.path.join(OUT, "shops"), exist_ok=True)
    for key, name, keeper, sells, buys in SHOPS:
        doc = {"name": name}
        if keeper:
            doc["keeper"] = keeper
        doc["sells"] = []
        for entry in sells:
            one = {"item": full(entry[0]), "price": entry[1]}
            if len(entry) > 2:
                one["look"] = entry[2]       # 見た目の服（守りも強さもない。名前は shop.amemaru.look.<名前>）
                one["color"] = entry[3]
            elif full(entry[0]) not in USES:
                raise SystemExit("使い道（USES）のない品: " + full(entry[0]) + "（" + key + "）")
            doc["sells"].append(one)
        # 見た目の服は買い取らない（同じ革の帽子でも、ほかの物と見分けられないため）。塔の素材の買い取りは雑貨屋だけ
        buy_items = buys if buys is not None else [e[0] for e in sells if len(e) == 2 and full(e[0]) not in TOWER_MATERIALS]
        seen = []
        for i in buy_items:
            if full(i) not in seen:
                seen.append(full(i))
        for i in seen:
            if prices.get(i, 0) * BUY_RATIO >= lowest.get(i, 10 ** 9):
                raise SystemExit("買い取り値が売値以上になる: " + i)
        doc["buys"] = [{"item": i, "ratio": BUY_RATIO} for i in seen]
        with open(os.path.join(OUT, "shops", key + ".json"), "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, indent=2)
            f.write("\n")
    os.makedirs(os.path.join(OUT, "market_prices"), exist_ok=True)
    table = {
        "_comment": "相場の基準価格（ノット。Phase 0-A 20章の考え方【提案】。数は tools/datagen/build_shops.py で計算した【補完】）。"
                    "フリーマーケットの値段の幅（基準価格の50〜200%）と、店の買い取り値に使う。表にない物は、"
                    "rarity_base ×（1＋level_step×装備レベル）。アクセサリーは、さらに品質の倍率（★1〜5）をかける。"
                    "untradeable はフリーマーケットに出せない品（クエストの品）",
        "prices": dict(sorted(prices.items())),
        "rarity_base": RARITY_BASE,
        "accessory_quality": [0.6, 0.8, 1.0, 1.6, 3.0],
        "level_step": 0.08,
        # クエストの品（Phase 0-A 20章：取引できない）。光のかけらは1階の石柱の核に使う
        "untradeable": UNTRADEABLE,
    }
    with open(os.path.join(OUT, "market_prices", "default.json"), "w", encoding="utf-8") as f:
        json.dump(table, f, ensure_ascii=False, indent=2)
        f.write("\n")
    # 言語ファイル：使い道と、見た目の服の名前（日本語と英語の表は同じ中身）
    for path in LANG:
        with open(path, encoding="utf-8") as f:
            lang = json.load(f)
        for item, text in USES.items():
            ns, name = item.split(":")
            lang["itemuse.%s.%s" % (ns, name)] = text
        lang["itemuse.amemaru.look"] = LOOK_USE
        for look, name in LOOKS.items():
            lang["shop.amemaru.look." + look] = name
        lang["shop.amemaru.look_lore"] = "見た目の品（守りも強さもない）"
        with open(path, "w", encoding="utf-8") as f:
            f.write(json.dumps(lang, ensure_ascii=False, indent=2) + "\n")
    for k in ("amemaru:moss_iron", "amemaru:crystal_shard", "amemaru:ancient_stone_sword", "amemaru:grassland_horn",
              "amemaru:healing_potion_small"):
        print(k, prices[k])
    print("店", len(SHOPS), "・基準価格", len(prices))


if __name__ == "__main__":
    main()
