# walkthrough27 — Wiki記事への自動リンク配線 (P2-B)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL
**根拠:** 強化計画 Phase 2（P2-Aの同型展開）

## 1. 背景

P2-Aで詳細画面の本文・定義文に自動リンクを開通させた。§12.5が約束する3面
（ノート・定義・記事）のうち記事だけが残っていた。WikiはVMがentry層を知らず、
クリック解決も記事内のみだった。

## 2. 変更 (2ファイル)

* `viewmodel/WikiViewModel.kt` (+約30行):
  - `AutoLinkerProvider`（既存Singleton・5万件キャッシュ）+ `EntryRepository` を注入。
  - `autoLinkedContentMd`（article×linkerのcombine、未構築時は原文）。
  - `resolveLink(title, onWiki, onEntry, onMissing)`（記事→なければentry→なければmissing）。
* `ui/screen/WikiScreens.kt` (+約10行):
  - `WikiArticleScreen` で埋め込み済み本文を表示、クリックは記事→entryの順に解決。
  - 未使用の `rememberCoroutineScope` を削除（解決はVMに寄せた）。

## 3. 検証

* コンパイル成功。実機では記事中の既知タイトルがリンク化→タップで記事/詳細へ遷移することを確認する。
* P2-C（EntryTypeSections孤児確認）は別途。P2-Aで定義文経路は既に配線済みのため残作業は確認のみ。

## 4. 次の一手

* P1-1: 白板セクションCRUD UI。
