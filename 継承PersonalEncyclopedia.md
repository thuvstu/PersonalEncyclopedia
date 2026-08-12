# Personal Encyclopedia — 統合設計書 v12.0
**改良版（クリティカルレビュー反映・引き継ぎ用報告書）**

---

## ドキュメント情報

| 項目 | 内容 |
|---|---|
| バージョン | 12.0（Implementation Sync + Critical Review Edition）|
| 作成日 | 2026-08-02 |
| ベース | 統合設計書 v11.0（Unified Edition）|
| 性質 | **実装コードベースとの突合＋批判的レビュー＋改良設計**を統合した引き継ぎ用完全版 |
| 想定読者 | 次期開発担当者・レビュアー・プロジェクト引き継ぎ先 |

### 改訂履歴

| 版 | 内容 |
|---|---|
| 11.0 | 7系統の学習支援システム計画を統合した確定版（設計の到達点）|
| 12.0 | Phase 0〜3 の実装完了を反映。レッドチーム視点での批判的レビュー（§10）と、それを踏まえた改良設計（§11）を追加 |

### 本書の読み方

- **引き継ぐ人** → §1 → §9 → §10 → §13 の順で読むと全体像と危険箇所が掴めます
- **レビュアー** → §10（批判）と §11（改良）を重点的に
- **実装者** → §5〜§8 の仕様と §12 の優先順位を参照

---

## 1. エグゼクティブサマリー

### 1.1 プロジェクトの正体（30秒版）

Android 端末一台を「個人百科事典のサーバ兼クライアント」にするアプリ。

- **データ本体**：Room (SQLite)。`entry` 統一型 + 13種の拡張テーブル（Class Table Inheritance）
- **知能（Brain Layer）**：Embedding / ハイブリッド検索 / 接続候補生成 / 採点 / クイズ生成 / SRS — すべてアプリ内で完結
- **外部AI**：Gemini API（無料枠）。Embedding + LLM生成
- **PC連携**：内蔵 Ktor サーバ（Bearer認証）をLAN公開。PCはDBを持たないビューア

### 1.2 現状ステータス

| フェーズ | 状態 | 概要 |
|---|---|---|
| Phase 0（最小版）| ✅ 完了 | entry/thought/definition/tag、Ktor雛形、LIKE検索 |
| Phase 1（学習基本）| ✅ 完了 | SRS(SM-2)、quiz、多段階採点、ルールベース生成、バックアップ |
| Phase 2（検索・全型）| ✅ 完了 | FTS4+Nグラム、Embedding、Hybrid Search、全13型、Webスクレイパー |
| Phase 3（接続・UI完成）| ✅ 完了 | connection候補承認制、全13型統合エディタ、統計、デモデータ |
| Phase 4（自動接続・外部連携）| ⚪ 未着手 | ホワイトボード、FSRS、Drive同期等 |

### 1.3 最重要課題（レッドチーム評価）

> **「動くデモ」としては優秀。「一生使うデータ基盤」としては、バックアップが端末内に閉じている時点で Data-Permanent 要件（コア要件2）を満たしていない。ここが最優先の負債。**

---

## 2. プロジェクト概要

### 2.1 一文定義

生涯にわたって学んだこと・考えたこと・出会った情報のすべてを、常に手元にある Android 端末一台に記録し、間隔反復・AI採点・意味検索・知識接続によって「何十巻もの百科事典」規模まで育て続けられる、個人専用の学習兼知識管理基盤。

### 2.2 解決する問題

1. 情報がノートアプリ・単語帳・クイズアプリ・ブックマークに分散し、横断検索も接続もできない
2. 過去の複数システム（GAS版・Supabase版・WSL2常時起動版）が「入力の重さ」「常時稼働インフラの脆さ」「認知コストの増大」によって使われなくなった
3. 「一生使う」前提なら、PCの起動状態やクラウドサービスの継続可否にデータの生存が左右されてはならない

### 2.3 コア要件（優先順位）

| 優先度 | 要件 | 説明 | 現状充足 |
|---|---|---|---|
| 1 | Input-Easy | 入力摩擦を最小化。クイック追加は2タップ以内 | ✅ |
| 2 | **Data-Permanent** | データがアプリより長生き。オープン標準で常に復元可能 | 🔴 **未充足（§10-1）** |
| 3 | Free-First | 月額固定費ゼロ。Gemini無料枠に収まる設計 | ✅ |
| 4 | Search-Advanced | 全文+意味のハイブリッド検索 | ✅（性能改善余地あり）|
| 5 | Connection-Clear | 知識のつながりを可視化、暴走させない | ✅ |
| 6 | Learn-Deep | 間隔反復・多段階採点で「使える知識」に | ✅（正確性改善余地あり）|

### 2.4 対象プラットフォームと役割分担

- **Android** = データの本体・実行エンジン。Room DB・Ktor・Brain Layer・プラグインエンジンを内包
- **PC（React+Vite/bun）** = 入力・閲覧・可視化専用のクライアント。DBを持たない。React Flowによるグラフ描画はPC側
- **Google Drive** = 非同期の橋渡しとバックアップ倉庫（**現状未実装**）

---

## 3. 設計原則（不変の骨格）

### 3.1 データ・アーキテクチャ原則

1. **データが先、UIは後** — データは特定のUI・フレームワークに依存しない。Room Entityは可能な限りMarkdown/CSV/JSONへの可逆エクスポートを保つ
2. **自作するのは"知能"の部分だけ** — DB・ローカルHTTP・認証は成熟した仕組みをそのまま使う
3. **入力は最速の経路を用意する** — クイックキャプチャ・Android共有メニュー・URL貼り付けの3経路
4. **統合は段階的に** — N個の外部サービスを一度に繋ぐとN²の障害点が生まれる
5. **Friction削減は機能追加より優先** — 最大の敵は「記録の面倒さ」
6. **見た目のための複雑さを避ける**

### 3.2 エコの3軸

| 軸 | 意味 | 対策 |
|---|---|---|
| 金銭コスト | 月額固定費ゼロ | 自前サーバー無し。Gemini無料枠＋Drive無料枠 |
| 開発コスト | 実装・保守の手間を減らす | 単一言語(Kotlin)に集約。PC側はビューアに徹する |
| 認知コスト | 覚える技術要素を絞る | WSL2・Docker・Cloudflare Tunnel・Supabase・GASを全廃 |

### 3.3 フェーズ哲学（最重要）

> 知識管理システムの最大の敵は「完成前の疲弊」。各フェーズのゴールは次フェーズへの移行ではなく「実際に毎日使うこと」。

---

## 4. アーキテクチャ・技術スタック

### 4.1 全体構成図

```
┌─────────────────────────────────────────────┐
│ UI層 (Compose)                               │
│   15画面 / NavGraph / 型別カラー・アイコン      │
├─────────────────────────────────────────────┤
│ ViewModel層                                  │
│   StateFlow + HiltViewModel                  │
├─────────────────────────────────────────────┤
│ Repository + Brain層                         │
│   EntryRepository / SearchRepository         │
│   EmbeddingQueue / HybridSearchEngine        │
│   ConnectionEngine / MultiStageGrader / SM-2 │
├─────────────────────────────────────────────┤
│ データ層 (Room) + インフラ                     │
│   AppDatabase v5 / Ktor / Keystore           │
└─────────────────────────────────────────────┘
```

### 4.2 技術スタック

| レイヤー | 技術 | 備考 |
|---|---|---|
| Android | Kotlin + Jetpack Compose | Material3 |
| DB | Room (SQLite) v5 | FTS4仮想テーブル併用 |
| APIサーバー | Ktor (embedded, Netty) | Bearer認証 |
| DI | Hilt | Interface差し替え可能 |
| バックグラウンド | WorkManager | バックアップ・Embedding回復 |
| AI Embedding | Gemini `gemini-embedding-2-preview` | 768次元(MRL截断) |
| AI LLM | Gemini `gemini-2.5-flash`(主) / `gemini-3-flash-preview`(副) | フォールバック |
| 暗号化 | AES-256-GCM + Android Keystore | `age`の代替 |
| PCフロント | React + Vite (bun) | DBなし |

---

## 5. データベース設計

### 5.1 設計パターン：entry統一型 + Class Table Inheritance

**最大の資産**。`entry` テーブルが全13種共通フィールドを持ち、各型専用の拡張テーブルが `entry.id` を主キー兼外部キーとして持つ。

```kotlin
@Entity(tableName = "entry", indices = [...])
data class EntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,              // ← 文字列（次期改善対象 §11-8）
    val title: String,
    val content: String? = null,   // 「ユーザーの声」専用
    val isFavorite: Boolean = false,
    val isMuted: Boolean = false,  // 削除せず検索から降格
    val accessedAt: Long? = null,  // リサーフェシングの基盤
    val deletedAt: Long? = null,   // 論理削除
    ...
)
```

これにより検索・埋め込み・接続・タグ・SRSはすべて `entry.id` だけを参照すればよく、型が増えてもエンジンは変更不要。

### 5.2 13種の型

| 型 | 用途 | 拡張テーブル |
|---|---|---|
| thought | メモ・思考 | entry_thought |
| definition | 用語・単語帳 | entry_definition |
| webpage | Webページ | entry_webpage |
| book / video / document | 本・動画・文書 | 各entry_* |
| media / person / org | メディア・人物・組織 | 各entry_* |
| place / event / liked / ai_conv | 場所・出来事・いいね・AI会話 | 各entry_* |

### 5.3 横断テーブル（Brain Layer）

- `search_document` + `search_document_fts`(FTS4) — 検索・Embedding入力の集約
- `embedding` / `embedding_job` — 意味検索用ベクトル＋プロセスキル耐性キュー
- `connection` / `connection_candidate` / `connection_type_def` — 知識接続（承認制）
- `tag` / `topic` — 分類
- `srs_review` + `SrsCurrentView` — 間隔反復（履歴から都度導出）
- `quiz_bank` / `quiz_attempts` + `QuizMasteryView` — クイズ・採点・攻略度
- `ai_explanations` — AI解説キャッシュ
- `progress_events` — 進捗ログ（ヒートマップ用）
- `plugins` — クイズプラグインレジストリ
- `entry_attachment` — 添付画像

### 5.4 マイグレーション履歴

| version | 内容 |
|---|---|
| 1→2 | topic / srs_review / quiz系 + 2つのView |
| 2→3 | 11拡張型 + FTS4 + embedding系 |
| 3→4 | connection系 + ai_explanations + progress_events + plugins |
| 4→5 | entry_attachment（添付画像）|

---

## 6. Brain Layer（知能）

### 6.1 Embedding Engine

- **型別Embedding入力構築戦略**（検索品質の要）：型ごとに「何をEmbeddingに食わせるか」を個別最適化
- **レート制限付きキュー**：Gemini無料枠(100RPM)を超えないよう90RPMで制御。`embedding_job`テーブルでプロセスキル耐性
- **差分検知**：内容が変わっていなければAPIを呼ばない（コスト節約）
- **起動時回復**：`status IN ('queued','running')` を全件再投入

### 6.2 Search Engine（Hybrid Search / RRF）

| モード | 説明 |
|---|---|
| semantic | Embeddingコサイン類似度 |
| fulltext | FTS4 + Nグラム |
| hybrid（既定）| Semantic + Fulltext を RRF で統合 |
| like | SQL LIKE |

### 6.3 Connection Engine（承認制）

**Knowledge OS v10 最大の学び**。自動接続は直接 `connection` に書かず、まず `connection_candidate` に積んでユーザーが承認する。これでグラフの早期毛玉化を防ぐ。`AUTO_CONNECT_ENABLED` は既定 `false`。

### 6.4 多段階採点エンジン

```
1. 正規化完全一致 → 2. 暦・数値変換 → 3. Fuzzy(レーヴェンシュタイン)
→ 4. 同義語合成 → 5. 複数正解展開 → 6. 意味的採点(Embedding)
```

### 6.5 その他

- **ルールベースクイズ生成**（コスト0）：QA/逆引きQA/スマート4択/穴埋め
- **LLMクイズ生成**（Gemini）：`GeneratedQuiz` JSON契約
- **SM-2 SRS**：履歴から都度導出
- **リサーフェシングエンジン**（§7.5 時間軸）：自動削除は絶対に行わず「整理しませんか」の提案のみ
- **タグ表記揺れサジェスト**：Levenshtein距離で類似タグを提案
- **Trie木オートリンカー**：最長一致優先で他エントリータイトルを検出（閲覧時のみ・connectionには書き込まない）

---

## 7. UI/UX仕様

### 7.1 実装済み画面（15）

Dashboard / Search / EntryDetail / EntryEdit（全13型）/ ThoughtEdit / DefinitionEdit / QuizEdit / SrsReview / Quiz / Stats / ConnectionCandidates / Connections / Settings / Import / DatabaseManagement

### 7.2 主要入力経路

- **クイック追加（FAB）**：全13型グリッド + URLスクレイプ
- **Android共有メニュー連携**：OS標準共有シートから即座に取り込み（capture frictionを最も下げる最重要機能）

### 7.3 型別カラーシステム

| 型 | カラー | 型 | カラー |
|---|---|---|---|
| webpage | Blue #3B82F6 | person | Pink #EC4899 |
| thought | Purple #8B5CF6 | place | Teal #14B8A6 |
| book | Amber #F59E0B | event | Orange #F97316 |
| video | Red #EF4444 | ai_conv | Indigo #6366F1 |
| definition | Green #10B981 | liked | Rose #F43F5E |

---

## 8. バックアップ・データ永続性

### 8.1 設計（3層）

| 層 | 内容 | 頻度 | 現状 |
|---|---|---|---|
| 完全バックアップ | SQLiteファイルの暗号化コピー | 毎日1回 | 🟡 **ローカルのみ（Drive未実装）** |
| 可搬バックアップ | Markdown/CSV/JSONエクスポート | 週1回 | 🟡 ローカルのみ |
| スキーマメタ情報 | カラム型・バージョン記録 | エクスポート時同梱 | ⚪ 未実装 |

### 8.2 暗号化

AES-256-GCM + Android Keystore。出力形式 `[12-byte IV][ciphertext+tag]`。

### 8.3 データ永続性の保証

- 論理削除（`deletedAt`）を全entryに適用。物理削除はしない
- オープン標準（SQLite/Markdown/CSV/JSON）を維持
- 「UIが消えてもデータは無傷」原則

---

## 9. 実装状況マップ

### 9.1 完全に実装済み

| 領域 | 実装 |
|---|---|
| データモデル | entry統一型 + 13拡張型（CTI）・全横断テーブル・マイグレーション5まで |
| 検索 | FTS4+Nグラム・Gemini Embedding・Hybrid RRF・ブルートフォースベクトル |
| 接続 | 候補承認制・手動接続・グラフ探索（WITH RECURSIVE）|
| 学習 | SM-2 SRS・多段階採点・意味的採点・ルールベース+LLMクイズ生成・攻略度 |
| 知能 | コーチング・リサーフェシング・タグ表記揺れ・Trie自動リンク・型別Embedding構築 |
| 連携 | Webスクレイパー2段階・CSV/MD/JSON/URL/Obsidianインポート・Ktor API・共有メニュー |
| 永続性 | AES-256-GCM暗号化バックアップ・可搬エクスポート・論理削除・添付画像 |
| プラグイン | Rhinoエンジン・マニフェスト検証・builtin-mcq |

### 9.2 設計のみ・未実装

| 項目 | 現状 |
|---|---|
| **Google Drive 同期** | `BackupWorker` に `// TODO Phase 1.5` のみ。`drivesync/` パッケージ自体が存在しない |
| FSRS移行 | SM-2のみ |
| PC Webアプリ本体 | `package.json` のみで本体なし |
| ブロックエディタ | Markdown簡易レンダリングのみ |
| 数値可変問題 / ゲーミフィケーション | フィールドのみ、ロジック未実装 |

### 9.3 意図的に「やらない」こと

- sqlite-vec / FTS5 移行（個人規模ではブルートフォース+FTS4で十分）
- Playwrightスクレイプ（Chromium依存でAndroid不向き）
- 自動接続の直接書き込み（必ず候補→承認を経る）
- 自動削除・自動mute（提案のみ）

---

## 10. クリティカルレビュー（設計と実装のギャップ・技術的負債）

> レッドチーム／デビルズアドボケイト視点での指摘。次期担当者がまず把握すべき危険箇所。

### 10.1 🔴 高優先度（データ整合性・セキュリティ・要件充足）

| # | 問題 | 詳細 | 影響 |
|---|---|---|---|
| 1 | **バックアップが端末内完結** | `BackupWorker` は `filesDir/backups/` に暗号化コピーを作るのみ。Drive アップロードはTODOコメントのみ。**端末紛失・故障で全データ消失** | コア要件2（Data-Permanent）未充足。最優先 |
| 2 | **APIキー平文保存** | `SettingsRepository` は DataStore 平文。設計書の EncryptedSharedPreferences 未採用 | セキュリティリスク |
| 3 | **N+1クエリ** | `HybridSearchEngine.hybridSearch` が結果1件ごとに `getById` を**2回**（recency判定+muteフィルタ）。50件なら100クエリ。`EntryExporter` も同様にタグをループ内で取得 | 大量データで顕著に遅い |
| 4 | **スレッドセーフティ欠如** | `InMemoryVectorIndex` は `topK` が読む配列を `addVector` が別スレッドで書き換え。`GeminiClient` のレートリミッタ `lastCallAt` もコルーチン間で競合 | 中途半端な状態の読み取り・競合の可能性 |
| 5 | **単体テストが一切ない** | 採点エンジン・SM-2・Nグラムトークナイザー等、正確性が致命的なロジックにテストゼロ | リグレッション検知不能 |

### 10.2 🟡 中優先度（正確性）

| # | 問題 | 詳細 |
|---|---|---|
| 6 | **CoachingEngine が topicId を無視** | `analyzeWeakPoints(topicId)` が `getWrongQuizzes()`（全局）を呼び、引数が死んでいる |
| 7 | **和暦テーブルが不完全** | `japaneseEras` は近現代5元号（令和〜明治）のみ。設計書が例示する「1600年=慶長5年」は採点できない |
| 8 | **元号→西暦オフセットが不正確** | `baseYear + yearInEra` だが「明治元年=1868」であり、baseYear(1867)+1 で偶然合うだけの危うい実装 |
| 9 | **SM-2 repetitionCount の脆弱な導出** | `srs_review` が履歴のみのため、間隔日数から反復回数を推定。「たまたま1日」と「初回」を区別できない |
| 10 | **MainActivity の StateFlow 代入** | `incomingNavigation.pendingEntryId.value = id` は読み取り専用 StateFlow への代入で本来コンパイルエラー。正しくは `setPendingEntry()` / `clear()` |

### 10.3 🟢 低優先度（リファクタ・保守性）

| # | 問題 | 詳細 |
|---|---|---|
| 11 | **type が文字列リテラル** | `"webpage"` `"definition"` 等が数十箇所に散在。typoがコンパイル時に検出されない |
| 12 | **EntryEditViewModel の肥大化** | 13型分の保存ロジックを1ファイルで抱え、1ファイル1000行以下原則に接近 |
| 13 | **マジックストリング** | `"__UNLEARNED__"` が採点ロジックとUIの双方にハードコード |
| 14 | **FTS外部コンテンツテーブル同期** | `search_document_fts` が独立FTS4で rowid を手動同期。同期漏れで検索と実データが食い違う可能性 |

---

## 11. 改良設計（v12.0での変更点）

> §10 の批判に対する建設的な対応設計。次期担当者が実装すべき改良版。

### 11-1. バックアップの端末外退避（最優先）

**現状**：ローカル `filesDir` のみ。
**改良**：Drive `backups/db-snapshots/` へのアップロードを実装。

```
設計:
- drivesync/ パッケージを新設
- DriveSyncManager.upload(encryptedFile) を BackupWorker の TODO に接続
- Google Drive API の無料枠(15GB)を利用（エコの3軸に適合）
- 機種変更時は Keystore 鍵のエクスポート手順をアプリ内に明示
```

**代替（Drive API を避ける場合）**：SAF（Storage Access Framework）でユーザーが任意のクラウド同期フォルダを指定し、そこに暗号化ファイルを出力する方式。APIキー・審査が不要で即実装可能。

### 11-2. APIキー暗号化

```kotlin
// SettingsRepository を EncryptedSharedPreferences 版へ差し替え
// あるいは既存の BackupEncryptor が使う Keystore キーを流用して
// DataStore の値を AES-GCM で暗号化
```

### 11-3. N+1クエリ解消

```kotlin
// EntryDao に一括取得を追加
@Query("SELECT * FROM entry WHERE id IN (:ids)")
suspend fun getByIds(ids: List<String>): List<EntryEntity>

// HybridSearchEngine: allIds を一括取得して Map 化し、
// recency 判定・mute フィルタの両方で再利用（getById は1回も呼ばない）
// EntryExporter: タグを entryIds で一括取得して Map 化
```

### 11-4. スレッドセーフ化

```kotlin
// InMemoryVectorIndex: Mutex で load/topK/addVector を保護、
// または immutable snapshot の参照差し替え方式に変更
// GeminiClient: lastCallAt を Mutex で囲む
```

### 11-5. テスト基盤の導入

最優先でテストを書くべき箇所：
1. `MultiStageGrader`（正規化・Fuzzy・和暦変換・複数正解展開）
2. `Sm2Algorithm.calculate`（grade別のinterval/ease導出）
3. `NgramTokenizer`（bi-gram分割・FTSクエリ構築）
4. `cosineSimilarity` / RRF マージ

### 11-6. CoachingEngine の topicId 反映

```kotlin
// QuizDao にトピックフィルタ付きクエリを追加し、
// analyzeWeakPoints が topicId を渡すように修正
@Query("""
    SELECT qb.* FROM quiz_bank qb
    LEFT JOIN entry_topic et ON et.entryId = qb.sourceEntryId
    WHERE qb.id IN (SELECT quizId FROM quiz_attempts WHERE isCorrect = 0)
    AND (:topicId IS NULL OR :topicId = 'all'
         OR qb.topicId = :topicId OR et.topicId = :topicId)
    ORDER BY RANDOM() LIMIT :limit
""")
suspend fun getWrongQuizzesByTopic(topicId: String?, limit: Int = 20): List<QuizBankEntity>
```

### 11-7. 和暦マスタの整備

```kotlin
// 近現代5元号に加え、歴史的元号を「元年の西暦」で正確に保持
// 例: "明治" to 1868, "大正" to 1912, "昭和" to 1926,
//     "平成" to 1989, "令和" to 2019, "慶長" to 1596, ...
// baseYear + yearInEra ではなく、元年西暦 + (yearInEra - 1) で計算
```

### 11-8. type の定数化

```kotlin
// 文字列リテラルの散在を解消
object EntryTypes {
    const val THOUGHT = "thought"
    const val DEFINITION = "definition"
    const val WEBPAGE = "webpage"
    // ... 全13型
}
// when(entry.type) は EntryTypes.WEBPAGE 等で参照
```

### 11-9. EntryEditViewModel の分割

型ごとの Mapper/Saver に分離し、1ファイル1000行以下原則を回復。例：`EntrySaver` interface + 型別実装、あるいは拡張関数群への委譲。

### 11-10. マジックストリングの排除

```kotlin
// "__UNLEARNED__" を定数化または enum/null 化
object QuizAnswer { const val UNLEARNED = "__UNLEARNED__" }
```

### 11-11. FTS外部コンテンツテーブル化

```kotlin
// 手動 rowid 同期から、Room の @Fts4(contentEntity = ...) による
// 外部コンテンツテーブル化を検討。同期漏れリスクを排除
```

### 11-12. SM-2 状態導出の改善

repetitionCount の推定を改善するか、FSRS移行時に `srs_review` へ `stability`/`difficulty` 相当カラムを追加して状態を明示保持。

---

## 12. ロードマップ・優先順位

> 「最適が変わるかもしれない」ことへの備え：以下は現状の最善推定。計測して裏切られたら変えてよい。ただし**変更前にテストを書くこと**（テストゼロが最大のリスク）。

### 第1優先：守りを固める（データを守る）

1. バックアップの端末外退避（Drive or SAF）
2. APIキー暗号化
3. MainActivity の StateFlow 代入バグ修正＋ビルド確認
4. テストの土台づくり（採点エンジンとSM-2に単体テスト）

### 第2優先：性能・正確性

5. N+1クエリ解消（検索・エクスポート）
6. VectorIndex/GeminiClient のスレッドセーフ化
7. CoachingEngine のtopicId、和暦マスタ、SM-2導出の修正

### 第3優先：負債返済（リファクタ）

8. type 定数化
9. EntryEditViewModel 分割
10. FTS外部コンテンツテーブル化

### 第4優先：機能拡張（Phase 4）

11. ホワイトボード（txt.md のノード関係可視化）
12. FSRS移行（スキーマ追加を伴う）
13. Notion / Obsidian 追加インポート
14. AUTO_CONNECT_ENABLED 有効化の判断（§5.5.3の3条件を満たしてから）

---

## 13. 引き継ぎ事項・注意事項

### 13.1 まず確認すべきこと

- [ ] `MainActivity` のビルドが通るか確認（StateFlow代入バグ）
- [ ] バックアップが**端末外**に出ているか確認（現状出ていない可能性大）
- [ ] APIキーの保存場所を確認（平文のはず）
- [ ] `AUTO_CONNECT_ENABLED` が安易にtrueになっていないか確認
- [ ] テストがゼロであることの認識共有

### 13.2 設計の到達点（総括）

本書は、7系統の学習支援システム計画（Supabase案・GAS/LearningSheet v25・LearningMasterMap・ALH Omni-Master v26/All-Specification v5.2・Personal Knowledge OS v10・KnOS EX・quizstudy/lumina_pkm/txt.md）を評価・統合したもの。ベースはAndroidネイティブ最新版であるPersonalEncyclopediaとし、その「データ主権をAndroidに置く」というエコで壊れにくい骨格は変更していない。

### 13.3 壊してはいけない不変の骨格

1. **データ主権はAndroid**。PCもDriveもデータを持たない
2. **接続は承認制**。自動接続は candidate 経由のみ
3. **論理削除**。物理削除はしない
4. **entry統一型 + CTI**。型が増えてもエンジンは変えない
5. **Friction削減優先**。機能追加より入力のしやすさ

### 13.4 クリティカルフレンドとしての最後の一言

> このコードベースは「設計思想の解像度」が非常に高く、承認制接続・CTI・差分検知Embeddingなど、考え抜かれた資産が多い。一方で**「動くことの保証」（テスト）と「データの実保護」（バックアップ・暗号化）が設計の理想に追いついていない**。次期担当者が最初にやるべきは機能追加ではなく、**この2つの穴を塞ぐこと**。それが「一生使う基盤」というこのプロジェクト本来の約束を守る道である。

---

*本書は実装と同期して更新される。実装が本書と乖離した場合は、まずコードを正とし、§9・§10 を更新すること。*

---
