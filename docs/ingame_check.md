# Minecraftで確かめる：確かめ用のワールドと、街をまとめて建てるコマンド（11月21日）

今までの全部（Phase 1〜3、装備のデザイン、街の11施設）を、ゲームの中で見て回るための道具です。

確かめ用のブランチ `check/ingame` は、まだマージしていない3つのプルリクエストを main に合わせたものです。
3つは衝突しないので、マージの順番は自由です。

- #7：装備のデザイン（`phase-3`）
- #8：武器の持ち方（`gear/weapon-fix`）
- #6：街の11施設（`map/phase1-guild`）

このブランチの自動ビルドの jar には、3つと下の道具がすべて入っています。

## 確かめ用のワールド（ワールドタイプ「コネリアの確かめ用（平地）」）

- ワールドを作る画面の「ワールド」タブで、「ワールドタイプ」のボタンを押して「コネリアの確かめ用（平地）」にします。
- 地面は y 64 の草です（その下は土3段と石）。街の道の高さと同じなので、街を建てると地面になじみます。
- 敵も動物も、自然には湧きません（バイオームは `the_void`）。雨も降らず、木や草もありません。敵はコマンドかスポーンエッグで出します。
- 必要なファイルは `data/amemaru/worldgen/world_preset/conelia_check.json` と、一覧に出すための `data/minecraft/tags/worldgen/world_preset/normal.json` です。
- このModを入れた人のワールドタイプの一覧に、だれでも出ます。本番の前に外すかどうかは、あとで決めます【提案】。

## 街を建てる・見て回る（`/amemaru admin city`。OP・権限レベル2以上）

| コマンド | 内容 |
| --- | --- |
| `/amemaru admin city build` | 11施設を地図の位置に建てます（はじめの形）。中央広場の真ん中（大結晶の場所）が (0, 64, 0) になります。数秒、ゲームが止まります |
| `/amemaru admin city build grown` | はじめから建て直し、育ったあとに重ねる形9つも置きます（料理屋・露店の8区画・記憶の相談所・湖畔の釣り場・第2章の氷・ハイ＆ロー・魚レース・記念碑・古代炉） |
| `/amemaru admin city build at <x y z> [grown]` | 真ん中の場所を決めて建てます（CIの写真では空に建てる） |
| `/amemaru admin city tp [場所] [人]` | 見どころへ移ります（場所を省くと一覧、人を省くと自分）。最後に建てた所が基準です |

- 置き場所と順番は、街全体の通しの確かめ（`tools/mapgen/check_city.py`）と同じです。
- 建てる範囲（x −144〜135、z −184〜127）は、道の高さの13下から64上までを作り直します。
- 範囲の中の「もの」（額縁・防具立て・落ちたアイテム・動物など）は、消してから置き直します。プレイヤーは消えません。建て直しても額縁は重なりません。
- まだ作っていない道と広場は、地面に仮に示します（ゾーニングの図の中央広場・大通り・環状路・城壁沿いの道・各施設の前の広場は土の道、城壁の場所（半径112〜116）は砂利）。
- 大結晶・結界の柱・城壁・門・家・木は、まだありません。

| 場所 | 名前 | 立つ所（中央広場の真ん中から） | 向き |
| --- | --- | --- | --- |
| `plaza` | 中央広場 | (0, 1, 12) | 北（塔の道とギルド） |
| `guild` | 冒険者ギルド | 塔の道 (−2, 1, −79) | 東（正面） |
| `storage` | 個人倉庫 | 環状路の南 (49, 1, −46) | 北（正面） |
| `weapon` | 武器工房 | 工房通りの南 (90, 1, 6) | 北（正面） |
| `armor` | 防具工房 | 工房通りの北 (71, 1, −7) | 南（正面） |
| `accessory` | 装飾工房 | 工房通りの南 (31, 1, 6) | 北（正面） |
| `market` | 商店街 | 中央広場の南の端 (0, 1, 17) | 南（市場通りの奥） |
| `free_market` | フリーマーケット | 環状路の北 (−34, 1, 51) | 南（北の門） |
| `church` | 結びの教会 | 祈りの道 (−51, 1, 0) | 西（正面と鐘楼） |
| `fishing` | 釣り堀 | 環状路の南 (−50, 1, 56) | 西（入口の門と池） |
| `lake` | 水門と湖 | 城壁の外の湖の南東の岸 (−100, 1, 88) | 北西（湖と水門） |
| `casino` | カジノ | 市場通り (−2, 1, 84) | 東（正面） |
| `tower` | 塔の入口 | 塔の道の北の端 (0, 1, −110) | 北（北門の外の広場と塔の入口） |
| `top_n`・`top_s` | 上空（北の半分・南の半分） | 高さ110、(−14, −101)・(−14, 26) の上 | 真下（画面の上が北） |
| `aerial_se` | 上空（南東から） | (110, 70, 110) | 北西 |
| `aerial_nw` | 上空（北西から） | (−126, 70, −151) | 南東 |
| `restaurant` | 料理屋（育ったあと） | 市場通り (2, 1, 28) | 西（商店街の北の西の店） |
| `stalls` | フリーマーケットの露店 | 高さ30、(−34, 76) の上 | 真下 |
| `frozen_pond` | 釣り堀の池（第2章の氷） | 高さ30、(−80, 47) の上 | 真下 |
| `highlow` | ハイ＆ローの卓（育ったあと） | カジノ1階 (22, 3, 92) | 南（カードの卓） |
| `fishrace` | 魚レースの走路（育ったあと） | カジノ2階の宴の間 (25, 10, 80) | 南（水の走路） |
| `monument` | 記念碑（育ったあと） | 塔前広場 (−6, 2, −128) | 西（記念碑） |
| `forge` | 古代炉（育ったあと） | 武器工房の炉の間 (93, 1, −15) | 北（古代炉） |

描く距離が12チャンクだと、それより遠い所は霧で見えません。そのため、上からの見どころは北と南の半分ずつにしています。描く距離を20チャンク以上にすれば、もっと高い所から全体を見られます。

## CIで撮る写真（11月21日に足した）

建物の見本の写真の後に、街を空（道の高さ y 200）に建てて、次の写真を撮ります。

- 上から・斜めから：`city_top_n`・`city_top_s`・`city_aerial_se`・`city_aerial_nw`
- 各施設の前：`city_plaza`・`city_guild`・`city_storage`・`city_weapon`・`city_armor`・`city_accessory`・`city_market`・`city_free_market`・`city_church`・`city_fishing`・`city_lake`・`city_casino`・`city_tower`
- 夜：`city_top_n_night`・`city_top_s_night`・`city_plaza_night`・`city_market_night`・`city_church_night`・`city_tower_night`
- 育ったあとの形：`city_grown_top_n`・`city_grown_top_s`・`city_grown_monument`・`city_grown_forge`・`city_grown_highlow`・`city_grown_fishrace`・`city_grown_restaurant`・`city_grown_stalls`・`city_grown_frozen_pond`・`city_grown_lake`

写真が33枚増えたので、クライアントの確かめの時間の上限を45分にしました（ジョブは55分）。
CIのコマンドはサーバーとして動くので、移る人を `@p` で渡します（`city tp <場所> @p`）。遠くへ移ったあとは、まわりの形を作り直すのを待ってから撮ります（上からと斜め、100ブロックより遠くへ移るときは10〜12秒、ほかは5〜7秒）。
空に建てているので、城壁の外の遠くは空の色に写ります（描く距離12チャンクの霧の外は描かれない）。

## ファイル

| ファイル | 役割 |
| --- | --- |
| `src/main/java/jp/amemaru/lostconnection/admin/CityCommands.java` | `/amemaru admin city`（置き場所・道の範囲・見どころ・最後に建てた所の記録 `amemaru_city_check.dat`） |
| `src/main/resources/data/amemaru/worldgen/world_preset/conelia_check.json` | 確かめ用の平らなワールド |
| `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json` | ワールドタイプの一覧に出す |
| `src/main/java/jp/amemaru/lostconnection/client/CiScreenshots.java` | CIの街全体の写真（`CITY` の段階） |
