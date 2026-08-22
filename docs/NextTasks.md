# Personal Encyclopedia — パフォーマンス大改良計画

**作成日:** 2026-08-22
**方法:** 実リポジトリ(最新コミット`f259916`)を再クローンし実コードを監査。加えて2026年8月時点のAndroid/Compose/Room公式ドキュメント・実務記事を調査
**現状規模:** 209 Kotlinファイル・23,133行・DB v9(20テーブル超)・実機ビルド確認済み

---

## 0. 大前提: 計測してから最適化する

調査した最新記事群が繰り返し強調していたのは「Macrobenchmarkが真実を示す。Baseline Profileがそれを固定する」という順序です。現状、デモデータはentry数件程度しかなく、**実データ規模(何十巻もの百科事典=数万entry相当)での挙動が一度も計測されていません**。勘での最適化は避け、まず負荷を再現してから直すべきです。これが今回のRound 0になります。

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
- **Macrobenchmark → 最適化 → Baseline Profile再生成 → CIで固定**、という運用ループが2026年の定番(複数の実務記事で一致)
- **WAL + synchronous=NORMAL + Executor分離**: ある実測記事ではWAL単体で4倍の改善。ただし「WALは実際に読み書きが競合する場面でしか効かない」「コネクション数を増やしすぎない(低スペック機で先に検証)」という留保が付く
- **RoomのInvalidationTrackerはテーブル粒度**: 更新頻度の高いテーブルを購読するUIが多いと過剰再クエリの原因になる。手動の変更検知の方が有利な場合がある、との指摘あり
- **Strong Skipping mode**: Compose Compilerの安定化オプション。ラムダの安定性判定が緩和され、開発者が`@Stable`を意識しすぎなくても再コンポジションを抑制しやすくなる(2026年時点で実務チームの標準装備という評価)
- **R8**: 本プロジェクトは既に`isMinifyEnabled = true`済み。追加調査は不要

---

## 3. タスクリスト(Round形式、実装原則§2.5準拠)

### Round 0 — 計測基盤(最優先。これがないと以降が勘に頼った改良になる)

**確定条件(2026-08-22)**: 最適化対象は自端末/高スペック端末。低スペック機への配慮は優先度を下げてよい。目標データ規模は**50,000 entry**。

- [ ] **M-1: 合成負荷データ生成スクリプト作成** `debug`ビルドのみで有効な`SyntheticDataSeeder`を追加し、1,000→10,000→**50,000**件と段階的に投入できるようにする(最終確認は必ず50,000件で行う。中間段階は問題の早期発見用)
- [ ] **M-2: Macrobenchmarkモジュール追加** cold start・主要画面遷移・検索応答時間を計測できるようにする
- [ ] **M-3: 50,000件データでの実測値を記録する**(このRoundの成果物は「直す」ことではなく「今どれだけ遅いか/速いかを数字で持つ」こと)
  🛑 ここで得た実測値をベースラインとし、以降のRoundは全て「この数値がどう変わったか」で評価する

**高スペック前提での方針調整**: WAL接続プール数やCoilのキャッシュサイズは、低スペック機を想定した保守的な値ではなく、自端末のRAM/CPUに見合った値まで踏み込んでよい(Round 1/3で具体値を決める際にこの前提を使う)。

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

- [ ] **PERF-6** Baseline Profile生成・導入(Round 0のMacrobenchmarkモジュールを流用)
- [ ] §3.4の段階的初期化(Phase A/B/C)の各所要時間をログ化し、ボトルネックのフェーズを特定する
  🛑 cold start計測値がBaseline Profile導入前後でどう変わったか記録

### Round 5 — 検索・Embedding層(実データ規模での検証)

- [ ] **PERF-8** M-1の1万件データで`InMemoryVectorIndex`のtopK応答時間、FTS4+Nグラムのインデックスサイズ・検索速度を実測
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
