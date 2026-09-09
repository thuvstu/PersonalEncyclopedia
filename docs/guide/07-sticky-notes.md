# 付箋（Sticky Notes）— カードに「思ったこと」を貼る

> 対象: walkthrough56 以降。実装は `repository/StickyNoteRepository.kt` が中心。

## 何のためのものか

カードを読んだとき・復習中・クイズを間違えた直後に浮かぶ一言（疑問・感想・あとで確認）を、
**本文を汚さずに**その場で残すためのもの。紙の付箋と同じで「貼っておいて、必要なときだけめくる」。

思考型エントリ(`entry_thought`)は「1つの考えを1枚のカードにする」もの。付箋は「既存カードに対する短いメモ」で、
後から思考エントリへ**昇格**させられる。

## どこで貼れるか

| 場所 | UI | `source` |
|---|---|---|
| ダッシュボード / 検索の一覧カード | カード下部の細いタブ。タップで展開、その場で追加 | `list` |
| エントリ詳細 | 添付画像の下の「📝 付箋」セクション（常時展開、解決済みは折りたたみ） | `detail` |
| SRS復習 | カード下の「💭 思ったことを付箋に」 | `srs` |
| クイズ回答直後 | 「💭 間違えた理由を付箋に」（出典entryへ。`contextId`=quizId） | `quiz` |
| ホワイトボード | entryノード左下の付箋バッジ → ボトムシート。ノード内に先頭1枚をちら見せ | `whiteboard` |
| Wiki記事 | 記事下部。同名entryが無ければ「同名カードを作成して貼る」 | `wiki` |
| PC(Web) | 詳細右ペイン下部。追加/編集(ダブルクリック)/ピン/解決/削除 | `api` |

## 1枚の付箋にできること（長押し or ⋮）

- **ピン留め** — 一覧タブの見出しに優先表示
- **解決済み ✓** — 消さずに畳んで残す（取り消し線＋半透明）。詳細では「解決済み N枚」に折りたたむ
- **上へ / 下へ** — 同じ状態(ピン/解決)の中で並べ替え
- **削除** — Snackbar「元に戻す」でUndo（直前1件）
- **💭 思考エントリにする** — 付箋本文＋`元カード: [[タイトル]]` を本文にした thought を作成。付箋は解決済み＋`promotedEntryId`
- **✅ タスクにする** — 見積分数・締切を選んで `task` 作成(`linkedEntryId`=元entry)
- **🔗 接続候補にする** — 付箋内の `[[カード名]]` を相手に `connection_candidate(pending)` を作る。
  **connectionへ直接は書かない**（承認制の骨格）

## 検索との関係

- 付箋の追加/編集/削除ごとに `EmbeddingQueue.enqueue(entryId)` が走り、`EmbeddingTextBuilder.build(entry, ext, stickyTexts)` が
  本文(1600字)＋付箋(400字)を `search_document.combinedText` に連結 → FTS/意味検索でヒットする
- 検索画面は加えて `entry_sticky_note.text LIKE` の即時経路も持ち（FTSが追いつく前でも当たる）、結果の末尾に足す
- 検索画面の「📝 付箋あり」チップで付箋付きカードだけに絞れる
- ダッシュボードに「📝 最近の付箋」（未解決・新着8枚）の横スクロールフィード

## 永続化

- DB本体バックアップ(SAF暗号化)には自動で含まれる
- 可搬エクスポート: Markdownは `### 📝 付箋` セクション、JSONは `stickyNotes[]`。`EntryJsonCodec` が同キーで復元（往復対称）。
  別端末取込(`keepId=false`)では昇格先IDは捨てる

## コード上の入口

| 層 | ファイル |
|---|---|
| Entity/DAO/Migration | `db/entity/EntryStickyNoteEntity.kt`, `db/dao/EntryStickyNoteDao.kt`, `db/Migration11to12.kt` |
| ライフサイクル・昇格 | `repository/StickyNoteRepository.kt` |
| VM共有 | `viewmodel/StickyNoteController.kt`（各VMが `val sticky = StickyNoteController(...)` を持つ） |
| UI部品 | `ui/component/StickyNoteSection.kt`（Tab / Section / QuickAdd / Editor / TaskDialog）, `StickyNoteHost.kt`（Snackbar配線） |
| API | `server/routes/StickyNoteRoutes.kt` |
| Web | `web/src/components/StickyNotes.tsx` |

---

## 付箋から広げる（wt58「増殖」）

付箋は貼って終わりではなく、**次のカード・リンク・接続を生やす出発点**になる。

| 操作 | 場所 | 何が起きるか |
|---|---|---|
| `[[` を打つ | 付箋エディタ | カード名の候補チップ（前方一致優先）。タップで `[[名前]]` に補完。未作成なら「保存後に＋から作れます」 |
| 🔗チップ | 付箋の下 | 本文の `[[既存カード]]` をチップ化。タップで開く |
| ＋○○を作る | 付箋の下 | `[[未作成カード]]` からスタブ定義カードを作成し、元カードと `related` で接続。本文は「【定義】【体系】【例】」の空欄テンプレ＋発端の付箋 |
| 📖 新しいカードにする | ⋮メニュー | 付箋本文を定義カードにし、元カードから `extends`（派生）で接続。付箋は解決済みに |
| 🔗 今すぐ接続 | ⋮メニュー | `[[リンク]]` 先の1件目と直接接続（候補を経由しない） |
| 🔗 カードを選んで接続… | ⋮メニュー | 検索ピッカーから相手を選んで直接接続 |
| 📋 別カードにも貼る… | ⋮メニュー | 同じ付箋を別カードへ複製（「[[元カード]] から」を末尾に付与） |

**なぜ承認なしで接続を書くか**: 自動候補（コサイン類似度）は誤りを含むので承認制だが、ここは全てユーザーが付箋メニューから明示的に選んだ操作。`ConnectionEngine.createManualConnection`（`isAuto=false`）と同格に扱う。

### 種付箋（🌱）
高校全教科シードには `source="seed"` の付箋が最初から貼られている（`db/HsStickies.kt`、157枚）。
色の意味: **黄**=疑問・確認 / **桃**=混同注意 / **青**=他科目とのつながり（既存リンク） / **緑**=広げどころ（未作成リンク）。
種付箋はダッシュボードの「未解決」件数・フィードには**数えない**（カード上には出る）。不要なら普通の付箋と同じく削除・解決できる。

### PC(Web)
詳細ペインの付箋に 📖（派生カード）・🔗（リンク先と接続）ボタンとリンクチップ。API:
`GET /api/sticky-notes/{id}/links`, `POST /api/sticky-notes/{id}/promote/definition`, `POST .../create-from-link {title}`, `POST .../connect {targetEntryId?}`。
