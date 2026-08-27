# walkthrough14 — sqlite-vec再有効化 + UI/安定性12コミットの棚卸し

**日付:** 2026-08-28
**対象:** walkthrough13以降の未記録12コミット + `BundledSQLiteDriver.withSqliteVec()` 再有効化の検証待ち解消

---

## 1. 背景

walkthrough13で `sqlite-vec` のDB側近傍検索(`vec_distance_cosine`)は準備できたが、実機で

* `PerfSeedReceiver` の `Toast` が `Dispatchers.IO` で `Looper.prepare()` 未呼出クラッシュ
* `transactionExecutor = newSingleThreadExecutor()` で `withTransaction` がデッドロック

の2原因で `BundledSQLiteDriver().withSqliteVec()` を一旦無効化し、InMemory skip(50k 9.8s→4ms, Pss 553M→208M)だけでOOMを解消した。両原因は個別に修正済み(前者は `Handler(Looper.getMainLooper()).post{}`, 後者は `newFixedThreadPool(2)` — コミット `c999e75`, `2e204e7`)だが、再有効化コミット `68a8006` はメッセージ「re」のみで実機検証・記録がなく、さらにその間にUI/安定性/AGP周りで12コミット分のwalkthroughが欠落していた。本walkthroughで棚卸しし、AGENTS.mdの「作業のまとまりごとにwalkthroughを書く」を回復する。

## 2. 今回までに積まれた12コミット(530ff96..68a8006)

| コミット | 内容 |
|---|---|
| `0019200` | AGP9ベストプラクティス移行: `builtInKotlin/newDsl` フラグ撤去, `kotlin.android` プラグイン除去 |
| `b94afa4` | `EmbeddingDao` スタブに `count/vecSearchRaw` 追加 — PERF-8のDAO拡張にテスト追随 |
| `f67df15` | DemoData拡充: 白板2件・Wiki2件・リンク4件 (歴史/CSボード、比較記事) |
| `278d2bb` | 白板ドラッグ/px-dp変換/クリック遷移/ダイアログ検証を修正 — まともに動く白板に |
| `7ee609d` | Whiteboard ViewModelをRepo経由に修正、重なり回避のランダム配置、`touchBoard` で一覧更新を正しく |
| `fdc695f` | Connection InputChipのネストクリック解消(`associateBy`化)、強度表示追加 |
| `6227512` | Wiki起動クラッシュ修正: `RichContentView` のWebView/CDN/エスケープ安定化、WikiArticle null/リンク解決を適切に |
| `63ca141` | Whiteboard見た目クリーン化: グリッド背景、ElevatedCard、タイトル/件数、削除ボタン、空状態 |
| `340bbff` | 接続ダイアログ改善 + Dashboard統計導線: 検索プレビュー/強度ラベル/候補リスト |
| `68a8006` | `DatabaseModule` に `BundledSQLiteDriver().withSqliteVec()` を再有効化(3行追加) |

`f484fb1`/`ae1dee4`/`435a12f` の `AutoMirrored` 移行もこの期間に含まれる。

**評価:** いずれも筋の良い修正。ただしコミットメッセージが文字化け混じりで、まとめて出すとレビュー不能になる — 今後は1機能1コミット1walkthroughを厳守する。

## 3. 今回の実装

### a. sqlite-vec再有効化

`di/DatabaseModule.kt:21,27,41`

```kotlin
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.hubbla.roomvec.withSqliteVec
...
.setDriver(BundledSQLiteDriver().withSqliteVec())
```

`gradle/libs.versions.toml` の `sqlite-bundled 2.5.2` + `room-vec-common 0.1.0-alpha01` はwalkthrough13で導入済み。`EmbeddingDao.vecSearch()` (`vec_distance_cosine`) と `HybridSearchEngine` のDB優先→InMemoryフォールバック分岐はそのまま活きる。

### b. 既に直っている2原因の確認

* Toast Looper: `PerfSeedReceiver` は全Toastを `Handler(Looper.getMainLooper()).post{}` 経由に修正済み
* デッドロック: `DatabaseModule.transactionExecutor = newFixedThreadPool(2)` でWAL並行書き込みを活かす

このため再有効化は理にかなうが、**実機で50k seedがクラッシュしないこと・`vec_distance_cosine` が非空を返すことの2点をログで確認するまでは「直った」と言わない** — 次のセッションの検証項目とする。

```powershell
adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 50000
adb logcat -s SyntheticSeeder App PerfSearch -v time
# Geminiキー設定状態で検索1件実行し、空でないことを確認
```

## 4. ビルド確認

* `./gradlew assembleDebug` — BUILD SUCCESSFUL (本walkthrough作成前に2回確認)
* `./gradlew :app:testDebugUnitTest --rerun-tasks` — 99 tests passed, 0 failures

実機50k seed / semantic検索の非空確認は次セッションで実施し、結果を `docs/perf/BASELINE.md` に追補する。

## 5. 残タスク

* **最優先:** 実機で `vec_distance_cosine` の本番運用確認(Gemini設定時のsemantic検索が非空)。確認できたら本walkthroughに追記するか `walkthrough14補遺` を残す
* FTS膨張(DB 624M)対策 — `search_document_fts` のVACUUM/FTS5検討
* `largeHeap` 要否の再評価(skip後は不要の可能性)
* UI改良は見える部分から1画面ずつチェックリスト化して進める(次セッションで §11 画面一覧を土台に)
* 初期データ拡充(高校古典/数学/英語 + 地歴/法/経済のガチ学問)はカリキュラム設計を先行し、DB投入はsqlite-vec検証完了後に段階的に(1万件超を見込むため)

## 6. 教訓

* コミットメッセージは「re」でなく「何をどう検証したか」を書く
* 12コミットまとめてはレビュー不能 — 1機能1walkthroughを守る
* 「良さそうに見える」は前回の `RichContentView` パラメータ不一致と同じ罠。ログで確認するまで信用しない
