"""強さの目安（11月24日の2回目のバランス調節：レベルの上限・スキルツリー・敵の硬さ。docs/phase8_balance.md）。

  python3 tools/balance/estimate.py            # 1〜5階の目安の表
  python3 tools/balance/estimate.py --targets  # 目標の幅から外れたものだけ

敵・装備・成長・スキルツリーの定義（JSON）から、階ごとのおすすめのレベルの真ん中の「ふつうの装備の人」を作り、
  - 雑魚・名のある魔物を、2人（ウィザードとナイト）で倒すのにかかる時間
  - 敵の1回の攻撃（強い技）で、ウィザード・ナイトのHPが何%減るか
  - ボスを2人・4人で倒すのにかかる時間（怒るまでの時間と比べる）
の目安を出す。ゲームの中の動き（よける・技の組み合わせ・装飾品・結晶果）は入れない、おおまかな数【補完】。

成長は 11月24日の形：レベルが上がるごとにツリーポイント2（5の倍数のレベルではさらに1）。ポイントはスキルツリーの
ステータスの節（1つ1ポイント）とスキルの段階に分けて使う。ここでは、ポイントの55%をステータスの節に使う人を考える。
1秒あたりのダメージは「攻撃（魔力）× 係数 × 段階の伸び」：ナイト1.8・ウィザード2.6（通常攻撃と技をならしたもの）。
戦っている間の手の止まり（移動・よける・回復）で、雑魚は75%、ボスは55%だけ当てられるとする。

目標（11月24日【補完】）：雑魚 2〜9秒（硬い雑魚まで）・名のある魔物 18〜40秒・ボス2人 5〜9分（ひとりは2人ぶんのHP）。
1回の攻撃で減るHP：ナイト 6〜14%、ウィザード 10〜24%（強い技は ナイト 30%・ウィザード 45% まで）。
"""
import glob
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "amemaru", "amemaru"))
G = json.load(open(os.path.join(R, "growth", "default.json"), encoding="utf-8"))
GEAR_RULES = json.load(open(os.path.join(R, "gear_rules", "default.json"), encoding="utf-8"))
JOBS = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "jobs", "*.json"))}
EQ = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "equipment", "*.json"))}
EN = {os.path.basename(p)[:-5]: json.load(open(p, encoding="utf-8")) for p in glob.glob(os.path.join(R, "enemies", "*.json"))}
FL = {}
for p in glob.glob(os.path.join(R, "floors", "floor*.json")):
    d = json.load(open(p, encoding="utf-8"))
    FL[d["floor"]] = d

KEYS = ["hp", "mp", "attack", "magic", "defense", "speed", "crit"]
HEAVY = ["ancient_stone_helm", "ancient_stone_cuirass", "ancient_stone_greaves", "ancient_stone_sabatons"]
ROBE = ["ancient_stone_hood", "ancient_stone_robe", "ancient_stone_legwear", "ancient_stone_boots"]
RYUMON_KNIGHT = ["ryumon_knight_helm", "ryumon_knight_cuirass", "ryumon_knight_greaves", "ryumon_knight_sabatons"]
RYUMON_WIZARD = ["ryumon_wizard_hat", "ryumon_wizard_robe", "ryumon_wizard_legwear", "ryumon_wizard_shoes"]
GEAR = {
    "knight": {"early": ["apprentice_sword", "apprentice_shield"], "mid": HEAVY + ["ancient_stone_sword", "ancient_stone_shield"],
               "late": RYUMON_KNIGHT + ["ryumon_sword", "ryumon_shield"]},
    "wizard": {"early": ["apprentice_staff"], "mid": ROBE + ["ancient_stone_staff"], "late": RYUMON_WIZARD + ["ryumon_staff"]},
}
# 階ごとに見る「おすすめのレベルの真ん中」と、そのころの装備と強化の段（+r は装備のステータス +4%×r）
REFERENCE = {1: [(4, "early", 0)], 2: [(9, "mid", 0)], 3: [(12, "mid", 1), (15, "late", 0)], 4: [(18, "late", 2)],
             5: [(22, "late", 4)]}

# スキルツリー（tools/datagen/build_skill_trees.py の STAT_VALUES と同じ）：ステータスの節の値（内側・中ほど・外側）
NODE = {"hp": (20, 24, 28), "mp": (10, 12, 14), "attack": (2, 2, 3), "magic": (2, 2, 3), "defense": (2, 2, 3),
        "speed": (1, 1, 2), "crit": (0.8, 1.0, 1.2)}
STAT_SHARE = 0.55
NODE_MIX = {"knight": {"hp": 0.35, "attack": 0.3, "defense": 0.25, "crit": 0.1},
            "wizard": {"magic": 0.45, "mp": 0.2, "hp": 0.2, "crit": 0.15}}
# 大きな節（%）：18ポイントごとに1つ取るとする
NOTABLE = {"knight": {"hp": 6, "attack": 6}, "wizard": {"magic": 6}}
DPS = {"knight": ("attack", 1.8), "wizard": ("magic", 2.6)}
TARGET_NORMAL = (2.0, 9.0)
TARGET_NAMED = (18.0, 40.0)
TARGET_BOSS_MIN = (5.0, 9.0)
TARGET_HIT = {"knight": (6.0, 14.0), "wizard": (10.0, 24.0)}
TARGET_STRONG = {"knight": 30.0, "wizard": 45.0}


def tree_points(level):
    p = G["points"]
    pts = p["skill_per_level"] * max(0, level - 1) + p["stat_per_level"] * max(0, level - 1)
    if p.get("skill_bonus_every", 0) > 0:
        pts += p["skill_bonus"] * (level // p["skill_bonus_every"])
    return pts


def band_value(stat, level):
    a, b, c = NODE[stat]
    if level < 10:
        return a
    if level < 20:
        return (a + b) / 2
    return (b + c) / 2


def rank_factor(level):
    return 1.0 if level < 8 else 1.08 if level < 16 else 1.15 if level < 23 else 1.2


def stats(level, job, gear, refine):
    flat = {k: 0.0 for k in KEYS}
    pct = {k: 0.0 for k in KEYS}
    up = 1 + GEAR_RULES.get("refine", {}).get("percent_per_level", 4) / 100.0 * refine if isinstance(GEAR_RULES.get("refine"), dict) \
        else 1 + 0.04 * refine
    for g in gear:
        for k, v in EQ[g]["stats"].items():
            if k in flat:
                flat[k] += v * up
    js = JOBS[job].get("stats", {})
    for k, v in js.get("percent", {}).items():
        pct[k] += v
    for k, v in js.get("flat", {}).items():
        flat[k] += v
    nodes = tree_points(level) * STAT_SHARE
    for k, share in NODE_MIX[job].items():
        flat[k] += nodes * share * band_value(k, level)
    for k, v in NOTABLE[job].items():
        pct[k] += v * (tree_points(level) // 18)
    out = {}
    for k in KEYS:
        v = G["base"].get(k, 0) + G["per_level"].get(k, 0) * (level - 1) + flat[k]
        out[k] = v * (1 + pct[k] / 100)
    return out


def mitigation(defense):
    return 100.0 / (100.0 + max(0.0, defense))


def strongest(e):
    return max([s.get("power", 1.0) for s in e.get("skills", {}).values() if isinstance(s, dict) and "power" in s] or [1.0])


def main():
    only_bad = "--targets" in sys.argv
    bad = 0
    for floor in sorted(FL):
        for level, era, refine in REFERENCE.get(floor, []):
            bad += report(floor, level, era, refine, only_bad)
    if only_bad:
        print("目標から外れたもの：%d" % bad)


def report(floor, level, era, refine, only_bad):
    knight = stats(level, "knight", GEAR["knight"][era], refine)
    wizard = stats(level, "wizard", GEAR["wizard"][era], refine)
    raw = sum(s[DPS[j][0]] * DPS[j][1] * rank_factor(level) for j, s in (("knight", knight), ("wizard", wizard)))
    lines = []
    bad = 0
    boss = FL[floor]["boss_room"]["boss"].split(":")[1]
    for name, e in sorted(EN.items(), key=lambda kv: kv[1]["stats"]["hp"]):
        if e.get("floor") != floor or name == boss or e["stats"]["attack"] <= 0:
            continue
        named = e.get("exp", 0) >= 150
        dps = raw * mitigation(e["stats"]["defense"]) * 0.75
        ttk = e["stats"]["hp"] / dps
        hit = e["stats"]["attack"]
        power = strongest(e)
        wiz = hit * mitigation(wizard["defense"]) / wizard["hp"] * 100
        kni = hit * mitigation(knight["defense"]) / knight["hp"] * 100
        wiz_strong = wiz * power
        kni_strong = kni * power
        lo, hi = TARGET_NAMED if named else TARGET_NORMAL
        flags = []
        if not lo <= ttk <= hi:
            flags.append("倒す秒")
        if not TARGET_HIT["knight"][0] <= kni <= TARGET_HIT["knight"][1]:
            flags.append("ナイト")
        if not TARGET_HIT["wizard"][0] <= wiz <= TARGET_HIT["wizard"][1]:
            flags.append("ウィザード")
        if wiz_strong > TARGET_STRONG["wizard"] or kni_strong > TARGET_STRONG["knight"]:
            flags.append("強い技")
        bad += 1 if flags else 0
        if flags or not only_bad:
            lines.append("| %s%s | %d | %d | %.0f | %.0f%%（強い技 %.0f%%） | %.0f%%（%.0f%%） | %s |" % (
                e["name"], "（名）" if named else "", e["stats"]["hp"], hit, ttk, wiz, wiz_strong, kni, kni_strong, "・".join(flags)))
    room = FL[floor]["boss_room"]
    e = EN[boss]
    k = room.get("hp_per_extra_player", 0.6)
    mn = room.get("hp_min_players", 1)
    boss_lines = []
    for n in (1, 2, 4):
        mult = 1 + k * (max(n, mn) - 1)
        hp = e["stats"]["hp"] * mult
        dps = raw / 2 * n * mitigation(e["stats"]["defense"]) * 0.55
        minutes = hp / dps / 60
        flag = ""
        if n == 2 and not TARGET_BOSS_MIN[0] <= minutes <= TARGET_BOSS_MIN[1]:
            flag = "（目標 %d〜%d 分から外れる）" % TARGET_BOSS_MIN
            bad += 1
        if minutes * 60 > room.get("enrage_seconds", 1e9):
            flag += "（怒るまでに倒せない）"
            bad += 1
        if flag or not only_bad:
            boss_lines.append("ボス %s・%d人：HP %d、およそ %.1f 分（怒るまで %d 分）%s" % (
                e["name"], n, hp, minutes, room.get("enrage_seconds", 0) / 60, flag))
        hit = e["stats"]["attack"]
    hit = e["stats"]["attack"]
    power = strongest(e)
    boss_hit = "ボスの1回：ウィザード %.0f%%（強い技 %.0f%%）・ナイト %.0f%%（%.0f%%）" % (
        hit * mitigation(wizard["defense"]) / wizard["hp"] * 100, hit * power * mitigation(wizard["defense"]) / wizard["hp"] * 100,
        hit * mitigation(knight["defense"]) / knight["hp"] * 100, hit * power * mitigation(knight["defense"]) / knight["hp"] * 100)
    if lines or boss_lines or not only_bad:
        print("== %d階（Lv%d・%s+%d。ウィザード HP%d 魔力%d 防御%d・ナイト HP%d 攻撃%d 防御%d、2人の1秒あたり %d）" % (
            floor, level, {"early": "見習い装備", "mid": "古代石", "late": "流紋"}[era], refine, wizard["hp"], wizard["magic"],
            wizard["defense"], knight["hp"], knight["attack"], knight["defense"], raw))
        print("| 敵 | HP | 攻撃 | 2人で倒す秒 | 1回でウィザードの | ナイトの | 目標から外れる |")
        print("| --- | --- | --- | --- | --- | --- | --- |")
        for line in lines:
            print(line)
        for line in boss_lines:
            print(line)
        if not only_bad:
            print(boss_hit)
        print()
    return bad


if __name__ == "__main__":
    main()
