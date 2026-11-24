"""5階「古代守護者の祭壇」のデータ（敵・落とし物・物語・会話・装備・お守り・言葉）を書き出す（11月24日【補完】）。

  python3 tools/datagen/build_floor5_data.py

Phase 0-A 9章・10章・20章・21章・23章の5階（確定）：
  雑魚：監視眼ウォッチャー（浮かぶ目。見つかると守護者が強くなる）・防衛兵センチネル（盾持ち）・修復ドローン（守護者を回復するので最優先）
  ボス：古代守護者（第1章の章ボス・6人パーティ。3段階・結び台・接続判定・最後に全員が中央に集まると膝をつく）
  古代記録「守護者の記録核」（前からある）。物語は5日目：古代文明の滅亡とリンク・コアの存在が判明・ガルドが正式な冒険者と認める
  21章：守護者の核剣（両手剣：味方が近いほど攻撃+）／守護者の外装（防具：被ダメージ減）各4%。11月23日の決まりで職業ごと
  23章：守護者の記録核「第七の門鍵」（お守り。小さな立方体の翠の結晶、まわりを細い環が回る。認証：3人以上の味方が8ブロック以内に
        いると、その全員の被ダメージが5%減る。偉業：初撃破パーティで、結び台に一番長く乗っていた人。取引不可）
数値・台詞の細かいところは【補完】。言葉は ja_jp.json と en_us.json に同じものを書く。
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_floor4_data import DATA, DEFS, LANG, REPO, enemy, pool, st, write  # noqa: E402

FRUITS = [{"item": "amemaru:crystal_fruit_amber", "chance": 0.6}, {"item": "amemaru:crystal_fruit_azure", "chance": 0.3}]
NAMED_RARE = [{"item": "amemaru:jade_core_shard", "chance": 35.0}, {"item": "amemaru:altar_knot_charm", "chance": 5.0},
              {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}]


def fix_floor(d):
    d["floor"] = 5
    return d


# ================================================================ 守護者シリーズ（職業ごと）

JOBS = OrderedDict([
    ("knight", ("ナイト", "greatsword", "守護者の核剣", {"attack": 42}, "重鎧",
                {"defense": 9, "hp": 42},
                "古代守護者の腕から削り出した、白い石と青銅の大剣。刃の根元に翠の核の欠片が埋めこまれ、近くにいる仲間の気配が増えるほど、"
                "刃の光が強くなる。",
                "守護者の胸を覆っていた白い石の装甲を、騎士の胴に合わせて組み直したもの。継ぎ目を翠の光がめぐり、受けた衝撃を散らす。")),
    ("wizard", ("ウィザード", "staff", "守護者の核杖", {"magic": 33, "mp": 70}, "法衣",
                {"defense": 6, "mp": 56, "magic": 6},
                "翠の核の欠片を、青銅の輪で杖の先に留めたもの。仲間の詠唱の気配に共鳴して、魔力の流れが太くなる。",
                "守護者の装甲の薄い板を、法衣の胸と肩に縫いつけたもの。板の翠の線が、受けた魔力の衝撃を逃がす。")),
    ("healer", ("ヒーラー", "bell_staff", "守護者の核鈴", {"magic": 30, "mp": 70, "heal_power": 7}, "白衣",
                {"defense": 6, "mp": 42, "hp": 21, "heal_power": 3},
                "白い石の輪に、翠の核の欠片の鈴をさげた鈴杖。仲間が近くにいるほど、鈴の音が澄んで遠くまで届く。",
                "守護者の装甲の白い板を、白衣の胸当てにしたもの。翠の線の模様が、傷を受けたときだけ淡く光る。")),
    ("archer", ("アーチャー", "bow", "守護者の核弓", {"attack": 25, "crit": 4}, "革鎧",
                {"defense": 7, "hp": 35, "crit": 2},
                "守護者の腕の青銅の筋を、弓の背に張ったもの。握りの翠の核が、仲間の狙いと重なると強く光る。",
                "守護者の装甲の小さな板を、革の胴着に鱗のように重ねたもの。動きを妨げずに、受けた矢と刃を散らす。")),
    ("rogue", ("ローグ", "daggers", "守護者の双核刃", {"attack": 25, "crit": 4}, "黒装束",
               {"defense": 6, "hp": 31, "speed": 2, "crit": 1},
               "守護者の指の刃を、二本の短剣に打ち直したもの。二つの柄の翠の核が、近くの仲間の数だけ脈打つ。",
               "黒い布の下に、守護者の装甲の薄い板を隠した装束。板の翠の光は、布の内側でだけ灯る。")),
    ("monk", ("モンク", "fist", "守護者の核拳", {"attack": 25, "hp": 40}, "道着",
              {"defense": 7, "hp": 42, "speed": 2},
              "守護者の拳の青銅を、籠手に打ち直したもの。拳の甲の翠の核が、仲間と息を合わせるほど熱を帯びる。",
              "守護者の装甲の帯を、道着の胸と腰に巻いたもの。帯の翠の線が、受けた一撃の重さを地面へ逃がす。")),
])
WEAPON_IDS = {"greatsword": "guardian_greatsword", "staff": "guardian_staff", "bell_staff": "guardian_bell_staff", "bow": "guardian_bow",
              "daggers": "guardian_daggers", "fist": "guardian_gauntlets"}


def guardian_gear():
    out = OrderedDict()
    for job, (jp_name, kind, wname, wstats, aname, astats, wdesc, adesc) in JOBS.items():
        wid = WEAPON_IDS[kind]
        out[wid] = OrderedDict([
            ("slot", "weapon"), ("generation", 1), ("series", "守護者シリーズ（%s）" % jp_name), ("rarity", "epic"), ("required_level", 22),
            ("jobs", ["amemaru:" + job]), ("stats", wstats), ("durability", 620), ("binding", "on_equip"),
            ("effects", [{"type": "ally_power", "threshold": 8, "percent": 3}]), ("rare", True),
            ("repair", [{"item": "amemaru:jade_core_shard", "count": 1}, {"item": "amemaru:sentinel_plate", "count": 2}]),
            ("description", wdesc),
        ])
        aid = "guardian_%s_shell" % job
        out[aid] = OrderedDict([
            ("slot", "chest"), ("generation", 1), ("series", "守護者シリーズ（%s）" % jp_name), ("rarity", "epic"), ("required_level", 22),
            ("jobs", ["amemaru:" + job]), ("stats", astats), ("durability", 600 if job == "knight" else 440), ("binding", "on_equip"),
            ("effects", [{"type": "damage_reduction", "percent": 5}]), ("rare", True),
            ("repair", [{"item": "amemaru:jade_core_shard", "count": 1}, {"item": "amemaru:sentinel_plate", "count": 2}]),
            ("description", adesc),
        ])
    return out


GEAR_NAMES = OrderedDict()
for _job, (_jp, _kind, _wname, _ws, _aname, _as, _wd, _ad) in JOBS.items():
    GEAR_NAMES[WEAPON_IDS[_kind]] = _wname
    GEAR_NAMES["guardian_%s_shell" % _job] = "守護者の外装（%s）" % _aname

GATE_KEY = OrderedDict([
    ("_comment", "守護者の記録核「第七の門鍵」（Phase 0-A 23章のユニーク。お守り。認証：3人以上の味方が8ブロック以内にいると、その全員の"
                 "被ダメージが5%減る。偉業：古代守護者の初撃破パーティで、結び台に一番長く乗っていた人。取引不可。落とし物ではなく、"
                 "初撃破のときに古代守護者が渡す）"),
    ("slot", "charm"), ("generation", 1), ("floor", 5), ("rarity", "unique"),
    ("effects", [{"type": "party_ward", "threshold": 8, "percent": 5}]),
])

# ================================================================ 敵

ENEMIES = {
    "watcher": fix_floor(enemy(
        "監視眼ウォッチャー（5階の雑魚。Phase 0-A 9章「浮かぶ目。見つかると守護者が強くなる」）。はじめて人を見つけると階の警戒を behavior.alert "
        "上げる（同じ目は alert_cooldown 秒に1度）。光線（ray。一直線の予兆のあと撃つ）。宙に浮く。数値は【補完】",
        "監視眼ウォッチャー", 22,
        "金の枠に囲まれた、宙に浮かぶ大きな目。祭壇へ近づく者を見張り続け、見つけると赤く光って、祭壇の守護者に知らせる。",
        st(1160, 132, 8, 0.2, 22, 13), 44,
        {"ray": {"power": 1.2, "windup": 1.0, "cooldown": 3.5, "range": 14.0, "width": 1.2}},
        {"alert": 1, "alert_cooldown": 60.0},
        "watcher", FRUITS)),
    "sentinel": fix_floor(enemy(
        "防衛兵センチネル（5階の雑魚。Phase 0-A 9章「盾持ち」）。前（shield_angle の内）からのダメージは shield_taken。盾打ち（bash。前の扇）と"
        "槍の突進（charge）。数値は【補完】",
        "防衛兵センチネル", 22,
        "白い石と青銅の、祭壇の兵。左腕に大きな塔の盾、右手に槍。盾の正面からの攻撃は、ほとんど通らない。後ろか横から攻めたい。",
        st(2530, 172, 28, 0.19, 20, 10), 52,
        {"bash": {"power": 1.2, "windup": 0.8, "cooldown": 2.4, "range": 3.0, "angle": 100, "push": 0.8},
         "charge": {"power": 1.5, "windup": 1.0, "cooldown": 8.0, "range": 10.0, "min_range": 4.0, "width": 1.4, "speed": 0.9,
                    "recover": 1.2}},
        {"shield_angle": 120.0, "shield_taken": 0.25},
        "sentinel", FRUITS)),
    "repair_drone": fix_floor(enemy(
        "修復ドローン（5階の雑魚。Phase 0-A 9章「守護者を回復するので最優先」）。repair：近くのいちばん傷ついた祭壇の機械へ光の糸を伸ばし、"
        "windup 秒のあと最大HPの power を直す（古代守護者は boss_heal 倍）。糸の間に最大HPの interrupt の傷で止まる。人が近いと離れる。"
        "zap：弱い電撃。数値は【補完】",
        "修復ドローン", 19,
        "青銅の小さな球に、四枚の羽根と翠に光る修理の腕。傷ついた祭壇の機械を見つけると、光の糸で直してしまう。もろいので、先に落としたい。",
        st(630, 80, 4, 0.22, 20, 11), 30,
        {"repair": {"power": 0.08, "windup": 1.5, "cooldown": 6.0, "range": 12.0},
         "zap": {"power": 0.7, "windup": 0.6, "cooldown": 2.5, "range": 2.5}},
        {"boss_heal": 0.1, "interrupt": 0.2},
        "repair_drone", FRUITS)),
    "watcher_great": fix_floor(enemy(
        "監視の大眼（5階の名のある魔物。監視の回廊の西の台・接続の柱 A の守り手。大きく、遠くまで見え、一度に警戒を2つ上げる）。数値は【補完】",
        "監視の大眼", 26,
        "祭壇の見張りを束ねる、ひときわ大きな目。そのまなざしは奈落の向こうまで届き、見つけた者のことを、守護者へ二度知らせる。",
        st(11600, 168, 12, 0.18, 30, 18), 380,
        {"ray": {"power": 1.5, "windup": 1.1, "cooldown": 3.0, "range": 18.0, "width": 2.0}},
        {"alert": 2, "alert_cooldown": 45.0, "scale": 1.8},
        "watcher", NAMED_RARE, model="amemaru:watcher")),
    "sentinel_captain": fix_floor(enemy(
        "防衛隊長（5階の名のある魔物。防衛兵の砦の内郭・接続の柱 B の守り手。大きく、盾がさらに硬い。HPが半分を下回ると一度、防衛兵を call 体呼ぶ）。"
        "数値は【補完】",
        "防衛隊長", 26,
        "砦の内郭を守る、防衛兵たちの隊長。塔の盾には古代の紋が刻まれ、正面からの攻めはまず通らない。傷つくと角笛を吹いて、兵を呼ぶ。",
        st(13300, 188, 34, 0.2, 26, 12), 420,
        {"bash": {"power": 1.45, "windup": 0.9, "cooldown": 2.4, "range": 3.8, "angle": 120, "push": 1.1},
         "charge": {"power": 1.9, "windup": 1.1, "cooldown": 6.0, "range": 14.0, "min_range": 4.0, "width": 2.0, "speed": 1.0,
                    "recover": 1.0}},
        {"shield_angle": 140.0, "shield_taken": 0.2, "call": 2, "scale": 1.4},
        "sentinel", NAMED_RARE, model="amemaru:sentinel")),
}


def guardian_rare():
    rare = []
    for wid in GEAR_NAMES:
        rare.append({"item": "amemaru:" + wid, "chance": 4, "rarity": "epic", "job_match": True})
    rare += [{"item": "amemaru:jade_core_shard", "chance": 100, "count": 2, "pity": False},
             {"item": "amemaru:crystal_fruit_azure", "chance": 20, "pity": False},
             {"item": "amemaru:crystal_fruit_crimson", "chance": 15, "pity": False},
             {"item": "amemaru:crystal_fruit_white", "chance": 15, "pity": False}]
    return rare


ENEMIES["ancient_guardian"] = fix_floor(enemy(
    "5階のボス、古代守護者（第1章の章ボス・6人パーティ。Phase 0-A 9章・10章）。第1段階（HP100〜70%）：腕の薙ぎ払い（sweep。扇。欠けた左腕は"
    "flip_chance で打つ直前に裏返る）・直線の光線（beam）・ヘイト1位への叩きつけ（slam）。第2段階（70〜35%）：結界（barrier）で無敵。4つの"
    "結び台に4人（戦う人が少なければその人数）が同時に hold 秒乗ると解け、stagger 秒よろめき、broken_seconds 秒の間 broken 倍のダメージ。"
    "結界の間は call_every 秒ごとに防衛兵 sentinels・ドローン drones を呼ぶ（max_guards まで）。第2段階のうちは cooldown 秒ごとに張り直す。"
    "第3段階（35〜0%）：接続判定（judge。windup 秒の予兆のあと、range 以内に味方がいない人に power、いる人は linked 倍。ひとりのときは"
    "判定しない）。最後：HPが尽きると止まり、全員が祭壇の中央（gather.range）に集まると膝をつく（timeout 秒で膝をつく）。"
    "祈りの合唱と同じ「みんなで受ける一撃」（share）。HPは2人のときの基準（ひとりでも2人ぶん）。数値は【補完】（4人で20分・6人で16分ほど）",
    "古代守護者", 31,
    "奈落の上の祭壇を守り続ける、白い石と青銅の巨人。胸の翠の記録核には、古代の終わりの記録が眠っている。左腕はロストに白く欠け、"
    "その動きは、ときどき狂う。挑む者たちが互いにつながっているかを、試すように戦う。",
    st(72000, 183, 16, 0.12, 64, 30), 2400,
    {"sweep": {"power": 1.3, "windup": 1.2, "cooldown": 5.0, "range": 9.0, "angle": 140, "push": 1.1, "flip_chance": 0.3,
               "flip_at": 0.45},
     "beam": {"power": 1.5, "windup": 1.6, "cooldown": 8.0, "range": 30.0, "width": 2.4},
     "slam": {"power": 1.8, "windup": 1.4, "cooldown": 9.0, "range": 30.0, "radius": 3.2},
     "barrier": {"cooldown": 45.0, "hold": 3.0, "call_every": 15.0, "max_guards": 5, "sentinels": 2, "drones": 1, "stagger": 4.0,
                 "broken": 1.5, "broken_seconds": 10.0},
     "judge": {"power": 2.6, "windup": 3.0, "cooldown": 14.0, "range": 6.0, "linked": 0.12, "first": 6.0},
     "gather": {"range": 5.0, "timeout": 60.0},
     "share": {"power": 240, "windup": 3.4, "cooldown": 40, "range": 3.5, "first": 30}},
    {},
    "ancient_guardian", guardian_rare(), pity=True))

LOOT = {
    "watcher": [pool("amemaru:watcher_lens", 0.55), pool("amemaru:crystal_shard", 0.35), pool("amemaru:echo_fragment", 0.05)],
    "sentinel": [pool("amemaru:sentinel_plate", 0.6, 1, 2), pool("amemaru:moss_iron", 0.4, 1, 2), pool("amemaru:polishing_powder", 0.2)],
    "repair_drone": [pool("amemaru:drone_spring", 0.5), pool("amemaru:crystal_shard", 0.3)],
    "ancient_guardian": [pool("amemaru:sentinel_plate", None, 3, 5), pool("amemaru:watcher_lens", None, 2, 3),
                         pool("amemaru:drone_spring", 0.8, 1, 2), pool("amemaru:crystal_shard", None, 4, 6),
                         pool("amemaru:clear_echo", 0.4)],
}


def chest_table():
    def e(item, weight, lo, hi):
        x = {"type": "minecraft:item", "name": item, "weight": weight}
        if hi > 1:
            x["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return x
    return {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5},
             "entries": [e("amemaru:watcher_lens", 16, 1, 3), e("amemaru:sentinel_plate", 16, 1, 3), e("amemaru:drone_spring", 10, 1, 2),
                         e("amemaru:crystal_shard", 14, 2, 4), e("amemaru:moss_iron", 12, 2, 4), e("amemaru:ancient_stone", 10, 2, 4),
                         e("amemaru:polishing_powder", 8, 1, 3), e("amemaru:echo_fragment", 5, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:jade_core_shard"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.15}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_amber", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_crimson", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.14}]},
        ],
    }


# ================================================================ 物語（5日目）

def sub(text, ticks=110, wait=True):
    return {"type": "amemaru:subtitle", "text": text, "ticks": ticks, "wait": wait}


STORY = {
    "enter": {
        "_comment": "初めて5階に入ったとき（本人だけ）。深い奈落の上の円形の祭壇（Phase 0-A 9章の5階の地形・環境）。台詞は【補完】",
        "title": "古代守護者の祭壇",
        "trigger": {"type": "amemaru:floor_enter", "floor": 5},
        "scope": "player", "replayable": True,
        "steps": [
            {"type": "amemaru:wait", "ticks": 70},
            {"type": "amemaru:sound", "sound": "minecraft:ambient.basalt_deltas.mood", "volume": 1.0, "pitch": 0.6},
            sub("足もとから、底の見えない奈落が口を開けている。遠い闇の向こうに、円い祭壇が浮かんでいた……。", 120),
            sub("その手前を、いくつもの眼が見張っている。見つかれば、祭壇の主に知られてしまうだろう。", 110, False),
        ],
    },
    "abyss_edge": {
        "_comment": "入口の岩棚のふち（本人だけ。一人一度）。奈落に落ちると、ともした中継の灯（なければ入口）へ戻される【補完】",
        "title": "奈落",
        "trigger": {"type": "amemaru:floor_zone", "floor": 5, "zone": "abyss_edge"},
        "scope": "player", "once": False, "replayable": True,
        "conditions": {"flags_none": ["amemaru:story/abyss_seen"]},
        "steps": [
            sub("底の見えない奈落。踏み外せば、どこまでも落ちていきそうだ。", 100),
            sub("……落ちても、塔の糸が、ともした灯まで引き戻してくれる。ただし、無傷ではすまない。", 110, False),
            {"type": "amemaru:set_flags", "add": ["amemaru:story/abyss_seen"]},
        ],
    },
    "lattice_sky": {
        "_comment": "祭壇の前庭（本人だけ。一人一度）。Phase 0-A 9章の5階「天井の星は、よく見ると格子の結び目」。台詞は【補完】",
        "title": "格子の空",
        "trigger": {"type": "amemaru:floor_zone", "floor": 5, "zone": "lattice_sky"},
        "scope": "player", "once": False, "replayable": True,
        "conditions": {"flags_none": ["amemaru:story/lattice_seen"]},
        "steps": [
            sub("見上げると、天井いっぱいに星が光っている。", 90),
            sub("……いや、よく見ると、星は格子の結び目に並んでいる。誰かがこの空を、糸で編んだかのように。", 130, False),
            {"type": "amemaru:set_flags", "add": ["amemaru:story/lattice_seen"]},
        ],
    },
    "first_kill": {
        "_comment": "5階のボス（第1章の章ボス）の初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。古代文明の滅亡と"
                    "リンク・コアの存在が判明（確定）。台詞の細かいところは【補完】",
        "title": "古代守護者",
        "trigger": {"type": "amemaru:floor_first_kill", "floor": 5},
        "scope": "party", "once": True, "replayable": True,
        "steps": [
            {"type": "amemaru:wait", "ticks": 60},
            {"type": "amemaru:hud", "hide": True},
            {"type": "amemaru:sound", "sound": "minecraft:block.beacon.deactivate", "volume": 1.0, "pitch": 0.6},
            {"type": "amemaru:fade", "from": "none", "to": "white", "ticks": 30},
            {"type": "amemaru:fade", "from": "white", "to": "none", "ticks": 60},
            {"type": "amemaru:title", "title": "古代守護者の祭壇　踏破", "subtitle": "第1章　失われた古代領域", "ticks": 110},
            sub("守護者の胸の記録核が、翠の光をこぼしながら開いた。", 100),
            sub("「全域同期実験、失敗」「大断絶」――そして、中枢装置「リンク・コア」。", 130),
            sub("古代の人々は、すべてをつなごうとして、すべてを失ったのだ。", 120),
            sub("大扉の向こうへ、道が上へ続いている。……街へ戻って、ガルドに知らせよう。", 120),
            {"type": "amemaru:hud", "hide": False},
        ],
    },
}

DIALOGUES = {
    ("gard", "ceremony"): {
        "_comment": "5日目：昇格式。古代守護者を倒した人を、ガルドが正式な冒険者と認める（Phase 0-A 9章の5階の物語・12章のガルド"
                    "「5日目 正式に認める（昇格式）」。確定）。称号「コネリアの冒険者」を渡す。台詞は【補完】",
        "npc": "amemaru:gard",
        "priority": 45,
        "conditions": {"opening": 2, "flags_all": ["amemaru:story/guardian_defeated"],
                       "flags_none": ["amemaru:story/promotion_ceremony_done"]},
        "start": "s1",
        "nodes": {
            "s1": {"expression": "stern", "text": "……古代守護者を、倒したそうだな", "next": "s2"},
            "s2": {"text": "15年前、俺たちが越えられなかった祭壇だ。お前たちは、最後まで互いの名を呼び合えたんだな", "next": "s3"},
            "s3": {"text": "迷い人と呼ぶのは、今日で終わりだ", "next": "s4"},
            "s4": {"expression": "stern", "text": "{player}。今日から、お前はこの街の冒険者だ。冒険者ギルドの長として、ここに認める", "next": "s5"},
            "s5": {"expression": "smile", "text": "……よく、生きて戻った"},
        },
        "end_actions": [
            {"type": "amemaru:grant_title", "title": "amemaru:conelia_adventurer"},
            {"type": "amemaru:bond", "npc": "amemaru:gard", "amount": 20},
            {"type": "amemaru:set_flags", "add": ["amemaru:story/promotion_ceremony_done"]},
        ],
    },
}

# ================================================================ 言葉

LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.cantare"] = OrderedDict([
    ("entity.amemaru.watcher", "監視眼ウォッチャー"),
    ("entity.amemaru.sentinel", "防衛兵センチネル"),
    ("entity.amemaru.repair_drone", "修復ドローン"),
    ("entity.amemaru.ancient_guardian", "古代守護者"),
])
LANG_AFTER["item.amemaru.cantare_spawn_egg"] = OrderedDict([
    ("item.amemaru.watcher_spawn_egg", "監視眼ウォッチャーを呼ぶ卵"),
    ("item.amemaru.sentinel_spawn_egg", "防衛兵センチネルを呼ぶ卵"),
    ("item.amemaru.repair_drone_spawn_egg", "修復ドローンを呼ぶ卵"),
    ("item.amemaru.ancient_guardian_spawn_egg", "古代守護者のスポーンエッグ"),
])
LANG_AFTER["item.amemaru.prayer_bell_staff"] = OrderedDict(("item.amemaru." + k, v) for k, v in GEAR_NAMES.items())
LANG_AFTER["item.amemaru.harold_tag.desc"] = OrderedDict([
    ("item.amemaru.seventh_gate_key", "第七の門鍵"),
    ("item.amemaru.seventh_gate_key.desc", "守護者の記録核。小さな立方体の翠の結晶のまわりを、細い環がゆっくり回り続けている。古代の技術者たちが、"
                                           "未来へ託した「通行証」。古代守護者を初めて倒したとき、結び台に一番長く立っていた人へ渡された。（5階の品）"),
    ("item.amemaru.seventh_gate_key.effect", "認証：3人以上の味方が8ブロック以内にいると、その全員の受けるダメージ −5%"),
])
LANG_AFTER["block.amemaru.knight_relic"] = OrderedDict([
    ("block.amemaru.binding_dais", "結び台"),
])
LANG_AFTER["message.amemaru.beast.roar"] = OrderedDict([
    ("message.amemaru.altar.alert", "%sが監視眼に見つかった！ 祭壇の警戒 %s/%s（守護者の攻撃 +%s%%）。警戒は5分で1つずつ消える"),
    ("message.amemaru.altar.abyss", "奈落に落ちた……。塔の糸に引き戻された（最大HPの15%を失った）"),
    ("message.amemaru.sentinel.shield", "塔の盾に弾かれた！ 防衛兵は、後ろか横から攻めよう"),
    ("message.amemaru.guardian.gate_key", "%sが、守護者の記録核「第七の門鍵」を受け取った！"),
])
LANG_AFTER["boss.amemaru.cantare.tip.rage"] = OrderedDict([
    ("cast.amemaru.ancient_guardian.sweep", "腕の薙ぎ払い（前の扇）"),
    ("cast.amemaru.ancient_guardian.sweep_lost", "欠けた左腕の薙ぎ払い ― 向きが狂うかもしれない"),
    ("cast.amemaru.ancient_guardian.beam", "直線の光線"),
    ("cast.amemaru.ancient_guardian.slam", "叩きつけ（ヘイト1位の足もと）"),
    ("cast.amemaru.ancient_guardian.barrier", "結界 ― 4つの結び台に、同時に乗れ"),
    ("cast.amemaru.ancient_guardian.stagger", "結界が解けた！ 守護者がよろめいている"),
    ("cast.amemaru.ancient_guardian.judge", "接続判定 ― 仲間のそばへ寄れ"),
    ("cast.amemaru.ancient_guardian.kneel", "守護者が動きを止めた"),
    ("cast.amemaru.ancient_guardian.share", "記録核の光 ― 青い円に集まって、みんなで受けろ"),
    ("status.amemaru.ancient_guardian.barrier_1", "結界：無敵。結び台に乗って、そのまま3秒（乗っている台 %s/1）"),
    ("status.amemaru.ancient_guardian.barrier_2", "結界：無敵。2つの結び台に同時に乗って3秒（乗っている台 %s/2）"),
    ("status.amemaru.ancient_guardian.barrier_3", "結界：無敵。3つの結び台に同時に乗って3秒（乗っている台 %s/3）"),
    ("status.amemaru.ancient_guardian.barrier_4", "結界：無敵。4つの結び台に同時に乗って3秒（乗っている台 %s/4）"),
    ("status.amemaru.ancient_guardian.judge", "接続判定まで %s 秒 ― 6ブロック以内に仲間がいれば耐えられる"),
    ("status.amemaru.ancient_guardian.gather", "守護者が待っている。全員で祭壇のまん中の円に集まろう（円の中 %s 人）"),
    ("boss.amemaru.ancient_guardian.line.start", "白い巨人の胸で、翠の記録核が光った。「……接続を、確認する」"),
    ("boss.amemaru.ancient_guardian.line.barrier", "四本の柱が光り、守護者を結界が包んだ！"),
    ("boss.amemaru.ancient_guardian.line.broken", "結び台がそろって光り、結界がほどけた！"),
    ("boss.amemaru.ancient_guardian.line.judge", "「……接続を、判定する」守護者の声が、祭壇じゅうに響いた"),
    ("boss.amemaru.ancient_guardian.line.gather", "守護者の動きが止まった。翠の目が、祭壇のまん中を見つめている……"),
    ("boss.amemaru.ancient_guardian.line.kneel", "守護者が、ゆっくりと膝をついた。「……登録外の、接続波形……」"),
    ("boss.amemaru.ancient_guardian.alerted", "祭壇の警戒 %s：見つかった数だけ、守護者の攻撃が強い（+%s%%）"),
    ("boss.amemaru.ancient_guardian.broken", "結界が解けた！ %s秒の間、守護者は傷つきやすい"),
    ("boss.amemaru.ancient_guardian.judged", "孤立していた……！ 接続判定の大きな一撃を受けた"),
    ("boss.amemaru.ancient_guardian.judge_alone", "ひとりの挑戦者を、守護者は判定しなかった"),
    ("boss.amemaru.ancient_guardian.tip.start", "古代守護者は3つの段階で戦い方が変わる。予兆（赤い扇・線・円）をよく見て、よけよう"),
    ("boss.amemaru.ancient_guardian.tip.lost_arm", "白く欠けた左腕は、打つ直前に向きが裏返ることがある。白い光が走ったら、反対側へ"),
    ("boss.amemaru.ancient_guardian.tip.beam", "地面に長い線が出たら、直線の光線。線の外へよけよう"),
    ("boss.amemaru.ancient_guardian.tip.slam", "ヘイト1位の足もとに赤い円が出たら、叩きつけ。狙われた人は円の外へ"),
    ("boss.amemaru.ancient_guardian.tip.barrier", "結界の間は攻撃が効かない。東西南北の結び台に、手分けして同時に乗ろう。乗っている人を、残りの人が守る"),
    ("boss.amemaru.ancient_guardian.tip.guards", "防衛兵と修復ドローンが来た！ ドローンは守護者を直してしまう。先に落とそう"),
    ("boss.amemaru.ancient_guardian.tip.broken", "今が攻めどき！ 結界が解けた直後は、守護者が傷つきやすい"),
    ("boss.amemaru.ancient_guardian.tip.judge", "接続判定：6ブロック以内に仲間がいない人は、大きな一撃を受ける。仲間のそばへ寄ろう（緑の輪なら大丈夫）"),
    ("boss.amemaru.ancient_guardian.tip.gather", "守護者はもう戦わない。全員で、祭壇のまん中の青い円に集まろう"),
])


def merge_lang():
    for name in ("ja_jp.json", "en_us.json"):
        path = os.path.join(LANG, name)
        with open(path, encoding="utf-8") as fp:
            d = json.load(fp, object_pairs_hook=OrderedDict)
        for anchor, entries in LANG_AFTER.items():
            items = [(k, v) for k, v in d.items() if k not in entries]
            keys = [k for k, _ in items]
            if anchor not in keys:
                raise SystemExit("言葉の目じるしがない: " + anchor)
            i = keys.index(anchor) + 1
            items = items[:i] + list(entries.items()) + items[i:]
            d = OrderedDict(items)
        with open(path, "w", encoding="utf-8") as fp:
            fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def merge_item_types():
    path = os.path.join(REPO, "src/main/resources/amemaru/item_types.json")
    with open(path, encoding="utf-8") as fp:
        d = json.load(fp, object_pairs_hook=OrderedDict)
    weapons = [w for w in d["weapons"] if not w["id"].startswith("guardian_")]
    at = next(i for i, w in enumerate(weapons) if w["id"] == "prayer_bell_staff") + 1
    for kind, wid in WEAPON_IDS.items():
        weapons.insert(at, OrderedDict([("id", wid), ("kind", kind)]))
        at += 1
    d["weapons"] = weapons
    armor = [a for a in d["armor"] if not a["series"].startswith("guardian_")]
    for job in JOBS:
        armor.append(OrderedDict([("series", "guardian_" + job), ("pieces", OrderedDict([("chest", "guardian_%s_shell" % job)]))]))
    d["armor"] = armor
    acc = [a for a in d["accessories"] if a["id"] != "seventh_gate_key"]
    # 11月24日：ユニークなので、体にも表示する（専用のモデルと回る環。tools/artgen/make_gate_key.py）
    acc.append(OrderedDict([("id", "seventh_gate_key"), ("slot", "charm"), ("model", "geckolib")]))
    d["accessories"] = acc
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor5_ancient.json"), chest_table())
    for name, data in STORY.items():
        write(os.path.join(DEFS, "story_events/floor5", name + ".json"), data)
    for (npc, name), data in DIALOGUES.items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    for gid, data in guardian_gear().items():
        write(os.path.join(DEFS, "equipment", gid + ".json"), data)
    write(os.path.join(DEFS, "accessories", "seventh_gate_key.json"), GATE_KEY)
    merge_item_types()
    merge_lang()
    print("enemies", len(ENEMIES), "gear", len(GEAR_NAMES), "story", len(STORY))


if __name__ == "__main__":
    main()
