# AMEMARU: LOST CONNECTION

Discordサーバー「あめまるさん」のための、Minecraft Java版 1.20.1（Forge）のRPG Modです。

- 設計：Phase 0-A（世界観とゲームデザイン）、Phase 0-B（技術設計）
- 作り方：Phaseごとにブランチ（`phase-1` など）で作り、プルリクエストで確認していただいてから `main` に入れます。
- 各Phaseの内容と確認の手順は [`docs/`](docs/) にあります（[Phase 1](docs/phase1.md)・[Phase 2](docs/phase2.md)・[Phase 3](docs/phase3.md)・[Phase 4](docs/phase4.md)・[Phase 5](docs/phase5.md)・[Phase 6](docs/phase6.md)・[Phase 7 その1](docs/phase7_connection.md)）。建物の様式は [`docs/map_style.md`](docs/map_style.md)、装備のデザインは [`docs/gear/`](docs/gear/README.md)。
- 今までの全部をゲームの中で見て回る道具（確かめ用の平らなワールドと、街の11施設をまとめて建てるコマンド）は [`docs/ingame_check.md`](docs/ingame_check.md)。

## 動かすのに必要なもの

| もの | 版 |
| --- | --- |
| Minecraft Java版 | 1.20.1 |
| Forge | 47.4系の最新（47.4.29 で確かめています） |
| Java | 17（Minecraftのランチャーが用意するもので足ります） |
| playerAnimator（Forge 1.20.1 版） | 1.0.2-rc1+1.20 以上。**クライアント（遊ぶ人のPC）にだけ必要**。サーバーには入れなくてよい |
| GeckoLib（Forge 1.20.1 版） | 4.8 以上（4.8.4 で確かめています）。**クライアントとサーバーの両方に必要**（敵・防具・動く武器の見た目） |
| Curios API（Forge 1.20.1 版） | 5.14 以上（5.14.1+1.20.1 で確かめています）。**クライアントとサーバーの両方に必要**（アクセサリーの枠） |

## Modのファイル（jar）の受け取り方

GitHubの自動ビルド（GitHub Actions）が、コードを上げるたびにModを作ります。

1. このリポジトリの「Actions」タブを開く
2. 一覧から、緑のチェックが付いた実行を開く（プルリクエストの確認なら、そのブランチ名のもの）
3. 下の「Artifacts」にある `amemaru-mod-<番号>` をダウンロードする
4. ダウンロードしたzipを開き、中の4つの jar を取り出す
   - `amemaru-1.20.1-<版>.jar` … このMod（クライアントとサーバーの両方に入れる）
   - `geckolib-forge-1.20.1-<版>.jar` … GeckoLib（クライアントとサーバーの両方に入れる）
   - `curios-forge-<版>.jar` … Curios API（クライアントとサーバーの両方に入れる）
   - `player-animation-lib-forge-<版>.jar` … playerAnimator（クライアントにだけ入れる）

同じ場所の `screenshots-<番号>` には、自動の確認で撮ったHUD・ステータス画面・スキル・敵・装備・工房などの写真が入っています。

## 遊び方（LANでの確認）

1. Minecraftのランチャーに Forge 1.20.1 を入れる（Forgeの公式サイトで 1.20.1 の「Latest」のインストーラーを使い、「Install client」）
2. 取り出した4つの jar を、Minecraftのフォルダの `mods` に入れる（Windowsなら `%APPDATA%\.minecraft\mods`）
3. 起動の構成で「forge」を選んで遊ぶ
4. 一緒に確かめる人も、同じ版の Forge と4つの jar を入れる。ワールドを開いた人が「LANに公開」し、ほかの人はマルチプレイの一覧から入る
5. 専用サーバー（AGAMES など）には、このMod・GeckoLib・Curios の3つの jar を入れる（playerAnimator は要らない）

## 開発用の起動（任意）

コードを直接動かして確かめたいときの方法です。JDK 17 が入っていなくても、初回のビルドで自動で用意されます。

```
gradlew build          … Modの jar を build/libs に作る
gradlew dist           … mods に置く jar（このMod ＋ playerAnimator ＋ GeckoLib ＋ Curios）を build/dist にそろえる
gradlew runClient      … 開発用のクライアント（名前は Dev）
gradlew runServer      … 開発用のサーバー（初回は run/eula.txt の eula を true にする）
gradlew runClient2     … 1台のPCで2人目を試すクライアント（名前は Tester2）
```

開発用のサーバーに2つのクライアントで入るときは、`run/server.properties` の `online-mode` を `false` にしてから、両方のクライアントで「マルチプレイ」→ `localhost` に接続します。

## フォルダの構成

```
src/main/java/jp/amemaru/lostconnection/
├─ AmemaruMod.java   Modの入り口
├─ core/             設定・データパックの読み込み・登録（ブロック・アイテム・効果音など）
├─ player/           プレイヤーデータ・ステータス・レベル・HPとMP
├─ job/              職業・スキル・スキルツリー・専用ゲージ・パッシブ・受け流し（job/def は定義の読み込み）
├─ combat/           ダメージの計算と受け渡し・連携・挑発・敵に重なる状態（凍え・凍結・感電・星痕・防御崩し）
├─ particle/         演出用パーティクルの値
├─ progression/      進行日
├─ block/ item/ entity/ effect/   誓約の結晶・見習い装備・訓練人形・状態
├─ admin/            管理者のコマンド
├─ network/          通信（c2s：クライアント→サーバー、s2c：サーバー→クライアント）
├─ gametest/         自動テスト
└─ client/           HUD・画面・キー・演出・モーション（クライアント専用。client/fx は演出の形・パーティクル・状態の見た目）
src/main/resources/
├─ assets/amemaru/                 画面の文字（日本語）・絵・モデル・効果音・モーション・演出のテクスチャ
└─ data/amemaru/amemaru/           成長・職業・スキルの定義（データパックで上書きできる）
tools/                             仮の絵・演出のテクスチャ・モーションを作るスクリプト（Modには入らない）
```
