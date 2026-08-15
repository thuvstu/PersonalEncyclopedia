# 04-quiz-and-srs.md — 採点エンジン・SRSアルゴリズム・攻略度スコアの計算方法

学習は2トラック(§8)。

- **単語帳トラック**: `entry_definition` を対象に SRS(SM-2 / 将来 FSRS)で長期記憶へ定着させる。
- **クイズ演習トラック**: `quiz_bank` を対象に多段階採点+動的出題で理解度をチェックする。

## 1. 多段階採点エンジン(`brain/quiz/Grader.kt`)

`MultiStageGrader.grade(userAnswer, correctAnswer, mode)` は、1つの答えを何段階も試して最も緩い判定で成功させる。

| 段階 | 何を試すか | method |
|---|---|---|
| 1 | 完全一致(空白だけ除く) | exact |
| 2 | 正規化(空白・全角/半角・引用符・句読点を揃えて)比較 | normalized |
| 3 | 複数解答の展開(`A/B`, `C(付記)` の全パターン) | multi_answer |
| 4 | 数値(和暦→西暦変換込み、`紀元前`対応) | numeric |
| 5 | 内蔵同義語(ww1=第一次世界大戦, usa=米国 など) | synonym |
| 6 | レーベンシュタイン距離ベースの類似度(既定 0.85) | fuzzy |

- **和暦**は `era_master` テーブル参照で変換する(§8.9)。`1600年 = 慶長5年` のようなケースを正とする(テスト: `MultiStageGraderTest` の `1600年 equals 慶長5年`)。
- 元号らしき表記があるのにデータが無い場合は、誤答扱いにせず **`undeterminable=true`(判定不能)** で明示する。誤答と判定不能を混ぜないため(§8.9)。
- `mode` で厳しさを切替(`exact`=一致のみ / `strict` / `standard` / `lenient`=0.70)。

## 2. SRS(SM-2)の計算(`brain/srs/Sm2Algorithm.kt`)

復習グレード 0〜5 のルール(§8.5)。

- 0〜1: 完全に忘れた → 間隔リセット、**10分後**に再出題。
- 2: 答えを見れば思い出せた → 前回間隔の半分。
- 3〜5: 成功。初回は1日、2回目は6日、以降は `前回間隔 × easeFactor`。
- easeFactor: `max(1.3, 前回 + 0.1 - (5-grade)×(0.08 + (5-grade)×0.02))`。成功ほど伸びる。

**設計の要点**: `srs_review` は履歴テーブルであり、`repetitionCount`(累積成功回数)を明示記録している(§5.8.5)。
これにより SM-2 から FSRS(`brain/srs/FsrsAlgorithm.kt` に実装あり)へ、**既存データを失わずに**アルゴリズムだけ差し替えられる。
`SettingsScreen` の SRS 切替(§8.6)は `SrsRepository.recordReview` の分岐で SM-2/FSRS を選ぶ。

## 3. 攻略度スコアと速度ボーナス(§8.7)

- 回答結果は `quiz_attempts` に保存され、`answeredWithinMs`(設問表示〜回答までの経過時間)も記録する。
- クイズ演習のスコアには **Kahoot 由来の速度ボーナス**(§8.7.3)が効く: 正解かつ 10 秒未満で回答すると最大 +50%。
  `QuizRepository.gradeAndRecord()` がスコア係数を計算する。
- 復習モードに **サバイバル形式**(1問ミスで即終了、連続正解数を記録)と、**プレッシャーテスト全列挙型**(同一分野の定義群から正解集合を動的生成し、60秒制限で漏れなく答える)がある(§8.7.2)。`QuizViewModel` / `QuizScreen` がモード切替を実装。

## 4. 新採点システム(ルーブリック採点・試作)

`docs/新採点システム.txt` の仕様を実装した **rubric ベース採点**(試作)。既存の多段階採点では拾えない「意味が近いのに採点が全く違う」ケースを複数軸で判定する。

パイプライン(新採点システム.txt):

```
Rubric分解(RubricParser) → feature抽出(RubricFeatureExtractor)
→ confidence集計(RubricConfidence) → 最終LLM judge(RubricJudge)
```

- **Rubric分解**: `quiz_bank.gradingContextJson`(未設定なら模範解答から自動分解)。
  ```json
  {"rubric":[{"kind":"keyword","label":"必須語","expected":"プレート境界","weight":0.5},
             {"kind":"numeric_unit","label":"単位変換","expected":"72 km/h","weight":0.3},
             {"kind":"polarity","label":"肯定/否定","expected":"POSITIVE","weight":0.2}],
   "modelAnswers":["模範解答1","模範解答2"]}
  ```
- **判定軸** (`RubricKind`): `KEYWORD`(語の一致) / `CONCEPT`(Embedding類似度・複数模範解答とのmax) / `NUMERIC_UNIT`(単位換算込みの数値検証: `72 km/h = 20 m/s`) / `RELATION`(A→B vs B→A の向き) / `POLARITY`(否定・極性。**最も重要**:「位置する/しない」を区別) / `EXPLANATION`(説明量)。
- **confidence**: 決定論軸(数値0.98/キーワード0.95/極性0.9)は高く、Embeddingのみ(0.5〜0.7)は低い。矛盾シグナル(極性反転・関係反転)や判定不能、総合confidence<0.7 は `deferToLlm` で最終LLM judgeへ委譲。
- **最終LLM judge**: 構造化evidenceをプロンプトに埋め込みGeminiに最終判定させる。API未設定時は決定論フォールバック(重み付きスコア≧0.6で正解)。`judgeSource` は `llm` / `heuristic`。
- **provider差し替え点**: `brain/quiz/rubric/provider/` の `IEmbeddingProvider` / `IEntailmentProvider` / `ICrossEncoderProvider` / `IJudgerProvider`。現在は `GeminiGradingProviders`(既存Gemini APIアダプタ)。将来端末内モデル(Qwen3系)は `LocalGradingProviders` を同じInterfaceで追加し、`GradingProviderModule` のバインディング差し替えのみで切替。
- **統合点**: `QuizRepository.gradeAndRecord` が記述式(qa/essay・5文字以上)に適用。rubricが正解と判定した場合のみ `method="rubric"` へ昇格(既存の意味的採点昇格は維持)。採点根拠は `QuizViewModel.QuizUiState.Answered.rubricRationale` として渡り、`QuizScreen` の「採点の根拠」カードに表示される。DBマイグレーションは行わない(試作ブランチを統合しやすいように)。

## どこで使われているか

- アプリ内: `QuizViewModel`(出題→採点→履歴)、`SrsRepository`(復習)、`RubricGrader`(新採点システム)
- サーバー: `/api/quiz`・`/api/srs`(Web クライアント用、§10)
- テスト: `MultiStageGraderTest`(採点)、`brain/quiz/rubric/*Test`(新採点システム: PolarityAnalyzer/NumericUnitVerifier/RelationDirectionChecker/RubricGrader ほか)、`InMemoryVectorIndexConcurrencyTest`(検索基盤)

- 参考: 設計書 §8(クイズ・学習エンジン仕様)、`docs/新採点システム.txt`
