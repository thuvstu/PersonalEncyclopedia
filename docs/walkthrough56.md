# walkthrough56 — 付箋システム（カードに「思ったこと」を貼る／タップで展開）

**日付:** 2026-09-09
**コミット:** `a8c2905`(骨格) → `44007a4`(本格化)
**ビルド:** 作業環境にJDK/Android SDK/外部ネットワークが無く `./gradlew` を一切実行できていない（`apt`・gradle配布・adoptiumいずれも到達不能）。
**次回セッションの最初に必ず `./gradlew assembleDebug testDebugUnitTest` を回すこと。** 未検証で入れたファイル数が多いため、
コンパイルエラーが3ファイル以上に波及した場合は AGENTS.md 原則4に従い `a8c2905` へ戻して層ごとに再導入する。

## 1. 背景

「カードに対して思ったことを書ける付箋みたいなシステム（タップして広げる）」。
初回の骨格(テーブル＋カード下タブ)では「足りない」との指摘を受け、付箋を**システム**として成立させる範囲まで広げた:
(a) 貼れる場所を学習動線全体に、(b) ライフサイクルと昇格、(c) 検索・集約、(d) 永続化・PC。

## 2. 設計判断

- `entry_thought` とは別テーブル。thoughtは「1考え=1カード」、付箋は「既存カードへの短いメモ(1:N)」。付箋→thoughtは**昇格**として明示
- 操作は `StickyNoteRepository` に一元化（原則4「手続きは共有サービスに」）。全画面・APIがここを通る
- 昇格は3方向とも承認制の骨格を守る: 接続候補化は `connection_candidate(pending)` までで、`connection` には書かない
- 検索連動は `EmbeddingTextBuilder.build(..., stickyNotes)` の引数追加のみ。既存呼び出し(既定値)は無変更
- ViewModel間の重複を避けるため `StickyNoteController`(VM側) と `rememberStickyNoteBinding`(画面側) を導入。
  各画面の追加コードは「VMに2行 + 画面に3〜4行 + Scaffoldに snackbarHost」で済む
- 一覧の付箋は `observeAll()` 1本を購読し `groupBy(entryId)`。カード数ぶんFlowを張らない

## 3. 変更ファイル

### DB (v11 → v12)
- `db/entity/EntryStickyNoteEntity.kt` … `source/contextId/isPinned/isResolved/resolvedAt/sortOrder/promoted*` を追加。索引 entryId/createdAt/isResolved
- `db/dao/EntryStickyNoteDao.kt` … CRUD + `observeAll/observeRecentUnresolved/observeUnresolvedCount/observeEntryIdsWithNotes/searchText/nextSortOrder/latestUpdatedAt/set*`
- `db/Migration11to12.kt`, `db/AppDatabase.kt`(version 12), `di/DatabaseModule.kt`
- `androidTest/db/MigrationTest.kt` … `migrate11To12_addsStickyNoteTable`、フルチェーン v1→v12

### ドメイン
- `repository/StickyNoteRepository.kt`(新規) … add/updateText/delete/undoDelete/setPinned/setResolved/move/promoteToThought/promoteToTask/promoteToConnectionCandidate/searchNotes。純粋関数 `titleFrom` `extractWikiLinks`
- `brain/search/EmbeddingTextBuilder.kt` … `stickyNotes` 引数（本文1600字＋付箋400字、合計2000字上限）
- `brain/ai/EmbeddingQueue.kt` … `EntryStickyNoteDao` 注入、`updateSearchDocument` で付箋連結、`isSearchDocumentFresh` が付箋更新も鮮度に含める

### ViewModel
- `viewmodel/StickyNoteController.kt`(新規)
- `DashboardViewModel`(sticky/stickyNotesByEntry/recentStickyNotes/unresolvedStickyCount/stickyEntryTitles)
- `SearchViewModel`(sticky/stickyNotesByEntry/onlyWithStickyNotes/resultsWithSticky/付箋LIKEヒット)
- `EntryDetailViewModel`, `SrsViewModel`, `QuizViewModel`, `WhiteboardViewModel`, `WikiViewModel`

### UI
- `ui/component/StickyNoteSection.kt`(全面改訂) … `StickyNoteActions`/`StickyNoteTab`/`StickyNoteSection`/`StickyNoteQuickAdd`/`StickyNoteEditor`/`StickyNotePromoteTaskDialog`
- `ui/component/StickyNoteHost.kt`(新規) … `rememberStickyNoteBinding`: Actions束ね＋Snackbar(Undo/昇格通知)＋タスク化ダイアログ
- `ui/component/EntryCard.kt` … `stickyNotes/stickyNoteActions` 省略可能引数。**clickable を Card→内側Row に移動**
- `DashboardScreen`(カード配線＋「📝 最近の付箋」LazyRow), `SearchScreen`(配線＋「📝 付箋あり」チップ), `EntryDetailScreen`,
  `SrsReviewScreen`(QuickAdd), `QuizScreen`(Answered時にQuickAdd), `WhiteboardScreen`(ノードのバッジ＋ちら見せ＋ModalBottomSheet), `WikiScreens`(記事下部)
- 上記7画面の Scaffold に `snackbarHost` を追加

### 永続化 / API / Web
- `backup/EntryExporter.kt` … Markdown `### 📝 付箋`、JSON `stickyNotes[]`
- `importer/EntryJsonCodec.kt` … `Decoded.stickyNotes`、`keepId=false` で昇格先IDを破棄
- `importer/ImportPipeline.kt` … `insertAll`
- `server/dto/ApiDtos.kt`(StickyNoteResponse/Create/Update), `server/routes/StickyNoteRoutes.kt`(新規), `server/ServerDependencies.kt`, `server/LocalServer.kt`
- `web/src/api/client.ts`, `web/src/components/StickyNotes.tsx`(新規), `web/src/components/EntryDetail.tsx`, `web/src/styles.css`

### テスト(JVM)
- `EntryJsonCodecTest` +4件（往復/newId/破損スキップ/旧形式）
- `EmbeddingTextBuilderStickyTest`(新規4件)
- `StickyNoteRepositoryPureTest`(新規2件)

### ドキュメント
- `DESIGN.md`(テーブル一覧/APIエンドポイント/ServerDependencies), `docs/guide/07-sticky-notes.md`(新規)

## 4. 未検証・次にやること（優先順）

1. `./gradlew assembleDebug` → Roomが `app/schemas/.../12.json` を生成するのでコミット
2. `./gradlew testDebugUnitTest` → 上記JVMテスト10件
3. `./gradlew connectedAndroidTest` → `MigrationTest`
4. `cd web && bun install && bun run build`（tsc）
5. 実機: 一覧タブ開閉 → 追加 → 長押しメニュー → 解決 → 削除Undo → 思考化Snackbar「開く」 → クイズ誤答後の付箋 → 白板バッジ → 検索「付箋あり」
6. 気になる点: Compose の `combinedClickable` は `ExperimentalFoundationApi`(OptIn済)。`Icons.Default.StickyNote2` は material-icons-extended 依存（導入済み）
