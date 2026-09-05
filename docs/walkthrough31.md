# walkthrough31 — 並べ替えクイズ形式の追加 (P1-3)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL
**根拠:** 強化計画 Phase 1（P1-3）、LearningProjectsの出題多様性資産

## 1. 背景

`sort` はスキーマ上存在したが生成・出題対象外だった。同分野4語があれば
ルールベースで出題できるため、cloze/customより先に開通させる。

## 2. 変更 (6ファイル)

* `brain/quiz/RuleBasedQuizGenerator.kt` (+約40行):
  - `generateSort(members, topicId)`（読み五十音順が正解、`>` 区切り、選択肢シャッフル、
    シャッフル結果が正解と同一なら振り直し）。採点は既存パイプラインの正規化完全一致
    （`>` は保持・空白除去のため `A > B` も可）。
  - `generateBatch` で同分野4語以上なら1問追加。
* `repository/QuizRepository.kt` (+5行): `generateFromEntry` と `getNextQuizzes` 既定型にsort追加。
* `repository/SettingsRepository.kt` (+2行): `SUPPORTED_QUIZ_TYPES` にsort追加（4種化）。
* `server/routes/QuizRoutes.kt` (+1行): 既定型にsort追加。
* `ui/screen/QuizScreen.kt` (+約20行): sortは選択肢一覧＋ `>` 区切り入力欄。
* `ui/screen/QuizListScreen.kt` (+1行): ラベル「並べ替え」。

## 3. 検証

* コンパイル成功。実機では同分野4語以上で生成→出題→ `>` 回答で正解になることを確認する。
* cloze/customは引き続き対象外（仕様）。

## 4. 次の一手

* CORS導入・トークン暗号化・Web未配線の解消。
