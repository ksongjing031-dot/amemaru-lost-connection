"""ヒーラー（回復・支援／鈴杖／MP／流派：癒し・加護）。Phase 0-A 19章の6つのスキル・3つのパッシブ・奥義に、
11月22日のご指示（スキルとパッシブを増やす）で足した分を合わせる。【新】は足したもの。"""
from common import *

J = "healer"

JOB = {
    "kind": "combat",
    "order": 30,
    "name": "ヒーラー",
    "role": "回復・支援",
    "description": "結びの教会の鈴を手に、仲間の傷をつなぎ直す癒し手。鈴の音で傷をふさぎ、倒れた仲間を呼び戻し、加護の灯で仲間を守る。",
    "color": "#8CF0B4",
    "icon": "amemaru:textures/gui/job/healer.png",
    "weapons": "amemaru:weapons/healer",
    "weapon_text": "鈴杖",
    "tags": ["magic", "support", "healer"],
    "stats": {"percent": {"mp": 40, "magic": 15, "defense": 5, "attack": -20}},
    "schools": [
        {"id": "healing", "name": "癒し",
         "description": "仲間の傷を癒し、倒れた仲間を呼び戻す流派。ひとりを大きく癒す鈴、みんなを包む輪と雨、命の糸。",
         "color": "#8CF0B4"},
        {"id": "blessing", "name": "加護",
         "description": "鈴の音で仲間を守り、敵を戒める流派。加護の灯と祝福の盾で守り、清めの鈴音と天鈴で敵を打つ。",
         "color": "#FFE08A"},
    ],
    "starting_skills": ["healer/bell_heal", "healer/purifying_chime"],
    "starter_kit": [{"item": "amemaru:apprentice_bell_staff"}],
    "upper": {
        "name": "鈴守",
        "description": "結びの鈴を守り継ぐ者。奥義（癒しの「大いなる呼び声」か、加護の「結びの聖域」）と、各スキルの第5段が開く。",
        "min_level": 60,
        "min_day": 11,
    },
    # 11月26日のご指示「遠距離職は攻撃の際に…硬直やほぼほぼ動けないスロウ効果」【補完】：攻撃の鈴だけ、ウィザードと同じ硬直
    # （0.4〜1.2秒、ほぼ止まる）。回復・強化・障壁などで仲間も支える鈴（exempt_support）は、今までどおり動ける
    "_comment_attack_lock": "11月26日【補完】：攻撃の鈴だけ、放ったあと0.4〜1.2秒ほぼ止まる（ウィザードと同じ）。仲間も支える鈴は動ける",
    "attack_lock": {
        "skill": {"base": 0.3, "per_cooldown": 0.05, "min": 0.4, "max": 1.2, "level": 3, "exempt_support": True},
    },
}

ALLY = d_target(20, allies=True, fallback_self=True, smart=True)

# ================================================================ 癒し
active(J, "bell_heal", "healing", 0, 0, "癒しの鈴",
       "鈴杖を鳴らし、狙った仲間の傷をふさぐ。狙った先に仲間がいなければ、まわりでいちばん傷ついた仲間（いなければ自分）を癒す。",
       mp=10, cd=3, delivery=ALLY, effects=[heal((3.0, 0.4))], tags=["heal", "bell"], anim="healer_ring")
active(J, "prayer_circle", "healing", 1, 0, "祈りの輪",
       "鈴を高く掲げて祈り、自分のまわりに癒しの輪を広げる。輪の中の仲間（自分も）の傷がふさがる。",
       mp=20, cd=10, delivery=d_around((7, 1), allies=True), effects=[heal((1.8, 0.3))], tags=["heal"],
       anim="healer_pray", requires="healer/bell_heal")
passive(J, "mercy", "healing", 1, "慈愛", "癒す手に、ためらいがない。回復の量が上がる。", "heal_power", (8, 4),
        requires="healer/bell_heal")
active(J, "bond_mend", "healing", 2, 0, "結び手当て",
       "光の糸で仲間の傷を結び合わせる。傷をふさぎ、ロスト状態などの悪い状態をほどく。",
       mp=16, cd=(8, -0.5), link="support", delivery=ALLY, effects=[heal((2.2, 0.3)), cleanse()], tags=["heal"],
       anim="healer_point", requires="healer/prayer_circle")
active(J, "healing_rain", "healing", 2, 1, "癒しの雨",
       "狙った地点に、やわらかな光の雨を降らせる。雨の中にいる仲間は、少しずつ傷が癒えていく。",
       mp=28, cd=(16, -1), delivery=d_point(20, (4, 0.5)),
       effects=[zone((4, 0.5), 6, 1.0, [dict(heal((0.5, 0.08)), to="allies")])], tags=["heal"],
       anim="healer_raise", requires="healer/prayer_circle")
passive(J, "aftercare", "healing", 2, "余韻",
        "鈴の音は、鳴りやんでも残る。自分が癒した仲間は、その後も少しずつ傷が癒え続ける。", "aftercare", (30, 10),
        requires="healer/mercy")
active(J, "call_back", "healing", 3, 0, "呼び戻し",
       "鈴の音で、倒れた仲間の名前を呼ぶ。離れた所でダウンしている仲間を、その場で起こす。",
       mp=40, cd=(150, -20), cast=1.0, delivery=d_target(24, allies=True, downed=True), effects=[revive((30, 10))],
       tags=["heal"], anim="healer_call", cast_anim="healer_call_cast", requires="healer/bond_mend")
active(J, "life_thread", "healing", 3, 1, "命の糸",
       "仲間と自分を、命の糸でつなぐ。糸がつながっている間は少しずつ傷が癒え、仲間のHPが大きく減ると、糸が一度だけ強く光って大きく癒す。",
       mp=22, cd=20, link="support", delivery=ALLY,
       effects=[buff("life_thread", 10, (0.25, 0.05), burst=(3.0, 0.4), threshold=35)], tags=["heal"],
       anim="healer_point", requires="healer/healing_rain")
passive(J, "bond_healing", "healing", 3, "つながりの癒し",
        "仲間が多いほど、癒しの力も強くなる。まわりにいるパーティの仲間1人につき、回復の量が上がる（4人まで）。", "bond_heal",
        (4, 2), radius=20, requires="healer/aftercare")
active(J, "swelling_bell", "healing", 4, 0, "満ちる鈴",
       "鈴杖に祈りを満たしてから鳴らす。長く満たすほど大きく癒し、満たしきると悪い状態もほどいて、しばらく癒し続ける。",
       mp=24, cd=9, charge=(1.5, 1.0, 2.25), delivery=ALLY, effects=[heal((2.0, 0.3))],
       full=[cleanse(), regen((0.3, 0.05), 5)], tags=["heal", "bell"], anim="healer_release", charge_anim="healer_charge",
       requires="healer/call_back")
passive(J, "overflowing_heal", "healing", 4, "溢れる癒し",
        "あふれた癒しは、薄い光の膜になって残る。満タンを超えて癒した分の一部が、ダメージを吸う障壁になる（最大HPの15%まで・8秒）。",
        "overheal_barrier", (30, 10), requires="healer/bond_healing")
active(J, "resonant_healing", "healing", 5, 0, "癒しの共鳴",
       "大きく鈴を鳴らし、遠くの仲間の糸まで響かせる。32ブロック以内の仲間全員を癒し、悪い状態を1つずつほどく。",
       mp=45, cd=(40, -4), link="support", delivery=d_around(32, allies=True), effects=[heal((2.0, 0.3)), cleanse(1)],
       tags=["heal", "bell"], anim="healer_resonance", requires="healer/swelling_bell")
passive(J, "full_heart", "healing", 5, "満ちる心",
        "仲間が癒えると、自分の心も満ちる。ほかの仲間を癒すと、癒した量の一部だけMPが戻る。", "heal_mp", (4, 2),
        requires="healer/overflowing_heal")
ultimate(J, "great_call", "healing", "大いなる呼び声",
         "鈴守の奥義。天に届くほど大きく鈴を鳴らし、まわりで倒れている仲間を全員起こし、仲間全員の傷を大きく癒す。",
         mp=80, cd=300, cast=2.0, delivery=d_around(16, allies=True, downed=True), effects=[revive(50), heal(3.0)],
         requires="healer/call_back", tags=["heal", "bell"], anim="healer_great_call", cast_anim="healer_great_call_cast",
         cast_sound="amemaru:skill.healer.great_call_cast")

# ================================================================ 加護
active(J, "purifying_chime", "blessing", 0, 0, "清めの鈴音",
       "鈴を前へ振り、清めの音を波のように放つ。前にいる敵を打ち、少し押し返す。ひとりで戦うときの、ヒーラーの身を守る技。",
       mp=8, cd=4, link="starter", delivery=d_cone((6, 0.5), 50), effects=[dmg("magic", (1.2, 0.2)), kb(0.4)],
       tags=["bell"], anim="healer_chime")
active(J, "blessing_light", "blessing", 1, 0, "加護の灯",
       "小さな加護の灯をともし、まわりの仲間に分ける。灯を受けた仲間（自分も）は、しばらく防御が上がる。",
       mp=18, cd=20, link="support", delivery=d_around(8, allies=True), effects=[buff("defense_up", 15, (15, 5))],
       anim="healer_lantern", requires="healer/purifying_chime")
passive(J, "bell_ward", "blessing", 1, "鈴の守り",
        "鈴の音が届く所では、仲間の体が少しだけ守られる。まわりの仲間（自分も）の受けるダメージが減る。", "bell_guard", (3, 2),
        radius=8, requires="healer/purifying_chime")
active(J, "silence_sweep", "blessing", 2, 0, "静寂払い",
       "鈴を一度だけ静かに鳴らし、まわりの空気を澄ませる。まわりの仲間（自分も）の悪い状態をまとめてほどき、少し癒す。",
       mp=20, cd=(14, -1), link="support", delivery=d_around((8, 1), allies=True), effects=[cleanse(), heal((0.8, 0.15))],
       tags=["heal", "bell"], anim="healer_ring", requires="healer/blessing_light")
active(J, "blessed_shield", "blessing", 2, 1, "祝福の盾",
       "祝福の光で、狙った仲間を包む盾を作る。盾は受けるダメージを吸い、消えるとき（砕けても、時間が切れても）、盾の大きさの半分だけ傷を癒す。",
       mp=20, cd=12, link="support", delivery=ALLY, effects=[barrier((2.5, 0.4), 8, heal_on_end=50)],
       anim="healer_point", requires="healer/blessing_light")
passive(J, "purity", "blessing", 2, "清めの心得",
        "清められた体は、しばらく守られる。自分の技で悪い状態をほどかれた仲間は、4秒受けるダメージが減る。", "cleanse_guard",
        (10, 5), requires="healer/bell_ward")
active(J, "admonishing_bell", "blessing", 3, 0, "戒めの鈴",
       "敵の頭の上で、戒めの鈴を鳴らす。鈴が鳴っている間、その敵の攻撃が弱まる。",
       mp=14, cd=10, link="starter", delivery=d_target(16), effects=[dmg("magic", (0.8, 0.15)), weaken((15, 5), 8)],
       tags=["bell"], anim="healer_point", requires="healer/silence_sweep")
active(J, "calming_circle", "blessing", 3, 1, "鎮めの陣",
       "地面に鈴の紋の陣を描く。陣の中の敵は鈴の音に打たれて足が鈍り、陣の中の仲間は守られる。",
       mp=26, cd=18, delivery=d_point(18, (4, 0.5)),
       effects=[zone((4, 0.5), 6, 1.0, [dmg("magic", (0.5, 0.1)), slow(1.2, 2),
                                        buff("ward", 1.2, 10, to="allies")])],
       tags=["bell"], anim="healer_slam", requires="healer/blessed_shield")
passive(J, "admonishing_echo", "blessing", 3, "戒めの響き",
        "鈴の音は、敵の心にも響く。自分のスキルでダメージを与えた敵は、4秒攻撃が弱まる。", "weaken_echo", (5, 3),
        requires="healer/purity")
active(J, "guardian_bell", "blessing", 4, 0, "身代わりの鈴",
       "狙った仲間の頭の上に、小さな身代わりの鈴を浮かべる。鈴が浮いている間に倒れるほどの攻撃を受けると、鈴が代わりに砕け、仲間は倒れずにHPが戻る（1回だけ）。",
       mp=30, cd=(60, -10), link="support", delivery=ALLY, effects=[buff("guardian", (20, 5), (25, 5))],
       tags=["bell"], anim="healer_raise", requires="healer/admonishing_bell")
passive(J, "steady_prayer", "blessing", 4, "揺るがぬ祈り",
        "祈りの最中は、心が揺るがない。詠唱と溜めの間、受けるダメージが減る。", "steady_prayer", (15, 5),
        requires="healer/admonishing_echo")
active(J, "heavens_bell", "blessing", 5, 0, "天鈴",
       "空から光の大鈴を落とす。落ちた所の敵を打ってよろめかせ、そこにいる仲間を癒す。",
       mp=36, cd=(16, -1), link="finisher", delivery=d_point(20, (4, 0.5), delay=0.8),
       effects=[dmg("magic", (3.0, 0.4)), stagger(1.5), heal((1.0, 0.15))], tags=["bell", "heal"],
       anim="healer_slam", impact_sound="amemaru:skill.healer.heavens_bell_impact", requires="healer/guardian_bell")
passive(J, "bell_echo", "blessing", 5, "鈴の余韻",
        "敵を打った鈴の音が、仲間の傷を癒す音に変わる。自分のスキルで与えたダメージの一部だけ、まわりでいちばん傷ついた仲間を癒す。",
        "bell_echo", (15, 5), radius=12, requires="healer/steady_prayer")
ultimate(J, "bond_sanctuary", "blessing", "結びの聖域",
         "鈴守の奥義。鈴の音で結ばれた聖域を開く（10秒）。聖域の中の仲間は受けるダメージが減って少しずつ癒え、中の敵は攻撃が弱まる。",
         mp=70, cd=240, delivery=d_around(8, allies=True),
         effects=[zone(8, 10, 1.0, [buff("protect", 1.2, 25, to="allies"), dict(heal(0.4), to="allies"), weaken(20, 1.2)])],
         requires="healer/guardian_bell", tags=["bell", "heal"], anim="healer_sanctuary")

# ================================================================ 11月24日に足したスキル（スキルツリーの作り直し）
active(J, "bell_strike", "blessing", 1, 3, "鈴打ち",
       "鈴杖で打ち、鳴った鈴の音が自分の傷をふさぐ。与えたダメージの一部だけHPが戻る。",
       mp=8, cd=5, delivery=d_target(4.5), effects=[dmg("magic", (1.1, 0.18), lifesteal=(40, 5))], anim="healer_slam",
       requires="healer/purifying_chime")
active(J, "mending_light", "healing", 1, 3, "手当ての灯",
       "仲間ひとりに小さな灯をともし、すぐに癒して、しばらく少しずつ癒し続ける。",
       mp=12, cd=7, delivery=ALLY, effects=[heal((1.4, 0.25)), regen((0.2, 0.04), 6)], anim="healer_point",
       requires="healer/bell_heal")
active(J, "chorus_bell", "blessing", 2, 3, "輪唱の鈴",
       "鈴を輪唱のように重ねて鳴らす。まわりの仲間（自分も）の与えるダメージが、しばらく上がる。",
       mp=22, cd=32, cdpr=-2, link="support", delivery=d_around(10, allies=True), effects=[buff("power_up", (12, 1), (8, 3))],
       anim="healer_ring", requires="healer/blessing_light")
active(J, "swift_prayer", "healing", 3, 3, "疾き祈り",
       "短い祈りで、まわりの仲間（自分も）の足を速くし、悪い状態を1つ払う。",
       mp=18, cd=26, cdpr=-1.5, link="support", delivery=d_around(10, allies=True), effects=[buff("haste", (8, 1), (20, 4)), cleanse(1)],
       anim="healer_pray", requires="healer/bond_mend")
active(J, "judgment_bell", "blessing", 4, 3, "裁きの鐘",
       "狙った所に大きな鐘の音を落とす。まわりの敵にダメージを与え、少しよろめかせ、目をくらませる。",
       mp=28, cd=16, cdpr=-0.5, delivery=d_point(16, (5, 0.5)), effects=[dmg("magic", (1.8, 0.25)), stagger(0.6), blind(20, 3)],
       anim="healer_raise", requires="healer/admonishing_bell")
