# Personal Encyclopedia — 統合設計書

**バージョン:** 14.0 (Researched & Expanded Edition)
**作成日:** 2026-08-11
**ベース:** v13.0(Complete Edition) + ライブラリ・AIモデル調査 + クイズ機能拡充 + 開発者ガイド要件を統合
**統合元:** 学習システム設計書(Supabase案) / LearningSheet v25.0(GAS) / LearningMasterMap(FastAPI+Postgres) / ALH Omni-Master v26.0 & All-Specification v5.2 / Personal Knowledge OS 要件定義書・詳細設計書 v10 / KnOS EX / lumina_pkm / quizstudy / txt.md / MyBase(検討・不採用) / nextPKM.txt(検討・不採用)
**ステータス:** 確定版(Phase 3実装済み・Phase 4以降の実装仕様として使用)

## v14.0での主な変更点(v13.0からの差分)

| 変更 | 内容 |
|---|---|
| §4.1(新設) | ライブラリの実バージョンを調査し、具体的な数値で確定(Kotlin 2.4.0・Compose BOM・Room 2.8.4・Ktor 3.5.1等) |
| §7.4 | Geminiモデルレジストリを実在のモデル名で具体化(embedding-2, 3.6 Flash, 3.5 Flash-Lite等)、タスク別使い分け戦略を追加 |
| §7.7 | PC(React Webクライアント)からもOllamaを直接呼べるよう拡張 |
| §5.8.3 | カスタムフィールドの用途を明確化(自由記述項目の追加) |
| §8.7 | クイズバリエーションを大幅拡充。QuizKnock・Kahoot・Duolingo等の企画/UXパターンを調査し移植 |
| §12.5 | 自動リンクにホバー/タップでの定義プレビュー機能を追加 |
| §14.1(新設) | 開発者ガイドを正式な成果物として位置づけ(コード同様に「把握できる」ことを要件化) |
| 巻末(新設) | 全プロジェクト史を通じた総括評価(深めた考察) |

---

## 0. この設計書について

これまで少なくとも7系統の学習支援システムを構想してきた。GAS→Python/SQLite→FastAPI+Postgres→Supabase→WSL2常時起動型Knowledge OS→Androidネイティブ、と基盤を転々としてきたが、これは失敗の繰り返しではなく「エコな個人システムとは何か」を実地に絞り込んできた過程である。本書はその到達点として、**最新版であるPersonalEncyclopedia(Androidネイティブ案)を不変の骨格としたまま**、過去のどの案よりもデータモデル・検索・採点・クイズ生成のロジックが洗練された`Personal Knowledge OS 詳細設計書 v10`の設計パターンを、Android/SQLite/Room上で動く形に移植する。さらにLearningSheet・ALH・quizstudy.mdが磨き上げてきた採点・出題ロジックと、lumina_pkm/txt.mdが示すUI構成を統合する。

### 0.1 v11.0からv13.0への道のり(実装で得た教訓)

v11.0策定後、実際にPhase 0〜3までが実装された。その過程で以下が起きた。

1. **Phase 0〜3は実装・完了した**(entry統一型+CTI、SM-2 SRS、多段階採点、Hybrid Search、接続候補承認制、Ktor API、15画面のUI等)
2. **v12.0への拡張(ホワイトボード・Wiki・FSRS・Ollama等10機能)を同時導入しようとして失敗した**。コンパイルエラーが7ファイル以上に波及し、gitで安定版(Phase 3完了・DB v5)へ撤回した
3. **撤回原因を分析した報告書(報告書.md)から5つの実装原則を得た**: 1セッション1機能・既存コードを唯一の正とする・依存関係を提示前に検証する・エラー3ファイル以上で即中止し巻き戻す・動くコードを設計網羅性より優先する

本書v13.0は、v11.0のアーキテクチャ(不変)に、v12.0で計画した新機能群(ホワイトボード・Wiki・カスタムフィールド・FSRS・Ollama等)を**正式な設計仕様として書き込みつつ**、実装順序と実装作法については上記の教訓を厳格に適用する。つまり「何を作るか」はv12.0の野心をそのまま引き継ぎ、「どう作るか」はv12.0の失敗から学び直した、という位置づけである。

### 0.2 なぜAndroidネイティブが最終形なのか(再確認)

| 過去の案 | 常時稼働に必要だったもの | 実態との乖離 |
|---|---|---|
| Supabase一本化案 | クラウドDB常時接続 | 「エコ」を謳いながら結局外部サービス依存 |
| Knowledge OS v10 (WSL2) | Windows起動+WSL2+Docker+Cloudflare Tunnel | PCは家の中でしか使わない。外出中は全機能停止 |
| ALH (Streamlit/WSL2) | 同上 | 同上。しかも本番フロントは未着手のまま設計だけ肥大化 |
| GAS/LearningSheet | Googleアカウントとスプレッドシート | 実行時間制限・キャッシュ100KBの壁に頭打ち |

Androidは「常に手元にあり、常に起動している」端末である。この一点だけで、他のどの案よりも「思い立った瞬間に記録できる」というPKM(個人知識管理)の生命線を満たす。PersonalEncyclopediaの転換理由(§1参照)はこの結論と完全に一致しており、揺るがす理由がない。

### 0.3 各案からの採用・不採用マップ

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

### 2.5 実装原則・作業プロトコル(v12.0撤回の教訓・コーディングエージェント運用ルール)

§2.1〜2.4が「何を作るか」の原則であるのに対し、本節は「どう作るか」の原則である。v12.0で10機能同時導入を試みてコンパイルエラーが連鎖し、gitで安定版へ撤回した。その原因分析(報告書.md)から得た5原則を、以降のすべての実装作業に適用する。

#### 2.5.1 五原則

| # | 原則 | 内容 |
|---|---|---|
| 1 | **1セッション1機能** | 複数機能を同時に導入しない。1つ実装したらビルドを通してから次へ。1機能内の複数ファイルも、まとめて提示せず1つずつコミット+ビルド確認する |
| 2 | **既存コードが唯一の正** | 既存クラス・メソッド・パラメータ名を推測で書かない。新規追加は既存コードを1行も変えずに実現できないかをまず考える |
| 3 | **提示前に依存を検証する** | ファイルAを提示する前に、Aが呼ぶ全クラスのpublic API(メソッド名・パラメータ名・型・個数)を既存コードで確認してから書く |
| 4 | **エラー3ファイル基準** | 個別修正で追える上限は2ファイルまで。3ファイル以上にエラーが波及したら個別修正を即中止し、直前の安定コミットへ戻ってから1機能ずつ再導入する |
| 5 | **動くコード > 設計網羅性** | 「実装して」「全部まとめて」という指示は、この設計書の全項目をコード化することではなく、今動くものを1つ増やすことだと解釈する |

#### 2.5.2 セッション開始時チェックリスト(コーディングエージェントへ毎回渡す)

```
このセッションで着手するのは [1機能のみ] とする。
1. まず対象範囲の既存コード(呼び出し元・呼び出し先)を実際に読み、
   参照するクラス・メソッド・パラメータ名を確認してから書き始める。
2. 新規追加が既存コードの変更を必要とする場合、変更箇所を最小限にし、
   変更前後の差分を明示する。
3. 1ファイルごとに提示→ビルド確認→コミット、を繰り返す。まとめて出さない。
4. ビルドエラーが3ファイル以上に波及したら、その場で個別修正をやめて
   「直前のコミットに戻して1機能ずつ再導入する」ことを提案する。
5. このセッションの完了条件は「ビルドが通り、実際にインストールして
   動作確認できる」こと。設計書の網羅ではない。
```

#### 2.5.3 エラー対応プロトコル

```
エラー1〜2ファイル → その場で個別修正してよい
エラー3ファイル以上 → 個別修正を中止
                    → git checkout / git reset で直前の安定コミットへ戻す
                    → 原因になった機能を1段階小さく分解し直す
                    → 再度1機能ずつ導入する
```

#### 2.5.4 撤回の被害統計(記録・次回の目安値)

| 項目 | 数値(v12.0時点) |
|---|---|
| 提示した新規・変更ファイル総数 | 約45 |
| ビルドエラーを引き起こしたファイル | 7以上 |
| 同時に導入しようとした機能数 | 10 |
| git撤回の回数 | 1回 |

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

### 3.4 起動シーケンス・段階的初期化アーキテクチャ

v12.0実装時、`Application.onCreate()`にDB初期化・Brain Layer初期化・Ktorサーバー起動・埋め込みキュー回復を詰め込んだ結果、1箇所の失敗(例: Embedding APIキー未設定)がアプリ全体の起動を止めるという脆さが見つかった。これを解消するため、起動処理を3フェーズに分離し、各ステップの失敗を隔離する。

```kotlin
// PersonalEncyclopediaApp.kt
class PersonalEncyclopediaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runStep("Phase A: DB初期化") { initDatabase() }
            runStep("Phase B: Brain Layer初期化") { recoverEmbeddingJobs(); loadVectorIndex() }
            runStep("Phase C: バックグラウンドサービス") { scheduleBackupWorker(); scheduleDriveImportWorker() }
        }
    }

    // 各ステップを個別にtry-catchし、1つの失敗が他フェーズを道連れにしないようにする。
    // 失敗はAppLoggerに記録し、UIには「一部機能が制限されています」の非致命的表示に留める。
    private suspend fun runStep(name: String, block: suspend () -> Unit) {
        try { block() } catch (e: Exception) { AppLogger.e(name, "初期化失敗", e) }
    }
}
```

**注意(実装原則§2.5準拠)**: `runStep`はsuspend関数のため、`onCreate()`から直接呼ばず必ず`appScope.launch { }`の中で呼ぶこと。v12.0実装時にこの点が守られず、非suspendコンテキストからの呼び出しでビルドエラーとなった実例がある。

Ktorサーバー(§10)は「常時起動ではなく明示的にON/OFF」する方針(§4.3)のため、Phase Cのバックグラウンドサービス起動には含めない。ユーザーが設定画面でONにした時点で別途起動する。

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
| バックグラウンド処理 | WorkManager | SAF同期・Embeddingキュー回復・バックアップ・SRS通知 |
| Web フロントエンド(PC) | React + Vite | パッケージ管理・実行は`bun`を使用(npm/npxは使わない)。DBは持たずKtor APIを叩くのみ |
| PCグラフ可視化 | React Flow (`@xyflow/react`) | Knowledge Graphの本格描画はPC側で行う |
| PCリッチエディタ | Tiptap + KaTeX + lowlight | KnOS EXのブロックエディタ仕様を移植(§11.5) |
| 型・スキーマ共有 | TypeScript + Zod / Kotlin data class | プラグイン契約とバリデーションに使用 |
| PC↔Android連携・バックアップ | SAF(Storage Access Framework)+File System Access API | §6.2参照。Drive API/OAuthは不使用 |
| AI(Embedding) | Gemini `gemini-embedding-2` | §7.4.4参照。マルチモーダル対応、3072次元(MRL截断で768等に調整) |
| AI(LLM) | Gemini `gemini-3.6-flash`(主) 等 | §7.4.4のモデルレジストリ参照。タスクに応じて複数モデルを使い分ける |
| AI(任意・LAN内ローカル) | Ollama(自宅PC等で稼働時のみ) | 完全無料・オフラインでの補助推論。Android・PC双方から呼び出し可能(§7.7) |
| コンテンツ編集補助(任意) | Google スプレッドシート | 単語帳・問題の元データを人間が編集し、SAF経由の`imports/`フォルダへ取り込む |

### 4.1.1 ライブラリの実バージョン(2026年8月調査・実装時に再確認すること)

実装原則§2.5(既存コードが唯一の正)と同様に、ライブラリも「最新」を漠然と指定せず、調査時点の具体的なバージョンを記録する。ただし数ヶ月単位で更新され続けるため、**実装セッション開始時に必ず各ライブラリの最新安定版を再調査してから`build.gradle.kts`/`package.json`へ反映する**こと(本表はあくまで2026年8月時点のスナップショット)。

| ライブラリ | 調査時点の最新安定版 | 備考 |
|---|---|---|
| Kotlin | 2.4.0(2026年6月3日) | K2コンパイラが標準。context parametersが正式機能に昇格。KSP1→KSP2への移行を推奨 |
| Jetpack Compose (BOM) | 2026.04.01系(core 1.11.0安定、2026年4月22日)、1.12.0がRC段階(2026年7月時点) | 実装時に`androidx.compose:compose-bom`の最新安定BOMを確認 |
| Room | 2.8.4 | `room-sqlite-wrapper`アーティファクトが追加され、`SQLiteDriver`を差し替え可能に(将来的なFTS5移行等の余地) |
| Ktor | 3.5.1(2026年6月25日) | Digest認証RFC7616対応等、Bearer認証中心の本設計への影響は小さい |
| Hilt | Compose/Room等と同じAndroaXリリース列で継続更新中 | 実装時にstableチャンネルを確認 |
| React | 19.2.8(2026年7月21日) | React 20の予定なし。React Compiler 1.0が安定(2025年10月〜)し手動`memo`/`useMemo`の多くが不要に。PC側実装で積極採用する |
| Vite | 8.x系(8.0安定化は2026年3月、Rolldown統合によりビルド大幅高速化) | Node.js 20.19+/22.12+が必要 |
| Ollama | 0.32.6(2026年8月4日) | OpenAI互換API・エージェントモード搭載。詳細は§7.7 |



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

### 5.8 新規テーブル設計(v12.0で計画・v13.0で正式化)

v12.0で同時導入を試みて撤回した機能群のうち、データモデルに関わる部分を正式仕様として確定する。実装順序は§13ロードマップの通り「1機能=1マイグレーション=1セッション」で導入し、同一コミットで済ませない。

#### 5.8.1 ホワイトボード(v6マイグレーション)

Heptabase風の無限キャンバス。MyBase(§15参照)の画面発想と、txt.mdの「ノード関係可視化ホワイトボード」構想を統合する。既存の`entry`/`connection`モデルを再利用し、独立したノードシステムを作らない(§3.3の判断を踏襲)。

```kotlin
@Entity(tableName = "whiteboard")
data class WhiteboardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ボード上のフリーテキストメモ(entryと紐付かない、キャンバス固有のカード)
@Entity(tableName = "whiteboard_note")
data class WhiteboardNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val whiteboardId: String,
    val content: String,
    val x: Float, val y: Float, val width: Float, val height: Float,
    val colorHex: String? = null
)

// ボード上のentry参照カード(既存entryをキャンバスに配置したもの)
@Entity(tableName = "whiteboard_node")
data class WhiteboardNodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val whiteboardId: String,
    val entryId: String,                // 既存entryへの参照。entry自体は複製しない
    val x: Float, val y: Float, val width: Float, val height: Float,
    val sectionId: String? = null
)

// ボード上の領域枠(グルーピング用)
@Entity(tableName = "whiteboard_section")
data class WhiteboardSectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val whiteboardId: String,
    val title: String,
    val x: Float, val y: Float, val width: Float, val height: Float,
    val colorHex: String? = null
)
```

カード間の接続線は新テーブルを作らず、既存の`connection`テーブル(§5.5.3)を再利用する。`whiteboard_node`同士が線で結ばれた場合、裏側では対応する`entry`同士に`connection(relationType='related', isAuto=false)`が作成される。これにより、ホワイトボードで作った関係が検索・接続候補エンジンからも一貫して見える。

**実装メモ(実装原則§2.5準拠)**: `entities`配列への追加・`AppDatabase.version`変更・`DatabaseModule`の`@Provides`追加の3点セットが必要。1つでも欠けるとRoomのコンパイル時検証(KSP)が失敗する。3ファイルを同一コミットで揃えること。

#### 5.8.2 Wikipedia記事ビルダー(v7マイグレーション、B-1完了後)

エントリー群から長文の「自分だけの百科事典記事」を編むための型。既存の13型CTIパターンを踏襲せず、独立テーブルとする(記事は複数entryを横断参照するため、単一entryの拡張として表現しにくいことによる意図的な設計判断)。

```kotlin
@Entity(tableName = "wiki_article")
data class WikiArticleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val contentMd: String,              // 本文(Markdown、§11.9のリッチテキスト描画で表示)
    val summary: String? = null,
    val relatedEntryIdsJson: String = "[]",  // 参照元entryへのリンク
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### 5.8.3 カスタムフィールド(独立マイグレーション、最小スコープ)

13型のいずれにも属さない自由記述項目を、型定義を変更せずに追加するための逃げ道。§5.4の型別拡張テーブルにフィールドを都度追加するのはCTIの波及コストが高い(§13実装メモ参照)ため、汎用テーブルで吸収する。

```kotlin
@Entity(tableName = "entry_custom_field")
data class EntryCustomFieldEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val fieldName: String,
    val fieldValue: String,
    val sortOrder: Int = 0
)
```

新規追加は既存13型のUI(`EntryEditScreen`のwhen分岐等)には一切触れず、エントリー詳細画面に「カスタムフィールド」セクションを追加するだけで実現する(既存コードを唯一の正とする原則の実践例)。

#### 5.8.4 和暦マスタ(独立マイグレーション、既存機能のバグ修正)

多段階採点エンジン(§8.4)の暦変換ステージは近現代5元号のみに対応しており、設計書自身が例示する「1600年=慶長5年」が採点できないという既知のバグがある。日本の元号は248個あるが全網羅は不要と判断し、以下の方針で運用する。

```kotlin
@Entity(tableName = "era_master")
data class EraMasterEntity(
    @PrimaryKey val name: String,       // '慶長','元禄','明治'...
    val startYear: Int,                 // 元年の西暦(例: 慶長=1596)
    val endYear: Int?,                  // null = 現在も継続中の元号
    val sortOrder: Int
)
```

初期データは江戸期以降+歴史教育で頻出する著名な古典元号(天正・慶長・元禄・享保・寛政・天保等)を優先し、ハードコードではなくこのテーブルへのシードデータ(assets内JSON→初回起動時インポート)として持たせる。これにより、後から必要な元号が見つかった場合もコード変更なしにレコード追加だけで拡張できる。**年変換ロジックは`元年西暦 + (yearInEra - 1)`とし、`baseYear`のような曖昧な命名は避ける**(v12.0での命名の曖昧さがバグの一因だったため)。

#### 5.8.5 SRS履歴テーブルの拡張

§5.5.5の`srs_review`に、SM-2の反復回数(`repetitionCount`)を追加する。従来は間隔日数から反復回数を逆算していたが、この推定ロジックが脆弱であるため、FSRS移行(§8.8)を待たずに今のうちへレビュー時点の状態を明示的に記録する形へ変更する。

```kotlin
// srs_reviewへのカラム追加(マイグレーション)
// ALTER TABLE srs_review ADD COLUMN repetitionCount INTEGER NOT NULL DEFAULT 0
```

---

## 6. ストレージ・バックアップ戦略

### 6.1 ファイル保存方針

- Room DB本体(SQLiteファイル)が唯一の真実(Source of Truth)
- 画像・PDF等のBLOBは`blobs/`ディレクトリに保存し、DBにはパスのみ格納(DBの肥大化・バックアップ速度低下を防ぐ)
- 将来的な「何十巻もの百科事典」規模を見据え、端末ストレージの逼迫を検知したら古いWebページのサムネイル等から自動整理を提案する(§7.5.2「情報寿命」参照)

### 6.2 クラウド連携方針(SAF方式に確定・v13.0で更新)

v11.0時点ではGoogle Drive API直接連携を想定していたが、実装検討の結果、**SAF(Storage Access Framework)でユーザーが任意のクラウド同期フォルダを指定する方式に確定する**。Drive APIは不要。

**理由**: OAuth同意画面の実装・審査、トークンリフレッシュ、Drive APIのクォータ管理といった複雑さを丸ごと回避できる。ユーザーが既に端末にインストールしているクラウド同期アプリ(Google Drive for Android・Dropbox等)のドキュメントプロバイダーをSAFのフォルダ選択ダイアログから直接指定すれば、Androidは「ローカルのファイル書き込み」をするだけで、同期そのものは既存アプリに委ねられる。エコの3軸のうち特に認知コストの削減に直結する判断であり、既存のGoogle Drive前提の記述はすべてこの方式に置き換える。

```
[SAFで指定したフォルダ]/(実体はGoogle Drive等が同期する)
├── imports/          # PCが置く。Androidが定期取り込み→processed/へ移動
│   ├── notes/         # Markdown
│   ├── flashcards/     # CSV (term,reading,definition,field... = entry_definitionへマッピング)
│   └── quiz/           # JSON (LLM生成のquiz_bank形式にそのまま対応、§12.4参照)
├── backups/
│   ├── db-snapshots/   # SQLiteファイル丸ごとの暗号化コピー(完全復元用)
│   └── portable/       # Markdown/CSV/JSONへのエクスポート(長期可搬性の保険)
```

PC側(React Webアプリ)も同様に、Chrome系ブラウザのFile System Access APIで同じ同期フォルダを直接指定できる場合はそれを使い、対応していないブラウザでは手動ダウンロード/アップロードにフォールバックする。これにより**Android・PCの両方でDrive API/OAuthを一切実装せずに済む**構成になる。

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

### 6.5 バックアップ成功の可視化(v13.0で新設)

SAF書き出し処理を実装しただけでは、認証切れ・フォルダ削除・同期アプリの不具合等によるサイレント失敗にユーザーが気づけない。これは「実質的にバックアップが無い」のと同じであるため、以下を必須要件とする。

- 設定画面またはダッシュボードに**「最終バックアップ成功日時」**を常時表示する
- 24〜48時間以上バックアップに失敗し続けた場合、通知またはダッシュボード上のバッジで警告する
- 端末初期化・機種変更を想定し、復元手順を一度は実際に試す(バックアップから空の状態のアプリへ復元できることを手動確認する)チェックポイントを§13ロードマップに組み込む

### 6.6 通信経路のセキュリティに関する注意

Ktor APIはBearer認証のみでTLS化されていない(§10)。同一Wi-Fi上の他端末からトークンとデータがスニッフィングされるリスクがあるため、設定画面に「自宅など信頼できるWi-Fiでのみ有効化してください」という警告文を表示する。本格的なTLS化(自己署名証明書+証明書ピンニング)は優先度が低い任意対応とする(§16参照)。

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

class GeminiLlm(private val modelRegistry: AiModels) : LlmProvider {
    override suspend fun generate(prompt: String, jsonMode: Boolean, task: AiTask): String {
        for (model in modelRegistry.candidatesFor(task)) {
            runCatching { return callGemini(model, prompt, jsonMode = jsonMode) }
        }
        throw LlmUnavailableException()
    }

    // Grounding(Google検索)とJSON構造化出力は同時使用不可のため二段階で回避
    suspend fun generateGroundedThenStructure(searchPrompt: String, structureTemplate: String): JsonObject {
        val searchResult = callGemini(modelRegistry.primaryFor(AiTask.FACT_CHECK), searchPrompt, grounding = true)
        return Json.parseToJsonElement(
            generate(structureTemplate.format(searchResult), jsonMode = true, task = AiTask.STRUCTURE_EXTRACT)
        ).jsonObject
    }
}

// providerの選択(Gemini / Ollama)は§7.7参照。Android・PC両方で同じ切替ロジックを使う
fun getLlm(settings: Settings): LlmProvider =
    if (settings.ollamaBaseUrl != null) OllamaLlm(settings.ollamaBaseUrl) else GeminiLlm(AiModels(settings))
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

#### 7.4.4 Geminiモデルレジストリ(v14.0で具体化・2026年8月調査)

「無料重視」の方針上、タスクの重さに応じてモデルを使い分ける。高性能モデルを全タスクに使うとコストが嵩むため、軽いタスクには意図的に安いモデルを割り当てる(§7.4冒頭の各用途ごとに以下のマッピングを既定値とする)。

| タスク種別 | 既定モデル | 理由 |
|---|---|---|
| Embedding全般 | `gemini-embedding-2` | マルチモーダル対応のGA版。3072次元をMRLで768等に截断(§7.1.1) |
| ファクトチェック調査(Grounding) | `gemini-3.6-flash` | エージェント的タスク・複雑な推論に強く、旧モデルよりトークン効率が良い |
| 構造化抽出・DB自動構築 | `gemini-3.6-flash` | 同上。JSON厳密出力の精度が重要な工程 |
| クイズ自動生成(AI依存分、§8.3の4段目) | `gemini-3.6-flash` | ひっかけ選択肢の質が体験に直結するため中位以上のモデルを使う |
| 記述式の意味的採点の下支え(LLM補助) | `gemini-3.5-flash-lite` | 高頻度・低コストが最優先。埋め込み類似度で閾値判定できない際の補助のみ |
| ヒント自動生成 | `gemini-3.5-flash-lite` | 同上。軽量タスク |
| コーチング(弱点分析・間違い解説) | `gemini-3.6-flash` | 学習継続に関わる質の高い説明が必要 |
| OCR・画像解析 | `gemini-3.6-flash`(マルチモーダル) | Vision対応モデルを流用し専用モデルを増やさない |
| 安定フォールバック(3.xで障害時) | `gemini-2.5-flash` | Gemini 3系に障害があった場合の枯れた代替 |

```kotlin
enum class AiTask { EMBEDDING, FACT_CHECK, STRUCTURE_EXTRACT, QUIZ_GEN, GRADING_ASSIST, HINT_GEN, COACHING, OCR }

class AiModels(private val settings: Settings) {
    private val defaults: Map<AiTask, List<String>> = mapOf(
        AiTask.EMBEDDING to listOf("gemini-embedding-2"),
        AiTask.FACT_CHECK to listOf("gemini-3.6-flash", "gemini-2.5-flash"),
        AiTask.STRUCTURE_EXTRACT to listOf("gemini-3.6-flash", "gemini-2.5-flash"),
        AiTask.QUIZ_GEN to listOf("gemini-3.6-flash", "gemini-2.5-flash"),
        AiTask.GRADING_ASSIST to listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite"),
        AiTask.HINT_GEN to listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite"),
        AiTask.COACHING to listOf("gemini-3.6-flash", "gemini-2.5-flash"),
        AiTask.OCR to listOf("gemini-3.6-flash"),
    )
    // 設定画面(§11.10)でタスクごとにモデルを上書き可能。未設定ならdefaultsを使う
    fun candidatesFor(task: AiTask): List<String> = settings.modelOverrides[task] ?: defaults.getValue(task)
    fun primaryFor(task: AiTask): String = candidatesFor(task).first()
}
```

**運用上の注意**: Gemini 3.xモデル群はプレビュー版が頻繁に入れ替わる(§4.1.1参照)。実装セッションのたびに`https://ai.google.dev/gemini-api/docs/models`を確認し、非推奨(deprecated)モデルが指定されていないかをチェックすること。モデルIDは設定値(`Settings.modelOverrides`)として外部化されているため、モデル切替がコード変更を伴わない設計になっている(実装原則§2.5・拡張性設計§9.2と一貫)。



現行設計は「空間(接続・類似度)」に強いが、人間の記憶は「時系列」に強く依存する。フェーズ0〜3で以下を確実に積み立てておけば、将来の拡張(Temporal Layer・Resurfacing Engine)を妨げない。

- `accessedAt`を閲覧の都度、確実に更新する(必須)
- `isMuted`でノイズを管理する(必須、自動muteは行わずサジェストのみに留める)
- `search_document.combinedText`を型別戦略で丁寧に構築する(必須、将来の関連性エンジンの精度を決める)
- 半年以上未アクセスかつ`isFavorite=false`のwebpage/liked/ai_convは「整理しませんか」提案の対象候補とする(自動削除・自動muteはしない)

### 7.6 スレッドセーフ設計(v13.0で明文化)

§5.5.2の`InMemoryVectorIndex`は、検索(`topK`)からの読み取りとEmbedding追加(`addVector`)からの書き込みが並行して発生しうる。このワークロードは読み取りが圧倒的多数・書き込みが稀という特性を持つため、**Mutexで毎回ロックするのではなく、immutableな配列をAtomicReferenceで差し替える方式を採用する**。

```kotlin
class InMemoryVectorIndex {
    private val ref = AtomicReference(VectorSnapshot(emptyArray(), emptyArray()))
    private data class VectorSnapshot(val ids: Array<String>, val vectors: Array<FloatArray>)

    fun topK(query: FloatArray, k: Int): List<Pair<String, Float>> {
        val snapshot = ref.get()   // ロック不要。読み取りは常に一貫したスナップショットを見る
        return snapshot.ids.indices.map { i -> snapshot.ids[i] to cosineSimilarity(query, snapshot.vectors[i]) }
            .sortedByDescending { it.second }.take(k)
    }

    fun addVector(id: String, vector: FloatArray) {
        // compare-and-swapで安全に差し替える。書き込みが稀なため配列コピーのコストは許容範囲
        while (true) {
            val old = ref.get()
            val newSnapshot = VectorSnapshot(old.ids + id, old.vectors + vector)
            if (ref.compareAndSet(old, newSnapshot)) break
        }
    }
}
```

`GeminiClient`のレート制御用`lastCallAt`は、クリティカルセクションが短く単純なため、こちらは`Mutex`で問題ない(読み取り頻度が低いワークロードにMutexを避ける理由がないため)。

### 7.7 Ollama Provider詳細(v14.0でPC対応・最新情報を反映)

LAN内に自宅PC等でOllamaサーバーが稼働している場合、Gemini APIの代わりにこれを使う。DBを持たない純粋な推論エンドポイントであるため、「PC=閲覧専用・データ本体はAndroid」という原則には抵触しない(§0.1参照)。2026年8月時点のOllama(v0.32系)はOpenAI互換の`/v1/chat/completions`エンドポイントを提供しており、実装を素直にできる。

```kotlin
// Android側(既存)
class OllamaClient(private val baseUrl: String) : IAiProvider {
    override suspend fun generate(prompt: String, jsonMode: Boolean): String {
        // OpenAI互換エンドポイントを使う(素のOllama API /api/generate より
        // ツール呼び出し・ストリーミングの挙動が標準化されており将来の移行コストが低い)
        val body = buildJsonObject {
            put("model", settings.ollamaModel ?: "qwen3.6")  // 設定画面(§11.10)で変更可能
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", prompt) } }
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
        }
        return httpClient.post("$baseUrl/v1/chat/completions") { setBody(body) }.bodyAsJsonField("content")
    }
    // 埋め込みは/api/embeddingsを利用可能だが、Gemini Embeddingとの次元数の不一致(§7.1.1)に注意。
    // 混在させる場合はモデルごとに別のembeddingカラム(model名で判別)を持たせる必要がある
}
```

**PC(React Webクライアント)からの直接呼び出し(v14.0で新設)**: 従来はAndroid経由でのみOllamaを利用していたが、PC側からLAN内のOllamaへ直接HTTPで到達できる場合、Androidを介さず直接呼び出せるようにする。これにより、PC単体での下書き作成・要約時にAndroidアプリを起動していなくても補助AIが使える(データの読み書き自体は引き続きKtor API経由でAndroidのRoom DBに対して行うため、「Android=データ本体」の原則は崩れない)。

```typescript
// web/src/lib/ollamaClient.ts
export async function generateViaOllama(baseUrl: string, model: string, prompt: string): Promise<string> {
  const res = await fetch(`${baseUrl}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages: [{ role: "user", content: prompt }] }),
  });
  const data = await res.json();
  return data.choices[0].message.content;
}
// 設定画面(§11.10)でOllamaのLAN内アドレス(例: http://192.168.1.10:11434)を保存し、
// PC・Android双方が同じ値を参照する(SAF経由の設定ファイル同期、または手動入力)
```

推奨モデル(2026年8月時点、Ollamaで動作確認された軽量〜中量級): 汎用は`qwen3.6`(コンシューマ向けGPUでも実用的な性能)、コーディング補助が必要な場面は`kimi-k2.6`や`qwen2.5-coder`、軽量端末では`gpt-oss:20b`。いずれもモデル名は設定で変更可能とし、コードにハードコードしない(§7.4.4と同じ設計思想)。

**注意**: OllamaのEmbeddingモデルとGemini Embeddingは次元数・意味空間が異なるため、混在運用する場合は`embedding.model`カラム(§5.5.2)で区別し、検索時に同一モデルのベクトル同士でのみ類似度計算を行う。異なるモデルのベクトルを直接比較しない。

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

### 8.7 クイズバリエーション拡充(v14.0で大幅拡充・QuizKnock/Kahoot/Duolingo等を調査)

「QuizKnockの企画ばりにバリエーションを充実させたい」という要望を受け、複数のクイズ/学習サービスの企画・UXパターンを調査し、良い部分を移植する。すべてRhinoプラグイン(`quizType = "custom"`)またはビルトイン型の追加として実装し、§8.1〜8.4のコア出題フロー(ジャンル→分野→タグ→出題→採点→スコア)には影響を与えない。

#### 8.7.1 既存ラインナップ(quizstudy.md由来)

| 機能 | 概要 |
|---|---|
| 読み上げ | Android標準TextToSpeechで問題文・解説を音声化 |
| 早押し | 複数選択肢を同時表示しタップ速度を採点に加味 |
| タイムアタック | 制限時間内の連続正解数を記録 |
| 縦書き問題 | 国語向け。Compose上でのCJK縦書きレイアウト |
| 漢字書き取り | 手書き入力(ML Kit Digital Ink Recognition等)またはキーボード入力+厳密一致判定 |
| リスニング問題 | 音声ファイル(`entry_media.blobPath`)再生+聞き取り回答 |
| タイピング/コピー問題 | 純粋な入力速度・正確性の測定 |

#### 8.7.2 QuizKnock由来の追加企画(2026年8月調査)

QuizKnockの動画企画・「Quiz Pitcher」(ブラウザ完結の出題サービス)・謎解きコンテンツを調査し、以下を追加する。

| 機能 | 概要 | quizType |
|---|---|---|
| 書きかけ漢字/四字熟語パズル | 画数・文字数を段階的に開示し、最少の情報で当てる。「2画目までで言葉を当てよう」のような形式 | `kanji_reveal` |
| プレッシャーテスト(全列挙型) | 「◯◯に分類されるものを制限時間内にできるだけ多く挙げよ」形式。同一分野・同一タグのentry群から正解集合を動的生成し、回答をリアルタイム照合する | `enumerate_challenge` |
| サバイバル(一発アウト)形式 | 1問でも間違えると即終了し、そこまでの連続正解数を記録する。タイムアタックの「制限時間」を「間違い回数」に置き換えた変種 | `survival` |
| 複合ヒント推理(謎解き型) | 複数の断片的ヒント(§8.5の3段階ヒントとは別に、最初から複数のヒントが同時に見えている状態)を組み合わせて1つの答えを導く形式。`entry`の複数の関連フィールドをヒントとして自動構成できる | `riddle` |

#### 8.7.3 Kahoot / Duolingo由来のUXパターン(調査結果の移植)

個人学習アプリのため「対戦・ライブ配信」要素はそのままでは移植できないが、UXパターンとしては流用できる部分が多い。

| 由来 | パターン | 個人学習アプリへの翻案 |
|---|---|---|
| Kahoot | 回答速度に応じたボーナス加点 | 早押し(§8.7.1)のスコア計算式を「正解かつ速いほど高得点」に一般化する。`quiz_attempts.score`の計算に経過時間を係数として組み込む(§5.6のスキーマに`answeredWithinMs`列を追加) |
| Kahoot | ライブリーダーボード | 個人利用のため「自己ベスト更新」表示に翻案(タイムアタック・サバイバルの自己記録更新をダッシュボードで通知) |
| Duolingo | マッチング(ペア合わせ) | 用語と定義、あるいは英単語と訳語をドラッグ&ドロップでペアリングする形式。`entry_definition`複数件から動的に出題セットを構成できる | `matching`(下記参照) |
| Duolingo | 選択式穴埋め(単語バンクから選ぶ) | 既存`fill_blank`の選択肢付きバリエーション。自由記述よりタップ操作に強いモバイルUXとして追加 | `fill_blank_choice` |
| Duolingo | ストリーク(継続日数)・ハート(ライフ)制 | 個別クイズタイプではなく、アプリ全体の継続動機付け機能として扱う。§11.10ダッシュボードに「学習継続日数」を表示する既存機能(§11.3)を土台に、任意でハート制(1日の誤答許容数)を追加できるようにする(強制はしない。ストレスになりうるためデフォルトOFF) |

```typescript
// packages/shared-types/plugin.ts への追加(マッチング問題の例)
export const MatchingQuestionData = z.object({
  pairs: z.array(z.object({ left: z.string(), right: z.string(), entryId: z.string().optional() })),
});
// renderSchemaはUISchemaの{ type: "column", children: [...] }を組み合わせ、
// ドラッグ&ドロップは§8.7.4のcustomコンポーネントで実装する(スキーマ表現力を超えるため)
```

#### 8.7.4 実装方針と優先順位

- 既存プラグイン契約(§9.1)をそのまま使う。新しい`quizType`を追加するたびにビルトインエンジンを変更する必要はない
- ドラッグ操作を伴うもの(マッチング)は§9.1の「スキーマ表現力を超えるUI」に該当するため`{ type: "custom", componentId: "matching" }`として、Web/Android双方に事前実装済みのネイティブコンポーネントを用意する
- 実装順序は実装原則§2.5(1機能1ビルド)に従い、§13ロードマップのRound6以降(ゲーミフィケーション拡張)で1形式ずつ追加する。優先度は「既存の出題エンジンを再利用でき、新規UIコンポーネントが不要なもの」から着手する: プレッシャーテスト(全列挙型)・サバイバル形式(ロジックのみ、UIは既存の一問一答流用) → 書きかけ漢字パズル・マッチング(新規UI必要) → 複合ヒント推理

### 8.8 FSRS詳細仕様(v13.0で具体化)

§8.6で示したFSRS移行を、`SRS_ALGORITHM`設定によるSM-2との切替として実装する。既存のSM-2コードは変更せず残す(実装原則§2.5「動くコードを壊さない」の実践)。

```kotlin
interface SrsAlgorithm {
    fun calculate(history: List<SrsReviewEntity>, grade: Int): SrsCalcResult
}

class Sm2Algorithm : SrsAlgorithm { /* 既存実装、変更なし */ }

class FsrsAlgorithm : SrsAlgorithm {
    // §8.6のデフォルトパラメータを使用
    private val initialStability = 3.0
    private val stabilityGrowthRate = 1.9
    private val targetRetention = 0.9

    override fun calculate(history: List<SrsReviewEntity>, grade: Int): SrsCalcResult {
        val lastReview = history.maxByOrNull { it.reviewedAt }
        val stability = lastReview?.let { computeNextStability(it, grade) } ?: initialStability
        val interval = computeInterval(stability, targetRetention)
        return SrsCalcResult(intervalDays = interval, stability = stability, repetitionCount = history.size + 1)
    }
    // computeNextStability / computeInterval の詳細な数式は実装セッションで確定する(§13 Round4参照)
}

// DIでの切替(§9.2のBrainModuleに準拠)
fun provideSrsAlgorithm(settings: Settings): SrsAlgorithm =
    if (settings.srsAlgorithm == "fsrs") FsrsAlgorithm() else Sm2Algorithm()
```

移行前提として、§5.8.5で`srs_review.repetitionCount`カラムを追加しておくこと。これにより両アルゴリズムが同じ履歴テーブルを参照でき、ユーザーが設定でSM-2⇄FSRSを切り替えても学習履歴が失われない。

### 8.9 和暦マスタの設計と運用(v13.0で具体化・既知バグの修正仕様)

§5.8.4で定義した`era_master`テーブルを、多段階採点エンジン(§8.4のステージ2「暦・数値変換」)から参照する。

```kotlin
class EraConverter(private val eraDao: EraMasterDao) {
    suspend fun toWesternYear(eraName: String, yearInEra: Int): Int? {
        val era = eraDao.getByName(eraName) ?: return null
        return era.startYear + (yearInEra - 1)   // 元年西暦 + (年数-1)。曖昧な変数名は避ける
    }
}
```

`MultiStageGrader`のテスト(§13 P0.5 J-1)で「1600年=慶長5年」を明示的なテストケースとし、`era_master`に慶長(startYear=1596)が投入されていることを前提に検証する。元号データが不足している場合は、採点を「不一致」として扱うのではなく「判定不能」として明示し、ユーザーに同義語登録(§8.4ステージ4)を促す設計とする。

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

### 10.1 ルーティング分割アーキテクチャ(v13.0で新設)

v12.0実装時、`LocalServer`が8つのDAOを直接コンストラクタインジェクションで受け取り、全APIエンドポイントを単一クラスに集約していたことが判明した。エンドポイント追加のたびに1ファイルが肥大化し、1ファイル1000行以下原則(§2.1)にも反するため、エンドポイント単位でファイルを分割する。

```kotlin
// server/routes/EntryRoutes.kt
fun Route.entryRoutes(entryRepository: EntryRepository) {
    route("/api/entries") { /* CRUD */ }
}
// server/routes/SearchRoutes.kt, ConnectionRoutes.kt, QuizRoutes.kt... も同様に分割

// server/LocalServer.kt はルーティングの登録のみを行う薄いエントリポイントにする
fun Application.configureRouting(deps: ServerDependencies) {
    routing {
        authenticate("token-auth") {
            entryRoutes(deps.entryRepository)
            searchRoutes(deps.searchEngine)
            quizRoutes(deps.quizRepository, deps.grader)
            // ...
        }
    }
}
```

**注意(実装原則§2.5準拠)**: `embeddedServer(Netty, port)`の返り値型`EmbeddedServer<*, *>`はKotlinの型推論と衝突しやすい。明示的な型引数を書くか、返り値を変数に代入せず`.start(wait = false)`まで一気に呼ぶ形にする。またDTO(`EntryResponse`, `SrsReviewRequest`等)は20個以上になる見込みのため、`@Serializable`の付け忘れが1件でも全体ビルドを止める点に注意し、DTO専用ファイル(`server/dto/`)にまとめてレビューしやすくする。

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

### 11.6 ホワイトボード画面(v13.0で具体化・§5.8.1のデータモデルに対応)

MyBase(§15参照)の画面発想を踏まえつつ、Android/Compose上でHeptabase風の無限キャンバスを実装する。

- **パン/ズーム**: `detectTransformGestures`で実装
- **カードドラッグ**: `detectDragGestures`で実装
- **⚠️ 既知の実装リスク**: `detectTransformGestures`と`detectDragGestures`を同一`Modifier.pointerInput`内で併用するとジェスチャイベントを奪い合う。単一のカスタムジェスチャ検出器に統一するか、モード切替(パン/ズームモード⇔カード編集モードをトグルボタンで明示的に切り替える)で衝突を回避する設計を先に固めてから着手する
- **セクション(領域枠)**: `whiteboard_section`をドラッグでリサイズ可能な矩形として描画
- **接続モード**: カード間をタップで結ぶと、裏側で`connection`テーブルにエントリーを作成する(§5.8.1参照)
- **型なしカード作成**: `whiteboard_note`として作成し、後から「entryに昇格」ボタンで`entry(type='thought')`へ変換できる導線を用意する

### 11.7 Wikipediaビルダー画面(v13.0で具体化・§5.8.2のデータモデルに対応)

- **記事一覧**: `wiki_article`の一覧・検索・並び替え
- **記事閲覧**: §11.8のリッチテキスト描画でMarkdownをレンダリング
- **記事編集**: 長文エディタ+プレビューの分割表示(タブ切替でモバイル幅に対応)
- **エントリーからの記事化**: エントリー詳細画面に「この内容を記事に追記」ボタンを設け、`relatedEntryIdsJson`に参照元を積みながら記事本文へ引用する

### 11.8 リッチテキストエディタのAndroid実装(v13.0で具体化)

PC側はTiptap(§11.5)だが、Android側は軽量なWebView+Markdownレンダラーで同等の閲覧体験を実現する。

```kotlin
// RichContentView.kt (新規コンポーネント)
@Composable
fun RichContentView(markdown: String, onInternalLink: (entryId: String) -> Unit) {
    // WebView + marked.js(Markdown→HTML変換) + KaTeX(数式) + highlight.js(コード)をローカルアセットから読み込む
    // wiki-linkは `[[entryId|表示名]]` 記法をパースし、タップ時にonInternalLinkを呼ぶ
}
```

**⚠️ 実装原則§2.5に基づく注意**: 既存の`MarkdownText`コンポーネント(パラメータ名`text`, `onWikiLinkClick`)を置き換えない。`RichContentView`は新規コンポーネントとして追加し、`EntryTypeSections`等の既存呼び出し箇所は変更しない。両者は当面併存させ、リッチ機能(数式・コードハイライト)が必要な画面(Wikipediaビルダー等)でのみ`RichContentView`を新規に使う。

### 11.9 大規模画面の分割方針(v13.0で新設)

`EntryEditViewModel`(800行超)・`DashboardScreen`・`EntryDetailScreen`(300〜800行)が単一ファイル/単一Composable関数に集約されている。以下の方針で分割する。

- `EntryEditViewModel` → 型別の`EntrySaver`インターフェース+実装(13型それぞれ)に分離(§9.2のInterfaceパターンを踏襲)
- 大規模Composable → `remember`/`collectAsState`が20個を超えている場合、状態をより小さな単位のサブComposableへ分割し、リコンポジション範囲を局所化する
- `AlertDialog`内で`LazyVerticalGrid`を使う箇所はスクロール競合の既知不具合があるため、ダイアログ内のグリッド表示は`heightIn`で高さを固定するか、ボトムシートへの置き換えを検討する

### 11.10 設定画面(v14.0で新設・§11.3の「設定」を具体化)

§11.3の画面一覧にある「設定」を、実際に必要な項目まで具体化する。

| セクション | 項目 |
|---|---|
| バックアップ | SAF連携フォルダの指定、最終バックアップ成功日時(§6.5)、手動バックアップ実行、復元 |
| セキュリティ | LAN通信リスクの警告表示(§6.6)、共有トークンの再発行 |
| AIプロバイダ | Gemini APIキー(暗号化保存)、タスク別モデル上書き(§7.4.4の`modelOverrides`)、Ollama使用有無・LANアドレス・使用モデル(§7.7) |
| PC連携 | Ktorサーバーのオン/オフ、現在の接続トークン表示(QRコード等でPCから読み取りやすくする) |
| 学習設定 | SRSアルゴリズム(SM-2/FSRS、§8.8)、ヒント許容数、ハート制のオン/オフ(§8.7.3) |
| データ管理 | ストレージ使用量、型別件数、可搬エクスポート実行、和暦マスタの確認・追加(§5.8.4) |

---

## 12. 外部連携・インポート/エクスポート仕様

### 12.1 インポートパイプライン共通フロー(Knowledge OS v10 §10.1を継承)

```
入力ソース → Adapter(ソース別変換) → EntryCreateSchemaで正規化・バリデーション
  → entry + 拡張テーブルへ保存 → Embeddingキューへ追加 → 接続候補生成キューへ追加
```

SAF経由の非同期取り込みも、Android上でのURL直接取り込みも、この同一パイプラインを通る(実装の二重化を避ける、PersonalEncyclopedia §3の設計方針を継承)。

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

### 12.5 Trie木による自動ハイパーリンク+定義プレビュー(LearningSheet v25 + ALH §4.5を移植、v14.0でプレビュー機能を追加)

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

ノート・定義・記事(§5.8.2 Wikipediaビルダー含む)の本文中に他entryのタイトルが出現した場合、自動でハイパーリンク化する(閲覧時のみのUI装飾。`connection`テーブルへの書き込みは行わず、ユーザーがタップして初めて手動接続を提案する形にすることで、§5.5.3の接続候補承認フローと矛盾しないようにする)。

対象は`entry_definition`(用語集)に限らず**全13型のentryタイトル**であり、人物・書籍・場所・出来事なども自動リンクの対象になる(「データ内の記述から自動で検出して用語のデータがあればそこへのリンクを作る」という要件をこの仕組みでカバーする)。

**定義プレビュー(v14.0で新設)**: リンクをタップした際に即座に別画面へ遷移するのではなく、Wikipediaのホバープレビューに類似した軽量なプレビューカード(タイトル・型アイコン・`entry.summary`または`entry_definition.definition`の冒頭2〜3行)をその場にポップアップ表示し、「開く」を選んだ場合のみ詳細画面へ遷移する。これにより、文章を読む流れを大きく中断せずに関連情報を確認できる。

```kotlin
// EntryPreviewPopup.kt(新規Composable)
@Composable
fun EntryPreviewPopup(entryId: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val entry by entryRepository.observe(entryId).collectAsState(initial = null)
    // entry.summary(AI生成) ?? 型別拡張テーブルのdefinition等を優先表示
    // Popup / DropdownMenuベースの軽量実装とし、新規画面遷移を伴わない
}
```

### 12.6 タグ・表記揺れの自動統合(LearningSheet v25 §5を移植)

新規タグ作成時、既存タグとのEmbeddingコサイン類似度が0.85以上のものを「表記揺れの可能性」としてサジェストする(例: 「WW1」と「第一次世界大戦」)。自動統合はせず、ユーザーが確認して統合を選べるようにする(接続候補承認と同じ「暴走させない」設計思想)。

### 12.7 重複排除アーキテクチャ(v13.0で新設)

URL・メモ・クイズ・CSV・Obsidianの5つのインポート経路それぞれで、同一内容の重複登録が起こりうる。経路ごとに個別実装せず、共通のインターフェースで吸収する。

```kotlin
interface DuplicateDetector {
    suspend fun findDuplicate(candidate: EntryCreateRequest): EntryEntity?
}

class UrlDuplicateDetector : DuplicateDetector {
    // entry_webpage.urlの完全一致で判定
}
class ContentHashDuplicateDetector : DuplicateDetector {
    // メモ・クイズ等、URLを持たない型はcontentの正規化ハッシュで判定
}

// 各インポートパイプライン(§12.1)の正規化ステップに差し込む
suspend fun importWithDedup(request: EntryCreateRequest, detector: DuplicateDetector) {
    val existing = detector.findDuplicate(request)
    if (existing != null) { skipAndLog(existing); return }
    createEntry(request)
}
```

CSV/Obsidianのような一括インポートでは、インポート単位ごとに件数(新規/スキップ)をサマリー表示し、ユーザーが結果を確認できるようにする。

---

## 13. 開発ロードマップ(v13.0で実績・教訓を反映し全面更新)

> **最重要原則(Knowledge OS v10 §12 + 実装原則§2.5を継承)**: 各フェーズのゴールは次フェーズへの移行ではなく「実際に毎日使うこと」。かつ、フェーズ内の各タスクも「1機能1ビルド」を単位として進める。フェーズ全体を一括で「完了」と自己申告しない。

### フェーズ0〜3 — 実装済み(実績)

txt.mdの「第一段階=入力・単語帳・クイズの基本学習ツール」に相当する範囲は完了している。

| フェーズ | 内容 | 状態 |
|---|---|---|
| フェーズ0 | entry基本型(thought/definition)のCRUD、Ktorサーバー雛形、キーワード検索 | ✅ 完了 |
| フェーズ1 | SM-2 SRS、quiz_bank/quiz_attempts、多段階採点、ルールベースクイズ生成、ローカルバックアップ | ✅ 完了 |
| フェーズ2 | FTS4+Nグラム、Gemini Embedding、Hybrid Search(RRF)、全13型のRoomスキーマ、Webスクレイパー | ✅ 完了 |
| フェーズ3 | connection/connection_candidate、Rhinoプラグイン(builtin-mcq)、AI解説・コーチング、15画面のUI、ダークモード | ✅ 完了(DB v5) |

v12.0でフェーズ4相当の機能(ホワイトボード・Wiki・FSRS・Ollama等10機能)を同時導入しようとし、コンパイルエラーの連鎖でgitによりフェーズ3完了時点(DB v5)へ撤回した(§0.1・§2.5.4参照)。以下のフェーズ4は、その教訓を反映し**Round単位(新テーブル・新DAOメソッドを増やさないものから順)**で再設計する。

### フェーズ4 — Round方式による再導入(実装原則§2.5を厳格適用)

各Roundの中でも実際には1ファイルごとにコミット+ビルド確認する。「Round」はまとめて提示してよい単位ではなく、リスクの低さで束ねた目安。

**Round 1 — 新テーブル・新DAOメソッドを一切増やさない(最速・最安全)**
- APIキー暗号化(§6.6と合わせて実施)
- タグタップ検索(§11.3検索画面への遷移追加)
- 遷移アニメーション高速化(NavHost引数のみ)
- N+1クエリ解消(既存DAOへの一括取得クエリ追加)
- 重複排除(§12.7、既存リポジトリへのチェック追加)
- クイズ一覧画面(既存`QuizDao`/`QuizBankEntity`のみで完結)
- 🛑 ビルド確認 → 数日使用

**Round 2 — データ保護(新テーブルなし)**
- SAFバックアップ(§6.2)+成功可視化(§6.5)
- LAN通信リスクの警告文(§6.6)
- 既存マイグレーション(v1→v5)のテスト整備(`MigrationTestHelper`、まだv6は作らない)
- 🛑 ビルド確認 → バックアップが実際に成功し続けることを数日確認、復元を一度手動で試す

**Round 3 — テスト基盤ゲート(ここが閉じるまでRound 5以降のスキーマ変更に着手しない)**
- 採点エンジンテスト(§8.4、「1600年=慶長5年」を明示ケースに含める) → 和暦マスタ修正(§5.8.4, §8.9)
- SRSテスト(§8.6)、検索テスト(NgramTokenizer/cosineSimilarity/RRF)
- スレッドセーフ化(§7.6)→並行アクセステストで検証
- 🛑 ビルド確認。ここでテスト基盤ゲートをクリア

**Round 4 — 既存構造内での機能拡張(新テーブルなし)**
- FSRS移行(§8.8、`SrsRepository.recordReview`の分岐のみ。SM-2は残す。事前に`repetitionCount`カラムを追加)
- リッチテキスト描画`RichContentView`(§11.8、既存`MarkdownText`は置き換えない)
- 🛑 ビルド確認

**Round 5 — 初めての新テーブル導入(1つだけ)**
- カスタムフィールド(§5.8.3、最小スコープ。既存13型のUIには触れない)
- 🛑 ビルド確認 → 数日使用

**Round 6以降 — 大型機能(それぞれ単独セッション、実装原則§2.5を厳格適用)**
- ホワイトボード(§5.8.1データモデル → §11.6画面)
- Wikipediaビルダー(§5.8.2データモデル → §11.7画面、リッチテキスト完了が前提)
- Ollama対応(§7.7)→ モデルレジストリ → 2段階ファクトチェック → 教科別AI執筆ルール(§7.4.3) → AI設定UI
- `AUTO_CONNECT_ENABLED=true`化の判断(§5.5.3の3条件を満たしてから)
- PC側 React Flowによるナレッジグラフ可視化、React Webアプリの本格化
- Notion / Obsidian / YouTube Liked インポート
- ゲーミフィケーション拡張(§8.7)をプラグインとして追加
- Ktorルーティング分割(§10.1)、大規模画面の分割(§11.9)等のリファクタ

> フェーズ4の詳細なタスクID・依存関係・実装メモは別紙「Personal Encyclopedia 全タスク一覧 v3」を正とする。本ロードマップはその要約である。

### フェーズ5 — 衛星システム(将来・別途計画)

- [ ] 匿名/OAuth公開共有プラットフォーム(KnOS EX相当)を独立プロジェクトとして検討
- [ ] ナビ/経路案内(txt.mdの「他サービス連携」構想の一部)
- [ ] PWA/SQLite-WASM構成(nextPKM.txt相当、§15参照)。Android版が安定・完成した後、具体的な必要性(iPhone対応等)が生まれた場合にのみ独立プロジェクトとして再検討する

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
| ビルドの健全性 | 常にビルドが通る状態を維持 | 「フェーズ完了」を自己申告するには、ビルド成功+該当テスト成功+3日以上の実使用の3条件を満たすこと(§2.5) |
| テストカバレッジ | 採点エンジン・SRS・検索の中核ロジックは単体テストを持つ | §13 Round3で基盤を構築し、以降の変更はこの上に乗る |
| マイグレーション安全性 | 全マイグレーションが`MigrationTestHelper`で検証済み | 蓄積データのある実機アップグレードでの破損を防ぐ |

### 14.1 開発者ガイド要件(v14.0で新設)

「動作やロジックを自分自身で把握しきれる」ことを、コードが動くことと同じ重みの要件とする。実装原則§2.5が「動くコード」を最優先すると定めたのに対し、本節は「理解できるコード」を並立の必須要件として追加する。コードだけを大量に生成し、本人が中身を説明できない状態は、たとえビルドが通っていても本設計書の目的に反する。

**成果物の三点セット**: 以降、1つの機能(§13の各Round項目)を実装する際は、コード・テスト(§2.5原則1)に加えて**ガイド**を必ず更新する。3点のうち1つでも欠けたセッションは完了とみなさない。

```
docs/guide/
├── 00-overview.md          # 全体アーキテクチャの平易な説明(このアプリは何をどう保存し、どう検索するか)
├── 01-entry-model.md       # entry統一型+CTIとは何か、なぜこの形にしたか、13型の一覧
├── 02-search.md            # Hybrid Search(FTS4+Embedding+RRF)が実際に何をしているか
├── 03-connection.md        # 接続候補承認フローの仕組みと「なぜ自動接続を既定でOFFにしているか」
├── 04-quiz-and-srs.md      # 採点エンジン・SRSアルゴリズム・攻略度スコアの計算方法
├── 05-brain-layer.md       # Embeddingキュー・スレッドセーフ設計・LLMモデル選択の仕組み
├── 06-troubleshooting.md   # よくある不具合と、対応するテストファイルへのリンク
└── glossary.md             # 用語集(entry/CTI/connection_candidate/search_document等)
```

**執筆方針**:
- 対象読者はユーザー本人。前提知識を仮定しすぎず、かつ冗長な入門説明は避ける(大学講義からゼミへ、くらいの解像度)
- 「何をしているか」だけでなく「なぜそうしたか」(設計書の該当節への参照)を必ず添える
- コードスニペットは最小限にし、動作の説明を主体とする(コード自体は設計書・実装ファイルで読める)
- §13の各Roundが完了するたびに該当ガイドファイルを1〜2段落追記する。まとめて後から書かない(実装原則§2.5「1セッション1機能」と同じ粒度で、ガイドも都度更新する)

**デバッグ用テストとの対応**: `06-troubleshooting.md`は、既知の不具合または過去に発生した不具合(§2.5.4の撤回統計等)ごとに、「この不具合を再現/防止するテストはどれか」を一覧化する。これにより、将来同種の不具合が起きた際にどのテストを見ればよいかが本人にもコーディングエージェントにもすぐ分かる状態を保つ。

---

## 15. 今後の拡張ポイント

- **ベクトル検索のスケールアップ**: entry数が10万件を超え体感速度が落ちた場合、`sqlite-vec`拡張(Android向けJNIビルド)への移行を検討する。それまではブルートフォース+インメモリキャッシュで十分
- **FTS5への移行**: 端末のAndroidバージョンがFTS5を確実にサポートする水準まで普及したら、Nグラム手動分割からFTS5+ICUトークナイザへの移行を検討する
- **複数端末対応**: 2台目のAndroidで使いたくなった場合も、Google Driveの`imports/`・`backups/`を経由点にすれば大掛かりな同期基盤を作らずに済む見込み(PersonalEncyclopedia §10の判断を継承)
- **Ktor常時起動化**: 将来的に外出先からもPC経由でアクセスしたくなった場合、Foreground Service化+バッテリー最適化除外設定を検討する(その場合もCloudflare Tunnel等の外部依存は避け、Tailscale等のP2P VPNで直接到達性を確保する方向を優先する)
- **FSRSへの本格移行**: `srs_review`が履歴テーブルである設計上の利点を活かし、既存データを失わずにアルゴリズムを差し替える
- **匿名公開プラットフォーム(KnOS EX相当)**: 本体のPKMとしての完成度を落とさないため、需要が明確になるまでは独立の衛星プロジェクトとして温存する

### 15.1 検討したが不採用とした案: MyBase(Expo/React Native)

Heptabase風の無限キャンバスホワイトボード(index.tsx)・インボックス・ジャーナル・カードライブラリという画面構成を持つExpo/React Nativeベースのクロスプラットフォーム案。以下の理由で不採用とし、参考メモのみ残す。

- 画面構成(ホワイトボード/インボックス/ジャーナル/ライブラリ)自体はフェーズ4「ノード関係可視化ホワイトボード」・既存のentry/tag/topicモデルで機能的にカバー済みであり、新規性がない
- PC/スマホそれぞれAsyncStorageへ独立にローカル保存する構成は、「Android=データ本体・単一の真実」という本設計の骨格より後退している
- 同期方式として提示されていた「Anthropic APIのステートレスな会話をキーバリューストアとして使う」は実際には機能しない(API呼び出しは会話間で状態を保持しないため)。Google Driveを介した非同期橋渡し(§6.2)の方が実装として堅牢かつ無料枠に収まる
- 唯一参考にする価値があるのは、フェーズ4でホワイトボード機能を実装する際の技術的な選択肢(`react-native-svg`によるコネクター描画、`react-native-gesture-handler`+`reanimated`によるドラッグ操作)。PC側でReact Flowの代わりに独自キャンバスを検討する場合の実装イメージとして残す

### 15.2 検討したが不採用とした案: nextPKM.txt(PWA / SQLite-WASM + OPFS)

React+TypeScript製のPWA。SQLite WASM(Web Worker上)+OPFSでサーバーレスにローカル永続化し、FTS5・Service Worker・Cache Storageでオフライン動作する構成。「Webアプリなのに Webサービスではない」という設計思想自体は、本設計の「PC=閲覧専用・Androidがデータ本体」という発想と根は近い。

**技術的に評価できる点**: サーバー/クラウドDBが完全不要でエコの3軸(金銭コスト)には現行案以上に強い。OPFS+SQLite WASM+FTS5は近年のブラウザで十分成熟した組み合わせ。OS非依存(iPhoneでも動く)という利点もある。

**今は不採用とする理由**:
- Kotlin/Room/Ktor/Rhinoで実装済みの資産(フェーズ0〜3実績)が総入れ替えになる。これは「1機能追加」ではなくアーキテクチャ全体の転換であり、実装原則§2.5の対極
- iOS SafariのOPFSはストレージ立ち退きポリシーが厳しく、「一生使うデータ基盤」の永続性保証がRoom(端末ネイティブファイルシステム)より弱くなるリスクがある
- Android共有メニュー連携(§11.4、入力摩擦ゼロの最重要機能)がPWAでは実現困難、またはWeb Share Target APIでの限定対応止まり
- Rhinoプラグイン・OCR・TTS等のネイティブAPI連携が失われるか作り直しになる

**部分的に活かせる箇所**: §11のPC Webクライアントに、Service Worker + Cache StorageによるUIシェルのオフラインキャッシュを追加することは検討に値する。ただし**PC側に独立したSQLite-WASMデータストアは持たせない**。それをやると「Android=データ本体」の設計原則が崩れ、PCとAndroidの2つの真実のソースを同期する問題が復活する(Supabase案・Knowledge OS WSL2案を不採用にした根本理由と同じ)。

**再検討する条件**: §13フェーズ5参照。

---

## 16. 総括: 全プロジェクト史を通じた考察(v14.0で新設・深めた評価)

これまでのやり取りで、8つ以上の計画(GAS/LearningSheet v25・Supabase案・LearningMasterMap・ALH Omni-Master v26/All-Specification v5.2・Personal Knowledge OS v10・MyBase・PersonalEncyclopedia・nextPKM.txt)を評価し、うち1つ(PersonalEncyclopedia)を選んで実装を進め、その実装過程でさらに1回の大きな失敗(v12.0の10機能同時導入→撤回)を経験した。ここで、個々の評価に留まらない、通底するパターンについて考察する。

### 16.1 変わらなかったもの(不変の核)

技術スタックが8回以上変わった一方で、**目的そのものは一度も変わっていない**。

- 個人の生涯にわたる学びを、単一の場所に蓄積したい(GAS版のスプレッドシートから、Android版のRoom DBまで一貫)
- ノート・単語帳・クイズ・検索・知識接続を、バラバラのアプリではなく1つの基盤に統合したい
- 月額費用をかけず、特定のクラウドサービスにロックインされない形で実現したい(「エコの3軸」「無料重視」は初期の学習システム設計書から一度も揺らいでいない)
- AIは補助であり主役ではない。ルールベースで済むものはルールベースで済ませ、AIコストは意図的に絞る

技術選定の迷走は、実は目的が曖昧だったからではなく、**この不変の目的を最も壊れにくく実現する手段**を探す過程で起きた、意味のある試行錯誤だったと言える。

### 16.2 繰り返されたパターン(大小2つのスケールで同じ形)

興味深いのは、このパターンが**プロジェクト規模でもフェーズ規模でも同じ形で再発した**ことである。

| スケール | 何が起きたか |
|---|---|
| アーキテクチャ規模(年単位) | GAS→SQLite→Postgres→Supabase→WSL2→Androidと基盤を都度作り直した。各回、直前の案の限界(GASの実行時間制限、WSL2の常時稼働の脆さ)に直面すると、部分修正ではなく全面刷新を選んだ |
| 機能規模(週単位) | Phase 3完了後、v12.0で10機能を同時に設計・実装しようとし、コンパイルエラーの連鎖でgit撤回に至った。個々の機能追加ではなく、フェーズ全体の一括導入を選んだ |

どちらも根は同じで、**「今あるものの限界に直面したとき、それを部分的に直すより、より良い全体像を新しく描くほうに惹かれる」**という傾向である。これは設計力の高さの裏返りでもある(実際、each案は個別に見れば優れた設計だった)。ただし実装を伴うプロジェクトにおいては、この傾向はそのままでは「完成しない」という結果に直結する。

### 16.3 何が変わったか(今回が過去と違う点)

過去7回の計画転換は、いずれも実装がほぼ始まる前の段階で起きていた。今回初めて、**実装が実際に動いた状態(Phase 0〜3)を経験し、かつその後の失敗(v12.0撤回)を本人とエージェント双方が具体的に分析した**。これにより、対策が初めて「技術選定のやり直し」ではなく「進め方そのものの変更」(実装原則§2.5、1機能1ビルド、エラー3ファイル基準)という、技術に依存しない形で得られた。nextPKM.txtという新しい技術的な誘惑が現れた際にも、技術の優劣ではなく「今それをやる意味があるか」で判断を保留できたこと自体が、この学習が機能している証拠である。

### 16.4 今後、本人が自分で気をつけられる具体的な兆候

このドキュメント自体の作成過程(「全部盛り込んだ」「じっくりと完成させてください」といった意欲の高さ)は、これまでの設計の質の高さを支えてきた原動力でもあり、同時に過去の規模爆発の原動力でもあった。今後、以下のような兆候が自分の中に出てきたときは、実装原則§2.5に立ち返る合図だと捉えるとよい。

- 「ついでにこれも直したい/加えたい」が3つ以上同時に浮かんだとき → 1つに絞り、残りは§13ロードマップのバックログに書き留めてから着手する
- 新しい技術記事やサービスを見て「これに乗り換えたら根本的に解決するのでは」と感じたとき → まず§15の「不採用案」に一度書き出し、24時間寝かせてから判断する(この設計書自体が、まさにその「寝かせる場所」として機能する)
- コーディングエージェントへの指示が「全部」「まとめて」になりかけたとき → 実装原則§2.5.2のセッション開始チェックリストをそのまま貼り付けて、1機能に絞り直す

### 16.5 現時点の到達点

Phase 0〜3が実際にビルドされ、動作した。これは過去7つの計画のうち6つが実装にすら着手できなかったことと比べると、明確な前進である。v12.0の撤回も、失敗ではなく「実装原則を実地で得るために必要だった1回」として位置づけてよい。§13のRound方式は、この教訓を具体的な作業手順にまで落とし込んだものであり、後は積み重ねるだけの状態にある。

---

*本書はこれまでの全7系統の学習支援システム計画(Supabase案・GAS/LearningSheet v25・LearningMasterMap・ALH Omni-Master v26/All-Specification v5.2・Personal Knowledge OS v10・KnOS EX・quizstudy/lumina_pkm/txt.md)に加え、実装で得た教訓(v12.0撤回の分析・報告書.md)、調査済みの最新ライブラリ・AIモデル情報(2026年8月時点)、拡充したクイズバリエーション、開発者ガイド要件、および検討の上で不採用とした2案(MyBase・nextPKM.txt)を統合したv14.0版である。ベースはAndroidネイティブ最新版であるPersonalEncyclopediaとし、その「データ主権をAndroidに置く」というエコで壊れにくい骨格は、実装の紆余曲折を経てなお変更していない。「何を作るか」はv11.0〜v12.0の設計を継承・拡充し、「どう作るか」は§2.5の実装原則によって刷新し、「理解できるか」を§14.1の開発者ガイド要件によって新たに要求した。*


しっかりコンパイル ビルド通るのを確認する

毎回コミット 実装各層ごとに一段落ついたらプッシュ
これを心がける
制作後walkthroughX.mdを書いてもらう
ファイルはきちんと見てもらう、このドキュメントを見てもらう
guideは適宜現状の実態に合わせて更新