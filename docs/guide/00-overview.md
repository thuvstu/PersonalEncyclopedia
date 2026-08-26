# 00-overview.md — 全体アーキテクチャ

このアプリは「学んだことを1か所に貯めて、検索・つなげて・反復練習する」ための個人用アプリ。
データの本体は Android 端末内の Room DB にだけ存在する(設計書 §1.2「データ主権を Android に置く」)。

## 何をどう保存するか

- **entry(統一型+CTI)**…1つの「知識のかたまり」。種類(型)だけ違う13型があり、共通カラム(タイトル・本文・サマリーなど)を `entry` テーブルが持ち、型ごとの追加情報は別テーブルが持つ(§01-entry-model.md 参照)。
- **quiz_bank**…クイズの問題。
- **srs_review**…単語帳の復習履歴。**履歴だけ**を貯める設計で、アルゴリズムは後から差し替えられる(§04-quiz-and-srs.md 参照)。
- **search_document**…検索専用の結合テキスト(FTS4 用)。entry 本体とは別に持つ。
- **connection**…entry 同士の関連。承認された正式な「つながり」。

## 何をどう検索するか

検索は1本の経路ではなく、3つの結果を1つに合算する(§02-search.md 参照)。

1. **全文検索**: 日本語を2文字ずつのバイグラムに分割して SQLite FTS4 で MATCH。
2. **ベクトル検索**: Gemini(または Ollama)で文を768次元ベクトルに変換し、全 entry とのコサイン類似度を総当たり計算。
3. **RRF合算**: 両者の順位を Reciprocal Rank Fusion で統合し、作成から7日/30日以内のものには微増のボーナスを足す。

`HybridSearchEngine.search()` がこの全部をやっている(`brain/search/HybridSearchEngine.kt`)。

## 起動時の流れ(§3.4)

`PersonalEncyclopediaApp.kt` の `runStep` が3段階で起動する。

1. `initDatabase` … Room DB 初期化。
2. `initBrainLayer` … ベクトル索引のロード、埋め込みワーカーの起動、未完了ジョブの復旧。
3. `scheduleBackgroundWorkers` … バックグラウンド処理(接続候補生成など)の定期実行。

どの段階で失敗してもアプリが落ちないよう、各段階は独立してトライされ、失敗はログに残る。

## パフォーマンス計測(Round 0で追加)

「直す前に測る」ための仕組みが2つある(詳細は `DESIGN.md` §14 / `docs/perf/BASELINE.md`)。

- **合成データ投入(debugビルド限定)**: `app/src/debug/` の `SyntheticDataSeeder` が、実データ規模
  (最大50,000 entry)の疑似データを entry・検索ドキュメント・FTS・768次元embedding込みで作る。
  adb broadcast 1発で投入・全削除ができる。release APKには含まれない。
- **軽量計測(2026-08-24改訂)**: Macrobenchmarkは複雑すぎるため撤去。代わりに
  `adb shell am start -W`(コールドスタート)、`dumpsys gfxinfo`(スクロールのジャンク)、
  `util/Timed.kt` の `timed()` ラッパー+logcat(個別処理時間)で計測する。
  最適化の前後で必ずこの数字を取り、悪化したら理由を残す。手順は `docs/perf/BASELINE.md`。

## ネットワーク

- アプリ内に **Ktor サーバー**(`server/LocalServer.kt`)を内蔵しており、明示的に ON にしたときだけ LAN 上に API を公開する(§4.3)。
- PC の **React Web クライアント**(`web/`)は DB を持たず、この Ktor API を叩くだけ(§13)。「PC=閲覧・操作端末、Android=データ本体」の関係を崩さないため。
- 認証は端末内生成トークンを Authorization Bearer で送る簡易方式(§4.3)。

## ディレクトリ対応

| 層 | 場所 | 責務 |
|---|---|---|
| データ層 | `db/` | Room Entity・DAO・マイグレーション |
| サーバー層 | `server/` | Ktor ルーティング(薄く保つ、§10.1) |
| 思考層 | `brain/` | 検索・接続・採点・LLM・SRS |
| 画面層 | `ui/` + `viewmodel/` | Compose 画面と状態 |
| 取り込み | `importer/` | Webスクレイプ・ファイル・URLリストの import、重複検出(§12.7) |
| 入力プラグイン | `plugins/` | Rhino で動くユーザー拡張 |

- 参考: 設計書 §3(アーキテクチャ)、§10(Ktor)、§11(画面)、§12(取り込み)
