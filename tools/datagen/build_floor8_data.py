"""8階「沈黙の湖」のデータ（敵・落とし物・宝箱・物語・セレナの会話・クエスト・ボスのレア・素材・石板・言葉）を書き出す（11月25日）。

  python3 tools/datagen/build_floor8_data.py      # tools/mapgen/build_floor8.py で floors/floor8.json を作ってから

Phase 0-A 9章の8階（確定）：音のない凍った湖。足音も環境音も消えている。氷の下に巨大な影と、沈んだ祠が見える。雑魚は無音の大魚（氷の
下から襲う。割れ目が予兆）・声喰い（近くにいると効果音が消える）・凍て鶴・氷霧の亡霊。ボスは声なき湖主ムーネ（「静寂」の間は効果音・
予兆表示・パーティHUDが消え、ボイスチャットの声かけだけが頼りになる）。ギミックは氷の割れ目（同じ場所に集まりすぎると割れる）と、湖の
まわりの鐘（正しい順番で鳴らすと音が戻り、祠への道が開く）。古代記録「湖底の祠の石板」（records/f08_lake_shrine.json）。物語は8日目：
この石板を持ち帰ると、セレナが「ロストで記憶が消える仕組み」を伝える（フラグ serena_explains。13章「記憶は消滅しない。糸が切れて
届かなくなるだけで、糸を結び直せば戻る。ただし長く切れたままの糸は結び直しにくい」・セレナの伏線「8日目の説明が妙に詳しい」）。
ロストの侵食：音そのものが消えている。ロスト状態（11章の表）：断線（パーティHUDから味方のHPが消える）・静寂（効果音と予兆の音が消える）。
ボスのレア（20章）：湖主の髭弓（弓：静寂状態でも予兆の音が自分にだけ聞こえる）4%。21章の第2章の一覧の「凍て鶴の腕輪」は、
tools/datagen/build_floor_accessories.py。数値・台詞・素材・名のある魔物は【補完】。
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_floor4_data import DATA, DEFS, LANG, REPO, pool, st, write  # noqa: E402
from build_floor6_data import choice, flags, sub  # noqa: E402,F401

FLOOR = 8
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
PEARL = [{"item": "amemaru:lake_pearl", "chance": 1.0}]

ENEMIES = OrderedDict()
ENEMIES["silent_fish"] = enemy(
    "無音の大魚（8階の雑魚。Phase 0-A 9章の8階「氷の下から襲う。割れ目が予兆」）。ふだんは氷の下を泳ぎ、叩いても届かない（影だけが"
    "見える。enemy/lake/SilentFish.java）。氷の上の人の真下へ来ると、足もとに氷のひびの印（予兆。lunge.windup 秒）を出し、氷を割って"
    "跳び上がる（lunge。半径 radius。割れた所は hole_seconds 秒、水の穴）。そのあと beached 秒だけ氷の上でもがき、この間だけ叩ける。"
    "かみつく（bite）。氷の下からでも、足音で気づく（見えていなくてよい）。数値は【補完】",
    "無音の大魚", 33,
    "白い鱗の、舟ほどもある湖の魚。ひれを動かしても水音ひとつ立てない。氷の上を歩く者の足音を、氷の下から聞いている。",
    st(1500, 236, 16, 0.25, 24, 14), 72,
    OrderedDict([("lunge", {"power": 1.5, "windup": 1.3, "cooldown": 7.0, "range": 18.0, "radius": 2.2, "hole": 1.6, "hole_seconds": 8.0,
                            "lift": 0.55, "beached": 4.0, "swim": 0.22}),
                 ("bite", {"power": 1.0, "windup": 0.5, "cooldown": 1.6, "range": 2.6})]),
    {},
    "silent_fish", FRUITS + PEARL)
ENEMIES["voice_eater"] = enemy(
    "声喰い（8階の雑魚。Phase 0-A 9章の8階「近くにいると効果音が消える」）。宙に浮き、声の礫を吐く（needle。1階の結晶蜂と同じ動き）。"
    "そば（behavior.silence_range）の人は、ロスト状態「静寂」（効果音と予兆の音が消える。silence_seconds 秒。そばにいる間とぎれない）。"
    "数値は【補完】",
    "声喰い", 32,
    "大きな口だけの、ふわふわと浮く白い影。のどの奥が、これまでに食べた声で青く光っている。近くでは、自分の足音さえ聞こえなくなる。",
    st(1500, 214, 10, 0.24, 24, 14), 66,
    {"needle": {"power": 0.9, "windup": 0.8, "cooldown": 3.0, "range": 12, "speed": 0.9}},
    {"keep_distance": 5, "hover_height": 1.5, "silence_range": 7.0, "silence_seconds": 3.0},
    "voice_eater", FRUITS + PEARL)
ENEMIES["voice_eater_elder"] = enemy(
    "千声喰い（8階の名のある魔物。東の声の洞の宝箱の守り手）。声喰いの長。静寂の届く所が広く（silence_range）、ときどき大きく息を"
    "吸いこんで（inhale。地面の円の予兆のあと seconds 秒、range の中の人を引き寄せ、最後にかみつく。かまれると静寂 silence_seconds 秒）。"
    "数値は【補完】",
    "千声喰い", 36,
    "千の声を食べたという、大きな声喰い。のどの奥では、食べた声がいまも小さく響きあっている。千声喰いの洞では、仲間を呼ぶ声も届かない。",
    st(20000, 236, 14, 0.22, 28, 16), 560,
    {"needle": {"power": 1.0, "windup": 0.8, "cooldown": 2.6, "range": 14, "speed": 1.0},
     "inhale": {"power": 1.4, "windup": 1.6, "cooldown": 14.0, "range": 10.0, "seconds": 2.0, "pull": 0.12, "silence_seconds": 8.0}},
    {"keep_distance": 6, "hover_height": 2.0, "silence_range": 12.0, "silence_seconds": 3.0, "scale": 1.6},
    "voice_eater", [{"item": "amemaru:lake_pearl", "chance": 40.0}, {"item": "amemaru:thousand_voice_stone", "chance": 5.0},
                    {"item": "amemaru:crystal_fruit_violet", "chance": 6.0}], model="amemaru:voice_eater")
ENEMIES["frost_crane"] = enemy(
    "凍て鶴（8階の雑魚。Phase 0-A 9章の8階）。くちばしで突き（bite）、低く翼を広げて滑るように突進し（charge。1階の欠け牙の狼と同じ動き）、"
    "翼を振って前の扇に吹雪を叩きつける（gust。押し返し、凍える）。くちばしに当たると凍える（behavior.chill_stacks 段）。数値は【補完】",
    "凍て鶴", 32,
    "白い羽に黒い風切り、頭には霜の冠。凍った湖の岸に一本足で立ち、近づく者を、音もなく翼で払う。鳴くはずの喉からは、何も聞こえない。",
    st(2200, 228, 18, 0.28, 22, 12), 70,
    {"bite": {"power": 1.05, "windup": 0.6, "cooldown": 1.6, "range": 2.6, "angle": 80},
     "charge": {"power": 1.6, "windup": 1.1, "cooldown": 8.0, "range": 12.0, "min_range": 4.0, "speed": 0.9, "width": 1.2, "recover": 1.2},
     "gust": {"power": 1.2, "windup": 1.1, "cooldown": 9.0, "range": 6.0, "angle": 100, "push": 1.0, "chill": 1}},
    {"chill_stacks": 1, "chill_seconds": 4.0},
    "frost_crane", FRUITS + PEARL)
ENEMIES["frost_crane_lord"] = enemy(
    "凍て鶴の長「霜冠」（8階の名のある魔物。西の葦原の宝箱の守り手）。大きく、吹雪が強く広い（gust。押し返しが強く、凍えが2つ）。"
    "数値は【補完】",
    "霜冠", 36,
    "雪のように白い、大きな凍て鶴。頭の霜の冠は、朝の光を受けると七色に光る。霜冠が舞うと、葦原じゅうの凍った葦が、音もなく倒れる。",
    st(22000, 242, 22, 0.3, 28, 16), 580,
    {"bite": {"power": 1.25, "windup": 0.6, "cooldown": 1.5, "range": 3.2, "angle": 90},
     "charge": {"power": 2.0, "windup": 1.0, "cooldown": 6.0, "range": 16.0, "min_range": 5.0, "speed": 1.05, "width": 2.0, "recover": 1.0},
     "gust": {"power": 1.4, "windup": 1.0, "cooldown": 6.0, "range": 8.0, "angle": 120, "push": 1.6, "chill": 2}},
    {"chill_stacks": 2, "chill_seconds": 5.0, "scale": 1.5},
    "frost_crane", [{"item": "amemaru:lake_pearl", "chance": 40.0}, {"item": "amemaru:frost_crown_plume", "chance": 5.0},
                    {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}], model="amemaru:frost_crane")
ENEMIES["fog_wraith"] = enemy(
    "氷霧の亡霊（8階の雑魚。Phase 0-A 9章の8階）。宙に浮き、冷たい手で触れる（claw）。触れられると、ロスト状態「断線」（パーティの欄から"
    "仲間のHPが消える。behavior.sever_seconds 秒。13章の表「主な原因は8階」）。ときどき霧に溶けて、狙った人の後ろへ移る（blink）。"
    "数値は【補完】",
    "氷霧の亡霊", 33,
    "霧の衣をまとった、足のない亡霊。湖の霧にまぎれて近づき、仲間とのあいだに、冷たい手を差しこんでくる。触れられた所から、つながりが白くほつれる。",
    st(1700, 224, 10, 0.26, 22, 14), 68,
    {"claw": {"power": 1.1, "windup": 0.7, "cooldown": 1.8, "range": 2.4, "angle": 90},
     "blink": {"power": 0.0, "windup": 0.8, "cooldown": 9.0, "range": 16.0, "min_range": 6.0}},
    {"hover_height": 0.5, "keep_distance": 2, "sever_seconds": 10.0},
    "fog_wraith", FRUITS + PEARL, lost=True)


def mune_rare():
    return [{"item": "amemaru:lake_lord_whisker_bow", "chance": 4, "rarity": "epic"},
            {"item": "amemaru:lake_pearl", "chance": 100, "count": 2, "pity": False},
            {"item": "amemaru:crystal_fruit_white", "chance": 20, "pity": False},
            {"item": "amemaru:crystal_fruit_azure", "chance": 15, "pity": False},
            {"item": "amemaru:crystal_fruit_violet", "chance": 15, "pity": False}]


ENEMIES["mune"] = enemy(
    "8階のボス、声なき湖主ムーネ（Phase 0-A 9章の8階：「静寂」の間は効果音・予兆表示・パーティHUDが消え、ボイスチャットの声かけだけが"
    "頼りになる）。氷の下にいる間は叩けない（enemy/boss/Mune.java）。技：rise（氷の下から、狙った人の足もとに氷のひびの印。氷を割って"
    "跳ね上げる。surface_after 回で、最後の穴から氷の上へ出て surface 秒叩ける。第3段階は surface_after_late 回・全員を狙う）・sweep"
    "（氷の上で、前の扇）・tail（氷の上で、まわりの円。crack_radius の薄氷に crack 段のひび）・veil（静寂の帳：参加者全員を静寂の強さ1に"
    "seconds 秒。違う鳴り石2つを window 秒のうちに鳴らすと破れ、daze 秒動けず、受けるダメージ broken 倍。first 秒から）・wave"
    "（第2段階から。湖のまん中から氷の下を波が広がる（speed）。足が氷についていると断線 sever_seconds 秒）。HPは2人のときの基準"
    "（ひとりでも2人ぶん）。叩ける間が半分ほどなので、7階より少ない。数値は【補完】（tools/balance/estimate.py の目安）",
    "声なき湖主ムーネ", 39,
    "沈黙の湖の主。白い鱗の巨大な魚で、口のまわりに光る長いひげをたくわえている。湖が音を失ったその日から、氷の下を、ただ黙って"
    "泳ぎつづけている。――湖主が口を開けても、声は出ない。かわりに、あたりの音がすべて消える。",
    st(64000, 226, 22, 0.0, 64, 48), 3000,
    OrderedDict([
        ("rise", {"power": 1.4, "windup": 1.6, "anim_windup": 1.4, "cooldown": 6.0, "range": 2.8, "hole": 2.0, "hole_seconds": 12.0,
                  "lift": 0.7, "swim": 0.2, "surface_after": 2, "surface_after_late": 3, "surface": 10.0}),
        ("sweep", {"power": 1.3, "windup": 1.3, "anim_windup": 1.1, "cooldown": 5.0, "range": 9.0, "angle": 120, "push": 1.0}),
        ("tail", {"power": 1.5, "windup": 1.5, "anim_windup": 1.3, "cooldown": 8.0, "range": 6.5, "crack_radius": 9.0, "crack": 2}),
        ("veil", {"windup": 2.0, "anim_windup": 2.0, "cooldown": 70.0, "seconds": 20.0, "first": 25.0, "window": 3.0, "daze": 8.0,
                  "broken": 1.3, "late_scale": 0.7}),
        ("wave", {"power": 1.2, "windup": 1.8, "anim_windup": 1.6, "cooldown": 30.0, "range": 46.0, "speed": 0.8, "sever_seconds": 15.0,
                  "first": 10.0}),
    ]),
    {},
    "mune", mune_rare(), pity=True)

LOOT = {
    "silent_fish": [pool("amemaru:fish_scale", 0.6), pool("amemaru:silent_ice", 0.25)],
    "voice_eater": [pool("amemaru:voice_stone", 0.55), pool("amemaru:silent_ice", 0.2)],
    "frost_crane": [pool("amemaru:crane_plume", 0.6), pool("amemaru:ice_feather", 0.25)],
    "fog_wraith": [pool("amemaru:mist_thread", 0.6), pool("amemaru:silent_ice", 0.2), pool("amemaru:echo_fragment", 0.1)],
    "mune": [pool("amemaru:silent_ice", None, 4, 6), pool("amemaru:lake_pearl", None, 1, 2), pool("amemaru:fish_scale", None, 2, 4),
             pool("amemaru:voice_stone", 0.8, 1, 2), pool("amemaru:knot_cord", 0.6, 1, 2), pool("amemaru:clear_echo", 0.4)],
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
             "entries": [e("amemaru:silent_ice", 14, 1, 3), e("amemaru:fish_scale", 13, 1, 3), e("amemaru:voice_stone", 11, 1, 2),
                         e("amemaru:crane_plume", 12, 1, 3), e("amemaru:mist_thread", 12, 1, 3), e("amemaru:frost_silver", 9, 1, 2),
                         e("amemaru:mirror_ice", 7, 1, 2), e("amemaru:knot_cord", 9, 1, 2), e("amemaru:polishing_powder", 8, 1, 3),
                         e("minecraft:bread", 6, 1, 3), e("amemaru:echo_fragment", 5, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:clear_echo"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_white", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_violet", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.14}]},
        ],
    }


# ================================================================ ボスのレア（20章：湖主の髭弓）

BOW = OrderedDict([
    ("slot", "weapon"), ("generation", 2), ("series", "8階のレア装備"), ("rarity", "epic"), ("required_level", 34),
    ("jobs", ["amemaru:archer"]), ("stats", OrderedDict([("attack", 35), ("crit", 7)])), ("durability", 620), ("binding", "on_equip"),
    ("effects", [OrderedDict([("type", "warning_ear"), ("percent", 100)])]),
    ("rare", True),
    ("repair", [{"item": "amemaru:silent_ice", "count": 2}, {"item": "amemaru:lake_pearl", "count": 1}]),
    ("description", "声なき湖主ムーネの、光る長いひげを弦に張った弓。湖の音がすべて消えても、この弦だけは、危ないものが来る前に、"
                    "持ち主の耳もとで小さく鳴る。"),
])


# ================================================================ 物語（8日目）

def story():
    out = OrderedDict()
    out["enter"] = OrderedDict([
        ("_comment", "初めて8階に入ったとき（本人だけ）。音のない凍った湖。足音も環境音も消えている。氷の下に巨大な影と、沈んだ祠が見える"
                     "（Phase 0-A 9章の8階の地形・環境）。8日目。台詞は【補完】"),
        ("title", "沈黙の湖"),
        ("trigger", OrderedDict([("type", "amemaru:floor_enter"), ("floor", FLOOR)])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:title"), ("title", "第2章　凍てついた記憶"), ("subtitle", "8階　沈黙の湖"), ("ticks", 110)]),
            sub("……足音がしない。", 90),
            sub("雪を踏んでも、氷をたたいても、何も聞こえない。自分の息の音さえ、湖に吸いこまれていく。", 130),
            sub("凍った湖の下を、大きな影がゆっくりと横切った。", 110),
            sub("この湖では、声だけが頼りだ。仲間と声をかけ合おう（ボイスチャット）。", 120, False),
        ]),
    ])
    out["gorge"] = OrderedDict([
        ("_comment", "黒い裂け目の岸（本人だけ。一人一度）。沈んだ石橋は、入江の鐘を正しい順番で鳴らすと浮かぶ【補完】"),
        ("title", "黒い裂け目"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "gorge")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/lake_gorge_seen"])])),
        ("steps", [
            sub("黒い裂け目の底に、崩れた石橋が沈んでいる。", 100),
            sub("岸の碑に、こう刻まれている。「入江の鐘を、歌のとおりに鳴らせ。沈んだ道は、音に応えて浮かぶ」", 140, False),
            flags("amemaru:story/lake_gorge_seen"),
        ]),
    ])
    out["cove"] = OrderedDict([
        ("_comment", "南の岸の入江（本人だけ。一人一度）。入江の鐘3つと、入江の歌の碑【補完】"),
        ("title", "入江の鐘"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "cove")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/lake_cove_seen"])])),
        ("steps", [
            sub("小さな入江のまわりに、古い鐘が三つ。鳴らしても、音はしない。", 110),
            sub("入江の歌の碑に触れて、今日の歌（鐘を鳴らす順番）を確かめよう。歌は、毎日かわるらしい。", 130, False),
            flags("amemaru:story/lake_cove_seen"),
        ]),
    ])
    out["fishers"] = OrderedDict([
        ("_comment", "湖の上の、凍った釣り人たち（本人だけ。一人一度）。ロストの侵食：音そのものが消えている【補完】"),
        ("title", "凍った釣り人たち"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "fishers")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/lake_fishers_seen"])])),
        ("steps", [
            sub("湖の上に、釣り糸を垂らしたまま凍りついた人たちがいる。", 110),
            sub("呼び合う声が消えた湖で、隣にいる誰かに気づかないまま……何日も、何年も。", 130, False),
            flags("amemaru:story/lake_fishers_seen"),
        ]),
    ])
    out["shrine"] = OrderedDict([
        ("_comment", "湖底の祠（本人だけ。一人一度）。古代記録の石板と、歌い手の氷像【補完】"),
        ("title", "湖底の祠"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "shrine")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/lake_shrine_seen"])])),
        ("steps", [
            sub("湖の底の祠。天井の上で、湖の水が静かに揺れている。", 110),
            sub("奥の台に、古い石板がまつられている。手前には、歌うように口を開けたまま凍った人の像。", 130, False),
            flags("amemaru:story/lake_shrine_seen"),
        ]),
    ])
    out["tablet"] = OrderedDict([
        ("_comment", "湖底の祠の石板を手に取ったとき（本人だけ）。持ち帰ってセレナに見せるクエスト lake_tablet が始まる【補完】"),
        ("title", "湖底の祠の石板"),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            sub("石板を手に取った。冷たい。指先に、刻まれた文字のでこぼこが伝わる。", 110),
            sub("「沈黙は、争いを止めた。だが、歌も止めた」", 110),
            sub("……この言葉の意味を、セレナなら知っているかもしれない。街へ持ち帰ろう。", 120, False),
        ]),
    ])
    out["memory_singer"] = OrderedDict([
        ("_comment", "歌い手の氷像を、祠の灯籠の熱の結晶で溶かしたとき（6階と同じ記憶の氷像）。湖が音を失った日の場面【補完】"),
        ("title", "最後の歌い手"),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 20)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 40)]),
            sub("――氷が溶けると、歌い手の声が、一瞬だけ聞こえた。", 110),
            sub("「やめて。湖の両岸で、もう誰も傷つけ合わないで」", 120),
            sub("「歌が届かないなら……いっそ、声ごと止めてしまえばいい」　誰かが、湖の鐘を鳴らした。", 140),
            sub("湖じゅうの音が消えた。争う声も、泣く声も、歌も。歌い手は、口を開けたまま凍りついた。", 140, False),
            flags("amemaru:story/memory_singer"),
        ]),
    ])
    out["sound_returns"] = OrderedDict([
        ("_comment", "湖の鐘をすべて正しい順番で鳴らしたとき（BellService が、階にいる人に流す）。音が戻り、祠の氷のふたが開く【補完】"),
        ("title", "音が戻る"),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            sub("……鐘の音が、聞こえた。", 90),
            sub("氷のきしむ音。風の音。自分の足音。湖に、音が戻ってくる。", 120),
            sub("湖の北で、祠への氷のふたが静かに開いた。", 110, False),
        ]),
    ])
    out["first_kill"] = OrderedDict([
        ("_comment", "8階のボスの初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。台詞は【補完】"),
        ("title", "声なき湖主ムーネ"),
        ("trigger", OrderedDict([("type", "amemaru:floor_first_kill"), ("floor", FLOOR)])),
        ("scope", "party"),
        ("once", True),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:hud"), ("hide", True)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 30)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:title"), ("title", "沈黙の湖　踏破"), ("subtitle", "第2章　凍てついた記憶"), ("ticks", 110)]),
            sub("湖主の体が、ゆっくりと湖の底へ沈んでいく。光るひげが、最後に一度だけ揺れた。", 130),
            sub("その瞬間、湖じゅうに音があふれた。氷の割れる音、水の音、仲間の声。", 130),
            sub("沈黙は、争いを止めた。だが、歌も止めた。――湖主は、ずっとその両方を守っていたのかもしれない。", 150),
            sub("祠の石板を、セレナのところへ持ち帰ろう。", 110),
            OrderedDict([("type", "amemaru:hud"), ("hide", False)]),
        ]),
    ])
    return out


# ================================================================ セレナの会話（8日目：ロストで記憶が消える仕組み）

def dialogues():
    out = OrderedDict()
    out[("serena", "lake_tablet")] = OrderedDict([
        ("_comment", "8日目：湖底の祠の石板を持ち帰った人に、セレナが「ロストで記憶が消える仕組み」を伝える（Phase 0-A 9章の8階の物語・"
                     "12章のセレナ「8日目 仕組みを伝える」・確定設定「記憶は消滅しない。糸が切れて届かなくなるだけで、糸を結び直せば戻る。"
                     "ただし長く切れたままの糸は結び直しにくい。8日目にセレナが伝え、エンディングで記憶が戻る理由になる」・セレナの伏線"
                     "「8日目の説明が妙に詳しい」（秘蹟のことには触れない））。石板を預かり（クエスト lake_tablet の報告）、物語フラグ"
                     "ch2/serena_explains（サーバー全体）を立てる。台詞は【補完】"),
        ("npc", "amemaru:serena"),
        ("priority", 40),
        ("conditions", OrderedDict([("opening", 2), ("flags_all", ["amemaru:story/lake_tablet_found"]),
                                    ("flags_none", ["amemaru:story/serena_explained"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("text", "……その石板。湖の底から、持ち帰ってくださったのですね"), ("next", "s2")])),
            ("s2", OrderedDict([("text", "「沈黙は、争いを止めた。だが、歌も止めた」……古い祈りの言葉と、同じ形をしています"), ("next", "s3")])),
            ("s3", OrderedDict([("text", "{player}さん。ロストで記憶が消えるのは、どうしてだと思いますか"), ("next", "s4")])),
            ("s4", OrderedDict([("text", "記憶は、消えてはいないのです。人と人をつなぐ糸が切れて、届かなくなるだけなのです"), ("next", "s5")])),
            ("s5", OrderedDict([("text", "糸が切れると、その先の記憶は、凍ったように動かなくなります。切れた端が白くほつれて、ほどけるように……"),
                                ("next", "s6")])),
            ("s6", OrderedDict([("text", "けれど、糸は結び直せます。そばにいること。名前を呼ぶこと。触れること。そうすれば、また届くようになる"),
                                ("next", "s7")])),
            ("s7", OrderedDict([("text", "ただ……長く切れたままの糸は、結び直しにくくなるのです。凍った湖が、春になっても、なかなか溶けないように"),
                                ("next", "s8")])),
            ("s8", OrderedDict([("text", "だから、どうか。忘れられても、名前を呼びつづけてください。……つながりは、灯りです"), ("next", "s9")])),
            ("s9", OrderedDict([("expression", "smile"), ("text", "この石板は、わたくしが預かりますね。教会の古文書と、読み比べてみます")])),
        ])),
        ("end_actions", [
            OrderedDict([("type", "amemaru:report_quest"), ("quest", "amemaru:lake_tablet")]),
            OrderedDict([("type", "amemaru:set_flags"), ("add", ["amemaru:story/serena_explained"])]),
            OrderedDict([("type", "amemaru:set_flags"), ("scope", "server"), ("add", ["amemaru:ch2/serena_explains"])]),
        ]),
    ])
    return out


QUEST = OrderedDict([
    ("_comment", "8日目（8階）：湖底の祠の石板を、セレナに見せる（石板を手に取ると始まる。セレナの会話で報告）【補完】"),
    ("name", "湖底の祠の石板"),
    ("kind", "story"),
    ("giver", "amemaru:serena"),
    ("report", "amemaru:serena"),
    ("board", False),
    ("description", "8階の湖の底の祠で、古い石板を見つけた。「沈黙は、争いを止めた。だが、歌も止めた」。教会のセレナに見せよう。"),
    ("requires", OrderedDict()),
    ("objectives", [OrderedDict([("type", "collect"), ("item", "amemaru:lake_shrine_tablet"), ("count", 1), ("consume", True),
                                 ("label", "湖底の祠の石板をセレナに見せる")])]),
    ("rewards", OrderedDict([("knots", 400), ("exp", 500), ("affinity", {"amemaru:serena": 20}),
                             ("flags", ["amemaru:story/lake_tablet_reported"])])),
    ("order", 140),
])


# ================================================================ 素材・石板

MATERIALS = OrderedDict([
    ("silent_ice", ("静氷", "uncommon",
                    "音を吸いこむ、白く濁った湖の氷。たたいても、音がしない。8階の装備とアクセサリーの材料になる。")),
    ("fish_scale", ("大魚の鱗", "common",
                    "無音の大魚の、手のひらほどもある白い鱗。光にかざすと、湖の底の色が見える。8階のアクセサリーの材料になる。")),
    ("voice_stone", ("声石", "uncommon",
                     "声喰いが消えたあとに残る、青く光る小石。耳に当てると、誰かの声が遠くで聞こえる。8階のアクセサリーの材料になる。")),
    ("crane_plume", ("凍て鶴の冠羽", "common",
                     "凍て鶴の頭の、霜の冠の羽。冬の朝の光の色をしている。8階のアクセサリーの材料になる。")),
    ("mist_thread", ("霧の糸", "common",
                     "氷霧の亡霊の衣がほどけた、細い霧の糸。指に巻くと、ひんやりと消えていく。8階のアクセサリーの材料になる。")),
    ("lake_pearl", ("湖の真珠", "rare",
                    "湖主のひげの根もとに抱かれていた、灰色の真珠。のぞきこむと、凍った湖の底が映る。8階の名のある魔物とボスが持つ。")),
    ("lake_shrine_tablet", ("湖底の祠の石板", "rare",
                            "湖底の祠にまつられていた、手のひらほどの石板。「沈黙は、争いを止めた。だが、歌も止めた」と刻まれている。"
                            "セレナに見せよう。")),
])


# ================================================================ 言葉

LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.reflecta"] = OrderedDict([
    ("entity.amemaru.silent_fish", "無音の大魚"), ("entity.amemaru.voice_eater", "声喰い"), ("entity.amemaru.frost_crane", "凍て鶴"),
    ("entity.amemaru.fog_wraith", "氷霧の亡霊"), ("entity.amemaru.mune", "声なき湖主ムーネ"),
])
LANG_AFTER["item.amemaru.reflecta_spawn_egg"] = OrderedDict([
    ("item.amemaru.silent_fish_spawn_egg", "無音の大魚のスポーンエッグ"), ("item.amemaru.voice_eater_spawn_egg", "声喰いのスポーンエッグ"),
    ("item.amemaru.frost_crane_spawn_egg", "凍て鶴のスポーンエッグ"), ("item.amemaru.fog_wraith_spawn_egg", "氷霧の亡霊のスポーンエッグ"),
    ("item.amemaru.mune_spawn_egg", "声なき湖主ムーネのスポーンエッグ"),
])
LANG_AFTER["boss.amemaru.reflecta.tip.mirror"] = OrderedDict([
    ("boss.amemaru.intro.mune", "第8階層「沈黙の湖」　湖心"),
    ("cast.amemaru.mune.rise", "突き上げ（足もとの氷のひび）"), ("cast.amemaru.mune.sweep", "髭払い（前の扇）"),
    ("cast.amemaru.mune.tail", "尾打ち（まわりの円・薄氷にひび）"),
    ("cast.amemaru.mune.veil", "静寂の帳（音・予兆の印・仲間の欄が消える）"),
    ("cast.amemaru.mune.wave", "断ち波（湖のまん中から広がる波。跳んでよける）"),
    ("status.amemaru.mune.veil", "静寂の帳（あと %1$s 秒）― 違う鳴り石を2つ、続けて鳴らすと破れる"),
    ("status.amemaru.mune.dazed", "静寂の帳が破れた！ 湖主は動けない。攻めどき（あと %1$s 秒）"),
    ("status.amemaru.mune.surfaced", "湖主が氷の上に出た。今なら叩ける（あと %1$s 秒）"),
    ("boss.amemaru.mune.line.start", "湖の氷の下を、巨大な影がゆっくりと回りはじめた。……音が、また遠くなる"),
    ("boss.amemaru.mune.line.phase2", "湖主が氷の下で身をよじった。湖じゅうの氷が、低くうなる"),
    ("boss.amemaru.mune.line.phase3", "湖主の光るひげが、氷を透かして激しく揺れる。静けさが、重くのしかかってくる"),
    ("boss.amemaru.mune.line.veil", "湖主が口を開けた。……声は出ない。かわりに、あたりの音がすべて消えていく"),
    ("boss.amemaru.mune.line.broken", "鳴り石の音が、湖に響いた！ 静寂の帳が破れ、湖主が氷の上で身動きを止める"),
    ("boss.amemaru.mune.tip.start", "声なき湖主は、氷の下では叩けない。氷のひびの印が出たら、すぐ離れよう"),
    ("boss.amemaru.mune.tip.rise", "突き上げ：足もとに氷のひびの印。何回か突き上げると、湖主は氷の上へ出てくる（そのときが攻めどき）"),
    ("boss.amemaru.mune.tip.surface", "湖主が氷の上に出た！ しばらくの間だけ叩ける。髭払い（前の扇）と尾打ち（まわりの円）に気をつけて"),
    ("boss.amemaru.mune.tip.veil", "静寂の帳：音と予兆の印と仲間の欄が消える。湖心のふちの鳴り石を、2人で別々に、続けて（3秒のうちに）鳴らそう。"
                                  "声をかけ合って（ボイスチャット）"),
    ("boss.amemaru.mune.tip.wave", "断ち波：湖のまん中から、氷の下を波が広がる。波が来たら跳んでよけよう（当たると「断線」）"),
])
LANG_AFTER["message.amemaru.seal.passphrase"] = OrderedDict([
    ("message.amemaru.seal.bells", "祠の奥の扉は、まだ閉じている。湖の鐘を、歌のとおりに鳴らそう（%s/%s）"),
])
LANG_AFTER["message.amemaru.lilia.stage3"] = OrderedDict([
    ("message.amemaru.bells.song_title", "%s　今日の歌"),
    ("message.amemaru.bells.song", "今日の歌：%1$s。この順番で鐘を鳴らそう（最初の鐘から%2$s秒のうちに。鐘の音はしない。灯れば正しい）。"
                                   "鐘：%3$s"),
    ("message.amemaru.bells.song_done", "今日の歌：%1$s。この鐘の歌は、もう湖に響いている。鐘：%3$s"),
    ("message.amemaru.bells.song_chat", "%s：今日の歌は「%s」"),
    ("message.amemaru.bells.already", "%sは、もう正しく鳴らしてある"),
    ("message.amemaru.bells.wrong", "%sは、今日の歌の順番ではない（%s）"),
    ("message.amemaru.bells.wrong_reset", "%sは、今日の歌の順番ではない。灯っていた鐘がすべて消えた（%s。はじめから）"),
    ("message.amemaru.bells.rang", "%sが灯った（%s/%s・あと%s秒）"),
    ("message.amemaru.bells.timeout", "%s：時間が過ぎて、灯っていた鐘がすべて消えた（はじめから）"),
    ("message.amemaru.bells.solved", "%sの歌が、正しく響いた！（%s/%s）"),
    ("message.amemaru.bells.solved_sound", "%sの歌が、正しく響いた！ 湖に音が戻った（%s/%s）"),
    ("message.amemaru.bells.sound_returns", "%s：湖に、音が戻った"),
    ("message.amemaru.lake.ice_cracking", "足もとの薄氷に、ひびが入っていく……（集まりすぎ。離れよう）"),
    ("message.amemaru.lake.ice_broke", "薄氷が割れた！"),
    ("message.amemaru.lake.rescued", "冷たい水から、なんとか這い上がった（凍える）"),
    ("message.amemaru.mune.bell_quiet", "鳴り石は、静まりかえっている（湖主が静寂の帳を広げたときに響く）"),
    ("message.amemaru.mune.bell_one", "鳴り石が光った！ 別の鳴り石を、%s秒のうちに鳴らそう"),
    ("message.amemaru.lost.lost_sever", "ロスト状態「断線」：仲間のHPが見えない"),
    ("message.amemaru.lost.lost_silence", "ロスト状態「静寂」：音と、予兆の音が消えた"),
])
LANG_AFTER["commands.amemaru.floor.passphrase_reset"] = OrderedDict([
    ("commands.amemaru.floor.bells_reset", "鐘を、朝の沈黙に戻しました"),
    ("commands.amemaru.floor.no_bells", "%s階には、鐘がありません"),
    ("commands.amemaru.floor.bells_solved", "鐘の組を %s つ鳴らしきりました"),
])
LANG_AFTER["block.amemaru.light_target"] = OrderedDict([
    ("block.amemaru.lake_ice", "湖の氷"), ("block.amemaru.thin_ice", "薄氷"), ("block.amemaru.lake_bell", "湖の鐘"),
    ("block.amemaru.song_stele", "歌の碑"), ("block.amemaru.lake_tablet", "湖底の祠の石板"),
])
LANG_AFTER["effect.amemaru.lost_fade"] = OrderedDict([
    ("effect.amemaru.lost_sever", "断線"), ("effect.amemaru.lost_silence", "静寂"),
])
LANG_AFTER["effect.amemaru.lost_resist"] = OrderedDict([
    ("effect.amemaru.warning_ear", "ロスト状態「静寂」の間も、予兆の音が自分にだけ聞こえる"),
])
LANG_AFTER["item.amemaru.mirror_ice_daggers"] = OrderedDict([
    ("item.amemaru.lake_lord_whisker_bow", "湖主の髭弓"),
])


def mat_lang():
    out = OrderedDict()
    for mid, (name, _r, desc) in MATERIALS.items():
        out["item.amemaru." + mid] = name
        out["item.amemaru." + mid + ".desc"] = desc
    return out


def merge_lang():
    LANG_AFTER["item.amemaru.prism_shard.desc"] = mat_lang()
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
    weapons = [w for w in d["weapons"] if w["id"] != "lake_lord_whisker_bow"]
    weapons.append(OrderedDict([("id", "lake_lord_whisker_bow"), ("kind", "bow")]))
    d["weapons"] = weapons
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def item_models():
    for mid in MATERIALS:
        write(os.path.join(ASSETS, "models/item", mid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + mid}})
    for egg in ("silent_fish", "voice_eater", "frost_crane", "fog_wraith", "mune"):
        write(os.path.join(ASSETS, "models/item", egg + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor8_lake.json"), chest_table())
    for name, data in story().items():
        write(os.path.join(DEFS, "story_events/floor8", name + ".json"), data)
    for (npc, name), data in dialogues().items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    write(os.path.join(DEFS, "quests", "lake_tablet.json"), QUEST)
    write(os.path.join(DEFS, "equipment", "lake_lord_whisker_bow.json"), BOW)
    merge_item_types()
    merge_lang()
    item_models()
    print("enemies", len(ENEMIES), "story", len(story()), "dialogues", len(dialogues()))


if __name__ == "__main__":
    main()
