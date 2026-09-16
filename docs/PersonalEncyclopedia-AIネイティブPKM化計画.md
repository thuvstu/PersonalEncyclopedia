# Personal Encyclopedia — AIネイティブPKM化計画

**作成日:** 2026-09-16
**トリガー:** 以下の2ポストに示される「AIネイティブなPKM」の方向性への着地を決めるため作成

- [@_ryu15_ / 2026-09-15](https://x.com/_ryu15_/status/2099919702852325747) — MCP+ブラウザベースで「どのAIからでも同じタスクにアクセスできる」PKM。ブクマ/タスクがどんどん溜まるのでAIに管理させる。車輪の再発明でも「自分の使いやすさ」が武器
- [@yuki_arano / 2026-09-16](https://x.com/yuki_arano/status/2100158175043600805) — 共通項は「ハーネス超えたタスク管理」と「**ユーザー判断待ちの一括返答**」。特色は GitHub/Phabricator等のwatch・Handoff・エージェント間ディスク協調・**CLIベース(エージェントが使いやすい)**

**位置づけ:** 本計画は**新トラック**。現行のパフォーマンス改良トラック(`docs/NextTasks.md` = パフォーマンス大改良計画、Round構成)とは併走し、AGENTS.mdの「1セッション1機能」をそのまま適用する。どちらのトラックも同時に着手しない。

> 注: AGENTS.mdが参照する `docs/PersonalEncyclopedia-パフォーマンス大改良計画.md` は存在せず、実体は `docs/NextTasks.md`(タイトルが「パフォーマンス大改良計画」)に置かれている。今後どちらかに揃える(本計画のスコープ外)。

---

## 1. 方向性の要約

2ポストに共通するのは、以下を足し合わせた姿である。

1. **PKMを「1人の人間のアプリ」から「複数のAI(ハーネス)と人間が共同で使う共有空間」へ**
   - Claude/Cursor/自前エージェント/PCブラウザ… どれからでも**同じデータ**(エントリ・タスク・判断待ち)にアクセスできる
   - 「ハーネス超えたタスク管理」= タスクが1つのアプリの内部に閉じず、外部のAIからも操作可能
2. **人間は「判断」だけやり、それ以外はAIに任せる**
   - 「ユーザー判断待ちの一括返答」= 承認待ちの候補などが溜まったら、一覧化してまとめて判断する動線
   - Automatic connection(AUTO_CONNECT_ENABLED既定false)のような**提案→承認**の思想は本プロジェクトの設計原則5と一致している
3. **AIが操作しやすいインターフェースを第一級に持つ**
   - MCP(AIクライアントがネイティブ接続)・CLI(ターミナル内AIが直接叩ける)・ブラウザ(HTTP API)の3経路が同じデータに等価に到達する

## 2. 現状の事実 (実コード検証済み, 2026-09-16, `005dbd4`)

| 項目 | 現状 | 根拠 |
|---|---|---|
| データ本体 | Android内Room DB (entry 13型CTI, task, task_time_log, connection, connection_candidate, srs, quiz…) | `db/entity/` |
| タスクモデル | `TaskEntity`: id/title/description/**estimatedMinutes(必須)**/deadlineAt/**status: pending/in_progress/done/failed/abandoned**/postponeCount/linkedEntryId/linkedTopicId。`TaskTimeLogEntity`: startedAt/endedAt/studyPlusSynced | `db/entity/TaskEntities.kt:17-44` |
| タスクの脳・連携 | `TaskEngine` / `TaskSuggester` / `TaskNotifyWorker` / `StudyPlusClient`(§7.8連携) | `brain/task/`, `task/`, `integration/` |
| ローカルAPI | Ktor(Netty) 既定port 8080、**bearerトークン認証**(EncryptedSharedPreferences `secure_settings` に保管)、CORS anyHost、明示的ON/OFF。`/health`(認証外) + `/api/*` | `server/LocalServer.kt:41-106`, `server/TokenManager.kt` |
| 既存API面 | `entries / search / srs / quiz / connections / connection-candidates / graph / progress / plugin / sticky-notes` | `server/routes/` 9ファイル |
| **タスクのAPI** | **未実装**。task系はアプリ内(Compose UI)完結で、サーバー側から操作できない | `server/routes/` にtask系なし |
| 判断待ちの処理 | `ConnectionRepository.getPendingCandidatesWithEntries()` / `approveCandidate(candidateId, relationType?)` / `rejectCandidate(candidateId)` は存在。**一括処理は未実装**(1件ずつ承認) | `repository/ConnectionRepository.kt:41-65` |
| PCクライアント | React+Vite(`web/`, bun)、DBを持たずKtor APIを叩く。トークンは `web/src/api/client.ts` / `ConnectionBar.tsx` | `web/` |
| MCP / CLI | なし | — |
| 制約(AGENTS.md) | データ主権はAndroid / Drive API不使用(SAF方式) / 承認制 / 月額固定費ゼロ・無料枠優先 / 1セッション1機能 | `AGENTS.md` |

## 3. ギャップ分析

| 発想(ポスト) | 当リポジトリ | ギャップ |
|---|---|---|
| どのAIからでも同じタスクにアクセス(_ryu15_) | タスクはアプリ内完結。API面がentries系中心 | **タスクAPI**(F1) → **MCP**(F3) |
| 同じタスクにアクセス(ブラウザ)(_ryu15_) | 既にKtor HTTP API + Webクライアント | △(タスクAPIが乗ると完成) |
| ブクマ/タスクが溜まるのでAIに管理(_ryu15_) | ResurfacingEngine(提案のみ)・重複候補は存在 | AIが「整理の提案」をAPI経由で実行できる面が足りない(F1/F3で補完) |
| ハーネス超えたタスク管理(yuki) | 同上 | 同上 |
| ユーザー判断待ちの一括返答(yuki) | connection_candidate + 承認制(1件ずつ) | **判断待ちキュー + 一括返答**(F2) |
| GitHub/Phabricator等のwatch(yuki) | 外部ソース取り込みなし(ファイルimportのみ) | **Watch**(F5, 第2フェーズ) |
| Handoff / エージェント間協調(yuki) | 専用機構なし | 初回は機構不要(§5 F6参照) |
| CLIベース(エージェントが使いやすい)(yuki) | なし | **CLI**(F4) |

## 4. 本計画の原則 (既存原則の延伸)

1. **AIはデータ主権の共有者ではなく、また1つのクライアントである**
   - データは引き続きAndroidのRoom DBのみに存在(設計原則1不変)。MCPもCLIもPC Webと同じ**LAN内Ktor API + 既存bearerトークン**を経由する。新たな認証機構・クラウド・外部サービスは増やさない
2. **人間は判断だけやる**
   - 「提案→承認」(原則5)を**全種の判断待ち**に横断適用する。AIが接続を自動確定させないことは今も未来も不変
3. **決定論が一次、AIは補助**
   - watch(F5)の重複判定・整理提案は決定論ロジック。LLM judgeは任意・graceful degradation(原則2)
4. **3経路の等価性**
   - ブラウザ(HTTP API)・CLI・MCP が同じAPI面を呼ぶ。**MCP/CLI固有の裏道(DB直書き等)を作らない**。こうして「どのAIでも同じ状態」を構造的に保証する
5. **月額固定費ゼロ**(原則不変) — 有料SDK・クラウドマネージドAPIを使わない。MCPは依存追加なしの自前実装

## 5. 機能定義

各Round = 1セッション1機能。着手ファイルは着手時に実コードを再確認すること(AGENTS.md 五原則2)。

### F1. タスクAPI (Round 1) — 全体の大前提

- **目的:** タスクを「ハーネス超え」にする第一段階。どのクライアントでもタスクを見られる・動かせられる
- **内容:**
  - 新規 `server/routes/TaskRoutes.kt`( `/api/tasks` 配下):
    - `GET /api/tasks?status=` — 一覧(既定はアクティブ: pending+in_progress、締切昇順)
    - `GET /api/tasks/{id}`
    - `POST /api/tasks` — 作成(title, estimatedMinutes, deadlineAt 必須。`TaskEntity` の既定値を尊重)
    - `PATCH /api/tasks/{id}/status` — pending/in_progress/done/failed/abandoned へ遷移(completedAt自動)
    - `POST /api/tasks/{id}/postpone` — 締切繰延+postponeCount増
    - `POST /api/tasks/{id}/time-logs` / `GET /api/tasks/{id}/time-logs` — 計時(StudyPlus連携はAPI経路で**しない**。連携はアプリ側のWorkerが独占し、`studyPlusSynced` フラグの意味を壊さない)
    - `GET /api/tasks/active-count`
  - `ServerDependencies` に `TaskDao`/`TaskTimeLogDao` を加算、`LocalServer.kt` に `taskRoutes(deps)` を1行追加
  - **brain層(TaskEngine/TaskSuggester)には触れない**。F1はデータ経路の公開のみで、AIによる「タスクの分割・見積もり」は別機能とする
- **着手ファイル:** `server/routes/TaskRoutes.kt`(新規), `server/ServerDependencies.kt`, `server/LocalServer.kt`, `server/dto/ApiDtos.kt`
- **依存:** 無(既存TaskDaoで完結)
- **スコープ目安:** 1セッション
- **受け入れ基準:** PCから curl / Webクライアントで CRUD+status遷移+計時が通る。`./gradlew assembleDebug` 通過

### F2. 判断待ちキュー (一括返答) (Round 2) — yuki路線の核心

- **目的:** 「ユーザー判断待ちの一括返答」を実現。現在 connection_candidate のみだが、**判断待ちの横断リスト**にする
- **内容:**
  - 新規 `server/routes/JudgmentRoutes.kt`:
    - `GET /api/judgment/pending` — 種別付きで一覧化。初回種別は `connection_candidate`(相手エントリ名付き=既存 `CandidateWithEntries` を再利用)
    - `POST /api/judgment/batch` — `{ items: [{ kind, id, action: approve|reject, relationType? }] }` を1トランザクション相当で処理。結果は逐件返す(1件失敗で全体を巻き戻さない)
  - 将来の種別(実装は各自のRoundで追加する): resurfacing提案・taskのfailed/abandoned差し戻し判断・watch(F5)からの新規取り込み提案
  - Web UI: 既存の候補承認画面を「判断待ちキュー」画面に格上げし、**全選択+一括承認/一括却下**を追加
- **着手ファイル:** `server/routes/JudgmentRoutes.kt`(新規), `server/LocalServer.kt`, `repository/ConnectionRepository.kt`(バッチ用メソッド追加), Web側の候補承認画面
- **依存:** 無(候補APIは既存)。F1と直列にすること(1セッション1機能)
- **スコープ目安:** 1〜2セッション(API→UI)
- **受け入れ基準:** 5件以上の候補を2タップで片付けられる。MCP(F3)とCLI(F4)が同じ `/api/judgment/*` を叩ける

### F3. MCPエンドポイント (Round 3) — 目玉: どのAIからもネイティブ接続

- **目的:** Claude Desktop / Cursor / 任意のMCPクライアントが `http://<android-ip>:8080/mcp` を指すだけでPKMを操作できる
- **設計:**
  - **依存追加なしでKtor上に自前実装**(Kotlin/Java製MCP SDKのAndroid互換性は未確認=リスク。JSON-RPCのstateless実装は小規模)
  - 新規 `server/mcp/McpEndpoint.kt` + `server/mcp/McpTools.kt`(純粋関数 core=原則7でJVMテスト可能)
  - Transport: **Streamable HTTP** (spec 2025-03-26以降)。単一エンドポイント `POST /mcp` + JSON-RPC。v1は **response-only・stateless**: `initialize` / `notifications/initialized` / `tools/list` / `tools/call` のみ。SSE(server push)・セッション管理はv1に**含めない**(statelessはspec上合法。不要な状態を持つ)
  - 認証: 既存 bearer トークンを `Authorization` ヘッダで流用(MCPクライアントのheaders設定で指定)。`/mcp` も `authenticate("token-auth")` 配下
  - **v1のツール面(読み取り+判断+タスク中心。エントリ作成はv2以降):**

    | ツール | 対応API | 備考 |
    |---|---|---|
    | `search_entries` | `/api/search` | query, limit |
    | `list_entries` / `get_entry` | `/api/entries` | |
    | `list_tasks` / `get_task` / `add_task` | F1 の `/api/tasks` | add_taskはestimatedMinutes必須のバリデーションをツールスキーマで強制 |
    | `complete_task` / `postpone_task` | F1 | |
    | `list_pending_judgments` / `batch_judgment` | F2 の `/api/judgment/*` | **判断の返答は人間が画面でやるべき**が、AIが「今何件溜まっているか・内容は何か」を常に見られるようにする。batch実行は人間の明示指示時のみ(説明に明記) |
    | `list_connections` / `get_graph` | `/api/connections` / `/api/graph` | 読み取り |

  - `tools/call` の戻りは `content:[{type:"text", text: <JSON文字列>}]`。ツール実行エラーは `isError:true` で返す(例外は握りつぶさない)
- **着手ファイル:** `server/mcp/`(新規2ファイル+必要ならDTO), `server/LocalServer.kt`(ルーティング), JVMユニットテスト(初期化・tools/list・tools/call のJSON-RPC処理)
- **依存:** F1(タスク系ツール), F2(判断系ツール)。両方完了が前提
- **リスク:** ①spec追従(ツール呼び出し面は安定。v1は最小面なので低) ②Netty上の長時間IDLE连接(接続タイムアウトは既存設定に任せ、接続の常時保持を前提にしない) ③AndroidのDoze/スリープでサーバー停止(既存の「明示的ON/OFF」原則の延長。AI接続時はアプリの常時表示を前提にする — 制約として明記)
- **スコープ目安:** 1〜2セッション
- **受け入れ基準:** 任意のMCPクライアント(Claude Desktop等)から `initialize` → `tools/list` → `search_entries` / `list_tasks` が通る。`./gradlew test` でJSON-RPC coreがJVM上検証される

### F4. CLI (Round 4) — yuki路線: ベースはCLI

- **目的:** ターミナルの人間とAI(エージェント)がshellから直接PKMを操作する
- **設計:**
  - 新規 `cli/` 配下。**bun + TypeScript**(リポジトリのweb/と同一パッケージ管理規約。Node互換)
  - 1ファイル主体の薄いCLI(既存 `/api/*` をfetchするのみ。MCPも通さない=F3が壊れても動く独立経路)
  - コマンド:
    ```
    pe tasks list [--status s] / add --title T --est 30 --deadline 2026-09-20T18:00 / done <id> / postpone <id> +1d / time start|stop <id>
    pe search <query> / entries get <id>
    pe pending / approve <id> [relation] / reject <id> / batch --approve-all
    pe server url / token (設定の表示のみ)
    ```
  - 設定: `PE_HOST`(例 `http://192.168.1.10:8080`) + `PE_TOKEN` の環境変数、または `cli/config.json`(gitignore対象)
  - 出力は既定で **JSON**(人間は `--pretty`、AIはJSONをそのまま食う)
- **着手ファイル:** `cli/`(新規), `.gitignore`
- **依存:** F1・F2(API面が完成してから)
- **スコープ目安:** 1セッション
- **受け入れ基準:** Androidサーバー起動中ならPCのshellから上記全コマンドが通る。エージェント(Claude Code等)がPE_HOST/PE_TOKENを与えられれば、説明なしに`pe tasks list`等で状態を取得できる(READMEにその旨)

### F5. Watch: 外部ソースの監視 (Round 5, 要判断)

- **目的:** 「ブクマ/タスクがどんどんたまる」源を自動で取り込む(yukiのGitHub/Phabricator watch相当)
- **設計:**
  - 新規 `brain/watch/`: watch定義(URL種別+interval) → **決定論的なポーリング+重複判定**(URL正規化+ハッシュ、既存HybridSearchの類似度で候補判定) → 新規は **entry作成 or connection_candidate/取り込み候補に積み**(=判断待ちキューF2に流す)
  - v1ソース候補: **GitHub public issues/PR**(APIキー不要の60回/h制限内) と **RSS/Markdownファイル**(SAF/フォルダ)。**X/TwitterはOAuth実装コスト大なのでv1から外す**
  - 取り込みは「提案→承認」原則通り: **自動で正式entryにしない**、候補として溜める
- **依存:** F2(判断待ちに流すため)
- **スコープ:** 1〜2セッション(要: 上記「要判断」で優先順位を決めた後)
- **受け入れ基準:** GitHubの公開issueを1本watchすると、新issueが発生すると判断待ちキューに候補が現れる(自動ではentryにならない)

### F6. Handoff / エージェント間協調 — 初回は機構を作らない

- 別AIへの引継ぎメモを**既存のエントリ(memo)+taskで表現する**運用约定として置く(API/MCP/CLIで全エージェントから見える=§4原則4の等価性がそのまま効く)
- 「エージェント間ディスク協調」(yuki)に相当する仕組みが必要になったら、その時は `entry` 種別増設や共有フォルダ方式で設計する。当面は不要(データは元々DB1本)

## 6. Roadmap (実施順)

| Round | 機能 | 理由 | 対象トラック |
|---|---|---|---|
| 0 | 本設計書 | 方向性の確定 | 本計画 |
| 1 | **F1 タスクAPI** | 最も小・全ての「AIアクセス」の前提。これだけでcurl/ブラウザからタスク管理が可能になり、Ryu路線の半分が達成 | 本計画 |
| 2 | **F2 判断待ちキュー** | 「一括返答」の核心。承認制(原則5)の横断化 | 本計画 |
| 3 | **F3 MCP** | 目玉。F1+F2のAPI面に載る | 本計画 |
| 4 | **F4 CLI** | 独立の第3経路。エージェントのシェル利用 | 本計画 |
| 5 | **F5 Watch** | 要: 優先順位判断 | 本計画 |

- パフォーマンストラック(`docs/NextTasks.md` のRound)と**交互に、1セッションでどちらか1つ**を回す
- F1の完了時点での「中間報告」: PCブラウザ/curlからタスク管理が完全に可能になった状態=「ハーネス超えたタスク管理」の最小実現

## 7. セキュリティ・制約 (不変項の確認)

- サーバーは**明示的ON/OFFのまま**(設計原則)。MCP/CLIは新設の公開面ではない。既存のLAN+token前提の延長
- 認証は**既存bearerトークン1本**で統一。MCP/CLI専用のトークン・暗号方式は作らない
- AIが**接続(connection)を自動確定させる道は存在しない**。F5の取り込みも候補まで
- 月額固定費ゼロ: 依存追加は `cli/` のbunのみ(web/と同一)。MCPはSDKなし。クラウド不使用
- 既知制約(明記): Androidのスリープ/DozeでKtorサーバーは停止しうるので、**MCP/CLI接続中はアプリの常時起動(画面ON或いはフォアグラウンド)を前提**にする。常時接続基盤の構築は本計画の目的外(設計§2の「非同期が十分」判断に従う)

## 8. オープンクエスチョン (着手前に潰す)

1. **MCPのツール面に `add_entry` をv1に入れるか** — 本計画は「読み取り+タスク+判断」で開始し、エントリ作成はv2に回す方針。反対意見あればF3で変更
2. **F5の優先度** — 「溜まる源」を自動監視する価値 vs 性能トラックの優先度。Round 4のCLI完了時点で再判断
3. **CLIの言語** — 本計画はbun/TS(web/規約一致)だが、python1ファイルの方がエージェント側の普及が広い可能性はある(任意の環境で動ける)。採用前に確認
4. **トークンの配布UX** — Webクライアントは設定画面でtokenをコピーして渡している。MCP接続時は同じフローで十分か(Claude Desktopのconfigに手書きは問題ないか)

## 9. 参照

- 2ポスト: 冒頭URL
- 現状設計: `DESIGN.md`(§2 設計思想・7原則, §7 サーバー層)
- 運用ルール: `AGENTS.md`(五原則・骨格・1セッション1機能)
- 性能トラック: `docs/NextTasks.md`(パフォーマンス大改良計画)
- MCP spec (Streamable HTTP): [modelcontextprotocol.io](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports) — 実装時は現行specを確認すること(この計画はv1最小面なのでspec変更に強い)
