# walkthrough12 — Round1 DB最適化 + largeHeap暫定対応

**日付:** 2026-08-27 00:00〜
**対象:** PERF-2(progress_events索引) / PERF-1(WAL/Executor) / PERF-8暫定(largeHeap)

---

## 1. PERF-2: progress_events.entityId 索引追加(v9→v10)

- `ProgressEventEntity`: `indices` に `Index("entityId")` を追加(既存の entityType/eventType/createdAt に加え4つ目)
- `Migration9to10`: `CREATE INDEX index_progress_events_entityId ON progress_events(entityId)`
- `AppDatabase` version 9→10, `DatabaseModule` に `MIGRATION_9_10` を追加
- ビルド成功、実機で起動確認(マイグレーションは次回起動時に適用)

効果: 無制限ログの「特定entryの履歴」クエリが全件スキャン→索引スキャンに。10k/50kでの劣化防止。

## 2. PERF-1: WAL明示化 + synchronous=NORMAL + Executor分離

`DatabaseModule.provideDatabase`:

```kotlin
.setJournalMode(WRITE_AHEAD_LOGGING)
.setQueryExecutor(Executors.newFixedThreadPool(4))
.setTransactionExecutor(Executors.newSingleThreadExecutor())
.addCallback(onOpen { execSQL("PRAGMA synchronous = NORMAL") })
```

- WAL: 読み書き競合で4倍改善の報告あり(高スペックでは効果大)
- synchronous=NORMAL: WALではfsync頻度を下げても安全
- Executor分離: 高スペック(SM8735/11GB RAM)に合わせ query 4本 / transaction 1本。低スペック配慮は後回し(NextTasks方針通り)
- 実機で `encyclopedia.db-wal` が生成されることを確認、起動成功

## 3. PERF-8暫定: largeHeap=true

50kで `InMemoryVectorIndex.load` が `target footprint 256M` でOOMしたため、暫定で `AndroidManifest` に
`android:largeHeap="true"` を追加。heap上限512Mで50kでも起動可能になった。

**再実測(50k, largeHeapあり):**
- load: 9531 / 9646 / 9837 / 9962 ms (中央値 9.8s) — OOMは解消したが9.8sは実用外
- search: 71 / 66 / 61 ms — FTSは50kでも高速
- Pss: 553M, Heap Size 302M / Alloc 188M
- TotalTime: 767ms前後で変わらず(ロードはバックグラウンド)

**結論:** largeHeapでクラッシュは回避したが、本命の `sqlite-vec` 移行が依然最優先。
9.8sのブロッキングロードをon-disk化で解消する必要がある。

## 4. 変更ファイル

- `db/entity/ProgressEventEntity.kt`, `db/Migration9to10.kt`, `db/AppDatabase.kt`, `di/DatabaseModule.kt` (PERF-2/1)
- `AndroidManifest.xml` (largeHeap)
- `docs/perf/BASELINE.md` — 50k欄をlargeHeap有り/無しで更新、スクロール表にタブ切替結果追記、ウィンドウ切替セクション追加

## 5. ビルド確認

- 各変更後に `:app:assembleDebug` 成功(3回)
- 実機で `installDebug` → `am start`/ `du -h` / `logcat -s App/Nav` / `dumpsys meminfo` で確認
- 50k再投入(63s, 623M) → cold start 9.8s load確認 → `run-as rm` でクリーン(3.5K)に戻す

## 6. 次の一手

- **Round5本命:** `sqlite-vec` 拡張の導入検討(ネイティブlib, Room拡張ポイント §15)。`InMemoryVectorIndex` の全件ロードを廃止
- **Round2:** PERF-4(Lazy key) / PERF-5(巨大Screen分割) / Strong Skipping — ウィンドウ切替の体感は `fade 120/90ms` が支配的だが、再構成コストも削減
- **Round3:** PERF-7(Coil) — サムネイル原寸デコードの解消

