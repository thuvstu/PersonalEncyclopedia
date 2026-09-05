# walkthrough39 — PCリッチエディタ（Tiptap＋作成API）

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `:app:compileDebugKotlin` 成功＋ `bun run build` 成功（tsc＋vite、chunk警告のみ）
**根拠:** v15 §11.5、mismatch §5.2（Tiptap約束の履行）

## 1. 背景

v15 §11.5のPCリッチエディタ約束がコード0件だった。保存先API（POST /api/entries）
自体が存在しなかったため、サーバー・Webの両面を実装する。

## 2. 変更 (6ファイル＋依存2件)

* サーバー:
  - `server/dto/ApiDtos.kt` (+8行): `CreateEntryRequest(type/title/content)`。
  - `server/routes/EntriesRoutes.kt` (+約40行): `POST /api/entries`
    （13型バリデーション、thought/definitionは拡張まで作成、201応答）。
* Web:
  - `package.json`（bun）: `@tiptap/react @tiptap/starter-kit@3.31.3` を追加。
  - `web/src/api/client.ts` (+6行): `createEntry`。
  - 新規 `web/src/components/EditorPanel.tsx`（型選択＋タイトル＋Tiptap本文→HTML保存）。
  - `web/src/App.tsx` (+約10行): 6番目のタブ「作成」。
  - `web/src/styles.css` (+約25行): `.tiptap-wrap` 系。

## 3. 検証

* 両ビルド成功。実機ではLAN接続後に作成→エントリ一覧への反映を確認する。
* Zod/shared-types契約は未導入（将来）。

## 4. 次の一手

* 実機での総合動作確認（API疎通・通知・各画面）。
