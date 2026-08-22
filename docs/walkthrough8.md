Personal Encyclopedia — パフォーマンス改良 Round 0: 計測基盤 (walkthrough8)
master(6efbba2直前のf259916)上で、docs/NextTasks.md のRound 0(M-1/M-2/M-3)を実装しました。
対象: M-1(合成負荷データ生成) / M-2(Macrobenchmarkモジュール) / M-3(計測手順と記録テンプレート)
方針どおり、このRoundでは「直す」のではなく「今どれだけ速いか/遅いかを数字で持つ」ための土台だけを作った。

方針
- 着手前に NextTasks.md と関連実コード(DatabaseModule / AppDatabase / EntryDao / SearchDocumentDao /
  EmbeddingDao / EmbeddingQueue / InMemoryVectorIndex / EmbeddingTextBuilder / MainActivity / NavGraph)を読んでから着手
- 合成データは debug ソースセットに隔離し、release APKには一切含めない
- 本番パスとの差分を生まないため、search_document/FTS生成は本番と同じ
  EmbeddingTextBuilder.build() + NgramTokenizer.tokenize() を使用する

実施内容

0. 前提修正 — assembleReleaseの復旧
- 既存コミット(f259916)で :app:assembleRelease が R8 missing class エラーで失敗していることを確認
  (tink/netty/rhino/log4j等がコンパイル時のみ参照する省略可能クラス。本変更より以前から存在)
- AGP生成の missing_rules.txt を app/proguard-rules.pro へ取り込んで修復
  (Macrobenchmarkはrelease APKをインストールするため、releaseビルドが通ることはRound 0の前提)

A. M-1: SyntheticDataSeeder (debug専用)
- app/src/debug/java/com/thuvstu/personalencyclopedia/perf/SyntheticDataSeeder.kt
  - 1,000 / 10,000 / 50,000件を段階的投入(BATCH_SIZE=2,000件ごとにwithTransactionで一括コミット)
  - type比率: definition50% / thought25% / webpage15% / book10%。webpage/bookは拡張テーブル行も作成
  - 全行を metadataJson={"synthetic":true} でマーク(ユーザーの実データ・デモデータには触れない)
  - embeddingは768次元(GeminiClient.embed の outputDimensionality=768 に一致)の疑似単位ベクトル。
    model="synthetic-768" で本番embeddingと識別可能。InMemoryVectorIndex.load()/topK を実データ規模にできる
  - Randomシード=count固定 → 同一countは常に同一データ(再現性)
- PerfSeedReceiver(debug専用BroadcastReceiver, @AndroidEntryPoint):
  - adb broadcast で SEED(count extra)/CLEAR を受ける。投入はプロセス生存中のコルーチンへ委譲
    (50k件はブロードキャスト制限時間内に終わらないため)
  - 実行例:
    adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver \
      -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 50000
- DAO追加(いずれも既存クエリに影響しない追加のみ):
  - EntryDao: insertAll / countAll / deleteSynthetic(metadataJson LIKE)
  - EntryDefinitionDao / EntryThoughtDao: insertAll
  - SearchDocumentDao: insertAll / getRowids(SearchDocRowId投影) / deleteSyntheticFts
  - EmbeddingDao: insertAll(IGNORE競合回避)
- クリア時のFTS掃除: search_document_fts は手動rowid同期方式のため entry削除CASCADEで消えない。
  deleteSyntheticFts() → deleteSynthetic() の順でトランザクション実行
- debug/AndroidManifest.xml でレシーバー登録(releaseには含まれない)

B. M-2: Macrobenchmarkモジュール (:benchmark)
- 新規モジュール com.android.test プラグイン + targetProjectPath=":app"
- benchmarkビルドタイプ(debuggable=false, matchingFallbacks=[release], debugキー署名)
- managed device pixel8Api34(ATDイメージ, 計測オーバーヘッド最小)
  ※AGP 9 では managedDevices.devices{} が localDevices{} に改名されていた
- テスト4種(benchmark/src/androidTest):
  - StartupBenchmark: cold start ×5 (CompilationMode.None。Baseline Profile比較用PartialはRound 4で追加)
  - NavigationBenchmark: 検索→統計→ホームのボトムナビ遷移 FrameTimingMetric
  - ScrollBenchmark: ダッシュボード一覧フリング×3 FrameTimingMetric
  - SearchBenchmark: 検索画面のEditTextにsetText(SetText semantics action経由でonValueChange発火) FrameTimingMetric
- アプリ側: マニフェストに <profileable android:shell="true"/>、release buildTypeにdebug署名設定
  (配布用リリースでは差し替える旨をコメント明記)
- libs.versions.toml: androidx.benchmark:benchmark-macro-junit4 1.4.1 / android-testプラグイン追加

C. M-3: docs/perf/BASELINE.md
- シード投入コマンド・ベンチマーク実行コマンド・1k/10k/50k×4指標の記録表テンプレート
- 「初回起動(rebuildあり)」を別記録とする注意書き、以降のRoundでの再計測ルール

検証
- :app:assembleDebug / :app:assembleRelease: 成功(R8修復後)
- :benchmark:assemble / :benchmark:assembleAndroidTest: 成功
  benchmark-benchmark.apk 生成まで確認(self-instrumenting構成)
- マニフェスト分離: debugのマージ済みマニフェストに PerfSeedReceiver あり /
  releaseには無し + <profileable> あり を確認
- :app:testDebugUnitTest: 全テスト green(99本)
- release APKに seeder が含まれないことをソースセット分離で担保(app/src/debug 配置)

全体再確認(2026-08-22)で発見・対処した不具合
- EmbeddingDao.insertAll 追加により InMemoryVectorIndexConcurrencyTest の手書きスタブが
  コンパイルエラー → スタブに override を追加(99本すべてgreen)
- 段階投入(1k→10k)でID接頭辞が同一のためPK衝突する潜在バグ → seed()冒頭で
  countSynthetic()>0 なら clear() してから投入する「置き換え」方式に変更
  (各SEEDで合成データは常にちょうどcount件。BASELINE.mdの手順も追記)
- java.util.Random.nextLong(bound) は minSdk 28 のAndroidランタイムに存在しない可能性
  (API 34系で追加) → nextDouble()ベースの自前実装に置換

実装中に発見・対処した不具合
- Kotlin文字列テンプレートの貪欲な識別子解釈: "$count件" が $count件(1識別子扱い)になりコンパイルエラー
  → "${count}件" 形式に統一
- AGP 9.3.1 で com.android.test プラグインのバージョン付き解決が「already on the classpath」エラー
  → ルートbuild.gradle.ktsに alias(libs.plugins.android.test) apply false を追加して解決
- AGP 9 で ManagedVirtualDevice コンテナのDSL名が devices → localDevices に変更
- 初回コミットで benchmark/build が .gitignore 未カバーで混入 → ignore追加上でamendして除去

残課題 (次セッション以降)
- 🛑 M-3の実測値入力: 自端末で BASELINE.md の手順を実行して表を埋める(この数字が全Roundの評価基準)
- Round 1 以降: DB層(WAL明示化・progress_events.entityIdインデックス・EXPLAIN QUERY PLAN)へ着手
- Round 4 向け: StartupBenchmark に CompilationMode.Partial(BaselineProfileMode.Require) を追加
