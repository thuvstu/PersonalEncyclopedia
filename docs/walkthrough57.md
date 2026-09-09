# walkthrough57 — 高校全教科シードデータ（古文〜ノンジャンル 14教科）

**日付:** 2026-09-09
**コミット:** `29f7620`(14教科+エンジン) → `cfe3acb`(起動/ダッシュボード連携)
**ビルド:** 前回(wt56)同様、作業環境に JDK / Android SDK / 外部ネットワークが無く `./gradlew` を実行できていない。
**次回セッションの最初に必ず `./gradlew assembleDebug` を回すこと。** シードは純データ+既存DAO呼び出しのみで、
`InitialDataMath` と同じAPIだけを使っているため、失敗するとしたらエンジン `InitialDataHighSchool.kt` のDAO呼び出し名の可能性が高い。

## 1. 依頼
「古文 漢文 英語 数学 化学 物理 生物 地理 歴史 政治 経済 倫理 情報 法 ノンジャンル の全データ完全追加」。
数学は wt51 で新課程全単元(215定義)が既に入っているため対象外とし、残り 14 教科を追加した。

## 2. 設計判断
- **教科ごとに1ファイル** (`db/Hs*.kt`)。各ファイルは `object HsXxx { val seed = SubjectSeed(...) }` という純データで、投入ロジックは持たない。
- **共通エンジン** `db/InitialDataHighSchool.kt`。`InitialDataMath.seedAppend` と同じ手順（Topic upsert → 定義をタイトル冪等で追加 → 思考 → クイズ(設問文冪等) → 接続(タイトル解決・未存在はスキップ) → タグ → Wiki(タイトル冪等) → 白板(ボード名冪等)）を `SubjectSeed` に対して回す。
- **センチネル**は `係り結びの法則`(古文)。`DemoData/InitialData/InitialDataMath` のどれにも無いタイトル。
- **既存タイトルは再利用**: InitialData の 古典20/英語20/地歴25/法20/経済20 や DemoData の 忘却曲線・チューリング機械 等は Def を作らず `Conn` で新規定義から張るだけ。二重登録しない。
- 定義文は数学と同じ「【定義】…【体系/例】…」スタイル。読み(reading)も全件入れた。
- 教科間の横断リンクを意図的に多く張った（例: 情報→数学「データの分析」、倫理→古典「本歌取り」は削除、ノンジャンル→保健→生物「免疫」、家庭科→法「消費者契約法」）。未存在タイトルへの Conn はエンジン側でスキップされるので安全。
- ルート topic は既存 id を再宣言して上書き（`topic-koten`/`topic-english`/`topic-law`/`topic-cs`/`topic-philosophy` 等）。InitialData 側の色は変わるが名前は維持。新規ルートは `topic-chem` `topic-phys` `topic-bio` `topic-geo` `topic-hist` `topic-politics` `topic-misc` 等。

## 3. 規模（定義 / クイズ / 接続）
| 教科 | ファイル | 定義 | クイズ | 接続 |
|---|---|---|---|---|
| 古文 | HsKobun | 107 | 15 | 41 |
| 漢文 | HsKanbun | 64 | 15 | 34 |
| 英語 | HsEnglish | 74 | 16 | 46 |
| 化学 | HsChemistry | 92 | 16 | 73 |
| 物理 | HsPhysics | 72 | 17 | 62 |
| 生物 | HsBiology | 72 | 18 | 69 |
| 地理 | HsGeography | 63 | 17 | 66 |
| 歴史 | HsHistory | 72 | 22 | 77 |
| 政治 | HsPolitics | 41 | 17 | 56 |
| 経済 | HsEconomics | 43 | 18 | 60 |
| 倫理 | HsEthics | 48 | 19 | 62 |
| 情報 | HsInformatics | 47 | 20 | 58 |
| 法 | HsLaw | 53 | 18 | 71 |
| ノンジャンル | HsMisc | 64 | 22 | 81 |
| **計** | | **約912** | **250** | **856** |

各教科に 思考3・タグ・Wiki単元マップ1・白板1（5〜6ハブ）を同梱。

## 4. 連携
- `PersonalEncyclopediaApp.seedHighSchoolIfNeeded()` … Phase A、数学の直後。センチネル未存在なら件数ゲート無しで一度だけ投入。
- `DashboardViewModel.seedInitialData()` … `r4 = InitialDataHighSchool.seedAppend(...)`。メッセージの接続/クイズ件数に合算。

## 5. 未検証・注意
- ビルド未実行。特に `InitialDataHighSchool.kt` 内の DAO メソッド名と `WhiteboardDao`/`WikiArticleDao` の insert 呼び出しを最初に確認。
- 全教科一括で約900件の定義が起動時に入るため、初回起動の Phase A が数秒延びる想定。遅い場合は `seedHighSchoolIfNeeded` を Dashboard ボタンのみに退避する。
- 埋め込み再生成は `EmbeddingQueue` の差分処理に任せている。
