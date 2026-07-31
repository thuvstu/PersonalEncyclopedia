# Personal Encyclopedia — 統合設計書

**バージョン:** 11.0 (Unified Edition)
**作成日:** 2026-07-20
**ベース:** PersonalEncyclopedia.md (Androidネイティブ案)
**統合元:** 学習システム設計書(Supabase案) / LearningSheet v25.0(GAS) / LearningMasterMap(FastAPI+Postgres) / ALH Omni-Master v26.0 & All-Specification v5.2 / Personal Knowledge OS 要件定義書・詳細設計書 v10 / KnOS EX / lumina_pkm / quizstudy / txt.md
**ステータス:** 確定版(初期実装用)

---

## 0. この設計書について

これまで少なくとも8系統の学習支援システムを構想してきた。GAS→Python/SQLite→FastAPI+Postgres→Supabase→WSL2常時起動型Knowledge OS→Androidネイティブ、と基盤を転々としてきたが、これは失敗の繰り返しではなく「エコな個人システムとは何か」を実地に絞り込んできた過程である。本書はその到達点として、**最新版であるPersonalEncyclopedia(Androidネイティブ案)を不変の骨格としたまま**、過去のどの案よりもデータモデル・検索・採点・クイズ生成のロジックが洗練された`Personal Knowledge OS 詳細設計書 v10`の設計パターンを、Android/SQLite/Room上で動く形に移植する。さらにLearningSheet・ALH・quizstudy.mdが磨き上げてきた採点・出題ロジックと、lumina_pkm/txt.mdが示すUI構成を統合する。

### 0.1 なぜAndroidネイティブが最終形なのか(再確認)

| 過去の案 | 常時稼働に必要だったもの | 実態との乖離 |
|---|---|---|
| Supabase一本化案 | クラウドDB常時接続 | 「エコ」を謳いながら結局外部サービス依存 |
| Knowledge OS v10 (WSL2) | Windows起動+WSL2+Docker+Cloudflare Tunnel | PCは家の中でしか使わない。外出中は全機能停止 |
| ALH (Streamlit/WSL2) | 同上 | 同上。しかも本番フロントは未着手のまま設計だけ肥大化 |
| GAS/LearningSheet | Googleアカウントとスプレッドシート | 実行時間制限・キャッシュ100KBの壁に頭打ち |

Androidは「常に手元にあり、常に起動している」端末である。この一点だけで、他のどの案よりも「思い立った瞬間に記録できる」というPKM(個人知識管理)の生命線を満たす。PersonalEncyclopediaの転換理由(§1参照)はこの結論と完全に一致しており、揺るがす理由がない。

### 0.2 各案からの採用・不採用マップ

| 要素 | 出典 | 採用 | Android向けの変更点 |
|---|---|---|---|
| Android本体・Room・Ktor・Rhino・Drive橋渡し | PersonalEncyclopedia | ✅ そのまま骨格 | — |
| entry統一型 + 13種Class Table Inheritance | Knowledge OS v10 | ✅ 採用 | PostgresスキーマをRoom Entityに移植 |
| search_document集約 + Hybrid Search(RRF) | Knowledge OS v10 | ✅ 採用 | pgroonga→FTS4+Nグラム、pgvector→端末内ブルートフォース |
| connection_candidate承認フロー | Knowledge OS v10 | ✅ 採用 | そのままの設計思想が最重要資産 |
| embedding_jobキュー+再開処理 | Knowledge OS v10 | ✅ 採用、むしろ強化 | Androidはプロセスkillが頻発するため一層重要 |
| 2段階ファクトチェック・教科別AI指示ルール | LearningSheet v25 | ✅ 採用 | Gemini Grounding経由でAndroidから直接呼び出し |
| Trie木ハイパーリンク自動化 | LearningSheet v25 / ALH | ✅ 採用 | Kotlinで実装 |
| 多段階採点(正規化→Fuzzy→意味) | LearningSheet v25 / ALH | ✅ 採用 | rapidfuzz相当をKotlinで実装 |
| ルールベース(コスト0)クイズ生成 | ALH | ✅ 採用 | 「無料重視」方針と直結、最優先実装 |
| DI+インターフェース+イベント設計 | ALH | ✅ 採用 | Hilt(Kotlin DI)で移植 |
| コーチングエンジン(弱点分析) | ALH | ✅ 採用 | ai_explanationsキャッシュ拡張として |
| FSRSパラメータ / srs_review履歴+ビュー | LearningMasterMap / Knowledge OS | ✅ 採用 | SM-2から段階的移行 |
| スコア上下限・ヒント減点・再出題ロジック | quizstudy.md | ✅ 採用 | クイズエンジン中核 |
| ブロックエディタ(数式・コード・Callout) | KnOS EX | ✅ 部分採用 | ノートエディタのリッチ化に転用 |
| 匿名公開SNS機能そのもの | KnOS EX | ▲ 将来の衛星案 | 個人PKMのスコープ外、フェーズ5候補 |
| 画面構成・ダッシュボード発想 | lumina_pkm / txt.md | ✅ 採用 | §11 UI仕様に統合 |
| 段階的スコープ拡大(MVP→フルスタック) | txt.md | ✅ 採用 | §13 ロードマップの骨格 |
| React Flowナレッジグラフ | LearningMasterMap / Knowledge OS | ✅ 採用(PC側) | Android側はフェーズ4まで簡易版 |
| WSL2/Docker/Cloudflare Tunnel/GAS/Supabase | 各案 | ❌ 不採用 | Androidネイティブ方針と根本的に矛盾 |

---

## 1. プロジェクト概要

### 1.1 一文定義

生涯にわたって学んだこと・考えたこと・出会った情報のすべてを、常に手元にあるAndroid端末一台に記録し、間隔反復・AI採点・意味検索・知識接続によって「何十巻もの百科事典」規模まで育て続けられる、個人専用の学習兼知識管理基盤。

### 1.2 解決する問題

- 情報がノートアプリ・単語帳アプリ・クイズアプリ・ブックマークに分散し、横断検索も接続もできない
- 過去の複数のシステム(GAS版・Supabase版・WSL2常時起動版)が「入力の重さ」「常時稼働インフラの脆さ」「認知コストの増大」によって使われなくなった
- 「一生使う」ことを前提にするなら、PCの起動状態やクラウドサービスの継続可否にデータの生存が左右されてはならない

### 1.3 コア要件(優先順位、Knowledge OSのInput-Easy原則を継承)

| 優先度 | 要件 | 説明 |
|---|---|---|
| 1 | **Input-Easy** | 入力摩擦を最小化する。続かないシステムは死ぬ。クイック追加は2タップ以内 |
| 2 | **Data-Permanent** | データがアプリより長生きする。オープン標準(SQLite/Markdown/CSV/JSON)で常に復元可能 |
| 3 | **Free-First(無料重視)** | 月額固定費ゼロを維持する。Gemini API等の無料枠に収まる設計を優先する |
| 4 | **Search-Advanced** | 全文検索・意味検索のハイブリッドで「あれ何だっけ」に応える |
| 5 | **Connection-Clear** | 知識同士のつながりを可視化し、かつ暴走(グラフ毛玉化)させない |
| 6 | **Learn-Deep** | 単なる保存で終わらせず、間隔反復・多段階採点で「使える知識」に変える |

### 1.4 対象プラットフォームと役割分担

- **Android = データの本体・実行エンジン**。Room DB・Ktorローカルサーバー・Rhinoプラグインエンジン・埋め込みキュー・接続候補生成など、すべての知能はここで動く。
- **PC(React+Vite/bun) = 入力・閲覧・可視化専用のクライアント**。DBを持たない。ナレッジグラフの本格可視化(React Flow)はPC側で行う(Android単体では画面が小さく大規模グラフ表示に向かないため)。
- **Google Drive = 非同期の橋渡しとバックアップ倉庫**。VPNや常時接続APIサーバーを持たないための、最もエコな解。

### 1.5 スコープ外(このシステムが担わないもの)

- チームコラボレーション・マルチユーザー機能
- 課金・サブスクリプション管理
- 匿名公開型のナレッジ共有SNS(KnOS EXの発想) — 需要が生まれた場合のみ独立した衛星プロジェクトとして別リポジトリで検討する
- 経路探索・ナビゲーション機能(txt.mdでは「他サービス連携」に含まれ得るが、地図表示程度に留め、独立ナビシステムは対象外とする)

---

## 2. 設計原則

Knowledge OS v10 が到達した6原則と、PersonalEncyclopedia独自の「エコの3軸」、txt.mdの段階的スコープ拡大の思想を統合する。

### 2.1 データ・アーキテクチャ原則

1. **データが先、UIは後** — データは特定のUI・フレームワークに依存しない。Room Entityは可能な限りMarkdown/CSV/JSONへの可逆エクスポートを保つ。
2. **自作するのは"知能"の部分だけ** — DB(Room)・ローカルHTTP(Ktor)・認証(共有シークレット)は成熟した仕組みをそのまま使う。Embedding・検索ランキング・接続候補生成・採点ロジックだけが「自分の知識に最適化する価値がある」ので自作する。
3. **入力は最速の経路を用意する** — クイックキャプチャ(フローティングボタン)・Android共有メニュー・URL貼り付けの3経路を最優先実装する。
4. **統合は段階的に** — N個の外部サービスを一度に繋ぐとN²の障害点が生まれる(Knowledge OS v10 原則4)。フェーズを割り、確実に動くものから積む。
5. **Friction削減は機能追加より優先する** — 知識管理システム最大の敵は「記録の面倒さ」であり、アルゴリズムの洗練度ではない。タグ・トピック・接続は記録時に強制しない(後整理前提)。
6. **見た目のための複雑さを避ける** — UIは情報構造が一目でわかることを最優先し、装飾のための複雑さは持たない。

### 2.2 エコの3軸(学習システム設計書より継承・再定義)

| 軸 | 意味 | Android中心案での対策 |
|---|---|---|
| 金銭コスト | 月額固定費ゼロ | 自前サーバー無し。Gemini API無料枠(embedding: 無料枠あり / LLM: gemini-2.5-flash等)を主軸に、Google Drive無料枠(15GB)をバックアップに利用 |
| 開発コスト | 実装・保守の手間を減らす | 単一言語(Kotlin)にDB・API・AIロジックを集約。PC側はビューアに徹し二重実装を避ける |
| 認知コスト | 覚える技術要素を絞る | WSL2・Docker・Cloudflare Tunnel・Supabase・GASを全廃し、「Android 1台のRoom DB」という単一の心的モデルに収束させる |

### 2.3 過去の失敗から学んだ禁止事項(Knowledge OS v10 §2.3を継承)

- UIとデータを密結合させない(PC側は常にAPI越しにアクセスする)
- 「全部統合してから使う」設計にしない。フェーズ0は驚くほど小さく作り、まず毎日使う
- 1ファイル1000行を超えない。Kotlinパッケージも機能単位で細分化する
- 外部API依存は必ずAdapter/Interfaceパターンで隔離する(§9参照)

### 2.4 フェーズ哲学(Knowledge OS v10 §12冒頭を継承・最重要)

> 知識管理システムの最大の敵は「完成前の疲弊」である。各フェーズのゴールは次フェーズへの移行ではなく「実際に毎日使うこと」。フェーズ0で毎日使えなければフェーズ1に進まない。

txt.mdが示した「初期段階で入力・単語帳・クイズの基本学習ツールを完成させ、第二段階で残りのフルスタック機能を備える」という段階分けも、この哲学の具体化として §13 ロードマップに反映する。

---

## 3. システム全体構成

### 3.1 アーキテクチャ全体図

```
┌───────────────────────────────────────────────────────────────┐
│                    Androidアプリ(端末内・データの本体)              │
│                                                                 │
│  ┌────────────┐  ┌───────────────┐  ┌──────────────────────┐ │
│  │  Room DB    │  │ Ktorサーバー    │  │   Brain Layer         │ │
│  │  (SQLite)   │  │ (ローカルAPI)   │  │ ・Embedding Queue     │ │
│  │             │  │ Bearer認証     │  │ ・Search Engine       │ │
│  │ entry統一型  │◄─┤ /api/entries  │  │   (FTS + Vector RRF)  │ │
│  │ + 13拡張    │  │ /api/quiz     │  │ ・Connection Engine   │ │
│  │ quiz/srs/   │  │ /api/srs      │  │   (候補生成→承認制)    │ │
│  │ connection/ │  │ /api/graph    │  │ ・LLM Engine          │ │
│  │ embedding/  │  │ /api/import   │  │   (Gemini Embed/LLM)  │ │
│  │ plugin      │  │               │  │ ・Grader / QuizGen    │ │
│  └────────────┘  └──────┬────────┘  └──────────────────────┘ │
│                          │           ┌──────────────────────┐ │
│                          │           │ プラグインエンジン(Rhino)│ │
│                          │           │  quizType plugins(JS) │ │
│                          │           └──────────────────────┘ │
└──────────────────────────┼─────────────────────────────────────┘
                            │ 同一LAN内は直接HTTP
                            ▼
                 ┌─────────────────────┐
                 │  PC Webアプリ(React)   │
                 │ 入力・閲覧・グラフ可視化 │
                 │      (DBなし)         │
                 └───────────┬─────────┘
                              │ LAN外 or 非同期入力時
                              ▼
                 ┌─────────────────────┐
                 │     Google Drive      │
                 │ /imports/ ← PCが書く  │
                 │ /backups/ ← Androidが書く │
                 └─────────────────────┘
                              ▲
                       Androidが定期的に
                     imports/を取り込み、
                     backups/へ暗号化して書き出す

                 ┌─────────────────────┐
                 │   Gemini API (無料枠)  │  ← Android/Ktorから直接呼び出し
                 │ Embedding / LLM /     │
                 │ Grounding / Vision    │
                 └─────────────────────┘
```

Knowledge OS v10 の「Interface Layer → Cloudflare Tunnel → Brain Layer(WSL2)」という3層構成のうち、Brain LayerをそのままAndroid端末内に持ち込み、Cloudflare Tunnelの役割をLAN内直接HTTP＋Google Drive非同期橋渡しに置き換えたものが本構成である。Brain Layerの中身(Embedding/Search/Connection/LLM Engine)はKnowledge OS v10で磨き上げられた設計をほぼそのまま踏襲する。

### 3.2 通信経路(PersonalEncyclopedia §3を継承)

1. **同一LAN(自宅Wi-Fi等)**: PC WebアプリがAndroidのKtor APIに直接HTTPリクエスト。即時反映。
2. **LAN外 or 非同期でよい入力**: PCがGoogle Driveの`imports/`に構造化ファイル(Markdown/CSV/JSON)を置き、AndroidがWorkManagerの定期ジョブで取り込む。

どちらの経路も内部的には同じ取り込みパイプライン(§12.1)を通るため実装は二重化しない。

### 3.3 衛星システム(将来・別リポジトリ)

Knowledge OS v10 の「衛星システム」の考え方を踏襲し、本体のスコープを侵食しない形で以下を将来検討する。

| 衛星システム | 内容 | 本体との接続点 |
|---|---|---|
| KnOS EX相当(公開共有) | 匿名/OAuth投稿できるブロックエディタ型知識共有 | entryの`source_url`的な参照のみ、DBは完全分離 |
| ナビ/経路案内 | txt.mdの「他サービス連携」構想の一部 | 地図表示(`entry_place`)とは独立 |
| タスク管理・ホワイトボード | txt.mdのフルスタック構想の一部 | フェーズ4以降、本体のentry/connectionモデルを再利用する形で本体内機能として吸収する方が実装コストが低いため、無理に別リポジトリ化しない(§13参照) |

---

## 4. インフラ・環境仕様

### 4.1 技術スタック(最終決定)

| レイヤー | 技術 | 補足 |
|---|---|---|
| Android | Kotlin + Jetpack Compose | アプリ本体。DB・API・Brain Layer・プラグインエンジンを内包 |
| Android内DB | Room (SQLite) | データの唯一の本体。FTS4/FTS5仮想テーブルを併用 |
| Android内APIサーバー | Ktor (embedded server, Netty) | 同一LAN内からPCがアクセスするためのローカルAPI |
| DI(依存性注入) | Hilt | ALHのDIコンテナ思想をKotlinへ移植。ISearchProvider等のInterfaceを実装差し替え可能にする |
| プラグイン実行エンジン | Rhino (JVM純正JSエンジン) | NDK不要。AI生成コードを再ビルドなしで動的実行 |
| バックグラウンド処理 | WorkManager | Drive同期・Embeddingキュー回復・バックアップ・SRS通知 |
| Web フロントエンド(PC) | React + Vite | パッケージ管理・実行は`bun`を使用(npm/npxは使わない)。DBは持たずKtor APIを叩くのみ |
| PCグラフ可視化 | React Flow (`@xyflow/react`) | Knowledge Graphの本格描画はPC側で行う |
| PCリッチエディタ | Tiptap + KaTeX + lowlight | KnOS EXのブロックエディタ仕様を移植(§11.5) |
| 型・スキーマ共有 | TypeScript + Zod / Kotlin data class | プラグイン契約とバリデーションに使用 |
| PC→Android連携・バックアップ | Google Drive API | 非同期ファイル転送(取り込み)とバックアップ保管の両方 |
| AI(Embedding) | Gemini `gemini-embedding-2-preview` | 768次元(MRL截断)を既定値。無料枠(Google AI Studio APIキー) |
| AI(LLM) | Gemini `gemini-2.5-flash`(主) / `gemini-3-flash-preview`(副) | サマリー生成・構造化抽出・採点補助・クイズ生成・コーチング |
| AI(任意・LAN内ローカル) | Ollama(自宅PC等で稼働時のみ) | 完全無料・オフラインでの補助推論。DBを持たない純粋な推論エンドポイントなので「PC=閲覧専用」原則には抵触しない |
| コンテンツ編集補助(任意) | Google スプレッドシート | 単語帳・問題の元データを人間が編集し、Driveの`imports/`経由でAndroidへ取り込む |

### 4.2 フォルダ構成(モノレポ)

```
personal-encyclopedia/
├── apps/
│   ├── android/
│   │   └── app/src/main/java/.../
│   │       ├── db/              # Room Entity・DAO(entry統一型+13拡張、quiz、srs、connection等)
│   │       ├── server/          # Ktorルーティング
│   │       ├── brain/           # Embedding/Search/Connection/LLM/Grader/QuizGen Engine
│   │       ├── plugins/         # Rhino実行ラッパー
│   │       ├── importer/        # Webスクレイプ・PDF/DOCX・Notion・Obsidian等アダプタ
│   │       └── drivesync/       # Drive取り込み・バックアップ
│   └── web/                     # React + Vite (bun)。DBなし、Ktor APIを叩くのみ
├── packages/
│   └── shared-types/            # プラグイン型定義・Zodスキーマ(TS側、AIに渡す仕様書としても使う)
├── plugins/                     # 人力/AI作成のクイズプラグインソース(Android転送前のワークスペース)
├── docs/
│   └── design.md                # このファイル
└── package.json                 # bun workspaces設定(web側のみ)
```

### 4.3 認証・起動方式(PersonalEncyclopedia §5を継承)

- 端末内で生成したアクセストークンをAndroidアプリ画面に表示し、PC側で1回だけ入力して保存する簡易共有シークレット方式。OAuthのような重い仕組みは不要(単一ユーザー・単一端末が前提のため)。
- Ktorサーバーは常時起動ではなく、**Androidアプリ側で明示的にON/OFFする**運用(バッテリー消費とのバランス)。

### 4.4 環境変数・設定(Android内 EncryptedSharedPreferences で管理)

| キー | 説明 |
|---|---|
| `GEMINI_API_KEY` | Embedding・LLM呼び出し用 |
| `EMBEDDING_DIMENSION` | 既定768(MRL截断)。将来3072へ拡張する場合は全件再埋め込みが必要 |
| `EMBEDDING_RATE_LIMIT_RPM` | 既定90(無料枠100RPMに対し余裕を持たせる) |
| `AUTO_CONNECT_ENABLED` | 既定false。接続候補の自動生成有効化フラグ(§8.4参照) |
| `AUTO_CONNECT_THRESHOLD` | 既定0.88 |
| `OLLAMA_BASE_URL` | 任意。LAN内Ollamaサーバーがあれば設定 |
| `LOCAL_ACCESS_TOKEN` | PC連携用の共有シークレット(端末内生成) |
| `PLAYWRIGHT_ENABLED` 相当 | Android版は非対応(§12.2で代替方式を規定) |

---

## 5. データベース設計(Room / Kotlin)

### 5.1 設計パターン: entry統一型 + Class Table Inheritance

Knowledge OS v10 最大の資産である「**entry統一型 + 型別拡張テーブル(Class Table Inheritance)**」パターンをRoomへ移植する。旧PersonalEncyclopedia案の`notebooks`/`notes`/`decks`/`flashcards`はバラバラの独立テーブルだったため、ノートと単語帳と閲覧履歴を横断検索・接続できなかった。統一後は以下が成立する。

- `entry`テーブルが全13種共通のフィールド(タイトル・本文注釈・お気に入り・ミュート・削除・アクセス日時)を持つ
- 各型専用の拡張テーブル(`entry_webpage`等)が`entry.id`を主キー兼外部キーとして持つ
- 検索(`search_document`)・埋め込み(`embedding`)・接続(`connection`)・タグ(`entry_tag`)・SRS(`srs_review`)はすべて`entry.id`だけを参照すればよく、型が増えてもこれらのエンジンは変更不要
- **単語帳(旧`flashcards`)は`entry_definition`(type='definition')に統合する。** 前後(front/back)は用語(term)/定義(definition)にそのまま対応し、これにより単語帳エントリーも全文検索・意味検索・知識接続の対象になる(旧設計では単語帳だけが検索/接続の外にあった)

### 5.2 entry_type — マスターデータ

```kotlin
@Entity(tableName = "entry_type")
data class EntryTypeEntity(
    @PrimaryKey val name: String,       // 'webpage','thought','book','video','document',
                                         // 'media','person','org','place','event',
                                         // 'definition','liked','ai_conv','quiz_bank_source'
    val labelJa: String,
    val icon: String?,
    val colorHex: String,               // 型別カラー(§11.3のカラーシステムと対応)
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
// 新しい型の追加はINSERT 1行で完結する(ALTER TABLE不要)。ENUMを使わない理由はPostgres版と同じ。
```

### 5.3 entry — 共通ベーステーブル

```kotlin
@Entity(tableName = "entry", indices = [
    Index("type"), Index("createdAt"), Index("accessedAt"),
    Index("isFavorite"), Index("isMuted"), Index("deletedAt")
])
data class EntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,                   // entry_type.name への参照
    val title: String,
    val content: String? = null,        // ユーザーが書いた注釈・感想のみ。型固有の全文は拡張テーブルへ
    val summary: String? = null,        // AI生成サマリー(任意)
    val sourceUrl: String? = null,
    val lang: String? = null,           // BCP47 ('ja','en','zh-TW')
    val isFavorite: Boolean = false,
    val isMuted: Boolean = false,       // 削除せず検索ランキングから降格(ノイズ管理)
    val accessedAt: Long? = null,       // 閲覧の都度更新。時系列機能・再浮上の基盤
    val deletedAt: Long? = null,        // 論理削除。物理削除はしない
    val metadataJson: String = "{}",    // 拡張テーブルに追加するほどでもないソース固有情報
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

`entry.content`は「ユーザーの声」専用とし、スクレイプ全文やPDF抽出テキストは拡張テーブル側に置く(Knowledge OS v10と同じ判断)。これにより検索ノイズを抑える。

### 5.4 型別拡張テーブル(13種)

Class Table Inheritance のため、以下すべて `entryId` を主キー兼 `entry(id)` への外部キー(CASCADE削除)として持つ。

```kotlin
@Entity(tableName = "entry_webpage")
data class EntryWebpageEntity(
    @PrimaryKey val entryId: String,
    val url: String,
    val domain: String,                 // insert時にKotlin側でurlから抽出(生成列はSQLite可搬性のためアプリ層で計算)
    val scrapedAt: Long?,
    val fullText: String?,              // スクレイプ全文(検索用)
    val thumbnailPath: String?,
    val readingTimeS: Int?,
    val author: String?,
    val publishedAt: Long?,
    val scraperUsed: String?            // 'okhttp+readability' | 'jsoup' | 'ai_extract'
)

@Entity(tableName = "entry_thought")
data class EntryThoughtEntity(
    @PrimaryKey val entryId: String,
    val mood: String? = null,
    val context: String? = null,
    val isDraft: Boolean = false
)

@Entity(tableName = "entry_book")
data class EntryBookEntity(
    @PrimaryKey val entryId: String,
    val isbn: String? = null,
    val authorsJson: String = "[]",     // List<String>をJSON文字列で(Room TypeConverter)
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val totalPages: Int? = null,
    val readStatus: String = "unread",  // unread/reading/done/dropped
    val readStartDate: Long? = null,
    val readEndDate: Long? = null,
    val rating: Int? = null,            // 1-5
    val coverPath: String? = null
)

@Entity(tableName = "entry_video")
data class EntryVideoEntity(
    @PrimaryKey val entryId: String,
    val platform: String,
    val videoId: String? = null,
    val channelName: String? = null,
    val durationS: Int? = null,
    val thumbnailUrl: String? = null,
    val transcript: String? = null,
    val watchedAt: Long? = null,
    val watchProgress: Float? = null    // 0.0-1.0
)

@Entity(tableName = "entry_document")
data class EntryDocumentEntity(
    @PrimaryKey val entryId: String,
    val docType: String,                // pdf/docx/xlsx/pptx/gdoc/txt/md/other
    val blobPath: String? = null,       // 端末内保存パス
    val gdriveId: String? = null,
    val mimeType: String,
    val fileSizeBytes: Long? = null,
    val pageCount: Int? = null,
    val extractedText: String? = null,  // 検索用
    val extractionMethod: String? = null
)

@Entity(tableName = "entry_media")
data class EntryMediaEntity(
    @PrimaryKey val entryId: String,
    val mediaType: String,              // image/audio/video_file/other
    val blobPath: String,
    val mimeType: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationS: Float? = null,
    val ocrText: String? = null,        // Gemini Vision APIによるOCR結果
    val caption: String? = null
)

@Entity(tableName = "entry_person")
data class EntryPersonEntity(
    @PrimaryKey val entryId: String,
    val fullName: String,
    val aliasesJson: String = "[]",
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val nationality: String? = null,
    val occupationsJson: String = "[]",
    val biography: String? = null,
    val photoPath: String? = null
)

@Entity(tableName = "entry_org")
data class EntryOrgEntity(
    @PrimaryKey val entryId: String,
    val officialName: String,
    val orgType: String? = null,
    val foundedYear: Int? = null,
    val country: String? = null,
    val websiteUrl: String? = null,
    val description: String? = null
)

@Entity(tableName = "entry_place")
data class EntryPlaceEntity(
    @PrimaryKey val entryId: String,
    val placeName: String,
    val placeType: String? = null,
    val address: String? = null,
    val latitude: Double? = null,       // PostGIS Point の代わりに単純な緯度経度カラム
    val longitude: Double? = null,      // 地図表示はAndroid標準のMapsコンポーネントで十分
    val visitedDatesJson: String = "[]"
)

@Entity(tableName = "entry_event")
data class EntryEventEntity(
    @PrimaryKey val entryId: String,
    val eventName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val locationText: String? = null,
    val placeEntryId: String? = null,   // entry_placeとの紐付け(任意)
    val isPersonal: Boolean = true,
    val participantsJson: String = "[]"
)

// --- 単語帳(旧flashcards)を吸収。SRS状態は持たず、srs_reviewの履歴から導出する ---
@Entity(tableName = "entry_definition")
data class EntryDefinitionEntity(
    @PrimaryKey val entryId: String,
    val term: String,                   // 旧flashcard.front 相当
    val reading: String? = null,        // ふりがな
    val definition: String,             // 旧flashcard.back 相当
    val field: String? = null,          // '数学','CS','経済学' 等。topicとも併用可
    val examplesJson: String = "[]",
    val relatedTermsJson: String = "[]"
)

@Entity(tableName = "entry_liked")
data class EntryLikedEntity(
    @PrimaryKey val entryId: String,
    val platform: String,
    val originalId: String,
    val likedAt: Long? = null,
    val contentType: String,
    val authorName: String? = null,
    val fullText: String? = null
)

@Entity(tableName = "entry_ai_conv")
data class EntryAiConvEntity(
    @PrimaryKey val entryId: String,
    val model: String,
    val provider: String,               // anthropic/google/local
    val messagesJson: String = "[]",    // [{role, content, timestamp}]
    val tokenCount: Int? = null,
    val topic: String? = null,
    val isUseful: Boolean? = null
)
```

### 5.5 横断テーブル(Brain Layer)

#### 5.5.1 search_document — 検索・Embedding入力の集約

型別に分散した全文テキストを毎回JOINすると検索が遅く複雑になる(Knowledge OS v10の学び)。entryごとに1レコードへ集約する。

```kotlin
@Entity(tableName = "search_document")
data class SearchDocumentEntity(
    @PrimaryKey val entryId: String,
    val combinedText: String,           // §7.1.2 の型別戦略で構築
    val lang: String = "ja",
    val updatedAt: Long = System.currentTimeMillis()
)

// Android標準のFTS4仮想テーブル(全端末で確実に使える。FTS5は端末のSQLiteビルド依存のため避ける)
// 日本語は形態素解析を持たないため、pgroongaのTokenNgram戦略を踏襲し、
// combinedTextを挿入前にKotlin側でNグラム(bi-gram)分割してftsContentへ格納する。
@Fts4(contentEntity = SearchDocumentEntity::class)
@Entity(tableName = "search_document_fts")
data class SearchDocumentFtsEntity(
    val ftsContent: String              // combinedTextのNグラム展開版
)
```

#### 5.5.2 embedding — 意味検索用ベクトル

```kotlin
@Entity(tableName = "embedding", indices = [Index("entryId", unique = true)])
data class EmbeddingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val vectorBlob: ByteArray,          // FloatArray(768)をBLOB化。pgvectorの代替
    val model: String = "gemini-embedding-2-preview",
    val inputText: String,              // search_document.combinedText と同じ内容(差分検知に使用)
    val createdAt: Long = System.currentTimeMillis()
)
```

**ベクトル検索方式(pgvector/HNSWの代替)**: 個人規模(数千〜数万件)では、768次元ベクトルをすべてメモリに載せてのブルートフォース・コサイン類似度計算で十分実用速度が出る(1万件×768次元×4byte≒30MB、数十ms)。`EmbeddingCache`が起動時に全ベクトルを`FloatArray`としてメモリへロードし、クエリベクトルとの内積計算で上位N件を取る。件数が将来10万件を超え体感速度が落ちた場合のみ、`sqlite-vec`拡張(Android向けJNIビルドが公開されている)への移行を検討する(§16参照)。

#### 5.5.3 connection / connection_candidate — 知識接続(承認制)

Knowledge OS v10 最大の学びである「**自動接続は直接connectionに書かず、まずconnection_candidateに積んでユーザーが承認する**」設計を全面採用する。これがないと類似度の高いエントリーが際限なく自動連結され、ナレッジグラフが早期に「毛玉化」して使い物にならなくなる。

```kotlin
@Entity(tableName = "connection_type_def")
data class ConnectionTypeDefEntity(
    @PrimaryKey val name: String,       // related/references/contradicts/extends/exemplifies/
                                         // authored_by/published_by/located_at/occurred_at
    val labelJa: String,
    val isDirected: Boolean,
    val inverseLabelJa: String? = null  // 有向型の逆ラベル(例: references→被参照)
)

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryAId: String,
    val entryBId: String,
    val relationType: String,
    val strength: Float = 0.5f,         // 0.0-1.0
    val note: String? = null,
    val isAuto: Boolean = false,
    val isDirected: Boolean,            // INSERT時にconnectionTypeDefからコピー(Kotlin側で実施)
    val canonicalA: String,             // 無向関係の重複防止用正規化ペア(min(a,b))。アプリ層で計算
    val canonicalB: String,             // (Postgres生成列の代わり。SQLiteバージョン非依存で確実に動く)
    val createdAt: Long = System.currentTimeMillis()
)
// Unique制約: (canonicalA, canonicalB, relationType) は無向型のみ、
//            (entryAId, entryBId, relationType) は有向型のみ、それぞれ挿入前にKotlinリポジトリ層で重複チェック

@Entity(tableName = "connection_candidate")
data class ConnectionCandidateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryAId: String,
    val entryBId: String,
    val similarity: Float,
    val suggestedType: String = "related",
    val status: String = "pending",     // pending/approved/rejected
    val connectionId: String? = null,   // 承認後にconnection.idを記録
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
)
```

**接続候補の昇格フロー(Knowledge OS v10 §5.5.2bを踏襲)**:
```
entry作成 → 非同期でgenerateConnectionCandidates() (AUTO_CONNECT_ENABLED=trueの時のみ)
  → connection_candidate(status='pending')に積む
  → ダッシュボードの「新着接続候補」でユーザーが確認
  → 承認: connection作成 → connectionIdをリンク → status='approved'
  → 却下: status='rejected'(同ペアは再提案しない)
```

**有効化の判断基準(Knowledge OS v10を踏襲)**: タグ・トピックが50件以上整備済み、手動接続を20件以上作成して「有用な接続とは何か」の感覚がある、閾値(目安0.88)を実データで検証済み、の3条件を満たすまでは`AUTO_CONNECT_ENABLED=false`のままにする。

#### 5.5.4 tag / topic — 分類(quizstudy.mdの階層をここに統合)

quizstudy.mdの「ジャンル(学問系統)>分野(ジャンル内トピック)>タグ(汎用横断)」という3層分類は、`topic`の親子階層(ジャンル=親トピック、分野=子トピック)と`tag`(汎用)にそのまま対応する。ノート・単語帳・クイズすべてが同じ分類体系を共有する。

```kotlin
@Entity(tableName = "topic")
data class TopicEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,                   // 例: "世界史"(ジャンル) / "第一次世界大戦"(分野)
    val parentId: String? = null,       // null=ジャンル(最上位), 非null=分野(ジャンルの子)
    val description: String? = null,
    val colorHex: String? = null
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String? = null
)

@Entity(tableName = "entry_topic", primaryKeys = ["entryId", "topicId"])
data class EntryTopicEntity(val entryId: String, val topicId: String)

@Entity(tableName = "entry_tag", primaryKeys = ["entryId", "tagId"])
data class EntryTagEntity(val entryId: String, val tagId: String)

@Entity(tableName = "quiz_tag", primaryKeys = ["quizId", "tagId"])
data class QuizTagEntity(val quizId: String, val tagId: String)
```

#### 5.5.5 srs_review — 間隔反復の履歴(状態は持たない)

`entry_definition`にSRS状態を持たせず、`srs_review`の履歴から都度導出する(Knowledge OS v10の設計)。これによりアルゴリズムをSM-2→FSRSへ将来差し替えても過去データを失わない。

```kotlin
@Entity(tableName = "srs_review")
data class SrsReviewEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,                // entry_definition.entryId を参照
    val reviewedAt: Long = System.currentTimeMillis(),
    val grade: Int,                     // 0-5 (SM-2グレード。0-1:忘却/即再試行 2:誤答だが想起 3-4:正解 5:完璧)
    val intervalDays: Int,
    val easeFactor: Float = 2.5f,
    val nextReviewAt: Long
)

// Room @DatabaseView: 各entryの「現在のSRS状態」を最新レコードから導出
@DatabaseView(
    "SELECT entryId, grade, intervalDays, easeFactor, nextReviewAt, reviewedAt AS lastReviewedAt " +
    "FROM srs_review sr WHERE reviewedAt = (SELECT MAX(reviewedAt) FROM srs_review WHERE entryId = sr.entryId)"
)
data class SrsCurrentView(
    val entryId: String, val grade: Int, val intervalDays: Int,
    val easeFactor: Float, val nextReviewAt: Long, val lastReviewedAt: Long
)
```

#### 5.5.6 embedding_job — 埋め込みキューの永続化(Android版で一層重要)

WSL2なら「再起動は稀」だが、**Androidアプリはメモリ圧迫でOSに頻繁にプロセスキルされる**。Knowledge OS v10がフェーズ2で追加した「DBバックのキュー+再開処理」は、Android版ではむしろフェーズ0から必須とする。

```kotlin
@Entity(tableName = "embedding_job")
data class EmbeddingJobEntity(
    @PrimaryKey val entryId: String,
    val status: String = "queued",      // queued/running/done/failed
    val attempts: Int = 0,
    val error: String? = null,
    val queuedAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
)
// アプリ起動時(Application.onCreate)に status IN ('queued','running') を全件再投入する。
// 'running'は前回の異常終了(プロセスkill)とみなしattemptsを+1する。3回失敗でfailedに固定。
```

### 5.6 学習エンジン系テーブル(クイズ・採点)

ALHの`quiz_bank`のリッチなフィールド構成と、quizstudy.mdの採点・ヒート・再出題ロジックを統合する。PersonalEncyclopedia旧案の`quiz_sets`/`quiz_questions`より詳細な1テーブルに統合し、`sourceEntryId`で知識entryとも紐付ける(DB構造利用クイズ生成のため)。

```kotlin
@Entity(tableName = "quiz_bank")
data class QuizBankEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceEntryId: String? = null,  // 生成元のentry(定義・ノート等)。手動作成ならnull
    val topicId: String? = null,        // 分野(ジャンルは親topicをたどって導出)
    val pluginId: String? = null,       // 組み込み型(qa/mcq/fill_blank/sort/essay/cloze)ならnull、
                                         // 独自出題形式(早押し/縦書き等)ならRhinoプラグインID
    val quizType: String,               // qa/mcq/fill_blank/sort/essay/cloze/custom
    val question: String,
    val choicesJson: String = "[]",     // MCQ選択肢
    val answer: String,
    val gradingContextJson: String = "{}", // 採点用コンテキスト(採点で重視する項目。LLM採点に渡す)
    val hintsJson: String = "[]",       // 進捗開示ヒント(最大3件推奨)。開示ごとにscore -0.3
    val explanation: String? = null,
    val imagesJson: String = "{}",      // {question:[],choices:[[]],hint:[[]],explanation:[]}
    val generationMethod: String,       // rule_based/cloud_ai/local_ai/manual
    val numericVariantConfigJson: String? = null, // 数値可変問題(数学等)の生成・採点ロジック設定
    val difficulty: Int = 3,            // 1-5
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizId: String,
    val userAnswer: String,             // "未習"を示す特殊値も許容
    val isCorrect: Boolean?,            // null = 未習(判定なし)
    val score: Float,                   // 正解: 1.0 - 0.3*hintsRevealed(下限0) / 誤答: -1.0 / 未習: 0.0
    val gradingMethod: String,          // exact/fuzzy/semantic/llm
    val hintsRevealed: Int = 0,
    val attemptedAt: Long = System.currentTimeMillis()
)

// 「攻略度」= 各問題ごとの到達点(-1〜+1に丸め込み、一度でも正解すれば+1で固定)をMAXで導出
@DatabaseView(
    "SELECT quizId, MAX(score) as masteryScore FROM quiz_attempts GROUP BY quizId"
)
data class QuizMasteryView(val quizId: String, val masteryScore: Float)
```

### 5.7 その他の横断テーブル

```kotlin
// AI解説・添削・コーチングのキャッシュ(同一問いへのAPI再呼び出しを避けてコスト削減)
@Entity(tableName = "ai_explanations")
data class AiExplanationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceType: String,             // 'quiz_mistake'|'weak_point_analysis'|'entry_summary'|'grounding_fact_check'
    val sourceId: String,
    val prompt: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis()
)

// クイズプラグインのメタデータレジストリ(実行コード本体はRhino用JSファイルとして端末内保存)
@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val manifestJson: String,
    val scriptPath: String
)

// 進捗ログ(可視化用の汎用イベントテーブル)
@Entity(tableName = "progress_events")
data class ProgressEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,             // 'entry'|'quiz'|'srs'|'connection'
    val entityId: String,
    val eventType: String,              // 'viewed'|'edited'|'answered'|'reviewed'|'connected'
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 6. ストレージ・バックアップ戦略

### 6.1 ファイル保存方針

- Room DB本体(SQLiteファイル)が唯一の真実(Source of Truth)
- 画像・PDF等のBLOBは`blobs/`ディレクトリに保存し、DBにはパスのみ格納(DBの肥大化・バックアップ速度低下を防ぐ)
- 将来的な「何十巻もの百科事典」規模を見据え、端末ストレージの逼迫を検知したら古いWebページのサムネイル等から自動整理を提案する(§7.5.2「情報寿命」参照)

### 6.2 Google Drive連携方針(PersonalEncyclopedia §7を継承)

```
LearningSystem/(Google Drive)
├── imports/          # PCが置く。Androidが定期取り込み→processed/へ移動
│   ├── notes/         # Markdown
│   ├── flashcards/     # CSV (term,reading,definition,field... = entry_definitionへマッピング)
│   └── quiz/           # JSON (LLM生成のquiz_bank形式にそのまま対応、§12.4参照)
├── backups/
│   ├── db-snapshots/   # SQLiteファイル丸ごとの暗号化コピー(完全復元用)
│   └── portable/       # Markdown/CSV/JSONへのエクスポート(長期可搬性の保険)
```

### 6.3 バックアップ戦略(3層、Knowledge OS v10のNFR-301〜304を継承)

| 層 | 内容 | 頻度 | 目的 |
|---|---|---|---|
| 完全バックアップ | Room SQLiteファイルの暗号化コピー | 毎日1回(WorkManager, Wi-Fi+充電中のみ) | 即時の完全復元 |
| 可搬バックアップ | Markdown/CSV/JSONへのエクスポート | 週1回 | 10年後にRoom/Androidが存在しなくても読み書きできる保険 |
| スキーマメタ情報 | `_schema_meta`(カラム型・バージョン記録) | エクスポート時に同梱 | 将来の移行時に安全な型変換を可能にする(Knowledge OS v10 DR-011を踏襲) |

**暗号化(Knowledge OS v10のage暗号化 SEC-008を代替)**: `age`はAndroidネイティブでは扱いにくいため、Android Keystoreで保護した鍵によるAES-256-GCM暗号化を採用する。人生のログ(思考メモ・AI会話・閲覧履歴)が平文でクラウド上に存在しないという目的は同じ手段で達成できる。復号鍵は端末のKeystoreに束縛されるため、機種変更時は事前に鍵をエクスポートしておく手順をアプリ内に用意する。

- 世代管理: `db-snapshots/`は直近30世代を保持し、それより古いものは自動削除
- Google Driveの無料枠(15GB)で当面十分足りる規模感

### 6.4 データ永続性の保証

- 論理削除(`deletedAt`)を全entryに適用。物理削除はしない
- オープン標準(SQLite/Markdown/CSV/JSON)を維持し、特定フレームワークへのロックインを避ける
- 「UIが消えてもデータは無傷で存在する」原則(Knowledge OS v10)をAndroid版でも堅持する

---

## 7. Brain Layer仕様

### 7.1 Embedding Engine

#### 7.1.1 モデル仕様

| 項目 | 値 |
|---|---|
| モデル | Gemini `gemini-embedding-2-preview` |
| 次元数 | 768(MRL截断・既定)。将来3072へ拡張する場合は全件再埋め込みが必要 |
| 対応モダリティ | テキスト・画像・PDF(マルチモーダル) |
| 無料枠 | Google AI Studio APIキーで利用可(クレカ不要) |

768次元を既定にする理由はKnowledge OS v10と同じ: ストレージ・RAM・ブルートフォース計算コストが4分の1になり、個人規模・日本語主体では3072との品質差は体感できるほど大きくない。

#### 7.1.2 型別Embedding入力構築戦略

検索品質を決めるのは「何をEmbeddingに食わせるか」である。Knowledge OS v10が磨き上げた型別戦略をそのままKotlinへ移植する。

| 型 | 入力テキスト構築方針 |
|---|---|
| webpage | `title + content(ユーザーメモ) + fullText[:1500]` |
| thought | `title + content`(加工不要、ユーザーの思考そのものが価値) |
| book | `title + authors + content(読書メモ)`。あらすじは不要 |
| video | `title + channel + content + transcript[:1000]`(字幕は冒頭1000文字が最も内容を表す) |
| document | `title + content + extractedText[:1500]` |
| definition | `term + ": " + definition + examples[:3]`(定義文そのものが最重要) |
| person | `fullName + occupations + biography[:300]` |
| place | `placeName + placeType + address + content` |
| event | `eventName + year + location + content` |
| ai_conv | `topic + userメッセージの最初と最後`(assistant応答は含めない。自分の思考のみ) |
| liked | `platform + author + title + fullText[:500]` |

```kotlin
// brain/EmbeddingTextBuilder.kt
fun buildEmbeddingText(entry: EntryWithExtension): String {
    val parts = mutableListOf(entry.title)
    entry.content?.let { parts.add(it) }
    when (entry.type) {
        "webpage" -> entry.ext.fullText?.let { parts.add(it.take(1500)) }
        "book" -> entry.ext.authors?.let { parts.add(it.joinToString(" / ")) }
        "video" -> {
            entry.ext.channelName?.let { parts.add(it) }
            entry.ext.transcript?.let { parts.add(it.take(1000)) }
        }
        "document" -> entry.ext.extractedText?.let { parts.add(it.take(1500)) }
        "definition" -> {
            parts.add(entry.ext.definition)
            parts.addAll(entry.ext.examples.take(3))
        }
        "person" -> {
            parts.add(entry.ext.occupations.joinToString(", "))
            entry.ext.biography?.let { parts.add(it.take(300)) }
        }
        "ai_conv" -> {
            val userMsgs = entry.ext.messages.filter { it.role == "user" }
            userMsgs.firstOrNull()?.let { parts.add(it.content) }
            if (userMsgs.size > 1) parts.add(userMsgs.last().content)
        }
        // ...他型も同様
    }
    return parts.filter { it.isNotBlank() }.joinToString("\n").take(2000)
}
```

#### 7.1.3 レート制限付きタスクキュー

Gemini無料枠を超えないよう、Embedding生成は必ずキュー経由で行う。Android版はプロセスキルへの耐性がWSL2版以上に重要なため、`embedding_job`テーブル(§5.5.6)をフェーズ0から使用する。

```kotlin
// brain/EmbeddingQueue.kt
class EmbeddingQueue(private val rpm: Int = 90) {
    private val intervalMs = 60_000L / rpm
    private var lastCallAt = 0L
    private val channel = Channel<String>(Channel.UNLIMITED)

    suspend fun enqueue(entryId: String) {
        db.embeddingJobDao().upsert(EmbeddingJobEntity(entryId = entryId, status = "queued"))
        channel.send(entryId)
    }

    // WorkManagerの定期/常駐Workerから起動する
    suspend fun worker() {
        for (entryId in channel) {
            val wait = intervalMs - (System.currentTimeMillis() - lastCallAt)
            if (wait > 0) delay(wait)
            db.embeddingJobDao().updateStatus(entryId, "running")
            try {
                embedEntry(entryId)
                db.embeddingJobDao().updateStatus(entryId, "done")
            } catch (e: Exception) {
                db.embeddingJobDao().markFailedOrRetry(entryId, maxAttempts = 3)
            }
            lastCallAt = System.currentTimeMillis()
        }
    }
}

// Application.onCreate() で必ず呼ぶ: 前回異常終了(プロセスkill)からの回復
suspend fun recoverEmbeddingJobs() {
    val pending = db.embeddingJobDao().getByStatus(listOf("queued", "running"))
    pending.forEach { job ->
        val attempts = if (job.status == "running") job.attempts + 1 else job.attempts
        if (attempts >= 3) db.embeddingJobDao().markFailed(job.entryId, "起動時リカバリで上限到達")
        else embeddingQueue.enqueue(job.entryId)
    }
}
```

#### 7.1.4 差分検知

`embedding.inputText`と新しい`buildEmbeddingText()`の結果を比較し、変化がなければAPIを呼ばない(コスト節約、Knowledge OS v10と同じ判断)。

#### 7.1.5 ベクトル検索(ブルートフォース、§5.5.2参照)

```kotlin
// brain/VectorSearch.kt
class InMemoryVectorIndex {
    private var ids: Array<String> = emptyArray()
    private var vectors: Array<FloatArray> = emptyArray()  // 起動時に全件ロード

    fun topK(query: FloatArray, k: Int, excludeMuted: Boolean = true): List<Pair<String, Float>> =
        ids.indices.map { i -> ids[i] to cosineSimilarity(query, vectors[i]) }
            .sortedByDescending { it.second }
            .take(k)

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i] }
        return dot / (sqrt(na) * sqrt(nb) + 1e-8f)
    }
}
```

### 7.2 Search Engine

#### 7.2.1 検索モード

| モード | 説明 |
|---|---|
| `semantic` | クエリをEmbedding化しコサイン類似度検索(§7.1.5) |
| `fulltext` | FTS4 + Nグラムトークナイズによる全文検索 |
| `hybrid`(既定) | Semantic + Fulltext を RRF(Reciprocal Rank Fusion)で統合 |
| `graph` | connectionを再帰的に辿る(§7.3.2) |

#### 7.2.2 Hybrid Search(RRF実装)

Postgres版のSQLロジックをそのままKotlinのアプリケーション層で再現する(SQLiteはRRFを一発のSQLで書きにくいため、2つの結果リストをKotlin側でマージする)。

```kotlin
suspend fun hybridSearch(query: String, limit: Int = 20): List<SearchResult> {
    val semanticRanked = vectorIndex.topK(embed(query), k = 50)
        .mapIndexed { i, (id, _) -> id to (i + 1) }.toMap()
    val fulltextRanked = ftsDao.search(toNgram(query), limit = 50)
        .mapIndexed { i, id -> id to (i + 1) }.toMap()

    val allIds = semanticRanked.keys + fulltextRanked.keys
    return allIds.map { id ->
        val rrf = (semanticRanked[id]?.let { 1.0 / (60 + it) } ?: 0.0) +
                  (fulltextRanked[id]?.let { 1.0 / (60 + it) } ?: 0.0)
        val recencyBoost = recencyBoostFor(id)   // 直近7日+0.05、30日+0.02
        SearchResult(id, rrf + recencyBoost)
    }.filter { !isMuted(it.entryId) }
     .sortedByDescending { it.score }
     .take(limit)
}
```

`is_muted=true`のエントリーは検索対象から除外する(削除はせず、ランキングから降格させるだけ、Knowledge OS v10と同じ設計)。

### 7.3 Connection Engine

#### 7.3.1 接続候補生成

```kotlin
suspend fun generateConnectionCandidates(entryId: String): Int {
    if (!settings.autoConnectEnabled) return 0   // 既定false。§5.5.3の判断基準を満たすまでON にしない
    val similar = vectorIndex.topK(embeddingOf(entryId), k = 10)
    var created = 0
    similar.filter { it.second >= settings.autoConnectThreshold && it.first != entryId }
        .forEach { (candidateId, sim) ->
            db.connectionCandidateDao().insertIgnoreConflict(
                ConnectionCandidateEntity(entryAId = entryId, entryBId = candidateId, similarity = sim)
            )
            created++
        }
    return created
}
```

#### 7.3.2 Knowledge Graph探索

SQLiteは`WITH RECURSIVE`をサポートするため、Postgres版の再帰CTEはほぼそのまま移植できる。

```sql
WITH RECURSIVE graph AS (
    SELECT entryAId AS src, entryBId AS dst, relationType, strength, 1 AS depth
    FROM connection WHERE entryAId = :entryId OR entryBId = :entryId
    UNION ALL
    SELECT c.entryAId, c.entryBId, c.relationType, c.strength, g.depth + 1
    FROM connection c JOIN graph g ON (c.entryAId = g.dst OR c.entryBId = g.dst)
    WHERE g.depth < :maxDepth
)
SELECT DISTINCT * FROM graph LIMIT :maxNodes
```

Android単体では表示領域の制約からリスト形式の「関連エントリー」「バックリンク」表示に留め(フェーズ3)、React Flowによる本格的なグラフ描画はPC側(フェーズ4)で行う。

### 7.4 LLM Engine

| 用途 | 説明 |
|---|---|
| サマリー生成 | `entry.summary`の自動生成(インポート時・手動リクエスト時) |
| 構造化抽出 | スクレイプHTMLからメタデータ(著者・日付等)をJSON抽出 |
| OCR | `entry_media`の画像テキスト認識(Gemini Vision) |
| ファクトチェック調査 | LearningSheet v25の2段階方式(§7.4.2) |
| クイズ自動生成 | §8.3 |
| コーチング | 間違い解説・弱点分析(§8.5) |

#### 7.4.1 モデル選択とGrounding制約(Knowledge OS v10のパターンをそのまま踏襲)

```kotlin
// brain/LlmEngine.kt
interface LlmProvider { suspend fun generate(prompt: String, jsonMode: Boolean = false): String }

class GeminiLlm : LlmProvider {
    private val primary = "gemini-2.5-flash"
    private val fallback = "gemini-3-flash-preview"

    override suspend fun generate(prompt: String, jsonMode: Boolean): String {
        for (model in listOf(primary, fallback)) {
            runCatching { return callGemini(model, prompt, jsonMode = jsonMode) }
        }
        throw LlmUnavailableException()
    }

    // Grounding(Google検索)とJSON構造化出力は同時使用不可のため二段階で回避
    suspend fun generateGroundedThenStructure(searchPrompt: String, structureTemplate: String): JsonObject {
        val searchResult = callGemini(primary, searchPrompt, grounding = true)
        return Json.parseToJsonElement(
            generate(structureTemplate.format(searchResult), jsonMode = true)
        ).jsonObject
    }
}

// 任意: LAN内にOllamaサーバーがあれば優先(APIコスト・レート制限の節約)
fun getLlm(): LlmProvider =
    if (settings.ollamaBaseUrl != null) OllamaLlm(settings.ollamaBaseUrl) else GeminiLlm()
```

#### 7.4.2 2段階ファクトチェック(LearningSheet v25 §3を移植)

自動DB構築・定義エントリー生成時のハルシネーション対策として、以下を標準フローとする。

```
1. 調査: Gemini Grounding(Google検索)で信頼できる事実コンテキストを構築(最大28,000文字目安)
   信頼ドメイン優先: wikipedia.org, go.jp, ac.jp, 各種一次資料
2. 生成: このコンテキストのみを情報源として、Pydantic相当のKotlin data classスキーマに
   厳密一致するJSONのみを出力させる(generateGroundedThenStructure())
```

#### 7.4.3 教科別AI執筆ルール(LearningSheet v25 §3を移植)

`entry_definition`や`quiz_bank`をAI生成する際、分野(`topic`)に応じて以下のプロンプト制約を適用する。

| 分野 | AI執筆ルール |
|---|---|
| 歴史 | 紀元前対応、因果関係(きっかけ→影響)を300文字以上で詳述 |
| 公民 | 出典統計(調査組織/年)を明示 |
| 法律 | 公布年と施行年を区別、重要条文の要約と判例意義 |
| 理科 | 公式はTeX表記、SI単位系遵守、実験上の注意点を明記 |
| 数学 | 証明・導出プロセスをステップ詳述、適用条件を明記 |
| 国語 | 漢字の成り立ち(由来)、誤用警告を明記 |
| 偉人 | 人物像を印象付ける逸話を2つ以上 |
| 英語 | IPA発音記号、語源(ラテン/ギリシャ語根)、コロケーション表現 |

これらのプロンプトテンプレートは`prompts/`パッケージに分離し(ALHの設計方針§9.3参照)、コード変更なしで改善できるようにする。

### 7.5 将来のBrain Layer設計指針(Knowledge OS v10 §7.5を継承)

現行設計は「空間(接続・類似度)」に強いが、人間の記憶は「時系列」に強く依存する。フェーズ0〜3で以下を確実に積み立てておけば、将来の拡張(Temporal Layer・Resurfacing Engine)を妨げない。

- `accessedAt`を閲覧の都度、確実に更新する(必須)
- `isMuted`でノイズを管理する(必須、自動muteは行わずサジェストのみに留める)
- `search_document.combinedText`を型別戦略で丁寧に構築する(必須、将来の関連性エンジンの精度を決める)
- 半年以上未アクセスかつ`isFavorite=false`のwebpage/liked/ai_convは「整理しませんか」提案の対象候補とする(自動削除・自動muteはしない)

---

## 8. クイズ・学習エンジン仕様

学習機能は2つのトラックを持つ。両者は`topic`/`tag`分類を共有するが、目的が異なるため別エンジンとして扱う(ALHの`memory_logs`と`quiz_bank`が分離されているのと同じ判断)。

| トラック | 対象 | 目的 | アルゴリズム |
|---|---|---|---|
| **単語帳(SRS)トラック** | `entry_definition` | 長期記憶への定着 | SM-2(初期)→FSRS(将来) |
| **クイズ演習トラック** | `quiz_bank` | 演習による理解度チェック・弱点発見 | 多段階採点+動的出題 |

### 8.1 クイズ出題フロー(quizstudy.mdを正式仕様として採用)

```
1. ジャンル(topic親)を選ぶ
   ↓
2. 選んだジャンル→分野(topic子)→タグから問題を抽出して出題(択一 or 記述)
   ジャンル・分野・タグは問題と問題の間(回答後)でいつでも変更可能
   ↓
3. ユーザーは「回答」または「未習(まだ習っていない)」のどちらかを選ぶ
   ↓
4. 正誤にかかわらず、同一UI内で正解と解説を表示する
   ↓
5. 正答 → 「次の問題へ」ボタンを表示。quiz_attempts.score = 1.0 - 0.3×hintsRevealed(下限0)
   誤答/未習 → 類似の追加問題を同分野から出題し、正解できるまで(または諦めるまで)出題数を増やす
             誤答時 score = -1.0 / 未習時 score = 0.0(変動なし)
```

**攻略度スコアの丸め込みルール(§5.6 QuizMasteryView)**: 1問題ごとに`MAX(score)`を取ることで、「一度でも正解すればその問題は+1として固定」「何回間違えても下限は-1」「未習のままなら0」を1つのビューで自然に表現する。ダッシュボードの「攻略度」はフィルタ範囲内の`quiz_mastery`合計として表示する。

### 8.2 出題内容の適応(誤答・正答ログに基づく)

- 正解済みの範囲(分野/タグ)は出題頻度を下げる
- 誤答した問題は「正解できるまで」他より出題比重を上げ、正解後も定期的に再出題する(間隔反復トラックへの緩やかな合流)
- ログは`progress_events`と`quiz_attempts`から集計する

### 8.3 動的クイズ生成エンジン(ALH §4.4 + LearningSheet §6を統合)

**コスト優先順位(「無料重視」方針の直接的な実装)**:

```
1. ルールベース(コスト0・最優先)
   - {{重要}}タグからの穴埋め問題生成
   - 同一topic内の他entry(entry_definition等)をダミー選択肢にした「スマート4択」自動生成
   - entry_event.startedAtを利用した「年代並び替え」クイズ
   - CSV/Notion取り込み時、列指定による確実な一問一答生成
2. DB構造利用(コスト0)
   - 正引きQA「明治維新のyearは?」→「1868」
   - 逆引きQA「yearが1868であるものは?」→「明治維新」
   - MCQ(プロパティ)・リスト問題
3. ローカルAI(コストゼロ、任意・Ollama)
   - テキストからの基本的な一問一答抽出
4. クラウドAI(従量だが無料枠内、最終手段)
   - Gemini Visionによる画像・図解からのビジュアルクイズ生成
   - ひっかけ選択肢(もっともらしい誤答)とその解説の生成
   - LLM(json_format)によるクイズ一括生成。解説・採点用コンテキストも同時生成させる
```

**LLMクイズ生成のJSON契約(quizstudy.mdの要求を反映)**:

```kotlin
@Serializable
data class GeneratedQuiz(
    val question: String,
    val quizType: String,               // qa/mcq/fill_blank/sort/essay/cloze
    val choices: List<String> = emptyList(),
    val answer: String,
    val hints: List<String> = emptyList(),   // 最大3件、段階的開示用
    val explanation: String,
    val gradingContext: Map<String, String> = emptyMap()  // 記述式採点で重視する観点
)
```

**数値可変問題(quizstudy.mdの要求、数学等)**: `numericVariantConfigJson`にパラメータ範囲と計算式を保持し、出題の都度Kotlin側で数値を再抽選、`answer`もその場で再計算する。採点も同じロジックを使うため文字列一致ではなく数値評価で行う。

### 8.4 多段階採点エンジン(LearningSheet v25 §6 + ALH §4.2を統合)

```
1. 正規化完全一致    全半角・大文字小文字・記号を統一して比較
2. 暦・数値変換      西暦⇄和暦⇄紀元前を共通数値化して比較(例: 1600年 = 慶長5年)
3. Fuzzy判定        レーベンシュタイン距離。類似率85%以上の誤字脱字を許容
4. 同義語合成        組み込み辞書＋ユーザー定義シノニムを正解扱い
5. 複数正解展開      括弧・カンマ区切りを自動展開して全パターンに適用
6. 意味的採点(記述式) 端末内Embeddingでコサイン類似度計算。0.85以上で正解/部分点
   (rapidfuzz相当はKotlinで簡易Levenshtein実装、意味的採点は§7.1.5のベクトル基盤を再利用)
```

**採点モード**: `standard`(上記フル)/`lenient`(閾値70%)/`strict`(exactのみ、正規化あり)/`exact`(正規化なし)。論述問題はGemini LLMに`gradingContext`を渡して採点させ、`quiz_attempts.gradingMethod = "llm"`として記録する。

### 8.5 進捗ヒント・コーチングエンジン

- **段階的ヒント(quizstudy.md + LearningSheet §6を統合)**: 3つの中から選んで1つずつ開示。開示するたびその問題の正解時得点が`1.0 - 0.3×開示数`に減少(下限0)。LLMによるヒント自動生成にも対応
- **コーチングエンジン(ALH §5.4を移植)**: `explainMistake(quizAttemptId)` — 個別の間違いをAIが解説。`analyzeWeakPoints(topicId)` — 複数の間違いパターンから弱点分野を分析しダッシュボードに表示。結果は`ai_explanations`にキャッシュしてAPI再呼び出しを防ぐ

### 8.6 SRS(間隔反復)

- フェーズ1〜3: SM-2アルゴリズム(easeFactor/intervalDays/repetitions相当を`srs_review`履歴から都度計算)
- フェーズ4以降: FSRSへの移行を検討(LearningMasterMap・ALHが採用)。移行時のデフォルトパラメータは以下を踏襲

| パラメータ | デフォルト値 |
|---|---|
| 初期安定性(Good) | 3.0日 |
| 安定性成長率 | 1.9倍 |
| 目標保持率 | 90% |
| 最小/最大間隔 | 1日 / 365日 |

`srs_review`が履歴テーブルであるため、アルゴリズムの差し替えは新しい計算ロジックに置き換えるだけで済み、過去データのマイグレーションが不要という設計上の利点がある。

### 8.7 ゲーミフィケーション拡張(quizstudy.md extra、フェーズ4以降・プラグインとして実装)

以下はRhinoプラグイン(`quizType = "custom"`)として実装し、コア出題フローに影響を与えない形で追加する。

| 機能 | 概要 |
|---|---|
| 読み上げ | Android標準TextToSpeechで問題文・解説を音声化 |
| 早押し | 複数選択肢を同時表示しタップ速度を採点に加味 |
| タイムアタック | 制限時間内の連続正解数を記録 |
| 縦書き問題 | 国語向け。Compose上でのCJK縦書きレイアウト |
| 漢字書き取り | 手書き入力(ML Kit Digital Ink Recognition等)またはキーボード入力+厳密一致判定 |
| リスニング問題 | 音声ファイル(`entry_media.blobPath`)再生+聞き取り回答 |
| タイピング/コピー問題 | 純粋な入力速度・正確性の測定 |

---

## 9. プラグインシステム & 拡張性設計

### 9.1 クイズプラグイン(PersonalEncyclopedia §6を継承)

型契約はTypeScript側でZodスキーマとして定義し(人にもAIにも共通の仕様書として機能)、実行はAndroid上のRhino(JVM純正JSエンジン)で行う。**再ビルド不要でプラグインを追加できる**のが最大の利点で、アプリストア審査を待たずにJSファイルを転送するだけで拡張機能が増える。

```typescript
// packages/shared-types/plugin.ts
export const QuizPluginManifest = z.object({
  id: z.string(), name: z.string(), version: z.string(),
  type: z.literal("quizType"),
});

export interface QuizPlugin {
  manifest: z.infer<typeof QuizPluginManifest>;
  grade: (answer: unknown, answerData: unknown) => { correct: boolean; score: number };
  renderSchema: (questionData: unknown) => UISchema;
}

export type UISchema =
  | { type: "text"; content: string }
  | { type: "input"; id: string; placeholder: string }
  | { type: "multipleChoice"; id: string; options: string[] }
  | { type: "column"; children: UISchema[] }
  | { type: "custom"; componentId: string };  // §8.7のゲーミフィケーション拡張等
```

**実行フロー**: ①人またはAIがJS契約に従いコードを書く → ②Ktorサーバーロード時にKotlin側でマニフェスト検証 → ③検証通過で`plugins`テーブルに登録・スクリプトを内部ストレージに保存 → ④採点/描画リクエスト時にRhinoで実行 → ⑤失敗時のエラーメッセージ(Zodのフォーマット済みエラー)をそのままAIに渡して自己修正させる。

**Server-Driven UI**: プラグインはUIをコードでなく`UISchema`(JSON)として返す。Web(React)とAndroid(Compose)はそれぞれ1つの汎用レンダラーを持ち、`type`に応じて描画を切り替えるため新しい問題タイプ追加時も両プラットフォームへの反映は自動。ドラッグ&ドロップ等スキーマ表現力を超えるUIのみ`{ type: "custom", componentId }`で事前実装済みネイティブコンポーネントを呼ぶハイブリッド方式にする。

### 9.2 コアエンジンの拡張性設計(ALH §7を移植)

クイズプラグインとは別に、スクレイパー・採点器・検索プロバイダなど**コアエンジン自体の実装を差し替え可能にする**ため、ALHのInterface + DIコンテナ設計をKotlin/Hiltへ移植する。これにより「Gemini APIを別のAIプロバイダに差し替える」「スクレイパーの実装を追加する」といった変更が、呼び出し側のコードに触れずに行える。

```kotlin
// brain/interfaces/
interface ISearchProvider { suspend fun search(query: String): List<SearchResult> }
interface IScraperProvider { suspend fun scrape(url: String): ScrapedPage }
interface IAiProvider { suspend fun generate(prompt: String, jsonMode: Boolean): String
                         suspend fun embed(text: String): FloatArray }
interface IGrader { fun grade(userAnswer: String, quiz: QuizBankEntity): GradeResult }
interface IQuizGenerator { suspend fun generate(entry: EntryWithExtension, count: Int): List<GeneratedQuiz> }
interface ITtsProvider { fun speak(text: String) }
interface IExporter { suspend fun export(entries: List<EntryEntity>): File }

// Hiltモジュールで実装をバインド(差し替えはここを変更するだけ)
@Module @InstallIn(SingletonComponent::class)
object BrainModule {
    @Provides fun provideAiProvider(): IAiProvider = GeminiProvider()
    @Provides fun provideGrader(): IGrader = MultiStageGrader()
    // ...
}
```

**設定外部化**: 信頼ドメインリスト・モデル定義・FSRSパラメータ・TTS音声設定・除外プロパティ等の全ハードコード値は`config/`パッケージに集約し(ALH §7.4)、コード変更なしで調整できるようにする。

**プロンプトテンプレート分離**: 全AIプロンプトは`prompts/`パッケージにテンプレートとして分離し(ALH §7.5)、教科別ルール(§7.4.3)やクイズ生成プロンプトの改善がコード変更なしで行えるようにする。

### 9.3 イベントシステム(任意・フェーズ3以降)

進捗の可視化や将来の自動化(例: 接続候補生成のトリガー)のため、軽量なイベントバスを導入する(ALH §7.3のイベント定義を参考に、Kotlin `SharedFlow`で実装)。

| カテゴリ | イベント |
|---|---|
| データ変更 | `entry.created` / `entry.updated` / `entry.deleted` |
| 学習 | `quiz.answered` / `quiz.correct` / `quiz.incorrect` / `srs.reviewed` |
| 知識接続 | `connection.candidateGenerated` / `connection.approved` |
| インポート | `import.started` / `import.stepComplete` / `import.complete` / `import.error` |

---

## 10. Ktorローカルサーバー(API仕様)

PersonalEncyclopedia §5を土台に、統合後のリソースに合わせてエンドポイントを拡張する。

```kotlin
fun startLocalServer(port: Int = 8080) {
    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) { json() }
        install(Authentication) {
            bearer("token-auth") {
                authenticate { if (it.token == getLocalAccessToken()) UserIdPrincipal("owner") else null }
            }
        }
        routing {
            authenticate("token-auth") {
                route("/api/entries") { /* CRUD、型別拡張含む */ }
                route("/api/search") { /* mode=semantic|fulltext|hybrid|graph */ }
                route("/api/connections") { /* 手動接続CRUD */ }
                route("/api/connection-candidates") { /* 一覧・承認・却下 */ }
                route("/api/quiz") { /* 出題・採点(グレーダー/プラグイン経由) */ }
                route("/api/srs") { /* 本日の復習キュー・結果記録 */ }
                route("/api/graph") { /* Knowledge Graph探索(depth指定) */ }
                route("/api/import") { /* URL取り込み・ファイルアップロード */ }
                route("/api/progress") { /* 進捗集計・攻略度・弱点分析 */ }
                route("/api/plugins") { /* プラグイン登録・一覧 */ }
            }
        }
    }.start(wait = false)
}
```

エラーレスポンスはKnowledge OS v10のパターンを踏襲し、`{ code, message, status }`の統一形式で返す。

---

## 11. UI/UX仕様

### 11.1 デザイン原則(Knowledge OS v10 + txt.mdを統合)

- **入力摩擦ゼロ**: クイック追加はどの画面からでも1タップで開く(フローティングボタン)
- **情報の階層が一目でわかる**: 型別アイコン・カラーコードで視覚的に区別
- **モバイルファースト**: Android Chrome/Compose双方で快適に動作
- **後整理前提**: タグ・トピック・接続は記録時に強制しない
- **エンプティステート/ローディング/オプティミスティックアップデート/レスポンシブ**(txt.mdの要求)を全CRUD画面で徹底する
- **初回起動時からリアルなデモデータを同梱**し、空っぽの画面でユーザーを迎えない(txt.mdの要求。オンボーディング用のサンプルentry・quiz・接続をあらかじめ用意する)
- ダークモードはシステム設定に自動追従

### 11.2 カラーシステム(型別、Knowledge OS v10 §9.3を継承)

| 型 | カラー |
|---|---|
| webpage | Blue `#3B82F6` |
| thought | Purple `#8B5CF6` |
| book | Amber `#F59E0B` |
| video | Red `#EF4444` |
| document | Slate `#64748B` |
| definition | Green `#10B981` |
| person | Pink `#EC4899` |
| place | Teal `#14B8A6` |
| event | Orange `#F97316` |
| ai_conv | Indigo `#6366F1` |

### 11.3 画面構成(lumina_pkm + Knowledge OS v10 + txt.mdの画面群を統合)

バーナビゲーション(下部タブ or サイドバー)構成:

| 画面 | 概要 |
|---|---|
| **ダッシュボード** | 最近追加(直近10件)・今日の復習キュー件数・新着接続候補・学習継続日数・攻略度サマリー・クイック追加フォーム |
| **検索** | 大きな検索バー+モード切替(Hybrid/Semantic/Fulltext/Graph)、型・タグ・トピック・日付フィルタ、無限スクロール |
| **エントリー詳細** | 型バッジ・本文(Markdownレンダリング)・型別セクション・接続セクション(手動追加UI)・類似セクション(上位5件)・タグ/トピック編集 |
| **単語帳(SRS復習)** | 集中力を高めるカード型学習インターフェース。ワンタップ解答表示+評価 |
| **クイズ演習** | ジャンル/分野/タグ選択→出題→解答/未習→解説→継続、のシンプル高速レスポンスUI(§8.1) |
| **学習統計** | ヒートマップ・攻略度グラフ・弱点分野の可視化 |
| **ナレッジグラフ** | React Flow(PC)による接続可視化。Android単体は関連リスト+バックリンクに簡略化 |
| **インボックス/取り込み** | Drive `imports/`の未処理ファイル一覧、AI要約による効率的な整理 |
| **DB/データベース管理** | ストレージ監視・型別件数・エクスポート・生データ閲覧 |
| **設定** | APIキー・Drive連携・バックアップ・共有トークン管理 |

### 11.4 主要画面の詳細(Knowledge OS v10 §9.4を移植)

- **クイック追加(フローティング)**: 画面右下の常設ボタン。タップで「URLを追加」「メモを書く」の2択。URLモードはスクレイプ進捗を表示、メモモードは即座に`thought`として保存
- **Android共有メニュー連携**: OS標準の共有シートからURL/テキストをそのままクイック追加に送信(capture frictionを最も下げる最重要機能。PersonalEncyclopedia基本設計に追加する新規要件)

### 11.5 ノートエディタのリッチ化(KnOS EX §6を部分移植)

PC側(React+Tiptap)のノート編集を、KnOS EXで磨かれたブロックエディタ仕様で強化する。Android側(Compose)はフェーズ1では標準Markdown編集、フェーズ3以降で同等のブロック機能を追加する。

| ブロック | 対応 |
|---|---|
| 見出しH1〜H4・太字・箇条書き・チェックリスト・引用・区切り線 | 標準対応 |
| コードブロック | シンタックスハイライト+コピーボタン(10言語) |
| 数式(KaTeX) | インライン`$...$` / ブロック`$$...$$` |
| テーブル | リサイズ可能 |
| Callout(info/tip/warning/danger) | 注記の視覚強調 |
| 動画埋め込み | YouTube/Vimeo自動変換(取り込むと`entry_video`としても登録可能) |
| スラッシュコマンド | `/`でブロック挿入メニュー |
| ブロック単位タグ付け | ノート全体でなく特定ブロックにもタグを付与可能 |

---

## 12. 外部連携・インポート/エクスポート仕様

### 12.1 インポートパイプライン共通フロー(Knowledge OS v10 §10.1を継承)

```
入力ソース → Adapter(ソース別変換) → EntryCreateSchemaで正規化・バリデーション
  → entry + 拡張テーブルへ保存 → Embeddingキューへ追加 → 接続候補生成キューへ追加
```

PC経由のGoogle Drive非同期取り込みも、Android上でのURL直接取り込みも、この同一パイプラインを通る(実装の二重化を避ける、PersonalEncyclopedia §3の設計方針を継承)。

### 12.2 Webスクレイパー(段階的フォールバック、Android向けに再設計)

Playwright(フルブラウザ)はChromium依存でAndroidには不向きなため、PC版のような3段階は組まず、Android向けに2段階+AIフォールバックとする。

```
段階1(Must):  OkHttp + Jsoup + Readability相当のボイラープレート除去ロジック
              — 実際の大半のページはこれで十分
段階2(Should): 上記で本文が閾値文字数(100文字)未満の場合、取得したHTMLをGemini LLMに渡し
              「本文のみ抽出して要約せず全文を返す」プロンプトで構造化抽出させる
              (Bot検知回避が必要な高度なケースは無理に自動化せず、取り込み失敗として
               ユーザーに手動貼り付けを促す方がAndroid単体構成としては現実的)
```

### 12.3 ファイルインポート

| 形式 | 抽出方法(Android) |
|---|---|
| PDF | PdfBox-Android |
| DOCX/PPTX/XLSX | Apache POI(Android互換ビルド)またはサーバーサイド変換なしの軽量パーサー |
| MD/TXT | そのまま |
| 画像 | Gemini Vision APIによるOCR |
| Obsidianエクスポート(MD zip) | Markdownパーサー+`[[wiki-link]]`→`connection(references)`変換(Knowledge OS v10 §10.3のロジックをKotlin移植) |
| Notionエクスポート | HTML/MD zip → BeautifulSoup相当(Jsoup)でMarkdown変換 |
| Googleスプレッドシート | Drive `imports/`経由のCSV取り込み(単語帳・クイズの元データ編集用) |

### 12.4 LLMによるクイズ一括生成(quizstudy.md + LearningSheetの要求)

インポートしたテキスト・既存entryから、§8.3で定義した`GeneratedQuiz`形式のJSONをLLMに一括生成させ、`quiz_bank`へまとめてinsertする。生成時は`gradingContext`(採点で重視する観点)も同時に生成させ、記述式問題のLLM採点(§8.4)にそのまま利用する。

### 12.5 Trie木による自動ハイパーリンク(LearningSheet v25 + ALH §4.5を移植)

```kotlin
// import/AutoLinker.kt
class AutoLinker(entries: List<EntryEntity>) {
    private val trie = buildTrie(entries.map { it.title to it.id })  // 最長一致優先

    fun linkify(text: String): List<Pair<IntRange, String>> {
        // "日本文化"のような長い語を"日本"より優先してマッチさせ、干渉を防ぐ
        return trie.findLongestMatches(text)
    }
}
```

ノート・定義の本文中に他entryのタイトルが出現した場合、自動でハイパーリンク(閲覧時のみのUI装飾。`connection`テーブルへの書き込みは行わず、ユーザーがタップして初めて手動接続を提案する形にすることで、§5.5.3の接続候補承認フローと矛盾しないようにする)。

### 12.6 タグ・表記揺れの自動統合(LearningSheet v25 §5を移植)

新規タグ作成時、既存タグとのEmbeddingコサイン類似度が0.85以上のものを「表記揺れの可能性」としてサジェストする(例: 「WW1」と「第一次世界大戦」)。自動統合はせず、ユーザーが確認して統合を選べるようにする(接続候補承認と同じ「暴走させない」設計思想)。

---

## 13. 開発ロードマップ

> **最重要原則(Knowledge OS v10 §12を継承)**: 各フェーズのゴールは次フェーズへの移行ではなく「実際に毎日使うこと」。フェーズ0で3日以上連続して使えなければ、フェーズ1には進まない。

txt.mdが示した「第一段階=入力・単語帳・クイズの基本学習ツール」「第二段階=残り全部のフルスタック機能」という大きな2段階構成を、以下のようにより細かいフェーズへ具体化する。

### フェーズ0 — 毎日使える最小版(目標: 3〜5日)

**禁止事項(全部後回し)**: Embedding・意味検索・接続候補・Knowledge Graph・Drive連携・プラグインエンジン・SRS・AI生成

- [ ] Room: `entry` + `entry_type` + `entry_thought` + `entry_definition` + `tag`/`entry_tag`のみ
- [ ] Ktorローカルサーバーの雛形 + トークン認証
- [ ] Android単体でメモ(thought)と単語帳(definition)をCRUDできる
- [ ] キーワード検索(SQL LIKE検索で十分。FTS4は次フェーズ)
- [ ] **判定基準**: 3日連続で何かを記録した

### フェーズ1 — 単語帳SRS + クイズ基本形(目標: 1〜2週間)

txt.mdの「第一段階(基本学習ツール)」に相当。

- [ ] `srs_review`テーブル+`SrsCurrentView`、SM-2アルゴリズムによる復習キュー
- [ ] `quiz_bank` / `quiz_attempts` + `QuizMasteryView`
- [ ] 多段階採点エンジン(正規化→Fuzzy、意味的採点はフェーズ2でEmbedding導入後)
- [ ] クイズ出題フロー(§8.1)をルールベース生成(§8.3の1〜2段階)で実装
- [ ] Google Drive `backups/db-snapshots/`への日次自動バックアップ(**最優先で入れる**、PersonalEncyclopedia旧ロードマップの判断を継承)
- [ ] CSV/Markdown経由のインポート(`imports/`取り込みパイプライン)
- [ ] **判定基準**: 単語帳とクイズを実際の学習に使い続けている

### フェーズ2 — 検索・埋め込み・全entry型対応(目標: +2〜3週間)

- [ ] `search_document` + FTS4(Nグラム) + Hybrid Search(Fulltextのみからスタート)
- [ ] Gemini Embedding導入、`embedding` + `embedding_job`(キュー回復処理を含む)
- [ ] Hybrid SearchにSemanticを追加(RRF)
- [ ] 残り全entry型(webpage/book/video/document/media/person/org/place/event/liked/ai_conv)のRoomスキーマ
- [ ] Webスクレイパー(OkHttp+Jsoup、§12.2)
- [ ] PDF/DOCXインポート

### フェーズ3 — 知識接続・PC連携・UI完成(目標: +3〜4週間)

- [ ] PC Webアプリ(React+Vite/bun)雛形 → 同一LAN内でKtor APIに接続
- [ ] `connection` / `connection_candidate`実装(**`AUTO_CONNECT_ENABLED`は既定false据え置き**)
- [ ] 手動接続UI、関連エントリー・バックリンク表示
- [ ] プラグイン契約確定 → Rhinoエンジンで`multiple-choice`プラグインを1つ実装
- [ ] AI解説生成・コーチングエンジン(§8.5)
- [ ] 進捗・攻略度・学習統計画面
- [ ] ダークモード、エンプティステート/ローディング/オプティミスティックアップデートの全画面適用
- [ ] リアルなデモデータの同梱(初回起動時)

### フェーズ4 — Knowledge Graph本格化・自動接続・外部連携(目標: +4週間)

txt.mdの「第二段階(フルスタック)」相当。ここで初めて自動化・グラフ可視化・高度な外部連携に着手する。

- [ ] `AUTO_CONNECT_ENABLED=true`化の判断(§5.5.3の3条件を満たしてから)
- [ ] PC側 React Flowによるナレッジグラフ可視化
- [ ] KnOS EX由来のブロックエディタ機能をPC/Android両方へ
- [ ] Notion / Obsidian / YouTube Liked インポート
- [ ] Trie木自動リンク、タグ表記揺れ統合サジェスト
- [ ] ゲーミフィケーション拡張(§8.7)をプラグインとして追加
- [ ] FSRSへの移行検討
- [ ] タスク管理・ノード関係可視化ホワイトボード(txt.mdのフルスタック構想の残り機能。既存のentry/connectionモデルを再利用する形で本体に統合)

### フェーズ5 — 衛星システム(将来・別途計画)

- [ ] 匿名/OAuth公開共有プラットフォーム(KnOS EX相当)を独立プロジェクトとして検討
- [ ] ナビ/経路案内(txt.mdの「他サービス連携」構想の一部)

---

## 14. 非機能要件

| 項目 | 目標値 | 説明 |
|---|---|---|
| 検索応答時間 | < 500ms(1万件規模) | ブルートフォース・ベクトル検索+FTS4の合算 |
| Embedding生成 | < 2秒 | Gemini API通信込み |
| ページ初期表示(PC) | < 2秒 | Vite本番ビルド |
| バックアップ復旧 | 日次バックアップ時点まで復元可能 | 「ゼロ損失」は個人端末構成では保証できないため、現実的な目標として明示する |
| ファイル行数 | 1ファイル1000行以下 | Kotlinパッケージも機能単位で分割徹底 |
| 一人運用 | 全運用タスクを一人で完結 | 監視は`adb logcat`+アプリ内ログ画面で手動確認 |
| データ独立性 | UIが消えてもデータは無傷 | Room DBとMarkdown/CSV/JSON可搬エクスポートの二重保証 |
| 想定データ規模 | 個人利用(年間数千〜数万entry) | 「何十巻もの百科事典」規模になっても、768次元ブルートフォース検索で数十ms〜低速でも数百ms程度に収まる規模感 |
| バッテリー | Ktorサーバーは明示的ON/OFF | 常時起動によるバッテリー消費を避ける(§4.3) |
| 予算 | 月額固定費ゼロ | 有料サービス・クレジットカード登録不可(「無料重視」方針) |

---

## 15. 今後の拡張ポイント

- **ベクトル検索のスケールアップ**: entry数が10万件を超え体感速度が落ちた場合、`sqlite-vec`拡張(Android向けJNIビルド)への移行を検討する。それまではブルートフォース+インメモリキャッシュで十分
- **FTS5への移行**: 端末のAndroidバージョンがFTS5を確実にサポートする水準まで普及したら、Nグラム手動分割からFTS5+ICUトークナイザへの移行を検討する
- **複数端末対応**: 2台目のAndroidで使いたくなった場合も、Google Driveの`imports/`・`backups/`を経由点にすれば大掛かりな同期基盤を作らずに済む見込み(PersonalEncyclopedia §10の判断を継承)
- **Ktor常時起動化**: 将来的に外出先からもPC経由でアクセスしたくなった場合、Foreground Service化+バッテリー最適化除外設定を検討する(その場合もCloudflare Tunnel等の外部依存は避け、Tailscale等のP2P VPNで直接到達性を確保する方向を優先する)
- **FSRSへの本格移行**: `srs_review`が履歴テーブルである設計上の利点を活かし、既存データを失わずにアルゴリズムを差し替える
- **匿名公開プラットフォーム(KnOS EX相当)**: 本体のPKMとしての完成度を落とさないため、需要が明確になるまでは独立の衛星プロジェクトとして温存する

---

*本書はこれまでの全7系統の学習支援システム計画(Supabase案・GAS/LearningSheet v25・LearningMasterMap・ALH Omni-Master v26/All-Specification v5.2・Personal Knowledge OS v10・KnOS EX・quizstudy/lumina_pkm/txt.md)を評価・統合したものである。ベースはAndroidネイティブ最新版であるPersonalEncyclopediaとし、その「データ主権をAndroidに置く」というエコで壊れにくい骨格は変更していない。*
