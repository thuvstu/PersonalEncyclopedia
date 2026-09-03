# walkthrough21 — ホワイトボード ピンチ中心基準ズーム

**日付:** 2026-09-04
**コミット:** c2d9fbe
**ビルド:** assembleDebug BUILD SUCCESSFUL

## 1. 背景

* walkthrough20 までのズームは原点基準だったため、拡大すると見ていた場所が画面外へ逃げ、パンで探し直す必要があった。実用上ストレスになるため、指の下の内容点が固定される方式に変える。

## 2. 変更 (1ファイル)

* 対象: `ui/screen/WhiteboardScreen.kt` (外層キャンバスの `detectTransformGestures`)
  - これまで無視していた `centroid` を採用
  - `newScale = (oldScale * zoom).coerceIn(0.3f, 3f)` のうえ、実際に適用された変化率 `factor = newScale / oldScale` で平行移動を補正:
    `canvasOffset = (canvasOffset - centroid) * factor + centroid + pan`
  - `coerceIn` で頭打ち (0.3x/3x到達時) しても `factor` が実変化率なので内容点がずれない
  - 純粋なパン時 (`zoom=1`) は `factor=1` で従来式と完全一致するため挙動不変

## 3. 検証

* `./gradlew assembleDebug` 成功。`git diff --stat` は当該1ファイルのみ (8 insertions, 3 deletions)。
* 実機での指追従感触 (2本指で狙ったノードを拡大し、そのノードが指の下に残ること) は次回実機セッションで確認する。

## 4. 次の一手 (1機能ずつ、別コミット)

* 空白ドラッグとノードドラッグの奪い合いの分離 (残っていれば)
* ノードにタイトル表示 (現状はID先頭8文字のみで中身が分からない)
* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
