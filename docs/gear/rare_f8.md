# ⑮ 8階のレア装備（湖主の髭弓）

![湖主の髭弓と、8階の仕掛けのブロックの確認画像（下の段：湖の氷・薄氷0〜3・髭弓の持ち物の絵とモデルの絵・状態のアイコン・歌い手の姿）](rare_f8_sheet.png)

| 項目 | 内容 |
| --- | --- |
| 品 | 湖主の髭弓（アーチャーの弓） |
| レアリティ | エピック |
| 世界観 | Phase 0-A 20章（確定）：8階のボス「声なき湖主ムーネ」のレア「湖主の髭弓（弓：静寂状態でも予兆の音が自分にだけ聞こえる）」4%。出ないと少しずつ出やすくなる（天井）。ムーネの光る長いひげを弦に張った弓。湖の音がすべて消えても、この弦だけは、危ないものが来る前に、持ち主の耳もとで小さく鳴る |
| 数値【補完】 | 第2世代・必要Lv34・アーチャー・攻撃35・会心7・耐久620・装備で持ち主が決まる。**ロスト状態「静寂」の間も、予兆が出た所から鈴の音が自分にだけ聞こえる**（特殊効果 `warning_ear`。働きはクライアントの `client/LakeSoundFilter.java`）。修理は静氷2・湖の真珠1 |
| 形【補完】 | 11月26日に形から作り直した（前は霜銀の弓の塗り直し）。湖主ムーネ（大きなナマズ）の体と同じ色：灰白のうろこの段の腕・弓の先からさらに伸びて外へ巻く、光るひげ・光るひげの弦・握りの白い真珠と、それを巻く淡い青のひれ・握りの外（背）に立つ三本の骨のひれ。下のひげは、Java のモデルの決まり（−16より下に出せない）で短く巻く。弦の位置と持ち方は霜銀の弓と同じ |
| 光る所 | エピック：ひげの弦と、上下の先の巻いたひげ（7部品。Java のモデルの `forge_data` の明るさ15） |
| 描き方 | 手に持つと3D（`models/item/lake_lord_whisker_bow_3d.json`）、持ち物の枠・額縁・地面は2Dの絵（32×32） |
| 作り直し方 | `python3 tools/artgen/make_lake_things.py . <確認画像の出力先>`（8階のブロックと、この弓。弓は中で `tools/artgen/make_gear_rare_reshape.py` の `export_rare_weapon` を呼ぶ。持ち物の絵は `rare_icons.py`）→ `python3 tools/artgen/enhance_icons.py .`。弓の確認画像（[lake_lord_whisker_bow_sheet.png](lake_lord_whisker_bow_sheet.png)）は `make_gear_rare_reshape.py` が作る |

![湖主の髭弓（正面・側面・背面・斜めと持ち物の枠。11月26日に形から作り直した）](lake_lord_whisker_bow_sheet.png)

## ゲームの中での確認

- CIの写真：`phase8_f8_bow`（アーチャーで持ったとき）
- 自動テスト：`LakeGameTests.floor8GearAndSerena`（効き目）・`muneVeilBrokenByTwoBells`（ムーネのレア 4%）
