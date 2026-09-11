# walkthrough61 — ビルド復旧(wt56〜59のコンパイル破壊・6ファイル)

## 0. 背景
- 強化計画第3版 §1.4/§5 Phase -1 の指摘どおり、現HEADは `compileDebugKotlin` が通らない状態だった。
- 実機(25053PC47G)接続済み・JDK/Android SDKありの環境で `./gradlew` を実行し、エラーを切り分けて最小修正した。
- wt47以来はじめて「コンパイル→APK→実機起動→FATAL 0」まで一本通したHEAD。

## 1. ビルド
- `compileDebugKotlin`: 最初は3ファイル、その後に3ファイルの計6ファイルで失敗 → 修正後は `BUILD SUCCESSFUL`
- `assembleDebug`: `BUILD SUCCESSFUL` (app-debug.apk 約107MB)
- 実機: `adb install -r` 成功 → `am start -W` で MainActivity が COLD起動 (TotalTime 916ms)
- `adb logcat` で FATAL 0件、プロセス生存(pid確認)・当パッケージの Exception/Error 0件

## 2. 修正内容(6ファイル・いずれも構文/初期化順のみ、機能変更なし)

| ファイル | 原因 | 修正 |
|---|---|---|
| `viewmodel/DashboardViewModel.kt` | wt56付箋追加でクラスを早期閉じする迷子の `}` (145行目)。以降の `softDelete` 等がクラス外に落ち `db`/`searchRepo` 未解決に | 迷子の `}` 1行を削除 |
| `viewmodel/EntryDetailViewModel.kt` | 同上 (201行目)。`entryQuizzes` 等がクラス外に落ち `quizRepo`/`entryId`/`viewModelScope` 未解決に | 迷子の `}` 1行を削除 |
| `viewmodel/SearchViewModel.kt` | 同上 (末尾の余分な `}`)。`177:1 Syntax error` | 余分な `}` 1行を削除 |
| `db/HsDeepAncient.kt` | `val seed` の初期化子が後方宣言の `LECTURE_0/INTRO/1` を参照し `must be initialized` | `val seed` → `val seed by lazy { ... }` (参照を使う側=InitialDataHighSchool.kt は `HsDeepAncient.seed` のまま動く) |
| `db/HsDeepEgypt.kt` | 同上 (`LECTURE_2`) | 同上 |
| `db/HsStickies.kt` | ブロックコメント内の `InitialData*/DemoData` の `*/` がコメントを誤って閉じていた (206行目付近の大量 syntax error の正体) | `InitialData・DemoData` に修正 |
| `app/schemas/.../AppDatabase/12.json` | 上記DB関連コードでビルドした際の Room スキーマ出力(v12・新規) | 生成物をそのまま同梱(従来どおり追跡する) |

## 3. 環境メモ(次回以降のため)
- Temurin JDK17(`.jdks/OpenJDK17U-...`)は `lib/tzdb.dat` が欠落した不完全展開で、`assembleDebug` の D8 段階で `FileNotFoundException: tzdb.dat` になる。**ビルド用 JAVA_HOME は JDK24(`.jdks/OpenJDK24U-.../jdk-24.0.1+9`、tzdb.dat あり)** を使用。Gradle 9.5 + AGP 9.3.1 は JDK24 ランタイムで動作確認済み。
- Kotlin daemon のキャッシュロック(`class-attributes.tab` が他プロセス使用中)が出たら `./gradlew --stop` 後に `app/build/kotlin/compileDebugKotlin` を削除して再実行。
- `compileDebugKotlin` 単体は Temurin17でも通るが、APKまで行くなら最初から JDK24 にすること。

## 4. 残課題(本セッションでは触らない)
- 主要画面(白板/Wiki/EntryDetail/ToDo通知/付箋)の手触り確認は未実施。実機があるうちに手で一通り触って `adb logcat` を見ること(P-1-4の残り)。
- `testDebugUnitTest` は未実行。
- Phase 0(ドキュメント監査: DESIGN.md/mismatch.md/強化計画.md の35本分陳腐化)は別セッションで着手すること。本セッションはビルド復旧のみ。

## 5. Git
- 修正6ファイル + schema 12.json + 本walkthrough を1コミットで構成(論理1機能=ビルド復旧のため)。
