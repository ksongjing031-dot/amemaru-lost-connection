"""Phase 7 その2（釣り）の定義を書き出し、Mod の読み込みと同じ決まりで確かめる。

使い方：python3 tools/datagen/build_fishing.py          （書き出して確かめる。問題があれば一覧を出して 1 で終わる）
        python3 tools/datagen/build_fishing.py --check  （書き出さずに、今のファイルを確かめるだけ）

書き出すもの：
  data/amemaru/amemaru/fishing/<名前>.json        魚の表（Phase 0-A 15章「魚図鑑：全40種【提案】」。Phase 0-B 6章のフォルダ「fishing/」）
  data/amemaru/amemaru/fishing_rules/default.json  釣りの決まり（ミニゲーム・釣り場・記憶魚と湖の主の確率・大物・留め糸・大会）
  assets/amemaru/models/item/fish_<名前>.json      魚のアイテムのモデル（絵は tools/artgen/make_fish.py）
  amemaru/item_types.json の "fish"                魚のアイテムの種類（起動時に登録する）
  lang/ja_jp.json・en_us.json の item.amemaru.fish_<名前>

章ごとの魚（Phase 0-A 15章）：第1章は川魚、第2章は一部が凍った釣り堀の氷下の魚、第3章は温かい湧き水の魚、第4章は空を映す
水面の「空魚」。章ごとに記憶魚が1種（古代の人々の短い記憶が記録帳に入る）。湖の主「記憶の主」は湖だけ（40種め）。
名前と説明（ポッカ爺のひと言）は【補完】。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/amemaru/amemaru"
ASSETS = ROOT / "src/main/resources/assets/amemaru"
MANIFEST = ROOT / "src/main/resources/amemaru/item_types.json"

# 釣り場：pond＝釣り堀、lake＝湖畔の釣り場（共同事業①のあと）、river＝そのほかの水（川・水路・塔の中の水）
# 形（絵）：slim・round・tiny・long・eel・pike・goby・catfish・cod・salmon・sturgeon・ray・shrimp・jelly・koi・arowana・
#          gourami・perch・lord
# (名前, 日本語の名前, 章, 星, 釣り場, 最小cm, 最大cm, 基準の値, 手ごたえ0〜1, 形, 色(体, 背, 腹, ひれ, 模様, 光), 模様, 説明, 記憶の記録)
FISH = [
    # ---------------------------------------------------------------- 第1章：川魚
    ("pebble_dace", "小石ハヤ", 1, 1, ["pond", "river", "lake"], 8, 15, 18, 0.15, "slim",
     ("#9A9A86", "#5E6252", "#D8D8C8", "#7E8270", "#4A4E40", None), "dots",
     "どこの水にもいる、すばしこいやつだなぁ。最初の一匹は、たいていこいつだ"),
    ("moss_crucian", "苔ブナ", 1, 1, ["pond", "lake"], 12, 24, 22, 0.2, "round",
     ("#7E8A4A", "#4E5A2C", "#C8C88A", "#5E6A34", "#3E4A22", None), "none",
     "古い貯水池の苔を食べて育つんだなぁ。背中に苔がついてるのもいるよ"),
    ("silver_stripe", "銀縞ウグイ", 1, 1, ["river", "pond"], 15, 30, 26, 0.25, "slim",
     ("#C8CCD0", "#6A7480", "#EEF0F2", "#9AA2AA", "#3A4450", None), "stripe",
     "横に一本、銀の縞。流れのある所が好きなんだなぁ"),
    ("lantern_medaka", "灯りメダカ", 1, 2, ["pond"], 3, 5, 60, 0.3, "tiny",
     ("#E8E0C0", "#A8A080", "#FFF6C0", "#D8D0A8", "#FFD860", "#FFE890"), "belly_glow",
     "腹がぽうっと光るんだ。夜に結晶灯のそばで、よく群れてるなぁ"),
    ("crystal_trout", "結晶ヤマメ", 1, 2, ["river", "pond"], 18, 32, 70, 0.4, "slim",
     ("#B8B49A", "#6E7A5E", "#E8E6D6", "#9AC8E0", "#5E6A8A", "#BFE8FF"), "parr",
     "ひれに小さな結晶がつく。水がきれいな所にしかいないんだなぁ"),
    ("stone_loach", "石くれドジョウ", 1, 2, ["pond"], 10, 18, 55, 0.35, "long",
     ("#8A7458", "#5A4A36", "#C8B89A", "#6E5E48", "#3A3024", None), "spots",
     "石のすき間にもぐって、石くれ兵の足もとでも平気な顔をしてるなぁ"),
    ("amber_carp", "琥珀ゴイ", 1, 3, ["pond", "lake"], 40, 80, 180, 0.55, "koi",
     ("#E0962E", "#A86418", "#F6D08A", "#C87A22", "#FFE0A0", None), "scales",
     "うろこが琥珀みたいに透けるんだ。大きいのは、わしより年上かもしれんなぁ"),
    ("waterway_eel", "水路ウナギ", 1, 3, ["pond", "lake"], 50, 90, 200, 0.6, "eel",
     ("#5E6A3A", "#343E1E", "#B8B88A", "#4A562C", "#2A3218", None), "none",
     "古い水路を通って、どこからともなく来るんだなぁ。力が強いぞ"),
    ("spear_pike", "湖ヤリウオ", 1, 3, ["lake"], 60, 110, 240, 0.65, "pike",
     ("#6E8A4E", "#3E5428", "#D0D8A8", "#5A7038", "#C8D890", None), "spots",
     "湖のヤリウオは、口が槍みたいにとがってる。糸を切られんようになぁ"),
    ("memory_dawn", "朝凪の記憶魚", 1, 4, ["pond", "lake"], 20, 35, 500, 0.7, "slim",
     ("#F2E4B0", "#C8A85A", "#FFF8E0", "#F8E8A0", "#FFFFFF", "#FFF2B8"), "memory",
     "こいつは、水路をめぐる記憶を食べて育った魚だなぁ。朝の光みたいに、あったかい", "fish_memory_dawn"),
    # ---------------------------------------------------------------- 第2章：氷下の魚
    ("frost_smelt", "霜ワカサギ", 2, 1, ["pond", "lake"], 8, 14, 20, 0.2, "slim",
     ("#C8DAE4", "#7E9AAA", "#EEF6FA", "#AAC4D2", "#5E7A8A", None), "stripe",
     "氷に穴をあけて釣るのが楽しいんだなぁ。寒いほど、よく釣れる"),
    ("ice_dace", "氷ハヤ", 2, 1, ["pond", "river"], 10, 18, 22, 0.2, "slim",
     ("#B8D4E8", "#6A8EAA", "#E8F4FC", "#9ABCD6", "#4A6E8A", None), "dots",
     "冷たい水でも元気に泳ぐハヤだ。うろこが氷の色をしてるなぁ"),
    ("snow_trout", "雪マス", 2, 2, ["pond", "lake"], 25, 45, 75, 0.4, "salmon",
     ("#E8ECEE", "#9AA4AA", "#FFFFFF", "#C8D0D4", "#7A848A", None), "spots",
     "真っ白な体に、灰色のぽつぽつ。雪の上に置くと見えなくなるんだなぁ"),
    ("glass_goby", "玻璃ハゼ", 2, 2, ["pond"], 5, 10, 65, 0.35, "goby",
     ("#D8EEF2", "#A8C8D0", "#F2FAFC", "#C0E0E8", "#8AA8B0", "#E8FFFF"), "bones",
     "体が透けて、骨まで見えるんだ。光にかざすと、ガラス細工みたいだなぁ"),
    ("frostsilver_char", "霜銀イワナ", 2, 2, ["lake", "river"], 25, 40, 85, 0.45, "slim",
     ("#B0B8C0", "#5E6874", "#E0E6EC", "#D8E0E8", "#FFFFFF", None), "spots",
     "霜銀の鉱脈のそばの水にいるイワナだ。白い点が霜みたいだなぁ"),
    ("icicle_cod", "氷柱ダラ", 2, 3, ["pond", "lake"], 40, 70, 190, 0.55, "cod",
     ("#B8C4C8", "#6E7E84", "#E6ECEE", "#D0F0FF", "#8EA4AA", "#D8F6FF"), "spots",
     "ひれが氷柱みたいに透けてとがってる。氷の下の大物だなぁ"),
    ("white_catfish", "白ナマズ", 2, 3, ["pond"], 40, 80, 210, 0.6, "catfish",
     ("#E8E4DC", "#B4ACA0", "#FAF8F4", "#CCC4B8", "#8A8276", None), "none",
     "氷の下の泥で、じっと春を待ってるんだ。ひげが長いほど年寄りだなぁ"),
    ("aurora_salmon", "極光サケ", 2, 3, ["lake"], 50, 90, 260, 0.65, "salmon",
     ("#6AB89A", "#3A6E8A", "#D8F0E4", "#8A6AC8", "#B88AE8", "#C8FFE8"), "gradient",
     "冬の夜空の光みたいに、緑から紫へ色が変わるサケだ。湖でしか見ないなぁ"),
    ("sleeping_sturgeon", "眠りチョウザメ", 2, 3, ["lake"], 100, 180, 320, 0.75, "sturgeon",
     ("#7E868A", "#4A5054", "#C0C6C8", "#646C70", "#D8DCDE", None), "plates",
     "湖の底で、何十年も眠ってるような顔をしてる。起こすと大暴れだなぁ"),
    ("memory_snow", "雪明かりの記憶魚", 2, 4, ["pond", "lake"], 20, 35, 500, 0.7, "slim",
     ("#E0ECFF", "#9AB0E0", "#FFFFFF", "#C8D8FF", "#FFFFFF", "#E8F2FF"), "memory",
     "氷の下で、氷の上の声を聞いてた魚だなぁ。すべる足音を覚えてるらしい", "fish_memory_snow"),
    # ---------------------------------------------------------------- 第3章：温かい湧き水の魚
    ("spring_guppy", "湧き水グッピー", 3, 1, ["pond"], 3, 6, 20, 0.2, "tiny",
     ("#E8C88A", "#B89A5A", "#FFF0D0", "#E86A9A", "#6AC8E8", None), "tail",
     "温かい湧き水が出てから増えた小魚だ。尾びれがにぎやかだなぁ"),
    ("ember_crucian", "火照りブナ", 3, 1, ["pond", "lake"], 15, 25, 24, 0.25, "round",
     ("#C8744A", "#8A4A2A", "#F0C8A0", "#A85A34", "#E8A070", None), "none",
     "温い水に入ると、体がほんのり赤くなるフナだなぁ"),
    ("steam_minnow", "湯気ハヤ", 3, 1, ["pond", "river"], 8, 14, 22, 0.2, "slim",
     ("#E8D4CC", "#A88A80", "#FFF4F0", "#D4B4AA", "#8A6A60", None), "dots",
     "湯気の立つ水面で、ぴょんぴょん跳ねるんだ。見てるだけで楽しいなぁ"),
    ("coral_koi", "珊瑚ゴイ", 3, 2, ["pond"], 40, 70, 90, 0.45, "koi",
     ("#F4F0EC", "#E8826A", "#FFFFFF", "#F0A898", "#E8604A", None), "patches",
     "白い体に珊瑚色のまだら。温かい水で、色がどんどん冴えるんだなぁ"),
    ("sun_perch", "陽だまりパーチ", 3, 2, ["pond", "lake"], 15, 30, 70, 0.4, "perch",
     ("#E8C84A", "#8A7A2A", "#FFF0A8", "#E89A3A", "#5A5A2A", None), "bars",
     "日なたの浅瀬が好きなんだ。縦の縞が、木もれ日みたいだなぁ"),
    ("lamp_shrimp", "灯りテナガエビ", 3, 2, ["pond"], 5, 12, 60, 0.35, "shrimp",
     ("#E8D8C8", "#C8A88A", "#FFF0E0", "#E8C8A8", "#FFB070", "#FFE0A0"), "glow_dots",
     "魚じゃないけど、図鑑には入れておくよ。手が長くて、体に灯りの点があるんだなぁ"),
    ("golden_carp", "金鱗ゴイ", 3, 3, ["pond", "lake"], 50, 90, 230, 0.6, "koi",
     ("#E8C040", "#A8821A", "#FFF0A0", "#D8A82A", "#FFF8D0", "#FFF2A0"), "scales",
     "金のうろこが一枚一枚光るんだ。縁起がいいって、みんな拝みにくるなぁ"),
    ("rainbow_gourami", "虹鱗グラミー", 3, 3, ["pond"], 10, 20, 200, 0.55, "gourami",
     ("#6AA8E8", "#3A6AA8", "#D8ECFF", "#E86AA8", "#E8E06A", None), "rainbow",
     "見る向きで、うろこの色が虹みたいに変わる。湧き水の口のそばにいるなぁ"),
    ("ember_ray", "熾火エイ", 3, 3, ["lake"], 60, 120, 280, 0.7, "ray",
     ("#4A3A34", "#2A201C", "#8A7A70", "#3A2C26", "#FF8A3A", "#FFB070"), "ember",
     "背中に熾火みたいな点が光るエイだ。湖の温かい所の底にいるなぁ"),
    ("memory_steam", "湯けむりの記憶魚", 3, 4, ["pond", "lake"], 20, 35, 500, 0.7, "slim",
     ("#F8E0E0", "#D8A8A8", "#FFF8F8", "#F0C8C8", "#FFFFFF", "#FFE8E8"), "memory",
     "湯けむりの中の話し声を覚えてる魚だなぁ。帰ってきた人の声らしい", "fish_memory_steam"),
    # ---------------------------------------------------------------- 第4章：空魚
    ("cloud_medaka", "雲メダカ", 4, 1, ["pond", "lake"], 3, 6, 24, 0.2, "tiny",
     ("#F4F8FF", "#B8C8E0", "#FFFFFF", "#E0EAFA", "#A8B8D0", None), "fluffy",
     "ひれがふわふわで、水に映った雲の中を泳いでるみたいだなぁ"),
    ("sky_dace", "空ハヤ", 4, 1, ["pond", "river"], 10, 18, 26, 0.25, "slim",
     ("#8AC0F0", "#4A80C0", "#E0F0FF", "#6AA0D8", "#2A5A9A", None), "stripe",
     "空の色をそのまま写したようなハヤだ。晴れた日ほど青いなぁ"),
    ("wind_trout", "風マス", 4, 2, ["lake", "river"], 25, 40, 90, 0.45, "salmon",
     ("#C8DCF0", "#6A8AB8", "#F0F6FF", "#A8C4E0", "#FFFFFF", None), "streaks",
     "体に白い筋が流れてる。風が吹くと、水面すれすれを走るんだなぁ"),
    ("star_goby", "星ハゼ", 4, 2, ["pond"], 6, 12, 80, 0.4, "goby",
     ("#2A3A6A", "#1A2448", "#4A5A8A", "#3A4A7A", "#FFFFFF", "#E8F0FF"), "stars",
     "紺色の体に白い点。夜の水面の星を食べてるって、子どもたちは言うなぁ"),
    ("link_jelly", "結びクラゲ", 4, 2, ["pond", "lake"], 20, 40, 85, 0.35, "jelly",
     ("#D8E8FF", "#A8C0E8", "#F0F6FF", "#C0D4F4", "#8AE0FF", "#D8F4FF"), "threads",
     "細い糸みたいな足が、ほかのクラゲと結ばってることがある。ふしぎだなぁ"),
    ("moon_arowana", "月光アロワナ", 4, 3, ["lake"], 60, 100, 300, 0.65, "arowana",
     ("#D8DCE4", "#8A92A4", "#F4F6FA", "#B8BECC", "#FFFFFF", "#F0F4FF"), "scales",
     "月の夜にだけ、水面のすぐ下まで上がってくる。うろこが月みたいだなぁ"),
    ("thunder_catfish", "雷ナマズ", 4, 3, ["pond", "lake"], 50, 90, 280, 0.7, "catfish",
     ("#3A3E48", "#22252C", "#6A6E78", "#2E3238", "#FFE04A", "#FFF080"), "zigzag",
     "体に稲妻の模様。かかると、竿までびりっと来るんだなぁ"),
    ("dawn_koi", "暁ゴイ", 4, 3, ["lake"], 50, 80, 300, 0.65, "koi",
     ("#E8986A", "#A8586A", "#FFE0C8", "#C87A8A", "#8A6AC8", None), "gradient",
     "夜明けの空みたいに、頭から尾へ色が変わるコイだ。湖でしか見ないなぁ"),
    ("memory_star", "星映しの記憶魚", 4, 4, ["pond", "lake"], 20, 35, 500, 0.7, "slim",
     ("#C8D0F8", "#6A78C8", "#F0F2FF", "#A8B4F0", "#FFFFFF", "#E0E6FF"), "memory",
     "水に映った灯りの輪を覚えてる魚だなぁ。手から手へ、火を渡してたらしい", "fish_memory_star"),
    # ---------------------------------------------------------------- 湖の主
    ("memory_lord", "記憶の主", 1, 5, ["lake"], 180, 300, 5000, 0.95, "lord",
     ("#5A6A7A", "#2E3A48", "#A8B4C0", "#4A5868", "#9AE8FF", "#C8F4FF"), "lord",
     "……わしが五十年、待ち続けた湖の主だなぁ。この湖のぜんぶの記憶を、こいつは知ってるのかもしれん"),
]

RARITY_POINTS = {1: 1, 2: 2, 3: 5, 4: 10, 5: 50}

RULES = {
    "_comment": "釣りの決まり（Phase 7 その2。Phase 0-A 15章「娯楽の設計（釣り）」・13章「ほかの人の近くで釣る」「魚図鑑に新しく登録」）。"
                "数は【補完】。釣り場の範囲は中央広場の真ん中からの位置（docs/map_style.md の釣り堀と湖）",
    "rarity_weights": {"1": 60, "2": 30, "3": 10},
    "current_chapter_weight": 1.5,
    "memory_chance": 2.0,
    "lord_chance": 0.2,
    "size_skew": 1.6,
    "big_ratio": 0.9,
    "big_price_factor": 1.3,
    "points_per_thread": 10,
    "rod_rent_knots": 0,
    "near_radius": 16,
    "near_seconds": 30,
    "spots": {
        "pond": {"min": [-96, -10, 22], "max": [-59, 6, 68]},
        "lake": {"min": [-132, -12, 54], "max": [-95, 6, 92]}
    },
    "lake_flag": "amemaru:project/lakeside",
    # 手で遊ぶ見立て（反応の遅れ 0.25秒前後・見まちがい）で、★1〜3と記憶魚は9割以上釣れ、湖の主は4割ほど。
    # 1匹にかかる時間は 2.5〜8秒（湖の主は14秒ほど）。python3 tools/datagen/build_fishing.py --sim で確かめられる【補完】
    "minigame": {
        "zone_center": 0.5,
        "zone_half": 0.19,
        "zone_shrink": 0.35,
        "reel_rate": 0.024,
        "slack_rate": 0.022,
        "pull": 0.016,
        "burst": 0.065,
        "decay": 0.25,
        "base_ticks": 50,
        "extra_ticks": 50,
        "escape_ticks": 40,
        "limit_seconds": 20
    },
    "tournament": {
        "minutes": 10,
        "countdown_seconds": 30,
        "rarity_multiplier": {"1": 1, "2": 2, "3": 4, "4": 6, "5": 20},
        "prizes": [
            {"knots": 3000, "threads": 3},
            {"knots": 2000, "threads": 2},
            {"knots": 1000, "threads": 1}
        ]
    }
}


def fish_json(f):
    fid, name, chapter, stars, spots, lo, hi, price, diff, shape, colors, pattern, comment = f[:13]
    memory = f[13] if len(f) > 13 else None
    out = {
        "name": name,
        "chapter": chapter,
        "rarity": stars,
        "spots": spots,
        "size": [lo, hi],
        "price": price,
        "points": RARITY_POINTS[stars],
        "difficulty": diff,
        "comment": comment,
        "order": FISH.index(f),
    }
    if memory:
        out["memory"] = "amemaru:" + memory
    if stars == 5:
        out["legendary"] = True
    return out


def write_all():
    folder = DATA / "fishing"
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob("*.json"):
        old.unlink()
    for f in FISH:
        dump(folder / (f[0] + ".json"), fish_json(f))
    (DATA / "fishing_rules").mkdir(parents=True, exist_ok=True)
    dump(DATA / "fishing_rules" / "default.json", RULES)
    # アイテムのモデル（絵は tools/artgen/make_fish.py）
    models = ASSETS / "models" / "item"
    for f in FISH:
        dump(models / ("fish_" + f[0] + ".json"), {"parent": "minecraft:item/generated",
                                                    "textures": {"layer0": "amemaru:item/fish/" + f[0]}})
    # 同梱の種類の表
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    manifest["fish"] = [{"id": "fish_" + f[0], "rarity": f[3]} for f in FISH]
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # 言語ファイル（日本語と同じものを en_us にも）
    for lang in ("ja_jp", "en_us"):
        path = ASSETS / "lang" / (lang + ".json")
        text = path.read_text(encoding="utf-8")
        data = json.loads(text)
        for f in FISH:
            data["item.amemaru.fish_" + f[0]] = f[1]
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def dump(path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ================================================================ 確かめる
def check():
    problems = []
    ids = [f[0] for f in FISH]
    if len(FISH) != 40:
        problems.append(f"魚が40種ではありません（{len(FISH)}）")
    if len(set(ids)) != len(ids):
        problems.append("魚の名前が重なっています")
    for chapter in (1, 2, 3, 4):
        n = sum(1 for f in FISH if f[2] == chapter and f[3] < 5)
        mem = sum(1 for f in FISH if f[2] == chapter and f[3] == 4)
        if mem != 1:
            problems.append(f"第{chapter}章の記憶魚が1種ではありません（{mem}）")
        if n < 9:
            problems.append(f"第{chapter}章の魚が少なすぎます（{n}）")
    for f in FISH:
        fid, name, chapter, stars, spots, lo, hi, price, diff = f[:9]
        if not 1 <= len(name) <= 12:
            problems.append(f"{fid}：名前は1〜12文字（{name}）")
        if not (0 < lo < hi):
            problems.append(f"{fid}：大きさの範囲がおかしい")
        if not set(spots) <= {"pond", "lake", "river"} or not spots:
            problems.append(f"{fid}：釣り場がおかしい {spots}")
        if not 0 <= diff <= 1:
            problems.append(f"{fid}：手ごたえは0〜1")
        if stars == 4 and len(f) < 14:
            problems.append(f"{fid}：記憶魚に記録がありません")
    data_folder = DATA / "fishing"
    if data_folder.exists():
        for f in FISH:
            p = data_folder / (f[0] + ".json")
            if not p.exists():
                problems.append(f"{p} がありません")
            elif json.loads(p.read_text(encoding="utf-8")) != fish_json(f):
                problems.append(f"{p} が表と合っていません（書き出し直してください）")
        tex = ASSETS / "textures" / "item" / "fish"
        for f in FISH:
            if not (tex / (f[0] + ".png")).exists():
                problems.append(f"絵がありません：textures/item/fish/{f[0]}.png（tools/artgen/make_fish.py）")
    records = DATA / "records"
    for f in FISH:
        if len(f) > 13 and not (records / (f[13] + ".json")).exists():
            problems.append(f"記憶の記録がありません：records/{f[13]}.json（tools/datagen/build_guild.py）")
    return problems


# ================================================================ ミニゲームの見立て
def sim(trials=400, delay=5, noise=0.04):
    """糸の張りのミニゲーム（client/ClientFishing.java と同じ動き）を、人の手の見立て
    （反応が delay ティック前後おくれる・張りを noise ほど見まちがえる）で何度も遊んで、
    手ごたえごとに「釣れる割合」と「かかる秒」を出す。数を変えたときに確かめる。"""
    import math
    import random
    mg = RULES["minigame"]
    rows = []
    for d in sorted({f[8] for f in FISH}):
        ok_ticks = []
        for seed in range(trials):
            rnd = random.Random(seed)
            hand = random.Random(seed * 7 + 1)
            center = mg["zone_center"]
            half = max(0.03, mg["zone_half"] * (1 - mg["zone_shrink"] * d))
            pull = mg["pull"] * (0.5 + d)
            burst = mg["burst"] * (0.3 + d)
            ticks = mg["base_ticks"] + round(mg["extra_ticks"] * d)
            limit = mg["limit_seconds"] * 20
            tension, progress, age, low, kick, next_kick = center, 0.25, 0, 0, 0.0, 20
            seen = [tension] * 12
            while True:
                lag = max(2, delay + hand.choice((-2, -1, 0, 1, 2)))
                hold = seen[-1 - lag] + hand.gauss(0, noise) < center - half * 0.1
                tension += mg["reel_rate"] if hold else -mg["slack_rate"]
                tension += math.sin(age * (0.16 + d * 0.14)) * pull
                if age >= next_kick:
                    kick = (1 if rnd.random() < 0.5 else -1) * burst * (0.6 + rnd.random() * 0.8)
                    next_kick = age + 14 + rnd.randrange(max(4, 30 - round(d * 16)))
                tension += kick
                kick *= 0.78
                inside = abs(tension - center) <= half
                progress = min(1, max(0, progress + (1 / ticks if inside else -mg["decay"] / ticks)))
                age += 1
                seen.append(tension)
                if tension >= 1 or age >= limit:
                    break
                if progress >= 1:
                    ok_ticks.append(age)
                    break
                if tension <= 0:
                    tension = 0
                    low += 1
                    if low >= mg["escape_ticks"]:
                        break
                else:
                    low = 0
        names = [f[1] for f in FISH if f[8] == d]
        rate = len(ok_ticks) / trials
        secs = sum(ok_ticks) / len(ok_ticks) / 20 if ok_ticks else 0
        rows.append(f"手ごたえ {d:.2f}：釣れる {rate:.0%}・{secs:.1f}秒（{'・'.join(names[:3])}{'ほか' if len(names) > 3 else ''}）")
    return rows


if __name__ == "__main__":
    if "--sim" in sys.argv:
        for row in sim():
            print(row)
        sys.exit(0)
    if "--check" not in sys.argv:
        write_all()
    found = check()
    for p in found:
        print("問題：" + p)
    print(f"魚 {len(FISH)}（第1章 {sum(1 for f in FISH if f[2] == 1)}・記憶魚 {sum(1 for f in FISH if f[3] == 4)}・湖の主 1）")
    sys.exit(1 if found else 0)
