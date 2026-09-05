# walkthrough26 — MigrationTest v10対応 (#D1)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL。実機実行は未 (エミュレータなしのため)
**根拠:** DESIGN §13 D1・§15 #D1

## 1. 背景

DBはv10なのにMigrationTestはv1→v9まで。`MIGRATION_9_10` (PERF-2・`index_progress_events_entityId`)
が未検証で、`10.json` はあるのに未使用だった。`app/schemas/` の3/4/5.json欠落は、
Roomが現行スキーマのみexportするためビルドでは復元不能。よって中間単段の復活は諦め、
v1→v10フルチェーン＋v9→v10単段で代替する方針。

## 2. 変更 (1ファイル)

* `androidTest/.../db/MigrationTest.kt`:
  - `allMigrations` に `MIGRATION_9_10` を追加。
  - 旧 `migrate1To9_...` → `migrate1To10_fullChainPreservesData` に改名し、
    `runMigrationsAndValidate(testDb, 10, ...)` + `index_progress_events_entityId` 存在 assertion を追加。
  - 新 `migrate9To10_addsProgressEventsEntityIdIndex` (v9作成→データ投入→v9→v10→保持＋索引確認)。
  - KDocに schemas 3/4/5欠落の制約を明記。

## 3. 検証

* コンパイル成功。実機 `connectedAndroidTest` は次回実機セッションで実行すること。
* 3/4/5.jsonの再生成は別途、旧版コードのチェックアウト＋ビルドが必要 (本セッションの範囲外)。

## 4. 次の一手

* P2-B: Wiki記事本文への自動リンク配線。
