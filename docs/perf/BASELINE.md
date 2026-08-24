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

`util/Timed.kt`の`timed()`ラッパー(NextTasks.md §0参照)を該当箇所に仕込み、`adb logcat -s App`で確認する。1機能1ビルドの原則に従い、計測したい箇所ごとに小さく追加してよい(計測用コードは`if (BuildConfig.DEBUG)`等で本番ビルドに影響しないようにする)。

### DBサイズ

```powershell
adb shell run-as com.thuvstu.personalencyclopedia du -h databases/
```

結果の出力先: すべてコンソール出力+`adb logcat`。ファイル転送・追加のGradleタスクは不要。

## 4. 記録表

### 端末情報

| 項目 | 値 |
|---|---|
| 端末名 | |
| Android version | |
| RAM / SoC | |
| 計測日 | |

### Startup (cold start, ms、`am start -W`のTotalTime、3〜5回中央値)

| データ規模 | TotalTime(中央値) | 初回起動(rebuildあり) |
|---|---|---|
| 1,000 | | |
| 10,000 | | |
| 50,000 | | |

### スクロール(ダッシュボード一覧、`dumpsys gfxinfo`)

| データ規模 | Janky frames | 総フレーム数 | jank率 |
|---|---|---|---|
| 1,000 | | | |
| 10,000 | | | |
| 50,000 | | | |

### 個別処理時間(`timed()`ラッパー、ms)

| データ規模 | 検索応答(hybridSearch) | InMemoryVectorIndex.load() |
|---|---|---|
| 1,000 | | |
| 10,000 | | |
| 50,000 | | |

### 補足

| 項目 | 1,000 | 10,000 | 50,000 |
|---|---|---|---|
| DBファイルサイズ | | | |
| アプリRAM使用量(`adb shell dumpsys meminfo`) | | | |

## 5. 運用ルール

- 各Round終了後、同手順で50,000件の再計測を行い、この表の差分をwalkthroughに残す
- 数値が悪化した場合は理由を切り分けず次に進まない(§4実行時プロトコル)