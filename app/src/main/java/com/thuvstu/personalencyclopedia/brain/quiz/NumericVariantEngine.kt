// 📁 app/src/main/java/com/thuvstu/personalencyclopedia/brain/quiz/NumericVariantEngine.kt
package com.thuvstu.personalencyclopedia.brain.quiz

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class NumericVariantEngine @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ParamConfig(val name: String, val min: Double, val max: Double, val step: Double = 1.0)

    @Serializable
    data class VariantConfig(
        val template: String,
        val params: List<ParamConfig>,
        val answerExpr: String
    )

    data class GeneratedVariant(val question: String, val answer: String)

    fun generate(configJson: String?): GeneratedVariant? {
        if (configJson.isNullOrBlank()) return null
        return try {
            val config = json.decodeFromString<VariantConfig>(configJson)
            val values = mutableMapOf<String, Double>()

            // パラメータの抽選
            for (p in config.params) {
                val steps = ((p.max - p.min) / p.step).toInt()
                if (steps < 0) continue
                val randomStep = Random.nextInt(0, steps + 1)
                values[p.name] = p.min + randomStep * p.step
            }

            // 問題文のテンプレート展開
            val question = config.template.replace(Regex("\\$\\{(\\w+)\\}")) { match ->
                val name = match.groupValues[1]
                val v = values[name] ?: return@replace match.value
                if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
            }

            // 答えの計算
            val answer = evaluateExpr(config.answerExpr, values)
            if (answer.isNaN()) return null

            val answerStr = if (answer % 1.0 == 0.0) answer.toInt().toString() else answer.toString()
            GeneratedVariant(question, answerStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun evaluateExpr(expr: String, values: Map<String, Double>): Double {
        val rhino = RhinoContext.enter()
        try {
            rhino.optimizationLevel = -1 // サンドボックス・安全性重視
            val scope: Scriptable = rhino.initStandardObjects()
            values.forEach { (k, v) ->
                rhino.evaluateString(scope, "var $k = $v;", "init", 1, null)
            }
            val result = rhino.evaluateString(scope, expr, "eval", 1, null)
            return (result as? Number)?.toDouble() ?: Double.NaN
        } finally {
            RhinoContext.exit()
        }
    }
}