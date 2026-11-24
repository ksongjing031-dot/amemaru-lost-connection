"""職業とスキルのデータを書くための道具（build_jobs.py から使う）。

数値は「1段目の値」と「段が1つ上がるごとの増え方」の組 (base, per_rank) でも書ける。
例：coef=(1.2, 0.2) → "coefficient": 1.2, "coefficient_per_rank": 0.2
"""

# スキルツリーの段と、覚えられるレベル（11月22日のご指示「スキルとパッシブを増やす」で、Lv20・Lv25 の段を足した）
# 11月24日：レベルの上限を Lv100（1章は Lv30）に上げた。段のレベルは1章のうちにすべての段に届くように、ほぼそのまま
# （Lv21・Lv26）にし、奥義は上位職（Lv60）から。段階は5まで（5段目は上位職から）、使うポイントは 1・1・2・2・3
ROW_LEVEL = {0: 1, 1: 5, 2: 10, 3: 15, 4: 21, 5: 26}
ULTIMATE_LEVEL = 60
MAX_RANK = 5
RANK_COSTS = [1, 1, 2, 2, 3]


# 11月24日：段階を5まで（前は3）にした。5段目（上位職から）が前の3段目と同じ強さになるよう、書いた「1段ごとの増え方」を
# 半分にして書き出す（1段目は同じ。上位職になる前に上げられる4段目は、前の2段目と3段目の間）
RANK_STEP = 0.5


def ranked(key, value):
    """数値か (base, per_rank) を、JSON の2つのキーにする（per_rank は RANK_STEP 倍）。"""
    if isinstance(value, tuple):
        base, per = value
        out = {key: base}
        if per:
            out[key + "_per_rank"] = round(per * RANK_STEP, 4)
        return out
    return {key: value}


def merge(*parts):
    out = {}
    for part in parts:
        out.update(part)
    return out


# ---------------------------------------------------------------- 届け方

def d_self():
    return {"type": "self"}


def d_around(radius, allies=False, include_self=None, downed=False):
    out = merge({"type": "around"}, ranked("radius", radius))
    if allies:
        out["affects"] = "allies"
        out["include_self"] = True if include_self is None else include_self
    if downed:
        out["include_downed"] = True
    return out


def d_target(range_, allies=False, fallback_self=False, smart=False, downed=False):
    out = merge({"type": "target"}, ranked("range", range_))
    if allies:
        out["affects"] = "allies"
        if fallback_self:
            out["fallback_self"] = True
        if smart:
            out["smart"] = True
        if downed:
            out["downed"] = True
    return out


def d_point(range_, radius, delay=0):
    out = merge({"type": "point"}, ranked("range", range_), ranked("radius", radius))
    if delay:
        out.update(ranked("impact_delay", delay))
    return out


def d_projectile(range_, speed=1.8, count=1, spread=0, kind="arrow", radius=0.25, explode=0, burst=1, burst_interval=4,
                 count_charged=0):
    out = merge({"type": "projectile", "kind": kind}, ranked("range", range_), ranked("speed", speed), ranked("radius", radius))
    if count != 1:
        out.update(ranked("count", count))
    if count_charged:
        out.update(ranked("count_charged", count_charged))
    if spread:
        out.update(ranked("spread", spread))
    if explode:
        out.update(ranked("explode", explode))
    if burst != 1:
        out.update(ranked("burst", burst))
        out["burst_interval"] = burst_interval
    return out


def d_dash(distance, width=1.4):
    return merge({"type": "dash"}, ranked("distance", distance), ranked("width", width))


def d_chain(range_, jumps, jump_range=8, falloff=0.85):
    return merge({"type": "chain"}, ranked("range", range_), ranked("jumps", jumps), ranked("jump_range", jump_range),
                 ranked("falloff", falloff))


def d_blink(distance):
    return merge({"type": "blink"}, ranked("distance", distance))


def d_leap(range_, radius):
    return merge({"type": "leap"}, ranked("range", range_), ranked("radius", radius))


def d_line(range_, width=1.2, flat=False):
    out = merge({"type": "line"}, ranked("range", range_), ranked("width", width))
    if flat:
        out["flat"] = True
    return out


def d_cone(radius, angle=60):
    return merge({"type": "cone"}, ranked("radius", radius), ranked("angle", angle))


def d_behind(range_):
    """狙った敵の背後へ回り込み、その敵に効く（11月22日：ローグ・モンク）。"""
    return merge({"type": "behind"}, ranked("range", range_))


def d_backstep(distance, range_=28, speed=2.0, count=1, spread=0, kind="arrow"):
    """後ろへ跳びながら、前へ弾を撃つ（アーチャーの後跳び）。"""
    out = merge({"type": "backstep", "kind": kind}, ranked("distance", distance), ranked("range", range_), ranked("speed", speed))
    if count != 1:
        out.update(ranked("count", count))
    if spread:
        out.update(ranked("spread", spread))
    return out


# ---------------------------------------------------------------- 効果

def dmg(stat, coef, hits=1, interval=4, backstab=None, pierce=None, bonus_status=None, bonus=None, consume=False, lifesteal=None):
    out = merge({"type": "damage", "stat": stat}, ranked("coefficient", coef))
    if hits != 1:
        out.update(ranked("hits", hits))
        out["hit_interval"] = interval
    if backstab:
        out.update(ranked("backstab", backstab))
    if pierce:
        out.update(ranked("pierce", pierce))
    if bonus_status:
        out["bonus_status"] = bonus_status
        out.update(ranked("bonus", bonus))
        if consume:
            out["consume_status"] = True
    if lifesteal:
        out.update(ranked("lifesteal", lifesteal))
    return out


def heal(coef, stat="magic"):
    return merge({"type": "heal", "stat": stat}, ranked("coefficient", coef))


def regen(coef, duration, stat="magic"):
    """毎秒、ステータス × coef だけHPが戻る（duration 秒）。"""
    return merge({"type": "regen", "stat": stat}, ranked("coefficient", coef), ranked("duration", duration))


def cleanse(count=0):
    return {"type": "cleanse", "count": count}


def revive(hp):
    return merge({"type": "revive"}, ranked("hp", hp))


def poison(coef, duration=6, stacks=1, cap=3, stat="attack"):
    return merge({"type": "poison", "stat": stat}, ranked("coefficient", coef), ranked("duration", duration),
                 ranked("stacks", stacks), ranked("max", cap))


def detonate_poison(base, per_stack, stat="attack"):
    return merge({"type": "detonate_poison", "stat": stat}, ranked("base", base), ranked("per_stack", per_stack))


def mark(value, duration):
    return merge({"type": "mark"}, ranked("value", value), ranked("duration", duration))


def root(duration):
    return merge({"type": "root"}, ranked("duration", duration))


def weaken(value, duration):
    return merge({"type": "weaken"}, ranked("value", value), ranked("duration", duration))


def blind(value, duration):
    return merge({"type": "blind"}, ranked("value", value), ranked("duration", duration))


def stealth(duration):
    return merge({"type": "stealth"}, ranked("duration", duration))


def teleport_to():
    return {"type": "teleport_to"}


def throw(strength=1.0):
    return merge({"type": "throw"}, ranked("strength", strength))


def launch(strength):
    return merge({"type": "launch"}, ranked("strength", strength))


def status(effect, duration, level=None):
    out = merge({"type": "status", "effect": effect}, ranked("duration", duration))
    if level:
        out.update(ranked("level", level))
    return out


def stagger(duration):
    return status("amemaru:stagger", duration)


def armor_break(duration):
    return status("amemaru:armor_break", duration)


def slow(duration, level=2):
    return status("minecraft:slowness", duration, level)


def kb(strength):
    return merge({"type": "knockback"}, ranked("strength", strength))


def pull(strength=1.0, stop=2.0):
    return merge({"type": "pull"}, ranked("strength", strength), ranked("stop", stop))


def taunt(duration):
    return merge({"type": "taunt"}, ranked("duration", duration))


def gauge(amount=0, per_target=0, cap=0):
    out = {"type": "gauge"}
    if amount:
        out.update(ranked("amount", amount))
    if per_target:
        out.update(ranked("per_target", per_target))
    if cap:
        out.update(ranked("max", cap))
    return out


def gauge_empower(consume, multiplier):
    return merge({"type": "gauge_empower"}, ranked("consume", consume), ranked("multiplier", multiplier))


def gauge_burst(per_gauge):
    return merge({"type": "gauge_burst"}, ranked("per_gauge", per_gauge))


def barrier(coef, duration, stat="magic", heal_on_end=0):
    out = merge({"type": "barrier", "stat": stat}, ranked("coefficient", coef), ranked("duration", duration))
    if heal_on_end:
        out.update(ranked("heal_on_end", heal_on_end))
    return out


def buff(kind, duration, value=None, to=None, **extra):
    out = merge({"type": "buff", "buff": kind}, ranked("duration", duration))
    if value is not None:
        out.update(ranked("value", value))
    if to:
        out["to"] = to
    for key, val in extra.items():
        if key == "on_success":
            out["on_success"] = val
        else:
            out.update(ranked(key, val))
    return out


def zone(radius, duration, interval, effects, follow=False, mode=None, angle=None):
    out = merge({"type": "zone"}, ranked("radius", radius), ranked("duration", duration), ranked("interval", interval))
    if follow:
        out["follow"] = True
    if mode:
        out["mode"] = mode
    if angle:
        out.update(ranked("angle", angle))
    out["effects"] = effects
    return out


def chill(stacks=1, duration=4, cap=5, freeze=2, freeze_if=0):
    out = merge({"type": "chill"}, ranked("stacks", stacks), ranked("duration", duration), ranked("max", cap),
                ranked("freeze", freeze))
    if freeze_if:
        out.update(ranked("freeze_if", freeze_if))
    return out


def shock(duration=6):
    return merge({"type": "shock"}, ranked("duration", duration))


def brand(stacks=1, duration=8):
    return merge({"type": "brand"}, ranked("stacks", stacks), ranked("duration", duration))


def mp_transfer(amount):
    return merge({"type": "mp_transfer"}, ranked("amount", amount))


def heal_ally_hp(percent):
    return merge({"type": "heal_percent"}, ranked("percent", percent))


# ---------------------------------------------------------------- スキル

SKILLS = []


def active(job, sid, school, row, col, name, desc, *, mp, cd, delivery, effects, cdpr=0, link="none", gauge_cost=0, cast=0,
           charge=None, full=None, tags=(), combo=None, anim=None, cast_anim=None, charge_anim=None, sound=None, cast_sound=None,
           impact_sound=None, requires=None, mppr=0, castpr=0, support_cast=None):
    skill = {
        "id": f"{job}/{sid}",
        "job": job,
        "school": school,
        "row": row,
        "column": col,
        "type": "active",
        "name": name,
        "description": desc,
        "max_rank": MAX_RANK,
        "rank_costs": list(RANK_COSTS),
        "min_level": ROW_LEVEL[row],
    }
    if requires:
        skill["requires"] = [{"skill": r, "rank": 1} for r in ([requires] if isinstance(requires, str) else requires)]
    cost = ranked("mp", (mp, mppr) if mppr else mp)
    if gauge_cost:
        cost.update(ranked("gauge", gauge_cost))
    skill["cost"] = cost
    skill.update(ranked("cooldown", (cd, cdpr) if cdpr else cd))
    if cast:
        skill.update(ranked("cast", (cast, castpr) if castpr else cast))
        if support_cast:
            skill["cast_support_multiplier"] = support_cast
    if link != "none":
        skill["link"] = link
    if charge:
        skill["charge"] = {"time": charge[0], "min_multiplier": charge[1], "max_multiplier": charge[2]}
    skill["delivery"] = delivery
    skill["effects"] = effects
    if full:
        skill["full_charge_effects"] = full
    if tags:
        skill["tags"] = list(tags)
    if combo:
        skill["combo"] = combo
    skill["animation"] = anim or f"{job}_{sid}"
    if cast_anim:
        skill["cast_animation"] = cast_anim
    if charge_anim:
        skill["charge_animation"] = charge_anim
    skill["sound"] = sound or f"amemaru:skill.{job}.{sid}"
    if cast_sound:
        skill["cast_sound"] = cast_sound
    if impact_sound:
        skill["impact_sound"] = impact_sound
    SKILLS.append(skill)
    return skill


def passive(job, sid, school, row, name, desc, ptype, value, requires=None, radius=None, col=2):
    skill = {
        "id": f"{job}/{sid}",
        "job": job,
        "school": school,
        "row": row,
        "column": col,
        "type": "passive",
        "name": name,
        "description": desc,
        "max_rank": MAX_RANK,
        "rank_costs": list(RANK_COSTS),
        "min_level": ROW_LEVEL[row],
    }
    if requires:
        skill["requires"] = [{"skill": r, "rank": 1} for r in ([requires] if isinstance(requires, str) else requires)]
    p = merge({"type": ptype}, ranked("value", value))
    if radius is not None:
        p.update(ranked("radius", radius))
    skill["passive"] = p
    SKILLS.append(skill)
    return skill


def ultimate(job, sid, school, name, desc, *, mp, cd, delivery, effects, requires, gauge_cost=0, cast=0, link="none", tags=(),
             combo=None, anim=None, cast_anim=None, sound=None, cast_sound=None, impact_sound=None, charge=None, full=None,
             charge_anim=None):
    skill = {
        "id": f"{job}/{sid}",
        "job": job,
        "school": school,
        "row": 4,
        "column": 0,
        "type": "active",
        "ultimate": True,
        "name": name,
        "description": desc,
        "max_rank": 1,
        "rank_costs": [5],
        "upper_rank": 1,
        "min_level": ULTIMATE_LEVEL,
        "requires": [{"skill": requires, "rank": 1}],
    }
    cost = {"mp": mp}
    if gauge_cost:
        cost["gauge"] = gauge_cost
    skill["cost"] = cost
    skill["cooldown"] = cd
    if cast:
        skill["cast"] = cast
    if link != "none":
        skill["link"] = link
    if charge:
        skill["charge"] = {"time": charge[0], "min_multiplier": charge[1], "max_multiplier": charge[2]}
    skill["delivery"] = delivery
    skill["effects"] = effects
    if full:
        skill["full_charge_effects"] = full
    if tags:
        skill["tags"] = list(tags)
    if combo:
        skill["combo"] = combo
    skill["animation"] = anim or f"{job}_{sid}"
    if cast_anim:
        skill["cast_animation"] = cast_anim
    if charge_anim:
        skill["charge_animation"] = charge_anim
    skill["sound"] = sound or f"amemaru:skill.{job}.{sid}"
    if cast_sound:
        skill["cast_sound"] = cast_sound
    if impact_sound:
        skill["impact_sound"] = impact_sound
    SKILLS.append(skill)
    return skill
