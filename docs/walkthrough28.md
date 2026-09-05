# walkthrough28 — 白板セクションCRUD開通 (P1-1)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL（警告2件は既存のopt-in由来）
**根拠:** 強化計画 Phase 1（P1-1）

## 1. 背景

`WhiteboardSectionEntity`・DAO・Repository（`addSection/deleteSection/setNodeSection`）は
実装済みだったが、VMにも画面にも呼出が無く、枠は表示のみだった。呼出元ゼロの死に機能。

## 2. 変更 (2ファイル)

* `viewmodel/WhiteboardViewModel.kt` (+約30行):
  - `createSection(title)`（ランダムずらし配置＋touchBoard）。
  - `renameSection(id, title)`（現行取得→copy→upsertSection。DAO改修なし）。
  - `deleteSection(id)`。
* `ui/screen/WhiteboardScreen.kt` (+約70行):
  - TopBarにセクション追加アクション（既存Dashboardアイコン流用）＋作成ダイアログ。
  - 枠タイトルタップで改名ダイアログ、右上×で削除（ノード削除と同型）。

## 3. 検証

* コンパイル成功。実機では作成→改名→削除→再起動後残存を確認すること。
* リサイズ・ノード割付は残（P3/将来）。

## 4. 次の一手

* 通知系（ToDo期限・タイムボックスのWorkManager+通知）。
