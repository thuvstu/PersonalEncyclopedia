# walkthrough54 — ビルド復旧 (wt49の不足import追加・実機未接続)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `./gradlew assembleDebug testDebugUnitTest` 成功。実機は未接続 (`adb devices` 空) のためインストール・起動確認は未実施。

## 1. 背景

wt49 (openHelper→Driver API置換) 以降、HEADがコンパイル不通過だった。
`useReaderConnection` / `useWriterConnection` は Room 2.8.4 の `androidx.room` トップレベルsuspend拡張
(`RoomDatabaseKt` に実在することをjavapで確認) だが、3ファイルとも `import` が無く未解決参照になっていた。
`usePrepared`・`it`・suspend系の残り30件超のエラーはすべてこの連鎖だった。

## 2. 変更 (3ファイル・importのみ)

* `db/ReadOnlySqlExecutor.kt`: `import androidx.room.useReaderConnection` 追加
* `backup/BackupExporter.kt`: `import androidx.room.useWriterConnection` 追加
* `backup/BackupWorker.kt`: `import androidx.room.useWriterConnection` 追加

SQL文・ロジックの変更なし。`androidx.room.execSQL` (TransactorKt) のimportは既存で正しかった。

## 3. 効果

* `:app:compileDebugKotlin` → BUILD SUCCESSFUL
* `assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL (APK生成済み)

## 4. 検証

* 実機が `offline`→切断のため `adb install` 不可。再接続後に以下を通すこと:
  `adb install -r app/build/outputs/apk/debug/app-debug.apk` →
  `adb shell am start -W -n com.thuvstu.personalencyclopedia/.MainActivity` →
  8秒後に `ps` 生存 + `logcat -d | Select-String FATAL` 0件
* 初回起動はwt50/51の自動シード (InitialData+2+Math+形式クイズ) が走るため、
  起動直後の重さ・ANRの有無を実機で見ること

## 5. 残課題

* 実機での起動確認は次回接続時に必ず実施 (本セッションではPCビルド確認まで)
