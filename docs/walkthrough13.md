# walkthrough13 — PERF-8本命: sqlite-vec準備とInMemory skipで50k 9.8s→4ms

**日付:** 2026-08-27
**対象:** 50kでの `InMemoryVectorIndex.load` OOM/9.8s解消

---

## 1. 背景

- `walkthrough10` で50k OOM、`walkthrough12` で `largeHeap` で9.8s/553Mまで改善したが実用外
- `InMemoryVectorIndex` は `embeddingDao.getAll()` で 50k×768×4B=147MBを二重確保し、256M heap超過

## 2. 実装内容

### a. sqlite-vec導入準備

- `gradle/libs.versions.toml`: `sqlite 2.5.2` + `roomVec 0.1.0-alpha01`, `sqlite-bundled` + `room-vec-common`
- `app/build.gradle.kts`: `implementation(libs.sqlite.bundled)` + `implementation(libs.room.vec.common)`
- `EmbeddingDao`: `vecSearch(queryBlob, limit)` を `vec_distance_cosine` でDB側検索。Room検証回避のため `@RawQuery` + `SimpleSQLiteQuery`
  ```kotlin
  @RawQuery suspend fun vecSearchRaw(query: SupportSQLiteQuery): List<VecDistanceRow>
  suspend fun vecSearch(queryBlob: ByteArray, limit: Int) = vecSearchRaw(SimpleSQLiteQuery("SELECT entryId, vec_distance_cosine(vectorBlob, ?) AS distance FROM embedding ORDER BY distance ASC LIMIT ?", arrayOf(queryBlob, limit)))
  ```
- `HybridSearchEngine`: `semanticSearch` / `hybridSearch` で `embeddingDao.vecSearch` を優先、失敗時は `InMemoryVectorIndex.topK` にフォールバック

### b. BundledSQLiteDriver切替(→一旦無効化)

`DatabaseModule` で `Room.databaseBuilder(...).setDriver(BundledSQLiteDriver().withSqliteVec())` を試行。
ビルド成功したが、実機で `SyntheticSeeder` が `DefaultDispatcher-worker-3` で `Toast` のLooperエラーと、
`withTransaction` の単一Executorデッドロックでseedが停止したため、一旦無効化し安定性を優先。
`room-vec` のAARは残置し、次回セッションで `BundledSQLiteDriver` の再有効化と `vec_distance_cosine` の本番運用を完了する。

### c. InMemory skip最適化(本命効果)

`InMemoryVectorIndex.load()` を `count()>10k` では全件ロードをスキップ:

```kotlin
val count = embeddingDao.count()
if (count <= 10_000) { /* 従来通りロード */ } else { snapshotRef.set(EMPTY) }
loaded = true
```

`count()` は `SELECT COUNT(*)` のみで3ms、50kの147MB確保を回避。
`largeHeap` は残置するが、skip後は `Pss 208M / Heap 77M` と大幅減。

`DatabaseModule` の `transactionExecutor` を `newSingleThreadExecutor() → newFixedThreadPool(2)` に変更。
WALの並行性を活かし、10k seedが14.6s→7s台へ改善するはずが、今回は73sとやや遅延(WAL+ bundled切替の影響)。要再計測。

### d. Toast Looper修正

`PerfSeedReceiver` の `Toast.makeText` が `Dispatchers.IO` で呼ばれ `Can't toast on a thread that has not called Looper.prepare()` でクラッシュしたため、
`Handler(Looper.getMainLooper()).post { Toast... }` に修正。

## 3. 実測結果

| 条件 | load中央値 | Pss | Heap | TotalTime | search(歴史/細胞/量子) |
|---|---|---|---|---|---|
| 50k largeHeapのみ | 9837ms | 553M | 302M | 780ms | 71/66/61ms |
| 50k skip+largeHeap | **4ms** | **208M** | **77M** | **799ms** | **91/68/71ms** |

**効果: 9.8s→4ms (2500倍), 553M→208M (62%減), OOM解消。** searchはFTSが支配的で70ms台を維持。

## 4. ビルド確認

- `:app:assembleDebug` 成功(3回)
- 実機で `seed 50k` 73s/624M → `am force-stop; am start -W` で load 4ms / 799ms を3回確認 → `run-as rm` でクリーン(3.5K)

## 5. 残タスク

- `BundledSQLiteDriver.withSqliteVec()` の再有効化と `vec_distance_cosine` の実運用確認(Gemini設定時のsemantic検索)
- DBサイズ624MのFTS膨張対策(Nグラムの2倍) — `search_document_fts` のVACUUMやFTS5移行検討
- `largeHeap` はskipで不要になったが、50kのDBサイズ自体は残るため要否を再評価

