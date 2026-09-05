# walkthrough25 — バグ潰し+体験3連打 (#S1/P2-A/P6-1/P5-1)

**日付:** 2026-09-06
**コミット:** bd6380c (#S1) + 6956693 (P2-A) + 148276b (P6-1) + fe5443d (P5-1)
**ビルド:** 全コミット前に BUILD SUCCESSFUL (`:app:compileDebugKotlin`、警告は既存由来のみ)
**根拠文書:** `docs/強化計画.md` Phase 1〜2、`docs/chatted.md` 修正後ステップ0・1、`docs/mismatch.md` §1.2/§2.6

## 1. 背景

mismatch.md の判定は「達成ゼロ・部分13・未着手2」。骨格は構想超えだが体験層が部分止まり。
chatted.md の修正後順序 (0→0.5→1→2→3→4) のうち、コードで片付くものから着手した。
ステップ0.5 (規模目標の決定) は判断事項のため据え置き。

## 2. 変更 (4コミット・各1機能で緑を維持)

* `server/routes/QuizRoutes.kt` (#S1・bd6380c):
  - `get("/count")` を `get("/{id}")` より前に移動 + 再発防止NOTE。
    Ktorは登録順解決のため、逆だと `count` が `id="count"` に吸収され404だった (DESIGN §13 A1)。
  - EntriesRoutes・SrsRoutesに同型問題なしを確認済み。
* 自動リンク配線 P2-A (6956693・3ファイル):
  - `importer/AutoLinker.kt` (+27行): 純粋関数 `applyAsWikiLinks(text, selfEntryId)`。
    検出→`[[title]]`埋め込み、自己除外、既存`[[...]]`内の二重化防止、後方挿入。
  - `viewmodel/EntryDetailViewModel.kt` (+14行): `autoLinkedContent` / `autoLinkedDefinition`
    (既存flowのcombine、DB不変)。
  - `ui/screen/EntryDetailScreen.kt` (+12行): 本文は埋め込み済み文字列を `RichContentView` へ、
    定義文は `copy()` 差し替えで `EntryTypeSection` へ。`RichContentView` 無改修・承認制不変。
  - これで `EntryTypeSections.kt:328` の「自動リンク」コメントが虚偽表示でなくなった。
* bookmark.html取込 P6-1 (148276b・3ファイル):
  - `importer/ImportPipeline.kt` (+約130行): Netscapeパーサ (`parseNetscapeBookmarks` 純粋関数、
    フォルダ階層・ADD_DATE復元、`<p>`ラッパー・兄弟DL両配置対応、jsoupのみ) +
    `importBookmarksHtml` (URL重複スキップ、本文スクレイプなし高速登録、
    `metadataJson` にフォルダ記録、`scraperUsed="bookmark_import"`)。
    `EntryExtensionDao` 注入を追加 (Hiltの既存 `@Provides` 利用)。
  - `viewmodel/ImportViewModel.kt` (+14行): `importBookmarkHtml`。
  - `ui/screen/ImportScreen.kt` (+6行): 取込行「🔖 bookmark.html」。
  - ビルド警告1件 (不要safe call) はその場で潰した。
* ポモドーロ P5-1 (fe5443d・2ファイル):
  - `viewmodel/TaskViewModel.kt` (+約90行): `PomodoroPhase` (IDLE/FOCUS/BREAK) +
    25分/5分往復ループ (VM常駐で回転に強い、実行中タスクと独立、切替は既存messageでToast)。
  - `ui/screen/ToDoScreen.kt` (+約60行): `PomodoroCard` (残時間・進捗バー・サイクル数・開始/一時停止/リセット)。
  - ビルド警告2件はいずれも既存由来 (`!!`・`flatMapLatest` は今回追加分に無し)。

## 3. 検証

* 4コミットとも単独でビルド成功。エラー波及なし (3ファイル基準に抵触せず)。
* 実機確認は次回実機セッションで: (a) `/api/quiz/count` が200を返すこと、
  (b) 詳細画面で既知タイトルがリンク化→タップでプレビューが出ること、
  (c) 実bookmark.html数百件の投入時間・重複スキップ、
  (d) ポモドーロ25分→5分の往復と回転時の継続。

## 4. 次の一手 (1機能ずつ)

* P2-B: Wiki記事本文への自動リンク配線 (`WikiScreens.kt`、P2-Aと同型)。
* P2-C: `EntryTypeSections` 孤児疑いの実態確認込み配線 (DESIGN §13 C4と統合)。
* P1-1: 白板セクションCRUD UI (Entity/DAO/Repo済み・呼出ゼロ)。
* P3: 白板エッジ表示 (別FablePKMENCY風の `whiteboard_edges` 設計を参考に。下記調査の移植候補P0-1)。
* ステップ0.5: 規模目標の決定 (50GB/数万entry/50k件の三重状態の解消、コードなし判断)。

## 5. 外部プロジェクト調査メモ (2026-09-06、読み取り専用)

`C:\Users\gogok\Documents\programming` 配下のlearning/knowledge/PKM系3系統を調査。
詳細は本セッションの報告サマリを参照。要点のみ:

* **別FablePKMENCY風** (Next.js 16 + Drizzle + PG、自称Codex個人百科): xyflowなし自前キャンバス
  (`BoardCanvas.tsx` 608行、辺SVG+ベジェ+`C`接続モード+`F`フィット)、`whiteboard_edges(label)`、
  `[[T|alias]]`抽出+改名追随、CmdKパレット、`[[`オートコンプリート+赤リンク、SQLiteダンプ/MD書籍export、
  赤リンク上位提示のstats。**移植筆頭は辺label・リンク抽出正規表現・CmdK・赤リンク・export形式**。
* **Fable5.1製SQLiteHeptabasePKM** (Next 16 + @xyflow/react 12、実態PG):
  `syncRelations` ([[x]]/#y→自動作成+全置換+孤児掃除)、未リンク言及クエリ、エッジWダブルクリックでラベル編集、
  D&D配置、決定論force-directedグラフ。**未リンク言及・ラベル編集・D&Dが移植候補**。
* **my-learning-canvas** (Vite+xyflow、localStorage): 動的ハンドル均等再配分、編集中フラッシュ防止+debounce、
  検索dim+ジャンプ、Undoスナップショット規律。**UX細部の教科書**。
* **LearningProjects** (GAS/LearningHub/LMM): 未取り込みは `10_config.js` のDB別約30出題mode+
  `judge` 定義、7ゲーム、rating推定式、Anki CSV/NotebookLM出力、edge-tts、Notion/Linkwarden取込層。
  **クイズ多様性 (sort/matching/true-false) がP1-3直結**。通知は3PJとも無し (PE新規実装要)。
* 移植禁止の再確認: PG/Docker/常駐構成は持ち込まない (ロジック・スキーマ・UXのみ)。
