"""モンク（格闘・連撃／拳装備／専用ゲージ「気」：続けて当てるとたまる／流派：剛・柔）。

11月22日のご指示「モンクなどにはコンボを用意し、複数のスキルを回すと火力が上がる」を「連撃」として作る【提案】：
- 【起】のスキルで連撃が始まる（連撃1）。始まった後は、【起】【継】のスキルを「前と違うスキル」で3秒以内に続けると、連撃が1つずつ増える（最大5）。
- 連撃1つにつき、モンクのスキルのダメージ+6%。同じスキルを続けたり、3秒あいたりすると、連撃は消える。
- 【極】のスキルは、連撃を残らず使って締めくくる：連撃1つにつき、さらにダメージ+15%。
"""
from common import *

J = "monk"

JOB = {
    "kind": "combat",
    "order": 60,
    "name": "モンク",
    "role": "格闘・連撃",
    "description": "拳ひとつで道を開く格闘家。技と技をつないで「連撃」を重ね、締めの一撃で一気に打ち抜く。続けて当てるほど「気」が満ちる。",
    "color": "#FF9A5C",
    "icon": "amemaru:textures/gui/job/monk.png",
    "weapons": "amemaru:weapons/monk",
    "weapon_text": "籠手",
    "tags": ["melee", "monk"],
    # 11月25日「近接職がボス戦ですぐに倒れる」：HP +10%→+15%・防御 +5%→+15%【補完】
    "stats": {"percent": {"attack": 10, "hp": 15, "defense": 15, "mp": -10, "magic": -20}, "flat": {"speed": 2}},
    # 11月23日：近接職は、覚えられるレベルが高いスキルほど消費MPを増やす（1レベルにつき +5%。Lv10 で ×1.45、Lv30 で ×2.45）
    "skill_mp_per_level": 0.05,
    "gauge": {
        "type": "ki",
        "name": "気",
        "description": "攻撃を続けて当てるとたまる（2秒あくと、たまり方が戻る）。3秒当てないでいると減っていく。発勁・気功・轟拳などで使う。戦闘が終わると0に戻る。",
        "max": 100,
        "color": "#FF9A5C",
        "hit_gain": 6,
        "first_gain": 3,
        "window": 2.0,
        "decay_delay": 3.0,
        "decay": 15,
    },
    "combo": {
        "window": 3.0,
        "max": 5,
        "per_step": 6,
        "finisher_per_step": 15,
        "ki_per_step": 5,
    },
    # 11月27日のご指示「モンクが弱すぎる、火力が低すぎる（序盤は弱めでいいが後半で爆発的な攻撃力を）」「どの役職もどこかのタイミングで
    # 強くなるタイミングを（パワースパイク）」【補完】：練気でレベルとともに伸び、気の器（Lv20）と崩天（Lv32）で大きく跳ねる
    "awakenings": [
        {"id": "rentai", "level": 10, "name": "練気",
         "description": "拳を重ねるほど、気が練られていく。Lv10を超えた1レベルにつき、与えるダメージ+0.75%（Lv50で+30%まで）。",
         "scaling": {"from": 10, "per_level": 0.75, "cap": 30}},
        {"id": "ki_vessel", "level": 20, "name": "気の器",
         "description": "体が気の器として目覚める。気の最大+50・連撃の最大+2（7まで）。連撃が1つ増えるたびに、最大MPの1.5%が戻る。",
         "values": {"gauge_max": 50, "combo_max": 2, "combo_mp": 1.5}},
        {"id": "houten", "level": 32, "name": "崩天",
         "description": "重ねた連撃のすべてを、一撃に。【極】の、連撃1つごとの上乗せ+10%。連撃が最大のまま【極】で締めると、少し遅れて同じだけの余震がもう一度入る。",
         "values": {"combo_finisher_per_step": 10},
         "echo": {"when": ["combo_full"], "percent": 100, "delay": 8}},
    ],
    "schools": [
        {"id": "hard", "name": "剛",
         "description": "拳と脚で打ち砕く流派。連打で連撃を始め、蹴りと崩拳でつなぎ、発勁と破山撃で締めくくる。",
         "color": "#FF8A5C"},
        {"id": "soft", "name": "柔",
         "description": "受けて流し、返す流派。流水の構えで攻撃を打ち返し、縮地と投げで間合いを操り、気を仲間に分ける。",
         "color": "#7FD6FF"},
    ],
    "starting_skills": ["monk/jab_combo", "monk/palm_strike"],
    "starter_kit": [{"item": "amemaru:apprentice_gauntlets"}],
    "upper": {
        "name": "拳聖",
        "description": "拳で人と人をつなぐ聖者。奥義（剛の「百烈・結」か、柔の「天地流転」）と、各スキルの第5段が開く。",
        "min_level": 60,
        "min_day": 11,
    },
}

MELEE = d_target(4.5)

# ================================================================ 剛
active(J, "jab_combo", "hard", 0, 0, "連打",
       "左右の拳で三段の連撃を打ちこむ。【起】連撃の始まりにも、つなぎにもなる。",
       mp=6, cd=3, link="starter", delivery=MELEE, effects=[dmg("attack", (0.5, 0.1), hits=3, interval=4)],
       tags=["fist"], combo="opener", anim="monk_jab")
active(J, "spin_kick", "hard", 1, 0, "旋脚",
       "体ごと回って、まわりの敵を蹴り払う。【継】",
       mp=10, cd=6, delivery=d_around((3.5, 0.5)), effects=[dmg("attack", (1.2, 0.2)), kb(0.5)], tags=["kick"],
       combo="link", anim="monk_kick", requires="monk/jab_combo")
passive(J, "combo_mastery", "hard", 1, "連撃の心得",
        "技と技の継ぎ目に、無駄がない。連撃1つごとのダメージの上がり方が大きくなる。", "chain_mastery", (2, 1),
        requires="monk/jab_combo")
active(J, "hakkei", "hard", 2, 0, "発勁",
       "足から背、背から掌へと力を通し、防御ごと打ち抜く。敵の防御を無視する。気が30以上あれば、30使って1.5倍。【極】",
       mp=12, cd=8, link="finisher", delivery=MELEE,
       effects=[gauge_empower(30, 1.5), dmg("attack", (1.8, 0.3), pierce=100)], tags=["fist"], combo="finisher",
       anim="monk_palm", requires="monk/spin_kick")
active(J, "collapsing_fist", "hard", 2, 1, "崩拳",
       "半歩で間合いを詰め、まっすぐな拳で敵の体勢を崩す。【継】",
       mp=10, cd=6, delivery=d_dash((4, 0.5), 1.4), effects=[dmg("attack", (1.4, 0.25)), stagger(0.8)], tags=["fist"],
       combo="link", anim="monk_dash_punch", requires="monk/spin_kick")
passive(J, "iron_body", "hard", 2, "鋼の肉体", "鍛えぬいた体は、刃も通しにくい。防御が上がる。", "iron_body", (6, 3),
        requires="monk/combo_mastery")
active(J, "rising_dragon", "hard", 3, 0, "昇龍脚",
       "地を蹴って跳び上がりながら、まわりの敵を蹴り上げる。【継】",
       mp=14, cd=9, delivery=d_around(3), effects=[dmg("attack", (1.5, 0.25)), launch(0.9)], tags=["kick"], combo="link",
       anim="monk_rising", requires="monk/hakkei")
active(J, "hundred_fists", "hard", 3, 1, "百裂拳",
       "前の敵に、数えきれないほどの拳を浴びせる（1.5秒）。【起】",
       mp=16, cd=12, delivery=d_self(),
       effects=[zone(3.5, 1.5, 0.15, [dmg("attack", (0.25, 0.04))], follow=True, mode="cone", angle=60)], tags=["fist"],
       combo="opener", anim="monk_rush", requires="monk/collapsing_fist")
passive(J, "battle_aura", "hard", 3, "闘気", "気が満ちると、拳にも力がみなぎる。気が50以上ある間、攻撃が上がる。", "battle_aura",
        (5, 3), requires="monk/iron_body")
active(J, "mountain_breaker", "hard", 4, 0, "破山撃",
       "高く跳び、落ちる勢いのまま地面に拳を叩きつける。まわりの敵の守りを砕く。【極】",
       mp=20, cd=14, link="finisher", delivery=d_leap(8, 4), effects=[dmg("attack", (2.2, 0.35)), armor_break(5)],
       tags=["fist"], combo="finisher", anim="monk_slam", impact_sound="amemaru:skill.monk.mountain_breaker_impact",
       requires="monk/rising_dragon")
passive(J, "flowing_ki", "hard", 4, "気の巡り",
        "連撃を締めくくると、巡った気が体を癒す。【極】の技で連撃を使うと、連撃1つにつき最大HPの一部が戻る。", "flowing_ki",
        (2, 1), requires="monk/battle_aura")
active(J, "thunder_fist", "hard", 5, 0, "轟拳",
       "拳に気を溜めに溜め、ありったけを一撃に込める。溜めるほど強く、気を残らず使う（気1につき威力+0.8%）。【極】",
       mp=24, cd=16, link="finisher", charge=(1.2, 1.0, 2.0), delivery=MELEE,
       effects=[gauge_burst(0.008), dmg("attack", (2.5, 0.25))], tags=["fist"], combo="finisher", anim="monk_heavy",
       charge_anim="monk_charge", requires="monk/mountain_breaker")
passive(J, "finishing_blow", "hard", 5, "極めの一撃",
        "締めの一撃に、すべてを込める。【極】の技のダメージが上がり、連撃5で放つと必ず会心になる。", "finishing_blow", (8, 4),
        requires="monk/flowing_ki")
ultimate(J, "hundred_bond", "hard", "百烈・結",
         "拳聖の奥義。百の拳で敵を打ちすえ、最後に結びの一撃を放つ。打ち終えると、まわりの仲間全員の次のスキルが強くなる。【極】",
         mp=50, cd=90, link="finisher", delivery=MELEE,
         effects=[dmg("attack", 0.35, hits=20, interval=2), dict(dmg("attack", 2.0), delay=42),
                  dict(buff("empower_next", 6, 30), area=10)],
         requires="monk/hakkei", tags=["fist"], combo="finisher", anim="monk_hundred")

# ================================================================ 柔
active(J, "palm_strike", "soft", 0, 0, "掌打",
       "やわらかな掌で、敵の芯を押し出す。少し吹き飛ばして、よろめかせる。【起】",
       mp=6, cd=4, link="starter", delivery=MELEE, effects=[dmg("attack", (1.1, 0.2)), kb(0.8), stagger(0.5)],
       tags=["fist"], combo="opener", anim="monk_palm_light")
active(J, "flowing_stance", "soft", 1, 0, "流水の構え",
       "水のように構え、しばらくの間、どの向きからの攻撃も受け流して打ち返す。打ち返すたびに気がたまる。【継】",
       mp=10, cd=14, delivery=d_self(),
       effects=[buff("parry", (3, 0.5), counters=99, all_directions=1,
                     on_success=[dmg("attack", (1.0, 0.2)), gauge(10)])],
       combo="link", anim="monk_stance", requires="monk/palm_strike")
passive(J, "allied_fists", "soft", 1, "仲間の拳",
        "肩を並べて戦う仲間がいると、体が軽くなる。まわりに近接で戦う仲間がいると、素早さが上がる。", "ally_fist", (5, 3), radius=8,
        requires="monk/palm_strike")
active(J, "ki_share", "soft", 2, 0, "気功・分け与え",
       "自分の気を手のひらに集め、仲間に分け与える。気を30使って仲間を癒し、しばらく会心を上げる（気が30以上ないと使えない）。",
       mp=10, cd=15, gauge_cost=30, link="support", delivery=d_target(16, allies=True, fallback_self=True, smart=True),
       effects=[heal((1.5, 0.3), stat="attack"), buff("crit_up", 8, 10)], anim="monk_ki_give",
       requires="monk/flowing_stance")
active(J, "shrink_step", "soft", 2, 1, "縮地",
       "地を縮めるような一歩で、敵の背後へ回り込み、打ちこむ。【起】",
       mp=10, cd=9, delivery=d_behind(10), effects=[dmg("attack", (1.0, 0.18))], tags=["fist", "move"], combo="opener",
       anim="monk_step", requires="monk/flowing_stance")
passive(J, "receiving_art", "soft", 2, "受けの心得",
        "受けることは、打つことの始まり。受け流しからの反撃のダメージが上がり、反撃すると気がたまる。", "counter_mastery",
        (15, 8), requires="monk/allied_fists")
active(J, "ki_bullet", "soft", 3, 0, "気弾",
       "練った気を掌から撃ち出す。離れた敵にも連撃をつなげられる。【継】",
       mp=12, cd=6, delivery=d_projectile(20, speed=1.6, kind="ki", radius=0.5),
       effects=[dmg("attack", (1.4, 0.25)), gauge(4)], tags=["ki"], combo="link", anim="monk_push",
       requires="monk/ki_share")
active(J, "turning_throw", "soft", 3, 1, "転身投げ",
       "敵の勢いを受け止め、体を回して背中の後ろへ投げ飛ばす（ボスは投げられない）。【継】",
       mp=12, cd=10, delivery=MELEE, effects=[dmg("attack", (1.3, 0.2)), throw(1.0), stagger(1.2)], tags=["fist"],
       combo="link", anim="monk_throw", requires="monk/shrink_step")
passive(J, "soft_way", "soft", 3, "柔の理", "力に力で逆らわない。回避が上がり、かわすたびに気がたまる。", "soft_way", (5, 2.5),
        requires="monk/receiving_art")
active(J, "ki_vortex", "soft", 4, 0, "気の渦",
       "気の渦を起こし、まわりの敵を自分のそばへ引き寄せて打つ。引き寄せられた敵は足が鈍る。【継】",
       mp=16, cd=14, delivery=d_around((6, 0.5)), effects=[pull(1.0, 1.5), dmg("attack", (1.0, 0.18)), slow(2, 2)],
       tags=["ki"], combo="link", anim="monk_vortex", requires="monk/ki_bullet")
passive(J, "calm_breath", "soft", 4, "静の呼吸",
        "呼吸を整え、気で体を満たす。戦いの間、気が50以上あると、毎秒HPが少しずつ戻る。", "calm_breath", (0.5, 0.25),
        requires="monk/soft_way")
active(J, "inch_punch", "soft", 5, 0, "寸勁",
       "拳を敵に当てたまま、一寸の間合いから気を打ちこむ。打った後、しばらく自分を気の膜が包む。【極】",
       mp=18, cd=12, link="finisher", delivery=d_target(3.5),
       effects=[dmg("attack", (2.0, 0.3)), dict(barrier((1.0, 0.2), 6, stat="attack"), self=True)], tags=["fist"],
       combo="finisher", anim="monk_inch", requires="monk/ki_vortex")
passive(J, "reading_flow", "soft", 5, "流れを読む",
        "相手の流れを読みきる。受け流しやかわした後3秒は、次のスキルが同じ技でも連撃をつなぎ、ダメージも上がる。", "read_flow",
        (10, 5), requires="monk/calm_breath")
ultimate(J, "heaven_earth", "soft", "天地流転",
         "拳聖の奥義。天と地の流れに身をゆだね、8秒の間、どの向きからの攻撃も受け流して打ち返す。打ち返すたびに連撃がつながり、その間は【極】の技を使っても連撃が消えない。",
         mp=45, cd=100, delivery=d_self(),
         effects=[buff("parry", 8, counters=99, all_directions=1,
                       on_success=[dmg("attack", 1.2), gauge(10), {"type": "combo_step"}]),
                  buff("endless_flow", 8)],
         requires="monk/flowing_stance", anim="monk_heaven")

# ================================================================ 11月24日に足したスキル（スキルツリーの作り直し）
# ご指示「モンクだと回復できるパンチや、パッシブとは別で一定時間火力上げる自身のバフ」
active(J, "kaisei_fist", "soft", 1, 3, "回生拳",
       "気を巡らせた拳で打ち、打った手応えをそのまま命に変える。与えたダメージの一部だけHPが戻る。【継】",
       mp=10, cd=6, delivery=MELEE, effects=[dmg("attack", (1.3, 0.2), lifesteal=(30, 5))], tags=["fist"], combo="link",
       anim="monk_palm", requires="monk/palm_strike")
active(J, "kien", "hard", 2, 3, "気炎",
       "腹の底の気を燃やし、しばらく自分の与えるダメージを上げる。使うと気が20たまる。",
       mp=16, cd=40, cdpr=-2, delivery=d_self(), effects=[buff("power_up", (10, 1), (15, 4)), gauge(20)], anim="monk_stance",
       requires="monk/spin_kick")
active(J, "air_palm", "soft", 2, 3, "空掌",
       "離れた敵へ、掌から気の塊を撃ち出す。当たった敵を大きく押し返す。【起】",
       mp=10, cd=6, delivery=d_projectile(14, speed=1.6, kind="ki"), effects=[dmg("attack", (1.1, 0.18)), kb(1.0)], tags=["fist"],
       combo="opener", anim="monk_push", requires="monk/flowing_stance")
active(J, "chain_kicks", "hard", 3, 3, "連環脚",
       "回りながら三度蹴り、まわりの敵をまとめて打つ。【継】",
       mp=16, cd=10, delivery=d_around(3.5), effects=[dmg("attack", (0.55, 0.08), hits=3, interval=4), kb(0.3)], tags=["kick"],
       combo="link", anim="monk_kick", requires="monk/hakkei")
active(J, "back_strike", "hard", 4, 3, "背当て",
       "踏み込みざまに背中ごとぶつかり、敵を大きく吹き飛ばしてよろめかせる。【極】",
       mp=22, cd=14, cdpr=-0.5, link="finisher", delivery=d_dash(4, 1.6), effects=[dmg("attack", (2.2, 0.3)), stagger(1.0), kb(1.4)],
       tags=["fist"], combo="finisher", anim="monk_dash_punch", requires="monk/rising_dragon")

# ================================================================ 11月25日に足したパッシブ（近接職の守り）
# ご指示「近接職がボス戦でのダメージ受けすぎてすぐに倒れてしまう。回復系のスキルや防御力を上げるなどで対策をする」【補完】
passive(J, "adamant_body", "hard", 1, "金剛身",
        "気を体の表に張りめぐらせ、打たれても崩れない。受けるダメージが減り、気が満ちているほど、さらに減る。", "adamant_body", (6, 2),
        col=3)
