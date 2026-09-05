# walkthrough29 — ToDo通知を本物にする (v15 §8.10.3)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL（警告2件は既存由来）
**根拠:** mismatch §2.1・§5.2、強化計画 Phase 5（P5-2の先行実装）

## 1. 背景

TaskEngineの強制対峙は動くのに通知が一切無く、アプリを閉じたら何も起きない。
`Notification|AlarmManager` はコード0件、`POST_NOTIFICATIONS` は宣言のみだった。

## 2. 変更 (1新規+3編集)

* 新規 `task/TaskNotifyWorker.kt` (HiltWorker):
  - `deadline_sweep`（15分周期）: 期限1時間以内 or 期限切れ24時間以内を1回だけ通知
    （端末内SharedPreferencesで通知済み管理、再通知なし）。
  - `timebox`（ワンショット）: 開始時に見積もり時間後で予約。発火時に進行中＋超過のときだけ通知。
  - チャンネル `task_reminders` を冪等作成。タップでアプリ起動。権限なしでもクラッシュしない。
* `PersonalEncyclopediaApp.kt`: Phase Cに sweep を登録。
* `viewmodel/TaskViewModel.kt`: 開始で予約、完了・破棄でキャンセル。
* `ui/screen/ToDoScreen.kt`: Android 13+ で初回に権限リクエスト（拒否でも機能は動く）。

## 3. 検証

* コンパイル成功。実機では (a) 権限ダイアログ、(b) 期限間近通知、
  (c) タイムボックス終了通知、(d) 完了後のキャンセル（通知が来ないこと）を確認する。
* 再通知・スヌーズは将来（v15 §8.10.3のスヌーズ2回規定は未実装）。

## 4. 次の一手

* changeSummary AI生成＋履歴復元。
