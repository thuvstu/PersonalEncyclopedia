# walkthrough55 — おまかせタスク提案 (先読み自動生成・承認制・LLM整形)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `./gradlew assembleDebug testDebugUnitTest` 成功。`TaskSuggesterTest` 6件全通過。実機未接続のため起動確認は未実施。

## 1. 背景

「突先を見据えて1から自動でタスクを作る」要求。骨格「自動処理は必ず提案→承認」に従い、
直接タスク化せず ToDo画面の「🔮おまかせ提案」からワンタップ採用/却下する方式にした。

## 2. 変更 (4ファイル + テスト1)

* `db/dao/EntryExtensionDao.kt`
  - `getUpcomingEvents(from, to, limit)` 追加。クエリ追加のみでスキーマ変更・マイグレーション不要
* `brain/task/TaskSuggester.kt` (新規)
  - 先読み信号: SRS到来 (`getDueEntries`)・未克服誤答 (`getWrongUnmasteredQuizzes`+`QuizFormats.IDS`)・
    未挑戦 (`getNeverAttemptedQuizzes`)・7日以内イベント・48h以内締切タスク
  - `TaskSuggestionBuilder.build` は純粋関数 (優先度: 復習→苦手→未習→イベント準備→締切仕上げ、最大5件)
  - LLM設定時のみ `GeminiClient.generate` で題名・説明を整形。見積もり・締切・紐付けは決定論を保持し、
    1件でも解釈不能ならその件は決定論のまま。未設定・失敗時は決定論のみ
* `viewmodel/TaskViewModel.kt`
  - `suggestions` / `suggesting` StateFlow、`refreshSuggestions`・`adoptSuggestion`・`dismissSuggestion` 追加。
    却下キーはVM内保持の揮発性
* `ui/screen/ToDoScreen.kt`
  - 提案ヘッダ (生成/再生成ボタン+進捗) と `SuggestionCard` (理由・目安時間・タスク化/いらない) 追加
* `app/src/test/.../brain/task/TaskSuggesterTest.kt` (新規・6件):
  信号ゼロ→0件、復習最優先、苦手と未習の分離、イベント紐付けと締切<開始、締切間近は1件、最大5件切詰め

## 3. 効果

* ToDo画面の「提案を生成」で、復習・苦手・未習・イベント準備・締切仕上げが最大5件並ぶ
* Gemini APIキー設定時は言い回しが磨かれる。未設定でも決定論テンプレートで動く
* 採用は通常タスク化 (見積もり・締切・entry/topic紐付けを引き継ぐ)

## 4. 検証

* `compileDebugKotlin` 成功 (途中でFlow拡張import漏れ1件を修正)
* `testDebugUnitTest` 成功。`TaskSuggesterTest` 6件全通過をXMLで確認
* `assembleDebug` 成功。実機未接続のため `adb install` は次回接続時に実施

## 5. 残課題 (次セッション以降)

* 実機での起動・提案生成・採用の動作確認 (最優先)
* 提案の永続化 (却下キーがVM揮発のため画面離脱で復活する)
* 信号の拡充候補: 放置entryの再浮上 (`ResurfacingEngine`)、学習時間帯の考慮
