"""職業とスキルのデータを書き出す（Phase 6 その2「職業とスキル」）。

使い方: python3 tools/datagen/build_jobs.py [--check]

書き出すもの：
- data/amemaru/amemaru/jobs/{healer,archer,rogue,monk}.json
- data/amemaru/amemaru/skills/<職業>/<名前>.json（4職業のすべてと、ナイト・ウィザードに足した Lv20・Lv25 の段）
- docs/skills_table.md（全職業のスキルの一覧。設計書と docs/phase6.md から参照する）
- sounds.json に、足したスキルの音（バニラの音を当てる）
ナイト・ウィザードの Lv1〜15 の段のスキルは、手で書いた JSON のまま（ここでは読むだけ）。
--check を付けると、書き出さずに確かめだけする。
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "jobs"))

import common  # noqa: E402
import healer  # noqa: E402
import archer  # noqa: E402
import rogue  # noqa: E402
import monk  # noqa: E402
import knight_wizard  # noqa: E402,F401

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src/main/resources/data/amemaru/amemaru")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/amemaru")
NEW_JOBS = {"healer": healer.JOB, "archer": archer.JOB, "rogue": rogue.JOB, "monk": monk.JOB}
CHECK = "--check" in sys.argv

# 足したスキルの音（バニラの音を当てる。名前 → (バニラの音, 字幕)）
SOUNDS = {
    # ヒーラー
    "skill.healer.bell_heal": ("minecraft:block.amethyst_block.chime", "鈴が鳴る"),
    "skill.healer.prayer_circle": ("minecraft:block.bell.resonate", "祈りの輪が広がる"),
    "skill.healer.bond_mend": ("minecraft:block.amethyst_cluster.place", "光の糸が結ばれる"),
    "skill.healer.healing_rain": ("minecraft:weather.rain", "光の雨が降る"),
    "skill.healer.call_back": ("minecraft:block.bell.use", "鈴が名前を呼ぶ"),
    "skill.healer.life_thread": ("minecraft:block.amethyst_block.resonate", "命の糸がつながる"),
    "skill.healer.swelling_bell": ("minecraft:block.bell.use", "満ちた鈴が鳴る"),
    "skill.healer.resonant_healing": ("minecraft:block.bell.resonate", "鈴の共鳴"),
    "skill.healer.great_call": ("minecraft:block.bell.use", "大いなる呼び声"),
    "skill.healer.great_call_cast": ("minecraft:block.beacon.ambient", "鈴に祈りが集まる"),
    "skill.healer.purifying_chime": ("minecraft:block.amethyst_block.chime", "清めの鈴音"),
    "skill.healer.blessing_light": ("minecraft:block.beacon.power_select", "加護の灯がともる"),
    "skill.healer.silence_sweep": ("minecraft:block.amethyst_block.resonate", "静寂が広がる"),
    "skill.healer.blessed_shield": ("minecraft:block.beacon.activate", "祝福の盾"),
    "skill.healer.admonishing_bell": ("minecraft:block.note_block.bell", "戒めの鈴"),
    "skill.healer.calming_circle": ("minecraft:block.enchantment_table.use", "鎮めの陣"),
    "skill.healer.guardian_bell": ("minecraft:block.note_block.chime", "身代わりの鈴が浮かぶ"),
    "skill.healer.heavens_bell": ("minecraft:block.beacon.ambient", "天鈴が降りてくる"),
    "skill.healer.heavens_bell_impact": ("minecraft:block.bell.use", "天鈴が落ちる"),
    "skill.healer.bond_sanctuary": ("minecraft:block.beacon.activate", "結びの聖域"),
    # アーチャー
    "skill.archer.marking_arrow": ("minecraft:entity.arrow.shoot", "標の矢"),
    "skill.archer.piercing_shot": ("minecraft:item.crossbow.shoot", "射抜く"),
    "skill.archer.pinning_arrow": ("minecraft:entity.arrow.shoot", "縫い止めの矢"),
    "skill.archer.full_draw": ("minecraft:item.crossbow.shoot", "引き絞った矢"),
    "skill.archer.hawks_gaze": ("minecraft:entity.parrot.fly", "鷹の眼差し"),
    "skill.archer.drill_arrow": ("minecraft:item.trident.throw", "穿ち矢"),
    "skill.archer.sniper_stance": ("minecraft:item.crossbow.loading_end", "狙撃の構え"),
    "skill.archer.sky_piercer": ("minecraft:item.trident.thunder", "天穿ち"),
    "skill.archer.shooting_star": ("minecraft:entity.firework_rocket.launch", "流れ星の一矢"),
    "skill.archer.shooting_star_cast": ("minecraft:block.beacon.ambient", "星が集まる"),
    "skill.archer.spread_arrows": ("minecraft:entity.arrow.shoot", "拡散矢"),
    "skill.archer.back_leap": ("minecraft:entity.arrow.shoot", "後跳び"),
    "skill.archer.arrow_rain": ("minecraft:entity.arrow.shoot", "矢を空へ射る"),
    "skill.archer.arrow_rain_impact": ("minecraft:entity.arrow.hit", "矢の雨"),
    "skill.archer.triple_shot": ("minecraft:entity.arrow.shoot", "三連矢"),
    "skill.archer.burst_arrow": ("minecraft:entity.arrow.shoot", "爆ぜ矢"),
    "skill.archer.burst_arrow_impact": ("minecraft:entity.firework_rocket.blast", "矢が弾ける"),
    "skill.archer.whirl_arrows": ("minecraft:entity.player.attack.sweep", "旋風矢"),
    "skill.archer.ricochet": ("minecraft:entity.arrow.shoot", "跳ね矢"),
    "skill.archer.arrow_storm": ("minecraft:entity.arrow.shoot", "矢嵐"),
    "skill.archer.gale_dance": ("minecraft:entity.phantom.flap", "疾風の弓舞"),
    "skill.archer.basic_shot": ("minecraft:entity.arrow.shoot", "矢を射る"),
    # ローグ
    "skill.rogue.flurry": ("minecraft:entity.player.attack.sweep", "乱れ刃"),
    "skill.rogue.shadow_run": ("minecraft:entity.phantom.swoop", "影走り"),
    "skill.rogue.vital_strike": ("minecraft:entity.player.attack.crit", "急所突き"),
    "skill.rogue.shadow_strike": ("minecraft:entity.enderman.teleport", "影討ち"),
    "skill.rogue.shadow_stitch": ("minecraft:entity.enderman.teleport", "影縫い"),
    "skill.rogue.phantom_blades": ("minecraft:entity.player.attack.sweep", "幻影刃"),
    "skill.rogue.shadow_burial": ("minecraft:entity.evoker.prepare_attack", "影葬"),
    "skill.rogue.shadow_double": ("minecraft:entity.illusioner.mirror_move", "影分身"),
    "skill.rogue.thousand_blades": ("minecraft:entity.player.attack.sweep", "千の刃"),
    "skill.rogue.venom_blade": ("minecraft:entity.player.attack.sweep", "毒刃"),
    "skill.rogue.smoke_bomb": ("minecraft:entity.generic.extinguish_fire", "煙玉"),
    "skill.rogue.poison_mist": ("minecraft:entity.splash_potion.break", "毒霧"),
    "skill.rogue.numbing_needle": ("minecraft:entity.arrow.shoot", "痺れ針"),
    "skill.rogue.toxic_burst": ("minecraft:entity.slime.squish", "毒爆の合図"),
    "skill.rogue.toxic_burst_impact": ("minecraft:entity.slime.jump", "毒が弾ける"),
    "skill.rogue.binding_threads": ("minecraft:block.cobweb.place", "絡め糸"),
    "skill.rogue.envenom": ("minecraft:item.bottle.empty", "毒を塗る"),
    "skill.rogue.corroding_edge": ("minecraft:block.lava.extinguish", "腐蝕の刃"),
    "skill.rogue.poison_bloom": ("minecraft:block.chorus_flower.grow", "毒華が咲く"),
    # モンク
    "skill.monk.jab_combo": ("minecraft:entity.player.attack.strong", "連打"),
    "skill.monk.spin_kick": ("minecraft:entity.player.attack.sweep", "旋脚"),
    "skill.monk.hakkei": ("minecraft:entity.iron_golem.attack", "発勁"),
    "skill.monk.collapsing_fist": ("minecraft:entity.player.attack.knockback", "崩拳"),
    "skill.monk.rising_dragon": ("minecraft:entity.blaze.shoot", "昇龍脚"),
    "skill.monk.hundred_fists": ("minecraft:entity.player.attack.strong", "百裂拳"),
    "skill.monk.mountain_breaker": ("minecraft:entity.ravager.roar", "破山撃"),
    "skill.monk.mountain_breaker_impact": ("minecraft:entity.generic.explode", "地面が砕ける"),
    "skill.monk.thunder_fist": ("minecraft:entity.warden.sonic_boom", "轟拳"),
    "skill.monk.hundred_bond": ("minecraft:entity.player.attack.strong", "百烈・結"),
    "skill.monk.palm_strike": ("minecraft:entity.player.attack.knockback", "掌打"),
    "skill.monk.flowing_stance": ("minecraft:ambient.underwater.enter", "流水の構え"),
    "skill.monk.ki_share": ("minecraft:block.beacon.power_select", "気を分け与える"),
    "skill.monk.shrink_step": ("minecraft:entity.phantom.swoop", "縮地"),
    "skill.monk.ki_bullet": ("minecraft:entity.blaze.shoot", "気弾"),
    "skill.monk.turning_throw": ("minecraft:entity.player.attack.knockback", "転身投げ"),
    "skill.monk.ki_vortex": ("minecraft:entity.evoker.cast_spell", "気の渦"),
    "skill.monk.inch_punch": ("minecraft:entity.iron_golem.attack", "寸勁"),
    "skill.monk.heaven_earth": ("minecraft:block.conduit.activate", "天地流転"),
    "combat.chain": ("minecraft:block.note_block.pling", "連撃"),
    "combat.chain_finish": ("minecraft:entity.player.attack.crit", "連撃の締め"),
    # ナイト・ウィザード（足した段）
    "skill.knight.iron_pledge": ("minecraft:item.armor.equip_chain", "鉄の誓約"),
    "skill.knight.shield_bash": ("minecraft:item.shield.block", "盾撃"),
    "skill.knight.great_wheel": ("minecraft:entity.player.attack.sweep", "大車輪"),
    "skill.knight.earth_splitter": ("minecraft:entity.ravager.attack", "地裂斬"),
    "skill.wizard.star_ring": ("minecraft:block.amethyst_block.resonate", "星の環"),
    "skill.wizard.comet": ("minecraft:entity.firework_rocket.launch", "彗星"),
    "skill.wizard.comet_impact": ("minecraft:entity.firework_rocket.large_blast", "彗星が弾ける"),
    "skill.wizard.blizzard": ("minecraft:item.elytra.flying", "吹雪"),
    "skill.wizard.absolute_zero": ("minecraft:block.glass.break", "絶対零度"),
}

SCHOOL_NAMES = {}
for job in NEW_JOBS.values():
    for school in job["schools"]:
        SCHOOL_NAMES[school["id"]] = school["name"]
SCHOOL_NAMES.update({"guardian": "守護", "greatsword": "剛剣", "starlight": "星灯", "frost": "氷結"})
JOB_NAMES = {key: job["name"] for key, job in NEW_JOBS.items()}
JOB_NAMES.update({"knight": "ナイト", "wizard": "ウィザード"})
COMBO_LABEL = {"opener": "起", "link": "継", "finisher": "極"}
LINK_LABEL = {"starter": "起点", "finisher": "追撃", "support": "支援"}


def load_existing():
    """手で書いた既存のスキル（ナイト・ウィザード）。"""
    out = []
    for path in sorted(glob.glob(os.path.join(DATA, "skills", "*", "*.json"))):
        rel = os.path.relpath(path, os.path.join(DATA, "skills"))[:-5]
        if any(s["id"] == rel for s in common.SKILLS):
            continue
        with open(path, encoding="utf-8") as f:
            skill = json.load(f)
        skill["id"] = rel
        out.append(skill)
    return out


def check(all_skills):
    ids = {s["id"] for s in all_skills}
    errors = []
    by_cell = {}
    for s in all_skills:
        for req in s.get("requires", []):
            if req["skill"] not in ids:
                errors.append(f"{s['id']}: 前提のスキル {req['skill']} がない")
        if not s.get("ultimate"):
            key = (s["job"], s["school"], s["row"], s.get("column", 0))
            if key in by_cell:
                errors.append(f"{s['id']} と {by_cell[key]} が同じ場所 {key}")
            by_cell[key] = s["id"]
        if s.get("type", "active") == "active":
            sound = s.get("sound", "")
            if sound.startswith("amemaru:"):
                name = sound.split(":", 1)[1]
                if name not in SOUNDS and not existing_sound(name):
                    errors.append(f"{s['id']}: 音 {sound} がない")
    # 流派ごとの数（アクティブ8・パッシブ5・奥義1）
    for job in list(NEW_JOBS) + ["knight", "wizard"]:
        for school in {s["school"] for s in all_skills if s["job"] == job}:
            group = [s for s in all_skills if s["job"] == job and s["school"] == school]
            actives = [s for s in group if s.get("type", "active") == "active" and not s.get("ultimate")]
            passives = [s for s in group if s.get("type") == "passive"]
            ults = [s for s in group if s.get("ultimate")]
            if (len(actives), len(passives), len(ults)) != (8, 5, 1):
                errors.append(f"{job}/{school}: アクティブ{len(actives)}・パッシブ{len(passives)}・奥義{len(ults)}（8・5・1 のはず）")
    return errors


_EXISTING_SOUNDS = None


def existing_sound(name):
    global _EXISTING_SOUNDS
    if _EXISTING_SOUNDS is None:
        with open(os.path.join(ASSETS, "sounds.json"), encoding="utf-8") as f:
            _EXISTING_SOUNDS = json.load(f)
    return name in _EXISTING_SOUNDS


def write_json(path, data):
    if CHECK:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def write_sounds():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f)
    for name, (vanilla, _subtitle) in SOUNDS.items():
        sounds[name] = {"sounds": [{"name": vanilla, "type": "event"}], "subtitle": "subtitles.amemaru." + name}
    write_json(path, sounds)


def write_lang():
    """字幕を言語ファイルに足す（ja_jp と en_us は同じ中身。Phase 1 の決まり）。"""
    for lang in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", lang + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for name, (_vanilla, subtitle) in SOUNDS.items():
            data["subtitles.amemaru." + name] = subtitle
        write_json(path, data)


def table(all_skills):
    lines = ["# スキルの一覧（全職業）", "",
             "`tools/datagen/build_jobs.py` が書き出す表です（数値はデータパックの JSON と同じ。段は Lv1・5・10・15・20・25、奥義は上位職・Lv30）。",
             "【新】は11月22日のご指示（スキルとパッシブを増やす）で足したもの、それ以外は Phase 0-A 19章の設計のとおりです。",
             "連携は、起点・追撃・支援（Phase 0-A 13章）。モンクの【起】【継】【極】は連撃（Phase 6 その2）。", ""]
    original = original_names()
    for job in ["knight", "wizard", "healer", "archer", "rogue", "monk"]:
        lines.append(f"## {JOB_NAMES[job]}")
        lines.append("")
        schools = []
        for s in all_skills:
            if s["job"] == job and s["school"] not in schools:
                schools.append(s["school"])
        for school in schools:
            lines.append(f"### {SCHOOL_NAMES.get(school, school)}")
            lines.append("")
            lines.append("| 段 | 種類 | スキル | 効果 | MP・ゲージ | クール | 連携・型 |")
            lines.append("| --- | --- | --- | --- | --- | --- | --- |")
            group = [s for s in all_skills if s["job"] == job and s["school"] == school]
            group.sort(key=lambda s: (1 if s.get("ultimate") else 0, s["row"], s.get("column", 0)))
            for s in group:
                kind = "奥義" if s.get("ultimate") else ("パッシブ" if s.get("type") == "passive" else "アクティブ")
                level = "Lv30" if s.get("ultimate") else f"Lv{s.get('min_level', 1)}"
                new = "" if s["name"] in original else "【新】"
                if s.get("ultimate") and s["name"] not in original:
                    new = "【新・提案】"
                cost = ""
                cd = ""
                link = ""
                if s.get("type", "active") == "active":
                    c = s.get("cost", {})
                    cost = str(c.get("mp", 0))
                    if c.get("gauge"):
                        cost += f"・ゲージ{c['gauge']}"
                    cd = f"{s.get('cooldown')}秒"
                    parts = []
                    if s.get("link"):
                        parts.append(LINK_LABEL[s["link"]])
                    if s.get("combo"):
                        parts.append("【" + COMBO_LABEL[s["combo"]] + "】")
                    link = "・".join(parts)
                desc = s["description"].replace("|", "｜")
                lines.append(f"| {level} | {kind} | {s['name']}{new} | {desc} | {cost} | {cd} | {link} |")
            lines.append("")
    return "\n".join(lines) + "\n"


def original_names():
    """Phase 0-A 19章（11月22日より前）に名前があったスキル。"""
    return {
        # ナイト・ウィザード（Phase 2 で作った分）
        "呼び声", "盾結び", "縛鎖", "城壁の構え", "受け流し", "誓いの円陣", "突進斬り", "旋風断", "溜め斬り", "不屈の誓い", "跳躍斬", "誓気解放",
        "守る者の誇り", "鉄壁", "挑発の極意", "誓気の循環", "砕きの心得", "闘志", "不落の城", "天断ち",
        "灯火弾", "流星群", "魔力の糸", "灯火の連弾", "光の帳", "星爆", "氷結陣", "雷鎖", "氷柱", "瞬転", "凍てつく波動", "雷霆",
        "詠唱短縮", "魔力の共鳴", "星の残光", "マナの泉", "氷の心臓", "深冷", "星落とし", "氷華の檻",
        # 4職業
        "癒しの鈴", "祈りの輪", "結び手当て", "加護の灯", "呼び戻し", "静寂払い", "慈愛", "つながりの癒し", "余韻", "大いなる呼び声",
        "射抜き", "拡散矢", "標の矢", "縫い止め", "後跳び", "矢の雨", "鷹の目", "静止集中", "遠矢", "流れ星の一矢",
        "乱れ刃", "急所突き", "影走り", "煙玉", "毒刃", "影縫い", "背後の心得", "身軽", "気配消し", "千の刃",
        "連打", "発勁", "旋脚", "流水の構え", "気功・分け与え", "縮地", "連撃の心得", "鋼の肉体", "仲間の拳", "百烈・結",
    }


def main():
    all_skills = common.SKILLS + load_existing()
    errors = check(all_skills)
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    for key, job in NEW_JOBS.items():
        write_json(os.path.join(DATA, "jobs", key + ".json"), job)
    for skill in common.SKILLS:
        data = dict(skill)
        sid = data.pop("id")
        write_json(os.path.join(DATA, "skills", sid + ".json"), data)
    write_sounds()
    write_lang()
    if not CHECK:
        with open(os.path.join(ROOT, "docs", "skills_table.md"), "w", encoding="utf-8") as f:
            f.write(table(all_skills))
    count = {}
    for s in common.SKILLS:
        count[s["job"]] = count.get(s["job"], 0) + 1
    print("書き出したスキル:", count, "合計", len(common.SKILLS), "（既存", len(all_skills) - len(common.SKILLS), "）")


if __name__ == "__main__":
    main()
