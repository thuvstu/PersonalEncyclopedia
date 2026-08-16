Personal Encyclopedia — v15.0機能実装 (walkthrough7)
master(83b4c00)上で、統合設計書v15完全版で新設された6機能を実装しました。
対象: §5.9(タスク・編集履歴のデータモデル) / §8.10(タスクエンジン) / §7.8(StudyPlus連携) /
      §11.11(ToDo画面) / §11.12(SQL Explorer) / §11.13(編集履歴UI)
DBは v8 → v9(新テーブル4つ)。本walkthrough作成時点では未コミット(作業ツリー状態)。

方針
- 対象機能がコードベースに一切存在しない(検索で0件)ことを確認してから着手
- StudyPlus公式SDK(com.github.studyplus:Studyplus-Android-SDK:4.0.2、JitPack配布)は
  オフライン環境で解決不可(JitPackへ到達不可)のため、SDK呼び出しだけをブリッジに隔離し、
  同期キュー・設定・UIまでは完全実装した(設計書§7.8の「登録承認までOFF運用」方針どおり)
- DB層 → エンジン → UI の順に、compileDebugKotlin を都度通過させながら1機能ずつ追加

実施内容

A. DB v9(§5.9 / §11.12)
- 新エンティティ(TaskEntities.kt):
  TaskEntity(task) / TaskTimeLogEntity(task_time_log) / EntryHistoryEntity(entry_history) / SavedQueryEntity(saved_query)
- 新DAO: TaskDao / TaskTimeLogDao / EntryHistoryDao / SavedQueryDao
  TaskWithActualMinutes はRoomが生クエリでネスト型をマッピングできないため平坦化POJO
- Migration8to9: 4テーブル + インデックス5本(task×2 / task_time_log×1 / entry_history×2)
- TopicDao に getById / observeAll を追加(ToDoの科目選択・StudyPlusコメント欄用)
- DatabaseModule: 新DAO提供 + ReadOnlySqlExecutor + StudyPlusSdkBridge(NoOp)のバインディング

B. タスクエンジン(§8.10) — brain/task/TaskEngine.kt
- startTask(進行中ログ作成+in_progress) / completeTask(ログを閉じて返却→StudyPlus同期に使用) /
  abandonTask / failTask
- estimationBiasReport: 実績/見積もりの平均乖離を集計(§8.10.1、ダッシュボード表示用)
- postponeTask: MAX_SILENT_POSTPONES=3回を超えると RequireForcedChoice(§8.10.2)。
  forceFinishToday は締切を今日23:59:59に固定してカウントを+1
- progress_events に entityType='task' で started/completed/abandoned/failed/postponed を記録(§8.10.4)

C. 編集履歴(§5.9.2 / §11.13)
- EntryRepository: createThought / createDefinition / updateThought / updateDefinition /
  updateEntryCommon の5経路にスナップショット記録フック(対象は entry.content のみ、charCountDelta付き)
- EntryDetailScreen: 「🕘 編集履歴」セクション(新しい順・+/-文字数表示) + 読み取り専用プレビューダイアログ
  (復元・巻き戻しはv15.0のスコープ外として実装しない)

D. ToDo画面(§11.11)
- TaskViewModel: タスク一覧 / 実行中タスク+開始時刻 / 強制選択・タイムボックス期限切れ状態 /
  見積もり乖離 / StudyPlus同期呼び出し
- ToDoScreen:
  - ステータス別セクション(期限超過/期限間近24h/未着手/完了/破棄・失敗)
  - 作成ダイアログ: estimatedMinutes 未入力では保存不可(§8.10.1を画面レベルで担保)
  - 実行中カード: カウントダウン+プログレスバー。残り0秒で強制対峙モーダル(§8.10.3)
  - 延期ダイアログ(日時ピッカー)→ 上限超過時は強制選択モーダル
  - 強制選択モーダルは onDismissRequest={} でバックボタン・外側タップ不可(§8.10.2)
- Dashboard: 「✅ タスク(n)」ボタン + 見積もり精度レポートカード(§8.10.1)

E. SQL Explorer(§11.12) — 読み取り専用を強制
- ReadOnlySqlExecutor: 二重防御
  ① 先頭トークンが SELECT/WITH 以外は拒否 + コメント・文字列リテラル除去後の書き込み系キーワード検査
  ② 実行直前に PRAGMA query_only=ON、finally で必ず OFF(Roomは単一コネクションのため割り込みなし)
  結果は500行で打ち切り(巨大クエリ保護)
- スキーマブラウザ(sqlite_master + PRAGMA table_info)、DB統計(journal_mode/page_count/page_size/
  freelist_count)、integrity_check、保存済みクエリ(保存/読み込み/削除)
- DatabaseManagementScreen に導線を追加(通常のユーザー導線には置かない)

F. StudyPlus連携(§7.8)
- SettingsRepository: studyPlusEnabled(DataStore) / consumerKey・Secret(EncryptedSharedPreferences) +
  loadStudyPlusCredentials(起動時読み込み)
- StudyPlusClient: isConfigured / startAuth / syncTaskTimeLog(時間は最大24h、コメント=タスク名+科目) /
  observePendingSyncCount / syncAllPending(未同期分の一括再試行)
- StudyPlusSdkBridge インターフェース + NoOpStudyPlusBridge(SDK未導入時)。
  SDK導入の手順(JitPack追加・desugaring・ブリッジ実装)をコメントに明記
- SettingsScreen: StudyPlusセクション(ON/OFF・キー/シークレット入力・未同期件数・一括同期ボタン)
- TaskViewModel.completeTask から syncTaskTimeLog を呼び出し(未設定/無効時は静かにスキップ)

検証
- :app:compileDebugKotlin: 成功(KSPがDAOクエリ・Room v9スキーマを検証、9.jsonをエクスポート)
- :app:testDebugUnitTest: 全テスト green(合計99本、内TaskEngineTest 8本を新規追加)
- :app:assembleDebug: 成功
- :app:compileDebugAndroidTestKotlin: 成功(MigrationTest を v1→v9 チェーン + 新規 migrate8To9 に拡張)
- スキーマ整合: 9.json の createSql と Migration8to9 の CREATE TABLE が完全一致
  (実行時の identity hash 検証を満たす)

実装中に発見・対処した不具合
- Roomは生クエリでネスト型(TaskWithActualMinutes{task, actualMinutes})をマッピング不可
  → 平坦化POJOに変更
- Hilt MissingBinding: StudyPlusSdkBridge → DatabaseModule に @Provides(NoOpStudyPlusBridge)を追加
- SettingsRepository の studyPlusConsumerKey を flow{emit} で公開すると set 後に再 emit されず
  入力欄が読み取り専用になる → MutableStateFlow + 起動時 loadStudyPlusCredentials に変更
- consumerKey/Secret の setter がキーストローク毎にトーストを連発 → 保存メッセージを削除し無言保存に

残課題
- StudyPlus 実投稿: JitPackへ到達できる環境で SDK依存を追加し SdkStudyPlusBridge を実装
  (教材アプリ開発者登録の承認まで連携OFFで運用)
- §8.10.3 タイムボックス終了のWorkManager通知(バックグラウンド時のアプリ前面化+モーダル)は
  アプリ内モーダルのみ実装。通知経由は未実装
- §11.13 履歴の復元機能(巻き戻し)は需要が明確になってから別途設計
- entry_history.changeSummary(AI生成)は空欄のまま(需要があればLLM連携)
- 強制選択モーダル表示中にアプリがバックグラウンドへ退いた場合の動作は未検証(要実機確認)
