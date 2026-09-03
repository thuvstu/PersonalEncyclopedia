# walkthrough23 — ホワイトボード ノードにタイトル表示

**日付:** 2026-09-04
**コミット:** ba6d82f (ViewModel) + c5e8a39 (Screen)
**ビルド:** 各コミット前に assembleDebug/compileDebugKotlin BUILD SUCCESSFUL

## 1. 背景

* ノードカードは `entryId/noteId` の先頭8文字しか表示せず、中身が分からない状態だった。
  幸い解決基盤は既存だった (`NodeWithContent.displayTitle` + `WhiteboardRepository.resolveNodes`) ため、
  未配線の表示層だけを繋ぐ。

## 2. 変更 (2ファイル・各1コミットで緑を維持)

* `viewmodel/WhiteboardViewModel.kt` (+8行):
  `resolvedTitles: StateFlow<Map<String, String>>` を追加。
  `observeNodes` → `mapLatest { repo.resolveNodes }` → ノードID→表示タイトルへ変換。
  geometry/drag用の `nodes` フローには触れていない。
* `ui/screen/WhiteboardScreen.kt` (+2/-1行):
  `resolvedTitles` を collect し、カード本文を `take(8)` から `titles[node.id] ?: "…"` に置換。
  種別ラベル (📝メモ/📄エントリー) は維持。

## 3. 検証

* 2コミットとも単独でビルド成功 (中間状態でも `nodes` 型不変のため壊れない手順を選んだ)。
* 削除済みエントリー参照時は `displayTitle` が「（無題）」にフォールバックする (DAOで `deletedAt IS NULL` 除外のため)。
* 実機での見た目確認は次回実機セッションで行う。

## 4. 次の一手

* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
* FTS 膨張 (DB 624M) 対策の調査
