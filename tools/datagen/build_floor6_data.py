"""6階「凍結した村」のデータ（敵・落とし物・宝箱・物語・会話・クエスト・記憶の場面の役者・ボスのレア・言葉）を書き出す（11月25日）。

  python3 tools/datagen/build_floor6_data.py      # tools/mapgen/build_floor6.py で floors/floor6.json を作ってから

Phase 0-A 9章の6階（確定）：一瞬のまま凍りついた雪の村。雑魚は霜狼・氷像の残響・凍て鳥（吹雪を起こす）・霜の欠け熊（大型）。
ボスは村長の残響オルド。ギミックは記憶の解凍。古代記録「村の子どもの日記」。物語は6日目：この階に到達すると、ロストがコネリアにも
及ぶ演出が起き、街の人の一部がプレイヤーを忘れ始める（13章：街の人（一部）は教会の「記憶の相談所」のクエストで一人ずつ、
ポッカ爺は魚を見せると一匹ずつ思い出す。10日目に全員）。ボスのレア（20章【提案】）：村長の毛皮外套（防具：寒さと遅くなる効果を
減らす）5%。【11月25日に決定】防具は一式全部揃えた形 → 村長の毛皮（頭・胴・脚・足）の一式にした。数値と台詞は【補完】。
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_floor4_data import DATA, DEFS, LANG, REPO, pool, st, write  # noqa: E402

FLOOR = 6


def enemy(comment, name, level, desc, stats, exp, skills, behavior, loot, rare, model=None, pity=None):
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
    return d


FRUITS = [{"item": "amemaru:crystal_fruit_white", "chance": 0.6}, {"item": "amemaru:crystal_fruit_azure", "chance": 0.3}]

ENEMIES = OrderedDict()
ENEMIES["frost_wolf"] = enemy(
    "霜狼（6階の雑魚。Phase 0-A 9章の6階）。群れで動き、噛みつき（bite）と突進（charge。1階の欠け牙の狼と同じ動き）。"
    "噛まれると凍える（behavior.chill_stacks 段・chill_seconds 秒。凍え1つで移動 −10%）。数値は【補完】",
    "霜狼", 25,
    "白く凍った毛並みの狼。群れで村の通りをさまよい、吐く息で獲物の足を凍えさせる。足あとには、溶けない霜が残る。",
    st(1500, 165, 14, 0.27, 22, 12), 56,
    {"bite": {"power": 1.0, "windup": 0.5, "cooldown": 1.5, "range": 2.0, "angle": 90},
     "charge": {"power": 1.6, "windup": 1.2, "cooldown": 8.0, "range": 13.0, "min_range": 4.5, "speed": 0.85, "width": 1.2,
                "recover": 1.2}},
    {"chill_stacks": 1, "chill_seconds": 4.0},
    "frost_wolf", FRUITS)
ENEMIES["frost_wolf_leader"] = enemy(
    "霜狼の群れ長（6階の名のある魔物。川向こうの林のねぐらの宝箱の守り手）。大きく、噛まれると凍えが2つ。HPが半分を切ると遠吠えで"
    "仲間を呼ぶ（behavior.call 匹）。数値は【補完】",
    "霜狼の群れ長", 29,
    "白い牙を持つ、ひときわ大きな霜狼。凍った川のほとりで群れを率いる。遠吠えひとつで、林じゅうの霜狼が集まってくる。",
    st(14800, 190, 18, 0.28, 28, 16), 460,
    {"bite": {"power": 1.2, "windup": 0.6, "cooldown": 1.6, "range": 2.6, "angle": 100},
     "charge": {"power": 1.9, "windup": 1.2, "cooldown": 6.5, "range": 16.0, "min_range": 4.5, "speed": 0.95, "width": 1.8,
                "recover": 1.0}},
    {"chill_stacks": 2, "chill_seconds": 5.0, "call": 3, "scale": 1.6},
    "frost_wolf", [{"item": "amemaru:frost_silver", "chance": 40.0}, {"item": "amemaru:white_fang_charm", "chance": 5.0},
                   {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}], model="amemaru:frost_wolf")
ENEMIES["statue_echo"] = enemy(
    "氷像の残響（6階の雑魚。Phase 0-A 9章の6階「村人の氷像の一部が近づくと動く」）。凍った村人の氷像と同じ見た目で、ふだんは像のまま"
    "動かない。behavior.wake_range まで近づく・叩くと目覚める（4階の沈黙の石像と同じ動き）。眠っている間の受けるダメージ "
    "dormant_taken、起きている間 stone_taken。拳（fist）と叩きつけ（quake。当たると凍えが chill_stacks）。数値は【補完】",
    "氷像の残響", 26,
    "凍った村人の氷像に、残った記憶がしがみついたもの。顔は白く抜け落ちている。近づく者を、村を荒らす余所者だと思い込んで動き出す。",
    st(2700, 178, 28, 0.18, 18, 8), 60,
    {"fist": {"power": 1.2, "windup": 0.9, "cooldown": 2.4, "range": 2.8},
     "quake": {"power": 1.5, "windup": 1.4, "cooldown": 9.0, "range": 3.5}},
    {"wake_range": 5.0, "dormant_taken": 0.3, "stone_taken": 0.85, "chill_stacks": 2, "chill_seconds": 5.0},
    "statue_echo", FRUITS)
ENEMIES["frost_bird"] = enemy(
    "凍て鳥（6階の雑魚。Phase 0-A 9章の6階「凍て鳥（吹雪を起こす）」）。空に浮き、氷の羽根を撃つ（needle。1階の結晶蜂と同じ動き）。"
    "blizzard：狙った人のまわりに円の予兆（windup 秒）のあと、seconds 秒の吹雪（radius。1秒ごとに power の傷と凍え。近くが見えにくく"
    "なる）。数値は【補完】",
    "凍て鳥", 25,
    "白と氷青の羽の大きな鳥。羽ばたくたびに雪が舞い、群れが空を横切ると、村に吹雪が起こる。",
    st(1100, 150, 8, 0.22, 24, 14), 50,
    {"needle": {"power": 0.9, "windup": 0.8, "cooldown": 3.0, "range": 14, "speed": 0.9},
     "blizzard": {"power": 0.3, "windup": 1.8, "cooldown": 16.0, "range": 16.0, "radius": 6.0, "seconds": 5.0, "chill_seconds": 4.0}},
    {"keep_distance": 8, "hover_height": 5.0},
    "frost_bird", FRUITS)
ENEMIES["frost_bear"] = enemy(
    "霜の欠け熊（6階の大型の雑魚。Phase 0-A 9章の6階）。爪の薙ぎ払い（claw。扇）・跳びかかり（charge）・咆哮（まわりの仲間の攻撃+。"
    "4階の欠けた聖獣と同じ動き）。爪に当たると凍えが chill_stacks。体のところどころが白く欠けている（ロスト）。数値は【補完】",
    "霜の欠け熊", 27,
    "霜の毛並みの大きな熊。体のところどころが白く欠け落ちて、ときどき自分が何をしていたか忘れたように立ち止まる。",
    st(5200, 205, 22, 0.21, 22, 11), 120,
    {"claw": {"power": 1.3, "windup": 0.9, "cooldown": 2.6, "range": 3.6, "angle": 120},
     "charge": {"power": 1.7, "windup": 1.3, "cooldown": 9.0, "range": 14.0, "min_range": 5.0, "width": 2.4, "speed": 0.9,
                "recover": 1.6}},
    {"roars": 1, "roar_radius": 10.0, "roar_attack": 1.2, "roar_seconds": 10.0, "forget_chance": 0.04, "chill_stacks": 2,
     "chill_seconds": 5.0},
    "frost_bear", FRUITS + [{"item": "amemaru:crystal_fruit_amber", "chance": 0.4}])
ENEMIES["frost_bear_elder"] = enemy(
    "霜の欠け熊の主（6階の名のある魔物。牧場の納屋の宝箱の守り手）。もっと大きく、咆哮が2回。数値は【補完】",
    "霜の欠け熊の主", 30,
    "村の牧場に居座る、山のような霜の熊。白く欠けた背中から、溶けない氷の棘が生えている。",
    st(19000, 220, 26, 0.22, 28, 14), 520,
    {"claw": {"power": 1.45, "windup": 0.95, "cooldown": 2.6, "range": 4.6, "angle": 130},
     "charge": {"power": 1.9, "windup": 1.2, "cooldown": 6.5, "range": 18.0, "min_range": 5.0, "width": 3.0, "speed": 1.0,
                "recover": 1.2}},
    {"roars": 2, "roar_radius": 14.0, "roar_attack": 1.3, "roar_seconds": 12.0, "forget_chance": 0.02, "chill_stacks": 2,
     "chill_seconds": 6.0, "scale": 1.5},
    "frost_bear", [{"item": "amemaru:frost_silver", "chance": 40.0}, {"item": "amemaru:bear_frost_charm", "chance": 5.0},
                   {"item": "amemaru:crystal_fruit_amber", "chance": 6.0}], model="amemaru:frost_bear")

# ---- ボス：村長の残響オルド（第2章のはじめのボス。4人パーティくらい）
FUR_SET = OrderedDict([
    ("mayor_fur_hat", ("head", "村長の毛皮帽", {"defense": 5, "hp": 30}, 4,
                       "村長オルドが冬の見回りにかぶっていた毛皮の帽子。耳まで温かく、吹雪の中でも村の音がよく聞こえる。")),
    ("mayor_fur_coat", ("chest", "村長の毛皮外套", {"defense": 9, "hp": 52}, 5,
                        "村長オルドの毛皮の外套。重いが、どんな寒さも通さない。えりの毛皮に、村の子どもたちの小さな手の跡が残っている。")),
    ("mayor_fur_leggings", ("legs", "村長の毛皮の脚衣", {"defense": 7, "hp": 40}, 4,
                            "毛皮を裏に張った厚い脚衣。雪の深い道でも、足が冷えない。")),
    ("mayor_fur_boots", ("feet", "村長の毛皮の長靴", {"defense": 4, "hp": 22, "speed": 1}, 4,
                         "凍った道でも滑らない、毛皮の長靴。オルドは、これで毎朝、村じゅうの雪をかいて回った。")),
])


def fur_gear():
    out = OrderedDict()
    for gid, (slot, name, stats, _chance, desc) in FUR_SET.items():
        d = OrderedDict([
            ("slot", slot), ("generation", 2), ("series", "村長の毛皮"), ("rarity", "epic"), ("required_level", 26), ("jobs", []),
            ("stats", stats), ("durability", 520 if slot == "chest" else 420), ("binding", "on_equip"),
        ])
        if slot == "chest":
            d["effects"] = [{"type": "cold_resist", "percent": 20}]
        d["rare"] = True
        d["repair"] = [{"item": "amemaru:frozen_fur", "count": 2}, {"item": "amemaru:frost_silver", "count": 1}]
        d["set_effects"] = [{"type": "cold_resist", "percent": 30}]
        d["description"] = desc
        out[gid] = d
    return out


def ordo_rare():
    rare = []
    for gid, (_slot, _name, _stats, chance, _desc) in FUR_SET.items():
        rare.append({"item": "amemaru:" + gid, "chance": chance, "rarity": "epic"})
    rare += [{"item": "amemaru:frost_silver", "chance": 100, "count": 2, "pity": False},
             {"item": "amemaru:crystal_fruit_white", "chance": 20, "pity": False},
             {"item": "amemaru:crystal_fruit_azure", "chance": 15, "pity": False},
             {"item": "amemaru:crystal_fruit_violet", "chance": 15, "pity": False}]
    return rare


ENEMIES["ordo"] = enemy(
    "6階のボス、村長の残響オルド（Phase 0-A 9章の6階：村を守るつもりで、誰が仲間か分からなくなっている。戦いながら家々から記憶の結晶を"
    "3つ集めて渡すと、思い出して戦いをやめる＝撃破扱い）。memory：結晶を運ぶ人がオルドのそば range へ来ると受け取り、stagger 秒よろめく"
    "（その間 broken 倍のダメージ）。次の結晶は、よろめきの後、HPが second_at・third_at を切るか every 秒で出る。3つ受け取るまで、HPは "
    "floor より下がらない。技：sweep（前の扇）・icicle（線。凍え chill）・blizzard（まわりの円。第2段階から）・gaze（疑いの眼差し。"
    "見ている方の扇 angle。目との間にブロックがあれば無事。入ると freeze_seconds 秒凍りつく）・call（霜狼を count 匹、第3段階は count_late）。"
    "HPは2人のときの基準（ひとりでも2人ぶん）。数値は【補完】（4人で15分ほど）",
    "村長の残響オルド", 32,
    "凍った村の村長の、記憶だけが残った姿。あの日のまま、村を守ろうと立ち続けている。けれど、もう誰が村の者なのか分からない。"
    "近づく者すべてに、杖の灯を向ける。",
    st(70000, 196, 18, 0.16, 64, 30), 2200,
    {"sweep": {"power": 1.3, "windup": 1.4, "anim_windup": 1.2, "cooldown": 6.0, "range": 7.0, "angle": 120, "push": 1.0},
     "icicle": {"power": 1.5, "windup": 1.8, "anim_windup": 1.4, "cooldown": 10.0, "range": 26.0, "width": 2.6, "chill": 2},
     "blizzard": {"power": 1.2, "windup": 2.2, "anim_windup": 1.8, "cooldown": 18.0, "range": 9.0, "chill": 2, "first": 20.0},
     "gaze": {"power": 0.8, "windup": 2.8, "anim_windup": 2.4, "cooldown": 24.0, "range": 30.0, "angle": 70, "freeze_seconds": 2.0,
              "first": 14.0},
     "call": {"windup": 1.6, "cooldown": 40.0, "count": 2, "count_late": 3, "max": 4, "first": 30.0},
     "memory": {"range": 3.5, "stagger": 12.0, "broken": 1.6, "floor": 0.1, "second_at": 0.7, "third_at": 0.4, "every": 100.0}},
    {},
    "ordo", ordo_rare(), pity=True)

LOOT = {
    "frost_wolf": [pool("amemaru:frozen_fur", 0.55), pool("amemaru:frost_fang", 0.35), pool("amemaru:ice_crystal", 0.15)],
    "statue_echo": [pool("amemaru:ice_crystal", 0.55, 1, 2), pool("amemaru:frost_silver", 0.25)],
    "frost_bird": [pool("amemaru:ice_feather", 0.6), pool("amemaru:ice_crystal", 0.3)],
    "frost_bear": [pool("amemaru:frozen_fur", 0.7, 1, 2), pool("amemaru:frost_silver", 0.4), pool("amemaru:frost_fang", 0.3)],
    "ordo": [pool("amemaru:frost_silver", None, 3, 5), pool("amemaru:ice_crystal", None, 3, 5), pool("amemaru:frozen_fur", None, 2, 4),
             pool("amemaru:ice_feather", 0.8, 1, 2), pool("amemaru:clear_echo", 0.4)],
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
             "entries": [e("amemaru:frost_silver", 14, 1, 3), e("amemaru:ice_crystal", 16, 1, 3), e("amemaru:frozen_fur", 16, 1, 3),
                         e("amemaru:frost_fang", 10, 1, 2), e("amemaru:ice_feather", 10, 1, 2), e("amemaru:polishing_powder", 8, 1, 3),
                         e("minecraft:bread", 8, 1, 3), e("amemaru:echo_fragment", 5, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:clear_echo"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_white", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_violet", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.14}]},
        ],
    }


# ================================================================ 物語（6日目）

def sub(text, ticks=110, wait=True):
    return OrderedDict([("type", "amemaru:subtitle"), ("text", text), ("ticks", ticks), ("wait", wait)])


def actor(aid, npc, pos, yaw):
    return OrderedDict([("type", "amemaru:actor"), ("id", aid), ("npc", npc),
                        ("pos", OrderedDict([("anchor", "floor"), ("offset", list(pos))])), ("yaw", yaw)])


def remove(aid):
    return OrderedDict([("type", "amemaru:actor_remove"), ("id", aid)])


def flags(*names, server=False):
    d = OrderedDict([("type", "amemaru:set_flags")])
    if server:
        d["scope"] = "server"
    d["add"] = list(names)
    return d


def floor_data():
    with open(os.path.join(DEFS, "floors", "floor6.json"), encoding="utf-8") as fp:
        return json.load(fp)


def memory_scene(mid, title, comment, cast, lines, flag):
    """記憶の氷像が溶けたときの、その日の村の短い場面（役者は、記憶の氷像のそばに立つ。氷像の場所は floors/floor6.json から）"""
    floor = floor_data()
    statue = next(m for m in floor["memory_statues"] if m["id"] == mid)
    x, y, z = statue["pos"]
    steps = [OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 12)])]
    for aid, npc, (dx, dz), yaw in cast:
        steps.append(actor(aid, npc, (x + 0.5 + dx, y, z + 0.5 + dz), yaw))
    steps.append(OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 24)]))
    steps.append(OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:block.amethyst_block.resonate"), ("volume", 1.0),
                              ("pitch", 0.7)]))
    for text, ticks in lines:
        steps.append(sub(text, ticks))
    steps.append(OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 16)]))
    for aid, _npc, _p, _y in cast:
        steps.append(remove(aid))
    steps.append(OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 24)]))
    steps.append(flags(flag))
    return OrderedDict([
        ("_comment", comment),
        ("title", title),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", steps),
    ])


def story():
    out = OrderedDict()
    out["enter"] = OrderedDict([
        ("_comment", "初めて6階に入ったとき（本人だけ）。一瞬のまま凍りついた雪の村・空にオーロラ（Phase 0-A 9章の6階の地形・環境）。"
                     "6日目：この階に到達すると、ロストがコネリアにも及ぶ（物語フラグ ch2/lost_in_town。サーバー全体。知らせは "
                     "NpcMemoryService が出す）。台詞は【補完】"),
        ("title", "凍結した村"),
        ("trigger", OrderedDict([("type", "amemaru:floor_enter"), ("floor", FLOOR)])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:ambient.basalt_deltas.mood"), ("volume", 0.8), ("pitch", 1.4)]),
            OrderedDict([("type", "amemaru:title"), ("title", "第2章　凍てついた記憶"), ("subtitle", "6階　凍結した村"), ("ticks", 110)]),
            sub("一面の雪。音のない村が、息をとめたまま凍りついていた……。", 120),
            sub("家の前で雪をかく人、手を振る人。誰もが、その一瞬のまま氷になっている。", 120),
            sub("見上げると、空に、緑と紫の光の帯――オーロラが揺れていた。", 110, False),
            flags("amemaru:ch2/lost_in_town", server=True),
        ]),
    ])
    out["village_gate"] = OrderedDict([
        ("_comment", "村はずれの門のすぐ内側（本人だけ。一人一度）。ロストの侵食：氷像の顔が空白（Phase 0-A 9章の6階）【補完】"),
        ("title", "顔のない氷像"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "village_gate")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/blank_faces_seen"])])),
        ("steps", [
            sub("門の上の鐘も、凍りついている。……鳴らそうとした誰かの手も、きっと。", 110),
            sub("氷像の顔をのぞきこむと、どれも、顔のところだけが白く抜け落ちていた。", 120),
            sub("……ロストだ。この村の人たちは、何かを――誰かを、忘れてしまったのだ。", 120, False),
            flags("amemaru:story/blank_faces_seen"),
        ]),
    ])
    out["frozen_market"] = OrderedDict([
        ("_comment", "市場の広場（本人だけ。一人一度）。記憶の解凍の手がかり（熱の結晶は、火の残る所でもらえる）【補完】"),
        ("title", "凍った市場"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "frozen_market")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/frozen_market_seen"])])),
        ("steps", [
            sub("値切り声が、聞こえる気がした。市場は、笑い声のかたちのまま凍っている。", 120),
            sub("淡く光る氷像がある。……溶かせば、凍った記憶が戻るかもしれない。", 110),
            sub("宿屋の暖炉、パン屋のかまど、鍛冶屋の炉。火の残る所なら、熱を分けてもらえそうだ。", 130, False),
            flags("amemaru:story/frozen_market_seen"),
        ]),
    ])
    out["aurora_lookout"] = OrderedDict([
        ("_comment", "北東の見張りの塔の上（本人だけ。一人一度）【補完】"),
        ("title", "見張りの塔"),
        ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", "aurora_lookout")])),
        ("scope", "player"),
        ("once", False),
        ("replayable", True),
        ("conditions", OrderedDict([("flags_none", ["amemaru:story/aurora_seen"])])),
        ("steps", [
            sub("塔の上から、村が見渡せる。オーロラの光が、凍った屋根を緑と紫に染めていた。", 130),
            sub("……忘れることは、凍ること。この村は、あの日から一歩も進めずにいる。", 120, False),
            flags("amemaru:story/aurora_seen"),
        ]),
    ])
    out["memory_market"] = memory_scene(
        "market", "記憶の場面：市場の値切り",
        "記憶の氷像「市場の値切り」が溶けたとき、そばの人に流れる、あの日の村の短い場面（Phase 0-A 9章の6階「その日の村の短い場面が"
        "見える」）。台詞は【補完】",
        [("merchant", "amemaru:echo_merchant", (0.0, 0.0), 90.0), ("buyer", "amemaru:echo_buyer", (-2.2, 0.0), 270.0)],
        [("商人「明日は冬祭りだ。この林檎、ひとつおまけしとくよ」", 110),
         ("客「もう、いつもそう言って……あら、雪？ ずいぶん白い雪ね」", 120),
         ("商人「……あれ。いま、何の話をしてたっけ」", 110),
         ("白い雪が降るたびに、村の人たちは、少しずつ何かを忘れていった。", 130)],
        "amemaru:story/memory_market")
    out["memory_mother"] = memory_scene(
        "mother", "記憶の場面：母と子",
        "記憶の氷像「母と子」（日記の子と、その母）。台詞は【補完】",
        [("mother", "amemaru:echo_mother", (0.0, 0.0), 180.0), ("child", "amemaru:echo_child", (-1.0, 1.4), 0.0)],
        [("女の子「おかあさん、まって！ わたしも、おまつりに行く！」", 120),
         ("母「……ええと。あなたは……どこの子だったかしら」", 120),
         ("女の子「……おかあさん？」", 90),
         ("日記の子だ。母親が、娘の名前を呼ばなくなった日――。", 130)],
        "amemaru:story/memory_mother")
    out["memory_ringer"] = memory_scene(
        "ringer", "記憶の場面：鐘を鳴らす若者",
        "記憶の氷像「鐘を鳴らす若者」（教会の前）。台詞は【補完】",
        [("ringer", "amemaru:echo_ringer", (0.0, 0.0), 90.0)],
        [("若者「白い雪だ！ みんな、家に入れ！ 鐘を……鐘を鳴らさないと……！」", 130),
         ("若者「……鐘？ なんのために、鳴らすんだっけ……」", 110),
         ("鐘は、ついに鳴らなかった。", 100)],
        "amemaru:story/memory_ringer")
    out["memory_mayor_call"] = memory_scene(
        "mayor_call", "記憶の場面：村長の呼びかけ",
        "記憶の氷像「村長の呼びかけ」（凍りついた門の前）。ボスの台詞「おまえは……村の者か……？」につながる。台詞は【補完】",
        [("ordo", "amemaru:echo_ordo", (0.0, 0.0), 0.0), ("listener", "amemaru:echo_merchant", (-3.0, 4.5), 180.0)],
        [("オルド「落ち着け、みんな！ 名前を呼び合え。忘れるな、互いの名を――」", 130),
         ("オルド「……おまえは……村の者か……？」", 110),
         ("村長は、最後まで村を守ろうとしていた。誰を守るのかさえ、分からなくなっても。", 140),
         ("凍りついた門の向こうに、村長の家と、雪の広場がある。", 110)],
        "amemaru:story/memory_mayor_call")
    out["first_kill"] = OrderedDict([
        ("_comment", "6階のボスの初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。オルドは思い出して戦いをやめる"
                     "（撃破扱い）。台詞は【補完】（白き声・NULLの名前は、まだ出さない）"),
        ("title", "村長の残響オルド"),
        ("trigger", OrderedDict([("type", "amemaru:floor_first_kill"), ("floor", FLOOR)])),
        ("scope", "party"),
        ("once", True),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:hud"), ("hide", True)]),
            OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:block.bell.resonate"), ("volume", 1.0), ("pitch", 0.8)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 30)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:title"), ("title", "凍結した村　踏破"), ("subtitle", "第2章　凍てついた記憶"), ("ticks", 110)]),
            sub("オルドは、ひざをついたまま、やさしく笑った。「……客人に、杖を向けるとはな」", 130),
            sub("「白い雪が降ってから、皆が少しずつ、互いを忘れていった。わしも……守るべき顔を、忘れてしまった」", 140),
            sub("「この先の森にも、まだ凍った記憶が眠っている。どうか、思い出してやってくれ」", 130),
            sub("オルドの姿は、光の粒になって、雪の中へ溶けていった。", 120),
            OrderedDict([("type", "amemaru:hud"), ("hide", False)]),
        ]),
    ])
    return out


ECHOES = OrderedDict([
    ("echo_merchant", ("市場の商人", "merchant", False)),
    ("echo_buyer", ("市場の客", "buyer", True)),
    ("echo_mother", ("母", "mother", True)),
    ("echo_child", ("日記の子", "child_girl", True)),
    ("echo_ringer", ("鐘を鳴らそうとした若者", "ringer", False)),
    ("echo_ordo", ("村長オルド", "ordo", False)),
])


def echo_npcs():
    out = OrderedDict()
    for nid, (name, skin, slim) in ECHOES.items():
        out[nid] = OrderedDict([
            ("_comment", "6階の記憶の場面の役者（あの日の村人。記憶の氷像が溶けたときの場面にだけ出る。街には立たない＝home がない）。"
                         "見た目は tools/artgen/make_village_things.py【補完】"),
            ("name", name),
            ("title", "あの日の村人"),
            ("skin", "amemaru:textures/entity/village/%s.png" % skin),
            ("slim", slim),
        ])
        if nid == "echo_child":
            out[nid]["model_scale"] = 0.72
    return out


# ================================================================ 会話・クエスト（6日目：記憶の相談所）

def choice(text, nxt="end", actions=None, conditions=None):
    c = OrderedDict([("text", text)])
    if conditions:
        c["conditions"] = conditions
    if actions:
        c["actions"] = actions
    c["next"] = nxt
    return c


FORGET = OrderedDict([
    ("bard", ("門番バルド", "……誰だ。見ない顔だな",
              [choice("塔へ行く", actions=[{"type": "amemaru:open_floor_select"}]), choice("やめておく")],
              "……塔へ行くなら、円に立て。……なぜだろうな。前にも、誰かにそう言った気がする")),
    ("gisele", ("ジゼル", "あら、はじめましてかしら？ ……変ね、どこかで会った気がするのに",
                [choice("ルーレットで遊ぶ", actions=[{"type": "amemaru:open_casino"}]),
                 choice("チップを両替したい", actions=[{"type": "amemaru:open_casino"}]), choice("またね")],
                "勝っても負けても笑う人は好きよ。……あなた、前にもそう言われなかった？")),
    ("petra", ("ペトラ", "いらっしゃいませ。……はじめてのお客さん、ですよね？",
               [choice("料理を作ってほしい", actions=[{"type": "amemaru:open_restaurant"}]), choice("またね")],
               "お料理の腕は、ちゃんと覚えてるんです。……お客さんのお名前だけが、どうしても")),
])


HINTS = {
    "bard": "……すまない。思い出せないんだ。教会のセレナなら、何か知っているかもしれん",
    "gisele": "……ごめんなさい。思い出せないの。教会で、記憶の相談をしているって聞いたわ",
    "petra": "……ごめんなさい。教会のセレナさんが、記憶の相談にのってくれるそうです",
}


def dialogues():
    out = OrderedDict()
    for npc, (name, first, choices, more) in FORGET.items():
        nodes = OrderedDict([
            ("s1", OrderedDict([("text", first), ("choices", choices + [choice("（前にも会ったことを話す）", "m1")])])),
            ("m1", OrderedDict([("text", more), ("next", "m2")])),
            ("m2", OrderedDict([("text", HINTS[npc])])),
        ])
        out[(npc, "forgot")] = OrderedDict([
            ("_comment", "6日目から（物語フラグ ch2/lost_in_town）、%sはプレイヤーを忘れている（Phase 0-A 13章「街の人（一部）」"
                         "「あんた、見ない顔だね」。NpcMemoryService）。施設の働きは止めない。教会の「記憶の相談所」のクエストで思い出す。"
                         "台詞は【補完】" % name),
            ("npc", "amemaru:" + npc),
            ("priority", 40),
            ("conditions", OrderedDict([("remembered", False)])),
            ("start", "s1"),
            ("nodes", nodes),
        ])
    out[("serena", "memory_desk")] = OrderedDict([
        ("_comment", "6日目：教会に「記憶の相談所」（Phase 0-A 15章の6日目の街の発展・13章「教会の『記憶の相談所』のクエストで一人ずつ」）。"
                     "忘れられた人ごとのクエストを始める。台詞は【補完】"),
        ("npc", "amemaru:serena"),
        ("priority", 45),
        ("conditions", OrderedDict([("flags_all", ["amemaru:ch2/lost_in_town"]), ("flags_none", ["amemaru:ch2/queen_down"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("text", "記憶の相談所へ、ようこそ。……あなたを忘れてしまった人が、いるのね"),
                                ("choices", [
                                    choice("門番のバルドのこと", "b1", [{"type": "amemaru:start_quest", "quest": "amemaru:memory_bard"}],
                                           OrderedDict([("flags_none", ["amemaru:memory/bard"])])),
                                    choice("カジノのジゼルのこと", "b1", [{"type": "amemaru:start_quest", "quest": "amemaru:memory_gisele"}],
                                           OrderedDict([("flags_none", ["amemaru:memory/gisele"])])),
                                    choice("料理屋のペトラのこと", "b1", [{"type": "amemaru:start_quest", "quest": "amemaru:memory_petra"}],
                                           OrderedDict([("flags_none", ["amemaru:memory/petra"])])),
                                    choice("記憶が消えるって、どういうこと？", "w1"),
                                    choice("またにする"),
                                ])])),
            ("b1", OrderedDict([("text", "その人と、前に交わした言葉を思い出して。それを、もう一度伝えに行きましょう"), ("next", "b2")])),
            ("b2", OrderedDict([("text", "糸は切れても、結び直せるわ。……一人ずつ、ゆっくりね")])),
            ("w1", OrderedDict([("text", "塔の上で、凍った村が見つかったでしょう。あの白い雪が、街にも降り始めたの"), ("next", "w2")])),
            ("w2", OrderedDict([("text", "わたくしの家は、記憶を守る家系。だから、わたくしは忘れないの"), ("next", "w3")])),
            ("w3", OrderedDict([("text", "忘れている間も、あなたとその人のつながりは消えないわ。凍っているだけ")])),
        ])),
    ])
    out[("pokka", "forgot")] = OrderedDict([
        ("_comment", "6日目から、ポッカ爺は魚の名前を忘れる（Phase 0-A 13章。魚図鑑の名前が一時的に「？？？」。魚を手に持って話しかけると、"
                     "一匹ずつ思い出す。NpcMemoryService.showFish）。台詞は【補完】"),
        ("npc", "amemaru:pokka"),
        ("priority", 40),
        ("conditions", OrderedDict([("flags_all", ["amemaru:ch2/lost_in_town"]), ("flags_none", ["amemaru:ch2/queen_down"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("text", "おう、いらっしゃい。……すまんなぁ、魚の名前が、どうにも出てこないんだなぁ"),
                                ("choices", [choice("魚図鑑を見せて", actions=[{"type": "amemaru:open_fishing"}]),
                                             choice("魚を売りたい・換えたい", actions=[{"type": "amemaru:open_fishing"}]),
                                             choice("どうしたら思い出せる？", "h1"), choice("またね")])])),
            ("h1", OrderedDict([("text", "魚を手に持って、話しかけてくれりゃ……顔を見りゃ、一匹ずつ思い出せる気がするんだなぁ")])),
        ])),
    ])
    return out


def quests():
    out = OrderedDict()
    for order, (npc, name, label) in enumerate((("bard", "門番バルド", "バルドに、前に交わした言葉を伝える"),
                                                ("gisele", "ジゼル", "ジゼルに、前に交わした言葉を伝える"),
                                                ("petra", "ペトラ", "ペトラに、前に交わした言葉を伝える"))):
        out["memory_" + npc] = OrderedDict([
            ("_comment", "6日目：記憶の相談所（セレナ）のクエスト。%sに話しかけると思い出してもらえる（本人のフラグ amemaru:memory/%s。"
                         "思い出したときに親密度を少し上乗せ。Phase 0-A 13章）【補完】" % (name, npc)),
            ("name", "記憶の相談：" + name),
            ("kind", "story"),
            ("giver", "amemaru:serena"),
            ("report", "amemaru:" + npc),
            ("board", False),
            ("description", "%sが、わたしのことを忘れてしまった。セレナの言うとおり、前に交わした言葉を、もう一度伝えに行こう。" % name),
            ("requires", OrderedDict()),
            ("objectives", [OrderedDict([("type", "talk"), ("npc", "amemaru:" + npc), ("label", label)])]),
            ("rewards", OrderedDict([("knots", 150), ("exp", 250), ("affinity", {"amemaru:" + npc: 10}),
                                     ("flags", ["amemaru:memory/" + npc])])),
            ("order", 160 + order),
        ])
    return out


# ================================================================ 言葉

LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.ancient_guardian"] = OrderedDict([
    ("entity.amemaru.frost_wolf", "霜狼"), ("entity.amemaru.statue_echo", "氷像の残響"), ("entity.amemaru.frost_bird", "凍て鳥"),
    ("entity.amemaru.frost_bear", "霜の欠け熊"), ("entity.amemaru.ordo", "村長の残響オルド"),
])
LANG_AFTER["item.amemaru.ancient_guardian_spawn_egg"] = OrderedDict([
    ("item.amemaru.frost_wolf_spawn_egg", "霜狼のスポーンエッグ"), ("item.amemaru.statue_echo_spawn_egg", "氷像の残響のスポーンエッグ"),
    ("item.amemaru.frost_bird_spawn_egg", "凍て鳥のスポーンエッグ"), ("item.amemaru.frost_bear_spawn_egg", "霜の欠け熊のスポーンエッグ"),
    ("item.amemaru.ordo_spawn_egg", "村長の残響オルドのスポーンエッグ"),
])
LANG_AFTER["boss.amemaru.intro.ancient_guardian"] = OrderedDict([
    ("boss.amemaru.intro.ordo", "第6階層「凍結した村」　雪の広場"),
    ("cast.amemaru.ordo.sweep", "杖の薙ぎ払い（前の扇）"), ("cast.amemaru.ordo.icicle", "氷柱の道（線の上）"),
    ("cast.amemaru.ordo.blizzard", "吹雪（まわりの円）"), ("cast.amemaru.ordo.gaze", "疑いの眼差し（見ている方の扇・陰に隠れる）"),
    ("cast.amemaru.ordo.call", "霜狼を呼ぶ"),
    ("status.amemaru.ordo.memory", "記憶の結晶 %2$s/%3$s ― まわりの家から運んで、オルドのそばへ（運ぶ人を守ろう）"),
    ("status.amemaru.ordo.open", "記憶がよみがえり、オルドがよろめいている！ 攻めどき（あと %1$s 秒）"),
    ("status.amemaru.ordo.gaze", "疑いの眼差しまで %1$s 秒 ― 家や泉の陰に隠れる（オルドの目から見えない所へ）"),
    ("boss.amemaru.ordo.line.start", "雪の広場に、杖の灯がともった。「……おまえは……村の者か……？」"),
    ("boss.amemaru.ordo.line.phase2", "「白い雪が……！ みんな、家に入れ……！」オルドの声が、広場に響いた"),
    ("boss.amemaru.ordo.line.phase3", "「名前を……名前を呼び合え……忘れるな……！」"),
    ("boss.amemaru.ordo.line.gaze", "オルドの目が、淡く光った。「……おまえは、誰だ」"),
    ("boss.amemaru.ordo.line.floor", "オルドは倒れない。「まだ……倒れるわけには、いかん……村を、守らねば……」"),
    ("boss.amemaru.ordo.line.memory1", "「……この声……市場の、笑い声……」オルドの杖が、わずかに下がった"),
    ("boss.amemaru.ordo.line.memory2", "「……あの子が、母を呼んでいる……わしは、何を……」"),
    ("boss.amemaru.ordo.line.remember", "オルドの手から、杖が落ちた。「……ああ。そうだ……おまえたちは、村の客人だったな」"),
    ("boss.amemaru.ordo.line.thanks", "「……すまなかった。ありがとう。思い出させてくれて」"),
    ("boss.amemaru.ordo.tip.start", "記憶の結晶が、広場のまわりの家に現れる。拾ってオルドのそばへ運ぼう。3つ渡すと、オルドは戦いをやめる"),
    ("boss.amemaru.ordo.tip.crystal", "記憶の結晶を運ぶ人は遅くなり、霜狼に狙われる。ほかの人が守ろう"),
    ("boss.amemaru.ordo.tip.open", "結晶を受け取ったオルドはよろめく。今が攻めどき！ 次の結晶は、しばらくすると現れる"),
    ("boss.amemaru.ordo.tip.gaze", "疑いの眼差し：オルドが見ている方の扇に入ると凍りつく。家や泉の陰に隠れて、目から見えない所へ"),
    ("boss.amemaru.ordo.tip.icicle", "地面に長い線が出たら、氷柱の道。当たると凍えて遅くなる。線の外へ"),
    ("boss.amemaru.ordo.tip.blizzard", "オルドのまわりに赤い円が出たら、吹雪。円の外へ離れよう"),
    ("boss.amemaru.ordo.tip.call", "霜狼が来た！ 結晶を運ぶ人を狙ってくる"),
    ("boss.amemaru.ordo.tip.floor", "オルドは倒れない。記憶の結晶をすべて渡せば、思い出して戦いをやめる"),
    ("boss.amemaru.ordo.spot.0", "西の家"), ("boss.amemaru.ordo.spot.1", "東の家"), ("boss.amemaru.ordo.spot.2", "村長の家"),
    ("boss.amemaru.ordo.crystal", "記憶の結晶が、%sに現れた！"),
    ("boss.amemaru.ordo.received", "%sが、記憶の結晶をオルドに渡した（%s/%s）"),
    ("boss.amemaru.ordo.hidden", "陰に隠れて、疑いの眼差しをやり過ごした"),
    ("boss.amemaru.ordo.frozen", "疑いの眼差しに射すくめられ、体が凍りついた！"),
])
LANG_AFTER["message.amemaru.village.all_thawed"] = OrderedDict([
    ("message.amemaru.town.lost", "コネリアに、白い雪が降り始めた……"),
    ("message.amemaru.town.lost_detail", "街の人の一部が、あなたたちのことを忘れ始めている。教会のセレナが「記憶の相談所」を開いたらしい。"),
    ("message.amemaru.pokka.remembered", "ポッカ爺「おお……そうだ、こいつは「%s」だなぁ！ 思い出したよ」"),
])


def fur_lang():
    out = OrderedDict()
    for gid, (_slot, name, _stats, _chance, _desc) in FUR_SET.items():
        out["item.amemaru." + gid] = name
    return out


MATERIALS = OrderedDict([
    ("frost_silver", ("霜銀", "uncommon", "凍った村の古い鉱脈から採れる、白く冷たい銀。第2世代の装備の地金になる。")),
    ("ice_crystal", ("氷晶", "uncommon", "溶けない氷の結晶。中に、かすかに誰かの声が閉じこめられている。")),
    ("frozen_fur", ("凍て毛皮", "common", "霜の獣の毛皮。凍ったままでも柔らかく、寒さを通さない。")),
    ("frost_fang", ("霜狼の牙", "common", "霜狼の白い牙。触れると指先が凍える。")),
    ("ice_feather", ("凍て鳥の羽根", "common", "凍て鳥の氷青の羽根。ふわりと舞うたびに、雪のかけらがこぼれる。")),
])


def mat_lang():
    out = OrderedDict()
    for mid, (name, _r, desc) in MATERIALS.items():
        out["item.amemaru." + mid] = name
        out["item.amemaru." + mid + ".desc"] = desc
    return out


def merge_lang():
    LANG_AFTER["item.amemaru.guardian_monk_sandals"] = fur_lang()
    LANG_AFTER["item.amemaru.jade_core_shard"] = mat_lang()
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
    armor = [a for a in d["armor"] if a["series"] != "mayor_fur"]
    armor.append(OrderedDict([("series", "mayor_fur"), ("pieces", OrderedDict(
        (slot, gid) for gid, (slot, *_rest) in FUR_SET.items()))]))
    d["armor"] = armor
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor6_village.json"), chest_table())
    for name, data in story().items():
        write(os.path.join(DEFS, "story_events/floor6", name + ".json"), data)
    for (npc, name), data in dialogues().items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    for name, data in quests().items():
        write(os.path.join(DEFS, "quests", name + ".json"), data)
    for name, data in echo_npcs().items():
        write(os.path.join(DEFS, "npcs", name + ".json"), data)
    for gid, data in fur_gear().items():
        write(os.path.join(DEFS, "equipment", gid + ".json"), data)
    merge_item_types()
    merge_lang()
    print("enemies", len(ENEMIES), "story", len(story()), "dialogues", len(dialogues()), "quests", len(quests()))


if __name__ == "__main__":
    main()
