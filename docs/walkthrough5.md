Personal Encyclopedia — 新採点システム(ルーブリック採点)試作 (walkthrough5)
`docs/新採点システム.txt` の仕様を、ブランチ `feat/rubric-grader`(master から切出し)で試作実装しました。
「1機能1ビルド」「既存コードを唯一の正とする」方針に沿い、最終的にローカルモデルへ差し替えられるよう provider を Interface 化しています。

方針(ユーザー確認済み)
- 統合は「エンジン + 既存クイズフロー接続」、ML は既存 Gemini + 決定論解析器
- LLM judge は API 未設定時は決定論フォールバック
- DB マイグレーションは行わない(ブランチ統合時にスキーマ非互換を作らない)
- ローカルモデル切替は `GradingProviderModule` のバインディング差し替えだけで完了

実施コミット
C1(05029ec): データモデル + provider層
- `brain/quiz/rubric/RubricModels.kt`: RubricKind(KEYWORD/CONCEPT/NUMERIC_UNIT/RELATION/POLARITY/EXPLANATION)、RubricItem、Signal、RubricItemFeature、RubricEvidence、RubricGradeResult、evidence→LLM用JSON
- `brain/quiz/rubric/provider/`: IEmbeddingProvider / IEntailmentProvider / ICrossEncoderProvider / IJudgerProvider(全provider「利用不可ならnull」契約)、GeminiGradingProviders(既存 GeminiClient アダプタ)、GradingProviderModule(Hilt)

C2(20220da): 決定論解析器
- TextNorm(MultiStageGrader と同仕様の正規化/類似度/bigram)
- KeywordMatcher(部分一致 + 誤字脱字許容の類似度)
- PolarityAnalyzer(否定・極性・否定スコープ。最重要軸)
- NumericUnitVerifier(単位換算込みの数値検証。era_master 再利用)
- RelationDirectionChecker(A→B vs B→A、比較・因果の向き)

C3(b188c44): Rubric分解 + 解析器テスト
- RubricParser(gradingContextJson 契約 + 模範解答からの自動分解フォールバック)
- テスト6本: KeywordMatcher / PolarityAnalyzer / NumericUnitVerifier / RelationDirectionChecker / RubricParser / TextNormConsistency

C4(68727b4): 採点エンジン + E2Eテスト
- RubricFeatureExtractor(軸ごとの score/confidence/signals 抽出。複数模範解答との embedding max)
- RubricConfidence(重み付き平均 + defer 判定: 矛盾/判定不能/総合<0.7 で LLM judge へ委譲)
- RubricJudge(evidence 埋め込みプロンプトで Gemini 判定、失敗時はヒューリスティックフォールバック)
- RubricGrader(トップレベル・適用判定 qa/essay + 5文字以上)
- テスト: RubricFeatureExtractor / RubricConfidence / RubricJudge / RubricGrader(E2E)

C5(af9c8fd): 既存フロー統合
- QuizRepository.gradeAndRecord: rubric が正解と判定した場合のみ method="rubric" へ昇格(既存の意味的採点昇格は維持)。返り値を QuizGradingResult(attempt + rubricRationale + rubricEvidenceJson)へ
- QuizViewModel.QuizUiState.Answered に rubricRationale / rubricEvidenceJson を追加
- QuizScreen の Answered 分岐に「採点の根拠」カードを追加

C6(c9e3e7f): ドキュメント
- docs/guide/04-quiz-and-srs.md に新採点システムの節を追記

検証
- :app:testDebugUnitTest: 全テスト green(新規67件 + 既存)
- :app:assembleDebug: 成功(Hilt DI 含む)
- git push -u origin feat/rubric-grader: 完了

実装中に発見・対処した不具合
- NumericUnitVerifier の速度グループ換算係数誤り(m/s を 1/3.6 ではなく 3.6 に修正)
- parseYear が「100°C」の 100 を年と誤解釈 → 年号らしき表記に限定してから era_master 比較
- Kotlin はインターフェース修飾付きプロパティ override 不可 → 共通 available は単一実装で満たす
- expected が「POSITIVE/NEGATIVE」ラベル指定の POLARITY は参照文がないため、極性不一致自体を矛盾シグナルに

残課題(本格実装時)
- gradingContextJson の実データ投入(RuleBasedQuizGenerator / LlmQuizGenerator からの書き出し)
- 端末内モデル実装(LocalGradingProviders: Qwen3-Embedding-4B / NLI / Reranker / ローカルLLM)
- rubric スコアの部分点への反映(現状は正誤のみ昇格)
- rubricRationale / evidenceJson の DB 永続化(統合時マイグレーション)
