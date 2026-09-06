# walkthrough41 — JSON export/import の完全往復 (mismatch §6-2)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` 未実行。
`EntryJsonCodec` と `EntryJsonCodecTest` は kotlinc 2.4.10＋スタブで**JVM実行し7件全PASS**を確認。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin testDebugUnitTest` を最初に通すこと。**
**根拠:** mismatch §3.2・§6-2・§7.5-1（v15 §14「Room＋可搬exportの二重保証」の自己矛盾）

## 1. 背景

Exporterは6型の拡張しか書かず、Importerはそのうち definition しか読まず、
タグ・お気に入り・時刻・旧ID・lang・metadataJson を全捨てしていた。
画面の「エクスポートと対称」表示は誤り。機種変で知識グラフが痩せる構造だった。

## 2. 変更 (5ファイル)

* `backup/EntryExporter.kt` (+約120行): 本体に `lang/metadataJson/accessedAt` を追加。
  拡張を **thought＋11型（definition/webpage/book/video/document/media/person/org/place/event/liked/ai_conv）の全カラム**に拡大。
  `EntryThoughtDao` を注入。端末固有パス（thumbnailPath/coverPath/blobPath(document)/photoPath/gdriveId）は書かない（media.blobPath のみ復元判定に使うため書く）。
* 新規 `importer/EntryJsonCodec.kt` (約250行): JSON1要素→`EntryEntity`＋拡張＋タグ名の純粋デコーダ。
  - `keepId=true` で旧ID保持、時刻・お気に入り・mute・lang・metadataJson を復元。
  - 旧exporter形式（キー欠落）はデフォルトで埋める後方互換。型不一致は null 扱いで1件の破損が全体を止めない。
  - `{"entries":[...]}` 形式も受理（`importEntriesJson` 側）。
* `importer/ImportPipeline.kt` (`importEntriesJson` 全面書換＋`insertDecoded`):
  - 同一IDが既にあれば**スキップ**（同じデータの再取込。上書きしない）。ID無しの旧形式は §12.7 の内容重複判定。
  - entry→拡張→タグ（`TagDao.getByName`→無ければ作成→`linkTag`）→`EmbeddingQueue.enqueue`（検索文書を即時更新）。
  - `TagDao`・`EmbeddingQueue` を注入（EmbeddingQueue→ImportPipeline の逆依存は無く循環しない）。
* 新規 `test/.../importer/EntryJsonCodecTest.kt` (7件): 往復保持・新ID採番・旧形式互換・11型復元・
  webpageのsourceUrlフォールバック・media blob無し非復元・破損値の寛容。
* `ui/screen/ImportScreen.kt`: 文言を「完全対称。同IDはスキップ」に訂正。

## 3. 検証

* JVM: `EntryJsonCodecTest` 7/7 PASS（スタブ実行。Gradle上でも同じテストが動く）。
* 実機で確認すること: DB管理→JSONエクスポート→（別端末 or 削除後）→取込→
  タグ・お気に入り・作成日時・各型の拡張が一致すること。2回目の取込は全件スキップになること。
* 3条件チェック（v15 §14）: ビルド ☐ / テスト ☐(JVMスタブは済) / 3日実使用 ☐

## 4. 次の一手

* 検索のソート切替＋条件フィルタ（mismatch §3.4）。
