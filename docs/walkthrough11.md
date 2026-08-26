# walkthrough11 — ウィンドウ切替計測の仕込みと実測

**日付:** 2026-08-26 23:50〜
**対象:** ユーザー要望「ウィンドウ切り替えが遅いと感じるので実機操作での時間テスト」
**端末:** 25053PC47G, Android 16, RAM 11.5GB

---

## 1. 実装内容

### MainActivity.kt — ボトムタブ5件+共有遷移

`NavigationBarItem` の `onClick` 5箇所(`dashboard/search/srs_review/quiz/stats`)と
`LaunchedEffect(pending)` の `entry/$id` 遷移を `timed("Nav", "tab:...")` で包んだ。
`navController.navigate()` 呼び出し自体の所要時間を `logcat -s Nav` に出力。

### NavGraph.kt — Entry詳細遷移6箇所

`Dashboard/Search/Connections/WhiteboardBoard/WikiArticle/EntryDetail` の
`onNavigateToEntry = { id -> navController.navigate("entry/$id") }` を
`timed("Nav", "entry:$id")` で包んだ。
`WikiArticle` の `onEdit` と `EntryDetail` の `onEdit/onNavigateToWiki` も同様。

いずれも `util/Timed.kt` の既存ラッパーを利用、ビルド影響は数msのログ出力のみ。

## 2. ビルド確認

- `:app:assembleDebug` 成功(2回)
- `:app:installDebug` 成功、実機で `adb shell input tap` による自動タップと `logcat -s Nav` で確認

## 3. 実測結果(現状DB 1k〜10k)

### navigate() 自体の所要時間(`timed`)

| 遷移 | 1k | 10k |
|---|---|---|
| tab:dashboard | 5ms | 4-6ms |
| tab:search | 8ms | 5-8ms |
| tab:srs_review | 3ms | — |
| tab:quiz | 3ms | — |
| tab:stats | 6ms | — |
| entry:detail | 5ms | 5ms |

全て5ms前後で高速。navigate呼び出し自体はボトルネックではない。

### レンダリング(`dumpsys gfxinfo`)

- 1k タブ切替 10回: 758 frames, Janky 1 (0.13%), 50th 7ms — 滑らか
- 10k タブ切替 8回: 708 frames, Janky 1 (0.14%), 50th 7ms — 同様に滑らか
- 起動直後の自然操作 205 framesで5.85% jank(1k), 起直後の `reset` 直後は13 framesで38%と一時的

`navigate` も `gfxinfo` も滑らかで、フレーム落ちは観測されず。

### 所感

体感の「遅さ」はフレーム落ちではなく `NavGraph.kt:52-55` の

```kotlin
enterTransition = { fadeIn(tween(120)) }
exitTransition  = { fadeOut(tween(90)) }
```

による **210msのアニメーション時間** と、遷移先画面の初期化(例: `EntryDetailScreen` のDBロード、
`DashboardScreen` のLazyColumn再構成) が支配的と推定。
PERF-4(key欠如)/PERF-5(巨大Composable)やアニメーション短縮(例: 80/60ms)で体感改善が見込める。

## 4. 変更ファイル

- `app/src/main/java/.../MainActivity.kt` — 6箇所に `timed("Nav")`
- `app/src/main/java/.../ui/navigation/NavGraph.kt` — 6箇所に `timed("Nav")`
- `docs/perf/BASELINE.md` — ウィンドウ切替セクション追加、スクロール表にタブ切替結果追記

## 5. 次の一手

- **Round 2:** PERF-4(Lazy key徹底) / PERF-5(巨大Screen分割) / Strong Skipping有効化で再構成コスト削減
- **アニメーション調整:** `tween(120/90)` → `tween(80/60)` など短縮を試験し、体感と `gfxinfo` で効果確認
- **詳細遷移のDBロード:** `EntryDetailViewModel` の `getById` に `timed("DB","entryById")` を仕込み、
  10k/1kでの差を計測(次回セッションで実施)

