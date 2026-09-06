# walkthrough50 — 基本データ追加 (初回起動で InitialData+InitialData2 を自動投入)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスに JDK / Android SDK が無いため `assembleDebug` 未実行。構文・既存シグネチャは実ファイルを読んで確認。
**⚠ 次回実機セッションで `./gradlew assembleDebug testDebugUnitTest` を最初に通し、下記「検証」を実機確認すること。**

1機能。骨格（Android=データ本体、Drive API不使用、承認制接続）は無改変。

## 何が足りなかったか

基本データそのもの(`InitialData` 125定義 + `InitialData2` 13型グラフ)は wt15/wt45 で書いてあった。
しかし Hub透明性(wt16)で **Dashboard のボタン待ち** にしたため、初回起動は DemoData 8件だけで、検索・クイズ・白板・Wiki が空に近い。

「基本データ追加」= 既にある核を、デモ規模のDBへ **起動時に入れる**。新しい第3弾は作らない。

## 実装

`PersonalEncyclopediaApp.seedBasicDataIfSparse()` を Phase A の DemoData 直後に呼ぶ。

| 条件 | 動作 |
|---|---|
| `entry ≤ 20` かつ「枕草子」が無い | `InitialData.seedAppend` → `InitialData2.seedAppend` |
| センチネル「枕草子」がある | スキップ(1クエリ)。以降の起動は安い |
| `entry > 20` | 触らない。自作が多いDBを汚染しない |

「枕草子」は InitialData 第1件で DemoData に無い。投入はタイトル一致の冪等なので、ボタンとの二重実行も重複しない。
検索文書は続く Phase B の `rebuildAllSearchDocuments` が拾う。

Dashboard の投入/追記ボタンは残す(自作が多いDB・自動投入が届かなかったとき用)。文言を「基本データ」に合わせた。

## 検証（次回実機）

1. 新規インストール(または DemoData だけのDB)で起動 → ホーム総計が 8 ではなく 200件前後。検索で「枕草子」「福澤諭吉」が当たる。白板に「人物ハブ」。
2. 2回目の起動で件数が増えない(冪等)。
3. 自作が21件超のDBでは自動投入されない。Dashboard「基本データを追記」で足せる。
4. 既にボタンで投入済みのDBは起動しても増えない。

## 残

* JDK/SDK 無しのため本セッションでは Gradle 未実行。
* 自作21件超でまだ基本データが無いユーザーは手動追記が必要(意図どおり)。
* 初回 Phase A は ~200 insert のため、ホームが数秒空に見えることがある。
