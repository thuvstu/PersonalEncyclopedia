# walkthrough20 — ホワイトボード ズーム時のノードドラッグ補正

**日付:** 2026-09-04
**コミット:** 5a804d6
**ビルド:** assembleDebug BUILD SUCCESSFUL

## 1. 背景

* walkthrough17 の WB-1 残課題: パン/ズーム導入後、ズーム状態でノードをドラッグすると指の移動とノードの移動がずれる。
  原因は `graphicsLayer(scale)` が描画のみ拡大し、pointer 座標を自動補正しないこと。指の移動量(画面px)を内容座標にそのまま加算していたため、ズームイン時は scale 倍に飛んでいた。

## 2. 変更 (1ファイル)

* 対象: `ui/screen/WhiteboardScreen.kt` (ノードの `detectDragGestures`)
  - `onDrag` を `dragOffset += dragAmount` → `dragOffset += dragAmount / scale` に修正
  - `pointerInput(node.id)` → `pointerInput(node.id, scale)` にし、倍率変更時にジェスチャ検出器を再登録 (古い倍率のクロージャ掴みを防止)
* 触っていないもの: 外層キャンバスのパン (`translationX/Y` は画面px基準のため補正不要)、ピンチ中心補正 (別タスク)

## 3. 検証

* `./gradlew assembleDebug` 成功。`git diff --stat` は当該1ファイルのみ (5 insertions, 2 deletions)。
* 実機での指追従感触 (等倍/2倍/0.5倍でのドラッグ) は次回実機セッションで確認する。

## 4. 次の一手 (1機能ずつ、別コミット)

* ピンチ中心基準のズーム (現在は原点基準) — 要実機感触確認つき
* 空白ドラッグとノードドラッグの奪い合いが残っていれば `requireUnconsumed` 等で分離
* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
