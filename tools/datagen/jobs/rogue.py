"""ローグ（高火力・機動力／二刀の短剣／専用ゲージ「気配」：攻撃ごとに1ずつ、最大5／流派：影・毒）。"""
from common import *

J = "rogue"

JOB = {
    "kind": "combat",
    "order": 50,
    "name": "ローグ",
    "role": "高火力・機動力",
    "description": "影を渡り、二本の短剣で急所を断つ者。攻撃を当てるたびに「気配」を研ぎ、締めの一撃で一気に放つ。毒で敵をじわじわと弱らせる。",
    "color": "#B58CFF",
    "icon": "amemaru:textures/gui/job/rogue.png",
    "weapons": "amemaru:weapons/rogue",
    "weapon_text": "二刀の短剣",
    "tags": ["melee", "rogue"],
    # 11月25日「近接職がボス戦ですぐに倒れる」：HP -10%→0%・防御 -15%→-5%【補完】
    "stats": {"percent": {"attack": 20, "speed": 15, "hp": 0, "defense": -5, "magic": -20}, "flat": {"crit": 5}},
    # 11月23日：近接職は、覚えられるレベルが高いスキルほど消費MPを増やす（1レベルにつき +5%。Lv10 で ×1.45、Lv30 で ×2.45）
    "skill_mp_per_level": 0.05,
    "gauge": {
        "type": "presence",
        "name": "気配",
        "description": "攻撃を当てるたびに1ずつたまる（最大5）。急所突き・影葬・毒爆などの締めの技で、残らず使って威力を上げる。戦闘が終わると0に戻る。",
        "max": 5,
        "color": "#B58CFF",
    },
    # 11月27日のご指示「どの役職もどこかのタイミングで強くなるタイミングを（パワースパイク）」【補完】
    "awakenings": [
        {"id": "shadow_edge", "level": 18, "name": "影の刃",
         "description": "影に溶ける感覚をつかむ。気配の最大+2（7まで）・会心+5・与えるダメージ+8%。",
         "values": {"gauge_max": 2, "crit": 5, "damage": 8}},
        {"id": "no_shadow", "level": 32, "name": "無影",
         "description": "影さえ残さない。気配を5つ以上使った締めの技は、少し遅れて影がもう一度斬る（ダメージの60%）。会心ダメージ+20%。",
         "values": {"crit_damage": 20},
         "echo": {"when": ["gauge_burst"], "min_gauge": 5, "percent": 60, "delay": 6}},
    ],
    "schools": [
        {"id": "shadow", "name": "影",
         "description": "影を渡って背後を取る流派。素早い連撃で気配を研ぎ、影から急所をひと突きにする。",
         "color": "#B58CFF"},
        {"id": "venom", "name": "毒",
         "description": "毒と罠で敵を弱らせる流派。毒を重ね、煙と糸で敵を惑わせ、最後に毒ごと弾けさせる。",
         "color": "#8CE65A"},
    ],
    "starting_skills": ["rogue/flurry", "rogue/venom_blade"],
    "starter_kit": [{"item": "amemaru:apprentice_daggers"}],
    "upper": {
        "name": "影結び",
        "description": "影と影を結んで渡り歩く者。奥義（影の「千の刃」か、毒の「毒華」）と、各スキルの第5段が開く。",
        "min_level": 60,
        "min_day": 11,
    },
}

MELEE = d_target(4.5)

# ================================================================ 影
active(J, "flurry", "shadow", 0, 0, "乱れ刃",
       "二本の短剣で、目にも止まらぬ三連撃を浴びせる。気配が2たまる。",
       mp=8, cd=4, link="starter", delivery=MELEE, effects=[dmg("attack", (0.55, 0.1), hits=3, interval=3), gauge(1)],
       tags=["blade"], anim="rogue_flurry")
active(J, "shadow_run", "shadow", 1, 0, "影走り",
       "影の中を走り抜け、向いている方へ一気に駆ける。走っている間は、敵の攻撃をすり抜ける。",
       mp=8, cd=(8, -1), delivery=d_dash((8, 1), 1.2), effects=[dict(buff("evade", 0.8), self=True)], tags=["move"],
       anim="rogue_dash", requires="rogue/flurry")
passive(J, "backstab_mastery", "shadow", 1, "背後の心得",
        "敵の背後こそ、ローグの立つ場所。背後から当てたダメージが上がる。", "backstab_mastery", (10, 5), requires="rogue/flurry")
active(J, "vital_strike", "shadow", 2, 0, "急所突き",
       "気配を残らず使い、急所をひと突きする。使った気配1つにつき威力が40%上がり、背後から突くと、さらに1.5倍（気配が1以上ないと使えない）。",
       mp=12, cd=7, gauge_cost=1, link="finisher", delivery=MELEE,
       effects=[gauge_burst(0.4), dmg("attack", (1.4, 0.2), backstab=1.5)], tags=["blade"], anim="rogue_thrust",
       requires="rogue/shadow_run")
active(J, "shadow_strike", "shadow", 2, 1, "影討ち",
       "敵の影に溶けて背後へ回り込み、そのまま斬りつける。",
       mp=14, cd=10, delivery=d_behind(12), effects=[dmg("attack", (1.2, 0.2), backstab=1.3)], tags=["blade", "move"],
       anim="rogue_behind", requires="rogue/shadow_run")
passive(J, "nimble", "shadow", 2, "身軽", "身のこなしが軽く、攻撃をひらりとかわす。回避が上がる。", "nimble", (5, 3),
        requires="rogue/backstab_mastery")
active(J, "shadow_stitch", "shadow", 3, 0, "影縫い",
       "味方の影に飛び込み、その人のそばへ移る。守りに行くときにも、逃げるときにも使える。",
       mp=12, cd=15, link="support", delivery=d_target(24, allies=True), effects=[teleport_to()], tags=["move"],
       anim="rogue_dash", requires="rogue/vital_strike")
active(J, "phantom_blades", "shadow", 3, 1, "幻影刃",
       "影の分身たちと一緒に、まわりの敵をいっせいに斬る。",
       mp=16, cd=10, delivery=d_around((4, 0.5)), effects=[dmg("attack", (1.2, 0.2))], tags=["blade"], anim="rogue_spin",
       requires="rogue/shadow_strike")
passive(J, "fade", "shadow", 3, "気配消し",
        "息をひそめると、敵は見失う。3秒攻撃しないでいると敵に狙われにくくなり、次の一撃のダメージが上がる。", "fade", (15, 5),
        requires="rogue/nimble")
active(J, "shadow_burial", "shadow", 4, 0, "影葬",
       "気配を残らず使い、足もとの影から黒い刃を突き上げる。使った気配1つにつき威力が35%上がり、敵の防御の半分を無視してよろめかせる（気配が1以上ないと使えない）。",
       mp=18, cd=12, gauge_cost=1, link="finisher", delivery=d_target(6),
       effects=[gauge_burst(0.35), dmg("attack", (1.8, 0.3), pierce=50), stagger(1.0)], tags=["blade"],
       anim="rogue_cross", requires="rogue/shadow_stitch")
passive(J, "shadow_footwork", "shadow", 4, "影の歩法",
        "影から出た直後の一撃は、迷いがない。影走り・影討ち・影縫いの後3秒、次の一撃の会心が上がる。", "shadow_step", (20, 10),
        requires="rogue/fade")
active(J, "shadow_double", "shadow", 5, 0, "影分身",
       "自分の影を立ち上がらせ、共に戦わせる。8秒の間、スキルを当てるたびに影がもう一度斬りつける（ダメージの一部）。",
       mp=26, cd=40, delivery=d_self(), effects=[buff("shadow_clone", 8, (30, 10))], anim="rogue_clone",
       requires="rogue/shadow_burial")
passive(J, "dark_edge", "shadow", 5, "闇の刃", "闇に研がれた刃は、深く入る。会心ダメージが上がる。", "dark_edge", (10, 5),
        requires="rogue/shadow_footwork")
ultimate(J, "thousand_blades", "shadow", "千の刃",
         "影結びの奥義。まわりの敵すべてに、千の刃のような連撃を浴びせる。仲間の起点の印がある敵には、倍の傷を刻む。気配が残っていれば、それも込める。",
         mp=50, cd=90, delivery=d_around(6),
         effects=[gauge_burst(0.2), dict(dmg("attack", 0.6, hits=10, interval=2), bonus_linked=2.0)],
         requires="rogue/vital_strike", tags=["blade"], anim="rogue_thousand")

# ================================================================ 毒
POISON = poison((0.25, 0.05), 6, 1, 3)
active(J, "venom_blade", "venom", 0, 0, "毒刃",
       "毒を塗った刃で斬りつける。斬られた敵は、しばらく毒で傷つき続ける（3つまで重なる）。",
       mp=8, cd=5, link="starter", delivery=MELEE, effects=[dmg("attack", (0.8, 0.15)), POISON], tags=["blade", "poison"],
       anim="rogue_slash")
active(J, "smoke_bomb", "venom", 1, 0, "煙玉",
       "足もとに煙玉をたたきつける。煙の中の敵は狙いが定まらず（攻撃が外れやすい）、自分は敵に見失われる。",
       mp=14, cd=18, delivery=d_around(5),
       effects=[stealth(3), zone(5, 5, 0.5, [blind((30, 10), 1.0)])], tags=["poison"], anim="rogue_throw",
       requires="rogue/venom_blade")
passive(J, "toxin_mastery", "venom", 1, "毒の心得", "毒の調合を知り尽くしている。毒のダメージが上がる。", "toxin_power", (10, 5),
        requires="rogue/venom_blade")
active(J, "poison_mist", "venom", 2, 0, "毒霧",
       "毒の小瓶を投げ、狙った地点に毒の霧を広げる。霧の中の敵には、毎秒毒が重なっていく。",
       mp=16, cd=12, delivery=d_point(12, (3.5, 0.5)), effects=[zone((3.5, 0.5), 5, 1.0, [POISON])], tags=["poison"],
       anim="rogue_throw", requires="rogue/smoke_bomb")
active(J, "numbing_needle", "venom", 2, 1, "痺れ針",
       "痺れ薬を塗った細い針を投げる。当たった敵は体がしびれてよろめき、しばらく足が鈍る。",
       mp=10, cd=9, link="starter", delivery=d_projectile(20, speed=2.4, kind="needle"),
       effects=[dmg("attack", (0.7, 0.1)), stagger((1.0, 0.25)), slow(3, 2)], tags=["poison"], anim="rogue_needle",
       requires="rogue/smoke_bomb")
passive(J, "corrosion", "venom", 2, "蝕み",
        "毒は体の守りを内側から蝕む。毒にかかった敵は、誰から受けるダメージも上がる。", "corrode", (6, 3),
        requires="rogue/toxin_mastery")
active(J, "toxic_burst", "venom", 3, 0, "毒爆",
       "毒に合図を送り、敵の体の中で一気に弾けさせる。狙った敵とまわりの敵の毒を消すかわりに、毒の数だけ大きなダメージを与える。気配が残っていれば、それも込める。",
       mp=16, cd=10, link="finisher", delivery=d_target(16),
       effects=[gauge_burst(0.25), dict(detonate_poison(0.6, (0.6, 0.1)), radius=4)], tags=["poison"], anim="rogue_snap",
       impact_sound="amemaru:skill.rogue.toxic_burst_impact", requires="rogue/poison_mist")
active(J, "binding_threads", "venom", 3, 1, "絡め糸",
       "粘る糸を扇に投げ広げ、前の敵をまとめて絡め取る。絡まった敵は少しの間動けず、その後もしばらく足が鈍る。",
       mp=14, cd=14, delivery=d_cone((6, 0.5), 45), effects=[dmg("attack", (0.6, 0.1)), root((1.0, 0.25)), slow(3, 3)],
       anim="rogue_throw_wide", requires="rogue/numbing_needle")
passive(J, "deep_toxin", "venom", 3, "しみる毒", "毒が体の奥までしみこむ。毒の重なる上限が増える。", "deep_toxin", (1, 1),
        requires="rogue/corrosion")
active(J, "envenom", "venom", 4, 0, "毒塗り",
       "二本の刃に毒をたっぷり塗る。しばらく、通常の攻撃とスキルが当たるたびに、毒が重なる。",
       mp=18, cd=30, delivery=d_self(), effects=[buff("venom", (10, 2), (0.25, 0.05))], tags=["poison"], anim="rogue_coat",
       requires="rogue/toxic_burst")
passive(J, "exploit", "venom", 4, "弱り目", "弱った獲物ほど、急所が見えやすい。毒にかかった敵への会心が上がる。", "exploit", (5, 3),
        requires="rogue/deep_toxin")
active(J, "corroding_edge", "venom", 5, 0, "腐蝕の刃",
       "金物も溶かす毒の刃で斬りつける。敵の守りを砕き、毒を2つ重ねる。",
       mp=20, cd=12, link="finisher", delivery=MELEE,
       effects=[dmg("attack", (1.6, 0.25)), armor_break(6), poison((0.25, 0.05), 6, 2, 3)], tags=["blade", "poison"],
       anim="rogue_cross", requires="rogue/envenom")
passive(J, "contagion", "venom", 5, "毒の連鎖",
        "毒は倒れた体から、まわりへ移っていく。毒にかかった敵が倒れると、まわりの敵に毒が移る。", "contagion", (2, 1), radius=4,
        requires="rogue/exploit")
ultimate(J, "poison_bloom", "venom", "毒華",
         "影結びの奥義。足もとに大きな毒の華を咲かせる。まわりの敵に毒を5つ重ね、華が咲いている間（8秒）、毒を重ね続ける。気配が残っていれば、毒が強くなる。",
         mp=50, cd=100, delivery=d_around(8),
         effects=[gauge_burst(0.2), poison(0.3, 8, 5, 5), zone(8, 8, 1.0, [poison(0.3, 8, 1, 5)])],
         requires="rogue/toxic_burst", tags=["poison"], anim="rogue_bloom")

# ================================================================ 11月24日に足したスキル（スキルツリーの作り直し）
active(J, "throwing_knife", "shadow", 1, 3, "投げ刃",
       "袖に隠した短い刃を、離れた敵へ投げつける。",
       mp=8, cd=4, delivery=d_projectile(18, speed=2.0, kind="needle"), effects=[dmg("attack", (1.0, 0.15))],
       anim="rogue_throw", requires="rogue/flurry")
active(J, "twin_fangs", "shadow", 2, 3, "双牙",
       "左右の刃で、同じ所を2度続けて斬る。背後からなら深く入る。",
       mp=12, cd=6, delivery=MELEE, effects=[dmg("attack", (0.8, 0.12), hits=2, interval=3, backstab=1.5)], anim="rogue_cross",
       requires="rogue/shadow_run")
active(J, "venom_veil", "venom", 2, 3, "毒の帳",
       "狙った所に毒の霧の帳を下ろす。中にいる敵に毒が重なっていく。",
       mp=22, cd=18, cdpr=-1, delivery=d_point(14, 4), effects=[zone(4, 6, 1.0, [poison((0.25, 0.05), 6)])], anim="rogue_throw_wide",
       requires="rogue/smoke_bomb")
active(J, "shadow_dance", "shadow", 3, 3, "影の舞",
       "影のように軽く舞う。しばらく足が速くなり、与えるダメージも上がる。",
       mp=20, cd=36, cdpr=-2, delivery=d_self(), effects=[buff("power_up", (8, 1), (15, 4)), buff("haste", (8, 1), (15, 3))],
       anim="rogue_spin", requires="rogue/vital_strike")
active(J, "hamstring", "venom", 3, 3, "足払い刃",
       "足の腱を狙って斬る。敵はしばらくその場から動けず、攻撃も弱まる。",
       mp=14, cd=12, delivery=MELEE, effects=[dmg("attack", (1.2, 0.2)), root(2), weaken(15, 6)], anim="rogue_slash",
       requires="rogue/poison_mist")

# ================================================================ 11月25日に足したパッシブ（近接職の守り）
# ご指示「近接職がボス戦でのダメージ受けすぎてすぐに倒れてしまう。回復系のスキルや防御力を上げるなどで対策をする」【補完】
passive(J, "shadow_drain", "shadow", 1, "影吸い",
        "刃が吸った力は、影を通って自分の命になる。与えたダメージの一部だけHPが戻る。", "life_drain", (6, 2), col=1)
