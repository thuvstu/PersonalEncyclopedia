# walkthrough16 — Hub/透明性 + UI磨き3画面

**日付:** 2026-08-28
**対象:** 明示的初期データ投入、DB透明性、Whiteboard/Wiki/Quizの視認性

## 1. Hubの透明性

* DashboardViewModelに AppDatabase注入 + `seedInitialData()` / `seedState` / `isSeeding` を追加
* Dashboardの統計カード内に「初期データ135件を投入」ボタンを常設 (0件時はFilled、既存あり時はOutlined+説明)
* DatabaseManagementScreenの統計カードを「全部が見える」にリネームしSQL Explorer導線を強調

## 2. UI磨き

* Whiteboard: 空状態Card→ElevatedCardで階層明確化
* Wiki: TopAppBar→CenterAlignedTopAppBarで重心調整
* Quiz: カード定数整理

## 3. ビルド

* assembleDebug BUILD SUCCESSFUL (各コミットで確認)
* testは前回99 passed

## 4. 残タスク

* 初期データの3層化を全件へ展開
* Settingsにも投入ボタンを追加
* sqlite-vec実機検証→初期データ本投入
