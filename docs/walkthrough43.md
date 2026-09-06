# walkthrough43 — PDF/DOCX の取込とアプリ内PDF閲覧 (mismatch §1.3)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` 未実行。kotlinc 単体で構文確認のみ。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin` を最初に通し、PDF取込→「PDFを開く」を実機確認すること。**
**根拠:** mismatch §1.3「PDF表示なし・抽出のみ。`ImportPipeline` は `DocumentExtractor` を呼ばない」、§7.5 データ流入導線の細さ

## 1. 背景

document 型は手入力フォームでしか作れず、pdfbox のテキスト抽出器(`DocumentExtractor`)はどこからも呼ばれていなかった。
取り込んだ文書の実体を端末に持つ経路も、PDFを画面で見る手段も無かった。

## 2. 変更 (7ファイル)

* `importer/ImportPipeline.kt` `importDocumentFile(uri, nameHint)` 新設:
  1. `filesDir/blobs/documents/<entryId>/<name>` へコピー(端末内保管。Drive API不使用＝骨格維持)
  2. `DocumentExtractor.extractText` で本文抽出(pdfbox / docx解凍)。失敗しても登録は続行(`extractionMethod="none"`)
  3. §12.7 重複判定(タイトル＋抽出本文) → entry(content=抽出先頭500字)＋`entry_document`(blobPath/size/pageCount/extractedText) → `EmbeddingQueue.enqueue`
  - `importSafFolder` のフォルダ一括も `.pdf/.docx` を振り分けるようにした。
  - `DocumentExtractor` を注入(`@ApplicationContext` を付与。Hilt が素の `Context` を解決できないため)。
* `importer/DocumentExtractor.kt`: `pageCount(file)` 追加。
* 新規 `ui/component/PdfViewerDialog.kt`: OS標準 `android.graphics.pdf.PdfRenderer` で1ページずつ描画する全画面ダイアログ。
  ページ送り／ピンチズーム(1〜4倍)／ドラッグ。1ページ分しかメモリに持たない(画面幅×1.5、上限2048px)。外部ライブラリ不要。
* `ui/component/EntryTypeSections.kt` `DocumentSection`: 実体があり pdf なら「📄 PDFを開く」ボタン、docx は保管表示、
  実体無し(別端末から取込んだJSON)は警告表示。
* `viewmodel/ImportViewModel.kt` `importDocument(uri)`、`ui/screen/ImportScreen.kt` に「📄 PDF / DOCX」行。

## 3. 設計判断

* **端末内保管**: JSON往復(wt41)では `blobPath` を書かない方針のまま。文書実体はバックアップ対象外(50GB構想は別途)。
  別端末で復元した document は抽出テキストだけ残り、ビューアは「実体なし」を明示する。
* `entry.content` に抽出先頭500字を入れるのはカード/一覧の視認性のため。全文は `extractedText`(検索文書は先頭1500字)。
* ビューアは Compose の `Image` に `graphicsLayer` でズームする最小構成。ページ単位描画なので数百ページでも耐える。

## 4. 検証

* kotlinc 構文チェック: 新規/変更ファイルに新しい構造エラー無し(`EntryTypeSections.kt:277 p1` は変更前から出る分類解決アーティファクト)。
* 実機で確認: インポート→PDF選択→トースト→詳細画面で「PDFを開く」→ページ送り・ズーム。同じPDFを再取込でスキップ。
  検索でPDF本文の語がヒットすること。
* 3条件チェック: ビルド ☐ / テスト ☐ / 3日実使用 ☐

## 5. 残
* DOCX の本文表示(抽出テキストのみ)、xlsx/pptx、PDFのテキスト選択・検索語ハイライト、文書ブロブのバックアップ。
