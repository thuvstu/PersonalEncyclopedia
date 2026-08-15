package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IEmbeddingProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.IJudgerProvider
import com.thuvstu.personalencyclopedia.brain.quiz.rubric.provider.JudgeOutput

/**
 * テスト用フェイクプロバイダー。
 * - FakeEmbeddingProvider: バイグラム出現ベクトル(決定論的)。共有バイグラムが多いほどcosineが高くなる
 * - FakeJudgerProvider: 指定した出力を返す / available=false でフォールバック経路を検証できる
 */
class FakeEmbeddingProvider : IEmbeddingProvider {
    override val name: String = "fake"
    override val available: Boolean = true
    override suspend fun embed(text: String): FloatArray? = embedOf(text)

    companion object {
        fun embedOf(text: String): FloatArray {
            val vec = FloatArray(128)
            TextNorm.bigrams(text).forEach { b ->
                vec[Math.abs(b.hashCode()) % 128] += 1f
            }
            return vec
        }
    }
}

class FakeJudgerProvider(
    private val availableFlag: Boolean = true,
    private val output: JudgeOutput? = JudgeOutput(true, 1.0f, "fake judge", 0.9f)
) : IJudgerProvider {
    override val name: String = "fake-judger"
    override val available: Boolean get() = availableFlag
    var lastPrompt: String? = null
    override suspend fun judge(prompt: String): JudgeOutput? {
        lastPrompt = prompt
        return output
    }
}
