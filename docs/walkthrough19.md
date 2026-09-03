# walkthrough19 — SQL Explorer テーブルタップ後の自動スクロール

**日付:** 2026-09-04
**コミット:** ec24aba
**ビルド:** assembleDebug BUILD SUCCESSFUL

## 1. 背景

* walkthrough17 の DB-1 残課題: テーブルタップでプレビュー実行はできるが、結果カードがスキーマより上にあるため手動で上スクロールが必要だった。

## 2. 変更 (1ファイル)

* 対象: `ui/screen/SqlExplorerScreen.kt`
  - `import kotlinx.coroutines.launch` を追加
  - 画面の `verticalScroll(rememberScrollState())` を名前付き `listScroll` に hoist + `rememberCoroutineScope()` を追加
  - テーブルタップの `.clickable {}` 末尾に `scope.launch { listScroll.animateScrollTo(0) }` を追加
* 効果: テーブル名タップ → スキーマ表示 + クエリ反映 + 実行 + 先頭(クエリ欄・結果カード)へアニメーションスクロールまで一連で完結
* 安全性: スクロールは UI 装飾のみで `ReadOnlySqlExecutor` の防御に触れない。`animateScrollTo` はスコープキャンセルで安全失敗する

## 3. 検証

* `./gradlew assembleDebug` 成功。`git diff --stat` は当該1ファイルのみ (7 insertions, 1 deletion)。
* 実機でのスクロール感触は次回実機セッションで確認する。

## 4. 次の一手 (1機能ずつ、別コミット)

* WB-1 残課題: ズーム後のタップ/ドラッグずれ (`dragAmount / scale` 補正) — `WhiteboardScreen.kt` のみ
* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
