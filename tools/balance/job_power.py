"""職業ごとの火力の目安（11月27日）。

  python3 tools/balance/job_power.py [レベル,レベル,...] [--no-awaken]

（--no-awaken：覚醒を入れない。11月27日より前と比べるとき）

11月27日のご指示「モンクが弱すぎる、火力が低すぎる（序盤は弱めでいいが後半で爆発的な攻撃力を）」「どの役職もどこかのタイミングで
強くなるタイミングを（パワースパイク）」を確かめるための、ひとつの的（ボスくらいの防御）を120秒たたいたときの1秒あたりの
ダメージ（DPS）の目安。データパックの JSON（成長・職業・スキル・武器・覚醒）から計算する【補完】。

おおまかな決まり：
- ステータス：成長の基礎＋レベル＋武器（Lv1〜9 見習い・10〜23 古代石・24〜 霜銀。+強化なし）。スキルツリーのステータスの節は入れない
  （どの職業も同じだけ振れるので、比べるときは消える）
- スキル：覚えられるレベルのものは、ぜんぶ取ったことにする（上限の目安）。段階は Lv から決める（取ってから6レベルごとに1つ、4まで）
- 動き：スキルを使うと、その間（溜め・詠唱・連打の長さ、ほかは0.4秒）は通常の攻撃ができない。MPは戦闘中の回復（毎秒1%）だけ
- 近接職の通常の攻撃は、武器の速さで、いつも全力（籠手 2.6回/秒・短剣 2.4・剣 1.6・大剣 1.0）。弓は通常の射撃（1.3回/秒）
- ローグの背後からは半分の割合で当たることにする。会心は確率×会心ダメージの平均
- 覚醒（job の awakenings）は、そのレベルで効くものを入れる
"""
import glob
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru/amemaru")
JOBS = ["knight", "wizard", "healer", "archer", "rogue", "monk"]
WEAPON = {"knight": "greatsword", "wizard": "staff", "healer": "bell_staff", "archer": "bow", "rogue": "daggers", "monk": "gauntlets"}
SPEED = {"greatsword": 1.0, "sword": 1.6, "daggers": 2.4, "gauntlets": 2.6, "bow": 1.3, "staff": 1.2, "bell_staff": 1.2}
MELEE_BASIC = {"knight", "rogue", "monk"}
TIERS = [(1, "apprentice"), (10, "ancient_stone"), (24, "frost_silver")]
SECONDS = 120.0
TICK = 0.05


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


GROWTH = load(os.path.join(DATA, "growth/default.json"))


def job_def(job):
    return load(os.path.join(DATA, "jobs", job + ".json"))


def weapon_attack(job, level):
    tier = [name for lv, name in TIERS if level >= lv][-1]
    path = os.path.join(DATA, "equipment", f"{tier}_{WEAPON[job]}.json")
    if not os.path.exists(path):
        return {}
    return load(path).get("stats", {})


NO_AWAKEN = "--no-awaken" in sys.argv


def awakenings(jd, level):
    if NO_AWAKEN:
        return []
    return [a for a in jd.get("awakenings", []) if level >= a.get("level", 999)]


def awake_value(jd, level, key):
    total = 0.0
    for a in awakenings(jd, level):
        total += a.get("values", {}).get(key, 0.0)
    # 練気：レベルで上がる（from を超えた1レベルにつき per_level%、cap まで）
    if key == "damage":
        for a in awakenings(jd, level):
            scale = a.get("scaling")
            if scale:
                total += min(scale.get("cap", 100), max(0, level - scale["from"]) * scale["per_level"])
    return total


def awake_echo(jd, level, condition, spent=0.0):
    best = 0.0
    for a in awakenings(jd, level):
        e = a.get("echo")
        if e and condition in e["when"] and spent + 1e-6 >= e.get("min_gauge", 0):
            best = max(best, e["percent"])
    return best


def stats(job, level):
    jd = job_def(job)
    out = {}
    gear = weapon_attack(job, level)
    for key in ("hp", "mp", "attack", "magic", "defense", "crit", "crit_damage"):
        v = GROWTH["base"].get(key, 0) + GROWTH["per_level"].get(key, 0) * (level - 1)
        v += gear.get(key, 0) + jd["stats"].get("flat", {}).get(key, 0)
        v *= 1 + jd["stats"].get("percent", {}).get(key, 0) / 100.0
        out[key] = v
    out["crit"] += awake_value(jd, level, "crit")
    out["crit_damage"] += awake_value(jd, level, "crit_damage")
    return out


def skills(job, level):
    out = []
    for p in sorted(glob.glob(os.path.join(DATA, "skills", job, "*.json"))):
        d = load(p)
        if d["type"] != "active" or d.get("ultimate") or d["min_level"] > level:
            continue
        if not any(e["type"] in ("damage", "zone", "poison") for e in d.get("effects", [])):
            continue
        d["rank"] = max(1, min(4, 1 + (level - d["min_level"]) // 6))
        d["id"] = os.path.basename(p)[:-5]
        out.append(d)
    return out


def ranked(e, key, rank):
    return e.get(key, 0.0) + e.get(key + "_per_rank", 0.0) * (rank - 1)


def skill_damage(s, st, defense, job):
    """1回のダメージ（倍率の前。連撃・ゲージは外で掛ける）。"""
    rank = s["rank"]
    total = 0.0
    for e in s["effects"]:
        if e["type"] == "damage":
            coef = ranked(e, "coefficient", rank) * e.get("hits", 1)
            pierce = e.get("pierce", 0) / 100.0
            mit = 100.0 / (100.0 + defense * (1 - pierce))
            back = e.get("backstab", 1.0)
            back = 1 + (back - 1) * 0.5 if job == "rogue" else 1.0
            total += coef * st[e.get("stat", "attack")] * mit * back
        elif e["type"] == "zone":
            hits = e.get("duration", 1) / max(0.05, e.get("interval", 1))
            for inner in e.get("effects", []):
                if inner["type"] == "damage":
                    total += ranked(inner, "coefficient", rank) * hits * st[inner.get("stat", "attack")] * 100.0 / (100.0 + defense)
        elif e["type"] == "poison":
            total += ranked(e, "coefficient", rank) * e.get("duration", 6) * e.get("stacks", 1) * st[e.get("stat", "attack")] \
                * 100.0 / (100.0 + defense)
    charge = s.get("charge")
    if charge:
        total *= charge.get("max_multiplier", 1.0)
    return total


def simulate(job, level, defense):
    jd = job_def(job)
    st = stats(job, level)
    crit_avg = 1 + min(100, st["crit"]) / 100.0 * st["crit_damage"] / 100.0
    damage_up = 1 + awake_value(jd, level, "damage") / 100.0
    skill_up = 1 + awake_value(jd, level, "skill_damage") / 100.0
    finisher_up = 1 + awake_value(jd, level, "finisher_damage") / 100.0
    cd_mult = 1 - awake_value(jd, level, "cooldown") / 100.0
    mp_mult = 1.0
    sk = skills(job, level)
    per_level = jd.get("skill_mp_per_level", 0.0)
    gauge = jd.get("gauge") or {}
    gauge_max = gauge.get("max", 0) + awake_value(jd, level, "gauge_max")
    combo = jd.get("combo")
    combo_max = (combo or {}).get("max", 0) + int(awake_value(jd, level, "combo_max"))
    combo_step = (combo or {}).get("per_step", 0)
    finisher_step = (combo or {}).get("finisher_per_step", 0) + awake_value(jd, level, "combo_finisher_per_step")
    echo = awake_echo(jd, level, "combo_full")
    combo_mp = awake_value(jd, level, "combo_mp")
    mp = st["mp"]
    ready = {s["id"]: 0.0 for s in sk}
    t = 0.0
    lock = 0.0
    next_basic = 0.0
    total = 0.0
    g = 0.0
    chain = 0
    chain_until = 0.0
    last = None
    while t < SECONDS:
        mp = min(st["mp"], mp + st["mp"] * 0.01 * TICK)
        if t >= lock:
            choice = None
            best = 0.0
            for s in sk:
                cost = s.get("cost", {}).get("mp", 0) * (1 + per_level * min(30, s["min_level"])) * mp_mult
                if ready[s["id"]] > t or mp < cost or s.get("cost", {}).get("gauge", 0) > g:
                    continue
                dmg = skill_damage(s, st, defense, job)
                role = s.get("combo")
                if combo:
                    live = chain if t <= chain_until else 0
                    if role == "finisher":
                        # 崩天（余震）があれば、連撃が最大になるまで待つ
                        need = combo_max if echo else min(combo_max, 3 + level // 10)
                        if live < need:
                            continue
                        dmg *= (1 + combo_step * live / 100.0) * (1 + finisher_step * live / 100.0) * finisher_up
                        if live >= combo_max and echo:
                            dmg *= 1 + echo / 100.0
                    elif role in ("opener", "link"):
                        if role == "link" and live == 0:
                            continue
                        if last == s["id"]:
                            continue
                        dmg *= 1 + combo_step * min(combo_max, live + 1) / 100.0
                        dmg *= 3.0  # 連撃をつなぐ価値（締めの技を強くする）を、選ぶときだけ重く見る
                for e in s["effects"]:
                    if e["type"] == "gauge_burst":
                        if g < gauge_max * 0.8:
                            dmg = -1
                        else:
                            dmg *= 1 + g * e["per_gauge"]
                    if e["type"] == "gauge_empower" and g >= e["consume"]:
                        dmg *= e["multiplier"]
                if dmg > best:
                    best, choice = dmg, (s, cost)
            if choice:
                s, cost = choice
                mp -= cost
                ready[s["id"]] = t + max(1.0, (s.get("cooldown", 0) + s.get("cooldown_per_rank", 0) * (s["rank"] - 1)) * cd_mult)
                hit = skill_damage(s, st, defense, job) * crit_avg * damage_up * skill_up
                role = s.get("combo")
                if combo:
                    live = chain if t <= chain_until else 0
                    if role == "finisher":
                        hit *= (1 + combo_step * live / 100.0) * (1 + finisher_step * live / 100.0) * finisher_up
                        if live >= combo_max and echo:
                            hit *= 1 + echo / 100.0
                        chain, last = 0, s["id"]
                    elif role in ("opener", "link"):
                        chain = min(combo_max, (chain if t <= chain_until else 0) + 1) if last != s["id"] else 1
                        chain_until = t + combo["window"]
                        hit *= 1 + combo_step * chain / 100.0
                        last = s["id"]
                        g = min(gauge_max, g + combo.get("ki_per_step", 0))
                        mp = min(st["mp"], mp + st["mp"] * combo_mp / 100.0)
                extra = 0.0
                if s.get("cast") or s.get("charge"):
                    extra = max(extra, awake_echo(jd, level, "cast"))
                if s.get("charge"):
                    extra = max(extra, awake_echo(jd, level, "charge_full"))
                if s.get("cost", {}).get("gauge", 0) > 0:
                    extra = max(extra, awake_echo(jd, level, "gauge_cost"))
                for e in s["effects"]:
                    if e["type"] == "gauge_burst":
                        hit *= 1 + g * e["per_gauge"]
                        extra = max(extra, awake_echo(jd, level, "gauge_burst", g))
                        g = 0
                    elif e["type"] == "gauge_empower" and g >= e["consume"]:
                        hit *= e["multiplier"]
                        g -= e["consume"]
                hit *= 1 + extra / 100.0
                total += hit
                took = 0.4
                if s.get("charge"):
                    took = s["charge"]["time"] + 0.2
                elif s.get("cast"):
                    took = s["cast"]
                else:
                    for e in s["effects"]:
                        if e["type"] == "zone" and e.get("follow"):
                            took = max(took, e.get("duration", 0.4))
                lock = t + took
                hits = sum(e.get("hits", 1) for e in s["effects"] if e["type"] == "damage")
                g = gain(gauge, g, gauge_max, hits)
            elif job in MELEE_BASIC or job == "archer":
                if t >= next_basic:
                    st_key = "attack"
                    hit = st[st_key] * 100.0 / (100.0 + defense) * crit_avg * damage_up
                    total += hit
                    next_basic = t + 1.0 / SPEED[WEAPON[job]]
                    g = gain(gauge, g, gauge_max, 1)
        t += TICK
    return total / SECONDS


def gain(gauge, g, gmax, hits):
    kind = gauge.get("type")
    if kind == "presence":
        return min(gmax, g + hits)
    if kind == "ki":
        return min(gmax, g + gauge.get("hit_gain", 6) * hits)
    if kind == "focus":
        return min(gmax, g + 4 * hits)
    if kind == "oath":
        return min(gmax, g + 3 * hits)
    return g


def boss_defense(level):
    floors = sorted(glob.glob(os.path.join(DATA, "floors", "floor*.json")))
    best = 8
    for p in floors:
        d = load(p)
        lo, hi = d.get("recommended_level", [0, 0])
        if level >= lo:
            boss = d.get("boss_room", {}).get("boss") if isinstance(d.get("boss_room"), dict) else None
            if boss:
                e = load(os.path.join(DATA, "enemies", boss.split(":")[1] + ".json"))
                best = e["stats"].get("defense", best)
    return best


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    levels = [int(x) for x in (args[0] if args else "5,10,15,20,25,30,35,40").split(",")]
    print("| Lv | 的の防御 | " + " | ".join(job_def(j)["name"] for j in JOBS) + " |")
    print("| --- | --- | " + " | ".join("---" for _ in JOBS) + " |")
    for lv in levels:
        d = boss_defense(lv)
        row = [simulate(j, lv, d) for j in JOBS]
        base = max(row)
        cells = []
        for j, v in zip(JOBS, row):
            mark = "".join("★" for a in awakenings(job_def(j), lv))
            cells.append(f"{v:.0f}（{v / base * 100:.0f}%）{mark}")
        print(f"| {lv} | {d} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    main()
