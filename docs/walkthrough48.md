# walkthrough48 — docs完全棚卸し (調査のみ・コード変更なし)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** なし (docs調査セッションのため未実行。前回wt47で assembleDebug + testDebugUnitTest + 実機起動確認済み)

## 1. 背景

docs/ が60件超 (walkthrough 47件 + 設計版履歴 + 要求メモ + guide 8件) に膨張し、正本・陳腐・重複の判別が不能になっていた。
4系統に分けて並行精読し、実装 (`AppDatabase.kt: version=11`, `DatabaseModule.kt: MIGRATION_1_2〜10_11`) を正として判定した。

## 2. 方法 (4並行サブエージェント・読取のみ)

* 設計正本系: 統合設計書(無印/v13/v14/v15) + PersonalEncyclopedia.md + 完全タスクリスト + 全タスク一覧v2〜v5 + GAP計画v6 + DESIGN §13〜15
* 要求引継ぎ系: RealityTasks / NextTasks / mismatch / chatted / zako_task / 継承 / 報告書 / 強化計画 / 新採点 / BASELINE + AGENTS参照解決
* walkthrough系: 無印+2〜47の先頭15行 + git log -30突き合わせ
* guide系: guide 8件全文 + AppDatabase/DatabaseModule正本照合

## 3. 結果サマリ

* 正本は二本立て: 理想=`統合設計書-v15完全版.md`、実装=`DESIGN.md`。運用正本=`AGENTS.md` (五原則は有効)
* 最重要不具合: `AGENTS.md` が参照する `docs/PersonalEncyclopedia-パフォーマンス大改良計画.md` が不在。正体は `docs/NextTasks.md`
* walkthroughは欠番なし47件揃い。wt40〜46は `compileDebugKotlin` 未実行のまま残る (wt47でassembleは通過)
* guideは `03-connection.md` のみwt40/44/46反映済み。他7件はwt40〜47未反映
* 削除候補: 無印統合設計書・完全タスクリスト・全タスク一覧v2/v3/v4。アーカイブ候補: v13/v14/v5/v6/PersonalEncyclopedia.md

## 4. 検証

* `git status --short` クリーン確認後に本記録のみ追加
* ビルド不要 (docsのみ変更)。次回コードセッションで `./gradlew assembleDebug` を通すこと

## 5. 残課題

* AGENTS.mdのリンク切れ修正 (ファイル名統一) は別セッションで1機能として実施
* guide 7件のwt40〜47反映、DESIGN §13/§15/付録Aのwt47差分訂正も別セッション
* 本セッションでは棚卸し報告のみ。削除・移動・改訂は実行していない
