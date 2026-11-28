"""職業・スキルの定義（data/amemaru/amemaru/{jobs,skills}）を、Mod の読み込みと同じ決まりで確かめる。

Mod の Java（job/def/*.java・SkillRegistry）は Minecraft がないと動かせないので、CI を待たずに確かめるため、
読み込みの決まりをここに写した。数の範囲（Ranked.read / readOptional / checkRange）は Java のソースから読み取るので、
Java を変えれば、ここも自動で合う。種類ごとの細かい決まり（self を付けられる効果など）は手で写している。

使い方：python3 tools/datagen/validate_defs.py   （問題があれば一覧を出して 1 で終わる）
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/jp/amemaru/lostconnection"
DATA = ROOT / "src/main/resources/data/amemaru/amemaru"
ASSETS = ROOT / "src/main/resources/assets/amemaru"

NUM = r"(-?[0-9.]+|MAX_DISTANCE)"


def _num(text: str, constants: dict[str, float]) -> float:
    return constants[text] if text in constants else float(text)


def enum_ids(path: Path, enum_name: str) -> dict[str, list[str]]:
    """enum の定数と、その最初の引数（JSON での名前）と残りの引数。"""
    source = path.read_text(encoding="utf-8")
    start = source.index(f"enum {enum_name}")
    body = source[source.index("{", start) + 1:]
    body = body[:body.index(";")]
    result = {}
    for match in re.finditer(r"^\s*([A-Z_]+)\(\"([a-z_]+)\"(.*?)\)[,;]?\s*$", body, re.M):
        result[match.group(2)] = [match.group(1)] + [a.strip() for a in match.group(3).split(",") if a.strip()]
    return result


def switch_blocks(source: str, marker: str) -> dict[str, str]:
    """「switch (type) {」の中の「case A, B -> …」を、種類 → その中のコードにする。"""
    start = source.index(marker)
    start = source.index("{", start) + 1
    depth = 1
    i = start
    while depth > 0:
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
        i += 1
    body = source[start:i - 1]
    blocks: dict[str, str] = {}
    pos = 0
    pattern = re.compile(r"^\s*case ([A-Z_, ]+) ->", re.M)
    matches = list(pattern.finditer(body))
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        code = body[match.end():end]
        for name in match.group(1).split(","):
            blocks[name.strip()] = code
        pos = end
    return blocks


class ParamSpec:
    def __init__(self, key: str, required: bool, base_min: float, base_max: float):
        self.key = key
        self.required = required
        self.base_min = base_min
        self.base_max = base_max
        self.all_min: float | None = None
        self.all_max: float | None = None
        self.conditional = False


def param_specs(code: str, constants: dict[str, float]) -> dict[str, ParamSpec]:
    specs: dict[str, ParamSpec] = {}
    conditional_spans = []
    for match in re.finditer(r"if \(json\.has\(\"([a-z_]+)\"\)\) \{", code):
        conditional_spans.append((match.end(), code.index("}", match.end())))
    for match in re.finditer(r"Ranked\.read\(json, \"([a-z_]+)\", " + NUM + r", " + NUM + r"\)", code):
        spec = ParamSpec(match.group(1), True, _num(match.group(2), constants), _num(match.group(3), constants))
        spec.conditional = any(a <= match.start() < b for a, b in conditional_spans)
        specs[spec.key] = spec
    for match in re.finditer(r"Ranked\.readOptional\(json, \"([a-z_]+)\", " + NUM + r", " + NUM + r", " + NUM + r"\)", code):
        specs[match.group(1)] = ParamSpec(match.group(1), False, _num(match.group(3), constants), _num(match.group(4), constants))
    for match in re.finditer(r"put\(params, \"([a-z_]+)\", .*?, maxRank, " + NUM + r", " + NUM + r"\);", code):
        spec = specs.get(match.group(1))
        if spec is not None:
            spec.all_min = _num(match.group(2), constants)
            spec.all_max = _num(match.group(3), constants)
    return specs


def at(json_obj: dict, key: str, rank: int, fallback: float = 0.0) -> float:
    base = json_obj.get(key, fallback)
    per = json_obj.get(key + "_per_rank", 0.0)
    return base + per * (rank - 1)


class Validator:
    def __init__(self) -> None:
        self.problems: list[str] = []
        self.warnings: list[str] = []
        def_dir = JAVA / "job/def"
        self.effect_types = enum_ids(def_dir / "EffectDef.java", "Type")
        self.delivery_types = enum_ids(def_dir / "DeliveryDef.java", "Type")
        self.passive_types = enum_ids(def_dir / "PassiveDef.java", "Type")
        self.buff_kinds = enum_ids(def_dir / "BuffKind.java", "BuffKind")
        effect_source = (def_dir / "EffectDef.java").read_text(encoding="utf-8")
        delivery_source = (def_dir / "DeliveryDef.java").read_text(encoding="utf-8")
        constants = {"MAX_DISTANCE": 64.0}
        self.effect_specs = {name: param_specs(code, constants)
                             for name, code in switch_blocks(effect_source, "switch (type) {\n            case DAMAGE").items()}
        self.delivery_specs = {name: param_specs(code, constants)
                               for name, code in switch_blocks(delivery_source, "switch (type) {\n            case SELF -> {").items()}
        # 届け方の、すべての段を確かめる値（checkRange）
        self.delivery_all = {}
        for match in re.finditer(r"def\.([a-zA-Z]+)\.checkRange\(\"delivery\.([a-z_]+)\", maxRank, " + NUM + r", " + NUM + r"\)",
                                 delivery_source):
            self.delivery_all[match.group(2)] = (_num(match.group(3), constants), _num(match.group(4), constants))
        zone = re.search(r"ZONE_TYPES = EnumSet\.of\((.*?)\);", effect_source, re.S).group(1)
        self.zone_types = set(re.findall(r"Type\.([A-Z_]+)", zone))
        selfs = re.search(r"SELF_TYPES = EnumSet\.of\((.*?)\);", effect_source, re.S).group(1)
        self.self_types = set(re.findall(r"Type\.([A-Z_]+)", selfs))
        allies = re.search(r"public boolean forAllies\(\) \{(.*?)\}", effect_source, re.S).group(1)
        self.ally_types = set(re.findall(r"this == ([A-Z_]+)", allies))
        effects_source = (JAVA / "core/registry/ModEffects.java").read_text(encoding="utf-8")
        self.mob_effects = {"amemaru:" + name for name in re.findall(r"EFFECTS\.register\(\"([a-z_]+)\"", effects_source)}
        self.mob_effects |= {"minecraft:" + name for name in (
            "speed slowness haste mining_fatigue strength instant_health instant_damage jump_boost nausea regeneration "
            "resistance fire_resistance water_breathing invisibility blindness night_vision hunger weakness poison wither "
            "health_boost absorption saturation glowing levitation luck unluck slow_falling conduit_power dolphins_grace "
            "bad_omen hero_of_the_village darkness").split()}
        sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
        self.sounds = {"amemaru:" + key for key in sounds}
        known = re.search(r"KNOWN_GAUGES = Set\.of\((.*?)\);",
                          (def_dir / "SkillRegistry.java").read_text(encoding="utf-8")).group(1)
        self.known_gauges = set(re.findall(r"\"([a-z_]+)\"", known))

    # ------------------------------------------------------------------ 共通

    def check_ranked(self, where: str, obj: dict, key: str, required: bool, base_min: float, base_max: float,
                     max_rank: int, all_range: tuple[float, float] | None) -> None:
        if key not in obj:
            if required:
                self.problems.append(f"{where}：「{key}」がありません")
            return
        value = obj[key]
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            self.problems.append(f"{where}：「{key}」は数にしてください")
            return
        if value < base_min or value > base_max:
            self.problems.append(f"{where}：「{key}」が {value} で、範囲（{base_min}〜{base_max}）の外です")
        if all_range is not None:
            for rank in range(1, max_rank + 1):
                v = at(obj, key, rank)
                if v < all_range[0] - 1e-9 or v > all_range[1] + 1e-9:
                    self.problems.append(f"{where}：「{key}」が{rank}段目で {v:g} になり、範囲（{all_range[0]:g}〜{all_range[1]:g}）の外です")
                    break

    def check_int(self, where: str, obj: dict, key: str, required: bool, lo: int, hi: int) -> None:
        if key not in obj:
            if required:
                self.problems.append(f"{where}：「{key}」がありません")
            return
        value = obj[key]
        if not isinstance(value, int) or isinstance(value, bool) or value < lo or value > hi:
            self.problems.append(f"{where}：「{key}」は {lo}〜{hi} の整数にしてください（今は {value}）")

    # ------------------------------------------------------------------ 効果

    GENERAL_EFFECT_KEYS = {"type", "self", "to", "stat", "effect", "buff", "bonus_status", "consume_status", "on_success",
                           "effects", "follow", "mode"}

    def check_effects(self, where: str, effects: list, max_rank: int, inside_zone: bool, statuses: list) -> None:
        for index, effect in enumerate(effects):
            self.check_effect(f"{where}effects[{index}]", effect, max_rank, inside_zone, statuses)

    def check_effect(self, where: str, effect: dict, max_rank: int, inside_zone: bool, statuses: list) -> None:
        type_id = effect.get("type")
        if type_id not in self.effect_types:
            self.problems.append(f"{where}：効果の種類「{type_id}」はありません")
            return
        name = self.effect_types[type_id][0]
        if inside_zone and name not in self.zone_types:
            self.problems.append(f"{where}：陣（zone）の中で、効果「{type_id}」は使えません")
        if effect.get("self") and name not in self.self_types:
            self.problems.append(f"{where}：「self」は {sorted(self.self_types)} にだけ付けられます")
        if effect.get("self") and inside_zone:
            self.problems.append(f"{where}：陣の中の効果に「self」は付けられません")
        if inside_zone and name in self.ally_types and effect.get("to", "allies") != "allies":
            self.problems.append(f"{where}：陣の中の「{type_id}」は、味方にだけかけられます")
        if "to" in effect and effect["to"] not in ("enemies", "allies"):
            self.problems.append(f"{where}：「to」は enemies か allies にしてください")
        specs = self.effect_specs.get(name, {})
        for spec in specs.values():
            required = spec.required and not (spec.conditional and "bonus_status" not in effect)
            all_range = (spec.all_min, spec.all_max) if spec.all_min is not None else None
            self.check_ranked(where, effect, spec.key, required, spec.base_min, spec.base_max, max_rank, all_range)
        allowed = set(self.GENERAL_EFFECT_KEYS)
        for key in specs:
            allowed |= {key, key + "_per_rank"}
        for key in effect:
            if key not in allowed:
                self.warnings.append(f"{where}：「{key}」は効果「{type_id}」では使われません（打ち間違い？）")
        if name in ("DAMAGE", "DETONATE", "BARRIER", "HEAL", "REGEN", "POISON", "DETONATE_POISON"):
            if effect.get("stat") not in ("attack", "magic", "defense"):
                self.problems.append(f"{where}：「stat」は attack・magic・defense のどれかにしてください（今は {effect.get('stat')}）")
        if name == "STATUS":
            statuses.append((where, effect.get("effect")))
        if "bonus_status" in effect:
            statuses.append((where, effect["bonus_status"]))
        if name == "BUFF":
            buff = effect.get("buff")
            if buff not in self.buff_kinds:
                self.problems.append(f"{where}：「buff」に、ない種類「{buff}」があります")
            elif buff == "parry":
                if inside_zone:
                    self.problems.append(f"{where}：受け流し（parry）は、陣の中には置けません")
                for i, inner in enumerate(effect.get("on_success", [])):
                    if inner.get("type") in ("zone", "buff", "gauge_burst", "gauge_empower", "barrier", "shield_bond", "mp_transfer",
                                             "heal", "revive", "teleport_to"):
                        self.problems.append(f"{where}：受け流しの「on_success」に「{inner.get('type')}」は書けません")
                self.check_effects(where + "on_success.", effect.get("on_success", []), max_rank, False, statuses)
        if name == "ZONE":
            mode = effect.get("mode", "area")
            if mode not in ("area", "cone", "nearest"):
                self.problems.append(f"{where}：陣の「mode」は area・cone・nearest のどれかにしてください")
            if mode == "cone" and not effect.get("follow", False):
                self.problems.append(f"{where}：陣の「mode」が cone のときは、「follow」を true にしてください")
            inner = effect.get("effects")
            if not inner:
                self.problems.append(f"{where}：陣（zone）の「effects」が空です")
            else:
                self.check_effects(where + "zone.", inner, max_rank, True, statuses)

    # ------------------------------------------------------------------ 届け方

    def check_delivery(self, where: str, delivery: dict | None, max_rank: int) -> str | None:
        if delivery is None:
            self.problems.append(f"{where}：「delivery」がありません")
            return None
        type_id = delivery.get("type")
        if type_id not in self.delivery_types:
            self.problems.append(f"{where}：届け方の種類「{type_id}」はありません")
            return None
        name = self.delivery_types[type_id][0]
        affects = delivery.get("affects", "allies" if name in ("SELF", "BLINK") else "enemies")
        if delivery.get("fallback_self") and (name != "TARGET" or affects != "allies"):
            self.problems.append(f"{where}：「fallback_self」は、味方を狙う target のときだけ使えます")
        if delivery.get("flat") and name != "LINE":
            self.problems.append(f"{where}：「flat」は line のときだけ使えます")
        if (delivery.get("smart") or delivery.get("downed")) and (name != "TARGET" or affects != "allies"):
            self.problems.append(f"{where}：「smart」「downed」は、味方を狙う target のときだけ使えます")
        if delivery.get("include_downed") and (name != "AROUND" or affects != "allies"):
            self.problems.append(f"{where}：「include_downed」は、味方に効く around のときだけ使えます")
        if name in ("PROJECTILE", "BACKSTEP") and delivery.get("kind", "lamp") not in ("lamp", "arrow", "needle", "ki", "comet"):
            self.problems.append(f"{where}：弾の「kind」は lamp・arrow・needle・ki・comet のどれか")
        specs = self.delivery_specs.get(name, {})
        for spec in specs.values():
            # 「distance」は、PROJECTILE と BACKSTEP の同じ枝の中で、BACKSTEP のときだけ読む
            required = spec.required and not (spec.key == "distance" and name == "PROJECTILE")
            all_range = self.delivery_all.get(spec.key)
            self.check_ranked(where + "delivery", delivery, spec.key, required, spec.base_min, spec.base_max, max_rank, all_range)
        if name in ("PROJECTILE", "BACKSTEP"):
            self.check_int(where + "delivery", delivery, "burst_interval", False, 1, 40)
        allowed = {"type", "affects", "include_self", "fallback_self", "flat", "smart", "downed", "include_downed", "kind",
                   "burst_interval"}
        for key in specs:
            allowed |= {key, key + "_per_rank"}
        for key in delivery:
            if key not in allowed:
                self.warnings.append(f"{where}delivery：「{key}」は届け方「{type_id}」では使われません（打ち間違い？）")
        return name

    # ------------------------------------------------------------------ スキル

    def check_skill(self, skill_id: str, data: dict, statuses: list) -> bool:
        where = f"skills/{skill_id}："
        before = len(self.problems)
        for key in ("job", "school", "name", "description"):
            if not isinstance(data.get(key), str) or not data.get(key):
                self.problems.append(f"{where}「{key}」がありません")
        self.check_int(where, data, "row", True, 0, 5)
        self.check_int(where, data, "column", False, 0, 3)
        kind = data.get("type", "active")
        if kind not in ("active", "passive"):
            self.problems.append(f"{where}「type」は active か passive")
        ultimate = data.get("ultimate", False)
        max_rank = data.get("max_rank", 1 if ultimate else 3)
        self.check_int(where, data, "max_rank", False, 1, 5)
        costs = data.get("rank_costs")
        if costs is not None:
            if len(costs) != max_rank:
                self.problems.append(f"{where}「rank_costs」の数（{len(costs)}）を「max_rank」（{max_rank}）と同じにしてください")
            if any(not isinstance(c, int) or c < 0 or c > 20 for c in costs):
                self.problems.append(f"{where}「rank_costs」は0〜20の整数にしてください")
        upper_rank = data.get("upper_rank", 1 if ultimate else max_rank)
        if not 1 <= upper_rank <= max_rank + 1:
            self.problems.append(f"{where}「upper_rank」が範囲の外です")
        if ultimate and upper_rank != 1:
            self.problems.append(f"{where}奥義（ultimate）は「upper_rank」を1にしてください")
        self.check_int(where, data, "min_level", False, 1, 200)
        for req in data.get("requires", []):
            self.check_int(where + "requires", req, "rank", False, 1, 5)
        if data.get("combo", "none") not in ("none", "opener", "link", "finisher"):
            self.problems.append(f"{where}「combo」は opener・link・finisher のどれか")
        if kind == "active":
            cost = data.get("cost") or {}
            self.check_ranked(where + "cost", cost, "mp", False, 0, 10000, max_rank, (0, 10000))
            self.check_ranked(where + "cost", cost, "gauge", False, 0, 1000, max_rank, (0, 1000))
            self.check_ranked(where, data, "cooldown", True, 0, 3600, max_rank, (0, 3600))
            self.check_ranked(where, data, "cast", False, 0, 30, max_rank, (0, 30))
            if data.get("link", "none") not in ("none", "starter", "finisher", "support"):
                self.problems.append(f"{where}「link」が正しくありません（{data.get('link')}）")
            delivery_name = self.check_delivery(where, data.get("delivery"), max_rank)
            effects = data.get("effects", [])
            if not effects and delivery_name not in ("BLINK", "DASH"):
                self.problems.append(f"{where}「effects」が空です")
            self.check_effects(where, effects, max_rank, False, statuses)
            charge = data.get("charge")
            if charge is not None:
                lo = charge.get("min_multiplier", 1.0)
                hi = charge.get("max_multiplier", 2.0)
                if not 0.2 <= charge.get("time", 1.0) <= 5.0 or not 0.1 <= lo <= 10.0 or not 0.1 <= hi <= 20.0 or hi < lo:
                    self.problems.append(f"{where}「charge」の値が範囲の外です")
                if at(data, "cast", 1) > 0:
                    self.problems.append(f"{where}溜め（charge）と詠唱（cast）は、同じスキルに書けません")
            if data.get("full_charge_effects"):
                if charge is None:
                    self.problems.append(f"{where}「full_charge_effects」は、溜めのあるスキルにだけ書けます")
                self.check_effects(where + "full_charge_", data["full_charge_effects"], max_rank, False, statuses)
            if "passive" in data:
                self.problems.append(f"{where}アクティブのスキルに「passive」は書けません")
        else:
            if ultimate:
                self.problems.append(f"{where}パッシブのスキルは奥義（ultimate）にできません")
            passive = data.get("passive")
            if passive is None:
                self.problems.append(f"{where}「passive」がありません")
            else:
                type_id = passive.get("type")
                if type_id not in self.passive_types:
                    self.problems.append(f"{where}「passive.type」に、ない種類「{type_id}」があります")
                else:
                    uses_radius = self.passive_types[type_id][-1] == "true"
                    self.check_ranked(where + "passive", passive, "value", True, 0, 95, max_rank, (0, 95))
                    if uses_radius:
                        self.check_ranked(where + "passive", passive, "radius", True, 1, 64, max_rank, (0, 64))
        for key in ("sound", "cast_sound", "impact_sound"):
            sound = data.get(key)
            if sound and sound.startswith("amemaru:") and sound not in self.sounds:
                self.problems.append(f"{where}音「{sound}」がありません（{key}）")
        return len(self.problems) == before

    # ------------------------------------------------------------------ 固有スキル（11月28日）

    UNIQUE_FORBIDDEN = ("job", "school", "row", "column", "ultimate", "max_rank", "rank_costs", "upper_rank", "requires", "charge",
                        "passive")

    def check_unique_skills(self) -> int:
        """ユニーク武器の固有スキル（unique_skills/*.json。UniqueSkillDef.java と同じ決まり）。数を返す。"""
        folder = DATA / "unique_skills"
        if not folder.is_dir():
            return 0
        animations = ROOT / "src/main/resources/assets/amemaru/player_animation"
        items = set()
        count = 0
        for path in sorted(folder.glob("*.json")):
            name = path.stem
            where = f"unique_skills/{name}："
            data = json.loads(path.read_text(encoding="utf-8"))
            for key in self.UNIQUE_FORBIDDEN:
                if key in data:
                    self.problems.append(f"{where}固有スキルに「{key}」は書けません")
            item = data.get("item", "")
            if not item.startswith("amemaru:") or not (DATA / "equipment" / (item.split(":", 1)[1] + ".json")).is_file():
                self.problems.append(f"{where}武器「{item}」の定義（equipment）がありません")
            elif item in items:
                self.problems.append(f"{where}武器「{item}」に固有スキルが2つあります")
            else:
                equipment = json.loads((DATA / "equipment" / (item.split(":", 1)[1] + ".json")).read_text(encoding="utf-8"))
                if equipment.get("rarity") != "unique":
                    self.warnings.append(f"{where}武器「{item}」はユニークではありません")
            items.add(item)
            fx = data.get("fx", "unique_" + name)
            if not fx.startswith("unique_"):
                self.problems.append(f"{where}「fx」は unique_ で始めてください")
            if at(data, "cast", 1) > 0:
                self.problems.append(f"{where}固有スキルに詠唱（cast）は付けられません")
            if at(data, "cooldown", 1) < 5:
                self.problems.append(f"{where}クールダウンは5秒以上にしてください")
            animation = data.get("animation", "")
            if animation and not (animations / (animation + ".json")).is_file():
                self.problems.append(f"{where}モーション「{animation}」がありません")
            merged = {k: v for k, v in data.items() if k not in ("item", "color")}
            merged.update({"job": "amemaru:unique", "school": "unique", "row": 0, "type": "active", "max_rank": 1})
            statuses: list = []
            if self.check_skill("amemaru:unique/" + name, merged, statuses):
                bad = [s for s in statuses if s[1] not in self.mob_effects]
                if bad:
                    self.problems.append(f"{where}状態「{bad[0][1]}」がありません")
                else:
                    count += 1
        return count

    # ------------------------------------------------------------------ 全体

    AWAKENING_KEYS = {"damage", "skill_damage", "finisher_damage", "crit", "crit_damage", "taken", "cooldown", "heal", "gauge_max",
                      "combo_max", "combo_finisher_per_step", "combo_mp", "ally_power", "ally_power_seconds"}
    ECHO_CONDITIONS = {"combo_full", "gauge_burst", "cast", "charge_full", "gauge_cost"}

    def check_awakenings(self, job_id: str, data: dict) -> None:
        """覚醒（11月27日。JobDef.parseAwakenings と同じ決まり）。"""
        ids = set()
        for i, entry in enumerate(data.get("awakenings", [])):
            where = f"jobs/{job_id}：awakenings[{i}]"
            if not isinstance(entry, dict) or not entry.get("id") or not entry.get("name"):
                self.problems.append(f"{where} に id と name を書いてください")
                continue
            if entry["id"] in ids:
                self.problems.append(f"{where}：覚醒「{entry['id']}」が2つあります")
            ids.add(entry["id"])
            if not 1 <= int(entry.get("level", 1)) <= 200:
                self.problems.append(f"{where}：level は 1〜200")
            for key in entry.get("values", {}):
                if key not in self.AWAKENING_KEYS:
                    self.problems.append(f"{where}：values の「{key}」は使えません")
            echo = entry.get("echo")
            if echo is not None:
                when = echo.get("when", [])
                if not when or any(w not in self.ECHO_CONDITIONS for w in when):
                    self.problems.append(f"{where}：echo.when は {sorted(self.ECHO_CONDITIONS)} から1つ以上")
            if "combo_max" in entry.get("values", {}) and "combo" not in data:
                self.problems.append(f"{where}：連撃のない職業に combo_max があります")
            if "gauge_max" in entry.get("values", {}) and "gauge" not in data:
                self.problems.append(f"{where}：専用ゲージのない職業に gauge_max があります")

    def run(self) -> int:
        jobs = {}
        for path in sorted((DATA / "jobs").glob("*.json")):
            job_id = "amemaru:" + path.stem
            data = json.loads(path.read_text(encoding="utf-8"))
            jobs[job_id] = data
            gauge = data.get("gauge")
            if gauge and gauge.get("type") not in self.known_gauges:
                self.problems.append(f"jobs/{job_id}：専用ゲージの種類「{gauge.get('type')}」はまだありません（使えるもの：{sorted(self.known_gauges)}）")
            schools = data.get("schools", [])
            if data.get("kind", "combat") == "combat" and not 1 <= len(schools) <= 3:
                self.problems.append(f"jobs/{job_id}：流派は1〜3つにしてください")
            self.check_awakenings(job_id, data)
            if len(data.get("starting_skills", [])) > 6:
                self.problems.append(f"jobs/{job_id}：「starting_skills」は6つまで")
        skills = {}
        for path in sorted((DATA / "skills").rglob("*.json")):
            rel = path.relative_to(DATA / "skills").with_suffix("")
            skill_id = "amemaru:" + "/".join(rel.parts)
            data = json.loads(path.read_text(encoding="utf-8"))
            statuses: list = []
            if not self.check_skill(skill_id, data, statuses):
                continue  # 読み込めない定義は、Mod でも一覧に入らない
            job_id = data["job"] if ":" in data["job"] else "amemaru:" + data["job"]
            job = jobs.get(job_id)
            where = f"skills/{skill_id}："
            if job is None:
                self.problems.append(f"{where}職業「{job_id}」がありません")
                continue
            if job.get("kind", "combat") != "combat":
                self.problems.append(f"{where}スキルを持てるのは戦闘職だけです")
                continue
            if data["school"] not in [s["id"] for s in job.get("schools", [])]:
                self.problems.append(f"{where}流派「{data['school']}」が職業にありません")
                continue
            if data.get("ultimate") and "upper" not in job:
                self.problems.append(f"{where}奥義ですが、職業に上位職（upper）がありません")
                continue
            bad = [s for s in statuses if s[1] not in self.mob_effects]
            if bad:
                self.problems.append(f"{where}状態「{bad[0][1]}」がありません")
                continue
            skills[skill_id] = data
        # 前提
        changed = True
        while changed:
            changed = False
            for skill_id, data in list(skills.items()):
                for req in data.get("requires", []):
                    req_id = req["skill"] if ":" in req["skill"] else "amemaru:" + req["skill"]
                    other = skills.get(req_id)
                    problem = None
                    if other is None:
                        problem = f"前提のスキル「{req_id}」がありません"
                    elif other["job"] != data["job"]:
                        problem = f"前提のスキル「{req_id}」は別の職業のスキルです"
                    elif req.get("rank", 1) > other.get("max_rank", 1 if other.get("ultimate") else 3):
                        problem = f"前提のスキル「{req_id}」の段階は、最大の段階より大きいです"
                    elif req_id == skill_id:
                        problem = "自分自身を前提にしています"
                    if problem:
                        self.problems.append(f"skills/{skill_id}：{problem}")
                        del skills[skill_id]
                        changed = True
                        break
        # 置き場所
        places = {}
        for skill_id, data in skills.items():
            place = data["job"] + "/" + ("ultimate/" + data["school"] if data.get("ultimate")
                                         else f"{data['school']}/{data['row']}/{data.get('column', 0)}")
            if place in places:
                self.problems.append(f"skills/{skill_id}：スキルツリーの同じ場所に「{places[place]}」があります")
            else:
                places[place] = skill_id
        for job_id, data in jobs.items():
            for start in data.get("starting_skills", []):
                start_id = start if ":" in start else "amemaru:" + start
                skill = skills.get(start_id)
                if skill is None:
                    self.problems.append(f"jobs/{job_id}：最初から使えるスキル「{start_id}」がありません")
                elif (skill.get("type", "active") != "active" or skill.get("ultimate") or skill.get("requires")
                      or skill.get("min_level", 1) > 1):
                    self.problems.append(f"jobs/{job_id}：最初から使えるスキル「{start_id}」は、前提のない・レベル1のアクティブにしてください")
        # 武器のタグ（スキルを使うときに見る）と、装備の定義の jobs が合っているか（11月25日：古樹の枝杖などが抜けていた）
        import weapon_tags
        for job, items in sorted(weapon_tags.missing().items()):
            self.problems.append(f"tags/items/weapons/{job}.json：{', '.join(items)} がありません（装備の定義では {job} の武器。"
                                 f"python3 tools/datagen/weapon_tags.py で足せます）")
        # 会話の選択肢は、1つの場面に4つまで（DialogueDef の決まり。11月25日：セレナの「記憶の相談所」で5つにしてしまった）
        for path in sorted((DATA / "dialogues").rglob("*.json")):
            dialogue = json.loads(path.read_text(encoding="utf-8"))
            for node_id, node in dialogue.get("nodes", {}).items():
                if len(node.get("choices", [])) > 4:
                    self.problems.append(f"dialogues/{path.relative_to(DATA / 'dialogues')}：場面「{node_id}」の選択肢が"
                                         f"{len(node['choices'])}つあります（4つまで）")
        uniques = self.check_unique_skills()
        for warning in self.warnings:
            print("注意：" + warning)
        for problem in self.problems:
            print("問題：" + problem)
        print(f"職業 {len(jobs)}・使えるスキル {len(skills)}・固有スキル {uniques}・問題 {len(self.problems)}・注意 {len(self.warnings)}")
        return 1 if self.problems else 0


if __name__ == "__main__":
    sys.exit(Validator().run())
