"""ユニーク武器の固有スキルを書き出す（11月28日）。

  python3 -I tools/datagen/build_unique_skills.py

11月28日のご指示：「ユニーク武器や装備には特殊効果や特殊スキルをつけましょう」
決め方【補完】：
  - 祈願（ガチャ）の★5のユニーク武器8本に、1つずつ「固有スキル」をつける。その武器を手に持っているときだけ、
    固有スキルのキー（初期値：Y）で使える。スキルツリーにも段階にも入らない（武器そのものの技）
  - 強さは、職業のスキル（2〜3倍・クールダウン5〜20秒）と奥義（6〜7倍・90秒）のあいだ。3〜4倍の力と、武器の階の
    ボスの記憶にちなんだ働き（打ち上げ・根・水・聖歌・誓環・凍結・写し身・波紋）。クールダウンは35〜50秒
  - 色は、その階の祈願の色（tools/datagen/build_gacha.py の BANNERS）。演出は client/UniqueSkillFx.java
  - 詠唱と溜めはない（押した瞬間に出る）。武器の特殊効果（今までのもの）は、そのまま残す

書き出すもの：data/amemaru/amemaru/unique_skills/<武器>.json
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "amemaru", "amemaru", "unique_skills")

SKILLS = [
    {
        "item": "amemaru:earth_horn_greatsword",
        "name": "角鳴りの地裂き",
        "description": "大剣を地に突き立てると、草原の主の角が目の前の地面を一直線に突き破って立ち上がる。角に貫かれた敵は打ち上げられ、"
                       "しばらくよろめく。",
        "color": "#E2B65A",
        "cost": {"mp": 26},
        "cooldown": 40,
        "delivery": {"type": "line", "flat": True, "range": 9, "width": 2.4},
        "effects": [
            {"type": "damage", "stat": "attack", "coefficient": 3.2},
            {"type": "launch", "strength": 0.6},
            {"type": "status", "effect": "amemaru:stagger", "duration": 1.5},
        ],
        "animation": "knight_earth_split",
        "sound": "amemaru:skill.knight.heavy_slash",
        "fx": "unique_earth_horn",
    },
    {
        "item": "amemaru:millennium_tree_staff",
        "name": "千年樹の芽吹き",
        "description": "杖の種を狙った地へ放つ。種は根を張って敵の足をからめ取り、千年樹の花が開く。花が咲いている間、中の敵は"
                       "少しずつ傷つき、中の仲間は少しずつ癒える。",
        "color": "#7ED957",
        "cost": {"mp": 40},
        "cooldown": 45,
        "delivery": {"type": "point", "range": 20, "radius": 4.5, "impact_delay": 0.3},
        "effects": [
            {"type": "damage", "stat": "magic", "coefficient": 1.6},
            {"type": "root", "duration": 2.5},
            {"type": "zone", "radius": 4.5, "duration": 6, "interval": 1.0, "effects": [
                {"type": "damage", "stat": "magic", "coefficient": 0.25},
                {"type": "heal", "to": "allies", "stat": "magic", "coefficient": 0.2},
            ]},
        ],
        "animation": "wizard_frost_circle",
        "sound": "amemaru:skill.wizard.frost_circle",
        "fx": "unique_millennium",
    },
    {
        "item": "amemaru:tide_chime_bow",
        "name": "大水門の一矢",
        "description": "握りの水門を開き、水をまとった一矢を放つ。当たった所で大水門が開いたように水があふれ、まわりの敵を"
                       "押し流す。防御を3割無視する。",
        "color": "#4FB6F0",
        "cost": {"mp": 24},
        "cooldown": 35,
        "delivery": {"type": "projectile", "kind": "arrow", "range": 36, "speed": 2.6, "radius": 0.5, "explode": 3.5},
        "effects": [
            {"type": "damage", "stat": "attack", "coefficient": 3.0, "pierce": 30},
            {"type": "knockback", "strength": 0.9},
            {"type": "status", "effect": "amemaru:stagger", "duration": 1.0},
        ],
        "tags": ["arrow"],
        "animation": "archer_heavy",
        "sound": "amemaru:skill.archer.piercing_shot",
        "fx": "unique_tide_chime",
    },
    {
        "item": "amemaru:hymn_great_bell",
        "name": "終わらぬ聖歌",
        "description": "神殿で最後まで鳴っていた大鈴を三度鳴らす。聖歌の波が届いた仲間（自分も）を癒し、障壁で包み、しばらく"
                       "与えるダメージを上げる。",
        "color": "#F2C94C",
        "cost": {"mp": 45},
        "cooldown": 50,
        "delivery": {"type": "around", "radius": 9, "affects": "allies", "include_self": True},
        "effects": [
            {"type": "heal", "stat": "magic", "coefficient": 1.8},
            {"type": "barrier", "stat": "magic", "coefficient": 1.2, "duration": 8},
            {"type": "buff", "buff": "power_up", "duration": 10, "value": 12},
        ],
        "animation": "healer_great_call",
        "sound": "amemaru:skill.healer.great_call",
        "fx": "unique_hymn_bell",
    },
    {
        "item": "amemaru:jade_oath_sword",
        "name": "翠環の誓い",
        "description": "古代守護者の誓いの環を、足もとに広げる。環の中の仲間（自分も）は障壁に包まれ、受けるダメージが減る。"
                       "環の中の敵は、こちらから目を離せなくなる。",
        "color": "#4FCC9E",
        "cost": {"mp": 30},
        "cooldown": 45,
        "delivery": {"type": "around", "radius": 6, "affects": "allies", "include_self": True},
        "effects": [
            {"type": "barrier", "stat": "defense", "coefficient": 4.0, "duration": 6},
            {"type": "zone", "radius": 6, "duration": 6, "interval": 0.5, "effects": [
                {"type": "taunt", "duration": 1.5},
                {"type": "buff", "to": "allies", "buff": "ward", "duration": 1.0, "value": 20},
            ]},
        ],
        "animation": "knight_oath_circle",
        "sound": "amemaru:skill.knight.oath_circle",
        "fx": "unique_jade_oath",
    },
    {
        "item": "amemaru:snowlight_fists",
        "name": "雪灯の百裂拳",
        "description": "暖炉の火を閉じこめた拳で、前の敵へ六度打ちこむ。最後の一撃で雪が舞い、打たれた敵は凍りつく。",
        "color": "#BFEFFF",
        "cost": {"mp": 24},
        "cooldown": 40,
        "delivery": {"type": "cone", "radius": 4.5, "angle": 80},
        "effects": [
            {"type": "damage", "stat": "attack", "coefficient": 0.6, "hits": 6, "hit_interval": 3},
            {"type": "chill", "stacks": 5, "duration": 5, "freeze": 2},
        ],
        "tags": ["fist"],
        "animation": "monk_hundred",
        "sound": "amemaru:skill.monk.jab_combo",
        "fx": "unique_snowlight",
    },
    {
        "item": "amemaru:mirror_moon_daggers",
        "name": "鏡月の写し身",
        "description": "鏡に映った自分と入れ替わるように、狙った敵の背後へ移り、三色の刃で三度斬る。斬った敵には標が残り、"
                       "しばらくプレイヤーから受けるダメージが上がる。",
        "color": "#C9A8FF",
        "cost": {"mp": 22},
        "cooldown": 35,
        "delivery": {"type": "behind", "range": 14},
        "effects": [
            {"type": "damage", "stat": "attack", "coefficient": 1.3, "hits": 3, "hit_interval": 4, "backstab": 1.4},
            {"type": "mark", "value": 15, "duration": 8},
        ],
        "tags": ["blade", "move"],
        "animation": "rogue_cross",
        "sound": "amemaru:skill.rogue.shadow_strike",
        "fx": "unique_mirror_moon",
    },
    {
        "item": "amemaru:still_lake_pearl_staff",
        "name": "静湖の大波紋",
        "description": "杖の真珠を狙った地に落とす。静かな湖に落ちた真珠のように、三重の波紋が広がって敵を打ち、敵の攻撃を"
                       "しばらく弱める。",
        "color": "#A8F7FF",
        "cost": {"mp": 50},
        "cooldown": 45,
        "delivery": {"type": "point", "range": 24, "radius": 6, "impact_delay": 0.5},
        "effects": [
            {"type": "damage", "stat": "magic", "coefficient": 3.4},
            {"type": "weaken", "value": 20, "duration": 6},
            {"type": "knockback", "strength": 0.5},
        ],
        "animation": "wizard_star_burst",
        "sound": "amemaru:skill.wizard.star_burst",
        "impact_sound": "amemaru:skill.wizard.meteor_impact",
        "fx": "unique_still_lake",
    },
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for skill in SKILLS:
        name = skill["item"].split(":", 1)[1]
        data = {"_comment": "ユニーク武器の固有スキル（11月28日【補完】。tools/datagen/build_unique_skills.py）"}
        data.update(skill)
        path = os.path.join(OUT, name + ".json")
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(data, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
