"""カジノの地下遊技場のパチンコとスロット（11月27日のご指示「パチンコとスロットを追加する。本物と同じように演出や日にちごとに代わる
あたり台と外れ台を用意する。本物ように設定を作る。専用のＧＵＩから遊べる。少し多めに用意する」【補完】）の機種のデータを書き出す。

  python3 tools/datagen/build_parlor.py

書き出すもの
  data/amemaru/amemaru/casino_machines/<機種>.json   機種（パチンコ3・スロット3）
  data/amemaru/amemaru/casino/default.json の "parlor"  遊技場の決まりと台の並び（台番号・機種・置き場所。tools/mapgen/parlor_layout.py）

機種（名前はすべてこのModのもの）
  パチンコ  輝きの塔（ミドル 1/319.7・確変ループ）・結び目の森 甘（甘デジ 1/99.9）・沈黙の湖 LT（ハイスペック・設定付き・ST とラッキートリガー）
  スロット  ノットランプ（Aタイプ・告知ランプ）・星詠みの塔（AT・押し順ナビ・CZ・上乗せ・天井）・月影の大鐘（AT・荒い）
  沈黙の湖 LT と月影の大鐘は、共同事業⑥「カジノの新台入替」のあと（2階の新台の間）
数（確率・振り分け・ボーダー・機械割）は tools/balance/ParlorSim.java で確かめた値。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
DATA = os.path.join(REPO, "src", "main", "resources", "data", "amemaru", "amemaru")
sys.path.insert(0, os.path.join(REPO, "tools", "mapgen"))

COMMENT = ("カジノの地下遊技場の機種（11月27日のご指示「パチンコとスロットを追加する。本物ように設定を作る」【補完】）。"
           "tools/datagen/build_parlor.py が書き出す。数は tools/balance/ParlorSim.java で確かめた値")

# ---------------------------------------------------------------- スロット
# 図柄：7 赤7・B BAR・G ぶどう・R リプレイ・C チェリー・L ベル・K 結び目（Aタイプ）・S スイカ・X ブランク（AT）
SYMBOLS_A = [
    {"id": "7", "name": "赤7", "color": "#E8283C"},
    {"id": "B", "name": "BAR", "color": "#202028"},
    {"id": "G", "name": "ぶどう", "color": "#8A4CC8"},
    {"id": "R", "name": "リプレイ", "color": "#3C8CE0"},
    {"id": "C", "name": "チェリー", "color": "#E04060"},
    {"id": "L", "name": "ベル", "color": "#F0C830"},
    {"id": "K", "name": "結び目", "color": "#D07830"},
]
SYMBOLS_AT = [
    {"id": "7", "name": "赤7", "color": "#E8283C"},
    {"id": "B", "name": "BAR", "color": "#202028"},
    {"id": "L", "name": "ベル", "color": "#F0C830"},
    {"id": "R", "name": "リプレイ", "color": "#3C8CE0"},
    {"id": "C", "name": "チェリー", "color": "#E04060"},
    {"id": "S", "name": "スイカ", "color": "#40B050"},
    {"id": "X", "name": "星", "color": "#F4F0E0"},
]
# 有効ラインは中段の1本（中段1ライン。チェリーだけは左のリールの上段・中段・下段のどこでも）
LINE1 = [[1, 1, 1]]

# リールの並び（上から下へ21コマ）
REELS_A = [
    "G7CGRGLBRKGBRGGCLRG7R",
    "R7GLRGCBRGKGR7GLRKGBG",
    "7GKRLGBRCG7RGLGRBGKRG",
]
REELS_AT = [
    "RLBRLLCRRLRRSSLLRXC7L",
    "7LRSLRBLRSLRXLRSLRCLR",
    "7RLSRLBRLSRLXRLSRLBRL",
]

S_KNOT = {
    "kind": "slot", "type": "a", "name": "ノットランプ", "spec": "Aタイプ（告知ランプ）",
    "bet": 3, "wait_ms": 3200, "rpm": 60,
    "symbols": SYMBOLS_A, "reels": REELS_A, "lines": LINE1,
    "roles": [
        {"id": "big", "type": "bonus", "pattern": "777", "bonus": "big", "p": [273.1, 270.8, 268.6, 259.0, 252.1, 245.5]},
        {"id": "reg", "type": "bonus", "pattern": "77B", "bonus": "reg", "p": [431.2, 399.6, 358.1, 331.0, 301.2, 268.6]},
        {"id": "grape", "pattern": "GGG", "pay": 8, "p": [6.49, 6.43, 6.36, 6.28, 6.18, 6.02]},
        {"id": "replay", "pattern": "RRR", "replay": True, "p": 7.30},
        {"id": "cherry", "type": "cherry", "pattern": "C", "rows": [0, 1, 2], "pay": 2, "p": [35.6, 35.4, 34.6, 35.4, 33.6, 33.2]},
        {"id": "bell", "pattern": "LLL", "pay": 14, "p": 1092.3},
        {"id": "knot", "pattern": "KKK", "pay": 10, "p": 1092.3},
    ],
    "bonuses": {
        "big": {"cap": 312, "bet": 2, "pay": 14, "role": "grape"},
        "reg": {"cap": 104, "bet": 2, "pay": 14, "role": "grape"},
    },
    "lamp": {"name": "ノットランプ", "pre": 0.25, "premium": 0.05},
    "payout": [97.0, 98.0, 99.9, 102.8, 105.3, 109.0],
    "theme": {"frame": "#5A2A82", "accent": "#F2C84A", "screen": "#1A0E26", "glow": "#FF5AA8"},
    "labels": {"big": "BIG BONUS", "reg": "REGULAR BONUS", "stage": "ラッキーノット"},
}

AT_ROLES = [
    {"id": "replay", "pattern": "RRR", "replay": True, "p": 7.30},
    {"id": "obell", "type": "order", "pattern": "LLL", "pay": 9, "miss_pay": 0, "p": 1.80},
    {"id": "cbell", "pattern": "LLL", "pay": 9, "p": 40.0},
    {"id": "wcherry", "type": "cherry", "pattern": "C", "rows": [0, 2], "pay": 2, "rare": True, "p": [100, 98, 96, 94, 92, 90]},
    {"id": "scherry", "type": "cherry", "pattern": "C", "rows": [1], "pay": 2, "rare": True, "p": 400},
    {"id": "melon", "pattern": "SSS", "pay": 5, "rare": True, "p": [120, 118, 116, 114, 112, 110]},
    {"id": "chance", "pattern": "RRB", "pay": 0, "rare": True, "p": 250},
]

S_STAR = {
    "kind": "slot", "type": "at", "name": "星詠みの塔", "spec": "AT（押し順ナビ・CZ・上乗せ・天井）",
    "bet": 3, "wait_ms": 3200, "rpm": 60,
    "symbols": SYMBOLS_AT, "reels": REELS_AT, "lines": LINE1,
    "roles": AT_ROLES,
    "at": {
        "ceiling": 800, "initial": 50, "cz_games": 10,
        "cz": {"wcherry": [10, 11, 11.5, 13, 15, 16], "melon": [12, 13, 14, 16, 18, 20], "chance": [30, 32, 33, 36, 40, 42],
               "scherry": [70, 70, 71, 73, 76, 78], "other": [0.08, 0.09, 0.093, 0.108, 0.13, 0.14]},
        "direct": {"scherry": [25, 26, 26, 27, 29, 30], "chance": [6, 6, 6.5, 7, 8, 9], "other": [0.01, 0.01, 0.011, 0.013, 0.018, 0.02]},
        "cz_win": {"replay": 3, "obell": 3, "cbell": 12, "wcherry": 45, "melon": 55, "scherry": 100, "chance": 100, "other": 1},
        "add": {"wcherry": {"pct": 50, "games": 20}, "melon": {"pct": 60, "games": 20}, "scherry": {"pct": 100, "games": 50},
                "chance": {"pct": 100, "games": 30}, "other": {"pct": 1.9, "games": 15}},
    },
    "payout": [97.5, 98.8, 100.5, 103.5, 107.0, 111.5],
    "theme": {"frame": "#1C2A5C", "accent": "#F4D27A", "screen": "#0A1028", "glow": "#7AB8FF"},
    "labels": {"stage": "星の回廊", "cz": "星の扉", "at": "星詠みラッシュ"},
}

S_MOON = {
    "kind": "slot", "type": "at", "name": "月影の大鐘", "spec": "AT（荒い・天井600）",
    "bet": 3, "wait_ms": 3200, "rpm": 60,
    "symbols": SYMBOLS_AT, "reels": REELS_AT, "lines": LINE1,
    "roles": AT_ROLES,
    "at": {
        "ceiling": 600, "initial": 30, "cz_games": 8,
        "cz": {"wcherry": [8, 9, 10, 11, 13, 15], "melon": [10, 11, 12, 14, 16, 18], "chance": [25, 27, 30, 33, 36, 40],
               "scherry": [60, 60, 62, 65, 68, 70], "other": [0.06, 0.07, 0.08, 0.09, 0.11, 0.13]},
        "direct": {"scherry": [30, 31, 32, 34, 36, 40], "chance": [8, 8, 9, 10, 11, 12], "other": [0.01, 0.01, 0.012, 0.015, 0.02, 0.025]},
        "cz_win": {"replay": 2, "obell": 2, "cbell": 10, "wcherry": 40, "melon": 50, "scherry": 100, "chance": 100, "other": 1},
        "add": {"wcherry": {"pct": 50, "games": 20}, "melon": {"pct": 60, "games": 30}, "scherry": {"pct": 100, "games": 100},
                "chance": {"pct": 100, "games": 50}, "other": {"pct": 0.67, "games": 20}},
    },
    "payout": [97.0, 98.5, 100.5, 104.0, 108.0, 113.0],
    "theme": {"frame": "#3A3E4A", "accent": "#E8ECF4", "screen": "#0C0E16", "glow": "#C8D8FF"},
    "labels": {"stage": "月影の塔", "cz": "月夜の鐘", "at": "大鐘ラッシュ"},
}

# ---------------------------------------------------------------- パチンコ
P_TOWER = {
    "kind": "pachinko", "name": "P 輝きの塔", "spec": "ミドル（確変ループ）", "border": 18.2,
    "normal": 319.7, "high": 39.9, "count": 10, "ball_pay": 15, "heso_pay": 3, "denchu_pay": 1, "denchu": 0.95,
    "nails": {"normal": [15.0, 20.0], "event": [16.5, 22.5]},
    "jackpots": [
        {"id": "k10", "name": "10R確変", "rounds": 10, "next": "kakuhen", "heso": 10, "denchu": 55},
        {"id": "k4", "name": "4R確変", "rounds": 4, "next": "kakuhen", "heso": 52, "denchu": 10},
        {"id": "n10", "name": "10R通常", "rounds": 10, "next": "jitan", "spins": 100, "heso": 0, "denchu": 35},
        {"id": "n4", "name": "4R通常", "rounds": 4, "next": "jitan", "spins": 100, "heso": 38, "denchu": 0},
    ],
    "theme": {"frame": "#1E3C78", "accent": "#F2C84A", "screen": "#08142E", "glow": "#7FD8FF"},
    "labels": {"normal": "リーチ", "super": "塔の守り手リーチ", "special": "輝きの大結晶リーチ", "all": "全回転",
               "kakuhen": "輝きラッシュ", "jitan": "チャンスタイム", "stage": "塔のふもと"},
}

P_FOREST = {
    "kind": "pachinko", "name": "P 結び目の森 甘", "spec": "甘デジ（確変ループ）", "border": 17.2,
    "normal": 99.9, "high": 25.1, "count": 10, "ball_pay": 10, "heso_pay": 3, "denchu_pay": 1, "denchu": 0.95,
    "nails": {"normal": [15.0, 20.0], "event": [16.5, 22.5]},
    "jackpots": [
        {"id": "k10", "name": "10R確変", "rounds": 10, "next": "kakuhen", "heso": 0, "denchu": 15},
        {"id": "k4", "name": "4R確変", "rounds": 4, "next": "kakuhen", "heso": 40, "denchu": 35},
        {"id": "n10", "name": "10R通常", "rounds": 10, "next": "jitan", "spins": 50, "heso": 0, "denchu": 10},
        {"id": "n4", "name": "4R通常", "rounds": 4, "next": "jitan", "spins": 50, "heso": 60, "denchu": 40},
    ],
    "theme": {"frame": "#2C5A2E", "accent": "#F0E090", "screen": "#0C1E10", "glow": "#9CF08A"},
    "labels": {"normal": "リーチ", "super": "森の小道リーチ", "special": "結び目の大樹リーチ", "all": "全回転",
               "kakuhen": "森の確変", "jitan": "木もれ日タイム", "stage": "結び目の森"},
}

P_LAKE = {
    "kind": "pachinko", "name": "P 沈黙の湖 LT", "spec": "ハイスペック（ST・ラッキートリガー・設定付き）", "border": 18.2,
    "normal": [349.9, 339.6, 329.8, 319.9, 309.7, 299.6], "settings": True, "high": 99.6,
    "count": 10, "ball_pay": 15, "heso_pay": 3, "denchu_pay": 1, "denchu": 0.95,
    "nails": {"normal": [15.5, 20.5], "event": [17.0, 23.0]},
    "jackpots": [
        {"id": "r3", "name": "3R＋ラッシュ", "rounds": 3, "next": "st", "spins": 160, "heso": 50, "denchu": 0, "denchu_lt": 0},
        {"id": "n3", "name": "3R通常", "rounds": 3, "next": "jitan", "spins": 100, "heso": 50, "denchu": 0, "denchu_lt": 0},
        {"id": "s10", "name": "10R＋ラッシュ", "rounds": 10, "next": "st", "spins": 160, "heso": 0, "denchu": 55, "denchu_lt": 0},
        {"id": "s3", "name": "3R＋ラッシュ", "rounds": 3, "next": "st", "spins": 160, "heso": 0, "denchu": 35, "denchu_lt": 0},
        {"id": "lt10", "name": "10R＋ラッキートリガー", "rounds": 10, "next": "st", "spins": 230, "lt": True,
         "heso": 0, "denchu": 10, "denchu_lt": 100},
    ],
    "theme": {"frame": "#2A4A66", "accent": "#E4F2FF", "screen": "#06121E", "glow": "#9CD4FF"},
    "labels": {"normal": "リーチ", "super": "湖の底リーチ", "special": "声なき湖主リーチ", "all": "全回転",
               "st": "湖面ラッシュ", "lt": "ラッキートリガー 静寂の極み", "jitan": "チャンスタイム", "stage": "沈黙の湖"},
}

MACHINES = {
    "p_tower": P_TOWER, "p_forest": P_FOREST, "p_lake": P_LAKE,
    "s_knot": S_KNOT, "s_star": S_STAR, "s_moon": S_MOON,
}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(obj, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def parlor_json():
    """casino/default.json の "parlor"：遊技場の決まりと、台の並び（tools/mapgen/parlor_layout.py）"""
    import parlor_layout as lay
    islands = []
    for (iid, name, model, cols, vr, foot, floor, start) in lay.ISLANDS:
        islands.append({"id": iid, "name": name, "model": model, "floor": floor,
                        "boards": [[lay.BX + u, y, lay.BZ + v] for (i2, u, v, y) in lay.boards() if i2 == iid]})
    machines = []
    for m in lay.machines():
        x, z = lay.world(m["u"], m["v"])
        machines.append({"no": m["no"], "model": m["model"], "island": m["island"], "pos": [x, m["y"], z],
                         "facing": m["facing"]})
    (u0, v0), (u1, v1) = lay.COUNTER
    return {
        "_comment": "地下遊技場（11月27日【補完】）。玉とメダルはチップで借りる（1枚で25個・5枚）。台を離れても keep 秒は"
                    "その人の台（ボーナス・確変・AT の最中は keep_feature 秒）。new の島は共同事業⑥「カジノの新台入替」のあと。"
                    "台の並びは tools/mapgen/parlor_layout.py（tools/datagen/build_parlor.py が書く）",
        "balls_per_chip": 25,
        "medals_per_chip": 5,
        "lend": [1, 10, 40],
        "keep_seconds": 120,
        "keep_feature_seconds": 600,
        "reach": 6,
        "new_flag": "amemaru:project/new_machines",
        "counter": {"min": [lay.BX + u0, lay.B_FOOT, lay.BZ + v0], "max": [lay.BX + u1, lay.B_FOOT + 1, lay.BZ + v1]},
        "islands": islands,
        "machines": machines,
    }


def write_parlor():
    path = os.path.join(DATA, "casino", "default.json")
    with open(path, encoding="utf-8") as fp:
        rules = json.load(fp)
    rules["parlor"] = parlor_json()
    dump(path, rules)
    print("wrote casino/default.json parlor（台 %d）" % len(rules["parlor"]["machines"]))


def write_machines():
    for mid, spec in MACHINES.items():
        out = {"_comment": COMMENT}
        out.update(spec)
        dump(os.path.join(DATA, "casino_machines", mid + ".json"), out)
        print("wrote casino_machines/%s.json" % mid)


if __name__ == "__main__":
    write_machines()
    write_parlor()
