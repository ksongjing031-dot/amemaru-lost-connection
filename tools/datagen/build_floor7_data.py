"""7階「氷の森林」のデータ（敵・落とし物・宝箱・物語・リリアの会話・ボスのレア・素材・結び紐・言葉）を書き出す（11月25日）。

  python3 tools/datagen/build_floor7_data.py      # tools/mapgen/build_floor7.py で floors/floor7.json を作ってから

Phase 0-A 9章の7階（確定）：鏡のような氷の木々の森。氷に映る自分の姿が、ときどき別の仲間の姿になる。雑魚は写し身（プレイヤーの
姿を真似る敵。名前が「？？？」）・氷角鹿・つらら蝙蝠・吹雪の精。ボスは鏡像獣リフレクタ（パーティメンバーの写し身を作る。本物の
見分け方は、名前の表示とHPバーの動き。ここでロスト状態「薄れ（名前が？？？）」が初登場）。ギミックは氷の鏡で光を導く迷路と、
最奥の合言葉の扉。古代記録「森林監視所の記録」（records/f07_watch_post.json）。物語は7日目：リリアがプレイヤーを忘れる
（「はじめまして……ですよね？」。「ただいま」で少しずつ思い出す。13章）。ロストの侵食：映り込みが間違っている。名前が読めなくなる。
ボスのレア（20章）：鏡氷の短剣（短剣：一定確率で攻撃が二重に当たる）5%。ロスト状態の共通の決まり（11章）：結び直し・回復役の浄化・
消耗品「結び紐」で解除。数値・台詞・素材・名のある魔物は【補完】。
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_floor4_data import DATA, DEFS, LANG, REPO, pool, st, write  # noqa: E402
from build_floor6_data import actor, choice, flags, remove, sub  # noqa: E402,F401

FLOOR = 7
ASSETS = os.path.join(REPO, "src/main/resources/assets/amemaru")


def enemy(comment, name, level, desc, stats, exp, skills, behavior, loot, rare, model=None, pity=None, bestiary=True, lost=False):
    d = OrderedDict()
    d["_comment"] = comment
    d["name"] = name
    if model:
        d["model"] = model
    d["floor"] = FLOOR
    d["level"] = level
    d["description"] = desc
    d["stats"] = stats
    d["exp"] = exp
    d["skills"] = skills
    d["behavior"] = behavior
    drops = OrderedDict([("loot_table", "amemaru:entities/" + loot)])
    if pity is not None:
        drops["pity"] = pity
    drops["rare"] = rare
    d["drops"] = drops
    if lost:
        d["lost"] = True
    if not bestiary:
        d["bestiary"] = False
    return d


FRUITS = [{"item": "amemaru:crystal_fruit_white", "chance": 0.6}, {"item": "amemaru:crystal_fruit_azure", "chance": 0.3}]

ENEMIES = OrderedDict()
ENEMIES["mimic"] = enemy(
    "写し身（7階の雑魚。Phase 0-A 9章の7階「プレイヤーの姿を真似る敵。名前が『？？？』」）。はじめに近づいた人の姿を写す"
    "（enemy/iceforest/Mimic.java・client/render/MimicRenderer.java）。剣で斬る（slash）・踏みこんで突く（charge）。斬られると、"
    "ロスト状態「薄れ」（behavior.fade_seconds 秒。仲間の名前が「？？？」に見える）。名前はいつも「？？？」（氷の色）。数値は【補完】",
    "写し身", 29,
    "氷に映った誰かの姿が、そのまま歩き出したもの。近づいた人と同じ顔、同じ服、同じ武器。名前を呼んでも、答えない。",
    st(2050, 216, 18, 0.26, 22, 12), 70,
    {"slash": {"power": 1.1, "windup": 0.7, "cooldown": 1.8, "range": 2.6, "angle": 100},
     "charge": {"power": 1.6, "windup": 1.1, "cooldown": 8.0, "range": 12.0, "min_range": 4.0, "speed": 0.9, "width": 1.2,
                "recover": 1.2}},
    {"fade_seconds": 8.0},
    "mimic", FRUITS + [{"item": "amemaru:prism_shard", "chance": 1.0}], lost=True)
ENEMIES["reflecta_copy"] = enemy(
    "鏡像獣リフレクタが作る写し身（7階のボスの技 mirror。戦う人ひとりずつを写す。見た目は写し身と同じ）。写し身が残っている間、"
    "リフレクタは鏡の守りで硬い（ward 倍）。すべて倒すと、しばらく守りが割れる。図鑑には載せない。数値は【補完】",
    "写し身", 33,
    "鏡像獣のたてがみの鏡から生まれた、仲間の写し身。本物と同じ顔をしているが、名前は氷の色で、斬ればHPのバーが減っていく。",
    st(3600, 175, 12, 0.27, 40, 32), 0,
    {"slash": {"power": 1.0, "windup": 0.7, "cooldown": 1.9, "range": 2.6, "angle": 100},
     "charge": {"power": 1.4, "windup": 1.1, "cooldown": 9.0, "range": 12.0, "min_range": 4.0, "speed": 0.9, "width": 1.2,
                "recover": 1.2}},
    {"fade_seconds": 6.0},
    "reflecta_copy", [], model="amemaru:mimic", bestiary=False, lost=True)
ENEMIES["ice_stag"] = enemy(
    "氷角鹿（7階の雑魚）。細い脚の鹿で、透きとおった氷の角。角で突く（bite。1階の欠け牙の狼と同じ動き）・頭を下げて突進（charge）。"
    "角に当たると凍える（behavior.chill_stacks 段・chill_seconds 秒）。数値は【補完】",
    "氷角鹿", 28,
    "霜の毛並みに、透きとおった氷の角を持つ鹿。鏡の木々の間を音もなく駆け、近づく者を角で突き上げる。角の先が、鈴のように鳴る。",
    st(1900, 212, 16, 0.28, 22, 12), 64,
    {"bite": {"power": 1.05, "windup": 0.6, "cooldown": 1.6, "range": 2.4, "angle": 90},
     "charge": {"power": 1.7, "windup": 1.2, "cooldown": 8.0, "range": 14.0, "min_range": 5.0, "speed": 0.95, "width": 1.4,
                "recover": 1.2}},
    {"chill_stacks": 1, "chill_seconds": 4.0},
    "ice_stag", FRUITS)
ENEMIES["ice_stag_lord"] = enemy(
    "氷角鹿の主「白角」（7階の名のある魔物。西の林の空き地の宝箱の守り手）。大きく、角に当たると凍えが2つ。数値は【補完】",
    "白角", 32,
    "雪よりも白い、大きな氷角鹿。枝分かれした角は、森の鏡の木々よりも長い。白角が駆けると、林じゅうの氷の枝が鳴る。",
    st(20000, 228, 22, 0.29, 28, 16), 560,
    {"bite": {"power": 1.25, "windup": 0.6, "cooldown": 1.6, "range": 3.0, "angle": 100},
     "charge": {"power": 2.0, "windup": 1.1, "cooldown": 6.0, "range": 18.0, "min_range": 5.0, "speed": 1.05, "width": 2.2,
                "recover": 1.0}},
    {"chill_stacks": 2, "chill_seconds": 5.0, "scale": 1.5},
    "ice_stag", [{"item": "amemaru:prism_shard", "chance": 40.0}, {"item": "amemaru:white_horn_charm", "chance": 5.0},
                 {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}], model="amemaru:ice_stag")
ENEMIES["icicle_bat"] = enemy(
    "つらら蝙蝠（7階の雑魚）。空に浮き、翼のつららを撃つ（needle。1階の結晶蜂と同じ動き）。当たると少し凍える。数値は【補完】",
    "つらら蝙蝠", 28,
    "夜の色の小さな体に、大きな膜の翼。翼のふちに下がったつららを、羽ばたきひとつで撃ち出す。群れで、鏡の木々の枝にぶら下がっている。",
    st(1250, 202, 9, 0.24, 24, 14), 56,
    {"needle": {"power": 0.95, "windup": 0.8, "cooldown": 2.8, "range": 14, "speed": 0.95}},
    {"keep_distance": 7, "hover_height": 4.5},
    "icicle_bat", FRUITS)
ENEMIES["blizzard_spirit"] = enemy(
    "吹雪の精（7階の雑魚）。宙に浮き、氷のつぶて（needle）と吹雪（blizzard。6階の凍て鳥と同じ動き）。吹雪に巻かれると、凍えと、"
    "ロスト状態「薄れ」（behavior.fade_seconds 秒）。数値は【補完】",
    "吹雪の精", 29,
    "雪の衣のフードをかぶった、顔のない精。フードの闇に、2つの目だけが光っている。精が通ったあとは、足あとも、名前も、雪に埋もれる。",
    st(1450, 206, 10, 0.22, 24, 14), 66,
    {"needle": {"power": 0.9, "windup": 0.8, "cooldown": 3.0, "range": 14, "speed": 0.9},
     "blizzard": {"power": 0.32, "windup": 1.8, "cooldown": 16.0, "range": 16.0, "radius": 6.0, "seconds": 5.0, "chill_seconds": 4.0}},
    {"keep_distance": 8, "hover_height": 3.0, "fade_seconds": 8.0},
    "blizzard_spirit", FRUITS)
ENEMIES["blizzard_great_spirit"] = enemy(
    "吹雪の大精（7階の名のある魔物。東の凍った滝の宝箱の守り手）。大きく、吹雪の間が短く、範囲が広い。薄れも長い。数値は【補完】",
    "吹雪の大精", 32,
    "凍った滝に棲む、大きな吹雪の精。衣のすそが、オーロラの色に光る。大精が歌うと、森じゅうの雪が舞い上がり、誰が誰だか分からなくなる。",
    st(18000, 222, 14, 0.22, 28, 16), 540,
    {"needle": {"power": 1.05, "windup": 0.8, "cooldown": 2.4, "range": 16, "speed": 1.0},
     "blizzard": {"power": 0.4, "windup": 1.6, "cooldown": 11.0, "range": 20.0, "radius": 8.0, "seconds": 6.0, "chill_seconds": 5.0}},
    {"keep_distance": 9, "hover_height": 3.5, "fade_seconds": 12.0, "scale": 1.6},
    "blizzard_spirit", [{"item": "amemaru:prism_shard", "chance": 40.0}, {"item": "amemaru:blizzard_lamp_charm", "chance": 5.0},
                        {"item": "amemaru:crystal_fruit_violet", "chance": 6.0}], model="amemaru:blizzard_spirit")


def reflecta_rare():
    return [{"item": "amemaru:mirror_ice_daggers", "chance": 5, "rarity": "epic"},
            {"item": "amemaru:prism_shard", "chance": 100, "count": 2, "pity": False},
            {"item": "amemaru:crystal_fruit_white", "chance": 20, "pity": False},
            {"item": "amemaru:crystal_fruit_azure", "chance": 15, "pity": False},
            {"item": "amemaru:crystal_fruit_violet", "chance": 15, "pity": False}]


ENEMIES["reflecta"] = enemy(
    "7階のボス、鏡像獣リフレクタ（Phase 0-A 9章の7階：パーティメンバーの写し身を作る。本物の見分け方は、名前の表示とHPバーの動き。"
    "ロスト状態「薄れ」の初登場）。技：claw（前の扇。押す）・shards（線。凍え chill）・prism（見ている方の細い扇 angle。当たると薄れ "
    "fade_seconds 秒）・scatter（第2段階から。みんなの足もとに円 range）・mirror（戦う人ひとりずつの写し身を、max 体まで range の所に"
    "作り、全員を薄れ fade_seconds 秒。写し身が残っている間は受けるダメージが ward 倍。すべて倒すと open_seconds 秒、受けるダメージが "
    "broken 倍。last 秒残った写し身は本体へ戻り、1体ごとにHPが heal% 戻る。first 秒から。段階が変わるたびにも使う）。"
    "HPは2人のときの基準（ひとりでも2人ぶん）。数値は【補完】（tools/balance/estimate.py の目安）",
    "鏡像獣リフレクタ", 35,
    "氷と鏡の板でできた、獅子のような獣。放射状に並ぶ鏡のたてがみに、近づく者の姿を映し取る。映し取られた姿は、たてがみから"
    "歩き出し、本物の隣に並ぶ。……どちらが本物か、名前を呼んで確かめることは、もうできない。",
    st(96000, 214, 20, 0.17, 64, 30), 2600,
    {"claw": {"power": 1.35, "windup": 1.3, "anim_windup": 1.1, "cooldown": 6.0, "range": 6.5, "angle": 110, "push": 0.9},
     "shards": {"power": 1.45, "windup": 1.6, "anim_windup": 1.4, "cooldown": 10.0, "range": 24.0, "width": 2.4, "chill": 1},
     "prism": {"power": 1.15, "windup": 2.0, "anim_windup": 1.8, "cooldown": 16.0, "range": 20.0, "angle": 50, "fade_seconds": 12.0,
               "first": 10.0},
     "scatter": {"power": 1.1, "windup": 1.6, "anim_windup": 1.4, "cooldown": 18.0, "range": 3.0, "first": 15.0},
     "mirror": {"windup": 2.0, "anim_windup": 2.0, "cooldown": 60.0, "range": 6.0, "first": 20.0, "max": 6, "fade_seconds": 20.0,
                "ward": 0.3, "broken": 1.3, "open_seconds": 10.0, "last": 45.0, "heal": 3.0}},
    {},
    "reflecta", reflecta_rare(), pity=True)

LOOT = {
    "mimic": [pool("amemaru:mirror_ice", 0.55), pool("amemaru:echo_fragment", 0.12)],
    "reflecta_copy": [],
    "ice_stag": [pool("amemaru:ice_antler", 0.6), pool("amemaru:frozen_fur", 0.3)],
    "icicle_bat": [pool("amemaru:icicle_wing", 0.6), pool("amemaru:ice_crystal", 0.25)],
    "blizzard_spirit": [pool("amemaru:blizzard_core", 0.55), pool("amemaru:ice_crystal", 0.25), pool("amemaru:mirror_ice", 0.15)],
    "reflecta": [pool("amemaru:mirror_ice", None, 4, 6), pool("amemaru:prism_shard", None, 1, 2), pool("amemaru:blizzard_core", None, 2, 4),
                 pool("amemaru:ice_antler", 0.8, 1, 2), pool("amemaru:knot_cord", 0.6, 1, 2), pool("amemaru:clear_echo", 0.4)],
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
             "entries": [e("amemaru:mirror_ice", 14, 1, 3), e("amemaru:ice_antler", 14, 1, 3), e("amemaru:icicle_wing", 12, 1, 3),
                         e("amemaru:blizzard_core", 10, 1, 2), e("amemaru:frost_silver", 10, 1, 2), e("amemaru:ice_crystal", 10, 1, 2),
                         e("amemaru:knot_cord", 8, 1, 2), e("amemaru:polishing_powder", 8, 1, 3), e("minecraft:bread", 6, 1, 3),
                         e("amemaru:echo_fragment", 5, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:clear_echo"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_white", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_violet", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.14}]},
        ],
    }


# ================================================================ ボスのレア（20章：鏡氷の短剣）

DAGGERS = OrderedDict([
    ("slot", "weapon"), ("generation", 2), ("series", "7階のレア装備"), ("rarity", "epic"), ("required_level", 30),
    ("jobs", ["amemaru:rogue"]), ("stats", OrderedDict([("attack", 32), ("crit", 6)])), ("durability", 600), ("binding", "on_equip"),
    ("effects", [OrderedDict([("type", "double_hit"), ("chance", 15), ("percent", 100)])]),
    ("rare", True),
    ("repair", [{"item": "amemaru:mirror_ice", "count": 2}, {"item": "amemaru:prism_shard", "count": 1}]),
    ("description", "鏡像獣リフレクタの、鏡のたてがみから削り出した二本ひと組の短剣。刃に映る自分の姿が、ほんの一瞬遅れて動く。"
                    "斬りつけると、ときどき刃の中の写しも、同じ所を斬る。"),
])


# ================================================================ 物語（7日目）

def story():
    out = OrderedDict()
    out["enter"] = OrderedDict([
        ("_comment", "初めて7階に入ったとき（本人だけ）。鏡のような氷の木々の森。氷に映る自分の姿が、ときどき別の仲間の姿になる"
                     "（Phase 0-A 9章の7階の地形・環境）。7日目：リリアがプレイヤーを忘れる（物語フラグ ch2/lilia_forgot。サーバー全体。"
                     "知らせは LiliaMemoryService が出す）。台詞は【補完】"),
        ("title", "氷の森林"),
        ("trigger", OrderedDict([("type", "amemaru:floor_enter"), ("floor", FLOOR)])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:block.amethyst_block.resonate"), ("volume", 0.9), ("pitch", 0.6)]),
            OrderedDict([("type", "amemaru:title"), ("title", "第2章　凍てついた記憶"), ("subtitle", "7階　氷の森林"), ("ticks", 110)]),
            sub("氷の木々が、鏡のように光っている。枝のすきまに、自分の姿が映っていた……。", 120),
            sub("……いや。映っているのは、隣を歩く仲間の顔だ。自分の顔は、どこにも映っていない。", 120),
            sub("森の奥から、冷たい風が吹いてくる。誰かの名前を呼ぶ声が、途中でかすれて消えた。", 120, False),
            flags("amemaru:ch2/lilia_forgot", server=True),
        ]),
    ])
    out["mirror_grove"] = OrderedDict([
        ("_comment", "入口の先の鏡の木立（本人だけ。一人一度）。ロストの侵食：映り込みが間違っている・名前が読めなくなる"
                     "（Phase 0-A 9章の7階）【補完】"),
        ("title", "鏡の木立"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "mirror_grove")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/mirror_grove_seen"])])),
        ("steps", [
            sub("どの氷の幹にも、誰かが映っている。ひとつ、またひとつ。", 110),
            sub("……その中のひとりが、こちらを見て笑った。映っているはずの誰かは、隣にいないのに。", 120),
            sub("名前を呼ぼうとして、気づく。――その人の名前が、読めない。", 120, False),
            flags("amemaru:story/mirror_grove_seen"),
        ]),
    ])
    out["watch_station"] = OrderedDict([
        ("_comment", "森林監視所の跡（本人だけ。一人一度）。古代記録「森林監視所の記録」の書見台がある【補完】"),
        ("title", "森林監視所の跡"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "watch_station")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/watch_station_seen"])])),
        ("steps", [
            sub("崩れた見張り所。昔、この森を見張っていた人たちがいたらしい。", 110),
            sub("書見台に、古い記録が残っている。……読めば、この森で何が起きたのか分かるかもしれない。", 120, False),
            flags("amemaru:story/watch_station_seen"),
        ]),
    ])
    out["passphrase_door"] = OrderedDict([
        ("_comment", "合言葉の扉の前（本人だけ。一人一度）。パーティ全員が同じ合言葉をチャットに打つと開く（Phase 0-A 9章の7階）【補完】"),
        ("title", "合言葉の扉"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "passphrase_door")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/passphrase_door_seen"])])),
        ("steps", [
            sub("凍った扉の碑に、こう刻まれている。「名を呼べぬときは、同じ言葉を」", 120),
            sub("扉の前で、パーティの全員が、同じ合言葉をチャットに打とう（20秒のうちに）。", 120),
            sub("……互いにしか分からない言葉を。名前は、侵食に最初に奪われるのだから。", 120, False),
            flags("amemaru:story/passphrase_door_seen"),
        ]),
    ])
    out["first_kill"] = OrderedDict([
        ("_comment", "7階のボスの初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。台詞は【補完】"),
        ("title", "鏡像獣リフレクタ"),
        ("trigger", OrderedDict([("type", "amemaru:floor_first_kill"), ("floor", FLOOR)])),
        ("scope", "party"),
        ("once", True),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:hud"), ("hide", True)]),
            OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:block.glass.break"), ("volume", 1.0), ("pitch", 0.6)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 30)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:title"), ("title", "氷の森林　踏破"), ("subtitle", "第2章　凍てついた記憶"), ("ticks", 110)]),
            sub("鏡像獣の体に、ひびが走った。鏡のたてがみが、ひとつ、またひとつと割れていく。", 130),
            sub("割れた鏡のかけらの一枚一枚に、仲間たちの顔が映っている。……名前も、ちゃんと読める。", 130),
            sub("隣にいるのが誰か、もう間違えない。", 100),
            sub("街では、リリアが待っている。「はじめまして」と言われても――「ただいま」を言いに帰ろう。", 140),
            OrderedDict([("type", "amemaru:hud"), ("hide", False)]),
        ]),
    ])
    return out


# ================================================================ リリアの会話（7日目：プレイヤーを忘れる）

def greet_choices():
    return [
        choice("ただいま", "g1", [{"type": "amemaru:greet_lilia"}]),
        choice("ギルドの窓口を見たい", actions=[{"type": "amemaru:open_guild", "tab": "quests"}]),
        choice("職業を変えたい", actions=[{"type": "amemaru:open_job_screen"}]),
        choice("またね"),
    ]


FORGOT = OrderedDict([
    # (ファイル, 段階の説明, 立っている物語フラグ, 立っていない物語フラグ, 表情, 1つ目, 2つ目, ただいまの返事1, 2)
    ("forgot", ("まだ何も思い出していない", [], ["amemaru:ch2/lilia_name"], "worried",
                "いらっしゃいませ。冒険者ギルドへようこそ。……はじめまして、ですよね？",
                "ごめんなさい。登録の記録はあるのに、お顔とお名前が、どうしても出てこなくて……",
                "……ただいま、ですか？ ふしぎ。そう言われると、胸のあたりが少しだけ温かくなるんです",
                "……おかえりなさい、で、いいんでしょうか。また明日も、来てくださいね")),
    ("forgot_name", ("名前をうっすら（25%）", ["amemaru:ch2/lilia_name"], ["amemaru:ch2/lilia_face"], "surprised",
                     "いらっしゃいませ。……{player}さん、ですよね？ お名前だけ、なぜか知っている気がするんです",
                     "冒険者さんたちが、毎日声をかけてくれて……少しずつ、霧が晴れていくみたいです",
                     "……ただいま。……ええ、その言葉、知っています。毎日、誰かに言ってもらっていた気がする",
                     "おかえりなさい、{player}さん。……うん、ちゃんと言えました")),
    ("forgot_face", ("顔（50%）", ["amemaru:ch2/lilia_face"], ["amemaru:ch2/lilia_more"], "smile",
                     "{player}さん！ ……そのお顔、ちゃんと覚えています。塔から帰ってくるたびに、ここで……",
                     "あと少しなんです。思い出せないところが、まだ白く抜けていて",
                     "ただいま、ですね。……ふふ、なんだか、わたしの方が言いたかったみたい",
                     "おかえりなさい！ ……あと少しだけ、待っていてくださいね")),
    ("forgot_more", ("さらに（75%）", ["amemaru:ch2/lilia_more"], [], "smile",
                     "おかえりなさい、{player}さん！ ……もう、ほとんど思い出しました",
                     "白い霧の向こうに、まだ何か大事なことが残っている気がして。……塔の、もっと上のほうに",
                     "ただいま。……ありがとうございます。みんなの声が、わたしをここに引き戻してくれたんです",
                     "全部思い出せたら、いちばんに言いますね。――おかえりなさい、って")),
])


def dialogues():
    out = OrderedDict()
    for name, (stage, has, lacks, face, first, second, reply1, reply2) in FORGOT.items():
        cond = OrderedDict([("opening", 2), ("has_job", True),
                            ("flags_all", ["amemaru:ch2/lilia_forgot"] + has),
                            ("flags_none", lacks + ["amemaru:ch2/queen_down"])])
        out[("lilia", name)] = OrderedDict([
            ("_comment", "7日目から（物語フラグ ch2/lilia_forgot。7階に誰かが初めて入ったとき）、リリアはプレイヤーを忘れている"
                         "（Phase 0-A 13章・12章「7日目 プレイヤーを忘れる（『はじめまして……ですよね？』）→ プレイヤーの『ただいま』で"
                         "少しずつ思い出す」）。段階：%s。「ただいま」は1人1日1回、サーバー全体で数える（LiliaMemoryService）。ギルドの窓口と"
                         "職業の画面は、忘れていても開ける。10日目のボス撃破（ch2/queen_down）で完全に戻る。台詞は【補完】" % stage),
            ("npc", "amemaru:lilia"),
            ("priority", 35),
            ("conditions", cond),
            ("start", "s1"),
            ("nodes", OrderedDict([
                ("s1", OrderedDict([("expression", face), ("text", first), ("next", "s2")])),
                ("s2", OrderedDict([("text", second), ("choices", greet_choices())])),
                ("g1", OrderedDict([("expression", "smile"), ("text", reply1), ("next", "g2")])),
                ("g2", OrderedDict([("text", reply2)])),
            ])),
        ])
    return out


# ================================================================ 素材・結び紐

MATERIALS = OrderedDict([
    ("mirror_ice", ("鏡氷", "uncommon",
                    "写し身が砕けたあとに残る、鏡のように磨かれた氷のかけら。のぞきこむと、ときどき自分ではない誰かが映る。"
                    "7階の装備とアクセサリーの材料になる。")),
    ("ice_antler", ("氷角", "common", "氷角鹿の、透きとおった角のかけら。枝分かれの先が、振ると小さく鈴のように鳴る。7階のアクセサリーの材料になる。")),
    ("icicle_wing", ("つらら蝙蝠の翼", "common",
                     "夜の色の薄い膜に、細いつららが下がったままの翼。触れると、指先がしびれるほど冷たい。7階のアクセサリーの材料になる。")),
    ("blizzard_core", ("吹雪の芯", "uncommon",
                       "吹雪の精が消えたあとに残る、小さな白い渦。手のひらの上で、まだ雪を巻いている。7階のアクセサリーの材料になる。")),
    ("prism_shard", ("プリズムの欠片", "rare",
                     "光を七つの色に分ける、透きとおった結晶のかけら。鏡像獣の背に生えていたもの。7階の名のある魔物とボスが持つ。")),
])

KNOT_CORD_DESC = ("赤と白の糸をより合わせた、小さな結び目の紐。ほどけかけたつながりを、もう一度結ぶ。ロスト状態（薄れなど）を"
                  "ほどく。薬屋で買える。")


# ================================================================ 言葉

LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.ordo"] = OrderedDict([
    ("entity.amemaru.mimic", "写し身"), ("entity.amemaru.ice_stag", "氷角鹿"), ("entity.amemaru.icicle_bat", "つらら蝙蝠"),
    ("entity.amemaru.blizzard_spirit", "吹雪の精"), ("entity.amemaru.reflecta", "鏡像獣リフレクタ"),
])
LANG_AFTER["item.amemaru.ordo_spawn_egg"] = OrderedDict([
    ("item.amemaru.mimic_spawn_egg", "写し身のスポーンエッグ"), ("item.amemaru.ice_stag_spawn_egg", "氷角鹿のスポーンエッグ"),
    ("item.amemaru.icicle_bat_spawn_egg", "つらら蝙蝠のスポーンエッグ"), ("item.amemaru.blizzard_spirit_spawn_egg", "吹雪の精のスポーンエッグ"),
    ("item.amemaru.reflecta_spawn_egg", "鏡像獣リフレクタのスポーンエッグ"),
])
LANG_AFTER["boss.amemaru.ordo.frozen"] = OrderedDict([
    ("boss.amemaru.intro.reflecta", "第7階層「氷の森林」　鏡の湖"),
    ("cast.amemaru.reflecta.claw", "鏡爪（前の扇）"), ("cast.amemaru.reflecta.shards", "鏡片の雨（線の上）"),
    ("cast.amemaru.reflecta.prism", "屈折の光（見ている方の細い扇・当たると「薄れ」）"),
    ("cast.amemaru.reflecta.scatter", "乱反射（みんなの足もとに円）"), ("cast.amemaru.reflecta.mirror", "鏡写し（写し身を作る）"),
    ("status.amemaru.reflecta.ward", "鏡の守り（受けるダメージが減る）― 写し身があと %2$s 体。名前の色とHPバーで見分けて倒そう"),
    ("status.amemaru.reflecta.open", "鏡の守りが割れた！ 攻めどき（あと %1$s 秒）"),
    ("boss.amemaru.reflecta.line.start", "湖の氷が、鏡のように光った。そこに映っていたのは――あなたたち自身だった"),
    ("boss.amemaru.reflecta.line.phase2", "鏡像獣のたてがみが、ざわりと逆立った。湖じゅうの氷に、あなたたちの姿が映る"),
    ("boss.amemaru.reflecta.line.phase3", "「……ダレ……？」鏡の中の声が、あなたたちの名前を呼ぼうとして、かすれた"),
    ("boss.amemaru.reflecta.line.mirror", "鏡像獣のたてがみが広がり、湖に、あなたたちの写し身が立ち上がる！"),
    ("boss.amemaru.reflecta.line.broken", "写し身が砕けた！ 鏡像獣の鏡の守りが、音を立てて割れる"),
    ("boss.amemaru.reflecta.line.return", "残った写し身が、鏡像獣の体へ溶けて戻った……（鏡像獣のHPが少し戻った）"),
    ("boss.amemaru.reflecta.tip.start", "鏡像獣リフレクタは、ときどき仲間の写し身を作る。写し身の名前は氷の色で、叩くと頭の上にHPバーが出る"),
    ("boss.amemaru.reflecta.tip.shards", "地面に長い線が出たら、鏡片の雨。線の外へ（当たると凍える）"),
    ("boss.amemaru.reflecta.tip.prism", "屈折の光：鏡像獣が見ている方の細い扇。当たると、ロスト状態「薄れ」（仲間の名前が？？？に見える）"),
    ("boss.amemaru.reflecta.tip.scatter", "乱反射：みんなの足もとに赤い円。円の外へ動こう"),
    ("boss.amemaru.reflecta.tip.mirror", "写し身が出た！ 写し身が残っている間、鏡像獣は硬い。すべて倒すと鏡の守りが割れる。"
                                         "薄れは、仲間のそばでしゃがみ続ける「結び直し」でほどける"),
])
LANG_AFTER["message.amemaru.seal.statues"] = OrderedDict([
    ("message.amemaru.seal.lights", "凍った扉は開かない。鏡の迷路の光を、すべて受け石へ届けよう（%s/%s）"),
    ("message.amemaru.seal.passphrase", "凍った扉は開かない。扉の前で、パーティの全員で同じ合言葉をチャットに打とう"),
])
LANG_AFTER["message.amemaru.pokka.remembered"] = OrderedDict([
    ("message.amemaru.light.locked", "この鏡の迷路は、もう解けている"),
    ("message.amemaru.light.reached", "%s：光は受け石まで届いている（%sマス）"),
    ("message.amemaru.light.path", "%s：光の道を見せた。鏡を右クリックで回して、光を受け石へ導こう（光は今 %sマス進む）"),
    ("message.amemaru.light.solved", "%sの光が、受け石に届いた！ 氷の門が開いた（%s/%s）"),
    ("message.amemaru.light.all", "%s：鏡の迷路の光が、すべて届いた"),
    ("message.amemaru.passphrase.waiting", "合言葉：%s/%s人が同じ言葉を言った（%s秒のうちに、全員で同じ言葉を）"),
    ("message.amemaru.passphrase.opened", "合言葉「%s」が、全員でそろった。凍った扉が、あなたたちを通す"),
    ("message.amemaru.lost.lost_fade", "ロスト状態「薄れ」：仲間の名前が読めない（？？？）"),
    ("message.amemaru.lost.retie_hint", "ロスト状態は、仲間のそばでしゃがみ続けると（3秒）「結び直し」でほどける。ヒーラーの浄化や「結び紐」でもほどける"),
    ("message.amemaru.lost.retying", "%sと結び直している……%s%%"),
    ("message.amemaru.lost.retied", "%sと結び直した。ロスト状態がほどけた"),
    ("message.amemaru.knot_cord.freed_by", "%sが、結び紐でロスト状態をほどいてくれた"),
    ("message.amemaru.knot_cord.nothing", "まわりに、ロスト状態の人がいない（結び紐は減らない）"),
    ("message.amemaru.knot_cord.used", "結び紐で、%s人のロスト状態をほどいた"),
    ("message.amemaru.lilia.forgot", "ギルドの受付のリリアが、冒険者たちのことを忘れてしまった……"),
    ("message.amemaru.lilia.forgot_detail", "「はじめまして……ですよね？」 リリアに「ただいま」と声をかけよう（1日1回。街のみんなの声が集まるほど、"
                                            "少しずつ思い出す）"),
    ("message.amemaru.lilia.greeted", "リリアに「ただいま」と声をかけた。（みんなの声で、リリアの記憶が戻ってきている：%s%%）"),
    ("message.amemaru.lilia.greeted_today", "今日は、もうリリアに「ただいま」を言った。また明日、声をかけよう"),
    ("message.amemaru.lilia.stage1", "ギルドの受付で、リリアが冒険者たちの名前を、少しずつ思い出し始めた"),
    ("message.amemaru.lilia.stage2", "リリアが、冒険者たちの顔を思い出した。……あと少し"),
    ("message.amemaru.lilia.stage3", "リリア「もう少しで、全部思い出せそうです」――みんなの「ただいま」が、届いている"),
])
LANG_AFTER["commands.amemaru.floor.woods_tied"] = OrderedDict([
    ("commands.amemaru.floor.lights_reset", "鏡の迷路を、はじめの向きに戻しました"),
    ("commands.amemaru.floor.no_lights", "%s階には、鏡の迷路がありません"),
    ("commands.amemaru.floor.lights_solved", "鏡の迷路を %s つ解きました"),
    ("commands.amemaru.floor.passphrase_done", "%s階の合言葉を、%s人にそろえました"),
    ("commands.amemaru.floor.passphrase_reset", "%s階の合言葉を、%s人から消しました"),
])
LANG_AFTER["block.amemaru.village_statue"] = OrderedDict([
    ("block.amemaru.light_spring", "光の泉"), ("block.amemaru.ice_mirror", "氷の鏡"), ("block.amemaru.light_target", "光の受け石"),
])
LANG_AFTER["effect.amemaru.frozen"] = OrderedDict([
    ("effect.amemaru.lost_fade", "薄れ"),
])
LANG_AFTER["effect.amemaru.cold_resist"] = OrderedDict([
    ("effect.amemaru.double_hit", "攻撃が当たると%s%%の確率で、鏡の写しがもう一度当たる（与えたダメージの%s%%）"),
    ("effect.amemaru.lost_resist", "ロスト耐性 +%s%%（ロスト状態の時間が短くなる）"),
])
LANG_AFTER["item.amemaru.antidote.desc"] = OrderedDict([
    ("item.amemaru.knot_cord", "結び紐"),
    ("item.amemaru.knot_cord.desc", KNOT_CORD_DESC),
    ("tooltip.amemaru.knot_cord", "右クリック：自分と、まわり%sブロックの仲間のロスト状態をほどく（10秒に1回）"),
])
LANG_AFTER["item.amemaru.mayor_fur_boots"] = OrderedDict([
    ("item.amemaru.mirror_ice_daggers", "鏡氷の短剣"),
])


def mat_lang():
    out = OrderedDict()
    for mid, (name, _r, desc) in MATERIALS.items():
        out["item.amemaru." + mid] = name
        out["item.amemaru." + mid + ".desc"] = desc
    return out


def merge_lang():
    LANG_AFTER["item.amemaru.ice_feather.desc"] = mat_lang()
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
    mats = [m for m in d["materials"] if m["id"] not in MATERIALS]
    for mid, (_name, rarity, _desc) in MATERIALS.items():
        mats.append(OrderedDict([("id", mid), ("rarity", rarity)]))
    d["materials"] = mats
    weapons = [w for w in d["weapons"] if w["id"] != "mirror_ice_daggers"]
    weapons.append(OrderedDict([("id", "mirror_ice_daggers"), ("kind", "daggers")]))
    d["weapons"] = weapons
    tools = [t for t in d["tools"] if t["id"] != "knot_cord"]
    tools.append(OrderedDict([("id", "knot_cord"), ("kind", "knot_cord"), ("stack", 16)]))
    d["tools"] = tools
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def item_models():
    for mid in list(MATERIALS) + ["knot_cord"]:
        write(os.path.join(ASSETS, "models/item", mid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + mid}})
    for egg in ("mimic", "ice_stag", "icicle_bat", "blizzard_spirit", "reflecta"):
        write(os.path.join(ASSETS, "models/item", egg + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor7_iceforest.json"), chest_table())
    for name, data in story().items():
        write(os.path.join(DEFS, "story_events/floor7", name + ".json"), data)
    for (npc, name), data in dialogues().items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    write(os.path.join(DEFS, "equipment", "mirror_ice_daggers.json"), DAGGERS)
    merge_item_types()
    merge_lang()
    item_models()
    print("enemies", len(ENEMIES), "story", len(story()), "dialogues", len(dialogues()))


if __name__ == "__main__":
    main()
