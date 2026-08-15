Personal Encyclopedia

Androidネイティブの学習ツールを搭載したアプリを用意してPC版は完全入力、閲覧用ハードとする
DBなど根幹部分はすべてAndroidに載せる DBもストレージに載せる(今どきのスマホならいける,さすがに100GBとかまではいかないし)
## 1. 概要

- **目的**: 個人の学習を支援するシステム。ノート/リンク管理、単語帳(フラッシュカード)、問題演習・自動採点、進捗の可視化、AIによる解説生成・添削を統合する。将来的には「何十巻もの百科事典」規模まで育てることを見据える。
- **対象プラットフォーム**: Androidネイティブアプリ、PC用Webアプリの2つ。
  - **Android = データの本体**。DB・ストレージ・API・拡張機能の実行エンジンをすべて端末内に持つ。
  - **PC = 入力・閲覧専用のクライアント**。DBを持たない。
- **開発方針**: 自分の手でコードを書く。AIは設計の壁打ち・レビュー・ヒント出しに使い、拡張機能(プラグイン)については人力実装とAI生成コードの両方を受け付ける。

**Supabaseクラウド案からAndroid中心案への転換理由**:

- Androidは持ち歩く端末であり、常に手元にある。PCは家の中でしか使わない前提なので、**データの主権をAndroidに置く方が実態に合っている**。
- PC→Android間の「入力」は、リアルタイム性が必須ではない(その場で反映されなくても困らない)。ならば常時接続の仕組み(VPN)を維持するより、**非同期のファイル受け渡し**の方がシンプルで壊れにくい。
- 単語帳やノートの取り込み機能(CSV/Markdown/JSON→DB)はどのみち複数実装することになる。**Google Drive経由のPC入力も「取り込み先が1つ増えるだけ」**であり、追加の専用インフラ(VPN、認証基盤)が不要になる分エコ。

## 2. 技術スタック(最終決定)

| レイヤー | 技術 | 補足 |
|---|---|---|
| Android | Kotlin + Jetpack Compose | アプリ本体。DB・API・プラグイン実行エンジンをすべて内包 |
| Android内DB | Room (SQLite) | データの唯一の本体 |
| Android内APIサーバー | Ktor (embedded server) | 同一LAN内からPCがアクセスするためのローカルAPI |
| プラグイン実行エンジン | Rhino (JVM純正JSエンジン) | NDK不要。AI生成コードを再ビルドなしで動的実行 |
| Web フロントエンド(PC) | React + Vite | パッケージ管理・実行は `bun` を使用(npm/npxは使わない)。DBは持たずAndroidのAPIを叩くのみ |
| 型・スキーマ共有 | TypeScript + Zod / Kotlinデータクラス | プラグイン契約とバリデーションに使用 |
| PC→Android連携・バックアップ | Google Drive API | 非同期ファイル転送(取り込み用)とバックアップ保管の両方に使う |
| コンテンツ編集補助(任意) | Google スプレッドシート | 単語帳や問題の元データを人間が編集し、Driveの取り込みフォルダ経由でAndroidへ import |

## 3. システム構成

```
┌─────────────────────────────────────────────┐
│              Androidアプリ(端末内)              │
│  ┌──────────┐ ┌────────────┐ ┌─────────────┐ │
│  │ Room DB   │ │ Ktorサーバー │ │プラグインエンジン│ │
│  │ (SQLite) │ │(ローカルAPI) │ │  (Rhino/JS)  │ │
│  └──────────┘ └─────┬──────┘ └─────────────┘ │
└───────────────────────┼──────────────────────┘
                         │ 同一LAN内は直接HTTP
                         ▼
              ┌─────────────────────┐
              │   PC Webアプリ (React)  │
              │  入力・閲覧専用(DBなし)  │
              └───────────┬─────────┘
                           │ LAN外 or 非同期入力時
                           ▼
              ┌─────────────────────┐
              │     Google Drive      │
              │ /imports/  ← PCが書く │
              │ /backups/  ← Androidが書く │
              └─────────────────────┘
                           ▲
                    Androidが定期的に
                  imports/を取り込み、
                  backups/へ書き出す
```

**2つの通信経路**:

1. **同一LAN(自宅Wi-Fi等)**: PCのWebアプリがAndroidのKtor APIに直接HTTPリクエストを送る。即時反映。
2. **LAN外 or 非同期でよい入力**: PCがGoogle Driveの `imports/` フォルダに構造化ファイル(Markdown/CSV/JSON)を置く。Android側がWorkManagerの定期ジョブで検知し、既存の取り込みパイプラインでRoom DBへ反映する。

どちらの経路も内部的には「取り込み処理を呼ぶ」という同じコードパスを通るため、実装が二重化しない。

## 4. データベース設計(Room / Kotlin)

```kotlin
// ノートブック(教科・科目単位のまとまり)
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ノートページ本体(常にMarkdownで保存 = 長期的な可搬性を優先)
@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = NotebookEntity::class,
        parentColumns = ["id"], childColumns = ["notebookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val title: String,
    val contentMarkdown: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ノートに紐づく外部リンク
@Entity(tableName = "links")
data class LinkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val noteId: String,
    val url: String,
    val label: String?
)

// 単語帳(デッキ)
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String?
)

// フラッシュカード + 間隔反復(SM-2アルゴリズム相当)
@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val front: String,
    val back: String,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: Long = System.currentTimeMillis()
)

// 問題セット(プラグイン単位で問題タイプが変わる)
@Entity(tableName = "quiz_sets")
data class QuizSetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pluginId: String  // 例: "fill-in-blank", "multiple-choice"
)

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizSetId: String,
    val questionDataJson: String,  // プラグインごとに自由形式(JSON文字列)
    val answerDataJson: String     // 採点用の正解データ
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    val answerJson: String,
    val isCorrect: Boolean?,
    val score: Float?,
    val attemptedAt: Long = System.currentTimeMillis()
)

// 進捗ログ(可視化用の汎用イベントテーブル)
@Entity(tableName = "progress_events")
data class ProgressEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,   // "flashcard" | "quiz" | "note"
    val entityId: String,
    val eventType: String,    // "reviewed" | "answered" | "edited"
    val createdAt: Long = System.currentTimeMillis()
)

// AI解説・添削のキャッシュ(同じ質問へのAPI再呼び出しを避けてコスト削減)
@Entity(tableName = "ai_explanations")
data class AiExplanationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceType: String,
    val sourceId: String,
    val prompt: String,
    val response: String,
    val createdAt: Long = System.currentTimeMillis()
)

// プラグインのメタデータレジストリ(実行コード本体はファイルとして別途保存)
@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,          // 例: "fill-in-blank"
    val name: String,
    val version: String,
    val manifestJson: String,            // 検証済みのマニフェスト(JSON文字列)
    val scriptPath: String               // Rhinoで実行するJSファイルの内部保存パス
)
```

## 5. Ktorローカルサーバー設計

```kotlin
// AndroidアプリのForeground Service内で起動
fun startLocalServer(port: Int = 8080) {
    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) { json() }
        install(Authentication) {
            bearer("token-auth") {
                authenticate { tokenCredential ->
                    if (tokenCredential.token == getLocalAccessToken()) UserIdPrincipal("owner") else null
                }
            }
        }
        routing {
            authenticate("token-auth") {
                route("/api/notebooks") { /* CRUD */ }
                route("/api/notes") { /* CRUD */ }
                route("/api/flashcards") { /* CRUD + SRS更新 */ }
                route("/api/quiz") { /* 出題・採点(プラグインエンジン呼び出し) */ }
                route("/api/progress") { /* 集計取得 */ }
            }
        }
    }.start(wait = false)
}
```

- **認証**: 端末内で生成したアクセストークンをAndroidアプリ画面に表示し、PC側で1回だけ入力して保存する方式(簡易共有シークレット)。OAuthのような重い仕組みは不要。
- **起動方式**: 常時起動ではなく、**Androidアプリ側で明示的にON/OFFする**運用にする(バッテリー消費とのバランスを取るため)。PCから使いたい時だけAndroidでサーバーを起動する。

## 6. プラグインシステム仕様

### 6.1 型契約(人にもAIにも共通の仕様書、TypeScript側の定義)

```typescript
// packages/shared-types/plugin.ts
import { z } from "zod";

export const QuizPluginManifest = z.object({
  id: z.string(),
  name: z.string(),
  version: z.string(),
  type: z.literal("quizType"),
});

export interface QuizPlugin {
  manifest: z.infer<typeof QuizPluginManifest>;
  // 純粋関数のみ許可(ファイルI/O・ネットワークアクセス禁止)
  grade: (answer: unknown, answerData: unknown) => { correct: boolean; score: number };
  renderSchema: (questionData: unknown) => UISchema;
}

export type UISchema =
  | { type: "text"; content: string }
  | { type: "input"; id: string; placeholder: string }
  | { type: "multipleChoice"; id: string; options: string[] }
  | { type: "column"; children: UISchema[] };
```

### 6.2 実行方式(Android上のRhinoエンジン)

1. 人またはAIが上記契約に従ったJSコードを書く(`grade`関数と`renderSchema`関数をexportする形)
2. AndroidのKtorサーバーがプラグインをロードする際、まずマニフェストをKotlin側でバリデーション
3. 検証に通ったら `plugins` テーブルに登録し、スクリプト本体を内部ストレージに保存
4. 採点リクエスト時、RhinoエンジンでそのJSファイルを実行し、`grade()`の結果を受け取る
5. 失敗時のエラーメッセージはそのままAIに渡して自己修正させられる(このループはSupabase案の頃と同じ)

**再ビルド不要でプラグインを追加できる**のがこの方式の最大の利点。アプリストア審査を待たずに、自分で書いたJSファイルをAndroidに転送するだけで拡張機能が増える。

### 6.3 Server-Driven UI(Web/Android二重実装の回避)

プラグインはUIをコードではなく `UISchema` (JSON)として返す。Web(React)とAndroid(Compose)は、それぞれ汎用レンダラーを1つずつ持ち、スキーマの `type` に応じて描画を切り替える。

**限界**: ドラッグ&ドロップ並べ替えのような凝ったUIはスキーマ表現力を超える。その場合のみ `{ type: "custom", componentId: "..." }` として、Web/Android双方に事前実装済みのネイティブコンポーネントを呼び出すハイブリッド方式にする。

## 7. Google Drive連携(取り込み & バックアップ)

### 7.1 フォルダ構成

```
LearningSystem/
├── imports/          # PCがここに置く。Androidが定期的に取り込んで削除(またはprocessed/へ移動)
│   ├── notes/         # Markdownファイル
│   ├── flashcards/     # CSV
│   └── quiz/           # JSON
├── backups/
│   ├── db-snapshots/   # SQLiteファイルそのものの丸ごとコピー(完全復元用)
│   └── portable/       # Markdown/CSV/JSONへのエクスポート(長期可搬性の保険用)
```

### 7.2 取り込み(import)

- WorkManagerで定期実行(例: 1日1回、Wi-Fi接続時のみ)
- `imports/` 配下の新規ファイルをGoogle Drive APIで取得 → 種類ごとのパーサー(Markdown/CSV/JSON)でパース → 各Entityに変換してRoom DBへinsert → 処理済みファイルは `processed/` に移動(誤って二重取り込みしないため)
- **この取り込みパイプラインは、将来Googleスプレッドシートを介したインポートにもそのまま再利用する**(パーサーの入り口を増やすだけで済む設計にする)

### 7.3 バックアップ(妥協しない部分)

- **完全バックアップ(`db-snapshots/`)**: RoomのSQLiteファイルをそのままコピーしてDrive上にアップロード。WorkManagerで**毎日1回、Wi-Fi+充電中のみ**自動実行(バッテリーへの配慮とエコを両立)。世代管理として直近30世代を保持し、それより古いものは自動削除。
- **可搬バックアップ(`portable/`)**: ノートはMarkdownファイル、単語帳・問題はCSV/JSONとして書き出す。頻度は週1回で十分(こちらは「10年後にSupabaseもRoomも使っていなくても読み書きできる」ための保険であり、毎日更新する必要はない)。
- どちらもGoogle Driveの無料枠(15GB、Googleアカウント全体で共有)で当面は十分足りる規模感。

## 8. フォルダ構成(モノレポ)

```
learning-system/
├── apps/
│   ├── android/                 # Kotlin + Jetpack Compose。DB・API・プラグインエンジンを内包
│   │   ├── app/src/main/java/.../db/        # Room Entity・DAO
│   │   ├── app/src/main/java/.../server/    # Ktorルーティング
│   │   ├── app/src/main/java/.../plugins/   # Rhino実行ラッパー
│   │   └── app/src/main/java/.../drivesync/ # Drive取り込み・バックアップ
│   └── web/                     # React + Vite (bun)。DBなし、Ktor APIを叩くだけ
├── packages/
│   └── shared-types/            # プラグイン型定義・Zodスキーマ(TS側、AIに渡す仕様書として使う)
├── plugins/                     # 人力/AI作成のプラグインソース(Android転送前のワークスペース)
├── docs/
│   └── design.md                # このファイル
└── package.json                 # bun workspaces設定(web側のみ)
```

## 9. 開発ロードマップ

1. Android: Room DBのスキーマ実装(§5のEntity一式)
2. Android: Ktorローカルサーバーの雛形 + トークン認証
3. Android: 単語帳CRUD + SRSロジック(まずAndroid単体で完結させる)
4. Android: WorkManagerによるDrive `backups/db-snapshots/` への自動バックアップ(**最優先で入れる**)
5. Web: React + Vite雛形 → 同一LAN内でKtor APIに繋いでノート入力ができるところまで
6. Drive `imports/` の取り込みパイプライン実装(Markdownノートから)
7. プラグイン契約確定 → Rhinoエンジンでの実行 + `multiple-choice`プラグインを1つ実装
8. AI解説生成・添削(Anthropic API呼び出しをAndroid内かKtor経由で実装)
9. 進捗可視化画面
10. Drive `portable/` への可搬バックアップ(週次)
11. Googleスプレッドシート経由の取り込み(任意・後回し可、§8.2の取り込みパイプラインを再利用するだけ)

## 10. 今後の拡張ポイント

- 複雑なUIが必要なプラグインタイプが出てきた場合の `custom` コンポーネント方式の具体設計
- Ktorサーバーの常時起動が必要になった場合のForeground Service設計・バッテリー最適化除外設定の検討
- 複数端末(例: 2台目のAndroid)で使いたくなった場合、Driveの `imports/`・`backups/` を経由点にすれば、大掛かりな同期基盤を作らずに済む見込み