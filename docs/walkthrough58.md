# walkthrough58 — 付箋から「増殖」する（カード・リンク・接続をひたすら生やす）

**日付:** 2026-09-09
**コミット:** `309de27`(増殖UI/API) → `639c825`(データ強化) → `16fb518`(質の強化) → `a0fae31`(純粋関数抽出+テスト) → `a189c72`(PC接続グループ) → `8e1bf6c`(guide)
**ビルド:** 未検証（JDK/SDK/ネットワーク無し）。次回最初に `./gradlew assembleDebug` と `cd web && bun run build`。

## 1. 依頼
「（高校全教科のカードに）付箋とかリンクとかひたすら作り広げていけるようにしたい」。
wt56 の付箋は「貼る→解決/昇格」で終点だった。今回は付箋を **出発点** にして次のカード・リンク・接続を生やし、
生えた先にまた付箋が貼れる（＝無限に広げられる）ループにした。

## 2. 増殖ループ
```
カード ─貼る→ 付箋「[[○○]]と混同しがち」
              ├ [[○○]] が既存 → チップ🔗 タップで開く / 「今すぐ接続」
              ├ [[○○]] が未作成 → チップ「＋○○を作る」→ スタブ定義カード + related接続 → そのカードへ
              ├ 「📖 新しいカードにする」→ 付箋本文が定義カードに + 元カードから extends接続
              ├ 「🔗 カードを選んで接続…」→ 検索ピッカー → 直接接続
              └ 「📋 別カードにも貼る…」→ 同じ付箋を関連カードに複製
新カード ─貼る→ 付箋 … (繰り返し)
```
エディタで `[[` を打つとタイトル候補チップが出て、タップで `[[タイトル]]` に補完される。未作成タイトルはその旨を表示。

## 3. 設計判断
- **直接接続を許した**: wt56 は「候補→承認」だけだったが、ユーザーが付箋メニューから明示的に選ぶ操作は手動接続（`ConnectionEngine.createManualConnection`, `isAuto=false`）と同格なので `connection` に直接書く。自動候補の承認制は従来通り。
- スタブカードは `definition` 型。分野(`field`)は元カードから継承し、本文に「（付箋から作成・未記入）」と元付箋・元カードリンクを残す。
- 全操作は `StickyNoteRepository` に追加（原則「手続きは共有サービスに」）。Android UI と PC API が同じメソッドを叩く。
- UI は `StickyNoteActions` に optional コールバックを追加しただけなので、SRS/クイズ/白板など「渡していない画面」には新メニューが出ず、既存挙動は不変。

## 4. 変更ファイル
- `repository/StickyNoteRepository.kt` … `resolveLinks / suggestTitles / promoteToDefinition / createCardFromLink / connectNow / copyTo`。DI に `EntryDefinitionDao`, `ConnectionEngine` 追加
- `viewmodel/StickyNoteController.kt` … 同名メソッド + `Event.CardCreated / Connected`
- `ui/component/StickyNoteHost.kt` … Binding で新アクション配線、Snackbar「カードを作成・接続しました → 開く」
- `ui/component/StickyNoteSection.kt` … `StickyLinkChips`（🔗開く / ＋作る）、メニュー4項目、`StickyCardPickerDialog`、エディタ `[[` 補完。file-level OptIn(ExperimentalLayoutApi/Material3)
- `server/routes/StickyNoteRoutes.kt`, `server/dto/ApiDtos.kt` … `GET /sticky-notes/{id}/links`, `POST .../promote/definition`, `POST .../create-from-link`, `POST .../connect`
- `web/src/api/client.ts`, `web/src/components/StickyNotes.tsx`, `EntryDetail.tsx`, `styles.css` … リンクチップ・📖・🔗ボタン

## 4b. データ強化（「データごと強化だ」）— `db/HsStickies.kt`
高校全教科の約900カードに、増殖の**入口**を最初から仕込んだ。

| 種類 | 件数 | 内容 |
|---|---|---|
| 🌱 種付箋 `HsStickies.common` | 157枚 | 起点カードに貼ってある短い問い。色で役割分け: 黄=疑問・確認 / 桃=混同注意 / 青=他科目とのつながり（既存 `[[リンク]]`→チップで即ジャンプ・接続） / 緑=広げどころ（**未作成** `[[リンク]]`→「＋作る」で新カードが生える）。`source="seed"`、UIバッジ「🌱 種付箋」 |
| 科目横断ブリッジ `HsStickies.bridges` | 106本 | 漢文「孔子と『論語』」↔倫理「孔子と儒家」、化学「気体の状態方程式」↔物理、生物「免疫の概要」↔保健「感染症」、数学「三角関数」↔物理「単振動」など、Hs*.kt 内では張れなかった科目間の太い線。InitialData/DemoData の既存カードも相手にする |
| 自動 references `seedAutoReferences` | 動的（数百本見込み） | 全定義本文を走査し、他の定義タイトル（4文字以上）が本文中に出てきたら `references`（本文が言及）を張る。1カード最大6本。タイトルの包含関係（「評論」⊂「評論文の読み方」）は誤爆防止で除外 |

投入: `seedAppend(..., stickyDao=)` の末尾で実行。**既に wt57 で本体投入済みのDB**にも、`seedEnrichmentOnly()` が「seed 付箋が1枚もない」条件で一度だけ足す（`PersonalEncyclopediaApp.seedHighSchoolIfNeeded`）。全て冪等。

## 4c. 質の強化（「ただ増やすだけではない」）
量を増やした結果あらわになった**構造の欠陥**を直した。

1. **関係型の欠落を修正**: シード接続 1,030 本のうち `prerequisite` 254 本・`contrast` 32 本が `connection_type_def` に未登録で、UIでは生の英語ラベル、向きも不明だった。`ConnectionEngine.seedTypeDefs` に `prerequisite`(前提 / 逆: 次に進む, 有向)・`contrast`(対比, 無向) を追加（`IGNORE` insert なので既存DBにも次起動で入る）。
2. **接続セクションを「学習動線」に再設計** (`ui/component/ConnectionSection.kt`): 平置きFlowRow+強度の数字 → **⬅前提（先に） / ➡次に進む / ↔対比・混同注意 / 🔗関連 / 📎参照・例示** の5グループ。有向接続は `ConnectionWithEntry.isSource`（DAOのUNIONに `1/0 AS isSource` を追加）で向きを判定し、終点側では `inverseLabelJa` を出す。各グループ6件＋「＋N」展開、強度≥0.9 は背景色で強調、接続 note（「本文が言及」「同一法則」等）を2行目に表示。
3. **シードクイズ→カード紐づけ** (`InitialDataHighSchool.linkQuizzesToCards`): wt51/57 の initial クイズ 300+ 問は `sourceEntryId=null` で、カードから辿れず付箋も貼れなかった。設問/正答/解説に最長一致する定義タイトルのカードへ `sourceEntryId` を書き込む（`QuizDao.getUnlinkedInitial/setSourceEntry`）。毎起動で未紐づけ分だけ処理（0件なら即return）。
4. **カード詳細に「❓ このカードのクイズ」** (`EntryQuizPeekSection`): 設問だけ並べ、タップで答え。読む前に思い出す（能動想起）。「解く」でクイズ画面へ。`QuizRepository.observeQuizzesForEntry` / `EntryDetailViewModel.entryQuizzes` / NavGraph に `onNavigateToQuiz`。
5. **スタブカードの雛形**: 付箋の `[[未作成]]` から生えるカードの本文を「【定義】（1文で）【体系】（上位・下位・対比）【例】（1つ）」の空欄テンプレにした。既存シードと同じ骨格なので、埋めれば同質のカードになる。
6. **種付箋をフィードから分離**: `observeRecentUnresolved / observeUnresolvedCount` から `source='seed'` を除外。157枚がダッシュボードの「未解決」を占拠しないように。カード上には出る。

## 4d. 仕上げ（プロダクト品質）
- **テスト**: `db/InitialDataHighSchoolPureTest`（本文言及の抽出・クイズ最長一致・**全14教科シードの整合性**: タイトル重複なし／自己接続なし／mcq正答が選択肢に含まれる／白板ハブ≤4／種付箋の貼り先が既知／色が4色）、`ui/ConnectionGroupingTest`（5群分割と向き）。JVM単体で走る（DB不要）。Python で同等の検査を事前実行し、mcq 2件の検出は正規表現の誤検知（実データは正常）、種付箋の貼り先 2件（行動経済学・確率の定義）は既存カードだったので `extraLinkTitles` に追加して既知化。
- **補完の質**: `EntryDao.suggestByTitle`（前方一致→短いタイトル→最近アクセス順）を追加。旧 `search` は本文 LIKE も含み候補が濁っていた。
- **ピッカー**: `suggestEntries` で (title, id) を直接返す。旧実装の「付箋を偽装して resolveLinks で id を引く」ハックを撤去。
- **PC**: `web/src/components/ConnectionGroups.tsx` — Android と同じ5群を詳細ペインに表示（これまで PC 詳細は接続を出していなかった）。
- **ガイド**: `docs/guide/07-sticky-notes.md`（増殖・種付箋・API）、`docs/guide/03-connection.md`（5群の読み方・シード接続3層）を追記。

## 5. 確認手順
1. 任意の定義カード（例: 係り結びの法則）を開き、付箋に `[[強意の係助詞]]は「ぞ・なむ・こそ」` と入力 → `[[` 補完が出ること
2. 保存 → 「＋ 強意の係助詞 を作る」チップ → 新カードへ遷移、接続セクションに元カードが related で出ること
3. 新カードに付箋 → 「📖 新しいカードにする」→ さらに派生カードが extends で繋がること
4. PC: 同カードで 📖 / 🔗 ボタン、リンクチップが動くこと

5. 「係り結びの法則」に🌱付箋3枚（黄・桃・緑）が最初から貼ってあり、緑の「＋ 係り結びの省略 を作る」で新カードが生えること
6. 設定/ダッシュボードの追記ボタンの結果に「種付箋157」相当が出ること（2回目は0）

7. 「係り結びの法則」の接続セクションが「➡次に進む: 四段活用…」「🔗関連: …」のようにグループ表示され、`prerequisite` が「前提/次に進む」の日本語ラベルになっていること
8. 同カードに「❓ このカードのクイズ」が出て、タップで答えが開くこと（初回起動後に紐づく）

## 6. 未検証・注意
- `StickyNoteRepository` のコンストラクタ引数が増えたため、`app/src/test/.../repository/` の既存テストでモック引数の追加が必要（コンパイルエラーになる想定。`definitionDao`/`connectionEngine` を追加）。
- `ConnectionWithEntry` に `isSource` を追加したため、`ConnectionResponse`(API) にも同名フィールドを追加済み。web 側は未使用（読み飛ばし）。
- 自動 references は 900×900 の文字列 contains 走査（約80万回）。初回起動で1〜2秒程度の想定。遅ければ `AUTO_REF_CAP` を下げるか Dashboard ボタンのみに退避。
- `EntryDao.search` は FTS 依存。空DB直後は候補が出ないことがある（FTS再構築後は出る）。
