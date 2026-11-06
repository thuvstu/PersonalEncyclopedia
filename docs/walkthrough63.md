# walkthrough63 — サーバー寿命の分離(設定画面を離れても停止しない)

## 0. 背景

「Androidアプリ + それ以外すべての端末で使うWebアプリ(Androidでも一応使う)」という構成相談から始まった。
相談は3ラウンド続き、ユーザーは「サーバー維持さえも本当に必要だろうか」「APIという方法も悩んでいる」と全面的に迷走。
最終判断は **「とにかく作り込みまくってみるか!!!突っ走ってみよう。その先になにか見えてくるかも知れない」**。

ただし AGENTS.md 第5原則(「全部まとめて」は設計書の全項目コード化ではなく、今動くものを1つ増やすこと)に従い、
**ペースは上げるが粒度は守る**方針で、最初の1機能として「玄関を開ける」ことを選んだ。

### なぜこれが1機能目か

実コード調査で、Web全端末化の前提を壊す欠陥が見つかった:

- `NavGraph.kt:180` の `composable(Routes.SETTINGS)` 内で `SettingsScreen(viewModel: ServerViewModel = hiltViewModel())`
- → `ServerViewModel` は **設定画面の NavBackStackEntry にスコープされる**
- → `ServerViewModel.onCleared()` が `localServer.stop()` を呼んでいた
- → **設定でON → 戻る → ViewModel破棄 → サーバー停止**

つまり PC から使えるのは「設定画面を開きっぱなしにしている間」だけだった。
`LocalServer` 自体は `@Singleton` なのに、寿命を画面が握っていた状態。

加えて `_isRunning` が ViewModel 独自の `MutableStateFlow(false)` だったため、
設定画面を開き直すと**実サーバが起動中でも「停止中」と表示**されていた(状態の二重化)。

## 1. 変更内容(コード2ファイル)

| ファイル | 変更 |
|---|---|
| `server/LocalServer.kt` | `var isRunning: Boolean`(private set) → `val isRunning: StateFlow<Boolean>`。内部 `_isRunning` に集約し、`start()`/`stop()` の3箇所を追従。flow import 3行追加 |
| `viewmodel/ServerViewModel.kt` | 独自 `_isRunning` を削除し `localServer.isRunning` を公開するだけに変更。`toggleServer()` は LocalServer の状態を読むだけに。**`onCleared()` による `stop()` を削除** |

### 設計判断: Foreground Service 化はしない

相談で「サーバー維持は本当に必要か」という疑問が出たため、能力を分解して検討した:

| 能力 | ライブ接続が必須か | 代替 |
|---|---|---|
| 閲覧・一覧・詳細・グラフ・全文検索 | 不要 | 可搬JSON / DBスナップショットで足りる |
| 作成・編集 | 不要 | 書き戻しキュー → Androidが取り込む |
| SRS復習 | ほぼ不要 | 自己採点 + SM-2は純粋関数 |
| **クイズ採点** | **必須** | 3段階パイプライン + LLM judge。移植は非現実的 |
| AIチャット | 不要 | Ollamaタブは既にKtorを経由せず直接叩いている |

結論: **サーバーは「常時稼働するインフラ」ではなく「必要なときに起動する道具」**。
よって Foreground Service・電池最適化除外・Tailscale常時接続は必須とせず、
「起動したら画面遷移では死なない」だけを保証する最小修正に留めた。
アプリプロセスがOSに殺されれば止まる(それは許容)。停止は設定画面のSwitchをOFFにしたときだけ。

## 2. 静的検証(実施済み)

このサンドボックスでは **Androidビルドが不可能**なため、以下を静的に確認した:

| 確認事項 | 結果 |
|---|---|
| `ServerViewModel` の `_isRunning` 残存参照 | 0件 ✅ |
| `LocalServer.isRunning` を Boolean として読む外部箇所 | 0件(型変更の追従漏れなし) ✅ |
| `isRunning` の代入箇所 | `_isRunning.value` 3箇所すべて置換済み ✅ |
| 波括弧バランス | LocalServer 17/17、ServerViewModel 62/62 ✅ |
| テスト(`app/src/test`, `app/src/androidTest`)からの参照 | 0件(影響なし) ✅ |
| `SettingsScreen.kt:45` の `viewModel.isRunning.collectAsState()` | 型は StateFlow のままなので変更不要 ✅ |
| 末尾改行 | 追加済み ✅ |

## 3. 未検証事項(重要)

**ビルド・実機確認は未実施。** 環境制約による:

- JDK なし(`java: command not found`)、Android SDK なし、adb なし
- Gradle/Maven 配布ホストが全て到達不可(`services.gradle.org` / `dl.google.com` / `repo.maven.apache.org`)
- npm レジストリは到達可(Web側は検証可能だが今回は触っていない)

### ユーザー環境での検証手順

walkthrough61 の環境メモどおり **JAVA_HOME は JDK24** を使うこと(Temurin17 は tzdb.dat 欠落で D8 が失敗する)。

```bash
export JAVA_HOME=~/.jdks/OpenJDK24U-.../jdk-24.0.1+9   # 実際のパスに合わせる
./gradlew compileDebugKotlin
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

動作確認(ここが本機能の完了条件):

1. 設定画面でサーバー Switch を ON
2. **戻るボタンで設定画面を離れる**
3. PC ブラウザから `http://<端末IP>:8080/health` を叩く → **200 が返ること**(旧実装ならここで落ちる)
4. 設定画面を再度開く → Switch が **ON のまま**表示されること(旧実装は「停止中」に化けていた)
5. Switch を OFF → `/health` が繋がらなくなること
6. `adb logcat` で FATAL 0件を確認

## 4. ドキュメント更新

- `DESIGN.md` §7.1 に寿命と状態ソースの記述を追記(Foreground Service 化していないことも明記)
- `docs/guide/00-overview.md` のネットワーク節に「ONにしたらアプリ終了まで起動し続ける」を追記

## 5. 相談で判明したが本次未着手の事項(バックログ)

実コード検証で見つかった、Web全端末化に向けた残ギャップ。**1セッション1機能で順に潰す**:

| # | 内容 | 根拠 |
|---|---|---|
| G1 | `POST /api/entries` が `embeddingQueue.enqueue()` を呼ばない → **Webから作ったentryが起動まで検索に載らない**。`ServerDependencies` に `EmbeddingQueue` が入っていないので呼べない構造 | `EntriesRoutes.kt` / `ImportPipeline.insertDecoded()` は呼んでいる |
| G2 | `/api/search` が `entryDao.search()` = **`LIKE '%q%'`** で、FTSですらない。Android の `HybridSearchEngine`(FTS+ベクトル+RRF+rerank)がAPIに露出していない | `SearchRoutes.kt` / `EntryDao.kt:57-66` |
| G3 | entry の **更新APIがない**(get/post/delete/favoriteのみ)。Tiptapタブは新規作成専用。post は thought/definition の拡張行しか作らない | `EntriesRoutes.kt` |
| G4 | 可搬JSONが **connection / srs_review / quiz_bank / task を運ばない**。`importEntriesJson` は同一IDをスキップする **insert-only** なので「同期」には使えない(更新セマンティクス新設が必要) | `EntryJsonCodec.Decoded` / `ImportPipeline.kt:223-266` |
| G5 | Web配信方式。Ktor同一オリジン配信が第一候補(Android assets は classpath resource でないので `staticResources` は使えず、AssetManager route か filesDir コピー+`staticFiles`) | 相談での調査結果 |

### 相談で得られた外部知見(2026年時点)

- **外部ホスティング + LAN API**: Chromium 142+ は Local Network Access 許可で HTTPS→ローカルIP の HTTP fetch が通る(IPリテラル/`.local`/`targetAddressSpace` 条件)。**WebKit は LNA 未実装で mixed content としてブロック**。日本はiPhone比率が高いので外部ホスティング単独は片肺になる
- **Tailscale Serve/Funnel は Android/iOS 非対応**(AndroidアプリはVPNクライアント専用)。よって「AndroidのKtorをTailscaleでHTTPS化」はできない。VPNとして使い `http://100.x.y.z:8080` に同一オリジンアクセスする形になる
- 既に **サーバーレス経路の部品は揃っている**: 可搬JSON往復(`EntryExporter.buildJson` ↔ `EntryJsonCodec.decode`、13型拡張+タグ+付箋)、DB丸ごと暗号化(`BackupExporter.exportToSaf`、全44テーブル)、SAFフォルダ取り込み(`importSafFolder`、200件上限)

### docs の陳腐化(在本次確認)

`docs/mismatch.md` の調査対象は `7abdd27`(2026-09-04, wt24)で、現HEAD `6e09113`(2026-09-16)より **38 walkthrough分古い**。実コード照合の結果:

| mismatch.md の判定 | 実際 |
|---|---|
| §2.1「通知なし。`Notification\|AlarmManager\|createNotificationChannel` は0件」 | **誤り(古い)**。`TaskNotifyWorker` が実装済みで `PersonalEncyclopediaApp.kt:191` で15分周期schedule済み。期限切れ/期限間近/タイムボックス終了を通知 |
| §3.3「rerankは未着手確定」 | **誤り(古い)**。`SemanticReranker` が `HybridSearchEngine` に注入され3箇所(66/80/145行)で呼ばれている |
| §2.3「デッキなし」 | 正しい(deckコード0件) |
| §3.1「要約・自動タグなし」 | 正しい |

ユーザーの主訴は「機能不足が辛い」だったが、**在庫表の陳腐化によって実装済み機能を未実装と誤認していた**可能性がある。
在庫表の再同期は独立した価値のある作業なので別セッションで着手する。

## 6. Git

- 変更: コード2ファイル + ドキュメント2ファイル + 本walkthrough
- 論理1機能(=サーバー寿命の分離)として1コミット
- ビルド未検証のため、コミットメッセージに未検証である旨を明記
