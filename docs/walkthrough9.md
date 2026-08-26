# walkthrough9 — Round 0完了: timed()計測ラッパー + :benchmarkモジュール撤去

**日付:** 2026-08-26
**対象:** NextTasks.md §0「撤去・変更するもの」の完了と、軽量計測用コードの導入

---

## A. timed()計測ラッパー追加

### 実装内容

- **`util/Timed.kt`(新規)**: `inline fun <T> timed(tag, label, block)` — blockの所要時間を
  `AppLogger.d()` 経由でlogcatに出力する数行のラッパー。§0に書かれた設計そのまま
- **`PersonalEncyclopediaApp.kt`**: `initBrainLayer()` の `vectorIndex.load()` を `timed("App", "InMemoryVectorIndex.load") { ... }` で包んだ
- **`SearchRepository.kt`**: `hybridSearch.search(...)` を `timed("App", "hybridSearch") { ... }` で包んだ

### 躓いた点

- 初版は `if (BuildConfig.DEBUG)` ガードを入れていたが、本プロジェクトはAGP 9で
  buildConfig機能が無効(`android.defaults.buildfeatures.buildconfig`廃止)のため
  `Unresolved reference 'BuildConfig'` でビルド失敗。ガードを外して解消
  (NextTasks.md §0の元コードにもガードは無い。計測ログ1行のオーバーヘッドは無視できる)

### ビルド確認

- `:app:assembleDebug` 成功 → コミット `217d964`

## B. :benchmarkモジュール撤去

NextTasks.md §0の方針(Macrobenchmark放棄→軽量計測置換)の残タスクを実施。

### 変更内容

| ファイル | 変更 |
|---|---|
| `settings.gradle.kts` | `include(":benchmark")` を削除 |
| `build.gradle.kts`(root) | `libs.plugins.android.test` のalias行を削除 |
| `gradle/libs.versions.toml` | `benchmark = "1.4.1"` バージョン / `androidx-benchmark-macro-junit4` ライブラリ / `android-test` プラグインを削除 |
| `benchmark/` | ディレクトリごと削除(build.gradle.kts + Benchmarkテスト4種) |
| `.gitignore` | `benchmark/build` 行を削除 |
| `app/build.gradle.kts` | M-2で追加したreleaseへのdebug署名は**残存**(コメント更新)。R8有効release APKを実機インストールし軽量計測に使えるため |
| `AndroidManifest.xml` | `<profileable>` は**残存**(コメント更新)。simpleperf等の手動プロファイリングに有用なため |

### 残した理由(R8/profileable)

NextTasks.md §0に「R8修正自体はrelease buildの健全化として単独で価値がある」と明記済み。
debug署名・profileableも同じ理屈(計測インフラとしてRound 4以降で有用)で保持。

### ビルド確認

- `:app:assembleDebug` 成功(benchmark参照除去後も設定エラーなし) → コミット `71ca470`

## C. ドキュメント更新

- `DESIGN.md`: §14.2を「軽量計測ツールキット」に書き換え、付録Aディレクトリマップから`benchmark/`除去・`util/ Timed`追記
- `docs/guide/00-overview.md`: パフォーマンス計測節を軽量計測方式に書き換え
- `docs/NextTasks.md`: §0の撤去チェックリスト4項目を✅に更新
- `docs/perf/BASELINE.md`: 前回セッションで軽量版に書き換え済み(変更なし)

## D. 検証状況

- [x] debugビルド成功
- [ ] **M-3実測は未実施**(adb接続端末なしのため次セッションへ)。BASELINE.md §2〜4の手順に従い、
  端末接続後に1,000→10,000→50,000の段階計測を行うこと

## E. 次の一手

1. **M-3**: 端末接続 → SEED投入 → `am start -W` / `gfxinfo` / logcat(timed) / DBサイズを記録 → BASELINE.md記入
2. Round 1(PERF-1 WAL明示化 / PERF-2 progress_eventsインデックス)へ着手
