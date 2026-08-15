package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IEmbeddingProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IJudgerProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 新採点システム(ルーブリック採点)のトップレベルエントリポイント。
 *
 * パイプライン(新採点システム.txt):
 *   Rubric分解(RubricParser) → feature抽出(RubricFeatureExtractor) → confidence集計(RubricConfidence)
 *   → 最終LLM judge(RubricJudge、未設定時は決定論フォールバック)
 *
 * 既存採点(MultiStageGrader/SemanticGrader)との使い分けは QuizRepository 側で適用判定する。
 * providerの差し替え(LocalGradingProviders)は GradingProviderModule のバインディング変更のみで完了し、
 * 本クラスは Interface しか参照しない。
 */
@Singleton
class RubricGrader @Inject constructor(
    multiStageGrader: MultiStageGrader,
    private val embeddingProvider: IEmbeddingProvider,
    private val judgerProvider: IJudgerProvider
) {
    private val numericVerifier = NumericUnitVerifier(multiStageGrader)
    private val extractor = RubricFeatureExtractor(embeddingProvider, numericVerifier)
    private val judge = RubricJudge(judgerProvider)

    /**
     * ルーブリック採点の適用判定。記述式(qa/essay)で十分な長さの回答のみ対象。
     * __UNLEARNED__(未習スキップ)は対象外。
     */
    fun applicable(quizType: String, userAnswer: String): Boolean =
        quizType in listOf("qa", "essay") &&
            userAnswer != "__UNLEARNED__" &&
            userAnswer.length >= 5

    suspend fun grade(
        question: String,
        userAnswer: String,
        correctAnswer: String,
        gradingContextJson: String?
    ): RubricGradeResult {
        val bundle = RubricParser.parse(gradingContextJson, correctAnswer)
        val features = extractor.extract(userAnswer, bundle)
        val evidence = RubricConfidence.compute(features, embeddingProvider.name)
        val outcome = judge.judge(question, userAnswer, correctAnswer, evidence, bundle)
        return RubricGradeResult(
            isCorrect = outcome.isCorrect,
            score = outcome.score,
            method = "rubric",
            rationale = outcome.rationale,
            evidence = evidence,
            judgeSource = outcome.source
        )
    }
}
