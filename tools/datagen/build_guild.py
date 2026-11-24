"""Phase 6 その3（ギルド・クエスト・古代記録・称号）の定義を書き出し、Mod の読み込みと同じ決まりで確かめる。

使い方：python3 tools/datagen/build_guild.py          （書き出して確かめる。問題があれば一覧を出して 1 で終わる）
        python3 tools/datagen/build_guild.py --check  （書き出さずに、今のファイルを確かめるだけ）

書き出すもの（data/amemaru/amemaru/）：
  titles/<名前>.json    称号（Phase 0-A 22章の表。個別に63。ユニーク称号は管理者がその場で作るので、ここにはない）
  records/<名前>.json   古代記録（Phase 0-A 9章の各階の「古代記録」。2〜19階は Phase 8 で階を作ったときに置く）と、
                        記憶魚の記録（Phase 7 その2。15章。塔の外）
  quests/<名前>.json    クエスト（街めぐり・デイリー・ギルドの依頼・上位・昇格・NPCの個人的な依頼）

決まり（Java の TitleDef・RecordDef・QuestDef の parse と同じ）：称号の名前は1〜16文字、ユニーク称号の条件は admin だけ、
限定称号の条件は first だけ、数の条件は GuildCounters にある名前だけ。記録の1ページは200文字まで。クエストの目標は1〜12個。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data/amemaru/amemaru"
ASSETS = ROOT / "src/main/resources/assets/amemaru"
JAVA = ROOT / "src/main/java/jp/amemaru/lostconnection"

# ================================================================ 称号（Phase 0-A 22章）
# (id, 名前, 分野, 種類, 系統, 段階, 段階の数, 条件, 説明, ヒント, 効果)


def counter(name, n):
    return {"type": "counter", "counter": name, "at_least": n}


def event(name):
    return {"type": "event", "event": name}


def first(name):
    return {"type": "first", "event": name}


TITLES = [
    # ---- 戦闘
    ("hunter_1", "見習い討伐者", "combat", "normal", "hunter", 1, 3, counter("kills", 500), "敵を500体倒した", "", None),
    ("hunter_2", "熟練討伐者", "combat", "normal", "hunter", 2, 3, counter("kills", 3000), "敵を3,000体倒した", "", None),
    ("hunter_3", "達人討伐者", "combat", "normal", "hunter", 3, 3, counter("kills", 10000), "敵を10,000体倒した", "", None),
    ("link_1", "連携の糸", "combat", "normal", "link", 1, 3, counter("link_combos", 100), "連携技を100回成功させた", "", None),
    ("link_2", "連携の網", "combat", "normal", "link", 2, 3, counter("link_combos", 500), "連携技を500回成功させた", "", None),
    ("link_3", "千の結び", "combat", "normal", "link", 3, 3, counter("link_combos", 2000), "連携技を2,000回成功させた", "", None),
    ("ancient_breaker", "古代を越えし者", "combat", "normal", "", 1, 1, {"type": "boss", "boss": "amemaru:ancient_guardian"},
     "5階の古代守護者を倒した", "", None),
    ("spring_caller", "春を呼ぶ者", "combat", "normal", "", 1, 1, {"type": "boss", "boss": "amemaru:queen_of_oblivion"},
     "10階の忘却の女王を倒した", "", None),
    ("severance_breaker", "断絶を断つ者", "combat", "normal", "", 1, 1, {"type": "boss", "boss": "amemaru:severance_executor"},
     "15階の断絶の執行者を倒した", "", None),
    ("unfallen", "倒れぬ心", "combat", "rare", "", 1, 1, {"type": "boss_no_down", "chapter_boss": True},
     "一度もダウンせずに、章ボスを倒した", "", None),
    ("last_bastion", "最後の砦", "combat", "rare", "", 1, 1, {"type": "boss_guard", "chapter_boss": True},
     "章ボス戦で、一番多く攻撃を受け止めて勝った", "", None),
    # ---- 生産
    ("crafter_1", "見習い職人", "craft", "normal", "crafter", 1, 3, counter("crafted", 50), "装備を50個作った", "", None),
    ("crafter_2", "熟練職人", "craft", "normal", "crafter", 2, 3, counter("crafted", 300), "装備を300個作った", "", None),
    ("crafter_3", "名工", "craft", "normal", "crafter", 3, 3, counter("crafted", 1000), "装備を1,000個作った", "", None),
    ("gem_1", "宝石研ぎ", "craft", "normal", "gem", 1, 3, counter("rerolls", 50), "アクセサリーの厳選を50回した", "", None),
    ("gem_2", "結晶細工師", "craft", "normal", "gem", 2, 3, counter("rerolls", 300), "アクセサリーの厳選を300回した", "", None),
    ("gem_3", "輝きの探求者", "craft", "normal", "gem", 3, 3, counter("rerolls", 1000), "アクセサリーの厳選を1,000回した", "", None),
    ("hammer_for_others", "誰かのための槌", "craft", "normal", "", 1, 1, counter("helped", 30),
     "ほかの人のための制作・修理・厳選を、合わせて30回した", "", None),
    ("perfect_crystal", "完璧の結晶", "craft", "rare", "", 1, 1, event("perfect_accessory"), "完璧なアクセサリーを手に入れた", "", None),
    # ---- 商売
    ("trader_1", "露店デビュー", "trade", "normal", "trader", 1, 3, counter("trades", 10), "フリーマーケットで10回取引した", "", None),
    ("trader_2", "市場の顔", "trade", "normal", "trader", 2, 3, counter("trades", 100), "フリーマーケットで100回取引した", "", None),
    ("trader_3", "コネリアの大商人", "trade", "normal", "trader", 3, 3, counter("trades", 500), "フリーマーケットで500回取引した", "",
     {"type": "listing_bonus", "amount": 5}),
    ("famous_stall", "名物露店", "trade", "normal", "", 1, 1, counter("stall_visitors", 200), "自分の露店に、のべ200人のお客が来た", "", None),
    # ---- 交流
    ("helper_1", "手を差し伸べる者", "social", "normal", "helper", 1, 3, counter("revives", 10), "仲間を10回起こした", "", None),
    ("helper_2", "救いの手", "social", "normal", "helper", 2, 3, counter("revives", 50), "仲間を50回起こした", "", None),
    ("helper_3", "不屈の結び手", "social", "normal", "helper", 3, 3, counter("revives", 200), "仲間を200回起こした", "", None),
    ("hello_1", "はじめまして", "social", "normal", "hello", 1, 3, counter("partners", 5), "5人とパーティを組んだ（25人のとき）", "", None),
    ("hello_2", "顔の広い冒険者", "social", "normal", "hello", 2, 3, counter("partners", 12), "12人とパーティを組んだ（25人のとき）", "",
     None),
    ("hello_3", "コネリアの縁結び", "social", "normal", "hello", 3, 3, counter("partners", 20), "20人とパーティを組んだ（25人のとき）", "",
     None),
    ("thanks_50", "ありがとうの数だけ", "social", "normal", "", 1, 1, counter("thanks_received", 50), "結び札を50枚受け取った", "", None),
    ("leader", "先導者", "social", "normal", "", 1, 1, counter("leads", 10), "途中から来た人・後輩とのパーティを10回組んだ", "", None),
    ("town_guard", "街の守り手", "social", "normal", "", 1, 1, event("raid_defense_top"), "街の襲撃で、役割の貢献が上位だった", "", None),
    # ---- 娯楽
    ("fisher_1", "釣り見習い", "leisure", "normal", "fisher", 1, 3, counter("fish_percent", 25), "魚図鑑を25%うめた", "", None),
    ("fisher_2", "釣り名人", "leisure", "normal", "fisher", 2, 3, counter("fish_percent", 60), "魚図鑑を60%うめた", "", None),
    ("fisher_3", "湖を知る者", "leisure", "normal", "fisher", 3, 3, counter("fish_percent", 100), "魚図鑑をすべてうめた", "",
     {"type": "fishing_gauge", "amount": 20}),
    ("memory_angler", "記憶を釣り上げた者", "leisure", "normal", "", 1, 1, event("memory_fish_complete"), "記憶魚を全種類釣った", "", None),
    ("jackpot", "大当たり！", "leisure", "normal", "", 1, 1, event("roulette_straight_win"), "ルーレットの一点賭けを当てた", "", None),
    ("laughed_at", "運命の女神に笑われた", "leisure", "normal", "", 1, 1, event("roulette_lose_streak_10"), "ルーレットで10回続けて外した", "",
     None),
    # ---- 物語
    ("records_1", "記録の拾い手", "story", "normal", "records", 1, 3, counter("records_percent", 25), "古代記録を25%集めた", "", None),
    ("records_2", "記録の読み手", "story", "normal", "records", 2, 3, counter("records_percent", 60), "古代記録を60%集めた", "", None),
    ("records_3", "歴史の語り部", "story", "normal", "records", 3, 3, counter("records_percent", 100), "古代記録をすべて集めた", "",
     {"type": "record_hints", "amount": 1}),
    ("friend_lilia", "リリアの友", "story", "normal", "", 1, 1, {"type": "affinity", "npc": "amemaru:lilia", "stage": 5},
     "リリアと「かけがえのない人」になった", "", None),
    ("trust_gard", "ガルドの信頼", "story", "normal", "", 1, 1, {"type": "affinity", "npc": "amemaru:gard", "stage": 5},
     "ガルドと「かけがえのない人」になった", "", None),
    ("partner_doran", "ドランの相棒", "story", "normal", "", 1, 1, {"type": "affinity", "npc": "amemaru:doran", "stage": 5},
     "ドランと「かけがえのない人」になった", "", None),
    ("light_serena", "セレナの灯", "story", "normal", "", 1, 1, {"type": "affinity", "npc": "amemaru:serena", "stage": 5},
     "セレナと「かけがえのない人」になった", "", None),
    ("assistant_ernesto", "エルネストの助手", "story", "normal", "", 1, 1, {"type": "affinity", "npc": "amemaru:ernesto", "stage": 5},
     "エルネストと「かけがえのない人」になった", "", None),
    ("conelia_adventurer", "コネリアの冒険者", "story", "normal", "", 1, 1, event("promotion_ceremony"),
     "5日目の昇格式に立ち会った（あとから来た人は、ガルドと話す）", "", None),
    ("knot_bearer", "結び手", "story", "normal", "", 1, 1, event("truth_witnessed"), "15日目の真相を見届けた（見逃した人は、記憶の再生で）",
     "", None),
    ("core_reacher", "中枢に至りし者", "story", "normal", "", 1, 1, event("final_battle_joined"), "最終決戦に加わった", "", None),
    # ---- 隠し（手に入れるまで「？？？」。ヒントは古代記録とNPCの台詞）
    ("said_im_home", "ただいまを言った人", "story", "hidden", "", 1, 1, event("said_tadaima"), "記憶を失ったリリアに「ただいま」と言った",
     "リリアの口ぐせを、思い出して", None),
    ("lost_guide", "迷子の案内人", "social", "hidden", "", 1, 1, event("guided_lost_party"),
     "2階で道に迷っているほかのパーティを、出口まで連れていった", "森で迷った者を見かけたら、出口まで", None),
    ("nameless_hero", "名もなき英雄", "combat", "hidden", "", 1, 1, event("nameless_hero"), "称号を表示しないまま、章ボスを3体倒した",
     "名を名乗らずに戦った者たちの昔話", None),
    ("untouched_white", "白い花を摘まなかった人", "story", "hidden", "", 1, 1, event("white_flowers_untouched"),
     "17階を、白い花に一度も触れずに抜けた", "咲いている途中の記憶には、触れないで", None),
    ("zero_step", "ゼロからの一歩", "trade", "hidden", "", 1, 1, {"type": "zero_earn", "amount": 10000},
     "手持ちが0ノットになってから、1万ノットを稼いだ", "すっからかんから立ち直った人の噂話", None),
    ("one_who_remembers", "覚えている人", "story", "hidden", "", 1, 1, {"type": "affinity", "npc": "amemaru:noa", "stage": 5},
     "ノアと「かけがえのない人」になった", "……覚えてる？", None),
    ("answered", "答えた人", "story", "hidden", "", 1, 1, event("final_question_answered"), "最終決戦の問いに、自分の言葉で答えた",
     "問いかけの像は、答えを待っている", None),
    # ---- 限定（サーバーで最初の1人か1パーティだけ）
    ("first_step", "最初の一歩", "combat", "limited", "", 1, 1, first("first_boss_floor1"), "1階のボスを、サーバーで最初に倒した", "",
     None),
    ("ancient_gate", "古代の門を開きし者", "combat", "limited", "", 1, 1, first("first_boss_floor5"),
     "古代守護者を、サーバーで最初に倒した", "", None),
    ("frost_breaker", "凍てを解きし者", "combat", "limited", "", 1, 1, first("first_boss_floor10"),
     "忘却の女王を、サーバーで最初に倒した", "", None),
    ("bond_keeper", "絆を守りし者", "combat", "limited", "", 1, 1, first("first_boss_floor15"),
     "断絶の執行者を、サーバーで最初に倒した", "", None),
    ("first_perfect", "最初の完璧", "craft", "limited", "", 1, 1, first("first_perfect_accessory"),
     "完璧なアクセサリーを、サーバーで最初に手に入れた", "", None),
    ("market_pioneer", "市場の開拓者", "trade", "limited", "", 1, 1, first("first_market_trade"),
     "フリーマーケットの最初の取引を成立させた（売り手と買い手）", "", None),
    ("first_big_catch", "最初の大物", "leisure", "limited", "", 1, 1, first("first_lake_lord"),
     "湖の主「記憶の主」を、サーバーで最初に釣った", "", None),
]

# ================================================================ 古代記録（Phase 0-A 9章。本文とエルネストのひと言は【提案】）
# (id, 階, 名前, 分類, 伏線, ページ, エルネストのひと言, 見つけていないときのヒント, 隠し称号のヒント)

RECORDS = [
    ("f01_waystone", 1, "道標の石板・一", "link", None,
     ["ひとりで来たのなら、引き返せ。この先は、手をつなぐ者のための道"],
     "道標というより、注意書きじゃな。この塔は、ひとりで登る者を最初から断っておる。実に、実に興味深い！",
     "1階：草原の主の広間の奥、半分埋もれた石板", None),
    ("f02_forester", 2, "森番の日誌", "link", None,
     ["森番の日誌より。木々の根は、地の下で細い糸を運んでいる。わたしたちが「リンク」と呼ぶ、あの糸だ。",
      "最近、獣たちが帰り道を忘れる。巣の前で立ち止まり、首をかしげて、また森の奥へ戻っていく。迷った者を見かけたら、出口まで連れていってやってほしい。"],
     "森そのものが、糸の通り道じゃったとはのう。帰り道を忘れる獣……気になる書き方じゃ。",
     "2階：霧の森の、森番の小屋", "lost_guide"),
    ("f03_waterworks", 3, "水路管理局の掲示", "link", None,
     ["水路管理局より告知。この水路は、魔力の循環路を兼ねる。流れが止まれば、街は乾く。",
      "水門を開け閉めするときは、必ず二か所を同時に。片方だけでは、水はどこへも行かない。"],
     "水と魔力を同じ道に通すとは、古代の者たちは無駄が嫌いじゃったようじゃ。読めぬものは、これからもわしが読もう。",
     "3階：古代の水路の、管理局の掲示板", None),
    ("f04_temple", 4, "神殿の碑文", "history", None,
     ["われらは神を祀らない。祀るのは、人と人の間にあるものである。",
      "（壁画）真ん中に高い塔。その根元に、輪のような装置が描かれている。装置の名を記した所だけが、削れて読めない。"],
     "神ではなく「間」を祀る……古代の信仰は、思っておったよりずっと身近じゃ。削れた名前が惜しいのう。",
     "4階：崩れた神殿の、奥の壁", None),
    ("f05_guardian_core", 5, "守護者の記録核", "history", None,
     ["記録核、再生。全域同期実験、失敗。各地の回路が焼け落ち、文明は分かたれた。のちの者は、これを「大断絶」と呼ぶ。",
      "中枢装置リンク・コア、稼働を継続。守護者は、資格なき者を通さぬこと。"],
     "リンク・コア！ 名前が、ついに読めたぞ！ 実に、実に興味深い！",
     "5階：祭壇の守護者が、身の内に抱えていた記録", None),
    ("f06_child_diary", 6, "村の子どもの日記", "people", None,
     ["きょう、おかあさんが、わたしの名前を呼ばなくなった。",
      "村の大人たちも、少しずつ、だれかの名前を忘れていく。雪がふるたびに。"],
     "……子どもの字じゃ。この村では、忘れることが、病のように広がっておったらしい。",
     "6階：凍った村の、小さな家の中", None),
    ("f07_watch_post", 7, "森林監視所の記録", "lost", None,
     ["森林監視所より通達。侵食域では、隣にいる者が誰か分からなくなる。合言葉を決めよ。",
      "合言葉は、互いにしか分からない言葉にすること。名前は、侵食に最初に奪われる。"],
     "名前が最初に奪われる、か。……セレナ殿に見せねばならんのう。",
     "7階：氷の森の、見張り小屋", None),
    ("f08_lake_shrine", 8, "湖底の祠の石板", "unknown", None,
     ["沈黙は、争いを止めた。だが、歌も止めた。",
      "祠の守り手より。湖に音が戻る日まで、この石板を沈めておく。"],
     "静けさを、良いものとも悪いものとも書いておらん。書いた者も、迷っておったのじゃろう。",
     "8階：沈黙の湖の底の、小さな祠", None),
    ("f09_queen_letters", 9, "女王の書簡（三通）", "lost", None,
     ["一通目。わたしは、あなたたちを忘れたくない。だから毎晩、名前を書き留めています。",
      "二通目。書き留めた名前が、朝には知らない文字に見えるのです。……あなたは、誰だったかしら。",
      "三通目。白き声が言った。忘れれば、もう誰も泣かない、と。わたしは、それでいいと思ってしまった。"],
     "女王もまた、忘れたくなかったのじゃな。「白き声」とは、何者じゃ……。",
     "9階：氷結した王城の、女王の書斎", None),
    ("f10_null_signal", 10, "NULLの通信記録", "null", None,
     ["管理者NULL：対象地域の記憶を回収。苦痛指数、ゼロ。",
      "管理者NULL：回収を継続。対象地域の歌声、検出されず。"],
     "NULL……これが白き声の名か。苦しみをゼロにするために、記憶ごと奪う。理屈が通っておるのが、恐ろしいのう。",
     "10階：忘却の玉座の奥の、通信の装置", None),
    ("f11_mining", 11, "採掘記録", "history", None,
     ["採掘局の年次報告。人々のつながりを結晶に蓄えれば、もう誰かを頼る必要はない。",
      "結晶の取引量は、前の年の三倍。「つながり」は、いまや最も高く売れる品である。"],
     "つながりを掘って、売る……。郷里の学者どもが聞いたら、喜んで真似をしそうじゃ。",
     "11階：灼熱の坑道の、採掘局の詰め所", None),
    ("f12_forgemaster", 12, "工房長の手記", "people", None,
     ["二人の職人の糸を響き合わせて打つ。これを「結びの鍛造」と名づける。一人では、決して届かない硬さに届く。",
      "工房は、剣を打つ所になった。結びの鍛造は、もう二人の名を刻まない。"],
     "結びの鍛造……ドランの小僧に見せてやらねばのう。あやつの古い金槌に刻まれた、二つの名前を思い出したわい。",
     "12階：溶岩の工房の、工房長の机", None),
    ("f13_sync_plan", 13, "全域同期計画・概要書", "history", None,
     ["全域同期計画の概要。すべての人の心を、ひとつの回路で完全につなぐ。誤解も、孤独も、なくなる。",
      "反対意見も記録に残す。「完全につながった心は、まだ心と呼べるのか」"],
     "善意の計画ほど、止めにくいものはない。……わしは、少し怖くなってきたぞ。",
     "13階：崩壊した研究都市の、計画局の書庫", None),
    ("f13_admin_log", 13, "管理者開発記録", "null", None,
     ["管理者開発記録。回路の調停役として、管理者を作る。名は、NULL。痛みを数え、ゼロに近づける役目。",
      "起動試験、良好。NULLは、試験に立ち会った者すべての名を覚えた。"],
     "NULLは、はじめは人を守るために作られたのか……。",
     "13階：崩壊した研究都市の、管理者開発室", None),
    ("f13_ring_memo", 13, "接続研究院の備忘録", "unknown", 5,
     ["観測環の調整。本日も応答なし。予算の会議では、また打ち切りを言われるだろう。"],
     "観測環……何を観測しておったのか、どこにも書いておらん。あくまで、わしの仮説じゃがのう。",
     "13階：崩壊した研究都市の、研究院の机", None),
    ("f14_last_day", 14, "同期実験・最終日の記録", "null", None,
     ["同期実験、最終日。回路が、悲鳴で満ちた。",
      "NULLの記録：つながりは、痛みを増幅する。よって、つながりを断つ。"],
     "これが、大断絶の夜か。……つながったから、皆の痛みが一度に流れ込んだのじゃな。",
     "14階：封印された実験区画の、最後の部屋", None),
    ("f15_summoning", 15, "召喚記録", "link", None,
     ["召喚記録。中枢装置は、断たれた糸の端を、世界の外へ投げた。",
      "呼びかけに応えた者たちの共通点。特別な力、なし。誰かとつながり続けてきたこと、あり。"],
     "わしの四十年は、この一頁のためにあった。",
     "15階：召喚装置の中枢の、台座", None),
] + [
    ("f15_observation_%d" % n, 15, "観測記録・" + kanji, "unknown", 5, [text], comment, "15階：観測所のどこかに散った一枚", None)
    for n, kanji, text, comment in (
        (1, "一", "空の外に、灯をひとつ観測。呼びかけを送る。応答なし。", "灯、じゃと……？"),
        (2, "二", "二つ目の灯。ひとつ目とは、揺れ方が違う。応答なし。", "わからん。わからんが、捨てられん記録じゃ。"),
        (3, "三", "三つ目の灯。とても小さく、すぐに見えなくなる。応答なし。", "消えた灯は、どこへ行ったのかのう。"),
        (4, "四", "四つ目の灯。こちらの呼びかけを、真似するように瞬く。応答とは見なさない。", "真似をする灯……。あくまで、わしの仮説じゃがのう。"),
        (5, "五", "五つ目の灯。観測環の針が、一度だけ大きく振れた。記録の誤りとする。", "誤りとして片づけるには、惜しい記録じゃ。"),
        (6, "六", "六つ目の灯。灯どうしが、互いに呼び合っているように見える。", "呼び合う灯、か。"),
        (7, "七", "七つ目の灯。……ひとつだけ、応えた。", "七つの灯……街の子どもの数え歌と、同じ数じゃ。偶然かのう。"),
    )
] + [
    ("f16_null_boot", 16, "NULLの起動記録", "null", None,
     ["管理者NULL、起動。本日の祭り、参加者四百十二名。全員の名を記録した。",
      "管理者NULL：子どもが転んだ。痛みの値、小。すぐに笑った。……よかった。"],
     "優しかったのじゃな、最初は。……わしは、こういう記録がいちばん堪える。",
     "16階：天空の回廊の、古い管理室", None),
    ("f17_null_watch", 17, "NULLの観察記録", "null", None,
     ["管理者NULL：人は、別れのたびに痛む。痛みを減らす方法を学習中。",
      "管理者NULL：全域同期計画に反対。完全な接続は、痛みを分けるのではなく、増やす。"],
     "NULLは計画に反対しておった……。止められなかったのは、人の方じゃったか。",
     "17階：記憶の庭園の、管理者の東屋", None),
    ("f17_gardener", 17, "庭師のメモ", "people", None,
     ["庭師のメモ。この庭の花は、触れると誰かの記憶を見せる。見たくない記憶もある。",
      "白い花には触れないでおくれ。あれは、まだ咲いている途中の記憶だから。最後まで触れずに通り抜けた人には、庭がそっと礼を言うだろう。"],
     "庭師の気持ちも、わからんでもない。咲き終わるまで、待ってやりたい記憶もあるのじゃろう。",
     "17階：記憶の庭園の、庭師の道具小屋", "untouched_white"),
    ("f18_founders_oath", 18, "創設者たちの誓い", "history", None,
     ["われら創設者は誓う。この塔を、つながる者のために建てる。ひとりでは登れぬように。",
      "誰かを置いていかないこと。置いていかれた者を、迎えに行くこと。"],
     "一階の石柱の一行は、ここから来ておったのか！ 塔そのものが、誓いの形じゃったわけじゃ。",
     "18階：神域の神殿の、誓いの間", None),
    ("f18_final_severance", 18, "最終断絶の計画書", "unknown", 2,
     ["最終断絶の計画。管理者NULLに、全回路の遮断を委ねる。実行の後、権限は創設者会議へ返すこと。",
      "（末尾に、あとから書き足された一行）管理者権限：未返還。"],
     "未返還……。返されぬまま、何百年も。",
     "18階：神域の神殿の、封じられた書庫", None),
    ("f19_core_voice", 19, "リンク・コアの声（断片）", "link", None,
     ["……呼んだのは、わたし……"],
     "声じゃ。記録というより、声そのものが石に残っておる。",
     "19階：リンク・コアの守護領域の、コアへ続く道", None),
] + [
    # Phase 7 その2：記憶魚の記録（Phase 0-A 15章「まれに記憶を宿した魚が釣れ、古代の人々の短い記憶が記録帳に入る」）。
    # 塔の外（floor 0）・人々。章ごとに1種。本文とエルネストのひと言は【補完】
    ("fish_memory_dawn", 0, "記憶魚・朝凪の記憶", "people", None,
     ["（水面に映る、小さな手と大きな手）「竿は、こう。力を抜いて、待つんだ」",
      "「釣れないよ」「釣れなくてもいいさ。待っているあいだに、空がきれいになる」……朝の貯水池は、鏡のように静かだった。"],
     "魚が覚えておった、朝の釣りの記憶じゃな。待つことを教える者は、いつの時代にもおったのじゃ。ポッカに読ませてやりたいのう。",
     "釣り堀・湖：まれに釣れる「朝凪の記憶魚」（第1章から）", None),
    ("fish_memory_snow", 0, "記憶魚・雪明かりの記憶", "people", None,
     ["（凍った貯水池。氷の上を、ふたりの影がすべっていく）「転んだら、わたしの名前を呼んで」",
      "「呼んだら、どうするの」「すぐに手を伸ばす。……ほら、もう呼んだ」笑い声が、氷の下まで届いていた。"],
     "氷の下の魚が、氷の上の声を聞いておった。名前を呼べば手が伸びる……この街の古い習いかもしれんのう。",
     "釣り堀・湖：まれに釣れる「雪明かりの記憶魚」（第2章から）", None),
    ("fish_memory_steam", 0, "記憶魚・湯けむりの記憶", "people", None,
     ["（湧き湯の縁。湯気の向こうで、仕事帰りの者たちが肩を並べている）「今日も、塔から帰ったな」",
      "「ああ、帰った。帰る場所があるから、登れる」……湯の音にまぎれて、だれかが小さく歌っていた。"],
     "湯につかりながら、帰ってきたことを確かめ合う……古代の者も、わしらと同じじゃ。",
     "釣り堀・湖：まれに釣れる「湯けむりの記憶魚」（第3章から）", None),
    ("fish_memory_star", 0, "記憶魚・星映しの記憶", "people", None,
     ["（夜の水面いっぱいの星。岸に並んだ灯りが、一つずつ点っていく）「ひとつ点けたら、となりの人に火を渡して」",
      "「最後の一つは？」「最初の人に、返すのさ。そうすれば、輪になる」……水に映った灯りの輪は、空の星とつながって見えた。"],
     "灯りを渡して輪にする祭りじゃな。……輪、か。この街は、どこを掘っても「つながり」が出てくるのう。",
     "釣り堀・湖：まれに釣れる「星映しの記憶魚」（第4章から）", None),
]

# ================================================================ クエスト（Phase 0-A 7章・11章・13章・14章・19章）
# 街めぐり（1日目）の行き先：施設の前の通り（CityCommands の見どころと、構造物の入口から）【補完】


def visit(label, pos, radius=10):
    return {"type": "visit", "place": "conelia", "pos": pos, "radius": radius, "label": label}


def talk(npc):
    return {"type": "talk", "npc": "amemaru:" + npc}


def kill(enemy, n, floor=1):
    return {"type": "kill", "enemy": "amemaru:" + enemy, "count": n, "floor": floor}


def collect(item, n, consume=True):
    return {"type": "collect", "item": item, "count": n, "consume": consume}


QUESTS = {
    # ---- 街めぐり（1日目。職業を登録したら自動で受ける。Phase 0-A 11章「全施設を回る」）
    "town_tour": {
        "name": "街めぐり", "kind": "town_tour", "giver": "amemaru:lilia", "report": "amemaru:lilia", "board": True,
        "description": "{player}さん、まずはコネリアの施設を一通り回ってみてください。工房・商店街・教会・倉庫・釣り堀。"
                       "どこも、これからお世話になる所ですよ！",
        "requires": {"has_job": True},
        "objectives": [talk("doran") | {"label": "武器工房"}, visit("防具工房", [71.5, 1, -3.5]), visit("装飾工房", [31.5, 1, 0.5]),
                       visit("商店街", [0.5, 1, 34.5]), talk("serena") | {"label": "結びの教会"},
                       visit("個人倉庫", [30.5, 1, -62.5], 8), visit("釣り堀", [-62.5, 1, 56.5])],
        "rewards": {"knots": 300, "exp": 150, "items": [{"item": "amemaru:healing_potion_small", "count": 3}],
                    "affinity": {"amemaru:lilia": 10}},
        "order": 0,
    },
    # ---- デイリー（1日3回まで。毎日、この中から3つが掲示板に出る。報告は掲示板）
    "daily_tsunomofu": {"name": "ツノモフの間引き", "kind": "daily", "pool": "floor1",
                        "description": "草原のツノモフが増えすぎています。少し数を減らしてください。",
                        "objectives": [kill("tsunomofu", 12)], "rewards": {"knots": 150, "exp": 120}, "order": 10},
    "daily_ishikure": {"name": "見張りの石くれ兵", "kind": "daily", "pool": "floor1",
                       "description": "決まった道を巡回する古代の見張りが、新人の行く手をふさいでいます。",
                       "objectives": [kill("ishikure_soldier", 8)], "rewards": {"knots": 180, "exp": 150}, "order": 20},
    "daily_wolf": {"name": "欠け牙の狼", "kind": "daily", "pool": "floor1",
                   "description": "体の一部が白く欠けた狼が出ています。突進に気をつけて。",
                   "objectives": [kill("kakekiba_wolf", 5)], "rewards": {"knots": 200, "exp": 160}, "order": 30},
    "daily_sweep": {"name": "草原の見回り", "kind": "daily", "pool": "floor1",
                    "description": "1階の敵を、種類を問わず倒してください。仲間と組めば、みんなの数になります。",
                    "objectives": [{"type": "kill_any", "count": 20, "floor": 1}], "rewards": {"knots": 180, "exp": 160}, "order": 40},
    "daily_stinger": {"name": "結晶蜂の針", "kind": "daily", "pool": "floor1",
                      "description": "工房から、結晶蜂の針の注文です。",
                      "objectives": [collect("amemaru:bee_stinger", 4)], "rewards": {"knots": 160, "exp": 120}, "order": 50},
    "daily_fluff": {"name": "ツノモフの毛", "kind": "daily", "pool": "floor1",
                    "description": "布と仕立ての店から、ツノモフの毛の注文です。",
                    "objectives": [collect("amemaru:tsunomofu_fluff", 6)], "rewards": {"knots": 140, "exp": 100}, "order": 60},
    "daily_moss_iron": {"name": "苔鉄の納品", "kind": "daily", "pool": "floor1",
                        "description": "金物屋から、苔鉄の注文です。",
                        "objectives": [collect("amemaru:moss_iron", 5)], "rewards": {"knots": 160, "exp": 110}, "order": 70},
    # ---- ギルドの依頼（いつでも。一度きり）
    "guild_first_floor": {"name": "はじめての草原", "kind": "guild",
                          "description": "1階「始まりの草原」の奥にいる草原の主を倒してください。仲間と一緒に、が条件です。",
                          "requires": {"has_job": True},
                          "objectives": [{"type": "boss", "count": 1, "floor": 1}], "rewards": {"knots": 500, "exp": 300}, "order": 100},
    "guild_pillars": {"name": "三本の石柱", "kind": "guild",
                      "description": "草原に散らばる三本の石柱を起動してください。触れるたびに、古代の文字が一行ずつ読めるそうです。",
                      "requires": {"has_job": True},
                      "objectives": [{"type": "pillar", "count": 3, "floor": 1}], "rewards": {"knots": 200, "exp": 150}, "order": 110},
    "guild_chests": {"name": "古代の宝箱", "kind": "guild",
                     "description": "1階のどこかに、古代の宝箱が眠っています。3つ見つけて開けてください。",
                     "requires": {"has_job": True},
                     "objectives": [{"type": "chest", "count": 3, "floor": 1}], "rewards": {"knots": 250, "exp": 150}, "order": 120},
    "guild_tablet": {"name": "道標の石板", "kind": "guild",
                     "description": "草原の主の広間の奥に、古代の石板があるそうです。読んで、記録に残してきてください。",
                     "requires": {"has_job": True},
                     "objectives": [{"type": "record", "count": 1, "floor": 1}], "rewards": {"knots": 150, "exp": 100}, "order": 130},
    # ---- 上位クエスト（5日目から。Phase 0-A 7章）
    "upper_alpha": {"name": "群れの長", "kind": "upper",
                    "description": "ツノモフの群れを率いる長が見つかりました。群れごと相手にすることになります。",
                    "requires": {"day_min": 5, "has_job": True},
                    "objectives": [kill("tsunomofu_alpha", 1)], "rewards": {"knots": 800, "exp": 500}, "order": 200},
    "upper_veldhorn": {"name": "草原の主、ふたたび", "kind": "upper",
                       "description": "草原の主ヴェルドホーンが、また広間に戻っています。三度倒して、新人の道を開けてください。",
                       "requires": {"day_min": 5, "has_job": True},
                       "objectives": [{"type": "boss", "count": 3, "floor": 1}], "rewards": {"knots": 1200, "exp": 800}, "order": 210},
    "upper_sweep": {"name": "草原の大掃除", "kind": "upper",
                    "description": "新人が増える前に、1階の敵をまとめて減らしておきたいのです。",
                    "requires": {"day_min": 5, "has_job": True},
                    "objectives": [{"type": "kill_any", "count": 80, "floor": 1}], "rewards": {"knots": 1000, "exp": 700}, "order": 220},
    # ---- 昇格クエスト（11日目・Lv60から（11月24日の2回目に30から変えた。職業の upper.min_level）。終えると上位職になる。Phase 0-A 19章）
    "promotion": {"name": "昇格試験", "kind": "promotion", "giver": "amemaru:gard", "report": "amemaru:gard", "board": True,
                  "description": "上位職への昇格を望むなら、腕を見せろ。……生きて戻れ。それが命令だ。",
                  "requires": {"day_min": 11, "has_job": True},
                  "objectives": [{"type": "kill_any", "count": 100}, {"type": "boss", "count": 2}],
                  "rewards": {"knots": 2000, "exp": 1000, "promote": True, "affinity": {"amemaru:gard": 15}}, "order": 300},
}

# ---- NPCの個人的な依頼（親密度2で①、3で②。Phase 0-A 14章。中身は【提案】）
PERSONAL = [
    ("lilia_1", "lilia", 2, "名簿の届け物", "冒険者名簿の写しを、ガルドさんと門番のバルドさんに届けてもらえますか？ わたし、受付を離れられなくて。",
     [talk("gard"), talk("bard")], {"knots": 150, "exp": 80}),
    ("lilia_2", "lilia", 3, "受付の花", "受付に、青い花を飾りたいんです。花と種の店の矢車菊を、3本お願いできますか？",
     [collect("minecraft:cornflower", 3)], {"knots": 100, "items": [{"item": "minecraft:cookie", "count": 6}]}),
    ("gard_1", "gard", 2, "見回り", "草原の見回りだ。ツノモフを10、石くれ兵を5。……無理はするな。",
     [kill("tsunomofu", 10), kill("ishikure_soldier", 5)], {"knots": 250, "exp": 150}),
    ("gard_2", "gard", 3, "新人の備え", "新人に持たせる回復薬が足りん。3つ、用意してくれ。",
     [collect("amemaru:healing_potion_small", 3)], {"knots": 300}),
    ("doran_1", "doran", 2, "古い石を持ってこい", "塔の古い石を5つだ。叩けば、昔の職人の腕がわかる。",
     [collect("amemaru:ancient_stone", 5)], {"knots": 200, "exp": 80}),
    ("doran_2", "doran", 3, "結晶の目利き", "結晶の欠片を8つと、石くれの核を1つ。古代の武器の秘密に、もう少しで手が届きそうなんだ。",
     [collect("amemaru:crystal_shard", 8), collect("amemaru:ishikure_core", 1)], {"knots": 400, "exp": 120}),
    ("serena_1", "serena", 2, "灯りの見回り", "夕べの祈りの前に、街の灯りを見てきていただけますか。中央広場と、塔前広場と、釣り堀の灯りを。",
     [visit("中央広場", [0.5, 1, 12.5], 8), visit("塔前広場", [0.5, 3, -130.5]), visit("釣り堀", [-62.5, 1, 56.5])],
     {"knots": 150, "exp": 80}),
    ("serena_2", "serena", 3, "残響の欠片", "塔で拾える「残響の欠片」を、5つ調べさせてください。祈りの声に、よく似た響きがするのです。",
     [collect("amemaru:echo_fragment", 5)], {"knots": 250, "items": [{"item": "amemaru:mana_water_small", "count": 2}]}),
    ("ernesto_1", "ernesto", 2, "古い声の欠片", "残響の欠片を3つ、持ってきてくれんか。古代の音が、まだ中に残っておるかもしれん。",
     [collect("amemaru:echo_fragment", 3)], {"knots": 200, "exp": 100}),
    ("ernesto_2", "ernesto", 3, "石くれの核", "石くれの核を2つ。見張りが何を見張っておったのか、核に書いてあるはずじゃ。",
     [collect("amemaru:ishikure_core", 2)], {"knots": 300, "exp": 120}),
    ("noa_1", "noa", 2, "街の音", "街の音を、聞いてきて。広場の大結晶と、教会の鐘と、釣り堀の水。……どんな音だった？",
     [visit("中央広場", [0.5, 1, 12.5], 8), visit("結びの教会", [-65.5, 1, 0.5]), visit("釣り堀", [-62.5, 1, 56.5])], {"knots": 100, "exp": 80}),
    ("noa_2", "noa", 3, "甘いもの", "パン屋のクッキー、3つ。……甘いものは、覚えておきたいから。",
     [collect("minecraft:cookie", 3)], {"knots": 100}),
]

for qid, npc, stage, name, description, objectives, rewards in PERSONAL:
    r = dict(rewards)
    r["affinity"] = {"amemaru:" + npc: 15 if stage == 2 else 20}
    r["flags"] = ["amemaru:bond/%s_done" % qid]
    QUESTS[qid] = {"name": name, "kind": "personal", "giver": "amemaru:" + npc, "board": True, "description": description,
                   "requires": {"affinity_min": stage, "has_job": True}, "objectives": objectives, "rewards": r,
                   "order": 400 + len(QUESTS)}

# ---- 留め糸（厳選の固定に使う素材）は、NPCの依頼のお礼（Phase 0-A 21章「厳選素材」：釣り・カジノの景品・NPCの依頼）。
# 釣りとカジノは Phase 7 なので、それまでは依頼だけが入手先になる。デイリーは1本、個人的な依頼の②は2本【補完】（Phase 6 その4）
BINDING_THREAD = "amemaru:binding_thread"
for qid, q in QUESTS.items():
    count = 1 if q["kind"] == "daily" else 2 if q["kind"] == "personal" and q["requires"].get("affinity_min") == 3 else 0
    if count:
        q["rewards"] = dict(q["rewards"])
        q["rewards"]["items"] = list(q["rewards"].get("items", [])) + [{"item": BINDING_THREAD, "count": count}]


# ================================================================ 書き出し

def title_json(t):
    tid, name, field, kind, series, tier, tiers, condition, description, hint, effect = t
    out = {"name": name, "field": field, "kind": kind}
    if series:
        out["series"] = series
    if tiers > 1:
        out["tier"] = tier
        out["tiers"] = tiers
    out["condition"] = condition
    out["description"] = description
    if hint:
        out["hint"] = hint
    if effect:
        out["effect"] = effect
    return out


def record_json(r, order):
    rid, floor, title, category, foreshadow, pages, comment, hint, title_hint = r
    out = {"title": title, "floor": floor, "category": category, "pages": pages, "comment": comment, "hint": hint, "order": order}
    if foreshadow:
        out["foreshadow"] = foreshadow
    if title_hint:
        out["title_hint"] = "amemaru:" + title_hint
    return out


def write_all():
    for kind in ("titles", "records", "quests"):
        folder = DATA / kind
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.json"):
            old.unlink()
    for i, t in enumerate(TITLES):
        out = title_json(t)
        out["order"] = i * 10
        dump(DATA / "titles" / (t[0] + ".json"), out)
    for i, r in enumerate(RECORDS):
        dump(DATA / "records" / (r[0] + ".json"), record_json(r, i * 10))
    for qid, q in QUESTS.items():
        dump(DATA / "quests" / (qid + ".json"), q)


def dump(path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ================================================================ 確かめる

def counters():
    source = (JAVA / "guild/GuildCounters.java").read_text(encoding="utf-8")
    return set(re.findall(r'public static final String [A-Z_]+ = "([a-z_]+)"', source))


def item_exists(item):
    ns, path = item.split(":")
    if ns == "minecraft":
        return True
    return (ASSETS / "models/item" / (path + ".json")).exists()


def check():
    problems = []
    names = counters()
    titles = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (DATA / "titles").glob("*.json")}
    records = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (DATA / "records").glob("*.json")}
    quests = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (DATA / "quests").glob("*.json")}
    npcs = {p.stem for p in (DATA / "npcs").glob("*.json")}
    enemies = {p.stem for p in (DATA / "enemies").glob("*.json")}
    kinds = {"normal", "rare", "hidden", "limited", "unique"}
    fields = {"combat", "craft", "trade", "social", "leisure", "story"}
    for tid, t in titles.items():
        where = "titles/" + tid
        if not (1 <= len(t["name"]) <= 16):
            problems.append(where + ": 名前は1〜16文字")
        if t.get("kind", "normal") not in kinds:
            problems.append(where + ": kind")
        if t.get("field", "story") not in fields:
            problems.append(where + ": field")
        c = t["condition"]
        if c["type"] == "counter" and c["counter"] not in names:
            problems.append(where + ": 数の名前 " + c["counter"])
        if c["type"] == "affinity" and c["npc"].split(":")[1] not in npcs:
            problems.append(where + ": NPC " + c["npc"])
        if t.get("kind") == "limited" and c["type"] != "first":
            problems.append(where + ": 限定称号の条件は first")
        if t.get("kind") == "unique" and c["type"] != "admin":
            problems.append(where + ": ユニーク称号の条件は admin")
    if "perfect_crystal" not in titles:
        problems.append("titles: AccessoryService.PERFECT_TITLE（amemaru:perfect_crystal）がない")
    for rid, r in records.items():
        where = "records/" + rid
        for page in r["pages"]:
            if len(page) > 200:
                problems.append(where + ": 1ページは200文字まで（%d）" % len(page))
        if r["category"] not in {"history", "link", "lost", "null", "people", "unknown"}:
            problems.append(where + ": category")
        if r.get("title_hint") and r["title_hint"].split(":")[1] not in titles:
            problems.append(where + ": title_hint " + r["title_hint"])
    floor1 = json.loads((DATA / "floors/floor1.json").read_text(encoding="utf-8"))
    if not any(r["floor"] == 1 and r["title"] == floor1["tablet"]["title"] for r in records.values()):
        problems.append("records: 1階の石板（%s）と同じ名前の記録がない" % floor1["tablet"]["title"])
    for qid, q in quests.items():
        where = "quests/" + qid
        if not (1 <= len(q["objectives"]) <= 12):
            problems.append(where + ": 目標は1〜12個")
        for ref in [q.get("giver"), q.get("report")]:
            if ref and ref != "board" and ref.split(":")[1] not in npcs:
                problems.append(where + ": NPC " + ref)
        for o in q["objectives"]:
            if o["type"] == "kill" and o["enemy"].split(":")[1] not in enemies:
                problems.append(where + ": 敵 " + o["enemy"])
            if o["type"] == "talk" and o["npc"].split(":")[1] not in npcs:
                problems.append(where + ": NPC " + o["npc"])
            if o["type"] == "collect" and not item_exists(o["item"]):
                problems.append(where + ": 品 " + o["item"])
        for item in q.get("rewards", {}).get("items", []):
            if not item_exists(item["item"]):
                problems.append(where + ": 報酬の品 " + item["item"])
        for npc in q.get("rewards", {}).get("affinity", {}):
            if npc.split(":")[1] not in npcs:
                problems.append(where + ": 親密度の NPC " + npc)
        title = q.get("rewards", {}).get("title")
        if title and title.split(":")[1] not in titles:
            problems.append(where + ": 称号 " + title)
    print("称号 %d・古代記録 %d・クエスト %d" % (len(titles), len(records), len(quests)))
    for p in problems:
        print("  ✗", p)
    return 1 if problems else 0


if __name__ == "__main__":
    if "--check" not in sys.argv:
        write_all()
    sys.exit(check())
