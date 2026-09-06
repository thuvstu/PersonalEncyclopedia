# walkthrough46 — 自動リンクの表記揺れ対応 (mismatch §1.2 残課題「完全一致Trie」)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` / `testDebugUnitTest` 未実行。
`AutoLinker`/`AutoLinkerProvider` と新規 `AutoLinkerTest` は kotlinc＋スタブで **15テスト全通過**、
シード全文(219エントリー・232テキスト)に対する回帰は **旧実装と一致(218/218)・誤検出ゼロ** を確認。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin testDebugUnitTest` を最初に通すこと。**
**根拠:** mismatch §1.2「残: 完全一致Trie (表記揺れ・読み仮名非対応)」/ 既存機能の改良に注力する方針

## 1. 背景

wt44 で自動リンク索引は共有・自動更新されるようになったが、照合は**タイトルとの完全一致**のみだった。

- `福沢諭吉`(常用) と登録タイトル `福澤諭吉` は別物 → リンクされない
- 定義に `reading`(読み)、人物に `fullName`/`aliasesJson`(別名)、組織/場所/出来事に正式名があるのに索引に使っていない
- `Google`/`google`/`ＧＯＯＧＬＥ`、`ｽﾐｽ`/`スミス`、`コンピュータ`/`コンピューター` はすべて不一致
- 一方で完全一致でも `Go` が `Google` の内部で、`アイ` が `アイデア` の内部で発火する誤検出があった

## 2. 変更 (4ファイル + テスト1)

* `importer/AutoLinker.kt`
  - 照合を **1文字→1文字の正規化**(全角英数記号→半角・全角空白→半角・半角カナ→全角・英字小文字化)後に行う。
    1:1 なので一致範囲は原文の位置のまま(`LinkMatch.start/end` の意味は不変)。
  - `fromTitles(titles, aliases)` で **別名表層形**を同じTrieに登録。別名がどこかのタイトルと衝突したらタイトル優先。
    `LinkMatch.canonicalTitle` を追加し、`applyAsWikiLinks` は一致文字列≠正式タイトルのとき
    **`[[正式タイトル|一致文字列]]`** を出力(表示は原文のまま、タップ解決は正式タイトル)。
  - 誤検出抑制: 英数字は単語境界、カタカナは同種連続の境界でのみ一致(`Go`≠`Google`、`アイ`≠`アイデア`、中黒は区切り)。
    ひらがなのみの表層形(読み)は**両側がひらがな**の位置では採用しない(`そのていしもんだいが` は不一致、
    `停止問題（ていしもんだい）`・文頭・漢字直後は一致)。かなのみの表層形は3文字以上のみ登録。
  - 語末長音: `…ター` 形は `…タ` も登録、照合時は語末直後の `ー` を範囲に取り込む(双方向)。
  - 互換: `AutoLinker(entries)`/`build()`/`fromTitles(titles)` は従来通り動く(別名なし)。
* `db/dao/EntryDao.kt`: `getAllAliasForms()` — 定義 `reading`・人物 `fullName`/`aliasesJson`・組織 `officialName`・
  場所 `placeName`・出来事 `eventName` を `(id, 表層形)` 射影で UNION ALL(削除済み除外・本文は読まない)。
* `importer/AutoLinkerProvider.kt`: 構築時に `getAllAliasForms()` も読み、`expandAliasForms()` で
  `aliasesJson`(JSON配列)を要素展開して `fromTitles(titles, aliases)` へ。別名クエリ失敗時はタイトルのみで継続。
  指紋(`COUNT-MAX(updatedAt)`)は据え置き — 拡張テーブルの編集も `updateDefinition`/`updateEntryCommon` が
  `entry.updatedAt` を更新するので検知できる。
* `ui/component/RichContentView.kt`: `wiki://` href を `Uri.encode(title)`、クリック側で `Uri.decode`。
  従来は生の日本語/空白をhrefに埋めていたため、WebView(Chromium)が %エンコードして渡す URL を
  そのまま `findByTitle` に渡していた(別名リンク `[[正式タイトル|表示]]` を確実に往復させるための同梱修正)。
* `app/src/test/.../importer/AutoLinkerTest.kt`(新規・15件): 別名→正式タイトル解決、全半角/大小文字/半角カナ、
  単語・カタカナ境界、ひらがな埋没抑制、長音双方向、タイトル優先、自己除外、既存 `[[ ]]` 保護、
  `wikiLinkMarkup` の不正文字拒否、`expandAliasForms`、旧コンストラクタ互換。

## 3. 効果

* `福沢諭吉`→`[[福澤諭吉|福沢諭吉]]`、`ていしもんだい`→`[[停止問題|ていしもんだい]]`、
  `ＧＯＯＧＬＥ`→`[[Google|ＧＯＯＧＬＥ]]`、`コンピューター`→`[[コンピュータ|コンピューター]]` がタップ可能に。
* シード219件では別名表層形44件が索引に加わり、既存232テキストのリンク結果は**旧実装と完全一致**
  (誤検出の増減なし)。構築 ~3ms、232テキスト走査 ~14ms(JVM)。
* 詳細画面(本文・定義文)と Wiki 記事の両方が同じ Provider を使うため、追加の画面改修なしで発動する。

## 4. 検証

* JVM: `AutoLinkerTest` 15件(kotlinc+スタブで実行済み。Gradle では `testDebugUnitTest` で走る)。
* シード回帰: `DemoData`+`InitialData`+`InitialData2` の全文で新旧 `findMatches` を突合 → 差分0。
* 実機で確認: (a) 人物「福澤諭吉」を残したまま別エントリー本文に「福沢諭吉」と書く→リンク化→タップで
  福澤諭吉のプレビューが出る。(b) 定義の読みを本文に書く(例「ていしもんだい」)→同様。
  (c) 空白を含むタイトル(例「Adam Smith」)の `[[ ]]` タップが解決する(Uri.encode 修正の確認)。
* 3条件チェック: ビルド ☐ / テスト ☐(JVMスタブ済) / 3日実使用 ☐

## 5. 既知の限界・残
* 異体字(澤/沢)は**別名として登録されている場合のみ**吸収(自動変換なし)。人物なら「別名(カンマ区切り)」欄に書く。
* 濁点付き半角カナ(`ｸﾞ`=2文字)は1:1正規化の対象外、ひらがな⇄カタカナの相互一致も対象外。
* 編集中サジェスト(入力補完)は未着手。
