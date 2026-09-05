# walkthrough35 — 採点堅牢化3点 (#Q1/#Q2/#I1/#P1)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` + `testDebugUnitTest(brain.quiz.*)` 成功
**根拠:** DESIGN §13 A2/A3・B3・D3・§15 #Q1/#Q2/#I1/#P1

## 1. 背景

採点・取込・プラグインの4件の既知欠陥を一括解消する（いずれも小規模・独立）。

## 2. 変更 (5ファイル)

* `brain/quiz/LlmQuizGenerator.kt` (#Q1・+3行):
  選択肢・ヒントJSONの手文字列結合を `json.encodeToString` に置換。
  `"`・改行混じりでも壊れない。
* `brain/quiz/SemanticGrader.kt` + `brain/quiz/QuizGraderService.kt` (#Q2・+8行):
  KDoc通り「0.70以上で部分点」を実装。`semantic-partial` として保持し、
  -1.0ではなく `score - hintPenalty*hints` の減点緩和にする。
  既存テストはAPI未設定スキップのみのため影響なし（`testDebugUnitTest` 緑で確認）。
* `importer/ObsidianImporter.kt` (#I1・+4行):
  `getById(note.title)` の誤用を `findByTitle` に修正＋同バッチ内重複も検査。
* `plugins/PluginEngine.kt` (#P1・+約20行):
  JSソースへの直接補間をやめ、回答・JSONはスコープ変数で渡す＋JSON事前検証。
  特殊文字回答での任意JS実行・誤採点を防ぐ（ClassShutter等は将来）。

## 3. 検証

* コンパイル＋クイズ系JVMテスト成功。実機では特殊文字回答のプラグイン採点、
  同一Vault二重投入の件数不変を確認する。

## 4. 次の一手

* バックアップのストリーミング化（#K1）＋FTS差分更新。
