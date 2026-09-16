# walkthrough62 — AIネイティブPKM化計画(設計書・コード変更なし)

## 0. 背景
- X上の2ポスト([@_ryu15_](https://x.com/_ryu15_/status/2099919702852325747) / [@yuki_arano](https://x.com/yuki_arano/status/2100158175043600805))に示される「AIネイティブPKM」方向(MCP+ブラウザでどのAIからも同じタスクにアクセス / ハーネス超えたタスク管理 / ユーザー判断待ちの一括返答 / CLIベース)を当リポジトリにどう着地させるかを設計した。
- ユーザー判断で「まず設計に落とす」を選択 → 本walkthroughは**設計書の作成のみ**。コード変更ゼロ。

## 1. 作成物
- `docs/PersonalEncyclopedia-AIネイティブPKM化計画.md`(新規)
  - 現状の事実(実コード検証済み)・ギャップ分析・原則5項目・機能F1〜F6・Roadmap・セキュリティ不変項・オープンクエスチョン4件

## 2. 設計のために行った実コード検証 (2026-09-16, HEAD `005dbd4`)
| 確認事項 | 結果 |
|---|---|
| KtorサーバーのAPI面 | `server/LocalServer.kt:90-105` — `/api` 配下に entries/search/srs/quiz/connections(+connection-candidates)/graph/progress/plugin/sticky-notes の9ルート。**task系ルートの不在を確認** |
| 認証 | bearer `token-auth`(`server/TokenManager.kt`, EncryptedSharedPreferences `secure_settings` に保管) + CORS anyHost。MCP/CLIはこれをそのまま流用できる |
| タスクモデル | `db/entity/TaskEntities.kt:17-44` — statusは pending/in_progress/done/failed/abandoned、estimatedMinutes必須、task_time_log(studyPlusSyncedフラグ付き)。`TaskDao`/`TaskTimeLogDao` でF1は完結し得る |
| 判断待ち | `repository/ConnectionRepository.kt:41-65` — `getPendingCandidatesWithEntries`/`approveCandidate`/`rejectCandidate` が個別処理のみ。**一括処理の不在を確認**(F2の着手対象) |

## 3. 方針の要点
- **AI=また1つのクライアント**: データ主権(Android Room DB)は不変。MCP/CLI/ブラウザは同一LAN Ktor API + 同一tokenに等価に到達(裏道を作らない)
- 実施順: **F1 タスクAPI → F2 判断待ちキュー(一括返答) → F3 MCP(stateless Streamable HTTP・依存追加なし) → F4 CLI(bun) → F5 Watch(要判断)**
- パフォーマンス大改良トラック(`docs/NextTasks.md`)と併走。1セッション1機能は両トラック共通で維持

## 4. 確認事項
- コード変更なしのためビルド不要(変更対象はdocs/のみ)
- 既知のドキュメント不整合を計画書内に注記: AGENTS.mdが参照する `docs/PersonalEncyclopedia-パフォーマンス大改良計画.md` は存在せず、実体は `docs/NextTasks.md`(スコープ外)
