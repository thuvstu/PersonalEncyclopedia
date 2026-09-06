# walkthrough45 — 初期データ第2弾: 13型すべてが埋まる知識グラフ (mismatch §4.4)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDKが無いため `compileDebugKotlin` 未実行。
`DemoData.seed → InitialData.seedAppend → InitialData2.seedAppend` を**インメモリ偽DAOで実行し、
2回目の実行が完全に無変更(冪等)であること・13型が揃うこと・接続/白板/Wikiリンクの参照整合を検証済み**(下記)。
**⚠ 次回実機セッションで `./gradlew :app:compileDebugKotlin` を最初に通し、Dashboardの投入ボタンを2回押して2回目が「投入済み」になることを確認。**
**根拠:** mismatch §4.4「自動シードは定義6+思考2、手動135件。`seedIfEmpty` は1件でもentryがあると何も足さず、画面文言『追記』と矛盾」

## 1. 背景

初期データは definition/thought の2型だけで、person/org/place/event/book/webpage/video/document/media/liked/ai_conv の
11型は空。型付き接続(authored_by / located_at / occurred_at …)も `seedTypeDefs` に定義があるだけで1本も張られておらず、
「13型＋接続」という骨格を初回起動で体感できなかった。さらに投入ボタンは空DB以外では無反応だった。

## 2. 変更 (5ファイル)

* 新規 `db/InitialData2.kt` (約500行): **13型すべて**のサンプルデータ。
  - 人物12(福澤諭吉・紫式部・芭蕉・スミス・チューリング・クヌース・キュリー・ケインズ・ソクラテス・アーレント・北斎・エビングハウス)
  - 組織6・場所6(座標付き)・出来事7(日付付き)・書籍9・Web7(実URL)・動画4・文書3(本文入り)・メディア3(OCR付き)・いいね3・AI会話2(4往復JSON)
  - 追加定義15(独立自尊・見えざる手・有効需要・チューリング機械・停止問題・忘却曲線・間隔反復・想起練習・無知の知…)・思考3
  - **型付き接続 67本**: authored_by(著作→人物) / located_at(組織→場所) / occurred_at(出来事→場所) / exemplifies / extends / references / related。
    第1弾の定義(明治維新・関ヶ原の戦い・ハッシュテーブル・再帰・IS-LM分析・不法行為…)にも張る
  - タグ16種・トピック3(哲学/芸術/学習法)・Wiki5(人物→著作→組織→概念のハブ記事)・クイズ16(mcq 11 + fill_blank 5、ヒント付き)
  - 白板「人物ハブ」: 福澤・チューリング・ソクラテスを中心に放射状配置、**エッジのラベルは接続種別の日本語名**(wt40のエッジと接続の対応例)
  - **冪等**: タイトル(定義は term)一致で存在確認 → 既存は再利用して接続だけ張る。クイズは設問文、Wikiはタイトル、白板は同名ボードで判定。
    端末固有パス(blobPath 等)は入れない。
* `db/InitialData.kt`: `seedIfEmpty`(空DBガード) → **`seedAppend`(タイトル一致で冪等追記、`Result(added, skipped)`)** に置換。
  定義に `entry_topic` リンクを付与(従来は topicId を持ちながら未リンク)。トピックは DemoData 分も IGNORE 挿入して FK を保証。
* `viewmodel/DashboardViewModel.kt`: 投入ボタンで第1弾→第2弾を順に実行し「N件を追加(既存M件はスキップ・接続K件)」/「投入済み」をトースト。
  投入後に `SearchRepository.rebuildAllIndices()`(差分更新)で検索文書を即時反映。`SearchRepository` を注入。
* `ui/screen/DashboardScreen.kt`: 文言を実数(13型211件・接続87・Wiki11・クイズ46)に更新。

## 3. 検証(インメモリ偽DAO・kotlinc 2.4.10)

```
DemoData:        entries=8   quizzes=10 conns=4  boards=2 wiki=2
InitialData#1:   added=131 skipped=0   entries=139 quizzes=40 conns=24 wiki=8
InitialData2#1:  added=80  skipped=0   conns+=67 entries=219 ext=62 tags=16/43 quizzes=56 conns=91 boards=3 nodes=25 edges=14 wiki=13 topicLinks=205
2nd run:         r1 added=0 skipped=131; r2 added=0 skipped=80 conns+=0   ← 全カウント不変
types: 13種すべて   unresolved wiki links: []   接続の端点欠落: []   ALL CHECKS PASSED
```
実機で確認: Dashboard「初期データを投入」→ 人物「福澤諭吉」の詳細に 著者/関連 の接続チップ、白板「人物ハブ」にラベル付きエッジ、
検索「チューリング」で person/event/book/webpage が混在ヒット、クイズに穴埋め形式が出ること。2回目押下で「投入済み」。
3条件チェック: ビルド ☐ / テスト ☐(JVM偽DAO済) / 3日実使用 ☐

## 4. 残
* 数値の正確性は一般的な参考書レベルで確認したが、紀元前の日付は `GregorianCalendar` の負の年を使うため表示が概数(例: ソクラテス裁判)。
* 文書/メディアは実体ファイル無し(抽出テキスト・OCRのみ)。第3弾で「例題つき数学」「英文法の例文」など definition の examplesJson を埋める。
