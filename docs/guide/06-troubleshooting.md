# 06-troubleshooting.md — よくある不具合と、対応するテストへのリンク

「どの不具合を再現/防止するテストがどれか」を一覧化している(設計書 §14.1)。
将来同じ系統の不具合が起きたら、この表から該当テストを開いて確認・拡張すること。

## 過去に発生し、テストで防いでいる不具合

| 不具合 | 原因 | 防止するテスト |
|---|---|---|
| ベクトル索引を読み書きするスレッドが競合し、検索結果が壊れたりクラッシュする(GAP-4 D1) | インメモリ索引を可変リストとして直接共有していた | `InMemoryVectorIndexConcurrencyTest`: 同時 topK/addVector/removeVector がクラッシュせず件数が不整合にならないことを検証 |
| 和暦を西暦に換算できず誤答になる、未知の元号を「間違い」扱いしてしまう(GAP-5 E2/E3) | 元号変換をハードコード配列で持ち、未知元号と誤答を区別していなかった | `MultiStageGraderTest`: `1600年 equals 慶長5年`・`unknown era is marked undeterminable not incorrect` など |
| 大量機能の一括実装でコンパイルエラー連鎖を起こし、git 撤回に至った(v12.0) | 実装原則を破り複数機能を同時に導入した | (テストではなくプロセス) §2.5「1機能1ビルド1コミット」。エラーは3ファイル基準で収まらなければ回避 |

## 現在のコードで観測しうる挙動(既知仕様)

| 症状 | 原因と対処 |
|---|---|
| 検索結果が突然0件になる | FTS4 の MATCH 構文エラーは `HybridSearchEngine` が例外を握りつぶして空を返す仕様(§7.2)。`adb logcat` に検索系の例外が無いか確認する。クエリに `"` や `*` などの特殊文字が混ざると起きやすい |
| 埋め込みが一部の entry で終わらない | `embedding_job` の status を見る。`failed` なら attempts 上限(3回)到達、`running` のままなら前回クラッシュ。起動時の `recoverJobs()` が再投入するので通常は放置で復旧する |
| Gemini を設定したのにベクトル検索が効かない | `isConfigured()` が false、または `InMemoryVectorIndex.isLoaded()` が false(起動の initBrainLayer が失敗)。API キーと起動ログを確認 |
| Ollama 埋め込みを混ぜると検索精度が落ちる | Gemini と Ollama のベクトルは次元数・意味空間が異なるため直接比較してはいけない(§7.1.1)。`embedding.model` カラムで区別し、同一モデル同士でのみ類似度を計算する |
| アプリ更新後に復習間隔が変わる | SM-2→FSRS のアルゴリズム切替は `repetitionCount` を保持したまま行える設計(§5.8.5)。切替直後は間隔の出発点が変わるため、予定が動くのは正常 |
| 自動接続候補が大量に出る/まったく出ない | `AUTO_CONNECT_ENABLED` と `AUTO_CONNECT_THRESHOLD`(既定 0.88)の設定を確認。既定 OFF が仕様(§8.4、03-connection.md) |
| Web クライアントが「API error 401」 | トークン不一致。Android 側に表示されているアクセストークンを Web の接続設定に入力する(§4.3) |
| 取り込み時に重複がスキップされる | 重複検出(`DuplicateDetector`、§12.7)の仕様。skip 数は import 結果に表示される |

## テストの実行方法

```sh
# アプリ側の単体テスト(JVM、エミュレータ不要)
./gradlew :app:testDebugUnitTest

# アプリのビルド確認
./gradlew :app:assembleDebug

# Web クライアントのビルド確認(bun)
cd web && bun run build
```

> androidTest(計器テスト)はエミュレータが必要なため、この環境ではコンパイル確認(`:app:compileDebugAndroidTestKotlin`)まで。
