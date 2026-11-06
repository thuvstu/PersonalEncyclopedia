# walkthrough64 — Webクライアントのレスポンシブ対応 + DESIGN.md の誤記11箇所を実測で修正

## 0. 方針

wt63 でサーバー寿命を分離したが、Android側は当サンドボックスでビルド不可(JDK/SDK/adbなし、Gradle・Maven到達不可)。
検証できない変更を積み上げるのは AGENTS.md の「エラー3ファイル基準」リスクが高いので、**検証可能なWeb側**に進んだ。

環境調査の結果、Web側は検証できた:
- npmレジストリ到達可 → `npm install -g bun` で **bun 1.4.4** を導入(AGENTS.md は web/ に bun を要求)
- `bun install` → 95パッケージ、**lockfile差分なし**
- `bun run build`(`tsc --noEmit && vite build`) → **成功**

### ベースライン(直す前に計測)

| 項目 | 変更前 | 変更後 |
|---|---|---|
| CSS | 24.34 kB (gzip 4.74) | 25.90 kB (gzip 5.07) |
| JS | 791.67 kB (gzip 251.18) | 791.80 kB (gzip 251.22) |
| ビルド | 成功 | 成功 |

JS増分 +0.13 kB は戻るボタンとクラス名のみ。chunk 500kB警告は**変更前から出ていた**既存事項(本次未対処)。

## 1. なぜレスポンシブが先か

当初要求は「それ以外すべての端末で利用するWebアプリ(**Androidスマホでも一応使う**)」。
しかし `styles.css` の **幅ベース `@media` は0個**(`prefers-color-scheme` のみ)で、スマホでは実用に耐えない状態だった:

| 問題 | 実測根拠 |
|---|---|
| 詳細欄がほぼ消える | `.main-split` が横並び固定、`.entry-pane` が `width:320px / min-width:260px` → 390px幅で詳細は70〜130pxしか残らない |
| タブが横溢れ | `.tabs` は `display:flex` で折返し・スクロール指定なし。6タブが溢れる |
| ヒートマップが横溢れ | `.heatmap-cell` の `min-width:3px` + `gap:2px` × 90日 ≈ 448px必要 |

## 2. 変更内容(2ファイル)

### `web/src/styles.css` (+184行、末尾追記のみ)

`@media (max-width: 760px)` を新設。**PC側の既定ルールは一切書き換えていない**(追記のみ)。

- **master-detail**: `.main-split` を縦積みにし、`.has-selection` の有無で一覧/詳細を排他表示
- `.back-to-list`: 既定 `display:none`(PCでは非表示)、スマホ幅でのみ `display:block`
- `.app { height: 100dvh }`: モバイルのアドレスバー伸縮に追従(未対応ブラウザは `height:100%` にフォールバック)
- タブは折返さず横スクロール(縦スペースを食わない)、スクロールバーは非表示
- 接続バーは brand と status を全幅化、入力欄を flex 化
- `.heatmap-cell { min-width: 0 }` + `gap:1px` で90日でも溢れさせない
- タップ領域拡大(button/item/chip)、`srs-grades` 折返し、quiz選択肢を全幅化

**途中で自分のバグを検出して修正**: 縦積みにすると `.entry-pane` は flex アイテムとして `min-height:auto`(コンテンツ由来の最小高)になり縮まない。
一覧が長いと `overflow:hidden` で切られ**下部のエントリに到達できなくなる**。`flex:1; min-height:0` を入れて内部の `.entry-list` にスクロールを任せた。
PC版は横並びで `align-items:stretch` に救われていたため顕在化していなかった箇所。

### `web/src/App.tsx` (+5行)

- `.main-split` に `selectedId` に応じて `has-selection` を付与
- `.right-column` 先頭に戻るボタンを追加(PCでは CSS で非表示)

`EntryList` は `display:none` でも**マウント維持**なので、詳細から戻っても検索クエリ・型フィルタ・スクロール位置が保持される。

## 3. 検証(実施済み)

| 確認 | 結果 |
|---|---|
| `bun run build` (`tsc --noEmit` 含む) | **成功** ✅ |
| 出力CSSにメディアクエリ存在 | `@media (width<=760px)` を確認(minifyで Range記法に変換される) ✅ |
| PC既定ルールが残存 | `.main-split{flex:1;display:flex;overflow:hidden}` と `.entry-pane{...width:320px;min-width:260px...}` を確認 ✅ |
| `.back-to-list` がPCで非表示 | `.back-to-list{display:none}` を確認 ✅ |
| `min-height:0` が出力されたか | `.entry-pane{border-right:none;flex:1;width:100%;min-width:0;min-height:0}` を確認 ✅ |
| `web/dist/` がコミットに混入しないか | `.gitignore:15` で無視を確認 ✅ |

### 未検証

**ブラウザでの実描画は未確認**(当サンドボックスに chromium/firefox/playwright いずれも無し)。
CSSの論理とビルド出力は確認したが、視覚確認はユーザー環境で行うこと:

```bash
cd web
bun install
bun run dev   # → http://localhost:5173 をスマホ実機またはDevToolsのデバイスモードで開く
```

確認観点: ①一覧が下端までスクロールできる ②タップで詳細に切替わる ③「← 一覧に戻る」で検索状態が保持される
④タブが横スクロールする ⑤ヒートマップが横溢れしない ⑥PC幅(>760px)で従来どおり3ペイン。

## 4. DESIGN.md の誤記11箇所を修正(★本次の副産物)

Web導線をドキュメントに書くため実コードを照合したところ、**記述が広範囲に誤っていた**。
すべて実測で確定させてから置換した(推測では書いていない)。

| 箇所 | 旧記述 | 実測 |
|---|---|---|
| §7.1 / §13 B1 / §15 #S2 | 「CORSプラグイン未導入(依存・install共になし)」 | **導入済み**。`LocalServer.kt:55-64` に `install(CORS){anyHost()...}`、`app/build.gradle.kts:97` に依存 |
| 構成図 / §7.1 | 「8登録・全22エンドポイント」 | **9登録・全31エンドポイント**(`LocalServer.kt:99-108`) |
| §8.2 / §13 C5 / §15 #W1 | 接続CRUD・候補承認・ヒートマップ・SRS件数が「Web導線なし」 | **すべて `ConnectPanel` で配線済み** |
| §13 A1 / §15 #S1 | 「`GET /quiz/count` が `/{id}` に吸収され404」 | **解消済み**。`/count`(31行) が `/{id}`(36行) より前 |
| §8.2 見出し | 「App.tsx の4タブ」 | **6タブ**(つながり・作成が抜けていた) |

### 計測手法の誤りを2回、自分で潰した

ドキュメントに数字を書く以上、grepの取り損ねを放置できない:

1. **API呼出件数**: 行単位grepで `getStickyNotes` を「呼出0件」と誤計上。実際は `api` と `.getStickyNotes` が**改行をまたいでいた**(`StickyNotes.tsx:32-34`)。
   空白正規化して数え直し → `client.ts` 27メソッド中、**呼出0件は `getRecentStickyNotes` の1件だけ**だった。
2. **エンドポイント数**: `get("` のような括弧付きだけ数えて22としたが、Ktorはパス省略形 `get { }` が書ける。
   省略形8箇所を取り損ねていた。含めて数え直し → **31**。内訳: entries 5 / search 1 / srs 3 / quiz 4 / connections+candidates 6 / graph 1 / progress 1 / plugins 1 / sticky-notes 9。

もし誤計測のまま書いていたら、wt63 で指摘した「在庫表の陳腐化」を**自分で再生産**していた。

### 本当に残っているWeb未配線(実測)

`client.ts` に**関数自体がない**もの: `DELETE /api/entries/{id}`・`PATCH /api/entries/{id}/favorite`・`GET /api/quiz/{id}`・`GET /api/quiz/count`・`GET /api/plugins`。
呼出0件の定義済み関数: `getRecentStickyNotes`。
そして**エントリ更新はサーバ側にも存在しない**(G3)。

## 5. バックログ(wt63 §5 から更新)

| # | 内容 | 状態 |
|---|---|---|
| G1 | Web作成エントリが検索インデックスに載らない(`enqueue` 未呼出) | 未着手 |
| G2 | `/api/search` が `LIKE '%q%'`。HybridSearch未露出 | 未着手 |
| G3 | エントリ更新APIがない | 未着手 |
| G4 | 可搬JSONが connection/srs/quiz/task を運ばない + import が insert-only | 未着手 |
| G5 | Ktor同一オリジン配信 | 未着手 |
| — | mismatch.md の再同期(38 walkthrough分古い) | **DESIGN.md 側は本次で一部消化**。mismatch.md 本体は未着手 |

## 6. Git

- 変更: `web/src/styles.css`・`web/src/App.tsx`・`DESIGN.md` + 本walkthrough
- 論理1機能(Webレスポンシブ)として1コミット。ドキュメント誤記修正は同一コミットに含めた(実測という同一作業の産物)
- **ビルド検証済み**(Web)。Android側は本次未変更
