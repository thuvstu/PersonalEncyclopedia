# Personal Encyclopedia — パフォーマンス大改良計画

**作成日:** 2026-08-22
**方法:** 実リポジトリ(最新コミット`f259916`)を再クローンし実コードを監査。加えて2026年8月時点のAndroid/Compose/Room公式ドキュメント・実務記事を調査
**現状規模:** 209 Kotlinファイル・23,133行・DB v9(20テーブル超)・実機ビルド確認済み

---

## 0. 大前提: 計測してから最適化する(2026-08-24方針転換)

「測ってから最適化する」という方針自体は維持する。ただし**Macrobenchmarkは個人開発1人で回すには複雑すぎる(Gradle Managed Device・additional test outputの端末→ホストへの自動転送・profileable manifest等)と判断し、放棄する。** 複数セッションのトラブルシュートでも解決に至らなかったため、ツール選定そのものが誤りだったと結論づける。`:benchmark`モジュールは撤去し、以下の軽量計測に置き換える。

### 軽量計測ツールキット(コード追加ほぼゼロ)

| 目的 | 方法 |
|---|---|
| コールドスタート時間 | `adb shell am start -W -n com.thuvstu.personalencyclopedia/.MainActivity` → `TotalTime`/`WaitTime`がその場で出力される。ビルド・コード変更不要 |
| スクロールのジャンク(フレーム落ち) | `adb shell dumpsys gfxinfo com.thuvstu.personalencyclopedia reset` → 操作 → `adb shell dumpsys gfxinfo com.thuvstu.personalencyclopedia` でJanky frames数・パーセンタイルが取得できる。Android標準機能でライブラリ不要 |
| 検索・画面遷移などの個別処理時間 | `AppLogger`に計測用の薄いラッパーを1つ追加し、`adb logcat`で確認する(下記コード) |
| DBファイルサイズ(FTSインデックス膨張確認) | `adb shell run-as com.thuvstu.personalencyclopedia du -h databases/` |

```kotlin
// util/Timed.kt(新規、数行で完結)
inline fun <T> timed(tag: String, label: String, block: () -> T): T {
  val start = System.currentTimeMillis()
  return block().also { AppLogger.d(tag, "$label: ${System.currentTimeMillis() - start}ms") }
}
// 使用例: timed("Search", "hybridSearch") { searchEngine.hybridSearch(query) }
```

### 撤去・変更するもの

- [x] **`:benchmark`モジュールを削除** ✅ 実装済み(walkthrough9)。`settings.gradle.kts`・root `build.gradle.kts`・`libs.versions.toml`から参照除去、`benchmark/`ディレクトリ削除。release buildTypeのdebug署名とmanifestの`profileable`は軽量計測(R8有効APKの実機インストール/simpleperf)に有用なため残存
- [x] `app/build.gradle.kts`の`benchmark`ビルドタイプ関連設定があれば整理 ✅ 独立したbenchmarkビルドタイプは無し。R8/proguard-rules.proの変更はrelease build健全化として単独価値があるため残存
- [x] `docs/perf/BASELINE.md`を上記の軽量計測手順に書き換える ✅ 済み(2026-08-24)
- [x] `AGENTS.md`(現在0バイト)に実際の内容を再コミットする ✅ 済み

### 維持するもの(変更不要)

- **M-1: SyntheticDataSeeder**はそのまま活かす。Macrobenchmarkの前提として作ったが、軽量計測でも50,000件データの生成元として引き続き必要
- Round 1〜5のタスク内容自体は変更なし。計測方法だけが変わる

- [x] **M-1: 合成負荷データ生成スクリプト作成** ✅ 実装済み(walkthrough8)。そのまま使う
- [x] ~~M-2: Macrobenchmarkモジュール追加~~ **撤回。上記の軽量計測に置き換え**
- [ ] **M-3(改訂): 50,000件データでの実測値を軽量計測で記録する** `adb shell am start -W`でコールドスタート、`dumpsys gfxinfo`でスクロール、`timed()`ラッパーで検索応答時間を計測し、`docs/perf/BASELINE.md`(軽量版)に記入する
  🛑 ここで得た実測値をベースラインとし、以降のRoundは全て「この数値がどう変わったか」で評価する

**高スペック前提での方針調整**: WAL接続プール数やCoilのキャッシュサイズは、低スペック機を想定した保守的な値ではなく、自端末のRAM/CPUに見合った値まで踏み込んでよい(Round 1/3で具体値を決める際にこの前提を使う)。

---

## 1. 実コード監査結果(確認済みの具体的な問題)

### 🔴 DB層

| ID | 問題 | 確認内容 |
|---|---|---|
| PERF-1 | WAL/Executor設定が未明示 | `DatabaseModule.kt`は`Room.databaseBuilder(...).addMigrations(...).build()`のみ。WALはAPI16+/非低メモリ端末ではRoomのデフォルト(`AUTOMATIC`)で有効になる可能性が高いが、明示していないため確認できない。`setQueryExecutor`/`setTransactionExecutor`の分離も未設定 |
| PERF-2 | `progress_events.entityId`にインデックスが無い | `entityType`/`eventType`/`createdAt`は索引済みだが、「この特定entryの履歴を見る」に使う`entityId`が漏れている。無制限に増え続けるログテーブルのため、件数が増えるほど全件スキャンの影響が大きくなる |
| PERF-3 | 高頻度更新テーブルのInvalidationTracker負荷が未検証 | `embedding`/`search_document`はエントリー追加のたびに書き込まれる。RoomのFlow監視はテーブル粒度のため、これらを購読するUIが不必要に再クエリされていないか未検証 |

### 🟠 UI/Compose層

| ID | 問題 | 確認内容 |
|---|---|---|
| PERF-4 | `LazyColumn`/`LazyVerticalGrid`の`key`指定が不徹底 | 使用18箇所に対し`items(..., key = ...)`指定は11箇所のみ。残り箇所は並べ替え・挿入・削除時に不要な再作成/アニメーション崩れが起きうる |
| PERF-5 | 大規模Composable/ViewModelが残存 | `ToDoScreen.kt`(659行)・`EntryDetailScreen.kt`(533行)が突出。`EntryEditViewModel`は800行超から460行まで縮小済みだが、§11.9の分割方針は一部の画面にしか適用されていない |
| PERF-6 | Baseline Profileが存在しない | `app/src/`にプロファイル生成用のbenchmarkモジュールが無い。Compose 1.9+はViewsと同等のjank率まで来ているが、それは適切なBaseline Profileがあってこそ(初回起動が約30%高速化するという計測が2026年6月時点のAndroid公式ドキュメントにある) |

### 🟡 画像・メモリ層

| ID | 問題 | 確認内容 |
|---|---|---|
| PERF-7 | Coil等の画像読み込みライブラリが未導入 | `gradle/libs.versions.toml`にCoil等の記載なし。`AttachmentSection.kt`が`BitmapFactory.decodeFile`/`decodeStream`を直接使用しており、表示サイズに関わらず原寸大でデコードしている(サムネイル一覧表示でのメモリ・速度両面のリスク) |

### 🟢 検索・Embedding層(設計通りだが検証が必要)

| ID | 問題 | 確認内容 |
|---|---|---|
| PERF-8 | 実データ規模での検証が皆無 | `InMemoryVectorIndex`のブルートフォース方式(§7.1.5で「数万件規模まで実用速度」と想定)、FTS4+Nグラムのストレージ膨張(bigram化で約2倍)ともに、実測されたことがない。デモデータがentry数件のみのため顕在化していない |

---

## 2. 2026年時点のベストプラクティス調査(反映済み)

- **Baseline Profiles**: 初回起動コードを事前コンパイルし、体感で約30%起動を高速化(Android公式、2026年6月時点)。Play Storeのクラウドコンパイルにより端末側コンパイル前でも効果が及ぶ
- ~~**Macrobenchmark → 最適化 → Baseline Profile再生成 → CIで固定**、という運用ループが2026年の定番(複数の実務記事で一致)~~ **2026-08-24: 個人開発では複雑すぎると判断し撤回。§0の軽量計測に置き換え済み**
- **WAL + synchronous=NORMAL + Executor分離**: ある実測記事ではWAL単体で4倍の改善。ただし「WALは実際に読み書きが競合する場面でしか効かない」「コネクション数を増やしすぎない(低スペック機で先に検証)」という留保が付く
- **RoomのInvalidationTrackerはテーブル粒度**: 更新頻度の高いテーブルを購読するUIが多いと過剰再クエリの原因になる。手動の変更検知の方が有利な場合がある、との指摘あり
- **Strong Skipping mode**: Compose Compilerの安定化オプション。ラムダの安定性判定が緩和され、開発者が`@Stable`を意識しすぎなくても再コンポジションを抑制しやすくなる(2026年時点で実務チームの標準装備という評価)
- **R8**: 本プロジェクトは既に`isMinifyEnabled = true`済み。追加調査は不要

---

## 3. タスクリスト(Round形式、実装原則§2.5準拠)

### Round 0 — 計測基盤(内容は§0参照。ここでは進捗のみ管理)

**確定条件(2026-08-22)**: 最適化対象は自端末/高スペック端末。低スペック機への配慮は優先度を下げてよい。目標データ規模は**50,000 entry**。

- [x] M-1: SyntheticDataSeeder ✅ 実装済み(walkthrough8)。§0「維持するもの」参照
- [x] ~~M-2: Macrobenchmarkモジュール~~ 撤回済み(2026-08-24)。§0「軽量計測ツールキット」に置き換え
- [ ] M-3(改訂): 50,000件データでの軽量計測実測値の記録。§0参照、次セッションで実施

### Round 1 — DB層(低リスク・高確度)

- [ ] **PERF-1** WAL明示化(`setJournalMode(WRITE_AHEAD_LOGGING)`)+`PRAGMA synchronous=NORMAL`+query/transaction Executor分離
- [ ] **PERF-2** `progress_events`へのインデックス追加(v9→v10マイグレーション。§14.1ガイドにも追記)
- [ ] 主要DAOクエリに対し`EXPLAIN QUERY PLAN`を当て、`SCAN TABLE`になっている箇所を洗い出す(M-1の1万件データで実施しないと意味がない)
  🛑 M-2のベンチマークで検索/一覧表示クエリの所要時間がRound 0比でどう変わったか確認

### Round 2 — UI/Compose層

- [ ] **PERF-4** 全`items()`呼び出しに`key = { it.id }`を徹底
- [ ] **PERF-5** `ToDoScreen.kt`・`EntryDetailScreen.kt`の分割(§11.9方針の適用)
- [ ] Compose Compilerのstrong skipping設定を確認・有効化
  🛑 Layout Inspector/Vkompose等で不要な再コンポジションが減ったか確認

### Round 3 — 画像・メモリ

- [ ] **PERF-7** Coil導入、`AttachmentSection.kt`等の`BitmapFactory`直接デコードを置き換え
  🛑 サムネイル一覧表示のスクロール滑らかさを確認

### Round 4 — 起動最適化

- [ ] **PERF-6** Baseline Profile生成・導入。**注意: Macrobenchmarkモジュールは撤去済みのため、Round 0で使っていた前提が崩れている**。Baseline Profile生成は`androidx.benchmark.baselineprofile`プラグイン単体でも可能だが、同様にGradle Managed Device関連の複雑さを伴う可能性があるため、着手前に軽量な代替(`pm compile --reset`や手動プロファイル収集等)がないか改めて調査してから判断する。効果(初回起動30%高速化)は魅力的だが、Round 0の教訓(道具の複雑さが見合うか)を必ず踏まえる
- [ ] §3.4の段階的初期化(Phase A/B/C)の各所要時間を`timed()`ラッパー(§0参照)でログ化し、ボトルネックのフェーズを特定する
  🛑 cold start計測値(`adb shell am start -W`)がBaseline Profile導入前後でどう変わったか記録

### Round 5 — 検索・Embedding層(実データ規模での検証)

- [ ] **PERF-8** M-1の50,000件データで`timed()`ラッパー(§0)を`InMemoryVectorIndex.topK`呼び出し前後に仕込み、応答時間を実測。FTS4+Nグラムのインデックスサイズは`adb shell run-as ... du -h databases/`で確認する
- [ ] 実測結果が§7.1.5の想定(数万件までブルートフォースで実用速度)を下回った場合のみ、sqlite-vec拡張への移行(§15既存の拡張ポイント)を検討する。上回っていれば何もしない(過剰最適化を避ける)

---

## 4. 実行時プロトコル(2026-08-22確定・全Round共通)

既存の実装原則§2.5(1機能1ビルド・既存コードが唯一の正・依存関係を提示前に検証・エラー3ファイル基準)に加え、本改良では以下を徹底する。

- **ビルド確認のタイミング**: 1つの実装をひと息に終え、依存関係のエラーがすべて解消された状態になったら、その時点でビルドが通ることを必ず確認する。通らないまま次の実装に進まない
- **コミット**: 毎回コミットする(§2.5の「1ファイルごとに提示→ビルド確認→コミット」を厳守)
- **プッシュ**: Round(実装各層)ごとに一段落ついたタイミングでプッシュする。1コミットごとの都度プッシュではなく、Round単位でまとめる
- **walkthrough記録**: 各Round終了後、`docs/walkthrough8.md`以降(既存の`walkthrough1〜7.md`の続番)に何を実装したかを記録する
- **着手前にファイル・ドキュメントを実際に読む**: このタスクリスト(`NextTasks.md`)と、関連する既存コードを読んでから着手する。読まずに推測で書き始めない(実装原則§2.5原則3の実践)
- **ドキュメント更新**: 各Round終了後、`DESIGN.md`(実装を正として書かれた解説書、現在`83b4c00`時点で停止している)と`docs/guide/`配下の該当ファイルを、実装後の実態に合わせて更新する。「作ったが誰も読めるドキュメントが無い」状態を作らない