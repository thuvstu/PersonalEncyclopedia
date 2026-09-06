# walkthrough47 — 実機起動クラッシュ修正 (BundledSQLiteDriver + Migration形式不整合)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `./gradlew assembleDebug testDebugUnitTest` 成功。実機 (25053PC47G, onyx_global) に `adb install -r` → `am start -W` で起動確認。

## 1. 背景

前回ビルドは通っていたが、実機で起動直後にプロセス死亡を確認。

- `adb shell pidof` が空、`dumpsys` に活動なし
- `logcat` に `FATAL EXCEPTION: DefaultDispatcher-worker-2`
- 原因: `kotlin.NotImplementedError: Migration functionality with a provided SQLiteDriver requires overriding the migrate(SQLiteConnection) function.`
- `DatabaseModule` が `setDriver(BundledSQLiteDriver().withSqliteVec())` しているのに、MIGRATION_1_2〜10_11 が旧 `migrate(SupportSQLiteDatabase)` のままだった
- `PersonalEncyclopediaApp.initDatabase()` は `runStep` で `Exception` しか捕捉しないため、`Error` である `NotImplementedError` がプロセスを殺していた

## 2. 変更 (11ファイル・1機能)

* `db/Migration1to2.kt` 〜 `db/Migration10to11.kt` (10件)
  - `override fun migrate(db: SupportSQLiteDatabase)` → `override fun migrate(connection: SQLiteConnection)`
  - `db.execSQL` → `connection.execSQL` (`androidx.sqlite.execSQL`)
  - `Migration6to7.insertEra` のみバインド引数ありのため `prepare` + `bindText/bindLong/bindNull` + `step()` + `use` へ変換
* `di/DatabaseModule.kt`
  - `RoomDatabase.Callback.onOpen(db: SupportSQLiteDatabase)` → `onOpen(connection: SQLiteConnection)`
  - `PRAGMA synchronous = NORMAL` を新APIで継続

既存コードが正という原則に従い、SQL文自体は一切変更なし。API形式のみ変換。

## 3. 効果

* 実機で `TotalTime: 819ms / WaitTime: 824ms` で `MainActivity` が `topResumedActivity`・`visible=true` に到達
* `ps -A` でプロセス生存、`FATAL`・`NotImplementedError` ゼロ
* `InMemoryVectorIndex.load: 3ms` のみで `Phase初期化失敗` なし
* `compileDebugKotlin`・`testDebugUnitTest` 成功

## 4. 検証

* `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL
* `adb install -r app-debug.apk` → Success
* `adb shell am start -W` → Status: ok, Complete
* 8秒後に `ps` でプロセス生存、`logcat -d | Select-String FATAL,NotImplementedError` で0件
* `dumpsys activity` で `topResumedActivity=MainActivity` を確認

## 5. 残課題 (次セッション以降・本セッションでは触らない)

* `BackupExporter` / `BackupWorker` の `openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")` 3箇所
* `ReadOnlySqlExecutor` の `openHelper.readable/writableDatabase` 5箇所
* いずれも `setDriver` 後に呼ぶと例外になる旧API。起動経路ではないため今回は未修正。SQL Explorer・バックアップ操作時に実機で要検証。
* `MigrationTest` (androidTest) は計装テストのため未実行。次回エミュレータ/実機で `connectedAndroidTest` を通すこと。
