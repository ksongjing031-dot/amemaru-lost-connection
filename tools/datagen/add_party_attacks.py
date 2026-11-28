"""ボスの「参加者全員を狙う技」（一斉の印 volley・大技 nova）を、1〜8階のボスの定義に書き足す（11月28日【補完】）。

11月28日のご指示「現在ボスが一人をターゲットしてその人にしか攻撃をしないので、ボス参加している人全員をターゲットにする攻撃を
追加して他のプレイヤーが一方的に攻撃できる状態を減らす（すごくでかい範囲攻撃やプレイヤーの立っている場所に攻撃を出すなど）」。
技の動きは enemy/boss/PartyAttacks.java（どのボスにも共通）。ここでは、ボスごとの強さ・間・大技の形だけを決める。

11月27日のボスの作り直しと同じく、ボスの定義（enemies/<ボス>.json）を直に書きかえる（何度動かしても同じになる）。
階の data の作り直し（build_floorN_data.py）でボスの定義を書き直したときは、11月27日の数値とあわせて、これをもう一度動かす。

使い方: python3 tools/datagen/add_party_attacks.py
"""
import json
import os
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ENEMIES = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "amemaru", "amemaru", "enemies"))

OUT, IN, SAFE = 1, 2, 4

# ボス: (一斉の印, 大技)。強さ（power）は、固有のギミックをしくじったときの一撃よりは弱く（11月27日の決まり）
PARTY = {
    "veldhorn": (
        {"power": 0.9, "windup": 1.6, "cooldown": 24.0, "range": 2.4, "waves": 2, "gap": 0.9, "first": 16.0, "lift": 0.35},
        {"power": 1.6, "windup": 2.8, "cooldown": 42.0, "range": 13.0, "kinds": OUT, "first": 34.0, "lift": 0.6}),
    "old_ward": (
        {"power": 1.0, "windup": 1.6, "cooldown": 24.0, "range": 2.5, "waves": 2, "gap": 0.9, "first": 15.0, "lift": 0.35},
        {"power": 1.8, "windup": 3.0, "cooldown": 44.0, "range": 14.0, "kinds": IN, "safe_radius": 7.0, "first": 32.0, "lift": 0.6}),
    "hydram": (
        {"power": 1.0, "windup": 1.6, "cooldown": 24.0, "range": 2.6, "waves": 2, "gap": 0.9, "first": 15.0, "lift": 0.4},
        {"power": 1.8, "windup": 3.0, "cooldown": 44.0, "range": 14.0, "kinds": OUT, "first": 32.0, "lift": 0.6}),
    "cantare": (
        {"power": 1.1, "windup": 1.5, "cooldown": 22.0, "range": 2.6, "waves": 3, "gap": 0.9, "first": 14.0, "lift": 0.35},
        {"power": 1.9, "windup": 3.2, "cooldown": 46.0, "range": 14.0, "kinds": IN, "safe_radius": 7.0, "first": 30.0, "lift": 0.6}),
    "ancient_guardian": (
        {"power": 1.1, "windup": 1.5, "cooldown": 22.0, "range": 2.8, "waves": 3, "gap": 0.9, "first": 14.0, "lift": 0.35},
        {"power": 2.0, "windup": 3.4, "cooldown": 48.0, "range": 15.0, "kinds": SAFE, "safe_count": 3, "safe_radius": 4.5,
         "first": 30.0, "lift": 0.6}),
    "ordo": (
        {"power": 1.2, "windup": 1.5, "cooldown": 22.0, "range": 2.8, "waves": 3, "gap": 0.9, "first": 14.0, "lift": 0.35},
        {"power": 2.0, "windup": 3.0, "cooldown": 44.0, "range": 15.0, "kinds": OUT | IN, "safe_radius": 7.0, "first": 30.0,
         "lift": 0.6}),
    "reflecta": (
        {"power": 1.2, "windup": 1.5, "cooldown": 22.0, "range": 2.8, "waves": 3, "gap": 0.9, "first": 14.0, "lift": 0.35},
        {"power": 2.1, "windup": 3.2, "cooldown": 46.0, "range": 15.0, "kinds": IN | SAFE, "safe_count": 3, "safe_radius": 4.5,
         "first": 30.0, "lift": 0.6}),
    "mune": (
        {"power": 1.2, "windup": 1.5, "cooldown": 22.0, "range": 2.8, "waves": 3, "gap": 0.9, "first": 14.0, "lift": 0.35},
        {"power": 2.1, "windup": 3.4, "cooldown": 48.0, "range": 15.0, "kinds": SAFE, "safe_count": 3, "safe_radius": 5.0,
         "first": 30.0, "lift": 0.6}),
}

NOTE = ("11月28日のご指示「ボスが一人をターゲットしてその人にしか攻撃をしないので、ボス参加している人全員をターゲットにする攻撃を"
        "追加して他のプレイヤーが一方的に攻撃できる状態を減らす」【補完】：volley（一斉の印：参加者全員の足もとに円。waves 回、gap 秒ずつ"
        "遅れて追いかける）と nova（大技：kinds 1=ボスのまわり range・2=ボスのそばの金の円 safe_radius の外ぜんぶ・4=部屋の金の円"
        " safe_count 個の外ぜんぶ）を足した（enemy/boss/PartyAttacks.java。tools/datagen/add_party_attacks.py）。")


def main():
    for boss, (volley, nova) in PARTY.items():
        path = os.path.join(ENEMIES, boss + ".json")
        with open(path, encoding="utf-8") as fp:
            data = json.load(fp, object_pairs_hook=OrderedDict)
        skills = data.setdefault("skills", OrderedDict())
        skills["volley"] = OrderedDict(volley)
        skills["nova"] = OrderedDict(nova)
        comment = data.get("_comment", "")
        if NOTE not in comment:
            data["_comment"] = (comment + " " + NOTE).strip()
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(data, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
        print(boss, "volley", volley["power"], "nova", nova["power"], "kinds", nova["kinds"])


if __name__ == "__main__":
    main()
