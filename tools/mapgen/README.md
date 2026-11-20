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
python3 tools/mapgen/build_church.py                      # church.nbt（結びの教会）と church_garden.nbt（記憶の庭）を書き直す
python3 tools/mapgen/build_church.py /tmp/out --render /tmp/out   # 見本の絵も描く（外5枚・中5枚・記憶の庭2枚、高さ7・12・17で切った絵3枚。記憶の庭を並べて描く）
python3 tools/mapgen/build_church.py /tmp/out --ci        # 自動の確認の写真の座標（CiScreenshots.java の MAP_VIEWS の行）を出す
```

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
| `build_church.py` | コネリアの結びの教会（PHASE 8 の設計。46×41×38、正面は東）と記憶の庭（25×12×18）。平面図の (u, v) と高さ y（道のブロックが 0）で組み立てる。アーチは `arch_row()`、色ガラスの窓は `lancet()` と `rose()`、鐘楼のらせん階段は `spiral()`（1回りめは下を埋め、2回りめは裏に逆さの階段）。記憶の庭を並べた写しは `combined_world()`（見本の絵と確かめで使う） |

## 決まり

- 座標は x が東、y が上、z が南。ブロックは `(名前, 性質)` の組で、名前に `minecraft:` は付けない。
- 階段の `facing` は高い側（背）の向き。屋根では棟の方を向ける。
- 開いたトラップドアの板は、`facing` と反対の側にある。壁に沿わせる（雨戸）ときは、`facing` を壁の外向きにする。
- 屋根どうしが交わる所は、`Roof.inside()`（屋根の面より下か）で、ほかの屋根の中に入るブロックを置かない。
  これで谷ができ、低い方の屋根は高い方の下に隠れる。
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

様式（材料・層・屋根・飾り）の決まりは `docs/map_style.md` にまとめています。
