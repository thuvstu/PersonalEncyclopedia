# パフォーマンス・ベースライン計測手順と記録 (Round 0 / M-3)

**作成日:** 2026-08-22
**目的:** 50,000 entry相当の合成データでの実測値を記録し、以降の全Round(PERF-1〜8)の評価基準にする。
NextTasks.md §3 Round 0 の成果物。以降のRoundは「この数値がどう変わったか」で評価する。

---

## 1. 前提

- 対象端末: **自端末(高スペック)**。2026-08-26計測時の実端末は `25053PC47G` (Xiaomi, SoC SM8735/QTI, arm64-v8a, MemTotal 11.5GB + Swap 12GB)
- 計測条件: USB給電あり(AC false, USB true), バッテリー30%前後, 画面ON固定推奨
- シードは **debugビルド** に対して実行する(合成データ生成はdebug専用, `app/src/debug/` の PerfSeedReceiver)
- 軽量計測方式(2026-08-24改訂)で実施。`:benchmark` モジュールは撤去済み

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

## 3. 計測実行(2026-08-24改訂: 軽量計測方式)

Macrobenchmarkは個人開発には複雑すぎると判断し撤回。以下のadb標準機能+簡易ラッパーで代替する。コード追加はほぼゼロ、Gradle Managed Deviceも不要。

### コールドスタート

```powershell
# アプリを完全に停止してから実行(force-stopしないとコールドにならない)
adb shell am force-stop com.thuvstu.personalencyclopedia
adb shell am start -W -n com.thuvstu.personalencyclopedia/.MainActivity
```
`TotalTime`(体感起動時間)・`WaitTime`が出力される。ばらつきを見るため3〜5回実行し、中央値を記録する。

### スクロールのジャンク(フレーム落ち)

```powershell
adb shell dumpsys gfxinfo com.thuvstu.personalencyclopedia reset
# ここでダッシュボード一覧を実際にフリングスクロールする(手動操作)
adb shell dumpsys gfxinfo com.thuvstu.personalencyclopedia
```
`Janky frames`の数・割合が出力される。

### 検索応答時間・InMemoryVectorIndex.load()等の個別処理

`util/Timed.kt`の`timed()`ラッパーを `PersonalEncyclopediaApp.initBrainLayer()` の `vectorIndex.load()` と
`SearchRepository.search()` の `hybridSearch.search()` に仕込み済み。`adb logcat -s App` および
debug専用 `PerfSeedReceiver.SEARCH` ブロードキャスト(`adb shell am broadcast ... -a ...perf.SEARCH --es query bench`)で
3クエリ(歴史/細胞分裂/量子もつれ)の応答時間を計測する(FTS+RRF経路, semanticはGemini未設定のためスキップ)。

### DBサイズ

```powershell
adb shell run-as com.thuvstu.personalencyclopedia du -h databases/
```

結果の出力先: すべてコンソール出力+`adb logcat`。ファイル転送・追加のGradleタスクは不要。

## 4. 記録表 — 2026-08-26 実測値(軽量計測方式)

> 計測者: 実機 25053PC47G で手動実施。`SyntheticDataSeeder` は `seed(count)` で既存合成データを全削除→投入するため各規模は独立。

### 端末情報

| 項目 | 値 |
|---|---|
| 端末名 | 25053PC47G (Xiaomi) |
| Android version | 16 (SDK 36) |
| SoC / ABI | SM8735 (QTI) / arm64-v8a |
| RAM | MemTotal 11,502,936 kB (≈11.5GB) + Swap 12GB (cat /proc/meminfo) |
| 計測日 | 2026-08-26 23:20〜23:42 JST |
| ビルド | debug (`:app:installDebug`, commit 94a6692時点) |
| 条件 | USB給電, バッテリー30%前後, 手動軽量計測(adb) |

### Startup (cold start, ms、`am start -W` の TotalTime、5回実行の中央値)

> `adb shell am force-stop` → `am start -W -n com.thuvstu.personalencyclopedia/.MainActivity` を5回繰り返し。
> 初回起動(rebuildあり)はseed直後の1回目、2回目以降は同一値のため中央値は後者を採用。

| データ規模 | TotalTime(中央値) | 初回起動(rebuildあり) | 備考 |
|---|---|---|---|
| 0 (empty) | — | — | load 2ms, DB 1M |
| 1,000 | **798** (771/792/798/804/841) | 841 |  |
| 10,000 | **767** (760/765/767/769/774) | 769 | 中央値が1kより小さいのは誤差範囲 |
| 50,000 | **767** (753/759/778) ※ | 807 | ※largeHeap無しではOOM。largeHeap=trueで **load 9.8s** で起動可能(下記§5参照)。TotalTimeはWALで780ms前後 |

### スクロール(ダッシュボード一覧、`dumpsys gfxinfo`)

> 軽量計測の自動swipe( `adb shell input swipe 640 1800 640 500 250` ×3 )ではフレーム数が10〜16と少なすぎて
> jank率が不安定(43〜50%)になるため参考値。手動フリングでの再計測が推奨。

| データ規模 | Janky frames | 総フレーム数 | jank率 | 50th/90th | 備考 |
|---|---|---|---|---|---|
| 1,000 | 7 | 16 | 43.7% | 150ms/200ms | 自動swipe, サンプル少 |
| 1,000 タブ切替(10回) | 1 | 758 | 0.13% | 7ms/9ms | `timed(Nav)` 3-8ms, レンダリングは滑らか |
| 10,000 タブ切替(8回) | 1 | 708 | 0.14% | 7ms/8ms | 同上、データ増でもJankほぼ無し |
| 10,000 | — | — | — | — | 未計測(手動要) |
| 50,000 タブ切替 | — | — | — | — | largeHeapで起動可能だが未計測 |
| (参考)largeHeap無し50k | — | — | — | — | クラッシュのため計測不可 |
| (参考)1k手動前 | 12 | 205 | 5.85% | 17ms/73ms | `gfxinfo` リセット前の自然操作 |

### ウィンドウ切替(`timed("Nav")`, ms) — 2026-08-26追加

> `MainActivity` ボトムタブ5件と `NavGraph` の entry詳細遷移に `timed("Nav", ...)` を仕込み。`adb logcat -s Nav` で確認。
> `adb shell input tap x y` で自動タップし5〜8回切替の中央値を取る。

| 遷移 | 1,000件 | 10,000件 | 50,000件 | 備考 |
|---|---|---|---|---|
| tab:dashboard | 5ms | 4-6ms | — | `navController.navigate` 呼び出し自体の所要時間 |
| tab:search | 8ms | 5-8ms | — | 同上 |
| tab:srs_review | 3ms | — | — |  |
| tab:quiz | 3ms | — | — |  |
| tab:stats | 6ms | — | — |  |
| entry:detail | 5ms | 5ms | OOM | `entry/$id` 遷移 |

> **所感:** `navigate()` 自体は全て5ms前後で高速。体感の「遅さ」は `NavHost` の `fadeIn 120ms / fadeOut 90ms` アニメーションと、
> 遷移先画面の初期化(例: `EntryDetailScreen` のDBロード) が支配的。`dumpsys gfxinfo` でもタブ切替は 0.13% jank と滑らかで、
> フレーム落ちではなくアニメーション時間が「のっそり感」の原因と推定。短縮(例: 80/60ms)で体感改善が見込める。

### 個別処理時間(`timed()`ラッパー、ms)

> `App` タグ(`adb logcat -s App`) + `PerfSearch` ( `...perf.SEARCH --es query bench` で 歴史/細胞分裂/量子もつれ の3クエリ)

| データ規模 | 検索応答 hybridSearch[歴史] | hybridSearch[細胞分裂] | hybridSearch[量子もつれ] | InMemoryVectorIndex.load() (中央値) |
|---|---|---|---|---|
| 0 | — | — | — | **2** |
| 1,000 | **73** (86→73再測) | **36** | **37** | **443** (400/412/443/462/463) |
| 10,000 | **79** | **77** | **74** | **911** (873/874/911/913/940) ※以前894も同様 |
| 50,000 | **71** | **66** | **61** | **9837** (9531/9646/9837/9962) ※largeHeap=trueで計測。無しではOOM |

### 補足

| 項目 | 0 | 1,000 | 10,000 | 50,000 |
|---|---|---|---|---|
| DBファイルサイズ (`du -h` / `ls -lh encyclopedia.db`) | 1M (652K+32K+406K) | **16M** (15M+32K+512K) | **131M** (131M+64K+512K) | **623M** (623M+64K+27M) ※656M total |
| アプリRAM使用量(`dumpsys meminfo` TOTAL Pss) | 201M | **216M** | **249M** | **553M** (largeHeap, Heap 302M/Alloc188M) / OOM without largeHeap |
| Heap Size / Alloc | 68M / 35M | 81M / 37M | 178M / 66M | 302M/188M (largeHeap) |
| seed所要時間 | — | 0.9s | 7.6s | 63s (WAL+largeHeap時はやや遅延) |
| 計測時の挙動 | 正常 | 正常 | 正常 | **largeHeapで9.8s loadで起動**(無しではクラッシュ, §5参照) |

## 5. 50,000件でのクラッシュ詳細と largeHeap 暫定対応(PERF-8検証結果)

**現象(largeHeap無し):** 50k投入後、コールドスタートで `TotalTime` は 780ms前後で出力されるが、約10秒後に
`InMemoryVectorIndex.load()` 中に `OutOfMemoryError` でクラッシュ。

```
Failed to allocate a 3088 byte allocation with 588624 free bytes and 574KB until OOM,
target footprint 268435456, growth limit 268435456
  at com.thuvstu.personalencyclopedia.brain.ai.GeminiClientKt.toFloatArray(GeminiClient.kt:131)
  at com.thuvstu.personalencyclopedia.brain.search.InMemoryVectorIndex.load(InMemoryVectorIndex.kt:44)
```

**原因:** `embeddingDao.getAll()` で全件の `vectorBlob` (768dim×4B=3KB/件) を一括ロードし、
さらに `ByteArray→FloatArray` 変換で一時的に二重に保持。50kでは 147MB×2 + オーバーヘッドで
heap上限256MBを超過。`DESIGN.md §7.1.5` の想定「数万件までブルートフォースで実用」は **10kまでは成立、50kで破綻**。

**暫定対応(2026-08-27):** `AndroidManifest` に `android:largeHeap="true"` を追加。
heap上限が512MBに緩和され、50kでも起動可能になった。実測: `load 9531/9646/9837/9962ms` (中央値 **9.8s**)、
searchは 71/66/61ms と高速を維持、Pss 553M / Heap 302M。TotalTimeは780ms前後で変わらず(バックグラウンドロード)。

**本命対応(NextTasks.md Round 5):**
- `sqlite-vec` 拡張への移行(§15既存の拡張ポイント)でon-diskベクトル検索化。`InMemoryVectorIndex` を
  遅延/ページングロードするか、クエリ時にDB側で近傍検索する方式へ置換
- 代替: FTSのみにフォールバックするモード(セマンティック無効時の hybridSearch はFTS+RRFのみで十分高速 70ms前後)

**再現手順:** `adb shell am broadcast ... --ei count 50000` → `am force-stop; am start -W` → largeHeap無しでは10秒後にクラッシュ、
largeHeapありでは9.8sでロード完了。復旧は `adb shell run-as ... rm databases/encyclopedia.db*` または `pm clear`。

**影響:** largeHeapで50kのクラッシュは回避したが **9.8sの起動時ロードは実用外**。Round 5のsqlite-vec移行が最優先のまま。

## 6. 運用ルール

- 各Round終了後、同手順で50,000件の再計測を行い、この表の差分をwalkthroughに残す
- 数値が悪化した場合は理由を切り分けず次に進まない(§4実行時プロトコル)