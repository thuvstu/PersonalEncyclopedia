# パフォーマンス・ベースライン計測手順と記録 (Round 0 / M-3)

**作成日:** 2026-08-22
**目的:** 50,000 entry相当の合成データでの実測値を記録し、以降の全Round(PERF-1〜8)の評価基準にする。
NextTasks.md §3 Round 0 の成果物。以降のRoundは「この数値がどう変わったか」で評価する。

---

## 1. 前提

- 対象端末: **自端末(高スペック)**。エミュレータで代替する場合は `:benchmark` に定義済みの `pixel8Api34`(ATDイメージ)を使う
- Macrobenchmarkは対象アプリの **release ビルドを自動インストール**して計測する(debugでは測らない)
- 計計測条件を揃える: 充電器接続・画面ON固定・バックグラウンドアプリ最小化
- シードは **debugビルド** に対して実行する(合成データ生成はdebug専用)

## 2. 合成データの投入 (M-1: SyntheticDataSeeder)

```powershell
# debugビルドをインストールし、一度起動して初期化を完了させておく
.\gradlew.bat :app:installDebug
```

投入(段階的に 1,000 → 10,000 → 50,000。最終確認は必ず50,000):

```powershell
adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 1000
adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 10000
adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 50000
```

- **各SEED呼び出しは既存の合成データを全削除してから投入する**(合成データは常にちょうど`count`件になる)。
  段階計測の際にCLEARを挟む必要はない。ユーザーの実データ・デモデータには触れない
- 進捗: `adb logcat -s SyntheticSeeder` (完了時にToast表示)
- 全削除のみ: `... -a com.thuvstu.personalencyclopedia.perf.CLEAR`
- 投入内容: entry / entry_definition / entry_thought / entry_webpage / entry_book /
  search_document + FTS(Nグラム) / embedding(768次元の疑似単位ベクトル, model=`synthetic-768`)
- 同一countは常に同一データ(Randomシード固定)。計測は再現可能

**注意:** seed直後の初回起動では Phase B の `rebuildAllSearchDocuments()` が全件走査する。
「初回起動(再構築あり)」と「2回目以降」は別の数値として記録すること。

## 3. ベンチマーク実行 (M-2: :benchmarkモジュール)

```powershell
# 物理端末(接続した自端末)の場合
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest --console=plain

# 管理エミュレータの場合
.\gradlew.bat :benchmark:pixel8Api34BenchmarkAndroidTest --console=plain
```

| テスト | 計測内容 | メトリクス |
|---|---|---|
| `StartupBenchmark#coldStartup` | cold start | StartupTimingMetric |
| `NavigationBenchmark#navigateBottomTabs` | 検索→統計→ホーム 遷移 | FrameTimingMetric |
| `ScrollBenchmark#scrollDashboardList` | ダッシュボード一覧フリング×3 | FrameTimingMetric |
| `SearchBenchmark#searchQueryResponse` | 検索画面でのクエリ入力→結果再描画 | FrameTimingMetric |

結果の出力先: 実行コンソール(Studio) / `benchmark/build/outputs/managed_device_results/`(管理エミュレータ)

補助観測(任意だが推奨):
- `InMemoryVectorIndex.load()` の所要時間 → 起動時logcat(Appタグ)
- DBサイズ → `adb shell run-as com.thuvstu.personalencyclopedia ls -l databases/`(debugビルドのみ可)

## 4. 記録表

### 端末情報

| 項目 | 値 |
|---|---|
| 端末名 | |
| Android version | |
| RAM / SoC | |
| 計測日 | |

### Startup (cold start, ms)

| データ規模 | P50 | P90 | P99 | 初回起動(rebuildあり) |
|---|---|---|---|---|
| 1,000 | | | | |
| 10,000 | | | | |
| 50,000 | | | | |

### Frame timing — 画面遷移 (frameDurationMs)

| データ規模 | P50 | P90 | P99 | jank率(>16ms) |
|---|---|---|---|---|
| 1,000 | | | | |
| 10,000 | | | | |
| 50,000 | | | | |

### Frame timing — 一覧スクロール

| データ規模 | P50 | P90 | P99 | jank率 |
|---|---|---|---|---|
| 1,000 | | | | |
| 10,000 | | | | |
| 50,000 | | | | |

### Frame timing — 検索応答

| データ規模 | P50 | P90 | P99 | jank率 |
|---|---|---|---|---|
| 1,000 | | | | |
| 10,000 | | | | |
| 50,000 | | | | |

### 補足

| 項目 | 1,000 | 10,000 | 50,000 |
|---|---|---|---|
| InMemoryVectorIndex.load() 所要時間 | | | |
| DBファイルサイズ | | | |
| FTSインデックスサイズ(search_document_fts) | | | |
| アプリRAM使用量(VectorIndex含む) | | | |

## 5. 運用ルール

- 各Round終了後、同手順で50,000件の再計測を行い、この表の差分をwalkthroughに残す
- 数値が悪化した場合は理由を切り分けず次に進まない(§4実行時プロトコル)
