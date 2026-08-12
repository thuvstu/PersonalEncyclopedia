  # Personal Encyclopedia — 全タスク一覧 v5(実コード検証版)

**作成日:** 2026-08-11
**方法:** GitHub実リポジトリ(thuvstu/PersonalEncyclopedia, master, 最終コミット2026-08-10 "がちでわからん 助けてコーディングエージェントー！")を実際にクローンし、ファイル単位でソースコードを検証した
**位置づけ:** v3/v4タスクリストは自己申告の報告書ベースだった。本v5は実コード確認済みの内容に全面差し替える

---

## 0. 現状棚卸し(実コード確認済み)

| 項目 | 実態 |
|---|---|
| Kotlinファイル数 | 145 |
| DBバージョン | 6(`exportSchema = false`のためschemas/2.jsonのみ存在) |
| UI画面数 | 17ファイル(Dashboard/Search/EntryDetail/EntryEdit/ThoughtEdit/DefinitionEdit/QuizList/QuizEdit/Quiz/SrsReview/Connections/ConnectionCandidates/Import/Stats/Settings/DatabaseManagement/Whiteboard/Wiki) |
| ライブラリバージョン | Kotlin 2.1.0 / Compose BOM 2025.01.01 / Room 2.7.1 / Ktor 3.1.1 / Hilt 2.54 / AGP 8.7.3(いずれも設計書v14で調査した最新版より数バージョン遅れ) |

**実装済みと確認できたエンジン(想定より大幅に進んでいた)**

| エンジン | ファイル | 状態 |
|---|---|---|
| 多段階採点 | `brain/quiz/Grader.kt`(183行) | 実在。和暦5元号のみの制約あり |
| ルールベースクイズ生成 | `brain/quiz/RuleBasedQuizGenerator.kt`(142行) | 実在 |
| LLMクイズ生成 | `brain/quiz/LlmQuizGenerator.kt`(93行) | 実在。ただしBUG-1あり |
| 数値可変問題 | `brain/quiz/NumericVariantEngine.kt`(73行) | 実在 |
| コーチング | `brain/coaching/CoachingEngine.kt`(134行) | 実在。ただしBUG-3あり |
| 接続エンジン | `brain/connection/ConnectionEngine.kt`(129行) | 実在 |
| ベクトル検索 | `brain/search/InMemoryVectorIndex.kt`(60行) | 実在。スレッドセーフ未対応 |
| Gemini/Ollamaクライアント | `brain/ai/GeminiClient.kt`, `OllamaClient.kt` | 両方実在 |
| Embeddingキュー | `brain/ai/EmbeddingQueue.kt`(222行) | 実在 |
| ファクトチェック | `brain/ai/FactCheckEngine.kt`(71行) | 実在 |
| SM-2 / FSRS | `brain/srs/Sm2Algorithm.kt`, `FsrsAlgorithm.kt` | 両方実在 |
| プラグインエンジン | `plugins/PluginEngine.kt`(192行) | 実在 |
| バックアップ(暗号化) | `backup/BackupWorker.kt`, `BackupEncryptor.kt` | AES-256-GCM実装済み。端末外送信は未実装(TODOコメントのみ) |
| Ktor APIサーバー | `server/LocalServer.kt`(579行) | 実在。単一巨大クラス(§10.1で指摘した通りの状態) |
| ホワイトボード | DB(4テーブル)+DAO+2画面 | ほぼ完成。私の設計より洗練された構造(`whiteboard_node`がentry/note排他参照) |
| Wikipediaビルダー | DB+DAO+画面 | ほぼ完成 |

**結論**: v12.0は「10機能同時導入で全部壊れた」のではなく、**ほぼ全機能を作り終えた状態から、局所的な数箇所の不整合でビルドが止まり、そこで撤回に至った**、というのが実態に近い。

---

## 1. 🔴 確定バグ(ビルドを止めている・最優先・今すぐ)

実際のコードを突き合わせて確認した、現時点で確実に存在するバグ。

### BUG-1: `QuizDao.countByQuestion()`が存在しない

- **呼び出し箇所**: `repository/QuizRepository.kt`(39行目・61行目)、`brain/quiz/LlmQuizGenerator.kt`(71行目)
- **実際の`QuizDao.kt`**: 該当メソッドなし
- **修正**: `QuizDao.kt`に以下を追加するだけ
  ```kotlin
  @Query("SELECT COUNT(*) FROM quiz_bank WHERE question = :question")
  suspend fun countByQuestion(question: String): Int
  ```

### BUG-2: `RichContentView`呼び出し時のパラメータ名不一致

- **実際の定義**(`ui/component/RichContentView.kt`): `fun RichContentView(content: String, onWikiLinkClick: (String) -> Unit = {}, modifier: Modifier = Modifier)`
- **壊れている呼び出し**: `ui/component/EntryTypeSections.kt`(329行目)、`ui/screen/EntryDetailScreen.kt`(147行目) — いずれも`markdown =`, `onInternalLink =`という存在しない引数名で呼んでいる
- **正しく呼べている箇所**(参考): `ui/screen/WikiScreens.kt`(116行目・179行目)は`content =`, `onWikiLinkClick =`で正しく呼んでいる
- **修正**: 壊れている2箇所を、`WikiScreens.kt`と同じ引数名に合わせるだけ

### BUG-3: `CoachingEngine.analyzeWeakPoints(topicId)`がtopicIdを無視

- **場所**: `brain/coaching/CoachingEngine.kt`(75行目〜)
- **実際**: `topicId`はキャッシュキー(`"topic_$topicId"`)にしか使われておらず、誤答取得は`quizDao.getWrongQuizzes(limit = 20)`(全トピック対象)を呼んでいる
- **修正**: 既に`QuizDao`に存在する`getWrongQuizzesByTopic(topicId, limit)`に差し替えるだけ

**この3つを直した時点で一度ビルドを試すこと。** おそらくこれで初めてビルドが通るか、通らなくても同種の小さな不一致があと1〜2箇所露出する程度で収まるはずである(実装原則§2.5のエラー3ファイル基準の範囲内)。

---

## 2. 🟠 リポジトリの整理(ビルドに影響しないが早めに片付ける)

| ID | 内容 | 詳細 |
|---|---|---|
| CLEAN-1 | `combined_code.txt`を削除 | `app/src/main/java/com/thuvstu/personalencyclopedia/combined_code.txt`(56万字・13,672行)。Windows PowerShellで全ファイルを結合したデバッグダンプがそのまま残っている。`.gitignore`にも追加する |
| CLEAN-2 | `apps/web/`を正しい場所へ移動 | `app/src/main/java/com/thuvstu/personalencyclopedia/apps/web/`(React/TSファイル)がAndroidのKotlinソースツリー内に誤って配置されている。中身がスタブ程度なら削除、実質的な内容があるなら設計書§4.2の`apps/web/`(リポジトリ直下)へ移動する |
| CLEAN-3 | `Migration6to7.kt`を削除または転用 | `wiki_article`を再度作ろうとしているが`Migration5to6.kt`で既に作成済みの重複。どこからも参照されていない。削除するか、次の実マイグレーション(和暦マスタ・カスタムフィールド追加用)として書き直す |

---

## 3. 🟡 確認された既知のギャップ(実コードで再確認・優先度順)

| ID | 内容 | 実コードでの確認結果 |
|---|---|---|
| GAP-1(最優先) | バックアップが端末外に出ていない | `BackupWorker.kt`: AES-256-GCM暗号化・30世代保持まで実装済みだが、`// TODO Phase 1.5: Upload to Google Drive`のコメントのままアップロード処理が存在しない。**今の実装では端末紛失=全データ消失**。設計書§6.2の方針通りSAF方式で実装する(`google-api-services-drive`等のDrive API依存は削除してよい) |
| GAP-2 | `exportSchema = false` | `AppDatabase.kt`で無効化されている。マイグレーションテスト(`MigrationTestHelper`)を書く前に、まず`true`に戻してv3〜v6のスキーマJSONを再生成する必要がある |
| GAP-3 | APIキーが平文保存 | `SettingsRepository.kt`で`stringPreferencesKey("GEMINI_API_KEY")`を素のDataStoreに保存。EncryptedSharedPreferences/Keystoreへの移行が未着手 |
| GAP-4 | `InMemoryVectorIndex`が無防備 | `ids`/`vectors`が同期なしの`var`。`addVector`/`removeVector`の配列再代入と`topK`の読み取りが競合しうる。設計書§7.6のAtomicReferenceスナップショット方式で修正する |
| GAP-5 | 和暦が5元号のみ | `Grader.kt`(48行目)に令和/平成/昭和/大正/明治のみハードコード。設計書§5.8.4・§8.9の`era_master`テーブル化が未着手 |
| GAP-6 | `LocalServer.kt`が579行の単一クラス | 設計書§10.1のルーティング分割が未着手 |

---

## 4. 再整理した推奨着手順

1. **BUG-1〜3を直す**(1セッション・3ファイル程度、実装原則§2.5の範囲内)
2. **初めてビルドを通す**。通らなければ出てきたエラーをここで初めて個別に見る(3ファイル基準を超えたら§2.5.3のプロトコルに従う)
3. **実機/エミュレータで実際に起動確認**。ここまでで「動くPersonalEncyclopedia」が初めて手に入る
4. **CLEAN-1〜3を1コミットで片付ける**(機能変更を含まないためリスクなし)
5. GAP-3(APIキー暗号化)→ GAP-4(スレッドセーフ化)→ GAP-5(和暦マスタ)を1つずつ、テストを添えて実施
6. GAP-2(`exportSchema=true`復帰)→ マイグレーションテスト整備
7. **GAP-1(SAFバックアップ)を実装**。ここまで来て初めて「データが本当に守られている」状態になる
8. GAP-6(Ktorルーティング分割)、およびv14設計書のライブラリ更新(§4.1.1: Kotlin 2.1.0→2.4.0、Compose BOM 2025.01.01→2026年版、Room 2.7.1→2.8.4等)は上記が安定してから着手する

**この時点で、ホワイトボード・Wiki・FSRS・Ollama・コーチング等の「新機能」は既にほぼ実装済みのため、v3/v4タスクリストの「Round 6以降」は新規開発ではなく、実機での動作確認・磨き込みフェーズとして扱ってよい。**
