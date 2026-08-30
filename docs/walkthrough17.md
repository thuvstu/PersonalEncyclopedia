# walkthrough17 — DB-1 SQL Explorerプレビュー + WB-1 ホワイトボード パン/ズーム

**日付:** 2026-08-30
**コミット:** 6451b72
**ビルド:** assembleDebug BUILD SUCCESSFUL (2m36s)

## 1. DB-1: SQL Explorer テーブルタップでプレビュー

* 対象: `ui/screen/SqlExplorerScreen.kt:193`
* 変更: スキーマブラウザの `.clickable { viewModel.selectTable(obj.name) }` を拡張
  ```kotlin
  .clickable {
      viewModel.selectTable(obj.name)
      val previewSql = "SELECT * FROM ${obj.name} LIMIT 100;"
      queryText = previewSql
      viewModel.runQuery(previewSql)
  }
  ```
* 効果: テーブル名タップでスキーマカラム表示 + クエリ欄に `SELECT * LIMIT 100` を反映 + その場で実行し結果カードに最大100行表示
* 安全性: `obj.name` は `sqlite_master` 由来の実在名のみ、`runQuery()` は `ReadOnlySqlExecutor` (SELECT/WITH + PRAGMA query_only) の二重防御を経由
* 既知の残課題: 結果カードがスキーマより上にあるためタップ後に上スクロールが必要。気になる場合は自動スクロールを別タスクで追加

## 2. WB-1: ホワイトボード パン/ズーム

* 対象: `ui/screen/WhiteboardScreen.kt:1-294`
* 変更:
  - import追加: `detectTransformGestures`, `graphicsLayer`
  - 状態追加: `var scale by mutableFloatStateOf(1f)`, `var canvasOffset by mutableStateOf(Offset.Zero)`
  - 外層Boxに `pointerInput { detectTransformGestures {_,pan,zoom,_ -> scale=(scale*zoom).coerceIn(0.3f,3f); canvasOffset+=pan } }`
  - 内層Boxに `graphicsLayer(scaleX/Y=scale, translationX/Y=canvasOffset)` で Canvas+セクション+ノードを包む
  - ノード個別の `detectDragGestures` は維持（内側=ノード、外側=キャンバスで競合回避を試みる設計）
* 実機で要確認:
  - 空白ドラッグ → キャンバスがパンする
  - ノード上ドラッグ → ノードのみ移動（キャンバスはパンしない）
  - ピンチ → ズーム (0.3x〜3x)
  - ズーム後のタップ/ドラッグずれ（graphicsLayerは描画のみでpointer座標を自動補正しないため、ずれがあれば次回 dragAmount/scale 補正を追加）

## 3. 検証

* `./gradlew assembleDebug` 成功
* 原則「1セッション1機能」に対し今回はユーザ明示指示により2機能同時適用。エラー波及なし

## 4. 次の一手

* 実機でのジェスチャー感触確認 → 奪い合い/ずれがあればフォローアップ
* DB-1 自動スクロール要否の判断
* DESIGN.md §11.6 / §11.12 の記述を実装に合わせて更新
