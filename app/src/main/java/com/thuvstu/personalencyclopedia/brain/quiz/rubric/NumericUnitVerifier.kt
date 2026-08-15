package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader

/**
 * 数値・単位・式の決定論的検証(新採点システム.txt「数値・単位・式の検証」)。
 * LLM/Embeddingではなく決定論的に検証するため、confidence を高く与えられる。
 *
 * 検証順:
 *   1. 年号・純数値比較(MultiStageGrader.gradeNumeric を再利用。era_master 参照含む)
 *   2. 数値+単位ペアの単位換算比較(72 km/h = 20 m/s 等)
 *   3. どちらにも検証可能な数値が無ければ 判定不能(null) を返す
 */
class NumericUnitVerifier(
    private val grader: MultiStageGrader
) {

    data class NumberWithUnit(val value: Double, val unit: String)

    data class NumericCheckResult(
        /** null = 判定不能(誤答扱いしない) */
        val matched: Boolean?,
        val detail: String,
        val userValue: String? = null,
        val expectedValue: String? = null,
        val converted: String? = null
    )

    companion object {
        private val UNIT_PATTERN = Regex(
            "(-?\\d+(?:\\.\\d+)?)\\s*(km/h|m/s|mph|km|cm|mm|kg|mg|t|min|ms|sec|°C|℃|°F|℉|%|h|m|g|s|年|秒|分|時間|円|個|倍)?",
            RegexOption.IGNORE_CASE
        )

        /** 「1600年」「2024年」等の純年号表記 */
        private val FULL_YEAR_RE = Regex("\\d{3,4}年")

        /** 和暦・元号らしき表記(漢字2〜3字+数字 or 元)を含むか */
        private fun containsEra(text: String): Boolean =
            Regex("[\\u4E00-\\u9FFF]{2,3}(\\d{1,2}年?|元)").containsMatchIn(text)

        // 単位換算テーブル。同一グループ内で SI 基準値(右辺)へ変換して比較する
        private val GROUPS: List<Map<String, Double>> = listOf(
            mapOf("km/h" to 1.0, "m/s" to 3.6, "mph" to 1.609344), // → km/h 基準(1 m/s = 3.6 km/h)
            mapOf("km" to 1000.0, "m" to 1.0, "cm" to 0.01, "mm" to 0.001), // → m 基準
            mapOf("t" to 1000.0, "kg" to 1.0, "g" to 0.001, "mg" to 0.000001), // → kg 基準
            mapOf("h" to 3600.0, "min" to 60.0, "s" to 1.0, "ms" to 0.001), // → s 基準
            mapOf("%" to 1.0)
        )
    }

    /** テキストから数値+単位のペアを抽出する */
    fun extractNumbers(text: String): List<NumberWithUnit> {
        return UNIT_PATTERN.findAll(text).mapNotNull { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val unit = m.groupValues[2].lowercase().ifEmpty { "" }
            NumberWithUnit(value, unit)
        }.toList()
    }

    /** 単位換算。互換グループ内なら toUnit 基準の値へ変換、非対応は null */
    fun convert(value: Double, from: String, to: String): Double? {
        val f = from.lowercase(); val t = to.lowercase()
        if (f == t) return value
        if (f == "°c" && t == "°f") return value * 9.0 / 5.0 + 32.0
        if (f == "°f" && t == "°c") return (value - 32.0) * 5.0 / 9.0
        for (group in GROUPS) {
            val fFactor = group[f] ?: continue
            val tFactor = group[t] ?: continue
            return value * fFactor / tFactor
        }
        return null
    }

    suspend fun verify(userText: String, expectedText: String): NumericCheckResult {
        val yearLikeUser = containsEra(userText) || FULL_YEAR_RE.matches(userText.trim())
        val yearLikeExpected = containsEra(expectedText) || FULL_YEAR_RE.matches(expectedText.trim())

        // 1. 年号・元号の既存比較(era_master 経由)。純年号・和暦表記のみに限定
        //    (parseYear は「100°C」の 100 を年と誤解するため、ここでは年号らしき表記に絞る)
        if (yearLikeUser && yearLikeExpected) {
            grader.gradeNumeric(userText, expectedText)?.let { basic ->
                return if (basic.undeterminable) {
                    NumericCheckResult(null, "判定不能(元号データ不足)")
                } else {
                    NumericCheckResult(
                        matched = basic.isCorrect,
                        detail = if (basic.isCorrect) "数値一致" else "数値不一致",
                        userValue = userText, expectedValue = expectedText
                    )
                }
            }
        }

        // 2. 純数値比較(テキスト全体が数値の場合)
        val userNum = userText.trim().toDoubleOrNull()
        val expectedNum = expectedText.trim().toDoubleOrNull()
        if (userNum != null && expectedNum != null) {
            return NumericCheckResult(
                matched = kotlin.math.abs(userNum - expectedNum) < 1e-9,
                detail = if (userNum == expectedNum) "数値一致" else "数値不一致",
                userValue = userText, expectedValue = expectedText
            )
        }

        // 3. 数値+単位ペアの単位換算比較
        val userNums = extractNumbers(userText)
        val expectedNums = extractNumbers(expectedText)
        if (userNums.isEmpty() && expectedNums.isEmpty()) {
            return NumericCheckResult(null, "数値なし(判定不能)")
        }
        if (userNums.isEmpty() || expectedNums.isEmpty()) {
            return NumericCheckResult(null, "片側にしか数値が無い(判定不能)")
        }
        for (exp in expectedNums) {
            for (user in userNums) {
                val converted = convert(user.value, user.unit, exp.unit)
                if (converted != null && kotlin.math.abs(converted - exp.value) < 1e-6) {
                    return NumericCheckResult(
                        matched = true,
                        detail = "単位換算一致(${user.value} ${user.unit} = ${fmt(converted)} ${exp.unit})",
                        userValue = fmt(user.value) + " " + user.unit,
                        expectedValue = fmt(exp.value) + " " + exp.unit,
                        converted = fmt(converted) + " " + exp.unit
                    )
                }
            }
        }
        return NumericCheckResult(
            matched = false,
            detail = "数値・単位が一致しない",
            userValue = userNums.joinToString(", ") { "${fmt(it.value)} ${it.unit}".trim() },
            expectedValue = expectedNums.joinToString(", ") { "${fmt(it.value)} ${it.unit}".trim() }
        )
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.')
}
