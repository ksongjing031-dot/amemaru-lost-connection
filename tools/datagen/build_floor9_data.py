"""9階「氷結した王城」のデータ（敵・落とし物・宝箱・物語・ガルドの会話・ボスのレア・素材・名前の札・女王の書簡・言葉）を書き出す（11月28日）。

  python3 tools/datagen/build_floor9_data.py      # tools/mapgen/build_floor9.py で floors/floor9.json を作ってから
  python3 tools/datagen/add_party_attacks.py       # ボスの全員を狙う技（一斉の印・大技）を書き足す

Phase 0-A 9章の9階（確定）：凍った王城。大広間、凍った兵士、王家の書庫。王家の肖像画の顔はすべて空白。雑魚は凍れる近衛兵・氷の騎兵・
書架霊（ページが舞う亡霊。魔法攻撃）・霜の猟犬・忘れられた侍女。ボスは氷の近衛長ヴァルグリム（今も女王を守っている近衛隊長の残響。
部下を守る「盾の誓い」を使うため、部下と引き離すと崩れる。守れなかった隊長という点でガルドと重なる）。ギミックは王家の系譜（城中に
散らばった名前の札を集め、肖像画の空白に正しく戻す）。古代記録「女王の書簡（三通）」（records/f09_queen_letters.json。22章：9階の
隠し部屋にある女王の書簡3通は、10階の女王を倒す前に玉座へ届けると、ユニーク「イゼルナの雫」の隠し条件になる）。物語は9日目：ガルドが
団員の名前を思い出せなくなる（物語フラグ ch2/gard_forgot。10日目の撃破 ch2/queen_down で戻る）。ロストの侵食：名前と顔の空白。
ボスのレア（20章）：近衛の誓盾（盾：近くの味方の被ダメージを少し肩代わり）4%。9日目の夜の襲撃（15章）は、別のPRで作る【提案】。
数値・台詞・素材・名のある魔物・王家の人々の名前と肖像画の絵柄は【補完】。
"""
import json
import os
import sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_floor4_data import DATA, DEFS, LANG, REPO, pool, st, write  # noqa: E402
from build_floor6_data import choice, flags, sub  # noqa: E402,F401

FLOOR = 9
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
SEAL = [{"item": "amemaru:royal_seal", "chance": 1.0}]

GUARD_BEHAVIOR = OrderedDict([("wake_range", 5.0), ("dormant_taken", 0.3), ("shield_taken", 0.4), ("formation_taken", 0.2),
                              ("formation_range", 4.0), ("shield_angle", 120.0), ("chill_seconds", 4.0), ("bash_push", 0.9)])

ENEMIES = OrderedDict()
ENEMIES["frozen_guard"] = enemy(
    "凍れる近衛兵（9階の雑魚。Phase 0-A 9章の9階）。凍った兵士の像にまじって、像のまま立っている（叩いても硬い：dormant_taken。"
    "近づく wake_range・叩くと目を覚ます。誰もいないと、元の場所でまた像になる。enemy/castle/FrozenGuard.java）。斧槍の突き（thrust。"
    "前の細い扇。凍える）と盾打ち（bash。前の扇。押し返す bash_push）。塔の盾：前（shield_angle 度）から受けるダメージは shield_taken、"
    "formation_range の中にほかの近衛兵がいる（隊列）と formation_taken。横か後ろから攻める。数値は【補完】",
    "凍れる近衛兵", 36,
    "氷に覆われたまま、今も城を守っている近衛の兵。青い外套に、氷の斧槍と塔の盾。兜の中の顔は、白く抜けている。"
    "――隊列を組めば、ひとりの盾は、みんなの盾になる。",
    st(2600, 236, 30, 0.22, 24, 10), 76,
    OrderedDict([("thrust", {"power": 1.3, "windup": 1.0, "cooldown": 4.0, "range": 3.6, "angle": 40}),
                 ("bash", {"power": 1.0, "windup": 0.8, "cooldown": 3.0, "range": 2.6, "angle": 100})]),
    GUARD_BEHAVIOR,
    "frozen_guard", FRUITS + SEAL)
ENEMIES["valgrim_guard"] = enemy(
    "近衛長の近衛（9階のボス、氷の近衛長ヴァルグリムの部下。見た目は凍れる近衛兵。enemy/boss/Valgrim.java が出す）。近衛長から"
    "oath.range の中にいる間は、盾の誓いでつながって傷つかない。図鑑には出さず、落とし物もない。数値は【補完】",
    "近衛長の近衛", 40,
    "近衛長ヴァルグリムに盾の誓いを立てた近衛。隊長から離れると、誓いの光の鎖が切れる。",
    st(9000, 214, 30, 0.24, 48, 32), 0,
    OrderedDict([("thrust", {"power": 1.1, "windup": 1.0, "cooldown": 4.0, "range": 3.6, "angle": 40}),
                 ("bash", {"power": 0.9, "windup": 0.8, "cooldown": 3.5, "range": 2.6, "angle": 100})]),
    GUARD_BEHAVIOR,
    "valgrim_guard", [], model="amemaru:frozen_guard", bestiary=False)
ENEMIES["ice_cavalry"] = enemy(
    "氷の騎兵（9階の雑魚。Phase 0-A 9章の9階）。凍った馬に乗った近衛の騎士（enemy/castle/IceCavalry.java）。槍の突撃（charge。1階の"
    "欠け牙の狼と同じ突進。当たると凍える）。線の途中で壁にぶつかると、馬が転んでよろめく（stagger_seconds 秒動けず、受けるダメージ"
    " stagger_taken 倍。突撃を壁ぎわへ誘うのが攻略）。まわりにいる人へ、蹄の踏み鳴らし（stomp。まわり全部の円）。数値は【補完】",
    "氷の騎兵", 36,
    "凍った馬に乗った、近衛の騎士。馬も乗り手も、たてがみの先まで氷に覆われている。城の石畳を駆ける蹄の音だけが、今も昔のまま響く。",
    st(3200, 240, 22, 0.30, 24, 14), 80,
    OrderedDict([("charge", {"power": 1.7, "windup": 1.2, "cooldown": 9.0, "range": 18.0, "min_range": 6.0, "speed": 0.95, "width": 1.4,
                             "recover": 1.0}),
                 ("stomp", {"power": 1.1, "windup": 0.9, "cooldown": 4.0, "range": 3.0, "angle": 360})]),
    {"stagger_seconds": 3.0, "stagger_taken": 1.5, "chill_stacks": 1, "chill_seconds": 4.0},
    "ice_cavalry", FRUITS + SEAL)
ENEMIES["ice_cavalry_bearer"] = enemy(
    "霜蹄の旗手（9階の名のある魔物。西の練兵場の宝箱の守り手）。氷の騎兵の旗手。続けて chain 回突撃し、HPが半分を切ると旗を振って号令を"
    "かけ、凍った兵を call 体目覚めさせる（練兵場の碑「霜蹄の旗手が、今も号令を待つ」）。数値は【補完】",
    "霜蹄の旗手", 40,
    "近衛騎兵の旗を背負った、大きな氷の騎兵。青い旗は凍って、ひるがえる形のまま止まっている。号令をかける声は、もう誰にも届かない。",
    st(24000, 252, 26, 0.31, 28, 16), 600,
    OrderedDict([("charge", {"power": 2.0, "windup": 1.1, "cooldown": 7.0, "range": 22.0, "min_range": 6.0, "speed": 1.05, "width": 2.0,
                             "recover": 0.8}),
                 ("stomp", {"power": 1.3, "windup": 0.9, "cooldown": 3.5, "range": 3.6, "angle": 360})]),
    {"chain": 2, "call": 2, "stagger_seconds": 4.0, "stagger_taken": 1.5, "chill_stacks": 2, "chill_seconds": 5.0, "scale": 1.15},
    "ice_cavalry", [{"item": "amemaru:royal_seal", "chance": 40.0}, {"item": "amemaru:bearer_tassel", "chance": 5.0},
                    {"item": "amemaru:crystal_fruit_violet", "chance": 6.0}], model="amemaru:ice_cavalry")
ENEMIES["shelf_wraith"] = enemy(
    "書架霊（9階の雑魚。Phase 0-A 9章の9階「ページが舞う亡霊。魔法攻撃」）。宙に浮いて頁を撃つ（needle。1階の結晶蜂と同じ動き）。頁は"
    "魔法で、受ける人の守りの pierce を通り抜ける（鎧を通る魔法）。当たるとロスト状態「薄れ」（fade_seconds 秒。名前が「？？？」に"
    "見える。9階のロストの侵食「名前と顔の空白」）。狙った人の足もとに印を出し、頁を渦巻かせる（storm。radius）。数値は【補完】",
    "書架霊", 35,
    "王家の書庫の本から抜け出た亡霊。ほどけた頁が、ひらひらと体のまわりを舞っている。頁に書かれていたはずの名前は、どれも白く消えている。",
    st(1500, 226, 8, 0.22, 22, 14), 72,
    OrderedDict([("needle", {"power": 0.9, "windup": 0.8, "cooldown": 2.8, "range": 14, "speed": 0.7}),
                 ("storm", {"power": 1.2, "windup": 1.4, "cooldown": 9.0, "range": 14.0, "radius": 2.5})]),
    {"keep_distance": 7, "hover_height": 2.5, "pierce": 0.5, "fade_seconds": 8.0},
    "shelf_wraith", FRUITS + SEAL, lost=True)
ENEMIES["shelf_wraith_binder"] = enemy(
    "綴じ手（9階の名のある魔物。王家の書庫の奥の書庫の宝箱の守り手）。書架霊の長。胸の前に開いた大きな本。頁の嵐に当たった人を、頁で"
    "綴じて bind_seconds 秒動けなくする。薄れが長い（fade_seconds）。数値は【補完】",
    "綴じ手", 39,
    "奥の書庫で、ほどけた頁を一枚ずつ綴じ直している大きな書架霊。綴じても綴じても、名前の書かれた頁だけが、指のあいだからこぼれ落ちる。",
    st(21000, 240, 12, 0.22, 26, 16), 580,
    OrderedDict([("needle", {"power": 1.0, "windup": 0.7, "cooldown": 2.4, "range": 16, "speed": 0.8}),
                 ("storm", {"power": 1.4, "windup": 1.3, "cooldown": 7.0, "range": 16.0, "radius": 3.0})]),
    {"keep_distance": 8, "hover_height": 3.0, "pierce": 0.5, "fade_seconds": 12.0, "bind_seconds": 2.0, "scale": 1.5},
    "shelf_wraith", [{"item": "amemaru:royal_seal", "chance": 40.0}, {"item": "amemaru:binder_bookmark", "chance": 5.0},
                     {"item": "amemaru:crystal_fruit_crimson", "chance": 6.0}], model="amemaru:shelf_wraith", lost=True)
ENEMIES["frost_hound"] = enemy(
    "霜の猟犬（9階の雑魚。Phase 0-A 9章の9階）。王城の狩りの犬（enemy/castle/FrostHound.java）。噛みつき（bite）と飛びかかり（pounce。"
    "狙った人の足もとに印を出して跳びこむ）。噛まれた人には霜の匂い（scent_seconds 秒）。pack_range の中の猟犬は、匂いのついた人を狙い、"
    "速く走り（scent_speed）、強く噛む（scent_multiplier）。しゃがんで仲間に触れると匂いが消える。数値は【補完】",
    "霜の猟犬", 35,
    "王城で飼われていた、痩せた狩りの犬。霜の毛に、近衛の青い首輪。一度かいだ匂いは、群れの全員で、どこまでも追いかける。",
    st(1700, 230, 12, 0.30, 24, 16), 70,
    OrderedDict([("bite", {"power": 1.0, "windup": 0.45, "cooldown": 1.6, "range": 2.0, "angle": 90}),
                 ("pounce", {"power": 1.3, "windup": 0.8, "cooldown": 7.0, "range": 9.0, "min_range": 4.0, "radius": 1.6})]),
    {"scent_seconds": 8.0, "scent_multiplier": 1.3, "scent_speed": 1.25, "pack_range": 16.0, "scent_threat": 30.0, "chill_seconds": 3.0},
    "frost_hound", FRUITS + SEAL)
ENEMIES["forgotten_maid"] = enemy(
    "忘れられた侍女（9階の雑魚。Phase 0-A 9章の9階）。仕える相手の名前を忘れたまま、城の仕事を続ける侍女の残響"
    "（enemy/castle/ForgottenMaid.java）。傷ついた城の敵のそばへ行き、1秒ごとに最大HPの tend.power を治す（最長 tend.windup 秒。"
    "相手の足もとに金の輪。大きな傷 interrupt でやめて逃げる）。先に倒す相手。人が近づく（flee_range）と離れる。追いつめられると、"
    "銀の盆で叩いて押し返す（tray）。数値は【補完】",
    "忘れられた侍女", 34,
    "白い前掛けの侍女の残響。手に銀の盆を持ち、今も城の廊下を行き来している。顔は白く抜け、仕えていたお方の名前も、もう思い出せない。",
    st(1600, 180, 8, 0.24, 20, 12), 66,
    OrderedDict([("tray", {"power": 0.8, "windup": 0.6, "cooldown": 3.0, "range": 2.2, "angle": 90}),
                 ("tend", {"power": 0.05, "windup": 6.0, "cooldown": 8.0, "range": 14.0})]),
    {"flee_range": 5.0, "flee_speed": 1.3, "interrupt": 0.15},
    "forgotten_maid", FRUITS + SEAL)


def valgrim_rare():
    return [{"item": "amemaru:royal_oath_shield", "chance": 4, "rarity": "epic"},
            {"item": "amemaru:royal_seal", "chance": 100, "count": 2, "pity": False},
            {"item": "amemaru:crystal_fruit_white", "chance": 20, "pity": False},
            {"item": "amemaru:crystal_fruit_azure", "chance": 15, "pity": False},
            {"item": "amemaru:crystal_fruit_violet", "chance": 15, "pity": False}]


ENEMIES["valgrim"] = enemy(
    "9階のボス、氷の近衛長ヴァルグリム（Phase 0-A 9章の9階：今も女王を守っている近衛隊長の残響。部下を守る「盾の誓い」を使うため、部下と"
    "引き離すと崩れる。守れなかった隊長という点でガルドと重なる。enemy/boss/Valgrim.java）。盾の誓い（oath）：部下の近衛"
    "（valgrim_guard。始めは4人、近衛の円のまわり）は、近衛長から range の中にいる間は傷つかず、つながった部下がいる間は近衛長も"
    "受けるダメージ ward 倍。つながりが消えると崩れる（crumble：seconds 秒動けず taken 倍。立ち上がってから regroup 秒は崩れない）。"
    "立つと、離れた部下を呼び戻し（rally）、第2段階からは部屋のふちから増援（reinforce.count）。技：thrust（前の線）・bash（前の扇。"
    "押し返す）・spears（何人かへ向けて氷の槍の列。第3段階はまわり全部 ring 本）・guard（seconds 秒、前から受けるダメージ front 倍）・"
    "march（近衛の行進：誓いが limit 秒切れないと、部屋を横切る太い帯 width。しくじったときの一撃）。第3段階（30%）「守れなかった隊長」："
    "誓いを解き、部下は砕ける。HPは2人のときの基準（ひとりでも2人ぶん）。数値は【補完】",
    "氷の近衛長ヴァルグリム", 42,
    "女王の間の扉の前に立ちつづける、近衛隊長の残響。氷の大盾と、氷の槍。隊長の兜の羽飾りだけが、凍ってもなお青い。"
    "――部下を守ると誓った。女王を守ると誓った。そのどちらも、守れなかった。",
    st(140000, 236, 24, 0.20, 64, 48), 3400,
    OrderedDict([
        ("thrust", {"power": 1.5, "windup": 1.2, "anim_windup": 1.0, "cooldown": 5.0, "range": 9.0, "width": 2.2}),
        ("bash", {"power": 1.3, "windup": 1.0, "anim_windup": 0.9, "cooldown": 6.0, "range": 5.0, "angle": 110, "push": 1.4}),
        ("spears", {"power": 1.4, "windup": 1.7, "anim_windup": 1.4, "cooldown": 12.0, "range": 22.0, "lines": 3, "ring": 8, "gap": 2.2,
                    "radius": 1.4, "first": 10.0}),
        ("guard", {"power": 0.0, "windup": 0.6, "anim_windup": 0.6, "cooldown": 20.0, "range": 0.0, "seconds": 5.0, "front": 0.25,
                   "angle": 140, "first": 16.0}),
        ("rally", {"power": 0.0, "windup": 1.2, "anim_windup": 1.2, "cooldown": 18.0, "range": 30.0, "seconds": 5.0, "first": 12.0}),
        ("march", {"power": 3.4, "windup": 3.6, "anim_windup": 3.0, "cooldown": 10.0, "range": 46.0, "width": 12.0, "limit": 45.0,
                   "push": 1.6}),
        ("oath", {"power": 0.0, "windup": 0.0, "cooldown": 0.0, "range": 12.0, "ward": 0.35}),
        ("crumble", {"power": 0.0, "windup": 0.0, "cooldown": 0.0, "range": 0.0, "seconds": 8.0, "taken": 1.5, "regroup": 6.0}),
        ("reinforce", {"power": 0.0, "windup": 0.0, "cooldown": 0.0, "range": 0.0, "count": 2}),
    ]),
    {"hp_bars": 5},
    "valgrim", valgrim_rare(), pity=True)

LOOT = {
    "frozen_guard": [pool("amemaru:guard_ice", 0.55), pool("amemaru:castle_ice", 0.2)],
    "valgrim_guard": [],
    "ice_cavalry": [pool("amemaru:ice_horseshoe", 0.6), pool("amemaru:castle_ice", 0.25)],
    "shelf_wraith": [pool("amemaru:torn_page", 0.6), pool("amemaru:castle_ice", 0.2), pool("amemaru:echo_fragment", 0.1)],
    "frost_hound": [pool("amemaru:hound_collar", 0.5), pool("amemaru:castle_ice", 0.2)],
    "forgotten_maid": [pool("amemaru:lace_scrap", 0.6), pool("amemaru:castle_ice", 0.15)],
    "valgrim": [pool("amemaru:castle_ice", None, 4, 6), pool("amemaru:royal_seal", None, 1, 2), pool("amemaru:guard_ice", None, 2, 4),
                pool("amemaru:torn_page", 0.8, 1, 2), pool("amemaru:knot_cord", 0.6, 1, 2), pool("amemaru:clear_echo", 0.4)],
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
             "entries": [e("amemaru:castle_ice", 14, 1, 3), e("amemaru:guard_ice", 12, 1, 3), e("amemaru:ice_horseshoe", 11, 1, 2),
                         e("amemaru:torn_page", 12, 1, 3), e("amemaru:lace_scrap", 11, 1, 3), e("amemaru:hound_collar", 10, 1, 2),
                         e("amemaru:frost_silver", 9, 1, 2), e("amemaru:mirror_ice", 6, 1, 2), e("amemaru:knot_cord", 9, 1, 2),
                         e("amemaru:polishing_powder", 8, 1, 3), e("minecraft:bread", 6, 1, 3), e("amemaru:echo_fragment", 5, 1, 1)]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:clear_echo"}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "amemaru:crystal_fruit_white", "weight": 3},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_azure", "weight": 2},
                                     {"type": "minecraft:item", "name": "amemaru:crystal_fruit_violet", "weight": 1}],
             "conditions": [{"condition": "minecraft:random_chance", "chance": 0.14}]},
        ],
    }


# ================================================================ ボスのレア（20章：近衛の誓盾）

SHIELD = OrderedDict([
    ("slot", "offhand"), ("generation", 2), ("series", "9階のレア装備"), ("rarity", "epic"), ("required_level", 38),
    ("jobs", ["amemaru:knight"]), ("stats", OrderedDict([("defense", 15), ("hp", 54)])), ("durability", 660), ("binding", "on_equip"),
    ("effects", [OrderedDict([("type", "oath_guard"), ("percent", 10), ("threshold", 6)])]),
    ("rare", True),
    ("repair", [{"item": "amemaru:guard_ice", "count": 2}, {"item": "amemaru:royal_seal", "count": 1}]),
    ("description", "氷の近衛長ヴァルグリムが、女王の間の扉の前で構えつづけた大盾。表に、近衛の誓いの言葉が刻まれている。"
                    "「この盾の後ろに立つ者を、わたしは忘れない」。持つ者のそばにいる仲間への攻撃を、少しだけ代わりに受け止める。"),
])


# ================================================================ 王家の系譜（名前の札・肖像画・手がかり）

# 札: (名前, 組) 王家の9人と、まぎらわしい札2つ（王家ではない人。tools/mapgen/build_floor9.py の PLATES と同じ）
PLATES = OrderedDict([
    ("bertram", ("ベルトラム", "founding")), ("rosalinde", ("ロザリンデ", "founding")), ("edmund", ("エドムント", "founding")),
    ("sigrid", ("シグリット", "chapel")), ("halvard", ("ハルヴァルト", "chapel")), ("albrecht", ("アルブレヒト", "chapel")),
    ("oswin", ("オスヴィン", "family")), ("elvira", ("エルヴィーラ", "family")), ("liselotte", ("リーゼロッテ", "family")),
    ("helga", ("ヘルガ", "")), ("tobias", ("トビアス", "")),
])

# 肖像画の絵柄（空白の顔のまわりに描かれているもの）と、名前が戻ったときの一言【補完】
PORTRAITS = OrderedDict([
    ("bertram", ("冠をかぶり、足もとに大きな礎石を置いた人の肖像。顔のところだけが、白く抜けている。",
                 "初代の王ベルトラム。凍った谷に最初の礎石を据え、この城を建てた。")),
    ("rosalinde", ("雪の花で編んだ冠をかぶった人の肖像。顔は白く抜け、冠の花だけが細かく描かれている。",
                   "初代の王妃ロザリンデ。冬にだけ咲く雪の花を、王家の印にした。")),
    ("edmund", ("机に地図を広げ、羽ペンを持った人の肖像。顔は白く抜けている。",
                "二代目の王エドムント。地図を描き、国の境と、民の村の名前をひとつずつ定めた。")),
    ("sigrid", ("竪琴を抱いた人の肖像。弦のところに、細い金が残っている。顔は白く抜けている。",
                "歌の王女シグリット。礼拝堂で、王家の歌を最初に歌った。")),
    ("halvard", ("腕に鷹をとまらせた人の肖像。鷹の目だけが、金で光っている。顔は白く抜けている。",
                 "鷹の王ハルヴァルト。鷹とともに国じゅうを巡り、村々の声を聞いた。")),
    ("albrecht", ("塔の窓辺で、望遠鏡を夜空に向けた人の肖像。顔は白く抜けている。",
                  "星読みのアルブレヒト。城の塔の上で、雪の降る夜を読みつづけた。")),
    ("oswin", ("王の杖を持ち、うしろに近衛の盾が並ぶ人の肖像。顔は白く抜けている。",
               "王オスヴィン。近衛に、盾の誓いを立てさせた王。")),
    ("elvira", ("白い百合の花束を抱いた人の肖像。顔は白く抜けている。",
                "王妃エルヴィーラ。城の庭に、白い百合を植えた。")),
    ("liselotte", ("指先に青い小鳥をとまらせた人の肖像。顔は白く抜けている。",
                   "王女リーゼロッテ。北東の塔の上で、青い小鳥を育てていた。")),
])

CLUES = OrderedDict([
    ("lineage_stele", ("建国の碑",
                       "凍った谷に、初代の王ベルトラムが、最初の礎石を据えた。\n"
                       "王妃ロザリンデは、冬にだけ咲く雪の花で冠を編み、それを王家の印とした。\n"
                       "その子エドムントは、地図を広げ、羽ペンで国の境と村々の名を書き記した。\n\n"
                       "――名を刻め。名を呼べ。名がある限り、この国は凍らない。")),
    ("lineage_book", ("王家の系譜の書",
                      "エドムント王の子らは三人。\n"
                      "竪琴を愛し、礼拝堂で王家の歌を歌った王女シグリット。\n"
                      "鷹を腕に、国じゅうを巡った王ハルヴァルト。\n"
                      "塔の上で、夜ごと星を読んだアルブレヒト。\n\n"
                      "ハルヴァルト王の子オスヴィンは、近衛に盾の誓いを立てさせた。\n"
                      "王妃エルヴィーラは白い百合を植え、長女リーゼロッテは、北東の塔で青い小鳥を育てた。\n"
                      "そして、末の娘――（ここから先の名は、霜に覆われて読めない）")),
])

GROUP_DONE = OrderedDict([
    ("founding", "「%s」がそろった！ 大広間の奥、大階段をふさいでいた氷の門が溶けていく"),
    ("chapel", "「%s」がそろった！ 肖像の回廊への扉が開いた"),
    ("family", "「%s」がそろった！ 回廊の東の端で、女王の肖像が、かすかに光った"),
])

QUEEN_NAMES = ["イゼルナ"]


# ================================================================ 物語（9日目）

def story():
    out = OrderedDict()
    out["enter"] = OrderedDict([
        ("_comment", "初めて9階に入ったとき（本人だけ）。凍った王城。大広間、凍った兵士、王家の書庫。王家の肖像画の顔はすべて空白"
                     "（Phase 0-A 9章の9階の地形・環境）。9日目：ガルドが団員の名前を思い出せなくなる（物語フラグ ch2/gard_forgot。サーバー全体。"
                     "知らせは GardMemoryService が出す。10日目の撃破 ch2/queen_down で戻る）。台詞は【補完】"),
        ("title", "氷結した王城"),
        ("trigger", OrderedDict([("type", "amemaru:floor_enter"), ("floor", FLOOR)])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:sound"), ("sound", "minecraft:block.bell.resonate"), ("volume", 0.7), ("pitch", 0.5)]),
            OrderedDict([("type", "amemaru:title"), ("title", "第2章　凍てついた記憶"), ("subtitle", "9階　氷結した王城"), ("ticks", 110)]),
            sub("雪原の向こうに、凍った王城が立っている。城壁も、塔も、旗も、氷の中で時を止めていた。", 120),
            sub("城門の前に、兵士たちが隊列のまま凍りついている。……どの兜の中にも、顔がない。", 120),
            sub("名前を呼ぶ声が、どこかで途切れた。この城では、名前と顔から先に消えていく。", 120, False),
            flags("amemaru:ch2/gard_forgot", server=True),
        ]),
    ])
    zones = [
        ("town", "城下町の跡", "城下町の跡（本人だけ。一人一度）。店の看板の名前が、どれも白く消えている【補完】",
         ["凍った城下町。店の看板が並んでいるのに、どの看板にも、名前が書かれていない。",
          "戸口に、名札をかけるための釘だけが残っている。札は、どこへ行ったのだろう。"]),
        ("soldiers", "凍った兵士の隊列", "外庭の凍った兵士の隊列（本人だけ。一人一度）。像にまじって、目を覚ます近衛兵がいる【補完】",
         ["外庭いっぱいに、凍った兵士が隊列を組んでいる。",
          "……近づくと、盾の向こうで、氷がかすかにきしんだ。全部が、ただの像ではないのかもしれない。"]),
        ("hall", "大広間", "大広間（本人だけ。一人一度）。顔のない3枚の肖像画と、建国の碑【補完】",
         ["大広間の壁に、3枚の肖像画がかかっている。どれも、顔のところだけが白く抜けている。",
          "肖像画の下には、空っぽの名札の台座。奥の建国の碑に、何かが刻まれている。",
          "城じゅうに散らばった名前の札を集めて、肖像画に戻そう（札を持って、肖像画に触れる）。"]),
        ("library", "王家の書庫", "王家の書庫（本人だけ。一人一度）。頁の舞う書架霊と、系譜の書【補完】",
         ["王家の書庫。床一面に、ほどけた頁が散らばっている。",
          "拾い上げた頁は、どれも名前のところだけが、白く消えていた。"]),
        ("gallery", "肖像の回廊", "肖像の回廊（本人だけ。一人一度）。最後の家族の肖像と、東の端の女王の肖像【補完】",
         ["長い回廊に、肖像画が並んでいる。東の端に、ひときわ大きな女王の肖像。",
          "女王の肖像の下には、名札の台座さえない。……この方の名前は、札ではなく、声で呼ぶのかもしれない。"]),
        ("study", "女王の書斎", "隠し部屋「女王の書斎」（本人だけ。一人一度）。女王の書簡3通【補完】",
         ["小さな書斎。書き物机の上に、書きかけの名前の一覧が、凍ったまま残っている。",
          "机のまわりに、手紙が三通。女王が、誰かに宛てて書いたものらしい。"]),
        ("corridor", "近衛の回廊", "近衛の間の手前（本人だけ。一人一度）。ボスの前の物語【補完】",
         ["回廊の先から、規則正しい足音が聞こえる。……誰かが、今も扉の前を守っている。",
          "「ここより先は、女王陛下の間。……名乗れ」　答える前に、声はかすれて消えた。"]),
    ]
    for zid, title, comment, lines in zones:
        steps = [sub(t, 120) for t in lines[:-1]] + [sub(lines[-1], 120, False), flags("amemaru:story/castle_%s_seen" % zid)]
        out[zid] = OrderedDict([
            ("_comment", comment),
            ("title", title),
            ("trigger", OrderedDict([("type", "amemaru:floor_zone"), ("floor", FLOOR), ("zone", zid)])),
            ("scope", "player"),
            ("once", False),
            ("replayable", True),
            ("conditions", OrderedDict([("flags_none", ["amemaru:story/castle_%s_seen" % zid])])),
            ("steps", steps),
        ])
    letters = [
        ("一通目", "わたしは、あなたたちを忘れたくない。だから毎晩、名前を書き留めています。",
         "インクのにじんだ、ていねいな字。最後に、たくさんの名前が並んでいる。"),
        ("二通目", "書き留めた名前が、朝には知らない文字に見えるのです。……あなたは、誰だったかしら。",
         "字が、ところどころ震えている。名前の一覧の半分が、線で消されている。"),
        ("三通目", "白き声が言った。忘れれば、もう誰も泣かない、と。わたしは、それでいいと思ってしまった。",
         "名前の一覧は、どこにもない。最後の一行だけが、強く書かれている。"),
    ]
    for k, (nth, text, look) in enumerate(letters, start=1):
        steps = [sub("女王の書簡（%s）を手に取った。%s" % (nth, look), 110), sub("「%s」" % text, 130, k != 3)]
        if k == 3:
            steps.append(sub("……白き声。誰かが、女王に、忘れることを勧めたのだ。", 120, False))
            steps.append(flags("amemaru:ch2/white_voice", server=True))
        out["letter_%d" % k] = OrderedDict([
            ("_comment", "女王の書斎で、女王の書簡（%s）を手に取ったとき（本人だけ。CastleBlocks.Letter → TempleService.takeRelic。持ち物"
                         "queen_letter_%d と古代記録 f09_queen_letters）。Phase 0-A 9章の9階の古代記録%s【補完】"
                         % (nth, k, "。三通目で、白き声（NULL の前触れ）の物語フラグ ch2/white_voice" if k == 3 else "")),
            ("title", "女王の書簡（%s）" % nth),
            ("trigger", OrderedDict([("type", "amemaru:admin")])),
            ("scope", "player"),
            ("replayable", True),
            ("steps", steps),
        ])
    out["queen_name"] = OrderedDict([
        ("_comment", "女王の肖像の前で、女王の名を呼び、隠し扉が初めて開いたとき（本人だけ。LineageService.callQueen）【補完】"),
        ("title", "女王の名"),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            sub("名前を呼ぶと、女王の肖像の空白の顔に、一瞬だけ、やわらかな色が差した。", 110),
            sub("肖像の横の壁が、静かに奥へ下がっていく。……隠された部屋がある。", 110, False),
        ]),
    ])
    out["lineage_done"] = OrderedDict([
        ("_comment", "王家の系譜がすべて戻ったとき（その階にいる人に。LineageService）【補完】"),
        ("title", "王家の系譜"),
        ("trigger", OrderedDict([("type", "amemaru:admin")])),
        ("scope", "player"),
        ("replayable", True),
        ("steps", [
            sub("城じゅうの肖像画に、名前が戻った。空白だった顔は、まだ白いまま。……けれど、名前を呼ぶと、少しだけ近くに感じる。", 140),
            sub("城の奥、近衛の回廊の先で、重い扉が開く音がした。", 110, False),
        ]),
    ])
    out["first_kill"] = OrderedDict([
        ("_comment", "9階のボスの初撃破（倒したパーティの画面だけ。サーバー全体には知らせが出る）。守れなかった隊長（ガルドと重なる）。"
                     "物語フラグ ch2/valgrim_down（サーバー全体。ガルドの会話）。台詞は【補完】"),
        ("title", "氷の近衛長ヴァルグリム"),
        ("trigger", OrderedDict([("type", "amemaru:floor_first_kill"), ("floor", FLOOR)])),
        ("scope", "party"),
        ("once", True),
        ("replayable", True),
        ("steps", [
            OrderedDict([("type", "amemaru:wait"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:hud"), ("hide", True)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "none"), ("to", "white"), ("ticks", 30)]),
            OrderedDict([("type", "amemaru:fade"), ("from", "white"), ("to", "none"), ("ticks", 60)]),
            OrderedDict([("type", "amemaru:title"), ("title", "氷結した王城　踏破"), ("subtitle", "第2章　凍てついた記憶"), ("ticks", 110)]),
            sub("近衛長の大盾が、扉の前で静かに倒れた。", 110),
            sub("「……陛下。わたしは、隊の者たちの名を……最後まで、呼べませんでした」", 130),
            sub("氷が砕ける音とともに、隊長の残響は消えた。扉の向こうから、冷たい風が吹きこんでくる。", 130),
            sub("守れなかった隊長。……街のギルドにも、同じ痛みを抱えた人がいる。", 120),
            OrderedDict([("type", "amemaru:hud"), ("hide", False)]),
            flags("amemaru:ch2/valgrim_down", server=True),
        ]),
    ])
    return out


# ================================================================ ガルドの会話（9日目：団員の名前を思い出せなくなる）

def dialogues():
    out = OrderedDict()
    out[("gard", "forgot")] = OrderedDict([
        ("_comment", "9日目から（物語フラグ ch2/gard_forgot。9階に誰かが初めて入ったとき）、ガルドは結晶騎士団の団員の名前を思い出せない"
                     "（Phase 0-A 9章の9階の物語「ガルドが団員の名前を思い出せなくなる」・12章のガルド「9日目 団員の名前を思い出せなくなる → "
                     "10日目 戻る」）。ハロルドの名前は、リリアに届けた認識票のおかげで覚えている【補完】。10日目の撃破（ch2/queen_down）で戻る。"
                     "台詞は【補完】"),
        ("npc", "amemaru:gard"),
        ("priority", 38),
        ("conditions", OrderedDict([("opening", 2), ("flags_all", ["amemaru:ch2/gard_forgot"]),
                                    ("flags_none", ["amemaru:ch2/queen_down", "amemaru:story/gard_forgot_seen"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("expression", "stern"), ("text", "……戻ったか。生きて戻れ、とは言ったが……そうか"), ("next", "s2")])),
            ("s2", OrderedDict([("text", "今朝、いつものように、騎士団の連中の名前を呼ぼうとした。15年、毎朝、ひとりずつだ"), ("next", "s3")])),
            ("s3", OrderedDict([("text", "ハロルドは呼べた。あいつの認識票は、リリアの胸にある。……だが、ほかの10人が、出てこない"),
                                ("next", "s4")])),
            ("s4", OrderedDict([("text", "顔は浮かぶ。笑い方も、背中も、盾の構え方も覚えている。名前だけが、白く抜けている"), ("next", "s5")])),
            ("s5", OrderedDict([("expression", "stern"),
                                ("text", "仲間に名前を忘れられる怖さを、俺は誰よりも知っているはずだった。……情けない話だ"), ("next", "s6")])),
            ("s6", OrderedDict([("text", "9階の城には、今も女王を守っている近衛の隊長がいるそうだな"),
                                ("choices", [
                                    choice("必ず、思い出せる", "a1"),
                                    choice("その隊長と、戦ってくる", "a2"),
                                ])])),
            ("a1", OrderedDict([("text", "……ああ。思い出すまで、毎朝呼び続ける。名前の出てこない名前をな"), ("next", "s7")])),
            ("a2", OrderedDict([("text", "守れなかった者が、守り続けている、か。……他人事とは思えん"), ("next", "s7")])),
            ("s7", OrderedDict([("text", "行け。俺のことはいい。……生きて戻れ。それが命令だ")])),
        ])),
        ("end_actions", [
            OrderedDict([("type", "amemaru:set_flags"), ("add", ["amemaru:story/gard_forgot_seen"])]),
        ]),
    ])
    out[("gard", "forgot_again")] = OrderedDict([
        ("_comment", "9日目から、忘れていることをもう聞いた人に（短い会話）。台詞は【補完】"),
        ("npc", "amemaru:gard"),
        ("priority", 37),
        ("conditions", OrderedDict([("opening", 2), ("flags_all", ["amemaru:ch2/gard_forgot", "amemaru:story/gard_forgot_seen"]),
                                    ("flags_none", ["amemaru:ch2/queen_down"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("expression", "stern"), ("text", "……まだ、出てこない。紙に書いておいた名前さえ、知らない文字に見える"),
                                ("next", "s2")])),
            ("s2", OrderedDict([("text", "それでも呼ぶ。呼べない名前を、呼び続ける。……お前たちは、塔のことだけ考えろ。生きて戻れ")])),
        ])),
    ])
    out[("gard", "captain")] = OrderedDict([
        ("_comment", "9階のボス（氷の近衛長ヴァルグリム）が倒されたあと（ch2/valgrim_down）、忘れている間のガルドが、守れなかった隊長に"
                     "自分を重ねる（Phase 0-A 9章の9階「守れなかった隊長という点でガルドと重なる」）。台詞は【補完】"),
        ("npc", "amemaru:gard"),
        ("priority", 42),
        ("conditions", OrderedDict([("opening", 2), ("flags_all", ["amemaru:ch2/gard_forgot", "amemaru:ch2/valgrim_down"]),
                                    ("flags_none", ["amemaru:ch2/queen_down", "amemaru:story/gard_captain_told"])])),
        ("start", "s1"),
        ("nodes", OrderedDict([
            ("s1", OrderedDict([("expression", "stern"), ("text", "近衛の隊長を、倒したそうだな"), ("next", "s2")])),
            ("s2", OrderedDict([("text", "最後に、なんと言っていた"),
                                ("choices", [choice("隊の者たちの名を、呼べなかったと", "s3")])])),
            ("s3", OrderedDict([("text", "……そうか"), ("next", "s4")])),
            ("s4", OrderedDict([("text", "俺も、15年前は呼べなかった。ロストで、隣の仲間が誰だか分からなくなった。……そして今、また呼べない"),
                                ("next", "s5")])),
            ("s5", OrderedDict([("text", "だが、その隊長は、最後まで扉の前に立っていたんだろう。誰も守れなくても、立っていた"), ("next", "s6")])),
            ("s6", OrderedDict([("expression", "stern"), ("text", "なら、俺も立っている。名前を思い出すまで、ここで、お前たちを送り出す")])),
        ])),
        ("end_actions", [
            OrderedDict([("type", "amemaru:set_flags"), ("add", ["amemaru:story/gard_captain_told"])]),
        ]),
    ])
    return out


# ================================================================ 素材・名前の札・女王の書簡

MATERIALS = OrderedDict([
    ("castle_ice", ("王城の霜氷", "uncommon",
                    "王城の石の継ぎ目から削り取った、青白い霜の氷。指でこすると、遠い楽の音のようにきしむ。9階の装備とアクセサリーの材料になる。")),
    ("guard_ice", ("近衛の氷", "common",
                   "凍れる近衛兵の鎧から剥がれた、青みがかった氷。盾の形に割れていることが多い。9階のアクセサリーの材料になる。")),
    ("ice_horseshoe", ("氷の蹄鉄", "common",
                       "氷の騎兵の馬が落とした蹄鉄。鉄ではなく、氷でできている。9階のアクセサリーの材料になる。")),
    ("torn_page", ("ほどけた頁", "common",
                   "書架霊の体からこぼれた、古い本の頁。文字は読めるのに、名前のところだけが白く消えている。9階のアクセサリーの材料になる。")),
    ("hound_collar", ("猟犬の青い首輪", "common",
                      "霜の猟犬がつけていた、近衛の青い首輪。名札の金具だけが残り、名前は消えている。9階のアクセサリーの材料になる。")),
    ("lace_scrap", ("前掛けのレース", "uncommon",
                    "忘れられた侍女の白い前掛けの、ほつれたレース。洗いたての石けんの匂いが、まだ少しだけ残っている。9階のアクセサリーの材料になる。")),
    ("royal_seal", ("王家の封蝋", "rare",
                    "雪の花の印が押された、青い封蝋のかけら。王家の手紙に使われていた。9階の名のある魔物とボスが持つ。")),
])

# 名前の札（王家の系譜の札。札の台から取る。肖像画に戻すと消える）
PLATE_ITEMS = OrderedDict()
for _pid, (_name, _group) in PLATES.items():
    PLATE_ITEMS["name_plate_" + _pid] = (
        "%sの名札" % _name, "uncommon",
        "王城のどこかにあった、銀の名札。「%s」と刻まれている。この名前の人の肖像画を探して、空白の名札の台座に戻そう"
        "（札を手に持って、肖像画に触れる）。違う肖像画に入れると、はじかれる。" % _name)

LETTER_ITEMS = OrderedDict([
    ("queen_letter_1", ("女王の書簡（一通目）", "rare",
                        "女王の書斎にあった手紙。「わたしは、あなたたちを忘れたくない。だから毎晩、名前を書き留めています」。"
                        "最後に、たくさんの名前が並んでいる。")),
    ("queen_letter_2", ("女王の書簡（二通目）", "rare",
                        "女王の書斎にあった手紙。「書き留めた名前が、朝には知らない文字に見えるのです。……あなたは、誰だったかしら」。"
                        "名前の一覧の半分が、線で消されている。")),
    ("queen_letter_3", ("女王の書簡（三通目）", "rare",
                        "女王の書斎にあった手紙。「白き声が言った。忘れれば、もう誰も泣かない、と。わたしは、それでいいと思ってしまった」。"
                        "……この手紙は、女王のもとへ届けるべきなのかもしれない。")),
])


# ================================================================ 言葉

def portrait_lang():
    out = OrderedDict()
    for pid, (blank, named) in PORTRAITS.items():
        out["lineage.amemaru.portrait.%s.name" % pid] = PLATES[pid][0]
        out["lineage.amemaru.portrait.%s.blank" % pid] = blank
        out["lineage.amemaru.portrait.%s.named" % pid] = named
    out["lineage.amemaru.portrait.blank_title"] = "顔のない肖像画"
    out["lineage.amemaru.portrait.blank_hint"] = "名札の台座は空っぽだ。この人の名前の札を手に持って、もう一度触れよう（城のどこかにある札の台から取れる）。"
    for cid, (title, text) in CLUES.items():
        out["lineage.amemaru.clue.%s.title" % cid] = title
        out["lineage.amemaru.clue.%s" % cid] = text
    out["lineage.amemaru.queen.title"] = "女王の肖像"
    out["lineage.amemaru.queen.blank"] = ("白い冠の女王の肖像。顔だけでなく、名札の台座さえない。……まだ、何かが足りない気がする"
                                         "（回廊の「最後の家族」の名前を、先に戻そう）。")
    out["lineage.amemaru.queen.ready"] = ("白い冠の女王の肖像。空白の顔が、こちらを見ている気がする。この方には札がない。"
                                         "……名前を、声に出して呼んでみよう（肖像の前で、チャットに女王の名前を書く）。")
    return out


LANG_AFTER = OrderedDict()
LANG_AFTER["entity.amemaru.mune"] = OrderedDict([
    ("entity.amemaru.frozen_guard", "凍れる近衛兵"), ("entity.amemaru.ice_cavalry", "氷の騎兵"), ("entity.amemaru.shelf_wraith", "書架霊"),
    ("entity.amemaru.frost_hound", "霜の猟犬"), ("entity.amemaru.forgotten_maid", "忘れられた侍女"),
    ("entity.amemaru.valgrim", "氷の近衛長ヴァルグリム"),
])
LANG_AFTER["item.amemaru.mune_spawn_egg"] = OrderedDict([
    ("item.amemaru.frozen_guard_spawn_egg", "凍れる近衛兵のスポーンエッグ"), ("item.amemaru.ice_cavalry_spawn_egg", "氷の騎兵のスポーンエッグ"),
    ("item.amemaru.shelf_wraith_spawn_egg", "書架霊のスポーンエッグ"), ("item.amemaru.frost_hound_spawn_egg", "霜の猟犬のスポーンエッグ"),
    ("item.amemaru.forgotten_maid_spawn_egg", "忘れられた侍女のスポーンエッグ"),
    ("item.amemaru.valgrim_spawn_egg", "氷の近衛長ヴァルグリムのスポーンエッグ"),
])
LANG_AFTER["boss.amemaru.mune.tip.wave"] = OrderedDict([
    ("boss.amemaru.intro.valgrim", "第9階層「氷結した王城」　近衛の間"),
    ("cast.amemaru.valgrim.thrust", "槍の突き（前の線）"), ("cast.amemaru.valgrim.bash", "盾打ち（前の扇・押し返す）"),
    ("cast.amemaru.valgrim.spears", "氷槍の列（足もとから、槍の列）"), ("cast.amemaru.valgrim.guard", "誓いの構え（前からの攻撃が効かない）"),
    ("cast.amemaru.valgrim.rally", "集え（離れた部下を呼び戻す）"), ("cast.amemaru.valgrim.march", "近衛の行進（部屋を横切る太い帯）"),
    ("cast.amemaru.valgrim.crumble", "誓いが崩れた（膝をつく）"),
    ("cast.amemaru.valgrim.volley", "氷の号令（全員の足もと）"),
    ("cast.amemaru.valgrim.nova_out", "大盾の踏み込み ― 近衛長から離れろ"),
    ("cast.amemaru.valgrim.nova_safe", "吹雪の陣 ― 部屋の金の円へ"),
    ("status.amemaru.valgrim.oath", "盾の誓い ― 部下 %2$s 人とつながっている（部下は傷つかず、近衛長への傷も少ない）。部下を近衛長から"
                                    "引き離そう。近衛の行進まで %1$s 秒"),
    ("status.amemaru.valgrim.open", "誓いが崩れた！ 近衛長が膝をついている。攻めどき（あと %1$s 秒）"),
    ("status.amemaru.valgrim.guard", "誓いの構え ― 前からの攻撃は、ほとんど効かない。後ろへ回ろう（あと %1$s 秒）"),
    ("boss.amemaru.valgrim.line.start", "「ここより先は、女王陛下の間。……近衛、構え！」 氷の盾が、いっせいに鳴った"),
    ("boss.amemaru.valgrim.line.phase2", "「隊列を崩すな！ 誰ひとり、陛下の前へ通すな！」"),
    ("boss.amemaru.valgrim.line.phase3", "「……もう、守る者はいない。部下も、陛下も。わたしは、誰も守れなかった」 誓いの鎖が、音を立てて砕けた"),
    ("boss.amemaru.valgrim.line.rally", "「近衛、集え！」"),
    ("boss.amemaru.valgrim.line.reinforce", "部屋のふちの氷が割れ、新しい近衛が現れた。近衛長のもとへ歩いていく"),
    ("boss.amemaru.valgrim.line.crumble", "盾の誓いが切れた！ 近衛長が、膝をついた"),
    ("boss.amemaru.valgrim.line.rise", "「……まだだ。誓いは、まだ終わっていない」 近衛長が立ち上がる"),
    ("boss.amemaru.valgrim.line.march", "「近衛、前へ！」 隊列が、足並みをそろえて動きだす"),
    ("boss.amemaru.valgrim.line.defeat", "「……陛下。どうか、お名前を……」"),
    ("boss.amemaru.valgrim.tip.oath", "盾の誓い：近衛長のそばにいる部下は、青い光の鎖でつながって傷つかない。部下を引きつけて、近衛長から離そう"
                                      "（離れた部下は傷つく）。つながりがすべて消えると、近衛長は崩れる"),
    ("boss.amemaru.valgrim.tip.blocked", "鎖でつながった部下には、攻撃が効かない。近衛長から12ブロックより遠くへ引き離そう"),
    ("boss.amemaru.valgrim.tip.separate", "鎖が切れた！ 離れた部下は、もう守られていない"),
    ("boss.amemaru.valgrim.tip.crumble", "近衛長が崩れた！ しばらく動けず、受けるダメージが増える。いまのうちに攻めよう"),
    ("boss.amemaru.valgrim.tip.reinforce", "増援：部屋のふちから、新しい近衛が近衛長のもとへ歩いてくる。たどり着く前に止めよう"),
    ("boss.amemaru.valgrim.tip.spears", "氷槍の列：足もとに並んだ赤い円から、氷の槍が近い順に突き出す。列の横へよけよう"),
    ("boss.amemaru.valgrim.tip.guard", "誓いの構え：前からの攻撃は、ほとんど効かない。うしろへ回ろう"),
    ("boss.amemaru.valgrim.tip.march", "近衛の行進：誓いが長く切れないと、部屋を横切る太い帯を進む（とても強い）。帯の外へ逃げよう。"
                                       "部下を引き離して、誓いを切れば止まる"),
    ("boss.amemaru.valgrim.tip.release", "守れなかった隊長：誓いを解いた近衛長は、ひとりで、速く強く戦う。氷槍の列が、まわり全部へ"),
])
LANG_AFTER["message.amemaru.seal.bells"] = OrderedDict([
    ("message.amemaru.seal.lineage", "近衛の間の扉は、まだ閉じている。王家の肖像画に、名前を戻そう（%s/%s）"),
])
LANG_AFTER["message.amemaru.lost.lost_silence"] = OrderedDict([
    ("message.amemaru.lineage.plate_have", "「%s」の名札は、もう持っている"),
    ("message.amemaru.lineage.plate_take", "名前の札を手に取った：「%s」"),
    ("message.amemaru.lineage.restored", "肖像画に名前が戻った：%s（%s %s/%s）"),
    ("message.amemaru.lineage.restored_line", "……%s。名前を呼ぶと、白い顔が、少しだけ近くに感じた"),
    ("message.amemaru.lineage.wrong", "「%s」の札は、この肖像画にはまらない。札がはじかれ、近衛兵が目を覚ました！"),
    ("message.amemaru.lineage.all_done", "王家の系譜が、すべて戻った（肖像画%s枚）"),
    ("message.amemaru.lineage.clue_read", "%sが「%s」を読んだ"),
    ("message.amemaru.lineage.queen_locked", "女王の肖像は、こたえない。……まだ、ほかの家族の名前が戻っていない"),
    ("message.amemaru.lineage.queen_open", "女王の肖像の横の壁が、奥へ下がった！（%s秒で閉じる。中からは留め金で開けられる）"),
    ("message.amemaru.lineage.queen_close", "女王の肖像の横の壁が、もとに戻った"),
] + [("message.amemaru.lineage.group_done." + g, t) for g, t in GROUP_DONE.items()] + [
    ("message.amemaru.frozen_guard.shield", "塔の盾にはじかれた！ 近衛兵は、前からの攻撃に強い（隊列を組むと、もっと硬い）。横か後ろから攻めよう"),
    ("message.amemaru.frost_hound.scent", "霜の匂いがついた！ 霜の猟犬の群れが、あなたを追ってくる（仲間のそばでしゃがむと消える）"),
    ("message.amemaru.frost_hound.scent_cleared", "仲間に触れて、霜の匂いが消えた"),
    ("message.amemaru.shelf_wraith.bound", "頁に綴じられて、動けない！"),
    ("message.amemaru.forgotten_maid.last_words", "侍女の残響「……ひめさまの、お名前を……お呼びしなくては……」"),
    ("message.amemaru.gard.forgot", "ギルドマスターのガルドが、結晶騎士団の仲間たちの名前を思い出せなくなった……"),
    ("message.amemaru.gard.forgot_detail", "「顔は浮かぶ。名前だけが、白く抜けている」 ギルドでガルドに話しかけよう"),
])
LANG_AFTER["commands.amemaru.floor.bells_solved"] = OrderedDict([
    ("commands.amemaru.floor.no_lineage", "%s階には、王家の系譜がありません"),
    ("commands.amemaru.floor.no_lineage_group", "肖像画の組「%s」はありません（founding・chapel・family か all）"),
    ("commands.amemaru.floor.lineage_solved", "%s階の肖像画に名前を戻しました（%s/%s）"),
    ("commands.amemaru.floor.lineage_reset", "肖像画を、朝の空白に戻しました（札も台に戻ります）"),
    ("commands.amemaru.floor.lineage_queen", "%s階の女王の肖像の隠し扉を開けました"),
])
LANG_AFTER["commands.amemaru.solo.part.bells"] = OrderedDict([
    ("commands.amemaru.solo.part.lineage", "王家の系譜"),
])
LANG_AFTER["block.amemaru.lake_tablet"] = OrderedDict([
    ("block.amemaru.royal_portrait", "王家の肖像画"), ("block.amemaru.name_plate_stand", "名前の札の台"),
    ("block.amemaru.lineage_stele", "建国の碑"), ("block.amemaru.lineage_book", "王家の系譜の書"),
    ("block.amemaru.queen_letter", "女王の書簡"), ("block.amemaru.study_latch", "書斎の留め金"),
])
LANG_AFTER["effect.amemaru.warning_ear"] = OrderedDict([
    ("effect.amemaru.oath_guard", "%sブロック以内の味方が受けるダメージの%s%%を、代わりに受ける"),
])
LANG_AFTER["item.amemaru.lake_lord_whisker_bow"] = OrderedDict([
    ("item.amemaru.royal_oath_shield", "近衛の誓盾"),
])


def item_lang():
    out = OrderedDict()
    for table in (MATERIALS, PLATE_ITEMS, LETTER_ITEMS):
        for mid, (name, _r, desc) in table.items():
            out["item.amemaru." + mid] = name
            out["item.amemaru." + mid + ".desc"] = desc
    return out


def merge_lang():
    LANG_AFTER["item.amemaru.lake_shrine_tablet.desc"] = item_lang()
    LANG_AFTER["message.amemaru.lineage.queen_close"] = portrait_lang()
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
    ours = list(MATERIALS) + list(PLATE_ITEMS) + list(LETTER_ITEMS)
    mats = [m for m in d["materials"] if m["id"] not in ours]
    for table in (MATERIALS, PLATE_ITEMS, LETTER_ITEMS):
        for mid, (_name, rarity, _desc) in table.items():
            mats.append(OrderedDict([("id", mid), ("rarity", rarity)]))
    d["materials"] = mats
    shields = [s for s in d["shields"] if s["id"] != "royal_oath_shield"]
    shields.append(OrderedDict([("id", "royal_oath_shield")]))
    d["shields"] = shields
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def item_models():
    for table in (MATERIALS, PLATE_ITEMS, LETTER_ITEMS):
        for mid in table:
            texture = mid
            if mid.startswith("name_plate_"):
                texture = "name_plate"
            elif mid.startswith("queen_letter_"):
                texture = "queen_letter_item"
            write(os.path.join(ASSETS, "models/item", mid + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + texture}})
    for egg in ("frozen_guard", "ice_cavalry", "shelf_wraith", "frost_hound", "forgotten_maid", "valgrim"):
        write(os.path.join(ASSETS, "models/item", egg + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def main():
    for name, data in ENEMIES.items():
        write(os.path.join(DEFS, "enemies", name + ".json"), data)
    for name, pools in LOOT.items():
        write(os.path.join(DATA, "loot_tables/entities", name + ".json"), {"type": "minecraft:entity", "pools": pools})
    write(os.path.join(DATA, "loot_tables/chests/floor9_castle.json"), chest_table())
    for name, data in story().items():
        write(os.path.join(DEFS, "story_events/floor9", name + ".json"), data)
    for (npc, name), data in dialogues().items():
        write(os.path.join(DEFS, "dialogues", npc, name + ".json"), data)
    write(os.path.join(DEFS, "equipment", "royal_oath_shield.json"), SHIELD)
    merge_item_types()
    merge_lang()
    item_models()
    print("enemies", len(ENEMIES), "story", len(story()), "dialogues", len(dialogues()), "items",
          len(MATERIALS) + len(PLATE_ITEMS) + len(LETTER_ITEMS))


if __name__ == "__main__":
    main()
