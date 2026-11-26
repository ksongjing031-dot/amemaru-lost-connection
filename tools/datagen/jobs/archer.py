"""アーチャー（遠距離／弓／専用ゲージ「集中」：立ち止まる・当てるとたまる／流派：狙撃・速射）。"""
from common import *

J = "archer"

JOB = {
    "kind": "combat",
    "order": 40,
    "name": "アーチャー",
    "role": "遠距離",
    "description": "風を読み、遠くから仲間の道を射ひらく弓使い。立ち止まって研いだ「集中」を一矢に込め、標の矢で仲間の攻撃を導く。",
    "color": "#9FD8FF",
    "icon": "amemaru:textures/gui/job/archer.png",
    "weapons": "amemaru:weapons/archer",
    "weapon_text": "弓",
    "tags": ["ranged", "archer"],
    "stats": {"percent": {"attack": 15, "hp": -10, "defense": -10, "magic": -20}, "flat": {"crit": 3, "speed": 2}},
    "gauge": {
        "type": "focus",
        "name": "集中",
        "description": "立ち止まっている間と、矢を当てるとたまる。射抜き・天穿ち・流れ星の一矢で使う。戦闘が終わると0に戻る。",
        "max": 100,
        "color": "#9FD8FF",
        "still_gain": 6,
        "hit_gain": 5,
    },
    "schools": [
        {"id": "sniping", "name": "狙撃",
         "description": "一矢を研ぎすます流派。標の矢で仲間を導き、縫い止めで足を止め、集中を込めた矢で遠くから射抜く。",
         "color": "#9FD8FF"},
        {"id": "rapid", "name": "速射",
         "description": "矢継ぎ早に射る流派。扇に広げ、雨と降らせ、跳ねさせ、動きながら群れを射すくめる。",
         "color": "#FFB37A"},
    ],
    "starting_skills": ["archer/marking_arrow", "archer/spread_arrows"],
    "starter_kit": [{"item": "amemaru:apprentice_bow"}],
    "upper": {
        "name": "天弓",
        "description": "星の高さまで矢を届かせる射手。奥義（狙撃の「流れ星の一矢」か、速射の「疾風の弓舞」）と、各スキルの第5段が開く。",
        "min_level": 60,
        "min_day": 11,
    },
    # 11月26日のご指示「遠距離職は攻撃の際にスキルなどは硬直やほぼほぼ動けないスロウ効果などをつけましょう。動きながら打てるのが
    # 強すぎる。…アーチャーの調整が要検討が必要」【提案】：矢を放つ瞬間だけ足を止める（0.15＋クールダウンの秒×0.03秒。0.25〜0.6秒、
    # ほぼ止まる）。通常の射撃は止めず、射るたびに0.9秒だけ足が60%遅い（押し続けている間はずっと遅い。弓を引きながら歩く）。
    # 引き絞り（溜め）と流れ星の一矢の詠唱の間は、ほぼ止まる。後跳び（逃げる技）と、強化の技にはつかない
    "_comment_attack_lock": "11月26日【提案】：矢を放つ瞬間だけ足を止める（0.25〜0.6秒）。通常の射撃は射っている間だけ足が60%遅い。"
                            "引き絞りと詠唱の間はほぼ止まる。後跳びと強化の技にはつかない",
    "attack_lock": {
        "skill": {"base": 0.15, "per_cooldown": 0.03, "min": 0.25, "max": 0.6, "level": 3},
        "chant_level": 3,
        "charge_level": 3,
        "basic": {"seconds": 0.9, "level": 2},
    },
}


def arrow(range_=32, speed=2.6, **kw):
    return d_projectile(range_, speed=speed, kind="arrow", **kw)


# ================================================================ 狙撃
active(J, "marking_arrow", "sniping", 0, 0, "標の矢",
       "敵に光の標を射込む。標のある敵は、仲間みんなから受けるダメージが上がる。",
       mp=8, cd=6, link="starter", delivery=arrow(40), effects=[dmg("attack", (1.0, 0.15)), mark((10, 3), (10, 2))],
       tags=["arrow"], anim="archer_shot")
active(J, "piercing_shot", "sniping", 1, 0, "射抜き",
       "集中を矢に込めて、一点を射抜く。集中を残らず使い、使った分だけ威力が上がる（集中が30以上ないと射てない）。",
       mp=10, cd=8, gauge_cost=30, link="finisher", delivery=arrow(40, speed=3.0),
       effects=[gauge_burst(0.012), dmg("attack", (2.2, 0.35))], tags=["arrow"], anim="archer_heavy",
       requires="archer/marking_arrow")
passive(J, "hawk_eye", "sniping", 1, "鷹の目", "遠くの獲物の、わずかな隙も見逃さない。会心が上がる。", "hawk_eye", (3, 2),
        requires="archer/marking_arrow")
active(J, "pinning_arrow", "sniping", 2, 0, "縫い止め",
       "敵の影を地面に縫いとめる矢。当たった敵は、しばらくその場から動けなくなる（攻撃はしてくる）。",
       mp=12, cd=12, link="starter", delivery=arrow(30), effects=[dmg("attack", (0.9, 0.15)), root((2.0, 0.5))],
       tags=["arrow"], anim="archer_shot", requires="archer/piercing_shot")
active(J, "full_draw", "sniping", 2, 1, "引き絞り",
       "弓を限界まで引き絞り、一直線に貫く矢を放つ。引くほど強くなり、引ききると敵の守りを砕く。",
       mp=16, cd=9, charge=(1.6, 1.0, 2.4), delivery=d_line(40, 1.0), effects=[dmg("attack", (1.4, 0.2))],
       full=[armor_break(6)], tags=["arrow"], anim="archer_release", charge_anim="archer_draw",
       requires="archer/piercing_shot")
passive(J, "still_focus", "sniping", 2, "静止集中",
        "足を止めると、心が研ぎ澄まされる。立ち止まっている間の集中のたまり方が上がる。", "still_focus", (40, 20),
        requires="archer/hawk_eye")
active(J, "hawks_gaze", "sniping", 3, 0, "鷹の眼差し",
       "鷹の目で戦場を見渡す。しばらく会心が大きく上がり、集中が20たまる。",
       mp=18, cd=30, delivery=d_self(), effects=[buff("crit_up", (8, 1), (15, 5)), gauge(20)], anim="archer_gaze",
       requires="archer/pinning_arrow")
active(J, "drill_arrow", "sniping", 3, 1, "穿ち矢",
       "螺旋を描く矢で、並んだ敵をまとめて貫く。標のある敵には、さらに深く刺さる。",
       mp=18, cd=10, link="finisher", delivery=d_line(32, 1.2),
       effects=[dmg("attack", (1.8, 0.3), bonus_status="amemaru:hunter_mark", bonus=1.3)], tags=["arrow"],
       anim="archer_heavy", requires="archer/full_draw")
passive(J, "long_shot", "sniping", 3, "遠矢",
        "遠くから射るほど、矢は重くなる。6ブロックより遠い敵へのダメージが、1ブロックごとに1%上がる（上限あり）。", "long_shot",
        (15, 5), requires="archer/still_focus")
active(J, "sniper_stance", "sniping", 4, 0, "狙撃の構え",
       "片ひざをつき、狙いを定める。しばらく足が遅くなるかわりに、次に放つ矢3本（スキルと通常の射撃）の威力が上がり、集中が10たまる。",
       mp=16, cd=20, delivery=d_self(), effects=[buff("aim", 8, (40, 10), count=3), gauge(10)], anim="archer_kneel",
       requires="archer/hawks_gaze")
passive(J, "weak_point", "sniping", 4, "弱点看破",
        "動けない敵、標のある敵の急所が見える。標のある敵と、縫い止められた敵への会心ダメージが上がる。", "weak_point", (15, 5),
        requires="archer/long_shot")
active(J, "sky_piercer", "sniping", 5, 0, "天穿ち",
       "集中を50使い、空ごと穿つ一矢を引き絞る。とても遠くまで届き、敵の防御の半分を無視して貫く。",
       mp=28, cd=24, gauge_cost=50, link="finisher", charge=(2.0, 1.0, 2.0), delivery=d_line(64, 1.0),
       effects=[dmg("attack", (3.0, 0.5), pierce=50)], tags=["arrow"], anim="archer_release", charge_anim="archer_draw_sky",
       requires="archer/sniper_stance")
passive(J, "focus_mastery", "sniping", 5, "集中の極意",
        "使った集中は、次の一矢のために戻ってくる。スキルで使った集中の一部が戻る。", "focus_refund", (30, 10),
        requires="archer/weak_point")
ultimate(J, "shooting_star", "sniping", "流れ星の一矢",
         "天弓の奥義。流れ星を矢にして放ち、一直線のすべてを貫く。標のある敵には2倍の傷を刻む。集中が残っていれば、それも込める。",
         mp=40, cd=90, cast=1.0, delivery=d_line(64, 1.6),
         effects=[gauge_burst(0.005), dmg("attack", 6.0, bonus_status="amemaru:hunter_mark", bonus=2.0)],
         requires="archer/piercing_shot", tags=["arrow"], anim="archer_star_release", cast_anim="archer_star_cast",
         cast_sound="amemaru:skill.archer.shooting_star_cast")

# ================================================================ 速射
active(J, "spread_arrows", "rapid", 0, 0, "拡散矢",
       "矢を扇に広げて一度に放つ。段が上がると矢が増える。",
       mp=8, cd=5, delivery=arrow(28, count=(3, 1), spread=40), effects=[dmg("attack", (0.8, 0.12))], tags=["arrow"],
       anim="archer_fan")
active(J, "back_leap", "rapid", 1, 0, "後跳び",
       "後ろへ大きく跳びながら、前へ一矢を放つ。当たった敵は足が鈍る。",
       mp=10, cd=(9, -1), delivery=d_backstep(6), effects=[dmg("attack", (1.0, 0.2)), slow(2, 2)], tags=["arrow"],
       anim="archer_back", requires="archer/spread_arrows")
passive(J, "nimble_archer", "rapid", 1, "身軽な射手",
        "軽い装いで、戦場を駆ける。弓を持っていると、移動が速くなる。", "nimble_archer", (4, 3), requires="archer/spread_arrows")
active(J, "arrow_rain", "rapid", 2, 0, "矢の雨",
       "空へ射た矢が、狙った地点に雨となって降りそそぐ。しばらく降り続ける。",
       mp=22, cd=(14, -1), link="finisher", delivery=d_point(24, (4, 0.5), delay=0.6),
       effects=[dmg("attack", (0.6, 0.1)), zone((4, 0.5), 3, 0.5, [dmg("attack", (0.45, 0.08))])], tags=["arrow"],
       anim="archer_sky", impact_sound="amemaru:skill.archer.arrow_rain_impact", requires="archer/back_leap")
active(J, "triple_shot", "rapid", 2, 1, "三連矢",
       "息つく間もなく、三本の矢を続けて放つ。",
       mp=12, cd=6, delivery=arrow(32, burst=3, burst_interval=4), effects=[dmg("attack", (0.9, 0.15))], tags=["arrow"],
       anim="archer_triple", requires="archer/back_leap")
passive(J, "rapid_mastery", "rapid", 2, "速射の心得",
        "引いて、放つ。その動きが体に染みている。速射の流派のスキルのクールダウンが短くなる。", "school_haste", (6, 3),
        requires="archer/nimble_archer")
active(J, "burst_arrow", "rapid", 3, 0, "爆ぜ矢",
       "結晶の火薬を仕込んだ矢。当たった所で弾け、まわりの敵をまとめて吹き飛ばす。",
       mp=18, cd=10, link="finisher", delivery=arrow(30, explode=(3, 0.5)), effects=[dmg("attack", (1.6, 0.25)), kb(0.5)],
       tags=["arrow"], anim="archer_shot", impact_sound="amemaru:skill.archer.burst_arrow_impact",
       requires="archer/arrow_rain")
active(J, "whirl_arrows", "rapid", 3, 1, "旋風矢",
       "その場で身をひるがえし、まわりへ矢を放つ。近づいた敵を吹き飛ばし、しばらく足が速くなる。",
       mp=16, cd=12, delivery=d_around((5, 0.5)),
       effects=[dmg("attack", (1.0, 0.15)), kb(0.6), dict(buff("haste", 3, 20), self=True)], tags=["arrow"],
       anim="archer_spin", requires="archer/triple_shot")
passive(J, "chained_arrows", "rapid", 3, "連なる矢",
        "矢が当たるたびに、次の矢が狙いやすくなる。2秒以内に矢を当て続けると、1本ごとにダメージが上がる（10本まで）。",
        "arrow_chain", (2, 1), requires="archer/rapid_mastery")
# 11月24日のご指示「アーチャーだと跳弾する矢」：前は Lv20 の段（1章では届かなかった）。2段目（Lv10）へ移した
active(J, "ricochet", "rapid", 2, 3, "跳ね矢",
       "敵から敵へ跳ねる矢。狙った敵から、近くの敵へ次々に当たる。",
       mp=14, cd=8, delivery=d_chain(30, (3, 1), 8, 0.85), effects=[dmg("attack", (1.2, 0.18))], tags=["arrow"],
       anim="archer_shot", requires="archer/spread_arrows")
passive(J, "wind_reading", "rapid", 4, "風読み",
        "風を読み、矢の軌道を整える。矢のスキルのダメージが上がり、矢が速く遠くまで飛ぶ。", "wind_reading", (5, 2.5),
        requires="archer/chained_arrows")
active(J, "arrow_storm", "rapid", 5, 0, "矢嵐",
       "矢筒が空になるまで射続ける。3秒の間、まわり16ブロックでいちばん近い敵へ、次々に矢を放つ（その間は足が遅くなる）。",
       mp=30, cd=20, delivery=d_self(),
       effects=[zone(16, 3, 0.25, [dmg("attack", (0.5, 0.08))], follow=True, mode="nearest"),
                dict(status("minecraft:slowness", 3, 2), self=True)],
       tags=["arrow"], anim="archer_storm", requires="archer/ricochet")
passive(J, "quiver_master", "rapid", 5, "矢筒の名手",
        "矢筒の中身を知り尽くしている。速射の流派のスキルを使うと、ときどきクールダウンが半分になる。", "quiver_master", (15, 5),
        requires="archer/wind_reading")
ultimate(J, "gale_dance", "rapid", "疾風の弓舞",
         "天弓の奥義。風をまとって舞うように射る。10秒の間、足が速くなり、矢のダメージが上がり、スキルのクールダウンが早く戻る。",
         mp=50, cd=120, delivery=d_self(), effects=[buff("bow_dance", 10, 30)], requires="archer/arrow_rain",
         anim="archer_dance")

# ================================================================ 11月24日に足したスキル（スキルツリーの作り直し）
active(J, "quick_draw", "rapid", 1, 3, "速射",
       "弦を引ききらずに、狙った敵へ3本の矢をたて続けに放つ。",
       mp=9, cd=4, delivery=d_projectile(26, speed=2.4, burst=3, burst_interval=3), effects=[dmg("attack", (0.55, 0.08))],
       tags=["arrow"], anim="archer_triple", requires="archer/spread_arrows")
active(J, "flash_arrow", "sniping", 2, 3, "閃光矢",
       "鏃に光る石を付けた矢。当たると強く光り、敵の目をくらませる（攻撃が当たりにくくなる）。",
       mp=14, cd=10, delivery=d_projectile(24, speed=2.2), effects=[dmg("attack", (1.1, 0.18)), blind((30, 5), 4)],
       tags=["arrow"], anim="archer_shot", requires="archer/piercing_shot")
active(J, "wind_cloak", "rapid", 3, 3, "風纏い",
       "風を身にまとい、しばらく足が速くなり、与えるダメージも少し上がる。",
       mp=16, cd=28, cdpr=-1.5, delivery=d_self(), effects=[buff("haste", (6, 1), (20, 4)), buff("power_up", (6, 1), (8, 2))],
       anim="archer_dance", requires="archer/triple_shot")
active(J, "hunters_focus", "sniping", 3, 3, "狩人の集中",
       "息を止め、獲物だけを見る。しばらく自分の与えるダメージが上がる。",
       mp=18, cd=36, cdpr=-2, delivery=d_self(), effects=[buff("power_up", (10, 1), (15, 4))], anim="archer_gaze",
       requires="archer/full_draw")
active(J, "arrow_fan", "rapid", 4, 0, "扇射",
       "前へ扇のように5本の矢を放ち、当たった敵を押し返す。",
       mp=22, cd=11, cdpr=-0.5, delivery=d_projectile(20, speed=2.2, count=5, spread=50), effects=[dmg("attack", (1.0, 0.15)), kb(0.4)],
       tags=["arrow"], anim="archer_fan", requires="archer/burst_arrow")
