# walkthrough42 — 検索の並べ替え・絞り込み (mismatch §3.4)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` 未実行。
`SearchRefiner`＋`SearchRefinerTest` は kotlinc 2.4.10＋スタブで**JVM実行し5件全PASS**。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin testDebugUnitTest` を最初に通すこと。**
**根拠:** mismatch §3.4「ソート固定(createdAt DESC)・タグ/日付/お気に入り絞込なし・件数表示が紛らわしい」

## 1. 背景

検索は4モード＋型チップのみ。並べ替え不可、タグ・期間・お気に入りで絞れず、
結果カードの☆は空クロージャで押しても何も起きなかった。件数は30件打切りなのに総数に見えた。

## 2. 設計

エンジン(`HybridSearchEngine`)は触らない。関連度順の候補を**多め(100件)**に取り、
後段の純粋関数 `SearchRefiner.apply(candidates, criteria, tagsByEntry)` で条件を適用する。
条件変更は**再検索なし**でメモリ上即時反映(体感が速い)。モード変更だけ再検索。

## 3. 変更 (5ファイル)

* 新規 `brain/search/SearchRefiner.kt`: `SortKey`(関連度/更新新/作成新/作成古/タイトル)・
  `Period`(全期間/1週/1月/1年、updatedAt基準)・`Criteria`(型/お気に入り/期間/タグAND/並べ替え)。
* `viewmodel/SearchViewModel.kt` (書換): `criteria`/`results`(combine)/`candidateCount`/`allTags`。
  候補のタグは `TagDao.getTagsForEntries` で一括取得(N+1回避)。`toggleFavorite` を実装(候補リストも即時更新)。
  `typeFilter` は互換のため残置(criteria.type の写像)。`TagDao`・`EntryRepository` を注入。
* `ui/screen/SearchScreen.kt`: TopBarに🎛(Tune)で絞り込みパネル開閉(条件が既定以外なら強調)。
  並べ替え・期間・☆お気に入りのみ・#タグ のチップ列＋「条件をクリア」。
  件数表示は「候補N件中M件（条件適用）」と正直に。空状態も「条件で除外」を区別。☆タップが効くように。
* 新規 `test/.../brain/search/SearchRefinerTest.kt` (5件): 既定=関連度順、5ソート、型/☆/期間、タグAND・大小無視、複合。

## 4. 検証

* JVM: 5/5 PASS(スタブ実行)。
* 実機で確認: 検索→🎛→並べ替え切替が即時、期間1週間で古い結果が消える、
  #タグ2つ選択でAND、☆で結果カードのお気に入りが切り替わる。
* 3条件チェック: ビルド ☐ / テスト ☐(JVMスタブ済) / 3日実使用 ☐

## 5. 残
* 保存フィルタ(`saved_query` は SQL Explorer 専用のまま)、AND/OR/除外構文、総件数のCOUNTクエリ。
