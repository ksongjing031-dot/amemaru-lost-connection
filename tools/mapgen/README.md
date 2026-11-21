# マップの設計データの道具（tools/mapgen）

建物や街の区画を Python で組み立て、構造物ファイル（`.nbt`、Minecraft 1.20.1）に書き出す道具です。
ゲームを起動しなくても、バニラのテクスチャで見本の絵を描いて、形と色を確かめられます。

## 使い方

```sh
pip install nbtlib numpy pillow
python3 tools/mapgen/build_guild.py                       # Modの data/amemaru/structures/map/conelia/guild_hall.nbt を書き直す
python3 tools/mapgen/build_guild.py /tmp/out --render /tmp/out   # 別の所に書き出して、見本の絵も描く（6つの向き＋まとめた1枚、各階を上で切った絵3枚、中の絵4枚）
python3 tools/mapgen/build_weapon.py                      # weapon_workshop.nbt と weapon_forge_lit.nbt（古代炉に火が入った形）を書き直す
python3 tools/mapgen/build_weapon.py /tmp/out --render /tmp/out  # 見本の絵も描く（6つの向き＋まとめた1枚、切った絵3枚、中の絵4枚＋火の入った古代炉）
python3 tools/mapgen/build_armor.py                       # armor_workshop.nbt（防具工房）を書き直す
python3 tools/mapgen/build_armor.py /tmp/out --render /tmp/out   # 見本の絵も描く（6つの向き＋まとめた1枚、切った絵2枚、中の絵6枚＋第4章の型紙の壁）
python3 tools/mapgen/build_armor.py /tmp/out --render /tmp/out --only front,in_shop   # 一部の絵だけ描く
python3 tools/mapgen/build_accessory.py                   # accessory_workshop.nbt（装飾工房）を書き直す
python3 tools/mapgen/build_accessory.py /tmp/out --render /tmp/out   # 見本の絵も描く（6つの向き＋まとめた1枚、切った絵2枚、中の絵9枚＋留め具の枠のいまと第3章）
python3 tools/mapgen/build_market.py                      # market_north.nbt・market_south.nbt・market_restaurant_open.nbt（商店街）を書き直す
python3 tools/mapgen/build_market.py /tmp/out --render /tmp/out   # 見本の絵も描く（北6・南6、切った絵2枚、中の絵6枚、料理屋の開く前と後と中）
python3 tools/mapgen/build_storage.py                     # personal_storage.nbt（個人倉庫：受付棟・渡り廊下・地下2層）を書き直す
python3 tools/mapgen/build_storage.py /tmp/out --render /tmp/out  # 見本の絵も描く（ギルドと重ねた外7枚、中の絵11枚、各階を切った絵4枚）
python3 tools/mapgen/build_storage.py /tmp/out --ci       # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_freemarket.py                  # free_market.nbt（フリーマーケットのはじめの形）と free_market_expanded.nbt（増えた8区画）を書き直す
python3 tools/mapgen/build_freemarket.py /tmp/out --render /tmp/out   # 見本の絵も描く（はじめの形8枚・増えたあと3枚、高さ4で切った絵と真上の絵を2つずつ）
python3 tools/mapgen/build_freemarket.py /tmp/out --ci    # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_church.py                      # church.nbt（結びの教会）・church_garden.nbt（記憶の庭）・church_counsel.nbt（6日目の記憶の相談所）を書き直す
python3 tools/mapgen/build_church.py /tmp/out --render /tmp/out   # 見本の絵も描く（外5枚・中5枚・記憶の庭2枚・相談所1枚、高さ7・12・17で切った絵3枚。記憶の庭を並べて描く）
python3 tools/mapgen/build_church.py /tmp/out --ci        # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_fishing.py                     # fishing_pond.nbt（釣り堀）・water_gate.nbt（水門と湖）・fishing_alley.nbt（路地）・fishing_lakeside.nbt（湖畔の釣り場）・fishing_pond_frozen.nbt（第2章の氷）を書き直す
python3 tools/mapgen/build_fishing.py /tmp/out --render /tmp/out   # 見本の絵も描く（はじめの形16枚・湖畔の釣り場4枚・氷1枚、高さ1・3で切った絵2枚。水門と湖・路地を並べて描く）
python3 tools/mapgen/build_fishing.py /tmp/out --ci       # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_casino.py                      # casino.nbt（カジノ）・casino_highlow.nbt（共同事業②のハイ＆ロー）・casino_fishrace.nbt（共同事業⑥の魚レース）を書き直す
python3 tools/mapgen/build_casino.py /tmp/out --render /tmp/out   # 見本の絵も描く（外6枚・中13枚・ハイ＆ロー1枚・魚レース2枚、地下・1階・2階で切った絵3枚）
python3 tools/mapgen/build_casino.py /tmp/out --render /tmp/out --only front,vip   # 一部の絵だけ描く
python3 tools/mapgen/build_casino.py /tmp/out --ci        # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_tower.py                       # tower_entrance.nbt（塔の入口）・tower_plaza.nbt（塔前広場）・tower_plaza_monument.nbt（1週目の記念碑）を書き直す
python3 tools/mapgen/build_tower.py /tmp/out --render /tmp/out   # 見本の絵も描く（外6枚・中13枚・記念碑1枚、高さ5・17で切った絵2枚。入口と広場を合わせて描く）
python3 tools/mapgen/build_tower.py /tmp/out --render /tmp/out --only hall,ring   # 一部の絵だけ描く
python3 tools/mapgen/build_tower.py /tmp/out --ci         # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
python3 tools/mapgen/build_city.py                        # 街並み（道・中央広場と大結晶・城壁と門・家・倉庫・灯り・木）を town/t*_*.nbt に書き直す（20秒ほど）
python3 tools/mapgen/build_city.py /tmp/out --render /tmp/out    # 別の所に書き出して、上から見た地図（town_map.png）も描く
python3 tools/mapgen/build_floor1.py                      # 塔の1階「始まりの草原」（384×384）を map/floor1/p*_*.nbt と floors/floor1.json に書き直す
python3 tools/mapgen/build_floor1.py /tmp/out --render /tmp/out  # 見本の絵も描く（上から見た地図・ボスの広間・石柱・入口）
python3 tools/mapgen/check_city.py                        # 街並みと11の施設の構造物を世界の座標に並べて、街全体を通しで確かめる（はじめの形と育ったあとの形。30秒ほど）
python3 tools/mapgen/check_city.py --render /tmp/city     # 結果（city_check.json）と、上から見た街・南から見た高さ・夜の灯り・つながる道の絵も書く
python3 tools/mapgen/check_city.py --render /tmp/city --aerial   # 鳥瞰の絵も描く（光線で描くので1〜2分）
python3 -I tools/mapgen/import_edits.py <送ってもらったフォルダ>   # ゲームの中で直した所（city export）を確かめて edits/ に取り込む
python3 -I tools/mapgen/import_edits.py <フォルダ> --dry-run       # 確かめるだけ
```

ゲームの中で直した所（`/amemaru admin city export` が書き出す `generated/amemaru/city/edits/<構造物>.json`）は、
`import_edits.py` で `tools/mapgen/edits/` に取り込みます。`World.save_structure` が書き出すときに重ねる（`mapgen/edits.py`）ので、
`build_*.py` で作り直しても直しは残ります（見本の絵は、書き出す前に描くので、直しは入りません）。取り込んだら、表示される
`build_*.py` で作り直し、`check_city.py` で街全体を確かめます。送ってもらったファイルは信じずに確かめます（形・構造物の名前・マスの範囲・
ブロックの書き方・中身・「もの」の種類）。くわしくは `docs/ingame_check.md` の「建物を直す」。

見本の絵のテクスチャと模型（ランタン・看板・旗などの形）は、初めて使うときに GitHub の Minecraft の素材の写し（1.20.1）から
取ってきて、`~/.cache/amemaru-mapgen` にためます（リポジトリには入れません）。

ゲームの中では、クリエイティブで平らな所に立って次のコマンドで置けます（正面は西。置いた範囲の中は片づきます）。

```
/place template amemaru:map/conelia/guild_hall ~ ~-1 ~
/place template amemaru:map/conelia/weapon_workshop ~ ~-1 ~
/place template amemaru:map/conelia/armor_workshop ~ ~-1 ~
/place template amemaru:map/conelia/accessory_workshop ~ ~-1 ~
/place template amemaru:map/conelia/market_north ~ ~-1 ~
/place template amemaru:map/conelia/market_south ~ ~-1 ~38
/place template amemaru:map/conelia/personal_storage ~16 ~-8 ~18
/place template amemaru:map/conelia/fishing_pond ~ ~-10 ~
/place template amemaru:map/conelia/water_gate ~-36 ~-10 ~25
/place template amemaru:map/conelia/fishing_alley ~29 ~-2 ~43
/place template amemaru:map/conelia/casino ~ ~-4 ~
/place template amemaru:map/conelia/tower_entrance ~ ~ ~
/place template amemaru:map/conelia/tower_plaza ~-1 ~-2 ~30
```

武器工房は正面が南です（立った所から東へ25、南へ22ブロック広がります）。古代炉に火が入った形（12日目）は、
工房を置いた角から (13, 1, 3) ずらした所に `amemaru:map/conelia/weapon_forge_lit` を重ねて置きます（古代炉のまわりの 5×20×4 だけが入れ替わります）。
防具工房は正面が北です（立った所から東へ22、南へ23ブロック広がります）。
装飾工房は正面が南です（立った所から東へ19、南へ22ブロック広がります）。
商店街は南北に通る市場通り（幅9）の両側の店で、環状路（幅7）をはさんで北と南の2つの構造物に分かれています
（北は東へ37・南へ32、南は東へ37・南へ44。上のように南を38ずらして置くと、間の6列＋北の最後の1列が環状路の幅になります）。
北の最後の1列（環状路の1列目）と、南の東側（カジノの場所）は structure_void で、置いても元のブロックを消しません。
寄付で料理屋が開いたあとの形は、北と同じ所に `amemaru:map/conelia/market_restaurant_open` を重ねて置きます
（料理屋のまわりの 15×26×13 だけが入れ替わります）。
個人倉庫は、ギルドを置いた後に、ギルドの角から東へ16・下へ7・南へ18ずらして重ねます（上の最後の行。ギルドを `~ ~-1 ~` で
置いたとき）。地上の受付棟とギルドの東の渡り廊下に加えて、ギルドの1階の床の下（保管庫）と、受付棟の下の地下2層まで入っています。
ギルドの建物の中・軒・地面の下の土は書かないので、置いてもギルドは消えません（下の決まりの `owned`）。
釣り堀は池の底が道より9低いので、道に立って `~ ~-10 ~` で置きます（立った所から東へ36、南へ44）。水門と湖（城壁の一部と
城壁の外の湖を含む）は西へ36・南へ25、路地は東へ29・南へ43・上へ8（釣り堀の構造物の底から）ずらして並べます（上の最後の3行）。
釣り堀の構造物は記憶の庭と城壁沿いの道のマスを書かないので、記憶の庭の後に置いても庭は消えません。寄付で開く湖畔の釣り場
（`fishing_lakeside`）は水門と同じ所に、第2章の氷（`fishing_pond_frozen`）は釣り堀と同じ所に重ねます（書き換えるマスだけを書く）。
カジノは地下（秘密の階）があり、構造物の底が道より3低いので、道に立って `~ ~-4 ~` で置きます（立った所から東へ43、南へ36。
建物は東へ8・南へ4の所から 30×26）。城壁沿いの道（中心から半径107より外）・南門の前の東の区画・茶と香辛料の店（商店街の南の東の角の店）の
南の横の地面より上は書かないので、商店街の南の後に置いても店の入口の段や屋根は消えません。寄付で足すハイ＆ロー
（`casino_highlow`）と魚レース（`casino_fishrace`）は、カジノと同じ所に重ねます（書き換えるマスだけを書く）。
塔の入口は足もとの地面（道の高さ）を書かないので、地面に立って `~ ~ ~` で置きます（足もとの1つ上から、東へ45、南へ30、上へ48。
正面は南）。塔前広場は西へ1・下へ2・南へ30ずらして並べます（上の最後の2行。広場の南の端が北門の外の面に接します）。
1週目の共同の目標のあとの記念碑（`tower_plaza_monument`）は、塔前広場と同じ所に重ねます（台と碑と碑文のマスだけを書く）。

## 中身

| ファイル | 役割 |
| --- | --- |
| `mapgen/world.py` | 設計データ（ブロックの置き場所）と、構造物ファイルへの書き出し。柵・塀・板ガラスのつながりと階段の角の形は、書き出す前に `finalize()` で周りを見て決める（構造物として置くと、ゲームは中のブロックの形を計算し直さないため） |
| `mapgen/arch.py` | 切妻の屋根（`Roof`）・四角すいの屋根（`Pyramid`）・切妻の三角の壁・飾りの小塔（`finial`） |
| `mapgen/facade.py` | 壁の立面を、奥行きの層ごとの文字の絵で描いて置く（`stamp`・`compose`）、窓（`window`） |
| `mapgen/render.py` | 見本の絵。1ブロックを 4×4×4 の小さな箱に分けて、階段・ハーフ・柵・トラップドアなどの形と、日の光の影を描く。小さな部品は `models.py` の模型で描いて重ねる |
| `mapgen/models.py` | 小さな部品の模型。ランタン・鎖・焚き火・ろうそく・花・植木鉢・金床・砥石・書見台・大釜などはゲームの模型（blockstates・models）を読み、看板・旗（模様つき）・チェスト・寝台・鐘・額縁（中のアイテム）・防具立ては箱を組む。防具立てに着せた鎧（革は染めた色も）と、額縁の模様入りの盾も描く |
| `mapgen/fixtures.py` | 街の灯り（都市のルール⑦）：古代の結晶灯（`crystal_lamp`。大通りと広場）、壁のランタン（`wall_lantern`）、再建のランタン灯（`lamp_post`）、鎖で吊る灯り（`hang_lantern`）、ろうそく（`candles`） |
| `build_guild.py` | コネリアの冒険者ギルド（PHASE 1 の設計。36×38×41、正面は西。外観・内装・まわりまで） |
| `build_weapon.py` | コネリアの武器工房（PHASE 2 の設計。25×24×22、正面は南）と、古代炉に火が入った形（5×20×4） |
| `build_armor.py` | コネリアの防具工房（PHASE 3 の設計。22×24×23、正面は北）。型紙の壁が章ごとに増える形は `patterns_world(枚数)` |
| `build_accessory.py` | コネリアの装飾工房（PHASE 4 の設計。19×24×22、正面は南）。第3章で留め具の枠が埋まる形は `clasp_world()` |
| `build_market.py` | コネリアの商店街（PHASE 5 の設計。北 37×26×32・南 37×26×44）。14軒の店は4つの型（A 平入り2階・B 妻入り2階・C 妻入り3階・D 角の平入り）の `Shop` を組み替え、店ごとの品と色は `TRADES`。寄付で開いた料理屋の形は `restaurant_overlay()` |
| `build_storage.py` | コネリアの個人倉庫（PHASE 6 の設計。40×30×23、正面は南）。平面図の (u, v) と高さ y（道のブロックが 0、地下は負）で組み立てる。地下の部屋は「中のマス」を決めて `shell()` でまわりを壁・床・天井で囲む。ギルドと重ねた写しは `combined_world()`（見本の絵と確かめで使う） |
| `build_freemarket.py` | コネリアのフリーマーケット（PHASE 7 の設計。30×10×32、北の門と東の門）。露店20区画は `PLOTS`（番号・場所・向き・型・色・縁の木・品）で、7つの型（`STYLES`）の部品を組み替える。はじめの形 `build(False)` と増えたあと `build(True)` は同じ乱数の種で作り、重ねる形は増える8区画の箱だけを書く（`overlay()`）。商店街の南と並べた写しは `combined_world()` |
| `build_church.py` | コネリアの結びの教会（PHASE 8 の設計。46×41×38、正面は東）と記憶の庭（25×12×18）。平面図の (u, v) と高さ y（道のブロックが 0）で組み立てる。アーチは `arch_row()`、色ガラスの窓は `lancet()` と `rose()`、鐘楼のらせん階段は `spiral()`（1回りめは下を埋め、2回りめは裏に逆さの階段）。記憶の庭を並べた写しは `combined_world()`（見本の絵と確かめで使う）。6日目の記憶の相談所の重ねる形は `counsel_overlay()`（書く所は `COUNSEL_OWNED` の4マスだけ）、重ねたあとは `counsel_world()` |
| `build_fishing.py` | コネリアの釣り堀（PHASE 9 の設計。36×25×44、入口は東）と、水門と湖（48×34×48）・路地（18×8×14）。平面図の (u, v) と高さ y（道のブロックが 0、水面のブロックは −3）で組み立てる。池の形は `POND_ROWS`、水の深さは `depth()`（岸からの近さ）、岸の斜面は `land_dist()`。釣り堀が書くマスは `mine()`（記憶の庭と城壁沿いの道は書かない）。水は構造物ごとに漏れない（池は `weir()` の堰板、水門は `gate_channel()` の堰板で止まる）。寄付で開く湖畔の釣り場は `lakeside()`（書くマスは `LK_OWNED`）、第2章の氷は `frozen_overlay()`（`FROZEN_OWNED`）。3つの形を並べた写しは `checked_world(which)`（見本の絵と確かめで使う） |
| `build_casino.py` | コネリアのカジノ「ラッキーノット」（PHASE 10 の設計。43×29×36、正面は西）。建物の中の (u, v)（u 0 = 世界の x 13、v 0 = 世界の z 72）と高さ y（道のブロックが 0、地下は負）で組み立てる。屋根は前と奥の寄棟 `Hip`（金の縁の軒）、大広間の切妻と真ん中の帯の切妻（`Roof`、縁は `dark_rim()` で黒くして外の破風板だけを金に）、正面の両端の小塔。ルーレットの卓は `roulette()`（12の目は `POCKETS`）、大きな吊り灯は `chandelier()`、部屋から見える屋根の下は `roof_boards()` で野地板にする。書くマスは `owned()`（城壁沿いの道・南門の前の東の区画・茶と香辛料の店の横は書かない）。寄付で足すハイ＆ローは `highlow_overlay()`（`HL_OWNED`）、魚レースは `fishrace_overlay()`（`FR_OWNED`）。重ねたあとの写しは `checked_world(which)` |
| `check_city.py` | 街全体の通しの確かめ。構造物ファイル（`src/main/resources/.../map/conelia/*.nbt`）を、地図を組むときの位置と順番（街並みの区画 `town/placements.txt` を先に、次に `PLACEMENTS`。docs/map_style.md の「置き方（地図を組むとき）」と同じ）に並べた街 `City(stage)`（街並みを入れないときは `town=False`）（`"start"` はじめの形、`"grown"` 重ねる形をすべて重ねた形）で、構造物どうしの重なり（`overlaps`）、ゾーニングの図の道と広場（`ROADS`・`ROAD_CENTERS`。まだ作っていない所も）の真ん中と上の物、城壁と城壁沿いの道にかかる物、高さ（`footprints`・`ring_heights`）、灯り（`lamp_kinds`・`chandeliers`・`door_lamps`・`dark_spots`）、つながる道（`walk`・`back_reach`。まだない道は道の高さの地面、施設のない土地は歩けない所）と各 PHASE の行き先（`GOALS`）を確かめる。読んだ構造物は `~/.cache/amemaru-mapgen/city` にためる |
| `city_figures.py` | 街全体の絵（`check_city.py --render` から呼ぶ）：上から見た街（各柱のいちばん上のブロックの色と陰）・南から見た高さ（施設ごとの影絵と④の線）・夜の灯り（中央広場から歩ける所の明るさと灯りの種類）・つながる道・鳥瞰（`render_aerial()`。池と湖は `render(sunken=...)` で地面の平らな面の下も描く） |
| `build_city.py` | コネリアの街並み（Phase 5）。11の施設の構造物（`check_city.City("grown", town=False)`）が置かれていない所を、建築の文書 Tab 1 の都市のルールで埋める：道と中央広場の舗装（`paving`・`plaza`。大結晶）、城壁と3つの門と門の塔・斜めの4つの塔（`wall_and_gates`。城壁の上の通路は見張りの所で、上がる階段は作らない）、結界の柱8本、家50軒（`house`。2階＋屋根裏、平入りと妻入り、**内装なし**。扉は手で開かない `amemaru:house_door_*`、中は空洞で見えない灯り）・倉庫3棟（`warehouse`）・小広場と井戸6か所・裏庭の木と花の畑（`yards`）・灯り（`street_lamps`）・案内板（`signposts`）。暗くて湧ける所は、見えない灯り（`minecraft:light`）で埋める（`fill_dark`。街が光をさえぎって暗くなった施設の足もとも、そばの空いたマスで）。街の箱（x −144〜135、z −184〜127）を 48×48 の区画 `town/t<i>_<j>.nbt` に、空気を書かずに書き出し、置く所を `town/placements.txt` に書く。施設の書くマスは書かない（`Town.put`）ので、施設より先に置く |
| `build_floor1.py` | 塔の1階「始まりの草原」（Phase 5。384×48×384）。丘で囲んだ草原・小川と石橋・古代の道・3本の石柱・封印の門・草原の主の広間（ボスの部屋）・道標の石板・湧く所。48×48 の区画64個（`map/floor1/p<i>_<j>.nbt`）と、階の定義 `floors/floor1.json`（入口・戻りの円・ボスの部屋・石柱・湧く所・石板）を書き出す |
| `floor_figures.py` | 階の見本の絵（`build_floor1.py --render` から呼ぶ） |
| `build_tower.py` | コネリアの塔の入口（PHASE 11 の設計。入口 45×48×30・塔前広場 47×18×32、正面は南）。世界の座標（x・z）と高さ y（道のブロックが 0）で、入口と広場を1つの合わせた形（47×50×62）に組み立て、書き出すときに2つの箱に切り分ける（`split()`。箱からはみ出すブロックがあれば止まる）。塔の基部の丸い壁は `base_face()`（3つの帯の半径 `BANDS`）、門のアーチは `arch_open()`、転移門の輪は `ring_cell()`・`ring_open()`、左右の塔のらせん階段は `spiral()`、広場の段は `terrace()`。重ねる形は記念碑（`M`）。確かめの写し `checked_world()` は、建物の下と外にゲームの地面（y 0）を足す |

## 決まり

- 座標は x が東、y が上、z が南。ブロックは `(名前, 性質)` の組で、名前に `minecraft:` は付けない。
- 階段の `facing` は高い側（背）の向き。屋根では棟の方を向ける。
- 開いたトラップドアの板は、`facing` と反対の側にある。壁に沿わせる（雨戸）ときは、`facing` を壁の外向きにする。
- 屋根どうしが交わる所は、`Roof.inside()`（屋根の面より下か）で、ほかの屋根の中に入るブロックを置かない。
  これで谷ができ、低い方の屋根は高い方の下に隠れる。
- 屋根の縁取りのブロックは `Roof(trim_block=...)` で変えられる（ふつうは `pal.trim + "_planks"`。カジノの金の縁は、なめらかな砂岩）。
- 感圧板は、見本の絵でも確かめでも、当たりのない薄い板（絨毯と同じ形）として扱う（カジノのルーレットの金の軸と金庫）。
- 構造物ファイル1つは 48×48×48 まで。それより大きい施設（塔の入口と塔前広場）は、1つの合わせた形で設計して、
  書き出すときに箱ごとに切り分ける（`build_tower.py` の `split()`。額縁の `TileX`・`TileY`・`TileZ` も箱の中の位置に直す）。
- 地面より上だけを書く構造物（塔の入口）は、足もとの地面の1つ上から置く。地面の高さに置く部品（壁の足もとの水切りなど）は、
  地面の1つ上の半ブロックで作る（その下はゲームの地面のまま）。
- 立面の文字の絵では、層 0 が1階の石の面、+1 が柱と2階の漆喰、+2 が2階の木組み（張り出し）、+3 が花箱の前板や灯りの腕木。
  文字の意味は `facade.legend()` にある（例：`P` 漆喰、`S` 石、`|` 柱、`=` 梁、`(` `)` 石のアーチの角、`[` `]` 木のアーチの角、
  `v` 持ち送り、`g` 板ガラス、`f` `a` 花箱の葉、`L` 吊るした灯り、`.` 空ける、空白は何もしない）。
- 看板の文字・旗の模様・書見台の本は、`World.put(x, y, z, 状態, nbt)` の `nbt` に `sign_nbt(...)`・`banner_nbt(...)`・
  `lectern_nbt(題, 書いた人, [ページ, ...])` を渡す（構造物ファイルの、ブロックの追加データとして書き出す。書見台の状態は `has_book=True`）。
- 2つの屋根の軒が同じ高さで出会う角では、`inside()` どうしが消し合ってすき間ができることがある。組み立てた後で手で埋める
  （`build_guild.py` の `roofs()` の終わり）。
- 灯りは `fixtures.py` の形を使う（どの建物でも同じ形にするため）。形を変えるときは、建築の文書の都市のルール⑦も直す。
- 置いた後で壊れないように、吊るす灯りの上・置く灯りの下・花の下（草）・扉の下には、支えになるブロックを置く。
  板ガラスは、両脇がガラスかブロックになるようにする（つながらないと細い柱に見える）。
- 置いても元のブロックを消さない所（ほかの区画にかかる所）には `structure_void` を置く。書き出すときは、その所をファイルに
  書かない（ゲームの構造物ブロックが保存するときと同じ。`structure_void` のまま書くと、見えないブロックとして置かれ、元のブロックが消える）。
- ほかの建物と大きく重なる構造物（個人倉庫の地下がギルドの下に入るなど）は、`save_structure(path, owned=関数)` で書き出す。
  何も置いていないマスのうち `owned(x, y, z)` が False の所は書かない（structure_void と同じ）。地下の部屋の空気のように、
  はっきり空けたいマスは `owned` で True にする（`build_storage.py` は、空けたマスを `AIR_CELLS` に覚えておく）。
- 階段は、いちばん上の段を上の床と同じ高さに置く（段のブロックの上の面が、上の床の上の面とそろう）。高さ h の階を上る階段は
  h 段になる。段が床より1低いところで終わると、最後に1ブロックの段差が残って、跳ばないと上れない。
- 額縁と防具立ては「もの（エンティティ）」として、`World.item_frame(x, y, z, 向き, アイテム)`・`World.armor_stand(...)` で置く。
  アイテムは名前のほか、`("leather_chestplate", {"color": 0x8E3A2E})`（革の色）や
  `("shield", {"base": "gray", "patterns": [("cs", "red")]})`（盾の地と模様。記号は旗と同じ）の組でも渡せる。
  構造物ファイルの `entities` に書き出され、`/place template` で一緒に置かれる。どちらも固定（`Fixed`・壊れない・重力なし）で、
  額縁は向きの反対側のブロックに掛かる（`"up"` は台の上に寝かせる、`"down"` は梁の下に吊るす）。置く所のマスはあけておく。
  プレイヤー（サバイバル・アドベンチャー）は中身を取る・回す・入れる・壊すができない（額縁は `Fixed`・`Invulnerable`、
  防具立ては `DisabledSlots`・`Invulnerable`）。この守りは `gametest/MapDisplayGameTests.java` で毎回確かめる。
  額縁か防具立てのある建物を足したら、そのファイルにテストを1つ足す（額縁と防具立ての数を書く）。
- 上にふさぐブロックがある土の道（`dirt_path`）は、置いたときにゲームが土に変えるので、`finalize()` で先に土にする。
- 物語で形が変わる所（育つ施設）は、変わる範囲だけの小さな構造物を別に書き出して、後から重ねて置く
  （`build_weapon.py` の `forge_lit_world()`。範囲の中は空気も書き出すので、外したブロックは消える）。
- 街並み（`build_city.py`）は施設の後から作るので、施設を足したり、置き方や形を直したりしたら、`build_city.py` も作り直す
  （施設の書くマスを避けて、家・灯り・暗い所の灯りを置き直す）。
- 街並みの家の並び（`row`）は、中央広場の縁から13まで（④の最初の1列）にかかる家を、軒9・煙突なし・屋根の縁取りの頂も18までにする
  （`first_ring`・`house(max_top=18)`）。木の葉は道・広場・城壁沿いの道の上に出さない（`leaf_ok`）。灯りの腕木と結晶灯は、
  道の真ん中の帯（⑤）に出さない。
- 作物（小麦・ニンジン・ジャガイモ）は植えない。時間で育ち、建てた直後に一部が消えることもあって、ゲームの中で直した所を
  見分ける確かめ（`city export`・自動テスト）に出てしまう。花・シダ・ツツジなど、変わらない植物にする。
- 書き出すファイルは、同じ設計からいつも同じになるようにする（性質は名前の順、圧縮の時刻は 0）。
- 施設を足したり、置き方や形を直したりしたら、`check_city.py` で街全体を通しで確かめる。新しい施設は `PLACEMENTS` に
  置く所と順番（後に置くほど上書きする）を足し、重ねる形は土台の構造物の名前も書く。各 PHASE の行き先は `GOALS` に足す。
- 1つの施設の中の直しで、乱数を引く数を変えない（`rng.random()` を引く回数が変わると、そのあとに作る形の乱数がすべてずれて、
  直していない所まで変わる。`build_market.py` の `build_chimney()` のように、引く数はそのままにして、置くかどうかだけを変える）。

様式（材料・層・屋根・飾り）の決まりは `docs/map_style.md` にまとめています。
