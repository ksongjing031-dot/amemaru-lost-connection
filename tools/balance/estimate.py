"""強さの目安（11月24日のバランス調節。docs/phase8_balance.md）。

  python3 tools/balance/estimate.py

敵・装備・成長の定義（JSON）から、階ごとのおすすめのレベルの「ふつうの装備の人」を作り、
  - 雑魚・名のある魔物を、2人（ウィザードとナイト）で倒すのにかかる時間
  - 敵の1回の攻撃で、ウィザード・ナイトのHPが何%減るか
  - ボスを2人・4人で倒すのにかかる時間（怒るまでの時間と比べる）
の目安を出す。ゲームの中の動き（よける・技の組み合わせ・装飾品・結晶果）は入れない、おおまかな数【補完】。
1秒あたりのダメージは「攻撃（魔力）× 係数」：ナイト1.8・ウィザード2.6（通常攻撃と技をならしたもの）。
戦っている間の手の止まり（移動・よける・回復）で、雑魚は75%、ボスは55%だけ当てられるとする。
"""
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "amemaru", "amemaru"))
G = json.load(open(os.path.join(R, "growth", "default.json"), encoding="utf-8"))
JOBS = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "jobs", "*.json"))}
EQ = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "equipment", "*.json"))}
EN = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "enemies", "*.json"))}
FL = {}
for p in glob.glob(os.path.join(R, "floors", "floor*.json")):
    d = json.load(open(p, encoding="utf-8"))
    FL[d["floor"]] = d

HEAVY = ["ancient_stone_helm", "ancient_stone_cuirass", "ancient_stone_greaves", "ancient_stone_sabatons"]
ROBE = ["ancient_stone_hood", "ancient_stone_robe", "ancient_stone_legwear", "ancient_stone_boots"]
# 3〜4階の職業ごとの装備「流紋シリーズ」（Phase 8 その2。tools/datagen/build_gear_ryumon.py）
RYUMON_KNIGHT = ["ryumon_knight_helm", "ryumon_knight_cuirass", "ryumon_knight_greaves", "ryumon_knight_sabatons"]
RYUMON_WIZARD = ["ryumon_wizard_hat", "ryumon_wizard_robe", "ryumon_wizard_legwear", "ryumon_wizard_shoes"]
GEAR = {
    "knight": {"early": ["apprentice_sword", "apprentice_shield"], "mid": HEAVY + ["ancient_stone_sword", "ancient_stone_shield"],
               "late": RYUMON_KNIGHT + ["ryumon_sword", "ryumon_shield"]},
    "wizard": {"early": ["apprentice_staff"], "mid": ROBE + ["ancient_stone_staff"], "late": RYUMON_WIZARD + ["ryumon_staff"]},
}
ALLOC = {"knight": {"hp": 0.34, "attack": 0.33, "defense": 0.33}, "wizard": {"magic": 0.6, "hp": 0.2, "mp": 0.2}}
DPS = {"knight": ("attack", 1.8), "wizard": ("magic", 2.6)}
# 階ごとのおすすめのレベルの真ん中と、そのころの装備
# 3階は、入ったころ（古代石）と、流紋シリーズをそろえたころの2つ
REFERENCE = {1: [(3, "early")], 2: [(5, "mid")], 3: [(7, "mid"), (8, "late")]}


def stats(level, job, gear):
    keys = ["hp", "mp", "attack", "magic", "defense", "speed", "crit"]
    points = G["points"]["stat_per_level"] * (level - 1)
    flat = {k: 0.0 for k in keys}
    pct = {k: 0.0 for k in keys}
    for g in gear:
        for k, v in EQ[g]["stats"].items():
            if k in flat:
                flat[k] += v
    js = JOBS[job].get("stats", {})
    for k, v in js.get("percent", {}).items():
        pct[k] += v
    for k, v in js.get("flat", {}).items():
        flat[k] += v
    out = {}
    for k in keys:
        a = ALLOC[job].get(k, 0) * points
        v = G["base"].get(k, 0) + G["per_level"].get(k, 0) * (level - 1) + a * G["per_point"].get(k, 0) + flat[k]
        out[k] = v * (1 + pct[k] / 100)
    return out


def mitigation(defense):
    return 100.0 / (100.0 + max(0.0, defense))


def main():
    for floor in sorted(FL):
        for level, era in REFERENCE.get(floor, []):
            report(floor, level, era)


def report(floor, level, era):
    if True:
        knight = stats(level, "knight", GEAR["knight"][era])
        wizard = stats(level, "wizard", GEAR["wizard"][era])
        raw = sum(s[DPS[j][0]] * DPS[j][1] for j, s in (("knight", knight), ("wizard", wizard)))
        print("== %d階（Lv%d・%s。ウィザード HP%d・ナイト HP%d、2人の1秒あたり %d）" % (
            floor, level, {"early": "見習い装備", "mid": "古代石", "late": "流紋"}[era], wizard["hp"], knight["hp"], raw))
        print("| 敵 | HP | 攻撃 | 2人で倒す秒 | 1回でウィザードの | ナイトの |")
        print("| --- | --- | --- | --- | --- | --- |")
        boss = FL[floor]["boss_room"]["boss"].split(":")[1]
        for name, e in sorted(EN.items(), key=lambda kv: kv[1]["stats"]["hp"]):
            if e.get("floor") != floor or name == boss or e["stats"]["attack"] <= 0:
                continue
            dps = raw * mitigation(e["stats"]["defense"]) * 0.75
            power = max([s.get("power", 1.0) for s in e.get("skills", {}).values() if isinstance(s, dict) and "power" in s] or [1.0])
            hit = e["stats"]["attack"]
            print("| %s | %d | %d | %.0f | %.0f%%（強い技 %.0f%%） | %.0f%% |" % (
                e["name"], e["stats"]["hp"], hit, e["stats"]["hp"] / dps,
                hit * mitigation(wizard["defense"]) / wizard["hp"] * 100, hit * power * mitigation(wizard["defense"]) / wizard["hp"] * 100,
                hit * mitigation(knight["defense"]) / knight["hp"] * 100))
        room = FL[floor]["boss_room"]
        e = EN[boss]
        k = room.get("hp_per_extra_player", 0.6)
        mn = room.get("hp_min_players", 1)
        for n in (1, 2, 4):
            mult = 1 + k * (max(n, mn) - 1)
            hp = e["stats"]["hp"] * mult
            dps = raw / 2 * n * mitigation(e["stats"]["defense"]) * 0.55
            print("ボス %s・%d人：HP %d、およそ %.0f 分（怒るまで %d 分）" % (e["name"], n, hp, hp / dps / 60, room.get("enrage_seconds", 0) / 60))
        print()


if __name__ == "__main__":
    main()
