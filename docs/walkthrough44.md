# walkthrough44 — 自動リンク索引の共有・自動更新 (mismatch §1.2 残課題)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` 未実行。
`AutoLinker` の新旧コンストラクタは kotlinc＋スタブで**同一出力**を確認(3アサーション)。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin` を最初に通すこと。**
**根拠:** mismatch §1.2「VM側5000件打切りとProvider側50000件の不整合、増分更新なし(`invalidate()` 呼出なし)」

## 1. 背景

P2-A/P2-B(wt25/27)で自動リンクは発動するようになったが、
- 詳細画面(`EntryDetailViewModel`)は**画面を開くたびに**5000件を読んでTrieを再構築(本文込みの全カラム読込)
- Wiki画面は `AutoLinkerProvider` の**一度作ったら二度と更新されない**キャッシュを使い、両者で件数上限も異なる
- 取込・作成・改名しても `invalidate()` はどこからも呼ばれず、新しいエントリーが Wiki のリンク対象にならない

## 2. 変更 (4ファイル)

* `db/dao/EntryDao.kt`: `linkerFingerprint()`(`COUNT || '-' || MAX(updatedAt)` の1クエリ)と
  `getAllTitles(): List<EntryTitle>`(id, title 射影)を追加。
* `importer/AutoLinkerProvider.kt`: 指紋が一致すればキャッシュを返し、変われば再構築。
  作成・改名・削除・取込は全て `updatedAt`/件数を動かすので**自動で反映**。`invalidate()` は互換で残置。
* `importer/AutoLinker.kt`: 主コンストラクタを `(id,title)` の `Sequence` に変更し、`fromTitles()` 追加。
  `AutoLinker(entries: List<EntryEntity>)` / `build()` は互換維持(削除済み除外もそのまま)。
* `viewmodel/EntryDetailViewModel.kt`: 自前構築をやめて `AutoLinkerProvider.get()` を使用。`EntryDao` 依存を除去。

## 3. 効果

* 詳細画面の初回表示コストから「5000件全カラム読込＋Trie構築」が消える(2回目以降は COUNT/MAX クエリ1発)。
* 5000件/50000件の不整合解消(上限なし・タイトル列のみ)。
* Wiki画面でも新規エントリーが即リンク対象になる。

## 4. 検証

* JVM: `AutoLinker.build(entities)` と `fromTitles(pairs)` の `applyAsWikiLinks` 出力一致、自己除外一致。
* 実機で確認: エントリーを新規作成→別エントリーの詳細を開くと新タイトルがリンク化されること。
  Wiki記事でも同様。`adb logcat` で Room のクエリ量が減っていること(任意)。
* 3条件チェック: ビルド ☐ / テスト ☐(JVMスタブ済) / 3日実使用 ☐

## 5. 残
* 表記揺れ・読み仮名(§1.2)、編集中サジェスト。
