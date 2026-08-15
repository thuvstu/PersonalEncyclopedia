Personal Encyclopedia — 設計書v14 未実装要素 実装計画 (walkthrough4)
設計書v14(docs/PersonalEncyclopedia-統合設計書-v14完全版.md)の監査で見つかった未実装要素のうち、ユーザー選択スコープ「DB層 #1-3」を「1機能1ビルド」「毎回コミット」の原則に則って実装・検証します。

実施スコープ（設計書v14の監査結果より）
#1 カスタムフィールド (§5.8.3): entry_custom_field テーブル新規
#2 srs_review.repetitionCount (§5.8.5): 明示的な反復回数記録（FSRS移行§8.8の前提）
#3 quiz_attempts.answeredWithinMs (§8.7.3): 早押しスコア係数用の回答時間記録
上記3件を「1つの v8 マイグレーション」に束ねる（設計書§5.8.1の実装メモ: Entity/version/DIの3点セットは同一コミットで揃える）。

今回のスコープ外（監査で未実装が確認済み・別ラウンドで対応）
#4 クイズバリエーション / #5 EntryPreviewPopup / #6 SettingsScreen(LAN警告・SRS切替UI・AI設定) / #7 DuplicateDetector / #8 Webアプリ / #9 docs/guide / #10 App起動時runStep分割

実施ラウンド
Round 1: v8 マイグレーション一式（1コミット）
Round 2: repetitionCount の明示的記録（1コミット）
Round 3: answeredWithinMs の記録（1コミット）
Round 4: walkthrough4.md 最終更新 + 全体検証（1コミット）

Round 1: v8 マイグレーション一式
概要
- §5.8.3: EntryCustomFieldEntity + EntryCustomFieldDao を新規追加（Index(entryId)）
- §5.8.5: srs_review に repetitionCount INTEGER NOT NULL DEFAULT 0 を追加
- §8.7.3: quiz_attempts に answeredWithinMs INTEGER（nullable）を追加
- Migration7to8 を新設し、上記3点 + SrsCurrentView の再作成（repetitionCount を含む定義へ更新）を行う
- AppDatabase.kt version=7→8、DatabaseModule.kt に MIGRATION_7_8 と DAO provides を追加
- 既存 MigrationTest の version パラメータ不備（終了バージョンでなく開始バージョンが渡され、マイグレーションが実実行されないバグ）を修正し、v7→v8 検証を追加
- 既存 Migration1to2 の View 作成SQLの空白形式（Room スキーマJSONとの完全一致が要求される）を修正
- ビルド時に schemas/ に 8.json が自動生成されることを確認

変更予定ファイル
[NEW] db/entity/EntryCustomFieldEntity.kt
[NEW] db/dao/EntryCustomFieldDao.kt
[NEW] db/Migration7to8.kt
[MODIFY] db/entity/SrsReviewEntity.kt（repetitionCount 追加 + SrsCurrentView 更新）
[MODIFY] db/entity/QuizBankEntity.kt（QuizAttemptEntity に answeredWithinMs 追加）
[MODIFY] db/AppDatabase.kt（version=8 / entity / dao 追加）
[MODIFY] di/DatabaseModule.kt（MIGRATION_7_8 + provide）
[MODIFY] db/Migration1to2.kt（View SQL を Room 出力形式に修正）
[MODIFY] androidTest/.../MigrationTest.kt（version 修正 + v8 テスト追加）
[MODIFY] docs/walkthrough4.md（本ラウンドの結果を追記）

完了チェック
[ ] .\gradlew.bat :app:assembleDebug が成功（8.json が自動生成される）
[ ] .\gradlew.bat :app:testDebugUnitTest が green（ベースライン13件維持）
[ ] git コミット実施

Round 2: repetitionCount の明示的記録
概要
- §5.8.5 の意図どおり「レビュー時点の状態を明示的に記録」へ変更
- SrsRepository.recordReview / SrsRoutes POST /review が、間隔日数からの逆算推定をやめ、DBの repetitionCount をそのまま前回値として使い、次回値（成功時 +1 / 失敗時 0）を保存する
- v8 移行前のレコード（repetitionCount=0 のまま）は従来の推定をフォールバックとして維持（既存挙動を壊さない）
- Sm2Algorithm / FsrsAlgorithm の createReview が repetitionCount を保存する

完了チェック
[ ] ビルド成功
[ ] 単体テスト green
[ ] コミット実施

Round 3: answeredWithinMs の記録
概要
- QuizViewModel が設問表示〜回答までの経過時間を計測し、QuizRepository.gradeAndRecord 経由で保存
- Ktor API(QuizRoutes POST /{id}/attempt)も answeredWithinMs を受け付ける
- スコア計算式への係数組み込み（早押し加点）は今回のスコープ外（DB層のみ）

完了チェック
[ ] ビルド成功
[ ] 単体テスト green
[ ] コミット実施

Round 4: 全体検証
概要
- 全ラウンドの変更を walkthrough4.md に最終反映
- 最終ビルド + 単体テスト再実行
- コミット実施
