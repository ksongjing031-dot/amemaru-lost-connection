"""4階「崩壊した神殿」のデータ（敵・落とし物・物語・会話・クエスト・品・言葉）を書き出す（11月24日【補完】）。

  python3 tools/datagen/build_floor4_data.py

Phase 0-A 9章の4階（確定）：
  雑魚：沈黙の石像（近づくと動き出す）・祈り手の残響（祈りの歌で仲間を回復させるので先に倒す）・結晶甲虫（硬いが魔法に弱い）・
        欠けた聖獣（神殿の守護獣がロストで欠けたもの）
  ボス：祈りの残響カンターレ（4体の歌い手で、歌の順番どおりに4つの音の床を踏むと無防備になる）
  古代記録「神殿の碑文」。物語は4日目：結晶騎士団の遺品（冑と認識票）が見つかり、ガルドが15年前の遠征を語る。
  副団長ハロルドの認識票をリリアに届けるクエスト。ノアと初めて言葉を交わす（ノアの会話 first は前から4日目にある）
  21章：カンターレのレア装備「祈り手の鈴」（鈴杖：回復に小さな防御強化がつく）5%
数値・台詞の細かいところは【補完】。言葉は ja_jp.json と en_us.json に同じものを書く（英語は後で）。
"""
import json
import os
from collections import OrderedDict

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DATA = os.path.join(REPO, "src/main/resources/data/amemaru")
DEFS = os.path.join(DATA, "amemaru")
LANG = os.path.join(REPO, "src/main/resources/assets/amemaru/lang")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


# ================================================================ 敵

def enemy(comment, name, level, desc, stats, exp, skills, behavior, loot, rare, model=None, pity=None):
    d = OrderedDict()
    d["_comment"] = comment
    d["name"] = name
    if model:
        d["model"] = model
    d["floor"] = 4
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


def st(hp, attack, defense, speed, follow, aggro):
    return OrderedDict([("hp", hp), ("attack", attack), ("defense", defense), ("move_speed", speed), ("follow_range", follow),
                        ("aggro_range", aggro)])


FRUITS = [{"item": "amemaru:crystal_fruit_amber", "chance": 0.6}, {"item": "amemaru:crystal_fruit_azure", "chance": 0.3}]

ENEMIES = {
    "silent_statue": enemy(
        "沈黙の石像（4階の雑魚。Phase 0-A 9章「近づくと動き出す」）。眠っている間は動かず、behavior.wake_range まで近づく・叩くと"
        "目覚める。眠っている間の受けるダメージ dormant_taken、起きている間 stone_taken。拳（fist。前）と叩きつけ（quake。まわりの円）。"
        "しばらく誰もいないと元の場所で像に戻る。数値は【補完】",
        "沈黙の石像", 19,
        "顔を削り取られた、白い大理石の神殿の像。ふだんはただの像のように立っているが、近づく者があると、石をきしませて動き出す。"
        "眠っている間の石の肌は、ひどく硬い。",
        st(2130, 127, 26, 0.17, 18, 8), 46,
        {"fist": {"power": 1.2, "windup": 0.9, "cooldown": 2.4, "range": 2.8},
         "quake": {"power": 1.6, "windup": 1.4, "cooldown": 9.0, "range": 3.5}},
        {"wake_range": 5.0, "dormant_taken": 0.3, "stone_taken": 0.85},
        "silent_statue", FRUITS),
    "prayer_echo": enemy(
        "祈り手の残響（4階の雑魚。Phase 0-A 9章「祈りの歌で仲間を回復させるので先に倒す」）。hymn：少し離れて windup 秒歌い、"
        "range の神殿の敵のHPを power（最大HPの割合）回復。歌の間に最大HPの behavior.interrupt の傷を受けると止まる。"
        "wail：まわりへの声（円）。宙に浮く。数値は【補完】",
        "祈り手の残響", 17,
        "神殿で祈っていた人の、声だけが残った淡い姿。いまも祈りの歌を口ずさみ、まわりの神殿の魔物の傷をふさぐ。"
        "体はもろいので、歌い出す前に倒したい。",
        st(910, 90, 6, 0.2, 20, 11), 36,
        {"wail": {"power": 0.9, "windup": 0.8, "cooldown": 2.6, "range": 3.0},
         "hymn": {"power": 0.12, "windup": 3.0, "cooldown": 12.0, "range": 9.0}},
        {"interrupt": 0.12},
        "prayer_echo", FRUITS),
    "crystal_beetle": enemy(
        "結晶甲虫（4階の雑魚。Phase 0-A 9章「硬いが魔法に弱い」）。魔力でないダメージは physical_taken、魔力のダメージは"
        "magic_taken（スキルの効果が魔力で決まるか）。噛みつき（bite。前）と体当たり（charge。短い線）。数値は【補完】",
        "結晶甲虫", 17,
        "背中の殻から青白い結晶を生やした、大きな甲虫。殻は剣も矢も弾くほど硬いが、魔力には弱く、魔法が当たると殻にひびが走る。",
        st(1620, 109, 30, 0.19, 18, 9), 40,
        {"bite": {"power": 1.1, "windup": 0.6, "cooldown": 1.8, "range": 2.4},
         "charge": {"power": 1.6, "windup": 1.2, "cooldown": 8.0, "range": 10.0, "min_range": 4.0, "width": 1.4, "speed": 0.8,
                    "recover": 1.2}},
        {"physical_taken": 0.35, "magic_taken": 1.6},
        "crystal_beetle", FRUITS),
    "broken_beast": enemy(
        "欠けた聖獣（4階の雑魚。Phase 0-A 9章「神殿の守護獣がロストで欠けたもの」）。爪（claw。前の扇）と跳びかかり（charge）。"
        "HPが半分を下回ると roars 回吠えて、roar_radius の神殿の敵の攻撃を roar_attack 倍（roar_seconds 秒）。"
        "ときどき forget_chance で、守るものを忘れて立ち止まる（ロスト）。数値は【補完】",
        "欠けた聖獣", 19,
        "神殿を守っていた、白い石の毛並みと金のたてがみの守護獣。ロストに体のところどころを白く削られ、ときどき、何を守っていた"
        "のかを忘れて立ち尽くす。傷つくと吠えて、仲間を奮い立たせる。",
        st(1830, 130, 12, 0.24, 22, 11), 46,
        {"claw": {"power": 1.15, "windup": 0.7, "cooldown": 2.0, "range": 3.0, "angle": 110},
         "charge": {"power": 1.5, "windup": 1.0, "cooldown": 7.0, "range": 12.0, "min_range": 4.0, "width": 1.6, "speed": 0.95,
                    "recover": 1.0}},
        {"roars": 1, "roar_radius": 10.0, "roar_attack": 1.25, "roar_seconds": 10.0, "forget_chance": 0.04},
        "broken_beast", FRUITS),
    # ---- 名のある魔物（中ボス。宝箱と封印の守り手）
    "prayer_echo_leader": enemy(
        "祈りの導き手（4階の名のある魔物。祈りの間の奥の院の守り手。祈り手の残響の大きいもの。歌が速く、よく治す）。数値は【補完】",
        "祈りの導き手", 22,
        "祈り手たちの歌を導いていた、ひときわ大きな残響。その歌は速く、深い傷さえふさいでしまう。奥の院の祭壇を、いまも離れない。",
        st(9800, 112, 10, 0.21, 24, 13), 300,
        {"wail": {"power": 1.1, "windup": 0.8, "cooldown": 2.4, "range": 3.8},
         "hymn": {"power": 0.18, "windup": 2.2, "cooldown": 9.0, "range": 12.0}},
        {"interrupt": 0.06, "scale": 1.35},
        "prayer_echo",
        [{"item": "amemaru:prayer_stone", "chance": 35.0}, {"item": "amemaru:broken_seal_charm", "chance": 5.0},
         {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}],
        model="amemaru:prayer_echo"),
    "broken_beast_elder": enemy(
        "古き番獣（4階の名のある魔物。聖獣の庭の守り手。大きく、咆哮が2回）。数値は【補完】",
        "古き番獣", 24,
        "聖獣の庭の主。誰よりも長く神殿を守ってきたが、その体の半分は、もう白い。二度吠えて、庭の獣たちを奮い立たせる。",
        st(12700, 152, 16, 0.25, 28, 14), 360,
        {"claw": {"power": 1.35, "windup": 0.8, "cooldown": 2.2, "range": 4.0, "angle": 120},
         "charge": {"power": 1.8, "windup": 1.1, "cooldown": 6.0, "range": 16.0, "min_range": 4.0, "width": 2.2, "speed": 1.0,
                    "recover": 1.0}},
        {"roars": 2, "roar_radius": 14.0, "roar_attack": 1.3, "roar_seconds": 12.0, "forget_chance": 0.02, "scale": 1.45},
        "broken_beast",
        [{"item": "amemaru:prayer_stone", "chance": 35.0}, {"item": "amemaru:broken_seal_charm", "chance": 5.0},
         {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}],
        model="amemaru:broken_beast"),
    "silent_statue_gate": enemy(
        "門の大石像（4階の名のある魔物。地下の納骨堂の、結晶騎士団の野営の跡の前の守り手。大きく、叩きつけの範囲が広い）。数値は【補完】",
        "門の大石像", 24,
        "納骨堂の門を守る、ひときわ大きな石像。その奥には、結晶騎士団の野営の跡がある。叩きつけの揺れは、広い範囲に届く。",
        st(9800, 149, 30, 0.18, 22, 10), 340,
        {"fist": {"power": 1.4, "windup": 1.0, "cooldown": 2.6, "range": 3.6},
         "quake": {"power": 1.9, "windup": 1.5, "cooldown": 8.0, "range": 5.5}},
        {"wake_range": 7.0, "dormant_taken": 0.3, "stone_taken": 0.8, "scale": 1.5},
        "silent_statue",
        [{"item": "amemaru:prayer_stone", "chance": 35.0}, {"item": "amemaru:broken_seal_charm", "chance": 5.0},
         {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}],
        model="amemaru:silent_statue"),
    # ---- ボス
    "cantare": enemy(
        "4階のボス（Phase 0-A 9章「4体の歌い手で、歌の順番どおりに4つの音の床を踏むと無防備になる」）。ふだんは歌に守られ、受けるダメージが"
        "song.guarded 倍。歌（song）：歌い手が1人ずつ歌い（note_seconds 秒ずつ。HP30%から5音）、answer 秒のうちに同じ順番で部屋の音の床"
        "（階の定義の boss_marks.sound_plates）を踏むと、exposed 秒無防備（song.open 倍）。違う床・時間切れは不協和音（全員に power）、retry 秒で"
        "また歌う。技：嘆きの波（wave。前の扇）・高音の柱（pillars。何人かの足もとの円）・祈りの合唱（share。みんなで受ける一撃）。"
        "HP60%で祈り手の残響を2体呼ぶ（call）。HPは2人のときの基準（ひとりでも2人ぶん）。数値は【補完】（2人で18分・4人で14分ほど）",
        "祈りの残響カンターレ", 26,
        "歌の大広間で、いまも祈りの歌を指揮し続ける、顔のない残響。まわりを回る4人の歌い手（白・青・金・紅）の歌に守られている。"
        "歌い手が歌った順番どおりに4つの音の床を踏むと、歌がそろって、守りが解ける。",
        st(62000, 186, 12, 0.14, 64, 28), 1500,
        {"wave": {"power": 1.3, "windup": 1.2, "cooldown": 5.0, "range": 12.0, "angle": 120, "push": 1.0},
         "pillars": {"power": 1.25, "windup": 1.8, "cooldown": 9.0, "range": 30.0, "radius": 2.4, "count": 2},
         "song": {"power": 0.9, "windup": 0.7, "cooldown": 30.0, "first": 8.0, "note_seconds": 0.9, "answer": 12.0,
                  "exposed": 12.0, "open": 2.5, "guarded": 0.34, "retry": 8.0},
         "call": {"count": 2, "max": 4, "range": 10.0},
         "share": {"power": 180, "windup": 3.4, "cooldown": 40, "range": 3.5, "first": 30}},
        {},
        "cantare",
        [{"item": "amemaru:prayer_bell_staff", "chance": 5, "rarity": "epic"},
         {"item": "amemaru:prayer_stone", "chance": 60, "pity": False},
         {"item": "amemaru:crystal_fruit_azure", "chance": 15, "pity": False},
         {"item": "amemaru:crystal_fruit_crimson", "chance": 10, "pity": False},
         {"item": "amemaru:crystal_fruit_white", "chance": 10, "pity": False}],
        pity=True),
}


# ================================================================ 落とし物

def pool(item, chance=None, lo=1, hi=1):
    entry = {"type": "minecraft:item", "name": item}
    if hi > 1 or lo > 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    p = {"rolls": 1, "entries": [entry]}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p


LOOT = {
    "silent_statue": [pool("amemaru:statue_shard", 0.6, 1, 2), pool("amemaru:ancient_stone", 0.5, 1, 2),
                      pool("amemaru:polishing_powder", 0.2)],
    "prayer_echo": [pool("amemaru:song_crystal", 0.55), pool("amemaru:echo_fragment", 0.06), pool("amemaru:crystal_shard", 0.3)],
    "crystal_beetle": [pool("amemaru:beetle_shell", 0.5), pool("amemaru:crystal_shard", 0.5, 1, 2), pool("amemaru:moss_iron", 0.3)],
    "broken_beast": [pool("amemaru:sacred_mane", 0.45), pool("amemaru:ancient_stone", 0.4, 1, 2), pool("amemaru:echo_fragment", 0.05)],
    "cantare": [pool("amemaru:song_crystal", None, 3, 5), pool("amemaru:statue_shard", None, 2, 3),
                pool("amemaru:beetle_shell", 0.8, 1, 2), pool("amemaru:sacred_mane", 0.8, 1, 2),
                pool("amemaru:crystal_shard", None, 3, 5), pool("amemaru:clear_echo", 0.25)],
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
             "entries": [e("amemaru:statue_shard", 18, 1, 3), e("amemaru:song_crystal", 16, 1, 3), e("amemaru:beetle_shell", 10, 1, 2),
                         e("amemaru:sacred_mane", 8, 1, 2), e("amemaru:crystal_shard", 14, 1, 3), e("amemaru:ancient_stone", 12, 2, 4),
                         e("amemaru:moss_iron", 10, 2, 4), e("amemaru:polishing_powder", 8, 1, 2), e("amemaru:echo_fragment", 4, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:prayer_stone"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.15}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_amber", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_crimson", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}]},
        ],
    }


# ================================================================ 物語（4日目）

def sub(text, ticks=110, wait=True):
    return {"type": "amemaru:subtitle", "text": text, "ticks": ticks, "wait": wait}


STORY = {
    "enter": {
        "_comment": "初めて4階に入ったとき（本人だけ）。崩れた柱と浮かぶ瓦礫の神殿（Phase 0-A 9章の4階の地形・環境）。台詞は【補完】",
        "title": "崩壊した神殿",
        "trigger": {"type": "amemaru:floor_enter", "floor": 4},
        "scope": "player", "replayable": True,
        "steps": [
            {"type": "amemaru:wait", "ticks": 70},
            {"type": "amemaru:sound", "sound": "minecraft:block.amethyst_block.resonate", "volume": 1.0, "pitch": 0.7},
            sub("崩れた柱の間を、風が抜けていく。見上げると、大きな瓦礫がいくつも、音もなく空に浮かんでいた……。", 120),
            sub("どこか遠くで、祈りの歌が聞こえる。歌っているのは、いったい誰だろう。", 110, False),
        ],
    },
    "relics": {
        "_comment": "地下の納骨堂の、結晶騎士団の野営の跡で遺品を見つけたとき（本人だけ。階の定義の relics の event。遺品のブロックを"
                    "右クリックすると、冑と認識票をもらい、フラグ knight_relics_found が立ち、クエスト knight_relics が始まる）。Phase 0-A 9章の4階の物語。台詞は【補完】",
        "title": "結晶騎士団の遺品",
        "trigger": {"type": "amemaru:admin"},
        "scope": "player", "replayable": True,
        "steps": [
            {"type": "amemaru:sound", "sound": "minecraft:item.armor.equip_iron", "volume": 1.0, "pitch": 0.8},
            sub("古い野営の跡に、白い冑がひとつ。そばに、鎖のついた小さな認識票が落ちていた。", 110),
            sub("認識票には「結晶騎士団　副団長　ハロルド」と刻まれている。……ガルドに見せよう。", 120, False),
        ],
    },
    "white_murals": {
        "_comment": "Phase 0-A 9章の4階のロストの侵食：壁画の人物が何人か白く消されている（崩れた鐘楼の白い壁画の間。階の定義の story_zones の"
                    "white_murals）。入った人に、一人一度だけ（本人の物語フラグ white_murals_seen）。台詞の2行目は確定の言葉",
        "title": "白い壁画",
        "trigger": {"type": "amemaru:floor_zone", "floor": 4, "zone": "white_murals"},
        "scope": "player", "once": False, "replayable": True,
        "conditions": {"flags_none": ["amemaru:story/white_murals_seen"]},
        "steps": [
            {"type": "amemaru:sound", "sound": "minecraft:block.amethyst_block.chime", "volume": 1.0, "pitch": 0.5},
            sub("壁画の人物が、何人か白く消されている。削られたのではない。はじめから、そこにいなかったかのように。", 120),
            sub("誰かが、誰かを忘れた跡――。", 100, False),
            {"type": "amemaru:set_flags", "add": ["amemaru:story/white_murals_seen"]},
        ],
    },
    "knight_camp": {
        "_comment": "地下の納骨堂の、結晶騎士団の野営の跡に初めて入ったとき（本人だけ。一人一度）。台詞は【補完】",
        "title": "野営の跡",
        "trigger": {"type": "amemaru:floor_zone", "floor": 4, "zone": "knight_camp"},
        "scope": "player", "once": False, "replayable": True,
        "conditions": {"flags_none": ["amemaru:story/knight_camp_seen"]},
        "steps": [
            sub("崩れた天幕と、立てかけたままの白い旗。人の手で作った野営の跡だ。古代のものではない。", 110),
            sub("奥に、何かが大事そうに置かれている。", 80, False),
            {"type": "amemaru:set_flags", "add": ["amemaru:story/knight_camp_seen"]},
        ],
    },
    "first_kill": {
        "_comment": "4階のボスの初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。2行目は古代記録「神殿の碑文」の壁画と同じ。台詞は【補完】",
        "title": "祈りの残響",
        "trigger": {"type": "amemaru:floor_first_kill", "floor": 4},
        "scope": "party", "once": True, "replayable": True,
        "steps": [
            {"type": "amemaru:wait", "ticks": 50},
            {"type": "amemaru:hud", "hide": True},
            {"type": "amemaru:sound", "sound": "minecraft:block.bell.resonate", "volume": 1.0, "pitch": 1.2},
            {"type": "amemaru:fade", "from": "none", "to": "white", "ticks": 25},
            {"type": "amemaru:fade", "from": "white", "to": "none", "ticks": 50},
            {"type": "amemaru:title", "title": "崩壊した神殿　踏破", "subtitle": "4階", "ticks": 90},
            sub("歌がやんだ。四人の歌い手は、最後に声をそろえて一つの和音を響かせ、光になって消えた……。", 120),
            sub("大広間の奥の壁に、高い塔と、その根元の輪のような装置の絵。名前を記した所だけが、削れて読めない。", 130),
            {"type": "amemaru:hud", "hide": False},
        ],
    },
}

DIALOGUES = {
    ("gard", "relics"): {
        "_comment": "4日目：結晶騎士団の遺品を見つけた人に、ガルドが15年前の第七次塔遠征を語る（Phase 0-A 9章の4階の物語・12章のガルド"
                    "「4日目 団員の遺品を受け取り、遠征を語る」・確定設定「第七次塔遠征で5階の古代守護者に挑み、ロストで団員同士が互いを認識"
                    "できなくなって連携が崩れ、壊滅。副団長ハロルドら11人を失う。騎士団を解散し、冒険者ギルドに改組した」）。"
                    "冑を預かり（クエスト knight_relics の報告）、認識票をリリアに届けるクエスト harold_tag を渡す。台詞は【補完】",
        "npc": "amemaru:gard",
        "priority": 40,
        "conditions": {"opening": 2, "flags_all": ["amemaru:story/knight_relics_found"],
                       "flags_none": ["amemaru:story/gard_expedition_told"]},
        "start": "s1",
        "nodes": {
            "s1": {"expression": "stern", "text": "……その冑を、どこで見つけた", "next": "s2"},
            "s2": {"text": "4階の神殿の、地下か。……そうか。あいつらは、あそこまで行っていたのか", "next": "s3"},
            "s3": {"text": "15年前だ。結晶騎士団は、第七次の塔遠征に出た。5階の祭壇で、古代の守護者に挑んだ", "next": "s4"},
            "s4": {"text": "戦いの途中で、ロストが来た。……隣で戦っている仲間が、誰だか分からなくなった。呼ぼうにも、名前が出てこない",
                   "next": "s5"},
            "s5": {"expression": "stern", "text": "連携は崩れた。副団長のハロルドをはじめ、11人を失った", "next": "s6"},
            "s6": {"text": "騎士団は解散して、ギルドに作り直した。……もう誰も、名前を忘れたまま死なせないためにな", "next": "s7"},
            "s7": {"text": "その認識票は、ハロルドのものだ。……リリアに届けてやってくれ。あいつの父親だ", "next": "s8"},
            "s8": {"text": "冑は、俺が預かる。……見つけてくれて、ありがとう"},
        },
        "end_actions": [
            {"type": "amemaru:report_quest", "quest": "amemaru:knight_relics"},
            {"type": "amemaru:set_flags", "add": ["amemaru:story/gard_expedition_told"]},
            {"type": "amemaru:start_quest", "quest": "amemaru:harold_tag"},
        ],
    },
    ("lilia", "harold_tag"): {
        "_comment": "4日目：父ハロルドの認識票を受け取る（Phase 0-A 12章のリリア「4日目 父の認識票を受け取る」）。クエスト harold_tag の報告"
                    "（認識票を渡す）。台詞は【補完】",
        "npc": "amemaru:lilia",
        "priority": 40,
        "conditions": {"opening": 2, "flags_all": ["amemaru:story/gard_expedition_told"],
                       "flags_none": ["amemaru:story/lilia_tag_received"]},
        "start": "s1",
        "nodes": {
            "s1": {"expression": "surprised", "text": "{player}さん？ ガルドさんから……わたしに、ですか？", "next": "s2"},
            "s2": {"expression": "surprised", "text": "……これ、父の……", "next": "s3"},
            "s3": {"text": "「結晶騎士団　副団長　ハロルド」……。父は、15年前の遠征から、帰ってこなかったんです", "next": "s4"},
            "s4": {"expression": "sad", "text": "わたし、まだ小さくて。父の顔は、もうあまり覚えていないんです。……でも、この傷には見覚えがある",
                   "next": "s5"},
            "s5": {"expression": "smile", "text": "……おかえりなさい、お父さん", "next": "s6"},
            "s6": {"expression": "smile", "text": "ありがとうございます、{player}さん。この認識票は、受付の、いちばん近い所に置いておきますね"},
        },
        "end_actions": [
            {"type": "amemaru:report_quest", "quest": "amemaru:harold_tag"},
            {"type": "amemaru:set_flags", "add": ["amemaru:story/lilia_tag_received"]},
        ],
    },
}

QUESTS = {
    "knight_relics": {
        "_comment": "4日目（4階）：結晶騎士団の遺品の冑を、ガルドに見せる（遺品を見つけると始まる。ガルドの会話で報告）【補完】",
        "name": "結晶騎士団の遺品", "kind": "story", "giver": "amemaru:gard", "report": "amemaru:gard", "board": False,
        "description": "4階の神殿の地下で、結晶騎士団の冑と認識票を見つけた。ガルドに見せよう。",
        "requires": {},
        "objectives": [{"type": "collect", "item": "amemaru:knight_helm_relic", "count": 1, "consume": True,
                        "label": "結晶騎士団の冑をガルドに見せる"}],
        "rewards": {"knots": 300, "exp": 300, "affinity": {"amemaru:gard": 20}, "flags": ["amemaru:story/knight_relics_reported"]},
        "order": 130,
    },
    "harold_tag": {
        "_comment": "4日目（4階）：副団長ハロルドの認識票を、娘のリリアに届ける（Phase 0-A 9章の4階の物語。確定）【補完】",
        "name": "父の認識票", "kind": "story", "giver": "amemaru:gard", "report": "amemaru:lilia", "board": False,
        "description": "ハロルドの認識票を、リリアに届けてくれ。あいつの父親だ。",
        "requires": {},
        "objectives": [{"type": "collect", "item": "amemaru:harold_tag", "count": 1, "consume": True,
                        "label": "ハロルドの認識票をリリアに届ける"}],
        "rewards": {"knots": 300, "exp": 300, "affinity": {"amemaru:lilia": 25, "amemaru:gard": 10},
                    "flags": ["amemaru:story/harold_tag_delivered"]},
        "order": 131,
    },
}

# ================================================================ 品と装備

PRAYER_BELL = OrderedDict([
    ("slot", "weapon"), ("generation", 1), ("series", "4階のレア装備"), ("rarity", "epic"), ("required_level", 17),
    ("jobs", ["amemaru:healer"]), ("stats", {"magic": 26, "mp": 60, "heal_power": 7}), ("durability", 560),
    ("binding", "on_equip"), ("effects", [{"type": "heal_ward", "percent": 8}]), ("rare", True),
    ("repair", [{"item": "amemaru:song_crystal", "count": 2}, {"item": "amemaru:crystal_shard", "count": 2}]),
    ("description", "祈りの残響カンターレの歌い手たちが鳴らしていた、四つの音の鈴。白い石の輪に、白・青・金・紅の結晶の鈴がさがる。"
                    "癒やしの祈りに鈴の音が重なると、受けた人のまわりに、ほんの少しの間、守りの光の膜が残る。"),
])

ITEM_TYPES_MATERIALS = [{"id": "knight_helm_relic", "rarity": "rare"}, {"id": "harold_tag", "rarity": "rare"}]

# ================================================================ 言葉

LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.hydram"] = OrderedDict([
    ("entity.amemaru.silent_statue", "沈黙の石像"),
    ("entity.amemaru.prayer_echo", "祈り手の残響"),
    ("entity.amemaru.crystal_beetle", "結晶甲虫"),
    ("entity.amemaru.broken_beast", "欠けた聖獣"),
    ("entity.amemaru.cantare", "祈りの残響カンターレ"),
])
LANG_AFTER["item.amemaru.hydram_spawn_egg"] = OrderedDict([
    ("item.amemaru.silent_statue_spawn_egg", "沈黙の石像を呼ぶ卵"),
    ("item.amemaru.prayer_echo_spawn_egg", "祈り手の残響を呼ぶ卵"),
    ("item.amemaru.crystal_beetle_spawn_egg", "結晶甲虫を呼ぶ卵"),
    ("item.amemaru.broken_beast_spawn_egg", "欠けた聖獣を呼ぶ卵"),
    ("item.amemaru.cantare_spawn_egg", "祈りの残響カンターレのスポーンエッグ"),
])
LANG_AFTER["item.amemaru.floodgate_key_shield"] = OrderedDict([
    ("item.amemaru.prayer_bell_staff", "祈り手の鈴"),
])
LANG_AFTER["item.amemaru.flow_crystal.desc"] = OrderedDict([
    ("item.amemaru.knight_helm_relic", "結晶騎士団の冑"),
    ("item.amemaru.knight_helm_relic.desc", "4階の神殿の地下、古い野営の跡に残されていた白い冑。額に、結晶騎士団の紋が刻まれている。"
                                            "ガルドに見せよう。"),
    ("item.amemaru.harold_tag", "ハロルドの認識票"),
    ("item.amemaru.harold_tag.desc", "鎖のついた小さな金属の札。「結晶騎士団　副団長　ハロルド」と刻まれ、裏に古い傷がひとすじ。"
                                     "リリアに届けよう。"),
])
LANG_AFTER["block.amemaru.floodgate_valve"] = OrderedDict([
    ("block.amemaru.mural_panel", "壁画のパネル"),
    ("block.amemaru.sound_plate", "音の床"),
    ("block.amemaru.chime_stone", "鈴の石"),
    ("block.amemaru.knight_relic", "結晶騎士団の遺品"),
])
LANG_AFTER["message.amemaru.seal.floodgates"] = OrderedDict([
    ("message.amemaru.seal.murals", "光の幕が道をふさいでいる。この階の壁画を、すべて元の絵に戻そう（%s/%s）"),
])
LANG_AFTER["message.amemaru.floodgate.all_opened"] = OrderedDict([
    ("message.amemaru.mural.display", "三つの壁画を元に戻すと、この「一枚の絵」が灯る"),
    ("message.amemaru.mural.already", "%sは、もう元の絵に戻っている"),
    ("message.amemaru.mural.progress", "%s：正しい向きのパネル %s/%s"),
    ("message.amemaru.mural.solved", "%sが元の絵に戻った！（この階の壁画：%s/%s）"),
    ("message.amemaru.mural.all", "%sの壁画が、すべて元に戻った。前室の「一枚の絵」が灯り、歌の大広間への封印がゆるんだ"),
    ("message.amemaru.song.already", "%sの扉は、もう開いている"),
    ("message.amemaru.song.listen", "鈴の石が歌い始めた……。光った音の床を、同じ順番で踏もう（%s音）"),
    ("message.amemaru.song.progress", "音がそろっていく（%s/%s）"),
    ("message.amemaru.song.done", "%sがそろった！ 奥の扉が開いた"),
    ("message.amemaru.song.wrong", "音がはずれた……。もう一度、鈴の石を鳴らそう"),
    ("message.amemaru.relic.already", "遺品は、もう受け取った"),
    ("message.amemaru.beast.roar", "聖獣が吠えた！ まわりの神殿の魔物が奮い立っている"),
])
LANG_AFTER["status.amemaru.hydram.refilled"] = OrderedDict([
    ("cast.amemaru.cantare.wave", "嘆きの波（前の扇）"),
    ("cast.amemaru.cantare.pillars", "高音の柱（足もとの円）"),
    ("cast.amemaru.cantare.song", "選律の歌 ― 歌の順番を覚えよう"),
    ("cast.amemaru.cantare.call", "祈り手を呼ぶ"),
    ("cast.amemaru.cantare.share", "祈りの合唱 ― 青い円に集まって、みんなで受けろ"),
    ("cast.amemaru.cantare.open", "歌い手が黙った！ 指揮者が無防備になっている"),
    ("status.amemaru.cantare.guarded", "歌の守り：ダメージ 1/3。歌い手の歌った順番どおりに、4つの音の床を踏もう"),
    ("status.amemaru.cantare.answer", "同じ順番で音の床を踏もう！ あと %s 秒"),
    ("status.amemaru.cantare.open", "無防備！ あと %s 秒（ダメージ 2.5倍）"),
    ("status.amemaru.cantare.song", "歌の順番：%s"),
    ("status.amemaru.note.white", "白"),
    ("status.amemaru.note.blue", "青"),
    ("status.amemaru.note.gold", "金"),
    ("status.amemaru.note.red", "紅"),
])
LANG_AFTER["boss.amemaru.hydram.tip.rage"] = OrderedDict([
    ("boss.amemaru.cantare.line.start", "顔のない指揮者が、静かに指揮棒を上げた。四人の歌い手が、まわりを回り始める"),
    ("boss.amemaru.cantare.line.song", "歌い手が、ひとりずつ歌い始めた……"),
    ("boss.amemaru.cantare.line.open", "歌がそろった！ 歌い手たちが黙り、指揮者がよろめいた"),
    ("boss.amemaru.cantare.line.discord", "音がはずれた！ 不協和音が、大広間じゅうに響きわたる"),
    ("boss.amemaru.cantare.line.recover", "指揮者が指揮棒を上げ直した。歌い手たちが、また歌い始める"),
    ("boss.amemaru.cantare.line.call", "指揮者が両腕を広げた。祈り手の残響が、どこからか集まってくる！"),
    ("boss.amemaru.cantare.line.rage", "歌が速くなった。歌い手が、五つの音を重ね始めた……！"),
    ("boss.amemaru.cantare.answer", "%s音の歌が終わった！ 同じ順番で、音の床を踏もう"),
    ("boss.amemaru.cantare.progress", "音の床：%s/%s"),
    ("boss.amemaru.cantare.opened", "指揮者が無防備になった！ %s秒の間、攻撃を集めよう"),
    ("boss.amemaru.cantare.discord", "不協和音！ 順番がちがった。次の歌を待とう"),
    ("boss.amemaru.cantare.tip.song", "指揮者は歌に守られていて、ほとんど効かない。歌い手が歌う色の順番（白・青・金・紅）を覚えて、歌が終わったら、"
                                      "部屋の同じ色の音の床を、同じ順番で踏もう。誰が踏んでもよい。手分けすると速い"),
    ("boss.amemaru.cantare.tip.answer", "今！ 歌の順番どおりに、音の床を踏もう。違う床を踏むと、全員に不協和音のダメージ"),
    ("boss.amemaru.cantare.tip.open", "今が攻めどき！ 無防備の間は、ダメージが2.5倍。時間がたつと、また歌に守られる"),
    ("boss.amemaru.cantare.tip.wave", "指揮者が両腕を引いたら、嘆きの波。前の赤い扇の外へ。後ろか横が安全"),
    ("boss.amemaru.cantare.tip.pillars", "足もとに赤い円が出たら、高音の柱が落ちてくる。円の外へ"),
    ("boss.amemaru.cantare.tip.call", "祈り手の残響は、歌で指揮者の傷を治してしまう。歌い出す前に、先に倒そう"),
    ("boss.amemaru.cantare.tip.rage", "歌が5音になり、技が速くなった。順番を声に出して、みんなで覚えよう"),
])
LANG_AFTER["effect.amemaru.move_speed"] = OrderedDict([
    ("effect.amemaru.heal_ward", "回復スキルで癒やした相手の防御を、5秒の間 +%s%%"),
    ("effect.amemaru.ally_power", "%sブロック以内の味方1人ごとに、与えるダメージ +%s%%（5人まで）"),
    ("effect.amemaru.party_ward", "%sブロック以内に味方が3人以上いると、自分と近くの味方の受けるダメージ −%s%%"),
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
            # 目じるしのあとに、前に足した同じ区切りの言葉が続いていれば、そのあとへ
            items = items[:i] + list(entries.items()) + items[i:]
            d = OrderedDict(items)
        with open(path, "w", encoding="utf-8") as fp:
            fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def merge_item_types():
    path = os.path.join(REPO, "src/main/resources/amemaru/item_types.json")
    with open(path, encoding="utf-8") as fp:
        d = json.load(fp, object_pairs_hook=OrderedDict)
    mats = [m for m in d["materials"] if m["id"] not in {x["id"] for x in ITEM_TYPES_MATERIALS}]
    at = next(i for i, m in enumerate(mats) if m["id"] == "prayer_stone") + 1
    d["materials"] = mats[:at] + [OrderedDict(x) for x in ITEM_TYPES_MATERIALS] + mats[at:]
    weapons = [w for w in d["weapons"] if w["id"] != "prayer_bell_staff"]
    at = next(i for i, w in enumerate(weapons) if w["id"] == "elder_branch_staff") + 1
    weapons.insert(at, OrderedDict([("id", "prayer_bell_staff"), ("kind", "bell_staff"), ("model", "geckolib")]))
    d["weapons"] = weapons
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor4_ancient.json"), chest_table())
    for name, data in STORY.items():
        write(os.path.join(DEFS, "story_events/floor4", name + ".json"), data)
    for (npc, name), data in DIALOGUES.items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    for name, data in QUESTS.items():
        write(os.path.join(DEFS, "quests", name + ".json"), data)
    write(os.path.join(DEFS, "equipment", "prayer_bell_staff.json"), PRAYER_BELL)
    merge_item_types()
    merge_lang()
    print("enemies", len(ENEMIES), "story", len(STORY), "dialogues", len(DIALOGUES), "quests", len(QUESTS))


if __name__ == "__main__":
    main()
