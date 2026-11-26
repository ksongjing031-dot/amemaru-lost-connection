"""祈願（ガチャ）の決まり・祈願札・ガチャ限の武器とアクセサリーを書き出す（11月27日）。

  python3 -I tools/datagen/build_gacha.py

11月27日のご指示：
  「ガチャ限定の武器や装備を追加する。ガチャを回すときはボスを二回目以上から倒すと出る専用アイテムを使う。
    専用アイテムは各層のボスから各層ごとの専用アイテムを出す仕組みにしてガチャも各層のごとのガチャにする。
    ガチャからはガチャ限のユニーク武器も作る」
決め方【補完】：
  - 祈願札（その階のボスを2回目以降に倒すと、参加した一人ずつに5枚）。1枚で1回、10枚で10回（★4以上が1つは出る）
  - 星：★5 1.5%（その階のガチャ限のユニーク武器。職業は階ごとに替わる）・★4 13.5%（その階のガチャ限のアクセサリー2つと結晶果2つ）・
    ★3 85%（その階の素材・結晶の欠片・薬）。★5が出ないまま60回目は必ず★5（天井。祈願ごと・人ごと）
  - ユニーク武器は、その階の上のレベルで使える、その階のレアより少し強い武器（いつも上限の数。本人専用）と、固有の特殊効果2つ
  - アクセサリーはレジェンダリー（いつも体に出さない指輪とお守り）。固有の特殊効果1つ
  - 引ける所：塔の入口の広間の祈願所の巫女ユノ（npcs/yuno.json）

書き出すもの：
  data/amemaru/amemaru/gacha/default.json・equipment/<ユニーク武器>.json・accessories/<アクセサリー>.json・
  src/main/resources/amemaru/item_types.json（足す）・言語ファイル（ja_jp と en_us は同じ）・models/item/<祈願札・アクセサリー>.json
  （ユニーク武器のモデルと絵は tools/artgen/make_gacha_weapons.py、祈願札とアクセサリーの絵は tools/artgen/make_gacha_art.py）
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data", "amemaru")
ASSETS = os.path.join(RES, "assets", "amemaru")

BOSS_TOKENS = 5
RATE_FIVE = 1.5
RATE_FOUR = 13.5
PITY = 60

# (階, 祈願のID, 祈願の名前, 色, 祈願札のID, 祈願札の名前, 祈願札の説明, ボス)
BANNERS = [
    (1, "grassland", "草原の祈願", "#E2B65A", "wish_tag_grassland", "草原の祈願札", "草原の主の記憶が、薄い琥珀の札になったもの", "草原の主ヴェルドホーン"),
    (2, "forest", "森の祈願", "#7ED957", "wish_tag_forest", "森の祈願札", "森番の古樹の記憶が、若葉の色の札になったもの", "森番の古樹オールドウォード"),
    (3, "waterway", "水路の祈願", "#4FB6F0", "wish_tag_waterway", "水路の祈願札", "大水門の番人の記憶が、青銅のふちの札になったもの", "大水門の番人ハイドラム"),
    (4, "temple", "神殿の祈願", "#F2C94C", "wish_tag_temple", "神殿の祈願札", "祈りの残響の記憶が、金の文字の札になったもの", "祈りの残響カンターレ"),
    (5, "altar", "祭壇の祈願", "#4FCC9E", "wish_tag_altar", "祭壇の祈願札", "古代守護者の記憶が、翠の格子の札になったもの", "古代守護者"),
    (6, "village", "雪村の祈願", "#BFEFFF", "wish_tag_village", "雪村の祈願札", "村長の残響の記憶が、雪の結晶の札になったもの", "村長の残響オルド"),
    (7, "iceforest", "氷林の祈願", "#C9A8FF", "wish_tag_iceforest", "氷林の祈願札", "鏡像獣の記憶が、鏡の面の札になったもの", "鏡像獣リフレクタ"),
    (8, "lake", "湖の祈願", "#A8F7FF", "wish_tag_lake", "湖の祈願札", "声なき湖主の記憶が、真珠色の札になったもの", "声なき湖主ムーネ"),
]

# ★5：ガチャ限のユニーク武器
# (階, ID, 名前, 種類, 職業, 使えるレベル, ステータス, 耐久, 特殊効果, 説明)
UNIQUES = [
    (1, "earth_horn_greatsword", "大地角の大剣", "greatsword", "knight", 8, {"attack": 22}, 460,
     [{"type": "extra_damage", "chance": 25, "percent": 50}],
     "草原の主が祈願に残した記憶から、もう一度形になった大剣。刃は苔むした大地の石、鍔からは象牙の角が二本、空へ反り返る。"
     "振り下ろすたびに刃の琥珀の筋が光り、地の底が遅れてうなる。"),
    (2, "millennium_tree_staff", "千年樹の杖", "staff", "wizard", 13, {"magic": 21, "mp": 50}, 520,
     [{"type": "heal_on_hit", "chance": 100, "percent": 4, "source": "skill"}, {"type": "regen", "percent": 1}],
     "森番の古樹が千年かけて抱いてきた種を、生きた枝が包む杖。杖の先で種が灯のように脈を打ち、葉のあいだを小さな光が飛ぶ。"
     "魔法が当たるたびに、樹液の光が持ち手へ戻ってくる。"),
    (3, "tide_chime_bow", "潮鳴りの弓", "bow", "archer", 17, {"attack": 16, "crit": 3}, 560,
     [{"type": "double_hit", "chance": 25, "percent": 50}],
     "大水門の青銅の管を、そのまま弓に曲げたもの。握りに小さな水門のハンドルがあり、弦を引くと管の中で水が鳴る。"
     "放った矢のあとを、水の写しがもう一本追いかける。"),
    (4, "hymn_great_bell", "聖歌の大鈴", "bell_staff", "healer", 20, {"magic": 24, "mp": 60, "heal_power": 7}, 600,
     [{"type": "heal_ward", "percent": 10}, {"type": "ally_power", "threshold": 8, "percent": 3}],
     "崩れた神殿で、最後まで鳴っていた大鈴。白い大理石の柄の先で金の鈴が揺れ、まわりに紫の音の羽が浮かぶ。"
     "鈴の音に包まれた仲間は、少しだけ強くなる。"),
    (5, "jade_oath_sword", "翠晶の誓剣", "sword", "knight", 25, {"attack": 22, "hp": 40}, 640,
     [{"type": "damage_reduction", "percent": 6}, {"type": "execute", "threshold": 30, "percent": 15}],
     "古代守護者の誓いを写した片手剣。白い石の刃の根もとに、翠の結晶の環が浮かんで回る。"
     "持ち主が、倒れかけた敵へ踏みこむとき、環が強く光る。"),
    (6, "snowlight_fists", "雪灯の拳甲", "gauntlets", "monk", 30, {"attack": 26, "hp": 50}, 680,
     [{"type": "double_hit", "chance": 30, "percent": 40}, {"type": "cold_resist", "percent": 30}],
     "凍った村の暖炉の火を、そのまま閉じこめた籠手。毛皮のふちと金の鎖、甲には雪灯のように光る結晶。"
     "拳が当たると、もう一つの雪の拳が続く。"),
    (7, "mirror_moon_daggers", "鏡月の双刃", "daggers", "rogue", 34, {"attack": 30, "crit": 6}, 720,
     [{"type": "double_hit", "chance": 30, "percent": 60}, {"type": "lost_resist", "percent": 15}],
     "氷の森の鏡が映した、三日月の形の双刃。刃の面がプリズムのように光を割り、振るたびに、鏡の写しの刃がもう一度走る。"
     "薄れにも、少し強い。"),
    (8, "still_lake_pearl_staff", "静湖の真珠杖", "staff", "wizard", 38, {"magic": 40, "mp": 100}, 760,
     [{"type": "extra_damage", "chance": 25, "percent": 60, "source": "skill"}, {"type": "warning_ear", "percent": 100}],
     "沈黙の湖の底で眠っていた真珠を、波紋の環が囲む杖。湖主のひげのような細い糸が、杖の先から流れる。"
     "静寂の中でも、迫る音だけは聞こえる。"),
]
KIND_TO_ITEM = {"gauntlets": "fist"}   # item_types.json の kind（籠手は fist）

# ★4：ガチャ限のアクセサリー（指輪とお守り。体には出さない）
# (階, ID, 名前, 枠, 特殊効果, 説明)
ACCESSORIES = [
    (1, "reed_whistle_ring", "草笛の指輪", "ring", {"type": "move_speed", "percent": 5},
     "草原の風を受けて鳴る、草笛の形の指輪。走るほど、軽い音がする。"),
    (1, "sunny_spot_charm", "陽だまりのお守り", "charm", {"type": "regen", "percent": 1},
     "草原の日なたの温かさを、小さな琥珀に閉じこめたお守り。"),
    (2, "moss_cloak_ring", "苔衣の指輪", "ring", {"type": "damage_reduction", "percent": 4},
     "苔をまとった古樹の枝を巻いた指輪。打たれても、苔がやわらかく受け止める。"),
    (2, "forest_light_charm", "森灯のお守り", "charm", {"type": "heal_on_hit", "chance": 30, "percent": 3},
     "迷い灯の光を、葉の形の器に入れたお守り。"),
    (3, "cogwheel_ring", "歯車の指輪", "ring", {"type": "extra_damage", "chance": 10, "percent": 30},
     "水路の機兵の歯車を磨いた指輪。噛み合うたびに、一撃が重くなる。"),
    (3, "clear_water_charm", "澄み水のお守り", "charm", {"type": "regen", "percent": 1.5},
     "大水門の澄んだ水を、青銅の小瓶に詰めたお守り。"),
    (4, "echo_note_ring", "残響の指輪", "ring", {"type": "ally_power", "threshold": 8, "percent": 2},
     "祈りの歌の残響を、銀の輪に刻んだ指輪。仲間の声が、指先に届く。"),
    (4, "bell_tone_charm", "鈴音のお守り", "charm", {"type": "heal_ward", "percent": 6},
     "神殿の小さな鈴を下げたお守り。癒しの鈴に合わせて鳴り、癒した仲間を守る。"),
    (5, "knot_lattice_ring", "結び目の指輪", "ring", {"type": "party_ward", "threshold": 8, "percent": 3},
     "結び台の格子を写した、白い石の指輪。"),
    (5, "record_core_charm", "記録核のお守り", "charm", {"type": "execute", "threshold": 30, "percent": 8},
     "翠の記録核の、ほんのかけら。弱った敵のほころびを、そっと教える。"),
    (6, "frost_weave_ring", "霜編みの指輪", "ring", {"type": "cold_resist", "percent": 30},
     "雪村の毛糸で、霜の模様を編みこんだ指輪。"),
    (6, "hearth_fire_charm", "暖炉火のお守り", "charm", {"type": "regen", "percent": 1.5},
     "雪灯亭の暖炉の火を、一粒だけ移した温かいお守り。"),
    (7, "mirror_shard_ring", "鏡片の指輪", "ring", {"type": "double_hit", "chance": 15, "percent": 30},
     "氷の鏡のかけらをはめた指輪。攻撃の影を、もう一つ映す。"),
    (7, "twilight_charm", "薄明のお守り", "charm", {"type": "lost_resist", "percent": 20},
     "夜明け前の氷林の光を閉じこめたお守り。薄れに抗う。"),
    (8, "ripple_ring", "波紋の指輪", "ring", {"type": "extra_damage", "chance": 15, "percent": 40},
     "湖の波紋を、真珠色の輪に写した指輪。"),
    (8, "pearl_charm", "真珠のお守り", "charm", {"type": "warning_ear", "percent": 100},
     "湖主のひげの根もとの真珠を、糸で包んだお守り。静けさの中で、迫る音を知らせる。"),
]

# ★4の結晶果（階ごとに2つ）と、★3（その階の素材・結晶の欠片・薬）
FRUITS = {1: ("crimson", "jade"), 2: ("azure", "jade"), 3: ("amber", "azure"), 4: ("white", "azure"), 5: ("gold", "violet"),
          6: ("crimson", "white"), 7: ("violet", "gold"), 8: ("azure", "amber")}
MATERIALS = {
    1: ["ancient_stone", "moss_iron", "tsunomofu_fluff", "bee_stinger", "chipped_fang"],
    2: ["old_bark", "glowcap", "wisp_wick", "shade_tail", "moss_iron"],
    3: ["gnawed_cog", "murky_gel", "waterway_bronze", "catfish_whisker", "flow_crystal"],
    4: ["statue_shard", "song_crystal", "beetle_shell", "sacred_mane"],
    5: ["watcher_lens", "sentinel_plate", "drone_spring", "jade_core_shard"],
    6: ["frost_silver", "ice_crystal", "frozen_fur", "frost_fang"],
    7: ["ice_feather", "mirror_ice", "ice_antler", "icicle_wing"],
    8: ["fish_scale", "voice_stone", "crane_plume", "mist_thread"],
}


def effect_text(effect):
    kind = effect["type"]
    p = fmt(effect.get("percent", 0))
    if kind == "extra_damage":
        return "攻撃が当たると%d%%の確率で、与えたダメージの%s%%を追加で与える" % (effect["chance"], p)
    if kind == "damage_reduction":
        return "敵から受けるダメージ −%s%%" % p
    if kind == "heal_on_hit":
        return "攻撃が当たると%d%%の確率で、与えたダメージの%s%%だけHPが回復する" % (effect["chance"], p)
    if kind == "execute":
        return "HPが%d%%以下の敵に与えるダメージ +%s%%" % (effect["threshold"], p)
    if kind == "move_speed":
        return "移動の速さ +%s%%" % p
    if kind == "regen":
        return "5秒ごとに、最大HPの%s%%だけHPが回復する" % p
    if kind == "cold_resist":
        return "寒さ（凍え・凍結）と、遅くなる効果の時間 −%s%%" % p
    if kind == "lost_resist":
        return "ロスト耐性 +%s%%（ロスト状態の時間が短くなる）" % p
    if kind == "ally_power":
        return "%sブロック以内の味方1人ごとに、与えるダメージ +%s%%（5人まで）" % (fmt(effect["threshold"]), p)
    if kind == "heal_ward":
        return "スキルで味方を回復すると、その味方の防御 +%s%%（5秒）" % p
    if kind == "party_ward":
        return "%sブロック以内に味方が3人以上いると、自分とその味方の受けるダメージ −%s%%" % (fmt(effect["threshold"]), p)
    if kind == "double_hit":
        return "攻撃が当たると%d%%の確率で、写しがもう一度当たる（与えたダメージの%s%%）" % (effect["chance"], p)
    if kind == "warning_ear":
        return "ロスト状態「静寂」の間も、予兆の音が自分にだけ聞こえる"
    raise ValueError(kind)


def fmt(value):
    return ("%d" % value) if abs(value - round(value)) < 1e-9 else ("%.1f" % value)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def read_json(path):
    with open(path, encoding="utf-8") as fp:
        return json.load(fp)


def banner_json():
    banners = []
    for floor, bid, name, color, token, token_name, _desc, boss in BANNERS:
        unique = next(u for u in UNIQUES if u[0] == floor)
        accessories = [a for a in ACCESSORIES if a[0] == floor]
        fruit = FRUITS[floor]
        potion = "healing_potion_small" if floor <= 3 else "healing_potion_medium"
        water = "mana_water_small" if floor <= 3 else "mana_water_medium"
        three = [{"item": "amemaru:crystal_shard", "count": [2, 4], "weight": 6},
                 {"item": "amemaru:" + potion, "count": [2, 3], "weight": 5},
                 {"item": "amemaru:" + water, "count": [2, 3], "weight": 5},
                 {"item": "amemaru:repair_kit", "weight": 2},
                 {"item": "amemaru:return_feather", "count": [1, 2], "weight": 2}]
        for m in MATERIALS[floor]:
            three.append({"item": "amemaru:" + m, "count": [2, 4], "weight": 4})
        banners.append({
            "id": bid,
            "floor": floor,
            "name": name,
            "text": "%sの記憶を引く。★5は「%s」（%s）。" % (boss, unique[2], JOB_NAMES[unique[4]]),
            "token": "amemaru:" + token,
            "color": color,
            "rates": {"5": RATE_FIVE, "4": RATE_FOUR},
            "pity": PITY,
            "pool": {
                "5": [{"item": "amemaru:" + unique[1]}],
                "4": [{"item": "amemaru:" + a[1], "weight": 3} for a in accessories]
                     + [{"item": "amemaru:crystal_fruit_" + f, "weight": 2} for f in fruit],
                "3": three,
            },
        })
    return {
        "_comment": [
            "祈願（ガチャ）の決まり（11月27日のご指示。tools/datagen/build_gacha.py で書き出す。数は【補完】）。",
            "boss_tokens：その階のボスを2回目以降に倒すと、参加した一人ずつにその階の祈願札をいくつ渡すか。",
            "ten：10回まとめて引くと、guarantee の星以上が1つは出る。npc_range：祈願所の巫女から何ブロックまで離れて引けるか。",
            "banners：階ごとの祈願。rates は★5・★4の確率（%。のこりは★3）、pity は★5が出ないまま何回目で必ず★5か（天井）。",
        ],
        "boss_tokens": BOSS_TOKENS,
        "ten": {"count": 10, "guarantee": 4},
        "npc_range": 8,
        "banners": banners,
    }


JOB_NAMES = {"knight": "ナイト", "wizard": "ウィザード", "healer": "ヒーラー", "archer": "アーチャー", "rogue": "ローグ", "monk": "モンク"}


def main():
    lang = {}
    manifest_path = os.path.join(RES, "amemaru", "item_types.json")
    manifest = read_json(manifest_path)

    # 祈願札（素材の一覧に足す。重なる）
    known = {m["id"] for m in manifest["materials"]}
    for floor, bid, name, color, token, token_name, desc, boss in BANNERS:
        if token not in known:
            manifest["materials"].append({"id": token, "rarity": "epic"})
        lang["item.amemaru." + token] = token_name
        lang["item.amemaru." + token + ".desc"] = (desc + "。%d階のボス（%s）を2回目から倒すと手に入る。塔の入口の祈願所で「%s」を1回引ける。"
                                                   % (floor, boss, name))
        write_json(os.path.join(ASSETS, "models", "item", token + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + token}})

    # ★5：ユニーク武器
    known = {w["id"] for w in manifest["weapons"]}
    for (floor, wid, name, kind, job, level, stats, durability, effects, desc) in UNIQUES:
        if wid not in known:
            manifest["weapons"].append({"id": wid, "kind": KIND_TO_ITEM.get(kind, kind)})
        write_json(os.path.join(DATA, "amemaru", "equipment", wid + ".json"), {
            "_comment": "%s（%d階の祈願の★5。ガチャ限のユニーク武器。11月27日【補完】。tools/datagen/build_gacha.py）" % (name, floor),
            "slot": "weapon",
            "generation": (floor - 1) // 5 + 1,
            "series": "%d階の祈願" % floor,
            "rarity": "unique",
            "required_level": level,
            "jobs": ["amemaru:" + job],
            "stats": stats,
            "fixed": True,
            "durability": durability,
            "binding": "bound",
            "effects": effects,
            "rare": True,
            "repair": [{"item": "amemaru:crystal_shard", "count": 3}],
            "description": desc + "（%d階の祈願の品）" % floor,
        })
        lang["item.amemaru." + wid] = name

    # ★4：アクセサリー
    known = {a["id"] for a in manifest["accessories"]}
    for (floor, aid, name, slot, effect, desc) in ACCESSORIES:
        if aid not in known:
            entry = {"id": aid, "slot": slot}
            if effect["type"] == "warning_ear":
                # アクセサリーの定義はサーバーにしかないので、クライアントが音を分けられるように種類にも書く
                entry["warning_ear"] = True
            manifest["accessories"].append(entry)
        write_json(os.path.join(DATA, "amemaru", "accessories", aid + ".json"), {
            "_comment": "%s（%d階の祈願の★4。ガチャ限のアクセサリー。11月27日【補完】。tools/datagen/build_gacha.py）" % (name, floor),
            "slot": slot,
            "generation": (floor - 1) // 5 + 1,
            "floor": floor,
            "rarity": "legendary",
            "effects": [effect],
        })
        lang["item.amemaru." + aid] = name
        lang["item.amemaru." + aid + ".desc"] = desc + "（%d階の祈願の品）" % floor
        lang["item.amemaru." + aid + ".effect"] = effect_text(effect)
        write_json(os.path.join(ASSETS, "models", "item", aid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + aid}})

    write_json(manifest_path, manifest)
    write_json(os.path.join(DATA, "amemaru", "gacha", "default.json"), banner_json())
    for name in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", name + ".json")
        data = read_json(path)
        data.update(lang)
        write_json(path, data)
    # 職業の武器のタグ（スキルを使うときに見る）を、装備の定義に合わせる
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import weapon_tags
    weapon_tags.sync()
    print("祈願", len(BANNERS), "・ユニーク武器", len(UNIQUES), "・アクセサリー", len(ACCESSORIES), "・言語", len(lang))


if __name__ == "__main__":
    main()
