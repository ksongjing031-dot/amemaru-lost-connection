# AMEMARU: LOST CONNECTION

Discordサーバー「あめまるさん」のための、Minecraft Java版 1.20.1（Forge）のRPG Modです。

- 設計：Phase 0-A（世界観とゲームデザイン）、Phase 0-B（技術設計）
- 作り方：Phaseごとにブランチ（`phase-1` など）で作り、プルリクエストで確認していただいてから `main` に入れます。
- 各Phaseの内容と確認の手順は [`docs/`](docs/) にあります（Phase 1は [`docs/phase1.md`](docs/phase1.md)）。

## 動かすのに必要なもの

| もの | 版 |
| --- | --- |
| Minecraft Java版 | 1.20.1 |
| Forge | 47.4系の最新（47.4.29 で確かめています） |
| Java | 17（Minecraftのランチャーが用意するもので足ります） |

## Modのファイル（jar）の受け取り方

GitHubの自動ビルド（GitHub Actions）が、コードを上げるたびにModを作ります。

1. このリポジトリの「Actions」タブを開く
2. 一覧から、緑のチェックが付いた実行を開く（プルリクエストの確認なら、そのブランチ名のもの）
3. 下の「Artifacts」にある `amemaru-mod-<番号>` をダウンロードする
4. ダウンロードしたzipを開き、中の `amemaru-1.20.1-<版>.jar` を取り出す

## 遊び方（LANでの確認）

1. Minecraftのランチャーに Forge 1.20.1 を入れる（Forgeの公式サイトで 1.20.1 の「Latest」のインストーラーを使い、「Install client」）
2. 取り出した jar を、Minecraftのフォルダの `mods` に入れる（Windowsなら `%APPDATA%\.minecraft\mods`）
3. 起動の構成で「forge」を選んで遊ぶ
4. 一緒に確かめる人も、同じ版の Forge と jar を入れる。ワールドを開いた人が「LANに公開」し、ほかの人はマルチプレイの一覧から入る

## 開発用の起動（任意）

コードを直接動かして確かめたいときの方法です。JDK 17 が入っていなくても、初回のビルドで自動で用意されます。

```
gradlew build          … Modの jar を build/libs に作る
gradlew runClient      … 開発用のクライアント（名前は Dev）
gradlew runServer      … 開発用のサーバー（初回は run/eula.txt の eula を true にする）
gradlew runClient2     … 1台のPCで2人目を試すクライアント（名前は Tester2）
```

開発用のサーバーに2つのクライアントで入るときは、`run/server.properties` の `online-mode` を `false` にしてから、両方のクライアントで「マルチプレイ」→ `localhost` に接続します。

## フォルダの構成

```
src/main/java/jp/amemaru/lostconnection/
├─ AmemaruMod.java   Modの入り口
├─ core/             設定・データパックの読み込み・ワールドの設定
├─ player/           プレイヤーデータ・ステータス・レベル・HPとMP
├─ admin/            管理者のコマンド
├─ network/          通信（c2s：クライアント→サーバー、s2c：サーバー→クライアント）
└─ client/           HUD・画面・キー（クライアント専用）
src/main/resources/
├─ assets/amemaru/lang/            画面の文字（日本語）
└─ data/amemaru/amemaru/growth/    成長の定義（データパックで上書きできる）
```
