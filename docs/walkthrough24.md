# walkthrough24 — ホワイトボード 既存エントリー配置 (Heptabase化1)

**日付:** 2026-09-04
**コミット:** 53a3f91 (ViewModel) + fc4b7a3 (Screen)
**ビルド:** 各コミット前に BUILD SUCCESSFUL (Screen側でimport漏れ1件→追加で解消)

## 1. 背景

* Heptabaseの中核は「既存カードをキャンバスに並べる」こと。従来のボードは自由メモしか置けず、
  `WhiteboardRepository.addEntryRef` は誰からも呼ばれない死に機能だった。まずここを開通させる。

## 2. 変更 (2ファイル・各1コミットで緑を維持)

* `viewmodel/WhiteboardViewModel.kt` (+27行):
  - `EntryDao` を注入 (Hiltの既存 `@Provides` を利用、新規DI設定なし)
  - `entryQuery` + `entryResults` (空欄=最近20件、入力時=`EntryDao.search`のLIKE検索・Flow切替)
  - `addEntry(entryId)` が `repo.addEntryRef` で配置 (重なり回避のランダムずらし付き)
* `ui/screen/WhiteboardScreen.kt` (+70行):
  - TopBarに🔍アクション追加 → 「エントリーを配置」ダイアログ
  - 検索欄 + 結果リスト。タップで配置して閉じる。型ラベル付き

## 3. 検証

* 2コミットとも単独でビルド成功。
* 配置後は `resolvedTitles` (walkthrough23) により表題表示のカードが生える。
  タップで `onNavigateToEntry` に遷移できる (既存のclickableが活きる)。
* 実機での検索→配置フロー確認は次回実機セッションで行う。

## 4. 次の一手 (Heptabase化の候補、1機能ずつ)

* ノード間の接続線表示 (知識グラフ `connection` をキャンバスに描画)
* セクションの作成・ドラッグ・リサイズ (枠は表示のみで今は作れない)
* カードのインライン編集 (メモ内容をボード上で直接編集)
