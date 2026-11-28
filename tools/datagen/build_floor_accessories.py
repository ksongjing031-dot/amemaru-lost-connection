"""階ごとのアクセサリーと、その材料の素材を書き出す（11月24日のご指示「階層ごとにアクセサリーも追加しましょう」）。

  python3 -I tools/datagen/build_floor_accessories.py

決め方【補完】：
  - 1階の品（苔鉄の闘環・智環・草原の首飾り・石くれの腕輪・道標のお守り。Phase 0-A 21章の第1章の一覧）のあとに、
    2〜5階でも、その階の素材で作る5つ（闘環・智環・首飾り・腕輪・お守り）を装飾師が作れるようにする。
    メインステータスの上限は、章の1つ目の階を1倍として、1階ごとに +5%（AccessoryDef#mainFactor）。サブの幅・厳選・強化は章のまま
  - 2〜5階の名のある魔物（中ボス）が、その階の「レアのお守り」（固有の特殊効果つき。光らないお守りで、体には出さない）を落とす
  - 2階には素材が無かったので、4種類の敵から1つずつ素材を足す（古樹の樹皮・光茸のかさ・迷い灯の芯・葉影の尾）
  - 4階・5階の素材もここで決める（敵のドロップは、4階・5階の敵を作るときに入れる）

書き出すもの：
  data/amemaru/amemaru/accessories/<ID>.json・data/amemaru/recipes/workshop/<ID>.json・
  src/main/resources/amemaru/item_types.json（アクセサリーと素材の一覧に足す）・言語ファイル（日本語と同じものを en_us にも）・
  models/item/<ID>.json（持ち物の絵は tools/artgen/make_floor_accessory_art.py）
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data", "amemaru")
ASSETS = os.path.join(RES, "assets", "amemaru")

# ---------------------------------------------------------------- 素材
# (ID, 名前, レアリティ, 階, 説明)
MATERIALS = [
    ("old_bark", "古樹の樹皮", "common", 2,
     "樹皮兵の体からはがれた、古い樹の皮。古代の管の錆が染みて、ところどころ赤い。2階のアクセサリーの材料になる。"),
    ("glowcap", "光茸のかさ", "common", 2,
     "ポコダケの頭の、霧の中で淡く光るかさ。乾かしても、しばらく光が消えない。2階のアクセサリーの材料になる。"),
    ("wisp_wick", "迷い灯の芯", "uncommon", 2,
     "迷い灯の奥で燃えていた、細い光の芯。持つと、少しだけ道が分かる気がする。2階のアクセサリーの材料になる。"),
    ("shade_tail", "葉影の尾", "uncommon", 2,
     "葉影ギツネのふさふさした尾の毛。光を吸う、黒みがかった緑。2階のアクセサリーの材料になる。"),
    ("statue_shard", "石像のかけら", "common", 4,
     "動き出した石像から欠け落ちた、白い石のかけら。表面に、古代の祈りの文字が薄く残る。4階の装備とアクセサリーの材料になる。"),
    ("song_crystal", "歌声の結晶", "common", 4,
     "祈り手の残響が消えたあとに残る、小さな結晶。耳を近づけると、かすかに歌が聞こえる。4階のアクセサリーの材料になる。"),
    ("beetle_shell", "甲虫の結晶殻", "uncommon", 4,
     "結晶甲虫の背中の殻。刃は通さないが、魔力には弱い。4階のアクセサリーの材料になる。"),
    ("sacred_mane", "聖獣のたてがみ", "uncommon", 4,
     "神殿の守護獣の、金色のたてがみ。ところどころ、白く色が抜けている。4階のアクセサリーの材料になる。"),
    ("prayer_stone", "祈り石", "rare", 4,
     "神殿の祭壇に長く置かれていた、乳白色の石。手のひらにのせると、温かい。4階の名のある魔物とボスが持つ。"),
    ("watcher_lens", "監視眼のレンズ", "common", 5,
     "監視眼ウォッチャーの目の奥にあった、薄い水晶のレンズ。のぞくと、遠くの物が少し近く見える。5階のアクセサリーの材料になる。"),
    ("sentinel_plate", "防衛兵の装甲片", "common", 5,
     "防衛兵センチネルの盾と体をおおっていた、真鍮の板。古代の継ぎ目の模様がある。5階の装備とアクセサリーの材料になる。"),
    ("drone_spring", "修復ドローンのぜんまい", "uncommon", 5,
     "修復ドローンの中で回っていた、細いぜんまい。まだ少しだけ、ほどけようとする力が残っている。5階のアクセサリーの材料になる。"),
    ("jade_core_shard", "翠の核片", "rare", 5,
     "古代の防衛兵器の胸にはまる、翠の結晶のかけら。中で、細い光の糸が結び目を作っている。5階の名のある魔物とボスが持つ。"),
]

# ---------------------------------------------------------------- アクセサリー
# 効き目の説明（言語ファイルの .effect。数値は effects と同じにする）


def effect_text(effect):
    kind = effect["type"]
    if kind == "extra_damage":
        return "攻撃が当たると%d%%の確率で、与えたダメージの%d%%を追加で与える" % (effect["chance"], effect["percent"])
    if kind == "damage_reduction":
        return "敵から受けるダメージ −%s%%" % fmt(effect["percent"])
    if kind == "heal_on_hit":
        return "攻撃が当たると%d%%の確率で、与えたダメージの%d%%だけHPが回復する" % (effect["chance"], effect["percent"])
    if kind == "execute":
        return "HPが%d%%以下の敵に与えるダメージ +%d%%" % (effect["threshold"], effect["percent"])
    if kind == "move_speed":
        return "移動の速さ +%s%%" % fmt(effect["percent"])
    if kind == "regen":
        return "5秒ごとに、最大HPの%s%%だけHPが回復する" % fmt(effect["percent"])
    if kind == "cold_resist":
        return "寒さ（凍え・凍結）と、遅くなる効果の時間 −%s%%" % fmt(effect["percent"])
    if kind == "lost_resist":
        return "ロスト耐性 +%s%%（ロスト状態の時間が短くなる）" % fmt(effect["percent"])
    if kind == "ally_power":
        return "%sブロック以内の味方1人ごとに、与えるダメージ +%s%%（5人まで）" % (fmt(effect["threshold"]), fmt(effect["percent"]))
    raise ValueError(kind)


def fmt(value):
    return ("%d" % value) if abs(value - round(value)) < 1e-9 else ("%.1f" % value)


def ing(*pairs):
    return [{"item": "amemaru:" + name, "count": count} for name, count in pairs]


# (ID, 名前, 枠, メイン（None は枠のとおり）, 階, レアリティ, 体に出すか, レシピ（None は作れない）, 熟練度, 熟練の経験, 効き目, 説明)
ACCESSORIES = [
    # 2階「忘れられた森」：古樹の樹皮と光茸の、森の灯のひと揃い
    ("bark_battle_ring", "樹皮の闘環", "ring", ["attack"], 2, "uncommon", False,
     ing(("old_bark", 3), ("moss_iron", 2), ("glowcap", 1)), 2, 12, [],
     "古樹の樹皮を苔鉄の芯に巻きつけた指輪。光茸の粉で、継ぎ目が淡く光る。"),
    ("bark_wisdom_ring", "樹皮の智環", "ring", ["magic"], 2, "uncommon", False,
     ing(("old_bark", 3), ("moss_iron", 2), ("wisp_wick", 1)), 2, 12, [],
     "樹皮の指輪の内側に、迷い灯の芯を一筋だけ通したもの。魔力の流れを、迷わず指先へ導く。"),
    ("glowcap_necklace", "光茸の首飾り", "necklace", None, 2, "uncommon", True,
     ing(("glowcap", 3), ("shade_tail", 1), ("crystal_shard", 1)), 2, 12, [],
     "葉影の尾の毛を撚った紐に、光茸のかさを3つ下げた首飾り。霧の中でも、仲間に自分の場所を知らせる。"),
    ("rootknot_bracelet", "根結びの腕輪", "bracelet", None, 2, "uncommon", True,
     ing(("old_bark", 4), ("ancient_stone", 2), ("moss_iron", 1)), 2, 12, [],
     "古代の管に巻きついていた根の形を写して、樹皮と石で編んだ腕輪。ほどけにくい結び目が、腕を守る。"),
    ("wisp_charm", "迷い灯のお守り", "charm", None, 2, "uncommon", True,
     ing(("wisp_wick", 2), ("shade_tail", 1), ("echo_fragment", 1)), 2, 12, [],
     "小さな灯籠に迷い灯の芯を入れ、腰に下げるお守り。もう道に迷わないように、という願いを込めて作る。"),
    ("shade_tail_charm", "葉隠れの尾飾り", "charm", None, 2, "rare", False, None, 0, 0,
     [{"type": "extra_damage", "chance": 10, "percent": 25}],
     "葉隠れの長の尾の先の、ひときわ濃い毛を束ねた飾り。死角からの一撃が、ときどき深く入る。2階の名のある魔物が落とす。"),
    # 3階「古代の水路」：水路の青銅と流水の結晶のひと揃い
    ("bronze_battle_ring", "水路銅の闘環", "ring", ["attack"], 3, "uncommon", False,
     ing(("waterway_bronze", 3), ("gnawed_cog", 2), ("catfish_whisker", 1)), 2, 14, [],
     "水路の青銅を打ち延ばした指輪。歯車の歯の形の刻みが、握りを確かにする。"),
    ("bronze_wisdom_ring", "水路銅の智環", "ring", ["magic"], 3, "uncommon", False,
     ing(("waterway_bronze", 3), ("murky_gel", 2), ("catfish_whisker", 1)), 2, 14, [],
     "澱みのゼリーで磨いた、緑青の青い指輪。指にはめると、魔力の水がゆっくり巡る。"),
    ("clearwater_necklace", "澄み水の首飾り", "necklace", None, 3, "uncommon", True,
     ing(("flow_crystal", 1), ("murky_gel", 3), ("catfish_whisker", 1)), 3, 16, [],
     "ナマズのひげを撚った紐に、流水の結晶の雫を下げた首飾り。結晶の中の水は、決して濁らない。"),
    ("cogwheel_bracelet", "歯車の腕輪", "bracelet", None, 3, "uncommon", True,
     ing(("gnawed_cog", 4), ("waterway_bronze", 2)), 2, 14, [],
     "かじられた歯車を4つ噛み合わせた、ごつい腕輪。歯がそろうと、ぴたりと止まる。"),
    ("whisker_charm", "ナマズひげのお守り", "charm", None, 3, "uncommon", True,
     ing(("catfish_whisker", 2), ("murky_gel", 2), ("echo_fragment", 1)), 3, 16, [],
     "青銅の小さな水門の札に、ナマズのひげを結んだお守り。水の流れを読む、水路の番人の守り。"),
    ("water_mirror_charm", "水鏡のお守り", "charm", None, 3, "rare", False, None, 0, 0,
     [{"type": "damage_reduction", "percent": 3}],
     "水門の底で見つかる、鏡のように澄んだ水の膜を閉じこめた札。受けた一撃の、ほんの少しを水が受け流す。3階の名のある魔物が落とす。"),
    # 4階「崩壊した神殿」：祈り石と鈴の音のひと揃い
    ("prayer_battle_ring", "祈り石の闘環", "ring", ["attack"], 4, "uncommon", False,
     ing(("statue_shard", 3), ("beetle_shell", 2), ("prayer_stone", 1)), 3, 18, [],
     "祈り石を石像のかけらの台に埋めた指輪。拳を握ると、石が少し温かくなる。"),
    ("prayer_wisdom_ring", "祈り石の智環", "ring", ["magic"], 4, "uncommon", False,
     ing(("statue_shard", 3), ("song_crystal", 2), ("prayer_stone", 1)), 3, 18, [],
     "祈り石のまわりに、歌声の結晶を細かくはめた指輪。耳をすますと、祈りの歌が聞こえる。"),
    ("chime_necklace", "鈴音の首飾り", "necklace", None, 4, "uncommon", True,
     ing(("song_crystal", 3), ("sacred_mane", 1), ("crystal_shard", 1)), 3, 18, [],
     "聖獣のたてがみの紐に、歌声の結晶で作った小さな鈴を下げた首飾り。歩くたびに、澄んだ音がする。"),
    ("mural_bracelet", "壁画の腕輪", "bracelet", None, 4, "uncommon", True,
     ing(("statue_shard", 4), ("beetle_shell", 2)), 3, 18, [],
     "手をつなぐ古代の人の壁画を、石像のかけらに彫って輪にした腕輪。輪の上で、人の列がどこまでも続く。"),
    ("mane_charm", "聖獣の毛のお守り", "charm", None, 4, "uncommon", True,
     ing(("sacred_mane", 2), ("song_crystal", 1), ("echo_fragment", 1)), 3, 18, [],
     "聖獣のたてがみを、白い石の輪に巻いて下げるお守り。神殿を守った獣の、最後の勇気をもらう。"),
    ("broken_seal_charm", "欠けた聖印", "charm", None, 4, "rare", False, None, 0, 0,
     [{"type": "heal_on_hit", "chance": 12, "percent": 5}],
     "神殿の祈り手が首にかけていた、欠けた石の聖印。打つたびに、祈りの残り香が傷をふさぐ。4階の名のある魔物が落とす。"),
    # 5階「古代守護者の祭壇」：真鍮と翠のひと揃い
    ("brass_battle_ring", "真鍮の闘環", "ring", ["attack"], 5, "uncommon", False,
     ing(("sentinel_plate", 3), ("drone_spring", 2), ("jade_core_shard", 1)), 3, 20, [],
     "防衛兵の装甲を打ち直した真鍮の指輪。翠の核片の粒が、継ぎ目に光る。"),
    ("brass_wisdom_ring", "真鍮の智環", "ring", ["magic"], 5, "uncommon", False,
     ing(("sentinel_plate", 3), ("watcher_lens", 2), ("jade_core_shard", 1)), 3, 20, [],
     "監視眼のレンズを、真鍮の輪に小さくはめた指輪。のぞくと、魔力の結び目が見える。"),
    ("jade_necklace", "翠核の首飾り", "necklace", None, 5, "uncommon", True,
     ing(("jade_core_shard", 1), ("watcher_lens", 2), ("drone_spring", 2)), 3, 20, [],
     "翠の核片を真鍮の枠で包み、細い鎖で下げた首飾り。守護者の胸の結晶と、同じ色で光る。"),
    ("sentinel_bracelet", "防衛兵の腕輪", "bracelet", None, 5, "uncommon", True,
     ing(("sentinel_plate", 4), ("drone_spring", 2)), 3, 20, [],
     "防衛兵の盾の縁を、手首に合わせて曲げた腕輪。古代の兵の、ゆるがない守り。"),
    ("lens_charm", "監視眼のお守り", "charm", None, 5, "uncommon", True,
     ing(("watcher_lens", 2), ("drone_spring", 1), ("echo_fragment", 2)), 3, 20, [],
     "レンズをはめた真鍮の目玉のお守り。もう見つからないように、ではなく、仲間を見失わないように。"),
    ("altar_knot_charm", "結び台の欠片", "charm", None, 5, "rare", False, None, 0, 0,
     [{"type": "execute", "threshold": 30, "percent": 10}],
     "祭壇の結び台から欠け落ちた、真鍮と翠の小さなかけら。弱った敵への最後の一撃に、力を添える。5階の名のある魔物が落とす。"),
    # 6階「凍結した村」（第2章のはじめ。Phase 0-A 21章の第2章の一覧から：霜銀の闘環・智環・氷晶の首飾り・雪灯のお守り。
    # 腕輪は、6階の素材の「凍て毛皮の腕輪」にした【補完】。一覧の「凍て鶴の腕輪」は、凍て鶴の出る8階で作る）。素材は
    # tools/datagen/build_floor6_data.py（霜銀・氷晶・凍て毛皮・霜狼の牙・凍て鳥の羽根）
    # 第2章からのアクセサリーは、防具職人が織る「台座布」を1つ使う（0-A 19章の三すくみ【提案】。
    # 部品は tools/datagen/build_gear_frost_silver.py）
    ("frost_silver_battle_ring", "霜銀の闘環", "ring", ["attack"], 6, "uncommon", False,
     ing(("frost_silver", 3), ("frost_fang", 2), ("ice_crystal", 1), ("pedestal_cloth", 1)), 4, 22, [],
     "霜銀を打ち延ばした白い指輪。霜狼の牙を削った飾りが、拳の上で冷たく光る。"),
    ("frost_silver_wisdom_ring", "霜銀の智環", "ring", ["magic"], 6, "uncommon", False,
     ing(("frost_silver", 3), ("ice_feather", 2), ("ice_crystal", 1), ("pedestal_cloth", 1)), 4, 22, [],
     "霜銀の輪に、溶けない氷晶を一粒はめた指輪。のぞくと、凍った誰かの声が聞こえる気がする。"),
    ("ice_crystal_necklace", "氷晶の首飾り", "necklace", None, 6, "uncommon", True,
     ing(("ice_crystal", 3), ("frozen_fur", 1), ("frost_silver", 1), ("pedestal_cloth", 1)), 4, 22, [],
     "凍て毛皮の紐に、霜銀の留め金で氷晶の柱を下げた首飾り。どんな火のそばでも、決して溶けない。"),
    ("frozen_fur_bracelet", "凍て毛皮の腕輪", "bracelet", None, 6, "uncommon", True,
     ing(("frozen_fur", 4), ("frost_silver", 2), ("pedestal_cloth", 1)), 4, 22, [],
     "霜の獣の毛皮を手首に巻き、霜銀の留め具でとめた腕輪。雪の村の猟師たちが、冬の初めに作った。"),
    ("snowlight_charm", "雪灯のお守り", "charm", None, 6, "uncommon", True,
     ing(("ice_crystal", 2), ("ice_feather", 1), ("echo_fragment", 1), ("pedestal_cloth", 1)), 4, 22, [],
     "小さな霜銀の灯籠に、淡く光る氷晶を入れたお守り。凍った村の家々は、冬の夜にこの灯を戸口に下げた。"),
    ("white_fang_charm", "群れ長の白牙", "charm", None, 6, "rare", False, None, 0, 0,
     [{"type": "extra_damage", "chance": 12, "percent": 20}],
     "霜狼の群れ長の、白い牙を革紐で結んだ飾り。群れを率いた狼の、迷いのない一撃が宿る。6階の名のある魔物が落とす。"),
    ("bear_frost_charm", "霜熊の氷の棘", "charm", None, 6, "rare", False, None, 0, 0,
     [{"type": "cold_resist", "percent": 25}],
     "霜の欠け熊の主の背に生えていた、溶けない氷の棘。持つ者の体から、寒さを遠ざける。6階の名のある魔物が落とす。"),
    # 7階「氷の森林」（11月25日）：鏡氷と氷角のひと揃い。素材は tools/datagen/build_floor7_data.py（鏡氷・氷角・つらら蝙蝠の翼・
    # 吹雪の芯・プリズムの欠片）。第2章の品なので、防具職人の「台座布」を1つ使う。レアのお守りは、白角（名のある氷角鹿）と
    # 吹雪の大精が落とす。「吹雪の灯」のロスト耐性は、7階から出るロスト状態に（Phase 0-A 11章「ロスト耐性で短くなる」）【補完】
    ("mirror_ice_battle_ring", "鏡氷の闘環", "ring", ["attack"], 7, "uncommon", False,
     ing(("mirror_ice", 3), ("ice_antler", 2), ("prism_shard", 1), ("pedestal_cloth", 1)), 4, 24, [],
     "鏡氷を霜銀の芯に巻いた指輪。拳を握ると、映りこんだ自分の拳が、ほんの少し遅れて握られる。"),
    ("mirror_ice_wisdom_ring", "鏡氷の智環", "ring", ["magic"], 7, "uncommon", False,
     ing(("mirror_ice", 3), ("blizzard_core", 2), ("prism_shard", 1), ("pedestal_cloth", 1)), 4, 24, [],
     "鏡氷の輪に、プリズムの欠片を一粒はめた指輪。魔力を通すと、指先に小さな虹がかかる。"),
    ("ice_antler_necklace", "氷角の首飾り", "necklace", None, 7, "uncommon", True,
     ing(("ice_antler", 3), ("frozen_fur", 1), ("mirror_ice", 1), ("pedestal_cloth", 1)), 4, 24, [],
     "凍て毛皮の紐に、枝分かれした氷角のかけらを下げた首飾り。歩くたびに、角の先が鈴のように鳴る。"),
    ("icicle_bracelet", "つららの腕輪", "bracelet", None, 7, "uncommon", True,
     ing(("icicle_wing", 3), ("frost_silver", 2), ("pedestal_cloth", 1)), 4, 24, [],
     "つらら蝙蝠の翼の骨を、霜銀の留め具でとめた腕輪。外側に、溶けないつららが三本下がる。"),
    ("passphrase_charm", "合言葉の札", "charm", None, 7, "uncommon", True,
     ing(("mirror_ice", 2), ("blizzard_core", 1), ("echo_fragment", 1), ("pedestal_cloth", 1)), 4, 24, [],
     "鏡氷の小さな札に、仲間と決めた合言葉を刻んで腰に下げるお守り。名前を呼べなくなっても、同じ言葉を言えるように。"),
    ("white_horn_charm", "白角の欠片", "charm", None, 7, "rare", False, None, 0, 0,
     [{"type": "move_speed", "percent": 6}],
     "氷角鹿の主「白角」の角の先を、革紐で結んだ飾り。鏡の森を音もなく駆けた鹿の、軽い足どりが宿る。7階の名のある魔物が落とす。"),
    ("blizzard_lamp_charm", "吹雪の灯", "charm", None, 7, "rare", False, None, 0, 0,
     [{"type": "lost_resist", "percent": 20}],
     "吹雪の大精の衣のすそに灯っていた、オーロラ色の小さな灯。吹雪の中でも、仲間の名前を見失わない。7階の名のある魔物が落とす。"),
    # 8階「沈黙の湖」（11月25日）：静氷と湖の生きもののひと揃い。腕輪は Phase 0-A 21章の第2章の一覧の「凍て鶴の腕輪」（確定）。
    # ほかの名前と中身は【補完】。素材は tools/datagen/build_floor8_data.py（静氷・大魚の鱗・声石・凍て鶴の冠羽・霧の糸・湖の真珠）。
    # レアのお守りは、霜冠（名のある凍て鶴。「鶴は千年」で、HPが少しずつ戻る）と千声喰い（仲間の声を集めて力に変える）が落とす
    ("silent_ice_battle_ring", "静氷の闘環", "ring", ["attack"], 8, "uncommon", False,
     ing(("silent_ice", 3), ("fish_scale", 2), ("lake_pearl", 1), ("pedestal_cloth", 1)), 4, 26, [],
     "音を吸う静氷を、霜銀の芯に巻いた指輪。拳を握っても、音ひとつ立たない。"),
    ("silent_ice_wisdom_ring", "静氷の智環", "ring", ["magic"], 8, "uncommon", False,
     ing(("silent_ice", 3), ("voice_stone", 2), ("lake_pearl", 1), ("pedestal_cloth", 1)), 4, 26, [],
     "静氷の輪に、青く光る声石を一粒はめた指輪。魔力を通すと、遠い誰かの声が、かすかに指先へ響く。"),
    ("fish_scale_necklace", "大魚の鱗の首飾り", "necklace", None, 8, "uncommon", True,
     ing(("fish_scale", 3), ("mist_thread", 1), ("silent_ice", 1), ("pedestal_cloth", 1)), 4, 26, [],
     "霧の糸に、無音の大魚の白い鱗を重ねて下げた首飾り。光にかざすと、湖の底の色に変わる。"),
    ("frost_crane_bracelet", "凍て鶴の腕輪", "bracelet", None, 8, "uncommon", True,
     ing(("crane_plume", 3), ("frost_silver", 2), ("pedestal_cloth", 1)), 4, 26, [],
     "凍て鶴の冠羽を、霜銀の輪に編みこんだ腕輪。冬の朝の光の色をしている。"),
    ("bell_charm", "鐘の音のお守り", "charm", None, 8, "uncommon", True,
     ing(("silent_ice", 2), ("voice_stone", 1), ("echo_fragment", 1), ("pedestal_cloth", 1)), 4, 26, [],
     "静氷を小さな鐘の形に削ったお守り。振っても鳴らないが、仲間を呼びたいときだけ、胸の中で鳴る気がする。"),
    ("frost_crown_plume", "霜冠の冠羽", "charm", None, 8, "rare", False, None, 0, 0,
     [{"type": "regen", "percent": 1.5}],
     "凍て鶴の長「霜冠」の、七色に光る霜の冠羽。鶴は千年――持つ者の傷を、ゆっくりと癒やす。8階の名のある魔物が落とす。"),
    ("thousand_voice_stone", "千の声の喉石", "charm", None, 8, "rare", False, None, 0, 0,
     [{"type": "ally_power", "threshold": 8, "percent": 3}],
     "千声喰いののどの奥にあった、青い石。そばにいる仲間の声を集めて、力に変える。8階の名のある魔物が落とす。"),
    # 9階「氷結した王城」（11月28日）：近衛の氷と王家のひと揃い。名前と中身は【補完】。素材は tools/datagen/build_floor9_data.py
    # （王城の霜氷・近衛の氷・氷の蹄鉄・ほどけた頁・猟犬の青い首輪・前掛けのレース・王家の封蝋）。
    # レアのお守りは、霜蹄の旗手（号令を受けたように足が前へ出る）と綴じ手（忘れたくない名前の頁を、抜け落ちないように綴じる）が落とす
    ("guard_ice_battle_ring", "近衛氷の闘環", "ring", ["attack"], 9, "uncommon", False,
     ing(("guard_ice", 3), ("ice_horseshoe", 2), ("royal_seal", 1), ("pedestal_cloth", 1)), 4, 28, [],
     "近衛兵の鎧から剥がれた青い氷を、霜銀の芯に巻いた指輪。握ると、盾を構えたときのように背筋がのびる。"),
    ("guard_ice_wisdom_ring", "近衛氷の智環", "ring", ["magic"], 9, "uncommon", False,
     ing(("guard_ice", 3), ("torn_page", 2), ("royal_seal", 1), ("pedestal_cloth", 1)), 4, 28, [],
     "近衛の氷の輪に、ほどけた頁の切れ端を細く巻いた指輪。頁の文字は読めないが、魔力を通すと青白く浮かぶ。"),
    ("portrait_necklace", "肖像の首飾り", "necklace", None, 9, "uncommon", True,
     ing(("lace_scrap", 3), ("castle_ice", 1), ("royal_seal", 1), ("pedestal_cloth", 1)), 4, 28, [],
     "侍女の前掛けのレースに、小さな楕円の額を下げた首飾り。額の中は白いまま。持ち主が誰かを思い浮かべると、うっすら色がつく。"),
    ("horseshoe_bracelet", "氷の蹄鉄の腕輪", "bracelet", None, 9, "uncommon", True,
     ing(("ice_horseshoe", 3), ("frost_silver", 2), ("pedestal_cloth", 1)), 4, 28, [],
     "氷の騎兵の馬が落とした蹄鉄を、霜銀の輪に打ち直した腕輪。歩くと、遠い石畳の蹄の音がする気がする。"),
    ("name_charm", "名札のお守り", "charm", None, 9, "uncommon", True,
     ing(("guard_ice", 2), ("hound_collar", 1), ("echo_fragment", 1), ("pedestal_cloth", 1)), 4, 28, [],
     "王城の名札をまねた、小さな銀の札のお守り。自分の名前を刻んでおけば、忘れても札が覚えていてくれる。"),
    ("bearer_tassel", "旗手の房飾り", "charm", None, 9, "rare", False, None, 0, 0,
     [{"type": "move_speed", "percent": 6}],
     "霜蹄の旗手の、凍った青い旗の房飾り。持つと、号令を受けたように足が前へ出る。9階の名のある魔物が落とす。"),
    ("binder_bookmark", "綴じ糸の栞", "charm", None, 9, "rare", False, None, 0, 0,
     [{"type": "lost_resist", "percent": 25}],
     "綴じ手が頁を綴じていた、銀の糸の栞。挟んだ頁は決して抜け落ちない――忘れたくない名前も。9階の名のある魔物が落とす。"),
]

# 1階の品（Phase 3 からある）：階の印と、効き目の説明だけ足す
FLOOR1 = ["moss_iron_battle_ring", "moss_iron_wisdom_ring", "grassland_necklace", "ishikure_bracelet", "waystone_charm",
          "grassland_horn"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def read_json(path):
    with open(path, encoding="utf-8") as fp:
        return json.load(fp)


def add_lang(entries):
    for name in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", name + ".json")
        data = read_json(path)
        for key, value in entries.items():
            data[key] = value
        write_json(path, data)


def main():
    lang = {}
    manifest_path = os.path.join(RES, "amemaru", "item_types.json")
    manifest = read_json(manifest_path)

    # 素材
    known = {m["id"] for m in manifest["materials"]}
    for mid, name, rarity, floor, desc in MATERIALS:
        if mid not in known:
            entry = {"id": mid}
            if rarity != "common":
                entry["rarity"] = rarity
            manifest["materials"].append(entry)
        lang["item.amemaru." + mid] = name
        lang["item.amemaru." + mid + ".desc"] = desc
        write_json(os.path.join(ASSETS, "models", "item", mid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + mid}})

    # アクセサリー
    known = {a["id"] for a in manifest["accessories"]}
    for (aid, name, slot, main, floor, rarity, body, recipe, mastery, exp, effects, desc) in ACCESSORIES:
        if aid not in known:
            entry = {"id": aid, "slot": slot}
            if body:
                entry["model"] = "geckolib"
            manifest["accessories"].append(entry)
        definition = {
            "_comment": "%s（%d階。11月24日「階層ごとにアクセサリーも追加しましょう」【補完】。tools/datagen/build_floor_accessories.py）"
                        % (name, floor),
            "slot": slot,
            "generation": (floor - 1) // 5 + 1,
            "floor": floor,
            "rarity": rarity,
        }
        if main:
            definition["main"] = main
        if effects:
            definition["effects"] = effects
        write_json(os.path.join(DATA, "amemaru", "accessories", aid + ".json"), definition)
        if recipe:
            write_json(os.path.join(DATA, "recipes", "workshop", aid + ".json"), {
                "type": "amemaru:workshop",
                "workshop": "accessory",
                "mastery": mastery,
                "mastery_exp": exp,
                "ingredients": recipe,
                "result": {"item": "amemaru:" + aid},
            })
        lang["item.amemaru." + aid] = name
        lang["item.amemaru." + aid + ".desc"] = desc + ("（%d階の品）" % floor)
        if effects:
            lang["item.amemaru." + aid + ".effect"] = "・".join(effect_text(e) for e in effects)
        write_json(os.path.join(ASSETS, "models", "item", aid + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "amemaru:item/" + aid}})

    # 1階の品：階の印
    for aid in FLOOR1:
        path = os.path.join(DATA, "amemaru", "accessories", aid + ".json")
        data = read_json(path)
        if data.get("floor") != 1:
            items = list(data.items())
            out = {}
            for key, value in items:
                out[key] = value
                if key == "generation":
                    out["floor"] = 1
            if "floor" not in out:
                out["floor"] = 1
            write_json(path, out)
        if "effects" in data:
            lang["item.amemaru." + aid + ".effect"] = "・".join(effect_text(e) for e in data["effects"])
    write_json(manifest_path, manifest)
    add_lang(lang)
    print("素材", len(MATERIALS), "アクセサリー", len(ACCESSORIES))


if __name__ == "__main__":
    main()
