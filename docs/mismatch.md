# mismatch.md — 初期構想と実装実態の乖離リスト

> 初期構想と現在の実装を突き合わせ、「食い違っている・未実装・実用的でない」点を一次情報として残す文書。
> 対象リビジョン: `master` (`7abdd27` walkthrough24、2026-09-04調査)。
> 本書はコード改変を一切伴わない調査記録である。対策の優先順位づけは `DESIGN.md` §13(一次リスト)・§15(着手順序表) を参照。

## 情報源

| 文書 | 役割 |
|---|---|
| `docs/RealityTasks.md` (15項目) | 初期構想の正本。「新しくプロジェクトを作るなら」の要求メモ |
| `docs/PersonalEncyclopedia.md` | ベース設計書 (§1〜§10)。旧モデル (notebooks/notes/decks)・Drive API前提の残存あり |
| `docs/PersonalEncyclopedia-統合設計書.md` / `v13完全版` / `v14完全版` | 統合設計書の版履歴。v13でSAF確定・後追い設計化 |
| `docs/PersonalEncyclopedia-統合設計書-v15完全版.md` (全2440行) | 確定版の正本。§5.9/§7.8/§8.10/§11.11-13/§15.3がv15新設 |
| `docs/zako_task.txt` (P0-P9) / `docs/継承PersonalEncyclopedia.md` / `docs/報告書.md` | 雑タスク・引継ぎ・失敗報告。約束と撤回の記録 |
| `docs/NextTasks.md` / `docs/perf/BASELINE.md` / `docs/walkthrough7.md` | 性能計画・実測・残課題の自己申告 |

## 判定凡例

- **達成**: 構想通り動き、実用になる
- **部分**: 動くが核心が欠ける／実用に支障がある
- **未着手**: コード0件 (grep確定)
- 全指摘に `ファイル:行` を添えた。行番号は調査時点のもの。

---

## §0. 総括表 — RealityTasks 15項目の判定

| # | 構想 (RealityTasks.md) | 判定 | 一言 | 詳説 |
|---|---|---|---|---|
| 1 | Heptabaseライク画面・ノード関係可視化 | 部分 | 置けるが繋げない・区切れない | §1.1 |
| 2 | 文書中の単語検出→自動ハイパーリンク→定義ジャンプ | 部分 | 部品はあるがどこでも発動しない | §1.2 |
| 3 | Wikipedia/ブログ風メディア付きドキュメント (Web風/pdf風) | 部分 | メタ表示止まり。PDF表示なし | §1.3 |
| 4 | 強制リマインダー付きToDo | 部分 | 強制対峙はあるが通知が無い | §2.1 |
| 5 | Google Keep風メモ | 部分 | CRUDのみ。Keep的機能なし | §2.2 |
| 6 | Quizlet/Anki風単語帳 | 部分 | 期限管理は本物。デッキ・同期なし | §2.3 |
| 7 | QuizKnock風多様な企画形式 | 部分 | 遊べるのは3形式。4形式は死にスキーマ | §2.4 |
| 8 | 知識の可視化・理解深化に時間を使いやすく (目的) | 部分 | 上記1〜3・§4の総和で未達 | §1・§4 |
| 9 | LLM APIでデータ自動生成 | 部分 | クイズ/解説/抽出はあるが要約・タグなし | §3.1 |
| 10 | SQLiteで50GB・限界耐性・10年利用 | 部分 | 骨格はあるが50GB実測・機種変復元なし | §3.2 |
| 11 | Embedding+reranking意味検索 | 部分 | rerankは未着手確定。混在運用が危険 | §3.3 |
| 12 | ソート・フィルター・条件検索の充実 | 部分 | 型×モードのみ。ソート固定・複合条件なし | §3.4 |
| 13 | DBの全貌をアプリ内から覗ける | 部分 | 件数・SQLはあるが容量内訳なし | §3.5 |
| 14 | Material 3 Expressive・無駄な遷移時間なし | 未着手/部分 | Expressive未導入。遷移は速いが起動が重い | §4.1・§4.2 |
| 15 | bookmark.html取込 / ポモドーロタイマー | 達成（2026-09-06） | P6-1・P5-1実装済み | §2.5・§2.6 |

> 要約（2026-09-06更新）: **達成2（bookmark・ポモドーロ）・部分12・未着手1（Expressive）**。骨格 (entry統一型・FTS+ベクトル検索・SM-2/FSRS・承認制・SAFバックアップ) は構想を超えて実装された一方、構想の顔である「可視化・自動リンク・リッチ文書・通知・取込」の体験層が部分止まり。これが本書の主題である。

---

## §1. 知識の可視化 — 置けるが繋がらない

### §1.1 Heptabaseライク画面 → 部分 (関係線・区切り・編集が無い)

あるもの: ボード一覧+作成 (`WhiteboardScreen.kt:42-136`)、自由メモ配置 (`:347-374`)、既存entry参照配置 (`:376-431`、walkthrough24で開通)、パン/ピンチズーム (`:191-248`、0.3–3x中心基準)、ノードドラッグ (`:297-308`、`/scale`補正)、題名解決 (`WhiteboardViewModel.kt:47-51`)。DBは `WhiteboardEntities.kt:35-51` (`x,y,width=240,height=120,zIndex`)。

無いもの (Heptabase未達の核心):

- **接続線 (エッジ) 表示なし**。`WhiteboardScreen.kt:286-335` はカードの `forEach` のみで `Canvas` はグリッド専用。エッジ用Entity/DAO/`drawLine` が存在しない。`connection` を描くのはWebの `GraphView.tsx` のみで、Androidキャンバスと非連動・編集不可。
- **セクション作成なし**。Entity (`WhiteboardEntities.kt:56-68`)・DAO (`WhiteboardDao.kt:62-72`)・Repository (`WhiteboardRepository.kt:58-66` の `addSection/deleteSection/setNodeSection`) はあるが**呼出元が皆無** (grepはRepo/Daoのみ)。画面は背景 `Box` 描画だけ (`WhiteboardScreen.kt:270-285`) でCRUD/リサイズ/割付UIなし。
- **カード内テキスト編集なし**。追加はダイアログのみ、キャンバス上は `maxLines=3` の読み取り表示 (`:321-325`)。`width/height` 変更UIなし。`note.contentMd` のMarkdown/KaTeX/`[[wiki]]` も先頭行表示のみ。

実用上の問題: 関係線が引けないので「関係可視化」にならない。配置はランダムずらし (`WhiteboardViewModel.kt:67-68,91-92`) で重なる。Webグラフは同心円固定 (`GraphView.tsx:74-79`) で50件超は判読不能、走査も `LIMIT 100` 打切り (`ConnectionDao.kt:87-99`)。

### §1.2 単語検出→自動ハイパーリンク → 部分 (実質未接続)

部品は揃っている: Trie最長一致検出器 (`AutoLinker.kt:20-48`)、5万件キャッシュProvider (`AutoLinkerProvider.kt:16-32`)、描画側2系統 (`RichContentView.kt:94-103` と `RichText.kt:31-137` の `MarkdownText`)。設計コメントにも「閲覧時UI装飾のみ・connection書込なし」と明記 (`AutoLinker.kt:10-13`)。

しかし**どこでも発動しない**。詳細画面 (`EntryDetailScreen.kt:159-171`)・定義文 (`EntryTypeSections.kt:329-333`)・Wiki記事 (`WikiScreens.kt:133-150,213-216`) はいずれも `AutoLinker` を渡さず、唯一のconsumer `MarkdownText` は呼出箇所0 (定義行のみ)。`EntryDetailViewModel.kt:107-119` が構築する `_autoLinker` フローも画面がcollectしない死蔵。現動作は手動 `[[wiki-link]]` →プレビューポップアップのみ。

5万件実用性も未検証: VM側5000件打切りとProvider側50000件の不整合、増分更新なし (全再構築・`invalidate()` 呼出なし)、完全一致Trie (表記揺れ・読み仮名非対応)。5万件実測なし。

構想の「検出次第自動生成」には、エディタ入力中サジェストか閲覧時装飾の配線のどちらかが要る。現状はどちらも無い。

### §1.3 メディア付きドキュメント (Web風/pdf風) → 部分 (メタ表示止まり)

型拡張は保持 (`EntryTypeSections.kt:39-51` で `document/media/book/video/webpage` 分岐) するが、各型は `SectionCard+InfoRow+ExpandableText(maxLines=4)` の縦積み (`WebpageSection :95-105`、`BookSection :108-130`、`VideoSection :132-142`、`DocumentSection :144-154`、`MediaSection :156-166`)。画像添付のみリッチ (`AttachmentSection.kt:67-75` のCoilサムネ+拡大)。

無いもの:

- **PDF表示なし・抽出のみ**。`DocumentExtractor.kt:32-53` はpdfboxテキスト抽出+docx解凍のみで、表示側に `PdfRenderer`/外部Intentなし。`DocumentSection` は抽出テキストの折畳み、`EntryEditScreen.kt:145` も素のテキストエリア。
- **メディアリッチなし**。動画プレーヤ・音声・OCRプレビューなし。`transcript/ocrText` はplain `Text`。画像以外に「ドキュメント風」に見えない。
- **Web風レイアウトなし**。`WebpageSection` は本文トグル表示のみでOGPカード・目次・引用スタイルなし。Web側 `EntryDetail.tsx:43-71` は拡張フィールド・添付を無視。

制約: `RichContentView` はCDN必須 (`:123-126`、オフラインは `innerText` フォールバック `:142-146`)、高さ上限 (`EntryDetailScreen.kt:170` の400dp等) で長文が切れる。

---

## §2. 学習・生産性 — 動くが顔が欠ける

### §2.1 強制リマインダー付きToDo → 部分 (強制対峙あり・通知なし)

あるもの: 先延ばし3回超で回避不可モーダル (`TaskEngine.kt:107-122`、`ToDoScreen.kt:376-407` の `onDismissRequest={}`)、当日固定 (`forceFinishToday :125-134`)、タイムボックスcountdown (`ToDoScreen.kt:58-75`)、見積乖離可視化 (`estimationBiasReport :93-103`)。

**無いもの: リマインダー**。`Notification|AlarmManager|PendingIntent|createNotificationChannel` は `app/src/main/java` で0件。`POST_NOTIFICATIONS` はManifest宣言のみ (`AndroidManifest.xml:7`) で使用箇所なし。WorkManager実体はバックアップ系のみ。`walkthrough7.md:88-89` の自己申告通り「通知経由は未実装」。

実用上: アプリを開いている間のみ有効。バックグラウンド・キル時・期限前予告・超過通知・スヌーズ一切なし。「強制的」の核心が欠落している。

### §2.2 Keep風メモ → 部分 (CRUDのみ)

`thought` 型は `title/content/mood` の3フィールド (`ThoughtEditScreen.kt:57-80`)、`context/isDraft` は表示のみ。チェックリスト・色分け (thoughtに色カラムなし)・ピン留め (全entry共通 `isFavorite` のみ)・描画/画像/音声メモ・ウィジェットなし。素朴な1タイトル1本文としては使えるがKeep代替の高速キャプチャ・視覚整理にはならない。

### §2.3 Quizlet/Anki風単語帳 → 部分 (期限管理は本物・運用機能なし)

あるもの: 単枚カード→4段階評価 (`SrsReviewScreen.kt:74-183`)、SM-2/FSRS切替 (`SrsRepository.kt:44-75`)、未学習優先の期限クエリ (`SrsReviewDao.kt:14-34`)。

無いもの: デッキ/サブデッキ、学習ステップ・新規/復習上限の調整、対象は `definition` 型のみ (thought/webpageはSRS不可)、画像・音声・TTS、同期 (AnkiWeb相当)、統計グラフ。個人用間隔反復としては動作するが体系的暗記運用・マルチデバイスはできない。

### §2.4 QuizKnock風多様形式 → 部分 (3形式+2モード・4形式は死にスキーマ)

スキーマ上は7形式 (`QuizBankEntity.kt:15`: `qa/mcq/fill_blank/sort/essay/cloze/custom`) だが、`SettingsRepository.kt:156-157` が `sort/cloze/customは生成・出題対象外` と明言し、取得既定も3形式 (`QuizRepository.kt:82`)。`LlmQuizGenerator` はmcq固定要求。プレイ画面 (`QuizScreen.kt:236-293`) の分岐は「mcq→選択肢 / else→記述」のみでsort並替え・cloze複数空欄UIなし。`custom` 用プラグイン機構はあるが (`PluginEngine.kt:28-54`) 出題系からの呼出が無く未接続。

遊べるのは `qa/mcq/fill_blank` + セッション形式3種 (通常/サバイバル/プレッシャー列挙)。QuizKnock的企画拡張性はない。

### §2.5 ポモドーロタイマー → 達成（2026-09-06・P5-1）

`TaskViewModel` に `PomodoroPhase`（IDLE/FOCUS/BREAK）＋25分/5分往復ループを実装（VM常駐で回転に強い）。
`ToDoScreen` に `PomodoroCard`（残時間・進捗バー・サイクル数・開始/一時停止/リセット）。
集中統計・通知連携は未着手（将来）。

### §2.6 bookmark.html取込 → 未着手

→ 達成（2026-09-06・P6-1）。`ImportPipeline.importBookmarksHtml`（Netscapeパーサ、フォルダ階層・ADD_DATE復元、本文スクレイプなし高速登録、`metadataJson` にフォルダ記録）を実装。`ImportScreen` に取込行追加。既読管理・タグ自動付与は未着手（将来）。

---

## §3. 検索・データ基盤 — 骨格は堅いが長期に穴

### §3.1 LLM自動生成 → 部分 (生成できる種別が偏る)

あるもの: クイズ生成 (`LlmQuizGenerator.kt:40-103`、`generationMethod="cloud_ai"`)、Web本文抽出Stage2 (`WebScraper.kt:93-101`、「要約しない」方針)、ファクトチェック2段階 (`FactCheckEngine.kt:23-71`)、誤答解説・弱点分析 (`CoachingEngine.kt`)、embedding (`EmbeddingQueue.kt:156`)、接続候補 (ベクトルのみ・LLM非使用、`ConnectionEngine.kt:42-70`)。API未設定時のフォールバックは明確 (早期return/定型文/FTS継続)。Ollama透過切替あり (`GeminiClient.kt:60-61,86`)。

無いもの: **要約・タグ・トピック自動付与** (`summariz|generateTags|autoTag` は「要約しない」宣言のみ)。`TagSuggestionEngine` はLevenshteinのみ。Ollama時にgroundingが沈黙無効 (`FactCheckEngine.kt:39` が実質無視)、embed未設定の半設定状態があり得る (`OllamaClient.kt:59` vs `:32`)。

### §3.2 50GB・10年利用 → 部分 (50GBと機種変に遠い)

あるもの: WAL+`synchronous=NORMAL`+Executor分離 (`DatabaseModule.kt:33-52`)、日次暗号バックアップ30世代 (`BackupWorker.kt`)、SAF書出、非破壊マイグレーション9本 (`DROP TABLE` なし)、`exportSchema=true`、可搬export (上限100k)。

定量的批判:

- 実測は最大50k件でDB 624M・ほぼFTS (`BASELINE.md:154-159`)。1件12〜16KB換算で50GBは約400万件相当だが**50GB実測なし**。FTS膨張対策は残タスク明言 (`BASELINE.md:194-195`)。`VACUUM/page_size` 調整なし。
- GB級で破綻する実装: `BackupEncryptor.kt:54,68` の `readBytes()` 全載せ (GBでOOM確実)、30世代×50GBは `filesDir` に不可能、可搬exportはN+1全件取得 (`PortableExportWorker.kt:60-64`)。
- **機種変不可**: `.enc` はKeystore束縛鍵 (`BackupEncryptor.kt:24-43`) のため新端末で復号できない。しかも復元UIが無い (`DatabaseManagementScreen.kt:181-224` にexportのみ、復元ボタンなし)。
- **JSON往復が非対称**: Exporterの6型extensionのうち5型をImporterが捨て、タグ・お気に入り・時刻・旧IDを全捨て (`EntryExporter.kt:156-224` vs `ImportPipeline.kt:201-260`)。画面表示「エクスポートと対称」(`ImportScreen.kt:82-84`) は誤解を招く。
- `app/schemas/` に `3,4,5.json` 欠落。10年履歴の証跡に穴。

### §3.3 Embedding+reranking → 部分 (rerank未着手確定・混在危険)

あるもの: RRF融合 (`HybridSearchEngine.kt:86-142`、`RRF_K=60`、FTS50+ベクトル50、recency boost)、`vec_distance_cosine` 委譲 (`EmbeddingDao.kt:40-44`)、10k超skip+委譲 (`InMemoryVectorIndex.kt:40-60`)。

**rerank実装ゼロ (確定)**: 検索経路のrerank呼出は0件。「Reranker」の言及は将来コメントのみ (`GradingProviderModule.kt:13`、`GeminiGradingProviders.kt:19`、`GradingProviders.kt:13,38`)。`ICrossEncoderProvider` は定義のみ (`GradingProviders.kt:38-43`)、`GeminiGradingProviders.kt:42,44` は `null` 明示、`RubricConfidence.kt:27` は `crossEncoderUsed=false` 固定。構想は検索経路の第二段精密化だが存在しない。

**モデル混在が危険**: `embedding.model` 列があるだけで切替時の次元検証・再埋め込みなし。不一致は `0f` を返すだけ (`GeminiClient.kt:134-139`)。`EmbeddingQueue.kt:52-60` のスキップ判定は本文のみでmodelを見ないため、Gemini→Ollama切替後に旧ベクトルと新クエリが混ざり精度が壊れる。

### §3.4 ソート・フィルター・条件検索 → 部分

あるもの: 4モード+型14チップ (`SearchScreen.kt:41-54`)、debounce 400ms、保存クエリ (SQL Explorer専用、`SqlExplorerScreen.kt:261-291`)。

無いもの: **ソート切替なし** (全て `createdAt DESC` 固定、`EntryDao.kt:44,52,71,84`)、タグ/トピック/日付/添付の絞り込みなし、AND/OR/除外/フィールド構文なし、FTS+条件の複合クエリなし (型は後付けメモリfilter、mute除外は `getById` のN+1)。件数表示は30件打切りなのに総数と紛らわしい (`SearchScreen.kt:118`)。`saved_query` は検索画面と分断 (保存・再実行導線なし)。

### §3.5 DB全貌の可視化 → 部分

あるもの: 3点容量+型別件数+比率バー (`DatabaseManagementScreen.kt:96-179`)、読専SQL+スキーマブラウザ+`integrity_check`+保存クエリ (`SqlExplorerScreen.kt` + `ReadOnlySqlExecutor.kt`)。「隠しデータはありません」(`:143`) 通り全エンティティ到達可。

無いもの: テーブル別バイト数・FTS vs embedding vs 添付の内訳 (624Mの由来が画面から分からない)、追加テーブル (タグ/Wiki/白板/添付/履歴) の件数表示 (`DatabaseManagementViewModel.kt:72` に自認)、GB表示 (`fmtBytes :248-252` はMBまで)、500行cap・セル60字丸め。肥大トップ特定・容量アラートなし。

---

## §4. 体験・運用 — 見た目と足回り

### §4.1 Material 3 Expressive → 未着手

`material3-expressive` 依存なし (`libs.versions.toml:26-33`、`app/build.gradle.kts:64-73`)。`Theme.kt:10-26` はdynamic colorのみでtypography/shapes/MotionScheme指定なし。`ui/theme/` は2ファイルのみ (`Type.kt/Shape.kt/Motion.kt` なし)。`ButtonGroup/LoadingIndicator` 等の使用0件。代わりに `RoundedCornerShape(8〜20dp)` 直書きが全画面に散在。dynamic colorだけが部分的だが構想の「expressiveでスタイリッシュ」は未導入。

### §4.2 遷移時間 → 部分 (遷移は速い・起動と初回表示が重い)

達成寄り: 遷移アニメ80/60ms化 (`NavGraph.kt:52-56`)、全遷移 `timed` 計測。

残債:

- bottomBarがstateを捨てる (`MainActivity.kt:96-164` に `saveState/restoreState/launchSingleTop` なし)。タブ往復でスクロール位置・検索クエリが作り直し。
- Dashboard初回表示が重い: 8フロー同時購読 (`DashboardScreen.kt:60-73`) + 起動直後に再浮上2クエリ+見積レポート (`DashboardViewModel.kt:80-95`)。
- **起動時全件FTS rebuild**: `rebuildAllSearchDocuments` (`EmbeddingQueue.kt:212-222`) が100件ページングで全件再構築。件数が増えるほど起動が遅くなる逆スケール。5万件常用の最大障害 (§6-1位)。

### §4.3 PC連携の実用性 → 部分 (疎通はするが実用でない)

サーバ22EPに対しWebは4タブ (`entries/srs/quiz/ollama`、`App.tsx:11,38-62`)。接続CRUD・候補承認・ヒートマップ・SRS件数・プラグイン等はサーバ済み・Webなし。**CORS未導入** (`LocalServer.kt:40-47` にinstallなし) のため別オリジン (Vite dev→Ktor) のブラウザ運用で躓く。**平文HTTP固定** (`client.ts:37`) + **平文トークン両端** (`TokenManager.kt:20` のDataStore平文、`client.ts:17-30` のlocalStorage平文)。「PC=閲覧クライアント」の骨格 (AGENTS.md) に対しブラウザ実用・安全運用ができない。

### §4.4 データ投入の現実性 → 未着手 (桁が3〜4桁足りない)

自動シードは定義6+思考2等のみ (`DemoData.kt:22-47`)、手動135件 (`InitialData.kt:12`)。しかも `seedIfEmpty` は1件でもentryがあると何も足さず (`:223-228`)、画面文言「追記 (重複を避けて追加)」(`DashboardScreen.kt:247`) と矛盾。50GB/5万件構想との差を埋める導線が無い: bookmark.html未対応 (§2.6)、フォルダ一括PDF/DOCXなし (`ImportPipeline` は `DocumentExtractor` を呼ばない、xlsx/pptxは表示ラベルのみ)、URL一括は逐次・再開なし (`importUrlList :263-287`)。

---

## §5. 文書間の不整合 — 版を重ねた代償

設計書がv13→v14→v15と後追いで膨らんだ結果、版間の矛盾と「約束→未実装」が残っている。コードを正とする。

### §5.1 旧文書の残存矛盾

| 矛盾 | 文書 | 実態 |
|---|---|---|
| Drive API必須 vs SAF確定「Drive API不要」 vs 「Google Drive=橋渡し/15GB」文言の残存 | `PersonalEncyclopedia.md:§2,§3,§7` vs `v13:§6.2` vs `v13:§1.4,§3,§12` | SAFのみ (`BackupExporter.kt:23-24`)、`DriveService` 0件。AGENTS.md骨格もSAF方式 |
| PC=完全入力 vs 閲覧・可視化 vs 閲覧クライアント | `PersonalEncyclopedia.md:§1` vs `統合設計書:§1.4` vs AGENTS.md | Webは薄クライアント。OllamaPanelはKtorを経由しない例外 (`DESIGN.md` §8.2) |
| DB v5/Phase3完了の記述 vs 現状v10 | `継承:§4.2,§1.2`、`zako_task:§0` | 版ラグ。`AppDatabase.kt:67` が正 |
| WorkManager=Drive同期・SRS通知の表 vs 実装通知0件 | `統合設計書:§4`、`v13:§6.5` | 通知系コード0件。`AppEventBus.kt:26` もemit/subscribeゼロ |
| トークン平文表示の約束 vs 暗号化移行 vs 分裂 | `PersonalEncyclopedia.md:§5` vs `zako_task:A-2` | Gemini/StudyPlus鍵は暗号化、トークンのみ平文 (`TokenManager.kt:20`) |

### §5.2 v15新設約束 → 未実装 (6件)

| v15約束 | コード実態 |
|---|---|
| §7.8 StudyPlus SDK 4.0.2導入 | `NoOpStudyPlusBridge` 固定 (`StudyPlusClient.kt:101-114`、`DatabaseModule.kt:85-87`)。`jitpack/desugar` 実記述0件。`markSynced` 到達不能 |
| §8.10.3 タイムボックス終了のWorkManager通知 | `notifyTimeboxExpired()` はStateFlow代入のみ (`TaskViewModel.kt:152-158`)。通知・Worker・Alarm 0件 |
| §5.9.2 `entry_history.changeSummary` AI生成 | `changeSummary=""` 固定 (`EntryRepository.kt:43-60`)。LLM生成呼出0件 |
| §11.13 履歴復元 (巻き戻し) | プレビューのみ。画面が「復元はv15.0スコープ外」と自認 (`EntryDetailScreen.kt:305`) |
| §11.5 PCリッチエディタ Tiptap+KaTeX+lowlight / Zod・shared-types契約 | `web/package.json` に該当依存0件、`web/` に該当文字列0件、エディタコンポーネントなし |
| §6.2 Drive橋渡し `imports/→processed/` 定期取込 | `ImportWorker|processed/` 0件。`gdriveId` 残存 (`Migration2to3.kt:63`) でB6未判断 |

### §5.3 自己申告の残課題 → 放置の確認

- `walkthrough7.md:85-92` の5件 (実投稿・通知・復元・changeSummary・BG退避未検証) はすべてコード上で継続。
- `BASELINE.md:§4-5` の残 (vec実機非空確認・FTS膨張対策・largeHeap撤去後50k再計測・10k/50kフリング未計測) は未着手。数値がlargeHeap有無混在のまま。
- `完全タスクリスト` の `[ ]` (V-1〜V-5、`EntryEditViewModel` 分割、FTS外部コンテンツ化等) はコード先行・記録放置が混在。`GAP-v6` のB3/B5/B6、H1-H5も同様。

### §5.4 実装先行で初期文書に無いもの (後追い設計化・評価)

旧モデル (notebooks/notes/decks) しか無い `PersonalEncyclopedia.md:§4` に対し、entry統一型+13CTI・FTS4+N-gram・Hybrid RRF・sqlite-vec・承認制・FSRS・Ollama・FACT2段・SAF暗号バックアップ・段階初期化・重複排除・ToDo/SQL Explorerは**実装が設計を追い越した**。v13で後追い設計化されたが、ベース設計書の更新は無い。初期構想に無い仕事が本体になっている——これは乖離ではなく成長だが、文書の正本がv15であることを周知しないと新規参加者が旧文書を読む罠になる。

---

## §6. 実用に響く上位 — 先に潰すべき順

> 対策の着手順序は `DESIGN.md` §15の比較表へ。本節は「なぜそれか」の根拠。

1. **起動毎の全件FTS rebuild** (`PersonalEncyclopediaApp.kt:99` → `EmbeddingQueue.kt:212-222`)。件数比例で起動が遅くなる逆スケール。5万件常用の最大障害。差分・遅延・バックグラウンド化のいずれかが要る。
2. **JSON export/import往復の欠落** (`EntryExporter.kt:175-219` vs `ImportPipeline.kt:201-260`)。タグ・拡張5型・時刻・IDを落とす。10年利用の信頼性を崩し、機種変で知識グラフが痩せる。復元UIの欠落・Keystore束縛と合わせて移行不能。
3. **PC連携の実用不能** (CORSなし `LocalServer.kt:40-47` + 平文HTTP/平文トークン + 4タブ未配線)。「PC=閲覧」の骨格に反してブラウザ実用・安全運用ができない。直す順は CORS → 保管 → 未配線タブ。

次点: bookmark.html未対応と文書一括なし (§2.6・§4.4)、schemas 3-5欠落 (§3.2)、モデル混在の危険 (§3.3)、bottomBar state破棄 (§4.2)、白板エッジ/セクション (§1.1)。

---

## §7. 考察 — 乖離はなぜ生まれ、どれが本当に痛いか

> 本節は §0〜§6 の一次情報に基づく考察である。コード改変を伴わない。

### §7.0 前提: 乖離は2種類ある

本書は「構想 vs 実装」を一直線に比べているが、実態は三層構造である。

```
RealityTasks（構想・15項目）
   ↓ ① 設計時の値切り・先送り
v15統合設計書（2440行・正本）
   ↓ ② 実装時の未達・先行
DESIGN.md（実装記録・7abdd27時点）
```

乖離の半分は「実装の失敗」ではなく、**設計書が構想を実装可能な形に削った痕跡**である。これを混同すると、直すべきでないものまで直そうとする。以下、層ごとに分ける。

### §7.1 ①の乖離: 設計書は構想を既に値切っている (adaptive な乖離)

v15 §14「非機能要件」の一行が象徴的である。

> 想定データ規模｜個人利用（**年間数千〜数万entry**）(`v15:2300`)

構想 (`RealityTasks.md:5`) の「256GB水準で50GBまで」は、**設計書の段階で既に数万entryへダウングレード**されている。50GBが何の中身なのか (添付かFTSかベクトルか) を定義した文書は群全体に存在しない。性能計画が50,000件 (≒624M) で戦っているのは、この値切られた目標に対してすら過剰な水準である。三つの数字——構想50GB・設計数万entry・実測50k件——は互いに整合しておらず、**どれが目標なのか決まったことがない**。これが §3.2 の混乱の根である。

同様の値切りは他にもある: PC完全入力→閲覧クライアント (`PersonalEncyclopedia.md:§1` vs AGENTS.md)、Drive同期→SAF、通知→表だけの約束。これらは失敗ではなく設計判断であり、本書の「部分」判定の多くはここに属する。実装を責める前に「設計がそう決めた」ことを分離すべきである。

### §7.2 ②の乖離: 設計→実装は「宣言先行」パターン

v15に書いたが実装しなかった6件 (§5.2: StudyPlus実投稿・通知・changeSummary・履歴復元・Tiptap・Drive取込) は、すべて**設計書が実装に先行した宣言**である。共通点は「外部依存か、工数大の体験層」であること。v15執筆時点で「書けば作れる」という設計力の裏返しが、未実装の約束を量産した。

ここで重いのは、v15 §16.5 の自己申告である。

> Phase 0〜4相当が**実際にビルドされ、実機で動作することを確認済み** (`v15:2422`)

しかし §14 が定める「完了」の3条件 (ビルド成功＋該当テスト成功＋**3日以上の実使用**、`v15:2303`) を満たした記録は、walkthroughにもチェックリストにも残っていない。`完全タスクリスト` の `[ ]` が放置されている事実 (§5.3) と合わせると、**完了の宣言と完了の記録が分離している**。動いているものは動いているのだが、「確認済み」の射程が検証不能である。これは v12 撤回の教訓 (`v15:§16.2`「完成しない」パターン) が、形を変えて残っている箇所である——今度は10機能同時ではなく、「宣言の同時」である。

### §7.3 逆乖離: 実装が設計を追い越した部分 (良い乖離)

entry統一型+13CTI・承認制・FSRS併存・Hybrid RRF・sqlite-vec委譲・SQL Explorer・SAF暗号バックアップ——これらは初期文書に無いか、v13で後追い設計化されたものである (§5.4)。**乖離の方向が逆**で、これは成長である。

ただし問題が一つ: 追い越しを記録する場所が DESIGN.md と walkthrough に分散し、v15正本への逆反映が無い。v15 §13 のロードマップは「Round 6以降」に白板・Wiki・Ollamaを「これから」として書いたままである (`v15:2269-2277`)。新規参加者が v15 §13 を読むと「未実装」に見えるが実態は実装済み——**ロードマップが stale になったまま正本を名乗り続けている**。これも乖離の一種で、方向は逆でも害は同じ (誤った着手順序を誘発する)。

### §7.4 構造的原因: なぜ乖離が生まれ続けるか

**(a) 設計書の二重役割。** v15 §16.4 は自分で警告している: 「このドキュメント自体が『寝かせる場所』として機能する」(`v15:2417`)。しかし現実には同じ文書が「理想の置き場」も兼ねて2440行に肥大した。「寝かせる場所」に書かれた未実装の約束 (通知・Tiptap・SDK等) が、正本の権威を帯びて「やるべきこと」に見える。これが宣言先行の直接原因である。§16.4 の「24時間寝かせる」は書かれたが、「寝かせたものを§15不採用欄へ移す」運用が回っていない。

**(b) walkthrough駆動と設計書更新の速度差。** 実装は walkthrough 単位 (24本) で速く進み、設計書は版単位 (v13→v14→v15) でしか更新されない。間の差分は DESIGN.md が吸収しているが、DESIGN.md は「実装の解説書」であって「次に何を作るか」の文書ではない。結果、**「今何ができて、何が次か」を一目で言える文書が無い**——NextTasks は性能計画に特化、RealityTasks は構想メモ、§15比較表は DESIGN.md 内。本書が生まれたのはこの空白のためである。

**(c) 完了定義の記録不在。** チェックリストが `[ ]` のままなのは、記録の場が無いからではなく、完了の3条件を判定する手順が回っていないからである。walkthrough は「やったこと」の記録であって「3条件の合否」の記録ではない。このままだと §16.5 のような総括だけが増え、検証可能性は増えない。

### §7.5 重大乖離の再評価: どれが本当に痛いか

§6 の上位3点 (起動rebuild・JSON往復・PC連携) に、設計書視点を足して再評価する。

1. **JSON往復の欠落は、v15 §14 の自己約束を破っている。** §14 は「データ独立性: UIが消えてもデータは無傷」「Room＋可搬exportの二重保証」を掲げている (`v15:2299`) が、exporter の6型extensionのうち5型を importer が捨てる現状 (§3.2) では二重保証が成立しない。これは単なる未実装ではなく、**非機能要件の自己矛盾**である。最優先で直すか、約束を下げるか、二択が必要である。
2. **通知の欠落は、ToDo機能の存在理由を侵食する。** v15 §8.10 の Parkinson 対抗設計はアプリ内強制で半分実現したが、構想の「強制的リマインダー」の核は通知である。バックグラウンドで何も起きない ToDo は、ただのリストである。ただし通知は権限・バッテリー・OS差分の沼でもあり、やるなら単独計画化すべき小案件ではない。
3. **規模目標の未決定は、全性能作業の土台を揺らす。** 50GB・数万entry・50k件のどれを目標にするかで、FTS対策の要否もバックアップ設計も変わる。現状は「50kで戦いながら50GBを語り、設計は数万entryと言う」三重状態である。**まず目標を一つに決める**ことが、コードを書くより先である。

逆に、後回しでよいもの: Expressive (見た目であり骨格に無影響)、PCリッチエディタ (Web閲覧が動いてから)、rerank (10万件超えてから——v15 §15 の条件通り、`v15:2337`)、StudyPlus実投稿 (外部依存・キューはある)。

### §7.6 提言: 文書ガバナンスの最小手当て

コードを書く前の提言である。

1. **正本の役割分担を宣言する。** v15＝理想と判断記録、DESIGN.md＝実装の実態、本書＝乖離の一次情報＋考察、NextTasks＝性能計画、RealityTasks＝構想メモ。現状は v15 が全部を兼ねようとして stale 化している。特に v15 §13 ロードマップの「Round 6以降これから」部分は、実装済みを反映した注記が必要である。
2. **「やらない／保留」リストを §15 に移す。** §15.1・15.2 (MyBase・nextPKM 不採用、`v15:2344-2367`) の前例に倣い、通知・Tiptap・SDK実投稿・rerank 等を「保留／条件付き (○件超えたら等)」として明記する。書いたままは約束に見えるので、置き場所を変えるだけで宣言先行が止まる。
3. **v16は書かない。** 差分追記に留め、版を増やさない。版が増えるほど正本が不明確になる実績がある (§5.1の版ラグ)。
4. **walkthrough に3条件チェック欄を足す。** ビルド＋テスト＋3日実使用の合否を1行で残すだけで、§16.5 のような総括が検証可能になる。
5. **規模目標を一つ決める。** 50GB・数万entry・50k件の三重状態の解消が、性能・バックアップ・取込の全判断の前提である。

---

## 付録A. 判定基準

- grep 0件は `rg` 全文検索で確定したもののみ「未着手」とした (pomodoro・bookmark・通知系・expressive・tiptap等。rerankは将来コメントのみで呼出0件)。
- 「部分」は動作確認済みコードがあるが構想の核心 (自動化・通知・表示・往復・容量) に届かないもの。
- 行番号は2026-09-04時点。コード変更時は `DESIGN.md` と併せて本書も更新すること。

## 付録B. 対応表 — 本書 → DESIGN.md §15候補

| 本書 | DESIGN.md §15 |
|---|---|
| §1.1 白板エッジ/セクション | #U3周辺・将来拡張 (接続線は新規計画化) |
| §1.2 自動リンク未接続 | 将来拡張 (承認制との整合設計が必要) |
| §2.1 通知なし | #V1 (通知設計と一括) |
| §2.4 死にスキーマ4形式 | #Q2周辺・仕様確定 |
| §2.5・§2.6・§4.4 取込系 | 新規計画化 (bookmark.html+文書一括) |
| §3.2 50GB・往復・復元 | #K1/#K2・#D1・#F1・#F2 |
| §3.3 rerank・混在 | #F2周辺・将来の端内モデル時 |
| §3.4 検索条件 | #W1周辺・検索画面拡張 |
| §4.1 Expressive | 将来拡張 (依存追加+トークン化) |
| §4.2 起動rebuild | #F1 (計測先行)・#F2 |
| §4.3 PC連携 | #S1・#S2・#S3・#W1 |
| §5.2 v15未実装6件 | #V1/#V2/#W2 (別計画化) |
| §5.3 残課題放置 | #F1・#U1 |

*本書は `docs/mismatch.md` として、`7abdd27` 時点の全ソース・全設計書を実コード検証により執筆。コード改変なし。*
