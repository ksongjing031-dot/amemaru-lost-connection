# Phase 1：土台・プレイヤーデータ・ステータス・レベル・基本HUD

Phase 0-B 13章の「Phase 1でやること」をすべて作りました。確認の目安は「レベルを上げるとステータスが変わり、HUDに出る。入り直しても残っている」です。

## 1. 決めた版（【Phase 1で確認】の結果）

| 項目 | 版 | メモ |
| --- | --- | --- |
| Minecraft | 1.20.1 | — |
| Forge | 47.4.10 | 公開情報で確かめられた47.4系の版。遊ぶ人は 47.4.10 以上の47.4系を入れる |
| ビルド | ForgeGradle 6（Gradle 8.8）、公式のマッピング | 1.20.1のMDKが10月まで使っていた構成。11月8日にMDKはForgeGradle 7へ移ったが、まだ新しいため実績のある6を使う |
| Java | 17 | — |
| GeckoLib・Curios API・playerAnimator | Phase 1では入れない | 使い始めるPhase（2・3）で、その日の最新版を確かめて `gradle.properties` に固定する |

## 2. 追加したファイルと役割

| ファイル | 役割 |
| --- | --- |
| `build.gradle`・`settings.gradle`・`gradle.properties` | ビルドの設定。開発用の起動（runClient・runClient2・runServer）もここ |
| `gradlew`・`gradlew.bat`・`gradle/wrapper/` | Gradleを自動で用意する仕組み |
| `.github/workflows/build.yml` | GitHub Actionsの自動ビルド。jarを「Artifacts」に保存する |
| `src/main/resources/META-INF/mods.toml`・`pack.mcmeta` | Modの情報（Modの一覧に出る説明は日本語） |
| `AmemaruMod.java` | Modの入り口。設定ファイルと通信を登録する |
| `core/config/ServerConfig.java` | サーバーの設定（成長・HPとMPの回復・ワールド） |
| `core/config/ClientConfig.java` | 見た目の設定（HUDの大きさ・数字の表示） |
| `core/data/DefinitionLoader.java` | データパックの定義（`data/*/amemaru/<種類>/*.json`）を読む土台。/reload に対応 |
| `core/data/Json.java`・`DefinitionException.java` | 定義の検査。問題は日本語で「どこを・どう直すか」を出す |
| `core/data/DataReport.java` | 読み込みの問題を管理者に知らせる（/reload の後とログイン時） |
| `core/world/GameRuleSetup.java` | 起動時にゲームルールを合わせる |
| `core/ServerEvents.java` | 読み込み処理の登録、起動時の準備、/reload の後の計算し直し |
| `player/StatType.java` | ステータスの種類（11種。振り分けられるのは7種） |
| `player/StatBlock.java` | 計算済みのステータスの組 |
| `player/GrowthDef.java`・`GrowthRules.java` | 成長の定義（レベルの上限・経験値の式・ポイント・基礎値と伸び） |
| `player/StatCalculator.java`・`CollectStatsEvent.java` | ステータスの計算。装備や効果は `CollectStatsEvent` で上乗せする（Phase 2以降） |
| `player/PlayerData.java`・`PlayerDataProvider.java`・`PlayerDataCapability.java` | プレイヤーデータ（Capability）の中身・保存・取り出し |
| `player/LevelService.java` | 経験値・レベル・振り分け |
| `player/VitalsService.java` | 独自のHPとMP、自然回復、満腹度の固定 |
| `player/PlayerSync.java` | 本人への同期（同じtickの変更は1回にまとめる） |
| `player/PlayerEvents.java`・`PlayerModEvents.java` | データの付与・引き継ぎ・ログインとリスポーン・毎tickの処理 |
| `admin/AdminCommands.java` | 管理者のコマンド |
| `network/ModNetwork.java` | 通信のチャンネル `amemaru:main` |
| `network/s2c/SyncPlayerDataS2C.java` | プレイヤーデータの同期（変わったまとまりだけ送る） |
| `network/c2s/AllocateStatC2S.java` | ステータスの振り分けの要求 |
| `client/AmemaruHud.java` | 基本HUD |
| `client/screen/StatusScreen.java` | ステータス画面 |
| `client/ClientPlayerState.java`・`ClientPacketHandler.java` | 受け取ったデータの写し |
| `client/KeyBindings.java`・`ClientModEvents.java`・`ClientForgeEvents.java` | キー、HUDの登録、バニラの表示を隠す |
| `assets/amemaru/lang/ja_jp.json`・`en_us.json` | 画面の文字（英語の設定の人にも日本語が出るよう、両方に同じ日本語） |
| `data/amemaru/amemaru/growth/default.json` | 成長の数値。データパックで上書きできる |

後回しにした部分はありません。Phase 1の範囲の外にあるもの（ダウン、職業、敵など）は、5章に書いたPhaseで作ります。

## 3. できること

### HPとMP（Phase 0-A 19章の方式B）

- 独自のHPとMPで計算し、バニラのハートは隠して、ホットバーの上にHPとMPのバーを出します。
- 落下・溺れ・溶岩などでバニラの体力が減ると、減った割合（最大の体力に対する割合）を独自のHPから引きます。回復薬で増えたときも同じ割合で増えます。
- 自然回復は独自に行います（下の数値）。満腹度は減りません。
- HPが0になると、Phase 1ではバニラの死亡になります（Phase 4でダウン→蘇生→教会に置き換え）。

### レベルとステータス

- 次のレベルまでの経験値は「120 × 1.09 の（今のレベル）乗」です（Lv1→2は131、Lv10→11は284、Lv49→50は8,186。Lv50までの合計は97,687）。
- レベルが上がるごとにステータスポイント3、スキルポイント1。5の倍数のレベルではスキルポイントがさらに1。
- ステータス＝基礎＋レベルの伸び＋振り分け（＋Phase 2以降の装備と効果）。
- レベルが上がると、HPとMPが全快し、チャットに知らせが出ます。

### HUD（ホットバーの上）

```
[ HP 560/560 ]  10  [ MP 190/190 ]
[============ 経験値 ============]
[          ホットバー          ]
```

- 真ん中の数字がレベルです。振り分けていないポイントがあると黄色になります。
- 隠すバニラの表示：ハート、防御、満腹度、経験値のバー（空気の泡はHUDの上に出ます）。
- `config/amemaru-client.toml` で、大きさ（0.5〜2.0）と数字の表示を変えられます。

### ステータス画面（Kキー）

- レベル・経験値・スキルポイント・7つのステータスと、補助の能力4つを見られます。
- 「＋」で1ポイント、Shiftを押しながらで10ポイント振り分けます。もう一度Kか、Escで閉じます。
- キーは「設定」→「操作設定」→「AMEMARU: LOST CONNECTION」で変えられます。

### 管理者のコマンド（OP・権限レベル2以上）

| コマンド | すること |
| --- | --- |
| `/amemaru admin level set <プレイヤー> <レベル>` | レベルを指定する（経験値は0に戻る） |
| `/amemaru admin level add <プレイヤー> <数>` | レベルを上げる |
| `/amemaru admin level exp <プレイヤー> <量>` | 経験値を与える（configの倍率はかけない） |
| `/amemaru admin level resetstats <プレイヤー>` | 振り分けを戻す |
| `/amemaru admin level info <プレイヤー>` | レベル・ポイント・HPとMP・ステータスを見る |
| `/amemaru admin debug vitals <プレイヤー> <HP%> <MP%>` | HPとMPを最大値の割合で入れる（確かめ用） |

実行した人と内容は、サーバーのログに残ります。

## 4. ゲーム内での動作確認の手順

jarの受け取り方と入れ方は [README](../README.md) にあります。ワールドは、チートを許可して新しく作ってください。

1. **HUD**：ハートと満腹度の代わりに、赤いHPと青いMPのバー、真ん中にレベル「1」、その下に経験値のバーが出る。HPは `200/200`、MPは `100/100`。
2. **レベル**：`/amemaru admin level set @s 10` を実行する。
   - チャットに「レベルが10に上がりました。ステータスポイント +27、スキルポイント +11（Kキーでステータス画面）」が出て、音が鳴る。
   - HPは `560/560`、MPは `190/190` になり、レベルの数字が黄色になる。
3. **ステータス画面**：Kを押す。
   - 「レベル 10」「経験値 0 / 284」「スキルポイント 11」「残りポイント 27」と、攻撃 28・魔力 28・防御 14・素早さ 10・会心 5.0%・会心ダメージ 50.0% が出る。
   - HPの「＋」を押すと、HPが572になり（今のHPも同じだけ増える）、「（+1）」と「残りポイント 26」に変わる。HUDも572になる。
   - Shiftを押しながら攻撃の「＋」を押すと、攻撃が38、「（+10）」になる。
4. **経験値**：`/amemaru admin level exp @s 1000` を実行する。Lv10から3つ上がってLv13になり、ステータス画面は「経験値 68 / 368」。
5. **ダメージ**：サバイバルにして（`/gamemode survival`）、10ブロックほどの高さから落ちる。バニラの落下ダメージの割合だけ、HPのバーが減る（例：ハート3.5個分＝35%）。
6. **自然回復**：攻撃を受けずに8秒たつと、HPが1秒に最大の2%ずつ戻る。`/amemaru admin debug vitals @s 50 10` で、HPが半分、MPが1割になり、MPは1秒に3%ずつ戻る。
7. **保存**：ワールドを出て入り直す。レベル・振り分け・HPとMPがそのまま残っている。
8. **死亡**：`/kill` で死んで復活すると、HPとMPは全快、レベルと振り分けはそのまま。
9. **確認と戻し**：`/amemaru admin level info @s` で値を確かめる。`/amemaru admin level resetstats @s` で振り分けが戻る。
10. **ゲームルール**：`/gamerule naturalRegeneration` が false、`/gamerule keepInventory` が true になっている。
11. **LAN**：「LANに公開」して、もう一人が同じjarで入る。それぞれのHUDに自分の値が出て、片方のレベルを変えても、もう片方は変わらない。

### データパックで数値を変える（任意）

`data/amemaru/amemaru/growth/default.json` と同じ場所に置いたデータパックで、成長の数値を上書きできます。ワールドの `datapacks` フォルダに入れて `/reload` すると、全員のステータスが計算し直されます。わざと数値をおかしくすると（例：`"max_level": 0`）、管理者のチャットに「データパックの読み込みで、1件の問題がありました」と、直す場所が出ます。

## 5. 既知の問題と、次のPhaseでやること

| 内容 | いつ |
| --- | --- |
| HPが0になるとバニラの死亡になる。ダウン→蘇生待ち→教会での復活とデスペナルティに置き換える | Phase 4 |
| 経験値は管理者のコマンドでしか手に入らない。敵から得る経験値と、追いつき補正（中央値より5・10以上低い人に+50%・+100%）を作る | Phase 3 |
| 最大レベルでは経験値が捨てられる。「結晶の欠片」がたまるようにする | Phase 3 |
| パーティでの経験値の分配 | Phase 4 |
| 章ごとのレベルの上限（15・28・40・50）。進行（章）の仕組みと一緒に作る | Phase 5 |
| 攻撃・魔力・防御・素早さ・会心は、まだ戦闘に使われない。素早さの移動速度・回避も含めて、戦闘と一緒に効くようにする | Phase 2・3 |
| ステータスのリセットアイテム「忘れ水」（今は管理者のコマンドだけ） | Phase 6 |
| 一般プレイヤーをアドベンチャーモードにする・ブロックを守る。マップと一緒に作る | Phase 5 |
| スキルのキー（R・G・V・Z・X・C）。Vはボイスチャットのキーと重なることがあるため、Phase 2で入れて確かめる | Phase 2 |
| GeckoLibのモデルの書き出し（Blockbench 4系と旧プラグイン）の確認。Phase 0-B 12章の手順を固める | Phase 3（GeckoLibを入れるとき） |

## 6. 補った数値【補完】

Phase 0-Aに数値がなかったところを、次のとおり補いました。どれもデータパックかconfigで変えられ、Phase 9でまとめて調整します。

| 項目 | 数値 | 置き場所 |
| --- | --- | --- |
| Lv1のステータス | HP 200、MP 100、攻撃 10、魔力 10、防御 5、素早さ 10、会心 5%、会心ダメージ 50% | `growth/default.json` の `base` |
| レベルごとの伸び | HP +40、MP +10、攻撃 +2、魔力 +2、防御 +1 | `per_level` |
| 1ポイントごとの伸び | HP +12、MP +5、攻撃 +1、魔力 +1、防御 +1、素早さ +1、会心 +0.3% | `per_point` |
| HPの自然回復 | 戦闘中 0%／秒、戦闘外 2%／秒 | `amemaru-server.toml` の `vitals` |
| MPの自然回復 | 戦闘中 1%／秒、戦闘外 3%／秒 | 同上 |
| 戦闘外になるまで | 最後に攻撃した・受けたときから8秒 | 同上 |
| レベルアップ時 | HPとMPを全快 | `growth.restoreOnLevelUp` |
| ステータス画面のキー | K | 操作設定 |

Lv50で振り分けなしなら、HP 2,160、MP 590、攻撃 108、魔力 108、防御 54です。振り分け（合計147ポイント）と装備を足して、Phase 0-Aの例「HP 3,000」前後になる想定です。

起動時に合わせるゲームルール（`world.applyGameRules` をオフにすると変えません）：自然回復・敵の自然発生・襲撃隊・行商人・ファントム・火の広がり・モブによる破壊をオフ、死亡で持ち物を失わない（keepInventory）をオン。

## 7. 世界観・物語の追加

ありません（Phase 1は仕組みだけです）。

## 8. 差し替えてほしい素材

ありません（Phase 1にはモデルとテクスチャがありません）。HUDとステータス画面は、色の四角と文字だけで描いています。見た目を変えたいときは、Phase 3以降でテクスチャを使う形に直せます。
