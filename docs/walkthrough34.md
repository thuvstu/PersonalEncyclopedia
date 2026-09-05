# walkthrough34 — Web未配線の解消「つながり」タブ (#W1)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `bun run build` 成功（tsc --noEmit + vite、837ms）
**根拠:** DESIGN §13 C5・§15 #W1

## 1. 背景

サーバ実装済みだがWeb導線が無かった: 接続CRUD・候補承認/却下・ヒートマップ・SRS件数。
`client.ts` に関数はあるが呼出0件のものもあった。

## 2. 変更 (4ファイル)

* `web/src/api/client.ts` (+約15行): `Candidate` 型＋ `deleteConnection` /
  `getCandidates` / `approveCandidate` / `rejectCandidate` を追加。
  これで未使用関数はゼロ。
* 新規 `web/src/components/ConnectPanel.tsx` (約200行):
  - 接続の検索・一覧・削除（タイトル解決はsearchで完全一致優先）。
  - 接続の作成（2タイトル＋9関係タイプ）。
  - 承認待ち候補の承認/却下（A/B表題を並列解決、失敗時はID表示）。
  - 学習ヒートマップ90日＋復習待ち件数（`getHeatmap`・`getSrsDueCount` を初配線）。
* `web/src/App.tsx` (+約10行): 5番目のタブ「つながり」。
* `web/src/styles.css` (+約35行): `.row`・`.heatmap` 系（既存変数のみ使用）。

## 3. 検証

* 型検査＋本番ビルド成功。実機ではLAN接続後に各操作が通ることを確認する。
* お気に入りトグル・削除・quiz/count等は未配線のまま（次回以降）。

## 4. 次の一手

* rerank・FTS差分化・バックアップ堅牢化。
