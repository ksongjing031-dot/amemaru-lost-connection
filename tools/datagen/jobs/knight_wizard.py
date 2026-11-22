"""ナイトとウィザードに足すスキル（11月22日のご指示「スキルとパッシブを増やす」）。

今あるスキル（Lv1〜15の段）は手で書いた JSON のまま。ここでは Lv20・Lv25 の段に、流派ごとにアクティブ2つ・パッシブ2つを足す。
"""
from common import *

# ================================================================ ナイト：守護
K = "knight"
active(K, "iron_pledge", "guardian", 4, 0, "鉄の誓約",
       "剣を掲げて誓いを立て、まわりの仲間すべてと鎖の糸で結ぶ。8秒、仲間の受けるダメージの一部を肩代わりする。",
       mp=26, cd=30, link="support", delivery=d_around(8, allies=True),
       effects=[{"type": "shield_bond", "duration": 8, "share": 15, "share_per_rank": 5}], anim="knight_vow",
       requires="knight/oath_circle")
passive(K, "steadfast_shield", "guardian", 4, "揺るがぬ盾",
        "構えた盾は、決して揺るがない。城壁の構え・受け流し・誓いの円陣の間、受けるダメージがさらに減る。", "steadfast_shield",
        (5, 3), requires="knight/taunt_mastery")
active(K, "shield_bash", "guardian", 5, 0, "盾撃",
       "盾ごと体をぶつけ、敵をよろめかせて引きつける。誓気が15たまる。",
       mp=14, cd=10, link="starter", delivery=d_target(4.5),
       effects=[dmg("attack", (1.6, 0.25)), stagger(1.5), taunt(4), gauge(15)], anim="knight_bash",
       requires="knight/iron_pledge")
passive(K, "guardians_heart", "guardian", 5, "守り手の心",
        "仲間が危ないとき、誓いは燃え上がる。まわりの仲間のHPが3割を切ると、誓気が20たまり、5秒受けるダメージが減る（20秒に1回）。",
        "guardian_heart", (15, 5), radius=12, requires="knight/steadfast_shield")

# ================================================================ ナイト：剛剣
active(K, "great_wheel", "greatsword", 4, 0, "大車輪",
       "両手剣を振り回し、竜巻のように回り続ける（2秒）。回っている間も歩ける。誓気が20以上あれば、20使って1.3倍。",
       mp=22, cd=16, delivery=d_self(),
       effects=[gauge_empower(20, 1.3), zone(3.5, 2, 0.4, [dmg("attack", (0.6, 0.1)), kb(0.2)], follow=True)],
       anim="knight_great_wheel", requires="knight/oath_release")
passive(K, "greatsword_mastery", "greatsword", 4, "剛剣の極意",
        "剛剣の型を極めた。剛剣の流派のスキルのダメージが上がる。", "school_power", (5, 3), requires="knight/fighting_spirit")
active(K, "earth_splitter", "greatsword", 5, 0, "地裂斬",
       "大地ごと断ち割る一撃。地を走る裂け目が前の敵を打ち上げ、よろめいている敵には深く入る。",
       mp=24, cd=14, link="finisher", delivery=d_line(12, 2.0, flat=True),
       effects=[dmg("attack", (2.4, 0.35), bonus_status="amemaru:stagger", bonus=1.3), launch(0.6)],
       anim="knight_earth_split", requires="knight/great_wheel")
passive(K, "blade_chain", "greatsword", 5, "連ね斬り",
        "剛剣の技を途切れなくつなぐ。3秒以内に違う剛剣のスキルを続けて当てると、1つごとにダメージが上がる（3つまで）。",
        "blade_chain", (6, 3), requires="knight/greatsword_mastery")

# ================================================================ ウィザード：星灯
W = "wizard"
active(W, "star_ring", "starlight", 4, 0, "星の環",
       "小さな星を5つ呼び、自分のまわりを巡らせる（10秒）。近づいた敵に星がぶつかってダメージを与え、星痕を刻む。",
       mp=22, cd=18, delivery=d_self(), effects=[buff("star_ring", 10, (0.6, 0.1), count=5)], anim="wizard_star_ring",
       requires="wizard/light_veil")
passive(W, "star_guide", "starlight", 4, "星の導き",
        "星痕は、仲間の攻撃も導く。星痕のある敵は、星痕1つにつき、誰から受けるダメージも上がる。", "star_guide", (2, 1),
        requires="wizard/star_afterglow")
active(W, "comet", "starlight", 5, 0, "彗星",
       "長い尾を引く彗星を撃ち出す。当たった所で弾け、まわりの敵に大きなダメージを与え、星痕を2つ刻む。",
       mp=28, cd=12, cast=0.8, link="finisher", delivery=d_projectile(30, speed=1.2, kind="comet", radius=0.6, explode=4),
       effects=[dmg("magic", (2.8, 0.4)), brand(2, 8)], anim="wizard_comet", cast_anim="wizard_comet_cast",
       impact_sound="amemaru:skill.wizard.comet_impact", requires="wizard/star_ring")
passive(W, "starry_sky", "starlight", 5, "満天",
        "星が弾けるたび、次の星が近づく。星痕を弾けさせると、星痕1つにつき、星灯の流派のスキルのクールダウンが縮む。",
        "starry_sky", (0.3, 0.15), requires="wizard/star_guide")

# ================================================================ ウィザード：氷結
active(W, "blizzard", "frost", 4, 0, "吹雪",
       "狙った地点に吹雪を起こす（6秒）。吹雪の中の敵を打ち続け、凍えを重ねていく（吹雪だけでは4つまで）。",
       mp=30, cd=18, delivery=d_point(20, (5, 0.5)),
       effects=[zone((5, 0.5), 6, 0.5, [dmg("magic", (0.3, 0.05)), chill(1, 2, cap=4)])], anim="wizard_blizzard",
       requires="wizard/thunderbolt")
passive(W, "thunder_mastery", "frost", 4, "雷の心得",
        "雷の扱いに長けている。感電した敵へのダメージが上がり、感電が長く続く。", "thunder_mastery", (5, 3),
        requires="wizard/deep_chill")
active(W, "absolute_zero", "frost", 5, 0, "絶対零度",
       "まわりの空気を一瞬で凍らせる。凍えが2つ以上の敵はすぐに凍結し、ほかの敵には凍えが3つ重なる。",
       mp=30, cd=20, link="starter", delivery=d_around((6, 0.5)),
       effects=[dmg("magic", (1.2, 0.2)), chill(3, 4, 5, 2.5, 2)], anim="wizard_absolute_zero", requires="wizard/blizzard")
passive(W, "permafrost", "frost", 5, "凍土",
        "砕けた氷は、まわりも凍らせる。凍結を砕くと、氷がまわりの敵に飛び散ってダメージを与え、凍えを重ねる。", "permafrost",
        (0.8, 0.2), requires="wizard/thunder_mastery")
