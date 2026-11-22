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
}
# 表にない物：レアリティごとの基準額（×（1＋0.08×装備レベル）はゲームの中で計算する）【補完】
RARITY_BASE = {"common": 30, "uncommon": 100, "rare": 400, "epic": 1600, "legendary": 6000, "unique": 20000}

TOWER_MATERIALS = ["amemaru:ancient_stone", "amemaru:moss_iron", "amemaru:crystal_shard", "amemaru:tsunomofu_fluff",
                   "amemaru:bee_stinger", "amemaru:chipped_fang", "amemaru:echo_fragment", "amemaru:soft_horn",
                   "amemaru:ishikure_core", "amemaru:polishing_powder", "amemaru:wavering_crystal"]

# 店：(ファイルの名前, 名前, 店主, 売る品 [(品, 値段)], 買い取る品（None は売る品を買い戻す）)
SHOPS = [
    ("general", "雑貨屋", "マルタ・ポム", [
        ("amemaru:healing_potion_small", 100), ("amemaru:mana_water_small", 120), ("bread", 12), ("apple", 8),
        ("torch", 4), ("lead", 30), ("glass_bottle", 6), ("string", 6), ("paper", 6)], TOWER_MATERIALS),
    ("bakery", "パン屋", "", [("bread", 12), ("cookie", 4), ("pumpkin_pie", 18), ("cake", 60)], None),
    ("greengrocer", "八百屋", "", [("carrot", 5), ("potato", 5), ("beetroot", 4), ("apple", 8), ("melon_slice", 3),
                                  ("sweet_berries", 4), ("pumpkin", 12)], None),
    ("apothecary", "薬屋", "", [("honey_bottle", 24), ("glow_berries", 8), ("sugar", 4), ("glass_bottle", 6)], None),
    ("tools", "道具屋", "", [("shears", 40), ("compass", 60), ("clock", 80), ("spyglass", 120), ("lead", 30)], None),
    ("lights", "ろうそくと灯り", "", [("lantern", 20), ("torch", 4), ("candle", 10), ("honeycomb", 10)], None),
    ("cloth", "布と仕立て", "", [("white_wool", 10), ("magenta_wool", 12), ("light_blue_wool", 12), ("yellow_wool", 12),
                                ("string", 6), ("feather", 4), ("leather", 12)], None),
    ("butcher", "肉と燻製", "", [("cooked_porkchop", 20), ("cooked_beef", 20), ("cooked_mutton", 16), ("cooked_chicken", 14),
                               ("rabbit_stew", 30)], None),
    ("books", "本と地図", "", [("book", 30), ("writable_book", 40), ("map", 40), ("paper", 6), ("feather", 4)], None),
    ("leather", "革と鞄", "", [("leather", 12), ("rabbit_hide", 6), ("string", 6), ("lead", 30)], None),
    ("flowers", "花と種", "", [("poppy", 4), ("dandelion", 4), ("cornflower", 4), ("allium", 6), ("wheat_seeds", 2),
                              ("pumpkin_seeds", 4), ("beetroot_seeds", 2), ("bone_meal", 4)], None),
    ("hardware", "金物屋", "", [("chain", 8), ("iron_nugget", 4), ("shears", 40), ("lantern", 20)], None),
    ("tea", "茶と香辛料", "", [("cocoa_beans", 6), ("sweet_berries", 4), ("sugar", 4), ("honey_bottle", 24)], None),
]
BUY_RATIO = 0.5
# フリーマーケットに出せない品（クエストの品）【補完】。結晶果・見習い装備・装備したボスのレア装備は、ゲームの中の決まりで出せない
UNTRADEABLE = ["amemaru:light_shard"]


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
        for item, price in sells:
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
        doc["sells"] = [{"item": full(i), "price": p} for i, p in sells]
        buy_items = buys if buys is not None else [i for i, _ in sells]
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
    for k in ("amemaru:moss_iron", "amemaru:crystal_shard", "amemaru:ancient_stone_sword", "amemaru:grassland_horn",
              "amemaru:healing_potion_small"):
        print(k, prices[k])
    print("店", len(SHOPS), "・基準価格", len(prices))


if __name__ == "__main__":
    main()
