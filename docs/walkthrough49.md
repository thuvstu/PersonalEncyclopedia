# walkthrough49 — アプリらしく・シームレスに (wt47残課題 + リッチ描画 + 共有導線 + ショートカット)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスに JDK / Android SDK が無いため `assembleDebug` 未実行。構文・既存シグネチャは実ファイルを読んで確認。
**⚠ 次回実機セッションで `./gradlew assembleDebug testDebugUnitTest` を最初に通し、下記「検証」を実機確認すること。**

優先順どおり 4 項目。骨格（Android=データ本体、Drive API不使用、承認制接続）は無改変。

## 1. 【最優先・信頼性】wt47 残課題 — バックアップと SQL Explorer

wt47 で `setDriver(BundledSQLiteDriver)` にした後、`openHelper.writableDatabase` は Room が例外を投げる旧 API。残っていた 8 箇所を置換。

* `BackupExporter.kt` ×2 / `BackupWorker.kt` ×1
  - `database.useWriterConnection { it.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }`
* `ReadOnlySqlExecutor.kt` ×5
  - `useReaderConnection` + `usePrepared` / `step`
  - ユーザー SQL は従来どおり SELECT/WITH ゲート + 書込キーワード拒否
  - reader 接続で `PRAGMA query_only = ON`（プーリングされた reader を書き込み可能に戻さないよう OFF にしない）
* JVM テスト `ReadOnlySqlExecutorTest` 6件（Room 不要の `denyReason`）

`openHelper.writable/readableDatabase` はソースから 0 件。

## 2. 【体感】リッチ描画をオフライン・ダーク・全高対応に

wt46 の自動リンクがオフラインで `<a href="wiki://%E7%A6%8F…">福澤諭吉</a>` と生表示されていた（CDN の marked.js が読めず `innerText` フォールバック）。加えて `color:#222` 固定と `heightIn(max=400.dp)` でダークモードの白い箱・長文切断・ネストスクロール。

* `assets/marked.min.js` に marked v11.1.1 を同梱（約 35KB、MIT。KaTeX は CDN 継続）
* `loadDataWithBaseURL("file:///android_asset/", …)` でローカルスクリプトを読む
* MaterialTheme の色を CSS 変数で注入。WebView の algorithmic darkening は OFF
* フォールバックを `innerHTML` に変更（wiki-link/ruby は既に HTML）
* `evaluateJavascript` で `scrollHeight` を取り、カード内は内容高に合わせる
* `EntryDetailScreen` / `EntryTypeSections` の `heightIn(max=…)` を撤去
* Wiki 全画面は `autoHeight=false`（親がサイズを持つ）
* ついで: `EntryTypeSection` の definition 分岐がプレーン Text 版に食われ RichContentView 版が死んでいたので一本化

## 3. 【取り込み導線】共有・テキスト選択から一発保存

* Manifest: `ACTION_SEND` に `image/*`・`application/pdf`、`ACTION_SEND_MULTIPLE`、`ACTION_PROCESS_TEXT`（ラベル「百科事典に保存」）
* テキスト = 既存の URL スクレイプ or メモ
* 画像 = `filesDir/blobs/media/` へコピー + `EntryRepository.createMedia`
* PDF = wt43 の `ImportPipeline.importDocumentFile`
* Toast 連発を Snackbar「保存しました」+ アクション「開く」に
* `IncomingNavigation` に notice / route キューを追加（Activity→Compose の既存橋渡しを拡張）

## 4. 【小さくて効く】アプリショートカット・予測型戻る

* `res/xml/shortcuts.xml`: アイコン長押し → 新規メモ / 検索 / 今日の復習
* `android:enableOnBackInvokedCallback="true"`（予測型戻る）

## 検証（次回実機）

1. SQL Explorer で `SELECT 1` とテーブルタップが例外なく結果を返す。`DELETE` は拒否。
2. 設定からバックアップ実行、または充電+Wi-Fi で BackupWorker が FAILED にならない。
3. 機内モードでエントリー詳細: 自動リンクが青いリンクとして描画され、タップでプレビュー。ダークモードで本文が白箱にならない。長文が 400dp で切れずページ全体がスクロールする。
4. ブラウザから URL 共有 / ギャラリーから画像共有 / PDF 共有 → Snackbar「保存しました」→「開く」。他アプリで文字選択→「百科事典に保存」。
5. ランチャーでアイコン長押し → 新規メモ / 検索 / 今日の復習。

## 残

* KaTeX フォントは CDN のまま。オフラインの数式は `$...$` のまま残る。
* 共有画像の OCR は未接続（`createMedia` の ocrText は null）。
* JDK/SDK 無しのため本セッションでは Gradle 未実行。
