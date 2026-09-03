# walkthrough22 — ホワイトボード 空白/ノードのジェスチャ分離

**日付:** 2026-09-04
**コミット:** c71dfd1
**ビルド:** assembleDebug BUILD SUCCESSFUL (3回コンパイルエラー→解消の過程あり、下記)

## 1. 背景

* ノード上ドラッグでキャンバスも一緒にパンする「奪い合い」が残っていた。
  原因は親の `detectTransformGestures` が子より先にイベントを受け、子の `detectDragGestures` と同時に発火すること。

## 2. 変更 (1ファイル)

* 対象: `ui/screen/WhiteboardScreen.kt` (外層キャンバスのみ。ノード側は walkthrough20 のまま無変更)
  - `detectTransformGestures` をやめ、`awaitEachGesture` + `awaitFirstDown` による手動ループに変更
  - タッチ開始点を内容座標に戻し (`(down.position - canvasOffset) / scale`)、ノード矩形内なら親は何も消費せず指が離れるまで待機のみ → 子のドラッグだけが生きる
  - 空白開始のときだけパン/ズームを処理 (touchSlop 判定 + ピンチ中心基準ズームは walkthrough21 の式を移植、回転は従来通り無視)
  - `pointerInput(Unit)` のまま `rememberUpdatedState(nodes)` で最新ノード配置を参照 (キー変更によるジェスチャ中断を回避)

## 3. 検証と教訓

* `./gradlew assembleDebug` 成功。差分は当該1ファイルのみ。
* 過程で `positionChanged()` / `positionChange()` / `positionChangeIgnoreConsumed()` と3連敗した。
  いずれも記憶違いで、この環境の Compose UI 1.12.0 には存在しないか非公開だった。
  **推測でAPIを書かず、確認できた公開API (`consume()` / `isConsumed`) だけで書く** 方針に切り替え、
  slop超過後は無条件 `consume()` として解決。AGENTS.md 原則2「既存コードが唯一の正」の実践例。
* 実機での奪い合い解消の体感確認は次回実機セッションで行う。

## 4. 次の一手 (1機能ずつ、別コミット)

* ノードにタイトル表示 (現状はID先頭8文字のみ。要 ViewModel 側の表示名解決=別ファイルのため独立タスク)
* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
