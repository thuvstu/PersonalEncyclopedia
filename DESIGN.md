# PersonalEncyclopedia 設計全解説

> 本ドキュメントは、`PersonalEncyclopedia` プロジェクトの全ソースコード・DBスキーマ・ビルド定義・ドキュメント群を横断的に調査し、**現在の実装を正として**設計を解説するものです。
> 対象リビジョン: `master` (最新: `83b4c00`)

---

## 目次

1. [プロジェクト概要](#1-プロジェクト概要)
2. [設計思想・7つの原則](#2-設計思想・7つの原則)
3. [全体アーキテクチャ](#3-全体アーキテクチャ)
4. [技術スタック](#4-技術スタック)
5. [データ層 (Room DB)](#5-データ層-room-db)
6. [脳層 (Brain Layer)](#6-脳層-brain-layer)
7. [サーバー層 (Ktor)](#7-サーバー層-ktor)
8. [Webクライアント (React)](#8-webクライアント-react)
9. [インポート・バックアップ・プラグイン](#9-インポート・バックアップ・プラグイン)
10. [UI層 (Compose + MVVM)](#10-ui層-compose--mvvm)
11. [テスト戦略](#11-テスト戦略)
12. [設計パターン集大成](#12-設計パターン集大成)
13. [注目点・課題・将来性](#13-注目点・課題・将来性)

---

## 1. プロジェクト概要

**PersonalEncyclopedia** は「**学んだことを1か所に貯めて、検索・つなげて・反復練習する**」ための個人用ナレッジマネジメントアプリです(`docs/guide/00-overview.md`)。

コンセプト上の最重要原則は「**データ主権を Android に置く**」(設計書 §1.2) です。知識データは Android 端末内の Room DB に**だけ**存在し、PC の Webクライアントは DB を持たず、Android 内蔵の Ktor サーバー経由でアクセスします。「PC=閲覧・操作端末、Android=データ本体」という関係を崩さない設計です。

### 1.1 3つの核機能

| 核機能 | 実装 | 特徴 |
|---|---|---|
| **貯める** (Knowledge) | `entry` テーブル + 13タイプ拡張テーブル | メモ/単語帳/Webページ/本/動画/人物/場所/出来事…を統一型+CTIで扱う |
| **検索・つなげる** (Search & Connect) | `HybridSearchEngine` / `ConnectionEngine` | RRF融合ハイブリッド検索 + 知識グラフ接続 |
| **反復練習する** (Review) | `Sm2Algorithm`/`FsrsAlgorithm` + `QuizGraderService` | 間隔反復 + マルチステージ採点クイズ |

### 1.2 開発フェーズの歴史 (git log から)

| フェーズ | コミット | 内容 |
|---|---|---|
| 基盤構築 | `d6385c8` 〜 | エントリモデル・検索・SRS・DB設計 (§1〜§12) |
| 本格化 | `388bb93` など | クイズバリエーション・Webクライアント・開発者ガイド8ファイル |
| 新採点システム試作 | `05029ec` 〜 `cb92311` | rubric採点 (C1〜C7: データモデル→解析器→エンジン→統合→ガイド) |
| クイズ最適化 | `21fc535` 〜 `91d175c` | 排他出題分類・演習設定・採点共通化 (R1〜R7) |

コード内に `★最適化R1〜R7`、`★新採点システムC1〜C7`、`★D1`、`★GAP-5` などのマーカーと設計書の節番号 (`§8.4`, `§12.7`) が散在し、**要件トレーサビリティ**が実装コード内に刻まれています。

---

## 2. 設計思想・7つの原則

調査を通じて浮かび上がった、このプロジェクトを貫く設計原則です。

### 原則1: データ主権は Android に置く
- 全データは端末内 `encyclopedia.db` にのみ存在。Webクライアントは「DBを持たないビュー」(`web/` 全体が Ktor API の薄いクライアント)。
- サーバーは**常時起動ではなく明示的ON/OFF** (設計書 §4.3)。設定画面の Switch で制御し、`LocalServer.start(port)` を呼ぶまでLAN公開されない。

### 原則2: 決定論を最優先、AIは「決定論が判断できない部分」だけ担当
- 採点・検索・重複判定の一次判定はすべて規則・正規表現・コサイン類似度などの決定論ロジック。
- AI (Gemini/Ollama) はあくまで「判断の補助・最終審判 (LLM judge)」で、**API未設定時も機能がフルに動く**(graceful degradation)。
  - 例: `RubricJudge` はLLM不可時 `heuristic` へフォールバック (`RubricJudge.kt:41-51`)、`CoachingEngine` は固定文案を生成 (`CoachingEngine.kt:124-128`)。

### 原則3: 一覧・履歴・最新状態の分離
- **履歴は無制限に貯め、最新状態は DB ビューで導出**する。
  - `srs_review` は全履歴を保存し、`SrsCurrentView` が `MAX(reviewedAt)` で最新スナップショットを提供。
  - `quiz_attempts` から `QuizMasteryView` が最高スコアを導出。
- これにより**アルゴリズム(SM-2/FSRS)の差し替えが履歴を壊さずに可能**。

### 原則4: 手続きは共有サービスに一元化
- 採点は `QuizGraderService` に統一(**★最適化R6**)。アプリ (`QuizRepository`) と Ktor サーバー (`QuizRoutes`) が**同一インスタンスの同一ロジック**を共有し、採点結果の食い違いを構造的に排除 (`ServerDependencies.kt:27-28`)。
- 埋め込みは `EmbeddingQueue.enqueue(entryId)` だけ呼べば、FTS更新→ベクトル化→メモリ索引反映まで非同期で完結。

### 原則5: 自動処理は必ず「提案→承認」の承認制
- 自動接続候補は `connection_candidate` に積み、ユーザーが approve して初めて正式な `connection` になる (設計書 §5.5.3、「毛玉化」防止)。
- `ResurfacingEngine` は**絶対に自動削除・自動muteしない**。「提案のみ、整理の判断は人間」(`ResurfacingEngine.kt:14-18`)。

### 原則6: 失敗しても死なない起動・フェールセーフ設計
- 起動は `runStep` で3フェーズを**個別try-catch**し、1箇所の失敗で起動全体が止まらない (`PersonalEncyclopediaApp.kt:57-63`)。
- 外部API呼び出しには全てフォールバック・リトライ・レートリミットが存在 (表は§6.6)。

### 原則7: 純粋関数・JVMテスト可能な構造
- 状態を持たないエンジンは `object` (NgramTokenizer, Sm2Algorithm, RuleBasedQuizGenerator, rubric解析器群)。
- `PortableExportWorker` は「suspendでデータ取得 → 純粋関数で書き出し」を明示 (`PortableExportWorker.kt:59-69`)。
- 9本のJVMユニットテストが brain 層ロジックを端末なしで検証。

---

## 3. 全体アーキテクチャ

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              PC (Webクライアント)                            │
│  React 19 + Vite 8 + TypeScript + React Flow (@xyflow/react)                │
│  web/src  ── DBを持たない。fetch + Bearer Token で Ktor API を叩くだけ       │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │ LAN (同一Wi-Fi, 平文HTTP, ポート8080)
                                ▼
┌───────────────────────────────┼────────────────────────────────────────────┐
│                            Android アプリ (データ本体)                        │
│                                                                             │
│  ┌───────────────────┐  ┌───────────────────────────────────────────────┐   │
│  │  server/ (Ktor)   │  │  ui/ + viewmodel/ (Compose MVVM)               │   │
│  │  LocalServer      │  │  NavGraph(28ルート) → Screen → ViewModel       │   │
│  │  routes/ (8本)    │  │                                                │   │
│  └─────────┬─────────┘  └─────────────────────┬─────────────────────────┘   │
│            │    Bearer 認証 (TokenManager)     │                              │
│            ▼                                  ▼                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  repository/ (9本) — DAO を隠蔽しドメイン操作を提供                     │   │
│  └───────────────────────────┬──────────────────────────────────────────┘   │
│                              │                                              │
│  ┌────────────┬──────────────┼─────────────┬─────────────┬───────────────┐  │
│  │  db/       │  brain/      │  importer/  │  backup/    │  plugins/     │  │
│  │  Room v8   │  検索/接続/   │  スクレイプ/ │  AES-256-GCM│  Rhino (JS)   │  │
│  │  40表+2View│  SRS/採点/AI  │  Obsidian/   │  SAF/WorkMgr│  PluginEngine │  │
│  │  7本マイグ  │  rubric採点   │  重複検出    │             │               │  │
│  └────────────┴──────────────┴─────────────┴─────────────┴───────────────┘  │
│        ▲                                        │                            │
│        │ enqueue(entryId)                       │ AI (Gemini / Ollama)       │
│        └──── brain/ai/EmbeddingQueue ───────────┴─► Gemini API / Ollama /    │
│              (FTS更新→ベクトル化→InMemoryVectorIndex)    Google Search     │
└────────────────────────────────────────────────────────────────────────────┘
```

**レイヤ間の依存方向**: `ui → viewmodel → repository → {db, brain, importer, server}`。UIは brain/repository を直接触らない(MVVMの境界を厳守。唯一の例外は `EntryDetailViewModel` がAutoLinker構築用に `entryDao` を注入する箇所)。

---

## 4. 技術スタック

### 4.1 Android (Kotlin)

| 項目 | バージョン/値 | 備考 |
|---|---|---|
| Kotlin | 2.4.10 | `kotlinx.serialization` 有効 |
| AGP | 9.3.1 | |
| compileSdk / minSdk / targetSdk | 37 / 28 / 35 | |
| Compose | BOM 2026.08.00 + Material3 | `material-icons-extended` 使用 |
| Room | 2.8.4 | KSP, `exportSchema=true` |
| Hilt | 2.60.1 | + `hilt-navigation-compose` |
| Ktor (server) | 3.5.2 | Nettyエンジン, auth, content-negotiation |
| DataStore | 1.1.3 | Preferences (設定・トークン) |
| WorkManager | 2.10.0 | + Hilt統合 (`HiltWorkerFactory`) |
| security-crypto | 1.1.0-alpha06 | EncryptedSharedPreferences, KeyStore |
| OkHttp / jsoup | 4.12.0 / 1.18.3 | Webスクレイピング |
| Rhino | 1.7.15 | プラグインJSエンジン |
| pdfbox-android | 2.0.27.0 | PDFテキスト抽出 |
| その他 | navigation-compose 2.8.6, lifecycle 2.8.7, coroutines 1.10.1, androidx.startup | |

### 4.2 Web (React)

| 項目 | バージョン/値 | 備考 |
|---|---|---|
| React / react-dom | 19.2 | `StrictMode` 有効 |
| Vite | 8.0 | dev ポート 5173 |
| TypeScript | 5.6 | `strict`, `react-jsx` |
| @xyflow/react | 12.3 | 知識グラフ描画 |
| パッケージマネージャ | bun 1.3.14 | `packageManager` 指定 |
| 状態管理 | なし (素のuseState + localStorage) | zustand は推移的依存のみで未使用 |

### 4.3 ビルド構成の特徴
- `settings.gradle.kts` は `RepositoriesMode.FAIL_ON_PROJECT_REPOS` でリポジトリ宣言を中央集権。
- `packaging.resources.excludes` で Netty の `META-INF` 重複を解決 (`app/build.gradle.kts:26-41`)。
- RoomスキーマJSONは `app/schemas/` にエクスポートされ、`MigrationTest` が検証に使用。

---

## 5. データ層 (Room DB)

### 5.1 概観

- **ファイル**: `encyclopedia.db` / **バージョン 8** / `exportSchema = true`
- **40テーブル + 2ビュー**。開発フェーズ(Phase 0〜4 + v7/v8)に沿って追加された履歴が見える (`AppDatabase.kt:10-56`)。
- **マイグレーション**: 7本 (`MIGRATION_1_2`〜`MIGRATION_7_8`)、**破壊的変更ゼロ**の「新規テーブル追加 + ビュー再作成 + カラム追加」のみ。`fallbackToDestructiveMigration()` は**未設定**(データ破壊フォールバックなし)。

### 5.2 テーブル全一覧

#### Phase 0 (v1, 基盤)
| テーブル | 役割 |
|---|---|
| `entry` | **全コンテンツのマスタ**。13タイプを `type` で判別するポリモーフィック設計。共通カラム: `title/content/summary/sourceUrl/lang/isFavorite/isMuted/accessedAt/deletedAt(ソフトデリート)/metadataJson` |
| `entry_type` | 13タイプのマスタ (`name/labelJa/icon/colorHex/isActive/sortOrder`) |
| `entry_thought` | メモタイプの1:1拡張 (`mood/context/isDraft`) |
| `entry_definition` | 単語帳タイプの1:1拡張 (`term/reading/definition/field/examplesJson/relatedTermsJson`) |
| `tag` / `entry_tag` | タグ + 多対多中間テーブル |

#### Phase 1 (v2, 学習)
| テーブル | 役割 |
|---|---|
| `topic` / `entry_topic` | 2階層トピック(ジャンル/分野)。`parentId` 自己参照 + 多対多中間 |
| `srs_review` | **SM-2/FSRS復習履歴(無限)**。`grade(0-5)/intervalDays/easeFactor/nextReviewAt/repetitionCount(v8)` |
| `quiz_bank` | クイズ問題バンク。`quizType(qa/mcq/fill_blank/sort/essay/cloze/custom)/choicesJson/answer/gradingContextJson/hintsJson/generationMethod/difficulty/isActive` |
| `quiz_attempts` | 回答履歴。`isCorrect/score/gradingMethod(exact/fuzzy/semantic/llm)/hintsRevealed/answeredWithinMs(v8)` |

#### Phase 2 (v3, 13タイプ拡張 + 検索基盤)
| テーブル | 役割 |
|---|---|
| `entry_webpage` 〜 `entry_ai_conv` (10種) | 各タイプの1:1拡張。`entryId` がPK兼FK、CASCADE削除 |
| `search_document` | 全文検索用の統合テキスト (`combinedText`) |
| `search_document_fts` | **FTS4仮想テーブル**。手動同期方式(トリガーなし) |
| `embedding` | ベクトル埋め込み (`vectorBlob BLOB`, 768次元, `model` カラムでモデル別管理) |
| `embedding_job` | 埋め込みジョブキュー (`queued/running/done/failed`, リトライ回数) |

#### Phase 3 (v4/v5, 知識グラフ)
| テーブル | 役割 |
|---|---|
| `connection_type_def` | 接続タイプ定義マスタ(9種)。`isDirected`/`inverseLabelJa` |
| `connection` | **知識グラフ接続**。`strength/isAuto/isDirected` + **正準形ユニーク** `unique(canonicalA, canonicalB, relationType)` |
| `connection_candidate` | AI提案の接続候補 (`status: pending/approved/rejected`, `unique(entryAId,entryBId)`) |
| `ai_explanations` | **AI出力キャッシュ**。`unique(sourceType, sourceId)` |
| `progress_events` | 学習活動イベントログ (`viewed/edited/answered/reviewed/connected`) |
| `plugins` | プラグイン定義 (`manifestJson/scriptPath/isActive`) |
| `entry_attachment` | 添付ファイル (BLOBは`filesDir/blobs/attachments/`でDB外管理) |

#### Phase 4 (v6, アウトライナー + Wiki)
| テーブル | 役割 |
|---|---|
| `whiteboard` / `whiteboard_note` / `whiteboard_node` / `whiteboard_section` | **Heptabase型ホワイトボード**(百科の「巻」/「章」/カード配置)。`whiteboard_node` は entry か note の**排他参照** |
| `wiki_article` | 内蔵Wiki記事 (`title unique`, `contentMd`) |

#### v7 / v8
| テーブル | 役割 |
|---|---|
| `era_master` | **和暦マスタ** (天文1532〜令和2019+、56件シード)。和暦→西暦変換に使用 |
| `entry_custom_field` | カスタムフィールド (v8, §5.8.3) |

#### ビュー
| ビュー | 定義 | 用途 |
|---|---|---|
| `SrsCurrentView` | `srs_review` を `MAX(reviewedAt)` でグループ化 | 各エントリの現行SRS状態 |
| `QuizMasteryView` | `SELECT quizId, MAX(score) ... GROUP BY quizId` | クイズ別マスタリー |

### 5.3 リレーション設計の4パターン

1. **厳密FK + CASCADE (1:1拡張)**: タイプ別拡張テーブルは `entryId` をPK兼FKにし `onDelete=CASCADE`。entry を消せば拡張も消える。
2. **多対多中間テーブル**: `entry_tag`, `entry_topic` は複合PK + 両方向CASCADE。
3. **論理FK (宣言なし)**: `srs_review.entryId`, `quiz_bank.sourceEntryId`, `connection.entryAId/entryBId`, `whiteboard_node.*` はエンティティ上でFK宣言せずインデックスのみ。**削除制約より柔軟性を優先**する意図。
4. **自己参照**: `topic.parentId` (2階層)、`connection` の無向は `canonicalA < canonicalB` の正準形。

### 5.4 特徴的なテーブル設計

#### (a) 正準形によるグラフ重複排除
無向接続は `canonicalA = min(idA, idB)` に正規化して `unique(canonicalA, canonicalB, relationType)` 制約で完全重複を排除 (`ConnectionEntities.kt:20`, `DemoData.kt:114-115`)。有向/無向で重複チェックが分岐 (`ConnectionEngine.createManualConnection`)。

#### (b) FTS4 の手動同期
`search_document_fts` は外部コンテンツなしFTS4。`EmbeddingQueue` が `rowid` で `insertFts/deleteFts` を手動実行し、bigram化したテキストを格納 (`Migration2to3.kt:180-183`)。

#### (c) JSON列によるスキーマ回避
リスト/辞書は `xxxJson` TEXT列にシリアライズ (`choicesJson/authorsJson/messagesJson/manifestJson/metadataJson` など)。**頻繁なスキーマ変更を回避**する実務的パターン。

#### (d) マイグレーション内シード
`era_master` の56件は `Migration6to7.kt` 内の `insertEra()` (`INSERT OR REPLACE`) で投入。アプリ版数(v12)とDBバージョン(8)は別軸で進んでいる点に注意。

### 5.5 複雑なDAOクエリ集

| クエリ | 場所 | 内容 |
|---|---|---|
| **再帰CTEグラフBFS** | `ConnectionDao.kt:87-99` | `WITH RECURSIVE graph` で深さ制限付き幅優先探索、両方向扱い |
| **双方向接続取得** | `ConnectionDao.kt:55-70` | `UNION ALL` で A→B / B→A を1クエリに |
| **SRS期限到来** | `SrsReviewDao.kt:14-34` | entry×definition JOIN + 最新レビューサブクエリ、未受験を優先ソート |
| **排他出題分類(3本)** | `QuizDao.kt:54-108` | 苦手(不正解あり&正解なし)/未習(履歴なし)/未マスタリーランダム |
| **FTS全文検索** | `SearchDocumentDao.kt:22-38` | FTS4 MATCH + rowid で search_document に JOIN |
| **日次集計** | `ProgressEventDao.kt:26-56` | `strftime('%Y-%m-%d', createdAt/1000, 'unixepoch', 'localtime')` でヒートマップ/ストリーク用集計 |
| **N+1回避の一括取得** | `TagDao.kt:37-43`, `WhiteboardDao.kt:75-79` | `IN (:ids)` バッチクエリ |

---

## 6. 脳層 (Brain Layer)

`brain/` は7つのドメインで構成される「知能系エンジン群」(約3,400行)。DIは `@Singleton` + `@Inject constructor`、無状態エンジンは `object`。

```
brain/
├── search/     HybridSearchEngine, InMemoryVectorIndex, NgramTokenizer, EmbeddingTextBuilder
├── srs/        Sm2Algorithm, FsrsAlgorithm
├── quiz/       RuleBasedQuizGenerator, LlmQuizGenerator, MultiStageGrader,
│               QuizGraderService, SemanticGrader, EraConverter, NumericVariantEngine
│   └── rubric/ RubricParser, RubricFeatureExtractor, RubricConfidence, RubricJudge,
│               RubricGrader, KeywordMatcher, NumericUnitVerifier, PolarityAnalyzer,
│               RelationDirectionChecker, TextNorm, RubricModels
│       └── provider/  GradingProviders(I/F), GradingProviderModule(Hilt), GeminiGradingProviders
├── ai/         GeminiClient, OllamaClient, FactCheckEngine, EmbeddingQueue, AiModels
├── connection/ ConnectionEngine
├── coaching/   CoachingEngine
└── (top)       ResurfacingEngine, TagSuggestionEngine
```

### 6.1 検索ドメイン (search/)

#### NgramTokenizer — 日本語を分割する工夫
形態素解析器を導入せず、**重複2文字バイグラム**方式で日本語を扱う (`NgramTokenizer.kt:4-7`)。`tokenize()` で大文字化・全角空白統一のうえ重複bigramを空白区切りで生成、`buildFtsQuery()` でクエリ側もbigram+OR接続の部分一致クエリを構築。

#### InMemoryVectorIndex — 無ロック・スナップショット
個人規模(数千件 × 768次元 ≈ 30MB)を想定し、**メモリ総当たり検索**(数十ms)を選択 (`InMemoryVectorIndex.kt:10-18`)。スレッド安全性は **★D1** として「不変 `Snapshot` + `AtomicReference` + CASループ」で実現(`InMemoryVectorIndex.kt:28-37, 71-100`)。`topK` は固定スナップショットを読むため、`addVector` と非干渉。

#### HybridSearchEngine — RRF融合検索
`SearchMode` = `HYBRID / FULLTEXT / SEMANTIC / LIKE` の4モード (`HybridSearchEngine.kt:36-52`)。

```
LIKE   → entryDao.search (名次スコア 1/(RRF_K+rank+1))
FULLTEXT → bigram FTS4 MATCH (名次スコア)
SEMANTIC → Gemini embed → コサイン類似度(生値をスコア化)
HYBRID  → 両者の順位を Reciprocal Rank Fusion で統合
           rrfScore = 1/(60+fulltextRank) + 1/(60+semanticRank)
           recencyBoost: 7日以内 +0.05 / 30日以内 +0.02 (作成日ベース)
           その後 isMuted / deletedAt をフィルタ
```

**RRFの利点**: スコアの尺度(次元)が違う全文/意味を**順位という共通尺度に落として融合**するため、正規化不要。`RRF_K=60` は標準値。

### 6.2 SRSドメイン (srs/)

**SM-2 と FSRS-4.5 の並行実装**。`srs_review` に履歴だけを貯める設計のおかげで、アルゴリズムを差し替え可能(`SrsRepository.recordReview` が `settingsRepo.srsAlgorithm` で分岐)。

| アルゴリズム | 特徴 |
|---|---|
| `Sm2Algorithm` | 定番のSM-2。grade 0-5。失敗で10分後再学習、ease下限1.3、`repetitionCount` は成功+1/失敗リセット(v8, §5.8.5) |
| `FsrsAlgorithm` | 公式FSRS-4.5(目標保持率90%, `DECAY=-0.5`, 重みw[0..16])。D0の平均回帰、可読性 `R(t,S)=(1+FACTOR·t/S)^DECAY`。`sm2GradeToFsrs` で SM-2 grade(0-5)→FSRS grade(1-4) に橋渡し、**gradeはSM-2基準で履歴保存**(互換性維持)。`easeFactor=difficulty` / `intervalDays=stability` と旧テーブルにマッピング |

### 6.3 クイズドメイン (quiz/)

#### 出題 (2系統)
- **`RuleBasedQuizGenerator`**: 定義エントリから**コスト0**で4形式生成 (`generationMethod="rule_based"`)。
  - QA「termの定義」、逆QA「定義→term」、MCQ(同分野の定義3つを誤答)、穴埋め(termを`＿＿＿`に)。
  - **生成時点で rubric 用 `gradingContextJson` を同梱**(★最適化R5)。ルール出題も新採点システムに対応。
- **`LlmQuizGenerator`**: Gemini にJSONモードで `count=3` 問を生成。既存同文は `countByQuestion` で重複スキップ。記述式は正解をkeyword+modelAnswersとして `gradingContextJson` に格納。
- **`NumericVariantEngine`**: 数値計算問題のバリエーション生成。パラメータを `min + Random.nextInt(0,steps+1)*step` で抽出し、**解答式を Rhino サンドボックス(`optimizationLevel=-1`)で実行**。NaN/例外はnullで安全失敗。

#### 採点 (3段階パイプライン) — 本プロジェクト最大の見どころ
**`MultiStageGrader`(決定論)** → **`RubricGrader`(試作)** → **`SemanticGrader`(意味)** の順に実行し、後段は前段が不正解のときのみ介入。**rubricが正解と判定した時だけ正解に昇格**(安全な試作統合) (`QuizGraderService.kt:46-57`)。

```
QuizGraderService.grade() (★最適化R6: App⇔サーバー共通)
│
├─ 1. MultiStageGrader (exact/strict/lenient/exactモード)
│     ├─ normalize: 全角→半角、括弧・句読点統一、小文字化
│     ├─ expandAnswers: 「A(オプション)」「A/B」展開
│     ├─ 数値: parseYear(和暦→西暦 via era_master, 紀元前=負数, 誤差1e-9)
│     │        ※ 和暦風表記で era_master に無い場合は undeterminable(不一致にしない)
│     ├─ 同義語表 (ww1/ww2/usa/uk)
│     └─ 曖昧: Levenshtein 正規化類似度 (strict 0.85 / lenient 0.70)
│
├─ 2. RubricGrader (qa/essay + 5文字以上 + 非__UNLEARNED__ のとき)
│     └─ rubric.isCorrect なら score=max(rubric.score, 既存) に昇格, method="rubric"
│
├─ 3. SemanticGrader (不正解 + qa/essay + API利用可)
│     └─ コサイン ≥0.85 で正解に昇格, method="semantic"
│
└─ スコア計算
     ├─ 正解:   max(0, 1 - 0.3×hintsRevealed)        (ヒント1個で-30%)
     ├─ 未習(__UNLEARNED__): 0 (isCorrect=null)
     ├─ 不正解: -1.0
     └─ 速度ボーナス(§8.7.3, Kahoot由来): 10秒未満で (1-elapsed/10s)×0.5 最大+50%
```

### 6.4 Rubric採点システム (quiz/rubric/) — 新採点システム試作

設計根拠は `docs/新採点システム.txt`。「**Rubric分解 → feature抽出 → 最終LLM judge**」のパイプライン。**誤答と判定不能を混同しない**哲学が中核 (`RubricModels.kt:79`)。

#### 判定軸 (`RubricKind`) — 6軸
`KEYWORD`(明示語一致) / `CONCEPT`(概念存在) / `NUMERIC_UNIT`(数値単位) / `RELATION`(関係の向き) / `POLARITY`(否定極性) / `EXPLANATION`(説明量)

#### パイプライン
```
gradingContextJson
   │  RubricParser.parse (中日表記ゆらぎ対応: keyword/キーワード/必須語/用語 → KEYWORD)
   │  ※ 空/不正 → autoDecompose (正解を文分割し自動rubric化、LLM不要)
   ▼
RubricFeatureExtractor — 各軸を決定論解析器で signal 化
   │  KEYWORD    → KeywordMatcher (部分一致/誤字許容)
   │  CONCEPT    → Embeddingコサイン(≥0.75) or bigramフォールバック
   │  NUMERIC    → NumericUnitVerifier (和暦/数値/単位換算 km/h↔m/s, °C↔°F)
   │  POLARITY   → PolarityAnalyzer (否定極性の反転検出)
   │  RELATION   → RelationDirectionChecker (7正規表現で関係の向きを抽出)
   │  EXPLANATION→ 長さ比ヒューリスティック(真の評価はLLM judgeへ)
   ▼
RubricConfidence
   │  overallConfidence = 重み付き平均
   │  deferToLlm = 矛盾シグナル || 判定不能シグナル || confidence < 0.7
   ▼
RubricJudge (LLM judge or heuristic)
   │  LLM: evidence JSON を提示し最終判定 (極性/関係反転は内容が正しくても「不正解」寄りに)
   └  heuristic: 重み付き平均 ≥0.6 で正解 + rationale 生成
```

#### 決定論解析器の3つのハイライト
1. **`PolarityAnalyzer`** — Embeddingでは「位置する」vs「位置しない」を区別できない問題を、**否定パターンの長さ降順マッチ** + **非否定パターン(「ではないか」「なければならない」)の先マスク** + **否定作用域の肯定形復元**(「位置しない」→「位置する」)で解決。二重否定等はLLM judgeへ defer。
2. **`RelationDirectionChecker`** — 「AよりBが大きい」「AがBの原因」「A→B」等7パターンの正規表現で `RelationTuple(termA, termB, relation, direction)` を抽出し、**反転tuple**を検出。bigram重なり(≥0.6)でterm対応。
3. **`NumericUnitVerifier`** — 和暦→数値→**単位換算**(km/h↔m/s, m, kg, s のグループ + °C/°F)の順で検証。「72km/h = 20m/s」判定が可能。片側に数値なし=判定不能(null)。

#### プロバイダー層 — モデル差し替え点
`IEmbeddingProvider / IEntailmentProvider / ICrossEncoderProvider / IJudgerProvider` の4インターフェース(**契約: 不可用はnull**)。`GradingProviderModule`(Hilt) が全て `GeminiGradingProviders` にバインド。**このモジュールを差し替えるだけで端内モデル(Qwen3-NLI等)へ移行可能**(`GradingProviderModule.kt:11-15`)。LLMの乱出力は**括弧深度解析器 `extractJsonObject`** で最初のJSONオブジェクトを取り出す堅牢化。

### 6.5 AIドメイン (ai/)

| クラス | 役割 |
|---|---|
| `GeminiClient` | **プロバイダールーティングFacade** (`provider: "gemini"|"ollama"`)。90RPMレートリミット(約667ms間隔)、JSONモード、grounding(Google Search連動)、**モデルフォールバックチェーン**(失敗時に次のモデルへ)。768次元埋め込み |
| `OllamaClient` | ネイティブ Ollama API (`/api/generate`, `/api/embed`, `/api/tags`)。connect 10s / read 120s、**最大2回リトライ** |
| `AiModels` | モデルレジストリ (`GeminiModelDef(id,label,supportsJson,supportsGrounding,tier)`)。埋め込みモデルは `gemini-embedding-2-preview` |
| `EmbeddingQueue` | `Channel<String>(UNLIMITED)` + IOスコープワーカー。`enqueue`→`updateSearchDocument`(FTS更新)→`processEmbedding`(3回まで再試行、成功でメモリ索引に即反映)。**変更検知**(`combinedText == inputText` でスキップ)、`recoverJobs` で再起動時自己回復、`rebuildAllSearchDocuments` で100件ページング全再構築 |
| `FactCheckEngine` | **★H-6 2段階ファクトチェック**: ①groundingで調査 ②調査結果を文脈にJSON構造化(isAccurate/correction/sources/confidence)。`ai_explanations` にキャッシュ |

**モデル混在ガード**: Gemini と Ollama のベクトルは次元・意味空間が異なるため `embedding.model` で区別し**同一モデル同士のみ比較**(設計書v14:1413)。

### 6.6 その他のエンジン

| エンジン | 役割・アルゴリズム |
|---|---|
| `ConnectionEngine` | 9種の関係タイプ定義シード。自動候補: コサイン ≥0.88 で `related` 提案。手動接続は正準形で重複回避、`approveCandidate` で正式化 |
| `ResurfacingEngine` | **再浮上**: 30〜180日未訪問×半減期(webpage 60日/definition 365日…)×指数減衰+リニア減衰の合成スコア。**整理提案**: 180日以上未訪問のwebpage/liked/ai_conv。削除・muteはしない |
| `CoachingEngine` | `explainMistake`(誤答解説) / `analyzeWeakPoints`(トピック別に誤答20件から弱点分析, 150字以内)。AI不可時は固定文案 |
| `TagSuggestionEngine` | Levenshtein 正規化類似度 ≥0.75 の既存タグを提案(表記揺れ統合) |

### 6.7 brain層の設計思想のまとめ

> **「Embedding = 内容が似ているか」「構造解析 = 正しく述べているか」の分離** (`RelationDirectionChecker.kt:12-14`)。内容の類似性だけでは「否定・関係の向き・単位」という正確性の誤りを検出できないため、決定論解析器が独立した判定軸を持つ。この思想が、このプロジェクトの採点品質の核である。

---

## 7. サーバー層 (Ktor)

### 7.1 LocalServer — 薄いエントリポイント
- `@Singleton`。Netty 埋め込みサーバー、既定ポート 8080 (設定で変更可)。
- プラグイン構成: `ContentNegotiation`(JSON, `ignoreUnknownKeys=true`, `encodeDefaults=true`) + `Authentication`(Bearer "token-auth")。
- **`/health` のみ認証なし**、他は `/api` 配下に8本のルート拡張関数を登録 (`LocalServer.kt:62-85`)。
- 設計書 §10.1「ルーティング分割アーキテクチャ」: エンドポイント実装は `server/routes/`、DTO は `server/dto/` に集約。新ルート追加は「登録行を1行足すだけ」。

### 7.2 認証 — TokenManager
- UUID トークンを **DataStore(`server_prefs`)** に永続化。`getOrCreateToken` / `regenerateToken`。
- Ktor `bearer("token-auth")` が一致を検証 → `UserIdPrincipal("owner")`。
- 設定画面でトークン表示・コピー・再発行。**TLSなし平文HTTP** の代替としてLAN警告UIを実装(設計書 A-10)。

### 7.3 API エンドポイント全一覧

| メソッド | パス | 機能 | 認証 |
|---|---|---|---|
| GET | `/health` | ヘルスチェック (`version:"0.3.0"`) | なし |
| GET | `/api/entries?limit&offset&type` | エントリ一覧(型フィルタ) | Bearer |
| GET | `/api/entries/{id}` | エントリ詳細 (**accessedAt touch**) | Bearer |
| DELETE | `/api/entries/{id}` | **ソフト削除** | Bearer |
| PATCH | `/api/entries/{id}/favorite` | お気に入りトグル | Bearer |
| GET | `/api/search?q&limit&type` | FTS全文検索 | Bearer |
| GET | `/api/srs/due?limit` / `/api/srs/count` | 復習対象一覧/件数 | Bearer |
| POST | `/api/srs/review` | SM-2復習記録 | Bearer |
| GET | `/api/quiz?limit&type` / `/api/quiz/{id}` / `/api/quiz/count` | 出題・取得・件数 | Bearer |
| POST | `/api/quiz/{id}/attempt` | **採点+履歴記録(QuizGraderService共通)** | Bearer |
| GET | `/api/connections?entryId` | 接続一覧 | Bearer |
| POST | `/api/connections` | 接続作成(**正準形で無向重複回避**) | Bearer |
| DELETE | `/api/connections/{id}` | 接続削除 | Bearer |
| GET | `/api/connection-candidates` | 承認待ち候補 | Bearer |
| POST | `/api/connection-candidates/{id}/approve` / `reject` | 候補承認/却下 | Bearer |
| GET | `/api/graph?entryId&depth` | **再帰CTEのBFSグラフ探索** | Bearer |
| GET | `/api/progress/heatmap?days` | 日別アクティビティ | Bearer |
| GET | `/api/plugins` | プラグイン一覧 | Bearer |

DTO は `ApiDtos.kt` に14種。`QuizAttemptRequest.answeredWithinMs` が速度ボーナスに対応(`ApiDtos.kt:67-71`)。

### 7.4 ServerDependencies — 「依存の束」パターン
9つのDAO + `QuizGraderService` を束ねて各ルートへ渡す (`ServerDependencies.kt:18-29`)。**★最適化R6** により採点ロジックがアプリと完全共通。

---

## 8. Webクライアント (React)

### 8.1 技術構成
- React 19 + Vite 8 + TypeScript (strict) + **@xyflow/react**。状態管理ライブラリ不使用(素の `useState` + localStorage)。
- `api/client.ts` が **モジュールレベル settings シングルトン** (`loadSettings/saveSettings/getSettings`, キー `encyclopedia_settings`)。`request<T>()` が fetch ラッパーで Bearer ヘッダ + JSON を自動付与。TS インターフェースはサーバーDTOと1:1対応。
- 型マスタ `lib/entryTypes.ts` は13型のラベル・色を Web側に重複定義(APIに型一覧エンドポイントが無いため)。

### 8.2 画面構成 (App.tsx の4タブ)

| タブ | コンポーネント | 機能 |
|---|---|---|
| エントリ | `EntryList` + `EntryDetail` + `GraphView` | 3ペイン分割。検索+型チップ絞り込み、Markdown描画、グラフ表示 |
| 単語帳 | `SrsPanel` | 復習カード → 答え表示 → grade 0-5 → 次カード |
| クイズ | `QuizPanel` | 形式チップ(qa/mcq/fill_blank)。MCQ選択/記述入力。**経過時間計測**、`__UNLEARNED__` 対応、正解+解説 |
| Ollama | `OllamaPanel` | LAN内Ollamaへ**直接** `/v1/chat/completions`。設定はlocalStorage。Android起動不要で補助AI使用可能(§7.7) |

### 8.3 GraphView — React Flow による知識グラフ
1. `/api/graph` でエッジ取得 → **N+1 で各ノードを `/api/entries/{id}` で並列フェッチ**(失敗時はID表示でフォールバック)。
2. **深さ別円環レイアウト**: エッジの depth から中心を決定し、同一深度ノードを `angle=2π·idx/count-π/2, radius=depth·240` で円周配置(`useMemo`)。
3. ノードは型色を背景に、中心は太枠。エッジは `strength` で線幅、`relationType` ラベル。

### 8.4 Markdown レンダラー — XSS対策
`lib/markdown.tsx` は**innerHTML 不使用・React要素のみ**で構築。`[[wiki-link]]` → クリックで `/api/search` により `navigateToTitle` 解決、`{語|よみ}` → `<ruby>` 表示。

### 8.5 Android⇔Web 連携フロー
```
Android設定画面: トークン表示 → PC ConnectionBar: ホスト/ポート/トークン入力
  → 「接続テスト」で /health 確認 → 「保存」で localStorage
  → 以後 fetch + Authorization: Bearer <token> で /api/*
```

---

## 9. インポート・バックアップ・プラグイン

### 9.1 インポートパイプライン (importer/)

**共通パターン**: `抽出 → 解析 → 重複排除(§12.7) → 保存` の4段階。

| 入力 | メソッド | フロー |
|---|---|---|
| CSV (単語帳) | `importDefinitionsCsv` | ヘッダ正規化で `term/reading/definition/field` 列を解決 → ContentHash/URL重複判定 → entry+definition 挿入 |
| Markdown | `importMarkdown` | `^#{1,2}\s+` でセクション分割 → thought として保存 |
| JSON | `importEntriesJson` | 型別に拡張復元。`sourceUrl` があれば URL 重複判定。**エクスポートと往復互換** |
| URLリスト | `importUrlList` | `webScraper.scrapeAndSave` へ委譲 |
| Notion | `importNotionMarkdown` | `WikiLinkParser` 抽出 → `ObsidianImporter.importNotes` に流用 |

- **`WebScraper` — 2段階フォールバック**: Stage1 で Jsoup が `<article>/<main>/[role=main]` を優先しノイズ除去(script/nav/footer等)して本文抽出 → **本文100文字未満なら Stage2 で Gemini に「要約せず全文」を返させる**(最大15000文字)。`scraperUsed` に使用経路を記録。readingTimeS は400字/秒想定。最後に `embeddingQueue.enqueue`。
- **`DuplicateDetector`**: `UrlDuplicateDetector`(sourceUrl一致) + `ContentHashDuplicateDetector`(タイトル絞り込み→正規化本文一致) を **OR合成** (`ImportPipeline.kt:38-40`)。
- **`AutoLinker` — Trie木の最長一致**: 閲覧時UI装飾のみで **connectionには書き込まない**という明示的な設計方針 (`AutoLinker.kt:9-13`)。`AutoLinkerProvider` が `@Volatile`+`Mutex` の二重チェックロッキングで最大5万件を1回だけ構築。
- **`ObsidianImporter` — 2パス方式**: ①エントリ作成(forward reference解決) ②wiki-linkごとに `references` 接続作成。

### 9.2 バックアップ (backup/)

| コンポーネント | 仕組み |
|---|---|
| `BackupEncryptor` | **AES-256-GCM**。鍵は AndroidKeyStore(`encyclopedia_backup_key`)内に保持されファイルに含まれない(デバイスバインド)。出力形式 `[12byte IV][ciphertext+tag]` |
| `BackupExporter` | **SAF経由のクラウド非依存バックアップ**(Drive API不要)。復元時はSQLiteヘッダ(`"SQLite format 3\0"` 16byte)を検証してからDB差し替え |
| `BackupWorker` | WorkManager 日次・**充電中+Wi-Fi限定**。WAL checkpoint→DBコピー→暗号化→30世代プルーニング→SAFリモート or `"LOCAL_ONLY"` |
| `PortableExportWorker` | 週次・充電中。Markdown/CSV/JSON の3形式を **純粋関数で書き出し**(テスト容易性) |
| `EntryExporter` | 手動エクスポート(MARKDOWN/CSV/JSON)。SAFへ直接書き込み。JSONはインポートと往復互換 |

### 9.3 プラグインエンジン (plugins/)

- **Rhino (Mozilla JS) エンジン**でユーザー拡張を実行。`optimizationLevel=-1`(インタプリタ)。
- `installPlugin`: 構文チェック + `manifest/grade/renderSchema` の存在検証後、`filesDir/plugins/$id.js` に保存。
- `gradeWithPlugin`(外部採点) / `getRenderSchema`(**UIスキーマ駆動レンダリング**) を提供。
- **ビルトイン**: MCQ(4択)プラグインがJS文字列で同梱、起動時に自動インストール。
- セキュリティ: 標準スコープのみのサンドボックス(Android APIブリッジなし、純関数のみ)。

### 9.4 リポジトリ層 (repository/) — 9本

| リポジトリ | 特徴 |
|---|---|
| `EntryRepository` | 最大。**共通テーブル+13種拡張の2段構造を `create*` APIで集約**。`upsertExtension(entity: Any)` が `when` で11種の拡張DAOへ型安全ルーティング。全 create が `embeddingQueue.enqueue` |
| `SearchRepository` | `HybridSearchEngine` 内包。`indexEntry`=enqueue、`rebuildAllIndices`=FTS全再構築 |
| `QuizRepository` | ルールベース一括生成、**排他分類プール**(苦手+未習+未マスタリーを `distinctBy` 合成)、採点は `QuizGraderService` に委譲 |
| `SrsRepository` | SM-2/FSRS切替。`resolvePriorRepetitionCount` でv8移行前データを間隔日数から推定 |
| `ConnectionRepository` | `ConnectionEngine` にビジネスロジック委譲。`traverseGraph` は再帰CTE |
| `SettingsRepository` | **DataStore(一般) + EncryptedSharedPreferences(機密) の2層**。`initApiKey` が平文→暗号化のワンタイム移行 |
| `WhiteboardRepository` / `WikiRepository` / `AttachmentRepository` | 各機能のCRUD。Attachmentは物理コピー+DB登録、削除は両方 |

### 9.5 DI (Hilt)

- `DatabaseModule`: Room DB + **20個のDAOを個別 @Provides**。
- `ServerModule`: TokenManager 提供。
- `PersonalEncyclopediaApp` が Phase A(DB/シード) → Phase B(Brain層) → Phase C(WorkManager) の**3フェーズ段階的初期化**を `runStep` で実行。

---

## 10. UI層 (Compose + MVVM)

### 10.1 起動フロー
```
AndroidManifest: Application=PersonalEncyclopediaApp, MainActivity=singleTop
PersonalEncyclopediaApp.onCreate ─┬─ Phase A: APIキー暗号化移行 / seedTypeDefs / ビルトインプラグイン / DemoData / SeedData
                                  ├─ Phase B: vectorIndex.load / recoverJobs / startWorker / rebuildAllSearchDocuments
                                  └─ Phase C: BackupWorker / PortableExportWorker スケジュール
MainActivity.onCreate → handleIncomingIntent (ACTION_SEND: URL→scrape, テキスト→thought)
  → setContent { EncyclopediaTheme { MainContent } }
```

- **共有インテント対応**: 他アプリからURL/テキストを受け取り、スクレイプ or メモ作成 → `IncomingNavigation.setPendingEntry(id)` → Compose側 `LaunchedEffect` が監視して `entry/$id` へ遷移 (`MainActivity.kt:81-88`)。**Activity→Compose Navigationの橋渡しキュー** (§11.4)。

### 10.2 ナビゲーション — 28ルートの単一NavHost
- `Routes` オブジェクトに28ルート定義、フラットな単一 NavHost (`NavGraph.kt:43-254`)。`startDestination = DASHBOARD`。
- ボトムバー5画面: ホーム/検索/復習/クイズ/統計。トップレベル遷移は `popUpTo(DASHBOARD)` でスタックを畳む。
- エディタ保存後は `popBackStack(); navigate("entry/$id")` の共通パターン。
- 型別編集は `EntryDetail` が type で `thought/edit`/`definition/edit`/`entry/edit/$type` にディスパッチ。

### 10.3 画面一覧 (20+ スクリーン)

| 画面 | 機能 |
|---|---|
| Dashboard | 統計カード/復習・クイズ・白板・Wikiへの導線/最近追加/クイック追加ダイアログ(URL取込+13型グリッド)/接続候補バッジ |
| EntryDetail | 型バッジ+リッチ本文+wiki-link→`EntryPreviewPopup`/タグ(表記揺れ提案)/接続管理(関係タイプ+強度スライダー)/クイズ自動生成/記事化 |
| EntryEdit | **全13型を1画面でカバーする統合エディタ**。`when(type)` で61フィールドの `EntryFormState` を分岐 |
| Search | 4検索モードチップ+型フィルタ、400msデバウンス |
| Quiz | 通常/サバイバル(1問ミスで終了)/プレッシャーテスト(全列挙) の3モード。ヒント段階開示/MCQ正誤強調/**rubric採点根拠カード**/中断確認 |
| SrsReview | SM-2/FSRSフラッシュカード、`RubyText` で読み仮名表示、4段階評価 |
| Stats | ストリーク/学習日数/12週間ヒートマップ/`CoachingEngine` 弱点分析 |
| Import | CSV/MD/JSON/URL一括+Obsidian貼り付け+AIクイズ一括生成、進捗表示 |
| Settings | SAF自動バックアップ/Geminiキー/自動接続/Ktorサーバー+トークン(LAN警告)/SRS切替/クイズ演習設定6種/AI設定/メンテナンス |
| Whiteboard | ボード一覧+キャンバス(ドラッグ移動/セクション矩形) |
| Wiki / DatabaseManagement / Connections / Candidates / ThoughtEdit / DefinitionEdit / QuizList / QuizEdit | 各機能 |

### 10.4 リッチテキスト描画 — 2系統
- **RichContentView (WebView方式・メイン採用)**: `[[title|alias]]`→`wiki://` リンク、`{漢字|よみ}`→`<ruby>`、CDNの `marked@11.1.1` + `KaTeX@0.16.9` で Markdown+数式描画。JS失敗時 `innerText` フォールバック (`RichContentView.kt:97-99`)。
- **MarkdownText (Composeネイティブ)**: `AnnotatedString` 方式。`**bold**`/`*italic*`/`` `code` ``/`[[wiki-link]]`/見出し/リスト/引用/コードブロック + `AutoLinker` のTrie最長一致リンク。**現在は未使用**(WebView版が主流)。

### 10.5 動的UI — 設計予約コンポーネント
- **`UiSchemaRenderer`**: JSONスキーマ駆動の動的フォームレンダラー (`column/text/input/multipleChoice`)。プラグインの `renderSchema` 出力を描画する将来利用を想定。
- **`EntryTypeSections`**: 全13型の型固有プロパティカードをディスパッチ (`ThoughtSection`〜`AiConvSection`)。共通部品 `SectionCard/InfoRow/ExpandableText`。

### 10.6 ViewModel 設計パターン (21個)
- 全VMが `@HiltViewModel` + コンストラクタインジェクション。
- **Reactive state**: `StateFlow` + `stateIn(WhileSubscribed(5000), 初期値)` でDBのFlowを変換。
- **画面固有状態**: `sealed class` で状態機械を表現 (`QuizUiState` は SelectMode/Loading/Empty/Question/Answered/Enumerate…/SessionComplete を when 分岐)。
- **1回限りイベント**: `MutableSharedFlow` (`saved/message/actionMessage`) → 画面の `LaunchedEffect`+`collectLatest` で Toast/遷移。
- 長時間処理は `Dispatchers.IO` 明示。

### 10.7 テーマ
- `EncyclopediaTheme`: Android 12+ は **dynamicDark/LightColorScheme(壁紙連動)**、それ以外は既定色。カスタムカラートークンなし(Material3依存)。
- `Color.kt`: **エントリ型別のアクセントカラー**(webpage青/thought紫/book橙/…)+絵文字アイコン+日本語ラベル。全UIで型バッジ・背景色に統一利用。

---

## 11. テスト戦略

### 11.1 JVM ユニットテスト (`app/src/test`)
| テスト | 対象 |
|---|---|
| `RubricParserTest` / `RubricGraderTest` / `RubricJudgeTest` / `RubricFeatureExtractorTest` / `RubricConfidenceTest` | rubric採点パイプライン各部 |
| `KeywordMatcherTest` / `PolarityAnalyzerTest` / `RelationDirectionCheckerTest` / `NumericUnitVerifierTest` | 決定論解析器4種 |
| `QuizGraderServiceTest` / `MultiStageGraderTest` / `RuleBasedQuizGeneratorTest` | クイズ生成・採点 |
| `TextNormConsistencyTest` | `TextNorm` を **既存 `MultiStageGrader` と対拍**し仕様乖離を防止 |
| `InMemoryVectorIndexConcurrencyTest` | ★D1 の無ロック索引の並行安全性 |

### 11.2 インストゥルメントテスト (`app/src/androidTest`)
| テスト | 対象 |
|---|---|
| `MigrationTest` | **v1→v8 フルチェーンのマイグレーション検証** (`runMigrationsAndValidate`) |
| `ExampleInstrumentedTest` | スモーク |

### 11.3 テスト容易性の工夫
- 無状態エンジンは `object`(mock不要)。
- `TextNormConsistencyTest` は `EraConverter` を fake DAO に差し替えて `MultiStageGrader` を実体化(「既存コードを唯一の基準にする」方針)。
- `PortableExportWorker` の書き出しは suspend 非依存の純粋関数。

---

## 12. 設計パターン集大成

このプロジェクトで確認できる、意識的/無意識的な設計パターンの総覧です。

| パターン | 実装箇所 |
|---|---|
| **ポリモーフィック+クラステーブル継承** | `entry` 共通 + 13種1:1拡張テーブル |
| **ソフトデリート** | `entry.deletedAt` + 全クエリで `deletedAt IS NULL` |
| **履歴と最新状態の分離** | `srs_review`(全履歴) + `SrsCurrentView`/`QuizMasteryView`(最新) |
| **JSON列によるスキーマ進化回避** | `choicesJson` 等のTEXT+JSON列 |
| **正準形IDによる重複排除** | `connection.canonicalA/canonicalB` + unique制約 |
| **リポジトリパターン** | 9リポジトリがDAOを隠蔽、`upsertExtension(Any)` で型安全ルーティング |
| **ストラテジー切替** | SM-2/FSRS、DuplicateDetector 2実装のOR合成、プロバイダー4I/F |
| **共通サービス(ユビキタス実装)** | `QuizGraderService` をApp⇔サーバーで共有 |
| **非同期キュー+自己回復** | `EmbeddingQueue`(Channel) + `recoverJobs`/`rebuildAllSearchDocuments` |
| **無ロック並行性** | 不変Snapshot + AtomicReference + CAS |
| **承認制ワークフロー** | connection_candidate → approve/reject |
| **イベントログ** | `progress_events`(ヒートマップ/ストリークの源) |
| **キャッシュ** | `ai_explanations` unique(sourceType, sourceId) |
| **AIキャッシュ+フェールセーフ** | FactCheck/Coaching のキャッシュ + 固定文案フォールバック |
| **3フェーズ段階的初期化** | `runStep` で個別try-catch |
| **2パス処理** | Obsidianインポート(エントリ→接続) |
| **デバウンス検索** | `SearchViewModel` 400ms + `collectLatest` |
| **sealed class 状態機械** | `QuizUiState`/`SrsUiState`/`ImportState` |
| **依存の束 (ServerDependencies)** | ルート分割のDI簡素化 |
| **モデル差し替え点** | `GradingProviderModule` 1モジュール |
| **Trie自動リンク** | `AutoLinker`(UI装飾限定) |
| **AES-GCM + Keystore** | バックアップ暗号化(デバイスバインド) |
| **エラー表示の責務分離** | `AppLogger` は Logcat+リングバッファ200件+StateFlow公開 |

---

## 13. 注目点・課題・将来性

### 13.1 特に優れている点
1. **採点パイプラインの完成度**: 決定論→rubric→意味の3段階 + rubric内部5段階 + プロバイダー抽象。テスト9本で保護され、`docs/新採点システム.txt` という設計根拠まで揃う。
2. **トレーサビリティ**: コード内に設計書の節番号(`§8.4` 等)と改訂マーカー(`★最適化R6`)が刻まれ、要件→実装の追跡が可能。
3. **フェールセーフ哲学**: API未設定・LLM乱出力・重複実行・再起動時の回復まで網羅。動作しない状態が「壊れる」のではなく「劣化」する。

### 13.2 課題・気になる点 (実コード検証に基づく)
| # | 対象 | 内容 |
|---|---|---|
| 1 | **CORS未設定** | `ktor-server-cors` 未導入。ブラウザからの POST/PATCH/DELETE はプリフライトの関係で制限される可能性(Webクライアント実運用の潜在ギャップ) |
| 2 | **スキーマJSONの欠落** | `app/schemas/` に **v3/v4/v5 が存在しない**。MigrationTest は v1/v2/v6/v7 起点のみ検証可能 |
| 3 | **設計予約コンポーネント** | `AppEventBus`(誰もemit/subscribeしていない)/`UiSchemaRenderer`/`MarkdownText` は定義のみで未使用 |
| 4 | **孤児ファイル** | `web/src/main/assets/rich_content.html` は参照ゼロ(CLEAN-2問題の残骸) |
| 5 | **暗号化の不一致** | 設計書はトークンに EncryptedSharedPreferences と記載するが、実装は平文 DataStore (`server_prefs`) |
| 6 | **ポーリング型API** | サーバーは Room Flow を `.first()` で読むため Web画面の自動更新なし(手動 `reloadKey` 機構) |
| 7 | **メモリ設計上の留意** | `BackupEncryptor` はファイル全体を一括メモリ読込(大規模DBでメモリ注意) |
| 8 | **AppEventBusの将来価値** | 現状の「保存→再構築」フローはイベント駆動化で軽量化できる余地(設計書 §9.3) |

### 13.3 将来性
- `GradingProviderModule` を差し替えるだけで **端内モデル(Qwen3-Embedding-4B / NLI / Reranker / ローカルLLM)へ移行可能**(設計書の意図がコードに明記済み)。
- `connection_candidate` の承認フローは、より複雑な推薦(共起/時系列)に拡張可能。
- `UiSchemaRenderer` はプラグインの `renderSchema` と組み合わせることで、**ユーザー定義クイズ形式のUIをサーバー定義で描画**する拡張が可能。
- `progress_events` + `AppEventBus` を配線すれば、アクティビティ駆動の通知・リザーフェーシングが実現可能。

---

## 付録A. ディレクトリ構造マップ

```
PersonalEncyclopedia/
├── app/                                  # Android アプリ本体
│   ├── build.gradle.kts                  # Compose/Room/Hilt/Ktor/WorkManager 等
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/thuvstu/personalencyclopedia/
│       │   │   ├── MainActivity.kt / PersonalEncyclopediaApp.kt
│       │   │   ├── AppEventBus.kt / IncomingNavigation.kt
│       │   │   ├── backup/    BackupEncryptor, BackupExporter, BackupWorker, PortableExportWorker
│       │   │   ├── brain/     search/ srs/ quiz/(+rubric/) ai/ connection/ coaching/
│       │   │   │              ResurfacingEngine, TagSuggestionEngine
│       │   │   ├── db/        AppDatabase, entity/ dao/ Migration*to*.kt, SeedData, DemoData
│       │   │   ├── di/        DatabaseModule, ServerModule
│       │   │   ├── importer/  ImportPipeline, WebScraper, ObsidianImporter, DuplicateDetector, AutoLinker…
│       │   │   ├── plugins/   PluginEngine
│       │   │   ├── repository/ (9本)
│       │   │   ├── server/    LocalServer, TokenManager, ServerDependencies, dto/, routes/
│       │   │   ├── ui/        navigation/ theme/ component/ screen/
│       │   │   ├── util/      AppLogger
│       │   │   └── viewmodel/ (21本)
│       │   └── res/ …
│       ├── test/               # JVM ユニットテスト (rubric/クイズ/索引並行性)
│       └── androidTest/        # MigrationTest ほか
├── web/                        # React Webクライアント (DBなし・Ktor APIクライアント)
│   ├── package.json / vite.config.ts / tsconfig.json
│   └── src/  api/client.ts, lib/, components/ (EntryList, EntryDetail, GraphView,
│            QuizPanel, SrsPanel, OllamaPanel, ConnectionBar)
├── docs/                       # 設計書・ガイド・walkthrough 8ファイル + guide/ 8ファイル
├── gradle/                     # libs.versions.toml, wrapper
└── gradlew / settings.gradle.kts / build.gradle.kts
```

## 付録B. 設計書ドキュメント群との対応

| ドキュメント | 内容 |
|---|---|
| `docs/PersonalEncyclopedia-統合設計書-v14完全版.md` | 最新の統合設計書 (v13/v14で追補) |
| `docs/PersonalEncyclopedia.md` | ベース設計書 |
| `docs/新採点システム.txt` | rubric採点システムの設計根拠 |
| `docs/guide/` (8ファイル) | 開発者ガイド(00-overview, 01-entry-model, 02-search, 03-connection, 04-quiz-and-srs, 05-brain-layer, 06-troubleshooting, glossary) |
| `docs/walkthrough*.md` (6本) | 実装ウォークスルー |
| `docs/報告書.md` / `docs/継承PersonalEncyclopedia.md` | 受け継ぎ・報告 |

---

*本ドキュメントは `DESIGN.md` として、コミット `83b4c00` 時点の全ソースコード・ビルド定義・ドキュメントを実コード検証により執筆されました。*
