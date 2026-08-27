# walkthrough15 — 初期データ体系拡充 + UI見える部分3点

**日付:** 2026-08-28
**対象:** 初期データ135件化と検索/Dashboard/詳細の視認性向上

## 1. 初期データ (InitialData.kt)

* **135定義** (古典20/数学20/英語20/地歴25/法30/経済20) + 思考6 + クイズ30 + Wiki6 + 接続20
* 定義は【定義→体系的位置→例】の3層で記述し、prerequisite/related/contrast接続でカリキュラムを編む
* Wiki6がハブ (古典文法/数学ハブ/英語5文型/日本史ストーリー/法学マップ/経済コア) に [[リンク]] で相互接続
* `seedIfEmpty` は空DB初回のみ投入。sqlite-vec検証完了後に有効化予定

## 2. UI改良

| 画面 | 変更 |
|---|---|
| Search | TopAppBar埋込のTextFieldを本文へ移動、extraLarge形状、ヒット件数表示 |
| Dashboard | 統計カードをCard→ElevatedCard化、絵文字付ラベルで視認性向上 |
| EntryDetail | 削除を即時 `softDelete()` から確認ダイアログ化で誤操作防止 |

## 3. ビルド

* `assembleDebug` BUILD SUCCESSFUL (3回)
* 既存 `testDebugUnitTest 99 passed` は前回確認済み

## 4. 残タスク

* 全定義の3層化を残件へ展開、クイズ/接続の網羅性チェック
* 白板/Wiki/クイズ画面の見える部分を1画面ずつ磨く
* sqlite-vecの実機50k非空検証→InitialDataの本投入
